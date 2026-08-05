package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.MethodEntry;
import java.util.*;

/**
 * Holds shared state during expression recovery.
 */
public class RecoveryContext
{

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
    private final Set<String> baselineDeclaredVariables = new HashSet<>();

    private final Map<String, SourceType> declaredVariableTypes = new HashMap<>();

    private final Map<Integer, String> localSlotNames = new HashMap<>();

    private final Set<SSAValue> materializedValues = new HashSet<>();
    private final Set<SSAValue> pinnedToVariable = new HashSet<>();

    private final Deque<Set<String>> forLoopScopedVariables = new ArrayDeque<>();

    private final Deque<Set<String>> branchScopedVariables = new ArrayDeque<>();

    private final Set<SSAValue> recordDeconstructionTemps = new HashSet<>();

    /**
     * Creates an empty context, deriving the local model from the IR method.
     *
     * @param irMethod SSA method being recovered
     * @param sourceMethod the class file method it came from
     * @param defUseChains def-use chains over the same IR
     */
    public RecoveryContext(IRMethod irMethod, MethodEntry sourceMethod, DefUseChains defUseChains)
    {
        this.irMethod = irMethod;
        this.sourceMethod = sourceMethod;
        this.defUseChains = defUseChains;
        this.locals = new MethodLocals(irMethod);
    }

    /**
     * @return the ir method
     */
    public IRMethod getIrMethod()
    {
        return irMethod;
    }

    /**
     * @return the source method
     */
    public MethodEntry getSourceMethod()
    {
        return sourceMethod;
    }

    /**
     * @return the def use chains
     */
    public DefUseChains getDefUseChains()
    {
        return defUseChains;
    }

    /**
     * @return the locals
     */
    public MethodLocals getLocals()
    {
        return locals;
    }

    /**
     * @return the reaching-definition partition of local slots into source variables
     */
    public SlotVariablePartition getSlotPartition()
    {
        return slotPartition;
    }

    /**
     * @param slotPartition partition of local slots into source variables
     */
    public void setSlotPartition(SlotVariablePartition slotPartition)
    {
        this.slotPartition = slotPartition;
    }

    private java.util.function.IntFunction<String> debugNameResolver;

    /**
     * Installs the lookup backing debugNameForSlot.
     *
     * @param resolver slot to recovered debug name, or null to disable the lookup
     */
    public void setDebugNameResolver(java.util.function.IntFunction<String> resolver)
    {
        this.debugNameResolver = resolver;
    }

    /**
     * The recovered debug name a slot carries. Callers ask this instead of inspecting the shape of a name they
     * were handed - a generated name and a recorded one are then distinguished by where they came from, not by
     * whether they happen to start with a particular prefix.
     *
     * @param slot local slot index
     * @return the recovered name, or null when the class carries none for the slot or the active
     *         name-recovery strategy declines it
     */
    public String debugNameForSlot(int slot)
    {
        return debugNameResolver == null ? null : debugNameResolver.apply(slot);
    }

    private java.util.function.BiFunction<Integer, Integer, String> debugDescriptorResolver;

    /**
     * Installs the lookup backing debugDescriptorAt.
     *
     * @param resolver slot and bytecode offset to LVT descriptor, or null to disable the lookup
     */
    public void setDebugDescriptorResolver(java.util.function.BiFunction<Integer, Integer, String> resolver)
    {
        this.debugDescriptorResolver = resolver;
    }

    /**
     * The LocalVariableTable type descriptor a slot carries at a bytecode offset.
     *
     * @param slot local slot index
     * @param offset bytecode offset to read the table at
     * @return the descriptor, or null when no resolver is installed or the table has no entry
     */
    public String debugDescriptorAt(int slot, int offset)
    {
        return debugDescriptorResolver == null ? null : debugDescriptorResolver.apply(slot, offset);
    }

    private java.util.function.BiFunction<Integer, Integer, String> debugStoreDescriptorResolver;

    /**
     * Installs the lookup backing debugDescriptorAtStore.
     *
     * @param resolver slot and store offset to LVT descriptor, or null to disable the lookup
     */
    public void setDebugStoreDescriptorResolver(java.util.function.BiFunction<Integer, Integer, String> resolver)
    {
        this.debugStoreDescriptorResolver = resolver;
    }

    /**
     * The LVT descriptor at the pc where a store takes effect.
     *
     * @param slot local slot index
     * @param storeOffset bytecode offset of the store
     * @return the descriptor, or null when no resolver is installed or the table has no entry
     */
    public String debugDescriptorAtStore(int slot, int storeOffset)
    {
        return debugStoreDescriptorResolver == null ? null
                : debugStoreDescriptorResolver.apply(slot, storeOffset);
    }

