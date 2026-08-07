package com.tonic.analysis.source.editor.util;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Factory for building source AST expressions and statements programmatically.
 */
public class ASTFactory
{

    /**
     * Creates an integer literal.
     *
     * @param value the literal value
     * @return the literal expression
     */
    public LiteralExpr intLiteral(int value)
    {
        return LiteralExpr.ofInt(value);
    }

    /**
     * Creates a long literal.
     *
     * @param value the literal value
     * @return the literal expression
     */
    public LiteralExpr longLiteral(long value)
    {
        return LiteralExpr.ofLong(value);
    }

    /**
     * Creates a float literal.
     *
     * @param value the literal value
     * @return the literal expression
     */
    public LiteralExpr floatLiteral(float value)
    {
        return LiteralExpr.ofFloat(value);
    }

    /**
     * Creates a double literal.
     *
     * @param value the literal value
     * @return the literal expression
     */
    public LiteralExpr doubleLiteral(double value)
    {
        return LiteralExpr.ofDouble(value);
    }

    /**
     * Creates a boolean literal.
     *
     * @param value the literal value
     * @return the literal expression
     */
    public LiteralExpr boolLiteral(boolean value)
    {
        return LiteralExpr.ofBoolean(value);
    }

    /**
     * Creates a character literal.
     *
     * @param value the literal value
     * @return the literal expression
     */
    public LiteralExpr charLiteral(char value)
    {
        return LiteralExpr.ofChar(value);
    }

    /**
     * Creates a string literal.
     *
     * @param value the literal value
     * @return the literal expression
     */
    public LiteralExpr stringLiteral(String value)
    {
        return LiteralExpr.ofString(value);
    }

    /**
     * Creates a null literal.
     *
     * @return the literal expression
     */
    public LiteralExpr nullLiteral()
    {
        return LiteralExpr.ofNull();
    }

    /**
     * Creates a variable reference.
     *
     * @param name the variable name
     * @param type the declared type
     * @return the reference expression
     */
    public VarRefExpr variable(String name, SourceType type)
    {
        return new VarRefExpr(name, type);
    }

    /**
     * Creates a variable reference typed as java.lang.Object.
     *
     * @param name the variable name
     * @return the reference expression
     */
    public VarRefExpr variable(String name)
    {
        return new VarRefExpr(name, ReferenceSourceType.OBJECT);
    }

    /**
     * Creates an instance field access.
     *
     * @param target the receiver expression
     * @param fieldName the field name
     * @param ownerClass the internal name of the declaring class
     * @param type the field type
     * @return the field access expression
     */
    public FieldAccessExpr fieldAccess(Expression target, String fieldName, String ownerClass, SourceType type)
    {
        return FieldAccessExpr.instanceField(target, fieldName, ownerClass, type);
    }

    /**
     * Creates a static field access.
     *
     * @param ownerClass the internal name of the declaring class
     * @param fieldName the field name
     * @param type the field type
     * @return the field access expression
     */
    public FieldAccessExpr staticField(String ownerClass, String fieldName, SourceType type)
    {
        return FieldAccessExpr.staticField(ownerClass, fieldName, type);
    }

    /**
     * Creates an array access expression.
     *
     * @param array the array expression
     * @param index the index expression
     * @param componentType the type of the accessed element
     * @return the array access expression
     */
    public ArrayAccessExpr arrayAccess(Expression array, Expression index, SourceType componentType)
    {
        return new ArrayAccessExpr(array, index, componentType);
    }

    /**
     * Starts a method call for incremental configuration.
     *
     * @param methodName the method name
     * @return the builder
     */
    public MethodCallBuilder methodCall(String methodName)
    {
        return new MethodCallBuilder(methodName);
    }

    /**
     * Creates a static method call.
     *
     * @param ownerClass the internal name of the declaring class
     * @param methodName the method name
     * @param returnType the return type
     * @param args the argument expressions
     * @return the call expression
     */
    public MethodCallExpr staticCall(String ownerClass, String methodName, SourceType returnType, Expression... args)
    {
        return MethodCallExpr.staticCall(ownerClass, methodName, Arrays.asList(args), returnType);
    }

    /**
     * Creates an instance method call.
     *
     * @param receiver the receiver expression
     * @param ownerClass the internal name of the declaring class
     * @param methodName the method name
     * @param returnType the return type
     * @param args the argument expressions
     * @return the call expression
     */
    public MethodCallExpr instanceCall(Expression receiver, String ownerClass, String methodName, SourceType returnType, Expression... args)
    {
        return MethodCallExpr.instanceCall(receiver, methodName, ownerClass, Arrays.asList(args), returnType);
    }

