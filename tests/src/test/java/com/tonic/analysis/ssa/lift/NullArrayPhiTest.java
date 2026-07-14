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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: a JVM slot initialized to null and later assigned an array in a branch produces a
 * phi merging the null with the array type. aconst_null lifts to an Object-typed value, so the phi
 * must treat that incoming as bottom and keep the array type rather than widening to Object; a widened
 * phi makes the following aastore store into a non-array-typed value.
 */
class NullArrayPhiTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void nullMergedWithArrayKeepsArrayType() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("NullArrayPhi");
        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("build"))
                .findFirst()
                .orElseThrow();

        IRMethod ir = new SSA(cf.getConstPool()).lift(method);

        boolean sawArrayPhi = false;
        for (IRBlock block : ir.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                boolean hasArray = false;
                for (Value v : phi.getIncomingValues().values()) {
                    if (v != null && v.getType() != null
                            && "[Ljava/lang/String;".equals(v.getType().getDescriptor())) {
                        hasArray = true;
                    }
                }
                if (hasArray) {
                    sawArrayPhi = true;
                    assertEquals("[Ljava/lang/String;", phi.getResult().getType().getDescriptor(),
                            "a null-with-array phi must keep the array type, not widen to Object");
                }
            }
        }
        assertTrue(sawArrayPhi, "expected a phi merging null and a String[] value");
    }
}
