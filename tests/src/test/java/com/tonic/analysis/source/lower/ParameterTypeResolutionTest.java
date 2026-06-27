package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a method parameter whose type is a simple name (e.g. wildcard-imported {@code java.awt.Frame}) must
 * have its IR/SSA type resolved to the FQN, so the StackMapTable frame for that local references {@code java/awt/Frame}
 * rather than a bare {@code Frame} CONSTANT_Class - which fails to link with {@code ClassNotFoundException: Frame}.
 * {@code ASTLowerer} previously used the raw {@code SourceType.toIRType()} for parameter types and the descriptor.
 */
public class ParameterTypeResolutionTest {

    @Test
    void wildcardImportedParamTypeResolvedInIr() throws Exception {
        ClassPool pool = new ClassPool();
        ClassFile cf = pool.createNewClass("test/User", 0x0001);

        CompilationUnit cu = JavaParser.create().parse(
            "package test;\nimport java.awt.*;\npublic class User { public void m(Frame f) {} }\n");
        ClassDecl decl = (ClassDecl) cu.getTypes().get(0);
        MethodDecl m = decl.getMethods().stream()
            .filter(x -> x.getName().equals("m")).findFirst().orElseThrow();

        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());
        IRMethod ir = lowerer.lower(m, "test/User");

        // parameters[0] = this, parameters[1] = f
        SSAValue f = ir.getParameters().get(1);
        assertTrue(f.getType() instanceof ReferenceType, "param type should be a reference, was " + f.getType());
        assertEquals("java/awt/Frame", ((ReferenceType) f.getType()).getInternalName(),
            "a wildcard-imported parameter type must resolve to its FQN in the IR");
    }
}
