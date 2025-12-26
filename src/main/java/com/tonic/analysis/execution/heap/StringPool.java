package com.tonic.analysis.execution.heap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StringPool {

    private final ConcurrentHashMap<String, ObjectInstance> pool;
    private final AtomicInteger nextId;
    private Boolean useCompactStrings = null;

    public StringPool(AtomicInteger nextId) {
        this.pool = new ConcurrentHashMap<>();
        this.nextId = nextId;
    }

    public void setUseCompactStrings(boolean compact) {
        this.useCompactStrings = compact;
    }

    public boolean isUsingCompactStrings() {
        return useCompactStrings != null && useCompactStrings;
    }

    public ObjectInstance intern(String value) {
        return pool.computeIfAbsent(value, this::createStringObject);
    }

    private ObjectInstance createStringObject(String value) {
        ObjectInstance stringObj = new ObjectInstance(nextId.getAndIncrement(), "java/lang/String");

        if (useCompactStrings != null && useCompactStrings) {
            createJava9PlusString(stringObj, value);
        } else {
            createJava8String(stringObj, value);
        }

        return stringObj;
    }

    private void createJava8String(ObjectInstance stringObj, String value) {
        int length = value.length();
        ArrayInstance charArray = new ArrayInstance(nextId.getAndIncrement(), "C", length);
        for (int i = 0; i < length; i++) {
            charArray.setChar(i, value.charAt(i));
        }
        stringObj.setField("java/lang/String", "value", "[C", charArray);
    }

    private void createJava9PlusString(ObjectInstance stringObj, String value) {
        boolean isLatin1 = true;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 255) {
                isLatin1 = false;
                break;
            }
        }

        if (isLatin1) {
            byte[] bytes = new byte[value.length()];
            for (int i = 0; i < value.length(); i++) {
                bytes[i] = (byte) value.charAt(i);
            }
            ArrayInstance byteArray = new ArrayInstance(nextId.getAndIncrement(), "B", bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                byteArray.setByte(i, bytes[i]);
            }
            stringObj.setField("java/lang/String", "value", "[B", byteArray);
            stringObj.setField("java/lang/String", "coder", "B", (byte) 0);
        } else {
            byte[] bytes = new byte[value.length() * 2];
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                bytes[i * 2] = (byte) (c & 0xFF);
                bytes[i * 2 + 1] = (byte) ((c >> 8) & 0xFF);
            }
            ArrayInstance byteArray = new ArrayInstance(nextId.getAndIncrement(), "B", bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                byteArray.setByte(i, bytes[i]);
            }
            stringObj.setField("java/lang/String", "value", "[B", byteArray);
            stringObj.setField("java/lang/String", "coder", "B", (byte) 1);
        }
    }

    public boolean isInterned(String value) {
        return pool.containsKey(value);
    }

    public int size() {
        return pool.size();
    }

    public void clear() {
        pool.clear();
    }
}
