package com.tonic.demo;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.ClassFileUtil;
import com.tonic.utill.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demonstrates the AST mutation capability:
 * 1. Load a class file
 * 2. Lift bytecode to IR
 * 3. Recover AST from IR
 * 4. Mutate the AST
 * 5. Lower AST back to IR
 * 6. Lower IR to bytecode
 * 7. Export modified class file
 * 8. Run the modified class and verify behavior
 */
public class ASTMutationDemo {

    private static final String OUTPUT_DIR = "C:\\test\\mutation";

    public static void main(String[] args) throws Exception {
        Logger.setLog(false);

        System.out.println("===========================================");
        System.out.println("      AST Mutation Demo");
        System.out.println("===========================================");
        System.out.println();

        // Load the SSAShowcase class
        ClassPool classPool = ClassPool.getDefault();

        try (InputStream is = ASTMutationDemo.class.getResourceAsStream("SSAShowcase.class")) {
            if (is == null) {
                throw new IOException("Resource 'SSAShowcase.class' not found.");
            }

            ClassFile classFile = classPool.loadClass(is);
            ConstPool constPool = classFile.getConstPool();

            System.out.println("Loaded class: " + classFile.getClassName());
            System.out.println();

            // Find a simple method to mutate
            MethodEntry targetMethod = null;
            for (MethodEntry method : classFile.getMethods()) {
                if (method.getName().equals("constantFolding")) {
                    targetMethod = method;
                    break;
                }
            }

            if (targetMethod == null) {
                System.err.println("Could not find 'constantFolding' method");
                return;
            }

            // Run the full mutation pipeline
            demonstrateMutation(targetMethod, constPool, classFile);
        }
    }

    private static void demonstrateMutation(MethodEntry method, ConstPool constPool, ClassFile classFile) throws Exception {
        System.out.println("--- Method: " + method.getName() + method.getDesc() + " ---");
        System.out.println();

        // Step 1: Lift bytecode to SSA-form IR
        System.out.println("Step 1: Lift bytecode to IR");
        SSA ssa = new SSA(constPool);
        IRMethod irMethod = ssa.lift(method);
        System.out.println("  Blocks: " + irMethod.getBlocks().size());
        System.out.println();

        // Step 2: Recover AST from IR
        System.out.println("Step 2: Recover AST from IR");
        MethodRecoverer recoverer = new MethodRecoverer(irMethod, method);
        BlockStmt originalAst = recoverer.recover();
        String originalSource = SourceEmitter.emit(originalAst);
        System.out.println("  Original AST:");
        printIndented(originalSource, "    ");
        System.out.println();

        // Step 3: Mutate the AST
        System.out.println("Step 3: Mutate the AST");
        BlockStmt mutatedAst = mutateAst(originalAst);
        String mutatedSource = SourceEmitter.emit(mutatedAst);
        System.out.println("  Mutated AST:");
        printIndented(mutatedSource, "    ");
        System.out.println();

        // Step 4: Lower AST back to IR
        System.out.println("Step 4: Lower AST back to IR");
        ASTLowerer astLowerer = new ASTLowerer(constPool);
        astLowerer.lower(mutatedAst, irMethod, method);
        System.out.println("  New IR blocks: " + irMethod.getBlocks().size());
        System.out.println();

        // Step 5: Lower IR to bytecode
        System.out.println("Step 5: Lower IR to bytecode");
        ssa.lower(irMethod, method);
        CodeWriter cw = new CodeWriter(method);
        System.out.println("  Bytecode size: " + cw.getBytecode().length + " bytes");
        System.out.println();

        // Step 6: Export modified class
        System.out.println("Step 6: Export modified class file");
        String newClassName = "SSAShowcase_Mutated";
        classFile.setClassName(newClassName);
        classFile.computeFrames();
        classFile.rebuild();

        // Ensure output directory exists
        Path outputPath = Path.of(OUTPUT_DIR);
        Files.createDirectories(outputPath);

        ClassFileUtil.saveClassFile(classFile.write(), OUTPUT_DIR, newClassName);
        System.out.println("  Saved to: " + OUTPUT_DIR + "\\" + newClassName + ".class");
        System.out.println();

        // Step 7: Load and run the modified class
        System.out.println("Step 7: Run the modified class");
        runModifiedClass(newClassName);
    }

    /**
     * Mutates the AST by finding all integer literals with value 10 and changing to 100.
     * This demonstrates modifying expressions deep within the AST.
     */
    private static BlockStmt mutateAst(BlockStmt ast) {
        // Traverse all statements and mutate expressions
        for (Statement stmt : ast.getStatements()) {
            mutateStatement(stmt);
        }
        return ast;
    }

