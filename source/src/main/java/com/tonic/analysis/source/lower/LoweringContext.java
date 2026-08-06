package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.CopyInstruction;
import com.tonic.analysis.ssa.ir.LoadLocalInstruction;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ConstPool;
import java.util.*;

/**
 * Shared mutable state for AST-to-IR lowering: variable bindings, the current block, and
 * break/continue targets.
 */
public class LoweringContext
{

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

    /**
     * Synthetic lambda methods generated during lowering
     */
    private final List<SyntheticLambdaMethod> syntheticMethods = new ArrayList<>();

    /**
     * Synthetic array constructor methods generated during lowering
     */
    private final List<SyntheticArrayConstructor> arrayConstructors = new ArrayList<>();

    private int lambdaCounter = 0;

    private int arrayConstructorCounter = 0;

    private String currentMethodName = "method";

    private String ownerClass;

    private String superClassName;

    /**
     * Creates a lowering context for one method.
     * @param irMethod the IR method being built
     * @param constPool the constant pool receiving new entries
     * @param typeResolver the resolver for field and method types
     */
    public LoweringContext(IRMethod irMethod, ConstPool constPool, TypeResolver typeResolver)
    {
        this.irMethod = irMethod;
        this.constPool = constPool;
        this.typeResolver = typeResolver;
    }

    /**
     * @return the IR method being built
     */
    public IRMethod getIrMethod()
    {
        return irMethod;
    }

    /**
     * @return the constant pool for creating constants and references
     */
    public ConstPool getConstPool()
    {
        return constPool;
    }

    /**
     * @return the resolver for looking up field and method types from the ClassPool
     */
    public TypeResolver getTypeResolver()
    {
        return typeResolver;
    }

    /**
     * @return the block instructions are currently emitted into
     */
    public IRBlock getCurrentBlock()
    {
        return currentBlock;
    }

    /**
     * Sets the block instructions are emitted into.
     * @param currentBlock the new emission target
     */
    public void setCurrentBlock(IRBlock currentBlock)
    {
        this.currentBlock = currentBlock;
    }

    /**
     * @return the map from variable names to their current SSA values
     */
    public Map<String, SSAValue> getVariableMap()
    {
        return variableMap;
    }

    /**
     * @return the map from variable names to their local slot indices
     */
    public Map<String, Integer> getVariableLocalIndices()
    {
        return variableLocalIndices;
    }

    /**
     * @return the in-scope source-local record per name (re-created on each declaration, so
     *         disjoint same-name declarations stay distinct), used to capture
     *         LocalVariableTable info during lowering
     */
    public Map<String, IRMethod.SourceLocal> getCurrentSourceLocal()
    {
        return currentSourceLocal;
    }

    /**
     * @return the next available local slot index
     */
    public int getNextLocalIndex()
    {
        return nextLocalIndex;
    }

    /**
     * @return whether Load/Store instructions are emitted for variables (needed for loops)
     */
    public boolean isEmitLocalInstructions()
    {
        return emitLocalInstructions;
    }

    /**
     * Sets whether Load/Store instructions are emitted for variable access; loops need them.
     * @param emitLocalInstructions true to emit explicit local loads and stores
     */
    public void setEmitLocalInstructions(boolean emitLocalInstructions)
    {
        this.emitLocalInstructions = emitLocalInstructions;
    }

    /**
     * @return the stack of loop targets for break/continue
     */
    public Deque<LoopTargets> getLoopStack()
    {
        return loopStack;
    }

    /**
     * @return the map from labels to their loop targets
     */
    public Map<String, LoopTargets> getLabelMap()
    {
        return labelMap;
    }

    /**
     * @return the map from switch labels to their target blocks
     */
    public Map<String, IRBlock> getSwitchLabelMap()
    {
        return switchLabelMap;
    }

    /**
     * @return the counter for generating temporary variable names
     */
    public int getTempCounter()
    {
        return tempCounter;
    }

    /**
     * @return the counter for generating unique lambda method names
     */
    public int getLambdaCounter()
    {
        return lambdaCounter;
    }

