package com.tonic.analysis.execution.state;

import com.tonic.analysis.execution.heap.ObjectInstance;

import java.util.Objects;

public final class ConcreteValue {

    private final ValueTag tag;
    private final long bits;
    private final ObjectInstance ref;

    private ConcreteValue(ValueTag tag, long bits, ObjectInstance ref) {
        this.tag = tag;
        this.bits = bits;
        this.ref = ref;
    }

    public static ConcreteValue intValue(int value) {
        return new ConcreteValue(ValueTag.INT, value, null);
    }

    public static ConcreteValue longValue(long value) {
        return new ConcreteValue(ValueTag.LONG, value, null);
    }

    public static ConcreteValue floatValue(float value) {
        return new ConcreteValue(ValueTag.FLOAT, Float.floatToRawIntBits(value), null);
    }

    public static ConcreteValue doubleValue(double value) {
        return new ConcreteValue(ValueTag.DOUBLE, Double.doubleToRawLongBits(value), null);
    }

    public static ConcreteValue reference(ObjectInstance instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Use nullRef() for null references");
        }
        return new ConcreteValue(ValueTag.REFERENCE, 0, instance);
    }

    public static ConcreteValue nullRef() {
        return new ConcreteValue(ValueTag.NULL, 0, null);
    }

    public static ConcreteValue returnAddress(int address) {
        return new ConcreteValue(ValueTag.RETURN_ADDRESS, address, null);
    }

    public ValueTag getTag() {
        return tag;
    }

    public boolean isNull() {
        return tag == ValueTag.NULL;
    }

    public boolean isWide() {
        return tag.isWide();
    }

    public boolean isReference() {
        return tag == ValueTag.REFERENCE || tag == ValueTag.NULL;
    }

    public boolean isIntegral() {
        return tag == ValueTag.INT || tag == ValueTag.LONG;
    }

    public int getCategory() {
        return tag.getCategory();
    }

    public int asInt() {
        if (tag != ValueTag.INT) {
            throw new IllegalStateException("Value is not INT: " + tag);
        }
        return (int) bits;
    }

    public long asLong() {
        if (tag != ValueTag.LONG) {
            throw new IllegalStateException("Value is not LONG: " + tag);
        }
        return bits;
    }

    public float asFloat() {
        if (tag != ValueTag.FLOAT) {
            throw new IllegalStateException("Value is not FLOAT: " + tag);
        }
        return Float.intBitsToFloat((int) bits);
    }

    public double asDouble() {
        if (tag != ValueTag.DOUBLE) {
            throw new IllegalStateException("Value is not DOUBLE: " + tag);
        }
        return Double.longBitsToDouble(bits);
    }

    public ObjectInstance asReference() {
        if (tag == ValueTag.NULL) {
            return null;
        }
        if (tag != ValueTag.REFERENCE) {
            throw new IllegalStateException("Value is not REFERENCE: " + tag);
        }
        return ref;
    }

    public int asReturnAddress() {
        if (tag != ValueTag.RETURN_ADDRESS) {
            throw new IllegalStateException("Value is not RETURN_ADDRESS: " + tag);
        }
        return (int) bits;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConcreteValue)) return false;
        ConcreteValue that = (ConcreteValue) o;
        return bits == that.bits && tag == that.tag && Objects.equals(ref, that.ref);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, bits, ref);
    }

    @Override
    public String toString() {
        switch (tag) {
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
