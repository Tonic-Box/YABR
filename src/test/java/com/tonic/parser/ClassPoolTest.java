package com.tonic.parser;

import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.io.IOException;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ClassPoolTest {

    private ClassPool pool;

    @BeforeEach
    void setUp() {
        pool = TestUtils.emptyPool();
    }

    @Test
    void emptyPoolHasNoClasses() {
        assertTrue(pool.getClasses().isEmpty(), "Empty pool should have no classes");
    }

    @Test
    void getNonexistentClassReturnsNull() {
        assertNull(pool.get("com/nonexistent/Class"));
    }

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

    @Test
    void loadClassFromBytes() throws IOException {
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

    @Nested
    class LoadingEdgeCases {
        @Test
        void loadClassFromInputStream() throws IOException {
            ClassPool sourcePool = TestUtils.emptyPool();
            int access = new AccessBuilder().setPublic().build();
            ClassFile original = sourcePool.createNewClass("com/test/StreamClass", access);
            byte[] bytes = original.write();

            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ClassFile loaded = pool.loadClass(bais);

            assertNotNull(loaded);
            assertEquals("com/test/StreamClass", loaded.getClassName());
        }

        @Test
        void putMultipleClassesSeparately() throws IOException {
            ClassPool sourcePool = TestUtils.emptyPool();
            int access = new AccessBuilder().setPublic().build();

            ClassFile cf1 = sourcePool.createNewClass("com/test/Class1", access);
            ClassFile cf2 = sourcePool.createNewClass("com/test/Class2", access);
            ClassFile cf3 = sourcePool.createNewClass("com/test/Class3", access);

            pool.put(cf1);
            pool.put(cf2);
            pool.put(cf3);

            assertEquals(3, pool.getClasses().size());
        }

        @Test
        void getReturnsNullForPartialMatch() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            pool.createNewClass("com/test/MyClass", access);

            assertNull(pool.get("com/test/My"));
            assertNull(pool.get("com/test/MyClass2"));
            assertNull(pool.get("MyClass"));
        }
    }

    @Nested
    class ClassCreationVariations {
        @Test
        void createAbstractClass() throws IOException {
            int access = new AccessBuilder().setPublic().setAbstract().build();
            ClassFile cf = pool.createNewClass("com/test/AbstractClass", access);

            assertTrue((cf.getAccess() & 0x0400) != 0);
        }

        @Test
        void createFinalClass() throws IOException {
            int access = new AccessBuilder().setPublic().setFinal().build();
            ClassFile cf = pool.createNewClass("com/test/FinalClass", access);

            assertTrue((cf.getAccess() & 0x0010) != 0);
        }

        @Test
        void createInterfaceClass() throws IOException {
            int access = new AccessBuilder().setPublic().setInterface().setAbstract().build();
            ClassFile cf = pool.createNewClass("com/test/MyInterface", access);

            assertTrue((cf.getAccess() & 0x0200) != 0);
        }

        @Test
        void createEnumClass() throws IOException {
            int access = new AccessBuilder().setPublic().setEnum().setFinal().build();
            ClassFile cf = pool.createNewClass("com/test/MyEnum", access);

            assertTrue((cf.getAccess() & 0x4000) != 0);
        }

        @Test
        void createAnnotationClass() throws IOException {
            int access = new AccessBuilder().setPublic().setAnnotation().setInterface().setAbstract().build();
            ClassFile cf = pool.createNewClass("com/test/MyAnnotation", access);

            assertTrue((cf.getAccess() & 0x2000) != 0);
        }
    }

    @Nested
    class NameValidationEdgeCases {
        @Test
        void createClassWithNestedPackageName() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/company/product/module/feature/MyClass", access);

            assertNotNull(cf);
            assertEquals("com/company/product/module/feature/MyClass", cf.getClassName());
        }

        @Test
        void createClassWithSinglePackage() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("test/SimpleClass", access);

            assertNotNull(cf);
            assertEquals("test/SimpleClass", cf.getClassName());
        }

        @Test
        void createClassWithNoPackage() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("DefaultPackageClass", access);

            assertNotNull(cf);
            assertEquals("DefaultPackageClass", cf.getClassName());
        }
    }

    @Nested
    class RoundTripTests {
        @Test
        void roundTripSimpleClass() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile original = pool.createNewClass("com/test/SimpleClass", access);
            byte[] bytes = original.write();

            ClassPool newPool = TestUtils.emptyPool();
            ClassFile reloaded = newPool.loadClass(bytes);

            assertEquals(original.getClassName(), reloaded.getClassName());
            assertEquals(original.getSuperClassName(), reloaded.getSuperClassName());
            assertEquals(original.getAccess(), reloaded.getAccess());
        }

        @Test
        void roundTripClassWithMethods() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile original = pool.createNewClass("com/test/WithMethods", access);
            original.createNewMethod(access, "testMethod1", "V");
            original.createNewMethod(access, "testMethod2", "I");
            byte[] bytes = original.write();

            ClassPool newPool = TestUtils.emptyPool();
            ClassFile reloaded = newPool.loadClass(bytes);

            assertEquals(original.getMethods().size(), reloaded.getMethods().size());
        }

        @Test
        void roundTripClassWithFields() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile original = pool.createNewClass("com/test/WithFields", access);
            original.createNewField(new AccessBuilder().setPrivate().build(), "field1", "I", null);
            original.createNewField(new AccessBuilder().setPrivate().build(), "field2", "Ljava/lang/String;", null);
            byte[] bytes = original.write();

            ClassPool newPool = TestUtils.emptyPool();
            ClassFile reloaded = newPool.loadClass(bytes);

            assertEquals(original.getFields().size(), reloaded.getFields().size());
        }

        @Test
        void roundTripClassWithInterfaces() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile original = pool.createNewClass("com/test/WithInterfaces", access);
            original.addInterface("java/io/Serializable");
            original.addInterface("java/lang/Cloneable");
            byte[] bytes = original.write();

            ClassPool newPool = TestUtils.emptyPool();
            ClassFile reloaded = newPool.loadClass(bytes);

            assertEquals(original.getInterfaces().size(), reloaded.getInterfaces().size());
        }
    }

    @Nested
    class GetEdgeCases {
        @Test
        void getWithNullReturnsNull() {
            assertNull(pool.get(null));
        }

        @Test
        void getWithEmptyStringReturnsNull() {
            assertNull(pool.get(""));
        }

        @Test
        void getAfterPuttingMultipleClasses() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf1 = pool.createNewClass("com/test/Alpha", access);
            ClassFile cf2 = pool.createNewClass("com/test/Beta", access);
            ClassFile cf3 = pool.createNewClass("com/test/Gamma", access);

            assertSame(cf1, pool.get("com/test/Alpha"));
            assertSame(cf2, pool.get("com/test/Beta"));
            assertSame(cf3, pool.get("com/test/Gamma"));
        }
    }
}
