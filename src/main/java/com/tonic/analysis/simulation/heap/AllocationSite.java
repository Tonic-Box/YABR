package com.tonic.analysis.simulation.heap;

import java.util.Objects;

/**
 * Represents a unique allocation site in the program.
 * Each NEW instruction creates a distinct allocation site, enabling
 * points-to analysis with bounded memory.
 */
public final class AllocationSite {

    public static final String UNKNOWN_METHOD = "<unknown>";
    public static final AllocationSite EXTERNAL = new AllocationSite("<external>", -1, "<external>");

    private final String className;
    private final int instructionIndex;
    private final String methodKey;

    private AllocationSite(String className, int instructionIndex, String methodKey) {
        this.className = Objects.requireNonNull(className);
        this.instructionIndex = instructionIndex;
        this.methodKey = Objects.requireNonNull(methodKey);
    }

    public static AllocationSite of(String className, int instructionIndex, String methodKey) {
        return new AllocationSite(className, instructionIndex, methodKey);
    }

    public static AllocationSite external(String className) {
        return new AllocationSite(className, -1, "<external>");
    }

    public static AllocationSite synthetic(String className, String description) {
        return new AllocationSite(className, -2, "<synthetic:" + description + ">");
    }

    public String getClassName() {
        return className;
    }

    public int getInstructionIndex() {
        return instructionIndex;
    }

    public String getMethodKey() {
        return methodKey;
    }

    public boolean isExternal() {
        return instructionIndex == -1 && "<external>".equals(methodKey);
    }

    public boolean isSynthetic() {
        return instructionIndex == -2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AllocationSite)) return false;
        AllocationSite that = (AllocationSite) o;
        return instructionIndex == that.instructionIndex &&
               className.equals(that.className) &&
               methodKey.equals(that.methodKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, instructionIndex, methodKey);
    }

    @Override
    public String toString() {
        if (isExternal()) {
            return "AllocationSite[external:" + className + "]";
        }
        if (isSynthetic()) {
            return "AllocationSite[" + methodKey + ":" + className + "]";
        }
        return "AllocationSite[" + methodKey + "@" + instructionIndex + ":" + className + "]";
    }
}
