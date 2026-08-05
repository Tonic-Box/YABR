package com.tonic.analysis.dataflow;

import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.SSAValue;

import java.util.Objects;

/**
 * A node in the data flow graph standing for a value or operation, with mutable taint state.
 */
public class DataFlowNode
{

    private final int id;
    private final DataFlowNodeType type;
    private final String name;
    private final String description;

    // SSA/IR context
    private final SSAValue ssaValue;
    private final IRInstruction instruction;
    private final int blockId;
    private final int instructionIndex;

    // Taint tracking
    private boolean isTainted;
    private String taintSource;

    private DataFlowNode(Builder builder)
    {
        this.id = builder.id;
        this.type = builder.type;
        this.name = builder.name;
        this.description = builder.description;
        this.ssaValue = builder.ssaValue;
        this.instruction = builder.instruction;
        this.blockId = builder.blockId;
        this.instructionIndex = builder.instructionIndex;
        this.isTainted = false;
        this.taintSource = null;
    }

    // Getters

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
    public DataFlowNodeType getType()
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
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * @return the ssa value
     */
    public SSAValue getSsaValue()
    {
        return ssaValue;
    }

    /**
     * @return the instruction
     */
    public IRInstruction getInstruction()
    {
        return instruction;
    }

    /**
     * @return the block id
     */
    public int getBlockId()
    {
        return blockId;
    }

    /**
     * @return the instruction index
     */
    public int getInstructionIndex()
    {
        return instructionIndex;
    }

    /**
     * @return whether tainted
     */
    public boolean isTainted()
    {
        return isTainted;
    }

    /**
     * @return the taint source
     */
    public String getTaintSource()
    {
        return taintSource;
    }

    // Setters

    /**
     * Sets the taint flag without touching the recorded source.
     * @param tainted new taint state
     */
    public void setTainted(boolean tainted)
    {
        this.isTainted = tainted;
    }

    /**
     * Records where the taint came from, setting the tainted flag to match.
     * @param source origin description, or null to clear the taint
     */
    public void setTaintSource(String source)
    {
        this.taintSource = source;
        this.isTainted = source != null;
    }

    // Display

    /**
     * @return the name, else the SSA value's name, else the type display name with the id appended
     */
    public String getLabel()
    {
        if (name != null && !name.isEmpty())
        {
            return name;
        }
        if (ssaValue != null)
        {
            return ssaValue.getName();
        }
        return type.getDisplayName() + "_" + id;
    }

    /**
     * @return the position formatted as "blockN:index"
     */
    public String getLocation()
    {
        return "block" + blockId + ":" + instructionIndex;
    }

    /**
     * Builds multi-line hover text with the type, location, SSA type and taint state.
     * @return the tooltip text
     */
    public String getTooltip()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getDisplayName());
        if (name != null)
        {
            sb.append(": ").append(name);
        }
        sb.append("\nLocation: ").append(getLocation());
        if (ssaValue != null && ssaValue.getType() != null)
        {
            sb.append("\nType: ").append(ssaValue.getType());
        }
        if (isTainted)
        {
            sb.append("\n⚠ TAINTED");
            if (taintSource != null)
            {
                sb.append(" (from ").append(taintSource).append(")");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFlowNode that = (DataFlowNode) o;
        return id == that.id;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id);
    }

    @Override
    public String toString()
    {
        return getLabel() + " [" + type.name() + "]";
    }

    // Builder

    /**
     * @return a new builder with type defaulted to LOCAL
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Mutable accumulator for the immutable fields of a {@link DataFlowNode}.
     */
    public static class Builder
    {
        private int id;
        private DataFlowNodeType type = DataFlowNodeType.LOCAL;
        private String name;
        private String description;
        private SSAValue ssaValue;
        private IRInstruction instruction;
        private int blockId;
        private int instructionIndex;

        /**
         * @param id identity used for equality and hashing
         * @return this builder
         */
        public Builder id(int id)
        {
            this.id = id;
            return this;
        }

        /**
         * @param type node kind, defaulting to LOCAL
         * @return this builder
         */
        public Builder type(DataFlowNodeType type)
        {
            this.type = type;
            return this;
        }

        /**
         * @param name display name, overriding the one derived from an SSA value
         * @return this builder
         */
        public Builder name(String name)
        {
            this.name = name;
            return this;
        }

        /**
         * @param description free-form detail text
         * @return this builder
         */
        public Builder description(String description)
        {
            this.description = description;
            return this;
        }

        /**
         * Sets the SSA value this node stands for, defaulting the name to the value's name.
         * @param value backing SSA value, may be null
         * @return this builder
         */
        public Builder ssaValue(SSAValue value)
        {
            this.ssaValue = value;
            if (value != null && this.name == null)
            {
                this.name = value.getName();
            }
            return this;
        }

        /**
         * Sets the IR instruction this node stands for.
         * @param instruction backing instruction, may be null
         * @return this builder
         */
        public Builder instruction(IRInstruction instruction)
        {
            this.instruction = instruction;
            return this;
        }

        /**
         * Sets the block and instruction position the node came from.
         * @param blockId owning basic block id
         * @param instructionIndex index within that block
         * @return this builder
         */
        public Builder location(int blockId, int instructionIndex)
        {
            this.blockId = blockId;
            this.instructionIndex = instructionIndex;
            return this;
        }

        /**
         * Creates the node from the values collected so far.
         * @return the built node
         */
        public DataFlowNode build()
        {
            return new DataFlowNode(this);
        }
    }
}
