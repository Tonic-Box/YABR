package com.tonic.analysis.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip regression tests for constructor emission. The emitter now produces the idiomatic
 * {@code new; dup; args; invokespecial} sequence (storing the result to a local), so {@code new X(args)} survives
 * decompile -> recompile instead of spilling to {@code new; astore; aload; ...; invokespecial} - which the decompiler
 * could not re-pair, dropping the arguments ({@code new X()}).
 */
class ConstructorRoundTripTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void constructorArgumentsSurviveRoundTrip() throws Exception {
        String src = "package test; public class CtorT {"
            + " public static String f(String s){ return new StringBuilder(s).append(\"!\").toString(); } }";
        ClassFile cf = TestUtils.compileSource(src, "test/CtorT");
        TestUtils.linkAndVerify(cf);
        String out = ClassDecompiler.decompile(cf);
        assertFalse(out.replaceAll("\\s+", "").contains("StringBuilder()"),
            "constructor must keep its argument, not become a no-arg new:\n" + out);
    }

    @Test
    void constantConstructorArgSurvives() throws Exception {
        String src = "package test; public class CtorC {"
            + " public static String f(){ return new StringBuilder(\"hi\").append(\"!\").toString(); } }";
        ClassFile cf = TestUtils.compileSource(src, "test/CtorC");
        TestUtils.linkAndVerify(cf);
        String out = ClassDecompiler.decompile(cf);
        assertTrue(out.contains("\"hi\""), "constructor argument literal must survive:\n" + out);
        assertFalse(out.replaceAll("\\s+", "").contains("StringBuilder()"),
            "constructor must keep its argument:\n" + out);
    }

    @Test
    void constructionNestedInStringConcatVerifies() throws Exception {
        // The case that exposed the frame bug: a construction whose result feeds a string concat (branchy bytecode).
        String src = "package test; public class CtorN {"
            + " public static String f(boolean c, int n){"
            + "   StringBuilder b = new StringBuilder(\"x\");"
            + "   for (int i = 0; i < n; i++) { b.append(new StringBuilder(\"y\").append(i).toString()); }"
            + "   return c ? b.toString() : \"\"; } }";
        ClassFile cf = TestUtils.compileSource(src, "test/CtorN");
        TestUtils.linkAndVerify(cf);
    }
}
