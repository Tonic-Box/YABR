package com.tonic.analysis.dataflow;

import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for DataFlowNode class.
 * Tests node creation, builder pattern, getters, setters, taint tracking,
 * display methods, equality, and edge cases.
 */
class DataFlowNodeTest {

    @BeforeEach
    void setUp() {
        SSAValue.resetIdCounter();
        IRInstruction.resetIdCounter();
    }

    // ==================== Constructor and Builder Tests ====================

    @Nested
    class BuilderTests {

        @Test
        void builderCreatesNodeWithDefaultType() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            assertNotNull(node);
            assertEquals(DataFlowNodeType.LOCAL, node.getType());
        }

        @Test
        void builderCreatesNodeWithSpecificType() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.CONSTANT)
                    .build();

            assertEquals(DataFlowNodeType.CONSTANT, node.getType());
        }

        @Test
        void builderSetsId() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(42)
                    .build();

            assertEquals(42, node.getId());
        }

        @Test
        void builderSetsName() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .name("myVar")
                    .build();

            assertEquals("myVar", node.getName());
        }

        @Test
        void builderSetsDescription() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .description("Test description")
                    .build();

            assertEquals("Test description", node.getDescription());
        }

        @Test
        void builderSetsSsaValue() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "v0");
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .ssaValue(value)
                    .build();

            assertEquals(value, node.getSsaValue());
        }

        @Test
        void builderAutomaticallySetsNameFromSsaValue() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "v0");
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .ssaValue(value)
                    .build();

            assertEquals("v0", node.getName());
        }

        @Test
        void builderExplicitNameOverridesSsaValueName() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "v0");
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .name("customName")
                    .ssaValue(value)
                    .build();

            assertEquals("customName", node.getName());
        }

        @Test
        void builderSetsInstruction() {
            SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
            ConstantInstruction instr = new ConstantInstruction(v0, new IntConstant(42));

            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .instruction(instr)
                    .build();

            assertEquals(instr, node.getInstruction());
        }

        @Test
        void builderSetsLocation() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .location(5, 10)
                    .build();

            assertEquals(5, node.getBlockId());
            assertEquals(10, node.getInstructionIndex());
        }

        @Test
        void builderChainingWorksCorrectly() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "v0");
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.BINARY_OP)
                    .name("result")
                    .description("Addition result")
                    .ssaValue(value)
                    .location(3, 7)
                    .build();

            assertEquals(1, node.getId());
            assertEquals(DataFlowNodeType.BINARY_OP, node.getType());
            assertEquals("result", node.getName());
            assertEquals("Addition result", node.getDescription());
            assertEquals(value, node.getSsaValue());
            assertEquals(3, node.getBlockId());
            assertEquals(7, node.getInstructionIndex());
        }

        @Test
        void builderCreatesNewBuilderInstance() {
            DataFlowNode.Builder builder1 = DataFlowNode.builder();
            DataFlowNode.Builder builder2 = DataFlowNode.builder();

            assertNotSame(builder1, builder2);
        }
    }

    // ==================== Getter Tests ====================

    @Nested
    class GetterTests {

        @Test
        void getIdReturnsCorrectValue() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(123)
                    .build();

            assertEquals(123, node.getId());
        }

        @Test
        void getTypeReturnsCorrectValue() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.PHI)
                    .build();

            assertEquals(DataFlowNodeType.PHI, node.getType());
        }

        @Test
        void getNameReturnsCorrectValue() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .name("testName")
                    .build();

            assertEquals("testName", node.getName());
        }

        @Test
        void getDescriptionReturnsCorrectValue() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .description("Test description")
                    .build();

            assertEquals("Test description", node.getDescription());
        }

        @Test
        void getSsaValueReturnsNull() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            assertNull(node.getSsaValue());
        }

        @Test
        void getInstructionReturnsNull() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            assertNull(node.getInstruction());
        }

        @Test
        void getBlockIdReturnsZero() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            assertEquals(0, node.getBlockId());
        }

        @Test
        void getInstructionIndexReturnsZero() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            assertEquals(0, node.getInstructionIndex());
        }

        @Test
        void isTaintedReturnsFalseByDefault() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            assertFalse(node.isTainted());
        }

        @Test
        void getTaintSourceReturnsNullByDefault() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            assertNull(node.getTaintSource());
        }
    }

    // ==================== Taint Tracking Tests ====================

    @Nested
    class TaintTrackingTests {

        @Test
        void setTaintedToTrue() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            node.setTainted(true);

            assertTrue(node.isTainted());
        }

        @Test
        void setTaintedToFalse() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            node.setTainted(true);
            node.setTainted(false);

            assertFalse(node.isTainted());
        }

        @Test
        void setTaintSourceSetsSource() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            node.setTaintSource("user_input");

            assertEquals("user_input", node.getTaintSource());
        }

        @Test
        void setTaintSourceAutomaticallySetsTainted() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            node.setTaintSource("user_input");

            assertTrue(node.isTainted());
            assertEquals("user_input", node.getTaintSource());
        }

        @Test
        void setTaintSourceToNullClearsTaint() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            node.setTaintSource("user_input");
            node.setTaintSource(null);

            assertFalse(node.isTainted());
            assertNull(node.getTaintSource());
        }

        @Test
        void setTaintSourceOverwritesPreviousSource() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            node.setTaintSource("source1");
            node.setTaintSource("source2");

            assertEquals("source2", node.getTaintSource());
            assertTrue(node.isTainted());
        }
    }

    // ==================== Display Method Tests ====================

    @Nested
    class DisplayMethodTests {

        @Test
        void getLabelReturnsNameWhenSet() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .name("myVariable")
                    .type(DataFlowNodeType.LOCAL)
                    .build();

            assertEquals("myVariable", node.getLabel());
        }

        @Test
        void getLabelReturnsSsaValueNameWhenNoNameSet() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "v0");
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .ssaValue(value)
                    .build();

            assertEquals("v0", node.getLabel());
        }

        @Test
        void getLabelReturnsTypeAndIdWhenNoNameOrSsaValue() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(42)
                    .type(DataFlowNodeType.CONSTANT)
                    .build();

            assertEquals("Constant_42", node.getLabel());
        }

        @Test
        void getLabelIgnoresEmptyName() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "v0");
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .name("")
                    .ssaValue(value)
                    .build();

            assertEquals("v0", node.getLabel());
        }

        @Test
        void getLocationReturnsFormattedString() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .location(5, 10)
                    .build();

            assertEquals("block5:10", node.getLocation());
        }

        @Test
        void getLocationWithZeroValues() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .location(0, 0)
                    .build();

            assertEquals("block0:0", node.getLocation());
        }

        @Test
        void getTooltipWithMinimalInfo() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.LOCAL)
                    .location(0, 0)
                    .build();

            String tooltip = node.getTooltip();

            assertTrue(tooltip.contains("Local Variable"));
            assertTrue(tooltip.contains("Location: block0:0"));
        }

        @Test
        void getTooltipWithName() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .name("myVar")
                    .type(DataFlowNodeType.LOCAL)
                    .location(2, 5)
                    .build();

            String tooltip = node.getTooltip();

            assertTrue(tooltip.contains("Local Variable: myVar"));
            assertTrue(tooltip.contains("Location: block2:5"));
        }

        @Test
        void getTooltipWithSsaValueType() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "v0");
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .ssaValue(value)
                    .type(DataFlowNodeType.CONSTANT)
                    .location(1, 2)
                    .build();

            String tooltip = node.getTooltip();

            // PrimitiveType.INT toString() returns "I" (the descriptor)
            assertTrue(tooltip.contains("Type: I"));
        }

        @Test
        void getTooltipWithTaintedNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.LOCAL)
                    .location(0, 0)
                    .build();

            node.setTainted(true);
            String tooltip = node.getTooltip();

            assertTrue(tooltip.contains("⚠ TAINTED"));
        }

        @Test
        void getTooltipWithTaintSource() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.LOCAL)
                    .location(0, 0)
                    .build();

            node.setTaintSource("user_input");
            String tooltip = node.getTooltip();

            assertTrue(tooltip.contains("⚠ TAINTED"));
            assertTrue(tooltip.contains("(from user_input)"));
        }

        @Test
        void getTooltipCompleteExample() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "v0");
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .name("count")
                    .type(DataFlowNodeType.BINARY_OP)
                    .ssaValue(value)
                    .location(3, 7)
                    .build();

            node.setTaintSource("network");
            String tooltip = node.getTooltip();

            assertTrue(tooltip.contains("Binary Operation: count"));
            assertTrue(tooltip.contains("Location: block3:7"));
            // PrimitiveType.INT toString() returns "I" (the descriptor)
            assertTrue(tooltip.contains("Type: I"));
            assertTrue(tooltip.contains("⚠ TAINTED"));
            assertTrue(tooltip.contains("(from network)"));
        }
    }

    // ==================== Equality and HashCode Tests ====================

    @Nested
    class EqualityTests {

        @Test
        void equalsSameInstance() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            assertEquals(node, node);
        }

        @Test
        void equalsWithSameId() {
            DataFlowNode node1 = DataFlowNode.builder()
                    .id(1)
                    .name("name1")
                    .build();

            DataFlowNode node2 = DataFlowNode.builder()
                    .id(1)
                    .name("name2")
                    .build();

            assertEquals(node1, node2);
        }

        @Test
        void notEqualsWithDifferentId() {
            DataFlowNode node1 = DataFlowNode.builder()
                    .id(1)
                    .build();

            DataFlowNode node2 = DataFlowNode.builder()
                    .id(2)
                    .build();

            assertNotEquals(node1, node2);
        }

        @Test
        void notEqualsWithNull() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            assertNotEquals(null, node);
        }

        @Test
        void notEqualsWithDifferentClass() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            assertNotEquals(node, "string");
        }

        @Test
        void hashCodeConsistentWithEquals() {
            DataFlowNode node1 = DataFlowNode.builder()
                    .id(1)
                    .name("name1")
                    .build();

            DataFlowNode node2 = DataFlowNode.builder()
                    .id(1)
                    .name("name2")
                    .build();

            assertEquals(node1.hashCode(), node2.hashCode());
        }

        @Test
        void hashCodeDifferentForDifferentIds() {
            DataFlowNode node1 = DataFlowNode.builder()
                    .id(1)
                    .build();

            DataFlowNode node2 = DataFlowNode.builder()
                    .id(2)
                    .build();

            assertNotEquals(node1.hashCode(), node2.hashCode());
        }
    }

    // ==================== ToString Tests ====================

    @Nested
    class ToStringTests {

        @Test
        void toStringWithName() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .name("myVar")
                    .type(DataFlowNodeType.LOCAL)
                    .build();

            String str = node.toString();

            assertTrue(str.contains("myVar"));
            assertTrue(str.contains("LOCAL"));
        }

        @Test
        void toStringWithSsaValue() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "v0");
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .ssaValue(value)
                    .type(DataFlowNodeType.CONSTANT)
                    .build();

            String str = node.toString();

            assertTrue(str.contains("v0"));
            assertTrue(str.contains("CONSTANT"));
        }

        @Test
        void toStringWithNoNameOrSsaValue() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(42)
                    .type(DataFlowNodeType.PHI)
                    .build();

            String str = node.toString();

            assertTrue(str.contains("Phi_42"));
            assertTrue(str.contains("PHI"));
        }
    }

    // ==================== All Node Types Tests ====================

    @Nested
    class NodeTypeTests {

        @Test
        void createParamNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.PARAM)
                    .name("arg0")
                    .build();

            assertEquals(DataFlowNodeType.PARAM, node.getType());
            assertEquals("Parameter", node.getType().getDisplayName());
        }

        @Test
        void createConstantNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.CONSTANT)
                    .build();

            assertEquals(DataFlowNodeType.CONSTANT, node.getType());
        }

        @Test
        void createPhiNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.PHI)
                    .build();

            assertEquals(DataFlowNodeType.PHI, node.getType());
        }

        @Test
        void createInvokeResultNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.INVOKE_RESULT)
                    .build();

            assertEquals(DataFlowNodeType.INVOKE_RESULT, node.getType());
        }

        @Test
        void createFieldLoadNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.FIELD_LOAD)
                    .build();

            assertEquals(DataFlowNodeType.FIELD_LOAD, node.getType());
        }

        @Test
        void createArrayLoadNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.ARRAY_LOAD)
                    .build();

            assertEquals(DataFlowNodeType.ARRAY_LOAD, node.getType());
        }

        @Test
        void createBinaryOpNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.BINARY_OP)
                    .build();

            assertEquals(DataFlowNodeType.BINARY_OP, node.getType());
        }

        @Test
        void createUnaryOpNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.UNARY_OP)
                    .build();

            assertEquals(DataFlowNodeType.UNARY_OP, node.getType());
        }

        @Test
        void createCastNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.CAST)
                    .build();

            assertEquals(DataFlowNodeType.CAST, node.getType());
        }

        @Test
        void createNewObjectNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.NEW_OBJECT)
                    .build();

            assertEquals(DataFlowNodeType.NEW_OBJECT, node.getType());
        }

        @Test
        void createReturnNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.RETURN)
                    .build();

            assertEquals(DataFlowNodeType.RETURN, node.getType());
        }

        @Test
        void createFieldStoreNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.FIELD_STORE)
                    .build();

            assertEquals(DataFlowNodeType.FIELD_STORE, node.getType());
        }

        @Test
        void createArrayStoreNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.ARRAY_STORE)
                    .build();

            assertEquals(DataFlowNodeType.ARRAY_STORE, node.getType());
        }

        @Test
        void createInvokeArgNode() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.INVOKE_ARG)
                    .build();

            assertEquals(DataFlowNodeType.INVOKE_ARG, node.getType());
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    class IntegrationTests {

        @Test
        void nodeWithConstantInstruction() {
            SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
            ConstantInstruction instr = new ConstantInstruction(v0, new IntConstant(42));

            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.CONSTANT)
                    .ssaValue(v0)
                    .instruction(instr)
                    .location(0, 0)
                    .build();

            assertEquals(v0, node.getSsaValue());
            assertEquals(instr, node.getInstruction());
            assertEquals("v0", node.getName());
        }

        @Test
        void nodeWithBinaryOpInstruction() {
            SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
            SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
            SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");
            BinaryOpInstruction instr = new BinaryOpInstruction(v2, BinaryOp.ADD, v0, v1);

            DataFlowNode node = DataFlowNode.builder()
                    .id(2)
                    .type(DataFlowNodeType.BINARY_OP)
                    .ssaValue(v2)
                    .instruction(instr)
                    .location(1, 5)
                    .build();

            assertEquals(DataFlowNodeType.BINARY_OP, node.getType());
            assertEquals(v2, node.getSsaValue());
            assertEquals(1, node.getBlockId());
            assertEquals(5, node.getInstructionIndex());
        }

        @Test
        void nodeWithReferenceType() {
            ReferenceType stringType = new ReferenceType("java/lang/String");
            SSAValue v0 = new SSAValue(stringType, "str");

            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.NEW_OBJECT)
                    .ssaValue(v0)
                    .build();

            assertEquals(stringType, node.getSsaValue().getType());
            // ReferenceType toString() returns internal name, not descriptor
            assertTrue(node.getTooltip().contains("Type: java/lang/String"));
        }

        @Test
        void multipleNodesWithSameType() {
            DataFlowNode node1 = DataFlowNode.builder()
                    .id(1)
                    .type(DataFlowNodeType.CONSTANT)
                    .build();

            DataFlowNode node2 = DataFlowNode.builder()
                    .id(2)
                    .type(DataFlowNodeType.CONSTANT)
                    .build();

            DataFlowNode node3 = DataFlowNode.builder()
                    .id(3)
                    .type(DataFlowNodeType.CONSTANT)
                    .build();

            assertNotEquals(node1, node2);
            assertNotEquals(node2, node3);
            assertNotEquals(node1, node3);
        }

        @Test
        void nodeWithComplexLocation() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .location(100, 255)
                    .build();

            assertEquals("block100:255", node.getLocation());
        }
    }

    // ==================== Edge Cases and Boundary Tests ====================

    @Nested
    class EdgeCaseTests {

        @Test
        void nodeWithZeroId() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(0)
                    .build();

            assertEquals(0, node.getId());
        }

        @Test
        void nodeWithNegativeId() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(-1)
                    .build();

            assertEquals(-1, node.getId());
        }

        @Test
        void nodeWithLargeId() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(Integer.MAX_VALUE)
                    .build();

            assertEquals(Integer.MAX_VALUE, node.getId());
        }

        @Test
        void nodeWithNullName() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .name(null)
                    .build();

            assertNull(node.getName());
        }

        @Test
        void nodeWithNullDescription() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .description(null)
                    .build();

            assertNull(node.getDescription());
        }

        @Test
        void nodeWithNullSsaValue() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .ssaValue(null)
                    .build();

            assertNull(node.getSsaValue());
        }

        @Test
        void nodeWithNullInstruction() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .instruction(null)
                    .build();

            assertNull(node.getInstruction());
        }

        @Test
        void nodeWithEmptyString() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .name("")
                    .description("")
                    .build();

            assertEquals("", node.getName());
            assertEquals("", node.getDescription());
        }

        @Test
        void nodeWithVeryLongName() {
            String longName = "a".repeat(1000);
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .name(longName)
                    .build();

            assertEquals(longName, node.getName());
        }

        @Test
        void multipleSetTaintSource() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            node.setTaintSource("source1");
            node.setTaintSource("source2");
            node.setTaintSource("source3");

            assertEquals("source3", node.getTaintSource());
            assertTrue(node.isTainted());
        }

        @Test
        void taintSourceWithEmptyString() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .build();

            node.setTaintSource("");

            assertEquals("", node.getTaintSource());
            // Empty string is not null, so isTainted is true
            assertTrue(node.isTainted());
        }

        @Test
        void negativeBlockId() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .location(-1, 0)
                    .build();

            assertEquals(-1, node.getBlockId());
            assertEquals("block-1:0", node.getLocation());
        }

        @Test
        void negativeInstructionIndex() {
            DataFlowNode node = DataFlowNode.builder()
                    .id(1)
                    .location(0, -1)
                    .build();

            assertEquals(-1, node.getInstructionIndex());
            assertEquals("block0:-1", node.getLocation());
        }
    }
}
