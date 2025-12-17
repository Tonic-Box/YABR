package com.tonic.analysis.simulation.state;

import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.Value;

import java.util.Objects;

/**
 * Represents a simulated value during execution simulation.
 * Tracks the value's type, source instruction, and optionally concrete value.
 *
 * <p>SimValues are immutable and can be used as map keys.
 */
public class SimValue {

    private final IRType type;
    private final IRInstruction sourceInstruction;
    private final Value ssaValue;
    private final Object constantValue;
    private final int id;

    private static int nextId = 0;

    private SimValue(IRType type, IRInstruction sourceInstruction, Value ssaValue, Object constantValue) {
        this.type = type;
        this.sourceInstruction = sourceInstruction;
        this.ssaValue = ssaValue;
        this.constantValue = constantValue;
        this.id = nextId++;
    }

    /**
     * Creates a SimValue from an SSA value.
     */
    public static SimValue fromSSA(Value value, IRInstruction source) {
        IRType type = value != null ? value.getType() : null;
        return new SimValue(type, source, value, null);
    }

    /**
     * Creates a SimValue with a known constant value.
     */
    public static SimValue constant(Object value, IRType type, IRInstruction source) {
        return new SimValue(type, source, null, value);
    }

    /**
     * Creates a SimValue with just type information.
     */
    public static SimValue ofType(IRType type, IRInstruction source) {
        return new SimValue(type, source, null, null);
    }

    /**
     * Creates an unknown/untyped SimValue.
     */
    public static SimValue unknown(IRInstruction source) {
        return new SimValue(null, source, null, null);
    }

    /**
     * Creates a placeholder for wide value second slot.
     */
    public static SimValue wideSecondSlot() {
        return new SimValue(null, null, null, "WIDE_SECOND_SLOT");
    }

    /**
     * Gets the type of this value.
     */
    public IRType getType() {
        return type;
    }

    /**
     * Gets the instruction that produced this value.
     */
    public IRInstruction getSourceInstruction() {
        return sourceInstruction;
    }

    /**
     * Gets the underlying SSA value if available.
     */
    public Value getSSAValue() {
        return ssaValue;
    }

    /**
     * Gets the constant value if this is a constant.
     */
    public Object getConstantValue() {
        return constantValue;
    }

    /**
     * Returns true if this value has a known constant.
     */
    public boolean isConstant() {
        return constantValue != null && !"WIDE_SECOND_SLOT".equals(constantValue);
    }

    /**
     * Returns true if this is the second slot of a wide (long/double) value.
     */
    public boolean isWideSecondSlot() {
        return "WIDE_SECOND_SLOT".equals(constantValue);
    }

    /**
     * Returns true if this is a wide type (long or double).
     */
    public boolean isWide() {
        if (type == null) return false;
        return type.isTwoSlot();
    }

    /**
     * Returns true if this is a reference type.
     */
    public boolean isReference() {
        return type != null && type.isReference();
    }

    /**
     * Returns true if this is an unknown/untyped value.
     */
    public boolean isUnknown() {
        return type == null && ssaValue == null && !isConstant() && !isWideSecondSlot();
    }

    /**
     * Gets the unique ID of this value.
     */
    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimValue)) return false;
        SimValue simValue = (SimValue) o;
        return id == simValue.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SimValue[");
        sb.append("id=").append(id);
        if (type != null) {
            sb.append(", type=").append(type);
        }
        if (isConstant()) {
            sb.append(", const=").append(constantValue);
        }
        if (isWideSecondSlot()) {
            sb.append(", WIDE_SLOT_2");
        }
        sb.append("]");
        return sb.toString();
    }
}
