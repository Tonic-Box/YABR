package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.MethodRefExpr;
import com.tonic.analysis.source.ast.expr.MethodRefKind;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class SamDescriptorInferenceTest {

    private ExpressionLowerer lowerer;
    private Method getSamDescriptorMethod;
    private Method inferSamDescriptorMethod;

    @BeforeEach
    void setUp() throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile classFile = pool.createNewClass("com/test/SamDescriptorTest", access);
        ConstPool constPool = classFile.getConstPool();

        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        IRMethod irMethod = new IRMethod("com/test/Test", "test", "()V", true);
        IRBlock entryBlock = new IRBlock();
        irMethod.addBlock(entryBlock);
        irMethod.setEntryBlock(entryBlock);

        TypeResolver typeResolver = new TypeResolver(pool, "com/test/Test");
        LoweringContext ctx = new LoweringContext(irMethod, constPool, typeResolver);
        ctx.setCurrentBlock(entryBlock);
        lowerer = new ExpressionLowerer(ctx);

        getSamDescriptorMethod = ExpressionLowerer.class.getDeclaredMethod(
            "getSamDescriptor", String.class, String.class);
        getSamDescriptorMethod.setAccessible(true);

        inferSamDescriptorMethod = ExpressionLowerer.class.getDeclaredMethod(
            "inferSamDescriptorFromMethodRef", MethodRefExpr.class, String.class, MethodRefKind.class);
        inferSamDescriptorMethod.setAccessible(true);
    }

    private String getSamDescriptor(String interfaceName, String implDescriptor) throws Exception {
        return (String) getSamDescriptorMethod.invoke(lowerer, interfaceName, implDescriptor);
    }

    private String inferSamDescriptor(MethodRefExpr ref, String implDesc, MethodRefKind kind) throws Exception {
        return (String) inferSamDescriptorMethod.invoke(lowerer, ref, implDesc, kind);
    }

    private ReferenceSourceType funcInterface(String name) {
        return new ReferenceSourceType("java/util/function/" + name);
    }

    @Nested
    class GetSamDescriptorTests {

        @Test
        void runnable() throws Exception {
            assertEquals("()V", getSamDescriptor("Runnable", "()V"));
        }

        @Test
        void callable() throws Exception {
            assertEquals("()Ljava/lang/String;", getSamDescriptor("Callable", "()Ljava/lang/String;"));
        }

        @Test
        void supplier() throws Exception {
            assertEquals("()Ljava/lang/Integer;", getSamDescriptor("Supplier", "()Ljava/lang/Integer;"));
        }

        @Test
        void primitiveSuppliers() throws Exception {
            assertEquals("()Z", getSamDescriptor("BooleanSupplier", "()Z"));
            assertEquals("()I", getSamDescriptor("IntSupplier", "()I"));
            assertEquals("()J", getSamDescriptor("LongSupplier", "()J"));
            assertEquals("()D", getSamDescriptor("DoubleSupplier", "()D"));
        }

        @Test
        void consumer() throws Exception {
            assertEquals("(Ljava/lang/Object;)V", getSamDescriptor("Consumer", "(Ljava/lang/String;)V"));
        }

        @Test
        void primitiveConsumers() throws Exception {
            assertEquals("(I)V", getSamDescriptor("IntConsumer", "(I)V"));
            assertEquals("(J)V", getSamDescriptor("LongConsumer", "(J)V"));
            assertEquals("(D)V", getSamDescriptor("DoubleConsumer", "(D)V"));
        }

        @Test
        void biConsumer() throws Exception {
            assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)V",
                getSamDescriptor("BiConsumer", "(Ljava/lang/String;Ljava/lang/Integer;)V"));
        }

        @Test
        void objPrimitiveConsumers() throws Exception {
            assertEquals("(Ljava/lang/Object;I)V", getSamDescriptor("ObjIntConsumer", "(Ljava/lang/String;I)V"));
            assertEquals("(Ljava/lang/Object;J)V", getSamDescriptor("ObjLongConsumer", "(Ljava/lang/String;J)V"));
            assertEquals("(Ljava/lang/Object;D)V", getSamDescriptor("ObjDoubleConsumer", "(Ljava/lang/String;D)V"));
        }

        @Test
        void predicate() throws Exception {
            assertEquals("(Ljava/lang/Object;)Z", getSamDescriptor("Predicate", "(Ljava/lang/String;)Z"));
        }

        @Test
        void primitivePredicates() throws Exception {
            assertEquals("(I)Z", getSamDescriptor("IntPredicate", "(I)Z"));
            assertEquals("(J)Z", getSamDescriptor("LongPredicate", "(J)Z"));
            assertEquals("(D)Z", getSamDescriptor("DoublePredicate", "(D)Z"));
        }

        @Test
        void biPredicate() throws Exception {
            assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)Z",
                getSamDescriptor("BiPredicate", "(Ljava/lang/String;Ljava/lang/Integer;)Z"));
        }

        @Test
        void function() throws Exception {
            assertEquals("(Ljava/lang/Object;)Ljava/lang/Integer;",
                getSamDescriptor("Function", "(Ljava/lang/String;)Ljava/lang/Integer;"));
        }

        @Test
        void primitiveToObjectFunctions() throws Exception {
            assertEquals("(I)Ljava/lang/String;", getSamDescriptor("IntFunction", "(I)Ljava/lang/String;"));
            assertEquals("(J)Ljava/lang/String;", getSamDescriptor("LongFunction", "(J)Ljava/lang/String;"));
            assertEquals("(D)Ljava/lang/String;", getSamDescriptor("DoubleFunction", "(D)Ljava/lang/String;"));
        }

        @Test
        void objectToPrimitiveFunctions() throws Exception {
            assertEquals("(Ljava/lang/Object;)I", getSamDescriptor("ToIntFunction", "(Ljava/lang/String;)I"));
            assertEquals("(Ljava/lang/Object;)J", getSamDescriptor("ToLongFunction", "(Ljava/lang/String;)J"));
            assertEquals("(Ljava/lang/Object;)D", getSamDescriptor("ToDoubleFunction", "(Ljava/lang/String;)D"));
        }

        @Test
        void primitiveToPrimitiveFunctions() throws Exception {
            assertEquals("(I)J", getSamDescriptor("IntToLongFunction", "(I)J"));
            assertEquals("(I)D", getSamDescriptor("IntToDoubleFunction", "(I)D"));
            assertEquals("(J)I", getSamDescriptor("LongToIntFunction", "(J)I"));
            assertEquals("(J)D", getSamDescriptor("LongToDoubleFunction", "(J)D"));
            assertEquals("(D)I", getSamDescriptor("DoubleToIntFunction", "(D)I"));
            assertEquals("(D)J", getSamDescriptor("DoubleToLongFunction", "(D)J"));
        }

        @Test
        void biFunction() throws Exception {
            assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;",
                getSamDescriptor("BiFunction", "(Ljava/lang/String;Ljava/lang/Integer;)Ljava/lang/String;"));
        }

        @Test
        void objectToPrimitiveBiFunctions() throws Exception {
            assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)I",
                getSamDescriptor("ToIntBiFunction", "(Ljava/lang/String;Ljava/lang/Integer;)I"));
            assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)J",
                getSamDescriptor("ToLongBiFunction", "(Ljava/lang/String;Ljava/lang/Integer;)J"));
            assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)D",
                getSamDescriptor("ToDoubleBiFunction", "(Ljava/lang/String;Ljava/lang/Integer;)D"));
        }

        @Test
        void unaryOperator() throws Exception {
            assertEquals("(Ljava/lang/Object;)Ljava/lang/Object;",
                getSamDescriptor("UnaryOperator", "(Ljava/lang/String;)Ljava/lang/String;"));
        }

        @Test
        void primitiveUnaryOperators() throws Exception {
            assertEquals("(I)I", getSamDescriptor("IntUnaryOperator", "(I)I"));
            assertEquals("(J)J", getSamDescriptor("LongUnaryOperator", "(J)J"));
            assertEquals("(D)D", getSamDescriptor("DoubleUnaryOperator", "(D)D"));
        }

        @Test
        void binaryOperator() throws Exception {
            assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                getSamDescriptor("BinaryOperator", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
        }

        @Test
        void primitiveBinaryOperators() throws Exception {
            assertEquals("(II)I", getSamDescriptor("IntBinaryOperator", "(II)I"));
            assertEquals("(JJ)J", getSamDescriptor("LongBinaryOperator", "(JJ)J"));
            assertEquals("(DD)D", getSamDescriptor("DoubleBinaryOperator", "(DD)D"));
        }

        @Test
        void comparator() throws Exception {
            assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)I",
                getSamDescriptor("Comparator", "(Ljava/lang/String;Ljava/lang/String;)I"));
        }

        @Test
        void unknownInterfaceReturnsNull() throws Exception {
            assertNull(getSamDescriptor("CustomFunctionalInterface", "(Ljava/lang/String;)Ljava/lang/String;"));
        }
    }

    @Nested
    class InferSamDescriptorFromMethodRefTests {

        @Test
        void staticRefWithFunction() throws Exception {
            MethodRefExpr ref = MethodRefExpr.staticRef(
                "java/lang/Integer", "parseInt", funcInterface("Function"));

            String result = inferSamDescriptor(ref, "(Ljava/lang/String;)I", MethodRefKind.STATIC);
            assertEquals("(Ljava/lang/Object;)I", result);
        }

        @Test
        void staticRefWithToIntFunction() throws Exception {
            MethodRefExpr ref = MethodRefExpr.staticRef(
                "java/lang/Integer", "parseInt", funcInterface("ToIntFunction"));

            String result = inferSamDescriptor(ref, "(Ljava/lang/String;)I", MethodRefKind.STATIC);
            assertEquals("(Ljava/lang/Object;)I", result);
        }

        @Test
        void staticRefWithIntFunction() throws Exception {
            MethodRefExpr ref = MethodRefExpr.staticRef(
                "java/lang/String", "valueOf", funcInterface("IntFunction"));

            String result = inferSamDescriptor(ref, "(I)Ljava/lang/String;", MethodRefKind.STATIC);
            assertEquals("(I)Ljava/lang/String;", result);
        }

        @Test
        void instanceRefWithFunction() throws Exception {
            MethodRefExpr ref = MethodRefExpr.instanceRef(
                "java/lang/String", "length", funcInterface("ToIntFunction"));

            String result = inferSamDescriptor(ref, "()I", MethodRefKind.INSTANCE);
            assertEquals("(Ljava/lang/Object;)I", result);
        }

        @Test
        void instanceRefWithConsumer() throws Exception {
            MethodRefExpr ref = MethodRefExpr.instanceRef(
                "java/io/PrintStream", "println", funcInterface("Consumer"));

            String result = inferSamDescriptor(ref, "(Ljava/lang/String;)V", MethodRefKind.INSTANCE);
            assertEquals("(Ljava/lang/Object;)V", result);
        }

        @Test
        void staticRefWithSupplier() throws Exception {
            MethodRefExpr ref = MethodRefExpr.staticRef(
                "java/lang/System", "currentTimeMillis", funcInterface("LongSupplier"));

            String result = inferSamDescriptor(ref, "()J", MethodRefKind.STATIC);
            assertEquals("()J", result);
        }

        @Test
        void instanceRefWithPredicate() throws Exception {
            MethodRefExpr ref = MethodRefExpr.instanceRef(
                "java/lang/String", "isEmpty", funcInterface("Predicate"));

            String result = inferSamDescriptor(ref, "()Z", MethodRefKind.INSTANCE);
            assertEquals("(Ljava/lang/Object;)Z", result);
        }

        @Test
        void instanceRefWithUnknownInterfaceFallsBack() throws Exception {
            MethodRefExpr ref = MethodRefExpr.instanceRef(
                "java/lang/String", "length",
                new ReferenceSourceType("com/custom/MyInterface"));

            String result = inferSamDescriptor(ref, "()I", MethodRefKind.INSTANCE);
            assertEquals("(Ljava/lang/Object;)I", result);
        }

        @Test
        void staticRefWithUnknownInterfaceUsesImplDescriptor() throws Exception {
            MethodRefExpr ref = MethodRefExpr.staticRef(
                "com/custom/Utils", "process",
                new ReferenceSourceType("com/custom/MyInterface"));

            String result = inferSamDescriptor(ref, "(Ljava/lang/String;)V", MethodRefKind.STATIC);
            assertEquals("(Ljava/lang/String;)V", result);
        }

        @Test
        void constructorRefWithSupplier() throws Exception {
            MethodRefExpr ref = MethodRefExpr.constructorRef(
                "java/util/ArrayList", funcInterface("Supplier"));

            String result = inferSamDescriptor(ref, "()Ljava/util/ArrayList;", MethodRefKind.CONSTRUCTOR);
            assertEquals("()Ljava/util/ArrayList;", result);
        }

        @Test
        void staticRefWithIntBinaryOperator() throws Exception {
            MethodRefExpr ref = MethodRefExpr.staticRef(
                "java/lang/Integer", "compare", funcInterface("IntBinaryOperator"));

            String result = inferSamDescriptor(ref, "(II)I", MethodRefKind.STATIC);
            assertEquals("(II)I", result);
        }
    }
}
