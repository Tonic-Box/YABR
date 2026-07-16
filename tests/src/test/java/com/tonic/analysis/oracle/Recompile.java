package com.tonic.analysis.oracle;

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

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * Internal recompile helper for the recovery-equivalence oracle: the decompile -> parse -> lower
 * round-trip, composed from the existing public source APIs. Kept package-private in the test tree
 * (not exposed as a public API).
 */
final class Recompile {

    private Recompile() {
    }

    /**
     * Produces an independent {@link ClassFile} whose method bodies are the result of decompiling
     * {@code cf} and recompiling that source. The original is left untouched. Returns {@code null}
     * when the class is not recompilable (non-plain-class primary type).
     */
    static ClassFile recompiledClone(ClassFile cf, ClassPool pool) throws Exception {
        String owner = cf.getClassName();
        String source = ClassDecompiler.decompile(cf);
        if (source.contains("@interface ")) {
            return null;
        }
        ClassFile clone = new ClassFile(new ByteArrayInputStream(cf.write()));
        CompilationUnit cu = JavaParser.create().parse(source);
        if (!(cu.getPrimaryType() instanceof ClassDecl)) {
            return null;
        }
        ClassDecl decl = (ClassDecl) cu.getPrimaryType();
        TypeResolver resolver = new TypeResolver(pool, owner);
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(decl);
        ASTLowerer lowerer = new ASTLowerer(clone.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(clone.getConstPool());

        for (MethodDecl md : decl.getMethods()) {
            if (md.getBody() == null) {
                continue;
            }
            String d = desc(md.getParameters(), resolver.descriptorOf(md.getReturnType()), resolver);
            MethodEntry target = find(clone, md.getName(), d);
            if (target != null) {
                ssa.lower(lowerer.lower(md, owner), target);
            }
        }
        for (ConstructorDecl ctor : decl.getConstructors()) {
            if (ctor.getBody() == null) {
                continue;
            }
            String d = desc(ctor.getParameters(), "V", resolver);
            MethodEntry target = find(clone, "<init>", d);
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
        clone.rebuild();
        return clone;
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
