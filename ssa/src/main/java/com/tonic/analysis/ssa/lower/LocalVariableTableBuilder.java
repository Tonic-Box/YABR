package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;
import com.tonic.parser.attribute.table.LvtSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@code LocalVariableTable} from the source-local model recorded during AST lowering
 * ({@link IRMethod.SourceLocal}), resolved against the final register allocation and bytecode layout.
 *
 * <p>Slots come straight from {@link RegisterAllocator} (which already owns the two-slot long/double
 * accounting). Scopes are block-range: the receiver and parameters span the whole method, a body local spans
 * the blocks containing its defs and uses (reused slots therefore get disjoint scopes, since the allocator
 * only reuses a slot whose prior occupant is already dead). Names and declared types come from the source-local
 * records, so compiler temps — which have no record — never produce an entry. Returns {@code null} when there
 * is nothing to emit (e.g. IR not produced from source has no source-local model).
 */
public final class LocalVariableTableBuilder {

    private final IRMethod irMethod;
    private final RegisterAllocator regAlloc;
    private final BytecodeEmitter emitter;
    private final int codeLength;
    private final ConstPool constPool;
    private final MethodEntry targetMethod;

    public LocalVariableTableBuilder(IRMethod irMethod, RegisterAllocator regAlloc, BytecodeEmitter emitter,
                                     int codeLength, ConstPool constPool, MethodEntry targetMethod) {
        this.irMethod = irMethod;
        this.regAlloc = regAlloc;
        this.emitter = emitter;
        this.codeLength = codeLength;
        this.constPool = constPool;
        this.targetMethod = targetMethod;
    }

    public LocalVariableTableAttribute build() {
        List<IRMethod.SourceLocal> locals = irMethod.getSourceLocals();
        if (locals.isEmpty()) {
            return null;
        }
        Map<SSAValue, Integer> allocation = regAlloc.getAllocation();
        int maxLocals = regAlloc.getMaxLocals();

        List<LocalVariableTableEntry> entries = new ArrayList<>();
        for (IRMethod.SourceLocal local : locals) {
            String desc = local.getType() != null ? local.getType().getDescriptor() : null;
            if (desc == null || local.getName() == null) {
                continue;
            }
            if (local.isParameter()) {
                Integer slot = resolveSlot(local, allocation);
                if (slot != null && LvtSupport.valid(slot, maxLocals, 0, codeLength, codeLength)) {
                    entries.add(LvtSupport.entry(constPool, slot, local.getName(), desc, 0, codeLength));
                }
                continue;
            }
            // A source variable's SSA versions can land on DIFFERENT slots (e.g. a dead `= null` default-init
            // on one slot, the real values on another). Emit one entry per slot, each scoped to only the values
            // ON that slot - a single scope spanning all versions would cover slots the entry doesn't name and
            // over-reach into other variables sharing this slot, so the overlap-drop would silently discard
            // their names and the decompiler would mislabel them (an unstable, drifting round trip).
            Map<Integer, List<SSAValue>> valuesBySlot = new LinkedHashMap<>();
            for (SSAValue v : local.getValues()) {
                Integer s = allocation.get(v);
                if (s != null) {
                    valuesBySlot.computeIfAbsent(s, k -> new ArrayList<>()).add(v);
                }
            }
            for (Map.Entry<Integer, List<SSAValue>> e : valuesBySlot.entrySet()) {
                int slot = e.getKey();
                int[] scope = instructionScope(e.getValue());
                int startPc = scope[0];
                int length = scope[1] - scope[0];
                if (LvtSupport.valid(slot, maxLocals, startPc, length, codeLength)) {
                    entries.add(LvtSupport.entry(constPool, slot, local.getName(), desc, startPc, length));
                }
            }
        }

        entries = trimSameSlotOverlaps(entries);
        if (entries.isEmpty()) {
            return null;
        }
        int attrNameIndex = constPool.findOrAddUtf8("LocalVariableTable").getIndex(constPool);
        LocalVariableTableAttribute attr =
                new LocalVariableTableAttribute("LocalVariableTable", targetMethod, attrNameIndex, 0);
        attr.setLocalVariableTable(entries);
        attr.updateLength();
        return attr;
    }

