package com.tonic.analysis.execution.invoke;

/**
 * Contributor that registers a family of native-method handlers with a registry.
 */
@FunctionalInterface
public interface NativeHandlerProvider
{
    /**
     * Adds this provider's handlers to a registry.
     *
     * @param registry the registry to populate
     */
    void register(NativeRegistry registry);
}
