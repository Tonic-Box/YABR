package com.tonic.analysis.source.ast;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Visitor-backed helpers for traversing and querying AST nodes.
 */
public final class ASTUtils
{

    private ASTUtils() {}

    /**
     * Walks every statement under a root in depth-first order.
     *
     * @param root statement to walk
     * @param action invoked once per visited statement
     */
    public static void forEachStatement(Statement root, Consumer<Statement> action)
    {
        root.accept(new StatementWalker(action));
    }

    /**
     * Collects the statements under a root that are instances of one type.
     *
     * @param <T> statement type collected
     * @param root statement to walk
     * @param type type to match
     * @return the matching statements in visit order
     */
    public static <T extends Statement> List<T> collect(Statement root, Class<T> type)
    {
        List<T> result = new ArrayList<>();
        forEachStatement(root, stmt -> {
            if (type.isInstance(stmt))
            {
                result.add(type.cast(stmt));
            }
        });
        return result;
    }

    /**
     * Collects the statements under a root that satisfy a predicate.
     *
     * @param root statement to walk
     * @param pred test applied to each visited statement
     * @return the accepted statements in visit order
     */
    public static List<Statement> filter(Statement root, Predicate<Statement> pred)
    {
        List<Statement> result = new ArrayList<>();
        forEachStatement(root, stmt -> {
            if (pred.test(stmt))
            {
                result.add(stmt);
            }
        });
        return result;
    }

    /**
     * Walks every expression under a statement tree in depth-first order.
     *
     * @param root statement to walk
     * @param action invoked once per visited expression
     */
    public static void forEachExpression(Statement root, Consumer<Expression> action)
    {
        root.accept(new ExpressionWalker(action));
    }

    /**
     * Collects the expressions in a statement tree that are instances of one type.
     *
     * @param <T> expression type collected
     * @param root statement to walk
     * @param type type to match
     * @return the matching expressions in visit order
     */
    public static <T extends Expression> List<T> collectExpressions(Statement root, Class<T> type)
    {
        List<T> result = new ArrayList<>();
        forEachExpression(root, expr -> {
            if (type.isInstance(expr))
            {
                result.add(type.cast(expr));
            }
        });
        return result;
    }

    /**
     * Collects the expressions in a statement tree that satisfy a predicate.
     *
     * @param root statement to walk
     * @param pred test applied to each visited expression
     * @return the accepted expressions in visit order
     */
    public static List<Expression> filterExpressions(Statement root, Predicate<Expression> pred)
    {
        List<Expression> result = new ArrayList<>();
        forEachExpression(root, expr -> {
            if (pred.test(expr))
            {
                result.add(expr);
            }
        });
        return result;
    }

    /**
     * Visitor that walks statements and calls a consumer on each.
     */
    private static class StatementWalker extends AbstractSourceVisitor<Void>
    {
        private final Consumer<Statement> action;

        StatementWalker(Consumer<Statement> action)
        {
            this.action = action;
        }

        @Override
        public Void visitBlock(BlockStmt stmt)
        {
            action.accept(stmt);
            return super.visitBlock(stmt);
        }

        @Override
        public Void visitIf(IfStmt stmt)
        {
            action.accept(stmt);
            return super.visitIf(stmt);
        }

        @Override
        public Void visitWhile(WhileStmt stmt)
        {
            action.accept(stmt);
            return super.visitWhile(stmt);
        }

        @Override
        public Void visitDoWhile(DoWhileStmt stmt)
        {
            action.accept(stmt);
            return super.visitDoWhile(stmt);
        }

        @Override
        public Void visitFor(ForStmt stmt)
        {
            action.accept(stmt);
            return super.visitFor(stmt);
        }

        @Override
        public Void visitForEach(ForEachStmt stmt)
        {
            action.accept(stmt);
            return super.visitForEach(stmt);
        }

        @Override
        public Void visitSwitch(SwitchStmt stmt)
        {
            action.accept(stmt);
            return super.visitSwitch(stmt);
        }

        @Override
        public Void visitTryCatch(TryCatchStmt stmt)
        {
            action.accept(stmt);
            return super.visitTryCatch(stmt);
        }

        @Override
        public Void visitReturn(ReturnStmt stmt)
        {
            action.accept(stmt);
            return super.visitReturn(stmt);
        }

