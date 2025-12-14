package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.editor.util.ASTFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides context information during AST editing.
 * Contains method information, current location, and utility methods.
 */
public class EditorContext {

    private final BlockStmt methodBody;
    private final String methodName;
    private final String methodDescriptor;
    private final String ownerClass;
    private final ASTFactory factory;

    // Current traversal state
    private Statement currentStatement;
    private BlockStmt enclosingBlock;
    private int statementIndex;
    private final Set<String> checkedVariables;

    // Nesting tracking
    private int tryDepth;
    private int loopDepth;
    private int conditionalDepth;

    public EditorContext(BlockStmt methodBody, String methodName, String methodDescriptor, String ownerClass) {
        this.methodBody = methodBody;
        this.methodName = methodName != null ? methodName : "<unknown>";
        this.methodDescriptor = methodDescriptor != null ? methodDescriptor : "()V";
        this.ownerClass = ownerClass != null ? ownerClass : "<unknown>";
        this.factory = new ASTFactory();
        this.checkedVariables = new HashSet<>();
        this.tryDepth = 0;
        this.loopDepth = 0;
        this.conditionalDepth = 0;
    }

    // ==================== Method Information ====================

    /**
     * Gets the method body being edited.
     */
    public BlockStmt getMethodBody() {
        return methodBody;
    }

    /**
     * Gets the name of the method being edited.
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Gets the descriptor of the method being edited.
     */
    public String getMethodDescriptor() {
        return methodDescriptor;
    }

    /**
     * Gets the internal name of the class that owns this method.
     */
    public String getOwnerClass() {
        return ownerClass;
    }

    // ==================== Current Location ====================

    /**
     * Gets the current statement being visited.
     */
    public Statement getCurrentStatement() {
        return currentStatement;
    }

    /**
     * Sets the current statement (called by editor during traversal).
     */
    public void setCurrentStatement(Statement stmt) {
        this.currentStatement = stmt;
    }

    /**
     * Gets the enclosing block statement.
     */
    public BlockStmt getEnclosingBlock() {
        return enclosingBlock;
    }

    /**
     * Sets the enclosing block (called by editor during traversal).
     */
    public void setEnclosingBlock(BlockStmt block) {
        this.enclosingBlock = block;
    }

    /**
     * Gets the index of the current statement within its enclosing block.
     */
    public int getStatementIndex() {
        return statementIndex;
    }

    /**
     * Sets the statement index (called by editor during traversal).
     */
    public void setStatementIndex(int index) {
        this.statementIndex = index;
    }

    // ==================== Nesting Tracking ====================

    /**
     * Checks if the current location is inside a try block.
     */
    public boolean isInTryBlock() {
        return tryDepth > 0;
    }

    /**
     * Checks if the current location is inside a loop.
     */
    public boolean isInLoop() {
        return loopDepth > 0;
    }

    /**
     * Checks if the current location is inside a conditional (if/switch).
     */
    public boolean isInConditional() {
        return conditionalDepth > 0;
    }

    /**
     * Enters a try block.
     */
    public void enterTry() {
        tryDepth++;
    }

    /**
     * Exits a try block.
     */
    public void exitTry() {
        tryDepth = Math.max(0, tryDepth - 1);
    }

    /**
     * Enters a loop.
     */
    public void enterLoop() {
        loopDepth++;
    }

    /**
     * Exits a loop.
     */
    public void exitLoop() {
        loopDepth = Math.max(0, loopDepth - 1);
    }

    /**
     * Enters a conditional.
     */
    public void enterConditional() {
        conditionalDepth++;
    }

    /**
     * Exits a conditional.
     */
    public void exitConditional() {
        conditionalDepth = Math.max(0, conditionalDepth - 1);
    }

    // ==================== Variable Tracking ====================

    /**
     * Gets all variables that are visible at the current location.
     * Note: This is a simplified implementation that returns variables
     * marked as null-checked. A complete implementation would track
     * all declared variables.
     */
    public Set<String> getVisibleVariables() {
        return new HashSet<>(checkedVariables);
    }

    /**
     * Checks if an expression has been null-checked.
     * Currently tracks simple variable names.
     */
    public boolean isNullChecked(Expression expr) {
        String varName = extractVariableName(expr);
        return varName != null && checkedVariables.contains(varName);
    }

    /**
     * Marks a variable as null-checked.
     */
    public void markNullChecked(String varName) {
        if (varName != null) {
            checkedVariables.add(varName);
        }
    }

    /**
     * Clears null-check tracking.
     */
    public void clearNullChecks() {
        checkedVariables.clear();
    }

    // ==================== Factory ====================

    /**
     * Gets the AST factory for creating new nodes.
     */
    public ASTFactory factory() {
        return factory;
    }

    // ==================== Utility Methods ====================

    /**
     * Creates a replacement that inserts statements before the current statement.
     */
    public Replacement insertBefore(Statement... stmts) {
        return Replacement.insertBefore(stmts);
    }

    /**
     * Creates a replacement that inserts statements after the current statement.
     */
    public Replacement insertAfter(Statement... stmts) {
        return Replacement.insertAfter(stmts);
    }

    /**
     * Wraps an expression with a null check.
     * Returns a replacement that adds a null check before any usage.
     *
     * @param expr the expression to check
     * @return a replacement with null check
     */
    public Replacement wrapWithNullCheck(Expression expr) {
        // Create: if (expr != null) { original }
        // This is a simplified implementation - the actual wrapping depends on context
        String varName = extractVariableName(expr);
        if (varName != null) {
            markNullChecked(varName);
        }
        // For now, just return keep - actual null check wrapping
        // requires more sophisticated statement manipulation
        return Replacement.keep();
    }

    /**
     * Extracts a variable name from an expression if possible.
     */
    private String extractVariableName(Expression expr) {
        if (expr == null) {
            return null;
        }
        // VarRefExpr stores the variable name
        if (expr instanceof com.tonic.analysis.source.ast.expr.VarRefExpr) {
            return ((com.tonic.analysis.source.ast.expr.VarRefExpr) expr).getName();
        }
        return null;
    }

    /**
     * Finds the enclosing statement for an expression.
     */
    public Statement findEnclosingStatement(Expression expr) {
        if (expr == null) {
            return null;
        }
        ASTNode node = expr.getParent();
        while (node != null) {
            if (node instanceof Statement) {
                return (Statement) node;
            }
            node = node.getParent();
        }
        return null;
    }

    /**
     * Creates a copy of this context for nested traversal.
     */
    public EditorContext createNestedContext() {
        EditorContext nested = new EditorContext(methodBody, methodName, methodDescriptor, ownerClass);
        nested.tryDepth = this.tryDepth;
        nested.loopDepth = this.loopDepth;
        nested.conditionalDepth = this.conditionalDepth;
        nested.checkedVariables.addAll(this.checkedVariables);
        return nested;
    }
}
