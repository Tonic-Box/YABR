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
            System.out.println("[NativeRegistry] NO HANDLER for: " + key);
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

    public void registerDefaults() {
        registerObjectHandlers();
        registerSystemHandlers();
        registerMathHandlers();
        registerFloatHandlers();
        registerDoubleHandlers();
        registerStringHandlers();
        registerBase64Handlers();
        registerStringExtendedHandlers();
        registerExceptionHandlers();
    }

    private void registerExceptionHandlers() {
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
            register(exClass, "<init>", "()V",
                (receiver, args, ctx) -> ConcreteValue.nullRef());

            register(exClass, "<init>", "(Ljava/lang/String;)V",
                (receiver, args, ctx) -> {
                    if (receiver != null && args != null && args.length > 0 && !args[0].isNull()) {
                        receiver.setField(exClass, "detailMessage", "Ljava/lang/String;", args[0].asReference());
                    }
                    return ConcreteValue.nullRef();
                });

            register(exClass, "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V",
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

            register(exClass, "<init>", "(Ljava/lang/Throwable;)V",
                (receiver, args, ctx) -> {
                    if (receiver != null && args != null && args.length > 0 && !args[0].isNull()) {
                        receiver.setField(exClass, "cause", "Ljava/lang/Throwable;", args[0].asReference());
                    }
                    return ConcreteValue.nullRef();
                });

            register(exClass, "getMessage", "()Ljava/lang/String;",
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

            register(exClass, "getCause", "()Ljava/lang/Throwable;",
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

    private void registerBase64Handlers() {
        register("java/util/Base64", "getDecoder", "()Ljava/util/Base64$Decoder;",
            (receiver, args, ctx) -> {
                ObjectInstance decoder = ctx.getHeapManager().newObject("java/util/Base64$Decoder");
                return ConcreteValue.reference(decoder);
            });

        register("java/util/Base64", "getEncoder", "()Ljava/util/Base64$Encoder;",
            (receiver, args, ctx) -> {
                ObjectInstance encoder = ctx.getHeapManager().newObject("java/util/Base64$Encoder");
                return ConcreteValue.reference(encoder);
            });

        register("java/util/Base64$Decoder", "decode", "(Ljava/lang/String;)[B",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Input string is null");
                }
                ObjectInstance strObj = args[0].asReference();
                String encoded = ctx.getHeapManager().extractString(strObj);
                if (encoded == null) {
                    throw new NativeException("java/lang/NullPointerException", "Cannot extract string");
                }
                byte[] decoded = java.util.Base64.getDecoder().decode(encoded);
                ArrayInstance result = ctx.getHeapManager().newArray("B", decoded.length);
                for (int i = 0; i < decoded.length; i++) {
                    result.setByte(i, decoded[i]);
                }
                return ConcreteValue.reference(result);
            });

        register("java/util/Base64$Decoder", "decode", "([B)[B",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Input array is null");
                }
                ArrayInstance inputArray = (ArrayInstance) args[0].asReference();
                byte[] input = new byte[inputArray.getLength()];
                for (int i = 0; i < input.length; i++) {
                    input[i] = inputArray.getByte(i);
                }
                byte[] decoded = java.util.Base64.getDecoder().decode(input);
                ArrayInstance result = ctx.getHeapManager().newArray("B", decoded.length);
                for (int i = 0; i < decoded.length; i++) {
                    result.setByte(i, decoded[i]);
                }
                return ConcreteValue.reference(result);
            });

        register("java/util/Base64$Encoder", "encode", "([B)[B",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Input array is null");
                }
                ArrayInstance inputArray = (ArrayInstance) args[0].asReference();
                byte[] input = new byte[inputArray.getLength()];
                for (int i = 0; i < input.length; i++) {
                    input[i] = inputArray.getByte(i);
                }
                byte[] encoded = java.util.Base64.getEncoder().encode(input);
                ArrayInstance result = ctx.getHeapManager().newArray("B", encoded.length);
                for (int i = 0; i < encoded.length; i++) {
                    result.setByte(i, encoded[i]);
                }
                return ConcreteValue.reference(result);
            });

        register("java/util/Base64$Encoder", "encodeToString", "([B)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Input array is null");
                }
                ArrayInstance inputArray = (ArrayInstance) args[0].asReference();
                byte[] input = new byte[inputArray.getLength()];
                for (int i = 0; i < input.length; i++) {
                    input[i] = inputArray.getByte(i);
                }
                String encoded = java.util.Base64.getEncoder().encodeToString(input);
                return ConcreteValue.reference(ctx.getHeapManager().internString(encoded));
            });
    }

    private void registerStringExtendedHandlers() {
        register("java/lang/String", "getBytes", "()[B",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "getBytes() on null");
                }
                String str = ctx.getHeapManager().extractString(receiver);
                if (str == null) {
                    throw new NativeException("java/lang/NullPointerException", "Cannot extract string");
                }
                byte[] bytes = str.getBytes();
                ArrayInstance result = ctx.getHeapManager().newArray("B", bytes.length);
                for (int i = 0; i < bytes.length; i++) {
                    result.setByte(i, bytes[i]);
                }
                return ConcreteValue.reference(result);
            });

        register("java/lang/String", "getBytes", "(Ljava/lang/String;)[B",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "getBytes() on null");
                }
                String str = ctx.getHeapManager().extractString(receiver);
                if (str == null) {
                    throw new NativeException("java/lang/NullPointerException", "Cannot extract string");
                }
                String charset = ctx.getHeapManager().extractString(args[0].asReference());
                try {
                    byte[] bytes = str.getBytes(charset);
                    ArrayInstance result = ctx.getHeapManager().newArray("B", bytes.length);
                    for (int i = 0; i < bytes.length; i++) {
                        result.setByte(i, bytes[i]);
                    }
                    return ConcreteValue.reference(result);
                } catch (java.io.UnsupportedEncodingException e) {
                    throw new NativeException("java/io/UnsupportedEncodingException", charset);
                }
            });

        register("java/lang/String", "toCharArray", "()[C",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "toCharArray() on null");
                }
                String str = ctx.getHeapManager().extractString(receiver);
                if (str == null) {
                    throw new NativeException("java/lang/NullPointerException", "Cannot extract string");
                }
                char[] chars = str.toCharArray();
                ArrayInstance result = ctx.getHeapManager().newArray("C", chars.length);
                for (int i = 0; i < chars.length; i++) {
                    result.setChar(i, chars[i]);
                }
                return ConcreteValue.reference(result);
            });

        register("java/lang/String", "substring", "(I)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "substring() on null");
                }
                String str = ctx.getHeapManager().extractString(receiver);
                if (str == null) {
                    throw new NativeException("java/lang/NullPointerException", "Cannot extract string");
                }
                int beginIndex = args[0].asInt();
                String sub = str.substring(beginIndex);
                return ConcreteValue.reference(ctx.getHeapManager().internString(sub));
            });

        register("java/lang/String", "substring", "(II)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "substring() on null");
                }
                String str = ctx.getHeapManager().extractString(receiver);
                if (str == null) {
                    throw new NativeException("java/lang/NullPointerException", "Cannot extract string");
                }
                int beginIndex = args[0].asInt();
                int endIndex = args[1].asInt();
                String sub = str.substring(beginIndex, endIndex);
                return ConcreteValue.reference(ctx.getHeapManager().internString(sub));
            });

        register("java/lang/String", "<init>", "([B)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "String init on null");
                }
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Null byte array");
                }
                ArrayInstance byteArray = (ArrayInstance) args[0].asReference();
                byte[] bytes = new byte[byteArray.getLength()];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = byteArray.getByte(i);
                }
                String str = new String(bytes);
                char[] chars = str.toCharArray();
                ArrayInstance charArray = ctx.getHeapManager().newArray("C", chars.length);
                for (int i = 0; i < chars.length; i++) {
                    charArray.setChar(i, chars[i]);
                }
                receiver.setField("java/lang/String", "value", "[C", charArray);
                return ConcreteValue.nullRef();
            });

        register("java/lang/String", "<init>", "([BLjava/lang/String;)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "String init on null");
                }
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Null byte array");
                }
                ArrayInstance byteArray = (ArrayInstance) args[0].asReference();
                String charset = ctx.getHeapManager().extractString(args[1].asReference());
                byte[] bytes = new byte[byteArray.getLength()];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = byteArray.getByte(i);
                }
                try {
                    String str = new String(bytes, charset);
                    char[] chars = str.toCharArray();
                    ArrayInstance charArray = ctx.getHeapManager().newArray("C", chars.length);
                    for (int i = 0; i < chars.length; i++) {
                        charArray.setChar(i, chars[i]);
                    }
                    receiver.setField("java/lang/String", "value", "[C", charArray);
                    return ConcreteValue.nullRef();
                } catch (java.io.UnsupportedEncodingException e) {
                    throw new NativeException("java/io/UnsupportedEncodingException", charset);
                }
            });

        register("java/lang/String", "<init>", "([C)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "String init on null");
                }
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Null char array");
                }
                ArrayInstance srcArray = (ArrayInstance) args[0].asReference();
                int len = srcArray.getLength();
                ArrayInstance charArray = ctx.getHeapManager().newArray("C", len);
                for (int i = 0; i < len; i++) {
                    charArray.setChar(i, srcArray.getChar(i));
                }
                receiver.setField("java/lang/String", "value", "[C", charArray);
                return ConcreteValue.nullRef();
            });

        register("java/lang/String", "equals", "(Ljava/lang/Object;)Z",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    return ConcreteValue.intValue(args[0].isNull() ? 1 : 0);
                }
                if (args[0].isNull()) {
                    return ConcreteValue.intValue(0);
                }
                ObjectInstance other = args[0].asReference();
                if (!"java/lang/String".equals(other.getClassName())) {
                    return ConcreteValue.intValue(0);
                }
                String s1 = ctx.getHeapManager().extractString(receiver);
                String s2 = ctx.getHeapManager().extractString(other);
                return ConcreteValue.intValue(java.util.Objects.equals(s1, s2) ? 1 : 0);
            });

        register("java/lang/String", "hashCode", "()I",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "hashCode() on null");
                }
                String str = ctx.getHeapManager().extractString(receiver);
                if (str == null) {
                    return ConcreteValue.intValue(0);
                }
                return ConcreteValue.intValue(str.hashCode());
            });

        register("java/lang/String", "isEmpty", "()Z",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "isEmpty() on null");
                }
                String str = ctx.getHeapManager().extractString(receiver);
                return ConcreteValue.intValue(str == null || str.isEmpty() ? 1 : 0);
            });

        register("java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "concat() on null");
                }
                String s1 = ctx.getHeapManager().extractString(receiver);
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "concat with null");
                }
                String s2 = ctx.getHeapManager().extractString(args[0].asReference());
                String result = s1 + s2;
                return ConcreteValue.reference(ctx.getHeapManager().internString(result));
            });

        register("java/lang/String", "valueOf", "(I)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String result = String.valueOf(args[0].asInt());
                return ConcreteValue.reference(ctx.getHeapManager().internString(result));
            });

        register("java/lang/String", "valueOf", "(J)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String result = String.valueOf(args[0].asLong());
                return ConcreteValue.reference(ctx.getHeapManager().internString(result));
            });

        register("java/lang/String", "valueOf", "(Z)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String result = String.valueOf(args[0].asInt() != 0);
                return ConcreteValue.reference(ctx.getHeapManager().internString(result));
            });

        register("java/lang/String", "valueOf", "(C)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String result = String.valueOf((char) args[0].asInt());
                return ConcreteValue.reference(ctx.getHeapManager().internString(result));
            });
    }
}
