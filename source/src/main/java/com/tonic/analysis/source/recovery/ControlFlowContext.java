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
public class ControlFlowContext
{

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

    /**
     * Creates a recovery context over one method's analyses.
     *
     * @param irMethod the method being recovered
     * @param dominatorTree its dominator tree
     * @param loopAnalysis its loop analysis
     * @param expressionContext the expression-level recovery context
     */
    public ControlFlowContext(IRMethod irMethod, DominatorTree dominatorTree, LoopAnalysis loopAnalysis, RecoveryContext expressionContext)
    {
        this.irMethod = irMethod;
        this.dominatorTree = dominatorTree;
        this.loopAnalysis = loopAnalysis;
        this.expressionContext = expressionContext;
    }

    /**
     * @return the ir method
     */
    public IRMethod getIrMethod()
    {
        return irMethod;
    }

    /**
     * @return the dominator tree
     */
    public DominatorTree getDominatorTree()
    {
        return dominatorTree;
    }

    /**
     * @return the loop analysis
     */
    public LoopAnalysis getLoopAnalysis()
    {
        return loopAnalysis;
    }

    /**
     * @return the expression context
     */
    public RecoveryContext getExpressionContext()
    {
        return expressionContext;
    }

    /**
     * @return the live set of blocks recovery has already consumed
     */
    public Set<IRBlock> getProcessedBlocks()
    {
        return processedBlocks;
    }

    /**
     * @return the recovered statements keyed by the block they came from
     */
    public Map<IRBlock, List<Statement>> getBlockStatements()
    {
        return blockStatements;
    }

    /**
     * @return the structured region each block was assigned to
     */
    public Map<IRBlock, StructuredRegion> getBlockToRegion()
    {
        return blockToRegion;
    }

    /**
     * @return the labels generated for blocks that are break or continue targets
     */
    public Map<IRBlock, String> getBlockLabels()
    {
        return blockLabels;
    }

    /**
     * @return the enclosing loop frames, innermost first, which resolve break and continue targets
     *         and decide when a label is needed
     */
    public Deque<LoopFrame> getLoopStack()
    {
        return loopStack;
    }

    /**
     * @return the live queue of statements to emit before the next structured statement
     */
    public List<Statement> getPendingStatements()
    {
        return pendingStatements;
    }

    /**
     * @return the scoped stack of blocks that bound each enclosing control structure
     */
    public Deque<Set<IRBlock>> getStopBlocksStack()
    {
        return stopBlocksStack;
    }

    /**
     * @return the scoped stack of SSA values proven false or zero
     */
    public Deque<Set<SSAValue>> getKnownFalseValuesStack()
    {
        return knownFalseValuesStack;
    }

    /**
     * @return the scoped stack of fields, keyed by owner and name, proven false or zero
     */
    public Deque<Set<FieldKey>> getKnownFalseFieldsStack()
    {
        return knownFalseFieldsStack;
    }

    /**
     * @return the scoped stack of instruction sets recovery must not emit, such as a lifted
     *         for-loop increment
     */
    public Deque<Set<IRInstruction>> getSkipInstructionsStack()
    {
        return skipInstructionsStack;
    }

    /**
     * @return the instructions permanently skipped because a for-loop claimed them as initializers
     */
    public Set<IRInstruction> getForLoopInitInstructions()
    {
        return forLoopInitInstructions;
    }

    /**
     * @return the local slots holding for-loop induction variables, whose PHI declarations are skipped
     */
    public Set<Integer> getForLoopInductionLocalIndices()
    {
        return forLoopInductionLocalIndices;
    }

    /**
     * @return the PHI results carrying for-loop induction variables, which must not be declared early
     */
    public Set<SSAValue> getForLoopInductionPhis()
    {
        return forLoopInductionPhis;
    }

    /**
     * @return the for-loop header blocks, which scope the induction local index checks
     */
    public Set<IRBlock> getForLoopHeaderBlocks()
    {
        return forLoopHeaderBlocks;
    }

    /**
     * @return the label counter
     */
    public int getLabelCounter()
    {
        return labelCounter;
    }

    /**
     * Marks a block as emitted so it is not recovered a second time.
     *
     * @param block the block to mark
     * @throws NumberFormatException if "yabr.debug.mark" is not an integer
     */
    public void markProcessed(IRBlock block)
    {
        String dbg = System.getProperty("yabr.debug.mark");
        if (dbg != null && block.getBytecodeOffset() == Integer.parseInt(dbg) && !processedBlocks.contains(block))
        {
            new Exception("markProcessed " + block.getBytecodeOffset()).printStackTrace();
        }
        processedBlocks.add(block);
    }