    /**
     * Starts an allocation for incremental configuration.
     *
     * @param className the class to allocate, in dotted or internal form
     * @return the builder
     */
    public NewExprBuilder newInstance(String className)
    {
        return new NewExprBuilder(className);
    }

    /**
     * Creates an allocation expression.
     *
     * @param className the class to allocate
     * @param args the constructor argument expressions
     * @return the allocation expression
     */
    public NewExpr newExpr(String className, Expression... args)
    {
        return new NewExpr(className, Arrays.asList(args));
    }

    /**
     * Creates a sized array allocation.
     *
     * @param elementType the element type
     * @param size the length expression
     * @return the allocation expression
     */
    public NewArrayExpr newArray(SourceType elementType, Expression size)
    {
        return NewArrayExpr.withSize(elementType, size);
    }

    /**
     * Creates a sized array allocation, parsing the element type from its name.
     *
     * @param typeName the element type name
     * @param size the length expression
     * @return the allocation expression
     */
    public NewArrayExpr newArray(String typeName, Expression size)
    {
        return NewArrayExpr.withSize(parseType(typeName), size);
    }

    /**
     * Creates a binary expression.
     *
     * @param left the left operand
     * @param op the operator
     * @param right the right operand
     * @param type the result type
     * @return the binary expression
     */
    public BinaryExpr binary(Expression left, BinaryOperator op, Expression right, SourceType type)
    {
        return new BinaryExpr(op, left, right, type);
    }

    /**
     * Creates an addition expression.
     *
     * @param left the left operand
     * @param right the right operand
     * @param type the result type
     * @return the binary expression
     */
    public BinaryExpr add(Expression left, Expression right, SourceType type)
    {
        return binary(left, BinaryOperator.ADD, right, type);
    }

    /**
     * Creates a subtraction expression.
     *
     * @param left the left operand
     * @param right the right operand
     * @param type the result type
     * @return the binary expression
     */
    public BinaryExpr subtract(Expression left, Expression right, SourceType type)
    {
        return binary(left, BinaryOperator.SUB, right, type);
    }

    /**
     * Creates a multiplication expression.
     *
     * @param left the left operand
     * @param right the right operand
     * @param type the result type
     * @return the binary expression
     */
    public BinaryExpr multiply(Expression left, Expression right, SourceType type)
    {
        return binary(left, BinaryOperator.MUL, right, type);
    }

    /**
     * Creates a division expression.
     *
     * @param left the left operand
     * @param right the right operand
     * @param type the result type
     * @return the binary expression
     */
    public BinaryExpr divide(Expression left, Expression right, SourceType type)
    {
        return binary(left, BinaryOperator.DIV, right, type);
    }

    /**
     * Creates an equality comparison typed boolean.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public BinaryExpr equals(Expression left, Expression right)
    {
        return binary(left, BinaryOperator.EQ, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates an inequality comparison typed boolean.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public BinaryExpr notEquals(Expression left, Expression right)
    {
        return binary(left, BinaryOperator.NE, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates a less-than comparison typed boolean.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public BinaryExpr lessThan(Expression left, Expression right)
    {
        return binary(left, BinaryOperator.LT, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates a greater-than comparison typed boolean.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public BinaryExpr greaterThan(Expression left, Expression right)
    {
        return binary(left, BinaryOperator.GT, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates a logical AND expression typed boolean.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public BinaryExpr and(Expression left, Expression right)
    {
        return binary(left, BinaryOperator.AND, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates a logical OR expression typed boolean.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public BinaryExpr or(Expression left, Expression right)
    {
        return binary(left, BinaryOperator.OR, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates an assignment expression.
     *
     * @param left the assignment target
     * @param right the assigned value
     * @param type the result type
     * @return the binary expression
     */
    public BinaryExpr assign(Expression left, Expression right, SourceType type)
    {
        return binary(left, BinaryOperator.ASSIGN, right, type);
    }

    /**
     * Creates a unary expression.
     *
     * @param op the operator
     * @param operand the operand
     * @param type the result type
     * @return the unary expression
     */
    public UnaryExpr unary(UnaryOperator op, Expression operand, SourceType type)
    {
        return new UnaryExpr(op, operand, type);
    }

    /**
     * Creates an arithmetic negation.
     *
     * @param operand the operand
     * @param type the result type
     * @return the unary expression
     */
    public UnaryExpr negate(Expression operand, SourceType type)
    {
        return unary(UnaryOperator.NEG, operand, type);
    }

