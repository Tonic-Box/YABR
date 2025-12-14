package com.tonic.analysis.source.editor.util;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Factory for creating AST nodes programmatically.
 * Provides convenient methods for building expressions and statements.
 */
public class ASTFactory {

    // ==================== Literal Expressions ====================

    /**
     * Creates an integer literal.
     */
    public LiteralExpr intLiteral(int value) {
        return LiteralExpr.ofInt(value);
    }

    /**
     * Creates a long literal.
     */
    public LiteralExpr longLiteral(long value) {
        return LiteralExpr.ofLong(value);
    }

    /**
     * Creates a float literal.
     */
    public LiteralExpr floatLiteral(float value) {
        return LiteralExpr.ofFloat(value);
    }

    /**
     * Creates a double literal.
     */
    public LiteralExpr doubleLiteral(double value) {
        return LiteralExpr.ofDouble(value);
    }

    /**
     * Creates a boolean literal.
     */
    public LiteralExpr boolLiteral(boolean value) {
        return LiteralExpr.ofBoolean(value);
    }

    /**
     * Creates a character literal.
     */
    public LiteralExpr charLiteral(char value) {
        return LiteralExpr.ofChar(value);
    }

    /**
     * Creates a string literal.
     */
    public LiteralExpr stringLiteral(String value) {
        return LiteralExpr.ofString(value);
    }

    /**
     * Creates a null literal.
     */
    public LiteralExpr nullLiteral() {
        return LiteralExpr.ofNull();
    }

    // ==================== Variable Expressions ====================

    /**
     * Creates a variable reference.
     */
    public VarRefExpr variable(String name, SourceType type) {
        return new VarRefExpr(name, type);
    }

    /**
     * Creates a variable reference with Object type.
     */
    public VarRefExpr variable(String name) {
        return new VarRefExpr(name, ReferenceSourceType.OBJECT);
    }

    /**
     * Creates an instance field access.
     */
    public FieldAccessExpr fieldAccess(Expression target, String fieldName, String ownerClass, SourceType type) {
        return FieldAccessExpr.instanceField(target, fieldName, ownerClass, type);
    }

    /**
     * Creates a static field access.
     */
    public FieldAccessExpr staticField(String ownerClass, String fieldName, SourceType type) {
        return FieldAccessExpr.staticField(ownerClass, fieldName, type);
    }

    /**
     * Creates an array access expression.
     */
    public ArrayAccessExpr arrayAccess(Expression array, Expression index, SourceType componentType) {
        return new ArrayAccessExpr(array, index, componentType);
    }

    // ==================== Method Calls ====================

    /**
     * Creates a method call builder.
     */
    public MethodCallBuilder methodCall(String methodName) {
        return new MethodCallBuilder(methodName);
    }

    /**
     * Creates a static method call directly.
     */
    public MethodCallExpr staticCall(String ownerClass, String methodName, SourceType returnType, Expression... args) {
        return MethodCallExpr.staticCall(ownerClass, methodName, Arrays.asList(args), returnType);
    }

    /**
     * Creates an instance method call directly.
     */
    public MethodCallExpr instanceCall(Expression receiver, String ownerClass, String methodName,
                                        SourceType returnType, Expression... args) {
        return MethodCallExpr.instanceCall(receiver, methodName, ownerClass, Arrays.asList(args), returnType);
    }

    // ==================== Object Creation ====================

    /**
     * Creates a new object expression builder.
     */
    public NewExprBuilder newInstance(String className) {
        return new NewExprBuilder(className);
    }

    /**
     * Creates a new object expression directly.
     */
    public NewExpr newExpr(String className, Expression... args) {
        return new NewExpr(className, Arrays.asList(args));
    }

    /**
     * Creates a new array expression.
     */
    public NewArrayExpr newArray(SourceType elementType, Expression size) {
        return NewArrayExpr.withSize(elementType, size);
    }

    /**
     * Creates a new array expression from type name.
     */
    public NewArrayExpr newArray(String typeName, Expression size) {
        return NewArrayExpr.withSize(parseType(typeName), size);
    }

    // ==================== Operators ====================

    /**
     * Creates a binary expression.
     */
    public BinaryExpr binary(Expression left, BinaryOperator op, Expression right, SourceType type) {
        return new BinaryExpr(op, left, right, type);
    }

    /**
     * Creates an addition expression.
     */
    public BinaryExpr add(Expression left, Expression right, SourceType type) {
        return binary(left, BinaryOperator.ADD, right, type);
    }

    /**
     * Creates a subtraction expression.
     */
    public BinaryExpr subtract(Expression left, Expression right, SourceType type) {
        return binary(left, BinaryOperator.SUB, right, type);
    }

