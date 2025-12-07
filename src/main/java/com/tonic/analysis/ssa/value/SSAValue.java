package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.type.IRType;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a value defined by an SSA instruction.
 * Each SSAValue has exactly one definition point.
 */
@Getter
public class SSAValue implements Value {

    private static int nextId = 0;

    private final int id;
    private final IRType type;
    @Setter
    private String name;
    @Setter
    private IRInstruction definition;
    private final List<IRInstruction> uses;

    /**
     * Creates an SSA value with the given type.
     *
     * @param type the IR type of this value
     */
    public SSAValue(IRType type) {
        this.id = nextId++;
        this.type = type;
        this.name = "v" + id;
        this.uses = new ArrayList<>();
    }

    /**
     * Creates an SSA value with the given type and name.
     *
     * @param type the IR type of this value
     * @param name the name for this value
     */
    public SSAValue(IRType type, String name) {
        this.id = nextId++;
        this.type = type;
        this.name = name;
        this.uses = new ArrayList<>();
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    /**
     * Adds a use of this value by an instruction.
     *
     * @param instruction the instruction using this value
     */
    public void addUse(IRInstruction instruction) {
        uses.add(instruction);
    }

    /**
     * Removes a use of this value by an instruction.
     *
     * @param instruction the instruction to remove
     */
    public void removeUse(IRInstruction instruction) {
        uses.remove(instruction);
    }

    /**
     * Checks if this value has any uses.
     *
     * @return true if there are uses, false otherwise
     */
    public boolean hasUses() {
        return !uses.isEmpty();
    }

    /**
     * Gets the number of uses of this value.
     *
     * @return the use count
     */
    public int getUseCount() {
        return uses.size();
    }

    /**
     * Replaces all uses of this value with a new value.
     *
     * @param newValue the value to replace with
     */
    public void replaceAllUsesWith(Value newValue) {
        for (IRInstruction use : new ArrayList<>(uses)) {
            use.replaceOperand(this, newValue);
        }
        uses.clear();
    }

    /**
     * Resets the ID counter for SSA values.
     */
    public static void resetIdCounter() {
        nextId = 0;
    }

    @Override
    public String toString() {
        return name;
    }
}
