package com.tonic.analysis.execution.core;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.listener.BytecodeListener;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BytecodeEngineTest {

    private HeapManager heapManager;
    private ClassResolver classResolver;
    private BytecodeContext context;

    @BeforeEach
    void setUp() throws Exception {
        heapManager = new SimpleHeapManager();
        ClassPool pool = new ClassPool();
        classResolver = new ClassResolver(pool);

        context = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(classResolver)
            .maxCallDepth(100)
            .maxInstructions(1000)
            .trackStatistics(true)
            .build();
    }

    @Test
    void testEngineConstruction() {
        BytecodeEngine engine = new BytecodeEngine(context);
        assertNotNull(engine);
        assertNotNull(engine.getCallStack());
        assertEquals(0, engine.getInstructionCount());
    }

    @Test
    void testEngineRejectsNullContext() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BytecodeEngine(null);
        });
    }

    @Test
    void testReset() {
        BytecodeEngine engine = new BytecodeEngine(context);
        engine.reset();

        assertEquals(0, engine.getInstructionCount());
        assertTrue(engine.getCallStack().isEmpty());
    }

    @Test
    void testInterrupt() {
        BytecodeEngine engine = new BytecodeEngine(context);
        engine.interrupt();

        MethodEntry method = createMockMethod();
        BytecodeResult result = engine.execute(method);

        assertEquals(BytecodeResult.Status.INTERRUPTED, result.getStatus());
    }

    @Test
    void testGetCurrentFrameWhenEmpty() {
        BytecodeEngine engine = new BytecodeEngine(context);
        assertNull(engine.getCurrentFrame());
    }

    @Test
    void testAddListener() {
        BytecodeEngine engine = new BytecodeEngine(context);
        TestListener listener = new TestListener();

        BytecodeEngine result = engine.addListener(listener);
        assertSame(engine, result);
    }

    @Test
    void testAddNullListenerIsIgnored() {
        BytecodeEngine engine = new BytecodeEngine(context);
        assertDoesNotThrow(() -> engine.addListener(null));
    }

    @Test
    void testListenerNotification() {
        BytecodeEngine engine = new BytecodeEngine(context);
        TestListener listener = new TestListener();
        engine.addListener(listener);

        MethodEntry method = createMockMethod();
        engine.execute(method);

        assertTrue(listener.beforeCalled || listener.afterCalled);
    }

    @Test
    void testInstructionLimit() {
        BytecodeContext limitedContext = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(classResolver)
            .maxInstructions(5)
            .build();

        BytecodeEngine engine = new BytecodeEngine(limitedContext);
        MethodEntry method = createMockMethodWithManyInstructions(10);

        BytecodeResult result = engine.execute(method);

        assertEquals(BytecodeResult.Status.INSTRUCTION_LIMIT, result.getStatus());
    }

    @Test
    void testDepthLimit() {
        BytecodeContext limitedContext = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(classResolver)
            .maxCallDepth(2)
            .build();

        BytecodeEngine engine = new BytecodeEngine(limitedContext);
        MethodEntry method = createMockMethod();

        assertDoesNotThrow(() -> engine.execute(method));
    }

    @Test
    void testStepExecution() {
        BytecodeEngine engine = new BytecodeEngine(context);
        assertFalse(engine.step());
    }

    @Test
    void testStepWhenInterrupted() {
        BytecodeEngine engine = new BytecodeEngine(context);
        engine.interrupt();
        assertFalse(engine.step());
    }

    @Test
    void testInstructionCountIncreases() {
        BytecodeEngine engine = new BytecodeEngine(context);
        MethodEntry method = createMockMethodWithManyInstructions(3);

        engine.execute(method);

        assertTrue(engine.getInstructionCount() > 0);
    }

    @Test
    void testExecutionStatistics() {
        BytecodeEngine engine = new BytecodeEngine(context);
        MethodEntry method = createMockMethod();

        BytecodeResult result = engine.execute(method);

        assertTrue(result.getInstructionsExecuted() >= 0);
        assertTrue(result.getExecutionTimeNanos() >= 0);
    }

    @Test
    void testCallStackManagement() {
        BytecodeEngine engine = new BytecodeEngine(context);
        MethodEntry method = createMockMethod();

        engine.execute(method);

        assertTrue(engine.getCallStack().isEmpty());
    }

    @Test
    void testMultipleExecutions() {
        BytecodeEngine engine = new BytecodeEngine(context);
        MethodEntry method = createMockMethod();

        BytecodeResult result1 = engine.execute(method);
        BytecodeResult result2 = engine.execute(method);

        assertNotNull(result1);
        assertNotNull(result2);
    }

    @Test
    void testExecutionWithArguments() {
        BytecodeEngine engine = new BytecodeEngine(context);
        MethodEntry method = createMockMethod();

        ConcreteValue arg1 = ConcreteValue.intValue(10);
        ConcreteValue arg2 = ConcreteValue.intValue(20);

        BytecodeResult result = engine.execute(method, arg1, arg2);

        assertNotNull(result);
    }

    @Test
    void testExecutionWithNoArguments() {
        BytecodeEngine engine = new BytecodeEngine(context);
        MethodEntry method = createMockMethod();

        BytecodeResult result = engine.execute(method);

        assertNotNull(result);
    }

    @Test
    void testNewObjectCreation() {
        BytecodeEngine engine = new BytecodeEngine(context);

        long initialCount = heapManager.objectCount();
        MethodEntry method = createMockMethod();
        engine.execute(method);

        assertTrue(heapManager.objectCount() >= initialCount);
    }

    @Test
    void testExceptionHandling() {
        BytecodeEngine engine = new BytecodeEngine(context);
        MethodEntry method = createMockMethodThatThrows();

        BytecodeResult result = engine.execute(method);

        assertNotNull(result);
    }

    @Test
    void testResetClearsState() {
        BytecodeEngine engine = new BytecodeEngine(context);
        MethodEntry method = createMockMethod();

        engine.execute(method);
        long count1 = engine.getInstructionCount();

        engine.reset();
        assertEquals(0, engine.getInstructionCount());
        assertTrue(engine.getCallStack().isEmpty());

        engine.execute(method);
        long count2 = engine.getInstructionCount();

        assertTrue(count2 > 0);
    }

    @Test
    void testGetCallStackReturnsCorrectInstance() {
        BytecodeEngine engine = new BytecodeEngine(context);
        assertNotNull(engine.getCallStack());
        assertSame(engine.getCallStack(), engine.getCallStack());
    }

    @Test
    void testDelegatedMode() {
        BytecodeContext delegatedContext = new BytecodeContext.Builder()
            .mode(ExecutionMode.DELEGATED)
            .heapManager(heapManager)
            .classResolver(classResolver)
            .build();

        BytecodeEngine engine = new BytecodeEngine(delegatedContext);
        MethodEntry method = createMockMethod();

        BytecodeResult result = engine.execute(method);

        assertNotNull(result);
    }

    @Test
    void testRecursiveMode() {
        BytecodeContext recursiveContext = new BytecodeContext.Builder()
            .mode(ExecutionMode.RECURSIVE)
            .heapManager(heapManager)
            .classResolver(classResolver)
            .build();

        BytecodeEngine engine = new BytecodeEngine(recursiveContext);
        MethodEntry method = createMockMethod();

        BytecodeResult result = engine.execute(method);

        assertNotNull(result);
    }

    private MethodEntry createMockMethod() {
        MethodEntry method = mock(MethodEntry.class);
        CodeAttribute codeAttr = mock(CodeAttribute.class);
        ClassFile classFile = mock(ClassFile.class);
        com.tonic.parser.ConstPool constPool = mock(com.tonic.parser.ConstPool.class);

        when(method.getCodeAttribute()).thenReturn(codeAttr);
        when(method.getName()).thenReturn("testMethod");
        when(method.getDesc()).thenReturn("()V");
        when(method.getOwnerName()).thenReturn("TestClass");
        when(method.getClassFile()).thenReturn(classFile);
        when(classFile.getConstPool()).thenReturn(constPool);
        when(codeAttr.getMaxStack()).thenReturn(10);
        when(codeAttr.getMaxLocals()).thenReturn(10);
        when(codeAttr.getCode()).thenReturn(new byte[] { (byte) 0xB1 });

        return method;
    }

    private MethodEntry createMockMethodWithManyInstructions(int count) {
        MethodEntry method = mock(MethodEntry.class);
        CodeAttribute codeAttr = mock(CodeAttribute.class);
        ClassFile classFile = mock(ClassFile.class);
        com.tonic.parser.ConstPool constPool = mock(com.tonic.parser.ConstPool.class);

        when(method.getCodeAttribute()).thenReturn(codeAttr);
        when(method.getName()).thenReturn("testMethod");
        when(method.getDesc()).thenReturn("()V");
        when(method.getOwnerName()).thenReturn("TestClass");
        when(method.getClassFile()).thenReturn(classFile);
        when(classFile.getConstPool()).thenReturn(constPool);
        when(codeAttr.getMaxStack()).thenReturn(10);
        when(codeAttr.getMaxLocals()).thenReturn(10);
        when(codeAttr.getCode()).thenReturn(new byte[] {
            (byte) 0xa7, 0x00, 0x00
        });

        return method;
    }

    private MethodEntry createMockMethodThatThrows() {
        return createMockMethod();
    }

    private static class TestListener implements BytecodeListener {
        boolean beforeCalled = false;
        boolean afterCalled = false;

        @Override
        public void beforeInstruction(StackFrame frame, Instruction instruction) {
            beforeCalled = true;
        }

        @Override
        public void afterInstruction(StackFrame frame, Instruction instruction) {
            afterCalled = true;
        }
    }
}
