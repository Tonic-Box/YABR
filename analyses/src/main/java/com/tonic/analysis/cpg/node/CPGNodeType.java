package com.tonic.analysis.cpg.node;

/**
 * Kinds of nodes a CPG can contain, spanning IR, AST, PDG, and SDG layers.
 */
public enum CPGNodeType
{
    /**
     * A whole method, the root that its blocks and locals belong to.
     */
    METHOD("Method", "Method definition"),
    /**
     * A basic block, a run of instructions entered only at its head.
     */
    BLOCK("Block", "Basic block"),
    /**
     * A single IR instruction inside a block.
     */
    INSTRUCTION("Instruction", "IR instruction"),
    /**
     * A formal parameter of the method, live from entry rather than assigned by a body instruction.
     */
    PARAMETER("Parameter", "Method parameter"),
    /**
     * A local variable of the method, distinct from its declared parameters.
     */
    LOCAL("Local", "Local variable"),
    /**
     * A single invocation, the anchor interprocedural edges attach to.
     */
    CALL_SITE("CallSite", "Method invocation"),
    /**
     * A compile-time constant operand, such as a number or string literal.
     */
    LITERAL("Literal", "Constant value"),
    /**
     * A named class or descriptor type mentioned by the code, not a value of that type.
     */
    TYPE("Type", "Type reference"),
    /**
     * A reference to a field, covering both reads and writes of it.
     */
    FIELD_REF("FieldRef", "Field reference"),

    /**
     * An expression in the recovered source tree, evaluating to a value.
     */
    AST_EXPRESSION("ASTExpr", "AST expression node"),
    /**
     * A statement in the recovered source tree, executed for effect rather than for a value.
     */
    AST_STATEMENT("ASTStmt", "AST statement node"),

    /**
     * The synthetic root that unconditionally executed code hangs its control dependence from.
     */
    PDG_ENTRY("PDGEntry", "PDG entry node"),
    /**
     * The synthetic sink every path out of the method converges on.
     */
    PDG_EXIT("PDGExit", "PDG exit node"),

    /**
     * A value entering a method at its entry, the callee side of an actual-in.
     */
    SDG_FORMAL_IN("FormalIn", "SDG formal parameter"),
    /**
     * A value leaving a method at its exit, the callee side of an actual-out.
     */
    SDG_FORMAL_OUT("FormalOut", "SDG formal return"),
    /**
     * An argument supplied at a call site, paired with the callee's formal-in.
     */
    SDG_ACTUAL_IN("ActualIn", "SDG actual parameter"),
    /**
     * The value handed back to the caller at a call site, paired with the callee's formal-out.
     */
    SDG_ACTUAL_OUT("ActualOut", "SDG actual return");

    private final String shortName;
    private final String description;

    CPGNodeType(String shortName, String description)
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
     * @return whether this is an IR-layer node type
     */
    public boolean isIRNode()
    {
        return this == METHOD || this == BLOCK || this == INSTRUCTION
            || this == PARAMETER || this == LOCAL || this == CALL_SITE;
    }

    /**
     * @return whether this is an AST-layer node type
     */
    public boolean isASTNode()
    {
        return this == AST_EXPRESSION || this == AST_STATEMENT;
    }

    /**
     * @return whether this is a PDG-layer node type
     */
    public boolean isPDGNode()
    {
        return this == PDG_ENTRY || this == PDG_EXIT;
    }

    /**
     * @return whether this is an SDG parameter node type
     */
    public boolean isSDGNode()
    {
        return this == SDG_FORMAL_IN || this == SDG_FORMAL_OUT
            || this == SDG_ACTUAL_IN || this == SDG_ACTUAL_OUT;
    }
}
