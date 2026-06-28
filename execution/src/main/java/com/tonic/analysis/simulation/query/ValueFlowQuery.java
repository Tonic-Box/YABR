package com.tonic.analysis.simulation.query;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.StateSnapshot;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Query interface for value flow analysis.
 *
 * <p>Provides methods to trace where values come from and where they go.
 *
 * <p>Example usage:
 * <pre>
 * ValueFlowQuery query = ValueFlowQuery.from(result);
 *
 * // Find where a value comes from
 * IRInstruction origin = query.getDefiningInstruction(value);
 *
 * // Find where a value is used
 * List&lt;IRInstruction&gt; uses = query.getUses(value);
 *
 * // Check if value flows to another
 * boolean flows = query.flowsTo(source, target);
 * </pre>
 */
public class ValueFlowQuery {

    private final SimulationResult result;
    private final Map<SimValue, IRInstruction> definitions;
    private final Map<SimValue, List<IRInstruction>> uses;
    private final Map<SimValue, Set<SimValue>> dependencies;

    private ValueFlowQuery(SimulationResult result) {
        this.result = result;
        this.definitions = new HashMap<>();
        this.uses = new HashMap<>();
        this.dependencies = new HashMap<>();
        buildFlowGraph();
    }

    /**
     * Creates a value flow query from a simulation result.
     */
    public static ValueFlowQuery from(SimulationResult result) {
        return new ValueFlowQuery(result);
    }

    private void buildFlowGraph() {
        // Reverse maps used to bridge the IR-level def-use (instruction operands) to SimValues:
        // which SimValue each instruction produced, and which instruction defines each SSA value.
        Map<IRInstruction, SimValue> producedBy = new HashMap<>();
        Map<Value, IRInstruction> ssaDef = new HashMap<>();

        for (StateSnapshot snapshot : result.getAllStates()) {
            indexSsaDefs(snapshot, ssaDef);

            IRInstruction instr = getCurrentInstruction(snapshot);
            if (instr == null) continue;

            for (SimValue value : valuesIn(snapshot)) {
                if (value != null && value.getSourceInstruction() == instr) {
                    definitions.put(value, instr);
                    producedBy.putIfAbsent(instr, value);
                }
            }
        }

        // An instruction's IR operands resolve (through their producing instruction) to the SimValues
        // it consumes: each produced value depends on those inputs, and each input is used by the
        // consuming instruction.
        for (Map.Entry<IRInstruction, SimValue> produced : producedBy.entrySet()) {
            for (Value operand : produced.getKey().getOperands()) {
                IRInstruction def = ssaDef.get(operand);
                SimValue input = def == null ? null : producedBy.get(def);
                if (input == null || input == produced.getValue()) {
                    continue;
                }
                dependencies.computeIfAbsent(produced.getValue(), k -> new HashSet<>()).add(input);
                uses.computeIfAbsent(input, k -> new ArrayList<>()).add(produced.getKey());
            }
        }
    }

    private List<SimValue> valuesIn(StateSnapshot snapshot) {
        List<SimValue> values = new ArrayList<>(snapshot.getStackValues());
        values.addAll(snapshot.getLocalValues().values());
        return values;
    }

    private void indexSsaDefs(StateSnapshot snapshot, Map<Value, IRInstruction> ssaDef) {
        if (snapshot.getBlock() == null) {
            return;
        }
        for (IRInstruction instr : snapshot.getBlock().getInstructions()) {
            if (instr.getResult() != null) {
                ssaDef.putIfAbsent(instr.getResult(), instr);
            }
        }
    }

    private IRInstruction getCurrentInstruction(StateSnapshot snapshot) {
        if (snapshot.getBlock() == null) return null;
        var instructions = snapshot.getBlock().getInstructions();
        int index = snapshot.getInstructionIndex();
        if (index >= 0 && index < instructions.size()) {
            return instructions.get(index);
        }
        return null;
    }

