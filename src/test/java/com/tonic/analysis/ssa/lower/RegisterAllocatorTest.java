package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.analysis.LivenessAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.LongConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RegisterAllocator.
 * Covers basic allocation, two-slot parameter handling, reserved slot management,
 * live interval building, phi result pre-allocation, fallback allocation, and maxLocals calculation.
 */
class RegisterAllocatorTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    // ========== Basic Allocation Tests ==========

    @Test
    void allocateEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        assertEquals(0, allocator.getMaxLocals());
    }

    @Test
    void allocateSimpleMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // v0 = const 42
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));

        // return v0
        entry.addInstruction(new ReturnInstruction(v0));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // v0 should get a register
        int reg = allocator.getRegister(v0);
        assertTrue(reg >= 0);
        assertTrue(allocator.getMaxLocals() >= 1);
    }

    @Test
    void allocateMultipleValues() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // v0 = const 10
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(10)));

        // v1 = const 20
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(20)));

        // v2 = add v0, v1
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");
        entry.addInstruction(new BinaryOpInstruction(v2, BinaryOp.ADD, v0, v1));

        // return v2
        entry.addInstruction(new ReturnInstruction(v2));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // All values should get registers
        int reg0 = allocator.getRegister(v0);
        int reg1 = allocator.getRegister(v1);
        int reg2 = allocator.getRegister(v2);

        assertTrue(reg0 >= 0);
        assertTrue(reg1 >= 0);
        assertTrue(reg2 >= 0);
    }

    // ========== Two-Slot Parameter Handling Tests ==========

    @Test
    void twoSlotParameterTakesConsecutiveSlots() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(J)J", true);

        // Add long parameter
        SSAValue param0 = new SSAValue(PrimitiveType.LONG, "param0");
        method.addParameter(param0);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction(param0));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // Long parameter should be at slot 0 and take slots 0-1
        int reg = allocator.getRegister(param0);
        assertEquals(0, reg);
        // maxLocals should be at least 2 (slots 0 and 1 for the long)
        assertTrue(allocator.getMaxLocals() >= 2);
    }

    @Test
    void multipleTwoSlotParameters() {
        // Test method signature: (JJ)J - two long params -> long
        IRMethod method = new IRMethod("com/test/Test", "foo", "(JJ)J", true);

        // Add two long parameters
        SSAValue param0 = new SSAValue(PrimitiveType.LONG, "param0");
        SSAValue param1 = new SSAValue(PrimitiveType.LONG, "param1");
        method.addParameter(param0);
        method.addParameter(param1);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // v0 = add param0, param1
        SSAValue v0 = new SSAValue(PrimitiveType.LONG, "v0");
        entry.addInstruction(new BinaryOpInstruction(v0, BinaryOp.ADD, param0, param1));
        entry.addInstruction(new ReturnInstruction(v0));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // First long param should be at slots 0-1
        int reg0 = allocator.getRegister(param0);
        assertEquals(0, reg0);

        // Second long param should be at slots 2-3
        int reg1 = allocator.getRegister(param1);
        assertEquals(2, reg1);

        // Reserved slot count should be 4
        assertEquals(4, allocator.getReservedSlotCount());
    }

    @Test
    void mixedSingleAndTwoSlotParameters() {
        // Test method signature: (IJ)V - int param, long param
        IRMethod method = new IRMethod("com/test/Test", "foo", "(IJ)V", true);

        SSAValue param0 = new SSAValue(PrimitiveType.INT, "param0");
        SSAValue param1 = new SSAValue(PrimitiveType.LONG, "param1");
        method.addParameter(param0);
        method.addParameter(param1);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // Int param should be at slot 0
        int reg0 = allocator.getRegister(param0);
        assertEquals(0, reg0);

        // Long param should be at slots 1-2
        int reg1 = allocator.getRegister(param1);
        assertEquals(1, reg1);

        // Reserved slot count should be 3 (slot 0 for int, slots 1-2 for long)
        assertEquals(3, allocator.getReservedSlotCount());
    }

    @Test
    void doubleParameterTakesConsecutiveSlots() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(D)D", true);

        SSAValue param0 = new SSAValue(PrimitiveType.DOUBLE, "param0");
        method.addParameter(param0);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction(param0));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // Double parameter should be at slot 0
        int reg = allocator.getRegister(param0);
        assertEquals(0, reg);
        // maxLocals should account for double taking 2 slots
        assertTrue(allocator.getMaxLocals() >= 2);
    }

    // ========== Reserved Slot Management Tests ==========

    @Test
    void reservedSlotsNotReusedAfterExpiry() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(I)I", true);

        SSAValue param0 = new SSAValue(PrimitiveType.INT, "param0");
        method.addParameter(param0);

        IRBlock entry = new IRBlock("entry");
        IRBlock block2 = new IRBlock("block2");
        method.addBlock(entry);
        method.addBlock(block2);
        method.setEntryBlock(entry);
        entry.addSuccessor(block2);

        // Use param0 in entry block
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new BinaryOpInstruction(v0, BinaryOp.ADD, param0, new IntConstant(1)));

        // Create a new value in block2 (param0 is no longer live)
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        block2.addInstruction(new ConstantInstruction(v1, new IntConstant(42)));
        block2.addInstruction(new ReturnInstruction(v1));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        int paramReg = allocator.getRegister(param0);
        int v1Reg = allocator.getRegister(v1);

        // v1 should NOT reuse param0's slot (slot 0)
        // even though param0 is no longer live
        assertEquals(0, paramReg);
        assertNotEquals(0, v1Reg);
    }

    @Test
    void reservedSlotCountCorrectWithNoParameters() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        assertEquals(0, allocator.getReservedSlotCount());
    }

    // ========== Live Interval Building Tests ==========

    @Test
    void liveIntervalSpansDefinitionToLastUse() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // v0 = const 5 (position 0)
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(5)));

        // v1 = const 10 (position 1)
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(10)));

        // v2 = add v0, v1 (position 2 - last use of v0 and v1)
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");
        entry.addInstruction(new BinaryOpInstruction(v2, BinaryOp.ADD, v0, v1));

        // return v2 (position 3)
        entry.addInstruction(new ReturnInstruction(v2));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // All values should get registers
        // v0 and v1 can potentially reuse slots since they die at position 2
        int reg0 = allocator.getRegister(v0);
        int reg1 = allocator.getRegister(v1);
        int reg2 = allocator.getRegister(v2);

        assertTrue(reg0 >= 0);
        assertTrue(reg1 >= 0);
        assertTrue(reg2 >= 0);
    }

    @Test
    void phiResultIntervalExtendedToCoverPhiCopies() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock left = new IRBlock("left");
        IRBlock right = new IRBlock("right");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(left);
        method.addBlock(right);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(left);
        entry.addSuccessor(right);

        // left: v0 = 1
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        left.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        left.addSuccessor(merge);

        // right: v1 = 2
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        right.addInstruction(new ConstantInstruction(v1, new IntConstant(2)));
        right.addSuccessor(merge);

        // merge: v2 = phi(v0, v1)
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");
        PhiInstruction phi = new PhiInstruction(v2);
        phi.addIncoming(v0, left);
        phi.addIncoming(v1, right);
        merge.addPhi(phi);
        merge.addInstruction(new ReturnInstruction(v2));

        // Set up phi copy mapping
        SSAValue copy0 = new SSAValue(PrimitiveType.INT, "copy0");
        SSAValue copy1 = new SSAValue(PrimitiveType.INT, "copy1");
        Map<SSAValue, List<CopyInfo>> phiCopyMapping = new HashMap<>();
        phiCopyMapping.put(v2, List.of(
            new CopyInfo(copy0, left),
            new CopyInfo(copy1, right)
        ));
        method.setPhiCopyMapping(phiCopyMapping);

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // v2 (phi result) should be pre-allocated
        int reg2 = allocator.getRegister(v2);
        assertTrue(reg2 >= 0);
    }

    // ========== Pre-allocation of Phi Results Tests ==========

    @Test
    void phiResultsPreAllocatedBeforeRegularValues() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock left = new IRBlock("left");
        IRBlock right = new IRBlock("right");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(left);
        method.addBlock(right);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(left);
        entry.addSuccessor(right);
        left.addSuccessor(merge);
        right.addSuccessor(merge);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        left.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));

        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        right.addInstruction(new ConstantInstruction(v1, new IntConstant(2)));

        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");
        PhiInstruction phi = new PhiInstruction(v2);
        phi.addIncoming(v0, left);
        phi.addIncoming(v1, right);
        merge.addPhi(phi);
        merge.addInstruction(new ReturnInstruction(v2));

        // Set up phi copy mapping to trigger pre-allocation
        SSAValue copy0 = new SSAValue(PrimitiveType.INT, "copy0");
        SSAValue copy1 = new SSAValue(PrimitiveType.INT, "copy1");
        Map<SSAValue, List<CopyInfo>> phiCopyMapping = new HashMap<>();
        phiCopyMapping.put(v2, List.of(
            new CopyInfo(copy0, left),
            new CopyInfo(copy1, right)
        ));
        method.setPhiCopyMapping(phiCopyMapping);

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // Phi result should have a register allocated
        int phiReg = allocator.getRegister(v2);
        assertTrue(phiReg >= 0);
    }

    @Test
    void phiResultWithTwoSlotType() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()J", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock left = new IRBlock("left");
        IRBlock right = new IRBlock("right");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(left);
        method.addBlock(right);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(left);
        entry.addSuccessor(right);
        left.addSuccessor(merge);
        right.addSuccessor(merge);

        // Long values
        SSAValue v0 = new SSAValue(PrimitiveType.LONG, "v0");
        left.addInstruction(new ConstantInstruction(v0, new LongConstant(100L)));

        SSAValue v1 = new SSAValue(PrimitiveType.LONG, "v1");
        right.addInstruction(new ConstantInstruction(v1, new LongConstant(200L)));

        SSAValue v2 = new SSAValue(PrimitiveType.LONG, "v2");
        PhiInstruction phi = new PhiInstruction(v2);
        phi.addIncoming(v0, left);
        phi.addIncoming(v1, right);
        merge.addPhi(phi);
        merge.addInstruction(new ReturnInstruction(v2));

        // Set up phi copy mapping
        SSAValue copy0 = new SSAValue(PrimitiveType.LONG, "copy0");
        SSAValue copy1 = new SSAValue(PrimitiveType.LONG, "copy1");
        Map<SSAValue, List<CopyInfo>> phiCopyMapping = new HashMap<>();
        phiCopyMapping.put(v2, List.of(
            new CopyInfo(copy0, left),
            new CopyInfo(copy1, right)
        ));
        method.setPhiCopyMapping(phiCopyMapping);

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // Long phi result should have a register
        int phiReg = allocator.getRegister(v2);
        assertTrue(phiReg >= 0);

        // maxLocals should account for two-slot values
        assertTrue(allocator.getMaxLocals() >= phiReg + 2);
    }

    // ========== Fallback Allocation Tests ==========

    @Test
    void fallbackAllocatesOnDemandForMissedValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // Create a value that wasn't part of the original allocation
        SSAValue newValue = new SSAValue(PrimitiveType.INT, "newValue");

        // Fallback should allocate a register on-demand
        int reg = allocator.getRegister(newValue);
        assertTrue(reg >= 0);
        assertTrue(allocator.getMaxLocals() > reg);
    }

    @Test
    void fallbackAllocationForTwoSlotValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // Create a two-slot value that wasn't part of the original allocation
        SSAValue newValue = new SSAValue(PrimitiveType.LONG, "newLong");

        // Fallback should allocate 2 consecutive slots
        int reg = allocator.getRegister(newValue);
        assertTrue(reg >= 0);
        // maxLocals should account for the two slots
        assertTrue(allocator.getMaxLocals() >= reg + 2);
    }

    @Test
    void fallbackAllocatesMultipleValuesSequentially() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // Allocate multiple values via fallback
        SSAValue val1 = new SSAValue(PrimitiveType.INT, "val1");
        SSAValue val2 = new SSAValue(PrimitiveType.INT, "val2");
        SSAValue val3 = new SSAValue(PrimitiveType.INT, "val3");

        int reg1 = allocator.getRegister(val1);
        int reg2 = allocator.getRegister(val2);
        int reg3 = allocator.getRegister(val3);

        assertTrue(reg1 >= 0);
        assertTrue(reg2 >= 0);
        assertTrue(reg3 >= 0);

        // Each should get a different register
        assertNotEquals(reg1, reg2);
        assertNotEquals(reg2, reg3);
        assertNotEquals(reg1, reg3);
    }

    // ========== MaxLocals Calculation Tests ==========

    @Test
    void maxLocalsReturnsHighestSlotPlusOne() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction(v0));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        int maxLocals = allocator.getMaxLocals();
        int v0Reg = allocator.getRegister(v0);

        // maxLocals should be at least v0's register + 1
        assertTrue(maxLocals >= v0Reg + 1);
    }

    @Test
    void maxLocalsAccountsForTwoSlotValues() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()J", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.LONG, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new LongConstant(42L)));
        entry.addInstruction(new ReturnInstruction(v0));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        int maxLocals = allocator.getMaxLocals();
        int v0Reg = allocator.getRegister(v0);

        // maxLocals should be at least v0's register + 2 (for two-slot)
        assertTrue(maxLocals >= v0Reg + 2);
    }

    @Test
    void maxLocalsWithMixedSingleAndTwoSlotValues() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()J", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // Create a mix of single-slot and two-slot values
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(10)));

        SSAValue v1 = new SSAValue(PrimitiveType.LONG, "v1");
        entry.addInstruction(new ConstantInstruction(v1, new LongConstant(100L)));

        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");
        entry.addInstruction(new ConstantInstruction(v2, new IntConstant(20)));

        entry.addInstruction(new ReturnInstruction(v1));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        int maxLocals = allocator.getMaxLocals();

        // All values should have registers
        int reg0 = allocator.getRegister(v0);
        int reg1 = allocator.getRegister(v1);
        int reg2 = allocator.getRegister(v2);

        assertTrue(reg0 >= 0);
        assertTrue(reg1 >= 0);
        assertTrue(reg2 >= 0);

        // maxLocals should be sufficient for all values
        assertTrue(maxLocals > reg0);
        assertTrue(maxLocals >= reg1 + 2); // two-slot
        assertTrue(maxLocals > reg2);
    }

    @Test
    void maxLocalsIncludesReservedParameterSlots() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(IJI)V", true);

        SSAValue param0 = new SSAValue(PrimitiveType.INT, "param0");
        SSAValue param1 = new SSAValue(PrimitiveType.LONG, "param1");
        SSAValue param2 = new SSAValue(PrimitiveType.INT, "param2");
        method.addParameter(param0);
        method.addParameter(param1);
        method.addParameter(param2);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // param0 at slot 0, param1 at slots 1-2, param2 at slot 3
        assertEquals(0, allocator.getRegister(param0));
        assertEquals(1, allocator.getRegister(param1));
        assertEquals(3, allocator.getRegister(param2));

        // maxLocals should be at least 4 to cover all parameter slots
        assertTrue(allocator.getMaxLocals() >= 4);
        assertEquals(4, allocator.getReservedSlotCount());
    }

    // ========== Edge Case Tests ==========

    @Test
    void allocatorHandlesNoPhiCopyMapping() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock left = new IRBlock("left");
        IRBlock right = new IRBlock("right");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(left);
        method.addBlock(right);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(left);
        entry.addSuccessor(right);
        left.addSuccessor(merge);
        right.addSuccessor(merge);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        left.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));

        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        right.addInstruction(new ConstantInstruction(v1, new IntConstant(2)));

        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");
        PhiInstruction phi = new PhiInstruction(v2);
        phi.addIncoming(v0, left);
        phi.addIncoming(v1, right);
        merge.addPhi(phi);
        merge.addInstruction(new ReturnInstruction(v2));

        // No phi copy mapping set
        method.setPhiCopyMapping(null);

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // Should still allocate successfully
        assertTrue(allocator.getRegister(v0) >= 0);
        assertTrue(allocator.getRegister(v1) >= 0);
        assertTrue(allocator.getRegister(v2) >= 0);
    }

    @Test
    void allocatorHandlesComplexControlFlow() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock loop = new IRBlock("loop");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(loop);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(loop);

        // loop block with back-edge
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        loop.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));

        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        loop.addInstruction(new BinaryOpInstruction(v1, BinaryOp.ADD, v0, new IntConstant(1)));

        loop.addSuccessor(loop); // back-edge
        loop.addSuccessor(exit);

        exit.addInstruction(new ReturnInstruction(v1));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);
        allocator.allocate();

        // Should handle loop back-edges
        assertTrue(allocator.getRegister(v0) >= 0);
        assertTrue(allocator.getRegister(v1) >= 0);
    }

    @Test
    void getterMethodsReturnCorrectValues() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(I)I", true);
        SSAValue param0 = new SSAValue(PrimitiveType.INT, "param0");
        method.addParameter(param0);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction(param0));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        RegisterAllocator allocator = new RegisterAllocator(method, liveness);

        // Test getters
        assertEquals(method, allocator.getMethod());
        assertEquals(liveness, allocator.getLiveness());

        allocator.allocate();

        assertNotNull(allocator.getAllocation());
        assertTrue(allocator.getMaxLocals() >= 1);
        assertEquals(1, allocator.getReservedSlotCount());
    }
}
