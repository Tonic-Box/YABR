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
 *
 * <p>Each emitted member also carries its text span ({@link #getMethodSpan},
 * {@link #getFieldSpan}, {@link #getClassSpan}) — the 1-based first/last line of its full
 * declaration in {@link #getSource()} — for slicing or locating a declaration without parsing text.
 */
public final class DecompileResult {

    private final String source;
    private final Map<String, NavigableMap<Integer, Integer>> lineMaps;
    private final Map<String, MethodSpan> methodSpans;
    private final Map<String, MemberSpan> fieldSpans;
    private final MemberSpan classSpan;

    DecompileResult(String source, Map<String, NavigableMap<Integer, Integer>> lineMaps,
                    Map<String, MethodSpan> methodSpans, Map<String, MemberSpan> fieldSpans,
                    MemberSpan classSpan) {
        this.source = source;
        this.lineMaps = Collections.unmodifiableMap(lineMaps);
        this.methodSpans = Collections.unmodifiableMap(methodSpans);
        this.fieldSpans = Collections.unmodifiableMap(fieldSpans);
        this.classSpan = classSpan;
    }

    public String getSource() {
        return source;
    }

    public Map<String, NavigableMap<Integer, Integer>> getLineMaps() {
        return lineMaps;
    }

    public Map<String, MethodSpan> getMethodSpans() {
        return methodSpans;
    }

    public Map<String, MemberSpan> getFieldSpans() {
        return fieldSpans;
    }

    public MemberSpan getClassSpan() {
        return classSpan;
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
     * The text span of one field in {@link #getSource()}, or null if it was not emitted.
     */
    public MemberSpan getFieldSpan(String fieldName, String fieldDesc) {
        return fieldSpans.get(fieldName + fieldDesc);
    }

    /**
     * The 1-based first/last line of a member's full text in the decompiled source — annotations
     * and signature through the closing brace (or the declaration line for abstract/native members
     * and fields).
     */
    public static class MemberSpan {
        private final int startLine;
        private final int endLine;

        MemberSpan(int startLine, int endLine) {
            this.startLine = startLine;
            this.endLine = endLine;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getEndLine() {
            return endLine;
        }

        public boolean contains(int line) {
            return line >= startLine && line <= endLine;
        }
    }

    /**
     * A {@link MemberSpan} for a method or constructor. Retained as a distinct type for source and
     * API compatibility; the span semantics live entirely in {@link MemberSpan}.
     */
    public static final class MethodSpan extends MemberSpan {
        MethodSpan(int startLine, int endLine) {
            super(startLine, endLine);
        }
    }
}
