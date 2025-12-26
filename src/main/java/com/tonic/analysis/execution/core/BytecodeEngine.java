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
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.DoubleItem;
import com.tonic.parser.constpool.FloatItem;
import com.tonic.parser.constpool.IntegerItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.LongItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.analysis.execution.invoke.InvocationContext;
import com.tonic.analysis.execution.invoke.InvocationHandler;
import com.tonic.analysis.execution.invoke.InvocationResult;
import com.tonic.analysis.execution.invoke.NativeContext;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.invoke.RecursiveHandler;
import com.tonic.analysis.execution.resolve.ResolvedMethod;
import com.tonic.analysis.execution.listener.BytecodeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BytecodeEngine {

    private final BytecodeContext context;
    private final CallStack callStack;
    private final OpcodeDispatcher dispatcher;
    private final List<BytecodeListener> listeners;
    private final InvocationHandler invocationHandler;
    private final Set<String> initializedClasses;

    private volatile boolean interrupted;
    private long instructionCount;
    private ConcreteValue lastReturnValue;
    private ObjectInstance lastException;

    public BytecodeEngine(BytecodeContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context;
        this.callStack = new CallStack(context.getMaxCallDepth());
        this.dispatcher = new OpcodeDispatcher();
        this.listeners = new ArrayList<>();
        this.initializedClasses = ConcurrentHashMap.newKeySet();
        this.interrupted = false;
        this.instructionCount = 0;

        if (context.getMode() == ExecutionMode.RECURSIVE) {
            this.invocationHandler = new RecursiveHandler(
                context.getClassResolver(),
                context.getNativeRegistry()
            );
        } else {
            this.invocationHandler = null;
        }
    }

    public BytecodeResult execute(MethodEntry method, ConcreteValue... args) {
        if ("<clinit>".equals(method.getName())) {
            String className = method.getClassFile().getClassName();
            if (!initializedClasses.contains(className)) {
                initializedClasses.add(className);
            }
        }

        boolean wasInterrupted = interrupted;
        reset();

        if (wasInterrupted) {
            BytecodeResult result = BytecodeResult.interrupted().withStatistics(0, 0);
            notifyExecutionEnd(result);
            return result;
        }

        long startTime = System.nanoTime();
        notifyExecutionStart(method);

        try {
            StackFrame frame = new StackFrame(method, args);
            callStack.push(frame);

            while (!callStack.isEmpty() && !interrupted) {
                if (instructionCount >= context.getMaxInstructions()) {
                    long elapsed = System.nanoTime() - startTime;
                    BytecodeResult result = BytecodeResult.instructionLimit(instructionCount).withStatistics(instructionCount, elapsed);
                    notifyExecutionEnd(result);
                    return result;
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
                    EngineDispatchContext dispatchContext = new EngineDispatchContext(current);
                    DispatchResult result = dispatcher.dispatch(current, dispatchContext);
                    instructionCount++;

                    handleDispatchResult(result, current, dispatchContext);
                    notifyAfterInstruction(current, instr);

                } catch (Exception e) {
                    System.out.println("[BytecodeEngine] CAUGHT EXCEPTION: " + e.getClass().getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    ObjectInstance exceptionObj = wrapException(e);
                    if (!tryHandleException(current, exceptionObj)) {
                        current.completeExceptionally(exceptionObj);
                    }
                }
            }

            if (interrupted) {
                long elapsed = System.nanoTime() - startTime;
                BytecodeResult result = BytecodeResult.interrupted().withStatistics(instructionCount, elapsed);
                notifyExecutionEnd(result);
                return result;
            }

            long elapsed = System.nanoTime() - startTime;
            BytecodeResult result;
            if (lastException != null) {
                List<String> trace = buildStackTrace();
                result = BytecodeResult.exception(lastException, trace).withStatistics(instructionCount, elapsed);
            } else {
                result = BytecodeResult.completed(lastReturnValue).withStatistics(instructionCount, elapsed);
            }
            notifyExecutionEnd(result);
            return result;

        } catch (StackOverflowError e) {
            List<String> trace = buildStackTrace();
            trace.add("Stack overflow at depth: " + callStack.depth());
            long elapsed = System.nanoTime() - startTime;
            BytecodeResult result = BytecodeResult.depthLimit(callStack.depth()).withStatistics(instructionCount, elapsed);
            notifyExecutionEnd(result);
            return result;
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

        EngineDispatchContext dispatchContext = new EngineDispatchContext(current);
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
        lastReturnValue = ConcreteValue.nullRef();
        lastException = null;
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

    public ConcreteValue getLastReturnValue() {
        return lastReturnValue;
    }

    public boolean ensureClassInitialized(String className) {
        if (initializedClasses.contains(className)) {
            return true;
        }

        if (isJdkClass(className)) {
            initializedClasses.add(className);
            return true;
        }

        initializedClasses.add(className);

        try {
            ResolvedMethod clinit = context.getClassResolver().resolveMethod(
                className, "<clinit>", "()V");
            if (clinit != null && clinit.getMethod() != null) {
                BytecodeResult result = execute(clinit.getMethod());
                if (result.getStatus() != BytecodeResult.Status.COMPLETED) {
                    return false;
                }
            }
        } catch (Exception e) {
        }

        return true;
    }

    private boolean isJdkClass(String className) {
        return className.startsWith("java/") ||
               className.startsWith("javax/") ||
               className.startsWith("sun/") ||
               className.startsWith("com/sun/") ||
               className.startsWith("jdk/");
    }

    public boolean isClassInitialized(String className) {
        return initializedClasses.contains(className);
    }

    public void markClassInitialized(String className) {
        initializedClasses.add(className);
    }

    public void resetClassInitialization() {
        initializedClasses.clear();
    }

    private void handleFrameCompletion() {
        StackFrame completed = callStack.pop();

        if (callStack.isEmpty()) {
            if (completed.getException() != null) {
                lastException = completed.getException();
            } else {
                lastReturnValue = completed.getReturnValue();
                if (lastReturnValue == null) {
                    lastReturnValue = ConcreteValue.nullRef();
                }
            }
            return;
        }

        StackFrame caller = callStack.peek();

        if (completed.getException() != null) {
            ObjectInstance exception = completed.getException();
            if (!tryHandleException(caller, exception)) {
                caller.completeExceptionally(exception);
            }
        } else {
            ConcreteValue returnValue = completed.getReturnValue();
            if (returnValue != null && !returnValue.isNull()) {
                caller.getStack().push(returnValue);
            }
            caller.advancePC(caller.getCurrentInstruction().getLength());
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
                    ConcreteValue.nullRef() : frame.getStack().pop();
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

        if (context.getMode() == ExecutionMode.RECURSIVE && invocationHandler != null) {
            ConcreteValue[] args = extractArguments(frame, descriptor);
            ObjectInstance receiver = null;
            if (!methodInfo.isStatic()) {
                ConcreteValue receiverVal = frame.getStack().pop();
                if (!receiverVal.isNull()) {
                    receiver = receiverVal.asReference();
                }
            }

            MethodEntry targetMethod = resolveMethod(methodInfo);
            if (targetMethod == null) {
                if (tryNativeInvoke(frame, methodInfo, receiver, args)) {
                    return;
                }
                stubInvoke(frame, descriptor, methodInfo.isStatic());
                return;
            }

            InvocationResult result = invocationHandler.invoke(
                targetMethod, receiver, args, createInvocationContext());

            if (result.isPushFrame()) {
                callStack.push(result.getNewFrame());
            } else if (result.isNativeHandled()) {
                ConcreteValue returnVal = result.getReturnValue();
                String returnType = getReturnType(descriptor);
                if (returnVal != null && !"V".equals(returnType)) {
                    frame.getStack().push(returnVal);
                }
                frame.advancePC(frame.getCurrentInstruction().getLength());
            } else if (result.isException()) {
                frame.completeExceptionally(result.getException());
            } else {
                frame.advancePC(frame.getCurrentInstruction().getLength());
            }
        } else {
            stubInvoke(frame, descriptor, methodInfo.isStatic());
        }
    }

    private boolean tryNativeInvoke(StackFrame frame, MethodInfo methodInfo,
                                    ObjectInstance receiver, ConcreteValue[] args) {
        NativeRegistry nativeRegistry = getNativeRegistry();
        if (nativeRegistry == null) {
            return false;
        }

        String owner = methodInfo.getOwnerClass();
        String name = methodInfo.getMethodName();
        String desc = methodInfo.getDescriptor();

        boolean hasHandler = nativeRegistry.hasHandler(owner, name, desc);
        if (!hasHandler) {
            return false;
        }

        try {
            NativeContext nativeContext = new NativeContext() {
                @Override
                public com.tonic.analysis.execution.heap.HeapManager getHeapManager() {
                    return context.getHeapManager();
                }
                @Override
                public com.tonic.analysis.execution.resolve.ClassResolver getClassResolver() {
                    return context.getClassResolver();
                }
                @Override
                public ObjectInstance createString(String value) {
                    return context.getHeapManager().internString(value);
                }
                @Override
                public ObjectInstance createException(String className, String message) {
                    ObjectInstance exception = context.getHeapManager().newObject(className);
                    if (message != null) {
                        ObjectInstance messageStr = context.getHeapManager().internString(message);
                        exception.setField(className, "detailMessage", "Ljava/lang/String;", messageStr);
                    }
                    return exception;
                }
            };

            ConcreteValue result = nativeRegistry.execute(owner, name, desc, receiver, args, nativeContext);
            String returnType = getReturnType(desc);
            if (!"V".equals(returnType) && result != null) {
                frame.getStack().push(result);
            }
            frame.advancePC(frame.getCurrentInstruction().getLength());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private NativeRegistry getNativeRegistry() {
        return context.getNativeRegistry();
    }

    private void stubInvoke(StackFrame frame, String descriptor, boolean isStatic) {
        int paramSlots = getParameterSlots(descriptor);
        for (int i = 0; i < paramSlots; i++) {
            frame.getStack().pop();
        }

        if (!isStatic) {
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

    private ConcreteValue[] extractArguments(StackFrame frame, String descriptor) {
        int paramCount = getParameterCount(descriptor);
        ConcreteValue[] args = new ConcreteValue[paramCount];
        for (int i = paramCount - 1; i >= 0; i--) {
            args[i] = frame.getStack().pop();
        }
        return args;
    }

    private int getParameterCount(String descriptor) {
        if (descriptor == null || !descriptor.startsWith("(")) {
            return 0;
        }

        int count = 0;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            switch (c) {
                case 'J':
                case 'D':
                case 'I':
                case 'F':
                case 'B':
                case 'C':
                case 'S':
                case 'Z':
                    count++;
                    i++;
                    break;
                case 'L':
                    count++;
                    while (i < descriptor.length() && descriptor.charAt(i) != ';') {
                        i++;
                    }
                    i++;
                    break;
                case '[':
                    count++;
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
                    count++;
                    i++;
                    break;
            }
        }
        return count;
    }

    private MethodEntry resolveMethod(MethodInfo methodInfo) {
        try {
            ResolvedMethod resolved = context.getClassResolver().resolveMethod(
                methodInfo.getOwnerClass(),
                methodInfo.getMethodName(),
                methodInfo.getDescriptor()
            );
            return resolved != null ? resolved.getMethod() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private InvocationContext createInvocationContext() {
        return new InvocationContext() {
            @Override
            public CallStack getCallStack() {
                return callStack;
            }

            @Override
            public com.tonic.analysis.execution.heap.HeapManager getHeapManager() {
                return context.getHeapManager();
            }

            @Override
            public ClassResolver getClassResolver() {
                return context.getClassResolver();
            }
        };
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
        String owner = fieldInfo.getOwnerClass();
        String name = fieldInfo.getFieldName();

        if (fieldInfo.isStatic()) {
            ensureClassInitialized(owner);
            Object value = context.getHeapManager().getStaticField(owner, name, descriptor);
            pushFieldValue(frame, descriptor, value);
        } else {
            ConcreteValue objRef = frame.getStack().pop();
            if (objRef.isNull()) {
                pushDefaultValue(frame, descriptor);
            } else {
                ObjectInstance obj = objRef.asReference();
                Object value = obj.getField(owner, name, descriptor);
                pushFieldValue(frame, descriptor, value);
            }
        }

        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void handleFieldPut(StackFrame frame, EngineDispatchContext ctx) {
        FieldInfo fieldInfo = ctx.getPendingFieldAccess();
        String descriptor = fieldInfo.getDescriptor();
        String owner = fieldInfo.getOwnerClass();
        String name = fieldInfo.getFieldName();

        ConcreteValue value = frame.getStack().pop();

        if (fieldInfo.isStatic()) {
            ensureClassInitialized(owner);
            context.getHeapManager().putStaticField(owner, name, descriptor, convertToStorable(value, descriptor));
        } else {
            ConcreteValue objRef = frame.getStack().pop();
            if (!objRef.isNull()) {
                ObjectInstance obj = objRef.asReference();
                obj.setField(owner, name, descriptor, convertToStorable(value, descriptor));
            }
        }

        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void pushFieldValue(StackFrame frame, String descriptor, Object value) {
        if (value == null) {
            pushDefaultValue(frame, descriptor);
            return;
        }
        char typeChar = descriptor.charAt(0);
        switch (typeChar) {
            case 'J':
                frame.getStack().pushLong(value instanceof Long ? (Long) value : ((Number) value).longValue());
                break;
            case 'D':
                frame.getStack().pushDouble(value instanceof Double ? (Double) value : ((Number) value).doubleValue());
                break;
            case 'F':
                frame.getStack().pushFloat(value instanceof Float ? (Float) value : ((Number) value).floatValue());
                break;
            case 'I':
            case 'Z':
            case 'B':
            case 'C':
            case 'S':
                if (value instanceof Boolean) {
                    frame.getStack().pushInt((Boolean) value ? 1 : 0);
                } else if (value instanceof Character) {
                    frame.getStack().pushInt((Character) value);
                } else {
                    frame.getStack().pushInt(((Number) value).intValue());
                }
                break;
            case 'L':
            case '[':
                if (value instanceof ObjectInstance) {
                    frame.getStack().pushReference((ObjectInstance) value);
                } else {
                    frame.getStack().pushReference(null);
                }
                break;
            default:
                pushDefaultValue(frame, descriptor);
                break;
        }
    }

    private void pushDefaultValue(StackFrame frame, String descriptor) {
        char typeChar = descriptor.charAt(0);
        switch (typeChar) {
            case 'J':
                frame.getStack().pushLong(0L);
                break;
            case 'D':
                frame.getStack().pushDouble(0.0);
                break;
            case 'F':
                frame.getStack().pushFloat(0.0f);
                break;
            case 'L':
            case '[':
                frame.getStack().pushReference(null);
                break;
            default:
                frame.getStack().pushInt(0);
                break;
        }
    }

    private Object convertToStorable(ConcreteValue value, String descriptor) {
        if (value.isNull()) {
            return null;
        }
        char typeChar = descriptor.charAt(0);
        switch (typeChar) {
            case 'J':
                return value.asLong();
            case 'D':
                return value.asDouble();
            case 'F':
                return value.asFloat();
            case 'I':
                return value.asInt();
            case 'Z':
                return value.asInt() != 0;
            case 'B':
                return (byte) value.asInt();
            case 'C':
                return (char) value.asInt();
            case 'S':
                return (short) value.asInt();
            case 'L':
            case '[':
                return value.asReference();
            default:
                return value.asInt();
        }
    }

    private void handleNewObject(StackFrame frame, EngineDispatchContext ctx) {
        String className = ctx.getPendingNewClass();
        ObjectInstance obj = context.getHeapManager().newObject(className);
        notifyObjectAllocation(obj);
        frame.getStack().pushReference(obj);
        frame.advancePC(frame.getCurrentInstruction().getLength());
    }

    private void handleNewArray(StackFrame frame, EngineDispatchContext ctx) {
        String className = ctx.getPendingNewClass();
        int[] dimensions = ctx.getPendingArrayDimensions();

        if (dimensions.length == 1) {
            ArrayInstance array = context.getHeapManager().newArray(className, dimensions[0]);
            notifyArrayAllocation(array);
            frame.getStack().pushReference(array);
        } else {
            ArrayInstance array = context.getHeapManager().newMultiArray(className, dimensions);
            notifyArrayAllocation(array);
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

        if (!tryHandleException(frame, exception)) {
            frame.completeExceptionally(exception);
        }
    }

    private boolean tryHandleException(StackFrame frame, ObjectInstance exception) {
        ExceptionTableEntry handler = findExceptionHandler(frame, exception);
        if (handler != null) {
            frame.getStack().clear();
            frame.getStack().pushReference(exception);
            frame.setPC(handler.getHandlerPc());
            return true;
        }
        return false;
    }

    private ExceptionTableEntry findExceptionHandler(StackFrame frame, ObjectInstance exception) {
        CodeAttribute codeAttr = frame.getMethod().getCodeAttribute();
        if (codeAttr == null) {
            return null;
        }

        int pc = frame.getPC();
        String exceptionType = exception.getClassName();

        for (ExceptionTableEntry entry : codeAttr.getExceptionTable()) {
            if (pc >= entry.getStartPc() && pc < entry.getEndPc()) {
                if (entry.getCatchType() == 0) {
                    return entry;
                }
                String catchTypeName = resolveCatchType(frame, entry.getCatchType());
                if (catchTypeName != null && isExceptionAssignable(exceptionType, catchTypeName)) {
                    return entry;
                }
            }
        }
        return null;
    }

    private String resolveCatchType(StackFrame frame, int catchTypeIndex) {
        if (catchTypeIndex == 0) {
            return null;
        }
        ConstPool constPool = frame.getMethod().getClassFile().getConstPool();
        Item<?> item = constPool.getItem(catchTypeIndex);
        if (item instanceof ClassRefItem) {
            return ((ClassRefItem) item).getClassName();
        }
        return null;
    }

    private boolean isExceptionAssignable(String exceptionType, String catchType) {
        if (exceptionType == null || catchType == null) {
            return false;
        }
        if (exceptionType.equals(catchType)) {
            return true;
        }
        ClassResolver resolver = context.getClassResolver();
        if (resolver != null) {
            return resolver.isAssignableFrom(catchType, exceptionType);
        }
        return false;
    }

    private ObjectInstance wrapException(Exception e) {
        try {
            String exceptionClass = mapJavaExceptionToInternalName(e.getClass());
            return context.getHeapManager().newObject(exceptionClass);
        } catch (Exception ex) {
            return context.getHeapManager().newObject("java/lang/Exception");
        }
    }

    private String mapJavaExceptionToInternalName(Class<?> exceptionClass) {
        String name = exceptionClass.getName().replace('.', '/');
        if (name.startsWith("java/lang/") || name.startsWith("java/io/") ||
            name.startsWith("java/util/") || name.startsWith("java/net/")) {
            return name;
        }
        return "java/lang/Exception";
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

    private void notifyObjectAllocation(ObjectInstance instance) {
        for (BytecodeListener listener : listeners) {
            listener.onObjectAllocation(instance);
        }
    }

    private void notifyArrayAllocation(ArrayInstance array) {
        for (BytecodeListener listener : listeners) {
            listener.onArrayAllocation(array);
        }
    }

    private void notifyExecutionStart(MethodEntry method) {
        for (BytecodeListener listener : listeners) {
            listener.onExecutionStart(method);
        }
    }

    private void notifyExecutionEnd(BytecodeResult result) {
        com.tonic.analysis.execution.result.BytecodeResult listenerResult;
        if (result.getStatus() == BytecodeResult.Status.COMPLETED) {
            listenerResult = com.tonic.analysis.execution.result.BytecodeResult.success(result.getReturnValue());
        } else if (result.getStatus() == BytecodeResult.Status.EXCEPTION) {
            ObjectInstance exObj = result.getException();
            String exMsg = exObj != null ? exObj.getClassName() : "Unknown";
            listenerResult = com.tonic.analysis.execution.result.BytecodeResult.failure(new RuntimeException(exMsg));
        } else {
            listenerResult = com.tonic.analysis.execution.result.BytecodeResult.incomplete();
        }
        for (BytecodeListener listener : listeners) {
            listener.onExecutionEnd(listenerResult);
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
        private final StackFrame frame;
        private MethodInfo pendingInvoke;
        private FieldInfo pendingFieldAccess;
        private String pendingNewClass;
        private int[] pendingArrayDimensions;
        private int branchTarget;
        private InvokeDynamicInfo pendingInvokeDynamic;
        private MethodHandleInfo pendingMethodHandle;
        private MethodTypeInfo pendingMethodType;
        private ConstantDynamicInfo pendingConstantDynamic;

        EngineDispatchContext(StackFrame frame) {
            this.frame = frame;
        }

        private ConstPool getConstPool() {
            return frame.getMethod().getClassFile().getConstPool();
        }

        @Override
        public int resolveIntConstant(int index) {
            Item<?> item = getConstPool().getItem(index);
            if (item instanceof IntegerItem) {
                return ((IntegerItem) item).getValue();
            }
            return 0;
        }

        @Override
        public long resolveLongConstant(int index) {
            Item<?> item = getConstPool().getItem(index);
            if (item instanceof LongItem) {
                return ((LongItem) item).getValue();
            }
            return 0L;
        }

        @Override
        public float resolveFloatConstant(int index) {
            Item<?> item = getConstPool().getItem(index);
            if (item instanceof FloatItem) {
                return ((FloatItem) item).getValue();
            }
            return 0.0f;
        }

        @Override
        public double resolveDoubleConstant(int index) {
            Item<?> item = getConstPool().getItem(index);
            if (item instanceof DoubleItem) {
                return ((DoubleItem) item).getValue();
            }
            return 0.0;
        }

        @Override
        public String resolveStringConstant(int index) {
            Item<?> item = getConstPool().getItem(index);
            if (item instanceof StringRefItem) {
                int stringIndex = ((StringRefItem) item).getValue();
                Item<?> utf8Item = getConstPool().getItem(stringIndex);
                if (utf8Item instanceof Utf8Item) {
                    return ((Utf8Item) utf8Item).getValue();
                }
            } else if (item instanceof Utf8Item) {
                return ((Utf8Item) item).getValue();
            }
            return "";
        }

        @Override
        public ObjectInstance resolveStringObject(int index) {
            String value = resolveStringConstant(index);
            return context.getHeapManager().internString(value);
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
}
