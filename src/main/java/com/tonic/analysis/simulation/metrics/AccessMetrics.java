package com.tonic.analysis.simulation.metrics;

import com.tonic.analysis.simulation.listener.FieldAccessListener;

/**
 * Metrics container for field and array access operations.
 *
 * <p>This class provides a clean interface to access statistics
 * collected during simulation.
 */
public class AccessMetrics {

    private final int fieldReads;
    private final int fieldWrites;
    private final int staticFieldReads;
    private final int staticFieldWrites;
    private final int arrayReads;
    private final int arrayWrites;
    private final int distinctFields;

    private AccessMetrics(int fieldReads, int fieldWrites, int staticFieldReads,
                          int staticFieldWrites, int arrayReads, int arrayWrites, int distinctFields) {
        this.fieldReads = fieldReads;
        this.fieldWrites = fieldWrites;
        this.staticFieldReads = staticFieldReads;
        this.staticFieldWrites = staticFieldWrites;
        this.arrayReads = arrayReads;
        this.arrayWrites = arrayWrites;
        this.distinctFields = distinctFields;
    }

    /**
     * Creates metrics from a FieldAccessListener.
     */
    public static AccessMetrics from(FieldAccessListener listener) {
        return new AccessMetrics(
            listener.getFieldReadCount(),
            listener.getFieldWriteCount(),
            listener.getStaticFieldReadCount(),
            listener.getStaticFieldWriteCount(),
            listener.getArrayReadCount(),
            listener.getArrayWriteCount(),
            listener.getDistinctFieldCount()
        );
    }

    /**
     * Creates empty metrics.
     */
    public static AccessMetrics empty() {
        return new AccessMetrics(0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Gets the total number of field reads.
     */
    public int getFieldReads() {
        return fieldReads;
    }

    /**
     * Gets the total number of field writes.
     */
    public int getFieldWrites() {
        return fieldWrites;
    }

    /**
     * Gets the total number of field accesses.
     */
    public int getTotalFieldAccesses() {
        return fieldReads + fieldWrites;
    }

    /**
     * Gets the number of static field reads.
     */
    public int getStaticFieldReads() {
        return staticFieldReads;
    }

    /**
     * Gets the number of static field writes.
     */
    public int getStaticFieldWrites() {
        return staticFieldWrites;
    }

    /**
     * Gets the number of instance field reads.
     */
    public int getInstanceFieldReads() {
        return fieldReads - staticFieldReads;
    }

    /**
     * Gets the number of instance field writes.
     */
    public int getInstanceFieldWrites() {
        return fieldWrites - staticFieldWrites;
    }

    /**
     * Gets the total number of array reads.
     */
    public int getArrayReads() {
        return arrayReads;
    }

    /**
     * Gets the total number of array writes.
     */
    public int getArrayWrites() {
        return arrayWrites;
    }

    /**
     * Gets the total number of array accesses.
     */
    public int getTotalArrayAccesses() {
        return arrayReads + arrayWrites;
    }

    /**
     * Gets the total number of all accesses.
     */
    public int getTotalAccesses() {
        return getTotalFieldAccesses() + getTotalArrayAccesses();
    }

    /**
     * Gets the number of distinct fields accessed.
     */
    public int getDistinctFields() {
        return distinctFields;
    }

    /**
     * Gets the read/write ratio for fields.
     */
    public double getFieldReadWriteRatio() {
        if (fieldWrites == 0) return fieldReads;
        return (double) fieldReads / fieldWrites;
    }

    /**
     * Returns true if any accesses occurred.
     */
    public boolean hasAccesses() {
        return fieldReads > 0 || fieldWrites > 0 || arrayReads > 0 || arrayWrites > 0;
    }

    /**
     * Combines this metrics with another.
     */
    public AccessMetrics combine(AccessMetrics other) {
        return new AccessMetrics(
            this.fieldReads + other.fieldReads,
            this.fieldWrites + other.fieldWrites,
            this.staticFieldReads + other.staticFieldReads,
            this.staticFieldWrites + other.staticFieldWrites,
            this.arrayReads + other.arrayReads,
            this.arrayWrites + other.arrayWrites,
            this.distinctFields + other.distinctFields
        );
    }

    @Override
    public String toString() {
        return "AccessMetrics[fieldReads=" + fieldReads +
            ", fieldWrites=" + fieldWrites +
            ", arrayReads=" + arrayReads +
            ", arrayWrites=" + arrayWrites +
            ", distinctFields=" + distinctFields + "]";
    }
}
