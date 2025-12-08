package com.tonic.analysis.source.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ConstPool;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Shared state during AST to IR lowering.
 * Tracks variables, blocks, and control flow targets.
 */
@Getter
public class LoweringContext {

    /** The IR method being built */
    private final IRMethod irMethod;

    /** Constant pool for creating constants and references */
    private final ConstPool constPool;

    /** Current block where instructions are being emitted */
    @Setter
    private IRBlock currentBlock;

    /** Map from variable names to their current SSA values */
    private final Map<String, SSAValue> variableMap = new HashMap<>();

    /** Stack of loop targets for break/continue */
    private final Deque<LoopTargets> loopStack = new ArrayDeque<>();

    /** Map from labels to their loop targets */
    private final Map<String, LoopTargets> labelMap = new HashMap<>();

    /** Map from switch labels to their target blocks */
    private final Map<String, IRBlock> switchLabelMap = new HashMap<>();

    /** Counter for generating unique block names */
    private int blockCounter = 0;

    /** Counter for generating temporary variable names */
    private int tempCounter = 0;

    /**
     * Creates a new lowering context.
     */
    public LoweringContext(IRMethod irMethod, ConstPool constPool) {
        this.irMethod = irMethod;
        this.constPool = constPool;
    }

    /**
     * Creates a new basic block and adds it to the method.
     */
    public IRBlock createBlock() {
        IRBlock block = new IRBlock();
        irMethod.addBlock(block);
        return block;
    }

    /**
     * Creates a new basic block with a specific name prefix.
     */
    public IRBlock createBlock(String prefix) {
        IRBlock block = new IRBlock();
        irMethod.addBlock(block);
        return block;
    }

    /**
     * Sets or updates a variable's SSA value.
     */
    public void setVariable(String name, SSAValue value) {
        variableMap.put(name, value);
    }

    /**
     * Gets a variable's current SSA value.
     */
    public SSAValue getVariable(String name) {
        SSAValue value = variableMap.get(name);
        if (value == null) {
            throw new LoweringException("Undefined variable: " + name);
        }
        return value;
    }

    /**
     * Checks if a variable is defined.
     */
    public boolean hasVariable(String name) {
        return variableMap.containsKey(name);
    }

    /**
     * Creates a new SSA value with the given type.
     */
    public SSAValue newValue(IRType type) {
        return new SSAValue(type);
    }

    /**
     * Generates a unique temporary variable name.
     */
    public String newTempName() {
        return "$tmp" + (tempCounter++);
    }

    /**
     * Pushes loop targets onto the stack.
     * @param label optional label for labeled loops
     * @param continueTarget block to jump to for continue
     * @param breakTarget block to jump to for break
     */
    public void pushLoop(String label, IRBlock continueTarget, IRBlock breakTarget) {
        LoopTargets targets = new LoopTargets(continueTarget, breakTarget);
        loopStack.push(targets);
        if (label != null) {
            labelMap.put(label, targets);
        }
    }

    /**
     * Pops the current loop targets from the stack.
     */
    public void popLoop() {
        LoopTargets targets = loopStack.pop();
        // Remove from label map if present
        labelMap.values().removeIf(t -> t == targets);
    }

    /**
     * Gets the continue target for the current or labeled loop.
     */
    public IRBlock getContinueTarget(String label) {
        if (label != null) {
            LoopTargets targets = labelMap.get(label);
            if (targets == null) {
                throw new LoweringException("Unknown label: " + label);
            }
            return targets.continueTarget();
        }
        if (loopStack.isEmpty()) {
            throw new LoweringException("Continue outside of loop");
        }
        return loopStack.peek().continueTarget();
    }

    /**
     * Gets the break target for the current or labeled loop.
     */
    public IRBlock getBreakTarget(String label) {
        if (label != null) {
            LoopTargets targets = labelMap.get(label);
            if (targets == null) {
                throw new LoweringException("Unknown label: " + label);
            }
            return targets.breakTarget();
        }
        if (loopStack.isEmpty()) {
            throw new LoweringException("Break outside of loop");
        }
        return loopStack.peek().breakTarget();
    }

    /**
     * Sets a switch case label target.
     */
    public void setSwitchLabel(String label, IRBlock target) {
        switchLabelMap.put(label, target);
    }

    /**
     * Gets a switch case label target.
     */
    public IRBlock getSwitchLabel(String label) {
        return switchLabelMap.get(label);
    }

    /**
     * Clears switch labels (after switch statement processing).
     */
    public void clearSwitchLabels() {
        switchLabelMap.clear();
    }

    /**
     * Creates a snapshot of variable state (for branching).
     */
    public Map<String, SSAValue> snapshotVariables() {
        return new HashMap<>(variableMap);
    }

    /**
     * Restores variable state from a snapshot.
     */
    public void restoreVariables(Map<String, SSAValue> snapshot) {
        variableMap.clear();
        variableMap.putAll(snapshot);
    }

    /**
     * Loop target information for break/continue.
     */
    public record LoopTargets(IRBlock continueTarget, IRBlock breakTarget) {}
}
