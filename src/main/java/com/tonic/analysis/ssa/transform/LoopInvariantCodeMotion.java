package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.analysis.LoopAnalysis.Loop;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Loop-Invariant Code Motion (LICM) optimization transform.
 *
 * Moves loop-invariant computations outside the loop:
 * - An instruction is loop-invariant if all its operands are defined outside
 *   the loop or are constants
 * - Only pure computations (no side effects) are moved
 * - Instructions are moved to the loop preheader
 */
public class LoopInvariantCodeMotion implements IRTransform {

    @Override
    public String getName() {
        return "LoopInvariantCodeMotion";
    }

    @Override
    public boolean run(IRMethod method) {
        if (method.getEntryBlock() == null) {
            return false;
        }

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
        loopAnalysis.compute();

        if (loopAnalysis.getLoops().isEmpty()) {
            return false;
        }

        boolean changed = false;

        for (Loop loop : loopAnalysis.getLoops()) {
            changed |= processLoop(loop, method, domTree);
        }

        return changed;
    }

    private boolean processLoop(Loop loop, IRMethod method, DominatorTree domTree) {
        IRBlock header = loop.getHeader();

        IRBlock preheader = findPreheader(header, loop);
        if (preheader == null) {
            return false;
        }

        List<IRInstruction> invariantInstructions = new ArrayList<>();
        Set<Integer> loopDefinedValues = collectLoopDefinedValues(loop);

        for (IRBlock block : loop.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (isLoopInvariant(instr, loopDefinedValues, loop) && isSafeToHoist(instr)) {
                    invariantInstructions.add(instr);
                }
            }
        }

        if (invariantInstructions.isEmpty()) {
            return false;
        }

        for (IRInstruction instr : invariantInstructions) {
            IRBlock sourceBlock = instr.getBlock();
            sourceBlock.removeInstruction(instr);

            int insertPos = preheader.getInstructions().size();
            IRInstruction terminator = preheader.getTerminator();
            if (terminator != null) {
                insertPos = preheader.getInstructions().indexOf(terminator);
            }

            instr.setBlock(preheader);
            preheader.insertInstruction(insertPos, instr);

            if (instr.getResult() != null) {
                SSAValue movedValue = instr.getResult();
                updatePhisForMovedValue(loop, movedValue, sourceBlock, preheader);
                loopDefinedValues.remove(movedValue.getId());
            }
        }

        return true;
    }

    private void updatePhisForMovedValue(Loop loop, SSAValue movedValue, IRBlock oldBlock, IRBlock newBlock) {
        for (IRBlock block : loop.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                Map<IRBlock, Value> incoming = phi.getIncomingValues();
                Value incomingFromOld = incoming.get(oldBlock);
                if (incomingFromOld instanceof SSAValue) {
                    SSAValue ssaVal = (SSAValue) incomingFromOld;
                    if (ssaVal.getId() == movedValue.getId()) {
                        incoming.remove(oldBlock);
                        incoming.put(newBlock, movedValue);
                    }
                }
            }
        }
    }

    private IRBlock findPreheader(IRBlock header, Loop loop) {
        for (IRBlock pred : header.getPredecessors()) {
            if (!loop.contains(pred)) {
                return pred;
            }
        }
        return null;
    }

    private Set<Integer> collectLoopDefinedValues(Loop loop) {
        Set<Integer> defined = new HashSet<>();

        for (IRBlock block : loop.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (phi.getResult() != null) {
                    defined.add(phi.getResult().getId());
                }
            }

            for (IRInstruction instr : block.getInstructions()) {
                if (instr.getResult() != null) {
                    defined.add(instr.getResult().getId());
                }
            }
        }

        return defined;
    }

    private boolean isLoopInvariant(IRInstruction instr, Set<Integer> loopDefinedValues, Loop loop) {
        for (Value operand : instr.getOperands()) {
            if (operand instanceof SSAValue) {
                SSAValue ssaOperand = (SSAValue) operand;
                if (loopDefinedValues.contains(ssaOperand.getId())) {
                    return false;
                }
            }
        }

        if (instr.getResult() != null) {
            int resultId = instr.getResult().getId();
            IRBlock header = loop.getHeader();
            for (PhiInstruction phi : header.getPhiInstructions()) {
                for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
                    Value v = entry.getValue();
                    if (v instanceof SSAValue) {
                        SSAValue ssaVal = (SSAValue) v;
                        if (ssaVal.getId() == resultId) {
                            boolean hasOutsideEntry = false;
                            for (IRBlock phiBlock : phi.getIncomingValues().keySet()) {
                                if (!loop.contains(phiBlock)) {
                                    hasOutsideEntry = true;
                                    break;
                                }
                            }
                            if (!hasOutsideEntry) {
                                return false;
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    private boolean isSafeToHoist(IRInstruction instr) {
        if (instr instanceof BinaryOpInstruction) {
            return true;
        }
        if (instr instanceof UnaryOpInstruction) {
            return true;
        }
        if (instr instanceof ConstantInstruction) {
            return true;
        }
        if (instr instanceof CopyInstruction) {
            return true;
        }

        return false;
    }
}
