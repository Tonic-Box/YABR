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

    public StatementLowerer(LoweringContext ctx, ExpressionLowerer exprLowerer) {
        this.ctx = ctx;
        this.exprLowerer = exprLowerer;
    }

    /**
     * Lowers a statement to IR.
     */
    public void lower(Statement stmt) {
        if (stmt instanceof BlockStmt block) {
            lowerBlock(block);
        } else if (stmt instanceof VarDeclStmt decl) {
            lowerVarDecl(decl);
        } else if (stmt instanceof ExprStmt expr) {
            lowerExprStmt(expr);
        } else if (stmt instanceof ReturnStmt ret) {
            lowerReturn(ret);
        } else if (stmt instanceof IfStmt ifStmt) {
            lowerIf(ifStmt);
        } else if (stmt instanceof WhileStmt whileStmt) {
            lowerWhile(whileStmt);
        } else if (stmt instanceof DoWhileStmt doWhile) {
            lowerDoWhile(doWhile);
        } else if (stmt instanceof ForStmt forStmt) {
            lowerFor(forStmt);
        } else if (stmt instanceof ForEachStmt forEach) {
            lowerForEach(forEach);
        } else if (stmt instanceof SwitchStmt switchStmt) {
            lowerSwitch(switchStmt);
        } else if (stmt instanceof ThrowStmt throwStmt) {
            lowerThrow(throwStmt);
        } else if (stmt instanceof BreakStmt breakStmt) {
            lowerBreak(breakStmt);
        } else if (stmt instanceof ContinueStmt contStmt) {
            lowerContinue(contStmt);
        } else if (stmt instanceof TryCatchStmt tryCatch) {
            lowerTryCatch(tryCatch);
        } else if (stmt instanceof SynchronizedStmt syncStmt) {
            lowerSynchronized(syncStmt);
        } else if (stmt instanceof LabeledStmt labeled) {
            lowerLabeled(labeled);
        } else if (stmt instanceof IRRegionStmt irRegion) {
            lowerIRRegion(irRegion);
        } else {
            throw new LoweringException("Unsupported statement type: " + stmt.getClass().getSimpleName());
        }
    }

    private void lowerBlock(BlockStmt block) {
        for (Statement stmt : block.getStatements()) {
            lower(stmt);

            // Stop if we hit a terminator
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
            if (value instanceof SSAValue ssaVal) {
                ctx.setVariable(name, ssaVal);
            } else {
                // Wrap constant in SSA value
                IRType irType = type.toIRType();
                SSAValue ssaVal = ctx.newValue(irType);
                ctx.getCurrentBlock().addInstruction(new ConstantInstruction(ssaVal, (com.tonic.analysis.ssa.value.Constant) value));
                ctx.setVariable(name, ssaVal);
            }
        } else {
            // Uninitialized variable - create placeholder
            IRType irType = type.toIRType();
            SSAValue ssaVal = ctx.newValue(irType);
            ctx.setVariable(name, ssaVal);
        }
    }

    private void lowerExprStmt(ExprStmt stmt) {
        // Lower expression for side effects, discard result
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
        // Lower condition
        Value cond = exprLowerer.lower(ifStmt.getCondition());

        // Create blocks
        IRBlock thenBlock = ctx.createBlock();
        IRBlock elseBlock = ifStmt.getElseBranch() != null ? ctx.createBlock() : null;
        IRBlock mergeBlock = ctx.createBlock();

        // Branch instruction
        IRBlock falseTarget = elseBlock != null ? elseBlock : mergeBlock;
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, cond, thenBlock, falseTarget);
        ctx.getCurrentBlock().addInstruction(branch);

        // Connect CFG edges
        ctx.getCurrentBlock().addSuccessor(thenBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        ctx.getCurrentBlock().addSuccessor(falseTarget, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        // Lower then branch
        ctx.setCurrentBlock(thenBlock);
        lower(ifStmt.getThenBranch());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(mergeBlock));
            ctx.getCurrentBlock().addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        // Lower else branch (if present)
        if (elseBlock != null) {
            ctx.setCurrentBlock(elseBlock);
            lower(ifStmt.getElseBranch());
            if (ctx.getCurrentBlock().getTerminator() == null) {
                ctx.getCurrentBlock().addInstruction(new GotoInstruction(mergeBlock));
                ctx.getCurrentBlock().addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            }
        }

        // Continue from merge block
        ctx.setCurrentBlock(mergeBlock);
    }

    private void lowerWhile(WhileStmt whileStmt) {
        IRBlock condBlock = ctx.createBlock();
        IRBlock bodyBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        // Jump to condition
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(condBlock));
        ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        // Condition block
        ctx.setCurrentBlock(condBlock);
        Value cond = exprLowerer.lower(whileStmt.getCondition());
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, cond, bodyBlock, exitBlock);
        ctx.getCurrentBlock().addInstruction(branch);
        condBlock.addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        condBlock.addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        // Push loop targets
        ctx.pushLoop(whileStmt.getLabel(), condBlock, exitBlock);

        // Body block
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

        // Jump to body
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(bodyBlock));
        ctx.getCurrentBlock().addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        // Push loop targets (continue goes to condition)
        ctx.pushLoop(doWhile.getLabel(), condBlock, exitBlock);

        // Body block
        ctx.setCurrentBlock(bodyBlock);
        lower(doWhile.getBody());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(condBlock));
            ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        // Condition block
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
        // Lower initializers in current block
        for (Statement init : forStmt.getInit()) {
            lower(init);
        }

        IRBlock condBlock = ctx.createBlock();
        IRBlock bodyBlock = ctx.createBlock();
        IRBlock updateBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        // Jump to condition
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(condBlock));
        ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        // Condition block
        ctx.setCurrentBlock(condBlock);
        Expression cond = forStmt.getCondition();
        if (cond != null) {
            Value condVal = exprLowerer.lower(cond);
            BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, condVal, bodyBlock, exitBlock);
            ctx.getCurrentBlock().addInstruction(branch);
            condBlock.addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            condBlock.addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        } else {
            // Infinite loop - always go to body
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(bodyBlock));
            condBlock.addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        // Push loop targets (continue goes to update)
        ctx.pushLoop(forStmt.getLabel(), updateBlock, exitBlock);

        // Body block
        ctx.setCurrentBlock(bodyBlock);
        lower(forStmt.getBody());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(updateBlock));
            ctx.getCurrentBlock().addSuccessor(updateBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        // Update block
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
        // For-each over array: for (T item : array) { ... }
        // Translates to: for (int i = 0; i < array.length; i++) { T item = array[i]; ... }

        // Get iterable
        Value iterable = exprLowerer.lower(forEach.getIterable());

        // Create index variable
        IRType intType = com.tonic.analysis.ssa.type.PrimitiveType.INT;
        SSAValue indexVar = ctx.newValue(intType);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(indexVar, com.tonic.analysis.ssa.value.IntConstant.ZERO));
        String indexName = ctx.newTempName();
        ctx.setVariable(indexName, indexVar);

        IRBlock condBlock = ctx.createBlock();
        IRBlock bodyBlock = ctx.createBlock();
        IRBlock updateBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        // Jump to condition
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(condBlock));
        ctx.getCurrentBlock().addSuccessor(condBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        // Condition: i < array.length
        ctx.setCurrentBlock(condBlock);
        SSAValue length = ctx.newValue(intType);
        ctx.getCurrentBlock().addInstruction(new ArrayLengthInstruction(length, iterable));

        SSAValue index = ctx.getVariable(indexName);
        BranchInstruction branch = new BranchInstruction(CompareOp.LT, index, length, bodyBlock, exitBlock);
        ctx.getCurrentBlock().addInstruction(branch);
        condBlock.addSuccessor(bodyBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        condBlock.addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        // Push loop targets
        ctx.pushLoop(forEach.getLabel(), updateBlock, exitBlock);

        // Body: load array[i] into loop variable, then execute body
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

        // Update: i++
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

        // Create blocks for each case
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

        // Create switch instruction
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

        // Add CFG edges
        for (IRBlock caseBlock : caseBlocks) {
            ctx.getCurrentBlock().addSuccessor(caseBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }
        if (defaultBlock == exitBlock) {
            ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        // Push break target
        ctx.pushLoop(null, exitBlock, exitBlock); // continue = break for switch

        // Lower each case
        for (int i = 0; i < cases.size(); i++) {
            ctx.setCurrentBlock(caseBlocks[i]);
            SwitchCase sc = cases.get(i);

            for (Statement stmt : sc.statements()) {
                lower(stmt);
                if (ctx.getCurrentBlock().getTerminator() != null) {
                    break;
                }
            }

            // Fall through to next case if no break
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
        IRBlock target = ctx.getBreakTarget(breakStmt.getLabel());
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(target));
        ctx.getCurrentBlock().addSuccessor(target, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
    }

    private void lowerContinue(ContinueStmt contStmt) {
        IRBlock target = ctx.getContinueTarget(contStmt.getLabel());
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(target));
        ctx.getCurrentBlock().addSuccessor(target, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
    }

    private void lowerTryCatch(TryCatchStmt tryCatch) {
        // Simplified try-catch lowering
        // Full implementation would need exception handler registration

        IRBlock tryBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        // Jump to try block
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(tryBlock));
        ctx.getCurrentBlock().addSuccessor(tryBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        // Lower try block
        ctx.setCurrentBlock(tryBlock);
        lower(tryCatch.getTryBlock());
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(exitBlock));
            ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        // Lower catch clauses
        for (CatchClause catchClause : tryCatch.getCatches()) {
            IRBlock catchBlock = ctx.createBlock();
            tryBlock.addSuccessor(catchBlock, com.tonic.analysis.ssa.cfg.EdgeType.EXCEPTION);

            ctx.setCurrentBlock(catchBlock);

            // Create variable for caught exception
            String exVarName = catchClause.variableName();
            SourceType exType = catchClause.exceptionTypes().get(0); // Use first type
            SSAValue exVar = ctx.newValue(exType.toIRType());
            ctx.setVariable(exVarName, exVar);

            lower(catchClause.body());
            if (ctx.getCurrentBlock().getTerminator() == null) {
                ctx.getCurrentBlock().addInstruction(new GotoInstruction(exitBlock));
                ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            }
        }

        // Lower finally block (if present)
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

        // Monitor enter
        ctx.getCurrentBlock().addInstruction(new MonitorEnterInstruction(monitor));

        // Lower body (in try block for proper monitor exit on exception)
        lower(syncStmt.getBody());

        // Monitor exit
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new MonitorExitInstruction(monitor));
        }
    }

    private void lowerLabeled(LabeledStmt labeled) {
        // The label is handled by the inner statement (loop or switch)
        lower(labeled.getStatement());
    }

    private void lowerIRRegion(IRRegionStmt irRegion) {
        // IRRegionStmt is a special marker for inline IR
        // For now, skip it - used during recovery as a boundary marker
    }
}
