package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
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
        IRREDUCIBLE
    }
}
