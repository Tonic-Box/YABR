package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.lift.BytecodeLifter;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.MethodEntry;
import java.util.List;

/**
 * Facade that recovers a source-level method body from an IR method, wiring together name, expression, structural,
 * and statement recovery.
 */
public class MethodRecoverer
{

    private final IRMethod irMethod;
    private final MethodEntry sourceMethod;
    private final NameRecoveryStrategy nameStrategy;
    private final java.util.Set<String> reservedNames = new java.util.HashSet<>();
    private final java.util.Set<SSAValue> recordDeconstructionTemps = new java.util.HashSet<>();

    private DominatorTree dominatorTree;
    private LoopAnalysis loopAnalysis;
    private DefUseChains defUseChains;

    private RecoveryContext recoveryContext;
    private ControlFlowContext controlFlowContext;
    private NameRecoverer nameRecoverer;
    private ExpressionRecoverer expressionRecoverer;
    private StructuralAnalyzer structuralAnalyzer;
    private StatementRecoverer statementRecoverer;

    /**
     * Creates a recoverer that prefers names from debug info.
     *
     * @param irMethod the lifted method
     * @param sourceMethod the method the IR came from
     */
    public MethodRecoverer(IRMethod irMethod, MethodEntry sourceMethod)
    {
        this(irMethod, sourceMethod, NameRecoveryStrategy.PREFER_DEBUG_INFO);
    }

    /**
     * Creates a recoverer with an explicit naming strategy.
     *
     * @param irMethod the lifted method
     * @param sourceMethod the method the IR came from
     * @param nameStrategy how local names are chosen
     */
    public MethodRecoverer(IRMethod irMethod, MethodEntry sourceMethod, NameRecoveryStrategy nameStrategy)
    {
        this.irMethod = irMethod;
        this.sourceMethod = sourceMethod;
        this.nameStrategy = nameStrategy;
    }

    /**
     * @return the ir method
     */
    public IRMethod getIrMethod()
    {
        return irMethod;
    }

    /**
     * @return the source method
     */
    public MethodEntry getSourceMethod()
    {
        return sourceMethod;
    }

    /**
     * @return the name strategy
     */
    public NameRecoveryStrategy getNameStrategy()
    {
        return nameStrategy;
    }

    /**
     * @return the names reserved by the caller (e.g. captured outer variables), which
     *         {@code baseNameForSlot} skips
     */
    public java.util.Set<String> getReservedNames()
    {
        return reservedNames;
    }

    /**
     * @return the cast results that are a record deconstruction's synthetic temp (the {@code (T) selector})
     */
    public java.util.Set<SSAValue> getRecordDeconstructionTemps()
    {
        return recordDeconstructionTemps;
    }

    /**
     * @return the dominator tree
     */
    public DominatorTree getDominatorTree()
    {
        return dominatorTree;
    }

    /**
     * @return the loop analysis
     */
    public LoopAnalysis getLoopAnalysis()
    {
        return loopAnalysis;
    }

    /**
     * @return the def use chains
     */
    public DefUseChains getDefUseChains()
    {
        return defUseChains;
    }

    /**
     * @return the recovery context
     */
    public RecoveryContext getRecoveryContext()
    {
        return recoveryContext;
    }

    /**
     * @return the control flow context
     */
    public ControlFlowContext getControlFlowContext()
    {
        return controlFlowContext;
    }

    /**
     * @return the name recoverer
     */
    public NameRecoverer getNameRecoverer()
    {
        return nameRecoverer;
    }

    /**
     * @return the expression recoverer
     */
    public ExpressionRecoverer getExpressionRecoverer()
    {
        return expressionRecoverer;
    }

    /**
     * @return the structural analyzer
     */
    public StructuralAnalyzer getStructuralAnalyzer()
    {
        return structuralAnalyzer;
    }

    /**
     * @return the statement recoverer
     */
    public StatementRecoverer getStatementRecoverer()
    {
        return statementRecoverer;
    }

    /**
     * Reserves {@code names} so that {@link #baseNameForSlot} never returns them.
     *
     * @param names the names to reserve
     */
    public void reserveNames(java.util.Set<String> names)
    {
        reservedNames.addAll(names);
    }