    /**
     * @return the recovered expression of each recovered SSA value
     */
    public Map<SSAValue, Expression> getRecoveredExpressions()
    {
        return recoveredExpressions;
    }

    /**
     * @return values folded into their single use instead of being assigned
     */
    public Set<SSAValue> getInlinedValues()
    {
        return inlinedValues;
    }

    /**
     * @return the name bound to each named SSA value
     */
    public Map<SSAValue, String> getVariableNames()
    {
        return variableNames;
    }

    /**
     * @return the counter backing synthetic name generation
     */
    public int getSyntheticCounter()
    {
        return syntheticCounter;
    }

    /**
     * @return the class name of each allocation still waiting for its {@code <init>} call
     */
    public Map<SSAValue, String> getPendingNewInstructions()
    {
        return pendingNewInstructions;
    }

    /**
     * @return the class name of the pending new value held in each local slot
     */
    public Map<Integer, String> getPendingNewLocalSlots()
    {
        return pendingNewLocalSlots;
    }

    /**
     * @return the variable names declared in the current scope
     */
    public Set<String> getDeclaredVariables()
    {
        return declaredVariables;
    }

    /**
     * @return the declared type of each declared variable name
     */
    public Map<String, SourceType> getDeclaredVariableTypes()
    {
        return declaredVariableTypes;
    }

    /**
     * @return the current variable name of each local slot index
     */
    public Map<Integer, String> getLocalSlotNames()
    {
        return localSlotNames;
    }

    /**
     * @return values assigned to variables, which uses must reference by name instead of inlining
     */
    public Set<SSAValue> getMaterializedValues()
    {
        return materializedValues;
    }

    /**
     * @return the stack of variable sets declared in for-loop inits, scoped to the loop body
     */
    public Deque<Set<String>> getForLoopScopedVariables()
    {
        return forLoopScopedVariables;
    }

    /**
     * @return the stack of variable sets declared in if-then-else branches, scoped to the branch
     */
    public Deque<Set<String>> getBranchScopedVariables()
    {
        return branchScopedVariables;
    }

    /**
     * @return cast results that are a record deconstruction's synthetic temp (the {@code (T) selector})
     */
    public Set<SSAValue> getRecordDeconstructionTemps()
    {
        return recordDeconstructionTemps;
    }

    /**
     * @return the slot index each SSA value was first stored to
     */
    public Map<SSAValue, Integer> getSsaValueSlot()
    {
        return ssaValueSlot;
    }

    /**
     * Stores the recovered expression for a value, replacing any earlier one.
     *
     * @param value value the expression computes
     * @param expr recovered expression
     */
    public void cacheExpression(SSAValue value, Expression expr)
    {
        recoveredExpressions.put(value, expr);
    }

    /**
     * @param value value to look up
     * @return the cached expression, or null if the value has not been recovered
     */
    public Expression getCachedExpression(SSAValue value)
    {
        return recoveredExpressions.get(value);
    }

    /**
     * @param value value to test
     * @return true if an expression has been cached for the value
     */
    public boolean isRecovered(SSAValue value)
    {
        return recoveredExpressions.containsKey(value);
    }

    /**
     * Records that a value's expression was folded into its single use rather than assigned.
     *
     * @param value value that was inlined
     */
    public void markInlined(SSAValue value)
    {
        inlinedValues.add(value);
    }

    /**
     * @param value value to test
     * @return true if the value was marked inlined
     */
    public boolean isInlined(SSAValue value)
    {
        return inlinedValues.contains(value);
    }

    /**
     * Binds a name to a value, refusing to rename a parameter that already has one.
     *
     * @param value value to name
     * @param name name to bind
     */
    public void setVariableName(SSAValue value, String name)
    {
        // A parameter's name is fixed by the signature. The lifter forwards a slot load straight to the
        // parameter value it copies, so a slot-derived name would RENAME the parameter everywhere - and the
        // copy's own declaration (`float c1 = p1;`) then reads as an identity store and is dropped.
        if (irMethod.getParameters().contains(value) && variableNames.containsKey(value))
        {
            return;
        }
        variableNames.put(value, name);
    }

    /**
     * @param value value to look up
     * @return the name bound to the value, or null if it has none
     */
    public String getVariableName(SSAValue value)
    {
        return variableNames.get(value);
    }

    /**
     * @return the next synthetic name id, advancing the counter
     */
    public int nextSyntheticId()
    {
        return syntheticCounter++;
    }

