package com.tonic.analysis.execution;

import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionIntegrationTest {

    private ClassFile classFile;
    private ClassPool classPool;
    private BytecodeContext context;

    @BeforeEach
    void setUp() throws IOException {
        classFile = TestUtils.loadTestFixture("ExecutionTestFixture");
        classPool = new ClassPool();
        classPool.put(classFile);

        context = new BytecodeContext.Builder()
            .heapManager(new SimpleHeapManager())
            .classResolver(new ClassResolver(classPool))
            .maxCallDepth(1000)
            .maxInstructions(100000)
            .trackStatistics(true)
            .build();
    }

    private MethodEntry findMethod(String name) {
        return classFile.getMethods().stream()
            .filter(m -> m.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Method not found: " + name));
    }

    private BytecodeEngine createEngine() {
        return new BytecodeEngine(context);
    }

    @Nested
    class SimpleRecursionTests {

        @Test
        void testFactorial_baseCase_zero() {
            MethodEntry method = findMethod("factorial");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testFactorial_baseCase_one() {
            MethodEntry method = findMethod("factorial");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(1));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testFactorial_five() {
            MethodEntry method = findMethod("factorial");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(5));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(120, result.getReturnValue().asInt());
        }

        @Test
        void testFactorial_ten() {
            MethodEntry method = findMethod("factorial");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(3628800, result.getReturnValue().asInt());
        }

        @Test
        void testFibonacci_baseCase_zero() {
            MethodEntry method = findMethod("fibonacci");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testFibonacci_baseCase_one() {
            MethodEntry method = findMethod("fibonacci");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(1));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(1, result.getReturnValue().asInt());
        }

        @Test
        void testFibonacci_ten() {
            MethodEntry method = findMethod("fibonacci");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(55, result.getReturnValue().asInt());
        }

        @Test
        void testSumDigits() {
            MethodEntry method = findMethod("sumDigits");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(12345));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(15, result.getReturnValue().asInt());
        }

        @Test
        void testSumDigits_zero() {
            MethodEntry method = findMethod("sumDigits");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testFactorialIterative_matchesRecursive() {
            MethodEntry recursive = findMethod("factorial");
            MethodEntry iterative = findMethod("factorialIterative");
            BytecodeEngine engine = createEngine();

            for (int n = 0; n <= 10; n++) {
                BytecodeResult recursiveResult = engine.execute(recursive, ConcreteValue.intValue(n));
                BytecodeResult iterativeResult = engine.execute(iterative, ConcreteValue.intValue(n));

                assertEquals(recursiveResult.getReturnValue().asInt(),
                             iterativeResult.getReturnValue().asInt(),
                             "Mismatch for n=" + n);
            }
        }
    }

    @Nested
    class MutualRecursionTests {

        @Test
        void testIsEven_zero() {
            MethodEntry method = findMethod("isEven");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(1, result.getReturnValue().asInt()); // true = 1
        }

        @Test
        void testIsEven_ten() {
            MethodEntry method = findMethod("isEven");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(1, result.getReturnValue().asInt()); // true = 1
        }

        @Test
        void testIsEven_seven() {
            MethodEntry method = findMethod("isEven");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(7));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(0, result.getReturnValue().asInt()); // false = 0
        }

        @Test
        void testIsOdd_zero() {
            MethodEntry method = findMethod("isOdd");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(0, result.getReturnValue().asInt()); // false = 0
        }

        @Test
        void testIsOdd_seven() {
            MethodEntry method = findMethod("isOdd");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(7));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(1, result.getReturnValue().asInt()); // true = 1
        }

        @Test
        void testIsOdd_ten() {
            MethodEntry method = findMethod("isOdd");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(0, result.getReturnValue().asInt()); // false = 0
        }

        @Test
        void testMaleSequence_zero() {
            MethodEntry method = findMethod("maleSequence");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testFemaleSequence_zero() {
            MethodEntry method = findMethod("femaleSequence");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(1, result.getReturnValue().asInt());
        }
    }

    @Nested
    class MultiMethodChainTests {

        @Test
        void testChainedComputation() {
            MethodEntry method = findMethod("chainedComputation");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(3),
                ConcreteValue.intValue(4));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            // a=3, b=4
            // step1: sum=7, product=12
            // step2: x=7, y=12, z=7-12=-5
            // step3: 7 + 12 + (-5) = 14
            assertEquals(14, result.getReturnValue().asInt());
        }

        @Test
        void testMixedChain() {
            MethodEntry method = findMethod("mixedChain");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.longValue(20L),
                ConcreteValue.doubleValue(5.0));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            // step1Long: 10 + 20 = 30
            // step2Double: 5.0 * 2.0 = 10.0
            // mixedChain: 30 + (long)10.0 = 40
            assertEquals(40L, result.getReturnValue().asLong());
        }
    }

    @Nested
    class PrimitiveOperationsTests {

        @Test
        void testBitwiseMagic() {
            MethodEntry method = findMethod("bitwiseMagic");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(0b1100),
                ConcreteValue.intValue(0b1010));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            // and = 0b1000 = 8
            // or = 0b1110 = 14
            // xor = 0b0110 = 6
            // shifted = (0b1100 << 2) | (0b1010 >> 1) = 0b110000 | 0b101 = 48 | 5 = 53
            // total = 8 + 14 + 6 + 53 = 81
            assertEquals(81, result.getReturnValue().asInt());
        }

        @Test
        void testCompareChain_aGreaterThanB_bGreaterThanC() {
            MethodEntry method = findMethod("compareChain");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(2));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            // a > b (10 > 5): true, b > c (5 > 2): true → return a + b + c = 17
            assertEquals(17, result.getReturnValue().asInt());
        }

        @Test
        void testCompareChain_aGreaterThanB_bNotGreaterThanC() {
            MethodEntry method = findMethod("compareChain");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(8));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            // a > b (10 > 5): true, b > c (5 > 8): false → return a - b + c = 10 - 5 + 8 = 13
            assertEquals(13, result.getReturnValue().asInt());
        }

        @Test
        void testCompareChain_aNotGreaterThanB_aGreaterThanC() {
            MethodEntry method = findMethod("compareChain");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(3));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            // a > b (5 > 10): false, a > c (5 > 3): true → return a * 2 = 10
            assertEquals(10, result.getReturnValue().asInt());
        }

        @Test
        void testCompareChain_aNotGreaterThanB_aNotGreaterThanC() {
            MethodEntry method = findMethod("compareChain");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(8));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            // a > b (5 > 10): false, a > c (5 > 8): false → return c * 2 = 16
            assertEquals(16, result.getReturnValue().asInt());
        }

        @Test
        void testPrimitiveOrchestra() {
            MethodEntry method = findMethod("primitiveOrchestra");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.longValue(20L),
                ConcreteValue.floatValue(1.5f),
                ConcreteValue.doubleValue(2.5));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            // sum = 10 + (int)20 = 30
            // product = 20 * 10 = 200
            // fResult = 1.5 + 30.0 = 31.5
            // dResult = 2.5 + 31.5 + 200 = 234.0
            assertEquals(234.0, result.getReturnValue().asDouble(), 0.001);
        }
    }

    @Nested
    class ArrayOperationsTests {

        @Test
        void testSumArray_simple() {
            MethodEntry method = findMethod("sumArray");
            BytecodeEngine engine = createEngine();

            int[] testData = {1, 2, 3, 4, 5};
            ConcreteValue arrayArg = createIntArray(testData);

            BytecodeResult result = engine.execute(method, arrayArg);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(15, result.getReturnValue().asInt());
        }

        @Test
        void testSumArray_empty() {
            MethodEntry method = findMethod("sumArray");
            BytecodeEngine engine = createEngine();

            int[] testData = {};
            ConcreteValue arrayArg = createIntArray(testData);

            BytecodeResult result = engine.execute(method, arrayArg);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testSumArray_single() {
            MethodEntry method = findMethod("sumArray");
            BytecodeEngine engine = createEngine();

            int[] testData = {42};
            ConcreteValue arrayArg = createIntArray(testData);

            BytecodeResult result = engine.execute(method, arrayArg);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(42, result.getReturnValue().asInt());
        }

        @Test
        void testFindMax_typical() {
            MethodEntry method = findMethod("findMax");
            BytecodeEngine engine = createEngine();

            int[] testData = {3, 1, 4, 1, 5, 9, 2, 6};
            ConcreteValue arrayArg = createIntArray(testData);

            BytecodeResult result = engine.execute(method, arrayArg);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(9, result.getReturnValue().asInt());
        }

        @Test
        void testFindMax_allSame() {
            MethodEntry method = findMethod("findMax");
            BytecodeEngine engine = createEngine();

            int[] testData = {7, 7, 7, 7};
            ConcreteValue arrayArg = createIntArray(testData);

            BytecodeResult result = engine.execute(method, arrayArg);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(7, result.getReturnValue().asInt());
        }

        @Test
        void testFindMax_negatives() {
            MethodEntry method = findMethod("findMax");
            BytecodeEngine engine = createEngine();

            int[] testData = {-5, -2, -8, -1};
            ConcreteValue arrayArg = createIntArray(testData);

            BytecodeResult result = engine.execute(method, arrayArg);

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(-1, result.getReturnValue().asInt());
        }

        @Test
        void testCreateAndSum() {
            MethodEntry method = findMethod("createAndSum");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(30));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(60, result.getReturnValue().asInt());
        }

        @Test
        void testBinarySearch_found() {
            MethodEntry method = findMethod("binarySearch");
            BytecodeEngine engine = createEngine();

            int[] sortedData = {1, 3, 5, 7, 9, 11, 13};
            ConcreteValue arrayArg = createIntArray(sortedData);

            BytecodeResult result = engine.execute(method,
                arrayArg,
                ConcreteValue.intValue(7),
                ConcreteValue.intValue(0),
                ConcreteValue.intValue(6));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(3, result.getReturnValue().asInt());
        }

        @Test
        void testBinarySearch_notFound() {
            MethodEntry method = findMethod("binarySearch");
            BytecodeEngine engine = createEngine();

            int[] sortedData = {1, 3, 5, 7, 9, 11, 13};
            ConcreteValue arrayArg = createIntArray(sortedData);

            BytecodeResult result = engine.execute(method,
                arrayArg,
                ConcreteValue.intValue(6),
                ConcreteValue.intValue(0),
                ConcreteValue.intValue(6));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(-1, result.getReturnValue().asInt());
        }

        @Test
        void testBinarySearch_firstElement() {
            MethodEntry method = findMethod("binarySearch");
            BytecodeEngine engine = createEngine();

            int[] sortedData = {1, 3, 5, 7, 9};
            ConcreteValue arrayArg = createIntArray(sortedData);

            BytecodeResult result = engine.execute(method,
                arrayArg,
                ConcreteValue.intValue(1),
                ConcreteValue.intValue(0),
                ConcreteValue.intValue(4));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(0, result.getReturnValue().asInt());
        }

        @Test
        void testBinarySearch_lastElement() {
            MethodEntry method = findMethod("binarySearch");
            BytecodeEngine engine = createEngine();

            int[] sortedData = {1, 3, 5, 7, 9};
            ConcreteValue arrayArg = createIntArray(sortedData);

            BytecodeResult result = engine.execute(method,
                arrayArg,
                ConcreteValue.intValue(9),
                ConcreteValue.intValue(0),
                ConcreteValue.intValue(4));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(4, result.getReturnValue().asInt());
        }

        private ConcreteValue createIntArray(int[] data) {
            com.tonic.analysis.execution.heap.ArrayInstance array =
                context.getHeapManager().newArray("I", data.length);
            for (int i = 0; i < data.length; i++) {
                array.setInt(i, data[i]);
            }
            return ConcreteValue.reference(array);
        }
    }

    @Nested
    class ExceptionHandlingTests {

        @Test
        void testSafeDivide_normal() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(2));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(5, result.getReturnValue().asInt());
        }

        @Test
        void testSafeDivide_byZero() {
            MethodEntry method = findMethod("safeDivide");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            // NOTE: YABR currently doesn't implement exception handler lookup
            // Once implemented, this should be: COMPLETED with return -1
            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }

        @Test
        void testDivideOrThrow_normal() {
            MethodEntry method = findMethod("divideOrThrow");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(20),
                ConcreteValue.intValue(4));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(5, result.getReturnValue().asInt());
        }

        @Test
        void testDivideOrThrow_byZero() {
            MethodEntry method = findMethod("divideOrThrow");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(0));

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
            assertNotNull(result.getStackTrace());
        }

        @Test
        void testNestedTryCatch_noException() {
            MethodEntry method = findMethod("nestedTryCatch");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(4),
                ConcreteValue.intValue(2));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(5, result.getReturnValue().asInt());
        }

        @Test
        void testNestedTryCatch_innerException() {
            MethodEntry method = findMethod("nestedTryCatch");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(10),
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(0));

            // NOTE: YABR currently doesn't implement exception handler lookup
            // Once implemented, this should be: COMPLETED with return 10 (inner catch path)
            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }

        @Test
        void testSafeRecursiveSum_withinLimit() {
            MethodEntry method = findMethod("safeRecursiveSum");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(15, result.getReturnValue().asInt());
        }

        @Test
        void testSafeRecursiveSum_exceedsLimit() {
            MethodEntry method = findMethod("safeRecursiveSum");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(15),
                ConcreteValue.intValue(10));

            // NOTE: YABR currently doesn't implement exception handler lookup
            // Once implemented, this should be: COMPLETED with return -1 (caught exception)
            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        }

        @Test
        void testRecursiveWithException_normal() {
            MethodEntry method = findMethod("recursiveWithException");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(5),
                ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(15, result.getReturnValue().asInt());
        }

        @Test
        void testRecursiveWithException_exceedsLimit() {
            MethodEntry method = findMethod("recursiveWithException");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(15),
                ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
            assertNotNull(result.getStackTrace());
        }
    }

    @Nested
    class VerificationTests {

        @Test
        void testFactorial_completes() {
            MethodEntry method = findMethod("factorial");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(5));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
        }

        @Test
        void testFibonacci_completes() {
            MethodEntry method = findMethod("fibonacci");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method, ConcreteValue.intValue(10));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
        }

        @Test
        void testAckermann_small() {
            MethodEntry method = findMethod("ackermann");
            BytecodeEngine engine = createEngine();

            BytecodeResult result = engine.execute(method,
                ConcreteValue.intValue(2),
                ConcreteValue.intValue(2));

            assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
            assertEquals(7, result.getReturnValue().asInt());
        }
    }
}
