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

    /** Pending NewInstruction results waiting for <init> call */
    private final Map<SSAValue, String> pendingNewInstructions = new HashMap<>();

    /** Declared variable names in current scope */
    private final Set<String> declaredVariables = new HashSet<>();

    /** SSA values that have been assigned to variables (should use var ref, not inline) */
    private final Set<SSAValue> materializedValues = new HashSet<>();

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

    /**
     * Registers a pending NewInstruction result that awaits its <init> call.
     */
    public void registerPendingNew(SSAValue result, String className) {
        pendingNewInstructions.put(result, className);
    }

    /**
     * Checks if a value is a pending NewInstruction result.
     */
    public boolean isPendingNew(SSAValue value) {
        return pendingNewInstructions.containsKey(value);
    }

    /**
     * Gets and removes the pending new class name for a value.
     */
    public String consumePendingNew(SSAValue value) {
        return pendingNewInstructions.remove(value);
    }

    /**
     * Marks a variable name as declared.
     */
    public void markDeclared(String name) {
        declaredVariables.add(name);
    }

    /**
     * Checks if a variable name has been declared.
     */
    public boolean isDeclared(String name) {
        return declaredVariables.contains(name);
    }

    /**
     * Clears declared variables (for new scope).
     */
    public void clearDeclaredVariables() {
        declaredVariables.clear();
    }

    /**
     * Marks an SSA value as materialized into a variable.
     * After this, subsequent uses should reference the variable, not inline the expression.
     */
    public void markMaterialized(SSAValue value) {
        materializedValues.add(value);
    }

    /**
     * Checks if an SSA value has been materialized into a variable.
     */
    public boolean isMaterialized(SSAValue value) {
        return materializedValues.contains(value);
    }
}
