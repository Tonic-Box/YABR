package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.dispatch.ConstantDynamicInfo;
import com.tonic.analysis.execution.dispatch.DispatchContext;
import com.tonic.analysis.execution.dispatch.FieldInfo;
import com.tonic.analysis.execution.dispatch.InvokeDynamicInfo;
import com.tonic.analysis.execution.dispatch.MethodHandleInfo;
import com.tonic.analysis.execution.dispatch.MethodInfo;
import com.tonic.analysis.execution.dispatch.MethodTypeInfo;
import com.tonic.analysis.execution.dispatch.OpcodeDispatcher;
import com.tonic.analysis.execution.dispatch.OpcodeDispatcher.DispatchResult;
import com.tonic.analysis.execution.frame.CallStack;
import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.HeapException;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;

import java.util.ArrayList;
import java.util.List;

public final class BytecodeEngine {

    private final BytecodeContext context;
    private final CallStack callStack;
    private final OpcodeDispatcher dispatcher;
    private final List<BytecodeListener> listeners;

    private volatile boolean interrupted;
    private long instructionCount;

    public BytecodeEngine(BytecodeContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context;
        this.callStack = new CallStack(context.getMaxCallDepth());
        this.dispatcher = new OpcodeDispatcher();
        this.listeners = new ArrayList<>();
        this.interrupted = false;
        this.instructionCount = 0;
    }

    public BytecodeResult execute(MethodEntry method, ConcreteValue... args) {
        boolean wasInterrupted = interrupted;
        reset();

        if (wasInterrupted) {
            return BytecodeResult.interrupted().withStatistics(0, 0);
        }

        long startTime = System.nanoTime();

        try {
            StackFrame frame = new StackFrame(method, args);
            callStack.push(frame);

            while (!callStack.isEmpty() && !interrupted) {
                if (instructionCount >= context.getMaxInstructions()) {
                    long elapsed = System.nanoTime() - startTime;
                    return BytecodeResult.instructionLimit(instructionCount).withStatistics(instructionCount, elapsed);
                }

                StackFrame current = callStack.peek();

                if (current.isCompleted()) {
                    handleFrameCompletion();
                    continue;
                }

                Instruction instr = current.getCurrentInstruction();
                if (instr == null) {
                    current.complete(ConcreteValue.nullRef());
                    continue;
                }

                notifyBeforeInstruction(current, instr);

                try {
                    EngineDispatchContext dispatchContext = new EngineDispatchContext();
                    DispatchResult result = dispatcher.dispatch(current, dispatchContext);
                    instructionCount++;

                    handleDispatchResult(result, current, dispatchContext);
                    notifyAfterInstruction(current, instr);

                } catch (Exception e) {
                    ObjectInstance exceptionObj = wrapException(e);
                    current.completeExceptionally(exceptionObj);

                    List<String> trace = buildStackTrace();
                    long elapsed = System.nanoTime() - startTime;
                    return BytecodeResult.exception(exceptionObj, trace).withStatistics(instructionCount, elapsed);
                }
            }

            if (interrupted) {
                long elapsed = System.nanoTime() - startTime;
                return BytecodeResult.interrupted().withStatistics(instructionCount, elapsed);
            }

            ConcreteValue result = callStack.isEmpty() ? ConcreteValue.nullRef() : ConcreteValue.nullRef();
            long elapsed = System.nanoTime() - startTime;
            return BytecodeResult.completed(result).withStatistics(instructionCount, elapsed);

        } catch (StackOverflowError e) {
            List<String> trace = buildStackTrace();
            trace.add("Stack overflow at depth: " + callStack.depth());
            long elapsed = System.nanoTime() - startTime;
            return BytecodeResult.depthLimit(callStack.depth()).withStatistics(instructionCount, elapsed);
        }
    }

    public boolean step() {
        if (callStack.isEmpty() || interrupted) {
            return false;
        }

        StackFrame current = callStack.peek();
        if (current.isCompleted()) {
            handleFrameCompletion();
            return !callStack.isEmpty();
        }

        Instruction instr = current.getCurrentInstruction();
        if (instr == null) {
            current.complete(ConcreteValue.nullRef());
            return !callStack.isEmpty();
        }

        notifyBeforeInstruction(current, instr);

        EngineDispatchContext dispatchContext = new EngineDispatchContext();
        DispatchResult result = dispatcher.dispatch(current, dispatchContext);
        instructionCount++;

        handleDispatchResult(result, current, dispatchContext);
        notifyAfterInstruction(current, instr);

        return !callStack.isEmpty();
    }

