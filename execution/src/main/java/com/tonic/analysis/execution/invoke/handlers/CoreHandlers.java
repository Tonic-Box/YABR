package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeContext;
import com.tonic.analysis.execution.invoke.NativeException;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class CoreHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerObjectHandlers(registry);
        registerExceptionHandlers(registry);
        registerClassHandlers(registry);
    }

    private void registerObjectHandlers(NativeRegistry registry) {
        registry.register("java/lang/Object", "<init>", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Object", "hashCode", "()I",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    return ConcreteValue.intValue(0);
                }
                return ConcreteValue.intValue(receiver.getIdentityHashCode());
            });

        registry.register("java/lang/Object", "getClass", "()Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "getClass on null");
                }
                ObjectInstance classObj = ctx.getHeapManager().newObject("java/lang/Class");
                classObj.setField("java/lang/Class", "name", "Ljava/lang/String;",
                    ctx.createString(receiver.getClassName()));
                return ConcreteValue.reference(classObj);
            });

        registry.register("java/lang/Object", "equals", "(Ljava/lang/Object;)Z",
            (receiver, args, ctx) -> {
                if (args == null || args.length == 0) {
                    return ConcreteValue.intValue(receiver == null ? 1 : 0);
                }
                ObjectInstance other = args[0].isNull() ? null : args[0].asReference();
                return ConcreteValue.intValue(receiver == other ? 1 : 0);
            });
    }

    private void registerExceptionHandlers(NativeRegistry registry) {
        String[] exceptionClasses = {
            "java/lang/Throwable",
            "java/lang/Exception",
            "java/lang/RuntimeException",
            "java/lang/Error",
            "java/lang/ArithmeticException",
            "java/lang/IllegalArgumentException",
            "java/lang/IllegalStateException",
            "java/lang/NullPointerException",
            "java/lang/IndexOutOfBoundsException",
            "java/lang/ArrayIndexOutOfBoundsException",
            "java/lang/StringIndexOutOfBoundsException",
            "java/lang/ClassCastException",
            "java/lang/UnsupportedOperationException",
            "java/lang/NumberFormatException"
        };

        for (String exClass : exceptionClasses) {
            registry.register(exClass, "<init>", "()V",
                (receiver, args, ctx) -> ConcreteValue.nullRef());

            registry.register(exClass, "<init>", "(Ljava/lang/String;)V",
                (receiver, args, ctx) -> {
                    if (receiver != null && args != null && args.length > 0 && !args[0].isNull()) {
                        receiver.setField(exClass, "detailMessage", "Ljava/lang/String;", args[0].asReference());
                    }
                    return ConcreteValue.nullRef();
                });

            registry.register(exClass, "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V",
                (receiver, args, ctx) -> {
                    if (receiver != null && args != null) {
                        if (args.length > 0 && !args[0].isNull()) {
                            receiver.setField(exClass, "detailMessage", "Ljava/lang/String;", args[0].asReference());
                        }
                        if (args.length > 1 && !args[1].isNull()) {
                            receiver.setField(exClass, "cause", "Ljava/lang/Throwable;", args[1].asReference());
                        }
                    }
                    return ConcreteValue.nullRef();
                });

            registry.register(exClass, "<init>", "(Ljava/lang/Throwable;)V",
                (receiver, args, ctx) -> {
                    if (receiver != null && args != null && args.length > 0 && !args[0].isNull()) {
                        receiver.setField(exClass, "cause", "Ljava/lang/Throwable;", args[0].asReference());
                    }
                    return ConcreteValue.nullRef();
                });

            registry.register(exClass, "getMessage", "()Ljava/lang/String;",
                (receiver, args, ctx) -> {
                    if (receiver == null) {
                        return ConcreteValue.nullRef();
                    }
                    Object msg = receiver.getField(exClass, "detailMessage", "Ljava/lang/String;");
                    if (msg instanceof ObjectInstance) {
                        return ConcreteValue.reference((ObjectInstance) msg);
                    }
                    return ConcreteValue.nullRef();
                });

            registry.register(exClass, "getCause", "()Ljava/lang/Throwable;",
                (receiver, args, ctx) -> {
                    if (receiver == null) {
                        return ConcreteValue.nullRef();
                    }
                    Object cause = receiver.getField(exClass, "cause", "Ljava/lang/Throwable;");
                    if (cause instanceof ObjectInstance) {
                        return ConcreteValue.reference((ObjectInstance) cause);
                    }
                    return ConcreteValue.nullRef();
                });
        }
    }

    private void registerClassHandlers(NativeRegistry registry) {
        registry.register("java/lang/Class", "isArray", "()Z",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "isArray on null");
                }
                Object nameObj = receiver.getField("java/lang/Class", "name", "Ljava/lang/String;");
                if (nameObj instanceof ObjectInstance) {
                    String name = ctx.getHeapManager().extractString((ObjectInstance) nameObj);
                    return ConcreteValue.intValue(name != null && name.startsWith("[") ? 1 : 0);
                }
                return ConcreteValue.intValue(0);
            });

        registry.register("java/lang/Class", "isPrimitive", "()Z",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "isPrimitive on null");
                }
                Object nameObj = receiver.getField("java/lang/Class", "name", "Ljava/lang/String;");
                if (nameObj instanceof ObjectInstance) {
                    String name = ctx.getHeapManager().extractString((ObjectInstance) nameObj);
                    if (name == null) return ConcreteValue.intValue(0);
                    boolean isPrim = name.equals("int") || name.equals("long") || name.equals("byte") ||
                                    name.equals("short") || name.equals("char") || name.equals("boolean") ||
                                    name.equals("float") || name.equals("double") || name.equals("void");
                    return ConcreteValue.intValue(isPrim ? 1 : 0);
                }
                return ConcreteValue.intValue(0);
            });

        registry.register("java/lang/Class", "getName", "()Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "getName on null");
                }
                Object nameObj = receiver.getField("java/lang/Class", "name", "Ljava/lang/String;");
                if (nameObj instanceof ObjectInstance) {
                    return ConcreteValue.reference((ObjectInstance) nameObj);
                }
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/Class", "desiredAssertionStatus", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/Class", "getDeclaredFields0", "(Z)[Ljava/lang/reflect/Field;",
            (receiver, args, ctx) -> {
                ArrayInstance emptyArray = ctx.getHeapManager().newArray("[Ljava/lang/reflect/Field;", 0);
                return ConcreteValue.reference(emptyArray);
            });

        registry.register("java/lang/Class", "getDeclaredMethods0", "(Z)[Ljava/lang/reflect/Method;",
            (receiver, args, ctx) -> {
                ArrayInstance emptyArray = ctx.getHeapManager().newArray("[Ljava/lang/reflect/Method;", 0);
                return ConcreteValue.reference(emptyArray);
            });

        registry.register("java/lang/Class", "getDeclaredConstructors0", "(Z)[Ljava/lang/reflect/Constructor;",
            (receiver, args, ctx) -> {
                ArrayInstance emptyArray = ctx.getHeapManager().newArray("[Ljava/lang/reflect/Constructor;", 0);
                return ConcreteValue.reference(emptyArray);
            });

        registry.register("java/lang/Class", "getDeclaredClasses0", "()[Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                ArrayInstance emptyArray = ctx.getHeapManager().newArray("[Ljava/lang/Class;", 0);
                return ConcreteValue.reference(emptyArray);
            });

        registry.register("java/lang/Class", "getInterfaces0", "()[Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                ArrayInstance emptyArray = ctx.getHeapManager().newArray("[Ljava/lang/Class;", 0);
                return ConcreteValue.reference(emptyArray);
            });

        registry.register("java/lang/Class", "getSuperclass", "()Ljava/lang/Class;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/Class", "getModifiers", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("java/lang/Class", "isInterface", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/Class", "isInstance", "(Ljava/lang/Object;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/Class", "isAssignableFrom", "(Ljava/lang/Class;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/Class", "getSigners", "()[Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/Class", "setSigners", "([Ljava/lang/Object;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Class", "getDeclaringClass0", "()Ljava/lang/Class;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/Class", "getEnclosingMethod0", "()[Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/Class", "getSimpleBinaryName0", "()Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/Class", "getGenericSignature0", "()Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/Class", "getRawAnnotations", "()[B",
            (receiver, args, ctx) -> {
                ArrayInstance emptyArray = ctx.getHeapManager().newArray("[B", 0);
                return ConcreteValue.reference(emptyArray);
            });

        registry.register("java/lang/Class", "getRawTypeAnnotations", "()[B",
            (receiver, args, ctx) -> {
                ArrayInstance emptyArray = ctx.getHeapManager().newArray("[B", 0);
                return ConcreteValue.reference(emptyArray);
            });

        registry.register("java/lang/Class", "getConstantPool", "()Ljdk/internal/reflect/ConstantPool;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/Class", "getProtectionDomain0", "()Ljava/security/ProtectionDomain;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/Class", "getNestHost0", "()Ljava/lang/Class;",
            (receiver, args, ctx) -> receiver != null ? ConcreteValue.reference(receiver) : ConcreteValue.nullRef());

        registry.register("java/lang/Class", "getNestMembers0", "()[Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/Class;", 1);
                if (receiver != null) {
                    arr.set(0, ConcreteValue.reference(receiver));
                }
                return ConcreteValue.reference(arr);
            });

        registry.register("java/lang/Class", "initClassName", "()Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (receiver == null) return ConcreteValue.nullRef();
                Object nameObj = receiver.getField("java/lang/Class", "name", "Ljava/lang/String;");
                if (nameObj instanceof ObjectInstance) {
                    return ConcreteValue.reference((ObjectInstance) nameObj);
                }
                return ConcreteValue.reference(ctx.createString("Unknown"));
            });

        registry.register("java/lang/Class", "desiredAssertionStatus0", "(Ljava/lang/Class;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));
    }
}
