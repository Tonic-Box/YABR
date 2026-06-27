package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Strength Reduction optimization transform.
 *
 * Replaces expensive operations with cheaper equivalents:
 * - x * 2^n  ->  x << n  (multiplication by power of 2)
 * - x / 2^n  ->  x >> n  (division by power of 2, positive only)
 * - x % 2^n  ->  x & (2^n - 1)  (modulo by power of 2, positive only)
 * - x * 0    ->  0
 * - x * 1    ->  x
 */
public class StrengthReduction implements IRTransform {

    @Override
    public String getName() {
        return "StrengthReduction";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());

            for (int i = 0; i < instructions.size(); i++) {
                IRInstruction instr = instructions.get(i);

                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                    IRInstruction replacement = tryReduce(binOp);
                    if (replacement != null) {
                        replacement.setBlock(block);
                        int idx = block.getInstructions().indexOf(instr);
                        block.removeInstruction(instr);
                        block.insertInstruction(idx, replacement);
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    private IRInstruction tryReduce(BinaryOpInstruction instr) {
        BinaryOp op = instr.getOp();
        Value left = instr.getLeft();
        Value right = instr.getRight();
        SSAValue result = instr.getResult();

        Integer rightConst = getIntConstant(right);
        Integer leftConst = getIntConstant(left);

        switch (op) {
            case MUL:
                if (rightConst != null && isPowerOfTwo(rightConst)) {
                    int shift = Integer.numberOfTrailingZeros(rightConst);
                    return new BinaryOpInstruction(result, BinaryOp.SHL, left,
                            IntConstant.of(shift));
                }
                if (leftConst != null && isPowerOfTwo(leftConst)) {
                    int shift = Integer.numberOfTrailingZeros(leftConst);
                    return new BinaryOpInstruction(result, BinaryOp.SHL, right,
                            IntConstant.of(shift));
                }
                break;

            case DIV:
                if (rightConst != null && rightConst > 0 && isPowerOfTwo(rightConst)) {
                    int shift = Integer.numberOfTrailingZeros(rightConst);
                    return new BinaryOpInstruction(result, BinaryOp.SHR, left,
                            IntConstant.of(shift));
                }
                break;

            case REM:
                if (rightConst != null && rightConst > 0 && isPowerOfTwo(rightConst)) {
                    int mask = rightConst - 1;
                    return new BinaryOpInstruction(result, BinaryOp.AND, left,
                            IntConstant.of(mask));
                }
                break;

            default:
                break;
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

    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}
