package com.tonic.analysis.pdg.edge;

import lombok.Getter;

@Getter
public enum PDGDependenceType {
    CONTROL_TRUE("Ctrl-T", "Control dependency - true branch", true, false),
    CONTROL_FALSE("Ctrl-F", "Control dependency - false branch", true, false),
    CONTROL_EXCEPTION("Ctrl-Ex", "Control dependency - exception edge", true, false),
    CONTROL_UNCONDITIONAL("Ctrl", "Unconditional control dependency", true, false),
    CONTROL_SWITCH("Ctrl-Sw", "Control dependency - switch case", true, false),

    DATA_DEF_USE("Def-Use", "Data dependency via def-use chain", false, true),
    DATA_PHI("Phi", "Data dependency through phi node", false, true),
    DATA_ANTI("Anti", "Anti-dependency (read-after-write)", false, true),
    DATA_OUTPUT("Output", "Output dependency (write-after-write)", false, true),

    PARAMETER_IN("Param-In", "Actual to formal parameter edge", false, false),
    PARAMETER_OUT("Param-Out", "Formal to actual return edge", false, false),
    CALL("Call", "Call site to method entry edge", false, false),
    RETURN("Return", "Method exit to call site return edge", false, false),
    SUMMARY("Summary", "Interprocedural summary edge", false, false);

    private final String shortName;
    private final String description;
    @Getter
    private final boolean controlDependence;
    @Getter
    private final boolean dataDependence;

    PDGDependenceType(String shortName, String description, boolean controlDependence, boolean dataDependence) {
        this.shortName = shortName;
        this.description = description;
        this.controlDependence = controlDependence;
        this.dataDependence = dataDependence;
    }

    public boolean isInterproceduralEdge() {
        return this == PARAMETER_IN || this == PARAMETER_OUT
            || this == CALL || this == RETURN || this == SUMMARY;
    }

    public boolean isControlDependency() {
        return controlDependence;
    }

    public boolean isDataDependency() {
        return dataDependence;
    }

    public static PDGDependenceType forBranchCondition(boolean condition) {
        return condition ? CONTROL_TRUE : CONTROL_FALSE;
    }
}
