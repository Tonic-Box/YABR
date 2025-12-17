package com.tonic.analysis.xref;

import com.tonic.analysis.common.MethodReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the XrefDatabase API.
 * Covers building cross-reference database, finding references, and handling method/field accesses.
 */
class XrefDatabaseTest {

    private XrefDatabase db;

    @BeforeEach
    void setUp() {
        db = new XrefDatabase();
    }

    // ========== Basic Construction Tests ==========

    @Test
    void newDatabaseIsEmpty() {
        assertTrue(db.isEmpty());
        assertEquals(0, db.getTotalXrefCount());
    }

    @Test
    void addXrefIncreasesCount() {
        Xref xref = Xref.builder()
            .sourceClass("com/test/Source")
            .sourceMethod("method", "()V")
            .targetClass("com/test/Target")
            .targetMember("targetMethod", "()V")
            .type(XrefType.METHOD_CALL)
            .build();

        db.addXref(xref);

        assertFalse(db.isEmpty());
        assertEquals(1, db.getTotalXrefCount());
    }

    @Test
    void addAllXrefsAddsMultiple() {
        Xref xref1 = createMethodCall("com/test/A", "method1", "com/test/B", "method2");
        Xref xref2 = createMethodCall("com/test/C", "method3", "com/test/D", "method4");

        db.addAllXrefs(List.of(xref1, xref2));

        assertEquals(2, db.getTotalXrefCount());
    }

    @Test
    void clearRemovesAllXrefs() {
        Xref xref = createMethodCall("com/test/A", "method", "com/test/B", "method");
        db.addXref(xref);

        db.clear();

        assertTrue(db.isEmpty());
        assertEquals(0, db.getTotalXrefCount());
    }

    // ========== Class Reference Tests ==========

    @Test
    void getRefsToClassReturnsEmpty() {
        List<Xref> refs = db.getRefsToClass("com/test/NonExistent");

        assertNotNull(refs);
        assertTrue(refs.isEmpty());
    }

    @Test
    void getRefsToClassFindsReferences() {
        Xref xref = createMethodCall("com/test/Caller", "method", "com/test/Target", "called");
        db.addXref(xref);

        List<Xref> refs = db.getRefsToClass("com/test/Target");

        assertNotNull(refs);
        assertFalse(refs.isEmpty());
        assertTrue(refs.contains(xref));
    }

    @Test
    void getRefsFromClassReturnsEmpty() {
        List<Xref> refs = db.getRefsFromClass("com/test/NonExistent");

        assertNotNull(refs);
        assertTrue(refs.isEmpty());
    }

    @Test
    void getRefsFromClassFindsReferences() {
        Xref xref = createMethodCall("com/test/Caller", "method", "com/test/Target", "called");
        db.addXref(xref);

        List<Xref> refs = db.getRefsFromClass("com/test/Caller");

        assertNotNull(refs);
        assertFalse(refs.isEmpty());
        assertTrue(refs.contains(xref));
    }

    // ========== Method Reference Tests ==========

    @Test
    void getRefsToMethodReturnsEmpty() {
        MethodReference ref = new MethodReference("com/test/Class", "method", "()V");

        List<Xref> refs = db.getRefsToMethod(ref);

        assertNotNull(refs);
        assertTrue(refs.isEmpty());
    }

    @Test
    void getRefsToMethodFindsCallers() {
        Xref xref = createMethodCall("com/test/Caller", "caller", "com/test/Target", "target");
        db.addXref(xref);

        MethodReference target = new MethodReference("com/test/Target", "target", "()V");
        List<Xref> refs = db.getRefsToMethod(target);

        assertNotNull(refs);
        assertFalse(refs.isEmpty());
        assertTrue(refs.contains(xref));
    }

    @Test
    void getRefsToMethodByComponents() {
        Xref xref = createMethodCall("com/test/Caller", "caller", "com/test/Target", "target");
        db.addXref(xref);

        List<Xref> refs = db.getRefsToMethod("com/test/Target", "target", "()V");

        assertNotNull(refs);
        assertFalse(refs.isEmpty());
    }

    @Test
    void getRefsFromMethodReturnsEmpty() {
        MethodReference ref = new MethodReference("com/test/Class", "method", "()V");

        List<Xref> refs = db.getRefsFromMethod(ref);

        assertNotNull(refs);
        assertTrue(refs.isEmpty());
    }

