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

        // Pre-declare parameters so stores to them become assignments, not declarations
        preDeclareParameters();
    }

    /**
     * Pre-declares parameter names so that stores to parameter slots
     * generate assignment statements instead of variable declarations.
     */
    private void preDeclareParameters() {
        IRMethod method = context.getIrMethod();
        String descriptor = method.getDescriptor();
        if (descriptor == null) {
            return;
        }

        List<String> paramTypes = parseParameterTypes(descriptor);
        int slot = method.isStatic() ? 0 : 1; // Skip 'this' for instance methods

        for (int i = 0; i < paramTypes.size(); i++) {
            String paramName = "arg" + i;
            context.getExpressionContext().markDeclared(paramName);

            String paramType = paramTypes.get(i);
            slot++;
            // Long and double take 2 slots
            if ("J".equals(paramType) || "D".equals(paramType)) {
                slot++;
            }
        }
    }

    private final Set<SSAValue> ternaryPhiValues = new HashSet<>();

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

        collectTernaryPhis(method);

        emitPhiDeclarations(method, statements);

        List<ExceptionHandler> handlers = method.getExceptionHandlers();
        if (handlers != null && !handlers.isEmpty()) {
            statements.addAll(recoverWithExceptionHandling(entry, handlers));
        } else {
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
                if (h.getHandlerBlock() == outerHandler.getHandlerBlock()) {
                    outerHandlers.add(h);
                } else {
                    innerHandlers.add(h);
                }
            }

            Set<IRBlock> stopBlocks = new HashSet<>(handlerBlocks);
            List<Statement> tryStmts;
            if (!innerHandlers.isEmpty()) {
                tryStmts = recoverWithNestedHandlers(entry, innerHandlers, stopBlocks);
            } else {
                tryStmts = recoverBlockSequence(entry, stopBlocks);
            }
            BlockStmt tryBlock = new BlockStmt(tryStmts);

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

            if (!catchClauses.isEmpty()) {
                TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, catchClauses);
                result.add(tryCatch);
            } else {
                result.addAll(tryStmts);
            }
        } else {
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

    /**
     * Finds the outermost exception handler (the one covering the most code from entry).
     */
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

                for (ExceptionHandler h : innerHandlers) {
                    if (h.getTryStart() == innerHandler.getTryStart()) {
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
                    result.add(new TryCatchStmt(tryBlock, catchClauses));
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

        SourceType exceptionType;
        if (handler.isCatchAll()) {
            exceptionType = new ReferenceSourceType("java/lang/Throwable", Collections.emptyList());
        } else {
            String typeName = handler.getCatchType().getInternalName();
            exceptionType = new ReferenceSourceType(typeName, Collections.emptyList());
        }

        String exceptionVarName = findExceptionVariableName(handlerBlock);
        if (exceptionVarName == null) {
            String simpleName;
            if (exceptionType instanceof ReferenceSourceType) {
                ReferenceSourceType refType = (ReferenceSourceType) exceptionType;
                simpleName = refType.getSimpleName().toLowerCase();
            } else {
                simpleName = "ex";
            }
            exceptionVarName = simpleName.substring(0, 1) + "_ex";
        }

        registerExceptionVariables(handlerBlock, exceptionVarName);

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
        if (!(handlerTerminator instanceof GotoInstruction)) {
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
     * Recursively recovers all blocks reachable from the handler until we hit a throw, return, or goto.
     * IMPORTANT: A GotoInstruction in a catch handler typically means "exit the catch and continue",
     * so we should NOT follow goto targets - they are the merge point, not part of the catch body.
     */
    private void recoverHandlerBlocks(List<IRBlock> successors, Set<IRBlock> visited, List<Statement> stmts) {
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
            if (terminator instanceof ThrowInstruction) {
                ThrowInstruction throwInstr = (ThrowInstruction) terminator;
                Statement throwStmt = recoverThrow(throwInstr);
                if (throwStmt != null) {
                    stmts.add(throwStmt);
                }
                continue;
            }

            if (terminator instanceof ReturnInstruction) {
                continue;
            }

            if (terminator instanceof GotoInstruction) {
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
    private void registerExceptionVariables(IRBlock handlerBlock, String exceptionVarName) {
        Set<SSAValue> exceptionValuesSet = new HashSet<>();
        findExceptionSSAValues(handlerBlock, exceptionValuesSet, new HashSet<>());

        for (SSAValue excVal : exceptionValuesSet) {
            context.getExpressionContext().setVariableName(excVal, exceptionVarName);
        }
    }

    /**
     * Finds all SSA values that represent the caught exception.
     * Starts from values with "exc_" prefix and tracks through copies and local stores/loads.
     */
    private void findExceptionSSAValues(IRBlock block, Set<SSAValue> result, Set<IRBlock> visited) {
        Set<Integer> exceptionLocalSlots = new HashSet<>();

        findExceptionSSAValuesPass1(block, result, exceptionLocalSlots, new HashSet<>());

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
            if (instr instanceof LoadLocalInstruction) {
                LoadLocalInstruction load = (LoadLocalInstruction) instr;
                if (exceptionSlots.contains(load.getLocalIndex())) {
                    SSAValue loadResult = load.getResult();
                    if (loadResult != null) {
                        result.add(loadResult);
                    }
                }
            }
        }

        for (IRBlock succ : block.getSuccessors()) {
            findExceptionSSAValuesPass2(succ, result, exceptionSlots, visited);
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

        public IRBlock start() {
            return start;
        }

        public IRBlock end() {
            return end;
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
     * Finds an exception handler whose try region starts at the given block.
     */
    private ExceptionHandler findHandlerStartingAt(IRBlock block) {
        IRMethod irMethod = context.getIrMethod();
        List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();
        if (handlers == null || handlers.isEmpty()) {
            return null;
        }
        for (ExceptionHandler handler : handlers) {
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
        IRMethod irMethod = context.getIrMethod();
        List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();
        List<ExceptionHandler> sameRegionHandlers = new ArrayList<>();
        for (ExceptionHandler h : handlers) {
            if (h.getTryStart() == mainHandler.getTryStart()) {
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
                if (block.getBytecodeOffset() > tryEndOffset) {
                    tryStopBlocks.add(block);
                }
            }
        }

        List<Statement> tryStmts = recoverBlocksForTry(startBlock, tryStopBlocks, visited);
        BlockStmt tryBlock = new BlockStmt(tryStmts);

        List<CatchClause> catchClauses = new ArrayList<>();
        for (ExceptionHandler h : sameRegionHandlers) {
            CatchClause clause = recoverCatchClause(h);
            if (clause != null) {
                catchClauses.add(clause);
            }
        }

        if (catchClauses.isEmpty()) {
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
                    current = info.getMergeBlock();
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

        return result;
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

        Set<IRBlock> handlerBlocks = collectExceptionHandlerBlocks(method);

        boolean isStatic = method.isStatic();

        Map<String, List<SourceType>> localSlotTypes = new HashMap<>();

        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof LoadLocalInstruction) {
                    LoadLocalInstruction loadLocal = (LoadLocalInstruction) instr;
                    if (loadLocal.getResult() != null) {
                        int localIndex = loadLocal.getLocalIndex();
                        String localName = getNameForLocalSlot(localIndex);
                        context.getExpressionContext().setVariableName(loadLocal.getResult(), localName);
                        // Mark as materialized so recoverOperand returns a VarRefExpr instead of
                        // re-recovering the instruction (which would create duplicate new expressions)
                        context.getExpressionContext().markMaterialized(loadLocal.getResult());
                    }
                } else if (instr instanceof StoreLocalInstruction) {
                    StoreLocalInstruction storeLocal = (StoreLocalInstruction) instr;
                    int localIndex = storeLocal.getLocalIndex();
                    String localName = getNameForLocalSlot(localIndex);

                    Value storedValue = storeLocal.getValue();
                    SourceType storedType = typeRecoverer.recoverType(storedValue);
                    if (storedType != null && !storedType.isVoid()) {
                        localSlotTypes.computeIfAbsent(localName, k -> new ArrayList<>()).add(storedType);
                    }

                    // Mark the stored value as materialized with the local variable name.
                    // This is needed because RedundantCopyElimination replaces load_local results
                    // with the originally stored value. So when putfield uses what was originally
                    // a load_local result, it now directly references the stored value.
                    // Example: store_local 1, v14; v15 = load_local 1; putfield v15.fill
                    // After optimization: store_local 1, v14; putfield v14.fill
                    // Without this fix, v14 wouldn't have the variable name, causing:
                    // "new GridBagConstraints().fill = 2" instead of "c.fill = 2"
                    if (storedValue instanceof SSAValue) {
                        SSAValue sourceValue = (SSAValue) storedValue;
                        // Only preserve parameter names like "arg0", "this" - overwrite synthetic names
                        // like "v14" (default SSA name), "g8" (from NameRecoverer), etc.
                        String existingName = context.getExpressionContext().getVariableName(sourceValue);
                        boolean shouldOverwrite = existingName == null
                                || existingName.startsWith("v")
                                || existingName.matches("[a-z]\\d+");  // synthetic names like "g8", "f2", etc.
                        if (shouldOverwrite) {
                            context.getExpressionContext().setVariableName(sourceValue, localName);
                            context.getExpressionContext().markMaterialized(sourceValue);
                        }
                    }
                }
            }
        }

        localSlotUnifiedTypes.clear();
        for (Map.Entry<String, List<SourceType>> entry : localSlotTypes.entrySet()) {
            String slotName = entry.getKey();
            List<SourceType> types = entry.getValue();
            if (!types.isEmpty()) {
                SourceType unifiedType = typeRecoverer.computeCommonType(types);
                localSlotUnifiedTypes.put(slotName, unifiedType);
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

        if ("this".equals(name) || name.startsWith("arg")) {
            return;
        }

        if (declaredNames.contains(name) || context.getExpressionContext().isDeclared(name)) {
            return;
        }

        if (ternaryPhiValues.contains(result)) {
            return;
        }

        SourceType type = computePhiUnifiedType(phi);

        declaredNames.add(name);
        context.getExpressionContext().markDeclared(name);
        context.getExpressionContext().markMaterialized(result);
        context.getExpressionContext().setVariableName(result, name);

        Expression initValue = getDefaultValue(type);
        statements.add(new VarDeclStmt(type, name, initValue));
    }

    /**
     * Computes a unified type for a phi instruction by examining all incoming values.
     * For incompatible types (e.g., Insets and Graphics), returns their common supertype (Object).
     */
    private SourceType computePhiUnifiedType(PhiInstruction phi) {
        SSAValue result = phi.getResult();

        String localName = context.getExpressionContext().getVariableName(result);
        if (localName != null && localSlotUnifiedTypes.containsKey(localName)) {
            return localSlotUnifiedTypes.get(localName);
        }

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
        if (inProgress.contains(value)) return;

        inProgress.add(value);

        IRInstruction def = value.getDefinition();
        if (def != null) {
            for (com.tonic.analysis.ssa.value.Value operand : def.getOperands()) {
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

        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.isEmpty()) return true;

        for (IRInstruction use : uses) {
            if (use instanceof InvokeInstruction) continue;
            if (use instanceof ThrowInstruction) continue;
            if (use instanceof ReturnInstruction) continue;
            if (use instanceof BinaryOpInstruction) continue;
            if (use instanceof UnaryOpInstruction) continue;
            if (use instanceof CastInstruction) continue;
            if (use instanceof ArrayLengthInstruction) continue;
            if (use instanceof ArrayLoadInstruction) continue;
            if (use instanceof GetFieldInstruction) continue;
            if (use instanceof InstanceOfInstruction) continue;
            if (use instanceof NewArrayInstruction) continue;
            if (use instanceof BranchInstruction) return false;
            if (use instanceof PutFieldInstruction) return false;
            if (use instanceof ArrayStoreInstruction) return false;
            if (use instanceof StoreLocalInstruction) return false;
            if (use instanceof PhiInstruction) return false;
        }
        return true;
    }

    /**
     * Checks if an SSA value is used exactly once and that use is a PutFieldInstruction.
     * Such values can be safely inlined into the field assignment.
     */
    private boolean isSingleUsePutField(SSAValue value) {
        if (value == null) return false;
        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.size() != 1) return false;
        return uses.get(0) instanceof PutFieldInstruction;
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
            if (use instanceof PhiInstruction) {
                PhiInstruction phi = (PhiInstruction) use;
                return phi;
            }
        }
        return null;
    }

    /**
     * Gets a default value for the given type.
     */
    private Expression getDefaultValue(SourceType type) {
        if (type instanceof com.tonic.analysis.source.ast.type.PrimitiveSourceType) {
            com.tonic.analysis.source.ast.type.PrimitiveSourceType pst = (com.tonic.analysis.source.ast.type.PrimitiveSourceType) type;
            if (pst == com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN) {
                return LiteralExpr.ofBoolean(false);
            } else if (pst == com.tonic.analysis.source.ast.type.PrimitiveSourceType.LONG) {
                return LiteralExpr.ofLong(0L);
            } else if (pst == com.tonic.analysis.source.ast.type.PrimitiveSourceType.FLOAT) {
                return LiteralExpr.ofFloat(0.0f);
            } else if (pst == com.tonic.analysis.source.ast.type.PrimitiveSourceType.DOUBLE) {
                return LiteralExpr.ofDouble(0.0);
            } else {
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
            ExceptionHandler tryHandler = findHandlerStartingAt(current);
            if (tryHandler != null && !processedTryHandlers.contains(tryHandler)) {
                processedTryHandlers.add(tryHandler);
                Set<IRBlock> tryVisited = new HashSet<>(visited);
                TryCatchStmt tryCatch = recoverTryCatch(current, tryHandler, stopBlocks, tryVisited);
                if (tryCatch != null) {
                    result.add(tryCatch);
                    visited.addAll(tryVisited);
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
                    IRBlock merge = info.getMergeBlock();
                    if (merge != null) {
                        current = merge;
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
                    }
                    IRBlock merge = info.getMergeBlock();
                    if (merge != null) {
                        current = merge;
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
                    result.add(recoverSwitch(current, info));
                    current = findSwitchMerge(info);
                    break;
                }
                case IRREDUCIBLE: {
                    result.add(recoverIrreducible(current, info));
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

    private List<Statement> recoverSimpleBlock(IRBlock block) {
        List<Statement> statements = new ArrayList<>();

        for (PhiInstruction phi : block.getPhiInstructions()) {
            Statement stmt = recoverPhiAssignment(phi);
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        for (IRInstruction instr : block.getInstructions()) {
            Statement stmt = recoverInstruction(instr);
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        return statements;
    }

    private Statement recoverPhiAssignment(PhiInstruction phi) {
        return null;
    }

    private Statement recoverInstruction(IRInstruction instr) {
        if (instr.isTerminator()) {
            return recoverTerminator(instr);
        }

        if (instr instanceof StoreLocalInstruction) {
            StoreLocalInstruction store = (StoreLocalInstruction) instr;
            return recoverStoreLocal(store);
        }

        if (instr instanceof PutFieldInstruction) {
            PutFieldInstruction putField = (PutFieldInstruction) instr;
            return recoverPutField(putField);
        }

        if (instr instanceof ArrayStoreInstruction) {
            ArrayStoreInstruction arrayStore = (ArrayStoreInstruction) instr;
            return recoverArrayStore(arrayStore);
        }

        if (instr instanceof InvokeInstruction) {
            InvokeInstruction invoke = (InvokeInstruction) instr;
            if ("<init>".equals(invoke.getName())) {
                Expression expr = exprRecoverer.recover(invoke);
                if (expr instanceof MethodCallExpr) {
                    MethodCallExpr mce = (MethodCallExpr) expr;
                    String methodName = mce.getMethodName();
                    if ("super".equals(methodName) || "this".equals(methodName)) {
                        return new ExprStmt(expr);
                    }
                }
                if (expr instanceof NewExpr) {
                    return null;
                }
                return new ExprStmt(expr);
            }
            if (invoke.getResult() == null || invoke.getResult().getType() == null) {
                Expression expr = exprRecoverer.recover(invoke);
                return new ExprStmt(expr);
            }
            SSAValue result = invoke.getResult();
            if (result != null && isUsedByStoreLocal(result)) {
                Expression expr = exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
            if (result != null && result.getUses().isEmpty()) {
                Expression expr = exprRecoverer.recover(invoke);
                return new ExprStmt(expr);
            }
            if (result != null && isIntermediateValue(result)) {
                exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, exprRecoverer.recover(invoke));
                return null;
            }
            if (result != null && isSingleUsePutField(result)) {
                Expression expr = exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
        }

        if (instr instanceof NewInstruction) {
            if (instr.getResult() != null) {
                exprRecoverer.recover(instr);
            }
            return null;
        }

        if (instr instanceof MonitorEnterInstruction || instr instanceof MonitorExitInstruction) {
            return null;
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

        if (instr instanceof GetFieldInstruction) {
            if (instr.getResult() != null) {
                Expression value = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), value);
            }
            return null;
        }

        if (instr instanceof BinaryOpInstruction || instr instanceof UnaryOpInstruction || instr instanceof CastInstruction) {
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
            return recoverVarDecl(instr);
        }

        return null;
    }

    private Statement recoverTerminator(IRInstruction instr) {
        if (instr instanceof ReturnInstruction) {
            ReturnInstruction ret = (ReturnInstruction) instr;
            return recoverReturn(ret);
        }
        if (instr instanceof ThrowInstruction) {
            ThrowInstruction throwInstr = (ThrowInstruction) instr;
            return recoverThrow(throwInstr);
        }
        return null;
    }

    private Statement recoverStoreLocal(StoreLocalInstruction store) {
        // When recovering the initialization value for a variable declaration,
        // we need to recover the actual expression (e.g., "new GridBagConstraints()"),
        // not a variable reference (e.g., "local1"). So we temporarily un-materialize
        // the value during recovery, then re-materialize it after.
        Value storeValue = store.getValue();
        boolean wasMaterialized = false;
        if (storeValue instanceof SSAValue) {
            SSAValue ssaValue = (SSAValue) storeValue;
            wasMaterialized = context.getExpressionContext().isMaterialized(ssaValue);
            if (wasMaterialized) {
                context.getExpressionContext().unmarkMaterialized(ssaValue);
            }
        }

        Expression value = exprRecoverer.recoverOperand(storeValue);

        // Re-materialize after recovery
        if (wasMaterialized && storeValue instanceof SSAValue) {
            context.getExpressionContext().markMaterialized((SSAValue) storeValue);
        }

        int localIndex = store.getLocalIndex();

        // Use proper name for parameter slots vs local variable slots
        String name = getNameForLocalSlot(localIndex);

        SourceType type = getLocalSlotUnifiedType(name);
        if (type == null) {
            type = value.getType();
        }
        if (type == null) {
            type = com.tonic.analysis.source.ast.type.VoidSourceType.INSTANCE;
        }

        // If the source value is an SSA value, mark it as materialized with the local's name.
        // This ensures that if the SSA value is used directly elsewhere (without going through
        // a load_local), it will resolve to the local variable instead of being re-evaluated.
        // For example: v3 = newarray; store_local 2, v3; v3[0] = 1; return v3
        // Without this, v3 would be inlined as "new int[...]" everywhere, which is incorrect.
        if (store.getValue() instanceof SSAValue) {
            SSAValue sourceValue = (SSAValue) store.getValue();
            // Only do this if the source value doesn't already have a different name
            // (e.g., it's not a parameter like "arg0" being stored to a different local)
            String existingName = context.getExpressionContext().getVariableName(sourceValue);
            if (existingName == null || existingName.startsWith("v")) {
                context.getExpressionContext().setVariableName(sourceValue, name);
                context.getExpressionContext().markMaterialized(sourceValue);
            }
        }

        if (context.getExpressionContext().isDeclared(name)) {
            VarRefExpr target = new VarRefExpr(name, type, null);
            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
        }

        context.getExpressionContext().markDeclared(name);
        return new VarDeclStmt(type, name, value);
    }

    private Statement recoverPutField(PutFieldInstruction putField) {
        Expression receiver = putField.isStatic() ? null : exprRecoverer.recoverOperand(putField.getObjectRef());

        SourceType fieldType = typeRecoverer.recoverType(putField.getDescriptor());
        Expression value = exprRecoverer.recoverOperand(putField.getValue(), fieldType);

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

    private Statement recoverIfThen(IRBlock header, RegionInfo info) {
        context.markProcessed(header);

        List<Statement> headerStmts = recoverBlockInstructions(header);

        Expression condition = recoverCondition(header, info.isConditionNegated());
        Set<IRBlock> stopBlocks = new HashSet<>(context.getAllStopBlocks());
        if (info.getMergeBlock() != null) {
            stopBlocks.add(info.getMergeBlock());
        }

        List<Statement> thenStmts = recoverBlockSequence(info.getThenBlock(), stopBlocks);
        BlockStmt thenBlock = new BlockStmt(thenStmts);

        IfStmt ifStmt = new IfStmt(condition, thenBlock, null);

        if (!headerStmts.isEmpty()) {
            context.addPendingStatements(headerStmts);
        }
        return ifStmt;
    }

    private Statement recoverIfThenElse(IRBlock header, RegionInfo info) {
        context.markProcessed(header);

        List<Statement> headerStmts = recoverBlockInstructions(header);


        Expression condition = recoverCondition(header, info.isConditionNegated());
        Set<IRBlock> stopBlocks = new HashSet<>(context.getAllStopBlocks());
        if (info.getMergeBlock() != null) {
            stopBlocks.add(info.getMergeBlock());
        }

        List<Statement> thenStmts = recoverBlockSequence(info.getThenBlock(), stopBlocks);
        List<Statement> elseStmts = recoverBlockSequence(info.getElseBlock(), stopBlocks);

        if (isBooleanReturnPattern(thenStmts, elseStmts)) {
            Statement booleanReturn = collapseToBooleanReturn(condition, thenStmts, elseStmts);
            if (!headerStmts.isEmpty()) {
                context.addPendingStatements(headerStmts);
            }
            return booleanReturn;
        }

        if (isOrConditionChain(thenStmts, elseStmts)) {
            Statement mergedIf = mergeOrConditions(condition, thenStmts, elseStmts);
            if (!headerStmts.isEmpty()) {
                context.addPendingStatements(headerStmts);
            }
            return mergedIf;
        }

        if (isBooleanPhiReturnPattern(thenStmts, elseStmts, info.getMergeBlock())) {
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

        PhiInstruction booleanPhi = findBooleanPhiAssignmentPatternIR(header, info);
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

        PhiInstruction ternaryPhi = findTernaryPhiPattern(header, info);
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

        for (PhiInstruction phi : block.getPhiInstructions()) {
            Statement stmt = recoverPhiAssignment(phi);
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        for (IRInstruction instr : block.getInstructions()) {
            if (instr.isTerminator()) {
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

        Expression condition = recoverCondition(header, info.isConditionNegated());

        Set<IRBlock> stopBlocks = new HashSet<>();
        stopBlocks.add(header);
        if (info.getLoopExit() != null) {
            stopBlocks.add(info.getLoopExit());
        } else if (info.getLoop() != null) {
            // When no explicit exit block, find blocks outside the loop
            // that are reachable from within the loop
            Set<IRBlock> loopBlocks = info.getLoop().getBlocks();
            for (IRBlock loopBlock : loopBlocks) {
                for (IRBlock succ : loopBlock.getSuccessors()) {
                    if (!loopBlocks.contains(succ)) {
                        stopBlocks.add(succ);
                    }
                }
            }
        }

        // Push stop blocks so inner control structures respect loop exits
        context.pushStopBlocks(stopBlocks);
        try {
            List<Statement> bodyStmts = recoverBlockSequence(info.getLoopBody(), stopBlocks);
            BlockStmt body = new BlockStmt(bodyStmts);
            return new WhileStmt(condition, body);
        } finally {
            context.popStopBlocks();
        }
    }

    private Statement recoverDoWhileLoop(IRBlock header, RegionInfo info) {
        context.markProcessed(header);

        Set<IRBlock> stopBlocks = new HashSet<>();
        stopBlocks.add(header);
        if (info.getLoopExit() != null) {
            stopBlocks.add(info.getLoopExit());
        } else if (info.getLoop() != null) {
            // When no explicit exit block, find blocks outside the loop
            Set<IRBlock> loopBlocks = info.getLoop().getBlocks();
            for (IRBlock loopBlock : loopBlocks) {
                for (IRBlock succ : loopBlock.getSuccessors()) {
                    if (!loopBlocks.contains(succ)) {
                        stopBlocks.add(succ);
                    }
                }
            }
        }

        // Push stop blocks so inner control structures respect loop exits
        context.pushStopBlocks(stopBlocks);
        try {
            List<Statement> bodyStmts = recoverBlockSequence(info.getLoopBody(), stopBlocks);
            BlockStmt body = new BlockStmt(bodyStmts);
            Expression condition = recoverCondition(header, info.isConditionNegated());
            return new DoWhileStmt(body, condition);
        } finally {
            context.popStopBlocks();
        }
    }

    private Statement recoverForLoop(IRBlock header, RegionInfo info) {
        return recoverWhileLoop(header, info);
    }

    private Statement recoverSwitch(IRBlock header, RegionInfo info) {
        context.markProcessed(header);

        IRInstruction terminator = header.getTerminator();
        if (!(terminator instanceof SwitchInstruction)) {
            return new IRRegionStmt(List.of(header));
        }

        SwitchInstruction sw = (SwitchInstruction) terminator;

        Expression selector = exprRecoverer.recoverOperand(sw.getKey());
        List<SwitchCase> cases = new ArrayList<>();

        IRBlock mergeBlock = findSwitchMerge(info);
        Set<IRBlock> stopBlocks = new HashSet<>();
        if (mergeBlock != null) {
            stopBlocks.add(mergeBlock);
        }

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

        if (info.getDefaultTarget() != null) {
            List<Statement> defaultStmts = recoverBlockSequence(info.getDefaultTarget(), stopBlocks);
            cases.add(SwitchCase.defaultCase(defaultStmts));
        }

        return new SwitchStmt(selector, cases);
    }

    private Statement recoverIrreducible(IRBlock header, RegionInfo info) {
        Set<IRBlock> blocks = new HashSet<>();
        collectReachableBlocks(header, blocks, new HashSet<>());
        return new IRRegionStmt(new ArrayList<>(blocks));
    }

    private Expression recoverCondition(IRBlock block, boolean negate) {
        IRInstruction terminator = block.getTerminator();
        if (terminator instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) terminator;
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

            if (OperatorMapper.isNullCheck(condition)) {
                Expression nullExpr = LiteralExpr.ofNull();
                com.tonic.analysis.source.ast.expr.BinaryOperator op =
                    OperatorMapper.mapCompareOp(condition);
                if (negate) {
                    op = negateOperator(op);
                }
                return new com.tonic.analysis.source.ast.expr.BinaryExpr(
                    op, left, nullExpr, com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN);
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
                        com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN);
                }
            }

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
        SourceType type = expr.getType();
        if (type == com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN) {
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
            if (fieldType == com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN) {
                return true;
            }
        }

        if (expr instanceof VarRefExpr) {
            VarRefExpr vre = (VarRefExpr) expr;
            String varName = vre.getName();
            if (varName != null && varName.startsWith("arg")) {
                try {
                    int argIndex = Integer.parseInt(varName.substring(3));
                    if (isParameterBoolean(argIndex)) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
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
        if (value instanceof com.tonic.analysis.ssa.value.SSAValue) {
            com.tonic.analysis.ssa.value.SSAValue ssaValue = (com.tonic.analysis.ssa.value.SSAValue) value;
            com.tonic.analysis.ssa.type.IRType type = ssaValue.getType();
            if (type == com.tonic.analysis.ssa.type.PrimitiveType.BOOLEAN) {
                return true;
            }
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

    private Expression recoverCondition(IRBlock block) {
        return recoverCondition(block, false);
    }

    private com.tonic.analysis.source.ast.expr.BinaryOperator negateOperator(
            com.tonic.analysis.source.ast.expr.BinaryOperator op) {
        if (op == com.tonic.analysis.source.ast.expr.BinaryOperator.EQ) {
            return com.tonic.analysis.source.ast.expr.BinaryOperator.NE;
        } else if (op == com.tonic.analysis.source.ast.expr.BinaryOperator.NE) {
            return com.tonic.analysis.source.ast.expr.BinaryOperator.EQ;
        } else if (op == com.tonic.analysis.source.ast.expr.BinaryOperator.LT) {
            return com.tonic.analysis.source.ast.expr.BinaryOperator.GE;
        } else if (op == com.tonic.analysis.source.ast.expr.BinaryOperator.GE) {
            return com.tonic.analysis.source.ast.expr.BinaryOperator.LT;
        } else if (op == com.tonic.analysis.source.ast.expr.BinaryOperator.GT) {
            return com.tonic.analysis.source.ast.expr.BinaryOperator.LE;
        } else if (op == com.tonic.analysis.source.ast.expr.BinaryOperator.LE) {
            return com.tonic.analysis.source.ast.expr.BinaryOperator.GT;
        } else {
            return op;
        }
    }

    private IRBlock getNextSequentialBlock(IRBlock block) {
        if (block.getSuccessors().size() == 1) {
            return block.getSuccessors().get(0);
        }
        return null;
    }

    /**
     * Finds the exit block for a loop, handling cases where the exit block
     * is not directly set (when both loop header successors are in the loop).
     */
    private IRBlock findLoopExit(RegionInfo info, Set<IRBlock> visited, Set<IRBlock> stopBlocks) {
        // If there's an explicit exit, use it
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
                Integer i = (Integer) val;
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
                Boolean b = (Boolean) val;
                return b;
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
    private Statement collapseToBooleanReturn(Expression condition,
                                               List<Statement> thenStmts,
                                               List<Statement> elseStmts) {
        ReturnStmt thenRet = (ReturnStmt) thenStmts.get(0);
        boolean thenValue = getBooleanValue(thenRet.getValue());

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
        for (com.tonic.analysis.ssa.value.Value val : phi.getOperands()) {
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

        List<com.tonic.analysis.ssa.value.Value> operands = phi.getOperands();
        if (operands.size() != 2) {
            return null;
        }

        Integer val0 = extractBooleanConstant(operands.get(0));
        Integer val1 = extractBooleanConstant(operands.get(1));

        if (val0 == null || val1 == null) {
            return null;
        }

        if (val0 == val1) {
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
        if (condition instanceof com.tonic.analysis.source.ast.expr.BinaryExpr) {
            com.tonic.analysis.source.ast.expr.BinaryExpr binExpr = (com.tonic.analysis.source.ast.expr.BinaryExpr) condition;
            BinaryOperator op = binExpr.getOperator();
            BinaryOperator inverted = negateOperator(op);
            if (inverted != op) {
                return new com.tonic.analysis.source.ast.expr.BinaryExpr(
                    inverted, binExpr.getLeft(), binExpr.getRight(), binExpr.getType());
            }
        }
        return new UnaryExpr(UnaryOperator.NOT, condition,
                com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Extracts a boolean constant value (0 or 1) from an IR Value.
     * Handles both direct IntConstants and SSAValues defined by ConstantInstructions.
     */
    private Integer extractBooleanConstant(com.tonic.analysis.ssa.value.Value val) {
        if (val instanceof com.tonic.analysis.ssa.value.IntConstant) {
            com.tonic.analysis.ssa.value.IntConstant ic = (com.tonic.analysis.ssa.value.IntConstant) val;
            int v = ic.getValue();
            if (v == 0 || v == 1) {
                return v;
            }
            return null;
        }

        if (val instanceof com.tonic.analysis.ssa.value.SSAValue) {
            com.tonic.analysis.ssa.value.SSAValue ssaVal = (com.tonic.analysis.ssa.value.SSAValue) val;
            IRInstruction def = ssaVal.getDefinition();
            if (def instanceof ConstantInstruction) {
                ConstantInstruction constInstr = (ConstantInstruction) def;
                com.tonic.analysis.ssa.value.Constant c = constInstr.getConstant();
                if (c instanceof com.tonic.analysis.ssa.value.IntConstant) {
                    com.tonic.analysis.ssa.value.IntConstant ic = (com.tonic.analysis.ssa.value.IntConstant) c;
                    int v = ic.getValue();
                    if (v == 0 || v == 1) {
                        return v;
                    }
                }
            }
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
                com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN);

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
                Statement furtherMerged = mergeOrConditions(merged, thenStmts, nestedElse);
                return furtherMerged;
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

        Integer thenConst = extractSingleBooleanConstant(thenBlock);
        Integer elseConst = extractSingleBooleanConstant(elseBlock);

        if (thenConst == null || elseConst == null) {
            return null;
        }

        if (thenConst.equals(elseConst)) {
            return null;
        }

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

        if (instructions.isEmpty() || instructions.size() > 2) {
            return null;
        }

        ConstantInstruction constInstr = null;
        for (IRInstruction instr : instructions) {
            if (instr instanceof ConstantInstruction) {
                ConstantInstruction ci = (ConstantInstruction) instr;
                constInstr = ci;
            } else if (instr instanceof GotoInstruction) {
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
        List<com.tonic.analysis.ssa.value.Value> operands = phi.getOperands();
        Set<IRBlock> incomingBlocks = phi.getIncomingBlocks();

        if (operands.size() != 2 || incomingBlocks.size() != 2) {
            return false;
        }

        boolean hasThen = incomingBlocks.contains(thenBlock);
        boolean hasElse = incomingBlocks.contains(elseBlock);

        if (!hasThen || !hasElse) {
            return false;
        }

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
     * Pre-pass to collect PHI values that will be collapsed into ternary expressions.
     * This allows us to skip declaring variables for these PHIs.
     */
    private void collectTernaryPhis(IRMethod method) {
        ternaryPhiValues.clear();

        for (IRBlock block : method.getBlocks()) {
            RegionInfo info = analyzer.getRegionInfo(block);
            if (info == null || info.getType() != ControlFlowContext.StructuredRegion.IF_THEN_ELSE) {
                continue;
            }

            PhiInstruction booleanPhi = findBooleanPhiAssignmentPatternIR(block, info);
            if (booleanPhi != null && booleanPhi.getResult() != null) {
                ternaryPhiValues.add(booleanPhi.getResult());
                continue;
            }

            PhiInstruction ternaryPhi = findTernaryPhiPatternForPrepass(info);
            if (ternaryPhi != null && ternaryPhi.getResult() != null) {
                ternaryPhiValues.add(ternaryPhi.getResult());
            }
        }
    }

    /**
     * Simplified version of findTernaryPhiPattern for the pre-pass.
     * Doesn't require header block since we just need to check the pattern.
     */
    private PhiInstruction findTernaryPhiPatternForPrepass(RegionInfo info) {
        IRBlock thenBlock = info.getThenBlock();
        IRBlock elseBlock = info.getElseBlock();
        IRBlock mergeBlock = info.getMergeBlock();

        if (thenBlock == null || elseBlock == null || mergeBlock == null) {
            return null;
        }

        SSAValue thenValue = extractSingleProducedValue(thenBlock);
        SSAValue elseValue = extractSingleProducedValue(elseBlock);

        if (thenValue == null || elseValue == null) {
            return null;
        }

        for (PhiInstruction phi : mergeBlock.getPhiInstructions()) {
            if (phiReceivesValues(phi, thenBlock, elseBlock, thenValue, elseValue)) {
                return phi;
            }
        }

        return null;
    }

    /**
     * Detects a general ternary pattern where then/else blocks each produce a single value
     * that feeds into a PHI in the merge block. This pattern occurs with code like:
     * x == null ? "" : x
     *
     * The bytecode pattern is:
     * - if(cond) goto thenBlock else elseBlock
     * - thenBlock: load value1, goto mergeBlock
     * - elseBlock: load value2, goto mergeBlock
     * - mergeBlock: PHI(value1, value2), use PHI in method call/assignment
     */
    private PhiInstruction findTernaryPhiPattern(IRBlock header, RegionInfo info) {
        IRBlock thenBlock = info.getThenBlock();
        IRBlock elseBlock = info.getElseBlock();
        IRBlock mergeBlock = info.getMergeBlock();

        if (thenBlock == null || elseBlock == null || mergeBlock == null) {
            return null;
        }

        SSAValue thenValue = extractSingleProducedValue(thenBlock);
        SSAValue elseValue = extractSingleProducedValue(elseBlock);

        if (thenValue == null || elseValue == null) {
            return null;
        }

        for (PhiInstruction phi : mergeBlock.getPhiInstructions()) {
            if (phiReceivesValues(phi, thenBlock, elseBlock, thenValue, elseValue)) {
                return phi;
            }
        }

        return null;
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
            if (instr instanceof GotoInstruction) {
                continue;
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
     * Checks if a PHI receives the given values from the then/else blocks.
     */
    private boolean phiReceivesValues(PhiInstruction phi, IRBlock thenBlock, IRBlock elseBlock,
                                       SSAValue thenValue, SSAValue elseValue) {
        Set<IRBlock> incomingBlocks = phi.getIncomingBlocks();

        if (incomingBlocks.size() != 2) {
            return false;
        }

        if (!incomingBlocks.contains(thenBlock) || !incomingBlocks.contains(elseBlock)) {
            return false;
        }

        Value phiThenValue = phi.getIncoming(thenBlock);
        Value phiElseValue = phi.getIncoming(elseBlock);

        boolean thenMatches = valuesMatch(phiThenValue, thenValue);
        boolean elseMatches = valuesMatch(phiElseValue, elseValue);

        return thenMatches && elseMatches;
    }

    /**
     * Checks if two values match, accounting for both direct SSAValue equality
     * and cases where one is a constant.
     */
    private boolean valuesMatch(Value phiValue, SSAValue blockValue) {
        if (phiValue == blockValue) {
            return true;
        }
        if (phiValue instanceof SSAValue) {
            SSAValue ssaPhiValue = (SSAValue) phiValue;
            return ssaPhiValue == blockValue;
        }
        if (phiValue instanceof com.tonic.analysis.ssa.value.Constant) {
            if (blockValue != null && blockValue.getDefinition() instanceof ConstantInstruction) {
                ConstantInstruction ci = (ConstantInstruction) blockValue.getDefinition();
                return ci.getConstant().equals(phiValue);
            }
        }
        return false;
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

        Expression thenExpr = exprRecoverer.recoverOperand(thenValue);
        Expression elseExpr = exprRecoverer.recoverOperand(elseValue);

        SSAValue phiResult = phi.getResult();
        SourceType type = typeRecoverer.recoverType(phiResult);

        TernaryExpr ternaryExpr = new TernaryExpr(condition, thenExpr, elseExpr, type);

        if (phiResult != null) {
            context.getExpressionContext().cacheExpression(phiResult, ternaryExpr);
            context.getExpressionContext().unmarkMaterialized(phiResult);
        }
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

        // Check if this is a parameter slot
        int paramSlots = computeParameterSlots();
        if (localIndex < paramSlots) {
            int argIndex = isStatic ? localIndex : localIndex - 1;
            return "arg" + argIndex;
        }

        return "local" + localIndex;
    }

    /**
     * Computes the number of local variable slots used by parameters.
     * Note: for instance methods, slot 0 is 'this', so parameters start at slot 1.
     * Long and double types take 2 slots.
     */
    private int computeParameterSlots() {
        IRMethod method = context.getIrMethod();
        int slots = method.isStatic() ? 0 : 1; // 'this' takes slot 0 for instance methods

        String descriptor = method.getDescriptor();
        if (descriptor == null) {
            return slots;
        }

        List<String> paramTypes = parseParameterTypes(descriptor);
        for (String paramType : paramTypes) {
            slots++;
            // Long and double take 2 slots
            if ("J".equals(paramType) || "D".equals(paramType)) {
                slots++;
            }
        }
        return slots;
    }
}
