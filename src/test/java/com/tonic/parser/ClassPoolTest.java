package com.tonic.parser;

import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClassPool functionality.
 * Covers class loading, retrieval, and creation operations.
 */
class ClassPoolTest {

    private ClassPool pool;

    @BeforeEach
    void setUp() {
        pool = TestUtils.emptyPool();
    }

    // ========== Empty Pool Tests ==========

    @Test
    void emptyPoolHasNoClasses() {
        assertTrue(pool.getClasses().isEmpty(), "Empty pool should have no classes");
    }

    @Test
    void getNonexistentClassReturnsNull() {
        assertNull(pool.get("com/nonexistent/Class"));
    }

    // ========== Class Creation Tests ==========

    @Test
    void createNewClassAddsToPool() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/NewClass", access);

        assertNotNull(cf);
        assertEquals("com/test/NewClass", cf.getClassName());
        assertEquals(1, pool.getClasses().size());
    }

    @Test
    void createNewClassSetsAccessFlags() throws IOException {
        int access = new AccessBuilder().setPublic().setFinal().build();
        ClassFile cf = pool.createNewClass("com/test/FinalClass", access);

        assertEquals(access, cf.getAccess());
    }

    @Test
    void createNewClassSetsSuperclass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Child", access);

        assertEquals("java/lang/Object", cf.getSuperClassName());
    }

    @Test
    void createNewClassAddsDefaultConstructor() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithInit", access);

        boolean hasInit = cf.getMethods().stream()
                .anyMatch(m -> m.getName().equals("<init>"));
        assertTrue(hasInit, "New class should have default constructor");
    }

    @Test
    void createNewClassAddsClassInitializer() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithClinit", access);

        boolean hasClinit = cf.getMethods().stream()
                .anyMatch(m -> m.getName().equals("<clinit>"));
        assertTrue(hasClinit, "New class should have class initializer");
    }

    @Test
    void createNewClassWithSlashSeparators() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/deep/nested/package/TestClass", access);

        assertEquals("com/deep/nested/package/TestClass", cf.getClassName());
    }

    // ========== Class Creation Validation Tests ==========

    @Test
    void createNewClassRejectsNullName() {
        int access = new AccessBuilder().setPublic().build();
        assertThrows(IllegalArgumentException.class, () ->
                pool.createNewClass(null, access));
    }

    @Test
    void createNewClassRejectsEmptyName() {
        int access = new AccessBuilder().setPublic().build();
        assertThrows(IllegalArgumentException.class, () ->
                pool.createNewClass("", access));
    }

    @Test
    void createNewClassRejectsDotSeparators() {
        int access = new AccessBuilder().setPublic().build();
        assertThrows(IllegalArgumentException.class, () ->
                pool.createNewClass("com.test.DotClass", access));
    }

    @Test
    void createNewClassRejectsDuplicateName() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Duplicate", access);

        assertThrows(IllegalArgumentException.class, () ->
                pool.createNewClass("com/test/Duplicate", access));
    }

    // ========== Class Put/Get Tests ==========

    @Test
    void putAndGetClass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/PutGet", access);

        ClassFile retrieved = pool.get("com/test/PutGet");
        assertSame(cf, retrieved);
    }

    @Test
    void putAddsClassToPool() throws IOException {
        ClassPool otherPool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = otherPool.createNewClass("com/test/FromOther", access);

        pool.put(cf);
        assertEquals(1, pool.getClasses().size());
        assertSame(cf, pool.get("com/test/FromOther"));
    }

    // ========== Class Load Tests ==========

    @Test
    void loadClassFromBytes() throws IOException {
        // Create a class in another pool, write it, then load
        ClassPool sourcePool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile original = sourcePool.createNewClass("com/test/LoadFromBytes", access);
        byte[] bytes = original.write();

        ClassFile loaded = pool.loadClass(bytes);

        assertNotNull(loaded);
        assertEquals("com/test/LoadFromBytes", loaded.getClassName());
    }

    @Test
    void loadClassAddsToPool() throws IOException {
        ClassPool sourcePool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile original = sourcePool.createNewClass("com/test/AddedByLoad", access);
        byte[] bytes = original.write();

        pool.loadClass(bytes);

        assertEquals(1, pool.getClasses().size());
    }

    // ========== Multiple Classes Tests ==========

    @Test
    void poolCanHoldMultipleClasses() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/ClassA", access);
        pool.createNewClass("com/test/ClassB", access);
        pool.createNewClass("com/test/ClassC", access);

        assertEquals(3, pool.getClasses().size());
        assertNotNull(pool.get("com/test/ClassA"));
        assertNotNull(pool.get("com/test/ClassB"));
        assertNotNull(pool.get("com/test/ClassC"));
    }

    @Test
    void getClassesMaintainsInsertionOrder() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/First", access);
        pool.createNewClass("com/test/Second", access);
        pool.createNewClass("com/test/Third", access);

        assertEquals("com/test/First", pool.getClasses().get(0).getClassName());
        assertEquals("com/test/Second", pool.getClasses().get(1).getClassName());
        assertEquals("com/test/Third", pool.getClasses().get(2).getClassName());
    }
}
