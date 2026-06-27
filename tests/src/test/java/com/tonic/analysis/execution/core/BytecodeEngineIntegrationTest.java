package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeEngineIntegrationTest {

    private BytecodeContext context;

    @BeforeEach
    void setUp() throws IOException {
        context = new BytecodeContext.Builder()
            .heapManager(new SimpleHeapManager())
            .classResolver(new ClassResolver(new ClassPool()))
            .maxCallDepth(100)
            .maxInstructions(10000)
            .build();
    }

    private MethodEntry findMethod(ClassFile cf, String name) {
        return cf.getMethods().stream()
            .filter(m -> m.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Method not found: " + name));
    }

    @Nested
    class ReturnTests {

        @Test
        void testReturnInt() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getInt", "()I")
                    .iconst(5)
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getInt");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertNotNull(result.getReturnValue());
            assertEquals(5, result.getReturnValue().asInt());
        }

        @Test
        void testReturnLong() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getLong", "()J")
                    .lconst(1L)
                    .lreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"getLong");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertNotNull(result.getReturnValue());
            assertEquals(1L, result.getReturnValue().asLong());
        }

        @Test
        void testReturnFloat() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getFloat", "()F")
                    .fconst(2.0f)
                    .freturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"getFloat");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertNotNull(result.getReturnValue());
            assertEquals(2.0f, result.getReturnValue().asFloat(), 0.001f);
        }

        @Test
        void testReturnDouble() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getDouble", "()D")
                    .dconst(1.0)
                    .dreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"getDouble");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertNotNull(result.getReturnValue());
            assertEquals(1.0, result.getReturnValue().asDouble(), 0.001);
        }

        @Test
        void testReturnVoid() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("doNothing", "()V")
                    .vreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"doNothing");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
        }

        @Test
        void testReturnNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getNull", "()Ljava/lang/Object;")
                    .aconst_null()
                    .areturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"getNull");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertNotNull(result.getReturnValue());
            assertTrue(result.getReturnValue().isNull());
        }
    }

    @Nested
    class IntArithmeticTests {

        @Test
        void testIAdd() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("add", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"add");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(3),
                ConcreteValue.intValue(5));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(8, result.getReturnValue().asInt());
        }

        @Test
        void testISub() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("subtract", "(II)I")
                    .iload(0)
                    .iload(1)
                    .isub()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"subtract");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(3));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(7, result.getReturnValue().asInt());
        }

        @Test
        void testIMul() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("multiply", "(II)I")
                    .iload(0)
                    .iload(1)
                    .imul()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"multiply");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(4),
                ConcreteValue.intValue(6));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(24, result.getReturnValue().asInt());
        }

        @Test
        void testIDiv() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("divide", "(II)I")
                    .iload(0)
                    .iload(1)
                    .idiv()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"divide");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(15),
                ConcreteValue.intValue(3));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(5, result.getReturnValue().asInt());
        }

        @Test
        void testIRem() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("remainder", "(II)I")
                    .iload(0)
                    .iload(1)
                    .irem()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"remainder");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(17),
                ConcreteValue.intValue(5));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(2, result.getReturnValue().asInt());
        }

        @Test
        void testINeg() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("negate", "(I)I")
                    .iload(0)
                    .ineg()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"negate");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(42));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(-42, result.getReturnValue().asInt());
        }

        @Test
        void testIDivByZero() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("divideByZero", "(II)I")
                    .iload(0)
                    .iload(1)
                    .idiv()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"divideByZero");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }
    }

    @Nested
    class LongArithmeticTests {

        @Test
        void testLAdd() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("addLongs", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .ladd()
                    .lreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"addLongs");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.longValue(100L),
                ConcreteValue.longValue(200L));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(300L, result.getReturnValue().asLong());
        }

        @Test
        void testLSub() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("subtractLongs", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .lsub()
                    .lreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"subtractLongs");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.longValue(500L),
                ConcreteValue.longValue(200L));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(300L, result.getReturnValue().asLong());
        }

        @Test
        void testLMul() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("multiplyLongs", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .lmul()
                    .lreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"multiplyLongs");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.longValue(10L),
                ConcreteValue.longValue(20L));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(200L, result.getReturnValue().asLong());
        }

        @Test
        void testLDiv() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("divideLongs", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .ldiv()
                    .lreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"divideLongs");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.longValue(100L),
                ConcreteValue.longValue(5L));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(20L, result.getReturnValue().asLong());
        }
    }

    @Nested
    class TypeConversionTests {

        @Test
        void testI2L() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("intToLong", "(I)J")
                    .iload(0)
                    .i2l()
                    .lreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"intToLong");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(42));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(42L, result.getReturnValue().asLong());
        }

        @Test
        void testI2F() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("intToFloat", "(I)F")
                    .iload(0)
                    .i2f()
                    .freturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"intToFloat");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(10.0f, result.getReturnValue().asFloat(), 0.001f);
        }

        @Test
        void testI2D() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("intToDouble", "(I)D")
                    .iload(0)
                    .i2d()
                    .dreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"intToDouble");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(25));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(25.0, result.getReturnValue().asDouble(), 0.001);
        }

        @Test
        void testL2I() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("longToInt", "(J)I")
                    .lload(0)
                    .l2i()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"longToInt");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.longValue(100L));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(100, result.getReturnValue().asInt());
        }
    }

    @Nested
    class ComparisonTests {

        @Test
        void testLCmp_Equal() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("compareLongs", "(JJ)I")
                    .lload(0)
                    .lload(2)
                    .lcmp()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"compareLongs");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.longValue(100L),
                ConcreteValue.longValue(100L));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testLCmp_LessThan() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("compareLongs", "(JJ)I")
                    .lload(0)
                    .lload(2)
                    .lcmp()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"compareLongs");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.longValue(50L),
                ConcreteValue.longValue(100L));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(-1, result.getReturnValue().asInt());
        }

        @Test
        void testLCmp_GreaterThan() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("compareLongs", "(JJ)I")
                    .lload(0)
                    .lload(2)
                    .lcmp()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"compareLongs");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.longValue(200L),
                ConcreteValue.longValue(100L));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testFCmpl() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("compareFloatsL", "(FF)I")
                    .fload(0)
                    .fload(1)
                    .fcmpl()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"compareFloatsL");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.floatValue(1.5f),
                ConcreteValue.floatValue(2.5f));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(-1, result.getReturnValue().asInt());
        }

        @Test
        void testFCmpg() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("compareFloatsG", "(FF)I")
                    .fload(0)
                    .fload(1)
                    .fcmpg()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"compareFloatsG");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.floatValue(3.0f),
                ConcreteValue.floatValue(2.0f));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testDCmpl() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("compareDoublesL", "(DD)I")
                    .dload(0)
                    .dload(2)
                    .dcmpl()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"compareDoublesL");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.doubleValue(10.5),
                ConcreteValue.doubleValue(10.5));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testDCmpg() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("compareDoublesG", "(DD)I")
                    .dload(0)
                    .dload(2)
                    .dcmpg()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"compareDoublesG");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.doubleValue(5.0),
                ConcreteValue.doubleValue(10.0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(-1, result.getReturnValue().asInt());
        }
    }

    @Nested
    class EngineFeatureTests {

        @Test
        void testInstructionLimitReached() throws IOException {
            BytecodeContext limitedContext = new BytecodeContext.Builder()
                .heapManager(new SimpleHeapManager())
                .classResolver(new ClassResolver(new ClassPool()))
                .maxCallDepth(100)
                .maxInstructions(3)
                .build();

            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("manyOps", "()I")
                    .iconst(1)
                    .iconst(2)
                    .iadd()
                    .iconst(3)
                    .iadd()
                    .iconst(4)
                    .iadd()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"manyOps");
            BytecodeEngine engine = new BytecodeEngine(limitedContext);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.INSTRUCTION_LIMIT, result.getStatus());
        }

        @Test
        void testInstructionCountTracking() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("simpleAdd", "()I")
                    .iconst(10)
                    .iconst(20)
                    .iadd()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"simpleAdd");
            BytecodeEngine engine = new BytecodeEngine(context);

            engine.execute(method);

            assertTrue(engine.getInstructionCount() > 0);
            assertTrue(engine.getInstructionCount() <= 10);
        }

        @Test
        void testExecutionTimeTracking() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("doWork", "()I")
                    .iconst(1)
                    .iconst(2)
                    .iadd()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"doWork");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertTrue(result.getExecutionTimeNanos() >= 0);
        }

        @Test
        void testMultipleExecutionsOnSameEngine() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getValue", "()I")
                    .iconst(42)
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"getValue");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result1 = engine.execute(method);
            long count1 = result1.getInstructionsExecuted();

            BytecodeResult result2 = engine.execute(method);
            long count2 = result2.getInstructionsExecuted();

            assertEquals(BytecodeResult.Status.COMPLETED, result1.getStatus());
            assertEquals(BytecodeResult.Status.COMPLETED, result2.getStatus());
            assertEquals(42, result1.getReturnValue().asInt());
            assertEquals(42, result2.getReturnValue().asInt());
        }

        @Test
        void testComplexExpression() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("calculate", "(III)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .iload(2)
                    .imul()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf,"calculate");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(2),
                ConcreteValue.intValue(3),
                ConcreteValue.intValue(4));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(20, result.getReturnValue().asInt());
        }
    }
}
