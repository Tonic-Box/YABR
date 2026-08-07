package com.tonic.analysis.source.ast;

import com.tonic.analysis.source.ast.stmt.Statement;

/**
 * Helpers for carrying {@link SourceLocation} provenance across statement rewrites.
 */
public final class Locations
{

    private Locations()
    {
    }

    /**
     * Carries offset provenance across a 1:1 statement rewrite, doing nothing if either side
     * is null or the source location has no bytecode offset.
     *
     * @param from statement supplying the location
     * @param to statement receiving it
     */
    public static void copy(Statement from, Statement to)
    {
        if (from != null && to != null && from.getLocation() != null && from.getLocation().hasOffset())
        {
            to.setLocation(from.getLocation());
        }
    }
}
