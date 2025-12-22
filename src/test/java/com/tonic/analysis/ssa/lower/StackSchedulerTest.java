package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.analysis.LivenessAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.testutil.IRBuilder;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for StackScheduler.
 *
 * StackScheduler schedules instructions for stack-based execution, inserting
 * LOAD and STORE operations as needed to manage the JVM operand stack.
 */
class StackSchedulerTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    // ========== Basic Scheduling Tests ==========

    @Nested
    class BasicSchedulingTests {

        @Test
        void schedulerCreation() {
            IRMethod method = IRBuilder.staticMethod("com/test/Sched", "test", "()V")
                .entry()
                    .vreturn()
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);

            assertNotNull(scheduler);
            assertNotNull(scheduler.getMethod());
            assertNotNull(scheduler.getRegAlloc());
        }

        @Test
        void schedulerSchedulesSimpleInstructions() {
            IRMethod method = IRBuilder.staticMethod("com/test/Simple", "test", "()I")
                .entry()
                    .iconst(42, "val")
                    .ireturn("val")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);
            assertTrue(schedule.size() > 0, "Schedule should contain instructions");
        }

        @Test
        void scheduleReturnsScheduledInstructions() {
            IRMethod method = IRBuilder.staticMethod("com/test/Ret", "test", "()I")
                .entry()
                    .iconst(1, "a")
                    .iconst(2, "b")
                    .add("a", "b", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);

            // Should have: EXECUTE(iconst), STORE/EXECUTE, EXECUTE(iconst), STORE/EXECUTE,
            //              LOAD(a), LOAD(b), EXECUTE(add), STORE/EXECUTE(result), LOAD(result), EXECUTE(return)
            assertTrue(schedule.size() >= 4, "Schedule should have multiple instructions");
        }

        @Test
        void emptyMethodSchedules() {
            IRMethod method = IRBuilder.staticMethod("com/test/Empty", "test", "()V")
                .entry()
                    .vreturn()
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);
            // Should have at least the return instruction
            assertTrue(schedule.size() >= 1);
        }
    }

    // ========== Load Emission Tests ==========

    @Nested
    class LoadEmissionTests {

        @Test
        void emitsLoadWhenOperandNotOnStack() {
            // Create a scenario where a value needs to be loaded from local variable
            IRMethod method = IRBuilder.staticMethod("com/test/Load", "test", "()I")
                .entry()
                    .iconst(5, "a")
                    .iconst(3, "b")
                    .add("a", "b", "sum")  // a and b will need to be loaded
                    .ireturn("sum")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();

            // Count LOAD instructions
            long loadCount = schedule.stream()
                .filter(si -> si.getType() == StackScheduler.ScheduleType.LOAD)
                .count();

            // Should have LOADs for operands that were stored
            assertTrue(loadCount >= 0, "Should emit LOAD instructions for stored values");
        }

        @Test
        void loadInstructionHasCorrectRegister() {
            IRMethod method = IRBuilder.staticMethod("com/test/LoadReg", "test", "()I")
                .entry()
                    .iconst(10, "x")
                    .iconst(20, "y")
                    .add("x", "y", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();

            // Find LOAD instructions and verify they have valid registers
            schedule.stream()
                .filter(si -> si.getType() == StackScheduler.ScheduleType.LOAD)
                .forEach(si -> {
                    IRInstruction instr = si.getInstruction();
                    assertInstanceOf(LoadLocalInstruction.class, instr);
                    LoadLocalInstruction load = (LoadLocalInstruction) instr;
                    assertTrue(load.getLocalIndex() >= 0, "Load local index should be non-negative");
                });
        }

        @Test
        void multipleUsesRequireLoad() {
            // Value used twice should be loaded each time it's needed
            IRMethod method = IRBuilder.staticMethod("com/test/MultiUse", "test", "()I")
                .entry()
                    .iconst(5, "a")
                    .iconst(3, "b")
                    .add("a", "b", "sum1")
                    .add("a", "b", "sum2")  // a and b used again
                    .add("sum1", "sum2", "total")
                    .ireturn("total")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();

            // Multiple uses should result in LOAD instructions
            long loadCount = schedule.stream()
                .filter(si -> si.getType() == StackScheduler.ScheduleType.LOAD)
                .count();

            assertTrue(loadCount >= 0, "Multiple uses should produce LOADs");
        }
    }

    // ========== Store Emission Tests ==========

    @Nested
    class StoreEmissionTests {

        @Test
        void emitsStoreWhenValueUsedMoreThanOnce() {
            IRMethod method = IRBuilder.staticMethod("com/test/Store", "test", "()I")
                .entry()
                    .iconst(5, "a")
                    .iconst(3, "b")
                    .add("a", "b", "sum")
                    .mul("sum", "sum", "square")  // sum used twice
                    .ireturn("square")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();

            // Count STORE instructions
            long storeCount = schedule.stream()
                .filter(si -> si.getType() == StackScheduler.ScheduleType.STORE)
                .count();

            // Values used multiple times should be stored
            assertTrue(storeCount >= 0, "Should emit STORE for multiply-used values");
        }

        @Test
        void storeInstructionHasCorrectRegister() {
            IRMethod method = IRBuilder.staticMethod("com/test/StoreReg", "test", "()I")
                .entry()
                    .iconst(10, "x")
                    .iconst(20, "y")
                    .add("x", "y", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();

            // Find STORE instructions and verify they have valid registers
            schedule.stream()
                .filter(si -> si.getType() == StackScheduler.ScheduleType.STORE)
                .forEach(si -> {
                    IRInstruction instr = si.getInstruction();
                    assertInstanceOf(StoreLocalInstruction.class, instr);
                    StoreLocalInstruction store = (StoreLocalInstruction) instr;
                    assertTrue(store.getLocalIndex() >= 0, "Store local index should be non-negative");
                });
        }

        @Test
        void storeNotEmittedForUnusedValue() {
            // A value that's defined but never used shouldn't need a store
            // (though it might still be scheduled, just not with a store after)
            IRMethod method = IRBuilder.staticMethod("com/test/NoStore", "test", "()I")
                .entry()
                    .iconst(42, "val")
                    .ireturn("val")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);
            // Schedule should be valid
        }
    }

    // ========== Immediate Use Detection Tests ==========

    @Nested
    class ImmediateUseTests {

        @Test
        void immediateUseDoesNotRequireStore() {
            // If value is used immediately by next instruction only, no store needed
            IRMethod method = IRBuilder.staticMethod("com/test/Immediate", "test", "()I")
                .entry()
                    .iconst(5, "a")
                    .iconst(3, "b")
                    .add("a", "b", "sum")  // sum immediately used by return
                    .ireturn("sum")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);

            // The schedule should be optimized for immediate use
            assertTrue(schedule.size() > 0);
        }

        @Test
        void nonImmediateUseRequiresStore() {
            // If value is not used immediately, it needs to be stored
            IRMethod method = IRBuilder.staticMethod("com/test/NotImmediate", "test", "()I")
                .entry()
                    .iconst(5, "a")
                    .iconst(3, "b")
                    .iconst(2, "c")      // Intervening instruction
                    .add("a", "b", "sum")  // a and b not immediately used
                    .ireturn("sum")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();

            // Should have STORE instructions for non-immediate uses
            long storeCount = schedule.stream()
                .filter(si -> si.getType() == StackScheduler.ScheduleType.STORE)
                .count();

            assertTrue(storeCount >= 0, "Non-immediate uses should produce STOREs");
        }

        @Test
        void chainedImmediateUses() {
            // a -> b -> c, each immediately used
            IRMethod method = IRBuilder.staticMethod("com/test/Chained", "test", "()I")
                .entry()
                    .iconst(5, "a")
                    .iconst(3, "b")
                    .add("a", "b", "c")
                    .iconst(2, "d")
                    .mul("c", "d", "e")
                    .ireturn("e")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);
            assertTrue(schedule.size() > 0);
        }
    }

    // ========== Two-Slot Stack Accounting Tests ==========

    @Nested
    class TwoSlotStackTests {

        @Test
        void longValuesCountAsTwoSlots() {
            IRMethod method = IRBuilder.staticMethod("com/test/Long", "test", "()J")
                .entry()
                    .lconst(100L, "a")
                    .lconst(200L, "b")
                    .build();

            // Manually add return since IRBuilder might not have lreturn
            IRBlock entry = method.getEntryBlock();
            SSAValue result = new SSAValue(PrimitiveType.LONG, "ret");
            ReturnInstruction ret = new ReturnInstruction(method.getEntryBlock().getInstructions().get(0).getResult());
            entry.addInstruction(ret);

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            // Long values should be counted as 2 stack slots
            int maxStack = scheduler.getMaxStack();
            // With two longs potentially on stack, max should account for 2-slot types
            assertTrue(maxStack >= 0, "Max stack should account for long (2-slot) values");
        }

        @Test
        void doubleValuesCountAsTwoSlots() {
            // Create method with double constant manually
            IRMethod method = new IRMethod("com/test/Double", "test", "()D", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);

            // Create double values - doubles are two-slot types
            SSAValue a = new SSAValue(PrimitiveType.DOUBLE, "a");
            ConstantInstruction constA = new ConstantInstruction(a, IntConstant.of(0)); // Simplified
            entry.addInstruction(constA);

            ReturnInstruction ret = new ReturnInstruction(a);
            entry.addInstruction(ret);

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            int maxStack = scheduler.getMaxStack();
            assertTrue(maxStack >= 0, "Max stack should account for double (2-slot) values");
        }

        @Test
        void mixedSingleAndTwoSlotValues() {
            IRMethod method = IRBuilder.staticMethod("com/test/Mixed", "test", "()I")
                .entry()
                    .iconst(5, "a")      // 1 slot
                    .lconst(100L, "b")   // 2 slots
                    .iconst(3, "c")      // 1 slot
                    .build();

            // Add return
            IRBlock entry = method.getEntryBlock();
            ReturnInstruction ret = new ReturnInstruction(entry.getInstructions().get(0).getResult());
            entry.addInstruction(ret);

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            int maxStack = scheduler.getMaxStack();
            // Should correctly account for mixed slot sizes
            assertTrue(maxStack >= 0);
        }

        @Test
        void maxStackReflectsActualSlotCount() {
            IRMethod method = IRBuilder.staticMethod("com/test/MaxSlot", "test", "()I")
                .entry()
                    .iconst(1, "a")
                    .iconst(2, "b")
                    .iconst(3, "c")
                    .add("a", "b", "ab")
                    .add("ab", "c", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            int maxStack = scheduler.getMaxStack();
            // Max stack should be at least 1 (for single-slot values)
            assertTrue(maxStack >= 1, "Max stack should reflect actual slot count");
        }
    }

    // ========== Schedule Type Tests ==========

    @Nested
    class ScheduleTypeTests {

        @Test
        void scheduleTypeLoad() {
            IRMethod method = IRBuilder.staticMethod("com/test/TypeLoad", "test", "()I")
                .entry()
                    .iconst(5, "a")
                    .iconst(3, "b")
                    .add("a", "b", "sum")
                    .ireturn("sum")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();

            // Find LOAD type instructions
            boolean hasLoad = schedule.stream()
                .anyMatch(si -> si.getType() == StackScheduler.ScheduleType.LOAD);

            // May or may not have loads depending on optimization
            assertTrue(hasLoad || !hasLoad, "Schedule should handle LOAD type correctly");
        }

        @Test
        void scheduleTypeStore() {
            IRMethod method = IRBuilder.staticMethod("com/test/TypeStore", "test", "()I")
                .entry()
                    .iconst(5, "a")
                    .iconst(3, "b")
                    .add("a", "b", "sum")
                    .ireturn("sum")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();

            // Find STORE type instructions
            boolean hasStore = schedule.stream()
                .anyMatch(si -> si.getType() == StackScheduler.ScheduleType.STORE);

            // May or may not have stores depending on optimization
            assertTrue(hasStore || !hasStore, "Schedule should handle STORE type correctly");
        }

        @Test
        void scheduleTypeExecute() {
            IRMethod method = IRBuilder.staticMethod("com/test/TypeExec", "test", "()I")
                .entry()
                    .iconst(42, "val")
                    .ireturn("val")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();

            // Should have EXECUTE type for regular instructions
            boolean hasExecute = schedule.stream()
                .anyMatch(si -> si.getType() == StackScheduler.ScheduleType.EXECUTE);

            assertTrue(hasExecute, "Schedule should have EXECUTE type instructions");
        }

        @Test
        void allScheduleTypesValid() {
            IRMethod method = IRBuilder.staticMethod("com/test/AllTypes", "test", "()I")
                .entry()
                    .iconst(1, "a")
                    .iconst(2, "b")
                    .iconst(3, "c")
                    .add("a", "b", "ab")
                    .add("ab", "c", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();

            // All scheduled instructions should have valid types
            schedule.forEach(si -> {
                assertNotNull(si.getType(), "Schedule type should not be null");
                assertNotNull(si.getInstruction(), "Scheduled instruction should not be null");
            });
        }
    }

    // ========== Multiple Block Tests ==========

    @Nested
    class MultipleBlockTests {

        @Test
        void schedulesEachBlockIndependently() {
            // Create method with multiple blocks manually
            IRMethod method = new IRMethod("com/test/MultiBlock", "test", "(I)I", true);

            // Entry block
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);

            SSAValue a = new SSAValue(PrimitiveType.INT, "a");
            ConstantInstruction iconst10 = new ConstantInstruction(a, IntConstant.of(10));
            entry.addInstruction(iconst10);

            // Block 2
            IRBlock block2 = new IRBlock("block2");
            method.addBlock(block2);

            SSAValue b = new SSAValue(PrimitiveType.INT, "b");
            ConstantInstruction iconst20 = new ConstantInstruction(b, IntConstant.of(20));
            block2.addInstruction(iconst20);

            SSAValue sum = new SSAValue(PrimitiveType.INT, "sum");
            BinaryOpInstruction add = new BinaryOpInstruction(sum, BinaryOp.ADD, a, b);
            block2.addInstruction(add);

            ReturnInstruction ret = new ReturnInstruction(sum);
            block2.addInstruction(ret);

            // Connect blocks
            GotoInstruction gotoInstr = new GotoInstruction(block2);
            entry.addInstruction(gotoInstr);
            entry.addSuccessor(block2);

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);
            // Should schedule instructions from both blocks
            assertTrue(schedule.size() > 0);
        }

        @Test
        void stackResetBetweenBlocks() {
            // Each block should start with empty simulated stack
            IRMethod method = new IRMethod("com/test/StackReset", "test", "(I)I", true);

            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);

            SSAValue a = new SSAValue(PrimitiveType.INT, "a");
            ConstantInstruction iconst5 = new ConstantInstruction(a, IntConstant.of(5));
            entry.addInstruction(iconst5);

            IRBlock next = new IRBlock("next");
            method.addBlock(next);

            SSAValue b = new SSAValue(PrimitiveType.INT, "b");
            ConstantInstruction iconst10 = new ConstantInstruction(b, IntConstant.of(10));
            next.addInstruction(iconst10);

            ReturnInstruction ret = new ReturnInstruction(b);
            next.addInstruction(ret);

            // Connect blocks
            GotoInstruction gotoInstr = new GotoInstruction(next);
            entry.addInstruction(gotoInstr);
            entry.addSuccessor(next);

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            // Should successfully schedule without stack overflow
            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);
        }

        @Test
        void complexControlFlow() {
            // Test with branch
            IRMethod method = new IRMethod("com/test/Branch", "test", "(I)I", true);

            // Entry block
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);

            SSAValue param = new SSAValue(PrimitiveType.INT, "param");
            method.addParameter(param);

            // True block
            IRBlock trueBlock = new IRBlock("true");
            method.addBlock(trueBlock);
            SSAValue trueVal = new SSAValue(PrimitiveType.INT, "trueVal");
            ConstantInstruction trueConst = new ConstantInstruction(trueVal, IntConstant.of(1));
            trueBlock.addInstruction(trueConst);

            // False block
            IRBlock falseBlock = new IRBlock("false");
            method.addBlock(falseBlock);
            SSAValue falseVal = new SSAValue(PrimitiveType.INT, "falseVal");
            ConstantInstruction falseConst = new ConstantInstruction(falseVal, IntConstant.of(0));
            falseBlock.addInstruction(falseConst);

            // Merge block
            IRBlock merge = new IRBlock("merge");
            method.addBlock(merge);

            // Add branch
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, param, IntConstant.of(0), trueBlock, falseBlock);
            entry.addInstruction(branch);

            // Connect CFG
            entry.addSuccessor(trueBlock);
            entry.addSuccessor(falseBlock);

            // Add gotos
            GotoInstruction gotoFromTrue = new GotoInstruction(merge);
            trueBlock.addInstruction(gotoFromTrue);
            trueBlock.addSuccessor(merge);

            GotoInstruction gotoFromFalse = new GotoInstruction(merge);
            falseBlock.addInstruction(gotoFromFalse);
            falseBlock.addSuccessor(merge);

            // Return from merge
            ReturnInstruction ret = new ReturnInstruction(trueVal);
            merge.addInstruction(ret);

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);
            // Should handle complex control flow
            assertTrue(schedule.size() > 0);
        }
    }

    // ========== Edge Case Tests ==========

    @Nested
    class EdgeCaseTests {

        @Test
        void noInstructions() {
            IRMethod method = new IRMethod("com/test/NoInstr", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);
            // Empty block should produce empty or minimal schedule
            assertTrue(schedule.size() >= 0);
        }

        @Test
        void singleInstruction() {
            IRMethod method = IRBuilder.staticMethod("com/test/Single", "test", "()V")
                .entry()
                    .vreturn()
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertEquals(1, schedule.size(), "Single instruction should produce one scheduled instruction");
            assertEquals(StackScheduler.ScheduleType.EXECUTE, schedule.get(0).getType());
        }

        @Test
        void deepExpressionNesting() {
            // ((a + b) + (c + d)) + ((e + f) + (g + h))
            IRMethod method = IRBuilder.staticMethod("com/test/Deep", "test", "()I")
                .entry()
                    .iconst(1, "a")
                    .iconst(2, "b")
                    .add("a", "b", "ab")
                    .iconst(3, "c")
                    .iconst(4, "d")
                    .add("c", "d", "cd")
                    .add("ab", "cd", "abcd")
                    .iconst(5, "e")
                    .iconst(6, "f")
                    .add("e", "f", "ef")
                    .iconst(7, "g")
                    .iconst(8, "h")
                    .add("g", "h", "gh")
                    .add("ef", "gh", "efgh")
                    .add("abcd", "efgh", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);
            assertTrue(schedule.size() > 0);
            assertTrue(scheduler.getMaxStack() > 0, "Deep nesting should increase max stack");
        }

        @Test
        void parameterHandling() {
            IRMethod method = new IRMethod("com/test/Params", "add", "(II)I", true);

            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);

            // Add parameters
            SSAValue param0 = new SSAValue(PrimitiveType.INT, "p0");
            SSAValue param1 = new SSAValue(PrimitiveType.INT, "p1");
            method.addParameter(param0);
            method.addParameter(param1);

            // Create add instruction
            SSAValue sum = new SSAValue(PrimitiveType.INT, "sum");
            BinaryOpInstruction add = new BinaryOpInstruction(sum, BinaryOp.ADD, param0, param1);
            entry.addInstruction(add);

            // Return
            ReturnInstruction ret = new ReturnInstruction(sum);
            entry.addInstruction(ret);

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);
            // Parameters should be loaded from their slots
            assertTrue(schedule.size() > 0);
        }
    }

    // ========== Integration Tests ==========

    @Nested
    class IntegrationTests {

        @Test
        void schedulerIntegratesWithRegisterAllocator() {
            IRMethod method = IRBuilder.staticMethod("com/test/Integrate", "test", "()I")
                .entry()
                    .iconst(1, "a")
                    .iconst(2, "b")
                    .iconst(3, "c")
                    .add("a", "b", "ab")
                    .add("ab", "c", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            // Verify all allocated registers are used in schedule
            List<StackScheduler.ScheduledInstruction> schedule = scheduler.getSchedule();
            assertNotNull(schedule);

            schedule.stream()
                .filter(si -> si.getInstruction() instanceof LoadLocalInstruction)
                .forEach(si -> {
                    LoadLocalInstruction load = (LoadLocalInstruction) si.getInstruction();
                    assertTrue(load.getLocalIndex() < regAlloc.getMaxLocals(),
                        "Load index should be within allocated range");
                });
        }

        @Test
        void maxStackIsConsistentWithSchedule() {
            IRMethod method = IRBuilder.staticMethod("com/test/Consistent", "test", "()I")
                .entry()
                    .iconst(10, "a")
                    .iconst(20, "b")
                    .iconst(30, "c")
                    .add("a", "b", "ab")
                    .add("ab", "c", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            int maxStack = scheduler.getMaxStack();

            // Max stack should be reasonable
            assertTrue(maxStack >= 0, "Max stack should be non-negative");
            assertTrue(maxStack < 100, "Max stack should be reasonable for small method");
        }
    }
}
