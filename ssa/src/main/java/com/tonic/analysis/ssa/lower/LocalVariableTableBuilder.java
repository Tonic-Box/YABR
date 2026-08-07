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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A builder for a {@code LocalVariableTable} over the source-local model recorded during AST lowering ({@link
 * IRMethod.SourceLocal}), taking slots from {@link RegisterAllocator} and scoping each entry to the blocks
 * holding that local's defs and uses.
 */
public final class LocalVariableTableBuilder
{

    private final IRMethod irMethod;
    private final RegisterAllocator regAlloc;
    private final BytecodeEmitter emitter;
    private final int codeLength;
    private final ConstPool constPool;
    private final MethodEntry targetMethod;
    private final Map<Long, String> signaturesByLvKey = new LinkedHashMap<>();

    /**
     * Creates a builder over one lowered method.
     * @param irMethod the method whose source-local records supply names and declared types
     * @param regAlloc the final register allocation, which owns the two-slot long/double accounting
     * @param emitter the emitter holding the written slots and store end offsets of the final layout
     * @param codeLength length in bytes of the emitted code, used as the scope end for parameters
     * @param constPool pool the name and descriptor Utf8 entries are added to
     * @param targetMethod the method the attribute will be attached to
     */
    public LocalVariableTableBuilder(IRMethod irMethod, RegisterAllocator regAlloc, BytecodeEmitter emitter, int codeLength, ConstPool constPool, MethodEntry targetMethod)
    {
        this.irMethod = irMethod;
        this.regAlloc = regAlloc;
        this.emitter = emitter;
        this.codeLength = codeLength;
        this.constPool = constPool;
        this.targetMethod = targetMethod;
    }

