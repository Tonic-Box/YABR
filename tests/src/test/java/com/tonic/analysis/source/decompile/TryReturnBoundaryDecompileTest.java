package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.BytecodeBuilder.Label;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Faithful-recovery tests for two control-flow shapes the DemoApplication gate corpus lacks, where the
 * reaching-condition engine was found (only via a differential oracle sweep over a large jar) to
 * mishandle real-world bytecode: a {@code try { return foo(); }} whose {@code return} the compiler places
 * past the protected range, and a guard-clause tail reached by fall-through past a side-effecting
 * condition. These lock in correct output for the shapes; the exact historical mis-recovery depended on a
 * specific compiler's block layout that neither the host javac nor a hand-built class reproduce, so the
 * differential oracle remains the guard for that (see the recovery-oracle notes).
 */
class TryReturnBoundaryDecompileTest {

    /**
     * {@code try { return foo(); }} - the returned value is computed inside the protected range but
     * returned from a block outside it. The engine's try-body region (bounded by the protected range)
     * must not drop that return.
     */
    @Test
    void tryReturningAValuePastTheProtectedRangeKeepsTheReturn() throws Exception {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryRet")
            .publicStaticMethod("f", "(Ljava/lang/String;)Ljava/lang/Class;");
        Label tryStart = mb.newLabel();
        Label tryEnd = mb.newLabel();
        Label handler = mb.newLabel();
        ClassFile cf = mb
            .label(tryStart)
            .aload(0)
            .invokestatic("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
            .label(tryEnd)          // protected range ends here - the areturn below is outside it
            .areturn()
            .label(handler)
            .pop()
            .aconst_null()
            .areturn()
            .tryCatch(tryStart, tryEnd, handler, "java/lang/ClassNotFoundException")
            .build();

        String out = new ClassDecompiler(cf).decompile();
        assertTrue(out.contains("Class.forName"),
            "the try's returned value, computed inside the protected range, must survive:\n" + out);
        assertTrue(out.contains("return") && !out.replaceAll("\\s", "").contains("try{}"),
            "the return must not be dropped, leaving an empty try:\n" + out);
    }

    /**
     * A guard-clause tail reached by fall-through past a side-effecting condition
     * ({@code if (a && sideEffect()) continue; tail}) must evaluate the condition once, not repeat it
     * under a redundant tail guard (which would duplicate the effect).
     */
    @Test
    void impureGuardClauseTailEvaluatesTheConditionOnce() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        org.junit.jupiter.api.Assumptions.assumeTrue(compiler != null, "no JDK compiler in this runtime");
        Path dir = Files.createTempDirectory("yabr-guard");
        try {
            Path srcFile = dir.resolve("Guard.java");
            Files.writeString(srcFile,
                "public class Guard {\n" +
                "  public static int f(String[] items) {\n" +
                "    int n = 0;\n" +
                "    for (String s : items) {\n" +
                "      if (s != null && !s.isEmpty()) { continue; }\n" +
                "      n++;\n" +
                "    }\n" +
                "    return n;\n" +
                "  }\n" +
                "}\n");
            assertEquals(0, compiler.run(null, null, null, "-d", dir.toString(), srcFile.toString()), "javac failed");
            byte[] bytes = Files.readAllBytes(dir.resolve("Guard.class"));
            String out = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
            assertEquals(1, count(out, "isEmpty()"),
                "a side-effecting guard condition must be evaluated once, not repeated under a tail guard:\n" + out);
        } finally {
            try (var paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    private static int count(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
