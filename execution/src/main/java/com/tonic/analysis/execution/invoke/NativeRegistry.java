package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.handlers.*;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry mapping owner.name+descriptor keys to native-method handlers.
 */
public final class NativeRegistry
{

    private final ConcurrentHashMap<String, NativeMethodHandler> handlers;

    /**
     * Creates an empty registry.
     */
    public NativeRegistry()
    {
        this.handlers = new ConcurrentHashMap<>();
    }

    /**
     * Registers a handler for the identified method.
     * @param owner internal name of the declaring class
     * @param name the method name
     * @param descriptor the method descriptor
     * @param handler the handler to invoke for the method
     */
    public void register(String owner, String name, String descriptor, NativeMethodHandler handler)
    {
        String key = methodKey(owner, name, descriptor);
        handlers.put(key, handler);
    }

    /**
     * Registers a handler under a precomputed method key.
     * @param key a key produced by methodKey
     * @param handler the handler to invoke for the method
     */
    public void register(String key, NativeMethodHandler handler)
    {
        handlers.put(key, handler);
    }

    /**
     * Checks whether a handler is registered for the identified method.
     * @param owner internal name of the declaring class
     * @param name the method name
     * @param descriptor the method descriptor
     * @return true if a handler is registered
     */
    public boolean hasHandler(String owner, String name, String descriptor)
    {
        String key = methodKey(owner, name, descriptor);
        return handlers.containsKey(key);
    }

    /**
     * Checks whether a handler is registered for the given method.
     * @param method the method to look up
     * @return true if a handler is registered
     */
    public boolean hasHandler(MethodEntry method)
    {
        return hasHandler(method.getOwnerName(), method.getName(), method.getDesc());
    }

    /**
     * Looks up the handler for the identified method.
     * @param owner internal name of the declaring class
     * @param name the method name
     * @param descriptor the method descriptor
     * @return the registered handler
     * @throws IllegalArgumentException if no handler is registered
     */
    public NativeMethodHandler getHandler(String owner, String name, String descriptor)
    {
        String key = methodKey(owner, name, descriptor);
        NativeMethodHandler handler = handlers.get(key);
        if (handler == null)
        {
            throw new IllegalArgumentException("No handler for: " + key);
        }
        return handler;
    }

    /**
     * Looks up the handler for the given method.
     * @param method the method to look up
     * @return the registered handler
     * @throws IllegalArgumentException if no handler is registered
     */
    public NativeMethodHandler getHandler(MethodEntry method)
    {
        return getHandler(method.getOwnerName(), method.getName(), method.getDesc());
    }

    /**
     * Runs the registered handler for the given method.
     * @param method the method to execute
     * @param receiver the receiver instance, or null for static methods
     * @param args the argument values
     * @param context the native execution environment
     * @return the handler's result, or null for void
     * @throws NativeException if the handler raises a guest exception
     * @throws IllegalArgumentException if no handler is registered
     */
    public ConcreteValue execute(MethodEntry method, ObjectInstance receiver, ConcreteValue[] args, NativeContext context)
            throws NativeException
            {
        NativeMethodHandler handler = getHandler(method);
        return handler.handle(receiver, args, context);
    }

    /**
     * Runs the registered handler for the identified method.
     * @param owner internal name of the declaring class
     * @param name the method name
     * @param descriptor the method descriptor
     * @param receiver the receiver instance, or null for static methods
     * @param args the argument values
     * @param context the native execution environment
     * @return the handler's result, or null for void
     * @throws NativeException if the handler raises a guest exception
     * @throws IllegalArgumentException if no handler is registered
     */
    public ConcreteValue execute(String owner, String name, String descriptor, ObjectInstance receiver, ConcreteValue[] args, NativeContext context) throws NativeException
    {
        NativeMethodHandler handler = getHandler(owner, name, descriptor);
        return handler.handle(receiver, args, context);
    }

    /**
     * Builds the registry key for the identified method.
     * @param owner internal name of the declaring class
     * @param name the method name
     * @param descriptor the method descriptor
     * @return the composite key
     */
    public static String methodKey(String owner, String name, String descriptor)
    {
        return owner + "." + name + descriptor;
    }

    /**
     * Builds the registry key for the given method.
     * @param method the method to key
     * @return the composite key
     */
    public static String methodKey(MethodEntry method)
    {
        return methodKey(method.getOwnerName(), method.getName(), method.getDesc());
    }

    /**
     * Lets the given provider register its handlers with this registry.
     * @param provider the handler contributor
     */
    public void registerProvider(NativeHandlerProvider provider)
    {
        provider.register(this);
    }

    /**
     * Registers all built-in handler families covering the core JDK surface.
     */
    public void registerDefaults()
    {
        registerProvider(new CoreHandlers());
        registerProvider(new MathHandlers());
        registerProvider(new StringHandlers());
        registerProvider(new WrapperHandlers());
        registerProvider(new CollectionHandlers());
        registerProvider(new IOHandlers());
        registerProvider(new TimeHandlers());
        registerProvider(new SystemHandlers());
        registerProvider(new ConcurrencyHandlers());
        registerProvider(new ZipHandlers());
        registerProvider(new FileIOHandlers());
        registerProvider(new ProcessHandlers());
        registerProvider(new ThreadHandlers());
        registerProvider(new ReflectionHandlers());
        registerProvider(new NetworkHandlers());
        registerProvider(new NIOHandlers());
        registerProvider(new VMHandlers());
        registerProvider(new SecurityHandlers());
        registerProvider(new LocaleHandlers());
    }
}
