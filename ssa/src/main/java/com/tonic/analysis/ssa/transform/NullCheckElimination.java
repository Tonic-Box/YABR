package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Null Check Elimination optimization transform.
 */
public class NullCheckElimination implements IRTransform
{

    @Override
    public String getName()
    {
        return "NullCheckElimination";
    }

    @Override
    public boolean run(IRMethod method)
    {
        if (method.getEntryBlock() == null)
        {
            return false;
        }

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        Map<IRBlock, Set<Integer>> nonNullOnExit = new HashMap<>();
        boolean changed = false;

        for (IRBlock block : dominatorPreorder(method, domTree))
        {
            Set<Integer> nonNull = nonNullOnEntry(method, block, domTree, nonNullOnExit);
            changed |= processBlock(block, nonNull);
            nonNullOnExit.put(block, nonNull);
        }

        return changed;
    }

    /**
     * The blocks in dominator-tree preorder, so a block is visited after the dominator whose facts it
     * inherits.
     */
    private List<IRBlock> dominatorPreorder(IRMethod method, DominatorTree domTree)
    {
        List<IRBlock> order = new ArrayList<>();
        Deque<IRBlock> work = new ArrayDeque<>();
        work.push(method.getEntryBlock());

        Set<IRBlock> seen = new HashSet<>();
        while (!work.isEmpty())
        {
            IRBlock block = work.pop();
            if (!seen.add(block))
            {
                continue;
            }
            order.add(block);
            for (IRBlock child : domTree.getDominatorTreeChildren(block))
            {
                work.push(child);
            }
        }
        return order;
    }

    /**
     * The values known non-null on entry to {@code block}: those established by its immediate dominator,
     * which runs on every path here, plus the operand of a null guard when the guarded arm is the only way
     * in. Facts from a sibling branch are never inherited - it may not have run.
     */
    private Set<Integer> nonNullOnEntry(IRMethod method, IRBlock block, DominatorTree domTree, Map<IRBlock, Set<Integer>> nonNullOnExit)
    {
        if (block == method.getEntryBlock())
        {
            Set<Integer> facts = new HashSet<>();
            if (!method.isStatic() && !method.getParameters().isEmpty())
            {
                facts.add(method.getParameters().get(0).getId());
            }
            return facts;
        }

        IRBlock idom = domTree.getImmediateDominator(block);
        Set<Integer> inherited = idom == null ? null : nonNullOnExit.get(idom);
        Set<Integer> facts = inherited == null ? new HashSet<>() : new HashSet<>(inherited);
        addGuardedNonNull(idom, block, facts);
        return facts;
    }

    /**
     * Adds the operand of {@code idom}'s null guard when {@code block} is the arm that guard proves non-null
     * and no other edge reaches it.
     */
    private void addGuardedNonNull(IRBlock idom, IRBlock block, Set<Integer> facts)
    {
        if (idom == null || !(idom.getTerminator() instanceof BranchInstruction))
        {
            return;
        }

        BranchInstruction branch = (BranchInstruction) idom.getTerminator();
        CompareOp cond = branch.getCondition();
        if (cond != CompareOp.IFNULL && cond != CompareOp.IFNONNULL)
        {
            return;
        }
        if (!(branch.getLeft() instanceof SSAValue))
        {
            return;
        }

        IRBlock nonNullArm = cond == CompareOp.IFNONNULL ? branch.getTrueTarget() : branch.getFalseTarget();
        if (block != nonNullArm)
        {
            return;
        }

        Set<IRBlock> preds = block.getPredecessors();
        if (preds.size() != 1 || !preds.contains(idom))
        {
            return;
        }

        facts.add(((SSAValue) branch.getLeft()).getId());
    }

    /**
     * Records the block's own non-null definitions and folds its null guard when the operand is already
     * known non-null.
     */
    private boolean processBlock(IRBlock block, Set<Integer> nonNull)
    {
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr instanceof NewInstruction)
            {
                SSAValue result = instr.getResult();
                if (result != null)
                {
                    nonNull.add(result.getId());
                }
            }
        }

        IRInstruction terminator = block.getTerminator();
        if (!(terminator instanceof BranchInstruction))
        {
            return false;
        }

        BranchInstruction branch = (BranchInstruction) terminator;
        CompareOp cond = branch.getCondition();
        if (cond != CompareOp.IFNULL && cond != CompareOp.IFNONNULL)
        {
            return false;
        }
        if (!(branch.getLeft() instanceof SSAValue))
        {
            return false;
        }
        if (!nonNull.contains(((SSAValue) branch.getLeft()).getId()))
        {
            return false;
        }

        IRBlock target = cond == CompareOp.IFNULL ? branch.getFalseTarget() : branch.getTrueTarget();
        IRBlock dead = cond == CompareOp.IFNULL ? branch.getTrueTarget() : branch.getFalseTarget();

        SimpleInstruction gotoInstr = SimpleInstruction.createGoto(target);
        gotoInstr.setBlock(block);

        int idx = block.getInstructions().indexOf(branch);
        block.removeInstruction(branch);
        block.insertInstruction(idx, gotoInstr);

        if (target != dead)
        {
            block.removeSuccessor(dead);
            dead.getPredecessors().remove(block);
            for (PhiInstruction phi : dead.getPhiInstructions())
            {
                phi.removeIncoming(block);
            }
        }

        return true;
    }
}