        @Override
        public Void visitThrow(ThrowStmt stmt)
        {
            action.accept(stmt);
            return super.visitThrow(stmt);
        }

        @Override
        public Void visitVarDecl(VarDeclStmt stmt)
        {
            action.accept(stmt);
            return super.visitVarDecl(stmt);
        }

        @Override
        public Void visitExprStmt(ExprStmt stmt)
        {
            action.accept(stmt);
            return super.visitExprStmt(stmt);
        }

        @Override
        public Void visitSynchronized(SynchronizedStmt stmt)
        {
            action.accept(stmt);
            return super.visitSynchronized(stmt);
        }

        @Override
        public Void visitLabeled(LabeledStmt stmt)
        {
            action.accept(stmt);
            return super.visitLabeled(stmt);
        }

        @Override
        public Void visitBreak(BreakStmt stmt)
        {
            action.accept(stmt);
            return super.visitBreak(stmt);
        }

        @Override
        public Void visitContinue(ContinueStmt stmt)
        {
            action.accept(stmt);
            return super.visitContinue(stmt);
        }

        @Override
        public Void visitIRRegion(IRRegionStmt stmt)
        {
            action.accept(stmt);
            return super.visitIRRegion(stmt);
        }
    }

    /**
     * Visitor that walks expressions and calls a consumer on each.
     */
    private static class ExpressionWalker extends AbstractSourceVisitor<Void>
    {
        private final Consumer<Expression> action;

        ExpressionWalker(Consumer<Expression> action)
        {
            this.action = action;
        }

        @Override
        public Void visitLiteral(LiteralExpr expr)
        {
            action.accept(expr);
            return super.visitLiteral(expr);
        }

        @Override
        public Void visitVarRef(VarRefExpr expr)
        {
            action.accept(expr);
            return super.visitVarRef(expr);
        }

        @Override
        public Void visitFieldAccess(FieldAccessExpr expr)
        {
            action.accept(expr);
            return super.visitFieldAccess(expr);
        }

        @Override
        public Void visitArrayAccess(ArrayAccessExpr expr)
        {
            action.accept(expr);
            return super.visitArrayAccess(expr);
        }

        @Override
        public Void visitMethodCall(MethodCallExpr expr)
        {
            action.accept(expr);
            return super.visitMethodCall(expr);
        }

        @Override
        public Void visitNew(NewExpr expr)
        {
            action.accept(expr);
            return super.visitNew(expr);
        }

        @Override
        public Void visitNewArray(NewArrayExpr expr)
        {
            action.accept(expr);
            return super.visitNewArray(expr);
        }

        @Override
        public Void visitArrayInit(ArrayInitExpr expr)
        {
            action.accept(expr);
            return super.visitArrayInit(expr);
        }

        @Override
        public Void visitBinary(BinaryExpr expr)
        {
            action.accept(expr);
            return super.visitBinary(expr);
        }

        @Override
        public Void visitUnary(UnaryExpr expr)
        {
            action.accept(expr);
            return super.visitUnary(expr);
        }

        @Override
        public Void visitCast(CastExpr expr)
        {
            action.accept(expr);
            return super.visitCast(expr);
        }

        @Override
        public Void visitInstanceOf(InstanceOfExpr expr)
        {
            action.accept(expr);
            return super.visitInstanceOf(expr);
        }

        @Override
        public Void visitTernary(TernaryExpr expr)
        {
            action.accept(expr);
            return super.visitTernary(expr);
        }

        @Override
        public Void visitLambda(LambdaExpr expr)
        {
            action.accept(expr);
            return super.visitLambda(expr);
        }

        @Override
        public Void visitMethodRef(MethodRefExpr expr)
        {
            action.accept(expr);
            return super.visitMethodRef(expr);
        }

        @Override
        public Void visitThis(ThisExpr expr)
        {
            action.accept(expr);
            return super.visitThis(expr);
        }

        @Override
        public Void visitSuper(SuperExpr expr)
        {
            action.accept(expr);
            return super.visitSuper(expr);
        }

        @Override
        public Void visitClass(ClassExpr expr)
        {
            action.accept(expr);
            return super.visitClass(expr);
        }
    }
}
