package com.tonic.analysis;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Validates the verbose method-level disassembly against a javac-compiled fixture that exercises
 * line numbers, local variables, an exception table, and invokedynamic (lambda + string concat).
 */
class VerboseDisassemblyTest {

    private static final String SOURCE =
            "package t;\n" +
            "import java.util.function.Supplier;\n" +
            "public class Verbose {\n" +
            "    public int run(int n) {\n" +
            "        int total = 0;\n" +
            "        try {\n" +
            "            Supplier<String> s = () -> \"x\" + n;\n" +
            "            total = s.get().length() + n;\n" +
            "        } catch (RuntimeException e) {\n" +
            "            total = -1;\n" +
            "        }\n" +
            "        return total;\n" +
            "    }\n" +
            "}\n";

    private CodeAttribute compileRunMethod() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available in this runtime");

        Path dir = Files.createTempDirectory("yabr-verbose");
        try {
            Path src = dir.resolve("Verbose.java");
            Files.writeString(src, SOURCE);
            int rc = compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString());
            assertEquals(0, rc, "javac should compile the fixture");

            byte[] bytes = Files.readAllBytes(dir.resolve("t").resolve("Verbose.class"));
            ClassFile cf = new ClassFile(new ByteArrayInputStream(bytes));
            MethodEntry run = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("run"))
                    .findFirst()
                    .orElseThrow();
            return run.getCodeAttribute();
        } finally {
            try (var paths = Files.walk(dir)) {
                paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void verboseIncludesAllEnrichments() throws Exception {
        CodeAttribute code = compileRunMethod();

        String verbose = code.prettyPrintCode(DisassemblyOptions.verbose());

        assertTrue(verbose.contains("// signature: run(n: I)"), "signature with param name present:\n" + verbose);
        assertTrue(verbose.contains("max_stack ="), "header present:\n" + verbose);
        assertTrue(verbose.contains("// line "), "line numbers present:\n" + verbose);
        assertTrue(verbose.contains("invokedynamic"), "invokedynamic present:\n" + verbose);
        assertTrue(verbose.contains("// BSM:"), "bootstrap resolved present:\n" + verbose);
        assertTrue(verbose.contains("Exception table:"), "exception table present:\n" + verbose);
        assertTrue(verbose.contains("// total: I") || verbose.contains("// n: I"),
                "local variable annotation present:\n" + verbose);
    }

    @Test
    void terseOptionMatchesLegacyOutput() throws Exception {
        CodeAttribute code = compileRunMethod();

        String legacy = code.prettyPrintCode();
        String terse = code.prettyPrintCode(DisassemblyOptions.terse());

        assertEquals(legacy, terse, "terse options must reproduce the legacy listing");
    }

    @Test
    void verboseContainsEveryTerseInstructionLine() throws Exception {
        CodeAttribute code = compileRunMethod();

        String terse = code.prettyPrintCode(DisassemblyOptions.terse());
        String verbose = code.prettyPrintCode(DisassemblyOptions.verbose());

        for (String line : terse.split("\n")) {
            String instruction = line.trim();
            if (instruction.matches("\\d{4}:.*")) {
                String offset = instruction.substring(0, instruction.indexOf(':'));
                assertTrue(verbose.contains(offset + ":"),
                        "verbose output should retain instruction at offset " + offset);
            }
        }
    }
}
