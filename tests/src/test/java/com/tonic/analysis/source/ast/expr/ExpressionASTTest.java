package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ArraySourceType;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionASTTest {

    private static Expression createTestExpression() {
        return LiteralExpr.ofInt(42);
    }

    private static SourceLocation createTestLocation() {
        return new SourceLocation(10, 5);
    }

    private static class TestVisitor implements SourceVisitor<String> {
        @Override
        public String visitLiteral(LiteralExpr expr) {
            return "visitLiteral";
        }

        @Override
        public String visitVarRef(VarRefExpr expr) {
            return "visitVarRef";
        }

        @Override
        public String visitFieldAccess(FieldAccessExpr expr) {
            return "visitFieldAccess";
        }

        @Override
        public String visitArrayAccess(ArrayAccessExpr expr) {
            return "visitArrayAccess";
        }

        @Override
        public String visitMethodCall(MethodCallExpr expr) {
            return "visitMethodCall";
        }

        @Override
        public String visitNew(NewExpr expr) {
            return "visitNew";
        }

        @Override
        public String visitNewArray(NewArrayExpr expr) {
            return "visitNewArray";
        }

        @Override
        public String visitArrayInit(ArrayInitExpr expr) {
            return "visitArrayInit";
        }

        @Override
        public String visitBinary(BinaryExpr expr) {
            return "visitBinary";
        }

        @Override
        public String visitUnary(UnaryExpr expr) {
            return "visitUnary";
        }

        @Override
        public String visitCast(CastExpr expr) {
            return "visitCast";
        }

        @Override
        public String visitInstanceOf(InstanceOfExpr expr) {
            return "visitInstanceOf";
        }

        @Override
        public String visitTernary(TernaryExpr expr) {
            return "visitTernary";
        }

        @Override
        public String visitLambda(LambdaExpr expr) {
            return "visitLambda";
        }

        @Override
        public String visitMethodRef(MethodRefExpr expr) {
            return "visitMethodRef";
        }

        @Override
        public String visitThis(ThisExpr expr) {
            return "visitThis";
        }

        @Override
        public String visitSuper(SuperExpr expr) {
            return "visitSuper";
        }

        @Override
        public String visitClass(ClassExpr expr) {
            return "visitClass";
        }

        @Override
        public String visitDynamicConstant(DynamicConstantExpr expr) {
            return "visitDynamicConstant";
        }

        @Override
        public String visitInvokeDynamic(InvokeDynamicExpr expr) {
            return "visitInvokeDynamic";
        }

        @Override
        public String visitBlock(com.tonic.analysis.source.ast.stmt.BlockStmt stmt) {
            return "visitBlock";
        }

        @Override
        public String visitIf(com.tonic.analysis.source.ast.stmt.IfStmt stmt) {
            return "visitIf";
        }

        @Override
        public String visitWhile(com.tonic.analysis.source.ast.stmt.WhileStmt stmt) {
            return "visitWhile";
        }

        @Override
        public String visitDoWhile(com.tonic.analysis.source.ast.stmt.DoWhileStmt stmt) {
            return "visitDoWhile";
        }

        @Override
        public String visitFor(com.tonic.analysis.source.ast.stmt.ForStmt stmt) {
            return "visitFor";
        }

        @Override
        public String visitForEach(com.tonic.analysis.source.ast.stmt.ForEachStmt stmt) {
            return "visitForEach";
        }

        @Override
        public String visitSwitch(com.tonic.analysis.source.ast.stmt.SwitchStmt stmt) {
            return "visitSwitch";
        }

        @Override
        public String visitTryCatch(com.tonic.analysis.source.ast.stmt.TryCatchStmt stmt) {
            return "visitTryCatch";
        }

        @Override
        public String visitReturn(com.tonic.analysis.source.ast.stmt.ReturnStmt stmt) {
            return "visitReturn";
        }

        @Override
        public String visitThrow(com.tonic.analysis.source.ast.stmt.ThrowStmt stmt) {
            return "visitThrow";
        }

        @Override
        public String visitVarDecl(com.tonic.analysis.source.ast.stmt.VarDeclStmt stmt) {
            return "visitVarDecl";
        }

        @Override
        public String visitExprStmt(com.tonic.analysis.source.ast.stmt.ExprStmt stmt) {
            return "visitExprStmt";
        }

        @Override
        public String visitSynchronized(com.tonic.analysis.source.ast.stmt.SynchronizedStmt stmt) {
            return "visitSynchronized";
        }

        @Override
        public String visitLabeled(com.tonic.analysis.source.ast.stmt.LabeledStmt stmt) {
            return "visitLabeled";
        }

        @Override
        public String visitBreak(com.tonic.analysis.source.ast.stmt.BreakStmt stmt) {
            return "visitBreak";
        }

        @Override
        public String visitContinue(com.tonic.analysis.source.ast.stmt.ContinueStmt stmt) {
            return "visitContinue";
        }

        @Override
        public String visitIRRegion(com.tonic.analysis.source.ast.stmt.IRRegionStmt stmt) {
            return "visitIRRegion";
        }

        @Override
        public String visitPrimitiveType(PrimitiveSourceType type) {
            return "visitPrimitiveType";
        }

        @Override
        public String visitReferenceType(ReferenceSourceType type) {
            return "visitReferenceType";
        }

        @Override
        public String visitArrayType(ArraySourceType type) {
            return "visitArrayType";
        }

        @Override
        public String visitVoidType(com.tonic.analysis.source.ast.type.VoidSourceType type) {
            return "visitVoidType";
        }
    }

    @Nested
    class ArrayAccessExprTests {

        @Test
        void basicConstructor() {
            Expression array = LiteralExpr.ofString("arr");
            Expression index = LiteralExpr.ofInt(0);
            SourceType type = PrimitiveSourceType.INT;

            ArrayAccessExpr expr = new ArrayAccessExpr(array, index, type);

            assertNotNull(expr);
            assertEquals(array, expr.getArray());
            assertEquals(index, expr.getIndex());
            assertEquals(type, expr.getType());
            assertEquals(SourceLocation.UNKNOWN, expr.getLocation());
        }

        @Test
        void withLocation() {
            Expression array = LiteralExpr.ofString("arr");
            Expression index = LiteralExpr.ofInt(0);
            SourceLocation location = createTestLocation();

            ArrayAccessExpr expr = new ArrayAccessExpr(array, index, PrimitiveSourceType.INT, location);

            assertEquals(location, expr.getLocation());
        }

        @Test
        void parentChildRelationships() {
            Expression array = LiteralExpr.ofString("arr");
            Expression index = LiteralExpr.ofInt(0);

            ArrayAccessExpr expr = new ArrayAccessExpr(array, index, PrimitiveSourceType.INT);

            assertEquals(expr, array.getParent());
            assertEquals(expr, index.getParent());
        }

        @Test
        void setters() {
            ArrayAccessExpr expr = new ArrayAccessExpr(
                    LiteralExpr.ofString("arr"),
                    LiteralExpr.ofInt(0),
                    PrimitiveSourceType.INT
            );

            Expression newArray = LiteralExpr.ofString("newArr");
            Expression newIndex = LiteralExpr.ofInt(5);

            expr.setArray(newArray);
            expr.setIndex(newIndex);

            assertEquals(newArray, expr.getArray());
            assertEquals(newIndex, expr.getIndex());
        }

        @Test
        void nullArrayThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ArrayAccessExpr(null, LiteralExpr.ofInt(0), PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullIndexThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ArrayAccessExpr(LiteralExpr.ofString("arr"), null, PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ArrayAccessExpr(LiteralExpr.ofString("arr"), LiteralExpr.ofInt(0), null)
            );
        }

        @Test
        void visitorPattern() {
            ArrayAccessExpr expr = new ArrayAccessExpr(
                    LiteralExpr.ofString("arr"),
                    LiteralExpr.ofInt(0),
                    PrimitiveSourceType.INT
            );
            TestVisitor visitor = new TestVisitor();

            String result = expr.accept(visitor);

            assertEquals("visitArrayAccess", result);
        }

        @Test
        void toStringFormat() {
            ArrayAccessExpr expr = new ArrayAccessExpr(
                    LiteralExpr.ofString("arr"),
                    LiteralExpr.ofInt(5),
                    PrimitiveSourceType.INT
            );

            String str = expr.toString();

            assertTrue(str.contains("arr"));
            assertTrue(str.contains("["));
            assertTrue(str.contains("5"));
            assertTrue(str.contains("]"));
        }
    }

    @Nested
    class NewArrayExprTests {

        @Test
        void basicConstructor() {
            SourceType elementType = PrimitiveSourceType.INT;
            List<Expression> dimensions = Arrays.asList(LiteralExpr.ofInt(10));

            NewArrayExpr expr = new NewArrayExpr(elementType, dimensions);

            assertNotNull(expr);
            assertEquals(elementType, expr.getElementType());
            assertEquals(1, expr.getDimensionCount());
            assertFalse(expr.hasInitializer());
            assertEquals(SourceLocation.UNKNOWN, expr.getLocation());
        }

        @Test
        void withSize() {
            Expression size = LiteralExpr.ofInt(5);

            NewArrayExpr expr = NewArrayExpr.withSize(PrimitiveSourceType.INT, size);

            assertEquals(1, expr.getDimensionCount());
            assertEquals(size, expr.getDimensions().get(0));
        }

        @Test
        void withInitializer() {
            ArrayInitExpr init = new ArrayInitExpr(
                    Arrays.asList(LiteralExpr.ofInt(1), LiteralExpr.ofInt(2)),
                    new ArraySourceType(PrimitiveSourceType.INT)
            );

            NewArrayExpr expr = NewArrayExpr.withInit(PrimitiveSourceType.INT, init);

            assertTrue(expr.hasInitializer());
            assertEquals(init, expr.getInitializer());
        }

        @Test
        void multiDimensional() {
            List<Expression> dimensions = Arrays.asList(
                    LiteralExpr.ofInt(5),
                    LiteralExpr.ofInt(10)
            );

            NewArrayExpr expr = new NewArrayExpr(PrimitiveSourceType.INT, dimensions);

            assertEquals(2, expr.getDimensionCount());
        }

        @Test
        void addDimension() {
            NewArrayExpr expr = new NewArrayExpr(PrimitiveSourceType.INT, Collections.emptyList());

            Expression dim = LiteralExpr.ofInt(7);
            expr.addDimension(dim);

            assertEquals(1, expr.getDimensionCount());
            assertEquals(expr, dim.getParent());
        }

        @Test
        void parentChildRelationships() {
            Expression dim1 = LiteralExpr.ofInt(5);
            Expression dim2 = LiteralExpr.ofInt(10);
            ArrayInitExpr init = new ArrayInitExpr(Collections.emptyList(), new ArraySourceType(PrimitiveSourceType.INT));

            NewArrayExpr expr = new NewArrayExpr(
                    PrimitiveSourceType.INT,
                    Arrays.asList(dim1, dim2),
                    init,
                    null,
                    SourceLocation.UNKNOWN
            );

            assertEquals(expr, dim1.getParent());
            assertEquals(expr, dim2.getParent());
            assertEquals(expr, init.getParent());
        }

        @Test
        void nullElementTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new NewArrayExpr(null, Collections.emptyList())
            );
        }

        @Test
        void nullDimensionsHandled() {
            NewArrayExpr expr = new NewArrayExpr(PrimitiveSourceType.INT, (List<Expression>) null);

            assertNotNull(expr.getDimensions());
            assertEquals(0, expr.getDimensionCount());
        }

        @Test
        void setInitializer() {
            NewArrayExpr expr = new NewArrayExpr(PrimitiveSourceType.INT, Collections.emptyList());
            ArrayInitExpr init = new ArrayInitExpr(Collections.emptyList(), new ArraySourceType(PrimitiveSourceType.INT));

            expr.setInitializer(init);

            assertTrue(expr.hasInitializer());
            assertEquals(init, expr.getInitializer());
        }

        @Test
        void visitorPattern() {
            NewArrayExpr expr = new NewArrayExpr(PrimitiveSourceType.INT, Collections.emptyList());
            TestVisitor visitor = new TestVisitor();

            String result = expr.accept(visitor);

            assertEquals("visitNewArray", result);
        }

        @Test
        void toStringWithDimension() {
            NewArrayExpr expr = NewArrayExpr.withSize(PrimitiveSourceType.INT, LiteralExpr.ofInt(10));

            String str = expr.toString();

            assertTrue(str.contains("new"));
            assertTrue(str.contains("int"));
            assertTrue(str.contains("["));
            assertTrue(str.contains("10"));
            assertTrue(str.contains("]"));
        }

        @Test
        void toStringWithInitializer() {
            ArrayInitExpr init = new ArrayInitExpr(
                    Arrays.asList(LiteralExpr.ofInt(1)),
                    new ArraySourceType(PrimitiveSourceType.INT)
            );
            NewArrayExpr expr = NewArrayExpr.withInit(PrimitiveSourceType.INT, init);

            String str = expr.toString();

            assertTrue(str.contains("new"));
            assertTrue(str.contains("int[]"));
            assertTrue(str.contains("{"));
        }
    }

    @Nested
    class BinaryExprTests {

        @Test
        void basicConstructor() {
            Expression left = LiteralExpr.ofInt(5);
            Expression right = LiteralExpr.ofInt(3);
            BinaryOperator op = BinaryOperator.ADD;

            BinaryExpr expr = new BinaryExpr(op, left, right, PrimitiveSourceType.INT);

            assertNotNull(expr);
            assertEquals(op, expr.getOperator());
            assertEquals(left, expr.getLeft());
            assertEquals(right, expr.getRight());
            assertEquals(PrimitiveSourceType.INT, expr.getType());
        }

        @Test
        void withLocation() {
            SourceLocation location = createTestLocation();

            BinaryExpr expr = new BinaryExpr(
                    BinaryOperator.ADD,
                    LiteralExpr.ofInt(1),
                    LiteralExpr.ofInt(2),
                    PrimitiveSourceType.INT,
                    location
            );

            assertEquals(location, expr.getLocation());
        }

        @Test
        void getPrecedence() {
            BinaryExpr expr = new BinaryExpr(
                    BinaryOperator.MUL,
                    LiteralExpr.ofInt(2),
                    LiteralExpr.ofInt(3),
                    PrimitiveSourceType.INT
            );

            assertEquals(BinaryOperator.MUL.getPrecedence(), expr.getPrecedence());
        }

        @Test
        void isAssignment() {
            BinaryExpr assignExpr = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    createTestExpression(),
                    createTestExpression(),
                    PrimitiveSourceType.INT
            );
            BinaryExpr addExpr = new BinaryExpr(
                    BinaryOperator.ADD,
                    createTestExpression(),
                    createTestExpression(),
                    PrimitiveSourceType.INT
            );

            assertTrue(assignExpr.isAssignment());
            assertFalse(addExpr.isAssignment());
        }

        @Test
        void isComparison() {
            BinaryExpr eqExpr = new BinaryExpr(
                    BinaryOperator.EQ,
                    createTestExpression(),
                    createTestExpression(),
                    PrimitiveSourceType.BOOLEAN
            );
            BinaryExpr addExpr = new BinaryExpr(
                    BinaryOperator.ADD,
                    createTestExpression(),
                    createTestExpression(),
                    PrimitiveSourceType.INT
            );

            assertTrue(eqExpr.isComparison());
            assertFalse(addExpr.isComparison());
        }

        @Test
        void isLogical() {
            BinaryExpr andExpr = new BinaryExpr(
                    BinaryOperator.AND,
                    LiteralExpr.ofBoolean(true),
                    LiteralExpr.ofBoolean(false),
                    PrimitiveSourceType.BOOLEAN
            );
            BinaryExpr addExpr = new BinaryExpr(
                    BinaryOperator.ADD,
                    createTestExpression(),
                    createTestExpression(),
                    PrimitiveSourceType.INT
            );

            assertTrue(andExpr.isLogical());
            assertFalse(addExpr.isLogical());
        }

        @Test
        void parentChildRelationships() {
            Expression left = LiteralExpr.ofInt(5);
            Expression right = LiteralExpr.ofInt(3);

            BinaryExpr expr = new BinaryExpr(BinaryOperator.ADD, left, right, PrimitiveSourceType.INT);

            assertEquals(expr, left.getParent());
            assertEquals(expr, right.getParent());
        }

        @Test
        void setters() {
            BinaryExpr expr = new BinaryExpr(
                    BinaryOperator.ADD,
                    LiteralExpr.ofInt(1),
                    LiteralExpr.ofInt(2),
                    PrimitiveSourceType.INT
            );

            BinaryOperator newOp = BinaryOperator.SUB;
            Expression newLeft = LiteralExpr.ofInt(10);
            Expression newRight = LiteralExpr.ofInt(5);

            expr.setOperator(newOp);
            expr.setLeft(newLeft);
            expr.setRight(newRight);

            assertEquals(newOp, expr.getOperator());
            assertEquals(newLeft, expr.getLeft());
            assertEquals(newRight, expr.getRight());
        }

        @Test
        void nullOperatorThrows() {
            assertThrows(NullPointerException.class, () ->
                    new BinaryExpr(null, createTestExpression(), createTestExpression(), PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullLeftThrows() {
            assertThrows(NullPointerException.class, () ->
                    new BinaryExpr(BinaryOperator.ADD, null, createTestExpression(), PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullRightThrows() {
            assertThrows(NullPointerException.class, () ->
                    new BinaryExpr(BinaryOperator.ADD, createTestExpression(), null, PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new BinaryExpr(BinaryOperator.ADD, createTestExpression(), createTestExpression(), null)
            );
        }

        @Test
        void visitorPattern() {
            BinaryExpr expr = new BinaryExpr(
                    BinaryOperator.ADD,
                    createTestExpression(),
                    createTestExpression(),
                    PrimitiveSourceType.INT
            );
            TestVisitor visitor = new TestVisitor();

            String result = expr.accept(visitor);

            assertEquals("visitBinary", result);
        }

        @Test
        void toStringFormat() {
            BinaryExpr expr = new BinaryExpr(
                    BinaryOperator.ADD,
                    LiteralExpr.ofInt(5),
                    LiteralExpr.ofInt(3),
                    PrimitiveSourceType.INT
            );

            String str = expr.toString();

            assertTrue(str.contains("5"));
            assertTrue(str.contains("+"));
            assertTrue(str.contains("3"));
            assertTrue(str.contains("("));
            assertTrue(str.contains(")"));
        }
    }

    @Nested
    class CastExprTests {

        @Test
        void basicConstructor() {
            SourceType targetType = PrimitiveSourceType.INT;
            Expression expression = LiteralExpr.ofDouble(3.14);

            CastExpr expr = new CastExpr(targetType, expression);

            assertNotNull(expr);
            assertEquals(targetType, expr.getTargetType());
            assertEquals(expression, expr.getExpression());
            assertEquals(targetType, expr.getType());
            assertEquals(SourceLocation.UNKNOWN, expr.getLocation());
        }

        @Test
        void withLocation() {
            SourceLocation location = createTestLocation();

            CastExpr expr = new CastExpr(
                    PrimitiveSourceType.INT,
                    LiteralExpr.ofDouble(3.14),
                    location
            );

            assertEquals(location, expr.getLocation());
        }

        @Test
        void getTypeReturnsCastType() {
            SourceType targetType = PrimitiveSourceType.LONG;

            CastExpr expr = new CastExpr(targetType, createTestExpression());

            assertEquals(targetType, expr.getType());
        }

        @Test
        void parentChildRelationship() {
            Expression expression = LiteralExpr.ofDouble(3.14);

            CastExpr expr = new CastExpr(PrimitiveSourceType.INT, expression);

            assertEquals(expr, expression.getParent());
        }

        @Test
        void setExpression() {
            CastExpr expr = new CastExpr(PrimitiveSourceType.INT, LiteralExpr.ofDouble(1.0));

            Expression newExpr = LiteralExpr.ofDouble(2.0);
            expr.setExpression(newExpr);

            assertEquals(newExpr, expr.getExpression());
        }

        @Test
        void nullTargetTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new CastExpr(null, createTestExpression())
            );
        }

        @Test
        void nullExpressionThrows() {
            assertThrows(NullPointerException.class, () ->
                    new CastExpr(PrimitiveSourceType.INT, null)
            );
        }

        @Test
        void visitorPattern() {
            CastExpr expr = new CastExpr(PrimitiveSourceType.INT, createTestExpression());
            TestVisitor visitor = new TestVisitor();

            String result = expr.accept(visitor);

            assertEquals("visitCast", result);
        }

        @Test
        void toStringFormat() {
            CastExpr expr = new CastExpr(PrimitiveSourceType.INT, LiteralExpr.ofDouble(3.14));

            String str = expr.toString();

            assertTrue(str.contains("("));
            assertTrue(str.contains("int"));
            assertTrue(str.contains(")"));
            assertTrue(str.contains("3.14"));
        }
    }

    @Nested
    class ClassExprTests {

        @Test
        void basicConstructor() {
            SourceType classType = PrimitiveSourceType.INT;

            ClassExpr expr = new ClassExpr(classType);

            assertNotNull(expr);
            assertEquals(classType, expr.getClassType());
            assertEquals(SourceLocation.UNKNOWN, expr.getLocation());
        }

        @Test
        void withLocation() {
            SourceLocation location = createTestLocation();

            ClassExpr expr = new ClassExpr(PrimitiveSourceType.INT, location);

            assertEquals(location, expr.getLocation());
        }

        @Test
        void typeIsClassType() {
            ClassExpr expr = new ClassExpr(PrimitiveSourceType.INT);

            assertNotNull(expr.getType());
            assertTrue(expr.getType() instanceof ReferenceSourceType);
        }

        @Test
        void referenceType() {
            SourceType classType = ReferenceSourceType.STRING;

            ClassExpr expr = new ClassExpr(classType);

            assertEquals(classType, expr.getClassType());
        }

        @Test
        void nullClassTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ClassExpr(null)
            );
        }

        @Test
        void visitorPattern() {
            ClassExpr expr = new ClassExpr(PrimitiveSourceType.INT);
            TestVisitor visitor = new TestVisitor();

            String result = expr.accept(visitor);

            assertEquals("visitClass", result);
        }

        @Test
        void toStringFormat() {
            ClassExpr expr = new ClassExpr(PrimitiveSourceType.INT);

            String str = expr.toString();

            assertTrue(str.contains("int"));
            assertTrue(str.contains(".class"));
        }
    }

    @Nested
    class FieldAccessExprTests {

        @Test
        void basicConstructor() {
            Expression receiver = createTestExpression();
            String fieldName = "value";
            String ownerClass = "java/lang/String";
            SourceType type = PrimitiveSourceType.INT;

            FieldAccessExpr expr = new FieldAccessExpr(receiver, fieldName, ownerClass, false, type);

            assertNotNull(expr);
            assertEquals(receiver, expr.getReceiver());
            assertEquals(fieldName, expr.getFieldName());
            assertEquals(ownerClass, expr.getOwnerClass());
            assertFalse(expr.isStatic());
            assertEquals(type, expr.getType());
        }

        @Test
        void staticFieldAccess() {
            FieldAccessExpr expr = FieldAccessExpr.staticField(
                    "java/lang/System",
                    "out",
                    ReferenceSourceType.OBJECT
            );

            assertNull(expr.getReceiver());
            assertTrue(expr.isStatic());
            assertEquals("out", expr.getFieldName());
        }

        @Test
        void instanceFieldAccess() {
            Expression receiver = createTestExpression();

            FieldAccessExpr expr = FieldAccessExpr.instanceField(
                    receiver,
                    "length",
                    "java/lang/String",
                    PrimitiveSourceType.INT
            );

            assertEquals(receiver, expr.getReceiver());
            assertFalse(expr.isStatic());
            assertEquals("length", expr.getFieldName());
        }

        @Test
        void parentChildRelationship() {
            Expression receiver = createTestExpression();

            FieldAccessExpr expr = new FieldAccessExpr(
                    receiver,
                    "field",
                    "Owner",
                    false,
                    PrimitiveSourceType.INT
            );

            assertEquals(expr, receiver.getParent());
        }

        @Test
        void nullReceiverAllowedForStatic() {
            FieldAccessExpr expr = new FieldAccessExpr(
                    null,
                    "staticField",
                    "Owner",
                    true,
                    PrimitiveSourceType.INT
            );

            assertNull(expr.getReceiver());
        }

        @Test
        void setters() {
            FieldAccessExpr expr = new FieldAccessExpr(
                    createTestExpression(),
                    "field",
                    "Owner",
                    false,
                    PrimitiveSourceType.INT
            );

            Expression newReceiver = LiteralExpr.ofString("new");
            String newFieldName = "newField";

            expr.setReceiver(newReceiver);
            expr.setFieldName(newFieldName);

            assertEquals(newReceiver, expr.getReceiver());
            assertEquals(newFieldName, expr.getFieldName());
        }

        @Test
        void nullFieldNameThrows() {
            assertThrows(NullPointerException.class, () ->
                    new FieldAccessExpr(createTestExpression(), null, "Owner", false, PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullOwnerClassThrows() {
            assertThrows(NullPointerException.class, () ->
                    new FieldAccessExpr(createTestExpression(), "field", null, false, PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new FieldAccessExpr(createTestExpression(), "field", "Owner", false, null)
            );
        }

        @Test
        void visitorPattern() {
            FieldAccessExpr expr = FieldAccessExpr.instanceField(
                    createTestExpression(),
                    "field",
                    "Owner",
                    PrimitiveSourceType.INT
            );
            TestVisitor visitor = new TestVisitor();

            String result = expr.accept(visitor);

            assertEquals("visitFieldAccess", result);
        }

        @Test
        void toStringInstanceAccess() {
            FieldAccessExpr expr = FieldAccessExpr.instanceField(
                    LiteralExpr.ofString("obj"),
                    "field",
                    "Owner",
                    PrimitiveSourceType.INT
            );

            String str = expr.toString();

            assertTrue(str.contains("obj"));
            assertTrue(str.contains("."));
            assertTrue(str.contains("field"));
        }

        @Test
        void toStringStaticAccess() {
            FieldAccessExpr expr = FieldAccessExpr.staticField(
                    "java/lang/System",
                    "out",
                    ReferenceSourceType.OBJECT
            );

            String str = expr.toString();

            assertTrue(str.contains("."));
            assertTrue(str.contains("out"));
        }
    }

    @Nested
    class InstanceOfExprTests {

        @Test
        void basicConstructor() {
            Expression expression = createTestExpression();
            SourceType checkType = ReferenceSourceType.STRING;

            InstanceOfExpr expr = new InstanceOfExpr(expression, checkType);

            assertNotNull(expr);
            assertEquals(expression, expr.getExpression());
            assertEquals(checkType, expr.getCheckType());
            assertFalse(expr.hasPatternVariable());
            assertEquals(PrimitiveSourceType.BOOLEAN, expr.getType());
        }

        @Test
        void withPatternVariable() {
            Expression expression = createTestExpression();
            SourceType checkType = ReferenceSourceType.STRING;
            String pattern = "str";

            InstanceOfExpr expr = new InstanceOfExpr(expression, checkType, pattern);

            assertTrue(expr.hasPatternVariable());
            assertEquals(pattern, expr.getPatternVariable());
        }

        @Test
        void withLocation() {
            SourceLocation location = createTestLocation();

            InstanceOfExpr expr = new InstanceOfExpr(
                    createTestExpression(),
                    ReferenceSourceType.STRING,
                    null,
                    location
            );

            assertEquals(location, expr.getLocation());
        }

        @Test
        void typeIsBoolean() {
            InstanceOfExpr expr = new InstanceOfExpr(
                    createTestExpression(),
                    ReferenceSourceType.STRING
            );

            assertEquals(PrimitiveSourceType.BOOLEAN, expr.getType());
        }

        @Test
        void parentChildRelationship() {
            Expression expression = createTestExpression();

            InstanceOfExpr expr = new InstanceOfExpr(expression, ReferenceSourceType.STRING);

            assertEquals(expr, expression.getParent());
        }

        @Test
        void setters() {
            InstanceOfExpr expr = new InstanceOfExpr(
                    createTestExpression(),
                    ReferenceSourceType.STRING
            );

            Expression newExpr = LiteralExpr.ofString("test");
            String newPattern = "s";

            expr.setExpression(newExpr);
            expr.setPatternVariable(newPattern);

            assertEquals(newExpr, expr.getExpression());
            assertEquals(newPattern, expr.getPatternVariable());
            assertTrue(expr.hasPatternVariable());
        }

        @Test
        void nullExpressionThrows() {
            assertThrows(NullPointerException.class, () ->
                    new InstanceOfExpr(null, ReferenceSourceType.STRING)
            );
        }

        @Test
        void nullCheckTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new InstanceOfExpr(createTestExpression(), null)
            );
        }

        @Test
        void visitorPattern() {
            InstanceOfExpr expr = new InstanceOfExpr(createTestExpression(), ReferenceSourceType.STRING);
            TestVisitor visitor = new TestVisitor();

            String result = expr.accept(visitor);

            assertEquals("visitInstanceOf", result);
        }

        @Test
        void toStringWithoutPattern() {
            InstanceOfExpr expr = new InstanceOfExpr(
                    LiteralExpr.ofString("obj"),
                    ReferenceSourceType.STRING
            );

            String str = expr.toString();

            assertTrue(str.contains("obj"));
            assertTrue(str.contains("instanceof"));
            assertTrue(str.contains("String"));
        }

        @Test
        void toStringWithPattern() {
            InstanceOfExpr expr = new InstanceOfExpr(
                    LiteralExpr.ofString("obj"),
                    ReferenceSourceType.STRING,
                    "str"
            );

            String str = expr.toString();

            assertTrue(str.contains("obj"));
            assertTrue(str.contains("instanceof"));
            assertTrue(str.contains("String"));
            assertTrue(str.contains("str"));
        }
    }

    @Nested
    class LiteralExprTests {

        @Test
        void ofInt() {
            LiteralExpr expr = LiteralExpr.ofInt(42);

            assertEquals(42, expr.getValue());
            assertEquals(PrimitiveSourceType.INT, expr.getType());
            assertFalse(expr.isNull());
            assertTrue(expr.isNumeric());
            assertFalse(expr.isString());
        }

        @Test
        void ofLong() {
            LiteralExpr expr = LiteralExpr.ofLong(100L);

            assertEquals(100L, expr.getValue());
            assertEquals(PrimitiveSourceType.LONG, expr.getType());
            assertTrue(expr.isNumeric());
        }

        @Test
        void ofFloat() {
            LiteralExpr expr = LiteralExpr.ofFloat(3.14f);

            assertEquals(3.14f, expr.getValue());
            assertEquals(PrimitiveSourceType.FLOAT, expr.getType());
            assertTrue(expr.isNumeric());
        }

        @Test
        void ofDouble() {
            LiteralExpr expr = LiteralExpr.ofDouble(2.718);

            assertEquals(2.718, expr.getValue());
            assertEquals(PrimitiveSourceType.DOUBLE, expr.getType());
            assertTrue(expr.isNumeric());
        }

        @Test
        void ofBoolean() {
            LiteralExpr expr = LiteralExpr.ofBoolean(true);

            assertEquals(true, expr.getValue());
            assertEquals(PrimitiveSourceType.BOOLEAN, expr.getType());
            assertFalse(expr.isNumeric());
        }

        @Test
        void ofChar() {
            LiteralExpr expr = LiteralExpr.ofChar('A');

            assertEquals('A', expr.getValue());
            assertEquals(PrimitiveSourceType.CHAR, expr.getType());
        }

        @Test
        void ofString() {
            LiteralExpr expr = LiteralExpr.ofString("hello");

            assertEquals("hello", expr.getValue());
            assertEquals(ReferenceSourceType.STRING, expr.getType());
            assertTrue(expr.isString());
            assertFalse(expr.isNull());
        }

        @Test
        void ofNull() {
            LiteralExpr expr = LiteralExpr.ofNull();

            assertNull(expr.getValue());
            assertTrue(expr.isNull());
            assertFalse(expr.isString());
            assertFalse(expr.isNumeric());
        }

        @Test
        void withLocation() {
            SourceLocation location = createTestLocation();

            LiteralExpr expr = new LiteralExpr(42, PrimitiveSourceType.INT, location);

            assertEquals(location, expr.getLocation());
        }

        @Test
        void setters() {
            LiteralExpr expr = LiteralExpr.ofInt(1);

            expr.setValue(2);
            expr.setType(PrimitiveSourceType.LONG);

            assertEquals(2, expr.getValue());
            assertEquals(PrimitiveSourceType.LONG, expr.getType());
        }

        @Test
        void visitorPattern() {
            LiteralExpr expr = LiteralExpr.ofInt(42);
            TestVisitor visitor = new TestVisitor();

            String result = expr.accept(visitor);

            assertEquals("visitLiteral", result);
        }

        @Test
        void toStringNull() {
            LiteralExpr expr = LiteralExpr.ofNull();

            assertEquals("null", expr.toString());
        }

        @Test
        void toStringInt() {
            LiteralExpr expr = LiteralExpr.ofInt(42);

            assertEquals("42", expr.toString());
        }

        @Test
        void toStringLong() {
            LiteralExpr expr = LiteralExpr.ofLong(100L);

            assertEquals("100L", expr.toString());
        }

        @Test
        void toStringFloat() {
            LiteralExpr expr = LiteralExpr.ofFloat(3.14f);

            assertTrue(expr.toString().contains("3.14"));
            assertTrue(expr.toString().contains("f"));
        }

        @Test
        void toStringDouble() {
            LiteralExpr expr = LiteralExpr.ofDouble(2.718);

            assertTrue(expr.toString().contains("2.718"));
            assertTrue(expr.toString().contains("d"));
        }

        @Test
        void toStringBoolean() {
            LiteralExpr trueExpr = LiteralExpr.ofBoolean(true);
            LiteralExpr falseExpr = LiteralExpr.ofBoolean(false);

            assertEquals("true", trueExpr.toString());
            assertEquals("false", falseExpr.toString());
        }

        @Test
        void toStringChar() {
            LiteralExpr expr = LiteralExpr.ofChar('A');

            assertEquals("'A'", expr.toString());
        }

        @Test
        void toStringString() {
            LiteralExpr expr = LiteralExpr.ofString("hello");

            assertEquals("\"hello\"", expr.toString());
        }

        @Test
        void toStringEscapesSpecialChars() {
            LiteralExpr expr = LiteralExpr.ofString("line1\nline2");

            assertTrue(expr.toString().contains("\\n"));
        }
    }

    @Nested
    class VarRefExprTests {

        @Test
        void basicConstructor() {
            String name = "variable";
            SourceType type = PrimitiveSourceType.INT;

            VarRefExpr expr = new VarRefExpr(name, type);

            assertNotNull(expr);
            assertEquals(name, expr.getName());
            assertEquals(type, expr.getType());
            assertFalse(expr.hasSSAValue());
            assertNull(expr.getSsaValue());
        }

        @Test
        void withSSAValue() {
            SSAValue ssaValue = null;
            VarRefExpr expr = new VarRefExpr("var", PrimitiveSourceType.INT, ssaValue);

            assertFalse(expr.hasSSAValue());
        }

        @Test
        void withLocation() {
            SourceLocation location = createTestLocation();

            VarRefExpr expr = new VarRefExpr(
                    "var",
                    PrimitiveSourceType.INT,
                    null,
                    location
            );

            assertEquals(location, expr.getLocation());
        }

        @Test
        void setName() {
            VarRefExpr expr = new VarRefExpr("var1", PrimitiveSourceType.INT);

            expr.setName("var2");

            assertEquals("var2", expr.getName());
        }

        @Test
        void nullNameThrows() {
            assertThrows(NullPointerException.class, () ->
                    new VarRefExpr(null, PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new VarRefExpr("var", null)
            );
        }

        @Test
        void visitorPattern() {
            VarRefExpr expr = new VarRefExpr("var", PrimitiveSourceType.INT);
            TestVisitor visitor = new TestVisitor();

            String result = expr.accept(visitor);

            assertEquals("visitVarRef", result);
        }

        @Test
        void toStringFormat() {
            VarRefExpr expr = new VarRefExpr("myVariable", PrimitiveSourceType.INT);

            assertEquals("myVariable", expr.toString());
        }
    }

    @Nested
    class MethodCallExprTests {

        @Test
        void basicConstructor() {
            Expression receiver = createTestExpression();
            String methodName = "toString";
            String ownerClass = "java/lang/Object";
            List<Expression> arguments = Arrays.asList(createTestExpression());
            SourceType returnType = ReferenceSourceType.STRING;

            MethodCallExpr expr = new MethodCallExpr(
                    receiver,
                    methodName,
                    ownerClass,
                    arguments,
                    false,
                    returnType
            );

            assertNotNull(expr);
            assertEquals(receiver, expr.getReceiver());
            assertEquals(methodName, expr.getMethodName());
            assertEquals(ownerClass, expr.getOwnerClass());
            assertEquals(1, expr.getArgumentCount());
            assertFalse(expr.isStatic());
            assertEquals(returnType, expr.getType());
        }

        @Test
        void staticCall() {
            MethodCallExpr expr = MethodCallExpr.staticCall(
                    "java/lang/Math",
                    "abs",
                    Arrays.asList(LiteralExpr.ofInt(-5)),
                    PrimitiveSourceType.INT
            );

            assertNull(expr.getReceiver());
            assertTrue(expr.isStatic());
            assertEquals("abs", expr.getMethodName());
            assertEquals(1, expr.getArgumentCount());
        }

        @Test
        void instanceCall() {
            Expression receiver = createTestExpression();

            MethodCallExpr expr = MethodCallExpr.instanceCall(
                    receiver,
                    "length",
                    "java/lang/String",
                    Collections.emptyList(),
                    PrimitiveSourceType.INT
            );

            assertEquals(receiver, expr.getReceiver());
            assertFalse(expr.isStatic());
            assertEquals(0, expr.getArgumentCount());
        }

        @Test
        void addArgument() {
            MethodCallExpr expr = MethodCallExpr.staticCall(
                    "Owner",
                    "method",
                    Collections.emptyList(),
                    PrimitiveSourceType.INT
            );

            Expression arg = createTestExpression();
            expr.addArgument(arg);

            assertEquals(1, expr.getArgumentCount());
            assertEquals(expr, arg.getParent());
        }

        @Test
        void nullArguments() {
            MethodCallExpr expr = new MethodCallExpr(
                    createTestExpression(),
                    "method",
                    "Owner",
                    null,
                    false,
                    PrimitiveSourceType.INT
            );

            assertNotNull(expr.getArguments());
            assertEquals(0, expr.getArgumentCount());
        }

        @Test
        void nullReturnType() {
            MethodCallExpr expr = new MethodCallExpr(
                    createTestExpression(),
                    "method",
                    "Owner",
                    Collections.emptyList(),
                    false,
                    null
            );

            assertNotNull(expr.getType());
        }

        @Test
        void parentChildRelationships() {
            Expression receiver = createTestExpression();
            Expression arg1 = LiteralExpr.ofInt(1);
            Expression arg2 = LiteralExpr.ofInt(2);

            MethodCallExpr expr = new MethodCallExpr(
                    receiver,
                    "method",
                    "Owner",
                    Arrays.asList(arg1, arg2),
                    false,
                    PrimitiveSourceType.INT
            );

            assertEquals(expr, receiver.getParent());
            assertEquals(expr, arg1.getParent());
            assertEquals(expr, arg2.getParent());
        }

        @Test
        void setters() {
            MethodCallExpr expr = MethodCallExpr.staticCall(
                    "Owner",
                    "method",
                    Collections.emptyList(),
                    PrimitiveSourceType.INT
            );

            Expression newReceiver = createTestExpression();
            String newMethodName = "newMethod";

            expr.setReceiver(newReceiver);
            expr.setMethodName(newMethodName);

            assertEquals(newReceiver, expr.getReceiver());
            assertEquals(newMethodName, expr.getMethodName());
        }

        @Test
        void nullMethodNameThrows() {
            assertThrows(NullPointerException.class, () ->
                    new MethodCallExpr(
                            createTestExpression(),
                            null,
                            "Owner",
                            Collections.emptyList(),
                            false,
                            PrimitiveSourceType.INT
                    )
            );
        }

        @Test
        void nullOwnerClassThrows() {
            assertThrows(NullPointerException.class, () ->
                    new MethodCallExpr(
                            createTestExpression(),
                            "method",
                            null,
                            Collections.emptyList(),
                            false,
                            PrimitiveSourceType.INT
                    )
            );
        }

        @Test
        void visitorPattern() {
            MethodCallExpr expr = MethodCallExpr.staticCall(
                    "Owner",
                    "method",
                    Collections.emptyList(),
                    PrimitiveSourceType.INT
            );
            TestVisitor visitor = new TestVisitor();

            String result = expr.accept(visitor);

            assertEquals("visitMethodCall", result);
        }

        @Test
        void toStringStaticCall() {
            MethodCallExpr expr = MethodCallExpr.staticCall(
                    "java/lang/Math",
                    "abs",
                    Arrays.asList(LiteralExpr.ofInt(-5)),
                    PrimitiveSourceType.INT
            );

            String str = expr.toString();

            assertTrue(str.contains("."));
            assertTrue(str.contains("abs"));
            assertTrue(str.contains("("));
            assertTrue(str.contains(")"));
        }

        @Test
        void toStringInstanceCall() {
            MethodCallExpr expr = MethodCallExpr.instanceCall(
                    LiteralExpr.ofString("str"),
                    "length",
                    "java/lang/String",
                    Collections.emptyList(),
                    PrimitiveSourceType.INT
            );

            String str = expr.toString();

            assertTrue(str.contains("str"));
            assertTrue(str.contains("."));
            assertTrue(str.contains("length"));
            assertTrue(str.contains("("));
            assertTrue(str.contains(")"));
        }

        @Test
        void toStringMultipleArgs() {
            MethodCallExpr expr = MethodCallExpr.staticCall(
                    "Owner",
                    "method",
                    Arrays.asList(LiteralExpr.ofInt(1), LiteralExpr.ofInt(2)),
                    PrimitiveSourceType.INT
            );

            String str = expr.toString();

            assertTrue(str.contains("1"));
            assertTrue(str.contains("2"));
            assertTrue(str.contains(","));
        }
    }

    @Nested
    class NewExprTests {

        @Test
        void basicConstructor() {
            String className = "java/lang/String";

            NewExpr expr = new NewExpr(className);

            assertNotNull(expr);
            assertEquals(className, expr.getClassName());
            assertEquals(0, expr.getArgumentCount());
            assertEquals(SourceLocation.UNKNOWN, expr.getLocation());
        }

        @Test
        void withArguments() {
            String className = "java/lang/StringBuilder";
            List<Expression> arguments = Arrays.asList(LiteralExpr.ofString("initial"));

            NewExpr expr = new NewExpr(className, arguments);

            assertEquals(1, expr.getArgumentCount());
            assertEquals(arguments.get(0), expr.getArguments().get(0));
        }

        @Test
        void withTypeAndLocation() {
            String className = "Owner";
            SourceType type = new ReferenceSourceType(className);
            SourceLocation location = createTestLocation();

            NewExpr expr = new NewExpr(className, Collections.emptyList(), type, location);

            assertEquals(type, expr.getType());
            assertEquals(location, expr.getLocation());
        }

        @Test
        void addArgument() {
            NewExpr expr = new NewExpr("Owner");

            Expression arg = createTestExpression();
            expr.addArgument(arg);

            assertEquals(1, expr.getArgumentCount());
            assertEquals(expr, arg.getParent());
        }

        @Test
        void nullArguments() {
            NewExpr expr = new NewExpr("Owner", null);

            assertNotNull(expr.getArguments());
            assertEquals(0, expr.getArgumentCount());
        }

        @Test
        void nullType() {
            NewExpr expr = new NewExpr("Owner", Collections.emptyList(), null);

            assertNotNull(expr.getType());
        }

        @Test
        void parentChildRelationships() {
            Expression arg1 = LiteralExpr.ofInt(1);
            Expression arg2 = LiteralExpr.ofString("test");

            NewExpr expr = new NewExpr("Owner", Arrays.asList(arg1, arg2));

            assertEquals(expr, arg1.getParent());
            assertEquals(expr, arg2.getParent());
        }

        @Test
        void nullClassNameThrows() {
            assertThrows(NullPointerException.class, () ->
                    new NewExpr(null)
            );
        }

        @Test
        void visitorPattern() {
            NewExpr expr = new NewExpr("Owner");
            TestVisitor visitor = new TestVisitor();

            String result = expr.accept(visitor);

            assertEquals("visitNew", result);
        }

        @Test
        void toStringNoArgs() {
            NewExpr expr = new NewExpr("java/lang/Object");

            String str = expr.toString();

            assertTrue(str.contains("new"));
            assertTrue(str.contains("("));
            assertTrue(str.contains(")"));
        }

        @Test
        void toStringWithArgs() {
            NewExpr expr = new NewExpr(
                    "Owner",
                    Arrays.asList(LiteralExpr.ofInt(1), LiteralExpr.ofString("test"))
            );

            String str = expr.toString();

            assertTrue(str.contains("new"));
            assertTrue(str.contains("("));
            assertTrue(str.contains("1"));
            assertTrue(str.contains(","));
            assertTrue(str.contains("test"));
            assertTrue(str.contains(")"));
        }
    }

    @Nested
    class LambdaExprTests {

        @Test
        void constructorWithExpressionBody() {
            List<LambdaParameter> params = Arrays.asList(
                    LambdaParameter.implicit("x", PrimitiveSourceType.INT),
                    LambdaParameter.implicit("y", PrimitiveSourceType.INT)
            );
            Expression body = LiteralExpr.ofInt(10);
            SourceType type = new ReferenceSourceType("java/util/function/BiFunction");
            SourceLocation location = createTestLocation();

            LambdaExpr lambda = new LambdaExpr(params, body, type, location);

            assertEquals(2, lambda.getParameters().size());
            assertSame(body, lambda.getBody());
            assertSame(type, lambda.getType());
            assertSame(location, lambda.getLocation());
            assertSame(lambda, body.getParent());
        }

        @Test
        void constructorWithBlockBody() {
            List<LambdaParameter> params = Collections.singletonList(
                    LambdaParameter.explicit(PrimitiveSourceType.INT, "x")
            );
            com.tonic.analysis.source.ast.stmt.Statement body =
                    new com.tonic.analysis.source.ast.stmt.BlockStmt(
                            Collections.singletonList(new com.tonic.analysis.source.ast.stmt.ReturnStmt())
                    );
            SourceType type = new ReferenceSourceType("java/util/function/Function");

            LambdaExpr lambda = new LambdaExpr(params, body, type);

            assertEquals(1, lambda.getParameterCount());
            assertTrue(lambda.isBlockBody());
            assertFalse(lambda.isExpressionBody());
            assertSame(body, lambda.getBody());
            assertEquals(SourceLocation.UNKNOWN, lambda.getLocation());
        }

        @Test
        void emptyParameterList() {
            Expression body = LiteralExpr.ofInt(42);
            SourceType type = new ReferenceSourceType("java/util/function/Supplier");

            LambdaExpr lambda = new LambdaExpr(null, body, type);

            assertEquals(0, lambda.getParameterCount());
            assertTrue(lambda.getParameters().isEmpty());
        }

        @Test
        void isExpressionBody() {
            Expression body = LiteralExpr.ofInt(10);
            LambdaExpr lambda = new LambdaExpr(Collections.emptyList(), body,
                    new ReferenceSourceType("java/util/function/Supplier"));

            assertTrue(lambda.isExpressionBody());
            assertFalse(lambda.isBlockBody());
            assertSame(body, lambda.getExpressionBody());
        }

        @Test
        void isBlockBody() {
            com.tonic.analysis.source.ast.stmt.Statement body =
                    new com.tonic.analysis.source.ast.stmt.BlockStmt(Collections.emptyList());
            LambdaExpr lambda = new LambdaExpr(Collections.emptyList(), body,
                    new ReferenceSourceType("java/lang/Runnable"));

            assertTrue(lambda.isBlockBody());
            assertFalse(lambda.isExpressionBody());
            assertSame(body, lambda.getBlockBody());
        }

        @Test
        void getExpressionBodyThrowsForBlockBody() {
            com.tonic.analysis.source.ast.stmt.Statement body =
                    new com.tonic.analysis.source.ast.stmt.BlockStmt(Collections.emptyList());
            LambdaExpr lambda = new LambdaExpr(Collections.emptyList(), body,
                    new ReferenceSourceType("java/lang/Runnable"));

            assertThrows(IllegalStateException.class, lambda::getExpressionBody);
        }

        @Test
        void getBlockBodyThrowsForExpressionBody() {
            Expression body = LiteralExpr.ofInt(10);
            LambdaExpr lambda = new LambdaExpr(Collections.emptyList(), body,
                    new ReferenceSourceType("java/util/function/Supplier"));

            assertThrows(IllegalStateException.class, lambda::getBlockBody);
        }

        @Test
        void hasImplicitParameterTypes() {
            List<LambdaParameter> implicitParams = Arrays.asList(
                    LambdaParameter.implicit("x", PrimitiveSourceType.INT),
                    LambdaParameter.implicit("y", PrimitiveSourceType.INT)
            );
            LambdaExpr lambda = new LambdaExpr(implicitParams, LiteralExpr.ofInt(10),
                    new ReferenceSourceType("java/util/function/BiFunction"));

            assertTrue(lambda.hasImplicitParameterTypes());
        }

        @Test
        void hasExplicitParameterTypes() {
            List<LambdaParameter> explicitParams = Collections.singletonList(
                    LambdaParameter.explicit(PrimitiveSourceType.INT, "x")
            );
            LambdaExpr lambda = new LambdaExpr(explicitParams, LiteralExpr.ofInt(10),
                    new ReferenceSourceType("java/util/function/Function"));

            assertFalse(lambda.hasImplicitParameterTypes());
        }

        @Test
        void setBody() {
            Expression originalBody = LiteralExpr.ofInt(10);
            LambdaExpr lambda = new LambdaExpr(Collections.emptyList(), originalBody,
                    new ReferenceSourceType("java/util/function/Supplier"));

            Expression newBody = LiteralExpr.ofInt(20);
            lambda.setBody(newBody);

            assertSame(newBody, lambda.getBody());
        }

        @Test
        void visitorPattern() {
            LambdaExpr lambda = new LambdaExpr(Collections.emptyList(), LiteralExpr.ofInt(10),
                    new ReferenceSourceType("java/util/function/Supplier"));

            String result = lambda.accept(new TestVisitor());

            assertEquals("visitLambda", result);
        }

        @Test
        void toStringWithSingleImplicitParameter() {
            List<LambdaParameter> params = Collections.singletonList(
                    LambdaParameter.implicit("x", PrimitiveSourceType.INT)
            );
            Expression body = LiteralExpr.ofInt(10);
            LambdaExpr lambda = new LambdaExpr(params, body,
                    new ReferenceSourceType("java/util/function/Function"));

            String result = lambda.toString();

            assertTrue(result.contains("x"));
            assertTrue(result.contains("->"));
        }

        @Test
        void toStringWithMultipleParameters() {
            List<LambdaParameter> params = Arrays.asList(
                    LambdaParameter.explicit(PrimitiveSourceType.INT, "x"),
                    LambdaParameter.explicit(PrimitiveSourceType.INT, "y")
            );
            Expression body = LiteralExpr.ofInt(10);
            LambdaExpr lambda = new LambdaExpr(params, body,
                    new ReferenceSourceType("java/util/function/BiFunction"));

            String result = lambda.toString();

            assertTrue(result.contains("("));
            assertTrue(result.contains("x"));
            assertTrue(result.contains("y"));
            assertTrue(result.contains("->"));
        }

        @Test
        void toStringWithBlockBody() {
            com.tonic.analysis.source.ast.stmt.Statement body =
                    new com.tonic.analysis.source.ast.stmt.BlockStmt(Collections.emptyList());
            LambdaExpr lambda = new LambdaExpr(Collections.emptyList(), body,
                    new ReferenceSourceType("java/lang/Runnable"));

            String result = lambda.toString();

            assertTrue(result.contains("->"));
            assertTrue(result.contains("{"));
        }

        @Test
        void nullBodyThrows() {
            assertThrows(NullPointerException.class, () ->
                    new LambdaExpr(Collections.emptyList(), null,
                            new ReferenceSourceType("java/lang/Runnable"))
            );
        }

        @Test
        void nullTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new LambdaExpr(Collections.emptyList(), LiteralExpr.ofInt(10), null)
            );
        }

        @Test
        void parentChildRelationship() {
            Expression body = LiteralExpr.ofInt(10);
            LambdaExpr lambda = new LambdaExpr(Collections.emptyList(), body,
                    new ReferenceSourceType("java/util/function/Supplier"));

            assertSame(lambda, body.getParent());
        }
    }

    @Nested
    class LambdaParameterTests {

        @Test
        void explicitParameter() {
            LambdaParameter param = LambdaParameter.explicit(PrimitiveSourceType.INT, "count");

            assertEquals("count", param.name());
            assertEquals(PrimitiveSourceType.INT, param.type());
            assertFalse(param.implicitType());
        }

        @Test
        void implicitParameter() {
            LambdaParameter param = LambdaParameter.implicit("value", PrimitiveSourceType.DOUBLE);

            assertEquals("value", param.name());
            assertEquals(PrimitiveSourceType.DOUBLE, param.type());
            assertTrue(param.implicitType());
        }

        @Test
        void toJavaSourceExplicit() {
            LambdaParameter param = LambdaParameter.explicit(PrimitiveSourceType.INT, "x");

            String source = param.toJavaSource();

            assertEquals("int x", source);
        }

        @Test
        void toJavaSourceImplicit() {
            LambdaParameter param = LambdaParameter.implicit("x", PrimitiveSourceType.INT);

            String source = param.toJavaSource();

            assertEquals("x", source);
        }

        @Test
        void toJavaSourceImplicitWithNullType() {
            LambdaParameter param = new LambdaParameter("x", null, true);

            String source = param.toJavaSource();

            assertEquals("x", source);
        }

        @Test
        void equalsAndHashCode() {
            LambdaParameter param1 = LambdaParameter.explicit(PrimitiveSourceType.INT, "x");
            LambdaParameter param2 = LambdaParameter.explicit(PrimitiveSourceType.INT, "x");
            LambdaParameter param3 = LambdaParameter.explicit(PrimitiveSourceType.INT, "y");
            LambdaParameter param4 = LambdaParameter.implicit("x", PrimitiveSourceType.INT);

            assertEquals(param1, param2);
            assertNotEquals(param1, param3);
            assertNotEquals(param1, param4);
            assertEquals(param1.hashCode(), param2.hashCode());
        }

        @Test
        void toStringFormat() {
            LambdaParameter param = LambdaParameter.explicit(PrimitiveSourceType.INT, "count");

            String result = param.toString();

            assertTrue(result.contains("count"));
            assertTrue(result.contains("int"));
            assertTrue(result.contains("false"));
        }
    }

    @Nested
    class MethodRefExprTests {

        @Test
        void constructorWithReceiver() {
            Expression receiver = new VarRefExpr("obj", ReferenceSourceType.OBJECT);
            SourceType type = new ReferenceSourceType("java/util/function/Function");
            SourceLocation location = createTestLocation();

            MethodRefExpr methodRef = new MethodRefExpr(receiver, "toString", "java/lang/Object",
                    MethodRefKind.BOUND, type, location);

            assertSame(receiver, methodRef.getReceiver());
            assertEquals("toString", methodRef.getMethodName());
            assertEquals("java/lang/Object", methodRef.getOwnerClass());
            assertEquals(MethodRefKind.BOUND, methodRef.getKind());
            assertSame(type, methodRef.getType());
            assertSame(location, methodRef.getLocation());
            assertSame(methodRef, receiver.getParent());
        }

        @Test
        void constructorWithoutReceiver() {
            SourceType type = new ReferenceSourceType("java/util/function/Function");

            MethodRefExpr methodRef = new MethodRefExpr(null, "parseInt", "java/lang/Integer",
                    MethodRefKind.STATIC, type);

            assertNull(methodRef.getReceiver());
            assertEquals("parseInt", methodRef.getMethodName());
            assertEquals(SourceLocation.UNKNOWN, methodRef.getLocation());
        }

        @Test
        void staticRef() {
            SourceType type = new ReferenceSourceType("java/util/function/Function");

            MethodRefExpr methodRef = MethodRefExpr.staticRef("java/lang/Integer", "parseInt", type);

            assertNull(methodRef.getReceiver());
            assertEquals("parseInt", methodRef.getMethodName());
            assertEquals("java/lang/Integer", methodRef.getOwnerClass());
            assertEquals(MethodRefKind.STATIC, methodRef.getKind());
        }

        @Test
        void instanceRef() {
            SourceType type = new ReferenceSourceType("java/util/function/Function");

            MethodRefExpr methodRef = MethodRefExpr.instanceRef("java/lang/String", "length", type);

            assertNull(methodRef.getReceiver());
            assertEquals("length", methodRef.getMethodName());
            assertEquals("java/lang/String", methodRef.getOwnerClass());
            assertEquals(MethodRefKind.INSTANCE, methodRef.getKind());
        }

        @Test
        void boundRef() {
            Expression receiver = new VarRefExpr("str", ReferenceSourceType.STRING);
            SourceType type = new ReferenceSourceType("java/util/function/Supplier");

            MethodRefExpr methodRef = MethodRefExpr.boundRef(receiver, "length",
                    "java/lang/String", type);

            assertSame(receiver, methodRef.getReceiver());
            assertEquals("length", methodRef.getMethodName());
            assertEquals("java/lang/String", methodRef.getOwnerClass());
            assertEquals(MethodRefKind.BOUND, methodRef.getKind());
        }

        @Test
        void constructorRef() {
            SourceType type = new ReferenceSourceType("java/util/function/Supplier");

            MethodRefExpr methodRef = MethodRefExpr.constructorRef("java/lang/String", type);

            assertNull(methodRef.getReceiver());
            assertEquals("new", methodRef.getMethodName());
            assertEquals("java/lang/String", methodRef.getOwnerClass());
            assertEquals(MethodRefKind.CONSTRUCTOR, methodRef.getKind());
            assertTrue(methodRef.isConstructorRef());
        }

        @Test
        void arrayConstructorRef() {
            SourceType arrayType = new ReferenceSourceType("java/lang/String");

            MethodRefExpr methodRef = MethodRefExpr.arrayConstructorRef(arrayType);

            assertNull(methodRef.getReceiver());
            assertEquals("new", methodRef.getMethodName());
            assertEquals(MethodRefKind.ARRAY_CONSTRUCTOR, methodRef.getKind());
            assertTrue(methodRef.isConstructorRef());
        }

        @Test
        void setters() {
            MethodRefExpr methodRef = MethodRefExpr.staticRef("java/lang/Integer", "parseInt",
                    new ReferenceSourceType("java/util/function/Function"));

            methodRef.setMethodName("valueOf");
            Expression newReceiver = new VarRefExpr("obj", ReferenceSourceType.OBJECT);
            methodRef.setReceiver(newReceiver);

            assertEquals("valueOf", methodRef.getMethodName());
            assertSame(newReceiver, methodRef.getReceiver());
        }

        @Test
        void isConstructorRef() {
            MethodRefExpr constructor = MethodRefExpr.constructorRef("java/lang/String",
                    new ReferenceSourceType("java/util/function/Supplier"));
            MethodRefExpr arrayConstructor = MethodRefExpr.arrayConstructorRef(ReferenceSourceType.STRING);
            MethodRefExpr staticMethod = MethodRefExpr.staticRef("java/lang/Integer", "parseInt",
                    new ReferenceSourceType("java/util/function/Function"));

            assertTrue(constructor.isConstructorRef());
            assertTrue(arrayConstructor.isConstructorRef());
            assertFalse(staticMethod.isConstructorRef());
        }

        @Test
        void visitorPattern() {
            MethodRefExpr methodRef = MethodRefExpr.staticRef("java/lang/Integer", "parseInt",
                    new ReferenceSourceType("java/util/function/Function"));

            String result = methodRef.accept(new TestVisitor());

            assertEquals("visitMethodRef", result);
        }

        @Test
        void toStringStatic() {
            MethodRefExpr methodRef = MethodRefExpr.staticRef("java/lang/Integer", "parseInt",
                    new ReferenceSourceType("java/util/function/Function"));

            String result = methodRef.toString();

            assertTrue(result.contains("::"));
            assertTrue(result.contains("parseInt"));
        }

        @Test
        void toStringBound() {
            Expression receiver = new VarRefExpr("str", ReferenceSourceType.STRING);
            MethodRefExpr methodRef = MethodRefExpr.boundRef(receiver, "length",
                    "java/lang/String", new ReferenceSourceType("java/util/function/Supplier"));

            String result = methodRef.toString();

            assertTrue(result.contains("::"));
            assertTrue(result.contains("length"));
        }

        @Test
        void toStringConstructor() {
            MethodRefExpr methodRef = MethodRefExpr.constructorRef("java/lang/String",
                    new ReferenceSourceType("java/util/function/Supplier"));

            String result = methodRef.toString();

            assertTrue(result.contains("::"));
            assertTrue(result.contains("new"));
        }

        @Test
        void nullMethodNameThrows() {
            assertThrows(NullPointerException.class, () ->
                    new MethodRefExpr(null, null, "java/lang/Object",
                            MethodRefKind.STATIC, new ReferenceSourceType("java/util/function/Function"))
            );
        }

        @Test
        void nullOwnerClassThrows() {
            assertThrows(NullPointerException.class, () ->
                    new MethodRefExpr(null, "method", null,
                            MethodRefKind.STATIC, new ReferenceSourceType("java/util/function/Function"))
            );
        }

        @Test
        void nullKindThrows() {
            assertThrows(NullPointerException.class, () ->
                    new MethodRefExpr(null, "method", "java/lang/Object",
                            null, new ReferenceSourceType("java/util/function/Function"))
            );
        }

        @Test
        void nullTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new MethodRefExpr(null, "method", "java/lang/Object",
                            MethodRefKind.STATIC, null)
            );
        }

        @Test
        void parentChildRelationship() {
            Expression receiver = new VarRefExpr("obj", ReferenceSourceType.OBJECT);
            MethodRefExpr methodRef = MethodRefExpr.boundRef(receiver, "toString",
                    "java/lang/Object", new ReferenceSourceType("java/util/function/Supplier"));

            assertSame(methodRef, receiver.getParent());
        }
    }

    @Nested
    class TernaryExprTests {

        @Test
        void basicConstructor() {
            Expression condition = LiteralExpr.ofBoolean(true);
            Expression thenExpr = LiteralExpr.ofInt(10);
            Expression elseExpr = LiteralExpr.ofInt(20);
            SourceType type = PrimitiveSourceType.INT;
            SourceLocation location = createTestLocation();

            TernaryExpr ternary = new TernaryExpr(condition, thenExpr, elseExpr, type, location);

            assertSame(condition, ternary.getCondition());
            assertSame(thenExpr, ternary.getThenExpr());
            assertSame(elseExpr, ternary.getElseExpr());
            assertSame(type, ternary.getType());
            assertSame(location, ternary.getLocation());
            assertSame(ternary, condition.getParent());
            assertSame(ternary, thenExpr.getParent());
            assertSame(ternary, elseExpr.getParent());
        }

        @Test
        void constructorWithoutLocation() {
            Expression condition = LiteralExpr.ofBoolean(true);
            Expression thenExpr = LiteralExpr.ofInt(10);
            Expression elseExpr = LiteralExpr.ofInt(20);
            SourceType type = PrimitiveSourceType.INT;

            TernaryExpr ternary = new TernaryExpr(condition, thenExpr, elseExpr, type);

            assertEquals(SourceLocation.UNKNOWN, ternary.getLocation());
        }

        @Test
        void setters() {
            Expression condition = LiteralExpr.ofBoolean(true);
            TernaryExpr ternary = new TernaryExpr(condition, LiteralExpr.ofInt(10),
                    LiteralExpr.ofInt(20), PrimitiveSourceType.INT);

            Expression newCondition = LiteralExpr.ofBoolean(false);
            Expression newThen = LiteralExpr.ofInt(30);
            Expression newElse = LiteralExpr.ofInt(40);

            ternary.setCondition(newCondition);
            ternary.setThenExpr(newThen);
            ternary.setElseExpr(newElse);

            assertSame(newCondition, ternary.getCondition());
            assertSame(newThen, ternary.getThenExpr());
            assertSame(newElse, ternary.getElseExpr());
        }

        @Test
        void visitorPattern() {
            TernaryExpr ternary = new TernaryExpr(LiteralExpr.ofBoolean(true),
                    LiteralExpr.ofInt(10), LiteralExpr.ofInt(20), PrimitiveSourceType.INT);

            String result = ternary.accept(new TestVisitor());

            assertEquals("visitTernary", result);
        }

        @Test
        void toStringFormat() {
            TernaryExpr ternary = new TernaryExpr(LiteralExpr.ofBoolean(true),
                    LiteralExpr.ofInt(10), LiteralExpr.ofInt(20), PrimitiveSourceType.INT);

            String result = ternary.toString();

            assertTrue(result.contains("?"));
            assertTrue(result.contains(":"));
        }

        @Test
        void nullConditionThrows() {
            assertThrows(NullPointerException.class, () ->
                    new TernaryExpr(null, LiteralExpr.ofInt(10), LiteralExpr.ofInt(20),
                            PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullThenExprThrows() {
            assertThrows(NullPointerException.class, () ->
                    new TernaryExpr(LiteralExpr.ofBoolean(true), null, LiteralExpr.ofInt(20),
                            PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullElseExprThrows() {
            assertThrows(NullPointerException.class, () ->
                    new TernaryExpr(LiteralExpr.ofBoolean(true), LiteralExpr.ofInt(10), null,
                            PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new TernaryExpr(LiteralExpr.ofBoolean(true), LiteralExpr.ofInt(10),
                            LiteralExpr.ofInt(20), null)
            );
        }

        @Test
        void parentChildRelationship() {
            Expression condition = LiteralExpr.ofBoolean(true);
            Expression thenExpr = LiteralExpr.ofInt(10);
            Expression elseExpr = LiteralExpr.ofInt(20);
            TernaryExpr ternary = new TernaryExpr(condition, thenExpr, elseExpr,
                    PrimitiveSourceType.INT);

            assertSame(ternary, condition.getParent());
            assertSame(ternary, thenExpr.getParent());
            assertSame(ternary, elseExpr.getParent());
        }
    }

    @Nested
    class ThisExprTests {

        @Test
        void basicConstructor() {
            SourceType type = new ReferenceSourceType("com/example/MyClass");
            SourceLocation location = createTestLocation();

            ThisExpr thisExpr = new ThisExpr(type, location);

            assertSame(type, thisExpr.getType());
            assertSame(location, thisExpr.getLocation());
        }

        @Test
        void constructorWithoutLocation() {
            SourceType type = new ReferenceSourceType("com/example/MyClass");

            ThisExpr thisExpr = new ThisExpr(type);

            assertSame(type, thisExpr.getType());
            assertEquals(SourceLocation.UNKNOWN, thisExpr.getLocation());
        }

        @Test
        void visitorPattern() {
            ThisExpr thisExpr = new ThisExpr(new ReferenceSourceType("com/example/MyClass"));

            String result = thisExpr.accept(new TestVisitor());

            assertEquals("visitThis", result);
        }

        @Test
        void toStringFormat() {
            ThisExpr thisExpr = new ThisExpr(new ReferenceSourceType("com/example/MyClass"));

            String result = thisExpr.toString();

            assertEquals("this", result);
        }

        @Test
        void nullTypeThrows() {
            assertThrows(NullPointerException.class, () -> new ThisExpr(null));
        }
    }

    @Nested
    class SuperExprTests {

        @Test
        void basicConstructor() {
            SourceType type = new ReferenceSourceType("com/example/MyClass");
            SourceLocation location = createTestLocation();

            SuperExpr superExpr = new SuperExpr(type, location);

            assertSame(type, superExpr.getType());
            assertSame(location, superExpr.getLocation());
        }

        @Test
        void constructorWithoutLocation() {
            SourceType type = new ReferenceSourceType("com/example/MyClass");

            SuperExpr superExpr = new SuperExpr(type);

            assertSame(type, superExpr.getType());
            assertEquals(SourceLocation.UNKNOWN, superExpr.getLocation());
        }

        @Test
        void visitorPattern() {
            SuperExpr superExpr = new SuperExpr(new ReferenceSourceType("com/example/MyClass"));

            String result = superExpr.accept(new TestVisitor());

            assertEquals("visitSuper", result);
        }

        @Test
        void toStringFormat() {
            SuperExpr superExpr = new SuperExpr(new ReferenceSourceType("com/example/MyClass"));

            String result = superExpr.toString();

            assertEquals("super", result);
        }

        @Test
        void nullTypeThrows() {
            assertThrows(NullPointerException.class, () -> new SuperExpr(null));
        }
    }

    @Nested
    class UnaryExprTests {

        @Test
        void basicConstructor() {
            Expression operand = LiteralExpr.ofInt(42);
            SourceType type = PrimitiveSourceType.INT;
            SourceLocation location = createTestLocation();

            UnaryExpr unary = new UnaryExpr(UnaryOperator.NEG, operand, type, location);

            assertEquals(UnaryOperator.NEG, unary.getOperator());
            assertSame(operand, unary.getOperand());
            assertSame(type, unary.getType());
            assertSame(location, unary.getLocation());
            assertSame(unary, operand.getParent());
        }

        @Test
        void constructorWithoutLocation() {
            Expression operand = LiteralExpr.ofInt(42);
            SourceType type = PrimitiveSourceType.INT;

            UnaryExpr unary = new UnaryExpr(UnaryOperator.NEG, operand, type);

            assertEquals(SourceLocation.UNKNOWN, unary.getLocation());
        }

        @Test
        void prefixOperators() {
            Expression operand = LiteralExpr.ofInt(42);

            UnaryExpr negation = new UnaryExpr(UnaryOperator.NEG, operand, PrimitiveSourceType.INT);
            assertTrue(negation.isPrefix());
            assertFalse(negation.isPostfix());

            UnaryExpr not = new UnaryExpr(UnaryOperator.NOT, LiteralExpr.ofBoolean(true),
                    PrimitiveSourceType.BOOLEAN);
            assertTrue(not.isPrefix());

            UnaryExpr preInc = new UnaryExpr(UnaryOperator.PRE_INC, operand, PrimitiveSourceType.INT);
            assertTrue(preInc.isPrefix());
            assertTrue(preInc.isIncDec());
        }

        @Test
        void postfixOperators() {
            Expression operand = LiteralExpr.ofInt(42);

            UnaryExpr postInc = new UnaryExpr(UnaryOperator.POST_INC, operand, PrimitiveSourceType.INT);
            assertFalse(postInc.isPrefix());
            assertTrue(postInc.isPostfix());
            assertTrue(postInc.isIncDec());

            UnaryExpr postDec = new UnaryExpr(UnaryOperator.POST_DEC, operand, PrimitiveSourceType.INT);
            assertTrue(postDec.isPostfix());
            assertTrue(postDec.isIncDec());
        }

        @Test
        void allUnaryOperators() {
            Expression operand = LiteralExpr.ofInt(42);

            for (UnaryOperator op : UnaryOperator.values()) {
                UnaryExpr unary = new UnaryExpr(op, operand, PrimitiveSourceType.INT);
                assertEquals(op, unary.getOperator());
                assertEquals(op.isPrefix(), unary.isPrefix());
                assertEquals(op.isPostfix(), unary.isPostfix());
            }
        }

        @Test
        void setters() {
            Expression operand = LiteralExpr.ofInt(42);
            UnaryExpr unary = new UnaryExpr(UnaryOperator.NEG, operand, PrimitiveSourceType.INT);

            unary.setOperator(UnaryOperator.POS);
            Expression newOperand = LiteralExpr.ofInt(100);
            unary.setOperand(newOperand);

            assertEquals(UnaryOperator.POS, unary.getOperator());
            assertSame(newOperand, unary.getOperand());
        }

        @Test
        void visitorPattern() {
            UnaryExpr unary = new UnaryExpr(UnaryOperator.NEG, LiteralExpr.ofInt(42),
                    PrimitiveSourceType.INT);

            String result = unary.accept(new TestVisitor());

            assertEquals("visitUnary", result);
        }

        @Test
        void toStringPrefix() {
            UnaryExpr unary = new UnaryExpr(UnaryOperator.NEG, LiteralExpr.ofInt(42),
                    PrimitiveSourceType.INT);

            String result = unary.toString();

            assertTrue(result.startsWith("-"));
        }

        @Test
        void toStringPostfix() {
            UnaryExpr unary = new UnaryExpr(UnaryOperator.POST_INC, LiteralExpr.ofInt(42),
                    PrimitiveSourceType.INT);

            String result = unary.toString();

            assertTrue(result.endsWith("++"));
        }

        @Test
        void nullOperatorThrows() {
            assertThrows(NullPointerException.class, () ->
                    new UnaryExpr(null, LiteralExpr.ofInt(42), PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullOperandThrows() {
            assertThrows(NullPointerException.class, () ->
                    new UnaryExpr(UnaryOperator.NEG, null, PrimitiveSourceType.INT)
            );
        }

        @Test
        void nullTypeThrows() {
            assertThrows(NullPointerException.class, () ->
                    new UnaryExpr(UnaryOperator.NEG, LiteralExpr.ofInt(42), null)
            );
        }

        @Test
        void parentChildRelationship() {
            Expression operand = LiteralExpr.ofInt(42);
            UnaryExpr unary = new UnaryExpr(UnaryOperator.NEG, operand, PrimitiveSourceType.INT);

            assertSame(unary, operand.getParent());
        }
    }

    @Nested
    class UnaryOperatorTests {

        @Test
        void prefixOperators() {
            assertTrue(UnaryOperator.NEG.isPrefix());
            assertTrue(UnaryOperator.POS.isPrefix());
            assertTrue(UnaryOperator.BNOT.isPrefix());
            assertTrue(UnaryOperator.NOT.isPrefix());
            assertTrue(UnaryOperator.PRE_INC.isPrefix());
            assertTrue(UnaryOperator.PRE_DEC.isPrefix());

            assertFalse(UnaryOperator.POST_INC.isPrefix());
            assertFalse(UnaryOperator.POST_DEC.isPrefix());
        }

        @Test
        void postfixOperators() {
            assertTrue(UnaryOperator.POST_INC.isPostfix());
            assertTrue(UnaryOperator.POST_DEC.isPostfix());

            assertFalse(UnaryOperator.NEG.isPostfix());
            assertFalse(UnaryOperator.POS.isPostfix());
            assertFalse(UnaryOperator.BNOT.isPostfix());
            assertFalse(UnaryOperator.NOT.isPostfix());
            assertFalse(UnaryOperator.PRE_INC.isPostfix());
            assertFalse(UnaryOperator.PRE_DEC.isPostfix());
        }

        @Test
        void incDecOperators() {
            assertTrue(UnaryOperator.PRE_INC.isIncDec());
            assertTrue(UnaryOperator.PRE_DEC.isIncDec());
            assertTrue(UnaryOperator.POST_INC.isIncDec());
            assertTrue(UnaryOperator.POST_DEC.isIncDec());

            assertFalse(UnaryOperator.NEG.isIncDec());
            assertFalse(UnaryOperator.POS.isIncDec());
            assertFalse(UnaryOperator.BNOT.isIncDec());
            assertFalse(UnaryOperator.NOT.isIncDec());
        }

        @Test
        void symbols() {
            assertEquals("-", UnaryOperator.NEG.getSymbol());
            assertEquals("+", UnaryOperator.POS.getSymbol());
            assertEquals("~", UnaryOperator.BNOT.getSymbol());
            assertEquals("!", UnaryOperator.NOT.getSymbol());
            assertEquals("++", UnaryOperator.PRE_INC.getSymbol());
            assertEquals("--", UnaryOperator.PRE_DEC.getSymbol());
            assertEquals("++", UnaryOperator.POST_INC.getSymbol());
            assertEquals("--", UnaryOperator.POST_DEC.getSymbol());
        }

        @Test
        void toStringFormat() {
            assertEquals("-", UnaryOperator.NEG.toString());
            assertEquals("+", UnaryOperator.POS.toString());
            assertEquals("~", UnaryOperator.BNOT.toString());
            assertEquals("!", UnaryOperator.NOT.toString());
            assertEquals("++", UnaryOperator.PRE_INC.toString());
            assertEquals("--", UnaryOperator.PRE_DEC.toString());
            assertEquals("++", UnaryOperator.POST_INC.toString());
            assertEquals("--", UnaryOperator.POST_DEC.toString());
        }
    }

    @Nested
    class FluentSetterTests {

        @Test
        void binaryExpr_fluentChaining() {
            LiteralExpr left = new LiteralExpr(1, PrimitiveSourceType.INT);
            LiteralExpr right = new LiteralExpr(2, PrimitiveSourceType.INT);
            LiteralExpr newLeft = new LiteralExpr(10, PrimitiveSourceType.INT);
            LiteralExpr newRight = new LiteralExpr(20, PrimitiveSourceType.INT);

            BinaryExpr expr = new BinaryExpr(BinaryOperator.ADD, left, right, PrimitiveSourceType.INT);

            BinaryExpr result = expr
                    .withOperator(BinaryOperator.MUL)
                    .withLeft(newLeft)
                    .withRight(newRight);

            assertSame(expr, result);
            assertEquals(BinaryOperator.MUL, expr.getOperator());
            assertSame(newLeft, expr.getLeft());
            assertSame(newRight, expr.getRight());
            assertSame(expr, newLeft.getParent());
            assertSame(expr, newRight.getParent());
            assertNull(left.getParent());
            assertNull(right.getParent());
        }

        @Test
        void binaryExpr_withNullOperands() {
            LiteralExpr left = new LiteralExpr(1, PrimitiveSourceType.INT);
            LiteralExpr right = new LiteralExpr(2, PrimitiveSourceType.INT);
            BinaryExpr expr = new BinaryExpr(BinaryOperator.ADD, left, right, PrimitiveSourceType.INT);

            expr.withLeft(null).withRight(null);

            assertNull(expr.getLeft());
            assertNull(expr.getRight());
            assertNull(left.getParent());
            assertNull(right.getParent());
        }

        @Test
        void unaryExpr_fluentChaining() {
            LiteralExpr operand = new LiteralExpr(5, PrimitiveSourceType.INT);
            LiteralExpr newOperand = new LiteralExpr(10, PrimitiveSourceType.INT);

            UnaryExpr expr = new UnaryExpr(UnaryOperator.NEG, operand, PrimitiveSourceType.INT);

            UnaryExpr result = expr
                    .withOperator(UnaryOperator.BNOT)
                    .withOperand(newOperand);

            assertSame(expr, result);
            assertEquals(UnaryOperator.BNOT, expr.getOperator());
            assertSame(newOperand, expr.getOperand());
            assertSame(expr, newOperand.getParent());
            assertNull(operand.getParent());
        }

        @Test
        void methodCallExpr_fluentChaining() {
            VarRefExpr receiver = new VarRefExpr("obj", ReferenceSourceType.OBJECT);
            VarRefExpr newReceiver = new VarRefExpr("other", ReferenceSourceType.OBJECT);
            List<Expression> args = new ArrayList<>();

            MethodCallExpr expr = new MethodCallExpr(receiver, "method", "java/lang/Object", args, false, ReferenceSourceType.OBJECT);

            MethodCallExpr result = expr
                    .withReceiver(newReceiver)
                    .withMethodName("newMethod");

            assertSame(expr, result);
            assertSame(newReceiver, expr.getReceiver());
            assertEquals("newMethod", expr.getMethodName());
            assertSame(expr, newReceiver.getParent());
            assertNull(receiver.getParent());
        }

        @Test
        void fieldAccessExpr_fluentChaining() {
            VarRefExpr receiver = new VarRefExpr("obj", ReferenceSourceType.OBJECT);
            VarRefExpr newReceiver = new VarRefExpr("other", ReferenceSourceType.OBJECT);

            FieldAccessExpr expr = new FieldAccessExpr(receiver, "field", "java/lang/Object", false, PrimitiveSourceType.INT);

            FieldAccessExpr result = expr.withReceiver(newReceiver);

            assertSame(expr, result);
            assertSame(newReceiver, expr.getReceiver());
            assertSame(expr, newReceiver.getParent());
            assertNull(receiver.getParent());
        }

        @Test
        void arrayAccessExpr_fluentChaining() {
            VarRefExpr array = new VarRefExpr("arr", new ArraySourceType(PrimitiveSourceType.INT, 1));
            LiteralExpr index = new LiteralExpr(0, PrimitiveSourceType.INT);
            VarRefExpr newArray = new VarRefExpr("arr2", new ArraySourceType(PrimitiveSourceType.INT, 1));
            LiteralExpr newIndex = new LiteralExpr(5, PrimitiveSourceType.INT);

            ArrayAccessExpr expr = new ArrayAccessExpr(array, index, PrimitiveSourceType.INT);

            ArrayAccessExpr result = expr.withArray(newArray).withIndex(newIndex);

            assertSame(expr, result);
            assertSame(newArray, expr.getArray());
            assertSame(newIndex, expr.getIndex());
            assertSame(expr, newArray.getParent());
            assertSame(expr, newIndex.getParent());
            assertNull(array.getParent());
            assertNull(index.getParent());
        }

        @Test
        void castExpr_fluentChaining() {
            LiteralExpr original = new LiteralExpr(1, PrimitiveSourceType.INT);
            LiteralExpr newExpr = new LiteralExpr(2, PrimitiveSourceType.INT);

            CastExpr expr = new CastExpr(PrimitiveSourceType.LONG, original);

            CastExpr result = expr.withExpression(newExpr);

            assertSame(expr, result);
            assertSame(newExpr, expr.getExpression());
            assertSame(expr, newExpr.getParent());
            assertNull(original.getParent());
        }

        @Test
        void ternaryExpr_fluentChaining() {
            LiteralExpr cond = new LiteralExpr(true, PrimitiveSourceType.BOOLEAN);
            LiteralExpr thenE = new LiteralExpr(1, PrimitiveSourceType.INT);
            LiteralExpr elseE = new LiteralExpr(2, PrimitiveSourceType.INT);
            LiteralExpr newCond = new LiteralExpr(false, PrimitiveSourceType.BOOLEAN);
            LiteralExpr newThen = new LiteralExpr(10, PrimitiveSourceType.INT);
            LiteralExpr newElse = new LiteralExpr(20, PrimitiveSourceType.INT);

            TernaryExpr expr = new TernaryExpr(cond, thenE, elseE, PrimitiveSourceType.INT);

            TernaryExpr result = expr
                    .withCondition(newCond)
                    .withThenExpr(newThen)
                    .withElseExpr(newElse);

            assertSame(expr, result);
            assertSame(newCond, expr.getCondition());
            assertSame(newThen, expr.getThenExpr());
            assertSame(newElse, expr.getElseExpr());
            assertSame(expr, newCond.getParent());
            assertSame(expr, newThen.getParent());
            assertSame(expr, newElse.getParent());
        }

        @Test
        void instanceOfExpr_fluentChaining() {
            VarRefExpr original = new VarRefExpr("obj", ReferenceSourceType.OBJECT);
            VarRefExpr newExpr = new VarRefExpr("other", ReferenceSourceType.OBJECT);

            InstanceOfExpr expr = new InstanceOfExpr(original, ReferenceSourceType.STRING, "str");

            InstanceOfExpr result = expr
                    .withExpression(newExpr)
                    .withPatternVariable("newVar");

            assertSame(expr, result);
            assertSame(newExpr, expr.getExpression());
            assertEquals("newVar", expr.getPatternVariable());
            assertSame(expr, newExpr.getParent());
            assertNull(original.getParent());
        }

        @Test
        void varRefExpr_fluentChaining() {
            VarRefExpr expr = new VarRefExpr("oldName", PrimitiveSourceType.INT);

            VarRefExpr result = expr.withName("newName");

            assertSame(expr, result);
            assertEquals("newName", expr.getName());
        }

        @Test
        void literalExpr_fluentChaining() {
            LiteralExpr expr = new LiteralExpr(42, PrimitiveSourceType.INT);

            LiteralExpr result = expr
                    .withValue(100L)
                    .withType(PrimitiveSourceType.LONG);

            assertSame(expr, result);
            assertEquals(100L, expr.getValue());
            assertEquals(PrimitiveSourceType.LONG, expr.getType());
        }

        @Test
        void methodRefExpr_fluentChaining() {
            VarRefExpr receiver = new VarRefExpr("list", ReferenceSourceType.OBJECT);
            VarRefExpr newReceiver = new VarRefExpr("set", ReferenceSourceType.OBJECT);

            MethodRefExpr expr = new MethodRefExpr(receiver, "add", "java/util/List", MethodRefKind.BOUND, ReferenceSourceType.OBJECT);

            MethodRefExpr result = expr
                    .withReceiver(newReceiver)
                    .withMethodName("put");

            assertSame(expr, result);
            assertSame(newReceiver, expr.getReceiver());
            assertEquals("put", expr.getMethodName());
            assertSame(expr, newReceiver.getParent());
            assertNull(receiver.getParent());
        }

        @Test
        void newArrayExpr_fluentChaining() {
            ArrayInitExpr init = new ArrayInitExpr(
                    List.of(new LiteralExpr(1, PrimitiveSourceType.INT)),
                    new ArraySourceType(PrimitiveSourceType.INT, 1)
            );
            ArrayInitExpr newInit = new ArrayInitExpr(
                    List.of(new LiteralExpr(2, PrimitiveSourceType.INT)),
                    new ArraySourceType(PrimitiveSourceType.INT, 1)
            );

            NewArrayExpr expr = new NewArrayExpr(PrimitiveSourceType.INT, init);

            NewArrayExpr result = expr.withInitializer(newInit);

            assertSame(expr, result);
            assertSame(newInit, expr.getInitializer());
            assertSame(expr, newInit.getParent());
            assertNull(init.getParent());
        }

        @Test
        void fluentSetters_preserveParentChildRelationship() {
            BinaryExpr parent = new BinaryExpr(
                    BinaryOperator.ADD,
                    new LiteralExpr(1, PrimitiveSourceType.INT),
                    new LiteralExpr(2, PrimitiveSourceType.INT),
                    PrimitiveSourceType.INT
            );

            LiteralExpr newChild = new LiteralExpr(99, PrimitiveSourceType.INT);
            parent.withLeft(newChild);

            assertSame(parent, newChild.getParent());
        }

        @Test
        void fluentSetters_clearOldParent() {
            LiteralExpr child = new LiteralExpr(1, PrimitiveSourceType.INT);
            BinaryExpr parent1 = new BinaryExpr(BinaryOperator.ADD, child, new LiteralExpr(2, PrimitiveSourceType.INT), PrimitiveSourceType.INT);

            assertSame(parent1, child.getParent());

            BinaryExpr parent2 = new BinaryExpr(BinaryOperator.MUL, new LiteralExpr(3, PrimitiveSourceType.INT), new LiteralExpr(4, PrimitiveSourceType.INT), PrimitiveSourceType.INT);
            parent2.withLeft(child);

            assertSame(parent2, child.getParent());
        }

        @Test
        void fluentSetters_chainMultipleOperations() {
            BinaryExpr expr = new BinaryExpr(
                    BinaryOperator.ADD,
                    new LiteralExpr(1, PrimitiveSourceType.INT),
                    new LiteralExpr(2, PrimitiveSourceType.INT),
                    PrimitiveSourceType.INT
            );

            BinaryExpr result = expr
                    .withOperator(BinaryOperator.SUB)
                    .withLeft(new LiteralExpr(10, PrimitiveSourceType.INT))
                    .withRight(new LiteralExpr(5, PrimitiveSourceType.INT));

            assertEquals(BinaryOperator.SUB, result.getOperator());
            assertEquals(10, ((LiteralExpr) result.getLeft()).getValue());
            assertEquals(5, ((LiteralExpr) result.getRight()).getValue());
        }
    }
}
