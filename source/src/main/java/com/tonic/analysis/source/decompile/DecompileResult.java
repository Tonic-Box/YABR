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
 * Each emitted member also carries its text span ({@link #getMethodSpan},
 * {@link #getFieldSpan}, {@link #getClassSpan}) - the 1-based first/last line of its full
 * declaration in {@link #getSource()} - for slicing or locating a declaration without parsing text.
 */
public final class DecompileResult
{

    private final String source;
    private final Map<String, NavigableMap<Integer, Integer>> lineMaps;
    private final Map<String, MethodSpan> methodSpans;
    private final Map<String, MemberSpan> fieldSpans;
    private final MemberSpan classSpan;

    DecompileResult(String source, Map<String, NavigableMap<Integer, Integer>> lineMaps,
                    Map<String, MethodSpan> methodSpans, Map<String, MemberSpan> fieldSpans,
                    MemberSpan classSpan)
                    {
        this.source = source;
        this.lineMaps = Collections.unmodifiableMap(lineMaps);
        this.methodSpans = Collections.unmodifiableMap(methodSpans);
        this.fieldSpans = Collections.unmodifiableMap(fieldSpans);
        this.classSpan = classSpan;
    }

    /**
     * @return the source
     */
    public String getSource()
    {
        return source;
    }

    /**
     * @return the line maps
     */
    public Map<String, NavigableMap<Integer, Integer>> getLineMaps()
    {
        return lineMaps;
    }

    /**
     * @return the method spans
     */
    public Map<String, MethodSpan> getMethodSpans()
    {
        return methodSpans;
    }

    /**
     * @return the field spans
     */
    public Map<String, MemberSpan> getFieldSpans()
    {
        return fieldSpans;
    }

    /**
     * @return the class span
     */
    public MemberSpan getClassSpan()
    {
        return classSpan;
    }

    /**
     * Looks up the offset-to-line map for one method.
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @return the offset-to-line map, or null if the method has no mapped statements
     */
    public NavigableMap<Integer, Integer> getLineMap(String methodName, String methodDesc)
    {
        return lineMaps.get(methodName + methodDesc);
    }

    /**
     * Looks up the text span of one method in {@link #getSource()}.
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @return the method span, or null if it was not emitted
     */
    public MethodSpan getMethodSpan(String methodName, String methodDesc)
    {
        return methodSpans.get(methodName + methodDesc);
    }

    /**
     * Looks up the text span of one field in {@link #getSource()}.
     * @param fieldName the field name
     * @param fieldDesc the field descriptor
     * @return the field span, or null if it was not emitted
     */
    public MemberSpan getFieldSpan(String fieldName, String fieldDesc)
    {
        return fieldSpans.get(fieldName + fieldDesc);
    }

    /**
     * The 1-based first/last line of a member's full text in the decompiled source - annotations
     * and signature through the closing brace (or the declaration line for abstract/native members
     * and fields).
     */
    public static class MemberSpan
    {
        private final int startLine;
        private final int endLine;

        MemberSpan(int startLine, int endLine)
        {
            this.startLine = startLine;
            this.endLine = endLine;
        }

        /**
         * @return the start line
         */
        public int getStartLine()
        {
            return startLine;
        }

        /**
         * @return the end line
         */
        public int getEndLine()
        {
            return endLine;
        }

        /**
         * Checks whether a source line falls within this span.
         * @param line the 1-based line number
         * @return true if the line lies within the span, inclusive
         */
        public boolean contains(int line)
        {
            return line >= startLine && line <= endLine;
        }
    }

    /**
     * A {@link MemberSpan} for a method or constructor. Retained as a distinct type for source and
     * API compatibility; the span semantics live entirely in {@link MemberSpan}.
     */
    public static final class MethodSpan extends MemberSpan
    {
        MethodSpan(int startLine, int endLine)
        {
            super(startLine, endLine);
        }
    }
}
