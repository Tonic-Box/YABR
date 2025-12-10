package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recovers variable names from debug info or generates synthetic names.
 */
public class NameRecoverer {

    @Getter
    private final NameRecoveryStrategy strategy;
    private final IRMethod irMethod;
    private final MethodEntry sourceMethod;
    private final LocalVariableTableAttribute lvt;
    private final ConstPool constPool;
    private final Map<Integer, String> slotToName = new HashMap<>();
    private int syntheticCounter = 0;

    public NameRecoverer(IRMethod irMethod, MethodEntry sourceMethod, NameRecoveryStrategy strategy) {
        this.irMethod = irMethod;
        this.sourceMethod = sourceMethod;
        this.strategy = strategy;
        this.constPool = sourceMethod.getClassFile().getConstPool();
        this.lvt = findLocalVariableTable();
        buildSlotNameMap();
    }

    private LocalVariableTableAttribute findLocalVariableTable() {
        CodeAttribute code = sourceMethod.getCodeAttribute();
        if (code == null) return null;

        for (Attribute attr : code.getAttributes()) {
            if (attr instanceof LocalVariableTableAttribute) {
                return (LocalVariableTableAttribute) attr;
            }
        }
        return null;
    }

    private void buildSlotNameMap() {
        if (lvt == null) return;

        for (LocalVariableTableEntry entry : lvt.getLocalVariableTable()) {
            String name = resolveUtf8(entry.getNameIndex());
            if (name != null) {
                slotToName.put(entry.getIndex(), name);
            }
        }
    }

    private String resolveUtf8(int index) {
        try {
            var item = constPool.getItem(index);
            if (item instanceof Utf8Item) {
                Utf8Item utf8Item = (Utf8Item) item;
                return utf8Item.getValue();
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Recovers a name for the given SSA value.
     */
    public String recoverName(SSAValue value, int localSlot, int bytecodeOffset) {
        if (strategy == NameRecoveryStrategy.ALWAYS_SYNTHETIC) {
            return generateSyntheticName(value);
        }

        String debugName = lookupFromDebugInfo(localSlot, bytecodeOffset);
        if (debugName != null && strategy == NameRecoveryStrategy.PREFER_DEBUG_INFO) {
            return debugName;
        }

        if (isParameter(localSlot)) {
            if (debugName != null) return debugName;
            return generateParameterName(localSlot);
        }

        if (strategy == NameRecoveryStrategy.PARAMETERS_ONLY) {
            return generateSyntheticName(value);
        }

        return debugName != null ? debugName : generateSyntheticName(value);
    }

    private String lookupFromDebugInfo(int slot, int offset) {
        if (lvt == null) return null;

        for (LocalVariableTableEntry entry : lvt.getLocalVariableTable()) {
            if (entry.getIndex() == slot) {
                if (offset >= entry.getStartPc() && offset < entry.getStartPc() + entry.getLengthPc()) {
                    return resolveUtf8(entry.getNameIndex());
                }
            }
        }
        return slotToName.get(slot);
    }

    private boolean isParameter(int slot) {
        int paramSlots = irMethod.isStatic() ? 0 : 1;
        for (SSAValue param : irMethod.getParameters()) {
            paramSlots++;
            if (param.getType() instanceof PrimitiveType) {
                PrimitiveType p = (PrimitiveType) param.getType();
                if (p == PrimitiveType.LONG || p == PrimitiveType.DOUBLE) {
                    paramSlots++;
                }
            }
        }
        return slot < paramSlots;
    }

    private String generateParameterName(int slot) {
        if (!irMethod.isStatic() && slot == 0) {
            return "this";
        }
        int paramIndex = irMethod.isStatic() ? slot : slot - 1;
        return "arg" + paramIndex;
    }

    /**
     * Generates a synthetic name based on the value's type.
     */
    public String generateSyntheticName(SSAValue value) {
        IRType type = value.getType();
        String prefix = getTypePrefix(type);
        return prefix + (syntheticCounter++);
    }

    private String getTypePrefix(IRType type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType p = (PrimitiveType) type;
            switch (p) {
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
        if (type instanceof ReferenceType) {
            ReferenceType r = (ReferenceType) type;
            String name = r.getInternalName();
            int lastSlash = name.lastIndexOf('/');
            String simple = lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
            if (simple.isEmpty()) return "obj";
            return Character.toLowerCase(simple.charAt(0)) + "";
        }
        return "v";
    }
}
