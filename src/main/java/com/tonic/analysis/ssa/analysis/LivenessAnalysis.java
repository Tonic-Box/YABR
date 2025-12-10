package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import lombok.Getter;

import java.util.*;

/**
 * Computes liveness information for SSA values.
 */
@Getter
public class LivenessAnalysis {

    private final IRMethod method;
    private Map<IRBlock, Set<SSAValue>> liveIn;
    private Map<IRBlock, Set<SSAValue>> liveOut;
    private Map<SSAValue, Set<IRBlock>> liveBlocks;

    public LivenessAnalysis(IRMethod method) {
        this.method = method;
        this.liveIn = new HashMap<>();
        this.liveOut = new HashMap<>();
        this.liveBlocks = new HashMap<>();
    }

    /**
     * Computes liveness information for all blocks.
     */
    public void compute() {
        for (IRBlock block : method.getBlocks()) {
            liveIn.put(block, new HashSet<>());
            liveOut.put(block, new HashSet<>());
        }

        boolean changed = true;
        while (changed) {
            changed = false;

            List<IRBlock> postOrder = method.getPostOrder();
            for (IRBlock block : postOrder) {
                Set<SSAValue> newLiveOut = new HashSet<>();
                for (IRBlock succ : block.getSuccessors()) {
                    newLiveOut.addAll(liveIn.get(succ));
                }

                Set<SSAValue> newLiveIn = new HashSet<>(newLiveOut);
                Set<SSAValue> defs = getDefinitions(block);
                newLiveIn.removeAll(defs);
                newLiveIn.addAll(getUses(block));

                if (!newLiveIn.equals(liveIn.get(block)) || !newLiveOut.equals(liveOut.get(block))) {
                    liveIn.put(block, newLiveIn);
                    liveOut.put(block, newLiveOut);
                    changed = true;
                }
            }
        }

        computeLiveBlocks();
    }

    private Set<SSAValue> getDefinitions(IRBlock block) {
        Set<SSAValue> defs = new HashSet<>();
        for (PhiInstruction phi : block.getPhiInstructions()) {
            if (phi.getResult() != null) {
                defs.add(phi.getResult());
            }
        }
        for (IRInstruction instr : block.getInstructions()) {
            if (instr.getResult() != null) {
                defs.add(instr.getResult());
            }
        }
        return defs;
    }

    private Set<SSAValue> getUses(IRBlock block) {
        Set<SSAValue> uses = new HashSet<>();
        for (PhiInstruction phi : block.getPhiInstructions()) {
            for (Value v : phi.getOperands()) {
                if (v instanceof SSAValue) {
                    SSAValue ssa = (SSAValue) v;
                    uses.add(ssa);
                }
            }
        }
        for (IRInstruction instr : block.getInstructions()) {
            for (Value v : instr.getOperands()) {
                if (v instanceof SSAValue) {
                    SSAValue ssa = (SSAValue) v;
                    uses.add(ssa);
                }
            }
        }
        return uses;
    }

    private void computeLiveBlocks() {
        for (IRBlock block : method.getBlocks()) {
            for (SSAValue val : liveIn.get(block)) {
                liveBlocks.computeIfAbsent(val, k -> new HashSet<>()).add(block);
            }
            for (SSAValue val : liveOut.get(block)) {
                liveBlocks.computeIfAbsent(val, k -> new HashSet<>()).add(block);
            }
        }
    }

    /**
     * Gets the set of values live at the entry of the specified block.
     *
     * @param block the block to query
     * @return the set of live-in values
     */
    public Set<SSAValue> getLiveIn(IRBlock block) {
        return liveIn.getOrDefault(block, Collections.emptySet());
    }

    /**
     * Gets the set of values live at the exit of the specified block.
     *
     * @param block the block to query
     * @return the set of live-out values
     */
    public Set<SSAValue> getLiveOut(IRBlock block) {
        return liveOut.getOrDefault(block, Collections.emptySet());
    }

    /**
     * Checks if a value is live at the specified block.
     *
     * @param value the value to check
     * @param block the block to query
     * @return true if the value is live at the block
     */
    public boolean isLiveAt(SSAValue value, IRBlock block) {
        return liveIn.getOrDefault(block, Collections.emptySet()).contains(value) ||
               liveOut.getOrDefault(block, Collections.emptySet()).contains(value);
    }

    /**
     * Gets the set of blocks where the specified value is live.
     *
     * @param value the value to query
     * @return the set of blocks where the value is live
     */
    public Set<IRBlock> getLiveBlocks(SSAValue value) {
        return liveBlocks.getOrDefault(value, Collections.emptySet());
    }
}
