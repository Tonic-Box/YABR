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

                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
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

        boolean sameOperand = areSameValue(left, right);

        switch (op) {
            case ADD:
                if (rightConst != null && rightConst == 0) {
                    return new CopyInstruction(result, left);
                }
                if (leftConst != null && leftConst == 0) {
                    return new CopyInstruction(result, right);
                }
                break;

            case SUB:
                if (rightConst != null && rightConst == 0) {
                    return new CopyInstruction(result, left);
                }
                if (sameOperand) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                break;

            case MUL:
                if (rightConst != null && rightConst == 0) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                if (leftConst != null && leftConst == 0) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                if (rightConst != null && rightConst == 1) {
                    return new CopyInstruction(result, left);
                }
                if (leftConst != null && leftConst == 1) {
                    return new CopyInstruction(result, right);
                }
                break;

            case DIV:
                if (rightConst != null && rightConst == 1) {
                    return new CopyInstruction(result, left);
                }
                break;

            case REM:
                if (rightConst != null && rightConst == 1) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                break;

            case AND:
                if (rightConst != null && rightConst == 0) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                if (leftConst != null && leftConst == 0) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                if (rightConst != null && rightConst == -1) {
                    return new CopyInstruction(result, left);
                }
                if (leftConst != null && leftConst == -1) {
                    return new CopyInstruction(result, right);
                }
                if (sameOperand) {
                    return new CopyInstruction(result, left);
                }
                break;

            case OR:
                if (rightConst != null && rightConst == 0) {
                    return new CopyInstruction(result, left);
                }
                if (leftConst != null && leftConst == 0) {
                    return new CopyInstruction(result, right);
                }
                if (rightConst != null && rightConst == -1) {
                    return new ConstantInstruction(result, IntConstant.of(-1));
                }
                if (leftConst != null && leftConst == -1) {
                    return new ConstantInstruction(result, IntConstant.of(-1));
                }
                if (sameOperand) {
                    return new CopyInstruction(result, left);
                }
                break;

            case XOR:
                if (rightConst != null && rightConst == 0) {
                    return new CopyInstruction(result, left);
                }
                if (leftConst != null && leftConst == 0) {
                    return new CopyInstruction(result, right);
                }
                if (sameOperand) {
                    return new ConstantInstruction(result, IntConstant.of(0));
                }
                break;

            case SHL:
            case SHR:
            case USHR:
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

    private boolean areSameValue(Value a, Value b) {
        if (a instanceof SSAValue && b instanceof SSAValue) {
            SSAValue ssaA = (SSAValue) a;
            SSAValue ssaB = (SSAValue) b;
            return ssaA.getId() == ssaB.getId();
        }
        if (a instanceof IntConstant && b instanceof IntConstant) {
            IntConstant icA = (IntConstant) a;
            IntConstant icB = (IntConstant) b;
            return icA.getValue() == icB.getValue();
        }
        return false;
    }
}
