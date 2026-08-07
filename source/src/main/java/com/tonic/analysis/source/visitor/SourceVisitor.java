package com.tonic.analysis.source.visitor;

import com.tonic.analysis.source.ast.decl.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.*;

/**
 * Visitor interface for traversing source AST nodes.
 * @param <T> the return type of visit methods
 */
public interface SourceVisitor<T>
{

    // Declarations

    /**
     * Visits a compilation unit.
     * @param cu the compilation unit node
     * @return the visitor's result, null unless overridden
     */
    default T visitCompilationUnit(CompilationUnit cu) { return null; }

    /**
     * Visits an import declaration.
     * @param decl the declaration node
     * @return the visitor's result, null unless overridden
     */
    default T visitImportDecl(ImportDecl decl) { return null; }

    /**
     * Visits a class declaration.
     * @param decl the declaration node
     * @return the visitor's result, null unless overridden
     */
    default T visitClassDecl(ClassDecl decl) { return null; }

    /**
     * Visits an interface declaration.
     * @param decl the declaration node
     * @return the visitor's result, null unless overridden
     */
    default T visitInterfaceDecl(InterfaceDecl decl) { return null; }

    /**
     * Visits an enum declaration.
     * @param decl the declaration node
     * @return the visitor's result, null unless overridden
     */
    default T visitEnumDecl(EnumDecl decl) { return null; }

    /**
     * Visits one enum constant.
     * @param decl the declaration node
     * @return the visitor's result, null unless overridden
     */
    default T visitEnumConstantDecl(EnumConstantDecl decl) { return null; }

    /**
     * Visits a method declaration.
     * @param decl the declaration node
     * @return the visitor's result, null unless overridden
     */
    default T visitMethodDecl(MethodDecl decl) { return null; }

    /**
     * Visits a constructor declaration.
     * @param decl the declaration node
     * @return the visitor's result, null unless overridden
     */
    default T visitConstructorDecl(ConstructorDecl decl) { return null; }

    /**
     * Visits a field declaration.
     * @param decl the declaration node
     * @return the visitor's result, null unless overridden
     */
    default T visitFieldDecl(FieldDecl decl) { return null; }

    /**
     * Visits a method or constructor parameter.
     * @param decl the declaration node
     * @return the visitor's result, null unless overridden
     */
    default T visitParameterDecl(ParameterDecl decl) { return null; }

    /**
     * Visits an annotation use.
     * @param expr the annotation node
     * @return the visitor's result, null unless overridden
     */
    default T visitAnnotationExpr(AnnotationExpr expr) { return null; }

    // Statements

    /**
     * Visits a braced block.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitBlock(BlockStmt stmt);

    /**
     * Visits an if statement.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitIf(IfStmt stmt);

    /**
     * Visits a while loop.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitWhile(WhileStmt stmt);

    /**
     * Visits a do-while loop.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitDoWhile(DoWhileStmt stmt);

    /**
     * Visits a counted for loop.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitFor(ForStmt stmt);

    /**
     * Visits an enhanced for loop.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitForEach(ForEachStmt stmt);

    /**
     * Visits a switch statement.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitSwitch(SwitchStmt stmt);

    /**
     * Visits a try statement, with its catch clauses and finally block.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitTryCatch(TryCatchStmt stmt);

    /**
     * Visits a return statement.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitReturn(ReturnStmt stmt);

    /**
     * Visits a throw statement.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitThrow(ThrowStmt stmt);

    /**
     * Visits a local variable declaration.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitVarDecl(VarDeclStmt stmt);

    /**
     * Visits an expression used as a statement.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitExprStmt(ExprStmt stmt);

    /**
     * Visits a synchronized block.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitSynchronized(SynchronizedStmt stmt);

    /**
     * Visits a labeled statement.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitLabeled(LabeledStmt stmt);

    /**
     * Visits a break statement.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitBreak(BreakStmt stmt);

    /**
     * Visits a continue statement.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitContinue(ContinueStmt stmt);

    /**
     * Visits a region of control flow that stayed in IR form because it could not be structured.
     * @param stmt the statement node
     * @return the visitor's result
     */
    T visitIRRegion(IRRegionStmt stmt);

    // Expressions

    /**
     * Visits a literal.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitLiteral(LiteralExpr expr);

    /**
     * Visits a variable reference.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitVarRef(VarRefExpr expr);

    /**
     * Visits a field access.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitFieldAccess(FieldAccessExpr expr);

    /**
     * Visits an array element access.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitArrayAccess(ArrayAccessExpr expr);

    /**
     * Visits a method call.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitMethodCall(MethodCallExpr expr);

    /**
     * Visits an object allocation.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitNew(NewExpr expr);

    /**
     * Visits an array allocation.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitNewArray(NewArrayExpr expr);

    /**
     * Visits a braced array initializer.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitArrayInit(ArrayInitExpr expr);

    /**
     * Visits a binary operation.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitBinary(BinaryExpr expr);

    /**
     * Visits a unary operation.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitUnary(UnaryExpr expr);

    /**
     * Visits a cast.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitCast(CastExpr expr);

    /**
     * Visits an {@code instanceof} test.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitInstanceOf(InstanceOfExpr expr);

    /**
     * Visits a ternary conditional.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitTernary(TernaryExpr expr);
    /**
     * Default so existing visitors need no change; only the source emitter overrides it.
     *
     * @param expr the switch expression to visit
     * @return the visitor result, null unless overridden
     */
    default T visitSwitchExpr(SwitchExpr expr) { return null; }
    /**
     * Visits a lambda expression.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitLambda(LambdaExpr expr);

    /**
     * Visits a method reference.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitMethodRef(MethodRefExpr expr);

    /**
     * Visits a {@code this} reference.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitThis(ThisExpr expr);

    /**
     * Visits a {@code super} reference.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitSuper(SuperExpr expr);

    /**
     * Visits a class literal.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitClass(ClassExpr expr);

    /**
     * Visits a dynamically computed constant.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitDynamicConstant(DynamicConstantExpr expr);

    /**
     * Visits an invokedynamic call site.
     * @param expr the expression node
     * @return the visitor's result
     */
    T visitInvokeDynamic(InvokeDynamicExpr expr);

    // Types

    /**
     * Visits a primitive type.
     * @param type the type node
     * @return the visitor's result
     */
    T visitPrimitiveType(PrimitiveSourceType type);

    /**
     * Visits a class or interface type.
     * @param type the type node
     * @return the visitor's result
     */
    T visitReferenceType(ReferenceSourceType type);

    /**
     * Visits an array type.
     * @param type the type node
     * @return the visitor's result
     */
    T visitArrayType(ArraySourceType type);

    /**
     * Visits the void type.
     * @param type the type node
     * @return the visitor's result
     */
    T visitVoidType(VoidSourceType type);
}
