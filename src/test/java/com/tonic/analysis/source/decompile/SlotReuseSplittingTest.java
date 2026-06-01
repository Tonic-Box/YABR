package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The JVM reuses one local slot for unrelated variables whose live ranges do not
 * overlap. The recovery layer must split such reuse into distinct, correctly typed
 * source variables; merging them under one over-broadened type (boolean, or Object)
 * produces output that does not recompile.
 */
class SlotReuseSplittingTest {

    @Test
    void slotReusedForStringThenIntSplitsAndRecompiles() throws Exception {
        String src =
            "public class Reuse {\n" +
            "  static String m(int x) {\n" +
            "    if (x > 0) {\n" +
            "      String s = \"value=\" + x;\n" +
            "      return s.trim();\n" +
            "    } else {\n" +
            "      int n = x * 31 + 7;\n" +
            "      return Integer.toString(n);\n" +
            "    }\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("Reuse", src);
        assertRecompiles("Reuse", out);
    }

    @Test
    void slotReusedForTwoReferenceTypesSplitsAndRecompiles() throws Exception {
        String src =
            "public class RefReuse {\n" +
            "  static String m(int x) {\n" +
            "    if (x > 0) {\n" +
            "      String s = String.valueOf(x);\n" +
            "      return s.substring(0);\n" +
            "    } else {\n" +
            "      StringBuilder b = new StringBuilder();\n" +
            "      b.append(x);\n" +
            "      return b.toString();\n" +
            "    }\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("RefReuse", src);
        assertRecompiles("RefReuse", out);
        assertFalse(out.contains("Object local"),
            "distinct reference types reused in one slot must not collapse to Object:\n" + out);
    }

    @Test
    void genuineMergeStaysOneVariableAndRecompiles() throws Exception {
        String src =
            "public class Merge {\n" +
            "  static int m(boolean c, java.util.List<String> a, java.util.List<String> b) {\n" +
            "    java.util.List<String> l = c ? a : b;\n" +
            "    return l.size();\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("Merge", src);
        assertRecompiles("Merge", out);
    }

    @Test
    void slotReusedAcrossManyDisjointTypesRecompiles() throws Exception {
        String src =
            "public class ManyReuse {\n" +
            "  static Object dispatch(int op, int x) {\n" +
            "    switch (op) {\n" +
            "      case 1: { String s = \"a\" + x; return s; }\n" +
            "      case 2: { int n = x + 1; return Integer.valueOf(n); }\n" +
            "      case 3: { long g = (long) x * 2L; return Long.valueOf(g); }\n" +
            "      case 4: { StringBuilder b = new StringBuilder(); b.append(x); return b; }\n" +
            "      case 5: { boolean f = x > 0; return Boolean.valueOf(f); }\n" +
            "      default: return null;\n" +
            "    }\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("ManyReuse", src);
        assertRecompiles("ManyReuse", out);
    }

    @Test
    void tryCatchFinallyWithSideEffectCoalescesAndRecompiles() throws Exception {
        // javac inlines the finally body (num += 2) onto every exit path (normal try completion,
        // catch completion, and a synthetic catch-all rethrow). The recovery must coalesce those
        // copies into one finally clause and keep `num` as a single variable. Previously the slot
        // fragmented across the exception handler blocks (which have no normal predecessors), which
        // folded the finally into the try, emitted an empty finally, and duplicated the update.
        String src =
            "public class FinallyFx {\n" +
            "  static int m() {\n" +
            "    int num = 0;\n" +
            "    try { num = Integer.parseInt(\"123\"); }\n" +
            "    catch (Exception ex) { ex.printStackTrace(); }\n" +
            "    finally { num += 2; }\n" +
            "    return num;\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("FinallyFx", src);
        assertRecompiles("FinallyFx", out);
        assertFalse(out.contains("$pc$") || out.contains("$dispatch$"),
            "a flat try/catch/finally must not become a dispatch loop:\n" + out);
        assertFalse(out.matches("(?s).*finally\\s*\\{\\s*\\}.*"),
            "the finally clause must not be empty (the update was lost):\n" + out);
        int plusTwo = countOccurrences(out, "+ 2");
        assertEquals(1, plusTwo, "the finally update must appear exactly once, was " + plusTwo + ":\n" + out);
    }

    @Test
    void readModifyWriteStaysOneVariableAndRecompiles() throws Exception {
        // A read-modify-write chain (n = n + 2; n += 3) is one source variable. The slot partition
        // must not fragment it into separate def-use webs (which would emit out-of-scope references
        // like `n_1 = n + 2`).
        String src =
            "public class Rmw {\n" +
            "  static int m(int x) {\n" +
            "    int n = x;\n" +
            "    n = n + 2;\n" +
            "    n += 3;\n" +
            "    return n;\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("Rmw", src);
        assertRecompiles("Rmw", out);
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private String roundTrip(String className, String src) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeCompiler(compiler);
        Path dir = Files.createTempDirectory("yabr-reuse");
        try {
            Path srcFile = dir.resolve(className + ".java");
            Files.writeString(srcFile, src);
            int rc = compiler.run(null, null, null, "-g", "-d", dir.toString(), srcFile.toString());
            assertEquals(0, rc, "javac of source fixture failed");

            byte[] bytes = Files.readAllBytes(dir.resolve(className + ".class"));
            return new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        } finally {
            deleteTree(dir);
        }
    }

    private void assertRecompiles(String className, String decompiled) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeCompiler(compiler);
        Path dir = Files.createTempDirectory("yabr-recompile");
        try {
            Path srcFile = dir.resolve(className + ".java");
            Files.writeString(srcFile, decompiled);
            int rc = compiler.run(null, null, null, "-d", dir.toString(), srcFile.toString());
            assertEquals(0, rc, "decompiled output did not recompile:\n" + decompiled);
        } finally {
            deleteTree(dir);
        }
    }

    private void assumeCompiler(JavaCompiler compiler) {
        if (compiler == null) {
            throw new org.opentest4j.TestAbortedException("no system Java compiler available");
        }
    }

    private void deleteTree(Path dir) throws Exception {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }
}
