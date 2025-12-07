package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LivenessAnalysis;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import lombok.Getter;

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
                attr instanceof com.tonic.parser.attribute.LocalVariableTableAttribute ||
                attr instanceof com.tonic.parser.attribute.LineNumberTableAttribute);

            codeAttr.updateLength();

            FrameGenerator frameGen = new FrameGenerator(constPool);
            frameGen.updateStackMapTable(targetMethod);
        }
    }
}
