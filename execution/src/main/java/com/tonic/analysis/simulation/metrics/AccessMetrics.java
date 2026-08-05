package com.tonic.analysis.simulation.metrics;

import com.tonic.analysis.simulation.listener.FieldAccessListener;

/**
 * Immutable field and array access counts collected during simulation.
 */
public class AccessMetrics
{

    private final int fieldReads;
    private final int fieldWrites;
    private final int staticFieldReads;
    private final int staticFieldWrites;
    private final int arrayReads;
    private final int arrayWrites;
    private final int distinctFields;

    private AccessMetrics(int fieldReads, int fieldWrites, int staticFieldReads, int staticFieldWrites, int arrayReads, int arrayWrites, int distinctFields)
    {
        this.fieldReads = fieldReads;
        this.fieldWrites = fieldWrites;
        this.staticFieldReads = staticFieldReads;
        this.staticFieldWrites = staticFieldWrites;
        this.arrayReads = arrayReads;
        this.arrayWrites = arrayWrites;
        this.distinctFields = distinctFields;
    }

    /**
     * Snapshots the counts a listener accumulated.
     *
     * @param listener the listener to read counts from
     * @return metrics holding that listener's counts
     */
    public static AccessMetrics from(FieldAccessListener listener)
    {
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
     * Creates metrics with every count at zero.
     *
     * @return all-zero metrics
     */
    public static AccessMetrics empty()
    {
        return new AccessMetrics(0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * @return the number of field reads
     */
    public int getFieldReads()
    {
        return fieldReads;
    }

    /**
     * @return the number of field writes
     */
    public int getFieldWrites()
    {
        return fieldWrites;
    }

    /**
     * @return field reads plus field writes
     */
    public int getTotalFieldAccesses()
    {
        return fieldReads + fieldWrites;
    }

    /**
     * @return the number of static field reads
     */
    public int getStaticFieldReads()
    {
        return staticFieldReads;
    }

    /**
     * @return the number of static field writes
     */
    public int getStaticFieldWrites()
    {
        return staticFieldWrites;
    }

    /**
     * @return the field reads that were not static
     */
    public int getInstanceFieldReads()
    {
        return fieldReads - staticFieldReads;
    }

    /**
     * @return the field writes that were not static
     */
    public int getInstanceFieldWrites()
    {
        return fieldWrites - staticFieldWrites;
    }

    /**
     * @return the number of array element reads
     */
    public int getArrayReads()
    {
        return arrayReads;
    }

    /**
     * @return the number of array element writes
     */
    public int getArrayWrites()
    {
        return arrayWrites;
    }

    /**
     * @return array reads plus array writes
     */
    public int getTotalArrayAccesses()
    {
        return arrayReads + arrayWrites;
    }

    /**
     * @return every field and array access counted
     */
    public int getTotalAccesses()
    {
        return getTotalFieldAccesses() + getTotalArrayAccesses();
    }

    /**
     * @return the number of distinct fields touched
     */
    public int getDistinctFields()
    {
        return distinctFields;
    }

    /**
     * @return field reads divided by field writes, or the read count when there were no writes
     */
    public double getFieldReadWriteRatio()
    {
        if (fieldWrites == 0) return fieldReads;
        return (double) fieldReads / fieldWrites;
    }

    /**
     * @return true when any field or array access was counted
     */
    public boolean hasAccesses()
    {
        return fieldReads > 0 || fieldWrites > 0 || arrayReads > 0 || arrayWrites > 0;
    }

    /**
     * Sums every count of both metrics, including the distinct-field totals.
     *
     * @param other the metrics to add
     * @return the summed metrics
     */
    public AccessMetrics combine(AccessMetrics other)
    {
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
    public String toString()
    {
        return "AccessMetrics[fieldReads=" + fieldReads +
            ", fieldWrites=" + fieldWrites +
            ", arrayReads=" + arrayReads +
            ", arrayWrites=" + arrayWrites +
            ", distinctFields=" + distinctFields + "]";
    }
}