    /**
     * Creates a logical NOT typed boolean.
     *
     * @param operand the operand
     * @return the unary expression
     */
    public UnaryExpr not(Expression operand)
    {
        return unary(UnaryOperator.NOT, operand, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates a pre-increment expression.
     *
     * @param operand the operand
     * @param type the result type
     * @return the unary expression
     */
    public UnaryExpr preIncrement(Expression operand, SourceType type)
    {
        return unary(UnaryOperator.PRE_INC, operand, type);
    }

    /**
     * Creates a post-increment expression.
     *
     * @param operand the operand
     * @param type the result type
     * @return the unary expression
     */
    public UnaryExpr postIncrement(Expression operand, SourceType type)
    {
        return unary(UnaryOperator.POST_INC, operand, type);
    }

    /**
     * Creates a cast expression.
     *
     * @param targetType the type cast to
     * @param expr the operand
     * @return the cast expression
     */
    public CastExpr cast(SourceType targetType, Expression expr)
    {
        return new CastExpr(targetType, expr);
    }

    /**
     * Creates a cast expression, parsing the target from its name.
     *
     * @param typeName the target type name
     * @param expr the operand
     * @return the cast expression
     */
    public CastExpr cast(String typeName, Expression expr)
    {
        return new CastExpr(parseType(typeName), expr);
    }

    /**
     * Creates an instanceof test.
     *
     * @param expr the operand
     * @param checkedType the type tested against
     * @return the instanceof expression
     */
    public InstanceOfExpr instanceOf(Expression expr, SourceType checkedType)
    {
        return new InstanceOfExpr(expr, checkedType);
    }

    /**
     * Creates an instanceof test, parsing the tested type from its name.
     *
     * @param expr the operand
     * @param typeName the tested type name
     * @return the instanceof expression
     */
    public InstanceOfExpr instanceOf(Expression expr, String typeName)
    {
        return new InstanceOfExpr(expr, parseType(typeName));
    }

    /**
     * Creates a ternary expression.
     *
     * @param condition the selecting condition
     * @param thenExpr the value when the condition holds
     * @param elseExpr the value otherwise
     * @param type the result type
     * @return the ternary expression
     */
    public TernaryExpr ternary(Expression condition, Expression thenExpr, Expression elseExpr, SourceType type)
    {
        return new TernaryExpr(condition, thenExpr, elseExpr, type);
    }

    /**
     * Wraps an expression as a statement.
     *
     * @param expr the expression to evaluate for effect
     * @return the statement
     */
    public ExprStmt exprStmt(Expression expr)
    {
        return new ExprStmt(expr);
    }

    /**
     * Creates a return statement with a value.
     *
     * @param value the returned expression
     * @return the statement
     */
    public ReturnStmt returnStmt(Expression value)
    {
        return new ReturnStmt(value);
    }

    /**
     * Creates a valueless return statement.
     *
     * @return the statement
     */
    public ReturnStmt returnVoid()
    {
        return new ReturnStmt();
    }

    /**
     * Creates a throw statement.
     *
     * @param exception the thrown expression
     * @return the statement
     */
    public ThrowStmt throwStmt(Expression exception)
    {
        return new ThrowStmt(exception);
    }

    /**
     * Creates a block statement.
     *
     * @param stmts the block contents, in order
     * @return the statement
     */
    public BlockStmt block(Statement... stmts)
    {
        return new BlockStmt(Arrays.asList(stmts));
    }

    /**
     * Creates a block statement.
     *
     * @param stmts the block contents, in order
     * @return the statement
     */
    public BlockStmt block(List<Statement> stmts)
    {
        return new BlockStmt(stmts);
    }

    /**
     * Creates an if statement without an else arm.
     *
     * @param condition the guard
     * @param thenBranch the guarded statement
     * @return the statement
     */
    public IfStmt ifStmt(Expression condition, Statement thenBranch)
    {
        return new IfStmt(condition, thenBranch);
    }

    /**
     * Creates an if statement with an else arm.
     *
     * @param condition the guard
     * @param thenBranch the statement run when the guard holds
     * @param elseBranch the statement run otherwise
     * @return the statement
     */
    public IfStmt ifElseStmt(Expression condition, Statement thenBranch, Statement elseBranch)
    {
        return new IfStmt(condition, thenBranch, elseBranch);
    }

    /**
     * Creates an initialized local variable declaration.
     *
     * @param type the declared type
     * @param name the variable name
     * @param initializer the initial value
     * @return the statement
     */
    public VarDeclStmt varDecl(SourceType type, String name, Expression initializer)
    {
        return new VarDeclStmt(type, name, initializer);
    }

    /**
     * Creates an initialized local variable declaration, parsing the type from its name.
     *
     * @param typeName the declared type name
     * @param name the variable name
     * @param initializer the initial value
     * @return the statement
     */
    public VarDeclStmt varDecl(String typeName, String name, Expression initializer)
    {
        return new VarDeclStmt(parseType(typeName), name, initializer);
    }

    /**
     * Creates an uninitialized local variable declaration.
     *
     * @param type the declared type
     * @param name the variable name
     * @return the statement
     */
    public VarDeclStmt varDecl(SourceType type, String name)
    {
        return new VarDeclStmt(type, name);
    }

    /**
     * Parses a type from a string representation.
     * @param typeName the type name to parse
     * @return the parsed source type
     */
    public SourceType parseType(String typeName)
    {
        if (typeName == null || typeName.isEmpty())
        {
            return ReferenceSourceType.OBJECT;
        }

        switch (typeName)
        {
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

        if (typeName.endsWith("[]"))
        {
            String componentTypeName = typeName.substring(0, typeName.length() - 2);
            return new ArraySourceType(parseType(componentTypeName));
        }

        return new ReferenceSourceType(typeName.replace('.', '/'));
    }

    /**
     * Builder for method call expressions.
     */
    public static class MethodCallBuilder
    {
        private final String methodName;
        private Expression receiver;
        private String ownerClass;
        private boolean isStatic;
        private final List<Expression> arguments = new ArrayList<>();
        private SourceType returnType = VoidSourceType.INSTANCE;

        MethodCallBuilder(String methodName)
        {
            this.methodName = methodName;
        }

        /**
         * Makes the call an instance call on the given receiver, inferring the owner from a field
         * access or defaulting it to java/lang/Object for a variable.
         *
         * @param target the receiver expression
         * @return this builder
         */
        public MethodCallBuilder on(Expression target)
        {
            this.receiver = target;
            this.isStatic = false;
            if (target instanceof FieldAccessExpr)
            {
                this.ownerClass = ((FieldAccessExpr) target).getOwnerClass();
            }
            else if (target instanceof VarRefExpr)
            {
                this.ownerClass = "java/lang/Object";
            }
            return this;
        }

        /**
         * Makes the call a static call on the given class, dropping any receiver.
         *
         * @param staticOwner the declaring class, in dotted or internal form
         * @return this builder
         */
        public MethodCallBuilder on(String staticOwner)
        {
            this.ownerClass = staticOwner.replace('.', '/');
            this.isStatic = true;
            this.receiver = null;
            return this;
        }

        /**
         * Overrides the declaring class without changing the receiver.
         *
         * @param ownerClass the declaring class, in dotted or internal form
         * @return this builder
         */
        public MethodCallBuilder owner(String ownerClass)
        {
            this.ownerClass = ownerClass.replace('.', '/');
            return this;
        }

        /**
         * Appends arguments to the call.
         *
         * @param args the arguments to append, in order
         * @return this builder
         */
        public MethodCallBuilder withArgs(Expression... args)
        {
            this.arguments.addAll(Arrays.asList(args));
            return this;
        }

        /**
         * Appends arguments to the call.
         *
         * @param args the arguments to append, in order
         * @return this builder
         */
        public MethodCallBuilder withArgs(List<Expression> args)
        {
            this.arguments.addAll(args);
            return this;
        }

        /**
         * Sets the return type; defaults to void.
         *
         * @param type the return type
         * @return this builder
         */
        public MethodCallBuilder returning(SourceType type)
        {
            this.returnType = type;
            return this;
        }

        /**
         * Builds the call, falling back to java/lang/Object when no owner was set.
         *
         * @return the call expression
         */
        public MethodCallExpr build()
        {
            String owner = ownerClass != null ? ownerClass : "java/lang/Object";
            return new MethodCallExpr(receiver, methodName, owner, arguments, isStatic, returnType);
        }

        /**
         * Builds the call and wraps it as a statement.
         *
         * @return the statement
         */
        public ExprStmt asStatement()
        {
            return new ExprStmt(build());
        }
    }

    /**
     * Builder for new object expressions.
     */
    public static class NewExprBuilder
    {
        private final String className;
        private final List<Expression> arguments = new ArrayList<>();
        private final SourceType type;

        NewExprBuilder(String className)
        {
            this.className = className.replace('.', '/');
            this.type = new ReferenceSourceType(this.className);
        }

        /**
         * Appends constructor arguments.
         *
         * @param args the arguments to append, in order
         * @return this builder
         */
        public NewExprBuilder withArgs(Expression... args)
        {
            this.arguments.addAll(Arrays.asList(args));
            return this;
        }

        /**
         * Appends constructor arguments.
         *
         * @param args the arguments to append, in order
         * @return this builder
         */
        public NewExprBuilder withArgs(List<Expression> args)
        {
            this.arguments.addAll(args);
            return this;
        }

        /**
         * Builds the allocation typed as the allocated class.
         *
         * @return the allocation expression
         */
        public NewExpr build()
        {
            return new NewExpr(className, arguments, type);
        }

        /**
         * Builds the allocation and wraps it as a statement.
         *
         * @return the statement
         */
        public ExprStmt asStatement()
        {
            return new ExprStmt(build());
        }
    }
}
