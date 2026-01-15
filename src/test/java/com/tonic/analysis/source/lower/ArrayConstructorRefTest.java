package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.MethodRefExpr;
import com.tonic.analysis.source.ast.expr.MethodRefKind;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.InvokeType;
import com.tonic.analysis.ssa.value.MethodHandleConstant;
import com.tonic.analysis.ssa.value.MethodTypeConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArrayConstructorRefTest {

    private LoweringContext ctx;
    private ExpressionLowerer lowerer;

    @BeforeEach
    void setUp() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile classFile = pool.createNewClass("com/test/ArrayConstructorRefTest", access);
        ConstPool constPool = classFile.getConstPool();

        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        IRMethod irMethod = new IRMethod("com/test/Test", "test", "()V", true);
        IRBlock entryBlock = new IRBlock();
        irMethod.addBlock(entryBlock);
        irMethod.setEntryBlock(entryBlock);

        TypeResolver typeResolver = new TypeResolver(pool, "com/test/Test");
        ctx = new LoweringContext(irMethod, constPool, typeResolver);
        ctx.setCurrentBlock(entryBlock);
        ctx.setOwnerClass("com/test/Test");
        lowerer = new ExpressionLowerer(ctx);
    }

    private MethodRefExpr createArrayConstructorRef(String arrayTypeName, SourceType funcInterfaceType) {
        return new MethodRefExpr(null, "new", arrayTypeName, MethodRefKind.ARRAY_CONSTRUCTOR, funcInterfaceType);
    }

    @Nested
    class PrimitiveArrayConstructors {

        @Test
        void intArrayConstructor() {
            SourceType intFuncType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("int[]", intFuncType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            assertInstanceOf(SSAValue.class, result);

            List<IRInstruction> instructions = ctx.getCurrentBlock().getInstructions();
            assertEquals(1, instructions.size());

            InvokeInstruction indy = (InvokeInstruction) instructions.get(0);
            assertEquals(InvokeType.DYNAMIC, indy.getInvokeType());
            assertEquals("apply", indy.getName());
            assertEquals("()Ljava/util/function/IntFunction;", indy.getDescriptor());

            List<SyntheticArrayConstructor> constructors = ctx.getArrayConstructors();
            assertEquals(1, constructors.size());

            SyntheticArrayConstructor synthetic = constructors.get(0);
            assertEquals("lambda$newArray$0", synthetic.getName());
            assertEquals("(I)[I", synthetic.getDescriptor());
            assertEquals(1, synthetic.getDimensions());
            assertTrue(synthetic.isPrimitiveArray());
        }

        @Test
        void longArrayConstructor() {
            SourceType longFuncType = new ReferenceSourceType("java/util/function/LongFunction");
            MethodRefExpr ref = createArrayConstructorRef("long[]", longFuncType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            List<SyntheticArrayConstructor> constructors = ctx.getArrayConstructors();
            assertEquals(1, constructors.size());
            assertEquals("(I)[J", constructors.get(0).getDescriptor());
            assertTrue(constructors.get(0).isPrimitiveArray());
        }

        @Test
        void doubleArrayConstructor() {
            SourceType doubleFuncType = new ReferenceSourceType("java/util/function/DoubleFunction");
            MethodRefExpr ref = createArrayConstructorRef("double[]", doubleFuncType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            List<SyntheticArrayConstructor> constructors = ctx.getArrayConstructors();
            assertEquals(1, constructors.size());
            assertEquals("(I)[D", constructors.get(0).getDescriptor());
        }

        @Test
        void booleanArrayConstructor() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("boolean[]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            List<SyntheticArrayConstructor> constructors = ctx.getArrayConstructors();
            assertEquals(1, constructors.size());
            assertEquals("(I)[Z", constructors.get(0).getDescriptor());
        }

        @Test
        void byteArrayConstructor() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("byte[]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            assertEquals("(I)[B", ctx.getArrayConstructors().get(0).getDescriptor());
        }

        @Test
        void charArrayConstructor() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("char[]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            assertEquals("(I)[C", ctx.getArrayConstructors().get(0).getDescriptor());
        }

        @Test
        void shortArrayConstructor() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("short[]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            assertEquals("(I)[S", ctx.getArrayConstructors().get(0).getDescriptor());
        }

        @Test
        void floatArrayConstructor() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("float[]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            assertEquals("(I)[F", ctx.getArrayConstructors().get(0).getDescriptor());
        }
    }

    @Nested
    class ReferenceArrayConstructors {

        @Test
        void stringArrayConstructor() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("java/lang/String[]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            List<SyntheticArrayConstructor> constructors = ctx.getArrayConstructors();
            assertEquals(1, constructors.size());

            SyntheticArrayConstructor synthetic = constructors.get(0);
            assertEquals("(I)[Ljava/lang/String;", synthetic.getDescriptor());
            assertEquals(1, synthetic.getDimensions());
            assertFalse(synthetic.isPrimitiveArray());
        }

        @Test
        void objectArrayConstructor() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("java/lang/Object[]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            assertEquals("(I)[Ljava/lang/Object;", ctx.getArrayConstructors().get(0).getDescriptor());
        }

        @Test
        void customClassArrayConstructor() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("com/example/MyClass[]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            assertEquals("(I)[Lcom/example/MyClass;", ctx.getArrayConstructors().get(0).getDescriptor());
        }

        @Test
        void innerClassArrayConstructor() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("com/example/Outer$Inner[]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            assertEquals("(I)[Lcom/example/Outer$Inner;", ctx.getArrayConstructors().get(0).getDescriptor());
        }
    }

    @Nested
    class MultiDimensionalArrays {

        @Test
        void twoDimensionalIntArray() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("int[][]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            List<SyntheticArrayConstructor> constructors = ctx.getArrayConstructors();
            assertEquals(1, constructors.size());

            SyntheticArrayConstructor synthetic = constructors.get(0);
            assertEquals("(I)[[I", synthetic.getDescriptor());
            assertEquals(2, synthetic.getDimensions());
        }

        @Test
        void twoDimensionalStringArray() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("java/lang/String[][]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            assertEquals("(I)[[Ljava/lang/String;", ctx.getArrayConstructors().get(0).getDescriptor());
        }

        @Test
        void threeDimensionalArray() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("int[][][]", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            SyntheticArrayConstructor synthetic = ctx.getArrayConstructors().get(0);
            assertEquals("(I)[[[I", synthetic.getDescriptor());
            assertEquals(3, synthetic.getDimensions());
        }
    }

    @Nested
    class BootstrapMethodValidation {

        @Test
        void correctBootstrapMethodHandle() {
            SourceType intFuncType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("int[]", intFuncType);

            lowerer.lower(ref);

            InvokeInstruction indy = (InvokeInstruction) ctx.getCurrentBlock().getInstructions().get(0);
            assertNotNull(indy.getBootstrapInfo());

            MethodHandleConstant bsm = indy.getBootstrapInfo().getBootstrapMethod();
            assertEquals(MethodHandleConstant.REF_invokeStatic, bsm.getReferenceKind());
            assertEquals("java/lang/invoke/LambdaMetafactory", bsm.getOwner());
            assertEquals("metafactory", bsm.getName());
        }

        @Test
        void correctBootstrapArguments() {
            SourceType intFuncType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("int[]", intFuncType);

            lowerer.lower(ref);

            InvokeInstruction indy = (InvokeInstruction) ctx.getCurrentBlock().getInstructions().get(0);
            var bsArgs = indy.getBootstrapInfo().getBootstrapArguments();
            assertEquals(3, bsArgs.size());

            assertInstanceOf(MethodTypeConstant.class, bsArgs.get(0));
            assertEquals("(I)Ljava/lang/Object;", ((MethodTypeConstant) bsArgs.get(0)).getDescriptor());

            assertInstanceOf(MethodHandleConstant.class, bsArgs.get(1));
            MethodHandleConstant implHandle = (MethodHandleConstant) bsArgs.get(1);
            assertEquals(MethodHandleConstant.REF_invokeStatic, implHandle.getReferenceKind());
            assertEquals("com/test/Test", implHandle.getOwner());
            assertEquals("lambda$newArray$0", implHandle.getName());
            assertEquals("(I)[I", implHandle.getDescriptor());

            assertInstanceOf(MethodTypeConstant.class, bsArgs.get(2));
            assertEquals("(I)[I", ((MethodTypeConstant) bsArgs.get(2)).getDescriptor());
        }
    }

    @Nested
    class SyntheticMethodNaming {

        @Test
        void uniqueNamesForMultipleConstructors() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");

            lowerer.lower(createArrayConstructorRef("int[]", funcType));
            lowerer.lower(createArrayConstructorRef("long[]", funcType));
            lowerer.lower(createArrayConstructorRef("java/lang/String[]", funcType));

            List<SyntheticArrayConstructor> constructors = ctx.getArrayConstructors();
            assertEquals(3, constructors.size());

            assertEquals("lambda$newArray$0", constructors.get(0).getName());
            assertEquals("lambda$newArray$1", constructors.get(1).getName());
            assertEquals("lambda$newArray$2", constructors.get(2).getName());
        }
    }

    @Nested
    class DescriptorFormatParsing {

        @Test
        void parseDescriptorFormat() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("[I", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            assertEquals("(I)[I", ctx.getArrayConstructors().get(0).getDescriptor());
        }

        @Test
        void parseReferenceDescriptorFormat() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("[Ljava/lang/String;", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            assertEquals("(I)[Ljava/lang/String;", ctx.getArrayConstructors().get(0).getDescriptor());
        }

        @Test
        void parseMultiDimensionalDescriptorFormat() {
            SourceType funcType = new ReferenceSourceType("java/util/function/IntFunction");
            MethodRefExpr ref = createArrayConstructorRef("[[I", funcType);

            Value result = lowerer.lower(ref);

            assertNotNull(result);
            SyntheticArrayConstructor synthetic = ctx.getArrayConstructors().get(0);
            assertEquals("(I)[[I", synthetic.getDescriptor());
            assertEquals(2, synthetic.getDimensions());
        }
    }

    @Nested
    class SyntheticArrayConstructorClass {

        @Test
        void primitiveArrayDescriptor() {
            SyntheticArrayConstructor sac = new SyntheticArrayConstructor(
                "test", PrimitiveSourceType.INT, 1
            );

            assertEquals("(I)[I", sac.getDescriptor());
            assertEquals("[I", sac.getArrayTypeDescriptor());
            assertTrue(sac.isPrimitiveArray());
        }

        @Test
        void referenceArrayDescriptor() {
            SyntheticArrayConstructor sac = new SyntheticArrayConstructor(
                "test", new ReferenceSourceType("java/lang/String"), 1
            );

            assertEquals("(I)[Ljava/lang/String;", sac.getDescriptor());
            assertEquals("[Ljava/lang/String;", sac.getArrayTypeDescriptor());
            assertFalse(sac.isPrimitiveArray());
        }

        @Test
        void multiDimensionalDescriptor() {
            SyntheticArrayConstructor sac = new SyntheticArrayConstructor(
                "test", PrimitiveSourceType.INT, 3
            );

            assertEquals("(I)[[[I", sac.getDescriptor());
            assertEquals("[[[I", sac.getArrayTypeDescriptor());
            assertEquals(3, sac.getDimensions());
        }
    }

    @Nested
    class LoweringContextIntegration {

        @Test
        void registerAndClearArrayConstructors() {
            SyntheticArrayConstructor sac1 = new SyntheticArrayConstructor("test1", PrimitiveSourceType.INT, 1);
            SyntheticArrayConstructor sac2 = new SyntheticArrayConstructor("test2", PrimitiveSourceType.LONG, 1);

            ctx.registerArrayConstructor(sac1);
            ctx.registerArrayConstructor(sac2);

            assertEquals(2, ctx.getArrayConstructors().size());

            ctx.clearArrayConstructors();
            assertEquals(0, ctx.getArrayConstructors().size());
        }

        @Test
        void generateUniqueMethodNames() {
            String name1 = ctx.generateArrayConstructorMethodName();
            String name2 = ctx.generateArrayConstructorMethodName();
            String name3 = ctx.generateArrayConstructorMethodName();

            assertEquals("lambda$newArray$0", name1);
            assertEquals("lambda$newArray$1", name2);
            assertEquals("lambda$newArray$2", name3);
        }
    }
}
