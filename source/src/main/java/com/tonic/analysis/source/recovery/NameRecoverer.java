package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.util.ClassNameUtil;
import java.util.HashMap;
import java.util.Map;

/**
 * Recovers variable names from debug info or generates synthetic names.
 */
public class NameRecoverer
{

    private final NameRecoveryStrategy strategy;
    private final IRMethod irMethod;
    private final MethodEntry sourceMethod;
    private final LocalVariableTableAttribute lvt;
    private final ConstPool constPool;
    private final Map<Integer, String> unambiguousSlotName = new HashMap<>();
    private int syntheticCounter = 0;

    /**
     * Creates a recoverer and eagerly indexes the method's local variable table and lifted instruction offsets.
     *
     * @param irMethod the lifted method
     * @param sourceMethod the method the IR came from
     * @param strategy whether debug names are preferred over synthetic ones
     * @throws NullPointerException if the source method has no owning class file
     */
    public NameRecoverer(IRMethod irMethod, MethodEntry sourceMethod, NameRecoveryStrategy strategy)
    {
        this.irMethod = irMethod;
        this.sourceMethod = sourceMethod;
        this.strategy = strategy;
        this.constPool = sourceMethod.getClassFile().getConstPool();
        this.lvt = findLocalVariableTable();
        buildSlotNameMap();
        buildSortedOffsets();
    }

    /**
     * Every lifted instruction offset, sorted - the instruction boundaries for exact range probes.
     */
    private int[] sortedOffsets = new int[0];

