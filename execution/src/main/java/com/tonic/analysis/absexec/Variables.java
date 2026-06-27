package com.tonic.analysis.absexec;

/**
 * The abstract local-variable table: an array of {@link VarCtx} indexed by slot. Copy-constructed when a
 * {@link Frame} forks at a branch.
 */
public final class Variables {

    private final VarCtx[] slots;

    public Variables(int maxLocals) {
        slots = new VarCtx[Math.max(maxLocals, 1)];
    }

    public Variables(Variables other) {
        this.slots = other.slots.clone();
    }

    public void set(int index, VarCtx value) {
        if (index >= 0 && index < slots.length) {
            slots[index] = value;
        }
    }

    public VarCtx get(int index) {
        return index >= 0 && index < slots.length ? slots[index] : null;
    }

    public int size() {
        return slots.length;
    }
}
