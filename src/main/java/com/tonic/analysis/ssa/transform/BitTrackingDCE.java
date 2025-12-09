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

        // Phase 1: Seed demanded bits from sinks
        seedDemandedBits(method);

        // Phase 2: Propagate backward through def-use chains
        propagateDemandedBits(method);

        // Phase 3: Eliminate dead operations
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
        // Sinks: instructions where all bits of operands are demanded
        if (instr instanceof ReturnInstruction ret) {
            if (ret.getReturnValue() != null) {
                demandAllBits(ret.getReturnValue());
            }
        } else if (instr instanceof InvokeInstruction invoke) {
            for (Value arg : invoke.getArguments()) {
                demandAllBits(arg);
            }
        } else if (instr instanceof ArrayStoreInstruction store) {
            demandAllBits(store.getArray());
            demandAllBits(store.getIndex());
            demandAllBits(store.getValue());
        } else if (instr instanceof PutFieldInstruction put) {
            demandAllBits(put.getValue());
            if (put.getObjectRef() != null) {
                demandAllBits(put.getObjectRef());
            }
        } else if (instr instanceof BranchInstruction branch) {
            demandAllBits(branch.getLeft());
            if (branch.getRight() != null) {
                demandAllBits(branch.getRight());
            }
        } else if (instr instanceof SwitchInstruction sw) {
            demandAllBits(sw.getKey());
        }
    }

    private void demandAllBits(Value value) {
        if (value instanceof SSAValue ssa) {
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
        if (instr instanceof BinaryOpInstruction bin) {
            propagateBinaryOp(bin, demanded);
        } else if (instr instanceof CopyInstruction copy) {
            if (copy.getSource() instanceof SSAValue src) {
                addDemandedBits(src, demanded);
            }
        } else if (instr instanceof PhiInstruction phi) {
            for (Value v : phi.getOperands()) {
                if (v instanceof SSAValue src) {
                    addDemandedBits(src, demanded);
                }
            }
        } else {
            // Conservative: demand all bits from all operands
            for (Value v : instr.getOperands()) {
                demandAllBits(v);
            }
        }
    }

    private void propagateBinaryOp(BinaryOpInstruction bin, long demanded) {
        Value left = bin.getLeft();
        Value right = bin.getRight();

        switch (bin.getOp()) {
            case AND -> {
                // AND with constant: only demand bits that survive the mask
                if (right instanceof IntConstant ic) {
                    long mask = ic.getValue() & ALL_BITS_INT;
                    if (left instanceof SSAValue src) {
                        addDemandedBits(src, demanded & mask);
                    }
                } else if (right instanceof LongConstant lc) {
                    long mask = lc.getValue();
                    if (left instanceof SSAValue src) {
                        addDemandedBits(src, demanded & mask);
                    }
                } else {
                    propagateBothOperands(left, right, demanded);
                }
            }
            case OR, XOR -> propagateBothOperands(left, right, demanded);
            case SHL -> {
                // Shift left: bits shifted out aren't needed in source
                if (right instanceof IntConstant ic) {
                    int shift = ic.getValue() & 0x1F;
                    if (left instanceof SSAValue src) {
                        addDemandedBits(src, demanded >>> shift);
                    }
                } else {
                    demandAllBits(left);
                    demandAllBits(right);
                }
            }
            case SHR, USHR -> {
                // Shift right: demand bits that shift into demanded positions
                if (right instanceof IntConstant ic) {
                    int shift = ic.getValue() & 0x1F;
                    if (left instanceof SSAValue src) {
                        addDemandedBits(src, demanded << shift);
                    }
                } else {
                    demandAllBits(left);
                    demandAllBits(right);
                }
            }
            default -> {
                // ADD, SUB, MUL, DIV, etc: conservative - demand all bits
                demandAllBits(left);
                demandAllBits(right);
            }
        }
    }

    private void propagateBothOperands(Value left, Value right, long demanded) {
        if (left instanceof SSAValue src) {
            addDemandedBits(src, demanded);
        }
        if (right instanceof SSAValue src) {
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
        // Only consider instructions with results
        if (!instr.hasResult()) return false;

        SSAValue result = instr.getResult();
        long demanded = demandedBits.getOrDefault(result, 0L);

        // If no bits are demanded and instruction has no side effects, it's dead
        if (demanded == 0L && !hasSideEffects(instr)) {
            return true;
        }

        return false;
    }

    private boolean hasSideEffects(IRInstruction instr) {
        return instr instanceof InvokeInstruction
            || instr instanceof PutFieldInstruction
            || instr instanceof ArrayStoreInstruction
            || instr instanceof MonitorEnterInstruction
            || instr instanceof MonitorExitInstruction
            || instr instanceof ThrowInstruction;
    }
}