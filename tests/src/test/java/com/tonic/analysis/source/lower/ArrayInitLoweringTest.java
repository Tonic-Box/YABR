package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code new T[]{...}} carries an inline initializer and no dimension expression. Lowering must emit the
 * length from the element count, allocate, and store each element - otherwise NEWARRAY gets no count on the
 * stack (a frame-generation "Stack underflow") and the elements are dropped. Regression for the cemented
 * "// Failed to decompile static initializer" on classes with array-valued static fields.
 */
class ArrayInitLoweringTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void newArrayWithInlineInitializerLowersAndVerifies() throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        int pub = new AccessBuilder().setPublic().setStatic().build();
        ClassFile cf = pool.createNewClass("test/ArrInit", new AccessBuilder().setPublic().build());

        CompilationUnit cu = JavaParser.create().parse(
            "package test; public class ArrInit { static int[] make() { return new int[]{0,1,2,3,4,5}; } }");
        ClassDecl decl = (ClassDecl) cu.getPrimaryType();

        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());

        assert decl != null;
        MethodDecl make = decl.getMethods().get(0);
        MethodEntry target = cf.createNewMethodWithDescriptor(pub, "make", "()[I");

        new SSA(cf.getConstPool()).lower(lowerer.lower(make, "test/ArrInit"), target);
        cf.rebuild();

        VerificationResult vr = Verifier.builder().classPool(pool).build().verify(cf);
        assertTrue(vr.getErrors().stream().noneMatch(VerificationError::isError),
            "new int[]{...} must lower to verifiable bytecode: " + vr.getErrors());
    }
}
