package com.tonic.analysis.source;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the lowering pipeline emits a well-formed LocalVariableTable carrying real source names,
 * types, slots, and scopes. Each case compiles source → bytecode (LVT on by default), forces JVM
 * verification, round-trips the class, and inspects the parsed-back entries. Body locals get an entry only
 * when the allocator gives them a slot (the straight-line path keeps simple locals on the operand stack), so
 * the body-local cases use loops/branches that force slot allocation.
 */
class LocalVariableTableEmissionTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    /** One parsed-back LVT entry, names/descriptors resolved through the round-tripped constant pool. */
    private static final class Row {
        final String name;
        final String desc;
        final int slot;
        final int startPc;
        final int length;

        Row(String name, String desc, int slot, int startPc, int length) {
            this.name = name;
            this.desc = desc;
            this.slot = slot;
            this.startPc = startPc;
            this.length = length;
        }

        @Override
        public String toString() {
            return name + ":" + desc + "@" + slot + "[" + startPc + "," + (startPc + length) + ")";
        }
    }

    private byte[] lastCode;
    private ClassFile lastClass;

    private List<Row> lvt(String internalName, String source, String methodName) throws Exception {
        ClassFile cf = TestUtils.compileSource(source, internalName);
        TestUtils.linkAndVerify(cf);
        ClassFile rt = TestUtils.roundTrip(cf);
        lastClass = rt;
        MethodEntry method = rt.getMethods().stream()
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow(() -> new AssertionError("method not found: " + methodName));
        CodeAttribute code = method.getCodeAttribute();
        assertNotNull(code, "method has Code");
        LocalVariableTableAttribute table = null;
        for (Attribute a : code.getAttributes()) {
            if (a instanceof LocalVariableTableAttribute) {
                table = (LocalVariableTableAttribute) a;
            }
        }
        List<Row> rows = new ArrayList<>();
        lastCode = code.getCode();
        int codeLength = code.getCode().length;
        int maxLocals = code.getMaxLocals();
        if (table != null) {
            for (LocalVariableTableEntry e : table.getLocalVariableTable()) {
                String name = utf8(rt, e.getNameIndex());
                String desc = utf8(rt, e.getDescriptorIndex());
                rows.add(new Row(name, desc, e.getIndex(), e.getStartPc(), e.getLengthPc()));
            }
        }
        // Structural invariants hold for every emitted entry (asserted centrally so every case is covered).
        for (Row r : rows) {
            assertNotNull(r.name, "name resolves: " + rows);
            assertNotNull(r.desc, "descriptor resolves: " + rows);
            assertTrue(r.slot >= 0 && r.slot < maxLocals, "slot in range: " + r + " maxLocals=" + maxLocals);
            assertTrue(r.startPc >= 0 && r.startPc + r.length <= codeLength,
                    "scope in bounds: " + r + " codeLength=" + codeLength);
        }
        for (int i = 0; i < rows.size(); i++) {
            for (int j = i + 1; j < rows.size(); j++) {
                Row a = rows.get(i), b = rows.get(j);
                if (a.slot == b.slot) {
                    boolean overlap = a.startPc < b.startPc + b.length && b.startPc < a.startPc + a.length;
                    assertFalse(overlap, "same-slot entries must not overlap: " + a + " vs " + b);
                }
            }
        }
        return rows;
    }

    private static String utf8(ClassFile cf, int index) {
        Object item = cf.getConstPool().getItem(index);
        return item instanceof Utf8Item ? ((Utf8Item) item).getValue() : null;
    }

    private static Row byName(List<Row> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.name)).findFirst().orElse(null);
    }

    private static List<String> names(List<Row> rows) {
        List<String> n = new ArrayList<>();
        for (Row r : rows) {
            n.add(r.name);
        }
        return n;
    }

    // ---- cases -------------------------------------------------------------------------------------

    @Test
    void parametersGetEntriesWithRealNamesAndDescriptors() throws Exception {
        List<Row> rows = lvt("t/P", "package t; public class P {"
                + " public static int f(int a, String s, int[] arr){ return a; } }", "f");
        assertEquals("I", byName(rows, "a").desc);
        assertEquals("Ljava/lang/String;", byName(rows, "s").desc);
        assertEquals("[I", byName(rows, "arr").desc);
        assertEquals(0, byName(rows, "a").slot);
        assertEquals(1, byName(rows, "s").slot);
        assertEquals(2, byName(rows, "arr").slot);
    }

    @Test
    void instanceMethodHasThisAtSlotZero() throws Exception {
        List<Row> rows = lvt("t/I", "package t; public class I {"
                + " public int g(int p){ return p; } }", "g");
        Row self = byName(rows, "this");
        assertNotNull(self, "this entry present: " + rows);
        assertEquals(0, self.slot);
        assertEquals("Lt/I;", self.desc);
        assertEquals(0, self.startPc);
        assertEquals(1, byName(rows, "p").slot);
    }

    @Test
    void staticMethodHasNoThisFirstParamAtSlotZero() throws Exception {
        List<Row> rows = lvt("t/S", "package t; public class S {"
                + " public static int f(int a, int b){ return a + b; } }", "f");
        assertFalse(names(rows).contains("this"), "no this for static: " + rows);
        assertEquals(0, byName(rows, "a").slot);
        assertEquals(1, byName(rows, "b").slot);
    }

    @Test
    void longDoubleParamsUseLowSlotSingleEntry() throws Exception {
        List<Row> rows = lvt("t/W", "package t; public class W {"
                + " public static long f(long n, double d){ return n; } }", "f");
        assertEquals("J", byName(rows, "n").desc);
        assertEquals("D", byName(rows, "d").desc);
        assertEquals(0, byName(rows, "n").slot);
        assertEquals(2, byName(rows, "d").slot, "double param starts after the 2-slot long");
        assertEquals(2, rows.stream().filter(r -> "n".equals(r.name) || "d".equals(r.name)).count(),
                "one entry per wide param: " + rows);
    }

    @Test
    void unusedParameterStillGetsEntry() throws Exception {
        List<Row> rows = lvt("t/U", "package t; public class U {"
                + " public static int f(int a, int unused){ return a; } }", "f");
        assertNotNull(byName(rows, "unused"), "unused param still has an entry: " + rows);
    }

    @Test
    void loopBodyLocalsGetEntries() throws Exception {
        List<Row> rows = lvt("t/L", "package t; public class L {"
                + " public static int f(int n){ int s = 0; for (int i = 0; i < n; i++) { s += i; } return s; } }", "f");
        assertNotNull(byName(rows, "n"), "param n: " + rows);
        assertNotNull(byName(rows, "s"), "accumulator s: " + rows);
        assertNotNull(byName(rows, "i"), "induction i: " + rows);
        assertEquals("I", byName(rows, "i").desc);
    }

    @Test
    void reusedSlotAcrossDisjointBlocksGivesDisjointEntries() throws Exception {
        // Two locals in disjoint loop bodies; the allocator may reuse a slot -> entries must stay disjoint.
        List<Row> rows = lvt("t/R", "package t; public class R {"
                + " public static int f(int n){ int t = 0;"
                + " for (int a = 0; a < n; a++) { t += a * 2; }"
                + " for (int b = 0; b < n; b++) { t += b * 3; }"
                + " return t; } }", "f");
        assertNotNull(byName(rows, "a"), "first-loop local a: " + rows);
        assertNotNull(byName(rows, "b"), "second-loop local b: " + rows);
        // (overlap invariant for same-slot entries is enforced centrally in lvt())
    }

    @Test
    void shadowedNamesInDisjointBlocksAreDistinctEntries() throws Exception {
        List<Row> rows = lvt("t/Sh", "package t; public class Sh {"
                + " public static int f(int n){ int r = 0;"
                + " for (int x = 0; x < n; x++) { r += x; }"
                + " for (int x = 0; x < n; x++) { r += x * 2; }"
                + " return r; } }", "f");
        long xCount = rows.stream().filter(r -> "x".equals(r.name)).count();
        assertEquals(2, xCount, "each disjoint x is its own entry: " + rows);
    }

    @Test
    void catchExceptionVariableGetsEntry() throws Exception {
        List<Row> rows = lvt("t/C", "package t; public class C {"
                + " public static int f(int n){ try { return 100 / n; } catch (ArithmeticException e) { return -1; } } }", "f");
        assertNotNull(byName(rows, "e"), "catch variable e: " + rows);
        assertEquals("Ljava/lang/ArithmeticException;", byName(rows, "e").desc);
    }

    @Test
    void tempsOnlyMethodEmitsOnlyParamEntries() throws Exception {
        List<Row> rows = lvt("t/T", "package t; public class T {"
                + " public static int f(int a){ return a * a + 1; } }", "f");
        assertEquals(1, rows.size(), "only the parameter, no temp entries: " + rows);
        assertEquals("a", rows.get(0).name);
    }

    @Test
    void aStackResidentLocalHasNoPhantomEntry() throws Exception {
        // The single-use local stays on the operand stack - the emitted code never writes a body
        // slot, and a truthful table must not name one.
        List<Row> rows = lvt("t/Ph", "package t; public class Ph {"
                + " public static int f(int a){ int doubled = a * 2; return doubled + 1; } }", "f");
        assertEquals(1, rows.size(), "no entry for a slot nothing writes: " + rows);
        assertEquals("a", rows.get(0).name);
    }

    @Test
    void bodyLocalRangeOpensAfterItsInitializingStore() throws Exception {
        List<Row> rows = lvt("t/Sp", "package t; public class Sp {"
                + " public static int f(int n){ int s = 7; if (n > 0) { s = s + n; } return s; } }", "f");
        Row s = byName(rows, "s");
        assertNotNull(s, "s present: " + rows);
        assertTrue(s.startPc > 0, "range opens after the store, not at pc 0: " + s);
        assertTrue(endsWithStoreTo(lastCode, s.startPc, s.slot),
                "the bytes before start_pc are the initializing store: " + s);
    }

    @Test
    void anAliasBetweenNamedLocalsKeepsBothEntries() throws Exception {
        List<Row> rows = lvt("t/Al", "package t; public class Al {"
                + " public static int f(int a, int n){ int b = a; if (n > 0) { b = b + 1; } return b; } }", "f");
        assertNotNull(byName(rows, "a"), "source variable a: " + rows);
        assertNotNull(byName(rows, "b"), "aliasing variable b keeps its own entry: " + rows);
    }

    @Test
    void aGenericLocalGetsASynthesizedTypeTableEntry() throws Exception {
        lvt("t/G", "package t; import java.util.List; import java.util.ArrayList; public class G {"
                + " public static int f(int n){ List<String> xs = new ArrayList<String>();"
                + " if (n > 0) { xs.add(\"x\"); } return xs.size(); } }", "f");
        MethodEntry method = lastClass.getMethods().stream()
                .filter(m -> m.getName().equals("f")).findFirst().orElseThrow();
        com.tonic.parser.attribute.LocalVariableTypeTableAttribute lvtt = null;
        for (Attribute a : method.getCodeAttribute().getAttributes()) {
            if (a instanceof com.tonic.parser.attribute.LocalVariableTypeTableAttribute) {
                lvtt = (com.tonic.parser.attribute.LocalVariableTypeTableAttribute) a;
            }
        }
        assertNotNull(lvtt, "a generic declaration synthesizes a LocalVariableTypeTable");
        boolean found = false;
        for (com.tonic.parser.attribute.table.LocalVariableTypeTableEntry e : lvtt.getLocalVariableTypeTable()) {
            if ("xs".equals(utf8(lastClass, e.getNameIndex()))
                    && "Ljava/util/List<Ljava/lang/String;>;".equals(utf8(lastClass, e.getSignatureIndex()))) {
                found = true;
            }
        }
        assertTrue(found, "xs carries its List<String> signature");
    }

    /** Whether the code bytes immediately before {@code pc} are a store to {@code slot}. */
    private static boolean endsWithStoreTo(byte[] code, int pc, int slot) {
        if (pc >= 1 && slot <= 3) {
            int b = code[pc - 1] & 0xff;
            if (b == 0x3b + slot || b == 0x4b + slot || b == 0x43 + slot
                    || b == 0x3f + slot || b == 0x47 + slot) {
                return true;
            }
        }
        if (pc >= 2) {
            int op = code[pc - 2] & 0xff;
            int idx = code[pc - 1] & 0xff;
            return idx == slot && (op == 0x36 || op == 0x37 || op == 0x38 || op == 0x39 || op == 0x3a);
        }
        return false;
    }
}
