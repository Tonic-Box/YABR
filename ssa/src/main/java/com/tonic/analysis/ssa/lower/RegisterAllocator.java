package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.analysis.LivenessAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BinaryOpInstruction;
import com.tonic.analysis.ssa.ir.CopyInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import java.util.*;

/**
 * Linear scan register allocator.
 * Assigns local variable slots to SSA values.
 */
public class RegisterAllocator {

    private final IRMethod method;
    private final LivenessAnalysis liveness;
    private final Map<SSAValue, Integer> allocation;
    private final Map<SSAValue, LiveInterval> intervalByValue = new HashMap<>();
    private final Map<Integer, IRType> slotType = new HashMap<>();
    // A slot is owned by at most one NAMED source variable. javac gives each named local its own slot for its
    // lexical scope; YABR's liveness-based reuse/coalescing otherwise lets two distinctly-named locals share a
    // slot (e.g. `byte[] hash = combined` - combined dies at the copy so hash reuses its slot), collapsing them
    // to one name on round trip. Ownership blocks that merge while leaving unnamed temps free to share.
    private final Map<Integer, IRMethod.SourceLocal> slotOwner = new HashMap<>();
    private Map<SSAValue, IRMethod.SourceLocal> namedLocalOf = new HashMap<>();
    private int maxLocals;
    private int reservedSlotCount; // Slots reserved for parameters, never released
    private Map<IRBlock, List<PhiInstruction>> phisByBlockCache;
    private Map<IRBlock, Set<SSAValue>> phiAwareLiveOutCache;

    public RegisterAllocator(IRMethod method, LivenessAnalysis liveness) {
        this.method = method;
        this.liveness = liveness;
        this.allocation = new HashMap<>();
        this.maxLocals = 0;
    }

    public IRMethod getMethod() {
        return method;
    }

    public LivenessAnalysis getLiveness() {
        return liveness;
    }

    public Map<SSAValue, Integer> getAllocation() {
        return allocation;
    }

    public int getMaxLocals() {
        return maxLocals;
    }

    public int getReservedSlotCount() {
        return reservedSlotCount;
    }

    public void allocate() {
        List<LiveInterval> intervals = buildIntervals();
        for (LiveInterval interval : intervals) {
            intervalByValue.put(interval.value, interval);
        }
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

        namedLocalOf = new HashMap<>();
        for (IRMethod.SourceLocal sl : method.getSourceLocals()) {
            if (sl.getName() == null) {
                continue;
            }
            for (SSAValue v : sl.getValues()) {
                namedLocalOf.putIfAbsent(v, sl);
            }
        }
        slotOwner.clear();
        for (SSAValue param : method.getParameters()) {
            claimSlot(allocation.get(param), param);
        }

        preAllocatePhiResults(freeRegs);
        assignPhiCopiesToPhiResultSlots();
        coalesceCopySourcesIntoPhiSlots();
        coalesceReassignmentsIntoVariableSlots();

        for (LiveInterval interval : intervals) {
            SSAValue value = interval.value;
            if (allocation.containsKey(value)) {
                continue;
            }

            expireOldIntervals(active, freeRegs, interval.start);

            int reg;
            if (interval.value.getType().isTwoSlot()) {
                // A long/double needs TWO consecutive exclusive slots. Search the full free set for a free (n, n+1)
                // pair; otherwise grow maxLocals by 2. (Previously this path first grabbed an unrelated single slot
                // and then re-picked, leaking that slot and miscounting maxLocals -> overlapping long slots like
                // 11-12 and 12-13 -> "bad type array size" at verification.)
                int pair = -1;
                for (int candidate : freeRegs) {
                    if (freeRegs.contains(candidate + 1)) {
                        pair = candidate;
                        break;
                    }
                }
                reg = (pair >= 0) ? pair : maxLocals;
                freeRegs.remove(reg);
                freeRegs.remove(reg + 1);
                maxLocals = Math.max(maxLocals, reg + 2);
            } else {
                // Reuse a free slot only when its last occupant is compatible. For a primitive, the same storage
                // kind (int family, long, float, double). For a REFERENCE, the same reference type - not merely
                // "some reference": two disjoint-lived refs of different types sharing a slot force the decompiler
                // to widen the slot to Object (an Object grab-bag of unrelated variables it can't cleanly name or
                // type on round trip). A fresh slot per reference type keeps each variable cleanly typed.
                IRType wantType = value.getType();
                int want = storageKind(wantType);
                boolean isRef = !(wantType instanceof PrimitiveType);
                int reuse = -1;
                for (int candidate : freeRegs) {
                    IRType last = slotType.get(candidate);
                    if (last == null
                            || (isRef ? sameReferenceType(last, wantType) : storageKind(last) == want)) {
                        reuse = candidate;
                        break;
                    }
                }
                if (reuse >= 0) {
                    reg = reuse;
                    freeRegs.remove(reg);
                } else {
                    reg = maxLocals++;
                }
            }

            allocation.put(value, reg);
            slotType.put(reg, value.getType());
            if (value.getType().isTwoSlot()) {
                slotType.put(reg + 1, value.getType());
            }
            active.add(interval);
            active.sort(Comparator.comparingInt(a -> a.end));
        }
    }

