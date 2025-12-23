package com.tonic.parser;

import com.tonic.testutil.TestClassLoader;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ClassFileTest {

    private ClassPool pool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/TestClass", access);
    }

    @Test
    void classHasCorrectName() {
        assertEquals("com/test/TestClass", classFile.getClassName());
    }

    @Test
    void classHasObjectSuperclass() {
        assertEquals("java/lang/Object", classFile.getSuperClassName());
    }

    @Test
    void classHasCorrectVersion() {
        assertEquals(55, classFile.getMajorVersion());
        assertEquals(0, classFile.getMinorVersion());
    }

    @Test
    void classHasConstPool() {
        assertNotNull(classFile.getConstPool());
    }

    @Test
    void setClassName() {
        classFile.setClassName("com/test/RenamedClass");
        assertEquals("com/test/RenamedClass", classFile.getClassName());
    }

    @Test
    void setClassNameWithDotsSeparatorsConverts() {
        classFile.setClassName("com.test.DottedClass");
        assertEquals("com/test/DottedClass", classFile.getClassName());
    }

    @Test
    void setSuperClassName() {
        classFile.setSuperClassName("java/util/ArrayList");
        assertEquals("java/util/ArrayList", classFile.getSuperClassName());
    }

    @Test
    void newClassHasNoInterfaces() {
        assertTrue(classFile.getInterfaces().isEmpty());
    }

    @Test
    void addInterfaceIncrementsInterfaceCount() {
        classFile.addInterface("java/io/Serializable");
        assertEquals(1, classFile.getInterfaces().size());
    }

    @Test
    void addMultipleInterfaces() {
        classFile.addInterface("java/io/Serializable");
        classFile.addInterface("java/lang/Cloneable");
        assertEquals(2, classFile.getInterfaces().size());
    }

    @Test
    void addInterfaceWithDotSeparatorsConverts() {
        classFile.addInterface("java.io.Serializable");
        assertEquals(1, classFile.getInterfaces().size());
    }

    @Test
    void addDuplicateInterfaceDoesNotDuplicate() {
        classFile.addInterface("java/io/Serializable");
        classFile.addInterface("java/io/Serializable");
        assertEquals(1, classFile.getInterfaces().size());
    }

    @Test
    void newClassHasNoUserFields() {
        assertTrue(classFile.getFields().isEmpty());
    }

    @Test
    void createNewFieldAddsField() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "value", "I", new ArrayList<>());

        assertNotNull(field);
        assertEquals(1, classFile.getFields().size());
    }

    @Test
    void createNewFieldWithCorrectProperties() {
        int access = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(access, "name", "Ljava/lang/String;", new ArrayList<>());

        assertEquals("name", field.getName());
        assertEquals("Ljava/lang/String;", field.getDesc());
        assertEquals(access, field.getAccess());
    }

    @Test
    void createStaticField() {
        int access = new AccessBuilder().setPublic().setStatic().build();
        FieldEntry field = classFile.createNewField(access, "counter", "I", new ArrayList<>());

        assertTrue((field.getAccess() & 0x0008) != 0, "Field should be static");
    }

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
    void newClassHasDefaultMethods() {
        assertEquals(2, classFile.getMethods().size());
    }

    @Test
    void createNewMethodAddsMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testMethod", "V");

        assertNotNull(method);
        assertEquals(3, classFile.getMethods().size());
    }

    @Test
    void createNewMethodWithCorrectSignature() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "add", "I", "I", "I");

        assertEquals("add", method.getName());
        assertEquals("(II)I", method.getDesc());
    }

    @Test
    void createNewMethodWithDescriptor() {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "process", "(Ljava/lang/String;I)V");

        assertEquals("process", method.getName());
        assertEquals("(Ljava/lang/String;I)V", method.getDesc());
    }

    @Test
    void generateGetterForField() throws IOException {
        int fieldAccess = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(fieldAccess, "value", "I", new ArrayList<>());

        MethodEntry getter = classFile.generateGetter(field, false);

        assertEquals("getValue", getter.getName());
        assertEquals("()I", getter.getDesc());
    }

    @Test
    void generateSetterForField() throws IOException {
        int fieldAccess = new AccessBuilder().setPrivate().build();
        FieldEntry field = classFile.createNewField(fieldAccess, "value", "I", new ArrayList<>());

        MethodEntry setter = classFile.generateSetter(field, false);

        assertEquals("setValue", setter.getName());
        assertEquals("(I)V", setter.getDesc());
    }

    @Test
    void writeProducesValidBytes() throws IOException {
        byte[] bytes = classFile.write();

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        assertEquals((byte) 0xCA, bytes[0]);
        assertEquals((byte) 0xFE, bytes[1]);
        assertEquals((byte) 0xBA, bytes[2]);
        assertEquals((byte) 0xBE, bytes[3]);
    }

    @Test
    void roundTripPreservesClassName() throws IOException {
        ClassFile reloaded = TestUtils.roundTrip(classFile);
        assertEquals(classFile.getClassName(), reloaded.getClassName());
    }

    @Test
    void roundTripPreservesSuperClassName() throws IOException {
        ClassFile reloaded = TestUtils.roundTrip(classFile);
        assertEquals(classFile.getSuperClassName(), reloaded.getSuperClassName());
    }

    @Test
    void roundTripPreservesVersion() throws IOException {
        ClassFile reloaded = TestUtils.roundTrip(classFile);
        assertEquals(classFile.getMajorVersion(), reloaded.getMajorVersion());
        assertEquals(classFile.getMinorVersion(), reloaded.getMinorVersion());
    }

    @Test
    void roundTripPreservesAccess() throws IOException {
        ClassFile reloaded = TestUtils.roundTrip(classFile);
        assertEquals(classFile.getAccess(), reloaded.getAccess());
    }

    @Test
    void roundTripPreservesMethodCount() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        classFile.createNewMethod(access, "customMethod", "V");

        ClassFile reloaded = TestUtils.roundTrip(classFile);
        assertEquals(classFile.getMethods().size(), reloaded.getMethods().size());
    }

    @Test
    void roundTripPreservesFieldCount() throws IOException {
        int access = new AccessBuilder().setPrivate().build();
        classFile.createNewField(access, "field1", "I", new ArrayList<>());
        classFile.createNewField(access, "field2", "J", new ArrayList<>());

        ClassFile reloaded = TestUtils.roundTrip(classFile);
        assertEquals(classFile.getFields().size(), reloaded.getFields().size());
    }

    @Test
    void generatedClassLoadsInJVM() throws Exception {
        byte[] bytes = classFile.write();
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.defineClass("com.test.TestClass", bytes);

        assertNotNull(clazz);
        assertEquals("com.test.TestClass", clazz.getName());
    }

    @Test
    void generatedClassCanBeInstantiated() throws Exception {
        byte[] bytes = classFile.write();
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.defineClass("com.test.TestClass", bytes);

        Object instance = clazz.getDeclaredConstructor().newInstance();
        assertNotNull(instance);
    }

    @Test
    void computeFramesReturnsMethodCount() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        classFile.createNewMethod(access, "method1", "V");
        classFile.createNewMethod(access, "method2", "V");

        int count = classFile.computeFrames();
        assertTrue(count > 0);
    }

    @Test
    void computeFramesForSpecificMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "targetMethod", "V");

        boolean computed = classFile.computeFrames("targetMethod", method.getDesc());
        assertTrue(computed);
    }

    @Test
    void computeFramesForNonexistentMethodReturnsFalse() {
        boolean computed = classFile.computeFrames("nonexistent", "()V");
        assertFalse(computed);
    }

    @Test
    void toStringIncludesClassName() {
        String str = classFile.toString();
        assertTrue(str.contains("com/test/TestClass"));
    }

    @Test
    void toStringIncludesSuperClass() {
        String str = classFile.toString();
        assertTrue(str.contains("java/lang/Object"));
    }

    @Test
    void roundTripWithLongConstantPreservesConstPoolCount() throws IOException {
        com.tonic.parser.constpool.LongItem longItem = new com.tonic.parser.constpool.LongItem();
        longItem.setValue(123456789L);
        int longIndex = classFile.getConstPool().addItem(longItem);

        int originalCpSize = classFile.getConstPool().getItems().size();

        ClassFile reloaded = TestUtils.roundTrip(classFile);

        assertEquals(originalCpSize, reloaded.getConstPool().getItems().size(),
                "Constant pool size should be preserved after round-trip with Long constant");
    }

    @Test
    void roundTripWithDoubleConstantPreservesConstPoolCount() throws IOException {
        com.tonic.parser.constpool.DoubleItem doubleItem = new com.tonic.parser.constpool.DoubleItem();
        doubleItem.setValue(3.14159);
        int doubleIndex = classFile.getConstPool().addItem(doubleItem);

        int originalCpSize = classFile.getConstPool().getItems().size();

        ClassFile reloaded = TestUtils.roundTrip(classFile);

        assertEquals(originalCpSize, reloaded.getConstPool().getItems().size(),
                "Constant pool size should be preserved after round-trip with Double constant");
    }

    @Test
    void roundTripWithMultipleLongsPreservesConstPoolCount() throws IOException {
        for (int i = 0; i < 16; i++) {
            com.tonic.parser.constpool.LongItem longItem = new com.tonic.parser.constpool.LongItem();
            longItem.setValue(i * 1000000L);
            classFile.getConstPool().addItem(longItem);
        }

        int originalCpSize = classFile.getConstPool().getItems().size();

        ClassFile reloaded = TestUtils.roundTrip(classFile);

        assertEquals(originalCpSize, reloaded.getConstPool().getItems().size(),
                "Constant pool size should be preserved after round-trip with 16 Long constants");
    }

    @Test
    void roundTripWithMixedLongDoublePreservesConstPoolCount() throws IOException {
        for (int i = 0; i < 8; i++) {
            com.tonic.parser.constpool.LongItem longItem = new com.tonic.parser.constpool.LongItem();
            longItem.setValue(i * 1000000L);
            classFile.getConstPool().addItem(longItem);

            com.tonic.parser.constpool.DoubleItem doubleItem = new com.tonic.parser.constpool.DoubleItem();
            doubleItem.setValue(i * 1.5);
            classFile.getConstPool().addItem(doubleItem);
        }

        int originalCpSize = classFile.getConstPool().getItems().size();

        ClassFile reloaded = TestUtils.roundTrip(classFile);

        assertEquals(originalCpSize, reloaded.getConstPool().getItems().size(),
                "Constant pool size should be preserved after round-trip with mixed Long/Double constants");
    }

    @Test
    void classWithLongConstantLoadsInJVM() throws Exception {
        com.tonic.parser.constpool.LongItem longItem = new com.tonic.parser.constpool.LongItem();
        longItem.setValue(9876543210L);
        classFile.getConstPool().addItem(longItem);

        byte[] bytes = classFile.write();
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.defineClass("com.test.TestClass", bytes);

        assertNotNull(clazz, "Class with Long constant should load successfully");
    }

    @Test
    void classWithDoubleConstantLoadsInJVM() throws Exception {
        com.tonic.parser.constpool.DoubleItem doubleItem = new com.tonic.parser.constpool.DoubleItem();
        doubleItem.setValue(2.71828);
        classFile.getConstPool().addItem(doubleItem);

        byte[] bytes = classFile.write();
        TestClassLoader loader = new TestClassLoader();
        Class<?> clazz = loader.defineClass("com.test.TestClass", bytes);

        assertNotNull(clazz, "Class with Double constant should load successfully");
    }

    @Nested
    class AccessFlagsTests {
        @Test
        void createInterfaceClass() throws IOException {
            int access = new AccessBuilder().setPublic().setInterface().setAbstract().build();
            ClassFile interfaceClass = pool.createNewClass("com/test/MyInterface", access);

            assertTrue((interfaceClass.getAccess() & 0x0200) != 0);
            String str = interfaceClass.toString();
            assertTrue(str.contains("interface"));
        }

        @Test
        void createAbstractClass() throws IOException {
            int access = new AccessBuilder().setPublic().setAbstract().build();
            ClassFile abstractClass = pool.createNewClass("com/test/AbstractClass", access);

            assertTrue((abstractClass.getAccess() & 0x0400) != 0);
            String str = abstractClass.toString();
            assertTrue(str.contains("abstract"));
        }

        @Test
        void createFinalClass() throws IOException {
            int access = new AccessBuilder().setPublic().setFinal().build();
            ClassFile finalClass = pool.createNewClass("com/test/FinalClass", access);

            assertTrue((finalClass.getAccess() & 0x0010) != 0);
            String str = finalClass.toString();
            assertTrue(str.contains("final"));
        }

        @Test
        void createEnumClass() throws IOException {
            int access = new AccessBuilder().setPublic().setEnum().setFinal().build();
            ClassFile enumClass = pool.createNewClass("com/test/MyEnum", access);

            assertTrue((enumClass.getAccess() & 0x4000) != 0);
            String str = enumClass.toString();
            assertTrue(str.contains("enum"));
        }

        @Test
        void createAnnotationClass() throws IOException {
            int access = new AccessBuilder().setPublic().setAnnotation().setInterface().setAbstract().build();
            ClassFile annotationClass = pool.createNewClass("com/test/MyAnnotation", access);

            assertTrue((annotationClass.getAccess() & 0x2000) != 0);
            String str = annotationClass.toString();
            assertTrue(str.contains("annotation"));
        }

        @Test
        void createSyntheticClass() throws IOException {
            int access = new AccessBuilder().setPublic().setSynthetic().build();
            ClassFile syntheticClass = pool.createNewClass("com/test/SyntheticClass", access);

            assertTrue((syntheticClass.getAccess() & 0x1000) != 0);
            String str = syntheticClass.toString();
            assertTrue(str.contains("synthetic"));
        }
    }

    @Nested
    class MethodCreationEdgeCases {
        @Test
        void createAbstractMethod() {
            int access = new AccessBuilder().setPublic().setAbstract().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "abstractMethod", "()V");

            assertNotNull(method);
            assertNull(method.getCodeAttribute());
        }

        @Test
        void createNativeMethod() {
            int access = new AccessBuilder().setPublic().setNative().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "nativeMethod", "()I");

            assertNotNull(method);
            assertNull(method.getCodeAttribute());
        }

        @Test
        void createStaticGetter() throws IOException {
            int fieldAccess = new AccessBuilder().setPrivate().setStatic().build();
            FieldEntry field = classFile.createNewField(fieldAccess, "staticValue", "J", null);

            MethodEntry getter = classFile.generateGetter(field, true);

            assertEquals("getStaticValue", getter.getName());
            assertEquals("()J", getter.getDesc());
            assertTrue((getter.getAccess() & 0x0008) != 0);
        }

        @Test
        void createStaticSetter() throws IOException {
            int fieldAccess = new AccessBuilder().setPrivate().setStatic().build();
            FieldEntry field = classFile.createNewField(fieldAccess, "staticCounter", "I", null);

            MethodEntry setter = classFile.generateSetter(field, true);

            assertEquals("setStaticCounter", setter.getName());
            assertEquals("(I)V", setter.getDesc());
            assertTrue((setter.getAccess() & 0x0008) != 0);
        }

        @Test
        void createMethodWithArrayDescriptor() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "arrayMethod", "([I)[Ljava/lang/String;");

            assertEquals("arrayMethod", method.getName());
            assertEquals("([I)[Ljava/lang/String;", method.getDesc());
        }

        @Test
        void createMethodWithMultiDimensionalArray() {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "matrixMethod", "([[D)[[D");

            assertEquals("matrixMethod", method.getName());
            assertEquals("([[D)[[D", method.getDesc());
        }
    }

    @Nested
    class FieldInitializationTests {
        @Test
        void setStaticIntFieldInitialValue() throws IOException {
            int access = new AccessBuilder().setPrivate().setStatic().build();
            FieldEntry field = classFile.createNewField(access, "staticInt", "I", null);

            classFile.setFieldInitialValue(field, 42);

            assertNotNull(field);
        }

        @Test
        void setStaticLongFieldInitialValue() throws IOException {
            int access = new AccessBuilder().setPrivate().setStatic().build();
            FieldEntry field = classFile.createNewField(access, "staticLong", "J", null);

            classFile.setFieldInitialValue(field, 123456789L);

            assertNotNull(field);
        }

        @Test
        void setStaticFloatFieldInitialValue() throws IOException {
            int access = new AccessBuilder().setPrivate().setStatic().build();
            FieldEntry field = classFile.createNewField(access, "staticFloat", "F", null);

            classFile.setFieldInitialValue(field, 3.14f);

            assertNotNull(field);
        }

        @Test
        void setStaticDoubleFieldInitialValue() throws IOException {
            int access = new AccessBuilder().setPrivate().setStatic().build();
            FieldEntry field = classFile.createNewField(access, "staticDouble", "D", null);

            classFile.setFieldInitialValue(field, 2.71828);

            assertNotNull(field);
        }

        @Test
        void setStaticStringFieldInitialValue() throws IOException {
            int access = new AccessBuilder().setPrivate().setStatic().build();
            FieldEntry field = classFile.createNewField(access, "staticString", "Ljava/lang/String;", null);

            classFile.setFieldInitialValue(field, "Hello, World!");

            assertNotNull(field);
        }

        @Test
        void setInstanceIntFieldInitialValue() throws IOException {
            int access = new AccessBuilder().setPrivate().build();
            FieldEntry field = classFile.createNewField(access, "instanceInt", "I", null);

            classFile.setFieldInitialValue(field, 99);

            assertNotNull(field);
        }

        @Test
        void setInstanceStringFieldInitialValue() throws IOException {
            int access = new AccessBuilder().setPrivate().build();
            FieldEntry field = classFile.createNewField(access, "instanceString", "Ljava/lang/String;", null);

            classFile.setFieldInitialValue(field, "Test String");

            assertNotNull(field);
        }
    }

    @Nested
    class ClassNameUpdateTests {
        @Test
        void setClassNameUpdatesMethodOwners() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            classFile.createNewMethod(access, "testMethod", "V");

            classFile.setClassName("com/test/RenamedClass");

            for (MethodEntry method : classFile.getMethods()) {
                assertEquals("com/test/RenamedClass", method.getOwnerName());
            }
        }

        @Test
        void setClassNameUpdatesFieldOwners() {
            int access = new AccessBuilder().setPrivate().build();
            classFile.createNewField(access, "testField", "I", null);

            classFile.setClassName("com/test/RenamedClass");

            for (FieldEntry field : classFile.getFields()) {
                assertEquals("com/test/RenamedClass", field.getOwnerName());
            }
        }

        @Test
        void setClassNameUpdatesDescriptors() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "selfReference",
                "(Lcom/test/TestClass;)Lcom/test/TestClass;");

            classFile.setClassName("com/test/NewClass");

            boolean foundUpdatedDescriptor = false;
            for (com.tonic.parser.constpool.Item<?> item : classFile.getConstPool().getItems()) {
                if (item instanceof com.tonic.parser.constpool.Utf8Item) {
                    String value = ((com.tonic.parser.constpool.Utf8Item) item).getValue();
                    if (value.contains("Lcom/test/NewClass;")) {
                        foundUpdatedDescriptor = true;
                        break;
                    }
                }
            }
            assertTrue(foundUpdatedDescriptor);
        }
    }

    @Nested
    class InterfaceEdgeCases {
        @Test
        void addInterfaceCreatesNewConstPoolEntries() {
            int initialSize = classFile.getConstPool().getItems().size();

            classFile.addInterface("java/util/List");

            assertTrue(classFile.getConstPool().getItems().size() > initialSize);
        }

        @Test
        void addExistingInterfaceReusesConstPoolEntry() {
            classFile.addInterface("java/io/Serializable");
            int sizeAfterFirst = classFile.getConstPool().getItems().size();

            classFile.addInterface("java/io/Serializable");
            int sizeAfterSecond = classFile.getConstPool().getItems().size();

            assertEquals(sizeAfterFirst, sizeAfterSecond);
        }

        @Test
        void roundTripPreservesInterfaces() throws IOException {
            classFile.addInterface("java/io/Serializable");
            classFile.addInterface("java/lang/Cloneable");
            classFile.addInterface("java/util/RandomAccess");

            ClassFile reloaded = TestUtils.roundTrip(classFile);

            assertEquals(3, reloaded.getInterfaces().size());
        }
    }

    @Nested
    class ToStringEdgeCases {
        @Test
        void toStringWithNoInterfaces() {
            String str = classFile.toString();
            assertTrue(str.contains("Interfaces: None"));
        }

        @Test
        void toStringWithInterfaces() {
            classFile.addInterface("java/io/Serializable");
            String str = classFile.toString();
            assertTrue(str.contains("java/io/Serializable"));
        }

        @Test
        void toStringWithNoFields() {
            String str = classFile.toString();
            assertTrue(str.contains("Fields: None"));
        }

        @Test
        void toStringWithFields() {
            int access = new AccessBuilder().setPrivate().build();
            classFile.createNewField(access, "testField", "I", null);

            String str = classFile.toString();
            assertTrue(str.contains("testField"));
        }

        @Test
        void toStringIncludesAccessFlags() {
            String str = classFile.toString();
            assertTrue(str.contains("Access Flags"));
        }
    }

    @Nested
    class VersionTests {
        @Test
        void setMinorVersion() {
            classFile.setMinorVersion(3);
            assertEquals(3, classFile.getMinorVersion());
        }

        @Test
        void setMajorVersion() {
            classFile.setMajorVersion(52);
            assertEquals(52, classFile.getMajorVersion());
        }

        @Test
        void roundTripPreservesModifiedVersion() throws IOException {
            classFile.setMajorVersion(61);
            classFile.setMinorVersion(0);

            ClassFile reloaded = TestUtils.roundTrip(classFile);

            assertEquals(61, reloaded.getMajorVersion());
            assertEquals(0, reloaded.getMinorVersion());
        }
    }

    @Nested
    class FieldCreationEdgeCases {
        @Test
        void createFieldWithNullAttributesCreatesEmptyList() {
            int access = new AccessBuilder().setPrivate().build();
            FieldEntry field = classFile.createNewField(access, "testField", "I", null);

            assertNotNull(field.getAttributes());
            assertTrue(field.getAttributes().isEmpty());
        }

        @Test
        void createFieldNormalizesDescriptor() {
            int access = new AccessBuilder().setPrivate().build();
            FieldEntry field = classFile.createNewField(access, "stringField", "java.lang.String", null);

            assertEquals("Ljava/lang/String;", field.getDesc());
        }
    }

    @Nested
    class ComputeFramesEdgeCases {
        @Test
        void computeFramesForMethodWithoutCode() {
            int access = new AccessBuilder().setPublic().setAbstract().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "abstractMethod", "()V");

            classFile.computeFrames(method);

            assertNotNull(method);
        }

        @Test
        void computeFramesSkipsAbstractMethods() throws IOException {
            int access = new AccessBuilder().setPublic().setAbstract().build();
            classFile.createNewMethodWithDescriptor(access, "abstractMethod", "()V");

            int count = classFile.computeFrames();

            assertEquals(2, count);
        }
    }
}
