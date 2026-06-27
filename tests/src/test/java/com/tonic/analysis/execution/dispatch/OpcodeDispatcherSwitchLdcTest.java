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
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpcodeDispatcherSwitchLdcTest {
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
    class TableSwitchTests {
        @Test
        void testTableSwitchCase0() throws IOException {
            BytecodeBuilder.Label case0 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label case1 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label case2 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label defaultCase = new BytecodeBuilder.Label();
            BytecodeBuilder.Label end = new BytecodeBuilder.Label();

            Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
            cases.put(0, case0);
            cases.put(1, case1);
            cases.put(2, case2);

            ClassFile cf = BytecodeBuilder.forClass("TestTableSwitch")
                    .publicStaticMethod("test", "(I)I")
                        .iload(0)
                        .tableswitch(0, 2, cases, defaultCase)
                        .label(case0)
                            .iconst(100)
                            .goto_(end)
                        .label(case1)
                            .iconst(200)
                            .goto_(end)
                        .label(case2)
                            .iconst(300)
                            .goto_(end)
                        .label(defaultCase)
                            .iconst(-1)
                        .label(end)
                            .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(0));
            assertEquals(100, result.getReturnValue().asInt());
        }

        @Test
        void testTableSwitchCase1() throws IOException {
            BytecodeBuilder.Label case0 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label case1 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label defaultCase = new BytecodeBuilder.Label();
            BytecodeBuilder.Label end = new BytecodeBuilder.Label();

            Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
            cases.put(0, case0);
            cases.put(1, case1);

            ClassFile cf = BytecodeBuilder.forClass("TestTableSwitch2")
                    .publicStaticMethod("test", "(I)I")
                        .iload(0)
                        .tableswitch(0, 1, cases, defaultCase)
                        .label(case0)
                            .iconst(10)
                            .goto_(end)
                        .label(case1)
                            .iconst(20)
                            .goto_(end)
                        .label(defaultCase)
                            .iconst(-1)
                        .label(end)
                            .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(1));
            assertEquals(20, result.getReturnValue().asInt());
        }

        @Test
        void testTableSwitchDefault() throws IOException {
            BytecodeBuilder.Label case0 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label defaultCase = new BytecodeBuilder.Label();
            BytecodeBuilder.Label end = new BytecodeBuilder.Label();

            Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
            cases.put(0, case0);

            ClassFile cf = BytecodeBuilder.forClass("TestTableSwitchDefault")
                    .publicStaticMethod("test", "(I)I")
                        .iload(0)
                        .tableswitch(0, 0, cases, defaultCase)
                        .label(case0)
                            .iconst(1)
                            .goto_(end)
                        .label(defaultCase)
                            .iconst(999)
                        .label(end)
                            .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(42));
            assertEquals(999, result.getReturnValue().asInt());
        }
    }

    @Nested
    class LookupSwitchTests {
        @Test
        void testLookupSwitchHit() throws IOException {
            BytecodeBuilder.Label case10 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label case100 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label defaultCase = new BytecodeBuilder.Label();
            BytecodeBuilder.Label end = new BytecodeBuilder.Label();

            Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
            cases.put(10, case10);
            cases.put(100, case100);

            ClassFile cf = BytecodeBuilder.forClass("TestLookupSwitch")
                    .publicStaticMethod("test", "(I)I")
                        .iload(0)
                        .lookupswitch(cases, defaultCase)
                        .label(case10)
                            .iconst(1)
                            .goto_(end)
                        .label(case100)
                            .iconst(2)
                            .goto_(end)
                        .label(defaultCase)
                            .iconst(0)
                        .label(end)
                            .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(100));
            assertEquals(2, result.getReturnValue().asInt());
        }

        @Test
        void testLookupSwitchDefault() throws IOException {
            BytecodeBuilder.Label case1 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label defaultCase = new BytecodeBuilder.Label();
            BytecodeBuilder.Label end = new BytecodeBuilder.Label();

            Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
            cases.put(1, case1);

            ClassFile cf = BytecodeBuilder.forClass("TestLookupSwitchDefault")
                    .publicStaticMethod("test", "(I)I")
                        .iload(0)
                        .lookupswitch(cases, defaultCase)
                        .label(case1)
                            .iconst(111)
                            .goto_(end)
                        .label(defaultCase)
                            .iconst(-999)
                        .label(end)
                            .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(50));
            assertEquals(-999, result.getReturnValue().asInt());
        }

        @Test
        void testLookupSwitchNegativeKeys() throws IOException {
            BytecodeBuilder.Label caseNeg10 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label case0 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label case10 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label defaultCase = new BytecodeBuilder.Label();
            BytecodeBuilder.Label end = new BytecodeBuilder.Label();

            Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
            cases.put(-10, caseNeg10);
            cases.put(0, case0);
            cases.put(10, case10);

            ClassFile cf = BytecodeBuilder.forClass("TestLookupSwitchNeg")
                    .publicStaticMethod("test", "(I)I")
                        .iload(0)
                        .lookupswitch(cases, defaultCase)
                        .label(caseNeg10)
                            .iconst(-1)
                            .goto_(end)
                        .label(case0)
                            .iconst(0)
                            .goto_(end)
                        .label(case10)
                            .iconst(1)
                            .goto_(end)
                        .label(defaultCase)
                            .iconst(99)
                        .label(end)
                            .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(-10));
            assertEquals(-1, result.getReturnValue().asInt());
        }
    }

    @Nested
    class LdcStringTests {
        @Test
        void testLdcString() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcString")
                    .publicStaticMethod("test", "()Ljava/lang/String;")
                        .ldc("Hello World")
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertNotNull(result.getReturnValue());
        }

        @Test
        void testLdcEmptyString() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestLdcEmpty")
                    .publicStaticMethod("test", "()Ljava/lang/String;")
                        .ldc("")
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertNotNull(result.getReturnValue());
        }

        @Test
        void testLdcMultipleStrings() throws IOException {
            BytecodeBuilder.Label skip = new BytecodeBuilder.Label();
            ClassFile cf = BytecodeBuilder.forClass("TestLdcMulti")
                    .publicStaticMethod("test", "(I)Ljava/lang/String;")
                        .iload(0)
                        .ifeq(skip)
                        .ldc("Truthy")
                        .areturn()
                        .label(skip)
                        .ldc("Falsy")
                        .areturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result1 = execute(method, ConcreteValue.intValue(1));
            assertNotNull(result1.getReturnValue());
            BytecodeResult result2 = execute(method, ConcreteValue.intValue(0));
            assertNotNull(result2.getReturnValue());
        }
    }

    @Nested
    class CombinedSwitchTests {
        @Test
        void testSwitchWithComputation() throws IOException {
            BytecodeBuilder.Label case1 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label case2 = new BytecodeBuilder.Label();
            BytecodeBuilder.Label defaultCase = new BytecodeBuilder.Label();
            BytecodeBuilder.Label end = new BytecodeBuilder.Label();

            Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
            cases.put(1, case1);
            cases.put(2, case2);

            ClassFile cf = BytecodeBuilder.forClass("TestSwitchCompute")
                    .publicStaticMethod("test", "(II)I")
                        .iload(0)
                        .lookupswitch(cases, defaultCase)
                        .label(case1)
                            .iload(1)
                            .iconst(2)
                            .imul()
                            .goto_(end)
                        .label(case2)
                            .iload(1)
                            .iconst(3)
                            .imul()
                            .goto_(end)
                        .label(defaultCase)
                            .iload(1)
                        .label(end)
                            .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(1), ConcreteValue.intValue(5));
            assertEquals(10, result.getReturnValue().asInt());
        }
    }
}
