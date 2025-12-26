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

    private static void copyStringFields(ObjectInstance src, ObjectInstance dst, NativeContext ctx) {
        Object byteValue = src.getField("java/lang/String", "value", "[B");
        if (byteValue instanceof ArrayInstance) {
            dst.setField("java/lang/String", "value", "[B", byteValue);
            Object coder = src.getField("java/lang/String", "coder", "B");
            if (coder != null) {
                dst.setField("java/lang/String", "coder", "B", coder);
            }
        } else {
            Object charValue = src.getField("java/lang/String", "value", "[C");
            if (charValue instanceof ArrayInstance) {
                dst.setField("java/lang/String", "value", "[C", charValue);
            }
        }
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
        registerArraysHandlers();
        registerClassHandlers();
        registerStringInternalHandlers();
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
                String str = ctx.getHeapManager().extractString(receiver);
                return ConcreteValue.intValue(str != null ? str.length() : 0);
            });

        register("java/lang/String", "charAt", "(I)C",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException",
                        "charAt() on null String");
                }
                String str = ctx.getHeapManager().extractString(receiver);
                if (str == null) {
                    throw new NativeException("java/lang/IllegalStateException",
                        "Cannot extract string");
                }
                int index = args[0].asInt();
                if (index < 0 || index >= str.length()) {
                    throw new NativeException("java/lang/StringIndexOutOfBoundsException",
                        "Index: " + index);
                }
                return ConcreteValue.intValue(str.charAt(index));
            });

        register("java/lang/String", "intern", "()Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException",
                        "intern() on null String");
                }
                return ConcreteValue.reference(receiver);
            });

        register("java/lang/String", "coder", "()B",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "coder() on null");
                }
                Object coder = receiver.getField("java/lang/String", "coder", "B");
                if (coder instanceof Byte) {
                    return ConcreteValue.intValue((Byte) coder);
                } else if (coder instanceof Integer) {
                    return ConcreteValue.intValue((Integer) coder);
                }
                return ConcreteValue.intValue(0);
            });

        register("java/lang/String", "isLatin1", "()Z",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "isLatin1() on null");
                }
                Object coder = receiver.getField("java/lang/String", "coder", "B");
                int coderVal = 0;
                if (coder instanceof Byte) {
                    coderVal = (Byte) coder;
                } else if (coder instanceof Integer) {
                    coderVal = (Integer) coder;
                }
                return ConcreteValue.intValue(coderVal == 0 ? 1 : 0);
            });

        register("java/lang/String", "getBytes", "([BIB)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "getBytes on null");
                }
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "dst is null");
                }
                ArrayInstance dst = (ArrayInstance) args[0].asReference();
                int dstBegin = args[1].asInt();
                int dstCoder = args[2].asInt();

                Object srcValue = receiver.getField("java/lang/String", "value", "[B");
                if (!(srcValue instanceof ArrayInstance)) {
                    srcValue = receiver.getField("java/lang/String", "value", "[C");
                    if (srcValue instanceof ArrayInstance) {
                        ArrayInstance charArray = (ArrayInstance) srcValue;
                        int len = charArray.getLength();
                        if (dstCoder == 0) {
                            for (int i = 0; i < len; i++) {
                                dst.setByte(dstBegin + i, (byte) charArray.getChar(i));
                            }
                        } else {
                            for (int i = 0; i < len; i++) {
                                char c = charArray.getChar(i);
                                dst.setByte((dstBegin + i) * 2, (byte) (c & 0xFF));
                                dst.setByte((dstBegin + i) * 2 + 1, (byte) ((c >> 8) & 0xFF));
                            }
                        }
                    }
                    return ConcreteValue.nullRef();
                }

                ArrayInstance srcArray = (ArrayInstance) srcValue;
                Object srcCoderObj = receiver.getField("java/lang/String", "coder", "B");
                int srcCoder = 0;
                if (srcCoderObj instanceof Byte) {
                    srcCoder = (Byte) srcCoderObj;
                } else if (srcCoderObj instanceof Integer) {
                    srcCoder = (Integer) srcCoderObj;
                }

                if (srcCoder == dstCoder) {
                    int len = srcArray.getLength();
                    int dstOff = dstBegin << dstCoder;
                    for (int i = 0; i < len; i++) {
                        dst.setByte(dstOff + i, srcArray.getByte(i));
                    }
                } else if (srcCoder == 0 && dstCoder == 1) {
                    int len = srcArray.getLength();
                    for (int i = 0; i < len; i++) {
                        byte b = srcArray.getByte(i);
                        dst.setByte((dstBegin + i) * 2, b);
                        dst.setByte((dstBegin + i) * 2 + 1, (byte) 0);
                    }
                } else {
                    int len = srcArray.getLength() / 2;
                    for (int i = 0; i < len; i++) {
                        dst.setByte(dstBegin + i, srcArray.getByte(i * 2));
                    }
                }
                return ConcreteValue.nullRef();
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
                ObjectInstance interned = ctx.getHeapManager().internString(str);
                copyStringFields(interned, receiver, ctx);
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
                    ObjectInstance interned = ctx.getHeapManager().internString(str);
                    copyStringFields(interned, receiver, ctx);
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
                char[] chars = new char[len];
                for (int i = 0; i < len; i++) {
                    chars[i] = srcArray.getChar(i);
                }
                String str = new String(chars);
                ObjectInstance interned = ctx.getHeapManager().internString(str);
                copyStringFields(interned, receiver, ctx);
                return ConcreteValue.nullRef();
            });

        register("java/lang/String", "<init>", "(Ljava/lang/String;)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "String init on null");
                }
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Null source string");
                }
                ObjectInstance src = args[0].asReference();
                copyStringFields(src, receiver, ctx);
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

    private void registerArraysHandlers() {
        register("java/util/Arrays", "copyOf", "([II)[I",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "copyOf null array");
                }
                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int newLen = args[1].asInt();
                ArrayInstance result = ctx.getHeapManager().newArray("I", newLen);
                int copyLen = Math.min(src.getLength(), newLen);
                for (int i = 0; i < copyLen; i++) {
                    result.set(i, src.get(i));
                }
                return ConcreteValue.reference(result);
            });

        register("java/util/Arrays", "copyOf", "([BI)[B",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "copyOf null array");
                }
                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int newLen = args[1].asInt();
                ArrayInstance result = ctx.getHeapManager().newArray("B", newLen);
                int copyLen = Math.min(src.getLength(), newLen);
                for (int i = 0; i < copyLen; i++) {
                    result.setByte(i, src.getByte(i));
                }
                return ConcreteValue.reference(result);
            });

        register("java/util/Arrays", "copyOf", "([CI)[C",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "copyOf null array");
                }
                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int newLen = args[1].asInt();
                ArrayInstance result = ctx.getHeapManager().newArray("C", newLen);
                int copyLen = Math.min(src.getLength(), newLen);
                for (int i = 0; i < copyLen; i++) {
                    result.setChar(i, src.getChar(i));
                }
                return ConcreteValue.reference(result);
            });

        register("java/util/Arrays", "copyOfRange", "([BII)[B",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "copyOfRange null array");
                }
                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int from = args[1].asInt();
                int to = args[2].asInt();
                int newLen = to - from;
                ArrayInstance result = ctx.getHeapManager().newArray("B", newLen);
                int copyLen = Math.min(src.getLength() - from, newLen);
                for (int i = 0; i < copyLen; i++) {
                    result.setByte(i, src.getByte(from + i));
                }
                return ConcreteValue.reference(result);
            });

        register("java/util/Arrays", "fill", "([BB)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "fill null array");
                }
                ArrayInstance arr = (ArrayInstance) args[0].asReference();
                byte val = (byte) args[1].asInt();
                for (int i = 0; i < arr.getLength(); i++) {
                    arr.setByte(i, val);
                }
                return ConcreteValue.nullRef();
            });

        register("java/util/Arrays", "fill", "([II)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "fill null array");
                }
                ArrayInstance arr = (ArrayInstance) args[0].asReference();
                int val = args[1].asInt();
                for (int i = 0; i < arr.getLength(); i++) {
                    arr.set(i, val);
                }
                return ConcreteValue.nullRef();
            });
    }

    private void registerClassHandlers() {
        register("java/lang/Class", "isArray", "()Z",
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

        register("java/lang/Class", "isPrimitive", "()Z",
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

        register("java/lang/Class", "getName", "()Ljava/lang/String;",
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

        register("java/lang/Class", "desiredAssertionStatus", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));
    }

    private void registerStringInternalHandlers() {
        register("java/lang/StringLatin1", "inflate", "([BI[BI)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull() || args[2].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "inflate null array");
                }
                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int srcOff = args[1].asInt();
                ArrayInstance dst = (ArrayInstance) args[2].asReference();
                int dstOff = args[3].asInt();
                int len = args[4].asInt();
                for (int i = 0; i < len; i++) {
                    byte b = src.getByte(srcOff + i);
                    dst.setByte((dstOff + i) * 2, (byte) (b & 0xFF));
                    dst.setByte((dstOff + i) * 2 + 1, (byte) 0);
                }
                return ConcreteValue.nullRef();
            });

        register("java/lang/StringLatin1", "inflate", "([BII[BI)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull() || args[3].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "inflate null array");
                }
                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int srcOff = args[1].asInt();
                int srcLen = args[2].asInt();
                ArrayInstance dst = (ArrayInstance) args[3].asReference();
                int dstOff = args[4].asInt();
                for (int i = 0; i < srcLen; i++) {
                    byte b = src.getByte(srcOff + i);
                    dst.setByte((dstOff + i) * 2, (byte) (b & 0xFF));
                    dst.setByte((dstOff + i) * 2 + 1, (byte) 0);
                }
                return ConcreteValue.nullRef();
            });

        register("java/lang/StringUTF16", "putChar", "([BII)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "putChar null array");
                }
                ArrayInstance arr = (ArrayInstance) args[0].asReference();
                int index = args[1].asInt();
                int c = args[2].asInt();
                arr.setByte(index * 2, (byte) (c & 0xFF));
                arr.setByte(index * 2 + 1, (byte) ((c >> 8) & 0xFF));
                return ConcreteValue.nullRef();
            });

        register("java/lang/StringUTF16", "getChar", "([BI)C",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "getChar null array");
                }
                ArrayInstance arr = (ArrayInstance) args[0].asReference();
                int index = args[1].asInt();
                int lo = arr.getByte(index * 2) & 0xFF;
                int hi = arr.getByte(index * 2 + 1) & 0xFF;
                return ConcreteValue.intValue((char) (lo | (hi << 8)));
            });

        register("java/lang/StringUTF16", "newBytesFor", "(I)[B",
            (receiver, args, ctx) -> {
                int len = args[0].asInt();
                if (len < 0) {
                    throw new NativeException("java/lang/NegativeArraySizeException", "len=" + len);
                }
                ArrayInstance arr = ctx.getHeapManager().newArray("B", len << 1);
                return ConcreteValue.reference(arr);
            });

        register("java/lang/StringLatin1", "newString", "([BII)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "newString null array");
                }
                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int off = args[1].asInt();
                int len = args[2].asInt();
                byte[] bytes = new byte[len];
                for (int i = 0; i < len; i++) {
                    bytes[i] = src.getByte(off + i);
                }
                String str = new String(bytes, 0, len, java.nio.charset.StandardCharsets.ISO_8859_1);
                return ConcreteValue.reference(ctx.getHeapManager().internString(str));
            });

        register("java/lang/Integer", "stringSize", "(I)I",
            (receiver, args, ctx) -> {
                int x = args[0].asInt();
                int d = 1;
                if (x >= 0) {
                    d = 0;
                    x = -x;
                }
                int p = -10;
                for (int i = 1; i < 10; i++) {
                    if (x > p) return ConcreteValue.intValue(i + d);
                    p *= 10;
                }
                return ConcreteValue.intValue(10 + d);
            });

        register("java/lang/Integer", "getChars", "(II[B)V",
            (receiver, args, ctx) -> {
                int i = args[0].asInt();
                int index = args[1].asInt();
                ArrayInstance buf = (ArrayInstance) args[2].asReference();
                String s = Integer.toString(i);
                for (int j = 0; j < s.length(); j++) {
                    buf.setByte((index - s.length() + j) * 2, (byte) s.charAt(j));
                    buf.setByte((index - s.length() + j) * 2 + 1, (byte) 0);
                }
                return ConcreteValue.nullRef();
            });

        register("java/lang/AbstractStringBuilder", "getCoder", "()B",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "getCoder on null");
                }
                Object coder = receiver.getField("java/lang/AbstractStringBuilder", "coder", "B");
                if (coder instanceof Byte) {
                    return ConcreteValue.intValue((Byte) coder);
                } else if (coder instanceof Integer) {
                    return ConcreteValue.intValue((Integer) coder);
                }
                return ConcreteValue.intValue(0);
            });
    }
}
