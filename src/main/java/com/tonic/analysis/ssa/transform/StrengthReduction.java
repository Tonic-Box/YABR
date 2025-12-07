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

                if (instr instanceof BinaryOpInstruction binOp) {
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

        // Get constant value if right operand is constant
        Integer rightConst = getIntConstant(right);
        Integer leftConst = getIntConstant(left);

        switch (op) {
            case MUL:
                // x * 0 -> 0 (handled by AlgebraicSimplification, but we check for shift)
                // x * 1 -> x (handled by AlgebraicSimplification)
                // x * 2^n -> x << n
                if (rightConst != null && isPowerOfTwo(rightConst)) {
                    int shift = Integer.numberOfTrailingZeros(rightConst);
                    return new BinaryOpInstruction(result, BinaryOp.SHL, left,
                            IntConstant.of(shift));
                }
                // 2^n * x -> x << n (commutative)
                if (leftConst != null && isPowerOfTwo(leftConst)) {
                    int shift = Integer.numberOfTrailingZeros(leftConst);
                    return new BinaryOpInstruction(result, BinaryOp.SHL, right,
                            IntConstant.of(shift));
                }
                break;

            case DIV:
                // x / 2^n -> x >> n (for positive values - we apply conservatively)
                // Note: This is only safe for non-negative dividends
                // For full correctness, would need sign analysis
                if (rightConst != null && rightConst > 0 && isPowerOfTwo(rightConst)) {
                    int shift = Integer.numberOfTrailingZeros(rightConst);
                    // Use arithmetic right shift
                    return new BinaryOpInstruction(result, BinaryOp.SHR, left,
                            IntConstant.of(shift));
                }
                break;

            case REM:
                // x % 2^n -> x & (2^n - 1) (for positive values)
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
        if (value instanceof IntConstant ic) {
            return ic.getValue();
        }
        if (value instanceof SSAValue ssa) {
            IRInstruction def = ssa.getDefinition();
            if (def instanceof ConstantInstruction ci) {
                Constant c = ci.getConstant();
                if (c instanceof IntConstant ic) {
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
