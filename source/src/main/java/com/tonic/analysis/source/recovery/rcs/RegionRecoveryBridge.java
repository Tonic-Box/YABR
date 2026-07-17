package com.tonic.analysis.source.recovery.rcs;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.ssa.cfg.IRBlock;

import java.util.List;

/**
 * The narrow set of statement/expression recovery leaves the reaching-condition engine needs from the
 * host {@code StatementRecoverer}. Keeping it an interface lets the engine live in its own package and
 * be exercised in isolation, while the host retains ownership of expression recovery, naming, and the
 * shared processed-block bookkeeping in {@code ControlFlowContext}.
 */
public interface RegionRecoveryBridge {

    /** The straight-line statements of {@code block} (its terminator branch is not emitted here). */
    List<Statement> recoverSimpleBlock(IRBlock block);

    /** The branch condition of {@code block}, negated when {@code negate} is set. */
    Expression recoverCondition(IRBlock block, boolean negate);

    /** SSA-destruction copies realized when the edge {@code pred -> succ} is taken. */
    List<Statement> lowerPhisOnEdge(IRBlock pred, IRBlock succ);

    /** Records {@code block}'s recovered statements and marks it emitted so nothing re-emits it. */
    void markRegionBlockProcessed(IRBlock block, List<Statement> statements);

    /** True once {@code block} has been emitted. */
    boolean isRegionBlockProcessed(IRBlock block);

    /**
     * If {@code branch} heads a value-producing ternary diamond - both arms produce a single value that
     * merges at a phi feeding an expression, e.g. {@code x > y ? x : y} - collapses it to a cached
     * {@code TernaryExpr} (inlined where the merge block consumes it) and marks the two arm blocks emitted,
     * then returns true. Returns false (touching nothing) when {@code branch} is not such a diamond, so the
     * caller structures it as ordinary control flow.
     */
    boolean tryCollapseTernaryDiamond(IRBlock branch);
}
