package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.editor.util.ASTFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Traversal context handed to AST editor handlers: the method being edited, the current
 * location within it, and helpers for building replacements.
 */
public class EditorContext
{

    private final BlockStmt methodBody;
    private final String methodName;
    private final String methodDescriptor;
    private final String ownerClass;
    private final ASTFactory factory;

    private Statement currentStatement;
    private BlockStmt enclosingBlock;
    private int statementIndex;
    private final Set<String> checkedVariables;

    private int tryDepth;
    private int loopDepth;
    private int conditionalDepth;

    /**
     * Creates a context for editing the given method body.
     * @param methodBody the method body being edited
     * @param methodName the method name; a placeholder is used when null
     * @param methodDescriptor the method descriptor; defaults to ()V when null
     * @param ownerClass the internal name of the owning class; a placeholder is used when null
     */
    public EditorContext(BlockStmt methodBody, String methodName, String methodDescriptor, String ownerClass)
    {
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

    /**
     * @return the method body being edited
     */
    public BlockStmt getMethodBody()
    {
        return methodBody;
    }

    /**
     * @return the name of the method being edited
     */
    public String getMethodName()
    {
        return methodName;
    }

    /**
     * @return the descriptor of the method being edited
     */
    public String getMethodDescriptor()
    {
        return methodDescriptor;
    }

    /**
     * @return the internal name of the class that owns this method
     */
    public String getOwnerClass()
    {
        return ownerClass;
    }

    /**
     * @return the statement currently being visited
     */
    public Statement getCurrentStatement()
    {
        return currentStatement;
    }

    /**
     * Sets the current statement; called by the editor during traversal.
     * @param stmt the statement now being visited
     */
    public void setCurrentStatement(Statement stmt)
    {
        this.currentStatement = stmt;
    }

    /**
     * @return the block enclosing the current statement
     */
    public BlockStmt getEnclosingBlock()
    {
        return enclosingBlock;
    }

    /**
     * Sets the enclosing block; called by the editor during traversal.
     * @param block the block enclosing the current statement
     */
    public void setEnclosingBlock(BlockStmt block)
    {
        this.enclosingBlock = block;
    }

    /**
     * @return the index of the current statement within its enclosing block
     */
    public int getStatementIndex()
    {
        return statementIndex;
    }

    /**
     * Sets the statement index; called by the editor during traversal.
     * @param index the index of the current statement within its enclosing block
     */
    public void setStatementIndex(int index)
    {
        this.statementIndex = index;
    }

    /**
     * @return true if the current location is inside a try block
     */
    public boolean isInTryBlock()
    {
        return tryDepth > 0;
    }

    /**
     * @return true if the current location is inside a loop
     */
    public boolean isInLoop()
    {
        return loopDepth > 0;
    }

    /**
     * @return true if the current location is inside a conditional (if/switch)
     */
    public boolean isInConditional()
    {
        return conditionalDepth > 0;
    }

    /**
     * Records entry into a try block.
     */
    public void enterTry()
    {
        tryDepth++;
    }

    /**
     * Records exit from a try block.
     */
    public void exitTry()
    {
        tryDepth = Math.max(0, tryDepth - 1);
    }

    /**
     * Records entry into a loop.
     */
    public void enterLoop()
    {
        loopDepth++;
    }

    /**
     * Records exit from a loop.
     */
    public void exitLoop()
    {
        loopDepth = Math.max(0, loopDepth - 1);
    }

    /**
     * Records entry into a conditional.
     */
    public void enterConditional()
    {
        conditionalDepth++;
    }

    /**
     * Records exit from a conditional.
     */
    public void exitConditional()
    {
        conditionalDepth = Math.max(0, conditionalDepth - 1);
    }

    /**
     * Collects variables visible at the current location; simplified to the null-checked set.
     * @return a copy of the null-checked variable names
     */
    public Set<String> getVisibleVariables()
    {
        return new HashSet<>(checkedVariables);
    }

    /**
     * Checks whether an expression has been marked null-checked; only simple variable
     * references are tracked.
     * @param expr the expression to test
     * @return true if the expression is a tracked null-checked variable
     */
    public boolean isNullChecked(Expression expr)
    {
        String varName = extractVariableName(expr);
        return varName != null && checkedVariables.contains(varName);
    }

    /**
     * Marks a variable as null-checked.
     * @param varName the variable name; ignored when null
     */
    public void markNullChecked(String varName)
    {
        if (varName != null)
        {
            checkedVariables.add(varName);
        }
    }

    /**
     * Clears all null-check tracking.
     */
    public void clearNullChecks()
    {
        checkedVariables.clear();
    }

    /**
     * @return the AST factory for creating new nodes
     */
    public ASTFactory factory()
    {
        return factory;
    }

    /**
     * Creates a replacement that inserts statements before the current statement.
     * @param stmts the statements to insert
     * @return the insert-before replacement
     */
    public Replacement insertBefore(Statement... stmts)
    {
        return Replacement.insertBefore(stmts);
    }

    /**
     * Creates a replacement that inserts statements after the current statement.
     * @param stmts the statements to insert
     * @return the insert-after replacement
     */
    public Replacement insertAfter(Statement... stmts)
    {
        return Replacement.insertAfter(stmts);
    }

    /**
     * Marks the expression's variable as null-checked without altering the node.
     * @param expr the expression to check
     * @return a keep replacement
     */
    public Replacement wrapWithNullCheck(Expression expr)
    {
        String varName = extractVariableName(expr);
        if (varName != null)
        {
            markNullChecked(varName);
        }
        return Replacement.keep();
    }

    /**
     * Extracts a variable name from an expression if possible.
     * @param expr the expression to extract from
     * @return the variable name or null
     */
    private String extractVariableName(Expression expr)
    {
        if (expr == null)
        {
            return null;
        }
        if (expr instanceof VarRefExpr)
        {
            return ((VarRefExpr) expr).getName();
        }
        return null;
    }

    /**
     * Walks parent links to find the statement that contains an expression.
     * @param expr the expression to search from
     * @return the enclosing statement, or null if none is found
     */
    public Statement findEnclosingStatement(Expression expr)
    {
        if (expr == null)
        {
            return null;
        }
        ASTNode node = expr.getParent();
        while (node != null)
        {
            if (node instanceof Statement)
            {
                return (Statement) node;
            }
            node = node.getParent();
        }
        return null;
    }

    /**
     * Creates a copy of this context, carrying over depths and null-check state, for
     * nested traversal.
     * @return the nested context
     */
    public EditorContext createNestedContext()
    {
        EditorContext nested = new EditorContext(methodBody, methodName, methodDescriptor, ownerClass);
        nested.tryDepth = this.tryDepth;
        nested.loopDepth = this.loopDepth;
        nested.conditionalDepth = this.conditionalDepth;
        nested.checkedVariables.addAll(this.checkedVariables);
        return nested;
    }
}
