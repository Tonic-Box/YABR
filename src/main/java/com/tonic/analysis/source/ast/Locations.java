package com.tonic.analysis.source.ast;

import com.tonic.analysis.source.ast.stmt.Statement;

/**
 * Helpers for carrying {@link SourceLocation} provenance across statement rewrites.
 */
public final class Locations {

    private Locations() {
    }

    /**
     * Copies {@code from}'s location onto {@code to} when it carries a bytecode offset. A rewrite
     * that replaces a statement 1:1 calls this so offset provenance survives the transform; no-op
     * when either side is null or the source has no offset.
     */
    public static void copy(Statement from, Statement to) {
        if (from != null && to != null && from.getLocation() != null && from.getLocation().hasOffset()) {
            to.setLocation(from.getLocation());
        }
    }
}
