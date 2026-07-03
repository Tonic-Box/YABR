package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ArrayAccessInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: aaload results must carry the array operand's element type when it is
 * statically known, not bare java/lang/Object.
 */
class ArrayLoadElementTypeTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void jaggedIntArrayLoadIsTypedAsIntArray() throws Exception {
        assertAaloadResultType("sum", "[[I", "[I");
    }

    @Test
    void objectArrayLoadIsTypedAsElementClass() throws Exception {
        assertAaloadResultType("first", "[Ljava/lang/String;", "Ljava/lang/String;");
    }

    private void assertAaloadResultType(String methodName, String arrayDescriptor,
                                        String expectedElementDescriptor) throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("JaggedArrays");
        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        IRMethod ir = new SSA(cf.getConstPool()).lift(method);

        boolean sawLoad = false;
        StringBuilder seen = new StringBuilder();
        for (IRBlock block : ir.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof ArrayAccessInstruction) {
                    ArrayAccessInstruction access = (ArrayAccessInstruction) instr;
                    if (!access.isLoad()) {
                        continue;
                    }
                    String arrayDesc = access.getArray().getType() == null
                            ? "null" : access.getArray().getType().getDescriptor();
                    seen.append(arrayDesc).append("->")
                            .append(access.getResult().getType().getDescriptor()).append(' ');
                    if (arrayDesc.equals(arrayDescriptor)) {
                        sawLoad = true;
                        assertEquals(expectedElementDescriptor,
                                access.getResult().getType().getDescriptor());
                    }
                }
            }
        }
        assertTrue(sawLoad, "expected an aaload from " + arrayDescriptor + "; saw: " + seen);
    }
}
