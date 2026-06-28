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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip stability: decompile -> recompile (all methods) -> decompile must NOT introduce a {@code $pc$}
 * dispatch-loop in a method that decompiled to structured code the first time. A new dispatch loop means the
 * structured recovery dropped an operation on the second pass (the "drift" users see on Refresh). Guarded on
 * the demo build output being present.
 */
class RoundTripStabilityTest {

    private static final String DIR = "C:/Users/zacke/IdeaProjects/DemoApplication/build/classes/java/main";

    @Test
    void demoClassesDoNotDriftIntoDispatchLoopOnRoundTrip() throws Exception {
        Path root = Path.of(DIR);
        Assumptions.assumeTrue(Files.isDirectory(root), "demo build output not present");

        List<Path> paths;
        try (var s = Files.walk(root)) {
            paths = s.filter(p -> p.toString().endsWith(".class")).sorted().collect(Collectors.toList());
        }

        List<String> drifts = new ArrayList<>();
        for (Path p : paths) {
            ClassPool pool = new ClassPool();
            ClassFile cf;
            try { cf = pool.loadClass(new ByteArrayInputStream(Files.readAllBytes(p))); }
            catch (Throwable t) { continue; }
            String name = cf.getClassName();

            String src1, src2;
            try {
                src1 = ClassDecompiler.decompile(cf);
                recompileMethods(cf, pool, src1);
                src2 = ClassDecompiler.decompile(cf);
            } catch (Throwable t) { continue; }

            for (String method : methodNames(src1)) {
                if (methodBody(src2, method).contains("$dispatch$")
                        && !methodBody(src1, method).contains("$dispatch$")) {
                    drifts.add(name + "#" + method);
                }
            }
        }

        assertTrue(drifts.isEmpty(),
            "methods drifted into a $pc$ dispatch loop on decompile->recompile->decompile: " + drifts);
    }

    private static void recompileMethods(ClassFile original, ClassPool pool, String source) {
        CompilationUnit cu = JavaParser.create().parse(source);
        if (!(cu.getPrimaryType() instanceof ClassDecl)) {
            return;
        }
        ClassDecl decl = (ClassDecl) cu.getPrimaryType();
        String owner = original.getClassName();
        TypeResolver resolver = new TypeResolver(pool, owner);
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(decl);
        ASTLowerer lowerer = new ASTLowerer(original.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(original.getConstPool());
        for (MethodDecl md : decl.getMethods()) {
            if (md.getBody() == null) {
                continue;
            }
            StringBuilder d = new StringBuilder("(");
            for (ParameterDecl pp : md.getParameters()) {
                d.append(resolver.descriptorOf(pp.getType()));
            }
            d.append(")").append(resolver.descriptorOf(md.getReturnType()));
            MethodEntry target = null;
            for (MethodEntry m : original.getMethods()) {
                if (m.getName().equals(md.getName()) && m.getDesc().contentEquals(d)) { target = m; break; }
            }
            if (target == null) {
                continue;
            }
            try { ssa.lower(lowerer.lower(md, owner), target); } catch (Throwable ignored) {}
        }
        try { original.rebuild(); } catch (Throwable ignored) {}
    }

    private static java.util.Set<String> methodNames(String src) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        Matcher mm = Pattern.compile(
            "(?:private|public|protected)\\s+(?:static\\s+)?[\\w<>\\[\\],.\\s]*?\\b(\\w+)\\s*\\(").matcher(src);
        while (mm.find()) {
            names.add(mm.group(1));
        }
        return names;
    }

    private static String methodBody(String src, String name) {
        String[] lines = src.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean in = false;
        int depth = 0;
        for (String l : lines) {
            if (!in && Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(").matcher(l).find()
                    && (l.contains("static") || l.contains("private") || l.contains("public"))) {
                in = true;
            }
            if (in) {
                sb.append(l).append("\n");
                depth += (int) (l.chars().filter(c -> c == '{').count() - l.chars().filter(c -> c == '}').count());
                if (depth <= 0 && l.contains("}")) {
                    break;
                }
            }
        }
        return sb.toString();
    }
}
