package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Merges duplicate blocks created by node splitting while preserving reducibility.
 */
public class DuplicateBlockMerging implements IRTransform {

    private final boolean aggressive;

    public DuplicateBlockMerging() {
        this(false);
    }

    public DuplicateBlockMerging(boolean aggressive) {
        this.aggressive = aggressive;
    }

    @Override
    public String getName() {
        return "DuplicateBlockMerging";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;
        boolean merged;

        do {
            merged = false;
            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loops = new LoopAnalysis(method, domTree);
            loops.compute();

            Map<String, List<IRBlock>> candidates = findCandidates(method);

            outer:
            for (List<IRBlock> group : candidates.values()) {
                if (group.size() < 2) continue;
                for (int i = 0; i < group.size() - 1; i++) {
                    for (int j = i + 1; j < group.size(); j++) {
                        if (isMergeSafe(group.get(i), group.get(j), domTree, loops)) {
                            mergeBlocks(group.get(i), group.get(j), method);
                            merged = true;
                            changed = true;
                            break outer;
                        }
                    }
                }
            }
        } while (merged);

        return changed;
    }

    private Map<String, List<IRBlock>> findCandidates(IRMethod method) {
        Map<String, List<IRBlock>> groups = new HashMap<>();
        for (IRBlock block : method.getBlocks()) {
            if (block == method.getEntryBlock()) continue;
            String sig = computeBlockSignature(block);
            groups.computeIfAbsent(sig, k -> new ArrayList<>()).add(block);
        }
        return groups;
    }

    private String computeBlockSignature(IRBlock block) {
        StringBuilder sb = new StringBuilder();
        for (IRInstruction instr : block.getInstructions()) {
            sb.append(getInstructionSignature(instr)).append(";");
        }
        IRInstruction term = block.getTerminator();
        if (term != null) {
            sb.append("T:").append(getTerminatorSignature(term));
        }
        return sb.toString();
    }

    private String getInstructionSignature(IRInstruction instr) {
        if (instr instanceof BinaryOpInstruction bin) {
            return "BIN:" + bin.getOp();
        } else if (instr instanceof UnaryOpInstruction un) {
            return "UN:" + un.getOp();
        } else if (instr instanceof LoadLocalInstruction ld) {
            return "LD:" + ld.getLocalIndex();
        } else if (instr instanceof StoreLocalInstruction st) {
            return "ST:" + st.getLocalIndex();
        } else if (instr instanceof GetFieldInstruction fl) {
            return "FLD:" + fl.getOwner() + "." + fl.getName();
        } else if (instr instanceof PutFieldInstruction fs) {
            return "FST:" + fs.getOwner() + "." + fs.getName();
        } else if (instr instanceof ArrayLoadInstruction) {
            return "ALD";
        } else if (instr instanceof ArrayStoreInstruction) {
            return "AST";
        } else if (instr instanceof InvokeInstruction inv) {
            return "INV:" + inv.getOwner() + "." + inv.getName() + inv.getDescriptor();
        } else if (instr instanceof NewInstruction nw) {
            return "NEW:" + nw.getClassName();
        } else if (instr instanceof NewArrayInstruction na) {
            return "NEWA:" + na.getElementType();
        } else if (instr instanceof CastInstruction cast) {
            return "CAST:" + cast.getTargetType();
        } else if (instr instanceof InstanceOfInstruction iof) {
            return "IOF:" + iof.getCheckType();
        } else if (instr instanceof PhiInstruction) {
            return "PHI";
        } else if (instr instanceof ConstantInstruction c) {
            return "CONST:" + c.getConstant();
        }
        return instr.getClass().getSimpleName();
    }

    private String getTerminatorSignature(IRInstruction term) {
        if (term instanceof GotoInstruction) {
            return "GOTO";
        } else if (term instanceof BranchInstruction br) {
            return "BR:" + br.getCondition();
        } else if (term instanceof SwitchInstruction sw) {
            return "SW:" + sw.getCases().size();
        } else if (term instanceof ReturnInstruction ret) {
            return "RET:" + (ret.isVoidReturn() ? "V" : "R");
        } else if (term instanceof ThrowInstruction) {
            return "THROW";
        }
        return term.getClass().getSimpleName();
    }

