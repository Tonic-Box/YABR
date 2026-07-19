package com.tonic.analysis.source.recovery;

import com.tonic.util.Logger;
import com.tonic.analysis.source.ast.Locations;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.source.recovery.ControlFlowContext.FieldKey;
import com.tonic.analysis.source.recovery.StructuralAnalyzer.RegionInfo;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.ir.BinaryOp;
import com.tonic.analysis.ssa.ir.CompareOp;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.NullConstant;
import com.tonic.analysis.ssa.value.StringConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Recovers Statement AST nodes from IR blocks using structural analysis.
 */
public class StatementRecoverer implements com.tonic.analysis.source.recovery.rcs.RegionRecoveryBridge {

    private final ControlFlowContext context;
    private final StructuralAnalyzer analyzer;
    private final ExpressionRecoverer exprRecoverer;
    private final TypeRecoverer typeRecoverer;

    /**
     * The reaching-condition structurer. It is offered each region first and falls back to the legacy walk
     * by returning null for shapes it does not yet handle (exception scaffolding, irreducible flow, and the
     * few regions it declines), which the legacy walk still recovers until it is retired.
     */
    private final com.tonic.analysis.source.recovery.rcs.ReachingConditionStructurer rcsStructurer;

    public StatementRecoverer(ControlFlowContext context, StructuralAnalyzer analyzer,
                              ExpressionRecoverer exprRecoverer) {
        this.context = context;
        this.analyzer = analyzer;
        this.exprRecoverer = exprRecoverer;
        this.typeRecoverer = new TypeRecoverer();
        this.rcsStructurer = new com.tonic.analysis.source.recovery.rcs.ReachingConditionStructurer(this, context);

        // Pre-declare parameters so stores to them become assignments, not declarations
        preDeclareParameters();
    }

    @Override
    public void markRegionBlockProcessed(IRBlock block, List<Statement> statements) {
        context.setStatements(block, statements);
        context.markProcessed(block);
    }

    @Override
    public boolean isRegionBlockProcessed(IRBlock block) {
        return context.isProcessed(block);
    }

    @Override
    public boolean tryCollapseTernaryDiamond(IRBlock branch) {
        IRInstruction term = branch.getTerminator();
        if (!(term instanceof BranchInstruction)) {
            return false;
        }
        BranchInstruction br = (BranchInstruction) term;
        IRBlock thenBlock = br.getTrueTarget();
        IRBlock elseBlock = br.getFalseTarget();
        if (thenBlock == null || elseBlock == null || thenBlock == elseBlock) {
            return false;
        }
        // Both arms must be reached only from this branch and flow to one common merge block.
        if (!isSolePredecessor(branch, thenBlock) || !isSolePredecessor(branch, elseBlock)) {
            return false;
        }
        IRBlock merge = soleSuccessor(thenBlock);
        if (merge == null || merge != soleSuccessor(elseBlock)) {
            return false;
        }
        PhiInstruction ternaryPhi = findTernaryPhi(thenBlock, elseBlock, merge);
        if (ternaryPhi == null) {
            return false;
        }
        // Only collapse a stack phi consumed directly by an expression (e.g. a call argument or a bare return
        // of the phi). If an arm stores its value into a local slot, the value flows through a variable that the
        // merge reads back: the arms structure into an `if/else` the AST pipeline folds to a ternary
        // (tryConvertIfElseToAssignment). Collapsing here would cache the ternary for the phi while the merge
        // reads the local, orphaning the arms' work and forcing the whole method into a dispatch loop. A phi
        // feeding another phi (a nested merge) likewise cannot be consumed here.
        if (armStoresToLocal(thenBlock) || armStoresToLocal(elseBlock)
                || hasStoreLocalUse(ternaryPhi.getResult()) || getPhiUsingValue(ternaryPhi.getResult()) != null) {
            return false;
        }
        Expression condition = recoverCondition(branch, false);
        collapseToTernaryPhiExpression(condition, ternaryPhi, thenBlock, elseBlock);
        context.markProcessed(thenBlock);
        context.markProcessed(elseBlock);
        return true;
    }

    /** True when {@code block} stores a value into a local slot - the diamond value flows through a variable. */
    private boolean armStoresToLocal(IRBlock block) {
        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof StoreLocalInstruction) {
                return true;
            }
        }
        return false;
    }

    private boolean isSolePredecessor(IRBlock pred, IRBlock block) {
        Set<IRBlock> preds = block.getPredecessors();
        return preds.size() == 1 && preds.contains(pred);
    }

    private IRBlock soleSuccessor(IRBlock block) {
        Set<IRBlock> succs = block.getSuccessors();
        return succs.size() == 1 ? succs.iterator().next() : null;
    }

    @Override
    public boolean canStructureSwitchRegion(IRBlock switchBlock) {
        if (!(switchBlock.getTerminator() instanceof SwitchInstruction)) {
            return false;
        }
        RegionInfo info = analyzer.getRegionInfo(switchBlock);
        if (info == null || info.getType() != ControlFlowContext.StructuredRegion.SWITCH) {
            return false;
        }
        // A pattern switch dispatches on `typeSwitch(selector, index)`; the pattern/switch-expression
        // reconstructors fold that (and its null-check and index scaffolding) into `switch (selector)`. The
        // native path would recover the raw dispatch and strand the scaffolding, so leave it to the legacy walk.
        Value key = ((SwitchInstruction) switchBlock.getTerminator()).getKey();
        if (key instanceof SSAValue) {
            IRInstruction def = ((SSAValue) key).getDefinition();
            if (def instanceof InvokeInstruction && ((InvokeInstruction) def).isDynamic()
                    && "typeSwitch".equals(((InvokeInstruction) def).getName())) {
                return false;
            }
        }
        // A switch inside a loop shares the loop's continue target as its merge and its induction phis flow
        // through the case bodies; structuring it as an opaque unit misplaces those. Leave it to the legacy
        // walk, which recovers the loop and switch together.
        if (context.getLoopAnalysis() != null && context.getLoopAnalysis().isInLoop(switchBlock)) {
            return false;
        }
        // A string switch is a hash switch feeding an index switch, recovered with its own scaffolding
        // bookkeeping; leave that shape to the legacy walk.
        return detectStringSwitch(switchBlock) == null;
    }

    @Override
    public IRBlock switchMergeBlock(IRBlock switchBlock) {
        RegionInfo info = analyzer.getRegionInfo(switchBlock);
        return info == null ? null : findSwitchMerge(info);
    }

    @Override
    public List<Statement> recoverSwitchRegion(IRBlock switchBlock) {
        RegionInfo info = analyzer.getRegionInfo(switchBlock);
        Statement sw = recoverSwitch(switchBlock, info);
        List<Statement> out = new ArrayList<>();
        // recoverSwitch wraps header statements and the switch in a block; flatten so they stay in the
        // enclosing sequence rather than nesting in a bare `{ }`.
        if (sw instanceof BlockStmt) {
            out.addAll(((BlockStmt) sw).getStatements());
        } else {
            out.add(sw);
        }
        return out;
    }

    /**
     * Pre-declares parameter names so that stores to parameter slots
     * generate assignment statements instead of variable declarations.
     */
    private void preDeclareParameters() {
        IRMethod method = context.getIrMethod();
        RecoveryContext ctx = context.getExpressionContext();
        List<SSAValue> params = method.getParameters();
        // Skip the receiver (slot 0 of an instance method); declare each parameter under the name it was
        // actually given - its real LocalVariableTable name when present, else the synthetic "argN" - so a
        // store back to a parameter slot recovers as an assignment, never a self-copy declaration.
        int start = method.isStatic() ? 0 : 1;
        for (int i = start; i < params.size(); i++) {
            String paramName = ctx.getVariableName(params.get(i));
            if (paramName != null) {
                ctx.markDeclared(paramName);
            }
        }
    }

    private void registerPendingNewInstructions(IRMethod method) {
        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof NewInstruction) {
                    NewInstruction newInstr = (NewInstruction) instr;
                    if (newInstr.getResult() != null) {
                        context.getExpressionContext().registerPendingNew(
                            newInstr.getResult(), newInstr.getClassName());
                    }
                }
            }
        }
    }

    /**
     * Recovers statements for the entire method.
     */
    public BlockStmt recoverMethod() {
        IRMethod method = context.getIrMethod();
        IRBlock entry = method.getEntryBlock();

        if (entry == null) {
            return new BlockStmt(Collections.emptyList());
        }

        List<Statement> statements = new ArrayList<>();

        detectSelfStorePhis(method);

        collectForLoopInitInstructions();

        registerPendingNewInstructions(method);
        emitPhiDeclarations(method, statements);
        splitClobberedIncrementReads(method);

        List<ExceptionHandler> handlers = method.getExceptionHandlers();
        if (handlers != null && !handlers.isEmpty()) {
            List<Statement> twr = recoverTryWithResources(entry, handlers);
            statements.addAll(Objects.requireNonNullElseGet(twr, () -> recoverWithExceptionHandling(entry, handlers)));
        } else {
            statements.addAll(recoverBlockSequence(entry, new HashSet<>()));
        }

        removeInlineFinallyDuplicates(statements);
        removeOrphanFinallyRethrows(statements);

        return new BlockStmt(statements);
    }

    private void removeOrphanFinallyRethrows(List<Statement> statements) {
        Set<String> declaredVars = collectDeclaredVariables(statements);
        removeOrphanThrowsRecursive(statements, declaredVars);
    }

    private void removeOrphanThrowsRecursive(List<Statement> statements, Set<String> declaredVars) {
        statements.removeIf(stmt -> isOrphanThrow(stmt, declaredVars));
        for (Statement stmt : statements) {
            cleanupOrphanThrowsInStatement(stmt, declaredVars);
        }
    }

    private boolean isOrphanThrow(Statement stmt, Set<String> declaredVars) {
        if (stmt instanceof ThrowStmt) {
            ThrowStmt throwStmt = (ThrowStmt) stmt;
            Expression exception = throwStmt.getException();
            if (exception instanceof VarRefExpr) {
                String varName = ((VarRefExpr) exception).getName();
                return !declaredVars.contains(varName);
            }
        }
        return false;
    }

    private void cleanupOrphanThrowsInStatement(Statement stmt, Set<String> declaredVars) {
        if (stmt instanceof BlockStmt) {
            BlockStmt block = (BlockStmt) stmt;
            removeOrphanThrowsRecursive(block.getStatements(), declaredVars);
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            if (tryCatch.getTryBlock() instanceof BlockStmt) {
                BlockStmt tryBlock = (BlockStmt) tryCatch.getTryBlock();
                removeOrphanThrowsRecursive(tryBlock.getStatements(), declaredVars);
            }
            for (CatchClause clause : tryCatch.getCatches()) {
                Set<String> catchVars = new HashSet<>(declaredVars);
                catchVars.add(clause.variableName());
                if (clause.body() instanceof BlockStmt) {
                    removeOrphanThrowsRecursive(((BlockStmt) clause.body()).getStatements(), catchVars);
                }
            }
            if (tryCatch.getFinallyBlock() instanceof BlockStmt) {
                removeOrphanThrowsRecursive(((BlockStmt) tryCatch.getFinallyBlock()).getStatements(), declaredVars);
            }
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            cleanupOrphanThrowsInStatement(ifStmt.getThenBranch(), declaredVars);
            if (ifStmt.getElseBranch() != null) {
                cleanupOrphanThrowsInStatement(ifStmt.getElseBranch(), declaredVars);
            }
        } else if (stmt instanceof WhileStmt) {
            cleanupOrphanThrowsInStatement(((WhileStmt) stmt).getBody(), declaredVars);
        } else if (stmt instanceof DoWhileStmt) {
            cleanupOrphanThrowsInStatement(((DoWhileStmt) stmt).getBody(), declaredVars);
        } else if (stmt instanceof ForStmt) {
            cleanupOrphanThrowsInStatement(((ForStmt) stmt).getBody(), declaredVars);
        }
    }

    private Set<String> collectDeclaredVariables(List<Statement> statements) {
        Set<String> declared = new HashSet<>();
        for (Statement stmt : statements) {
            collectDeclaredVarsRecursive(stmt, declared);
        }
        return declared;
    }

    private void collectDeclaredVarsRecursive(Statement stmt, Set<String> declared) {
        if (stmt instanceof VarDeclStmt) {
            declared.add(((VarDeclStmt) stmt).getName());
        } else if (stmt instanceof BlockStmt) {
            for (Statement s : ((BlockStmt) stmt).getStatements()) {
                collectDeclaredVarsRecursive(s, declared);
            }
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            collectDeclaredVarsRecursive(tryCatch.getTryBlock(), declared);
            for (CatchClause clause : tryCatch.getCatches()) {
                declared.add(clause.variableName());
                collectDeclaredVarsRecursive(clause.body(), declared);
            }
            if (tryCatch.getFinallyBlock() != null) {
                collectDeclaredVarsRecursive(tryCatch.getFinallyBlock(), declared);
            }
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            collectDeclaredVarsRecursive(ifStmt.getThenBranch(), declared);
            if (ifStmt.getElseBranch() != null) {
                collectDeclaredVarsRecursive(ifStmt.getElseBranch(), declared);
            }
        } else if (stmt instanceof WhileStmt) {
            collectDeclaredVarsRecursive(((WhileStmt) stmt).getBody(), declared);
        } else if (stmt instanceof DoWhileStmt) {
            collectDeclaredVarsRecursive(((DoWhileStmt) stmt).getBody(), declared);
        } else if (stmt instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) stmt;
            for (Statement initStmt : forStmt.getInit()) {
                collectDeclaredVarsRecursive(initStmt, declared);
            }
            collectDeclaredVarsRecursive(forStmt.getBody(), declared);
        }
    }

    /**
     * Removes duplicate inline finally code that appears after try-catch-finally statements.
     * The JVM inlines finally code for the normal execution path, creating duplicates.
     */
    private void removeInlineFinallyDuplicates(List<Statement> statements) {
        for (int i = 0; i < statements.size(); i++) {
            Statement stmt = statements.get(i);
            if (stmt instanceof TryCatchStmt) {
                TryCatchStmt tryCatch = (TryCatchStmt) stmt;
                if (tryCatch.hasFinally() && tryCatch.getFinallyBlock() instanceof BlockStmt) {
                    List<Statement> finallyStmts = ((BlockStmt) tryCatch.getFinallyBlock()).getStatements();
                    if (!finallyStmts.isEmpty()) {
                        int removed = removeMatchingStatements(statements, i + 1, finallyStmts);
                        i -= removed;
                    }
                }
            }
        }
    }

    /**
     * Removes statements from the list that match the given pattern statements.
     * Returns the number of statements removed.
     */
    private int removeMatchingStatements(List<Statement> statements, int startIndex, List<Statement> pattern) {
        if (startIndex >= statements.size() || pattern.isEmpty()) {
            return 0;
        }

        int matchCount = 0;
        for (int i = 0; i < pattern.size() && startIndex + i < statements.size(); i++) {
            Statement actual = statements.get(startIndex + i);
            Statement expected = pattern.get(i);
            if (statementsMatch(actual, expected)) {
                matchCount++;
            } else {
                break;
            }
        }

        if (matchCount == pattern.size()) {
            for (int i = 0; i < matchCount; i++) {
                statements.remove(startIndex);
            }
            return matchCount;
        }
        return 0;
    }

    /**
     * Checks if two statements are semantically equivalent for finally duplicate detection.
     */
    private boolean statementsMatch(Statement a, Statement b) {
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;

        if (a instanceof ExprStmt && b instanceof ExprStmt) {
            return expressionsMatch(((ExprStmt) a).getExpression(), ((ExprStmt) b).getExpression());
        }
        if (a instanceof VarDeclStmt && b instanceof VarDeclStmt) {
            VarDeclStmt va = (VarDeclStmt) a;
            VarDeclStmt vb = (VarDeclStmt) b;
            if (va.getInitializer() == null && vb.getInitializer() == null) return true;
            if (va.getInitializer() == null || vb.getInitializer() == null) return false;
            return expressionsMatch(va.getInitializer(), vb.getInitializer());
        }
        if (a instanceof ReturnStmt && b instanceof ReturnStmt) {
            Expression ea = ((ReturnStmt) a).getValue();
            Expression eb = ((ReturnStmt) b).getValue();
            if (ea == null && eb == null) return true;
            if (ea == null || eb == null) return false;
            return expressionsMatch(ea, eb);
        }

        return a.toString().equals(b.toString());
    }

    /**
     * Checks if two expressions are semantically equivalent for finally duplicate detection.
     */
    private boolean expressionsMatch(Expression a, Expression b) {
        if (a == null || b == null) return a == b;
        if (a.getClass() != b.getClass()) return false;

        if (a instanceof BinaryExpr && b instanceof BinaryExpr) {
            BinaryExpr ba = (BinaryExpr) a;
            BinaryExpr bb = (BinaryExpr) b;
            return ba.getOperator() == bb.getOperator()
                && expressionsMatch(ba.getLeft(), bb.getLeft())
                && expressionsMatch(ba.getRight(), bb.getRight());
        }
        if (a instanceof VarRefExpr && b instanceof VarRefExpr) {
            return ((VarRefExpr) a).getName().equals(((VarRefExpr) b).getName());
        }
        if (a instanceof LiteralExpr && b instanceof LiteralExpr) {
            Object va = ((LiteralExpr) a).getValue();
            Object vb = ((LiteralExpr) b).getValue();
            return Objects.equals(va, vb);
        }

        return a.toString().equals(b.toString());
    }

    /**
     * Recovers statements with exception handling structure.
     * Handles both simple and nested try-catch regions.
     */
    private List<Statement> recoverWithExceptionHandling(IRBlock entry, List<ExceptionHandler> handlers) {
        List<Statement> result = new ArrayList<>();

        Set<IRBlock> handlerBlocks = new HashSet<>();
        for (ExceptionHandler handler : handlers) {
            if (handler.getHandlerBlock() != null) {
                handlerBlocks.add(handler.getHandlerBlock());
                collectReachableBlocks(handler.getHandlerBlock(), handlerBlocks);
            }
        }

        List<ExceptionHandler> mergedHandlers = mergeHandlersWithSameTarget(handlers);

        ExceptionHandler outerHandler = findOutermostHandler(entry, mergedHandlers);

        if (outerHandler != null) {
            List<ExceptionHandler> outerHandlers = new ArrayList<>();
            List<ExceptionHandler> innerHandlers = new ArrayList<>();

            for (ExceptionHandler h : mergedHandlers) {
                // A handler over the identical try range is a sibling catch clause on the same try
                // (try { } catch (A) { } catch (B) { }), not a nested one. Only a handler over a strict
                // sub-range is genuinely nested; classifying a sibling as inner would rebuild it as a
                // nested try and drop the shared try body.
                if (h.getHandlerBlock() == outerHandler.getHandlerBlock()
                        || sameTryRange(h, outerHandler)) {
                    outerHandlers.add(h);
                } else {
                    innerHandlers.add(h);
                }
            }
            Set<IRBlock> outerHandlerBlocks = new HashSet<>();
            for (ExceptionHandler h : outerHandlers) {
                if (h.getHandlerBlock() != null) {
                    outerHandlerBlocks.add(h.getHandlerBlock());
                }
            }

            Set<IRBlock> stopBlocks = new HashSet<>(handlerBlocks);

            IRMethod irMethod = context.getIrMethod();
            if (outerHandler.getTryEnd() != null) {
                int tryEndOffset = outerHandler.getTryEnd().getBytecodeOffset();
                for (IRBlock block : irMethod.getBlocks()) {
                    if (block.getBytecodeOffset() >= tryEndOffset) {
                        stopBlocks.add(block);
                    }
                }
            }

            processedTryHandlers.addAll(outerHandlers);
            // Mark the handler block (stable across the merge that may have replaced the handler objects) so
            // the try body's own block-sequence recovery does not rebuild the same region as a nested
            // try/catch, which would duplicate the clause (and drop multi-catch types via the merge).
            processedHandlerBlocks.addAll(outerHandlerBlocks);

            IRBlock tryStart = outerHandler.getTryStart();
            List<Statement> preTryStmts = new ArrayList<>();
            if (tryStart != null && tryStart != entry) {
                Set<IRBlock> preTryStop = new HashSet<>(stopBlocks);
                preTryStop.add(tryStart);
                preTryStmts = recoverRegionHandoff(entry, preTryStop);
            }

            IRBlock startBlock = (tryStart != null) ? tryStart : entry;
            List<Statement> tryStmts;
            if (!innerHandlers.isEmpty()) {
                tryStmts = recoverWithNestedHandlers(startBlock, innerHandlers, stopBlocks);
            } else {
                tryStmts = recoverRegionHandoff(startBlock, stopBlocks);
            }
            BlockStmt tryBlock = new BlockStmt(tryStmts);
            result.addAll(preTryStmts);

            // Build clauses from the original (pre-merge) handlers sharing the outer handler block, so a
            // multi-catch's several exception-table entries coalesce into one `catch (A | B e)` clause.
            List<ExceptionHandler> outerRegionHandlers = new ArrayList<>();
            for (ExceptionHandler h : handlers) {
                if (outerHandlerBlocks.contains(h.getHandlerBlock())) {
                    outerRegionHandlers.add(h);
                }
            }
            List<CatchClause> catchClauses = buildCatchClauses(outerRegionHandlers);

            if (!catchClauses.isEmpty()) {
                BlockStmt finallyBlock = null;
                List<CatchClause> filteredCatches = new ArrayList<>();
                Set<String> finallyExceptionVars = new HashSet<>();

                for (CatchClause clause : catchClauses) {
                    if (isFinallyRethrowPattern(clause)) {
                        finallyBlock = extractFinallyBody(clause);
                        finallyExceptionVars.add(clause.variableName());
                    } else {
                        filteredCatches.add(clause);
                    }
                }

                if (!finallyExceptionVars.isEmpty()) {
                    tryStmts = filterOrphanFinallyThrows(tryStmts, finallyExceptionVars);

                    List<Statement> finallyStmts = finallyBlock.getStatements();
                    tryStmts = filterInlinedFinallyFromTryStatements(tryStmts, finallyStmts);

                    // The blocks between the protected region's end and the handler hold the finally inlined on
                    // the normal exit path plus the region's real continuation (e.g. its return). Recover them
                    // with the inlined-finally copies filtered out so the genuine continuation joins the try body
                    // instead of being dropped (which would leave an empty try when the finally has control flow).
                    if (outerHandler.getTryEnd() != null && outerHandler.getHandlerBlock() != null) {
                        List<Statement> gapStmts = recoverFinallyGap(
                            outerHandler.getTryEnd(), outerHandler.getHandlerBlock(), finallyBlock, finallyExceptionVars);

                        if (!gapStmts.isEmpty() && !isTerminatingBlock(new BlockStmt(tryStmts))) {
                            tryStmts = new ArrayList<>(tryStmts);
                            tryStmts.addAll(gapStmts);
                        }
                    }

                    tryBlock = new BlockStmt(tryStmts);
                }

                Value syncLock = filteredCatches.isEmpty() ? detectSynchronizedLock(outerHandler) : null;
                Statement region;
                if (syncLock != null) {
                    SynchronizedStmt sync = new SynchronizedStmt(exprRecoverer.recoverOperand(syncLock), tryBlock);
                    stampFromBody(sync, tryBlock);
                    region = sync;
                } else {
                    TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, filteredCatches, finallyBlock);
                    stampFromBody(tryCatch, tryBlock);
                    region = tryCatch;
                }
                result.add(region);
                // A region whose catch (or sync body) falls through continues at the join after
                // the protected range; without walking it the method's tail is silently dropped.
                if (!isTerminatingRecoveredTry(region)) {
                    Set<IRBlock> consumed = new HashSet<>();
                    for (ExceptionHandler h : outerRegionHandlers) {
                        if (h.getHandlerBlock() != null) {
                            collectCatchConsumedBlocks(h, consumed);
                        }
                    }
                    IRBlock continuation = findBlockAfterTryCatch(outerHandler, consumed);
                    if (continuation != null && !context.isProcessed(continuation)) {
                        result.addAll(recoverRegionHandoff(continuation, new HashSet<>()));
                    }
                }
            } else {
                result.addAll(tryStmts);
            }
        } else {
            // No handler covers the entry: the try begins after a prelude, so the whole region still
            // holds the unprocessed try. The RC engine would only decline it here; let the legacy walk
            // reach the try (recoverTryCatch) and hand the engine the handler-free body from there.
            result.addAll(recoverBlockSequence(entry, handlerBlocks));
        }

        return result;
    }

    /**
     * Merges exception handlers that share the same handler block.
     * This handles the case where a try region is split into multiple exception table entries
     * due to return statements within the try block.
     */
    private List<ExceptionHandler> mergeHandlersWithSameTarget(List<ExceptionHandler> handlers) {
        Map<IRBlock, List<ExceptionHandler>> byHandlerBlock = new LinkedHashMap<>();
        for (ExceptionHandler h : handlers) {
            byHandlerBlock.computeIfAbsent(h.getHandlerBlock(), k -> new ArrayList<>()).add(h);
        }

        List<ExceptionHandler> merged = new ArrayList<>();
        for (List<ExceptionHandler> group : byHandlerBlock.values()) {
            if (group.size() == 1) {
                merged.add(group.get(0));
            } else {
                IRBlock earliestStart = null;
                IRBlock latestEnd = null;
                int minOffset = Integer.MAX_VALUE;
                int maxOffset = Integer.MIN_VALUE;

                for (ExceptionHandler h : group) {
                    if (h.getTryStart() != null && h.getTryStart().getBytecodeOffset() < minOffset) {
                        minOffset = h.getTryStart().getBytecodeOffset();
                        earliestStart = h.getTryStart();
                    }
                    if (h.getTryEnd() != null && h.getTryEnd().getBytecodeOffset() > maxOffset) {
                        maxOffset = h.getTryEnd().getBytecodeOffset();
                        latestEnd = h.getTryEnd();
                    }
                }

                ExceptionHandler mergedHandler = new ExceptionHandler(
                    earliestStart,
                    latestEnd,
                    group.get(0).getHandlerBlock(),
                    group.get(0).getCatchType()
                );
                merged.add(mergedHandler);
            }
        }
        return merged;
    }

    /** Whether two handlers protect the identical try range (making them sibling catch clauses). */
    private static boolean sameTryRange(ExceptionHandler a, ExceptionHandler b) {
        return tryOffset(a.getTryStart()) == tryOffset(b.getTryStart())
                && tryOffset(a.getTryEnd()) == tryOffset(b.getTryEnd());
    }

    private static int tryOffset(IRBlock block) {
        return block != null ? block.getBytecodeOffset() : -1;
    }

    private ExceptionHandler findOutermostHandler(IRBlock entry, List<ExceptionHandler> handlers) {
        ExceptionHandler outermost = null;
        int maxCoverage = -1;

        for (ExceptionHandler handler : handlers) {
            int startOffset = handler.getTryStart().getBytecodeOffset();
            int endOffset = handler.getTryEnd() != null ? handler.getTryEnd().getBytecodeOffset() : Integer.MAX_VALUE;
            int coverage = endOffset - startOffset;

            if (startOffset <= entry.getBytecodeOffset() && coverage > maxCoverage) {
                maxCoverage = coverage;
                outermost = handler;
            }
        }

        return outermost;
    }

    /**
     * Recovers statements with nested exception handlers.
     * The inner handlers may start at blocks different from the entry block.
     */
    private List<Statement> recoverWithNestedHandlers(IRBlock start, List<ExceptionHandler> innerHandlers, Set<IRBlock> stopBlocks) {
        Set<IRBlock> allHandlerBlocks = new HashSet<>();
        for (ExceptionHandler h : innerHandlers) {
            if (h.getHandlerBlock() != null) {
                allHandlerBlocks.add(h.getHandlerBlock());
            }
        }

        List<ExceptionHandler> normalHandlers = new ArrayList<>();
        for (ExceptionHandler h : innerHandlers) {
            if (!allHandlerBlocks.contains(h.getTryStart())) {
                normalHandlers.add(h);
            }
        }

        Set<IRBlock> innerTryStarts = new HashSet<>();
        for (ExceptionHandler h : normalHandlers) {
            innerTryStarts.add(h.getTryStart());
        }

        return recoverWithInnerTryCatch(start, normalHandlers, innerTryStarts, stopBlocks, new HashSet<>());
    }

    /**
     * Recursive helper that recovers blocks and emits inner try-catch statements.
     */
    private List<Statement> recoverWithInnerTryCatch(IRBlock current, List<ExceptionHandler> innerHandlers,
                                                      Set<IRBlock> innerTryStarts, Set<IRBlock> stopBlocks,
                                                      Set<IRBlock> visited) {
        List<Statement> result = new ArrayList<>();

        Set<IRBlock> combinedStops = new HashSet<>(stopBlocks);
        combinedStops.addAll(innerTryStarts);

        while (current != null && !visited.contains(current) && !stopBlocks.contains(current)) {
            if (!innerTryStarts.contains(current)) {
                visited.add(current);
            }

            ExceptionHandler innerHandler = findHandlerStartingAt(current, innerHandlers);

            if (innerHandler != null) {
                List<ExceptionHandler> sameRegionHandlers = new ArrayList<>();
                List<ExceptionHandler> remainingHandlers = new ArrayList<>();

                TryRegion targetRegion = createTryRegion(innerHandler);
                for (ExceptionHandler h : innerHandlers) {
                    TryRegion handlerRegion = createTryRegion(h);
                    if (targetRegion != null && targetRegion.equals(handlerRegion)) {
                        sameRegionHandlers.add(h);
                    } else {
                        remainingHandlers.add(h);
                    }
                }

                Set<IRBlock> innerHandlerBlocks = new HashSet<>();
                for (ExceptionHandler h : sameRegionHandlers) {
                    if (h.getHandlerBlock() != null) {
                        innerHandlerBlocks.add(h.getHandlerBlock());
                    }
                }

                Set<IRBlock> innerStopBlocks = new HashSet<>(stopBlocks);
                innerStopBlocks.addAll(innerHandlerBlocks);

                IRBlock innerTryEnd = innerHandler.getTryEnd();
                if (innerTryEnd != null) {
                    for (IRBlock succ : innerTryEnd.getSuccessors()) {
                        if (!innerHandlerBlocks.contains(succ)) {
                            innerStopBlocks.add(succ);
                        }
                    }
                }

                List<Statement> tryStmts;
                if (!remainingHandlers.isEmpty()) {
                    Set<IRBlock> remainingTryStarts = new HashSet<>();
                    for (ExceptionHandler h : remainingHandlers) {
                        remainingTryStarts.add(h.getTryStart());
                    }
                    tryStmts = recoverWithInnerTryCatch(current, remainingHandlers, remainingTryStarts, innerStopBlocks, new HashSet<>());
                } else {
                    tryStmts = recoverBlocksForTry(current, innerStopBlocks, new HashSet<>());
                }
                BlockStmt tryBlock = new BlockStmt(tryStmts);

                List<CatchClause> catchClauses = new ArrayList<>();
                for (ExceptionHandler h : sameRegionHandlers) {
                    CatchClause catchClause = recoverCatchClause(h);
                    if (catchClause != null) {
                        catchClauses.add(catchClause);
                    }
                }

                if (!catchClauses.isEmpty()) {
                    BlockStmt finallyBlock = null;
                    List<CatchClause> filteredCatches = new ArrayList<>();
                    for (CatchClause clause : catchClauses) {
                        if (isFinallyRethrowPattern(clause)) {
                            finallyBlock = extractFinallyBody(clause);
                        } else {
                            filteredCatches.add(clause);
                        }
                    }
                    TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, filteredCatches, finallyBlock);
                    stampFromBody(tryCatch, tryBlock);
                    result.add(tryCatch);
                } else {
                    result.addAll(tryStmts);
                }

                IRBlock tryEnd = innerHandler.getTryEnd();
                if (tryEnd != null) {
                    visited.add(tryEnd);
                    IRBlock next = null;
                    for (IRBlock succ : tryEnd.getSuccessors()) {
                        if (!visited.contains(succ) && !stopBlocks.contains(succ)) {
                            if (innerTryStarts.contains(succ)) {
                                next = succ;
                                break;
                            }
                            if (!combinedStops.contains(succ) && next == null) {
                                next = succ;
                            }
                        }
                    }
                    current = next;
                } else {
                    current = null;
                }
                continue;
            }

            if (context.isProcessed(current)) {
                result.addAll(context.getStatements(current));
                IRBlock next = null;
                for (IRBlock succ : current.getSuccessors()) {
                    if (!visited.contains(succ) && !stopBlocks.contains(succ)) {
                        if (innerTryStarts.contains(succ)) {
                            next = succ;
                            break;
                        }
                        if (!combinedStops.contains(succ) && next == null) {
                            next = succ;
                        }
                    }
                }
                current = next;
                continue;
            }

            RegionInfo info = analyzer.getRegionInfo(current);
            if (info == null) {
                List<Statement> blockStmts = recoverSimpleBlock(current);
                result.addAll(blockStmts);
                context.setStatements(current, blockStmts);
                context.markProcessed(current);
                IRBlock next = null;
                for (IRBlock succ : current.getSuccessors()) {
                    if (!visited.contains(succ) && !stopBlocks.contains(succ)) {
                        if (innerTryStarts.contains(succ)) {
                            next = succ;
                            break;
                        }
                        if (!combinedStops.contains(succ) && next == null) {
                            next = succ;
                        }
                    }
                }
                current = next;
                continue;
            }

            switch (info.getType()) {
                case IF_THEN: {
                    Statement ifStmt = recoverIfThen(current, info);
                    result.addAll(context.collectPendingStatements());
                    result.add(ifStmt);
                    IRBlock merge = info.getMergeBlock();
                    if (merge != null && !visited.contains(merge) && !stopBlocks.contains(merge)) {
                        current = merge;
                    } else {
                        current = null;
                    }
                    break;
                }
                case IF_THEN_ELSE: {
                    Statement ifStmt = recoverIfThenElse(current, info);
                    result.addAll(context.collectPendingStatements());
                    result.add(ifStmt);
                    IRBlock merge = info.getMergeBlock();
                    if (merge != null && !visited.contains(merge) && !stopBlocks.contains(merge)) {
                        current = merge;
                    } else {
                        current = null;
                    }
                    break;
                }
                case WHILE_LOOP: {
                    result.add(recoverWhileLoop(current, info));
                    current = findLoopExit(info, visited, stopBlocks);
                    break;
                }
                case DO_WHILE_LOOP: {
                    result.add(recoverDoWhileLoop(current, info));
                    current = findLoopExit(info, visited, stopBlocks);
                    break;
                }
                case FOR_LOOP: {
                    result.add(recoverForLoop(current, info));
                    current = findLoopExit(info, visited, stopBlocks);
                    break;
                }
                case GUARD_CLAUSE: {
                    Statement guardStmt = recoverGuardClause(current, info);
                    // Collect pending statements (header computations) BEFORE the guard
                    result.addAll(context.collectPendingStatements());
                    result.add(guardStmt);
                    context.markProcessed(current);
                    current = info.getElseBlock();
                    break;
                }
                default: {
                    List<Statement> blockStmts = recoverSimpleBlock(current);
                    result.addAll(blockStmts);
                    context.setStatements(current, blockStmts);
                    context.markProcessed(current);
                    IRBlock next = null;
                    for (IRBlock succ : current.getSuccessors()) {
                        if (!visited.contains(succ) && !stopBlocks.contains(succ)) {
                            if (innerTryStarts.contains(succ)) {
                                next = succ;
                                break;
                            }
                            if (!combinedStops.contains(succ) && next == null) {
                                next = succ;
                            }
                        }
                    }
                    current = next;
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Finds a handler that starts at the given block.
     */
    private ExceptionHandler findHandlerStartingAt(IRBlock block, List<ExceptionHandler> handlers) {
        for (ExceptionHandler handler : handlers) {
            if (handler.getTryStart() == block) {
                return handler;
            }
        }
        return null;
    }

    private TryRegion createTryRegion(ExceptionHandler handler) {
        if (handler == null || handler.getTryStart() == null) {
            return null;
        }
        return new TryRegion(handler.getTryStart(), handler.getTryEnd());
    }

    /**
     * The caught type for one exception-table entry: {@code java/lang/Throwable} for a catch-all, otherwise
     * the declared catch type. Used to assemble the type list of a reconstructed multi-catch clause.
     */
    private SourceType catchTypeOf(ExceptionHandler handler) {
        if (handler.isCatchAll()) {
            return new ReferenceSourceType("java/lang/Throwable", Collections.emptyList());
        }
        return new ReferenceSourceType(handler.getCatchType().getInternalName(), Collections.emptyList());
    }

    /**
     * Builds the catch clauses for one try region, coalescing every group of handlers that share a handler
     * block into a single (multi-)catch clause. javac compiles a multi-catch {@code catch (A | B e)} to one
     * handler block reached by several exception-table entries; duplicate types (a region split into several
     * entries by intervening returns) collapse to a single type.
     */
    private List<CatchClause> buildCatchClauses(List<ExceptionHandler> regionHandlers) {
        Map<IRBlock, List<ExceptionHandler>> byBlock = new LinkedHashMap<>();
        for (ExceptionHandler h : regionHandlers) {
            if (h.getHandlerBlock() != null) {
                byBlock.computeIfAbsent(h.getHandlerBlock(), k -> new ArrayList<>()).add(h);
            }
        }

        List<CatchClause> clauses = new ArrayList<>();
        for (List<ExceptionHandler> group : byBlock.values()) {
            CatchClause clause = recoverCatchClause(group.get(0));
            if (clause == null) {
                continue;
            }
            List<SourceType> types = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ExceptionHandler h : group) {
                SourceType type = catchTypeOf(h);
                if (seen.add(((ReferenceSourceType) type).getInternalName())) {
                    types.add(type);
                }
            }
            if (types.size() > 1) {
                clause = CatchClause.multiCatch(types, clause.variableName(), clause.body());
            }
            clauses.add(clause);
        }
        return clauses;
    }

    /**
     * Detects a {@code synchronized} block. javac compiles it to a protected region guarded by a catch-all
     * handler that releases the monitor ({@code monitorexit}) and rethrows, with a {@code monitorenter} on
     * the same lock dominating the region. Returns the lock value of that {@code monitorenter}, or null if
     * the handler is not a monitor-release. The monitor instructions themselves are dropped during statement
     * recovery, so the region's recovered body is exactly the synchronized body.
     */
    private Value detectSynchronizedLock(ExceptionHandler handler) {
        if (handler == null || !handler.isCatchAll() || handler.getHandlerBlock() == null) {
            return null;
        }
        if (!blockContainsMonitorExit(handler.getHandlerBlock())) {
            return null;
        }
        return findMonitorEnterLock(handler.getTryStart());
    }

    private boolean blockContainsMonitorExit(IRBlock block) {
        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof SimpleInstruction && ((SimpleInstruction) instr).getOp() == SimpleOp.MONITOREXIT) {
                return true;
            }
        }
        return false;
    }

    /**
     * Searches backwards from a try region's start for the {@code monitorenter} that opens it, returning its
     * lock operand (the innermost enter reached first). Null if none precedes the region.
     */
    private Value findMonitorEnterLock(IRBlock tryStart) {
        if (tryStart == null) {
            return null;
        }
        Set<IRBlock> visited = new HashSet<>();
        Deque<IRBlock> work = new ArrayDeque<>(tryStart.getPredecessors());
        while (!work.isEmpty()) {
            IRBlock block = work.poll();
            if (!visited.add(block)) {
                continue;
            }
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof SimpleInstruction
                        && ((SimpleInstruction) instr).getOp() == SimpleOp.MONITORENTER) {
                    return ((SimpleInstruction) instr).getOperand();
                }
            }
            work.addAll(block.getPredecessors());
        }
        return null;
    }

    private CatchClause recoverCatchClause(ExceptionHandler handler) {
        IRBlock handlerBlock = handler.getHandlerBlock();
        if (handlerBlock == null) {
            return null;
        }

        ReferenceSourceType exceptionType;
        if (handler.isCatchAll()) {
            exceptionType = new ReferenceSourceType("java/lang/Throwable", Collections.emptyList());
        } else {
            String typeName = handler.getCatchType().getInternalName();
            exceptionType = new ReferenceSourceType(typeName, Collections.emptyList());
        }

        String exceptionVarName = findExceptionVariableName(handlerBlock);
        if (exceptionVarName == null) {
            String simpleName;
            simpleName = exceptionType.getSimpleName().toLowerCase();
            exceptionVarName = simpleName.charAt(0) + "_ex";
        }

        registerExceptionVariables(handler, exceptionVarName);

        Set<SSAValue> exceptionValues = collectExceptionValues(handlerBlock);

        List<Statement> handlerStmts = new ArrayList<>();

        for (IRInstruction instr : handlerBlock.getInstructions()) {
            if (instr instanceof CopyInstruction) {
                continue;
            }

            if (instr instanceof StoreLocalInstruction) {
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                if (store.getValue() instanceof SSAValue) {
                    SSAValue ssaValue = (SSAValue) store.getValue();
                    if (exceptionValues.contains(ssaValue)) {
                        continue;
                    }
                }
            }

            Statement stmt = recoverInstruction(instr);
            if (stmt != null) {
                handlerStmts.add(stmt);
            }
        }

        IRInstruction handlerTerminator = handlerBlock.getTerminator();
        boolean isGoto = false;
        if (handlerTerminator instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) handlerTerminator;
            isGoto = (simple.getOp() == SimpleOp.GOTO);
        }
        if (!isGoto) {
            Set<IRBlock> visitedHandlerBlocks = new HashSet<>();
            visitedHandlerBlocks.add(handlerBlock);
            recoverHandlerBlocks(handlerBlock.getSuccessors(), visitedHandlerBlocks, handlerStmts);
        } else if (handler.isCatchAll()) {
            Set<IRBlock> visitedHandlerBlocks = new HashSet<>();
            visitedHandlerBlocks.add(handlerBlock);
            recoverHandlerBlocks(handlerBlock.getSuccessors(), visitedHandlerBlocks, handlerStmts);
        }

        List<Statement> filteredStmts = new ArrayList<>();
        boolean reachedTerminator = false;
        for (Statement stmt : handlerStmts) {
            if (reachedTerminator) {
                continue;
            }
            if (stmt instanceof VarDeclStmt) {
                VarDeclStmt varDecl = (VarDeclStmt) stmt;
                if (varDecl.getName().equals(exceptionVarName)) {
                    continue;
                }
            }
            if (stmt instanceof ExprStmt) {
                ExprStmt exprStmt = (ExprStmt) stmt;
                if (exprStmt.getExpression() instanceof BinaryExpr) {
                    BinaryExpr binExpr = (BinaryExpr) exprStmt.getExpression();
                    if (binExpr.getOperator() == BinaryOperator.ASSIGN) {
                        if (binExpr.getLeft() instanceof VarRefExpr) {
                            VarRefExpr varRef = (VarRefExpr) binExpr.getLeft();
                            if (varRef.getName().equals(exceptionVarName)) {
                                continue;
                            }
                        }
                    }
                }
            }
            filteredStmts.add(stmt);

            if (stmt instanceof ReturnStmt || stmt instanceof ThrowStmt) {
                reachedTerminator = true;
            }
        }

        BlockStmt handlerBody = new BlockStmt(filteredStmts.isEmpty() ? handlerStmts : filteredStmts);

        return CatchClause.of(exceptionType, exceptionVarName, handlerBody);
    }

    /**
     * Checks if a catch clause represents a finally-rethrow pattern.
     * A finally-rethrow pattern is a catch-all (Throwable) that ends with throwing the caught exception.
     */
    private boolean isFinallyRethrowPattern(CatchClause clause) {
        if (clause == null) return false;

        SourceType type = clause.getPrimaryType();
        if (!(type instanceof ReferenceSourceType)) return false;

        String typeName = ((ReferenceSourceType) type).getInternalName();
        if (!typeName.equals("java/lang/Throwable") && !typeName.equals("Throwable")) {
            return false;
        }

        Statement body = clause.body();
        if (!(body instanceof BlockStmt)) return false;

        List<Statement> stmts = ((BlockStmt) body).getStatements();
        if (stmts.isEmpty()) return false;

        Statement lastStmt = stmts.get(stmts.size() - 1);
        if (!(lastStmt instanceof ThrowStmt)) return false;

        ThrowStmt throwStmt = (ThrowStmt) lastStmt;
        Expression thrown = throwStmt.getException();

        if (thrown instanceof VarRefExpr) {
            String thrownVar = ((VarRefExpr) thrown).getName();
            return thrownVar.equals(clause.variableName());
        }

        return false;
    }

    /**
     * Extracts the finally body from a finally-rethrow catch clause.
     * The finally body is everything except the final throw statement.
     */
    private BlockStmt extractFinallyBody(CatchClause clause) {
        if (clause == null || !(clause.body() instanceof BlockStmt)) {
            return new BlockStmt(Collections.emptyList());
        }

        List<Statement> stmts = ((BlockStmt) clause.body()).getStatements();
        if (stmts.isEmpty()) {
            return new BlockStmt(Collections.emptyList());
        }

        List<Statement> finallyStmts = new ArrayList<>(stmts.subList(0, stmts.size() - 1));
        return new BlockStmt(finallyStmts);
    }

    private List<Statement> filterOrphanFinallyThrows(List<Statement> statements, Set<String> finallyExceptionVars) {
        List<Statement> filtered = new ArrayList<>();
        for (Statement stmt : statements) {
            if (stmt instanceof ThrowStmt) {
                ThrowStmt throwStmt = (ThrowStmt) stmt;
                Expression exception = throwStmt.getException();
                if (exception instanceof VarRefExpr) {
                    String varName = ((VarRefExpr) exception).getName();
                    if (finallyExceptionVars.contains(varName)) {
                        continue;
                    }
                }
            }
            filtered.add(stmt);
        }
        return filtered;
    }

    private List<Statement> filterInlinedFinallyFromTryStatements(List<Statement> statements, List<Statement> finallyStmts) {
        if (finallyStmts == null || finallyStmts.isEmpty()) {
            return statements;
        }

        List<Statement> result = new ArrayList<>();
        int i = 0;
        while (i < statements.size()) {
            Statement stmt = statements.get(i);

            if (stmt instanceof ReturnStmt || stmt instanceof ThrowStmt) {
                result.add(stmt);
                i++;
                continue;
            }

            if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                Statement newThen = filterInlinedFinallyFromBranch(ifStmt.getThenBranch(), finallyStmts);
                Statement newElse = ifStmt.getElseBranch() != null
                    ? filterInlinedFinallyFromBranch(ifStmt.getElseBranch(), finallyStmts)
                    : null;
                IfStmt rebuiltIf = new IfStmt(ifStmt.getCondition(), newThen, newElse);
                Locations.copy(ifStmt, rebuiltIf);
                result.add(rebuiltIf);
                i++;
                continue;
            }

            if (stmt instanceof WhileStmt) {
                WhileStmt whileStmt = (WhileStmt) stmt;
                Statement newBody = filterInlinedFinallyFromBranch(whileStmt.getBody(), finallyStmts);
                WhileStmt rebuiltWhile = new WhileStmt(whileStmt.getCondition(), newBody);
                Locations.copy(whileStmt, rebuiltWhile);
                result.add(rebuiltWhile);
                i++;
                continue;
            }

            if (stmt instanceof DoWhileStmt) {
                DoWhileStmt doWhileStmt = (DoWhileStmt) stmt;
                Statement newBody = filterInlinedFinallyFromBranch(doWhileStmt.getBody(), finallyStmts);
                DoWhileStmt rebuiltDoWhile = new DoWhileStmt(newBody, doWhileStmt.getCondition());
                Locations.copy(doWhileStmt, rebuiltDoWhile);
                result.add(rebuiltDoWhile);
                i++;
                continue;
            }

            if (stmt instanceof ForStmt) {
                ForStmt forStmt = (ForStmt) stmt;
                Statement newBody = filterInlinedFinallyFromBranch(forStmt.getBody(), finallyStmts);
                ForStmt rebuiltFor = new ForStmt(forStmt.getInit(), forStmt.getCondition(), forStmt.getUpdate(), newBody);
                Locations.copy(forStmt, rebuiltFor);
                result.add(rebuiltFor);
                i++;
                continue;
            }

            if (stmt instanceof BlockStmt) {
                BlockStmt blockStmt = (BlockStmt) stmt;
                List<Statement> filteredBlock = filterInlinedFinallyFromTryStatements(blockStmt.getStatements(), finallyStmts);
                result.add(new BlockStmt(filteredBlock));
                i++;
                continue;
            }

            if (isStatementSequenceMatchingFinally(statements, i, finallyStmts)) {
                int nextIdx = i + finallyStmts.size();
                if (nextIdx < statements.size()) {
                    Statement next = statements.get(nextIdx);
                    if (next instanceof ReturnStmt || next instanceof ThrowStmt) {
                        i = nextIdx;
                        continue;
                    }
                }
            }

            result.add(stmt);
            i++;
        }
        return result;
    }

    private Statement filterInlinedFinallyFromBranch(Statement branch, List<Statement> finallyStmts) {
        if (branch == null) return null;

        if (branch instanceof BlockStmt) {
            List<Statement> filtered = filterInlinedFinallyFromTryStatements(
                ((BlockStmt) branch).getStatements(), finallyStmts);
            return new BlockStmt(filtered);
        }

        List<Statement> singleStmt = new ArrayList<>();
        singleStmt.add(branch);
        List<Statement> filtered = filterInlinedFinallyFromTryStatements(singleStmt, finallyStmts);
        if (filtered.isEmpty()) {
            return new BlockStmt(Collections.emptyList());
        }
        if (filtered.size() == 1) {
            return filtered.get(0);
        }
        return new BlockStmt(filtered);
    }

    private boolean isStatementSequenceMatchingFinally(List<Statement> statements, int startIdx, List<Statement> finallyStmts) {
        if (startIdx + finallyStmts.size() > statements.size()) {
            return false;
        }
        for (int i = 0; i < finallyStmts.size(); i++) {
            if (!statementsMatch(statements.get(startIdx + i), finallyStmts.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Recovers the blocks between a protected region's end and its handler - the finally inlined on the normal
     * exit path followed by the region's real continuation - then strips the inlined-finally copies. Recovery
     * is <em>structural</em> (not linear block-by-block) because when the finally contains control flow (e.g. a
     * conditional {@code return}) the inlined copy is itself an {@code if}/{@code else} whose non-finally arm is
     * the genuine continuation; linear recovery would otherwise stop at the finally's own inlined return.
     */
    private List<Statement> recoverFinallyGap(IRBlock tryEnd, IRBlock handlerBlock,
                                              BlockStmt finallyBlock, Set<String> finallyExceptionVars) {
        IRMethod irMethod = context.getIrMethod();
        int tryEndOffset = tryEnd.getBytecodeOffset();
        int handlerOffset = handlerBlock.getBytecodeOffset();

        IRBlock gapStart = null;
        Set<IRBlock> stopBlocks = new HashSet<>();
        for (IRBlock block : irMethod.getBlocks()) {
            int offset = block.getBytecodeOffset();
            if (offset >= handlerOffset) {
                stopBlocks.add(block);
            } else if (offset >= tryEndOffset
                    && (gapStart == null || offset < gapStart.getBytecodeOffset())) {
                gapStart = block;
            }
        }
        if (gapStart == null) {
            return Collections.emptyList();
        }

        List<Statement> gapStmts;
        context.pushStopBlocks(stopBlocks);
        try {
            gapStmts = recoverBlockSequence(gapStart, stopBlocks);
        } finally {
            context.popStopBlocks();
        }
        gapStmts = filterOrphanFinallyThrows(gapStmts, finallyExceptionVars);
        gapStmts = filterInlinedFinallyFromTryStatements(gapStmts, finallyBlock.getStatements());
        // Drop any standalone inlined-finally statement (one not folded into a preceding return by the filter
        // above), so a side-effecting finally like `x += 2` appears only in the finally, not also in the body.
        List<Statement> deduped = new ArrayList<>();
        for (Statement stmt : gapStmts) {
            if (!isStatementInFinallyBlock(stmt, finallyBlock.getStatements())) {
                deduped.add(stmt);
            }
        }
        return deduped;
    }

    private boolean isStatementInFinallyBlock(Statement stmt, List<Statement> finallyStmts) {
        if (finallyStmts.isEmpty()) {
            return false;
        }

        for (Statement finallyStmt : finallyStmts) {
            if (statementsMatch(stmt, finallyStmt)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recursively recovers all blocks reachable from the handler until we hit a throw, return, or goto.
     * IMPORTANT: A GotoInstruction in a catch handler typically means "exit the catch and continue",
     * so we should NOT follow goto targets - they are the merge point, not part of the catch body.
     */
    private void recoverHandlerBlocks(Collection<IRBlock> successors, Set<IRBlock> visited, List<Statement> stmts) {
        for (IRBlock block : successors) {
            if (visited.contains(block)) continue;
            visited.add(block);

            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof CopyInstruction) continue;
                Statement stmt = recoverInstruction(instr);
                if (stmt != null) {
                    stmts.add(stmt);
                }
            }

            IRInstruction terminator = block.getTerminator();
            boolean isThrow = false;
            boolean isReturn = terminator instanceof ReturnInstruction;
            boolean isGoto = false;

            if (!isReturn && terminator instanceof SimpleInstruction) {
                SimpleInstruction simple = (SimpleInstruction) terminator;
                if (simple.getOp() == SimpleOp.ATHROW) {
                    isThrow = true;
                } else if (simple.getOp() == SimpleOp.GOTO) {
                    isGoto = true;
                }
            }

            if (isThrow) {
                SimpleInstruction simple = (SimpleInstruction) terminator;
                Expression exception = exprRecoverer.recoverOperand(simple.getOperand());
                Statement throwStmt = new ThrowStmt(exception);
                stamp(throwStmt, simple);
                stmts.add(throwStmt);
                continue;
            }

            if (isReturn) {
                ReturnInstruction ret = (ReturnInstruction) terminator;
                Statement returnStmt = recoverReturn(ret);
                stamp(returnStmt, ret);
                stmts.add(returnStmt);
                continue;
            }

            if (isGoto) {
                continue;
            }

            recoverHandlerBlocks(block.getSuccessors(), visited, stmts);
        }
    }

    /**
     * Finds the exception variable name from the handler block.
     * The first instruction in a catch handler typically stores the exception to a local.
     */
    private String findExceptionVariableName(IRBlock handlerBlock) {
        for (IRInstruction instr : handlerBlock.getInstructions()) {
            if (instr instanceof StoreLocalInstruction) {
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                return "local" + store.getLocalIndex();
            }
            if (instr instanceof CopyInstruction) {
                CopyInstruction copy = (CopyInstruction) instr;
                SSAValue result = copy.getResult();
                if (result != null) {
                    String name = context.getExpressionContext().getVariableName(result);
                    if (name != null) {
                        return name;
                    }
                }
            }
            break;
        }
        return null;
    }

    /**
     * Collects all SSA values that represent the caught exception in a handler block.
     * These values come from CopyInstruction at the start of the handler.
     */
    private Set<SSAValue> collectExceptionValues(IRBlock handlerBlock) {
        Set<SSAValue> exceptionValues = new HashSet<>();
        for (IRInstruction instr : handlerBlock.getInstructions()) {
            if (instr instanceof CopyInstruction) {
                CopyInstruction copy = (CopyInstruction) instr;
                if (copy.getResult() != null) {
                    exceptionValues.add(copy.getResult());
                }
                if (copy.getSource() instanceof SSAValue) {
                    SSAValue ssaSrc = (SSAValue) copy.getSource();
                    exceptionValues.add(ssaSrc);
                }
            }
            SSAValue result = instr.getResult();
            if (result != null) {
                String name = result.getName();
                if (name != null && name.startsWith("exc_")) {
                    exceptionValues.add(result);
                }
            }
            if (instr instanceof StoreLocalInstruction) {
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                if (store.getValue() instanceof SSAValue) {
                    SSAValue ssaValue = (SSAValue) store.getValue();
                    exceptionValues.add(ssaValue);
                }
                break;
            }
        }
        return exceptionValues;
    }

    /**
     * Registers all SSA values that represent the exception in an exception handler.
     * This ensures they reference the catch parameter instead of undefined "v#" names.
     */
    private void registerExceptionVariables(ExceptionHandler handler, String exceptionVarName) {
        IRBlock handlerBlock = handler.getHandlerBlock();
        // Bound the walk to the blocks the catch clause's recovery actually consumes. The
        // handler block often falls through (physically) into the join after the try/catch;
        // an unbounded successor walk would claim every later load of the exception's slot
        // (a slot javac freely reuses) and rename unrelated variables to the catch variable.
        Set<IRBlock> consumed = new HashSet<>();
        collectCatchConsumedBlocks(handler, consumed);
        Set<SSAValue> exceptionValuesSet = new HashSet<>();
        findExceptionSSAValues(handlerBlock, exceptionValuesSet, consumed);

        for (SSAValue excVal : exceptionValuesSet) {
            context.getExpressionContext().setVariableName(excVal, exceptionVarName);
        }
    }

    /**
     * Finds all SSA values that represent the caught exception.
     * Starts from values with "exc_" prefix and tracks through copies and local stores/loads.
     */
    private void findExceptionSSAValues(IRBlock block, Set<SSAValue> result, Set<IRBlock> bound) {
        Set<Integer> exceptionLocalSlots = new HashSet<>();

        findExceptionSSAValuesPass1(block, result, exceptionLocalSlots, new HashSet<>(), bound);

        if (!exceptionLocalSlots.isEmpty()) {
            findExceptionSSAValuesPass2(block, result, exceptionLocalSlots, new HashSet<>(), bound);
        }
    }

    /**
     * First pass: Find exc_ prefix values and track which local slots they're stored to.
     */
    private void findExceptionSSAValuesPass1(IRBlock block, Set<SSAValue> result,
                                               Set<Integer> exceptionSlots, Set<IRBlock> visited,
                                               Set<IRBlock> bound) {
        if (visited.contains(block) || !bound.contains(block)) return;
        visited.add(block);

        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof CopyInstruction) {
                CopyInstruction copy = (CopyInstruction) instr;
                SSAValue copyResult = copy.getResult();
                Value copySource = copy.getSource();

                if (copySource instanceof SSAValue) {
                    SSAValue ssaSrc = (SSAValue) copySource;
                    String name = ssaSrc.getName();
                    if (name != null && name.startsWith("exc_")) {
                        result.add(ssaSrc);
                        if (copyResult != null) {
                            result.add(copyResult);
                        }
                    } else if (result.contains(ssaSrc)) {
                        if (copyResult != null) {
                            result.add(copyResult);
                        }
                    }
                }
            }

            if (instr instanceof StoreLocalInstruction) {
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                if (store.getValue() instanceof SSAValue) {
                    SSAValue ssaValue = (SSAValue) store.getValue();
                    String name = ssaValue.getName();
                    if (name != null && name.startsWith("exc_")) {
                        result.add(ssaValue);
                        exceptionSlots.add(store.getLocalIndex());
                    } else if (result.contains(ssaValue)) {
                        exceptionSlots.add(store.getLocalIndex());
                    }
                }
            }

            for (Value operand : instr.getOperands()) {
                if (operand instanceof SSAValue) {
                    SSAValue ssaOp = (SSAValue) operand;
                    String name = ssaOp.getName();
                    if (name != null && name.startsWith("exc_")) {
                        result.add(ssaOp);
                    }
                }
            }
        }

        for (IRBlock succ : block.getSuccessors()) {
            findExceptionSSAValuesPass1(succ, result, exceptionSlots, visited, bound);
        }
    }

    /**
     * Second pass: Find LoadLocal from exception-containing slots.
     */
    private void findExceptionSSAValuesPass2(IRBlock block, Set<SSAValue> result,
                                               Set<Integer> exceptionSlots, Set<IRBlock> visited,
                                               Set<IRBlock> bound) {
        if (visited.contains(block) || !bound.contains(block)) return;
        visited.add(block);

        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof LoadLocalInstruction) {
                LoadLocalInstruction load = (LoadLocalInstruction) instr;
                if (exceptionSlots.contains(load.getLocalIndex())) {
                    SSAValue loadResult = load.getResult();
                    if (loadResult != null) {
                        result.add(loadResult);
                    }
                }
            } else if (instr instanceof StoreLocalInstruction) {
                // A store of a non-exception value redefines the slot: javac reuses the catch
                // variable's slot for unrelated locals afterwards, so later loads are NOT the
                // exception and must keep their own variable.
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                if (exceptionSlots.contains(store.getLocalIndex())) {
                    Value stored = store.getValue();
                    boolean isExceptionValue = stored instanceof SSAValue && result.contains(stored);
                    if (!isExceptionValue) {
                        exceptionSlots.remove(store.getLocalIndex());
                    }
                }
            }
        }

        for (IRBlock succ : block.getSuccessors()) {
            findExceptionSSAValuesPass2(succ, result, exceptionSlots, visited, bound);
        }
    }

    /**
     * Represents a try region defined by start and end blocks.
     */
    private static final class TryRegion {
        private final IRBlock start;
        private final IRBlock end;

        public TryRegion(IRBlock start, IRBlock end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TryRegion that = (TryRegion) obj;
            return Objects.equals(start, that.start) &&
                   Objects.equals(end, that.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }

        @Override
        public String toString() {
            return "TryRegion{" +
                   "start=" + start +
                   ", end=" + end +
                   '}';
        }
    }

    /**
     * Finds the exception handler whose try region starts at the given block, preferring the
     * widest region: nested regions share a start pc with their enclosing one (and the table
     * lists inner entries first), so taking the first match would recover the inner region and
     * silently drop the outer catch. Ties keep table order.
     */
    private ExceptionHandler findHandlerStartingAt(IRBlock block) {
        IRMethod irMethod = context.getIrMethod();
        List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();
        if (handlers == null || handlers.isEmpty()) {
            return null;
        }
        ExceptionHandler best = null;
        int bestEnd = Integer.MIN_VALUE;
        for (ExceptionHandler handler : handlers) {
            IRBlock tryStart = handler.getTryStart();
            if (tryStart == block ||
                (tryStart != null && tryStart.getBytecodeOffset() == block.getBytecodeOffset())) {
                int end = handler.getTryEnd() != null
                        ? handler.getTryEnd().getBytecodeOffset() : Integer.MAX_VALUE;
                if (best == null || end > bestEnd) {
                    best = handler;
                    bestEnd = end;
                }
            }
        }
        return best;
    }

    /**
     * Recovers a try-with-resources statement from javac's desugaring. javac lowers
     * {@code try (R r = init) { body }} into per-resource cleanup handlers that close the resource and
     * re-throw, chaining suppressed exceptions via {@code Throwable.addSuppressed}. The generic try/catch
     * recovery mangles this (dropped return value, out-of-scope exception variable, unconditional throw).
     * When the {@code addSuppressed} signature is present this marks the cleanup handlers processed, recovers
     * the clean normal path (the handler blocks are not normal successors), lifts the resource declarations
     * into a {@code try (r1; r2) { ... }} header and strips the synthetic close calls. Returns null when the
     * method is not a try-with-resources, so the caller falls back to the generic recovery.
     */
    private List<Statement> recoverTryWithResources(IRBlock entry, List<ExceptionHandler> handlers) {
        if (!isTryWithResourcesMethod(handlers)) {
            return null;
        }
        for (ExceptionHandler h : handlers) {
            processedTryHandlers.add(h);
            if (h.getHandlerBlock() != null) {
                processedHandlerBlocks.add(h.getHandlerBlock());
            }
        }
        List<Statement> normalPath = recoverBlockSequence(entry, new HashSet<>());
        return reconstructTryWithResources(normalPath);
    }

    /** True when an exception handler (transitively) calls {@code Throwable.addSuppressed} — the TWR signature. */
    private boolean isTryWithResourcesMethod(List<ExceptionHandler> handlers) {
        Set<IRBlock> seen = new HashSet<>();
        Deque<IRBlock> work = new ArrayDeque<>();
        for (ExceptionHandler h : handlers) {
            if (h.getHandlerBlock() != null) {
                work.add(h.getHandlerBlock());
            }
        }
        while (!work.isEmpty()) {
            IRBlock b = work.poll();
            if (!seen.add(b)) {
                continue;
            }
            for (IRInstruction instr : b.getInstructions()) {
                if (instr instanceof InvokeInstruction && "addSuppressed".equals(((InvokeInstruction) instr).getName())) {
                    return true;
                }
            }
            work.addAll(b.getSuccessors());
        }
        return false;
    }

    /**
     * Folds a recovered clean normal path of a try-with-resources method into {@code try (resources) { body }}.
     * Resources are the locals that are {@code close()}d on the normal path; their declarations are lifted out
     * and referenced from the try header, and the synthetic close calls are dropped.
     */
    private List<Statement> reconstructTryWithResources(List<Statement> normalPath) {
        Set<String> closedVars = new LinkedHashSet<>();
        for (Statement s : normalPath) {
            String closed = findClosedResource(s);
            if (closed != null) {
                closedVars.add(closed);
            }
        }
        if (closedVars.isEmpty()) {
            return null;
        }

        List<VarDeclStmt> resourceDecls = new ArrayList<>();
        Set<String> resourceNames = new LinkedHashSet<>();
        for (Statement s : normalPath) {
            if (s instanceof VarDeclStmt) {
                VarDeclStmt d = (VarDeclStmt) s;
                if (closedVars.contains(d.getName()) && resourceNames.add(d.getName())) {
                    resourceDecls.add(d);
                }
            }
        }
        if (resourceDecls.isEmpty()) {
            return null;
        }

        int firstIdx = normalPath.size();
        for (int i = 0; i < normalPath.size(); i++) {
            Statement s = normalPath.get(i);
            if (s instanceof VarDeclStmt && resourceNames.contains(((VarDeclStmt) s).getName())) {
                firstIdx = i;
                break;
            }
        }

        List<Statement> pre = new ArrayList<>(normalPath.subList(0, firstIdx));
        List<Statement> body = new ArrayList<>();
        for (int i = firstIdx; i < normalPath.size(); i++) {
            Statement s = normalPath.get(i);
            if (s instanceof VarDeclStmt && resourceNames.contains(((VarDeclStmt) s).getName())) {
                continue; // resource declaration -> lifted into the try header
            }
            String closed = findClosedResource(s);
            if (closed != null && resourceNames.contains(closed)) {
                continue; // synthetic resource close (a bare close() or the `if (r != null) r.close()` finally)
            }
            body.add(s);
        }

        List<Expression> resources = new ArrayList<>();
        for (VarDeclStmt d : resourceDecls) {
            resources.add(new VarRefExpr(d.getName(), d.getType()));
        }
        TryCatchStmt tryStmt = new TryCatchStmt(new BlockStmt(body), new ArrayList<>(), null, resources,
                SourceLocation.UNKNOWN);

        List<Statement> result = new ArrayList<>(pre);
        result.addAll(resourceDecls);
        result.add(tryStmt);
        return result;
    }

    /**
     * The resource variable closed by {@code s}, whether directly ({@code r.close();}) or through the
     * try-with-resources finally wrapper javac emits ({@code if (r != null) { ... r.close() ... }}).
     * Recurses into if-branches and blocks so a close nested inside that wrapper is still recognized as
     * the resource's, which top-level inspection alone misses.
     */
    private String findClosedResource(Statement s) {
        if (s instanceof ExprStmt) {
            return closeReceiverName(s);
        }
        if (s instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) s;
            String r = findClosedResource(ifStmt.getThenBranch());
            return r != null ? r : findClosedResource(ifStmt.getElseBranch());
        }
        if (s instanceof BlockStmt) {
            for (Statement inner : ((BlockStmt) s).getStatements()) {
                String r = findClosedResource(inner);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /** The receiver variable name of a {@code receiver.close()} expression statement, else null. */
    private String closeReceiverName(Statement s) {
        if (!(s instanceof ExprStmt)) {
            return null;
        }
        Expression e = ((ExprStmt) s).getExpression();
        if (!(e instanceof MethodCallExpr)) {
            return null;
        }
        MethodCallExpr mc = (MethodCallExpr) e;
        if (!"close".equals(mc.getMethodName())) {
            return null;
        }
        return mc.getReceiver() instanceof VarRefExpr ? ((VarRefExpr) mc.getReceiver()).getName() : null;
    }

    private Statement recoverTryCatch(IRBlock startBlock, ExceptionHandler mainHandler,
                                          Set<IRBlock> originalStopBlocks, Set<IRBlock> visited) {
        IRMethod irMethod = context.getIrMethod();
        List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();

        // javac splits one try's protected range into several exception-table entries around
        // instructions that exit the try (a break/return between protected sections). All entries
        // targeting this handler block are the SAME source try; widen the effective range to their
        // union, otherwise the body recovery stops at the first entry's end and the blocks of the
        // later entries (e.g. code after an `if (...) break;` in the try) are silently skipped.
        IRBlock mergedStart = mainHandler.getTryStart();
        IRBlock mergedEnd = mainHandler.getTryEnd();
        for (ExceptionHandler h : handlers) {
            if (h.getHandlerBlock() != mainHandler.getHandlerBlock()) {
                continue;
            }
            if (h.getTryStart() != null && (mergedStart == null
                    || h.getTryStart().getBytecodeOffset() < mergedStart.getBytecodeOffset())) {
                mergedStart = h.getTryStart();
            }
            if (h.getTryEnd() != null && (mergedEnd == null
                    || h.getTryEnd().getBytecodeOffset() > mergedEnd.getBytecodeOffset())) {
                mergedEnd = h.getTryEnd();
            }
        }
        if (mergedStart != mainHandler.getTryStart() || mergedEnd != mainHandler.getTryEnd()) {
            mainHandler = new ExceptionHandler(
                mergedStart, mergedEnd, mainHandler.getHandlerBlock(), mainHandler.getCatchType());
        }

        // The region's clause set: entries targeting the same handler block (the split ranges
        // above plus multi-catch types) and entries whose range equals the merged range (a
        // finally handler protecting exactly this try).
        List<ExceptionHandler> sameRegionHandlers = new ArrayList<>();
        TryRegion mainRegion = createTryRegion(mainHandler);
        for (ExceptionHandler h : handlers) {
            if (h.getHandlerBlock() == mainHandler.getHandlerBlock()
                    || (mainRegion != null && mainRegion.equals(createTryRegion(h)))) {
                sameRegionHandlers.add(h);
            }
        }

        Set<IRBlock> tryStopBlocks = new HashSet<>(originalStopBlocks);
        for (ExceptionHandler h : sameRegionHandlers) {
            if (h.getHandlerBlock() != null) {
                tryStopBlocks.add(h.getHandlerBlock());
            }
        }

        if (mainHandler.getTryEnd() != null) {
            int tryEndOffset = mainHandler.getTryEnd().getBytecodeOffset();
            for (IRBlock block : irMethod.getBlocks()) {
                if (block.getBytecodeOffset() >= tryEndOffset) {
                    tryStopBlocks.add(block);
                }
            }
        }

        // YABR routes BOTH the try and catch normal exits through ONE shared finally block, which sits between
        // the try and the catch (below the catch-all's wider span), so it would be absorbed into the try body
        // AND re-recovered by the outer sequence -> a duplicated finally. javac duplicates the finally and has
        // no such block. Find it as the non-handler successor of an inner (catch) region that ends before the
        // catch-all, and stop there so it is recovered once, as the continuation after the try-catch-finally.
        boolean sharedFinallyStopAdded = false;
        if (mainHandler.getTryEnd() != null && mainHandler.getHandlerBlock() != null) {
            Set<IRBlock> allHandlerBlocks = new HashSet<>();
            for (ExceptionHandler h : handlers) {
                if (h.getHandlerBlock() != null) {
                    allHandlerBlocks.add(h.getHandlerBlock());
                }
            }
            // YABR routes the try AND catch normal exits through ONE shared finally block sitting in the gap
            // between the try region's end and the catch-all handler. It is a MERGE (reached from the try and
            // the catch); the outer block sequence recovers it once as the continuation, so stop the try body
            // there and skip the gap re-add - otherwise it is pulled into the try body too (duplicating the
            // finally / continuation). The first such merge block in the gap is the shared finally.
            int tryEndOffset = mainHandler.getTryEnd().getBytecodeOffset();
            int handlerOffset = mainHandler.getHandlerBlock().getBytecodeOffset();
            IRBlock shared = null;
            int bestOffset = Integer.MAX_VALUE;
            for (IRBlock block : irMethod.getBlocks()) {
                int off = block.getBytecodeOffset();
                if (off >= tryEndOffset && off < handlerOffset && off < bestOffset
                        && !allHandlerBlocks.contains(block) && block.getPredecessors().size() > 1) {
                    shared = block;
                    bestOffset = off;
                }
            }
            if (shared != null) {
                tryStopBlocks.add(shared);
                sharedFinallyStopAdded = true;
            }
        }

        // Handlers whose try range sits inside this region are nested try/catch/finally structure
        // that the body recovery must build (mirroring recoverWithExceptionHandling's outer/inner
        // split); recovering the body flat would drop or misplace their clauses.
        List<ExceptionHandler> nestedHandlers = new ArrayList<>();
        if (mainHandler.getTryStart() != null && mainHandler.getTryEnd() != null) {
            int mainStart = mainHandler.getTryStart().getBytecodeOffset();
            int mainEnd = mainHandler.getTryEnd().getBytecodeOffset();
            for (ExceptionHandler h : handlers) {
                if (sameRegionHandlers.contains(h) || h.getTryStart() == null || h.getTryEnd() == null) {
                    continue;
                }
                int hStart = h.getTryStart().getBytecodeOffset();
                int hEnd = h.getTryEnd().getBytecodeOffset();
                if (hStart >= mainStart && hEnd <= mainEnd) {
                    nestedHandlers.add(h);
                }
            }
        }
        List<Statement> tryStmts;
        if (!nestedHandlers.isEmpty()) {
            for (ExceptionHandler h : nestedHandlers) {
                processedTryHandlers.add(h);
                if (h.getHandlerBlock() != null) {
                    processedHandlerBlocks.add(h.getHandlerBlock());
                }
            }
            tryStmts = recoverWithNestedHandlers(startBlock, nestedHandlers, tryStopBlocks);
        } else {
            tryStmts = recoverBlocksForTry(startBlock, tryStopBlocks, visited);
        }
        BlockStmt tryBlock = new BlockStmt(tryStmts);

        Set<String> finallyExceptionVars = new HashSet<>();
        for (ExceptionHandler h : sameRegionHandlers) {
            if (h.getHandlerBlock() != null) {
                collectCatchConsumedBlocks(h, visited);
            }
        }
        List<CatchClause> catchClauses = buildCatchClauses(sameRegionHandlers);

        if (catchClauses.isEmpty()) {
            return null;
        }

        BlockStmt finallyBlock = null;
        List<CatchClause> filteredCatches = new ArrayList<>();
        for (CatchClause clause : catchClauses) {
            if (isFinallyRethrowPattern(clause)) {
                finallyBlock = extractFinallyBody(clause);
                finallyExceptionVars.add(clause.variableName());
            } else {
                filteredCatches.add(clause);
            }
        }

        if (!finallyExceptionVars.isEmpty()) {
            tryStmts = filterOrphanFinallyThrows(tryStmts, finallyExceptionVars);

            List<Statement> finallyStmts = finallyBlock.getStatements();
            tryStmts = filterInlinedFinallyFromTryStatements(tryStmts, finallyStmts);

            // Skip the gap re-add when we stopped at a shared finally block: that block IS the normal-exit
            // finally + continuation and is now recovered once by the outer block sequence; pulling it in here
            // too would duplicate it back into the try body.
            if (!sharedFinallyStopAdded
                    && mainHandler.getTryEnd() != null && mainHandler.getHandlerBlock() != null) {
                List<Statement> gapStmts = recoverFinallyGap(
                    mainHandler.getTryEnd(), mainHandler.getHandlerBlock(), finallyBlock, finallyExceptionVars);

                if (!gapStmts.isEmpty() && !isTerminatingBlock(new BlockStmt(tryStmts))) {
                    tryStmts = new ArrayList<>(tryStmts);
                    tryStmts.addAll(gapStmts);
                }
            }

            tryBlock = new BlockStmt(tryStmts);
        }

        Value syncLock = filteredCatches.isEmpty() ? detectSynchronizedLock(mainHandler) : null;
        if (syncLock != null) {
            SynchronizedStmt sync = new SynchronizedStmt(exprRecoverer.recoverOperand(syncLock), tryBlock);
            stampFromBody(sync, tryBlock);
            return sync;
        }

        TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, filteredCatches, finallyBlock);
        stampFromBody(tryCatch, tryBlock);
        return tryCatch;
    }

    /**
     * Recovers blocks for a try region, stopping at the specified stop blocks.
     */
    private List<Statement> recoverBlocksForTry(IRBlock startBlock, Set<IRBlock> stopBlocks, Set<IRBlock> visited) {
        // The try body is a wholesale region hand-off: the enclosing try (and any nested try) is already
        // marked processed, so the RC engine structures the handler-free body or declines to this walk. It
        // marks the body blocks processed via the shared context; the caller's continuation logic keys off
        // the try-end offset and those marks, not this local visited set, so RC need not populate it.
        List<Statement> structured = rcsStructurer.tryStructureRegion(startBlock, stopBlocks);
        if (structured != null) {
            return structured;
        }
        List<Statement> result = new ArrayList<>();
        IRBlock current = startBlock;

        while (current != null && !visited.contains(current) && !stopBlocks.contains(current)) {
            visited.add(current);

            if (context.isProcessed(current)) {
                result.addAll(context.getStatements(current));
                IRBlock revisitNext = getNextSequentialBlock(current);
                if (revisitNext == null && stopBlocks.isEmpty()) {
                    // A re-visited region header (if/switch/loop) has two-plus successors, so
                    // getNextSequentialBlock stops the chain — but the fall-through past it continues
                    // along the region merges to a shared trailing return that other arms already
                    // emitted. Walk the merge chain and re-emit that return (a terminator is
                    // idempotent) without re-adding the intermediate, already-emitted blocks, which
                    // would duplicate them.
                    IRBlock chain = current;
                    Set<IRBlock> chainSeen = new HashSet<>();
                    while (chain != null && chainSeen.add(chain)) {
                        if (chain != current && isReturnBlock(chain) && context.isProcessed(chain)) {
                            result.addAll(context.getStatements(chain));
                            break;
                        }
                        RegionInfo chainInfo = analyzer.getRegionInfo(chain);
                        chain = chainInfo != null && chainInfo.getMergeBlock() != null
                                ? chainInfo.getMergeBlock()
                                : getNextSequentialBlock(chain);
                    }
                }
                current = revisitNext;
                continue;
            }

            RegionInfo info = analyzer.getRegionInfo(current);
            if (info == null) {
                List<Statement> blockStmts = recoverSimpleBlock(current);
                result.addAll(blockStmts);
                context.setStatements(current, blockStmts);
                context.markProcessed(current);
                current = getNextSequentialBlock(current);
                continue;
            }

            switch (info.getType()) {
                case IF_THEN: {
                    Statement ifStmt = recoverIfThen(current, info);
                    result.addAll(context.collectPendingStatements());
                    result.add(ifStmt);
                    current = info.getMergeBlock();
                    break;
                }
                case IF_THEN_ELSE: {
                    Statement ifStmt = recoverIfThenElse(current, info);
                    result.addAll(context.collectPendingStatements());
                    result.add(ifStmt);
                    // A merge that is a loop boundary was only reached as a post-dominator:
                    // both arms already carry their own jumps, so walking to it would append
                    // a spurious break/continue after the if/else.
                    IRBlock merge = info.getMergeBlock();
                    current = merge != null && stopBlocks.contains(merge)
                            && context.classifyLoopJump(merge) != null
                            ? null
                            : merge;
                    break;
                }
                case WHILE_LOOP: {
                    result.add(recoverWhileLoop(current, info));
                    current = findLoopExit(info, visited, new HashSet<>());
                    break;
                }
                case DO_WHILE_LOOP: {
                    result.add(recoverDoWhileLoop(current, info));
                    current = findLoopExit(info, visited, new HashSet<>());
                    break;
                }
                case FOR_LOOP: {
                    result.add(recoverForLoop(current, info));
                    current = findLoopExit(info, visited, new HashSet<>());
                    break;
                }
                case GUARD_CLAUSE: {
                    Statement guardStmt = recoverGuardClause(current, info);
                    result.addAll(context.collectPendingStatements());
                    result.add(guardStmt);
                    context.markProcessed(current);
                    current = info.getElseBlock();
                    break;
                }
                default: {
                    List<Statement> blockStmts = recoverSimpleBlock(current);
                    result.addAll(blockStmts);
                    context.setStatements(current, blockStmts);
                    context.markProcessed(current);
                    current = getNextSequentialBlock(current);
                    break;
                }
            }
        }

        // A path that exits this sequence into a loop's exit (break) or continue-target (continue) is an
        // explicit jump. The innermost loop yields an unlabeled break/continue; an enclosing loop yields a
        // labeled one. A redundant trailing `continue` to the innermost loop is stripped by the loop recovery.
        if (current != null) {
            ControlFlowContext.LoopJump jump = context.classifyLoopJump(current);
            if (jump != null) {
                String label = jump.loopHeader != null ? context.getOrCreateLabel(jump.loopHeader) : null;
                if (jump.kind == ControlFlowContext.JumpKind.BREAK) {
                    result.add(label != null ? new BreakStmt(label) : new BreakStmt());
                } else {
                    result.add(label != null ? new ContinueStmt(label) : new ContinueStmt());
                }
                return result;
            }
        }

        if (current != null && stopBlocks.contains(current) && !visited.contains(current)) {
            // Only absorb a trailing terminator (e.g. a return) that is not the shared continuation of a
            // try/catch. A return block that a try body falls into AND a catch jumps to is the continuation
            // after the try/catch, not the try's own terminator; absorbing it emits a spurious `return;` inside
            // the try. (A switch/if merge-return reached only from normal case blocks is still absorbed.)
            if (isSimpleTerminatorBlock(current)
                    && (visited.containsAll(current.getPredecessors()) || !isReachedFromCatchHandler(current))) {
                List<Statement> termStmts = recoverSimpleBlock(current);
                result.addAll(termStmts);
                visited.add(current);
            }
        }

        return result;
    }

    private boolean isSimpleTerminatorBlock(IRBlock block) {
        List<IRInstruction> instrs = block.getInstructions();
        if (instrs.isEmpty()) return false;
        int terminatorCount = 0;
        for (IRInstruction instr : instrs) {
            if (instr instanceof ReturnInstruction) {
                terminatorCount++;
            } else if (instr instanceof SimpleInstruction) {
                SimpleOp op = ((SimpleInstruction) instr).getOp();
                if (op == SimpleOp.ATHROW || op == SimpleOp.GOTO) {
                    terminatorCount++;
                }
            }
        }
        return terminatorCount == instrs.size();
    }

    /**
     * Finds the block to continue from after a try-catch region.
     */
    private IRBlock findBlockAfterTryCatch(ExceptionHandler handler, Set<IRBlock> visited) {
        IRBlock handlerBlock = handler.getHandlerBlock();
        if (handlerBlock != null) {
            for (IRBlock succ : handlerBlock.getSuccessors()) {
                if (!visited.contains(succ)) {
                    return succ;
                }
            }
        }

        int endOffset = mergedTryEndOffset(handler);
        if (endOffset >= 0) {
            IRMethod irMethod = context.getIrMethod();
            for (IRBlock block : irMethod.getBlocks()) {
                if (block.getBytecodeOffset() > endOffset && !visited.contains(block)) {
                    return block;
                }
            }
        }

        return null;
    }

    /**
     * The end offset of a try/synchronized region, widened across every exception-table entry sharing
     * this handler block. javac splits one protected range into several entries around instructions that
     * exit it (a return/break between protected sections, e.g. per {@code monitorexit} in a synchronized
     * block); the continuation lies past the LAST entry's end, so using the passed entry's own {@code
     * tryEnd} would land on a block still inside the region. Mirrors the range-merge in recoverTryCatch.
     */
    private int mergedTryEndOffset(ExceptionHandler handler) {
        int end = handler.getTryEnd() != null ? handler.getTryEnd().getBytecodeOffset() : -1;
        if (handler.getHandlerBlock() == null) {
            return end;
        }
        for (ExceptionHandler h : context.getIrMethod().getExceptionHandlers()) {
            if (h.getHandlerBlock() == handler.getHandlerBlock() && h.getTryEnd() != null) {
                end = Math.max(end, h.getTryEnd().getBytecodeOffset());
            }
        }
        return end;
    }

    private boolean isTerminatingTryCatch(TryCatchStmt tryCatch) {
        if (!isTerminatingBranch(tryCatch.getTryBlock())) {
            return false;
        }
        for (CatchClause clause : tryCatch.getCatches()) {
            if (!isTerminatingBranch(clause.body())) {
                return false;
            }
        }
        return true;
    }

    /** Whether a try/catch or synchronized statement recovered for a region leaves no normal fall-through. */
    private boolean isTerminatingRecoveredTry(Statement recovered) {
        if (recovered instanceof TryCatchStmt) {
            return isTerminatingTryCatch((TryCatchStmt) recovered);
        }
        if (recovered instanceof SynchronizedStmt) {
            return isTerminatingBranch(((SynchronizedStmt) recovered).getBody());
        }
        return false;
    }

    private boolean isTerminatingBlock(BlockStmt block) {
        if (block == null || block.getStatements().isEmpty()) {
            return false;
        }
        Statement lastStmt = block.getStatements().get(block.getStatements().size() - 1);
        return isTerminatingStatement(lastStmt);
    }

    private boolean isTerminatingStatement(Statement stmt) {
        if (stmt instanceof ReturnStmt || stmt instanceof ThrowStmt
                || stmt instanceof BreakStmt || stmt instanceof ContinueStmt) {
            return true;
        }
        if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            if (ifStmt.getElseBranch() == null) {
                return false;
            }
            return isTerminatingBranch(ifStmt.getThenBranch())
                && isTerminatingBranch(ifStmt.getElseBranch());
        }
        if (stmt instanceof TryCatchStmt) {
            return isTerminatingTryCatch((TryCatchStmt) stmt);
        }
        if (stmt instanceof BlockStmt) {
            return isTerminatingBlock((BlockStmt) stmt);
        }
        return false;
    }

    private boolean isTerminatingBranch(Statement branch) {
        if (branch instanceof BlockStmt) {
            return isTerminatingBlock((BlockStmt) branch);
        }
        return isTerminatingStatement(branch);
    }

    private boolean isAllPathsTerminating(IfStmt ifStmt) {
        if (ifStmt.getElseBranch() == null) {
            return false;
        }
        return isTerminatingBranch(ifStmt.getThenBranch())
            && isTerminatingBranch(ifStmt.getElseBranch());
    }

    /** Map from local slot name to unified type (computed from all assignments) */
    private final Map<String, SourceType> localSlotUnifiedTypes = new HashMap<>();

    /** Maps slot index to (typeCategory -> variableName) for consistent naming of reused slots */
    private final Map<Integer, Map<String, String>> slotTypeCategoryToName = new HashMap<>();

    /**
     * Emits declarations for phi variables at method scope.
     * Only phi variables (values that merge from multiple control flow paths) need
     * early declaration. Other variables are declared inline where they're defined.
     */
    private final Set<Integer> phiSlots = new HashSet<>();

    private void emitPhiDeclarations(IRMethod method, List<Statement> statements) {
        Set<SSAValue> phiValues = new LinkedHashSet<>();
        Set<String> declaredNames = new HashSet<>();

        Set<IRBlock> handlerBlocks = collectExceptionHandlerBlocks(method);

        Map<String, List<SourceType>> localSlotTypes = new HashMap<>();
        // The authoritative narrow-primitive declared type (char/byte/short/boolean) from the
        // LocalVariableTable, per name - used to override int-widening below. A CONFLICT marker means two
        // stores of one name disagree, so we don't override.
        Map<String, String> localSlotLvtNarrow = new HashMap<>();

        slotTypeCategoryToName.clear();
        phiSlots.clear();

        // PRE-PASS: Identify slots that have phis (values merging from multiple branches)
        // For these slots, we use coarse type categories so all branches share the same variable name
        for (IRBlock block : method.getBlocks()) {
            if (handlerBlocks.contains(block)) {
                continue;
            }
            for (PhiInstruction phi : block.getPhiInstructions()) {
                int localIndex = getLocalIndexFromPhi(phi);
                if (localIndex >= 0) {
                    phiSlots.add(localIndex);
                }
            }
        }

        // PASS 1: Process ALL StoreLocalInstruction to establish slot names
        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof StoreLocalInstruction) {
                    StoreLocalInstruction storeLocal = (StoreLocalInstruction) instr;
                    int localIndex = storeLocal.getLocalIndex();

                    Value storedValue = storeLocal.getValue();
                    SourceType storedType = typeRecoverer.recoverType(storedValue);

                    String localName = partitionName(storeLocal);
                    if (localName == null) {
                        localName = getNameForLocalSlotWithType(localIndex, storedType);
                    }
                    context.getExpressionContext().setLocalSlotName(localIndex, localName);

                    if (storedType != null && !storedType.isVoid() && !isNullValue(storedValue)) {
                        localSlotTypes.computeIfAbsent(localName, k -> new ArrayList<>()).add(storedType);
                        String narrow = narrowLvtDescriptor(localIndex, storeLocal.getBytecodeOffset());
                        if (narrow != null) {
                            String prev = localSlotLvtNarrow.putIfAbsent(localName, narrow);
                            if (prev != null && !prev.equals(narrow)) {
                                localSlotLvtNarrow.put(localName, "CONFLICT");
                            }
                        }
                    }

                    if (storedValue instanceof SSAValue) {
                        SSAValue sourceValue = (SSAValue) storedValue;
                        if (isUsedByArrayStore(sourceValue)) {
                            if (context.getExpressionContext().isPendingNew(sourceValue)) {
                                String className = context.getExpressionContext().consumePendingNew(sourceValue);
                                context.getExpressionContext().registerPendingNewLocalSlot(localIndex, className);
                            }
                            continue;
                        }
                        String existingName = context.getExpressionContext().getVariableName(sourceValue);
                        int existingSlot = context.getExpressionContext().getSSAValueSlot(sourceValue);
                        // Overwrite only an absent or synthetic name (a value-id like "v3"/"v3_0" or a
                        // one-letter slot name like "i5") with this store's slot name. A real name -
                        // including one that merely starts with 'v', e.g. an LVT "viewPorts" - is kept,
                        // so a copy `slot2 = viewPorts` is not clobbered into a self-reference
                        // `local2 = local2` (which reads before assignment). A store to a DIFFERENT slot than
                        // the value already belongs to is a cross-slot copy (`x1 = min(); ...; newWidth = x1`),
                        // not a redefinition: keep the value's home-slot name so the copy references it rather
                        // than renaming the value to the copy target (which turns the copy into a dropped
                        // self-store and strands its guarding branch).
                        boolean crossSlotCopy = existingName != null && existingSlot >= 0 && existingSlot != localIndex;
                        boolean shouldOverwrite = !crossSlotCopy
                                && (existingName == null
                                || isSyntheticValueName(existingName)
                                || existingName.matches("[a-z]\\d+"));
                        if (shouldOverwrite) {
                            context.getExpressionContext().setVariableName(sourceValue, localName);
                            context.getExpressionContext().markMaterialized(sourceValue);
                            context.getExpressionContext().setSSAValueSlot(sourceValue, localIndex);
                        }

                        if (context.getExpressionContext().isPendingNew(sourceValue)) {
                            String className = context.getExpressionContext().consumePendingNew(sourceValue);
                            context.getExpressionContext().registerPendingNewLocalSlot(localIndex, className);
                        }
                    }
                }
            }
        }

        // Compute unified types for each variable name AFTER PASS 1 but BEFORE PASS 2.
        // PASS 2 needs this data to correctly determine type compatibility for loads.
        localSlotUnifiedTypes.clear();
        for (Map.Entry<String, List<SourceType>> entry : localSlotTypes.entrySet()) {
            String slotName = entry.getKey();
            List<SourceType> types = entry.getValue();
            if (!types.isEmpty()) {
                SourceType unifiedType = typeRecoverer.computeCommonType(types);
                // Prefer the LocalVariableTable's declared narrow-primitive type over int-widening: a
                // char/byte/short/boolean local stored through int-shaped bytecode (e.g. a synthetic `= 0`
                // init) otherwise widens to `int`, losing the declared type and drifting from javac.
                String narrow = localSlotLvtNarrow.get(slotName);
                if (narrow != null && !"CONFLICT".equals(narrow) && unifiedType == PrimitiveSourceType.INT) {
                    unifiedType = typeRecoverer.recoverType(narrow);
                }
                localSlotUnifiedTypes.put(slotName, unifiedType);
            }
        }

        // PASS 2: Process ALL LoadLocalInstruction now that slot names are established
        // Always use the category-based name lookup to ensure loads match the correct store
        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof LoadLocalInstruction) {
                    LoadLocalInstruction loadLocal = (LoadLocalInstruction) instr;
                    if (loadLocal.getResult() != null) {
                        int localIndex = loadLocal.getLocalIndex();
                        SourceType valueType = typeRecoverer.recoverType(loadLocal.getResult());
                        String localName = partitionName(loadLocal);
                        if (localName == null) {
                            localName = getNameForLocalSlotWithType(localIndex, valueType);
                        }
                        context.getExpressionContext().setVariableName(loadLocal.getResult(), localName);
                        context.getExpressionContext().markMaterialized(loadLocal.getResult());

                        String pendingNewClass = context.getExpressionContext().consumePendingNewLocalSlot(localIndex);
                        if (pendingNewClass != null) {
                            context.getExpressionContext().registerPendingNew(loadLocal.getResult(), pendingNewClass);
                        }
                    }
                }
            }
        }

        List<PhiInstruction> phiInstructions = new ArrayList<>();
        for (IRBlock block : method.getBlocks()) {
            if (handlerBlocks.contains(block)) {
                continue;
            }

            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (phi.getResult() != null) {
                    phiValues.add(phi.getResult());
                    phiInstructions.add(phi);
                }
            }
        }

        List<SSAValue> sortedValues = sortByDependencies(phiValues);

        Map<SSAValue, PhiInstruction> valueToPhiMap = new HashMap<>();
        for (PhiInstruction phi : phiInstructions) {
            valueToPhiMap.put(phi.getResult(), phi);
        }

        for (SSAValue value : sortedValues) {
            PhiInstruction phi = valueToPhiMap.get(value);
            emitPhiDeclaration(phi, statements, declaredNames, handlerBlocks);
        }

        // Additional pass: Declare handler block phis that are used through store chains
        // These phis are skipped in the main pass but may still need declarations
        // when their values are assigned via NEW instructions
        for (IRBlock block : method.getBlocks()) {
            if (!handlerBlocks.contains(block)) {
                continue;
            }
            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (phi.getResult() != null && !phiValues.contains(phi.getResult())) {
                    String phiVarName = context.getExpressionContext().getVariableName(phi.getResult());
                    if (phiVarName != null && !phiVarName.equals("this")
                            && !isParameterOrThisRef(phi.getResult())) {
                        if (!declaredNames.contains(phiVarName) && !context.getExpressionContext().isDeclared(phiVarName)) {
                            SourceType type = computePhiUnifiedType(phi);
                            declaredNames.add(phiVarName);
                            context.getExpressionContext().markDeclared(phiVarName);
                            context.getExpressionContext().markMaterialized(phi.getResult());
                            Expression initValue = getDefaultValue(type);
                            statements.add(new VarDeclStmt(type, phiVarName, initValue));
                        }
                    }
                }
            }
        }
    }

    /** A synthetic SSA value name ("v" followed by a digit, e.g. {@code v3} or {@code v3_0}) - not a real local name. */
    private static boolean isSyntheticValueName(String name) {
        return name.length() > 1 && name.charAt(0) == 'v' && Character.isDigit(name.charAt(1));
    }

    /**
     * The LocalVariableTable declared type at a store, when it is a narrow primitive (char/byte/short/boolean)
     * - the sub-int types that int-shaped bytecode would otherwise lose. A store makes its slot live at the
     * following pc, so the entry's scope typically begins just after the store; a small forward window catches
     * it. Returns null when there is no such entry (no debug info, or the slot holds a wider type there).
     */
    private String narrowLvtDescriptor(int slot, int offset) {
        RecoveryContext ctx = context.getExpressionContext();
        for (int d = 0; d <= 3; d++) {
            String desc = ctx.debugDescriptorAt(slot, offset + d);
            if (desc != null && desc.length() == 1 && "ZBCS".indexOf(desc.charAt(0)) >= 0) {
                return desc;
            }
        }
        return null;
    }

    /**
     * Gets the unified type for a local slot, or null if not computed.
     */
    public SourceType getLocalSlotUnifiedType(String slotName) {
        return localSlotUnifiedTypes.get(slotName);
    }

    /**
     * Emits a phi variable declaration with default value.
     * Uses unified type from all incoming phi values.
     */
    private void emitPhiDeclaration(PhiInstruction phi, List<Statement> statements, Set<String> declaredNames,
                                    Set<IRBlock> handlerBlocks) {
        if (phi == null) return;
        SSAValue result = phi.getResult();
        if (result == null) return;

        // A dead phi that merges a caught exception (an operand defined in a catch handler) with the
        // try-path's undefined slot value is the catch variable's reused slot bridging the shared finally
        // join. The catch variable is declared by its own catch clause, so declaring this phi too emits a
        // spurious top-level `Exception e = null`. (Restricted to dead + handler-sourced so the load-bearing
        // pattern-switch dead phis, which have no handler operand, still declare.)
        if (isDeadCatchVarPhi(phi, handlerBlocks)) {
            return;
        }

        // A phi that merges a primitive with a reference is a type-pun across a reused JVM
        // slot; it is only verifier-legal because its result is dead. Declaring it would
        // unify the operands to Object and mis-type the slot (e.g. Object local5 = null while
        // the slot is really an int). Skip it so the slot is declared by its actual stores.
        // Coherent dead phis (all-reference or all-primitive) still declare normally, since
        // they carry the slot's correct unified type.
        if (isTypePunDeadPhi(phi)) {
            return;
        }

        if (selfStorePhis.contains(phi)) {
            return;
        }

        if (isForLoopInductionPhi(phi)) {
            return;
        }

        SourceType phiType = computePhiUnifiedType(phi);

        String name = partitionName(phi);
        String nameFromMethodRecoverer = context.getExpressionContext().getVariableName(result);
        if (name == null) {
            if (nameFromMethodRecoverer != null && nameFromMethodRecoverer.startsWith("local")) {
                name = nameFromMethodRecoverer;
            } else {
                int localIndex = getLocalIndexFromPhi(phi);
                if (localIndex >= 0) {
                    name = getNameForLocalSlotWithType(localIndex, phiType);
                }
                if (name == null) {
                    name = nameFromMethodRecoverer;
                }
            }
        }
        if (name == null) {
            name = "v" + result.getId();
        }

        if ("this".equals(name) || isParameterOrThisRef(result)) {
            return;
        }

        // The declared type must match the variable this phi shares a name with: prefer the
        // unified type of the stores carrying this name (the partition component) so a phi the
        // SSA bridges across a heterogeneous merge does not mistype an unrelated slot variable.
        SourceType type = localSlotUnifiedTypes.getOrDefault(name, phiType);

        boolean upgradedToBoolean = false;
        if (type == PrimitiveSourceType.INT && phiReceivesBooleanConstantsOnly(phi)) {
            type = PrimitiveSourceType.BOOLEAN;
            upgradedToBoolean = true;
        }

        if (declaredNames.contains(name) || context.getExpressionContext().isDeclared(name)) {
            return;
        }

        declaredNames.add(name);
        context.getExpressionContext().markDeclaredWithType(name, type);
        context.getExpressionContext().markMaterialized(result);
        context.getExpressionContext().setVariableName(result, name);

        if (upgradedToBoolean) {
            localSlotUnifiedTypes.put(name, PrimitiveSourceType.BOOLEAN);
        }

        Expression initValue = getDefaultValue(type);
        boolean entryApplied = false;
        // A loop-carried phi's declaration should be initialized with its pre-loop (entry) value, not a
        // default: `int s = first` for `s = phi(first, s + r)`. Without this the entry value is lost
        // (e.g. an accumulator seeded from a parameter starts at 0). Only simple entry values (constant,
        // parameter, or a local load) are inlined, to avoid duplicating a side-effecting expression.
        Value entryInput = findLoopEntryInput(phi);
        if (isSafeEntryInit(entryInput) && entryDominatesPhi(entryInput, phi)) {
            Expression entryExpr = exprRecoverer.recoverOperand(entryInput, type);
            if (isSelfReference(entryExpr, name)) {
                // The entry value is materialized under this phi's own slot name, so recoverOperand
                // returns a reference to the variable being declared. Recover its underlying constant
                // directly so the entry value is not lost to a `T v = v` self-initializer (which a later
                // pass then resolves to the loop-body store, e.g. `boolean captured = true`).
                entryExpr = recoverEntryConstant(entryInput, type);
            }
            if (entryExpr != null) {
                initValue = entryExpr;
                entryApplied = true;
            }
        }
        // A merge phi whose dominating (pre-branch) operand is a side-effecting value that is ALSO consumed
        // elsewhere - `boolean result = base(s); if (result) {...} return result;`, where base(s) is both the
        // phi's entry operand and the branch condition - cannot inline that value as the initializer (it would
        // evaluate base() twice). Instead bind the value to this phi's variable, materialize it, and pin it, so
        // its single definition emits `result = base(s)` in place and the condition and phi both read `result`.
        // Without this the value is inlined into the condition, its store is lost, and the variable is undeclared.
        if (!entryApplied) {
            Value dominating = findDominatingOperand(phi);
            if (dominating instanceof SSAValue && !isSafeEntryInit(dominating)
                    && entryDominatesPhi(dominating, phi)
                    && !context.getExpressionContext().isMaterialized((SSAValue) dominating)) {
                SSAValue dominatingValue = (SSAValue) dominating;
                context.getExpressionContext().setVariableName(dominatingValue, name);
                context.getExpressionContext().markMaterialized(dominatingValue);
                context.getExpressionContext().pinToVariable(dominatingValue);
            }
        }
        statements.add(new VarDeclStmt(type, name, initValue));
    }

    /**
     * The single distinct phi operand whose definition dominates the phi's block - the value the variable
     * holds on entry to the merge, before any branch reassigns it. Null when no operand, or more than one
     * distinct value, dominates. The same value arriving from several predecessors still counts as one.
     */
    private Value findDominatingOperand(PhiInstruction phi) {
        IRBlock phiBlock = phi.getBlock();
        if (phiBlock == null) {
            return null;
        }
        Set<Value> dominating = new HashSet<>();
        for (Value in : phi.getIncomingValues().values()) {
            if (!(in instanceof SSAValue)) {
                continue;
            }
            IRInstruction def = ((SSAValue) in).getDefinition();
            IRBlock defBlock = def != null ? def.getBlock() : null;
            if (defBlock != null && analyzer.getDominatorTree().dominates(defBlock, phiBlock)) {
                dominating.add(in);
            }
        }
        return dominating.size() == 1 ? dominating.iterator().next() : null;
    }

    /**
     * Whether the entry input genuinely enters the loop from outside: its definition dominates the
     * phi's block. A merge phi inside the loop body (e.g. one whose non-recursive incoming is a
     * mid-loop assignment) has a non-dominating "entry" that must not seed the slot's declaration.
     */
    private boolean entryDominatesPhi(Value entryInput, PhiInstruction phi) {
        IRBlock phiBlock = phi.getBlock();
        if (phiBlock == null) {
            return false;
        }
        if (entryInput instanceof Constant) {
            return true;
        }
        if (entryInput instanceof SSAValue) {
            IRInstruction def = ((SSAValue) entryInput).getDefinition();
            if (def == null) {
                return true;
            }
            IRBlock defBlock = def.getBlock();
            return defBlock != null && analyzer.getDominatorTree().dominates(defBlock, phiBlock);
        }
        return false;
    }

    /** Whether {@code expr} is a reference to the variable {@code name} - a self-initializer to reject. */
    private boolean isSelfReference(Expression expr, String name) {
        return expr instanceof VarRefExpr && name != null
                && name.equals(((VarRefExpr) expr).getName());
    }

    /**
     * Recovers a constant-backed entry value as a literal, bypassing slot materialization. Returns null
     * when the entry input is not constant-backed, leaving the declaration's default initializer.
     */
    private Expression recoverEntryConstant(Value entryInput, SourceType type) {
        Constant c = null;
        if (entryInput instanceof Constant) {
            c = (Constant) entryInput;
        } else if (entryInput instanceof SSAValue) {
            IRInstruction def = ((SSAValue) entryInput).getDefinition();
            if (def instanceof ConstantInstruction) {
                c = ((ConstantInstruction) def).getConstant();
            }
        }
        return c != null ? exprRecoverer.recoverConstant(c, type) : null;
    }

    /** For a loop-carried phi (exactly one input recurses through the phi result), returns the entry input; else null. */
    private Value findLoopEntryInput(PhiInstruction phi) {
        SSAValue result = phi.getResult();
        if (result == null) {
            return null;
        }
        Value entry = null;
        int entryCount = 0;
        int recursiveCount = 0;
        for (Value in : phi.getIncomingValues().values()) {
            if (valueReaches(in, result, new HashSet<>())) {
                recursiveCount++;
            } else {
                entry = in;
                entryCount++;
            }
        }
        return (entryCount == 1 && recursiveCount >= 1) ? entry : null;
    }

    private boolean valueReaches(Value v, SSAValue target, Set<Value> seen) {
        if (v == target) {
            return true;
        }
        if (!(v instanceof SSAValue) || !seen.add(v)) {
            return false;
        }
        IRInstruction def = ((SSAValue) v).getDefinition();
        if (def == null) {
            return false;
        }
        for (Value op : def.getOperands()) {
            if (valueReaches(op, target, seen)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSafeEntryInit(Value v) {
        if (v instanceof Constant) {
            return true;
        }
        if (v instanceof SSAValue) {
            IRInstruction def = ((SSAValue) v).getDefinition();
            return def == null
                    || def instanceof ConstantInstruction
                    || def instanceof LoadLocalInstruction;
        }
        return false;
    }

    /**
     * Checks if a PHI instruction corresponds to a for-loop induction variable.
     * Such PHIs should not have their declaration emitted early since the variable
     * will be declared inline in the for-loop initialization.
     * <p>
     * Uses two checks:
     * 1. Direct PHI result marking (always applies)
     * 2. Local index check, but ONLY if the PHI is in a for-loop header block
     *    (this prevents slot reuse bugs where a later PHI for the same slot
     *    would be incorrectly skipped)
     */
    private boolean isForLoopInductionPhi(PhiInstruction phi) {
        SSAValue result = phi.getResult();
        if (result != null && context.isForLoopInductionPhi(result)) {
            return true;
        }
        IRBlock phiBlock = phi.getBlock();
        if (phiBlock != null && context.isForLoopHeader(phiBlock)) {
            int localIndex = getLocalIndexFromPhi(phi);
            return localIndex >= 0 && context.isForLoopInductionLocal(localIndex);
        }
        return false;
    }

    private int getLocalIndexFromPhi(PhiInstruction phi) {
        SSAValue result = phi.getResult();
        if (result == null) return -1;

        String varName = context.getExpressionContext().getVariableName(result);
        if (varName != null && varName.startsWith("local")) {
            try {
                return Integer.parseInt(varName.substring(5));
            } catch (NumberFormatException ignored) {
            }
        }

        for (Value incoming : phi.getOperands()) {
            if (incoming instanceof SSAValue) {
                SSAValue ssaVal = (SSAValue) incoming;
                IRInstruction def = ssaVal.getDefinition();
                if (def instanceof LoadLocalInstruction) {
                    return ((LoadLocalInstruction) def).getLocalIndex();
                }
                if (def instanceof StoreLocalInstruction) {
                    return ((StoreLocalInstruction) def).getLocalIndex();
                }
            }
        }

        for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
            IRBlock predBlock = entry.getKey();
            Value incomingValue = entry.getValue();
            for (IRInstruction instr : predBlock.getInstructions()) {
                if (instr instanceof StoreLocalInstruction) {
                    StoreLocalInstruction store = (StoreLocalInstruction) instr;
                    if (store.getValue() == incomingValue) {
                        return store.getLocalIndex();
                    }
                }
            }
        }

        return -1;
    }

    /**
     * True when {@code value} is stored to the slot the {@code phi} represents - a branch's own StoreLocal
     * already assigns the phi variable, so materializing the phi value as a separate `phiVar = value` copy
     * would duplicate that store (e.g. javac's `s = new X(); result = s` where both s and result get one
     * `new X` value: the result store assigns the phi, the extra phi copy must not be emitted).
     */
    private boolean isStoredToPhiSlot(SSAValue value, PhiInstruction phi) {
        int slot = getLocalIndexFromPhi(phi);
        if (slot < 0 || value == null) {
            return false;
        }
        for (IRInstruction use : value.getUses()) {
            if (use instanceof StoreLocalInstruction
                    && ((StoreLocalInstruction) use).getLocalIndex() == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a freshly-constructed {@code value} genuinely belongs to the variable {@code phiVarName}
     * that a phi merges it into. A JVM slot reused for two variables can leave a value from one
     * partition ({@code max}) feeding the phi of the other ({@code child}); materializing it under the
     * phi's name would emit a wrong-typed assignment (e.g. {@code child = new Vector3f()}). The value's
     * own store partition is authoritative, so require it to match. Unknown partitions do not veto.
     */
    private boolean valueBelongsToPhiVariable(SSAValue value, String phiVarName) {
        if (value == null || phiVarName == null) {
            return true;
        }
        for (IRInstruction use : value.getUses()) {
            if (use instanceof StoreLocalInstruction) {
                String storeName = partitionName(use);
                if (storeName != null) {
                    return storeName.equals(phiVarName);
                }
            }
        }
        return true;
    }

    /**
     * The local slot a value belongs to - its parameter slot, the slot it was stored to, or the slot of its
     * defining local load/store/phi - or -1 when it is not a local-slot value. Lets a variable's role be
     * decided from its slot layout rather than from its (now real) recovered name.
     */
    private int slotOfValue(SSAValue value) {
        if (value == null) {
            return -1;
        }
        RecoveryContext ctx = context.getExpressionContext();
        int paramSlot = ctx.parameterSlot(value);
        if (paramSlot >= 0) {
            return paramSlot;
        }
        int stored = ctx.getSSAValueSlot(value);
        if (stored >= 0) {
            return stored;
        }
        IRInstruction def = value.getDefinition();
        if (def instanceof LoadLocalInstruction) {
            return ((LoadLocalInstruction) def).getLocalIndex();
        }
        if (def instanceof StoreLocalInstruction) {
            return ((StoreLocalInstruction) def).getLocalIndex();
        }
        if (def instanceof PhiInstruction) {
            return getLocalIndexFromPhi((PhiInstruction) def);
        }
        return -1;
    }

    /** True when {@code value} refers to the receiver or a parameter (decided by its local slot). */
    private boolean isParameterOrThisRef(SSAValue value) {
        return context.getExpressionContext().isParameterOrThisSlot(slotOfValue(value));
    }

    /**
     * Whether {@code block} is reached from a catch handler - i.e. one of its predecessors lies in some
     * exception handler's region. Used to tell a shared try/catch continuation (the catch jumps to it) from
     * an ordinary sequence terminator, so the former is recovered after the try/catch rather than inside it.
     */
    private boolean isReachedFromCatchHandler(IRBlock block) {
        Set<IRBlock> handlerRegion = new HashSet<>();
        for (ExceptionHandler h : context.getIrMethod().getExceptionHandlers()) {
            if (h.getHandlerBlock() != null && handlerRegion.add(h.getHandlerBlock())) {
                collectReachableBlocks(h.getHandlerBlock(), handlerRegion);
            }
        }
        for (IRBlock pred : block.getPredecessors()) {
            if (handlerRegion.contains(pred)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDeadCatchVarPhi(PhiInstruction phi, Set<IRBlock> handlerBlocks) {
        SSAValue result = phi.getResult();
        if (result == null || !result.getUses().isEmpty()) {
            return false;
        }
        SourceType type = typeRecoverer.recoverType(result);
        if (type == null || type.isPrimitive() || type.isVoid()) {
            return false;
        }
        // A reference-typed dead phi that either has no real incoming value (the catch variable's reused slot
        // is undefined on the try path and its catch def was elided) or merges a value defined in a catch
        // handler is the catch variable bridging a shared finally join - already declared by its catch clause.
        boolean hasOperand = false;
        for (Value op : phi.getOperands()) {
            if (op instanceof SSAValue) {
                hasOperand = true;
                IRInstruction def = ((SSAValue) op).getDefinition();
                if (def != null && def.getBlock() != null && handlerBlocks != null
                        && handlerBlocks.contains(def.getBlock())) {
                    return true;
                }
            }
        }
        return !hasOperand;
    }

    private boolean isTypePunDeadPhi(PhiInstruction phi) {
        SSAValue result = phi.getResult();
        if (result == null || !result.getUses().isEmpty()) {
            return false;
        }
        boolean hasPrimitive = false;
        boolean hasReference = false;
        for (Value value : phi.getOperands()) {
            SourceType type = typeRecoverer.recoverType(value);
            if (type == null || type.isVoid()) {
                continue;
            }
            if (type.isPrimitive()) {
                hasPrimitive = true;
            } else {
                hasReference = true;
            }
        }
        return hasPrimitive && hasReference;
    }

    private SourceType computePhiUnifiedType(PhiInstruction phi) {
        SSAValue result = phi.getResult();

        List<SourceType> incomingTypes = new ArrayList<>();
        for (Value value : phi.getOperands()) {
            if (isNullValue(value)) {
                continue;
            }
            SourceType valueType = typeRecoverer.recoverType(value);
            if (valueType != null && !valueType.isVoid()) {
                incomingTypes.add(valueType);
            }
        }

        if (!incomingTypes.isEmpty()) {
            return typeRecoverer.computeCommonType(incomingTypes);
        }

        String localName = context.getExpressionContext().getVariableName(result);
        if (localName != null && localSlotUnifiedTypes.containsKey(localName)) {
            return localSlotUnifiedTypes.get(localName);
        }

        return typeRecoverer.recoverType(result);
    }

    /**
     * Collects exception handler ENTRY blocks only.
     * Only the handler entry block should be skipped for phi declarations,
     * as it contains the exception-related phi (for the caught exception).
     * Normal control flow merge blocks that are reachable from handlers
     * should NOT be skipped - they contain regular value phis that need declaration.
     */
    private Set<IRBlock> collectExceptionHandlerBlocks(IRMethod method) {
        Set<IRBlock> handlerBlocks = new HashSet<>();
        List<ExceptionHandler> handlers = method.getExceptionHandlers();
        if (handlers == null || handlers.isEmpty()) {
            return handlerBlocks;
        }

        for (ExceptionHandler handler : handlers) {
            IRBlock handlerBlock = handler.getHandlerBlock();
            if (handlerBlock != null) {
                handlerBlocks.add(handlerBlock);
            }
        }
        return handlerBlocks;
    }

    /**
     * Sorts SSA values so that values are declared after their dependencies.
     * Uses topological sort based on def-use chains.
     */
    private List<SSAValue> sortByDependencies(Set<SSAValue> values) {
        List<SSAValue> result = new ArrayList<>();
        Set<SSAValue> visited = new HashSet<>();
        Set<SSAValue> inProgress = new HashSet<>();

        for (SSAValue value : values) {
            visitForSort(value, values, visited, inProgress, result);
        }

        return result;
    }

    private void visitForSort(SSAValue value, Set<SSAValue> allValues,
                              Set<SSAValue> visited, Set<SSAValue> inProgress,
                              List<SSAValue> result) {
        if (visited.contains(value)) return;
        if (inProgress.contains(value)) return;

        inProgress.add(value);

        IRInstruction def = value.getDefinition();
        if (def != null) {
            for (Value operand : def.getOperands()) {
                if (operand instanceof SSAValue) {
                    SSAValue ssaDep = (SSAValue) operand;
                    if (allValues.contains(ssaDep)) {
                        visitForSort(ssaDep, allValues, visited, inProgress, result);
                    }
                }
            }
        }

        inProgress.remove(value);
        visited.add(value);
        result.add(value);
    }

    /**
     * Checks if an SSA value is an intermediate value that should be inlined.
     * Intermediate values are those used only by other instructions (not stored to fields/arrays/locals).
     */
    private boolean isIntermediateValue(SSAValue value) {
        if (value == null) return false;

        IRInstruction def = value.getDefinition();
        if (def instanceof InvokeInstruction && shouldStoreMethodResult((InvokeInstruction) def, value)) {
            return false;
        }

        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.isEmpty()) return true;

        // The value is intermediate (inlinable at its use) unless some use needs it to have a home
        // variable: a branch/phi (recovered as a named condition/merge), a store to a local, or a
        // store to a field/array. Every other use kind (invoke arg, return, arithmetic, type check,
        // field/array LOAD) consumes the value inline and does not force materialization.
        for (IRInstruction use : uses) {
            if (use instanceof BranchInstruction
                    || use instanceof StoreLocalInstruction
                    || use instanceof PhiInstruction) {
                return false;
            }
            if (use instanceof FieldAccessInstruction && ((FieldAccessInstruction) use).isStore()) {
                return false;
            }
            if (use instanceof ArrayAccessInstruction && ((ArrayAccessInstruction) use).isStore()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a call result must be forced into a named temporary instead of inlined. Only multi-use
     * receivers qualify: a single-use value is always inlined at its sole use site by the expression
     * recoverer, so materializing it here is futile and leaves an orphaned, re-inlined statement once
     * dead-variable elimination strips the unread declaration.
     */
    private boolean shouldStoreMethodResult(InvokeInstruction invoke, SSAValue result) {
        if (result == null || result.getType() == null) return false;
        if (result.getUses().size() <= 1) return false;

        String methodName = invoke.getName();
        if (isImportantMethodName(methodName)) {
            return isUsedAsMethodReceiver(result);
        }
        return false;
    }

    private boolean isImportantMethodName(String methodName) {
        if (methodName == null) return false;
        if (methodName.startsWith("get") && methodName.length() > 3) return true;
        if (methodName.startsWith("find") && methodName.length() > 4) return true;
        if (methodName.startsWith("load") && methodName.length() > 4) return true;
        if (methodName.startsWith("create") && methodName.length() > 6) return true;
        if (methodName.startsWith("compute") && methodName.length() > 7) return true;
        if (methodName.startsWith("read") && methodName.length() > 4) return true;
        if (methodName.startsWith("fetch") && methodName.length() > 5) return true;
        return methodName.startsWith("retrieve") && methodName.length() > 8;
    }

    private boolean isUsedAsMethodReceiver(SSAValue result) {
        for (IRInstruction use : result.getUses()) {
            if (use instanceof InvokeInstruction) {
                InvokeInstruction invoke = (InvokeInstruction) use;
                if (invoke.getInvokeType() != InvokeType.STATIC) {
                    java.util.List<Value> args = invoke.getArguments();
                    if (!args.isEmpty() && args.get(0) == result) {
                        SSAValue invokeResult = invoke.getResult();
                        if (isMethodChainIntermediate(invokeResult)) {
                            continue;
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isMethodChainIntermediate(SSAValue value) {
        if (value == null) return false;
        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.isEmpty()) return true;
        for (IRInstruction use : uses) {
            if (use instanceof InvokeInstruction) {
                InvokeInstruction invoke = (InvokeInstruction) use;
                java.util.List<Value> args = invoke.getArguments();
                if (!args.isEmpty() && args.get(0) == value) {
                    SSAValue invokeResult = invoke.getResult();
                    if (isMethodChainIntermediate(invokeResult)) {
                        continue;
                    }
                }
                continue;
            }
            if (use instanceof ReturnInstruction) continue;
            if (use instanceof BranchInstruction) continue;
            return false;
        }
        return true;
    }

    /**
     * Checks if an SSA value is used exactly once and that use is a FieldAccessInstruction store.
     * Such values can be safely inlined into the field assignment.
     */
    private boolean isSingleUsePutField(SSAValue value) {
        if (value == null) return false;
        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.size() != 1) return false;
        IRInstruction use = uses.get(0);
        if (use instanceof FieldAccessInstruction) {
            FieldAccessInstruction fa = (FieldAccessInstruction) use;
            return fa.isStore();
        }
        return false;
    }

    private boolean isSingleUsePhiOperand(SSAValue value) {
        if (value == null) return false;
        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.size() != 1) return false;
        IRInstruction use = uses.get(0);
        return use instanceof PhiInstruction;
    }

    /**
     * Checks if an SSA value is used by a StoreLocalInstruction.
     * In this case, the instruction producing the value should be skipped
     * and let StoreLocalInstruction handle the emission. The StoreLocal
     * will create the local variable, and other uses will reference that local.
     */
    private boolean isUsedByStoreLocal(SSAValue value) {
        if (value == null) return false;
        return isUsedByStoreLocalWithVisited(value, new HashSet<>());
    }

    private boolean isUsedByStoreLocalWithVisited(SSAValue value, Set<SSAValue> visited) {
        if (value == null || !visited.add(value)) return false;

        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.isEmpty()) return false;

        for (IRInstruction use : uses) {
            if (use instanceof StoreLocalInstruction) {
                return true;
            }
            if (use instanceof CopyInstruction) {
                CopyInstruction copy = (CopyInstruction) use;
                if (copy.getResult() != null) {
                    if (isUsedByStoreLocalWithVisited(copy.getResult(), visited)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Gets the PHI instruction that uses this value, if any.
     * Returns the first PHI instruction found that uses this value.
     */
    private PhiInstruction getPhiUsingValue(SSAValue value) {
        if (value == null) return null;
        return getPhiUsingValueWithVisited(value, new HashSet<>());
    }

    /** Whether the value is stored into a local, materializing it as a real assignment statement. */
    private boolean hasStoreLocalUse(SSAValue value) {
        if (value == null) {
            return false;
        }
        for (IRInstruction use : value.getUses()) {
            if (use instanceof StoreLocalInstruction) {
                return true;
            }
        }
        return false;
    }

    private PhiInstruction getPhiUsingValueWithVisited(SSAValue value, Set<SSAValue> visited) {
        if (value == null || !visited.add(value)) return null;

        java.util.List<IRInstruction> uses = value.getUses();
        for (IRInstruction use : uses) {
            if (use instanceof PhiInstruction) {
                // A degenerate phi (all incoming values identical) carries no merge information and
                // must not drive an assignment statement: doing so duplicates the store that already
                // materializes the value (e.g. spurious phi(B:v, C:v) on a local written in one branch).
                if (isDegeneratePhi((PhiInstruction) use)) {
                    continue;
                }
                return (PhiInstruction) use;
            }
            if (use instanceof CopyInstruction) {
                CopyInstruction copy = (CopyInstruction) use;
                if (copy.getResult() != null) {
                    PhiInstruction phi = getPhiUsingValueWithVisited(copy.getResult(), visited);
                    if (phi != null) {
                        return phi;
                    }
                }
            }
        }
        return null;
    }

    /**
     * A phi is degenerate when all its incoming values are the same {@link Value} (or it has a
     * single incoming). Such a phi is redundant — it is not a genuine control-flow merge of
     * distinct definitions — and must not be treated as a variable that needs its own assignment.
     */
    private static boolean isDegeneratePhi(PhiInstruction phi) {
        Value common = null;
        boolean first = true;
        for (Value incoming : phi.getIncomingValues().values()) {
            if (first) {
                common = incoming;
                first = false;
            } else if (incoming != common) {
                return false;
            }
        }
        return true;
    }

    private PhiInstruction getPhiThroughStoreChain(SSAValue value) {
        if (value == null) return null;

        for (IRInstruction use : value.getUses()) {
            if (use instanceof StoreLocalInstruction) {
                StoreLocalInstruction store = (StoreLocalInstruction) use;
                int slot = store.getLocalIndex();

                for (IRBlock block : context.getIrMethod().getBlocks()) {
                    for (IRInstruction instr : block.getInstructions()) {
                        if (instr instanceof LoadLocalInstruction) {
                            LoadLocalInstruction load = (LoadLocalInstruction) instr;
                            if (load.getLocalIndex() == slot && load.getResult() != null) {
                                PhiInstruction phi = getPhiUsingValue(load.getResult());
                                if (phi != null) {
                                    return phi;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private SSAValue findNewInstructionValue(SSAValue value) {
        if (value == null) return null;
        Set<SSAValue> visited = new HashSet<>();
        SSAValue current = value;
        while (visited.add(current)) {
            IRInstruction def = current.getDefinition();
            if (def instanceof NewInstruction) {
                return current;
            }
            if (def instanceof CopyInstruction) {
                Value source = ((CopyInstruction) def).getSource();
                if (source instanceof SSAValue) {
                    current = (SSAValue) source;
                    continue;
                }
            }
            break;
        }
        return null;
    }

    /** Whether {@code v} is a compile-time constant operand (a bare constant or a constant SSA def). */
    private static boolean isConstantOperand(Value v) {
        return v instanceof Constant
                || (v instanceof SSAValue
                    && ((SSAValue) v).getDefinition() instanceof ConstantInstruction);
    }

    private boolean isUsedByArrayStore(SSAValue value) {
        if (value == null) return false;

        java.util.List<IRInstruction> uses = value.getUses();
        for (IRInstruction use : uses) {
            if (use instanceof ArrayAccessInstruction) {
                ArrayAccessInstruction aa = (ArrayAccessInstruction) use;
                if (aa.isStore() && aa.getArray() == value) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True if {@code value} is consumed solely as the stored value of a single array
     * store ({@code arr[i] = value}). Such a value should be inlined into that store
     * rather than also emitted as a standalone (result-discarding) statement.
     */
    private boolean isSingleUseArrayStoreValue(SSAValue value) {
        if (value == null || value.getUses().size() != 1) return false;
        IRInstruction use = value.getUses().get(0);
        if (use instanceof ArrayAccessInstruction) {
            ArrayAccessInstruction aa = (ArrayAccessInstruction) use;
            return aa.isStore() && aa.getValue() == value;
        }
        return false;
    }

    /**
     * Gets the local variable name from the StoreLocalInstruction that stores this value.
     * Used to emit array declarations at the right position with the correct name.
     */
    private String getLocalNameFromStoreLocal(SSAValue value) {
        if (value == null) return null;

        java.util.List<IRInstruction> uses = value.getUses();
        for (IRInstruction use : uses) {
            if (use instanceof StoreLocalInstruction) {
                StoreLocalInstruction store = (StoreLocalInstruction) use;
                int localIndex = store.getLocalIndex();
                SourceType valueType = typeRecoverer.recoverType(value);
                String name = partitionName(store);
                if (name == null) {
                    name = getNameForLocalSlotWithType(localIndex, valueType);
                }
                if (name != null && context.getExpressionContext().isDeclared(name)) {
                    SourceType declaredType = context.getExpressionContext().getDeclaredType(name);
                    if (declaredType != null && !typesAreCompatibleForDeclaration(declaredType, valueType)) {
                        name = generateUniqueLocalName(localIndex);
                    }
                }
                return name;
            }
        }
        return null;
    }

    private boolean typesAreCompatibleForDeclaration(SourceType type1, SourceType type2) {
        if (type1 == null || type2 == null) {
            return true;
        }
        if (type1.equals(type2)) {
            return true;
        }
        boolean type1Array = type1 instanceof ArraySourceType;
        boolean type2Array = type2 instanceof ArraySourceType;
        return type1Array == type2Array;
    }

    private final Set<PhiInstruction> selfStorePhis = new HashSet<>();

    private void detectSelfStorePhis(IRMethod method) {
        selfStorePhis.clear();
        for (IRBlock block : method.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (isSelfStorePhiPattern(phi)) {
                    selfStorePhis.add(phi);
                    markSelfStorePhiChain(phi);
                }
            }
        }
    }

    private void markSelfStorePhiChain(PhiInstruction phi) {
        FieldAccessInstruction fieldLoad = findFieldLoadInPhiChain(phi, new HashSet<>());
        if (fieldLoad == null) return;

        SourceType fieldType = typeRecoverer.recoverType(fieldLoad.getDescriptor());
        Expression fieldExpr = new FieldAccessExpr(
            null, fieldLoad.getName(), fieldLoad.getOwner(), fieldLoad.isStatic(), fieldType)
            .withDescriptor(fieldLoad.getDescriptor());

        Set<PhiInstruction> visited = new HashSet<>();
        cacheFieldExprForPhiChain(phi, fieldExpr, visited);
    }

    private void cacheFieldExprForPhiChain(PhiInstruction phi, Expression fieldExpr, Set<PhiInstruction> visited) {
        if (phi == null || visited.contains(phi)) return;
        visited.add(phi);

        if (phi.getResult() != null) {
            context.getExpressionContext().cacheExpression(phi.getResult(), fieldExpr);
        }

        for (Value operand : phi.getOperands()) {
            if (operand instanceof SSAValue) {
                SSAValue ssaOp = (SSAValue) operand;
                IRInstruction def = ssaOp.getDefinition();
                if (def instanceof PhiInstruction) {
                    PhiInstruction nestedPhi = (PhiInstruction) def;
                    selfStorePhis.add(nestedPhi);
                    cacheFieldExprForPhiChain(nestedPhi, fieldExpr, visited);
                }
            }
        }
    }

    private boolean isSelfStorePhiPattern(PhiInstruction phi) {
        if (phi == null || phi.getResult() == null) return false;

        FieldAccessInstruction fieldLoad = findFieldLoadInPhiChain(phi, new HashSet<>());
        if (fieldLoad == null) {
            return false;
        }

        // The recovery renders this field cursor with an implicit (null) receiver, which only
        // reproduces the original access for a static field or a `this`-receiver field. An instance
        // field whose receiver is the cursor itself (e.g. `e.next` where `e` is this phi) would lose
        // its receiver and collapse to `this.next`; such a phi is an ordinary local, not a cursor.
        if (!selfStoreFieldReceiverIsImplicit(fieldLoad)) {
            return false;
        }

        String fieldOwner = fieldLoad.getOwner();
        String fieldName = fieldLoad.getName();

        return hasFieldStoreInPhiUseChain(phi, fieldOwner, fieldName, new HashSet<>());
    }

    /**
     * True when {@code fieldLoad}'s receiver matches the implicit (null) receiver the self-store
     * recovery emits: a static field, or an instance field accessed on {@code this}.
     */
    private boolean selfStoreFieldReceiverIsImplicit(FieldAccessInstruction fieldLoad) {
        if (fieldLoad.isStatic()) {
            return true;
        }
        Value receiver = fieldLoad.getObjectRef();
        if (!(receiver instanceof SSAValue)) {
            return false;
        }
        return !context.getIrMethod().isStatic() && slotOfValue((SSAValue) receiver) == 0;
    }

    /**
     * True when {@code result} (a field load feeding {@code targetPhi}) is written by a store_local to
     * a source variable other than the phi's own. The store is then the value's real assignment, and a
     * phi copy naming the phi's variable would be a spurious cross-variable one — either a redundant
     * advance (`e = e.next` beside the store `e = next` in the coalesced `next = e.next; e = next`
     * idiom) or a type-punned slot reuse (`i = model` where the reused JVM slot later holds the
     * {@code sceneModel} Spatial). Suppress the copy; the store owns the value. Names come from the
     * reaching-definition partition, which splits a reused slot into its distinct source variables.
     */
    private boolean fieldLoadValueBelongsToOtherVariable(SSAValue result, PhiInstruction targetPhi) {
        String phiName = context.getExpressionContext().getVariableName(targetPhi.getResult());
        if (phiName == null) {
            return false;
        }
        for (IRInstruction use : result.getUses()) {
            if (use instanceof StoreLocalInstruction) {
                String storeName = partitionName(use);
                if (storeName != null && !storeName.equals(phiName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasFieldStoreInPhiUseChain(PhiInstruction phi, String fieldOwner, String fieldName, Set<PhiInstruction> visited) {
        if (phi == null || visited.contains(phi)) return false;
        visited.add(phi);

        SSAValue phiResult = phi.getResult();
        if (phiResult == null) return false;

        java.util.List<IRInstruction> uses = phiResult.getUses();
        for (IRInstruction use : uses) {
            if (use instanceof FieldAccessInstruction) {
                FieldAccessInstruction fai = (FieldAccessInstruction) use;
                if (fai.isStore() && fieldOwner.equals(fai.getOwner()) && fieldName.equals(fai.getName())) {
                    return true;
                }
            } else if (use instanceof PhiInstruction) {
                if (hasFieldStoreInPhiUseChain((PhiInstruction) use, fieldOwner, fieldName, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private FieldAccessInstruction findFieldLoadInPhiChain(PhiInstruction phi, Set<PhiInstruction> visited) {
        if (phi == null || visited.contains(phi)) return null;
        visited.add(phi);

        for (Value operand : phi.getOperands()) {
            if (operand instanceof SSAValue) {
                SSAValue ssaOp = (SSAValue) operand;
                IRInstruction def = ssaOp.getDefinition();
                if (def instanceof FieldAccessInstruction) {
                    FieldAccessInstruction fai = (FieldAccessInstruction) def;
                    if (fai.isLoad()) {
                        return fai;
                    }
                } else if (def instanceof PhiInstruction) {
                    FieldAccessInstruction nested = findFieldLoadInPhiChain((PhiInstruction) def, visited);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
        }
        return null;
    }

    private FieldAccessInstruction getSelfStoreFieldInfo(PhiInstruction phi) {
        return findFieldLoadInPhiChain(phi, new HashSet<>());
    }

    /**
     * Gets a default value for the given type.
     */
    private Expression getDefaultValue(SourceType type) {
        if (type instanceof PrimitiveSourceType) {
            PrimitiveSourceType pst = (PrimitiveSourceType) type;
            if (pst == PrimitiveSourceType.BOOLEAN) {
                return LiteralExpr.ofBoolean(false);
            } else if (pst == PrimitiveSourceType.LONG) {
                return LiteralExpr.ofLong(0L);
            } else if (pst == PrimitiveSourceType.FLOAT) {
                return LiteralExpr.ofFloat(0.0f);
            } else if (pst == PrimitiveSourceType.DOUBLE) {
                return LiteralExpr.ofDouble(0.0);
            } else {
                return LiteralExpr.ofInt(0);
            }
        }
        return LiteralExpr.ofNull();
    }

    /**
     * Checks if an expression is a default value (0, false, null, 0L, 0.0, etc.).
     * Used to skip redundant assignments that re-initialize variables to their default values.
     */
    private boolean isDefaultValue(Expression expr) {
        if (!(expr instanceof LiteralExpr)) {
            return false;
        }
        LiteralExpr literal = (LiteralExpr) expr;
        Object value = literal.getValue();
        if (value == null) {
            return true;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() == 0.0;
        }
        if (value instanceof Boolean) {
            return !((Boolean) value);
        }
        return false;
    }

    /** Tracks try handlers that have already been processed to avoid infinite loops */
    private final Set<ExceptionHandler> processedTryHandlers = new HashSet<>();
    /** Tracks handler blocks to prevent nested try-finally for same finally block */
    private final Set<IRBlock> processedHandlerBlocks = new HashSet<>();

    public List<Statement> recoverBlockSequence(IRBlock startBlock, Set<IRBlock> stopBlocks) {
        // The RC engine structures a whole method at once. The legacy walk's own sub-recursion (if arms,
        // loop bodies) must not be intercepted, or the two engines interleave on one region and corrupt
        // shared phi/mark state - so only the top-level whole-method call is offered here. Exception-
        // scaffolding pieces, which are handed off in full, go through recoverRegionHandoff instead.
        if (startBlock == context.getIrMethod().getEntryBlock() && stopBlocks.isEmpty()) {
            List<Statement> structured = rcsStructurer.tryStructureRegion(startBlock, stopBlocks);
            if (structured != null) {
                return structured;
            }
        }
        return legacyBlockWalk(startBlock, stopBlocks);
    }

    /**
     * Recovers a wholesale region hand-off - an exception-scaffolding piece (the code before a try, a try
     * body, or the continuation after a try/catch) - preferring the RC engine, then the legacy walk. Such
     * a piece is handed off in full rather than being a sub-region of an in-progress schema structuring,
     * so the RC engine may structure it without interleaving with the legacy walk.
     */
    private List<Statement> recoverRegionHandoff(IRBlock startBlock, Set<IRBlock> stopBlocks) {
        List<Statement> structured = rcsStructurer.tryStructureRegion(startBlock, stopBlocks);
        if (structured != null) {
            return structured;
        }
        return legacyBlockWalk(startBlock, stopBlocks);
    }

    /** The schema-based structural walk: the legacy recovery the RC engine falls back to for a declined region. */
    private List<Statement> legacyBlockWalk(IRBlock startBlock, Set<IRBlock> stopBlocks) {
        List<Statement> result = new ArrayList<>();
        Set<IRBlock> visited = new HashSet<>();
        IRBlock current = startBlock;

        while (current != null && !visited.contains(current) && !stopBlocks.contains(current)) {
            ExceptionHandler tryHandler = findHandlerStartingAt(current);
            if (tryHandler != null && !processedTryHandlers.contains(tryHandler)
                    && !processedHandlerBlocks.contains(tryHandler.getHandlerBlock())) {
                processedTryHandlers.add(tryHandler);
                if (tryHandler.getHandlerBlock() != null) {
                    processedHandlerBlocks.add(tryHandler.getHandlerBlock());
                }
                Set<IRBlock> tryVisited = new HashSet<>(visited);
                Statement recovered = recoverTryCatch(current, tryHandler, stopBlocks, tryVisited);
                if (recovered != null) {
                    result.add(recovered);
                    visited.addAll(tryVisited);
                    if (isTerminatingRecoveredTry(recovered)) {
                        current = null;
                    } else {
                        current = findBlockAfterTryCatch(tryHandler, visited);
                    }
                    continue;
                }
            }

            visited.add(current);

            if (context.isProcessed(current)) {
                result.addAll(context.getStatements(current));
                IRBlock revisitNext = getNextSequentialBlock(current);
                if (revisitNext == null && stopBlocks.isEmpty()) {
                    // A re-visited region header (if/switch/loop) has two-plus successors, so
                    // getNextSequentialBlock stops the chain — but the fall-through past it continues
                    // along the region merges to a shared trailing return that other arms already
                    // emitted. Walk the merge chain and re-emit that return (a terminator is
                    // idempotent) without re-adding the intermediate, already-emitted blocks, which
                    // would duplicate them.
                    IRBlock chain = current;
                    Set<IRBlock> chainSeen = new HashSet<>();
                    while (chain != null && chainSeen.add(chain)) {
                        if (chain != current && isReturnBlock(chain) && context.isProcessed(chain)) {
                            result.addAll(context.getStatements(chain));
                            break;
                        }
                        RegionInfo chainInfo = analyzer.getRegionInfo(chain);
                        chain = chainInfo != null && chainInfo.getMergeBlock() != null
                                ? chainInfo.getMergeBlock()
                                : getNextSequentialBlock(chain);
                    }
                }
                current = revisitNext;
                continue;
            }

            RegionInfo info = analyzer.getRegionInfo(current);
            if (info == null) {
                List<Statement> blockStmts = recoverSimpleBlock(current);
                result.addAll(blockStmts);
                context.setStatements(current, blockStmts);
                context.markProcessed(current);
                current = getNextSequentialBlock(current);
                continue;
            }

            switch (info.getType()) {
                case IF_THEN: {
                    Statement ifStmt = recoverIfThen(current, info);
                    result.addAll(context.collectPendingStatements());
                    if (ifStmt != null) {
                        result.add(ifStmt);
                        if (isTerminatingStatement(ifStmt)) {
                            current = null;
                            break;
                        }
                    }
                    IRBlock merge = info.getMergeBlock();
                    if (merge != null) {
                        if (stopBlocks.contains(merge) && isReturnBlock(merge)
                                && context.classifyLoopJump(merge) != null) {
                            // The merge is a RETURN block that is also the enclosing loop's boundary: a
                            // shared exit reached only as a post-dominator. Don't inline its return here (it
                            // would duplicate the shared return into one branch and drop it from the others);
                            // the enclosing structure emits it once. A non-return loop-boundary merge falls
                            // through to `current = merge` below so the plain fall-through break is emitted.
                            current = null;
                        } else if (stopBlocks.contains(merge) && isReturnBlock(merge)) {
                            if (isMergeSharedBeyondIf(merge, current, info)) {
                                // The merge is a return reached from a sibling region too - e.g. the
                                // shared tail after a switch that several case bodies converge on.
                                // Inlining it here consumes it for this branch and drops it from the
                                // siblings; the enclosing structure (which walks to it once the switch
                                // is recovered) emits it a single time. Just break out.
                                current = null;
                            } else {
                                List<Statement> returnStmts = recoverSimpleBlock(merge);
                                result.addAll(returnStmts);
                                context.markProcessed(merge);
                                current = null;
                            }
                        } else if (context.isProcessed(merge) && isReturnBlock(merge) && ifStmt != null) {
                            if (isAllPathsTerminating((IfStmt) ifStmt)) {
                                List<Statement> returnStmts = recoverSimpleBlock(merge);
                                result.addAll(returnStmts);
                                current = null;
                            } else {
                                // The if's fall-through reaches this return merge, but the merge was
                                // already visited while recovering the then-branch (a nested if sharing
                                // it), so handing it back to the loop would skip it as visited and run the
                                // fall-through off the method end. Emit its recovered return here instead.
                                List<Statement> returnStmts = context.getStatements(merge);
                                if (returnStmts == null || returnStmts.isEmpty()) {
                                    returnStmts = recoverSimpleBlock(merge);
                                }
                                result.addAll(returnStmts);
                                current = null;
                            }
                        } else {
                            current = merge;
                        }
                    } else {
                        current = findNextUnprocessedBlock(current, visited, stopBlocks);
                    }
                    break;
                }
                case IF_THEN_ELSE: {
                    Statement ifStmt = recoverIfThenElse(current, info);
                    result.addAll(context.collectPendingStatements());
                    if (ifStmt != null) {
                        result.add(ifStmt);
                        if (isTerminatingStatement(ifStmt)) {
                            current = null;
                            break;
                        }
                    }
                    IRBlock merge = info.getMergeBlock();
                    if (merge != null) {
                        // A merge that is a loop boundary was only reached as a post-dominator:
                        // both arms already carry their own jumps, so walking to it would append
                        // a spurious break/continue after the if/else.
                        current = stopBlocks.contains(merge) && context.classifyLoopJump(merge) != null
                                ? null
                                : merge;
                    } else if (ifStmt instanceof IfStmt
                            && isTerminatingStatement(((IfStmt) ifStmt).getThenBranch())) {
                        // No merge and the then-branch exits (returns/throws): the else's tail leaves the
                        // straight-line flow too (it loops back to the enclosing loop), so there is no
                        // continuation. Searching for a "next" block would walk into the then-branch's own
                        // already-recovered blocks and re-add them, duplicating that branch after the if/else.
                        current = null;
                    } else {
                        current = findNextUnprocessedBlock(current, visited, stopBlocks);
                    }
                    break;
                }
                case WHILE_LOOP: {
                    result.add(recoverWhileLoop(current, info));
                    current = findLoopExit(info, visited, stopBlocks);
                    break;
                }
                case DO_WHILE_LOOP: {
                    result.add(recoverDoWhileLoop(current, info));
                    current = findLoopExit(info, visited, stopBlocks);
                    break;
                }
                case FOR_LOOP: {
                    result.add(recoverForLoop(current, info));
                    current = findLoopExit(info, visited, stopBlocks);
                    break;
                }
                case SWITCH: {
                    StringSwitchInfo stringSwitch = detectStringSwitch(current);
                    if (stringSwitch != null) {
                        IRBlock exit = stringSwitchExit(stringSwitch);
                        context.markProcessed(current);
                        Statement sw = recoverStringSwitch(current, stringSwitch, exit);
                        result.add(sw);
                        visited.addAll(stringSwitch.scaffolding);
                        current = (exit != null && !visited.contains(exit) && !stopBlocks.contains(exit))
                                ? exit : null;
                    } else {
                        result.add(recoverSwitch(current, info));
                        current = findSwitchMerge(info);
                    }
                    break;
                }
                case GUARD_CLAUSE: {
                    Statement guardStmt = recoverGuardClause(current, info);
                    result.addAll(context.collectPendingStatements());
                    result.add(guardStmt);
                    context.markProcessed(current);
                    current = info.getElseBlock();
                    break;
                }
                case IRREDUCIBLE: {
                    result.add(recoverIrreducible(current));
                    current = null;
                    break;
                }
                default: {
                    List<Statement> blockStmts = recoverSimpleBlock(current);
                    result.addAll(blockStmts);
                    context.setStatements(current, blockStmts);
                    context.markProcessed(current);
                    current = getNextSequentialBlock(current);
                    break;
                }
            }
        }

        // A path that exits this sequence into a loop's exit (break) or continue-target (continue) is an
        // explicit jump. The innermost loop yields an unlabeled break/continue; an enclosing loop yields a
        // labeled one. A redundant trailing `continue` to the innermost loop is stripped by the loop recovery.
        if (current != null) {
            ControlFlowContext.LoopJump jump = context.classifyLoopJump(current);
            if (jump != null) {
                String label = jump.loopHeader != null ? context.getOrCreateLabel(jump.loopHeader) : null;
                if (jump.kind == ControlFlowContext.JumpKind.BREAK) {
                    result.add(label != null ? new BreakStmt(label) : new BreakStmt());
                } else {
                    result.add(label != null ? new ContinueStmt(label) : new ContinueStmt());
                }
                return result;
            }
        }

        if (current != null && stopBlocks.contains(current) && !visited.contains(current)) {
            // Only absorb a trailing terminator (e.g. a return) that is not the shared continuation of a
            // try/catch. A return block that a try body falls into AND a catch jumps to is the continuation
            // after the try/catch, not the try's own terminator; absorbing it emits a spurious `return;` inside
            // the try. (A switch/if merge-return reached only from normal case blocks is still absorbed.)
            if (isSimpleTerminatorBlock(current)
                    && (visited.containsAll(current.getPredecessors()) || !isReachedFromCatchHandler(current))) {
                List<Statement> termStmts = recoverSimpleBlock(current);
                result.addAll(termStmts);
                visited.add(current);
            }
        }

        return result;
    }

    /**
     * Finds the next unprocessed block when the merge block is null.
     * This handles cases where control flow doesn't have a clear merge point
     * (e.g., if both branches return or throw).
     */
    private IRBlock findNextUnprocessedBlock(IRBlock current, Set<IRBlock> visited, Set<IRBlock> stopBlocks) {
        IRBlock next = getNextSequentialBlock(current);
        if (next != null && !visited.contains(next) && !stopBlocks.contains(next)) {
            return next;
        }

        for (IRBlock succ : current.getSuccessors()) {
            if (!visited.contains(succ) && !stopBlocks.contains(succ)) {
                return succ;
            }
        }

        IRMethod method = context.getIrMethod();
        for (IRBlock block : method.getBlocks()) {
            if (!visited.contains(block) && !stopBlocks.contains(block) && !context.isProcessed(block)) {
                if (isReachableFromEntry(block, method)) {
                    return block;
                }
            }
        }

        return null;
    }

    /**
     * Checks if a block is reachable from the method entry.
     */
    private boolean isReachableFromEntry(IRBlock target, IRMethod method) {
        Set<IRBlock> reachable = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(method.getEntryBlock());

        while (!worklist.isEmpty()) {
            IRBlock b = worklist.poll();
            if (reachable.contains(b)) continue;
            reachable.add(b);
            if (b == target) return true;
            worklist.addAll(b.getSuccessors());
        }
        return false;
    }

    @Override
    public List<Statement> recoverSimpleBlock(IRBlock block) {
        List<Statement> statements = new ArrayList<>();

        for (IRInstruction instr : block.getInstructions()) {
            if (context.shouldSkipInstruction(instr)) {
                continue;
            }
            Statement stmt = recoverInstruction(instr);
            if (stmt != null) {
                    statements.add(stmt);
            }
        }

        return statements;
    }

    /**
     * Recovers a statement for an instruction and stamps it with the instruction's bytecode-offset
     * provenance, so decompiled output can be mapped back to bytecode positions.
     */
    private Statement recoverInstruction(IRInstruction instr) {
        Statement stmt = recoverInstruction0(instr);
        if (stmt != null && instr.getBytecodeOffset() >= 0 && !stmt.getLocation().hasOffset()) {
            stmt.setLocation(SourceLocation.fromOffset(instr.getBytecodeOffset()));
        }
        return stmt;
    }

    private void stamp(Statement stmt, IRInstruction instr) {
        if (stmt != null && instr != null && instr.getBytecodeOffset() >= 0 && !stmt.getLocation().hasOffset()) {
            stmt.setLocation(SourceLocation.fromOffset(instr.getBytecodeOffset()));
        }
    }

    /**
     * Stamps a recovered control-flow statement with its header's bytecode offset, preferring the
     * branch/switch terminator's provenance and falling back to the block's start offset.
     */
    private void stampFromHeader(Statement stmt, IRBlock header) {
        if (stmt == null || header == null || stmt.getLocation().hasOffset()) {
            return;
        }
        int offset = header.getTerminator() != null ? header.getTerminator().getBytecodeOffset() : -1;
        if (offset < 0) {
            offset = header.getBytecodeOffset();
        }
        if (offset >= 0) {
            stmt.setLocation(SourceLocation.fromOffset(offset));
        }
    }

    /** Stamps a wrapper statement (e.g. try/catch) from the first stamped statement in its body. */
    private void stampFromBody(Statement stmt, BlockStmt body) {
        if (stmt == null || body == null || stmt.getLocation().hasOffset()) {
            return;
        }
        for (Statement child : body.getStatements()) {
            if (child.getLocation() != null && child.getLocation().hasOffset()) {
                stmt.setLocation(child.getLocation());
                return;
            }
        }
    }

    /**
     * True when a field-load value is read again after the same field is reassigned before that use.
     * A field read is not an SSA value backed by a register but a re-read of mutable memory, so
     * inlining it at a later use would observe the written value, not the loaded one. This is javac's
     * {@code arr[this.f++] = v} shape: the store index is the pre-increment field value, but the
     * putfield that increments the field is emitted first. Restricted to a single block, where
     * instruction order is program order, so the check needs no dominance reasoning.
     */
    private boolean fieldLoadClobberedBeforeUse(FieldAccessInstruction load, SSAValue result) {
        if (result == null) {
            return false;
        }
        IRBlock block = load.getBlock();
        if (block == null || !blockHasFieldStore(block)) {
            return false;
        }
        Set<IRInstruction> uses = null;
        boolean seenLoad = false;
        boolean clobbered = false;
        for (IRInstruction between : block.getInstructions()) {
            if (!seenLoad) {
                seenLoad = between == load;
                continue;
            }
            if (between instanceof FieldAccessInstruction) {
                FieldAccessInstruction store = (FieldAccessInstruction) between;
                if (store.isStore()
                        && store.isStatic() == load.isStatic()
                        && load.getName().equals(store.getName())
                        && load.getOwner().equals(store.getOwner())
                        && load.getObjectRef() == store.getObjectRef()) {
                    clobbered = true;
                }
            }
            if (clobbered) {
                if (uses == null) {
                    uses = new HashSet<>(result.getUses());
                }
                if (uses.contains(between)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a block writes any field, cached so store-free blocks skip the clobber scan in O(1). */
    private boolean blockHasFieldStore(IRBlock block) {
        Boolean cached = blockHasFieldStoreCache.get(block);
        if (cached != null) {
            return cached;
        }
        boolean has = false;
        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof FieldAccessInstruction && ((FieldAccessInstruction) instr).isStore()) {
                has = true;
                break;
            }
        }
        blockHasFieldStoreCache.put(block, has);
        return has;
    }

    private final java.util.Map<IRBlock, Boolean> blockHasFieldStoreCache = new java.util.IdentityHashMap<>();

    /**
     * Materializes a load into a named temporary declared at the load site so later uses read the
     * captured value instead of re-reading its storage after a write. Returns null when the value has no
     * recoverable type or the name is already taken (leaving the caller's default path).
     */
    private Statement materializeClobberedLoad(SSAValue result, Expression value) {
        SourceType type = value.getType();
        if (type == null) {
            type = typeRecoverer.recoverType(result);
        }
        if (type == null) {
            return null;
        }
        String name = "v" + result.getId();
        if (context.getExpressionContext().isDeclared(name)) {
            return null;
        }
        context.getExpressionContext().markDeclaredWithType(name, type);
        context.getExpressionContext().markMaterialized(result);
        context.getExpressionContext().setVariableName(result, name);
        return new VarDeclStmt(type, name, value);
    }

    private final Set<SSAValue> splitIncrementTemps = new HashSet<>();

    /**
     * Splits the live range of a self-increment's pre-value when it is read again, later in the same
     * block, after the increment store. A loop induction and its {@code + 1} coalesce to one source name
     * ({@code i}); {@code f(i++)} reads the pre-increment value but the store {@code i = i + 1} is
     * emitted first, so re-rendering that read as {@code i} would observe the incremented value. The
     * pre-value is captured into a temporary before the store and the post-store reads rewired to it,
     * yielding {@code int t = i; i = i + 1; f(t);}. The copy carries a temp name; its declaration is
     * emitted when the inserted {@link CopyInstruction} is recovered.
     */
    private void splitClobberedIncrementReads(IRMethod method) {
        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> snapshot = new ArrayList<>(block.getInstructions());
            for (int i = 0; i < snapshot.size(); i++) {
                if (!(snapshot.get(i) instanceof StoreLocalInstruction)) {
                    continue;
                }
                StoreLocalInstruction store = (StoreLocalInstruction) snapshot.get(i);
                SSAValue pre = incrementPreValue(store);
                if (pre == null) {
                    continue;
                }
                String storeName = partitionName(store);
                if (storeName == null
                        || !storeName.equals(context.getExpressionContext().getVariableName(pre))) {
                    continue;
                }
                List<IRInstruction> laterUses = new ArrayList<>();
                for (IRInstruction use : pre.getUses()) {
                    if (snapshot.indexOf(use) > i) {
                        laterUses.add(use);
                    }
                }
                if (laterUses.isEmpty()) {
                    continue;
                }
                SourceType type = typeRecoverer.recoverType(pre);
                if (type == null) {
                    continue;
                }
                SSAValue copy = new SSAValue(pre.getType());
                String name = "v" + copy.getId();
                context.getExpressionContext().markDeclaredWithType(name, type);
                context.getExpressionContext().markMaterialized(copy);
                context.getExpressionContext().setVariableName(copy, name);
                splitIncrementTemps.add(copy);
                block.insertInstruction(block.getInstructions().indexOf(store), new CopyInstruction(copy, pre));
                for (IRInstruction use : laterUses) {
                    use.replaceOperand(pre, copy);
                }
            }
        }
    }

    /** The incremented pre-value {@code x} of a {@code slot = x +/- constant} store, else null. */
    private SSAValue incrementPreValue(StoreLocalInstruction store) {
        Value stored = store.getValue();
        if (!(stored instanceof SSAValue)) {
            return null;
        }
        IRInstruction def = ((SSAValue) stored).getDefinition();
        if (!(def instanceof BinaryOpInstruction)) {
            return null;
        }
        BinaryOpInstruction bin = (BinaryOpInstruction) def;
        if (bin.getOp() != BinaryOp.ADD && bin.getOp() != BinaryOp.SUB) {
            return null;
        }
        Value left = bin.getLeft();
        Value right = bin.getRight();
        if (left instanceof SSAValue && isConstantValue(right)) {
            return (SSAValue) left;
        }
        if (right instanceof SSAValue && isConstantValue(left) && bin.getOp() == BinaryOp.ADD) {
            return (SSAValue) right;
        }
        return null;
    }

    /** A raw constant, or an SSA value produced by a constant load. */
    private boolean isConstantValue(Value v) {
        if (v instanceof Constant) {
            return true;
        }
        return v instanceof SSAValue && ((SSAValue) v).getDefinition() instanceof ConstantInstruction;
    }

    private Statement recoverInstruction0(IRInstruction instr) {
        if (instr.isTerminator()) {
            return recoverTerminator(instr);
        }

        if (instr instanceof StoreLocalInstruction) {
            StoreLocalInstruction store = (StoreLocalInstruction) instr;
            return recoverStoreLocal(store);
        }

        if (instr instanceof FieldAccessInstruction) {
            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
            if (fieldAccess.isStore()) {
                Value storedValue = fieldAccess.getValue();
                if (storedValue instanceof SSAValue) {
                    SSAValue ssaStored = (SSAValue) storedValue;
                    IRInstruction def = ssaStored.getDefinition();
                    if (def instanceof PhiInstruction && selfStorePhis.contains((PhiInstruction) def)) {
                        return null;
                    }
                }
                Expression receiver = fieldAccess.isStatic() ? null :
                    exprRecoverer.recoverOperand(fieldAccess.getObjectRef());
                SourceType fieldType = typeRecoverer.recoverType(fieldAccess.getDescriptor());
                Expression value = exprRecoverer.recoverOperand(fieldAccess.getValue(), fieldType);
                Expression target = new FieldAccessExpr(
                    receiver, fieldAccess.getName(), fieldAccess.getOwner(), fieldAccess.isStatic(), fieldType)
                    .withDescriptor(fieldAccess.getDescriptor());
                return new ExprStmt(new BinaryExpr(
                    BinaryOperator.ASSIGN, target, value, fieldType));
            }
            if (fieldAccess.isLoad() && fieldAccess.getResult() != null) {
                SSAValue result = fieldAccess.getResult();
                Expression value = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(result, value);
                if (fieldLoadClobberedBeforeUse(fieldAccess, result)) {
                    Statement decl = materializeClobberedLoad(result, value);
                    if (decl != null) {
                        return decl;
                    }
                }
                PhiInstruction targetPhi = getPhiUsingValue(result);
                if (targetPhi != null && targetPhi.getResult() != null) {
                    if (selfStorePhis.contains(targetPhi)) {
                        return null;
                    }
                    if (context.isForLoopInductionPhi(targetPhi.getResult())) {
                        return null;
                    }
                    // When this field-load value is written by a store_local to a source variable other
                    // than the phi's own, that store is its real assignment; a copy here naming the phi's
                    // variable would be a spurious cross-variable one (a redundant advance, or a
                    // type-punned reuse like `i = model`). Cache the expression and let the store emit it.
                    if (fieldLoadValueBelongsToOtherVariable(result, targetPhi)) {
                        return null;
                    }
                    String phiVarName = context.getExpressionContext().getVariableName(targetPhi.getResult());
                    if (phiVarName != null) {
                        SourceType type = value.getType();
                        if (type == null) {
                            type = typeRecoverer.recoverType(result);
                        }
                        VarRefExpr target = new VarRefExpr(phiVarName, type, targetPhi.getResult());
                        return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
                    }
                }
            }
            return null;
        }

        if (instr instanceof ArrayAccessInstruction) {
            ArrayAccessInstruction arrayAccess = (ArrayAccessInstruction) instr;
            if (arrayAccess.isStore()) {
                Expression array = exprRecoverer.recoverOperand(arrayAccess.getArray());
                Expression index = exprRecoverer.recoverOperand(arrayAccess.getIndex());
                Expression value = exprRecoverer.recoverOperand(arrayAccess.getValue());
                if (!(index instanceof LiteralExpr) && isConstantOperand(arrayAccess.getIndex())) {
                    Logger.error("decompiler: constant array-store index recovered as non-literal '"
                            + index + "' on '" + array + "' — likely a mis-materialized constant");
                }
                SourceType arrayType = array.getType();
                SourceType elemType = (arrayType instanceof ArraySourceType)
                    ? ((ArraySourceType) arrayType).getElementType()
                    : value.getType();
                value = coerceForStore(value, elemType);
                Expression target = new ArrayAccessExpr(array, index, elemType);
                return new ExprStmt(new BinaryExpr(
                    BinaryOperator.ASSIGN, target, value, elemType));
            } else {
                SSAValue result = arrayAccess.getResult();
                if (result != null) {
                    Expression expr = exprRecoverer.recover(arrayAccess);
                    context.getExpressionContext().cacheExpression(result, expr);
                }
                return null;
            }
        }

        if (instr instanceof InvokeInstruction) {
            InvokeInstruction invoke = (InvokeInstruction) instr;
            if ("<init>".equals(invoke.getName())) {
                Value receiver = invoke.getArguments().isEmpty() ? null : invoke.getArguments().get(0);
                SSAValue ssaReceiver = (receiver instanceof SSAValue) ? (SSAValue) receiver : null;
                Expression expr = exprRecoverer.recover(invoke);
                if (expr instanceof MethodCallExpr) {
                    MethodCallExpr mce = (MethodCallExpr) expr;
                    String methodName = mce.getMethodName();
                    if ("super".equals(methodName) || "this".equals(methodName)) {
                        return new ExprStmt(expr);
                    }
                }
                if (expr instanceof NewExpr) {
                    if (ssaReceiver != null) {
                        IRInstruction receiverDef = ssaReceiver.getDefinition();
                        if (receiverDef instanceof NewInstruction) {
                            NewInstruction newInstr = (NewInstruction) receiverDef;
                            SSAValue newResult = newInstr.getResult();
                            boolean usedByStore = isUsedByStoreLocal(newResult);
                            PhiInstruction targetPhi = getPhiUsingValue(newResult);
                            if (targetPhi == null && usedByStore) {
                                targetPhi = getPhiThroughStoreChain(newResult);
                            }
                            if (newResult != null && targetPhi != null && targetPhi.getResult() != null
                                    && !isStoredToPhiSlot(newResult, targetPhi)) {
                                String phiVarName = context.getExpressionContext().getVariableName(targetPhi.getResult());
                                if (phiVarName != null && !phiVarName.equals("this")
                                        && !isParameterOrThisRef(targetPhi.getResult())
                                        && valueBelongsToPhiVariable(newResult, phiVarName)) {
                                    SourceType type = expr.getType();
                                    VarRefExpr target = new VarRefExpr(phiVarName, type, targetPhi.getResult());
                                    return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, expr, type));
                                }
                            }
                            if (newResult != null) {
                                context.getExpressionContext().cacheExpression(newResult, expr);
                            }
                            if (usedByStore && targetPhi == null) {
                                return null;
                            }
                            return null;
                        }
                        SSAValue actualNewValue = findNewInstructionValue(ssaReceiver);
                        boolean usedByStoreLocal = isUsedByStoreLocal(actualNewValue);
                        if (actualNewValue != null && !usedByStoreLocal) {
                            PhiInstruction targetPhi = getPhiUsingValue(actualNewValue);
                            if (targetPhi != null && targetPhi.getResult() != null) {
                                String phiVarName = context.getExpressionContext().getVariableName(targetPhi.getResult());
                                if (phiVarName != null && !phiVarName.equals("this")
                                        && !isParameterOrThisRef(targetPhi.getResult())
                                        && valueBelongsToPhiVariable(actualNewValue, phiVarName)) {
                                    SourceType type = expr.getType();
                                    VarRefExpr target = new VarRefExpr(phiVarName, type, targetPhi.getResult());
                                    return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, expr, type));
                                }
                            }
                        }
                        String varName = context.getExpressionContext().getVariableName(ssaReceiver);
                        if (varName != null && !varName.equals("this")
                                && !isParameterOrThisRef(ssaReceiver)) {
                            SourceType type = expr.getType();
                            if (!context.getExpressionContext().isDeclared(varName)) {
                                context.getExpressionContext().markDeclared(varName);
                                return new VarDeclStmt(type, varName, expr);
                            }
                        }
                        if (actualNewValue != null) {
                            context.getExpressionContext().cacheExpression(actualNewValue, expr);
                        }
                    }
                    return null;
                }
                return new ExprStmt(expr);
            }
            if (invoke.getResult() == null || invoke.getResult().getType() == null) {
                Expression expr = exprRecoverer.recover(invoke);
                return new ExprStmt(expr);
            }
            SSAValue result = invoke.getResult();
            if (context.getExpressionContext().isRecovered(result)) {
                return null;
            }
            boolean usedByStoreLocal = isUsedByStoreLocal(result);
            if (usedByStoreLocal) {
                Expression expr = exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
            if (isSingleUseArrayStoreValue(result)) {
                Expression expr = exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
            if (result.getUses().isEmpty()) {
                Expression expr = exprRecoverer.recover(invoke);
                return new ExprStmt(expr);
            }
            if (isIntermediateValue(result)) {
                Expression expr = exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
            if (isSingleUsePutField(result)) {
                Expression expr = exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
            if (isSingleUsePhiOperand(result)) {
                Expression expr = exprRecoverer.recover(invoke);
                // If the value feeds an already-declared phi (e.g. a structured switch-expression
                // merge), emit the copy `phiVar = expr` here: the structured recovery path has no
                // lowerPhisOnEdge step, so otherwise a non-constant arm value would be dropped.
                Statement phiCopy = phiCopyForDeclaredMerge(result, expr);
                if (phiCopy != null) {
                    return phiCopy;
                }
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
        }

        if (instr instanceof NewInstruction) {
            SSAValue result = instr.getResult();
            if (result != null) {
                Expression expr = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(result, expr);
            }
            return null;
        }

        if (instr instanceof NewArrayInstruction) {
            SSAValue result = instr.getResult();
            if (result != null) {
                boolean usedByStore = isUsedByStoreLocal(result);
                boolean usedByArrayStore = isUsedByArrayStore(result);
                boolean usesEmpty = result.getUses().isEmpty();

                // If used by both array store and local store, emit declaration here
                // so array stores can use the variable name correctly.
                // Without this, array stores would appear before the declaration.
                if (usedByArrayStore && usedByStore) {
                    String varName = getLocalNameFromStoreLocal(result);
                    if (varName != null) {
                        SourceType type = typeRecoverer.recoverType(result);
                        Expression value = exprRecoverer.recover(instr);
                        context.getExpressionContext().cacheExpression(result, value);
                        context.getExpressionContext().setVariableName(result, varName);
                        context.getExpressionContext().markMaterialized(result);
                        // The slot may already be declared in an enclosing scope (its declaration was
                        // emitted at method scope or by an earlier store). Re-declaring here shadows it,
                        // so the array is built into a fresh inner variable and the outer one keeps its
                        // old value - the store is lost. Assign to the existing variable instead.
                        if (context.getExpressionContext().isDeclared(varName)) {
                            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN,
                                    new VarRefExpr(varName, type, result), value, type));
                        }
                        context.getExpressionContext().markDeclared(varName);
                        return new VarDeclStmt(type, varName, value);
                    }
                }

                if (usedByStore) {
                    Expression expr = exprRecoverer.recover(instr);
                    context.getExpressionContext().cacheExpression(result, expr);
                    return null;
                }
                if (usesEmpty) {
                    Expression expr = exprRecoverer.recover(instr);
                    return new ExprStmt(expr);
                }
                return recoverVarDecl(instr);
            }
            return null;
        }

        if (instr instanceof CopyInstruction) {
            CopyInstruction copy = (CopyInstruction) instr;
            if (copy.getResult() != null && splitIncrementTemps.contains(copy.getResult())) {
                SourceType type = typeRecoverer.recoverType(copy.getResult());
                String name = context.getExpressionContext().getVariableName(copy.getResult());
                Expression src = exprRecoverer.recoverOperand(copy.getSource());
                return new VarDeclStmt(type, name, src);
            }
            Value source = copy.getSource();
            Set<SSAValue> visited = new HashSet<>();
            while (source instanceof SSAValue) {
                SSAValue ssaSource = (SSAValue) source;
                if (!visited.add(ssaSource)) {
                    break;
                }
                if (context.getExpressionContext().isPendingNew(ssaSource)) {
                    return null;
                }
                IRInstruction def = ssaSource.getDefinition();
                if (def instanceof NewInstruction) {
                    return null;
                }
                if (def instanceof CopyInstruction) {
                    source = ((CopyInstruction) def).getSource();
                    continue;
                }
                break;
            }
        }

        if (instr instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) instr;
            if (simple.getOp() == SimpleOp.MONITORENTER ||
                simple.getOp() == SimpleOp.MONITOREXIT) {
                return null;
            }
            if (simple.getOp() == SimpleOp.ATHROW) {
                Expression exception = exprRecoverer.recoverOperand(simple.getOperand());
                return new ThrowStmt(exception);
            }
        }

        if (instr instanceof LoadLocalInstruction) {
            LoadLocalInstruction loadLocal = (LoadLocalInstruction) instr;
            if (loadLocal.getResult() != null) {
                String existingName = context.getExpressionContext().getVariableName(loadLocal.getResult());
                if (existingName == null) {
                    int localIndex = loadLocal.getLocalIndex();
                    String localName = getNameForLocalSlot(localIndex);
                    context.getExpressionContext().setVariableName(loadLocal.getResult(), localName);
                }
                // Mark as materialized so recoverOperand returns a VarRefExpr instead of
                // re-recovering the instruction (which would create duplicate new expressions)
                context.getExpressionContext().markMaterialized(loadLocal.getResult());
                Expression value = exprRecoverer.recover(loadLocal);
                context.getExpressionContext().cacheExpression(loadLocal.getResult(), value);
            }
            return null;
        }

        if (instr instanceof BinaryOpInstruction || instr instanceof UnaryOpInstruction || instr instanceof TypeCheckInstruction) {
            if (instr.getResult() != null && isIntermediateValue(instr.getResult())) {
                Expression value = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), value);
                return null;
            }
            if (instr.getResult() != null && isSingleUsePhiOperand(instr.getResult())) {
                Expression value = exprRecoverer.recover(instr);
                Statement phiCopy = phiCopyForDeclaredMerge(instr.getResult(), value);
                if (phiCopy != null) {
                    return phiCopy;
                }
                context.getExpressionContext().cacheExpression(instr.getResult(), value);
                return null;
            }
        }

        if (instr instanceof TypeCheckInstruction) {
            if (instr.getResult() != null && isIntermediateValue(instr.getResult())) {
                Expression value = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), value);
                return null;
            }
        }

        if (instr instanceof ConstantInstruction) {
            ConstantInstruction constInstr = (ConstantInstruction) instr;
            SSAValue result = constInstr.getResult();
            if (result != null) {
                PhiInstruction targetPhi = getPhiUsingValue(result);
                if (targetPhi != null && targetPhi.getResult() != null) {
                    if (selfStorePhis.contains(targetPhi)) {
                        FieldAccessInstruction fieldInfo = getSelfStoreFieldInfo(targetPhi);
                        if (fieldInfo != null) {
                            Expression value = exprRecoverer.recover(constInstr);
                            SourceType fieldType = typeRecoverer.recoverType(fieldInfo.getDescriptor());
                            Expression fieldTarget = new FieldAccessExpr(
                                null, fieldInfo.getName(), fieldInfo.getOwner(), fieldInfo.isStatic(), fieldType)
                                .withDescriptor(fieldInfo.getDescriptor());
                            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, fieldTarget, value, fieldType));
                        }
                    }
                    if (context.isForLoopInductionPhi(targetPhi.getResult())) {
                        Expression value = exprRecoverer.recover(constInstr);
                        context.getExpressionContext().cacheExpression(result, value);
                        return null;
                    }
                    String phiVarName = context.getExpressionContext().getVariableName(targetPhi.getResult());
                    if (phiVarName != null) {
                        Expression value = exprRecoverer.recover(constInstr);
                        if (!context.getExpressionContext().isDeclared(phiVarName)) {
                            context.getExpressionContext().cacheExpression(result, value);
                            return null;
                        }
                        if (isDefaultValue(value)) {
                            context.getExpressionContext().cacheExpression(result, value);
                            return null;
                        }
                        SourceType type = getLocalSlotUnifiedType(phiVarName);
                        if (type == null) {
                            type = value.getType();
                        }
                        if (type == null) {
                            type = typeRecoverer.recoverType(result);
                        }
                        if (type == PrimitiveSourceType.BOOLEAN) {
                            Expression boolValue = tryConvertToBooleanLiteral(value, result);
                            if (boolValue != null) {
                                value = boolValue;
                            }
                        }
                        VarRefExpr target = new VarRefExpr(phiVarName, type, targetPhi.getResult());
                        return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
                    }
                }
                Expression value = exprRecoverer.recover(constInstr);
                context.getExpressionContext().cacheExpression(result, value);
            }
            return null;
        }

        if (instr.getResult() != null) {
            if (isUsedByStoreLocal(instr.getResult())) {
                Expression expr = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), expr);
                return null;
            }
            if (isUsedOnlyByTerminator(instr.getResult())) {
                Expression expr = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), expr);
                return null;
            }
            return recoverVarDecl(instr);
        }

        return null;
    }

    private boolean isUsedOnlyByTerminator(SSAValue value) {
        if (value == null) return false;
        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.isEmpty()) return false;
        for (IRInstruction use : uses) {
            if (use instanceof BranchInstruction) continue;
            if (use instanceof ReturnInstruction) continue;
            if (use instanceof SwitchInstruction) continue;
            return false;
        }
        return true;
    }

    private Statement recoverTerminator(IRInstruction instr) {
        if (instr instanceof ReturnInstruction) {
            ReturnInstruction ret = (ReturnInstruction) instr;
            return recoverReturn(ret);
        }
        if (instr instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) instr;
            if (simple.getOp() == SimpleOp.ATHROW) {
                Expression exception = exprRecoverer.recoverOperand(simple.getOperand());
                return new ThrowStmt(exception);
            }
        }
        return null;
    }

    private Statement recoverStoreLocal(StoreLocalInstruction store) {
        Value storeValue = store.getValue();

        // Early exit: if this is a NewArrayInstruction result that was already
        // declared at the NewArray position (because it's used by array stores),
        // skip to avoid duplicate declarations. This is a specific case handled
        // in recoverInstruction for NewArrayInstruction.
        if (storeValue instanceof SSAValue) {
            SSAValue ssaValue = (SSAValue) storeValue;
            IRInstruction def = ssaValue.getDefinition();
            if (def instanceof NewArrayInstruction && isUsedByArrayStore(ssaValue)) {
                if (context.getExpressionContext().isMaterialized(ssaValue)) {
                    String existingName = context.getExpressionContext().getVariableName(ssaValue);
                    if (existingName != null && context.getExpressionContext().isDeclared(existingName)) {
                        int localIndex = store.getLocalIndex();
                        SourceType valueType = typeRecoverer.recoverType(ssaValue);
                        String expectedName = partitionName(store);
                        if (expectedName == null) {
                            expectedName = getNameForLocalSlotWithType(localIndex, valueType);
                        }
                        if (existingName.equals(expectedName)) {
                            return null;
                        }
                    }
                }
            }
        }

        int localIndex = store.getLocalIndex();

        // When recovering the initialization value for a variable declaration,
        // we need to recover the actual expression (e.g., "new GridBagConstraints()"),
        // not a variable reference (e.g., "local1"). So we temporarily un-materialize
        // the value during recovery, then re-materialize it after.
        // BUT: if the value was already stored to a DIFFERENT slot, we want the
        // variable reference (e.g., "result = i" should reference "i", not "new Integer(999)").
        boolean wasMaterialized;
        boolean shouldUnmaterialize = false;
        if (storeValue instanceof SSAValue) {
            SSAValue ssaValue = (SSAValue) storeValue;
            wasMaterialized = context.getExpressionContext().isMaterialized(ssaValue);
            int previousSlot = context.getExpressionContext().getSSAValueSlot(ssaValue);
            // An array initializer (`new Object[]{a, b}`) is emitted as its own declaration with the
            // element stores attached (`Object[] tmp = new Object[2]; tmp[0] = a; ...`). A store of
            // that array to another local must reference the temp, not re-recover the bare
            // `new Object[2]` expression, which would silently drop the element stores.
            String valueName = context.getExpressionContext().getVariableName(ssaValue);
            boolean declaredArrayInit = ssaValue.getDefinition() instanceof NewArrayInstruction
                    && isUsedByArrayStore(ssaValue)
                    && valueName != null
                    && context.getExpressionContext().isDeclared(valueName);
            // Only unmaterialize if this is the first store OR if we're storing to the same slot
            shouldUnmaterialize = wasMaterialized && !declaredArrayInit
                    && (previousSlot == -1 || previousSlot == localIndex);
            if (shouldUnmaterialize) {
                context.getExpressionContext().unmarkMaterialized(ssaValue);
            }
        }

        Expression value = exprRecoverer.recoverOperand(storeValue);

        // Re-materialize after recovery if we unmaterialized
        if (shouldUnmaterialize) {
            context.getExpressionContext().markMaterialized((SSAValue) storeValue);
        }

        value = stripDoubleNot(value);

        SourceType valueType = value.getType();
        if (valueType == null) {
            valueType = typeRecoverer.recoverType(store.getValue());
        }

        // Name the store via the reaching-definition partition so this slot's variable
        // matches the loads that read it; fall back to category naming if unplaced.
        String name = partitionName(store);
        if (name == null) {
            name = getNameForLocalSlotWithType(localIndex, valueType);
        }

        SourceType type = getLocalSlotUnifiedType(name);
        if (type == null) {
            type = value.getType();
        }
        if (type == null) {
            type = VoidSourceType.INSTANCE;
        }

        // Prefer boolean expression type over int unified type (JVM uses int for boolean)
        SourceType exprType = value.getType();
        if (exprType == PrimitiveSourceType.BOOLEAN && type == PrimitiveSourceType.INT) {
            type = PrimitiveSourceType.BOOLEAN;
        }

        // Convert int literal to boolean when storing to a boolean variable
        if (type == PrimitiveSourceType.BOOLEAN) {
            Expression boolValue = tryConvertToBooleanLiteral(value, store.getValue());
            if (boolValue != null) {
                value = boolValue;
            }
        }

        // If the source value is an SSA value, mark it as materialized.
        // For NEW values (from NewInstruction etc.), use the SSA value's existing name if it has one,
        // otherwise use the slot name. This ensures consistency between the variable declaration
        // and subsequent references to the SSA value.
        // For LOADED values (from LoadLocalInstruction), DON'T copy the source's name to the target slot.
        // The target slot should keep its own name, and the assignment should reference the source.
        if (store.getValue() instanceof SSAValue) {
            SSAValue sourceValue = (SSAValue) store.getValue();
            IRInstruction sourceDef = sourceValue.getDefinition();
            // A value from a load, a phi, or a parameter is a read of an existing variable (the
            // slot's current value, a loop-carried merge, or a method argument), not a value freshly
            // defined by this store. Copying the store's slot name onto it would conflate distinct
            // variables — e.g. `a = b` (b is a loop phi or a parameter) would rename b to "a" and
            // collapse the assignment into an elided self-store.
            boolean isLoadedValue = sourceDef instanceof LoadLocalInstruction
                || sourceDef instanceof PhiInstruction
                || isParameterOrThisRef(sourceValue);
            boolean isAlreadyMaterialized = context.getExpressionContext().isMaterialized(sourceValue);

            if (!isLoadedValue && !isAlreadyMaterialized) {
                String existingName = context.getExpressionContext().getVariableName(sourceValue);
                if (existingName != null && !existingName.startsWith("v")) {
                    name = existingName;
                } else {
                    context.getExpressionContext().setVariableName(sourceValue, name);
                }
                context.getExpressionContext().setSSAValueSlot(sourceValue, localIndex);
            }
            context.getExpressionContext().markMaterialized(sourceValue);
        }

        context.getExpressionContext().setLocalSlotName(localIndex, name);

        boolean isDeclared = context.getExpressionContext().isDeclared(name);

        if (isDeclared) {
            // A default-value store is covered by the declaration's default initializer only while the variable
            // provably still holds that default: outside a loop (a loop re-runs the store each iteration where
            // the hoisted declaration ran once) AND with no non-default store to the slot dominating this one. An
            // intervening reassignment (`result = fValue; ... result = 0.0f`) makes the later default store a
            // genuine re-initialization; eliding it there drops a live write and silently changes the value.
            if (isDefaultValue(value) && context.getLoopStack().isEmpty()
                    && !hasDominatingNonDefaultStore(store)) {
                return null;
            }
            if (value instanceof VarRefExpr) {
                VarRefExpr varRef = (VarRefExpr) value;
                if (name.equals(varRef.getName())) {
                    return null;
                }
            }
            VarRefExpr target = new VarRefExpr(name, type, null);
            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
        }

        context.getExpressionContext().markDeclared(name);
        return new VarDeclStmt(type, name, value);
    }

    /**
     * True when a non-default value is written to {@code store}'s slot on the path reaching it - either earlier
     * in its own block or in a dominating block (the nearest such store is the value the slot holds entering
     * {@code store}). When so, a default-valued store to the slot is a real re-initialization, not a redundant
     * repeat of the declaration's default, and must be kept.
     */
    private boolean hasDominatingNonDefaultStore(StoreLocalInstruction store) {
        int slot = store.getLocalIndex();
        IRBlock block = store.getBlock();
        if (block == null) {
            return false;
        }
        Value inBlock = lastSlotStoreValueBefore(block, store, slot);
        if (inBlock != null) {
            return !isDefaultIrValue(inBlock);
        }
        DominatorTree dom = context.getDominatorTree();
        if (dom == null) {
            return false;
        }
        IRBlock b = dom.getImmediateDominator(block);
        while (b != null && b != block) {
            Value v = lastSlotStoreValueBefore(b, null, slot);
            if (v != null) {
                return !isDefaultIrValue(v);
            }
            IRBlock next = dom.getImmediateDominator(b);
            if (next == b) {
                break; // the entry block immediately dominates itself; stop at the root
            }
            b = next;
        }
        return false;
    }

    /**
     * The value of the last {@code store_local slot} in {@code block} occurring before {@code limit} (or the
     * last one in the block when {@code limit} is null), or null when the block stores nothing to {@code slot}.
     */
    private Value lastSlotStoreValueBefore(IRBlock block, IRInstruction limit, int slot) {
        Value found = null;
        for (IRInstruction instr : block.getInstructions()) {
            if (instr == limit) {
                break;
            }
            if (instr instanceof StoreLocalInstruction && ((StoreLocalInstruction) instr).getLocalIndex() == slot) {
                found = ((StoreLocalInstruction) instr).getValue();
            }
        }
        return found;
    }

    /** True when an IR value is a default constant (0, 0.0, false, or null). */
    private boolean isDefaultIrValue(Value value) {
        if (value instanceof NullConstant) {
            return true;
        }
        IRInstruction def = value instanceof SSAValue ? ((SSAValue) value).getDefinition() : null;
        if (def instanceof ConstantInstruction) {
            Constant c = ((ConstantInstruction) def).getConstant();
            if (c instanceof NullConstant) {
                return true;
            }
            Object v = c.getValue();
            if (v instanceof Number) {
                return ((Number) v).doubleValue() == 0.0;
            }
            if (v instanceof Boolean) {
                return !((Boolean) v);
            }
        }
        return false;
    }


    private Statement recoverReturn(ReturnInstruction ret) {
        if (ret.isVoidReturn()) {
            return new ReturnStmt(null);
        }
        String methodDesc = context.getIrMethod().getDescriptor();
        SourceType returnType = null;
        if (methodDesc != null) {
            int parenEnd = methodDesc.indexOf(')');
            if (parenEnd >= 0 && parenEnd + 1 < methodDesc.length()) {
                String retDesc = methodDesc.substring(parenEnd + 1);
                returnType = typeRecoverer.recoverType(retDesc);
            }
        }
        Expression value = exprRecoverer.recoverOperand(ret.getReturnValue(), returnType);
        value = applyReturnTypeCoercion(value, returnType);
        ReturnStmt returnStmt = new ReturnStmt(value);
        returnStmt.setMethodReturnType(returnType);
        return returnStmt;
    }

    private Expression applyReturnTypeCoercion(Expression expr, SourceType returnType) {
        if (returnType == null) return expr;
        if (expr instanceof LiteralExpr) {
            LiteralExpr lit = (LiteralExpr) expr;
            Object val = lit.getValue();
            if (val instanceof Integer) {
                int intVal = (Integer) val;
                if (returnType == PrimitiveSourceType.BOOLEAN) {
                    return LiteralExpr.ofBoolean(intVal != 0);
                }
                if (returnType == PrimitiveSourceType.CHAR) {
                    return LiteralExpr.ofChar((char) intVal);
                }
            }
        }
        // `cond ? 1 : 0` returned from a boolean method is the JVM's int form of the boolean; in source it is
        // just `cond`. Folding it makes the round trip stable (recovery materializes the boolean differently on
        // recompiled vs javac bytecode, so one pass produces the ternary and the other the bare condition).
        if (returnType == PrimitiveSourceType.BOOLEAN && expr instanceof TernaryExpr) {
            TernaryExpr tern = (TernaryExpr) expr;
            Integer thenV = intLiteralValue(tern.getThenExpr());
            Integer elseV = intLiteralValue(tern.getElseExpr());
            if (thenV != null && elseV != null && thenV == 1 && elseV == 0) {
                return tern.getCondition();
            }
        }
        return expr;
    }

    /** The int value of {@code e} when it is an integer literal, else null. */
    private Integer intLiteralValue(Expression e) {
        if (e instanceof LiteralExpr && ((LiteralExpr) e).getValue() instanceof Integer) {
            return (Integer) ((LiteralExpr) e).getValue();
        }
        return null;
    }


    private Statement recoverVarDecl(IRInstruction instr) {
        SSAValue result = instr.getResult();
        String name = context.getExpressionContext().getVariableName(result);
        if (name == null) {
            name = "v" + result.getId();
        }

        SourceType type = typeRecoverer.recoverType(result);
        Expression value = exprRecoverer.recover(instr);

        context.getExpressionContext().cacheExpression(result, value);

        context.getExpressionContext().markMaterialized(result);
        context.getExpressionContext().setVariableName(result, name);

        if (context.getExpressionContext().isDeclared(name)) {
            VarRefExpr target = new VarRefExpr(name, type, result);
            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
        }

        context.getExpressionContext().markDeclared(name);

        return new VarDeclStmt(type, name, value);
    }

    /**
     * Rescues a branch whose body recovered to empty because its only path is a goto into a
     * pure-exit block (throw / void-return) that an enclosing region adopted as its merge/stop
     * block. Without this, such a branch collapses to {@code if (cond) {}} and the exit silently
     * vanishes (e.g. the second guard of a shared-throw bounds check). The exit block is recovered
     * and cached so the enclosing region's own emission of that block stays consistent.
     */
    private List<Statement> emitExitIfBranchVanished(List<Statement> branchStmts, IRBlock branchTarget) {
        if (!branchStmts.isEmpty() || branchTarget == null || !analyzer.isPureExitBlock(branchTarget)) {
            return branchStmts;
        }
        IRBlock exit = branchTarget;
        Set<IRBlock> seen = new HashSet<>();
        while (exit != null && seen.add(exit) && !exit.getSuccessors().isEmpty()) {
            exit = exit.getSuccessors().iterator().next();
        }
        if (exit == null) {
            return branchStmts;
        }
        if (context.isProcessed(exit)) {
            return new ArrayList<>(context.getStatements(exit));
        }
        List<Statement> exitStmts = recoverSimpleBlock(exit);
        if (exitStmts.isEmpty()) {
            return branchStmts;
        }
        context.setStatements(exit, exitStmts);
        context.markProcessed(exit);
        return exitStmts;
    }

    private Statement recoverIfThen(IRBlock header, RegionInfo info) {
        context.markProcessed(header);

        List<Statement> headerStmts = recoverBlockInstructions(header);

        SSAValue conditionValue = getConditionValue(header);
        CompareOp conditionOp = getConditionOp(header);

        if (info.isConditionNegated() && isConditionKnownFalse(conditionValue)) {
            if (conditionOp == CompareOp.IFNE) {
                Set<IRBlock> stopBlocks = new HashSet<>(context.getAllStopBlocks());
                if (info.getMergeBlock() != null) {
                    stopBlocks.add(info.getMergeBlock());
                }
                context.pushStopBlocks(stopBlocks);
                context.getExpressionContext().pushBranchScope();
                List<Statement> thenStmts;
                try {
                    thenStmts = recoverBlockSequence(info.getThenBlock(), stopBlocks);
                } finally {
                    context.getExpressionContext().popBranchScope();
                    context.popStopBlocks();
                }
                if (!headerStmts.isEmpty()) {
                    context.addPendingStatements(headerStmts);
                }
                if (thenStmts.isEmpty()) {
                    return null;
                } else if (thenStmts.size() == 1) {
                    return thenStmts.get(0);
                } else {
                    return new BlockStmt(thenStmts);
                }
            }
        }

        // For a short-circuit compound condition the true edge reaches the body (then) and the false
        // edge the merge; reconstruct the whole boolean expression over its condition blocks.
        Expression condition = info.getConditionBlocks() != null
                ? reconstructCompoundCondition(header, info.getThenBlock(), info.getMergeBlock(), info.getConditionBlocks())
                : null;
        if (condition == null) {
            condition = recoverCondition(header, info.isConditionNegated());
        }

        // A loop-body conditional whose then-arm is the enclosing loop's continue target and whose merge
        // is the loop's exit is a conditional break: entering the then loops back, falling through leaves.
        // Recover it as `if (breakCond) break;` - the then-arm is the implicit continue, and the exit is a
        // shared block the enclosing structure emits once (never this branch's private return).
        if (info.getConditionBlocks() == null && info.getThenBlock() != null && info.getMergeBlock() != null) {
            ControlFlowContext.LoopJump thenJump = context.classifyLoopJump(info.getThenBlock());
            ControlFlowContext.LoopJump mergeJump = context.classifyLoopJump(info.getMergeBlock());
            if (thenJump != null && thenJump.kind == ControlFlowContext.JumpKind.CONTINUE
                    && mergeJump != null && mergeJump.kind == ControlFlowContext.JumpKind.BREAK) {
                Expression breakCond = recoverCondition(header, !info.isConditionNegated());
                Statement brk = mergeJump.loopHeader != null
                        ? new BreakStmt(context.getOrCreateLabel(mergeJump.loopHeader))
                        : new BreakStmt();
                IfStmt guard = new IfStmt(breakCond, brk, null);
                stampFromHeader(guard, header);
                if (!headerStmts.isEmpty()) {
                    context.addPendingStatements(headerStmts);
                }
                return guard;
            }
        }

        Set<IRBlock> stopBlocks = new HashSet<>(context.getAllStopBlocks());
        if (info.getMergeBlock() != null) {
            stopBlocks.add(info.getMergeBlock());
        }

        Set<SSAValue> knownFalse = new HashSet<>();
        Set<FieldKey> knownFalseFields = new HashSet<>();
        boolean canTrackKnownFalse = (conditionOp == CompareOp.IFNE && info.isConditionNegated())
                                  || (conditionOp == CompareOp.IFEQ && !info.isConditionNegated());
        if (canTrackKnownFalse && conditionValue != null) {
            knownFalse.add(conditionValue);
            FieldKey fieldKey = extractFieldKey(conditionValue);
            if (fieldKey != null) {
                knownFalseFields.add(fieldKey);
            }
        }
        context.pushKnownFalseValues(knownFalse);
        context.pushKnownFalseFields(knownFalseFields);
        context.pushStopBlocks(stopBlocks);
        context.getExpressionContext().pushBranchScope();
        List<Statement> thenStmts;
        try {
            thenStmts = recoverBlockSequence(info.getThenBlock(), stopBlocks);
        } finally {
            context.getExpressionContext().popBranchScope();
            context.popStopBlocks();
            context.popKnownFalseFields();
            context.popKnownFalseValues();
        }

        thenStmts = emitExitIfBranchVanished(thenStmts, info.getThenBlock());

        if (isAndConditionChain(thenStmts)) {
            Statement mergedIf = mergeAndConditions(condition, thenStmts);
            stampFromHeader(mergedIf, header);
            if (!headerStmts.isEmpty()) {
                context.addPendingStatements(headerStmts);
            }
            return mergedIf;
        }

        BlockStmt thenBlock = new BlockStmt(thenStmts);

        IfStmt ifStmt = new IfStmt(condition, thenBlock, null);
        stampFromHeader(ifStmt, header);

        if (!headerStmts.isEmpty()) {
            context.addPendingStatements(headerStmts);
        }
        return ifStmt;
    }

    private Statement recoverGuardClause(IRBlock header, RegionInfo info) {
        List<Statement> headerStmts = recoverBlockInstructions(header);

        // For guard clauses, conditionNegated=true means we need to negate the bytecode condition
        // to get the guard clause condition (the condition under which we exit early)
        Expression condition = recoverCondition(header, info.isConditionNegated());

        IRBlock exitBlock = info.getThenBlock();
        List<Statement> exitStmts = recoverSimpleBlock(exitBlock);
        context.markProcessed(exitBlock);

        Statement exitStmt;
        if (exitStmts.isEmpty()) {
            exitStmt = new BlockStmt(Collections.emptyList());
        } else if (exitStmts.size() == 1) {
            exitStmt = exitStmts.get(0);
        } else {
            exitStmt = new BlockStmt(exitStmts);
        }

        IfStmt guardStmt = new IfStmt(condition, exitStmt, null);
        stampFromHeader(guardStmt, header);

        if (!headerStmts.isEmpty()) {
            context.addPendingStatements(headerStmts);
        }

        return guardStmt;
    }

    /**
     * Reconstructs a short-circuit compound condition ({@code A && B}, {@code A || B}, or any mix) as
     * one boolean expression over its condition blocks, where the true edge reaches {@code trueExit}
     * and the false edge {@code falseExit}. Marks the non-header condition blocks processed. Returns
     * null when the DAG is not a clean short-circuit chain (the caller then recovers the header
     * condition alone).
     */
    private Expression reconstructCompoundCondition(IRBlock header, IRBlock trueExit, IRBlock falseExit,
                                                    Set<IRBlock> conditionBlocks) {
        Expression expr = CompoundConditionBuilder.build(header, trueExit, falseExit, conditionBlocks,
                this::recoverCondition);
        if (expr != null) {
            for (IRBlock conditionBlock : conditionBlocks) {
                if (conditionBlock != header) {
                    context.markProcessed(conditionBlock);
                }
            }
        }
        return expr;
    }

    /**
     * Counts the negation operators in a reconstructed condition, used to pick the polarity
     * (true-arm orientation) that reads with the fewest {@code !}s.
     */
    private int countNots(Expression expr) {
        if (expr == null) {
            return 0;
        }
        if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            int self = unary.getOperator() == UnaryOperator.NOT ? 1 : 0;
            return self + countNots(unary.getOperand());
        }
        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            return countNots(binary.getLeft()) + countNots(binary.getRight());
        }
        return 0;
    }

    private Statement recoverIfThenElse(IRBlock header, RegionInfo info) {
        context.markProcessed(header);

        List<Statement> headerStmts = recoverBlockInstructions(header);

        SSAValue conditionValue = getConditionValue(header);
        CompareOp conditionOp = getConditionOp(header);

        if (info.getConditionBlocks() == null && isConditionKnownFalse(conditionValue)) {
            if (conditionOp == CompareOp.IFNE) {
                Set<IRBlock> stopBlocks = new HashSet<>(context.getAllStopBlocks());
                if (info.getMergeBlock() != null) {
                    stopBlocks.add(info.getMergeBlock());
                }
                context.pushStopBlocks(stopBlocks);
                context.getExpressionContext().pushBranchScope();
                List<Statement> elseStmts;
                try {
                    elseStmts = recoverBlockSequence(info.getElseBlock(), stopBlocks);
                } finally {
                    context.getExpressionContext().popBranchScope();
                    context.popStopBlocks();
                }
                if (!headerStmts.isEmpty()) {
                    context.addPendingStatements(headerStmts);
                }
                if (elseStmts.isEmpty()) {
                    return null;
                } else if (elseStmts.size() == 1) {
                    return elseStmts.get(0);
                } else {
                    return new BlockStmt(elseStmts);
                }
            }
        }

        // For a short-circuit compound condition the true edge reaches the then arm and the false edge
        // the else arm; reconstruct the whole boolean expression over its condition blocks. Either exit
        // can be the then arm - orient toward the polarity that needs fewer negations so the condition
        // reads naturally (`a && b`, not `!a || !b` with the arms swapped).
        Expression condition = null;
        if (info.getConditionBlocks() != null) {
            Expression asThen = CompoundConditionBuilder.build(header, info.getThenBlock(),
                    info.getElseBlock(), info.getConditionBlocks(), this::recoverCondition);
            Expression asElse = CompoundConditionBuilder.build(header, info.getElseBlock(),
                    info.getThenBlock(), info.getConditionBlocks(), this::recoverCondition);
            if (asElse != null && (asThen == null || countNots(asElse) < countNots(asThen))) {
                IRBlock swap = info.getThenBlock();
                info.setThenBlock(info.getElseBlock());
                info.setElseBlock(swap);
                condition = asElse;
            } else {
                condition = asThen;
            }
            if (condition != null) {
                for (IRBlock conditionBlock : info.getConditionBlocks()) {
                    if (conditionBlock != header) {
                        context.markProcessed(conditionBlock);
                    }
                }
            }
        }
        if (condition == null) {
            condition = recoverCondition(header, info.isConditionNegated());
        }
        Set<IRBlock> stopBlocks = new HashSet<>(context.getAllStopBlocks());
        if (info.getMergeBlock() != null) {
            stopBlocks.add(info.getMergeBlock());
        }

        SSAValue condValue = getConditionValue(header);
        List<Statement> thenStmts;
        List<Statement> elseStmts;
        Set<SSAValue> knownFalseForThen = new HashSet<>();
        Set<SSAValue> knownFalseForElse = new HashSet<>();
        Set<FieldKey> knownFalseFieldsForThen = new HashSet<>();
        Set<FieldKey> knownFalseFieldsForElse = new HashSet<>();
        if (condValue != null && info.getConditionBlocks() == null) {
            FieldKey fieldKey = extractFieldKey(condValue);
            boolean knownFalseThen = (conditionOp == CompareOp.IFNE && info.isConditionNegated())
                                  || (conditionOp == CompareOp.IFEQ && !info.isConditionNegated());
            boolean knownFalseElse = (conditionOp == CompareOp.IFNE && !info.isConditionNegated())
                                  || (conditionOp == CompareOp.IFEQ && info.isConditionNegated());
            if (knownFalseThen) {
                knownFalseForThen.add(condValue);
                if (fieldKey != null) {
                    knownFalseFieldsForThen.add(fieldKey);
                }
            }
            if (knownFalseElse) {
                knownFalseForElse.add(condValue);
                if (fieldKey != null) {
                    knownFalseFieldsForElse.add(fieldKey);
                }
            }
        }
        context.pushStopBlocks(stopBlocks);
        context.pushKnownFalseValues(knownFalseForThen);
        context.pushKnownFalseFields(knownFalseFieldsForThen);
        context.getExpressionContext().pushBranchScope();
        try {
            thenStmts = recoverBlockSequence(info.getThenBlock(), stopBlocks);
        } finally {
            context.getExpressionContext().popBranchScope();
            context.popKnownFalseFields();
            context.popKnownFalseValues();
        }
        context.pushKnownFalseValues(knownFalseForElse);
        context.pushKnownFalseFields(knownFalseFieldsForElse);
        context.getExpressionContext().pushBranchScope();
        try {
            elseStmts = recoverBlockSequence(info.getElseBlock(), stopBlocks);
        } finally {
            context.getExpressionContext().popBranchScope();
            context.popKnownFalseFields();
            context.popKnownFalseValues();
            context.popStopBlocks();
        }

        if (isBooleanReturnPattern(thenStmts, elseStmts)) {
            Statement booleanReturn = collapseToBooleanReturn(condition, thenStmts);
            if (!headerStmts.isEmpty()) {
                context.addPendingStatements(headerStmts);
            }
            return booleanReturn;
        }

        if (isOrConditionChain(thenStmts, elseStmts)) {
            Statement mergedIf = mergeOrConditions(condition, thenStmts, elseStmts);
            stampFromHeader(mergedIf, header);
            if (!headerStmts.isEmpty()) {
                context.addPendingStatements(headerStmts);
            }
            return mergedIf;
        }

        boolean boolPhiPattern = isBooleanPhiReturnPattern(thenStmts, elseStmts, info.getMergeBlock());
        if (boolPhiPattern) {
            Statement booleanReturn = collapsePhiToBooleanReturn(condition, info.getMergeBlock());
            if (booleanReturn != null) {
                if (info.getMergeBlock() != null) {
                    context.markProcessed(info.getMergeBlock());
                }
                if (!headerStmts.isEmpty()) {
                    context.addPendingStatements(headerStmts);
                }
                return booleanReturn;
            }
        }

        PhiInstruction booleanPhi = findBooleanPhiAssignmentPatternIR(info);
        if (booleanPhi != null) {
            collapseToBooleanPhiExpressionIR(condition, booleanPhi, info.getThenBlock());
            context.markProcessed(info.getThenBlock());
            context.markProcessed(info.getElseBlock());
            if (!headerStmts.isEmpty()) {
                context.addPendingStatements(headerStmts);
                return new BlockStmt(new ArrayList<>());
            }
            return null;
        }

        PhiInstruction ternaryPhi = findTernaryPhiPattern(info);
        // Don't collapse to a ternary when the merged value feeds ANOTHER phi (a nested if/else-if merge):
        // the collapsed value is cached but the outer merge can't consume it as a statement, so its arm would
        // be orphaned and dropped. Recover this if/else as a statement so the outer recovery keeps it. A value
        // that is also stored into a local is exempt: the store materializes it as a real assignment (`v = t`),
        // and any phi that reads it (e.g. a loop-carried counter) consumes the local, so nothing is orphaned.
        if (ternaryPhi != null && getPhiUsingValue(ternaryPhi.getResult()) != null
                && !hasStoreLocalUse(ternaryPhi.getResult())) {
            ternaryPhi = null;
        }
        if (ternaryPhi != null) {
            collapseToTernaryPhiExpression(condition, ternaryPhi, info.getThenBlock(), info.getElseBlock());
            context.markProcessed(info.getThenBlock());
            context.markProcessed(info.getElseBlock());
            if (!headerStmts.isEmpty()) {
                context.addPendingStatements(headerStmts);
            }
            return null;
        }

        BlockStmt thenBlock = new BlockStmt(thenStmts);
        BlockStmt elseBlock = new BlockStmt(elseStmts);

        IfStmt ifStmt = new IfStmt(condition, thenBlock, elseBlock);
        stampFromHeader(ifStmt, header);

        if (!headerStmts.isEmpty()) {
            context.addPendingStatements(headerStmts);
        }
        return ifStmt;
    }

    /**
     * Recovers non-terminator instructions from a block.
     * This is used to emit setup instructions before structured control flow.
     */
    private List<Statement> recoverBlockInstructions(IRBlock block) {
        List<Statement> statements = new ArrayList<>();

        for (IRInstruction instr : block.getInstructions()) {
            if (instr.isTerminator()) {
                continue;
            }
            if (context.shouldSkipInstruction(instr)) {
                continue;
            }
            Statement stmt = recoverInstruction(instr);
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        return statements;
    }

    private Statement recoverWhileLoop(IRBlock header, RegionInfo info) {
        context.markProcessed(header);

        Set<IRBlock> stopBlocks = new HashSet<>();
        stopBlocks.add(header);
        if (info.getLoopExit() != null) {
            stopBlocks.add(info.getLoopExit());
        } else if (info.getLoop() != null) {
            Set<IRBlock> loopBlocks = info.getLoop().getBlocks();
            for (IRBlock loopBlock : loopBlocks) {
                for (IRBlock succ : loopBlock.getSuccessors()) {
                    if (!loopBlocks.contains(succ) && !isMethodExitBlock(succ)) {
                        stopBlocks.add(succ);
                    }
                }
            }
        }

        // Push stop blocks so inner control structures respect loop exits
        context.pushStopBlocks(stopBlocks);
        context.pushLoop(header, header, info.getLoopExit());
        try {
            // An at-top induction update (javac's `for (i = n; --i >= 0;)` puts `i = i +/- c` in the
            // header, before the exit test) must be lifted or it is dropped and the loop reads
            // out of bounds. Lift it only when the header branch is a GENUINE loop exit whose taken
            // edge leaves the loop for good: when the "exit" block immediately re-enters the loop it
            // is a continue in disguise (e.g. a `\r`-skip straight back to the header), not the exit,
            // so turning it into a break would corrupt the loop. Restricted to constant induction
            // steps - a header that instead fuses reads/assigns into the condition (a tokenizer's
            // `c = (char) read()`) is left to the faithful $pc$ dispatch rather than mis-structured.
            boolean genuineExit = info.getLoopExit() != null && info.getLoop() != null
                    && info.getLoopExit().getSuccessors().stream()
                        .noneMatch(s -> info.getLoop().getBlocks().contains(s));
            List<Statement> headerStmts = genuineExit
                    ? recoverHeaderLeadingStatements(header, -1, true)
                    : java.util.Collections.emptyList();
            if (!headerStmts.isEmpty()) {
                // Emit the induction update at the top of the body and turn the loop condition into
                // an early break, keeping the update on every iteration.
                List<Statement> bodyStmts = recoverBlockSequence(info.getLoopBody(), stopBlocks);
                stripTrailingContinue(bodyStmts);
                List<Statement> merged = new ArrayList<>(headerStmts);
                Expression breakCond = recoverCondition(header, !info.isConditionNegated());
                List<Statement> breakBody = new ArrayList<>();
                breakBody.add(new BreakStmt());
                merged.add(new IfStmt(breakCond, new BlockStmt(breakBody)));
                merged.addAll(bodyStmts);
                WhileStmt whileStmt = new WhileStmt(LiteralExpr.ofBoolean(true), new BlockStmt(merged));
                stampFromHeader(whileStmt, header);
                return labelIfTargeted(header, whileStmt);
            }

            Expression condition = recoverCondition(header, info.isConditionNegated());
            List<Statement> bodyStmts = recoverBlockSequence(info.getLoopBody(), stopBlocks);
            stripTrailingContinue(bodyStmts);
            WhileStmt whileStmt = new WhileStmt(condition, new BlockStmt(bodyStmts));
            stampFromHeader(whileStmt, header);
            return labelIfTargeted(header, whileStmt);
        } finally {
            context.popLoop();
            context.popStopBlocks();
        }
    }

    private Statement recoverDoWhileLoop(IRBlock header, RegionInfo info) {
        if (info.getLatchBlock() != null) {
            return recoverLatchDoWhile(header, info);
        }
        context.markProcessed(header);

        Set<IRBlock> stopBlocks = new HashSet<>();
        stopBlocks.add(header);
        if (info.getLoopExit() != null) {
            stopBlocks.add(info.getLoopExit());
        } else if (info.getLoop() != null) {
            Set<IRBlock> loopBlocks = info.getLoop().getBlocks();
            for (IRBlock loopBlock : loopBlocks) {
                for (IRBlock succ : loopBlock.getSuccessors()) {
                    if (!loopBlocks.contains(succ) && !isMethodExitBlock(succ)) {
                        stopBlocks.add(succ);
                    }
                }
            }
        }

        context.pushStopBlocks(stopBlocks);
        try {
            // Single-block do-while: body, bottom condition and back-edge share the header, so its
            // own non-terminator instructions are the body (recoverBlockSequence would stop on the
            // header immediately, since it is its own stop block).
            List<Statement> bodyStmts = info.getLoopBody() == header
                    ? recoverBlockInstructions(header)
                    : recoverBlockSequence(info.getLoopBody(), stopBlocks);
            BlockStmt body = new BlockStmt(bodyStmts);
            Expression condition = recoverCondition(header, info.isConditionNegated());
            DoWhileStmt doWhileStmt = new DoWhileStmt(body, condition);
            stampFromHeader(doWhileStmt, header);
            return doWhileStmt;
        } finally {
            context.popStopBlocks();
        }
    }

    /**
     * Recovers a bottom-tested do-while whose header carries body control flow. The body runs
     * from the header (temporarily reclassified as its own conditional so the sequence walk does
     * not re-enter the loop) up to the latch, whose leading instructions close the body and whose
     * branch is the loop condition.
     */
    private Statement recoverLatchDoWhile(IRBlock header, RegionInfo info) {
        IRBlock latch = info.getLatchBlock();
        Set<IRBlock> stopBlocks = new HashSet<>();
        stopBlocks.add(latch);
        if (info.getLoopExit() != null) {
            stopBlocks.add(info.getLoopExit());
        }
        context.pushStopBlocks(stopBlocks);
        // The latch is a continue target only when it is the bare test: jumping to an impure latch
        // runs its trailing body statements first, which a source-level continue would skip.
        context.pushLoop(header, latchIsBareTest(latch) ? latch : null, info.getLoopExit());
        Map<IRBlock, RegionInfo> infos = analyzer.getRegionInfos();
        RegionInfo headerView = info.getHeaderConditional() != null
                ? info.getHeaderConditional()
                : new RegionInfo(ControlFlowContext.StructuredRegion.SEQUENCE, header);
        RegionInfo saved = infos.put(header, headerView);
        try {
            List<Statement> bodyStmts = new ArrayList<>(recoverBlockSequence(header, stopBlocks));
            stripTrailingContinue(bodyStmts);
            bodyStmts.addAll(recoverBlockInstructions(latch));
            context.markProcessed(latch);
            Expression condition = recoverCondition(latch, info.isConditionNegated());
            DoWhileStmt doWhileStmt = new DoWhileStmt(new BlockStmt(bodyStmts), condition);
            stampFromHeader(doWhileStmt, header);
            return labelIfTargeted(header, doWhileStmt);
        } finally {
            infos.put(header, saved);
            context.popLoop();
            context.popStopBlocks();
        }
    }

    /** True when the latch holds nothing but its branch — the shape a source continue can target. */
    private boolean latchIsBareTest(IRBlock latch) {
        for (IRInstruction instr : latch.getInstructions()) {
            if (!instr.isTerminator() && instr.getResult() == null) {
                return false;
            }
        }
        return true;
    }

    /** Wraps a recovered loop in a {@link LabeledStmt} when an inner non-local jump created a label for its header. */
    private Statement labelIfTargeted(IRBlock loopHeader, Statement loop) {
        return context.hasLabel(loopHeader) ? new LabeledStmt(context.getLabel(loopHeader), loop) : loop;
    }

    /** Drops a redundant trailing unlabeled {@code continue} (the body's natural fall-through to the next iteration). */
    private void stripTrailingContinue(List<Statement> stmts) {
        if (!stmts.isEmpty()) {
            Statement last = stmts.get(stmts.size() - 1);
            if (last instanceof ContinueStmt && !((ContinueStmt) last).hasLabel()) {
                stmts.remove(stmts.size() - 1);
            }
        }
    }

    /** True when a loop's increment block contains only the induction update (no merged body work). */
    private boolean isIncrementBlockPure(IRBlock block, Set<IRInstruction> incrementInstructions) {
        if (block == null) {
            return true;
        }
        // The increment block may be suppressed (folded into the for-update) only if it contains NOTHING but
        // the loop-counter update: every non-terminator instruction must be the update itself or transitively
        // feed it. A previous check only flagged non-increment local stores, so a side-effecting call sharing
        // the block (e.g. `clearPassword(); i = i + 1;`) was silently dropped - which broke the loop's
        // `continue` and let the recovery bleed into and duplicate the block after it.
        Map<SSAValue, IRInstruction> defs = new HashMap<>();
        for (IRInstruction instr : block.getInstructions()) {
            if (instr.getResult() != null) {
                defs.put(instr.getResult(), instr);
            }
        }
        Set<IRInstruction> needed = new HashSet<>(incrementInstructions);
        List<IRInstruction> work = new ArrayList<>(incrementInstructions);
        while (!work.isEmpty()) {
            IRInstruction instr = work.remove(work.size() - 1);
            for (Value operand : instr.getOperands()) {
                if (operand instanceof SSAValue) {
                    IRInstruction def = defs.get((SSAValue) operand);
                    if (def != null && needed.add(def)) {
                        work.add(def);
                    }
                }
            }
        }
        for (IRInstruction instr : block.getInstructions()) {
            if (instr.isTerminator()) {
                continue;
            }
            if (!needed.contains(instr)) {
                // A dead pure value (an unused `load_local`/`const` the iinc lift leaves behind, whose
                // value the induction actually reads from the header phi) is SSA noise, not body work
                // merged into the increment. Ignore it; only genuine work makes the block impure.
                if (isDeadPureInstruction(instr)) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    /** A value-producing instruction with no side effect whose result is unused (dead SSA noise). */
    private boolean isDeadPureInstruction(IRInstruction instr) {
        if (!(instr instanceof LoadLocalInstruction || instr instanceof ConstantInstruction
                || instr instanceof BinaryOpInstruction || instr instanceof UnaryOpInstruction)) {
            return false;
        }
        SSAValue result = instr.getResult();
        return result != null && result.getUses().isEmpty();
    }

    private Statement recoverForLoop(IRBlock header, RegionInfo info) {
        context.markProcessed(header);

        IRBlock incrementBlock = info.getIncrementBlock();
        int targetLocal = info.getInductionLocalIndex();

        if (incrementBlock == null || targetLocal < 0) {
            // No usable counter store: the "increment" the classifier found is not a real for-counter
            // (targetLocal < 0 is a standalone ADD/SUB such as an address computation `base + i*stride`
            // in the body). Recover as a while loop, which lifts an at-top induction update the header
            // itself performs (`for (i = n; --i >= 0;)`); a plain while that ignored that update would
            // drop it and loop forever.
            return recoverWhileLoop(header, info);
        }

        context.getExpressionContext().pushForLoopScope();
        try {
            List<Statement> initStmts = new ArrayList<>();

            for (IRBlock pred : header.getPredecessors()) {
                if (info.getLoop() != null && info.getLoop().contains(pred)) {
                    continue;
                }

                for (IRInstruction instr : pred.getInstructions()) {
                    if (instr instanceof StoreLocalInstruction) {
                        StoreLocalInstruction store = (StoreLocalInstruction) instr;
                        if (store.getLocalIndex() == targetLocal) {
                            Statement initStmt = recoverStoreLocalAsForInit(store);
                            initStmts.add(initStmt);
                        }
                    }
                }
            }

            List<Statement> headerStmts = recoverHeaderLeadingStatements(header, targetLocal);

            Expression condition = recoverCondition(header, info.isConditionNegated());

            List<Expression> updateExprs = new ArrayList<>();
            Set<IRInstruction> incrementInstructions = new HashSet<>();
            for (IRInstruction instr : incrementBlock.getInstructions()) {
                if (instr.isTerminator()) continue;

                if (instr instanceof StoreLocalInstruction) {
                    StoreLocalInstruction store = (StoreLocalInstruction) instr;
                    if (store.getLocalIndex() == targetLocal) {
                        Expression updateExpr = recoverUpdateExpression(store);
                        updateExprs.add(updateExpr);
                        incrementInstructions.add(instr);
                    }
                }
            }

            Set<IRBlock> stopBlocks = new HashSet<>();
            stopBlocks.add(header);
            if (info.getLoopExit() != null) {
                stopBlocks.add(info.getLoopExit());
            } else if (info.getLoop() != null) {
                Set<IRBlock> loopBlocks = info.getLoop().getBlocks();
                for (IRBlock loopBlock : loopBlocks) {
                    for (IRBlock succ : loopBlock.getSuccessors()) {
                        if (!loopBlocks.contains(succ)) {
                            stopBlocks.add(succ);
                        }
                    }
                }
            }

            IRBlock bodyBlock = info.getLoopBody();
            boolean bodyIsIncrement = bodyBlock == incrementBlock;
            // The increment block is PURE when it holds only the induction update. An impure one also
            // carries body work javac merged into it (e.g. `publish(); Thread.sleep(); i++;`): it must
            // NOT be folded into the for-update, and it must NOT be the continue target — jumping there
            // would skip its trailing body statements (mirrors recoverWhileLoop's impure-latch guard).
            boolean incrementPure = !bodyIsIncrement
                    && isIncrementBlockPure(incrementBlock, incrementInstructions);

            context.pushStopBlocks(stopBlocks);
            context.pushSkipInstructions(incrementInstructions);
            context.pushLoop(header, incrementPure ? incrementBlock : null, info.getLoopExit());
            try {
                if (incrementPure) {
                    context.markProcessed(incrementBlock);
                }

                List<Statement> bodyStmts = recoverBlockSequence(bodyBlock, stopBlocks);
                stripTrailingContinue(bodyStmts);

                ForStmt forStmt;
                if (headerStmts.isEmpty()) {
                    forStmt = new ForStmt(initStmts, condition, updateExprs, new BlockStmt(bodyStmts));
                } else {
                    // The header recomputes a value each iteration (e.g. a loop bound like min(x,10) the
                    // emitter materialized into a header local because the operand stack must be empty at the
                    // back-edge) - it cannot sit in the for-condition slot. Emit those statements at the top
                    // of the body and turn the loop condition into an early break, keeping the loop
                    // structured and round-trip stable instead of dropping the call (which would force the
                    // whole method into the $pc$ dispatch fallback on refresh).
                    List<Statement> merged = new ArrayList<>(headerStmts);
                    Expression breakCond = recoverCondition(header, !info.isConditionNegated());
                    List<Statement> breakBody = new ArrayList<>();
                    breakBody.add(new BreakStmt());
                    merged.add(new IfStmt(breakCond, new BlockStmt(breakBody)));
                    merged.addAll(bodyStmts);
                    forStmt = new ForStmt(initStmts, null, updateExprs, new BlockStmt(merged));
                }
                stampFromHeader(forStmt, header);
                return labelIfTargeted(header, forStmt);
            } finally {
                context.popLoop();
                context.popSkipInstructions();
                context.popStopBlocks();
            }
        } finally {
            context.getExpressionContext().popForLoopScope();
        }
    }

    /**
     * Recovers a loop header's leading statements - non-induction local stores. Normally empty (a clean
     * header is just the condition and induction phi); non-empty only when the emitter materialized a
     * per-iteration computation (e.g. a re-evaluated loop bound) into a header local, which the standard
     * for-loop shape would otherwise drop.
     */
    private List<Statement> recoverHeaderLeadingStatements(IRBlock header, int inductionLocal) {
        return recoverHeaderLeadingStatements(header, inductionLocal, false);
    }

    /**
     * When {@code inductionOnly} is set, restricts recovery to constant induction steps of their own
     * slot ({@code i = i +/- const}) - the update javac lifts to a loop's header for a pre-inc/dec
     * idiom. Other header stores are left alone (they belong to the plain-while or $pc$ recovery).
     */
    private List<Statement> recoverHeaderLeadingStatements(IRBlock header, int inductionLocal, boolean inductionOnly) {
        List<Statement> stmts = new ArrayList<>();
        for (IRInstruction instr : header.getInstructions()) {
            if (!(instr instanceof StoreLocalInstruction)) {
                continue;
            }
            StoreLocalInstruction st = (StoreLocalInstruction) instr;
            if (st.getLocalIndex() == inductionLocal || context.shouldSkipInstruction(instr)) {
                continue;
            }
            if (inductionOnly && !isConstantInductionStore(st)) {
                continue;
            }

            // Recover the stored VALUE from its defining expression (the materialized call), not the slot
            // variable: the value is named after its own slot, so the usual store recovery collapses it to a
            // dropped self-assignment (local = local) and loses the call. Recovering the definition yields
            // the actual `min(x, 10)`.
            Value value = st.getValue();
            Expression valueExpr;
            if (value instanceof SSAValue && ((SSAValue) value).getDefinition() != null) {
                valueExpr = exprRecoverer.recover(((SSAValue) value).getDefinition());
            } else {
                valueExpr = exprRecoverer.recoverOperand(value);
            }

            // Name + type via the reaching-definition partition (NOT the slot's last name), so a reused slot
            // resolves to the SAME variable the condition's load uses - otherwise the bound assigns to a
            // different (wrongly-typed) partition than the comparison reads.
            SourceType valueType = typeRecoverer.recoverType(value);
            String name = partitionName(st);
            if (name == null) {
                name = getNameForLocalSlotWithType(st.getLocalIndex(), valueType);
            }
            SourceType type = getLocalSlotUnifiedType(name);
            if (type == null) {
                type = valueType;
            }
            if (context.getExpressionContext().isDeclared(name)) {
                stmts.add(new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN,
                        new VarRefExpr(name, type, null), valueExpr, type)));
            } else {
                context.getExpressionContext().markDeclared(name);
                stmts.add(new VarDeclStmt(type, name, valueExpr));
            }
        }
        return stmts;
    }

    /**
     * True when a store writes a constant induction step of its own slot ({@code i = i +/- const}) -
     * the shape javac lifts to a loop header for a pre-increment/decrement condition like {@code --i}.
     */
    private boolean isConstantInductionStore(StoreLocalInstruction store) {
        if (!(store.getValue() instanceof SSAValue)) {
            return false;
        }
        IRInstruction def = ((SSAValue) store.getValue()).getDefinition();
        if (!(def instanceof BinaryOpInstruction)) {
            return false;
        }
        BinaryOpInstruction bin = (BinaryOpInstruction) def;
        if (bin.getOp() != BinaryOp.ADD && bin.getOp() != BinaryOp.SUB) {
            return false;
        }
        boolean leftConst = isConstantIrValue(bin.getLeft());
        boolean rightConst = isConstantIrValue(bin.getRight());
        if (leftConst == rightConst) {
            return false;
        }
        Value nonConst = leftConst ? bin.getRight() : bin.getLeft();
        if (!(nonConst instanceof SSAValue)) {
            return false;
        }
        IRInstruction nonConstDef = ((SSAValue) nonConst).getDefinition();
        // A non-loop step reads the slot directly.
        if (nonConstDef instanceof LoadLocalInstruction) {
            return ((LoadLocalInstruction) nonConstDef).getLocalIndex() == store.getLocalIndex();
        }
        // The usual loop shape: the slot's header value is a phi merging the initial value with this
        // store's own result on the back-edge (`i_phi = φ(init, i_next); i_next = i_phi +/- const`).
        if (nonConstDef instanceof PhiInstruction) {
            for (Value incoming : ((PhiInstruction) nonConstDef).getIncomingValues().values()) {
                if (incoming == store.getValue()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isConstantIrValue(Value v) {
        return v instanceof Constant
                || (v instanceof SSAValue && ((SSAValue) v).getDefinition() instanceof ConstantInstruction);
    }

    private Statement recoverStoreLocalAsForInit(StoreLocalInstruction store) {
        int localIndex = store.getLocalIndex();
        Value storedValue = store.getValue();

        SourceType storedType = typeRecoverer.recoverType(storedValue);
        String localName = partitionName(store);
        if (localName == null) {
            localName = getNameForLocalSlotWithType(localIndex, storedType);
        }

        // The init value is frequently coalesced into this same loop-variable partition (e.g. the
        // GETFIELD feeding `for (int i = this.position; ...)`), so a plain recovery would render it
        // as the variable's own name and produce a self-referential `int i = i`. Un-materialize it
        // across recovery so its defining expression is inlined, mirroring recoverStoreLocal.
        boolean shouldUnmaterialize = false;
        if (storedValue instanceof SSAValue) {
            SSAValue ssaValue = (SSAValue) storedValue;
            boolean wasMaterialized = context.getExpressionContext().isMaterialized(ssaValue);
            int previousSlot = context.getExpressionContext().getSSAValueSlot(ssaValue);
            String valueName = context.getExpressionContext().getVariableName(ssaValue);
            boolean declaredArrayInit = ssaValue.getDefinition() instanceof NewArrayInstruction
                    && isUsedByArrayStore(ssaValue)
                    && valueName != null
                    && context.getExpressionContext().isDeclared(valueName);
            shouldUnmaterialize = wasMaterialized && !declaredArrayInit
                    && (previousSlot == -1 || previousSlot == localIndex);
            if (shouldUnmaterialize) {
                context.getExpressionContext().unmarkMaterialized(ssaValue);
            }
        }

        Expression valueExpr = recoverExpressionDirectly(storedValue, storedType);

        if (shouldUnmaterialize) {
            context.getExpressionContext().markMaterialized((SSAValue) storedValue);
        }

        if (context.getExpressionContext().isDeclared(localName)) {
            VarRefExpr target = new VarRefExpr(localName, storedType, null);
            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, valueExpr, storedType));
        } else {
            context.getExpressionContext().markDeclaredInForLoopInit(localName);
            return new VarDeclStmt(storedType, localName, valueExpr);
        }
    }

    private Expression recoverUpdateExpression(StoreLocalInstruction store) {
        int localIndex = store.getLocalIndex();
        Value storedValue = store.getValue();

        SourceType storedType = typeRecoverer.recoverType(storedValue);
        String localName = partitionName(store);
        if (localName == null) {
            localName = getNameForLocalSlotWithType(localIndex, storedType);
        }

        Expression valueExpr = recoverExpressionDirectly(storedValue, storedType);

        VarRefExpr target = new VarRefExpr(localName, storedType, null);
        return new BinaryExpr(BinaryOperator.ASSIGN, target, valueExpr, storedType);
    }

    private Expression recoverExpressionDirectly(Value value, SourceType typeHint) {
        if (value instanceof Constant) {
            return exprRecoverer.recoverConstant((Constant) value, typeHint);
        }

        SSAValue ssa = (SSAValue) value;
        IRInstruction def = ssa.getDefinition();

        if (def instanceof ConstantInstruction) {
            return exprRecoverer.recoverConstant(((ConstantInstruction) def).getConstant(), typeHint);
        }

        if (def instanceof BinaryOpInstruction) {
            BinaryOpInstruction binOp = (BinaryOpInstruction) def;
            Expression left = recoverExpressionOrVarRef(binOp.getLeft());
            Expression right = recoverExpressionOrVarRef(binOp.getRight());
            BinaryOperator op = mapBinaryOp(binOp.getOp());
            SourceType resultType = typeRecoverer.recoverType(ssa);
            return new BinaryExpr(op, left, right, resultType);
        }

        return exprRecoverer.recoverOperand(value, typeHint);
    }

    private Expression recoverExpressionOrVarRef(Value value) {
        if (value instanceof Constant) {
            return exprRecoverer.recoverConstant((Constant) value, null);
        }

        SSAValue ssa = (SSAValue) value;
        IRInstruction def = ssa.getDefinition();

        if (def instanceof ConstantInstruction) {
            return exprRecoverer.recoverConstant(((ConstantInstruction) def).getConstant(), null);
        }

        if (def instanceof LoadLocalInstruction) {
            LoadLocalInstruction load = (LoadLocalInstruction) def;
            int localIndex = load.getLocalIndex();
            SourceType type = typeRecoverer.recoverType(ssa);
            String name = partitionName(load);
            if (name == null) {
                name = getNameForLocalSlotWithType(localIndex, type);
            }
            return new VarRefExpr(name, type, ssa);
        }

        if (def instanceof PhiInstruction) {
            PhiInstruction phi = (PhiInstruction) def;
            for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
                Value incoming = entry.getValue();
                if (incoming instanceof SSAValue) {
                    SSAValue incomingSSA = (SSAValue) incoming;
                    if (incomingSSA.getDefinition() instanceof LoadLocalInstruction) {
                        LoadLocalInstruction load = (LoadLocalInstruction) incomingSSA.getDefinition();
                        int localIndex = load.getLocalIndex();
                        SourceType type = typeRecoverer.recoverType(incomingSSA);
                        String name = partitionName(load);
                        if (name == null) {
                            name = getNameForLocalSlotWithType(localIndex, type);
                        }
                        return new VarRefExpr(name, type, incomingSSA);
                    }
                }
            }
        }

        return exprRecoverer.recoverOperand(value);
    }

    private BinaryOperator mapBinaryOp(BinaryOp op) {
        switch (op) {
            case SUB: return BinaryOperator.SUB;
            case MUL: return BinaryOperator.MUL;
            case DIV: return BinaryOperator.DIV;
            case REM: return BinaryOperator.MOD;
            case AND: return BinaryOperator.BAND;
            case OR: return BinaryOperator.BOR;
            case XOR: return BinaryOperator.BXOR;
            case SHL: return BinaryOperator.SHL;
            case SHR: return BinaryOperator.SHR;
            case USHR: return BinaryOperator.USHR;
            default: return BinaryOperator.ADD;
        }
    }

    /**
     * If {@code value} is a single-use operand of a phi whose variable is already declared — a
     * structured switch-expression / if-merge — returns the {@code phiVar = expr} assignment that
     * destructs the phi at this predecessor block. The structured recovery path has no
     * {@code lowerPhisOnEdge} step, so without this a non-constant arm value (e.g. a string concat)
     * feeding the merge phi would be silently dropped. Returns null otherwise (caller caches).
     */
    private Statement phiCopyForDeclaredMerge(SSAValue value, Expression expr) {
        PhiInstruction targetPhi = getPhiUsingValue(value);
        if (targetPhi == null || targetPhi.getResult() == null) {
            return null;
        }
        if (context.isForLoopInductionPhi(targetPhi.getResult()) || selfStorePhis.contains(targetPhi)) {
            return null;
        }
        String phiVarName = context.getExpressionContext().getVariableName(targetPhi.getResult());
        if (phiVarName == null || !context.getExpressionContext().isDeclared(phiVarName)
                || phiVarName.equals("this")
                || isParameterOrThisRef(targetPhi.getResult())) {
            return null;
        }
        SourceType type = getLocalSlotUnifiedType(phiVarName);
        if (type == null) {
            type = expr.getType();
        }
        if (type == null) {
            type = typeRecoverer.recoverType(value);
        }
        VarRefExpr target = new VarRefExpr(phiVarName, type, targetPhi.getResult());
        return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, expr, type));
    }

    /**
     * Reconstructed {@code switch} on a {@code String}. javac lowers it to two phases: a {@code switch} on
     * {@code s.hashCode()} whose cases run {@code s.equals("literal")} guards and, on a match, assign a dense
     * index to a synthetic local, followed by a second {@code switch} on that index holding the real bodies.
     */
    private static final class StringSwitchInfo {
        final Value stringValue;
        final Map<String, Integer> literalToIndex;
        final IRBlock mergeBlock;
        final SwitchInstruction indexSwitch;
        final Set<IRBlock> scaffolding;

        StringSwitchInfo(Value stringValue, Map<String, Integer> literalToIndex, IRBlock mergeBlock,
                         SwitchInstruction indexSwitch, Set<IRBlock> scaffolding) {
            this.stringValue = stringValue;
            this.literalToIndex = literalToIndex;
            this.mergeBlock = mergeBlock;
            this.indexSwitch = indexSwitch;
            this.scaffolding = scaffolding;
        }
    }

    private static final class EqualsStep {
        final String literal;
        final IRBlock matchBlock;
        final IRBlock noMatchBlock;

        EqualsStep(String literal, IRBlock matchBlock, IRBlock noMatchBlock) {
            this.literal = literal;
            this.matchBlock = matchBlock;
            this.noMatchBlock = noMatchBlock;
        }
    }

    /**
     * Recognizes javac's two-phase {@code String} switch rooted at a {@code switch (s.hashCode())} and returns
     * the data needed to rebuild a single {@code switch (s)}, or null if {@code header} is an ordinary switch.
     */
    private StringSwitchInfo detectStringSwitch(IRBlock header) {
        IRInstruction term = header.getTerminator();
        if (!(term instanceof SwitchInstruction)) {
            return null;
        }
        SwitchInstruction hashSwitch = (SwitchInstruction) term;

        Value key = hashSwitch.getKey();
        if (!(key instanceof SSAValue)) {
            return null;
        }
        IRInstruction keyDef = ((SSAValue) key).getDefinition();
        if (!(keyDef instanceof InvokeInstruction)) {
            return null;
        }
        InvokeInstruction hashCall = (InvokeInstruction) keyDef;
        if (!"hashCode".equals(hashCall.getName()) || !"()I".equals(hashCall.getDescriptor())) {
            return null;
        }
        Value stringValue = hashCall.getReceiver();
        if (stringValue == null) {
            return null;
        }

        Map<String, Integer> literalToIndex = new LinkedHashMap<>();
        Set<IRBlock> scaffolding = new HashSet<>();
        scaffolding.add(header);
        IRBlock mergeBlock = null;

        for (IRBlock caseTarget : hashSwitch.getCases().values()) {
            IRBlock current = caseTarget;
            Set<IRBlock> guard = new HashSet<>();
            while (current != null && guard.add(current)) {
                EqualsStep step = matchEqualsStep(current);
                if (step == null) {
                    return null;
                }
                Integer index = indexAssignedIn(step.matchBlock);
                IRBlock matchMerge = singleSuccessor(step.matchBlock);
                if (index == null || matchMerge == null) {
                    return null;
                }
                if (mergeBlock == null) {
                    mergeBlock = matchMerge;
                } else if (mergeBlock != matchMerge) {
                    return null;
                }
                literalToIndex.put(step.literal, index);
                scaffolding.add(current);
                scaffolding.add(step.matchBlock);
                // Several strings can share a hashCode: the no-match edge then tests the next equals.
                current = (matchEqualsStep(step.noMatchBlock) != null) ? step.noMatchBlock : null;
            }
        }

        if (literalToIndex.isEmpty()) {
            return null;
        }
        IRInstruction mergeTerm = mergeBlock.getTerminator();
        if (!(mergeTerm instanceof SwitchInstruction)) {
            return null;
        }
        SwitchInstruction indexSwitch = (SwitchInstruction) mergeTerm;
        for (Integer index : literalToIndex.values()) {
            if (!indexSwitch.getCases().containsKey(index)) {
                return null;
            }
        }
        scaffolding.add(mergeBlock);
        return new StringSwitchInfo(stringValue, literalToIndex, mergeBlock, indexSwitch, scaffolding);
    }

    /** The block following the entire string switch (where non-returning cases converge), or null. */
    private IRBlock stringSwitchExit(StringSwitchInfo info) {
        var postDom = analyzer.getPostDominatorTree();
        if (postDom == null) {
            return null;
        }
        IRBlock exit = postDom.getImmediatePostDominator(info.mergeBlock);
        if (exit == null || info.scaffolding.contains(exit) || info.indexSwitch.getCases().containsValue(exit)) {
            return null;
        }
        return exit;
    }

    private EqualsStep matchEqualsStep(IRBlock block) {
        if (block == null) {
            return null;
        }
        InvokeInstruction equalsCall = null;
        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof InvokeInstruction) {
                InvokeInstruction invoke = (InvokeInstruction) instr;
                if ("equals".equals(invoke.getName()) && "(Ljava/lang/Object;)Z".equals(invoke.getDescriptor())) {
                    equalsCall = invoke;
                }
            }
        }
        if (equalsCall == null || equalsCall.getMethodArguments().size() != 1) {
            return null;
        }
        String literal = stringConstantOf(equalsCall.getMethodArguments().get(0));
        if (literal == null) {
            return null;
        }
        if (!(block.getTerminator() instanceof BranchInstruction)) {
            return null;
        }
        BranchInstruction branch = (BranchInstruction) block.getTerminator();
        if (branch.getLeft() != equalsCall.getResult()) {
            return null;
        }
        if (branch.getCondition() == CompareOp.IFEQ) {
            return new EqualsStep(literal, branch.getFalseTarget(), branch.getTrueTarget());
        }
        if (branch.getCondition() == CompareOp.IFNE) {
            return new EqualsStep(literal, branch.getTrueTarget(), branch.getFalseTarget());
        }
        return null;
    }

    private Integer indexAssignedIn(IRBlock block) {
        if (block == null) {
            return null;
        }
        Integer index = null;
        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof StoreLocalInstruction) {
                Integer constant = intConstantOf(((StoreLocalInstruction) instr).getValue());
                if (constant != null) {
                    index = constant;
                }
            }
        }
        return index;
    }

    private IRBlock singleSuccessor(IRBlock block) {
        if (block == null) {
            return null;
        }
        IRInstruction term = block.getTerminator();
        if (term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.GOTO) {
            return ((SimpleInstruction) term).getTarget();
        }
        return block.getSuccessors().size() == 1 ? block.getSuccessors().iterator().next() : null;
    }

    private String stringConstantOf(Value value) {
        if (!(value instanceof SSAValue)) {
            return null;
        }
        IRInstruction def = ((SSAValue) value).getDefinition();
        if (def instanceof ConstantInstruction && ((ConstantInstruction) def).getConstant() instanceof StringConstant) {
            return ((StringConstant) ((ConstantInstruction) def).getConstant()).getValue();
        }
        return null;
    }

    private Integer intConstantOf(Value value) {
        if (!(value instanceof SSAValue)) {
            return null;
        }
        IRInstruction def = ((SSAValue) value).getDefinition();
        if (def instanceof ConstantInstruction && ((ConstantInstruction) def).getConstant() instanceof IntConstant) {
            return ((IntConstant) ((ConstantInstruction) def).getConstant()).getValue();
        }
        return null;
    }

    /**
     * Rebuilds a {@code switch (s)} from a detected two-phase string switch. Each {@code equals} literal maps to
     * a dense index, and that index's body in the second switch becomes the string case's body; index-switch
     * cases sharing a body collapse into consecutive {@code case "x":} labels.
     */
    private Statement recoverStringSwitch(IRBlock header, StringSwitchInfo info, IRBlock exit) {
        SwitchInstruction indexSwitch = info.indexSwitch;
        Expression selector = exprRecoverer.recoverOperand(info.stringValue);

        for (IRBlock block : info.scaffolding) {
            context.markProcessed(block);
            context.setStatements(block, Collections.emptyList());
        }

        Map<Integer, List<String>> indexToLiterals = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : info.literalToIndex.entrySet()) {
            indexToLiterals.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        // Every case body stops at the other case bodies and at the block following the whole switch, so a
        // body that breaks does not bleed into a sibling case or the continuation.
        Set<IRBlock> bodyStops = new LinkedHashSet<>(indexSwitch.getCases().values());
        if (indexSwitch.getDefaultTarget() != null) {
            bodyStops.add(indexSwitch.getDefaultTarget());
        }
        if (exit != null) {
            bodyStops.add(exit);
        }

        Map<IRBlock, List<String>> bodyToLiterals = new LinkedHashMap<>();
        for (Map.Entry<Integer, IRBlock> entry : indexSwitch.getCases().entrySet()) {
            List<String> literals = indexToLiterals.get(entry.getKey());
            if (literals != null) {
                bodyToLiterals.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).addAll(literals);
            }
        }

        List<SwitchCase> cases = new ArrayList<>();
        for (Map.Entry<IRBlock, List<String>> entry : bodyToLiterals.entrySet()) {
            List<Statement> stmts = recoverStringSwitchBody(entry.getKey(), bodyStops);
            List<Expression> labels = new ArrayList<>();
            for (String literal : entry.getValue()) {
                labels.add(LiteralExpr.ofString(literal));
            }
            cases.add(SwitchCase.ofExpressions(labels, stmts));
        }

        if (indexSwitch.getDefaultTarget() != null) {
            cases.add(SwitchCase.defaultCase(recoverStringSwitchBody(indexSwitch.getDefaultTarget(), bodyStops)));
        }

        Statement switchStmt = new SwitchStmt(selector, cases);
        stampFromHeader(switchStmt, header);
        return switchStmt;
    }

    private List<Statement> recoverStringSwitchBody(IRBlock body, Set<IRBlock> bodyStops) {
        Set<IRBlock> stopBlocks = new HashSet<>(bodyStops);
        stopBlocks.remove(body);
        context.pushStopBlocks(stopBlocks);
        try {
            return recoverBlockSequence(body, stopBlocks);
        } finally {
            context.popStopBlocks();
        }
    }

    private Statement recoverSwitch(IRBlock header, RegionInfo info) {
        context.markProcessed(header);

        List<Statement> headerStmts = recoverBlockInstructions(header);

        IRInstruction terminator = header.getTerminator();
        Expression selector;
        if (terminator instanceof SwitchInstruction) {
            selector = exprRecoverer.recoverOperand(((SwitchInstruction) terminator).getKey());
        } else if (info.getSwitchSelector() != null) {
            // Comparison-chain switch synthesized by StructuralAnalyzer: the header's
            // terminator is a branch, and the selector is carried on the region.
            selector = exprRecoverer.recoverOperand(info.getSwitchSelector());
        } else {
            return new IRRegionStmt(List.of(header));
        }

        // Detect and simplify enum switch map pattern:
        // SwitchMapClass.$SwitchMap$pkg$EnumName[enumVar.ordinal()] -> enumVar
        EnumSwitchInfo enumInfo = detectEnumSwitchPattern(selector);
        boolean enumNamesResolved = false;
        if (enumInfo != null) {
            if (enumInfo.enumClassName != null && allEnumCasesResolve(info, enumInfo)) {
                // Enum constants resolved (the $SwitchMap$ holder is available): switch (e) { case CONST: }.
                selector = enumInfo.enumVariable;
                enumNamesResolved = true;
            } else {
                // Holder class not in the pool, so the dense $SwitchMap$ indices cannot be mapped to
                // constant names. Fall back to switch (e.ordinal()) with the raw indices, which recompiles.
                selector = enumInfo.ordinalExpression;
            }
        }

        List<SwitchCase> cases = new ArrayList<>();

        IRBlock mergeBlock = findSwitchMerge(info);
        Set<IRBlock> baseStopBlocks = new HashSet<>();
        if (mergeBlock != null) {
            baseStopBlocks.add(mergeBlock);
        }
        // For a synthesized comparison-chain switch, the dispatch spine blocks are not
        // case bodies; stop there so a fall-through case body cannot bleed into the chain.
        if (info.getSwitchSpineBlocks() != null) {
            baseStopBlocks.addAll(info.getSwitchSpineBlocks());
        }

        Map<IRBlock, List<Integer>> targetToCases = new LinkedHashMap<>();
        for (Map.Entry<Integer, IRBlock> entry : info.getSwitchCases().entrySet()) {
            targetToCases.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        Set<IRBlock> allCaseTargets = new HashSet<>(targetToCases.keySet());
        if (info.getDefaultTarget() != null) {
            allCaseTargets.add(info.getDefaultTarget());
        }

        // A case "falls through" only if it reaches ANOTHER case BODY - never the switch's merge/exit block.
        // When the default is empty its target IS the merge, so a case that breaks (jumps to the merge) would
        // otherwise look like a fall-through into the default and lose its `break`. Exclude the merge.
        Set<IRBlock> fallThroughTargets = new HashSet<>(allCaseTargets);
        if (mergeBlock != null) {
            fallThroughTargets.remove(mergeBlock);
        }

        for (Map.Entry<IRBlock, List<Integer>> entry : targetToCases.entrySet()) {
            IRBlock target = entry.getKey();
            List<Integer> labels = entry.getValue();

            Set<IRBlock> stopBlocks = new HashSet<>(baseStopBlocks);
            for (IRBlock otherTarget : allCaseTargets) {
                if (otherTarget != target) {
                    stopBlocks.add(otherTarget);
                }
            }

            context.pushStopBlocks(stopBlocks);
            List<Statement> caseStmts;
            try {
                caseStmts = recoverBlockSequence(target, stopBlocks);
            } finally {
                context.popStopBlocks();
            }

            boolean fallsThrough = caseFallsThrough(target, stopBlocks, fallThroughTargets);

            if (enumNamesResolved) {
                List<Expression> enumLabels = new ArrayList<>();
                for (Integer caseValue : labels) {
                    String constantName = EnumSwitchMapRegistry.getInstance()
                            .lookupEnumConstant(enumInfo.holderClass, enumInfo.enumClassName, caseValue);
                    SourceType enumType = new ReferenceSourceType(enumInfo.enumClassName, Collections.emptyList());
                    enumLabels.add(FieldAccessExpr.staticField(enumInfo.enumClassName, constantName, enumType));
                }
                cases.add(SwitchCase.ofExpressions(enumLabels, caseStmts).withFallsThrough(fallsThrough));
                continue;
            }

            cases.add(SwitchCase.of(labels, caseStmts).withFallsThrough(fallsThrough));
        }

        if (info.getDefaultTarget() != null) {
            if (mergeBlock != null && info.getDefaultTarget() == mergeBlock) {
                cases.add(SwitchCase.defaultCase(Collections.emptyList()));
            } else {
                Set<IRBlock> defaultStopBlocks = new HashSet<>(baseStopBlocks);
                for (IRBlock otherTarget : allCaseTargets) {
                    if (otherTarget != info.getDefaultTarget()) {
                        defaultStopBlocks.add(otherTarget);
                    }
                }
                context.pushStopBlocks(defaultStopBlocks);
                List<Statement> defaultStmts;
                try {
                    defaultStmts = recoverBlockSequence(info.getDefaultTarget(), defaultStopBlocks);
                } finally {
                    context.popStopBlocks();
                }
                // A `return`/`throw` tail shared by the default and a case body (e.g.
                // `case: if (a && b) return true; default: return false;`) is consumed when the case
                // absorbs it, leaving the default empty and the method falling off its end. Re-emit the
                // terminator directly for the default so its exit is preserved.
                IRInstruction defTerm = info.getDefaultTarget().getTerminator();
                boolean defIsTerminal = defTerm instanceof ReturnInstruction
                        || (defTerm instanceof SimpleInstruction && ((SimpleInstruction) defTerm).getOp() == SimpleOp.ATHROW);
                if (defaultStmts.isEmpty() && defIsTerminal) {
                    defaultStmts = recoverSimpleBlock(info.getDefaultTarget());
                }
                cases.add(SwitchCase.defaultCase(defaultStmts));
            }
        }

        Statement switchStmt = new SwitchStmt(selector, cases);
        stampFromHeader(switchStmt, header);
        if (!headerStmts.isEmpty()) {
            List<Statement> combined = new ArrayList<>(headerStmts);
            combined.add(switchStmt);
            return new BlockStmt(combined);
        }
        return switchStmt;
    }

    private static class EnumSwitchInfo {
        Expression enumVariable;
        Expression ordinalExpression;
        String enumClassName;
        String holderClass;
    }

    private EnumSwitchInfo detectEnumSwitchPattern(Expression selector) {
        if (!(selector instanceof ArrayAccessExpr)) {
            return null;
        }

        ArrayAccessExpr arrayAccess = (ArrayAccessExpr) selector;
        Expression array = arrayAccess.getArray();
        Expression index = arrayAccess.getIndex();

        if (!(array instanceof FieldAccessExpr)) {
            return null;
        }

        FieldAccessExpr fieldAccess = (FieldAccessExpr) array;
        String fieldName = fieldAccess.getFieldName();

        if (!fieldName.startsWith("$SwitchMap$")) {
            return null;
        }

        if (!(index instanceof MethodCallExpr)) {
            return null;
        }

        MethodCallExpr methodCall = (MethodCallExpr) index;
        if (!"ordinal".equals(methodCall.getMethodName())) {
            return null;
        }

        Expression enumVar = methodCall.getReceiver();
        if (enumVar == null) {
            return null;
        }

        EnumSwitchInfo info = new EnumSwitchInfo();
        info.enumVariable = enumVar;
        info.ordinalExpression = methodCall;
        info.enumClassName = EnumSwitchMapRegistry.parseEnumClassFromFieldName(fieldName);
        info.holderClass = fieldAccess.getOwnerClass();
        return info;
    }

    /** True when every case value of an enum switch resolves to a constant name via the switch-map registry. */
    private boolean allEnumCasesResolve(RegionInfo info, EnumSwitchInfo enumInfo) {
        EnumSwitchMapRegistry registry = EnumSwitchMapRegistry.getInstance();
        if (!registry.hasMapping(enumInfo.holderClass, enumInfo.enumClassName)) {
            return false;
        }
        for (Integer caseValue : info.getSwitchCases().keySet()) {
            if (registry.lookupEnumConstant(enumInfo.holderClass, enumInfo.enumClassName, caseValue) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when a case body flows off its end into another case body (source-level fall-through), as opposed
     * to breaking to the merge block or returning/throwing. The case region is everything reachable from
     * {@code caseTarget} without crossing a stop block; an edge from that region into a different case target
     * is a fall-through (javac never branches between case bodies except by falling through).
     */
    private boolean caseFallsThrough(IRBlock caseTarget, Set<IRBlock> stopBlocks, Set<IRBlock> caseTargets) {
        Set<IRBlock> region = new HashSet<>();
        Deque<IRBlock> work = new ArrayDeque<>();
        region.add(caseTarget);
        work.add(caseTarget);
        while (!work.isEmpty()) {
            IRBlock b = work.poll();
            for (IRBlock s : b.getSuccessors()) {
                if (s != caseTarget && caseTargets.contains(s)) {
                    return true;
                }
                if (!stopBlocks.contains(s) && region.add(s)) {
                    work.add(s);
                }
            }
        }
        return false;
    }

    private Statement recoverIrreducible(IRBlock header) {
        Set<IRBlock> blocks = new HashSet<>();
        collectReachableBlocks(header, blocks, new HashSet<>());
        return new IRRegionStmt(new ArrayList<>(blocks));
    }

    private static final String DISPATCH_LABEL = "$dispatch$";

    /** Invoke names that legitimately have no MethodCallExpr in faithful output because the
     * recovery folds them into syntax (string concat -> +, boxing, for-each, constructors -> new). */
    private static final Set<String> FOLDED_CALL_NAMES = new HashSet<>(Arrays.asList(
        "<init>", "<clinit>", "append", "toString", "valueOf",
        "intValue", "longValue", "doubleValue", "floatValue", "booleanValue",
        "byteValue", "shortValue", "charValue",
        "iterator", "hasNext", "next", "makeConcatWithConstants",
        // ordinal() is folded into switch(enumVar) syntax by the $SwitchMap$ enum-switch idiom
        "ordinal",
        // hashCode()/equals() are folded into switch(stringVar) syntax by the String-switch idiom
        "hashCode", "equals"));

    /**
     * Completeness invariant: reports whether any observable operation reachable from the entry in
     * the bytecode is absent from the recovered source AST. This is the authoritative signal that
     * the structured recovery dropped an operation — by any mechanism (never-visited block,
     * irreducible region, or a block recovered then discarded during region assembly). Compares the
     * set of reachable IR method calls (keyed owner.name, excluding folded-into-syntax calls)
     * against the calls actually present in {@code body}.
     */
    public boolean hasDroppedOperations(BlockStmt body) {
        IRMethod method = context.getIrMethod();
        IRBlock entry = method.getEntryBlock();
        if (entry == null) {
            return false;
        }
        Set<IRBlock> reachable = new HashSet<>();
        collectReachableBlocks(entry, reachable);

        Set<String> irCalls = new HashSet<>();
        for (IRBlock block : reachable) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof InvokeInstruction) {
                    InvokeInstruction inv = (InvokeInstruction) instr;
                    if (FOLDED_CALL_NAMES.contains(inv.getName())) {
                        continue;
                    }
                    if (inv.isDynamic()) {
                        continue;
                    }
                    irCalls.add(simpleName(inv.getOwner()) + "." + inv.getName());
                }
            }
        }
        if (irCalls.isEmpty()) {
            return false;
        }

        Set<String> astCalls = new HashSet<>();
        body.walk(node -> {
            if (node instanceof MethodCallExpr) {
                MethodCallExpr call = (MethodCallExpr) node;
                astCalls.add(simpleName(call.getOwnerClass()) + "." + call.getMethodName());
            }
        });

        for (String key : irCalls) {
            if (!astCalls.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static String simpleName(String owner) {
        if (owner == null) {
            return "";
        }
        int slash = owner.lastIndexOf('/');
        int dot = owner.lastIndexOf('.');
        int cut = Math.max(slash, dot);
        return cut >= 0 ? owner.substring(cut + 1) : owner;
    }

    /**
     * Recovers the whole method as a structured dispatch loop
     * ({@code int $pc$ = entry; $dispatch$: while(true){ switch($pc$){ case L: ...; $pc$ = T; break; } }}),
     * which is complete and faithful for any (reducible or irreducible) CFG. Used as the fallback
     * when {@link #hasDroppedOperations(BlockStmt)} is true; must run on a fresh context.
     */
    public BlockStmt recoverMethodAsDispatch() {
        IRMethod method = context.getIrMethod();
        IRBlock entry = method.getEntryBlock();
        if (entry == null) {
            return new BlockStmt(Collections.emptyList());
        }
        List<Statement> statements = new ArrayList<>();
        detectSelfStorePhis(method);
        collectForLoopInitInstructions();
        registerPendingNewInstructions(method);
        emitPhiDeclarations(method, statements);

        Set<IRBlock> reachable = new HashSet<>();
        collectReachableBlocks(entry, reachable);
        List<IRBlock> ordered = new ArrayList<>();
        for (IRBlock block : method.getBlocksInOrder()) {
            if (reachable.contains(block)) {
                ordered.add(block);
            }
        }
        hoistDispatchLocals(ordered, statements);
        statements.addAll(buildDispatchLoop(ordered, entry));
        return new BlockStmt(statements);
    }

    /**
     * Pre-declares, at method scope, every non-phi local stored within the dispatch block set so a
     * declaration inside one switch case is neither out of scope nor "might not be initialized" in
     * a sibling case. Mirrors recoverStoreLocal's naming so subsequent stores become assignments
     * (the decl-vs-assign decision keys on isDeclared).
     */
    private void hoistDispatchLocals(List<IRBlock> blocks, List<Statement> statements) {
        Set<String> done = new HashSet<>();
        for (IRBlock block : blocks) {
            for (IRInstruction instr : block.getInstructions()) {
                if (!(instr instanceof StoreLocalInstruction)) {
                    continue;
                }
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                String name = partitionName(store);
                if (name == null) {
                    name = getNameForLocalSlotWithType(store.getLocalIndex(),
                        typeRecoverer.recoverType(store.getValue()));
                }
                if (name == null || name.equals("this")
                        || context.getExpressionContext().isParameterOrThisSlot(store.getLocalIndex())) {
                    continue;
                }
                if (!done.add(name) || context.getExpressionContext().isDeclared(name)) {
                    continue;
                }
                SourceType type = getLocalSlotUnifiedType(name);
                if (type == null) {
                    type = typeRecoverer.recoverType(store.getValue());
                }
                if (type == null) {
                    type = PrimitiveSourceType.INT;
                }
                statements.add(new VarDeclStmt(type, name, getDefaultValue(type)));
                context.getExpressionContext().markDeclaredWithType(name, type);
            }
        }
    }

    private List<Statement> buildDispatchLoop(List<IRBlock> ordered, IRBlock entry) {
        Map<IRBlock, Integer> pc = new LinkedHashMap<>();
        int next = 0;
        for (IRBlock block : ordered) {
            pc.put(block, next++);
        }
        String pcName = "$pc$";
        while (context.getExpressionContext().isDeclared(pcName)) {
            pcName = pcName + "$";
        }

        List<Statement> out = new ArrayList<>();
        out.add(new VarDeclStmt(PrimitiveSourceType.INT, pcName,
            LiteralExpr.ofInt(pc.getOrDefault(entry, 0))));

        List<SwitchCase> cases = new ArrayList<>();
        for (IRBlock block : ordered) {
            List<Statement> body = new ArrayList<>(recoverSimpleBlock(block));
            body.addAll(dispatchCaseTail(block, pc, pcName));
            context.markProcessed(block);
            cases.add(SwitchCase.of(pc.get(block), body));
        }
        List<Statement> def = new ArrayList<>();
        def.add(new BreakStmt(DISPATCH_LABEL));
        cases.add(SwitchCase.defaultCase(def));

        SwitchStmt sw = new SwitchStmt(new VarRefExpr(pcName, PrimitiveSourceType.INT), cases);
        List<Statement> loopBody = new ArrayList<>();
        loopBody.add(sw);
        WhileStmt loop = new WhileStmt(LiteralExpr.ofBoolean(true), new BlockStmt(loopBody));
        out.add(new LabeledStmt(DISPATCH_LABEL, loop));
        return out;
    }

    private Statement assignPc(String pcName, Map<IRBlock, Integer> pc, IRBlock target) {
        int label = pc.getOrDefault(target, -1);
        return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN,
            new VarRefExpr(pcName, PrimitiveSourceType.INT),
            LiteralExpr.ofInt(label), PrimitiveSourceType.INT));
    }

    /**
     * Translates a block's terminator into the dispatch-loop case tail: phi copies on the taken
     * edge, the {@code $pc$ = target} assignment, then a break. Return/throw blocks are already
     * emitted by recoverSimpleBlock and need no tail.
     */
    private List<Statement> dispatchCaseTail(IRBlock block, Map<IRBlock, Integer> pc, String pcName) {
        List<Statement> tail = new ArrayList<>();
        IRInstruction term = block.getTerminator();

        if (term instanceof ReturnInstruction) {
            return tail;
        }
        if (term instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) term;
            if (simple.getOp() == SimpleOp.ATHROW) {
                return tail;
            }
            if (simple.getOp() == SimpleOp.GOTO && simple.getTarget() != null) {
                IRBlock t = simple.getTarget();
                tail.addAll(lowerPhisOnEdge(block, t));
                tail.add(assignPc(pcName, pc, t));
                tail.add(new BreakStmt());
                return tail;
            }
        }
        if (term instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) term;
            IRBlock t = branch.getTrueTarget();
            IRBlock f = branch.getFalseTarget();
            Expression cond = recoverCondition(block, false);
            List<Statement> thenS = new ArrayList<>(lowerPhisOnEdge(block, t));
            thenS.add(assignPc(pcName, pc, t));
            List<Statement> elseS = new ArrayList<>(lowerPhisOnEdge(block, f));
            elseS.add(assignPc(pcName, pc, f));
            tail.add(new IfStmt(cond, new BlockStmt(thenS), new BlockStmt(elseS)));
            tail.add(new BreakStmt());
            return tail;
        }
        if (term instanceof SwitchInstruction) {
            SwitchInstruction switchInstr = (SwitchInstruction) term;
            Expression key = exprRecoverer.recoverOperand(switchInstr.getKey());
            List<SwitchCase> inner = new ArrayList<>();
            for (Map.Entry<Integer, IRBlock> e : switchInstr.getCases().entrySet()) {
                List<Statement> cs = new ArrayList<>(lowerPhisOnEdge(block, e.getValue()));
                cs.add(assignPc(pcName, pc, e.getValue()));
                cs.add(new BreakStmt());
                inner.add(SwitchCase.of(e.getKey(), cs));
            }
            List<Statement> ds = new ArrayList<>(lowerPhisOnEdge(block, switchInstr.getDefaultTarget()));
            ds.add(assignPc(pcName, pc, switchInstr.getDefaultTarget()));
            ds.add(new BreakStmt());
            inner.add(SwitchCase.defaultCase(ds));
            tail.add(new SwitchStmt(key, inner));
            tail.add(new BreakStmt());
            return tail;
        }

        Set<IRBlock> succs = block.getSuccessors();
        if (succs.size() == 1) {
            IRBlock s = succs.iterator().next();
            tail.addAll(lowerPhisOnEdge(block, s));
            tail.add(assignPc(pcName, pc, s));
            tail.add(new BreakStmt());
        } else {
            tail.add(new BreakStmt(DISPATCH_LABEL));
        }
        return tail;
    }

    /**
     * SSA destruction on a CFG edge: for each phi at {@code succ}, assign the phi variable the
     * value coming from {@code pred}, placed before the {@code $pc$} update so it runs when the
     * edge is taken. Reuses the phi result's already-bound name and method-scope declaration.
     */
    @Override
    public List<Statement> lowerPhisOnEdge(IRBlock pred, IRBlock succ) {
        List<Statement> copies = new ArrayList<>();
        for (PhiInstruction phi : succ.getPhiInstructions()) {
            SSAValue result = phi.getResult();
            if (result == null) {
                continue;
            }
            String target = context.getExpressionContext().getVariableName(result);
            if (target == null || target.equals("this")
                    || isParameterOrThisRef(result)) {
                continue;
            }
            Value incoming = phi.getIncoming(pred);
            if (incoming == null) {
                continue;
            }
            SourceType type = getLocalSlotUnifiedType(target);
            if (type == null) {
                type = typeRecoverer.recoverType(result);
            }
            Expression rhs = exprRecoverer.recoverOperand(incoming, type);
            if (rhs instanceof VarRefExpr && target.equals(((VarRefExpr) rhs).getName())) {
                continue;
            }
            copies.add(new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN,
                new VarRefExpr(target, type), rhs, type)));
        }
        return copies;
    }

    /**
     * Folds a three-way-compare result tested against zero into a direct relational comparison:
     * {@code lcmp/fcmp/dcmp(a, b) <branchCond> 0} becomes {@code a <branchCond> b}. Returns null when the
     * branch's left operand is not a three-way compare, leaving the generic compare-to-zero handling.
     */
    private Expression recoverThreeWayCompare(BranchInstruction branch, boolean negate) {
        if (!(branch.getLeft() instanceof SSAValue)) {
            return null;
        }
        IRInstruction def = ((SSAValue) branch.getLeft()).getDefinition();
        if (!(def instanceof BinaryOpInstruction)) {
            return null;
        }
        BinaryOpInstruction cmp = (BinaryOpInstruction) def;
        switch (cmp.getOp()) {
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                break;
            default:
                return null;
        }
        Expression a = exprRecoverer.recoverOperand(cmp.getLeft());
        Expression b = exprRecoverer.recoverOperand(cmp.getRight());
        BinaryOperator op = OperatorMapper.mapCompareOp(branch.getCondition());
        if (negate) {
            op = negateOperator(op);
        }
        // fcmpl/dcmpl bias NaN to -1, fcmpg/dcmpg to +1, so the int test reading in the biased
        // direction is NOT the plain relational: dcmpg >= 0 is !(a < b), true for NaN, while
        // a >= b is false. Emit the negated complement for those combinations.
        boolean nanGreater = cmp.getOp() == BinaryOp.FCMPG || cmp.getOp() == BinaryOp.DCMPG;
        boolean floatCmp = cmp.getOp() != BinaryOp.LCMP;
        if (floatCmp && (nanGreater
                ? (op == BinaryOperator.GT || op == BinaryOperator.GE)
                : (op == BinaryOperator.LT || op == BinaryOperator.LE))) {
            BinaryExpr complement = new BinaryExpr(negateOperator(op), a, b, PrimitiveSourceType.BOOLEAN);
            return new UnaryExpr(UnaryOperator.NOT, complement, PrimitiveSourceType.BOOLEAN);
        }
        return new BinaryExpr(op, a, b, PrimitiveSourceType.BOOLEAN);
    }

    @Override
    public Expression recoverCondition(IRBlock block, boolean negate) {
        IRInstruction terminator = block.getTerminator();
        if (terminator instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) terminator;

            // `Xcmp(a,b) <cond> 0` - an lcmp/fcmp/dcmp result tested by a compare-to-zero branch - is the
            // direct relational comparison `a <cond> b`. Recovering it as `(a - b) <cond> 0` is wrong-looking
            // and non-idempotent: it re-lowers to `lcmp((a-b), 0L)`, which then recovers as `(a - b - 0)
            // <cond> 0`, accumulating a spurious `- 0` on every round trip.
            if (branch.getRight() == null) {
                Expression threeWay = recoverThreeWayCompare(branch, negate);
                if (threeWay != null) {
                    return threeWay;
                }
            }

            Expression left = exprRecoverer.recoverOperand(branch.getLeft());
            CompareOp condition = branch.getCondition();

            if (branch.getRight() != null) {
                Expression right = exprRecoverer.recoverOperand(branch.getRight());
                BinaryOperator op =
                    OperatorMapper.mapCompareOp(condition);
                if (negate) {
                    op = negateOperator(op);
                }
                return new BinaryExpr(
                    op, left, right, PrimitiveSourceType.BOOLEAN);
            }

            if (OperatorMapper.isNullCheck(condition)) {
                Expression nullExpr = LiteralExpr.ofNull();
                BinaryOperator op =
                    OperatorMapper.mapCompareOp(condition);
                if (negate) {
                    op = negateOperator(op);
                }
                return new BinaryExpr(
                    op, left, nullExpr, PrimitiveSourceType.BOOLEAN);
            }

            if (isBooleanExpression(left) || isBooleanSSAValue(branch.getLeft())) {
                boolean wantTrue = (condition == CompareOp.NE || condition == CompareOp.IFNE);
                if (negate) {
                    wantTrue = !wantTrue;
                }
                if (wantTrue) {
                    return left;
                } else {
                    return new UnaryExpr(UnaryOperator.NOT, left,
                        PrimitiveSourceType.BOOLEAN);
                }
            }

            BinaryOperator op =
                OperatorMapper.mapCompareOp(condition);
            if (negate) {
                op = negateOperator(op);
            }
            Expression zero = LiteralExpr.ofInt(0);
            return new BinaryExpr(
                op, left, zero, PrimitiveSourceType.BOOLEAN);
        }
        return LiteralExpr.ofBoolean(!negate);
    }

    @Override
    public boolean conditionInlinesSideEffect(IRBlock block) {
        IRInstruction terminator = block.getTerminator();
        if (!(terminator instanceof BranchInstruction)) {
            return false;
        }
        BranchInstruction branch = (BranchInstruction) terminator;
        if (branch.getLeft() != null && exprRecoverer.operandInlinesSideEffect(branch.getLeft())) {
            return true;
        }
        return branch.getRight() != null && exprRecoverer.operandInlinesSideEffect(branch.getRight());
    }

    @Override
    public boolean guardAtomExceptionFree(IRBlock block) {
        IRInstruction terminator = block.getTerminator();
        if (!(terminator instanceof BranchInstruction)) {
            return false;
        }
        BranchInstruction branch = (BranchInstruction) terminator;
        if (branch.getLeft() != null && exprRecoverer.operandMayThrowInline(branch.getLeft())) {
            return false;
        }
        return branch.getRight() == null || !exprRecoverer.operandMayThrowInline(branch.getRight());
    }

    @Override
    public boolean isDuplicationSafe(IRBlock block) {
        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof FieldAccessInstruction) {
                FieldAccessInstruction field = (FieldAccessInstruction) instr;
                if (field.isStore()) {
                    return false;
                }
                if (field.isLoad() && field.getResult() != null
                        && fieldLoadClobberedBeforeUse(field, field.getResult())) {
                    return false;
                }
            } else if (instr instanceof ArrayAccessInstruction && ((ArrayAccessInstruction) instr).isStore()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean regionContainsUnprocessedHandler(Set<IRBlock> region) {
        List<ExceptionHandler> handlers = context.getIrMethod().getExceptionHandlers();
        if (handlers == null || handlers.isEmpty()) {
            return false;
        }
        for (IRBlock block : region) {
            ExceptionHandler handler = findHandlerStartingAt(block);
            if (handler != null && !processedTryHandlers.contains(handler)
                    && !processedHandlerBlocks.contains(handler.getHandlerBlock())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an expression is boolean-typed.
     */
    private boolean isBooleanExpression(Expression expr) {
        SourceType type = expr.getType();
        if (type == PrimitiveSourceType.BOOLEAN) {
            return true;
        }

        if (expr instanceof MethodCallExpr) {
            MethodCallExpr mce = (MethodCallExpr) expr;
            String name = mce.getMethodName();
            if (name.startsWith("is") || name.startsWith("has") || name.startsWith("can") ||
                name.startsWith("should") || name.startsWith("was") ||
                "equals".equals(name) || "contains".equals(name) ||
                "startsWith".equals(name) || "endsWith".equals(name) ||
                "isEmpty".equals(name) || "isPresent".equals(name)) {
                return true;
            }
        }

        if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr fae = (FieldAccessExpr) expr;
            SourceType fieldType = fae.getType();
            if (fieldType == PrimitiveSourceType.BOOLEAN) {
                return true;
            }
        }

        if (expr instanceof VarRefExpr) {
            int slot = slotOfValue(((VarRefExpr) expr).getSsaValue());
            return context.getExpressionContext().isParameterOrThisSlot(slot)
                    && isParameterBoolean(context.getExpressionContext().parameterIndexForSlot(slot));
        }

        return false;
    }

    /**
     * Checks if an SSAValue has boolean type directly from its IR type.
     * This is a fallback when the recovered expression type isn't detected as boolean.
     */
    private boolean isBooleanSSAValue(Value value) {
        if (value instanceof SSAValue) {
            SSAValue ssaValue = (SSAValue) value;
            IRType type = ssaValue.getType();
            if (type == PrimitiveType.BOOLEAN) {
                return true;
            }
            IRInstruction def = ssaValue.getDefinition();
            if (def instanceof TypeCheckInstruction) {
                TypeCheckInstruction typeCheck = (TypeCheckInstruction) def;
                return typeCheck.isInstanceOf();
            }
        }
        return false;
    }

    /**
     * Checks if a parameter at the given index is a boolean type based on method descriptor.
     */
    private boolean isParameterBoolean(int argIndex) {
        String descriptor = context.getIrMethod().getDescriptor();
        if (descriptor == null) return false;

        List<String> paramTypes = parseParameterTypes(descriptor);
        if (argIndex >= 0 && argIndex < paramTypes.size()) {
            return "Z".equals(paramTypes.get(argIndex));
        }
        return false;
    }

    /**
     * Parses parameter types from a method descriptor.
     * @param descriptor method descriptor like "(ZILjava/lang/String;)V"
     * @return list of type descriptors for each parameter
     */
    private List<String> parseParameterTypes(String descriptor) {
        List<String> types = new ArrayList<>();
        int start = descriptor.indexOf('(');
        int end = descriptor.indexOf(')', start);
        if (start == -1 || end == -1 || end <= start) {
            return types;
        }

        int index = start + 1;
        while (index < end) {
            char c = descriptor.charAt(index);
            if (c == 'B' || c == 'C' || c == 'D' || c == 'F' ||
                c == 'I' || c == 'J' || c == 'S' || c == 'Z') {
                types.add(String.valueOf(c));
                index++;
            } else if (c == 'L') {
                int semicolon = descriptor.indexOf(';', index);
                if (semicolon > index) {
                    types.add(descriptor.substring(index, semicolon + 1));
                    index = semicolon + 1;
                } else {
                    break;
                }
            } else if (c == '[') {
                int arrayStart = index;
                while (index < end && descriptor.charAt(index) == '[') {
                    index++;
                }
                if (index < end) {
                    char baseType = descriptor.charAt(index);
                    if (baseType == 'L') {
                        int semicolon = descriptor.indexOf(';', index);
                        if (semicolon > index) {
                            types.add(descriptor.substring(arrayStart, semicolon + 1));
                            index = semicolon + 1;
                        } else {
                            break;
                        }
                    } else {
                        types.add(descriptor.substring(arrayStart, index + 1));
                        index++;
                    }
                }
            } else {
                index++;
            }
        }
        return types;
    }

    private SSAValue getConditionValue(IRBlock block) {
        IRInstruction terminator = block.getTerminator();
        if (terminator instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) terminator;
            Value left = branch.getLeft();
            if (left instanceof SSAValue) {
                return (SSAValue) left;
            }
        }
        return null;
    }

    private CompareOp getConditionOp(IRBlock block) {
        IRInstruction terminator = block.getTerminator();
        if (terminator instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) terminator;
            return branch.getCondition();
        }
        return null;
    }

    private FieldKey extractFieldKey(SSAValue value) {
        if (value == null) {
            return null;
        }
        IRInstruction def = value.getDefinition();
        if (def instanceof FieldAccessInstruction) {
            FieldAccessInstruction fai = (FieldAccessInstruction) def;
            if (fai.isLoad()) {
                return new FieldKey(fai.getOwner(), fai.getName());
            }
        }
        return null;
    }

    private boolean isConditionKnownFalse(SSAValue value) {
        if (value == null) {
            return false;
        }
        if (context.isKnownFalse(value)) {
            return true;
        }
        FieldKey fieldKey = extractFieldKey(value);
        if (fieldKey != null) {
            return context.isFieldKnownFalse(fieldKey.getOwner(), fieldKey.getFieldName());
        }
        return false;
    }

    private BinaryOperator negateOperator(
            BinaryOperator op) {
        if (op == BinaryOperator.EQ) {
            return BinaryOperator.NE;
        } else if (op == BinaryOperator.NE) {
            return BinaryOperator.EQ;
        } else if (op == BinaryOperator.LT) {
            return BinaryOperator.GE;
        } else if (op == BinaryOperator.GE) {
            return BinaryOperator.LT;
        } else if (op == BinaryOperator.GT) {
            return BinaryOperator.LE;
        } else if (op == BinaryOperator.LE) {
            return BinaryOperator.GT;
        } else {
            return op;
        }
    }

    private IRBlock getNextSequentialBlock(IRBlock block) {
        if (block.getSuccessors().size() == 1) {
            return block.getSuccessors().iterator().next();
        }
        return null;
    }

    private boolean isReturnBlock(IRBlock block) {
        if (block == null) return false;
        IRInstruction terminator = block.getTerminator();
        return terminator instanceof ReturnInstruction;
    }

    private boolean isMethodExitBlock(IRBlock block) {
        if (block == null) return false;
        IRInstruction terminator = block.getTerminator();
        if (terminator instanceof ReturnInstruction) {
            return true;
        }
        if (terminator instanceof SimpleInstruction) {
            return ((SimpleInstruction) terminator).getOp() == SimpleOp.ATHROW;
        }
        return false;
    }

    /**
     * Finds the exit block for a loop, handling cases where the exit block
     * is not directly set (when both loop header successors are in the loop).
     */
    private IRBlock findLoopExit(RegionInfo info, Set<IRBlock> visited, Set<IRBlock> stopBlocks) {
        if (info.getLoopExit() != null) {
            return info.getLoopExit();
        }

        // Find blocks outside the loop that are reachable from within
        if (info.getLoop() != null) {
            Set<IRBlock> loopBlocks = info.getLoop().getBlocks();
            for (IRBlock loopBlock : loopBlocks) {
                for (IRBlock succ : loopBlock.getSuccessors()) {
                    if (!loopBlocks.contains(succ) && !visited.contains(succ) && !stopBlocks.contains(succ)) {
                        // Found an unvisited block outside the loop
                        return succ;
                    }
                }
            }
        }

        return null;
    }

    private IRBlock findSwitchMerge(RegionInfo info) {
        Set<IRBlock> caseTargets = new HashSet<>(info.getSwitchCases().values());
        Set<IRBlock> allTargets = new HashSet<>(caseTargets);
        if (info.getDefaultTarget() != null) {
            allTargets.add(info.getDefaultTarget());
        }
        var postDomTree = analyzer.getPostDominatorTree();
        if (postDomTree != null) {
            IRBlock ipdom = postDomTree.getImmediatePostDominator(info.getHeader());
            // The post-dominator tree is unreliable when the switch has several distinct `return`/`throw`
            // exits and no single sink: it can name a block reached through only one case body (e.g. the
            // `return true` arm of `case: return a && b; default: return false;`) as the header's ipdom,
            // even though the default path never reaches it. A genuine merge post-dominates the header, so
            // it must be reachable from every case target and the default; reject candidates that are not.
            if (ipdom != null && !caseTargets.contains(ipdom) && reachedFromAllTargets(ipdom, allTargets)) {
                return ipdom;
            }
        }

        // Reachable region of each target, stopping at the other targets so one case body's blocks
        // do not bleed into another's region.
        Map<IRBlock, Set<IRBlock>> reachableByTarget = new LinkedHashMap<>();
        for (IRBlock target : allTargets) {
            Set<IRBlock> reachable = new HashSet<>();
            Set<IRBlock> otherTargets = new HashSet<>(allTargets);
            otherTargets.remove(target);
            collectReachableBlocks(target, reachable, otherTargets);
            reachableByTarget.put(target, reachable);
        }

        // A case that returns or throws without rejoining the others never reaches the merge - its
        // region is disjoint from every sibling's. The merge is where the CONVERGING cases meet, so
        // intersect only over targets whose region overlaps another's. Requiring every target
        // (including a throwing default) to reach the merge collapses the intersection to empty and
        // leaves the shared post-switch tail to be absorbed into whichever case is recovered first.
        Set<IRBlock> converging = new HashSet<>();
        for (IRBlock a : allTargets) {
            for (IRBlock b : allTargets) {
                if (a != b && !Collections.disjoint(reachableByTarget.get(a), reachableByTarget.get(b))) {
                    converging.add(a);
                    break;
                }
            }
        }
        if (converging.size() < 2) {
            return null;
        }

        Set<IRBlock> commonSuccessors = null;
        for (IRBlock target : converging) {
            if (commonSuccessors == null) {
                commonSuccessors = new HashSet<>(reachableByTarget.get(target));
            } else {
                commonSuccessors.retainAll(reachableByTarget.get(target));
            }
        }
        commonSuccessors.removeAll(caseTargets);
        if (commonSuccessors.isEmpty()) {
            return null;
        }

        // The merge is the ENTRY of the shared region - the earliest common block reached straight
        // from a converging case (a predecessor outside the region), not one buried deeper in the
        // tail. Pick by lowest bytecode offset so the choice is deterministic.
        IRBlock merge = null;
        int mergeOffset = Integer.MAX_VALUE;
        for (IRBlock candidate : commonSuccessors) {
            boolean isEntry = false;
            for (IRBlock pred : candidate.getPredecessors()) {
                if (!commonSuccessors.contains(pred)) {
                    isEntry = true;
                    break;
                }
            }
            if (!isEntry) {
                continue;
            }
            int off = candidate.getInstructions().isEmpty()
                    ? Integer.MAX_VALUE
                    : candidate.getInstructions().get(0).getBytecodeOffset();
            if (off < mergeOffset) {
                mergeOffset = off;
                merge = candidate;
            }
        }
        return merge;
    }

    /**
     * True when {@code merge} is reachable from every switch target (each case target and the default),
     * i.e. it is a real convergence point that post-dominates the header. Guards against a spurious
     * post-dominator that only one case body can reach.
     */
    private boolean reachedFromAllTargets(IRBlock merge, Set<IRBlock> allTargets) {
        for (IRBlock target : allTargets) {
            if (target == merge) {
                continue;
            }
            Set<IRBlock> reachable = new HashSet<>();
            collectReachableBlocks(target, reachable);
            if (!reachable.contains(merge)) {
                return false;
            }
        }
        return true;
    }

    private void collectReachableBlocks(IRBlock start, Set<IRBlock> result) {
        collectReachableBlocks(start, result, Collections.emptySet());
    }

    /**
     * True when {@code merge} is reached not only through this if (its header {@code current} and the
     * then/else body) but from at least one predecessor outside it - a shared convergence point such
     * as the tail after a switch that multiple case bodies fall into. Inlining such a return merge
     * into one branch strips it from the siblings, so the caller must leave it for the enclosing
     * structure to emit once.
     */
    private boolean isMergeSharedBeyondIf(IRBlock merge, IRBlock current, RegionInfo info) {
        Set<IRBlock> owned = new HashSet<>();
        owned.add(current);
        Set<IRBlock> boundary = Collections.singleton(merge);
        if (info.getThenBlock() != null) {
            collectReachableBlocks(info.getThenBlock(), owned, boundary);
        }
        if (info.getElseBlock() != null) {
            collectReachableBlocks(info.getElseBlock(), owned, boundary);
        }
        for (IRBlock pred : merge.getPredecessors()) {
            if (!owned.contains(pred)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks exactly the blocks a catch clause's recovery consumes: the walk stops at goto,
     * return and athrow terminators, mirroring recoverCatchClause/recoverHandlerBlocks. An
     * unbounded reachability walk would claim the fall-through join and everything after the
     * try/catch, making the outer sequence drop the method's continuation.
     */
    private void collectCatchConsumedBlocks(ExceptionHandler handler, Set<IRBlock> result) {
        IRBlock handlerBlock = handler.getHandlerBlock();
        result.add(handlerBlock);
        if (isGotoTerminated(handlerBlock) && !handler.isCatchAll()) {
            return;
        }
        Deque<IRBlock> worklist = new ArrayDeque<>(handlerBlock.getSuccessors());
        while (!worklist.isEmpty()) {
            IRBlock current = worklist.poll();
            if (!result.add(current)) {
                continue;
            }
            IRInstruction terminator = current.getTerminator();
            if (terminator instanceof ReturnInstruction) {
                continue;
            }
            if (terminator instanceof SimpleInstruction) {
                SimpleOp op = ((SimpleInstruction) terminator).getOp();
                if (op == SimpleOp.GOTO || op == SimpleOp.ATHROW) {
                    continue;
                }
            }
            worklist.addAll(current.getSuccessors());
        }
    }

    private static boolean isGotoTerminated(IRBlock block) {
        IRInstruction terminator = block.getTerminator();
        return terminator instanceof SimpleInstruction
                && ((SimpleInstruction) terminator).getOp() == SimpleOp.GOTO;
    }

    private void collectReachableBlocks(IRBlock start, Set<IRBlock> result, Set<IRBlock> stopBlocks) {
        Deque<IRBlock> worklist = new ArrayDeque<>();
        worklist.add(start);

        while (!worklist.isEmpty()) {
            IRBlock current = worklist.poll();
            if (result.contains(current) || stopBlocks.contains(current)) {
                continue;
            }
            result.add(current);
            worklist.addAll(current.getSuccessors());
        }
    }

    /**
     * Checks if the then/else branches form a boolean return pattern.
     * Pattern: if(cond) return true/false; else return false/true;
     * This is common in bytecode for boolean comparison methods.
     */
    private boolean isBooleanReturnPattern(List<Statement> thenStmts, List<Statement> elseStmts) {
        if (thenStmts.size() != 1 || elseStmts.size() != 1) {
            return false;
        }

        Statement thenStmt = thenStmts.get(0);
        Statement elseStmt = elseStmts.get(0);

        if (!(thenStmt instanceof ReturnStmt)) {
            return false;
        }
        if (!(elseStmt instanceof ReturnStmt)) {
            return false;
        }

        ReturnStmt thenRet = (ReturnStmt) thenStmt;
        ReturnStmt elseRet = (ReturnStmt) elseStmt;

        return isBooleanLiteral(thenRet.getValue()) && isBooleanLiteral(elseRet.getValue());
    }

    /**
     * Checks if an expression is a boolean literal (true/false or integer 0/1).
     */
    private boolean isBooleanLiteral(Expression expr) {
        if (expr instanceof LiteralExpr) {
            LiteralExpr lit = (LiteralExpr) expr;
            Object val = lit.getValue();
            if (val instanceof Boolean) {
                return true;
            }
            if (val instanceof Integer) {
                int i = (Integer) val;
                return i == 0 || i == 1;
            }
        }
        return false;
    }

    /**
     * Gets the boolean value from a literal expression.
     */
    private boolean getBooleanValue(Expression expr) {
        if (expr instanceof LiteralExpr) {
            LiteralExpr lit = (LiteralExpr) expr;
            Object val = lit.getValue();
            if (val instanceof Boolean) {
                return (Boolean) val;
            }
            if (val instanceof Integer) {
                Integer i = (Integer) val;
                return i != 0;
            }
        }
        return false;
    }

    /**
     * Collapses a boolean return pattern into a single return statement.
     * if(cond) return true; else return false; -> return cond;
     * if(cond) return false; else return true; -> return !cond;
     */
    private Statement collapseToBooleanReturn(Expression condition, List<Statement> thenStmts) {
        ReturnStmt thenRet = (ReturnStmt) thenStmts.get(0);
        boolean thenValue = getBooleanValue(thenRet.getValue());

        if (thenValue) {
            return new ReturnStmt(condition);
        } else {
            return new ReturnStmt(new UnaryExpr(UnaryOperator.NOT, condition,
                    PrimitiveSourceType.BOOLEAN));
        }
    }

    /**
     * Checks for a PHI-based boolean return pattern.
     * This pattern occurs when both branches are empty/trivial (just assign constants)
     * and the merge block has a PHI of boolean values followed by return.
     */
    private boolean isBooleanPhiReturnPattern(List<Statement> thenStmts, List<Statement> elseStmts, IRBlock mergeBlock) {
        if (thenStmts.size() > 1 || elseStmts.size() > 1) {
            return false;
        }

        if (mergeBlock == null) {
            return false;
        }

        if (mergeBlock.getPhiInstructions().size() != 1) {
            return false;
        }

        List<IRInstruction> mergeInstrs = mergeBlock.getInstructions();
        if (mergeInstrs.size() != 1 || !(mergeInstrs.get(0) instanceof ReturnInstruction)) {
            return false;
        }

        PhiInstruction phi = mergeBlock.getPhiInstructions().get(0);
        for (Value val : phi.getOperands()) {
            Integer boolValue = extractBooleanConstant(val);
            if (boolValue == null) {
                return false;
            }
        }

        ReturnInstruction ret = (ReturnInstruction) mergeInstrs.get(0);
        return ret.getReturnValue() == phi.getResult();
    }

    /**
     * Collapses a PHI-based boolean return pattern into a single return statement.
     * Examines the PHI's incoming values to determine if condition should be negated.
     */
    private Statement collapsePhiToBooleanReturn(Expression condition, IRBlock mergeBlock) {
        if (mergeBlock == null) {
            return null;
        }

        PhiInstruction phi = mergeBlock.getPhiInstructions().get(0);

        List<Value> operands = phi.getOperands();
        if (operands.size() != 2) {
            return null;
        }

        Integer val0 = extractBooleanConstant(operands.get(0));
        Integer val1 = extractBooleanConstant(operands.get(1));

        if (val0 == null || val1 == null) {
            return null;
        }

        if (val0.equals(val1)) {
            return null;
        }

        if (val0 == 1) {
            return new ReturnStmt(condition);
        } else {
            Expression negatedCondition = invertCondition(condition);
            return new ReturnStmt(negatedCondition);
        }
    }

    /**
     * Inverts a condition expression.
     * For binary comparisons, inverts the operator (e.g., != to ==).
     * For other expressions, wraps in NOT.
     */
    private Expression invertCondition(Expression condition) {
        if (condition instanceof BinaryExpr) {
            BinaryExpr binExpr = (BinaryExpr) condition;
            BinaryOperator op = binExpr.getOperator();
            BinaryOperator inverted = negateOperator(op);
            if (inverted != op) {
                return new BinaryExpr(
                    inverted, binExpr.getLeft(), binExpr.getRight(), binExpr.getType());
            }
        }
        return new UnaryExpr(UnaryOperator.NOT, condition,
                PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Coerces a value to the slot type it is being stored into when the source-level
     * types disagree but the JVM treats them identically (a boolean is stored as an int).
     * Storing a boolean into an integral slot becomes {@code value ? 1 : 0}; storing an
     * integral value into a boolean slot becomes {@code value != 0}. Otherwise the value
     * is returned unchanged. {@code !!x} collapses to {@code x} first so the result is clean.
     */
    private Expression coerceForStore(Expression value, SourceType target) {
        if (target == null) {
            return value;
        }
        value = stripDoubleNot(value);
        boolean targetBool = target == PrimitiveSourceType.BOOLEAN;
        boolean valueBool = value.getType() == PrimitiveSourceType.BOOLEAN;
        if (targetBool == valueBool) {
            return value;
        }
        // Exactly one side is boolean here: bridge the JVM's int-as-boolean representation.
        if (valueBool && isIntegralType(target)) {
            // a boolean expression stored into an int-like slot: `cond ? 1 : 0`
            return new TernaryExpr(value, LiteralExpr.ofInt(1), LiteralExpr.ofInt(0), target);
        }
        if (targetBool && isIntegralType(value.getType())) {
            // an int-like value stored into a boolean slot: `value != 0`
            return new BinaryExpr(BinaryOperator.NE, value, LiteralExpr.ofInt(0),
                    PrimitiveSourceType.BOOLEAN);
        }
        return value;
    }

    private boolean isIntegralType(SourceType t) {
        return t == PrimitiveSourceType.INT || t == PrimitiveSourceType.SHORT
            || t == PrimitiveSourceType.BYTE || t == PrimitiveSourceType.CHAR;
    }

    /** Collapses {@code !!x} to {@code x}. */
    private Expression stripDoubleNot(Expression e) {
        while (e instanceof UnaryExpr && ((UnaryExpr) e).getOperator() == UnaryOperator.NOT) {
            Expression inner = ((UnaryExpr) e).getOperand();
            if (inner instanceof UnaryExpr && ((UnaryExpr) inner).getOperator() == UnaryOperator.NOT) {
                e = ((UnaryExpr) inner).getOperand();
            } else {
                break;
            }
        }
        return e;
    }

    /**
     * Extracts a boolean constant value (0 or 1) from an IR Value.
     * Handles both direct IntConstants and SSAValues defined by ConstantInstructions.
     */
    private Integer extractBooleanConstant(Value val) {
        if (val instanceof IntConstant) {
            IntConstant ic = (IntConstant) val;
            int v = ic.getValue();
            if (v == 0 || v == 1) {
                return v;
            }
            return null;
        }

        if (val instanceof SSAValue) {
            SSAValue ssaVal = (SSAValue) val;
            IRInstruction def = ssaVal.getDefinition();
            if (def instanceof ConstantInstruction) {
                ConstantInstruction constInstr = (ConstantInstruction) def;
                Constant c = constInstr.getConstant();
                if (c instanceof IntConstant) {
                    IntConstant ic = (IntConstant) c;
                    int v = ic.getValue();
                    if (v == 0 || v == 1) {
                        return v;
                    }
                }
            }
        }

        return null;
    }

    private Expression tryConvertToBooleanLiteral(Expression expr, Value sourceValue) {
        if (expr instanceof LiteralExpr) {
            LiteralExpr lit = (LiteralExpr) expr;
            Object litValue = lit.getValue();
            if (litValue instanceof Integer) {
                int intVal = (Integer) litValue;
                if (intVal == 0 || intVal == 1) {
                    return LiteralExpr.ofBoolean(intVal != 0);
                }
            }
        }

        Integer boolConst = extractBooleanConstant(sourceValue);
        if (boolConst != null) {
            return LiteralExpr.ofBoolean(boolConst != 0);
        }

        return null;
    }

    /**
     * Checks if the then/else branches form an OR condition chain pattern.
     * Pattern: if(a) { body } else { if(b) { sameBody } ... }
     * This is how bytecode represents "if (a || b) { body }".
     */
    private boolean isOrConditionChain(List<Statement> thenStmts, List<Statement> elseStmts) {
        if (elseStmts.size() != 1) {
            return false;
        }
        if (!(elseStmts.get(0) instanceof IfStmt)) {
            return false;
        }

        IfStmt nestedIf = (IfStmt) elseStmts.get(0);

        Statement nestedThenBranch = nestedIf.getThenBranch();
        List<Statement> nestedThen;
        if (nestedThenBranch instanceof BlockStmt) {
            BlockStmt nestedBlock = (BlockStmt) nestedThenBranch;
            nestedThen = nestedBlock.getStatements();
        } else {
            nestedThen = List.of(nestedThenBranch);
        }

        return statementsEqual(thenStmts, nestedThen);
    }

    /**
     * Merges OR condition chains into a single if statement with OR conditions.
     * if(a) { body } else { if(b) { body } else { elseBody } }
     * becomes: if(a || b) { body } else { elseBody }
     */
    private Statement mergeOrConditions(Expression cond1, List<Statement> thenStmts,
                                         List<Statement> elseStmts) {
        IfStmt nestedIf = (IfStmt) elseStmts.get(0);
        Expression cond2 = nestedIf.getCondition();

        Expression merged = new BinaryExpr(BinaryOperator.OR, cond1, cond2,
                PrimitiveSourceType.BOOLEAN);

        Statement nestedElseBranch = nestedIf.getElseBranch();
        List<Statement> finalElseStmts = null;

        if (nestedElseBranch != null) {
            List<Statement> nestedElse;
            if (nestedElseBranch instanceof BlockStmt) {
                BlockStmt nestedBlock = (BlockStmt) nestedElseBranch;
                nestedElse = nestedBlock.getStatements();
            } else {
                nestedElse = List.of(nestedElseBranch);
            }

            if (isOrConditionChain(thenStmts, nestedElse)) {
                return mergeOrConditions(merged, thenStmts, nestedElse);
            }
            finalElseStmts = nestedElse;
        }

        BlockStmt thenBlock = new BlockStmt(thenStmts);
        BlockStmt elseBlock = (finalElseStmts != null && !finalElseStmts.isEmpty())
                ? new BlockStmt(finalElseStmts) : null;
        return new IfStmt(merged, thenBlock, elseBlock);
    }

    /**
     * Compares two lists of statements for structural equality.
     * Used to detect if two code paths have the same body (for OR condition merging).
     */
    private boolean statementsEqual(List<Statement> a, List<Statement> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!statementEqual(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two statements for structural equality.
     */
    private boolean statementEqual(Statement a, Statement b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;

        return a.toString().equals(b.toString());
    }

    /**
     * Checks if the then statements form an AND condition chain pattern.
     * Pattern: if(a) { if(b) { if(c) { body } } }
     * This is how bytecode represents "if (a && b && c) { body }".
     * The nested ifs must have no else branches for this pattern.
     */
    private boolean isAndConditionChain(List<Statement> thenStmts) {
        if (thenStmts.size() != 1) {
            return false;
        }
        if (!(thenStmts.get(0) instanceof IfStmt)) {
            return false;
        }
        IfStmt nestedIf = (IfStmt) thenStmts.get(0);
        return nestedIf.getElseBranch() == null;
    }

    /**
     * Merges AND condition chains into a single if statement with AND conditions.
     * if(a) { if(b) { if(c) { body } } }
     * becomes: if(a && b && c) { body }
     */
    private Statement mergeAndConditions(Expression outerCondition, List<Statement> thenStmts) {
        List<Expression> conditions = new ArrayList<>();
        conditions.add(outerCondition);
        Statement body = collectAndChainConditions(thenStmts, conditions);

        Expression merged = conditions.get(0);
        for (int i = 1; i < conditions.size(); i++) {
            merged = new BinaryExpr(BinaryOperator.AND, merged, conditions.get(i),
                    PrimitiveSourceType.BOOLEAN);
        }

        return new IfStmt(merged, body, null);
    }

    /**
     * Recursively collects conditions from nested AND chain ifs.
     * Returns the innermost body (the actual code to execute).
     */
    private Statement collectAndChainConditions(List<Statement> stmts, List<Expression> conditions) {
        if (stmts.size() != 1 || !(stmts.get(0) instanceof IfStmt)) {
            if (stmts.size() == 1) {
                return stmts.get(0);
            }
            return new BlockStmt(stmts);
        }

        IfStmt ifStmt = (IfStmt) stmts.get(0);
        if (ifStmt.getElseBranch() != null) {
            return ifStmt;
        }

        conditions.add(ifStmt.getCondition());

        Statement thenBranch = ifStmt.getThenBranch();
        List<Statement> nestedStmts;
        if (thenBranch instanceof BlockStmt) {
            nestedStmts = ((BlockStmt) thenBranch).getStatements();
        } else {
            nestedStmts = List.of(thenBranch);
        }

        if (nestedStmts.size() == 1 && nestedStmts.get(0) instanceof IfStmt) {
            IfStmt nested = (IfStmt) nestedStmts.get(0);
            if (nested.getElseBranch() == null) {
                return collectAndChainConditions(nestedStmts, conditions);
            }
        }

        return thenBranch;
    }

    /**
     * Finds a PHI instruction that is assigned boolean constants (0/1) from the then/else branches.
     * Works at the IR level by examining the blocks directly rather than recovered statements.
     * <p>
     * Pattern in IR:
     *   B_then: v1 = const 0; goto B_merge
     *   B_else: v2 = const 1; goto B_merge
     *   B_merge: PHI = phi [v1, B_then], [v2, B_else]
     * <p>
     * This is how bytecode represents boolean expressions like !method() when used in larger expressions.
     *
     * @param info Region info with then/else/merge block information
     * @return The PHI instruction if this pattern is detected, null otherwise
     */
    private PhiInstruction findBooleanPhiAssignmentPatternIR(RegionInfo info) {
        IRBlock thenBlock = info.getThenBlock();
        IRBlock elseBlock = info.getElseBlock();
        IRBlock mergeBlock = info.getMergeBlock();

        if (thenBlock == null || elseBlock == null || mergeBlock == null) {
            return null;
        }

        Integer thenConst = extractSingleBooleanConstant(thenBlock);
        Integer elseConst = extractSingleBooleanConstant(elseBlock);

        if (thenConst == null || elseConst == null) {
            return null;
        }

        if (thenConst.equals(elseConst)) {
            return null;
        }

        if (hasMultipleConditionalPredecessors(thenBlock) || hasMultipleConditionalPredecessors(elseBlock)) {
            return null;
        }

        for (PhiInstruction phi : mergeBlock.getPhiInstructions()) {
            if (phiReceivesBooleanConstants(phi, thenBlock, elseBlock)) {
                return phi;
            }
        }

        return null;
    }

    private boolean hasMultipleConditionalPredecessors(IRBlock block) {
        Set<IRBlock> preds = block.getPredecessors();
        if (preds.size() <= 1) {
            return false;
        }
        int conditionalCount = 0;
        for (IRBlock pred : preds) {
            IRInstruction terminator = pred.getTerminator();
            if (terminator instanceof BranchInstruction) {
                conditionalCount++;
            }
        }
        return conditionalCount > 1;
    }

    /**
     * Extracts a single boolean constant (0 or 1) from a block that only contains
     * a constant instruction and a goto terminator.
     */
    private Integer extractSingleBooleanConstant(IRBlock block) {
        List<IRInstruction> instructions = block.getInstructions();

        if (instructions.isEmpty() || instructions.size() > 2) {
            return null;
        }

        ConstantInstruction constInstr = null;
        for (IRInstruction instr : instructions) {
            if (instr instanceof ConstantInstruction) {
                constInstr = (ConstantInstruction) instr;
            } else if (instr instanceof SimpleInstruction) {
                SimpleInstruction simple = (SimpleInstruction) instr;
                if (simple.getOp() != SimpleOp.GOTO) {
                    if (!instr.isTerminator()) {
                        return null;
                    }
                }
            } else if (!instr.isTerminator()) {
                return null;
            }
        }

        if (constInstr == null) {
            return null;
        }

        return extractBooleanConstant(constInstr.getConstant());
    }

    /**
     * Checks if a PHI instruction receives boolean constants from the then/else blocks.
     */
    private boolean phiReceivesBooleanConstants(PhiInstruction phi, IRBlock thenBlock, IRBlock elseBlock) {
        List<Value> operands = phi.getOperands();
        Set<IRBlock> incomingBlocks = phi.getIncomingBlocks();

        if (operands.size() != 2 || incomingBlocks.size() != 2) {
            return false;
        }

        boolean hasThen = incomingBlocks.contains(thenBlock);
        boolean hasElse = incomingBlocks.contains(elseBlock);

        if (!hasThen || !hasElse) {
            return false;
        }

        for (Value val : operands) {
            Integer constVal = extractBooleanConstant(val);
            if (constVal == null) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if all phi operands are boolean constants (0 or 1).
     * Used to determine if a phi should be typed as boolean instead of int.
     */
    private boolean phiReceivesBooleanConstantsOnly(PhiInstruction phi) {
        List<Value> operands = phi.getOperands();
        if (operands.isEmpty()) {
            return false;
        }
        for (Value val : operands) {
            Integer constVal = extractBooleanConstant(val);
            if (constVal == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the boolean constant value (0 or 1) that the then block contributes to a PHI.
     */
    private int getThenBlockBooleanValue(PhiInstruction phi, IRBlock thenBlock) {
        List<Value> operands = phi.getOperands();
        Set<IRBlock> incomingBlocks = phi.getIncomingBlocks();

        List<IRBlock> blockList = new ArrayList<>(incomingBlocks);
        for (int i = 0; i < blockList.size(); i++) {
            if (blockList.get(i) == thenBlock) {
                Integer val = extractBooleanConstant(operands.get(i));
                return val != null ? val : 0;
            }
        }
        return 0;
    }

    /**
     * Collapses a boolean PHI assignment pattern to a cached boolean expression.
     * Instead of emitting: if(cond) { i = 0; } else { i = 1; }
     * We cache the expression !cond (or cond) for the PHI result, so when it's used
     * in expressions like (x & i), it becomes (x & !cond).
     */
    private void collapseToBooleanPhiExpressionIR(Expression condition, PhiInstruction phi, IRBlock thenBlock) {
        int thenVal = getThenBlockBooleanValue(phi, thenBlock);

        Expression booleanExpr;
        if (thenVal == 1) {
            booleanExpr = condition;
        } else {
            booleanExpr = invertCondition(condition);
        }

        SSAValue phiResult = phi.getResult();
        if (phiResult != null) {
            context.getExpressionContext().cacheExpression(phiResult, booleanExpr);
            context.getExpressionContext().unmarkMaterialized(phiResult);
        }
    }

    /**
     * Pre-pass to collect for-loop initializer instructions before block processing.
     * This allows them to be inlined into the for-loop declaration instead of
     * being emitted separately in predecessor blocks.
     */
    private void collectForLoopInitInstructions() {
        for (RegionInfo info : analyzer.getForLoopRegions()) {
            int targetLocal = info.getInductionLocalIndex();
            if (targetLocal < 0) continue;

            context.markAsForLoopInductionLocal(targetLocal);

            IRBlock header = info.getHeader();
            context.markAsForLoopHeader(header);
            LoopAnalysis.Loop loop = info.getLoop();

            for (PhiInstruction phi : header.getPhiInstructions()) {
                if (isPhiForLocal(phi, targetLocal, loop)) {
                    context.markAsForLoopInductionPhi(phi.getResult());
                }
            }

            for (IRBlock pred : header.getPredecessors()) {
                if (loop != null && loop.contains(pred)) {
                    continue;
                }

                for (IRInstruction instr : pred.getInstructions()) {
                    if (instr instanceof StoreLocalInstruction) {
                        StoreLocalInstruction store = (StoreLocalInstruction) instr;
                        if (store.getLocalIndex() == targetLocal) {
                            context.markAsForLoopInit(instr);
                        }
                    }
                }
            }
        }
    }

    private boolean isPhiForLocal(PhiInstruction phi, int localIndex, LoopAnalysis.Loop loop) {
        for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
            IRBlock sourceBlock = entry.getKey();
            Value incomingValue = entry.getValue();

            boolean fromInsideLoop = loop != null && loop.contains(sourceBlock);
            if (fromInsideLoop) {
                if (incomingValue instanceof SSAValue) {
                    SSAValue ssaVal = (SSAValue) incomingValue;
                    IRInstruction def = ssaVal.getDefinition();
                    if (def instanceof BinaryOpInstruction) {
                        BinaryOpInstruction binOp = (BinaryOpInstruction) def;
                        if ((binOp.getOp() == BinaryOp.ADD || binOp.getOp() == BinaryOp.SUB)
                                && isInductionStep(binOp, phi.getResult())) {
                            return true;
                        }
                    }
                    if (isValueFromLocal(ssaVal, localIndex)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * True when {@code binOp} is {@code phiResult ± constant} — the canonical induction step. An
     * accumulation like {@code s = s + element} (the addend is a variable, not a constant) is NOT an
     * induction step and must not be mistaken for the loop counter.
     */
    private boolean isInductionStep(BinaryOpInstruction binOp, SSAValue phiResult) {
        if (phiResult == null) {
            return false;
        }
        Value left = binOp.getLeft();
        Value right = binOp.getRight();
        return (left == phiResult && isConstantOperand(right))
                || (right == phiResult && isConstantOperand(left));
    }

    private boolean isValueFromLocal(SSAValue value, int localIndex) {
        IRInstruction def = value.getDefinition();
        if (def instanceof LoadLocalInstruction) {
            return ((LoadLocalInstruction) def).getLocalIndex() == localIndex;
        }
        if (def instanceof BinaryOpInstruction) {
            BinaryOpInstruction binOp = (BinaryOpInstruction) def;
            Value left = binOp.getLeft();
            Value right = binOp.getRight();
            if (left instanceof SSAValue && isValueFromLocal((SSAValue) left, localIndex)) {
                return true;
            }
            return right instanceof SSAValue && isValueFromLocal((SSAValue) right, localIndex);
        }
        return false;
    }

    /**
     * Detects a general ternary pattern where then/else blocks each produce a single value
     * that feeds into a PHI in the merge block. This pattern occurs with code like:
     * x == null ? "" : x
     * <p>
     * The bytecode pattern is:
     * - if(cond) goto thenBlock else elseBlock
     * - thenBlock: load value1, goto mergeBlock
     * - elseBlock: load value2, goto mergeBlock
     * - mergeBlock: PHI(value1, value2), use PHI in method call/assignment
     */
    private PhiInstruction findTernaryPhiPattern(RegionInfo info) {
        return findTernaryPhi(info.getThenBlock(), info.getElseBlock(), info.getMergeBlock());
    }

    /**
     * The phi in {@code mergeBlock} that a diamond over {@code thenBlock}/{@code elseBlock} collapses to a
     * ternary - one incoming value from each arm, each arm a valid single-value producer - or null.
     */
    private PhiInstruction findTernaryPhi(IRBlock thenBlock, IRBlock elseBlock, IRBlock mergeBlock) {
        if (thenBlock == null || elseBlock == null || mergeBlock == null) {
            return null;
        }

        if (hasMultipleConditionalPredecessors(thenBlock) || hasMultipleConditionalPredecessors(elseBlock)) {
            return null;
        }

        for (PhiInstruction phi : mergeBlock.getPhiInstructions()) {
            // A phi taking one input from each arm is a ternary when each arm is either the classic
            // single-value block or a compound but provably pure computation (e.g. `-k` = load +
            // neg). Arms with calls/allocations stay on the single-value rule: their statements are
            // consumed by the collapse, so anything beyond the produced value would be lost.
            Set<IRBlock> incomingBlocks = phi.getIncomingBlocks();
            if (incomingBlocks.size() == 2
                    && incomingBlocks.contains(thenBlock) && incomingBlocks.contains(elseBlock)
                    && phi.getIncoming(thenBlock) != null && phi.getIncoming(elseBlock) != null
                    && isTernaryArm(thenBlock, phi.getIncoming(thenBlock))
                    && isTernaryArm(elseBlock, phi.getIncoming(elseBlock))) {
                return phi;
            }
        }

        return null;
    }

    private boolean isTernaryArm(IRBlock block, Value phiIncoming) {
        return extractSingleProducedValue(block) != null || isPureComputeArm(block, phiIncoming)
                || isSingleValueComputeArm(block, phiIncoming);
    }

    /**
     * True when the arm computes exactly the phi's incoming value and nothing else observable: every
     * non-terminator instruction either defines the incoming value, stores that value into the merged
     * slot, or produces an intermediate consumed only within this arm (e.g. a load feeding the call
     * whose result is the incoming value). Unlike {@link #extractSingleProducedValue} this tolerates
     * multi-instruction value computations (load + invoke), and unlike {@link #isPureComputeArm} it
     * allows the value-producing call/allocation itself - it is not lost, it becomes the ternary arm.
     * A side-effecting store or a value that escapes the arm for anything but the phi disqualifies it,
     * so the collapse never drops work.
     */
    private boolean isSingleValueComputeArm(IRBlock block, Value phiIncoming) {
        if (!(phiIncoming instanceof SSAValue)) {
            return false;
        }
        Set<IRInstruction> armInstrs = new HashSet<>(block.getInstructions());
        boolean producesIncoming = false;
        for (IRInstruction instr : block.getInstructions()) {
            if (instr.isTerminator()) {
                continue;
            }
            if (instr instanceof StoreLocalInstruction) {
                if (((StoreLocalInstruction) instr).getValue() != phiIncoming) {
                    return false;
                }
                continue;
            }
            if (instr instanceof FieldAccessInstruction && ((FieldAccessInstruction) instr).isStore()) {
                return false;
            }
            if (instr instanceof ArrayAccessInstruction && ((ArrayAccessInstruction) instr).isStore()) {
                return false;
            }
            SSAValue result = instr.getResult();
            if (result == null) {
                return false;
            }
            if (result == phiIncoming) {
                producesIncoming = true;
                continue;
            }
            for (IRInstruction use : result.getUses()) {
                if (!armInstrs.contains(use)) {
                    return false;
                }
            }
        }
        return producesIncoming;
    }

    /**
     * True when every instruction in a diamond arm is a side-effect-free computation: local/array/
     * field loads, constants, unary/binary ops and type checks. A local store is permitted only when
     * it stores the phi's incoming value (the redundant temp materialization the lifter emits).
     * Calls, allocations and real stores disqualify - the collapse would drop them.
     */
    private boolean isPureComputeArm(IRBlock block, Value phiIncoming) {
        for (IRInstruction instr : block.getInstructions()) {
            if (instr.isTerminator()) {
                continue;
            }
            if (instr instanceof StoreLocalInstruction) {
                if (((StoreLocalInstruction) instr).getValue() != phiIncoming) {
                    return false;
                }
            } else if (instr instanceof FieldAccessInstruction) {
                if (((FieldAccessInstruction) instr).isStore()) {
                    return false;
                }
            } else if (instr instanceof ArrayAccessInstruction) {
                if (((ArrayAccessInstruction) instr).isStore()) {
                    return false;
                }
            } else if (!(instr instanceof LoadLocalInstruction
                    || instr instanceof ConstantInstruction
                    || instr instanceof UnaryOpInstruction
                    || instr instanceof BinaryOpInstruction
                    || instr instanceof TypeCheckInstruction
                    || instr instanceof CopyInstruction)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts the single SSA value produced by a block (excluding the goto terminator).
     * The block should only contain one value-producing instruction.
     * Returns null if the block produces no values or multiple values.
     */
    private SSAValue extractSingleProducedValue(IRBlock block) {
        List<IRInstruction> instructions = block.getInstructions();

        SSAValue producedValue = null;
        for (IRInstruction instr : instructions) {
            if (instr instanceof SimpleInstruction) {
                SimpleInstruction simple = (SimpleInstruction) instr;
                if (simple.getOp() == SimpleOp.GOTO) {
                    continue;
                }
            }
            if (instr.isTerminator()) {
                continue;
            }

            SSAValue result = instr.getResult();
            if (result != null) {
                if (producedValue != null) {
                    return null;
                }
                producedValue = result;
            }
        }

        return producedValue;
    }

    /**
     * Collapses a ternary PHI pattern to a cached TernaryExpr.
     * Creates: condition ? thenValue : elseValue
     * and caches it for the PHI result so it gets inlined at usage sites.
     */
    private void collapseToTernaryPhiExpression(Expression condition, PhiInstruction phi,
                                                 IRBlock thenBlock, IRBlock elseBlock) {
        Value thenValue = phi.getIncoming(thenBlock);
        Value elseValue = phi.getIncoming(elseBlock);

        Expression thenExpr = recoverTernaryArmValue(thenValue, thenBlock);
        Expression elseExpr = recoverTernaryArmValue(elseValue, elseBlock);

        SSAValue phiResult = phi.getResult();
        SourceType type = typeRecoverer.recoverType(phiResult);

        TernaryExpr ternaryExpr = new TernaryExpr(condition, thenExpr, elseExpr, type);

        if (phiResult != null) {
            context.getExpressionContext().cacheExpression(phiResult, ternaryExpr);
            context.getExpressionContext().unmarkMaterialized(phiResult);
        }
    }

    /**
     * Recovers a ternary arm's incoming value as an inlined expression. The arm's statements are
     * discarded by the collapse, so a value the arm materialized into a temp (e.g. a call result
     * stored into the merged slot) must be re-recovered as its defining expression - referencing the
     * discarded temp name would leave an undefined variable. Un-materialize across the recovery so the
     * expression is inlined, then restore the flag so unrelated uses are unaffected.
     */
    private Expression recoverTernaryArmValue(Value value, IRBlock armBlock) {
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            // Only inline the defining expression for a value the arm itself produced (a temp the collapse
            // discards). A value defined before the arm - a pre-existing local like a loop-invariant bound -
            // must be referenced by its name: re-inlining its defining call would recompute it against the
            // current (possibly mutated) operands, changing its meaning.
            boolean definedInArm = ssa.getDefinition() != null && ssa.getDefinition().getBlock() == armBlock;
            boolean wasMaterialized = context.getExpressionContext().isMaterialized(ssa);
            if (definedInArm && wasMaterialized) {
                context.getExpressionContext().unmarkMaterialized(ssa);
            }
            Expression expr = exprRecoverer.recoverOperand(value);
            if (definedInArm && wasMaterialized) {
                context.getExpressionContext().markMaterialized(ssa);
            }
            return expr;
        }
        return exprRecoverer.recoverOperand(value);
    }

    /**
     * Gets the appropriate variable name for a local slot index.
     * Returns "this" for slot 0 in instance methods, "argN" for parameter slots,
     * and "localN" for true local variables.
     */
    private String getNameForLocalSlot(int localIndex) {
        boolean isStatic = context.getIrMethod().isStatic();
        if (!isStatic && localIndex == 0) {
            return "this";
        }

        int paramIndex = getParamIndexForSlot(localIndex);
        if (paramIndex >= 0) {
            return "arg" + paramIndex;
        }

        return "local" + localIndex;
    }

    /**
     * True if the value is the null reference (directly or via a constant instruction). Null is
     * assignable to any reference type, so it must not widen a slot's unified type to Object.
     */
    private boolean isNullValue(Value value) {
        if (value instanceof NullConstant) {
            return true;
        }
        if (value instanceof SSAValue) {
            IRInstruction def = ((SSAValue) value).getDefinition();
            if (def instanceof ConstantInstruction) {
                return ((ConstantInstruction) def).getConstant() instanceof NullConstant;
            }
        }
        return false;
    }

    /**
     * Resolves a local load/store/phi to its source-variable name via the reaching-definition
     * slot partition, or null when the partition could not place the instruction.
     */
    private String partitionName(IRInstruction instr) {
        SlotVariablePartition partition = context.getExpressionContext().getSlotPartition();
        if (partition == null) {
            return null;
        }
        if (instr instanceof StoreLocalInstruction) {
            return partition.nameForStore((StoreLocalInstruction) instr);
        }
        if (instr instanceof LoadLocalInstruction) {
            return partition.nameForLoad((LoadLocalInstruction) instr);
        }
        if (instr instanceof PhiInstruction) {
            return partition.nameForPhi((PhiInstruction) instr);
        }
        return null;
    }

    private String getNameForLocalSlotWithType(int localIndex, SourceType valueType) {
        boolean isStatic = context.getIrMethod().isStatic();
        if (!isStatic && localIndex == 0) {
            return "this";
        }

        int paramIndex = getParamIndexForSlot(localIndex);
        if (paramIndex >= 0) {
            return "arg" + paramIndex;
        }

        boolean isPhi = phiSlots.contains(localIndex);
        String typeCategory = isPhi
            ? getCoarseTypeCategory(valueType)
            : getTypeCategory(valueType);
        Map<String, String> categoryMap = slotTypeCategoryToName.computeIfAbsent(localIndex, k -> new LinkedHashMap<>());

        if (categoryMap.containsKey(typeCategory)) {
            return categoryMap.get(typeCategory);
        }

        if (categoryMap.isEmpty()) {
            String name = "local" + localIndex;
            if (context.getExpressionContext().isDeclared(name)) {
                name = generateUniqueLocalName(localIndex);
            }
            categoryMap.put(typeCategory, name);
            return name;
        }

        int suffix = categoryMap.size();
        String name = "local" + localIndex + "_" + suffix;
        if (context.getExpressionContext().isDeclared(name)) {
            name = generateUniqueLocalName(localIndex);
        }
        categoryMap.put(typeCategory, name);
        return name;
    }

    private String getCoarseTypeCategory(SourceType type) {
        if (type == null) {
            return "unknown";
        }
        if (type.isPrimitive()) {
            // Distinguish by JVM verification width so a slot reused as int vs long
            // (genuinely distinct source variables) is not merged under one name.
            switch (((PrimitiveSourceType) type).getKind()) {
                case LONG:   return "primitive:long";
                case FLOAT:  return "primitive:float";
                case DOUBLE: return "primitive:double";
                default:     return "primitive:int";
            }
        }
        if (type instanceof ArraySourceType) {
            return "array";
        }
        return "object";
    }

    private String generateUniqueLocalName(int baseIndex) {
        String baseName = "local" + baseIndex;
        int suffix = 2;
        String candidate = baseName + "_" + suffix;
        while (context.getExpressionContext().isDeclared(candidate)) {
            suffix++;
            candidate = baseName + "_" + suffix;
        }
        return candidate;
    }

    /**
     * Gets the parameter index for a given local slot.
     * Accounts for long/double parameters taking 2 slots.
     * Returns -1 if the slot is not a parameter slot (either 'this' or a local variable).
     */
    private int getParamIndexForSlot(int slot) {
        IRMethod method = context.getIrMethod();
        boolean isStatic = method.isStatic();

        if (!isStatic && slot == 0) {
            return -1;
        }

        String descriptor = method.getDescriptor();
        if (descriptor == null) {
            return isStatic ? slot : slot - 1;
        }

        List<String> paramTypes = parseParameterTypes(descriptor);
        int currentSlot = isStatic ? 0 : 1;

        for (int paramIndex = 0; paramIndex < paramTypes.size(); paramIndex++) {
            String paramType = paramTypes.get(paramIndex);
            int slotsForParam = 1;
            if ("J".equals(paramType) || "D".equals(paramType)) {
                slotsForParam = 2;
            }

            if (slot >= currentSlot && slot < currentSlot + slotsForParam) {
                return paramIndex;
            }
            currentSlot += slotsForParam;
        }

        return -1;
    }

    private String getTypeCategory(SourceType type) {
        if (type == null) {
            return "unknown";
        }
        if (type.isPrimitive()) {
            // Split primitives by JVM verification type so a slot reused across
            // incompatible widths (e.g. int vs long) gets distinct source variables.
            // boolean/byte/char/short/int share the "int" verification type and stay
            // grouped (preserving int<->boolean handling).
            switch (((PrimitiveSourceType) type).getKind()) {
                case LONG:   return "primitive:long";
                case FLOAT:  return "primitive:float";
                case DOUBLE: return "primitive:double";
                default:     return "primitive:int";
            }
        }
        if (type instanceof ArraySourceType) {
            return "array";
        }
        if (type instanceof ReferenceSourceType) {
            ReferenceSourceType refType = (ReferenceSourceType) type;
            return refType.getInternalName();
        }
        return "reference";
    }
}
