package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import java.util.*;

/**
 * A linearizer that walks the IR blocks in order and produces a flat schedule of loads, stores and
 * executions, tracking the peak operand stack depth as it goes.
 */
public class StackScheduler
{

    private final IRMethod method;
    private final RegisterAllocator regAlloc;
    private final List<ScheduledInstruction> schedule;
    private int maxStack;

    /**
     * Creates a scheduler over one method and its register allocation.
     * @param method the method to schedule
     * @param regAlloc allocation supplying the slot for each value that needs a load or store
     */
    public StackScheduler(IRMethod method, RegisterAllocator regAlloc)
    {
        this.method = method;
        this.regAlloc = regAlloc;
        this.schedule = new ArrayList<>();
        this.maxStack = 0;
    }

    /**
     * @return the method
     */
    public IRMethod getMethod()
    {
        return method;
    }

    /**
     * @return the reg alloc
     */
    public RegisterAllocator getRegAlloc()
    {
        return regAlloc;
    }

    /**
     * @return the schedule
     */
    public List<ScheduledInstruction> getSchedule()
    {
        return schedule;
    }

    /**
     * @return the max stack
     */
    public int getMaxStack()
    {
        return maxStack;
    }

    /**
     * Schedules every block in order, appending to the existing schedule and updating the peak stack depth.
     */
    public void schedule()
    {
        for (IRBlock block : method.getBlocksInOrder())
        {
            scheduleBlock(block);
        }
    }

    private void scheduleBlock(IRBlock block)
    {
        Deque<Value> simulatedStack = new ArrayDeque<>();

        for (IRInstruction instr : block.getInstructions())
        {
            scheduleInstruction(instr, simulatedStack);
        }
    }

    private void scheduleInstruction(IRInstruction instr, Deque<Value> stack)
    {
        List<Value> operands = instr.getOperands();
        for (Value operand : operands)
        {
            if (!isOnStack(operand, stack))
            {
                emitLoad(operand);
            }
            stack.push(operand);
        }

        schedule.add(new ScheduledInstruction(instr, ScheduleType.EXECUTE));
        // Calculate actual stack slots, accounting for two-slot types (long/double)
        int stackSlots = 0;
        for (Value v : stack)
        {
            stackSlots++;
            if (v instanceof SSAValue && ((SSAValue) v).getType().isTwoSlot())
            {
                stackSlots++;  // Long/double take 2 slots
            }
        }
        maxStack = Math.max(maxStack, stackSlots);

        for (int i = 0; i < operands.size(); i++)
        {
            if (!stack.isEmpty()) stack.pop();
        }

        if (instr.hasResult())
        {
            SSAValue result = instr.getResult();
            stack.push(result);

            if (needsStore(result, instr))
            {
                emitStore(result);
                stack.pop();
            }
        }
    }

    private boolean isOnStack(Value value, Deque<Value> stack)
    {
        return stack.contains(value);
    }

    private boolean needsStore(SSAValue value, IRInstruction defInstr)
    {
        return value.getUseCount() > 1 || !isImmediatelyUsed(value, defInstr);
    }

    private boolean isImmediatelyUsed(SSAValue value, IRInstruction defInstr)
    {
        IRBlock block = defInstr.getBlock();
        List<IRInstruction> instrs = block.getInstructions();
        int defIndex = instrs.indexOf(defInstr);

        if (defIndex + 1 < instrs.size())
        {
            IRInstruction nextInstr = instrs.get(defIndex + 1);
            return nextInstr.getOperands().contains(value);
        }
        return false;
    }

    private void emitLoad(Value value)
    {
        if (value instanceof SSAValue)
        {
            SSAValue ssa = (SSAValue) value;
            int reg = regAlloc.getRegister(ssa);
            schedule.add(new ScheduledInstruction(new LoadLocalInstruction(ssa, reg), ScheduleType.LOAD));
        }
    }

    private void emitStore(SSAValue value)
    {
        int reg = regAlloc.getRegister(value);
        schedule.add(new ScheduledInstruction(new StoreLocalInstruction(reg, value), ScheduleType.STORE));
    }

    /**
     * The role a scheduled entry plays - a synthesized local load or store, or the IR instruction itself.
     */
    public enum ScheduleType
    {
        /**
         * A synthesized load that brings an operand back from its assigned
         * register onto the stack.
         */
        LOAD,
        /**
         * A synthesized store that parks a produced value in the register the
         * allocator assigned it.
         */
        STORE,
        /**
         * An instruction carried over from the IR, emitted once its operands
         * are on the stack.
         */
        EXECUTE
    }

    /**
     * One entry of the schedule - an instruction paired with the role it plays.
     */
    public static class ScheduledInstruction
    {
        private final IRInstruction instruction;
        private final ScheduleType type;

        /**
         * Creates a schedule entry.
         * @param instruction the instruction to emit
         * @param type the role it plays
         */
        public ScheduledInstruction(IRInstruction instruction, ScheduleType type)
        {
            this.instruction = instruction;
            this.type = type;
        }

        /**
         * @return the instruction
         */
        public IRInstruction getInstruction()
        {
            return instruction;
        }

        /**
         * @return the type
         */
        public ScheduleType getType()
        {
            return type;
        }
    }
}
