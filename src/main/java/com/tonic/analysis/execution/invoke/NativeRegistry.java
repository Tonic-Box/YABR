package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.handlers.*;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;

import java.util.concurrent.ConcurrentHashMap;

public final class NativeRegistry {

    private final ConcurrentHashMap<String, NativeMethodHandler> handlers;

    public NativeRegistry() {
        this.handlers = new ConcurrentHashMap<>();
    }

    public void register(String owner, String name, String descriptor,
                        NativeMethodHandler handler) {
        String key = methodKey(owner, name, descriptor);
        handlers.put(key, handler);
    }

    public void register(String key, NativeMethodHandler handler) {
        handlers.put(key, handler);
    }

    public boolean hasHandler(String owner, String name, String descriptor) {
        String key = methodKey(owner, name, descriptor);
        return handlers.containsKey(key);
    }

    public boolean hasHandler(MethodEntry method) {
        return hasHandler(method.getOwnerName(), method.getName(), method.getDesc());
    }

    public NativeMethodHandler getHandler(String owner, String name, String descriptor) {
        String key = methodKey(owner, name, descriptor);
        NativeMethodHandler handler = handlers.get(key);
        if (handler == null) {
            throw new IllegalArgumentException("No handler for: " + key);
        }
        return handler;
    }

    public NativeMethodHandler getHandler(MethodEntry method) {
        return getHandler(method.getOwnerName(), method.getName(), method.getDesc());
    }

    public ConcreteValue execute(MethodEntry method, ObjectInstance receiver,
                                ConcreteValue[] args, NativeContext context)
            throws NativeException {
        NativeMethodHandler handler = getHandler(method);
        return handler.handle(receiver, args, context);
    }

    public ConcreteValue execute(String owner, String name, String descriptor,
                                ObjectInstance receiver, ConcreteValue[] args,
                                NativeContext context) throws NativeException {
        NativeMethodHandler handler = getHandler(owner, name, descriptor);
        return handler.handle(receiver, args, context);
    }

    public static String methodKey(String owner, String name, String descriptor) {
        return owner + "." + name + descriptor;
    }

    public static String methodKey(MethodEntry method) {
        return methodKey(method.getOwnerName(), method.getName(), method.getDesc());
    }

    public void registerProvider(NativeHandlerProvider provider) {
        provider.register(this);
    }

    public void registerDefaults() {
        registerProvider(new CoreHandlers());
        registerProvider(new MathHandlers());
        registerProvider(new StringHandlers());
        registerProvider(new WrapperHandlers());
        registerProvider(new CollectionHandlers());
        registerProvider(new IOHandlers());
        registerProvider(new TimeHandlers());
        registerProvider(new SystemHandlers());
        registerProvider(new ConcurrencyHandlers());
    }
}
