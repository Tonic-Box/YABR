package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import lombok.Getter;

import java.util.*;

/**
 * Computes def-use chains for SSA values.
 */
@Getter
public class DefUseChains {

    private final IRMethod method;
    private Map<SSAValue, IRInstruction> definitions;
    private Map<SSAValue, Set<IRInstruction>> uses;
    private Map<IRInstruction, Set<SSAValue>> instrUses;
    private Map<IRInstruction, SSAValue> instrDefs;

    public DefUseChains(IRMethod method) {
        this.method = method;
        this.definitions = new HashMap<>();
        this.uses = new HashMap<>();
        this.instrUses = new HashMap<>();
        this.instrDefs = new HashMap<>();
    }

    /**
     * Computes def-use chains for all values in the method.
     */
    public void compute() {
        for (IRBlock block : method.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                processInstruction(phi);
            }
            for (IRInstruction instr : block.getInstructions()) {
                processInstruction(instr);
            }
        }
    }

    private void processInstruction(IRInstruction instr) {
        if (instr.getResult() != null) {
            SSAValue def = instr.getResult();
            definitions.put(def, instr);
            instrDefs.put(instr, def);
        }

        Set<SSAValue> instrUseSet = new HashSet<>();
        for (Value operand : instr.getOperands()) {
            if (operand instanceof SSAValue) {
                SSAValue ssa = (SSAValue) operand;
                uses.computeIfAbsent(ssa, k -> new HashSet<>()).add(instr);
                instrUseSet.add(ssa);
            }
        }
        instrUses.put(instr, instrUseSet);
    }

    /**
     * Gets the instruction that defines the specified value.
     *
     * @param value the value to query
     * @return the defining instruction, or null if none exists
     */
    public IRInstruction getDefinition(SSAValue value) {
        return definitions.get(value);
    }

    /**
     * Gets all instructions that use the specified value.
     *
     * @param value the value to query
     * @return the set of instructions that use the value
     */
    public Set<IRInstruction> getUses(SSAValue value) {
        return uses.getOrDefault(value, Collections.emptySet());
    }

    /**
     * Gets all values used by the specified instruction.
     *
     * @param instr the instruction to query
     * @return the set of values used by the instruction
     */
    public Set<SSAValue> getUsedValues(IRInstruction instr) {
        return instrUses.getOrDefault(instr, Collections.emptySet());
    }

    /**
     * Gets the value defined by the specified instruction.
     *
     * @param instr the instruction to query
     * @return the defined value, or null if the instruction defines no value
     */
    public SSAValue getDefinedValue(IRInstruction instr) {
        return instrDefs.get(instr);
    }

    /**
     * Checks if the specified value has any uses.
     *
     * @param value the value to check
     * @return true if the value has at least one use
     */
    public boolean hasUses(SSAValue value) {
        Set<IRInstruction> useSet = uses.get(value);
        return useSet != null && !useSet.isEmpty();
    }

    /**
     * Gets the number of uses of the specified value.
     *
     * @param value the value to query
     * @return the use count
     */
    public int getUseCount(SSAValue value) {
        Set<IRInstruction> useSet = uses.get(value);
        return useSet != null ? useSet.size() : 0;
    }

    /**
     * Checks if an instruction is dead code.
     *
     * @param instr the instruction to check
     * @return true if the instruction defines a value that has no uses
     */
    public boolean isDeadCode(IRInstruction instr) {
        SSAValue def = instrDefs.get(instr);
        if (def == null) return false;
        return !hasUses(def);
    }
}
