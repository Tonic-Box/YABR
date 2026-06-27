package com.tonic.analysis.execution.dispatch;

import com.tonic.testutil.BytecodeBuilder;
import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class OpcodeDispatcherStackNegTest {

    private BytecodeContext context;

    @BeforeEach
    void setUp() {
        context = new BytecodeContext.Builder()
            .heapManager(new SimpleHeapManager())
            .classResolver(new ClassResolver(new ClassPool(true)))
            .maxInstructions(10000)
            .build();
    }

    private BytecodeResult execute(MethodEntry method, ConcreteValue... args) {
        return new BytecodeEngine(context).execute(method, args);
    }

    private MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError("Method not found: " + name);
    }

    @Nested
    class NegationOperationsTest {

        @Test
        void testINeg_PositiveValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("negateInt", "(I)I")
                    .iload(0)
                    .ineg()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "negateInt");
            BytecodeResult result = execute(method, ConcreteValue.intValue(42));

            assertTrue(result.isSuccess());
            assertEquals(-42, result.getReturnValue().asInt());
        }

        @Test
        void testINeg_NegativeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("negateInt", "(I)I")
                    .iload(0)
                    .ineg()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "negateInt");
            BytecodeResult result = execute(method, ConcreteValue.intValue(-42));

            assertTrue(result.isSuccess());
            assertEquals(42, result.getReturnValue().asInt());
        }

        @Test
        void testINeg_Zero() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("negateInt", "(I)I")
                    .iload(0)
                    .ineg()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "negateInt");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0));

            assertTrue(result.isSuccess());
            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testLNeg_PositiveValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("negateLong", "(J)J")
                    .lload(0)
                    .lneg()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "negateLong");
            BytecodeResult result = execute(method, ConcreteValue.longValue(123456789L));

            assertTrue(result.isSuccess());
            assertEquals(-123456789L, result.getReturnValue().asLong());
        }

        @Test
        void testLNeg_NegativeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("negateLong", "(J)J")
                    .lload(0)
                    .lneg()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "negateLong");
            BytecodeResult result = execute(method, ConcreteValue.longValue(-987654321L));

            assertTrue(result.isSuccess());
            assertEquals(987654321L, result.getReturnValue().asLong());
        }

        @Test
        void testFNeg_PositiveValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("negateFloat", "(F)F")
                    .fload(0)
                    .fneg()
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "negateFloat");
            BytecodeResult result = execute(method, ConcreteValue.floatValue(3.14f));

            assertTrue(result.isSuccess());
            assertEquals(-3.14f, result.getReturnValue().asFloat(), 0.0001f);
        }

        @Test
        void testFNeg_NegativeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("negateFloat", "(F)F")
                    .fload(0)
                    .fneg()
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "negateFloat");
            BytecodeResult result = execute(method, ConcreteValue.floatValue(-2.718f));

            assertTrue(result.isSuccess());
            assertEquals(2.718f, result.getReturnValue().asFloat(), 0.0001f);
        }

        @Test
        void testFNeg_Zero() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("negateFloat", "(F)F")
                    .fload(0)
                    .fneg()
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "negateFloat");
            BytecodeResult result = execute(method, ConcreteValue.floatValue(0.0f));

            assertTrue(result.isSuccess());
            assertEquals(-0.0f, result.getReturnValue().asFloat());
        }

        @Test
        void testDNeg_PositiveValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("negateDouble", "(D)D")
                    .dload(0)
                    .dneg()
                    .dreturn()
                .build();

            MethodEntry method = findMethod(cf, "negateDouble");
            BytecodeResult result = execute(method, ConcreteValue.doubleValue(3.141592653589793));

            assertTrue(result.isSuccess());
            assertEquals(-3.141592653589793, result.getReturnValue().asDouble(), 0.00001);
        }

        @Test
        void testDNeg_NegativeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("negateDouble", "(D)D")
                    .dload(0)
                    .dneg()
                    .dreturn()
                .build();

            MethodEntry method = findMethod(cf, "negateDouble");
            BytecodeResult result = execute(method, ConcreteValue.doubleValue(-2.718281828459045));

            assertTrue(result.isSuccess());
            assertEquals(2.718281828459045, result.getReturnValue().asDouble(), 0.00001);
        }
    }

    @Nested
    class StackManipulationTest {

        @Test
        void testDupX1_SingleWordValues() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testDupX1", "(II)I")
                    .iload(0)
                    .iload(1)
                    .dup_x1()
                    .pop()
                    .isub()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testDupX1");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10), ConcreteValue.intValue(5));

            assertTrue(result.isSuccess());
            assertEquals(-5, result.getReturnValue().asInt());
        }

        @Test
        void testDupX2_Form1() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testDupX2", "(III)I")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .dup_x2()
                    .pop()
                    .pop()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testDupX2");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30));

            assertTrue(result.isSuccess());
            assertEquals(40, result.getReturnValue().asInt());
        }

        @Test
        void testDupX2_Form2_WithLong() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testDupX2Long", "(III)I")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .dup_x2()
                    .pop()
                    .iadd()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testDupX2Long");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30));

            assertTrue(result.isSuccess());
            assertEquals(60, result.getReturnValue().asInt());
        }

        @Test
        void testDup2X1_Form1_TwoSingleWords() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testDup2X1", "(III)I")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .dup2_x1()
                    .pop2()
                    .pop()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testDup2X1");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30));

            assertTrue(result.isSuccess());
            assertEquals(50, result.getReturnValue().asInt());
        }

        @Test
        void testDup2X1_Form2_TwoSingleWordsOnTop() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testDup2X1Simple", "(III)I")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .dup2_x1()
                    .pop2()
                    .iadd()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testDup2X1Simple");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30));

            assertTrue(result.isSuccess());
            assertEquals(60, result.getReturnValue().asInt());
        }

        @Test
        void testDup2X2_Form1_AllSingleWords() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testDup2X2", "(IIII)I")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .iload(3)
                    .dup2_x2()
                    .pop2()
                    .pop2()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testDup2X2");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30),
                ConcreteValue.intValue(40));

            assertTrue(result.isSuccess());
            assertEquals(70, result.getReturnValue().asInt());
        }

        @Test
        void testDup2X2_Form2_FourInts() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testDup2X2Mixed", "(IIII)I")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .iload(3)
                    .dup2_x2()
                    .pop2()
                    .pop2()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testDup2X2Mixed");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30),
                ConcreteValue.intValue(40));

            assertTrue(result.isSuccess());
            assertEquals(70, result.getReturnValue().asInt());
        }

        @Test
        void testDup2X2_Form3_AllInts() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testDup2X2Form3", "(IIII)I")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .iload(3)
                    .dup2_x2()
                    .pop2()
                    .iadd()
                    .iadd()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testDup2X2Form3");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(15),
                ConcreteValue.intValue(20));

            assertTrue(result.isSuccess());
            assertEquals(50, result.getReturnValue().asInt());
        }

        @Test
        void testDup2X2_Form4_VerifyDuplication() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testDup2X2Doubles", "(IIII)I")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .iload(3)
                    .dup2_x2()
                    .iadd()
                    .iadd()
                    .iadd()
                    .iadd()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testDup2X2Doubles");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(1),
                ConcreteValue.intValue(2),
                ConcreteValue.intValue(3),
                ConcreteValue.intValue(4));

            assertTrue(result.isSuccess());
            assertEquals(17, result.getReturnValue().asInt());
        }
    }

    @Nested
    class MiscellaneousOperationsTest {

        @Test
        void testNop_DoesNothing() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testNop", "(I)I")
                    .iload(0)
                    .nop()
                    .nop()
                    .nop()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testNop");
            BytecodeResult result = execute(method, ConcreteValue.intValue(42));

            assertTrue(result.isSuccess());
            assertEquals(42, result.getReturnValue().asInt());
        }

        @Test
        void testNop_InComplexFlow() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testNopInFlow", "(II)I")
                    .iload(0)
                    .nop()
                    .iload(1)
                    .nop()
                    .iadd()
                    .nop()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testNopInFlow");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20));

            assertTrue(result.isSuccess());
            assertEquals(30, result.getReturnValue().asInt());
        }

        @Test
        void testGotoW_ForwardJump() throws IOException {
            BytecodeBuilder.Label target = new BytecodeBuilder.Label();

            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testGotoW", "(I)I")
                    .iload(0)
                    .goto_(target)
                    .iconst(999)
                    .iadd()
                    .label(target)
                    .iconst(100)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testGotoW");
            BytecodeResult result = execute(method, ConcreteValue.intValue(42));

            assertTrue(result.isSuccess());
            assertEquals(142, result.getReturnValue().asInt());
        }

        @Test
        void testGotoW_BackwardJump() throws IOException {
            BytecodeBuilder.Label loopStart = new BytecodeBuilder.Label();
            BytecodeBuilder.Label loopEnd = new BytecodeBuilder.Label();

            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("testGotoWBackward", "(I)I")
                    .iconst(0)
                    .istore(1)
                    .label(loopStart)
                    .iload(1)
                    .iload(0)
                    .if_icmpge(loopEnd)
                    .iload(1)
                    .iconst(1)
                    .iadd()
                    .istore(1)
                    .goto_(loopStart)
                    .label(loopEnd)
                    .iload(1)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "testGotoWBackward");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5));

            assertTrue(result.isSuccess());
            assertEquals(5, result.getReturnValue().asInt());
        }

        @Test
        void testI2L_PositiveValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("intToLong", "(I)J")
                    .iload(0)
                    .i2l()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "intToLong");
            BytecodeResult result = execute(method, ConcreteValue.intValue(12345));

            assertTrue(result.isSuccess());
            assertEquals(12345L, result.getReturnValue().asLong());
        }

        @Test
        void testI2L_NegativeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("intToLong", "(I)J")
                    .iload(0)
                    .i2l()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "intToLong");
            BytecodeResult result = execute(method, ConcreteValue.intValue(-67890));

            assertTrue(result.isSuccess());
            assertEquals(-67890L, result.getReturnValue().asLong());
        }

        @Test
        void testI2L_Zero() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("intToLong", "(I)J")
                    .iload(0)
                    .i2l()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "intToLong");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0));

            assertTrue(result.isSuccess());
            assertEquals(0L, result.getReturnValue().asLong());
        }

        @Test
        void testI2L_MaxInt() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("intToLong", "(I)J")
                    .iload(0)
                    .i2l()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "intToLong");
            BytecodeResult result = execute(method, ConcreteValue.intValue(Integer.MAX_VALUE));

            assertTrue(result.isSuccess());
            assertEquals((long) Integer.MAX_VALUE, result.getReturnValue().asLong());
        }

        @Test
        void testI2L_MinInt() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("intToLong", "(I)J")
                    .iload(0)
                    .i2l()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "intToLong");
            BytecodeResult result = execute(method, ConcreteValue.intValue(Integer.MIN_VALUE));

            assertTrue(result.isSuccess());
            assertEquals((long) Integer.MIN_VALUE, result.getReturnValue().asLong());
        }
    }
}
