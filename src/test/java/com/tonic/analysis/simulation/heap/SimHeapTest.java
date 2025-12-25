package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SimHeapTest {

    @Nested
    class AllocationSiteTests {

        @Test
        void testAllocationSiteEquality() {
            AllocationSite site1 = AllocationSite.of("com/example/Foo", 10, "com/example/Bar.test()V");
            AllocationSite site2 = AllocationSite.of("com/example/Foo", 10, "com/example/Bar.test()V");
            assertEquals(site1, site2);
        }

        @Test
        void testDifferentMethodsDifferentSites() {
            AllocationSite site1 = AllocationSite.of("com/example/Foo", 10, "method1");
            AllocationSite site2 = AllocationSite.of("com/example/Foo", 10, "method2");
            assertNotEquals(site1, site2);
        }

        @Test
        void testDifferentIndexDifferentSites() {
            AllocationSite site1 = AllocationSite.of("com/example/Foo", 10, "method");
            AllocationSite site2 = AllocationSite.of("com/example/Foo", 20, "method");
            assertNotEquals(site1, site2);
        }

        @Test
        void testAllocationSiteHashCode() {
            AllocationSite site1 = AllocationSite.of("com/example/Foo", 10, "method");
            AllocationSite site2 = AllocationSite.of("com/example/Foo", 10, "method");
            assertEquals(site1.hashCode(), site2.hashCode());
        }

        @Test
        void testAllocationSiteToString() {
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            String str = site.toString();
            assertTrue(str.contains("Foo"));
        }

        @Test
        void testSyntheticAllocationSite() {
            AllocationSite site = AllocationSite.synthetic("com/example/Foo", "test");
            assertEquals("com/example/Foo", site.getClassName());
            assertTrue(site.isSynthetic());
        }

        @Test
        void testAllocationSiteGetters() {
            AllocationSite site = AllocationSite.of("com/example/Foo", 15, "method()V");
            assertEquals("com/example/Foo", site.getClassName());
            assertEquals(15, site.getInstructionIndex());
            assertEquals("method()V", site.getMethodKey());
        }
    }

    @Nested
    class FieldKeyTests {

        @Test
        void testFieldKeyEquality() {
            FieldKey key1 = FieldKey.of("com/example/Foo", "value", "I");
            FieldKey key2 = FieldKey.of("com/example/Foo", "value", "I");
            assertEquals(key1, key2);
        }

        @Test
        void testDifferentOwnerDifferentKey() {
            FieldKey key1 = FieldKey.of("com/example/Foo", "value", "I");
            FieldKey key2 = FieldKey.of("com/example/Bar", "value", "I");
            assertNotEquals(key1, key2);
        }

        @Test
        void testDifferentNameDifferentKey() {
            FieldKey key1 = FieldKey.of("com/example/Foo", "value1", "I");
            FieldKey key2 = FieldKey.of("com/example/Foo", "value2", "I");
            assertNotEquals(key1, key2);
        }

        @Test
        void testDifferentDescriptorDifferentKey() {
            FieldKey key1 = FieldKey.of("com/example/Foo", "value", "I");
            FieldKey key2 = FieldKey.of("com/example/Foo", "value", "J");
            assertNotEquals(key1, key2);
        }

        @Test
        void testFieldKeyHashCode() {
            FieldKey key1 = FieldKey.of("com/example/Foo", "value", "I");
            FieldKey key2 = FieldKey.of("com/example/Foo", "value", "I");
            assertEquals(key1.hashCode(), key2.hashCode());
        }

        @Test
        void testFieldKeyGetters() {
            FieldKey key = FieldKey.of("com/example/Foo", "count", "I");
            assertEquals("com/example/Foo", key.getOwner());
            assertEquals("count", key.getName());
            assertEquals("I", key.getDescriptor());
        }

        @Test
        void testFieldKeyToString() {
            FieldKey key = FieldKey.of("com/example/Foo", "count", "I");
            String str = key.toString();
            assertTrue(str.contains("Foo"));
            assertTrue(str.contains("count"));
        }
    }

    @Nested
    class HeapModeTests {

        @Test
        void testHeapModeValues() {
            HeapMode[] modes = HeapMode.values();
            assertEquals(3, modes.length);
            assertNotNull(HeapMode.IMMUTABLE);
            assertNotNull(HeapMode.MUTABLE);
            assertNotNull(HeapMode.COPY_ON_MERGE);
        }

        @Test
        void testHeapModeValueOf() {
            assertEquals(HeapMode.IMMUTABLE, HeapMode.valueOf("IMMUTABLE"));
            assertEquals(HeapMode.MUTABLE, HeapMode.valueOf("MUTABLE"));
            assertEquals(HeapMode.COPY_ON_MERGE, HeapMode.valueOf("COPY_ON_MERGE"));
        }
    }

    @Nested
    class SimObjectTests {

        @Test
        void testObjectCreation() {
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            SimObject obj = new SimObject(site);
            assertEquals(site, obj.getSite());
            assertFalse(obj.hasEscaped());
        }

        @Test
        void testFieldWrite() {
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            SimObject obj = new SimObject(site);
            FieldKey field = FieldKey.of("com/example/Foo", "value", "I");
            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

            SimObject updated = obj.withField(field, value);
            Set<SimValue> values = updated.getField(field);
            assertEquals(1, values.size());
            assertTrue(values.contains(value));
        }

        @Test
        void testFieldRead() {
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            SimObject obj = new SimObject(site);
            FieldKey field = FieldKey.of("com/example/Foo", "value", "I");
            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

            SimObject updated = obj.withField(field, value);
            Set<SimValue> retrieved = updated.getField(field);
            assertEquals(1, retrieved.size());
            assertEquals(value, retrieved.iterator().next());
        }

        @Test
        void testUnknownFieldRead() {
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            SimObject obj = new SimObject(site);
            FieldKey unknownField = FieldKey.of("com/example/Foo", "unknown", "I");

            Set<SimValue> values = obj.getField(unknownField);
            assertTrue(values.isEmpty());
        }

        @Test
        void testObjectMerge() {
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            FieldKey field = FieldKey.of("com/example/Foo", "value", "I");

            SimObject obj1 = new SimObject(site).withField(field, SimValue.constant(1, PrimitiveType.INT, null));
            SimObject obj2 = new SimObject(site).withField(field, SimValue.constant(2, PrimitiveType.INT, null));

            SimObject merged = obj1.merge(obj2);
            Set<SimValue> values = merged.getField(field);
            assertEquals(2, values.size());
        }

        @Test
        void testEscapeMarking() {
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            SimObject obj = new SimObject(site);
            assertFalse(obj.hasEscaped());

            SimObject escaped = obj.markEscaped();
            assertTrue(escaped.hasEscaped());
            assertFalse(obj.hasEscaped());
        }

        @Test
        void testImmutableUpdates() {
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            SimObject obj = new SimObject(site);
            FieldKey field = FieldKey.of("com/example/Foo", "value", "I");
            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

            SimObject updated = obj.withField(field, value);
            assertTrue(obj.getField(field).isEmpty());
            assertEquals(1, updated.getField(field).size());
        }

        @Test
        void testMultipleFieldValues() {
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            FieldKey field = FieldKey.of("com/example/Foo", "value", "I");

            SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
            SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

            SimObject obj = new SimObject(site)
                .withField(field, v1)
                .withField(field, v2);

            Set<SimValue> values = obj.getField(field);
            assertEquals(2, values.size());
        }

        @Test
        void testGetFieldKeys() {
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            FieldKey field1 = FieldKey.of("com/example/Foo", "a", "I");
            FieldKey field2 = FieldKey.of("com/example/Foo", "b", "J");

            SimObject obj = new SimObject(site)
                .withField(field1, SimValue.constant(1, PrimitiveType.INT, null))
                .withField(field2, SimValue.constant(2L, PrimitiveType.LONG, null));

            Set<FieldKey> keys = obj.getFieldKeys();
            assertEquals(2, keys.size());
            assertTrue(keys.contains(field1));
            assertTrue(keys.contains(field2));
        }
    }

    @Nested
    class SimArrayTests {

        @Test
        void testArrayCreation() {
            AllocationSite site = AllocationSite.of("[I", 10, "method");
            SimValue length = SimValue.constant(5, PrimitiveType.INT, null);
            SimArray arr = new SimArray(site, PrimitiveType.INT, length);

            assertEquals(site, arr.getSite());
            assertEquals(PrimitiveType.INT, arr.getElementType());
            assertEquals(length, arr.getLength());
        }

        @Test
        void testArrayStore() {
            AllocationSite site = AllocationSite.of("[I", 10, "method");
            SimValue length = SimValue.constant(5, PrimitiveType.INT, null);
            SimArray arr = new SimArray(site, PrimitiveType.INT, length);

            SimValue index = SimValue.constant(2, PrimitiveType.INT, null);
            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

            SimArray updated = arr.withElement(index, value);
            assertNotSame(arr, updated);
        }

        @Test
        void testArrayLoad() {
            AllocationSite site = AllocationSite.of("[I", 10, "method");
            SimValue length = SimValue.constant(5, PrimitiveType.INT, null);
            SimArray arr = new SimArray(site, PrimitiveType.INT, length);

            SimValue index = SimValue.constant(2, PrimitiveType.INT, null);
            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

            SimArray updated = arr.withElement(index, value);
            Set<SimValue> loaded = updated.getElement(index);
            assertTrue(loaded.contains(value));
        }

        @Test
        void testUnknownIndexStore() {
            AllocationSite site = AllocationSite.of("[I", 10, "method");
            SimValue length = SimValue.constant(5, PrimitiveType.INT, null);
            SimArray arr = new SimArray(site, PrimitiveType.INT, length);

            SimValue unknownIndex = SimValue.ofType(PrimitiveType.INT, null);
            SimValue value = SimValue.constant(100, PrimitiveType.INT, null);

            SimArray updated = arr.withElement(unknownIndex, value);
            Set<SimValue> allElements = updated.getAllElements();
            assertTrue(allElements.contains(value));
        }

        @Test
        void testUnknownIndexLoad() {
            AllocationSite site = AllocationSite.of("[I", 10, "method");
            SimValue length = SimValue.constant(5, PrimitiveType.INT, null);
            SimArray arr = new SimArray(site, PrimitiveType.INT, length);

            SimValue v1 = SimValue.constant(10, PrimitiveType.INT, null);
            SimValue v2 = SimValue.constant(20, PrimitiveType.INT, null);

            SimArray updated = arr
                .withElement(SimValue.constant(0, PrimitiveType.INT, null), v1)
                .withElement(SimValue.constant(1, PrimitiveType.INT, null), v2);

            SimValue unknownIndex = SimValue.ofType(PrimitiveType.INT, null);
            Set<SimValue> loaded = updated.getElement(unknownIndex);
            assertTrue(loaded.contains(v1));
            assertTrue(loaded.contains(v2));
        }

        @Test
        void testArrayMerge() {
            AllocationSite site = AllocationSite.of("[I", 10, "method");
            SimValue length = SimValue.constant(5, PrimitiveType.INT, null);

            SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
            SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

            SimArray arr1 = new SimArray(site, PrimitiveType.INT, length)
                .withElement(SimValue.constant(0, PrimitiveType.INT, null), v1);
            SimArray arr2 = new SimArray(site, PrimitiveType.INT, length)
                .withElement(SimValue.constant(0, PrimitiveType.INT, null), v2);

            SimArray merged = arr1.merge(arr2);
            Set<SimValue> all = merged.getAllElements();
            assertEquals(2, all.size());
        }

        @Test
        void testArrayImmutability() {
            AllocationSite site = AllocationSite.of("[I", 10, "method");
            SimValue length = SimValue.constant(5, PrimitiveType.INT, null);
            SimArray arr = new SimArray(site, PrimitiveType.INT, length);

            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);
            SimArray updated = arr.withElement(SimValue.constant(0, PrimitiveType.INT, null), value);

            assertTrue(arr.getAllElements().isEmpty());
            assertFalse(updated.getAllElements().isEmpty());
        }
    }

    @Nested
    class SimHeapOperationsTests {

        @Test
        void testHeapAllocation() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");

            heap.allocate(site);
            SimObject obj = heap.getObject(site);
            assertNotNull(obj);
            assertEquals(site, obj.getSite());
        }

        @Test
        void testHeapPutField() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            heap.allocate(site);

            FieldKey field = FieldKey.of("com/example/Foo", "value", "I");
            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

            heap.putField(site, field, value);
            Set<SimValue> retrieved = heap.getField(site, field);
            assertTrue(retrieved.contains(value));
        }

        @Test
        void testHeapGetField() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            heap.allocate(site);

            FieldKey field = FieldKey.of("com/example/Foo", "value", "I");
            SimValue value = SimValue.constant(99, PrimitiveType.INT, null);

            heap.putField(site, field, value);
            Set<SimValue> values = heap.getField(site, field);
            assertEquals(1, values.size());
            assertEquals(value, values.iterator().next());
        }

        @Test
        void testHeapGetUnknownField() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("com/example/Foo", 10, "method");
            heap.allocate(site);

            FieldKey unknownField = FieldKey.of("com/example/Foo", "unknown", "I");
            Set<SimValue> values = heap.getField(site, unknownField);
            assertTrue(values.isEmpty());
        }

        @Test
        void testStaticFieldOperations() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            FieldKey staticField = FieldKey.of("com/example/Foo", "INSTANCE", "Lcom/example/Foo;");
            SimValue value = SimValue.ofType(IRType.fromDescriptor("Lcom/example/Foo;"), null);

            heap.putStatic(staticField, value);
            Set<SimValue> retrieved = heap.getStatic(staticField);
            assertTrue(retrieved.contains(value));
        }

        @Test
        void testHeapMerge() {
            SimHeap heap1 = new SimHeap(HeapMode.MUTABLE);
            SimHeap heap2 = new SimHeap(HeapMode.MUTABLE);

            AllocationSite site1 = AllocationSite.of("com/example/Foo", 10, "method");
            AllocationSite site2 = AllocationSite.of("com/example/Bar", 20, "method");

            heap1.allocate(site1);
            heap2.allocate(site2);

            SimHeap merged = heap1.merge(heap2);
            assertTrue(merged.getAllSites().contains(site1));
            assertTrue(merged.getAllSites().contains(site2));
        }

        @Test
        void testArrayAllocation() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("[I", 10, "method");
            SimValue length = SimValue.constant(10, PrimitiveType.INT, null);

            heap.allocateArray(site, PrimitiveType.INT, length);
            SimArray arr = heap.getArray(site);
            assertNotNull(arr);
            assertEquals(PrimitiveType.INT, arr.getElementType());
        }

        @Test
        void testArrayStore() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("[I", 10, "method");
            SimValue length = SimValue.constant(10, PrimitiveType.INT, null);

            heap.allocateArray(site, PrimitiveType.INT, length);

            SimValue index = SimValue.constant(5, PrimitiveType.INT, null);
            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

            heap.arrayStore(site, index, value);
            Set<SimValue> loaded = heap.arrayLoad(site, index);
            assertTrue(loaded.contains(value));
        }

        @Test
        void testGetAllSites() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site1 = AllocationSite.of("Foo", 1, "m");
            AllocationSite site2 = AllocationSite.of("Bar", 2, "m");

            heap.allocate(site1);
            heap.allocate(site2);

            Set<AllocationSite> sites = heap.getAllSites();
            assertEquals(2, sites.size());
            assertTrue(sites.contains(site1));
            assertTrue(sites.contains(site2));
        }

        @Test
        void testMarkEscaped() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);

            assertFalse(heap.hasEscaped(site));
            heap.markEscaped(site);
            assertTrue(heap.hasEscaped(site));
        }

        @Test
        void testHeapCopy() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);

            FieldKey field = FieldKey.of("Foo", "x", "I");
            heap.putField(site, field, SimValue.constant(10, PrimitiveType.INT, null));

            SimHeap copy = heap.copy();
            copy.putField(site, field, SimValue.constant(20, PrimitiveType.INT, null));

            Set<SimValue> origValues = heap.getField(site, field);
            Set<SimValue> copyValues = copy.getField(site, field);

            assertEquals(1, origValues.size());
            assertEquals(2, copyValues.size());
        }
    }

    @Nested
    class SimValueBuilderTests {

        @Test
        void testBuildPrimitive() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            SimValue value = SimValueBuilder.forType(PrimitiveType.INT)
                .withIntValue(42)
                .build(heap);

            assertEquals(42, value.getConstantValue());
        }

        @Test
        void testBuildLongPrimitive() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            SimValue value = SimValueBuilder.forType(PrimitiveType.LONG)
                .withLongValue(123456789L)
                .build(heap);

            assertEquals(123456789L, value.getConstantValue());
        }

        @Test
        void testBuildObject() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("com/example/Foo", 1, "test");

            SimValue value = SimValueBuilder.forClass("com/example/Foo")
                .withAllocationSite(site)
                .build(heap);

            assertTrue(value.getPointsTo().contains(site));
        }

        @Test
        void testBuildWithFields() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            SimValue obj = SimValueBuilder.forClass("com/example/User")
                .withField("age", 25)
                .withField("active", true)
                .build(heap);

            assertFalse(obj.getPointsTo().isEmpty());
        }

        @Test
        void testBuildNestedObjects() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            SimValue outer = SimValueBuilder.forClass("com/example/Outer")
                .withField("inner", SimValueBuilder.forClass("com/example/Inner")
                    .withField("value", 100))
                .build(heap);

            assertNotNull(outer);
            assertFalse(outer.getPointsTo().isEmpty());
        }

        @Test
        void testBuildArray() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
            SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

            SimValue arr = SimValueBuilder.forArray(PrimitiveType.INT)
                .asArray(v1, v2)
                .build(heap);

            assertFalse(arr.getPointsTo().isEmpty());
        }

        @Test
        void testBuildNullable() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            SimValue value = SimValueBuilder.forClass("com/example/Foo")
                .nullable()
                .build(heap);

            assertNotNull(value);
            assertFalse(value.getPointsTo().isEmpty());
        }

        @Test
        void testBuildDefinitelyNull() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            SimValue value = SimValueBuilder.forClass("com/example/Foo")
                .definitelyNull()
                .build(heap);

            assertTrue(value.isDefinitelyNull());
        }

        @Test
        void testBuildDefinitelyNotNull() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            SimValue value = SimValueBuilder.forClass("com/example/Foo")
                .definitelyNotNull()
                .build(heap);

            assertTrue(value.isDefinitelyNotNull());
        }

        @Test
        void testBuilderChaining() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            SimValue value = SimValueBuilder.forClass("com/example/Config")
                .withField("host", "localhost")
                .withField("port", 8080)
                .withField("enabled", true)
                .definitelyNotNull()
                .build(heap);

            assertNotNull(value);
            assertTrue(value.isDefinitelyNotNull());
        }
    }

    @Nested
    class EscapeAnalyzerTests {

        @Test
        void testNoEscape() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "method");
            heap.allocate(site);

            EscapeAnalyzer analyzer = new EscapeAnalyzer(heap);
            assertEquals(EscapeAnalyzer.EscapeState.NO_ESCAPE, analyzer.analyze(site));
        }

        @Test
        void testGlobalEscape() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "method");
            heap.allocate(site);
            heap.markEscaped(site);

            EscapeAnalyzer analyzer = new EscapeAnalyzer(heap);
            assertEquals(EscapeAnalyzer.EscapeState.GLOBAL_ESCAPE, analyzer.analyze(site));
        }

        @Test
        void testTransitiveEscape() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            AllocationSite escapedSite = AllocationSite.of("Container", 1, "m");
            AllocationSite reachableSite = AllocationSite.of("Inner", 2, "m");

            heap.allocate(escapedSite);
            heap.allocate(reachableSite);

            FieldKey field = FieldKey.of("Container", "inner", "LInner;");
            heap.putField(escapedSite, field, SimValue.ofAllocation(reachableSite,
                IRType.fromDescriptor("LInner;"), null));

            heap.markEscaped(escapedSite);

            EscapeAnalyzer analyzer = new EscapeAnalyzer(heap);
            assertEquals(EscapeAnalyzer.EscapeState.GLOBAL_ESCAPE, analyzer.analyze(reachableSite));
        }

        @Test
        void testGetNonEscaping() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            AllocationSite escaped = AllocationSite.of("Escaped", 1, "m");
            AllocationSite local = AllocationSite.of("Local", 2, "m");

            heap.allocate(escaped);
            heap.allocate(local);
            heap.markEscaped(escaped);

            EscapeAnalyzer analyzer = new EscapeAnalyzer(heap);
            Set<AllocationSite> nonEscaping = analyzer.getNonEscaping();

            assertFalse(nonEscaping.contains(escaped));
            assertTrue(nonEscaping.contains(local));
        }

        @Test
        void testGetEscaping() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            AllocationSite escaped = AllocationSite.of("Escaped", 1, "m");
            AllocationSite local = AllocationSite.of("Local", 2, "m");

            heap.allocate(escaped);
            heap.allocate(local);
            heap.markEscaped(escaped);

            EscapeAnalyzer analyzer = new EscapeAnalyzer(heap);
            Set<AllocationSite> escaping = analyzer.getEscaping();

            assertTrue(escaping.contains(escaped));
            assertFalse(escaping.contains(local));
        }

        @Test
        void testMayEscape() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);
            heap.markEscaped(site);

            EscapeAnalyzer analyzer = new EscapeAnalyzer(heap);
            assertTrue(analyzer.mayEscape(site));
        }

        @Test
        void testDefinitelyEscapes() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);
            heap.markEscaped(site);

            EscapeAnalyzer analyzer = new EscapeAnalyzer(heap);
            assertTrue(analyzer.definitelyEscapes(site));
        }

        @Test
        void testReachableFrom() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            AllocationSite root = AllocationSite.of("Root", 1, "m");
            AllocationSite child = AllocationSite.of("Child", 2, "m");

            heap.allocate(root);
            heap.allocate(child);

            FieldKey field = FieldKey.of("Root", "child", "LChild;");
            heap.putField(root, field, SimValue.ofAllocation(child,
                IRType.fromDescriptor("LChild;"), null));

            EscapeAnalyzer analyzer = new EscapeAnalyzer(heap);
            Set<AllocationSite> reachable = analyzer.getReachableFrom(root);

            assertTrue(reachable.contains(root));
            assertTrue(reachable.contains(child));
        }
    }

    @Nested
    class PointsToQueryTests {

        @Test
        void testPointsTo() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);

            SimValue ref = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);

            PointsToQuery query = new PointsToQuery(heap);
            Set<AllocationSite> pts = query.pointsTo(ref);
            assertTrue(pts.contains(site));
        }

        @Test
        void testMayPointTo() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);

            SimValue ref = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);

            PointsToQuery query = new PointsToQuery(heap);
            assertTrue(query.mayPointTo(ref, site));
        }

        @Test
        void testMustPointTo() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);

            SimValue ref = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);

            PointsToQuery query = new PointsToQuery(heap);
            assertTrue(query.mustPointTo(ref, site));
        }

        @Test
        void testMayAlias() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);

            SimValue ref1 = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);
            SimValue ref2 = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);

            PointsToQuery query = new PointsToQuery(heap);
            assertTrue(query.mayAlias(ref1, ref2));
        }

        @Test
        void testMayNotAlias() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site1 = AllocationSite.of("Foo", 1, "m");
            AllocationSite site2 = AllocationSite.of("Bar", 2, "m");
            heap.allocate(site1);
            heap.allocate(site2);

            SimValue ref1 = SimValue.ofAllocation(site1, IRType.fromDescriptor("LFoo;"), null);
            SimValue ref2 = SimValue.ofAllocation(site2, IRType.fromDescriptor("LBar;"), null);

            PointsToQuery query = new PointsToQuery(heap);
            assertFalse(query.mayAlias(ref1, ref2));
        }

        @Test
        void testMustAlias() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);

            SimValue ref1 = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);
            SimValue ref2 = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);

            PointsToQuery query = new PointsToQuery(heap);
            assertTrue(query.mustAlias(ref1, ref2));
        }

        @Test
        void testMayBeNull() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            SimValue nullable = SimValue.ofReference(IRType.fromDescriptor("LFoo;"), null,
                Collections.emptySet(), SimValue.NullState.MAYBE_NULL);
            SimValue notNull = SimValueBuilder.forClass("Foo").definitelyNotNull().build(heap);

            PointsToQuery query = new PointsToQuery(heap);
            assertTrue(query.mayBeNull(nullable));
            assertFalse(query.mayBeNull(notNull));
        }

        @Test
        void testIsDefinitelyNull() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            SimValue nullValue = SimValue.ofNull(IRType.fromDescriptor("LFoo;"), null);
            SimValue notNull = SimValueBuilder.forClass("Foo").definitelyNotNull().build(heap);

            PointsToQuery query = new PointsToQuery(heap);
            assertTrue(query.isDefinitelyNull(nullValue));
            assertFalse(query.isDefinitelyNull(notNull));
        }

        @Test
        void testReachableFrom() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            AllocationSite root = AllocationSite.of("Root", 1, "m");
            AllocationSite child = AllocationSite.of("Child", 2, "m");

            heap.allocate(root);
            heap.allocate(child);

            FieldKey field = FieldKey.of("Root", "child", "LChild;");
            SimValue childRef = SimValue.ofAllocation(child, IRType.fromDescriptor("LChild;"), null);
            heap.putField(root, field, childRef);

            SimValue rootRef = SimValue.ofAllocation(root, IRType.fromDescriptor("LRoot;"), null);

            PointsToQuery query = new PointsToQuery(heap);
            Set<SimValue> reachable = query.reachableFrom(rootRef);

            assertTrue(reachable.size() >= 2);
        }

        @Test
        void testGetFieldValues() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);

            FieldKey field = FieldKey.of("Foo", "x", "I");
            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);
            heap.putField(site, field, value);

            SimValue ref = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);

            PointsToQuery query = new PointsToQuery(heap);
            Set<SimValue> values = query.getFieldValues(ref, field);
            assertTrue(values.contains(value));
        }

        @Test
        void testGetArrayElements() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("[I", 1, "m");
            SimValue length = SimValue.constant(5, PrimitiveType.INT, null);
            heap.allocateArray(site, PrimitiveType.INT, length);

            SimValue element = SimValue.constant(99, PrimitiveType.INT, null);
            heap.arrayStore(site, SimValue.constant(0, PrimitiveType.INT, null), element);

            SimValue arrRef = SimValue.ofAllocation(site, IRType.fromDescriptor("[I"), null);

            PointsToQuery query = new PointsToQuery(heap);
            Set<SimValue> elements = query.getArrayElements(arrRef);
            assertTrue(elements.contains(element));
        }

        @Test
        void testGetPointsToSetSize() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);

            SimValue ref = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);

            PointsToQuery query = new PointsToQuery(heap);
            assertEquals(1, query.getPointsToSetSize(ref));
        }

        @Test
        void testIsSingleton() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            heap.allocate(site);

            SimValue ref = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);

            PointsToQuery query = new PointsToQuery(heap);
            assertTrue(query.isSingleton(ref));
        }
    }

    @Nested
    class SimValueEnhancementsTests {

        @Test
        void testOfAllocation() {
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            SimValue ref = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);

            assertTrue(ref.getPointsTo().contains(site));
            assertFalse(ref.mayBeNull());
        }

        @Test
        void testOfNull() {
            SimValue nullRef = SimValue.ofNull(IRType.fromDescriptor("LFoo;"), null);

            assertTrue(nullRef.isDefinitelyNull());
            assertTrue(nullRef.getPointsTo().isEmpty());
        }

        @Test
        void testMergeSimValues() {
            AllocationSite site1 = AllocationSite.of("Foo", 1, "m");
            AllocationSite site2 = AllocationSite.of("Bar", 2, "m");

            SimValue ref1 = SimValue.ofAllocation(site1, IRType.fromDescriptor("LFoo;"), null);
            SimValue ref2 = SimValue.ofAllocation(site2, IRType.fromDescriptor("LBar;"), null);

            SimValue merged = ref1.merge(ref2);
            assertTrue(merged.getPointsTo().contains(site1));
            assertTrue(merged.getPointsTo().contains(site2));
        }

        @Test
        void testMergeCollection() {
            AllocationSite site1 = AllocationSite.of("A", 1, "m");
            AllocationSite site2 = AllocationSite.of("B", 2, "m");
            AllocationSite site3 = AllocationSite.of("C", 3, "m");

            List<SimValue> values = Arrays.asList(
                SimValue.ofAllocation(site1, IRType.fromDescriptor("LA;"), null),
                SimValue.ofAllocation(site2, IRType.fromDescriptor("LB;"), null),
                SimValue.ofAllocation(site3, IRType.fromDescriptor("LC;"), null)
            );

            SimValue merged = SimValue.merge(values);
            assertEquals(3, merged.getPointsTo().size());
        }

        @Test
        void testNullStateDefinitelyNull() {
            SimValue nullVal = SimValue.ofNull(IRType.fromDescriptor("LFoo;"), null);
            assertTrue(nullVal.isDefinitelyNull());
            assertFalse(nullVal.isDefinitelyNotNull());
            assertTrue(nullVal.mayBeNull());
        }

        @Test
        void testNullStateDefinitelyNotNull() {
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            SimValue ref = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);
            assertFalse(ref.isDefinitelyNull());
            assertTrue(ref.isDefinitelyNotNull());
            assertFalse(ref.mayBeNull());
        }

        @Test
        void testNullStateMaybeNull() {
            SimValue nullable = SimValue.ofReference(IRType.fromDescriptor("LFoo;"), null,
                Collections.emptySet(), SimValue.NullState.MAYBE_NULL);
            assertFalse(nullable.isDefinitelyNull());
            assertFalse(nullable.isDefinitelyNotNull());
            assertTrue(nullable.mayBeNull());
        }

        @Test
        void testMergeNullStates() {
            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            SimValue notNull = SimValue.ofAllocation(site, IRType.fromDescriptor("LFoo;"), null);
            SimValue nullVal = SimValue.ofNull(IRType.fromDescriptor("LFoo;"), null);

            SimValue merged = notNull.merge(nullVal);
            assertTrue(merged.mayBeNull());
        }
    }

    @Nested
    class IntegrationTests {

        @Test
        void testObjectCreationAndFieldAccess() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            AllocationSite site = AllocationSite.of("com/example/User", 10, "createUser()V");
            heap.allocate(site);

            FieldKey nameField = FieldKey.of("com/example/User", "name", "Ljava/lang/String;");
            FieldKey ageField = FieldKey.of("com/example/User", "age", "I");

            heap.putField(site, nameField, SimValue.ofType(IRType.fromDescriptor("Ljava/lang/String;"), null));
            heap.putField(site, ageField, SimValue.constant(25, PrimitiveType.INT, null));

            Set<SimValue> nameValues = heap.getField(site, nameField);
            Set<SimValue> ageValues = heap.getField(site, ageField);

            assertEquals(1, nameValues.size());
            assertEquals(1, ageValues.size());
            assertEquals(25, ageValues.iterator().next().getConstantValue());
        }

        @Test
        void testArrayCreationAndAccess() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            AllocationSite arraySite = AllocationSite.of("[I", 5, "init()V");
            SimValue length = SimValue.constant(10, PrimitiveType.INT, null);
            heap.allocateArray(arraySite, PrimitiveType.INT, length);

            for (int i = 0; i < 5; i++) {
                SimValue index = SimValue.constant(i, PrimitiveType.INT, null);
                SimValue value = SimValue.constant(i * 10, PrimitiveType.INT, null);
                heap.arrayStore(arraySite, index, value);
            }

            SimValue loadIndex = SimValue.constant(2, PrimitiveType.INT, null);
            Set<SimValue> loaded = heap.arrayLoad(arraySite, loadIndex);
            assertTrue(loaded.stream().anyMatch(v -> v.isConstant() && Integer.valueOf(20).equals(v.getConstantValue())));
        }

        @Test
        void testHeapMergePreservesPrecision() {
            SimHeap heap1 = new SimHeap(HeapMode.MUTABLE);
            SimHeap heap2 = new SimHeap(HeapMode.MUTABLE);

            AllocationSite site = AllocationSite.of("Foo", 1, "m");
            FieldKey field = FieldKey.of("Foo", "x", "I");

            heap1.allocate(site);
            heap2.allocate(site);

            heap1.putField(site, field, SimValue.constant(1, PrimitiveType.INT, null));
            heap2.putField(site, field, SimValue.constant(2, PrimitiveType.INT, null));

            SimHeap merged = heap1.merge(heap2);
            Set<SimValue> values = merged.getField(site, field);

            assertEquals(2, values.size());
        }

        @Test
        void testEscapeAnalysisIntegration() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            AllocationSite localSite = AllocationSite.of("Local", 1, "m");
            AllocationSite escapedSite = AllocationSite.of("Escaped", 2, "m");

            heap.allocate(localSite);
            heap.allocate(escapedSite);
            heap.markEscaped(escapedSite);

            EscapeAnalyzer analyzer = new EscapeAnalyzer(heap);

            assertEquals(EscapeAnalyzer.EscapeState.NO_ESCAPE, analyzer.analyze(localSite));
            assertEquals(EscapeAnalyzer.EscapeState.GLOBAL_ESCAPE, analyzer.analyze(escapedSite));
        }

        @Test
        void testPointsToQueryIntegration() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            AllocationSite site1 = AllocationSite.of("Obj1", 1, "m");
            AllocationSite site2 = AllocationSite.of("Obj2", 2, "m");

            heap.allocate(site1);
            heap.allocate(site2);

            SimValue ref1 = SimValue.ofAllocation(site1, IRType.fromDescriptor("LObj1;"), null);
            SimValue ref2 = SimValue.ofAllocation(site2, IRType.fromDescriptor("LObj2;"), null);
            SimValue refBoth = ref1.merge(ref2);

            PointsToQuery query = new PointsToQuery(heap);

            assertTrue(query.isSingleton(ref1));
            assertTrue(query.isSingleton(ref2));
            assertFalse(query.isSingleton(refBoth));
            assertEquals(2, query.getPointsToSetSize(refBoth));
        }

        @Test
        void testBuilderWithHeapIntegration() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            SimValue config = SimValueBuilder.forClass("com/example/Config")
                .withField("host", "localhost")
                .withField("port", 8080)
                .withField("ssl", true)
                .definitelyNotNull()
                .build(heap);

            assertNotNull(config);
            assertFalse(config.getPointsTo().isEmpty());
            assertTrue(config.isDefinitelyNotNull());

            AllocationSite site = config.getPointsTo().iterator().next();
            assertNotNull(heap.getObject(site));
        }

        @Test
        void testComplexObjectGraph() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            AllocationSite outerSite = AllocationSite.of("Outer", 1, "m");
            AllocationSite innerSite = AllocationSite.of("Inner", 2, "m");
            AllocationSite leafSite = AllocationSite.of("Leaf", 3, "m");

            heap.allocate(outerSite);
            heap.allocate(innerSite);
            heap.allocate(leafSite);

            FieldKey innerField = FieldKey.of("Outer", "inner", "LInner;");
            FieldKey leafField = FieldKey.of("Inner", "leaf", "LLeaf;");

            heap.putField(outerSite, innerField,
                SimValue.ofAllocation(innerSite, IRType.fromDescriptor("LInner;"), null));
            heap.putField(innerSite, leafField,
                SimValue.ofAllocation(leafSite, IRType.fromDescriptor("LLeaf;"), null));

            EscapeAnalyzer analyzer = new EscapeAnalyzer(heap);
            Set<AllocationSite> reachable = analyzer.getReachableFrom(outerSite);

            assertTrue(reachable.contains(outerSite));
            assertTrue(reachable.contains(innerSite));
            assertTrue(reachable.contains(leafSite));
        }

        @Test
        void testStaticFieldTracking() {
            SimHeap heap = new SimHeap(HeapMode.MUTABLE);

            FieldKey staticField = FieldKey.of("com/example/Singleton", "INSTANCE", "Lcom/example/Singleton;");
            AllocationSite site = AllocationSite.of("com/example/Singleton", 1, "<clinit>()V");

            heap.allocate(site);
            SimValue ref = SimValue.ofAllocation(site, IRType.fromDescriptor("Lcom/example/Singleton;"), null);
            heap.putStatic(staticField, ref);

            Set<SimValue> retrieved = heap.getStatic(staticField);
            assertEquals(1, retrieved.size());
            assertTrue(retrieved.iterator().next().getPointsTo().contains(site));
        }

        @Test
        void testMergeWithDifferentObjects() {
            SimHeap heap1 = new SimHeap(HeapMode.MUTABLE);
            SimHeap heap2 = new SimHeap(HeapMode.MUTABLE);

            AllocationSite site1 = AllocationSite.of("A", 1, "m");
            AllocationSite site2 = AllocationSite.of("B", 2, "m");
            AllocationSite site3 = AllocationSite.of("C", 3, "m");

            heap1.allocate(site1);
            heap1.allocate(site2);
            heap2.allocate(site2);
            heap2.allocate(site3);

            SimHeap merged = heap1.merge(heap2);

            assertTrue(merged.getAllSites().contains(site1));
            assertTrue(merged.getAllSites().contains(site2));
            assertTrue(merged.getAllSites().contains(site3));
        }
    }
}
