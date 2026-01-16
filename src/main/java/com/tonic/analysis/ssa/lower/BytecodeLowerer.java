package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LivenessAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.LoadLocalInstruction;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LineNumberTableAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowers SSA-form IR back to JVM bytecode.
 */
@Getter
public class BytecodeLowerer {

    private final ConstPool constPool;

    public BytecodeLowerer(ConstPool constPool) {
        this.constPool = constPool;
    }

    public void lower(IRMethod irMethod, MethodEntry targetMethod) {
        // Remove LoadLocalInstruction and StoreLocalInstruction artifacts.
        // After SSA conversion, these are dead - VariableRenamer replaces their
        // results with actual SSA values. Keeping them causes incorrect bytecode
        // because they reference stale local variable indices.
        removeLocalInstructionArtifacts(irMethod);

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

            // Remove stale debug attributes - they reference old offsets/slots
            codeAttr.getAttributes().removeIf(attr ->
                attr instanceof LocalVariableTableAttribute ||
                attr instanceof LineNumberTableAttribute);

            codeAttr.updateLength();

            FrameGenerator frameGen = new FrameGenerator(constPool);
            frameGen.updateStackMapTable(targetMethod);
        }
    }

    /**
     * Removes LoadLocalInstruction and StoreLocalInstruction artifacts from the IR.
     *
     * After SSA lifting, these instructions are artifacts from the initial bytecode
     * conversion. The VariableRenamer replaces their results with actual SSA values,
     * making them dead. Keeping them causes incorrect bytecode because:
     * 1. LoadLocalInstruction references stale local indices from the original method
     * 2. StoreLocalInstruction stores to indices that may not exist in the current frame
     * 3. For inlined code, these indices reference the callee's frame, not the caller's
     *
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
