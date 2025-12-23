package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
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

    public static String methodKey(String owner, String name, String descriptor) {
        return owner + "." + name + descriptor;
    }

    public static String methodKey(MethodEntry method) {
        return methodKey(method.getOwnerName(), method.getName(), method.getDesc());
    }

    public void registerDefaults() {
        registerObjectHandlers();
        registerSystemHandlers();
        registerMathHandlers();
        registerFloatHandlers();
        registerDoubleHandlers();
        registerStringHandlers();
    }

    private void registerObjectHandlers() {
        register("java/lang/Object", "hashCode", "()I",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    return ConcreteValue.intValue(0);
                }
                return ConcreteValue.intValue(receiver.getIdentityHashCode());
            });

        register("java/lang/Object", "getClass", "()Ljava/lang/Class;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "getClass on null");
                }
                ObjectInstance classObj = ctx.getHeapManager().newObject("java/lang/Class");
                classObj.setField("java/lang/Class", "name", "Ljava/lang/String;",
                    ctx.createString(receiver.getClassName()));
                return ConcreteValue.reference(classObj);
            });

        register("java/lang/Object", "equals", "(Ljava/lang/Object;)Z",
            (receiver, args, ctx) -> {
                if (args == null || args.length == 0) {
                    return ConcreteValue.intValue(receiver == null ? 1 : 0);
                }
                ObjectInstance other = args[0].isNull() ? null : args[0].asReference();
                return ConcreteValue.intValue(receiver == other ? 1 : 0);
            });
    }

    private void registerSystemHandlers() {
        register("java/lang/System", "identityHashCode", "(Ljava/lang/Object;)I",
            (receiver, args, ctx) -> {
                if (args == null || args.length == 0 || args[0].isNull()) {
                    return ConcreteValue.intValue(0);
                }
                ObjectInstance obj = args[0].asReference();
                return ConcreteValue.intValue(obj.getIdentityHashCode());
            });

        register("java/lang/System", "currentTimeMillis", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(System.currentTimeMillis()));

        register("java/lang/System", "nanoTime", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(System.nanoTime()));

        register("java/lang/System", "arraycopy",
            "(Ljava/lang/Object;ILjava/lang/Object;II)V",
            (receiver, args, ctx) -> {
                if (args == null || args.length < 5) {
                    throw new NativeException("java/lang/IllegalArgumentException",
                        "Invalid arguments to arraycopy");
                }

                if (args[0].isNull() || args[2].isNull()) {
                    throw new NativeException("java/lang/NullPointerException",
                        "arraycopy with null array");
                }

                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int srcPos = args[1].asInt();
                ArrayInstance dest = (ArrayInstance) args[2].asReference();
                int destPos = args[3].asInt();
                int length = args[4].asInt();

                if (srcPos < 0 || destPos < 0 || length < 0 ||
                    srcPos + length > src.getLength() ||
                    destPos + length > dest.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException",
                        "Invalid arraycopy bounds");
                }

                for (int i = 0; i < length; i++) {
                    dest.set(destPos + i, src.get(srcPos + i));
                }

                return ConcreteValue.intValue(0);
            });
    }

    private void registerMathHandlers() {
        register("java/lang/Math", "abs", "(I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(Math.abs(args[0].asInt())));

        register("java/lang/Math", "abs", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(Math.abs(args[0].asLong())));

        register("java/lang/Math", "abs", "(F)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(Math.abs(args[0].asFloat())));

        register("java/lang/Math", "abs", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(Math.abs(args[0].asDouble())));

        register("java/lang/Math", "max", "(II)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(
                Math.max(args[0].asInt(), args[1].asInt())));

        register("java/lang/Math", "max", "(JJ)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(
                Math.max(args[0].asLong(), args[2].asLong())));

        register("java/lang/Math", "max", "(FF)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(
                Math.max(args[0].asFloat(), args[1].asFloat())));

        register("java/lang/Math", "max", "(DD)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.max(args[0].asDouble(), args[2].asDouble())));

        register("java/lang/Math", "min", "(II)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(
                Math.min(args[0].asInt(), args[1].asInt())));

        register("java/lang/Math", "min", "(JJ)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(
                Math.min(args[0].asLong(), args[2].asLong())));

        register("java/lang/Math", "min", "(FF)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(
                Math.min(args[0].asFloat(), args[1].asFloat())));

        register("java/lang/Math", "min", "(DD)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.min(args[0].asDouble(), args[2].asDouble())));

        register("java/lang/Math", "sqrt", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(Math.sqrt(args[0].asDouble())));

        register("java/lang/Math", "sin", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(Math.sin(args[0].asDouble())));

        register("java/lang/Math", "cos", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(Math.cos(args[0].asDouble())));

        register("java/lang/Math", "tan", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(Math.tan(args[0].asDouble())));
    }

    private void registerFloatHandlers() {
        register("java/lang/Float", "floatToRawIntBits", "(F)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(
                Float.floatToRawIntBits(args[0].asFloat())));

        register("java/lang/Float", "intBitsToFloat", "(I)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(
                Float.intBitsToFloat(args[0].asInt())));
    }

    private void registerDoubleHandlers() {
        register("java/lang/Double", "doubleToRawLongBits", "(D)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(
                Double.doubleToRawLongBits(args[0].asDouble())));

        register("java/lang/Double", "longBitsToDouble", "(J)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Double.longBitsToDouble(args[0].asLong())));
    }

    private void registerStringHandlers() {
        register("java/lang/String", "length", "()I",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException",
                        "length() on null String");
                }

                Object charArrayObj = receiver.getField("java/lang/String", "value", "[C");
                if (charArrayObj instanceof ArrayInstance) {
                    ArrayInstance charArray = (ArrayInstance) charArrayObj;
                    return ConcreteValue.intValue(charArray.getLength());
                }

                return ConcreteValue.intValue(0);
            });

        register("java/lang/String", "charAt", "(I)C",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException",
                        "charAt() on null String");
                }

                Object charArrayObj = receiver.getField("java/lang/String", "value", "[C");
                if (!(charArrayObj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalStateException",
                        "String has no char array");
                }

                ArrayInstance charArray = (ArrayInstance) charArrayObj;
                int index = args[0].asInt();

                if (index < 0 || index >= charArray.getLength()) {
                    throw new NativeException("java/lang/StringIndexOutOfBoundsException",
                        "Index: " + index);
                }

                char ch = charArray.getChar(index);
                return ConcreteValue.intValue(ch);
            });

        register("java/lang/String", "intern", "()Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException",
                        "intern() on null String");
                }
                return ConcreteValue.reference(receiver);
            });
    }
}
