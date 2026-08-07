package com.tonic.analysis.execution.dispatch;

/**
 * A resolved method reference: owner class, name, descriptor, and invocation kind flags.
 */
public class MethodInfo
{
    private final String ownerClass;
    private final String methodName;
    private final String descriptor;
    private final boolean isStatic;
    private final boolean isInterface;
    private final boolean isSpecial;

    /**
     * Creates a non-special method reference.
     * @param ownerClass internal name of the declaring class
     * @param methodName the method's name
     * @param descriptor the method's descriptor
     * @param isStatic whether the method is invoked statically
     * @param isInterface whether the method is invoked via invokeinterface
     */
    public MethodInfo(String ownerClass, String methodName, String descriptor, boolean isStatic, boolean isInterface)
    {
        this(ownerClass, methodName, descriptor, isStatic, isInterface, false);
    }

    /**
     * Creates a method reference.
     * @param ownerClass internal name of the declaring class
     * @param methodName the method's name
     * @param descriptor the method's descriptor
     * @param isStatic whether the method is invoked statically
     * @param isInterface whether the method is invoked via invokeinterface
     * @param isSpecial whether the method is invoked via invokespecial
     */
    public MethodInfo(String ownerClass, String methodName, String descriptor, boolean isStatic, boolean isInterface, boolean isSpecial)
    {
        this.ownerClass = ownerClass;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
        this.isInterface = isInterface;
        this.isSpecial = isSpecial;
    }

    /**
     * @return the owner class
     */
    public String getOwnerClass()
    {
        return ownerClass;
    }

    /**
     * @return the method name
     */
    public String getMethodName()
    {
        return methodName;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * @return whether static
     */
    public boolean isStatic()
    {
        return isStatic;
    }

    /**
     * @return whether interface
     */
    public boolean isInterface()
    {
        return isInterface;
    }

    /**
     * @return whether special
     */
    public boolean isSpecial()
    {
        return isSpecial;
    }

    @Override
    public String toString()
    {
        return ownerClass + "." + methodName + descriptor +
               (isStatic ? " (static)" : "") + (isInterface ? " (interface)" : "") + (isSpecial ? " (special)" : "");
    }
}