    /**
     * @return the counter for generating unique array constructor method names
     */
    public int getArrayConstructorCounter()
    {
        return arrayConstructorCounter;
    }

    /**
     * @return the name of the method being lowered
     */
    public String getCurrentMethodName()
    {
        return currentMethodName;
    }

    /**
     * Sets the name of the method being lowered.
     * @param currentMethodName the method name
     */
    public void setCurrentMethodName(String currentMethodName)
    {
        this.currentMethodName = currentMethodName;
    }

    /**
     * The declared return type of the method being lowered - the target type of its return values.
     */
    private SourceType currentMethodReturnType;

    /**
     * @return the current method return type
     */
    public SourceType getCurrentMethodReturnType()
    {
        return currentMethodReturnType;
    }

    /**
     * Sets the declared return type of the method being lowered.
     * @param type the target type of the method's return values
     */
    public void setCurrentMethodReturnType(SourceType type)
    {
        this.currentMethodReturnType = type;
    }

    /**
     * The type the surrounding declaration expects of the expression being lowered - the declared type of a
     * variable whose initializer is under way.
     */
    private final java.util.ArrayDeque<SourceType> expectedTypes =
            new java.util.ArrayDeque<>();

    /**
     * Pushes the type the surrounding declaration expects of the expression being lowered.
     * @param type the expected type
     */
    public void pushExpectedType(SourceType type)
    {
        expectedTypes.push(type);
    }

    /**
     * Pops the innermost expected type.
     */
    public void popExpectedType()
    {
        expectedTypes.pop();
    }

    /**
     * @return the innermost expected type, or null when none is active
     */
    public SourceType peekExpectedType()
    {
        return expectedTypes.peek();
    }

    /**
     * @return the owner class of the current method
     */
    public String getOwnerClass()
    {
        return ownerClass;
    }

    /**
     * Sets the owner class of the current method.
     * @param ownerClass the internal name of the owning class
     */
    public void setOwnerClass(String ownerClass)
    {
        this.ownerClass = ownerClass;
    }

    /**
     * @return the superclass of the owner class
     */
    public String getSuperClassName()
    {
        return superClassName;
    }

    /**
     * Sets the superclass of the owner class.
     * @param superClassName the internal name of the superclass
     */
    public void setSuperClassName(String superClassName)
    {
        this.superClassName = superClassName;
    }

    /**
     * Starts body-local allocation after the parameter slots.
     *
     * @param parameterSlotCount slots the receiver and parameters occupy
     */
    public void initializeLocalSlots(int parameterSlotCount)
    {
        this.nextLocalIndex = parameterSlotCount;
    }

    /**
     * Registers a parameter with its local slot index without emitting StoreLocal.
     *
     * @param name parameter name
     * @param localIndex slot the parameter arrives in
     * @param value SSA value holding the incoming argument
     */
    public void registerParameter(String name, int localIndex, SSAValue value)
    {
        variableMap.put(name, value);
        variableLocalIndices.put(name, localIndex);
        recordValue(name, value);
    }

    /**
     * Declares a source-level local, parameter or receiver with its declared type.
     *
     * @param name source name of the local
     * @param type declared type
     * @param isParameter true when the local is a parameter or the receiver
     */
    public void declareLocal(String name, IRType type, boolean isParameter)
    {
        declareLocal(name, type, isParameter, null);
    }

    /**
     * As {@link #declareLocal(String, IRType, boolean)} with the declared type's generic signature.
     *
     * @param name source name of the local
     * @param type declared type
     * @param isParameter true when the local is a parameter or the receiver
     * @param signature generic signature of the declared type, or null when it has none
     */
    public void declareLocal(String name, IRType type, boolean isParameter, String signature)
    {
        IRMethod.SourceLocal local = new IRMethod.SourceLocal(name, type, isParameter);
        local.setSignature(signature);
        currentSourceLocal.put(name, local);
        irMethod.addSourceLocal(local);
    }

