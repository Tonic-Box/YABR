package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class ReflectionHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerClassLoaderHandlers(registry);
        registerExecutableHandlers(registry);
        registerFieldHandlers(registry);
        registerModuleHandlers(registry);
        registerReferenceHandlers(registry);
    }

    private void registerClassLoaderHandlers(NativeRegistry registry) {
        registry.register("java/lang/ClassLoader", "registerNatives", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/ClassLoader", "defineClass1", "(Ljava/lang/ClassLoader;Ljava/lang/String;[BIILjava/security/ProtectionDomain;Ljava/lang/String;)Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                ObjectInstance classObj = ctx.getHeapManager().newObject("java/lang/Class");
                if (args != null && args.length > 1 && !args[1].isNull()) {
                    classObj.setField("java/lang/Class", "name", "Ljava/lang/String;", args[1].asReference());
                }
                return ConcreteValue.reference(classObj);
            });

        registry.register("java/lang/ClassLoader", "defineClass2", "(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/nio/ByteBuffer;IILjava/security/ProtectionDomain;Ljava/lang/String;)Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                ObjectInstance classObj = ctx.getHeapManager().newObject("java/lang/Class");
                if (args != null && args.length > 1 && !args[1].isNull()) {
                    classObj.setField("java/lang/Class", "name", "Ljava/lang/String;", args[1].asReference());
                }
                return ConcreteValue.reference(classObj);
            });

        registry.register("java/lang/ClassLoader", "findBootstrapClass", "(Ljava/lang/String;)Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                ObjectInstance classObj = ctx.getHeapManager().newObject("java/lang/Class");
                if (args != null && args.length > 0 && !args[0].isNull()) {
                    classObj.setField("java/lang/Class", "name", "Ljava/lang/String;", args[0].asReference());
                }
                return ConcreteValue.reference(classObj);
            });

        registry.register("java/lang/ClassLoader", "findLoadedClass0", "(Ljava/lang/String;)Ljava/lang/Class;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/ClassLoader", "findBuiltinLib", "(Ljava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/ClassLoader", "retrieveDirectives", "()Ljava/lang/AssertionStatusDirectives;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/ClassLoader$NativeLibrary", "load0", "(Ljava/lang/String;ZZ)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("java/lang/ClassLoader$NativeLibrary", "unload", "(Ljava/lang/String;ZJ)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/ClassLoader$NativeLibrary", "findEntry", "(Ljava/lang/String;)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));
    }

    private void registerExecutableHandlers(NativeRegistry registry) {
        registry.register("java/lang/reflect/Executable", "getParameters0", "()[Ljava/lang/reflect/Parameter;",
            (receiver, args, ctx) -> {
                ArrayInstance empty = ctx.getHeapManager().newArray("[Ljava/lang/reflect/Parameter;", 0);
                return ConcreteValue.reference(empty);
            });

        registry.register("java/lang/reflect/Executable", "getTypeAnnotationBytes0", "()[B",
            (receiver, args, ctx) -> {
                ArrayInstance empty = ctx.getHeapManager().newArray("[B", 0);
                return ConcreteValue.reference(empty);
            });
    }

    private void registerFieldHandlers(NativeRegistry registry) {
        registry.register("java/lang/reflect/Field", "getTypeAnnotationBytes0", "()[B",
            (receiver, args, ctx) -> {
                ArrayInstance empty = ctx.getHeapManager().newArray("[B", 0);
                return ConcreteValue.reference(empty);
            });
    }

    private void registerModuleHandlers(NativeRegistry registry) {
        registry.register("java/lang/Module", "defineModule0", "(Ljava/lang/Module;ZLjava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Module", "addReads0", "(Ljava/lang/Module;Ljava/lang/Module;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Module", "addExports0", "(Ljava/lang/Module;Ljava/lang/String;Ljava/lang/Module;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Module", "addExportsToAll0", "(Ljava/lang/Module;Ljava/lang/String;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Module", "addExportsToAllUnnamed0", "(Ljava/lang/Module;Ljava/lang/String;)V",
            (receiver, args, ctx) -> null);
    }

    private void registerReferenceHandlers(NativeRegistry registry) {
        registry.register("java/lang/ref/Reference", "getAndClearReferencePendingList", "()Ljava/lang/ref/Reference;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/ref/Reference", "hasReferencePendingList", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/ref/Reference", "waitForReferencePendingList", "()V",
            (receiver, args, ctx) -> null);
    }
}
