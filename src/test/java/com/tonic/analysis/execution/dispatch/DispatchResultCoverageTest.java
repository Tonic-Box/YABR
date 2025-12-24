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

class DispatchResultCoverageTest {
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
    class ContinueDispatchTests {

        @Test
        void testContinueWithIconstFollowedByIconst() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(10)
                    .iconst(20)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertEquals(30, result.getReturnValue().asInt());
        }

        @Test
        void testContinueWithMultipleLoadInstructions() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(15),
                ConcreteValue.intValue(25));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertEquals(40, result.getReturnValue().asInt());
        }

        @Test
        void testContinueWithStoreFollowedByLoad() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(42)
                    .istore(0)
                    .iload(0)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertEquals(42, result.getReturnValue().asInt());
        }
    }

    @Nested
    class BranchDispatchTests {

        @Test
        void testBranchWithIfeqTaken() throws IOException {
            BytecodeBuilder.Label skipLabel = new BytecodeBuilder.Label();

            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(0)
                    .ifeq(skipLabel)
                    .iconst(99)
                    .ireturn()
                    .label(skipLabel)
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertEquals(42, result.getReturnValue().asInt());
        }

        @Test
        void testBranchWithIfeqNotTaken() throws IOException {
            BytecodeBuilder.Label skipLabel = new BytecodeBuilder.Label();

            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(1)
                    .ifeq(skipLabel)
                    .iconst(99)
                    .ireturn()
                    .label(skipLabel)
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertEquals(99, result.getReturnValue().asInt());
        }

        @Test
        void testBranchWithIfneTaken() throws IOException {
            BytecodeBuilder.Label skipLabel = new BytecodeBuilder.Label();

            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(5)
                    .ifne(skipLabel)
                    .iconst(99)
                    .ireturn()
                    .label(skipLabel)
                    .iconst(100)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertEquals(100, result.getReturnValue().asInt());
        }

        @Test
        void testBranchWithIfneNotTaken() throws IOException {
            BytecodeBuilder.Label skipLabel = new BytecodeBuilder.Label();

            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(0)
                    .ifne(skipLabel)
                    .iconst(55)
                    .ireturn()
                    .label(skipLabel)
                    .iconst(100)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertEquals(55, result.getReturnValue().asInt());
        }

        @Test
        void testBranchWithGoto() throws IOException {
            BytecodeBuilder.Label targetLabel = new BytecodeBuilder.Label();

            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .goto_(targetLabel)
                    .iconst(99)
                    .ireturn()
                    .label(targetLabel)
                    .iconst(77)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertEquals(77, result.getReturnValue().asInt());
        }
    }

    @Nested
    class ReturnDispatchTests {

        @Test
        void testIreturnWithValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(123)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertNotNull(result.getReturnValue());
            assertEquals(123, result.getReturnValue().asInt());
        }

        @Test
        void testLreturnWithValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()J")
                    .lconst(999999L)
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertNotNull(result.getReturnValue());
            assertEquals(999999L, result.getReturnValue().asLong());
        }

        @Test
        void testFreturnWithValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()F")
                    .fconst(3.14f)
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertNotNull(result.getReturnValue());
            assertEquals(3.14f, result.getReturnValue().asFloat(), 0.001f);
        }

        @Test
        void testDreturnWithValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()D")
                    .dconst(2.71828)
                    .dreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertNotNull(result.getReturnValue());
            assertEquals(2.71828, result.getReturnValue().asDouble(), 0.00001);
        }

        @Test
        void testAreturnWithReference() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(Ljava/lang/String;)Ljava/lang/String;")
                    .aload(0)
                    .areturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            var obj = context.getHeapManager().newObject("java/lang/String");
            BytecodeResult result = execute(method, ConcreteValue.reference(obj));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertNotNull(result.getReturnValue());
            assertEquals(obj.getId(), result.getReturnValue().asReference().getId());
        }

        @Test
        void testVoidReturn() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertTrue(result.isSuccess());
            assertNotNull(result.getReturnValue());
            assertTrue(result.getReturnValue().isNull());
        }
    }
}
