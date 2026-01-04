package com.tonic.analysis.source.ast;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;

import java.util.Arrays;
import java.util.List;

/**
 * Factory class for creating AST nodes with sensible defaults.
 * Provides convenient static methods for common node creation patterns.
 */
public final class ASTFactory {

    private ASTFactory() {}

    // ========================
    // Literal Expressions
    // ========================

    public static LiteralExpr intLit(int value) {
        return new LiteralExpr(value, PrimitiveSourceType.INT);
    }

    public static LiteralExpr longLit(long value) {
        return new LiteralExpr(value, PrimitiveSourceType.LONG);
    }

    public static LiteralExpr floatLit(float value) {
        return new LiteralExpr(value, PrimitiveSourceType.FLOAT);
    }

    public static LiteralExpr doubleLit(double value) {
        return new LiteralExpr(value, PrimitiveSourceType.DOUBLE);
    }

    public static LiteralExpr boolLit(boolean value) {
        return new LiteralExpr(value, PrimitiveSourceType.BOOLEAN);
    }

    public static LiteralExpr charLit(char value) {
        return new LiteralExpr(value, PrimitiveSourceType.CHAR);
    }

    public static LiteralExpr stringLit(String value) {
        return new LiteralExpr(value, ReferenceSourceType.STRING);
    }

    public static LiteralExpr nullLit() {
        return LiteralExpr.ofNull();
    }

    // ========================
    // Variable References
    // ========================

    public static VarRefExpr varRef(String name, SourceType type) {
        return new VarRefExpr(name, type);
    }

    public static VarRefExpr intVar(String name) {
        return new VarRefExpr(name, PrimitiveSourceType.INT);
    }

    public static VarRefExpr boolVar(String name) {
        return new VarRefExpr(name, PrimitiveSourceType.BOOLEAN);
    }

    public static VarRefExpr objectVar(String name, String className) {
        return new VarRefExpr(name, new ReferenceSourceType(className));
    }

    // ========================
    // Binary Expressions
    // ========================

    public static BinaryExpr binary(BinaryOperator op, Expression left, Expression right, SourceType type) {
        return new BinaryExpr(op, left, right, type);
    }

