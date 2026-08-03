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

    @Test
    void aTailSynchronizedBlockDropsItsRedundantReturn() throws Exception {
        Map<String, ClassFile> loaded = compileAll("SyncTail",
                "import java.util.Iterator;",
                "public class SyncTail {",
                "    private final java.util.List<String> names = new java.util.ArrayList<>();",
                "    public void addAll(Iterable<String> more) {",
                "        synchronized (names) {",
                "            Iterator<String> it = more.iterator();",
                "            while (it.hasNext()) {",
                "                names.add(it.next());",
                "            }",
                "        }",
                "    }",
                "    public static int check() throws Exception {",
                "        SyncTail t = new SyncTail();",
                "        t.addAll(java.util.Arrays.asList(\"a\", \"b\"));",
                "        java.lang.reflect.Field f = SyncTail.class.getDeclaredField(\"names\");",
                "        f.setAccessible(true);",
                "        return ((java.util.List) f.get(t)).size();",
                "    }",
                "}");
        ClassFile cf = loaded.get("SyncTail");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals(2, original, "the fixture itself must add both entries under the lock");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "SyncTail"), "d1 recompiles");
        // The relowered layout routes the sync block's normal exit through a shared trailing return;
        // recovery surfaced it as an explicit `return;` inside the tail synchronized block, which is
        // implicit there exactly as it is at the method's own end.
        String d2 = ClassDecompiler.decompile(cf);
        assertEquals(d1, d2, "the tail synchronized block is a fixed point without a surfaced return");
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aValueReturningTailSynthesizesATypedReturn() throws Exception {
        Map<String, ClassFile> loaded = compileAll("GuardTail",
                "public class GuardTail {",
                "    static boolean flag(int k) {",
                "        return k > 0;",
                "    }",
                "    public static String check() {",
                "        return flag(1) + \"|\" + flag(-1);",
                "    }",
                "}");
        ClassFile cf = loaded.get("GuardTail");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("true|false", original, "the fixture itself must branch both ways");

        // Recovered guard chains can cover every real path yet leave the method's textual tail open
        // (`if (k > 0) return true; if (k <= 0) return false;`). Lowering that tail as a bare void
        // return contradicts the descriptor and fails verification; it must be a typed default.
        String guardTail = String.join("\n",
                "public class GuardTail {",
                "    static boolean flag(int k) {",
                "        if (k > 0) {",
                "            return true;",
                "        }",
                "        if (k <= 0) {",
                "            return false;",
                "        }",
                "    }",
                "    public static String check() {",
                "        return flag(1) + \"|\" + flag(-1);",
                "    }",
                "}");
        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        assertTrue(TestUtils.recompileSource(cf, pool, guardTail, "GuardTail"), "the guard-tail form recompiles");
        assertTrue(TestUtils.verifies(cf, pool), "the synthesized tail verifies");
        org.junit.jupiter.api.Assertions.assertFalse(TestUtils.hasControlFlowDrop(cf, pool),
                "a value-returning method's synthesized tail must match its descriptor");
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aValueGuardKeepsItsBodyFormAcrossRelowering() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Orient",
                "public class Orient {",
                "    Object result;",
                "    boolean flagged;",
                "    boolean cancel() {",
                "        if (this.result == null) {",
                "            this.flagged = true;",
                "            return true;",
                "        }",
                "        return false;",
                "    }",
                "    public static String check() {",
                "        Orient a = new Orient();",
                "        boolean empty = a.cancel();",
                "        a.result = \"r\";",
                "        return empty + \"|\" + a.cancel() + \"|\" + a.flagged;",
                "    }",
                "}");
        ClassFile cf = loaded.get("Orient");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("true|false|true", original, "the fixture itself must take both arms");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(d1.contains("== null"), "d1 keeps the source's positive body form:\n" + d1);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Orient"), "d1 recompiles");
        // The relowered layout recovers as the inverted guard (`if (result != null) return false;`);
        // the orientation canon flips it back to the positive body form so the round trip converges.
        // (check()'s local keeps a layout-derived name, the separate known naming family - so the
        // assertion reads the oriented method, not the whole text.)
        String d2 = ClassDecompiler.decompile(cf);
        assertTrue(d2.contains("== null") && !d2.contains("!= null"),
                "d2 recovers the positive body form, not the inverted guard:\n" + d2);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aComplementGuardAfterAnExitedGuardUnwraps() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Unguard",
                "import java.util.List;",
                "public class Unguard {",
                "    static String name(int t) {",
                "        return t > 0 ? \"nm\" : null;",
                "    }",
                "    static boolean supports(List<String> caps, int type) {",
                "        if (type == 2 && !caps.contains(\"array\")) {",
                "            return false;",
                "        }",
                "        String s = name(type);",
                "        if (s == null) {",
                "            return true;",
                "        }",
                "        switch (s.length()) {",
                "            case 1:",
                "                return caps.contains(\"a\");",
                "            case 2:",
                "                return caps.contains(\"b\");",
                "            default:",
                "                return true;",
                "        }",
                "    }",
                "    public static String check() {",
                "        List<String> caps = java.util.Arrays.asList(\"array\", \"a\");",
                "        return supports(caps, 2) + \"|\" + supports(java.util.Arrays.asList(), 2)",
                "                + \"|\" + supports(caps, -1) + \"|\" + supports(caps, 3);",
                "    }",
                "}");
        ClassFile cf = loaded.get("Unguard");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("false|false|true|false", original, "the fixture itself must take every path");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        // The region after the early-exit guard used to recover wrapped in the guard's COMPLEMENT
        // (`if (type != 2 || caps.contains("array")) {...}`) - a fictional second evaluation the
        // bytecode never performs, and a textual tail javac rejects as a missing return.
        String d1 = ClassDecompiler.decompile(cf);
        org.junit.jupiter.api.Assertions.assertFalse(d1.contains("type != 2"),
                "the implied complement guard must not be re-emitted:\n" + d1);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Unguard"), "d1 recompiles");
        assertEquals(d1, ClassDecompiler.decompile(cf), "the unguarded form is a fixed point");
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aTypedWrapRethrowCatchIsNotFinallyScaffolding() throws Exception {
        Map<String, ClassFile> loaded = compileAll("WrapCatch",
                "public class WrapCatch {",
                "    int size;",
                "    WrapCatch(String name, int size) throws Exception {",
                "        if (name == null || size <= 0) {",
                "            throw new Exception(\"bad args\");",
                "        }",
                "        try {",
                "            this.size = Integer.parseInt(name) + size;",
                "        }",
                "        catch (NumberFormatException e) {",
                "            throw new Exception(\"bad name: \" + name);",
                "        }",
                "    }",
                "    public static String check() {",
                "        StringBuilder sb = new StringBuilder();",
                "        try {",
                "            sb.append(new WrapCatch(\"7\", 3).size);",
                "        } catch (Exception e) {",
                "            sb.append(e.getMessage());",
                "        }",
                "        try {",
                "            new WrapCatch(null, 3);",
                "        } catch (Exception e) {",
                "            sb.append('|').append(e.getMessage());",
                "        }",
                "        try {",
                "            new WrapCatch(\"x\", 3);",
                "        } catch (Exception e) {",
                "            sb.append('|').append(e.getMessage());",
                "        }",
                "        return sb.toString();",
                "    }",
                "}");
        ClassFile cf = loaded.get("WrapCatch");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("10|bad args|bad name: x", original, "the fixture itself must take every path");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "WrapCatch"), "d1 recompiles");
        // The relowered layout parks the wrapped exception in a slot before its throw; the typed
        // wrap-rethrow catch must still read as a USER clause, not finally scaffolding - the
        // misclassification structured a phantom finally node, the region then declined, and the
        // constructor recovered as its unconditional guard throw alone.
        String d2 = ClassDecompiler.decompile(cf);
        assertTrue(d2.contains("catch (NumberFormatException"),
                "the typed catch survives the round trip:\n" + d2);
        assertTrue(d2.contains("parseInt"), "the try body survives the round trip:\n" + d2);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aTrySpilledReturnFoldsBackIntoTheTry() {
        // The modern-javac layout parks a returned value in a slot so the return sits outside the
        // protected range; recovery then renders `try { T x = expr; } catch { throw } return x;`.
        // The simplifier folds the spill back to the source's `try { return expr; }` form. (javac 11
        // keeps the return in-range, so this shape is only constructible directly.)
        com.tonic.analysis.source.ast.type.SourceType obj =
                new com.tonic.analysis.source.ast.type.ReferenceSourceType("java/lang/Object");
        com.tonic.analysis.source.ast.stmt.VarDeclStmt decl =
                new com.tonic.analysis.source.ast.stmt.VarDeclStmt(obj, "result",
                        com.tonic.analysis.source.ast.expr.LiteralExpr.ofInt(7));
        com.tonic.analysis.source.ast.stmt.BlockStmt tryBlock =
                new com.tonic.analysis.source.ast.stmt.BlockStmt(
                        new java.util.ArrayList<>(java.util.List.of(
                                (com.tonic.analysis.source.ast.stmt.Statement) decl)));
        com.tonic.analysis.source.ast.stmt.BlockStmt catchBody =
                new com.tonic.analysis.source.ast.stmt.BlockStmt(new java.util.ArrayList<>(java.util.List.of(
                        (com.tonic.analysis.source.ast.stmt.Statement) new com.tonic.analysis.source.ast.stmt.ThrowStmt(
                                new com.tonic.analysis.source.ast.expr.VarRefExpr("e", obj)))));
        com.tonic.analysis.source.ast.stmt.CatchClause clause =
                new com.tonic.analysis.source.ast.stmt.CatchClause(
                        java.util.List.of((com.tonic.analysis.source.ast.type.SourceType)
                                new com.tonic.analysis.source.ast.type.ReferenceSourceType("java/lang/Exception")),
                        "e", catchBody);
        com.tonic.analysis.source.ast.stmt.TryCatchStmt tryCatch =
                new com.tonic.analysis.source.ast.stmt.TryCatchStmt(tryBlock,
                        new java.util.ArrayList<>(java.util.List.of(clause)), null);
        com.tonic.analysis.source.ast.stmt.ReturnStmt ret =
                new com.tonic.analysis.source.ast.stmt.ReturnStmt(
                        new com.tonic.analysis.source.ast.expr.VarRefExpr("result", obj));
        com.tonic.analysis.source.ast.stmt.BlockStmt body =
                new com.tonic.analysis.source.ast.stmt.BlockStmt(new java.util.ArrayList<>(java.util.List.of(
                        tryCatch, ret)));

        new com.tonic.analysis.source.ast.transform.ControlFlowSimplifier().transform(body);

        assertEquals(1, body.getStatements().size(), "the trailing return folds away");
        com.tonic.analysis.source.ast.stmt.TryCatchStmt folded =
                (com.tonic.analysis.source.ast.stmt.TryCatchStmt) body.getStatements().get(0);
        com.tonic.analysis.source.ast.stmt.Statement last =
                ((com.tonic.analysis.source.ast.stmt.BlockStmt) folded.getTryBlock()).getStatements().get(0);
        assertTrue(last instanceof com.tonic.analysis.source.ast.stmt.ReturnStmt,
                "the spilled declaration becomes the try's own return: " + last);
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
