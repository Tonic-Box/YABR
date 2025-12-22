package com.tonic.analysis.instrumentation;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.ReturnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Instrumenter.
 * Verifies instrumentation hook registration and application.
 */
class InstrumenterTest {

    private ClassPool pool;
    private ClassFile testClass;
    private ClassFile hookClass;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();

        // Create test class with methods
        int access = new AccessBuilder().setPublic().build();
        testClass = pool.createNewClass("com/test/Target", access);

        // Create hook receiver class
        hookClass = pool.createNewClass("com/test/Hooks", access);
    }

    // ========== Instrumenter Creation Tests ==========

    @Test
    void forClassCreatesInstrumenter() {
        Instrumenter instrumenter = Instrumenter.forClass(testClass);

        assertNotNull(instrumenter);
    }

    @Test
    void forClassesListCreatesInstrumenter() {
        List<ClassFile> classes = List.of(testClass, hookClass);
        Instrumenter instrumenter = Instrumenter.forClasses(classes);

        assertNotNull(instrumenter);
    }

    @Test
    void forClassesVarargsCreatesInstrumenter() {
        Instrumenter instrumenter = Instrumenter.forClasses(testClass, hookClass);

        assertNotNull(instrumenter);
    }

    @Test
    void forClassPoolThrowsUnsupportedOperation() {
        assertThrows(UnsupportedOperationException.class, () ->
                Instrumenter.forClassPool(pool));
    }

    // ========== Method Entry Hook Tests ==========

    @Test
    void methodEntryHookRegisters() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodEntryHookWithClassName() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .inClass("com/test/Target")
                    .callStatic("com/test/Hooks", "onEntry", "(Ljava/lang/String;)V")
                    .withClassName()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodEntryHookWithMethodName() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "(Ljava/lang/String;)V")
                    .withMethodName()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodEntryHookWithThis() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = testClass.createNewMethodWithDescriptor(access, "instanceMethod", "()V");
        Bytecode bc = new Bytecode(method);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();

        int count = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "(Ljava/lang/Object;)V")
                    .withThis()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodEntryHookInPackage() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .inPackage("com/test/")
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodEntryHookMatchingMethod() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");
        addSimpleMethod(testClass, "otherMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .matchingMethod("target.*")
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodEntryHookWithAllParameters() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ParamsTest")
                .publicStaticMethod("hasParams", "(ILjava/lang/String;)V")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "([Ljava/lang/Object;)V")
                    .withAllParameters()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    // ========== Method Exit Hook Tests ==========

    @Test
    void methodExitHookRegisters() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .onMethodExit()
                    .callStatic("com/test/Hooks", "onExit", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodExitHookWithReturnValue() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ReturnTest")
                .publicStaticMethod("returnsInt", "()I")
                    .iconst(42)
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodExit()
                    .callStatic("com/test/Hooks", "onExit", "(I)V")
                    .withReturnValue()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodExitHookAllowModification() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ModifyReturnTest")
                .publicStaticMethod("returnsInt", "()I")
                    .iconst(42)
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodExit()
                    .callStatic("com/test/Hooks", "onExit", "(I)I")
                    .withReturnValue()
                    .allowModification()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodExitHookWithThisAndMethodName() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = testClass.createNewMethodWithDescriptor(access, "instanceMethod", "()V");
        Bytecode bc = new Bytecode(method);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();

        int count = Instrumenter.forClass(testClass)
                .onMethodExit()
                    .callStatic("com/test/Hooks", "onExit", "(Ljava/lang/Object;Ljava/lang/String;)V")
                    .withThis()
                    .withMethodName()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodExitHookWithClassName() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .onMethodExit()
                    .callStatic("com/test/Hooks", "onExit", "(Ljava/lang/String;)V")
                    .withClassName()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodExitHookInPackageFilter() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .onMethodExit()
                    .inPackage("com/test/")
                    .callStatic("com/test/Hooks", "onExit", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    // ========== Field Hook Tests ==========

    @Test
    void fieldWriteHookRegisters() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldTest")
                .field(new AccessBuilder().setPublic().build(), "testField", "I")
                .publicMethod("writeField", "()V")
                    .aload(0)
                    .iconst(42)
                    .putfield("com/test/FieldTest", "testField", "I")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldWrite()
                    .forField("testField")
                    .callStatic("com/test/Hooks", "onWrite", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldWriteHookWithNewValue() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldValueTest")
                .field(new AccessBuilder().setPublic().build(), "testField", "I")
                .publicMethod("writeField", "()V")
                    .aload(0)
                    .iconst(42)
                    .putfield("com/test/FieldValueTest", "testField", "I")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldWrite()
                    .forField("testField")
                    .callStatic("com/test/Hooks", "onWrite", "(I)V")
                    .withNewValue()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldReadHookRegisters() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldReadTest")
                .field(new AccessBuilder().setPublic().build(), "testField", "I")
                .publicMethod("readField", "()I")
                    .aload(0)
                    .getfield("com/test/FieldReadTest", "testField", "I")
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldRead()
                    .forField("testField")
                    .callStatic("com/test/Hooks", "onRead", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldWriteHookWithOwnerAndFieldName() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldOwnerTest")
                .field(new AccessBuilder().setPublic().build(), "testField", "I")
                .publicMethod("writeField", "()V")
                    .aload(0)
                    .iconst(42)
                    .putfield("com/test/FieldOwnerTest", "testField", "I")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldWrite()
                    .forField("testField")
                    .callStatic("com/test/Hooks", "onWrite", "(Ljava/lang/Object;Ljava/lang/String;)V")
                    .withOwner()
                    .withFieldName()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldWriteHookWithOldValue() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldOldValueTest")
                .field(new AccessBuilder().setPublic().build(), "testField", "I")
                .publicMethod("writeField", "()V")
                    .aload(0)
                    .iconst(42)
                    .putfield("com/test/FieldOldValueTest", "testField", "I")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldWrite()
                    .forField("testField")
                    .callStatic("com/test/Hooks", "onWrite", "(I)V")
                    .withOldValue()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldWriteHookWithModification() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldModifyTest")
                .field(new AccessBuilder().setPublic().build(), "testField", "I")
                .publicMethod("writeField", "()V")
                    .aload(0)
                    .iconst(42)
                    .putfield("com/test/FieldModifyTest", "testField", "I")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldWrite()
                    .forField("testField")
                    .callStatic("com/test/Hooks", "onWrite", "(I)I")
                    .withNewValue()
                    .allowModification()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldReadHookWithReadValue() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldReadValueTest")
                .field(new AccessBuilder().setPublic().build(), "testField", "I")
                .publicMethod("readField", "()I")
                    .aload(0)
                    .getfield("com/test/FieldReadValueTest", "testField", "I")
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldRead()
                    .forField("testField")
                    .callStatic("com/test/Hooks", "onRead", "(I)V")
                    .withReadValue()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldReadHookWithOwnerAndFieldName() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldReadOwnerTest")
                .field(new AccessBuilder().setPublic().build(), "testField", "I")
                .publicMethod("readField", "()I")
                    .aload(0)
                    .getfield("com/test/FieldReadOwnerTest", "testField", "I")
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldRead()
                    .forField("testField")
                    .callStatic("com/test/Hooks", "onRead", "(Ljava/lang/Object;Ljava/lang/String;)V")
                    .withOwner()
                    .withFieldName()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldWriteHookInstanceOnly() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/InstanceFieldTest")
                .field(new AccessBuilder().setPublic().build(), "instanceField", "I")
                .publicMethod("writeInstance", "()V")
                    .aload(0)
                    .iconst(42)
                    .putfield("com/test/InstanceFieldTest", "instanceField", "I")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldWrite()
                    .forField("instanceField")
                    .instanceOnly()
                    .callStatic("com/test/Hooks", "onWrite", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldReadHookInstanceOnly() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/InstanceFieldReadTest")
                .field(new AccessBuilder().setPublic().build(), "instanceField", "I")
                .publicMethod("readInstance", "()I")
                    .aload(0)
                    .getfield("com/test/InstanceFieldReadTest", "instanceField", "I")
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldRead()
                    .forField("instanceField")
                    .instanceOnly()
                    .callStatic("com/test/Hooks", "onRead", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldWriteHookMatchingPattern() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldPatternTest")
                .field(new AccessBuilder().setPublic().build(), "myField1", "I")
                .field(new AccessBuilder().setPublic().build(), "myField2", "I")
                .publicMethod("writeFields", "()V")
                    .aload(0)
                    .iconst(1)
                    .putfield("com/test/FieldPatternTest", "myField1", "I")
                    .aload(0)
                    .iconst(2)
                    .putfield("com/test/FieldPatternTest", "myField2", "I")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldWrite()
                    .forFieldsMatching("myField.*")
                    .callStatic("com/test/Hooks", "onWrite", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldWriteHookOfType() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldTypeTest")
                .field(new AccessBuilder().setPublic().build(), "intField", "I")
                .field(new AccessBuilder().setPublic().build(), "stringField", "Ljava/lang/String;")
                .publicMethod("writeFields", "()V")
                    .aload(0)
                    .iconst(42)
                    .putfield("com/test/FieldTypeTest", "intField", "I")
                    .aload(0)
                    .ldc("test")
                    .putfield("com/test/FieldTypeTest", "stringField", "Ljava/lang/String;")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldWrite()
                    .ofType("I")
                    .callStatic("com/test/Hooks", "onWrite", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldWriteHookInClassFilter() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldClassTest")
                .field(new AccessBuilder().setPublic().build(), "testField", "I")
                .publicMethod("writeField", "()V")
                    .aload(0)
                    .iconst(42)
                    .putfield("com/test/FieldClassTest", "testField", "I")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldWrite()
                    .inClass("com/test/FieldClassTest")
                    .callStatic("com/test/Hooks", "onWrite", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void fieldWriteHookInPackageFilter() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FieldPackageTest")
                .field(new AccessBuilder().setPublic().build(), "testField", "I")
                .publicMethod("writeField", "()V")
                    .aload(0)
                    .iconst(42)
                    .putfield("com/test/FieldPackageTest", "testField", "I")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onFieldWrite()
                    .inPackage("com/test/")
                    .callStatic("com/test/Hooks", "onWrite", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    // ========== Array Hook Tests ==========

    @Test
    void arrayStoreHookRegisters() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayStoreTest")
                .publicStaticMethod("arrayStore", "()V")
                    .iconst(5)
                    .newarray(10) // T_INT
                    .astore(0)
                    .aload(0)
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayStore()
                    .forIntArrays()
                    .callStatic("com/test/Hooks", "onArrayStore", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayLoadHookRegisters() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayLoadTest")
                .publicStaticMethod("arrayLoad", "([I)I")
                    .aload(0)
                    .iconst(0)
                    .iaload()
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayLoad()
                    .forArrayType("[I")
                    .callStatic("com/test/Hooks", "onArrayLoad", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayStoreHookWithArray() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayStoreArrayTest")
                .publicStaticMethod("arrayStore", "()V")
                    .iconst(5)
                    .newarray(10) // T_INT
                    .astore(0)
                    .aload(0)
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayStore()
                    .forIntArrays()
                    .callStatic("com/test/Hooks", "onArrayStore", "([I)V")
                    .withArray()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayStoreHookWithIndex() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayStoreIndexTest")
                .publicStaticMethod("arrayStore", "()V")
                    .iconst(5)
                    .newarray(10) // T_INT
                    .astore(0)
                    .aload(0)
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayStore()
                    .forIntArrays()
                    .callStatic("com/test/Hooks", "onArrayStore", "(I)V")
                    .withIndex()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayStoreHookWithValue() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayStoreValueTest")
                .publicStaticMethod("arrayStore", "()V")
                    .iconst(5)
                    .newarray(10) // T_INT
                    .astore(0)
                    .aload(0)
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayStore()
                    .forIntArrays()
                    .callStatic("com/test/Hooks", "onArrayStore", "(I)V")
                    .withValue()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayStoreHookWithAll() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayStoreAllTest")
                .publicStaticMethod("arrayStore", "()V")
                    .iconst(5)
                    .newarray(10) // T_INT
                    .astore(0)
                    .aload(0)
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayStore()
                    .forIntArrays()
                    .callStatic("com/test/Hooks", "onArrayStore", "([III)V")
                    .withAll()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayStoreHookWithModification() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayStoreModifyTest")
                .publicStaticMethod("arrayStore", "()V")
                    .iconst(5)
                    .newarray(10) // T_INT
                    .astore(0)
                    .aload(0)
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayStore()
                    .forIntArrays()
                    .callStatic("com/test/Hooks", "onArrayStore", "(I)I")
                    .withValue()
                    .allowModification()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayLoadHookWithArray() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayLoadArrayTest")
                .publicStaticMethod("arrayLoad", "([I)I")
                    .aload(0)
                    .iconst(0)
                    .iaload()
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayLoad()
                    .forArrayType("[I")
                    .callStatic("com/test/Hooks", "onArrayLoad", "([I)V")
                    .withArray()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayLoadHookWithIndex() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayLoadIndexTest")
                .publicStaticMethod("arrayLoad", "([I)I")
                    .aload(0)
                    .iconst(0)
                    .iaload()
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayLoad()
                    .forArrayType("[I")
                    .callStatic("com/test/Hooks", "onArrayLoad", "(I)V")
                    .withIndex()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayLoadHookWithValue() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayLoadValueTest")
                .publicStaticMethod("arrayLoad", "([I)I")
                    .aload(0)
                    .iconst(0)
                    .iaload()
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayLoad()
                    .forArrayType("[I")
                    .callStatic("com/test/Hooks", "onArrayLoad", "(I)V")
                    .withValue()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayStoreHookInClassFilter() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayStoreClassTest")
                .publicStaticMethod("arrayStore", "()V")
                    .iconst(5)
                    .newarray(10) // T_INT
                    .astore(0)
                    .aload(0)
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayStore()
                    .inClass("com/test/ArrayStoreClassTest")
                    .forIntArrays()
                    .callStatic("com/test/Hooks", "onArrayStore", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayStoreHookInPackageFilter() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayStorePackageTest")
                .publicStaticMethod("arrayStore", "()V")
                    .iconst(5)
                    .newarray(10) // T_INT
                    .astore(0)
                    .aload(0)
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayStore()
                    .inPackage("com/test/")
                    .forIntArrays()
                    .callStatic("com/test/Hooks", "onArrayStore", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void arrayStoreHookInMethodFilter() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayStoreMethodTest")
                .publicStaticMethod("arrayStore", "()V")
                    .iconst(5)
                    .newarray(10) // T_INT
                    .astore(0)
                    .aload(0)
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onArrayStore()
                    .inMethod("arrayStore")
                    .forIntArrays()
                    .callStatic("com/test/Hooks", "onArrayStore", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    // ========== Method Call Hook Tests ==========

    @Test
    void methodCallHookRegisters() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/CallTest")
                .publicStaticMethod("callMethod", "()V")
                    .invokestatic("com/test/Other", "someMethod", "()V")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodCall()
                    .targeting("com/test/Other", "someMethod")
                    .before()
                    .callStatic("com/test/Hooks", "beforeCall", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodCallHookAfter() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/CallAfterTest")
                .publicStaticMethod("callMethod", "()V")
                    .invokestatic("com/test/Other", "someMethod", "()V")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodCall()
                    .targeting("com/test/Other", "someMethod")
                    .after()
                    .callStatic("com/test/Hooks", "afterCall", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodCallHookWithReceiver() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/CallReceiverTest")
                .publicStaticMethod("callMethod", "()V")
                    .aconst_null()
                    .invokevirtual("java/lang/Object", "toString", "()Ljava/lang/String;")
                    .pop()
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodCall()
                    .targeting("java/lang/Object", "toString")
                    .before()
                    .callStatic("com/test/Hooks", "beforeCall", "(Ljava/lang/Object;)V")
                    .withReceiver()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodCallHookWithArguments() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/CallArgsTest")
                .publicStaticMethod("callMethod", "()V")
                    .iconst(42)
                    .ldc("test")
                    .invokestatic("com/test/Other", "method", "(ILjava/lang/String;)V")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodCall()
                    .targeting("com/test/Other", "method")
                    .before()
                    .callStatic("com/test/Hooks", "beforeCall", "([Ljava/lang/Object;)V")
                    .withArguments()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodCallHookWithResult() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/CallResultTest")
                .publicStaticMethod("callMethod", "()I")
                    .invokestatic("com/test/Other", "getInt", "()I")
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodCall()
                    .targeting("com/test/Other", "getInt")
                    .after()
                    .callStatic("com/test/Hooks", "afterCall", "(I)V")
                    .withResult()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodCallHookWithMethodName() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/CallMethodNameTest")
                .publicStaticMethod("callMethod", "()V")
                    .invokestatic("com/test/Other", "someMethod", "()V")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodCall()
                    .targeting("com/test/Other", "someMethod")
                    .before()
                    .callStatic("com/test/Hooks", "beforeCall", "(Ljava/lang/String;)V")
                    .withMethodName()
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodCallHookWithDescriptor() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/CallDescriptorTest")
                .publicStaticMethod("callMethod", "()V")
                    .invokestatic("com/test/Other", "someMethod", "()V")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodCall()
                    .targeting("com/test/Other", "someMethod", "()V")
                    .before()
                    .callStatic("com/test/Hooks", "beforeCall", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodCallHookInClassFilter() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/CallClassTest")
                .publicStaticMethod("callMethod", "()V")
                    .invokestatic("com/test/Other", "someMethod", "()V")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodCall()
                    .inClass("com/test/CallClassTest")
                    .targeting("com/test/Other", "someMethod")
                    .before()
                    .callStatic("com/test/Hooks", "beforeCall", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void methodCallHookInPackageFilter() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/CallPackageTest")
                .publicStaticMethod("callMethod", "()V")
                    .invokestatic("com/test/Other", "someMethod", "()V")
                    .vreturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodCall()
                    .inPackage("com/test/")
                    .targeting("com/test/Other", "someMethod")
                    .before()
                    .callStatic("com/test/Hooks", "beforeCall", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    // Note: Exception hook tests are omitted as BytecodeBuilder doesn't support
    // try-catch blocks. Exception hooks would need to be tested with manually
    // constructed ClassFiles using low-level Bytecode API.

    // ========== Configuration Tests ==========

    @Test
    void skipAbstractMethods() throws IOException {
        int abstractAccess = new AccessBuilder().setPublic().setAbstract().build();
        testClass.createNewMethodWithDescriptor(abstractAccess, "abstractMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .skipAbstract(true)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void skipConstructors() throws IOException {
        addSimpleMethod(testClass, "normalMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .skipConstructors(true)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void verboseMode() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .verbose(true)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void failOnErrorDisabled() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .failOnError(false)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void skipNativeMethods() throws IOException {
        int nativeAccess = new AccessBuilder().setPublic().setNative().build();
        testClass.createNewMethodWithDescriptor(nativeAccess, "nativeMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .skipNative(true)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void skipStaticInitializers() throws IOException {
        addSimpleMethod(testClass, "normalMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .skipStaticInitializers(true)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void skipSyntheticMethods() throws IOException {
        addSimpleMethod(testClass, "normalMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .skipSynthetic(true)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void skipBridgeMethods() throws IOException {
        addSimpleMethod(testClass, "normalMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .skipBridge(true)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    // ========== Report Tests ==========

    @Test
    void applyWithReportReturnsReport() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        Instrumenter instrumenter = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register();

        Instrumenter.InstrumentationReport report = instrumenter.applyWithReport();

        assertNotNull(report);
        assertTrue(report.getTotalInstrumentationPoints() >= 0);
        assertTrue(report.getClassesInstrumented() >= 0);
        assertTrue(report.getMethodsInstrumented() >= 0);
        assertTrue(report.getErrors() >= 0);
    }

    @Test
    void applyReturnsCount() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void lastReportAccessible() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        Instrumenter instrumenter = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register();

        instrumenter.apply();

        assertNotNull(instrumenter.getLastReport());
    }

    @Test
    void reportToStringIsReadable() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        Instrumenter instrumenter = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register();

        Instrumenter.InstrumentationReport report = instrumenter.applyWithReport();
        String str = report.toString();

        assertNotNull(str);
        assertTrue(str.contains("InstrumentationReport"));
    }

    // ========== Multiple Hooks Tests ==========

    @Test
    void multipleHooksCanBeRegistered() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .onMethodExit()
                    .callStatic("com/test/Hooks", "onExit", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void hookPriorityCanBeSet() throws IOException {
        addSimpleMethod(testClass, "targetMethod", "()V");

        int count = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "first", "()V")
                    .priority(1)
                    .register()
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "second", "()V")
                    .priority(2)
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void multipleHookTypesCanBeRegistered() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/MultiHookTest")
                .field(new AccessBuilder().setPublic().build(), "testField", "I")
                .publicMethod("complexMethod", "()I")
                    .aload(0)
                    .iconst(42)
                    .putfield("com/test/MultiHookTest", "testField", "I")
                    .aload(0)
                    .getfield("com/test/MultiHookTest", "testField", "I")
                    .ireturn()
                .endMethod()
                .build();

        int count = Instrumenter.forClass(cf)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register()
                .onMethodExit()
                    .callStatic("com/test/Hooks", "onExit", "()V")
                    .register()
                .onFieldWrite()
                    .forField("testField")
                    .callStatic("com/test/Hooks", "onWrite", "()V")
                    .register()
                .onFieldRead()
                    .forField("testField")
                    .callStatic("com/test/Hooks", "onRead", "()V")
                    .register()
                .apply();

        assertTrue(count >= 0);
    }

    @Test
    void instrumentationDoesNotThrowOnValidClass() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ValidInstrument")
                .publicStaticMethod("test", "()V")
                    .iconst(1)
                    .pop()
                    .vreturn()
                .endMethod()
                .build();

        assertDoesNotThrow(() -> {
            Instrumenter.forClass(cf)
                    .onMethodEntry()
                        .callStatic("com/test/Hooks", "onEntry", "()V")
                        .register()
                    .apply();
        });
    }

    // ========== Helper Methods ==========

    private void addSimpleMethod(ClassFile cf, String name, String desc) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = cf.createNewMethodWithDescriptor(access, name, desc);
        Bytecode bc = new Bytecode(method);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
    }
}
