package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.Objects;

public final class ValueInfo {

    private final String type;
    private final String valueStr;
    private final Object rawValue;

    public ValueInfo(ConcreteValue value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        this.type = value.getTag().name();
        this.valueStr = formatValue(value);
        this.rawValue = extractRawValue(value);
    }

    private String formatValue(ConcreteValue value) {
        switch (value.getTag()) {
            case INT:
                return String.valueOf(value.asInt());
            case LONG:
                return value.asLong() + "L";
            case FLOAT:
                return value.asFloat() + "f";
            case DOUBLE:
                return String.valueOf(value.asDouble());
            case REFERENCE:
                ObjectInstance ref = value.asReference();
                return ref.toString();
            case NULL:
                return "null";
            case RETURN_ADDRESS:
                return "retAddr(" + value.asReturnAddress() + ")";
            default:
                return "unknown";
        }
    }

    private Object extractRawValue(ConcreteValue value) {
        switch (value.getTag()) {
            case INT:
                return value.asInt();
            case LONG:
                return value.asLong();
            case FLOAT:
                return value.asFloat();
            case DOUBLE:
                return value.asDouble();
            case REFERENCE:
                return value.asReference();
            case NULL:
                return null;
            case RETURN_ADDRESS:
                return value.asReturnAddress();
            default:
                return null;
        }
    }

    public String getType() {
        return type;
    }

    public String getValueString() {
        return valueStr;
    }

    public Object getRawValue() {
        return rawValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValueInfo)) return false;
        ValueInfo valueInfo = (ValueInfo) o;
        return type.equals(valueInfo.type) &&
               valueStr.equals(valueInfo.valueStr) &&
               Objects.equals(rawValue, valueInfo.rawValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, valueStr, rawValue);
    }

    @Override
    public String toString() {
        return "ValueInfo{type=" + type + ", value=" + valueStr + "}";
    }
}
