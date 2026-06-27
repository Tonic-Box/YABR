package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression: a type referenced by simple name and brought in by a WILDCARD import of a JDK package whose module
 * isn't loaded into the {@link ClassPool} - e.g. {@code java.awt.Frame} via {@code import java.awt.*} - must still
 * resolve to its fully-qualified internal name. Pre-fix the wildcard branch only consulted {@code classPool.get},
 * so {@code Frame} stayed unqualified and the recompiled descriptor became {@code LFrame;} -> ClassNotFoundException.
 */
public class WildcardImportResolutionTest {

    @Test
    void wildcardImportedJdkTypeResolvesToFqn() throws Exception {
        ClassPool pool = new ClassPool();
        CompilationUnit cu = JavaParser.create().parse(
            "package test;\nimport javax.swing.*;\nimport java.awt.*;\n"
                + "public class User { void m(Frame f) {} }\n");
        ClassDecl decl = (ClassDecl) cu.getTypes().get(0);

        TypeResolver resolver = new TypeResolver(pool, "test/User");
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(decl);

        assertEquals("java/awt/Frame", resolver.resolveClassName("Frame"),
            "a java.awt.* wildcard-imported type must resolve to its FQN even when not preloaded in the pool");
    }
}
