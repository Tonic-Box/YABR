package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeContext;
import com.tonic.analysis.execution.invoke.NativeException;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class StringHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerStringHandlers(registry);
        registerBase64Handlers(registry);
        registerStringExtendedHandlers(registry);
        registerStringInternalHandlers(registry);
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

    private void registerStringHandlers(NativeRegistry registry) {
        registry.register("java/lang/String", "length", "()I",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException",
                        "length() on null String");
                }
                String str = ctx.getHeapManager().extractString(receiver);
                return ConcreteValue.intValue(str != null ? str.length() : 0);
            });

        registry.register("java/lang/String", "charAt", "(I)C",
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

        registry.register("java/lang/String", "intern", "()Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException",
                        "intern() on null String");
                }
                return ConcreteValue.reference(receiver);
            });

        registry.register("java/lang/String", "coder", "()B",
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

        registry.register("java/lang/String", "isLatin1", "()Z",
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

        registry.register("java/lang/String", "getBytes", "([BIB)V",
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

    private void registerBase64Handlers(NativeRegistry registry) {
        registry.register("java/util/Base64", "getDecoder", "()Ljava/util/Base64$Decoder;",
            (receiver, args, ctx) -> {
                ObjectInstance decoder = ctx.getHeapManager().newObject("java/util/Base64$Decoder");
                return ConcreteValue.reference(decoder);
            });

        registry.register("java/util/Base64", "getEncoder", "()Ljava/util/Base64$Encoder;",
            (receiver, args, ctx) -> {
                ObjectInstance encoder = ctx.getHeapManager().newObject("java/util/Base64$Encoder");
                return ConcreteValue.reference(encoder);
            });

        registry.register("java/util/Base64$Decoder", "decode", "(Ljava/lang/String;)[B",
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

        registry.register("java/util/Base64$Decoder", "decode", "([B)[B",
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

        registry.register("java/util/Base64$Encoder", "encode", "([B)[B",
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

        registry.register("java/util/Base64$Encoder", "encodeToString", "([B)Ljava/lang/String;",
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

    private void registerStringExtendedHandlers(NativeRegistry registry) {
        registry.register("java/lang/String", "getBytes", "()[B",
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

        registry.register("java/lang/String", "getBytes", "(Ljava/lang/String;)[B",
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

        registry.register("java/lang/String", "toCharArray", "()[C",
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

        registry.register("java/lang/String", "substring", "(I)Ljava/lang/String;",
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

        registry.register("java/lang/String", "substring", "(II)Ljava/lang/String;",
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

        registry.register("java/lang/String", "<init>", "([B)V",
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

        registry.register("java/lang/String", "<init>", "([BLjava/lang/String;)V",
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

        registry.register("java/lang/String", "<init>", "([C)V",
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

        registry.register("java/lang/String", "<init>", "(Ljava/lang/String;)V",
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

        registry.register("java/lang/String", "equals", "(Ljava/lang/Object;)Z",
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

        registry.register("java/lang/String", "hashCode", "()I",
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

        registry.register("java/lang/String", "isEmpty", "()Z",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "isEmpty() on null");
                }
                String str = ctx.getHeapManager().extractString(receiver);
                return ConcreteValue.intValue(str == null || str.isEmpty() ? 1 : 0);
            });

        registry.register("java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;",
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

        registry.register("java/lang/String", "valueOf", "(I)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String result = String.valueOf(args[0].asInt());
                return ConcreteValue.reference(ctx.getHeapManager().internString(result));
            });

        registry.register("java/lang/String", "valueOf", "(J)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String result = String.valueOf(args[0].asLong());
                return ConcreteValue.reference(ctx.getHeapManager().internString(result));
            });

        registry.register("java/lang/String", "valueOf", "(Z)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String result = String.valueOf(args[0].asInt() != 0);
                return ConcreteValue.reference(ctx.getHeapManager().internString(result));
            });

        registry.register("java/lang/String", "valueOf", "(C)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String result = String.valueOf((char) args[0].asInt());
                return ConcreteValue.reference(ctx.getHeapManager().internString(result));
            });
    }

    private void registerStringInternalHandlers(NativeRegistry registry) {
        registry.register("java/lang/StringLatin1", "inflate", "([BI[BI)V",
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

        registry.register("java/lang/StringLatin1", "inflate", "([BII[BI)V",
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

        registry.register("java/lang/StringUTF16", "putChar", "([BII)V",
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

        registry.register("java/lang/StringUTF16", "getChar", "([BI)C",
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

        registry.register("java/lang/StringUTF16", "newBytesFor", "(I)[B",
            (receiver, args, ctx) -> {
                int len = args[0].asInt();
                if (len < 0) {
                    throw new NativeException("java/lang/NegativeArraySizeException", "len=" + len);
                }
                ArrayInstance arr = ctx.getHeapManager().newArray("B", len << 1);
                return ConcreteValue.reference(arr);
            });

        registry.register("java/lang/StringLatin1", "newString", "([BII)Ljava/lang/String;",
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

        registry.register("java/lang/Integer", "stringSize", "(I)I",
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

        registry.register("java/lang/Integer", "getChars", "(II[B)V",
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

        registry.register("java/lang/AbstractStringBuilder", "getCoder", "()B",
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

        registry.register("java/lang/AbstractStringBuilder", "isLatin1", "()Z",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    return ConcreteValue.intValue(0);
                }
                if (!ctx.getHeapManager().isUsingCompactStrings()) {
                    return ConcreteValue.intValue(0);
                }
                Object coder = receiver.getField("java/lang/AbstractStringBuilder", "coder", "B");
                int coderVal = 0;
                if (coder instanceof Byte) {
                    coderVal = (Byte) coder;
                } else if (coder instanceof Integer) {
                    coderVal = (Integer) coder;
                }
                return ConcreteValue.intValue(coderVal == 0 ? 1 : 0);
            });

        registry.register("java/lang/AbstractStringBuilder", "inflate", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "inflate on null");
                }
                Object coderObj = receiver.getField("java/lang/AbstractStringBuilder", "coder", "B");
                int coder = 0;
                if (coderObj instanceof Byte) {
                    coder = (Byte) coderObj;
                } else if (coderObj instanceof Integer) {
                    coder = (Integer) coderObj;
                }
                if (coder != 0) {
                    return ConcreteValue.nullRef();
                }
                Object valueObj = receiver.getField("java/lang/AbstractStringBuilder", "value", "[B");
                if (!(valueObj instanceof ArrayInstance)) {
                    return ConcreteValue.nullRef();
                }
                ArrayInstance oldValue = (ArrayInstance) valueObj;
                Object countObj = receiver.getField("java/lang/AbstractStringBuilder", "count", "I");
                int count = 0;
                if (countObj instanceof Integer) {
                    count = (Integer) countObj;
                }
                ArrayInstance newValue = ctx.getHeapManager().newArray("B", oldValue.getLength() * 2);
                for (int i = 0; i < count; i++) {
                    byte b = oldValue.getByte(i);
                    newValue.setByte(i * 2, b);
                    newValue.setByte(i * 2 + 1, (byte) 0);
                }
                receiver.setField("java/lang/AbstractStringBuilder", "value", "[B", newValue);
                receiver.setField("java/lang/AbstractStringBuilder", "coder", "B", (byte) 1);
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/AbstractStringBuilder", "putStringAt", "(ILjava/lang/String;)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "putStringAt on null");
                }
                int index = args[0].asInt();
                if (args[1].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "putStringAt with null string");
                }
                ObjectInstance str = args[1].asReference();
                String strValue = ctx.getHeapManager().extractString(str);
                if (strValue == null) {
                    return ConcreteValue.nullRef();
                }
                Object valueObj = receiver.getField("java/lang/AbstractStringBuilder", "value", "[B");
                if (!(valueObj instanceof ArrayInstance)) {
                    return ConcreteValue.nullRef();
                }
                ArrayInstance value = (ArrayInstance) valueObj;
                Object coderObj = receiver.getField("java/lang/AbstractStringBuilder", "coder", "B");
                int coder = 0;
                if (coderObj instanceof Byte) {
                    coder = (Byte) coderObj;
                } else if (coderObj instanceof Integer) {
                    coder = (Integer) coderObj;
                }
                if (coder == 0) {
                    for (int i = 0; i < strValue.length(); i++) {
                        value.setByte(index + i, (byte) strValue.charAt(i));
                    }
                } else {
                    for (int i = 0; i < strValue.length(); i++) {
                        char c = strValue.charAt(i);
                        value.setByte((index + i) * 2, (byte) (c & 0xFF));
                        value.setByte((index + i) * 2 + 1, (byte) ((c >> 8) & 0xFF));
                    }
                }
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/AbstractStringBuilder", "ensureCapacityInternal", "(I)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "ensureCapacityInternal on null");
                }
                int minimumCapacity = args[0].asInt();
                Object valueObj = receiver.getField("java/lang/AbstractStringBuilder", "value", "[B");
                if (!(valueObj instanceof ArrayInstance)) {
                    ArrayInstance newValue = ctx.getHeapManager().newArray("B", Math.max(16, minimumCapacity));
                    receiver.setField("java/lang/AbstractStringBuilder", "value", "[B", newValue);
                    return ConcreteValue.nullRef();
                }
                ArrayInstance oldValue = (ArrayInstance) valueObj;
                Object coderObj = receiver.getField("java/lang/AbstractStringBuilder", "coder", "B");
                int coder = 0;
                if (coderObj instanceof Byte) {
                    coder = (Byte) coderObj;
                } else if (coderObj instanceof Integer) {
                    coder = (Integer) coderObj;
                }
                int oldCapacity = oldValue.getLength() >> coder;
                if (minimumCapacity > oldCapacity) {
                    int newCapacity = Math.max(minimumCapacity, oldCapacity * 2 + 2);
                    ArrayInstance newValue = ctx.getHeapManager().newArray("B", newCapacity << coder);
                    for (int i = 0; i < oldValue.getLength(); i++) {
                        newValue.setByte(i, oldValue.getByte(i));
                    }
                    receiver.setField("java/lang/AbstractStringBuilder", "value", "[B", newValue);
                }
                return ConcreteValue.nullRef();
            });
    }
}
