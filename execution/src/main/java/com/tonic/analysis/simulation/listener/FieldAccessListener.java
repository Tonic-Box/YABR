package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ArrayAccessInstruction;
import com.tonic.analysis.ssa.ir.FieldAccessInstruction;

import java.util.*;

/**
 * Simulation listener that counts field and array reads and writes, both in total and per field.
 */
public class FieldAccessListener extends AbstractListener
{

    private int fieldReadCount;
    private int fieldWriteCount;
    private int staticFieldReadCount;
    private int staticFieldWriteCount;
    private int arrayReadCount;
    private int arrayWriteCount;

    private final Map<FieldReference, AccessStats> fieldAccesses;

    /**
     * Creates a listener with all counters at zero.
     */
    public FieldAccessListener()
    {
        this.fieldAccesses = new HashMap<>();
    }

    @Override
    public void onSimulationStart(IRMethod method)
    {
        super.onSimulationStart(method);
        fieldReadCount = 0;
        fieldWriteCount = 0;
        staticFieldReadCount = 0;
        staticFieldWriteCount = 0;
        arrayReadCount = 0;
        arrayWriteCount = 0;
        fieldAccesses.clear();
    }

    @Override
    public void onFieldRead(FieldAccessInstruction instr, SimulationState state)
    {
        fieldReadCount++;
        if (instr.isStatic())
        {
            staticFieldReadCount++;
        }

        FieldReference ref = new FieldReference(instr.getOwner(), instr.getName(), instr.getDescriptor());
        fieldAccesses.computeIfAbsent(ref, k -> new AccessStats()).incrementReads();
    }

    @Override
    public void onFieldWrite(FieldAccessInstruction instr, SimulationState state)
    {
        fieldWriteCount++;
        if (instr.isStatic())
        {
            staticFieldWriteCount++;
        }

        FieldReference ref = new FieldReference(instr.getOwner(), instr.getName(), instr.getDescriptor());
        fieldAccesses.computeIfAbsent(ref, k -> new AccessStats()).incrementWrites();
    }

    @Override
    public void onArrayRead(ArrayAccessInstruction instr, SimulationState state)
    {
        arrayReadCount++;
    }

    @Override
    public void onArrayWrite(ArrayAccessInstruction instr, SimulationState state)
    {
        arrayWriteCount++;
    }

    /**
     * @return the number of field reads seen, static and instance combined
     */
    public int getFieldReadCount()
    {
        return fieldReadCount;
    }

    /**
     * @return the number of field writes seen, static and instance combined
     */
    public int getFieldWriteCount()
    {
        return fieldWriteCount;
    }

    /**
     * @return the number of static field reads seen
     */
    public int getStaticFieldReadCount()
    {
        return staticFieldReadCount;
    }

    /**
     * @return the number of static field writes seen
     */
    public int getStaticFieldWriteCount()
    {
        return staticFieldWriteCount;
    }

    /**
     * @return the field reads that were not static
     */
    public int getInstanceFieldReadCount()
    {
        return fieldReadCount - staticFieldReadCount;
    }

    /**
     * @return the field writes that were not static
     */
    public int getInstanceFieldWriteCount()
    {
        return fieldWriteCount - staticFieldWriteCount;
    }

    /**
     * @return the number of array element reads seen
     */
    public int getArrayReadCount()
    {
        return arrayReadCount;
    }

    /**
     * @return the number of array element writes seen
     */
    public int getArrayWriteCount()
    {
        return arrayWriteCount;
    }

    /**
     * @return the field reads plus the field writes
     */
    public int getTotalFieldAccesses()
    {
        return fieldReadCount + fieldWriteCount;
    }

    /**
     * @return the array reads plus the array writes
     */
    public int getTotalArrayAccesses()
    {
        return arrayReadCount + arrayWriteCount;
    }

    /**
     * Looks up the read count of one field, ignoring its descriptor.
     *
     * @param owner internal name of the declaring class
     * @param name field name
     * @return the recorded read count, or 0 if the field was never read
     */
    public int getReadCount(String owner, String name)
    {
        for (Map.Entry<FieldReference, AccessStats> entry : fieldAccesses.entrySet())
        {
            if (entry.getKey().getOwner().equals(owner) && entry.getKey().getName().equals(name))
            {
                return entry.getValue().getReadCount();
            }
        }
        return 0;
    }

    /**
     * Looks up the write count of one field, ignoring its descriptor.
     *
     * @param owner internal name of the declaring class
     * @param name field name
     * @return the recorded write count, or 0 if the field was never written
     */
    public int getWriteCount(String owner, String name)
    {
        for (Map.Entry<FieldReference, AccessStats> entry : fieldAccesses.entrySet())
        {
            if (entry.getKey().getOwner().equals(owner) && entry.getKey().getName().equals(name))
            {
                return entry.getValue().getWriteCount();
            }
        }
        return 0;
    }

    /**
     * @return an unmodifiable view of the per-field access counts
     */
    public Map<FieldReference, AccessStats> getFieldAccesses()
    {
        return Collections.unmodifiableMap(fieldAccesses);
    }

    /**
     * @return the number of distinct fields touched
     */
    public int getDistinctFieldCount()
    {
        return fieldAccesses.size();
    }

    /**
     * Represents a field reference.
     */
    public static class FieldReference
    {
        private final String owner;
        private final String name;
        private final String descriptor;

        public FieldReference(String owner, String name, String descriptor)
        {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        /**
         * @return the owner
         */
        public String getOwner()
        {
            return owner;
        }

        /**
         * @return the name
         */
        public String getName()
        {
            return name;
        }

        /**
         * @return the descriptor
         */
        public String getDescriptor()
        {
            return descriptor;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof FieldReference)) return false;
            FieldReference that = (FieldReference) o;
            return Objects.equals(owner, that.owner) &&
                   Objects.equals(name, that.name) &&
                   Objects.equals(descriptor, that.descriptor);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(owner, name, descriptor);
        }

        @Override
        public String toString()
        {
            return owner + "." + name + ":" + descriptor;
        }
    }

    /**
     * Read and write counts for one field.
     */
    public static class AccessStats
    {
        private int readCount;
        private int writeCount;

        /**
         * Records one read of the field.
         */
        public void incrementReads()
        {
            readCount++;
        }

        /**
         * Records one write of the field.
         */
        public void incrementWrites()
        {
            writeCount++;
        }

        /**
         * @return the read count
         */
        public int getReadCount()
        {
            return readCount;
        }

        /**
         * @return the write count
         */
        public int getWriteCount()
        {
            return writeCount;
        }

        /**
         * @return the read count plus the write count
         */
        public int getTotalCount()
        {
            return readCount + writeCount;
        }

        @Override
        public String toString()
        {
            return "reads=" + readCount + ", writes=" + writeCount;
        }
    }

    @Override
    public String toString()
    {
        return "FieldAccessListener[fieldReads=" + fieldReadCount +
            ", fieldWrites=" + fieldWriteCount +
            ", arrayReads=" + arrayReadCount +
            ", arrayWrites=" + arrayWriteCount + "]";
    }
}
