package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public final class StatisticsListener extends AbstractBytecodeListener {

    private long totalInstructions;
    private final Map<Integer, Long> opcodeCount;
    private final Map<String, Long> methodCallCount;
    private long objectAllocations;
    private long arrayAllocations;
    private int maxStackDepth;
    private int maxCallDepth;
    private int currentCallDepth;
    private long branchesTotal;
    private long branchesTaken;

    public StatisticsListener() {
        this.opcodeCount = new HashMap<>();
        this.methodCallCount = new HashMap<>();
        this.totalInstructions = 0;
        this.objectAllocations = 0;
        this.arrayAllocations = 0;
        this.maxStackDepth = 0;
        this.maxCallDepth = 0;
        this.currentCallDepth = 0;
        this.branchesTotal = 0;
        this.branchesTaken = 0;
    }

    @Override
    public void onFramePush(StackFrame frame) {
        super.onFramePush(frame);
        currentCallDepth++;
        maxCallDepth = Math.max(maxCallDepth, currentCallDepth);
    }

    @Override
    public void onFramePop(StackFrame frame, com.tonic.analysis.execution.state.ConcreteValue returnValue) {
        currentCallDepth--;
    }

    @Override
    public void afterInstruction(StackFrame frame, Instruction instruction) {
        super.afterInstruction(frame, instruction);
        totalInstructions++;
        opcodeCount.merge(instruction.getOpcode(), 1L, Long::sum);
        maxStackDepth = Math.max(maxStackDepth, frame.getStack().depth());
    }

    @Override
    public void onObjectAllocation(ObjectInstance instance) {
        objectAllocations++;
    }

    @Override
    public void onArrayAllocation(ArrayInstance array) {
        arrayAllocations++;
    }

    @Override
    public void onMethodCall(StackFrame caller, MethodEntry target, com.tonic.analysis.execution.state.ConcreteValue[] args) {
        String methodSig = target.getOwnerName() + "." + target.getName() + target.getDesc();
        methodCallCount.merge(methodSig, 1L, Long::sum);
    }

    @Override
    public void onBranch(StackFrame frame, int fromPC, int toPC, boolean taken) {
        branchesTotal++;
        if (taken) {
            branchesTaken++;
        }
    }

    public long getTotalInstructions() {
        return totalInstructions;
    }

    public Map<Integer, Long> getOpcodeCount() {
        return Collections.unmodifiableMap(opcodeCount);
    }

    public Map<String, Long> getMethodCallCount() {
        return Collections.unmodifiableMap(methodCallCount);
    }

    public long getObjectAllocations() {
        return objectAllocations;
    }

    public long getArrayAllocations() {
        return arrayAllocations;
    }

    public int getMaxStackDepth() {
        return maxStackDepth;
    }

    public int getMaxCallDepth() {
        return maxCallDepth;
    }

    public long getBranchesTotal() {
        return branchesTotal;
    }

    public long getBranchesTaken() {
        return branchesTaken;
    }

    public double getBranchTakenRatio() {
        if (branchesTotal == 0) {
            return 0.0;
        }
        return (double) branchesTaken / branchesTotal;
    }

    @Override
    public void reset() {
        super.reset();
        totalInstructions = 0;
        opcodeCount.clear();
        methodCallCount.clear();
        objectAllocations = 0;
        arrayAllocations = 0;
        maxStackDepth = 0;
        maxCallDepth = 0;
        currentCallDepth = 0;
        branchesTotal = 0;
        branchesTaken = 0;
    }

    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Execution Statistics:\n");
        sb.append("===================\n\n");

        sb.append("Instructions:\n");
        sb.append("  Total:     ").append(totalInstructions).append("\n");
        sb.append("  Opcodes:   ").append(opcodeCount.size()).append(" unique\n\n");

        sb.append("Method Calls:\n");
        sb.append("  Total:     ").append(methodCallCount.values().stream().mapToLong(Long::longValue).sum()).append("\n");
        sb.append("  Unique:    ").append(methodCallCount.size()).append("\n\n");

        sb.append("Heap Allocations:\n");
        sb.append("  Objects:   ").append(objectAllocations).append("\n");
        sb.append("  Arrays:    ").append(arrayAllocations).append("\n");
        sb.append("  Total:     ").append(objectAllocations + arrayAllocations).append("\n\n");

        sb.append("Stack & Call Depth:\n");
        sb.append("  Max stack: ").append(maxStackDepth).append("\n");
        sb.append("  Max calls: ").append(maxCallDepth).append("\n\n");

        sb.append("Branches:\n");
        sb.append("  Total:     ").append(branchesTotal).append("\n");
        sb.append("  Taken:     ").append(branchesTaken).append("\n");
        sb.append("  Ratio:     ").append(String.format("%.2f%%", getBranchTakenRatio() * 100)).append("\n\n");

        if (!opcodeCount.isEmpty()) {
            sb.append("Top Opcodes:\n");
            opcodeCount.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sb.append(String.format("  0x%02X: %d\n", e.getKey(), e.getValue())));
            sb.append("\n");
        }

        if (!methodCallCount.isEmpty()) {
            sb.append("Top Method Calls:\n");
            methodCallCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
        }

        return sb.toString();
    }
}
