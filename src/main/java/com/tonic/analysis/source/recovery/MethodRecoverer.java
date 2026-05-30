package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
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

        SlotVariablePartition partition = new SlotVariablePartition(irMethod, this::baseNameForSlot);
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
        if (!irMethod.isStatic() && slot == 0) {
            return "this";
        }
        int paramSlots = computeParameterSlots();
        if (slot < paramSlots) {
            return "arg" + getParamIndexForSlot(slot);
        }
        return "local" + slot;
    }

    /**
     * Assigns names to method parameters.
     * For instance methods, the first parameter (slot 0) is 'this'.
     */
    private void assignParameterNames() {
        int paramIndex = 0;
        for (var param : irMethod.getParameters()) {
            String name;
            if (!irMethod.isStatic() && paramIndex == 0) {
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

    /**
     * Computes the number of local variable slots used by parameters.
     * Note: irMethod.getParameters() includes 'this' for instance methods.
     */
    private int computeParameterSlots() {
        int slots = 0;
        for (var param : irMethod.getParameters()) {
            slots++;
            if (param.getType() instanceof PrimitiveType) {
                PrimitiveType p = (PrimitiveType) param.getType();
                if (p == PrimitiveType.LONG ||
                    p == PrimitiveType.DOUBLE) {
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
