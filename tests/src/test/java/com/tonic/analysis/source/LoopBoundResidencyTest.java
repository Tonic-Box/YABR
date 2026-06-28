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

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A loop bound recomputed each iteration ({@code for (i; i < s.length(); i++)}) must stay on the operand
 * stack like javac compiles it (push i, compute the bound, compare), not be spilled to a header local - which
 * would force the decompiler into a {@code while(true)}+break loop on round trip. Validated by EXECUTING the
 * recompiled bytecode (the Gradle test JVM does not verify) and YABR's Verifier.
 */
class LoopBoundResidencyTest {

    private static final String SRC =
        "package test; public class Lb { public static int f(int n, String s) {"
        + " int sum = 0;"
        + " for (int i = 0; i < s.length(); i++) { sum += s.charAt(i); }"   // call bound
        + " for (int i = 0; i < n / 4; i++) { sum += i; }"                  // division bound
        + " return sum; } }";

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void loopBoundStaysOnStackCorrectAndClean() throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        int pubStatic = new AccessBuilder().setPublic().setStatic().build();
        ClassFile cf = pool.createNewClass("test/Lb", new AccessBuilder().setPublic().build());

        lowerF(cf, pool, SRC, pubStatic);
        String src1 = methodBody(ClassDecompiler.decompile(cf), "f");
        lowerF(cf, pool, "package test; public class Lb {" + src1 + "}", pubStatic);
        String src2 = methodBody(ClassDecompiler.decompile(cf), "f");
        lowerF(cf, pool, "package test; public class Lb {" + src2 + "}", pubStatic);
        String src3 = methodBody(ClassDecompiler.decompile(cf), "f");

        VerificationResult vr = Verifier.builder().classPool(pool).build().verify(cf);
        assertTrue(vr.getErrors().stream().noneMatch(VerificationError::isError),
            "recompiled bytecode must verify: " + vr.getErrors());

        // Execute: char codes 'a'+'b'+'c' = 294, plus 0+1+2+3+4 (n/4 = 5) = 10 -> 304. A broken stack crashes.
        Class<?> c = defineClass("test.Lb", cf.write());
        Method f = c.getDeclaredMethod("f", int.class, String.class);
        f.setAccessible(true);
        assertEquals(304, f.invoke(null, 20, "abc"), "loops must sum correctly");

        // Both bounds (a call and a division) stay in the for-condition, not a while(true)+break, every round.
        for (String src : new String[]{src1, src2}) {
            assertTrue(src.contains("i < s.length()") && src.contains("i < n / 4"),
                "expected clean for-conditions with the bounds inline:\n" + src);
            assertFalse(src.contains("while") || src.contains("break"),
                "must not degrade into a while(true)+break loop:\n" + src);
        }
        assertEquals(src2, src3, "must stabilize after the first round trip:\n" + src2 + "\n---\n" + src3);
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
            target = cf.createNewMethodWithDescriptor(access, "f", "(ILjava/lang/String;)I");
        }
        new SSA(cf.getConstPool()).lower(lowerer.lower(f, "test/Lb"), target);
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
