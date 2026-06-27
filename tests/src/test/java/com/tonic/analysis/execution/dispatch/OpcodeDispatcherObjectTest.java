package com.tonic.analysis.execution.dispatch;

import com.tonic.testutil.BytecodeBuilder;
import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
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

class OpcodeDispatcherObjectTest {
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
    class AALoadTests {
        @Test
        void testAALoadNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestAALoad")
                    .publicStaticMethod("test", "([Ljava/lang/Object;I)Ljava/lang/Object;")
                        .aload(0)
                        .iload(1)
                        .aaload()
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ArrayInstance array = heapManager.newArray("Ljava/lang/Object;", 5);
            ConcreteValue arrayRef = ConcreteValue.reference(array);
            BytecodeResult result = execute(method, arrayRef, ConcreteValue.intValue(0));
            assertTrue(result.getReturnValue().isNull());
        }

        @Test
        void testAALoadWithValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestAALoad2")
                    .publicStaticMethod("test", "([Ljava/lang/Object;I)Ljava/lang/Object;")
                        .aload(0)
                        .iload(1)
                        .aaload()
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ArrayInstance array = heapManager.newArray("Ljava/lang/Object;", 5);
            ObjectInstance obj = heapManager.newObject("java/lang/Object");
            array.set(2, obj);
            ConcreteValue arrayRef = ConcreteValue.reference(array);
            BytecodeResult result = execute(method, arrayRef, ConcreteValue.intValue(2));
            assertFalse(result.getReturnValue().isNull());
        }
    }

    @Nested
    class AAStoreTests {
        @Test
        void testAAStoreNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestAAStore")
                    .publicStaticMethod("test", "([Ljava/lang/Object;ILjava/lang/Object;)V")
                        .aload(0)
                        .iload(1)
                        .aload(2)
                        .aastore()
                        .vreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ArrayInstance array = heapManager.newArray("Ljava/lang/Object;", 5);
            ConcreteValue arrayRef = ConcreteValue.reference(array);
            BytecodeResult result = execute(method, arrayRef, ConcreteValue.intValue(0), ConcreteValue.nullRef());
            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
        }

        @Test
        void testAAStoreWithObject() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestAAStore2")
                    .publicStaticMethod("test", "([Ljava/lang/Object;ILjava/lang/Object;)V")
                        .aload(0)
                        .iload(1)
                        .aload(2)
                        .aastore()
                        .vreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ArrayInstance array = heapManager.newArray("Ljava/lang/Object;", 5);
            ObjectInstance obj = heapManager.newObject("java/lang/Object");
            ConcreteValue arrayRef = ConcreteValue.reference(array);
            ConcreteValue objectRef = ConcreteValue.reference(obj);
            BytecodeResult result = execute(method, arrayRef, ConcreteValue.intValue(3), objectRef);
            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertNotNull(array.get(3));
        }
    }

    @Nested
    class ANewArrayTests {
        @Test
        void testANewArrayObject() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestANewArray")
                    .publicStaticMethod("test", "(I)[Ljava/lang/Object;")
                        .iload(0)
                        .anewarray("java/lang/Object")
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10));
            assertFalse(result.getReturnValue().isNull());
        }

        @Test
        void testANewArrayString() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestANewArray2")
                    .publicStaticMethod("test", "(I)[Ljava/lang/String;")
                        .iload(0)
                        .anewarray("java/lang/String")
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5));
            assertFalse(result.getReturnValue().isNull());
        }
    }

    @Nested
    class InstanceOfTests {
        @Test
        void testInstanceOfNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestInstanceOf")
                    .publicStaticMethod("test", "(Ljava/lang/Object;)I")
                        .aload(0)
                        .instanceof_("java/lang/String")
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.nullRef());
            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testInstanceOfTrue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestInstanceOf2")
                    .publicStaticMethod("test", "(Ljava/lang/Object;)I")
                        .aload(0)
                        .instanceof_("java/lang/Object")
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = heapManager.newObject("java/lang/Object");
            ConcreteValue objectRef = ConcreteValue.reference(obj);
            BytecodeResult result = execute(method, objectRef);
            assertEquals(1, result.getReturnValue().asInt());
        }
    }

    @Nested
    class CheckCastTests {
        @Test
        void testCheckCastNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestCheckCast")
                    .publicStaticMethod("test", "(Ljava/lang/Object;)Ljava/lang/String;")
                        .aload(0)
                        .checkcast("java/lang/String")
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.nullRef());
            assertTrue(result.getReturnValue().isNull());
        }

        @Test
        void testCheckCastSameType() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestCheckCast2")
                    .publicStaticMethod("test", "(Ljava/lang/Object;)Ljava/lang/Object;")
                        .aload(0)
                        .checkcast("java/lang/Object")
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = heapManager.newObject("java/lang/Object");
            ConcreteValue objectRef = ConcreteValue.reference(obj);
            BytecodeResult result = execute(method, objectRef);
            assertFalse(result.getReturnValue().isNull());
        }
    }

    @Nested
    class ArrayLengthTests {
        @Test
        void testArrayLengthObjectArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestArrayLen")
                    .publicStaticMethod("test", "([Ljava/lang/Object;)I")
                        .aload(0)
                        .arraylength()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ArrayInstance array = heapManager.newArray("Ljava/lang/Object;", 7);
            ConcreteValue arrayRef = ConcreteValue.reference(array);
            BytecodeResult result = execute(method, arrayRef);
            assertEquals(7, result.getReturnValue().asInt());
        }

        @Test
        void testArrayLengthIntArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestArrayLen2")
                    .publicStaticMethod("test", "([I)I")
                        .aload(0)
                        .arraylength()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ArrayInstance array = heapManager.newArray("I", 15);
            ConcreteValue arrayRef = ConcreteValue.reference(array);
            BytecodeResult result = execute(method, arrayRef);
            assertEquals(15, result.getReturnValue().asInt());
        }
    }

    @Nested
    class CombinedArrayObjectTests {
        @Test
        void testCreateAndStoreInObjectArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestCombined")
                    .publicStaticMethod("test", "()I")
                        .iconst(3)
                        .anewarray("java/lang/Object")
                        .arraylength()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(3, result.getReturnValue().asInt());
        }

        @Test
        void testLoadStoreRoundTrip() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestRoundTrip")
                    .publicStaticMethod("test", "([Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                        .aload(0)
                        .iconst(0)
                        .aload(1)
                        .aastore()
                        .aload(0)
                        .iconst(0)
                        .aaload()
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ArrayInstance array = heapManager.newArray("Ljava/lang/Object;", 5);
            ObjectInstance obj = heapManager.newObject("java/lang/Object");
            ConcreteValue arrayRef = ConcreteValue.reference(array);
            ConcreteValue objectRef = ConcreteValue.reference(obj);
            BytecodeResult result = execute(method, arrayRef, objectRef);
            assertFalse(result.getReturnValue().isNull());
        }
    }
}
