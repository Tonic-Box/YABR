package com.tonic.analysis.ssa.llvm.lift;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.llvm.LlvmLowering;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.BytecodeBuilder.Label;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-construct unit tests: lift bytecode to SSA, lower to LLVM text, lift back to SSA, assert
 * structural properties.
 */
class LlvmLifterTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    private IRMethod lowerAndLift(ClassFile cf, String name) {
        MethodEntry method = find(cf, name);
        IRMethod ir = TestUtils.liftMethod(method);
        String ll = new LlvmLowering().lower(ir);
        return new LlvmLifter().lift(ll);
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
    void intAddLiftsToMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("add", "(II)I")
            .iload(0).iload(1).iadd().ireturn().build();
        IRMethod lifted = lowerAndLift(cf, "add");
        assertNotNull(lifted.getEntryBlock());
        assertEquals(PrimitiveType.INT, lifted.getReturnType());
        assertTrue(lifted.isStatic());
        assertEquals(2, lifted.getParameters().size());
        List<IRBlock> blocks = lifted.getBlocksInOrder();
        assertFalse(blocks.isEmpty());
        boolean hasAdd = false;
        for (IRBlock b : blocks) {
            for (IRInstruction instr : b.getInstructions()) {
                if (instr instanceof BinaryOpInstruction
                        && ((BinaryOpInstruction) instr).getOp() == BinaryOp.ADD) {
                    hasAdd = true;
                    break;
                }
            }
        }
        assertTrue(hasAdd, "expected ADD instruction");
    }

    @Test
    void longAddLifts() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("la", "(JJ)J")
            .lload(0).lload(2).ladd().lreturn().build();
        IRMethod lifted = lowerAndLift(cf, "la");
        assertHasOp(lifted, BinaryOp.ADD);
    }

    @Test
    void signedDivLifts() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("d", "(II)I")
            .iload(0).iload(1).idiv().ireturn().build();
        assertHasOp(lowerAndLift(cf, "d"), BinaryOp.DIV);
    }

    @Test
    void signedRemLifts() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("r", "(II)I")
            .iload(0).iload(1).irem().ireturn().build();
        assertHasOp(lowerAndLift(cf, "r"), BinaryOp.REM);
    }

    @Test
    void shiftLeftLifts() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("s", "(II)I")
            .iload(0).iload(1).ishl().ireturn().build();
        assertHasOp(lowerAndLift(cf, "s"), BinaryOp.SHL);
    }

    @Test
    void logicalShiftRightLifts() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("u", "(II)I")
            .iload(0).iload(1).iushr().ireturn().build();
        assertHasOp(lowerAndLift(cf, "u"), BinaryOp.USHR);
    }

    @Test
    void arithmeticShiftRightLifts() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("s", "(II)I")
            .iload(0).iload(1).ishr().ireturn().build();
        assertHasOp(lowerAndLift(cf, "s"), BinaryOp.SHR);
    }

    @Test
    void loopWithPhiAndBranchLifts() throws IOException {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("T").publicStaticMethod("sum", "(I)I");
        Label head = mb.newLabel();
        Label end = mb.newLabel();
        ClassFile cf = mb
            .iconst(0).istore(1)
            .iconst(0).istore(2)
            .label(head).iload(2).iload(0).if_icmpge(end)
            .iload(1).iload(2).iadd().istore(1)
            .iinc(2, 1).goto_(head)
            .label(end).iload(1).ireturn()
            .build();
        IRMethod lifted = lowerAndLift(cf, "sum");
        // Should have phi instructions and a branch
        boolean hasPhi = false;
        boolean hasBranch = false;
        for (IRBlock b : lifted.getBlocksInOrder()) {
            if (!b.getPhiInstructions().isEmpty()) {
                hasPhi = true;
            }
            for (IRInstruction instr : b.getInstructions()) {
                if (instr instanceof BranchInstruction) {
                    hasBranch = true;
                    break;
                }
            }
        }
        assertTrue(hasPhi, "expected phi instruction");
        assertTrue(hasBranch, "expected branch instruction");
    }

    @Test
    void switchLifts() throws IOException {
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
        IRMethod lifted = lowerAndLift(cf, "sw");
        boolean hasSw = false;
        for (IRBlock b : lifted.getBlocksInOrder()) {
            for (IRInstruction instr : b.getInstructions()) {
                if (instr instanceof SwitchInstruction) {
                    SwitchInstruction si = (SwitchInstruction) instr;
                    assertEquals(2, si.getCases().size());
                    hasSw = true;
                }
            }
        }
        assertTrue(hasSw, "expected switch instruction");
    }

    @Test
    void staticCallLifts() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("c", "(I)I")
            .iload(0).invokestatic("Other", "helper", "(I)I").ireturn().build();
        IRMethod lifted = lowerAndLift(cf, "c");
        boolean hasInvoke = false;
        for (IRBlock b : lifted.getBlocksInOrder()) {
            for (IRInstruction instr : b.getInstructions()) {
                if (instr instanceof InvokeInstruction) {
                    InvokeInstruction inv = (InvokeInstruction) instr;
                    assertEquals(InvokeType.STATIC, inv.getInvokeType());
                    assertEquals("Other", inv.getOwner());
                    assertEquals("helper", inv.getName());
                    hasInvoke = true;
                }
            }
        }
        assertTrue(hasInvoke, "expected static invoke");
    }

    @Test
    void returnVoidLifts() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("v", "()V")
            .vreturn().build();
        IRMethod lifted = lowerAndLift(cf, "v");
        boolean hasReturn = false;
        for (IRBlock b : lifted.getBlocksInOrder()) {
            for (IRInstruction instr : b.getInstructions()) {
                if (instr instanceof ReturnInstruction && ((ReturnInstruction) instr).isVoidReturn()) {
                    hasReturn = true;
                }
            }
        }
        assertTrue(hasReturn);
    }

    @Test
    void methodHasCorrectBlockCount() throws IOException {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("T").publicStaticMethod("sum", "(I)I");
        Label head = mb.newLabel();
        Label end = mb.newLabel();
        ClassFile cf = mb
            .iconst(0).istore(1)
            .iconst(0).istore(2)
            .label(head).iload(2).iload(0).if_icmpge(end)
            .iload(1).iload(2).iadd().istore(1)
            .iinc(2, 1).goto_(head)
            .label(end).iload(1).ireturn()
            .build();
        MethodEntry method = find(cf, "sum");
        IRMethod original = TestUtils.liftMethod(method);
        String ll = new LlvmLowering().lower(original);
        IRMethod lifted = new LlvmLifter().lift(ll);
        // Block count should be preserved
        assertEquals(original.getBlocksInOrder().size(), lifted.getBlocksInOrder().size());
    }

    private static void assertHasOp(IRMethod method, BinaryOp expected) {
        for (IRBlock b : method.getBlocksInOrder()) {
            for (IRInstruction instr : b.getInstructions()) {
                if (instr instanceof BinaryOpInstruction
                    && ((BinaryOpInstruction) instr).getOp() == expected) {
                    return;
                }
            }
        }
        fail("expected BinaryOp." + expected + " but not found");
    }
}
