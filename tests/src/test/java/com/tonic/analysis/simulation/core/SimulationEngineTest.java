package com.tonic.analysis.simulation.core;

import com.tonic.analysis.simulation.listener.AllocationListener;
import com.tonic.analysis.simulation.listener.ControlFlowListener;
import com.tonic.analysis.simulation.listener.MethodCallListener;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {

    private ClassPool pool;
    private SimulationContext context;

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
        pool = TestUtils.emptyPool();
        context = SimulationContext.forPool(pool);
    }

    @Nested
    class EngineSetupTests {

        @Test
        void engineCreation() {
            SimulationEngine engine = new SimulationEngine(context);
            assertNotNull(engine);
            assertNotNull(engine.getContext());
            assertSame(context, engine.getContext());
        }

        @Test
        void engineAddListener() {
            SimulationEngine engine = new SimulationEngine(context);
            AllocationListener listener = new AllocationListener();

            SimulationEngine result = engine.addListener(listener);
            assertSame(engine, result);
        }

        @Test
        void engineAddMultipleListeners() {
            SimulationEngine engine = new SimulationEngine(context);
            AllocationListener alloc = new AllocationListener();
            MethodCallListener call = new MethodCallListener();

            engine.addListeners(alloc, call);
        }

        @Test
        void engineRemoveListener() {
            SimulationEngine engine = new SimulationEngine(context);
            AllocationListener listener = new AllocationListener();

            engine.addListener(listener);
            SimulationEngine result = engine.removeListener(listener);
            assertSame(engine, result);
        }

        @Test
        void engineGetListener() {
            SimulationEngine engine = new SimulationEngine(context);
            AllocationListener listener = new AllocationListener();
            engine.addListener(listener);

            AllocationListener retrieved = engine.getListener(AllocationListener.class);
            assertSame(listener, retrieved);
        }
    }

    @Nested
    class BasicSimulationTests {

        @Test
        void simulateEmptyMethod() {
            IRMethod method = new IRMethod("com/test/Test", "empty", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction(null));

            SimulationEngine engine = new SimulationEngine(context);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertEquals(method, result.getMethod());
            assertTrue(result.getTotalInstructions() > 0);
        }

        @Test
        void simulateMethodWithListener() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction(null));

            AllocationListener listener = new AllocationListener();
            SimulationEngine engine = new SimulationEngine(context);
            engine.addListener(listener);

            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
        }

        @Test
        void simulateWithMultipleListeners() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction(null));

            SimulationEngine engine = new SimulationEngine(context);
            engine.addListeners(
                new AllocationListener(),
                new MethodCallListener(),
                new ControlFlowListener()
            );

            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertTrue(result.getTotalInstructions() >= 0);
        }
    }

    @Nested
    class HandleNullEntryBlockTests {

        @Test
        void handleNullEntryBlock() {
            IRMethod method = new IRMethod("com/test/Test", "nullEntry", "()V", true);

            SimulationEngine engine = new SimulationEngine(context);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertEquals(0, result.getTotalInstructions());
        }

        @Test
        void handleEmptyBlockList() {
            IRMethod method = new IRMethod("com/test/Test", "empty", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.setEntryBlock(entry);

            SimulationEngine engine = new SimulationEngine(context);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
        }
    }

    @Nested
    class HandleLoopWithStateRevisitTests {

        @Test
        void handleSimpleLoop() {
            IRMethod method = new IRMethod("com/test/Test", "loop", "()V", true);

            IRBlock entry = new IRBlock("entry");
            IRBlock loopHeader = new IRBlock("loop_header");
            IRBlock loopBody = new IRBlock("loop_body");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(loopHeader);
            method.addBlock(loopBody);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addInstruction(SimpleInstruction.createGoto(loopHeader));
            entry.addSuccessor(loopHeader);

            SSAValue zero = new SSAValue(PrimitiveType.INT, "zero");
            loopHeader.addInstruction(new ConstantInstruction(zero, new IntConstant(0)));
            loopHeader.addInstruction(new BranchInstruction(CompareOp.IFEQ, zero, exit, loopBody));
            loopHeader.addSuccessor(loopBody);
            loopHeader.addSuccessor(exit);

            loopBody.addInstruction(SimpleInstruction.createGoto(loopHeader));
            loopBody.addSuccessor(loopHeader);

            exit.addInstruction(new ReturnInstruction(null));

            SimulationEngine engine = new SimulationEngine(context);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertTrue(result.getTotalInstructions() > 0);
        }

        @Test
        void handleLoopWithMultiplePredecessors() {
            IRMethod method = new IRMethod("com/test/Test", "multiPredLoop", "()V", true);

            IRBlock entry = new IRBlock("entry");
            IRBlock path1 = new IRBlock("path1");
            IRBlock path2 = new IRBlock("path2");
            IRBlock loopHeader = new IRBlock("loop_header");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(path1);
            method.addBlock(path2);
            method.addBlock(loopHeader);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            SSAValue zero = new SSAValue(PrimitiveType.INT, "zero");
            entry.addInstruction(new ConstantInstruction(zero, new IntConstant(0)));
            entry.addInstruction(new BranchInstruction(CompareOp.IFNE, zero, path1, path2));
            entry.addSuccessor(path1);
            entry.addSuccessor(path2);

            path1.addInstruction(SimpleInstruction.createGoto(loopHeader));
            path1.addSuccessor(loopHeader);

            path2.addInstruction(SimpleInstruction.createGoto(loopHeader));
            path2.addSuccessor(loopHeader);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "condition");
            loopHeader.addInstruction(new ConstantInstruction(condition, new IntConstant(0)));
            loopHeader.addInstruction(new BranchInstruction(CompareOp.IFEQ, condition, exit, loopHeader));
            loopHeader.addSuccessor(loopHeader);
            loopHeader.addSuccessor(exit);

            exit.addInstruction(new ReturnInstruction(null));

            SimulationEngine engine = new SimulationEngine(context);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertTrue(result.getTotalInstructions() > 0);
        }
    }

    @Nested
    class PhiInstructionMergingTests {

        @Test
        void phiInstructionMerging() {
            IRMethod method = new IRMethod("com/test/Test", "phiMerge", "()V", true);

            IRBlock entry = new IRBlock("entry");
            IRBlock trueBranch = new IRBlock("true_branch");
            IRBlock falseBranch = new IRBlock("false_branch");
            IRBlock merge = new IRBlock("merge");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(trueBranch);
            method.addBlock(falseBranch);
            method.addBlock(merge);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "condition");
            entry.addInstruction(new ConstantInstruction(condition, new IntConstant(1)));
            entry.addInstruction(new BranchInstruction(CompareOp.IFNE, condition, trueBranch, falseBranch));
            entry.addSuccessor(trueBranch);
            entry.addSuccessor(falseBranch);

            SSAValue trueValue = new SSAValue(PrimitiveType.INT, "true_val");
            trueBranch.addInstruction(new ConstantInstruction(trueValue, new IntConstant(10)));
            trueBranch.addInstruction(SimpleInstruction.createGoto(merge));
            trueBranch.addSuccessor(merge);

            SSAValue falseValue = new SSAValue(PrimitiveType.INT, "false_val");
            falseBranch.addInstruction(new ConstantInstruction(falseValue, new IntConstant(20)));
            falseBranch.addInstruction(SimpleInstruction.createGoto(merge));
            falseBranch.addSuccessor(merge);

            PhiInstruction phi = new PhiInstruction(new SSAValue(PrimitiveType.INT, "merged"));
            phi.addIncoming(trueValue, trueBranch);
            phi.addIncoming(falseValue, falseBranch);
            merge.addPhi(phi);
            merge.addInstruction(SimpleInstruction.createGoto(exit));
            merge.addSuccessor(exit);

            exit.addInstruction(new ReturnInstruction(null));

            SimulationEngine engine = new SimulationEngine(context);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertTrue(result.getTotalInstructions() > 0);
        }
    }

    @Nested
    class ReturnInstructionTerminationTests {

        @Test
        void returnInstructionTerminationVoid() {
            IRMethod method = new IRMethod("com/test/Test", "returnVoid", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);

            entry.addInstruction(new ReturnInstruction(null));

            SimulationEngine engine = new SimulationEngine(context);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertEquals(1, result.getTotalInstructions());
        }

        @Test
        void multipleReturnPaths() {
            IRMethod method = new IRMethod("com/test/Test", "multiReturn", "()I", true);

            IRBlock entry = new IRBlock("entry");
            IRBlock trueBranch = new IRBlock("true_branch");
            IRBlock falseBranch = new IRBlock("false_branch");

            method.addBlock(entry);
            method.addBlock(trueBranch);
            method.addBlock(falseBranch);
            method.setEntryBlock(entry);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "condition");
            entry.addInstruction(new ConstantInstruction(condition, new IntConstant(1)));
            entry.addInstruction(new BranchInstruction(CompareOp.IFNE, condition, trueBranch, falseBranch));
            entry.addSuccessor(trueBranch);
            entry.addSuccessor(falseBranch);

            SSAValue trueValue = new SSAValue(PrimitiveType.INT, "true_ret");
            trueBranch.addInstruction(new ConstantInstruction(trueValue, new IntConstant(1)));
            trueBranch.addInstruction(new ReturnInstruction(trueValue));

            SSAValue falseValue = new SSAValue(PrimitiveType.INT, "false_ret");
            falseBranch.addInstruction(new ConstantInstruction(falseValue, new IntConstant(0)));
            falseBranch.addInstruction(new ReturnInstruction(falseValue));

            SimulationEngine engine = new SimulationEngine(context);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertTrue(result.getTotalInstructions() > 0);
        }
    }

    @Nested
    class ThrowInstructionTerminationTests {

        @Test
        void throwInstructionTermination() {
            IRMethod method = new IRMethod("com/test/Test", "throwException", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);

            SSAValue exception = new SSAValue(null, "exception");
            entry.addInstruction(new NewInstruction(exception, "java/lang/RuntimeException"));
            entry.addInstruction(SimpleInstruction.createThrow(exception));

            SimulationEngine engine = new SimulationEngine(context);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertEquals(2, result.getTotalInstructions());
        }

        @Test
        void throwInstructionWithCondition() {
            IRMethod method = new IRMethod("com/test/Test", "conditionalThrow", "()V", true);

            IRBlock entry = new IRBlock("entry");
            IRBlock throwBlock = new IRBlock("throw_block");
            IRBlock normalPath = new IRBlock("normal_path");

            method.addBlock(entry);
            method.addBlock(throwBlock);
            method.addBlock(normalPath);
            method.setEntryBlock(entry);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "condition");
            entry.addInstruction(new ConstantInstruction(condition, new IntConstant(1)));
            entry.addInstruction(new BranchInstruction(CompareOp.IFNE, condition, throwBlock, normalPath));
            entry.addSuccessor(throwBlock);
            entry.addSuccessor(normalPath);

            SSAValue exception = new SSAValue(null, "exception");
            throwBlock.addInstruction(new NewInstruction(exception, "java/lang/IllegalArgumentException"));
            throwBlock.addInstruction(SimpleInstruction.createThrow(exception));

            normalPath.addInstruction(new ReturnInstruction(null));

            SimulationEngine engine = new SimulationEngine(context);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertTrue(result.getTotalInstructions() > 0);
        }
    }

    @Nested
    class StepOperationTests {

        @Test
        void stepSingleInstruction() {
            SimulationEngine engine = new SimulationEngine(context);
            SimulationState state = SimulationState.empty();

            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            ConstantInstruction instr = new ConstantInstruction(value, new IntConstant(42));

            SimulationState newState = engine.step(state, instr);

            assertNotNull(newState);
        }

        @Test
        void stepBlock() {
            SimulationEngine engine = new SimulationEngine(context);
            SimulationState state = SimulationState.empty();

            IRBlock block = new IRBlock("test_block");
            SSAValue val1 = new SSAValue(PrimitiveType.INT, "val1");
            SSAValue val2 = new SSAValue(PrimitiveType.INT, "val2");

            block.addInstruction(new ConstantInstruction(val1, new IntConstant(10)));
            block.addInstruction(new ConstantInstruction(val2, new IntConstant(20)));

            SimulationState newState = engine.stepBlock(state, block);

            assertNotNull(newState);
        }
    }

    @Nested
    class SimulatePathTests {

        @Test
        void simulateSpecificPath() {
            IRMethod method = new IRMethod("com/test/Test", "path", "()V", true);

            IRBlock entry = new IRBlock("entry");
            IRBlock block1 = new IRBlock("block1");
            IRBlock block2 = new IRBlock("block2");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(block1);
            method.addBlock(block2);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addInstruction(SimpleInstruction.createGoto(block1));
            entry.addSuccessor(block1);

            SSAValue val1 = new SSAValue(PrimitiveType.INT, "val1");
            block1.addInstruction(new ConstantInstruction(val1, new IntConstant(10)));
            block1.addInstruction(SimpleInstruction.createGoto(block2));
            block1.addSuccessor(block2);

            SSAValue val2 = new SSAValue(PrimitiveType.INT, "val2");
            block2.addInstruction(new ConstantInstruction(val2, new IntConstant(20)));
            block2.addInstruction(SimpleInstruction.createGoto(exit));
            block2.addSuccessor(exit);

            exit.addInstruction(new ReturnInstruction(null));

            SimulationEngine engine = new SimulationEngine(context);
            List<IRBlock> path = List.of(entry, block1, block2, exit);
            SimulationResult result = engine.simulatePath(method, path);

            assertNotNull(result);
            assertTrue(result.getTotalInstructions() > 0);
        }

        @Test
        void simulateEmptyPath() {
            IRMethod method = new IRMethod("com/test/Test", "emptyPath", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction(null));

            SimulationEngine engine = new SimulationEngine(context);
            List<IRBlock> path = List.of();
            SimulationResult result = engine.simulatePath(method, path);

            assertNotNull(result);
            assertEquals(0, result.getTotalInstructions());
        }
    }

    @Nested
    class InstructionLevelTrackingTests {

        @Test
        void instructionLevelTracking() {
            SimulationContext trackingContext = SimulationContext.forPool(pool)
                .withMode(SimulationMode.INSTRUCTION);

            IRMethod method = new IRMethod("com/test/Test", "track", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);

            SSAValue val1 = new SSAValue(PrimitiveType.INT, "val1");
            SSAValue val2 = new SSAValue(PrimitiveType.INT, "val2");
            entry.addInstruction(new ConstantInstruction(val1, new IntConstant(10)));
            entry.addInstruction(new ConstantInstruction(val2, new IntConstant(20)));
            entry.addInstruction(new ReturnInstruction(null));

            SimulationEngine engine = new SimulationEngine(trackingContext);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertTrue(result.getTotalInstructions() > 0);
            assertNotNull(result.getAllStates());
        }
    }
}
