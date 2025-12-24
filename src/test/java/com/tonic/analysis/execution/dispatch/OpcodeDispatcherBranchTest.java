package com.tonic.analysis.execution.dispatch;

import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.BytecodeBuilder.Label;
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

class OpcodeDispatcherBranchTest {
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
    class UnaryBranchTests {

        @Test
        void testIfeqTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .ifeq(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfeqNotTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .ifeq(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5));

            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testIfneTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .ifne(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfneNotTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .ifne(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0));

            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testIfltTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iflt(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(-5));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfltNotTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iflt(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5));

            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testIfgeTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .ifge(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfgeNotTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .ifge(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(-5));

            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testIfgtTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .ifgt(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfgtNotTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .ifgt(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0));

            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testIfleTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .ifle(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfleNotTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label taken = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .ifle(taken)
                .iconst(0)
                .ireturn()
                .label(taken)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5));

            assertEquals(0, result.getReturnValue().asInt());
        }
    }

    @Nested
    class BinaryIntBranchTests {

        @Test
        void testIfIcmpeqTrue() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label equal = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmpeq(equal)
                .iconst(0)
                .ireturn()
                .label(equal)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5), ConcreteValue.intValue(5));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfIcmpeqFalse() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label equal = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmpeq(equal)
                .iconst(0)
                .ireturn()
                .label(equal)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5), ConcreteValue.intValue(10));

            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testIfIcmpneTrue() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label notEqual = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmpne(notEqual)
                .iconst(0)
                .ireturn()
                .label(notEqual)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5), ConcreteValue.intValue(10));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfIcmpneFalse() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label notEqual = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmpne(notEqual)
                .iconst(0)
                .ireturn()
                .label(notEqual)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5), ConcreteValue.intValue(5));

            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testIfIcmpltTrue() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label lessThan = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmplt(lessThan)
                .iconst(0)
                .ireturn()
                .label(lessThan)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5), ConcreteValue.intValue(10));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfIcmpltFalse() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label lessThan = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmplt(lessThan)
                .iconst(0)
                .ireturn()
                .label(lessThan)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10), ConcreteValue.intValue(5));

            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testIfIcmpgeTrue() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label greaterOrEqual = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmpge(greaterOrEqual)
                .iconst(0)
                .ireturn()
                .label(greaterOrEqual)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10), ConcreteValue.intValue(5));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfIcmpgeFalse() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label greaterOrEqual = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmpge(greaterOrEqual)
                .iconst(0)
                .ireturn()
                .label(greaterOrEqual)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5), ConcreteValue.intValue(10));

            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testIfIcmpgtTrue() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label greaterThan = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmpgt(greaterThan)
                .iconst(0)
                .ireturn()
                .label(greaterThan)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10), ConcreteValue.intValue(5));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfIcmpgtFalse() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label greaterThan = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmpgt(greaterThan)
                .iconst(0)
                .ireturn()
                .label(greaterThan)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5), ConcreteValue.intValue(10));

            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testIfIcmpleTrue() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label lessOrEqual = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmple(lessOrEqual)
                .iconst(0)
                .ireturn()
                .label(lessOrEqual)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(5), ConcreteValue.intValue(10));

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfIcmpleFalse() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I");
            Label lessOrEqual = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iload(1)
                .if_icmple(lessOrEqual)
                .iconst(0)
                .ireturn()
                .label(lessOrEqual)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10), ConcreteValue.intValue(5));

            assertEquals(0, result.getReturnValue().asInt());
        }
    }

    @Nested
    class ReferenceBranchTests {

        @Test
        void testIfnullTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(Ljava/lang/Object;)I");
            Label isNull = mb.newLabel();

            ClassFile cf = mb
                .aload(0)
                .ifnull(isNull)
                .iconst(0)
                .ireturn()
                .label(isNull)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.nullRef());

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfnullNotTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(Ljava/lang/Object;)I");
            Label isNull = mb.newLabel();

            ClassFile cf = mb
                .aload(0)
                .ifnull(isNull)
                .iconst(0)
                .ireturn()
                .label(isNull)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            ConcreteValue obj = ConcreteValue.reference(context.getHeapManager().newObject("java/lang/Object"));
            BytecodeResult result = execute(method, obj);

            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testIfnonnullTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(Ljava/lang/Object;)I");
            Label notNull = mb.newLabel();

            ClassFile cf = mb
                .aload(0)
                .ifnonnull(notNull)
                .iconst(0)
                .ireturn()
                .label(notNull)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            ConcreteValue obj = ConcreteValue.reference(context.getHeapManager().newObject("java/lang/Object"));
            BytecodeResult result = execute(method, obj);

            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testIfnonnullNotTaken() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(Ljava/lang/Object;)I");
            Label notNull = mb.newLabel();

            ClassFile cf = mb
                .aload(0)
                .ifnonnull(notNull)
                .iconst(0)
                .ireturn()
                .label(notNull)
                .iconst(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.nullRef());

            assertEquals(0, result.getReturnValue().asInt());
        }
    }

    @Nested
    class UnconditionalBranchTests {

        @Test
        void testGotoForward() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I");
            Label target = mb.newLabel();

            ClassFile cf = mb
                .goto_(target)
                .iconst(0)
                .ireturn()
                .label(target)
                .iconst(42)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(42, result.getReturnValue().asInt());
        }

        @Test
        void testGotoBackward() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I");
            Label loopStart = mb.newLabel();
            Label loopEnd = mb.newLabel();

            ClassFile cf = mb
                .iconst(0)
                .istore(0)
                .label(loopStart)
                .iload(0)
                .iconst(3)
                .if_icmpge(loopEnd)
                .iload(0)
                .iconst(1)
                .iadd()
                .istore(0)
                .goto_(loopStart)
                .label(loopEnd)
                .iload(0)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(3, result.getReturnValue().asInt());
        }
    }

    @Nested
    class ComplexControlFlowTests {

        @Test
        void testSimpleLoop() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I");
            Label loopStart = mb.newLabel();
            Label loopEnd = mb.newLabel();

            ClassFile cf = mb
                .iconst(0)
                .istore(0)
                .iconst(0)
                .istore(1)
                .label(loopStart)
                .iload(0)
                .iconst(5)
                .if_icmpge(loopEnd)
                .iload(1)
                .iload(0)
                .iadd()
                .istore(1)
                .iload(0)
                .iconst(1)
                .iadd()
                .istore(0)
                .goto_(loopStart)
                .label(loopEnd)
                .iload(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(10, result.getReturnValue().asInt());
        }

        @Test
        void testNestedBranches() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label checkTen = mb.newLabel();
            Label checkTwenty = mb.newLabel();
            Label returnTwenty = mb.newLabel();
            Label returnThirty = mb.newLabel();
            Label end = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .iconst(5)
                .if_icmpne(checkTen)
                .iconst(10)
                .ireturn()
                .label(checkTen)
                .iload(0)
                .iconst(10)
                .if_icmpne(checkTwenty)
                .goto_(returnTwenty)
                .label(checkTwenty)
                .iload(0)
                .iconst(15)
                .if_icmpne(end)
                .goto_(returnThirty)
                .label(returnTwenty)
                .iconst(20)
                .ireturn()
                .label(returnThirty)
                .iconst(30)
                .ireturn()
                .label(end)
                .iconst(0)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");

            assertEquals(10, execute(method, ConcreteValue.intValue(5)).getReturnValue().asInt());
            assertEquals(20, execute(method, ConcreteValue.intValue(10)).getReturnValue().asInt());
            assertEquals(30, execute(method, ConcreteValue.intValue(15)).getReturnValue().asInt());
            assertEquals(0, execute(method, ConcreteValue.intValue(99)).getReturnValue().asInt());
        }

        @Test
        void testWhileLoop() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label loopStart = mb.newLabel();
            Label loopEnd = mb.newLabel();

            ClassFile cf = mb
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

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(7));

            assertEquals(7, result.getReturnValue().asInt());
        }

        @Test
        void testCountdown() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label loopStart = mb.newLabel();
            Label loopEnd = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .istore(0)
                .iconst(0)
                .istore(1)
                .label(loopStart)
                .iload(0)
                .ifle(loopEnd)
                .iload(1)
                .iload(0)
                .iadd()
                .istore(1)
                .iload(0)
                .iconst(1)
                .isub()
                .istore(0)
                .goto_(loopStart)
                .label(loopEnd)
                .iload(1)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10));

            assertEquals(55, result.getReturnValue().asInt());
        }

        @Test
        void testConditionalAccumulation() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I");
            Label loopStart = mb.newLabel();
            Label loopEnd = mb.newLabel();
            Label skipAdd = mb.newLabel();

            ClassFile cf = mb
                .iconst(0)
                .istore(1)
                .iconst(0)
                .istore(2)
                .label(loopStart)
                .iload(1)
                .iload(0)
                .if_icmpge(loopEnd)
                .iload(1)
                .iconst(2)
                .irem()
                .ifne(skipAdd)
                .iload(2)
                .iload(1)
                .iadd()
                .istore(2)
                .label(skipAdd)
                .iload(1)
                .iconst(1)
                .iadd()
                .istore(1)
                .goto_(loopStart)
                .label(loopEnd)
                .iload(2)
                .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10));

            assertEquals(20, result.getReturnValue().asInt());
        }
    }
}
