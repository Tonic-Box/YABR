package com.tonic.analysis.source;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.verifier.VerificationError;
import com.tonic.analysis.verifier.VerificationResult;
import com.tonic.analysis.verifier.Verifier;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A call receiver computed before a complex argument (e.g. {@code System.out.println("x" + i)}) must stay on
 * the operand stack like javac compiles it, not be spilled to a (type-reused, hence {@code Object}) local.
 * Validated by EXECUTING the recompiled bytecode (the Gradle test JVM does not verify), plus YABR's Verifier.
 */
class ReceiverResidencyTest {

    private static final String SRC =
        "package test; public class Recv { public static int f(int n) {"
        + " int s = 0; for (int i = 0; i < n; i++) { System.out.println(\"x\" + i); s = s + i; } return s; } }";

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void receiverStaysOnStackCorrectAndClean() throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        int pubStatic = new AccessBuilder().setPublic().setStatic().build();
        ClassFile cf = pool.createNewClass("test/Recv", new AccessBuilder().setPublic().build());

        lowerF(cf, pool, SRC, pubStatic);
        String src1 = methodBody(ClassDecompiler.decompile(cf), "f");
        // round trip
        lowerF(cf, pool, "package test; public class Recv {" + src1 + "}", pubStatic);
        String src2 = methodBody(ClassDecompiler.decompile(cf), "f");

        // Bytecode must verify...
        VerificationResult vr = Verifier.builder().classPool(pool).build().verify(cf);
        assertTrue(vr.getErrors().stream().noneMatch(VerificationError::isError),
            "recompiled bytecode must verify: " + vr.getErrors());

        // ...and EXECUTE correctly (the test JVM doesn't verify, so run it). f(4) prints x0..x3 and returns 6.
        Class<?> c = defineClass("test.Recv", cf.write());
        Method f = c.getDeclaredMethod("f", int.class);
        f.setAccessible(true);
        PrintStream orig = System.out;
        ByteArrayOutputStream cap = new ByteArrayOutputStream();
        Object result;
        System.setOut(new PrintStream(cap));
        try {
            result = f.invoke(null, 4);
        } finally {
            System.setOut(orig);
        }
        assertEquals(6, result, "loop result must be correct (0+1+2+3)");
        String printed = cap.toString().replace("\r\n", "\n");
        assertEquals("x0\nx1\nx2\nx3\n", printed, "println must receive the right argument each iteration");

        // Cleanliness + convergence: the receiver stays inline (no `Object` spill / `(PrintStream)` cast), stable.
        assertFalse(src1.contains("(PrintStream)"),
            "receiver should stay inline as System.out.println(...), not spilled to a cast local:\n" + src1);
        assertTrue(src1.contains("System.out.println"),
            "expected a clean System.out.println call:\n" + src1);
        assertEquals(src1, src2, "must round-trip identically:\n" + src1 + "\n---\n" + src2);
    }

    private static void lowerF(ClassFile cf, ClassPool pool, String source, int access) {
        CompilationUnit cu = JavaParser.create().parse(source);
        ClassDecl decl = (ClassDecl) cu.getPrimaryType();
        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());
        MethodDecl f = decl.getMethods().stream().filter(m -> m.getName().equals("f")).findFirst().orElseThrow();
        MethodEntry target = null;
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals("f")) { target = m; break; }
        }
        if (target == null) {
            target = cf.createNewMethodWithDescriptor(access, "f", "(I)I");
        }
        new SSA(cf.getConstPool()).lower(lowerer.lower(f, "test/Recv"), target);
        try { cf.rebuild(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Class<?> defineClass(String name, byte[] bytes) throws Exception {
        Method def = ClassLoader.class.getDeclaredMethod(
            "defineClass", String.class, byte[].class, int.class, int.class);
        def.setAccessible(true);
        return (Class<?>) def.invoke(new ClassLoader() {}, name, bytes, 0, bytes.length);
    }

    private static String methodBody(String src, String name) {
        boolean in = false;
        int depth = 0;
        StringBuilder sb = new StringBuilder();
        for (String l : src.split("\n")) {
            if (!in && l.contains(" " + name + "(")) in = true;
            if (in) {
                sb.append(l).append("\n");
                depth += (int) (l.chars().filter(c -> c == '{').count() - l.chars().filter(c -> c == '}').count());
                if (depth <= 0 && l.contains("}")) break;
            }
        }
        return sb.toString();
    }
}
