package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ArrayAccessInstruction;
import com.tonic.analysis.ssa.ir.FieldAccessInstruction;

import java.util.*;

/**
 * Listener that tracks field and array access during simulation.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Field reads (GETFIELD, GETSTATIC)</li>
 *   <li>Field writes (PUTFIELD, PUTSTATIC)</li>
 *   <li>Array reads</li>
 *   <li>Array writes</li>
 *   <li>Access counts per field</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * FieldAccessListener listener = new FieldAccessListener();
 * engine.addListener(listener);
 * engine.simulate(method);
 *
 * System.out.println("Field reads: " + listener.getFieldReadCount());
 * System.out.println("Field writes: " + listener.getFieldWriteCount());
 * </pre>
 */
public class FieldAccessListener extends AbstractListener {

    private int fieldReadCount;
    private int fieldWriteCount;
    private int staticFieldReadCount;
    private int staticFieldWriteCount;
    private int arrayReadCount;
    private int arrayWriteCount;

    private final Map<FieldReference, AccessStats> fieldAccesses;

    public FieldAccessListener() {
        this.fieldAccesses = new HashMap<>();
    }

    @Override
    public void onSimulationStart(IRMethod method) {
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
    public void onFieldRead(FieldAccessInstruction instr, SimulationState state) {
        fieldReadCount++;
        if (instr.isStatic()) {
            staticFieldReadCount++;
        }

        FieldReference ref = new FieldReference(instr.getOwner(), instr.getName(), instr.getDescriptor());
        fieldAccesses.computeIfAbsent(ref, k -> new AccessStats()).incrementReads();
    }

    @Override
    public void onFieldWrite(FieldAccessInstruction instr, SimulationState state) {
        fieldWriteCount++;
        if (instr.isStatic()) {
            staticFieldWriteCount++;
        }

        FieldReference ref = new FieldReference(instr.getOwner(), instr.getName(), instr.getDescriptor());
        fieldAccesses.computeIfAbsent(ref, k -> new AccessStats()).incrementWrites();
    }

    @Override
    public void onArrayRead(ArrayAccessInstruction instr, SimulationState state) {
        arrayReadCount++;
    }

    @Override
    public void onArrayWrite(ArrayAccessInstruction instr, SimulationState state) {
        arrayWriteCount++;
    }

    /**
     * Gets the total number of field reads.
     */
    public int getFieldReadCount() {
        return fieldReadCount;
    }

    /**
     * Gets the total number of field writes.
     */
    public int getFieldWriteCount() {
        return fieldWriteCount;
    }

    /**
     * Gets the number of static field reads.
     */
    public int getStaticFieldReadCount() {
        return staticFieldReadCount;
    }

    /**
     * Gets the number of static field writes.
     */
    public int getStaticFieldWriteCount() {
        return staticFieldWriteCount;
    }

    /**
     * Gets the number of instance field reads.
     */
    public int getInstanceFieldReadCount() {
        return fieldReadCount - staticFieldReadCount;
    }

    /**
     * Gets the number of instance field writes.
     */
    public int getInstanceFieldWriteCount() {
        return fieldWriteCount - staticFieldWriteCount;
    }

    /**
     * Gets the total number of array reads.
     */
    public int getArrayReadCount() {
        return arrayReadCount;
    }

    /**
     * Gets the total number of array writes.
     */
    public int getArrayWriteCount() {
        return arrayWriteCount;
    }

    /**
     * Gets the total number of field accesses.
     */
    public int getTotalFieldAccesses() {
        return fieldReadCount + fieldWriteCount;
    }

    /**
     * Gets the total number of array accesses.
     */
    public int getTotalArrayAccesses() {
        return arrayReadCount + arrayWriteCount;
    }

    /**
     * Gets the read count for a specific field.
     */
    public int getReadCount(String owner, String name) {
        for (Map.Entry<FieldReference, AccessStats> entry : fieldAccesses.entrySet()) {
            if (entry.getKey().getOwner().equals(owner) &&
                entry.getKey().getName().equals(name)) {
                return entry.getValue().getReadCount();
            }
        }
        return 0;
    }

    /**
     * Gets the write count for a specific field.
     */
    public int getWriteCount(String owner, String name) {
        for (Map.Entry<FieldReference, AccessStats> entry : fieldAccesses.entrySet()) {
            if (entry.getKey().getOwner().equals(owner) &&
                entry.getKey().getName().equals(name)) {
                return entry.getValue().getWriteCount();
            }
        }
        return 0;
    }

    /**
     * Gets access statistics for all fields.
     */
    public Map<FieldReference, AccessStats> getFieldAccesses() {
        return Collections.unmodifiableMap(fieldAccesses);
    }

    /**
     * Gets the number of distinct fields accessed.
     */
    public int getDistinctFieldCount() {
        return fieldAccesses.size();
    }

    /**
     * Represents a field reference.
     */
    public static class FieldReference {
        private final String owner;
        private final String name;
        private final String descriptor;

        public FieldReference(String owner, String name, String descriptor) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FieldReference)) return false;
            FieldReference that = (FieldReference) o;
            return Objects.equals(owner, that.owner) &&
                   Objects.equals(name, that.name) &&
                   Objects.equals(descriptor, that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, name, descriptor);
        }

        @Override
        public String toString() {
            return owner + "." + name + ":" + descriptor;
        }
    }

    /**
     * Access statistics for a field.
     */
    public static class AccessStats {
        private int readCount;
        private int writeCount;

        public void incrementReads() {
            readCount++;
        }

        public void incrementWrites() {
            writeCount++;
        }

        public int getReadCount() {
            return readCount;
        }

        public int getWriteCount() {
            return writeCount;
        }

        public int getTotalCount() {
            return readCount + writeCount;
        }

        @Override
        public String toString() {
            return "reads=" + readCount + ", writes=" + writeCount;
        }
    }

    @Override
    public String toString() {
        return "FieldAccessListener[fieldReads=" + fieldReadCount +
            ", fieldWrites=" + fieldWriteCount +
            ", arrayReads=" + arrayReadCount +
            ", arrayWrites=" + arrayWriteCount + "]";
    }
}
