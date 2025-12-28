package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeException;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class SystemHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerJdkCoreHandlers(registry);
        registerMoreSystemHandlers(registry);
    }

    private void registerJdkCoreHandlers(NativeRegistry registry) {
        registry.register("java/lang/Object", "registerNatives", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Class", "registerNatives", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/System", "registerNatives", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Thread", "registerNatives", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Class", "getPrimitiveClass", "(Ljava/lang/String;)Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                ObjectInstance classObj = ctx.getHeapManager().newObject("java/lang/Class");
                if (args != null && args.length > 0 && !args[0].isNull()) {
                    String typeName = ctx.getHeapManager().extractString(args[0].asReference());
                    if (typeName != null) {
                        classObj.setField("java/lang/Class", "name", "Ljava/lang/String;", args[0].asReference());
                    }
                }
                return ConcreteValue.reference(classObj);
            });

        registry.register("java/lang/Class", "desiredAssertionStatus", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/Class", "forName0", "(Ljava/lang/String;ZLjava/lang/ClassLoader;Ljava/lang/Class;)Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                ObjectInstance classObj = ctx.getHeapManager().newObject("java/lang/Class");
                if (args != null && args.length > 0 && !args[0].isNull()) {
                    classObj.setField("java/lang/Class", "name", "Ljava/lang/String;", args[0].asReference());
                }
                return ConcreteValue.reference(classObj);
            });

        registry.register("java/lang/StrictMath", "log", "(D)D",
            (receiver, args, ctx) -> {
                double val = args != null && args.length > 0 ? args[0].asDouble() : 0.0;
                return ConcreteValue.doubleValue(Math.log(val));
            });

        registry.register("java/lang/StrictMath", "sin", "(D)D",
            (receiver, args, ctx) -> {
                double val = args != null && args.length > 0 ? args[0].asDouble() : 0.0;
                return ConcreteValue.doubleValue(Math.sin(val));
            });

        registry.register("java/lang/StrictMath", "cos", "(D)D",
            (receiver, args, ctx) -> {
                double val = args != null && args.length > 0 ? args[0].asDouble() : 0.0;
                return ConcreteValue.doubleValue(Math.cos(val));
            });

        registry.register("java/lang/StrictMath", "sqrt", "(D)D",
            (receiver, args, ctx) -> {
                double val = args != null && args.length > 0 ? args[0].asDouble() : 0.0;
                return ConcreteValue.doubleValue(Math.sqrt(val));
            });

        registry.register("java/lang/StrictMath", "pow", "(DD)D",
            (receiver, args, ctx) -> {
                double base = args != null && args.length > 0 ? args[0].asDouble() : 0.0;
                double exp = args != null && args.length > 1 ? args[1].asDouble() : 0.0;
                return ConcreteValue.doubleValue(Math.pow(base, exp));
            });

        registry.register("java/lang/Runtime", "availableProcessors", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(Runtime.getRuntime().availableProcessors()));

        registry.register("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;",
            (receiver, args, ctx) -> {
                ObjectInstance thread = ctx.getHeapManager().newObject("java/lang/Thread");
                thread.setField("java/lang/Thread", "name", "Ljava/lang/String;",
                    ctx.getHeapManager().internString("main"));
                return ConcreteValue.reference(thread);
            });

        registry.register("jdk/internal/misc/Unsafe", "registerNatives", "()V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "compareAndSetInt", "(Ljava/lang/Object;JII)Z",
            (receiver, args, ctx) -> {
                if (args != null && args.length >= 4) {
                    ObjectInstance obj = args[0].isNull() ? null : args[0].asReference();
                    int expected = args[2].asInt();
                    int update = args[3].asInt();
                    return ConcreteValue.intValue(1);
                }
                return ConcreteValue.intValue(1);
            });

        registry.register("jdk/internal/misc/Unsafe", "compareAndSetLong", "(Ljava/lang/Object;JJJ)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("jdk/internal/misc/Unsafe", "compareAndSetReference", "(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("jdk/internal/misc/Unsafe", "compareAndSetObject", "(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("jdk/internal/misc/Unsafe", "getIntVolatile", "(Ljava/lang/Object;J)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/misc/Unsafe", "getLongVolatile", "(Ljava/lang/Object;J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("jdk/internal/misc/Unsafe", "getReferenceVolatile", "(Ljava/lang/Object;J)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/misc/Unsafe", "putIntVolatile", "(Ljava/lang/Object;JI)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "putLongVolatile", "(Ljava/lang/Object;JJ)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "putReferenceVolatile", "(Ljava/lang/Object;JLjava/lang/Object;)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "objectFieldOffset1", "(Ljava/lang/Class;Ljava/lang/String;)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(16L));

        registry.register("jdk/internal/misc/Unsafe", "arrayBaseOffset0", "(Ljava/lang/Class;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(16));

        registry.register("jdk/internal/misc/Unsafe", "arrayIndexScale0", "(Ljava/lang/Class;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(4));

        registry.register("jdk/internal/misc/Unsafe", "addressSize0", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(8));

        registry.register("jdk/internal/misc/Unsafe", "getObjectVolatile", "(Ljava/lang/Object;J)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/misc/Unsafe", "isBigEndian0", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/misc/Unsafe", "unalignedAccess0", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("jdk/internal/misc/Unsafe", "storeFence", "()V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "loadFence", "()V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "fullFence", "()V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "ensureClassInitialized0", "(Ljava/lang/Class;)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "shouldBeInitialized0", "(Ljava/lang/Class;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/misc/Unsafe", "getInt", "(Ljava/lang/Object;J)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/misc/Unsafe", "getLong", "(Ljava/lang/Object;J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("jdk/internal/misc/Unsafe", "getObject", "(Ljava/lang/Object;J)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/misc/Unsafe", "getReference", "(Ljava/lang/Object;J)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/misc/Unsafe", "getByte", "(Ljava/lang/Object;J)B",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/misc/Unsafe", "getShort", "(Ljava/lang/Object;J)S",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/misc/Unsafe", "getChar", "(Ljava/lang/Object;J)C",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/misc/Unsafe", "getFloat", "(Ljava/lang/Object;J)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(0.0f));

        registry.register("jdk/internal/misc/Unsafe", "getDouble", "(Ljava/lang/Object;J)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(0.0));

        registry.register("jdk/internal/misc/Unsafe", "getBoolean", "(Ljava/lang/Object;J)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/misc/Unsafe", "putInt", "(Ljava/lang/Object;JI)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "putLong", "(Ljava/lang/Object;JJ)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "putObject", "(Ljava/lang/Object;JLjava/lang/Object;)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "putReference", "(Ljava/lang/Object;JLjava/lang/Object;)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "putByte", "(Ljava/lang/Object;JB)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "putShort", "(Ljava/lang/Object;JS)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "putChar", "(Ljava/lang/Object;JC)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "putFloat", "(Ljava/lang/Object;JF)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "putDouble", "(Ljava/lang/Object;JD)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "putBoolean", "(Ljava/lang/Object;JZ)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "allocateMemory0", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("jdk/internal/misc/Unsafe", "freeMemory0", "(J)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "setMemory0", "(Ljava/lang/Object;JJB)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "copyMemory0", "(Ljava/lang/Object;JLjava/lang/Object;JJ)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "getLoadAverage0", "([DI)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/misc/Unsafe", "park", "(ZJ)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/Unsafe", "unpark", "(Ljava/lang/Object;)V",
            (receiver, args, ctx) -> null);
    }

    private void registerMoreSystemHandlers(NativeRegistry registry) {
        registry.register("java/lang/System", "currentTimeMillis", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(System.currentTimeMillis()));

        registry.register("java/lang/System", "nanoTime", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(System.nanoTime()));

        registry.register("java/lang/System", "identityHashCode", "(Ljava/lang/Object;)I",
            (receiver, args, ctx) -> {
                if (args == null || args.length == 0 || args[0].isNull()) {
                    return ConcreteValue.intValue(0);
                }
                return ConcreteValue.intValue(args[0].asReference().getIdentityHashCode());
            });

        registry.register("java/lang/Object", "getClass", "()Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                ObjectInstance classObj = ctx.getHeapManager().newObject("java/lang/Class");
                if (receiver != null) {
                    ObjectInstance nameStr = ctx.getHeapManager().internString(receiver.getClassName());
                    classObj.setField("java/lang/Class", "name", "Ljava/lang/String;", nameStr);
                }
                return ConcreteValue.reference(classObj);
            });

        registry.register("java/lang/Object", "hashCode", "()I",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    return ConcreteValue.intValue(0);
                }
                return ConcreteValue.intValue(receiver.getIdentityHashCode());
            });

        registry.register("java/lang/Object", "clone", "()Ljava/lang/Object;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "clone on null");
                }
                return ConcreteValue.reference(receiver);
            });

        registry.register("java/lang/Object", "notifyAll", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Object", "notify", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Object", "wait", "(J)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Float", "floatToRawIntBits", "(F)I",
            (receiver, args, ctx) -> {
                float val = args != null && args.length > 0 ? args[0].asFloat() : 0.0f;
                return ConcreteValue.intValue(Float.floatToRawIntBits(val));
            });

        registry.register("java/lang/Float", "intBitsToFloat", "(I)F",
            (receiver, args, ctx) -> {
                int val = args != null && args.length > 0 ? args[0].asInt() : 0;
                return ConcreteValue.floatValue(Float.intBitsToFloat(val));
            });

        registry.register("java/lang/Double", "doubleToRawLongBits", "(D)J",
            (receiver, args, ctx) -> {
                double val = args != null && args.length > 0 ? args[0].asDouble() : 0.0;
                return ConcreteValue.longValue(Double.doubleToRawLongBits(val));
            });

        registry.register("java/lang/Double", "longBitsToDouble", "(J)D",
            (receiver, args, ctx) -> {
                long val = args != null && args.length > 0 ? args[0].asLong() : 0L;
                return ConcreteValue.doubleValue(Double.longBitsToDouble(val));
            });

        registry.register("java/lang/Math", "sin", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.sin(args != null && args.length > 0 ? args[0].asDouble() : 0.0)));

        registry.register("java/lang/Math", "cos", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.cos(args != null && args.length > 0 ? args[0].asDouble() : 0.0)));

        registry.register("java/lang/Math", "tan", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.tan(args != null && args.length > 0 ? args[0].asDouble() : 0.0)));

        registry.register("java/lang/Math", "sqrt", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.sqrt(args != null && args.length > 0 ? args[0].asDouble() : 0.0)));

        registry.register("java/lang/Math", "log", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.log(args != null && args.length > 0 ? args[0].asDouble() : 0.0)));

        registry.register("java/lang/Math", "log10", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.log10(args != null && args.length > 0 ? args[0].asDouble() : 0.0)));

        registry.register("java/lang/Math", "exp", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.exp(args != null && args.length > 0 ? args[0].asDouble() : 0.0)));

        registry.register("java/lang/Math", "pow", "(DD)D",
            (receiver, args, ctx) -> {
                double base = args != null && args.length > 0 ? args[0].asDouble() : 0.0;
                double exp = args != null && args.length > 1 ? args[1].asDouble() : 0.0;
                return ConcreteValue.doubleValue(Math.pow(base, exp));
            });

        registry.register("java/lang/Math", "floor", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.floor(args != null && args.length > 0 ? args[0].asDouble() : 0.0)));

        registry.register("java/lang/Math", "ceil", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.ceil(args != null && args.length > 0 ? args[0].asDouble() : 0.0)));

        registry.register("java/lang/Math", "rint", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.rint(args != null && args.length > 0 ? args[0].asDouble() : 0.0)));

        registry.register("java/lang/Thread", "sleep", "(J)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Thread", "yield", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Thread", "interrupt0", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Thread", "isInterrupted", "(Z)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/Thread", "isAlive", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("java/lang/Thread", "start0", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Thread", "setPriority0", "(I)V",
            (receiver, args, ctx) -> null);

        registry.register("java/lang/Thread", "holdsLock", "(Ljava/lang/Object;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/Throwable", "fillInStackTrace", "(I)Ljava/lang/Throwable;",
            (receiver, args, ctx) -> ConcreteValue.reference(receiver));

        registry.register("java/lang/Throwable", "getStackTraceDepth", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/lang/Throwable", "getStackTraceElement", "(I)Ljava/lang/StackTraceElement;",
            (receiver, args, ctx) -> {
                ObjectInstance ste = ctx.getHeapManager().newObject("java/lang/StackTraceElement");
                return ConcreteValue.reference(ste);
            });

        registry.register("java/lang/reflect/Array", "newArray", "(Ljava/lang/Class;I)Ljava/lang/Object;",
            (receiver, args, ctx) -> {
                int length = args != null && args.length > 1 ? args[1].asInt() : 0;
                ArrayInstance arr = ctx.getHeapManager().newArray("Ljava/lang/Object;", length);
                return ConcreteValue.reference(arr);
            });

        registry.register("java/lang/reflect/Array", "getLength", "(Ljava/lang/Object;)I",
            (receiver, args, ctx) -> {
                if (args == null || args.length == 0 || args[0].isNull()) {
                    return ConcreteValue.intValue(0);
                }
                ObjectInstance obj = args[0].asReference();
                if (obj instanceof ArrayInstance) {
                    return ConcreteValue.intValue(((ArrayInstance) obj).getLength());
                }
                return ConcreteValue.intValue(0);
            });

        registry.register("java/security/AccessController", "doPrivileged", "(Ljava/security/PrivilegedAction;)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/security/AccessController", "doPrivileged", "(Ljava/security/PrivilegedExceptionAction;)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/security/AccessController", "getStackAccessControlContext", "()Ljava/security/AccessControlContext;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/reflect/Reflection", "getCallerClass", "()Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                ObjectInstance classObj = ctx.getHeapManager().newObject("java/lang/Class");
                return ConcreteValue.reference(classObj);
            });

        registry.register("jdk/internal/reflect/Reflection", "getClassAccessFlags", "(Ljava/lang/Class;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));
    }
}
