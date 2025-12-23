package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.dispatch.InvokeDynamicInfo;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.state.ConcreteValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class LambdaProxyFactoryTest {

    private LambdaProxyFactory factory;
    private HeapManager heapManager;

    @BeforeEach
    void setUp() {
        heapManager = new SimpleHeapManager();
        factory = new LambdaProxyFactory(heapManager);
    }

    @Nested
    class IsLambdaFactoryTests {
        @Test
        void shouldIdentifyMetafactory() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "metafactory", "()Ljava/util/function/Function;", 1);
            assertTrue(factory.isLambdaFactory(info));
        }

        @Test
        void shouldIdentifyAltMetafactory() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "altMetafactory", "()Ljava/util/function/Consumer;", 1);
            assertTrue(factory.isLambdaFactory(info));
        }

        @Test
        void shouldNotIdentifyMakeConcatWithConstants() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants", "(I)Ljava/lang/String;", 1);
            assertFalse(factory.isLambdaFactory(info));
        }

        @Test
        void shouldHandleNull() {
            assertFalse(factory.isLambdaFactory(null));
        }
    }

    @Nested
    class CreateProxyTests {
        @Test
        void shouldCreateProxyWithNoCapturedArgs() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "metafactory", "()Ljava/util/function/Runnable;", 1);
            ConcreteValue[] capturedArgs = {};

            ObjectInstance proxy = factory.createProxy(info, capturedArgs);

            assertNotNull(proxy);
            assertTrue(proxy.getClassName().startsWith("$Lambda$"));
        }

        @Test
        void shouldCreateProxyWithIntCapturedArg() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "metafactory", "(I)Ljava/util/function/Supplier;", 1);
            ConcreteValue[] capturedArgs = { ConcreteValue.intValue(42) };

            ObjectInstance proxy = factory.createProxy(info, capturedArgs);

            assertNotNull(proxy);
            Object field = proxy.getField(proxy.getClassName(), "capture$0", "I");
            assertEquals(42, field);
        }

        @Test
        void shouldCreateProxyWithLongCapturedArg() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "metafactory", "(J)Ljava/util/function/Supplier;", 1);
            ConcreteValue[] capturedArgs = { ConcreteValue.longValue(123456789L) };

            ObjectInstance proxy = factory.createProxy(info, capturedArgs);

            assertNotNull(proxy);
            Object field = proxy.getField(proxy.getClassName(), "capture$0", "J");
            assertEquals(123456789L, field);
        }

        @Test
        void shouldCreateProxyWithMultipleCapturedArgs() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "metafactory", "(IJ)Ljava/util/function/BiFunction;", 1);
            ConcreteValue[] capturedArgs = {
                ConcreteValue.intValue(10),
                ConcreteValue.longValue(20L)
            };

            ObjectInstance proxy = factory.createProxy(info, capturedArgs);

            assertNotNull(proxy);
            Object field0 = proxy.getField(proxy.getClassName(), "capture$0", "I");
            Object field1 = proxy.getField(proxy.getClassName(), "capture$1", "J");
            assertEquals(10, field0);
            assertEquals(20L, field1);
        }

        @Test
        void shouldCreateUniqueProxyClassNames() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "metafactory", "()Ljava/util/function/Runnable;", 1);
            ConcreteValue[] capturedArgs = {};

            ObjectInstance proxy1 = factory.createProxy(info, capturedArgs);
            ObjectInstance proxy2 = factory.createProxy(info, capturedArgs);

            assertNotEquals(proxy1.getClassName(), proxy2.getClassName());
        }
    }

    @Nested
    class GetCapturedArgCountTests {
        @Test
        void shouldReturnZeroForNoCapturedArgs() {
            assertEquals(0, factory.getCapturedArgCount("()Ljava/util/function/Runnable;"));
        }

        @Test
        void shouldCountIntCapturedArg() {
            assertEquals(1, factory.getCapturedArgCount("(I)Ljava/util/function/Supplier;"));
        }

        @Test
        void shouldCountLongCapturedArg() {
            assertEquals(1, factory.getCapturedArgCount("(J)Ljava/util/function/Supplier;"));
        }

        @Test
        void shouldCountMultiplePrimitiveCapturedArgs() {
            assertEquals(4, factory.getCapturedArgCount("(IJFD)Ljava/util/function/Supplier;"));
        }

        @Test
        void shouldCountObjectCapturedArg() {
            assertEquals(1, factory.getCapturedArgCount("(Ljava/lang/String;)Ljava/util/function/Function;"));
        }

        @Test
        void shouldCountArrayCapturedArg() {
            assertEquals(1, factory.getCapturedArgCount("([I)Ljava/util/function/Function;"));
        }

        @Test
        void shouldCountMixedCapturedArgs() {
            assertEquals(3, factory.getCapturedArgCount("(ILjava/lang/Object;[D)Ljava/util/function/Function;"));
        }

        @Test
        void shouldHandleNullDescriptor() {
            assertEquals(0, factory.getCapturedArgCount(null));
        }

        @Test
        void shouldHandleMalformedDescriptor() {
            assertEquals(0, factory.getCapturedArgCount("invalid"));
        }

        @Test
        void shouldHandleEmptyParens() {
            assertEquals(0, factory.getCapturedArgCount("()V"));
        }
    }

    @Nested
    class ExtractInterfaceTypeTests {
        @Test
        void shouldExtractRunnableInterface() {
            String result = factory.extractInterfaceType("()Ljava/util/function/Runnable;");
            assertEquals("java/util/function/Runnable", result);
        }

        @Test
        void shouldExtractFunctionInterface() {
            String result = factory.extractInterfaceType("(Ljava/lang/Object;)Ljava/util/function/Function;");
            assertEquals("java/util/function/Function", result);
        }

        @Test
        void shouldExtractConsumerInterface() {
            String result = factory.extractInterfaceType("(I)Ljava/util/function/Consumer;");
            assertEquals("java/util/function/Consumer", result);
        }

        @Test
        void shouldHandlePrimitiveReturn() {
            String result = factory.extractInterfaceType("()I");
            assertEquals("I", result);
        }

        @Test
        void shouldHandleNullDescriptor() {
            String result = factory.extractInterfaceType(null);
            assertEquals("java/lang/Object", result);
        }

        @Test
        void shouldHandleMalformedDescriptor() {
            String result = factory.extractInterfaceType("invalid");
            assertEquals("java/lang/Object", result);
        }
    }

    @Nested
    class GetTargetMethodNameTests {
        @Test
        void shouldReturnMethodName() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply", "()Ljava/util/function/Function;", 1);
            assertEquals("apply", factory.getTargetMethodName(info));
        }

        @Test
        void shouldReturnRunMethodName() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "run", "()Ljava/util/function/Runnable;", 1);
            assertEquals("run", factory.getTargetMethodName(info));
        }
    }

    @Nested
    class CapturedValueTypesTests {
        @Test
        void shouldCaptureFloatValue() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "metafactory", "(F)Ljava/util/function/Supplier;", 1);
            ConcreteValue[] capturedArgs = { ConcreteValue.floatValue(3.14f) };

            ObjectInstance proxy = factory.createProxy(info, capturedArgs);

            Object field = proxy.getField(proxy.getClassName(), "capture$0", "F");
            assertEquals(3.14f, (float) field, 0.001f);
        }

        @Test
        void shouldCaptureDoubleValue() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "metafactory", "(D)Ljava/util/function/Supplier;", 1);
            ConcreteValue[] capturedArgs = { ConcreteValue.doubleValue(2.718) };

            ObjectInstance proxy = factory.createProxy(info, capturedArgs);

            Object field = proxy.getField(proxy.getClassName(), "capture$0", "D");
            assertEquals(2.718, (double) field, 0.001);
        }

        @Test
        void shouldCaptureReferenceValue() {
            ObjectInstance captured = heapManager.newObject("java/lang/String");
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "metafactory", "(Ljava/lang/String;)Ljava/util/function/Function;", 1);
            ConcreteValue[] capturedArgs = { ConcreteValue.reference(captured) };

            ObjectInstance proxy = factory.createProxy(info, capturedArgs);

            Object field = proxy.getField(proxy.getClassName(), "capture$0", "Ljava/lang/Object;");
            assertSame(captured, field);
        }
    }
}
