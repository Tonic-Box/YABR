package com.tonic.analysis.execution.state;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.parser.MethodEntry;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ConcreteLocals {

    private final ConcreteValue[] locals;
    private final int maxLocals;

    public ConcreteLocals(int maxLocals) {
        this.maxLocals = maxLocals;
        this.locals = new ConcreteValue[maxLocals];
    }

    public void set(int slot, ConcreteValue value) {
        checkSlot(slot);
        locals[slot] = value;

        if (value.isWide()) {
            if (slot + 1 >= maxLocals) {
                throw new IllegalArgumentException("Wide value at slot " + slot + " exceeds max locals " + maxLocals);
            }
            locals[slot + 1] = null;
        }
    }

    public void setInt(int slot, int value) {
        set(slot, ConcreteValue.intValue(value));
    }

    public void setLong(int slot, long value) {
        set(slot, ConcreteValue.longValue(value));
    }

    public void setFloat(int slot, float value) {
        set(slot, ConcreteValue.floatValue(value));
    }

    public void setDouble(int slot, double value) {
        set(slot, ConcreteValue.doubleValue(value));
    }

    public void setReference(int slot, ObjectInstance instance) {
        set(slot, ConcreteValue.reference(instance));
    }

    public void setNull(int slot) {
        set(slot, ConcreteValue.nullRef());
    }

    public ConcreteValue get(int slot) {
        checkSlot(slot);
        ConcreteValue value = locals[slot];
        if (value == null) {
            throw new IllegalStateException("Local variable " + slot + " is not defined");
        }
        return value;
    }

    public int getInt(int slot) {
        return get(slot).asInt();
    }

    public long getLong(int slot) {
        return get(slot).asLong();
    }

    public float getFloat(int slot) {
        return get(slot).asFloat();
    }

    public double getDouble(int slot) {
        return get(slot).asDouble();
    }

    public ObjectInstance getReference(int slot) {
        return get(slot).asReference();
    }

    public int size() {
        return maxLocals;
    }

    public boolean isDefined(int slot) {
        if (slot < 0 || slot >= maxLocals) {
            return false;
        }
        return locals[slot] != null;
    }

    public Map<Integer, ConcreteValue> snapshot() {
        Map<Integer, ConcreteValue> result = new HashMap<>();
        for (int i = 0; i < maxLocals; i++) {
            if (locals[i] != null) {
                result.put(i, locals[i]);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public static ConcreteLocals forMethod(MethodEntry method, ConcreteValue[] args) {
        if (method == null) {
            throw new IllegalArgumentException("Method cannot be null");
        }

        int maxLocals = 0;
        if (method.getCodeAttribute() != null) {
            maxLocals = method.getCodeAttribute().getMaxLocals();
        } else {
            maxLocals = (args != null ? args.length : 0) + 10;
        }

        ConcreteLocals locals = new ConcreteLocals(maxLocals);

        if (args != null) {
            int slot = 0;
            for (ConcreteValue arg : args) {
                if (slot >= maxLocals) {
                    System.out.println("[ConcreteLocals] TOO MANY ARGS: slot=" + slot + " maxLocals=" + maxLocals +
                        " method=" + method.getOwnerName() + "." + method.getName() + method.getDesc());
                    throw new IllegalArgumentException("Too many arguments for method max locals: " + maxLocals);
                }
                locals.set(slot, arg);
                slot += arg.getCategory();
            }
        }

        return locals;
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= maxLocals) {
            throw new IndexOutOfBoundsException("Slot " + slot + " out of bounds [0, " + maxLocals + ")");
        }
    }

    @Override
    public String toString() {
        return "ConcreteLocals[max=" + maxLocals + ", values=" + snapshot() + "]";
    }
}
