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

class OpcodeDispatcherLoadStoreNTest {
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
    class ILoadNTests {
        @Test
        void testILoad0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test")
                    .publicStaticMethod("test", "(I)I")
                        .iload_0()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(42));
            assertEquals(42, result.getReturnValue().asInt());
        }

        @Test
        void testILoad1() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test2")
                    .publicStaticMethod("test", "(II)I")
                        .iload_1()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(10), ConcreteValue.intValue(20));
            assertEquals(20, result.getReturnValue().asInt());
        }

        @Test
        void testILoad2() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test3")
                    .publicStaticMethod("test", "(III)I")
                        .iload_2()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(1), ConcreteValue.intValue(2), ConcreteValue.intValue(30));
            assertEquals(30, result.getReturnValue().asInt());
        }

        @Test
        void testILoad3() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("Test4")
                    .publicStaticMethod("test", "(IIII)I")
                        .iload_3()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(1), ConcreteValue.intValue(2), ConcreteValue.intValue(3), ConcreteValue.intValue(40));
            assertEquals(40, result.getReturnValue().asInt());
        }
    }

    @Nested
    class LLoadNTests {
        @Test
        void testLLoad0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestL0")
                    .publicStaticMethod("test", "(J)J")
                        .lload_0()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(100L));
            assertEquals(100L, result.getReturnValue().asLong());
        }

        @Test
        void testLLoad2() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestL2")
                    .publicStaticMethod("test", "(JJ)J")
                        .lload_2()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(100L), ConcreteValue.longValue(200L));
            assertEquals(200L, result.getReturnValue().asLong());
        }
    }

    @Nested
    class FLoadNTests {
        @Test
        void testFLoad0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestF0")
                    .publicStaticMethod("test", "(F)F")
                        .fload_0()
                        .freturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.floatValue(3.14f));
            assertEquals(3.14f, result.getReturnValue().asFloat(), 0.001);
        }

        @Test
        void testFLoad1() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestF1")
                    .publicStaticMethod("test", "(FF)F")
                        .fload_1()
                        .freturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.floatValue(1.0f), ConcreteValue.floatValue(2.5f));
            assertEquals(2.5f, result.getReturnValue().asFloat(), 0.001);
        }
    }

    @Nested
    class DLoadNTests {
        @Test
        void testDLoad0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestD0")
                    .publicStaticMethod("test", "(D)D")
                        .dload_0()
                        .dreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.doubleValue(3.14159));
            assertEquals(3.14159, result.getReturnValue().asDouble(), 0.00001);
        }

        @Test
        void testDLoad2() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestD2")
                    .publicStaticMethod("test", "(DD)D")
                        .dload_2()
                        .dreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.doubleValue(1.0), ConcreteValue.doubleValue(2.718));
            assertEquals(2.718, result.getReturnValue().asDouble(), 0.001);
        }
    }

    @Nested
    class ALoadNTests {
        @Test
        void testALoad0Null() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestA0")
                    .publicStaticMethod("test", "(Ljava/lang/Object;)Ljava/lang/Object;")
                        .aload_0()
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.nullRef());
            assertTrue(result.getReturnValue().isNull());
        }
    }

    @Nested
    class IStoreNTests {
        @Test
        void testIStore0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIS0")
                    .publicStaticMethod("test", "(I)I")
                        .iconst(99)
                        .istore_0()
                        .iload_0()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(1));
            assertEquals(99, result.getReturnValue().asInt());
        }

        @Test
        void testIStore1() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestIS1")
                    .publicStaticMethod("test", "(II)I")
                        .iconst(88)
                        .istore_1()
                        .iload_1()
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(1), ConcreteValue.intValue(2));
            assertEquals(88, result.getReturnValue().asInt());
        }
    }

    @Nested
    class LStoreNTests {
        @Test
        void testLStore0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLS0")
                    .publicStaticMethod("test", "(J)J")
                        .lconst(999L)
                        .lstore_0()
                        .lload_0()
                        .lreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.longValue(1L));
            assertEquals(999L, result.getReturnValue().asLong());
        }
    }

    @Nested
    class FStoreNTests {
        @Test
        void testFStore0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestFS0")
                    .publicStaticMethod("test", "(F)F")
                        .fconst(9.9f)
                        .fstore_0()
                        .fload_0()
                        .freturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.floatValue(1.0f));
            assertEquals(9.9f, result.getReturnValue().asFloat(), 0.01);
        }
    }

    @Nested
    class DStoreNTests {
        @Test
        void testDStore0() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestDS0")
                    .publicStaticMethod("test", "(D)D")
                        .dconst(9.99)
                        .dstore_0()
                        .dload_0()
                        .dreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.doubleValue(1.0));
            assertEquals(9.99, result.getReturnValue().asDouble(), 0.001);
        }
    }

    @Nested
    class AStoreNTests {
        @Test
        void testAStore0Null() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestAS0")
                    .publicStaticMethod("test", "(Ljava/lang/Object;)Ljava/lang/Object;")
                        .aconst_null()
                        .astore_0()
                        .aload_0()
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.nullRef());
            assertTrue(result.getReturnValue().isNull());
        }
    }
}
