package com.tonic.analysis.execution.heap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StringPool {

    private final ConcurrentHashMap<String, ObjectInstance> pool;
    private final AtomicInteger nextId;

    public StringPool(AtomicInteger nextId) {
        this.pool = new ConcurrentHashMap<>();
        this.nextId = nextId;
    }

    public ObjectInstance intern(String value) {
        return pool.computeIfAbsent(value, this::createStringObject);
    }

    private ObjectInstance createStringObject(String value) {
        ObjectInstance stringObj = new ObjectInstance(nextId.getAndIncrement(), "java/lang/String");

        int length = value.length();
        ArrayInstance charArray = new ArrayInstance(nextId.getAndIncrement(), "C", length);
        for (int i = 0; i < length; i++) {
            charArray.setChar(i, value.charAt(i));
        }

        stringObj.setField("java/lang/String", "value", "[C", charArray);

        return stringObj;
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
