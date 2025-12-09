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

        // Find the outermost handler (one that starts at entry or covers most code)
        ExceptionHandler outerHandler = findOutermostHandler(entry, handlers);

        if (outerHandler != null) {
            // Get all handlers for this try region
            List<ExceptionHandler> outerHandlers = new ArrayList<>();
            List<ExceptionHandler> innerHandlers = new ArrayList<>();

            for (ExceptionHandler h : handlers) {
                if (h.getTryStart() == outerHandler.getTryStart()) {
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

            // Recover catch clauses
            List<CatchClause> catchClauses = new ArrayList<>();
            for (ExceptionHandler handler : outerHandlers) {
                CatchClause catchClause = recoverCatchClause(handler);
                if (catchClause != null) {
                    catchClauses.add(catchClause);
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
        // Find blocks that are inner try region starts
        Set<IRBlock> innerTryStarts = new HashSet<>();
        for (ExceptionHandler h : innerHandlers) {
            innerTryStarts.add(h.getTryStart());
        }

        // Recursively recover, emitting inner try-catch when we reach their start blocks
        return recoverWithInnerTryCatch(start, innerHandlers, innerTryStarts, stopBlocks, new HashSet<>());
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

                List<Statement> tryStmts;
                if (!remainingHandlers.isEmpty()) {
                    // Find remaining inner try starts
                    Set<IRBlock> remainingTryStarts = new HashSet<>();
                    for (ExceptionHandler h : remainingHandlers) {
                        remainingTryStarts.add(h.getTryStart());
                    }
                    tryStmts = recoverWithInnerTryCatch(current, remainingHandlers, remainingTryStarts, innerStopBlocks, new HashSet<>());
                } else {
                    tryStmts = recoverBlockSequence(current, innerStopBlocks);
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

                // Continue from the end of the try region
                current = innerHandler.getTryEnd();
                if (current != null) {
                    visited.add(current); // Mark try end as visited
                }
                continue;
            }

            // When we have inner try regions, do simple block-by-block recovery
            // to avoid structural analysis crossing into inner try boundaries
            if (context.isProcessed(current)) {
                result.addAll(context.getStatements(current));
            } else {
                result.addAll(recoverSimpleBlock(current));
                context.markProcessed(current);
            }

            // Find the next block to process, prioritizing inner try starts
            IRBlock next = null;
            for (IRBlock succ : current.getSuccessors()) {
                if (!visited.contains(succ) && !stopBlocks.contains(succ)) {
                    if (innerTryStarts.contains(succ)) {
                        // Found an inner try start - go there next
                        next = succ;
                        break;
                    }
                    // Skip successors that are inner try starts for later
                    if (!combinedStops.contains(succ) && next == null) {
                        next = succ;
                    }
                }
            }
            current = next;
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

        // Recover handler body - directly recover instructions from handler block
        List<Statement> handlerStmts = new ArrayList<>();

        // Process all instructions in the handler block (including terminator)
        for (IRInstruction instr : handlerBlock.getInstructions()) {
            // Skip CopyInstruction that just copies exception to itself
            if (instr instanceof CopyInstruction) {
                continue;
            }

            Statement stmt = recoverInstruction(instr);
            if (stmt != null) {
                handlerStmts.add(stmt);
            }
        }

        // Recursively recover all blocks reachable from the handler
        Set<IRBlock> visitedHandlerBlocks = new HashSet<>();
        visitedHandlerBlocks.add(handlerBlock);
        recoverHandlerBlocks(handlerBlock.getSuccessors(), visitedHandlerBlocks, handlerStmts);

        // Filter out redundant assignments to exception variable
        List<Statement> filteredStmts = new ArrayList<>();
        for (Statement stmt : handlerStmts) {
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
        }

        BlockStmt handlerBody = new BlockStmt(filteredStmts.isEmpty() ? handlerStmts : filteredStmts);

        return CatchClause.of(exceptionType, exceptionVarName, handlerBody);
    }

    /**
     * Recursively recovers all blocks reachable from the handler until we hit a throw or return.
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

            // Continue to successors
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
     * Represents a try region defined by start and end blocks.
     */
    private record TryRegion(IRBlock start, IRBlock end) {}

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
        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof LoadLocalInstruction loadLocal) {
                    if (loadLocal.getResult() != null) {
                        String localName = "local" + loadLocal.getLocalIndex();
                        context.getExpressionContext().setVariableName(loadLocal.getResult(), localName);
                    }
                } else if (instr instanceof StoreLocalInstruction storeLocal) {
                    // Track which local slots are used so we can declare them
                    String localName = "local" + storeLocal.getLocalIndex();
                    // If the value being stored is an SSA value, track its name too
                    if (storeLocal.getValue() instanceof SSAValue ssaValue) {
                        context.getExpressionContext().setVariableName(ssaValue, localName);
                    }
                }
            }
        }

        // Only collect PHI variables that need early declaration
        for (IRBlock block : method.getBlocks()) {
            // Skip exception handler blocks - their variables are declared inline
            if (handlerBlocks.contains(block)) {
                continue;
            }

            // Only phi results need early declaration (they merge values from multiple paths)
            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (phi.getResult() != null) {
                    phiValues.add(phi.getResult());
                }
            }
        }

        // Sort phi values by dependency order
        List<SSAValue> sortedValues = sortByDependencies(phiValues);

        // Emit declarations only for phi variables
        for (SSAValue value : sortedValues) {
            emitPhiDeclaration(value, statements, declaredNames);
        }
    }

    /**
     * Emits a phi variable declaration with default value.
     */
    private void emitPhiDeclaration(SSAValue result, List<Statement> statements, Set<String> declaredNames) {
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

        SourceType type = typeRecoverer.recoverType(result);

        // Mark as declared
        declaredNames.add(name);
        context.getExpressionContext().markDeclared(name);
        context.getExpressionContext().setVariableName(result, name);

        // Phi variables get default values (they'll be assigned in control flow)
        Expression initValue = getDefaultValue(type);
        statements.add(new VarDeclStmt(type, name, initValue));
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
    public List<Statement> recoverBlockSequence(IRBlock startBlock, Set<IRBlock> stopBlocks) {
        List<Statement> result = new ArrayList<>();
        Set<IRBlock> visited = new HashSet<>();
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
                // No structural info - recover as simple block
                result.addAll(recoverSimpleBlock(current));
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
                    current = info.getMergeBlock();
                }
                case IF_THEN_ELSE -> {
                    Statement ifStmt = recoverIfThenElse(current, info);
                    // Collect any header statements that were placed in pending
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
                case SWITCH -> {
                    result.add(recoverSwitch(current, info));
                    current = findSwitchMerge(info);
                }
                case IRREDUCIBLE -> {
                    result.add(recoverIrreducible(current, info));
                    current = null;
                }
                default -> {
                    result.addAll(recoverSimpleBlock(current));
                    context.markProcessed(current);
                    current = getNextSequentialBlock(current);
                }
            }
        }

        return result;
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
            // Skip intermediate invoke results that are only used by other instructions
            // These will be inlined at their usage site
            SSAValue result = invoke.getResult();
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
            if (loadLocal.getResult() != null) {
                String localName = "local" + loadLocal.getLocalIndex();
                context.getExpressionContext().setVariableName(loadLocal.getResult(), localName);
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

        // Skip ConstantInstruction - constants should be inlined at usage sites
        if (instr instanceof ConstantInstruction) {
            if (instr.getResult() != null) {
                Expression value = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), value);
            }
            return null;
        }

        // Other instructions with results - may need variable declaration
        if (instr.getResult() != null) {
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

        // Use consistent local slot naming
        String name = "local" + localIndex;

        // Try to infer type from the value being stored
        SourceType type = value.getType();
        if (type == null) {
            type = com.tonic.analysis.source.ast.type.VoidSourceType.INSTANCE;
        }

        // Mark the stored value as materialized - subsequent uses should reference the variable
        if (store.getValue() instanceof SSAValue ssaValue) {
            context.getExpressionContext().markMaterialized(ssaValue);
            // Also set the variable name for this SSA value so lookups work
            context.getExpressionContext().setVariableName(ssaValue, name);
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
            if (isBooleanExpression(left)) {
                boolean wantTrue = (condition == CompareOp.NE); // NE to 0 means we want true
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
}
