package com.tonic.analysis.simulation;

import com.tonic.analysis.simulation.core.*;
import com.tonic.analysis.simulation.listener.*;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InterProceduralEngine and simulation listeners.
 *
 * Tests core simulation functionality, listener callbacks, and result building.
 */
class InterProceduralEngineTest {

    private ClassPool pool;
    private SimulationContext context;

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
        pool = TestUtils.emptyPool();
        context = SimulationContext.forPool(pool);
    }

    // ========== Engine Setup Tests ==========

    @Nested
    class EngineSetupTests {

        @Test
        void engineCreation() {
            InterProceduralEngine engine = new InterProceduralEngine(context);
            assertNotNull(engine);
            assertNotNull(engine.getContext());
            assertSame(context, engine.getContext());
        }

        @Test
        void engineAddListener() {
            InterProceduralEngine engine = new InterProceduralEngine(context);
            AllocationListener listener = new AllocationListener();

            InterProceduralEngine result = engine.addListener(listener);
            assertSame(engine, result); // Fluent API
        }

        @Test
        void engineAddMultipleListeners() {
            InterProceduralEngine engine = new InterProceduralEngine(context);
            AllocationListener alloc = new AllocationListener();
            MethodCallListener call = new MethodCallListener();

            engine.addListeners(alloc, call);
            // No exception means success
        }

        @Test
        void engineCallStackInitiallyEmpty() {
            InterProceduralEngine engine = new InterProceduralEngine(context);
            assertNotNull(engine.getCallStack());
        }

        @Test
        void engineMethodsSimulatedInitiallyZero() {
            InterProceduralEngine engine = new InterProceduralEngine(context);
            assertEquals(0, engine.getMethodsSimulated());
        }
    }

    // ========== SimulationContext Tests ==========

    @Nested
    class SimulationContextTests {

        @Test
        void contextForPool() {
            SimulationContext ctx = SimulationContext.forPool(pool);
            assertNotNull(ctx);
        }

        @Test
        void contextDefaults() {
            SimulationContext ctx = SimulationContext.defaults();
            assertNotNull(ctx);
        }

        @Test
        void contextForMethod() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Ctx")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();
            MethodEntry method = cf.getMethods().get(0);

            SimulationContext ctx = SimulationContext.forMethod(method);
            assertNotNull(ctx);
        }

        @Test
        void contextForClass() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/CtxClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            SimulationContext ctx = SimulationContext.forClass(cf);
            assertNotNull(ctx);
        }
    }

    // ========== AllocationListener Tests ==========

    @Nested
    class AllocationListenerTests {

        @Test
        void allocationListenerCreation() {
            AllocationListener listener = new AllocationListener();
            assertNotNull(listener);
        }

        @Test
        void allocationListenerWithSiteTracking() {
            AllocationListener listener = new AllocationListener(true);
            assertNotNull(listener);
        }

        @Test
        void allocationListenerWithoutSiteTracking() {
            AllocationListener listener = new AllocationListener(false);
            assertNotNull(listener);
        }

        @Test
        void allocationListenerInitialCounts() {
            AllocationListener listener = new AllocationListener();
            // Trigger reset via onSimulationStart
            listener.onSimulationStart(null);

            assertEquals(0, listener.getObjectAllocationCount());
            assertEquals(0, listener.getArrayAllocationCount());
            assertEquals(0, listener.getTotalCount());
        }

        @Test
        void allocationListenerGetDistinctTypeCount() {
            AllocationListener listener = new AllocationListener();
            listener.onSimulationStart(null);

            assertEquals(0, listener.getDistinctTypeCount());
        }

        @Test
        void allocationListenerGetAllocationsByType() {
            AllocationListener listener = new AllocationListener();
            listener.onSimulationStart(null);

            assertNotNull(listener.getAllocationsByType());
            assertTrue(listener.getAllocationsByType().isEmpty());
        }

        @Test
        void allocationListenerGetCountForType() {
            AllocationListener listener = new AllocationListener();
            listener.onSimulationStart(null);

            assertEquals(0, listener.getCountForType("SomeType"));
        }

        @Test
        void allocationListenerGetAllocationSites() {
            AllocationListener listener = new AllocationListener();
            listener.onSimulationStart(null);

            assertNotNull(listener.getAllocationSites());
            assertTrue(listener.getAllocationSites().isEmpty());
        }

        @Test
        void allocationListenerGetAllocationsOf() {
            AllocationListener listener = new AllocationListener();
            listener.onSimulationStart(null);

            List<AllocationListener.AllocationSite> sites = listener.getAllocationsOf("SomeType");
            assertNotNull(sites);
            assertTrue(sites.isEmpty());
        }
    }

    // ========== FieldAccessListener Tests ==========

    @Nested
    class FieldAccessListenerTests {

        @Test
        void fieldAccessListenerCreation() {
            FieldAccessListener listener = new FieldAccessListener();
            assertNotNull(listener);
        }

        @Test
        void fieldAccessListenerInitialCounts() {
            FieldAccessListener listener = new FieldAccessListener();
            listener.onSimulationStart(null);

            assertEquals(0, listener.getFieldReadCount());
            assertEquals(0, listener.getFieldWriteCount());
        }
    }

    // ========== MethodCallListener Tests ==========

    @Nested
    class MethodCallListenerTests {

        @Test
        void methodCallListenerCreation() {
            MethodCallListener listener = new MethodCallListener();
            assertNotNull(listener);
        }

        @Test
        void methodCallListenerInitialCounts() {
            MethodCallListener listener = new MethodCallListener();
            listener.onSimulationStart(null);

            assertEquals(0, listener.getTotalCalls());
        }

        @Test
        void methodCallListenerDistinctMethods() {
            MethodCallListener listener = new MethodCallListener();
            listener.onSimulationStart(null);

            assertEquals(0, listener.getDistinctMethodCount());
        }
    }

    // ========== StackOperationListener Tests ==========

    @Nested
    class StackOperationListenerTests {

        @Test
        void stackOperationListenerCreation() {
            StackOperationListener listener = new StackOperationListener();
            assertNotNull(listener);
        }
    }

    // ========== ControlFlowListener Tests ==========

    @Nested
    class ControlFlowListenerTests {

        @Test
        void controlFlowListenerCreation() {
            ControlFlowListener listener = new ControlFlowListener();
            assertNotNull(listener);
        }

        @Test
        void controlFlowListenerInitialCounts() {
            ControlFlowListener listener = new ControlFlowListener();
            listener.onSimulationStart(null);

            assertEquals(0, listener.getBranchCount());
        }
    }

    // ========== SimulationResult Tests ==========

    @Nested
    class SimulationResultTests {

        @Test
        void simulationResultBuilder() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            SimulationResult.Builder builder = SimulationResult.builder()
                .method(method)
                .totalInstructions(10)
                .maxStackDepth(5);

            SimulationResult result = builder.build();

            assertNotNull(result);
            assertEquals(method, result.getMethod());
            assertEquals(10, result.getTotalInstructions());
            assertEquals(5, result.getMaxStackDepth());
        }

        @Test
        void simulationResultGetAllStates() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            SimulationResult result = SimulationResult.builder()
                .method(method)
                .build();

            assertNotNull(result.getAllStates());
        }

        @Test
        void simulationResultGetStateAtInvalid() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            SimulationResult result = SimulationResult.builder()
                .method(method)
                .build();

            assertNull(result.getStateAt(-1));
            assertNull(result.getStateAt(100));
        }
    }

    // ========== SimulationState Tests ==========

    @Nested
    class SimulationStateTests {

        @Test
        void simulationStateEmpty() {
            SimulationState state = SimulationState.empty();
            assertNotNull(state);
        }

        @Test
        void simulationStateStackDepth() {
            SimulationState state = SimulationState.empty();
            assertTrue(state.stackDepth() >= 0);
        }

        @Test
        void simulationStateMaxStackDepth() {
            SimulationState state = SimulationState.empty();
            assertTrue(state.maxStackDepth() >= 0);
        }
    }

    // ========== CompositeListener Tests ==========

    @Nested
    class CompositeListenerTests {

        @Test
        void compositeListenerCreation() {
            CompositeListener listener = new CompositeListener();
            assertNotNull(listener);
        }

        @Test
        void compositeListenerAdd() {
            CompositeListener composite = new CompositeListener();
            AllocationListener alloc = new AllocationListener();

            composite.add(alloc);
            // No exception means success
        }

        @Test
        void compositeListenerOnSimulationStart() {
            CompositeListener composite = new CompositeListener();
            AllocationListener alloc = new AllocationListener();
            composite.add(alloc);

            // Should not throw
            composite.onSimulationStart(null);
        }
    }

    // ========== Basic Simulation Tests ==========

    @Nested
    class BasicSimulationTests {

        @Test
        void simulateEmptyMethod() {
            IRMethod method = new IRMethod("com/test/Test", "empty", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction(null));

            InterProceduralEngine engine = new InterProceduralEngine(context);
            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            assertEquals(method, result.getMethod());
        }

        @Test
        void simulateMethodWithListener() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction(null));

            AllocationListener listener = new AllocationListener();
            InterProceduralEngine engine = new InterProceduralEngine(context);
            engine.addListener(listener);

            SimulationResult result = engine.simulate(method);

            assertNotNull(result);
            // Listener should have been notified
        }

        @Test
        void simulateIncrementsMethodsSimulated() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction(null));

            InterProceduralEngine engine = new InterProceduralEngine(context);

            assertEquals(0, engine.getMethodsSimulated());

            engine.simulate(method);

            assertTrue(engine.getMethodsSimulated() >= 1);
        }
    }

    // ========== AllocationSite Tests ==========

    @Nested
    class AllocationSiteTests {

        @Test
        void allocationSiteConstruction() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "arr");
            SSAValue length = new SSAValue(PrimitiveType.INT, "len");
            NewArrayInstruction instr = new NewArrayInstruction(result, PrimitiveType.INT, length);

            AllocationListener.AllocationSite site =
                new AllocationListener.AllocationSite(instr, "I[]", true, 5);

            assertEquals("I[]", site.getTypeName());
            assertTrue(site.isArray());
            assertSame(instr, site.getInstruction());
        }
    }

    // ========== Integration Tests ==========

    @Nested
    class IntegrationTests {

        @Test
        void simulateWithAllListeners() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction(null));

            InterProceduralEngine engine = new InterProceduralEngine(context);
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

    // ========== Helper Methods ==========

    private IRMethod liftToIR(MethodEntry method) throws IOException {
        return TestUtils.liftMethod(method);
    }
}
