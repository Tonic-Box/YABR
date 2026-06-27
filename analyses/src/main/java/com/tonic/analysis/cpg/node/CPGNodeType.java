package com.tonic.analysis.cpg.node;

import lombok.Getter;

@Getter
public enum CPGNodeType {
    METHOD("Method", "Method definition"),
    BLOCK("Block", "Basic block"),
    INSTRUCTION("Instruction", "IR instruction"),
    PARAMETER("Parameter", "Method parameter"),
    LOCAL("Local", "Local variable"),
    CALL_SITE("CallSite", "Method invocation"),
    LITERAL("Literal", "Constant value"),
    TYPE("Type", "Type reference"),
    FIELD_REF("FieldRef", "Field reference"),

    AST_EXPRESSION("ASTExpr", "AST expression node"),
    AST_STATEMENT("ASTStmt", "AST statement node"),

    PDG_ENTRY("PDGEntry", "PDG entry node"),
    PDG_EXIT("PDGExit", "PDG exit node"),

    SDG_FORMAL_IN("FormalIn", "SDG formal parameter"),
    SDG_FORMAL_OUT("FormalOut", "SDG formal return"),
    SDG_ACTUAL_IN("ActualIn", "SDG actual parameter"),
    SDG_ACTUAL_OUT("ActualOut", "SDG actual return");

    private final String shortName;
    private final String description;

    CPGNodeType(String shortName, String description) {
        this.shortName = shortName;
        this.description = description;
    }

    public boolean isIRNode() {
        return this == METHOD || this == BLOCK || this == INSTRUCTION
            || this == PARAMETER || this == LOCAL || this == CALL_SITE;
    }

    public boolean isASTNode() {
        return this == AST_EXPRESSION || this == AST_STATEMENT;
    }

    public boolean isPDGNode() {
        return this == PDG_ENTRY || this == PDG_EXIT;
    }

    public boolean isSDGNode() {
        return this == SDG_FORMAL_IN || this == SDG_FORMAL_OUT
            || this == SDG_ACTUAL_IN || this == SDG_ACTUAL_OUT;
    }
}
