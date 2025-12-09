package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
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
