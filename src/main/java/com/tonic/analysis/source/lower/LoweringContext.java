package com.tonic.analysis.source.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.LoadLocalInstruction;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
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

    /** Type resolver for looking up field/method types from ClassPool */
    private final TypeResolver typeResolver;

    /** Current block where instructions are being emitted */
    @Setter
    private IRBlock currentBlock;

    /** Map from variable names to their current SSA values */
    private final Map<String, SSAValue> variableMap = new HashMap<>();

    /** Map from variable names to their local slot indices */
    private final Map<String, Integer> variableLocalIndices = new HashMap<>();

    /** Next available local slot index */
    private int nextLocalIndex = 0;

    /** Whether to emit Load/Store instructions for variables (needed for loops) */
    @Setter
    private boolean emitLocalInstructions = false;

    /** Stack of loop targets for break/continue */
    private final Deque<LoopTargets> loopStack = new ArrayDeque<>();

    /** Map from labels to their loop targets */
    private final Map<String, LoopTargets> labelMap = new HashMap<>();

    /** Map from switch labels to their target blocks */
    private final Map<String, IRBlock> switchLabelMap = new HashMap<>();

    /** Counter for generating temporary variable names */
    private int tempCounter = 0;

    /** Synthetic lambda methods generated during lowering */
    private final List<SyntheticLambdaMethod> syntheticMethods = new ArrayList<>();

    /** Synthetic array constructor methods generated during lowering */
    private final List<SyntheticArrayConstructor> arrayConstructors = new ArrayList<>();

    /** Counter for generating unique lambda method names */
    private int lambdaCounter = 0;

    /** Counter for generating unique array constructor method names */
    private int arrayConstructorCounter = 0;

    /** The name of the current method being lowered */
    @Setter
    private String currentMethodName = "method";

    /** The owner class of the current method */
    @Setter
    private String ownerClass;

    /** The superclass of the owner class */
    @Setter
    private String superClassName;

    /**
     * Creates a new lowering context.
     */
    public LoweringContext(IRMethod irMethod, ConstPool constPool, TypeResolver typeResolver) {
        this.irMethod = irMethod;
        this.constPool = constPool;
        this.typeResolver = typeResolver;
    }

    /**
     * Initializes local slot indices starting after parameters.
     */
    public void initializeLocalSlots(int parameterSlotCount) {
        this.nextLocalIndex = parameterSlotCount;
    }

    /**
     * Registers a parameter with its local slot index without emitting StoreLocal.
     * Parameters are already at their slots from the method call.
     */
    public void registerParameter(String name, int localIndex, SSAValue value) {
        variableMap.put(name, value);
        variableLocalIndices.put(name, localIndex);
    }

    /**
     * Gets or allocates a local slot index for a variable.
     */
    public int getOrAllocateLocalIndex(String name) {
        return variableLocalIndices.computeIfAbsent(name, k -> nextLocalIndex++);
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
        IRBlock block = new IRBlock(prefix);
        irMethod.addBlock(block);
        return block;
    }

    /**
     * Sets or updates a variable's SSA value.
     * When emitLocalInstructions is enabled, also emits a StoreLocalInstruction.
     */
    public void setVariable(String name, SSAValue value) {
        variableMap.put(name, value);

        if (emitLocalInstructions && currentBlock != null) {
            int localIndex = getOrAllocateLocalIndex(name);
            StoreLocalInstruction store = new StoreLocalInstruction(localIndex, value);
            currentBlock.addInstruction(store);
        }
    }

    /**
     * Gets a variable's current SSA value.
     * When emitLocalInstructions is enabled, also emits a LoadLocalInstruction.
     */
    public SSAValue getVariable(String name) {
        SSAValue value = variableMap.get(name);
        if (value == null) {
            throw new LoweringException("Undefined variable: " + name);
        }

        if (emitLocalInstructions && currentBlock != null) {
            int localIndex = getOrAllocateLocalIndex(name);
            SSAValue loadedValue = newValue(value.getType());
            LoadLocalInstruction load = new LoadLocalInstruction(loadedValue, localIndex);
            currentBlock.addInstruction(load);
            return loadedValue;
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
     * Generates a unique name for a synthetic lambda method.
     */
    public String generateLambdaMethodName() {
        return "lambda$" + currentMethodName + "$" + (lambdaCounter++);
    }

    /**
     * Registers a synthetic lambda method for later generation.
     */
    public void registerSyntheticMethod(SyntheticLambdaMethod method) {
        syntheticMethods.add(method);
    }

    /**
     * Gets all registered synthetic methods.
     */
    public List<SyntheticLambdaMethod> getSyntheticMethods() {
        return new ArrayList<>(syntheticMethods);
    }

    /**
     * Clears synthetic methods after they have been processed.
     */
    public void clearSyntheticMethods() {
        syntheticMethods.clear();
    }

    /**
     * Generates a unique name for a synthetic array constructor method.
     */
    public String generateArrayConstructorMethodName() {
        return "lambda$newArray$" + (arrayConstructorCounter++);
    }

    /**
     * Registers a synthetic array constructor for later generation.
     */
    public void registerArrayConstructor(SyntheticArrayConstructor constructor) {
        arrayConstructors.add(constructor);
    }

    /**
     * Gets all registered array constructors.
     */
    public List<SyntheticArrayConstructor> getArrayConstructors() {
        return new ArrayList<>(arrayConstructors);
    }

    /**
     * Clears array constructors after they have been processed.
     */
    public void clearArrayConstructors() {
        arrayConstructors.clear();
    }

    /**
     * Loop target information for break/continue.
     */
    public static final class LoopTargets {
        private final IRBlock continueTarget;
        private final IRBlock breakTarget;

        public LoopTargets(IRBlock continueTarget, IRBlock breakTarget) {
            this.continueTarget = continueTarget;
            this.breakTarget = breakTarget;
        }

        public IRBlock continueTarget() { return continueTarget; }
        public IRBlock breakTarget() { return breakTarget; }
    }
}
