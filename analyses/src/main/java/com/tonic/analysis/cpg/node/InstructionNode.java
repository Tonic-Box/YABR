package com.tonic.analysis.cpg.node;

import com.tonic.analysis.ssa.ir.*;

/**
 * CPG node wrapping a single IR instruction, positioned by block id and index.
 */
public class InstructionNode extends CPGNode
{

    private final IRInstruction instruction;
    private final int instructionIndex;
    private final int blockId;

    /**
     * Creates a node for an IR instruction.
     * @param id the unique node id
     * @param instruction the wrapped instruction
     * @param blockId the id of the containing block
     * @param instructionIndex the instruction's position within the block
     */
    public InstructionNode(long id, IRInstruction instruction, int blockId, int instructionIndex)
    {
        super(id, CPGNodeType.INSTRUCTION);
        this.instruction = instruction;
        this.blockId = blockId;
        this.instructionIndex = instructionIndex;

        setProperty("blockId", blockId);
        setProperty("index", instructionIndex);
        setProperty("instrType", instruction.getClass().getSimpleName());

        if (instruction instanceof InvokeInstruction)
        {
            InvokeInstruction invoke = (InvokeInstruction) instruction;
            setProperty("targetOwner", invoke.getOwner());
            setProperty("targetName", invoke.getName());
            setProperty("targetDescriptor", invoke.getDescriptor());
        }
        else if (instruction instanceof FieldAccessInstruction)
        {
            FieldAccessInstruction field = (FieldAccessInstruction) instruction;
            setProperty("fieldOwner", field.getOwner());
            setProperty("fieldName", field.getName());
            setProperty("isStore", field.isStore());
        }
        else if (instruction instanceof NewInstruction)
        {
            NewInstruction newInstr = (NewInstruction) instruction;
            setProperty("allocType", newInstr.getClassName());
        }
    }

    /**
     * @return the instruction
     */
    public IRInstruction getInstruction()
    {
        return instruction;
    }

    /**
     * @return the instruction index
     */
    public int getInstructionIndex()
    {
        return instructionIndex;
    }

    /**
     * @return the block id
     */
    public int getBlockId()
    {
        return blockId;
    }

    @Override
    public String getLabel()
    {
        if (instruction.hasResult())
        {
            return instruction.getResult().getName() + " = " + instruction.getClass().getSimpleName();
        }
        return instruction.getClass().getSimpleName();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getUnderlying()
    {
        return (T) instruction;
    }

    /**
     * @return whether the instruction is a method invocation
     */
    public boolean isInvoke()
    {
        return instruction instanceof InvokeInstruction;
    }

    /**
     * @return whether the instruction reads or writes a field
     */
    public boolean isFieldAccess()
    {
        return instruction instanceof FieldAccessInstruction;
    }

    /**
     * @return whether the instruction allocates an object or array
     */
    public boolean isAllocation()
    {
        return instruction instanceof NewInstruction || instruction instanceof NewArrayInstruction;
    }

    /**
     * @return whether the instruction is a return
     */
    public boolean isReturn()
    {
        return instruction instanceof ReturnInstruction;
    }

    /**
     * @return whether the instruction is a conditional branch
     */
    public boolean isBranch()
    {
        return instruction instanceof BranchInstruction;
    }

    /**
     * @return whether the instruction is a phi
     */
    public boolean isPhi()
    {
        return instruction instanceof PhiInstruction;
    }

    /**
     * @return whether the instruction produces a result value
     */
    public boolean hasResult()
    {
        return instruction.hasResult();
    }

    /**
     * @return whether the instruction ends its block
     */
    public boolean isTerminator()
    {
        return instruction.isTerminator();
    }

    @Override
    public String toString()
    {
        return String.format("InstrNode[%d: %s @ B%d:%d]",
            getId(), instruction.getClass().getSimpleName(), blockId, instructionIndex);
    }
}
