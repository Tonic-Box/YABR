package com.tonic.analysis.dataflow;

/**
 * Types of nodes in a data flow graph.
 */
public enum DataFlowNodeType
{
    /**
     * A value arriving from the caller, the primary taint entry point into a method.
     */
    PARAM("Parameter", "Method parameter"),
    /**
     * A local variable definition, which relays a value rather than originating one.
     */
    LOCAL("Local Variable", "Local variable definition"),
    /**
     * A literal baked into the code, a value source that can never carry taint.
     */
    CONSTANT("Constant", "Constant value"),
    /**
     * A merge of the values reaching a join from each incoming edge.
     */
    PHI("Phi", "SSA phi node (merge point)"),
    /**
     * A value produced by a call, treated as a taint entry point since the callee is opaque here.
     */
    INVOKE_RESULT("Call Result", "Return value from method call"),
    /**
     * A value read out of a field, treated as a taint entry point since it comes from outside.
     */
    FIELD_LOAD("Field Load", "Value loaded from field"),
    /**
     * A value read out of an array element, a value source but not a taint entry point.
     */
    ARRAY_LOAD("Array Load", "Value loaded from array"),
    /**
     * A value computed from two operands, which propagates taint from either of them.
     */
    BINARY_OP("Binary Operation", "Result of binary operation"),
    /**
     * A value computed from one operand, such as a negation or a width conversion.
     */
    UNARY_OP("Unary Operation", "Result of unary operation"),
    /**
     * A retyped value, which forwards its operand rather than producing anything new.
     */
    CAST("Type Cast", "Result of type cast"),
    /**
     * A freshly allocated object, a value source but never itself tainted.
     */
    NEW_OBJECT("New Object", "Newly created object"),
    /**
     * A value handed back to the caller, a taint sink since it escapes this method.
     */
    RETURN("Return", "Return value"),
    /**
     * A write into a field, a taint sink because the value outlives this method.
     */
    FIELD_STORE("Field Store", "Value stored to field (sink)"),
    /**
     * A write into an array element, a sink but not one taint is tracked through.
     */
    ARRAY_STORE("Array Store", "Value stored to array (sink)"),
    /**
     * An argument handed to a call, a taint sink because the value leaves this method.
     */
    INVOKE_ARG("Call Argument", "Argument passed to method (sink)");

    private final String displayName;
    private final String description;

    DataFlowNodeType(String displayName, String description)
    {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * @return the display name
     */
    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Tests whether tainted data can enter through this type.
     *
     * @return true for parameters, field loads and call results
     */
    public boolean canBeTaintSource()
    {
        return this == PARAM || this == FIELD_LOAD || this == INVOKE_RESULT;
    }

    /**
     * Tests whether tainted data can escape through this type.
     *
     * @return true for field stores, call arguments and returns
     */
    public boolean canBeTaintSink()
    {
        return this == FIELD_STORE || this == INVOKE_ARG || this == RETURN;
    }

    /**
     * Tests whether this type produces values.
     *
     * @return true for parameters, constants, field and array loads, call results and allocations
     */
    public boolean isSource()
    {
        return this == PARAM || this == CONSTANT || this == FIELD_LOAD ||
               this == ARRAY_LOAD || this == INVOKE_RESULT || this == NEW_OBJECT;
    }

    /**
     * Tests whether this type consumes values.
     *
     * @return true for field stores, array stores, call arguments and returns
     */
    public boolean isSink()
    {
        return this == FIELD_STORE || this == ARRAY_STORE ||
               this == INVOKE_ARG || this == RETURN;
    }
}
