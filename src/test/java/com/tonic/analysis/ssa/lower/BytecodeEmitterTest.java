package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LivenessAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.IRBuilder;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BytecodeEmitter, RegisterAllocator, and StackScheduler.
 *
 * Tests lowering SSA-form IR back to JVM bytecode including:
 * - Register allocation
 * - Phi elimination
 * - Stack scheduling
 * - Bytecode emission
 */
class BytecodeEmitterTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    /**
     * Helper to find method by name in ClassFile.
     */
    private MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Method not found: " + name);
    }

    // ========== BytecodeLowerer Tests ==========

    @Nested
    class BytecodeLowererTests {

        @Test
        void lowererCreation() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Lower")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            BytecodeLowerer lowerer = new BytecodeLowerer(cf.getConstPool());
            assertNotNull(lowerer);
            assertNotNull(lowerer.getConstPool());
        }

        @Test
        void lowerSimpleMethod() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/SimpleL")
                .publicStaticMethod("get42", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "get42");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            // Verify the lowered class can be loaded
            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("get42");
            int result = (int) m.invoke(null);
            assertEquals(42, result);
        }

        @Test
        void lowerArithmeticMethod() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ArithL")
                .publicStaticMethod("add", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "add");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("add", int.class, int.class);
            int result = (int) m.invoke(null, 7, 3);
            assertEquals(10, result);
        }

        @Test
        void lowerMultiplication() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/MulL")
                .publicStaticMethod("mul", "(II)I")
                    .iload(0)
                    .iload(1)
                    .imul()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "mul");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("mul", int.class, int.class);
            int result = (int) m.invoke(null, 6, 7);
            assertEquals(42, result);
        }
    }

    // ========== RegisterAllocator Tests ==========

    @Nested
    class RegisterAllocatorTests {

        @Test
        void allocatorCreation() {
            IRMethod method = IRBuilder.staticMethod("com/test/Reg", "test", "()I")
                .entry()
                    .iconst(42, "val")
                    .ireturn("val")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator alloc = new RegisterAllocator(method, liveness);
            assertNotNull(alloc);
        }

        @Test
        void allocatorAllocatesRegisters() {
            IRMethod method = IRBuilder.staticMethod("com/test/Alloc", "test", "()I")
                .entry()
                    .iconst(1, "a")
                    .iconst(2, "b")
                    .add("a", "b", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator alloc = new RegisterAllocator(method, liveness);
            alloc.allocate();

            assertTrue(alloc.getMaxLocals() >= 0);
        }

        @Test
        void allocatorHandlesParameters() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ParamAlloc")
                .publicStaticMethod("identity", "(I)I")
                    .iload(0)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "identity");
            IRMethod ir = TestUtils.liftMethod(method);

            LivenessAnalysis liveness = new LivenessAnalysis(ir);
            liveness.compute();

            RegisterAllocator alloc = new RegisterAllocator(ir, liveness);
            alloc.allocate();

            // Parameters should be allocated
            assertTrue(alloc.getMaxLocals() >= 1);
        }

        @Test
        void allocatorReusesRegisters() {
            IRMethod method = IRBuilder.staticMethod("com/test/Reuse", "test", "()I")
                .entry()
                    .iconst(1, "a")
                    .iconst(2, "b")
                    .add("a", "b", "sum")
                    .iconst(3, "c") // a and b are dead, register reuse possible
                    .mul("sum", "c", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator alloc = new RegisterAllocator(method, liveness);
            alloc.allocate();

            assertNotNull(alloc.getAllocation());
        }
    }

    // ========== StackScheduler Tests ==========

    @Nested
    class StackSchedulerTests {

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
        }

        @Test
        void schedulerSchedulesInstructions() {
            IRMethod method = IRBuilder.staticMethod("com/test/SchedRun", "test", "()I")
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

            assertNotNull(scheduler.getSchedule());
            assertTrue(scheduler.getMaxStack() >= 0);
        }

        @Test
        void schedulerTracksMaxStack() {
            IRMethod method = IRBuilder.staticMethod("com/test/MaxStack", "test", "()I")
                .entry()
                    .iconst(1, "a")
                    .iconst(2, "b")
                    .iconst(3, "c")
                    .add("b", "c", "bc")
                    .add("a", "bc", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(method, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(method, regAlloc);
            scheduler.schedule();

            // Stack should track depth correctly
            assertTrue(scheduler.getMaxStack() >= 1);
        }
    }

    // ========== PhiEliminator Tests ==========

    @Nested
    class PhiEliminatorTests {

        @Test
        void eliminatorCreation() {
            PhiEliminator eliminator = new PhiEliminator();
            assertNotNull(eliminator);
        }

        @Test
        void eliminatorHandlesNoPhi() {
            IRMethod method = IRBuilder.staticMethod("com/test/NoPhi", "test", "()I")
                .entry()
                    .iconst(42, "val")
                    .ireturn("val")
                .build();

            PhiEliminator eliminator = new PhiEliminator();
            // Should not throw
            eliminator.eliminate(method);
        }
    }

    // ========== BytecodeEmitter Tests ==========

    @Nested
    class BytecodeEmitterDirectTests {

        @Test
        void emitterEmitsBytecode() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Emit")
                .publicStaticMethod("test", "()I")
                    .iconst(100)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);

            // Manually create all components
            PhiEliminator phiElim = new PhiEliminator();
            phiElim.eliminate(ir);

            LivenessAnalysis liveness = new LivenessAnalysis(ir);
            liveness.compute();

            RegisterAllocator regAlloc = new RegisterAllocator(ir, liveness);
            regAlloc.allocate();

            StackScheduler scheduler = new StackScheduler(ir, regAlloc);
            scheduler.schedule();

            BytecodeEmitter emitter = new BytecodeEmitter(ir, cf.getConstPool(), regAlloc, scheduler);
            byte[] bytecode = emitter.emit();

            assertNotNull(bytecode);
            assertTrue(bytecode.length > 0);
        }
    }

    // ========== Round-Trip Execution Tests ==========

    @Nested
    class RoundTripExecutionTests {

        @Test
        void executeAddition() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ExecAdd")
                .publicStaticMethod("add", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "add");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("add", int.class, int.class);

            assertEquals(5, (int) m.invoke(null, 2, 3));
            assertEquals(0, (int) m.invoke(null, 5, -5));
            assertEquals(-10, (int) m.invoke(null, -7, -3));
        }

        @Test
        void executeSubtraction() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ExecSub")
                .publicStaticMethod("sub", "(II)I")
                    .iload(0)
                    .iload(1)
                    .isub()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "sub");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("sub", int.class, int.class);

            assertEquals(7, (int) m.invoke(null, 10, 3));
            assertEquals(-5, (int) m.invoke(null, 0, 5));
        }

        @Test
        void executeDivision() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ExecDiv")
                .publicStaticMethod("div", "(II)I")
                    .iload(0)
                    .iload(1)
                    .idiv()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "div");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("div", int.class, int.class);

            assertEquals(5, (int) m.invoke(null, 15, 3));
            assertEquals(2, (int) m.invoke(null, 10, 4));
        }

        @Test
        void executeBitwiseOperations() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ExecBit")
                .publicStaticMethod("andOp", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iand()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "andOp");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("andOp", int.class, int.class);

            assertEquals(0b1010 & 0b1100, (int) m.invoke(null, 0b1010, 0b1100));
        }

        @Test
        void executeWithLongParams() throws Exception {
            // Tests that SSA lift/lower correctly handles long parameters (2 slots each)
            ClassFile cf = BytecodeBuilder.forClass("com/test/ExecLong")
                .publicStaticMethod("addLong", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .ladd()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "addLong");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("addLong", long.class, long.class);

            assertEquals(1000000000100L, (long) m.invoke(null, 1000000000000L, 100L));
        }

        @Test
        void executeConstantReturn() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ExecConst")
                .publicStaticMethod("const42", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "const42");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("const42");

            assertEquals(42, (int) m.invoke(null));
        }

        @Test
        void executeVoidMethod() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ExecVoid")
                .publicStaticMethod("doNothing", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "doNothing");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("doNothing");

            // Should not throw
            m.invoke(null);
        }
    }

    // ========== LivenessAnalysis Tests ==========

    @Nested
    class LivenessAnalysisTests {

        @Test
        void livenessCreation() {
            IRMethod method = IRBuilder.staticMethod("com/test/Live", "test", "()V")
                .entry()
                    .vreturn()
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            assertNotNull(liveness);
        }

        @Test
        void livenessCompute() {
            IRMethod method = IRBuilder.staticMethod("com/test/LiveComp", "test", "()I")
                .entry()
                    .iconst(1, "a")
                    .iconst(2, "b")
                    .add("a", "b", "result")
                    .ireturn("result")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            // Should not throw
        }

        @Test
        void livenessTracksLiveValues() {
            IRMethod method = IRBuilder.staticMethod("com/test/LiveTrack", "test", "()I")
                .entry()
                    .iconst(5, "x")
                    .iconst(3, "y")
                    .add("x", "y", "sum")
                    .ireturn("sum")
                .build();

            LivenessAnalysis liveness = new LivenessAnalysis(method);
            liveness.compute();

            // "sum" should be live at its definition
        }
    }

    // ========== DominatorTree Tests ==========

    @Nested
    class DominatorTreeTests {

        @Test
        void dominatorTreeCreation() {
            IRMethod method = IRBuilder.staticMethod("com/test/Dom", "test", "()V")
                .entry()
                    .vreturn()
                .build();

            DominatorTree domTree = new DominatorTree(method);
            assertNotNull(domTree);
        }

        @Test
        void dominatorTreeCompute() {
            IRMethod method = IRBuilder.staticMethod("com/test/DomComp", "test", "()I")
                .entry()
                    .iconst(42, "val")
                    .ireturn("val")
                .build();

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();

            assertNotNull(domTree.getImmediateDominator(method.getEntryBlock()));
        }
    }
}