    /** Whether {@code local} (a named source var, or null for an unnamed temp) may occupy {@code slot}. */
    private boolean canOwnSlot(int slot, IRMethod.SourceLocal local) {
        if (local == null) {
            return true;
        }
        IRMethod.SourceLocal owner = slotOwner.get(slot);
        return owner == null || owner == local;
    }

    /** Records the named owner of {@code slot} from {@code value}, if it is a named source variable. */
    private void claimSlot(int slot, SSAValue value) {
        IRMethod.SourceLocal local = namedLocalOf.get(value);
        if (local != null) {
            slotOwner.putIfAbsent(slot, local);
        }
    }

    /** Whether {@code a} and {@code b} are the same reference type (both references, equal descriptor). */
    private static boolean sameReferenceType(IRType a, IRType b) {
        return !(a instanceof PrimitiveType) && !(b instanceof PrimitiveType)
                && a.getDescriptor().equals(b.getDescriptor());
    }

    private static int storageKind(IRType type) {
        if (!(type instanceof PrimitiveType)) {
            return 0;
        }
        switch ((PrimitiveType) type) {
            case LONG: return 1;
            case FLOAT: return 2;
            case DOUBLE: return 3;
            default: return 4; // int / boolean / byte / char / short
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

    private SSAValue find(Map<SSAValue, SSAValue> parent, SSAValue value) {
        if (!parent.get(value).equals(value)) {
            parent.put(value, find(parent, parent.get(value)));
        }
        return parent.get(value);
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

    /**
     * Coalesces a reassignment back into its variable's slot. A value defined as {@code v = op(p, x)} where one
     * operand {@code p} belongs to {@code v}'s OWN source variable (e.g. {@code num = num + 2},
     * {@code sum = sum + i}) is the same variable's next value and javac keeps it in the one slot - but it has
     * no copy/phi edge, so the linear scan would spill it to a fresh temp. Pin it to {@code p}'s slot. Only an
     * operand of the SAME source variable qualifies (so {@code local6 = sum + local5} never coalesces across
     * variables), and it must not interfere with a DIFFERENT variable already in that slot (same-variable
     * occupants are temporally/path disjoint by construction, like the values javac keeps in one slot).
     */
    private Object commonCopySourceGroup(List<CopyInfo> copies, Map<SSAValue, Object> group) {
        Object common = null;
        for (CopyInfo ci : copies) {
            SSAValue src = copySource(ci);
            if (src == null) {
                continue;
            }
            Object g = group.get(src);
            if (g == null) {
                continue;
            }
            if (common == null) {
                common = g;
            } else if (common != g) {
                return null;
            }
        }
        return common;
    }

    private void coalesceReassignmentsIntoVariableSlots() {
        Map<SSAValue, Object> group = new HashMap<>();
        Map<Object, Set<Integer>> groupSlots = new HashMap<>();
        for (IRMethod.SourceLocal local : method.getSourceLocals()) {
            for (SSAValue v : local.getValues()) {
                group.putIfAbsent(v, local);
                Integer s = allocation.get(v);
                if (s != null) {
                    groupSlots.computeIfAbsent(local, k -> new HashSet<>()).add(s);
                }
            }
        }
        // A phi result is the loop/merge-carried version of a variable but is synthesised after lowering, so
        // it carries no SourceLocal (and by allocation time the phi is already eliminated - its structure lives
        // in the phi-copy mapping). Associate each phi result whose incoming copies all belong to one variable
        // with that variable, so a reassignment `v = op(phi, c)` recognises the phi's slot as the variable's -
        // otherwise a loop accumulator `x = x | ...` cannot coalesce and splits into a two-slot swap.
        Map<SSAValue, List<CopyInfo>> phiCopiesForGroup = method.getPhiCopyMapping();
        if (phiCopiesForGroup != null) {
            for (Map.Entry<SSAValue, List<CopyInfo>> entry : phiCopiesForGroup.entrySet()) {
                SSAValue res = entry.getKey();
                if (res == null || group.containsKey(res)) {
                    continue;
                }
                Object g = commonCopySourceGroup(entry.getValue(), group);
                if (g == null) {
                    continue;
                }
                group.put(res, g);
                Integer s = allocation.get(res);
                if (s != null) {
                    groupSlots.computeIfAbsent(g, k -> new HashSet<>()).add(s);
                }
            }
        }
        Map<Integer, List<SSAValue>> slotValues = new HashMap<>();
        for (Map.Entry<SSAValue, Integer> e : allocation.entrySet()) {
            slotValues.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }
        for (IRBlock b : method.getBlocks()) {
            for (IRInstruction instr : b.getInstructions()) {
                if (!(instr instanceof BinaryOpInstruction)) {
                    continue;
                }
                SSAValue v = instr.getResult();
                if (v == null || allocation.containsKey(v) || v.getType().isTwoSlot()) {
                    continue;
                }
                Object g = group.get(v);
                if (g == null) {
                    continue;
                }
                // An operand sitting in a slot v's OWN variable already occupies means this op reassigns the
                // variable (the operand is the variable's current value, e.g. the loop/entry phi in num's slot).
                Set<Integer> mySlots = groupSlots.getOrDefault(g, Collections.emptySet());
                Integer slot = null;
                for (Value op : instr.getOperands()) {
                    if (op instanceof SSAValue) {
                        Integer s = allocation.get(op);
                        if (s != null && s >= reservedSlotCount && mySlots.contains(s)) {
                            slot = s;
                            break;
                        }
                    }
                }
                if (slot == null) {
                    continue;
                }
                IRType last = slotType.get(slot);
                if (last != null && storageKind(last) != storageKind(v.getType())) {
                    continue;
                }
                boolean conflicts = false;
                for (SSAValue occ : slotValues.getOrDefault(slot, Collections.emptyList())) {
                    if (group.get(occ) != g && interferes(v, occ)) {
                        conflicts = true;
                        break;
                    }
                }
                if (conflicts) {
                    continue;
                }
                allocation.put(v, slot);
                slotValues.computeIfAbsent(slot, k -> new ArrayList<>()).add(v);
            }
        }
    }

    private void coalesceCopySourcesIntoPhiSlots() {
        Map<SSAValue, List<CopyInfo>> phiCopies = method.getPhiCopyMapping();
        if (phiCopies == null) return;

        // value -> its source variable. Two incoming copies of ONE phi that belong to the same source
        // variable are its pre- and post-reassignment SSA values arriving from different predecessors; they
        // are mutually exclusive at the phi and may share the phi slot even though their conservative
        // linear-scan intervals overlap (e.g. num0 = pre-try value restored on the catch path vs num1 = the
        // try's reassignment - the overlap is path-insensitive, the values never coexist).
        Map<SSAValue, Object> group = new HashMap<>();
        for (IRMethod.SourceLocal local : method.getSourceLocals()) {
            for (SSAValue v : local.getValues()) {
                group.putIfAbsent(v, local);
            }
        }

        for (Map.Entry<SSAValue, List<CopyInfo>> entry : phiCopies.entrySet()) {
            Integer phiSlot = allocation.get(entry.getKey());
            if (phiSlot == null) continue;

            // The phi belongs to the variable that flows around its own loop (`v = f(phi)`, the loop-carried
            // source). Claim its slot for that named variable so an init copy of a DIFFERENT named variable
            // (`byte[] hash = combined`) is not coalesced onto it and lost.
            for (CopyInfo copyInfo : entry.getValue()) {
                SSAValue s = copySource(copyInfo);
                if (s != null && definitionUsesValue(s, entry.getKey())) {
                    claimSlot(phiSlot, s);
                }
            }

            List<SSAValue> placed = new ArrayList<>();
            for (CopyInfo copyInfo : entry.getValue()) {
                SSAValue source = copySource(copyInfo);
                if (source == null || allocation.containsKey(source)) {
                    continue;
                }
                if (!canOwnSlot(phiSlot, namedLocalOf.get(source))) {
                    continue; // slot owned by a different named variable - keep this one separate, like javac
                }
                // Normally the conservative interval decides. The one case we override is a source that is the
                // phi's own loop-carried value (`v = f(phi)`, so it derives from the phi and becomes the phi
                // next iteration): the interval falsely reports a conflict there (it keeps the phi live across
                // the back-edge past its real last use), so confirm with precise liveness and coalesce when
                // they genuinely never coexist. Everything else keeps the interval behaviour, so no unrelated
                // allocation shifts. A nested accumulator whose phi truly coexists still reports interference.
                boolean loopCarriedFalseConflict = definitionUsesValue(source, entry.getKey())
                        && !preciselyInterferes(entry.getKey(), source);
                if (interferes(entry.getKey(), source) && !loopCarriedFalseConflict) {
                    continue;
                }
                Object sourceGroup = group.get(source);
                boolean conflictsWithPlaced = false;
                for (SSAValue other : placed) {
                    boolean sameVariable = sourceGroup != null && sourceGroup == group.get(other);
                    if (!sameVariable && interferes(other, source)) {
                        conflictsWithPlaced = true;
                        break;
                    }
                }
                if (conflictsWithPlaced) {
                    continue;
                }
                allocation.put(source, phiSlot);
                claimSlot(phiSlot, source);
                placed.add(source);
            }
        }
    }

    /**
     * Whether {@code value}'s defining instruction reads {@code used} as an operand - i.e. {@code value} is
     * computed from {@code used}. For a phi's incoming source this marks the loop-carried value derived from
     * the phi ({@code v = f(phi)}), which supersedes it on the back-edge and may share its slot.
     */
    private boolean definitionUsesValue(SSAValue value, SSAValue used) {
        IRInstruction def = value.getDefinition();
        if (def == null) {
            return false;
        }
        for (Value op : def.getOperands()) {
            if (op == used) {
                return true;
            }
        }
        return false;
    }

    /**
     * Precise interference test: whether {@code a} and {@code b} are ever simultaneously live at any program
     * point, computed from the true per-instruction liveness rather than the single conservative interval.
     * <p>
     * The interval an allocator carries for a phi result is deliberately over-extended (across the loop
     * back-edge and to cover its copies), so {@link #interferes} reports a false conflict between a phi and its
     * own loop-carried source ({@code v = f(phi)}): the interval says the phi is live in {@code [f(phi), back
     * edge]} where in truth it is dead (the back-edge carries the source, not the phi). Walking real liveness
     * shows they never coexist there, so the source may share the phi slot - while a nested-loop accumulator
     * whose phi genuinely coexists with the source is still reported as interfering.
     */
    private boolean preciselyInterferes(SSAValue a, SSAValue b) {
        if (a == b) {
            return false;
        }
        Map<IRBlock, Set<SSAValue>> liveOut = phiAwareLiveOut();
        for (IRBlock block : method.getBlocks()) {
            Set<SSAValue> live = new HashSet<>(liveOut.get(block));
            if (live.contains(a) && live.contains(b)) {
                return true;
            }
            List<IRInstruction> instrs = block.getInstructions();
            for (int i = instrs.size() - 1; i >= 0; i--) {
                IRInstruction instr = instrs.get(i);
                SSAValue def = instr.getResult();
                if (def != null) {
                    live.remove(def);
                }
                for (Value op : instr.getOperands()) {
                    if (op instanceof SSAValue) {
                        live.add((SSAValue) op);
                    }
                }
                if (live.contains(a) && live.contains(b)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Per-block live-out sets recomputed to be phi-aware, because the shared {@link LivenessAnalysis} is not:
     * phis are eliminated (their instructions cleared from blocks) before liveness runs, so a phi result has no
     * visible definition and is computed as live everywhere backward from its uses - over-extending it across
     * the loop back-edge so it falsely coexists with its own back-edge source. Here a phi result is a proper
     * definition at its block entry (not live-in there, not live on the back-edge), and each predecessor edge
     * carries the phi OPERAND supplied from that predecessor. Standard backward dataflow to a fixed point.
     */
    private Map<IRBlock, Set<SSAValue>> phiAwareLiveOut() {
        if (phiAwareLiveOutCache != null) {
            return phiAwareLiveOutCache;
        }
        List<IRBlock> blocks = method.getBlocks();
        Map<IRBlock, Set<SSAValue>> liveIn = new HashMap<>();
        Map<IRBlock, Set<SSAValue>> liveOut = new HashMap<>();
        for (IRBlock b : blocks) {
            liveIn.put(b, new HashSet<>());
            liveOut.put(b, new HashSet<>());
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = blocks.size() - 1; i >= 0; i--) {
                IRBlock b = blocks.get(i);
                Set<SSAValue> out = new HashSet<>();
                for (IRBlock succ : b.getSuccessors()) {
                    Set<SSAValue> edge = new HashSet<>(liveIn.get(succ));
                    for (PhiInstruction phi : phisByBlock().getOrDefault(succ, Collections.emptyList())) {
                        if (phi.getResult() != null) {
                            edge.remove(phi.getResult());
                        }
                        Value in = phi.getIncoming(b);
                        if (in instanceof SSAValue) {
                            edge.add((SSAValue) in);
                        }
                    }
                    out.addAll(edge);
                }
                Set<SSAValue> in = new HashSet<>(out);
                List<IRInstruction> instrs = b.getInstructions();
                for (int j = instrs.size() - 1; j >= 0; j--) {
                    IRInstruction instr = instrs.get(j);
                    if (instr.getResult() != null) {
                        in.remove(instr.getResult());
                    }
                    for (Value op : instr.getOperands()) {
                        if (op instanceof SSAValue) {
                            in.add((SSAValue) op);
                        }
                    }
                }
                for (PhiInstruction phi : phisByBlock().getOrDefault(b, Collections.emptyList())) {
                    if (phi.getResult() != null) {
                        in.remove(phi.getResult());
                    }
                }
                if (!out.equals(liveOut.get(b)) || !in.equals(liveIn.get(b))) {
                    liveOut.put(b, out);
                    liveIn.put(b, in);
                    changed = true;
                }
            }
        }
        phiAwareLiveOutCache = liveOut;
        return liveOut;
    }

    /**
     * Phi results grouped by the block that defines them, rebuilt from the phi-copy mapping. Phi instructions
     * are cleared from their blocks during phi elimination (before allocation), but each phi-result value still
     * points at its {@code PhiInstruction} (with its block and per-edge incoming values intact), so the phi
     * structure needed for phi-aware liveness survives here.
     */
    private Map<IRBlock, List<PhiInstruction>> phisByBlock() {
        if (phisByBlockCache == null) {
            phisByBlockCache = new HashMap<>();
            Map<SSAValue, List<CopyInfo>> phiCopies = method.getPhiCopyMapping();
            if (phiCopies != null) {
                for (SSAValue phiResult : phiCopies.keySet()) {
                    IRInstruction def = phiResult.getDefinition();
                    if (def instanceof PhiInstruction && def.getBlock() != null) {
                        phisByBlockCache.computeIfAbsent(def.getBlock(), k -> new ArrayList<>())
                                .add((PhiInstruction) def);
                    }
                }
            }
        }
        return phisByBlockCache;
    }

    /**
     * Returns the SSA source of the copy that produces a phi's incoming value, or null when the
     * source is a constant or otherwise not a slot-resident value.
     */
    private SSAValue copySource(CopyInfo copyInfo) {
        for (IRInstruction instr : copyInfo.block().getInstructions()) {
            if (instr instanceof CopyInstruction) {
                CopyInstruction copy = (CopyInstruction) instr;
                if (copy.getResult().equals(copyInfo.copyValue())
                        && copy.getSource() instanceof SSAValue) {
                    return (SSAValue) copy.getSource();
                }
            }
        }
        return null;
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
                            Value source = copy.getSource();
                            // Only coalesce two phis that reference each other when their live
                            // ranges do not overlap. Coalescing interfering phis into one slot
                            // corrupts code such as a loop swap (a = b; b = temp), where both phi
                            // results are simultaneously live and must occupy distinct slots. Use the
                            // phi-aware interference test - the shared LivenessAnalysis over-lives a phi
                            // result (phis are eliminated before it runs) and falsely reports a loop-carried
                            // phi as interfering with its own if/else-merge phi, splitting an accumulator like
                            // `x = f(x)` into `t = f(x); x = t`.
                            if (source instanceof SSAValue && allPhiResults.contains(source)
                                    && !preciselyInterferes(phiResult, (SSAValue) source)) {
                                coalescingMap.put(phiResult, (SSAValue) source);
                            }
                        }
                    }
                }
            }
        }

        return coalescingMap;
    }

    /**
     * Returns whether two values' live ranges overlap, i.e. they are simultaneously live and
     * therefore cannot share a register.
     */
    private boolean interferes(SSAValue x, SSAValue y) {
        LiveInterval a = intervalByValue.get(x);
        LiveInterval b = intervalByValue.get(y);
        if (a == null || b == null) {
            return false;
        }
        return a.start < b.end && b.start < a.end;
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
                continue;
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

        // First pass: build basic intervals from textual def/use positions, recording each block's range.
        Map<IRBlock, Integer> blockEnd = new HashMap<>();
        Set<SSAValue> phiResults = new HashSet<>();
        int pos = 0;
        for (IRBlock block : rpo) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (phi.getResult() != null) {
                    updateInterval(intervals, phi.getResult(), pos);
                    phiResults.add(phi.getResult());
                }
                pos++;
            }
            for (IRInstruction instr : block.getInstructions()) {
                if (instr.getResult() != null) {
                    updateInterval(intervals, instr.getResult(), pos);
                }
                for (Value operand : instr.getOperands()) {
                    if (operand instanceof SSAValue) {
                        SSAValue ssa = (SSAValue) operand;
                        updateInterval(intervals, ssa, pos);
                    }
                }
                pos++;
            }
            blockEnd.put(block, pos - 1);
        }

