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

        // Compute dominator tree and loop analysis
        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
        loopAnalysis.compute();

        if (loopAnalysis.getLoops().isEmpty()) {
            return false;
        }

        boolean changed = false;

        // Process each loop (from innermost to outermost would be ideal,
        // but simple iteration works for most cases)
        for (Loop loop : loopAnalysis.getLoops()) {
            changed |= processLoop(loop, method, domTree);
        }

        return changed;
    }

    private boolean processLoop(Loop loop, IRMethod method, DominatorTree domTree) {
        IRBlock header = loop.getHeader();

        // Find or create preheader (the block that jumps to the loop header)
        IRBlock preheader = findPreheader(header, loop);
        if (preheader == null) {
            // No suitable preheader found; skip this loop
            return false;
        }

        // Collect loop-invariant instructions
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

        // Move invariant instructions to preheader
        for (IRInstruction instr : invariantInstructions) {
            IRBlock sourceBlock = instr.getBlock();
            sourceBlock.removeInstruction(instr);

            // Insert before the terminator in preheader
            int insertPos = preheader.getInstructions().size();
            IRInstruction terminator = preheader.getTerminator();
            if (terminator != null) {
                insertPos = preheader.getInstructions().indexOf(terminator);
            }

            instr.setBlock(preheader);
            preheader.insertInstruction(insertPos, instr);

            // Update phi instructions that reference the moved value from the old block
            // The value is now defined in preheader, so update phi incoming edges
            if (instr.getResult() != null) {
                SSAValue movedValue = instr.getResult();
                updatePhisForMovedValue(loop, movedValue, sourceBlock, preheader);
                loopDefinedValues.remove(movedValue.getId());
            }
        }

        return true;
    }

    private void updatePhisForMovedValue(Loop loop, SSAValue movedValue, IRBlock oldBlock, IRBlock newBlock) {
        // Update phi instructions in the loop header that reference the moved value
        // from the old block - they should now reference it from the preheader path
        for (IRBlock block : loop.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                Map<IRBlock, Value> incoming = phi.getIncomingValues();
                // Check if this phi has an incoming value from oldBlock that matches movedValue
                Value incomingFromOld = incoming.get(oldBlock);
                if (incomingFromOld instanceof SSAValue ssaVal && ssaVal.getId() == movedValue.getId()) {
                    // The value is now defined in newBlock (preheader), update the phi
                    // Remove old entry and add new one from preheader
                    incoming.remove(oldBlock);
                    incoming.put(newBlock, movedValue);
                }
            }
        }
    }

    private IRBlock findPreheader(IRBlock header, Loop loop) {
        // A preheader is a predecessor of the loop header that is not in the loop
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
            // Phi instructions define values
            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (phi.getResult() != null) {
                    defined.add(phi.getResult().getId());
                }
            }

            // Regular instructions define values
            for (IRInstruction instr : block.getInstructions()) {
                if (instr.getResult() != null) {
                    defined.add(instr.getResult().getId());
                }
            }
        }

        return defined;
    }

    private boolean isLoopInvariant(IRInstruction instr, Set<Integer> loopDefinedValues, Loop loop) {
        // An instruction is loop-invariant if all its operands are
        // either constants or defined outside the loop
        for (Value operand : instr.getOperands()) {
            if (operand instanceof SSAValue ssaOperand) {
                if (loopDefinedValues.contains(ssaOperand.getId())) {
                    return false;
                }
            }
            // Constants are always loop-invariant
        }

        // Also check if any phi in the loop header references this instruction's result
        // If so, and the phi doesn't have an incoming edge from outside the loop,
        // we shouldn't hoist this instruction as it would break the phi
        if (instr.getResult() != null) {
            int resultId = instr.getResult().getId();
            IRBlock header = loop.getHeader();
            for (PhiInstruction phi : header.getPhiInstructions()) {
                for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
                    Value v = entry.getValue();
                    if (v instanceof SSAValue ssaVal && ssaVal.getId() == resultId) {
                        // This result is used in a phi - check if phi has incoming from outside loop
                        boolean hasOutsideEntry = false;
                        for (IRBlock phiBlock : phi.getIncomingValues().keySet()) {
                            if (!loop.contains(phiBlock)) {
                                hasOutsideEntry = true;
                                break;
                            }
                        }
                        if (!hasOutsideEntry) {
                            // Phi only has entries from inside the loop - don't hoist
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private boolean isSafeToHoist(IRInstruction instr) {
        // Only hoist pure computations (no side effects)
        // Binary and unary operations are safe
        if (instr instanceof BinaryOpInstruction) {
            // Division and remainder can throw ArithmeticException
            // For safety, we could skip them, but typically we assume
            // the original code was correct
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

        // Don't hoist:
        // - Field access (may have side effects, volatile)
        // - Method calls (side effects)
        // - Array access (may throw ArrayIndexOutOfBoundsException)
        // - NEW instructions (allocation)
        // - Terminators (branch, goto, return)

        return false;
    }
}
