package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeException;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public class CollectionHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerArraysHandlers(registry);
        registerCollectionHandlers(registry);
        registerReflectArrayHandlers(registry);
    }

    private void registerArraysHandlers(NativeRegistry registry) {
        registry.register("java/util/Arrays", "copyOf", "([II)[I",
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

        registry.register("java/util/Arrays", "copyOf", "([BI)[B",
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

        registry.register("java/util/Arrays", "copyOf", "([CI)[C",
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

        registry.register("java/util/Arrays", "copyOfRange", "([BII)[B",
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

        registry.register("java/util/Arrays", "fill", "([BB)V",
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

        registry.register("java/util/Arrays", "fill", "([II)V",
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

    private void registerCollectionHandlers(NativeRegistry registry) {
        registry.register("java/util/ArrayList", "<init>", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "ArrayList init on null");
                }
                ArrayInstance emptyArray = ctx.getHeapManager().newArray("Ljava/lang/Object;", 0);
                receiver.setField("java/util/ArrayList", "elementData", "[Ljava/lang/Object;", emptyArray);
                receiver.setField("java/util/ArrayList", "size", "I", 0);
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/ArrayList", "<init>", "(I)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "ArrayList init on null");
                }
                int initialCapacity = args[0].asInt();
                if (initialCapacity < 0) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Negative capacity");
                }
                ArrayInstance array = ctx.getHeapManager().newArray("Ljava/lang/Object;", initialCapacity);
                receiver.setField("java/util/ArrayList", "elementData", "[Ljava/lang/Object;", array);
                receiver.setField("java/util/ArrayList", "size", "I", 0);
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/ArrayList", "add", "(Ljava/lang/Object;)Z",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "add on null ArrayList");
                }
                Object elementDataObj = receiver.getField("java/util/ArrayList", "elementData", "[Ljava/lang/Object;");
                Object sizeObj = receiver.getField("java/util/ArrayList", "size", "I");
                int size = sizeObj instanceof Integer ? (Integer) sizeObj : 0;
                ArrayInstance elementData;
                if (elementDataObj instanceof ArrayInstance) {
                    elementData = (ArrayInstance) elementDataObj;
                } else {
                    elementData = ctx.getHeapManager().newArray("Ljava/lang/Object;", 10);
                    receiver.setField("java/util/ArrayList", "elementData", "[Ljava/lang/Object;", elementData);
                }
                if (size >= elementData.getLength()) {
                    int newCapacity = Math.max(10, elementData.getLength() * 2);
                    ArrayInstance newArray = ctx.getHeapManager().newArray("Ljava/lang/Object;", newCapacity);
                    for (int i = 0; i < size; i++) {
                        newArray.set(i, elementData.get(i));
                    }
                    elementData = newArray;
                    receiver.setField("java/util/ArrayList", "elementData", "[Ljava/lang/Object;", elementData);
                }
                Object element = args[0].isNull() ? null : args[0].asReference();
                elementData.set(size, element);
                receiver.setField("java/util/ArrayList", "size", "I", size + 1);
                return ConcreteValue.intValue(1);
            });

        registry.register("java/util/ArrayList", "get", "(I)Ljava/lang/Object;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "get on null ArrayList");
                }
                int index = args[0].asInt();
                Object sizeObj = receiver.getField("java/util/ArrayList", "size", "I");
                int size = sizeObj instanceof Integer ? (Integer) sizeObj : 0;
                if (index < 0 || index >= size) {
                    throw new NativeException("java/lang/IndexOutOfBoundsException", "Index: " + index);
                }
                Object elementDataObj = receiver.getField("java/util/ArrayList", "elementData", "[Ljava/lang/Object;");
                if (!(elementDataObj instanceof ArrayInstance)) {
                    return ConcreteValue.nullRef();
                }
                Object element = ((ArrayInstance) elementDataObj).get(index);
                if (element instanceof ObjectInstance) {
                    return ConcreteValue.reference((ObjectInstance) element);
                }
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/ArrayList", "size", "()I",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "size on null ArrayList");
                }
                Object sizeObj = receiver.getField("java/util/ArrayList", "size", "I");
                return ConcreteValue.intValue(sizeObj instanceof Integer ? (Integer) sizeObj : 0);
            });

        registry.register("java/util/HashMap", "<init>", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "HashMap init on null");
                }
                receiver.setField("java/util/HashMap", "loadFactor", "F", 0.75f);
                receiver.setField("java/util/HashMap", "threshold", "I", 12);
                receiver.setField("java/util/HashMap", "size", "I", 0);
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/HashMap", "<init>", "(I)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "HashMap init on null");
                }
                receiver.setField("java/util/HashMap", "loadFactor", "F", 0.75f);
                int capacity = args[0].asInt();
                receiver.setField("java/util/HashMap", "threshold", "I", (int)(capacity * 0.75f));
                receiver.setField("java/util/HashMap", "size", "I", 0);
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "put on null HashMap");
                }
                Object sizeObj = receiver.getField("java/util/HashMap", "size", "I");
                int size = sizeObj instanceof Integer ? (Integer) sizeObj : 0;
                receiver.setField("java/util/HashMap", "size", "I", size + 1);
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/HashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/util/HashMap", "size", "()I",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "size on null HashMap");
                }
                Object sizeObj = receiver.getField("java/util/HashMap", "size", "I");
                return ConcreteValue.intValue(sizeObj instanceof Integer ? (Integer) sizeObj : 0);
            });

        registry.register("java/util/LinkedHashMap", "<init>", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "LinkedHashMap init on null");
                }
                receiver.setField("java/util/HashMap", "loadFactor", "F", 0.75f);
                receiver.setField("java/util/HashMap", "threshold", "I", 12);
                receiver.setField("java/util/HashMap", "size", "I", 0);
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/LinkedHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "put on null LinkedHashMap");
                }
                Object sizeObj = receiver.getField("java/util/HashMap", "size", "I");
                int size = sizeObj instanceof Integer ? (Integer) sizeObj : 0;
                receiver.setField("java/util/HashMap", "size", "I", size + 1);
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/LinkedHashMap", "size", "()I",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "size on null LinkedHashMap");
                }
                Object sizeObj = receiver.getField("java/util/HashMap", "size", "I");
                return ConcreteValue.intValue(sizeObj instanceof Integer ? (Integer) sizeObj : 0);
            });

        registry.register("java/util/HashSet", "<init>", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "HashSet init on null");
                }
                ObjectInstance map = ctx.getHeapManager().newObject("java/util/HashMap");
                map.setField("java/util/HashMap", "loadFactor", "F", 0.75f);
                map.setField("java/util/HashMap", "threshold", "I", 12);
                map.setField("java/util/HashMap", "size", "I", 0);
                receiver.setField("java/util/HashSet", "map", "Ljava/util/HashMap;", map);
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/HashSet", "add", "(Ljava/lang/Object;)Z",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "add on null HashSet");
                }
                return ConcreteValue.intValue(1);
            });

        registry.register("java/util/TreeMap", "<init>", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "TreeMap init on null");
                }
                receiver.setField("java/util/TreeMap", "size", "I", 0);
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/TreeMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "put on null TreeMap");
                }
                Object sizeObj = receiver.getField("java/util/TreeMap", "size", "I");
                int size = sizeObj instanceof Integer ? (Integer) sizeObj : 0;
                receiver.setField("java/util/TreeMap", "size", "I", size + 1);
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/LinkedList", "<init>", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "LinkedList init on null");
                }
                receiver.setField("java/util/LinkedList", "size", "I", 0);
                return ConcreteValue.nullRef();
            });

        registry.register("java/util/LinkedList", "add", "(Ljava/lang/Object;)Z",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "add on null LinkedList");
                }
                Object sizeObj = receiver.getField("java/util/LinkedList", "size", "I");
                int size = sizeObj instanceof Integer ? (Integer) sizeObj : 0;
                receiver.setField("java/util/LinkedList", "size", "I", size + 1);
                return ConcreteValue.intValue(1);
            });

        registry.register("java/util/AbstractList", "<init>", "()V",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/util/AbstractCollection", "<init>", "()V",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/util/AbstractMap", "<init>", "()V",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/util/AbstractSet", "<init>", "()V",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/util/AbstractSequentialList", "<init>", "()V",
            (receiver, args, ctx) -> ConcreteValue.nullRef());
    }

    private void registerReflectArrayHandlers(NativeRegistry registry) {
        registry.register("java/lang/reflect/Array", "getLength", "(Ljava/lang/Object;)I",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.getLength on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                return ConcreteValue.intValue(((ArrayInstance) obj).getLength());
            });

        registry.register("java/lang/reflect/Array", "get", "(Ljava/lang/Object;I)Ljava/lang/Object;",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.get on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                Object element = arr.get(index);
                if (element instanceof ObjectInstance) {
                    return ConcreteValue.reference((ObjectInstance) element);
                } else if (element instanceof Integer) {
                    ObjectInstance boxed = ctx.getHeapManager().newObject("java/lang/Integer");
                    boxed.setField("java/lang/Integer", "value", "I", element);
                    return ConcreteValue.reference(boxed);
                } else if (element instanceof Long) {
                    ObjectInstance boxed = ctx.getHeapManager().newObject("java/lang/Long");
                    boxed.setField("java/lang/Long", "value", "J", element);
                    return ConcreteValue.reference(boxed);
                } else if (element instanceof Byte) {
                    ObjectInstance boxed = ctx.getHeapManager().newObject("java/lang/Byte");
                    boxed.setField("java/lang/Byte", "value", "B", element);
                    return ConcreteValue.reference(boxed);
                }
                return ConcreteValue.nullRef();
            });

        registry.register("java/lang/reflect/Array", "set", "(Ljava/lang/Object;ILjava/lang/Object;)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.set on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                Object value = args[2].isNull() ? null : args[2].asReference();
                arr.set(index, value);
                return null;
            });

        registry.register("java/lang/reflect/Array", "getInt", "(Ljava/lang/Object;I)I",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.getInt on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                Object element = arr.get(index);
                if (element instanceof Integer) {
                    return ConcreteValue.intValue((Integer) element);
                }
                return ConcreteValue.intValue(0);
            });

        registry.register("java/lang/reflect/Array", "setInt", "(Ljava/lang/Object;II)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.setInt on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                arr.set(index, args[2].asInt());
                return null;
            });

        registry.register("java/lang/reflect/Array", "getLong", "(Ljava/lang/Object;I)J",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.getLong on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                Object element = arr.get(index);
                if (element instanceof Long) {
                    return ConcreteValue.longValue((Long) element);
                }
                return ConcreteValue.longValue(0L);
            });

        registry.register("java/lang/reflect/Array", "setLong", "(Ljava/lang/Object;IJ)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.setLong on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                arr.set(index, args[2].asLong());
                return null;
            });

        registry.register("java/lang/reflect/Array", "getByte", "(Ljava/lang/Object;I)B",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.getByte on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                return ConcreteValue.intValue(arr.getByte(index));
            });

        registry.register("java/lang/reflect/Array", "setByte", "(Ljava/lang/Object;IB)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.setByte on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                arr.setByte(index, (byte) args[2].asInt());
                return null;
            });

        registry.register("java/lang/reflect/Array", "getBoolean", "(Ljava/lang/Object;I)Z",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.getBoolean on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                Object element = arr.get(index);
                if (element instanceof Boolean) {
                    return ConcreteValue.intValue((Boolean) element ? 1 : 0);
                }
                return ConcreteValue.intValue(0);
            });

        registry.register("java/lang/reflect/Array", "setBoolean", "(Ljava/lang/Object;IZ)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.setBoolean on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                arr.set(index, args[2].asInt() != 0);
                return null;
            });

        registry.register("java/lang/reflect/Array", "getChar", "(Ljava/lang/Object;I)C",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.getChar on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                return ConcreteValue.intValue(arr.getChar(index));
            });

        registry.register("java/lang/reflect/Array", "setChar", "(Ljava/lang/Object;IC)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.setChar on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                arr.setChar(index, (char) args[2].asInt());
                return null;
            });

        registry.register("java/lang/reflect/Array", "getShort", "(Ljava/lang/Object;I)S",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.getShort on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                Object element = arr.get(index);
                if (element instanceof Short) {
                    return ConcreteValue.intValue((Short) element);
                }
                return ConcreteValue.intValue(0);
            });

        registry.register("java/lang/reflect/Array", "setShort", "(Ljava/lang/Object;IS)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.setShort on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                arr.set(index, (short) args[2].asInt());
                return null;
            });

        registry.register("java/lang/reflect/Array", "getFloat", "(Ljava/lang/Object;I)F",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.getFloat on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                Object element = arr.get(index);
                if (element instanceof Float) {
                    return ConcreteValue.floatValue((Float) element);
                }
                return ConcreteValue.floatValue(0.0f);
            });

        registry.register("java/lang/reflect/Array", "setFloat", "(Ljava/lang/Object;IF)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.setFloat on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                arr.set(index, args[2].asFloat());
                return null;
            });

        registry.register("java/lang/reflect/Array", "getDouble", "(Ljava/lang/Object;I)D",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.getDouble on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                Object element = arr.get(index);
                if (element instanceof Double) {
                    return ConcreteValue.doubleValue((Double) element);
                }
                return ConcreteValue.doubleValue(0.0);
            });

        registry.register("java/lang/reflect/Array", "setDouble", "(Ljava/lang/Object;ID)V",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.setDouble on null");
                }
                ObjectInstance obj = args[0].asReference();
                if (!(obj instanceof ArrayInstance)) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Argument is not an array");
                }
                ArrayInstance arr = (ArrayInstance) obj;
                int index = args[1].asInt();
                if (index < 0 || index >= arr.getLength()) {
                    throw new NativeException("java/lang/ArrayIndexOutOfBoundsException", "Index: " + index);
                }
                arr.set(index, args[2].asDouble());
                return null;
            });

        registry.register("java/lang/reflect/Array", "newArray", "(Ljava/lang/Class;I)Ljava/lang/Object;",
            (receiver, args, ctx) -> {
                if (args[0].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.newArray with null class");
                }
                int length = args[1].asInt();
                if (length < 0) {
                    throw new NativeException("java/lang/NegativeArraySizeException", "Length: " + length);
                }
                ObjectInstance classObj = args[0].asReference();
                Object nameObj = classObj.getField("java/lang/Class", "name", "Ljava/lang/String;");
                String componentType = "Ljava/lang/Object;";
                if (nameObj instanceof ObjectInstance) {
                    String name = ctx.getHeapManager().extractString((ObjectInstance) nameObj);
                    if (name != null) {
                        componentType = "L" + name.replace('.', '/') + ";";
                    }
                }
                ArrayInstance arr = ctx.getHeapManager().newArray("[" + componentType, length);
                return ConcreteValue.reference(arr);
            });

        registry.register("java/lang/reflect/Array", "multiNewArray", "(Ljava/lang/Class;[I)Ljava/lang/Object;",
            (receiver, args, ctx) -> {
                if (args[0].isNull() || args[1].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Array.multiNewArray with null");
                }
                ArrayInstance dims = (ArrayInstance) args[1].asReference();
                if (dims.getLength() == 0) {
                    throw new NativeException("java/lang/IllegalArgumentException", "Empty dimensions array");
                }
                int firstDim = 0;
                Object d = dims.get(0);
                if (d instanceof Integer) {
                    firstDim = (Integer) d;
                }
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/Object;", firstDim);
                return ConcreteValue.reference(arr);
            });
    }
}
