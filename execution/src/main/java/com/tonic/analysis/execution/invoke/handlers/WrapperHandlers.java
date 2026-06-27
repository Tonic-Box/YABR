package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeException;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class WrapperHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registry.register("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;",
            (receiver, args, ctx) -> {
                int value = args[0].asInt();
                ObjectInstance intObj = ctx.getHeapManager().newObject("java/lang/Integer");
                intObj.setField("java/lang/Integer", "value", "I", value);
                return ConcreteValue.reference(intObj);
            });

        registry.register("java/lang/Integer", "intValue", "()I",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "intValue on null");
                }
                Object value = receiver.getField("java/lang/Integer", "value", "I");
                return ConcreteValue.intValue(value instanceof Integer ? (Integer) value : 0);
            });

        registry.register("java/lang/Integer", "<init>", "(I)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "Integer init on null");
                }
                receiver.setField("java/lang/Integer", "value", "I", args[0].asInt());
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;",
            (receiver, args, ctx) -> {
                long value = args[0].asLong();
                ObjectInstance longObj = ctx.getHeapManager().newObject("java/lang/Long");
                longObj.setField("java/lang/Long", "value", "J", value);
                return ConcreteValue.reference(longObj);
            });

        registry.register("java/lang/Long", "longValue", "()J",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "longValue on null");
                }
                Object value = receiver.getField("java/lang/Long", "value", "J");
                return ConcreteValue.longValue(value instanceof Long ? (Long) value : 0L);
            });

        registry.register("java/lang/Long", "<init>", "(J)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "Long init on null");
                }
                receiver.setField("java/lang/Long", "value", "J", args[0].asLong());
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;",
            (receiver, args, ctx) -> {
                byte value = (byte) args[0].asInt();
                ObjectInstance byteObj = ctx.getHeapManager().newObject("java/lang/Byte");
                byteObj.setField("java/lang/Byte", "value", "B", value);
                return ConcreteValue.reference(byteObj);
            });

        registry.register("java/lang/Byte", "<init>", "(B)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "Byte init on null");
                }
                receiver.setField("java/lang/Byte", "value", "B", (byte) args[0].asInt());
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/Short", "valueOf", "(S)Ljava/lang/Short;",
            (receiver, args, ctx) -> {
                short value = (short) args[0].asInt();
                ObjectInstance shortObj = ctx.getHeapManager().newObject("java/lang/Short");
                shortObj.setField("java/lang/Short", "value", "S", value);
                return ConcreteValue.reference(shortObj);
            });

        registry.register("java/lang/Short", "<init>", "(S)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "Short init on null");
                }
                receiver.setField("java/lang/Short", "value", "S", (short) args[0].asInt());
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/Character", "valueOf", "(C)Ljava/lang/Character;",
            (receiver, args, ctx) -> {
                char value = (char) args[0].asInt();
                ObjectInstance charObj = ctx.getHeapManager().newObject("java/lang/Character");
                charObj.setField("java/lang/Character", "value", "C", value);
                return ConcreteValue.reference(charObj);
            });

        registry.register("java/lang/Character", "<init>", "(C)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "Character init on null");
                }
                receiver.setField("java/lang/Character", "value", "C", (char) args[0].asInt());
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;",
            (receiver, args, ctx) -> {
                boolean value = args[0].asInt() != 0;
                ObjectInstance boolObj = ctx.getHeapManager().newObject("java/lang/Boolean");
                boolObj.setField("java/lang/Boolean", "value", "Z", value ? 1 : 0);
                return ConcreteValue.reference(boolObj);
            });

        registry.register("java/lang/Boolean", "<init>", "(Z)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "Boolean init on null");
                }
                receiver.setField("java/lang/Boolean", "value", "Z", args[0].asInt() != 0 ? 1 : 0);
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/Number", "<init>", "()V",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/lang/StringBuffer", "<init>", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "StringBuffer init on null");
                }
                ArrayInstance value = ctx.getHeapManager().newArray("B", 16);
                receiver.setField("java/lang/AbstractStringBuilder", "value", "[B", value);
                receiver.setField("java/lang/AbstractStringBuilder", "coder", "B", (byte) 0);
                receiver.setField("java/lang/AbstractStringBuilder", "count", "I", 0);
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/StringBuffer", "<init>", "(Ljava/lang/String;)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "StringBuffer init on null");
                }
                String str = "";
                if (!args[0].isNull()) {
                    str = ctx.getHeapManager().extractString(args[0].asReference());
                    if (str == null) str = "";
                }
                int capacity = str.length() + 16;
                ArrayInstance value = ctx.getHeapManager().newArray("B", capacity);
                for (int i = 0; i < str.length(); i++) {
                    value.setByte(i, (byte) str.charAt(i));
                }
                receiver.setField("java/lang/AbstractStringBuilder", "value", "[B", value);
                receiver.setField("java/lang/AbstractStringBuilder", "coder", "B", (byte) 0);
                receiver.setField("java/lang/AbstractStringBuilder", "count", "I", str.length());
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/StringBuilder", "<init>", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "StringBuilder init on null");
                }
                ArrayInstance value = ctx.getHeapManager().newArray("B", 16);
                receiver.setField("java/lang/AbstractStringBuilder", "value", "[B", value);
                receiver.setField("java/lang/AbstractStringBuilder", "coder", "B", (byte) 0);
                receiver.setField("java/lang/AbstractStringBuilder", "count", "I", 0);
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "StringBuilder init on null");
                }
                String str = "";
                if (!args[0].isNull()) {
                    str = ctx.getHeapManager().extractString(args[0].asReference());
                    if (str == null) str = "";
                }
                int capacity = str.length() + 16;
                ArrayInstance value = ctx.getHeapManager().newArray("B", capacity);
                for (int i = 0; i < str.length(); i++) {
                    value.setByte(i, (byte) str.charAt(i));
                }
                receiver.setField("java/lang/AbstractStringBuilder", "value", "[B", value);
                receiver.setField("java/lang/AbstractStringBuilder", "coder", "B", (byte) 0);
                receiver.setField("java/lang/AbstractStringBuilder", "count", "I", str.length());
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/StringBuilder", "<init>", "(I)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "StringBuilder init on null");
                }
                int capacity = args[0].asInt();
                ArrayInstance value = ctx.getHeapManager().newArray("B", capacity);
                receiver.setField("java/lang/AbstractStringBuilder", "value", "[B", value);
                receiver.setField("java/lang/AbstractStringBuilder", "coder", "B", (byte) 0);
                receiver.setField("java/lang/AbstractStringBuilder", "count", "I", 0);
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "append on null");
                }
                String str = "null";
                if (!args[0].isNull()) {
                    String extracted = ctx.getHeapManager().extractString(args[0].asReference());
                    if (extracted != null) str = extracted;
                }
                Object valueObj = receiver.getField("java/lang/AbstractStringBuilder", "value", "[B");
                Object countObj = receiver.getField("java/lang/AbstractStringBuilder", "count", "I");
                int count = countObj instanceof Integer ? (Integer) countObj : 0;
                ArrayInstance value;
                if (valueObj instanceof ArrayInstance) {
                    value = (ArrayInstance) valueObj;
                } else {
                    value = ctx.getHeapManager().newArray("B", 16);
                    receiver.setField("java/lang/AbstractStringBuilder", "value", "[B", value);
                }
                int newCount = count + str.length();
                if (newCount > value.getLength()) {
                    int newCapacity = Math.max(newCount, value.getLength() * 2 + 2);
                    ArrayInstance newValue = ctx.getHeapManager().newArray("B", newCapacity);
                    for (int i = 0; i < count; i++) {
                        newValue.setByte(i, value.getByte(i));
                    }
                    value = newValue;
                    receiver.setField("java/lang/AbstractStringBuilder", "value", "[B", value);
                }
                for (int i = 0; i < str.length(); i++) {
                    value.setByte(count + i, (byte) str.charAt(i));
                }
                receiver.setField("java/lang/AbstractStringBuilder", "count", "I", newCount);
                return ConcreteValue.reference(receiver);
            });

        registry.register("java/lang/StringBuilder", "toString", "()Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "toString on null");
                }
                Object valueObj = receiver.getField("java/lang/AbstractStringBuilder", "value", "[B");
                Object countObj = receiver.getField("java/lang/AbstractStringBuilder", "count", "I");
                int count = countObj instanceof Integer ? (Integer) countObj : 0;
                if (!(valueObj instanceof ArrayInstance)) {
                    return ConcreteValue.reference(ctx.getHeapManager().internString(""));
                }
                ArrayInstance value = (ArrayInstance) valueObj;
                StringBuilder sb = new StringBuilder(count);
                for (int i = 0; i < count; i++) {
                    sb.append((char) (value.getByte(i) & 0xFF));
                }
                return ConcreteValue.reference(ctx.getHeapManager().internString(sb.toString()));
            });
    }
}
