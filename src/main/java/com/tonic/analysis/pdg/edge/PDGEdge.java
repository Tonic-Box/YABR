package com.tonic.analysis.pdg.edge;

import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.ssa.value.SSAValue;
import lombok.Getter;
import lombok.Setter;

@Getter
public class PDGEdge {

    private final PDGNode source;
    private final PDGNode target;
    private final PDGDependenceType type;
    private final String label;
    private final SSAValue dependentValue;
    private final boolean branchCondition;

    @Setter
    private boolean tainted;

    public PDGEdge(PDGNode source, PDGNode target, PDGDependenceType type) {
        this(source, target, type, null, null, false);
    }

    public PDGEdge(PDGNode source, PDGNode target, PDGDependenceType type, SSAValue dependentValue) {
        this(source, target, type, null, dependentValue, false);
    }

    public PDGEdge(PDGNode source, PDGNode target, PDGDependenceType type, String label) {
        this(source, target, type, label, null, false);
    }

    public PDGEdge(PDGNode source, PDGNode target, PDGDependenceType type,
                   String label, SSAValue dependentValue, boolean branchCondition) {
        this.source = source;
        this.target = target;
        this.type = type;
        this.label = label;
        this.dependentValue = dependentValue;
        this.branchCondition = branchCondition;
    }

    public static PDGEdge controlEdge(PDGNode source, PDGNode target, boolean condition) {
        PDGDependenceType edgeType = PDGDependenceType.forBranchCondition(condition);
        return new PDGEdge(source, target, edgeType, null, null, condition);
    }

    public static PDGEdge dataEdge(PDGNode source, PDGNode target, SSAValue value) {
        return new PDGEdge(source, target, PDGDependenceType.DATA_DEF_USE, value);
    }

    public static PDGEdge phiEdge(PDGNode source, PDGNode target, SSAValue value, String blockLabel) {
        return new PDGEdge(source, target, PDGDependenceType.DATA_PHI, blockLabel, value, false);
    }

    public boolean isControlDependence() {
        return type.isControlDependence();
    }

    public boolean isDataDependence() {
        return type.isDataDependence();
    }

    public boolean isInterprocedural() {
        return type.isInterproceduralEdge();
    }

    public boolean hasDependentValue() {
        return dependentValue != null;
    }

    public boolean hasLabel() {
        return label != null && !label.isEmpty();
    }

    public String getVariable() {
        if (dependentValue != null) {
            return dependentValue.getName();
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PDGEdge pdgEdge = (PDGEdge) o;
        return source.getId() == pdgEdge.source.getId()
            && target.getId() == pdgEdge.target.getId()
            && type == pdgEdge.type;
    }

    @Override
    public int hashCode() {
        int result = source.getId();
        result = 31 * result + target.getId();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PDGEdge[");
        sb.append(source.getId());
        sb.append(" --[").append(type.getShortName()).append("]--> ");
        sb.append(target.getId());
        if (hasLabel()) {
            sb.append(" \"").append(label).append("\"");
        }
        if (hasDependentValue()) {
            sb.append(" via ").append(dependentValue.getName());
        }
        sb.append("]");
        return sb.toString();
    }
}
