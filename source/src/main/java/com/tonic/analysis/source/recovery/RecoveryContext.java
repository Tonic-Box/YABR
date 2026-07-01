package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.MethodEntry;
import com.tonic.analysis.ssa.analysis.DefUseChains;

import java.util.*;

/**
 * Holds shared state during expression recovery.
 */
public class RecoveryContext {

    private final IRMethod irMethod;
    private final MethodEntry sourceMethod;
    private final DefUseChains defUseChains;
    private final MethodLocals locals;

    private SlotVariablePartition slotPartition;

    private final Map<SSAValue, Expression> recoveredExpressions = new HashMap<>();

    private final Set<SSAValue> inlinedValues = new HashSet<>();

    private final Map<SSAValue, String> variableNames = new HashMap<>();

    private int syntheticCounter = 0;

    private final Map<SSAValue, String> pendingNewInstructions = new HashMap<>();

    private final Map<Integer, String> pendingNewLocalSlots = new HashMap<>();

    private final Set<String> declaredVariables = new HashSet<>();

    private final Map<String, com.tonic.analysis.source.ast.type.SourceType> declaredVariableTypes = new HashMap<>();

    private final Map<Integer, String> localSlotNames = new HashMap<>();

    private final Set<SSAValue> materializedValues = new HashSet<>();

    private final Deque<Set<String>> forLoopScopedVariables = new ArrayDeque<>();

    private final Deque<Set<String>> branchScopedVariables = new ArrayDeque<>();

    private final Set<SSAValue> recordDeconstructionTemps = new HashSet<>();

    public RecoveryContext(IRMethod irMethod, MethodEntry sourceMethod, DefUseChains defUseChains) {
        this.irMethod = irMethod;
        this.sourceMethod = sourceMethod;
        this.defUseChains = defUseChains;
        this.locals = new MethodLocals(irMethod);
    }

    public IRMethod getIrMethod() {
        return irMethod;
    }

    public MethodEntry getSourceMethod() {
        return sourceMethod;
    }

    public DefUseChains getDefUseChains() {
        return defUseChains;
    }

    public MethodLocals getLocals() {
        return locals;
    }

    /** Reaching-definition partition of local slots into source variables. */
    public SlotVariablePartition getSlotPartition() {
        return slotPartition;
    }

    public void setSlotPartition(SlotVariablePartition slotPartition) {
        this.slotPartition = slotPartition;
    }

    private java.util.function.BiFunction<Integer, Integer, String> debugDescriptorResolver;

    public void setDebugDescriptorResolver(java.util.function.BiFunction<Integer, Integer, String> resolver) {
        this.debugDescriptorResolver = resolver;
    }

    /** The LocalVariableTable type descriptor for {@code slot} at {@code offset}, or null if unavailable. */
    public String debugDescriptorAt(int slot, int offset) {
        return debugDescriptorResolver == null ? null : debugDescriptorResolver.apply(slot, offset);
    }

    /** Recovered expressions keyed by SSA value */
    public Map<SSAValue, Expression> getRecoveredExpressions() {
        return recoveredExpressions;
    }

    /** Values that have been inlined (single-use optimization) */
    public Set<SSAValue> getInlinedValues() {
        return inlinedValues;
    }

    /** Generated variable names */
    public Map<SSAValue, String> getVariableNames() {
        return variableNames;
    }

    /** Counter for synthetic names */
    public int getSyntheticCounter() {
        return syntheticCounter;
    }

    /** Pending NewInstruction results waiting for <init> call */
    public Map<SSAValue, String> getPendingNewInstructions() {
        return pendingNewInstructions;
    }

    /** Local slots that contain pending new values (slot index -> class name) */
    public Map<Integer, String> getPendingNewLocalSlots() {
        return pendingNewLocalSlots;
    }

    /** Declared variable names in current scope */
    public Set<String> getDeclaredVariables() {
        return declaredVariables;
    }

    /** Types of declared variables */
    public Map<String, com.tonic.analysis.source.ast.type.SourceType> getDeclaredVariableTypes() {
        return declaredVariableTypes;
    }

    /** Maps local slot indices to their current variable names */
    public Map<Integer, String> getLocalSlotNames() {
        return localSlotNames;
    }

    /** SSA values that have been assigned to variables (should use var ref, not inline) */
    public Set<SSAValue> getMaterializedValues() {
        return materializedValues;
    }

    /** Stack of variables declared in for-loop init (scoped to loop body) */
    public Deque<Set<String>> getForLoopScopedVariables() {
        return forLoopScopedVariables;
    }

    /** Stack of variables declared in if-then-else branches (scoped to branch) */
    public Deque<Set<String>> getBranchScopedVariables() {
        return branchScopedVariables;
    }

    /** Cast results that are a record deconstruction's synthetic temp (the {@code (T) selector}). */
    public Set<SSAValue> getRecordDeconstructionTemps() {
        return recordDeconstructionTemps;
    }

    /** Maps SSA values to the slot index they were first stored to */
    public Map<SSAValue, Integer> getSsaValueSlot() {
        return ssaValueSlot;
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

    // --- Structural variable-role queries -----------------------------------------------------------
    // Whether a local slot holds the receiver / a parameter / a body local is derived from the method's
    // SSA parameter list and slot layout, NOT from the recovered name's prefix. This lets a variable carry
    // its real LocalVariableTable name without breaking recovery decisions that previously keyed on the
    // "arg"/"local"/"this" prefixes. The receiver is the first entry of getParameters() for an instance method.

    /** The local slot a parameter (or receiver) SSA value occupies, or -1 when {@code value} is not one. */
    public int parameterSlot(SSAValue value) {
        return locals.slotOfParameter(value);
    }

    /** True when local {@code slot} holds the receiver or a parameter rather than a body local. */
    public boolean isParameterOrThisSlot(int slot) {
        return locals.isParameterOrThisSlot(slot);
    }

    /** The zero-based parameter index for a parameter {@code slot} (receiver excluded), or -1 otherwise. */
    public int parameterIndexForSlot(int slot) {
        return locals.parameterIndexForSlot(slot);
    }
}
