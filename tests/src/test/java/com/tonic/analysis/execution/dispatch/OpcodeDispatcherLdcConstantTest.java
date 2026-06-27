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

class OpcodeDispatcherLdcConstantTest {
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
    class LConstTests {
        @Test
        void testLConst0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLConst0")
                    .publicStaticMethod("test", "()J")
                        .lconst(0L)
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(0L, result.getReturnValue().asLong());
        }

        @Test
        void testLConst1() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLConst1")
                    .publicStaticMethod("test", "()J")
                        .lconst(1L)
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(1L, result.getReturnValue().asLong());
        }

        @Test
        void testLConst0WithArithmetic() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLConst0Arith")
                    .publicStaticMethod("test", "(J)J")
                        .lload(0)
                        .lconst(0L)
                        .ladd()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(42L));
            assertEquals(42L, result.getReturnValue().asLong());
        }

        @Test
        void testLConst1WithArithmetic() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLConst1Arith")
                    .publicStaticMethod("test", "(J)J")
                        .lload(0)
                        .lconst(1L)
                        .ladd()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(10L));
            assertEquals(11L, result.getReturnValue().asLong());
        }

        @Test
        void testLConst0And1Combined() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLConst01")
                    .publicStaticMethod("test", "()J")
                        .lconst(0L)
                        .lconst(1L)
                        .ladd()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(1L, result.getReturnValue().asLong());
        }
    }

    @Nested
    class LdcWTests {
        @Test
        void testLdcWInteger() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcWInt")
                    .publicStaticMethod("test", "()I")
                        .ldcw_int(12345)
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(12345, result.getReturnValue().asInt());
        }

        @Test
        void testLdcWFloat() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcWFloat")
                    .publicStaticMethod("test", "()F")
                        .ldcw_float(3.14159f)
                        .freturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(3.14159f, result.getReturnValue().asFloat(), 0.00001f);
        }

        @Test
        void testLdcWString() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcWString")
                    .publicStaticMethod("test", "()Ljava/lang/String;")
                        .ldcw_string("Hello LDC_W")
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertNotNull(result.getReturnValue());
        }

        @Test
        void testLdcWClass() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcWClass")
                    .publicStaticMethod("test", "()Ljava/lang/Class;")
                        .ldcw_class("java/lang/String")
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertNotNull(result.getReturnValue());
        }

        @Test
        void testLdcWIntegerArithmetic() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcWIntArith")
                    .publicStaticMethod("test", "(I)I")
                        .iload(0)
                        .ldcw_int(1000)
                        .iadd()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(234));
            assertEquals(1234, result.getReturnValue().asInt());
        }

        @Test
        void testLdcWFloatArithmetic() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcWFloatArith")
                    .publicStaticMethod("test", "(F)F")
                        .fload(0)
                        .ldcw_float(2.5f)
                        .fmul()
                        .freturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.floatValue(4.0f));
            assertEquals(10.0f, result.getReturnValue().asFloat(), 0.00001f);
        }

        @Test
        void testLdcWMultipleConstants() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcWMulti")
                    .publicStaticMethod("test", "()I")
                        .ldcw_int(100)
                        .ldcw_int(200)
                        .iadd()
                        .ldcw_int(300)
                        .iadd()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(600, result.getReturnValue().asInt());
        }
    }

    @Nested
    class LdcEdgeCasesTests {
        @Test
        void testLdcClassConstant() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcClass")
                    .publicStaticMethod("test", "()Ljava/lang/Class;")
                        .ldc_class("java/lang/Object")
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertNotNull(result.getReturnValue());
        }

        @Test
        void testLdcIntegerEdgeValues() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcIntEdge")
                    .publicStaticMethod("test", "()I")
                        .ldc_int(Integer.MAX_VALUE)
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(Integer.MAX_VALUE, result.getReturnValue().asInt());
        }

        @Test
        void testLdcFloatSpecialValues() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcFloatSpecial")
                    .publicStaticMethod("test", "()F")
                        .ldc_float(Float.NaN)
                        .freturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertTrue(Float.isNaN(result.getReturnValue().asFloat()));
        }
    }

    @Nested
    class Ldc2WEdgeCasesTests {
        @Test
        void testLdc2WLongMaxValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdc2WLongMax")
                    .publicStaticMethod("test", "()J")
                        .ldc2w_long(Long.MAX_VALUE)
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(Long.MAX_VALUE, result.getReturnValue().asLong());
        }

        @Test
        void testLdc2WLongMinValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdc2WLongMin")
                    .publicStaticMethod("test", "()J")
                        .ldc2w_long(Long.MIN_VALUE)
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(Long.MIN_VALUE, result.getReturnValue().asLong());
        }

        @Test
        void testLdc2WDoubleInfinity() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdc2WDoubleInf")
                    .publicStaticMethod("test", "()D")
                        .ldc2w_double(Double.POSITIVE_INFINITY)
                        .dreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(Double.POSITIVE_INFINITY, result.getReturnValue().asDouble(), 0.0);
        }

        @Test
        void testLdc2WDoubleNaN() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdc2WDoubleNaN")
                    .publicStaticMethod("test", "()D")
                        .ldc2w_double(Double.NaN)
                        .dreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertTrue(Double.isNaN(result.getReturnValue().asDouble()));
        }

        @Test
        void testLdc2WDoubleNegativeZero() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdc2WDoubleNegZero")
                    .publicStaticMethod("test", "()D")
                        .ldc2w_double(-0.0)
                        .dreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(-0.0, result.getReturnValue().asDouble(), 0.0);
        }
    }

    @Nested
    class CombinedConstantTests {
        @Test
        void testMixedConstantTypes() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestMixedConst")
                    .publicStaticMethod("test", "()I")
                        .iconst(5)
                        .lconst(0L)
                        .l2i()
                        .iadd()
                        .ldcw_int(10)
                        .iadd()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertEquals(15, result.getReturnValue().asInt());
        }

        @Test
        void testConstantsWithBranching() throws IOException {
            BytecodeBuilder.Label trueLabel = new BytecodeBuilder.Label();
            BytecodeBuilder.Label end = new BytecodeBuilder.Label();

            ClassFile cf = BytecodeBuilder.forClass("TestConstBranch")
                    .publicStaticMethod("test", "(I)J")
                        .iload(0)
                        .ifne(trueLabel)
                        .lconst(0L)
                        .goto_(end)
                        .label(trueLabel)
                        .lconst(1L)
                        .label(end)
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result1 = execute(method, ConcreteValue.intValue(0));
            assertEquals(0L, result1.getReturnValue().asLong());
            BytecodeResult result2 = execute(method, ConcreteValue.intValue(1));
            assertEquals(1L, result2.getReturnValue().asLong());
        }
    }
}
