package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Analyzes constructor bytecode to extract field assignments.
 * Used to auto-populate object fields during simulation.
 */
public final class ConstructorAnalyzer {

    public ConstructorAnalyzer() {
    }

    public SimObject analyzeConstructor(AllocationSite site, IRMethod constructor,
                                         List<SimValue> constructorArgs, SimHeap heap) {
        if (constructor == null || !isConstructor(constructor)) {
            return new SimObject(site);
        }

        Map<FieldKey, SimValue> assignments = extractFieldAssignments(constructor, constructorArgs);

        SimObject obj = new SimObject(site);
        for (Map.Entry<FieldKey, SimValue> entry : assignments.entrySet()) {
            obj = obj.withField(entry.getKey(), entry.getValue());
        }

        return obj;
    }

    public Map<FieldKey, SimValue> extractFieldAssignments(IRMethod constructor, List<SimValue> args) {
        Map<FieldKey, SimValue> assignments = new LinkedHashMap<>();

        List<IRBlock> blocks = constructor.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return assignments;
        }

        Map<Integer, SimValue> localBindings = new HashMap<>();
        localBindings.put(0, SimValue.ofType(IRType.fromDescriptor("L" + constructor.getOwnerClass() + ";"), null));
        for (int i = 0; i < args.size(); i++) {
            localBindings.put(i + 1, args.get(i));
        }

        for (IRBlock block : blocks) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof FieldAccessInstruction) {
                    FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
                    if (fieldAccess.isStore() && !fieldAccess.isStatic()) {
                        FieldKey fieldKey = FieldKey.of(
                            fieldAccess.getOwner(),
                            fieldAccess.getName(),
                            fieldAccess.getDescriptor()
                        );
                        SimValue value = resolveValue(fieldAccess.getValue(), localBindings, instr);
                        assignments.put(fieldKey, value);
                    }
                } else if (instr instanceof StoreLocalInstruction) {
                    StoreLocalInstruction store = (StoreLocalInstruction) instr;
                    SimValue value = resolveValue(store.getValue(), localBindings, instr);
                    localBindings.put(store.getLocalIndex(), value);
                }
            }
        }

        return assignments;
    }

    private SimValue resolveValue(Value value, Map<Integer, SimValue> locals, IRInstruction instr) {
        if (value == null) {
            return SimValue.unknown(instr);
        }

        if (value instanceof Constant) {
            Constant constant = (Constant) value;
            return SimValue.constant(constant.getValue(), value.getType(), instr);
        }

        return SimValue.ofType(value.getType(), instr);
    }

    public Set<FieldKey> getAssignedFields(IRMethod constructor) {
        Map<FieldKey, SimValue> assignments = extractFieldAssignments(constructor, Collections.emptyList());
        return assignments.keySet();
    }

    public boolean assignsField(IRMethod constructor, FieldKey field) {
        Set<FieldKey> assigned = getAssignedFields(constructor);
        return assigned.contains(field);
    }

    public boolean hasThisEscape(IRMethod constructor) {
        List<IRBlock> blocks = constructor.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }

        for (IRBlock block : blocks) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof InvokeInstruction) {
                    return true;
                }
                if (instr instanceof FieldAccessInstruction) {
                    FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
                    if (fieldAccess.isStore() && fieldAccess.isStatic()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isConstructor(IRMethod method) {
        return "<init>".equals(method.getName());
    }
}
