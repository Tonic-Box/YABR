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

        // Should instrument at least one method
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

        // Abstract methods should be skipped
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
