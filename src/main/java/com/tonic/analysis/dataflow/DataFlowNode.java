package com.tonic.analysis.dataflow;

import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.SSAValue;

import java.util.Objects;

/**
 * A node in the data flow graph representing a value or operation.
 */
public class DataFlowNode {

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

    private DataFlowNode(Builder builder) {
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

    // ==================== Getters ====================

    public int getId() {
        return id;
    }

    public DataFlowNodeType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public SSAValue getSsaValue() {
        return ssaValue;
    }

    public IRInstruction getInstruction() {
        return instruction;
    }

    public int getBlockId() {
        return blockId;
    }

    public int getInstructionIndex() {
        return instructionIndex;
    }

    public boolean isTainted() {
        return isTainted;
    }

    public String getTaintSource() {
        return taintSource;
    }

    // ==================== Setters ====================

    public void setTainted(boolean tainted) {
        this.isTainted = tainted;
    }

    public void setTaintSource(String source) {
        this.taintSource = source;
        this.isTainted = source != null;
    }

    // ==================== Display ====================

    /**
     * Get a short label for display in the graph.
     */
    public String getLabel() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if (ssaValue != null) {
            return ssaValue.getName();
        }
        return type.getDisplayName() + "_" + id;
    }

    /**
     * Get location string for display.
     */
    public String getLocation() {
        return "block" + blockId + ":" + instructionIndex;
    }

    /**
     * Get detailed tooltip text.
     */
    public String getTooltip() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getDisplayName());
        if (name != null) {
            sb.append(": ").append(name);
        }
        sb.append("\nLocation: ").append(getLocation());
        if (ssaValue != null && ssaValue.getType() != null) {
            sb.append("\nType: ").append(ssaValue.getType());
        }
        if (isTainted) {
            sb.append("\n⚠ TAINTED");
            if (taintSource != null) {
                sb.append(" (from ").append(taintSource).append(")");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFlowNode that = (DataFlowNode) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getLabel() + " [" + type.name() + "]";
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int id;
        private DataFlowNodeType type = DataFlowNodeType.LOCAL;
        private String name;
        private String description;
        private SSAValue ssaValue;
        private IRInstruction instruction;
        private int blockId;
        private int instructionIndex;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder type(DataFlowNodeType type) {
            this.type = type;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder ssaValue(SSAValue value) {
            this.ssaValue = value;
            if (value != null && this.name == null) {
                this.name = value.getName();
            }
            return this;
        }

        public Builder instruction(IRInstruction instruction) {
            this.instruction = instruction;
            return this;
        }

        public Builder location(int blockId, int instructionIndex) {
            this.blockId = blockId;
            this.instructionIndex = instructionIndex;
            return this;
        }

        public DataFlowNode build() {
            return new DataFlowNode(this);
        }
    }
}
