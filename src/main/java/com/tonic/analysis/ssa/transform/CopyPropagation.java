package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Propagates copies to eliminate redundant copy instructions.
 */
public class CopyPropagation implements IRTransform {

    /**
     * Gets the name of this transformation.
     *
     * @return the transformation name
     */
    @Override
    public String getName() {
        return "CopyPropagation";
    }

    /**
     * Runs the copy propagation transformation on the specified method.
     *
     * @param method the method to transform
     * @return true if the method was modified
     */
    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;
        Map<SSAValue, Value> copies = new HashMap<>();

        for (IRBlock block : method.getBlocksInOrder()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                Value singleSource = getSingleSource(phi);
                if (singleSource != null) {
                    copies.put(phi.getResult(), singleSource);
                }
            }

            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof CopyInstruction) {
                    CopyInstruction copy = (CopyInstruction) instr;
                    copies.put(copy.getResult(), resolve(copy.getSource(), copies));
                }
            }
        }

        for (IRBlock block : method.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                for (IRBlock pred : new ArrayList<>(phi.getIncomingBlocks())) {
                    Value incoming = phi.getIncoming(pred);
                    Value replacement = resolve(incoming, copies);
                    if (replacement != incoming) {
                        phi.removeIncoming(pred);
                        phi.addIncoming(replacement, pred);
                        changed = true;
                    }
                }
            }

            for (IRInstruction instr : block.getInstructions()) {
                for (Value operand : new ArrayList<>(instr.getOperands())) {
                    Value replacement = resolve(operand, copies);
                    if (replacement != operand) {
                        instr.replaceOperand(operand, replacement);
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    private Value getSingleSource(PhiInstruction phi) {
        Set<Value> sources = new HashSet<>(phi.getIncomingValues().values());
        sources.remove(phi.getResult());
        if (sources.size() == 1) {
            return sources.iterator().next();
        }
        return null;
    }

    /**
     * Follows a chain of copies to its ultimate source. A {@code visited} set makes this terminate even
     * when the chain cycles — mutually referencing phi/copy values (e.g. {@code a -> b -> a} from a loop)
     * would otherwise spin forever; on a cycle it stops at the first repeated value.
     */
    private static Value resolve(Value value, Map<SSAValue, Value> copies) {
        Value current = value;
        Set<SSAValue> visited = new HashSet<>();
        while (current instanceof SSAValue && visited.add((SSAValue) current)) {
            Value next = copies.get((SSAValue) current);
            if (next == null) {
                break;
            }
            current = next;
        }
        return current;
    }
}
