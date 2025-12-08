package com.tonic.demo.ast;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.source.emit.SourceEmitterConfig;
import com.tonic.analysis.source.recovery.*;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Demonstrates the Source-Level AST Layer capabilities.
 * Tests AST node construction, source emission, and IR recovery.
 */
public class SourceASTDemo {

    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) {
        Logger.setLog(false);

        System.out.println("================================================================");
        System.out.println("           Source-Level AST Layer Demo                          ");
        System.out.println("================================================================");
        System.out.println();

        // Part 1: Test AST Node Construction and Emission
        testASTConstruction();

        // Part 2: Test Expression Emission
        testExpressionEmission();

        // Part 3: Test Statement Emission
        testStatementEmission();

        // Part 4: Test Type System
        testTypeSystem();

        // Part 5: Test Recovery from IR
        testIRRecovery();

        // Summary
        System.out.println();
        System.out.println("================================================================");
        System.out.println("Results: " + passCount + " passed, " + failCount + " failed");
        System.out.println("================================================================");
    }

    // ==================== Part 1: AST Construction ====================

    private static void testASTConstruction() {
        section("Part 1: AST Node Construction");

        // Test literal expressions
        test("Integer literal", () -> {
            LiteralExpr lit = LiteralExpr.ofInt(42);
            return lit.getValue().equals(42) && lit.getType() == PrimitiveSourceType.INT;
        });

        test("String literal", () -> {
            LiteralExpr lit = LiteralExpr.ofString("hello");
            return lit.getValue().equals("hello");
        });

        test("Boolean literal", () -> {
            LiteralExpr t = LiteralExpr.ofBoolean(true);
            LiteralExpr f = LiteralExpr.ofBoolean(false);
            return t.getValue().equals(true) && f.getValue().equals(false);
        });

        test("Null literal", () -> {
            LiteralExpr n = LiteralExpr.ofNull();
            return n.getValue() == null;
        });

        // Test binary expressions
        test("Binary expression construction", () -> {
            Expression left = LiteralExpr.ofInt(10);
            Expression right = LiteralExpr.ofInt(20);
            BinaryExpr add = new BinaryExpr(BinaryOperator.ADD, left, right, PrimitiveSourceType.INT);
            return add.getOperator() == BinaryOperator.ADD
                    && add.getLeft() == left
                    && add.getRight() == right;
        });

        // Test variable reference
        test("Variable reference", () -> {
            VarRefExpr ref = new VarRefExpr("myVar", PrimitiveSourceType.INT, null);
            return ref.getName().equals("myVar");
        });

        // Test method call
        test("Method call expression", () -> {
            Expression receiver = new VarRefExpr("str", new ReferenceSourceType("java/lang/String", List.of()), null);
            MethodCallExpr call = MethodCallExpr.instanceCall(
                    receiver, "length", "java/lang/String",
                    List.of(), PrimitiveSourceType.INT);
            return call.getMethodName().equals("length") && !call.isStatic();
        });

        // Test new expression
        test("New object expression", () -> {
            NewExpr newExpr = new NewExpr("java/util/ArrayList", List.of());
            return newExpr.getClassName().equals("java/util/ArrayList");
        });
    }

    // ==================== Part 2: Expression Emission ====================

    private static void testExpressionEmission() {
        section("Part 2: Expression Emission");

        test("Emit integer literal", () -> {
            String code = SourceEmitter.emit(LiteralExpr.ofInt(42));
            return code.equals("42");
        });

        test("Emit string literal", () -> {
            String code = SourceEmitter.emit(LiteralExpr.ofString("hello"));
            return code.equals("\"hello\"");
        });

        test("Emit string with escapes", () -> {
            String code = SourceEmitter.emit(LiteralExpr.ofString("line1\nline2\ttab"));
            return code.equals("\"line1\\nline2\\ttab\"");
        });

        test("Emit long literal", () -> {
            String code = SourceEmitter.emit(LiteralExpr.ofLong(123456789L));
            return code.equals("123456789L");
        });

        test("Emit float literal", () -> {
            String code = SourceEmitter.emit(LiteralExpr.ofFloat(3.14f));
            return code.contains("3.14") && code.endsWith("f");
        });

        test("Emit binary add", () -> {
            BinaryExpr add = new BinaryExpr(
                    BinaryOperator.ADD,
                    LiteralExpr.ofInt(1),
                    LiteralExpr.ofInt(2),
                    PrimitiveSourceType.INT);
            String code = SourceEmitter.emit(add);
            return code.equals("1 + 2");
        });

        test("Emit binary multiply", () -> {
            BinaryExpr mul = new BinaryExpr(
                    BinaryOperator.MUL,
                    new VarRefExpr("x", PrimitiveSourceType.INT, null),
                    LiteralExpr.ofInt(3),
                    PrimitiveSourceType.INT);
            String code = SourceEmitter.emit(mul);
            return code.equals("x * 3");
        });

        test("Emit comparison", () -> {
            BinaryExpr cmp = new BinaryExpr(
                    BinaryOperator.LT,
                    new VarRefExpr("i", PrimitiveSourceType.INT, null),
                    new VarRefExpr("n", PrimitiveSourceType.INT, null),
                    PrimitiveSourceType.BOOLEAN);
            String code = SourceEmitter.emit(cmp);
            return code.equals("i < n");
        });

        test("Emit unary negation", () -> {
            UnaryExpr neg = new UnaryExpr(
                    UnaryOperator.NEG,
                    new VarRefExpr("x", PrimitiveSourceType.INT, null),
                    PrimitiveSourceType.INT);
            String code = SourceEmitter.emit(neg);
            return code.equals("-x");
        });

        test("Emit method call", () -> {
            VarRefExpr receiver = new VarRefExpr("str", new ReferenceSourceType("java/lang/String", List.of()), null);
            MethodCallExpr call = MethodCallExpr.instanceCall(
                    receiver, "substring", "java/lang/String",
                    List.of(LiteralExpr.ofInt(0), LiteralExpr.ofInt(5)),
                    new ReferenceSourceType("java/lang/String", List.of()));
            String code = SourceEmitter.emit(call);
            return code.equals("str.substring(0, 5)");
        });

        test("Emit static method call", () -> {
            MethodCallExpr call = MethodCallExpr.staticCall(
                    "java/lang/Math", "abs",
                    List.of(LiteralExpr.ofInt(-5)),
                    PrimitiveSourceType.INT);
            String code = SourceEmitter.emit(call);
            return code.equals("Math.abs(-5)");
        });

        test("Emit cast expression", () -> {
            CastExpr cast = new CastExpr(
                    PrimitiveSourceType.LONG,
                    new VarRefExpr("x", PrimitiveSourceType.INT, null));
            String code = SourceEmitter.emit(cast);
            return code.equals("(long) x");
        });

        test("Emit array access", () -> {
            ArrayAccessExpr access = new ArrayAccessExpr(
                    new VarRefExpr("arr", new ArraySourceType(PrimitiveSourceType.INT), null),
                    LiteralExpr.ofInt(0),
                    PrimitiveSourceType.INT);
            String code = SourceEmitter.emit(access);
            return code.equals("arr[0]");
        });

        test("Emit new object", () -> {
            NewExpr newExpr = new NewExpr("java/lang/StringBuilder",
                    List.of(LiteralExpr.ofString("init")));
            String code = SourceEmitter.emit(newExpr);
            return code.equals("new StringBuilder(\"init\")");
        });

        test("Emit new array", () -> {
            NewArrayExpr newArr = NewArrayExpr.withSize(PrimitiveSourceType.INT, LiteralExpr.ofInt(10));
            String code = SourceEmitter.emit(newArr);
            return code.equals("new int[10]");
        });
    }

    // ==================== Part 3: Statement Emission ====================

    private static void testStatementEmission() {
        section("Part 3: Statement Emission");

        test("Emit variable declaration", () -> {
            VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT, "count", LiteralExpr.ofInt(0));
            String code = SourceEmitter.emit(decl);
            return code.trim().equals("int count = 0;");
        });

        test("Emit return statement", () -> {
            ReturnStmt ret = new ReturnStmt(new VarRefExpr("result", PrimitiveSourceType.INT, null));
            String code = SourceEmitter.emit(ret);
            return code.trim().equals("return result;");
        });

        test("Emit void return", () -> {
            ReturnStmt ret = new ReturnStmt(null);
            String code = SourceEmitter.emit(ret);
            return code.trim().equals("return;");
        });

        test("Emit if statement", () -> {
            IfStmt ifStmt = new IfStmt(
                    new BinaryExpr(BinaryOperator.GT,
                            new VarRefExpr("x", PrimitiveSourceType.INT, null),
                            LiteralExpr.ofInt(0),
                            PrimitiveSourceType.BOOLEAN),
                    new ReturnStmt(LiteralExpr.ofInt(1)),
                    null);
            String code = SourceEmitter.emit(ifStmt);
            return code.contains("if (x > 0)") && code.contains("return 1;");
        });

        test("Emit if-else statement", () -> {
            IfStmt ifStmt = new IfStmt(
                    new BinaryExpr(BinaryOperator.GT,
                            new VarRefExpr("x", PrimitiveSourceType.INT, null),
                            LiteralExpr.ofInt(0),
                            PrimitiveSourceType.BOOLEAN),
                    new ReturnStmt(LiteralExpr.ofInt(1)),
                    new ReturnStmt(LiteralExpr.ofInt(0)));
            String code = SourceEmitter.emit(ifStmt);
            return code.contains("if (x > 0)") && code.contains("else") && code.contains("return 0;");
        });

        test("Emit while loop", () -> {
            WhileStmt whileStmt = new WhileStmt(
                    new BinaryExpr(BinaryOperator.LT,
                            new VarRefExpr("i", PrimitiveSourceType.INT, null),
                            LiteralExpr.ofInt(10),
                            PrimitiveSourceType.BOOLEAN),
                    new BlockStmt(List.of(
                            new ExprStmt(new BinaryExpr(BinaryOperator.ADD_ASSIGN,
                                    new VarRefExpr("i", PrimitiveSourceType.INT, null),
                                    LiteralExpr.ofInt(1),
                                    PrimitiveSourceType.INT))
                    )));
            String code = SourceEmitter.emit(whileStmt);
            return code.contains("while (i < 10)") && code.contains("i += 1");
        });

        test("Emit block statement", () -> {
            BlockStmt block = new BlockStmt(List.of(
                    new VarDeclStmt(PrimitiveSourceType.INT, "a", LiteralExpr.ofInt(1)),
                    new VarDeclStmt(PrimitiveSourceType.INT, "b", LiteralExpr.ofInt(2)),
                    new ReturnStmt(new BinaryExpr(BinaryOperator.ADD,
                            new VarRefExpr("a", PrimitiveSourceType.INT, null),
                            new VarRefExpr("b", PrimitiveSourceType.INT, null),
                            PrimitiveSourceType.INT))
            ));
            String code = SourceEmitter.emit(block);
            return code.contains("int a = 1;") && code.contains("int b = 2;") && code.contains("return a + b;");
        });

        test("Emit expression statement", () -> {
            ExprStmt expr = new ExprStmt(
                    MethodCallExpr.instanceCall(
                            new VarRefExpr("out", new ReferenceSourceType("java/io/PrintStream", List.of()), null),
                            "println", "java/io/PrintStream",
                            List.of(LiteralExpr.ofString("Hello")),
                            VoidSourceType.INSTANCE));
            String code = SourceEmitter.emit(expr);
            return code.contains("out.println(\"Hello\");");
        });

        test("Emit throw statement", () -> {
            ThrowStmt throwStmt = new ThrowStmt(
                    new NewExpr("java/lang/RuntimeException",
                            List.of(LiteralExpr.ofString("error"))));
            String code = SourceEmitter.emit(throwStmt);
            return code.contains("throw new RuntimeException(\"error\");");
        });

        test("Emit break statement", () -> {
            BreakStmt brk = new BreakStmt(null);
            String code = SourceEmitter.emit(brk);
            return code.trim().equals("break;");
        });

        test("Emit continue statement", () -> {
            ContinueStmt cont = new ContinueStmt(null);
            String code = SourceEmitter.emit(cont);
            return code.trim().equals("continue;");
        });
    }

    // ==================== Part 4: Type System ====================

    private static void testTypeSystem() {
        section("Part 4: Type System");

        test("Primitive types", () -> {
            return PrimitiveSourceType.INT.toJavaSource().equals("int")
                    && PrimitiveSourceType.LONG.toJavaSource().equals("long")
                    && PrimitiveSourceType.BOOLEAN.toJavaSource().equals("boolean")
                    && PrimitiveSourceType.DOUBLE.toJavaSource().equals("double");
        });

        test("Reference type simple name", () -> {
            ReferenceSourceType ref = new ReferenceSourceType("java/lang/String", List.of());
            return ref.toJavaSource().equals("String");
        });

        test("Array type", () -> {
            ArraySourceType arr = new ArraySourceType(PrimitiveSourceType.INT);
            return arr.toJavaSource().equals("int[]");
        });

        test("Multi-dimensional array", () -> {
            ArraySourceType arr2d = new ArraySourceType(PrimitiveSourceType.INT, 2);
            return arr2d.toJavaSource().equals("int[][]");
        });

        test("Void type", () -> {
            return VoidSourceType.INSTANCE.toJavaSource().equals("void");
        });

        test("Type from IR type conversion", () -> {
            // Test the fromIRType static method
            com.tonic.analysis.ssa.type.PrimitiveType irInt = com.tonic.analysis.ssa.type.PrimitiveType.INT;
            SourceType sourceType = SourceType.fromIRType(irInt);
            return sourceType == PrimitiveSourceType.INT;
        });
    }

    // ==================== Part 5: IR Recovery ====================

    private static void testIRRecovery() {
        section("Part 5: IR Recovery");

        try {
            ClassPool classPool = ClassPool.getDefault();

            try (InputStream is = SourceASTDemo.class.getResourceAsStream("ASTTestCases.class")) {
                if (is == null) {
                    System.out.println("  [SKIP] ASTTestCases.class not found - run 'gradlew build' first");
                    return;
                }

                ClassFile classFile = classPool.loadClass(is);
                System.out.println("  Loaded: " + classFile.getClassName());
                System.out.println("  Methods: " + classFile.getMethods().size());
                System.out.println();

                // Test recovery of various methods
                testMethodRecovery(classFile, "simpleArithmetic");
                testMethodRecovery(classFile, "conditionalLogic");
                testMethodRecovery(classFile, "whileLoop");
                testMethodRecovery(classFile, "methodCalls");
                testMethodRecovery(classFile, "fieldAccess");
                testMethodRecovery(classFile, "createObject");
                testMethodRecovery(classFile, "comparisons");
                testMethodRecovery(classFile, "bitwiseOps");
                testMethodRecovery(classFile, "typeCasting");
            }
        } catch (IOException e) {
            System.out.println("  [ERROR] Failed to load test class: " + e.getMessage());
            failCount++;
        }
    }

    private static void testMethodRecovery(ClassFile classFile, String methodName) {
        test("Recover " + methodName, () -> {
            MethodEntry method = findMethod(classFile, methodName);
            if (method == null) {
                System.out.println("    Method not found: " + methodName);
                return false;
            }

            // Lift to SSA
            SSA ssa = new SSA(classFile.getConstPool());
            IRMethod irMethod = ssa.lift(method);

            if (irMethod == null || irMethod.getBlocks().isEmpty()) {
                System.out.println("    Failed to lift to IR");
                return false;
            }

            // Recover source AST
            MethodRecoverer recoverer = new MethodRecoverer(irMethod, method,
                    NameRecoveryStrategy.PREFER_DEBUG_INFO);
            BlockStmt body = recoverer.recover();

            if (body == null) {
                System.out.println("    Failed to recover AST");
                return false;
            }

            // Emit to source
            String source = SourceEmitter.emit(body);

            if (source == null || source.isEmpty()) {
                System.out.println("    Failed to emit source");
                return false;
            }

            // Print recovered source
            System.out.println("    --- " + methodName + " ---");
            for (String line : source.split("\n")) {
                System.out.println("    " + line);
            }
            System.out.println();

            return true;
        });
    }

    private static MethodEntry findMethod(ClassFile classFile, String name) {
        for (MethodEntry method : classFile.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }

    // ==================== Test Helpers ====================

    private static void section(String title) {
        System.out.println();
        System.out.println("--- " + title + " ---");
        System.out.println();
    }

    private static void test(String name, TestCase testCase) {
        try {
            boolean result = testCase.run();
            if (result) {
                System.out.println("  [PASS] " + name);
                passCount++;
            } else {
                System.out.println("  [FAIL] " + name);
                failCount++;
            }
        } catch (Exception e) {
            System.out.println("  [FAIL] " + name + " - Exception: " + e.getMessage());
            e.printStackTrace();
            failCount++;
        }
    }

    @FunctionalInterface
    interface TestCase {
        boolean run() throws Exception;
    }
}
