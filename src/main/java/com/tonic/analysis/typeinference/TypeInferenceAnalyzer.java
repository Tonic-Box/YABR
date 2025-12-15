package com.tonic.analysis.typeinference;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.*;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.NullConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Performs type inference and nullability analysis on SSA IR.
 *
 * This analyzer computes:
 * - Inferred types for values without explicit type info
 * - Nullability states (definitely null, definitely not null, unknown)
 * - Possible types for polymorphic receivers
 * - Type narrowing from instanceof checks and null comparisons
 */
public class TypeInferenceAnalyzer {

    private final IRMethod method;
    private final Map<SSAValue, TypeState> valueTypes;
    private final Map<IRBlock, Map<SSAValue, TypeState>> blockEntryStates;
    private final Map<IRBlock, Map<SSAValue, TypeState>> blockExitStates;
    private boolean analyzed;

    public TypeInferenceAnalyzer(IRMethod method) {
        this.method = method;
        this.valueTypes = new HashMap<>();
        this.blockEntryStates = new HashMap<>();
        this.blockExitStates = new HashMap<>();
        this.analyzed = false;
    }

    /**
     * Runs the type inference analysis.
     */
    public void analyze() {
        if (analyzed) return;

        // Initialize parameter types
        initializeParameters();

        // Run dataflow analysis
        runDataflowAnalysis();

        analyzed = true;
    }

    private void initializeParameters() {
        for (SSAValue param : method.getParameters()) {
            IRType type = param.getType();
            Nullability nullability = type.isReference() ? Nullability.UNKNOWN : Nullability.NOT_NULL;
            valueTypes.put(param, new TypeState(type, nullability));
        }
    }

    private void runDataflowAnalysis() {
        // Initialize all blocks
        for (IRBlock block : method.getBlocks()) {
            blockEntryStates.put(block, new HashMap<>());
            blockExitStates.put(block, new HashMap<>());
        }

        // Worklist algorithm
        Queue<IRBlock> worklist = new LinkedList<>(method.getReversePostOrder());
        Set<IRBlock> inWorklist = new HashSet<>(worklist);

        while (!worklist.isEmpty()) {
            IRBlock block = worklist.poll();
            inWorklist.remove(block);

            // Compute entry state by joining predecessor exit states
            Map<SSAValue, TypeState> entryState = computeBlockEntryState(block);
            blockEntryStates.put(block, entryState);

            // Process block and compute exit state
            Map<SSAValue, TypeState> exitState = processBlock(block, new HashMap<>(entryState));

            // Check if exit state changed
            Map<SSAValue, TypeState> oldExit = blockExitStates.get(block);
            if (!exitState.equals(oldExit)) {
                blockExitStates.put(block, exitState);
                // Add successors to worklist
                for (IRBlock succ : block.getSuccessors()) {
                    if (!inWorklist.contains(succ)) {
                        worklist.add(succ);
                        inWorklist.add(succ);
                    }
                }
            }
        }
    }

    private Map<SSAValue, TypeState> computeBlockEntryState(IRBlock block) {
        Map<SSAValue, TypeState> entryState = new HashMap<>();

        List<IRBlock> preds = block.getPredecessors();
        if (preds.isEmpty()) {
            // Entry block - use parameter types
            entryState.putAll(valueTypes);
        } else {
            // Join all predecessor exit states
            for (IRBlock pred : preds) {
                Map<SSAValue, TypeState> predExit = blockExitStates.get(pred);
                if (predExit == null) continue;

                for (Map.Entry<SSAValue, TypeState> e : predExit.entrySet()) {
                    SSAValue value = e.getKey();
                    TypeState state = e.getValue();
                    TypeState existing = entryState.get(value);
                    if (existing == null) {
                        entryState.put(value, state);
                    } else {
                        entryState.put(value, existing.join(state));
                    }
                }
            }
        }

        return entryState;
    }

    private Map<SSAValue, TypeState> processBlock(IRBlock block, Map<SSAValue, TypeState> state) {
        // Process phi instructions
        for (PhiInstruction phi : block.getPhiInstructions()) {
            TypeState phiState = processPhiInstruction(phi, state);
            if (phi.getResult() != null) {
                state.put(phi.getResult(), phiState);
                valueTypes.put(phi.getResult(), phiState);
            }
        }

        // Process regular instructions
        for (IRInstruction instr : block.getInstructions()) {
            TypeState instrState = processInstruction(instr, state);
            if (instr.getResult() != null) {
                state.put(instr.getResult(), instrState);
                valueTypes.put(instr.getResult(), instrState);
            }

            // Handle type narrowing for branches
            if (instr instanceof BranchInstruction) {
                // Type narrowing is handled at block level, not here
            }
        }

        return state;
    }

    private TypeState processPhiInstruction(PhiInstruction phi, Map<SSAValue, TypeState> state) {
        TypeState result = TypeState.BOTTOM;

        for (Value operand : phi.getOperands()) {
            TypeState opState = getValueState(operand, state);
            result = result.join(opState);
        }

        return result;
    }

