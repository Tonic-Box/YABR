package com.tonic.analysis.dataflow;

/**
 * Types of nodes in a data flow graph.
 */
public enum DataFlowNodeType {
    PARAM("Parameter", "Method parameter"),
    LOCAL("Local Variable", "Local variable definition"),
    CONSTANT("Constant", "Constant value"),
    PHI("Phi", "SSA phi node (merge point)"),
    INVOKE_RESULT("Call Result", "Return value from method call"),
    FIELD_LOAD("Field Load", "Value loaded from field"),
    ARRAY_LOAD("Array Load", "Value loaded from array"),
    BINARY_OP("Binary Operation", "Result of binary operation"),
    UNARY_OP("Unary Operation", "Result of unary operation"),
    CAST("Type Cast", "Result of type cast"),
    NEW_OBJECT("New Object", "Newly created object"),
    RETURN("Return", "Return value"),
    FIELD_STORE("Field Store", "Value stored to field (sink)"),
    ARRAY_STORE("Array Store", "Value stored to array (sink)"),
    INVOKE_ARG("Call Argument", "Argument passed to method (sink)");

    private final String displayName;
    private final String description;

    DataFlowNodeType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this node type can be a taint source.
     */
    public boolean canBeTaintSource() {
        return this == PARAM || this == FIELD_LOAD || this == INVOKE_RESULT;
    }

    /**
     * Check if this node type can be a taint sink.
     */
    public boolean canBeTaintSink() {
        return this == FIELD_STORE || this == INVOKE_ARG || this == RETURN;
    }

    /**
     * Check if this is a data source (produces values).
     */
    public boolean isSource() {
        return this == PARAM || this == CONSTANT || this == FIELD_LOAD ||
               this == ARRAY_LOAD || this == INVOKE_RESULT || this == NEW_OBJECT;
    }

    /**
     * Check if this is a data sink (consumes values).
     */
    public boolean isSink() {
        return this == FIELD_STORE || this == ARRAY_STORE ||
               this == INVOKE_ARG || this == RETURN;
    }
}
