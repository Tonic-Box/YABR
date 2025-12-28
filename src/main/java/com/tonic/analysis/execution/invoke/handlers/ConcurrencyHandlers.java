package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public class ConcurrencyHandlers implements NativeHandlerProvider {
    @Override
    public void register(NativeRegistry registry) {
        registry.register("java/util/concurrent/atomic/AtomicLong", "VMSupportsCS8", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("java/util/concurrent/atomic/AtomicInteger", "VMSupportsCS8", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));
    }
}
