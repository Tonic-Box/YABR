package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Peephole optimization transform.
 *
 * Applies small pattern-based optimizations:
 * - Double negation: NEG(NEG(x)) -> x
 * - Shift by type width: x << 32 -> x (for int), x << 64 -> x (for long)
 * - Redundant operations: x + (-y) -> x - y
 * - Consecutive shifts: (x << a) << b -> x << (a + b) when safe
 */
public class PeepholeOptimizations implements IRTransform {

    @Override
    public String getName() {
        return "PeepholeOptimizations";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());

            for (int i = 0; i < instructions.size(); i++) {
                IRInstruction instr = instructions.get(i);
                IRInstruction replacement = tryOptimize(instr);

                if (replacement != null) {
                    replacement.setBlock(block);
                    int idx = block.getInstructions().indexOf(instr);
                    block.removeInstruction(instr);
                    block.insertInstruction(idx, replacement);
                    changed = true;
                }
            }
        }

        return changed;
    }

    private IRInstruction tryOptimize(IRInstruction instr) {
        if (instr instanceof UnaryOpInstruction) {
            UnaryOpInstruction unaryOp = (UnaryOpInstruction) instr;
            return tryOptimizeUnary(unaryOp);
        } else if (instr instanceof BinaryOpInstruction) {
            BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
            return tryOptimizeBinary(binOp);
        }
        return null;
    }

    private IRInstruction tryOptimizeUnary(UnaryOpInstruction instr) {
        UnaryOp op = instr.getOp();
        Value operand = instr.getOperand();
        SSAValue result = instr.getResult();

        if (op == UnaryOp.NEG && operand instanceof SSAValue) {
            SSAValue ssaOperand = (SSAValue) operand;
            IRInstruction def = ssaOperand.getDefinition();
            if (def instanceof UnaryOpInstruction) {
                UnaryOpInstruction innerUnary = (UnaryOpInstruction) def;
                if (innerUnary.getOp() == UnaryOp.NEG) {
                    return new CopyInstruction(result, innerUnary.getOperand());
                }
            }
        }

        return null;
    }

    private IRInstruction tryOptimizeBinary(BinaryOpInstruction instr) {
        BinaryOp op = instr.getOp();
        Value left = instr.getLeft();
        Value right = instr.getRight();
        SSAValue result = instr.getResult();

        if ((op == BinaryOp.SHL || op == BinaryOp.SHR || op == BinaryOp.USHR)) {
            Integer shiftAmount = getIntConstant(right);
            if (shiftAmount != null) {
                int effective = shiftAmount & 31;
                if (effective == 0) {
                    return new CopyInstruction(result, left);
                }
                if (effective != shiftAmount) {
                    return new BinaryOpInstruction(result, op, left, IntConstant.of(effective));
                }
            }
        }

        if (op == BinaryOp.ADD && right instanceof SSAValue) {
            SSAValue ssaRight = (SSAValue) right;
            IRInstruction rightDef = ssaRight.getDefinition();
            if (rightDef instanceof UnaryOpInstruction) {
                UnaryOpInstruction rightUnary = (UnaryOpInstruction) rightDef;
                if (rightUnary.getOp() == UnaryOp.NEG) {
                    return new BinaryOpInstruction(result, BinaryOp.SUB, left, rightUnary.getOperand());
                }
            }
        }

        if (op == BinaryOp.ADD && left instanceof SSAValue) {
            SSAValue ssaLeft = (SSAValue) left;
            IRInstruction leftDef = ssaLeft.getDefinition();
            if (leftDef instanceof UnaryOpInstruction) {
                UnaryOpInstruction leftUnary = (UnaryOpInstruction) leftDef;
                if (leftUnary.getOp() == UnaryOp.NEG) {
                    return new BinaryOpInstruction(result, BinaryOp.SUB, right, leftUnary.getOperand());
                }
            }
        }

        if (op == BinaryOp.SUB && right instanceof SSAValue) {
            SSAValue ssaRight = (SSAValue) right;
            IRInstruction rightDef = ssaRight.getDefinition();
            if (rightDef instanceof UnaryOpInstruction) {
                UnaryOpInstruction rightUnary = (UnaryOpInstruction) rightDef;
                if (rightUnary.getOp() == UnaryOp.NEG) {
                    return new BinaryOpInstruction(result, BinaryOp.ADD, left, rightUnary.getOperand());
                }
            }
        }

        if ((op == BinaryOp.SHL || op == BinaryOp.SHR || op == BinaryOp.USHR) && left instanceof SSAValue) {
            SSAValue ssaLeft = (SSAValue) left;
            Integer outerShift = getIntConstant(right);
            IRInstruction leftDef = ssaLeft.getDefinition();

            if (outerShift != null && leftDef instanceof BinaryOpInstruction) {
                BinaryOpInstruction innerBin = (BinaryOpInstruction) leftDef;
                if (innerBin.getOp() == op) {
                    Integer innerShift = getIntConstant(innerBin.getRight());
                    if (innerShift != null) {
                        int totalShift = innerShift + outerShift;
                        if (totalShift < 32) {
                            return new BinaryOpInstruction(result, op, innerBin.getLeft(), IntConstant.of(totalShift));
                        }
                    }
                }
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
}
