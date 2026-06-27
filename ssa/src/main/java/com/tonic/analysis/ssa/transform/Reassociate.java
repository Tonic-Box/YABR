package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BinaryOp;
import com.tonic.analysis.ssa.ir.BinaryOpInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Reassociate optimization - reorders commutative operations to group constants
 * together, enabling better constant folding.
 *
 * Example: (x + 5) + 10 → x + (5 + 10) → x + 15
 *
 * This is a simple version that only swaps operands of single binary ops
 * to put constants on the right (canonical form for other optimizations).
 */
public class Reassociate implements IRTransform {

    private Map<Value, Integer> rankMap;

    @Override
    public String getName() {
        return "Reassociate";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        computeRanks(method);

        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());

            for (IRInstruction instr : instructions) {
                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                    if (isCommutative(binOp.getOp())) {
                        if (canonicalize(binOp)) {
                            changed = true;
                        }
                    }
                }
            }
        }

        return changed;
    }

    private void computeRanks(IRMethod method) {
        rankMap = new HashMap<>();

        for (SSAValue param : method.getParameters()) {
            rankMap.put(param, 1);
        }

        int blockRank = 2;
        for (IRBlock block : method.getBlocksInOrder()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr.getResult() != null) {
                    rankMap.put(instr.getResult(), blockRank);
                }
            }
            blockRank++;
        }
    }

    private int getRank(Value v) {
        if (v instanceof Constant) {
            return 0;
        }
        return rankMap.getOrDefault(v, 1);
    }

    private boolean isCommutative(BinaryOp op) {
        return op == BinaryOp.ADD || op == BinaryOp.MUL ||
               op == BinaryOp.AND || op == BinaryOp.OR || op == BinaryOp.XOR;
    }

    /**
     * Canonicalize a binary op by putting higher-rank operand on left.
     * This puts constants on the right, making patterns easier to match.
     */
    private boolean canonicalize(BinaryOpInstruction binOp) {
        Value left = binOp.getLeft();
        Value right = binOp.getRight();

        int leftRank = getRank(left);
        int rightRank = getRank(right);

        if (leftRank < rightRank) {
            SSAValue result = binOp.getResult();
            IRBlock block = binOp.getBlock();

            BinaryOpInstruction newInstr = new BinaryOpInstruction(
                result, binOp.getOp(), right, left
            );
            newInstr.setBlock(block);

            int idx = block.getInstructions().indexOf(binOp);
            if (idx >= 0) {
                block.removeInstruction(binOp);
                block.insertInstruction(idx, newInstr);
                return true;
            }
        }

        return false;
    }
}
