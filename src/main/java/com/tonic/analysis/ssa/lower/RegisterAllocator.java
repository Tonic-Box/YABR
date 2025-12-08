package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.analysis.LivenessAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

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
    private int reservedSlotCount; // Slots reserved for parameters, never released

    public RegisterAllocator(IRMethod method, LivenessAnalysis liveness) {
        this.method = method;
        this.liveness = liveness;
        this.allocation = new HashMap<>();
        this.maxLocals = 0;
    }

    public void allocate() {
        List<LiveInterval> intervals = buildIntervals();
        Collections.sort(intervals, Comparator.comparingInt(a -> a.start));

        // Build coalescing groups from phi copies
        Map<SSAValue, SSAValue> coalesceTarget = buildCoalesceGroups();
        Map<SSAValue, LiveInterval> intervalMap = intervals.stream()
            .collect(Collectors.toMap(i -> i.value, i -> i, (a, b) -> a));

        List<LiveInterval> active = new ArrayList<>();
        Set<Integer> freeRegs = new TreeSet<>();

        // Reserve parameter slots - these must not be reused for values of different types
        // For instance methods, slot 0 is 'this' (object reference)
        // Reusing parameter slots can cause type conflicts in the verifier
        reservedSlotCount = method.getParameters().size();
        maxLocals = reservedSlotCount;
        for (int i = 0; i < maxLocals; i++) {
            if (i < method.getParameters().size()) {
                allocation.put(method.getParameters().get(i), i);
            }
        }

        // Pre-allocate phi results FIRST so copies can coalesce to them
        // This is necessary because phi results are defined at merge points which
        // come AFTER predecessor blocks in program order, but copies in predecessors
        // need to write to the phi result's register
        preAllocatePhiResults(intervals, freeRegs);

        for (LiveInterval interval : intervals) {
            SSAValue value = interval.value;

            // Skip values that already have registers (like parameters or phi results)
            if (allocation.containsKey(value)) {
                continue;
            }

            expireOldIntervals(active, freeRegs, interval.start);

            // Don't coalesce phi copies - they need their own slots to avoid conflicts
            // The BytecodeEmitter will handle the load from copy slot and store to phi slot

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

            allocation.put(value, reg);
            active.add(interval);
            active.sort(Comparator.comparingInt(a -> a.end));
        }
    }

    /**
     * Pre-allocates registers for phi results before the main allocation loop.
     * This ensures that when phi copy values are processed, they can be coalesced
     * to the phi result's register.
     */
    private void preAllocatePhiResults(List<LiveInterval> intervals, Set<Integer> freeRegs) {
        Map<SSAValue, List<CopyInfo>> phiCopies = method.getPhiCopyMapping();
        if (phiCopies == null) return;

        // Allocate a register for each phi result
        for (SSAValue phiResult : phiCopies.keySet()) {
            if (allocation.containsKey(phiResult)) continue;

            int reg;
            if (!freeRegs.isEmpty()) {
                reg = freeRegs.iterator().next();
                freeRegs.remove(reg);
            } else {
                reg = maxLocals++;
            }

            if (phiResult.getType().isTwoSlot()) {
                boolean foundConsecutive = false;
                for (int candidate : new ArrayList<>(freeRegs)) {
                    if (freeRegs.contains(candidate + 1)) {
                        reg = candidate;
                        foundConsecutive = true;
                        break;
                    }
                }
                if (!foundConsecutive) {
                    reg = maxLocals;
                    maxLocals += 2;
                }
                freeRegs.remove(reg);
                freeRegs.remove(reg + 1);
                maxLocals = Math.max(maxLocals, reg + 2);
            }

            allocation.put(phiResult, reg);
        }
    }

    /**
     * Builds a mapping from phi copy values to their target phi result values.
     * This allows the allocator to try to place copies in the same register as the phi result.
     */
    private Map<SSAValue, SSAValue> buildCoalesceGroups() {
        Map<SSAValue, SSAValue> coalesce = new HashMap<>();
        Map<SSAValue, List<CopyInfo>> phiCopies = method.getPhiCopyMapping();

        if (phiCopies == null) return coalesce;

        for (Map.Entry<SSAValue, List<CopyInfo>> entry : phiCopies.entrySet()) {
            SSAValue phiResult = entry.getKey();
            for (CopyInfo copy : entry.getValue()) {
                // All copies should try to coalesce with the phi result's register
                coalesce.put(copy.copyValue(), phiResult);
            }
        }

        return coalesce;
    }

    /**
     * Checks if a value can be coalesced into a specific register.
     * Coalescing is safe if the register is not live during this interval.
     */
    private boolean canCoalesce(LiveInterval interval, int reg, List<LiveInterval> active,
                                Map<SSAValue, LiveInterval> intervalMap) {
        // Check if any active interval with this register overlaps
        for (LiveInterval other : active) {
            Integer otherReg = allocation.get(other.value);
            if (otherReg != null && otherReg == reg) {
                // Both using the same register - check for overlap
                if (intervalsOverlap(interval, other)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Checks if two intervals overlap.
     */
    private boolean intervalsOverlap(LiveInterval a, LiveInterval b) {
        return !(a.end < b.start || b.end < a.start);
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

            // Never release reserved slots (parameter slots)
            // These must preserve their types throughout the method
            // For instance methods, slot 0 is 'this' (object reference)
            // Reusing these slots could cause type conflicts after inlining
            if (reg < reservedSlotCount) {
                continue; // Don't release reserved slots
            }

            freeRegs.add(reg);
            if (interval.value.getType().isTwoSlot()) {
                freeRegs.add(reg + 1);
            }
        }
    }

    private List<LiveInterval> buildIntervals() {
        Map<SSAValue, LiveInterval> intervals = new HashMap<>();
        List<IRBlock> rpo = method.getReversePostOrder();

        // First pass: build basic intervals
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

        // Second pass: extend phi result intervals to cover their phi copies
        // This ensures phi result slots aren't reused before all copies complete
        extendPhiResultIntervals(intervals);

        return new ArrayList<>(intervals.values());
    }

    /**
     * Extends phi result intervals to cover all their phi copy definitions.
     * This prevents the phi result's slot from being reused by other values
     * before all phi copies have been executed.
     */
    private void extendPhiResultIntervals(Map<SSAValue, LiveInterval> intervals) {
        Map<SSAValue, List<CopyInfo>> phiCopies = method.getPhiCopyMapping();
        if (phiCopies == null) return;

        for (Map.Entry<SSAValue, List<CopyInfo>> entry : phiCopies.entrySet()) {
            SSAValue phiResult = entry.getKey();
            LiveInterval phiInterval = intervals.get(phiResult);
            if (phiInterval == null) continue;

            // Find the maximum end position among all phi copy values
            for (CopyInfo copyInfo : entry.getValue()) {
                LiveInterval copyInterval = intervals.get(copyInfo.copyValue());
                if (copyInterval != null) {
                    // Extend phi result's interval to cover the copy's position
                    phiInterval.end = Math.max(phiInterval.end, copyInterval.end);
                }
            }
        }
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
