package com.tonic.analysis.oracle;

import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;
import com.tonic.util.DescriptorUtil;
import com.tonic.util.Modifiers;

import java.util.ArrayList;
import java.util.List;

/**
 * A heap-independent description of one input vector: a primitive value (as raw bits) and a
 * null-flag per parameter. Kept separate from {@link ConcreteValue} so the same input can be
 * re-materialized into a fresh heap for each execution and mutated without touching engine state.
 * Objects stay opaque (a null or a fresh blank instance); only primitives carry real values.
 */
final class InputSpec {

    final long[] prim;
    final boolean[] refNull;

    InputSpec(int paramCount) {
        this.prim = new long[paramCount];
        this.refNull = new boolean[paramCount];
    }

    InputSpec copy() {
        InputSpec c = new InputSpec(prim.length);
        System.arraycopy(prim, 0, c.prim, 0, prim.length);
        System.arraycopy(refNull, 0, c.refNull, 0, refNull.length);
        return c;
    }

    /** Builds the engine argument array for {@code method} (receiver first for instance methods). */
    static ConcreteValue[] materialize(InputSpec spec, MethodEntry method, HeapManager heap) {
        List<String> params = DescriptorUtil.parseParameterDescriptors(method.getDesc());
        List<ConcreteValue> out = new ArrayList<>();
        if (!Modifiers.isStatic(method.getAccess())) {
            out.add(ConcreteValue.reference(heap.newObject(method.getClassFile().getClassName())));
        }
        for (int i = 0; i < params.size(); i++) {
            out.add(materializeOne(params.get(i), spec.prim[i], spec.refNull[i], heap));
        }
        return out.toArray(new ConcreteValue[0]);
    }

    private static ConcreteValue materializeOne(String desc, long v, boolean refNull, HeapManager heap) {
        switch (desc.charAt(0)) {
            case 'Z': return ConcreteValue.intValue((int) (v & 1L));
            case 'B': return ConcreteValue.intValue((byte) v);
            case 'C': return ConcreteValue.intValue((char) v);
            case 'S': return ConcreteValue.intValue((short) v);
            case 'I': return ConcreteValue.intValue((int) v);
            case 'J': return ConcreteValue.longValue(v);
            case 'F': return ConcreteValue.floatValue((float) v);
            case 'D': return ConcreteValue.doubleValue((double) v);
            case 'L': return refNull ? ConcreteValue.nullRef()
                    : ConcreteValue.reference(heap.newObject(desc.substring(1, desc.length() - 1)));
            default: return ConcreteValue.nullRef();
        }
    }

    static boolean isPrimitive(String desc) {
        char c = desc.charAt(0);
        return c != 'L' && c != '[';
    }

    static boolean isNullableRef(String desc) {
        return desc.charAt(0) == 'L';
    }
}
