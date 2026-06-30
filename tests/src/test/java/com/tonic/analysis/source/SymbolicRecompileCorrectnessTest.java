package com.tonic.analysis.source;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.lower.TypeResolver;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Settles whether the decompile/recompile of {@code SymbolicExecutionTests} (whose round trip drifts on the
 * accumulator) PRESERVES SEMANTICS: executes {@code analyze(x,y,mode)} on the original bytecode and on the
 * bytecode recompiled from the first and second decompiles, and asserts identical results. A miscompile
 * (e.g. the accumulator coalesced with the loop counter) would change a result; identical results prove the
 * drift is cosmetic (naming), not a correctness bug.
 */
class SymbolicRecompileCorrectnessTest {

    private static final String DIR = "C:/Users/zacke/IdeaProjects/DemoApplication/build/classes/java/main";
    private static final int[][] INPUTS = {
        {10, 10, 0}, {10, 10, 1}, {10, 10, 2}, {10, 10, 5},
        {5, 5, 0}, {10, 5, 1}, {3, 7, 2}, {0, 0, 0}, {10, 10, -3}, {8, 12, 4}
    };

    @Test
    void recompilePreservesSemantics() throws Exception {
        Path p = Path.of(DIR, "osrs/dev/SymbolicExecutionTests.class");
        if (!Files.exists(p)) {
            return;
        }
        byte[] origBytes = Files.readAllBytes(p);
        int[] original = run(origBytes);

        // Diagnostic: recompile ONE method at a time (from fresh original) and report which one changes a result.
        String[] methods = {"analyze", "safeAdd", "safeDivide", "min", "abs", "computeSum", "computeProduct",
            "computeCombined", "computeRecursive", "computeSymmetric", "transform", "triangular", "computeCell",
            "isPositive", "combineChecks", "classify"};
        for (String only : methods) {
            ClassPool pool = new ClassPool();
            ClassFile cf = pool.loadClass(new ByteArrayInputStream(origBytes));
            String owner = cf.getClassName();
            String d1 = ClassDecompiler.decompile(cf);
            recompile(cf, pool, d1, owner, only);
            int[] r = run(cf.write());
            System.out.println("DIAG recompile-only " + only + ": "
                + (java.util.Arrays.equals(original, r) ? "OK" : "CHANGED " + java.util.Arrays.toString(r)
                    + " vs " + java.util.Arrays.toString(original)));
        }

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(origBytes));
        String owner = cf.getClassName();

        String d1 = ClassDecompiler.decompile(cf);
        recompile(cf, pool, d1, owner, null);
        int[] afterD1 = run(cf.write());

        assertArrayEquals(original, afterD1, "recompile(d1) must preserve analyze() semantics");
    }

    private static int[] run(byte[] bytes) throws Exception {
        Class<?> c = define("osrs.dev.SymbolicExecutionTests", bytes);
        Method m = c.getMethod("analyze", int.class, int.class, int.class);
        int[] out = new int[INPUTS.length];
        for (int i = 0; i < INPUTS.length; i++) {
            out[i] = (int) m.invoke(null, INPUTS[i][0], INPUTS[i][1], INPUTS[i][2]);
        }
        return out;
    }

    private static Class<?> define(String name, byte[] bytes) throws Exception {
        Method def = ClassLoader.class.getDeclaredMethod(
            "defineClass", String.class, byte[].class, int.class, int.class);
        def.setAccessible(true);
        return (Class<?>) def.invoke(new ClassLoader() {}, name, bytes, 0, bytes.length);
    }

    private static void recompile(ClassFile cf, ClassPool pool, String source, String owner, String only)
            throws Exception {
        CompilationUnit cu = JavaParser.create().parse(source);
        if (!(cu.getPrimaryType() instanceof ClassDecl)) {
            throw new IllegalStateException("recompile: source did not parse to a class");
        }
        ClassDecl decl = (ClassDecl) cu.getPrimaryType();
        TypeResolver resolver = new TypeResolver(pool, owner);
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(decl);
        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(cf.getConstPool());
        for (MethodDecl md : decl.getMethods()) {
            if (md.getBody() == null || (only != null && !md.getName().equals(only))) {
                continue;
            }
            String d = desc(md.getParameters(), resolver.descriptorOf(md.getReturnType()), resolver);
            MethodEntry target = find(cf, md.getName(), d);
            if (target != null) {
                ssa.lower(lowerer.lower(md, owner), target);
            }
        }
        cf.rebuild();
    }

    private static String desc(List<ParameterDecl> params, String ret, TypeResolver resolver) {
        StringBuilder d = new StringBuilder("(");
        for (ParameterDecl pp : params) {
            d.append(resolver.descriptorOf(pp.getType()));
        }
        return d.append(")").append(ret).toString();
    }

    private static MethodEntry find(ClassFile cf, String name, String desc) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name) && m.getDesc().contentEquals(desc)) {
                return m;
            }
        }
        return null;
    }
}
