package com.tonic.analysis.source.decompile;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies {@link ClassDecompiler#decompileWithLineMap()}: per-method bytecode-offset-to-line maps
 * resolve call sites to the lines that actually contain them, survive the AST transform pipeline
 * (if/loop/switch/try shapes), suppress inlined lambda-body offsets, and leave the plain
 * {@link ClassDecompiler#decompile()} output untouched.
 */
class LineMapTest {

    private static final String SOURCE =
        "public class Mapped {\n" +
        "    static int counter;\n" +
        "    static void greet(String name) { System.out.println(name); }\n" +
        "    static int sum(int n) {\n" +
        "        int total = 0;\n" +
        "        for (int i = 0; i < n; i++) { total += i; }\n" +
        "        if (total > 10) { greet(\"big\"); } else { greet(\"small\"); }\n" +
        "        switch (n) {\n" +
        "            case 1: greet(\"one\"); break;\n" +
        "            case 2: greet(\"two\"); break;\n" +
        "            default: greet(\"many\"); break;\n" +
        "        }\n" +
        "        try { greet(\"try\"); } catch (RuntimeException e) { greet(\"catch\"); }\n" +
        "        Runnable r = () -> { counter++; greet(\"lambda\"); };\n" +
        "        r.run();\n" +
        "        return total;\n" +
        "    }\n" +
        "}\n";

    private static ClassFile classFile;
    private static DecompileResult result;
    private static String[] lines;

    @BeforeAll
    static void setUp() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null);
        Path dir = Files.createTempDirectory("yabr-linemap");
        try {
            Path src = dir.resolve("Mapped.java");
            Files.writeString(src, SOURCE);
            assertEquals(0, compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()));
            byte[] bytes = Files.readAllBytes(dir.resolve("Mapped.class"));
            classFile = new ClassFile(new ByteArrayInputStream(bytes));
            result = new ClassDecompiler(classFile).decompileWithLineMap();
            lines = result.getSource().split("\n", -1);
        } finally {
            try (var paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) { }
                });
            }
        }
    }

    private static String lineText(int line) {
        return line >= 1 && line <= lines.length ? lines[line - 1] : "";
    }

    /** Resolves a PC the way a consumer would: ceiling candidate first, then floor. */
    private static boolean resolvesToGreetCall(NavigableMap<Integer, Integer> map, int pc) {
        Map.Entry<Integer, Integer> ceiling = map.ceilingEntry(pc);
        Map.Entry<Integer, Integer> floor = map.floorEntry(pc);
        return (ceiling != null && lineText(ceiling.getValue()).contains("greet("))
            || (floor != null && lineText(floor.getValue()).contains("greet("));
    }

    @Test
    void callSitesResolveToTheirLines() {
        NavigableMap<Integer, Integer> map = result.getLineMap("sum", "(I)I");
        assertNotNull(map, "sum should have a line map");
        assertFalse(map.isEmpty());

        SSA ssa = new SSA(classFile.getConstPool());
        MethodEntry sum = classFile.getMethod("sum", "(I)I");
        IRMethod ir = ssa.lift(sum);

        List<Integer> greetOffsets = new ArrayList<>();
        for (IRBlock block : ir.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof InvokeInstruction
                        && "greet".equals(((InvokeInstruction) instr).getName())
                        && instr.getBytecodeOffset() >= 0) {
                    greetOffsets.add(instr.getBytecodeOffset());
                }
            }
        }
        assertTrue(greetOffsets.size() >= 5, "expected the fixture's greet call sites, got " + greetOffsets);

        int resolved = 0;
        for (int pc : greetOffsets) {
            if (resolvesToGreetCall(map, pc)) {
                resolved++;
            }
        }
        assertTrue(resolved >= 5,
                "call sites resolving to a greet( line: " + resolved + "/" + greetOffsets.size());
    }

    @Test
    void switchAndTryArmsAreMapped() {
        String mapped = result.getLineMaps().get("sum(I)I").values().stream()
                .map(LineMapTest::lineText)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(mapped.contains("\"one\""), "switch case body must be mapped:\n" + mapped);
        assertTrue(mapped.contains("\"two\""), "switch case body must be mapped:\n" + mapped);
        assertTrue(mapped.contains("\"try\""), "try body must be mapped:\n" + mapped);
    }

    @Test
    void lambdaBodyOffsetsAreSuppressed() {
        NavigableMap<Integer, Integer> map = result.getLineMaps().get("sum(I)I");
        for (int line : map.values()) {
            String text = lineText(line);
            assertFalse(text.contains("counter++") || text.contains("\"lambda\""),
                    "lambda-body statement leaked into the enclosing method's map: line "
                            + line + " : " + text.trim());
        }
    }

    @Test
    void plainDecompileOutputUnchanged() {
        assertEquals(new ClassDecompiler(classFile).decompile(), result.getSource());
    }
}
