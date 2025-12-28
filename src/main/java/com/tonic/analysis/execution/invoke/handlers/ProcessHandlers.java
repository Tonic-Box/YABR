package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class ProcessHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerRuntimeHandlers(registry);
        registerProcessImplHandlers(registry);
        registerProcessHandleImplHandlers(registry);
        registerShutdownHandlers(registry);
        registerProcessEnvironmentHandlers(registry);
        registerSecurityManagerHandlers(registry);
    }

    private void registerRuntimeHandlers(NativeRegistry registry) {
        registry.register("java/lang/Runtime", "gc", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Runtime", "totalMemory", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(Runtime.getRuntime().totalMemory()));

        registry.register("java/lang/Runtime", "freeMemory", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(Runtime.getRuntime().freeMemory()));

        registry.register("java/lang/Runtime", "maxMemory", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(Runtime.getRuntime().maxMemory()));
    }

    private void registerProcessImplHandlers(NativeRegistry registry) {
        registry.register("java/lang/ProcessImpl", "create", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[JZ)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(-1L));

        registry.register("java/lang/ProcessImpl", "getExitCodeProcess", "(J)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/ProcessImpl", "getProcessId0", "(J)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/ProcessImpl", "getStillActive", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(259));

        registry.register("java/lang/ProcessImpl", "waitForInterruptibly", "(J)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/ProcessImpl", "waitForTimeoutInterruptibly", "(JJ)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/ProcessImpl", "terminateProcess", "(J)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/ProcessImpl", "isProcessAlive", "(J)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/ProcessImpl", "closeHandle", "(J)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("java/lang/ProcessImpl", "openForAtomicAppend", "(Ljava/lang/String;)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(-1L));
    }

    private void registerProcessHandleImplHandlers(NativeRegistry registry) {
        registry.register("java/lang/ProcessHandleImpl", "initNative", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/ProcessHandleImpl", "getCurrentPid0", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(ProcessHandle.current().pid()));

        registry.register("java/lang/ProcessHandleImpl", "parent0", "(JJ)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(-1L));

        registry.register("java/lang/ProcessHandleImpl", "getProcessPids0", "(J[J[J[J)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/ProcessHandleImpl", "destroy0", "(JJZ)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/ProcessHandleImpl", "isAlive0", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/lang/ProcessHandleImpl", "waitForProcessExit0", "(JZ)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/ProcessHandleImpl$Info", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/ProcessHandleImpl$Info", "info0", "(J)V",
            (receiver, args, ctx) -> null);
    }

    private void registerShutdownHandlers(NativeRegistry registry) {
        registry.register("java/lang/Shutdown", "beforeHalt", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Shutdown", "halt0", "(I)V",
            (receiver, args, ctx) -> null);
    }

    private void registerProcessEnvironmentHandlers(NativeRegistry registry) {
        registry.register("java/lang/ProcessEnvironment", "environmentBlock", "()Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("")));
    }

    private void registerSecurityManagerHandlers(NativeRegistry registry) {
        registry.register("java/lang/SecurityManager", "getClassContext", "()[Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                ArrayInstance empty = ctx.getHeapManager().newArray("[Ljava/lang/Class;", 0);
                return ConcreteValue.reference(empty);
            });
    }
}