    public static BinaryExpr add(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.ADD, left, right, left.getType());
    }

    public static BinaryExpr sub(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.SUB, left, right, left.getType());
    }

    public static BinaryExpr mul(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.MUL, left, right, left.getType());
    }

    public static BinaryExpr div(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.DIV, left, right, left.getType());
    }

    public static BinaryExpr mod(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.MOD, left, right, left.getType());
    }

    public static BinaryExpr eq(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.EQ, left, right, PrimitiveSourceType.BOOLEAN);
    }

    public static BinaryExpr ne(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.NE, left, right, PrimitiveSourceType.BOOLEAN);
    }

    public static BinaryExpr lt(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.LT, left, right, PrimitiveSourceType.BOOLEAN);
    }

    public static BinaryExpr le(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.LE, left, right, PrimitiveSourceType.BOOLEAN);
    }

    public static BinaryExpr gt(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.GT, left, right, PrimitiveSourceType.BOOLEAN);
    }

    public static BinaryExpr ge(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.GE, left, right, PrimitiveSourceType.BOOLEAN);
    }

    public static BinaryExpr and(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.AND, left, right, PrimitiveSourceType.BOOLEAN);
    }

    public static BinaryExpr or(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.OR, left, right, PrimitiveSourceType.BOOLEAN);
    }

    public static BinaryExpr assign(Expression left, Expression right) {
        return new BinaryExpr(BinaryOperator.ASSIGN, left, right, left.getType());
    }

    // ========================
    // Unary Expressions
    // ========================

    public static UnaryExpr unary(UnaryOperator op, Expression operand, SourceType type) {
        return new UnaryExpr(op, operand, type);
    }

    public static UnaryExpr not(Expression operand) {
        return new UnaryExpr(UnaryOperator.NOT, operand, PrimitiveSourceType.BOOLEAN);
    }

    public static UnaryExpr neg(Expression operand) {
        return new UnaryExpr(UnaryOperator.NEG, operand, operand.getType());
    }

    public static UnaryExpr preIncr(Expression operand) {
        return new UnaryExpr(UnaryOperator.PRE_INC, operand, operand.getType());
    }

    public static UnaryExpr preDecr(Expression operand) {
        return new UnaryExpr(UnaryOperator.PRE_DEC, operand, operand.getType());
    }

    public static UnaryExpr postIncr(Expression operand) {
        return new UnaryExpr(UnaryOperator.POST_INC, operand, operand.getType());
    }

    public static UnaryExpr postDecr(Expression operand) {
        return new UnaryExpr(UnaryOperator.POST_DEC, operand, operand.getType());
    }

    // ========================
    // Other Expressions
    // ========================

    public static TernaryExpr ternary(Expression condition, Expression thenExpr, Expression elseExpr) {
        return new TernaryExpr(condition, thenExpr, elseExpr, thenExpr.getType());
    }

    public static CastExpr cast(SourceType targetType, Expression expr) {
        return new CastExpr(targetType, expr);
    }

    public static InstanceOfExpr instanceOf(Expression expr, SourceType checkType) {
        return new InstanceOfExpr(expr, checkType);
    }

    public static InstanceOfExpr instanceOf(Expression expr, SourceType checkType, String patternVar) {
        return new InstanceOfExpr(expr, checkType, patternVar);
    }

    public static ArrayAccessExpr arrayAccess(Expression array, Expression index, SourceType elementType) {
        return new ArrayAccessExpr(array, index, elementType);
    }

    public static NewExpr newObj(String className, Expression... args) {
        return new NewExpr(className, Arrays.asList(args));
    }

    public static NewArrayExpr newArray(SourceType elementType, Expression size) {
        return NewArrayExpr.withSize(elementType, size);
    }

    public static ArrayInitExpr arrayInit(SourceType elementType, Expression... elements) {
        return ArrayInitExpr.of(elementType, Arrays.asList(elements));
    }

    public static ThisExpr thisExpr(SourceType type) {
        return new ThisExpr(type);
    }

    public static ClassExpr classExpr(SourceType classType) {
        return new ClassExpr(classType);
    }

    // ========================
    // Field Access
    // ========================

    public static FieldAccessExpr fieldAccess(Expression receiver, String fieldName, String ownerClass, SourceType type) {
        return FieldAccessExpr.instanceField(receiver, fieldName, ownerClass, type);
    }

    public static FieldAccessExpr staticField(String ownerClass, String fieldName, SourceType type) {
        return FieldAccessExpr.staticField(ownerClass, fieldName, type);
    }

    // ========================
    // Method Calls
    // ========================

    public static MethodCallExpr methodCall(Expression receiver, String methodName, String ownerClass,
                                            SourceType returnType, Expression... args) {
        return MethodCallExpr.instanceCall(receiver, methodName, ownerClass, Arrays.asList(args), returnType);
    }

    public static MethodCallExpr staticCall(String ownerClass, String methodName,
                                            SourceType returnType, Expression... args) {
        return MethodCallExpr.staticCall(ownerClass, methodName, Arrays.asList(args), returnType);
    }

    // ========================
    // Statements
    // ========================

    public static BlockStmt block(Statement... statements) {
        return new BlockStmt(Arrays.asList(statements));
    }

    public static BlockStmt block(List<Statement> statements) {
        return new BlockStmt(statements);
    }

    public static IfStmt ifStmt(Expression condition, Statement thenBranch) {
        return new IfStmt(condition, thenBranch);
    }

    public static IfStmt ifElse(Expression condition, Statement thenBranch, Statement elseBranch) {
        return new IfStmt(condition, thenBranch, elseBranch);
    }

    public static WhileStmt whileLoop(Expression condition, Statement body) {
        return new WhileStmt(condition, body);
    }

    public static DoWhileStmt doWhile(Statement body, Expression condition) {
        return new DoWhileStmt(body, condition);
    }

    public static ForStmt forLoop(List<Statement> init, Expression condition, List<Expression> update, Statement body) {
        return new ForStmt(init, condition, update, body);
    }

    public static ForStmt infiniteLoop(Statement body) {
        return ForStmt.infinite(body);
    }

    public static ForEachStmt forEach(VarDeclStmt variable, Expression iterable, Statement body) {
        return new ForEachStmt(variable, iterable, body);
    }

    public static ReturnStmt returnStmt(Expression value) {
        return new ReturnStmt(value);
    }

    public static ReturnStmt returnVoid() {
        return new ReturnStmt();
    }

    public static ThrowStmt throwStmt(Expression exception) {
        return new ThrowStmt(exception);
    }

    public static BreakStmt breakStmt() {
        return new BreakStmt();
    }

    public static BreakStmt breakStmt(String label) {
        return new BreakStmt(label);
    }

    public static ContinueStmt continueStmt() {
        return new ContinueStmt();
    }

    public static ContinueStmt continueStmt(String label) {
        return new ContinueStmt(label);
    }

    public static ExprStmt exprStmt(Expression expr) {
        return new ExprStmt(expr);
    }

    public static VarDeclStmt varDecl(SourceType type, String name) {
        return new VarDeclStmt(type, name);
    }

    public static VarDeclStmt varDecl(SourceType type, String name, Expression initializer) {
        return new VarDeclStmt(type, name, initializer);
    }

    public static VarDeclStmt finalVar(SourceType type, String name, Expression initializer) {
        return new VarDeclStmt(type, name, initializer, false, true, SourceLocation.UNKNOWN);
    }

    public static LabeledStmt labeled(String label, Statement statement) {
        return new LabeledStmt(label, statement);
    }

    public static SynchronizedStmt synchronizedStmt(Expression lock, Statement body) {
        return new SynchronizedStmt(lock, body);
    }

    public static TryCatchStmt tryCatch(Statement tryBlock, List<CatchClause> catches) {
        return new TryCatchStmt(tryBlock, catches);
    }

    public static TryCatchStmt tryCatchFinally(Statement tryBlock, List<CatchClause> catches, Statement finallyBlock) {
        return new TryCatchStmt(tryBlock, catches, finallyBlock);
    }

    public static SwitchStmt switchStmt(Expression selector, List<SwitchCase> cases) {
        return new SwitchStmt(selector, cases);
    }

    // ========================
    // Type Utilities
    // ========================

    public static ReferenceSourceType refType(String className) {
        return new ReferenceSourceType(className);
    }

    public static ArraySourceType arrayType(SourceType elementType) {
        return new ArraySourceType(elementType);
    }

    public static ArraySourceType arrayType(SourceType elementType, int dimensions) {
        return new ArraySourceType(elementType, dimensions);
    }
}
