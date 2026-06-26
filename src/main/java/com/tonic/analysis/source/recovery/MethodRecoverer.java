package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.transform.PatternSwitchReconstructor;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.MethodEntry;
import lombok.Getter;
import java.util.List;

/**
 * Facade for recovering source-level AST from an IR method.
 * Coordinates expression recovery, control flow analysis, and statement recovery.
 */
@Getter
public class MethodRecoverer {

    private final IRMethod irMethod;
    private final MethodEntry sourceMethod;
    private final NameRecoveryStrategy nameStrategy;
    /** Names reserved by the caller (e.g. captured outer variables); {@code baseNameForSlot} skips these. */
    private final java.util.Set<String> reservedNames = new java.util.HashSet<>();
    /** Cast results that are a record deconstruction's synthetic temp (the {@code (T) selector}). */
    private final java.util.Set<SSAValue> recordDeconstructionTemps = new java.util.HashSet<>();

    private DominatorTree dominatorTree;
    private LoopAnalysis loopAnalysis;
    private DefUseChains defUseChains;

    @Getter
    private RecoveryContext recoveryContext;
    private ControlFlowContext controlFlowContext;
    private NameRecoverer nameRecoverer;
    private ExpressionRecoverer expressionRecoverer;
    private StructuralAnalyzer structuralAnalyzer;
    private StatementRecoverer statementRecoverer;

    public MethodRecoverer(IRMethod irMethod, MethodEntry sourceMethod) {
        this(irMethod, sourceMethod, NameRecoveryStrategy.PREFER_DEBUG_INFO);
    }

    public MethodRecoverer(IRMethod irMethod, MethodEntry sourceMethod, NameRecoveryStrategy nameStrategy) {
        this.irMethod = irMethod;
        this.sourceMethod = sourceMethod;
        this.nameStrategy = nameStrategy;
    }

    /**
     * Reserves {@code names} so that {@link #baseNameForSlot} never returns them. Must be called
     * before {@link #initializeRecovery()}.
     */
    public void reserveNames(java.util.Set<String> names) {
        reservedNames.addAll(names);
    }

    /**
     * Performs all analysis passes needed for recovery.
     */
    public void analyze() {
        stripSyntheticMatchExceptionHandlers();

        dominatorTree = new DominatorTree(irMethod);
        dominatorTree.compute();

        loopAnalysis = new LoopAnalysis(irMethod, dominatorTree);
        loopAnalysis.compute();

        defUseChains = new DefUseChains(irMethod);
        defUseChains.compute();

        structuralAnalyzer = new StructuralAnalyzer(irMethod, dominatorTree, loopAnalysis);
        structuralAnalyzer.analyze();
    }

    /**
     * Removes compiler-synthesized exception handlers that rethrow caught failures as
     * {@code java.lang.MatchException}. {@code javac} wraps the record-component accessor
     * invocations of a record deconstruction pattern in such a handler so that an accessor
     * throwing surfaces as a {@code MatchException}; this machinery has no idiomatic source
     * form, so the handler region is pruned before structuring. The protected accessor
     * sequence then recovers as a straight-line deconstruction the pattern-switch
     * reconstructor folds into {@code case Type(...)}.
     */
    private void stripSyntheticMatchExceptionHandlers() {
        List<IRBlock> handlerBlocks = new java.util.ArrayList<>();
        java.util.Set<Integer> deconstructSlots = new java.util.HashSet<>();
        for (ExceptionHandler handler : irMethod.getExceptionHandlers()) {
            IRBlock hb = handler.getHandlerBlock();
            if (hb != null && rethrowsAsMatchException(hb)) {
                if (!handlerBlocks.contains(hb)) {
                    handlerBlocks.add(hb);
                }
                collectDeconstructionTemps(handler.getTryStart(), deconstructSlots);
            }
        }
        // When an accessor receiver is a local load rather than the cast directly, resolve the slot
        // to the cast that defines it.
        if (!deconstructSlots.isEmpty()) {
            for (IRBlock b : irMethod.getBlocks()) {
                for (IRInstruction instr : b.getInstructions()) {
                    if (!(instr instanceof TypeCheckInstruction) || !((TypeCheckInstruction) instr).isCast()) {
                        continue;
                    }
                    SSAValue castResult = instr.getResult();
                    if (castResult == null) {
                        continue;
                    }
                    for (IRInstruction use : castResult.getUses()) {
                        if (use instanceof StoreLocalInstruction
                                && deconstructSlots.contains(((StoreLocalInstruction) use).getLocalIndex())) {
                            recordDeconstructionTemps.add(castResult);
                        }
                    }
                }
            }
        }
        for (IRBlock hb : handlerBlocks) {
            if (hb.getPredecessors().isEmpty()) {
                irMethod.removeBlock(hb);
            } else {
                irMethod.getExceptionHandlers().removeIf(h -> h.getHandlerBlock() == hb);
            }
        }
    }

