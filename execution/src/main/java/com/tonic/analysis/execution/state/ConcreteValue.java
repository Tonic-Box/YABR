package com.tonic.analysis.execution.state;

import com.tonic.analysis.execution.heap.ObjectInstance;

import java.util.Objects;

/**
 * An immutable tagged JVM value, storing primitives as raw bits and references as a heap instance.
 */
public final class ConcreteValue
{

    private final ValueTag tag;
    private final long bits;
    private final ObjectInstance ref;

    private ConcreteValue(ValueTag tag, long bits, ObjectInstance ref)
    {
        this.tag = tag;
        this.bits = bits;
        this.ref = ref;
    }

    /**
     * Creates an INT value, also used for the sub-int types.
     * @param value the integer
     * @return the tagged value
     */
    public static ConcreteValue intValue(int value)
    {
        return new ConcreteValue(ValueTag.INT, value, null);
    }

    /**
     * Creates a LONG value.
     * @param value the long
     * @return the tagged value
     */
    public static ConcreteValue longValue(long value)
    {
        return new ConcreteValue(ValueTag.LONG, value, null);
    }

    /**
     * Creates a FLOAT value, storing its raw bit pattern so NaN payloads survive.
     * @param value the float
     * @return the tagged value
     */
    public static ConcreteValue floatValue(float value)
    {
        return new ConcreteValue(ValueTag.FLOAT, Float.floatToRawIntBits(value), null);
    }

    /**
     * Creates a DOUBLE value, storing its raw bit pattern so NaN payloads survive.
     * @param value the double
     * @return the tagged value
     */
    public static ConcreteValue doubleValue(double value)
    {
        return new ConcreteValue(ValueTag.DOUBLE, Double.doubleToRawLongBits(value), null);
    }

    /**
     * Creates a REFERENCE value pointing at a heap instance.
     * @param instance the target object, never null
     * @return the tagged value
     * @throws IllegalArgumentException if the instance is null
     */
    public static ConcreteValue reference(ObjectInstance instance)
    {
        if (instance == null)
        {
            throw new IllegalArgumentException("Use nullRef() for null references");
        }
        return new ConcreteValue(ValueTag.REFERENCE, 0, instance);
    }

    /**
     * @return a NULL-tagged reference value
     */
    public static ConcreteValue nullRef()
    {
        return new ConcreteValue(ValueTag.NULL, 0, null);
    }

    /**
     * Creates the value pushed by jsr.
     * @param address the bytecode offset to return to
     * @return the tagged value
     */
    public static ConcreteValue returnAddress(int address)
    {
        return new ConcreteValue(ValueTag.RETURN_ADDRESS, address, null);
    }

    /**
     * @return the tag
     */
    public ValueTag getTag()
    {
        return tag;
    }

    /**
     * @return true if this is the null reference
     */
    public boolean isNull()
    {
        return tag == ValueTag.NULL;
    }

    /**
     * @return true if the value occupies two stack or local slots
     */
    public boolean isWide()
    {
        return tag.isWide();
    }

    /**
     * @return true if the value is an object reference or null
     */
    public boolean isReference()
    {
        return tag == ValueTag.REFERENCE || tag == ValueTag.NULL;
    }

    /**
     * @return true if the value is an int or a long
     */
    public boolean isIntegral()
    {
        return tag == ValueTag.INT || tag == ValueTag.LONG;
    }

    /**
     * @return the JVM computational type category, 1 or 2
     */
    public int getCategory()
    {
        return tag.getCategory();
    }

    /**
     * @return the int contents
     * @throws IllegalStateException if the value is not tagged INT
     */
    public int asInt()
    {
        if (tag != ValueTag.INT)
        {
            throw new IllegalStateException("Value is not INT: " + tag);
        }
        return (int) bits;
    }

    /**
     * @return the long contents
     * @throws IllegalStateException if the value is not tagged LONG
     */
    public long asLong()
    {
        if (tag != ValueTag.LONG)
        {
            throw new IllegalStateException("Value is not LONG: " + tag);
        }
        return bits;
    }

    /**
     * @return the float decoded from the stored bits
     * @throws IllegalStateException if the value is not tagged FLOAT
     */
    public float asFloat()
    {
        if (tag != ValueTag.FLOAT)
        {
            throw new IllegalStateException("Value is not FLOAT: " + tag);
        }
        return Float.intBitsToFloat((int) bits);
    }

    /**
     * @return the double decoded from the stored bits
     * @throws IllegalStateException if the value is not tagged DOUBLE
     */
    public double asDouble()
    {
        if (tag != ValueTag.DOUBLE)
        {
            throw new IllegalStateException("Value is not DOUBLE: " + tag);
        }
        return Double.longBitsToDouble(bits);
    }

    /**
     * @return the referenced instance, or null when the value is the null reference
     * @throws IllegalStateException if the value is neither a reference nor null
     */
    public ObjectInstance asReference()
    {
        if (tag == ValueTag.NULL)
        {
            return null;
        }
        if (tag != ValueTag.REFERENCE)
        {
            throw new IllegalStateException("Value is not REFERENCE: " + tag);
        }
        return ref;
    }

    /**
     * @return the stored bytecode offset
     * @throws IllegalStateException if the value is not tagged RETURN_ADDRESS
     */
    public int asReturnAddress()
    {
        if (tag != ValueTag.RETURN_ADDRESS)
        {
            throw new IllegalStateException("Value is not RETURN_ADDRESS: " + tag);
        }
        return (int) bits;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof ConcreteValue)) return false;
        ConcreteValue that = (ConcreteValue) o;
        return bits == that.bits && tag == that.tag && Objects.equals(ref, that.ref);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(tag, bits, ref);
    }

    @Override
    public String toString()
    {
        switch (tag)
        {
            case INT:
                return "int(" + asInt() + ")";
            case LONG:
                return "long(" + asLong() + "L)";
            case FLOAT:
                return "float(" + asFloat() + "f)";
            case DOUBLE:
                return "double(" + asDouble() + ")";
            case REFERENCE:
                return "ref(" + ref + ")";
            case NULL:
                return "null";
            case RETURN_ADDRESS:
                return "retAddr(" + asReturnAddress() + ")";
            default:
                return "unknown";
        }
    }
}
