package com.tonic.analysis.source;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class CompileAndRunTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void compileAndRunWithImports() throws Exception {
        String source =
            "package test;\n" +
            "\n" +
            "import java.time.LocalDateTime;\n" +
            "\n" +
            "public class Logger {\n" +
            "    public static void main(String[] args) {\n" +
            "        System.out.println(\"[\" + LocalDateTime.now() + \"] Hello\");\n" +
            "    }\n" +
            "}\n";

        ClassFile cf = compileSource(source, "test/Logger");

        Path testPackage = tempDir.resolve("test");
        Files.createDirectories(testPackage);
        Path classFilePath = testPackage.resolve("Logger.class");
        byte[] classBytes = cf.write();
        Files.write(classFilePath, classBytes);

        System.out.println("Wrote class file to: " + classFilePath);
        System.out.println("Class file size: " + classBytes.length + " bytes");

        ProcessBuilder pb = new ProcessBuilder(
            "java", "-cp", tempDir.toString(), "test.Logger"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        System.out.println("Exit code: " + exitCode);
        System.out.println("Output:\n" + output);

        assertEquals(0, exitCode, "Process should exit successfully. Output: " + output);
        assertTrue(output.contains("Hello"), "Output should contain message");
    }

    @Test
    void compileAndRunSimpleConcat() throws Exception {
        String source =
            "package test;\n" +
            "\n" +
            "public class Simple {\n" +
            "    public static void main(String[] args) {\n" +
            "        String msg = \"Hello\";\n" +
            "        System.out.println(\"[INFO] \" + msg);\n" +
            "    }\n" +
            "}\n";

        ClassFile cf = compileSource(source, "test/Simple");

        Path testPackage = tempDir.resolve("test");
        Files.createDirectories(testPackage);
        Path classFilePath = testPackage.resolve("Simple.class");
        byte[] classBytes = cf.write();
        Files.write(classFilePath, classBytes);

        ProcessBuilder pb = new ProcessBuilder(
            "java", "-cp", tempDir.toString(), "test.Simple"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        System.out.println("Exit code: " + exitCode);
        System.out.println("Output:\n" + output);

        assertEquals(0, exitCode, "Process should exit successfully. Output: " + output);
        assertTrue(output.contains("[INFO] Hello"), "Output should contain message");
    }

    /**
     * Regression: an instance method called on a bare field receiver ({@code s.length()}, where {@code s}
     * is a field, not a local) must resolve the field's type and emit {@code invokevirtual
     * java/lang/String.length:()I} - not a bogus {@code invokestatic s.length:()Ljava/lang/Object;} (which
     * stores a reference where an int is expected and fails bytecode verification).
     */
    @Test
    void instanceMethodOnFieldReceiverEmitsInvokevirtual() throws Exception {
        String source =
            "package test;\n" +
            "public class FieldRecv {\n" +
            "    static String s = \"hello\";\n" +
            "    public static int len() {\n" +
            "        return s.length();\n" +
            "    }\n" +
            "}\n";

        ClassFile cf = compileSource(source, "test/FieldRecv");
        MethodEntry len = cf.getMethod("len", "()I");
        assertNotNull(len, "len() should be compiled");

        boolean foundVirtualLength = false;
        for (com.tonic.analysis.instruction.Instruction instr : new com.tonic.analysis.CodeWriter(len).getInstructions()) {
            if (instr instanceof com.tonic.analysis.instruction.InvokeStaticInstruction) {
                assertNotEquals("length",
                    ((com.tonic.analysis.instruction.InvokeStaticInstruction) instr).getMethodName(),
                    "length() must not be lowered to invokestatic");
            }
            if (instr instanceof com.tonic.analysis.instruction.InvokeVirtualInstruction) {
                com.tonic.analysis.instruction.InvokeVirtualInstruction call =
                    (com.tonic.analysis.instruction.InvokeVirtualInstruction) instr;
                if ("length".equals(call.getMethodName())) {
                    foundVirtualLength = true;
                    assertEquals("java/lang/String", call.getOwnerClass());
                    assertEquals("()I", call.getMethodDescriptor());
                }
            }
        }
        assertTrue(foundVirtualLength, "expected invokevirtual java/lang/String.length:()I");
    }


    private ClassFile compileSource(String source, String className) throws Exception {
        return TestUtils.compileSource(source, className);
    }
}
