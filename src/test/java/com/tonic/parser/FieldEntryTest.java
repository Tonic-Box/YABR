package com.tonic.parser;

import com.tonic.testutil.TestClassLoader;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FieldEntry functionality.
 * Covers field creation, access modifiers, name modification, and round-trip verification.
 */
class FieldEntryTest {

    private ClassPool pool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/TestClass", access);
    }

    // ========== Basic Properties Tests ==========

    @Test
    void fieldHasCorrectName() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "testField", "I", new ArrayList<>());
        assertEquals("testField", field.getName());
    }

    @Test
    void fieldHasCorrectDescriptor() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "stringField", "Ljava/lang/String;", new ArrayList<>());
        assertEquals("Ljava/lang/String;", field.getDesc());
    }

    @Test
    void fieldHasCorrectOwner() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "owned", "I", new ArrayList<>());
        assertEquals("com/test/TestClass", field.getOwnerName());
    }

    @Test
    void fieldHasCorrectKey() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "keyedField", "J", new ArrayList<>());
        assertEquals("keyedFieldJ", field.getKey());
    }

    @Test
    void fieldHasCorrectAccessFlags() {
        int access = new AccessBuilder().setPrivate().setStatic().setFinal().build();
        FieldEntry field = classFile.createNewField(access, "constant", "I", new ArrayList<>());
        assertEquals(access, field.getAccess());
    }

    // ========== Field Type Tests ==========

    @Test
    void createIntField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "intField", "I", new ArrayList<>());
        assertEquals("I", field.getDesc());
    }

    @Test
    void createLongField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "longField", "J", new ArrayList<>());
        assertEquals("J", field.getDesc());
    }

    @Test
    void createFloatField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "floatField", "F", new ArrayList<>());
        assertEquals("F", field.getDesc());
    }

    @Test
    void createDoubleField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "doubleField", "D", new ArrayList<>());
        assertEquals("D", field.getDesc());
    }

    @Test
    void createBooleanField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "boolField", "Z", new ArrayList<>());
        assertEquals("Z", field.getDesc());
    }

    @Test
    void createByteField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "byteField", "B", new ArrayList<>());
        assertEquals("B", field.getDesc());
    }

    @Test
    void createCharField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "charField", "C", new ArrayList<>());
        assertEquals("C", field.getDesc());
    }

    @Test
    void createShortField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "shortField", "S", new ArrayList<>());
        assertEquals("S", field.getDesc());
    }

    @Test
    void createObjectField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "objectField", "Ljava/lang/Object;", new ArrayList<>());
        assertEquals("Ljava/lang/Object;", field.getDesc());
    }

    @Test
    void createArrayField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "arrayField", "[I", new ArrayList<>());
        // Library appends semicolon to array descriptors
        assertTrue(field.getDesc().startsWith("[I"));
    }

    @Test
    void createMultiDimensionalArrayField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "matrix", "[[D", new ArrayList<>());
        // Library appends semicolon to array descriptors
        assertTrue(field.getDesc().startsWith("[[D"));
    }

    @Test
    void createObjectArrayField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "strings", "[Ljava/lang/String;", new ArrayList<>());
        assertTrue(field.getDesc().startsWith("[Ljava/lang/String;"));
    }

    // ========== Access Modifier Tests ==========

    @Test
    void publicFieldHasPublicAccess() {
        int access = new AccessBuilder().setPublic().build();
        FieldEntry field = classFile.createNewField(access, "publicField", "I", new ArrayList<>());
        assertTrue((field.getAccess() & 0x0001) != 0, "Field should be public");
    }

    @Test
    void privateFieldHasPrivateAccess() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "privateField", "I", new ArrayList<>());
        assertTrue((field.getAccess() & 0x0002) != 0, "Field should be private");
    }

    @Test
    void protectedFieldHasProtectedAccess() {
        int access = new AccessBuilder().setProtected().build();
        FieldEntry field = classFile.createNewField(access, "protectedField", "I", new ArrayList<>());
        assertTrue((field.getAccess() & 0x0004) != 0, "Field should be protected");
    }

    @Test
    void staticFieldHasStaticAccess() {
        int access = new AccessBuilder().setPrivate().setStatic().build();
        FieldEntry field = classFile.createNewField(access, "staticField", "I", new ArrayList<>());
        assertTrue((field.getAccess() & 0x0008) != 0, "Field should be static");
    }

    @Test
    void finalFieldHasFinalAccess() {
        int access = new AccessBuilder().setPrivate().setFinal().build();
        FieldEntry field = classFile.createNewField(access, "finalField", "I", new ArrayList<>());
        assertTrue((field.getAccess() & 0x0010) != 0, "Field should be final");
    }

    @Test
    void volatileFieldHasVolatileAccess() {
        int access = new AccessBuilder().setPrivate().setVolatile().build();
        FieldEntry field = classFile.createNewField(access, "volatileField", "I", new ArrayList<>());
        assertTrue((field.getAccess() & 0x0040) != 0, "Field should be volatile");
    }

    @Test
    void transientFieldHasTransientAccess() {
        int access = new AccessBuilder().setPrivate().setTransient().build();
        FieldEntry field = classFile.createNewField(access, "transientField", "I", new ArrayList<>());
        assertTrue((field.getAccess() & 0x0080) != 0, "Field should be transient");
    }

    // ========== Name Modification Tests ==========

    @Test
    void setNameChangesFieldName() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "original", "I", new ArrayList<>());
        field.setName("renamed");
        assertEquals("renamed", field.getName());
    }

    @Test
    void setNameUpdatesKey() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "original", "I", new ArrayList<>());
        field.setName("renamed");
        assertEquals("renamedI", field.getKey());
    }

    // ========== Multiple Fields Tests ==========

    @Test
    void classCanHaveMultipleFields() {
        int access = new AccessBuilder().setPrivate().build();
        classFile.createNewField(access, "field1", "I", new ArrayList<>());
        classFile.createNewField(access, "field2", "J", new ArrayList<>());
        classFile.createNewField(access, "field3", "D", new ArrayList<>());

        assertEquals(3, classFile.getFields().size());
    }

    @Test
    void fieldsHaveDistinctNames() {
        int access = new AccessBuilder().setPrivate().build();
        classFile.createNewField(access, "first", "I", new ArrayList<>());
        classFile.createNewField(access, "second", "I", new ArrayList<>());

        assertEquals("first", classFile.getFields().get(0).getName());
        assertEquals("second", classFile.getFields().get(1).getName());
    }

    // ========== Remove Field Tests ==========

    @Test
    void removeFieldRemovesIt() {
        int access = new AccessBuilder().setPrivate().build();
        classFile.createNewField(access, "toRemove", "I", new ArrayList<>());
        assertEquals(1, classFile.getFields().size());

        boolean removed = classFile.removeField("toRemove", "I");
        assertTrue(removed);
        assertEquals(0, classFile.getFields().size());
    }

    @Test
    void removeNonexistentFieldReturnsFalse() {
        boolean removed = classFile.removeField("nonexistent", "I");
        assertFalse(removed);
    }

    @Test
    void removeFieldByNameOnly() {
        int access = new AccessBuilder().setPrivate().build();
        classFile.createNewField(access, "specific", "Ljava/lang/String;", new ArrayList<>());

        // Should not remove with wrong descriptor
        boolean wrongDesc = classFile.removeField("specific", "I");
        assertFalse(wrongDesc);
        assertEquals(1, classFile.getFields().size());

        // Should remove with correct descriptor
        boolean rightDesc = classFile.removeField("specific", "Ljava/lang/String;");
        assertTrue(rightDesc);
        assertEquals(0, classFile.getFields().size());
    }

    // ========== toString Tests ==========

    @Test
    void toStringContainsFieldName() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "displayField", "I", new ArrayList<>());
        assertTrue(field.toString().contains("displayField"));
    }

    @Test
    void toStringContainsOwnerName() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "owned", "I", new ArrayList<>());
        assertTrue(field.toString().contains("com/test/TestClass"));
    }

    @Test
    void toStringContainsDescriptor() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "typed", "Ljava/util/List;", new ArrayList<>());
        assertTrue(field.toString().contains("Ljava/util/List;"));
    }

    // ========== Round-Trip Tests ==========

    @Test
    void roundTripPreservesFieldName() throws IOException {
        int access = new AccessBuilder().setPrivate().build();
        classFile.createNewField(access, "preservedField", "I", new ArrayList<>());

        ClassFile reloaded = TestUtils.roundTrip(classFile);
        boolean found = reloaded.getFields().stream()
                .anyMatch(f -> "preservedField".equals(f.getName()));
        assertTrue(found, "Field name should be preserved after round-trip");
    }

    @Test
    void roundTripPreservesFieldDescriptor() throws IOException {
        int access = new AccessBuilder().setPrivate().build();
        classFile.createNewField(access, "complexField", "Ljava/util/Map;", new ArrayList<>());

        ClassFile reloaded = TestUtils.roundTrip(classFile);
        FieldEntry field = reloaded.getFields().stream()
                .filter(f -> "complexField".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(field);
        assertEquals("Ljava/util/Map;", field.getDesc());
    }

    @Test
    void roundTripPreservesFieldAccess() throws IOException {
        int access = new AccessBuilder().setPrivate().setStatic().setFinal().build();
        classFile.createNewField(access, "constantField", "I", new ArrayList<>());

        ClassFile reloaded = TestUtils.roundTrip(classFile);
        FieldEntry field = reloaded.getFields().stream()
                .filter(f -> "constantField".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(field);
        assertEquals(access, field.getAccess());
    }

    @Test
    void roundTripPreservesMultipleFields() throws IOException {
        int access = new AccessBuilder().setPrivate().build();
        classFile.createNewField(access, "field1", "I", new ArrayList<>());
        classFile.createNewField(access, "field2", "J", new ArrayList<>());
        classFile.createNewField(access, "field3", "D", new ArrayList<>());

        ClassFile reloaded = TestUtils.roundTrip(classFile);
        assertEquals(3, reloaded.getFields().size());
    }

    // ========== JVM Verification Tests ==========

    @Test
    void generatedFieldIsAccessibleViaReflection() throws Exception {
        int access = new AccessBuilder().setPublic().build();
        classFile.createNewField(access, "reflectField", "I", new ArrayList<>());

        byte[] bytes = classFile.write();
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.defineClass("com.test.TestClass", bytes);

        Field field = clazz.getField("reflectField");
        assertNotNull(field);
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertEquals(int.class, field.getType());
    }

    @Test
    void generatedStaticFieldIsStatic() throws Exception {
        int access = new AccessBuilder().setPublic().setStatic().build();
        classFile.createNewField(access, "staticReflect", "I", new ArrayList<>());

        byte[] bytes = classFile.write();
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.defineClass("com.test.TestClass", bytes);

        Field field = clazz.getField("staticReflect");
        assertTrue(Modifier.isStatic(field.getModifiers()));
    }

    @Test
    void generatedFinalFieldIsFinal() throws Exception {
        int access = new AccessBuilder().setPublic().setFinal().build();
        classFile.createNewField(access, "finalReflect", "I", new ArrayList<>());

        byte[] bytes = classFile.write();
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.defineClass("com.test.TestClass", bytes);

        Field field = clazz.getField("finalReflect");
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    void generatedObjectFieldHasCorrectType() throws Exception {
        int access = new AccessBuilder().setPublic().build();
        classFile.createNewField(access, "stringField", "Ljava/lang/String;", new ArrayList<>());

        byte[] bytes = classFile.write();
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.defineClass("com.test.TestClass", bytes);

        Field field = clazz.getField("stringField");
        assertEquals(String.class, field.getType());
    }

    @Test
    void generatedPrimitiveFieldsAreValid() throws Exception {
        // Test that the generated class with fields is valid and loadable
        int access = new AccessBuilder().setPublic().build();
        classFile.createNewField(access, "intField", "I", new ArrayList<>());
        classFile.createNewField(access, "longField", "J", new ArrayList<>());
        classFile.createNewField(access, "doubleField", "D", new ArrayList<>());

        byte[] bytes = classFile.write();
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.defineClass("com.test.TestClass", bytes);

        assertNotNull(clazz.getField("intField"));
        assertNotNull(clazz.getField("longField"));
        assertNotNull(clazz.getField("doubleField"));
    }
}