    /**
     * Gets the instruction that defined/produced a value.
     */
    public IRInstruction getDefiningInstruction(SimValue value) {
        if (value == null) return null;
        IRInstruction def = definitions.get(value);
        if (def != null) return def;
        // Fall back to the source instruction stored in the value
        return value.getSourceInstruction();
    }

    /**
     * Gets the instructions that use a value.
     */
    public List<IRInstruction> getUses(SimValue value) {
        return uses.getOrDefault(value, Collections.emptyList());
    }

    /**
     * Gets the values that a value depends on (its inputs).
     */
    public Set<SimValue> getDependencies(SimValue value) {
        return dependencies.getOrDefault(value, Collections.emptySet());
    }

    /**
     * Checks if a source value flows to a target value.
     */
    public boolean flowsTo(SimValue source, SimValue target) {
        if (source == null || target == null) return false;
        if (source.equals(target)) return true;

        // BFS to find if there's a path
        Set<SimValue> visited = new HashSet<>();
        Queue<SimValue> worklist = new LinkedList<>();
        worklist.add(source);

        while (!worklist.isEmpty()) {
            SimValue current = worklist.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            // Check if current reaches target through dependencies
            Set<SimValue> deps = getDependents(current);
            if (deps.contains(target)) return true;
            worklist.addAll(deps);
        }

        return false;
    }

    /**
     * Gets values that depend on a value (its outputs).
     */
    public Set<SimValue> getDependents(SimValue value) {
        Set<SimValue> result = new HashSet<>();
        for (Map.Entry<SimValue, Set<SimValue>> entry : dependencies.entrySet()) {
            if (entry.getValue().contains(value)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Gets the flow path between two values.
     */
    public List<SimValue> getFlowPath(SimValue source, SimValue target) {
        if (source == null || target == null) return Collections.emptyList();
        if (source.equals(target)) return List.of(source);

        // BFS to find shortest path
        Map<SimValue, SimValue> parent = new HashMap<>();
        Set<SimValue> visited = new HashSet<>();
        Queue<SimValue> worklist = new LinkedList<>();
        worklist.add(source);
        parent.put(source, null);

        while (!worklist.isEmpty()) {
            SimValue current = worklist.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.equals(target)) {
                // Reconstruct path
                List<SimValue> path = new ArrayList<>();
                SimValue node = target;
                while (node != null) {
                    path.add(0, node);
                    node = parent.get(node);
                }
                return path;
            }

            for (SimValue dep : getDependents(current)) {
                if (!visited.contains(dep) && !parent.containsKey(dep)) {
                    parent.put(dep, current);
                    worklist.add(dep);
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Gets all values at a specific stack position across all states.
     */
    public List<SimValue> getValuesAtStackPosition(int position) {
        List<SimValue> values = new ArrayList<>();
        for (StateSnapshot snapshot : result.getAllStates()) {
            SimValue value = snapshot.getStackValue(position);
            if (value != null && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * Gets all values in a specific local variable across all states.
     */
    public List<SimValue> getValuesInLocal(int localIndex) {
        List<SimValue> values = new ArrayList<>();
        for (StateSnapshot snapshot : result.getAllStates()) {
            SimValue value = snapshot.getLocalValue(localIndex);
            if (value != null && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * Gets all constant values observed.
     */
    public List<SimValue> getConstants() {
        List<SimValue> constants = new ArrayList<>();
        for (StateSnapshot snapshot : result.getAllStates()) {
            for (SimValue value : snapshot.getStackValues()) {
                if (value != null && value.isConstant() && !constants.contains(value)) {
                    constants.add(value);
                }
            }
            for (SimValue value : snapshot.getLocalValues().values()) {
                if (value != null && value.isConstant() && !constants.contains(value)) {
                    constants.add(value);
                }
            }
        }
        return constants;
    }

    /**
     * Gets the number of values tracked.
     */
    public int getValueCount() {
        return definitions.size();
    }

    @Override
    public String toString() {
        return "ValueFlowQuery[values=" + definitions.size() + "]";
    }
}
