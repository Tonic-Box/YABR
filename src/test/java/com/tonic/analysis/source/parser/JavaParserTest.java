package com.tonic.analysis.source.parser;

import com.tonic.analysis.source.ast.decl.*;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.source.emit.SourceEmitter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserTest {

    private final JavaParser parser = JavaParser.create();

    @Nested
    class ExpressionTests {

        @Nested
        class LiteralExpressionTests {
            @Test
            void parsesIntegerLiteral() {
                Expression expr = parser.parseExpression("42");
                assertInstanceOf(LiteralExpr.class, expr);
                assertEquals(42, ((LiteralExpr) expr).getValue());
            }

            @Test
            void parsesLongLiteral() {
                Expression expr = parser.parseExpression("42L");
                assertInstanceOf(LiteralExpr.class, expr);
                assertEquals(42L, ((LiteralExpr) expr).getValue());
            }

            @Test
            void parsesFloatLiteral() {
                Expression expr = parser.parseExpression("3.14f");
                assertInstanceOf(LiteralExpr.class, expr);
            }

            @Test
            void parsesDoubleLiteral() {
                Expression expr = parser.parseExpression("3.14");
                assertInstanceOf(LiteralExpr.class, expr);
                assertEquals(3.14, ((LiteralExpr) expr).getValue());
            }

            @Test
            void parsesStringLiteral() {
                Expression expr = parser.parseExpression("\"hello\"");
                assertInstanceOf(LiteralExpr.class, expr);
                assertEquals("hello", ((LiteralExpr) expr).getValue());
            }

            @Test
            void parsesCharLiteral() {
                Expression expr = parser.parseExpression("'a'");
                assertInstanceOf(LiteralExpr.class, expr);
                assertEquals('a', ((LiteralExpr) expr).getValue());
            }

            @Test
            void parsesBooleanLiterals() {
                Expression trueExpr = parser.parseExpression("true");
                assertInstanceOf(LiteralExpr.class, trueExpr);
                assertEquals(true, ((LiteralExpr) trueExpr).getValue());

                Expression falseExpr = parser.parseExpression("false");
                assertInstanceOf(LiteralExpr.class, falseExpr);
                assertEquals(false, ((LiteralExpr) falseExpr).getValue());
            }

            @Test
            void parsesNullLiteral() {
                Expression expr = parser.parseExpression("null");
                assertInstanceOf(LiteralExpr.class, expr);
                assertNull(((LiteralExpr) expr).getValue());
            }
        }

        @Nested
        class BinaryExpressionTests {
            @Test
            void parsesAddition() {
                Expression expr = parser.parseExpression("1 + 2");
                assertInstanceOf(BinaryExpr.class, expr);
                BinaryExpr bin = (BinaryExpr) expr;
                assertEquals(BinaryOperator.ADD, bin.getOperator());
            }

            @Test
            void parsesSubtraction() {
                Expression expr = parser.parseExpression("5 - 3");
                assertInstanceOf(BinaryExpr.class, expr);
                BinaryExpr bin = (BinaryExpr) expr;
                assertEquals(BinaryOperator.SUB, bin.getOperator());
            }

            @Test
            void parsesMultiplication() {
                Expression expr = parser.parseExpression("4 * 2");
                assertInstanceOf(BinaryExpr.class, expr);
                BinaryExpr bin = (BinaryExpr) expr;
                assertEquals(BinaryOperator.MUL, bin.getOperator());
            }

            @Test
            void parsesDivision() {
                Expression expr = parser.parseExpression("10 / 2");
                assertInstanceOf(BinaryExpr.class, expr);
                BinaryExpr bin = (BinaryExpr) expr;
                assertEquals(BinaryOperator.DIV, bin.getOperator());
            }

            @Test
            void parsesModulo() {
                Expression expr = parser.parseExpression("7 % 3");
                assertInstanceOf(BinaryExpr.class, expr);
                BinaryExpr bin = (BinaryExpr) expr;
                assertEquals(BinaryOperator.MOD, bin.getOperator());
            }

            @Test
            void precedenceMultiplicationBeforeAddition() {
                Expression expr = parser.parseExpression("1 + 2 * 3");
                assertInstanceOf(BinaryExpr.class, expr);
                BinaryExpr bin = (BinaryExpr) expr;
                assertEquals(BinaryOperator.ADD, bin.getOperator());
                assertInstanceOf(BinaryExpr.class, bin.getRight());
            }

            @Test
            void parsesComparison() {
                assertEquals(BinaryOperator.LT, getBinaryOp("a < b"));
                assertEquals(BinaryOperator.GT, getBinaryOp("a > b"));
                assertEquals(BinaryOperator.LE, getBinaryOp("a <= b"));
                assertEquals(BinaryOperator.GE, getBinaryOp("a >= b"));
                assertEquals(BinaryOperator.EQ, getBinaryOp("a == b"));
                assertEquals(BinaryOperator.NE, getBinaryOp("a != b"));
            }

            @Test
            void parsesLogicalOperators() {
                assertEquals(BinaryOperator.AND, getBinaryOp("a && b"));
                assertEquals(BinaryOperator.OR, getBinaryOp("a || b"));
            }

            @Test
            void parsesBitwiseOperators() {
                assertEquals(BinaryOperator.BAND, getBinaryOp("a & b"));
                assertEquals(BinaryOperator.BOR, getBinaryOp("a | b"));
                assertEquals(BinaryOperator.BXOR, getBinaryOp("a ^ b"));
            }

            @Test
            void parsesShiftOperators() {
                assertEquals(BinaryOperator.SHL, getBinaryOp("a << b"));
                assertEquals(BinaryOperator.SHR, getBinaryOp("a >> b"));
                assertEquals(BinaryOperator.USHR, getBinaryOp("a >>> b"));
            }

            @Test
            void parsesAssignment() {
                assertEquals(BinaryOperator.ASSIGN, getBinaryOp("a = b"));
                assertEquals(BinaryOperator.ADD_ASSIGN, getBinaryOp("a += b"));
                assertEquals(BinaryOperator.SUB_ASSIGN, getBinaryOp("a -= b"));
                assertEquals(BinaryOperator.MUL_ASSIGN, getBinaryOp("a *= b"));
                assertEquals(BinaryOperator.DIV_ASSIGN, getBinaryOp("a /= b"));
            }

            private BinaryOperator getBinaryOp(String source) {
                Expression expr = parser.parseExpression(source);
                return ((BinaryExpr) expr).getOperator();
            }
        }

        @Nested
        class UnaryExpressionTests {
            @Test
            void parsesNegation() {
                Expression expr = parser.parseExpression("-x");
                assertInstanceOf(UnaryExpr.class, expr);
                assertEquals(UnaryOperator.NEG, ((UnaryExpr) expr).getOperator());
            }

            @Test
            void parsesLogicalNot() {
                Expression expr = parser.parseExpression("!x");
                assertInstanceOf(UnaryExpr.class, expr);
                assertEquals(UnaryOperator.NOT, ((UnaryExpr) expr).getOperator());
            }

            @Test
            void parsesBitwiseNot() {
                Expression expr = parser.parseExpression("~x");
                assertInstanceOf(UnaryExpr.class, expr);
                assertEquals(UnaryOperator.BNOT, ((UnaryExpr) expr).getOperator());
            }

            @Test
            void parsesPreIncrement() {
                Expression expr = parser.parseExpression("++x");
                assertInstanceOf(UnaryExpr.class, expr);
                assertEquals(UnaryOperator.PRE_INC, ((UnaryExpr) expr).getOperator());
            }

            @Test
            void parsesPostIncrement() {
                Expression expr = parser.parseExpression("x++");
                assertInstanceOf(UnaryExpr.class, expr);
                assertEquals(UnaryOperator.POST_INC, ((UnaryExpr) expr).getOperator());
            }
        }

        @Nested
        class TernaryExpressionTests {
            @Test
            void parsesTernary() {
                Expression expr = parser.parseExpression("a ? b : c");
                assertInstanceOf(TernaryExpr.class, expr);
                TernaryExpr ternary = (TernaryExpr) expr;
                assertInstanceOf(VarRefExpr.class, ternary.getCondition());
                assertInstanceOf(VarRefExpr.class, ternary.getThenExpr());
                assertInstanceOf(VarRefExpr.class, ternary.getElseExpr());
            }
        }

        @Nested
        class MethodCallTests {
            @Test
            void parsesSimpleMethodCall() {
                Expression expr = parser.parseExpression("foo()");
                assertInstanceOf(MethodCallExpr.class, expr);
                assertEquals("foo", ((MethodCallExpr) expr).getMethodName());
            }

            @Test
            void parsesMethodCallWithArgs() {
                Expression expr = parser.parseExpression("foo(1, 2, 3)");
                assertInstanceOf(MethodCallExpr.class, expr);
                MethodCallExpr call = (MethodCallExpr) expr;
                assertEquals("foo", call.getMethodName());
                assertEquals(3, call.getArguments().size());
            }

            @Test
            void parsesMethodCallOnObject() {
                Expression expr = parser.parseExpression("obj.foo()");
                assertInstanceOf(MethodCallExpr.class, expr);
                MethodCallExpr call = (MethodCallExpr) expr;
                assertEquals("foo", call.getMethodName());
                assertNotNull(call.getReceiver());
            }
        }

        @Nested
        class NewExpressionTests {
            @Test
            void parsesNewObject() {
                Expression expr = parser.parseExpression("new Foo()");
                assertInstanceOf(NewExpr.class, expr);
                assertEquals("Foo", ((NewExpr) expr).getClassName());
            }

            @Test
            void parsesNewObjectWithArgs() {
                Expression expr = parser.parseExpression("new Foo(1, 2)");
                assertInstanceOf(NewExpr.class, expr);
                NewExpr newExpr = (NewExpr) expr;
                assertEquals("Foo", newExpr.getClassName());
                assertEquals(2, newExpr.getArguments().size());
            }

            @Test
            void parsesNewArray() {
                Expression expr = parser.parseExpression("new int[10]");
                assertInstanceOf(NewArrayExpr.class, expr);
            }

            @Test
            void parsesNewArrayWithInit() {
                Expression expr = parser.parseExpression("new int[] {1, 2, 3}");
                assertInstanceOf(NewArrayExpr.class, expr);
            }
        }

        @Nested
        class LambdaExpressionTests {
            @Test
            void parsesSingleParamLambda() {
                Expression expr = parser.parseExpression("x -> x + 1");
                assertInstanceOf(LambdaExpr.class, expr);
                LambdaExpr lambda = (LambdaExpr) expr;
                assertEquals(1, lambda.getParameters().size());
            }

            @Test
            void parsesMultiParamLambda() {
                Expression expr = parser.parseExpression("(a, b) -> a + b");
                assertInstanceOf(LambdaExpr.class, expr);
                LambdaExpr lambda = (LambdaExpr) expr;
                assertEquals(2, lambda.getParameters().size());
            }

            @Test
            void parsesBlockLambda() {
                Expression expr = parser.parseExpression("x -> { return x + 1; }");
                assertInstanceOf(LambdaExpr.class, expr);
            }
        }

        @Nested
        class CastExpressionTests {
            @Test
            void parsesCast() {
                Expression expr = parser.parseExpression("(int) x");
                assertInstanceOf(CastExpr.class, expr);
                assertEquals(PrimitiveSourceType.INT, ((CastExpr) expr).getTargetType());
            }
        }

        @Nested
        class InstanceOfTests {
            @Test
            void parsesInstanceOf() {
                Expression expr = parser.parseExpression("x instanceof String");
                assertInstanceOf(InstanceOfExpr.class, expr);
            }
        }
    }

    @Nested
    class StatementTests {

        @Nested
        class IfStatementTests {
            @Test
            void parsesSimpleIf() {
                Statement stmt = parser.parseStatement("if (x) y();");
                assertInstanceOf(IfStmt.class, stmt);
            }

            @Test
            void parsesIfElse() {
                Statement stmt = parser.parseStatement("if (x) y(); else z();");
                assertInstanceOf(IfStmt.class, stmt);
                assertNotNull(((IfStmt) stmt).getElseBranch());
            }
        }

        @Nested
        class LoopStatementTests {
            @Test
            void parsesWhile() {
                Statement stmt = parser.parseStatement("while (x) y();");
                assertInstanceOf(WhileStmt.class, stmt);
            }

            @Test
            void parsesDoWhile() {
                Statement stmt = parser.parseStatement("do x(); while (y);");
                assertInstanceOf(DoWhileStmt.class, stmt);
            }

            @Test
            void parsesFor() {
                Statement stmt = parser.parseStatement("for (int i = 0; i < 10; i++) x();");
                assertInstanceOf(ForStmt.class, stmt);
            }

            @Test
            void parsesForEach() {
                Statement stmt = parser.parseStatement("for (int x : list) foo();");
                assertInstanceOf(ForEachStmt.class, stmt);
            }
        }

        @Nested
        class SwitchStatementTests {
            @Test
            void parsesSwitch() {
                Statement stmt = parser.parseStatement("switch (x) { case 1: break; default: return; }");
                assertInstanceOf(SwitchStmt.class, stmt);
                assertEquals(2, ((SwitchStmt) stmt).getCases().size());
            }
        }

        @Nested
        class TryStatementTests {
            @Test
            void parsesTryCatch() {
                Statement stmt = parser.parseStatement("try { x(); } catch (Exception e) { e.printStackTrace(); }");
                assertInstanceOf(TryCatchStmt.class, stmt);
            }

            @Test
            void parsesTryFinally() {
                Statement stmt = parser.parseStatement("try { x(); } finally { cleanup(); }");
                assertInstanceOf(TryCatchStmt.class, stmt);
                assertNotNull(((TryCatchStmt) stmt).getFinallyBlock());
            }

            @Test
            void parsesTryWithResources() {
                Statement stmt = parser.parseStatement("try (InputStream is = open()) { read(is); }");
                assertInstanceOf(TryCatchStmt.class, stmt);
            }
        }

        @Nested
        class ControlFlowStatementTests {
            @Test
            void parsesReturn() {
                Statement stmt = parser.parseStatement("return x;");
                assertInstanceOf(ReturnStmt.class, stmt);
            }

            @Test
            void parsesThrow() {
                Statement stmt = parser.parseStatement("throw new Exception();");
                assertInstanceOf(ThrowStmt.class, stmt);
            }

            @Test
            void parsesBreak() {
                Statement stmt = parser.parseStatement("break;");
                assertInstanceOf(BreakStmt.class, stmt);
            }

            @Test
            void parsesContinue() {
                Statement stmt = parser.parseStatement("continue;");
                assertInstanceOf(ContinueStmt.class, stmt);
            }
        }

        @Nested
        class VarDeclStatementTests {
            @Test
            void parsesVarDecl() {
                Statement stmt = parser.parseStatement("int x = 5;");
                assertInstanceOf(VarDeclStmt.class, stmt);
                VarDeclStmt varDecl = (VarDeclStmt) stmt;
                assertEquals("x", varDecl.getName());
            }

            @Test
            void parsesFinalVar() {
                Statement stmt = parser.parseStatement("final int x = 5;");
                assertInstanceOf(VarDeclStmt.class, stmt);
                assertTrue(((VarDeclStmt) stmt).isFinal());
            }

            @Test
            void parsesVarKeyword() {
                Statement stmt = parser.parseStatement("var x = 5;");
                assertInstanceOf(VarDeclStmt.class, stmt);
            }
        }
    }

    @Nested
    class TypeTests {
        @Test
        void parsesPrimitiveTypes() {
            assertEquals(PrimitiveSourceType.INT, parser.parseType("int"));
            assertEquals(PrimitiveSourceType.LONG, parser.parseType("long"));
            assertEquals(PrimitiveSourceType.DOUBLE, parser.parseType("double"));
            assertEquals(PrimitiveSourceType.BOOLEAN, parser.parseType("boolean"));
        }

        @Test
        void parsesReferenceType() {
            SourceType type = parser.parseType("String");
            assertInstanceOf(ReferenceSourceType.class, type);
        }

        @Test
        void parsesQualifiedType() {
            SourceType type = parser.parseType("java.util.List");
            assertInstanceOf(ReferenceSourceType.class, type);
        }

        @Test
        void parsesArrayType() {
            SourceType type = parser.parseType("int[]");
            assertInstanceOf(ArraySourceType.class, type);
        }

        @Test
        void parsesGenericType() {
            SourceType type = parser.parseType("List<String>");
            assertInstanceOf(GenericSourceType.class, type);
        }

        @Test
        void parsesWildcardType() {
            SourceType type = parser.parseType("List<?>");
            assertInstanceOf(GenericSourceType.class, type);
        }

        @Test
        void parsesWildcardWithExtends() {
            SourceType type = parser.parseType("List<? extends Number>");
            assertInstanceOf(GenericSourceType.class, type);
        }

        @Test
        void parsesWildcardWithSuper() {
            SourceType type = parser.parseType("List<? super Integer>");
            assertInstanceOf(GenericSourceType.class, type);
        }
    }

    @Nested
    class DeclarationTests {

        @Nested
        class ClassDeclarationTests {
            @Test
            void parsesEmptyClass() {
                CompilationUnit cu = parser.parse("class Foo {}");
                assertEquals(1, cu.getTypes().size());
                assertInstanceOf(ClassDecl.class, cu.getTypes().get(0));
                assertEquals("Foo", cu.getTypes().get(0).getName());
            }

            @Test
            void parsesPublicClass() {
                CompilationUnit cu = parser.parse("public class Foo {}");
                ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
                assertTrue(cls.isPublic());
            }

            @Test
            void parsesClassWithExtends() {
                CompilationUnit cu = parser.parse("class Foo extends Bar {}");
                ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
                assertNotNull(cls.getSuperclass());
            }

            @Test
            void parsesClassWithImplements() {
                CompilationUnit cu = parser.parse("class Foo implements Bar, Baz {}");
                ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
                assertEquals(2, cls.getInterfaces().size());
            }

            @Test
            void parsesClassWithField() {
                CompilationUnit cu = parser.parse("class Foo { int x; }");
                ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
                assertEquals(1, cls.getFields().size());
            }

            @Test
            void parsesClassWithMethod() {
                CompilationUnit cu = parser.parse("class Foo { void bar() {} }");
                ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
                assertEquals(1, cls.getMethods().size());
            }

            @Test
            void parsesClassWithConstructor() {
                CompilationUnit cu = parser.parse("class Foo { Foo() {} }");
                ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
                assertEquals(1, cls.getConstructors().size());
            }
        }

        @Nested
        class InterfaceDeclarationTests {
            @Test
            void parsesEmptyInterface() {
                CompilationUnit cu = parser.parse("interface Foo {}");
                assertInstanceOf(InterfaceDecl.class, cu.getTypes().get(0));
            }

            @Test
            void parsesInterfaceWithMethods() {
                CompilationUnit cu = parser.parse("interface Foo { void bar(); void baz(); }");
                InterfaceDecl iface = (InterfaceDecl) cu.getTypes().get(0);
                assertEquals(2, iface.getMethods().size());
            }

            @Test
            void parsesInterfaceWithExtends() {
                CompilationUnit cu = parser.parse("interface Foo extends Bar, Baz {}");
                InterfaceDecl iface = (InterfaceDecl) cu.getTypes().get(0);
                assertEquals(2, iface.getExtendedInterfaces().size());
            }
        }

        @Nested
        class EnumDeclarationTests {
            @Test
            void parsesSimpleEnum() {
                CompilationUnit cu = parser.parse("enum Color { RED, GREEN, BLUE }");
                assertInstanceOf(EnumDecl.class, cu.getTypes().get(0));
                EnumDecl enumDecl = (EnumDecl) cu.getTypes().get(0);
                assertEquals(3, enumDecl.getConstants().size());
            }

            @Test
            void parsesEnumWithMethods() {
                CompilationUnit cu = parser.parse("enum Color { RED; public void foo() {} }");
                EnumDecl enumDecl = (EnumDecl) cu.getTypes().get(0);
                assertEquals(1, enumDecl.getMethods().size());
            }
        }

        @Nested
        class CompilationUnitTests {
            @Test
            void parsesPackageDeclaration() {
                CompilationUnit cu = parser.parse("package com.example; class Foo {}");
                assertEquals("com.example", cu.getPackageName());
            }

            @Test
            void parsesImports() {
                CompilationUnit cu = parser.parse("import java.util.List; import java.util.Map; class Foo {}");
                assertEquals(2, cu.getImports().size());
            }

            @Test
            void parsesStaticImport() {
                CompilationUnit cu = parser.parse("import static java.lang.Math.PI; class Foo {}");
                assertTrue(cu.getImports().get(0).isStatic());
            }

            @Test
            void parsesWildcardImport() {
                CompilationUnit cu = parser.parse("import java.util.*; class Foo {}");
                assertTrue(cu.getImports().get(0).isWildcard());
            }
        }
    }

    @Nested
    class ErrorHandlingTests {
        @Test
        void throwsOnMissingSemicolon() {
            assertThrows(ParseException.class, () -> parser.parseStatement("int x = 5"));
        }

        @Test
        void throwsOnMissingClosingParen() {
            assertThrows(ParseException.class, () -> parser.parseExpression("foo(1, 2"));
        }

        @Test
        void throwsOnUnexpectedToken() {
            assertThrows(ParseException.class, () -> parser.parseExpression("1 * * 2"));
        }

        @Test
        void errorMessageIncludesLine() {
            try {
                parser.parse("class Foo {\n  int x =\n}");
                fail("Expected ParseException");
            } catch (ParseException e) {
                assertTrue(e.getLine() >= 2);
            }
        }
    }

    @Nested
    class RoundTripTests {
        @Test
        void roundTripSimpleClass() {
            String source = "class Foo {\n}";
            CompilationUnit cu = parser.parse(source);
            String emitted = SourceEmitter.emit(cu);
            assertNotNull(emitted);
            assertTrue(emitted.contains("class Foo"));
        }

        @Test
        void roundTripClassWithMethod() {
            CompilationUnit cu = parser.parse("class Foo { void bar() {} }");
            String emitted = SourceEmitter.emit(cu);
            assertTrue(emitted.contains("void bar()"));
        }
    }
}
