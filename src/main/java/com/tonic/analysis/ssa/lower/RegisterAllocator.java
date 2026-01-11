package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.analysis.LivenessAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.CopyInstruction;
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
    private final Map<SSAValue, Integer> allocation;
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
        intervals.sort(Comparator.comparingInt(a -> a.start));

        List<LiveInterval> active = new ArrayList<>();
        Set<Integer> freeRegs = new TreeSet<>();

        // Reserve parameter slots - these must not be reused for values of different types
        // For instance methods, slot 0 is 'this' (object reference)
        // Reusing parameter slots can cause type conflicts in the verifier
        // NOTE: long/double parameters take 2 slots each, so we must calculate actual slot count
        int slotIndex = 0;
        for (SSAValue param : method.getParameters()) {
            allocation.put(param, slotIndex);
            slotIndex++;
            if (param.getType().isTwoSlot()) {
                slotIndex++;  // Long/double take 2 slots
            }
        }
        reservedSlotCount = slotIndex;
        maxLocals = slotIndex;

        preAllocatePhiResults(freeRegs);
        assignPhiCopiesToPhiResultSlots();

        for (LiveInterval interval : intervals) {
            SSAValue value = interval.value;
            if (allocation.containsKey(value)) {
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

            allocation.put(value, reg);
            active.add(interval);
            active.sort(Comparator.comparingInt(a -> a.end));
        }
    }

    /**
     * Pre-allocates registers for phi results before the main allocation loop.
     * For nested phis (where an outer phi's incoming value is an inner phi result),
     * we coalesce them to the same slot to prevent type inconsistencies at merge points.
     * <p>
     * Uses Union-Find to handle cycles correctly (e.g., nested loops where inner and
     * outer count phis reference each other).
     */
    private void preAllocatePhiResults(Set<Integer> freeRegs) {
        Map<SSAValue, List<CopyInfo>> phiCopies = method.getPhiCopyMapping();
        if (phiCopies == null) return;

        Set<SSAValue> allPhiResults = phiCopies.keySet();
        Map<SSAValue, SSAValue> coalescingMap = buildPhiCoalescingMap(allPhiResults);

        Map<SSAValue, SSAValue> parent = new HashMap<>();
        for (SSAValue phi : allPhiResults) {
            parent.put(phi, phi);
        }

        for (Map.Entry<SSAValue, SSAValue> entry : coalescingMap.entrySet()) {
            union(parent, entry.getKey(), entry.getValue());
        }

        Map<SSAValue, Integer> representativeSlots = new HashMap<>();
        for (SSAValue phiResult : allPhiResults) {
            SSAValue representative = find(parent, phiResult);

            if (representativeSlots.containsKey(representative)) {
                allocation.put(phiResult, representativeSlots.get(representative));
            } else {
                int reg = allocateSlot(phiResult.getType().isTwoSlot(), freeRegs);
                representativeSlots.put(representative, reg);
                allocation.put(phiResult, reg);
            }
        }
    }

    private SSAValue find(Map<SSAValue, SSAValue> parent, SSAValue x) {
        if (!parent.get(x).equals(x)) {
            parent.put(x, find(parent, parent.get(x)));
        }
        return parent.get(x);
    }

    private void union(Map<SSAValue, SSAValue> parent, SSAValue x, SSAValue y) {
        SSAValue rootX = find(parent, x);
        SSAValue rootY = find(parent, y);
        if (!rootX.equals(rootY)) {
            parent.put(rootX, rootY);
        }
    }

    private void assignPhiCopiesToPhiResultSlots() {
        Map<SSAValue, List<CopyInfo>> phiCopies = method.getPhiCopyMapping();
        if (phiCopies == null) return;

        for (Map.Entry<SSAValue, List<CopyInfo>> entry : phiCopies.entrySet()) {
            SSAValue phiResult = entry.getKey();
            Integer phiSlot = allocation.get(phiResult);
            if (phiSlot == null) continue;

            for (CopyInfo copyInfo : entry.getValue()) {
                allocation.put(copyInfo.copyValue(), phiSlot);
            }
        }
    }

    private Map<SSAValue, SSAValue> buildPhiCoalescingMap(Set<SSAValue> allPhiResults) {
        Map<SSAValue, SSAValue> coalescingMap = new HashMap<>();
        Map<SSAValue, List<CopyInfo>> phiCopies = method.getPhiCopyMapping();
        if (phiCopies == null) return coalescingMap;

        for (Map.Entry<SSAValue, List<CopyInfo>> entry : phiCopies.entrySet()) {
            SSAValue phiResult = entry.getKey();
            for (CopyInfo copyInfo : entry.getValue()) {
                for (IRInstruction instr : copyInfo.block().getInstructions()) {
                    if (instr instanceof CopyInstruction) {
                        CopyInstruction copy = (CopyInstruction) instr;
                        if (copy.getResult().equals(copyInfo.copyValue())) {
                            com.tonic.analysis.ssa.value.Value source = copy.getSource();
                            if (source instanceof SSAValue && allPhiResults.contains(source)) {
                                coalescingMap.put(phiResult, (SSAValue) source);
                            }
                        }
                    }
                }
            }
        }

        return coalescingMap;
    }

    private int allocateSlot(boolean twoSlot, Set<Integer> freeRegs) {
        int reg;
        if (!freeRegs.isEmpty() && !twoSlot) {
            reg = freeRegs.iterator().next();
            freeRegs.remove(reg);
        } else if (twoSlot) {
            boolean foundConsecutive = false;
            reg = maxLocals;
            for (int candidate : new ArrayList<>(freeRegs)) {
                if (freeRegs.contains(candidate + 1)) {
                    reg = candidate;
                    foundConsecutive = true;
                    break;
                }
            }
            if (!foundConsecutive) {
                maxLocals += 2;
            }
            freeRegs.remove(reg);
            freeRegs.remove(reg + 1);
            maxLocals = Math.max(maxLocals, reg + 2);
        } else {
            reg = maxLocals++;
        }
        return reg;
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
                    if (operand instanceof SSAValue) {
                        SSAValue ssa = (SSAValue) operand;
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
        Integer reg = allocation.get(value);
        if (reg != null) {
            return reg;
        }

        // Fallback: allocate a register on-demand for values that were missed
        // during the main allocation pass (can happen after certain transforms)
        reg = maxLocals;
        if (value.getType().isTwoSlot()) {
            maxLocals += 2;
        } else {
            maxLocals++;
        }
        allocation.put(value, reg);
        return reg;
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
