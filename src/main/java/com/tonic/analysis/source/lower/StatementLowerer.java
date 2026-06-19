package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lowers AST Statement nodes to IR instructions and blocks.
 */
public class StatementLowerer {

    private final LoweringContext ctx;
    private final ExpressionLowerer exprLowerer;

    /**
     * Creates a new statement lowerer.
     *
     * @param ctx the lowering context
     * @param exprLowerer the expression lowerer
     */
    public StatementLowerer(LoweringContext ctx, ExpressionLowerer exprLowerer) {
        this.ctx = ctx;
        this.exprLowerer = exprLowerer;
    }

    /**
     * Lowers a statement to IR.
     */
    public void lower(Statement stmt) {
        if (stmt instanceof BlockStmt) {
            BlockStmt block = (BlockStmt) stmt;
            lowerBlock(block);
        } else if (stmt instanceof VarDeclStmt) {
            VarDeclStmt decl = (VarDeclStmt) stmt;
            lowerVarDecl(decl);
        } else if (stmt instanceof ExprStmt) {
            ExprStmt expr = (ExprStmt) stmt;
            lowerExprStmt(expr);
        } else if (stmt instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) stmt;
            lowerReturn(ret);
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            lowerIf(ifStmt);
        } else if (stmt instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt) stmt;
            lowerWhile(whileStmt);
        } else if (stmt instanceof DoWhileStmt) {
            DoWhileStmt doWhile = (DoWhileStmt) stmt;
            lowerDoWhile(doWhile);
        } else if (stmt instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) stmt;
            lowerFor(forStmt);
        } else if (stmt instanceof ForEachStmt) {
            ForEachStmt forEach = (ForEachStmt) stmt;
            lowerForEach(forEach);
        } else if (stmt instanceof SwitchStmt) {
            SwitchStmt switchStmt = (SwitchStmt) stmt;
            lowerSwitch(switchStmt);
        } else if (stmt instanceof ThrowStmt) {
            ThrowStmt throwStmt = (ThrowStmt) stmt;
            lowerThrow(throwStmt);
        } else if (stmt instanceof BreakStmt) {
            BreakStmt breakStmt = (BreakStmt) stmt;
            lowerBreak(breakStmt);
        } else if (stmt instanceof ContinueStmt) {
            ContinueStmt contStmt = (ContinueStmt) stmt;
            lowerContinue(contStmt);
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            lowerTryCatch(tryCatch);
        } else if (stmt instanceof SynchronizedStmt) {
            SynchronizedStmt syncStmt = (SynchronizedStmt) stmt;
            lowerSynchronized(syncStmt);
        } else if (stmt instanceof LabeledStmt) {
            LabeledStmt labeled = (LabeledStmt) stmt;
            lowerLabeled(labeled);
        } else if (stmt instanceof IRRegionStmt) {
            IRRegionStmt irRegion = (IRRegionStmt) stmt;
            lowerIRRegion(irRegion);
        } else {
            throw new LoweringException("Unsupported statement type: " + stmt.getClass().getSimpleName());
        }
    }

    private void lowerBlock(BlockStmt block) {
        for (Statement stmt : block.getStatements()) {
            lower(stmt);
            if (ctx.getCurrentBlock().getTerminator() != null) {
                break;
            }
        }
    }

    private void lowerVarDecl(VarDeclStmt decl) {
        SourceType type = decl.getType();
        String name = decl.getName();
        Expression init = decl.getInitializer();

        if (init != null) {
            Value value = exprLowerer.lower(init);
            if (value instanceof SSAValue) {
                SSAValue ssaVal = (SSAValue) value;
                ctx.setVariable(name, ssaVal);
            } else {
                IRType irType = type.toIRType();
                SSAValue ssaVal = ctx.newValue(irType);
                ctx.getCurrentBlock().addInstruction(new ConstantInstruction(ssaVal, (Constant) value));
                ctx.setVariable(name, ssaVal);
            }
        } else {
            IRType irType = type.toIRType();
            SSAValue ssaVal = ctx.newValue(irType);
            ctx.setVariable(name, ssaVal);
        }
    }

    private void lowerExprStmt(ExprStmt stmt) {
        exprLowerer.lower(stmt.getExpression());
    }

    private void lowerReturn(ReturnStmt ret) {
        Expression value = ret.getValue();

        if (value != null) {
            Value retVal = exprLowerer.lower(value);
            ctx.getCurrentBlock().addInstruction(new ReturnInstruction(retVal));
        } else {
            ctx.getCurrentBlock().addInstruction(new ReturnInstruction());
        }
    }

    private void lowerIf(IfStmt ifStmt) {
        IRBlock thenBlock = ctx.createBlock();
        IRBlock elseBlock = ifStmt.getElseBranch() != null ? ctx.createBlock() : null;
        IRBlock mergeBlock = ctx.createBlock();

        IRBlock falseTarget = elseBlock != null ? elseBlock : mergeBlock;
        exprLowerer.lowerCondition(ifStmt.getCondition(), thenBlock, falseTarget);

        ctx.setCurrentBlock(thenBlock);
        lower(ifStmt.getThenBranch());
        boolean thenFallsThrough = ctx.getCurrentBlock().getTerminator() == null;
        if (thenFallsThrough) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(mergeBlock));
            ctx.getCurrentBlock().addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }
        IRBlock thenEndBlock = ctx.getCurrentBlock();

        boolean elseFallsThrough;
        if (elseBlock != null) {
            ctx.setCurrentBlock(elseBlock);
            lower(ifStmt.getElseBranch());
            elseFallsThrough = ctx.getCurrentBlock().getTerminator() == null;
            if (elseFallsThrough) {
                ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(mergeBlock));
                ctx.getCurrentBlock().addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            }
        } else {
            elseFallsThrough = true;
        }

        if (thenFallsThrough || elseFallsThrough) {
            ctx.setCurrentBlock(mergeBlock);
        } else {
            ctx.setCurrentBlock(thenEndBlock);
        }
    }

    private void lowerWhile(WhileStmt whileStmt) {
        IRBlock condBlock = ctx.createBlock();
        IRBlock bodyBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(condBlock));
        ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(condBlock);
        exprLowerer.lowerCondition(whileStmt.getCondition(), bodyBlock, exitBlock);

        ctx.pushLoop(whileStmt.getLabel(), condBlock, exitBlock);

        ctx.setCurrentBlock(bodyBlock);
        lower(whileStmt.getBody());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(condBlock));
            ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        ctx.popLoop();
        ctx.setCurrentBlock(exitBlock);
    }

    private void lowerDoWhile(DoWhileStmt doWhile) {
        IRBlock bodyBlock = ctx.createBlock();
        IRBlock condBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(bodyBlock));
        ctx.getCurrentBlock().addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.pushLoop(doWhile.getLabel(), condBlock, exitBlock);

        ctx.setCurrentBlock(bodyBlock);
        lower(doWhile.getBody());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(condBlock));
            ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        ctx.setCurrentBlock(condBlock);
        exprLowerer.lowerCondition(doWhile.getCondition(), bodyBlock, exitBlock);

        ctx.popLoop();
        ctx.setCurrentBlock(exitBlock);
    }

    private void lowerFor(ForStmt forStmt) {
        for (Statement init : forStmt.getInit()) {
            lower(init);
        }

        IRBlock condBlock = ctx.createBlock();
        IRBlock bodyBlock = ctx.createBlock();
        IRBlock updateBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(condBlock));
        ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(condBlock);
        Expression cond = forStmt.getCondition();
        if (cond != null) {
            exprLowerer.lowerCondition(cond, bodyBlock, exitBlock);
        } else {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(bodyBlock));
            condBlock.addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        ctx.pushLoop(forStmt.getLabel(), updateBlock, exitBlock);

        ctx.setCurrentBlock(bodyBlock);
        lower(forStmt.getBody());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(updateBlock));
            ctx.getCurrentBlock().addSuccessor(updateBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        ctx.setCurrentBlock(updateBlock);
        for (Expression update : forStmt.getUpdate()) {
            exprLowerer.lower(update);
        }
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(condBlock));
        updateBlock.addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.popLoop();
        ctx.setCurrentBlock(exitBlock);
    }

    private void lowerForEach(ForEachStmt forEach) {
        Value iterable = exprLowerer.lower(forEach.getIterable());

        IRType intType = com.tonic.analysis.ssa.type.PrimitiveType.INT;
        SSAValue indexVar = ctx.newValue(intType);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(indexVar, com.tonic.analysis.ssa.value.IntConstant.ZERO));
        String indexName = ctx.newTempName();
        ctx.setVariable(indexName, indexVar);

        IRBlock condBlock = ctx.createBlock();
        IRBlock bodyBlock = ctx.createBlock();
        IRBlock updateBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(condBlock));
        ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(condBlock);
        SSAValue length = ctx.newValue(intType);
        SimpleInstruction arrayLenInstr = SimpleInstruction.createArrayLength(length, iterable);
        ctx.getCurrentBlock().addInstruction(arrayLenInstr);

        SSAValue index = ctx.getVariable(indexName);
        BranchInstruction branch = new BranchInstruction(CompareOp.LT, index, length, bodyBlock, exitBlock);
        ctx.getCurrentBlock().addInstruction(branch);
        condBlock.addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        condBlock.addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.pushLoop(forEach.getLabel(), updateBlock, exitBlock);

        ctx.setCurrentBlock(bodyBlock);
        index = ctx.getVariable(indexName);
        IRType elemType = forEach.getVariable().getType().toIRType();
        SSAValue elem = ctx.newValue(elemType);
        ArrayAccessInstruction loadInstr = ArrayAccessInstruction.createLoad(elem, iterable, index);
        ctx.getCurrentBlock().addInstruction(loadInstr);
        ctx.setVariable(forEach.getVariable().getName(), elem);

        lower(forEach.getBody());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(updateBlock));
            ctx.getCurrentBlock().addSuccessor(updateBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        ctx.setCurrentBlock(updateBlock);
        index = ctx.getVariable(indexName);
        SSAValue one = ctx.newValue(intType);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(one, com.tonic.analysis.ssa.value.IntConstant.ONE));
        SSAValue newIndex = ctx.newValue(intType);
        ctx.getCurrentBlock().addInstruction(new BinaryOpInstruction(newIndex, BinaryOp.ADD, index, one));
        ctx.setVariable(indexName, newIndex);
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(condBlock));
        updateBlock.addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.popLoop();
        ctx.setCurrentBlock(exitBlock);
    }

    private void lowerSwitch(SwitchStmt switchStmt) {
        Value selector = exprLowerer.lower(switchStmt.getSelector());

        IRBlock exitBlock = ctx.createBlock();
        IRBlock defaultBlock = null;

        List<SwitchCase> cases = switchStmt.getCases();
        IRBlock[] caseBlocks = new IRBlock[cases.size()];
        for (int i = 0; i < cases.size(); i++) {
            caseBlocks[i] = ctx.createBlock();
            if (cases.get(i).isDefault()) {
                defaultBlock = caseBlocks[i];
            }
        }

        if (defaultBlock == null) {
            defaultBlock = exitBlock;
        }

        SwitchInstruction switchInstr = new SwitchInstruction(selector, defaultBlock);
        for (int i = 0; i < cases.size(); i++) {
            SwitchCase sc = cases.get(i);
            if (sc.isDefault()) {
                continue;
            }
            // Parsed/desugared cases carry their labels as constant expressions; recovered ones use
            // integer labels. Honor both, or the switch lowers with no cases (a bare goto to default).
            if (sc.hasExpressionLabels()) {
                for (Expression label : sc.expressionLabels()) {
                    Integer key = constIntLabel(label);
                    if (key != null) {
                        switchInstr.addCase(key, caseBlocks[i]);
                    }
                }
            } else {
                for (Integer label : sc.labels()) {
                    switchInstr.addCase(label, caseBlocks[i]);
                }
            }
        }
        ctx.getCurrentBlock().addInstruction(switchInstr);

        for (IRBlock caseBlock : caseBlocks) {
            ctx.getCurrentBlock().addSuccessor(caseBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }
        if (defaultBlock == exitBlock) {
            ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        ctx.pushLoop(null, exitBlock, exitBlock);

        for (int i = 0; i < cases.size(); i++) {
            ctx.setCurrentBlock(caseBlocks[i]);
            SwitchCase sc = cases.get(i);

            for (Statement stmt : sc.statements()) {
                lower(stmt);
                if (ctx.getCurrentBlock().getTerminator() != null) {
                    break;
                }
            }

            if (ctx.getCurrentBlock().getTerminator() == null) {
                if (i + 1 < cases.size()) {
                    ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(caseBlocks[i + 1]));
                    ctx.getCurrentBlock().addSuccessor(caseBlocks[i + 1], com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
                } else {
                    ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(exitBlock));
                    ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
                }
            }
        }

        ctx.popLoop();
        ctx.setCurrentBlock(exitBlock);
    }

    /** Extracts the constant int value of a switch-case label expression (int/char literal), or null. */
    private Integer constIntLabel(Expression label) {
        if (label instanceof com.tonic.analysis.source.ast.expr.LiteralExpr) {
            Object v = ((com.tonic.analysis.source.ast.expr.LiteralExpr) label).getValue();
            if (v instanceof Integer) {
                return (Integer) v;
            }
            if (v instanceof Character) {
                return (int) (Character) v;
            }
            if (v instanceof Number) {
                return ((Number) v).intValue();
            }
            if (v instanceof String) {
                try {
                    return Integer.parseInt(((String) v).trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private void lowerThrow(ThrowStmt throwStmt) {
        Value exception = exprLowerer.lower(throwStmt.getException());
        SimpleInstruction throwInstr = SimpleInstruction.createThrow(exception);
        ctx.getCurrentBlock().addInstruction(throwInstr);
    }

    private void lowerBreak(BreakStmt breakStmt) {
        IRBlock target = ctx.getBreakTarget(breakStmt.getTargetLabel());
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(target));
        ctx.getCurrentBlock().addSuccessor(target, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
    }

    private void lowerContinue(ContinueStmt contStmt) {
        IRBlock target = ctx.getContinueTarget(contStmt.getTargetLabel());
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(target));
        ctx.getCurrentBlock().addSuccessor(target, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
    }

    private void lowerTryCatch(TryCatchStmt tryCatch) {
        IRBlock tryBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();
        // Normal exits (try-success, end-of-catch) flow THROUGH the finally so its body runs on every normal path -
        // a single shared copy with phis merging the protected variables at its entry. Previously the finally block
        // had no predecessors (try/catch jumped straight to exit), so it never ran and the protected variable was
        // split across slots (-> "Bad local variable type" / uninitialized local at verification).
        IRBlock finallyBlock = tryCatch.getFinallyBlock() != null ? ctx.createBlock() : null;
        IRBlock normalExit = finallyBlock != null ? finallyBlock : exitBlock;

        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(tryBlock));
        ctx.getCurrentBlock().addSuccessor(tryBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(tryBlock);
        int blocksBeforeTryBody = ctx.getIrMethod().getBlocks().size();
        lower(tryCatch.getTryBlock());
        IRBlock tryEnd = ctx.getCurrentBlock();
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(normalExit));
            ctx.getCurrentBlock().addSuccessor(normalExit, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        // The protected region is tryBlock plus every block produced while lowering the try body (including a
        // nested try/catch's own handler blocks, which the outer try still protects). Tracking the full set
        // lets the exception table be emitted as one entry per contiguous PC run even when an interleaved
        // handler splits the region.
        Set<IRBlock> tryBodyBlocks = new LinkedHashSet<>();
        tryBodyBlocks.add(tryBlock);
        List<IRBlock> allBlocks = ctx.getIrMethod().getBlocks();
        for (int i = blocksBeforeTryBody; i < allBlocks.size(); i++) {
            tryBodyBlocks.add(allBlocks.get(i));
        }

        for (CatchClause catchClause : tryCatch.getCatches()) {
            IRBlock catchBlock = ctx.createBlock();
            tryBlock.addSuccessor(catchBlock, com.tonic.analysis.ssa.cfg.EdgeType.EXCEPTION);

            ctx.setCurrentBlock(catchBlock);

            String exVarName = catchClause.variableName();
            String exVarType = ctx.getTypeResolver().resolveClassName(
                    ((ReferenceType) catchClause.exceptionTypes().get(0).toIRType()).getInternalName());
            SSAValue exVar = ctx.newValue(new ReferenceType(exVarType));
            // Capture the JVM-provided exception (on the stack at handler entry) into the catch variable;
            // otherwise it leaks onto the operand stack of whatever follows the catch.
            catchBlock.addInstruction(SimpleInstruction.createCatch(exVar));
            ctx.setVariable(exVarName, exVar);

            lower(catchClause.body());
            if (ctx.getCurrentBlock().getTerminator() == null) {
                ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(normalExit));
                ctx.getCurrentBlock().addSuccessor(normalExit, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            }

            // Register the exception table entry/entries. A multi-catch shares one handler block but
            // needs one table entry per caught type; without this the protected region is never recorded
            // and the handler ends up as dead, frame-less code that fails verification.
            for (SourceType caught : catchClause.exceptionTypes()) {
                String catchType = ctx.getTypeResolver()
                        .resolveClassName(((ReferenceType) caught.toIRType()).getInternalName());
                ExceptionHandler handler =
                        new ExceptionHandler(tryBlock, tryEnd, catchBlock, new ReferenceType(catchType));
                handler.setTryBlocks(tryBodyBlocks);
                ctx.getIrMethod().addExceptionHandler(handler);
            }
        }

        if (finallyBlock != null) {
            ctx.setCurrentBlock(finallyBlock);
            lower(tryCatch.getFinallyBlock());
            if (ctx.getCurrentBlock().getTerminator() == null) {
                ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(exitBlock));
                ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            }
        }

        ctx.setCurrentBlock(exitBlock);
    }

    private void lowerSynchronized(SynchronizedStmt syncStmt) {
        Value monitor = exprLowerer.lower(syncStmt.getLock());

        SimpleInstruction monitorEnterInstr = SimpleInstruction.createMonitorEnter(monitor);
        ctx.getCurrentBlock().addInstruction(monitorEnterInstr);

        lower(syncStmt.getBody());

        if (ctx.getCurrentBlock().getTerminator() == null) {
            SimpleInstruction monitorExitInstr = SimpleInstruction.createMonitorExit(monitor);
            ctx.getCurrentBlock().addInstruction(monitorExitInstr);
        }
    }

    private void lowerLabeled(LabeledStmt labeled) {
        lower(labeled.getStatement());
    }

    private void lowerIRRegion(IRRegionStmt irRegion) {
        List<IRBlock> blocks = irRegion.getBlocks();
        if (blocks.isEmpty()) {
            return;
        }

        Set<IRBlock> regionBlocks = new HashSet<>(blocks);

        for (IRBlock block : blocks) {
            for (IRBlock succ : block.getSuccessors()) {
                if (!regionBlocks.contains(succ) && !ctx.getIrMethod().getBlocks().contains(succ)) {
                    throw new LoweringException(
                        "IRRegion has external successor not in method: " + succ.getName());
                }
            }
        }

        for (IRBlock block : blocks) {
            if (!ctx.getIrMethod().getBlocks().contains(block)) {
                ctx.getIrMethod().addBlock(block);
            }
        }

        IRBlock entry = irRegion.getEntryBlock();
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(entry));
        ctx.getCurrentBlock().addSuccessor(entry, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        IRBlock exitBlock = null;
        for (IRBlock block : blocks) {
            for (IRBlock succ : block.getSuccessors()) {
                if (!regionBlocks.contains(succ)) {
                    exitBlock = succ;
                    break;
                }
            }
            if (exitBlock != null) break;
        }

        if (exitBlock != null) {
            ctx.setCurrentBlock(exitBlock);
        } else {
            ctx.setCurrentBlock(blocks.get(blocks.size() - 1));
        }
    }
}
