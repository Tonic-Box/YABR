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
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class OpcodeDispatcherEdgeCasesTest {
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

    @Test
    void testI2B_OverflowPositive() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .iconst(300)
                .i2b()
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals((byte) 300, result.getReturnValue().asInt());
    }

    @Test
    void testI2B_OverflowNegative() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .iconst(-200)
                .i2b()
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals((byte) -200, result.getReturnValue().asInt());
    }

    @Test
    void testI2C_NegativeToUnsigned() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .iconst(-1)
                .i2c()
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals((char) -1, result.getReturnValue().asInt());
        assertEquals(65535, result.getReturnValue().asInt());
    }

    @Test
    void testI2C_Overflow() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .iconst(70000)
                .i2c()
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals((char) 70000, result.getReturnValue().asInt());
    }

    @Test
    void testI2S_OverflowPositive() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .iconst(40000)
                .i2s()
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals((short) 40000, result.getReturnValue().asInt());
    }

    @Test
    void testI2S_OverflowNegative() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .iconst(-40000)
                .i2s()
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals((short) -40000, result.getReturnValue().asInt());
    }

    @Test
    void testLRem_Basic() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()J")
                .lconst(17L)
                .lconst(5L)
                .lrem()
                .lreturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(2L, result.getReturnValue().asLong());
    }

    @Test
    void testLRem_Negative() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()J")
                .lconst(-17L)
                .lconst(5L)
                .lrem()
                .lreturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(-2L, result.getReturnValue().asLong());
    }

    @Test
    void testFRem_Basic() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()F")
                .fconst(7.5f)
                .fconst(2.5f)
                .frem()
                .freturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(0.0f, result.getReturnValue().asFloat(), 0.001f);
    }

    @Test
    void testFRem_NonExact() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()F")
                .fconst(7.0f)
                .fconst(3.0f)
                .frem()
                .freturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(1.0f, result.getReturnValue().asFloat(), 0.001f);
    }

    @Test
    void testDRem_Basic() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()D")
                .dconst(10.0)
                .dconst(3.0)
                .drem()
                .dreturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(1.0, result.getReturnValue().asDouble(), 0.001);
    }

    @Test
    void testDRem_Negative() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()D")
                .dconst(-10.0)
                .dconst(3.0)
                .drem()
                .dreturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(-1.0, result.getReturnValue().asDouble(), 0.001);
    }

    @Test
    void testL2F_LargeLong() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()F")
                .lconst(123456789L)
                .l2f()
                .freturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(123456789.0f, result.getReturnValue().asFloat(), 100.0f);
    }

    @Test
    void testL2D_LargeLong() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()D")
                .lconst(123456789012345L)
                .l2d()
                .dreturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(123456789012345.0, result.getReturnValue().asDouble(), 1.0);
    }

    @Test
    void testF2L_WithRounding() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()J")
                .fconst(42.9f)
                .f2l()
                .lreturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(42L, result.getReturnValue().asLong());
    }

    @Test
    void testF2L_NaN() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()J")
                .fconst(Float.NaN)
                .f2l()
                .lreturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(0L, result.getReturnValue().asLong());
    }

    @Test
    void testF2D_PrecisionPreserved() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()D")
                .fconst(3.14159f)
                .f2d()
                .dreturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertTrue(Math.abs(result.getReturnValue().asDouble() - 3.14159) < 0.001);
    }

    @Test
    void testD2L_WithRounding() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()J")
                .dconst(999.99)
                .d2l()
                .lreturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(999L, result.getReturnValue().asLong());
    }

    @Test
    void testD2L_NaN() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()J")
                .dconst(Double.NaN)
                .d2l()
                .lreturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(0L, result.getReturnValue().asLong());
    }

    @Test
    void testD2F_PrecisionLoss() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()F")
                .dconst(1.23456789123456789)
                .d2f()
                .freturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(1.23456789f, result.getReturnValue().asFloat(), 0.0001f);
    }

    @Test
    void testFCmpL_WithNaN() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .fconst(Float.NaN)
                .fconst(1.0f)
                .fcmpl()
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(-1, result.getReturnValue().asInt());
    }

    @Test
    void testFCmpG_WithNaN() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .fconst(Float.NaN)
                .fconst(1.0f)
                .fcmpg()
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(1, result.getReturnValue().asInt());
    }

    @Test
    void testDCmpL_WithNaN() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .dconst(Double.NaN)
                .dconst(1.0)
                .dcmpl()
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(-1, result.getReturnValue().asInt());
    }

    @Test
    void testDCmpG_WithNaN() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .dconst(Double.NaN)
                .dconst(1.0)
                .dcmpg()
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(1, result.getReturnValue().asInt());
    }

    @Test
    void testIfAcmpeq_SameReference() throws IOException {
        BytecodeBuilder.Label trueLabel = new BytecodeBuilder.Label();
        BytecodeBuilder.Label endLabel = new BytecodeBuilder.Label();

        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .ldc("test")
                .dup()
                .if_acmpeq(trueLabel)
                .iconst(0)
                .goto_(endLabel)
                .label(trueLabel)
                .iconst(1)
                .label(endLabel)
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(1, result.getReturnValue().asInt());
    }

    @Test
    void testIfAcmpeq_DifferentReferences() throws IOException {
        BytecodeBuilder.Label trueLabel = new BytecodeBuilder.Label();
        BytecodeBuilder.Label endLabel = new BytecodeBuilder.Label();

        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .ldc("test1")
                .ldc("test2")
                .if_acmpeq(trueLabel)
                .iconst(0)
                .goto_(endLabel)
                .label(trueLabel)
                .iconst(1)
                .label(endLabel)
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(0, result.getReturnValue().asInt());
    }

    @Test
    void testIfAcmpne_SameReference() throws IOException {
        BytecodeBuilder.Label trueLabel = new BytecodeBuilder.Label();
        BytecodeBuilder.Label endLabel = new BytecodeBuilder.Label();

        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .ldc("test")
                .dup()
                .if_acmpne(trueLabel)
                .iconst(0)
                .goto_(endLabel)
                .label(trueLabel)
                .iconst(1)
                .label(endLabel)
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(0, result.getReturnValue().asInt());
    }

    @Test
    void testIfAcmpne_DifferentReferences() throws IOException {
        BytecodeBuilder.Label trueLabel = new BytecodeBuilder.Label();
        BytecodeBuilder.Label endLabel = new BytecodeBuilder.Label();

        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .ldc("test1")
                .ldc("test2")
                .if_acmpne(trueLabel)
                .iconst(0)
                .goto_(endLabel)
                .label(trueLabel)
                .iconst(1)
                .label(endLabel)
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        assertEquals(1, result.getReturnValue().asInt());
    }
}