    /**
     * @param block the block to test
     * @return true if the block has already been emitted
     */
    public boolean isProcessed(IRBlock block)
    {
        return processedBlocks.contains(block);
    }

    /**
     * Clears the emitted-block marks and their cached statements so a fresh recovery pass over the same method
     * starts clean.
     */
    public void resetProcessedBlocks()
    {
        processedBlocks.clear();
        blockStatements.clear();
        // A full re-pass re-recovers every block; names declared by a prior (possibly discarded) attempt
        // must be re-declarable or their stores come back as assignments without declarations.
        getExpressionContext().resetDeclaredVariablesToBaseline();
    }

    /**
     * Records the statements recovered for a block, replacing any earlier set.
     *
     * @param block the block
     * @param stmts its statements
     */
    public void setStatements(IRBlock block, List<Statement> stmts)
    {
        blockStatements.put(block, stmts);
    }

    /**
     * @param block the block to look up
     * @return its recovered statements, empty if none were recorded
     */
    public List<Statement> getStatements(IRBlock block)
    {
        return blockStatements.getOrDefault(block, Collections.emptyList());
    }

    /**
     * Records the structured shape a block was recovered as.
     *
     * @param block the block
     * @param region the region shape
     */
    public void setRegion(IRBlock block, StructuredRegion region)
    {
        blockToRegion.put(block, region);
    }

    /**
     * @param block the block to look up
     * @return its recovered region shape, or null if unassigned
     */
    public StructuredRegion getRegion(IRBlock block)
    {
        return blockToRegion.get(block);
    }

    /**
     * Returns a block's break/continue label, minting "labelN" on first use.
     *
     * @param block the block to label
     * @return the label name
     */
    public String getOrCreateLabel(IRBlock block)
    {
        return blockLabels.computeIfAbsent(block, b -> "label" + (labelCounter++));
    }

    /**
     * @param block the block to test
     * @return true if a label has already been created for it
     */
    public boolean hasLabel(IRBlock block)
    {
        return blockLabels.containsKey(block);
    }

    /**
     * @param block the block to look up
     * @return its label, or null if none was created
     */
    public String getLabel(IRBlock block)
    {
        return blockLabels.get(block);
    }

    /**
     * An enclosing loop: its header (label anchor), continue-target (latch/increment) and exit block.
     */
    public static final class LoopFrame
    {
        final IRBlock header;
        final IRBlock continueTarget;
        final IRBlock exit;
        final IRBlock latch;
        final int depth;
        LoopFrame(IRBlock header, IRBlock continueTarget, IRBlock exit, IRBlock latch, int depth)
        {
            this.header = header;
            this.continueTarget = continueTarget;
            this.exit = exit;
            this.latch = latch;
            this.depth = depth;
        }
    }

    /**
     * An enclosing {@code switch}.
     */
    public static final class SwitchFrame
    {
        final IRBlock header;
        final IRBlock merge;
        final Set<IRBlock> caseHeaders;
        final int depth;
        SwitchFrame(IRBlock header, IRBlock merge, Set<IRBlock> caseHeaders, int depth)
        {
            this.header = header;
            this.merge = merge;
            this.caseHeaders = caseHeaders;
            this.depth = depth;
        }
    }

    /**
     * Which loop boundary an edge crosses.
     */
    public enum JumpKind { BREAK, CONTINUE }

    /**
     * A break/continue jump.
     */
    public static final class LoopJump
    {
        public final JumpKind kind;
        public final IRBlock loopHeader;
        LoopJump(JumpKind kind, IRBlock loopHeader)
        {
            this.kind = kind;
            this.loopHeader = loopHeader;
        }
    }

    /**
     * How an edge leaves a switch case - out at the merge, or on to the next case.
     */
    public enum SwitchJumpKind { BREAK_SWITCH, FALL_THROUGH }

    /**
     * A jump within a switch: leaving it at its merge, or falling through to a sibling {@code caseHeader}.
     */
    public static final class SwitchJump
    {
        public final SwitchJumpKind kind;
        public final IRBlock caseHeader;
        SwitchJump(SwitchJumpKind kind, IRBlock caseHeader)
        {
            this.kind = kind;
            this.caseHeader = caseHeader;
        }
    }

    private int scopeDepth()
    {
        return loopStack.size() + switchStack.size();
    }

