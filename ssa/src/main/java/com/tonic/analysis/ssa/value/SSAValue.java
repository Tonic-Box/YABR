package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.ir.ConstantInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.type.IRType;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a value defined by an SSA instruction.
 */
public class SSAValue implements Value
{

    private static final ThreadLocal<int[]> NEXT_ID = ThreadLocal.withInitial(() -> new int[1]);

    private final int id;
    private final IRType type;
    private String name;
    private IRInstruction definition;
    private final List<IRInstruction> uses;

    /**
     * Creates an SSA value with the given type.
     * @param type the IR type of this value
     */
    public SSAValue(IRType type)
    {
        this.id = NEXT_ID.get()[0]++;
        this.type = type;
        this.name = "v" + id;
        this.uses = new ArrayList<>();
    }

    /**
     * Creates an SSA value with the given type and name.
     * @param type the IR type of this value
     * @param name the name for this value
     */
    public SSAValue(IRType type, String name)
    {
        this.id = NEXT_ID.get()[0]++;
        this.type = type;
        this.name = name;
        this.uses = new ArrayList<>();
    }

    /**
     * @return the id
     */
    public int getId()
    {
        return id;
    }

    /**
     * @return the type
     */
    public IRType getType()
    {
        return type;
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Renames this value.
     *
     * @param name the new name
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return the definition
     */
    public IRInstruction getDefinition()
    {
        return definition;
    }

    /**
     * Records the instruction that produces this value.
     *
     * @param definition the defining instruction
     */
    public void setDefinition(IRInstruction definition)
    {
        this.definition = definition;
    }

    /**
     * @return the uses
     */
    public List<IRInstruction> getUses()
    {
        return uses;
    }

    @Override
    public boolean isConstant()
    {
        return definition instanceof ConstantInstruction;
    }

    /**
     * Adds a use of this value by an instruction.
     * @param instruction the instruction using this value
     */
    public void addUse(IRInstruction instruction)
    {
        uses.add(instruction);
    }

    /**
     * Removes a use of this value by an instruction.
     * @param instruction the instruction to remove
     */
    public void removeUse(IRInstruction instruction)
    {
        uses.remove(instruction);
    }

    /**
     * Checks if this value has any uses.
     * @return true if there are uses, false otherwise
     */
    public boolean hasUses()
    {
        return !uses.isEmpty();
    }

    /**
     * Gets the number of uses of this value.
     * @return the use count
     */
    public int getUseCount()
    {
        return uses.size();
    }

    /**
     * Replaces all uses of this value with a new value.
     * @param newValue the value to replace with
     */
    public void replaceAllUsesWith(Value newValue)
    {
        for (IRInstruction use : new ArrayList<>(uses))
        {
            use.replaceOperand(this, newValue);
        }
        uses.clear();
    }

    /**
     * Resets the ID counter for SSA values.
     */
    public static void resetIdCounter()
    {
        NEXT_ID.get()[0] = 0;
    }

    /**
     * Identity by {@code id}.
     */
    @Override
    public boolean equals(Object o)
    {
        return this == o || (o instanceof SSAValue && ((SSAValue) o).id == id);
    }

    @Override
    public int hashCode()
    {
        return id;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
