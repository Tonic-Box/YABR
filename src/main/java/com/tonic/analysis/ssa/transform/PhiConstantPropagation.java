package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Phi Constant Propagation optimization transform.
 *
 * Simplifies phi nodes when all incoming values are identical:
 * - phi(10, 10, 10) -> 10 (all paths produce same constant)
 * - phi(x, x, x) -> x (all paths produce same SSA value)
 */
public class PhiConstantPropagation implements IRTransform {

    @Override
    public String getName() {
        return "PhiConstantPropagation";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocks()) {
            List<PhiInstruction> phis = new ArrayList<>(block.getPhiInstructions());

            for (PhiInstruction phi : phis) {
                IRInstruction replacement = trySimplifyPhi(phi);
                if (replacement != null) {
                    replacement.setBlock(block);
                    // Add the replacement as a regular instruction at the start
                    block.insertInstruction(0, replacement);
                    block.removePhi(phi);
                    changed = true;
                }
            }
        }

        return changed;
    }

    private IRInstruction trySimplifyPhi(PhiInstruction phi) {
        Collection<Value> incomingValues = phi.getIncomingValues().values();

        if (incomingValues.isEmpty()) {
            return null;
        }

        // Get the first value to compare against
        Value firstValue = incomingValues.iterator().next();

        // Check if all incoming values are the same
        boolean allSame = true;
        for (Value value : incomingValues) {
            if (!areSameValue(firstValue, value)) {
                allSame = false;
                break;
            }
        }

        if (!allSame) {
            return null;
        }

        SSAValue result = phi.getResult();

        // All values are the same - create appropriate replacement
        if (firstValue instanceof Constant constant) {
            return new ConstantInstruction(result, constant);
        } else if (firstValue instanceof SSAValue ssaValue) {
            return new CopyInstruction(result, ssaValue);
        }

        return null;
    }

    private boolean areSameValue(Value a, Value b) {
        if (a == b) {
            return true;
        }

        // Check if both are the same SSA value by ID
        if (a instanceof SSAValue ssaA && b instanceof SSAValue ssaB) {
            return ssaA.getId() == ssaB.getId();
        }

        // Check if both are constants with same value
        if (a instanceof IntConstant icA && b instanceof IntConstant icB) {
            return icA.getValue() == icB.getValue();
        }
        if (a instanceof LongConstant lcA && b instanceof LongConstant lcB) {
            return lcA.getValue() == lcB.getValue();
        }
        if (a instanceof FloatConstant fcA && b instanceof FloatConstant fcB) {
            return Float.compare(fcA.getValue(), fcB.getValue()) == 0;
        }
        if (a instanceof DoubleConstant dcA && b instanceof DoubleConstant dcB) {
            return Double.compare(dcA.getValue(), dcB.getValue()) == 0;
        }
        if (a instanceof NullConstant && b instanceof NullConstant) {
            return true;
        }

        return false;
    }
}