    /**
     * True when a {@code SwitchBootstraps.typeSwitch} switch block is also a loop header — the
     * restart loop {@code javac} emits for a guarded pattern ({@code case T t when cond}), where the
     * guard-fail edge re-dispatches with an incremented restart index. The generic structurer does
     * not recognize a switch-terminated loop header, dropping the back-edge; the faithful {@code $pc$}
     * dispatch form preserves it, and {@link PatternSwitchReconstructor} folds it back into a guard.
     */
    private boolean hasTypeSwitchRestartLoop() {
        if (loopAnalysis == null) {
            return false;
        }
        for (IRBlock block : irMethod.getBlocks()) {
            IRInstruction term = block.getTerminator();
            if (!(term instanceof SwitchInstruction)) {
                continue;
            }
            Value key = ((SwitchInstruction) term).getKey();
            if (key instanceof SSAValue) {
                IRInstruction def = ((SSAValue) key).getDefinition();
                if (def instanceof InvokeInstruction && ((InvokeInstruction) def).isDynamic()
                        && "typeSwitch".equals(((InvokeInstruction) def).getName())
                        && loopAnalysis.isLoopHeader(block)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True when {@code block} allocates a {@code java.lang.MatchException} (a synthetic rethrow handler). */
    private boolean rethrowsAsMatchException(IRBlock block) {
        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof NewInstruction
                    && "java/lang/MatchException".equals(((NewInstruction) instr).getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records the local slots of the receivers of the (component-accessor) invocations protected by
     * a record-deconstruction's MatchException handler. The cast that defines such a slot is the
     * deconstruction's synthetic temp.
     */
    private void collectDeconstructionTemps(IRBlock tryStart, java.util.Set<Integer> slots) {
        if (tryStart == null) {
            return;
        }
        for (IRInstruction instr : tryStart.getInstructions()) {
            if (!(instr instanceof InvokeInstruction)) {
                continue;
            }
            Value receiver = ((InvokeInstruction) instr).getReceiver();
            if (!(receiver instanceof SSAValue)) {
                continue;
            }
            IRInstruction def = ((SSAValue) receiver).getDefinition();
            if (def instanceof TypeCheckInstruction && ((TypeCheckInstruction) def).isCast()) {
                recordDeconstructionTemps.add((SSAValue) receiver);
            } else if (def instanceof LoadLocalInstruction) {
                slots.add(((LoadLocalInstruction) def).getLocalIndex());
            }
        }
    }

    /**
     * Initializes all recovery components.
     */
    public void initializeRecovery() {
        recoveryContext = new RecoveryContext(irMethod, sourceMethod, defUseChains);
        recoveryContext.getRecordDeconstructionTemps().addAll(recordDeconstructionTemps);

        nameRecoverer = new NameRecoverer(irMethod, sourceMethod, nameStrategy);
        assignVariableNames();

        expressionRecoverer = new ExpressionRecoverer(recoveryContext);

        controlFlowContext = new ControlFlowContext(irMethod, dominatorTree, loopAnalysis, recoveryContext);

        statementRecoverer = new StatementRecoverer(controlFlowContext, structuralAnalyzer, expressionRecoverer);
    }

    /**
     * Assigns variable names to all SSA values using the name recoverer.
     * When a bytecode slot is reused with an incompatible type (e.g., int then StringBuilder),
     * assigns unique names to avoid type unification issues.
     */
    private void assignVariableNames() {
        assignParameterNames();

        SlotVariablePartition partition = new SlotVariablePartition(irMethod, this::baseNameForSlot,
                nameRecoverer::debugNameAt);
        recoveryContext.setSlotPartition(partition);

        irMethod.getBlocks().forEach(block -> {
            block.getPhiInstructions().forEach(phi -> {
                if (phi.getResult() != null) {
                    String name = partition.nameForPhi(phi);
                    if (name == null) {
                        name = nameRecoverer.generateSyntheticName(phi.getResult());
                    }
                    recoveryContext.setVariableName(phi.getResult(), name);
                }
            });

            block.getInstructions().forEach(instr -> {
                if (instr.getResult() == null) {
                    return;
                }
                String name = null;
                if (instr instanceof LoadLocalInstruction) {
                    name = partition.nameForLoad((LoadLocalInstruction) instr);
                }
                if (name == null) {
                    name = recoverNameForInstruction(instr);
                }
                recoveryContext.setVariableName(instr.getResult(), name);
            });
        });
    }

    /**
     * Resolves the base (component-zero) name for a slot: 'this' for the receiver,
     * 'argN' for a parameter slot, or 'localN' otherwise.
     */
    private String baseNameForSlot(int slot) {
        String debug = nameRecoverer != null ? nameRecoverer.unambiguousDebugName(slot) : null;
        if (debug != null && !reservedNames.contains(debug)) {
            return debug;
        }
        if (!irMethod.isStatic() && slot == 0) {
            return "this";
        }
        int paramSlots = locals().parameterSlotCount();
        if (slot < paramSlots) {
            return "arg" + getParamIndexForSlot(slot);
        }
        String candidate = "local" + slot;
        if (!reservedNames.isEmpty() && reservedNames.contains(candidate)) {
            // The natural name collides with a reserved (captured) name. Bump to a suffix beyond the
            // whole local-slot space so the new name also can't collide with any other slot's natural
            // "localN" name.
            int n = Math.max(slot + 1, localSlotCeiling());
            while (reservedNames.contains("local" + n)) {
                n++;
            }
            return "local" + n;
        }
        return candidate;
    }

    /** One past the highest local slot index referenced in the method (cached). */
    private int slotCeiling = -1;

    private int localSlotCeiling() {
        if (slotCeiling >= 0) {
            return slotCeiling;
        }
        int max = irMethod.getMaxLocals();
        for (IRBlock block : irMethod.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof LoadLocalInstruction) {
                    max = Math.max(max, ((LoadLocalInstruction) instr).getLocalIndex() + 1);
                } else if (instr instanceof StoreLocalInstruction) {
                    max = Math.max(max, ((StoreLocalInstruction) instr).getLocalIndex() + 1);
                }
            }
        }
        slotCeiling = max;
        return max;
    }

    /**
     * Assigns names to method parameters.
     * For instance methods, the first parameter (slot 0) is 'this'.
     */
    private void assignParameterNames() {
        int paramIndex = 0;
        for (var param : irMethod.getParameters()) {
            int slot = locals().slotOfParameter(param);
            String debug = nameRecoverer != null ? nameRecoverer.unambiguousDebugName(slot) : null;
            String name;
            if (debug != null) {
                name = debug;
            } else if (!irMethod.isStatic() && paramIndex == 0) {
                name = "this";
            } else {
                int argIndex = irMethod.isStatic() ? paramIndex : paramIndex - 1;
                name = "arg" + argIndex;
            }
            recoveryContext.setVariableName(param, name);
            paramIndex++;
        }
    }

    /**
     * Recovers a fallback name for the result of an instruction. Local loads are
     * named by the slot partition; this covers loads the partition could not place
     * (e.g. a read with no reaching definition) and all other result instructions.
     */
    private String recoverNameForInstruction(IRInstruction instr) {
        if (instr instanceof LoadLocalInstruction) {
            return baseNameForSlot(((LoadLocalInstruction) instr).getLocalIndex());
        }
        return nameRecoverer.generateSyntheticName(instr.getResult());
    }

    private int getParamIndexForSlot(int slot) {
        boolean isStatic = irMethod.isStatic();

        if (!isStatic && slot == 0) {
            return -1;
        }

        String descriptor = irMethod.getDescriptor();
        if (descriptor == null) {
            return isStatic ? slot : slot - 1;
        }

        List<String> paramTypes = parseParameterTypes(descriptor);
        int currentSlot = isStatic ? 0 : 1;

        for (int paramIndex = 0; paramIndex < paramTypes.size(); paramIndex++) {
            String paramType = paramTypes.get(paramIndex);
            int slotsForParam = 1;
            if ("J".equals(paramType) || "D".equals(paramType)) {
                slotsForParam = 2;
            }

            if (slot >= currentSlot && slot < currentSlot + slotsForParam) {
                return paramIndex;
            }
            currentSlot += slotsForParam;
        }

        return -1;
    }

    private List<String> parseParameterTypes(String descriptor) {
        List<String> types = new java.util.ArrayList<>();
        int start = descriptor.indexOf('(');
        int end = descriptor.indexOf(')');
        if (start < 0 || end < 0) {
            return types;
        }

        String params = descriptor.substring(start + 1, end);
        int i = 0;
        while (i < params.length()) {
            char c = params.charAt(i);
            if (c == 'L') {
                int semiPos = params.indexOf(';', i);
                if (semiPos > i) {
                    types.add(params.substring(i, semiPos + 1));
                    i = semiPos + 1;
                } else {
                    break;
                }
            } else if (c == '[') {
                int arrayStart = i;
                while (i < params.length() && params.charAt(i) == '[') {
                    i++;
                }
                if (i < params.length()) {
                    char elementType = params.charAt(i);
                    if (elementType == 'L') {
                        int semiPos = params.indexOf(';', i);
                        if (semiPos > i) {
                            types.add(params.substring(arrayStart, semiPos + 1));
                            i = semiPos + 1;
                        } else {
                            break;
                        }
                    } else {
                        types.add(params.substring(arrayStart, i + 1));
                        i++;
                    }
                }
            } else {
                types.add(String.valueOf(c));
                i++;
            }
        }

        return types;
    }

    private MethodLocals locals;

    /** The method's parameter slot layout (lazily built); see {@link MethodLocals}. */
    private MethodLocals locals() {
        if (locals == null) {
            locals = new MethodLocals(irMethod);
        }
        return locals;
    }

    /**
     * Recovers the method body as a block statement.
     */
    public BlockStmt recover() {
        if (dominatorTree == null) {
            analyze();
        }
        if (statementRecoverer == null) {
            initializeRecovery();
        }

        BlockStmt body = statementRecoverer.recoverMethod();

        // Completeness guarantee: if any observable operation reachable in the bytecode is absent
        // from the recovered source (a dropped block, by any mechanism), re-recover the whole
        // method as a faithful dispatch loop on a FRESH context (the first pass mutated
        // materialization/declaration state). Skipped for methods with exception handlers, where a
        // flat dispatch loop cannot model the try/catch regions.
        List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();
        boolean noHandlers = handlers == null || handlers.isEmpty();
        if (noHandlers && !Boolean.getBoolean("dispatch.off")
                && (statementRecoverer.hasDroppedOperations(body) || hasTypeSwitchRestartLoop())) {
            initializeRecovery();
            body = statementRecoverer.recoverMethodAsDispatch();
        }
        return body;
    }

    /**
     * Full recovery pipeline: analyze, initialize, and recover.
     */
    public static BlockStmt recoverMethod(IRMethod irMethod, MethodEntry sourceMethod) {
        MethodRecoverer recoverer = new MethodRecoverer(irMethod, sourceMethod);
        return recoverer.recover();
    }

    /**
     * Full recovery pipeline with custom name strategy.
     */
    public static BlockStmt recoverMethod(IRMethod irMethod, MethodEntry sourceMethod,
                                          NameRecoveryStrategy nameStrategy) {
        MethodRecoverer recoverer = new MethodRecoverer(irMethod, sourceMethod, nameStrategy);
        return recoverer.recover();
    }
}
