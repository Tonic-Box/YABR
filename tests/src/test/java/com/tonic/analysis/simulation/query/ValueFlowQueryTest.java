package com.tonic.analysis.simulation.query;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.core.StateSnapshot;
import com.tonic.analysis.simulation.state.LocalState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.simulation.state.StackState;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.ConstantInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ValueFlowQuery.
 *
 * Tests value flow analysis queries on simulation results including:
 * - Factory method creation
 * - Definition tracking
 * - Use/dependency tracking
 * - Flow analysis with BFS
 * - Path reconstruction
 * - Position queries
 * - Constant extraction
 * - Edge cases
 */
class ValueFlowQueryTest {

    private IRBlock testBlock;
    private IRInstruction instr1;
    private IRInstruction instr2;
    private IRInstruction instr3;

    @BeforeEach
    void setUp() {
        // Create real IR elements
        testBlock = new IRBlock("test_block");

        // Create simple constant instructions
        SSAValue result1 = new SSAValue(PrimitiveType.INT);
        SSAValue result2 = new SSAValue(PrimitiveType.INT);
        SSAValue result3 = new SSAValue(PrimitiveType.INT);

        instr1 = new ConstantInstruction(result1, new IntConstant(42));
        instr2 = new ConstantInstruction(result2, new IntConstant(100));
        instr3 = new ConstantInstruction(result3, new IntConstant(200));

        // Add instructions to block
        testBlock.addInstruction(instr1);
        testBlock.addInstruction(instr2);
        testBlock.addInstruction(instr3);
    }

    // ========== Factory Method Tests ==========

    @Test
    void testFrom_shouldCreateQueryObject() {
        SimulationResult result = createEmptyResult();

        ValueFlowQuery query = ValueFlowQuery.from(result);

        assertNotNull(query, "Factory method should create query object");
        assertEquals(0, query.getValueCount(), "Empty result should have no tracked values");
    }