    public BytecodeEngine addListener(BytecodeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        return this;
    }

    public void interrupt() {
        this.interrupted = true;
    }

    public void reset() {
        callStack.clear();
        instructionCount = 0;
        interrupted = false;
    }

    public StackFrame getCurrentFrame() {
        return callStack.isEmpty() ? null : callStack.peek();
    }

    public CallStack getCallStack() {
        return callStack;
    }

    public long getInstructionCount() {
        return instructionCount;
    }

    private void handleFrameCompletion() {
        StackFrame completed = callStack.pop();

        if (callStack.isEmpty()) {
            return;
        }

        StackFrame caller = callStack.peek();

        if (completed.getException() != null) {
            caller.completeExceptionally(completed.getException());
        } else {
            ConcreteValue returnValue = completed.getReturnValue();
            if (returnValue != null && !returnValue.isNull()) {
                caller.getStack().push(returnValue);
            }
        }
    }

    private void handleDispatchResult(DispatchResult result, StackFrame frame, EngineDispatchContext ctx) {
        switch (result) {
            case CONTINUE:
                break;

            case BRANCH:
                int target = ctx.getBranchTarget();
                frame.setPC(target);
                break;

            case RETURN:
                ConcreteValue returnValue = frame.getStack().isEmpty() ?
                    ConcreteValue.nullRef() : frame.getStack().peek();
                frame.complete(returnValue);
                break;

            case INVOKE:
                handleInvoke(frame, ctx);
                break;

            case INVOKE_DYNAMIC:
                handleInvokeDynamic(frame, ctx);
                break;

            case FIELD_GET:
                handleFieldGet(frame, ctx);
                break;

            case FIELD_PUT:
                handleFieldPut(frame, ctx);
                break;

            case NEW_OBJECT:
                handleNewObject(frame, ctx);
                break;

            case NEW_ARRAY:
                handleNewArray(frame, ctx);
                break;

            case ATHROW:
                handleAthrow(frame);
                break;

            case CHECKCAST:
            case INSTANCEOF:
                break;

            case CONSTANT_DYNAMIC:
                handleConstantDynamic(frame, ctx);
                break;

            case METHOD_HANDLE:
                handleMethodHandle(frame, ctx);
                break;

            case METHOD_TYPE:
                handleMethodType(frame, ctx);
                break;

            default:
                throw new IllegalStateException("Unhandled dispatch result: " + result);
        }
    }

