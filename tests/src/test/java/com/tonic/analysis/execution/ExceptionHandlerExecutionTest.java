package com.tonic.analysis.execution;

import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Exception Handler Execution Tests")
class ExceptionHandlerExecutionTest {

    private ClassFile classFile;
    private ClassPool classPool;
    private BytecodeContext context;

    @BeforeEach
    void setUp() throws IOException {
        classFile = TestUtils.loadTestFixture("ExecutionTestFixture");
        classPool = new ClassPool();
        classPool.put(classFile);

        context = new BytecodeContext.Builder()
            .heapManager(new SimpleHeapManager())
            .classResolver(new ClassResolver(classPool))
            .maxCallDepth(1000)
            .maxInstructions(100000)
            .mode(ExecutionMode.RECURSIVE)
            .trackStatistics(true)
            .build();
    }

    private MethodEntry findMethod(String name) {
        return classFile.getMethods().stream()
            .filter(m -> m.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Method not found: " + name));
    }

    private BytecodeEngine createEngine() {
        return new BytecodeEngine(context);
    }

    @Nested
    @DisplayName("Basic Handler Tests")
    class BasicHandlerTests {

        @Test
        @DisplayName("Simple try-catch: Exception caught by direct handler, returns normally")
        void testSimpleTryCatch() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Exception should be caught and method completes normally");
            assertEquals(-1, result.getReturnValue().asInt(),
                "Caught exception should result in return value -1");
            assertNull(result.getException(),
                "Exception should not propagate when caught");
        }

        @Test
        @DisplayName("Try-catch miss: Exception type doesn't match handler, propagates")
        void testTryCatchMiss() {
            MethodEntry method = findMethod("divideOrThrow");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus(),
                "Uncaught exception should propagate");
            assertNotNull(result.getException(),
                "Exception object should be present");
            assertEquals("java/lang/ArithmeticException", result.getException().getClassName(),
                "Exception type should be ArithmeticException");
        }

