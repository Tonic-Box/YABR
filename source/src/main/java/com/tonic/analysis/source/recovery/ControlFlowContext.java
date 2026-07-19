package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.SSAValue;

import java.util.*;

/**
 * Shared context for control flow recovery operations.
 */
public class ControlFlowContext {

    private final IRMethod irMethod;
    private final DominatorTree dominatorTree;
    private final LoopAnalysis loopAnalysis;
    private final RecoveryContext expressionContext;

    private final Set<IRBlock> processedBlocks = new HashSet<>();

    private final Map<IRBlock, List<Statement>> blockStatements = new HashMap<>();

    private final Map<IRBlock, StructuredRegion> blockToRegion = new HashMap<>();

    private final Map<IRBlock, String> blockLabels = new HashMap<>();

    private final Deque<LoopFrame> loopStack = new ArrayDeque<>();

    private final Deque<SwitchFrame> switchStack = new ArrayDeque<>();

    private final List<Statement> pendingStatements = new ArrayList<>();

    private final Deque<Set<IRBlock>> stopBlocksStack = new ArrayDeque<>();

    private final Deque<Set<SSAValue>> knownFalseValuesStack = new ArrayDeque<>();

    private final Deque<Set<FieldKey>> knownFalseFieldsStack = new ArrayDeque<>();

    private final Deque<Set<IRInstruction>> skipInstructionsStack = new ArrayDeque<>();

    private final Set<IRInstruction> forLoopInitInstructions = new HashSet<>();

    private final Set<Integer> forLoopInductionLocalIndices = new HashSet<>();

    private final Set<SSAValue> forLoopInductionPhis = new HashSet<>();

    private final Set<IRBlock> forLoopHeaderBlocks = new HashSet<>();

    private int labelCounter = 0;

    public ControlFlowContext(IRMethod irMethod, DominatorTree dominatorTree,
                              LoopAnalysis loopAnalysis, RecoveryContext expressionContext) {
        this.irMethod = irMethod;
        this.dominatorTree = dominatorTree;
        this.loopAnalysis = loopAnalysis;
        this.expressionContext = expressionContext;
    }

    public IRMethod getIrMethod() {
        return irMethod;
    }

    public DominatorTree getDominatorTree() {
        return dominatorTree;
    }

    public LoopAnalysis getLoopAnalysis() {
        return loopAnalysis;
    }

    public RecoveryContext getExpressionContext() {
        return expressionContext;
    }

    /** Blocks that have been processed */
    public Set<IRBlock> getProcessedBlocks() {
        return processedBlocks;
    }

    /** Recovered statements keyed by block */
    public Map<IRBlock, List<Statement>> getBlockStatements() {
        return blockStatements;
    }

    /** Block to structured region mapping */
    public Map<IRBlock, StructuredRegion> getBlockToRegion() {
        return blockToRegion;
    }

    /** Labels generated for break/continue targets */
    public Map<IRBlock, String> getBlockLabels() {
        return blockLabels;
    }

    /** Stack of enclosing loops (innermost first) for recovering break/continue (labeled to an outer loop). */
    public Deque<LoopFrame> getLoopStack() {
        return loopStack;
    }

    /** Pending statements to be added before the next statement */
    public List<Statement> getPendingStatements() {
        return pendingStatements;
    }

    /** Stack of stop blocks from outer control structures (e.g., loop exits) */
    public Deque<Set<IRBlock>> getStopBlocksStack() {
        return stopBlocksStack;
    }

    /** Stack of SSAValues known to be false/zero in current context */
    public Deque<Set<SSAValue>> getKnownFalseValuesStack() {
        return knownFalseValuesStack;
    }

    /** Stack of fields (by owner+name) known to be false/zero in current context */
    public Deque<Set<FieldKey>> getKnownFalseFieldsStack() {
        return knownFalseFieldsStack;
    }

    /** Stack of instructions to skip during recovery (e.g., for-loop increment) */
    public Deque<Set<IRInstruction>> getSkipInstructionsStack() {
        return skipInstructionsStack;
    }

    /** Permanent set of instructions to skip (e.g., for-loop initializers claimed by for-loops) */
    public Set<IRInstruction> getForLoopInitInstructions() {
        return forLoopInitInstructions;
    }

    /** Local indices that are for-loop induction variables (their PHI declarations should be skipped) */
    public Set<Integer> getForLoopInductionLocalIndices() {
        return forLoopInductionLocalIndices;
    }

    /** PHI results for for-loop induction variables (these should not be declared early) */
    public Set<SSAValue> getForLoopInductionPhis() {
        return forLoopInductionPhis;
    }

    /** Blocks that are for-loop headers (used to scope local index checks) */
    public Set<IRBlock> getForLoopHeaderBlocks() {
        return forLoopHeaderBlocks;
    }

    public int getLabelCounter() {
        return labelCounter;
    }

    public void markProcessed(IRBlock block) {
        processedBlocks.add(block);
    }

    public boolean isProcessed(IRBlock block) {
        return processedBlocks.contains(block);
    }

