package com.tonic.analysis.absexec;

import com.tonic.analysis.instruction.Instruction;

import static com.tonic.util.Opcode.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Records one execution of one {@link Instruction} by the abstract {@link Execution} - its operand-stack pops
 * and pushes (with def-use links back to the {@code InsnContext} that produced each value) and its local
 * reads.
 */
public final class InsnContext
{

    private final Instruction insn;
    private final Frame frame;
    private final List<StackCtx> pops = new ArrayList<>();
    private final List<StackCtx> pushes = new ArrayList<>();
    private final List<VarCtx> reads = new ArrayList<>();
    private final List<Frame> branches = new ArrayList<>();

    /**
     * Creates a context for one execution of an instruction within a frame.
     * @param insn the executed instruction
     * @param frame the frame executing it
     */
    public InsnContext(Instruction insn, Frame frame)
    {
        this.insn = insn;
        this.frame = frame;
    }

    /**
     * @return the frame
     */
    public Frame getFrame()
    {
        return frame;
    }

    /**
     * @return the popped stack values, in pop order (index 0 = top of stack)
     */
    public List<StackCtx> getPops()
    {
        return pops;
    }

    /**
     * @return the pushes
     */
    public List<StackCtx> getPushes()
    {
        return pushes;
    }

    /**
     * @return the reads
     */
    public List<VarCtx> getReads()
    {
        return reads;
    }

    /**
     * @return the branches
     */
    public List<Frame> getBranches()
    {
        return branches;
    }

    /**
     * Records each popped stack value and back-links it to this instruction.
     * @param ctx the popped stack contexts
     */
    public void pop(StackCtx... ctx)
    {
        for (StackCtx c : ctx)
        {
            c.addPopped(this);
            pops.add(c);
        }
    }

    /**
     * Records the stack values this instruction pushed.
     * @param ctx the pushed stack contexts
     */
    public void push(StackCtx... ctx)
    {
        pushes.addAll(Arrays.asList(ctx));
    }

    /**
     * Records each read local slot and back-links it to this instruction.
     * @param ctx the local-slot contexts read
     */
    public void read(VarCtx... ctx)
    {
        for (VarCtx c : ctx)
        {
            c.addRead(this);
            reads.add(c);
        }
    }

    /**
     * Records a frame forked by this instruction at a branch target.
     * @param f the forked frame
     */
    public void branch(Frame f)
    {
        branches.add(f);
    }

    /**
     * @return the instruction
     */
    public Instruction getInstruction()
    {
        return insn;
    }

    /**
     * Follows def-use to the instruction that actually produced this context's value.
     *
     * @return the producing context, or this one when the chain ends here
     */
    public InsnContext resolve()
    {
        int op = insn.getOpcode();
        // the value being set is pops[0]
        if (op == PUTFIELD.getCode() || op == PUTSTATIC.getCode())
        {
            return pops.isEmpty() ? this : pops.get(0).getPushed().resolve();
        }
        // stores (istore..astore incl _n forms): pops[0] is the stored value.
        if ((op >= ISTORE.getCode() && op <= ASTORE.getCode())
                || (op >= ISTORE_0.getCode() && op <= ASTORE_3.getCode()))
        {
            return pops.isEmpty() ? this : pops.get(0).getPushed().resolve();
        }
        // loads (iload..aload incl _n forms): follow the local's storing instruction.
        if ((op >= ILOAD.getCode() && op <= ALOAD.getCode())
                || (op >= ILOAD_0.getCode() && op <= ALOAD_3.getCode()))
        {
            if (reads.isEmpty())
            {
                return this;
            }
            VarCtx v = reads.get(0);
            InsnContext stored = v.getInstructionWhichStored();
            if (stored == null || v.isParameter())
            {
                return this;
            }
            return stored.resolve();
        }
        return this;
    }
}
