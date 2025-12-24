package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.heap.ArrayInstance;
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

class EngineDispatchContextTest {

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
    class ConstantResolutionTests {

        @Test
        void testResolveIntConstant_SmallValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getSmallInt", "()I")
                    .iconst(5)
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getSmallInt");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(5, result.getReturnValue().asInt());
        }

        @Test
        void testResolveIntConstant_LargeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getLargeInt", "()I")
                    .iconst(100000)
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getLargeInt");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(100000, result.getReturnValue().asInt());
        }

        @Test
        void testResolveIntConstant_NegativeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getNegativeInt", "()I")
                    .iconst(-42)
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getNegativeInt");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(-42, result.getReturnValue().asInt());
        }

        @Test
        void testResolveIntConstant_MaxValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getMaxInt", "()I")
                    .iconst(Integer.MAX_VALUE)
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getMaxInt");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(Integer.MAX_VALUE, result.getReturnValue().asInt());
        }

        @Test
        void testResolveLongConstant_SmallValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getSmallLong", "()J")
                    .lconst(100L)
                    .lreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getSmallLong");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(100L, result.getReturnValue().asLong());
        }

        @Test
        void testResolveLongConstant_LargeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getLargeLong", "()J")
                    .lconst(9999999999L)
                    .lreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getLargeLong");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(9999999999L, result.getReturnValue().asLong());
        }

        @Test
        void testResolveLongConstant_MaxValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getMaxLong", "()J")
                    .lconst(Long.MAX_VALUE)
                    .lreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getMaxLong");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(Long.MAX_VALUE, result.getReturnValue().asLong());
        }

        @Test
        void testResolveFloatConstant_SimpleValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getPi", "()F")
                    .fconst(3.14f)
                    .freturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getPi");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(3.14f, result.getReturnValue().asFloat(), 0.001f);
        }

        @Test
        void testResolveFloatConstant_NegativeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getNegativeFloat", "()F")
                    .fconst(-2.5f)
                    .freturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getNegativeFloat");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(-2.5f, result.getReturnValue().asFloat(), 0.001f);
        }

        @Test
        void testResolveDoubleConstant_SimpleValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getE", "()D")
                    .dconst(2.71828)
                    .dreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getE");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(2.71828, result.getReturnValue().asDouble(), 0.00001);
        }

        @Test
        void testResolveDoubleConstant_PreciseValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getPreciseValue", "()D")
                    .dconst(1.234567890123456)
                    .dreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getPreciseValue");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(1.234567890123456, result.getReturnValue().asDouble(), 0.0000000000001);
        }

        @Test
        void testResolveStringConstant_SimpleString() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getString", "()Ljava/lang/String;")
                    .ldc("hello")
                    .areturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getString");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertNotNull(result.getReturnValue());
            assertFalse(result.getReturnValue().isNull());
        }

        @Test
        void testResolveStringConstant_EmptyString() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getEmptyString", "()Ljava/lang/String;")
                    .ldc("")
                    .areturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getEmptyString");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertNotNull(result.getReturnValue());
        }

        @Test
        void testResolveStringConstant_SpecialCharacters() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("getSpecialString", "()Ljava/lang/String;")
                    .ldc("test\nstring\twith\rspecial")
                    .areturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "getSpecialString");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertNotNull(result.getReturnValue());
        }
    }

    @Nested
    class ArrayAccessTests {

        @Test
        void testGetArray_ValidArrayInstance() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("createAndGetLength", "()I")
                    .iconst(10)
                    .newarray(10)
                    .arraylength()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "createAndGetLength");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(10, result.getReturnValue().asInt());
        }

        @Test
        void testCheckArrayBounds_ValidIndex() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("storeAndLoad", "()I")
                    .iconst(5)
                    .newarray(10)
                    .dup()
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .iconst(0)
                    .iaload()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "storeAndLoad");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(42, result.getReturnValue().asInt());
        }

        @Test
        void testCheckArrayBounds_IndexAtUpperBound() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("storeAtEnd", "()V")
                    .iconst(5)
                    .newarray(10)
                    .dup()
                    .iconst(4)
                    .iconst(99)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "storeAtEnd");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
        }

        @Test
        void testCheckArrayBounds_NegativeIndex() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("accessNegativeIndex", "()I")
                    .iconst(5)
                    .newarray(10)
                    .dup()
                    .iconst(-1)
                    .iaload()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "accessNegativeIndex");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }

        @Test
        void testCheckArrayBounds_IndexTooLarge() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("accessOutOfBounds", "()I")
                    .iconst(5)
                    .newarray(10)
                    .dup()
                    .iconst(10)
                    .iaload()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "accessOutOfBounds");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }

        @Test
        void testCheckArrayBounds_WayOutOfBounds() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("accessWayOutOfBounds", "()I")
                    .iconst(3)
                    .newarray(10)
                    .dup()
                    .iconst(100)
                    .iaload()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "accessWayOutOfBounds");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }

        @Test
        void testArrayStoreAndLoad_MultipleOperations() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("multipleArrayOps", "()I")
                    .iconst(10)
                    .newarray(10)
                    .dup()
                    .iconst(0)
                    .iconst(10)
                    .iastore()
                    .dup()
                    .iconst(5)
                    .iconst(20)
                    .iastore()
                    .dup()
                    .iconst(9)
                    .iconst(30)
                    .iastore()
                    .iconst(5)
                    .iaload()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "multipleArrayOps");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(20, result.getReturnValue().asInt());
        }
    }

    @Nested
    class NullCheckTests {

        @Test
        void testCheckNullReference_ThrowNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("throwNull", "()V")
                    .aconst_null()
                    .athrow()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "throwNull");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }

        @Test
        void testCheckNullReference_ArrayLengthOnNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("nullArrayLength", "()I")
                    .aconst_null()
                    .arraylength()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "nullArrayLength");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }

        @Test
        void testCheckNullReference_ArrayStoreOnNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("nullArrayStore", "()V")
                    .aconst_null()
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "nullArrayStore");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }

        @Test
        void testCheckNullReference_ArrayLoadOnNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("nullArrayLoad", "()I")
                    .aconst_null()
                    .iconst(0)
                    .iaload()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "nullArrayLoad");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }

        @Test
        void testValidReference_NotNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                .publicStaticMethod("validArrayLength", "()I")
                    .iconst(5)
                    .newarray(10)
                    .arraylength()
                    .ireturn()
                .endMethod()
                .build();

            MethodEntry method = findMethod(cf, "validArrayLength");
            BytecodeEngine engine = new BytecodeEngine(context);

            BytecodeResult result = engine.execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(5, result.getReturnValue().asInt());
        }
    }
}
