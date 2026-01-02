package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.ir.ArrayAccessInstruction;
import com.tonic.analysis.ssa.ir.FieldAccessInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FieldAccessListenerTest {

    private FieldAccessListener listener;
    private SimulationState mockState;

    @BeforeEach
    void setUp() {
        listener = new FieldAccessListener();
        mockState = SimulationState.empty();
    }

    @Test
    void trackGetField() {
        FieldAccessInstruction instr = createGetFieldInstruction("Ljava/lang/String;", "value", "Ljava/lang/String;", false);

        listener.onFieldRead(instr, mockState);

        assertEquals(1, listener.getFieldReadCount());
        assertEquals(0, listener.getFieldWriteCount());
        assertEquals(1, listener.getInstanceFieldReadCount());
        assertEquals(0, listener.getStaticFieldReadCount());
        assertEquals(1, listener.getReadCount("Ljava/lang/String;", "value"));
        assertEquals(0, listener.getWriteCount("Ljava/lang/String;", "value"));
    }

    @Test
    void trackMultipleGetField() {
        FieldAccessInstruction instr1 = createGetFieldInstruction("Ljava/lang/String;", "value", "Ljava/lang/String;", false);
        FieldAccessInstruction instr2 = createGetFieldInstruction("Ljava/lang/Integer;", "MAX_VALUE", "I", false);
        FieldAccessInstruction instr3 = createGetFieldInstruction("Ljava/lang/String;", "value", "Ljava/lang/String;", false);

        listener.onFieldRead(instr1, mockState);
        listener.onFieldRead(instr2, mockState);
        listener.onFieldRead(instr3, mockState);

        assertEquals(3, listener.getFieldReadCount());
        assertEquals(0, listener.getFieldWriteCount());
        assertEquals(3, listener.getInstanceFieldReadCount());
        assertEquals(2, listener.getReadCount("Ljava/lang/String;", "value"));
        assertEquals(1, listener.getReadCount("Ljava/lang/Integer;", "MAX_VALUE"));
    }

    @Test
    void trackPutField() {
        FieldAccessInstruction instr = createPutFieldInstruction("Ljava/lang/String;", "value", "Ljava/lang/String;", false);

        listener.onFieldWrite(instr, mockState);

        assertEquals(0, listener.getFieldReadCount());
        assertEquals(1, listener.getFieldWriteCount());
        assertEquals(1, listener.getInstanceFieldWriteCount());
        assertEquals(0, listener.getStaticFieldWriteCount());
        assertEquals(0, listener.getReadCount("Ljava/lang/String;", "value"));
        assertEquals(1, listener.getWriteCount("Ljava/lang/String;", "value"));
    }

    @Test
    void trackMultiplePutField() {
        FieldAccessInstruction instr1 = createPutFieldInstruction("Ljava/lang/String;", "value", "Ljava/lang/String;", false);
        FieldAccessInstruction instr2 = createPutFieldInstruction("Ljava/lang/Integer;", "count", "I", false);
        FieldAccessInstruction instr3 = createPutFieldInstruction("Ljava/lang/String;", "value", "Ljava/lang/String;", false);

        listener.onFieldWrite(instr1, mockState);
        listener.onFieldWrite(instr2, mockState);
        listener.onFieldWrite(instr3, mockState);

        assertEquals(0, listener.getFieldReadCount());
        assertEquals(3, listener.getFieldWriteCount());
        assertEquals(3, listener.getInstanceFieldWriteCount());
        assertEquals(2, listener.getWriteCount("Ljava/lang/String;", "value"));
        assertEquals(1, listener.getWriteCount("Ljava/lang/Integer;", "count"));
    }

    @Test
    void trackGetStatic() {
        FieldAccessInstruction instr = createGetFieldInstruction("Ljava/lang/System;", "out", "Ljava/io/PrintStream;", true);

        listener.onFieldRead(instr, mockState);

        assertEquals(1, listener.getFieldReadCount());
        assertEquals(0, listener.getFieldWriteCount());
        assertEquals(1, listener.getStaticFieldReadCount());
        assertEquals(0, listener.getInstanceFieldReadCount());
        assertEquals(1, listener.getReadCount("Ljava/lang/System;", "out"));
    }

    @Test
    void trackMultipleGetStatic() {
        FieldAccessInstruction instr1 = createGetFieldInstruction("Ljava/lang/System;", "out", "Ljava/io/PrintStream;", true);
        FieldAccessInstruction instr2 = createGetFieldInstruction("Ljava/lang/Integer;", "MAX_VALUE", "I", true);
        FieldAccessInstruction instr3 = createGetFieldInstruction("Ljava/lang/System;", "out", "Ljava/io/PrintStream;", true);

        listener.onFieldRead(instr1, mockState);
        listener.onFieldRead(instr2, mockState);
        listener.onFieldRead(instr3, mockState);

        assertEquals(3, listener.getFieldReadCount());
        assertEquals(3, listener.getStaticFieldReadCount());
        assertEquals(0, listener.getInstanceFieldReadCount());
        assertEquals(2, listener.getReadCount("Ljava/lang/System;", "out"));
        assertEquals(1, listener.getReadCount("Ljava/lang/Integer;", "MAX_VALUE"));
    }

    @Test
    void trackPutStatic() {
        FieldAccessInstruction instr = createPutFieldInstruction("Lcom/example/Config;", "DEBUG", "Z", true);

        listener.onFieldWrite(instr, mockState);

        assertEquals(0, listener.getFieldReadCount());
        assertEquals(1, listener.getFieldWriteCount());
        assertEquals(1, listener.getStaticFieldWriteCount());
        assertEquals(0, listener.getInstanceFieldWriteCount());
        assertEquals(1, listener.getWriteCount("Lcom/example/Config;", "DEBUG"));
    }

    @Test
    void trackMultiplePutStatic() {
        FieldAccessInstruction instr1 = createPutFieldInstruction("Lcom/example/Config;", "DEBUG", "Z", true);
        FieldAccessInstruction instr2 = createPutFieldInstruction("Lcom/example/Config;", "VERSION", "Ljava/lang/String;", true);
        FieldAccessInstruction instr3 = createPutFieldInstruction("Lcom/example/Config;", "DEBUG", "Z", true);

        listener.onFieldWrite(instr1, mockState);
        listener.onFieldWrite(instr2, mockState);
        listener.onFieldWrite(instr3, mockState);

        assertEquals(0, listener.getFieldReadCount());
        assertEquals(3, listener.getFieldWriteCount());
        assertEquals(3, listener.getStaticFieldWriteCount());
        assertEquals(0, listener.getInstanceFieldWriteCount());
        assertEquals(2, listener.getWriteCount("Lcom/example/Config;", "DEBUG"));
        assertEquals(1, listener.getWriteCount("Lcom/example/Config;", "VERSION"));
    }

    @Test
    void distinguishStaticVsInstance() {
        FieldAccessInstruction getStatic = createGetFieldInstruction("Ljava/lang/System;", "out", "Ljava/io/PrintStream;", true);
        FieldAccessInstruction getInstance = createGetFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        FieldAccessInstruction putStatic = createPutFieldInstruction("Lcom/example/Config;", "DEBUG", "Z", true);
        FieldAccessInstruction putInstance = createPutFieldInstruction("Ljava/lang/Point;", "x", "I", false);

        listener.onFieldRead(getStatic, mockState);
        listener.onFieldRead(getInstance, mockState);
        listener.onFieldWrite(putStatic, mockState);
        listener.onFieldWrite(putInstance, mockState);

        assertEquals(2, listener.getFieldReadCount());
        assertEquals(1, listener.getStaticFieldReadCount());
        assertEquals(1, listener.getInstanceFieldReadCount());

        assertEquals(2, listener.getFieldWriteCount());
        assertEquals(1, listener.getStaticFieldWriteCount());
        assertEquals(1, listener.getInstanceFieldWriteCount());

        assertEquals(4, listener.getTotalFieldAccesses());
    }

    @Test
    void getAccessesByField() {
        FieldAccessInstruction read1 = createGetFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        FieldAccessInstruction write1 = createPutFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        FieldAccessInstruction read2 = createGetFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        FieldAccessInstruction read3 = createGetFieldInstruction("Ljava/lang/Integer;", "value", "I", false);

        listener.onFieldRead(read1, mockState);
        listener.onFieldWrite(write1, mockState);
        listener.onFieldRead(read2, mockState);
        listener.onFieldRead(read3, mockState);

        assertEquals(2, listener.getReadCount("Ljava/lang/String;", "value"));
        assertEquals(1, listener.getWriteCount("Ljava/lang/String;", "value"));
        assertEquals(1, listener.getReadCount("Ljava/lang/Integer;", "value"));
        assertEquals(0, listener.getWriteCount("Ljava/lang/Integer;", "value"));
        assertEquals(0, listener.getReadCount("NonExistent", "field"));
        assertEquals(0, listener.getWriteCount("NonExistent", "field"));
    }

    @Test
    void trackArrayAccess() {
        ArrayAccessInstruction load = createMockArrayLoadInstruction();
        ArrayAccessInstruction store = createMockArrayStoreInstruction();

        listener.onArrayRead(load, mockState);
        listener.onArrayWrite(store, mockState);

        assertEquals(1, listener.getArrayReadCount());
        assertEquals(1, listener.getArrayWriteCount());
        assertEquals(2, listener.getTotalArrayAccesses());
    }

    @Test
    void trackMultipleArrayAccess() {
        ArrayAccessInstruction load1 = createMockArrayLoadInstruction();
        ArrayAccessInstruction load2 = createMockArrayLoadInstruction();
        ArrayAccessInstruction store1 = createMockArrayStoreInstruction();
        ArrayAccessInstruction store2 = createMockArrayStoreInstruction();
        ArrayAccessInstruction store3 = createMockArrayStoreInstruction();

        listener.onArrayRead(load1, mockState);
        listener.onArrayRead(load2, mockState);
        listener.onArrayWrite(store1, mockState);
        listener.onArrayWrite(store2, mockState);
        listener.onArrayWrite(store3, mockState);

        assertEquals(2, listener.getArrayReadCount());
        assertEquals(3, listener.getArrayWriteCount());
        assertEquals(5, listener.getTotalArrayAccesses());
    }

    @Test
    void getFieldAccessesMap() {
        FieldAccessInstruction read1 = createGetFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        FieldAccessInstruction write1 = createPutFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        FieldAccessInstruction read2 = createGetFieldInstruction("Ljava/lang/Integer;", "MAX_VALUE", "I", true);

        listener.onFieldRead(read1, mockState);
        listener.onFieldWrite(write1, mockState);
        listener.onFieldRead(read2, mockState);

        Map<FieldAccessListener.FieldReference, FieldAccessListener.AccessStats> accesses = listener.getFieldAccesses();

        assertEquals(2, accesses.size());

        FieldAccessListener.FieldReference stringValueRef = new FieldAccessListener.FieldReference("Ljava/lang/String;", "value", "[C");
        assertTrue(accesses.containsKey(stringValueRef));
        assertEquals(1, accesses.get(stringValueRef).getReadCount());
        assertEquals(1, accesses.get(stringValueRef).getWriteCount());
        assertEquals(2, accesses.get(stringValueRef).getTotalCount());

        FieldAccessListener.FieldReference intMaxValueRef = new FieldAccessListener.FieldReference("Ljava/lang/Integer;", "MAX_VALUE", "I");
        assertTrue(accesses.containsKey(intMaxValueRef));
        assertEquals(1, accesses.get(intMaxValueRef).getReadCount());
        assertEquals(0, accesses.get(intMaxValueRef).getWriteCount());
    }

    @Test
    void getFieldAccessesIsUnmodifiable() {
        FieldAccessInstruction read = createGetFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        listener.onFieldRead(read, mockState);

        Map<FieldAccessListener.FieldReference, FieldAccessListener.AccessStats> accesses = listener.getFieldAccesses();

        assertThrows(UnsupportedOperationException.class, () -> {
            accesses.put(new FieldAccessListener.FieldReference("Test", "field", "I"), new FieldAccessListener.AccessStats());
        });
    }

    @Test
    void getDistinctFieldCount() {
        FieldAccessInstruction read1 = createGetFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        FieldAccessInstruction write1 = createPutFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        FieldAccessInstruction read2 = createGetFieldInstruction("Ljava/lang/Integer;", "MAX_VALUE", "I", true);
        FieldAccessInstruction read3 = createGetFieldInstruction("Ljava/lang/String;", "hash", "I", false);

        assertEquals(0, listener.getDistinctFieldCount());

        listener.onFieldRead(read1, mockState);
        assertEquals(1, listener.getDistinctFieldCount());

        listener.onFieldWrite(write1, mockState);
        assertEquals(1, listener.getDistinctFieldCount());

        listener.onFieldRead(read2, mockState);
        assertEquals(2, listener.getDistinctFieldCount());

        listener.onFieldRead(read3, mockState);
        assertEquals(3, listener.getDistinctFieldCount());
    }

    @Test
    void resetOnNewSimulation() {
        FieldAccessInstruction read = createGetFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        FieldAccessInstruction write = createPutFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        ArrayAccessInstruction arrayRead = createMockArrayLoadInstruction();
        ArrayAccessInstruction arrayWrite = createMockArrayStoreInstruction();

        listener.onFieldRead(read, mockState);
        listener.onFieldWrite(write, mockState);
        listener.onArrayRead(arrayRead, mockState);
        listener.onArrayWrite(arrayWrite, mockState);

        assertEquals(1, listener.getFieldReadCount());
        assertEquals(1, listener.getFieldWriteCount());
        assertEquals(1, listener.getArrayReadCount());
        assertEquals(1, listener.getArrayWriteCount());
        assertEquals(1, listener.getDistinctFieldCount());

        listener.onSimulationStart(null);

        assertEquals(0, listener.getFieldReadCount());
        assertEquals(0, listener.getFieldWriteCount());
        assertEquals(0, listener.getStaticFieldReadCount());
        assertEquals(0, listener.getStaticFieldWriteCount());
        assertEquals(0, listener.getInstanceFieldReadCount());
        assertEquals(0, listener.getInstanceFieldWriteCount());
        assertEquals(0, listener.getArrayReadCount());
        assertEquals(0, listener.getArrayWriteCount());
        assertEquals(0, listener.getDistinctFieldCount());
        assertEquals(0, listener.getFieldAccesses().size());
    }

    @Test
    void fieldReferenceEquality() {
        FieldAccessListener.FieldReference ref1 = new FieldAccessListener.FieldReference("Ljava/lang/String;", "value", "[C");
        FieldAccessListener.FieldReference ref2 = new FieldAccessListener.FieldReference("Ljava/lang/String;", "value", "[C");
        FieldAccessListener.FieldReference ref3 = new FieldAccessListener.FieldReference("Ljava/lang/String;", "hash", "I");
        FieldAccessListener.FieldReference ref4 = new FieldAccessListener.FieldReference("Ljava/lang/Integer;", "value", "I");

        assertEquals(ref1, ref2);
        assertNotEquals(ref1, ref3);
        assertNotEquals(ref1, ref4);
        assertEquals(ref1.hashCode(), ref2.hashCode());
    }

    @Test
    void fieldReferenceToString() {
        FieldAccessListener.FieldReference ref = new FieldAccessListener.FieldReference("Ljava/lang/String;", "value", "[C");
        assertEquals("Ljava/lang/String;.value:[C", ref.toString());
    }

    @Test
    void accessStatsIncrements() {
        FieldAccessListener.AccessStats stats = new FieldAccessListener.AccessStats();

        assertEquals(0, stats.getReadCount());
        assertEquals(0, stats.getWriteCount());
        assertEquals(0, stats.getTotalCount());

        stats.incrementReads();
        assertEquals(1, stats.getReadCount());
        assertEquals(0, stats.getWriteCount());
        assertEquals(1, stats.getTotalCount());

        stats.incrementWrites();
        assertEquals(1, stats.getReadCount());
        assertEquals(1, stats.getWriteCount());
        assertEquals(2, stats.getTotalCount());

        stats.incrementReads();
        stats.incrementReads();
        assertEquals(3, stats.getReadCount());
        assertEquals(1, stats.getWriteCount());
        assertEquals(4, stats.getTotalCount());
    }

    @Test
    void accessStatsToString() {
        FieldAccessListener.AccessStats stats = new FieldAccessListener.AccessStats();
        stats.incrementReads();
        stats.incrementReads();
        stats.incrementWrites();

        String str = stats.toString();
        assertTrue(str.contains("reads=2"));
        assertTrue(str.contains("writes=1"));
    }

    @Test
    void listenerToString() {
        FieldAccessInstruction read1 = createGetFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        FieldAccessInstruction write1 = createPutFieldInstruction("Ljava/lang/String;", "value", "[C", false);
        ArrayAccessInstruction arrayRead = createMockArrayLoadInstruction();
        ArrayAccessInstruction arrayWrite = createMockArrayStoreInstruction();

        listener.onFieldRead(read1, mockState);
        listener.onFieldWrite(write1, mockState);
        listener.onArrayRead(arrayRead, mockState);
        listener.onArrayWrite(arrayWrite, mockState);

        String str = listener.toString();
        assertTrue(str.contains("fieldReads=1"));
        assertTrue(str.contains("fieldWrites=1"));
        assertTrue(str.contains("arrayReads=1"));
        assertTrue(str.contains("arrayWrites=1"));
    }

    private static FieldAccessInstruction createGetFieldInstruction(String owner, String name, String descriptor, boolean isStatic) {
        if (isStatic) {
            return FieldAccessInstruction.createStaticLoad(new MockSSAValue(), owner, name, descriptor);
        } else {
            return FieldAccessInstruction.createLoad(new MockSSAValue(), owner, name, descriptor, new MockValue());
        }
    }

    private static FieldAccessInstruction createPutFieldInstruction(String owner, String name, String descriptor, boolean isStatic) {
        if (isStatic) {
            return FieldAccessInstruction.createStaticStore(owner, name, descriptor, new MockValue());
        } else {
            return FieldAccessInstruction.createStore(owner, name, descriptor, new MockValue(), new MockValue());
        }
    }

    private static ArrayAccessInstruction createMockArrayLoadInstruction() {
        return ArrayAccessInstruction.createLoad(new MockSSAValue(), new MockValue(), new MockValue());
    }

    private static ArrayAccessInstruction createMockArrayStoreInstruction() {
        return ArrayAccessInstruction.createStore(new MockValue(), new MockValue(), new MockValue());
    }

    private static class MockValue implements Value {
        @Override
        public IRType getType() {
            return PrimitiveType.INT;
        }

        @Override
        public boolean isConstant() {
            return false;
        }

        @Override
        public String toString() {
            return "MockValue";
        }
    }

    private static class MockSSAValue extends SSAValue {
        MockSSAValue() {
            super(PrimitiveType.INT);
        }
    }
}
