package com.tonic.analysis.source.lower;

import com.tonic.analysis.CodePrinter;
import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.ConstructorDecl;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decompiler strips a constructor's implicit no-arg {@code super()}, so an empty/edited constructor
 * body has no chain call. Re-lowering it must still produce a verifiable {@code <init>}: ASTLowerer
 * synthesizes the {@code super(...)} call (resolved to the real superclass).
 */
class ConstructorLoweringTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void emptyConstructorBodyLowersToVerifiableInit() throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        int pub = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("test/CtorB", pub);

        CompilationUnit cu = JavaParser.create().parse(
            "package test; public class CtorB { public CtorB() { } }");
        ClassDecl decl = (ClassDecl) cu.getPrimaryType();

        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());

        assert decl != null;
        var constructors = decl.getConstructors();
        assertNotNull(constructors);
        assertFalse(constructors.isEmpty(), "parsed class should declare a constructor");
        ConstructorDecl ctor = constructors.get(0);
        MethodDecl init = new MethodDecl("<init>", VoidSourceType.INSTANCE).withModifiers(ctor.getModifiers());
        for (ParameterDecl p : ctor.getParameters()) {
            init.addParameter(p);
        }
        init.withBody(ctor.getBody());

        MethodEntry target = cf.getMethods().stream()
            .filter(m -> m.getName().equals("<init>") && m.getDesc().equals("()V"))
            .findFirst()
            .orElseGet(() -> cf.createNewMethodWithDescriptor(pub, "<init>", "()V"));

        IRMethod ir = lowerer.lower(init, "test/CtorB");
        new SSA(cf.getConstPool()).lower(ir, target);
        cf.rebuild();

        // linkAndVerify throws if <init> never invokes a super constructor; the synthesized super()
        // makes the re-lowered constructor verifiable.
        TestUtils.linkAndVerify(cf);
    }

    /**
     * A synthetic outer-instance field ({@code this$0}) assignment must be emitted BEFORE the
     * synthesized {@code super()} - matching javac, which sets captured/enclosing-instance fields
     * before the super call. The decompiler drops the implicit super(), so lowering re-injects it,
     * and it must land after the leading synthetic-capture inits, not at index 0.
     */
    @Test
    void syntheticOuterFieldInitPrecedesSuper() throws Exception {
        String asm = lowerCtorAndDisassemble(
                "package test; public class Cap { java.lang.Object this$0;"
                        + " public Cap(java.lang.Object outer) { this.this$0 = outer; } }",
                "test/Cap", "this$0", "Ljava/lang/Object;", "(Ljava/lang/Object;)V");
        int put = asm.indexOf("putfield");
        int sup = asm.indexOf("invokespecial");
        assertTrue(put >= 0 && sup >= 0 && put < sup,
                "synthetic this$0 init must precede super():\n" + asm);
    }

    /** Regression guard: an ORDINARY field assignment must still follow super() (super emitted first). */
    @Test
    void normalFieldInitFollowsSuper() throws Exception {
        String asm = lowerCtorAndDisassemble(
                "package test; public class Norm { int x; public Norm(int x) { this.x = x; } }",
                "test/Norm", "x", "I", "(I)V");
        int put = asm.indexOf("putfield");
        int sup = asm.indexOf("invokespecial");
        assertTrue(put >= 0 && sup >= 0 && sup < put,
                "a normal field init must follow super():\n" + asm);
    }

    private String lowerCtorAndDisassemble(String source, String internalName, String fieldName,
                                           String fieldDesc, String ctorDesc) throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        int pub = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass(internalName, pub);
        cf.createNewField(pub, fieldName, fieldDesc, new ArrayList<>());

        CompilationUnit cu = JavaParser.create().parse(source);
        ClassDecl decl = (ClassDecl) cu.getPrimaryType();
        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());

        ConstructorDecl ctor = decl.getConstructors().get(0);
        MethodDecl init = new MethodDecl("<init>", VoidSourceType.INSTANCE).withModifiers(ctor.getModifiers());
        for (ParameterDecl p : ctor.getParameters()) {
            init.addParameter(p);
        }
        init.withBody(ctor.getBody());

        MethodEntry target = cf.createNewMethodWithDescriptor(pub, "<init>", ctorDesc);
        IRMethod ir = lowerer.lower(init, internalName);
        new SSA(cf.getConstPool()).lower(ir, target);
        cf.rebuild();
        return CodePrinter.prettyPrintCode(target.getCodeAttribute());
    }
}
