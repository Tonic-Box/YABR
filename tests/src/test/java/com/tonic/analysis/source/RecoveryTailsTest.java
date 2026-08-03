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

    @Test
    void aStoreCarriedCallKeepsItsOrderAcrossAnEffect() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Carried",
                "public class Carried {",
                "    static StringBuilder log = new StringBuilder();",
                "    static String get(String b) { log.append(\"get;\"); return b; }",
                "    static String get2(String b) { log.append(\"get2;\"); return b; }",
                "    static void cancel(String b) { log.append(\"cancel;\"); }",
                "    static String run(String bone, boolean flag) {",
                "        String node;",
                "        if (flag) {",
                "            node = get(bone);",
                "            cancel(bone);",
                "        }",
                "        else {",
                "            node = get2(bone);",
                "            cancel(bone);",
                "        }",
                "        return node;",
                "    }",
                "    public static String check() {",
                "        log = new StringBuilder();",
                "        run(\"b\", true);",
                "        run(\"b\", false);",
                "        return log.toString();",
                "    }",
                "}");
        ClassFile cf = loaded.get("Carried");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("get;cancel;get2;cancel;", original, "the fixture itself must call get before cancel");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Carried"), "d1 recompiles");
        // The relowered layout merges node at the join and keeps each arm's call result on the
        // stack across cancel(); the decompile of that layout must still print the call before
        // the effect it precedes, not at the later store.
        String d2 = ClassDecompiler.decompile(cf);
        String flat = d2.replaceAll("\\s+", "");
        assertTrue(flat.contains("node=Carried.get(bone);Carried.cancel(bone);"),
                "the then-arm keeps source order:\n" + d2);
        assertTrue(flat.contains("node=Carried.get2(bone);Carried.cancel(bone);"),
                "the else-arm keeps source order:\n" + d2);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void switchCasesKeepTheirLayoutOrderAcrossRelowering() throws Exception {
        Map<String, ClassFile> loaded = compileAll("CaseOrder",
                "public class CaseOrder {",
                "    enum Kind { SPHERE, BOX, OTHER }",
                "    static String pick(Kind k) {",
                "        switch (k) {",
                "            case BOX:",
                "                return \"box\";",
                "            case SPHERE:",
                "                return \"sphere\";",
                "            default:",
                "                return \"other\";",
                "        }",
                "    }",
                "    public static String check() {",
                "        return pick(Kind.BOX) + \"|\" + pick(Kind.SPHERE) + \"|\" + pick(Kind.OTHER);",
                "    }",
                "}");
        ClassFile cf = loaded.get("CaseOrder");
        Object original = loadWith(loaded, cf).getMethod("check").invoke(null);
        assertEquals("box|sphere|other", original, "the fixture itself must dispatch all three ways");

        ClassPool pool = new ClassPool();
        for (ClassFile extra : loaded.values()) {
            pool.loadClass(extra.write());
        }
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(d1.contains("case BOX") && d1.indexOf("case BOX") < d1.indexOf("case SPHERE"),
                "d1 keeps the source's case order (BOX first):\n" + d1);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "CaseOrder"), "d1 recompiles");
        // The relowered switch dispatches on raw ordinals, whose key order is DECLARATION order
        // (SPHERE first); the printed cases must still follow the body layout, which both javac and
        // the re-lowerer carry over from source.
        String d2 = ClassDecompiler.decompile(cf);
        assertTrue(d2.contains("case BOX") && d2.indexOf("case BOX") < d2.indexOf("case SPHERE"),
                "d2 keeps the same case order as d1:\n" + d2);
        assertEquals(original, loadWith(loaded, cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aQualifiedNestedArrayAllocationReferencesTheRealClass() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Holder",
                "public class Holder {",
                "    enum Kind { A, B }",
                "    static Kind[] make() {",
                "        return new Kind[] {Kind.A, Kind.B};",
                "    }",
                "    public static String check() {",
                "        StringBuilder sb = new StringBuilder();",
                "        for (Kind k : make()) {",
                "            sb.append(k);",
                "        }",
                "        return sb.toString();",
                "    }",
                "}");
        ClassFile outer = loaded.get("Holder");
        Object original = loadWith(loaded, outer).getMethod("check").invoke(null);
        assertEquals("AB", original, "the fixture itself must build and iterate the array");

        ClassPool pool = new ClassPool();
        for (ClassFile each : loaded.values()) {
            pool.loadClass(each.write());
        }
        // The DOTTED source form of a nested type in an array allocation - what the decompile of a
        // modern-javac enum's $values() prints - must lower to the real Holder$Kind class, or the
        // allocation references a class that does not exist and make() throws NoClassDefFoundError.
        String qualified = String.join("\n",
                "public class Holder {",
                "    static Holder.Kind[] make() {",
                "        return new Holder.Kind[] {Holder.Kind.A, Holder.Kind.B};",
                "    }",
                "    public static String check() {",
                "        StringBuilder sb = new StringBuilder();",
                "        for (Holder.Kind k : make()) {",
                "            sb.append(k);",
                "        }",
                "        return sb.toString();",
                "    }",
                "}");
        assertTrue(TestUtils.recompileSource(outer, pool, qualified, "Holder"), "the qualified form recompiles");
        assertEquals(original, loadWith(loaded, outer).getMethod("check").invoke(null),
                "make() must load and run after relowering the qualified allocation");
    }

    @Test
    void aDefaultSharingAValueCaseKeepsItsArm() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Shared",
                "public class Shared {",
                "    static int pick(int k) {",
                "        switch (k) {",
                "            case 1:",
                "                return 10;",
                "            case 2:",
                "                return 20;",
                "            case 3:",
                "            default:",
                "                throw new IllegalArgumentException(String.valueOf(k));",
                "        }",
                "    }",
                "    public static String check() {",
                "        StringBuilder sb = new StringBuilder();",
                "        sb.append(pick(1)).append('|').append(pick(2)).append('|');",
                "        try {",
                "            pick(9);",
                "        } catch (IllegalArgumentException e) {",
                "            sb.append(e.getMessage());",
                "        }",
                "        return sb.toString();",
                "    }",
                "}");
        ClassFile cf = loaded.get("Shared");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("10|20|9", original, "the fixture itself must dispatch and throw");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        // The default's target IS the case-3 throw block; dropping the default arm makes the switch
        // relower with a fall-off edge, and a value-returning method's synthesized fall-off return is
        // not verifiable bytecode.
        assertTrue(d1.contains("default:"), "the shared default arm survives:\n" + d1);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Shared"), "d1 recompiles");
        String d2 = ClassDecompiler.decompile(cf);
        assertEquals(d1, d2, "the shared-default switch is a fixed point");
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    /** Defines every fixture class in one loader and returns {@code main}'s Class. */
    private static Class<?> loadWith(Map<String, ClassFile> all, ClassFile main) throws Exception {
        com.tonic.testutil.TestClassLoader loader = new com.tonic.testutil.TestClassLoader();
        Class<?> result = null;
        for (ClassFile each : all.values()) {
            Class<?> c = loader.defineClass(each.getClassName().replace('/', '.'), each.write());
            if (each == main) {
                result = c;
            }
        }
        return result;
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
