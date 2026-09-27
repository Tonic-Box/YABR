package com.tonic.analysis.simulation.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tonic.analysis.simulation.listener.AbstractListener;
import com.tonic.analysis.ssa.ir.BranchInstruction;
import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import com.tonic.type.AccessFlags;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BranchOutcomeTest
{

    @Test
    void outcomeIsDecidedOnlyByConstantOperands() throws Exception
    {
        TestUtils.resetSSACounters();
        byte[] bytes = ClassBuilder.create("com/test/Branches")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "run", "(I)I")
                .code()
                .iconst(3).iconst(2).if_icmplt("done")
                .iload(0).ifgt("done")
                .label("done")
                .iconst(0).ireturn()
                .end()
                .end()
                .toByteArray();
        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));

        List<Integer> offsets = new ArrayList<>();
        List<Boolean> outcomes = new ArrayList<>();
        new SimulationEngine(SimulationContext.forPool(pool)).addListener(new AbstractListener()
        {
            @Override
            public void onBranch(BranchInstruction instr, Boolean outcome, SimulationState state)
            {
                offsets.add(instr.getBytecodeOffset());
                outcomes.add(outcome);
            }
        }).simulate(cf.getMethod("run", "(I)I"));

        assertEquals(2, offsets.size(), offsets.toString());
        assertEquals(Boolean.FALSE, outcomes.get(offsets.indexOf(2)));
        assertEquals(null, outcomes.get(1 - offsets.indexOf(2)));
    }
}