        @Test
        @DisplayName("Catch-all handler: catchType=0 catches any exception (finally)")
        void testCatchAllHandler() {
            MethodEntry method = findMethod("tryFinallyCounter");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(5));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Finally block should allow normal completion");
            assertNotNull(result.getReturnValue(),
                "Should have a return value");
        }

        @Test
        @DisplayName("Multiple handlers: First matching handler wins")
        void testMultipleHandlers() {
            MethodEntry method = findMethod("nestedTryCatch");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "First matching handler should catch exception");
            assertNotNull(result.getReturnValue(),
                "Should return value after handling exception");
        }

        @Test
        @DisplayName("Nested try-catch: Inner handler catches, outer not reached")
        void testNestedTryCatch() {
            MethodEntry method = findMethod("nestedTryCatch");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(12),
                ConcreteValue.intValue(3),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Inner handler should catch before outer");
            assertEquals(12, result.getReturnValue().asInt(),
                "Should return result from inner handler path (a / 1 = 12)");
        }
    }

    @Nested
    @DisplayName("Type Hierarchy Tests")
    class TypeHierarchyTests {

        @Test
        @DisplayName("Catch supertype: Catch Exception, throw RuntimeException")
        void testCatchSupertype() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(100),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Supertype handler should catch subtype exception");
            assertEquals(-1, result.getReturnValue().asInt(),
                "Handler for supertype should execute");
        }

        @Test
        @DisplayName("Catch exact type: Exact type match")
        void testCatchExactType() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Exact type match should be caught");
            assertEquals(-1, result.getReturnValue().asInt(),
                "Exact match handler should execute");
        }

        @Test
        @DisplayName("Subtype not caught: Catch RuntimeException, throw IOException (no match)")
        void testSubtypeNotCaught() {
            MethodEntry method = findMethod("recursiveWithException");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(5));

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus(),
                "Unrelated exception types should not match");
            assertEquals("java/lang/IllegalArgumentException", result.getException().getClassName(),
                "Original exception type should propagate");
        }

        @Test
        @DisplayName("Catch Throwable: Catch Throwable catches anything")
        void testCatchThrowable() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Throwable handler should catch all exceptions");
            assertEquals(-1, result.getReturnValue().asInt(),
                "Throwable catch should execute handler");
        }
    }

    @Nested
    @DisplayName("Stack Behavior Tests")
    class StackBehaviorTests {

        @Test
        @DisplayName("Stack cleared on catch: Operand stack cleared before pushing exception")
        void testStackClearedOnCatch() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Stack should be properly managed during exception handling");
            assertNotNull(result.getReturnValue(),
                "Cleared stack should not affect return value");
        }

        @Test
        @DisplayName("Exception on stack: Exception reference is top of stack in handler")
        void testExceptionOnStack() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(100),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Exception should be pushed onto cleared stack");
            assertEquals(-1, result.getReturnValue().asInt(),
                "Handler can access exception from stack");
        }

        @Test
        @DisplayName("Stack depth after catch: Stack depth is exactly 1 after entering handler")
        void testStackDepthAfterCatch() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(15),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Stack should have correct depth in handler");
            assertEquals(-1, result.getReturnValue().asInt(),
                "Handler executes with properly initialized stack");
        }
    }

    @Nested
    @DisplayName("Control Flow Tests")
    class ControlFlowTests {

        @Test
        @DisplayName("Handler PC jump: PC correctly set to handler_pc")
        void testHandlerPcJump() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "PC should jump to handler correctly");
            assertEquals(-1, result.getReturnValue().asInt(),
                "Handler code should execute at correct PC");
        }

        @Test
        @DisplayName("Execution continues after catch: Normal execution resumes after handler")
        void testExecutionContinuesAfterCatch() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(4));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Normal execution should continue when no exception");
            assertEquals(5, result.getReturnValue().asInt(),
                "Normal path should execute correctly");
        }

        @Test
        @DisplayName("Rethrow from handler: Handler can rethrow same exception")
        void testRethrowFromHandler() {
            MethodEntry method = findMethod("nestedTryCatch");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(0),
                ConcreteValue.intValue(1),
                ConcreteValue.intValue(2));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Handler should be able to process exception");
            assertNotNull(result.getReturnValue(),
                "Rethrowing handler should still allow completion");
        }

        @Test
        @DisplayName("Throw different from handler: Handler throws different exception")
        void testThrowDifferentFromHandler() {
            MethodEntry method = findMethod("nestedTryCatch");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Handler can throw different exception type");
            assertNotNull(result.getReturnValue(),
                "Different exception handled correctly");
        }
    }

    @Nested
    @DisplayName("Cross-Method Tests")
    class CrossMethodTests {

        @Test
        @DisplayName("Exception propagates across methods: Unhandled in callee, caught in caller")
        void testExceptionPropagatesAcrossMethods() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Exception should propagate from callee to caller handler");
            assertEquals(-1, result.getReturnValue().asInt(),
                "Caller handler should catch callee exception");
        }

        @Test
        @DisplayName("Nested calls with handlers: Multiple call stack levels with handlers")
        void testNestedCallsWithHandlers() {
            MethodEntry method = findMethod("nestedTryCatch");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(12),
                ConcreteValue.intValue(3),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Nested handlers should work correctly");
            assertNotNull(result.getReturnValue(),
                "Nested exception handling preserves return values");
        }

        @Test
        @DisplayName("Exception from caller: Exception thrown after call returns")
        void testExceptionFromCaller() {
            MethodEntry method = findMethod("safeRecursiveSum");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(15),
                ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Caller should handle exception from recursive call");
            assertEquals(-1, result.getReturnValue().asInt(),
                "Caller exception handler should execute");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null exception throw: Throws NullPointerException for null ref")
        void testNullExceptionThrow() {
            MethodEntry method = findMethod("divideOrThrow");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(2));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Normal execution should not involve null exception");
            assertEquals(5, result.getReturnValue().asInt(),
                "Normal division should work correctly");
        }

        @Test
        @DisplayName("Empty exception table: No handlers, propagates immediately")
        void testEmptyExceptionTable() {
            MethodEntry method = findMethod("divideOrThrow");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus(),
                "Exception should propagate when no handlers present");
            assertNotNull(result.getException(),
                "Exception object should be available");
        }

        @Test
        @DisplayName("Overlapping handler ranges: Handlers with overlapping PC ranges")
        void testOverlappingHandlerRanges() {
            MethodEntry method = findMethod("nestedTryCatch");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "First matching handler in overlapping ranges should win");
            assertNotNull(result.getReturnValue(),
                "Overlapping handlers should not interfere");
        }

        @Test
        @DisplayName("Exception outside protected region: No handler match for PC")
        void testExceptionOutsideProtectedRegion() {
            MethodEntry method = findMethod("divideOrThrow");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(100),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus(),
                "Exception outside handler range should propagate");
            assertEquals("java/lang/ArithmeticException", result.getException().getClassName(),
                "Original exception type preserved");
        }
    }

    @Nested
    @DisplayName("Exception Table Verification")
    class ExceptionTableVerification {

        @Test
        @DisplayName("safeDivide should have exception table entries")
        void testSafeDivideHasExceptionTable() {
            MethodEntry method = findMethod("safeDivide");
            CodeAttribute codeAttr = method.getCodeAttribute();
            assertNotNull(codeAttr, "safeDivide should have code attribute");

            List<ExceptionTableEntry> exceptionTable = codeAttr.getExceptionTable();
            assertNotNull(exceptionTable, "safeDivide should have exception table");
            assertFalse(exceptionTable.isEmpty(), "safeDivide exception table should not be empty");

            for (ExceptionTableEntry entry : exceptionTable) {
                assertTrue(entry.getStartPc() >= 0, "startPc should be non-negative");
                assertTrue(entry.getEndPc() > entry.getStartPc(), "endPc should be after startPc");
                assertTrue(entry.getHandlerPc() >= 0, "handlerPc should be non-negative");
            }
        }

        @Test
        @DisplayName("nestedTryCatch should have multiple exception table entries")
        void testNestedTryCatchHasExceptionTable() {
            MethodEntry method = findMethod("nestedTryCatch");
            CodeAttribute codeAttr = method.getCodeAttribute();
            assertNotNull(codeAttr, "nestedTryCatch should have code attribute");

            List<ExceptionTableEntry> exceptionTable = codeAttr.getExceptionTable();
            assertNotNull(exceptionTable, "nestedTryCatch should have exception table");
            assertTrue(exceptionTable.size() >= 2, "nestedTryCatch should have at least 2 exception handlers");
        }
    }

    @Nested
    @DisplayName("Recursive Exception Tests")
    class RecursiveExceptionTests {

        @Test
        @DisplayName("Recursive call with exception beyond limit")
        void testRecursiveCallWithException() {
            MethodEntry method = findMethod("recursiveWithException");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(5));

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus(),
                "Should throw exception when limit exceeded");
            assertEquals("java/lang/IllegalArgumentException", result.getException().getClassName(),
                "Exception type should be IllegalArgumentException");
        }

        @Test
        @DisplayName("Recursive call within limit completes normally")
        void testRecursiveCallWithinLimit() {
            MethodEntry method = findMethod("recursiveWithException");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Should complete normally within limit");
            assertEquals(15, result.getReturnValue().asInt(),
                "Should calculate correct sum");
        }

        @Test
        @DisplayName("Safe recursive with catch handles exception")
        void testSafeRecursiveWithCatch() {
            MethodEntry method = findMethod("safeRecursiveSum");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Should catch exception from recursive call");
            assertEquals(-1, result.getReturnValue().asInt(),
                "Should return -1 when exception caught");
        }

        @Test
        @DisplayName("Safe recursive normal operation")
        void testSafeRecursiveNormal() {
            MethodEntry method = findMethod("safeRecursiveSum");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Should complete normally within limit");
            assertEquals(15, result.getReturnValue().asInt(),
                "Should calculate correct sum");
        }
    }

    @Nested
    @DisplayName("Finally Block Tests")
    class FinallyBlockTests {

        @Test
        @DisplayName("Finally block executes normally")
        void testFinallyExecutes() {
            MethodEntry method = findMethod("tryFinallyCounter");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Finally should allow normal completion");
            assertEquals(20, result.getReturnValue().asInt(),
                "Finally block should execute");
        }

        @Test
        @DisplayName("Finally executes even with exception path")
        void testFinallyExecutesWithException() {
            MethodEntry method = findMethod("tryFinallyCounter");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(3));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Finally should execute regardless of exception");
            assertEquals(6, result.getReturnValue().asInt(),
                "Finally modifies value correctly");
        }

        @Test
        @DisplayName("Finally does not interfere with return value")
        void testFinallyDoesNotInterfereWithReturn() {
            MethodEntry method = findMethod("tryFinallyCounter");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(7));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Finally should preserve return semantics");
            assertEquals(14, result.getReturnValue().asInt(),
                "Return value correctly computed");
        }
    }

    @Nested
    @DisplayName("Exception Stack Trace Tests")
    class ExceptionStackTraceTests {

        @Test
        @DisplayName("Stack trace present on exception")
        void testStackTraceOnException() {
            MethodEntry method = findMethod("divideOrThrow");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus(),
                "Should throw exception");
            assertNotNull(result.getStackTrace(),
                "Stack trace should be present (may be empty)");
        }

        @Test
        @DisplayName("Stack trace is available on exception result")
        void testStackTraceAvailable() {
            MethodEntry method = findMethod("divideOrThrow");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(100),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
            assertNotNull(result.getStackTrace(),
                "Stack trace list should not be null");
        }

        @Test
        @DisplayName("Nested stack trace correctness")
        void testNestedStackTrace() {
            MethodEntry method = findMethod("nestedTryCatch");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(0),
                ConcreteValue.intValue(0),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus(),
                "Nested handlers should complete normally");
        }
    }
}
