package com.tonic.analysis.simulation.state;

import com.tonic.analysis.simulation.heap.AllocationSite;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Represents a simulated value during execution simulation.
 * Tracks the value's type, source instruction, and optionally concrete value.
 * For reference types, also tracks points-to sets and null state.
 *
 * <p>SimValues are immutable and can be used as map keys.
 */
public class SimValue {

    public enum NullState {
        DEFINITELY_NULL,
        DEFINITELY_NOT_NULL,
        MAYBE_NULL
    }

    private final IRType type;
    private final IRInstruction sourceInstruction;
    private final Value ssaValue;
    private final Object constantValue;
    private final int id;
    private final Set<AllocationSite> pointsTo;
    private final NullState nullState;

    private static int nextId = 0;

    private SimValue(IRType type, IRInstruction sourceInstruction, Value ssaValue, Object constantValue) {
        this(type, sourceInstruction, ssaValue, constantValue, Collections.emptySet(), NullState.MAYBE_NULL);
    }

    private SimValue(IRType type, IRInstruction sourceInstruction, Value ssaValue, Object constantValue,
                     Set<AllocationSite> pointsTo, NullState nullState) {
        this.type = type;
        this.sourceInstruction = sourceInstruction;
        this.ssaValue = ssaValue;
        this.constantValue = constantValue;
        this.id = nextId++;
        this.pointsTo = pointsTo;
        this.nullState = nullState;
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
     * Creates a SimValue for an allocation that points to a specific allocation site.
     */
    public static SimValue ofAllocation(AllocationSite site, IRType type, IRInstruction source) {
        return new SimValue(type, source, null, null,
            Collections.singleton(site), NullState.DEFINITELY_NOT_NULL);
    }

    /**
     * Creates a definitely null SimValue.
     */
    public static SimValue ofNull(IRType type, IRInstruction source) {
        return new SimValue(type, source, null, null,
            Collections.emptySet(), NullState.DEFINITELY_NULL);
    }

    /**
     * Creates a SimValue with specific points-to set and null state.
     */
    public static SimValue ofReference(IRType type, IRInstruction source,
                                        Set<AllocationSite> pointsTo, NullState nullState) {
        return new SimValue(type, source, null, null, pointsTo, nullState);
    }

    /**
     * Merges multiple SimValues into one with unioned points-to sets.
     */
    public static SimValue merge(Collection<SimValue> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() == 1) {
            return values.iterator().next();
        }

        Iterator<SimValue> it = values.iterator();
        SimValue first = it.next();
        IRType mergedType = first.type;
        Set<AllocationSite> mergedPointsTo = new HashSet<>(first.pointsTo);
        NullState mergedNullState = first.nullState;

        while (it.hasNext()) {
            SimValue other = it.next();
            mergedPointsTo.addAll(other.pointsTo);
            mergedNullState = mergeNullStates(mergedNullState, other.nullState);
        }

        return new SimValue(mergedType, first.sourceInstruction, null, null,
            mergedPointsTo, mergedNullState);
    }

    private static NullState mergeNullStates(NullState a, NullState b) {
        if (a == b) return a;
        return NullState.MAYBE_NULL;
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

    /**
     * Gets the set of allocation sites this reference may point to.
     */
    public Set<AllocationSite> getPointsTo() {
        return Collections.unmodifiableSet(pointsTo);
    }

    /**
     * Returns true if this value has points-to information.
     */
    public boolean hasPointsTo() {
        return !pointsTo.isEmpty();
    }

    /**
     * Gets the null state of this value.
     */
    public NullState getNullState() {
        return nullState;
    }

    /**
     * Returns true if this value may be null.
     */
    public boolean mayBeNull() {
        return nullState != NullState.DEFINITELY_NOT_NULL;
    }

    /**
     * Returns true if this value is definitely null.
     */
    public boolean isDefinitelyNull() {
        return nullState == NullState.DEFINITELY_NULL;
    }

    /**
     * Returns true if this value is definitely not null.
     */
    public boolean isDefinitelyNotNull() {
        return nullState == NullState.DEFINITELY_NOT_NULL;
    }

    /**
     * Returns a new SimValue with the pointsTo set updated.
     */
    public SimValue withPointsTo(Set<AllocationSite> newPointsTo) {
        return new SimValue(type, sourceInstruction, ssaValue, constantValue, newPointsTo, nullState);
    }

    /**
     * Returns a new SimValue with an additional allocation site in pointsTo.
     */
    public SimValue withAdditionalPointsTo(AllocationSite site) {
        Set<AllocationSite> newPointsTo = new HashSet<>(pointsTo);
        newPointsTo.add(site);
        return new SimValue(type, sourceInstruction, ssaValue, constantValue, newPointsTo, nullState);
    }

    /**
     * Returns a new SimValue with the null state updated.
     */
    public SimValue withNullState(NullState newNullState) {
        return new SimValue(type, sourceInstruction, ssaValue, constantValue, pointsTo, newNullState);
    }

    /**
     * Merges this SimValue with another, unioning points-to sets.
     */
    public SimValue merge(SimValue other) {
        if (other == null) return this;
        if (this.equals(other)) return this;

        Set<AllocationSite> mergedPointsTo = new HashSet<>(this.pointsTo);
        mergedPointsTo.addAll(other.pointsTo);

        NullState mergedNullState = mergeNullStates(this.nullState, other.nullState);

        return new SimValue(this.type, this.sourceInstruction, null, null,
            mergedPointsTo, mergedNullState);
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
        if (!pointsTo.isEmpty()) {
            sb.append(", pointsTo=").append(pointsTo.size()).append(" sites");
        }
        if (nullState != NullState.MAYBE_NULL) {
            sb.append(", ").append(nullState);
        }
        sb.append("]");
        return sb.toString();
    }
}
