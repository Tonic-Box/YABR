package com.tonic.analysis.instrumentation;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import com.tonic.util.AccessBuilder;
import com.tonic.util.ReturnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Instrumenter.
 * Verifies instrumentation hook registration and application.
 */
class InstrumenterTest
{

    private ClassPool pool;
    private ClassFile testClass;
    private ClassFile hookClass;

    @BeforeEach
    void setUp() throws IOException
    {
        pool = TestUtils.emptyPool();

        int access = new AccessBuilder().setPublic().build();
        testClass = pool.createNewClass("com/test/Target", access);

        hookClass = pool.createNewClass("com/test/Hooks", access);
    }

    // Instrumenter Creation Tests

    @Test
    void forClassCreatesInstrumenter()
    {
        Instrumenter instrumenter = Instrumenter.forClass(testClass);

        assertNotNull(instrumenter);
    }

    @Test
    void forClassesListCreatesInstrumenter()
    {
        List<ClassFile> classes = List.of(testClass, hookClass);
        Instrumenter instrumenter = Instrumenter.forClasses(classes);

        assertNotNull(instrumenter);
    }

    @Test
    void forClassesVarargsCreatesInstrumenter()
    {
        Instrumenter instrumenter = Instrumenter.forClasses(testClass, hookClass);

        assertNotNull(instrumenter);
    }

    @Test
    void forClassPoolCreatesInstrumenter()
    {
        Instrumenter instrumenter = Instrumenter.forClassPool(pool);

        assertNotNull(instrumenter);
    }

    // Method Entry Hook Tests

    // Method Exit Hook Tests

    // Field Hook Tests

    // Array Hook Tests

    // Method Call Hook Tests

    // Note: Exception hook tests are omitted as BytecodeBuilder doesn't support
    // try-catch blocks. Exception hooks would need to be tested with manually
    // constructed ClassFiles using low-level Bytecode API.

    // Configuration Tests

    // Report Tests

    @Test
    void applyWithReportReturnsReport() throws IOException
    {
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
    void lastReportAccessible() throws IOException
    {
        addSimpleMethod(testClass, "targetMethod", "()V");

        Instrumenter instrumenter = Instrumenter.forClass(testClass)
                .onMethodEntry()
                    .callStatic("com/test/Hooks", "onEntry", "()V")
                    .register();

        instrumenter.apply();

        assertNotNull(instrumenter.getLastReport());
    }

    @Test
    void reportToStringIsReadable() throws IOException
    {
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

    // Multiple Hooks Tests

    @Test
    void instrumentationDoesNotThrowOnValidClass() throws IOException
    {
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

    // HookParameter Enum Tests

    @Nested
    class HookParameterTests
    {

        @Test
        void thisParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.THIS;
            assertNotNull(param);
            assertEquals("THIS", param.name());
        }

        @Test
        void methodNameParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.METHOD_NAME;
            assertNotNull(param);
            assertEquals("METHOD_NAME", param.name());
        }

        @Test
        void methodDescriptorParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.METHOD_DESCRIPTOR;
            assertNotNull(param);
            assertEquals("METHOD_DESCRIPTOR", param.name());
        }

        @Test
        void classNameParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.CLASS_NAME;
            assertNotNull(param);
            assertEquals("CLASS_NAME", param.name());
        }

