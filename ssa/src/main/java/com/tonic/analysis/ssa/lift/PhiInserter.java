package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;

import java.util.*;

/**
 * Inserts phi functions at dominance frontiers.
 * Uses the Cytron algorithm for minimal SSA form.
 */
public class PhiInserter {

    private final DominatorTree dominatorTree;
    private Map<Integer, Set<IRBlock>> varDefBlocks;
    private Map<Integer, IRType> varTypes;

    public PhiInserter(DominatorTree dominatorTree) {
        this.dominatorTree = dominatorTree;
    }

    public void insertPhis(IRMethod method) {
        collectVariableDefinitions(method);
        insertPhiFunctions(method);
    }

    private void collectVariableDefinitions(IRMethod method) {
        varDefBlocks = new HashMap<>();
        varTypes = new HashMap<>();

        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof StoreLocalInstruction) {
                    StoreLocalInstruction store = (StoreLocalInstruction) instr;
                    int localIndex = store.getLocalIndex();
                    varDefBlocks.computeIfAbsent(localIndex, k -> new HashSet<>()).add(block);

                    if (store.getValue() instanceof SSAValue) {
                        SSAValue ssaVal = (SSAValue) store.getValue();
                        varTypes.putIfAbsent(localIndex, ssaVal.getType());
                    }
                }
            }
        }
    }

    private void insertPhiFunctions(IRMethod method) {
        Set<IRBlock> dominanceFrontiers = dominatorTree.getDominanceFrontiers();

        for (Map.Entry<Integer, Set<IRBlock>> entry : varDefBlocks.entrySet()) {
            int varIndex = entry.getKey();
            Set<IRBlock> defBlocks = entry.getValue();
            IRType varType = varTypes.get(varIndex);
            if (varType == null) continue;

            Set<IRBlock> processed = new HashSet<>();
            Queue<IRBlock> worklist = new LinkedList<>(defBlocks);

            while (!worklist.isEmpty()) {
                IRBlock block = worklist.poll();
                Set<IRBlock> df = dominatorTree.getDominanceFrontier(block);

                for (IRBlock frontierBlock : df) {
                    if (processed.contains(frontierBlock)) continue;
                    processed.add(frontierBlock);

                    if (!hasPhiForVariable(frontierBlock, varIndex)) {
                        SSAValue phiResult = new SSAValue(varType, "phi_" + varIndex);
                        PhiInstruction phi = new PhiInstruction(phiResult);
                        frontierBlock.addPhi(phi);
                    }

                    if (!defBlocks.contains(frontierBlock)) {
                        worklist.add(frontierBlock);
                    }
                }
            }
        }
    }

    private boolean hasPhiForVariable(IRBlock block, int varIndex) {
        for (PhiInstruction phi : block.getPhiInstructions()) {
            if (phi.getResult().getName().equals("phi_" + varIndex)) {
                return true;
            }
        }
        return false;
    }
}
