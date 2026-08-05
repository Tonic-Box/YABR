package com.tonic.analysis.source.recovery.rcs;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.ssa.cfg.IRBlock;

import java.util.List;
import java.util.Set;

/**
 * The narrow set of statement/expression recovery leaves the reaching-condition engine needs from the
 * host {@code StatementRecoverer}. Keeping it an interface lets the engine live in its own package and
 * be exercised in isolation, while the host retains ownership of expression recovery, naming, and the
 * shared processed-block bookkeeping in {@code ControlFlowContext}.
 */
public interface RegionRecoveryBridge
{

    /**
     * The straight-line statements of {@code block} (its terminator branch is not emitted here).
     *
     * @param block the block to recover
     * @return its straight-line statements
     */
    List<Statement> recoverSimpleBlock(IRBlock block);

    /**
     * The branch condition of {@code block}, negated when {@code negate} is set.
     *
     * @param block the block terminated by the branch
     * @param negate whether to recover the complement
     * @return the condition expression
     */
    Expression recoverCondition(IRBlock block, boolean negate);

    /**
     * True when recovering {@code block}'s branch condition would inline an allocation or call - a side
     * effect a shared-tail guard must not duplicate by re-emitting the condition. False for a condition
     * over named locals only, which re-emits freely.
     *
     * @param block the block whose branch condition is inspected
     * @return true when re-emitting the condition would repeat a side effect
     */
    boolean conditionInlinesSideEffect(IRBlock block);

    /**
     * True when recovering {@code block}'s branch condition inlines no operation that can throw (division,
     * field/array access or arraylength, checkcast, call/allocation). A condition over locals, parameters,
     * constants, and non-throwing arithmetic is exception-free. Lets the engine decide whether the condition
     * may be hoisted out of its short-circuit position into an unconditionally-evaluated temporary without
     * changing which inputs throw.
     *
     * @param block the block whose branch condition is inspected
     * @return true when the condition inlines nothing that can throw
     */
    boolean guardAtomExceptionFree(IRBlock block);

    /**
     * True when {@code block} may be duplicated - re-recovered once per reaching edge - without changing
     * semantics or perturbing the round trip. Requires that re-recovering it is byte-identical and repeats no
     * side effect: no field or array store (which duplication would perform twice), and no field load that is
     * clobbered before use (its recovery emits a declaration on the first pass and nothing after, so a second
     * pass would drop it). Local stores are permitted - a duplicated tail's locals are loop-carried and
     * declared once by the phi-declaration pass, so the assignments re-emit idempotently.
     *
     * @param block the block a region would re-recover per reaching edge
     * @return true when duplicating it is safe
     */
    boolean isDuplicationSafe(IRBlock block);

    /**
     * SSA-destruction copies realized when the edge {@code pred -> succ} is taken.
     *
     * @param pred the source block of the edge
     * @param succ the target block whose phis are lowered
     * @return the copies for that edge
     */
    List<Statement> lowerPhisOnEdge(IRBlock pred, IRBlock succ);

    /**
     * Copies for the operand-stack merge phis of {@code succ} whose incoming on this edge is produced by no
     * instruction in {@code pred}. The structured path materializes an arm's contribution while recovering the
     * instruction that computes it, so an arm carrying an already-computed value (a ternary arm that just
     * reloads a local) emits nothing and the merge reads a stale temporary.
     *
     * @param pred the source block of the edge
     * @param succ the merge block whose stack phis are lowered
     * @return the copies that arm owes the merge, empty when the arm already produced them
     */
    List<Statement> stackPhiCopiesOnEdge(IRBlock pred, IRBlock succ);

    List<Statement> lowerInductionPhiInitsOnEdge(IRBlock pred, IRBlock succ);

    /**
     * The still-unconsumed for-induction inits of {@code header}'s preheader: init stores the for-region
     * pre-pass marked for skipping that no {@code for}-init or phi copy will re-emit (the header carries no
     * phi for the slot - a handler-only loop). Recovered as declarations, consumed exactly once.
     *
     * @param header the loop header whose preheader holds the inits
     * @return the init declarations, empty when none remain
     */
    List<Statement> recoverUnconsumedForLoopInits(IRBlock header);

    /**
     * True when some block in {@code region} starts an exception handler the surrounding recovery has not
     * yet consumed - a nested try the engine must decline so the try/catch scaffolding recovers it.
     *
     * @param region the blocks under consideration
     * @return true when one of them starts an unconsumed handler
     */
    boolean regionContainsUnprocessedHandler(Set<IRBlock> region);

    /**
     * Records {@code block}'s recovered statements and marks it emitted so nothing re-emits it.
     *
     * @param block the block being consumed
     * @param statements the statements recovered for it
     */
    void markRegionBlockProcessed(IRBlock block, List<Statement> statements);

    /**
     * True once {@code block} has been emitted.
     *
     * @param block the block to test
     * @return true when it has already been emitted
     */
    boolean isRegionBlockProcessed(IRBlock block);

