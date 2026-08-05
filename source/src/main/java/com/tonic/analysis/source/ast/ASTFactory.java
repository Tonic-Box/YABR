package com.tonic.analysis.source.ast;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;

import java.util.Arrays;
import java.util.List;

/**
 * Static factory of AST nodes with sensible type defaults.
 */
public final class ASTFactory
{

    private ASTFactory() {}

    // Literal Expressions

    /**
     * Builds an int literal node.
     * @param value the literal value
     * @return the literal expression
     */
    public static LiteralExpr intLit(int value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.INT);
    }

    /**
     * Builds a long literal node.
     * @param value the literal value
     * @return the literal expression
     */
    public static LiteralExpr longLit(long value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.LONG);
    }

    /**
     * Builds a float literal node.
     * @param value the literal value
     * @return the literal expression
     */
    public static LiteralExpr floatLit(float value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.FLOAT);
    }

    /**
     * Builds a double literal node.
     * @param value the literal value
     * @return the literal expression
     */
    public static LiteralExpr doubleLit(double value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.DOUBLE);
    }

    /**
     * Builds a boolean literal node.
     * @param value the literal value
     * @return the literal expression
     */
    public static LiteralExpr boolLit(boolean value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Builds a char literal node.
     * @param value the literal value
     * @return the literal expression
     */
    public static LiteralExpr charLit(char value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.CHAR);
    }

    /**
     * Builds a String literal node.
     * @param value the literal value
     * @return the literal expression
     */
    public static LiteralExpr stringLit(String value)
    {
        return new LiteralExpr(value, ReferenceSourceType.STRING);
    }

    /**
     * Builds a null literal node.
     * @return the null literal expression
     */
    public static LiteralExpr nullLit()
    {
        return LiteralExpr.ofNull();
    }

    // Variable References

    /**
     * Builds a variable reference node.
     * @param name the variable name
     * @param type the variable type
     * @return the variable reference
     */
    public static VarRefExpr varRef(String name, SourceType type)
    {
        return new VarRefExpr(name, type);
    }

    /**
     * Builds an int-typed variable reference node.
     * @param name the variable name
     * @return the variable reference
     */
    public static VarRefExpr intVar(String name)
    {
        return new VarRefExpr(name, PrimitiveSourceType.INT);
    }

    /**
     * Builds a boolean-typed variable reference node.
     * @param name the variable name
     * @return the variable reference
     */
    public static VarRefExpr boolVar(String name)
    {
        return new VarRefExpr(name, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Builds a reference-typed variable reference node.
     * @param name the variable name
     * @param className the class of the variable's type
     * @return the variable reference
     */
    public static VarRefExpr objectVar(String name, String className)
    {
        return new VarRefExpr(name, new ReferenceSourceType(className));
    }

    // Binary Expressions

    /**
     * Builds a binary expression node.
     * @param op the operator
     * @param left the left operand
     * @param right the right operand
     * @param type the result type
     * @return the binary expression
     */
    public static BinaryExpr binary(BinaryOperator op, Expression left, Expression right, SourceType type)
    {
        return new BinaryExpr(op, left, right, type);
    }

    /**
     * Builds an addition node typed from the left operand.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr add(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.ADD, left, right, left.getType());
    }

    /**
     * Builds a subtraction node typed from the left operand.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr sub(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.SUB, left, right, left.getType());
    }

    /**
     * Builds a multiplication node typed from the left operand.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr mul(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.MUL, left, right, left.getType());
    }

    /**
     * Builds a division node typed from the left operand.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr div(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.DIV, left, right, left.getType());
    }

    /**
     * Builds a modulo node typed from the left operand.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr mod(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.MOD, left, right, left.getType());
    }

    /**
     * Builds a boolean equality comparison node.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr eq(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.EQ, left, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Builds a boolean inequality comparison node.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr ne(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.NE, left, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Builds a boolean less-than comparison node.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr lt(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.LT, left, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Builds a boolean less-or-equal comparison node.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr le(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.LE, left, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Builds a boolean greater-than comparison node.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr gt(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.GT, left, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Builds a boolean greater-or-equal comparison node.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr ge(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.GE, left, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Builds a logical AND node.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr and(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.AND, left, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Builds a logical OR node.
     * @param left the left operand
     * @param right the right operand
     * @return the binary expression
     */
    public static BinaryExpr or(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.OR, left, right, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Builds an assignment expression node typed from the target.
     * @param left the assignment target
     * @param right the assigned value
     * @return the binary expression
     */
    public static BinaryExpr assign(Expression left, Expression right)
    {
        return new BinaryExpr(BinaryOperator.ASSIGN, left, right, left.getType());
    }

    // Unary Expressions

    /**
     * Builds a unary expression node.
     * @param op the operator
     * @param operand the operand
     * @param type the result type
     * @return the unary expression
     */
    public static UnaryExpr unary(UnaryOperator op, Expression operand, SourceType type)
    {
        return new UnaryExpr(op, operand, type);
    }

    /**
     * Builds a logical negation node.
     * @param operand the operand
     * @return the unary expression
     */
    public static UnaryExpr not(Expression operand)
    {
        return new UnaryExpr(UnaryOperator.NOT, operand, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Builds an arithmetic negation node typed from the operand.
     * @param operand the operand
     * @return the unary expression
     */
    public static UnaryExpr neg(Expression operand)
    {
        return new UnaryExpr(UnaryOperator.NEG, operand, operand.getType());
    }

    /**
     * Builds a pre-increment node typed from the operand.
     * @param operand the operand
     * @return the unary expression
     */
    public static UnaryExpr preIncr(Expression operand)
    {
        return new UnaryExpr(UnaryOperator.PRE_INC, operand, operand.getType());
    }

    /**
     * Builds a pre-decrement node typed from the operand.
     * @param operand the operand
     * @return the unary expression
     */
    public static UnaryExpr preDecr(Expression operand)
    {
        return new UnaryExpr(UnaryOperator.PRE_DEC, operand, operand.getType());
    }

    /**
     * Builds a post-increment node typed from the operand.
     * @param operand the operand
     * @return the unary expression
     */
    public static UnaryExpr postIncr(Expression operand)
    {
        return new UnaryExpr(UnaryOperator.POST_INC, operand, operand.getType());
    }

    /**
     * Builds a post-decrement node typed from the operand.
     * @param operand the operand
     * @return the unary expression
     */
    public static UnaryExpr postDecr(Expression operand)
    {
        return new UnaryExpr(UnaryOperator.POST_DEC, operand, operand.getType());
    }

    // Other Expressions

    /**
     * Builds a ternary conditional node typed from the then branch.
     * @param condition the condition
     * @param thenExpr the value when true
     * @param elseExpr the value when false
     * @return the ternary expression
     */
    public static TernaryExpr ternary(Expression condition, Expression thenExpr, Expression elseExpr)
    {
        return new TernaryExpr(condition, thenExpr, elseExpr, thenExpr.getType());
    }

    /**
     * Builds a cast node.
     * @param targetType the type cast to
     * @param expr the expression being cast
     * @return the cast expression
     */
    public static CastExpr cast(SourceType targetType, Expression expr)
    {
        return new CastExpr(targetType, expr);
    }

    /**
     * Builds an instanceof test node.
     * @param expr the tested expression
     * @param checkType the type tested against
     * @return the instanceof expression
     */
    public static InstanceOfExpr instanceOf(Expression expr, SourceType checkType)
    {
        return new InstanceOfExpr(expr, checkType);
    }

    /**
     * Builds an instanceof test node with a pattern variable.
     * @param expr the tested expression
     * @param checkType the type tested against
     * @param patternVar the binding variable name
     * @return the instanceof expression
     */
    public static InstanceOfExpr instanceOf(Expression expr, SourceType checkType, String patternVar)
    {
        return new InstanceOfExpr(expr, checkType, patternVar);
    }

    /**
     * Builds an array element access node.
     * @param array the array expression
     * @param index the index expression
     * @param elementType the element type
     * @return the array access expression
     */
    public static ArrayAccessExpr arrayAccess(Expression array, Expression index, SourceType elementType)
    {
        return new ArrayAccessExpr(array, index, elementType);
    }

    /**
     * Builds an object instantiation node.
     * @param className the class being instantiated
     * @param args the constructor arguments
     * @return the new expression
     */
    public static NewExpr newObj(String className, Expression... args)
    {
        return new NewExpr(className, Arrays.asList(args));
    }

    /**
     * Builds a sized array allocation node.
     * @param elementType the element type
     * @param size the length expression
     * @return the new-array expression
     */
    public static NewArrayExpr newArray(SourceType elementType, Expression size)
    {
        return NewArrayExpr.withSize(elementType, size);
    }

    /**
     * Builds an array initializer node.
     * @param elementType the element type
     * @param elements the initial elements
     * @return the array initializer expression
     */
    public static ArrayInitExpr arrayInit(SourceType elementType, Expression... elements)
    {
        return ArrayInitExpr.of(elementType, Arrays.asList(elements));
    }

    /**
     * Builds a this reference node.
     * @param type the enclosing class type
     * @return the this expression
     */
    public static ThisExpr thisExpr(SourceType type)
    {
        return new ThisExpr(type);
    }

    /**
     * Builds a class literal node.
     * @param classType the type whose class is referenced
     * @return the class expression
     */
    public static ClassExpr classExpr(SourceType classType)
    {
        return new ClassExpr(classType);
    }

    // Field Access

    /**
     * Builds an instance field access node.
     * @param receiver the receiver expression
     * @param fieldName the field name
     * @param ownerClass the class declaring the field
     * @param type the field type
     * @return the field access expression
     */
    public static FieldAccessExpr fieldAccess(Expression receiver, String fieldName, String ownerClass, SourceType type)
    {
        return FieldAccessExpr.instanceField(receiver, fieldName, ownerClass, type);
    }

    /**
     * Builds a static field access node.
     * @param ownerClass the class declaring the field
     * @param fieldName the field name
     * @param type the field type
     * @return the field access expression
     */
    public static FieldAccessExpr staticField(String ownerClass, String fieldName, SourceType type)
    {
        return FieldAccessExpr.staticField(ownerClass, fieldName, type);
    }

    // Method Calls

    /**
     * Builds an instance method call node.
     * @param receiver the receiver expression
     * @param methodName the method name
     * @param ownerClass the class declaring the method
     * @param returnType the return type
     * @param args the call arguments
     * @return the method call expression
     */
    public static MethodCallExpr methodCall(Expression receiver, String methodName, String ownerClass, SourceType returnType, Expression... args)
    {
        return MethodCallExpr.instanceCall(receiver, methodName, ownerClass, Arrays.asList(args), returnType);
    }

    /**
     * Builds a static method call node.
     * @param ownerClass the class declaring the method
     * @param methodName the method name
     * @param returnType the return type
     * @param args the call arguments
     * @return the method call expression
     */
    public static MethodCallExpr staticCall(String ownerClass, String methodName, SourceType returnType, Expression... args)
    {
        return MethodCallExpr.staticCall(ownerClass, methodName, Arrays.asList(args), returnType);
    }

    // Statements

    /**
     * Builds a block statement node.
     * @param statements the contained statements
     * @return the block statement
     */
    public static BlockStmt block(Statement... statements)
    {
        return new BlockStmt(Arrays.asList(statements));
    }

    /**
     * Builds a block statement node.
     * @param statements the contained statements
     * @return the block statement
     */
    public static BlockStmt block(List<Statement> statements)
    {
        return new BlockStmt(statements);
    }

    /**
     * Builds an if statement node without an else branch.
     * @param condition the condition
     * @param thenBranch the branch taken when true
     * @return the if statement
     */
    public static IfStmt ifStmt(Expression condition, Statement thenBranch)
    {
        return new IfStmt(condition, thenBranch);
    }

    /**
     * Builds an if-else statement node.
     * @param condition the condition
     * @param thenBranch the branch taken when true
     * @param elseBranch the branch taken when false
     * @return the if statement
     */
    public static IfStmt ifElse(Expression condition, Statement thenBranch, Statement elseBranch)
    {
        return new IfStmt(condition, thenBranch, elseBranch);
    }

    /**
     * Builds a while loop node.
     * @param condition the loop condition
     * @param body the loop body
     * @return the while statement
     */
    public static WhileStmt whileLoop(Expression condition, Statement body)
    {
        return new WhileStmt(condition, body);
    }

    /**
     * Builds a do-while loop node.
     * @param body the loop body
     * @param condition the loop condition
     * @return the do-while statement
     */
    public static DoWhileStmt doWhile(Statement body, Expression condition)
    {
        return new DoWhileStmt(body, condition);
    }

    /**
     * Builds a for loop node.
     * @param init the initializer statements
     * @param condition the loop condition
     * @param update the update expressions
     * @param body the loop body
     * @return the for statement
     */
    public static ForStmt forLoop(List<Statement> init, Expression condition, List<Expression> update, Statement body)
    {
        return new ForStmt(init, condition, update, body);
    }

    /**
     * Builds an infinite for loop node.
     * @param body the loop body
     * @return the for statement
     */
    public static ForStmt infiniteLoop(Statement body)
    {
        return ForStmt.infinite(body);
    }

    /**
     * Builds an enhanced-for loop node.
     * @param variable the loop variable declaration
     * @param iterable the iterated expression
     * @param body the loop body
     * @return the for-each statement
     */
    public static ForEachStmt forEach(VarDeclStmt variable, Expression iterable, Statement body)
    {
        return new ForEachStmt(variable, iterable, body);
    }

    /**
     * Builds a value-returning return statement node.
     * @param value the returned expression
     * @return the return statement
     */
    public static ReturnStmt returnStmt(Expression value)
    {
        return new ReturnStmt(value);
    }

    /**
     * Builds a void return statement node.
     * @return the return statement
     */
    public static ReturnStmt returnVoid()
    {
        return new ReturnStmt();
    }

    /**
     * Builds a throw statement node.
     * @param exception the thrown expression
     * @return the throw statement
     */
    public static ThrowStmt throwStmt(Expression exception)
    {
        return new ThrowStmt(exception);
    }

    /**
     * Builds an unlabeled break statement node.
     * @return the break statement
     */
    public static BreakStmt breakStmt()
    {
        return new BreakStmt();
    }

    /**
     * Builds a labeled break statement node.
     * @param label the target label
     * @return the break statement
     */
    public static BreakStmt breakStmt(String label)
    {
        return new BreakStmt(label);
    }

    /**
     * Builds an unlabeled continue statement node.
     * @return the continue statement
     */
    public static ContinueStmt continueStmt()
    {
        return new ContinueStmt();
    }

    /**
     * Builds a labeled continue statement node.
     * @param label the target label
     * @return the continue statement
     */
    public static ContinueStmt continueStmt(String label)
    {
        return new ContinueStmt(label);
    }

    /**
     * Builds an expression statement node.
     * @param expr the wrapped expression
     * @return the expression statement
     */
    public static ExprStmt exprStmt(Expression expr)
    {
        return new ExprStmt(expr);
    }

    /**
     * Builds an uninitialized local variable declaration node.
     * @param type the variable type
     * @param name the variable name
     * @return the declaration statement
     */
    public static VarDeclStmt varDecl(SourceType type, String name)
    {
        return new VarDeclStmt(type, name);
    }

    /**
     * Builds an initialized local variable declaration node.
     * @param type the variable type
     * @param name the variable name
     * @param initializer the initial value
     * @return the declaration statement
     */
    public static VarDeclStmt varDecl(SourceType type, String name, Expression initializer)
    {
        return new VarDeclStmt(type, name, initializer);
    }

    /**
     * Builds a final local variable declaration node.
     * @param type the variable type
     * @param name the variable name
     * @param initializer the initial value
     * @return the declaration statement
     */
    public static VarDeclStmt finalVar(SourceType type, String name, Expression initializer)
    {
        return new VarDeclStmt(type, name, initializer, false, true, SourceLocation.UNKNOWN);
    }

    /**
     * Builds a labeled statement node.
     * @param label the label name
     * @param statement the labeled statement
     * @return the labeled statement
     */
    public static LabeledStmt labeled(String label, Statement statement)
    {
        return new LabeledStmt(label, statement);
    }

    /**
     * Builds a synchronized block node.
     * @param lock the monitor expression
     * @param body the guarded body
     * @return the synchronized statement
     */
    public static SynchronizedStmt synchronizedStmt(Expression lock, Statement body)
    {
        return new SynchronizedStmt(lock, body);
    }

    /**
     * Builds a try-catch statement node.
     * @param tryBlock the guarded block
     * @param catches the catch clauses
     * @return the try-catch statement
     */
    public static TryCatchStmt tryCatch(Statement tryBlock, List<CatchClause> catches)
    {
        return new TryCatchStmt(tryBlock, catches);
    }

    /**
     * Builds a try-catch-finally statement node.
     * @param tryBlock the guarded block
     * @param catches the catch clauses
     * @param finallyBlock the finally block
     * @return the try-catch statement
     */
    public static TryCatchStmt tryCatchFinally(Statement tryBlock, List<CatchClause> catches, Statement finallyBlock)
    {
        return new TryCatchStmt(tryBlock, catches, finallyBlock);
    }

    /**
     * Builds a switch statement node.
     * @param selector the switched expression
     * @param cases the case groups
     * @return the switch statement
     */
    public static SwitchStmt switchStmt(Expression selector, List<SwitchCase> cases)
    {
        return new SwitchStmt(selector, cases);
    }

    // Type Utilities

    /**
     * Builds a reference type node.
     * @param className the referenced class
     * @return the reference type
     */
    public static ReferenceSourceType refType(String className)
    {
        return new ReferenceSourceType(className);
    }

    /**
     * Builds a one-dimensional array type node.
     * @param elementType the element type
     * @return the array type
     */
    public static ArraySourceType arrayType(SourceType elementType)
    {
        return new ArraySourceType(elementType);
    }

    /**
     * Builds a multi-dimensional array type node.
     * @param elementType the element type
     * @param dimensions the number of dimensions
     * @return the array type
     */
    public static ArraySourceType arrayType(SourceType elementType, int dimensions)
    {
        return new ArraySourceType(elementType, dimensions);
    }
}
