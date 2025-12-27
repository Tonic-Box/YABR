package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.resolve.ResolutionException;
import com.tonic.analysis.execution.resolve.ResolvedMethod;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.DescriptorUtil;

import java.util.List;

public final class RecursiveHandler implements InvocationHandler {

    private final ClassResolver resolver;
    private final NativeRegistry nativeRegistry;

    public RecursiveHandler(ClassResolver resolver) {
        this(resolver, new NativeRegistry());
    }

    public RecursiveHandler(ClassResolver resolver, NativeRegistry nativeRegistry) {
        if (resolver == null) {
            throw new IllegalArgumentException("ClassResolver cannot be null");
        }
        if (nativeRegistry == null) {
            throw new IllegalArgumentException("NativeRegistry cannot be null");
        }
        this.resolver = resolver;
        this.nativeRegistry = nativeRegistry;
    }

    @Override
    public InvocationResult invoke(MethodEntry method, ObjectInstance receiver,
                                    ConcreteValue[] args, InvocationContext context) {
        if (nativeRegistry.hasHandler(method)) {
            return handleNative(method, receiver, args, context);
        }

        MethodEntry targetMethod = resolveTarget(method, receiver);

        if (nativeRegistry.hasHandler(targetMethod)) {
            return handleNative(targetMethod, receiver, args, context);
        }

        if (isNative(targetMethod)) {
            throw new UnsupportedOperationException(
                "No native handler for native method: " + targetMethod.getOwnerName() + "." +
                targetMethod.getName() + targetMethod.getDesc()
            );
        }

        if (targetMethod.getCodeAttribute() == null) {
            return stubResult(targetMethod.getDesc(), context.getHeapManager());
        }

        ConcreteValue[] frameArgs = buildFrameArgs(targetMethod, receiver, args);

        StackFrame frame = new StackFrame(targetMethod, frameArgs);
        return InvocationResult.pushFrame(frame);
    }

    private MethodEntry resolveTarget(MethodEntry method, ObjectInstance receiver) {
        if (receiver == null || isStaticOrSpecial(method)) {
            return method;
        }

        try {
            String receiverType = receiver.getClassName();
            ResolvedMethod resolved = resolver.resolveVirtualMethod(
                receiverType,
                method.getName(),
                method.getDesc(),
                receiver
            );
            return resolved.getMethod();
        } catch (ResolutionException e) {
            return method;
        }
    }

    private boolean isStaticOrSpecial(MethodEntry method) {
        int access = method.getAccess();
        boolean isStatic = (access & 0x0008) != 0;
        boolean isPrivate = (access & 0x0002) != 0;
        return isStatic || isPrivate || method.getName().equals("<init>");
    }

    private boolean isNative(MethodEntry method) {
        return (method.getAccess() & 0x0100) != 0;
    }

    private InvocationResult handleNative(MethodEntry method, ObjectInstance receiver,
                                         ConcreteValue[] args, InvocationContext context) {
        try {
            NativeContext nativeContext = new DefaultNativeContext(
                context.getHeapManager(),
                context.getClassResolver()
            );
            ConcreteValue result = nativeRegistry.execute(method, receiver, args, nativeContext);
            return InvocationResult.nativeHandled(result);
        } catch (NativeException e) {
            ObjectInstance exception = context.getHeapManager().newObject(e.getExceptionClass());
            if (e.getMessage() != null) {
                ObjectInstance messageStr = context.getHeapManager().internString(e.getMessage());
                exception.setField("java/lang/Throwable", "detailMessage", "Ljava/lang/String;", messageStr);
            }
            return InvocationResult.exception(exception);
        }
    }

    private ConcreteValue[] buildFrameArgs(MethodEntry method, ObjectInstance receiver,
                                          ConcreteValue[] args) {
        boolean isStatic = (method.getAccess() & 0x0008) != 0;

        if (isStatic) {
            return args != null ? args : new ConcreteValue[0];
        }

        ConcreteValue receiverValue = receiver != null ?
            ConcreteValue.reference(receiver) : ConcreteValue.nullRef();

        ConcreteValue[] frameArgs = new ConcreteValue[1 + (args != null ? args.length : 0)];
        frameArgs[0] = receiverValue;
        if (args != null) {
            System.arraycopy(args, 0, frameArgs, 1, args.length);
        }

        return frameArgs;
    }

    private InvocationResult stubResult(String descriptor, HeapManager heapManager) {
        String returnType = getReturnType(descriptor);
        ConcreteValue result;
        if ("V".equals(returnType)) {
            result = null;
        } else if (returnType.startsWith("L") || returnType.startsWith("[")) {
            result = ConcreteValue.reference(heapManager.newObject("java/lang/Object"));
        } else if ("J".equals(returnType)) {
            result = ConcreteValue.longValue(0L);
        } else if ("D".equals(returnType)) {
            result = ConcreteValue.doubleValue(0.0);
        } else if ("F".equals(returnType)) {
            result = ConcreteValue.floatValue(0.0f);
        } else if ("Z".equals(returnType)) {
            result = ConcreteValue.intValue(0);
        } else {
            result = ConcreteValue.intValue(0);
        }
        return InvocationResult.nativeHandled(result);
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

    private static class DefaultNativeContext implements NativeContext {
        private final HeapManager heapManager;
        private final ClassResolver classResolver;

        DefaultNativeContext(HeapManager heapManager, ClassResolver classResolver) {
            this.heapManager = heapManager;
            this.classResolver = classResolver;
        }

        @Override
        public HeapManager getHeapManager() {
            return heapManager;
        }

        @Override
        public ClassResolver getClassResolver() {
            return classResolver;
        }

        @Override
        public ObjectInstance createString(String value) {
            return heapManager.internString(value);
        }

        @Override
        public ObjectInstance createException(String className, String message) {
            ObjectInstance exception = heapManager.newObject(className);
            if (message != null) {
                ObjectInstance messageStr = heapManager.internString(message);
                exception.setField(className, "detailMessage", "Ljava/lang/String;", messageStr);
            }
            return exception;
        }
    }
}
