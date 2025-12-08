package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.recovery.ControlFlowContext.StructuredRegion;
import com.tonic.analysis.source.recovery.StructuralAnalyzer.RegionInfo;
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

        List<Statement> statements = recoverBlockSequence(entry, new HashSet<>());
        return new BlockStmt(statements);
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
                    result.add(recoverIfThen(current, info));
                    current = info.getMergeBlock();
                }
                case IF_THEN_ELSE -> {
                    result.add(recoverIfThenElse(current, info));
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
        // Phi nodes typically become variable declarations or are handled implicitly
        SSAValue result = phi.getResult();
        String name = context.getExpressionContext().getVariableName(result);
        if (name == null) {
            name = "v" + result.getId();
        }

        SourceType type = typeRecoverer.recoverType(result);
        // Skip phi recovery here - handled by control flow merging
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
            // Void invocations become expression statements
            if (invoke.getResult() == null || invoke.getResult().getType() == null) {
                Expression expr = exprRecoverer.recover(invoke);
                return new ExprStmt(expr);
            }
        }

        if (instr instanceof MonitorEnterInstruction || instr instanceof MonitorExitInstruction) {
            // Monitor instructions handled by synchronized recovery
            return null;
        }

        if (instr instanceof ThrowInstruction throwInstr) {
            return recoverThrow(throwInstr);
        }

        // Skip LoadLocal instructions - they don't need variable declarations
        // (they load from already-declared locals or parameters like 'this')
        if (instr instanceof LoadLocalInstruction) {
            // Cache the expression but don't emit a declaration
            if (instr.getResult() != null) {
                Expression value = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), value);
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
        // Branch, Goto, Switch handled by structural analysis
        return null;
    }

    private Statement recoverStoreLocal(StoreLocalInstruction store) {
        Expression value = exprRecoverer.recoverOperand(store.getValue());
        int localIndex = store.getLocalIndex();

        // Generate a name based on local index
        String name = "local" + localIndex;

        // Try to infer type from the value being stored
        SourceType type = value.getType();
        if (type == null) {
            type = com.tonic.analysis.source.ast.type.VoidSourceType.INSTANCE;
        }

        return new VarDeclStmt(type, name, value);
    }

    private Statement recoverPutField(PutFieldInstruction putField) {
        Expression receiver = putField.isStatic() ? null : exprRecoverer.recoverOperand(putField.getObjectRef());
        Expression value = exprRecoverer.recoverOperand(putField.getValue());

        // Create field access expression as assignment target
        SourceType fieldType = typeRecoverer.recoverType(putField.getDescriptor());
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
        Expression value = exprRecoverer.recoverOperand(ret.getReturnValue());
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

        return new VarDeclStmt(type, name, value);
    }

    private Statement recoverIfThen(IRBlock header, RegionInfo info) {
        // Mark header as processed BEFORE recursing to prevent infinite loops
        context.markProcessed(header);

        // Use negation flag from structural analysis to get correct condition polarity
        Expression condition = recoverCondition(header, info.isConditionNegated());
        Set<IRBlock> stopBlocks = new HashSet<>();
        if (info.getMergeBlock() != null) {
            stopBlocks.add(info.getMergeBlock());
        }

        List<Statement> thenStmts = recoverBlockSequence(info.getThenBlock(), stopBlocks);
        BlockStmt thenBlock = new BlockStmt(thenStmts);

        return new IfStmt(condition, thenBlock, null);
    }

    private Statement recoverIfThenElse(IRBlock header, RegionInfo info) {
        // Mark header as processed BEFORE recursing to prevent infinite loops
        context.markProcessed(header);

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

        return new IfStmt(condition, thenBlock, elseBlock);
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