    /**
     * Resolves same-slot range overlaps by TRIMMING rather than dropping: within each slot, sort entries by
     * start and clamp each one's end to the next one's start, so a reused slot's successive variables get
     * disjoint ranges and BOTH keep their name. (The old drop discarded the later entry, so the decompiler
     * fell back to the surviving name for it - the mislabelled, drifting round trip.) The LocalVariableTable is
     * debug-only, so trimming never affects execution; it only sharpens which variable a slot names at each pc.
     */
    private List<LocalVariableTableEntry> trimSameSlotOverlaps(List<LocalVariableTableEntry> entries) {
        Map<Integer, List<LocalVariableTableEntry>> bySlot = new LinkedHashMap<>();
        for (LocalVariableTableEntry e : entries) {
            bySlot.computeIfAbsent(e.getIndex(), k -> new ArrayList<>()).add(e);
        }
        List<LocalVariableTableEntry> result = new ArrayList<>();
        for (List<LocalVariableTableEntry> slotEntries : bySlot.values()) {
            slotEntries.sort(java.util.Comparator.comparingInt(LocalVariableTableEntry::getStartPc)
                    .thenComparingInt(LocalVariableTableEntry::getLengthPc));
            for (int i = 0; i < slotEntries.size(); i++) {
                LocalVariableTableEntry e = slotEntries.get(i);
                int end = e.getStartPc() + e.getLengthPc();
                for (int j = i + 1; j < slotEntries.size(); j++) {
                    int nextStart = slotEntries.get(j).getStartPc();
                    if (nextStart > e.getStartPc()) {
                        end = Math.min(end, nextStart);
                        break;
                    }
                }
                int len = end - e.getStartPc();
                if (len > 0) {
                    result.add(new LocalVariableTableEntry(constPool, e.getStartPc(), len,
                            e.getNameIndex(), e.getDescriptorIndex(), e.getIndex()));
                }
            }
        }
        result.sort(java.util.Comparator.comparingInt(LocalVariableTableEntry::getStartPc));
        return result;
    }

    /** The final slot of a source variable: the first of its SSA values that was allocated one, else null. */
    private Integer resolveSlot(IRMethod.SourceLocal local, Map<SSAValue, Integer> allocation) {
        for (SSAValue v : local.getValues()) {
            Integer slot = allocation.get(v);
            if (slot != null) {
                return slot;
            }
        }
        return null;
    }

    /** Block-range scope {@code [startPc, endPc)} spanning the blocks of the given values' defs and uses. */
    private int[] blockScope(List<SSAValue> values) {
        Map<IRBlock, Integer> starts = emitter.getBlockOffsets();
        Map<IRBlock, Integer> ends = emitter.getBlockEndOffsets();
        int startPc = Integer.MAX_VALUE;
        int endPc = -1;
        for (SSAValue v : values) {
            int[] def = blockRange(v.getDefinition(), starts, ends);
            startPc = Math.min(startPc, def[0]);
            endPc = Math.max(endPc, def[1]);
            for (IRInstruction use : v.getUses()) {
                int[] r = blockRange(use, starts, ends);
                startPc = Math.min(startPc, r[0]);
                endPc = Math.max(endPc, r[1]);
            }
        }
        if (endPc < 0 || startPc == Integer.MAX_VALUE) {
            return new int[]{0, codeLength};
        }
        return new int[]{Math.max(0, startPc), Math.min(codeLength, endPc)};
    }

    private int[] blockRange(IRInstruction instr, Map<IRBlock, Integer> starts, Map<IRBlock, Integer> ends) {
        if (instr != null) {
            IRBlock b = instr.getBlock();
            Integer s = starts.get(b);
            Integer e = ends.get(b);
            if (s != null && e != null) {
                return new int[]{s, e};
            }
        }
        return new int[]{Integer.MAX_VALUE, -1};
    }

    /**
     * Instruction-precise scope {@code [startPc, endPc)} spanning the local's defs and uses - used only for a
     * reused slot, so its live-disjoint occupants get disjoint scopes (their block-range scopes would collide in
     * a straight-line method, losing one to {@link LvtSupport#dropSameSlotOverlaps} and widening the slot to
     * {@code Object}). Phi defs carry no bytecode offset and are simply skipped; a local's real (stored/used)
     * offsets bound its range.
     */
    private int[] instructionScope(List<SSAValue> values) {
        Map<IRInstruction, Integer> offs = emitter.getInstructionOffsets();
        int startPc = Integer.MAX_VALUE;
        int endPc = -1;
        for (SSAValue v : values) {
            startPc = Math.min(startPc, offsetOf(v.getDefinition(), offs, Integer.MAX_VALUE));
            endPc = Math.max(endPc, offsetOf(v.getDefinition(), offs, -1));
            for (IRInstruction use : v.getUses()) {
                startPc = Math.min(startPc, offsetOf(use, offs, Integer.MAX_VALUE));
                endPc = Math.max(endPc, offsetOf(use, offs, -1));
            }
        }
        if (endPc < 0 || startPc == Integer.MAX_VALUE) {
            return blockScope(values);
        }
        // End at the instruction boundary AFTER the last def/use, so the range covers that instruction yet
        // {@code start_pc + length} stays a valid opcode index (the JVM rejects a mid-instruction LVT bound).
        return new int[]{Math.max(0, startPc), nextBoundaryAfter(endPc, offs)};
    }

    /** The smallest emitted instruction offset strictly greater than {@code off}, or {@code codeLength}. */
    private int nextBoundaryAfter(int off, Map<IRInstruction, Integer> offs) {
        int best = codeLength;
        for (int o : offs.values()) {
            if (o > off && o < best) {
                best = o;
            }
        }
        return best;
    }

    private int offsetOf(IRInstruction instr, Map<IRInstruction, Integer> offs, int absent) {
        if (instr != null) {
            Integer off = offs.get(instr);
            if (off != null) {
                return off;
            }
        }
        return absent;
    }
}