    /**
     * The DECLARED type of a live source local.
     *
     * @param name variable to look up
     * @return the declared type, or null when the variable was never declared
     */
    public IRType declaredTypeOf(String name)
    {
        IRMethod.SourceLocal local = currentSourceLocal.get(name);
        return local != null ? local.getType() : null;
    }

    /**
     * Appends an SSA value to the current source-local record for {@code name}, if one was declared.
     */
    private void recordValue(String name, SSAValue value)
    {
        IRMethod.SourceLocal local = currentSourceLocal.get(name);
        if (local != null && value != null)
        {
            if (System.getProperty("yabr.lvttrace") != null)
            {
                System.err.println("[rec] " + name + " <- v" + value.getId() + " def="
                        + (value.getDefinition() == null ? "null" : value.getDefinition().getClass().getSimpleName()));
            }
            local.addValue(value);
        }
    }

    /**
     * The local slot a variable occupies, allocating the next free one on first use.
     *
     * @param name variable to place
     * @return the slot index bound to the variable
     */
    public int getOrAllocateLocalIndex(String name)
    {
        return variableLocalIndices.computeIfAbsent(name, k -> nextLocalIndex++);
    }

    /**
     * Creates a basic block and adds it to the method.
     *
     * @return the new block
     */
    public IRBlock createBlock()
    {
        IRBlock block = new IRBlock();
        irMethod.addBlock(block);
        return block;
    }

    /**
     * Creates a basic block with a name prefix and adds it to the method.
     *
     * @param prefix prefix for the block's generated name
     * @return the new block
     */
    public IRBlock createBlock(String prefix)
    {
        IRBlock block = new IRBlock(prefix);
        irMethod.addBlock(block);
        return block;
    }

    /**
     * Sets or updates a variable's SSA value.
     *
     * @param name variable being assigned
     * @param value value assigned to it
     */
    public void setVariable(String name, SSAValue value)
    {
        // An alias between two NAMED locals (`chained = complex;`) must stay two variables: sharing
        // the SSA value lets the allocator coalesce them onto one slot with one store, and the two
        // LocalVariableTable entries then collapse into one - the alias vanishes on round trip.
        // A real copy keeps javac's shape: each variable gets its own store and its own range.
        if (emitLocalInstructions && currentBlock != null)
        {
            IRMethod.SourceLocal owner = irMethod.sourceLocalOf(value);
            boolean aliasesOtherLocal = owner != null && owner != currentSourceLocal.get(name);
            if (!aliasesOtherLocal && value.getDefinition() instanceof LoadLocalInstruction)
            {
                int loadedFrom = ((LoadLocalInstruction) value.getDefinition()).getLocalIndex();
                aliasesOtherLocal = !variableLocalIndices.containsKey(name) || variableLocalIndices.get(name) != loadedFrom;
            }
            if (aliasesOtherLocal)
            {
                SSAValue aliased = newValue(value.getType());
                currentBlock.addInstruction(new CopyInstruction(aliased, value));
                value = aliased;
            }
        }
        variableMap.put(name, value);
        recordValue(name, value);

        if (emitLocalInstructions && currentBlock != null)
        {
            int localIndex = getOrAllocateLocalIndex(name);
            StoreLocalInstruction store = new StoreLocalInstruction(localIndex, value);
            currentBlock.addInstruction(store);
        }
    }

    /**
     * Gets a variable's current SSA value.
     *
     * @param name variable to read
     * @return the bound value, or the value the emitted load produces
     * @throws LoweringException if the name is not bound
     */
    public SSAValue getVariable(String name)
    {
        SSAValue value = variableMap.get(name);
        if (value == null)
        {
            throw new LoweringException("Undefined variable: " + name);
        }

        if (emitLocalInstructions && currentBlock != null)
        {
            int localIndex = getOrAllocateLocalIndex(name);
            SSAValue loadedValue = newValue(value.getType());
            LoadLocalInstruction load = new LoadLocalInstruction(loadedValue, localIndex);
            currentBlock.addInstruction(load);
            return loadedValue;
        }

        return value;
    }

