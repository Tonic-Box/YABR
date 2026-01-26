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

    /** Local slots that contain pending new values (slot index -> class name) */
    private final Map<Integer, String> pendingNewLocalSlots = new HashMap<>();

    /** Declared variable names in current scope */
    private final Set<String> declaredVariables = new HashSet<>();

    /** Types of declared variables */
    private final Map<String, com.tonic.analysis.source.ast.type.SourceType> declaredVariableTypes = new HashMap<>();

    /** Maps local slot indices to their current variable names */
    private final Map<Integer, String> localSlotNames = new HashMap<>();

    /** SSA values that have been assigned to variables (should use var ref, not inline) */
    private final Set<SSAValue> materializedValues = new HashSet<>();

    /** Stack of variables declared in for-loop init (scoped to loop body) */
    private final Deque<Set<String>> forLoopScopedVariables = new ArrayDeque<>();

    /** Stack of variables declared in if-then-else branches (scoped to branch) */
    private final Deque<Set<String>> branchScopedVariables = new ArrayDeque<>();

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
     * Registers a local slot as containing a pending new value.
     */
    public void registerPendingNewLocalSlot(int localIndex, String className) {
        pendingNewLocalSlots.put(localIndex, className);
    }

    /**
     * Checks if a local slot contains a pending new value and returns the class name.
     */
    public String consumePendingNewLocalSlot(int localIndex) {
        return pendingNewLocalSlots.remove(localIndex);
    }

    /**
     * Marks a variable name as declared.
     * If inside a branch scope, tracks it for removal when the branch ends.
     */
    public void markDeclared(String name) {
        declaredVariables.add(name);
        if (!branchScopedVariables.isEmpty()) {
            branchScopedVariables.peek().add(name);
        }
    }

    /**
     * Marks a variable name as declared with a specific type.
     */
    public void markDeclaredWithType(String name, com.tonic.analysis.source.ast.type.SourceType type) {
        markDeclared(name);
        if (type != null) {
            declaredVariableTypes.put(name, type);
        }
    }

    /**
     * Gets the declared type for a variable, or null if not tracked.
     */
    public com.tonic.analysis.source.ast.type.SourceType getDeclaredType(String name) {
        return declaredVariableTypes.get(name);
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

    /**
     * Unmarks an SSA value as materialized, allowing it to be inlined again.
     * Used when a PHI variable is collapsed to a boolean expression.
     */
    public void unmarkMaterialized(SSAValue value) {
        materializedValues.remove(value);
    }

    /**
     * Pushes a new scope for for-loop variables.
     * Variables declared in for-loop init will be tracked in this scope.
     */
    public void pushForLoopScope() {
        forLoopScopedVariables.push(new HashSet<>());
    }

    /**
     * Pops the current for-loop scope and removes its variables from declaredVariables.
     * This allows the same variable name to be re-declared in subsequent for-loops.
     */
    public void popForLoopScope() {
        if (!forLoopScopedVariables.isEmpty()) {
            Set<String> loopVars = forLoopScopedVariables.pop();
            for (String var : loopVars) {
                declaredVariables.remove(var);
            }
        }
    }

    /**
     * Marks a variable as declared in a for-loop init expression.
     * The variable will be removed from declaredVariables when the loop scope is popped.
     */
    public void markDeclaredInForLoopInit(String name) {
        declaredVariables.add(name);
        if (!forLoopScopedVariables.isEmpty()) {
            forLoopScopedVariables.peek().add(name);
        }
    }

    /**
     * Pushes a new scope for if-then-else branch variables.
     * Variables declared in this branch will be tracked and removed when the scope is popped.
     */
    public void pushBranchScope() {
        branchScopedVariables.push(new HashSet<>());
    }

    /**
     * Pops the current branch scope and removes its variables from declaredVariables.
     * This allows sibling branches to declare the same variable independently.
     */
    public void popBranchScope() {
        if (!branchScopedVariables.isEmpty()) {
            Set<String> branchVars = branchScopedVariables.pop();
            for (String var : branchVars) {
                declaredVariables.remove(var);
            }
        }
    }

    /**
     * Marks a variable as declared in the current scope.
     * If inside a branch scope, tracks it for removal when the branch ends.
     */
    public void markDeclaredInBranch(String name) {
        declaredVariables.add(name);
        if (!branchScopedVariables.isEmpty()) {
            branchScopedVariables.peek().add(name);
        }
    }

    /**
     * Checks if we're currently inside a branch scope.
     */
    public boolean isInBranchScope() {
        return !branchScopedVariables.isEmpty();
    }

    /**
     * Associates a variable name with a local slot index.
     * When a value is stored to a slot, record the variable name so that
     * subsequent loads from the same slot can use the same name.
     */
    public void setLocalSlotName(int slotIndex, String name) {
        localSlotNames.put(slotIndex, name);
    }

    /**
     * Gets the variable name associated with a local slot index.
     * Returns null if no name has been set for this slot.
     */
    public String getLocalSlotName(int slotIndex) {
        return localSlotNames.get(slotIndex);
    }

    /** Maps SSA values to the slot index they were first stored to */
    private final Map<SSAValue, Integer> ssaValueSlot = new HashMap<>();

    /**
     * Records that an SSA value was stored to a specific slot.
     */
    public void setSSAValueSlot(SSAValue value, int slotIndex) {
        if (!ssaValueSlot.containsKey(value)) {
            ssaValueSlot.put(value, slotIndex);
        }
    }

    /**
     * Gets the slot that an SSA value was first stored to, or -1 if not tracked.
     */
    public int getSSAValueSlot(SSAValue value) {
        return ssaValueSlot.getOrDefault(value, -1);
    }
}
