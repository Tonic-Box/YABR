package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.frame.CallStack;
import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.resolve.ResolvedMethod;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

class RecursiveHandlerTest {

    private ClassResolver resolver;
    private NativeRegistry nativeRegistry;
    private RecursiveHandler handler;
    private InvocationContext context;
    private HeapManager heapManager;

    @BeforeEach
    void setUp() {
        resolver = mock(ClassResolver.class);
        nativeRegistry = new NativeRegistry();
        handler = new RecursiveHandler(resolver, nativeRegistry);

        heapManager = new SimpleHeapManager();
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
    void testConstructorWithNullResolver() {
        assertThrows(IllegalArgumentException.class, () -> new RecursiveHandler(null));
    }

    @Test
    void testConstructorWithNullRegistry() {
        assertThrows(IllegalArgumentException.class, () -> new RecursiveHandler(resolver, null));
    }

    @Test
    void testStaticMethodInvocation() {
        MethodEntry method = createTestMethod("TestClass", "staticMethod", "()I", 0x0009);
        ConcreteValue[] args = new ConcreteValue[0];

        InvocationResult result = handler.invoke(method, null, args, context);

        assertTrue(result.isPushFrame());
        assertNotNull(result.getNewFrame());
    }

    @Test
    void testInstanceMethodInvocation() {
        MethodEntry method = createTestMethod("TestClass", "instanceMethod", "()V", 0x0001);
        ObjectInstance receiver = new ObjectInstance(1, "TestClass");
        ConcreteValue[] args = new ConcreteValue[0];

        when(resolver.resolveVirtualMethod(anyString(), anyString(), anyString()))
            .thenReturn(new ResolvedMethod(method, mock(ClassFile.class),
                ResolvedMethod.InvokeKind.VIRTUAL));

        InvocationResult result = handler.invoke(method, receiver, args, context);

        assertTrue(result.isPushFrame());
        StackFrame frame = result.getNewFrame();
        assertNotNull(frame);
        assertEquals(receiver, frame.getLocals().get(0).asReference());
    }

    @Test
    void testInstanceMethodWithArguments() {
        MethodEntry method = createTestMethod("TestClass", "method", "(II)I", 0x0001);
        ObjectInstance receiver = new ObjectInstance(1, "TestClass");
        ConcreteValue[] args = new ConcreteValue[] {
            ConcreteValue.intValue(10),
            ConcreteValue.intValue(20)
        };

        when(resolver.resolveVirtualMethod(anyString(), anyString(), anyString()))
            .thenReturn(new ResolvedMethod(method, mock(ClassFile.class),
                ResolvedMethod.InvokeKind.VIRTUAL));

        InvocationResult result = handler.invoke(method, receiver, args, context);

        assertTrue(result.isPushFrame());
        StackFrame frame = result.getNewFrame();
        assertEquals(receiver, frame.getLocals().get(0).asReference());
        assertEquals(10, frame.getLocals().get(1).asInt());
        assertEquals(20, frame.getLocals().get(2).asInt());
    }

    @Test
    void testStaticMethodWithArguments() {
        MethodEntry method = createTestMethod("TestClass", "staticMethod", "(JI)J", 0x0009);
        ConcreteValue[] args = new ConcreteValue[] {
            ConcreteValue.longValue(100L),
            ConcreteValue.intValue(5)
        };

        InvocationResult result = handler.invoke(method, null, args, context);

        assertTrue(result.isPushFrame());
        StackFrame frame = result.getNewFrame();
        assertEquals(100L, frame.getLocals().get(0).asLong());
        assertFalse(frame.getLocals().isDefined(1));
        assertEquals(5, frame.getLocals().get(2).asInt());
    }

    @Test
    void testWideArgumentExpansion() {
        MethodEntry method = createTestMethod("TestClass", "method", "(DJI)V", 0x0009);
        ConcreteValue[] args = new ConcreteValue[] {
            ConcreteValue.doubleValue(3.14),
            ConcreteValue.longValue(999L),
            ConcreteValue.intValue(42)
        };

        InvocationResult result = handler.invoke(method, null, args, context);

        assertTrue(result.isPushFrame());
        StackFrame frame = result.getNewFrame();
        assertEquals(3.14, frame.getLocals().get(0).asDouble());
        assertFalse(frame.getLocals().isDefined(1));
        assertEquals(999L, frame.getLocals().get(2).asLong());
        assertFalse(frame.getLocals().isDefined(3));
        assertEquals(42, frame.getLocals().get(4).asInt());
    }

    @Test
    void testVirtualDispatchResolution() {
        MethodEntry baseMethod = createTestMethod("BaseClass", "method", "()V", 0x0001);
        MethodEntry derivedMethod = createTestMethod("DerivedClass", "method", "()V", 0x0001);
        ObjectInstance receiver = new ObjectInstance(1, "DerivedClass");

        when(resolver.resolveVirtualMethod(eq("DerivedClass"), eq("method"), eq("()V")))
            .thenReturn(new ResolvedMethod(derivedMethod, mock(ClassFile.class),
                ResolvedMethod.InvokeKind.VIRTUAL));

        InvocationResult result = handler.invoke(baseMethod, receiver, new ConcreteValue[0], context);

        assertTrue(result.isPushFrame());
        assertEquals("DerivedClass", result.getNewFrame().getMethod().getOwnerName());
    }

    @Test
    void testPrivateMethodNoVirtualDispatch() {
        MethodEntry method = createTestMethod("TestClass", "privateMethod", "()V", 0x0002);
        ObjectInstance receiver = new ObjectInstance(1, "DerivedClass");

        InvocationResult result = handler.invoke(method, receiver, new ConcreteValue[0], context);

        assertTrue(result.isPushFrame());
        verify(resolver, never()).resolveVirtualMethod(anyString(), anyString(), anyString());
    }

    @Test
    void testConstructorNoVirtualDispatch() {
        MethodEntry method = createTestMethod("TestClass", "<init>", "()V", 0x0001);
        ObjectInstance receiver = new ObjectInstance(1, "TestClass");

        InvocationResult result = handler.invoke(method, receiver, new ConcreteValue[0], context);

        assertTrue(result.isPushFrame());
        verify(resolver, never()).resolveVirtualMethod(anyString(), anyString(), anyString());
    }

    @Test
    void testNativeMethodWithHandler() {
        MethodEntry method = createTestMethod("TestClass", "nativeMethod", "()I", 0x0101);
        nativeRegistry.register("TestClass", "nativeMethod", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(42));

        InvocationResult result = handler.invoke(method, null, new ConcreteValue[0], context);

        assertTrue(result.isNativeHandled());
        assertEquals(42, result.getReturnValue().asInt());
    }

    @Test
    void testNativeMethodWithoutHandler() {
        MethodEntry method = createTestMethod("TestClass", "nativeMethod", "()V", 0x0101);

        assertThrows(UnsupportedOperationException.class, () -> handler.invoke(method, null, new ConcreteValue[0], context));
    }

    @Test
    void testNativeMethodThrowsException() {
        MethodEntry method = createTestMethod("TestClass", "nativeMethod", "()V", 0x0101);
        nativeRegistry.register("TestClass", "nativeMethod", "()V",
            (receiver, args, ctx) -> {
                throw new NativeException("java/lang/RuntimeException", "Test error");
            });

        InvocationResult result = handler.invoke(method, null, new ConcreteValue[0], context);

        assertTrue(result.isException());
        assertNotNull(result.getException());
        assertEquals("java/lang/RuntimeException", result.getException().getClassName());
    }

    @Test
    void testReceiverPrepending() {
        MethodEntry method = createTestMethod("TestClass", "method", "(I)V", 0x0001);
        ObjectInstance receiver = new ObjectInstance(1, "TestClass");
        ConcreteValue[] args = new ConcreteValue[] { ConcreteValue.intValue(99) };

        when(resolver.resolveVirtualMethod(anyString(), anyString(), anyString()))
            .thenReturn(new ResolvedMethod(method, mock(ClassFile.class),
                ResolvedMethod.InvokeKind.VIRTUAL));

        InvocationResult result = handler.invoke(method, receiver, args, context);

        assertTrue(result.isPushFrame());
        StackFrame frame = result.getNewFrame();
        assertEquals(receiver, frame.getLocals().get(0).asReference());
        assertEquals(99, frame.getLocals().get(1).asInt());
    }

    @Test
    void testNullReceiverHandling() {
        MethodEntry method = createTestMethod("TestClass", "method", "()V", 0x0001);

        InvocationResult result = handler.invoke(method, null, new ConcreteValue[0], context);

        assertTrue(result.isPushFrame());
        StackFrame frame = result.getNewFrame();
        assertTrue(frame.getLocals().get(0).isNull());
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

        MethodEntry method = spy(new MethodEntry(classFile, access, 1, 2, new ArrayList<>()));

        com.tonic.parser.attribute.CodeAttribute codeAttr = mock(com.tonic.parser.attribute.CodeAttribute.class);
        when(codeAttr.getMaxLocals()).thenReturn(10);
        when(codeAttr.getMaxStack()).thenReturn(10);
        when(codeAttr.getCode()).thenReturn(new byte[] { (byte)0xB1 });
        doReturn(codeAttr).when(method).getCodeAttribute();

        return method;
    }
}