    @Test
    void testFrom_shouldBuildFlowGraph() {
        SimValue value1 = SimValue.constant(42, PrimitiveType.INT, instr1);
        SimValue value2 = SimValue.ofType(PrimitiveType.INT, instr2);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value1),
            createStateWithStack(1, value2)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);

        assertEquals(2, query.getValueCount(), "Should track values from snapshots");
    }

    // ========== Definition Tracking Tests ==========

    @Test
    void testGetDefiningInstruction_shouldReturnInstructionThatProducedValue() {
        SimValue value = SimValue.constant(100, PrimitiveType.INT, instr1);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        IRInstruction definingInstr = query.getDefiningInstruction(value);

        assertEquals(instr1, definingInstr,
            "Should return instruction that produced the value");
    }

    @Test
    void testGetDefiningInstruction_shouldFallbackToValueSourceInstruction() {
        // Create a value that won't be in the definitions map
        SimValue orphanValue = SimValue.constant(99, PrimitiveType.INT, instr2);

        SimulationResult result = createEmptyResult();
        ValueFlowQuery query = ValueFlowQuery.from(result);

        IRInstruction definingInstr = query.getDefiningInstruction(orphanValue);

        assertEquals(instr2, definingInstr,
            "Should fall back to value's source instruction");
    }

    @Test
    void testGetDefiningInstruction_withNullValue_shouldReturnNull() {
        SimulationResult result = createEmptyResult();
        ValueFlowQuery query = ValueFlowQuery.from(result);

        IRInstruction definingInstr = query.getDefiningInstruction(null);

        assertNull(definingInstr, "Should return null for null value");
    }

    @Test
    void testGetDefiningInstruction_withValueWithoutSource_shouldReturnNull() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

        SimulationResult result = createEmptyResult();
        ValueFlowQuery query = ValueFlowQuery.from(result);

        IRInstruction definingInstr = query.getDefiningInstruction(value);

        assertNull(definingInstr, "Should return null when value has no source");
    }

    // ========== Use Tracking Tests ==========

    @Test
    void testGetUses_withNoUses_shouldReturnEmptyList() {
        SimValue value = SimValue.constant(10, PrimitiveType.INT, instr1);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<IRInstruction> uses = query.getUses(value);

        assertNotNull(uses, "Should return non-null list");
        assertTrue(uses.isEmpty(), "Should return empty list when no uses tracked");
    }

    @Test
    void testGetUses_withUnknownValue_shouldReturnEmptyList() {
        SimValue value = SimValue.unknown(null);

        SimulationResult result = createEmptyResult();
        ValueFlowQuery query = ValueFlowQuery.from(result);

        List<IRInstruction> uses = query.getUses(value);

        assertNotNull(uses, "Should return non-null list");
        assertTrue(uses.isEmpty(), "Should return empty list for unknown value");
    }

    // ========== Dependency Tracking Tests ==========

    @Test
    void testGetDependencies_withNoDependencies_shouldReturnEmptySet() {
        SimValue value = SimValue.constant(5, PrimitiveType.INT, instr1);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        Set<SimValue> deps = query.getDependencies(value);

        assertNotNull(deps, "Should return non-null set");
        assertTrue(deps.isEmpty(), "Should return empty set when no dependencies");
    }

    @Test
    void testGetDependencies_withUnknownValue_shouldReturnEmptySet() {
        SimValue value = SimValue.unknown(null);

        SimulationResult result = createEmptyResult();
        ValueFlowQuery query = ValueFlowQuery.from(result);

        Set<SimValue> deps = query.getDependencies(value);

        assertNotNull(deps, "Should return non-null set");
        assertTrue(deps.isEmpty(), "Should return empty set for unknown value");
    }

    @Test
    void testGetDependents_withNoDependents_shouldReturnEmptySet() {
        SimValue value = SimValue.constant(7, PrimitiveType.INT, instr1);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        Set<SimValue> dependents = query.getDependents(value);

        assertNotNull(dependents, "Should return non-null set");
        assertTrue(dependents.isEmpty(), "Should return empty set when no dependents");
    }

    // ========== Flow Analysis Tests ==========

    @Test
    void testFlowsTo_withSameValue_shouldReturnTrue() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, instr1);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        boolean flows = query.flowsTo(value, value);

        assertTrue(flows, "Value should flow to itself");
    }

    @Test
    void testFlowsTo_withNullSource_shouldReturnFalse() {
        SimValue target = SimValue.constant(10, PrimitiveType.INT, instr1);

        SimulationResult result = createEmptyResult();
        ValueFlowQuery query = ValueFlowQuery.from(result);

        boolean flows = query.flowsTo(null, target);

        assertFalse(flows, "Null source should not flow to any target");
    }

    @Test
    void testFlowsTo_withNullTarget_shouldReturnFalse() {
        SimValue source = SimValue.constant(10, PrimitiveType.INT, instr1);

        SimulationResult result = createEmptyResult();
        ValueFlowQuery query = ValueFlowQuery.from(result);

        boolean flows = query.flowsTo(source, null);

        assertFalse(flows, "Source should not flow to null target");
    }

    @Test
    void testFlowsTo_withBothNull_shouldReturnFalse() {
        SimulationResult result = createEmptyResult();
        ValueFlowQuery query = ValueFlowQuery.from(result);

        boolean flows = query.flowsTo(null, null);

        assertFalse(flows, "Null should not flow to null");
    }

    @Test
    void testFlowsTo_withNoConnection_shouldReturnFalse() {
        SimValue value1 = SimValue.constant(1, PrimitiveType.INT, instr1);
        SimValue value2 = SimValue.constant(2, PrimitiveType.INT, instr2);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value1),
            createStateWithStack(1, value2)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        boolean flows = query.flowsTo(value1, value2);

        assertFalse(flows, "Unconnected values should not flow to each other");
    }

    // ========== Path Reconstruction Tests ==========

    @Test
    void testGetFlowPath_withSameValue_shouldReturnSingletonList() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, instr1);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> path = query.getFlowPath(value, value);

        assertEquals(1, path.size(), "Path from value to itself should have one element");
        assertEquals(value, path.get(0), "Path should contain the value");
    }

    @Test
    void testGetFlowPath_withNullSource_shouldReturnEmptyList() {
        SimValue target = SimValue.constant(10, PrimitiveType.INT, instr1);

        SimulationResult result = createEmptyResult();
        ValueFlowQuery query = ValueFlowQuery.from(result);

        List<SimValue> path = query.getFlowPath(null, target);

        assertTrue(path.isEmpty(), "Path with null source should be empty");
    }

    @Test
    void testGetFlowPath_withNullTarget_shouldReturnEmptyList() {
        SimValue source = SimValue.constant(10, PrimitiveType.INT, instr1);

        SimulationResult result = createEmptyResult();
        ValueFlowQuery query = ValueFlowQuery.from(result);

        List<SimValue> path = query.getFlowPath(source, null);

        assertTrue(path.isEmpty(), "Path with null target should be empty");
    }

    @Test
    void testGetFlowPath_withNoConnection_shouldReturnEmptyList() {
        SimValue value1 = SimValue.constant(1, PrimitiveType.INT, instr1);
        SimValue value2 = SimValue.constant(2, PrimitiveType.INT, instr2);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value1),
            createStateWithStack(1, value2)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> path = query.getFlowPath(value1, value2);

        assertTrue(path.isEmpty(), "Path should be empty when no connection exists");
    }

    // ========== Position Query Tests ==========

    @Test
    void testGetValuesAtStackPosition_withMultipleStates_shouldReturnUniqueValues() {
        SimValue value1 = SimValue.constant(10, PrimitiveType.INT, instr1);
        SimValue value2 = SimValue.constant(20, PrimitiveType.INT, instr2);

        // Both values at stack position 0 (top) in different states
        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value1),
            createStateWithStack(0, value2)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> values = query.getValuesAtStackPosition(0);

        assertEquals(2, values.size(), "Should find both unique values at position 0");
        assertTrue(values.contains(value1), "Should contain first value");
        assertTrue(values.contains(value2), "Should contain second value");
    }

    @Test
    void testGetValuesAtStackPosition_withSameValueMultipleTimes_shouldReturnOnce() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, instr1);

        // Same value appears in multiple states
        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value),
            createStateWithStack(0, value),
            createStateWithStack(0, value)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> values = query.getValuesAtStackPosition(0);

        assertEquals(1, values.size(), "Should deduplicate same value");
        assertEquals(value, values.get(0), "Should contain the value");
    }

    @Test
    void testGetValuesAtStackPosition_withInvalidPosition_shouldReturnEmptyList() {
        SimValue value = SimValue.constant(5, PrimitiveType.INT, instr1);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> values = query.getValuesAtStackPosition(10); // Invalid position

        assertTrue(values.isEmpty(), "Should return empty list for invalid position");
    }

    @Test
    void testGetValuesAtStackPosition_withEmptyResult_shouldReturnEmptyList() {
        SimulationResult result = createEmptyResult();

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> values = query.getValuesAtStackPosition(0);

        assertTrue(values.isEmpty(), "Should return empty list for empty result");
    }

    @Test
    void testGetValuesInLocal_withMultipleStates_shouldReturnUniqueValues() {
        SimValue value1 = SimValue.constant(100, PrimitiveType.INT, instr1);
        SimValue value2 = SimValue.constant(200, PrimitiveType.INT, instr2);

        SimulationResult result = createResultWithValues(
            createStateWithLocal(0, value1),
            createStateWithLocal(0, value2)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> values = query.getValuesInLocal(0);

        assertEquals(2, values.size(), "Should find both unique values in local 0");
        assertTrue(values.contains(value1), "Should contain first value");
        assertTrue(values.contains(value2), "Should contain second value");
    }

    @Test
    void testGetValuesInLocal_withSameValueMultipleTimes_shouldReturnOnce() {
        SimValue value = SimValue.constant(77, PrimitiveType.INT, instr1);

        SimulationResult result = createResultWithValues(
            createStateWithLocal(1, value),
            createStateWithLocal(1, value)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> values = query.getValuesInLocal(1);

        assertEquals(1, values.size(), "Should deduplicate same value");
        assertEquals(value, values.get(0), "Should contain the value");
    }

    @Test
    void testGetValuesInLocal_withUndefinedLocal_shouldReturnEmptyList() {
        SimValue value = SimValue.constant(5, PrimitiveType.INT, instr1);

        SimulationResult result = createResultWithValues(
            createStateWithLocal(0, value)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> values = query.getValuesInLocal(5); // Undefined local

        assertTrue(values.isEmpty(), "Should return empty list for undefined local");
    }

    // ========== Constant Extraction Tests ==========

    @Test
    void testGetConstants_shouldReturnAllConstantValues() {
        SimValue const1 = SimValue.constant(10, PrimitiveType.INT, instr1);
        SimValue const2 = SimValue.constant(20, PrimitiveType.INT, instr2);
        SimValue nonConst = SimValue.ofType(PrimitiveType.INT, instr3);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, const1),
            createStateWithStack(0, const2),
            createStateWithStack(0, nonConst)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> constants = query.getConstants();

        assertEquals(2, constants.size(), "Should find only constant values");
        assertTrue(constants.contains(const1), "Should contain first constant");
        assertTrue(constants.contains(const2), "Should contain second constant");
        assertFalse(constants.contains(nonConst), "Should not contain non-constant");
    }

    @Test
    void testGetConstants_withDuplicates_shouldReturnUnique() {
        SimValue constant = SimValue.constant(42, PrimitiveType.INT, instr1);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, constant),
            createStateWithLocal(0, constant),
            createStateWithStack(0, constant)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> constants = query.getConstants();

        assertEquals(1, constants.size(), "Should deduplicate constants");
        assertEquals(constant, constants.get(0), "Should contain the constant");
    }

    @Test
    void testGetConstants_withNoConstants_shouldReturnEmptyList() {
        SimValue value1 = SimValue.ofType(PrimitiveType.INT, instr1);
        SimValue value2 = SimValue.unknown(instr2);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value1),
            createStateWithStack(0, value2)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> constants = query.getConstants();

        assertTrue(constants.isEmpty(), "Should return empty list when no constants");
    }

    @Test
    void testGetConstants_withEmptyResult_shouldReturnEmptyList() {
        SimulationResult result = createEmptyResult();

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> constants = query.getConstants();

        assertTrue(constants.isEmpty(), "Should return empty list for empty result");
    }

    @Test
    void testGetConstants_fromLocals_shouldIncludeLocalConstants() {
        SimValue stackConst = SimValue.constant(10, PrimitiveType.INT, instr1);
        SimValue localConst = SimValue.constant(20, PrimitiveType.INT, instr2);

        SimulationResult result = createResultWithValues(
            createStateWithStackAndLocal(0, stackConst, 0, localConst)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        List<SimValue> constants = query.getConstants();

        assertEquals(2, constants.size(), "Should find constants from both stack and locals");
        assertTrue(constants.contains(stackConst), "Should contain stack constant");
        assertTrue(constants.contains(localConst), "Should contain local constant");
    }

    // ========== Edge Case Tests ==========

    @Test
    void testEmptySimulationResult() {
        SimulationResult result = createEmptyResult();

        ValueFlowQuery query = ValueFlowQuery.from(result);

        assertEquals(0, query.getValueCount(), "Empty result should have no values");
        assertTrue(query.getConstants().isEmpty(), "Should have no constants");
        assertTrue(query.getValuesAtStackPosition(0).isEmpty(), "Should have no stack values");
        assertTrue(query.getValuesInLocal(0).isEmpty(), "Should have no local values");
    }

    @Test
    void testToString() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, instr1);
        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);
        String str = query.toString();

        assertNotNull(str, "toString should not return null");
        assertTrue(str.contains("ValueFlowQuery"), "toString should contain class name");
        assertTrue(str.contains("values="), "toString should contain value count label");
    }

    @Test
    void testGetValueCount() {
        SimValue value1 = SimValue.constant(1, PrimitiveType.INT, instr1);
        SimValue value2 = SimValue.constant(2, PrimitiveType.INT, instr2);
        SimValue value3 = SimValue.constant(3, PrimitiveType.INT, instr3);

        SimulationResult result = createResultWithValues(
            createStateWithStack(0, value1),
            createStateWithStack(1, value2),
            createStateWithStack(2, value3)
        );

        ValueFlowQuery query = ValueFlowQuery.from(result);

        assertEquals(3, query.getValueCount(), "Should count all tracked values");
    }

    @Test
    void testSnapshotWithNullBlock_shouldNotCrash() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, instr1);

        // Create state without block
        SimulationState state = SimulationState.of(
            StackState.empty().push(value),
            LocalState.empty()
        );

        SimulationResult result = SimulationResult.builder()
            .addState(state.snapshot())
            .build();

        ValueFlowQuery query = ValueFlowQuery.from(result);

        // Should not crash, but won't track values without block context
        assertNotNull(query, "Query should be created even with null blocks");
    }

    // ========== Helper Methods ==========

    /**
     * Creates an empty SimulationResult.
     */
    private SimulationResult createEmptyResult() {
        return SimulationResult.builder().build();
    }

    /**
     * Creates a SimulationResult with the given state snapshots.
     */
    private SimulationResult createResultWithValues(StateSnapshot... snapshots) {
        SimulationResult.Builder builder = SimulationResult.builder();
        for (StateSnapshot snapshot : snapshots) {
            builder.addState(snapshot);
        }
        return builder.build();
    }

    /**
     * Creates a StateSnapshot with a value at the given stack position.
     */
    private StateSnapshot createStateWithStack(int instrIndex, SimValue value) {
        SimulationState state = SimulationState.of(
            StackState.empty().push(value),
            LocalState.empty()
        ).atBlock(testBlock).atInstruction(instrIndex);

        return state.snapshot();
    }

    /**
     * Creates a StateSnapshot with a value in a local variable.
     */
    private StateSnapshot createStateWithLocal(int localIndex, SimValue value) {
        SimulationState state = SimulationState.of(
            StackState.empty(),
            LocalState.empty().set(localIndex, value)
        ).atBlock(testBlock).atInstruction(0);

        return state.snapshot();
    }

    /**
     * Creates a StateSnapshot with values in both stack and local.
     */
    private StateSnapshot createStateWithStackAndLocal(int instrIndex, SimValue stackValue,
                                                       int localIndex, SimValue localValue) {
        SimulationState state = SimulationState.of(
            StackState.empty().push(stackValue),
            LocalState.empty().set(localIndex, localValue)
        ).atBlock(testBlock).atInstruction(instrIndex);

        return state.snapshot();
    }
}
