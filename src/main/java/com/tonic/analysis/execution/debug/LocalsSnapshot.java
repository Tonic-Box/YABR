package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalsSnapshot {

    private final Map<Integer, ValueInfo> values;

    public LocalsSnapshot(ConcreteLocals locals) {
        if (locals == null) {
            throw new IllegalArgumentException("Locals cannot be null");
        }

        Map<Integer, ValueInfo> temp = new LinkedHashMap<>();
        Map<Integer, ConcreteValue> snapshot = locals.snapshot();

        for (Map.Entry<Integer, ConcreteValue> entry : snapshot.entrySet()) {
            temp.put(entry.getKey(), new ValueInfo(entry.getValue()));
        }

        this.values = Collections.unmodifiableMap(temp);
    }

    public Map<Integer, ValueInfo> getValues() {
        return values;
    }

    public ValueInfo get(int slot) {
        return values.get(slot);
    }

    public int size() {
        return values.size();
    }

    @Override
    public String toString() {
        return "LocalsSnapshot{size=" + size() + ", values=" + values + "}";
    }
}