    /**
     * @param name variable name to test
     * @return true if the name is bound to a value
     */
    public boolean hasVariable(String name)
    {
        return variableMap.containsKey(name);
    }

    /**
     * Creates a new SSA value.
     *
     * @param type IR type of the value
     * @return the new value
     */
    public SSAValue newValue(IRType type)
    {
        return new SSAValue(type);
    }

    /**
     * Generates a unique temporary variable name, advancing the counter.
     *
     * @return the generated name
     */
    public String newTempName()
    {
        return "$tmp" + (tempCounter++);
    }

    /**
     * Pushes loop targets onto the stack.
     * @param label optional label for labeled loops
     * @param continueTarget block to jump to for continue
     * @param breakTarget block to jump to for break
     * @param finallyDepth finally-stack size at the moment the loop is entered
     */
    public void pushLoop(String label, IRBlock continueTarget, IRBlock breakTarget, int finallyDepth)
    {
        LoopTargets targets = new LoopTargets(continueTarget, breakTarget, finallyDepth);
        loopStack.push(targets);
        if (label != null)
        {
            labelMap.put(label, targets);
        }
    }

    /**
     * Pops the current loop targets from the stack.
     */
    public void popLoop()
    {
        LoopTargets targets = loopStack.pop();
        labelMap.values().removeIf(t -> t == targets);
    }

    /**
     * Gets the continue target for the current or labeled loop.
     *
     * @param label loop label, or null for the innermost enclosing loop
     * @return the block the continue jumps to
     * @throws LoweringException if the label is unknown or no enclosing loop is open
     */
    public IRBlock getContinueTarget(String label)
    {
        if (label != null)
        {
            LoopTargets targets = labelMap.get(label);
            if (targets == null)
            {
                throw new LoweringException("Unknown label: " + label);
            }
            return targets.continueTarget();
        }
        for (LoopTargets targets : loopStack)
        {
            if (targets.continueTarget() != null)
            {
                return targets.continueTarget();
            }
        }
        throw new LoweringException("Continue outside of loop");
    }

    /**
     * The block a {@code break} jumps to - the labeled frame's break target, else the innermost frame's.
     *
     * @param label loop or switch label, or null for the innermost frame
     * @return the block the break jumps to
     * @throws LoweringException if the label is unknown or no frame is open
     */
    public IRBlock getBreakTarget(String label)
    {
        return getBreakFrame(label).breakTarget();
    }

    /**
     * Resolves the loop/switch frame a {@code break} targets - the labeled frame, else the innermost.
     *
     * @param label loop or switch label, or null for the innermost frame
     * @return the frame the break leaves
     * @throws LoweringException if the label is unknown or no frame is open
     */
    public LoopTargets getBreakFrame(String label)
    {
        if (label != null)
        {
            LoopTargets targets = labelMap.get(label);
            if (targets == null)
            {
                throw new LoweringException("Unknown label: " + label);
            }
            return targets;
        }
        if (loopStack.isEmpty())
        {
            throw new LoweringException("Break outside of loop");
        }
        return loopStack.peek();
    }

    /**
     * Resolves the loop frame a {@code continue} targets - the labeled frame, else the innermost frame with a
     * continue-target.
     *
     * @param label loop label, or null for the innermost enclosing loop
     * @return the frame the continue leaves to
     * @throws LoweringException if the label is unknown or no enclosing loop is open
     */
    public LoopTargets getContinueFrame(String label)
    {
        if (label != null)
        {
            LoopTargets targets = labelMap.get(label);
            if (targets == null)
            {
                throw new LoweringException("Unknown label: " + label);
            }
            return targets;
        }
        for (LoopTargets targets : loopStack)
        {
            if (targets.continueTarget() != null)
            {
                return targets;
            }
        }
        throw new LoweringException("Continue outside of loop");
    }

    /**
     * Registers the block a switch case branches to.
     *
     * @param label case label
     * @param target block the case branches to
     */
    public void setSwitchLabel(String label, IRBlock target)
    {
        switchLabelMap.put(label, target);
    }

