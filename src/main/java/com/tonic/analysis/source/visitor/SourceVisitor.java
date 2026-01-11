package com.tonic.analysis.source.visitor;

import com.tonic.analysis.source.ast.decl.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.*;

/**
 * Visitor interface for traversing source AST nodes.
 * Each node type has a corresponding visit method.
 *
 * @param <T> the return type of visit methods
 */
public interface SourceVisitor<T> {

    // ==================== Declarations ====================

    default T visitCompilationUnit(CompilationUnit cu) { return null; }
    default T visitImportDecl(ImportDecl decl) { return null; }
    default T visitClassDecl(ClassDecl decl) { return null; }
    default T visitInterfaceDecl(InterfaceDecl decl) { return null; }
    default T visitEnumDecl(EnumDecl decl) { return null; }
    default T visitEnumConstantDecl(EnumConstantDecl decl) { return null; }
    default T visitMethodDecl(MethodDecl decl) { return null; }
    default T visitConstructorDecl(ConstructorDecl decl) { return null; }
    default T visitFieldDecl(FieldDecl decl) { return null; }
    default T visitParameterDecl(ParameterDecl decl) { return null; }
    default T visitAnnotationExpr(AnnotationExpr expr) { return null; }

    // ==================== Statements ====================

    T visitBlock(BlockStmt stmt);
    T visitIf(IfStmt stmt);
    T visitWhile(WhileStmt stmt);
    T visitDoWhile(DoWhileStmt stmt);
    T visitFor(ForStmt stmt);
    T visitForEach(ForEachStmt stmt);
    T visitSwitch(SwitchStmt stmt);
    T visitTryCatch(TryCatchStmt stmt);
    T visitReturn(ReturnStmt stmt);
    T visitThrow(ThrowStmt stmt);
    T visitVarDecl(VarDeclStmt stmt);
    T visitExprStmt(ExprStmt stmt);
    T visitSynchronized(SynchronizedStmt stmt);
    T visitLabeled(LabeledStmt stmt);
    T visitBreak(BreakStmt stmt);
    T visitContinue(ContinueStmt stmt);
    T visitIRRegion(IRRegionStmt stmt);

    // ==================== Expressions ====================

    T visitLiteral(LiteralExpr expr);
    T visitVarRef(VarRefExpr expr);
    T visitFieldAccess(FieldAccessExpr expr);
    T visitArrayAccess(ArrayAccessExpr expr);
    T visitMethodCall(MethodCallExpr expr);
    T visitNew(NewExpr expr);
    T visitNewArray(NewArrayExpr expr);
    T visitArrayInit(ArrayInitExpr expr);
    T visitBinary(BinaryExpr expr);
    T visitUnary(UnaryExpr expr);
    T visitCast(CastExpr expr);
    T visitInstanceOf(InstanceOfExpr expr);
    T visitTernary(TernaryExpr expr);
    T visitLambda(LambdaExpr expr);
    T visitMethodRef(MethodRefExpr expr);
    T visitThis(ThisExpr expr);
    T visitSuper(SuperExpr expr);
    T visitClass(ClassExpr expr);
    T visitDynamicConstant(DynamicConstantExpr expr);
    T visitInvokeDynamic(InvokeDynamicExpr expr);

    // ==================== Types ====================

    T visitPrimitiveType(PrimitiveSourceType type);
    T visitReferenceType(ReferenceSourceType type);
    T visitArrayType(ArraySourceType type);
    T visitVoidType(VoidSourceType type);
}
