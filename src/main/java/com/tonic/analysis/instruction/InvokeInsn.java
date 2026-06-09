package com.tonic.analysis.instruction;

/**
 * Common supertype for the four owner-bearing invoke instructions —
 * {@link InvokeVirtualInstruction}, {@link InvokeSpecialInstruction}, {@link InvokeStaticInstruction},
 * and {@link InvokeInterfaceInstruction}. Lets call-site work (call graphs, inlining, owner rewriting)
 * extract the callee uniformly: {@code if (insn instanceof InvokeInsn call) { call.getOwnerClass(); ... }}.
 *
 * <p>{@code invokedynamic} ({@link InvokeDynamicInstruction}) is intentionally excluded — it has no
 * owning class.
 */
public interface InvokeInsn {

    /** The internal name of the class that owns the invoked method. */
    String getOwnerClass();

    /** The invoked method's name. */
    String getMethodName();

    /** The invoked method's descriptor. */
    String getMethodDescriptor();

    /** Whether this is an {@code invokestatic}. */
    default boolean isStatic() {
        return this instanceof InvokeStaticInstruction;
    }

    /** Whether this is an {@code invokeinterface}. */
    default boolean isInterface() {
        return this instanceof InvokeInterfaceInstruction;
    }
}
