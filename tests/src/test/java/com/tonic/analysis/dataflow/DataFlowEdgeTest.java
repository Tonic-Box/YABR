package com.tonic.analysis.dataflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for DataFlowEdge class.
 * Tests edge creation, properties, edge types, equality, and display methods.
 * Organized using @Nested classes for clarity.
 */
class DataFlowEdgeTest {

    private DataFlowNode sourceNode;
    private DataFlowNode targetNode;

    @BeforeEach
    void setUp() {
        sourceNode = DataFlowNode.builder()
            .id(1)
            .type(DataFlowNodeType.CONSTANT)
            .name("source")
            .location(0, 0)
            .build();

        targetNode = DataFlowNode.builder()
            .id(2)
            .type(DataFlowNodeType.LOCAL)
            .name("target")
            .location(0, 1)
            .build();
    }

    // ==================== Constructor Tests ====================

    @Nested
    class ConstructorTests {

        @Test
        void constructorWithThreeArgsSetsSourceTargetType() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertEquals(sourceNode, edge.getSource());
            assertEquals(targetNode, edge.getTarget());
            assertEquals(DataFlowEdgeType.DEF_USE, edge.getType());
        }

        @Test
        void constructorWithThreeArgsSetsNullLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertNull(edge.getLabel());
        }

        @Test
        void constructorWithFourArgsSetsSourceTargetTypeLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.CALL_ARG, "arg0");

            assertEquals(sourceNode, edge.getSource());
            assertEquals(targetNode, edge.getTarget());
            assertEquals(DataFlowEdgeType.CALL_ARG, edge.getType());
            assertEquals("arg0", edge.getLabel());
        }

        @Test
        void constructorWithNullLabelAccepted() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.DEF_USE, null);

            assertNull(edge.getLabel());
        }

        @Test
        void constructorWithEmptyLabelAccepted() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.DEF_USE, "");

            assertEquals("", edge.getLabel());
        }
    }

    // ==================== Getter Tests ====================

    @Nested
    class GetterTests {

        @Test
        void getSourceReturnsCorrectNode() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertSame(sourceNode, edge.getSource());
        }

        @Test
        void getTargetReturnsCorrectNode() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertSame(targetNode, edge.getTarget());
        }

        @Test
        void getTypeReturnsCorrectType() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.PHI_INPUT);

            assertEquals(DataFlowEdgeType.PHI_INPUT, edge.getType());
        }

        @Test
        void getLabelReturnsNullWhenNotSet() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertNull(edge.getLabel());
        }

        @Test
        void getLabelReturnsCorrectLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.FIELD_STORE, "myField");

            assertEquals("myField", edge.getLabel());
        }
    }

    // ==================== Edge Type Tests ====================

    @Nested
    class EdgeTypeTests {

        @Test
        void defUseEdgeType() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertEquals(DataFlowEdgeType.DEF_USE, edge.getType());
        }

        @Test
        void phiInputEdgeType() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.PHI_INPUT);

            assertEquals(DataFlowEdgeType.PHI_INPUT, edge.getType());
        }

        @Test
        void callArgEdgeType() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.CALL_ARG);

            assertEquals(DataFlowEdgeType.CALL_ARG, edge.getType());
        }

        @Test
        void callReturnEdgeType() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.CALL_RETURN);

            assertEquals(DataFlowEdgeType.CALL_RETURN, edge.getType());
        }

        @Test
        void fieldStoreEdgeType() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.FIELD_STORE);

            assertEquals(DataFlowEdgeType.FIELD_STORE, edge.getType());
        }

        @Test
        void fieldLoadEdgeType() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.FIELD_LOAD);

            assertEquals(DataFlowEdgeType.FIELD_LOAD, edge.getType());
        }

        @Test
        void arrayStoreEdgeType() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.ARRAY_STORE);

            assertEquals(DataFlowEdgeType.ARRAY_STORE, edge.getType());
        }

        @Test
        void arrayLoadEdgeType() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.ARRAY_LOAD);

            assertEquals(DataFlowEdgeType.ARRAY_LOAD, edge.getType());
        }

        @Test
        void operandEdgeType() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.OPERAND);

            assertEquals(DataFlowEdgeType.OPERAND, edge.getType());
        }
    }

    // ==================== Display Label Tests ====================

    @Nested
    class DisplayLabelTests {

        @Test
        void getDisplayLabelReturnsCustomLabelWhenSet() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.CALL_ARG, "parameter1");

            assertEquals("parameter1", edge.getDisplayLabel());
        }

        @Test
        void getDisplayLabelReturnsTypeDisplayNameWhenLabelNull() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertEquals(DataFlowEdgeType.DEF_USE.getDisplayName(), edge.getDisplayLabel());
        }

        @Test
        void getDisplayLabelReturnsTypeDisplayNameWhenLabelEmpty() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.DEF_USE, "");

            assertEquals(DataFlowEdgeType.DEF_USE.getDisplayName(), edge.getDisplayLabel());
        }

        @Test
        void getDisplayLabelHandlesWhitespaceOnlyLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.FIELD_LOAD, "   ");

            // Whitespace is not considered empty by isEmpty()
            assertEquals("   ", edge.getDisplayLabel());
        }
    }

    // ==================== Tooltip Tests ====================

    @Nested
    class TooltipTests {

        @Test
        void getTooltipIncludesTypeDisplayName() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            String tooltip = edge.getTooltip();

            assertTrue(tooltip.contains(DataFlowEdgeType.DEF_USE.getDisplayName()));
        }

        @Test
        void getTooltipIncludesSourceLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            String tooltip = edge.getTooltip();

            assertTrue(tooltip.contains(sourceNode.getLabel()));
        }

        @Test
        void getTooltipIncludesTargetLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            String tooltip = edge.getTooltip();

            assertTrue(tooltip.contains(targetNode.getLabel()));
        }

        @Test
        void getTooltipIncludesArrowSymbol() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            String tooltip = edge.getTooltip();

            assertTrue(tooltip.contains("→"));
        }

        @Test
        void getTooltipIncludesLabelWhenSet() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.CALL_ARG, "myLabel");
            String tooltip = edge.getTooltip();

            assertTrue(tooltip.contains("myLabel"));
        }

        @Test
        void getTooltipExcludesLabelWhenNull() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            String tooltip = edge.getTooltip();

            // Should only contain type and nodes, not additional label line
            long newlineCount = tooltip.chars().filter(ch -> ch == '\n').count();
            assertEquals(1, newlineCount); // Only one newline for "type\nsource → target"
        }
    }

    // ==================== Taint Propagation Tests ====================

    @Nested
    class TaintPropagationTests {

        @Test
        void propagatesTaintForDefUse() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertTrue(edge.propagatesTaint());
        }

        @Test
        void propagatesTaintForPhiInput() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.PHI_INPUT);

            assertTrue(edge.propagatesTaint());
        }

        @Test
        void propagatesTaintForCallArg() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.CALL_ARG);

            assertTrue(edge.propagatesTaint());
        }

        @Test
        void propagatesTaintForAllEdgeTypes() {
            for (DataFlowEdgeType type : DataFlowEdgeType.values()) {
                DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, type);
                assertTrue(edge.propagatesTaint(),
                    "Edge type " + type + " should propagate taint");
            }
        }
    }

    // ==================== Equality Tests ====================

    @Nested
    class EqualityTests {

        @Test
        void equalsReturnsTrueForSameObject() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertEquals(edge, edge);
        }

        @Test
        void equalsReturnsTrueForSameSourceTargetType() {
            DataFlowEdge edge1 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            DataFlowEdge edge2 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertEquals(edge1, edge2);
        }

        @Test
        void equalsReturnsFalseForDifferentSource() {
            DataFlowNode differentSource = DataFlowNode.builder()
                .id(99)
                .type(DataFlowNodeType.PARAM)
                .name("different")
                .location(1, 0)
                .build();

            DataFlowEdge edge1 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            DataFlowEdge edge2 = new DataFlowEdge(differentSource, targetNode, DataFlowEdgeType.DEF_USE);

            assertNotEquals(edge1, edge2);
        }

        @Test
        void equalsReturnsFalseForDifferentTarget() {
            DataFlowNode differentTarget = DataFlowNode.builder()
                .id(99)
                .type(DataFlowNodeType.PARAM)
                .name("different")
                .location(1, 0)
                .build();

            DataFlowEdge edge1 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            DataFlowEdge edge2 = new DataFlowEdge(sourceNode, differentTarget, DataFlowEdgeType.DEF_USE);

            assertNotEquals(edge1, edge2);
        }

        @Test
        void equalsReturnsFalseForDifferentType() {
            DataFlowEdge edge1 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            DataFlowEdge edge2 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.PHI_INPUT);

            assertNotEquals(edge1, edge2);
        }

        @Test
        void equalsIgnoresLabel() {
            DataFlowEdge edge1 = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.CALL_ARG, "label1");
            DataFlowEdge edge2 = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.CALL_ARG, "label2");

            assertEquals(edge1, edge2);
        }

        @Test
        void equalsReturnsFalseForNull() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertNotEquals(edge, null);
        }

        @Test
        void equalsReturnsFalseForDifferentClass() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertNotEquals(edge, "not an edge");
        }
    }

    // ==================== Hash Code Tests ====================

    @Nested
    class HashCodeTests {

        @Test
        void hashCodeConsistentForSameObject() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertEquals(edge.hashCode(), edge.hashCode());
        }

        @Test
        void hashCodeEqualForEqualEdges() {
            DataFlowEdge edge1 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            DataFlowEdge edge2 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertEquals(edge1.hashCode(), edge2.hashCode());
        }

        @Test
        void hashCodeDifferentForDifferentTypes() {
            DataFlowEdge edge1 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            DataFlowEdge edge2 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.PHI_INPUT);

            assertNotEquals(edge1.hashCode(), edge2.hashCode());
        }

        @Test
        void hashCodeIgnoresLabel() {
            DataFlowEdge edge1 = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.CALL_ARG, "label1");
            DataFlowEdge edge2 = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.CALL_ARG, "label2");

            assertEquals(edge1.hashCode(), edge2.hashCode());
        }
    }

    // ==================== ToString Tests ====================

    @Nested
    class ToStringTests {

        @Test
        void toStringIncludesSourceLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            String str = edge.toString();

            assertTrue(str.contains(sourceNode.getLabel()));
        }

        @Test
        void toStringIncludesTargetLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            String str = edge.toString();

            assertTrue(str.contains(targetNode.getLabel()));
        }

        @Test
        void toStringIncludesTypeName() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.CALL_ARG);
            String str = edge.toString();

            assertTrue(str.contains("CALL_ARG"));
        }

        @Test
        void toStringHasArrowFormat() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            String str = edge.toString();

            assertTrue(str.contains("--["));
            assertTrue(str.contains("]-->"));
        }

        @Test
        void toStringDoesNotIncludeLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.FIELD_LOAD, "customLabel");
            String str = edge.toString();

            // toString uses type name, not custom label
            assertFalse(str.contains("customLabel"));
        }
    }

    // ==================== Edge Connection Tests ====================

    @Nested
    class EdgeConnectionTests {

        @Test
        void edgeConnectsSourceToTarget() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);

            assertSame(sourceNode, edge.getSource());
            assertSame(targetNode, edge.getTarget());
        }

        @Test
        void edgeCanConnectNodeToItself() {
            DataFlowEdge selfEdge = new DataFlowEdge(sourceNode, sourceNode, DataFlowEdgeType.DEF_USE);

            assertSame(sourceNode, selfEdge.getSource());
            assertSame(sourceNode, selfEdge.getTarget());
        }

        @Test
        void multipleEdgesBetweenSameNodesWithDifferentTypes() {
            DataFlowEdge edge1 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.DEF_USE);
            DataFlowEdge edge2 = new DataFlowEdge(sourceNode, targetNode, DataFlowEdgeType.OPERAND);

            assertNotEquals(edge1, edge2);
            assertSame(sourceNode, edge1.getSource());
            assertSame(sourceNode, edge2.getSource());
            assertSame(targetNode, edge1.getTarget());
            assertSame(targetNode, edge2.getTarget());
        }

        @Test
        void edgesBetweenDifferentNodeTypes() {
            DataFlowNode paramNode = DataFlowNode.builder()
                .id(10)
                .type(DataFlowNodeType.PARAM)
                .name("param")
                .location(0, 0)
                .build();

            DataFlowNode returnNode = DataFlowNode.builder()
                .id(20)
                .type(DataFlowNodeType.RETURN)
                .name("return")
                .location(1, 5)
                .build();

            DataFlowEdge edge = new DataFlowEdge(paramNode, returnNode, DataFlowEdgeType.DEF_USE);

            assertEquals(DataFlowNodeType.PARAM, edge.getSource().getType());
            assertEquals(DataFlowNodeType.RETURN, edge.getTarget().getType());
        }
    }

    // ==================== Edge Label Scenarios ====================

    @Nested
    class EdgeLabelScenarios {

        @Test
        void callArgEdgeWithParameterLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.CALL_ARG, "arg0");

            assertEquals("arg0", edge.getLabel());
            assertEquals("arg0", edge.getDisplayLabel());
        }

        @Test
        void fieldStoreEdgeWithFieldNameLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.FIELD_STORE, "myField");

            assertEquals("myField", edge.getLabel());
            assertTrue(edge.getTooltip().contains("myField"));
        }

        @Test
        void arrayStoreEdgeWithIndexLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.ARRAY_STORE, "[5]");

            assertEquals("[5]", edge.getLabel());
        }

        @Test
        void phiInputEdgeWithBlockLabel() {
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.PHI_INPUT, "block2");

            assertEquals("block2", edge.getLabel());
        }
    }

    // ==================== Edge Cases and Null Handling ====================

    @Nested
    class EdgeCasesAndNullHandling {

        @Test
        void edgeWithNullSourceAndTarget() {
            // While not recommended, the implementation doesn't prevent this
            DataFlowEdge edge = new DataFlowEdge(null, null, DataFlowEdgeType.DEF_USE);

            assertNull(edge.getSource());
            assertNull(edge.getTarget());
        }

        @Test
        void edgeWithVeryLongLabel() {
            String longLabel = "a".repeat(1000);
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.CALL_ARG, longLabel);

            assertEquals(longLabel, edge.getLabel());
            assertEquals(longLabel, edge.getDisplayLabel());
        }

        @Test
        void edgeWithSpecialCharactersInLabel() {
            String specialLabel = "field$name_123<generic>";
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.FIELD_LOAD, specialLabel);

            assertEquals(specialLabel, edge.getLabel());
        }

        @Test
        void edgeWithUnicodeLabel() {
            String unicodeLabel = "\u5909\u6570\u540D"; // Japanese: "variable name"
            DataFlowEdge edge = new DataFlowEdge(sourceNode, targetNode,
                DataFlowEdgeType.DEF_USE, unicodeLabel);

            assertEquals(unicodeLabel, edge.getLabel());
        }
    }
}
