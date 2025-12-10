package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;

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
            for (com.tonic.analysis.ssa.value.Value operand : instr.getOperands()) {
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

        return essential;
    }

    private boolean isEssential(IRInstruction instr) {
        return instr instanceof ReturnInstruction
                || instr instanceof ThrowInstruction
                || instr instanceof InvokeInstruction
                || instr instanceof PutFieldInstruction
                || instr instanceof ArrayStoreInstruction
                || instr instanceof MonitorEnterInstruction
                || instr instanceof MonitorExitInstruction
                || instr instanceof GotoInstruction
                || instr instanceof BranchInstruction
                || instr instanceof SwitchInstruction;
    }

    private void removeUnreachableBlocks(IRMethod method) {
        if (method.getEntryBlock() == null) return;

        Set<IRBlock> reachable = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(method.getEntryBlock());

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
