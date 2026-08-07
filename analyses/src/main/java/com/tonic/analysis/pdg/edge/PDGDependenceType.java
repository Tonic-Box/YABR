package com.tonic.analysis.pdg.edge;

/**
 * Kind of dependence a PDG edge carries - control, data, or interprocedural.
 */
public enum PDGDependenceType
{
    /**
     * The dependent node runs only when the governing branch takes its true side.
     */
    CONTROL_TRUE("Ctrl-T", "Control dependency - true branch", true, false),
    /**
     * The dependent node runs only when the governing branch takes its false side.
     */
    CONTROL_FALSE("Ctrl-F", "Control dependency - false branch", true, false),
    /**
     * The dependent node runs only when the protected region throws and control reaches a handler.
     */
    CONTROL_EXCEPTION("Ctrl-Ex", "Control dependency - exception edge", true, false),
    /**
     * The dependent node runs whenever the governing node does, with no condition to satisfy.
     */
    CONTROL_UNCONDITIONAL("Ctrl", "Unconditional control dependency", true, false),
    /**
     * The dependent node runs only when a switch selects the case that guards it.
     */
    CONTROL_SWITCH("Ctrl-Sw", "Control dependency - switch case", true, false),

    /**
     * A read depends on the write that supplies the value it observes.
     */
    DATA_DEF_USE("Def-Use", "Data dependency via def-use chain", false, true),
    /**
     * A read depends on a phi that merges definitions arriving from several predecessors.
     */
    DATA_PHI("Phi", "Data dependency through phi node", false, true),
    /**
     * A write must stay ordered after an earlier read of the same location, so reordering
     * would not clobber a value still in use.
     */
    DATA_ANTI("Anti", "Anti-dependency (read-after-write)", false, true),
    /**
     * Two writes to the same location must keep their relative order, so the last one still wins.
     */
    DATA_OUTPUT("Output", "Output dependency (write-after-write)", false, true),

    /**
     * Binds an argument at a call site to the callee's matching formal parameter; counted as
     * neither control nor data dependence.
     */
    PARAMETER_IN("Param-In", "Actual to formal parameter edge", false, false),
    /**
     * Binds the callee's returned value back to the call site that receives it.
     */
    PARAMETER_OUT("Param-Out", "Formal to actual return edge", false, false),
    /**
     * Links a call site to the entry of the method it invokes.
     */
    CALL("Call", "Call site to method entry edge", false, false),
    /**
     * Links a method exit back to the point in the caller where execution resumes.
     */
    RETURN("Return", "Method exit to call site return edge", false, false),
    /**
     * Condenses a callee's parameter-to-return effect so a slice can cross the call without
     * descending into the body.
     */
    SUMMARY("Summary", "Interprocedural summary edge", false, false);

    private final String shortName;
    private final String description;
    private final boolean controlDependence;
    private final boolean dataDependence;

    PDGDependenceType(String shortName, String description, boolean controlDependence, boolean dataDependence)
    {
        this.shortName = shortName;
        this.description = description;
        this.controlDependence = controlDependence;
        this.dataDependence = dataDependence;
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
     * @return whether control dependence
     */
    public boolean isControlDependence()
    {
        return controlDependence;
    }

    /**
     * @return whether data dependence
     */
    public boolean isDataDependence()
    {
        return dataDependence;
    }

    /**
     * @return true for the parameter, call, return and summary edge types that cross method boundaries
     */
    public boolean isInterproceduralEdge()
    {
        return this == PARAMETER_IN || this == PARAMETER_OUT
            || this == CALL || this == RETURN || this == SUMMARY;
    }

    /**
     * @return whether control dependency
     */
    public boolean isControlDependency()
    {
        return controlDependence;
    }

    /**
     * @return whether data dependency
     */
    public boolean isDataDependency()
    {
        return dataDependence;
    }

    /**
     * Picks the control edge type for one side of a branch.
     * @param condition true for the taken branch, false for the fall-through
     * @return CONTROL_TRUE or CONTROL_FALSE
     */
    public static PDGDependenceType forBranchCondition(boolean condition)
    {
        return condition ? CONTROL_TRUE : CONTROL_FALSE;
    }
}