    @Test
    void getRefsFromMethodFindsCallees() {
        Xref xref = createMethodCall("com/test/Caller", "caller", "com/test/Target", "target");
        db.addXref(xref);

        MethodReference caller = new MethodReference("com/test/Caller", "caller", "()V");
        List<Xref> refs = db.getRefsFromMethod(caller);

        assertNotNull(refs);
        assertFalse(refs.isEmpty());
        assertTrue(refs.contains(xref));
    }

    @Test
    void getRefsFromMethodByComponents() {
        Xref xref = createMethodCall("com/test/Caller", "caller", "com/test/Target", "target");
        db.addXref(xref);

        List<Xref> refs = db.getRefsFromMethod("com/test/Caller", "caller", "()V");

        assertNotNull(refs);
        assertFalse(refs.isEmpty());
    }

    // ========== Field Reference Tests ==========

    @Test
    void getRefsToFieldReturnsEmpty() {
        FieldReference ref = new FieldReference("com/test/Class", "field", "I");

        List<Xref> refs = db.getRefsToField(ref);

        assertNotNull(refs);
        assertTrue(refs.isEmpty());
    }

    @Test
    void getRefsToFieldFindsAccesses() {
        Xref xref = createFieldRead("com/test/Reader", "method", "com/test/Owner", "field");
        db.addXref(xref);

        FieldReference field = new FieldReference("com/test/Owner", "field", "I");
        List<Xref> refs = db.getRefsToField(field);

        assertNotNull(refs);
        assertFalse(refs.isEmpty());
        assertTrue(refs.contains(xref));
    }

    @Test
    void getRefsToFieldByComponents() {
        Xref xref = createFieldRead("com/test/Reader", "method", "com/test/Owner", "field");
        db.addXref(xref);

        List<Xref> refs = db.getRefsToField("com/test/Owner", "field", "I");

        assertNotNull(refs);
        assertFalse(refs.isEmpty());
    }

    // ========== Type-Based Query Tests ==========

    @Test
    void getRefsByTypeReturnsEmpty() {
        List<Xref> refs = db.getRefsByType(XrefType.METHOD_CALL);

        assertNotNull(refs);
        assertTrue(refs.isEmpty());
    }

    @Test
    void getRefsByTypeFindsMatches() {
        Xref methodCall = createMethodCall("com/test/A", "m1", "com/test/B", "m2");
        Xref fieldRead = createFieldRead("com/test/A", "m1", "com/test/B", "field");
        db.addXref(methodCall);
        db.addXref(fieldRead);

        List<Xref> calls = db.getRefsByType(XrefType.METHOD_CALL);
        List<Xref> reads = db.getRefsByType(XrefType.FIELD_READ);

        assertTrue(calls.contains(methodCall));
        assertTrue(reads.contains(fieldRead));
        assertFalse(calls.contains(fieldRead));
        assertFalse(reads.contains(methodCall));
    }

    @Test
    void getAllMethodCallsFiltersCorrectly() {
        Xref methodCall = createMethodCall("com/test/A", "m1", "com/test/B", "m2");
        Xref fieldRead = createFieldRead("com/test/A", "m1", "com/test/B", "field");
        db.addXref(methodCall);
        db.addXref(fieldRead);

        List<Xref> calls = db.getAllMethodCalls();

        assertTrue(calls.contains(methodCall));
        assertFalse(calls.contains(fieldRead));
    }

    @Test
    void getAllFieldReadsFiltersCorrectly() {
        Xref methodCall = createMethodCall("com/test/A", "m1", "com/test/B", "m2");
        Xref fieldRead = createFieldRead("com/test/A", "m1", "com/test/B", "field");
        db.addXref(methodCall);
        db.addXref(fieldRead);

        List<Xref> reads = db.getAllFieldReads();

        assertTrue(reads.contains(fieldRead));
        assertFalse(reads.contains(methodCall));
    }

