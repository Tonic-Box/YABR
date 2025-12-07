package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import lombok.Getter;

import java.util.*;

/**
 * Schedules instructions for stack-based execution.
 * Inserts load/store operations as needed.
 */
@Getter
public class StackScheduler {

    private final IRMethod method;
    private final RegisterAllocator regAlloc;
    private List<ScheduledInstruction> schedule;
    private int maxStack;

    public StackScheduler(IRMethod method, RegisterAllocator regAlloc) {
        this.method = method;
        this.regAlloc = regAlloc;
        this.schedule = new ArrayList<>();
        this.maxStack = 0;
    }

    public void schedule() {
        for (IRBlock block : method.getBlocksInOrder()) {
            scheduleBlock(block);
        }
    }

    private void scheduleBlock(IRBlock block) {
        Deque<Value> simulatedStack = new ArrayDeque<>();

        for (IRInstruction instr : block.getInstructions()) {
            scheduleInstruction(instr, simulatedStack);
        }
    }

    private void scheduleInstruction(IRInstruction instr, Deque<Value> stack) {
        List<Value> operands = instr.getOperands();
        for (Value operand : operands) {
            if (!isOnStack(operand, stack)) {
                emitLoad(operand);
            }
            stack.push(operand);
        }

        schedule.add(new ScheduledInstruction(instr, ScheduleType.EXECUTE));
        maxStack = Math.max(maxStack, stack.size());

        for (int i = 0; i < operands.size(); i++) {
            if (!stack.isEmpty()) stack.pop();
        }

        if (instr.hasResult()) {
            SSAValue result = instr.getResult();
            stack.push(result);

            if (needsStore(result, instr)) {
                emitStore(result);
                stack.pop();
            }
        }
    }

    private boolean isOnStack(Value value, Deque<Value> stack) {
        return stack.contains(value);
    }

    private boolean needsStore(SSAValue value, IRInstruction defInstr) {
        return value.getUseCount() > 1 || !isImmediatelyUsed(value, defInstr);
    }

    private boolean isImmediatelyUsed(SSAValue value, IRInstruction defInstr) {
        IRBlock block = defInstr.getBlock();
        List<IRInstruction> instrs = block.getInstructions();
        int defIndex = instrs.indexOf(defInstr);

        if (defIndex + 1 < instrs.size()) {
            IRInstruction nextInstr = instrs.get(defIndex + 1);
            return nextInstr.getOperands().contains(value);
        }
        return false;
    }

    private void emitLoad(Value value) {
        if (value instanceof SSAValue ssa) {
            int reg = regAlloc.getRegister(ssa);
            schedule.add(new ScheduledInstruction(
                    new LoadLocalInstruction(ssa, reg),
                    ScheduleType.LOAD
            ));
        }
    }

    private void emitStore(SSAValue value) {
        int reg = regAlloc.getRegister(value);
        schedule.add(new ScheduledInstruction(
                new StoreLocalInstruction(reg, value),
                ScheduleType.STORE
        ));
    }

    public enum ScheduleType {
        LOAD,
        STORE,
        EXECUTE
    }

    @Getter
    public static class ScheduledInstruction {
        private final IRInstruction instruction;
        private final ScheduleType type;

        public ScheduledInstruction(IRInstruction instruction, ScheduleType type) {
            this.instruction = instruction;
            this.type = type;
        }
    }
}
