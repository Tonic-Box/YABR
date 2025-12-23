package com.tonic.analysis.execution.dispatch;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.parser.ClassPool;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.InvokeDynamicInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvokeDynamicDispatcherTest {

    private OpcodeDispatcher dispatcher;
    private StackFrame frame;
    private ConcreteStack stack;
    private ConcreteLocals locals;
    private TestDispatchContext context;

    @BeforeEach
    void setUp() {
        dispatcher = new OpcodeDispatcher();
        stack = new ConcreteStack(20);
        locals = new ConcreteLocals(10);
        context = new TestDispatchContext();
    }

    @Nested
    class InvokeDynamicInfoTests {

        @Test
        void testBasicConstruction() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "run", "()V", 10);
            assertEquals(0, info.getBootstrapMethodIndex());
            assertEquals("run", info.getMethodName());
            assertEquals("()V", info.getDescriptor());
            assertEquals(10, info.getConstantPoolIndex());
        }

        @Test
        void testParameterSlotsNoParams() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "run", "()V", 10);
            assertEquals(0, info.getParameterSlots());
        }

        @Test
        void testParameterSlotsSingleInt() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply", "(I)I", 10);
            assertEquals(1, info.getParameterSlots());
        }

        @Test
        void testParameterSlotsSingleLong() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply", "(J)J", 10);
            assertEquals(2, info.getParameterSlots());
        }

        @Test
        void testParameterSlotsSingleDouble() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply", "(D)D", 10);
            assertEquals(2, info.getParameterSlots());
        }

        @Test
        void testParameterSlotsObjectRef() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply", "(Ljava/lang/String;)V", 10);
            assertEquals(1, info.getParameterSlots());
        }

        @Test
        void testParameterSlotsArrayRef() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply", "([I)V", 10);
            assertEquals(1, info.getParameterSlots());
        }

        @Test
        void testParameterSlotsMultiDimArray() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply", "([[Ljava/lang/Object;)V", 10);
            assertEquals(1, info.getParameterSlots());
        }

        @Test
        void testParameterSlotsMixed() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply", "(IJDLjava/lang/String;FZ)V", 10);
            assertEquals(8, info.getParameterSlots());
        }

        @Test
        void testReturnTypeVoid() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "run", "()V", 10);
            assertEquals("V", info.getReturnType());
            assertTrue(info.isVoidReturn());
        }

        @Test
        void testReturnTypeInt() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "getAsInt", "()I", 10);
            assertEquals("I", info.getReturnType());
            assertFalse(info.isVoidReturn());
        }

        @Test
        void testReturnTypeLong() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "getAsLong", "()J", 10);
            assertEquals("J", info.getReturnType());
        }

        @Test
        void testReturnTypeObject() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply", "()Ljava/lang/Object;", 10);
            assertEquals("Ljava/lang/Object;", info.getReturnType());
        }

        @Test
        void testReturnTypeArray() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "get", "()[I", 10);
            assertEquals("[I", info.getReturnType());
        }

        @Test
        void testIsLambdaMetafactoryRun() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "run", "()Ljava/lang/Runnable;", 10);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testIsLambdaMetafactoryApply() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", 10);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testIsLambdaMetafactoryAccept() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "accept", "(Ljava/lang/Object;)V", 10);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testIsLambdaMetafactoryTest() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "test", "(Ljava/lang/Object;)Z", 10);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testIsLambdaMetafactoryGet() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "get", "()Ljava/lang/Object;", 10);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testIsLambdaMetafactoryGetAsInt() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "getAsInt", "()I", 10);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testIsLambdaMetafactoryGetAsLong() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "getAsLong", "()J", 10);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testIsLambdaMetafactoryGetAsDouble() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "getAsDouble", "()D", 10);
            assertTrue(info.isLambdaMetafactory());
        }

        @Test
        void testIsNotLambdaMetafactory() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "someOtherMethod", "()V", 10);
            assertFalse(info.isLambdaMetafactory());
        }

        @Test
        void testIsStringConcatMakeConcatWithConstants() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants", "(Ljava/lang/String;I)Ljava/lang/String;", 10);
            assertTrue(info.isStringConcat());
        }

        @Test
        void testIsStringConcatMakeConcat() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcat", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", 10);
            assertTrue(info.isStringConcat());
        }

        @Test
        void testIsNotStringConcat() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "run", "()V", 10);
            assertFalse(info.isStringConcat());
        }

        @Test
        void testToString() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(5, "run", "()V", 20);
            String str = info.toString();
            assertTrue(str.contains("bsm=5"));
            assertTrue(str.contains("run"));
            assertTrue(str.contains("()V"));
            assertTrue(str.contains("cpIndex=20"));
        }

        @Test
        void testNullDescriptor() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "method", null, 10);
            assertEquals(0, info.getParameterSlots());
            assertEquals("V", info.getReturnType());
        }

        @Test
        void testInvalidDescriptor() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "method", "invalid", 10);
            assertEquals(0, info.getParameterSlots());
            assertEquals("V", info.getReturnType());
        }
    }

    @Nested
    class DispatchResultTests {

        @Test
        void testInvokeDynamicEnumExists() {
            OpcodeDispatcher.DispatchResult result = OpcodeDispatcher.DispatchResult.INVOKE_DYNAMIC;
            assertNotNull(result);
            assertEquals("INVOKE_DYNAMIC", result.name());
        }

        @Test
        void testAllDispatchResultsPresent() {
            OpcodeDispatcher.DispatchResult[] values = OpcodeDispatcher.DispatchResult.values();
            assertTrue(values.length >= 13);

            boolean hasInvokeDynamic = false;
            for (OpcodeDispatcher.DispatchResult r : values) {
                if (r == OpcodeDispatcher.DispatchResult.INVOKE_DYNAMIC) {
                    hasInvokeDynamic = true;
                    break;
                }
            }
            assertTrue(hasInvokeDynamic);
        }
    }

    @Nested
    class OpcodeDispatchTests {

        @Test
        void testInvokeDynamicOpcode() {
            InvokeDynamicInstruction instr = mock(InvokeDynamicInstruction.class);
            when(instr.getOpcode()).thenReturn(0xBA);
            when(instr.getLength()).thenReturn(5);
            when(instr.getBootstrapMethodAttrIndex()).thenReturn(0);
            when(instr.getNameAndTypeIndex()).thenReturn(10);
            when(instr.getCpIndex()).thenReturn(5);
            when(instr.resolveMethod()).thenReturn("run()V");

            frame = createMockFrame(instr);

            OpcodeDispatcher.DispatchResult result = dispatcher.dispatch(frame, context);
            assertEquals(OpcodeDispatcher.DispatchResult.INVOKE_DYNAMIC, result);
        }

        @Test
        void testInvokeDynamicSetsContext() {
            InvokeDynamicInstruction instr = mock(InvokeDynamicInstruction.class);
            when(instr.getOpcode()).thenReturn(0xBA);
            when(instr.getLength()).thenReturn(5);
            when(instr.getBootstrapMethodAttrIndex()).thenReturn(0);
            when(instr.getNameAndTypeIndex()).thenReturn(10);
            when(instr.getCpIndex()).thenReturn(5);
            when(instr.resolveMethod()).thenReturn("test()I");

            frame = createMockFrame(instr);

            dispatcher.dispatch(frame, context);

            InvokeDynamicInfo info = context.getPendingInvokeDynamic();
            assertNotNull(info);
        }

        @Test
        void testInvokeDynamicInfoFromDispatch() {
            ConstPool cp = mock(ConstPool.class);
            when(cp.getItem(anyInt())).thenReturn(null);

            InvokeDynamicInstruction instr = mock(InvokeDynamicInstruction.class);
            when(instr.getOpcode()).thenReturn(0xBA);
            when(instr.getLength()).thenReturn(5);
            when(instr.getBootstrapMethodAttrIndex()).thenReturn(3);
            when(instr.getNameAndTypeIndex()).thenReturn(15);
            when(instr.getCpIndex()).thenReturn(10);
            when(instr.resolveMethod()).thenReturn("apply(Ljava/lang/Object;)Ljava/lang/Object;");

            frame = createMockFrame(instr);

            dispatcher.dispatch(frame, context);

            InvokeDynamicInfo info = context.getPendingInvokeDynamic();
            assertNotNull(info);
            assertEquals(3, info.getBootstrapMethodIndex());
            assertEquals("apply", info.getMethodName());
            assertEquals("(Ljava/lang/Object;)Ljava/lang/Object;", info.getDescriptor());
            assertEquals(10, info.getConstantPoolIndex());
        }
    }

    @Nested
    class DispatchContextTests {

        @Test
        void testSetAndGetPendingInvokeDynamic() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(1, "test", "()V", 5);
            context.setPendingInvokeDynamic(info);
            assertSame(info, context.getPendingInvokeDynamic());
        }

        @Test
        void testPendingInvokeDynamicInitiallyNull() {
            assertNull(context.getPendingInvokeDynamic());
        }

        @Test
        void testPendingInvokeDynamicCanBeOverwritten() {
            InvokeDynamicInfo info1 = new InvokeDynamicInfo(1, "test1", "()V", 5);
            InvokeDynamicInfo info2 = new InvokeDynamicInfo(2, "test2", "()I", 10);

            context.setPendingInvokeDynamic(info1);
            context.setPendingInvokeDynamic(info2);

            assertSame(info2, context.getPendingInvokeDynamic());
        }
    }

    @Nested
    class BytecodeEngineInvokeDynamicTests {

        @Test
        void testEngineHandlesInvokeDynamic() {
            SimpleHeapManager heap = new SimpleHeapManager();
            ClassResolver resolver = new ClassResolver(mock(ClassPool.class));
            BytecodeContext ctx = new BytecodeContext.Builder()
                .heapManager(heap)
                .classResolver(resolver)
                .maxInstructions(1000)
                .build();

            BytecodeEngine engine = new BytecodeEngine(ctx);
            assertNotNull(engine);
        }
    }

    private StackFrame createMockFrame(Instruction instruction) {
        StackFrame mockFrame = mock(StackFrame.class);
        when(mockFrame.getStack()).thenReturn(stack);
        when(mockFrame.getLocals()).thenReturn(locals);
        when(mockFrame.getCurrentInstruction()).thenReturn(instruction);
        when(mockFrame.getPC()).thenReturn(0);
        return mockFrame;
    }

    private static class TestDispatchContext implements DispatchContext {
        private MethodInfo pendingInvoke;
        private FieldInfo pendingFieldAccess;
        private String pendingNewClass;
        private int[] pendingArrayDimensions;
        private int branchTarget;
        private InvokeDynamicInfo pendingInvokeDynamic;

        @Override
        public int resolveIntConstant(int index) {
            return 0;
        }

        @Override
        public long resolveLongConstant(int index) {
            return 0L;
        }

        @Override
        public float resolveFloatConstant(int index) {
            return 0.0f;
        }

        @Override
        public double resolveDoubleConstant(int index) {
            return 0.0;
        }

        @Override
        public String resolveStringConstant(int index) {
            return "";
        }

        @Override
        public ObjectInstance resolveClassConstant(int index) {
            return null;
        }

        @Override
        public ArrayInstance getArray(ObjectInstance ref) {
            return null;
        }

        @Override
        public void checkArrayBounds(ArrayInstance array, int index) {
        }

        @Override
        public void checkNullReference(ObjectInstance ref, String operation) {
        }

        @Override
        public FieldInfo resolveField(int cpIndex) {
            return new FieldInfo("Owner", "field", "I", false);
        }

        @Override
        public MethodInfo resolveMethod(int cpIndex) {
            return new MethodInfo("Owner", "method", "()V", false, false);
        }

        @Override
        public boolean isInstanceOf(ObjectInstance obj, String className) {
            return false;
        }

        @Override
        public void checkCast(ObjectInstance obj, String className) {
        }

        @Override
        public MethodInfo getPendingInvoke() {
            return pendingInvoke;
        }

        @Override
        public FieldInfo getPendingFieldAccess() {
            return pendingFieldAccess;
        }

        @Override
        public String getPendingNewClass() {
            return pendingNewClass;
        }

        @Override
        public int[] getPendingArrayDimensions() {
            return pendingArrayDimensions;
        }

        @Override
        public void setPendingInvoke(MethodInfo methodInfo) {
            this.pendingInvoke = methodInfo;
        }

        @Override
        public void setPendingFieldAccess(FieldInfo fieldInfo) {
            this.pendingFieldAccess = fieldInfo;
        }

        @Override
        public void setPendingNewClass(String className) {
            this.pendingNewClass = className;
        }

        @Override
        public void setPendingArrayDimensions(int[] dimensions) {
            this.pendingArrayDimensions = dimensions;
        }

        @Override
        public void setBranchTarget(int target) {
            this.branchTarget = target;
        }

        @Override
        public int getBranchTarget() {
            return branchTarget;
        }

        @Override
        public void setPendingInvokeDynamic(InvokeDynamicInfo info) {
            this.pendingInvokeDynamic = info;
        }

        @Override
        public InvokeDynamicInfo getPendingInvokeDynamic() {
            return pendingInvokeDynamic;
        }

        @Override
        public void setPendingMethodHandle(MethodHandleInfo info) {
        }

        @Override
        public MethodHandleInfo getPendingMethodHandle() {
            return null;
        }

        @Override
        public void setPendingMethodType(MethodTypeInfo info) {
        }

        @Override
        public MethodTypeInfo getPendingMethodType() {
            return null;
        }

        @Override
        public void setPendingConstantDynamic(ConstantDynamicInfo info) {
        }

        @Override
        public ConstantDynamicInfo getPendingConstantDynamic() {
            return null;
        }
    }
}
