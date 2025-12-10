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

        Value firstValue = incomingValues.iterator().next();

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

        if (firstValue instanceof Constant) {
            Constant constant = (Constant) firstValue;
            return new ConstantInstruction(result, constant);
        } else if (firstValue instanceof SSAValue) {
            SSAValue ssaValue = (SSAValue) firstValue;
            return new CopyInstruction(result, ssaValue);
        }

        return null;
    }

    private boolean areSameValue(Value a, Value b) {
        if (a == b) {
            return true;
        }

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
        if (a instanceof LongConstant && b instanceof LongConstant) {
            LongConstant lcA = (LongConstant) a;
            LongConstant lcB = (LongConstant) b;
            return lcA.getValue() == lcB.getValue();
        }
        if (a instanceof FloatConstant && b instanceof FloatConstant) {
            FloatConstant fcA = (FloatConstant) a;
            FloatConstant fcB = (FloatConstant) b;
            return Float.compare(fcA.getValue(), fcB.getValue()) == 0;
        }
        if (a instanceof DoubleConstant && b instanceof DoubleConstant) {
            DoubleConstant dcA = (DoubleConstant) a;
            DoubleConstant dcB = (DoubleConstant) b;
            return Double.compare(dcA.getValue(), dcB.getValue()) == 0;
        }
        if (a instanceof NullConstant && b instanceof NullConstant) {
            return true;
        }

        return false;
    }
}