    private boolean isMergeSafe(IRBlock b1, IRBlock b2, DominatorTree domTree, LoopAnalysis loops) {
        Set<IRBlock> preds1 = new HashSet<>(b1.getPredecessors());
        Set<IRBlock> preds2 = new HashSet<>(b2.getPredecessors());
        Set<IRBlock> allPreds = new HashSet<>();
        allPreds.addAll(preds1);
        allPreds.addAll(preds2);

        // Conservative: one predecessor dominates all others
        for (IRBlock pred : allPreds) {
            boolean dominatesAll = true;
            for (IRBlock other : allPreds) {
                if (other != pred && !domTree.dominates(pred, other)) {
                    dominatesAll = false;
                    break;
                }
            }
            if (dominatesAll) return true;
        }

        if (!aggressive) return false;

        // Aggressive: check no loop entry conflict
        LoopAnalysis.Loop loop1 = loops.getLoop(b1);
        LoopAnalysis.Loop loop2 = loops.getLoop(b2);

        if (loop1 != loop2) return false;

        for (IRBlock pred : allPreds) {
            LoopAnalysis.Loop predLoop = loops.getLoop(pred);
            if (predLoop != loop1) {
                // Entry from different loop - potential irreducibility
                int entriesFromOutside = 0;
                for (IRBlock p : allPreds) {
                    if (loops.getLoop(p) != loop1) entriesFromOutside++;
                }
                if (entriesFromOutside > 1) return false;
            }
        }
        return true;
    }

    private void mergeBlocks(IRBlock survivor, IRBlock duplicate, IRMethod method) {
        // Build value mapping: duplicate values -> survivor values
        Map<Value, Value> valueMap = buildValueMapping(survivor, duplicate);

        // Redirect predecessors of duplicate to survivor
        for (IRBlock pred : new ArrayList<>(duplicate.getPredecessors())) {
            IRInstruction term = pred.getTerminator();
            if (term != null) {
                term.replaceTarget(duplicate, survivor);
            }
            pred.getSuccessors().remove(duplicate);
            if (!pred.getSuccessors().contains(survivor)) {
                pred.getSuccessors().add(survivor);
            }
            duplicate.getPredecessors().remove(pred);
            if (!survivor.getPredecessors().contains(pred)) {
                survivor.getPredecessors().add(pred);
            }
        }

        // Update phi instructions in survivor to include new predecessors
        updatePhisForMerge(survivor, duplicate, valueMap);

        // Replace uses of duplicate's values in successors
        for (IRBlock succ : duplicate.getSuccessors()) {
            for (IRInstruction instr : succ.getInstructions()) {
                for (Value oldVal : valueMap.keySet()) {
                    if (instr.getOperands().contains(oldVal)) {
                        instr.replaceOperand(oldVal, valueMap.get(oldVal));
                    }
                }
            }
            if (succ.getTerminator() != null) {
                for (Value oldVal : valueMap.keySet()) {
                    if (succ.getTerminator().getOperands().contains(oldVal)) {
                        succ.getTerminator().replaceOperand(oldVal, valueMap.get(oldVal));
                    }
                }
            }
        }

        // Remove duplicate block
        method.getBlocks().remove(duplicate);
    }

    private Map<Value, Value> buildValueMapping(IRBlock survivor, IRBlock duplicate) {
        Map<Value, Value> mapping = new HashMap<>();
        List<IRInstruction> survInstr = survivor.getInstructions();
        List<IRInstruction> dupInstr = duplicate.getInstructions();

        for (int i = 0; i < Math.min(survInstr.size(), dupInstr.size()); i++) {
            SSAValue survResult = survInstr.get(i).getResult();
            SSAValue dupResult = dupInstr.get(i).getResult();
            if (survResult != null && dupResult != null) {
                mapping.put(dupResult, survResult);
            }
        }
        return mapping;
    }

    private void updatePhisForMerge(IRBlock survivor, IRBlock duplicate, Map<Value, Value> valueMap) {
        // For each phi in survivor's successors, update incoming values
        for (IRBlock succ : survivor.getSuccessors()) {
            for (IRInstruction instr : succ.getInstructions()) {
                if (instr instanceof PhiInstruction phi) {
                    Value dupValue = phi.getIncoming(duplicate);
                    if (dupValue != null) {
                        // Map the value and associate with merged predecessors
                        Value mappedValue = valueMap.getOrDefault(dupValue, dupValue);
                        for (IRBlock pred : duplicate.getPredecessors()) {
                            phi.addIncoming(mappedValue, pred);
                        }
                        phi.removeIncoming(duplicate);
                    }
                }
            }
        }
    }
}
