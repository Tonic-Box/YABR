package com.tonic.analysis.source.decompile;

import lombok.Getter;

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
@Getter
public final class DecompileResult {

    private final String source;
    private final Map<String, NavigableMap<Integer, Integer>> lineMaps;
    private final Map<String, MethodSpan> methodSpans;

    DecompileResult(String source, Map<String, NavigableMap<Integer, Integer>> lineMaps,
                    Map<String, MethodSpan> methodSpans) {
        this.source = source;
        this.lineMaps = Collections.unmodifiableMap(lineMaps);
        this.methodSpans = Collections.unmodifiableMap(methodSpans);
    }

    /**
     * The offset-to-line map for one method, or null if the method has no mapped statements.
     */
    public NavigableMap<Integer, Integer> getLineMap(String methodName, String methodDesc) {
        return lineMaps.get(methodName + methodDesc);
    }

    /**
     * The text span of one method in {@link #getSource()}, or null if it was not emitted.
     */
    public MethodSpan getMethodSpan(String methodName, String methodDesc) {
        return methodSpans.get(methodName + methodDesc);
    }

    /**
     * The 1-based first/last line of a member's full text in the decompiled source — annotations
     * and signature through the closing brace (or the declaration line for abstract/native members).
     */
    @Getter
    public static final class MethodSpan {
        private final int startLine;
        private final int endLine;

        MethodSpan(int startLine, int endLine) {
            this.startLine = startLine;
            this.endLine = endLine;
        }

        public boolean contains(int line) {
            return line >= startLine && line <= endLine;
        }
    }
}