    /**
     * Registers an allocation result that awaits its {@code <init>} call.
     *
     * @param result value produced by the allocation
     * @param className class being allocated
     */
    public void registerPendingNew(SSAValue result, String className)
    {
        pendingNewInstructions.put(result, className);
    }

    /**
     * @param value value to test
     * @return true if the value is an allocation still waiting for its constructor call
     */
    public boolean isPendingNew(SSAValue value)
    {
        return pendingNewInstructions.containsKey(value);
    }

    /**
     * Removes the pending new record for a value and returns its class name.
     *
     * @param value allocation result
     * @return the class name held pending for the value, or null if there is none
     */
    public String consumePendingNew(SSAValue value)
    {
        return pendingNewInstructions.remove(value);
    }

    /**
     * Registers a local slot as containing a pending new value.
     *
     * @param localIndex local slot the value was stored to
     * @param className class being allocated
     */
    public void registerPendingNewLocalSlot(int localIndex, String className)
    {
        pendingNewLocalSlots.put(localIndex, className);
    }

    /**
     * Removes the pending new record for a local slot and returns its class name.
     *
     * @param localIndex local slot index
     * @return the class name held pending for the slot, or null if there is none
     */
    public String consumePendingNewLocalSlot(int localIndex)
    {
        return pendingNewLocalSlots.remove(localIndex);
    }

    /**
     * Marks a variable name as declared.
     * If inside a branch scope, tracks it for removal when the branch ends.
     *
     * @param name variable name being declared
     */
    public void markDeclared(String name)
    {
        declaredVariables.add(name);
        if (!branchScopedVariables.isEmpty())
        {
            branchScopedVariables.peek().add(name);
        }
    }

    /**
     * Marks a variable name as declared, recording its type when one is given.
     *
     * @param name variable name being declared
     * @param type declared type, or null to record none
     */
    public void markDeclaredWithType(String name, SourceType type)
    {
        markDeclared(name);
        if (type != null)
        {
            declaredVariableTypes.put(name, type);
        }
    }

    /**
     * @param name variable name to look up
     * @return the declared type recorded for the name, or null if none is tracked
     */
    public SourceType getDeclaredType(String name)
    {
        return declaredVariableTypes.get(name);
    }

    /**
     * @param name variable name to test
     * @return true if the name has been declared
     */
    public boolean isDeclared(String name)
    {
        return declaredVariables.contains(name);
    }

    /**
     * Clears declared variables (for new scope).
     */
    public void clearDeclaredVariables()
    {
        declaredVariables.clear();
    }

    /**
     * Snapshots the current declared set as the method's baseline (the pre-declared parameters), so a full
     * re-pass can restore it. Without the restore, declarations emitted by a DISCARDED earlier attempt leak
     * into the re-pass and its stores recover as bare assignments with the declarations gone.
     */
    public void baselineDeclaredVariables()
    {
        baselineDeclaredVariables.clear();
        baselineDeclaredVariables.addAll(declaredVariables);
    }

    /**
     * Restores the declared set to the method baseline captured by {@link #baselineDeclaredVariables()}.
     */
    public void resetDeclaredVariablesToBaseline()
    {
        declaredVariables.clear();
        declaredVariables.addAll(baselineDeclaredVariables);
    }

    /**
     * Marks an SSA value as materialized into a variable.
     * After this, subsequent uses should reference the variable, not inline the expression.
     *
     * @param value value that was assigned to a variable
     */
    public void markMaterialized(SSAValue value)
    {
        materializedValues.add(value);
    }

    /**
     * @param value value to test
     * @return true if the value has been materialized into a variable
     */
    public boolean isMaterialized(SSAValue value)
    {
        return materializedValues.contains(value);
    }

    /**
     * Unmarks an SSA value as materialized, allowing it to be inlined again.
     * Used when a PHI variable is collapsed to a boolean expression.
     *
     * @param value value to unmark
     */
    public void unmarkMaterialized(SSAValue value)
    {
        materializedValues.remove(value);
    }

    /**
     * Pins a value to its variable: it must always be referenced by name, never force-inlined at a use, even
     * when it would otherwise qualify (e.g. a single-use invoke). Used for a value that feeds a phi and is also
     * consumed elsewhere - inlining it would drop the phi variable's assignment and duplicate a side effect.
     *
     * @param value value to pin
     */
    public void pinToVariable(SSAValue value)
    {
        pinnedToVariable.add(value);
    }

    /**
     * @param value value to test
     * @return true if the value was pinned and so must never be force-inlined at a use
     */
    public boolean isPinnedToVariable(SSAValue value)
    {
        return pinnedToVariable.contains(value);
    }

