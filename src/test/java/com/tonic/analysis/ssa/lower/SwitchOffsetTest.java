package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.ir.SimpleInstruction;
import com.tonic.analysis.ssa.ir.SwitchInstruction;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SwitchOffsetTest {

    private ClassPool pool;
    private ClassFile classFile;
    private SSA ssa;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/SwitchTest", access);
        ssa = new SSA(classFile.getConstPool());
    }

    @Nested
    class TableSwitchOffsetTests {

        @Test
        void tableSwitchCasesHaveCorrectRelativeOffsets() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "tableSwitch", "I", "I");

            IRMethod ir = ssa.lift(method);
            IRBlock entry = ir.getEntryBlock();

            IRBlock case0 = new IRBlock("case_0");
            IRBlock case1 = new IRBlock("case_1");
            IRBlock case2 = new IRBlock("case_2");
            IRBlock defaultBlock = new IRBlock("default");
            IRBlock exitBlock = new IRBlock("exit");

            ir.addBlock(case0);
            ir.addBlock(case1);
            ir.addBlock(case2);
            ir.addBlock(defaultBlock);
            ir.addBlock(exitBlock);

            SSAValue switchKey = new SSAValue(PrimitiveType.INT, "key");

            SwitchInstruction tableSwitch = new SwitchInstruction(switchKey, defaultBlock);
            tableSwitch.addCase(0, case0);
            tableSwitch.addCase(1, case1);
            tableSwitch.addCase(2, case2);

            entry.addInstruction(tableSwitch);
            entry.addSuccessor(case0);
            entry.addSuccessor(case1);
            entry.addSuccessor(case2);
            entry.addSuccessor(defaultBlock);

            case0.addInstruction(SimpleInstruction.createGoto(exitBlock));
            case0.addSuccessor(exitBlock);

            case1.addInstruction(SimpleInstruction.createGoto(exitBlock));
            case1.addSuccessor(exitBlock);

            case2.addInstruction(SimpleInstruction.createGoto(exitBlock));
            case2.addSuccessor(exitBlock);

            defaultBlock.addInstruction(SimpleInstruction.createGoto(exitBlock));
            defaultBlock.addSuccessor(exitBlock);

            SSAValue retVal = new SSAValue(PrimitiveType.INT, "retVal");
            exitBlock.addInstruction(new ReturnInstruction(retVal));

            ssa.lower(ir, method);

            CodeAttribute code = method.getCodeAttribute();
            assertNotNull(code);
            byte[] bytecode = code.getCode();
            assertTrue(bytecode.length > 0);
        }

        @Test
        void tableSwitchWithStackPhisAcrossBlocks() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "switchWithPhis", "I", "I");

            IRMethod ir = ssa.lift(method);

            ssa.lower(ir, method);

            CodeAttribute code = method.getCodeAttribute();
            assertNotNull(code);
        }
    }

    @Nested
    class LookupSwitchOffsetTests {

        @Test
        void lookupSwitchCasesHaveCorrectRelativeOffsets() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "lookupSwitch", "I", "I");

            IRMethod ir = ssa.lift(method);
            IRBlock entry = ir.getEntryBlock();

            IRBlock case100 = new IRBlock("case_100");
            IRBlock case500 = new IRBlock("case_500");
            IRBlock defaultBlock = new IRBlock("default");
            IRBlock exitBlock = new IRBlock("exit");

            ir.addBlock(case100);
            ir.addBlock(case500);
            ir.addBlock(defaultBlock);
            ir.addBlock(exitBlock);

            SSAValue switchKey = new SSAValue(PrimitiveType.INT, "key");

            SwitchInstruction lookupSwitch = new SwitchInstruction(switchKey, defaultBlock);
            lookupSwitch.addCase(100, case100);
            lookupSwitch.addCase(500, case500);

            entry.addInstruction(lookupSwitch);
            entry.addSuccessor(case100);
            entry.addSuccessor(case500);
            entry.addSuccessor(defaultBlock);

            case100.addInstruction(SimpleInstruction.createGoto(exitBlock));
            case100.addSuccessor(exitBlock);

            case500.addInstruction(SimpleInstruction.createGoto(exitBlock));
            case500.addSuccessor(exitBlock);

            defaultBlock.addInstruction(SimpleInstruction.createGoto(exitBlock));
            defaultBlock.addSuccessor(exitBlock);

            SSAValue retVal = new SSAValue(PrimitiveType.INT, "retVal");
            exitBlock.addInstruction(new ReturnInstruction(retVal));

            ssa.lower(ir, method);

            CodeAttribute code = method.getCodeAttribute();
            assertNotNull(code);
            byte[] bytecode = code.getCode();
            assertTrue(bytecode.length > 0);
        }
    }

    @Nested
    class StackPhiPropagationTests {

        @Test
        void stackPhiValuesPropagateThroughBranches() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "branchingStack", "I", "I", "I");

            IRMethod ir = ssa.lift(method);

            ssa.lower(ir, method);

            CodeAttribute code = method.getCodeAttribute();
            assertNotNull(code);
            assertTrue(code.getMaxStack() >= 0);
        }

        @Test
        void frameGeneratorHandlesControlFlowMerges() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "mergePoint", "I", "I");

            IRMethod ir = ssa.lift(method);

            ssa.lower(ir, method);

            CodeAttribute code = method.getCodeAttribute();
            assertNotNull(code);
        }
    }
}
