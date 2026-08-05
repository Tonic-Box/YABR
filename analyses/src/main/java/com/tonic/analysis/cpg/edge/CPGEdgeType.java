package com.tonic.analysis.cpg.edge;

/**
 * Kinds of edges a CPG can contain, spanning AST, CFG, data-flow, dependence,
 * call, interprocedural, and taint layers.
 */
public enum CPGEdgeType
{
    /**
     * Descends from a syntax node to one nested directly inside it.
     */
    AST_CHILD("AST", "AST parent to child"),
    /**
     * Ascends from a syntax node to the one enclosing it, the inverse of AST_CHILD.
     */
    AST_PARENT("AST↑", "AST child to parent"),

    /**
     * Falls through from one statement to the next with no branch taken.
     */
    CFG_NEXT("CFG", "Sequential control flow"),
    /**
     * Leaves a conditional along the arm taken when the test holds.
     */
    CFG_TRUE("CFG-T", "Conditional true branch"),
    /**
     * Leaves a conditional along the arm taken when the test fails.
     */
    CFG_FALSE("CFG-F", "Conditional false branch"),
    /**
     * Transfers control from a protected instruction to a handler that covers it.
     */
    CFG_EXCEPTION("CFG-Ex", "Exception handler edge"),
    /**
     * Jumps backward to a loop header, closing the cycle that makes the region a loop.
     */
    CFG_BACK("CFG-Back", "Loop back edge"),

    /**
     * Links an instruction to the value it writes.
     */
    DATA_DEF("Def", "Value definition"),
    /**
     * Links an instruction to a value it reads.
     */
    DATA_USE("Use", "Value use"),
    /**
     * Connects a read to a write that can reach it along at least one path, with no
     * intervening redefinition.
     */
    REACHING_DEF("Reach", "Reaching definition"),

    /**
     * The target executes only under a governing branch, without recording which arm.
     */
    CONTROL_DEP("CDep", "Control dependency"),
    /**
     * The target executes only when the governing branch takes its true arm.
     */
    CONTROL_DEP_TRUE("CDep-T", "Control dependency - true"),
    /**
     * The target executes only when the governing branch takes its false arm.
     */
    CONTROL_DEP_FALSE("CDep-F", "Control dependency - false"),

    /**
     * Links a call site to a method the call may dispatch to.
     */
    CALL("Call", "Caller to callee"),
    /**
     * Links a method back to a call site that can reach it, the inverse of CALL.
     */
    CALLEE("Callee", "Callee to caller (inverse)"),
    /**
     * Links a call site to an expression passed as one of its arguments.
     */
    ARGUMENT("Arg", "Call to argument"),
    /**
     * Links an instance call to the object it is invoked on.
     */
    RECEIVER("Recv", "Call to receiver object"),
    /**
     * Links a call site to the value the call yields back into the caller.
     */
    RETURN_VALUE("Ret", "Call to return value"),

    /**
     * Binds an argument at a call site to the callee's matching formal parameter.
     */
    PARAM_IN("PIn", "Actual to formal parameter"),
    /**
     * Binds the callee's returned value back to the call site that receives it.
     */
    PARAM_OUT("POut", "Formal to actual return"),
    /**
     * Condenses a callee's input-to-output effect so a flow can cross the call without
     * descending into the body.
     */
    SUMMARY("Sum", "Interprocedural summary"),

    /**
     * Links an expression to the type it evaluates to.
     */
    EVAL_TYPE("Type", "Expression to its type"),
    /**
     * Links a type to a class it extends or an interface it implements.
     */
    INHERITS_FROM("Extends", "Subtype to supertype"),
    /**
     * Links a declaration to a member declared inside it, such as a class to its methods.
     */
    CONTAINS("Contains", "Container to contained element"),

    /**
     * Marks a node that introduces untrusted data into the graph.
     */
    TAINT_SOURCE("TaintSrc", "Taint source marker"),
    /**
     * Marks a node where untrusted data arriving would be a vulnerability.
     */
    TAINT_SINK("TaintSnk", "Taint sink marker"),
    /**
     * Carries taint one step further, from a tainted node to one it contaminates.
     */
    TAINT_PROPAGATE("Taint", "Taint propagation path"),
    /**
     * Untyped taint edge for flows that fit none of the source, sink, or propagation roles;
     * unlike those, it is not reported by {@link #isTaintEdge()}.
     */
    TAINT("Taint", "Generic taint edge");

    private final String shortName;
    private final String description;

    CPGEdgeType(String shortName, String description)
    {
        this.shortName = shortName;
        this.description = description;
    }

    /**
     * @return the short name
     */
    public String getShortName()
    {
        return shortName;
    }

    /**
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * @return whether this is an AST structure edge
     */
    public boolean isASTEdge()
    {
        return this == AST_CHILD || this == AST_PARENT;
    }

    /**
     * @return whether this is a control-flow edge
     */
    public boolean isCFGEdge()
    {
        return name().startsWith("CFG_");
    }

    /**
     * @return whether this is a data-flow edge
     */
    public boolean isDataFlowEdge()
    {
        return this == DATA_DEF || this == DATA_USE || this == REACHING_DEF;
    }

    /**
     * @return whether this is a control-dependence edge
     */
    public boolean isControlDependenceEdge()
    {
        return name().startsWith("CONTROL_DEP");
    }

    /**
     * @return whether this is a call-graph edge
     */
    public boolean isCallGraphEdge()
    {
        return this == CALL || this == CALLEE || this == ARGUMENT
            || this == RECEIVER || this == RETURN_VALUE;
    }

    /**
     * @return whether this is an interprocedural parameter or summary edge
     */
    public boolean isInterproceduralEdge()
    {
        return this == PARAM_IN || this == PARAM_OUT || this == SUMMARY;
    }

    /**
     * @return whether this is a taint edge
     */
    public boolean isTaintEdge()
    {
        return name().startsWith("TAINT_");
    }
}