    private static void mutateStatement(Statement stmt) {
        if (stmt instanceof ReturnStmt returnStmt) {
            if (returnStmt.getValue() != null) {
                mutateExpression(returnStmt.getValue());
            }
        } else if (stmt instanceof VarDeclStmt varDecl) {
            if (varDecl.getInitializer() != null) {
                mutateExpression(varDecl.getInitializer());
            }
        } else if (stmt instanceof ExprStmt exprStmt) {
            mutateExpression(exprStmt.getExpression());
        } else if (stmt instanceof BlockStmt block) {
            for (Statement s : block.getStatements()) {
                mutateStatement(s);
            }
        } else if (stmt instanceof IfStmt ifStmt) {
            mutateExpression(ifStmt.getCondition());
            mutateStatement(ifStmt.getThenBranch());
            if (ifStmt.getElseBranch() != null) {
                mutateStatement(ifStmt.getElseBranch());
            }
        } else if (stmt instanceof WhileStmt whileStmt) {
            mutateExpression(whileStmt.getCondition());
            mutateStatement(whileStmt.getBody());
        } else if (stmt instanceof ForStmt forStmt) {
            for (Statement init : forStmt.getInit()) {
                mutateStatement(init);
            }
            if (forStmt.getCondition() != null) {
                mutateExpression(forStmt.getCondition());
            }
            for (Expression update : forStmt.getUpdate()) {
                mutateExpression(update);
            }
            mutateStatement(forStmt.getBody());
        }
    }

    private static void mutateExpression(Expression expr) {
        if (expr instanceof LiteralExpr literal) {
            Object value = literal.getValue();
            if (value instanceof Integer intVal && intVal == 10) {
                // Mutate: change 10 to 100
                System.out.println("  Mutation: literal 10 -> 100");
                literal.setValue(100);
            }
        } else if (expr instanceof BinaryExpr binary) {
            mutateExpression(binary.getLeft());
            mutateExpression(binary.getRight());
        } else if (expr instanceof UnaryExpr unary) {
            mutateExpression(unary.getOperand());
        } else if (expr instanceof CastExpr cast) {
            mutateExpression(cast.getExpression());
        } else if (expr instanceof TernaryExpr ternary) {
            mutateExpression(ternary.getCondition());
            mutateExpression(ternary.getThenExpr());
            mutateExpression(ternary.getElseExpr());
        } else if (expr instanceof MethodCallExpr call) {
            for (Expression arg : call.getArguments()) {
                mutateExpression(arg);
            }
        } else if (expr instanceof ArrayAccessExpr arrayAccess) {
            mutateExpression(arrayAccess.getArray());
            mutateExpression(arrayAccess.getIndex());
        }
    }

    private static void runModifiedClass(String className) {
        try {
            // Create a URL class loader to load the modified class
            Path classDir = Path.of(OUTPUT_DIR);
            URL[] urls = {classDir.toUri().toURL()};
            URLClassLoader classLoader = new URLClassLoader(urls, null);

            // Load the modified class
            Class<?> modifiedClass = classLoader.loadClass(className);
            System.out.println("  Loaded class: " + modifiedClass.getName());

            // Create an instance
            Object instance = modifiedClass.getDeclaredConstructor().newInstance();

            // Find and invoke the constantFolding method
            Method targetMethod = modifiedClass.getMethod("constantFolding");
            Object result = targetMethod.invoke(instance);
            System.out.println("  Result of constantFolding(): " + result);

            // Verify the mutation worked
            if (result instanceof Integer intResult) {
                // Original: (10 + 20) * 2 - 10 = 50
                // After mutation (10->100): (100 + 20) * 2 - 100 = 140
                System.out.println();
                if (intResult == 140) {
                    System.out.println("  SUCCESS: Mutation verified!");
                    System.out.println("  Original: (10 + 20) * 2 - 10 = 50");
                    System.out.println("  Mutated:  (100 + 20) * 2 - 100 = 140");
                } else if (intResult == 50) {
                    System.out.println("  FAILED: Mutation did not take effect, original value returned");
                } else {
                    System.out.println("  Result: " + intResult);
                }
            }

            classLoader.close();
        } catch (Exception e) {
            System.err.println("  Error running modified class: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printIndented(String text, String indent) {
        for (String line : text.split("\n")) {
            System.out.println(indent + line);
        }
    }
}
