package com.tonic.analysis.execution.heap;

public class ArrayInstance extends ObjectInstance {

    private final String componentType;
    private final int length;
    private final Object storage;
    private final boolean isPrimitiveArray;

    public ArrayInstance(int id, String componentType, int length) {
        super(id, getArrayClassName(componentType));
        this.componentType = componentType;
        this.length = length;
        this.isPrimitiveArray = isPrimitive(componentType);
        this.storage = createStorage(componentType, length);
    }

    private static String getArrayClassName(String componentType) {
        return "[" + componentType;
    }

    private static boolean isPrimitive(String componentType) {
        if (componentType.length() != 1) {
            return false;
        }
        char c = componentType.charAt(0);
        return c == 'Z' || c == 'B' || c == 'C' || c == 'S' ||
               c == 'I' || c == 'J' || c == 'F' || c == 'D';
    }

    private static Object createStorage(String componentType, int length) {
        if (length < 0) {
            throw new HeapException("Negative array length: " + length);
        }

        if (componentType.length() == 1) {
            switch (componentType.charAt(0)) {
                case 'Z': return new boolean[length];
                case 'B': return new byte[length];
                case 'C': return new char[length];
                case 'S': return new short[length];
                case 'I': return new int[length];
                case 'J': return new long[length];
                case 'F': return new float[length];
                case 'D': return new double[length];
            }
        }
        return new ObjectInstance[length];
    }

    public int getLength() {
        return length;
    }

    public String getComponentType() {
        return componentType;
    }

    public boolean isPrimitiveArray() {
        return isPrimitiveArray;
    }

    private void checkBounds(int index) {
        if (index < 0 || index >= length) {
            throw new HeapException("Array index out of bounds: " + index + " (length: " + length + ")");
        }
    }

    public Object get(int index) {
        checkBounds(index);

        if (storage instanceof boolean[]) {
            return ((boolean[]) storage)[index];
        } else if (storage instanceof byte[]) {
            return ((byte[]) storage)[index];
        } else if (storage instanceof char[]) {
            return ((char[]) storage)[index];
        } else if (storage instanceof short[]) {
            return ((short[]) storage)[index];
        } else if (storage instanceof int[]) {
            return ((int[]) storage)[index];
        } else if (storage instanceof long[]) {
            return ((long[]) storage)[index];
        } else if (storage instanceof float[]) {
            return ((float[]) storage)[index];
        } else if (storage instanceof double[]) {
            return ((double[]) storage)[index];
        } else {
            return ((ObjectInstance[]) storage)[index];
        }
    }

    public void set(int index, Object value) {
        checkBounds(index);

        if (storage instanceof boolean[]) {
            ((boolean[]) storage)[index] = (Boolean) value;
        } else if (storage instanceof byte[]) {
            ((byte[]) storage)[index] = ((Number) value).byteValue();
        } else if (storage instanceof char[]) {
            ((char[]) storage)[index] = (Character) value;
        } else if (storage instanceof short[]) {
            ((short[]) storage)[index] = ((Number) value).shortValue();
        } else if (storage instanceof int[]) {
            ((int[]) storage)[index] = ((Number) value).intValue();
        } else if (storage instanceof long[]) {
            ((long[]) storage)[index] = ((Number) value).longValue();
        } else if (storage instanceof float[]) {
            ((float[]) storage)[index] = ((Number) value).floatValue();
        } else if (storage instanceof double[]) {
            ((double[]) storage)[index] = ((Number) value).doubleValue();
        } else {
            ((ObjectInstance[]) storage)[index] = (ObjectInstance) value;
        }
    }

    public int getInt(int index) {
        checkBounds(index);
        if (!(storage instanceof int[])) {
            throw new HeapException("Array is not int[]");
        }
        return ((int[]) storage)[index];
    }

    public void setInt(int index, int value) {
        checkBounds(index);
        if (!(storage instanceof int[])) {
            throw new HeapException("Array is not int[]");
        }
        ((int[]) storage)[index] = value;
    }

    public long getLong(int index) {
        checkBounds(index);
        if (!(storage instanceof long[])) {
            throw new HeapException("Array is not long[]");
        }
        return ((long[]) storage)[index];
    }

    public void setLong(int index, long value) {
        checkBounds(index);
        if (!(storage instanceof long[])) {
            throw new HeapException("Array is not long[]");
        }
        ((long[]) storage)[index] = value;
    }

    public boolean getBoolean(int index) {
        checkBounds(index);
        if (!(storage instanceof boolean[])) {
            throw new HeapException("Array is not boolean[]");
        }
        return ((boolean[]) storage)[index];
    }

    public void setBoolean(int index, boolean value) {
        checkBounds(index);
        if (!(storage instanceof boolean[])) {
            throw new HeapException("Array is not boolean[]");
        }
        ((boolean[]) storage)[index] = value;
    }

    public byte getByte(int index) {
        checkBounds(index);
        if (!(storage instanceof byte[])) {
            throw new HeapException("Array is not byte[]");
        }
        return ((byte[]) storage)[index];
    }

    public void setByte(int index, byte value) {
        checkBounds(index);
        if (!(storage instanceof byte[])) {
            throw new HeapException("Array is not byte[]");
        }
        ((byte[]) storage)[index] = value;
    }

    public char getChar(int index) {
        checkBounds(index);
        if (!(storage instanceof char[])) {
            throw new HeapException("Array is not char[]");
        }
        return ((char[]) storage)[index];
    }

    public void setChar(int index, char value) {
        checkBounds(index);
        if (!(storage instanceof char[])) {
            throw new HeapException("Array is not char[]");
        }
        ((char[]) storage)[index] = value;
    }

    public short getShort(int index) {
        checkBounds(index);
        if (!(storage instanceof short[])) {
            throw new HeapException("Array is not short[]");
        }
        return ((short[]) storage)[index];
    }

    public void setShort(int index, short value) {
        checkBounds(index);
        if (!(storage instanceof short[])) {
            throw new HeapException("Array is not short[]");
        }
        ((short[]) storage)[index] = value;
    }

    public float getFloat(int index) {
        checkBounds(index);
        if (!(storage instanceof float[])) {
            throw new HeapException("Array is not float[]");
        }
        return ((float[]) storage)[index];
    }

    public void setFloat(int index, float value) {
        checkBounds(index);
        if (!(storage instanceof float[])) {
            throw new HeapException("Array is not float[]");
        }
        ((float[]) storage)[index] = value;
    }

    public double getDouble(int index) {
        checkBounds(index);
        if (!(storage instanceof double[])) {
            throw new HeapException("Array is not double[]");
        }
        return ((double[]) storage)[index];
    }

    public void setDouble(int index, double value) {
        checkBounds(index);
        if (!(storage instanceof double[])) {
            throw new HeapException("Array is not double[]");
        }
        ((double[]) storage)[index] = value;
    }

    @Override
    public String toString() {
        return getClassName() + "@" + Integer.toHexString(getId()) + "[" + length + "]";
    }
}