    /**
     * Enters a loop scope so edges to its exit or continue-target classify as
     * break or continue, recording the current scope depth for labeling.
     *
     * @param header the loop header, used as the label anchor
     * @param continueTarget where a continue jumps to
     * @param exit where a break jumps to
     */
    public void pushLoop(IRBlock header, IRBlock continueTarget, IRBlock exit)
    {
        loopStack.push(new LoopFrame(header, continueTarget, exit, null, scopeDepth()));
    }

    /**
     * As {@link #pushLoop(IRBlock, IRBlock, IRBlock)} but records the loop's {@code for}-update latch.
     *
     * @param header the loop header, used as the label anchor
     * @param continueTarget where a continue jumps to
     * @param exit where a break jumps to
     * @param latch the update block that back-edges to the header
     */
    public void pushLoop(IRBlock header, IRBlock continueTarget, IRBlock exit, IRBlock latch)
    {
        loopStack.push(new LoopFrame(header, continueTarget, exit, latch, scopeDepth()));
    }

    /**
     * Pops the innermost loop frame, if any.
     */
    public void popLoop()
    {
        if (!loopStack.isEmpty())
        {
            loopStack.pop();
        }
    }

    /**
     * Enters a switch scope so edges to its merge or sibling cases classify as
     * break or fall-through.
     *
     * @param header the switch header block
     * @param merge where control leaves the switch
     * @param caseHeaders the case entry blocks
     */
    public void pushSwitch(IRBlock header, IRBlock merge, Set<IRBlock> caseHeaders)
    {
        switchStack.push(new SwitchFrame(header, merge, caseHeaders, scopeDepth()));
    }

    /**
     * Pops the innermost switch frame, if any.
     */
    public void popSwitch()
    {
        if (!switchStack.isEmpty())
        {
            switchStack.pop();
        }
    }

    /**
     * @return the exit, that is the break target, of the innermost enclosing loop, or null when
     *         no loop encloses
     */
    public IRBlock innermostLoopExit()
    {
        LoopFrame f = loopStack.peek();
        return f == null ? null : f.exit;
    }

    /**
     * @return the {@code for}-update latch of the innermost enclosing loop, or null when none
     *         encloses or it is not a counted loop
     */
    public IRBlock innermostLoopLatch()
    {
        LoopFrame f = loopStack.peek();
        return f == null ? null : f.latch;
    }

    /**
     * @return the merge of the innermost enclosing switch, or null when no switch encloses
     */
    public IRBlock innermostSwitchMerge()
    {
        SwitchFrame f = switchStack.peek();
        return f == null ? null : f.merge;
    }

    /**
     * The boundaries - case headers and merge - of every currently enclosing {@code switch}.
     *
     * @return the case headers and merges of every enclosing switch
     */
    public Set<IRBlock> switchBoundaries()
    {
        Set<IRBlock> boundaries = new HashSet<>();
        for (SwitchFrame f : switchStack)
        {
            if (f.merge != null)
            {
                boundaries.add(f.merge);
            }
            boundaries.addAll(f.caseHeaders);
        }
        return boundaries;
    }

