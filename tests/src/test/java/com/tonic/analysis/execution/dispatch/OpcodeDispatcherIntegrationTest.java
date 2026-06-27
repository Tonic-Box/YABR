package com.tonic.analysis.execution.dispatch;

import com.tonic.testutil.BytecodeBuilder;
import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.heap.ObjectInstance;
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

class OpcodeDispatcherIntegrationTest {
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
    class ConstantTests {

        @Test
        void testBipush() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(42, result.getReturnValue().asInt());
        }

        @Test
        void testSipush() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(1000)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(1000, result.getReturnValue().asInt());
        }

        @Test
        void testLdcInt() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(100000)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(100000, result.getReturnValue().asInt());
        }

        @Test
        void testLdcFloat() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()F")
                    .fconst(3.14f)
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(3.14f, result.getReturnValue().asFloat(), 0.001f);
        }

        @Test
        void testLdc2WLong() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()J")
                    .lconst(1234567890123L)
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(1234567890123L, result.getReturnValue().asLong());
        }

        @Test
        void testLdc2WDouble() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()D")
                    .dconst(2.71828)
                    .dreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(2.71828, result.getReturnValue().asDouble(), 0.00001);
        }
    }

    @Nested
    class IntLoadStoreTests {

        @Test
        void testILoad() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I")
                    .iload(0)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(99));

            assertEquals(99, result.getReturnValue().asInt());
        }

        @Test
        void testILoad0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(I)I")
                    .iload(0)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10));

            assertEquals(10, result.getReturnValue().asInt());
        }

        @Test
        void testILoad1() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(II)I")
                    .iload(1)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20));

            assertEquals(20, result.getReturnValue().asInt());
        }

        @Test
        void testILoad2() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(III)I")
                    .iload(2)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30));

            assertEquals(30, result.getReturnValue().asInt());
        }

        @Test
        void testILoad3() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(IIII)I")
                    .iload(3)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30),
                ConcreteValue.intValue(40));

            assertEquals(40, result.getReturnValue().asInt());
        }

        @Test
        void testIStore() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(55)
                    .istore(0)
                    .iload(0)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(55, result.getReturnValue().asInt());
        }
    }

    @Nested
    class LongLoadStoreTests {

        @Test
        void testLLoad0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(J)J")
                    .lload(0)
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(100L));

            assertEquals(100L, result.getReturnValue().asLong());
        }

        @Test
        void testLLoad1() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(IJ)J")
                    .lload(1)
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.longValue(200L));

            assertEquals(200L, result.getReturnValue().asLong());
        }

        @Test
        void testLLoad2() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(IIJ)J")
                    .lload(2)
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.longValue(300L));

            assertEquals(300L, result.getReturnValue().asLong());
        }

        @Test
        void testLLoad3() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(IIIJ)J")
                    .lload(3)
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30),
                ConcreteValue.longValue(400L));

            assertEquals(400L, result.getReturnValue().asLong());
        }

        @Test
        void testLStore() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()J")
                    .lconst(999L)
                    .lstore(0)
                    .lload(0)
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(999L, result.getReturnValue().asLong());
        }
    }

    @Nested
    class FloatLoadStoreTests {

        @Test
        void testFLoad0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(F)F")
                    .fload(0)
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.floatValue(1.5f));

            assertEquals(1.5f, result.getReturnValue().asFloat(), 0.001f);
        }

        @Test
        void testFLoad1() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(IF)F")
                    .fload(1)
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.floatValue(2.5f));

            assertEquals(2.5f, result.getReturnValue().asFloat(), 0.001f);
        }

        @Test
        void testFLoad2() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(IIF)F")
                    .fload(2)
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.floatValue(3.5f));

            assertEquals(3.5f, result.getReturnValue().asFloat(), 0.001f);
        }

        @Test
        void testFLoad3() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(IIIF)F")
                    .fload(3)
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30),
                ConcreteValue.floatValue(4.5f));

            assertEquals(4.5f, result.getReturnValue().asFloat(), 0.001f);
        }

        @Test
        void testFStore() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()F")
                    .fconst(7.25f)
                    .fstore(0)
                    .fload(0)
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(7.25f, result.getReturnValue().asFloat(), 0.001f);
        }
    }

    @Nested
    class DoubleLoadStoreTests {

        @Test
        void testDLoad0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(D)D")
                    .dload(0)
                    .dreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.doubleValue(1.23));

            assertEquals(1.23, result.getReturnValue().asDouble(), 0.00001);
        }

        @Test
        void testDLoad1() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(ID)D")
                    .dload(1)
                    .dreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.doubleValue(2.34));

            assertEquals(2.34, result.getReturnValue().asDouble(), 0.00001);
        }

        @Test
        void testDLoad2() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(IID)D")
                    .dload(2)
                    .dreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.doubleValue(3.45));

            assertEquals(3.45, result.getReturnValue().asDouble(), 0.00001);
        }

        @Test
        void testDLoad3() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(IIID)D")
                    .dload(3)
                    .dreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30),
                ConcreteValue.doubleValue(4.56));

            assertEquals(4.56, result.getReturnValue().asDouble(), 0.00001);
        }

        @Test
        void testDStore() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()D")
                    .dconst(9.87)
                    .dstore(0)
                    .dload(0)
                    .dreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(9.87, result.getReturnValue().asDouble(), 0.00001);
        }
    }

    @Nested
    class RefLoadStoreTests {

        @Test
        void testALoad0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(Ljava/lang/String;)Ljava/lang/String;")
                    .aload(0)
                    .areturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = context.getHeapManager().newObject("java/lang/String");
            ConcreteValue stringRef = ConcreteValue.reference(obj);
            BytecodeResult result = execute(method, stringRef);

            assertEquals(obj.getId(), result.getReturnValue().asReference().getId());
        }

        @Test
        void testALoad1() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(ILjava/lang/String;)Ljava/lang/String;")
                    .aload(1)
                    .areturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = context.getHeapManager().newObject("java/lang/String");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.reference(obj));

            assertEquals(obj.getId(), result.getReturnValue().asReference().getId());
        }

        @Test
        void testALoad2() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(IILjava/lang/String;)Ljava/lang/String;")
                    .aload(2)
                    .areturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = context.getHeapManager().newObject("java/lang/String");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.reference(obj));

            assertEquals(obj.getId(), result.getReturnValue().asReference().getId());
        }

        @Test
        void testALoad3() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(IIILjava/lang/String;)Ljava/lang/String;")
                    .aload(3)
                    .areturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = context.getHeapManager().newObject("java/lang/String");
            BytecodeResult result = execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30),
                ConcreteValue.reference(obj));

            assertEquals(obj.getId(), result.getReturnValue().asReference().getId());
        }

        @Test
        void testAStore() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "(Ljava/lang/String;)Ljava/lang/String;")
                    .aload(0)
                    .astore(1)
                    .aload(1)
                    .areturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = context.getHeapManager().newObject("java/lang/String");
            BytecodeResult result = execute(method, ConcreteValue.reference(obj));

            assertEquals(obj.getId(), result.getReturnValue().asReference().getId());
        }
    }

    @Nested
    class StackOperationTests {

        @Test
        void testDup() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(42)
                    .dup()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(84, result.getReturnValue().asInt());
        }

        @Test
        void testDup2() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()J")
                    .lconst(100L)
                    .dup2()
                    .ladd()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(200L, result.getReturnValue().asLong());
        }

        @Test
        void testSwap() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(10)
                    .iconst(5)
                    .swap()
                    .isub()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(-5, result.getReturnValue().asInt());
        }

        @Test
        void testPop() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(99)
                    .iconst(42)
                    .pop()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(99, result.getReturnValue().asInt());
        }

        @Test
        void testPop2() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()J")
                    .lconst(100L)
                    .lconst(200L)
                    .pop2()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(100L, result.getReturnValue().asLong());
        }
    }
}
