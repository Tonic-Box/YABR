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
        classFile = compile("Mapped", SOURCE);
        result = new ClassDecompiler(classFile).decompileWithLineMap();
        lines = result.getSource().split("\n", -1);
    }

    /** Compiles a single top-level class to bytecode and parses it into a {@link ClassFile}. */
    private static ClassFile compile(String simpleName, String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null);
        Path dir = Files.createTempDirectory("yabr-linemap");
        try {
            Path src = dir.resolve(simpleName + ".java");
            Files.writeString(src, source);
            assertEquals(0, compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()));
            byte[] bytes = Files.readAllBytes(dir.resolve(simpleName + ".class"));
            return new ClassFile(new ByteArrayInputStream(bytes));
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
    void enclosingMethodMapExcludesLambdaBody() {
        // The lambda body's offsets live in a separate method's space, so they must not appear in the
        // enclosing method's map (they go under the lambda's own key instead — see next test).
        NavigableMap<Integer, Integer> map = result.getLineMaps().get("sum(I)I");
        for (int line : map.values()) {
            String text = lineText(line);
            assertFalse(text.contains("counter++") || text.contains("\"lambda\""),
                    "lambda-body statement leaked into the enclosing method's map: line "
                            + line + " : " + text.trim());
        }
    }

    @Test
    void lambdaBodyMappedUnderItsOwnKey() {
        String lambdaKey = result.getLineMaps().keySet().stream()
                .filter(k -> k.startsWith("lambda$"))
                .findFirst()
                .orElse(null);
        assertNotNull(lambdaKey, "the inlined lambda body should have its own line-map key: "
                + result.getLineMaps().keySet());

        String mapped = result.getLineMaps().get(lambdaKey).values().stream()
                .map(LineMapTest::lineText)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(mapped.contains("counter++") || mapped.contains("\"lambda\""),
                "lambda body statements must be mapped under the lambda key:\n" + mapped);
    }

    @Test
    void methodSpansCoverTheirMappedLines() {
        DecompileResult.MethodSpan sumSpan = result.getMethodSpan("sum", "(I)I");
        assertNotNull(sumSpan, "sum should have a span");
        assertTrue(lineText(sumSpan.getStartLine()).contains("sum("),
                "span must start at the declaration: " + lineText(sumSpan.getStartLine()).trim());
        assertEquals("}", lineText(sumSpan.getEndLine()).trim(),
                "span must end at the closing brace: " + lineText(sumSpan.getEndLine()).trim());
        for (int line : result.getLineMaps().get("sum(I)I").values()) {
            assertTrue(sumSpan.contains(line),
                    "mapped line " + line + " outside span [" + sumSpan.getStartLine() + ", " + sumSpan.getEndLine() + "]");
        }

        DecompileResult.MethodSpan greetSpan = result.getMethodSpan("greet", "(Ljava/lang/String;)V");
        assertNotNull(greetSpan);
        assertTrue(greetSpan.getEndLine() < sumSpan.getStartLine() || sumSpan.getEndLine() < greetSpan.getStartLine(),
                "method spans must not overlap");
    }

    @Test
    void fieldSpanCoversDeclaration() {
        DecompileResult.MemberSpan span = result.getFieldSpan("counter", "I");
        assertNotNull(span, "counter should have a field span");
        assertEquals(span.getStartLine(), span.getEndLine(), "plain field is a single line");
        String text = lineText(span.getStartLine());
        assertTrue(text.contains("counter"), "field span must land on the declaration: " + text.trim());
        assertEquals(";", text.trim().substring(text.trim().length() - 1), "field span must end at ';'");
    }

    @Test
    void classSpanCoversDeclaration() {
        DecompileResult.MemberSpan span = result.getClassSpan();
        assertNotNull(span, "class should have a span");
        StringBuilder spanned = new StringBuilder();
        for (int line = span.getStartLine(); line <= span.getEndLine(); line++) {
            spanned.append(lineText(line)).append('\n');
        }
        assertTrue(spanned.toString().contains("class Mapped"),
                "class span must cover the declaration:\n" + spanned);
    }

    @Test
    void annotationsAreIncludedInSpans() throws Exception {
        String source =
            "import java.lang.annotation.*;\n" +
            "@Deprecated\n" +
            "public class Annotated {\n" +
            "    @Deprecated\n" +
            "    static int flagged;\n" +
            "}\n";
        DecompileResult annotated = new ClassDecompiler(compile("Annotated", source)).decompileWithLineMap();
        String[] aLines = annotated.getSource().split("\n", -1);

        DecompileResult.MemberSpan fieldSpan = annotated.getFieldSpan("flagged", "I");
        assertNotNull(fieldSpan);
        assertTrue(fieldSpan.getEndLine() > fieldSpan.getStartLine(),
                "annotated field span must include the annotation line");
        assertTrue(aLines[fieldSpan.getStartLine() - 1].contains("@Deprecated"),
                "field span must start at its annotation");
        assertTrue(aLines[fieldSpan.getEndLine() - 1].contains("flagged"),
                "field span must end at the declaration");

        DecompileResult.MemberSpan classSpan = annotated.getClassSpan();
        assertNotNull(classSpan);
        assertTrue(classSpan.getEndLine() > classSpan.getStartLine(),
                "annotated class span must include the annotation line");
        assertTrue(aLines[classSpan.getStartLine() - 1].contains("@Deprecated"),
                "class span must start at its annotation");
        assertTrue(aLines[classSpan.getEndLine() - 1].contains("class Annotated"),
                "class span must end at the declaration");
    }

    @Test
    void plainDecompileOutputUnchanged() {
        assertEquals(new ClassDecompiler(classFile).decompile(), result.getSource());
    }
}