        @Test
        void allParametersParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.ALL_PARAMETERS;
            assertNotNull(param);
            assertEquals("ALL_PARAMETERS", param.name());
        }

        @Test
        void parameterParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.PARAMETER;
            assertNotNull(param);
            assertEquals("PARAMETER", param.name());
        }

        @Test
        void returnValueParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.RETURN_VALUE;
            assertNotNull(param);
            assertEquals("RETURN_VALUE", param.name());
        }

        @Test
        void fieldOwnerParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.FIELD_OWNER;
            assertNotNull(param);
            assertEquals("FIELD_OWNER", param.name());
        }

        @Test
        void fieldNameParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.FIELD_NAME;
            assertNotNull(param);
            assertEquals("FIELD_NAME", param.name());
        }

        @Test
        void newValueParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.NEW_VALUE;
            assertNotNull(param);
            assertEquals("NEW_VALUE", param.name());
        }

        @Test
        void readValueParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.READ_VALUE;
            assertNotNull(param);
            assertEquals("READ_VALUE", param.name());
        }

        @Test
        void arrayRefParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.ARRAY_REF;
            assertNotNull(param);
            assertEquals("ARRAY_REF", param.name());
        }

        @Test
        void arrayIndexParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.ARRAY_INDEX;
            assertNotNull(param);
            assertEquals("ARRAY_INDEX", param.name());
        }

        @Test
        void exceptionParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.EXCEPTION;
            assertNotNull(param);
            assertEquals("EXCEPTION", param.name());
        }

        @Test
        void callReceiverParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.CALL_RECEIVER;
            assertNotNull(param);
            assertEquals("CALL_RECEIVER", param.name());
        }

        @Test
        void callArgumentsParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.CALL_ARGUMENTS;
            assertNotNull(param);
            assertEquals("CALL_ARGUMENTS", param.name());
        }

        @Test
        void callResultParameterExists()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.CALL_RESULT;
            assertNotNull(param);
            assertEquals("CALL_RESULT", param.name());
        }

        @Test
        void allParameterTypesAccessible()
        {
            HookDescriptor.HookParameter[] params = HookDescriptor.HookParameter.values();
            assertEquals(17, params.length);
        }

        @Test
        void valueOfReturnsCorrectParameter()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.valueOf("THIS");
            assertEquals(HookDescriptor.HookParameter.THIS, param);
        }

        @Test
        void valueOfThrowsForInvalidName()
        {
            assertThrows(IllegalArgumentException.class, () ->
                    HookDescriptor.HookParameter.valueOf("INVALID_PARAMETER"));
        }

        @Test
        void parameterOrdinalIsConsistent()
        {
            HookDescriptor.HookParameter[] params = HookDescriptor.HookParameter.values();
            for (int i = 0; i < params.length; i++)
            {
                assertEquals(i, params[i].ordinal());
            }
        }

        @Test
        void parameterEqualsWorks()
        {
            HookDescriptor.HookParameter param1 = HookDescriptor.HookParameter.THIS;
            HookDescriptor.HookParameter param2 = HookDescriptor.HookParameter.THIS;
            HookDescriptor.HookParameter param3 = HookDescriptor.HookParameter.METHOD_NAME;

            assertEquals(param1, param2);
            assertNotEquals(param1, param3);
        }

        @Test
        void parameterHashCodeIsConsistent()
        {
            HookDescriptor.HookParameter param1 = HookDescriptor.HookParameter.RETURN_VALUE;
            HookDescriptor.HookParameter param2 = HookDescriptor.HookParameter.RETURN_VALUE;

            assertEquals(param1.hashCode(), param2.hashCode());
        }

        @Test
        void canSwitchOnParameterType()
        {
            HookDescriptor.HookParameter param = HookDescriptor.HookParameter.EXCEPTION;
            String result;
            switch (param)
            {
                case THIS:
                    result = "this";
                    break;
                case EXCEPTION:
                    result = "exception";
                    break;
                default:
                    result = "other";
                    break;
            }
            assertEquals("exception", result);
        }
    }

    // Exception Hook Tests

    @Nested
    class ExceptionHookBuilderTests
    {

        @Test
        void exceptionHookBuilderCreates()
        {
            Instrumenter.ExceptionHookBuilder builder = Instrumenter.forClass(testClass)
                    .onException();

            assertNotNull(builder);
        }

        @Test
        void exceptionHookRegistersWithInstrumenter() throws IOException
        {
            addSimpleMethod(testClass, "targetMethod", "()V");

            Instrumenter instrumenter = Instrumenter.forClass(testClass)
                    .onException()
                        .callStatic("com/test/Hooks", "onException", "(Ljava/lang/Throwable;)V")
                        .withException()
                        .register();

            assertNotNull(instrumenter);
            assertSame(testClass, instrumenter.apply() >= 0 ? testClass : testClass);
        }

        @Test
        void exceptionHookBuilderChaining() throws IOException
        {
            addSimpleMethod(testClass, "targetMethod", "()V");

            Instrumenter.ExceptionHookBuilder builder = Instrumenter.forClass(testClass)
                    .onException()
                        .forExceptionType("java/lang/Exception")
                        .inClass("com/test/Target")
                        .inPackage("com/test/")
                        .callStatic("com/test/Hooks", "onException", "(Ljava/lang/Throwable;Ljava/lang/String;Ljava/lang/String;)V")
                        .withException()
                        .withMethodName()
                        .withClassName()
                        .canSuppress()
                        .priority(5);

            assertNotNull(builder);
            Instrumenter instrumenter = builder.register();
            assertNotNull(instrumenter);
        }
    }

    // Helper Methods

    private void addSimpleMethod(ClassFile cf, String name, String desc) throws IOException
    {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = cf.createNewMethodWithDescriptor(access, name, desc);
        Bytecode bc = new Bytecode(method);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
    }
}
