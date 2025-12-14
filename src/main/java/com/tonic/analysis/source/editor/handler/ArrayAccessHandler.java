package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.expr.ArrayAccessExpr;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for array access expressions (array[index]).
 * Provides context about whether the access is a read or store operation.
 *
 * <p>Example usage:
 * <pre>
 * editor.onArrayAccess((ctx, access, accessType) -> {
 *     if (accessType == ArrayAccessType.STORE) {
 *         // Handle array stores (assignments to array elements)
 *         System.out.println("Storing to " + access.getArray() + "[" + access.getIndex() + "]");
 *     } else {
 *         // Handle array reads
 *         System.out.println("Reading from " + access.getArray() + "[" + access.getIndex() + "]");
 *     }
 *     return Replacement.keep();
 * });
 * </pre>
 */
@FunctionalInterface
public interface ArrayAccessHandler {

    /**
     * Handles an array access expression.
     *
     * @param ctx        the editing context
     * @param access     the array access expression
     * @param accessType whether this is a read or store operation
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, ArrayAccessExpr access, ArrayAccessType accessType);

    /**
     * The type of array access operation.
     */
    enum ArrayAccessType {
        /**
         * Reading from an array element: value = array[index]
         */
        READ,

        /**
         * Storing to an array element: array[index] = value
         */
        STORE,

        /**
         * Compound assignment to array element: array[index] += value
         * This involves both a read and a store.
         */
        COMPOUND_ASSIGN
    }
}