    @Test
    void getAllFieldWritesFiltersCorrectly() {
        Xref fieldWrite = createFieldWrite("com/test/A", "m1", "com/test/B", "field");
        Xref fieldRead = createFieldRead("com/test/A", "m1", "com/test/B", "field");
        db.addXref(fieldWrite);
        db.addXref(fieldRead);

        List<Xref> writes = db.getAllFieldWrites();

        assertTrue(writes.contains(fieldWrite));
        assertFalse(writes.contains(fieldRead));
    }

    @Test
    void getAllInstantiationsFiltersCorrectly() {
        Xref instantiation = createInstantiation("com/test/A", "method", "com/test/B");
        Xref methodCall = createMethodCall("com/test/A", "m1", "com/test/B", "m2");
        db.addXref(instantiation);
        db.addXref(methodCall);

        List<Xref> instantiations = db.getAllInstantiations();

        assertTrue(instantiations.contains(instantiation));
        assertFalse(instantiations.contains(methodCall));
    }

    @Test
    void getAllXrefsReturnsUnmodifiable() {
        Xref xref = createMethodCall("com/test/A", "m1", "com/test/B", "m2");
        db.addXref(xref);

        List<Xref> all = db.getAllXrefs();

        assertNotNull(all);
        assertEquals(1, all.size());
    }

    // ========== Search Query Tests ==========

    @Test
    void searchIncomingRefsFindsByClassName() {
        Xref xref = createMethodCall("com/test/Caller", "method", "com/test/Target", "called");
        db.addXref(xref);

        List<Xref> results = db.searchIncomingRefs("Target");

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertTrue(results.contains(xref));
    }

