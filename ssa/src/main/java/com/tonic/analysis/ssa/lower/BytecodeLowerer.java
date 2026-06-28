package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LivenessAnalysis;
import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.LoadLocalInstruction;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.analysis.ssa.transform.DeadCodeElimination;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LineNumberTableAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowers SSA-form IR back to JVM bytecode.
 */
@Getter
public class BytecodeLowerer {

    private final ConstPool constPool;
    private final boolean emitLocalVariableTable;

    public BytecodeLowerer(ConstPool constPool) {
        this(constPool, true);
    }

    public BytecodeLowerer(ConstPool constPool, boolean emitLocalVariableTable) {
        this.constPool = constPool;
        this.emitLocalVariableTable = emitLocalVariableTable;
    }

    public void lower(IRMethod irMethod, MethodEntry targetMethod) {
        // Remove now-dead LoadLocal/StoreLocal artifacts that reference stale local indices.
        removeLocalInstructionArtifacts(irMethod);

        // Drop blocks unreachable from entry/handlers (e.g. the fall-through join after an exhaustive
        // switch) so the emitter never produces a stray trailing return/goto for dead code.
        DeadCodeElimination.removeUnreachableBlocks(irMethod);

        PhiEliminator phiEliminator = new PhiEliminator();
        phiEliminator.eliminate(irMethod);

        DominatorTree domTree = new DominatorTree(irMethod);
        domTree.compute();

        LivenessAnalysis liveness = new LivenessAnalysis(irMethod);
        liveness.compute();

        RegisterAllocator regAlloc = new RegisterAllocator(irMethod, liveness);
        regAlloc.allocate();

        StackScheduler scheduler = new StackScheduler(irMethod, regAlloc);
        scheduler.schedule();

        BytecodeEmitter emitter = new BytecodeEmitter(irMethod, constPool, regAlloc, scheduler);
        byte[] bytecode = emitter.emit();

        CodeAttribute codeAttr = targetMethod.getCodeAttribute();
        if (codeAttr != null) {
            codeAttr.setCode(bytecode);
            codeAttr.setMaxStack(scheduler.getMaxStack());
            codeAttr.setMaxLocals(regAlloc.getMaxLocals());

            List<ExceptionTableEntry> newExceptionTable = regenerateExceptionTable(irMethod, emitter);
            codeAttr.getExceptionTable().clear();
            codeAttr.getExceptionTable().addAll(newExceptionTable);

            codeAttr.getAttributes().removeIf(attr ->
                attr instanceof LocalVariableTableAttribute ||
                attr instanceof LineNumberTableAttribute);

            if (emitLocalVariableTable) {
                LocalVariableTableAttribute lvt = new LocalVariableTableBuilder(
                        irMethod, regAlloc, emitter, bytecode.length, constPool, targetMethod).build();
                if (lvt != null) {
                    codeAttr.getAttributes().add(lvt);
                }
            }

            codeAttr.updateLength();

            FrameGenerator frameGen = new FrameGenerator(constPool);
            frameGen.updateStackMapTable(targetMethod);
        }
    }

    private List<ExceptionTableEntry> regenerateExceptionTable(IRMethod irMethod, BytecodeEmitter emitter) {
        List<ExceptionTableEntry> entries = new ArrayList<>();
        Map<IRBlock, Integer> offsets = emitter.getBlockOffsets();
        Map<IRBlock, Integer> endOffsets = emitter.getBlockEndOffsets();

        for (ExceptionHandler handler : irMethod.getExceptionHandlers()) {
            IRBlock handlerBlock = handler.getHandlerBlock();
            if (!offsets.containsKey(handlerBlock)) {
                continue;
            }
            int handlerPc = offsets.get(handlerBlock);

            int catchType = 0;
            if (handler.getCatchType() != null) {
                catchType = constPool.findOrAddClass(handler.getCatchType().getInternalName()).getIndex(constPool);
            }

            if (handler.getTryBlocks() != null && !handler.getTryBlocks().isEmpty()) {
                for (int[] run : contiguousRuns(handler.getTryBlocks(), offsets, endOffsets)) {
                    entries.add(new ExceptionTableEntry(run[0], run[1], handlerPc, catchType));
                }
                continue;
            }

            IRBlock tryStart = handler.getTryStart();
            IRBlock tryEnd = handler.getTryEnd();
            if (!offsets.containsKey(tryStart)) {
                continue;
            }
            int startPc = offsets.get(tryStart);
            int endPc;
            if (tryEnd != null && endOffsets.containsKey(tryEnd)) {
                endPc = endOffsets.get(tryEnd);
            } else {
                endPc = endOffsets.getOrDefault(tryStart, startPc + 1);
            }
            if (startPc < endPc) {
                entries.add(new ExceptionTableEntry(startPc, endPc, handlerPc, catchType));
            }
        }

        return entries;
    }

    /**
     * Collapses the emitted byte ranges of a protected region's blocks into maximal contiguous runs. A nested
     * try whose body is interrupted by an interleaved handler emits as several non-adjacent ranges; each run
     * becomes one exception-table entry, matching how javac splits such a region.
     */
    private List<int[]> contiguousRuns(Set<IRBlock> tryBlocks, Map<IRBlock, Integer> offsets,
                                       Map<IRBlock, Integer> endOffsets) {
        List<int[]> intervals = new ArrayList<>();
        for (IRBlock block : tryBlocks) {
            if (offsets.containsKey(block) && endOffsets.containsKey(block)) {
                int start = offsets.get(block);
                int end = endOffsets.get(block);
                if (start < end) {
                    intervals.add(new int[]{start, end});
                }
            }
        }
        intervals.sort(Comparator.comparingInt(a -> a[0]));

        List<int[]> runs = new ArrayList<>();
        for (int[] interval : intervals) {
            if (!runs.isEmpty() && interval[0] <= runs.get(runs.size() - 1)[1]) {
                int[] last = runs.get(runs.size() - 1);
                last[1] = Math.max(last[1], interval[1]);
            } else {
                runs.add(new int[]{interval[0], interval[1]});
            }
        }
        return runs;
    }

    /**
     * Removes LoadLocalInstruction and StoreLocalInstruction artifacts from the IR.
     * <p>
     * After SSA lifting, these instructions are artifacts from the initial bytecode
     * conversion. The VariableRenamer replaces their results with actual SSA values,
     * making them dead. Keeping them causes incorrect bytecode because:
     * 1. LoadLocalInstruction references stale local indices from the original method
     * 2. StoreLocalInstruction stores to indices that may not exist in the current frame
     * 3. For inlined code, these indices reference the callee's frame, not the caller's
     * <p>
     * In proper SSA form, all data flow is through SSAValue uses, not local variable slots.
     */
    private void removeLocalInstructionArtifacts(IRMethod method) {
        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> toRemove = new ArrayList<>();

            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof LoadLocalInstruction) {
                    toRemove.add(instr);
                } else if (instr instanceof StoreLocalInstruction) {
                    toRemove.add(instr);
                }
            }

            for (IRInstruction instr : toRemove) {
                block.removeInstruction(instr);
            }
        }
    }
}
