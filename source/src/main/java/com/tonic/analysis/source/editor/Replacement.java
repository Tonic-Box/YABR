package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.Statement;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The action an editor handler returns for a visited AST node: keep, replace, remove, or insert.
 */
public abstract class Replacement
{

    /**
     * The kind of action a replacement performs.
     */
    public enum Type
    {
        /**
         * No change.
         */
        KEEP,
        /**
         * Replace with new expression.
         */
        REPLACE_EXPR,
        /**
         * Replace with new statement.
         */
        REPLACE_STMT,
        /**
         * Replace with multiple statements.
         */
        REPLACE_BLOCK,
        /**
         * Remove entirely.
         */
        REMOVE,
        /**
         * Insert statements before current.
         */
        INSERT_BEFORE,
        /**
         * Insert statements after current.
         */
        INSERT_AFTER
    }

    private final Type type;

    protected Replacement(Type type)
    {
        this.type = type;
    }

    /**
     * @return the type
     */
    public Type getType()
    {
        return type;
    }

    /**
     * @return true if this replacement leaves the node unchanged
     */
    public boolean isKeep()
    {
        return type == Type.KEEP;
    }

    /**
     * @return true if this replacement substitutes an expression, statement, or block
     */
    public boolean isReplace()
    {
        return type == Type.REPLACE_EXPR || type == Type.REPLACE_STMT || type == Type.REPLACE_BLOCK;
    }

    /**
     * @return true if this replacement removes the node entirely
     */
    public boolean isRemove()
    {
        return type == Type.REMOVE;
    }

    /**
     * @return true if this replacement inserts a node before or after the original
     */
    public boolean isInsert()
    {
        return type == Type.INSERT_BEFORE || type == Type.INSERT_AFTER;
    }

    private static final Replacement KEEP_INSTANCE = new KeepReplacement();
    private static final Replacement REMOVE_INSTANCE = new RemoveReplacement();

    /**
     * @return the shared replacement that keeps the original node unchanged
     */
    public static Replacement keep()
    {
        return KEEP_INSTANCE;
    }

    /**
     * Creates a replacement that substitutes a new expression for the visited one.
     * @param newExpr the expression to substitute
     * @return the expression replacement
     * @throws IllegalArgumentException if newExpr is null
     */
    public static Replacement with(Expression newExpr)
    {
        if (newExpr == null)
        {
            throw new IllegalArgumentException("Replacement expression cannot be null");
        }
        return new ExprReplacement(newExpr);
    }

    /**
     * Creates a replacement that substitutes a new statement for the visited one.
     * @param newStmt the statement to substitute
     * @return the statement replacement
     * @throws IllegalArgumentException if newStmt is null
     */
    public static Replacement with(Statement newStmt)
    {
        if (newStmt == null)
        {
            throw new IllegalArgumentException("Replacement statement cannot be null");
        }
        return new StmtReplacement(newStmt);
    }

    /**
     * Creates a replacement that substitutes multiple statements for the visited one.
     * @param stmts the statements to substitute
     * @return the block replacement
     * @throws IllegalArgumentException if stmts is null or empty
     */
    public static Replacement withBlock(Statement... stmts)
    {
        if (stmts == null || stmts.length == 0)
        {
            throw new IllegalArgumentException("Block replacement requires at least one statement");
        }
        return new BlockReplacement(Arrays.asList(stmts));
    }

    /**
     * Creates a replacement that substitutes multiple statements for the visited one.
     * @param stmts the statements to substitute
     * @return the block replacement
     * @throws IllegalArgumentException if stmts is null or empty
     */
    public static Replacement withBlock(List<Statement> stmts)
    {
        if (stmts == null || stmts.isEmpty())
        {
            throw new IllegalArgumentException("Block replacement requires at least one statement");
        }
        return new BlockReplacement(stmts);
    }

    /**
     * @return the shared replacement that removes the node entirely
     */
    public static Replacement remove()
    {
        return REMOVE_INSTANCE;
    }

    /**
     * Creates a replacement that inserts statements before the current statement.
     * @param stmts the statements to insert
     * @return the insert-before replacement
     * @throws IllegalArgumentException if stmts is null or empty
     */
    public static Replacement insertBefore(Statement... stmts)
    {
        if (stmts == null || stmts.length == 0)
        {
            throw new IllegalArgumentException("Insert requires at least one statement");
        }
        return new InsertBeforeReplacement(Arrays.asList(stmts));
    }

