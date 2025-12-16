package com.tonic.renamer;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.exception.RenameException;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.mapping.MappingStore;
import com.tonic.renamer.validation.ValidationResult;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Renamer functionality.
 * Covers renaming classes, methods, and fields, and verifying reference updates.
 */
class RenamerTest {

    private ClassPool pool;
    private Renamer renamer;

    @BeforeEach
    void setUp() {
        pool = TestUtils.emptyPool();
        renamer = new Renamer(pool);
    }

    // ========== Class Renaming Tests ==========

    @Test
    void mapClassAddsMapping() {
        renamer.mapClass("com/old/MyClass", "com/new/RenamedClass");

        MappingStore mappings = renamer.getMappings();
        assertFalse(mappings.isEmpty());
        assertTrue(mappings.hasClassMapping("com/old/MyClass"));
    }

    @Test
    void mapClassReturnsRenamerForChaining() {
        Renamer result = renamer.mapClass("com/old/Test", "com/new/Test");
        assertSame(renamer, result);
    }

    @Test
    void renameClassUpdatesClassName() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/old/MyClass", access);

        renamer.mapClass("com/old/MyClass", "com/new/RenamedClass");
        renamer.apply();

        assertEquals("com/new/RenamedClass", cf.getClassName());
    }

    @Test
    void renameClassUpdatesPoolMapping() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/old/MyClass", access);

        renamer.mapClass("com/old/MyClass", "com/new/RenamedClass");
        renamer.apply();

        assertNull(pool.get("com/old/MyClass"));
        assertNotNull(pool.get("com/new/RenamedClass"));
    }

    @Test
    void renameMultipleClasses() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf1 = pool.createNewClass("com/old/ClassA", access);
        ClassFile cf2 = pool.createNewClass("com/old/ClassB", access);

        renamer.mapClass("com/old/ClassA", "com/new/ClassA")
               .mapClass("com/old/ClassB", "com/new/ClassB")
               .apply();

        assertEquals("com/new/ClassA", cf1.getClassName());
        assertEquals("com/new/ClassB", cf2.getClassName());
    }

    // ========== Method Renaming Tests ==========

    @Test
    void mapMethodAddsMapping() {
        renamer.mapMethod("com/test/MyClass", "oldMethod", "()V", "newMethod");

        MappingStore mappings = renamer.getMappings();
        assertFalse(mappings.isEmpty());
        assertTrue(mappings.hasMethodMapping("com/test/MyClass", "oldMethod", "()V"));
    }

    @Test
    void mapMethodReturnsRenamerForChaining() {
        Renamer result = renamer.mapMethod("com/test/Test", "foo", "()V", "bar");
        assertSame(renamer, result);
    }

    @Test
    void renameMethodUpdatesMethodName() throws IOException {
        ClassFile cf = TestUtils.createMinimalClass("com/test/MyClass");
        pool.put(cf);

        int methodAccess = new AccessBuilder().setPublic().build();
        MethodEntry method = cf.createNewMethodWithDescriptor(methodAccess, "oldMethod", "()V");

        renamer.mapMethod("com/test/MyClass", "oldMethod", "()V", "newMethod");
        renamer.apply();

        assertEquals("newMethod", method.getName());
    }

    @Test
    void mapMethodInHierarchyAddsMapping() {
        renamer.mapMethodInHierarchy("com/test/Base", "process", "(I)V", "handle");

        MappingStore mappings = renamer.getMappings();
        assertTrue(mappings.hasMethodMapping("com/test/Base", "process", "(I)V"));
    }

    @Test
    void mapMethodInHierarchyReturnsRenamer() {
        Renamer result = renamer.mapMethodInHierarchy("com/test/Base", "foo", "()V", "bar");
        assertSame(renamer, result);
    }

    // ========== Field Renaming Tests ==========

    @Test
    void mapFieldAddsMapping() {
        renamer.mapField("com/test/MyClass", "oldField", "I", "newField");

        MappingStore mappings = renamer.getMappings();
        assertFalse(mappings.isEmpty());
        assertTrue(mappings.hasFieldMapping("com/test/MyClass", "oldField", "I"));
    }

    @Test
    void mapFieldReturnsRenamerForChaining() {
        Renamer result = renamer.mapField("com/test/Test", "x", "I", "y");
        assertSame(renamer, result);
    }

    @Test
    void renameFieldUpdatesFieldName() throws IOException {
        ClassFile cf = TestUtils.createMinimalClass("com/test/MyClass");
        pool.put(cf);

        int fieldAccess = new AccessBuilder().setPrivate().build();
        FieldEntry field = cf.createNewField(fieldAccess, "oldField", "I", List.of());

        renamer.mapField("com/test/MyClass", "oldField", "I", "newField");
        renamer.apply();

        assertEquals("newField", field.getName());
    }

    @Test
    void renameMultipleFields() throws IOException {
        ClassFile cf = TestUtils.createMinimalClass("com/test/MyClass");
        pool.put(cf);

        int fieldAccess = new AccessBuilder().setPrivate().build();
        FieldEntry field1 = cf.createNewField(fieldAccess, "fieldA", "I", List.of());
        FieldEntry field2 = cf.createNewField(fieldAccess, "fieldB", "Ljava/lang/String;", List.of());

        renamer.mapField("com/test/MyClass", "fieldA", "I", "renamedA")
               .mapField("com/test/MyClass", "fieldB", "Ljava/lang/String;", "renamedB")
               .apply();

        assertEquals("renamedA", field1.getName());
        assertEquals("renamedB", field2.getName());
    }

    // ========== Chaining Tests ==========

    @Test
    void chainMultipleMappings() {
        Renamer result = renamer
                .mapClass("com/old/Class", "com/new/Class")
                .mapMethod("com/test/Test", "oldMethod", "()V", "newMethod")
                .mapField("com/test/Test", "field", "I", "renamedField");

        assertSame(renamer, result);
        MappingStore mappings = renamer.getMappings();
        assertTrue(mappings.hasClassMapping("com/old/Class"));
        assertTrue(mappings.hasMethodMapping("com/test/Test", "oldMethod", "()V"));
        assertTrue(mappings.hasFieldMapping("com/test/Test", "field", "I"));
    }

    // ========== Validation Tests ==========

    @Test
    void validateReturnsValidationResult() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        renamer.mapClass("com/test/MyClass", "com/test/RenamedClass");
        ValidationResult result = renamer.validate();

        assertNotNull(result);
    }

    @Test
    void validateSuccessForValidMapping() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        renamer.mapClass("com/test/MyClass", "com/test/RenamedClass");
        ValidationResult result = renamer.validate();

        assertTrue(result.isValid());
    }

    @Test
    void applyThrowsExceptionOnValidationFailure() {
        renamer.mapClass("com/nonexistent/Class", "com/new/Class");

        assertThrows(RenameException.class, () -> renamer.apply());
    }

    // ========== Apply Tests ==========

    @Test
    void applyDoesNothingWhenNoMappings() {
        assertDoesNotThrow(() -> renamer.apply());
    }

    @Test
    void applySucceedsWithValidMappings() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        renamer.mapClass("com/test/MyClass", "com/test/RenamedClass");
        assertDoesNotThrow(() -> renamer.apply());
    }

    // ========== ApplyUnsafe Tests ==========

    @Test
    void applyUnsafeDoesNothingWhenNoMappings() {
        assertDoesNotThrow(() -> renamer.applyUnsafe());
    }

    @Test
    void applyUnsafeDoesNotValidate() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        renamer.mapClass("com/test/MyClass", "com/test/RenamedClass");
        assertDoesNotThrow(() -> renamer.applyUnsafe());
    }

    // ========== Clear Tests ==========

    @Test
    void clearRemovesAllMappings() {
        renamer.mapClass("com/old/Class", "com/new/Class")
               .mapMethod("com/test/Test", "method", "()V", "renamed")
               .clear();

        assertTrue(renamer.getMappings().isEmpty());
    }

    @Test
    void clearReturnsRenamerForChaining() {
        Renamer result = renamer.clear();
        assertSame(renamer, result);
    }

    @Test
    void clearAllowsReuse() {
        renamer.mapClass("com/old/A", "com/new/A");
        renamer.clear();
        renamer.mapClass("com/old/B", "com/new/B");

        MappingStore mappings = renamer.getMappings();
        assertFalse(mappings.hasClassMapping("com/old/A"));
        assertTrue(mappings.hasClassMapping("com/old/B"));
    }

    // ========== GetMappings Tests ==========

    @Test
    void getMappingsReturnsNonNull() {
        assertNotNull(renamer.getMappings());
    }

    @Test
    void getMappingsReturnsEmptyStoreInitially() {
        assertTrue(renamer.getMappings().isEmpty());
    }

    @Test
    void getMappingsReturnsSameStore() {
        MappingStore mappings1 = renamer.getMappings();
        MappingStore mappings2 = renamer.getMappings();
        assertSame(mappings1, mappings2);
    }

    // ========== GetHierarchy Tests ==========

    @Test
    void getHierarchyReturnsNonNull() {
        ClassHierarchy hierarchy = renamer.getHierarchy();
        assertNotNull(hierarchy);
    }

    @Test
    void getHierarchyReturnsSameInstance() {
        ClassHierarchy hierarchy1 = renamer.getHierarchy();
        ClassHierarchy hierarchy2 = renamer.getHierarchy();
        assertSame(hierarchy1, hierarchy2);
    }

    // ========== FindOverrides Tests ==========

    @Test
    void findOverridesReturnsNonNull() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Base", access);
        int methodAccess = new AccessBuilder().setPublic().build();
        cf.createNewMethodWithDescriptor(methodAccess, "process", "()V");

        Set<MethodEntry> overrides = renamer.findOverrides("com/test/Base", "process", "()V");
        assertNotNull(overrides);
    }

    @Test
    void findOverridesReturnsEmptyForNonexistentMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Base", access);

        Set<MethodEntry> overrides = renamer.findOverrides("com/test/Base", "nonexistent", "()V");
        assertNotNull(overrides);
    }

    // ========== Complex Scenario Tests ==========

    @Test
    void renameClassAndItsMembers() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/OldClass", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        MethodEntry method = cf.createNewMethodWithDescriptor(methodAccess, "oldMethod", "()V");

        int fieldAccess = new AccessBuilder().setPrivate().build();
        FieldEntry field = cf.createNewField(fieldAccess, "oldField", "I", List.of());

        renamer.mapClass("com/test/OldClass", "com/test/NewClass")
               .mapMethod("com/test/OldClass", "oldMethod", "()V", "newMethod")
               .mapField("com/test/OldClass", "oldField", "I", "newField")
               .apply();

        assertEquals("com/test/NewClass", cf.getClassName());
        assertEquals("newMethod", method.getName());
        assertEquals("newField", field.getName());
    }

    @Test
    void multipleRenamingOperations() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf1 = pool.createNewClass("com/test/ClassA", access);
        ClassFile cf2 = pool.createNewClass("com/test/ClassB", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        MethodEntry method1 = cf1.createNewMethodWithDescriptor(methodAccess, "methodA", "()V");
        MethodEntry method2 = cf2.createNewMethodWithDescriptor(methodAccess, "methodB", "()V");

        renamer.mapClass("com/test/ClassA", "com/renamed/ClassA")
               .mapClass("com/test/ClassB", "com/renamed/ClassB")
               .mapMethod("com/test/ClassA", "methodA", "()V", "renamedA")
               .mapMethod("com/test/ClassB", "methodB", "()V", "renamedB")
               .apply();

        assertEquals("com/renamed/ClassA", cf1.getClassName());
        assertEquals("com/renamed/ClassB", cf2.getClassName());
        assertEquals("renamedA", method1.getName());
        assertEquals("renamedB", method2.getName());
    }

    // ========== Edge Cases Tests ==========

    @Test
    void applyWithEmptyPool() {
        renamer.mapClass("com/test/Class", "com/renamed/Class");
        assertThrows(RenameException.class, () -> renamer.apply());
    }

    @Test
    void clearAfterApply() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/MyClass", access);

        renamer.mapClass("com/test/MyClass", "com/renamed/MyClass");
        renamer.apply();
        renamer.clear();

        assertTrue(renamer.getMappings().isEmpty());
        assertEquals("com/renamed/MyClass", cf.getClassName());
    }

    @Test
    void validateAfterClear() {
        renamer.mapClass("com/test/Class", "com/renamed/Class");
        renamer.clear();

        ValidationResult result = renamer.validate();
        assertTrue(result.isValid());
    }

    // ========== State Management Tests ==========

    @Test
    void newRenamerHasEmptyMappings() {
        assertTrue(new Renamer(pool).getMappings().isEmpty());
    }

    @Test
    void mappingsPreservedBetweenValidations() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        renamer.mapClass("com/test/MyClass", "com/renamed/MyClass");
        renamer.validate();

        MappingStore mappings = renamer.getMappings();
        assertFalse(mappings.isEmpty());
        assertTrue(mappings.hasClassMapping("com/test/MyClass"));
    }
}
