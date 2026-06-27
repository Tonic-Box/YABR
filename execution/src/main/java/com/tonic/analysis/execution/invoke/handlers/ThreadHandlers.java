package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class ThreadHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerThreadHandlers(registry);
        registerStackWalkerHandlers(registry);
        registerStackTraceHandlers(registry);
    }

    private void registerThreadHandlers(NativeRegistry registry) {
        registry.register("java/lang/Thread", "setNativeName", "(Ljava/lang/String;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Thread", "suspend0", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Thread", "resume0", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Thread", "stop0", "(Ljava/lang/Object;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Thread", "countStackFrames", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/Thread", "dumpThreads", "([Ljava/lang/Thread;)[[Ljava/lang/StackTraceElement;",
            (receiver, args, ctx) -> {
                ArrayInstance empty = ctx.getHeapManager().newArray("[[Ljava/lang/StackTraceElement;", 0);
                return ConcreteValue.reference(empty);
            });

        registry.register("java/lang/Thread", "getThreads", "()[Ljava/lang/Thread;",
            (receiver, args, ctx) -> {
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/Thread;", 1);
                ObjectInstance mainThread = ctx.getHeapManager().newObject("java/lang/Thread");
                mainThread.setField("java/lang/Thread", "name", "Ljava/lang/String;",
                    ctx.getHeapManager().internString("main"));
                arr.set(0, ConcreteValue.reference(mainThread));
                return ConcreteValue.reference(arr);
            });
    }

    private void registerStackWalkerHandlers(NativeRegistry registry) {
        registry.register("java/lang/StackStreamFactory", "checkStackWalkModes", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("java/lang/StackStreamFactory$AbstractStackWalker", "callStackWalk", "(JIII[Ljava/lang/Object;)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/StackStreamFactory$AbstractStackWalker", "fetchStackFrames", "(JJII[Ljava/lang/Object;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));
    }

    private void registerStackTraceHandlers(NativeRegistry registry) {
        registry.register("java/lang/StackTraceElement", "initStackTraceElement", "(Ljava/lang/StackTraceElement;Ljava/lang/StackFrameInfo;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/StackTraceElement", "initStackTraceElements", "([Ljava/lang/StackTraceElement;Ljava/lang/Throwable;)V",
            (receiver, args, ctx) -> null);
    }
}
