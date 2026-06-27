package com.tonic.analysis.execution.invoke;

@FunctionalInterface
public interface NativeHandlerProvider {
    void register(NativeRegistry registry);
}