    /**
     * Creates a replacement that inserts statements before the current statement.
     * @param stmts the statements to insert
     * @return the insert-before replacement
     * @throws IllegalArgumentException if stmts is null or empty
     */
    public static Replacement insertBefore(List<Statement> stmts)
    {
        if (stmts == null || stmts.isEmpty())
        {
            throw new IllegalArgumentException("Insert requires at least one statement");
        }
        return new InsertBeforeReplacement(stmts);
    }

    /**
     * Creates a replacement that inserts statements after the current statement.
     * @param stmts the statements to insert
     * @return the insert-after replacement
     * @throws IllegalArgumentException if stmts is null or empty
     */
    public static Replacement insertAfter(Statement... stmts)
    {
        if (stmts == null || stmts.length == 0)
        {
            throw new IllegalArgumentException("Insert requires at least one statement");
        }
        return new InsertAfterReplacement(Arrays.asList(stmts));
    }

    /**
     * Creates a replacement that inserts statements after the current statement.
     * @param stmts the statements to insert
     * @return the insert-after replacement
     * @throws IllegalArgumentException if stmts is null or empty
     */
    public static Replacement insertAfter(List<Statement> stmts)
    {
        if (stmts == null || stmts.isEmpty())
        {
            throw new IllegalArgumentException("Insert requires at least one statement");
        }
        return new InsertAfterReplacement(stmts);
    }

    /**
     * @return the replacement expression, or null unless the type is REPLACE_EXPR
     */
    public Expression getExpression()
    {
        return null;
    }

    /**
     * @return the replacement statement, or null unless the type is REPLACE_STMT
     */
    public Statement getStatement()
    {
        return null;
    }

    /**
     * @return the replacement statements, or an empty list unless the type is REPLACE_BLOCK,
     *         INSERT_BEFORE, or INSERT_AFTER
     */
    public List<Statement> getStatements()
    {
        return Collections.emptyList();
    }

    private static final class KeepReplacement extends Replacement
    {
        KeepReplacement()
        {
            super(Type.KEEP);
        }

        @Override
        public String toString()
        {
            return "Replacement.keep()";
        }
    }

    private static final class RemoveReplacement extends Replacement
    {
        RemoveReplacement()
        {
            super(Type.REMOVE);
        }

        @Override
        public String toString()
        {
            return "Replacement.remove()";
        }
    }

    private static final class ExprReplacement extends Replacement
    {
        private final Expression expression;

        ExprReplacement(Expression expression)
        {
            super(Type.REPLACE_EXPR);
            this.expression = expression;
        }

        @Override
        public Expression getExpression()
        {
            return expression;
        }

        @Override
        public String toString()
        {
            return "Replacement.with(" + expression + ")";
        }
    }

    private static final class StmtReplacement extends Replacement
    {
        private final Statement statement;

        StmtReplacement(Statement statement)
        {
            super(Type.REPLACE_STMT);
            this.statement = statement;
        }

        @Override
        public Statement getStatement()
        {
            return statement;
        }

        @Override
        public String toString()
        {
            return "Replacement.with(" + statement + ")";
        }
    }

    private static final class BlockReplacement extends Replacement
    {
        private final List<Statement> statements;

        BlockReplacement(List<Statement> statements)
        {
            super(Type.REPLACE_BLOCK);
            this.statements = statements;
        }

        @Override
        public List<Statement> getStatements()
        {
            return statements;
        }

        @Override
        public String toString()
        {
            return "Replacement.withBlock(" + statements.size() + " statements)";
        }
    }

    private static final class InsertBeforeReplacement extends Replacement
    {
        private final List<Statement> statements;

        InsertBeforeReplacement(List<Statement> statements)
        {
            super(Type.INSERT_BEFORE);
            this.statements = statements;
        }

        @Override
        public List<Statement> getStatements()
        {
            return statements;
        }

        @Override
        public String toString()
        {
            return "Replacement.insertBefore(" + statements.size() + " statements)";
        }
    }

    private static final class InsertAfterReplacement extends Replacement
    {
        private final List<Statement> statements;

        InsertAfterReplacement(List<Statement> statements)
        {
            super(Type.INSERT_AFTER);
            this.statements = statements;
        }

        @Override
        public List<Statement> getStatements()
        {
            return statements;
        }

        @Override
        public String toString()
        {
            return "Replacement.insertAfter(" + statements.size() + " statements)";
        }
    }
}
