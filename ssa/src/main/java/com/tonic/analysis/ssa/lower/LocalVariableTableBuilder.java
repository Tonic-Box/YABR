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
            Integer slot = resolveSlot(local, allocation);
            if (slot == null) {
                continue;
            }
            // Parameters span the whole method; a body local uses its instruction-precise live range so that a
            // slot reused by a later local (or a compiler temp) does not fall inside this variable's scope and
            // merge into it - which in a straight-line method would widen the slot to Object on round trip.
            int[] scope = local.isParameter() ? new int[]{0, codeLength} : instructionScope(local);
            int startPc = scope[0];
            int length = scope[1] - scope[0];
            String desc = local.getType() != null ? local.getType().getDescriptor() : null;
            if (desc == null || local.getName() == null
                    || !LvtSupport.valid(slot, maxLocals, startPc, length, codeLength)) {
                continue;
            }
            entries.add(LvtSupport.entry(constPool, slot, local.getName(), desc, startPc, length));
        }

        LvtSupport.dropSameSlotOverlaps(entries);
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

    /** Block-range scope {@code [startPc, endPc)} spanning the blocks of the local's defs and uses (the default). */
    private int[] blockScope(IRMethod.SourceLocal local) {
        Map<IRBlock, Integer> starts = emitter.getBlockOffsets();
        Map<IRBlock, Integer> ends = emitter.getBlockEndOffsets();
        int startPc = Integer.MAX_VALUE;
        int endPc = -1;
        for (SSAValue v : local.getValues()) {
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
    private int[] instructionScope(IRMethod.SourceLocal local) {
        Map<IRInstruction, Integer> offs = emitter.getInstructionOffsets();
        int startPc = Integer.MAX_VALUE;
        int endPc = -1;
        for (SSAValue v : local.getValues()) {
            startPc = Math.min(startPc, offsetOf(v.getDefinition(), offs, Integer.MAX_VALUE));
            endPc = Math.max(endPc, offsetOf(v.getDefinition(), offs, -1));
            for (IRInstruction use : v.getUses()) {
                startPc = Math.min(startPc, offsetOf(use, offs, Integer.MAX_VALUE));
                endPc = Math.max(endPc, offsetOf(use, offs, -1));
            }
        }
        if (endPc < 0 || startPc == Integer.MAX_VALUE) {
            return blockScope(local);
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
