package com.tonic.parser.attribute.table;

import com.tonic.parser.ConstPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared primitives for emitting {@code LocalVariableTable} entries, used by both LVT builders: the
 * lowering-side one (source-local model + register allocation) and the recovery-side one (decompiler slot
 * partition + original offsets). Keeps entry construction, well-formedness validation, and same-slot overlap
 * removal in one place so the two builders don't duplicate them.
 */
public final class LvtSupport
{

    private LvtSupport()
    {
    }

    /**
     * Builds an entry, resolving the name + descriptor to UTF-8 constant-pool indices.
     * @param constPool the pool the UTF-8 entries are added to
     * @param slot the local variable slot
     * @param name the variable name
     * @param descriptor the variable's field descriptor
     * @param startPc the first code offset the entry covers
     * @param length the number of bytes the entry covers
     * @return the built entry
     */
    public static LocalVariableTableEntry entry(ConstPool constPool, int slot, String name, String descriptor, int startPc, int length)
    {
        int nameIndex = constPool.findOrAddUtf8(name).getIndex(constPool);
        int descIndex = constPool.findOrAddUtf8(descriptor).getIndex(constPool);
        return new LocalVariableTableEntry(constPool, startPc, length, nameIndex, descIndex, slot);
    }

    /**
     * True when an entry is well-formed: slot in range and scope wholly within the code.
     * @param slot the local variable slot
     * @param maxLocals the method's local slot count
     * @param startPc the first code offset the entry covers
     * @param length the number of bytes the entry covers
     * @param codeLength the method's code length
     * @return true when the slot and the scope are both in range
     */
    public static boolean valid(int slot, int maxLocals, int startPc, int length, int codeLength)
    {
        return slot >= 0 && slot < maxLocals && startPc >= 0 && length >= 0 && startPc + length <= codeLength;
    }

    /**
     * Drops any same-slot entry whose scope overlaps an already-kept one (keeps the earlier entry).
     * @param entries the entry list, rewritten in place
     */
    public static void dropSameSlotOverlaps(List<LocalVariableTableEntry> entries)
    {
        List<LocalVariableTableEntry> kept = new ArrayList<>();
        for (LocalVariableTableEntry e : entries)
        {
            boolean overlaps = false;
            for (LocalVariableTableEntry k : kept)
            {
                if (k.getIndex() == e.getIndex()
                        && e.getStartPc() < k.getStartPc() + k.getLengthPc()
                        && k.getStartPc() < e.getStartPc() + e.getLengthPc())
                {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps)
            {
                kept.add(e);
            }
        }
        entries.clear();
        entries.addAll(kept);
    }
}
