package com.tonic.analysis.source;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.type.SourceType;
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
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompileAndRunTest {

    private JavaParser parser;
    private ClassPool pool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = JavaParser.create();
        pool = ClassPool.getDefault();
        TestUtils.resetSSACounters();
    }

    @Test
    void compileAndRunWithImports() throws Exception {
        String source =
            "package test;\n" +
            "\n" +
            "import java.time.LocalDateTime;\n" +
            "\n" +
            "public class Logger {\n" +
            "    public static void main(String[] args) {\n" +
            "        System.out.println(\"[\" + LocalDateTime.now() + \"] Hello\");\n" +
            "    }\n" +
            "}\n";

        ClassFile cf = compileSource(source, "test/Logger");

        Path testPackage = tempDir.resolve("test");
        Files.createDirectories(testPackage);
        Path classFilePath = testPackage.resolve("Logger.class");
        byte[] classBytes = cf.write();
        Files.write(classFilePath, classBytes);

        System.out.println("Wrote class file to: " + classFilePath);
        System.out.println("Class file size: " + classBytes.length + " bytes");

        ProcessBuilder pb = new ProcessBuilder(
            "java", "-cp", tempDir.toString(), "test.Logger"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        System.out.println("Exit code: " + exitCode);
        System.out.println("Output:\n" + output);

        assertEquals(0, exitCode, "Process should exit successfully. Output: " + output);
        assertTrue(output.contains("Hello"), "Output should contain message");
    }

    @Test
    void compileAndRunSimpleConcat() throws Exception {
        String source =
            "package test;\n" +
            "\n" +
            "public class Simple {\n" +
            "    public static void main(String[] args) {\n" +
            "        String msg = \"Hello\";\n" +
            "        System.out.println(\"[INFO] \" + msg);\n" +
            "    }\n" +
            "}\n";

        ClassFile cf = compileSource(source, "test/Simple");

        Path testPackage = tempDir.resolve("test");
        Files.createDirectories(testPackage);
        Path classFilePath = testPackage.resolve("Simple.class");
        byte[] classBytes = cf.write();
        Files.write(classFilePath, classBytes);

        ProcessBuilder pb = new ProcessBuilder(
            "java", "-cp", tempDir.toString(), "test.Simple"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        System.out.println("Exit code: " + exitCode);
        System.out.println("Output:\n" + output);

        assertEquals(0, exitCode, "Process should exit successfully. Output: " + output);
        assertTrue(output.contains("[INFO] Hello"), "Output should contain message");
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
