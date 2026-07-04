package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: a ternary merging a String constant and an Object value produces a phi whose
 * type must widen to java/lang/Object, not stay narrowed to String. A stale String type would
 * make callers downcast the unrelated Object incoming and fail at runtime.
 */
class MixedReferencePhiTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void ternaryOfStringAndObjectWidensToObject() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("MixedReferencePhi");
        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("pick"))
                .findFirst()
                .orElseThrow();

        IRMethod ir = new SSA(cf.getConstPool()).lift(method);

        boolean sawMixedPhi = false;
        for (IRBlock block : ir.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                boolean hasString = false;
                boolean hasObject = false;
                for (Value v : phi.getIncomingValues().values()) {
                    if (v == null || v.getType() == null || !v.getType().isReference()) {
                        continue;
                    }
                    String desc = v.getType().getDescriptor();
                    if (desc.equals("Ljava/lang/String;")) {
                        hasString = true;
                    } else if (desc.equals("Ljava/lang/Object;")) {
                        hasObject = true;
                    }
                }
                if (hasString && hasObject) {
                    sawMixedPhi = true;
                    IRType resultType = phi.getResult().getType();
                    assertEquals("Ljava/lang/Object;", resultType.getDescriptor(),
                            "a String+Object ternary phi must widen to Object");
                }
            }
        }
        assertTrue(sawMixedPhi, "expected a phi merging a String constant and an Object value");
    }
}
