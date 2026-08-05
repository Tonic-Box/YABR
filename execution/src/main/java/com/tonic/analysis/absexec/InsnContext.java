package com.tonic.analysis.absexec;

import com.tonic.analysis.instruction.Instruction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Records one execution of one {@link Instruction} by the abstract {@link Execution} - its operand-stack pops
 * and pushes (with def-use links back to the {@code InsnContext} that produced each value) and its local reads.
 * A faithful, value-domain-free port of RuneLite's {@code net.runelite.asm.execution.InstructionContext}
 * onto YABR's instruction model. We do not track abstract values (the only consumer that needed them was
 * opaque-dead-branch removal, which the ModArith port does not use); constants are read directly from the
 * pushing instruction via {@link #resolve} / def-use.
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
     * Follows def-use to the instruction that actually produced this context's value: through a putfield/store
     * to the value being stored, and through a load to the instruction that stored the loaded local (or this
     * context, for a parameter / unknown source). Dup and swap are transparent in this engine (their outputs
     * keep the original pusher), so they need no handling here. Port of RuneLite's
     * {@code InstructionContext.resolve}.
     *
     * @return the producing context, or this one when the chain ends here
     */
    public InsnContext resolve()
    {
        int op = insn.getOpcode();
        // putfield (0xB5) / putstatic (0xB3): the value being set is pops[0].
        if (op == 0xB5 || op == 0xB3)
        {
            return pops.isEmpty() ? this : pops.get(0).getPushed().resolve();
        }
        // stores (istore..astore incl _n forms): pops[0] is the stored value.
        if ((op >= 0x36 && op <= 0x3A) || (op >= 0x3B && op <= 0x4E))
        {
            return pops.isEmpty() ? this : pops.get(0).getPushed().resolve();
        }
        // loads (iload..aload incl _n forms): follow the local's storing instruction.
        if ((op >= 0x15 && op <= 0x19) || (op >= 0x1A && op <= 0x2D))
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
