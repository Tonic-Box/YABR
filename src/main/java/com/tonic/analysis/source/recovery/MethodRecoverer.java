package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.LoadLocalInstruction;
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

        irMethod.getBlocks().forEach(block -> {
            block.getPhiInstructions().forEach(phi -> {
                if (phi.getResult() != null) {
                    String name = nameRecoverer.generateSyntheticName(phi.getResult());
                    recoveryContext.setVariableName(phi.getResult(), name);
                }
            });

            block.getInstructions().forEach(instr -> {
                if (instr.getResult() != null) {
                    String name = recoverNameForInstruction(instr);
                    recoveryContext.setVariableName(instr.getResult(), name);
                }
            });
        });
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
    private String recoverNameForInstruction(IRInstruction instr) {
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
        }
        return nameRecoverer.generateSyntheticName(instr.getResult());
    }

    /**
     * Computes the number of local variable slots used by parameters.
     */
    private int computeParameterSlots() {
        int slots = irMethod.isStatic() ? 0 : 1; // 'this' takes slot 0 for instance methods
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
