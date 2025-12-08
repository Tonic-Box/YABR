package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Common Subexpression Elimination (CSE) optimization transform.
 *
 * Identifies identical expressions computed multiple times and reuses
 * the first computed result:
 * - a = x + y; b = x + y; -> a = x + y; b = a;
 *
 * Works within basic blocks (local CSE). For commutative operations,
 * operand order is normalized for matching.
 */
public class CommonSubexpressionElimination implements IRTransform {

    @Override
    public String getName() {
        return "CommonSubexpressionElimination";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocks()) {
            changed |= eliminateInBlock(block);
        }

        return changed;
    }

    private boolean eliminateInBlock(IRBlock block) {
        boolean changed = false;
        // Map from expression key to the SSA value that computes it
        Map<String, SSAValue> expressionMap = new HashMap<>();
        List<IRInstruction> toReplace = new ArrayList<>();
        Map<IRInstruction, SSAValue> replacements = new HashMap<>();

        for (IRInstruction instr : block.getInstructions()) {
            String exprKey = computeExpressionKey(instr);

            if (exprKey != null && instr.getResult() != null) {
                if (expressionMap.containsKey(exprKey)) {
                    // Found a common subexpression - mark for replacement
                    SSAValue existing = expressionMap.get(exprKey);
                    toReplace.add(instr);
                    replacements.put(instr, existing);
                } else {
                    // First occurrence - record it
                    expressionMap.put(exprKey, instr.getResult());
                }
            }
        }

        // Replace common subexpressions with copies
        for (IRInstruction instr : toReplace) {
            SSAValue existing = replacements.get(instr);
            SSAValue result = instr.getResult();
            CopyInstruction copy = new CopyInstruction(result, existing);
            copy.setBlock(block);

            int idx = block.getInstructions().indexOf(instr);
            block.removeInstruction(instr);
            block.insertInstruction(idx, copy);
            changed = true;
        }

        return changed;
    }

    private String computeExpressionKey(IRInstruction instr) {
        if (instr instanceof BinaryOpInstruction binOp) {
            return computeBinaryKey(binOp);
        } else if (instr instanceof UnaryOpInstruction unaryOp) {
            return computeUnaryKey(unaryOp);
        }
        // Other instruction types (field access, method calls, etc.)
        // are not candidates for CSE due to potential side effects
        return null;
    }

    private String computeBinaryKey(BinaryOpInstruction instr) {
        BinaryOp op = instr.getOp();
        String leftKey = getValueKey(instr.getLeft());
        String rightKey = getValueKey(instr.getRight());

        if (leftKey == null || rightKey == null) {
            return null;
        }

        // Normalize commutative operations for matching
        if (isCommutative(op)) {
            // Use lexicographic ordering for canonical form
            if (leftKey.compareTo(rightKey) > 0) {
                String temp = leftKey;
                leftKey = rightKey;
                rightKey = temp;
            }
        }

        return op.name() + "_" + leftKey + "_" + rightKey;
    }

    private String computeUnaryKey(UnaryOpInstruction instr) {
        String operandKey = getValueKey(instr.getOperand());
        if (operandKey == null) {
            return null;
        }
        return instr.getOp().name() + "_" + operandKey;
    }

    private String getValueKey(Value value) {
        if (value instanceof SSAValue ssa) {
            return "v" + ssa.getId();
        } else if (value instanceof IntConstant ic) {
            return "i" + ic.getValue();
        } else if (value instanceof LongConstant lc) {
            return "l" + lc.getValue();
        } else if (value instanceof FloatConstant fc) {
            return "f" + Float.floatToIntBits(fc.getValue());
        } else if (value instanceof DoubleConstant dc) {
            return "d" + Double.doubleToLongBits(dc.getValue());
        } else if (value instanceof NullConstant) {
            return "null";
        }
        return null;
    }

    private boolean isCommutative(BinaryOp op) {
        return switch (op) {
            case ADD, MUL, AND, OR, XOR -> true;
            default -> false;
        };
    }
}