    /**
     * Creates a multiplication expression.
     */
    public BinaryExpr multiply(Expression left, Expression right, SourceType type) {
        return binary(left, BinaryOperator.MUL, right, type);
    }

    /**
     * Creates a division expression.
     */
    public BinaryExpr divide(Expression left, Expression right, SourceType type) {
        return binary(left, BinaryOperator.DIV, right, type);
    }

    /**
     * Creates an equality comparison.
     */
    public BinaryExpr equals(Expression left, Expression right) {
        return binary(left, BinaryOperator.EQ, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates an inequality comparison.
     */
    public BinaryExpr notEquals(Expression left, Expression right) {
        return binary(left, BinaryOperator.NE, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates a less-than comparison.
     */
    public BinaryExpr lessThan(Expression left, Expression right) {
        return binary(left, BinaryOperator.LT, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates a greater-than comparison.
     */
    public BinaryExpr greaterThan(Expression left, Expression right) {
        return binary(left, BinaryOperator.GT, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates a logical AND expression.
     */
    public BinaryExpr and(Expression left, Expression right) {
        return binary(left, BinaryOperator.AND, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates a logical OR expression.
     */
    public BinaryExpr or(Expression left, Expression right) {
        return binary(left, BinaryOperator.OR, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates an assignment expression.
     */
    public BinaryExpr assign(Expression left, Expression right, SourceType type) {
        return binary(left, BinaryOperator.ASSIGN, right, type);
    }

    /**
     * Creates a unary expression.
     */
    public UnaryExpr unary(UnaryOperator op, Expression operand, SourceType type) {
        return new UnaryExpr(op, operand, type);
    }

    /**
     * Creates a negation expression.
     */
    public UnaryExpr negate(Expression operand, SourceType type) {
        return unary(UnaryOperator.NEG, operand, type);
    }

    /**
     * Creates a logical NOT expression.
     */
    public UnaryExpr not(Expression operand) {
        return unary(UnaryOperator.NOT, operand, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates a pre-increment expression.
     */
    public UnaryExpr preIncrement(Expression operand, SourceType type) {
        return unary(UnaryOperator.PRE_INC, operand, type);
    }

    /**
     * Creates a post-increment expression.
     */
    public UnaryExpr postIncrement(Expression operand, SourceType type) {
        return unary(UnaryOperator.POST_INC, operand, type);
    }

    /**
     * Creates a cast expression.
     */
    public CastExpr cast(SourceType targetType, Expression expr) {
        return new CastExpr(targetType, expr);
    }

    /**
     * Creates a cast expression from type name.
     */
    public CastExpr cast(String typeName, Expression expr) {
        return new CastExpr(parseType(typeName), expr);
    }

    /**
     * Creates an instanceof expression.
     */
    public InstanceOfExpr instanceOf(Expression expr, SourceType checkedType) {
        return new InstanceOfExpr(expr, checkedType);
    }

    /**
     * Creates an instanceof expression from type name.
     */
    public InstanceOfExpr instanceOf(Expression expr, String typeName) {
        return new InstanceOfExpr(expr, parseType(typeName));
    }

    /**
     * Creates a ternary expression.
     */
    public TernaryExpr ternary(Expression condition, Expression thenExpr, Expression elseExpr, SourceType type) {
        return new TernaryExpr(condition, thenExpr, elseExpr, type);
    }

    // ==================== Statements ====================

    /**
     * Creates an expression statement.
     */
    public ExprStmt exprStmt(Expression expr) {
        return new ExprStmt(expr);
    }

    /**
     * Creates a return statement with a value.
     */
    public ReturnStmt returnStmt(Expression value) {
        return new ReturnStmt(value);
    }

    /**
     * Creates a void return statement.
     */
    public ReturnStmt returnVoid() {
        return new ReturnStmt();
    }

    /**
     * Creates a throw statement.
     */
    public ThrowStmt throwStmt(Expression exception) {
        return new ThrowStmt(exception);
    }

    /**
     * Creates a block statement.
     */
    public BlockStmt block(Statement... stmts) {
        return new BlockStmt(Arrays.asList(stmts));
    }

    /**
     * Creates a block statement from a list.
     */
    public BlockStmt block(List<Statement> stmts) {
        return new BlockStmt(stmts);
    }

    /**
     * Creates an if statement without else.
     */
    public IfStmt ifStmt(Expression condition, Statement thenBranch) {
        return new IfStmt(condition, thenBranch);
    }

    /**
     * Creates an if-else statement.
     */
    public IfStmt ifElseStmt(Expression condition, Statement thenBranch, Statement elseBranch) {
        return new IfStmt(condition, thenBranch, elseBranch);
    }

    /**
     * Creates a local variable declaration.
     */
    public VarDeclStmt varDecl(SourceType type, String name, Expression initializer) {
        return new VarDeclStmt(type, name, initializer);
    }

    /**
     * Creates a local variable declaration from type name.
     */
    public VarDeclStmt varDecl(String typeName, String name, Expression initializer) {
        return new VarDeclStmt(parseType(typeName), name, initializer);
    }

    /**
     * Creates a local variable declaration without initializer.
     */
    public VarDeclStmt varDecl(SourceType type, String name) {
        return new VarDeclStmt(type, name);
    }

    // ==================== Type Parsing ====================

    /**
     * Parses a type from a string representation.
     */
    public SourceType parseType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return ReferenceSourceType.OBJECT;
        }

        // Handle primitive types
        switch (typeName) {
            case "void":
                return VoidSourceType.INSTANCE;
            case "int":
                return PrimitiveSourceType.INT;
            case "long":
                return PrimitiveSourceType.LONG;
            case "float":
                return PrimitiveSourceType.FLOAT;
            case "double":
                return PrimitiveSourceType.DOUBLE;
            case "boolean":
                return PrimitiveSourceType.BOOLEAN;
            case "char":
                return PrimitiveSourceType.CHAR;
            case "byte":
                return PrimitiveSourceType.BYTE;
            case "short":
                return PrimitiveSourceType.SHORT;
        }

        // Handle array types
        if (typeName.endsWith("[]")) {
            String componentTypeName = typeName.substring(0, typeName.length() - 2);
            return new ArraySourceType(parseType(componentTypeName));
        }

        // Handle reference types
        return new ReferenceSourceType(typeName.replace('.', '/'));
    }

    // ==================== Builder Classes ====================

    /**
     * Builder for method call expressions.
     */
    public static class MethodCallBuilder {
        private final String methodName;
        private Expression receiver;
        private String ownerClass;
        private boolean isStatic;
        private List<Expression> arguments = new ArrayList<>();
        private SourceType returnType = VoidSourceType.INSTANCE;

        MethodCallBuilder(String methodName) {
            this.methodName = methodName;
        }

        /**
         * Sets the receiver for an instance call.
         */
        public MethodCallBuilder on(Expression target) {
            this.receiver = target;
            this.isStatic = false;
            if (target instanceof FieldAccessExpr) {
                this.ownerClass = ((FieldAccessExpr) target).getOwnerClass();
            } else if (target instanceof VarRefExpr) {
                this.ownerClass = "java/lang/Object";
            }
            return this;
        }

        /**
         * Sets this as a static call on the given class.
         */
        public MethodCallBuilder on(String staticOwner) {
            this.ownerClass = staticOwner.replace('.', '/');
            this.isStatic = true;
            this.receiver = null;
            return this;
        }

        /**
         * Sets the owner class explicitly.
         */
        public MethodCallBuilder owner(String ownerClass) {
            this.ownerClass = ownerClass.replace('.', '/');
            return this;
        }

        /**
         * Adds arguments to the method call.
         */
        public MethodCallBuilder withArgs(Expression... args) {
            this.arguments.addAll(Arrays.asList(args));
            return this;
        }

        /**
         * Adds arguments to the method call.
         */
        public MethodCallBuilder withArgs(List<Expression> args) {
            this.arguments.addAll(args);
            return this;
        }

        /**
         * Sets the return type.
         */
        public MethodCallBuilder returning(SourceType type) {
            this.returnType = type;
            return this;
        }

        /**
         * Builds the method call expression.
         */
        public MethodCallExpr build() {
            String owner = ownerClass != null ? ownerClass : "java/lang/Object";
            return new MethodCallExpr(receiver, methodName, owner, arguments, isStatic, returnType);
        }

        /**
         * Builds the method call and wraps it as a statement.
         */
        public ExprStmt asStatement() {
            return new ExprStmt(build());
        }
    }

    /**
     * Builder for new object expressions.
     */
    public static class NewExprBuilder {
        private final String className;
        private List<Expression> arguments = new ArrayList<>();
        private SourceType type;

        NewExprBuilder(String className) {
            this.className = className.replace('.', '/');
            this.type = new ReferenceSourceType(this.className);
        }

        /**
         * Adds arguments to the constructor call.
         */
        public NewExprBuilder withArgs(Expression... args) {
            this.arguments.addAll(Arrays.asList(args));
            return this;
        }

        /**
         * Adds arguments to the constructor call.
         */
        public NewExprBuilder withArgs(List<Expression> args) {
            this.arguments.addAll(args);
            return this;
        }

        /**
         * Builds the new expression.
         */
        public NewExpr build() {
            return new NewExpr(className, arguments, type);
        }

        /**
         * Builds the new expression and wraps it as a statement.
         */
        public ExprStmt asStatement() {
            return new ExprStmt(build());
        }
    }
}
