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

import java.util.ArrayDeque;
import java.util.Deque;
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
     * Finally blocks of the try statements the lowering is currently inside, innermost first. An
     * abrupt {@code return} out of a protected region must run each enclosing finally before exiting,
     * matching javac's inlined-finally lowering (JLS 14.20.2). Fall-through and exception paths get
     * their own finally copies in {@link #lowerTryCatch}.
     */
    private final Deque<Statement> finallyStack = new ArrayDeque<>();

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
        if (stmt instanceof MonitorExitStmt) {
            ctx.getCurrentBlock().addInstruction(
                    SimpleInstruction.createMonitorExit(((MonitorExitStmt) stmt).monitor));
        } else if (stmt instanceof BlockStmt) {
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
        ctx.declareLocal(name, type.toIRType(), false);

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
        // The return value is evaluated before any enclosing finally runs (JLS 14.20.2). Its SSA value
        // is an immutable snapshot, so a finally that reassigns the same variable cannot clobber it.
        Value retVal = value != null ? exprLowerer.lower(value) : null;

        for (Statement fin : finallyStack) {
            if (ctx.getCurrentBlock().getTerminator() != null) {
                return;
            }
            lower(fin);
        }
        if (ctx.getCurrentBlock().getTerminator() != null) {
            return;
        }

        ctx.getCurrentBlock().addInstruction(
                retVal != null ? new ReturnInstruction(retVal) : new ReturnInstruction());
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
        ctx.declareLocal(forEach.getVariable().getName(), elemType, false);
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

        // A switch is a break-only scope: an unlabeled break leaves it at exitBlock, but an unlabeled continue must
        // pass through to the enclosing loop's update (null continue-target keeps the resolver searching outward).
        ctx.pushLoop(null, null, exitBlock);

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
        if (!tryCatch.getResources().isEmpty()) {
            lowerTryWithResources(tryCatch);
            return;
        }
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
        // Protect the try body and catch bodies: a return out of either must run this finally first.
        if (finallyBlock != null) {
            finallyStack.push(tryCatch.getFinallyBlock());
        }
        java.util.Map<String, SSAValue> preTryVars = ctx.snapshotVariables();
        int blocksBeforeTryBody = ctx.getIrMethod().getBlocks().size();
        lower(tryCatch.getTryBlock());
        IRBlock tryEnd = ctx.getCurrentBlock();
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(normalExit));
            ctx.getCurrentBlock().addSuccessor(normalExit, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        // Variables reassigned inside the try: an exception can fire before the reassignment, so each handler must see
        // the PRE-try value. Re-establishing them at catch entry (below) emits a StoreLocal there - a real def that
        // forces a correct phi at the try/catch -> finally join, instead of a trivial phi that binds to the try's
        // post-store value (undefined on the exception path -> "Bad local variable type" at verification).
        // Inside a loop this snapshot is NOT re-established: it holds the pre-LOOP value, and binding the catch to
        // it would reset a loop-carried variable on every caught iteration; there the catch keeps the variable's
        // slot-carried value, which is the value at the fault point.
        java.util.Map<String, SSAValue> postTryVars = ctx.snapshotVariables();
        java.util.List<String> reassignedInTry = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, SSAValue> e : preTryVars.entrySet()) {
            SSAValue after = postTryVars.get(e.getKey());
            if (after != null && after != e.getValue()) {
                reassignedInTry.add(e.getKey());
            }
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

        List<IRBlock> catchBlocks = new java.util.ArrayList<>();
        for (CatchClause catchClause : tryCatch.getCatches()) {
            IRBlock catchBlock = ctx.createBlock();
            catchBlocks.add(catchBlock);
            tryBlock.addSuccessor(catchBlock, com.tonic.analysis.ssa.cfg.EdgeType.EXCEPTION);

            ctx.setCurrentBlock(catchBlock);

            String exVarName = catchClause.variableName();
            String exVarType = ctx.getTypeResolver().resolveClassName(
                    ((ReferenceType) catchClause.exceptionTypes().get(0).toIRType()).getInternalName());
            ReferenceType exVarIrType = new ReferenceType(exVarType);
            SSAValue exVar = ctx.newValue(exVarIrType);
            // Capture the JVM-provided exception (on the stack at handler entry) into the catch variable;
            // otherwise it leaks onto the operand stack of whatever follows the catch.
            catchBlock.addInstruction(SimpleInstruction.createCatch(exVar));
            ctx.declareLocal(exVarName, exVarIrType, false);
            ctx.setVariable(exVarName, exVar);
            if (ctx.getLoopStack().isEmpty()) {
                for (String name : reassignedInTry) {
                    ctx.setVariable(name, preTryVars.get(name));
                }
            }

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
            // The try/catch bodies are lowered; the finally copies below are not themselves protected.
            finallyStack.pop();
            // Synthetic catch-all so the finally also runs when an exception escapes the try/catches (javac
            // semantics - otherwise an uncaught throw skips the finally entirely), and so the re-decompile
            // recognizes the finally via the catch(Throwable){ <finally>; throw } pattern
            // (StatementRecoverer.isFinallyRethrowPattern). Registered AFTER the real catches so they take
            // precedence, and covering the catch blocks too so a throw inside a catch still runs the finally.
            java.util.Map<String, SSAValue> normalFinallyVars = ctx.snapshotVariables();
            IRBlock finallyHandler = ctx.createBlock();
            tryBlock.addSuccessor(finallyHandler, com.tonic.analysis.ssa.cfg.EdgeType.EXCEPTION);
            ctx.setCurrentBlock(finallyHandler);
            ReferenceType throwableType = new ReferenceType("java/lang/Throwable");
            SSAValue caught = ctx.newValue(throwableType);
            finallyHandler.addInstruction(SimpleInstruction.createCatch(caught));
            // No declareLocal/named variable for the captured exception: it is only re-thrown (createThrow
            // uses the value directly), and naming it leaks a synthetic local (e.g. $finallyEx) into the LVT
            // that the decompiler then surfaces on a reused slot. The recovery matches the rethrow by slot.
            for (String name : reassignedInTry) {
                ctx.setVariable(name, preTryVars.get(name));
            }
            lower(tryCatch.getFinallyBlock());
            if (ctx.getCurrentBlock().getTerminator() == null) {
                ctx.getCurrentBlock().addInstruction(SimpleInstruction.createThrow(caught));
            }
            Set<IRBlock> finallyProtected = new LinkedHashSet<>(tryBodyBlocks);
            finallyProtected.addAll(catchBlocks);
            ExceptionHandler finallyAll =
                    new ExceptionHandler(tryBlock, tryEnd, finallyHandler, throwableType);
            finallyAll.setTryBlocks(finallyProtected);
            ctx.getIrMethod().addExceptionHandler(finallyAll);
            ctx.restoreVariables(normalFinallyVars);

            ctx.setCurrentBlock(finallyBlock);
            lower(tryCatch.getFinallyBlock());
            if (ctx.getCurrentBlock().getTerminator() == null) {
                ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(exitBlock));
                ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            }
        }

        ctx.setCurrentBlock(exitBlock);
    }

    /**
     * Desugars a try-with-resources into javac's modern pattern so it recompiles to bytecode the decompiler folds
     * back into {@code try (r) { ... }}: the body runs in a protected region, the resource is closed on the normal
     * path (and before every return, via the finally stack), and a cleanup handler closes the resource - chaining a
     * close failure into the in-flight exception with {@code Throwable.addSuppressed} - then rethrows. Several
     * resources nest right to left; a try-with-resources that also has its own catch/finally wraps the resource
     * management in an ordinary try so the existing lowering handles the catch/finally around it.
     */
    private void lowerTryWithResources(TryCatchStmt tryCatch) {
        List<Expression> resources = tryCatch.getResources();
        if (!tryCatch.getCatches().isEmpty() || tryCatch.getFinallyBlock() != null) {
            TryCatchStmt inner = new TryCatchStmt(tryCatch.getTryBlock(), new java.util.ArrayList<>(), null,
                    new java.util.ArrayList<>(resources), tryCatch.getLocation());
            java.util.List<Statement> wrapped = new java.util.ArrayList<>();
            wrapped.add(inner);
            TryCatchStmt outer = new TryCatchStmt(new BlockStmt(wrapped), tryCatch.getCatches(),
                    tryCatch.getFinallyBlock(), new java.util.ArrayList<>(), tryCatch.getLocation());
            lowerTryCatch(outer);
            return;
        }
        lowerResource(resources, 0, tryCatch.getTryBlock());
    }

    private void lowerResource(List<Expression> resources, int idx, Statement innerBody) {
        if (idx >= resources.size()) {
            lower(innerBody);
            return;
        }
        Expression resource = resources.get(idx);
        IRBlock tryBlock = ctx.createBlock();
        IRBlock closeBlock = ctx.createBlock();
        IRBlock handlerBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();

        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(tryBlock));
        ctx.getCurrentBlock().addSuccessor(tryBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(tryBlock);
        int blocksBefore = ctx.getIrMethod().getBlocks().size();
        finallyStack.push(closeCall(resource));
        lowerResource(resources, idx + 1, innerBody);
        finallyStack.pop();
        IRBlock tryEnd = ctx.getCurrentBlock();
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(closeBlock));
            ctx.getCurrentBlock().addSuccessor(closeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        Set<IRBlock> tryBodyBlocks = new LinkedHashSet<>();
        tryBodyBlocks.add(tryBlock);
        List<IRBlock> allBlocks = ctx.getIrMethod().getBlocks();
        for (int i = blocksBefore; i < allBlocks.size(); i++) {
            tryBodyBlocks.add(allBlocks.get(i));
        }

        ctx.setCurrentBlock(closeBlock);
        lower(closeCall(resource));
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(exitBlock));
            ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        tryBlock.addSuccessor(handlerBlock, com.tonic.analysis.ssa.cfg.EdgeType.EXCEPTION);
        ReferenceType throwableType = new ReferenceType("java/lang/Throwable");
        String primaryName = "$twrPrimary$" + idx;
        String suppressedName = "$twrSuppressed$" + idx;
        com.tonic.analysis.source.ast.type.SourceType throwableSrc =
                new com.tonic.analysis.source.ast.type.ReferenceSourceType("java/lang/Throwable");

        IRBlock suppressTryBlock = ctx.createBlock();
        IRBlock suppressCatchBlock = ctx.createBlock();
        IRBlock throwBlock = ctx.createBlock();

        ctx.setCurrentBlock(handlerBlock);
        SSAValue primary = ctx.newValue(throwableType);
        handlerBlock.addInstruction(SimpleInstruction.createCatch(primary));
        ctx.declareLocal(primaryName, throwableType, false);
        ctx.setVariable(primaryName, primary);
        handlerBlock.addInstruction(SimpleInstruction.createGoto(suppressTryBlock));
        handlerBlock.addSuccessor(suppressTryBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        // Close the resource, chaining a close failure into the in-flight exception, then rethrow. The close is the
        // only statement protected by the suppress handler; the rethrow (throwBlock) sits outside it and is reached
        // both when the close succeeds and after a close failure is suppressed - the javac layout the decompiler
        // folds back to try-with-resources. Built directly as blocks so the primary stays a plain slot live to the
        // shared rethrow.
        ctx.setCurrentBlock(suppressTryBlock);
        lower(closeCall(resource));
        IRBlock suppressTryEnd = ctx.getCurrentBlock();
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(throwBlock));
            ctx.getCurrentBlock().addSuccessor(throwBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }
        Set<IRBlock> suppressBlocks = new LinkedHashSet<>();
        suppressBlocks.add(suppressTryBlock);

        suppressTryBlock.addSuccessor(suppressCatchBlock, com.tonic.analysis.ssa.cfg.EdgeType.EXCEPTION);
        ctx.setCurrentBlock(suppressCatchBlock);
        SSAValue suppressed = ctx.newValue(throwableType);
        suppressCatchBlock.addInstruction(SimpleInstruction.createCatch(suppressed));
        ctx.declareLocal(suppressedName, throwableType, false);
        ctx.setVariable(suppressedName, suppressed);
        Expression addSuppressed = new com.tonic.analysis.source.ast.expr.MethodCallExpr(
                new com.tonic.analysis.source.ast.expr.VarRefExpr(primaryName, throwableSrc), "addSuppressed",
                "java/lang/Throwable",
                java.util.List.of(new com.tonic.analysis.source.ast.expr.VarRefExpr(suppressedName, throwableSrc)),
                false, null);
        lower(new ExprStmt(addSuppressed));
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(throwBlock));
            ctx.getCurrentBlock().addSuccessor(throwBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }
        ExceptionHandler suppressHandler =
                new ExceptionHandler(suppressTryBlock, suppressTryEnd, suppressCatchBlock, throwableType);
        suppressHandler.setTryBlocks(suppressBlocks);
        ctx.getIrMethod().addExceptionHandler(suppressHandler);

        ctx.setCurrentBlock(throwBlock);
        throwBlock.addInstruction(SimpleInstruction.createThrow(ctx.getVariable(primaryName)));

        ExceptionHandler handler = new ExceptionHandler(tryBlock, tryEnd, handlerBlock, throwableType);
        handler.setTryBlocks(tryBodyBlocks);
        ctx.getIrMethod().addExceptionHandler(handler);

        ctx.setCurrentBlock(exitBlock);
    }

    /** A {@code resource.close()} statement, with a fresh receiver copy so it can be emitted on several paths. */
    private Statement closeCall(Expression resource) {
        return new ExprStmt(new com.tonic.analysis.source.ast.expr.MethodCallExpr(copyResource(resource), "close",
                resourceOwner(resource), new java.util.ArrayList<>(), false, null));
    }

    private String resourceOwner(Expression resource) {
        com.tonic.analysis.source.ast.type.SourceType type = resource.getType();
        if (type != null) {
            IRType ir = type.toIRType();
            if (ir instanceof ReferenceType) {
                return ((ReferenceType) ir).getInternalName();
            }
        }
        return "java/lang/AutoCloseable";
    }

    private Expression copyResource(Expression resource) {
        if (resource instanceof com.tonic.analysis.source.ast.expr.VarRefExpr) {
            com.tonic.analysis.source.ast.expr.VarRefExpr ref =
                    (com.tonic.analysis.source.ast.expr.VarRefExpr) resource;
            return new com.tonic.analysis.source.ast.expr.VarRefExpr(ref.getName(), ref.getType());
        }
        return resource;
    }

    /**
     * Lowers {@code synchronized (lock) { body }} to javac's monitor scaffolding: a {@code monitorenter}, the
     * body in a protected region that releases the monitor on every exit, and a catch-all handler that
     * releases it and rethrows when an exception escapes. The monitor must be released before each early
     * {@code return}/{@code break} out of the body too (not only on fall-through), so a
     * {@link MonitorExitStmt} is pushed onto the shared finally stack that {@link #lowerReturn} and the
     * abrupt-exit lowering already drain - mirroring how a try/finally's cleanup runs on every path. Emitting
     * the release only on fall-through (the prior behaviour) leaked the monitor whenever the body returned,
     * throwing {@code IllegalMonitorStateException} and dropping the synchronized shape on re-decompilation.
     */
    private void lowerSynchronized(SynchronizedStmt syncStmt) {
        Value monitor = exprLowerer.lower(syncStmt.getLock());

        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createMonitorEnter(monitor));

        IRBlock tryBlock = ctx.createBlock();
        IRBlock exitBlock = ctx.createBlock();
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(tryBlock));
        ctx.getCurrentBlock().addSuccessor(tryBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(tryBlock);
        int blocksBeforeBody = ctx.getIrMethod().getBlocks().size();
        finallyStack.push(new MonitorExitStmt(monitor));
        lower(syncStmt.getBody());
        finallyStack.pop();
        IRBlock tryEnd = ctx.getCurrentBlock();
        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createMonitorExit(monitor));
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(exitBlock));
            ctx.getCurrentBlock().addSuccessor(exitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        }

        Set<IRBlock> bodyBlocks = new LinkedHashSet<>();
        bodyBlocks.add(tryBlock);
        List<IRBlock> allBlocks = ctx.getIrMethod().getBlocks();
        for (int i = blocksBeforeBody; i < allBlocks.size(); i++) {
            bodyBlocks.add(allBlocks.get(i));
        }

        // Catch-all release/rethrow so an exception escaping the body still frees the monitor, and so the
        // re-decompile recognizes the synchronized block via the catch-all monitorexit/rethrow handler.
        IRBlock handler = ctx.createBlock();
        tryBlock.addSuccessor(handler, com.tonic.analysis.ssa.cfg.EdgeType.EXCEPTION);
        ctx.setCurrentBlock(handler);
        SSAValue caught = ctx.newValue(new ReferenceType("java/lang/Throwable"));
        handler.addInstruction(SimpleInstruction.createCatch(caught));
        handler.addInstruction(SimpleInstruction.createMonitorExit(monitor));
        handler.addInstruction(SimpleInstruction.createThrow(caught));
        ExceptionHandler release = new ExceptionHandler(tryBlock, tryEnd, handler, null);
        release.setTryBlocks(bodyBlocks);
        ctx.getIrMethod().addExceptionHandler(release);

        ctx.setCurrentBlock(exitBlock);
    }

    /**
     * A synthetic cleanup pushed onto {@link #finallyStack} for an enclosing {@code synchronized}: lowering
     * it emits a {@code monitorexit} of the block's monitor. It exists only during lowering (it is drained by
     * the abrupt-exit paths, never emitted or visited), so its visitor entry point is unsupported.
     */
    private static final class MonitorExitStmt implements Statement {
        private final Value monitor;
        MonitorExitStmt(Value monitor) {
            this.monitor = monitor;
        }
        @Override
        public com.tonic.analysis.source.ast.ASTNode getParent() {
            return null;
        }
        @Override
        public void setParent(com.tonic.analysis.source.ast.ASTNode parent) {
        }
        @Override
        public com.tonic.analysis.source.ast.SourceLocation getLocation() {
            return com.tonic.analysis.source.ast.SourceLocation.UNKNOWN;
        }
        @Override
        public <T> T accept(com.tonic.analysis.source.visitor.SourceVisitor<T> visitor) {
            throw new UnsupportedOperationException("synthetic monitor-exit cleanup is lowered directly");
        }
    }

    /**
     * Lowers a labeled statement. A label on a loop names that loop's break/continue targets, so it is moved onto
     * the loop before lowering - the loop's own lowering registers the label (via {@code pushLoop}), which is what
     * a labeled {@code break}/{@code continue} inside it resolves against.
     */
    private void lowerLabeled(LabeledStmt labeled) {
        Statement inner = labeled.getStatement();
        String label = labeled.getLabel();
        if (inner instanceof ForStmt && inner.getLabel() == null) {
            ((ForStmt) inner).setLabel(label);
        } else if (inner instanceof WhileStmt && inner.getLabel() == null) {
            ((WhileStmt) inner).setLabel(label);
        } else if (inner instanceof DoWhileStmt && inner.getLabel() == null) {
            ((DoWhileStmt) inner).setLabel(label);
        } else if (inner instanceof ForEachStmt && inner.getLabel() == null) {
            ((ForEachStmt) inner).setLabel(label);
        }
        lower(inner);
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
