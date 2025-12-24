package com.tonic.analysis.execution.dispatch;

import com.tonic.testutil.BytecodeBuilder;
import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class OpcodeDispatcherNewArithmeticTest {
    private BytecodeContext context;
    private SimpleHeapManager heapManager;

    @BeforeEach
    void setUp() {
        heapManager = new SimpleHeapManager();
        context = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(new ClassResolver(new ClassPool(true)))
            .maxInstructions(10000)
            .build();
    }

    private BytecodeResult execute(MethodEntry method, ConcreteValue... args) {
        return new BytecodeEngine(context).execute(method, args);
    }

    private MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError("Method not found: " + name);
    }

    @Nested
    class NewObjectTests {
        @Test
        void testNewBasicObject() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestNew")
                .publicStaticMethod("test", "()Ljava/lang/Object;")
                    .new_("java/lang/Object")
                    .areturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertFalse(result.getReturnValue().isNull());
        }

        @Test
        void testNewCustomClass() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestNew2")
                .publicStaticMethod("test", "()Ljava/lang/String;")
                    .new_("java/lang/String")
                    .areturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertFalse(result.getReturnValue().isNull());
        }

        @Test
        void testNewThenDup() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestNew3")
                .publicStaticMethod("test", "()Ljava/lang/Object;")
                    .new_("java/lang/Object")
                    .dup()
                    .pop()
                    .areturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertFalse(result.getReturnValue().isNull());
        }
    }

    @Nested
    class MultiANewArrayTests {
        @Test
        void testMultiANewArray2D() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestMulti2D")
                .publicStaticMethod("test", "(II)[[I")
                    .iload(0)
                    .iload(1)
                    .multianewarray("[[I", 2)
                    .areturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(3),
                ConcreteValue.intValue(4));
            assertFalse(result.getReturnValue().isNull());
        }

        @Test
        void testMultiANewArray3D() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestMulti3D")
                .publicStaticMethod("test", "(III)[[[Ljava/lang/String;")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .multianewarray("[[[Ljava/lang/String;", 3)
                    .areturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(2),
                ConcreteValue.intValue(3),
                ConcreteValue.intValue(4));
            assertFalse(result.getReturnValue().isNull());
        }

        @Test
        void testMultiANewArrayObject2D() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestMultiObj2D")
                .publicStaticMethod("test", "(II)[[Ljava/lang/Object;")
                    .iload(0)
                    .iload(1)
                    .multianewarray("[[Ljava/lang/Object;", 2)
                    .areturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(6));
            assertFalse(result.getReturnValue().isNull());
        }

        @Test
        void testMultiANewArrayInt2D() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestMultiInt2D")
                .publicStaticMethod("test", "(II)[[I")
                    .iload(0)
                    .iload(1)
                    .multianewarray("[[I", 2)
                    .areturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20));
            assertFalse(result.getReturnValue().isNull());
        }
    }

    @Nested
    class MonitorTests {
        @Test
        void testMonitorEnterExit() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestMonitor")
                .publicStaticMethod("test", "(Ljava/lang/Object;)V")
                    .aload(0)
                    .monitorenter()
                    .aload(0)
                    .monitorexit()
                    .vreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = heapManager.newObject("java/lang/Object");
            BytecodeResult result = execute(method, ConcreteValue.reference(obj));
            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
        }

        @Test
        void testNestedMonitors() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestNestedMonitor")
                .publicStaticMethod("test", "(Ljava/lang/Object;Ljava/lang/Object;)V")
                    .aload(0)
                    .monitorenter()
                    .aload(1)
                    .monitorenter()
                    .aload(1)
                    .monitorexit()
                    .aload(0)
                    .monitorexit()
                    .vreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj1 = heapManager.newObject("java/lang/Object");
            ObjectInstance obj2 = heapManager.newObject("java/lang/Object");
            BytecodeResult result = execute(method,
                ConcreteValue.reference(obj1),
                ConcreteValue.reference(obj2));
            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
        }

        @Test
        void testMonitorSameObjectTwice() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestMonitorSame")
                .publicStaticMethod("test", "(Ljava/lang/Object;)V")
                    .aload(0)
                    .monitorenter()
                    .aload(0)
                    .monitorenter()
                    .aload(0)
                    .monitorexit()
                    .aload(0)
                    .monitorexit()
                    .vreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = heapManager.newObject("java/lang/Object");
            BytecodeResult result = execute(method, ConcreteValue.reference(obj));
            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
        }
    }

    @Nested
    class AThrowTests {
        @Test
        void testAThrowWithException() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestThrow")
                .publicStaticMethod("test", "(Ljava/lang/Throwable;)V")
                    .aload(0)
                    .athrow()
                .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance exception = heapManager.newObject("java/lang/RuntimeException");
            BytecodeResult result = execute(method, ConcreteValue.reference(exception));
            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }

        @Test
        void testAThrowAfterNew() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestThrowNew")
                .publicStaticMethod("test", "()V")
                    .new_("java/lang/RuntimeException")
                    .athrow()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }
    }

    @Nested
    class LongRemainderTests {
        @Test
        void testLRemPositive() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLRem1")
                .publicStaticMethod("test", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .lrem()
                    .lreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.longValue(17L),
                ConcreteValue.longValue(5L));
            assertEquals(2L, result.getReturnValue().asLong());
        }

        @Test
        void testLRemNegativeDividend() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLRem2")
                .publicStaticMethod("test", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .lrem()
                    .lreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.longValue(-17L),
                ConcreteValue.longValue(5L));
            assertEquals(-2L, result.getReturnValue().asLong());
        }

        @Test
        void testLRemNegativeDivisor() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLRem3")
                .publicStaticMethod("test", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .lrem()
                    .lreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.longValue(17L),
                ConcreteValue.longValue(-5L));
            assertEquals(2L, result.getReturnValue().asLong());
        }
    }

    @Nested
    class FloatRemainderTests {
        @Test
        void testFRemBasic() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestFRem1")
                .publicStaticMethod("test", "(FF)F")
                    .fload(0)
                    .fload(1)
                    .frem()
                    .freturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.floatValue(10.5f),
                ConcreteValue.floatValue(3.0f));
            assertEquals(1.5f, result.getReturnValue().asFloat(), 0.0001f);
        }

        @Test
        void testFRemNegative() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestFRem2")
                .publicStaticMethod("test", "(FF)F")
                    .fload(0)
                    .fload(1)
                    .frem()
                    .freturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.floatValue(-10.5f),
                ConcreteValue.floatValue(3.0f));
            assertEquals(-1.5f, result.getReturnValue().asFloat(), 0.0001f);
        }

        @Test
        void testFRemDecimal() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestFRem3")
                .publicStaticMethod("test", "(FF)F")
                    .fload(0)
                    .fload(1)
                    .frem()
                    .freturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.floatValue(10.0f),
                ConcreteValue.floatValue(3.0f));
            assertEquals(1.0f, result.getReturnValue().asFloat(), 0.0001f);
        }
    }

    @Nested
    class DoubleRemainderTests {
        @Test
        void testDRemPositive() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestDRem1")
                .publicStaticMethod("test", "(DD)D")
                    .dload(0)
                    .dload(2)
                    .drem()
                    .dreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.doubleValue(10.0),
                ConcreteValue.doubleValue(3.0));
            assertEquals(1.0, result.getReturnValue().asDouble(), 0.0001);
        }

        @Test
        void testDRemNegative() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestDRem2")
                .publicStaticMethod("test", "(DD)D")
                    .dload(0)
                    .dload(2)
                    .drem()
                    .dreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.doubleValue(-10.0),
                ConcreteValue.doubleValue(3.0));
            assertEquals(-1.0, result.getReturnValue().asDouble(), 0.0001);
        }

        @Test
        void testDRemLargeDividend() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestDRem3")
                .publicStaticMethod("test", "(DD)D")
                    .dload(0)
                    .dload(2)
                    .drem()
                    .dreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.doubleValue(100.0),
                ConcreteValue.doubleValue(7.0));
            assertEquals(2.0, result.getReturnValue().asDouble(), 0.0001);
        }
    }

    @Nested
    class AdditionalConversionTests {
        @Test
        void testI2F() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestI2F")
                .publicStaticMethod("test", "(I)F")
                    .iload(0)
                    .i2f()
                    .freturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(42));
            assertEquals(42.0f, result.getReturnValue().asFloat(), 0.0001f);
        }

        @Test
        void testI2D() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestI2D")
                .publicStaticMethod("test", "(I)D")
                    .iload(0)
                    .i2d()
                    .dreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(123));
            assertEquals(123.0, result.getReturnValue().asDouble(), 0.0001);
        }

        @Test
        void testL2I() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestL2I")
                .publicStaticMethod("test", "(J)I")
                    .lload(0)
                    .l2i()
                    .ireturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(99999L));
            assertEquals(99999, result.getReturnValue().asInt());
        }

        @Test
        void testF2I() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestF2I")
                .publicStaticMethod("test", "(F)I")
                    .fload(0)
                    .f2i()
                    .ireturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.floatValue(42.9f));
            assertEquals(42, result.getReturnValue().asInt());
        }

        @Test
        void testD2I() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestD2I")
                .publicStaticMethod("test", "(D)I")
                    .dload(0)
                    .d2i()
                    .ireturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.doubleValue(789.5));
            assertEquals(789, result.getReturnValue().asInt());
        }
    }

    @Nested
    class CombinedOperationsTests {
        @Test
        void testNewThenMonitor() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestNewMonitor")
                .publicStaticMethod("test", "()Ljava/lang/Object;")
                    .new_("java/lang/Object")
                    .dup()
                    .monitorenter()
                    .dup()
                    .monitorexit()
                    .areturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertFalse(result.getReturnValue().isNull());
        }

        @Test
        void testMultiArrayWithLength() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestMultiArrayLen")
                .publicStaticMethod("test", "()I")
                    .iconst(5)
                    .iconst(3)
                    .multianewarray("[[I", 2)
                    .arraylength()
                    .ireturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(5, result.getReturnValue().asInt());
        }

        @Test
        void testArithmeticChainWithRemainder() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestRemChain")
                .publicStaticMethod("test", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .lrem()
                    .lconst(1L)
                    .ladd()
                    .lreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.longValue(23L),
                ConcreteValue.longValue(7L));
            assertEquals(3L, result.getReturnValue().asLong());
        }

        @Test
        void testConversionChain() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestConvChain")
                .publicStaticMethod("test", "(I)D")
                    .iload(0)
                    .i2f()
                    .f2d()
                    .dreturn()
                .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(100));
            assertEquals(100.0, result.getReturnValue().asDouble(), 0.0001);
        }
    }
}