        // Second pass: extend intervals to cover loop-carried liveness. A value live-OUT of a block is still
        // live at that block's exit (its back-edge or fall-through), so its slot must not be reused within the
        // block. Textual positions miss this for a value used only in a loop header - e.g. a loop bound read in
        // the condition but kept live across the body by the back-edge: its textual interval ends at the
        // condition, so a loop-body temp coalesces over its slot and corrupts it each iteration. Extending to
        // the block end makes the true interference visible (only lengthens intervals, never miscompiles).
        for (IRBlock block : rpo) {
            Integer end = blockEnd.get(block);
            if (end == null) {
                continue;
            }
            for (SSAValue live : liveness.getLiveOut(block)) {
                // Phi results carry their liveness through extendPhiResultIntervals; extending them here too
                // only perturbs allocation. Target the non-phi loop-carried values (e.g. a loop bound) that the
                // textual pass misses - those are the ones a body temp would otherwise falsely coalesce over.
                if (phiResults.contains(live)) {
                    continue;
                }
                LiveInterval interval = intervals.get(live);
                if (interval != null && end > interval.end) {
                    interval.end = end;
                }
            }
        }

        // Third pass: extend phi result intervals to cover their phi copies
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

            for (CopyInfo copyInfo : entry.getValue()) {
                LiveInterval copyInterval = intervals.get(copyInfo.copyValue());
                if (copyInterval != null) {
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
