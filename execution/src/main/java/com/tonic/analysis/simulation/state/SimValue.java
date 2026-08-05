package com.tonic.analysis.simulation.state;

import com.tonic.analysis.simulation.heap.AllocationSite;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Immutable simulated value carrying its type, defining instruction and, for
 * references, a points-to set and null state; usable as a map key.
 */
public class SimValue
{

    /**
     * What is known about a reference value's nullness.
     */
    public enum NullState
    {
        /**
         * The value is null on every path reaching this point, as for a simulated null constant.
         */
        DEFINITELY_NULL,
        /**
         * The value is non-null on every path, as for a fresh allocation.
         */
        DEFINITELY_NOT_NULL,
        /**
         * Nullness is unknown; also the result of merging two values whose states disagree, and
         * the default for a value with no null information.
         */
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

    private SimValue(IRType type, IRInstruction sourceInstruction, Value ssaValue, Object constantValue)
    {
        this(type, sourceInstruction, ssaValue, constantValue, Collections.emptySet(), NullState.MAYBE_NULL);
    }

    private SimValue(IRType type, IRInstruction sourceInstruction, Value ssaValue, Object constantValue, Set<AllocationSite> pointsTo, NullState nullState)
    {
        this.type = type;
        this.sourceInstruction = sourceInstruction;
        this.ssaValue = ssaValue;
        this.constantValue = constantValue;
        this.id = nextId++;
        this.pointsTo = pointsTo;
        this.nullState = nullState;
    }

    /**
     * Wraps an SSA value, adopting its type when it is present.
     *
     * @param value SSA value being simulated, may be null
     * @param source instruction that produced the value
     * @return the new simulated value
     */
    public static SimValue fromSSA(Value value, IRInstruction source)
    {
        IRType type = value != null ? value.getType() : null;
        return new SimValue(type, source, value, null);
    }

    /**
     * Creates a value with a known constant.
     *
     * @param value the constant
     * @param type type of the constant
     * @param source instruction that produced the value
     * @return the new simulated value
     */
    public static SimValue constant(Object value, IRType type, IRInstruction source)
    {
        return new SimValue(type, source, null, value);
    }

    /**
     * Creates a value carrying only type information.
     *
     * @param type type of the value
     * @param source instruction that produced the value
     * @return the new simulated value
     */
    public static SimValue ofType(IRType type, IRInstruction source)
    {
        return new SimValue(type, source, null, null);
    }

    /**
     * Creates an untyped value about which nothing is known.
     *
     * @param source instruction that produced the value
     * @return the new simulated value
     */
    public static SimValue unknown(IRInstruction source)
    {
        return new SimValue(null, source, null, null);
    }

    /**
     * @return the placeholder occupying the second slot of a long or double
     */
    public static SimValue wideSecondSlot()
    {
        return new SimValue(null, null, null, "WIDE_SECOND_SLOT");
    }

    /**
     * Creates a definitely non-null reference pointing at one allocation site.
     *
     * @param site site the reference points to
     * @param type type of the allocated object
     * @param source instruction that produced the value
     * @return the new simulated value
     */
    public static SimValue ofAllocation(AllocationSite site, IRType type, IRInstruction source)
    {
        return new SimValue(type, source, null, null, Collections.singleton(site), NullState.DEFINITELY_NOT_NULL);
    }

    /**
     * Creates a definitely null reference with an empty points-to set.
     *
     * @param type static type of the reference
     * @param source instruction that produced the value
     * @return the new simulated value
     */
    public static SimValue ofNull(IRType type, IRInstruction source)
    {
        return new SimValue(type, source, null, null, Collections.emptySet(), NullState.DEFINITELY_NULL);
    }

    /**
     * Creates a reference with a caller-supplied points-to set and null state.
     *
     * @param type static type of the reference
     * @param source instruction that produced the value
     * @param pointsTo allocation sites the reference may point to
     * @param nullState what is known about its nullness
     * @return the new simulated value
     */
    public static SimValue ofReference(IRType type, IRInstruction source, Set<AllocationSite> pointsTo, NullState nullState)
    {
        return new SimValue(type, source, null, null, pointsTo, nullState);
    }

    /**
     * Folds a collection into one value, unioning points-to sets and widening
     * disagreeing null states to MAYBE_NULL; the type and source come from the first element.
     *
     * @param values values to merge
     * @return null if the collection is null or empty, the sole element if there is one,
     *         otherwise the merged value
     */
    public static SimValue merge(Collection<SimValue> values)
    {
        if (values == null || values.isEmpty())
        {
            return null;
        }
        if (values.size() == 1)
        {
            return values.iterator().next();
        }

        Iterator<SimValue> it = values.iterator();
        SimValue first = it.next();
        IRType mergedType = first.type;
        Set<AllocationSite> mergedPointsTo = new HashSet<>(first.pointsTo);
        NullState mergedNullState = first.nullState;

        while (it.hasNext())
        {
            SimValue other = it.next();
            mergedPointsTo.addAll(other.pointsTo);
            mergedNullState = mergeNullStates(mergedNullState, other.nullState);
        }

        return new SimValue(mergedType, first.sourceInstruction, null, null, mergedPointsTo, mergedNullState);
    }

