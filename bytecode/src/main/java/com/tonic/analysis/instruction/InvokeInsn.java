package com.tonic.analysis.instruction;

/**
 * Common supertype for the four owner-bearing invoke instructions - {@link InvokeVirtualInstruction}, {@link
 * InvokeSpecialInstruction}, {@link InvokeStaticInstruction}, and {@link InvokeInterfaceInstruction}.
 */
public interface InvokeInsn
{

    /**
     * @return the internal name of the class that owns the invoked method
     */
    String getOwnerClass();

    /**
     * @return the invoked method's name
     */
    String getMethodName();

    /**
     * @return the invoked method's descriptor
     */
    String getMethodDescriptor();

    /**
     * @return true if this is an {@code invokestatic}
     */
    default boolean isStatic()
    {
        return this instanceof InvokeStaticInstruction;
    }

    /**
     * @return true if this is an {@code invokeinterface}
     */
    default boolean isInterface()
    {
        return this instanceof InvokeInterfaceInstruction;
    }
}
