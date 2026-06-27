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

class OpcodeDispatcherArrayTest {
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
    class IntArrayTests {

        @Test
        void testNewIntArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(5)
                    .newarray(10)
                    .arraylength()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(5, result.getReturnValue().asInt());
        }

        @Test
        void testIAStoreAndLoad() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(3)
                    .newarray(10)
                    .dup()
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .iconst(0)
                    .iaload()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(42, result.getReturnValue().asInt());
        }

        @Test
        void testIAStoreMultipleElements() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(5)
                    .newarray(10)
                    .dup()
                    .iconst(0)
                    .iconst(10)
                    .iastore()
                    .dup()
                    .iconst(2)
                    .iconst(30)
                    .iastore()
                    .dup()
                    .iconst(4)
                    .iconst(50)
                    .iastore()
                    .iconst(2)
                    .iaload()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(30, result.getReturnValue().asInt());
        }

        @Test
        void testIntArrayLength() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(10)
                    .newarray(10)
                    .arraylength()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(10, result.getReturnValue().asInt());
        }
    }

    @Nested
    class LongArrayTests {

        @Test
        void testNewLongArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(7)
                    .newarray(11)
                    .arraylength()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(7, result.getReturnValue().asInt());
        }

        @Test
        void testLAStoreAndLoad() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()J")
                    .iconst(3)
                    .newarray(11)
                    .dup()
                    .iconst(1)
                    .lconst(123456789L)
                    .lastore()
                    .iconst(1)
                    .laload()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(123456789L, result.getReturnValue().asLong());
        }

        @Test
        void testLAStoreMultipleElements() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()J")
                    .iconst(5)
                    .newarray(11)
                    .dup()
                    .iconst(0)
                    .lconst(100L)
                    .lastore()
                    .dup()
                    .iconst(3)
                    .lconst(300L)
                    .lastore()
                    .iconst(3)
                    .laload()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(300L, result.getReturnValue().asLong());
        }
    }

    @Nested
    class FloatArrayTests {

        @Test
        void testNewFloatArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(4)
                    .newarray(6)
                    .arraylength()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(4, result.getReturnValue().asInt());
        }

        @Test
        void testFAStoreAndLoad() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()F")
                    .iconst(5)
                    .newarray(6)
                    .dup()
                    .iconst(2)
                    .fconst(3.14f)
                    .fastore()
                    .iconst(2)
                    .faload()
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(3.14f, result.getReturnValue().asFloat(), 0.001f);
        }

        @Test
        void testFAStoreMultipleElements() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()F")
                    .iconst(4)
                    .newarray(6)
                    .dup()
                    .iconst(0)
                    .fconst(1.1f)
                    .fastore()
                    .dup()
                    .iconst(1)
                    .fconst(2.2f)
                    .fastore()
                    .dup()
                    .iconst(3)
                    .fconst(4.4f)
                    .fastore()
                    .iconst(1)
                    .faload()
                    .freturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(2.2f, result.getReturnValue().asFloat(), 0.001f);
        }
    }

    @Nested
    class DoubleArrayTests {

        @Test
        void testNewDoubleArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(6)
                    .newarray(7)
                    .arraylength()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(6, result.getReturnValue().asInt());
        }

        @Test
        void testDAStoreAndLoad() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()D")
                    .iconst(3)
                    .newarray(7)
                    .dup()
                    .iconst(1)
                    .dconst(2.71828)
                    .dastore()
                    .iconst(1)
                    .daload()
                    .dreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(2.71828, result.getReturnValue().asDouble(), 0.00001);
        }

        @Test
        void testDAStoreMultipleElements() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()D")
                    .iconst(4)
                    .newarray(7)
                    .dup()
                    .iconst(0)
                    .dconst(1.1)
                    .dastore()
                    .dup()
                    .iconst(2)
                    .dconst(3.3)
                    .dastore()
                    .iconst(2)
                    .daload()
                    .dreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(3.3, result.getReturnValue().asDouble(), 0.00001);
        }
    }

    @Nested
    class ByteArrayTests {

        @Test
        void testNewByteArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(8)
                    .newarray(8)
                    .arraylength()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(8, result.getReturnValue().asInt());
        }

        @Test
        void testBAStoreAndLoad() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(5)
                    .newarray(8)
                    .dup()
                    .iconst(2)
                    .iconst(127)
                    .bastore()
                    .iconst(2)
                    .baload()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(127, result.getReturnValue().asInt());
        }

        @Test
        void testBAStoreNegativeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(3)
                    .newarray(8)
                    .dup()
                    .iconst(1)
                    .iconst(-128)
                    .bastore()
                    .iconst(1)
                    .baload()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(-128, result.getReturnValue().asInt());
        }

        @Test
        void testBAStoreMultipleElements() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(5)
                    .newarray(8)
                    .dup()
                    .iconst(0)
                    .iconst(10)
                    .bastore()
                    .dup()
                    .iconst(2)
                    .iconst(20)
                    .bastore()
                    .dup()
                    .iconst(4)
                    .iconst(30)
                    .bastore()
                    .iconst(2)
                    .baload()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(20, result.getReturnValue().asInt());
        }
    }

    @Nested
    class CharArrayTests {

        @Test
        void testNewCharArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(10)
                    .newarray(5)
                    .arraylength()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(10, result.getReturnValue().asInt());
        }

        @Test
        void testCAStoreAndLoad() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(5)
                    .newarray(5)
                    .dup()
                    .iconst(3)
                    .iconst(65)
                    .castore()
                    .iconst(3)
                    .caload()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(65, result.getReturnValue().asInt());
        }

        @Test
        void testCAStoreUnicodeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(3)
                    .newarray(5)
                    .dup()
                    .iconst(1)
                    .iconst(0x3042)
                    .castore()
                    .iconst(1)
                    .caload()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(0x3042, result.getReturnValue().asInt());
        }
    }

    @Nested
    class ShortArrayTests {

        @Test
        void testNewShortArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(12)
                    .newarray(9)
                    .arraylength()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(12, result.getReturnValue().asInt());
        }

        @Test
        void testSAStoreAndLoad() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(5)
                    .newarray(9)
                    .dup()
                    .iconst(2)
                    .iconst(32767)
                    .sastore()
                    .iconst(2)
                    .saload()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(32767, result.getReturnValue().asInt());
        }

        @Test
        void testSAStoreNegativeValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(4)
                    .newarray(9)
                    .dup()
                    .iconst(1)
                    .iconst(-32768)
                    .sastore()
                    .iconst(1)
                    .saload()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(-32768, result.getReturnValue().asInt());
        }

        @Test
        void testSAStoreMultipleElements() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestClass")
                .publicStaticMethod("test", "()I")
                    .iconst(5)
                    .newarray(9)
                    .dup()
                    .iconst(0)
                    .iconst(100)
                    .sastore()
                    .dup()
                    .iconst(2)
                    .iconst(200)
                    .sastore()
                    .dup()
                    .iconst(4)
                    .iconst(300)
                    .sastore()
                    .iconst(2)
                    .saload()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);

            assertEquals(200, result.getReturnValue().asInt());
        }
    }
}