    private static NullState mergeNullStates(NullState a, NullState b)
    {
        if (a == b) return a;
        return NullState.MAYBE_NULL;
    }

    /**
     * @return the type, or null if untyped
     */
    public IRType getType()
    {
        return type;
    }

    /**
     * @return the instruction that produced this value
     */
    public IRInstruction getSourceInstruction()
    {
        return sourceInstruction;
    }

    /**
     * @return the underlying SSA value, or null if there is none
     */
    public Value getSSAValue()
    {
        return ssaValue;
    }

    /**
     * @return the constant, or null if the value is not constant
     */
    public Object getConstantValue()
    {
        return constantValue;
    }

    /**
     * @return true if a constant is known and it is not the wide-slot placeholder
     */
    public boolean isConstant()
    {
        return constantValue != null && !"WIDE_SECOND_SLOT".equals(constantValue);
    }

    /**
     * @return true if this is the second slot of a long or double
     */
    public boolean isWideSecondSlot()
    {
        return "WIDE_SECOND_SLOT".equals(constantValue);
    }

    /**
     * @return true if the type occupies two slots
     */
    public boolean isWide()
    {
        if (type == null) return false;
        return type.isTwoSlot();
    }

    /**
     * @return true if the type is a reference type
     */
    public boolean isReference()
    {
        return type != null && type.isReference();
    }

    /**
     * @return true if there is no type, SSA value, constant, or wide-slot marker
     */
    public boolean isUnknown()
    {
        return type == null && ssaValue == null && !isConstant() && !isWideSecondSlot();
    }

    /**
     * @return the identity used by equals and hashCode
     */
    public int getId()
    {
        return id;
    }

    /**
     * @return an unmodifiable view of the allocation sites this reference may point to
     */
    public Set<AllocationSite> getPointsTo()
    {
        return Collections.unmodifiableSet(pointsTo);
    }

    /**
     * @return true if the points-to set is non-empty
     */
    public boolean hasPointsTo()
    {
        return !pointsTo.isEmpty();
    }

    /**
     * @return what is known about this value's nullness
     */
    public NullState getNullState()
    {
        return nullState;
    }

    /**
     * @return true unless the value is known to be non-null
     */
    public boolean mayBeNull()
    {
        return nullState != NullState.DEFINITELY_NOT_NULL;
    }

    /**
     * @return true if the value is known to be null
     */
    public boolean isDefinitelyNull()
    {
        return nullState == NullState.DEFINITELY_NULL;
    }

    /**
     * @return true if the value is known to be non-null
     */
    public boolean isDefinitelyNotNull()
    {
        return nullState == NullState.DEFINITELY_NOT_NULL;
    }

    /**
     * Copies this value with a replaced points-to set.
     *
     * @param newPointsTo allocation sites for the copy
     * @return the copy
     */
    public SimValue withPointsTo(Set<AllocationSite> newPointsTo)
    {
        return new SimValue(type, sourceInstruction, ssaValue, constantValue, newPointsTo, nullState);
    }

    /**
     * Copies this value with one more allocation site in its points-to set.
     *
     * @param site site to add
     * @return the copy
     */
    public SimValue withAdditionalPointsTo(AllocationSite site)
    {
        Set<AllocationSite> newPointsTo = new HashSet<>(pointsTo);
        newPointsTo.add(site);
        return new SimValue(type, sourceInstruction, ssaValue, constantValue, newPointsTo, nullState);
    }

    /**
     * Copies this value with a replaced null state.
     *
     * @param newNullState null state for the copy
     * @return the copy
     */
    public SimValue withNullState(NullState newNullState)
    {
        return new SimValue(type, sourceInstruction, ssaValue, constantValue, pointsTo, newNullState);
    }

    /**
     * Merges another value into this one, unioning points-to sets and widening
     * disagreeing null states to MAYBE_NULL; the SSA value and constant are dropped.
     *
     * @param other value to merge, may be null
     * @return this value if the other is null or equal, otherwise the merged value
     */
    public SimValue merge(SimValue other)
    {
        if (other == null) return this;
        if (this.equals(other)) return this;

        Set<AllocationSite> mergedPointsTo = new HashSet<>(this.pointsTo);
        mergedPointsTo.addAll(other.pointsTo);

        NullState mergedNullState = mergeNullStates(this.nullState, other.nullState);

        return new SimValue(this.type, this.sourceInstruction, null, null, mergedPointsTo, mergedNullState);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof SimValue)) return false;
        SimValue simValue = (SimValue) o;
        return id == simValue.id;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SimValue[");
        sb.append("id=").append(id);
        if (type != null)
        {
            sb.append(", type=").append(type);
        }
        if (isConstant())
        {
            sb.append(", const=").append(constantValue);
        }
        if (isWideSecondSlot())
        {
            sb.append(", WIDE_SLOT_2");
        }
        if (!pointsTo.isEmpty())
        {
            sb.append(", pointsTo=").append(pointsTo.size()).append(" sites");
        }
        if (nullState != NullState.MAYBE_NULL)
        {
            sb.append(", ").append(nullState);
        }
        sb.append("]");
        return sb.toString();
    }
}
