package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.Statement;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents the result of handling an expression or statement during AST editing.
 * Provides factory methods for different replacement actions.
 */
public abstract class Replacement {

    /**
     * The type of replacement action.
     */
    public enum Type {
        KEEP,           // No change
        REPLACE_EXPR,   // Replace with new expression
        REPLACE_STMT,   // Replace with new statement
        REPLACE_BLOCK,  // Replace with multiple statements
        REMOVE,         // Remove entirely
        INSERT_BEFORE,  // Insert statements before current
        INSERT_AFTER    // Insert statements after current
    }

    private final Type type;

    protected Replacement(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public boolean isKeep() {
        return type == Type.KEEP;
    }

    public boolean isReplace() {
        return type == Type.REPLACE_EXPR || type == Type.REPLACE_STMT || type == Type.REPLACE_BLOCK;
    }

    public boolean isRemove() {
        return type == Type.REMOVE;
    }

    public boolean isInsert() {
        return type == Type.INSERT_BEFORE || type == Type.INSERT_AFTER;
    }

    // ==================== Factory Methods ====================

    private static final Replacement KEEP_INSTANCE = new KeepReplacement();
    private static final Replacement REMOVE_INSTANCE = new RemoveReplacement();

    /**
     * Keep the original node unchanged.
     */
    public static Replacement keep() {
        return KEEP_INSTANCE;
    }

    /**
     * Replace with a new expression.
     */
    public static Replacement with(Expression newExpr) {
        if (newExpr == null) {
            throw new IllegalArgumentException("Replacement expression cannot be null");
        }
        return new ExprReplacement(newExpr);
    }

    /**
     * Replace with a new statement.
     */
    public static Replacement with(Statement newStmt) {
        if (newStmt == null) {
            throw new IllegalArgumentException("Replacement statement cannot be null");
        }
        return new StmtReplacement(newStmt);
    }

    /**
     * Replace with multiple statements.
     */
    public static Replacement withBlock(Statement... stmts) {
        if (stmts == null || stmts.length == 0) {
            throw new IllegalArgumentException("Block replacement requires at least one statement");
        }
        return new BlockReplacement(Arrays.asList(stmts));
    }

    /**
     * Replace with multiple statements.
     */
    public static Replacement withBlock(List<Statement> stmts) {
        if (stmts == null || stmts.isEmpty()) {
            throw new IllegalArgumentException("Block replacement requires at least one statement");
        }
        return new BlockReplacement(stmts);
    }

    /**
     * Remove the node entirely.
     */
    public static Replacement remove() {
        return REMOVE_INSTANCE;
    }

    /**
     * Insert statements before the current statement.
     */
    public static Replacement insertBefore(Statement... stmts) {
        if (stmts == null || stmts.length == 0) {
            throw new IllegalArgumentException("Insert requires at least one statement");
        }
        return new InsertBeforeReplacement(Arrays.asList(stmts));
    }

    /**
     * Insert statements before the current statement.
     */
    public static Replacement insertBefore(List<Statement> stmts) {
        if (stmts == null || stmts.isEmpty()) {
            throw new IllegalArgumentException("Insert requires at least one statement");
        }
        return new InsertBeforeReplacement(stmts);
    }

    /**
     * Insert statements after the current statement.
     */
    public static Replacement insertAfter(Statement... stmts) {
        if (stmts == null || stmts.length == 0) {
            throw new IllegalArgumentException("Insert requires at least one statement");
        }
        return new InsertAfterReplacement(Arrays.asList(stmts));
    }

    /**
     * Insert statements after the current statement.
     */
    public static Replacement insertAfter(List<Statement> stmts) {
        if (stmts == null || stmts.isEmpty()) {
            throw new IllegalArgumentException("Insert requires at least one statement");
        }
        return new InsertAfterReplacement(stmts);
    }

    // ==================== Accessors for subclasses ====================

    /**
     * Gets the replacement expression (for REPLACE_EXPR type).
     */
    public Expression getExpression() {
        return null;
    }

    /**
     * Gets the replacement statement (for REPLACE_STMT type).
     */
    public Statement getStatement() {
        return null;
    }

    /**
     * Gets the replacement statements (for REPLACE_BLOCK, INSERT_BEFORE, INSERT_AFTER types).
     */
    public List<Statement> getStatements() {
        return Collections.emptyList();
    }

    // ==================== Inner Classes ====================

    private static final class KeepReplacement extends Replacement {
        KeepReplacement() {
            super(Type.KEEP);
        }

        @Override
        public String toString() {
            return "Replacement.keep()";
        }
    }

    private static final class RemoveReplacement extends Replacement {
        RemoveReplacement() {
            super(Type.REMOVE);
        }

        @Override
        public String toString() {
            return "Replacement.remove()";
        }
    }

    private static final class ExprReplacement extends Replacement {
        private final Expression expression;

        ExprReplacement(Expression expression) {
            super(Type.REPLACE_EXPR);
            this.expression = expression;
        }

        @Override
        public Expression getExpression() {
            return expression;
        }

        @Override
        public String toString() {
            return "Replacement.with(" + expression + ")";
        }
    }

    private static final class StmtReplacement extends Replacement {
        private final Statement statement;

        StmtReplacement(Statement statement) {
            super(Type.REPLACE_STMT);
            this.statement = statement;
        }

        @Override
        public Statement getStatement() {
            return statement;
        }

        @Override
        public String toString() {
            return "Replacement.with(" + statement + ")";
        }
    }

    private static final class BlockReplacement extends Replacement {
        private final List<Statement> statements;

        BlockReplacement(List<Statement> statements) {
            super(Type.REPLACE_BLOCK);
            this.statements = statements;
        }

        @Override
        public List<Statement> getStatements() {
            return statements;
        }

        @Override
        public String toString() {
            return "Replacement.withBlock(" + statements.size() + " statements)";
        }
    }

    private static final class InsertBeforeReplacement extends Replacement {
        private final List<Statement> statements;

        InsertBeforeReplacement(List<Statement> statements) {
            super(Type.INSERT_BEFORE);
            this.statements = statements;
        }

        @Override
        public List<Statement> getStatements() {
            return statements;
        }

        @Override
        public String toString() {
            return "Replacement.insertBefore(" + statements.size() + " statements)";
        }
    }

    private static final class InsertAfterReplacement extends Replacement {
        private final List<Statement> statements;

        InsertAfterReplacement(List<Statement> statements) {
            super(Type.INSERT_AFTER);
            this.statements = statements;
        }

        @Override
        public List<Statement> getStatements() {
            return statements;
        }

        @Override
        public String toString() {
            return "Replacement.insertAfter(" + statements.size() + " statements)";
        }
    }
}
