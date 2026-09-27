package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

/**
 * Conditional Constant Propagation (CCP) optimization transform.
 */
public class ConditionalConstantPropagation implements IRTransform
{

    @Override
    public String getName()
    {
        return "ConditionalConstantPropagation";
    }

    @Override
    public boolean run(IRMethod method)
    {
        boolean changed = false;

        for (IRBlock block : method.getBlocks())
        {
            IRInstruction terminator = block.getTerminator();

            if (terminator instanceof BranchInstruction)
            {
                BranchInstruction branch = (BranchInstruction) terminator;
                Boolean constantResult = BranchConditions.evaluate(branch);

                if (constantResult != null)
                {
                    IRBlock targetBlock = constantResult ? branch.getTrueTarget() : branch.getFalseTarget();
                    IRBlock deadBlock = constantResult ? branch.getFalseTarget() : branch.getTrueTarget();

                    SimpleInstruction gotoInstr = SimpleInstruction.createGoto(targetBlock);
                    gotoInstr.setBlock(block);

                    int idx = block.getInstructions().indexOf(branch);
                    block.removeInstruction(branch);
                    block.insertInstruction(idx, gotoInstr);

                    if (targetBlock != deadBlock)
                    {
                        block.removeSuccessor(deadBlock);
                        deadBlock.getPredecessors().remove(block);
                        removePhiEntriesForRemovedEdge(block, deadBlock);
                    }

                    changed = true;
                }
            }
        }

        return changed;
    }

    /**
     * Drops the incoming values that {@code removedSuccessor}'s phis carried for {@code from}.
     */
    private void removePhiEntriesForRemovedEdge(IRBlock from, IRBlock removedSuccessor)
    {
        for (PhiInstruction phi : removedSuccessor.getPhiInstructions())
        {
            phi.removeIncoming(from);
        }
    }
}
