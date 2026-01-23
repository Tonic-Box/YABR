package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import lombok.Getter;

import java.util.*;

/**
 * Shared context for control flow recovery operations.
 */
@Getter
public class ControlFlowContext {

    private final IRMethod irMethod;
    private final DominatorTree dominatorTree;
    private final LoopAnalysis loopAnalysis;
    private final RecoveryContext expressionContext;

    /** Blocks that have been processed */
    private final Set<IRBlock> processedBlocks = new HashSet<>();

    /** Recovered statements keyed by block */
    private final Map<IRBlock, List<Statement>> blockStatements = new HashMap<>();

    /** Block to structured region mapping */
    private final Map<IRBlock, StructuredRegion> blockToRegion = new HashMap<>();

    /** Labels generated for break/continue targets */
    private final Map<IRBlock, String> blockLabels = new HashMap<>();

    /** Pending statements to be added before the next statement */
    private final List<Statement> pendingStatements = new ArrayList<>();

    /** Stack of stop blocks from outer control structures (e.g., loop exits) */
    private final Deque<Set<IRBlock>> stopBlocksStack = new ArrayDeque<>();

    /** Stack of SSAValues known to be false/zero in current context */
    private final Deque<Set<SSAValue>> knownFalseValuesStack = new ArrayDeque<>();

    /** Stack of fields (by owner+name) known to be false/zero in current context */
    private final Deque<Set<FieldKey>> knownFalseFieldsStack = new ArrayDeque<>();

    /** Stack of instructions to skip during recovery (e.g., for-loop increment) */
    private final Deque<Set<IRInstruction>> skipInstructionsStack = new ArrayDeque<>();

    /** Permanent set of instructions to skip (e.g., for-loop initializers claimed by for-loops) */
    private final Set<IRInstruction> forLoopInitInstructions = new HashSet<>();

    /** Local indices that are for-loop induction variables (their PHI declarations should be skipped) */
    private final Set<Integer> forLoopInductionLocalIndices = new HashSet<>();

    /** PHI results for for-loop induction variables (these should not be declared early) */
    private final Set<SSAValue> forLoopInductionPhis = new HashSet<>();

    /** Blocks that are for-loop headers (used to scope local index checks) */
    private final Set<IRBlock> forLoopHeaderBlocks = new HashSet<>();

    private int labelCounter = 0;

    public ControlFlowContext(IRMethod irMethod, DominatorTree dominatorTree,
                              LoopAnalysis loopAnalysis, RecoveryContext expressionContext) {
        this.irMethod = irMethod;
        this.dominatorTree = dominatorTree;
        this.loopAnalysis = loopAnalysis;
        this.expressionContext = expressionContext;
    }

    public void markProcessed(IRBlock block) {
        processedBlocks.add(block);
    }

    public boolean isProcessed(IRBlock block) {
        return processedBlocks.contains(block);
    }

    public void setStatements(IRBlock block, List<Statement> stmts) {
        blockStatements.put(block, stmts);
    }

    public List<Statement> getStatements(IRBlock block) {
        return blockStatements.getOrDefault(block, Collections.emptyList());
    }

    public void setRegion(IRBlock block, StructuredRegion region) {
        blockToRegion.put(block, region);
    }

    public StructuredRegion getRegion(IRBlock block) {
        return blockToRegion.get(block);
    }

    public String getOrCreateLabel(IRBlock block) {
        return blockLabels.computeIfAbsent(block, b -> "label" + (labelCounter++));
    }

    public boolean hasLabel(IRBlock block) {
        return blockLabels.containsKey(block);
    }

    public String getLabel(IRBlock block) {
        return blockLabels.get(block);
    }

    /**
     * Adds statements that should be emitted before the next structured statement.
     * Used for header block instructions in if/while/etc.
     */
    public void addPendingStatements(List<Statement> stmts) {
        pendingStatements.addAll(stmts);
    }

    /**
     * Collects and clears any pending statements.
     * @return the pending statements, now cleared from context
     */
    public List<Statement> collectPendingStatements() {
        if (pendingStatements.isEmpty()) {
            return Collections.emptyList();
        }
        List<Statement> result = new ArrayList<>(pendingStatements);
        pendingStatements.clear();
        return result;
    }

    /**
     * Pushes a new set of stop blocks onto the stack.
     * Used when entering a control structure like a loop.
     */
    public void pushStopBlocks(Set<IRBlock> stopBlocks) {
        stopBlocksStack.push(stopBlocks);
    }

    /**
     * Pops the current stop blocks from the stack.
     * Used when exiting a control structure.
     */
    public void popStopBlocks() {
        if (!stopBlocksStack.isEmpty()) {
            stopBlocksStack.pop();
        }
    }

    /**
     * Gets all stop blocks from the entire stack (combined).
     * Used by inner control structures to respect outer exit points.
     */
    public Set<IRBlock> getAllStopBlocks() {
        Set<IRBlock> combined = new HashSet<>();
        for (Set<IRBlock> stopBlocks : stopBlocksStack) {
            combined.addAll(stopBlocks);
        }
        return combined;
    }

