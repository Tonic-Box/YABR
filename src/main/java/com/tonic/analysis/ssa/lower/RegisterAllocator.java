package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.analysis.LivenessAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import lombok.Getter;

import java.util.*;

/**
 * Linear scan register allocator.
 * Assigns local variable slots to SSA values.
 */
@Getter
public class RegisterAllocator {

    private final IRMethod method;
    private final LivenessAnalysis liveness;
    private Map<SSAValue, Integer> allocation;
    private int maxLocals;

    public RegisterAllocator(IRMethod method, LivenessAnalysis liveness) {
        this.method = method;
        this.liveness = liveness;
        this.allocation = new HashMap<>();
        this.maxLocals = 0;
    }

    public void allocate() {
        List<LiveInterval> intervals = buildIntervals();
        Collections.sort(intervals, Comparator.comparingInt(a -> a.start));

        List<LiveInterval> active = new ArrayList<>();
        Set<Integer> freeRegs = new TreeSet<>();

        maxLocals = method.getParameters().size();
        for (int i = 0; i < maxLocals; i++) {
            if (i < method.getParameters().size()) {
                allocation.put(method.getParameters().get(i), i);
            }
        }

        for (LiveInterval interval : intervals) {
            // Skip values that already have registers (like parameters)
            if (allocation.containsKey(interval.value)) {
                continue;
            }

            expireOldIntervals(active, freeRegs, interval.start);

            int reg;
            if (!freeRegs.isEmpty()) {
                reg = freeRegs.iterator().next();
                freeRegs.remove(reg);
            } else {
                reg = maxLocals++;
            }

            if (interval.value.getType().isTwoSlot()) {
                // For two-slot values, try to find consecutive free registers
                // or allocate new ones at the end
                boolean foundConsecutive = false;
                for (int candidate : new ArrayList<>(freeRegs)) {
                    if (freeRegs.contains(candidate + 1)) {
                        reg = candidate;
                        foundConsecutive = true;
                        break;
                    }
                }
                if (!foundConsecutive) {
                    // No consecutive free registers, allocate at end
                    reg = maxLocals;
                    maxLocals += 2;
                }
                freeRegs.remove(reg);
                freeRegs.remove(reg + 1);
                maxLocals = Math.max(maxLocals, reg + 2);
            }

            allocation.put(interval.value, reg);
            active.add(interval);
            active.sort(Comparator.comparingInt(a -> a.end));
        }
    }

    private boolean isConsecutiveFree(Set<Integer> freeRegs, int reg) {
        return freeRegs.contains(reg) && freeRegs.contains(reg + 1);
    }

    private void expireOldIntervals(List<LiveInterval> active, Set<Integer> freeRegs, int currentPos) {
        Iterator<LiveInterval> it = active.iterator();
        while (it.hasNext()) {
            LiveInterval interval = it.next();
            if (interval.end >= currentPos) break;

            it.remove();
            int reg = allocation.get(interval.value);
            freeRegs.add(reg);
            if (interval.value.getType().isTwoSlot()) {
                freeRegs.add(reg + 1);
            }
        }
    }

    private List<LiveInterval> buildIntervals() {
        Map<SSAValue, LiveInterval> intervals = new HashMap<>();
        List<IRBlock> rpo = method.getReversePostOrder();

        int pos = 0;
        for (IRBlock block : rpo) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (phi.getResult() != null) {
                    updateInterval(intervals, phi.getResult(), pos);
                }
                pos++;
            }
            for (IRInstruction instr : block.getInstructions()) {
                if (instr.getResult() != null) {
                    updateInterval(intervals, instr.getResult(), pos);
                }
                for (com.tonic.analysis.ssa.value.Value operand : instr.getOperands()) {
                    if (operand instanceof SSAValue ssa) {
                        updateInterval(intervals, ssa, pos);
                    }
                }
                pos++;
            }
        }

        return new ArrayList<>(intervals.values());
    }

    private void updateInterval(Map<SSAValue, LiveInterval> intervals, SSAValue value, int pos) {
        LiveInterval interval = intervals.get(value);
        if (interval == null) {
            interval = new LiveInterval(value, pos, pos);
            intervals.put(value, interval);
        } else {
            interval.end = Math.max(interval.end, pos);
        }
    }

    public int getRegister(SSAValue value) {
        return allocation.getOrDefault(value, -1);
    }

    private static class LiveInterval {
        final SSAValue value;
        int start;
        int end;

        LiveInterval(SSAValue value, int start, int end) {
            this.value = value;
            this.start = start;
            this.end = end;
        }
    }
}
