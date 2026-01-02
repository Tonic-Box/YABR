package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.MethodEntry;
import lombok.Getter;

/**
 * Facade for recovering source-level AST from an IR method.
 * Coordinates expression recovery, control flow analysis, and statement recovery.
 */
@Getter
public class MethodRecoverer {

    private final IRMethod irMethod;
    private final MethodEntry sourceMethod;
    private final NameRecoveryStrategy nameStrategy;

    private DominatorTree dominatorTree;
    private LoopAnalysis loopAnalysis;
    private DefUseChains defUseChains;

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
     * Performs all analysis passes needed for recovery.
     */
    public void analyze() {
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
     * Initializes all recovery components.
     */
    public void initializeRecovery() {
        recoveryContext = new RecoveryContext(irMethod, sourceMethod, defUseChains);

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

        java.util.Map<Integer, String> localSlotNames = new java.util.HashMap<>();
        java.util.Map<Integer, IRType> localSlotTypes = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> slotReuseCounter = new java.util.HashMap<>();

        irMethod.getBlocks().forEach(block -> {
            block.getInstructions().forEach(instr -> {
                if (instr instanceof LoadLocalInstruction) {
                    LoadLocalInstruction load = (LoadLocalInstruction) instr;
                    int localIndex = load.getLocalIndex();
                    IRType valueType = load.getResult() != null ? load.getResult().getType() : null;

                    if (!localSlotNames.containsKey(localIndex)) {
                        if (!irMethod.isStatic() && localIndex == 0) {
                            localSlotNames.put(localIndex, "this");
                        } else {
                            int paramSlots = computeParameterSlots();
                            if (localIndex < paramSlots) {
                                int argIndex = irMethod.isStatic() ? localIndex : localIndex - 1;
                                localSlotNames.put(localIndex, "arg" + argIndex);
                            } else {
                                localSlotNames.put(localIndex, "local" + localIndex);
                            }
                        }
                        localSlotTypes.put(localIndex, valueType);
                    }
                }
            });
        });

        irMethod.getBlocks().forEach(block -> {
            block.getPhiInstructions().forEach(phi -> {
                if (phi.getResult() != null) {
                    Integer localSlot = findPhiLocalSlot(phi);
                    IRType valueType = phi.getResult().getType();
                    String name;
                    if (localSlot != null && localSlotNames.containsKey(localSlot)) {
                        IRType existingType = localSlotTypes.get(localSlot);
                        if (areTypesCompatible(existingType, valueType)) {
                            name = localSlotNames.get(localSlot);
                        } else {
                            int count = slotReuseCounter.getOrDefault(localSlot, 0) + 1;
                            slotReuseCounter.put(localSlot, count);
                            name = "local" + localSlot + "_" + count;
                        }
                    } else if (localSlot != null) {
                        int paramSlots = computeParameterSlots();
                        if (localSlot < paramSlots) {
                            int argIndex = irMethod.isStatic() ? localSlot : localSlot - 1;
                            name = "arg" + argIndex;
                        } else {
                            name = "local" + localSlot;
                        }
                        localSlotNames.put(localSlot, name);
                        localSlotTypes.put(localSlot, valueType);
                    } else {
                        name = nameRecoverer.generateSyntheticName(phi.getResult());
                    }
                    recoveryContext.setVariableName(phi.getResult(), name);
                }
            });

            block.getInstructions().forEach(instr -> {
                if (instr.getResult() != null) {
                    String name = recoverNameForInstruction(instr, localSlotNames, localSlotTypes, slotReuseCounter);
                    recoveryContext.setVariableName(instr.getResult(), name);
                }
            });
        });
    }

    private boolean areTypesCompatible(IRType type1, IRType type2) {
        if (type1 == null || type2 == null) {
            return true;
        }
        if (type1.equals(type2)) {
            return true;
        }
        boolean type1Primitive = type1 instanceof PrimitiveType;
        boolean type2Primitive = type2 instanceof PrimitiveType;
        if (type1Primitive && type2Primitive) {
            return true;
        }
        boolean type1Reference = type1 instanceof ReferenceType || type1.isArray();
        boolean type2Reference = type2 instanceof ReferenceType || type2.isArray();
        if (type1Reference && type2Reference) {
            return true;
        }
        return false;
    }

    /**
     * Finds the local variable slot that a phi instruction represents.
     * SSA naming convention: "v{varIndex}_{version}" or "phi_{varIndex}"
     */
    private Integer findPhiLocalSlot(PhiInstruction phi) {
        SSAValue result = phi.getResult();
        if (result == null) return null;

        String name = result.getName();

        if (name != null && name.startsWith("phi_")) {
            try {
                return Integer.parseInt(name.substring(4));
            } catch (NumberFormatException ignored) {}
        }

        if (name != null && name.matches("v\\d+_\\d+")) {
            try {
                int underscorePos = name.indexOf('_');
                return Integer.parseInt(name.substring(1, underscorePos));
            } catch (NumberFormatException ignored) {}
        }

        if (name != null && name.matches("v\\d+")) {
            try {
                return Integer.parseInt(name.substring(1));
            } catch (NumberFormatException ignored) {}
        }

        for (IRInstruction use : result.getUses()) {
            if (use instanceof StoreLocalInstruction) {
                StoreLocalInstruction store = (StoreLocalInstruction) use;
                return store.getLocalIndex();
            }
        }

        return null;
    }

    /**
     * Assigns names to method parameters.
     * For instance methods, the first parameter (slot 0) is 'this'.
     */
    private void assignParameterNames() {
        int slot = 0;
        for (var param : irMethod.getParameters()) {
            String name;
            if (!irMethod.isStatic() && slot == 0) {
                name = "this";
            } else {
                int argIndex = irMethod.isStatic() ? slot : slot - 1;
                name = "arg" + argIndex;
            }
            recoveryContext.setVariableName(param, name);
            slot++;
            if (param.getType() instanceof com.tonic.analysis.ssa.type.PrimitiveType) {
                com.tonic.analysis.ssa.type.PrimitiveType p = (com.tonic.analysis.ssa.type.PrimitiveType) param.getType();
                if (p == com.tonic.analysis.ssa.type.PrimitiveType.LONG ||
                    p == com.tonic.analysis.ssa.type.PrimitiveType.DOUBLE) {
                    slot++;
                }
            }
        }
    }

    /**
     * Recovers a name for the result of an instruction.
     * Uses local slot information when available to detect 'this' and parameters.
     * When a slot is reused with incompatible types, assigns a unique name.
     */
    private String recoverNameForInstruction(IRInstruction instr,
                                              java.util.Map<Integer, String> localSlotNames,
                                              java.util.Map<Integer, IRType> localSlotTypes,
                                              java.util.Map<Integer, Integer> slotReuseCounter) {
        if (instr instanceof LoadLocalInstruction) {
            LoadLocalInstruction load = (LoadLocalInstruction) instr;
            int localIndex = load.getLocalIndex();
            IRType valueType = load.getResult() != null ? load.getResult().getType() : null;

            if (!irMethod.isStatic() && localIndex == 0) {
                return "this";
            }
            int paramSlots = computeParameterSlots();
            if (localIndex < paramSlots) {
                int argIndex = irMethod.isStatic() ? localIndex : localIndex - 1;
                return "arg" + argIndex;
            }
            if (localSlotNames.containsKey(localIndex)) {
                IRType existingType = localSlotTypes.get(localIndex);
                if (areTypesCompatible(existingType, valueType)) {
                    return localSlotNames.get(localIndex);
                } else {
                    int count = slotReuseCounter.getOrDefault(localIndex, 0) + 1;
                    slotReuseCounter.put(localIndex, count);
                    String newName = "local" + localIndex + "_" + count;
                    localSlotTypes.put(localIndex, valueType);
                    return newName;
                }
            }
            String name = "local" + localIndex;
            localSlotNames.put(localIndex, name);
            localSlotTypes.put(localIndex, valueType);
            return name;
        }
        return nameRecoverer.generateSyntheticName(instr.getResult());
    }

    /**
     * Computes the number of local variable slots used by parameters.
     * Note: irMethod.getParameters() includes 'this' for instance methods.
     */
    private int computeParameterSlots() {
        int slots = 0;
        for (var param : irMethod.getParameters()) {
            slots++;
            if (param.getType() instanceof com.tonic.analysis.ssa.type.PrimitiveType) {
                com.tonic.analysis.ssa.type.PrimitiveType p = (com.tonic.analysis.ssa.type.PrimitiveType) param.getType();
                if (p == com.tonic.analysis.ssa.type.PrimitiveType.LONG ||
                    p == com.tonic.analysis.ssa.type.PrimitiveType.DOUBLE) {
                    slots++;
                }
            }
        }
        return slots;
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

        return statementRecoverer.recoverMethod();
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
