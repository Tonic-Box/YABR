package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.ir.ConstantInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.type.IRType;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a value defined by an SSA instruction.
 * Each SSAValue has exactly one definition point.
 */
public class SSAValue implements Value {

    private static final ThreadLocal<int[]> NEXT_ID = ThreadLocal.withInitial(() -> new int[1]);

    private final int id;
    private final IRType type;
    private String name;
    private IRInstruction definition;
    private final List<IRInstruction> uses;

    /**
     * Creates an SSA value with the given type.
     *
     * @param type the IR type of this value
     */
    public SSAValue(IRType type) {
        this.id = NEXT_ID.get()[0]++;
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
        this.id = NEXT_ID.get()[0]++;
        this.type = type;
        this.name = name;
        this.uses = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public IRType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IRInstruction getDefinition() {
        return definition;
    }

    public void setDefinition(IRInstruction definition) {
        this.definition = definition;
    }

    public List<IRInstruction> getUses() {
        return uses;
    }

    @Override
    public boolean isConstant() {
        return definition instanceof ConstantInstruction;
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
        NEXT_ID.get()[0] = 0;
    }

    /**
     * Identity by {@code id}. Ids are unique within a method (the counter is reset
     * per lift in {@link com.tonic.analysis.ssa.lift.BytecodeLifter}), so this is
     * equivalent to object identity in every collection scope, while giving
     * deterministic hashing/iteration order across runs.
     */
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof SSAValue && ((SSAValue) o).id == id);
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return name;
    }
}