    /**
     * @param label case label to look up
     * @return the block the case branches to, or null when no target is registered
     */
    public IRBlock getSwitchLabel(String label)
    {
        return switchLabelMap.get(label);
    }

    /**
     * Clears switch labels (after switch statement processing).
     */
    public void clearSwitchLabels()
    {
        switchLabelMap.clear();
    }

    /**
     * Copies the current variable bindings so a branch can be lowered and then rolled back.
     *
     * @return a detached copy of the name-to-value bindings
     */
    public Map<String, SSAValue> snapshotVariables()
    {
        return new HashMap<>(variableMap);
    }

    /**
     * Restores variable state from a snapshot, discarding the current bindings.
     *
     * @param snapshot bindings taken by snapshotVariables
     */
    public void restoreVariables(Map<String, SSAValue> snapshot)
    {
        variableMap.clear();
        variableMap.putAll(snapshot);
    }

    /**
     * Generates a unique name for a synthetic lambda method, advancing the counter.
     *
     * @return the generated method name
     */
    public String generateLambdaMethodName()
    {
        return "lambda$" + lambdaEnclosingName(currentMethodName) + "$" + (lambdaCounter++);
    }

    /**
     * The enclosing-method label javac uses in a synthetic lambda name: {@code <init>} is "new" and
     * {@code <clinit>} is "static".
     *
     * @param methodName name of the enclosing method
     * @return the label to embed in the lambda method name
     */
    public static String lambdaEnclosingName(String methodName)
    {
        if ("<init>".equals(methodName))
        {
            return "new";
        }
        if ("<clinit>".equals(methodName))
        {
            return "static";
        }
        return methodName;
    }

    /**
     * Registers a synthetic lambda method for later generation.
     *
     * @param method lambda body to generate
     */
    public void registerSyntheticMethod(SyntheticLambdaMethod method)
    {
        syntheticMethods.add(method);
    }

    /**
     * @return a copy of the registered synthetic lambda methods
     */
    public List<SyntheticLambdaMethod> getSyntheticMethods()
    {
        return new ArrayList<>(syntheticMethods);
    }

    /**
     * Clears synthetic methods after they have been processed.
     */
    public void clearSyntheticMethods()
    {
        syntheticMethods.clear();
    }

    /**
     * Generates a unique name for a synthetic array constructor method, advancing the counter.
     *
     * @return the generated method name
     */
    public String generateArrayConstructorMethodName()
    {
        return "lambda$newArray$" + (arrayConstructorCounter++);
    }

    /**
     * Registers a synthetic array constructor for later generation.
     *
     * @param constructor array constructor to generate
     */
    public void registerArrayConstructor(SyntheticArrayConstructor constructor)
    {
        arrayConstructors.add(constructor);
    }

    /**
     * @return a copy of the registered array constructors
     */
    public List<SyntheticArrayConstructor> getArrayConstructors()
    {
        return new ArrayList<>(arrayConstructors);
    }

    /**
     * Clears array constructors after they have been processed.
     */
    public void clearArrayConstructors()
    {
        arrayConstructors.clear();
    }

    /**
     * Loop target information for break/continue.
     */
    public static final class LoopTargets
    {
        private final IRBlock continueTarget;
        private final IRBlock breakTarget;
        private final int finallyDepth;

        public LoopTargets(IRBlock continueTarget, IRBlock breakTarget, int finallyDepth)
        {
            this.continueTarget = continueTarget;
            this.breakTarget = breakTarget;
            this.finallyDepth = finallyDepth;
        }

        /**
         * @return the block a continue jumps to
         */
        public IRBlock continueTarget() { return continueTarget; }

        /**
         * @return the block a break jumps to
         */
        public IRBlock breakTarget() { return breakTarget; }

        /**
         * The lowerer's finally-stack size at the moment this loop/switch was entered.
         *
         * @return the finally-stack size at the moment this frame was entered
         */
        public int finallyDepth() { return finallyDepth; }
    }
}
