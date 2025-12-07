package com.tonic.analysis.visitor;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.io.IOException;

/**
 * Abstract visitor for processing SSA-form IR blocks and instructions.
 */
public abstract class AbstractBlockVisitor implements Visitor<MethodEntry> {

    protected IRMethod irMethod;
    protected MethodEntry method;
    protected ConstPool constPool;

    /**
     * Processes the given method by lifting to SSA and visiting its blocks and instructions.
     *
     * @param method the method entry to process
     * @throws IOException if an I/O error occurs during processing
     */
    @Override
    public void process(MethodEntry method) throws IOException {
        this.method = method;
        this.constPool = method.getClassFile().getConstPool();

        if (method.getCodeAttribute() == null) {
            return;
        }

        SSA ssa = new SSA(constPool);
        this.irMethod = ssa.lift(method);

        if (irMethod.getEntryBlock() == null) {
            return;
        }

        for (IRBlock block : irMethod.getBlocksInOrder()) {
            visitBlock(block);
        }
    }

    /**
     * Visits an IR block.
     *
     * @param block the block to visit
     */
    public void visitBlock(IRBlock block) {
        for (PhiInstruction phi : block.getPhiInstructions()) {
            visitPhi(phi);
        }

        for (IRInstruction instruction : block.getInstructions()) {
            visitInstruction(instruction);
        }
    }

    /**
     * Visits a phi instruction.
     *
     * @param phi the phi instruction to visit
     */
    public void visitPhi(PhiInstruction phi) {}

    /**
     * Visits an IR instruction.
     *
     * @param instruction the instruction to visit
     */
    public void visitInstruction(IRInstruction instruction) {}

    /**
     * Gets the current IR method being visited.
     *
     * @return the IR method
     */
    public IRMethod getIRMethod() {
        return irMethod;
    }

    /**
     * Gets the source method entry.
     *
     * @return the method entry
     */
    public MethodEntry getMethod() {
        return method;
    }
}