    /**
     * Clears the emitted-block marks and their cached statements so a fresh recovery pass over the same
     * method starts clean. The legacy walk is self-idempotent (it tracks visited blocks locally), but the
     * reaching-condition engine reads these marks to emit each block once - without a reset, a second
     * {@code recover()} on the same instance would see every block already emitted and produce nothing.
     */
    public void resetProcessedBlocks() {
        processedBlocks.clear();
        blockStatements.clear();
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
     * An enclosing loop: its header (label anchor), continue-target (latch/increment) and exit block. {@code depth}
     * is the number of enclosing break/continue scopes (loops and switches) at push time, so a jump can tell whether
     * this loop is the innermost break scope across both stacks.
     */
    public static final class LoopFrame {
        final IRBlock header;
        final IRBlock continueTarget;
        final IRBlock exit;
        final int depth;
        LoopFrame(IRBlock header, IRBlock continueTarget, IRBlock exit, int depth) {
            this.header = header;
            this.continueTarget = continueTarget;
            this.exit = exit;
            this.depth = depth;
        }
    }

    /**
     * An enclosing {@code switch}: its {@code merge} (where a case ends and control leaves the switch) and its
     * {@code caseHeaders} (the sibling case entries, i.e. fall-through targets). A switch captures an unlabeled
     * {@code break} but not an unlabeled {@code continue}.
     */
    public static final class SwitchFrame {
        final IRBlock header;
        final IRBlock merge;
        final Set<IRBlock> caseHeaders;
        final int depth;
        SwitchFrame(IRBlock header, IRBlock merge, Set<IRBlock> caseHeaders, int depth) {
            this.header = header;
            this.merge = merge;
            this.caseHeaders = caseHeaders;
            this.depth = depth;
        }
    }

    public enum JumpKind { BREAK, CONTINUE }

    /** A break/continue jump. {@code loopHeader} is null for the innermost loop (unlabeled), else the labeled target. */
    public static final class LoopJump {
        public final JumpKind kind;
        public final IRBlock loopHeader;
        LoopJump(JumpKind kind, IRBlock loopHeader) {
            this.kind = kind;
            this.loopHeader = loopHeader;
        }
    }

    public enum SwitchJumpKind { BREAK_SWITCH, FALL_THROUGH }

    /** A jump within a switch: leaving it at its merge, or falling through to a sibling {@code caseHeader}. */
    public static final class SwitchJump {
        public final SwitchJumpKind kind;
        public final IRBlock caseHeader;
        SwitchJump(SwitchJumpKind kind, IRBlock caseHeader) {
            this.kind = kind;
            this.caseHeader = caseHeader;
        }
    }

    private int scopeDepth() {
        return loopStack.size() + switchStack.size();
    }

    public void pushLoop(IRBlock header, IRBlock continueTarget, IRBlock exit) {
        loopStack.push(new LoopFrame(header, continueTarget, exit, scopeDepth()));
    }

    public void popLoop() {
        if (!loopStack.isEmpty()) {
            loopStack.pop();
        }
    }

    public void pushSwitch(IRBlock header, IRBlock merge, Set<IRBlock> caseHeaders) {
        switchStack.push(new SwitchFrame(header, merge, caseHeaders, scopeDepth()));
    }

    public void popSwitch() {
        if (!switchStack.isEmpty()) {
            switchStack.pop();
        }
    }

    /**
     * Classifies a control-flow edge into {@code target}: a {@code break} when {@code target} is a loop's exit, a
     * {@code continue} when it is a loop's continue-target. A jump is unlabeled only when its loop is the innermost
     * scope of the relevant kind: for {@code break}, the innermost of all loops and switches (an enclosing loop broken
     * across a switch or inner loop is labeled, since a bare {@code break} would leave the switch or inner loop); for
     * {@code continue}, the innermost loop (switches do not capture {@code continue}). Returns null when {@code target}
     * is not a loop boundary.
     */
    public LoopJump classifyLoopJump(IRBlock target) {
        int innermostBreak = scopeDepth() - 1;
        int innermostLoop = loopStack.isEmpty() ? -1 : loopStack.peek().depth;
        for (LoopFrame f : loopStack) {
            if (target == f.exit) {
                return new LoopJump(JumpKind.BREAK, f.depth == innermostBreak ? null : f.header);
            }
            if (target == f.continueTarget) {
                return new LoopJump(JumpKind.CONTINUE, f.depth == innermostLoop ? null : f.header);
            }
        }
        return null;
    }

    /**
     * Classifies an edge into {@code target} relative to the innermost {@code switch}: {@code BREAK_SWITCH} when it is
     * that switch's merge (the case ends and control leaves the switch), {@code FALL_THROUGH} when it is a sibling case
     * header. Returns null when {@code target} is not an innermost-switch boundary. A loop boundary takes precedence and
     * must be classified with {@link #classifyLoopJump} first.
     */
    public SwitchJump classifySwitchJump(IRBlock target) {
        SwitchFrame f = switchStack.peek();
        if (f == null) {
            return null;
        }
        if (target == f.merge) {
            return new SwitchJump(SwitchJumpKind.BREAK_SWITCH, null);
        }
        if (f.caseHeaders.contains(target)) {
            return new SwitchJump(SwitchJumpKind.FALL_THROUGH, target);
        }
        return null;
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
    public static class FieldKey {
        private final String owner;
        private final String fieldName;

        public FieldKey(String owner, String fieldName) {
            this.owner = owner;
            this.fieldName = fieldName;
        }

        public String getOwner() {
            return owner;
        }

        public String getFieldName() {
            return fieldName;
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
