package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression: a current-class field's type is taken from the source AST as a bare simple name; findFieldType /
 * resolveFieldType must resolve it to the fully-qualified internal name (imports + same package). Left unresolved, a
 * method call on that field gets a non-FQN owner that misses the FQN-keyed ClassPool, so the call's return type
 * defaults to Object -> invalid bytecode (e.g. {@code ifeq} on a reference -> VerifyError), as seen recompiling a
 * class that calls {@code this.<field>.someBooleanMethod()}.
 */
public class FieldTypeResolutionTest {

    @Test
    void currentClassFieldOfSamePackageTypeResolvesToFqn() throws Exception {
        ClassFile helper = TestUtils.compileSource("package test;\npublic class Helper {}\n", "test/Helper");
        ClassPool pool = new ClassPool();
        pool.loadClass(helper.write());

        CompilationUnit cu = JavaParser.create().parse(
            "package test;\npublic class User {\n    private Helper helper;\n}\n");
        ClassDecl userDecl = (ClassDecl) cu.getTypes().get(0);

        TypeResolver resolver = new TypeResolver(pool, "test/User");
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(userDecl);

        SourceType found = resolver.findFieldType("test/User", "helper");
        SourceType resolved = resolver.resolveFieldType("test/User", "helper");

        // Pre-fix both return ReferenceSourceType("Helper"); the non-FQN owner then resolves method calls to Object.
        assertEquals("test/Helper", ((ReferenceSourceType) found).getInternalName(),
            "findFieldType must resolve a current-class field's same-package type to its FQN");
        assertEquals("test/Helper", ((ReferenceSourceType) resolved).getInternalName(),
            "resolveFieldType must resolve a current-class field's same-package type to its FQN");
    }
}