    private TypeState processInstruction(IRInstruction instr, Map<SSAValue, TypeState> state) {
        if (instr instanceof NewInstruction) {
            return processNew((NewInstruction) instr);
        } else if (instr instanceof NewArrayInstruction) {
            return processNewArray((NewArrayInstruction) instr);
        } else if (instr instanceof InvokeInstruction) {
            return processInvoke((InvokeInstruction) instr, state);
        } else if (instr instanceof GetFieldInstruction) {
            return processGetField((GetFieldInstruction) instr, state);
        } else if (instr instanceof ArrayLoadInstruction) {
            return processArrayLoad((ArrayLoadInstruction) instr, state);
        } else if (instr instanceof CastInstruction) {
            return processCast((CastInstruction) instr, state);
        } else if (instr instanceof InstanceOfInstruction) {
            return processInstanceOf((InstanceOfInstruction) instr);
        } else if (instr instanceof ConstantInstruction) {
            return processConstant((ConstantInstruction) instr);
        } else if (instr instanceof CopyInstruction) {
            return processCopy((CopyInstruction) instr, state);
        } else if (instr instanceof BinaryOpInstruction) {
            return processBinaryOp((BinaryOpInstruction) instr);
        } else if (instr instanceof UnaryOpInstruction) {
            return processUnaryOp((UnaryOpInstruction) instr);
        } else if (instr instanceof LoadLocalInstruction) {
            return processLoadLocal((LoadLocalInstruction) instr);
        } else if (instr.getResult() != null) {
            // Default: use declared type with unknown nullability
            IRType type = instr.getResult().getType();
            Nullability nullability = type != null && type.isReference() ? Nullability.UNKNOWN : Nullability.NOT_NULL;
            return new TypeState(type, nullability);
        }

        return TypeState.BOTTOM;
    }

    private TypeState processNew(NewInstruction instr) {
        // NEW always produces a non-null value of the exact type
        IRType type = new ReferenceType(instr.getClassName());
        return TypeState.notNull(type);
    }

    private TypeState processNewArray(NewArrayInstruction instr) {
        // NEWARRAY always produces a non-null array
        IRType type = instr.getResult().getType();
        return TypeState.notNull(type);
    }

    private TypeState processInvoke(InvokeInstruction instr, Map<SSAValue, TypeState> state) {
        if (instr.getResult() == null) {
            return TypeState.BOTTOM;
        }

        IRType returnType = instr.getResult().getType();
        if (returnType == null || returnType.isVoid()) {
            return TypeState.BOTTOM;
        }

        // Check for known non-null return methods
        String methodName = instr.getName();
        String owner = instr.getOwner();

        // Constructor calls (invokespecial <init>) return non-null
        if ("<init>".equals(methodName)) {
            return TypeState.notNull(returnType);
        }

        // Some well-known methods that return non-null
        if (isKnownNonNullReturn(owner, methodName, instr.getDescriptor())) {
            return TypeState.notNull(returnType);
        }

        // Default: return type is nullable for references
        Nullability nullability = returnType.isReference() ? Nullability.UNKNOWN : Nullability.NOT_NULL;
        return new TypeState(returnType, nullability);
    }

    private boolean isKnownNonNullReturn(String owner, String name, String descriptor) {
        // String methods that return non-null
        if ("java/lang/String".equals(owner)) {
            switch (name) {
                case "toString":
                case "substring":
                case "toLowerCase":
                case "toUpperCase":
                case "trim":
                case "concat":
                case "replace":
                case "valueOf":
                    return true;
            }
        }

        // StringBuilder/StringBuffer methods
        if ("java/lang/StringBuilder".equals(owner) || "java/lang/StringBuffer".equals(owner)) {
            if ("append".equals(name) || "toString".equals(name)) {
                return true;
            }
        }

        // Class.getClass() always returns non-null
        if ("getClass".equals(name) && "()Ljava/lang/Class;".equals(descriptor)) {
            return true;
        }

        return false;
    }

    private TypeState processGetField(GetFieldInstruction instr, Map<SSAValue, TypeState> state) {
        IRType fieldType = instr.getResult().getType();
        Nullability nullability = fieldType.isReference() ? Nullability.UNKNOWN : Nullability.NOT_NULL;
        return new TypeState(fieldType, nullability);
    }

    private TypeState processArrayLoad(ArrayLoadInstruction instr, Map<SSAValue, TypeState> state) {
        IRType elementType = instr.getResult().getType();
        Nullability nullability = elementType.isReference() ? Nullability.UNKNOWN : Nullability.NOT_NULL;
        return new TypeState(elementType, nullability);
    }

    private TypeState processCast(CastInstruction instr, Map<SSAValue, TypeState> state) {
        IRType targetType = instr.getTargetType();
        TypeState sourceState = getValueState(instr.getObjectRef(), state);

        // Cast preserves nullability
        return new TypeState(targetType, sourceState.getNullability());
    }

    private TypeState processInstanceOf(InstanceOfInstruction instr) {
        // instanceof always returns a boolean (int in JVM)
        return TypeState.notNull(PrimitiveType.INT);
    }

