package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.recovery.ControlFlowContext.StructuredRegion;
import com.tonic.analysis.source.recovery.StructuralAnalyzer.RegionInfo;
import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.ir.CompareOp;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Recovers Statement AST nodes from IR blocks using structural analysis.
 */
public class StatementRecoverer {

    private final ControlFlowContext context;
    private final StructuralAnalyzer analyzer;
    private final ExpressionRecoverer exprRecoverer;
    private final TypeRecoverer typeRecoverer;

    public StatementRecoverer(ControlFlowContext context, StructuralAnalyzer analyzer,
                              ExpressionRecoverer exprRecoverer) {
        this.context = context;
        this.analyzer = analyzer;
        this.exprRecoverer = exprRecoverer;
        this.typeRecoverer = new TypeRecoverer();
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

        // First, emit declarations for all phi variables at method scope
        // This ensures they're declared before any use
        emitPhiDeclarations(method, statements);

        // Check if method has exception handlers
        List<ExceptionHandler> handlers = method.getExceptionHandlers();
        if (handlers != null && !handlers.isEmpty()) {
            // Recover with exception handling
            statements.addAll(recoverWithExceptionHandling(entry, handlers));
        } else {
            // Simple recovery without exception handling
            statements.addAll(recoverBlockSequence(entry, new HashSet<>()));
        }

        return new BlockStmt(statements);
    }

