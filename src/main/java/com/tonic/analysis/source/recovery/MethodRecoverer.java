package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
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

    // Analysis results
    private DominatorTree dominatorTree;
    private LoopAnalysis loopAnalysis;
    private DefUseChains defUseChains;

    // Recovery components
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
        // Compute dominator tree
        dominatorTree = new DominatorTree(irMethod);
        dominatorTree.compute();

        // Compute loop information
        loopAnalysis = new LoopAnalysis(irMethod, dominatorTree);
        loopAnalysis.compute();

        // Compute def-use chains
        defUseChains = new DefUseChains(irMethod);
        defUseChains.compute();

        // Run structural analysis
        structuralAnalyzer = new StructuralAnalyzer(irMethod, dominatorTree, loopAnalysis);
        structuralAnalyzer.analyze();
    }

    /**
     * Initializes all recovery components.
     */
    public void initializeRecovery() {
        // Create recovery context
        recoveryContext = new RecoveryContext(irMethod, sourceMethod, defUseChains);

        // Create name recoverer and assign names to SSA values
        nameRecoverer = new NameRecoverer(irMethod, sourceMethod, nameStrategy);
        assignVariableNames();

        // Create expression recoverer
        expressionRecoverer = new ExpressionRecoverer(recoveryContext);

        // Create control flow context
        controlFlowContext = new ControlFlowContext(irMethod, dominatorTree, loopAnalysis, recoveryContext);

        // Create statement recoverer
        statementRecoverer = new StatementRecoverer(controlFlowContext, structuralAnalyzer, expressionRecoverer);
    }

    /**
     * Assigns variable names to all SSA values using the name recoverer.
     */
    private void assignVariableNames() {
        // First, name the method parameters (including 'this' for instance methods)
        assignParameterNames();

        // Track local slot to name mapping for consistency
        java.util.Map<Integer, String> localSlotNames = new java.util.HashMap<>();

        // First pass: collect local slot names from LoadLocalInstruction
        // This ensures we have names established before processing phis
        irMethod.getBlocks().forEach(block -> {
            block.getInstructions().forEach(instr -> {
                if (instr instanceof LoadLocalInstruction load) {
                    int localIndex = load.getLocalIndex();
                    if (!localSlotNames.containsKey(localIndex)) {
                        // For instance methods, slot 0 is 'this'
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
                    }
                }
            });
        });

        // Second pass: assign names using established slot names
        irMethod.getBlocks().forEach(block -> {
            block.getPhiInstructions().forEach(phi -> {
                if (phi.getResult() != null) {
                    // Try to find the local slot this phi represents
                    Integer localSlot = findPhiLocalSlot(phi);
                    String name;
                    if (localSlot != null && localSlotNames.containsKey(localSlot)) {
                        name = localSlotNames.get(localSlot);
                    } else if (localSlot != null) {
                        // Generate consistent name for this slot
                        int paramSlots = computeParameterSlots();
                        if (localSlot < paramSlots) {
                            int argIndex = irMethod.isStatic() ? localSlot : localSlot - 1;
                            name = "arg" + argIndex;
                        } else {
                            name = "local" + localSlot;
                        }
                        localSlotNames.put(localSlot, name);
                    } else {
                        name = nameRecoverer.generateSyntheticName(phi.getResult());
                    }
                    recoveryContext.setVariableName(phi.getResult(), name);
                }
            });

            block.getInstructions().forEach(instr -> {
                if (instr.getResult() != null) {
                    String name = recoverNameForInstruction(instr, localSlotNames);
                    recoveryContext.setVariableName(instr.getResult(), name);
                }
            });
        });
    }

    /**
     * Finds the local variable slot that a phi instruction represents.
     * SSA naming convention: "v{varIndex}_{version}" or "phi_{varIndex}"
     */
    private Integer findPhiLocalSlot(PhiInstruction phi) {
        SSAValue result = phi.getResult();
        if (result == null) return null;

        String name = result.getName();

        // Try to parse "phi_N" format
        if (name != null && name.startsWith("phi_")) {
            try {
                return Integer.parseInt(name.substring(4));
            } catch (NumberFormatException ignored) {}
        }

        // Try to parse "vN_M" format (variable N, version M)
        if (name != null && name.matches("v\\d+_\\d+")) {
            try {
                int underscorePos = name.indexOf('_');
                return Integer.parseInt(name.substring(1, underscorePos));
            } catch (NumberFormatException ignored) {}
        }

        // Try to parse plain "vN" format (some phi nodes might use this)
        if (name != null && name.matches("v\\d+")) {
            try {
                return Integer.parseInt(name.substring(1));
            } catch (NumberFormatException ignored) {}
        }

        // Fallback: check if the phi result is used by a StoreLocalInstruction
        for (IRInstruction use : result.getUses()) {
            if (use instanceof StoreLocalInstruction store) {
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
            // Long and double take 2 slots
            if (param.getType() instanceof com.tonic.analysis.ssa.type.PrimitiveType p) {
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
     */
    private String recoverNameForInstruction(IRInstruction instr, java.util.Map<Integer, String> localSlotNames) {
        if (instr instanceof LoadLocalInstruction load) {
            int localIndex = load.getLocalIndex();
            // For instance methods, slot 0 is 'this'
            if (!irMethod.isStatic() && localIndex == 0) {
                return "this";
            }
            // For parameters, generate arg names
            int paramSlots = computeParameterSlots();
            if (localIndex < paramSlots) {
                int argIndex = irMethod.isStatic() ? localIndex : localIndex - 1;
                return "arg" + argIndex;
            }
            // Check if we already have a name for this local slot
            if (localSlotNames.containsKey(localIndex)) {
                return localSlotNames.get(localIndex);
            }
            // Generate a new name based on local slot
            String name = "local" + localIndex;
            localSlotNames.put(localIndex, name);
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
            // Long and double take 2 slots
            if (param.getType() instanceof com.tonic.analysis.ssa.type.PrimitiveType p) {
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
