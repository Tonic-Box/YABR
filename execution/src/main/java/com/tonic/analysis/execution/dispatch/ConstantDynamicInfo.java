package com.tonic.analysis.execution.dispatch;

/**
 * A CONSTANT_Dynamic constant pool entry awaiting bootstrap-method resolution.
 */
public final class ConstantDynamicInfo
{

    private final int bootstrapMethodIndex;
    private final String name;
    private final String descriptor;
    private final int constantPoolIndex;

    /**
     * Creates a constant-dynamic descriptor.
     * @param bootstrapMethodIndex index into the BootstrapMethods attribute
     * @param name the constant's name
     * @param descriptor the constant's field descriptor
     * @param constantPoolIndex index of the entry in the constant pool
     */
    public ConstantDynamicInfo(int bootstrapMethodIndex, String name, String descriptor, int constantPoolIndex)
    {
        this.bootstrapMethodIndex = bootstrapMethodIndex;
        this.name = name;
        this.descriptor = descriptor;
        this.constantPoolIndex = constantPoolIndex;
    }

    /**
     * @return the bootstrap method index
     */
    public int getBootstrapMethodIndex()
    {
        return bootstrapMethodIndex;
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

    /**
     * @return the constant pool index
     */
    public int getConstantPoolIndex()
    {
        return constantPoolIndex;
    }

    /**
     * @return the return type
     */
    public String getReturnType()
    {
        return descriptor;
    }

    /**
     * Checks whether the constant occupies two stack slots.
     * @return true if the descriptor is long or double
     */
    public boolean isWideType()
    {
        return "J".equals(descriptor) || "D".equals(descriptor);
    }

    /**
     * Checks whether the constant is of primitive type.
     * @return true if the descriptor denotes a primitive
     */
    public boolean isPrimitive()
    {
        if (descriptor == null || descriptor.isEmpty())
        {
            return false;
        }
        char c = descriptor.charAt(0);
        return c == 'Z' || c == 'B' || c == 'C' || c == 'S' ||
               c == 'I' || c == 'J' || c == 'F' || c == 'D';
    }

    /**
     * Checks whether the constant is of reference type.
     * @return true if the descriptor denotes an object or array
     */
    public boolean isReference()
    {
        if (descriptor == null || descriptor.isEmpty())
        {
            return false;
        }
        char c = descriptor.charAt(0);
        return c == 'L' || c == '[';
    }

    @Override
    public String toString()
    {
        return "ConstantDynamicInfo{" +
            "bsm=" + bootstrapMethodIndex +
            ", name='" + name + '\'' +
            ", desc='" + descriptor + '\'' +
            ", cpIndex=" + constantPoolIndex +
            '}';
    }
}
