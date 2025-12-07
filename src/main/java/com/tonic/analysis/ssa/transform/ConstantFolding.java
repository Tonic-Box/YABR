package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Folds constant expressions at compile time.
 */
public class ConstantFolding implements IRTransform {

    /**
     * Gets the name of this transformation.
     *
     * @return the transformation name
     */
    @Override
    public String getName() {
        return "ConstantFolding";
    }

    /**
     * Runs the constant folding transformation on the specified method.
     *
     * @param method the method to transform
     * @return true if the method was modified
     */
    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());

            for (int i = 0; i < instructions.size(); i++) {
                IRInstruction instr = instructions.get(i);
                Constant folded = tryFold(instr);

                if (folded != null && instr.getResult() != null) {
                    SSAValue result = instr.getResult();
                    ConstantInstruction constInstr = new ConstantInstruction(result, folded);
                    constInstr.setBlock(block);

                    int idx = block.getInstructions().indexOf(instr);
                    block.removeInstruction(instr);
                    block.insertInstruction(idx, constInstr);
                    changed = true;
                }
            }
        }

        return changed;
    }

    private Constant tryFold(IRInstruction instr) {
        if (instr instanceof BinaryOpInstruction binOp) {
            return foldBinary(binOp);
        } else if (instr instanceof UnaryOpInstruction unaryOp) {
            return foldUnary(unaryOp);
        }
        return null;
    }

    private Constant foldBinary(BinaryOpInstruction instr) {
        Constant left = resolveConstant(instr.getLeft());
        Constant right = resolveConstant(instr.getRight());

        if (left == null || right == null) {
            return null;
        }

        if (left instanceof IntConstant l && right instanceof IntConstant r) {
            return foldIntBinary(instr.getOp(), l.getValue(), r.getValue());
        } else if (left instanceof LongConstant l && right instanceof LongConstant r) {
            return foldLongBinary(instr.getOp(), l.getValue(), r.getValue());
        } else if (left instanceof FloatConstant l && right instanceof FloatConstant r) {
            return foldFloatBinary(instr.getOp(), l.getValue(), r.getValue());
        } else if (left instanceof DoubleConstant l && right instanceof DoubleConstant r) {
            return foldDoubleBinary(instr.getOp(), l.getValue(), r.getValue());
        }

        return null;
    }

    private Constant resolveConstant(Value value) {
        if (value instanceof Constant c) {
            return c;
        }
        if (value instanceof SSAValue ssa) {
            IRInstruction def = ssa.getDefinition();
            if (def instanceof ConstantInstruction ci) {
                return ci.getConstant();
            }
        }
        return null;
    }

    private Constant foldIntBinary(BinaryOp op, int left, int right) {
        try {
            return switch (op) {
                case ADD -> IntConstant.of(left + right);
                case SUB -> IntConstant.of(left - right);
                case MUL -> IntConstant.of(left * right);
                case DIV -> right != 0 ? IntConstant.of(left / right) : null;
                case REM -> right != 0 ? IntConstant.of(left % right) : null;
                case SHL -> IntConstant.of(left << right);
                case SHR -> IntConstant.of(left >> right);
                case USHR -> IntConstant.of(left >>> right);
                case AND -> IntConstant.of(left & right);
                case OR -> IntConstant.of(left | right);
                case XOR -> IntConstant.of(left ^ right);
                default -> null;
            };
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private Constant foldLongBinary(BinaryOp op, long left, long right) {
        try {
            return switch (op) {
                case ADD -> LongConstant.of(left + right);
                case SUB -> LongConstant.of(left - right);
                case MUL -> LongConstant.of(left * right);
                case DIV -> right != 0 ? LongConstant.of(left / right) : null;
                case REM -> right != 0 ? LongConstant.of(left % right) : null;
                case SHL -> LongConstant.of(left << right);
                case SHR -> LongConstant.of(left >> right);
                case USHR -> LongConstant.of(left >>> right);
                case AND -> LongConstant.of(left & right);
                case OR -> LongConstant.of(left | right);
                case XOR -> LongConstant.of(left ^ right);
                case LCMP -> IntConstant.of(Long.compare(left, right));
                default -> null;
            };
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private Constant foldFloatBinary(BinaryOp op, float left, float right) {
        return switch (op) {
            case ADD -> FloatConstant.of(left + right);
            case SUB -> FloatConstant.of(left - right);
            case MUL -> FloatConstant.of(left * right);
            case DIV -> FloatConstant.of(left / right);
            case REM -> FloatConstant.of(left % right);
            case FCMPL, FCMPG -> IntConstant.of(Float.compare(left, right));
            default -> null;
        };
    }

    private Constant foldDoubleBinary(BinaryOp op, double left, double right) {
        return switch (op) {
            case ADD -> DoubleConstant.of(left + right);
            case SUB -> DoubleConstant.of(left - right);
            case MUL -> DoubleConstant.of(left * right);
            case DIV -> DoubleConstant.of(left / right);
            case REM -> DoubleConstant.of(left % right);
            case DCMPL, DCMPG -> IntConstant.of(Double.compare(left, right));
            default -> null;
        };
    }

    private Constant foldUnary(UnaryOpInstruction instr) {
        Constant operand = resolveConstant(instr.getOperand());
        if (operand == null) {
            return null;
        }

        UnaryOp op = instr.getOp();

        if (operand instanceof IntConstant i) {
            int val = i.getValue();
            return switch (op) {
                case NEG -> IntConstant.of(-val);
                case I2L -> LongConstant.of(val);
                case I2F -> FloatConstant.of(val);
                case I2D -> DoubleConstant.of(val);
                case I2B -> IntConstant.of((byte) val);
                case I2C -> IntConstant.of((char) val);
                case I2S -> IntConstant.of((short) val);
                default -> null;
            };
        } else if (operand instanceof LongConstant l) {
            long val = l.getValue();
            return switch (op) {
                case NEG -> LongConstant.of(-val);
                case L2I -> IntConstant.of((int) val);
                case L2F -> FloatConstant.of(val);
                case L2D -> DoubleConstant.of(val);
                default -> null;
            };
        } else if (operand instanceof FloatConstant f) {
            float val = f.getValue();
            return switch (op) {
                case NEG -> FloatConstant.of(-val);
                case F2I -> IntConstant.of((int) val);
                case F2L -> LongConstant.of((long) val);
                case F2D -> DoubleConstant.of(val);
                default -> null;
            };
        } else if (operand instanceof DoubleConstant d) {
            double val = d.getValue();
            return switch (op) {
                case NEG -> DoubleConstant.of(-val);
                case D2I -> IntConstant.of((int) val);
                case D2L -> LongConstant.of((long) val);
                case D2F -> FloatConstant.of((float) val);
                default -> null;
            };
        }

        return null;
    }
}
