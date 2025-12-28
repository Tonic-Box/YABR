package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class SecurityHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerAccessControllerHandlers(registry);
        registerNativeSeedGeneratorHandlers(registry);
    }

    private void registerAccessControllerHandlers(NativeRegistry registry) {
        registry.register("java/security/AccessController", "doPrivileged", "(Ljava/security/PrivilegedAction;Ljava/security/AccessControlContext;)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/security/AccessController", "doPrivileged", "(Ljava/security/PrivilegedExceptionAction;Ljava/security/AccessControlContext;)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/security/AccessController", "getInheritedAccessControlContext", "()Ljava/security/AccessControlContext;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());
    }

    private void registerNativeSeedGeneratorHandlers(NativeRegistry registry) {
        registry.register("sun/security/provider/NativeSeedGenerator", "nativeGenerateSeed", "([B)Z",
            (receiver, args, ctx) -> {
                if (args != null && args.length > 0 && !args[0].isNull()) {
                    ArrayInstance arr = (ArrayInstance) args[0].asReference();
                    java.security.SecureRandom secureRandom = new java.security.SecureRandom();
                    byte[] bytes = new byte[arr.getLength()];
                    secureRandom.nextBytes(bytes);
                    for (int i = 0; i < bytes.length; i++) {
                        arr.setByte(i, bytes[i]);
                    }
                    return ConcreteValue.intValue(1);
                }
                return ConcreteValue.intValue(0);
            });
    }
}
