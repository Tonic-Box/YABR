package com.tonic.analysis.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Recovery tails that emitted references to variables that no longer exist:
 * <ul>
 * <li>A declaration merge that MOVES the merged value's evaluation above intervening statements is
 *     unsound when one of them declares a name the value reads
 *     ({@code float s = 0; int x = a[0]; s = s + t[x];} must not become
 *     {@code float s = 0 + t[x]; int x = a[0];}).</li>
 * <li>A ternary collapse discards its arm blocks, so an arm-produced allocation must inline as the
 *     allocation itself, never as the discarded temp's name.</li>
 * <li>A {@code synchronized} lock materialized into the scaffolding slot the sync recovery consumes
 *     must recover as the lock expression, not the never-emitted slot name.</li>
 * </ul>
 */
class RecoveryTailsTest {

    @Test
    void aDeclarationMergeRespectsInterveningDeclarations() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Accum",
                "public class Accum {",
                "    static float total(float[] t, int[] coords) {",
                "        float sum = 0;",
                "        int x = coords[0];",
                "        sum = sum + t[x];",
                "        sum = sum + t[x + 1];",
                "        return sum;",
                "    }",
                "    public static float check() {",
                "        return total(new float[] {1f, 2f, 3f}, new int[] {1});",
                "    }",
                "}");
        ClassFile cf = loaded.get("Accum");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals(5.0f, original, "the fixture itself must sum both elements");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Accum"),
                "x must be declared before anything reads it:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aTernaryArmAllocationInlines() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Fallback",
                "public class Fallback {",
                "    StringBuilder held;",
                "    String describe() {",
                "        return (this.held != null ? this.held.reverse() : new StringBuilder(\"empty\")).toString();",
                "    }",
                "    public static String check() {",
                "        Fallback f = new Fallback();",
                "        String a = f.describe();",
                "        f.held = new StringBuilder(\"ab\");",
                "        return a + \"|\" + f.describe();",
                "    }",
                "}");
        ClassFile cf = loaded.get("Fallback");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("empty|ba", original, "the fixture itself must take both arms");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Fallback"),
                "the allocation arm must inline, not reference a discarded temp:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aSynchronizedLockRecoversItsExpression() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Locked",
                "public class Locked {",
                "    private final Object gate = new Object();",
                "    private int count;",
                "    int bump() {",
                "        synchronized (this.gate) {",
                "            this.count = this.count + 1;",
                "            return this.count;",
                "        }",
                "    }",
                "    public static int check() {",
                "        Locked l = new Locked();",
                "        l.bump();",
                "        return l.bump();",
                "    }",
                "}");
        ClassFile cf = loaded.get("Locked");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals(2, original, "the fixture itself must count under the lock");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Locked"),
                "the lock must be an expression, not an unemitted slot name:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aSharedExitGuardChainFoldsToOneDisjunction() throws Exception {
        Map<String, ClassFile> loaded = compileAll("EqGuard",
                "public class EqGuard {",
                "    int kind;",
                "    public boolean same(Object obj) {",
                "        if (obj == null || getClass() != obj.getClass()) {",
                "            return false;",
                "        }",
                "        return this.kind == ((EqGuard) obj).kind;",
                "    }",
                "    public static String check() {",
                "        EqGuard a = new EqGuard();",
                "        EqGuard b = new EqGuard();",
                "        b.kind = 1;",
                "        return a.same(a) + \"|\" + a.same(b) + \"|\" + a.same(null) + \"|\" + a.same(\"x\");",
                "    }",
                "}");
        ClassFile cf = loaded.get("EqGuard");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("true|false|false|false", original, "the fixture itself must compare all four ways");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(d1.contains("obj == null || getClass() != obj.getClass()"),
                "the shared-exit guard chain folds back to the source's single disjunction: " + d1);
        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "EqGuard"), "d1 recompiles");
        assertEquals(d1, ClassDecompiler.decompile(cf), "the folded form is a fixed point");
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    private static Map<String, ClassFile> compileAll(String primary, String... lines) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory(primary.toLowerCase());
        Path src = dir.resolve(primary + ".java");
        Files.writeString(src, String.join(System.lineSeparator(), lines));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        ClassPool pool = new ClassPool();
        Map<String, ClassFile> loaded = new HashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.class")) {
            for (Path p : stream) {
                ClassFile cf = pool.loadClass(Files.readAllBytes(p));
                loaded.put(cf.getClassName(), cf);
            }
        }
        return loaded;
    }
}
