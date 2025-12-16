package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.List;

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
                ctx.getCurrentBlock().addInstruction(new ConstantInstruction(ssaVal, (com.tonic.analysis.ssa.value.Constant) value));
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
        Value cond = exprLowerer.lower(ifStmt.getCondition());

        IRBlock thenBlock = ctx.createBlock();
        IRBlock elseBlock = ifStmt.getElseBranch() != null ? ctx.createBlock() : null;
        IRBlock mergeBlock = ctx.createBlock();

        IRBlock falseTarget = elseBlock != null ? elseBlock : mergeBlock;
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, cond, thenBlock, falseTarget);
        ctx.getCurrentBlock().addInstruction(branch);

        ctx.getCurrentBlock().addSuccessor(thenBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        ctx.getCurrentBlock().addSuccessor(falseTarget, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(thenBlock);
        lower(ifStmt.getThenBranch());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(mergeBlock));
            ctx.getCurrentBlock().addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        if (elseBlock != null) {
            ctx.setCurrentBlock(elseBlock);
            lower(ifStmt.getElseBranch());
            if (ctx.getCurrentBlock().getTerminator() == null) {
                ctx.getCurrentBlock().addInstruction(new GotoInstruction(mergeBlock));
                ctx.getCurrentBlock().addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            }
        }

        ctx.setCurrentBlock(mergeBlock);
    }

    private void lowerWhile(WhileStmt whileStmt) {
        IRBlock condBlock = ctx.createBlock();
        IRBlock bodyBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        ctx.getCurrentBlock().addInstruction(new GotoInstruction(condBlock));
        ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(condBlock);
        Value cond = exprLowerer.lower(whileStmt.getCondition());
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, cond, bodyBlock, exitBlock);
        ctx.getCurrentBlock().addInstruction(branch);
        condBlock.addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        condBlock.addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.pushLoop(whileStmt.getLabel(), condBlock, exitBlock);

        ctx.setCurrentBlock(bodyBlock);
        lower(whileStmt.getBody());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(condBlock));
            ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        ctx.popLoop();
        ctx.setCurrentBlock(exitBlock);
    }

    private void lowerDoWhile(DoWhileStmt doWhile) {
        IRBlock bodyBlock = ctx.createBlock();
        IRBlock condBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        ctx.getCurrentBlock().addInstruction(new GotoInstruction(bodyBlock));
        ctx.getCurrentBlock().addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.pushLoop(doWhile.getLabel(), condBlock, exitBlock);

        ctx.setCurrentBlock(bodyBlock);
        lower(doWhile.getBody());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(condBlock));
            ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        ctx.setCurrentBlock(condBlock);
        Value cond = exprLowerer.lower(doWhile.getCondition());
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, cond, bodyBlock, exitBlock);
        ctx.getCurrentBlock().addInstruction(branch);
        condBlock.addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        condBlock.addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

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

        ctx.getCurrentBlock().addInstruction(new GotoInstruction(condBlock));
        ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(condBlock);
        Expression cond = forStmt.getCondition();
        if (cond != null) {
            Value condVal = exprLowerer.lower(cond);
            BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, condVal, bodyBlock, exitBlock);
            ctx.getCurrentBlock().addInstruction(branch);
            condBlock.addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            condBlock.addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        } else {
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(bodyBlock));
            condBlock.addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        ctx.pushLoop(forStmt.getLabel(), updateBlock, exitBlock);

        ctx.setCurrentBlock(bodyBlock);
        lower(forStmt.getBody());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(updateBlock));
            ctx.getCurrentBlock().addSuccessor(updateBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        ctx.setCurrentBlock(updateBlock);
        for (Expression update : forStmt.getUpdate()) {
            exprLowerer.lower(update);
        }
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(condBlock));
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

        ctx.getCurrentBlock().addInstruction(new GotoInstruction(condBlock));
        ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(condBlock);
        SSAValue length = ctx.newValue(intType);
        ctx.getCurrentBlock().addInstruction(new ArrayLengthInstruction(length, iterable));

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
        ctx.getCurrentBlock().addInstruction(new ArrayLoadInstruction(elem, iterable, index));
        ctx.setVariable(forEach.getVariable().getName(), elem);

        lower(forEach.getBody());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(updateBlock));
            ctx.getCurrentBlock().addSuccessor(updateBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        ctx.setCurrentBlock(updateBlock);
        index = ctx.getVariable(indexName);
        SSAValue one = ctx.newValue(intType);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(one, com.tonic.analysis.ssa.value.IntConstant.ONE));
        SSAValue newIndex = ctx.newValue(intType);
        ctx.getCurrentBlock().addInstruction(new BinaryOpInstruction(newIndex, BinaryOp.ADD, index, one));
        ctx.setVariable(indexName, newIndex);
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(condBlock));
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
            if (!sc.isDefault()) {
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
                    ctx.getCurrentBlock().addInstruction(new GotoInstruction(caseBlocks[i + 1]));
                    ctx.getCurrentBlock().addSuccessor(caseBlocks[i + 1], com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
                } else {
                    ctx.getCurrentBlock().addInstruction(new GotoInstruction(exitBlock));
                    ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
                }
            }
        }

        ctx.popLoop();
        ctx.setCurrentBlock(exitBlock);
    }

    private void lowerThrow(ThrowStmt throwStmt) {
        Value exception = exprLowerer.lower(throwStmt.getException());
        ctx.getCurrentBlock().addInstruction(new ThrowInstruction(exception));
    }

    private void lowerBreak(BreakStmt breakStmt) {
        IRBlock target = ctx.getBreakTarget(breakStmt.getTargetLabel());
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(target));
        ctx.getCurrentBlock().addSuccessor(target, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
    }

    private void lowerContinue(ContinueStmt contStmt) {
        IRBlock target = ctx.getContinueTarget(contStmt.getTargetLabel());
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(target));
        ctx.getCurrentBlock().addSuccessor(target, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
    }

    private void lowerTryCatch(TryCatchStmt tryCatch) {
        IRBlock tryBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        ctx.getCurrentBlock().addInstruction(new GotoInstruction(tryBlock));
        ctx.getCurrentBlock().addSuccessor(tryBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(tryBlock);
        lower(tryCatch.getTryBlock());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(exitBlock));
            ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        for (CatchClause catchClause : tryCatch.getCatches()) {
            IRBlock catchBlock = ctx.createBlock();
            tryBlock.addSuccessor(catchBlock, com.tonic.analysis.ssa.cfg.EdgeType.EXCEPTION);

            ctx.setCurrentBlock(catchBlock);

            String exVarName = catchClause.variableName();
            SourceType exType = catchClause.exceptionTypes().get(0);
            SSAValue exVar = ctx.newValue(exType.toIRType());
            ctx.setVariable(exVarName, exVar);

            lower(catchClause.body());
            if (ctx.getCurrentBlock().getTerminator() == null) {
                ctx.getCurrentBlock().addInstruction(new GotoInstruction(exitBlock));
                ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            }
        }

        if (tryCatch.getFinallyBlock() != null) {
            IRBlock finallyBlock = ctx.createBlock();
            ctx.setCurrentBlock(finallyBlock);
            lower(tryCatch.getFinallyBlock());
            if (ctx.getCurrentBlock().getTerminator() == null) {
                ctx.getCurrentBlock().addInstruction(new GotoInstruction(exitBlock));
                ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            }
        }

        ctx.setCurrentBlock(exitBlock);
    }

    private void lowerSynchronized(SynchronizedStmt syncStmt) {
        Value monitor = exprLowerer.lower(syncStmt.getLock());

        ctx.getCurrentBlock().addInstruction(new MonitorEnterInstruction(monitor));

        lower(syncStmt.getBody());

        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new MonitorExitInstruction(monitor));
        }
    }

    private void lowerLabeled(LabeledStmt labeled) {
        lower(labeled.getStatement());
    }

    private void lowerIRRegion(IRRegionStmt irRegion) {
    }
}