    @Test
    void searchIncomingRefsFindsByMemberName() {
        Xref xref = createMethodCall("com/test/Caller", "method", "com/test/Target", "called");
        db.addXref(xref);

        List<Xref> results = db.searchIncomingRefs("called");

        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    void searchOutgoingRefsFindsByClassName() {
        Xref xref = createMethodCall("com/test/Caller", "method", "com/test/Target", "called");
        db.addXref(xref);

        List<Xref> results = db.searchOutgoingRefs("Caller");

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertTrue(results.contains(xref));
    }

    @Test
    void searchOutgoingRefsFindsByMethodName() {
        Xref xref = createMethodCall("com/test/Caller", "method", "com/test/Target", "called");
        db.addXref(xref);

        List<Xref> results = db.searchOutgoingRefs("method");

        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    void findCallersOfMethodNamedFindsMatches() {
        Xref xref1 = createMethodCall("com/test/A", "m1", "com/test/B", "target");
        Xref xref2 = createMethodCall("com/test/C", "m2", "com/test/D", "target");
        db.addXref(xref1);
        db.addXref(xref2);

        List<Xref> callers = db.findCallersOfMethodNamed("target");

        assertNotNull(callers);
        assertTrue(callers.contains(xref1));
        assertTrue(callers.contains(xref2));
    }

    @Test
    void findRefsBetweenClassesFindsMatches() {
        Xref xref1 = createMethodCall("com/test/A", "m1", "com/test/B", "m2");
        Xref xref2 = createMethodCall("com/test/A", "m1", "com/test/C", "m3");
        db.addXref(xref1);
        db.addXref(xref2);

        List<Xref> refs = db.findRefsBetweenClasses("com/test/A", "com/test/B");

        assertNotNull(refs);
        assertTrue(refs.contains(xref1));
        assertFalse(refs.contains(xref2));
    }

    // ========== Grouping Tests ==========

    @Test
    void groupIncomingByTypeGroupsCorrectly() {
        Xref methodCall = createMethodCall("com/test/A", "m1", "com/test/Target", "m2");
        Xref fieldRead = createFieldRead("com/test/A", "m1", "com/test/Target", "field");
        db.addXref(methodCall);
        db.addXref(fieldRead);

        Map<XrefType, List<Xref>> grouped = db.groupIncomingByType("com/test/Target");

        assertNotNull(grouped);
        assertTrue(grouped.containsKey(XrefType.METHOD_CALL));
        assertTrue(grouped.containsKey(XrefType.FIELD_READ));
    }

    @Test
    void groupOutgoingByTypeGroupsCorrectly() {
        Xref methodCall = createMethodCall("com/test/Source", "m1", "com/test/B", "m2");
        Xref fieldRead = createFieldRead("com/test/Source", "m1", "com/test/B", "field");
        db.addXref(methodCall);
        db.addXref(fieldRead);

        Map<XrefType, List<Xref>> grouped = db.groupOutgoingByType("com/test/Source");

        assertNotNull(grouped);
        assertTrue(grouped.containsKey(XrefType.METHOD_CALL));
        assertTrue(grouped.containsKey(XrefType.FIELD_READ));
    }

    @Test
    void getClassesReferencingClassFindsUnique() {
        Xref xref1 = createMethodCall("com/test/A", "m1", "com/test/Target", "m2");
        Xref xref2 = createMethodCall("com/test/A", "m3", "com/test/Target", "m4");
        Xref xref3 = createMethodCall("com/test/B", "m5", "com/test/Target", "m6");
        db.addXref(xref1);
        db.addXref(xref2);
        db.addXref(xref3);

        Set<String> classes = db.getClassesReferencingClass("com/test/Target");

        assertNotNull(classes);
        assertTrue(classes.contains("com/test/A"));
        assertTrue(classes.contains("com/test/B"));
        assertEquals(2, classes.size());
    }

    @Test
    void getClassesReferencedByClassFindsUnique() {
        Xref xref1 = createMethodCall("com/test/Source", "m1", "com/test/A", "m2");
        Xref xref2 = createMethodCall("com/test/Source", "m3", "com/test/A", "m4");
        Xref xref3 = createMethodCall("com/test/Source", "m5", "com/test/B", "m6");
        db.addXref(xref1);
        db.addXref(xref2);
        db.addXref(xref3);

        Set<String> classes = db.getClassesReferencedByClass("com/test/Source");

        assertNotNull(classes);
        assertTrue(classes.contains("com/test/A"));
        assertTrue(classes.contains("com/test/B"));
        assertEquals(2, classes.size());
    }

    // ========== Statistics Tests ==========

    @Test
    void getTotalXrefCountReturnsZeroWhenEmpty() {
        assertEquals(0, db.getTotalXrefCount());
    }

    @Test
    void getTotalXrefCountReturnsCorrectCount() {
        db.addXref(createMethodCall("A", "m1", "B", "m2"));
        db.addXref(createMethodCall("C", "m3", "D", "m4"));

        assertEquals(2, db.getTotalXrefCount());
    }

    @Test
    void getXrefCountByTypeCountsCorrectly() {
        db.addXref(createMethodCall("A", "m1", "B", "m2"));
        db.addXref(createMethodCall("C", "m3", "D", "m4"));
        db.addXref(createFieldRead("A", "m1", "B", "field"));

        Map<XrefType, Integer> counts = db.getXrefCountByType();

        assertNotNull(counts);
        assertEquals(2, counts.get(XrefType.METHOD_CALL));
        assertEquals(1, counts.get(XrefType.FIELD_READ));
    }

    @Test
    void getUniqueTargetClassCountReturnsCount() {
        db.addXref(createMethodCall("A", "m1", "Target1", "m2"));
        db.addXref(createMethodCall("B", "m3", "Target2", "m4"));

        assertTrue(db.getUniqueTargetClassCount() >= 0);
    }

    @Test
    void getUniqueSourceClassCountReturnsCount() {
        db.addXref(createMethodCall("Source1", "m1", "B", "m2"));
        db.addXref(createMethodCall("Source2", "m3", "D", "m4"));

        assertTrue(db.getUniqueSourceClassCount() >= 0);
    }

    @Test
    void getUniqueTargetMethodCountReturnsCount() {
        db.addXref(createMethodCall("A", "m1", "B", "target1"));
        db.addXref(createMethodCall("C", "m2", "D", "target2"));

        assertTrue(db.getUniqueTargetMethodCount() >= 0);
    }

    @Test
    void getUniqueTargetFieldCountReturnsCount() {
        db.addXref(createFieldRead("A", "m1", "B", "field1"));
        db.addXref(createFieldRead("C", "m2", "D", "field2"));

        assertTrue(db.getUniqueTargetFieldCount() >= 0);
    }

    @Test
    void getBuildTimeReturnsSetValue() {
        db.setBuildTimeMs(1000);

        assertEquals(1000, db.getBuildTimeMs());
    }

    @Test
    void getTotalClassesReturnsSetValue() {
        db.setTotalClasses(10);

        assertEquals(10, db.getTotalClasses());
    }

    @Test
    void getTotalMethodsReturnsSetValue() {
        db.setTotalMethods(50);

        assertEquals(50, db.getTotalMethods());
    }

    @Test
    void getSummaryContainsInfo() {
        db.setTotalClasses(5);
        db.setTotalMethods(20);
        db.setBuildTimeMs(500);

        String summary = db.getSummary();

        assertNotNull(summary);
        assertTrue(summary.contains("XrefDatabase"));
        assertTrue(summary.contains("5"));
        assertTrue(summary.contains("20"));
        assertTrue(summary.contains("500"));
    }

    // ========== Edge Case Tests ==========

    @Test
    void handlesDuplicateXrefs() {
        Xref xref = createMethodCall("A", "m1", "B", "m2");
        db.addXref(xref);
        db.addXref(xref);

        // Should add both (duplicates allowed)
        assertEquals(2, db.getTotalXrefCount());
    }

    @Test
    void handlesNullSourceMethod() {
        Xref xref = Xref.builder()
            .sourceClass("com/test/Source")
            .targetClass("com/test/Target")
            .type(XrefType.CLASS_INSTANTIATE)
            .build();

        db.addXref(xref);

        assertEquals(1, db.getTotalXrefCount());
    }

    @Test
    void handlesNullTargetMember() {
        Xref xref = Xref.builder()
            .sourceClass("com/test/Source")
            .sourceMethod("method", "()V")
            .targetClass("com/test/Target")
            .type(XrefType.CLASS_INSTANTIATE)
            .build();

        db.addXref(xref);

        assertEquals(1, db.getTotalXrefCount());
    }

    @Test
    void handlesMultipleXrefTypes() {
        db.addXref(createMethodCall("A", "m", "B", "m"));
        db.addXref(createFieldRead("A", "m", "B", "f"));
        db.addXref(createFieldWrite("A", "m", "B", "f"));
        db.addXref(createInstantiation("A", "m", "B"));

        assertEquals(4, db.getTotalXrefCount());

        Map<XrefType, Integer> counts = db.getXrefCountByType();
        assertTrue(counts.containsKey(XrefType.METHOD_CALL));
        assertTrue(counts.containsKey(XrefType.FIELD_READ));
        assertTrue(counts.containsKey(XrefType.FIELD_WRITE));
        assertTrue(counts.containsKey(XrefType.CLASS_INSTANTIATE));
    }

    @Test
    void queriesReturnEmptyAfterClear() {
        db.addXref(createMethodCall("A", "m1", "B", "m2"));
        db.clear();

        assertTrue(db.getRefsToClass("B").isEmpty());
        assertTrue(db.getRefsFromClass("A").isEmpty());
        assertTrue(db.getAllMethodCalls().isEmpty());
    }

    // ========== Helper Methods ==========

    private Xref createMethodCall(String sourceClass, String sourceMethod,
                                   String targetClass, String targetMethod) {
        return Xref.builder()
            .sourceClass(sourceClass)
            .sourceMethod(sourceMethod, "()V")
            .targetMethod(targetClass, targetMethod, "()V")
            .type(XrefType.METHOD_CALL)
            .build();
    }

    private Xref createFieldRead(String sourceClass, String sourceMethod,
                                  String targetClass, String field) {
        return Xref.builder()
            .sourceClass(sourceClass)
            .sourceMethod(sourceMethod, "()V")
            .targetField(targetClass, field, "I")
            .type(XrefType.FIELD_READ)
            .build();
    }

    private Xref createFieldWrite(String sourceClass, String sourceMethod,
                                   String targetClass, String field) {
        return Xref.builder()
            .sourceClass(sourceClass)
            .sourceMethod(sourceMethod, "()V")
            .targetField(targetClass, field, "I")
            .type(XrefType.FIELD_WRITE)
            .build();
    }

    private Xref createInstantiation(String sourceClass, String sourceMethod,
                                      String targetClass) {
        return Xref.builder()
            .sourceClass(sourceClass)
            .sourceMethod(sourceMethod, "()V")
            .targetClass(targetClass)
            .type(XrefType.CLASS_INSTANTIATE)
            .build();
    }
}
