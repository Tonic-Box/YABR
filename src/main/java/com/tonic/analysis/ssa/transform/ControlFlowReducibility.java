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

    @Override
    public String getName() {
        return "ControlFlowReducibility";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;
        int iterations = 0;
        int maxIterations = 5; // Prevent infinite loops

        while (iterations < maxIterations) {
            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();

            LoopAnalysis loops = new LoopAnalysis(method, domTree);
            loops.compute();

            Set<IRBlock> multiEntryBlocks = findMultiEntryBlocks(method, domTree, loops);
            if (multiEntryBlocks.isEmpty()) {
                break;
            }

            // Split one block at a time and recompute
            IRBlock toSplit = multiEntryBlocks.iterator().next();
            if (splitBlock(method, toSplit, domTree, loops)) {
                changed = true;
            } else {
                break; // Can't split - avoid infinite loop
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

            // Check for loop-based irreducibility
            if (hasMultipleLoopEntries(block, loops)) {
                result.add(block);
                continue;
            }

            // Check for general irreducibility: multiple preds where none dominates the others
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
        List<IRBlock> preds = block.getPredecessors();
        if (preds.size() < 2) return false;

        // Check if any predecessor dominates all others (reducible merge point)
        for (IRBlock pred : preds) {
            boolean dominatesAll = true;
            for (IRBlock other : preds) {
                if (other != pred && !domTree.dominates(pred, other)) {
                    dominatesAll = false;
                    break;
                }
            }
            if (dominatesAll) return false; // This is a reducible merge
        }

        // No single predecessor dominates all - could be irreducible
        // but we need to be careful not to split normal merge points
        // Check if there's a back edge involved
        for (IRBlock pred : preds) {
            if (domTree.dominates(block, pred)) {
                // Back edge exists - this might be irreducible
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
            // Split based on loop membership
            for (IRBlock pred : block.getPredecessors()) {
                if (loop.contains(pred)) {
                    group1.add(pred);
                } else {
                    group2.add(pred);
                }
            }
        } else {
            // Split based on back edges
            for (IRBlock pred : block.getPredecessors()) {
                if (domTree.dominates(block, pred)) {
                    group1.add(pred); // Back edge
                } else {
                    group2.add(pred); // Forward edge
                }
            }
        }

        if (group1.isEmpty() || group2.isEmpty()) {
            return false;
        }

        // Create duplicate block for group2 predecessors
        IRBlock duplicate = duplicateBlock(block, method);
        if (duplicate == null) {
            return false;
        }

        // Redirect group2 predecessors to duplicate
        for (IRBlock pred : group2) {
            redirectEdge(pred, block, duplicate);
        }

        // Update phi instructions in successors
        for (IRBlock succ : new ArrayList<>(block.getSuccessors())) {
            updatePhisForSplit(succ, block, duplicate, group2);
        }

        return true;
    }

    private IRBlock duplicateBlock(IRBlock original, IRMethod method) {
        IRBlock duplicate = new IRBlock(original.getName() + "_dup");
        method.addBlock(duplicate);

        Map<SSAValue, SSAValue> valueMap = new HashMap<>();

        // Duplicate phi instructions
        for (PhiInstruction phi : original.getPhiInstructions()) {
            SSAValue newResult = new SSAValue(phi.getResult().getType());
            valueMap.put(phi.getResult(), newResult);

            PhiInstruction newPhi = new PhiInstruction(newResult);
            for (IRBlock pred : phi.getIncomingBlocks()) {
                newPhi.addIncoming(phi.getIncoming(pred), pred);
            }
            duplicate.addPhiInstruction(newPhi);
        }

        // Duplicate regular instructions
        for (IRInstruction instr : original.getInstructions()) {
            IRInstruction copy = copyInstruction(instr, valueMap);
            if (copy != null) {
                duplicate.addInstruction(copy);
            }
        }

        // Copy terminator and successors
        IRInstruction term = original.getTerminator();
        if (term != null) {
            IRInstruction termCopy = copyInstruction(term, valueMap);
            if (termCopy != null) {
                duplicate.setTerminator(termCopy);
            }
        }

        // Copy successor edges
        for (IRBlock succ : original.getSuccessors()) {
            duplicate.addSuccessor(succ);
            succ.addPredecessor(duplicate);
        }

        return duplicate;
    }

    private IRInstruction copyInstruction(IRInstruction instr, Map<SSAValue, SSAValue> valueMap) {
        // Create new result if instruction has one
        SSAValue newResult = null;
        if (instr.getResult() != null) {
            newResult = new SSAValue(instr.getResult().getType());
            valueMap.put(instr.getResult(), newResult);
        }

        // Map operands
        List<Value> newOperands = new ArrayList<>();
        for (Value op : instr.getOperands()) {
            if (op instanceof SSAValue ssa && valueMap.containsKey(ssa)) {
                newOperands.add(valueMap.get(ssa));
            } else {
                newOperands.add(op);
            }
        }

        return instr.copyWithNewOperands(newResult, newOperands);
    }

    private void redirectEdge(IRBlock pred, IRBlock oldTarget, IRBlock newTarget) {
        pred.removeSuccessor(oldTarget);
        oldTarget.removePredecessor(pred);

        pred.addSuccessor(newTarget);
        newTarget.addPredecessor(pred);

        // Update terminator
        IRInstruction term = pred.getTerminator();
        if (term != null) {
            term.replaceTarget(oldTarget, newTarget);
        }
    }

    private void updatePhisForSplit(IRBlock succ, IRBlock original, IRBlock duplicate, List<IRBlock> movedPreds) {
        for (PhiInstruction phi : succ.getPhiInstructions()) {
            Value origValue = phi.getIncoming(original);
            if (origValue != null) {
                phi.addIncoming(origValue, duplicate);
            }
        }
    }
}