    /**
     * True when {@code block} lies in a case body of the innermost enclosing switch (is dominated by one of its
     * case headers).
     *
     * @param block the block to locate
     * @return true if a case header of the innermost switch dominates it
     */
    public boolean inInnermostSwitchCase(IRBlock block)
    {
        SwitchFrame f = switchStack.peek();
        if (f == null)
        {
            return false;
        }
        for (IRBlock h : f.caseHeaders)
        {
            if (dominatorTree.dominates(h, block))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Classifies a control-flow edge into {@code target}.
     *
     * @param target the edge destination
     * @return the loop jump and its label anchor, or null when the target is not a loop boundary
     */
    public LoopJump classifyLoopJump(IRBlock target)
    {
        int innermostBreak = scopeDepth() - 1;
        int innermostLoop = loopStack.isEmpty() ? -1 : loopStack.peek().depth;
        for (LoopFrame f : loopStack)
        {
            if (target == f.exit)
            {
                return new LoopJump(JumpKind.BREAK, f.depth == innermostBreak ? null : f.header);
            }
            if (target == f.continueTarget)
            {
                return new LoopJump(JumpKind.CONTINUE, f.depth == innermostLoop ? null : f.header);
            }
        }
        return null;
    }

    /**
     * Classifies an edge into {@code target} relative to the innermost {@code switch}.
     *
     * @param target the edge destination
     * @return the switch jump, or null when the target is not an innermost-switch boundary
     */
    public SwitchJump classifySwitchJump(IRBlock target)
    {
        SwitchFrame f = switchStack.peek();
        if (f == null)
        {
            return null;
        }
        if (target == f.merge)
        {
            return new SwitchJump(SwitchJumpKind.BREAK_SWITCH, null);
        }
        if (f.caseHeaders.contains(target))
        {
            return new SwitchJump(SwitchJumpKind.FALL_THROUGH, target);
        }
        return null;
    }

    /**
     * Queues statements to be emitted before the next structured statement, as header block
     * instructions of an if or while need.
     *
     * @param stmts the statements to queue
     */
    public void addPendingStatements(List<Statement> stmts)
    {
        pendingStatements.addAll(stmts);
    }

    /**
     * Collects and clears any pending statements.
     * @return the pending statements, now cleared from context
     */
    public List<Statement> collectPendingStatements()
    {
        if (pendingStatements.isEmpty())
        {
            return Collections.emptyList();
        }
        List<Statement> result = new ArrayList<>(pendingStatements);
        pendingStatements.clear();
        return result;
    }

    /**
     * Enters a control structure whose recovery must stop at the given blocks.
     *
     * @param stopBlocks the blocks that bound this structure
     */
    public void pushStopBlocks(Set<IRBlock> stopBlocks)
    {
        stopBlocksStack.push(stopBlocks);
    }

    /**
     * Pops the current stop blocks from the stack.
     */
    public void popStopBlocks()
    {
        if (!stopBlocksStack.isEmpty())
        {
            stopBlocksStack.pop();
        }
    }

    /**
     * Flattens the stop blocks of every enclosing scope so an inner structure honours the
     * outer exit points too.
     *
     * @return the union of all pushed stop block sets
     */
    public Set<IRBlock> getAllStopBlocks()
    {
        Set<IRBlock> combined = new HashSet<>();
        for (Set<IRBlock> stopBlocks : stopBlocksStack)
        {
            combined.addAll(stopBlocks);
        }
        return combined;
    }

    /**
     * Enters a scope that suppresses instructions, as a for-loop does with the increment it
     * lifted into its update clause.
     *
     * @param instructions the instructions recovery must not emit inside this scope
     */
    public void pushSkipInstructions(Set<IRInstruction> instructions)
    {
        skipInstructionsStack.push(instructions);
    }

    /**
     * Pops the current skip instructions from the stack.
     */
    public void popSkipInstructions()
    {
        if (!skipInstructionsStack.isEmpty())
        {
            skipInstructionsStack.pop();
        }
    }

    /**
     * Tests an instruction against the permanent for-loop init claims and every pushed skip set.
     *
     * @param instruction the instruction to test
     * @return true if recovery should not emit it
     */
    public boolean shouldSkipInstruction(IRInstruction instruction)
    {
        if (forLoopInitInstructions.contains(instruction))
        {
            return true;
        }
        for (Set<IRInstruction> skipInstructions : skipInstructionsStack)
        {
            if (skipInstructions.contains(instruction))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Claims an instruction as a for-loop initializer, permanently skipping it in predecessor
     * blocks because the loop inlines it into its init clause.
     *
     * @param instruction the initializing instruction
     */
    public void markAsForLoopInit(IRInstruction instruction)
    {
        forLoopInitInstructions.add(instruction);
    }

    /**
     * @param instruction the instruction to test
     * @return true if a for-loop has claimed it as an initializer
     */
    public boolean isForLoopInit(IRInstruction instruction)
    {
        return forLoopInitInstructions.contains(instruction);
    }

    /**
     * Records a local slot as a for-loop induction variable so its PHI declaration is skipped -
     * the variable is declared in the loop's init instead.
     *
     * @param localIndex the local slot
     */
    public void markAsForLoopInductionLocal(int localIndex)
    {
        forLoopInductionLocalIndices.add(localIndex);
    }

    /**
     * @param localIndex the local slot to test
     * @return true if the slot holds a for-loop induction variable
     */
    public boolean isForLoopInductionLocal(int localIndex)
    {
        return forLoopInductionLocalIndices.contains(localIndex);
    }

    /**
     * Records a PHI as carrying a for-loop induction variable so its declaration is not
     * emitted early.
     *
     * @param phiResult the PHI result value
     */
    public void markAsForLoopInductionPhi(SSAValue phiResult)
    {
        forLoopInductionPhis.add(phiResult);
    }

    /**
     * @param phiResult the PHI result to test
     * @return true if the PHI carries a for-loop induction variable
     */
    public boolean isForLoopInductionPhi(SSAValue phiResult)
    {
        return forLoopInductionPhis.contains(phiResult);
    }

    /**
     * Records a block as a for-loop header, which scopes the induction local checks.
     *
     * @param block the header block
     */
    public void markAsForLoopHeader(IRBlock block)
    {
        forLoopHeaderBlocks.add(block);
    }

    /**
     * @param block the block to test
     * @return true if the block was marked as a for-loop header
     */
    public boolean isForLoopHeader(IRBlock block)
    {
        return forLoopHeaderBlocks.contains(block);
    }

    /**
     * Enters a scope in which the given values are proven false or zero, as the then-branch of
     * an inverted condition is.
     *
     * @param values the SSA values pinned to false or zero
     */
    public void pushKnownFalseValues(Set<SSAValue> values)
    {
        knownFalseValuesStack.push(values);
    }

    /**
     * Pops the current known false values from the stack.
     */
    public void popKnownFalseValues()
    {
        if (!knownFalseValuesStack.isEmpty())
        {
            knownFalseValuesStack.pop();
        }
    }

    /**
     * Searches every enclosing scope for a value pinned to false or zero.
     *
     * @param value the SSA value to test
     * @return true if some enclosing scope proved it false or zero
     */
    public boolean isKnownFalse(SSAValue value)
    {
        for (Set<SSAValue> falseValues : knownFalseValuesStack)
        {
            if (falseValues.contains(value))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Enters a scope in which the given fields are proven false or zero, the field-level
     * counterpart of {@link #pushKnownFalseValues}.
     *
     * @param fields the owner and name pairs pinned to false or zero
     */
    public void pushKnownFalseFields(Set<FieldKey> fields)
    {
        knownFalseFieldsStack.push(fields);
    }

    /**
     * Pops the current known false fields from the stack.
     */
    public void popKnownFalseFields()
    {
        if (!knownFalseFieldsStack.isEmpty())
        {
            knownFalseFieldsStack.pop();
        }
    }

    /**
     * Searches every enclosing scope for a field pinned to false or zero.
     *
     * @param owner the internal name of the class declaring the field
     * @param fieldName the field name
     * @return true if some enclosing scope proved the field false or zero
     */
    public boolean isFieldKnownFalse(String owner, String fieldName)
    {
        FieldKey key = new FieldKey(owner, fieldName);
        for (Set<FieldKey> falseFields : knownFalseFieldsStack)
        {
            if (falseFields.contains(key))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Represents a field by its owner class and field name.
     */
    public static class FieldKey
    {
        private final String owner;
        private final String fieldName;

        public FieldKey(String owner, String fieldName)
        {
            this.owner = owner;
            this.fieldName = fieldName;
        }

        /**
         * @return the owner
         */
        public String getOwner()
        {
            return owner;
        }

        /**
         * @return the field name
         */
        public String getFieldName()
        {
            return fieldName;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FieldKey fieldKey = (FieldKey) o;
            return Objects.equals(owner, fieldKey.owner) &&
                   Objects.equals(fieldName, fieldKey.fieldName);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(owner, fieldName);
        }

        @Override
        public String toString()
        {
            return owner + "." + fieldName;
        }
    }

    /**
     * Represents a structured control flow region.
     */
    public enum StructuredRegion
    {
        /**
         * A conditional with one populated arm, the other edge running straight to the merge.
         */
        IF_THEN,
        /**
         * A conditional with both arms populated, reconverging at a merge block.
         */
        IF_THEN_ELSE,
        /**
         * A loop tested at its header, with no induction variable recognized to make it a for.
         */
        WHILE_LOOP,
        /**
         * A loop whose test sits at the latch, so the body always runs once.
         */
        DO_WHILE_LOOP,
        /**
         * A head-tested loop whose induction variable and increment block were both identified,
         * so it can print as a for statement.
         */
        FOR_LOOP,
        /**
         * A multi-way dispatch on one value, with an arm per case label and a default.
         */
        SWITCH,
        /**
         * A protected body with its handlers, whose edges come from the exception table rather
         * than from branches.
         */
        TRY_CATCH,
        /**
         * Straight-line code with no branching of its own; the fallback when no other shape fits.
         */
        SEQUENCE,
        /**
         * A region no source construct matches, such as a loop entered at more than one header.
         */
        IRREDUCIBLE,
        /**
         * A conditional whose taken arm exits early, so it reads as a guard rather than an
         * if/else with two live arms.
         */
        GUARD_CLAUSE
    }
}
