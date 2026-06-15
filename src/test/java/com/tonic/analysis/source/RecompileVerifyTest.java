package com.tonic.analysis.source;

import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for recompiler / StackMapTable frame generation: each compiles a Java source string
 * through the full source pipeline and forces JVM bytecode verification ({@link TestUtils#linkAndVerify}).
 * Every construct here previously produced bytecode that failed verification.
 */
class RecompileVerifyTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    private void verify(String internalName, String source) {
        assertDoesNotThrow(() -> TestUtils.linkAndVerify(TestUtils.compileSource(source, internalName)),
                "recompiled class must pass JVM verification");
    }

    @Test
    void exhaustiveTableSwitchReturningEveryCase() {
        // Was: a stray unreachable trailing `return` after the switch's fall-through join.
        verify("test/SwitchT",
                "package test; public class SwitchT { public static String f(int x){"
                        + " switch(x){ case 0: return \"a\"; case 1: return \"b\"; case 2: return \"c\"; default: return \"d\"; } } }");
    }

    @Test
    void sparseLookupSwitchReturningEveryCase() {
        verify("test/SwitchL",
                "package test; public class SwitchL { public static int f(int x){"
                        + " switch(x){ case 1: return 10; case 100: return 20; case 1000: return 30; default: return 0; } } }");
    }

    @Test
    void tryCatchRegistersExceptionHandler() {
        // Was: the exception table was never emitted, leaving the handler as dead, frame-less code.
        verify("test/TryCatch",
                "package test; public class TryCatch { public static int f(String s){"
                        + " try { return Integer.parseInt(s); } catch(NumberFormatException e){ return -1; } } }");
    }

    @Test
    void multiCatchEmitsOneEntryPerType() {
        verify("test/MultiCatch",
                "package test; public class MultiCatch { public static int f(String s){"
                        + " try { return Integer.parseInt(s); } catch(NumberFormatException | NullPointerException e){ return -1; } } }");
    }

    @Test
    void longLoopCounterIncrement() {
        // Was: `long i++` emitted iconst_1 (one slot) into an ladd, underflowing the operand stack.
        verify("test/LongLoop",
                "package test; public class LongLoop { public static long f(long n){"
                        + " long s=0L; for(long i=0L;i<n;i++){ s += i*2L; } return s; } }");
    }

    @Test
    void doubleLoopCounterIncrement() {
        verify("test/DoubleLoop",
                "package test; public class DoubleLoop { public static double f(double x){"
                        + " double d=0.0; for(double i=0.0;i<x;i++){ d += i; } return d; } }");
    }

    @Test
    void longArithmeticInLoop() {
        verify("test/LongArr",
                "package test; public class LongArr { public static long f(int n){"
                        + " long s=0L; for(int i=0;i<n;i++){ s += (long)i * i; } return s; } }");
    }

    @Test
    void nestedIntLoops() {
        verify("test/Nested",
                "package test; public class Nested { public static int f(int n){"
                        + " int s=0; for(int i=0;i<n;i++) for(int j=0;j<i;j++) s += i*j; return s; } }");
    }

    @Test
    void tryCatchValueFlowsPastCatchToContinuation() {
        // Was: try/catch wasn't treated as a branch, so no SSA form was built; the try result was dropped and
        // the continuation always saw the catch value. Verify both that it links and that values are correct.
        String src = "package test; public class TryFlow { public static int f(String s){"
                + " int r; try { r = Integer.parseInt(s) * 4; } catch(NumberFormatException e){ r = -1; } return r; } }";
        verify("test/TryFlow", src);
        assertDoesNotThrow(() -> {
            Method f = TestUtils.compileLinkAndLoad(src, "test/TryFlow").getMethod("f", String.class);
            assertEquals(20, f.invoke(null, "5"), "try path value must flow to the continuation");
            assertEquals(-1, f.invoke(null, "x"), "catch path value must flow to the continuation");
        });
    }

    @Test
    void catchBlockUsesExceptionVariable() {
        // Was: the caught-exception local had an unqualified/Object type, so e.getMessage() resolved on Object
        // (wrong return type) and the chained .length() too, failing verification.
        verify("test/UsedEx",
                "package test; public class UsedEx { public static int f(String s){ int r=0;"
                        + " try { r = Integer.parseInt(s); } catch(NumberFormatException e){ r = e.getMessage().length(); } return r; } }");
    }

    @Test
    void nestedTryCatchProtectsInnerHandler() {
        // Was: the outer try's region (split by the interleaved inner handler) collapsed to a degenerate range
        // and its table entry was dropped, leaving the outer handler frame-less.
        verify("test/NestedTry",
                "package test; public class NestedTry { public static int f(String s){"
                        + " try { try { return Integer.parseInt(s); } catch(NullPointerException e){ return -2; } }"
                        + " catch(NumberFormatException e){ return -1; } } }");
    }

    @Test
    void tryCatchFinallyWithContinuation() {
        verify("test/TryFinally",
                "package test; public class TryFinally { public static int f(int x){ int r;"
                        + " try { r = 100/x; } catch(ArithmeticException e){ r = -1; } finally { x++; } return r + x; } }");
    }
}
