package com.tonic.parser;

import com.tonic.testutil.TestClassLoader;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClassFile functionality.
 * Covers class creation, modification, and write/round-trip operations.
 */
class ClassFileTest {

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
    void classHasCorrectName() {
        assertEquals("com/test/TestClass", classFile.getClassName());
    }

    @Test
    void classHasObjectSuperclass() {
        assertEquals("java/lang/Object", classFile.getSuperClassName());
    }

    @Test
    void classHasCorrectVersion() {
        assertEquals(55, classFile.getMajorVersion()); // Java 11
        assertEquals(0, classFile.getMinorVersion());
    }

    @Test
    void classHasConstPool() {
        assertNotNull(classFile.getConstPool());
    }

    // ========== Name Modification Tests ==========

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

    // ========== Interface Tests ==========

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

    // ========== Field Tests ==========

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

    // ========== Method Tests ==========

    @Test
    void newClassHasDefaultMethods() {
        // Should have <init> and <clinit>
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

    // ========== Write/Round-Trip Tests ==========

    @Test
    void writeProducesValidBytes() throws IOException {
        byte[] bytes = classFile.write();

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        // Verify magic number
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

    // ========== JVM Verification Tests ==========

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

    // ========== Frame Computation Tests ==========

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

        // Use the actual descriptor that was created
        boolean computed = classFile.computeFrames("targetMethod", method.getDesc());
        assertTrue(computed);
    }

    @Test
    void computeFramesForNonexistentMethodReturnsFalse() {
        boolean computed = classFile.computeFrames("nonexistent", "()V");
        assertFalse(computed);
    }

    // ========== toString Tests ==========

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
}
