package com.tonic.analysis.pdg.node;

/**
 * Kind of a program dependence graph node, carrying display text and whether the kind can act as
 * a dependence source or sink.
 */
public enum PDGNodeType
{
    /**
     * The root every unconditionally executed node is control dependent on.
     */
    ENTRY("Entry", "Method entry point", true, false),
    /**
     * The single sink every return and throw path converges on.
     */
    EXIT("Exit", "Method exit point", false, true),
    /**
     * An ordinary instruction, carrying dependences but neither starting nor ending a slice.
     */
    INSTRUCTION("Instruction", "Regular instruction", false, false),
    /**
     * A merge of values arriving on different incoming edges at a join.
     */
    PHI("Phi", "SSA phi instruction", false, false),
    /**
     * A grouping for nodes that share one control condition, so the condition is recorded once.
     */
    REGION("Region", "Control region node", false, false),
    /**
     * A conditional transfer, the node other nodes are control dependent on.
     */
    BRANCH("Branch", "Conditional branch node", false, false),

    /**
     * An invocation, which owns the actual-in and actual-out nodes for its arguments and result.
     */
    CALL_SITE("Call Site", "Method invocation point", false, false),
    /**
     * The caller side of an argument, a dependence source bound to the callee's formal-in.
     */
    ACTUAL_IN("Actual-In", "Actual parameter at call site", true, false),
    /**
     * The caller side of a returned value, a dependence sink bound to the callee's formal-out.
     */
    ACTUAL_OUT("Actual-Out", "Actual return value at call site", false, true),
    /**
     * The callee side of an argument, a dependence source at the method's entry.
     */
    FORMAL_IN("Formal-In", "Formal parameter at method entry", true, false),
    /**
     * The callee side of a returned value, a dependence sink at the method's exit.
     */
    FORMAL_OUT("Formal-Out", "Formal return at method exit", false, true),
    /**
     * A precomputed argument-to-result dependence for a callee, letting slices cross a call
     * without descending into it.
     */
    SUMMARY("Summary", "Summary node for interprocedural flow", false, false);

    private final String displayName;
    private final String description;
    private final boolean canBeSource;
    private final boolean canBeSink;

    PDGNodeType(String displayName, String description, boolean canBeSource, boolean canBeSink)
    {
        this.displayName = displayName;
        this.description = description;
        this.canBeSource = canBeSource;
        this.canBeSink = canBeSink;
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
     * @return whether can be source
     */
    public boolean isCanBeSource()
    {
        return canBeSource;
    }

    /**
     * @return whether can be sink
     */
    public boolean isCanBeSink()
    {
        return canBeSink;
    }

    /**
     * @return true for the call-site, parameter and summary kinds that only appear in an SDG
     */
    public boolean isInterprocedural()
    {
        return this == CALL_SITE || this == ACTUAL_IN || this == ACTUAL_OUT
            || this == FORMAL_IN || this == FORMAL_OUT || this == SUMMARY;
    }

    /**
     * @return true for the actual and formal parameter node kinds
     */
    public boolean isParameterNode()
    {
        return this == ACTUAL_IN || this == ACTUAL_OUT
            || this == FORMAL_IN || this == FORMAL_OUT;
    }
}
