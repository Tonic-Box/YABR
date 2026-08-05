package com.tonic.analysis.simulation.heap;

import java.util.Objects;

/**
 * The identity of one allocation in the program - class, owning method and
 * instruction index - which bounds points-to analysis to one abstract object
 * per NEW instruction.
 */
public final class AllocationSite
{

    public static final String UNKNOWN_METHOD = "<unknown>";
    public static final AllocationSite EXTERNAL = new AllocationSite("<external>", -1, "<external>");

    private final String className;
    private final int instructionIndex;
    private final String methodKey;

    private AllocationSite(String className, int instructionIndex, String methodKey)
    {
        this.className = Objects.requireNonNull(className);
        this.instructionIndex = instructionIndex;
        this.methodKey = Objects.requireNonNull(methodKey);
    }

    /**
     * Creates a site for an allocation seen in analysed code.
     * @param className internal name of the allocated class
     * @param instructionIndex index of the allocating instruction
     * @param methodKey key of the method containing the allocation
     * @return the allocation site
     * @throws NullPointerException if className or methodKey is null
     */
    public static AllocationSite of(String className, int instructionIndex, String methodKey)
    {
        return new AllocationSite(className, instructionIndex, methodKey);
    }

    /**
     * Creates a site for an object that entered from outside the analysed code.
     * @param className internal name of the object's class
     * @return the allocation site
     * @throws NullPointerException if className is null
     */
    public static AllocationSite external(String className)
    {
        return new AllocationSite(className, -1, "<external>");
    }

    /**
     * Creates a site for an object the analysis invents rather than one the
     * program allocates.
     * @param className internal name of the object's class
     * @param description what the synthetic object stands for
     * @return the allocation site
     * @throws NullPointerException if className is null
     */
    public static AllocationSite synthetic(String className, String description)
    {
        return new AllocationSite(className, -2, "<synthetic:" + description + ">");
    }

    /**
     * @return the class name
     */
    public String getClassName()
    {
        return className;
    }

    /**
     * @return the instruction index
     */
    public int getInstructionIndex()
    {
        return instructionIndex;
    }

    /**
     * @return the method key
     */
    public String getMethodKey()
    {
        return methodKey;
    }

    /**
     * @return true if this site stands for an object from outside the analysed code
     */
    public boolean isExternal()
    {
        return instructionIndex == -1 && "<external>".equals(methodKey);
    }

    /**
     * @return true if this site stands for an object invented by the analysis
     */
    public boolean isSynthetic()
    {
        return instructionIndex == -2;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof AllocationSite)) return false;
        AllocationSite that = (AllocationSite) o;
        return instructionIndex == that.instructionIndex &&
               className.equals(that.className) &&
               methodKey.equals(that.methodKey);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(className, instructionIndex, methodKey);
    }

    @Override
    public String toString()
    {
        if (isExternal())
        {
            return "AllocationSite[external:" + className + "]";
        }
        if (isSynthetic())
        {
            return "AllocationSite[" + methodKey + ":" + className + "]";
        }
        return "AllocationSite[" + methodKey + "@" + instructionIndex + ":" + className + "]";
    }
}
