package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Removes dead code (definitions with no uses).
 */
public class DeadCodeElimination implements IRTransform {

    /**
     * Gets the name of this transformation.
     *
     * @return the transformation name
     */
    @Override
    public String getName() {
        return "DeadCodeElimination";
    }

    /**
     * Runs the dead code elimination transformation on the specified method.
     *
     * @param method the method to transform
     * @return true if the method was modified
     */
    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        Set<IRInstruction> essential = findEssentialInstructions(method);
        Set<IRInstruction> live = new HashSet<>(essential);
        Queue<IRInstruction> worklist = new LinkedList<>(essential);

        while (!worklist.isEmpty()) {
            IRInstruction instr = worklist.poll();
            for (Value operand : instr.getOperands()) {
                if (operand instanceof SSAValue) {
                    SSAValue ssa = (SSAValue) operand;
                    IRInstruction def = ssa.getDefinition();
                    if (def != null && !live.contains(def)) {
                        live.add(def);
                        worklist.add(def);
                    }
                }
            }
        }

        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> toRemove = new ArrayList<>();
            for (IRInstruction instr : block.getInstructions()) {
                if (!live.contains(instr)) {
                    toRemove.add(instr);
                }
            }
            for (IRInstruction instr : toRemove) {
                block.removeInstruction(instr);
                changed = true;
            }

            List<PhiInstruction> phisToRemove = new ArrayList<>();
            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (!live.contains(phi)) {
                    phisToRemove.add(phi);
                }
            }
            for (PhiInstruction phi : phisToRemove) {
                block.removePhi(phi);
                changed = true;
            }
        }

        if (changed) {
            removeUnreachableBlocks(method);
        }

        return changed;
    }

    private Set<IRInstruction> findEssentialInstructions(IRMethod method) {
        Set<IRInstruction> essential = new HashSet<>();

        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (isEssential(instr)) {
                    essential.add(instr);
                }
            }
        }

        // Pin the leading self-copy of each handler block: the lowerer turns it into the astore that captures
        // the JVM-pushed exception. The caught exception has no defining instruction, so liveness never reaches
        // the marker; without pinning it the sweep drops it and the capturing astore is lost.
        for (ExceptionHandler handler : method.getExceptionHandlers()) {
            IRBlock handlerBlock = handler.getHandlerBlock();
            if (handlerBlock == null || handlerBlock.getInstructions().isEmpty()) {
                continue;
            }
            IRInstruction first = handlerBlock.getInstructions().get(0);
            if (first instanceof CopyInstruction) {
                CopyInstruction copy = (CopyInstruction) first;
                if (copy.getResult() != null && copy.getSource() == copy.getResult()) {
                    essential.add(first);
                }
            }
        }

        return essential;
    }

    private boolean isEssential(IRInstruction instr) {
        return instr.isTerminator() || InstructionEffects.hasSideEffects(instr);
    }

    /**
     * Removes blocks not reachable from the method entry or any exception handler. This is a purely
     * structural CFG cleanup (no SSA def/use assumptions), so it is safe to run at any lowering stage —
     * e.g. before bytecode emission, to drop unreachable join blocks (such as the fall-through after an
     * exhaustive {@code switch}) that would otherwise emit a stray trailing terminator.
     *
     * @param method the method whose unreachable blocks to remove
     */
    public static void removeUnreachableBlocks(IRMethod method) {
        if (method.getEntryBlock() == null) return;

        Set<IRBlock> reachable = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(method.getEntryBlock());

        for (ExceptionHandler h : method.getExceptionHandlers()) {
            if (h.getHandlerBlock() != null) {
                worklist.add(h.getHandlerBlock());
            }
        }

        while (!worklist.isEmpty()) {
            IRBlock block = worklist.poll();
            if (reachable.contains(block)) continue;
            reachable.add(block);
            worklist.addAll(block.getSuccessors());
        }

        List<IRBlock> toRemove = new ArrayList<>();
        for (IRBlock block : method.getBlocks()) {
            if (!reachable.contains(block)) {
                toRemove.add(block);
            }
        }

        for (IRBlock block : toRemove) {
            method.removeBlock(block);
        }
    }
}
