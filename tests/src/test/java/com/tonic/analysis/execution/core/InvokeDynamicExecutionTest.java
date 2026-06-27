package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.dispatch.InvokeDynamicInfo;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class InvokeDynamicExecutionTest {

    private SimpleHeapManager heap;

    @BeforeEach
    void setUp() {
        heap = new SimpleHeapManager();
    }

    @Nested
    class InvokeDynamicInfoParsingTests {

        @Test
        void testParseLambdaSupplier() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "get", "()Ljava/util/function/Supplier;", 10);
            assertEquals("get", info.getMethodName());
            assertEquals("Ljava/util/function/Supplier;", info.getReturnType());
            assertEquals(0, info.getParameterSlots());
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testParseLambdaFunction() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply", "(Ljava/lang/Object;)Ljava/util/function/Function;", 10);
            assertEquals("apply", info.getMethodName());
            assertEquals("Ljava/util/function/Function;", info.getReturnType());
            assertEquals(1, info.getParameterSlots());
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testParseLambdaConsumer() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "accept", "(Ljava/lang/Object;)Ljava/util/function/Consumer;", 10);
            assertEquals("accept", info.getMethodName());
            assertEquals("Ljava/util/function/Consumer;", info.getReturnType());
            assertEquals(1, info.getParameterSlots());
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testParseLambdaPredicate() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "(Ljava/lang/Object;)Ljava/util/function/Predicate;", 10);
            assertEquals("test", info.getMethodName());
            assertEquals("Ljava/util/function/Predicate;", info.getReturnType());
            assertEquals(1, info.getParameterSlots());
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testParseStringConcatTwoStrings() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", 10);
            assertEquals("makeConcatWithConstants", info.getMethodName());
            assertEquals("Ljava/lang/String;", info.getReturnType());
            assertEquals(2, info.getParameterSlots());
            assertTrue(info.isStringConcat());
        }

        @Test
        void testParseStringConcatMixed() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants",
                "(Ljava/lang/String;IZ)Ljava/lang/String;", 10);
            assertEquals(3, info.getParameterSlots());
            assertTrue(info.isStringConcat());
        }

        @Test
        void testParseStringConcatWithLongDouble() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants",
                "(JD)Ljava/lang/String;", 10);
            assertEquals(4, info.getParameterSlots());
            assertTrue(info.isStringConcat());
        }
    }

    @Nested
    class DescriptorParsingEdgeCases {

        @Test
        void testEmptyParams() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()I", 0);
            assertEquals(0, info.getParameterSlots());
        }

        @Test
        void testAllPrimitives() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "(ZBCSIJFD)V", 0);
            assertEquals(10, info.getParameterSlots());
        }

        @Test
        void testNestedObjectArrays() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "([[[Ljava/lang/Object;)V", 0);
            assertEquals(1, info.getParameterSlots());
        }

        @Test
        void testMultipleObjectParams() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test",
                "(Ljava/lang/String;Ljava/lang/Integer;Ljava/util/List;)V", 0);
            assertEquals(3, info.getParameterSlots());
        }

        @Test
        void testPrimitiveArrays() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "([I[J[D)V", 0);
            assertEquals(3, info.getParameterSlots());
        }
    }

    @Nested
    class ReturnTypeParsingTests {

        @Test
        void testReturnVoid() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "(I)V", 0);
            assertEquals("V", info.getReturnType());
            assertTrue(info.isVoidReturn());
        }

        @Test
        void testReturnBoolean() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()Z", 0);
            assertEquals("Z", info.getReturnType());
            assertFalse(info.isVoidReturn());
        }

        @Test
        void testReturnByte() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()B", 0);
            assertEquals("B", info.getReturnType());
        }

        @Test
        void testReturnChar() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()C", 0);
            assertEquals("C", info.getReturnType());
        }

        @Test
        void testReturnShort() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()S", 0);
            assertEquals("S", info.getReturnType());
        }

        @Test
        void testReturnInt() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()I", 0);
            assertEquals("I", info.getReturnType());
        }

        @Test
        void testReturnLong() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()J", 0);
            assertEquals("J", info.getReturnType());
        }

        @Test
        void testReturnFloat() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()F", 0);
            assertEquals("F", info.getReturnType());
        }

        @Test
        void testReturnDouble() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()D", 0);
            assertEquals("D", info.getReturnType());
        }

        @Test
        void testReturnObject() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()Ljava/lang/String;", 0);
            assertEquals("Ljava/lang/String;", info.getReturnType());
        }

        @Test
        void testReturnPrimitiveArray() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()[I", 0);
            assertEquals("[I", info.getReturnType());
        }

        @Test
        void testReturnObjectArray() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()[Ljava/lang/Object;", 0);
            assertEquals("[Ljava/lang/Object;", info.getReturnType());
        }

        @Test
        void testReturnMultiDimArray() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "()[[I", 0);
            assertEquals("[[I", info.getReturnType());
        }
    }

    @Nested
    class LambdaMetafactoryPatternTests {

        @Test
        void testRunnableRun() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "run", "()Ljava/lang/Runnable;", 0);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testCallableCall() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "call", "()Ljava/util/concurrent/Callable;", 0);
            assertFalse(info.isLambdaMetafactory());
        }

        @Test
        void testBiFunctionApply() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/function/BiFunction;", 0);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testIntSupplier() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "getAsInt", "()Ljava/util/function/IntSupplier;", 0);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testLongSupplier() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "getAsLong", "()Ljava/util/function/LongSupplier;", 0);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testDoubleSupplier() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "getAsDouble", "()Ljava/util/function/DoubleSupplier;", 0);
            assertTrue(info.isLambdaMetafactory());
        }
    }

    @Nested
    class StringConcatPatternTests {

        @Test
        void testMakeConcatWithConstants() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants",
                "(Ljava/lang/String;)Ljava/lang/String;", 0);
            assertTrue(info.isStringConcat());
            assertFalse(info.isLambdaMetafactory());
        }

        @Test
        void testMakeConcat() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcat",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", 0);
            assertTrue(info.isStringConcat());
        }

        @Test
        void testNotStringConcat() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "concat",
                "(Ljava/lang/String;)Ljava/lang/String;", 0);
            assertFalse(info.isStringConcat());
        }
    }

    @Nested
    class BytecodeEngineConfigTests {

        @Test
        void testEngineCreation() {
            ClassResolver resolver = new ClassResolver(mock(ClassPool.class));
            BytecodeContext ctx = new BytecodeContext.Builder()
                .heapManager(heap)
                .classResolver(resolver)
                .maxInstructions(1000)
                .build();
            BytecodeEngine engine = new BytecodeEngine(ctx);
            assertNotNull(engine);
        }

        @Test
        void testEngineWithDelegatedMode() {
            ClassResolver resolver = new ClassResolver(mock(ClassPool.class));
            BytecodeContext ctx = new BytecodeContext.Builder()
                .heapManager(heap)
                .classResolver(resolver)
                .mode(ExecutionMode.DELEGATED)
                .maxInstructions(1000)
                .build();
            BytecodeEngine engine = new BytecodeEngine(ctx);
            assertNotNull(engine);
        }

        @Test
        void testEngineWithRecursiveMode() {
            ClassResolver resolver = new ClassResolver(mock(ClassPool.class));
            BytecodeContext ctx = new BytecodeContext.Builder()
                .heapManager(heap)
                .classResolver(resolver)
                .mode(ExecutionMode.RECURSIVE)
                .maxInstructions(1000)
                .build();
            BytecodeEngine engine = new BytecodeEngine(ctx);
            assertNotNull(engine);
        }
    }
}
