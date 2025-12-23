package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StackSnapshot {

    private final List<ValueInfo> values;

    public StackSnapshot(ConcreteStack stack) {
        if (stack == null) {
            throw new IllegalArgumentException("Stack cannot be null");
        }

        List<ValueInfo> temp = new ArrayList<>();
        List<ConcreteValue> snapshot = stack.snapshot();

        for (ConcreteValue value : snapshot) {
            temp.add(new ValueInfo(value));
        }

        this.values = Collections.unmodifiableList(temp);
    }

    public List<ValueInfo> getValues() {
        return values;
    }

    public int depth() {
        return values.size();
    }

    @Override
    public String toString() {
        return "StackSnapshot{depth=" + depth() + ", values=" + values + "}";
    }
}
