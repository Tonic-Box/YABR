package com.tonic.analysis.ssa.llvm;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.BytecodeBuilder.Label;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lowers methods built with {@link BytecodeBuilder}, lifted to SSA, and asserts the emitted LLVM IR
 * text covers the computational subset correctly (including the tricky signedness / shift-mask /
 * 3-way-compare cases) and that out-of-subset ops are rejected.
 */
class SsaToLlvmLowererTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    private String lower(ClassFile cf, String name) {
        MethodEntry method = find(cf, name);
        IRMethod ir = TestUtils.liftMethod(method);
        return new LlvmLowering().lower(ir);
    }

    private MethodEntry find(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("method not found: " + name);
    }

    @Test
    void intAdd() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("add", "(II)I")
            .iload(0).iload(1).iadd().ireturn().build();
        String ll = lower(cf, "add");
        assertTrue(ll.contains("define i32 @\""), ll);
        assertTrue(ll.contains("add i32"), ll);
        assertTrue(ll.contains("ret i32"), ll);
    }

    @Test
    void signedDivAndRem() throws IOException {
        ClassFile d = BytecodeBuilder.forClass("T").publicStaticMethod("d", "(II)I")
            .iload(0).iload(1).idiv().ireturn().build();
        assertTrue(lower(d, "d").contains("sdiv i32"));
        ClassFile r = BytecodeBuilder.forClass("T").publicStaticMethod("r", "(II)I")
            .iload(0).iload(1).irem().ireturn().build();
        assertTrue(lower(r, "r").contains("srem i32"));
    }

    @Test
    void shiftsAreMaskedAndUseCorrectOpcode() throws IOException {
        ClassFile u = BytecodeBuilder.forClass("T").publicStaticMethod("u", "(II)I")
            .iload(0).iload(1).iushr().ireturn().build();
        String lu = lower(u, "u");
        assertTrue(lu.contains("and i32"), lu);
        assertTrue(lu.contains(", 31"), lu);
        assertTrue(lu.contains("lshr i32"), lu);

        ClassFile s = BytecodeBuilder.forClass("T").publicStaticMethod("s", "(II)I")
            .iload(0).iload(1).ishr().ireturn().build();
        assertTrue(lower(s, "s").contains("ashr i32"));
    }

    @Test
    void loopWithPhiAndBranch() throws IOException {
        // int sum(int n) { int s=0; for (int i=0; i<n; i++) s+=i; return s; }
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("T").publicStaticMethod("sum", "(I)I");
        Label head = mb.newLabel();
        Label end = mb.newLabel();
        ClassFile cf = mb
            .iconst(0).istore(1)
            .iconst(0).istore(2)
            .label(head)
            .iload(2).iload(0).if_icmpge(end)
            .iload(1).iload(2).iadd().istore(1)
            .iinc(2, 1)
            .goto_(head)
            .label(end)
            .iload(1).ireturn()
            .build();
        String ll = lower(cf, "sum");
        assertTrue(ll.contains("phi i32"), ll);
        assertTrue(ll.contains("icmp "), ll);
        assertTrue(ll.contains("br i1 "), ll);
        assertTrue(ll.contains("br label %B"), ll);
    }

    @Test
    void switchStatement() throws IOException {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("T").publicStaticMethod("sw", "(I)I");
        Label c0 = mb.newLabel();
        Label c1 = mb.newLabel();
        Label dflt = mb.newLabel();
        Map<Integer, Label> cases = new LinkedHashMap<>();
        cases.put(0, c0);
        cases.put(1, c1);
        ClassFile cf = mb
            .iload(0).tableswitch(0, 1, cases, dflt)
            .label(c0).iconst(10).ireturn()
            .label(c1).iconst(20).ireturn()
            .label(dflt).iconst(-1).ireturn()
            .build();
        String ll = lower(cf, "sw");
        assertTrue(ll.contains("switch i32"), ll);
        assertTrue(ll.contains("label %B"), ll);
    }

    @Test
    void longAndDoubleArithmetic() throws IOException {
        ClassFile la = BytecodeBuilder.forClass("T").publicStaticMethod("la", "(JJ)J")
            .lload(0).lload(2).ladd().lreturn().build();
        assertTrue(lower(la, "la").contains("add i64"));
        ClassFile da = BytecodeBuilder.forClass("T").publicStaticMethod("da", "(DD)D")
            .dload(0).dload(2).dadd().dreturn().build();
        assertTrue(lower(da, "da").contains("fadd double"));
    }

    @Test
    void longCompareExpandsToSelects() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("c", "(JJ)I")
            .lload(0).lload(2).lcmp().ireturn().build();
        String ll = lower(cf, "c");
        assertTrue(ll.contains("icmp slt i64"), ll);
        assertTrue(ll.contains("icmp sgt i64"), ll);
        assertTrue(ll.contains("select i1 "), ll);
        assertTrue(ll.contains("i32 -1"), ll);
    }

    @Test
    void staticCallEmitsCallAndDeclare() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("c", "(I)I")
            .iload(0).invokestatic("Other", "helper", "(I)I").ireturn().build();
        String ll = lower(cf, "c");
        assertTrue(ll.contains("call i32 @\""), ll);
        assertTrue(ll.contains("declare i32 @\""), ll);
    }

    @Test
    void unsupportedOpIsRejected() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("len", "(I)I")
            .iload(0).newarray(10).arraylength().ireturn().build();
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
            () -> lower(cf, "len"));
        assertTrue(e.getMessage().startsWith("LLVM lowering:"), e.getMessage());
    }
}
