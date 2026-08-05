package com.tonic.analysis.execution.heap;

/**
 * A heap-allocated array with typed primitive or reference backing storage.
 */
public class ArrayInstance extends ObjectInstance
{

    private final String componentType;
    private final int length;
    private final Object storage;
    private final boolean isPrimitiveArray;

    /**
     * Allocates an array with zeroed storage matching the component type.
     * @param id the heap object id
     * @param componentType descriptor of the element type
     * @param length the number of elements
     * @throws HeapException if the length is negative
     */
    public ArrayInstance(int id, String componentType, int length)
    {
        super(id, getArrayClassName(componentType));
        this.componentType = componentType;
        this.length = length;
        this.isPrimitiveArray = isPrimitive(componentType);
        this.storage = createStorage(componentType, length);
    }

    private static String getArrayClassName(String componentType)
    {
        return "[" + componentType;
    }

    private static boolean isPrimitive(String componentType)
    {
        if (componentType.length() != 1)
        {
            return false;
        }
        char c = componentType.charAt(0);
        return c == 'Z' || c == 'B' || c == 'C' || c == 'S' ||
               c == 'I' || c == 'J' || c == 'F' || c == 'D';
    }

    private static Object createStorage(String componentType, int length)
    {
        if (length < 0)
        {
            throw new HeapException("Negative array length: " + length);
        }

        if (componentType.length() == 1)
        {
            switch (componentType.charAt(0))
            {
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

    /**
     * @return the length
     */
    public int getLength()
    {
        return length;
    }

    /**
     * @return the component type
     */
    public String getComponentType()
    {
        return componentType;
    }

    /**
     * @return whether primitive array
     */
    public boolean isPrimitiveArray()
    {
        return isPrimitiveArray;
    }

    private void checkBounds(int index)
    {
        if (index < 0 || index >= length)
        {
            throw new HeapException("Array index out of bounds: " + index + " (length: " + length + ")");
        }
    }

    /**
     * Reads an element as a boxed value or reference.
     * @param index the element index
     * @return the element value
     * @throws HeapException if the index is out of bounds
     */
    public Object get(int index)
    {
        checkBounds(index);

        if (storage instanceof boolean[])
        {
            return ((boolean[]) storage)[index];
        }
        else if (storage instanceof byte[])
        {
            return ((byte[]) storage)[index];
        }
        else if (storage instanceof char[])
        {
            return ((char[]) storage)[index];
        }
        else if (storage instanceof short[])
        {
            return ((short[]) storage)[index];
        }
        else if (storage instanceof int[])
        {
            return ((int[]) storage)[index];
        }
        else if (storage instanceof long[])
        {
            return ((long[]) storage)[index];
        }
        else if (storage instanceof float[])
        {
            return ((float[]) storage)[index];
        }
        else if (storage instanceof double[])
        {
            return ((double[]) storage)[index];
        }
        else
        {
            return ((ObjectInstance[]) storage)[index];
        }
    }

    /**
     * Writes an element, unboxing to the storage type as needed.
     * @param index the element index
     * @param value the value to store
     * @throws HeapException if the index is out of bounds
     */
    public void set(int index, Object value)
    {
        checkBounds(index);

        if (storage instanceof boolean[])
        {
            ((boolean[]) storage)[index] = (Boolean) value;
        }
        else if (storage instanceof byte[])
        {
            ((byte[]) storage)[index] = ((Number) value).byteValue();
        }
        else if (storage instanceof char[])
        {
            ((char[]) storage)[index] = (Character) value;
        }
        else if (storage instanceof short[])
        {
            ((short[]) storage)[index] = ((Number) value).shortValue();
        }
        else if (storage instanceof int[])
        {
            ((int[]) storage)[index] = ((Number) value).intValue();
        }
        else if (storage instanceof long[])
        {
            ((long[]) storage)[index] = ((Number) value).longValue();
        }
        else if (storage instanceof float[])
        {
            ((float[]) storage)[index] = ((Number) value).floatValue();
        }
        else if (storage instanceof double[])
        {
            ((double[]) storage)[index] = ((Number) value).doubleValue();
        }
        else
        {
            ((ObjectInstance[]) storage)[index] = (ObjectInstance) value;
        }
    }

    /**
     * Reads an int element.
     * @param index the element index
     * @return the element value
     * @throws HeapException if out of bounds or the storage is not int[]
     */
    public int getInt(int index)
    {
        checkBounds(index);
        if (!(storage instanceof int[]))
        {
            throw new HeapException("Array is not int[]");
        }
        return ((int[]) storage)[index];
    }

    /**
     * Writes an int element.
     * @param index the element index
     * @param value the value to store
     * @throws HeapException if out of bounds or the storage is not int[]
     */
    public void setInt(int index, int value)
    {
        checkBounds(index);
        if (!(storage instanceof int[]))
        {
            throw new HeapException("Array is not int[]");
        }
        ((int[]) storage)[index] = value;
    }

    /**
     * Reads a long element.
     * @param index the element index
     * @return the element value
     * @throws HeapException if out of bounds or the storage is not long[]
     */
    public long getLong(int index)
    {
        checkBounds(index);
        if (!(storage instanceof long[]))
        {
            throw new HeapException("Array is not long[]");
        }
        return ((long[]) storage)[index];
    }

    /**
     * Writes a long element.
     * @param index the element index
     * @param value the value to store
     * @throws HeapException if out of bounds or the storage is not long[]
     */
    public void setLong(int index, long value)
    {
        checkBounds(index);
        if (!(storage instanceof long[]))
        {
            throw new HeapException("Array is not long[]");
        }
        ((long[]) storage)[index] = value;
    }

    /**
     * Reads a boolean element.
     * @param index the element index
     * @return the element value
     * @throws HeapException if out of bounds or the storage is not boolean[]
     */
    public boolean getBoolean(int index)
    {
        checkBounds(index);
        if (!(storage instanceof boolean[]))
        {
            throw new HeapException("Array is not boolean[]");
        }
        return ((boolean[]) storage)[index];
    }

    /**
     * Writes a boolean element.
     * @param index the element index
     * @param value the value to store
     * @throws HeapException if out of bounds or the storage is not boolean[]
     */
    public void setBoolean(int index, boolean value)
    {
        checkBounds(index);
        if (!(storage instanceof boolean[]))
        {
            throw new HeapException("Array is not boolean[]");
        }
        ((boolean[]) storage)[index] = value;
    }

    /**
     * Reads a byte element.
     * @param index the element index
     * @return the element value
     * @throws HeapException if out of bounds or the storage is not byte[]
     */
    public byte getByte(int index)
    {
        checkBounds(index);
        if (!(storage instanceof byte[]))
        {
            throw new HeapException("Array is not byte[]");
        }
        return ((byte[]) storage)[index];
    }

    /**
     * Writes a byte element.
     * @param index the element index
     * @param value the value to store
     * @throws HeapException if out of bounds or the storage is not byte[]
     */
    public void setByte(int index, byte value)
    {
        checkBounds(index);
        if (!(storage instanceof byte[]))
        {
            throw new HeapException("Array is not byte[]");
        }
        ((byte[]) storage)[index] = value;
    }

    /**
     * Reads a char element.
     * @param index the element index
     * @return the element value
     * @throws HeapException if out of bounds or the storage is not char[]
     */
    public char getChar(int index)
    {
        checkBounds(index);
        if (!(storage instanceof char[]))
        {
            throw new HeapException("Array is not char[]");
        }
        return ((char[]) storage)[index];
    }

    /**
     * Writes a char element.
     * @param index the element index
     * @param value the value to store
     * @throws HeapException if out of bounds or the storage is not char[]
     */
    public void setChar(int index, char value)
    {
        checkBounds(index);
        if (!(storage instanceof char[]))
        {
            throw new HeapException("Array is not char[]");
        }
        ((char[]) storage)[index] = value;
    }

    /**
     * Reads a short element.
     * @param index the element index
     * @return the element value
     * @throws HeapException if out of bounds or the storage is not short[]
     */
    public short getShort(int index)
    {
        checkBounds(index);
        if (!(storage instanceof short[]))
        {
            throw new HeapException("Array is not short[]");
        }
        return ((short[]) storage)[index];
    }

    /**
     * Writes a short element.
     * @param index the element index
     * @param value the value to store
     * @throws HeapException if out of bounds or the storage is not short[]
     */
    public void setShort(int index, short value)
    {
        checkBounds(index);
        if (!(storage instanceof short[]))
        {
            throw new HeapException("Array is not short[]");
        }
        ((short[]) storage)[index] = value;
    }

    /**
     * Reads a float element.
     * @param index the element index
     * @return the element value
     * @throws HeapException if out of bounds or the storage is not float[]
     */
    public float getFloat(int index)
    {
        checkBounds(index);
        if (!(storage instanceof float[]))
        {
            throw new HeapException("Array is not float[]");
        }
        return ((float[]) storage)[index];
    }

    /**
     * Writes a float element.
     * @param index the element index
     * @param value the value to store
     * @throws HeapException if out of bounds or the storage is not float[]
     */
    public void setFloat(int index, float value)
    {
        checkBounds(index);
        if (!(storage instanceof float[]))
        {
            throw new HeapException("Array is not float[]");
        }
        ((float[]) storage)[index] = value;
    }

    /**
     * Reads a double element.
     * @param index the element index
     * @return the element value
     * @throws HeapException if out of bounds or the storage is not double[]
     */
    public double getDouble(int index)
    {
        checkBounds(index);
        if (!(storage instanceof double[]))
        {
            throw new HeapException("Array is not double[]");
        }
        return ((double[]) storage)[index];
    }

    /**
     * Writes a double element.
     * @param index the element index
     * @param value the value to store
     * @throws HeapException if out of bounds or the storage is not double[]
     */
    public void setDouble(int index, double value)
    {
        checkBounds(index);
        if (!(storage instanceof double[]))
        {
            throw new HeapException("Array is not double[]");
        }
        ((double[]) storage)[index] = value;
    }

    @Override
    public String toString()
    {
        return getClassName() + "@" + Integer.toHexString(getId()) + "[" + length + "]";
    }
}
