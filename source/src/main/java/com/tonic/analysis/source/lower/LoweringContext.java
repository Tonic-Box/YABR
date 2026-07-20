package com.tonic.analysis.source.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.LoadLocalInstruction;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ConstPool;

import java.util.*;

/**
 * Shared state during AST to IR lowering.
 * Tracks variables, blocks, and control flow targets.
 */
public class LoweringContext {

    private final IRMethod irMethod;

    private final ConstPool constPool;

    private final TypeResolver typeResolver;

    private IRBlock currentBlock;

    private final Map<String, SSAValue> variableMap = new HashMap<>();

    private final Map<String, Integer> variableLocalIndices = new HashMap<>();

    private final Map<String, IRMethod.SourceLocal> currentSourceLocal = new HashMap<>();

    private int nextLocalIndex = 0;

    private boolean emitLocalInstructions = false;

    private final Deque<LoopTargets> loopStack = new ArrayDeque<>();

    private final Map<String, LoopTargets> labelMap = new HashMap<>();

    private final Map<String, IRBlock> switchLabelMap = new HashMap<>();

    private int tempCounter = 0;

    /** Synthetic lambda methods generated during lowering */
    private final List<SyntheticLambdaMethod> syntheticMethods = new ArrayList<>();

    /** Synthetic array constructor methods generated during lowering */
    private final List<SyntheticArrayConstructor> arrayConstructors = new ArrayList<>();

    private int lambdaCounter = 0;

    private int arrayConstructorCounter = 0;

    private String currentMethodName = "method";

    private String ownerClass;

    private String superClassName;

    /**
     * Creates a new lowering context.
     */
    public LoweringContext(IRMethod irMethod, ConstPool constPool, TypeResolver typeResolver) {
        this.irMethod = irMethod;
        this.constPool = constPool;
        this.typeResolver = typeResolver;
    }

    /** The IR method being built */
    public IRMethod getIrMethod() {
        return irMethod;
    }

    /** Constant pool for creating constants and references */
    public ConstPool getConstPool() {
        return constPool;
    }

    /** Type resolver for looking up field/method types from ClassPool */
    public TypeResolver getTypeResolver() {
        return typeResolver;
    }

    /** Current block where instructions are being emitted */
    public IRBlock getCurrentBlock() {
        return currentBlock;
    }

    public void setCurrentBlock(IRBlock currentBlock) {
        this.currentBlock = currentBlock;
    }

    /** Map from variable names to their current SSA values */
    public Map<String, SSAValue> getVariableMap() {
        return variableMap;
    }

    /** Map from variable names to their local slot indices */
    public Map<String, Integer> getVariableLocalIndices() {
        return variableLocalIndices;
    }

    /** The in-scope source-local record per name (re-created on each declaration, so disjoint same-name
     *  declarations stay distinct), used to capture LocalVariableTable info during lowering. */
    public Map<String, IRMethod.SourceLocal> getCurrentSourceLocal() {
        return currentSourceLocal;
    }

    /** Next available local slot index */
    public int getNextLocalIndex() {
        return nextLocalIndex;
    }

    /** Whether to emit Load/Store instructions for variables (needed for loops) */
    public boolean isEmitLocalInstructions() {
        return emitLocalInstructions;
    }

    public void setEmitLocalInstructions(boolean emitLocalInstructions) {
        this.emitLocalInstructions = emitLocalInstructions;
    }

    /** Stack of loop targets for break/continue */
    public Deque<LoopTargets> getLoopStack() {
        return loopStack;
    }

    /** Map from labels to their loop targets */
    public Map<String, LoopTargets> getLabelMap() {
        return labelMap;
    }

    /** Map from switch labels to their target blocks */
    public Map<String, IRBlock> getSwitchLabelMap() {
        return switchLabelMap;
    }

    /** Counter for generating temporary variable names */
    public int getTempCounter() {
        return tempCounter;
    }

    /** Counter for generating unique lambda method names */
    public int getLambdaCounter() {
        return lambdaCounter;
    }

    /** Counter for generating unique array constructor method names */
    public int getArrayConstructorCounter() {
        return arrayConstructorCounter;
    }

    /** The name of the current method being lowered */
    public String getCurrentMethodName() {
        return currentMethodName;
    }

    public void setCurrentMethodName(String currentMethodName) {
        this.currentMethodName = currentMethodName;
    }

    /** The owner class of the current method */
    public String getOwnerClass() {
        return ownerClass;
    }

    public void setOwnerClass(String ownerClass) {
        this.ownerClass = ownerClass;
    }

    /** The superclass of the owner class */
    public String getSuperClassName() {
        return superClassName;
    }

    public void setSuperClassName(String superClassName) {
        this.superClassName = superClassName;
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
        recordValue(name, value);
    }

    /**
     * Declares a source-level local (or parameter/receiver) under {@code name} with its declared {@code type}.
     * A fresh record is created each call so that two disjoint declarations of the same name (sequential
     * shadowing) remain distinct LocalVariableTable entries. Subsequent assignments captured via
     * {@link #setVariable}/{@link #registerParameter} append their SSA values to the current record.
     */
    public void declareLocal(String name, IRType type, boolean isParameter) {
        IRMethod.SourceLocal local = new IRMethod.SourceLocal(name, type, isParameter);
        currentSourceLocal.put(name, local);
        irMethod.addSourceLocal(local);
    }

    /** Appends an SSA value to the current source-local record for {@code name}, if one was declared. */
    private void recordValue(String name, SSAValue value) {
        IRMethod.SourceLocal local = currentSourceLocal.get(name);
        if (local != null && value != null) {
            local.addValue(value);
        }
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
        recordValue(name, value);

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
     * Gets the continue target for the current or labeled loop. An unlabeled {@code continue} skips break-only
     * scopes (a {@code switch}, whose frame carries a null continue-target) and resolves to the nearest enclosing
     * loop's continue-target, matching Java: a {@code continue} inside a {@code switch} continues the loop, it does
     * not leave the switch at its break target.
     */
    public IRBlock getContinueTarget(String label) {
        if (label != null) {
            LoopTargets targets = labelMap.get(label);
            if (targets == null) {
                throw new LoweringException("Unknown label: " + label);
            }
            return targets.continueTarget();
        }
        for (LoopTargets targets : loopStack) {
            if (targets.continueTarget() != null) {
                return targets.continueTarget();
            }
        }
        throw new LoweringException("Continue outside of loop");
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
