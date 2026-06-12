package com.tonic.analysis.source.decompile;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Decompiled source plus per-method bytecode-offset provenance: for each method (keyed
 * {@code name + descriptor}, e.g. {@code main([Ljava/lang/String;)V}, {@code <init>()V},
 * {@code <clinit>()V}) a map from bytecode offset to the 1-based line in {@link #getSource()} where
 * the statement recovered from that offset was emitted. Use {@code floorEntry}/{@code ceilingEntry}
 * to resolve an arbitrary PC to the nearest mapped statement line.
 */
public final class DecompileResult {

    private final String source;
    private final Map<String, NavigableMap<Integer, Integer>> lineMaps;

    DecompileResult(String source, Map<String, NavigableMap<Integer, Integer>> lineMaps) {
        this.source = source;
        this.lineMaps = Collections.unmodifiableMap(lineMaps);
    }

    public String getSource() {
        return source;
    }

    public Map<String, NavigableMap<Integer, Integer>> getLineMaps() {
        return lineMaps;
    }

    /**
     * The offset-to-line map for one method, or null if the method has no mapped statements.
     */
    public NavigableMap<Integer, Integer> getLineMap(String methodName, String methodDesc) {
        return lineMaps.get(methodName + methodDesc);
    }
}
