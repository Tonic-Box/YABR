package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable view of a frame's operand stack, taken at one point in execution.
 */
public final class StackSnapshot
{

    private final List<ValueInfo> values;

    /**
     * Copies every stack entry into an unmodifiable list of value descriptions.
     * @param stack the live operand stack
     * @throws IllegalArgumentException if the stack is null
     */
    public StackSnapshot(ConcreteStack stack)
    {
        if (stack == null)
        {
            throw new IllegalArgumentException("Stack cannot be null");
        }

        List<ValueInfo> temp = new ArrayList<>();
        List<ConcreteValue> snapshot = stack.snapshot();

        for (ConcreteValue value : snapshot)
        {
            temp.add(new ValueInfo(value));
        }

        this.values = Collections.unmodifiableList(temp);
    }

    /**
     * @return the values
     */
    public List<ValueInfo> getValues()
    {
        return values;
    }

    /**
     * @return the number of entries on the stack
     */
    public int depth()
    {
        return values.size();
    }

    @Override
    public String toString()
    {
        return "StackSnapshot{depth=" + depth() + ", values=" + values + "}";
    }
}
