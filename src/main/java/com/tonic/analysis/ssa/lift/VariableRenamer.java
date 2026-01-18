package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renames variables to complete SSA conversion by walking the dominator tree.
 */
public class VariableRenamer {

    private static final Pattern VAR_PATTERN = Pattern.compile("v(\\d+)(?:_(\\d+))?");

    private final DominatorTree dominatorTree;
    private Map<Integer, Deque<SSAValue>> varStacks;
    private Map<Integer, Integer> varCounters;

    /**
     * Creates a new variable renamer.
     *
     * @param dominatorTree the dominator tree for the method
     */
    public VariableRenamer(DominatorTree dominatorTree) {
        this.dominatorTree = dominatorTree;
    }

    /**
     * Performs SSA variable renaming on a method.
     *
     * @param method the method to rename
     */
    public void rename(IRMethod method) {
        varStacks = new HashMap<>();
        varCounters = new HashMap<>();

        initializeParameters(method);

        if (method.getEntryBlock() != null) {
            int blockCount = method.getBlockCount();
            if (blockCount > 500) {
                //System.err.println("[VariableRenamer] Processing large method: " +
                //    method.getOwnerClass() + "." + method.getName() + method.getDescriptor() +
                //    " with " + blockCount + " blocks");
            }
            renameBlock(method.getEntryBlock());
        }
    }

    private void initializeParameters(IRMethod method) {
        int localIndex = 0;
        for (SSAValue param : method.getParameters()) {
            pushVariable(localIndex, param);
            localIndex++;
            if (param.getType().isTwoSlot()) {
                localIndex++;
            }
        }
    }

    private void renameBlock(IRBlock startBlock) {
        Deque<RenameWorkItem> workStack = new ArrayDeque<>();
        Set<IRBlock> visited = new HashSet<>();
        workStack.push(new RenameWorkItem(startBlock, null, false));

        while (!workStack.isEmpty()) {
            RenameWorkItem item = workStack.pop();
            IRBlock block = item.block;

            if (item.isPostProcess) {
                for (Map.Entry<Integer, Integer> entry : item.savedStackSizes.entrySet()) {
                    Deque<SSAValue> stack = varStacks.get(entry.getKey());
                    while (stack != null && stack.size() > entry.getValue()) {
                        stack.pop();
                    }
                }
                continue;
            }

            if (!visited.add(block)) {
                //System.err.println("[VariableRenamer] CYCLE DETECTED: Block " + block.getId() + " already visited!");
                //System.err.println("  Block children: " + dominatorTree.getDominatorTreeChildren(block));
                //System.err.println("  Block idom: " + dominatorTree.getImmediateDominator(block));
                continue;
            }

            Map<Integer, Integer> savedStackSizes = new HashMap<>();
            for (Map.Entry<Integer, Deque<SSAValue>> entry : varStacks.entrySet()) {
                savedStackSizes.put(entry.getKey(), entry.getValue().size());
            }

            for (PhiInstruction phi : block.getPhiInstructions()) {
                String name = phi.getResult().getName();
                if (name.startsWith("phi_")) {
                    int varIndex = Integer.parseInt(name.substring(4));
                    SSAValue newName = createNewName(varIndex, phi.getResult().getType());
                    phi.setResult(newName);
                    newName.setDefinition(phi);
                    pushVariable(varIndex, newName);
                }
            }

            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());
            for (IRInstruction instr : instructions) {
                renameUses(instr);
                renameDefinitions(instr, block);
            }

            for (IRBlock succ : block.getSuccessors()) {
                for (PhiInstruction phi : succ.getPhiInstructions()) {
                    String name = phi.getResult().getName();
                    if (name.startsWith("phi_") || VAR_PATTERN.matcher(name).matches()) {
                        int varIndex = extractVarIndex(phi);
                        if (varIndex >= 0) {
                            SSAValue currentVal = getCurrentValue(varIndex);
                            if (currentVal != null) {
                                phi.addIncoming(currentVal, block);
                            }
                        }
                    }
                }
            }

            workStack.push(new RenameWorkItem(block, savedStackSizes, true));

            List<IRBlock> children = new ArrayList<>(dominatorTree.getDominatorTreeChildren(block));
            Collections.reverse(children);
            for (IRBlock child : children) {
                workStack.push(new RenameWorkItem(child, null, false));
            }
        }
    }

    private static class RenameWorkItem {
        final IRBlock block;
        final Map<Integer, Integer> savedStackSizes;
        final boolean isPostProcess;

        RenameWorkItem(IRBlock block, Map<Integer, Integer> savedStackSizes, boolean isPostProcess) {
            this.block = block;
            this.savedStackSizes = savedStackSizes;
            this.isPostProcess = isPostProcess;
        }
    }

    private void renameUses(IRInstruction instr) {
        if (instr instanceof LoadLocalInstruction) {
            LoadLocalInstruction load = (LoadLocalInstruction) instr;
            int varIndex = load.getLocalIndex();
            SSAValue currentVal = getCurrentValue(varIndex);
            if (currentVal != null && load.getResult() != null) {
                load.getResult().replaceAllUsesWith(currentVal);
            }
        }
    }

    private void renameDefinitions(IRInstruction instr, IRBlock block) {
        if (instr instanceof StoreLocalInstruction) {
            StoreLocalInstruction store = (StoreLocalInstruction) instr;
            int varIndex = store.getLocalIndex();
            Value storedValue = store.getValue();
            if (storedValue instanceof SSAValue) {
                SSAValue ssaVal = (SSAValue) storedValue;
                pushVariable(varIndex, ssaVal);
            }
        }
    }

    private int extractVarIndex(PhiInstruction phi) {
        String name = phi.getResult().getName();
        if (name.startsWith("phi_")) {
            try {
                return Integer.parseInt(name.substring(4));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        Matcher m = VAR_PATTERN.matcher(name);
        if (m.matches()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private void pushVariable(int varIndex, SSAValue value) {
        varStacks.computeIfAbsent(varIndex, k -> new ArrayDeque<>()).push(value);
    }

    private SSAValue getCurrentValue(int varIndex) {
        Deque<SSAValue> stack = varStacks.get(varIndex);
        return stack != null && !stack.isEmpty() ? stack.peek() : null;
    }

    private SSAValue createNewName(int varIndex, com.tonic.analysis.ssa.type.IRType type) {
        int count = varCounters.getOrDefault(varIndex, 0);
        varCounters.put(varIndex, count + 1);
        return new SSAValue(type, "v" + varIndex + "_" + count);
    }
}