    /**
     * Performs all analysis passes needed for recovery.
     */
    public void analyze()
    {
        stripSyntheticMatchExceptionHandlers();

        // Dominance and loop detection must see the exception edges: when a protected region
        // always throws (its only exit is the handler), the code after it is reachable solely
        // through the handler, and without the edges it has no dominators - back edges are then
        // missed and loops degrade to plain conditionals. The edges are removed again so the
        // structural analysis and statement recovery walk only real control flow.
        List<IRBlock[]> excEdges =
            BytecodeLifter.addExceptionEdges(irMethod);

        dominatorTree = new DominatorTree(irMethod);
        dominatorTree.compute();

        loopAnalysis = new LoopAnalysis(irMethod, dominatorTree);
        loopAnalysis.compute();

        BytecodeLifter.removeExceptionEdges(excEdges);

        defUseChains = new DefUseChains(irMethod);
        defUseChains.compute();

        structuralAnalyzer = new StructuralAnalyzer(irMethod, dominatorTree, loopAnalysis);
        structuralAnalyzer.analyze();
    }

    /**
     * Removes compiler-synthesized exception handlers that rethrow caught failures as {@code
     * java.lang.MatchException}.
     */
    private void stripSyntheticMatchExceptionHandlers()
    {
        List<IRBlock> handlerBlocks = new java.util.ArrayList<>();
        java.util.Set<Integer> deconstructSlots = new java.util.HashSet<>();
        for (ExceptionHandler handler : irMethod.getExceptionHandlers())
        {
            IRBlock hb = handler.getHandlerBlock();
            if (hb != null && rethrowsAsMatchException(hb))
            {
                if (!handlerBlocks.contains(hb))
                {
                    handlerBlocks.add(hb);
                }
                collectDeconstructionTemps(handler.getTryStart(), deconstructSlots);
            }
        }
        // When an accessor receiver is a local load rather than the cast directly, resolve the slot
        // to the cast that defines it.
        if (!deconstructSlots.isEmpty())
        {
            for (IRBlock b : irMethod.getBlocks())
            {
                for (IRInstruction instr : b.getInstructions())
                {
                    if (!(instr instanceof TypeCheckInstruction) || !((TypeCheckInstruction) instr).isCast())
                    {
                        continue;
                    }
                    SSAValue castResult = instr.getResult();
                    if (castResult == null)
                    {
                        continue;
                    }
                    for (IRInstruction use : castResult.getUses())
                    {
                        if (use instanceof StoreLocalInstruction
                                && deconstructSlots.contains(((StoreLocalInstruction) use).getLocalIndex()))
                        {
                            recordDeconstructionTemps.add(castResult);
                        }
                    }
                }
            }
        }
        for (IRBlock hb : handlerBlocks)
        {
            if (hb.getPredecessors().isEmpty())
            {
                irMethod.removeBlock(hb);
            }
            else
            {
                irMethod.getExceptionHandlers().removeIf(h -> h.getHandlerBlock() == hb);
            }
        }
    }


