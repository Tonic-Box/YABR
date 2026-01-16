package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LambdaRecoveryTest {

    private static final AtomicInteger classCounter = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        com.tonic.analysis.ssa.cfg.IRBlock.resetIdCounter();
        com.tonic.analysis.ssa.value.SSAValue.resetIdCounter();
    }

    private String uniqueClassName() {
        return "com/test/LambdaTest" + classCounter.incrementAndGet();
    }

    private Expression recoverInvokeDynamic(ClassFile cf, String methodName) throws IOException {
        MethodEntry method = findMethod(cf, methodName);
        IRMethod ir = TestUtils.liftMethod(method);
        DefUseChains defUse = new DefUseChains(ir);
        defUse.compute();

        RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
        ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

        for (IRBlock block : ir.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instr;
                    if (invoke.isDynamic()) {
                        return recoverer.recover(instr);
                    }
                }
            }
        }
        fail("No invokedynamic instruction found");
        return null;
    }

    private MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        fail("Method not found: " + name);
        return null;
    }

    @Nested
    class BasicLambdaTests {

        @Test
        void testSimpleLambdaNoParameters() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "()I"
            )
                .iconst(42)
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "()Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "()I"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Supplier;")
                .invokedynamic("get", "()Ljava/util/function/Supplier;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
            assertTrue(expr instanceof LambdaExpr || expr instanceof InvokeDynamicExpr,
                    "Expected LambdaExpr or InvokeDynamicExpr, got: " + expr.getClass().getSimpleName());
        }

        @Test
        void testLambdaWithSingleParameter() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(I)I"
            )
                .iload(0)
                .iconst(2)
                .imul()
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "(I)I"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Function;")
                .invokedynamic("apply", "()Ljava/util/function/Function;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
            assertTrue(expr instanceof LambdaExpr || expr instanceof InvokeDynamicExpr);
        }

        @Test
        void testLambdaWithMultipleParameters() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(II)I"
            )
                .iload(0)
                .iload(1)
                .iadd()
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "(II)I"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/BiFunction;")
                .invokedynamic("apply", "()Ljava/util/function/BiFunction;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
            assertTrue(expr instanceof LambdaExpr || expr instanceof InvokeDynamicExpr);
        }

        @Test
        void testLambdaReturningVoid() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(Ljava/lang/String;)V"
            )
                .vreturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;)V",
                className,
                "lambda$test$0",
                "(Ljava/lang/String;)V"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Consumer;")
                .invokedynamic("accept", "()Ljava/util/function/Consumer;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
            assertTrue(expr instanceof LambdaExpr || expr instanceof InvokeDynamicExpr);
        }

        @Test
        void testLambdaWithBlockBody() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            BytecodeBuilder.MethodBuilder mb = bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(I)I"
            );
            BytecodeBuilder.Label start = mb.newLabel();
            BytecodeBuilder.Label end = mb.newLabel();

            mb.label(start)
                .iload(0)
                .iconst(10)
                .if_icmplt(end)
                .iconst(100)
                .ireturn()
                .label(end)
                .iload(0)
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "(I)I"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Function;")
                .invokedynamic("apply", "()Ljava/util/function/Function;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
            assertTrue(expr instanceof LambdaExpr || expr instanceof InvokeDynamicExpr);
        }
    }

    @Nested
    class FunctionalInterfaceTests {

        @Test
        void testSupplierInterface() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "()Ljava/lang/String;"
            )
                .ldc("Hello")
                .areturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "()Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "()Ljava/lang/String;"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Supplier;")
                .invokedynamic("get", "()Ljava/util/function/Supplier;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
            assertTrue(expr instanceof LambdaExpr || expr instanceof InvokeDynamicExpr);
        }

        @Test
        void testConsumerInterface() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(Ljava/lang/Object;)V"
            )
                .vreturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;)V",
                className,
                "lambda$test$0",
                "(Ljava/lang/Object;)V"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Consumer;")
                .invokedynamic("accept", "()Ljava/util/function/Consumer;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }

        @Test
        void testFunctionInterface() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(Ljava/lang/String;)I"
            )
                .aload(0)
                .invokevirtual("java/lang/String", "length", "()I")
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "(Ljava/lang/String;)I"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Function;")
                .invokedynamic("apply", "()Ljava/util/function/Function;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }

        @Test
        void testPredicateInterface() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            BytecodeBuilder.MethodBuilder mb = bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(Ljava/lang/String;)Z"
            );
            BytecodeBuilder.Label falseLabel = mb.newLabel();
            BytecodeBuilder.Label endLabel = mb.newLabel();

            mb.aload(0)
                .invokevirtual("java/lang/String", "isEmpty", "()Z")
                .ifeq(falseLabel)
                .iconst(1)
                .goto_(endLabel)
                .label(falseLabel)
                .iconst(0)
                .label(endLabel)
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;)Z",
                className,
                "lambda$test$0",
                "(Ljava/lang/String;)Z"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Predicate;")
                .invokedynamic("test", "()Ljava/util/function/Predicate;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }

        @Test
        void testBiFunctionInterface() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
            )
                .aload(0)
                .aload(1)
                .invokevirtual("java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;")
                .areturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/BiFunction;")
                .invokedynamic("apply", "()Ljava/util/function/BiFunction;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }
    }

    @Nested
    class CaptureTests {

        @Test
        void testLambdaCapturingLocalVariable() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(I)I"
            )
                .iload(0)
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "()Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "(I)I"
            );

            bb.publicStaticMethod("test", "(I)Ljava/util/function/Supplier;")
                .iload(0)
                .invokedynamic("get", "(I)Ljava/util/function/Supplier;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }

        @Test
        void testLambdaCapturingMultipleVariables() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(II)I"
            )
                .iload(0)
                .iload(1)
                .iadd()
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "()Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "(II)I"
            );

            bb.publicStaticMethod("test", "(II)Ljava/util/function/Supplier;")
                .iload(0)
                .iload(1)
                .invokedynamic("get", "(II)Ljava/util/function/Supplier;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }

        @Test
        void testLambdaCapturingThis() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setSynthetic().build(),
                "lambda$test$0",
                "()I"
            )
                .iconst(42)
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "()Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "()I"
            );

            bb.publicMethod("test", "()Ljava/util/function/Supplier;")
                .aload(0)
                .invokedynamic("get", "(L" + className + ";)Ljava/util/function/Supplier;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }

        @Test
        void testLambdaCapturingMethodParameter() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(Ljava/lang/String;)I"
            )
                .aload(0)
                .invokevirtual("java/lang/String", "length", "()I")
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "()Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "(Ljava/lang/String;)I"
            );

            bb.publicStaticMethod("test", "(Ljava/lang/String;)Ljava/util/function/Supplier;")
                .aload(0)
                .invokedynamic("get", "(Ljava/lang/String;)Ljava/util/function/Supplier;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }

        @Test
        void testLambdaEffectivelyFinalCapture() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(I)I"
            )
                .iload(0)
                .iconst(1)
                .iadd()
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "()Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "(I)I"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Supplier;")
                .iconst(5)
                .invokedynamic("get", "(I)Ljava/util/function/Supplier;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }
    }

    @Nested
    class MethodReferenceTests {

        @Test
        void testStaticMethodReferencePattern() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.publicStaticMethod("helper", "(I)I")
                .iload(0)
                .iconst(2)
                .imul()
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                className,
                "helper",
                "(I)I"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Function;")
                .invokedynamic("apply", "()Ljava/util/function/Function;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }

        @Test
        void testInstanceMethodReferencePattern() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                "java/lang/String",
                "toUpperCase",
                "()Ljava/lang/String;"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Function;")
                .invokedynamic("apply", "()Ljava/util/function/Function;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }

        @Test
        void testConstructorReferencePattern() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            int bootstrapIdx = bb.addLambdaBootstrap(
                "()Ljava/lang/Object;",
                "java/lang/StringBuilder",
                "<init>",
                "()V"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Supplier;")
                .invokedynamic("get", "()Ljava/util/function/Supplier;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }
    }

    @Nested
    class ComplexPatternTests {

        @Test
        void testNestedLambdas() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$1",
                "(I)I"
            )
                .iload(0)
                .iconst(2)
                .imul()
                .ireturn()
            .endMethod();

            int innerBootstrap = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                className,
                "lambda$test$1",
                "(I)I"
            );

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "()Ljava/util/function/Function;"
            )
                .invokedynamic("apply", "()Ljava/util/function/Function;", innerBootstrap)
                .areturn()
            .endMethod();

            int outerBootstrap = bb.addLambdaBootstrap(
                "()Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "()Ljava/util/function/Function;"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/Supplier;")
                .invokedynamic("get", "()Ljava/util/function/Supplier;", outerBootstrap)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }

        @Test
        void testLambdaInsideLoop() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(I)V"
            )
                .vreturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;)V",
                className,
                "lambda$test$0",
                "(I)V"
            );

            BytecodeBuilder.MethodBuilder mb = bb.publicStaticMethod("test", "()V");
            BytecodeBuilder.Label loopStart = mb.newLabel();
            BytecodeBuilder.Label loopEnd = mb.newLabel();

            mb.iconst(0)
                .istore(0)
                .label(loopStart)
                .iload(0)
                .iconst(10)
                .if_icmpge(loopEnd)
                .iload(0)
                .invokedynamic("accept", "(I)Ljava/util/function/Consumer;", bootstrapIdx)
                .pop()
                .iinc(0, 1)
                .goto_(loopStart)
                .label(loopEnd)
                .vreturn()
            .endMethod();

            ClassFile cf = bb.build();
            MethodEntry method = findMethod(cf, "test");
            assertNotNull(method);
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void testInvokeDynamicWithoutBootstrapInfo() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            int bootstrapIdx = bb.addLambdaBootstrap(
                "()Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "()I"
            );

            bb.publicStaticMethod("test", "()Ljava/lang/Object;")
                .invokedynamic("get", "()Ljava/util/function/Supplier;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");
            assertNotNull(expr);
        }

        @Test
        void testLambdaWithComplexExpressionBody() throws IOException {
            String className = uniqueClassName();
            BytecodeBuilder bb = BytecodeBuilder.forClass(className);

            BytecodeBuilder.MethodBuilder mb = bb.method(
                new AccessBuilder().setPrivate().setStatic().setSynthetic().build(),
                "lambda$test$0",
                "(II)I"
            );
            BytecodeBuilder.Label falseLabel = mb.newLabel();
            BytecodeBuilder.Label endLabel = mb.newLabel();

            mb.iload(0)
                .iload(1)
                .if_icmpge(falseLabel)
                .iload(0)
                .goto_(endLabel)
                .label(falseLabel)
                .iload(1)
                .label(endLabel)
                .ireturn()
            .endMethod();

            int bootstrapIdx = bb.addLambdaBootstrap(
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                className,
                "lambda$test$0",
                "(II)I"
            );

            bb.publicStaticMethod("test", "()Ljava/util/function/BiFunction;")
                .invokedynamic("apply", "()Ljava/util/function/BiFunction;", bootstrapIdx)
                .areturn()
            .endMethod();

            ClassFile cf = bb.build();
            Expression expr = recoverInvokeDynamic(cf, "test");

            assertNotNull(expr);
        }
    }
}
