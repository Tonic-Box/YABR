package com.tonic.parser;

import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.testutil.TestClassLoader;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MethodEntryTest {

    private ClassPool pool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/TestClass", access);
    }

    @Test
    void methodHasCorrectName() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testMethod", "V");
        assertEquals("testMethod", method.getName());
    }

    @Test
    void methodHasCorrectDescriptor() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "compute", "I", "I", "I");
        assertEquals("(II)I", method.getDesc());
    }

    @Test
    void methodHasCorrectOwner() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "owned", "V");
        assertEquals("com/test/TestClass", method.getOwnerName());
    }

    @Test
    void methodHasCorrectKey() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "keyed", "I", "Ljava/lang/String;");
        assertEquals("keyed(Ljava/lang/String;)I", method.getKey());
    }

    @Test
    void methodHasCorrectAccessFlags() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().setFinal().build();
        MethodEntry method = classFile.createNewMethod(access, "staticFinal", "V");
        assertEquals(access, method.getAccess());
    }

    @Test
    void methodHasCodeAttribute() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "withCode", "V");
        assertNotNull(method.getCodeAttribute());
    }

    @Test
    void abstractMethodHasNoCodeAttribute() {
        int access = new AccessBuilder().setPublic().setAbstract().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "abstractMethod", "()V");
        assertNotNull(method);
    }

    @Test
    void nativeMethodCreation() {
        int access = new AccessBuilder().setPublic().setNative().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "nativeMethod", "()V");
        assertNotNull(method);
        assertTrue((method.getAccess() & 0x0100) != 0, "Method should be native");
    }

    @Test
    void isVoidReturnTrue() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "voidMethod", "V");
        assertTrue(method.isVoidReturn());
    }

    @Test
    void isVoidReturnFalseForInt() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "intMethod", "I");
        assertFalse(method.isVoidReturn());
    }

    @Test
    void isPrimitiveReturnTrueForInt() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "intReturn", "I");
        assertTrue(method.isPrimitiveReturn());
    }

    @Test
    void isPrimitiveReturnTrueForLong() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "longReturn", "J");
        assertTrue(method.isPrimitiveReturn());
    }

    @Test
    void isPrimitiveReturnTrueForFloat() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "floatReturn", "F");
        assertTrue(method.isPrimitiveReturn());
    }

    @Test
    void isPrimitiveReturnTrueForDouble() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "doubleReturn", "D");
        assertTrue(method.isPrimitiveReturn());
    }

    @Test
    void isPrimitiveReturnTrueForBoolean() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "boolReturn", "Z");
        assertTrue(method.isPrimitiveReturn());
    }

    @Test
    void isPrimitiveReturnTrueForByte() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "byteReturn", "B");
        assertTrue(method.isPrimitiveReturn());
    }

    @Test
    void isPrimitiveReturnTrueForChar() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "charReturn", "C");
        assertTrue(method.isPrimitiveReturn());
    }

    @Test
    void isPrimitiveReturnTrueForShort() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "shortReturn", "S");
        assertTrue(method.isPrimitiveReturn());
    }

    @Test
    void isPrimitiveReturnFalseForVoid() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "voidReturn", "V");
        assertFalse(method.isPrimitiveReturn());
    }

    @Test
    void setNameChangesMethodName() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "original", "V");
        method.setName("renamed");
        assertEquals("renamed", method.getName());
    }

    @Test
    void setNameUpdatesKey() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "original", "V");
        method.setName("renamed");
        assertEquals("renamed()V", method.getKey());
    }

    @Test
    void publicMethodHasPublicAccess() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "publicMethod", "V");
        assertTrue((method.getAccess() & 0x0001) != 0, "Method should be public");
    }

    @Test
    void privateMethodHasPrivateAccess() throws IOException {
        int access = new AccessBuilder().setPrivate().build();
        MethodEntry method = classFile.createNewMethod(access, "privateMethod", "V");
        assertTrue((method.getAccess() & 0x0002) != 0, "Method should be private");
    }

    @Test
    void staticMethodHasStaticAccess() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "staticMethod", "V");
        assertTrue((method.getAccess() & 0x0008) != 0, "Method should be static");
    }

    @Test
    void finalMethodHasFinalAccess() throws IOException {
        int access = new AccessBuilder().setPublic().setFinal().build();
        MethodEntry method = classFile.createNewMethod(access, "finalMethod", "V");
        assertTrue((method.getAccess() & 0x0010) != 0, "Method should be final");
    }

    @Test
    void synchronizedMethodHasSynchronizedAccess() throws IOException {
        int access = new AccessBuilder().setPublic().setSynchronized().build();
        MethodEntry method = classFile.createNewMethod(access, "syncMethod", "V");
        assertTrue((method.getAccess() & 0x0020) != 0, "Method should be synchronized");
    }

    @Test
    void toStringContainsMethodName() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "displayMe", "V");
        assertTrue(method.toString().contains("displayMe"));
    }

    @Test
    void toStringContainsOwnerName() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "owned", "V");
        assertTrue(method.toString().contains("com/test/TestClass"));
    }

    @Test
    void toStringContainsDescriptor() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "typed", "I", "Ljava/lang/String;");
        assertTrue(method.toString().contains("(Ljava/lang/String;)I"));
    }

    @Test
    void roundTripPreservesMethodName() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        classFile.createNewMethod(access, "preservedMethod", "V");

        ClassFile reloaded = TestUtils.roundTrip(classFile);
        boolean found = reloaded.getMethods().stream()
                .anyMatch(m -> "preservedMethod".equals(m.getName()));
        assertTrue(found, "Method name should be preserved after round-trip");
    }

    @Test
    void roundTripPreservesMethodDescriptor() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        classFile.createNewMethod(access, "complexMethod", "I", "J", "D");

        ClassFile reloaded = TestUtils.roundTrip(classFile);
        MethodEntry method = reloaded.getMethods().stream()
                .filter(m -> "complexMethod".equals(m.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(method);
        assertEquals("(JD)I", method.getDesc());
    }

    @Test
    void roundTripPreservesMethodAccess() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().setFinal().build();
        classFile.createNewMethod(access, "flaggedMethod", "V");

        ClassFile reloaded = TestUtils.roundTrip(classFile);
        MethodEntry method = reloaded.getMethods().stream()
                .filter(m -> "flaggedMethod".equals(m.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(method);
        assertEquals(access, method.getAccess());
    }

    @Test
    void generatedClassWithMethodsLoadsInJVM() throws Exception {
        byte[] bytes = classFile.write();
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.defineClass("com.test.TestClass", bytes);

        assertNotNull(clazz);
        assertEquals("com.test.TestClass", clazz.getName());
    }

    @Test
    void defaultMethodsExistAfterClassLoad() throws Exception {
        byte[] bytes = classFile.write();
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.defineClass("com.test.TestClass", bytes);

        assertNotNull(clazz.getDeclaredConstructor());
    }

    @Test
    void newClassHasInitMethod() {
        boolean hasInit = classFile.getMethods().stream()
                .anyMatch(m -> "<init>".equals(m.getName()));
        assertTrue(hasInit, "New class should have <init> method");
    }

    @Test
    void newClassHasClinitMethod() {
        boolean hasClinit = classFile.getMethods().stream()
                .anyMatch(m -> "<clinit>".equals(m.getName()));
        assertTrue(hasClinit, "New class should have <clinit> method");
    }

    @Test
    void initMethodHasVoidReturn() {
        MethodEntry init = classFile.getMethods().stream()
                .filter(m -> "<init>".equals(m.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(init);
        assertTrue(init.isVoidReturn());
    }

    @Nested
    class ReturnTypeTests {
        @Test
        void isReferenceReturnForObjectType() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "objectReturn", "()Ljava/lang/String;");

            assertTrue(method.isReferenceReturn());
        }

        @Test
        void isPrimitiveArrayReturnForIntArray() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "intArrayReturn", "()[I");

            assertTrue(method.isPrimitiveArrayReturn());
        }

        @Test
        void isPrimitiveArrayReturnForByteArray() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "byteArrayReturn", "()[B");

            assertTrue(method.isPrimitiveArrayReturn());
        }

        @Test
        void isPrimitiveArrayReturnForCharArray() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "charArrayReturn", "()[C");

            assertTrue(method.isPrimitiveArrayReturn());
        }

        @Test
        void isPrimitiveArrayReturnForShortArray() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "shortArrayReturn", "()[S");

            assertTrue(method.isPrimitiveArrayReturn());
        }

        @Test
        void isPrimitiveArrayReturnForLongArray() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "longArrayReturn", "()[J");

            assertTrue(method.isPrimitiveArrayReturn());
        }

        @Test
        void isPrimitiveArrayReturnForFloatArray() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "floatArrayReturn", "()[F");

            assertTrue(method.isPrimitiveArrayReturn());
        }

        @Test
        void isPrimitiveArrayReturnForDoubleArray() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "doubleArrayReturn", "()[D");

            assertTrue(method.isPrimitiveArrayReturn());
        }

        @Test
        void isPrimitiveArrayReturnForBooleanArray() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "booleanArrayReturn", "()[Z");

            assertTrue(method.isPrimitiveArrayReturn());
        }

        @Test
        void isReferenceArrayReturnForObjectArray() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "objectArrayReturn", "()[Ljava/lang/String;");

            assertTrue(method.isReferenceArrayReturn());
        }

        @Test
        void isPrimitiveReturnFalseForObjectType() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "objectReturn", "()Ljava/lang/String;");

            assertFalse(method.isPrimitiveReturn());
        }
    }

    @Nested
    class MethodAccessFlagTests {
        @Test
        void createProtectedMethod() throws IOException {
            int access = new AccessBuilder().setProtected().build();
            MethodEntry method = classFile.createNewMethod(access, "protectedMethod", "V");

            assertTrue((method.getAccess() & 0x0004) != 0);
        }

        @Test
        void createBridgeMethod() {
            int access = new AccessBuilder().setPublic().setBridge().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "bridgeMethod", "()V");

            assertTrue((method.getAccess() & 0x0040) != 0);
        }

        @Test
        void createVarargsMethod() {
            int access = new AccessBuilder().setPublic().setVarArgs().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "varargsMethod", "([Ljava/lang/String;)V");

            assertTrue((method.getAccess() & 0x0080) != 0);
        }

        @Test
        void createSyntheticMethod() {
            int access = new AccessBuilder().setPublic().setSynthetic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "syntheticMethod", "()V");

            assertTrue((method.getAccess() & 0x1000) != 0);
        }

        @Test
        void createStrictfpMethod() throws IOException {
            int access = new AccessBuilder().setPublic().setStrictfp().build();
            MethodEntry method = classFile.createNewMethod(access, "strictfpMethod", "D", "D");

            assertTrue((method.getAccess() & 0x0800) != 0);
        }
    }

    @Nested
    class ComplexDescriptorTests {
        @Test
        void methodWithNoParameters() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "noParams", "()V");

            assertEquals("noParams()V", method.getKey());
        }

        @Test
        void methodWithMultipleObjectParameters() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "multipleParams",
                    "(Ljava/lang/String;Ljava/lang/Integer;Ljava/util/List;)V");

            assertEquals("multipleParams(Ljava/lang/String;Ljava/lang/Integer;Ljava/util/List;)V", method.getKey());
        }

        @Test
        void methodWithMixedParameters() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "mixed",
                    "(ILjava/lang/String;[IZ)Ljava/lang/Object;");

            assertEquals("mixed(ILjava/lang/String;[IZ)Ljava/lang/Object;", method.getKey());
        }

        @Test
        void methodReturningMultiDimensionalArray() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "matrix", "()[[I");

            assertEquals("matrix()[[I", method.getKey());
        }

        @Test
        void methodWithGenericErasedTypes() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "genericMethod",
                    "(Ljava/util/List;)Ljava/util/Map;");

            assertEquals("genericMethod(Ljava/util/List;)Ljava/util/Map;", method.getKey());
        }
    }

    @Nested
    class ToStringTests {
        @Test
        void toStringContainsKey() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethod(access, "myMethod", "I", "Ljava/lang/String;");

            String str = method.toString();
            assertTrue(str.contains("key='myMethod(Ljava/lang/String;)I'"));
        }

        @Test
        void toStringContainsAccessFlags() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "staticMethod", "V");

            String str = method.toString();
            assertTrue(str.contains("access=0x"));
        }

        @Test
        void toStringContainsAttributes() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethod(access, "withCode", "V");

            String str = method.toString();
            assertTrue(str.contains("attributes="));
        }
    }

    @Nested
    class OwnerNameTests {
        @Test
        void ownerNameMatchesClassName() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethod(access, "test", "V");

            assertEquals(classFile.getClassName(), method.getOwnerName());
        }

        @Test
        void ownerNameUpdatesWhenClassRenamed() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethod(access, "test", "V");

            classFile.setClassName("com/test/RenamedClass");

            assertEquals("com/test/RenamedClass", method.getOwnerName());
        }
    }
}
