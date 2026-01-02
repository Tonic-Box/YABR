package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Bit-Tracking Dead Code Elimination (BDCE).
 * Tracks which bits of a value are actually used downstream and
 * eliminates operations on bits that are never used.
 */
public class BitTrackingDCE implements IRTransform {

    private static final long ALL_BITS_INT = 0xFFFFFFFFL;
    private static final long ALL_BITS_LONG = 0xFFFFFFFFFFFFFFFFL;

    private Map<SSAValue, Long> demandedBits;
    private Set<SSAValue> inWorklist;

    @Override
    public String getName() {
        return "BitTrackingDCE";
    }

    @Override
    public boolean run(IRMethod method) {
        demandedBits = new HashMap<>();
        inWorklist = new HashSet<>();

        seedDemandedBits(method);

        propagateDemandedBits(method);

        return eliminateDeadOps(method);
    }

    private void seedDemandedBits(IRMethod method) {
        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                seedFromInstruction(instr);
            }
        }
    }

    private void seedFromInstruction(IRInstruction instr) {
        if (instr instanceof ReturnInstruction) {
            ReturnInstruction ret = (ReturnInstruction) instr;
            if (ret.getReturnValue() != null) {
                demandAllBits(ret.getReturnValue());
            }
        } else if (instr instanceof InvokeInstruction) {
            InvokeInstruction invoke = (InvokeInstruction) instr;
            for (Value arg : invoke.getArguments()) {
                demandAllBits(arg);
            }
        } else if (instr instanceof ArrayAccessInstruction) {
            ArrayAccessInstruction access = (ArrayAccessInstruction) instr;
            if (access.isStore()) {
                demandAllBits(access.getArray());
                demandAllBits(access.getIndex());
                demandAllBits(access.getValue());
            }
        } else if (instr instanceof FieldAccessInstruction) {
            FieldAccessInstruction access = (FieldAccessInstruction) instr;
            if (access.isStore()) {
                demandAllBits(access.getValue());
                if (access.getObjectRef() != null) {
                    demandAllBits(access.getObjectRef());
                }
            }
        } else if (instr instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) instr;
            demandAllBits(branch.getLeft());
            if (branch.getRight() != null) {
                demandAllBits(branch.getRight());
            }
        } else if (instr instanceof SwitchInstruction) {
            SwitchInstruction sw = (SwitchInstruction) instr;
            demandAllBits(sw.getKey());
        }
    }

    private void demandAllBits(Value value) {
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            long allBits = getAllBitsForType(ssa);
            addDemandedBits(ssa, allBits);
        }
    }

    private long getAllBitsForType(SSAValue ssa) {
        if (ssa.getType() == PrimitiveType.LONG) {
            return ALL_BITS_LONG;
        }
        return ALL_BITS_INT;
    }

    private void addDemandedBits(SSAValue ssa, long bits) {
        long current = demandedBits.getOrDefault(ssa, 0L);
        long newBits = current | bits;
        if (newBits != current) {
            demandedBits.put(ssa, newBits);
            inWorklist.add(ssa);
        }
    }

    private void propagateDemandedBits(IRMethod method) {
        while (!inWorklist.isEmpty()) {
            SSAValue ssa = inWorklist.iterator().next();
            inWorklist.remove(ssa);

            IRInstruction def = ssa.getDefinition();
            if (def == null) continue;

            long demanded = demandedBits.getOrDefault(ssa, 0L);
            propagateToOperands(def, demanded);
        }
    }

    private void propagateToOperands(IRInstruction instr, long demanded) {
        if (instr instanceof BinaryOpInstruction) {
            BinaryOpInstruction bin = (BinaryOpInstruction) instr;
            propagateBinaryOp(bin, demanded);
        } else if (instr instanceof CopyInstruction) {
            CopyInstruction copy = (CopyInstruction) instr;
            if (copy.getSource() instanceof SSAValue) {
                SSAValue src = (SSAValue) copy.getSource();
                addDemandedBits(src, demanded);
            }
        } else if (instr instanceof PhiInstruction) {
            PhiInstruction phi = (PhiInstruction) instr;
            for (Value v : phi.getOperands()) {
                if (v instanceof SSAValue) {
                    SSAValue src = (SSAValue) v;
                    addDemandedBits(src, demanded);
                }
            }
        } else {
            for (Value v : instr.getOperands()) {
                demandAllBits(v);
            }
        }
    }

    private void propagateBinaryOp(BinaryOpInstruction bin, long demanded) {
        Value left = bin.getLeft();
        Value right = bin.getRight();

        switch (bin.getOp()) {
            case AND: {
                if (right instanceof IntConstant) {
                    IntConstant ic = (IntConstant) right;
                    long mask = ic.getValue() & ALL_BITS_INT;
                    if (left instanceof SSAValue) {
                        SSAValue src = (SSAValue) left;
                        addDemandedBits(src, demanded & mask);
                    }
                } else if (right instanceof LongConstant) {
                    LongConstant lc = (LongConstant) right;
                    long mask = lc.getValue();
                    if (left instanceof SSAValue) {
                        SSAValue src = (SSAValue) left;
                        addDemandedBits(src, demanded & mask);
                    }
                } else {
                    propagateBothOperands(left, right, demanded);
                }
                break;
            }
            case OR:
            case XOR:
                propagateBothOperands(left, right, demanded);
                break;
            case SHL: {
                if (right instanceof IntConstant) {
                    IntConstant ic = (IntConstant) right;
                    int shift = ic.getValue() & 0x1F;
                    if (left instanceof SSAValue) {
                        SSAValue src = (SSAValue) left;
                        addDemandedBits(src, demanded >>> shift);
                    }
                } else {
                    demandAllBits(left);
                    demandAllBits(right);
                }
                break;
            }
            case SHR:
            case USHR: {
                if (right instanceof IntConstant) {
                    IntConstant ic = (IntConstant) right;
                    int shift = ic.getValue() & 0x1F;
                    if (left instanceof SSAValue) {
                        SSAValue src = (SSAValue) left;
                        addDemandedBits(src, demanded << shift);
                    }
                } else {
                    demandAllBits(left);
                    demandAllBits(right);
                }
                break;
            }
            default: {
                demandAllBits(left);
                demandAllBits(right);
                break;
            }
        }
    }

    private void propagateBothOperands(Value left, Value right, long demanded) {
        if (left instanceof SSAValue) {
            SSAValue src = (SSAValue) left;
            addDemandedBits(src, demanded);
        }
        if (right instanceof SSAValue) {
            SSAValue src = (SSAValue) right;
            addDemandedBits(src, demanded);
        }
    }

    private boolean eliminateDeadOps(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> toRemove = new ArrayList<>();

            for (IRInstruction instr : block.getInstructions()) {
                if (isDeadInstruction(instr)) {
                    toRemove.add(instr);
                }
            }

            for (IRInstruction instr : toRemove) {
                block.removeInstruction(instr);
                changed = true;
            }
        }

        return changed;
    }

    private boolean isDeadInstruction(IRInstruction instr) {
        if (!instr.hasResult()) return false;

        SSAValue result = instr.getResult();
        long demanded = demandedBits.getOrDefault(result, 0L);

        if (demanded == 0L && !hasSideEffects(instr)) {
            return true;
        }

        return false;
    }

    private boolean hasSideEffects(IRInstruction instr) {
        if (instr instanceof InvokeInstruction) return true;

        if (instr instanceof FieldAccessInstruction) {
            FieldAccessInstruction access = (FieldAccessInstruction) instr;
            return access.isStore();
        }
        if (instr instanceof ArrayAccessInstruction) {
            ArrayAccessInstruction access = (ArrayAccessInstruction) instr;
            return access.isStore();
        }
        if (instr instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) instr;
            SimpleOp op = simple.getOp();
            return op == SimpleOp.MONITORENTER || op == SimpleOp.MONITOREXIT || op == SimpleOp.ATHROW;
        }

        return false;
    }
}