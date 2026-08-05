package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ConstantInstruction;
import com.tonic.analysis.ssa.ir.LoadLocalInstruction;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A synthesized fallback name never collides with a debug name on ANOTHER slot. A prior generation's
 * fallback ({@code local4_1}) becomes a declared name on whatever slot relowering assigns it, so a later
 * generation synthesizing the same pattern for slot 4 would merge two unrelated variables - the two then
 * share one name, their types unify, and the declaration comes out typed as neither ({@code float
 * local4_1 = ...iterator()}).
 */
class SlotVariablePartitionNameClashTest
{

    @BeforeEach
    void setUp()
    {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    private static StoreLocalInstruction store(IRBlock block, int slot, int offset)
    {
        SSAValue v = new SSAValue(PrimitiveType.INT);
        ConstantInstruction c = new ConstantInstruction(v, IntConstant.ZERO);
        c.setBytecodeOffset(offset - 1);
        block.addInstruction(c);
        StoreLocalInstruction s = new StoreLocalInstruction(slot, v);
        s.setBytecodeOffset(offset);
        block.addInstruction(s);
        return s;
    }

    private static LoadLocalInstruction load(IRBlock block, int slot, int offset)
    {
        LoadLocalInstruction l = new LoadLocalInstruction(new SSAValue(PrimitiveType.INT), slot);
        l.setBytecodeOffset(offset);
        block.addInstruction(l);
        return l;
    }

    @Test
    void aFallbackNameAvoidsDebugNamesOnOtherSlots()
    {
        IRMethod method = new IRMethod("com/test/Clash", "m", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);

        StoreLocalInstruction namedStore = store(entry, 9, 10);
        LoadLocalInstruction namedLoad = load(entry, 9, 20);

        StoreLocalInstruction first = store(entry, 4, 30);
        load(entry, 4, 40);
        StoreLocalInstruction second = store(entry, 4, 50);
        LoadLocalInstruction secondLoad = load(entry, 4, 60);
        entry.addInstruction(new ReturnInstruction());

        SlotVariablePartition partition = new SlotVariablePartition(
                method,
                slot -> "local" + slot,
                (slot, offset) -> slot == 9 ? "local4_1" : null);

        assertEquals("local4_1", partition.nameForStore(namedStore),
                "the debug name on slot 9 is authoritative for its own components");
        assertEquals(partition.nameForStore(namedStore), partition.nameForLoad(namedLoad), "one variable, one name");
        assertNotEquals("local4_1", partition.nameForStore(first),
                "slot 4 must not synthesize a name another slot already carries");
        assertNotEquals("local4_1", partition.nameForStore(second),
                "no slot-4 component may collide with slot 9's debug name");
        assertEquals(partition.nameForStore(second), partition.nameForLoad(secondLoad),
                "the renamed component still names its own reads");
        assertNotEquals(partition.nameForStore(first), partition.nameForStore(second),
                "disjoint components of a reused slot keep distinct names");
    }
}