    /**
     * True when {@code block} allocates a {@code java.lang.MatchException} (a synthetic rethrow handler).
     */
    private boolean rethrowsAsMatchException(IRBlock block)
    {
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr instanceof NewInstruction
                    && "java/lang/MatchException".equals(((NewInstruction) instr).getClassName()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Records the local slots of the receivers of the (component-accessor) invocations protected by a
     * record-deconstruction's MatchException handler.
     */
    private void collectDeconstructionTemps(IRBlock tryStart, java.util.Set<Integer> slots)
    {
        if (tryStart == null)
        {
            return;
        }
        for (IRInstruction instr : tryStart.getInstructions())
        {
            if (!(instr instanceof InvokeInstruction))
            {
                continue;
            }
            Value receiver = ((InvokeInstruction) instr).getReceiver();
            if (!(receiver instanceof SSAValue))
            {
                continue;
            }
            IRInstruction def = ((SSAValue) receiver).getDefinition();
            if (def instanceof TypeCheckInstruction && ((TypeCheckInstruction) def).isCast())
            {
                recordDeconstructionTemps.add((SSAValue) receiver);
            }
            else if (def instanceof LoadLocalInstruction)
            {
                slots.add(((LoadLocalInstruction) def).getLocalIndex());
            }
        }
    }

    /**
     * Initializes all recovery components.
     */
    public void initializeRecovery()
    {
        recoveryContext = new RecoveryContext(irMethod, sourceMethod, defUseChains);
        recoveryContext.getRecordDeconstructionTemps().addAll(recordDeconstructionTemps);

        nameRecoverer = new NameRecoverer(irMethod, sourceMethod, nameStrategy);
        assignVariableNames();

        expressionRecoverer = new ExpressionRecoverer(recoveryContext);

        controlFlowContext = new ControlFlowContext(irMethod, dominatorTree, loopAnalysis, recoveryContext);

        statementRecoverer = new StatementRecoverer(controlFlowContext, structuralAnalyzer, expressionRecoverer);
        if (sourceMethod.getClassFile() != null)
        {
            statementRecoverer.setEnumClassPool(sourceMethod.getClassFile().getClassPool());
        }
    }

    /**
     * Assigns variable names to all SSA values using the name recoverer.
     */
    private void assignVariableNames()
    {
        assignParameterNames();

        SlotVariablePartition partition = new SlotVariablePartition(irMethod, this::baseNameForSlot,
                nameRecoverer::debugNameAt, nameRecoverer::debugNameAtStore);
        recoveryContext.setSlotPartition(partition);
        recoveryContext.setDebugDescriptorResolver(nameRecoverer::debugDescriptorAt);
        recoveryContext.setDebugStoreDescriptorResolver(nameRecoverer::debugDescriptorAtStore);
        recoveryContext.setDebugNameResolver(nameRecoverer::unambiguousDebugName);

        irMethod.getBlocks().forEach(block -> {
            block.getPhiInstructions().forEach(phi -> {
                if (phi.getResult() != null)
                {
                    String name = partition.nameForPhi(phi);
                    if (name == null)
                    {
                        name = nameRecoverer.generateSyntheticName(phi.getResult());
                    }
                    recoveryContext.setVariableName(phi.getResult(), name);
                }
            });

            block.getInstructions().forEach(instr -> {
                if (instr.getResult() == null)
                {
                    return;
                }
                String name = null;
                if (instr instanceof LoadLocalInstruction)
                {
                    name = partition.nameForLoad((LoadLocalInstruction) instr);
                }
                if (name == null)
                {
                    // A value stored into a local slot shares that slot's variable (the partition unions it
                    // with the slot's loads and phis). Name it by its store so it does not split into a
                    // separate synthetic when it is ALSO materialized on its own - e.g. a boolean method
                    // result stored to a slot AND used directly as an if-condition whose slot is returned via
                    // a phi. Splitting would strand the phi's variable at its default value.
                    StoreLocalInstruction store = singleStoreConsumer(instr.getResult());
                    if (store != null)
                    {
                        name = partition.nameForStore(store);
                    }
                }
                if (name == null)
                {
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
    private String baseNameForSlot(int slot)
    {
        String debug = nameRecoverer != null ? nameRecoverer.unambiguousDebugName(slot) : null;
        if (debug != null && !reservedNames.contains(debug))
        {
            return debug;
        }
        if (!irMethod.isStatic() && slot == 0)
        {
            return "this";
        }
        int paramSlots = locals().parameterSlotCount();
        if (slot < paramSlots)
        {
            return "arg" + getParamIndexForSlot(slot);
        }
        String candidate = "local" + slot;
        if (!reservedNames.isEmpty() && reservedNames.contains(candidate))
        {
            // The natural name collides with a reserved (captured) name. Bump to a suffix beyond the
            // whole local-slot space so the new name also can't collide with any other slot's natural
            // "localN" name.
            int n = Math.max(slot + 1, localSlotCeiling());
            while (reservedNames.contains("local" + n))
            {
                n++;
            }
            return "local" + n;
        }
        return candidate;
    }

    /**
     * One past the highest local slot index referenced in the method (cached).
     */
    private int slotCeiling = -1;

    private int localSlotCeiling()
    {
        if (slotCeiling >= 0)
        {
            return slotCeiling;
        }
        int max = irMethod.getMaxLocals();
        for (IRBlock block : irMethod.getBlocks())
        {
            for (IRInstruction instr : block.getInstructions())
            {
                if (instr instanceof LoadLocalInstruction)
                {
                    max = Math.max(max, ((LoadLocalInstruction) instr).getLocalIndex() + 1);
                }
                else if (instr instanceof StoreLocalInstruction)
                {
                    max = Math.max(max, ((StoreLocalInstruction) instr).getLocalIndex() + 1);
                }
            }
        }
        slotCeiling = max;
        return max;
    }

    /**
     * Assigns names to method parameters.
     */
    private void assignParameterNames()
    {
        int paramIndex = 0;
        for (var param : irMethod.getParameters())
        {
            int slot = locals().slotOfParameter(param);
            // The entry covering pc 0 IS the parameter, even when the slot is later reused for a
            // body local (which makes the whole-slot name set ambiguous and used to degrade the
            // parameter to argN).
            String debug = nameRecoverer != null ? nameRecoverer.debugNameAt(slot, 0) : null;
            if (debug == null && nameRecoverer != null)
            {
                debug = nameRecoverer.unambiguousDebugName(slot);
            }
            String name;
            if (debug != null)
            {
                name = debug;
            }
            else if (!irMethod.isStatic() && paramIndex == 0)
            {
                name = "this";
            }
            else
            {
                int argIndex = irMethod.isStatic() ? paramIndex : paramIndex - 1;
                name = "arg" + argIndex;
            }
            recoveryContext.setVariableName(param, name);
            paramIndex++;
        }
    }

    /**
     * Recovers a fallback name for the result of an instruction.
     */
    private String recoverNameForInstruction(IRInstruction instr)
    {
        if (instr instanceof LoadLocalInstruction)
        {
            return baseNameForSlot(((LoadLocalInstruction) instr).getLocalIndex());
        }
        return nameRecoverer.generateSyntheticName(instr.getResult());
    }

    /**
     * The single StoreLocal that stores {@code value} into a slot, or null if there is not exactly one.
     */
    private StoreLocalInstruction singleStoreConsumer(SSAValue value)
    {
        StoreLocalInstruction found = null;
        for (IRInstruction use : value.getUses())
        {
            if (use instanceof StoreLocalInstruction && ((StoreLocalInstruction) use).getValue() == value)
            {
                if (found != null)
                {
                    return null;
                }
                found = (StoreLocalInstruction) use;
            }
        }
        return found;
    }

    private int getParamIndexForSlot(int slot)
    {
        boolean isStatic = irMethod.isStatic();

        if (!isStatic && slot == 0)
        {
            return -1;
        }

        String descriptor = irMethod.getDescriptor();
        if (descriptor == null)
        {
            return isStatic ? slot : slot - 1;
        }

        List<String> paramTypes = parseParameterTypes(descriptor);
        int currentSlot = isStatic ? 0 : 1;

        for (int paramIndex = 0; paramIndex < paramTypes.size(); paramIndex++)
        {
            String paramType = paramTypes.get(paramIndex);
            int slotsForParam = 1;
            if ("J".equals(paramType) || "D".equals(paramType))
            {
                slotsForParam = 2;
            }

            if (slot >= currentSlot && slot < currentSlot + slotsForParam)
            {
                return paramIndex;
            }
            currentSlot += slotsForParam;
        }

        return -1;
    }

    private List<String> parseParameterTypes(String descriptor)
    {
        List<String> types = new java.util.ArrayList<>();
        int start = descriptor.indexOf('(');
        int end = descriptor.indexOf(')');
        if (start < 0 || end < 0)
        {
            return types;
        }

        String params = descriptor.substring(start + 1, end);
        int i = 0;
        while (i < params.length())
        {
            char c = params.charAt(i);
            if (c == 'L')
            {
                int semiPos = params.indexOf(';', i);
                if (semiPos > i)
                {
                    types.add(params.substring(i, semiPos + 1));
                    i = semiPos + 1;
                }
                else
                {
                    break;
                }
            }
            else if (c == '[')
            {
                int arrayStart = i;
                while (i < params.length() && params.charAt(i) == '[')
                {
                    i++;
                }
                if (i < params.length())
                {
                    char elementType = params.charAt(i);
                    if (elementType == 'L')
                    {
                        int semiPos = params.indexOf(';', i);
                        if (semiPos > i)
                        {
                            types.add(params.substring(arrayStart, semiPos + 1));
                            i = semiPos + 1;
                        }
                        else
                        {
                            break;
                        }
                    }
                    else
                    {
                        types.add(params.substring(arrayStart, i + 1));
                        i++;
                    }
                }
            }
            else
            {
                types.add(String.valueOf(c));
                i++;
            }
        }

        return types;
    }

    private MethodLocals locals;

    /**
     * The method's parameter slot layout (lazily built); see {@link MethodLocals}.
     */
    private MethodLocals locals()
    {
        if (locals == null)
        {
            locals = new MethodLocals(irMethod);
        }
        return locals;
    }

    /**
     * Recovers the method body as a block statement, running the analysis and initialization passes first
     * if they have not run yet.
     *
     * @return the recovered body, re-recovered as a dispatch loop if the structured pass dropped operations
     * @throws StatementRecoverer.RetiredSchemaRecoveryException if no route owned a region and the dispatch
     *         fallback is unavailable (the method has exception handlers, or dispatch is disabled)
     */
    public BlockStmt recover()
    {
        if (dominatorTree == null)
        {
            analyze();
        }
        if (statementRecoverer == null)
        {
            initializeRecovery();
        }

        List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();
        boolean noHandlers = handlers == null || handlers.isEmpty();
        boolean dispatchAvailable = noHandlers && !Boolean.getBoolean("dispatch.off");
        BlockStmt body;
        try
        {
            body = statementRecoverer.recoverMethod();
        }
        catch (StatementRecoverer.RetiredSchemaRecoveryException retired)
        {
            // No structured route owned a region (e.g. irreducible flow the engine declines): the
            // faithful dispatch loop is the totality fallback. A handler-bearing method cannot take
            // it (a flat dispatch loop cannot model the try/catch regions), so there the signal
            // stays a loud routing gap.
            if (!dispatchAvailable)
            {
                throw retired;
            }
            initializeRecovery();
            return statementRecoverer.recoverMethodAsDispatch();
        }

        // Completeness guarantee: if any observable operation reachable in the bytecode is absent
        // from the recovered source (a dropped block, by any mechanism), re-recover the whole
        // method as a faithful dispatch loop on a FRESH context (the first pass mutated
        // materialization/declaration state). Skipped for methods with exception handlers, where a
        // flat dispatch loop cannot model the try/catch regions.
        if (dispatchAvailable && statementRecoverer.hasDroppedOperations(body))
        {
            initializeRecovery();
            body = statementRecoverer.recoverMethodAsDispatch();
        }
        return body;
    }

    /**
     * Full recovery pipeline: analyze, initialize, and recover.
     *
     * @param irMethod     the method to recover
     * @param sourceMethod the method entry the IR was lifted from
     * @return the recovered body
     */
    public static BlockStmt recoverMethod(IRMethod irMethod, MethodEntry sourceMethod)
    {
        MethodRecoverer recoverer = new MethodRecoverer(irMethod, sourceMethod);
        return recoverer.recover();
    }

    /**
     * Full recovery pipeline with custom name strategy.
     *
     * @param irMethod     the method to recover
     * @param sourceMethod the method entry the IR was lifted from
     * @param nameStrategy the strategy that names recovered locals
     * @return the recovered body
     */
    public static BlockStmt recoverMethod(IRMethod irMethod, MethodEntry sourceMethod, NameRecoveryStrategy nameStrategy)
    {
        MethodRecoverer recoverer = new MethodRecoverer(irMethod, sourceMethod, nameStrategy);
        return recoverer.recover();
    }
}
