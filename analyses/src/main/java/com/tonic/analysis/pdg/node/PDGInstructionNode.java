package com.tonic.analysis.pdg.node;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import java.util.List;

/**
 * A PDG node standing for a single IR instruction, typed PHI for phis and INSTRUCTION otherwise.
 */
public class PDGInstructionNode extends PDGNode
{

    private final IRInstruction instruction;
    private final int instructionIndex;

    /**
     * Creates a node for an instruction, deriving its node type from whether the instruction is a phi.
     * @param id the graph-unique node id
     * @param instruction the instruction represented
     * @param block the block containing the instruction
     * @param instructionIndex the position within the block, counting phis first
     */
    public PDGInstructionNode(int id, IRInstruction instruction, IRBlock block, int instructionIndex)
    {
        super(id, determineNodeType(instruction), block);
        this.instruction = instruction;
        this.instructionIndex = instructionIndex;
    }

    private static PDGNodeType determineNodeType(IRInstruction instruction)
    {
        if (instruction instanceof PhiInstruction)
        {
            return PDGNodeType.PHI;
        }
        return PDGNodeType.INSTRUCTION;
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

    @Override
    public String getLabel()
    {
        SSAValue result = instruction.getResult();
        if (result != null)
        {
            return result.getName() + " = " + instruction.getClass().getSimpleName();
        }
        return instruction.getClass().getSimpleName();
    }

    @Override
    public List<Value> getUsedValues()
    {
        return instruction.getOperands();
    }

    @Override
    public SSAValue getDefinedValue()
    {
        return instruction.getResult();
    }

    /**
     * @return true if the instruction is a phi
     */
    public boolean isPhi()
    {
        return instruction instanceof PhiInstruction;
    }

    /**
     * @return true if the instruction ends its block
     */
    public boolean isTerminator()
    {
        return instruction.isTerminator();
    }

    /**
     * @return true if the instruction defines a value
     */
    public boolean hasResult()
    {
        return instruction.hasResult();
    }

    @Override
    public String toString()
    {
        return String.format("PDGInstr[%d: %s @ B%d:%d]",
            getId(),
            getLabel(),
            getBlock() != null ? getBlock().getId() : -1,
            instructionIndex);
    }
}