    /**
     * Pushes a set of instructions to skip during recovery.
     * Used for for-loop increment statements that are extracted to the update expression.
     */
    public void pushSkipInstructions(Set<IRInstruction> instructions) {
        skipInstructionsStack.push(instructions);
    }

    /**
     * Pops the current skip instructions from the stack.
     */
    public void popSkipInstructions() {
        if (!skipInstructionsStack.isEmpty()) {
            skipInstructionsStack.pop();
        }
    }

    /**
     * Checks if an instruction should be skipped during recovery.
     */
    public boolean shouldSkipInstruction(IRInstruction instruction) {
        if (forLoopInitInstructions.contains(instruction)) {
            return true;
        }
        for (Set<IRInstruction> skipInstructions : skipInstructionsStack) {
            if (skipInstructions.contains(instruction)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks an instruction as a for-loop initializer that should be skipped
     * when processing predecessor blocks (because it will be inlined into the for-loop).
     */
    public void markAsForLoopInit(IRInstruction instruction) {
        forLoopInitInstructions.add(instruction);
    }

    /**
     * Checks if an instruction has been claimed as a for-loop initializer.
     */
    public boolean isForLoopInit(IRInstruction instruction) {
        return forLoopInitInstructions.contains(instruction);
    }

    /**
     * Marks a local index as a for-loop induction variable.
     * PHI declarations for this local should be skipped since the variable
     * will be declared in the for-loop init.
     */
    public void markAsForLoopInductionLocal(int localIndex) {
        forLoopInductionLocalIndices.add(localIndex);
    }

    /**
     * Checks if a local index is a for-loop induction variable.
     */
    public boolean isForLoopInductionLocal(int localIndex) {
        return forLoopInductionLocalIndices.contains(localIndex);
    }

    /**
     * Marks a PHI result as a for-loop induction PHI.
     * Such PHIs should not have their declaration emitted early.
     */
    public void markAsForLoopInductionPhi(SSAValue phiResult) {
        forLoopInductionPhis.add(phiResult);
    }

    /**
     * Checks if a PHI result is a for-loop induction PHI.
     */
    public boolean isForLoopInductionPhi(SSAValue phiResult) {
        return forLoopInductionPhis.contains(phiResult);
    }

    /**
     * Marks a block as a for-loop header.
     */
    public void markAsForLoopHeader(IRBlock block) {
        forLoopHeaderBlocks.add(block);
    }

    /**
     * Checks if a block is a for-loop header.
     */
    public boolean isForLoopHeader(IRBlock block) {
        return forLoopHeaderBlocks.contains(block);
    }

    /**
     * Pushes SSAValues that are known to be false/zero in the current context.
     * Used when entering the "then" branch of if(!condition).
     */
    public void pushKnownFalseValues(Set<SSAValue> values) {
        knownFalseValuesStack.push(values);
    }

    /**
     * Pops the current known false values from the stack.
     */
    public void popKnownFalseValues() {
        if (!knownFalseValuesStack.isEmpty()) {
            knownFalseValuesStack.pop();
        }
    }

    /**
     * Checks if an SSAValue is known to be false/zero in the current context.
     */
    public boolean isKnownFalse(SSAValue value) {
        for (Set<SSAValue> falseValues : knownFalseValuesStack) {
            if (falseValues.contains(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pushes fields (by owner+name) that are known to be false/zero in the current context.
     * Used alongside pushKnownFalseValues for semantic field tracking.
     */
    public void pushKnownFalseFields(Set<FieldKey> fields) {
        knownFalseFieldsStack.push(fields);
    }

    /**
     * Pops the current known false fields from the stack.
     */
    public void popKnownFalseFields() {
        if (!knownFalseFieldsStack.isEmpty()) {
            knownFalseFieldsStack.pop();
        }
    }

    /**
     * Checks if a field (by owner+name) is known to be false/zero in the current context.
     */
    public boolean isFieldKnownFalse(String owner, String fieldName) {
        FieldKey key = new FieldKey(owner, fieldName);
        for (Set<FieldKey> falseFields : knownFalseFieldsStack) {
            if (falseFields.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Represents a field by its owner class and field name.
     * Used for semantic identity tracking across SSAValues.
     */
    @Getter
    public static class FieldKey {
        private final String owner;
        private final String fieldName;

        public FieldKey(String owner, String fieldName) {
            this.owner = owner;
            this.fieldName = fieldName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FieldKey fieldKey = (FieldKey) o;
            return Objects.equals(owner, fieldKey.owner) &&
                   Objects.equals(fieldName, fieldKey.fieldName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, fieldName);
        }

        @Override
        public String toString() {
            return owner + "." + fieldName;
        }
    }

    /**
     * Represents a structured control flow region.
     */
    public enum StructuredRegion {
        IF_THEN,
        IF_THEN_ELSE,
        WHILE_LOOP,
        DO_WHILE_LOOP,
        FOR_LOOP,
        SWITCH,
        TRY_CATCH,
        SEQUENCE,
        IRREDUCIBLE,
        GUARD_CLAUSE
    }
}
