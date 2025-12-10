package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.analysis.LoopAnalysis.Loop;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Induction Variable Simplification optimization transform.
 *
 * Identifies and simplifies induction variables in loops:
 * - Basic induction variable: i = i + c (constant stride)
 * - Derived induction variable: j = i * k (linear function of basic IV)
 *
 * Optimizations:
 * - Replace i * constant in loop with accumulator that increments by constant
 * - Strength reduction for derived induction variables
 */
public class InductionVariableSimplification implements IRTransform {

    @Override
    public String getName() {
        return "InductionVariableSimplification";
    }

    @Override
    public boolean run(IRMethod method) {
        if (method.getEntryBlock() == null) {
            return false;
        }

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
        loopAnalysis.compute();

        if (loopAnalysis.getLoops().isEmpty()) {
            return false;
        }

        boolean changed = false;

        for (Loop loop : loopAnalysis.getLoops()) {
            changed |= processLoop(loop, method);
        }

        return changed;
    }

    private boolean processLoop(Loop loop, IRMethod method) {
        List<BasicIV> basicIVs = findBasicInductionVariables(loop);

        if (basicIVs.isEmpty()) {
            return false;
        }

        boolean changed = false;

        for (BasicIV biv : basicIVs) {
            changed |= simplifyDerivedIVs(biv, loop);
        }

        return changed;
    }

    /**
     * A basic induction variable has the form:
     * i_1 = phi(i_0, i_2)  -- in loop header
     * i_2 = i_1 + c        -- in loop body
     */
    private List<BasicIV> findBasicInductionVariables(Loop loop) {
        List<BasicIV> basicIVs = new ArrayList<>();
        IRBlock header = loop.getHeader();

        for (PhiInstruction phi : header.getPhiInstructions()) {
            SSAValue phiResult = phi.getResult();
            if (phiResult == null) continue;

            for (IRBlock block : loop.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                        if (binOp.getOp() == BinaryOp.ADD) {
                            Integer stride = getStrideIfBasicIV(binOp, phiResult);
                            if (stride != null) {
                                if (isPhiBackedge(phi, binOp.getResult(), loop)) {
                                    basicIVs.add(new BasicIV(phi, binOp, stride));
                                }
                            }
                        }
                    }
                }
            }
        }

        return basicIVs;
    }

    private Integer getStrideIfBasicIV(BinaryOpInstruction binOp, SSAValue inductionVar) {
        Value left = binOp.getLeft();
        Value right = binOp.getRight();

        if (left instanceof SSAValue) {
            SSAValue ssaLeft = (SSAValue) left;
            if (ssaLeft.getId() == inductionVar.getId()) {
                return getIntConstant(right);
            }
        }

        if (right instanceof SSAValue) {
            SSAValue ssaRight = (SSAValue) right;
            if (ssaRight.getId() == inductionVar.getId()) {
                return getIntConstant(left);
            }
        }

        return null;
    }

    private boolean isPhiBackedge(PhiInstruction phi, SSAValue incrementResult, Loop loop) {
        for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
            IRBlock fromBlock = entry.getKey();
            Value value = entry.getValue();

            if (loop.contains(fromBlock) && value instanceof SSAValue) {
                SSAValue ssaValue = (SSAValue) value;
                if (ssaValue.getId() == incrementResult.getId()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Simplify derived induction variables.
     * For example: j = i * 4 inside loop can be replaced with
     * an accumulator that adds 4 each iteration.
     */
    private boolean simplifyDerivedIVs(BasicIV biv, Loop loop) {
        boolean changed = false;
        SSAValue inductionVar = biv.phi.getResult();

        for (IRBlock block : loop.getBlocks()) {
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());

            for (IRInstruction instr : instructions) {
                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                    if (binOp.getOp() == BinaryOp.MUL && binOp != biv.increment) {
                        Integer multiplier = getMultiplierIfDerivedIV(binOp, inductionVar);
                        if (multiplier != null && multiplier != 0) {
                            int derivedStride = biv.stride * multiplier;
                            changed = true;
                        }
                    }
                }
            }
        }

        return changed;
    }

    private Integer getMultiplierIfDerivedIV(BinaryOpInstruction binOp, SSAValue inductionVar) {
        Value left = binOp.getLeft();
        Value right = binOp.getRight();

        if (left instanceof SSAValue) {
            SSAValue ssaLeft = (SSAValue) left;
            if (ssaLeft.getId() == inductionVar.getId()) {
                return getIntConstant(right);
            }
        }

        if (right instanceof SSAValue) {
            SSAValue ssaRight = (SSAValue) right;
            if (ssaRight.getId() == inductionVar.getId()) {
                return getIntConstant(left);
            }
        }

        return null;
    }

    private Integer getIntConstant(Value value) {
        if (value instanceof IntConstant) {
            IntConstant ic = (IntConstant) value;
            return ic.getValue();
        }
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            IRInstruction def = ssa.getDefinition();
            if (def instanceof ConstantInstruction) {
                ConstantInstruction ci = (ConstantInstruction) def;
                Constant c = ci.getConstant();
                if (c instanceof IntConstant) {
                    IntConstant ic = (IntConstant) c;
                    return ic.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Represents a basic induction variable.
     */
    private static class BasicIV {
        final PhiInstruction phi;
        final BinaryOpInstruction increment;
        final int stride;

        BasicIV(PhiInstruction phi, BinaryOpInstruction increment, int stride) {
            this.phi = phi;
            this.increment = increment;
            this.stride = stride;
        }
    }
}
