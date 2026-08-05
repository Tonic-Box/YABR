package com.tonic.analysis.query.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates the bytecode locations that satisfied a query, so results stay navigable. A matching
 * instruction/call/arg records the owning method signature + the producing instruction's pc; the
 * runner turns these into {@code PCTarget} evidence matches.
 */
public final class EvidenceCollector
{

    /**
     * A single matched location.
     */
    public static final class Hit
    {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int pc;
        private final String label;

        /**
         * Creates a hit at one bytecode offset.
         * @param className the owning class name
         * @param methodName the owning method name
         * @param descriptor the owning method descriptor
         * @param pc the bytecode offset of the matching instruction
         * @param label a description of what matched
         */
        public Hit(String className, String methodName, String descriptor, int pc, String label)
        {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.pc = pc;
            this.label = label;
        }

        /**
         * @return the owning class name
         */
        public String className() { return className; }
        /**
         * @return the owning method name
         */
        public String methodName() { return methodName; }
        /**
         * @return the owning method descriptor
         */
        public String descriptor() { return descriptor; }
        /**
         * @return the bytecode offset of the matching instruction
         */
        public int pc() { return pc; }
        /**
         * @return the label describing what matched
         */
        public String label() { return label; }
    }

    private final List<Hit> hits = new ArrayList<>();

    /**
     * Appends a matched location.
     * @param className the owning class name
     * @param methodName the owning method name
     * @param descriptor the owning method descriptor
     * @param pc the bytecode offset of the matching instruction
     * @param label a description of what matched
     */
    public void record(String className, String methodName, String descriptor, int pc, String label)
    {
        hits.add(new Hit(className, methodName, descriptor, pc, label));
    }

    /**
     * @return the live hit list, in record order
     */
    public List<Hit> hits()
    {
        return hits;
    }

    /**
     * @return whether nothing has been recorded
     */
    public boolean isEmpty()
    {
        return hits.isEmpty();
    }
}