    /**
     * Pushes a new scope for for-loop variables.
     * Variables declared in for-loop init will be tracked in this scope.
     */
    public void pushForLoopScope()
    {
        forLoopScopedVariables.push(new HashSet<>());
    }

    /**
     * Pops the current for-loop scope and removes its variables from declaredVariables.
     * This allows the same variable name to be re-declared in subsequent for-loops.
     */
    public void popForLoopScope()
    {
        if (!forLoopScopedVariables.isEmpty())
        {
            Set<String> loopVars = forLoopScopedVariables.pop();
            for (String var : loopVars)
            {
                declaredVariables.remove(var);
            }
        }
    }

    /**
     * Marks a variable as declared in a for-loop init expression.
     * The variable will be removed from declaredVariables when the loop scope is popped.
     *
     * @param name variable name being declared
     */
    public void markDeclaredInForLoopInit(String name)
    {
        declaredVariables.add(name);
        if (!forLoopScopedVariables.isEmpty())
        {
            forLoopScopedVariables.peek().add(name);
        }
    }

    /**
     * Pushes a new scope for if-then-else branch variables.
     * Variables declared in this branch will be tracked and removed when the scope is popped.
     */
    public void pushBranchScope()
    {
        branchScopedVariables.push(new HashSet<>());
    }

    /**
     * Pops the current branch scope and removes its variables from declaredVariables.
     * This allows sibling branches to declare the same variable independently.
     */
    public void popBranchScope()
    {
        if (!branchScopedVariables.isEmpty())
        {
            Set<String> branchVars = branchScopedVariables.pop();
            for (String var : branchVars)
            {
                declaredVariables.remove(var);
            }
        }
    }

    /**
     * Marks a variable as declared in the current scope.
     * If inside a branch scope, tracks it for removal when the branch ends.
     *
     * @param name variable name being declared
     */
    public void markDeclaredInBranch(String name)
    {
        declaredVariables.add(name);
        if (!branchScopedVariables.isEmpty())
        {
            branchScopedVariables.peek().add(name);
        }
    }

    /**
     * @return true while a branch scope is open
     */
    public boolean isInBranchScope()
    {
        return !branchScopedVariables.isEmpty();
    }

    /**
     * Binds a variable name to a local slot so later loads from the slot recover the same name.
     *
     * @param slotIndex local slot index
     * @param name name to bind
     */
    public void setLocalSlotName(int slotIndex, String name)
    {
        localSlotNames.put(slotIndex, name);
    }

    /**
     * @param slotIndex local slot index
     * @return the name bound to the slot, or null when none has been set
     */
    public String getLocalSlotName(int slotIndex)
    {
        return localSlotNames.get(slotIndex);
    }

    private final Map<SSAValue, Integer> ssaValueSlot = new HashMap<>();

    /**
     * Records the slot an SSA value was stored to, keeping the first one recorded.
     *
     * @param value value that was stored
     * @param slotIndex local slot it was stored to
     */
    public void setSSAValueSlot(SSAValue value, int slotIndex)
    {
        if (!ssaValueSlot.containsKey(value))
        {
            ssaValueSlot.put(value, slotIndex);
        }
    }

    /**
     * @param value value to look up
     * @return the slot the value was first stored to, or -1 when it is not tracked
     */
    public int getSSAValueSlot(SSAValue value)
    {
        return ssaValueSlot.getOrDefault(value, -1);
    }

    // Structural variable-role queries
    // Whether a local slot holds the receiver / a parameter / a body local is derived from the method's
    // SSA parameter list and slot layout, NOT from the recovered name's prefix. This lets a variable carry
    // its real LocalVariableTable name without breaking recovery decisions that previously keyed on the
    // "arg"/"local"/"this" prefixes. The receiver is the first entry of getParameters() for an instance method.

    /**
     * @param value value to look up
     * @return the local slot a parameter or receiver value occupies, or -1 when the value is neither
     */
    public int parameterSlot(SSAValue value)
    {
        return locals.slotOfParameter(value);
    }

    /**
     * @param slot local slot index
     * @return true when the slot holds the receiver or a parameter rather than a body local
     */
    public boolean isParameterOrThisSlot(int slot)
    {
        return locals.isParameterOrThisSlot(slot);
    }

    /**
     * The zero-based parameter index of a parameter slot, with the receiver excluded from the numbering.
     *
     * @param slot local slot index
     * @return the parameter index, or -1 when the slot does not hold a parameter
     */
    public int parameterIndexForSlot(int slot)
    {
        return locals.parameterIndexForSlot(slot);
    }
}
