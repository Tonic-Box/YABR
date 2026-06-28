package com.tonic.analysis.source;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A long/float/double comparison compiles to {@code Xcmp; ifXX}. It must recover as a direct relational
 * comparison ({@code a > b}), not {@code (a - b) > 0}: the subtraction form re-lowers to {@code lcmp((a-b),
 * 0L)} and recovers as {@code (a - b - 0) > 0}, accumulating a spurious {@code - 0} on every round trip.
 */
class LcmpRecoveryTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void longCompareRecoversAsDirectComparisonAndConverges() throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        int pubStatic = new AccessBuilder().setPublic().setStatic().build();
        ClassFile cf = pool.createNewClass("test/Cmp", new AccessBuilder().setPublic().build());

        lowerF(cf, pool,
            "package test; public class Cmp { public static int f(long a, long b) {"
            + " if (a > b) { return 1; } return 0; } }", pubStatic);

        String src1 = methodBody(ClassDecompiler.decompile(cf), "f");
        // Second round trip from the decompiled source.
        lowerF(cf, pool, "package test; public class Cmp {" + src1 + "}", pubStatic);
        String src2 = methodBody(ClassDecompiler.decompile(cf), "f");

        assertFalse(src1.contains("- (long) 0"),
            "lcmp must not recover as a `(a - b) - 0` subtraction:\n" + src1);
        assertTrue(Pattern.compile("if\\s*\\([^)]*[<>]").matcher(src1).find(),
            "the long comparison should recover as a relational `if (a > b)`:\n" + src1);
        assertEquals(src1, src2,
            "the comparison must round-trip identically (no accumulating `- 0`):\nsrc1:\n" + src1 + "\nsrc2:\n" + src2);
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
            target = cf.createNewMethodWithDescriptor(access, "f", "(JJ)I");
        }
        new SSA(cf.getConstPool()).lower(lowerer.lower(f, "test/Cmp"), target);
        try { cf.rebuild(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String methodBody(String src, String name) {
        boolean in = false;
        int depth = 0;
        StringBuilder sb = new StringBuilder();
        for (String l : src.split("\n")) {
            if (!in && l.contains(" " + name + "(")) in = true;
            if (in) {
                sb.append(l.trim()).append("\n");
                depth += (int) (l.chars().filter(c -> c == '{').count() - l.chars().filter(c -> c == '}').count());
                if (depth <= 0 && l.contains("}")) break;
            }
        }
        return sb.toString();
    }
}
