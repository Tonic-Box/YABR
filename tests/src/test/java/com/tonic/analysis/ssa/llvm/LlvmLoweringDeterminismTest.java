package com.tonic.analysis.ssa.llvm;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.BytecodeBuilder.Label;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The emitted LLVM IR must be byte-identical across runs (stable value/label naming + sorted
 * declares), so the backend output is reproducible.
 */
class LlvmLoweringDeterminismTest {

    private String lowerSum() throws IOException {
        TestUtils.resetSSACounters();
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
        MethodEntry method = cf.getMethods().stream()
            .filter(m -> m.getName().equals("sum")).findFirst().orElseThrow();
        IRMethod ir = TestUtils.liftMethod(method);
        return new LlvmLowering().lower(ir);
    }

    @Test
    void outputIsDeterministic() throws IOException {
        assertEquals(lowerSum(), lowerSum());
    }
}
