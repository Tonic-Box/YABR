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
public final class ConstructorAnalyzer
{

    /**
     * Creates a stateless analyzer.
     */
    public ConstructorAnalyzer()
    {
    }

    /**
     * Builds the object for an allocation site with the fields the constructor assigns.
     * @param site the allocation site the object belongs to
     * @param constructor the constructor to read, may be null
     * @param constructorArgs the argument values bound to locals 1 and up
     * @param heap the heap the object will live in
     * @return the populated object, or a bare object if the method is null or not a constructor
     */
    public SimObject analyzeConstructor(AllocationSite site, IRMethod constructor, List<SimValue> constructorArgs, SimHeap heap)
    {
        if (constructor == null || !isConstructor(constructor))
        {
            return new SimObject(site);
        }

        Map<FieldKey, SimValue> assignments = extractFieldAssignments(constructor, constructorArgs);

        SimObject obj = new SimObject(site);
        for (Map.Entry<FieldKey, SimValue> entry : assignments.entrySet())
        {
            obj = obj.withField(entry.getKey(), entry.getValue());
        }

        return obj;
    }

    /**
     * Walks every block in order, tracking local stores, and records the last value written
     * to each instance field.
     * @param constructor the constructor to read
     * @param args the argument values bound to locals 1 and up
     * @return the assigned fields mapped to their values, in first-assignment order
     */
    public Map<FieldKey, SimValue> extractFieldAssignments(IRMethod constructor, List<SimValue> args)
    {
        Map<FieldKey, SimValue> assignments = new LinkedHashMap<>();

        List<IRBlock> blocks = constructor.getBlocks();
        if (blocks == null || blocks.isEmpty())
        {
            return assignments;
        }

        Map<Integer, SimValue> localBindings = new HashMap<>();
        localBindings.put(0, SimValue.ofType(IRType.fromDescriptor("L" + constructor.getOwnerClass() + ";"), null));
        for (int i = 0; i < args.size(); i++)
        {
            localBindings.put(i + 1, args.get(i));
        }

        for (IRBlock block : blocks)
        {
            for (IRInstruction instr : block.getInstructions())
            {
                if (instr instanceof FieldAccessInstruction)
                {
                    FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
                    if (fieldAccess.isStore() && !fieldAccess.isStatic())
                    {
                        FieldKey fieldKey = FieldKey.of(
                            fieldAccess.getOwner(),
                            fieldAccess.getName(),
                            fieldAccess.getDescriptor()
                        );
                        SimValue value = resolveValue(fieldAccess.getValue(), localBindings, instr);
                        assignments.put(fieldKey, value);
                    }
                }
                else if (instr instanceof StoreLocalInstruction)
                {
                    StoreLocalInstruction store = (StoreLocalInstruction) instr;
                    SimValue value = resolveValue(store.getValue(), localBindings, instr);
                    localBindings.put(store.getLocalIndex(), value);
                }
            }
        }

        return assignments;
    }

    private SimValue resolveValue(Value value, Map<Integer, SimValue> locals, IRInstruction instr)
    {
        if (value == null)
        {
            return SimValue.unknown(instr);
        }

        if (value instanceof Constant)
        {
            Constant constant = (Constant) value;
            return SimValue.constant(constant.getValue(), value.getType(), instr);
        }

        return SimValue.ofType(value.getType(), instr);
    }

    /**
     * Lists the instance fields a constructor writes, ignoring the values written.
     * @param constructor the constructor to read
     * @return the assigned field keys
     */
    public Set<FieldKey> getAssignedFields(IRMethod constructor)
    {
        Map<FieldKey, SimValue> assignments = extractFieldAssignments(constructor, Collections.emptyList());
        return assignments.keySet();
    }

    /**
     * Tests whether a constructor writes a given instance field.
     * @param constructor the constructor to read
     * @param field the field to look for
     * @return whether the field is assigned
     */
    public boolean assignsField(IRMethod constructor, FieldKey field)
    {
        Set<FieldKey> assigned = getAssignedFields(constructor);
        return assigned.contains(field);
    }

    /**
     * Conservatively reports whether the object under construction may escape, treating any
     * invocation or static field store as an escape.
     * @param constructor the constructor to read
     * @return whether an escape is possible
     */
    public boolean hasThisEscape(IRMethod constructor)
    {
        List<IRBlock> blocks = constructor.getBlocks();
        if (blocks == null || blocks.isEmpty())
        {
            return false;
        }

        for (IRBlock block : blocks)
        {
            for (IRInstruction instr : block.getInstructions())
            {
                if (instr instanceof InvokeInstruction)
                {
                    return true;
                }
                if (instr instanceof FieldAccessInstruction)
                {
                    FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
                    if (fieldAccess.isStore() && fieldAccess.isStatic())
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isConstructor(IRMethod method)
    {
        return "<init>".equals(method.getName());
    }
}
