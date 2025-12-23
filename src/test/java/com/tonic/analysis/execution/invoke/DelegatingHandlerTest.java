package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.frame.CallStack;
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
import static org.mockito.Mockito.doReturn;

class DelegatingHandlerTest {

    private InvocationContext context;
    private HeapManager heapManager;

    @BeforeEach
    void setUp() {
        heapManager = new SimpleHeapManager();
        ClassResolver resolver = mock(ClassResolver.class);
        CallStack callStack = new CallStack(100);

        context = new InvocationContext() {
            @Override
            public CallStack getCallStack() {
                return callStack;
            }

            @Override
            public HeapManager getHeapManager() {
                return heapManager;
            }

            @Override
            public ClassResolver getClassResolver() {
                return resolver;
            }
        };
    }

    @Test
    void testConstructorWithNullCallback() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DelegatingHandler(null);
        });
    }

    @Test
    void testCallbackInvokedCorrectly() {
        ConcreteValue expectedResult = ConcreteValue.intValue(42);
        boolean[] callbackInvoked = { false };

        DelegatingHandler.InvocationCallback callback = (method, receiver, args) -> {
            callbackInvoked[0] = true;
            return expectedResult;
        };

        DelegatingHandler handler = new DelegatingHandler(callback);
        MethodEntry method = createTestMethod("TestClass", "method", "()I", 0x0009);

        InvocationResult result = handler.invoke(method, null, new ConcreteValue[0], context);

        assertTrue(callbackInvoked[0]);
        assertTrue(result.isNativeHandled());
        assertEquals(expectedResult, result.getReturnValue());
    }

    @Test
    void testCallbackReceivesCorrectArguments() {
        MethodEntry expectedMethod = createTestMethod("TestClass", "method", "(I)V", 0x0001);
        ObjectInstance expectedReceiver = new ObjectInstance(1, "TestClass");
        ConcreteValue[] expectedArgs = new ConcreteValue[] { ConcreteValue.intValue(10) };

        DelegatingHandler.InvocationCallback callback = (method, receiver, args) -> {
            assertEquals(expectedMethod, method);
            assertEquals(expectedReceiver, receiver);
            assertEquals(expectedArgs.length, args.length);
            assertEquals(expectedArgs[0], args[0]);
            return ConcreteValue.intValue(0);
        };

        DelegatingHandler handler = new DelegatingHandler(callback);
        handler.invoke(expectedMethod, expectedReceiver, expectedArgs, context);
    }

    @Test
    void testCallbackReturnsValue() {
        ConcreteValue[] testValues = {
            ConcreteValue.intValue(42),
            ConcreteValue.longValue(100L),
            ConcreteValue.floatValue(3.14f),
            ConcreteValue.doubleValue(2.718),
            ConcreteValue.nullRef()
        };

        for (ConcreteValue testValue : testValues) {
            DelegatingHandler handler = new DelegatingHandler((m, r, a) -> testValue);
            MethodEntry method = createTestMethod("Test", "m", "()V", 0x0001);

            InvocationResult result = handler.invoke(method, null, new ConcreteValue[0], context);

            assertTrue(result.isNativeHandled());
            assertEquals(testValue, result.getReturnValue());
        }
    }

    @Test
    void testCallbackThrowsException() {
        DelegatingHandler.InvocationCallback callback = (method, receiver, args) -> {
            throw new RuntimeException("Test exception");
        };

        DelegatingHandler handler = new DelegatingHandler(callback);
        MethodEntry method = createTestMethod("TestClass", "method", "()V", 0x0001);

        InvocationResult result = handler.invoke(method, null, new ConcreteValue[0], context);

        assertTrue(result.isException());
        assertNotNull(result.getException());
    }

    @Test
    void testExceptionCreation() {
        DelegatingHandler.InvocationCallback callback = (method, receiver, args) -> {
            throw new IllegalArgumentException("Invalid argument");
        };

        DelegatingHandler handler = new DelegatingHandler(callback);
        MethodEntry method = createTestMethod("TestClass", "method", "()V", 0x0001);

        InvocationResult result = handler.invoke(method, null, new ConcreteValue[0], context);

        assertTrue(result.isException());
        ObjectInstance exception = result.getException();
        assertNotNull(exception);
        assertTrue(exception.getClassName().contains("IllegalArgumentException"));
    }

    @Test
    void testNullReceiverHandling() {
        DelegatingHandler.InvocationCallback callback = (method, receiver, args) -> {
            assertNull(receiver);
            return ConcreteValue.intValue(0);
        };

        DelegatingHandler handler = new DelegatingHandler(callback);
        MethodEntry method = createTestMethod("TestClass", "staticMethod", "()I", 0x0009);

        InvocationResult result = handler.invoke(method, null, new ConcreteValue[0], context);

        assertTrue(result.isNativeHandled());
    }

    @Test
    void testNonNullReceiverHandling() {
        ObjectInstance receiver = new ObjectInstance(1, "TestClass");

        DelegatingHandler.InvocationCallback callback = (method, r, args) -> {
            assertNotNull(r);
            assertEquals(receiver, r);
            return ConcreteValue.intValue(0);
        };

        DelegatingHandler handler = new DelegatingHandler(callback);
        MethodEntry method = createTestMethod("TestClass", "method", "()I", 0x0001);

        handler.invoke(method, receiver, new ConcreteValue[0], context);
    }

    @Test
    void testEmptyArguments() {
        DelegatingHandler.InvocationCallback callback = (method, receiver, args) -> {
            assertNotNull(args);
            assertEquals(0, args.length);
            return ConcreteValue.intValue(0);
        };

        DelegatingHandler handler = new DelegatingHandler(callback);
        MethodEntry method = createTestMethod("TestClass", "method", "()I", 0x0009);

        handler.invoke(method, null, new ConcreteValue[0], context);
    }

    @Test
    void testMultipleArguments() {
        ConcreteValue[] args = new ConcreteValue[] {
            ConcreteValue.intValue(1),
            ConcreteValue.longValue(2L),
            ConcreteValue.floatValue(3.0f)
        };

        DelegatingHandler.InvocationCallback callback = (method, receiver, a) -> {
            assertEquals(args.length, a.length);
            for (int i = 0; i < args.length; i++) {
                assertEquals(args[i], a[i]);
            }
            return ConcreteValue.intValue(0);
        };

        DelegatingHandler handler = new DelegatingHandler(callback);
        MethodEntry method = createTestMethod("TestClass", "method", "(IJF)I", 0x0009);

        handler.invoke(method, null, args, context);
    }

    @Test
    void testCallbackReturnsNull() {
        DelegatingHandler.InvocationCallback callback = (method, receiver, args) -> null;

        DelegatingHandler handler = new DelegatingHandler(callback);
        MethodEntry method = createTestMethod("TestClass", "method", "()V", 0x0001);

        InvocationResult result = handler.invoke(method, null, new ConcreteValue[0], context);

        assertTrue(result.isNativeHandled());
        assertNull(result.getReturnValue());
    }

    @Test
    void testExceptionMessagePreserved() {
        String testMessage = "Custom error message";
        DelegatingHandler.InvocationCallback callback = (method, receiver, args) -> {
            throw new RuntimeException(testMessage);
        };

        DelegatingHandler handler = new DelegatingHandler(callback);
        MethodEntry method = createTestMethod("TestClass", "method", "()V", 0x0001);

        InvocationResult result = handler.invoke(method, null, new ConcreteValue[0], context);

        assertTrue(result.isException());
        ObjectInstance exception = result.getException();
        Object messageField = exception.getField(
            exception.getClassName(),
            "detailMessage",
            "Ljava/lang/String;"
        );
        assertNotNull(messageField);
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
