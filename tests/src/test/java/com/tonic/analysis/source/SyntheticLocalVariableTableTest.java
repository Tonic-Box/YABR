package com.tonic.analysis.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
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

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the synthetic LocalVariableTable built for STRIPPED classes (no debug info): names come from the
 * decompiler's recovery and are keyed to the original bytecode offsets, the bytecode is unchanged (only the
 * attribute is added), and the result round-trips and is well-formed.
 */
class SyntheticLocalVariableTableTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

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

    /** Compiles {@code source} with no debug info, builds the synthetic LVT, attaches it, round-trips, reads it back. */
    private List<Row> synthLvt(String binaryName, String source, String methodName) throws Exception {
        ClassFile cf = compileStripped(binaryName, source);
        MethodEntry method = method(cf, methodName);
        assertNull(findLvt(method.getCodeAttribute()), "fixture must be stripped (no original LVT): " + methodName);

        LocalVariableTableAttribute synthetic = new ClassDecompiler(cf).localVariableTableFor(method);
        assertNotNull(synthetic, "a stripped method with locals should get a synthetic LVT: " + methodName);
        method.getCodeAttribute().getAttributes().add(synthetic);

        ClassFile rt = new ClassFile(new ByteArrayInputStream(cf.write()));
        MethodEntry rtMethod = method(rt, methodName);
        CodeAttribute code = rtMethod.getCodeAttribute();
        LocalVariableTableAttribute table = findLvt(code);
        assertNotNull(table, "LVT must survive write+reparse (plain attribute add, not a structural edit)");

        List<Row> rows = new ArrayList<>();
        for (LocalVariableTableEntry e : table.getLocalVariableTable()) {
            rows.add(new Row(utf8(rt, e.getNameIndex()), utf8(rt, e.getDescriptorIndex()),
                    e.getIndex(), e.getStartPc(), e.getLengthPc()));
        }
        int codeLength = code.getCode().length;
        int maxLocals = code.getMaxLocals();
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
                    assertFalse(a.startPc < b.startPc + b.length && b.startPc < a.startPc + a.length,
                            "same-slot entries must not overlap: " + a + " vs " + b);
                }
            }
        }
        return rows;
    }

    private static ClassFile compileStripped(String binaryName, String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new org.opentest4j.TestAbortedException("no system Java compiler");
        }
        String simple = binaryName.substring(binaryName.lastIndexOf('/') + 1);
        Path dir = Files.createTempDirectory("yabr-synthlvt");
        try {
            Path src = dir.resolve(simple + ".java");
            Files.writeString(src, source);
            int rc = compiler.run(null, null, null, "-g:none", "-d", dir.toString(), src.toString());
            assertEquals(0, rc, "javac (no debug) must succeed");
            byte[] bytes = Files.readAllBytes(dir.resolve(binaryName + ".class"));
            return new ClassFile(new ByteArrayInputStream(bytes));
        } finally {
            try (Stream<Path> w = Files.walk(dir)) {
                w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static MethodEntry method(ClassFile cf, String name) {
        return cf.getMethods().stream().filter(m -> m.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("method not found: " + name));
    }

    private static LocalVariableTableAttribute findLvt(CodeAttribute code) {
        if (code == null) {
            return null;
        }
        for (Attribute a : code.getAttributes()) {
            if (a instanceof LocalVariableTableAttribute) {
                return (LocalVariableTableAttribute) a;
            }
        }
        return null;
    }

    private static String utf8(ClassFile cf, int index) {
        Object item = cf.getConstPool().getItem(index);
        return item instanceof Utf8Item ? ((Utf8Item) item).getValue() : null;
    }

    private static Row bySlot(List<Row> rows, int slot) {
        return rows.stream().filter(r -> r.slot == slot).findFirst().orElse(null);
    }

    // ---- cases -------------------------------------------------------------------------------------

    @Test
    void staticParamsGetWholeMethodEntries() throws Exception {
        List<Row> rows = synthLvt("t/A", "package t; public class A {"
                + " public static int f(int a, long n, String s){ return a; } }", "f");
        assertEquals("I", bySlot(rows, 0).desc, "first int param: " + rows);
        assertEquals("J", bySlot(rows, 1).desc, "long param at slot 1 (2 slots): " + rows);
        assertEquals("Ljava/lang/String;", bySlot(rows, 3).desc, "String param after the long: " + rows);
        // Parameters span the whole method.
        assertEquals(0, bySlot(rows, 0).startPc, "param scope starts at 0: " + rows);
    }

    @Test
    void instanceMethodHasThisAtSlotZero() throws Exception {
        List<Row> rows = synthLvt("t/B", "package t; public class B {"
                + " public int g(int p){ return p; } }", "g");
        Row self = bySlot(rows, 0);
        assertNotNull(self, "slot 0 entry: " + rows);
        assertEquals("Lt/B;", self.desc, "this descriptor: " + rows);
        assertEquals(0, self.startPc);
        assertNotNull(bySlot(rows, 1), "the parameter at slot 1: " + rows);
    }

    @Test
    void bodyLocalGetsAnEntryWithRecoveredName() throws Exception {
        // A loop forces a real body local in a slot; the recovered name is keyed to original offsets.
        List<Row> rows = synthLvt("t/C", "package t; public class C {"
                + " public static int f(int n){ int s = 0; for (int i = 0; i < n; i++) { s += i; } return s; } }", "f");
        // Param n + at least one body-local slot beyond the params.
        assertNotNull(bySlot(rows, 0), "param n at slot 0: " + rows);
        assertTrue(rows.stream().anyMatch(r -> r.slot >= 1),
                "at least one body local in a slot beyond the param: " + rows);
        // Every recovered name is non-empty.
        assertTrue(rows.stream().allMatch(r -> r.name != null && !r.name.isEmpty()), rows.toString());
    }

    @Test
    void reusedSlotStaysWithinBoundsAndDisjoint() throws Exception {
        // Two disjoint loops; whatever the recovery names them, the invariants (bounds, no same-slot overlap)
        // are enforced by synthLvt(); this asserts the builder produced a usable table.
        List<Row> rows = synthLvt("t/D", "package t; public class D {"
                + " public static int f(int n){ int t = 0;"
                + " for (int a = 0; a < n; a++) { t += a; }"
                + " for (int b = 0; b < n; b++) { t += b * 2; }"
                + " return t; } }", "f");
        assertFalse(rows.isEmpty(), "should recover some locals: " + rows);
    }
}
