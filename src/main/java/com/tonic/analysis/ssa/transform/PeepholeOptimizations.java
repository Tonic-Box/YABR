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
        if (instr instanceof UnaryOpInstruction unaryOp) {
            return tryOptimizeUnary(unaryOp);
        } else if (instr instanceof BinaryOpInstruction binOp) {
            return tryOptimizeBinary(binOp);
        }
        return null;
    }

    private IRInstruction tryOptimizeUnary(UnaryOpInstruction instr) {
        UnaryOp op = instr.getOp();
        Value operand = instr.getOperand();
        SSAValue result = instr.getResult();

        // Double negation: NEG(NEG(x)) -> x
        if (op == UnaryOp.NEG && operand instanceof SSAValue ssaOperand) {
            IRInstruction def = ssaOperand.getDefinition();
            if (def instanceof UnaryOpInstruction innerUnary && innerUnary.getOp() == UnaryOp.NEG) {
                return new CopyInstruction(result, innerUnary.getOperand());
            }
        }

        return null;
    }

    private IRInstruction tryOptimizeBinary(BinaryOpInstruction instr) {
        BinaryOp op = instr.getOp();
        Value left = instr.getLeft();
        Value right = instr.getRight();
        SSAValue result = instr.getResult();

        // Shift normalization: x << 32 -> x for int (shifts are masked by 31 for int)
        // In Java, int shifts are masked by 31, so x << 32 is x << 0 = x
        if ((op == BinaryOp.SHL || op == BinaryOp.SHR || op == BinaryOp.USHR)) {
            Integer shiftAmount = getIntConstant(right);
            if (shiftAmount != null) {
                // For int: shift amount is masked by 31, so 32 -> 0
                int effective = shiftAmount & 31;
                if (effective == 0) {
                    return new CopyInstruction(result, left);
                }
                // If effective shift is different from original, create normalized instruction
                if (effective != shiftAmount) {
                    return new BinaryOpInstruction(result, op, left, IntConstant.of(effective));
                }
            }
        }

        // x + (-y) -> x - y
        if (op == BinaryOp.ADD && right instanceof SSAValue ssaRight) {
            IRInstruction rightDef = ssaRight.getDefinition();
            if (rightDef instanceof UnaryOpInstruction rightUnary && rightUnary.getOp() == UnaryOp.NEG) {
                return new BinaryOpInstruction(result, BinaryOp.SUB, left, rightUnary.getOperand());
            }
        }

        // (-x) + y -> y - x
        if (op == BinaryOp.ADD && left instanceof SSAValue ssaLeft) {
            IRInstruction leftDef = ssaLeft.getDefinition();
            if (leftDef instanceof UnaryOpInstruction leftUnary && leftUnary.getOp() == UnaryOp.NEG) {
                return new BinaryOpInstruction(result, BinaryOp.SUB, right, leftUnary.getOperand());
            }
        }

        // x - (-y) -> x + y
        if (op == BinaryOp.SUB && right instanceof SSAValue ssaRight) {
            IRInstruction rightDef = ssaRight.getDefinition();
            if (rightDef instanceof UnaryOpInstruction rightUnary && rightUnary.getOp() == UnaryOp.NEG) {
                return new BinaryOpInstruction(result, BinaryOp.ADD, left, rightUnary.getOperand());
            }
        }

        // Consecutive shifts: (x << a) << b -> x << (a + b) when a + b < 32
        if ((op == BinaryOp.SHL || op == BinaryOp.SHR || op == BinaryOp.USHR) && left instanceof SSAValue ssaLeft) {
            Integer outerShift = getIntConstant(right);
            IRInstruction leftDef = ssaLeft.getDefinition();

            if (outerShift != null && leftDef instanceof BinaryOpInstruction innerBin && innerBin.getOp() == op) {
                Integer innerShift = getIntConstant(innerBin.getRight());
                if (innerShift != null) {
                    int totalShift = innerShift + outerShift;
                    // For int, if total >= 32, result depends on operation
                    if (totalShift < 32) {
                        return new BinaryOpInstruction(result, op, innerBin.getLeft(), IntConstant.of(totalShift));
                    }
                }
            }
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
}
