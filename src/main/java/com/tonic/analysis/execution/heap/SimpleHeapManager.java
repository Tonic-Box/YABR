package com.tonic.analysis.execution.heap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleHeapManager implements HeapManager {

    private final AtomicInteger nextId;
    private final ConcurrentHashMap<Integer, ObjectInstance> heap;
    private final StringPool stringPool;
    private final ConcurrentHashMap<String, Object> staticFields;
    private Object classResolver;

    public SimpleHeapManager() {
        this.nextId = new AtomicInteger(1);
        this.heap = new ConcurrentHashMap<>();
        this.stringPool = new StringPool(nextId);
        this.staticFields = new ConcurrentHashMap<>();
    }

    public void setClassResolver(Object classResolver) {
        this.classResolver = classResolver;
    }

    @Override
    public ObjectInstance newObject(String className) {
        int id = nextId.getAndIncrement();
        ObjectInstance instance = new ObjectInstance(id, className);
        instance.setClassResolver(classResolver);
        heap.put(id, instance);
        initializeFields(instance, className);
        return instance;
    }

    private void initializeFields(ObjectInstance instance, String className) {
    }

    @Override
    public ArrayInstance newArray(String componentType, int length) {
        if (length < 0) {
            throw new HeapException("Negative array length: " + length);
        }

        int id = nextId.getAndIncrement();
        ArrayInstance array = new ArrayInstance(id, componentType, length);
        heap.put(id, array);
        return array;
    }

    @Override
    public ArrayInstance newMultiArray(String componentType, int[] dimensions) {
        if (dimensions == null || dimensions.length == 0) {
            throw new HeapException("Invalid dimensions for multi-array");
        }

        for (int dim : dimensions) {
            if (dim < 0) {
                throw new HeapException("Negative dimension in multi-array: " + dim);
            }
        }

        return createMultiArrayRecursive(componentType, dimensions, 0);
    }

    private ArrayInstance createMultiArrayRecursive(String componentType, int[] dimensions, int depth) {
        int currentDim = dimensions[depth];

        if (depth == dimensions.length - 1) {
            return newArray(componentType, currentDim);
        }

        String arrayComponentType = "[" + componentType;
        ArrayInstance array = newArray(arrayComponentType, currentDim);

        for (int i = 0; i < currentDim; i++) {
            ArrayInstance subArray = createMultiArrayRecursive(componentType, dimensions, depth + 1);
            array.set(i, subArray);
        }

        return array;
    }

    @Override
    public ObjectInstance internString(String value) {
        ObjectInstance stringObj = stringPool.intern(value);
        heap.put(stringObj.getId(), stringObj);

        Object charArrayObj = stringObj.getField("java/lang/String", "value", "[C");
        if (charArrayObj instanceof ArrayInstance) {
            heap.put(((ArrayInstance) charArrayObj).getId(), (ArrayInstance) charArrayObj);
        }

        return stringObj;
    }

    @Override
    public String extractString(ObjectInstance instance) {
        if (instance == null) {
            return null;
        }
        if (!"java/lang/String".equals(instance.getClassName())) {
            return null;
        }

        Object charArrayObj = instance.getField("java/lang/String", "value", "[C");
        if (!(charArrayObj instanceof ArrayInstance)) {
            return null;
        }

        ArrayInstance charArray = (ArrayInstance) charArrayObj;
        int length = charArray.getLength();
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = charArray.getChar(i);
        }

        return new String(chars);
    }

    @Override
    public boolean isNull(ObjectInstance instance) {
        return instance == null;
    }

    @Override
    public int identityHashCode(ObjectInstance instance) {
        if (instance == null) {
            return 0;
        }
        return instance.getIdentityHashCode();
    }

    @Override
    public long objectCount() {
        return heap.size();
    }

    private String staticFieldKey(String owner, String name, String descriptor) {
        return owner + "." + name + ":" + descriptor;
    }

    @Override
    public void putStaticField(String owner, String name, String descriptor, Object value) {
        staticFields.put(staticFieldKey(owner, name, descriptor), value);
    }

    @Override
    public Object getStaticField(String owner, String name, String descriptor) {
        return staticFields.get(staticFieldKey(owner, name, descriptor));
    }

    @Override
    public boolean hasStaticField(String owner, String name, String descriptor) {
        return staticFields.containsKey(staticFieldKey(owner, name, descriptor));
    }

    @Override
    public void clearStaticFields() {
        staticFields.clear();
    }
}
