package com.tonic.analysis.pdg.node;

import lombok.Getter;

@Getter
public enum PDGNodeType {
    ENTRY("Entry", "Method entry point", true, false),
    EXIT("Exit", "Method exit point", false, true),
    INSTRUCTION("Instruction", "Regular instruction", false, false),
    PHI("Phi", "SSA phi instruction", false, false),
    REGION("Region", "Control region node", false, false),
    BRANCH("Branch", "Conditional branch node", false, false),

    CALL_SITE("Call Site", "Method invocation point", false, false),
    ACTUAL_IN("Actual-In", "Actual parameter at call site", true, false),
    ACTUAL_OUT("Actual-Out", "Actual return value at call site", false, true),
    FORMAL_IN("Formal-In", "Formal parameter at method entry", true, false),
    FORMAL_OUT("Formal-Out", "Formal return at method exit", false, true),
    SUMMARY("Summary", "Summary node for interprocedural flow", false, false);

    private final String displayName;
    private final String description;
    private final boolean canBeSource;
    private final boolean canBeSink;

    PDGNodeType(String displayName, String description, boolean canBeSource, boolean canBeSink) {
        this.displayName = displayName;
        this.description = description;
        this.canBeSource = canBeSource;
        this.canBeSink = canBeSink;
    }

    public boolean isInterprocedural() {
        return this == CALL_SITE || this == ACTUAL_IN || this == ACTUAL_OUT
            || this == FORMAL_IN || this == FORMAL_OUT || this == SUMMARY;
    }

    public boolean isParameterNode() {
        return this == ACTUAL_IN || this == ACTUAL_OUT
            || this == FORMAL_IN || this == FORMAL_OUT;
    }
}