    private void handleConstantDynamic(StackFrame frame, EngineDispatchContext ctx) {
        ConstantDynamicInfo info = ctx.getPendingConstantDynamic();
        String returnType = info.getDescriptor();

        if ("J".equals(returnType)) {
            frame.getStack().pushLong(0L);
        } else if ("D".equals(returnType)) {
            frame.getStack().pushDouble(0.0);
        } else if ("F".equals(returnType)) {
            frame.getStack().pushFloat(0.0f);
        } else if ("I".equals(returnType) || "Z".equals(returnType) ||
                   "B".equals(returnType) || "C".equals(returnType) || "S".equals(returnType)) {
            frame.getStack().pushInt(0);
        } else {
            ObjectInstance obj = context.getHeapManager().newObject("java/lang/Object");
            frame.getStack().pushReference(obj);
        }

        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void handleMethodHandle(StackFrame frame, EngineDispatchContext ctx) {
        MethodHandleInfo info = ctx.getPendingMethodHandle();
        ObjectInstance mh = context.getHeapManager().newObject("java/lang/invoke/MethodHandle");
        frame.getStack().pushReference(mh);
        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void handleMethodType(StackFrame frame, EngineDispatchContext ctx) {
        MethodTypeInfo info = ctx.getPendingMethodType();
        ObjectInstance mt = context.getHeapManager().newObject("java/lang/invoke/MethodType");
        frame.getStack().pushReference(mt);
        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void handleInvoke(StackFrame frame, EngineDispatchContext ctx) {
        MethodInfo methodInfo = ctx.getPendingInvoke();
        String descriptor = methodInfo.getDescriptor();

        int paramSlots = getParameterSlots(descriptor);
        for (int i = 0; i < paramSlots; i++) {
            frame.getStack().pop();
        }

        if (!methodInfo.isStatic()) {
            frame.getStack().pop();
        }

        String returnType = getReturnType(descriptor);
        if (!"V".equals(returnType)) {
            if (returnType.startsWith("L") || returnType.startsWith("[")) {
                ObjectInstance result = context.getHeapManager().newObject("java/lang/Object");
                frame.getStack().pushReference(result);
            } else if ("J".equals(returnType)) {
                frame.getStack().pushLong(0L);
            } else if ("D".equals(returnType)) {
                frame.getStack().pushDouble(0.0);
            } else if ("F".equals(returnType)) {
                frame.getStack().pushFloat(0.0f);
            } else {
                frame.getStack().pushInt(0);
            }
        }

        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void handleInvokeDynamic(StackFrame frame, EngineDispatchContext ctx) {
        InvokeDynamicInfo info = ctx.getPendingInvokeDynamic();
        int paramSlots = info.getParameterSlots();

        for (int i = 0; i < paramSlots; i++) {
            frame.getStack().pop();
        }

        String returnType = info.getReturnType();
        if (!"V".equals(returnType)) {
            if (info.isStringConcat()) {
                ObjectInstance strResult = context.getHeapManager().internString("<concat>");
                frame.getStack().pushReference(strResult);
            } else if (returnType.startsWith("L") || returnType.startsWith("[")) {
                ObjectInstance lambdaProxy = context.getHeapManager().newObject("java/lang/Object");
                frame.getStack().pushReference(lambdaProxy);
            } else if ("J".equals(returnType)) {
                frame.getStack().pushLong(0L);
            } else if ("D".equals(returnType)) {
                frame.getStack().pushDouble(0.0);
            } else if ("F".equals(returnType)) {
                frame.getStack().pushFloat(0.0f);
            } else {
                frame.getStack().pushInt(0);
            }
        }

        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void handleFieldGet(StackFrame frame, EngineDispatchContext ctx) {
        FieldInfo fieldInfo = ctx.getPendingFieldAccess();
        String descriptor = fieldInfo.getDescriptor();

        if (!fieldInfo.isStatic()) {
            frame.getStack().pop();
        }

        if ("J".equals(descriptor)) {
            frame.getStack().pushLong(0L);
        } else if ("D".equals(descriptor)) {
            frame.getStack().pushDouble(0.0);
        } else if ("F".equals(descriptor)) {
            frame.getStack().pushFloat(0.0f);
        } else if (descriptor.startsWith("L") || descriptor.startsWith("[")) {
            ObjectInstance result = context.getHeapManager().newObject("java/lang/Object");
            frame.getStack().pushReference(result);
        } else {
            frame.getStack().pushInt(0);
        }

        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void handleFieldPut(StackFrame frame, EngineDispatchContext ctx) {
        FieldInfo fieldInfo = ctx.getPendingFieldAccess();
        String descriptor = fieldInfo.getDescriptor();

        if ("J".equals(descriptor) || "D".equals(descriptor)) {
            frame.getStack().pop();
            frame.getStack().pop();
        } else {
            frame.getStack().pop();
        }

        if (!fieldInfo.isStatic()) {
            frame.getStack().pop();
        }

        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void handleNewObject(StackFrame frame, EngineDispatchContext ctx) {
        String className = ctx.getPendingNewClass();
        ObjectInstance obj = context.getHeapManager().newObject(className);
        frame.getStack().pushReference(obj);
        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void handleNewArray(StackFrame frame, EngineDispatchContext ctx) {
        String className = ctx.getPendingNewClass();
        int[] dimensions = ctx.getPendingArrayDimensions();

        if (dimensions.length == 1) {
            ArrayInstance array = context.getHeapManager().newArray(className, dimensions[0]);
            frame.getStack().pushReference(array);
        } else {
            ArrayInstance array = context.getHeapManager().newMultiArray(className, dimensions);
            frame.getStack().pushReference(array);
        }

        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void handleAthrow(StackFrame frame) {
        ConcreteValue exceptionRef = frame.getStack().pop();
        if (exceptionRef.isNull()) {
            throw new NullPointerException("Cannot throw null exception");
        }
        ObjectInstance exception = exceptionRef.asReference();
        frame.completeExceptionally(exception);
    }

    private ObjectInstance wrapException(Exception e) {
        try {
            return context.getHeapManager().newObject("java/lang/Exception");
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> buildStackTrace() {
        List<String> trace = new ArrayList<>();
        for (StackFrame frame : callStack.topToBottom()) {
            StringBuilder sb = new StringBuilder();
            sb.append(frame.getMethodSignature());
            sb.append(" (pc=").append(frame.getPC());

            int line = frame.getLineNumber();
            if (line >= 0) {
                sb.append(", line=").append(line);
            }
            sb.append(")");

            trace.add(sb.toString());
        }
        return trace;
    }

    private void notifyBeforeInstruction(StackFrame frame, Instruction instr) {
        for (BytecodeListener listener : listeners) {
            listener.beforeInstruction(frame, instr);
        }
    }

    private void notifyAfterInstruction(StackFrame frame, Instruction instr) {
        for (BytecodeListener listener : listeners) {
            listener.afterInstruction(frame, instr);
        }
    }

    private int getParameterSlots(String descriptor) {
        if (descriptor == null || !descriptor.startsWith("(")) {
            return 0;
        }

        int slots = 0;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            switch (c) {
                case 'J':
                case 'D':
                    slots += 2;
                    i++;
                    break;
                case 'L':
                    slots++;
                    while (i < descriptor.length() && descriptor.charAt(i) != ';') {
                        i++;
                    }
                    i++;
                    break;
                case '[':
                    slots++;
                    while (i < descriptor.length() && descriptor.charAt(i) == '[') {
                        i++;
                    }
                    if (i < descriptor.length() && descriptor.charAt(i) == 'L') {
                        while (i < descriptor.length() && descriptor.charAt(i) != ';') {
                            i++;
                        }
                    }
                    i++;
                    break;
                default:
                    slots++;
                    i++;
                    break;
            }
        }
        return slots;
    }

    private String getReturnType(String descriptor) {
        if (descriptor == null) {
            return "V";
        }
        int parenIndex = descriptor.indexOf(')');
        if (parenIndex >= 0 && parenIndex < descriptor.length() - 1) {
            return descriptor.substring(parenIndex + 1);
        }
        return "V";
    }

    private class EngineDispatchContext implements DispatchContext {
        private MethodInfo pendingInvoke;
        private FieldInfo pendingFieldAccess;
        private String pendingNewClass;
        private int[] pendingArrayDimensions;
        private int branchTarget;
        private InvokeDynamicInfo pendingInvokeDynamic;
        private MethodHandleInfo pendingMethodHandle;
        private MethodTypeInfo pendingMethodType;
        private ConstantDynamicInfo pendingConstantDynamic;

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
            return context.getHeapManager().newObject("java/lang/Class");
        }

        @Override
        public ArrayInstance getArray(ObjectInstance ref) {
            if (ref instanceof ArrayInstance) {
                return (ArrayInstance) ref;
            }
            throw new IllegalArgumentException("Object is not an array: " + ref);
        }

        @Override
        public void checkArrayBounds(ArrayInstance array, int index) {
            if (index < 0 || index >= array.getLength()) {
                throw new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + array.getLength());
            }
        }

        @Override
        public void checkNullReference(ObjectInstance ref, String operation) {
            if (ref == null) {
                throw new NullPointerException("Null reference in " + operation);
            }
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
            return obj.isInstanceOf(className);
        }

        @Override
        public void checkCast(ObjectInstance obj, String className) {
            if (!isInstanceOf(obj, className)) {
                throw new ClassCastException("Cannot cast " + obj.getClassName() + " to " + className);
            }
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
            this.pendingMethodHandle = info;
        }

        @Override
        public MethodHandleInfo getPendingMethodHandle() {
            return pendingMethodHandle;
        }

        @Override
        public void setPendingMethodType(MethodTypeInfo info) {
            this.pendingMethodType = info;
        }

        @Override
        public MethodTypeInfo getPendingMethodType() {
            return pendingMethodType;
        }

        @Override
        public void setPendingConstantDynamic(ConstantDynamicInfo info) {
            this.pendingConstantDynamic = info;
        }

        @Override
        public ConstantDynamicInfo getPendingConstantDynamic() {
            return pendingConstantDynamic;
        }
    }

    public interface BytecodeListener {
        void beforeInstruction(StackFrame frame, Instruction instruction);
        void afterInstruction(StackFrame frame, Instruction instruction);
    }
}
