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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Dumps d1 and d2 for every demo class to a directory for manual inspection. Not an assertion - a tool. */
class RoundTripDumpTest {

    private static final String DIR = "C:/Users/zacke/IdeaProjects/DemoApplication/build/classes/java/main";

    @Test
    void dumpRoundTrip() throws Exception {
        Path root = Path.of(DIR);
        if (!Files.exists(root)) {
            return;
        }
        List<Path> classes;
        try (Stream<Path> s = Files.walk(root)) {
            classes = s.filter(p -> p.toString().endsWith(".class")).sorted().collect(Collectors.toList());
        }
        ClassPool pool = new ClassPool();
        List<ClassFile> cfs = new ArrayList<>();
        for (Path p : classes) {
            cfs.add(pool.loadClass(new ByteArrayInputStream(Files.readAllBytes(p))));
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "roundtrip");
        Files.createDirectories(out);
        for (ClassFile cf : cfs) {
            String name = cf.getClassName().replace('/', '.');
            String d1 = ClassDecompiler.decompile(cf);
            Files.writeString(out.resolve(name + "_d1.java"), d1);
            try {
                if (recompile(cf, pool, d1, cf.getClassName())) {
                    String d2 = ClassDecompiler.decompile(cf);
                    Files.writeString(out.resolve(name + "_d2.java"), d2);
                }
            } catch (Throwable t) {
                Files.writeString(out.resolve(name + "_d2.java"), "RECOMPILE THREW: " + t);
            }
        }
        System.out.println("dumped to " + out);
    }

    private static boolean recompile(ClassFile cf, ClassPool pool, String source, String owner) throws Exception {
        if (source.contains("@interface ")) {
            return false;
        }
        CompilationUnit cu = JavaParser.create().parse(source);
        if (!(cu.getPrimaryType() instanceof ClassDecl)) {
            return false;
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
            if (md.getBody() == null) {
                continue;
            }
            String d = desc(md.getParameters(), resolver.descriptorOf(md.getReturnType()), resolver);
            MethodEntry target = find(cf, md.getName(), d);
            if (target != null) {
                ssa.lower(lowerer.lower(md, owner), target);
            }
        }
        for (ConstructorDecl ctor : decl.getConstructors()) {
            if (ctor.getBody() == null) {
                continue;
            }
            String d = desc(ctor.getParameters(), "V", resolver);
            MethodEntry target = find(cf, "<init>", d);
            if (target == null) {
                continue;
            }
            MethodDecl init = new MethodDecl("<init>", VoidSourceType.INSTANCE).withModifiers(ctor.getModifiers());
            for (ParameterDecl pp : ctor.getParameters()) {
                init.addParameter(pp);
            }
            init.withBody(ctor.getBody());
            ssa.lower(lowerer.lower(init, owner), target);
        }
        cf.rebuild();
        return true;
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