    private void buildSortedOffsets()
    {
        java.util.TreeSet<Integer> offs = new java.util.TreeSet<>();
        if (irMethod != null)
        {
            for (IRBlock block : irMethod.getBlocks())
            {
                for (IRInstruction instr : block.getInstructions())
                {
                    if (instr.getBytecodeOffset() >= 0)
                    {
                        offs.add(instr.getBytecodeOffset());
                    }
                }
            }
        }
        sortedOffsets = offs.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * The smallest instruction offset strictly greater than {@code off}, or {@code off + 1} if none known.
     *
     * @param off the bytecode offset to search past
     * @return the next known instruction offset, or one past the given offset
     */
    public int nextOffsetAfter(int off)
    {
        int lo = 0;
        int hi = sortedOffsets.length;
        while (lo < hi)
        {
            int mid = (lo + hi) >>> 1;
            if (sortedOffsets[mid] <= off)
            {
                lo = mid + 1;
            }
            else
            {
                hi = mid;
            }
        }
        return lo < sortedOffsets.length ? sortedOffsets[lo] : off + 1;
    }

    /**
     * The LVT name for {@code slot} at the pc where a STORE at {@code storeOffset} takes effect - the
     * following instruction, which is where javac opens the variable's range. This is the exact form
     * of the old fixed-width forward probe, which a wide store or multi-byte neighbor escaped.
     *
     * @param slot the local slot written
     * @param storeOffset the bytecode offset of the store
     * @return the name in scope, falling back to the name at the store itself, or null when neither
     *         has an entry
     */
    public String debugNameAtStore(int slot, int storeOffset)
    {
        String at = debugNameAt(slot, nextOffsetAfter(storeOffset));
        return at != null ? at : debugNameAt(slot, storeOffset);
    }

    /**
     * As {@link #debugNameAtStore} for the entry's descriptor.
     *
     * @param slot the local slot written
     * @param storeOffset the bytecode offset of the store
     * @return the declared descriptor in scope, falling back to the one at the store itself, or null
     *         when neither has an entry
     */
    public String debugDescriptorAtStore(int slot, int storeOffset)
    {
        String at = debugDescriptorAt(slot, nextOffsetAfter(storeOffset));
        return at != null ? at : debugDescriptorAt(slot, storeOffset);
    }

    /**
     * @return the strategy
     */
    public NameRecoveryStrategy getStrategy()
    {
        return strategy;
    }

    private LocalVariableTableAttribute findLocalVariableTable()
    {
        CodeAttribute code = sourceMethod.getCodeAttribute();
        if (code == null) return null;

        for (Attribute attr : code.getAttributes())
        {
            if (attr instanceof LocalVariableTableAttribute)
            {
                return (LocalVariableTableAttribute) attr;
            }
        }
        return null;
    }

    private void buildSlotNameMap()
    {
        if (lvt == null) return;

        Map<Integer, java.util.Set<String>> namesPerSlot = new HashMap<>();
        for (LocalVariableTableEntry entry : lvt.getLocalVariableTable())
        {
            String name = resolveUtf8(entry.getNameIndex());
            if (name != null)
            {
                namesPerSlot.computeIfAbsent(entry.getIndex(), k -> new java.util.HashSet<>()).add(name);
            }
        }
        for (Map.Entry<Integer, java.util.Set<String>> e : namesPerSlot.entrySet())
        {
            if (e.getValue().size() == 1)
            {
                unambiguousSlotName.put(e.getKey(), e.getValue().iterator().next());
            }
        }
    }

    /**
     * The LVT name for {@code slot} when every entry for that slot agrees on a single name, else null
     * (no debug info, or the slot is reused under different names across scopes). Used to recover real
     * variable names without risking a wrong label on a reused slot.
     *
     * @param slot the local slot to name
     * @return the single agreed name, or null when the entries disagree or the strategy forbids it
     */
    public String unambiguousDebugName(int slot)
    {
        return debugNamesAllowedFor(slot) ? unambiguousSlotName.get(slot) : null;
    }

    /**
     * Whether the strategy permits a recovered debug name for {@code slot}. This is the single gate the whole
     * naming path passes through - parameter names, a slot's base name, and the partition's scope lookup all
     * arrive here - so a strategy applies uniformly instead of holding only where a caller remembered it.
     */
    private boolean debugNamesAllowedFor(int slot)
    {
        switch (strategy)
        {
            case ALWAYS_SYNTHETIC:
                return false;
            case PARAMETERS_ONLY:
                return isParameter(slot);
            default:
                return true;
        }
    }

    /**
     * The LocalVariableTable name in scope for {@code slot} at bytecode {@code offset} - the entry whose
     * {@code [startPc, startPc + length)} range contains the offset - or null when there is no debug info
     * or no entry covers it. Unlike {@link #unambiguousDebugName} this resolves a reused slot correctly by
     * scope, so a slot holding {@code i} in one loop and {@code builder} in another names each by position.
     *
     * @param slot the local slot to name
     * @param offset the bytecode offset the name must be in scope at
     * @return the name in scope, or null when no entry covers the offset
     */
    public String debugNameAt(int slot, int offset)
    {
        if (lvt == null || !debugNamesAllowedFor(slot))
        {
            return null;
        }
        for (LocalVariableTableEntry entry : lvt.getLocalVariableTable())
        {
            if (entry.getIndex() == slot
                    && offset >= entry.getStartPc()
                    && offset < entry.getStartPc() + entry.getLengthPc())
            {
                return resolveUtf8(entry.getNameIndex());
            }
        }
        return null;
    }

    /**
     * The LocalVariableTable type descriptor in scope for {@code slot} at bytecode {@code offset} - the
     * declared type of the variable there (e.g. {@code "C"} for {@code char}), or null when no debug info or
     * no entry covers it. This is the authoritative declared type javac recorded, distinct from the widened
     * type inferred from the (int-shaped) stored values.
     *
     * @param slot the local slot to type
     * @param offset the bytecode offset the entry must be in scope at
     * @return the declared descriptor, or null when no entry covers the offset
     */
    public String debugDescriptorAt(int slot, int offset)
    {
        if (!debugNamesAllowedFor(slot))
        {
            return null;
        }
        if (lvt == null)
        {
            return null;
        }
        for (LocalVariableTableEntry entry : lvt.getLocalVariableTable())
        {
            if (entry.getIndex() == slot
                    && offset >= entry.getStartPc()
                    && offset < entry.getStartPc() + entry.getLengthPc())
            {
                return resolveUtf8(entry.getDescriptorIndex());
            }
        }
        return null;
    }

    private String resolveUtf8(int index)
    {
        try
        {
            var item = constPool.getItem(index);
            if (item instanceof Utf8Item)
            {
                Utf8Item utf8Item = (Utf8Item) item;
                return utf8Item.getValue();
            }
        }
        catch (Exception ignored)
        {
        }
        return null;
    }

    /**
     * Parameter slot count derived from the method descriptor, for use without lifted IR.
     */
    private int paramSlotsFromDescriptor()
    {
        int slots = (sourceMethod.getAccess() & 0x0008) != 0 ? 0 : 1;
        String desc = sourceMethod.getDesc();
        int i = desc.indexOf('(') + 1;
        while (i < desc.length() && desc.charAt(i) != ')')
        {
            char c = desc.charAt(i);
            boolean array = false;
            while (c == '[')
            {
                array = true;
                c = desc.charAt(++i);
            }
            if (c == 'L')
            {
                i = desc.indexOf(';', i) + 1;
            }
            else
            {
                i++;
            }
            slots += (!array && (c == 'J' || c == 'D')) ? 2 : 1;
        }
        return slots;
    }

    private boolean isParameter(int slot)
    {
        if (irMethod == null)
        {
            return slot < paramSlotsFromDescriptor();
        }
        int paramSlots = irMethod.isStatic() ? 0 : 1;
        for (SSAValue param : irMethod.getParameters())
        {
            paramSlots++;
            if (param.getType() instanceof PrimitiveType)
            {
                PrimitiveType p = (PrimitiveType) param.getType();
                if (p == PrimitiveType.LONG || p == PrimitiveType.DOUBLE)
                {
                    paramSlots++;
                }
            }
        }
        return slot < paramSlots;
    }

    /**
     * Generates a synthetic name based on the value's type.
     *
     * @param value the value to name
     * @return a type-prefixed name with a per-recoverer counter appended
     */
    public String generateSyntheticName(SSAValue value)
    {
        IRType type = value.getType();
        String prefix = getTypePrefix(type);
        return prefix + (syntheticCounter++);
    }

    private String getTypePrefix(IRType type)
    {
        if (type instanceof PrimitiveType)
        {
            PrimitiveType p = (PrimitiveType) type;
            switch (p)
            {
                case INT:
                case SHORT:
                case BYTE:
                    return "i";
                case LONG:
                    return "l";
                case FLOAT:
                    return "f";
                case DOUBLE:
                    return "d";
                case BOOLEAN:
                    return "flag";
                case CHAR:
                    return "c";
                default:
                    return "v";
            }
        }
        if (type instanceof ReferenceType)
        {
            ReferenceType r = (ReferenceType) type;
            String simple = ClassNameUtil.getSimpleNameWithInnerClasses(r.getInternalName());
            if (simple.isEmpty()) return "obj";
            return Character.toLowerCase(simple.charAt(0)) + "";
        }
        return "v";
    }
}