    /**
     * The statements of a processed RETURN block, for idempotent re-emission - a trailing return two paths
     * share is recovered once by the first path's pass; the other path re-emits the terminator instead of
     * silently falling off the end of the method. Empty for a block that is not a bare processed return.
     *
     * @param block the shared trailing block
     * @return its recovered return statements, empty when it is not a bare processed return
     */
    List<Statement> processedReturnStatements(IRBlock block);

    /**
     * If {@code branch} heads a value-producing ternary diamond - both arms produce a single value that
     * merges at a phi feeding an expression, e.g. {@code x > y ? x : y} - collapses it to a cached
     * {@code TernaryExpr} (inlined where the merge block consumes it) and marks the two arm blocks emitted,
     * then returns true. Returns false (touching nothing) when {@code branch} is not such a diamond, so the
     * caller structures it as ordinary control flow.
     *
     * @param branch the candidate diamond head
     * @return true when the diamond was collapsed and its arms marked emitted
     */
    boolean tryCollapseTernaryDiamond(IRBlock branch);





    /**
     * Decodes {@code switchBlock} into a structuring-ready {@link SwitchDescriptor} - selector, merge, ordered
     * cases and labels - without recovering case bodies or marking any block, so the reaching-condition engine
     * can structure the cases itself. Returns null for a switch shape the engine does not own natively (string,
     * pattern {@code typeSwitch}, or a synthesized comparison-chain switch), which then declines to the legacy walk.
     *
     * @param switchBlock the block terminated by the switch
     * @return the decoded descriptor, or null for a shape the engine does not own natively
     */
    SwitchDescriptor decodeSwitch(IRBlock switchBlock);

    /**
     * The switch header's own statements for emission before the {@code switch}: for an ordinary
     * switch the block's plain recovery, but for a desugared selector (a string switch's
     * hashCode/equals scaffold) only the user code BEFORE the dispatch scaffolding - and the
     * scaffold blocks are marked processed so nothing re-walks them.
     *
     * @param header the switch header block
     * @return the statements to emit before the switch
     */
    List<Statement> recoverSwitchHeaderStatements(IRBlock header);

    /**
     * True when {@code block} starts the protected range of an exception handler no recovery has consumed.
     *
     * @param block the candidate protected-range start
     * @return true when an unconsumed handler protects a range starting there
     */
    boolean startsUnprocessedHandler(IRBlock block);

    /**
     * Whether {@code block} is the entry of an exception handler the surrounding recovery has already
     * consumed - a retired copy-side guard catch or a de-duplicated finally's scaffolding. Such a block's
     * text is recovered inside the owning clause, so region machinery treats it like live handler code.
     *
     * @param block the candidate handler entry
     * @return true when it is a retired handler entry
     */
    boolean isRetiredHandlerBlock(IRBlock block);

    /**
     * Statically decodes the try starting at {@code block} into an opaque {@link TryNodeDescriptor} - the
     * blocks the try/catch recovery will consume and the single join it continues at - without recovering or
     * marking anything. Returns null for a shape the node model does not own (a nested unprocessed try in the
     * range, a catch with internal control flow, or an ambiguous join), which then declines.
     *
     * @param block the candidate try entry
     * @param regionStops blocks that bound the enclosing region's walk
     * @return the decoded node, or null for a shape the node model does not own
     */
    TryNodeDescriptor decodeTryNode(IRBlock block, Set<IRBlock> regionStops);

    /**
     * Recovers a straight terminal tail (single-successor blocks chaining into a return/throw) as fresh
     * statements, without marking the blocks processed: the engine inlines the tail once inside a region
     * whose flow converges on it, while the enclosing recovery still emits its own copy on the paths that
     * reach the tail from outside. Returns null when the shape is not such a tail.
     *
     * @param tail the first block of the candidate tail chain
     * @return the tail's statements, or null when the shape is not a straight terminal tail
     */
    List<Statement> recoverBoundaryTail(IRBlock tail);

    /**
     * Recovers the try node starting at {@code block} as one statement via the host's try/catch machinery,
     * marking its handler and blocks consumed. {@code alreadyEmitted} are the region blocks recovered before
     * the node, excluded from the try's own walk. Returns null when the machinery cannot recover the shape.
     *
     * @param block the try entry
     * @param node the descriptor decoded by {@link #decodeTryNode}
     * @param stopBlocks blocks the try's walk must not cross into
     * @param alreadyEmitted region blocks recovered before the node
     * @return the try statement, or null when the shape cannot be recovered
     */
    Statement recoverTryNode(IRBlock block, TryNodeDescriptor node, Set<IRBlock> stopBlocks,
                             Set<IRBlock> alreadyEmitted);

    /**
     * Whether a statement recovered by {@link #recoverTryNode} leaves no normal fall-through.
     *
     * @param recovered a statement produced by {@link #recoverTryNode}
     * @return true when every path out of it returns or throws
     */
    boolean recoveredTryTerminates(Statement recovered);

    /**
     * Signals that the host's try recovery produced nothing for a try node the engine had decoded -
     * a routing gap, since every region structures through the engine. Never returns normally; the
     * host raises a retired-route signal, which {@code MethodRecoverer} converts into the faithful
     * dispatch-loop re-recovery for a handler-free method.
     *
     * @param block the try entry whose recovery produced nothing
     */
    void unrecoveredTryNode(IRBlock block);
}
