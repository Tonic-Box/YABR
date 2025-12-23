package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.ir.NewArrayInstruction;
import com.tonic.analysis.ssa.ir.NewInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AllocationListenerTest {

    private AllocationListener listener;
    private SimulationState mockState;

    @BeforeEach
    void setUp() {
        listener = new AllocationListener();
        mockState = SimulationState.empty();
    }

    @Test
    void trackObjectAllocation() {
        NewInstruction instr = new MockNewInstruction("Ljava/lang/String;");

        listener.onAllocation(instr, mockState);

        assertEquals(1, listener.getObjectAllocationCount());
        assertEquals(0, listener.getArrayAllocationCount());
        assertEquals(1, listener.getTotalCount());
        assertEquals(1, listener.getCountForType("Ljava/lang/String;"));
    }

    @Test
    void trackMultipleObjectAllocations() {
        NewInstruction instr1 = new MockNewInstruction("Ljava/lang/String;");
        NewInstruction instr2 = new MockNewInstruction("Ljava/lang/Integer;");
        NewInstruction instr3 = new MockNewInstruction("Ljava/lang/String;");

        listener.onAllocation(instr1, mockState);
        listener.onAllocation(instr2, mockState);
        listener.onAllocation(instr3, mockState);

        assertEquals(3, listener.getObjectAllocationCount());
        assertEquals(0, listener.getArrayAllocationCount());
        assertEquals(3, listener.getTotalCount());
        assertEquals(2, listener.getCountForType("Ljava/lang/String;"));
        assertEquals(1, listener.getCountForType("Ljava/lang/Integer;"));
    }

    @Test
    void trackArrayAllocation() {
        NewArrayInstruction instr = new MockNewArrayInstruction("I", 1);

        listener.onArrayAllocation(instr, mockState);

        assertEquals(0, listener.getObjectAllocationCount());
        assertEquals(1, listener.getArrayAllocationCount());
        assertEquals(1, listener.getTotalCount());
        assertEquals(1, listener.getCountForType("I[]"));
    }

    @Test
    void trackMultipleArrayAllocations() {
        NewArrayInstruction instr1 = new MockNewArrayInstruction("I", 1);
        NewArrayInstruction instr2 = new MockNewArrayInstruction("Ljava/lang/String;", 1);
        NewArrayInstruction instr3 = new MockNewArrayInstruction("I", 1);

        listener.onArrayAllocation(instr1, mockState);
        listener.onArrayAllocation(instr2, mockState);
        listener.onArrayAllocation(instr3, mockState);

        assertEquals(0, listener.getObjectAllocationCount());
        assertEquals(3, listener.getArrayAllocationCount());
        assertEquals(3, listener.getTotalCount());
        assertEquals(2, listener.getCountForType("I[]"));
        assertEquals(1, listener.getCountForType("Ljava/lang/String;[]"));
    }

    @Test
    void trackMultiDimensionalArray() {
        NewArrayInstruction instr = new MockNewArrayInstruction("I", 3);

        listener.onArrayAllocation(instr, mockState);

        assertEquals(0, listener.getObjectAllocationCount());
        assertEquals(1, listener.getArrayAllocationCount());
        assertEquals(1, listener.getTotalCount());
        assertEquals(1, listener.getCountForType("I[](3D)"));
    }

    @Test
    void trackMixedAllocations() {
        NewInstruction objInstr1 = new MockNewInstruction("Ljava/lang/String;");
        NewArrayInstruction arrInstr1 = new MockNewArrayInstruction("I", 1);
        NewInstruction objInstr2 = new MockNewInstruction("Ljava/util/HashMap;");
        NewArrayInstruction arrInstr2 = new MockNewArrayInstruction("Ljava/lang/String;", 2);

        listener.onAllocation(objInstr1, mockState);
        listener.onArrayAllocation(arrInstr1, mockState);
        listener.onAllocation(objInstr2, mockState);
        listener.onArrayAllocation(arrInstr2, mockState);

        assertEquals(2, listener.getObjectAllocationCount());
        assertEquals(2, listener.getArrayAllocationCount());
        assertEquals(4, listener.getTotalCount());
        assertEquals(1, listener.getCountForType("Ljava/lang/String;"));
        assertEquals(1, listener.getCountForType("I[]"));
        assertEquals(1, listener.getCountForType("Ljava/util/HashMap;"));
        assertEquals(1, listener.getCountForType("Ljava/lang/String;[](2D)"));
    }

    @Test
    void getAllocationsByType() {
        NewInstruction instr1 = new MockNewInstruction("Ljava/lang/String;");
        NewInstruction instr2 = new MockNewInstruction("Ljava/lang/Integer;");
        NewInstruction instr3 = new MockNewInstruction("Ljava/lang/String;");
        NewArrayInstruction instr4 = new MockNewArrayInstruction("I", 1);

        listener.onAllocation(instr1, mockState);
        listener.onAllocation(instr2, mockState);
        listener.onAllocation(instr3, mockState);
        listener.onArrayAllocation(instr4, mockState);

        Map<String, Integer> byType = listener.getAllocationsByType();

        assertEquals(3, byType.size());
        assertEquals(2, byType.get("Ljava/lang/String;"));
        assertEquals(1, byType.get("Ljava/lang/Integer;"));
        assertEquals(1, byType.get("I[]"));
    }

    @Test
    void getAllocationsByTypeIsUnmodifiable() {
        NewInstruction instr = new MockNewInstruction("Ljava/lang/String;");
        listener.onAllocation(instr, mockState);

        Map<String, Integer> byType = listener.getAllocationsByType();

        assertThrows(UnsupportedOperationException.class, () -> {
            byType.put("Ljava/lang/Object;", 5);
        });
    }

    @Test
    void getCountForTypeWhenTypeDoesNotExist() {
        assertEquals(0, listener.getCountForType("Ljava/lang/String;"));
    }

    @Test
    void getDistinctTypeCount() {
        NewInstruction instr1 = new MockNewInstruction("Ljava/lang/String;");
        NewInstruction instr2 = new MockNewInstruction("Ljava/lang/Integer;");
        NewInstruction instr3 = new MockNewInstruction("Ljava/lang/String;");
        NewArrayInstruction instr4 = new MockNewArrayInstruction("I", 1);

        assertEquals(0, listener.getDistinctTypeCount());

        listener.onAllocation(instr1, mockState);
        assertEquals(1, listener.getDistinctTypeCount());

        listener.onAllocation(instr2, mockState);
        assertEquals(2, listener.getDistinctTypeCount());

        listener.onAllocation(instr3, mockState);
        assertEquals(2, listener.getDistinctTypeCount());

        listener.onArrayAllocation(instr4, mockState);
        assertEquals(3, listener.getDistinctTypeCount());
    }

    @Test
    void getAllocationSites() {
        listener = new AllocationListener(true);
        NewInstruction instr1 = new MockNewInstruction("Ljava/lang/String;");
        NewArrayInstruction instr2 = new MockNewArrayInstruction("I", 1);

        listener.onAllocation(instr1, mockState);
        listener.onArrayAllocation(instr2, mockState);

        List<AllocationListener.AllocationSite> sites = listener.getAllocationSites();

        assertEquals(2, sites.size());
        assertFalse(sites.get(0).isArray());
        assertEquals("Ljava/lang/String;", sites.get(0).getTypeName());
        assertEquals(0, sites.get(0).getStackDepthAtAllocation());
        assertTrue(sites.get(1).isArray());
        assertEquals("I[]", sites.get(1).getTypeName());
        assertEquals(0, sites.get(1).getStackDepthAtAllocation());
    }

    @Test
    void getAllocationSitesIsUnmodifiable() {
        listener = new AllocationListener(true);
        NewInstruction instr = new MockNewInstruction("Ljava/lang/String;");
        listener.onAllocation(instr, mockState);

        List<AllocationListener.AllocationSite> sites = listener.getAllocationSites();

        assertThrows(UnsupportedOperationException.class, () -> {
            sites.add(null);
        });
    }

    @Test
    void getAllocationsOfType() {
        listener = new AllocationListener(true);
        NewInstruction instr1 = new MockNewInstruction("Ljava/lang/String;");
        NewInstruction instr2 = new MockNewInstruction("Ljava/lang/Integer;");
        NewInstruction instr3 = new MockNewInstruction("Ljava/lang/String;");

        listener.onAllocation(instr1, mockState);
        listener.onAllocation(instr2, mockState);
        listener.onAllocation(instr3, mockState);

        List<AllocationListener.AllocationSite> stringSites = listener.getAllocationsOf("Ljava/lang/String;");
        assertEquals(2, stringSites.size());
        assertEquals("Ljava/lang/String;", stringSites.get(0).getTypeName());
        assertEquals("Ljava/lang/String;", stringSites.get(1).getTypeName());

        List<AllocationListener.AllocationSite> intSites = listener.getAllocationsOf("Ljava/lang/Integer;");
        assertEquals(1, intSites.size());
        assertEquals("Ljava/lang/Integer;", intSites.get(0).getTypeName());

        List<AllocationListener.AllocationSite> nonExistent = listener.getAllocationsOf("Ljava/lang/Object;");
        assertEquals(0, nonExistent.size());
    }

    @Test
    void trackSitesDisabledDoesNotRecordSites() {
        listener = new AllocationListener(false);
        NewInstruction instr = new MockNewInstruction("Ljava/lang/String;");

        listener.onAllocation(instr, mockState);

        assertEquals(1, listener.getObjectAllocationCount());
        assertEquals(0, listener.getAllocationSites().size());
    }

    @Test
    void resetOnNewSimulation() {
        NewInstruction instr1 = new MockNewInstruction("Ljava/lang/String;");
        NewArrayInstruction instr2 = new MockNewArrayInstruction("I", 1);

        listener.onAllocation(instr1, mockState);
        listener.onArrayAllocation(instr2, mockState);

        assertEquals(1, listener.getObjectAllocationCount());
        assertEquals(1, listener.getArrayAllocationCount());
        assertEquals(2, listener.getDistinctTypeCount());

        listener.onSimulationStart(null);

        assertEquals(0, listener.getObjectAllocationCount());
        assertEquals(0, listener.getArrayAllocationCount());
        assertEquals(0, listener.getTotalCount());
        assertEquals(0, listener.getDistinctTypeCount());
        assertEquals(0, listener.getAllocationSites().size());
    }

    @Test
    void handleNullTypeGracefully() {
        NewInstruction instr = new MockNewInstruction(null);

        listener.onAllocation(instr, mockState);

        assertEquals(1, listener.getObjectAllocationCount());
        assertEquals(1, listener.getCountForType("unknown"));
    }

    @Test
    void handleNullElementTypeForArrayGracefully() {
        NewArrayInstruction instr = new MockNewArrayInstruction(null, 1);

        listener.onArrayAllocation(instr, mockState);

        assertEquals(1, listener.getArrayAllocationCount());
        assertEquals(1, listener.getCountForType("?[]"));
    }

    @Test
    void allocationSiteToString() {
        listener = new AllocationListener(true);
        NewInstruction objInstr = new MockNewInstruction("Ljava/lang/String;");
        NewArrayInstruction arrInstr = new MockNewArrayInstruction("I", 1);

        listener.onAllocation(objInstr, mockState);
        listener.onArrayAllocation(arrInstr, mockState);

        List<AllocationListener.AllocationSite> sites = listener.getAllocationSites();
        assertEquals("NEW Ljava/lang/String;", sites.get(0).toString());
        assertEquals("NEWARRAY I[]", sites.get(1).toString());
    }

    @Test
    void listenerToString() {
        NewInstruction instr1 = new MockNewInstruction("Ljava/lang/String;");
        NewInstruction instr2 = new MockNewInstruction("Ljava/lang/Integer;");
        NewArrayInstruction instr3 = new MockNewArrayInstruction("I", 1);

        listener.onAllocation(instr1, mockState);
        listener.onAllocation(instr2, mockState);
        listener.onArrayAllocation(instr3, mockState);

        String str = listener.toString();
        assertTrue(str.contains("objects=2"));
        assertTrue(str.contains("arrays=1"));
        assertTrue(str.contains("types=3"));
    }

    private static class MockNewInstruction extends NewInstruction {
        private final IRType resultType;

        MockNewInstruction(String typeDescriptor) {
            super(null, "MockClass");
            this.resultType = typeDescriptor != null ? new MockIRType(typeDescriptor) : null;
        }

        @Override
        public IRType getResultType() {
            return resultType;
        }
    }

    private static class MockNewArrayInstruction extends NewArrayInstruction {
        private final int dimensions;

        MockNewArrayInstruction(String elementTypeDescriptor, int dimensions) {
            super(null, elementTypeDescriptor != null ? new MockIRType(elementTypeDescriptor) : null, (Value) null);
            this.dimensions = dimensions;
        }

        @Override
        public boolean isMultiDimensional() {
            return dimensions > 1;
        }

        @Override
        public List<Value> getDimensions() {
            List<Value> dims = new ArrayList<>();
            for (int i = 0; i < dimensions; i++) {
                dims.add(null);
            }
            return dims;
        }
    }

    private static class MockIRType implements IRType {
        private final String descriptor;

        MockIRType(String descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public String getDescriptor() {
            return descriptor;
        }

        @Override
        public int getSize() {
            return 1;
        }

        @Override
        public boolean isPrimitive() {
            return false;
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean isVoid() {
            return false;
        }

        @Override
        public boolean isArray() {
            return false;
        }

        @Override
        public boolean isTwoSlot() {
            return false;
        }
    }
}
