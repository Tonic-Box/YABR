package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Algebraic Simplification optimization transform.
 *
 * Applies mathematical identities to simplify expressions:
 * - x + 0 -> x
 * - x - 0 -> x
 * - x * 1 -> x
 * - x * 0 -> 0
 * - x / 1 -> x
 * - x & 0 -> 0
 * - x & -1 -> x (all bits set)
 * - x | 0 -> x
 * - x | -1 -> -1
 * - x ^ 0 -> x
 * - x ^ x -> 0 (same operand)
 * - x - x -> 0 (same operand)
 * - x << 0 -> x
 * - x >> 0 -> x
 * - x >>> 0 -> x
 */
public class AlgebraicSimplification implements IRTransform {

    @Override
    public String getName() {
        return "AlgebraicSimplification";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());

            for (int i = 0; i < instructions.size(); i++) {
                IRInstruction instr = instructions.get(i);

                if (instr instanceof BinaryOpInstruction binOp) {
                    IRInstruction replacement = trySimplify(binOp);
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

    private IRInstruction trySimplify(BinaryOpInstruction instr) {
        BinaryOp op = instr.getOp();
        Value left = instr.getLeft();
        Value right = instr.getRight();
        SSAValue result = instr.getResult();

        Integer leftConst = getIntConstant(left);
        Integer rightConst = getIntConstant(right);

        // Check if operands are the same SSA value (x op x patterns)
        boolean sameOperand = areSameValue(left, right);

        switch (op) {
            case ADD:
                // x + 0 -> x
                if (rightConst != null && rightConst == 0) {
                    return new CopyInstruction(result, left);
                }
                // 0 + x -> x
                if (leftConst != null && leftConst == 0) {
                    return new CopyInstruction(result, right);
                }
                break;

            case SUB:
                // x - 0 -> x
                if (rightConst != null && rightConst == 0) {
                    return new CopyInstruction(result, left);
                }
                // x - x -> 0
                if (sameOperand) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                break;

            case MUL:
                // x * 0 -> 0
                if (rightConst != null && rightConst == 0) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                // 0 * x -> 0
                if (leftConst != null && leftConst == 0) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                // x * 1 -> x
                if (rightConst != null && rightConst == 1) {
                    return new CopyInstruction(result, left);
                }
                // 1 * x -> x
                if (leftConst != null && leftConst == 1) {
                    return new CopyInstruction(result, right);
                }
                break;

            case DIV:
                // x / 1 -> x
                if (rightConst != null && rightConst == 1) {
                    return new CopyInstruction(result, left);
                }
                // x / x -> 1 (careful: x must not be 0)
                // We skip this as we can't guarantee x != 0
                break;

            case REM:
                // x % 1 -> 0
                if (rightConst != null && rightConst == 1) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                break;

            case AND:
                // x & 0 -> 0
                if (rightConst != null && rightConst == 0) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                // 0 & x -> 0
                if (leftConst != null && leftConst == 0) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                // x & -1 -> x (all bits set)
                if (rightConst != null && rightConst == -1) {
                    return new CopyInstruction(result, left);
                }
                // -1 & x -> x
                if (leftConst != null && leftConst == -1) {
                    return new CopyInstruction(result, right);
                }
                // x & x -> x
                if (sameOperand) {
                    return new CopyInstruction(result, left);
                }
                break;

            case OR:
                // x | 0 -> x
                if (rightConst != null && rightConst == 0) {
                    return new CopyInstruction(result, left);
                }
                // 0 | x -> x
                if (leftConst != null && leftConst == 0) {
                    return new CopyInstruction(result, right);
                }
                // x | -1 -> -1
                if (rightConst != null && rightConst == -1) {
                    return new ConstantInstruction(result, IntConstant.of(-1));
                }
                // -1 | x -> -1
                if (leftConst != null && leftConst == -1) {
                    return new ConstantInstruction(result, IntConstant.of(-1));
                }
                // x | x -> x
                if (sameOperand) {
                    return new CopyInstruction(result, left);
                }
                break;

            case XOR:
                // x ^ 0 -> x
                if (rightConst != null && rightConst == 0) {
                    return new CopyInstruction(result, left);
                }
                // 0 ^ x -> x
                if (leftConst != null && leftConst == 0) {
                    return new CopyInstruction(result, right);
                }
                // x ^ x -> 0
                if (sameOperand) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                break;

            case SHL:
            case SHR:
            case USHR:
                // x << 0 -> x, x >> 0 -> x, x >>> 0 -> x
                if (rightConst != null && rightConst == 0) {
                    return new CopyInstruction(result, left);
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

    private boolean areSameValue(Value a, Value b) {
        // Check if both are the same SSA value
        if (a instanceof SSAValue ssaA && b instanceof SSAValue ssaB) {
            return ssaA.getId() == ssaB.getId();
        }
        // Check if both are the same constant
        if (a instanceof IntConstant icA && b instanceof IntConstant icB) {
            return icA.getValue() == icB.getValue();
        }
        return false;
    }
}
