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
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
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

public class StringConcatDebugTest {

    private JavaParser parser;
    private ClassPool pool;

    @BeforeEach
    void setUp() {
        parser = JavaParser.create();
        pool = ClassPool.getDefault();
        TestUtils.resetSSACounters();
    }

    @Test
    void debugStringConcat() throws Exception {
        String source =
            "package test;\n" +
            "\n" +
            "public class SimpleConcat {\n" +
            "    public static void log(String msg) {\n" +
            "        System.out.println(\"[INFO] \" + msg);\n" +
            "    }\n" +
            "}\n";

        CompilationUnit cu = parser.parse(source);
        ClassDecl classDecl = (ClassDecl) cu.getTypes().get(0);

        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("test/SimpleConcat", classAccess);

        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(classDecl);

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
            if (methodDecl.isStatic()) methodAccess |= AccessFlags.ACC_STATIC;

            final int finalMethodAccess = methodAccess;
            MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals(methodName) && m.getDesc().equals(descriptor))
                .findFirst()
                .orElseGet(() -> cf.createNewMethodWithDescriptor(finalMethodAccess, methodName, descriptor));

            System.out.println("=== Lowering method: " + methodName + " ===");
            IRMethod ir = lowerer.lower(methodDecl, "test/SimpleConcat");

            System.out.println("\n=== IR Instructions ===");
            for (IRBlock block : ir.getBlocks()) {
                System.out.println("Block " + block.getId() + ":");
                for (IRInstruction instr : block.getInstructions()) {
                    System.out.println("  " + instr);
                }
            }

            System.out.println("\n=== Lowering to bytecode ===");
            SSA ssa = new SSA(cf.getConstPool());
            ssa.lower(ir, method);

            System.out.println("Bytecode generated, length: " +
                (method.getCodeAttribute() != null ? method.getCodeAttribute().getCode().length : 0));

            if (method.getCodeAttribute() != null) {
                byte[] code = method.getCodeAttribute().getCode();
                System.out.println("\n=== Bytecode hex ===");
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < code.length; i++) {
                    hex.append(String.format("%02x ", code[i] & 0xff));
                    if ((i + 1) % 16 == 0) hex.append("\n");
                }
                System.out.println(hex);
            }

            System.out.println("\n=== Lifting bytecode back to IR ===");
            com.tonic.analysis.ssa.lift.BytecodeLifter lifter = new com.tonic.analysis.ssa.lift.BytecodeLifter(cf.getConstPool());
            IRMethod liftedIr = lifter.lift(method);
            for (IRBlock liftedBlock : liftedIr.getBlocks()) {
                System.out.println("Lifted Block " + liftedBlock.getId() + ":");
                for (IRInstruction liftedInstr : liftedBlock.getInstructions()) {
                    System.out.println("  " + liftedInstr);
                }
            }
        }

        System.out.println("\n=== Decompiling ===");
        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String decompiled = decompiler.decompile();
        System.out.println(decompiled);
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