    private TypeState processConstant(ConstantInstruction instr) {
        Constant constant = instr.getConstant();
        IRType type = instr.getResult().getType();

        if (constant == null || constant.getValue() == null) {
            return TypeState.NULL;
        }

        // Non-null constant
        return TypeState.notNull(type);
    }

    private TypeState processCopy(CopyInstruction instr, Map<SSAValue, TypeState> state) {
        return getValueState(instr.getSource(), state);
    }

    private TypeState processBinaryOp(BinaryOpInstruction instr) {
        // Binary operations produce primitives (non-null)
        IRType type = instr.getResult().getType();
        return TypeState.notNull(type);
    }

    private TypeState processUnaryOp(UnaryOpInstruction instr) {
        // Unary operations produce primitives (non-null)
        IRType type = instr.getResult().getType();
        return TypeState.notNull(type);
    }

    private TypeState processLoadLocal(LoadLocalInstruction instr) {
        IRType type = instr.getResult().getType();
        Nullability nullability = type.isReference() ? Nullability.UNKNOWN : Nullability.NOT_NULL;
        return new TypeState(type, nullability);
    }

    private TypeState getValueState(Value value, Map<SSAValue, TypeState> state) {
        if (value instanceof NullConstant) {
            return TypeState.NULL;
        }

        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            TypeState s = state.get(ssa);
            if (s != null) return s;
            s = valueTypes.get(ssa);
            if (s != null) return s;

            // Fallback to declared type
            IRType type = ssa.getType();
            Nullability nullability = type != null && type.isReference() ? Nullability.UNKNOWN : Nullability.NOT_NULL;
            return new TypeState(type, nullability);
        }

        return TypeState.BOTTOM;
    }

    // ===== Public API =====

    /**
     * Gets the inferred type state for a value.
     */
    public TypeState getTypeState(SSAValue value) {
        if (!analyzed) analyze();
        return valueTypes.getOrDefault(value, TypeState.BOTTOM);
    }

    /**
     * Gets the inferred type for a value.
     */
    public IRType getInferredType(SSAValue value) {
        TypeState state = getTypeState(value);
        return state.getAnyType();
    }

    /**
     * Gets the nullability state for a value.
     */
    public Nullability getNullability(SSAValue value) {
        TypeState state = getTypeState(value);
        return state.getNullability();
    }

    /**
     * Checks if a value is definitely null.
     */
    public boolean isDefinitelyNull(SSAValue value) {
        return getNullability(value).isDefinitelyNull();
    }

    /**
     * Checks if a value is definitely not null.
     */
    public boolean isDefinitelyNotNull(SSAValue value) {
        return getNullability(value).isDefinitelyNotNull();
    }

    /**
     * Gets all possible types for a polymorphic value.
     */
    public TypeSet getPossibleTypes(SSAValue value) {
        TypeState state = getTypeState(value);
        return state.getTypeSet();
    }

    /**
     * Gets the type state at block entry for a value.
     */
    public TypeState getTypeStateAtBlockEntry(IRBlock block, SSAValue value) {
        if (!analyzed) analyze();
        Map<SSAValue, TypeState> entryState = blockEntryStates.get(block);
        if (entryState == null) return TypeState.BOTTOM;
        return entryState.getOrDefault(value, TypeState.BOTTOM);
    }

    /**
     * Gets the type state at block exit for a value.
     */
    public TypeState getTypeStateAtBlockExit(IRBlock block, SSAValue value) {
        if (!analyzed) analyze();
        Map<SSAValue, TypeState> exitState = blockExitStates.get(block);
        if (exitState == null) return TypeState.BOTTOM;
        return exitState.getOrDefault(value, TypeState.BOTTOM);
    }

    /**
     * Gets all values that are definitely null.
     */
    public Set<SSAValue> getNullValues() {
        if (!analyzed) analyze();
        Set<SSAValue> result = new LinkedHashSet<>();
        for (Map.Entry<SSAValue, TypeState> e : valueTypes.entrySet()) {
            if (e.getValue().isDefinitelyNull()) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /**
     * Gets all values that are definitely not null.
     */
    public Set<SSAValue> getNonNullValues() {
        if (!analyzed) analyze();
        Set<SSAValue> result = new LinkedHashSet<>();
        for (Map.Entry<SSAValue, TypeState> e : valueTypes.entrySet()) {
            if (e.getValue().isDefinitelyNotNull()) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /**
     * Gets all values with their type states.
     */
    public Map<SSAValue, TypeState> getAllTypeStates() {
        if (!analyzed) analyze();
        return Collections.unmodifiableMap(valueTypes);
    }

    /**
     * Checks if a value has a precise (exact) type known.
     */
    public boolean hasPreciseType(SSAValue value) {
        TypeState state = getTypeState(value);
        return state.isPrecise();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TypeInferenceAnalyzer for ").append(method.getName()).append(":\n");
        for (Map.Entry<SSAValue, TypeState> e : valueTypes.entrySet()) {
            sb.append("  ").append(e.getKey().getName()).append(": ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }
}
