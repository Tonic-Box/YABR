package com.tonic.analysis.cpg.edge;

import lombok.Getter;

@Getter
public enum CPGEdgeType {
    AST_CHILD("AST", "AST parent to child"),
    AST_PARENT("AST↑", "AST child to parent"),

    CFG_NEXT("CFG", "Sequential control flow"),
    CFG_TRUE("CFG-T", "Conditional true branch"),
    CFG_FALSE("CFG-F", "Conditional false branch"),
    CFG_EXCEPTION("CFG-Ex", "Exception handler edge"),
    CFG_BACK("CFG-Back", "Loop back edge"),

    DATA_DEF("Def", "Value definition"),
    DATA_USE("Use", "Value use"),
    REACHING_DEF("Reach", "Reaching definition"),

    CONTROL_DEP("CDep", "Control dependency"),
    CONTROL_DEP_TRUE("CDep-T", "Control dependency - true"),
    CONTROL_DEP_FALSE("CDep-F", "Control dependency - false"),

    CALL("Call", "Caller to callee"),
    CALLEE("Callee", "Callee to caller (inverse)"),
    ARGUMENT("Arg", "Call to argument"),
    RECEIVER("Recv", "Call to receiver object"),
    RETURN_VALUE("Ret", "Call to return value"),

    PARAM_IN("PIn", "Actual to formal parameter"),
    PARAM_OUT("POut", "Formal to actual return"),
    SUMMARY("Sum", "Interprocedural summary"),

    EVAL_TYPE("Type", "Expression to its type"),
    INHERITS_FROM("Extends", "Subtype to supertype"),
    CONTAINS("Contains", "Container to contained element"),

    TAINT_SOURCE("TaintSrc", "Taint source marker"),
    TAINT_SINK("TaintSnk", "Taint sink marker"),
    TAINT_PROPAGATE("Taint", "Taint propagation path"),
    TAINT("Taint", "Generic taint edge");

    private final String shortName;
    private final String description;

    CPGEdgeType(String shortName, String description) {
        this.shortName = shortName;
        this.description = description;
    }

    public boolean isASTEdge() {
        return this == AST_CHILD || this == AST_PARENT;
    }

    public boolean isCFGEdge() {
        return name().startsWith("CFG_");
    }

    public boolean isDataFlowEdge() {
        return this == DATA_DEF || this == DATA_USE || this == REACHING_DEF;
    }

    public boolean isControlDependenceEdge() {
        return name().startsWith("CONTROL_DEP");
    }

    public boolean isCallGraphEdge() {
        return this == CALL || this == CALLEE || this == ARGUMENT
            || this == RECEIVER || this == RETURN_VALUE;
    }

    public boolean isInterproceduralEdge() {
        return this == PARAM_IN || this == PARAM_OUT || this == SUMMARY;
    }

    public boolean isTaintEdge() {
        return name().startsWith("TAINT_");
    }
}