    /**
     * Builds the attribute, emitting one entry per slot a local occupies so that a variable whose
     * versions landed on different slots does not over-reach into its neighbours' scopes.
     * @return the built attribute, or null when the method carries no source-local model
     */
    public LocalVariableTableAttribute build()
    {
        List<IRMethod.SourceLocal> locals = irMethod.getSourceLocals();
        if (locals.isEmpty())
        {
            return null;
        }
        Map<SSAValue, Integer> allocation = regAlloc.getAllocation();
        int maxLocals = regAlloc.getMaxLocals();

        List<LocalVariableTableEntry> entries = new ArrayList<>();
        if (System.getProperty("yabr.lvttrace") != null)
        {
            System.err.println("[lvt] written=" + emitter.getWrittenSlots());
            for (IRMethod.SourceLocal l : locals)
            {
                StringBuilder sb = new StringBuilder("[lvt] " + l.getName() + " param=" + l.isParameter() + " vals=");
                for (SSAValue v : l.getValues())
                {
                    sb.append("v").append(v.getId())
                      .append("(def=").append(v.getDefinition() == null ? "null" : v.getDefinition().getClass().getSimpleName())
                      .append(",slot=").append(allocation.get(v))
                      .append(",se=").append(emitter.getStoreEndOffsets().get(v)).append(") ");
                }
                Set<SSAValue> grp = regAlloc.getHomeSlotGroups().get(l);
                sb.append(" group=").append(grp == null ? "null" : grp.size());
                System.err.println(sb);
            }
        }
        for (IRMethod.SourceLocal local : locals)
        {
            String desc = local.getType() != null ? local.getType().getDescriptor() : null;
            if (desc == null || local.getName() == null)
            {
                continue;
            }
            if (local.isParameter())
            {
                Integer slot = resolveSlot(local, allocation);
                if (slot != null && LvtSupport.valid(slot, maxLocals, 0, codeLength, codeLength))
                {
                    LocalVariableTableEntry entry =
                            LvtSupport.entry(constPool, slot, local.getName(), desc, 0, codeLength);
                    entries.add(entry);
                    if (local.getSignature() != null)
                    {
                        signaturesByLvKey.put(((long) slot << 32) | entry.getNameIndex(), local.getSignature());
                    }
                }
                continue;
            }
            // A source variable's SSA versions can land on DIFFERENT slots (e.g. a dead `= null` default-init
            // on one slot, the real values on another). Emit one entry per slot, each scoped to only the values
            // ON that slot - a single scope spanning all versions would cover slots the entry doesn't name and
            // over-reach into other variables sharing this slot, so the overlap-drop would silently discard
            // their names and the decompiler would mislabel them (an unstable, drifting round trip).
            Map<Integer, List<SSAValue>> valuesBySlot = new LinkedHashMap<>();
            for (SSAValue v : regAlloc.getHomeSlotGroups().getOrDefault(local, new LinkedHashSet<>(local.getValues())))
            {
                if (v.getDefinition() == null && !irMethod.getParameters().contains(v))
                {
                    continue;
                }
                Integer s = allocation.get(v);
                if (s != null)
                {
                    valuesBySlot.computeIfAbsent(s, k -> new ArrayList<>()).add(v);
                }
            }
            for (Map.Entry<Integer, List<SSAValue>> e : valuesBySlot.entrySet())
            {
                int slot = e.getKey();
                // The LVT is truthful: an entry names only a slot the emitted code actually writes.
                // A value kept on the stack or folded into its use has an allocation on paper only -
                // naming that slot points the reader at a local that never exists.
                if (!emitter.getWrittenSlots().contains(slot))
                {
                    continue;
                }
                int[] scope = instructionScope(e.getValue());
                int startPc = scope[0];
                int length = scope[1] - scope[0];
                if (LvtSupport.valid(slot, maxLocals, startPc, length, codeLength))
                {
                    LocalVariableTableEntry entry =
                            LvtSupport.entry(constPool, slot, local.getName(), desc, startPc, length);
                    entries.add(entry);
                    if (local.getSignature() != null)
                    {
                        signaturesByLvKey.put(((long) slot << 32) | entry.getNameIndex(), local.getSignature());
                    }
                }
            }
        }

        entries = mergeSameVariableRanges(entries);
        entries = trimSameSlotOverlaps(entries);
        if (entries.isEmpty())
        {
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
     * Folds the ranges of one variable on one slot into their union wherever they overlap or touch.
     */
    private List<LocalVariableTableEntry> mergeSameVariableRanges(List<LocalVariableTableEntry> entries)
    {
        Map<List<Integer>, List<LocalVariableTableEntry>> byVariable = new LinkedHashMap<>();
        for (LocalVariableTableEntry e : entries)
        {
            byVariable.computeIfAbsent(
                    List.of(e.getIndex(), e.getNameIndex(), e.getDescriptorIndex()),
                    k -> new ArrayList<>()).add(e);
        }
        List<LocalVariableTableEntry> result = new ArrayList<>();
        for (List<LocalVariableTableEntry> group : byVariable.values())
        {
            group.sort(java.util.Comparator.comparingInt(LocalVariableTableEntry::getStartPc));
            LocalVariableTableEntry open = null;
            int end = -1;
            for (LocalVariableTableEntry e : group)
            {
                if (open != null && e.getStartPc() <= end)
                {
                    end = Math.max(end, e.getStartPc() + e.getLengthPc());
                    continue;
                }
                if (open != null)
                {
                    result.add(new LocalVariableTableEntry(constPool, open.getStartPc(), end - open.getStartPc(),
                            open.getNameIndex(), open.getDescriptorIndex(), open.getIndex()));
                }
                open = e;
                end = e.getStartPc() + e.getLengthPc();
            }
            if (open != null)
            {
                result.add(new LocalVariableTableEntry(constPool, open.getStartPc(), end - open.getStartPc(),
                        open.getNameIndex(), open.getDescriptorIndex(), open.getIndex()));
            }
        }
        result.sort(java.util.Comparator.comparingInt(LocalVariableTableEntry::getStartPc));
        return result;
    }

    /**
     * Resolves same-slot range overlaps by TRIMMING rather than dropping.
     */
    private List<LocalVariableTableEntry> trimSameSlotOverlaps(List<LocalVariableTableEntry> entries)
    {
        Map<Integer, List<LocalVariableTableEntry>> bySlot = new LinkedHashMap<>();
        for (LocalVariableTableEntry e : entries)
        {
            bySlot.computeIfAbsent(e.getIndex(), k -> new ArrayList<>()).add(e);
        }
        List<LocalVariableTableEntry> result = new ArrayList<>();
        for (List<LocalVariableTableEntry> slotEntries : bySlot.values())
        {
            slotEntries.sort(java.util.Comparator.comparingInt(LocalVariableTableEntry::getStartPc)
                    .thenComparingInt(LocalVariableTableEntry::getLengthPc));
            for (int i = 0; i < slotEntries.size(); i++)
            {
                LocalVariableTableEntry e = slotEntries.get(i);
                int end = e.getStartPc() + e.getLengthPc();
                for (int j = i + 1; j < slotEntries.size(); j++)
                {
                    int nextStart = slotEntries.get(j).getStartPc();
                    if (nextStart > e.getStartPc())
                    {
                        end = Math.min(end, nextStart);
                        break;
                    }
                }
                int len = end - e.getStartPc();
                if (len > 0)
                {
                    result.add(new LocalVariableTableEntry(constPool, e.getStartPc(), len,
                            e.getNameIndex(), e.getDescriptorIndex(), e.getIndex()));
                }
            }
        }
        result.sort(java.util.Comparator.comparingInt(LocalVariableTableEntry::getStartPc));
        return result;
    }

    /**
     * @return generic signatures for the emitted entries, taken from the source-local declarations
     *         and keyed {@code slot << 32 | nameIndex} - the key the type-table rebuild matches on
     */
    public Map<Long, String> getSignaturesByLvKey()
    {
        return signaturesByLvKey;
    }

    /**
     * The final slot of a source variable: the first of its SSA values that was allocated one, else null.
     */
    private Integer resolveSlot(IRMethod.SourceLocal local, Map<SSAValue, Integer> allocation)
    {
        for (SSAValue v : local.getValues())
        {
            Integer slot = allocation.get(v);
            if (slot != null)
            {
                return slot;
            }
        }
        return null;
    }

    /**
     * Block-range scope {@code [startPc, endPc)} spanning the blocks of the given values' defs and uses.
     */
    private int[] blockScope(List<SSAValue> values)
    {
        Map<IRBlock, Integer> starts = emitter.getBlockOffsets();
        Map<IRBlock, Integer> ends = emitter.getBlockEndOffsets();
        int startPc = Integer.MAX_VALUE;
        int endPc = -1;
        for (SSAValue v : values)
        {
            int[] def = blockRange(v.getDefinition(), starts, ends);
            startPc = Math.min(startPc, def[0]);
            endPc = Math.max(endPc, def[1]);
            for (IRInstruction use : v.getUses())
            {
                int[] r = blockRange(use, starts, ends);
                startPc = Math.min(startPc, r[0]);
                endPc = Math.max(endPc, r[1]);
            }
        }
        if (endPc < 0 || startPc == Integer.MAX_VALUE)
        {
            return new int[]{0, codeLength};
        }
        return new int[]{Math.max(0, startPc), Math.min(codeLength, endPc)};
    }

    private int[] blockRange(IRInstruction instr, Map<IRBlock, Integer> starts, Map<IRBlock, Integer> ends)
    {
        if (instr != null)
        {
            IRBlock b = instr.getBlock();
            Integer s = starts.get(b);
            Integer e = ends.get(b);
            if (s != null && e != null)
            {
                return new int[]{s, e};
            }
        }
        return new int[]{Integer.MAX_VALUE, -1};
    }

    /**
     * Instruction-precise scope {@code [startPc, endPc)} spanning the local's defs and uses, for a reused
     * slot.
     */
    private int[] instructionScope(List<SSAValue> values)
    {
        Map<IRInstruction, Integer> offs = emitter.getInstructionOffsets();
        Map<SSAValue, Integer> storeEnds = emitter.getStoreEndOffsets();
        int startPc = Integer.MAX_VALUE;
        int storeStart = Integer.MAX_VALUE;
        int endPc = -1;
        for (SSAValue v : values)
        {
            Integer afterStore = storeEnds.get(v);
            if (afterStore != null)
            {
                storeStart = Math.min(storeStart, afterStore);
                endPc = Math.max(endPc, afterStore);
            }
            startPc = Math.min(startPc, offsetOf(v.getDefinition(), offs, Integer.MAX_VALUE));
            endPc = Math.max(endPc, offsetOf(v.getDefinition(), offs, -1));
            for (IRInstruction use : v.getUses())
            {
                startPc = Math.min(startPc, offsetOf(use, offs, Integer.MAX_VALUE));
                endPc = Math.max(endPc, offsetOf(use, offs, -1));
            }
        }
        // The range opens at the pc AFTER the initializing store, not where the initializer begins
        // computing (the javac convention). Opening earlier both misdescribes the slot - it still
        // holds the previous occupant there - and steals range from that occupant in the same-slot
        // trim.
        if (storeStart != Integer.MAX_VALUE)
        {
            startPc = storeStart;
        }
        if (endPc < 0 || startPc == Integer.MAX_VALUE)
        {
            return blockScope(values);
        }
        // End at the instruction boundary AFTER the last def/use, so the range covers that instruction yet
        // {@code start_pc + length} stays a valid opcode index (the JVM rejects a mid-instruction LVT bound).
        return new int[]{Math.max(0, startPc), Math.max(nextBoundaryAfter(endPc, offs), startPc)};
    }

    /**
     * The smallest emitted instruction offset strictly greater than {@code off}, or {@code codeLength}.
     */
    private int nextBoundaryAfter(int off, Map<IRInstruction, Integer> offs)
    {
        int best = codeLength;
        for (int o : offs.values())
        {
            if (o > off && o < best)
            {
                best = o;
            }
        }
        return best;
    }

    private int offsetOf(IRInstruction instr, Map<IRInstruction, Integer> offs, int absent)
    {
        if (instr != null)
        {
            Integer off = offs.get(instr);
            if (off != null)
            {
                return off;
            }
        }
        return absent;
    }
}
