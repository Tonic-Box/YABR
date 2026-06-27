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

class OpcodeDispatcherBitwiseTest {
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
    class IntegerBitwiseAndTests {
        @Test
        void testIAnd() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIAnd")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .iand()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0xFF00), ConcreteValue.intValue(0x0FF0));
            assertEquals(0x0F00, result.getReturnValue().asInt());
        }

        @Test
        void testIAndWithZero() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIAndZero")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .iand()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0xFFFFFFFF), ConcreteValue.intValue(0));
            assertEquals(0, result.getReturnValue().asInt());
        }
    }

    @Nested
    class IntegerBitwiseOrTests {
        @Test
        void testIOr() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIOr")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .ior()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0xF000), ConcreteValue.intValue(0x00F0));
            assertEquals(0xF0F0, result.getReturnValue().asInt());
        }

        @Test
        void testIOrWithAll() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIOrAll")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .ior()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0), ConcreteValue.intValue(0xFFFFFFFF));
            assertEquals(0xFFFFFFFF, result.getReturnValue().asInt());
        }
    }

    @Nested
    class IntegerBitwiseXorTests {
        @Test
        void testIXor() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIXor")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .ixor()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0xFF00), ConcreteValue.intValue(0x0FF0));
            assertEquals(0xF0F0, result.getReturnValue().asInt());
        }

        @Test
        void testIXorSameValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIXorSame")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .ixor()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0x12345678), ConcreteValue.intValue(0x12345678));
            assertEquals(0, result.getReturnValue().asInt());
        }
    }

    @Nested
    class IntegerShiftLeftTests {
        @Test
        void testIShl() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIShl")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .ishl()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(1), ConcreteValue.intValue(4));
            assertEquals(16, result.getReturnValue().asInt());
        }

        @Test
        void testIShlByZero() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIShlZero")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .ishl()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(42), ConcreteValue.intValue(0));
            assertEquals(42, result.getReturnValue().asInt());
        }
    }

    @Nested
    class IntegerShiftRightTests {
        @Test
        void testIShr() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIShr")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .ishr()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(256), ConcreteValue.intValue(4));
            assertEquals(16, result.getReturnValue().asInt());
        }

        @Test
        void testIShrNegative() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIShrNeg")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .ishr()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(-256), ConcreteValue.intValue(4));
            assertEquals(-16, result.getReturnValue().asInt());
        }
    }

    @Nested
    class IntegerUnsignedShiftRightTests {
        @Test
        void testIUShr() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIUShr")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .iushr()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(256), ConcreteValue.intValue(4));
            assertEquals(16, result.getReturnValue().asInt());
        }

        @Test
        void testIUShrNegative() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIUShrNeg")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .iushr()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(-1), ConcreteValue.intValue(1));
            assertEquals(Integer.MAX_VALUE, result.getReturnValue().asInt());
        }
    }

    @Nested
    class LongBitwiseAndTests {
        @Test
        void testLAnd() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLAnd")
                    .publicStaticMethod("test", "(JJ)J")
                        .lload(0)
                        .lload(2)
                        .land()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(0xFF00FF00FF00FF00L), ConcreteValue.longValue(0x0FF00FF00FF00FF0L));
            assertEquals(0x0F000F000F000F00L, result.getReturnValue().asLong());
        }
    }

    @Nested
    class LongBitwiseOrTests {
        @Test
        void testLOr() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLOr")
                    .publicStaticMethod("test", "(JJ)J")
                        .lload(0)
                        .lload(2)
                        .lor()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(0xF000000000000000L), ConcreteValue.longValue(0x000000000000000FL));
            assertEquals(0xF00000000000000FL, result.getReturnValue().asLong());
        }
    }

    @Nested
    class LongBitwiseXorTests {
        @Test
        void testLXor() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLXor")
                    .publicStaticMethod("test", "(JJ)J")
                        .lload(0)
                        .lload(2)
                        .lxor()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(0xAAAAAAAAAAAAAAAAL), ConcreteValue.longValue(0xAAAAAAAAAAAAAAAAL));
            assertEquals(0L, result.getReturnValue().asLong());
        }
    }

    @Nested
    class LongShiftTests {
        @Test
        void testLShl() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLShl")
                    .publicStaticMethod("test", "(JI)J")
                        .lload(0)
                        .iload(2)
                        .lshl()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(1L), ConcreteValue.intValue(32));
            assertEquals(0x100000000L, result.getReturnValue().asLong());
        }

        @Test
        void testLShr() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLShr")
                    .publicStaticMethod("test", "(JI)J")
                        .lload(0)
                        .iload(2)
                        .lshr()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(0x100000000L), ConcreteValue.intValue(32));
            assertEquals(1L, result.getReturnValue().asLong());
        }

        @Test
        void testLShrNegative() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLShrNeg")
                    .publicStaticMethod("test", "(JI)J")
                        .lload(0)
                        .iload(2)
                        .lshr()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(-256L), ConcreteValue.intValue(4));
            assertEquals(-16L, result.getReturnValue().asLong());
        }

        @Test
        void testLUShr() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLUShr")
                    .publicStaticMethod("test", "(JI)J")
                        .lload(0)
                        .iload(2)
                        .lushr()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(-1L), ConcreteValue.intValue(1));
            assertEquals(Long.MAX_VALUE, result.getReturnValue().asLong());
        }
    }

    @Nested
    class IIncTests {
        @Test
        void testIIncPositive() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIIncPos")
                    .publicStaticMethod("test", "(I)I")
                        .iinc(0, 5)
                        .iload_0()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10));
            assertEquals(15, result.getReturnValue().asInt());
        }

        @Test
        void testIIncNegative() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIIncNeg")
                    .publicStaticMethod("test", "(I)I")
                        .iinc(0, -3)
                        .iload_0()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10));
            assertEquals(7, result.getReturnValue().asInt());
        }

        @Test
        void testIIncZero() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIIncZero")
                    .publicStaticMethod("test", "(I)I")
                        .iinc(0, 0)
                        .iload_0()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(42));
            assertEquals(42, result.getReturnValue().asInt());
        }

        @Test
        void testIIncOnDifferentLocal() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIIncLocal")
                    .publicStaticMethod("test", "(III)I")
                        .iinc(2, 100)
                        .iload_2()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(1), ConcreteValue.intValue(2), ConcreteValue.intValue(3));
            assertEquals(103, result.getReturnValue().asInt());
        }
    }

    @Nested
    class CombinedBitwiseTests {
        @Test
        void testBitmaskExtraction() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestBitmask")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iload(1)
                        .ishr()
                        .iconst(0xFF)
                        .iand()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0x12345678), ConcreteValue.intValue(8));
            assertEquals(0x56, result.getReturnValue().asInt());
        }

        @Test
        void testSetBit() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestSetBit")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .iconst(1)
                        .iload(1)
                        .ishl()
                        .ior()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0), ConcreteValue.intValue(3));
            assertEquals(8, result.getReturnValue().asInt());
        }
    }
}
