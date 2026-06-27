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
        if (instr instanceof BinaryOpInstruction) {
            BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
            return foldBinary(binOp);
        } else if (instr instanceof UnaryOpInstruction) {
            UnaryOpInstruction unaryOp = (UnaryOpInstruction) instr;
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

        if (left instanceof IntConstant && right instanceof IntConstant) {
            IntConstant l = (IntConstant) left;
            IntConstant r = (IntConstant) right;
            return foldIntBinary(instr.getOp(), l.getValue(), r.getValue());
        } else if (left instanceof LongConstant && right instanceof LongConstant) {
            LongConstant l = (LongConstant) left;
            LongConstant r = (LongConstant) right;
            return foldLongBinary(instr.getOp(), l.getValue(), r.getValue());
        } else if (left instanceof FloatConstant && right instanceof FloatConstant) {
            FloatConstant l = (FloatConstant) left;
            FloatConstant r = (FloatConstant) right;
            return foldFloatBinary(instr.getOp(), l.getValue(), r.getValue());
        } else if (left instanceof DoubleConstant && right instanceof DoubleConstant) {
            DoubleConstant l = (DoubleConstant) left;
            DoubleConstant r = (DoubleConstant) right;
            return foldDoubleBinary(instr.getOp(), l.getValue(), r.getValue());
        }

        return null;
    }

    private Constant resolveConstant(Value value) {
        if (value instanceof Constant) {
            return (Constant) value;
        }
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            IRInstruction def = ssa.getDefinition();
            if (def instanceof ConstantInstruction) {
                ConstantInstruction ci = (ConstantInstruction) def;
                return ci.getConstant();
            }
        }
        return null;
    }

    private Constant foldIntBinary(BinaryOp op, int left, int right) {
        try {
            switch (op) {
                case ADD: return IntConstant.of(left + right);
                case SUB: return IntConstant.of(left - right);
                case MUL: return IntConstant.of(left * right);
                case DIV: return right != 0 ? IntConstant.of(left / right) : null;
                case REM: return right != 0 ? IntConstant.of(left % right) : null;
                case SHL: return IntConstant.of(left << right);
                case SHR: return IntConstant.of(left >> right);
                case USHR: return IntConstant.of(left >>> right);
                case AND: return IntConstant.of(left & right);
                case OR: return IntConstant.of(left | right);
                case XOR: return IntConstant.of(left ^ right);
                default: return null;
            }
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private Constant foldLongBinary(BinaryOp op, long left, long right) {
        try {
            switch (op) {
                case ADD: return LongConstant.of(left + right);
                case SUB: return LongConstant.of(left - right);
                case MUL: return LongConstant.of(left * right);
                case DIV: return right != 0 ? LongConstant.of(left / right) : null;
                case REM: return right != 0 ? LongConstant.of(left % right) : null;
                case SHL: return LongConstant.of(left << right);
                case SHR: return LongConstant.of(left >> right);
                case USHR: return LongConstant.of(left >>> right);
                case AND: return LongConstant.of(left & right);
                case OR: return LongConstant.of(left | right);
                case XOR: return LongConstant.of(left ^ right);
                case LCMP: return IntConstant.of(Long.compare(left, right));
                default: return null;
            }
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private Constant foldFloatBinary(BinaryOp op, float left, float right) {
        switch (op) {
            case ADD: return FloatConstant.of(left + right);
            case SUB: return FloatConstant.of(left - right);
            case MUL: return FloatConstant.of(left * right);
            case DIV: return FloatConstant.of(left / right);
            case REM: return FloatConstant.of(left % right);
            case FCMPL:
            case FCMPG: return IntConstant.of(Float.compare(left, right));
            default: return null;
        }
    }

    private Constant foldDoubleBinary(BinaryOp op, double left, double right) {
        switch (op) {
            case ADD: return DoubleConstant.of(left + right);
            case SUB: return DoubleConstant.of(left - right);
            case MUL: return DoubleConstant.of(left * right);
            case DIV: return DoubleConstant.of(left / right);
            case REM: return DoubleConstant.of(left % right);
            case DCMPL:
            case DCMPG: return IntConstant.of(Double.compare(left, right));
            default: return null;
        }
    }

    private Constant foldUnary(UnaryOpInstruction instr) {
        Constant operand = resolveConstant(instr.getOperand());
        if (operand == null) {
            return null;
        }

        UnaryOp op = instr.getOp();

        if (operand instanceof IntConstant) {
            IntConstant i = (IntConstant) operand;
            int val = i.getValue();
            switch (op) {
                case NEG: return IntConstant.of(-val);
                case I2L: return LongConstant.of(val);
                case I2F: return FloatConstant.of(val);
                case I2D: return DoubleConstant.of(val);
                case I2B: return IntConstant.of((byte) val);
                case I2C: return IntConstant.of((char) val);
                case I2S: return IntConstant.of((short) val);
                default: return null;
            }
        } else if (operand instanceof LongConstant) {
            LongConstant l = (LongConstant) operand;
            long val = l.getValue();
            switch (op) {
                case NEG: return LongConstant.of(-val);
                case L2I: return IntConstant.of((int) val);
                case L2F: return FloatConstant.of(val);
                case L2D: return DoubleConstant.of(val);
                default: return null;
            }
        } else if (operand instanceof FloatConstant) {
            FloatConstant f = (FloatConstant) operand;
            float val = f.getValue();
            switch (op) {
                case NEG: return FloatConstant.of(-val);
                case F2I: return IntConstant.of((int) val);
                case F2L: return LongConstant.of((long) val);
                case F2D: return DoubleConstant.of(val);
                default: return null;
            }
        } else if (operand instanceof DoubleConstant) {
            DoubleConstant d = (DoubleConstant) operand;
            double val = d.getValue();
            switch (op) {
                case NEG: return DoubleConstant.of(-val);
                case D2I: return IntConstant.of((int) val);
                case D2L: return LongConstant.of((long) val);
                case D2F: return FloatConstant.of((float) val);
                default: return null;
            }
        }

        return null;
    }
}