    /**
     * Recovers statements with exception handling structure.
     * Handles both simple and nested try-catch regions.
     */
    private List<Statement> recoverWithExceptionHandling(IRBlock entry, List<ExceptionHandler> handlers) {
        List<Statement> result = new ArrayList<>();

        // Track which blocks are handler blocks (should be skipped in normal flow)
        Set<IRBlock> handlerBlocks = new HashSet<>();
        for (ExceptionHandler handler : handlers) {
            if (handler.getHandlerBlock() != null) {
                handlerBlocks.add(handler.getHandlerBlock());
                // Also collect blocks reachable from handler
                collectReachableBlocks(handler.getHandlerBlock(), handlerBlocks);
            }
        }

        // Merge handlers that share the same handler block - these are split try regions
        // (e.g., when a try block has return statements that break the contiguous range)
        List<ExceptionHandler> mergedHandlers = mergeHandlersWithSameTarget(handlers);

        // Find the outermost handler (one that starts at entry or covers most code)
        ExceptionHandler outerHandler = findOutermostHandler(entry, mergedHandlers);

        if (outerHandler != null) {
            // Get all handlers for this try region - handlers sharing same handler block are the same region
            List<ExceptionHandler> outerHandlers = new ArrayList<>();
            List<ExceptionHandler> innerHandlers = new ArrayList<>();

            for (ExceptionHandler h : mergedHandlers) {
                // Handlers with the same handler block belong to the same try-catch
                if (h.getHandlerBlock() == outerHandler.getHandlerBlock()) {
                    outerHandlers.add(h);
                } else {
                    innerHandlers.add(h);
                }
            }

            // Recover try block content - may contain nested try-catch
            Set<IRBlock> stopBlocks = new HashSet<>(handlerBlocks);
            List<Statement> tryStmts;
            if (!innerHandlers.isEmpty()) {
                // Has nested handlers - recover with nesting
                tryStmts = recoverWithNestedHandlers(entry, innerHandlers, stopBlocks);
            } else {
                tryStmts = recoverBlockSequence(entry, stopBlocks);
            }
            BlockStmt tryBlock = new BlockStmt(tryStmts);

            // Recover catch clauses - only emit one catch per unique handler block
            List<CatchClause> catchClauses = new ArrayList<>();
            Set<IRBlock> emittedHandlerBlocks = new HashSet<>();
            for (ExceptionHandler handler : outerHandlers) {
                if (!emittedHandlerBlocks.contains(handler.getHandlerBlock())) {
                    CatchClause catchClause = recoverCatchClause(handler);
                    if (catchClause != null) {
                        catchClauses.add(catchClause);
                        emittedHandlerBlocks.add(handler.getHandlerBlock());
                    }
                }
            }

            // Create TryCatchStmt
            if (!catchClauses.isEmpty()) {
                TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, catchClauses);
                result.add(tryCatch);
            } else {
                result.addAll(tryStmts);
            }
        } else {
            // No outer handler found, just recover normally
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
        // Group handlers by handler block
        Map<IRBlock, List<ExceptionHandler>> byHandlerBlock = new LinkedHashMap<>();
        for (ExceptionHandler h : handlers) {
            byHandlerBlock.computeIfAbsent(h.getHandlerBlock(), k -> new ArrayList<>()).add(h);
        }

        List<ExceptionHandler> merged = new ArrayList<>();
        for (List<ExceptionHandler> group : byHandlerBlock.values()) {
            if (group.size() == 1) {
                merged.add(group.get(0));
            } else {
                // Find the earliest tryStart and latest tryEnd
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

                // Create a merged handler with the union range
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

    /**
     * Finds the outermost exception handler (the one covering the most code from entry).
     */
    private ExceptionHandler findOutermostHandler(IRBlock entry, List<ExceptionHandler> handlers) {
        ExceptionHandler outermost = null;
        int maxCoverage = -1;

        for (ExceptionHandler handler : handlers) {
            // Check if this handler starts at or before entry
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
        // Collect all handler blocks - these should not be treated as normal try starts
        Set<IRBlock> allHandlerBlocks = new HashSet<>();
        for (ExceptionHandler h : innerHandlers) {
            if (h.getHandlerBlock() != null) {
                allHandlerBlocks.add(h.getHandlerBlock());
            }
        }

        // Filter out handlers whose tryStart is a handler block
        // These are internal exception handlers for catch blocks themselves (e.g., monitorexit protection)
        List<ExceptionHandler> normalHandlers = new ArrayList<>();
        for (ExceptionHandler h : innerHandlers) {
            if (!allHandlerBlocks.contains(h.getTryStart())) {
                normalHandlers.add(h);
            }
        }

        // Find blocks that are inner try region starts
        Set<IRBlock> innerTryStarts = new HashSet<>();
        for (ExceptionHandler h : normalHandlers) {
            innerTryStarts.add(h.getTryStart());
        }

        // Recursively recover, emitting inner try-catch when we reach their start blocks
        return recoverWithInnerTryCatch(start, normalHandlers, innerTryStarts, stopBlocks, new HashSet<>());
    }

    /**
     * Recursive helper that recovers blocks and emits inner try-catch statements.
     */
    private List<Statement> recoverWithInnerTryCatch(IRBlock current, List<ExceptionHandler> innerHandlers,
                                                      Set<IRBlock> innerTryStarts, Set<IRBlock> stopBlocks,
                                                      Set<IRBlock> visited) {
        List<Statement> result = new ArrayList<>();

        // Create a combined stop set that includes inner try starts
        // This prevents normal block recovery from entering inner try regions
        Set<IRBlock> combinedStops = new HashSet<>(stopBlocks);
        combinedStops.addAll(innerTryStarts);

        while (current != null && !visited.contains(current) && !stopBlocks.contains(current)) {
            // If we've reached an inner try start, don't mark as visited yet
            if (!innerTryStarts.contains(current)) {
                visited.add(current);
            }

            // Check if this block starts an inner try region
            ExceptionHandler innerHandler = findHandlerStartingAt(current, innerHandlers);

            if (innerHandler != null) {
                // Collect all handlers for this inner try region
                List<ExceptionHandler> sameRegionHandlers = new ArrayList<>();
                List<ExceptionHandler> remainingHandlers = new ArrayList<>();

                for (ExceptionHandler h : innerHandlers) {
                    if (h.getTryStart() == innerHandler.getTryStart()) {
                        sameRegionHandlers.add(h);
                    } else {
                        remainingHandlers.add(h);
                    }
                }

                // Collect handler blocks for this inner region
                Set<IRBlock> innerHandlerBlocks = new HashSet<>();
                for (ExceptionHandler h : sameRegionHandlers) {
                    if (h.getHandlerBlock() != null) {
                        innerHandlerBlocks.add(h.getHandlerBlock());
                    }
                }

                // Recover try block
                Set<IRBlock> innerStopBlocks = new HashSet<>(stopBlocks);
                innerStopBlocks.addAll(innerHandlerBlocks);

                // Also stop at blocks after the try region (successors of tryEnd)
                // This ensures we don't recover code that should be outside the try block
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
                    // Find remaining inner try starts
                    Set<IRBlock> remainingTryStarts = new HashSet<>();
                    for (ExceptionHandler h : remainingHandlers) {
                        remainingTryStarts.add(h.getTryStart());
                    }
                    tryStmts = recoverWithInnerTryCatch(current, remainingHandlers, remainingTryStarts, innerStopBlocks, new HashSet<>());
                } else {
                    // Use recoverBlocksForTry to avoid re-entering exception handling
                    tryStmts = recoverBlocksForTry(current, innerStopBlocks, new HashSet<>());
                }
                BlockStmt tryBlock = new BlockStmt(tryStmts);

                // Recover catch clauses
                List<CatchClause> catchClauses = new ArrayList<>();
                for (ExceptionHandler h : sameRegionHandlers) {
                    CatchClause catchClause = recoverCatchClause(h);
                    if (catchClause != null) {
                        catchClauses.add(catchClause);
                    }
                }

                if (!catchClauses.isEmpty()) {
                    result.add(new TryCatchStmt(tryBlock, catchClauses));
                } else {
                    result.addAll(tryStmts);
                }

                // Continue from AFTER the try region (the successor of tryEnd, not tryEnd itself)
                // tryEnd was already processed as part of the try body
                IRBlock tryEnd = innerHandler.getTryEnd();
                if (tryEnd != null) {
                    visited.add(tryEnd); // Mark try end as visited
                    // Find the next block to continue from - successor of tryEnd
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

            // Use structural analysis to recover blocks properly
            // This ensures IF_THEN, IF_THEN_ELSE, and other structures are recognized
            if (context.isProcessed(current)) {
                result.addAll(context.getStatements(current));
                // Find next unvisited successor
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
                // Simple block
                List<Statement> blockStmts = recoverSimpleBlock(current);
                result.addAll(blockStmts);
                context.setStatements(current, blockStmts);  /* Store for later retrieval */
                context.markProcessed(current);
                // Find next unvisited successor
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

            // Handle structured regions
            switch (info.getType()) {
                case IF_THEN -> {
                    Statement ifStmt = recoverIfThen(current, info);
                    result.addAll(context.collectPendingStatements());
                    result.add(ifStmt);
                    // After IF_THEN, continue from merge block
                    IRBlock merge = info.getMergeBlock();
                    if (merge != null && !visited.contains(merge) && !stopBlocks.contains(merge)) {
                        current = merge;
                    } else {
                        current = null;
                    }
                }
                case IF_THEN_ELSE -> {
                    Statement ifStmt = recoverIfThenElse(current, info);
                    result.addAll(context.collectPendingStatements());
                    result.add(ifStmt);
                    IRBlock merge = info.getMergeBlock();
                    if (merge != null && !visited.contains(merge) && !stopBlocks.contains(merge)) {
                        current = merge;
                    } else {
                        current = null;
                    }
                }
                case WHILE_LOOP -> {
                    result.add(recoverWhileLoop(current, info));
                    current = info.getLoopExit();
                }
                case DO_WHILE_LOOP -> {
                    result.add(recoverDoWhileLoop(current, info));
                    current = info.getLoopExit();
                }
                case FOR_LOOP -> {
                    result.add(recoverForLoop(current, info));
                    current = info.getLoopExit();
                }
                default -> {
                    List<Statement> blockStmts = recoverSimpleBlock(current);
                result.addAll(blockStmts);
                context.setStatements(current, blockStmts);  /* Store for later retrieval */
                    context.markProcessed(current);
                    // Find next unvisited successor
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

    /**
     * Groups exception handlers by their try region.
     */
    private Map<TryRegion, List<ExceptionHandler>> groupHandlersByTryRegion(List<ExceptionHandler> handlers) {
        Map<TryRegion, List<ExceptionHandler>> grouped = new LinkedHashMap<>();
        for (ExceptionHandler handler : handlers) {
            TryRegion region = new TryRegion(handler.getTryStart(), handler.getTryEnd());
            grouped.computeIfAbsent(region, k -> new ArrayList<>()).add(handler);
        }
        return grouped;
    }

    /**
     * Recovers a catch clause from an exception handler.
     */
    private CatchClause recoverCatchClause(ExceptionHandler handler) {
        IRBlock handlerBlock = handler.getHandlerBlock();
        if (handlerBlock == null) {
            return null;
        }

        // Determine exception type
        SourceType exceptionType;
        if (handler.isCatchAll()) {
            // catch-all typically catches Throwable or Exception
            exceptionType = new ReferenceSourceType("java/lang/Throwable", Collections.emptyList());
        } else {
            String typeName = handler.getCatchType().getInternalName();
            exceptionType = new ReferenceSourceType(typeName, Collections.emptyList());
        }

        // Generate exception variable name from exception type
        String exceptionVarName = findExceptionVariableName(handlerBlock);
        if (exceptionVarName == null) {
            // Generate name based on type
            String simpleName = exceptionType instanceof ReferenceSourceType refType
                    ? refType.getSimpleName().toLowerCase()
                    : "ex";
            exceptionVarName = simpleName.substring(0, 1) + "_ex";
        }

        // Find and register all SSA values that represent the exception in the handler
        // This includes the initial exception value from CopyInstruction and any values derived from it
        registerExceptionVariables(handlerBlock, exceptionVarName);

        // Collect all exception-related SSA values for filtering
        Set<SSAValue> exceptionValues = collectExceptionValues(handlerBlock);

        // Recover handler body - directly recover instructions from handler block
        List<Statement> handlerStmts = new ArrayList<>();

        // Process all instructions in the handler block (including terminator)
        for (IRInstruction instr : handlerBlock.getInstructions()) {
            // Skip CopyInstruction that just copies exception to itself
            if (instr instanceof CopyInstruction) {
                continue;
            }

            // Skip StoreLocal that stores the exception to a local slot
            // This is common in bytecode but redundant in decompiled code
            if (instr instanceof StoreLocalInstruction store) {
                if (store.getValue() instanceof SSAValue ssaValue) {
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

        // Recursively recover all blocks reachable from the handler
        // BUT skip if the handler block ends with a goto (meaning empty catch body)
        IRInstruction handlerTerminator = handlerBlock.getTerminator();
        if (!(handlerTerminator instanceof GotoInstruction)) {
            Set<IRBlock> visitedHandlerBlocks = new HashSet<>();
            visitedHandlerBlocks.add(handlerBlock);
            recoverHandlerBlocks(handlerBlock.getSuccessors(), visitedHandlerBlocks, handlerStmts);
        }

        // Filter out redundant assignments to exception variable AND unreachable code after return/throw
        List<Statement> filteredStmts = new ArrayList<>();
        boolean reachedTerminator = false;
        for (Statement stmt : handlerStmts) {
            // Skip everything after a return or throw (unreachable code)
            if (reachedTerminator) {
                continue;
            }
            // Skip VarDeclStmt for exception variable (it's implicit in catch)
            if (stmt instanceof VarDeclStmt varDecl) {
                if (varDecl.getName().equals(exceptionVarName)) {
                    continue;
                }
            }
            // Skip self-assignments to the exception variable
            if (stmt instanceof ExprStmt exprStmt) {
                if (exprStmt.getExpression() instanceof BinaryExpr binExpr) {
                    if (binExpr.getOperator() == BinaryOperator.ASSIGN) {
                        if (binExpr.getLeft() instanceof VarRefExpr varRef) {
                            if (varRef.getName().equals(exceptionVarName)) {
                                continue;
                            }
                        }
                    }
                }
            }
            filteredStmts.add(stmt);

            // Check if this statement is a terminator
            if (stmt instanceof ReturnStmt || stmt instanceof ThrowStmt) {
                reachedTerminator = true;
            }
        }

        BlockStmt handlerBody = new BlockStmt(filteredStmts.isEmpty() ? handlerStmts : filteredStmts);

        return CatchClause.of(exceptionType, exceptionVarName, handlerBody);
    }

    /**
     * Recursively recovers all blocks reachable from the handler until we hit a throw, return, or goto.
     * IMPORTANT: A GotoInstruction in a catch handler typically means "exit the catch and continue",
     * so we should NOT follow goto targets - they are the merge point, not part of the catch body.
     */
    private void recoverHandlerBlocks(List<IRBlock> successors, Set<IRBlock> visited, List<Statement> stmts) {
        for (IRBlock block : successors) {
            if (visited.contains(block)) continue;
            visited.add(block);

            // Recover instructions from this block
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof CopyInstruction) continue;
                Statement stmt = recoverInstruction(instr);
                if (stmt != null) {
                    stmts.add(stmt);
                }
            }

            // Check for throw terminator
            IRInstruction terminator = block.getTerminator();
            if (terminator instanceof ThrowInstruction throwInstr) {
                Statement throwStmt = recoverThrow(throwInstr);
                if (throwStmt != null) {
                    stmts.add(throwStmt);
                }
                // Stop recursion after throw
                continue;
            }

            // Check for return terminator
            if (terminator instanceof ReturnInstruction) {
                // Stop recursion after return
                continue;
            }

            // Check for goto terminator - this means exit the catch block
            // The goto target is the merge point (code after try-catch), not part of catch body
            if (terminator instanceof GotoInstruction) {
                // Stop recursion - don't follow goto into merge block
                continue;
            }

            // Continue to successors only for branches/conditionals within the catch
            recoverHandlerBlocks(block.getSuccessors(), visited, stmts);
        }
    }

    /**
     * Finds the exception variable name from the handler block.
     * The first instruction in a catch handler typically stores the exception to a local.
     */
    private String findExceptionVariableName(IRBlock handlerBlock) {
        // Look for CopyInstruction or StoreLocal that captures the exception
        for (IRInstruction instr : handlerBlock.getInstructions()) {
            if (instr instanceof StoreLocalInstruction store) {
                return "local" + store.getLocalIndex();
            }
            if (instr instanceof CopyInstruction copy) {
                SSAValue result = copy.getResult();
                if (result != null) {
                    String name = context.getExpressionContext().getVariableName(result);
                    if (name != null) {
                        return name;
                    }
                }
            }
            // Don't look past the first few instructions
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
            if (instr instanceof CopyInstruction copy) {
                if (copy.getResult() != null) {
                    exceptionValues.add(copy.getResult());
                }
                if (copy.getSource() instanceof SSAValue ssaSrc) {
                    exceptionValues.add(ssaSrc);
                }
            }
            // Also check for values with "exc_" prefix (exception naming convention)
            SSAValue result = instr.getResult();
            if (result != null) {
                String name = result.getName();
                if (name != null && name.startsWith("exc_")) {
                    exceptionValues.add(result);
                }
            }
            // Check for StoreLocal that stores to a local - the value being stored
            // at the start of a handler is typically the exception
            if (instr instanceof StoreLocalInstruction store) {
                if (store.getValue() instanceof SSAValue ssaValue) {
                    // First StoreLocal in handler is usually storing the exception
                    exceptionValues.add(ssaValue);
                }
                break; // Only first StoreLocal
            }
        }
        return exceptionValues;
    }

    /**
     * Registers all SSA values that represent the exception in an exception handler.
     * This ensures they reference the catch parameter instead of undefined "v#" names.
     */
    private void registerExceptionVariables(IRBlock handlerBlock, String exceptionVarName) {
        // First, find the original exception SSA value (has name starting with "exc_")
        Set<SSAValue> exceptionValuesSet = new HashSet<>();
        findExceptionSSAValues(handlerBlock, exceptionValuesSet, new HashSet<>());

        // Now register all found exception values with the exception variable name
        for (SSAValue excVal : exceptionValuesSet) {
            context.getExpressionContext().setVariableName(excVal, exceptionVarName);
        }
    }

    /**
     * Finds all SSA values that represent the caught exception.
     * Starts from values with "exc_" prefix and tracks through copies and local stores/loads.
     */
    private void findExceptionSSAValues(IRBlock block, Set<SSAValue> result, Set<IRBlock> visited) {
        // Track which local slots contain the exception
        Set<Integer> exceptionLocalSlots = new HashSet<>();

        // First pass: find exc_ values and which slots they're stored to
        findExceptionSSAValuesPass1(block, result, exceptionLocalSlots, new HashSet<>());

        // Second pass: find LoadLocal from those slots
        if (!exceptionLocalSlots.isEmpty()) {
            findExceptionSSAValuesPass2(block, result, exceptionLocalSlots, new HashSet<>());
        }
    }

    /**
     * First pass: Find exc_ prefix values and track which local slots they're stored to.
     */
    private void findExceptionSSAValuesPass1(IRBlock block, Set<SSAValue> result,
                                               Set<Integer> exceptionSlots, Set<IRBlock> visited) {
        if (visited.contains(block)) return;
        visited.add(block);

        for (IRInstruction instr : block.getInstructions()) {
            // For CopyInstruction, if source is an exception value, result is too
            if (instr instanceof CopyInstruction copy) {
                SSAValue copyResult = copy.getResult();
                Value copySource = copy.getSource();

                if (copySource instanceof SSAValue ssaSrc) {
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

            // For StoreLocal, check if value being stored is exception
            // If so, record which local slot now contains the exception
            if (instr instanceof StoreLocalInstruction store) {
                if (store.getValue() instanceof SSAValue ssaValue) {
                    String name = ssaValue.getName();
                    if (name != null && name.startsWith("exc_")) {
                        result.add(ssaValue);
                        exceptionSlots.add(store.getLocalIndex());
                    } else if (result.contains(ssaValue)) {
                        exceptionSlots.add(store.getLocalIndex());
                    }
                }
            }

            // Check all operands for exc_ prefix values
            for (Value operand : instr.getOperands()) {
                if (operand instanceof SSAValue ssaOp) {
                    String name = ssaOp.getName();
                    if (name != null && name.startsWith("exc_")) {
                        result.add(ssaOp);
                    }
                }
            }
        }

        // Recurse into successors
        for (IRBlock succ : block.getSuccessors()) {
            findExceptionSSAValuesPass1(succ, result, exceptionSlots, visited);
        }
    }

    /**
     * Second pass: Find LoadLocal from exception-containing slots.
     */
    private void findExceptionSSAValuesPass2(IRBlock block, Set<SSAValue> result,
                                               Set<Integer> exceptionSlots, Set<IRBlock> visited) {
        if (visited.contains(block)) return;
        visited.add(block);

        for (IRInstruction instr : block.getInstructions()) {
            // For LoadLocal from exception slots, mark the result as exception value
            if (instr instanceof LoadLocalInstruction load) {
                if (exceptionSlots.contains(load.getLocalIndex())) {
                    SSAValue loadResult = load.getResult();
                    if (loadResult != null) {
                        result.add(loadResult);
                    }
                }
            }
        }

        // Recurse into successors
        for (IRBlock succ : block.getSuccessors()) {
            findExceptionSSAValuesPass2(succ, result, exceptionSlots, visited);
        }
    }

    /**
     * Represents a try region defined by start and end blocks.
     */
    private record TryRegion(IRBlock start, IRBlock end) {}

    /**
     * Finds an exception handler whose try region starts at the given block.
     */
    private ExceptionHandler findHandlerStartingAt(IRBlock block) {
        IRMethod irMethod = context.getIrMethod();
        List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();
        if (handlers == null || handlers.isEmpty()) {
            return null;
        }
        for (ExceptionHandler handler : handlers) {
            // Compare by object identity or bytecode offset
            IRBlock tryStart = handler.getTryStart();
            if (tryStart == block ||
                (tryStart != null && tryStart.getBytecodeOffset() == block.getBytecodeOffset())) {
                return handler;
            }
        }
        return null;
    }

    /**
     * Recovers a try-catch statement starting at the given block.
     */
    private TryCatchStmt recoverTryCatch(IRBlock startBlock, ExceptionHandler mainHandler,
                                          Set<IRBlock> originalStopBlocks, Set<IRBlock> visited) {
        // Collect all handlers that cover the same try region
        IRMethod irMethod = context.getIrMethod();
        List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();
        List<ExceptionHandler> sameRegionHandlers = new ArrayList<>();
        for (ExceptionHandler h : handlers) {
            if (h.getTryStart() == mainHandler.getTryStart()) {
                sameRegionHandlers.add(h);
            }
        }

        // Calculate the stop blocks for the try body: handler blocks + original stop blocks
        Set<IRBlock> tryStopBlocks = new HashSet<>(originalStopBlocks);
        for (ExceptionHandler h : sameRegionHandlers) {
            if (h.getHandlerBlock() != null) {
                tryStopBlocks.add(h.getHandlerBlock());
            }
        }

        // Also stop at the end of the try region - blocks strictly after tryEnd
        if (mainHandler.getTryEnd() != null) {
            // The tryEnd offset is exclusive - stop at blocks AFTER the end
            int tryEndOffset = mainHandler.getTryEnd().getBytecodeOffset();
            for (IRBlock block : irMethod.getBlocks()) {
                // Only stop at blocks strictly after the try region ends
                if (block.getBytecodeOffset() > tryEndOffset) {
                    tryStopBlocks.add(block);
                }
            }
        }

        // Recover the try block content
        List<Statement> tryStmts = recoverBlocksForTry(startBlock, tryStopBlocks, visited);
        BlockStmt tryBlock = new BlockStmt(tryStmts);

        // Recover catch clauses
        List<CatchClause> catchClauses = new ArrayList<>();
        for (ExceptionHandler h : sameRegionHandlers) {
            CatchClause clause = recoverCatchClause(h);
            if (clause != null) {
                catchClauses.add(clause);
            }
        }

        if (catchClauses.isEmpty()) {
            // No valid catch clauses - return null to fall back to normal recovery
            return null;
        }

        return new TryCatchStmt(tryBlock, catchClauses);
    }

    /**
     * Recovers blocks for a try region, stopping at the specified stop blocks.
     */
    private List<Statement> recoverBlocksForTry(IRBlock startBlock, Set<IRBlock> stopBlocks, Set<IRBlock> visited) {
        List<Statement> result = new ArrayList<>();
        IRBlock current = startBlock;

        while (current != null && !visited.contains(current) && !stopBlocks.contains(current)) {
            visited.add(current);

            if (context.isProcessed(current)) {
                result.addAll(context.getStatements(current));
                current = getNextSequentialBlock(current);
                continue;
            }

            RegionInfo info = analyzer.getRegionInfo(current);
            if (info == null) {
                List<Statement> blockStmts = recoverSimpleBlock(current);
                result.addAll(blockStmts);
                context.setStatements(current, blockStmts);  /* Store for later retrieval */
                context.markProcessed(current);
                current = getNextSequentialBlock(current);
                continue;
            }

            switch (info.getType()) {
                case IF_THEN -> {
                    Statement ifStmt = recoverIfThen(current, info);
                    result.addAll(context.collectPendingStatements());
                    result.add(ifStmt);
                    current = info.getMergeBlock();
                }
                case IF_THEN_ELSE -> {
                    Statement ifStmt = recoverIfThenElse(current, info);
                    result.addAll(context.collectPendingStatements());
                    result.add(ifStmt);
                    current = info.getMergeBlock();
                }
                case WHILE_LOOP -> {
                    result.add(recoverWhileLoop(current, info));
                    current = info.getLoopExit();
                }
                case DO_WHILE_LOOP -> {
                    result.add(recoverDoWhileLoop(current, info));
                    current = info.getLoopExit();
                }
                case FOR_LOOP -> {
                    result.add(recoverForLoop(current, info));
                    current = info.getLoopExit();
                }
                default -> {
                    List<Statement> blockStmts = recoverSimpleBlock(current);
                result.addAll(blockStmts);
                context.setStatements(current, blockStmts);  /* Store for later retrieval */
                    context.markProcessed(current);
                    current = getNextSequentialBlock(current);
                }
            }
        }

        return result;
    }

    /**
     * Finds the block to continue from after a try-catch region.
     */
    private IRBlock findBlockAfterTryCatch(ExceptionHandler handler, Set<IRBlock> visited) {
        // The merge point is typically reachable from both the try end and the catch handler
        // Look for a successor of the handler block that is after the try region
        IRBlock handlerBlock = handler.getHandlerBlock();
        if (handlerBlock != null) {
            for (IRBlock succ : handlerBlock.getSuccessors()) {
                // Find a block that's not already visited and not part of the handler
                if (!visited.contains(succ)) {
                    return succ;
                }
            }
        }

        // If tryEnd is specified, look for block at that offset
        if (handler.getTryEnd() != null) {
            IRMethod irMethod = context.getIrMethod();
            int endOffset = handler.getTryEnd().getBytecodeOffset();
            for (IRBlock block : irMethod.getBlocks()) {
                if (block.getBytecodeOffset() >= endOffset && !visited.contains(block)) {
                    return block;
                }
            }
        }

        return null;
    }

    /** Map from local slot name to unified type (computed from all assignments) */
    private Map<String, SourceType> localSlotUnifiedTypes = new HashMap<>();

    /**
     * Emits declarations for phi variables at method scope.
     * Only phi variables (values that merge from multiple control flow paths) need
     * early declaration. Other variables are declared inline where they're defined.
     */
    private void emitPhiDeclarations(IRMethod method, List<Statement> statements) {
        Set<SSAValue> phiValues = new LinkedHashSet<>();
        Set<String> declaredNames = new HashSet<>();

        // Identify exception handler blocks - don't declare their phi values at method start
        Set<IRBlock> handlerBlocks = collectExceptionHandlerBlocks(method);

        // First pass: Set up variable names for LoadLocal and StoreLocal instructions
        // This ensures consistent naming across all references to the same local slot
        boolean isStatic = method.isStatic();

        // Collect all types assigned to each local slot for unified type computation
        Map<String, List<SourceType>> localSlotTypes = new HashMap<>();

        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof LoadLocalInstruction loadLocal) {
                    if (loadLocal.getResult() != null) {
                        int localIndex = loadLocal.getLocalIndex();
                        // For instance methods, slot 0 is 'this'
                        String localName = (!isStatic && localIndex == 0) ? "this" : "local" + localIndex;
                        context.getExpressionContext().setVariableName(loadLocal.getResult(), localName);
                    }
                } else if (instr instanceof StoreLocalInstruction storeLocal) {
                    // Track which local slots are used so we can declare them
                    int localIndex = storeLocal.getLocalIndex();
                    // For instance methods, slot 0 is 'this' (though storing to 'this' is unusual)
                    String localName = (!isStatic && localIndex == 0) ? "this" : "local" + localIndex;

                    // Collect the type being stored for unified type computation
                    Value storedValue = storeLocal.getValue();
                    SourceType storedType = typeRecoverer.recoverType(storedValue);
                    if (storedType != null && !storedType.isVoid()) {
                        localSlotTypes.computeIfAbsent(localName, k -> new ArrayList<>()).add(storedType);
                    }

                    // If the value being stored is an SSA value, track its name too
                    // BUT don't rename if the value is a parameter (already named this/arg#)
                    if (storeLocal.getValue() instanceof SSAValue ssaValue) {
                        // Check if this value is a parameter (has no definition)
                        // Parameters have names set by assignParameterNames ("this" or "arg#")
                        boolean isParameter = ssaValue.getDefinition() == null && method.getParameters().contains(ssaValue);
                        if (!isParameter) {
                            context.getExpressionContext().setVariableName(ssaValue, localName);
                        }
                    }
                }
            }
        }

        // Compute unified types for each local slot
        localSlotUnifiedTypes.clear();
        for (Map.Entry<String, List<SourceType>> entry : localSlotTypes.entrySet()) {
            String slotName = entry.getKey();
            List<SourceType> types = entry.getValue();
            if (!types.isEmpty()) {
                SourceType unifiedType = typeRecoverer.computeCommonType(types);
                localSlotUnifiedTypes.put(slotName, unifiedType);
            }
        }

        // Only collect PHI instructions that need early declaration
        List<PhiInstruction> phiInstructions = new ArrayList<>();
        for (IRBlock block : method.getBlocks()) {
            // Skip exception handler blocks - their variables are declared inline
            if (handlerBlocks.contains(block)) {
                continue;
            }

            // Only phi results need early declaration (they merge values from multiple paths)
            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (phi.getResult() != null) {
                    phiValues.add(phi.getResult());
                    phiInstructions.add(phi);
                }
            }
        }

        // Sort phi values by dependency order
        List<SSAValue> sortedValues = sortByDependencies(phiValues);

        // Create a map from SSAValue to PhiInstruction for lookup
        Map<SSAValue, PhiInstruction> valueToPhiMap = new HashMap<>();
        for (PhiInstruction phi : phiInstructions) {
            valueToPhiMap.put(phi.getResult(), phi);
        }

        // Emit declarations only for phi variables
        for (SSAValue value : sortedValues) {
            PhiInstruction phi = valueToPhiMap.get(value);
            emitPhiDeclaration(phi, statements, declaredNames);
        }
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
    private void emitPhiDeclaration(PhiInstruction phi, List<Statement> statements, Set<String> declaredNames) {
        if (phi == null) return;
        SSAValue result = phi.getResult();
        if (result == null) return;

        String name = context.getExpressionContext().getVariableName(result);
        if (name == null) {
            name = "v" + result.getId();
        }

        // Skip 'this' and parameters - already declared by method signature
        if ("this".equals(name) || name.startsWith("arg")) {
            return;
        }

        // Skip if already declared
        if (declaredNames.contains(name) || context.getExpressionContext().isDeclared(name)) {
            return;
        }

        // Compute unified type from all incoming phi values
        // This handles cases where different paths have different types (e.g., Insets vs Graphics)
        SourceType type = computePhiUnifiedType(phi);

        // Mark as declared and materialized so subsequent uses reference this variable
        declaredNames.add(name);
        context.getExpressionContext().markDeclared(name);
        context.getExpressionContext().markMaterialized(result);
        context.getExpressionContext().setVariableName(result, name);

        // Phi variables get default values (they'll be assigned in control flow)
        Expression initValue = getDefaultValue(type);
        statements.add(new VarDeclStmt(type, name, initValue));
    }

    /**
     * Computes a unified type for a phi instruction by examining all incoming values.
     * For incompatible types (e.g., Insets and Graphics), returns their common supertype (Object).
     */
    private SourceType computePhiUnifiedType(PhiInstruction phi) {
        SSAValue result = phi.getResult();

        // First, try to get the unified type from StoreLocal instructions
        // This is more comprehensive as it includes ALL assignments to the local slot,
        // not just those that flow into the PHI
        String localName = context.getExpressionContext().getVariableName(result);
        if (localName != null && localSlotUnifiedTypes.containsKey(localName)) {
            return localSlotUnifiedTypes.get(localName);
        }

        // Fallback: collect types from PHI operands
        List<SourceType> incomingTypes = new ArrayList<>();
        for (Value value : phi.getOperands()) {
            SourceType valueType = typeRecoverer.recoverType(value);
            if (valueType != null && !valueType.isVoid()) {
                incomingTypes.add(valueType);
            }
        }

        if (!incomingTypes.isEmpty()) {
            return typeRecoverer.computeCommonType(incomingTypes);
        }

        // Final fallback: use PHI result type
        return typeRecoverer.recoverType(result);
    }

    /**
     * Collects all blocks that are part of exception handlers (including reachable blocks).
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
                // Collect the handler block and all blocks reachable from it
                collectReachableBlocks(handlerBlock, handlerBlocks);
            }
        }
        return handlerBlocks;
    }

    /**
     * Recursively collects all blocks reachable from the given block.
     */
    private void collectReachableBlocks(IRBlock block, Set<IRBlock> collected) {
        if (collected.contains(block)) return;
        collected.add(block);
        for (IRBlock succ : block.getSuccessors()) {
            collectReachableBlocks(succ, collected);
        }
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
        if (inProgress.contains(value)) return; // Cycle detected, skip

        inProgress.add(value);

        // Visit dependencies first (operands of the defining instruction)
        IRInstruction def = value.getDefinition();
        if (def != null) {
            for (com.tonic.analysis.ssa.value.Value operand : def.getOperands()) {
                if (operand instanceof SSAValue ssaDep && allValues.contains(ssaDep)) {
                    visitForSort(ssaDep, allValues, visited, inProgress, result);
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

        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.isEmpty()) return true; // Unused - can skip

        // Check if all uses are "consuming" instructions (invoke arguments, throw, return)
        for (IRInstruction use : uses) {
            // If used as argument to another invoke - intermediate
            if (use instanceof InvokeInstruction) continue;
            // If thrown - final use
            if (use instanceof ThrowInstruction) continue;
            // If returned - final use
            if (use instanceof ReturnInstruction) continue;
            // If used in a branch condition - not intermediate
            if (use instanceof BranchInstruction) return false;
            // If stored to field - not intermediate
            if (use instanceof PutFieldInstruction) return false;
            // If stored to array - not intermediate
            if (use instanceof ArrayStoreInstruction) return false;
            // If stored to local - not intermediate
            if (use instanceof StoreLocalInstruction) return false;
            // If used in a phi - not intermediate (needs to be assigned for phi merge)
            if (use instanceof PhiInstruction) return false;
        }
        return true;
    }

    /**
     * Checks if an SSA value is used by a StoreLocalInstruction.
     * In this case, the instruction producing the value should be skipped
     * and let StoreLocalInstruction handle the emission. The StoreLocal
     * will create the local variable, and other uses will reference that local.
     */
    private boolean isUsedByStoreLocal(SSAValue value) {
        if (value == null) return false;

        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.isEmpty()) return false;

        // Check if any use is a StoreLocalInstruction
        for (IRInstruction use : uses) {
            if (use instanceof StoreLocalInstruction) {
                return true;
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

        java.util.List<IRInstruction> uses = value.getUses();
        for (IRInstruction use : uses) {
            if (use instanceof PhiInstruction phi) {
                return phi;
            }
        }
        return null;
    }

    /**
     * Gets a default value for the given type.
     */
    private Expression getDefaultValue(SourceType type) {
        if (type instanceof com.tonic.analysis.source.ast.type.PrimitiveSourceType pst) {
            if (pst == com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN) {
                return LiteralExpr.ofBoolean(false);
            } else if (pst == com.tonic.analysis.source.ast.type.PrimitiveSourceType.LONG) {
                return LiteralExpr.ofLong(0L);
            } else if (pst == com.tonic.analysis.source.ast.type.PrimitiveSourceType.FLOAT) {
                return LiteralExpr.ofFloat(0.0f);
            } else if (pst == com.tonic.analysis.source.ast.type.PrimitiveSourceType.DOUBLE) {
                return LiteralExpr.ofDouble(0.0);
            } else {
                // INT, SHORT, BYTE, CHAR
                return LiteralExpr.ofInt(0);
            }
        }
        return LiteralExpr.ofNull();
    }

    /**
     * Recovers statements starting from a block, following control flow.
     */
    /** Tracks try handlers that have already been processed to avoid infinite loops */
    private Set<ExceptionHandler> processedTryHandlers = new HashSet<>();

    public List<Statement> recoverBlockSequence(IRBlock startBlock, Set<IRBlock> stopBlocks) {
        List<Statement> result = new ArrayList<>();
        Set<IRBlock> visited = new HashSet<>();
        IRBlock current = startBlock;

        while (current != null && !visited.contains(current) && !stopBlocks.contains(current)) {
            // Check if this block starts a try region BEFORE marking as visited
            ExceptionHandler tryHandler = findHandlerStartingAt(current);
            if (tryHandler != null && !processedTryHandlers.contains(tryHandler)) {
                // Mark this handler as processed to avoid infinite loops
                processedTryHandlers.add(tryHandler);
                // Use a new visited set for try recovery that starts fresh for the try block
                Set<IRBlock> tryVisited = new HashSet<>(visited);
                // Emit try-catch for this protected region
                TryCatchStmt tryCatch = recoverTryCatch(current, tryHandler, stopBlocks, tryVisited);
                if (tryCatch != null) {
                    result.add(tryCatch);
                    // Merge the try-visited blocks back
                    visited.addAll(tryVisited);
                    // Skip to after the try-catch region
                    current = findBlockAfterTryCatch(tryHandler, visited);
                    continue;
                }
            }

            visited.add(current);

            if (context.isProcessed(current)) {
                result.addAll(context.getStatements(current));
                current = getNextSequentialBlock(current);
                continue;
            }

            RegionInfo info = analyzer.getRegionInfo(current);
            if (info == null) {
                // No structural info - recover as simple block
                List<Statement> blockStmts = recoverSimpleBlock(current);
                result.addAll(blockStmts);
                context.setStatements(current, blockStmts);  /* Store for later retrieval */
                context.markProcessed(current);
                current = getNextSequentialBlock(current);
                continue;
            }

            switch (info.getType()) {
                case IF_THEN -> {
                    Statement ifStmt = recoverIfThen(current, info);
                    // Collect any header statements that were placed in pending
                    result.addAll(context.collectPendingStatements());
                    result.add(ifStmt);
                    IRBlock merge = info.getMergeBlock();
                    if (merge != null) {
                        current = merge;
                    } else {
                        // Null merge - find next unprocessed block
                        current = findNextUnprocessedBlock(current, visited, stopBlocks);
                    }
                }
                case IF_THEN_ELSE -> {
                    Statement ifStmt = recoverIfThenElse(current, info);
                    // Collect any header statements that were placed in pending
                    result.addAll(context.collectPendingStatements());
                    result.add(ifStmt);
                    IRBlock merge = info.getMergeBlock();
                    if (merge != null) {
                        current = merge;
                    } else {
                        // Null merge - find next unprocessed block
                        current = findNextUnprocessedBlock(current, visited, stopBlocks);
                    }
                }
                case WHILE_LOOP -> {
                    result.add(recoverWhileLoop(current, info));
                    current = info.getLoopExit();
                }
                case DO_WHILE_LOOP -> {
                    result.add(recoverDoWhileLoop(current, info));
                    current = info.getLoopExit();
                }
                case FOR_LOOP -> {
                    result.add(recoverForLoop(current, info));
                    current = info.getLoopExit();
                }
                case SWITCH -> {
                    result.add(recoverSwitch(current, info));
                    current = findSwitchMerge(info);
                }
                case IRREDUCIBLE -> {
                    result.add(recoverIrreducible(current, info));
                    current = null;
                }
                default -> {
                    List<Statement> blockStmts = recoverSimpleBlock(current);
                result.addAll(blockStmts);
                context.setStatements(current, blockStmts);  /* Store for later retrieval */
                    context.markProcessed(current);
                    current = getNextSequentialBlock(current);
                }
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
        // First try: sequential successor (common case)
        IRBlock next = getNextSequentialBlock(current);
        if (next != null && !visited.contains(next) && !stopBlocks.contains(next)) {
            return next;
        }

        // Second try: any unvisited successor
        for (IRBlock succ : current.getSuccessors()) {
            if (!visited.contains(succ) && !stopBlocks.contains(succ)) {
                return succ;
            }
        }

        // Third try: scan all method blocks for unvisited reachable blocks
        IRMethod method = context.getIrMethod();
        for (IRBlock block : method.getBlocks()) {
            if (!visited.contains(block) && !stopBlocks.contains(block) && !context.isProcessed(block)) {
                // Check if block is reachable from entry
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

    private List<Statement> recoverSimpleBlock(IRBlock block) {
        List<Statement> statements = new ArrayList<>();

        // Process phi instructions as variable assignments
        for (PhiInstruction phi : block.getPhiInstructions()) {
            Statement stmt = recoverPhiAssignment(phi);
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        // Process regular instructions
        for (IRInstruction instr : block.getInstructions()) {
            Statement stmt = recoverInstruction(instr);
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        return statements;
    }

    private Statement recoverPhiAssignment(PhiInstruction phi) {
        // Phi declarations are emitted at method start by emitPhiDeclarations()
        // At block level, we don't need to emit anything for phi nodes
        // The phi variable is already declared and will be assigned through
        // the incoming values from predecessor blocks
        return null;
    }

    private Statement recoverInstruction(IRInstruction instr) {
        if (instr.isTerminator()) {
            return recoverTerminator(instr);
        }

        if (instr instanceof StoreLocalInstruction store) {
            return recoverStoreLocal(store);
        }

        if (instr instanceof PutFieldInstruction putField) {
            return recoverPutField(putField);
        }

        if (instr instanceof ArrayStoreInstruction arrayStore) {
            return recoverArrayStore(arrayStore);
        }

        if (instr instanceof InvokeInstruction invoke) {
            // Handle <init> calls specially
            if ("<init>".equals(invoke.getName())) {
                Expression expr = exprRecoverer.recover(invoke);
                // For super()/this() calls, emit as expression statement
                if (expr instanceof MethodCallExpr mce) {
                    String methodName = mce.getMethodName();
                    if ("super".equals(methodName) || "this".equals(methodName)) {
                        return new ExprStmt(expr);
                    }
                }
                // For new X(...) combined expressions, check if it's assigned somewhere
                if (expr instanceof NewExpr) {
                    // Skip - will be used where the value is consumed
                    return null;
                }
                return new ExprStmt(expr);
            }
            // Void invocations become expression statements
            if (invoke.getResult() == null || invoke.getResult().getType() == null) {
                Expression expr = exprRecoverer.recover(invoke);
                return new ExprStmt(expr);
            }
            // Skip invoke results that are used by a StoreLocalInstruction
            // StoreLocalInstruction will handle the emission with proper local name
            // But we still need to recover and cache the expression so recoverOperand can find it
            SSAValue result = invoke.getResult();
            if (result != null && isUsedByStoreLocal(result)) {
                // Recover and cache for later use by StoreLocal
                Expression expr = exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
            // Skip intermediate invoke results that are only used by other instructions
            // These will be inlined at their usage site
            if (result != null && isIntermediateValue(result)) {
                // Cache the expression for later inlining
                exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, exprRecoverer.recover(invoke));
                return null;
            }
        }

        // Skip NewInstruction - will be combined with <init> call
        if (instr instanceof NewInstruction) {
            // Register the pending new and skip emission
            if (instr.getResult() != null) {
                exprRecoverer.recover(instr); // This registers the pending new
            }
            return null;
        }

        if (instr instanceof MonitorEnterInstruction || instr instanceof MonitorExitInstruction) {
            // Monitor instructions handled by synchronized recovery
            return null;
        }

        // Note: ThrowInstruction is handled in recoverTerminator()

        // Skip LoadLocal instructions - they don't need variable declarations
        // (they load from already-declared locals or parameters like 'this')
        if (instr instanceof LoadLocalInstruction loadLocal) {
            // Set the variable name for this SSA value so it's consistent with StoreLocal naming
            // BUT don't overwrite if already set (e.g., by registerExceptionVariables for catch params)
            if (loadLocal.getResult() != null) {
                String existingName = context.getExpressionContext().getVariableName(loadLocal.getResult());
                if (existingName == null) {
                    int localIndex = loadLocal.getLocalIndex();
                    boolean isStatic = context.getIrMethod().isStatic();
                    String localName = (!isStatic && localIndex == 0) ? "this" : "local" + localIndex;
                    context.getExpressionContext().setVariableName(loadLocal.getResult(), localName);
                }
                Expression value = exprRecoverer.recover(loadLocal);
                context.getExpressionContext().cacheExpression(loadLocal.getResult(), value);
            }
            return null;
        }

        // Skip GetField instructions - they produce pure expressions that should be inlined
        // at usage sites rather than assigned to intermediate variables
        if (instr instanceof GetFieldInstruction) {
            // Cache the expression but don't emit a declaration
            if (instr.getResult() != null) {
                Expression value = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), value);
            }
            return null;
        }

        // Handle ConstantInstruction - check if it flows into a PHI
        if (instr instanceof ConstantInstruction constInstr) {
            SSAValue result = constInstr.getResult();
            if (result != null) {
                // Check if this constant feeds directly into a PHI instruction
                PhiInstruction targetPhi = getPhiUsingValue(result);
                if (targetPhi != null && targetPhi.getResult() != null) {
                    // Emit an assignment to the PHI variable
                    String phiVarName = context.getExpressionContext().getVariableName(targetPhi.getResult());
                    if (phiVarName != null) {
                        Expression value = exprRecoverer.recover(constInstr);
                        SourceType type = value.getType();
                        if (type == null) {
                            type = typeRecoverer.recoverType(result);
                        }
                        VarRefExpr target = new VarRefExpr(phiVarName, type, targetPhi.getResult());
                        return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
                    }
                }
                // Default: cache for inlining at usage sites
                Expression value = exprRecoverer.recover(constInstr);
                context.getExpressionContext().cacheExpression(result, value);
            }
            return null;
        }

        // Other instructions with results - may need variable declaration
        if (instr.getResult() != null) {
            // Skip if result is used by a StoreLocalInstruction
            // StoreLocalInstruction will handle the emission with proper local name
            // But we still need to recover and cache the expression so recoverOperand can find it
            if (isUsedByStoreLocal(instr.getResult())) {
                Expression expr = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), expr);
                return null;
            }
            return recoverVarDecl(instr);
        }

        return null;
    }

    private Statement recoverTerminator(IRInstruction instr) {
        if (instr instanceof ReturnInstruction ret) {
            return recoverReturn(ret);
        }
        if (instr instanceof ThrowInstruction throwInstr) {
            return recoverThrow(throwInstr);
        }
        // Branch, Goto, Switch handled by structural analysis
        return null;
    }

    private Statement recoverStoreLocal(StoreLocalInstruction store) {
        Expression value = exprRecoverer.recoverOperand(store.getValue());
        int localIndex = store.getLocalIndex();

        // Use consistent local slot naming - slot 0 in instance methods is 'this'
        boolean isStatic = context.getIrMethod().isStatic();
        String name = (!isStatic && localIndex == 0) ? "this" : "local" + localIndex;

        // Use the pre-computed unified type for this local slot if available
        // This ensures the declared type is compatible with ALL values assigned to this slot
        SourceType type = getLocalSlotUnifiedType(name);
        if (type == null) {
            // Fallback to the current expression's type
            type = value.getType();
        }
        if (type == null) {
            type = com.tonic.analysis.source.ast.type.VoidSourceType.INSTANCE;
        }

        // Mark the stored value as materialized - subsequent uses should reference the variable
        // BUT don't rename parameters (they keep their arg# names)
        if (store.getValue() instanceof SSAValue ssaValue) {
            boolean isParameter = ssaValue.getDefinition() == null &&
                                  context.getIrMethod().getParameters().contains(ssaValue);
            if (!isParameter) {
                context.getExpressionContext().markMaterialized(ssaValue);
                // Also set the variable name for this SSA value so lookups work
                context.getExpressionContext().setVariableName(ssaValue, name);
            }
        }

        // Check if already declared - if so, emit assignment instead of declaration
        if (context.getExpressionContext().isDeclared(name)) {
            // Create assignment: name = value
            VarRefExpr target = new VarRefExpr(name, type, null);
            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
        }

        // Mark as declared and return declaration
        context.getExpressionContext().markDeclared(name);
        return new VarDeclStmt(type, name, value);
    }

    private Statement recoverPutField(PutFieldInstruction putField) {
        Expression receiver = putField.isStatic() ? null : exprRecoverer.recoverOperand(putField.getObjectRef());

        // Get field type for boolean detection
        SourceType fieldType = typeRecoverer.recoverType(putField.getDescriptor());
        // Pass type hint for boolean detection
        Expression value = exprRecoverer.recoverOperand(putField.getValue(), fieldType);

        // Create field access expression as assignment target
        Expression target = new com.tonic.analysis.source.ast.expr.FieldAccessExpr(
                receiver, putField.getName(), putField.getOwner(), putField.isStatic(), fieldType);

        return new ExprStmt(new com.tonic.analysis.source.ast.expr.BinaryExpr(
                com.tonic.analysis.source.ast.expr.BinaryOperator.ASSIGN, target, value, fieldType));
    }

    private Statement recoverArrayStore(ArrayStoreInstruction arrayStore) {
        Expression array = exprRecoverer.recoverOperand(arrayStore.getArray());
        Expression index = exprRecoverer.recoverOperand(arrayStore.getIndex());
        Expression value = exprRecoverer.recoverOperand(arrayStore.getValue());

        SourceType elemType = value.getType();
        Expression target = new com.tonic.analysis.source.ast.expr.ArrayAccessExpr(array, index, elemType);

        return new ExprStmt(new com.tonic.analysis.source.ast.expr.BinaryExpr(
                com.tonic.analysis.source.ast.expr.BinaryOperator.ASSIGN, target, value, elemType));
    }

    private Statement recoverReturn(ReturnInstruction ret) {
        if (ret.isVoidReturn()) {
            return new ReturnStmt(null);
        }
        // Get method return type for boolean detection
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
        return new ReturnStmt(value);
    }

    private Statement recoverThrow(ThrowInstruction throwInstr) {
        Expression exception = exprRecoverer.recoverOperand(throwInstr.getException());
        return new ThrowStmt(exception);
    }

    private Statement recoverVarDecl(IRInstruction instr) {
        SSAValue result = instr.getResult();
        String name = context.getExpressionContext().getVariableName(result);
        if (name == null) {
            name = "v" + result.getId();
        }

        SourceType type = typeRecoverer.recoverType(result);
        Expression value = exprRecoverer.recover(instr);

        // Cache the expression for later reference
        context.getExpressionContext().cacheExpression(result, value);

        // Mark as materialized so subsequent uses will reference this variable
        context.getExpressionContext().markMaterialized(result);
        context.getExpressionContext().setVariableName(result, name);

        // Check if already declared - if so, emit assignment instead
        if (context.getExpressionContext().isDeclared(name)) {
            VarRefExpr target = new VarRefExpr(name, type, result);
            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
        }

        // Mark as declared
        context.getExpressionContext().markDeclared(name);

        return new VarDeclStmt(type, name, value);
    }

    private Statement recoverIfThen(IRBlock header, RegionInfo info) {
        // Mark header as processed BEFORE recursing to prevent infinite loops
        context.markProcessed(header);

        // First, recover any instructions in the header block BEFORE the branch
        // These are setup instructions that should execute before the condition
        List<Statement> headerStmts = recoverBlockInstructions(header);

        // Use negation flag from structural analysis to get correct condition polarity
        Expression condition = recoverCondition(header, info.isConditionNegated());
        Set<IRBlock> stopBlocks = new HashSet<>();
        if (info.getMergeBlock() != null) {
            stopBlocks.add(info.getMergeBlock());
        }

        List<Statement> thenStmts = recoverBlockSequence(info.getThenBlock(), stopBlocks);
        BlockStmt thenBlock = new BlockStmt(thenStmts);

        IfStmt ifStmt = new IfStmt(condition, thenBlock, null);

        // If there are header instructions, store them in context for the caller to collect
        if (!headerStmts.isEmpty()) {
            context.addPendingStatements(headerStmts);
        }
        return ifStmt;
    }

    private Statement recoverIfThenElse(IRBlock header, RegionInfo info) {
        // Mark header as processed BEFORE recursing to prevent infinite loops
        context.markProcessed(header);

        // First, recover any instructions in the header block BEFORE the branch
        List<Statement> headerStmts = recoverBlockInstructions(header);

        // Use negation flag from structural analysis to get correct condition polarity
        Expression condition = recoverCondition(header, info.isConditionNegated());
        Set<IRBlock> stopBlocks = new HashSet<>();
        if (info.getMergeBlock() != null) {
            stopBlocks.add(info.getMergeBlock());
        }

        List<Statement> thenStmts = recoverBlockSequence(info.getThenBlock(), stopBlocks);
        List<Statement> elseStmts = recoverBlockSequence(info.getElseBlock(), stopBlocks);

        // Check for boolean return pattern: if(cond) return true/false; else return false/true;
        // This collapses patterns like isClientThread() { if(x==y) return true; else return false; }
        // into the simpler: return x == y;
        if (isBooleanReturnPattern(thenStmts, elseStmts)) {
            Statement booleanReturn = collapseToBooleanReturn(condition, thenStmts, elseStmts);
            // If there are header instructions, we need to include them
            if (!headerStmts.isEmpty()) {
                context.addPendingStatements(headerStmts);
            }
            return booleanReturn;
        }

        // Check for OR condition chain pattern: if(a) { body } else { if(b) { body } else { ... } }
        // This merges into: if(a || b) { body } else { ... }
        // Common pattern when bytecode compiles "if (a || b)" as sequential condition checks
        if (isOrConditionChain(thenStmts, elseStmts)) {
            Statement mergedIf = mergeOrConditions(condition, thenStmts, elseStmts);
            if (!headerStmts.isEmpty()) {
                context.addPendingStatements(headerStmts);
            }
            return mergedIf;
        }

        // Check for PHI-based boolean return pattern:
        // if(cond) { } else { } followed by merge block with PHI(true,false) + return PHI
        // This happens when bytecode uses: IF_xCMP -> ICONST_0 -> GOTO merge; ICONST_1 -> merge: IRETURN
        if (isBooleanPhiReturnPattern(thenStmts, elseStmts, info.getMergeBlock())) {
            Statement booleanReturn = collapsePhiToBooleanReturn(condition, info.getMergeBlock());
            if (booleanReturn != null) {
                // Mark merge block as processed since we're consuming it
                if (info.getMergeBlock() != null) {
                    context.markProcessed(info.getMergeBlock());
                }
                if (!headerStmts.isEmpty()) {
                    context.addPendingStatements(headerStmts);
                }
                return booleanReturn;
            }
        }

        // Check for PHI-based boolean assignment pattern:
        // if(cond) { const 0 } else { const 1 } followed by PHI that merges these constants
        // This collapses to just using the condition or !condition directly in the expression
        // Common pattern for: x & !flag where flag is a method call result
        PhiInstruction booleanPhi = findBooleanPhiAssignmentPatternIR(header, info);
        if (booleanPhi != null) {
            collapseToBooleanPhiExpressionIR(condition, booleanPhi, info.getThenBlock());
            // Mark the then/else blocks as processed since we're consuming them
            context.markProcessed(info.getThenBlock());
            context.markProcessed(info.getElseBlock());
            // Return null to skip emitting the if-else - the boolean expression is cached for the PHI
            if (!headerStmts.isEmpty()) {
                context.addPendingStatements(headerStmts);
                return new BlockStmt(new ArrayList<>()); // Empty block to hold pending statements
            }
            return null;
        }

        BlockStmt thenBlock = new BlockStmt(thenStmts);
        BlockStmt elseBlock = new BlockStmt(elseStmts);

        IfStmt ifStmt = new IfStmt(condition, thenBlock, elseBlock);

        // If there are header instructions, store them in context for the caller to collect
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

        // Process phi instructions as variable assignments
        for (PhiInstruction phi : block.getPhiInstructions()) {
            Statement stmt = recoverPhiAssignment(phi);
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        // Process regular instructions (excluding terminators)
        for (IRInstruction instr : block.getInstructions()) {
            if (instr.isTerminator()) {
                continue; // Skip terminators - handled by control flow
            }
            Statement stmt = recoverInstruction(instr);
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        return statements;
    }

    private Statement recoverWhileLoop(IRBlock header, RegionInfo info) {
        // Mark header as processed BEFORE recursing to prevent infinite loops
        context.markProcessed(header);

        // Use negation flag from structural analysis to get correct condition polarity
        Expression condition = recoverCondition(header, info.isConditionNegated());

        Set<IRBlock> stopBlocks = new HashSet<>();
        stopBlocks.add(header); // Don't re-enter header
        if (info.getLoopExit() != null) {
            stopBlocks.add(info.getLoopExit());
        }

        List<Statement> bodyStmts = recoverBlockSequence(info.getLoopBody(), stopBlocks);
        BlockStmt body = new BlockStmt(bodyStmts);

        return new WhileStmt(condition, body);
    }

    private Statement recoverDoWhileLoop(IRBlock header, RegionInfo info) {
        // Mark header as processed BEFORE recursing to prevent infinite loops
        context.markProcessed(header);

        Set<IRBlock> stopBlocks = new HashSet<>();
        stopBlocks.add(header);
        if (info.getLoopExit() != null) {
            stopBlocks.add(info.getLoopExit());
        }

        List<Statement> bodyStmts = recoverBlockSequence(info.getLoopBody(), stopBlocks);
        BlockStmt body = new BlockStmt(bodyStmts);

        // Use negation flag from structural analysis to get correct condition polarity
        Expression condition = recoverCondition(header, info.isConditionNegated());

        return new DoWhileStmt(body, condition);
    }

    private Statement recoverForLoop(IRBlock header, RegionInfo info) {
        // For simplicity, recover as while loop
        // Full for-loop recovery would require detecting init and update expressions
        return recoverWhileLoop(header, info);
    }

    private Statement recoverSwitch(IRBlock header, RegionInfo info) {
        // Mark header as processed BEFORE recursing to prevent infinite loops
        context.markProcessed(header);

        IRInstruction terminator = header.getTerminator();
        if (!(terminator instanceof SwitchInstruction sw)) {
            return new IRRegionStmt(List.of(header));
        }

        Expression selector = exprRecoverer.recoverOperand(sw.getKey());
        List<SwitchCase> cases = new ArrayList<>();

        // Find merge point for switch
        IRBlock mergeBlock = findSwitchMerge(info);
        Set<IRBlock> stopBlocks = new HashSet<>();
        if (mergeBlock != null) {
            stopBlocks.add(mergeBlock);
        }

        // Recover each case
        Map<IRBlock, List<Integer>> targetToCases = new LinkedHashMap<>();
        for (Map.Entry<Integer, IRBlock> entry : info.getSwitchCases().entrySet()) {
            targetToCases.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        for (Map.Entry<IRBlock, List<Integer>> entry : targetToCases.entrySet()) {
            IRBlock target = entry.getKey();
            List<Integer> labels = entry.getValue();

            List<Statement> caseStmts = recoverBlockSequence(target, stopBlocks);
            cases.add(SwitchCase.of(labels, caseStmts));
        }

        // Default case
        if (info.getDefaultTarget() != null) {
            List<Statement> defaultStmts = recoverBlockSequence(info.getDefaultTarget(), stopBlocks);
            cases.add(SwitchCase.defaultCase(defaultStmts));
        }

        return new SwitchStmt(selector, cases);
    }

    private Statement recoverIrreducible(IRBlock header, RegionInfo info) {
        // Fall back to IR region for irreducible control flow
        Set<IRBlock> blocks = new HashSet<>();
        collectReachableBlocks(header, blocks, new HashSet<>());
        return new IRRegionStmt(new ArrayList<>(blocks));
    }

    private Expression recoverCondition(IRBlock block, boolean negate) {
        IRInstruction terminator = block.getTerminator();
        if (terminator instanceof BranchInstruction branch) {
            Expression left = exprRecoverer.recoverOperand(branch.getLeft());
            CompareOp condition = branch.getCondition();

            if (branch.getRight() != null) {
                Expression right = exprRecoverer.recoverOperand(branch.getRight());
                com.tonic.analysis.source.ast.expr.BinaryOperator op =
                    OperatorMapper.mapCompareOp(condition);
                if (negate) {
                    op = negateOperator(op);
                }
                return new com.tonic.analysis.source.ast.expr.BinaryExpr(
                    op, left, right, com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN);
            }

            // Single-operand conditions (IFEQ, IFNE, IFNULL, IFNONNULL, etc.)
            if (OperatorMapper.isNullCheck(condition)) {
                // For null checks, compare to null explicitly
                Expression nullExpr = LiteralExpr.ofNull();
                com.tonic.analysis.source.ast.expr.BinaryOperator op =
                    OperatorMapper.mapCompareOp(condition);
                if (negate) {
                    op = negateOperator(op);
                }
                return new com.tonic.analysis.source.ast.expr.BinaryExpr(
                    op, left, nullExpr, com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN);
            }

            // Check if the expression is boolean-typed
            // If so, simplify: "x == 0" becomes "!x", "x != 0" becomes "x"
            // Also check the SSAValue's type directly as a fallback
            if (isBooleanExpression(left) || isBooleanSSAValue(branch.getLeft())) {
                // NE/IFNE to 0 means we want true (branch taken if value is non-zero/true)
                boolean wantTrue = (condition == CompareOp.NE || condition == CompareOp.IFNE);
                if (negate) {
                    wantTrue = !wantTrue;
                }
                if (wantTrue) {
                    return left; // expr != 0 -> expr
                } else {
                    // expr == 0 -> !expr
                    return new UnaryExpr(UnaryOperator.NOT, left,
                        com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN);
                }
            }

            // For zero comparisons (IFEQ, IFNE, etc.), compare to 0
            com.tonic.analysis.source.ast.expr.BinaryOperator op =
                OperatorMapper.mapCompareOp(condition);
            if (negate) {
                op = negateOperator(op);
            }
            Expression zero = LiteralExpr.ofInt(0);
            return new com.tonic.analysis.source.ast.expr.BinaryExpr(
                op, left, zero, com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN);
        }
        return LiteralExpr.ofBoolean(!negate);
    }

    /**
     * Checks if an expression is boolean-typed.
     */
    private boolean isBooleanExpression(Expression expr) {
        // Check expression type directly
        SourceType type = expr.getType();
        if (type == com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN) {
            return true;
        }

        // Check for method calls that return boolean
        if (expr instanceof MethodCallExpr mce) {
            String name = mce.getMethodName();
            // Common boolean methods
            if (name.startsWith("is") || name.startsWith("has") || name.startsWith("can") ||
                name.startsWith("should") || name.startsWith("was") ||
                "equals".equals(name) || "contains".equals(name) ||
                "startsWith".equals(name) || "endsWith".equals(name) ||
                "isEmpty".equals(name) || "isPresent".equals(name)) {
                return true;
            }
        }

        // Check for field accesses with boolean type
        if (expr instanceof FieldAccessExpr fae) {
            SourceType fieldType = fae.getType();
            if (fieldType == com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN) {
                return true;
            }
        }

        // Check for variable references to boolean parameters
        if (expr instanceof VarRefExpr vre) {
            String varName = vre.getName();
            // Check if this is a parameter reference (arg0, arg1, etc.)
            if (varName != null && varName.startsWith("arg")) {
                try {
                    int argIndex = Integer.parseInt(varName.substring(3));
                    if (isParameterBoolean(argIndex)) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // Not a valid arg index
                }
            }
        }

        return false;
    }

    /**
     * Checks if an SSAValue has boolean type directly from its IR type.
     * This is a fallback when the recovered expression type isn't detected as boolean.
     */
    private boolean isBooleanSSAValue(com.tonic.analysis.ssa.value.Value value) {
        if (value instanceof com.tonic.analysis.ssa.value.SSAValue ssaValue) {
            // Check if IR type is already BOOLEAN
            com.tonic.analysis.ssa.type.IRType type = ssaValue.getType();
            if (type == com.tonic.analysis.ssa.type.PrimitiveType.BOOLEAN) {
                return true;
            }
            // Check if defined by an instruction that produces boolean semantically
            // (e.g., instanceof uses INT in JVM but is semantically boolean)
            com.tonic.analysis.ssa.ir.IRInstruction def = ssaValue.getDefinition();
            if (def instanceof com.tonic.analysis.ssa.ir.InstanceOfInstruction) {
                return true;
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
                // Primitive type
                types.add(String.valueOf(c));
                index++;
            } else if (c == 'L') {
                // Object type - find the semicolon
                int semicolon = descriptor.indexOf(';', index);
                if (semicolon > index) {
                    types.add(descriptor.substring(index, semicolon + 1));
                    index = semicolon + 1;
                } else {
                    break;
                }
            } else if (c == '[') {
                // Array type - collect all dimensions and base type
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
                        // Primitive array
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

    private Expression recoverCondition(IRBlock block) {
        return recoverCondition(block, false);
    }

    private com.tonic.analysis.source.ast.expr.BinaryOperator negateOperator(
            com.tonic.analysis.source.ast.expr.BinaryOperator op) {
        return switch (op) {
            case EQ -> com.tonic.analysis.source.ast.expr.BinaryOperator.NE;
            case NE -> com.tonic.analysis.source.ast.expr.BinaryOperator.EQ;
            case LT -> com.tonic.analysis.source.ast.expr.BinaryOperator.GE;
            case GE -> com.tonic.analysis.source.ast.expr.BinaryOperator.LT;
            case GT -> com.tonic.analysis.source.ast.expr.BinaryOperator.LE;
            case LE -> com.tonic.analysis.source.ast.expr.BinaryOperator.GT;
            default -> op; // For non-relational operators, wrap in NOT
        };
    }

    private IRBlock getNextSequentialBlock(IRBlock block) {
        if (block.getSuccessors().size() == 1) {
            return block.getSuccessors().get(0);
        }
        return null;
    }

    private IRBlock findSwitchMerge(RegionInfo info) {
        // Find the common successor of all switch targets
        Set<IRBlock> allTargets = new HashSet<>(info.getSwitchCases().values());
        if (info.getDefaultTarget() != null) {
            allTargets.add(info.getDefaultTarget());
        }

        Set<IRBlock> commonSuccessors = null;
        for (IRBlock target : allTargets) {
            Set<IRBlock> reachable = new HashSet<>();
            collectReachableBlocks(target, reachable, allTargets);

            if (commonSuccessors == null) {
                commonSuccessors = reachable;
            } else {
                commonSuccessors.retainAll(reachable);
            }
        }

        if (commonSuccessors == null || commonSuccessors.isEmpty()) {
            return null;
        }

        return commonSuccessors.iterator().next();
    }

    private void collectReachableBlocks(IRBlock start, Set<IRBlock> result, Set<IRBlock> stopBlocks) {
        // Use iterative approach with worklist to avoid stack overflow on deep CFGs
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

        if (!(thenStmt instanceof ReturnStmt thenRet)) {
            return false;
        }
        if (!(elseStmt instanceof ReturnStmt elseRet)) {
            return false;
        }

        // Check if both return boolean literals (true/false or 0/1)
        return isBooleanLiteral(thenRet.getValue()) && isBooleanLiteral(elseRet.getValue());
    }

    /**
     * Checks if an expression is a boolean literal (true/false or integer 0/1).
     */
    private boolean isBooleanLiteral(Expression expr) {
        if (expr instanceof LiteralExpr lit) {
            Object val = lit.getValue();
            if (val instanceof Boolean) {
                return true;
            }
            if (val instanceof Integer i) {
                return i == 0 || i == 1;
            }
        }
        return false;
    }

    /**
     * Gets the boolean value from a literal expression.
     */
    private boolean getBooleanValue(Expression expr) {
        if (expr instanceof LiteralExpr lit) {
            Object val = lit.getValue();
            if (val instanceof Boolean b) {
                return b;
            }
            if (val instanceof Integer i) {
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
    private Statement collapseToBooleanReturn(Expression condition,
                                               List<Statement> thenStmts,
                                               List<Statement> elseStmts) {
        ReturnStmt thenRet = (ReturnStmt) thenStmts.get(0);
        boolean thenValue = getBooleanValue(thenRet.getValue());

        // If then returns true, the condition is the return value
        // If then returns false, the negated condition is the return value
        if (thenValue) {
            return new ReturnStmt(condition);
        } else {
            return new ReturnStmt(new UnaryExpr(UnaryOperator.NOT, condition,
                    com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN));
        }
    }

    /**
     * Checks for a PHI-based boolean return pattern.
     * This pattern occurs when both branches are empty/trivial (just assign constants)
     * and the merge block has a PHI of boolean values followed by return.
     */
    private boolean isBooleanPhiReturnPattern(List<Statement> thenStmts, List<Statement> elseStmts, IRBlock mergeBlock) {
        // Both branches should be empty or trivial (just variable declarations)
        if (thenStmts.size() > 1 || elseStmts.size() > 1) {
            return false;
        }

        if (mergeBlock == null) {
            return false;
        }

        // Merge block should have exactly one PHI and one return
        if (mergeBlock.getPhiInstructions().size() != 1) {
            return false;
        }

        List<IRInstruction> mergeInstrs = mergeBlock.getInstructions();
        if (mergeInstrs.size() != 1 || !(mergeInstrs.get(0) instanceof ReturnInstruction)) {
            return false;
        }

        // Check if PHI has boolean values (0/1 or true/false)
        // The operands may be SSAValues defined by ConstantInstructions
        PhiInstruction phi = mergeBlock.getPhiInstructions().get(0);
        for (com.tonic.analysis.ssa.value.Value val : phi.getOperands()) {
            Integer boolValue = extractBooleanConstant(val);
            if (boolValue == null) {
                return false;
            }
        }

        // Check if return uses the PHI result
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

        // Determine which value corresponds to "true" path
        // The PHI has values from different predecessors
        // We need to figure out if the "then" path contributes 0 or 1
        List<com.tonic.analysis.ssa.value.Value> operands = phi.getOperands();
        if (operands.size() != 2) {
            return null;
        }

        // Get the values - handle SSAValues defined by ConstantInstructions
        Integer val0 = extractBooleanConstant(operands.get(0));
        Integer val1 = extractBooleanConstant(operands.get(1));

        if (val0 == null || val1 == null) {
            return null;
        }

        // If both are the same, no boolean pattern
        if (val0 == val1) {
            return null;
        }

        // The condition leads to different branches - we need to figure out the polarity
        // For now, assume the first operand corresponds to the "true" branch of the condition
        // If first operand is 1 (true), return condition as-is
        // If first operand is 0 (false), negate the condition
        if (val0 == 1) {
            return new ReturnStmt(condition);
        } else {
            // Instead of wrapping in !(expr), try to invert the comparison operator
            // This transforms !(a != b) into a == b, which is cleaner
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
        if (condition instanceof com.tonic.analysis.source.ast.expr.BinaryExpr binExpr) {
            BinaryOperator op = binExpr.getOperator();
            BinaryOperator inverted = negateOperator(op);
            if (inverted != op) {
                return new com.tonic.analysis.source.ast.expr.BinaryExpr(
                    inverted, binExpr.getLeft(), binExpr.getRight(), binExpr.getType());
            }
        }
        // Fallback to NOT wrapper
        return new UnaryExpr(UnaryOperator.NOT, condition,
                com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Extracts a boolean constant value (0 or 1) from an IR Value.
     * Handles both direct IntConstants and SSAValues defined by ConstantInstructions.
     */
    private Integer extractBooleanConstant(com.tonic.analysis.ssa.value.Value val) {
        if (val instanceof com.tonic.analysis.ssa.value.IntConstant ic) {
            int v = ic.getValue();
            if (v == 0 || v == 1) {
                return v;
            }
            return null;
        }

        if (val instanceof com.tonic.analysis.ssa.value.SSAValue ssaVal) {
            // Check if this SSA value is defined by a constant instruction
            IRInstruction def = ssaVal.getDefinition();
            if (def instanceof ConstantInstruction constInstr) {
                com.tonic.analysis.ssa.value.Constant c = constInstr.getConstant();
                if (c instanceof com.tonic.analysis.ssa.value.IntConstant ic) {
                    int v = ic.getValue();
                    if (v == 0 || v == 1) {
                        return v;
                    }
                }
            }
        }

        return null;
    }

    // ========== OR/AND Condition Merging ==========

    /**
     * Checks if the then/else branches form an OR condition chain pattern.
     * Pattern: if(a) { body } else { if(b) { sameBody } ... }
     * This is how bytecode represents "if (a || b) { body }".
     */
    private boolean isOrConditionChain(List<Statement> thenStmts, List<Statement> elseStmts) {
        // The else branch must be a single IfStmt
        if (elseStmts.size() != 1) {
            return false;
        }
        if (!(elseStmts.get(0) instanceof IfStmt nestedIf)) {
            return false;
        }

        // Get the then branch of the nested if
        Statement nestedThenBranch = nestedIf.getThenBranch();
        List<Statement> nestedThen;
        if (nestedThenBranch instanceof BlockStmt nestedBlock) {
            nestedThen = nestedBlock.getStatements();
        } else {
            nestedThen = List.of(nestedThenBranch);
        }

        // Compare if the then bodies are structurally equivalent
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

        // Create merged condition: cond1 || cond2
        Expression merged = new BinaryExpr(BinaryOperator.OR, cond1, cond2,
                com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN);

        // Check if the nested else is also a chain (for a || b || c patterns)
        Statement nestedElseBranch = nestedIf.getElseBranch();
        List<Statement> finalElseStmts = null;

        if (nestedElseBranch != null) {
            List<Statement> nestedElse;
            if (nestedElseBranch instanceof BlockStmt nestedBlock) {
                nestedElse = nestedBlock.getStatements();
            } else {
                nestedElse = List.of(nestedElseBranch);
            }

            // Check if we can continue merging (a || b || c)
            if (isOrConditionChain(thenStmts, nestedElse)) {
                // Recursively merge
                Statement furtherMerged = mergeOrConditions(merged, thenStmts, nestedElse);
                return furtherMerged;
            }
            finalElseStmts = nestedElse;
        }

        // Create the final merged if statement
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

        // Compare by string representation for simplicity
        // This is a heuristic - for bytecode decompilation it's usually sufficient
        // because the same bytecode patterns produce the same AST structure
        return a.toString().equals(b.toString());
    }

    // ========== Boolean PHI Assignment Pattern ==========

    /**
     * Finds a PHI instruction that is assigned boolean constants (0/1) from the then/else branches.
     * Works at the IR level by examining the blocks directly rather than recovered statements.
     *
     * Pattern in IR:
     *   B_then: v1 = const 0; goto B_merge
     *   B_else: v2 = const 1; goto B_merge
     *   B_merge: PHI = phi [v1, B_then], [v2, B_else]
     *
     * This is how bytecode represents boolean expressions like !method() when used in larger expressions.
     *
     * @param header The header block containing the branch
     * @param info Region info with then/else/merge block information
     * @return The PHI instruction if this pattern is detected, null otherwise
     */
    private PhiInstruction findBooleanPhiAssignmentPatternIR(IRBlock header, RegionInfo info) {
        IRBlock thenBlock = info.getThenBlock();
        IRBlock elseBlock = info.getElseBlock();
        IRBlock mergeBlock = info.getMergeBlock();

        if (thenBlock == null || elseBlock == null || mergeBlock == null) {
            return null;
        }

        // Check if then block only contains a constant instruction (and goto)
        Integer thenConst = extractSingleBooleanConstant(thenBlock);
        Integer elseConst = extractSingleBooleanConstant(elseBlock);

        if (thenConst == null || elseConst == null) {
            return null;
        }

        // Values should be different (one 0, one 1)
        if (thenConst.equals(elseConst)) {
            return null;
        }

        // Find the PHI in the merge block that receives these constants
        for (PhiInstruction phi : mergeBlock.getPhiInstructions()) {
            if (phiReceivesBooleanConstants(phi, thenBlock, elseBlock)) {
                return phi;
            }
        }

        return null;
    }

    /**
     * Extracts a single boolean constant (0 or 1) from a block that only contains
     * a constant instruction and a goto terminator.
     */
    private Integer extractSingleBooleanConstant(IRBlock block) {
        List<IRInstruction> instructions = block.getInstructions();

        // Should have 1 or 2 instructions: constant, and optionally a goto
        if (instructions.isEmpty() || instructions.size() > 2) {
            return null;
        }

        // Find the constant instruction
        ConstantInstruction constInstr = null;
        for (IRInstruction instr : instructions) {
            if (instr instanceof ConstantInstruction ci) {
                constInstr = ci;
            } else if (instr instanceof GotoInstruction) {
                // OK, expected
            } else if (!instr.isTerminator()) {
                // Other non-terminator instruction - not a pure constant block
                return null;
            }
        }

        if (constInstr == null) {
            return null;
        }

        // Extract the boolean value from the constant
        return extractBooleanConstant(constInstr.getConstant());
    }

    /**
     * Checks if a PHI instruction receives boolean constants from the then/else blocks.
     */
    private boolean phiReceivesBooleanConstants(PhiInstruction phi, IRBlock thenBlock, IRBlock elseBlock) {
        List<com.tonic.analysis.ssa.value.Value> operands = phi.getOperands();
        Set<IRBlock> incomingBlocks = phi.getIncomingBlocks();

        if (operands.size() != 2 || incomingBlocks.size() != 2) {
            return false;
        }

        // Check that the operands come from the then/else blocks
        boolean hasThen = incomingBlocks.contains(thenBlock);
        boolean hasElse = incomingBlocks.contains(elseBlock);

        if (!hasThen || !hasElse) {
            return false;
        }

        // Check that both operands are boolean constants
        for (com.tonic.analysis.ssa.value.Value val : operands) {
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
        List<com.tonic.analysis.ssa.value.Value> operands = phi.getOperands();
        Set<IRBlock> incomingBlocks = phi.getIncomingBlocks();

        // PHI operands and incoming blocks are correlated by index
        // Convert set to list for indexed access (order matches operand order)
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
        // Determine which branch contributes 1 (true) to know if we need negation
        int thenVal = getThenBlockBooleanValue(phi, thenBlock);

        Expression booleanExpr;
        if (thenVal == 1) {
            // Then contributes 1 (true), so the expression is just the condition
            booleanExpr = condition;
        } else {
            // Then contributes 0 (false), else contributes 1 (true), so the expression is !condition
            booleanExpr = invertCondition(condition);
        }

        // Cache this expression for the PHI result so it gets inlined at usage sites
        SSAValue phiResult = phi.getResult();
        if (phiResult != null) {
            context.getExpressionContext().cacheExpression(phiResult, booleanExpr);
            // Un-mark as materialized so it gets inlined rather than referenced as a variable
            // The PHI was declared at method start, but we want to inline the boolean expression
            context.getExpressionContext().unmarkMaterialized(phiResult);
        }
    }
}
