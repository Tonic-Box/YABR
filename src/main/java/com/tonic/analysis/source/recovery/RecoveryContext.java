package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.MethodEntry;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import lombok.Getter;

import java.util.*;

/**
 * Holds shared state during expression recovery.
 */
@Getter
public class RecoveryContext {

    private final IRMethod irMethod;
    private final MethodEntry sourceMethod;
    private final DefUseChains defUseChains;

    /** Recovered expressions keyed by SSA value */
    private final Map<SSAValue, Expression> recoveredExpressions = new HashMap<>();

    /** Values that have been inlined (single-use optimization) */
    private final Set<SSAValue> inlinedValues = new HashSet<>();

    /** Generated variable names */
    private final Map<SSAValue, String> variableNames = new HashMap<>();

    /** Counter for synthetic names */
    private int syntheticCounter = 0;

    public RecoveryContext(IRMethod irMethod, MethodEntry sourceMethod, DefUseChains defUseChains) {
        this.irMethod = irMethod;
        this.sourceMethod = sourceMethod;
        this.defUseChains = defUseChains;
    }

    public void cacheExpression(SSAValue value, Expression expr) {
        recoveredExpressions.put(value, expr);
    }

    public Expression getCachedExpression(SSAValue value) {
        return recoveredExpressions.get(value);
    }

    public boolean isRecovered(SSAValue value) {
        return recoveredExpressions.containsKey(value);
    }

    public void markInlined(SSAValue value) {
        inlinedValues.add(value);
    }

    public boolean isInlined(SSAValue value) {
        return inlinedValues.contains(value);
    }

    public void setVariableName(SSAValue value, String name) {
        variableNames.put(value, name);
    }

    public String getVariableName(SSAValue value) {
        return variableNames.get(value);
    }

    public int nextSyntheticId() {
        return syntheticCounter++;
    }
}
