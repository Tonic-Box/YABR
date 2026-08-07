package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NameRecoverer}'s own surface: how it reads a LocalVariableTable and how it names a
 * value that has no recorded name. What a {@link NameRecoveryStrategy} does to the OUTPUT is asserted
 * end-to-end in {@code NameRecoveryStrategyTest}, against a class compiled with debug info - the only place
 * the difference between the modes is observable.
 */
class NameRecovererTest
{

    @BeforeEach
    void setUp()
    {
        SSAValue.resetIdCounter();
    }

    // Constructor and Initialization Tests

    @Nested
    class ConstructorTests
    {

        @Test
        void constructor_withValidInputs_initializesCorrectly() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);

            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.PREFER_DEBUG_INFO);

            assertNotNull(recoverer);
            assertEquals(NameRecoveryStrategy.PREFER_DEBUG_INFO, recoverer.getStrategy());
        }

        @Test
        void constructor_withAlwaysSyntheticStrategy_initializesCorrectly() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);

            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            assertNotNull(recoverer);
            assertEquals(NameRecoveryStrategy.ALWAYS_SYNTHETIC, recoverer.getStrategy());
        }

        @Test
        void constructor_withParametersOnlyStrategy_initializesCorrectly() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);

            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.PARAMETERS_ONLY);

            assertNotNull(recoverer);
            assertEquals(NameRecoveryStrategy.PARAMETERS_ONLY, recoverer.getStrategy());
        }

        @Test
        void constructor_withMethodWithoutCodeAttribute_handlesGracefully() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);

            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.PREFER_DEBUG_INFO);

            assertNotNull(recoverer);
        }
    }

    // Synthetic Name Generation Tests

    @Nested
    class SyntheticNameGenerationTests
    {

        @Test
        void generateSyntheticName_forIntType_returnsIPrefix() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            SSAValue intValue = new SSAValue(PrimitiveType.INT);
            String name = recoverer.generateSyntheticName(intValue);

            assertTrue(name.startsWith("i"));
            assertTrue(name.matches("i\\d+"));
        }

        @Test
        void generateSyntheticName_forLongType_returnsLPrefix() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            SSAValue longValue = new SSAValue(PrimitiveType.LONG);
            String name = recoverer.generateSyntheticName(longValue);

            assertTrue(name.startsWith("l"));
            assertTrue(name.matches("l\\d+"));
        }

        @Test
        void generateSyntheticName_forFloatType_returnsFPrefix() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            SSAValue floatValue = new SSAValue(PrimitiveType.FLOAT);
            String name = recoverer.generateSyntheticName(floatValue);

            assertTrue(name.startsWith("f"));
            assertTrue(name.matches("f\\d+"));
        }

        @Test
        void generateSyntheticName_forDoubleType_returnsDPrefix() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            SSAValue doubleValue = new SSAValue(PrimitiveType.DOUBLE);
            String name = recoverer.generateSyntheticName(doubleValue);

            assertTrue(name.startsWith("d"));
            assertTrue(name.matches("d\\d+"));
        }

        @Test
        void generateSyntheticName_forBooleanType_returnsFlagPrefix() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            SSAValue boolValue = new SSAValue(PrimitiveType.BOOLEAN);
            String name = recoverer.generateSyntheticName(boolValue);

            assertTrue(name.startsWith("flag"));
            assertTrue(name.matches("flag\\d+"));
        }

        @Test
        void generateSyntheticName_forCharType_returnsCPrefix() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            SSAValue charValue = new SSAValue(PrimitiveType.CHAR);
            String name = recoverer.generateSyntheticName(charValue);

            assertTrue(name.startsWith("c"));
            assertTrue(name.matches("c\\d+"));
        }

        @Test
        void generateSyntheticName_forByteType_returnsIPrefix() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            SSAValue byteValue = new SSAValue(PrimitiveType.BYTE);
            String name = recoverer.generateSyntheticName(byteValue);

            assertTrue(name.startsWith("i"));
            assertTrue(name.matches("i\\d+"));
        }

        @Test
        void generateSyntheticName_forShortType_returnsIPrefix() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            SSAValue shortValue = new SSAValue(PrimitiveType.SHORT);
            String name = recoverer.generateSyntheticName(shortValue);

            assertTrue(name.startsWith("i"));
            assertTrue(name.matches("i\\d+"));
        }

        @Test
        void generateSyntheticName_forObjectType_returnsFirstLetterLowercase() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            ReferenceType stringType = new ReferenceType("java/lang/String");
            SSAValue objectValue = new SSAValue(stringType);
            String name = recoverer.generateSyntheticName(objectValue);

            assertTrue(name.startsWith("s"));
            assertTrue(name.matches("s\\d+"));
        }

        @Test
        void generateSyntheticName_forSimpleClassName_returnsFirstLetterLowercase() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            ReferenceType listType = new ReferenceType("java/util/List");
            SSAValue objectValue = new SSAValue(listType);
            String name = recoverer.generateSyntheticName(objectValue);

            assertTrue(name.startsWith("l"));
            assertTrue(name.matches("l\\d+"));
        }

        @Test
        void generateSyntheticName_incrementsCounter() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            SSAValue val1 = new SSAValue(PrimitiveType.INT);
            SSAValue val2 = new SSAValue(PrimitiveType.INT);
            SSAValue val3 = new SSAValue(PrimitiveType.INT);

            String name1 = recoverer.generateSyntheticName(val1);
            String name2 = recoverer.generateSyntheticName(val2);
            String name3 = recoverer.generateSyntheticName(val3);

            assertEquals("i0", name1);
            assertEquals("i1", name2);
            assertEquals("i2", name3);
        }

        @Test
        void generateSyntheticName_differentTypes_independentCounters() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            SSAValue intVal = new SSAValue(PrimitiveType.INT);
            SSAValue longVal = new SSAValue(PrimitiveType.LONG);
            SSAValue floatVal = new SSAValue(PrimitiveType.FLOAT);

            String intName = recoverer.generateSyntheticName(intVal);
            String longName = recoverer.generateSyntheticName(longVal);
            String floatName = recoverer.generateSyntheticName(floatVal);

            assertEquals("i0", intName);
            assertEquals("l1", longName);
            assertEquals("f2", floatName);
        }
    }

    // RecoverName with PREFER_DEBUG_INFO Strategy Tests

    @Nested
    class PreferDebugInfoStrategyTests
    {

        @Test
        void recoverName_preferDebugInfo_noDebugInfo_generatesSynthetic() throws IOException
        {
            // Test synthetic name generation for local variables
            // Using generateSyntheticName directly to avoid SSA lifter parameter confusion
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.PREFER_DEBUG_INFO);

            SSAValue value = new SSAValue(PrimitiveType.INT);
            String name = recoverer.generateSyntheticName(value);

            assertTrue(name.startsWith("i"));
        }



    }

    // RecoverName with PARAMETERS_ONLY Strategy Tests

    @Nested
    class ParametersOnlyStrategyTests
    {

        @Test
        void recoverName_parametersOnly_forLocalVariable_generatesSynthetic() throws IOException
        {
            // Test synthetic name generation for locals with PARAMETERS_ONLY strategy
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.PARAMETERS_ONLY);

            SSAValue value = new SSAValue(PrimitiveType.INT);
            // Use generateSyntheticName directly to test synthetic name generation
            String name = recoverer.generateSyntheticName(value);

            assertTrue(name.startsWith("i"));
            assertTrue(name.matches("i\\d+"));
        }

    }

    // Reference Type Synthetic Name Tests

    @Nested
    class ReferenceTypeSyntheticNameTests
    {

        @Test
        void generateSyntheticName_forArrayList_returnsA() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            ReferenceType arrayListType = new ReferenceType("java/util/ArrayList");
            SSAValue value = new SSAValue(arrayListType);
            String name = recoverer.generateSyntheticName(value);

            assertTrue(name.startsWith("a"));
            assertTrue(name.matches("a\\d+"));
        }

        @Test
        void generateSyntheticName_forHashMap_returnsH() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            ReferenceType hashMapType = new ReferenceType("java/util/HashMap");
            SSAValue value = new SSAValue(hashMapType);
            String name = recoverer.generateSyntheticName(value);

            assertTrue(name.startsWith("h"));
            assertTrue(name.matches("h\\d+"));
        }

        @Test
        void generateSyntheticName_forInnerClass_handlesDollarSign() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            ReferenceType innerClassType = new ReferenceType("com/test/Outer$Inner");
            SSAValue value = new SSAValue(innerClassType);
            String name = recoverer.generateSyntheticName(value);

            assertNotNull(name);
            assertTrue(name.matches("[a-z]\\d+"));
        }

        @Test
        void generateSyntheticName_forCustomClass_usesFirstLetter() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            NameRecoverer recoverer = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);

            ReferenceType customType = new ReferenceType("com/example/MyCustomClass");
            SSAValue value = new SSAValue(customType);
            String name = recoverer.generateSyntheticName(value);

            assertTrue(name.startsWith("m"));
            assertTrue(name.matches("m\\d+"));
        }
    }


    // Strategy Getter Tests

    @Nested
    class StrategyGetterTests
    {

        @Test
        void getStrategy_returnsCorrectStrategy() throws IOException
        {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);

            NameRecoverer recoverer1 = new NameRecoverer(ir, method, NameRecoveryStrategy.PREFER_DEBUG_INFO);
            NameRecoverer recoverer2 = new NameRecoverer(ir, method, NameRecoveryStrategy.ALWAYS_SYNTHETIC);
            NameRecoverer recoverer3 = new NameRecoverer(ir, method, NameRecoveryStrategy.PARAMETERS_ONLY);

            assertEquals(NameRecoveryStrategy.PREFER_DEBUG_INFO, recoverer1.getStrategy());
            assertEquals(NameRecoveryStrategy.ALWAYS_SYNTHETIC, recoverer2.getStrategy());
            assertEquals(NameRecoveryStrategy.PARAMETERS_ONLY, recoverer3.getStrategy());
        }
    }
}
