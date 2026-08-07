package com.tonic.type;

import java.util.Objects;

/**
 * A CONSTANT_MethodHandle target: a reference kind tag plus the member it points at.
 */
public class MethodHandle
{

    public static final int H_GETFIELD = 1;
    public static final int H_GETSTATIC = 2;
    public static final int H_PUTFIELD = 3;
    public static final int H_PUTSTATIC = 4;
    public static final int H_INVOKEVIRTUAL = 5;
    public static final int H_INVOKESTATIC = 6;
    public static final int H_INVOKESPECIAL = 7;
    public static final int H_NEWINVOKESPECIAL = 8;
    public static final int H_INVOKEINTERFACE = 9;

    private final int tag;
    private final String owner;
    private final String name;
    private final String descriptor;
    private final boolean isInterface;

    /**
     * Creates a method handle whose owner is a class rather than an interface.
     * @param tag one of the H_ reference kinds
     * @param owner the internal name of the declaring class
     * @param name the member name
     * @param descriptor the member descriptor
     */
    public MethodHandle(int tag, String owner, String name, String descriptor)
    {
        this(tag, owner, name, descriptor, false);
    }

    /**
     * Creates a method handle.
     * @param tag one of the H_ reference kinds
     * @param owner the internal name of the declaring class
     * @param name the member name
     * @param descriptor the member descriptor
     * @param isInterface true if the owner is an interface
     */
    public MethodHandle(int tag, String owner, String name, String descriptor, boolean isInterface)
    {
        this.tag = tag;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.isInterface = isInterface;
    }

    /**
     * @return the tag
     */
    public int getTag()
    {
        return tag;
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

    /**
     * @return whether interface
     */
    public boolean isInterface()
    {
        return isInterface;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodHandle that = (MethodHandle) o;
        return tag == that.tag &&
               isInterface == that.isInterface &&
               Objects.equals(owner, that.owner) &&
               Objects.equals(name, that.name) &&
               Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(tag, owner, name, descriptor, isInterface);
    }

    @Override
    public String toString()
    {
        return "MethodHandle{" +
               "tag=" + tag +
               ", owner='" + owner + '\'' +
               ", name='" + name + '\'' +
               ", descriptor='" + descriptor + '\'' +
               ", isInterface=" + isInterface +
               '}';
    }
}
