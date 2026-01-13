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
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import com.tonic.type.AccessFlags;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RoundTripTypeTest {

    private JavaParser parser;
    private ClassPool pool;

    @BeforeEach
    void setUp() {
        parser = JavaParser.create();
        pool = ClassPool.getDefault();
        TestUtils.resetSSACounters();
    }

    @Test
    void stringConcatRoundTrip() throws Exception {
        String source =
            "package test;\n" +
            "\n" +
            "public class StringConcat {\n" +
            "    public static void log(String msg) {\n" +
            "        System.out.println(\"[INFO] \" + msg);\n" +
            "    }\n" +
            "}\n";

        String decompiled = compileAndDecompile(source, "test/StringConcat");
        System.out.println("Decompiled:\n" + decompiled);

        assertFalse(decompiled.contains("Object local"), "Should not have Object typed locals for string concat");
        assertTrue(decompiled.contains("String") || decompiled.contains("println"), "Should have proper types");
    }

    @Test
    void stringConcatWithIntRoundTrip() throws Exception {
        String source =
            "package test;\n" +
            "\n" +
            "public class StringInt {\n" +
            "    public static void print(int num) {\n" +
            "        System.out.println(\"Number: \" + num);\n" +
            "    }\n" +
            "}\n";

        String decompiled = compileAndDecompile(source, "test/StringInt");
        System.out.println("Decompiled:\n" + decompiled);

        assertFalse(decompiled.contains("Object local"), "Should not have Object typed locals");
    }

    @Test
    void chainedMethodCallsRoundTrip() throws Exception {
        String source =
            "package test;\n" +
            "\n" +
            "public class ChainedCalls {\n" +
            "    public static String format(String s) {\n" +
            "        return s.trim().toLowerCase();\n" +
            "    }\n" +
            "}\n";

        String decompiled = compileAndDecompile(source, "test/ChainedCalls");
        System.out.println("Decompiled:\n" + decompiled);

        assertFalse(decompiled.contains("Object local"), "Chained method calls should preserve types");
    }

    @Test
    void stringBuilderPatternInBytecode() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("test/SBPattern")
            .publicStaticMethod("concat", "(Ljava/lang/String;I)Ljava/lang/String;")
                .new_("java/lang/StringBuilder")
                .dup()
                .invokespecial("java/lang/StringBuilder", "<init>", "()V")
                .astore(2)
                .aload(2)
                .ldc("Prefix: ")
                .invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
                .pop()
                .aload(2)
                .aload(0)
                .invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
                .pop()
                .aload(2)
                .ldc(" - ")
                .invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
                .pop()
                .aload(2)
                .iload(1)
                .invokevirtual("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;")
                .pop()
                .aload(2)
                .invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
                .areturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String decompiled = decompiler.decompile();
        System.out.println("StringBuilder pattern decompiled:\n" + decompiled);

        assertFalse(decompiled.contains("Object local"),
            "StringBuilder pattern should not produce Object-typed locals");
    }

    @Test
    void multipleStringConcatsInMethod() throws Exception {
        String source =
            "package test;\n" +
            "\n" +
            "public class MultiConcat {\n" +
            "    public static void logBoth(String a, String b) {\n" +
            "        System.out.println(\"First: \" + a);\n" +
            "        System.out.println(\"Second: \" + b);\n" +
            "    }\n" +
            "}\n";

        String decompiled = compileAndDecompile(source, "test/MultiConcat");
        System.out.println("Decompiled:\n" + decompiled);

        int objectCount = countOccurrences(decompiled, "Object local");
        assertEquals(0, objectCount, "Should have 0 Object-typed locals, found " + objectCount);
    }

    private String compileAndDecompile(String source, String className) throws Exception {
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

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        return decompiler.decompile();
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

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}
