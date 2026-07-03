package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: invokedynamic has no receiver, so getReceiver() must be null and
 * getMethodArguments() must return every stack argument instead of dropping index 0.
 */
class IndyArgumentsTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void dynamicInvokeKeepsAllArguments() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("Concats");
        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("tag"))
                .findFirst()
                .orElseThrow();
        IRMethod ir = new SSA(cf.getConstPool()).lift(method);

        boolean sawIndy = false;
        for (IRBlock block : ir.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instr;
                    if (invoke.getInvokeType() == InvokeType.DYNAMIC) {
                        sawIndy = true;
                        assertNull(invoke.getReceiver(), "indy has no receiver");
                        assertEquals(invoke.getArguments().size(), invoke.getMethodArguments().size(),
                                "indy must not drop an argument as receiver");
                        assertEquals(2, invoke.getMethodArguments().size());
                    }
                }
            }
        }
        assertTrue(sawIndy, "expected an invokedynamic in the concat fixture");
    }
}
