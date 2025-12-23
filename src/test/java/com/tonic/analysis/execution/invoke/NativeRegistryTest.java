package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeRegistryTest {

    private NativeRegistry registry;
    private NativeContext context;
    private HeapManager heapManager;

    @BeforeEach
    void setUp() {
        registry = new NativeRegistry();
        heapManager = new SimpleHeapManager();
        ClassResolver resolver = mock(ClassResolver.class);

        context = new NativeContext() {
            @Override
            public HeapManager getHeapManager() {
                return heapManager;
            }

            @Override
            public ClassResolver getClassResolver() {
                return resolver;
            }

            @Override
            public ObjectInstance createString(String value) {
                return heapManager.internString(value);
            }

            @Override
            public ObjectInstance createException(String className, String message) {
                ObjectInstance ex = heapManager.newObject(className);
                if (message != null) {
                    ex.setField(className, "detailMessage", "Ljava/lang/String;",
                        heapManager.internString(message));
                }
                return ex;
            }
        };
    }

    @Test
    void testRegisterByComponents() {
        NativeMethodHandler handler = (receiver, args, ctx) -> ConcreteValue.intValue(42);
        registry.register("TestClass", "method", "()I", handler);

        assertTrue(registry.hasHandler("TestClass", "method", "()I"));
    }

    @Test
    void testRegisterByKey() {
        NativeMethodHandler handler = (receiver, args, ctx) -> ConcreteValue.intValue(42);
        String key = NativeRegistry.methodKey("TestClass", "method", "()I");
        registry.register(key, handler);

        assertTrue(registry.hasHandler("TestClass", "method", "()I"));
    }

    @Test
    void testHasHandlerWithMethod() {
        MethodEntry method = createTestMethod("TestClass", "method", "()I", 0x0101);
        NativeMethodHandler handler = (receiver, args, ctx) -> ConcreteValue.intValue(0);
        registry.register("TestClass", "method", "()I", handler);

        assertTrue(registry.hasHandler(method));
    }

    @Test
    void testHasHandlerReturnsFalse() {
        assertFalse(registry.hasHandler("NonExistent", "method", "()V"));
    }

    @Test
    void testGetHandler() {
        NativeMethodHandler handler = (receiver, args, ctx) -> ConcreteValue.intValue(42);
        registry.register("TestClass", "method", "()I", handler);

        NativeMethodHandler retrieved = registry.getHandler("TestClass", "method", "()I");
        assertNotNull(retrieved);
        assertEquals(handler, retrieved);
    }

    @Test
    void testGetHandlerWithMethod() {
        MethodEntry method = createTestMethod("TestClass", "method", "()I", 0x0101);
        NativeMethodHandler handler = (receiver, args, ctx) -> ConcreteValue.intValue(42);
        registry.register("TestClass", "method", "()I", handler);

        NativeMethodHandler retrieved = registry.getHandler(method);
        assertNotNull(retrieved);
    }

    @Test
    void testGetHandlerThrowsWhenNotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            registry.getHandler("NonExistent", "method", "()V");
        });
    }

    @Test
    void testExecute() throws NativeException {
        NativeMethodHandler handler = (receiver, args, ctx) -> ConcreteValue.intValue(99);
        registry.register("TestClass", "method", "()I", handler);

        MethodEntry method = createTestMethod("TestClass", "method", "()I", 0x0101);
        ConcreteValue result = registry.execute(method, null, new ConcreteValue[0], context);

        assertEquals(99, result.asInt());
    }

    @Test
    void testMethodKeyGeneration() {
        String key = NativeRegistry.methodKey("java/lang/Object", "hashCode", "()I");
        assertEquals("java/lang/Object.hashCode()I", key);
    }

    @Test
    void testMethodKeyFromMethodEntry() {
        MethodEntry method = createTestMethod("TestClass", "method", "(II)I", 0x0009);
        String key = NativeRegistry.methodKey(method);
        assertEquals("TestClass.method(II)I", key);
    }

    @Test
    void testRegisterDefaults() {
        registry.registerDefaults();

        assertTrue(registry.hasHandler("java/lang/Object", "hashCode", "()I"));
        assertTrue(registry.hasHandler("java/lang/Object", "equals", "(Ljava/lang/Object;)Z"));
        assertTrue(registry.hasHandler("java/lang/System", "identityHashCode", "(Ljava/lang/Object;)I"));
        assertTrue(registry.hasHandler("java/lang/Math", "abs", "(I)I"));
        assertTrue(registry.hasHandler("java/lang/Float", "floatToRawIntBits", "(F)I"));
        assertTrue(registry.hasHandler("java/lang/Double", "doubleToRawLongBits", "(D)J"));
        assertTrue(registry.hasHandler("java/lang/String", "length", "()I"));
    }

    @Test
    void testObjectHashCode() throws NativeException {
        registry.registerDefaults();
        ObjectInstance obj = new ObjectInstance(123, "TestClass");

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/Object", "hashCode", "()I", 0x0101),
            obj,
            new ConcreteValue[0],
            context
        );

        assertEquals(123, result.asInt());
    }

    @Test
    void testObjectEquals() throws NativeException {
        registry.registerDefaults();
        ObjectInstance obj1 = new ObjectInstance(1, "TestClass");
        ObjectInstance obj2 = new ObjectInstance(2, "TestClass");

        ConcreteValue resultSame = registry.execute(
            createTestMethod("java/lang/Object", "equals", "(Ljava/lang/Object;)Z", 0x0101),
            obj1,
            new ConcreteValue[] { ConcreteValue.reference(obj1) },
            context
        );
        assertEquals(1, resultSame.asInt());

        ConcreteValue resultDiff = registry.execute(
            createTestMethod("java/lang/Object", "equals", "(Ljava/lang/Object;)Z", 0x0101),
            obj1,
            new ConcreteValue[] { ConcreteValue.reference(obj2) },
            context
        );
        assertEquals(0, resultDiff.asInt());
    }

    @Test
    void testSystemIdentityHashCode() throws NativeException {
        registry.registerDefaults();
        ObjectInstance obj = new ObjectInstance(456, "TestClass");

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/System", "identityHashCode", "(Ljava/lang/Object;)I", 0x0109),
            null,
            new ConcreteValue[] { ConcreteValue.reference(obj) },
            context
        );

        assertEquals(456, result.asInt());
    }

    @Test
    void testSystemCurrentTimeMillis() throws NativeException {
        registry.registerDefaults();
        long before = System.currentTimeMillis();

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/System", "currentTimeMillis", "()J", 0x0109),
            null,
            new ConcreteValue[0],
            context
        );

        long after = System.currentTimeMillis();
        long value = result.asLong();
        assertTrue(value >= before && value <= after);
    }

    @Test
    void testMathAbsInt() throws NativeException {
        registry.registerDefaults();

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/Math", "abs", "(I)I", 0x0109),
            null,
            new ConcreteValue[] { ConcreteValue.intValue(-42) },
            context
        );

        assertEquals(42, result.asInt());
    }

    @Test
    void testMathAbsLong() throws NativeException {
        registry.registerDefaults();

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/Math", "abs", "(J)J", 0x0109),
            null,
            new ConcreteValue[] { ConcreteValue.longValue(-999L) },
            context
        );

        assertEquals(999L, result.asLong());
    }

    @Test
    void testMathMaxInt() throws NativeException {
        registry.registerDefaults();

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/Math", "max", "(II)I", 0x0109),
            null,
            new ConcreteValue[] { ConcreteValue.intValue(10), ConcreteValue.intValue(20) },
            context
        );

        assertEquals(20, result.asInt());
    }

    @Test
    void testMathSqrt() throws NativeException {
        registry.registerDefaults();

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/Math", "sqrt", "(D)D", 0x0109),
            null,
            new ConcreteValue[] { ConcreteValue.doubleValue(16.0) },
            context
        );

        assertEquals(4.0, result.asDouble(), 0.0001);
    }

    @Test
    void testFloatToRawIntBits() throws NativeException {
        registry.registerDefaults();
        float value = 3.14f;

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/Float", "floatToRawIntBits", "(F)I", 0x0109),
            null,
            new ConcreteValue[] { ConcreteValue.floatValue(value) },
            context
        );

        assertEquals(Float.floatToRawIntBits(value), result.asInt());
    }

    @Test
    void testIntBitsToFloat() throws NativeException {
        registry.registerDefaults();
        int bits = Float.floatToRawIntBits(3.14f);

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/Float", "intBitsToFloat", "(I)F", 0x0109),
            null,
            new ConcreteValue[] { ConcreteValue.intValue(bits) },
            context
        );

        assertEquals(3.14f, result.asFloat(), 0.0001);
    }

    @Test
    void testDoubleToRawLongBits() throws NativeException {
        registry.registerDefaults();
        double value = 2.718;

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/Double", "doubleToRawLongBits", "(D)J", 0x0109),
            null,
            new ConcreteValue[] { ConcreteValue.doubleValue(value) },
            context
        );

        assertEquals(Double.doubleToRawLongBits(value), result.asLong());
    }

    @Test
    void testStringLength() throws NativeException {
        registry.registerDefaults();
        ObjectInstance str = heapManager.internString("Hello");

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/String", "length", "()I", 0x0101),
            str,
            new ConcreteValue[0],
            context
        );

        assertEquals(5, result.asInt());
    }

    @Test
    void testStringCharAt() throws NativeException {
        registry.registerDefaults();
        ObjectInstance str = heapManager.internString("Test");

        ConcreteValue result = registry.execute(
            createTestMethod("java/lang/String", "charAt", "(I)C", 0x0101),
            str,
            new ConcreteValue[] { ConcreteValue.intValue(1) },
            context
        );

        assertEquals('e', (char) result.asInt());
    }

    @Test
    void testArrayCopy() throws NativeException {
        registry.registerDefaults();
        ArrayInstance src = heapManager.newArray("I", 5);
        ArrayInstance dest = heapManager.newArray("I", 5);

        for (int i = 0; i < 5; i++) {
            src.setInt(i, i * 10);
        }

        registry.execute(
            createTestMethod("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", 0x0109),
            null,
            new ConcreteValue[] {
                ConcreteValue.reference(src),
                ConcreteValue.intValue(1),
                ConcreteValue.reference(dest),
                ConcreteValue.intValue(0),
                ConcreteValue.intValue(3)
            },
            context
        );

        assertEquals(10, dest.getInt(0));
        assertEquals(20, dest.getInt(1));
        assertEquals(30, dest.getInt(2));
    }

    private MethodEntry createTestMethod(String owner, String name, String desc, int access) {
        ClassFile classFile = mock(ClassFile.class);
        when(classFile.getClassName()).thenReturn(owner);

        com.tonic.parser.ConstPool constPool = mock(com.tonic.parser.ConstPool.class);
        com.tonic.parser.constpool.Utf8Item nameItem = mock(com.tonic.parser.constpool.Utf8Item.class);
        com.tonic.parser.constpool.Utf8Item descItem = mock(com.tonic.parser.constpool.Utf8Item.class);
        when(nameItem.getValue()).thenReturn(name);
        when(descItem.getValue()).thenReturn(desc);
        doReturn(nameItem).when(constPool).getItem(1);
        doReturn(descItem).when(constPool).getItem(2);
        when(classFile.getConstPool()).thenReturn(constPool);

        MethodEntry method = new MethodEntry(classFile, access, 1, 2, new ArrayList<>());

        return method;
    }
}
