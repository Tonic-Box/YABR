package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;

/**
 * Sealed interface representing all statement types in the source AST.
 */
public sealed interface Statement extends ASTNode permits
        BlockStmt,
        IfStmt,
        WhileStmt,
        DoWhileStmt,
        ForStmt,
        ForEachStmt,
        SwitchStmt,
        TryCatchStmt,
        ReturnStmt,
        ThrowStmt,
        VarDeclStmt,
        ExprStmt,
        SynchronizedStmt,
        LabeledStmt,
        BreakStmt,
        ContinueStmt,
        IRRegionStmt {

    /**
     * Gets the label for this statement, if any.
     * Only LabeledStmt and loop statements typically have labels.
     *
     * @return the label, or null if not labeled
     */
    default String getLabel() {
        return null;
    }
}
