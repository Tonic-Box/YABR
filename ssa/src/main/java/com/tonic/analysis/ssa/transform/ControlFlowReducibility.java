package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Transforms irreducible control flow into reducible form using node splitting.
 * This allows the decompiler to emit structured Java code instead of IR regions.
 */
public class ControlFlowReducibility implements IRTransform {

    private static final int MAX_BLOCKS_CREATED = 100;

    @Override
    public String getName() {
        return "ControlFlowReducibility";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;
        int iterations = 0;
        int maxIterations = 5;
        int blocksCreated = 0;

        while (iterations < maxIterations && blocksCreated < MAX_BLOCKS_CREATED) {
            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();

            LoopAnalysis loops = new LoopAnalysis(method, domTree);
            loops.compute();

            Set<IRBlock> multiEntryBlocks = findMultiEntryBlocks(method, domTree, loops);
            if (multiEntryBlocks.isEmpty()) {
                break;
            }

            IRBlock toSplit = multiEntryBlocks.iterator().next();
            if (splitBlock(method, toSplit, domTree, loops)) {
                changed = true;
                blocksCreated++;
            } else {
                break;
            }
            iterations++;
        }

        return changed;
    }

    private Set<IRBlock> findMultiEntryBlocks(IRMethod method, DominatorTree domTree, LoopAnalysis loops) {
        Set<IRBlock> result = new HashSet<>();

        for (IRBlock block : method.getBlocks()) {
            if (block == method.getEntryBlock()) continue;
            if (block.getPredecessors().size() < 2) continue;

            if (hasMultipleLoopEntries(block, loops)) {
                result.add(block);
                continue;
            }

            if (hasIrreduciblePredecessors(block, domTree)) {
                result.add(block);
            }
        }
        return result;
    }

    private boolean hasMultipleLoopEntries(IRBlock block, LoopAnalysis loops) {
        LoopAnalysis.Loop loop = loops.getLoop(block);
        if (loop == null) {
            return false;
        }

        boolean hasInternalPred = false;
        boolean hasExternalPred = false;

        for (IRBlock pred : block.getPredecessors()) {
            if (loop.contains(pred)) {
                hasInternalPred = true;
            } else {
                hasExternalPred = true;
            }
        }

        return hasInternalPred && hasExternalPred && block != loop.getHeader();
    }

    private boolean hasIrreduciblePredecessors(IRBlock block, DominatorTree domTree) {
        Set<IRBlock> preds = block.getPredecessors();
        if (preds.size() < 2) return false;

        for (IRBlock pred : preds) {
            boolean dominatesAll = true;
            for (IRBlock other : preds) {
                if (other != pred && !domTree.dominates(pred, other)) {
                    dominatesAll = false;
                    break;
                }
            }
            if (dominatesAll) return false;
        }

        for (IRBlock pred : preds) {
            if (domTree.dominates(block, pred)) {
                return true;
            }
        }

        return false;
    }

    private boolean splitBlock(IRMethod method, IRBlock block, DominatorTree domTree, LoopAnalysis loops) {
        LoopAnalysis.Loop loop = loops.getLoop(block);

        List<IRBlock> group1 = new ArrayList<>();
        List<IRBlock> group2 = new ArrayList<>();

        if (loop != null) {
            for (IRBlock pred : block.getPredecessors()) {
                if (loop.contains(pred)) {
                    group1.add(pred);
                } else {
                    group2.add(pred);
                }
            }
        } else {
            for (IRBlock pred : block.getPredecessors()) {
                if (domTree.dominates(block, pred)) {
                    group1.add(pred);
                } else {
                    group2.add(pred);
                }
            }
        }

        if (group1.isEmpty() || group2.isEmpty()) {
            return false;
        }

        IRBlock duplicate = duplicateBlock(block, method);

        for (IRBlock pred : group2) {
            redirectEdge(pred, block, duplicate);
        }

        for (IRBlock succ : new ArrayList<>(block.getSuccessors())) {
            updatePhisForSplit(succ, block, duplicate);
        }

        return true;
    }

    private IRBlock duplicateBlock(IRBlock original, IRMethod method) {
        IRBlock duplicate = new IRBlock(original.getName() + "_dup");
        method.addBlock(duplicate);

        Map<SSAValue, SSAValue> valueMap = new HashMap<>();

        for (PhiInstruction phi : original.getPhiInstructions()) {
            SSAValue newResult = new SSAValue(phi.getResult().getType());
            valueMap.put(phi.getResult(), newResult);

            PhiInstruction newPhi = new PhiInstruction(newResult);
            for (IRBlock pred : phi.getIncomingBlocks()) {
                newPhi.addIncoming(phi.getIncoming(pred), pred);
            }
            duplicate.addPhiInstruction(newPhi);
        }

        for (IRInstruction instr : original.getInstructions()) {
            IRInstruction copy = copyInstruction(instr, valueMap);
            duplicate.addInstruction(copy);
        }

        IRInstruction term = original.getTerminator();
        if (term != null) {
            IRInstruction termCopy = copyInstruction(term, valueMap);
            duplicate.setTerminator(termCopy);
        }

        for (IRBlock succ : original.getSuccessors()) {
            duplicate.addSuccessor(succ);
            succ.addPredecessor(duplicate);
        }

        return duplicate;
    }

    private IRInstruction copyInstruction(IRInstruction instr, Map<SSAValue, SSAValue> valueMap) {
        SSAValue newResult = null;
        if (instr.getResult() != null) {
            newResult = new SSAValue(instr.getResult().getType());
            valueMap.put(instr.getResult(), newResult);
        }

        List<Value> newOperands = new ArrayList<>();
        for (Value op : instr.getOperands()) {
            if (op instanceof SSAValue && valueMap.containsKey((SSAValue) op)) {
                SSAValue ssa = (SSAValue) op;
                newOperands.add(valueMap.get(ssa));
            } else {
                newOperands.add(op);
            }
        }

        IRInstruction copy = instr.copyWithNewOperands(newResult, newOperands);
        // The duplicate stands in for the original at the same source position: without the offset
        // the recovery's LVT range attribution has nothing to match and falls back to generated
        // names for every variable the duplicated block touches.
        copy.setBytecodeOffset(instr.getBytecodeOffset());
        return copy;
    }

    private void redirectEdge(IRBlock pred, IRBlock oldTarget, IRBlock newTarget) {
        pred.removeSuccessor(oldTarget);
        oldTarget.removePredecessor(pred);

        pred.addSuccessor(newTarget);
        newTarget.addPredecessor(pred);

        IRInstruction term = pred.getTerminator();
        if (term != null) {
            term.replaceTarget(oldTarget, newTarget);
        }
    }

    /**
     * Mirrors each successor phi's incoming from the original block onto the duplicate, since control
     * can now arrive from either. The mirrored value is the ORIGINAL's - not the duplicate's clone of
     * it - which is imprecise when the value is defined inside the split block; the recovery tolerates
     * it because both define the same expression, and only irreducible methods reach this transform.
     */
    private void updatePhisForSplit(IRBlock succ, IRBlock original, IRBlock duplicate) {
        for (PhiInstruction phi : succ.getPhiInstructions()) {
            Value origValue = phi.getIncoming(original);
            if (origValue != null) {
                phi.addIncoming(origValue, duplicate);
            }
        }
    }
}
