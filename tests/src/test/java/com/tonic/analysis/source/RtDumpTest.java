package com.tonic.analysis.source;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.ConstructorDecl;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.type.VoidSourceType;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

class RtDumpTest {
    private static final String DIR = "C:/Users/zacke/IdeaProjects/DemoApplication/build/classes/java/main";

    @Test
    void dump() throws Exception {
        for (String target : new String[]{"SymbolicExecutionTests", "Main", "HeapAnalysisTest", "AuthenticationCoordinator"}) {
            Path root = Path.of(DIR);
            if (!Files.exists(root)) return;
            ClassPool pool = new ClassPool();
            ClassFile cf = null;
            try (Stream<Path> s = Files.walk(root)) {
                for (Path q : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".class")).sorted()::iterator) {
                    ClassFile c = pool.loadClass(new ByteArrayInputStream(Files.readAllBytes(q)));
                    if (c.getClassName().endsWith(target) && !c.getClassName().contains("$")) cf = c;
                }
            }
            if (cf == null) continue;
            String owner = cf.getClassName();
            Path out = Path.of(System.getProperty("java.io.tmpdir"), "rt");
            Files.createDirectories(out);
            try {
                String d1 = ClassDecompiler.decompile(cf);
                recompile(cf, pool, d1, owner);
                String d2 = ClassDecompiler.decompile(cf);
                recompile(cf, pool, d2, owner);
                String d3 = ClassDecompiler.decompile(cf);
                Files.writeString(out.resolve(target + "_d2.java"), d2);
                Files.writeString(out.resolve(target + "_d3.java"), d3);
                System.out.println("RT " + target + " d1==d2=" + d1.equals(d2) + " d2==d3=" + d2.equals(d3));
            } catch (Exception e) {
                System.out.println("RT " + target + " FAILED: " + e);
            }
        }
    }

    private static void recompile(ClassFile cf, ClassPool pool, String source, String owner) throws Exception {
        CompilationUnit cu = JavaParser.create().parse(source);
        ClassDecl decl = (ClassDecl) cu.getPrimaryType();
        TypeResolver resolver = new TypeResolver(pool, owner);
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(decl);
        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(cf.getConstPool());
        for (MethodDecl md : decl.getMethods()) {
            if (md.getBody() == null) continue;
            String d = desc(md.getParameters(), resolver.descriptorOf(md.getReturnType()), resolver);
            MethodEntry t = find(cf, md.getName(), d);
            if (t != null) ssa.lower(lowerer.lower(md, owner), t);
        }
        for (ConstructorDecl ctor : decl.getConstructors()) {
            if (ctor.getBody() == null) continue;
            String d = desc(ctor.getParameters(), "V", resolver);
            MethodEntry t = find(cf, "<init>", d);
            if (t == null) continue;
            MethodDecl init = new MethodDecl("<init>", VoidSourceType.INSTANCE).withModifiers(ctor.getModifiers());
            for (ParameterDecl pp : ctor.getParameters()) init.addParameter(pp);
            init.withBody(ctor.getBody());
            ssa.lower(lowerer.lower(init, owner), t);
        }
        cf.rebuild();
    }

    private static String desc(List<ParameterDecl> params, String ret, TypeResolver resolver) {
        StringBuilder d = new StringBuilder("(");
        for (ParameterDecl pp : params) d.append(resolver.descriptorOf(pp.getType()));
        return d.append(")").append(ret).toString();
    }

    private static MethodEntry find(ClassFile cf, String name, String desc) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name) && m.getDesc().contentEquals(desc)) return m;
        }
        return null;
    }
}
