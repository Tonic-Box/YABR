package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: a stack phi created for a ternary merge inside a loop must keep its real
 * incoming values. The lift fixup pass used to walk the cyclic CFG back to the phi's own
 * predecessors and rewrite its incoming edges to the phi result itself.
 */
class StackPhiSelfReferenceTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void stackPhiInLoopKeepsRealIncomings() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("StackPhiLoop");
        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("alternatingSum"))
                .findFirst()
                .orElseThrow();

        IRMethod ir = new SSA(cf.getConstPool()).lift(method);

        boolean sawStackPhi = false;
        for (IRBlock block : ir.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (phi.getResult() != null && phi.getResult().getName() != null
                        && phi.getResult().getName().startsWith("stack_phi")) {
                    sawStackPhi = true;
                }
                for (Map.Entry<IRBlock, Value> incoming : phi.getIncomingValues().entrySet()) {
                    assertNotEquals(phi.getResult(), incoming.getValue(),
                            "phi in B" + block.getId() + " references itself as incoming from B"
                                    + incoming.getKey().getId());
                }
            }
        }
        assertTrue(sawStackPhi, "expected the ternary to produce a stack phi");
    }
}
