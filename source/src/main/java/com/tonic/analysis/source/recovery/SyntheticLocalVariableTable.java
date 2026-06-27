package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.LoadLocalInstruction;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
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
 * Builds a {@code LocalVariableTable} for a STRIPPED method from the decompiler's recovered slot model, keyed
 * to the method's ORIGINAL bytecode offsets. Used to inject named locals into a class that has no debug info:
 * the names are exactly the ones the decompiler renders in source ({@code local{slot}}, scope-aware, or a real
 * name when an LVT was present), so a live debugger reading this table shows locals matching the decompilation.
 *
 * <p>Each distinct {@code (slot, recovered-name)} pair becomes one entry — parameters and the receiver span the
 * whole method, body locals span the offsets of their loads/stores. Compiler temps that never occupy a JVM
 * local slot get no entry. The attribute is added to the original {@code Code} without changing any bytecode.
 */
public final class SyntheticLocalVariableTable {

    private SyntheticLocalVariableTable() {
    }

    /**
     * @param ir         the lifted IR (after baseline transforms) of {@code method}
     * @param ctx        the recovery context whose slot partition holds the recovered names
     * @param method     the source method (the attribute's parent)
     * @param codeLength the original code length (scopes are clamped to it)
     * @param maxLocals  the method's {@code max_locals} (entry slots must be below it)
     * @return the synthetic attribute, or null when nothing recoverable
     */
    public static LocalVariableTableAttribute build(IRMethod ir, RecoveryContext ctx, MethodEntry method,
                                                    int codeLength, int maxLocals, ConstPool constPool) {
        SlotVariablePartition partition = ctx.getSlotPartition();
        if (partition == null) {
            return null;
        }
        MethodLocals locals = new MethodLocals(ir);
        Map<String, Local> byKey = new LinkedHashMap<>();

        // Parameters + receiver: whole-method scope, names/types from the recovered parameter values.
        for (SSAValue param : ir.getParameters()) {
            int slot = locals.slotOfParameter(param);
            String name = ctx.getVariableName(param);
            IRType type = param.getType();
            if (slot < 0 || name == null || type == null) {
                continue;
            }
            Local p = byKey.computeIfAbsent(slot + "|" + name, k -> new Local(slot, name));
            p.descriptor = type.getDescriptor();
            p.parameter = true;
        }

        // Body locals: each load/store contributes its recovered name and original bytecode offset.
        for (IRBlock block : ir.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                int slot;
                String name;
                IRType type;
                if (instr instanceof LoadLocalInstruction) {
                    LoadLocalInstruction load = (LoadLocalInstruction) instr;
                    slot = load.getLocalIndex();
                    name = partition.nameForLoad(load);
                    type = load.getResult() != null ? load.getResult().getType() : null;
                } else if (instr instanceof StoreLocalInstruction) {
                    StoreLocalInstruction store = (StoreLocalInstruction) instr;
                    slot = store.getLocalIndex();
                    name = partition.nameForStore(store);
                    type = typeOf(store.getValue());
                } else {
                    continue;
                }
                int off = instr.getBytecodeOffset();
                if (name == null || off < 0) {
                    continue;
                }
                Local local = byKey.computeIfAbsent(slot + "|" + name, k -> new Local(slot, name));
                local.extend(off);
                if (local.descriptor == null && type != null) {
                    local.descriptor = type.getDescriptor();
                }
            }
        }

        List<LocalVariableTableEntry> entries = new ArrayList<>();
        for (Local local : byKey.values()) {
            int startPc;
            int length;
            if (local.parameter || local.minOff == Integer.MAX_VALUE) {
                startPc = 0;
                length = codeLength;
            } else {
                startPc = Math.max(0, local.minOff);
                length = Math.min(codeLength, local.maxOff + 1) - startPc;
            }
            if (local.descriptor == null
                    || !LvtSupport.valid(local.slot, maxLocals, startPc, length, codeLength)) {
                continue;
            }
            entries.add(LvtSupport.entry(constPool, local.slot, local.name, local.descriptor, startPc, length));
        }

        LvtSupport.dropSameSlotOverlaps(entries);
        if (entries.isEmpty()) {
            return null;
        }
        int attrNameIndex = constPool.findOrAddUtf8("LocalVariableTable").getIndex(constPool);
        LocalVariableTableAttribute attr =
                new LocalVariableTableAttribute("LocalVariableTable", method, attrNameIndex, 0);
        attr.setLocalVariableTable(entries);
        attr.updateLength();
        return attr;
    }

    private static IRType typeOf(Value value) {
        return value instanceof SSAValue ? value.getType() : null;
    }

    private static final class Local {
        final int slot;
        final String name;
        String descriptor;
        int minOff = Integer.MAX_VALUE;
        int maxOff = -1;
        boolean parameter;

        Local(int slot, String name) {
            this.slot = slot;
            this.name = name;
        }

        void extend(int off) {
            minOff = Math.min(minOff, off);
            maxOff = Math.max(maxOff, off);
        }
    }
}
