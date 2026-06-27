package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DefUseChains analysis.
 * Covers definition tracking, use tracking, and dead code detection.
 */
class DefUseChainsTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    // ========== Basic Tests ==========

    @Test
    void computeOnEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        DefUseChains chains = new DefUseChains(method);

        chains.compute();

        assertNotNull(chains.getDefinitions());
        assertNotNull(chains.getUses());
    }

    // ========== Definition Tests ==========

    @Test
    void getDefinitionForDefinedValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);
        entry.addInstruction(new ReturnInstruction());

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        assertEquals(constInstr, chains.getDefinition(v0));
    }

    @Test
    void getDefinitionReturnsNullForUndefinedValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        SSAValue undefined = new SSAValue(PrimitiveType.INT, "undefined");
        assertNull(chains.getDefinition(undefined));
    }

    // ========== Use Tests ==========

    @Test
    void getUsesForUsedValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        ReturnInstruction ret = new ReturnInstruction(v0);
        entry.addInstruction(ret);

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        Set<IRInstruction> uses = chains.getUses(v0);
        assertTrue(uses.contains(ret));
    }

    @Test
    void getUsesReturnsEmptyForUnusedValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction()); // Doesn't use v0

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        Set<IRInstruction> uses = chains.getUses(v0);
        assertTrue(uses.isEmpty());
    }

    // ========== getUsedValues Tests ==========

    @Test
    void getUsedValuesForInstruction() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        ReturnInstruction ret = new ReturnInstruction(v0);
        entry.addInstruction(ret);

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        Set<SSAValue> usedValues = chains.getUsedValues(ret);
        assertTrue(usedValues.contains(v0));
    }

    // ========== getDefinedValue Tests ==========

    @Test
    void getDefinedValueForInstruction() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);
        entry.addInstruction(new ReturnInstruction());

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        assertEquals(v0, chains.getDefinedValue(constInstr));
    }

    @Test
    void getDefinedValueReturnsNullForNoResult() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        ReturnInstruction ret = new ReturnInstruction();
        entry.addInstruction(ret);

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        assertNull(chains.getDefinedValue(ret));
    }

    // ========== hasUses Tests ==========

    @Test
    void hasUsesReturnsTrueForUsedValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction(v0));

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        assertTrue(chains.hasUses(v0));
    }

    @Test
    void hasUsesReturnsFalseForUnusedValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction());

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        assertFalse(chains.hasUses(v0));
    }

    // ========== getUseCount Tests ==========

    @Test
    void getUseCountReturnsCorrectCount() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction(v0));

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        assertEquals(1, chains.getUseCount(v0));
    }

    @Test
    void getUseCountReturnsZeroForUnused() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction());

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        assertEquals(0, chains.getUseCount(v0));
    }

    // ========== isDeadCode Tests ==========

    @Test
    void isDeadCodeTrueForUnusedDefinition() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);
        entry.addInstruction(new ReturnInstruction());

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        assertTrue(chains.isDeadCode(constInstr));
    }

    @Test
    void isDeadCodeFalseForUsedDefinition() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);
        entry.addInstruction(new ReturnInstruction(v0));

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        assertFalse(chains.isDeadCode(constInstr));
    }

    @Test
    void isDeadCodeFalseForNoResult() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        ReturnInstruction ret = new ReturnInstruction();
        entry.addInstruction(ret);

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        // Return doesn't define a value, so it's not dead code
        assertFalse(chains.isDeadCode(ret));
    }

    // ========== Phi Instruction Tests ==========

    @Test
    void phiInstructionDefineValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(merge);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(v0);
        merge.addPhi(phi);
        merge.addInstruction(new ReturnInstruction(v0));

        DefUseChains chains = new DefUseChains(method);
        chains.compute();

        assertEquals(phi, chains.getDefinition(v0));
    }

    // ========== Method Reference Tests ==========

    @Test
    void getMethodReturnsMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        DefUseChains chains = new DefUseChains(method);

        assertEquals(method, chains.getMethod());
    }
}
