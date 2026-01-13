package com.tonic.analysis.source;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.type.AccessFlags;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RoundTripTypeTest {

    private JavaParser parser;
    private ClassPool pool;
    private static int testCounter = 0;

    @BeforeEach
    void setUp() {
        parser = JavaParser.create();
        pool = ClassPool.getDefault();
        TestUtils.resetSSACounters();
        testCounter++;
    }

    @Test
    void methodChainPreservesTypes() throws Exception {
        String className = "RoundTripLogger" + testCounter;
        String source =
            "package test;\n" +
            "\n" +
            "import java.time.LocalDateTime;\n" +
            "import java.time.format.DateTimeFormatter;\n" +
            "\n" +
            "public class " + className + " {\n" +
            "    public void log(String msg) {\n" +
            "        System.out.println(\"[\" + LocalDateTime.now().format(DateTimeFormatter.ofPattern(\"yyyy-MM-dd\")) + \"] \" + msg);\n" +
            "    }\n" +
            "}\n";

        ClassFile cf = compileSource(source, "test/" + className);

        String decompiled = ClassDecompiler.decompile(cf);

        System.out.println("=== Round-trip decompiled ===");
        System.out.println(decompiled);

        assertFalse(decompiled.contains("Object local"), "Should not have Object-typed locals");
        assertTrue(decompiled.contains("LocalDateTime.now()"), "Should have LocalDateTime.now() call");
        assertTrue(decompiled.contains(".format("), "Should have format() call");
        assertTrue(decompiled.contains("DateTimeFormatter.ofPattern"), "Should have DateTimeFormatter.ofPattern call");
    }

    @Test
    void stringConcatPreservesTypes() throws Exception {
        String className = "RoundTripConcat" + testCounter;
        String source =
            "package test;\n" +
            "\n" +
            "public class " + className + " {\n" +
            "    public String concat(String a, int b) {\n" +
            "        return \"prefix_\" + a + \"_\" + b + \"_suffix\";\n" +
            "    }\n" +
            "}\n";

        ClassFile cf = compileSource(source, "test/" + className);
        String decompiled = ClassDecompiler.decompile(cf);

        System.out.println("=== Round-trip decompiled ===");
        System.out.println(decompiled);

        assertFalse(decompiled.contains("StringBuilder"), "Should use invokedynamic not StringBuilder");
        assertFalse(decompiled.contains("Object local"), "Should not have Object-typed locals");
    }

    private ClassFile compileSource(String source, String className) throws Exception {
        CompilationUnit cu = parser.parse(source);
        assertNotNull(cu);

        ClassDecl classDecl = (ClassDecl) cu.getTypes().get(0);

        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass(className, classAccess);

        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(classDecl);
        lowerer.setImports(cu.getImports());

        for (MethodDecl methodDecl : classDecl.getMethods()) {
            if (methodDecl.getBody() == null) {
                continue;
            }

            List<SourceType> params = new ArrayList<>();
            for (ParameterDecl p : methodDecl.getParameters()) {
                params.add(p.getType());
            }
            SourceType returnType = methodDecl.getReturnType();
            String methodName = methodDecl.getName();

            String descriptor = buildDescriptor(params, returnType);
            int methodAccess = 0;
            if (methodDecl.isPublic()) methodAccess |= AccessFlags.ACC_PUBLIC;
            if (methodDecl.isPrivate()) methodAccess |= AccessFlags.ACC_PRIVATE;
            if (methodDecl.isProtected()) methodAccess |= AccessFlags.ACC_PROTECTED;
            if (methodDecl.isStatic()) methodAccess |= AccessFlags.ACC_STATIC;

            final int finalMethodAccess = methodAccess;
            MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals(methodName) && m.getDesc().equals(descriptor))
                .findFirst()
                .orElseGet(() -> cf.createNewMethodWithDescriptor(finalMethodAccess, methodName, descriptor));

            IRMethod ir = lowerer.lower(methodDecl, className);

            SSA ssa = new SSA(cf.getConstPool());
            ssa.lower(ir, method);
        }

        return cf;
    }

    private String buildDescriptor(List<SourceType> params, SourceType returnType) {
        StringBuilder sb = new StringBuilder("(");
        for (SourceType p : params) {
            sb.append(p.toIRType().getDescriptor());
        }
        sb.append(")");
        sb.append(returnType.toIRType().getDescriptor());
        return sb.toString();
    }
}
