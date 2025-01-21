package com.tonic.utill;

public final class Access {

    // Access flag constants based on JVM specification
    public static final int ACC_PUBLIC       = 0x0001;
    public static final int ACC_PRIVATE      = 0x0002;
    public static final int ACC_PROTECTED    = 0x0004;
    public static final int ACC_STATIC       = 0x0008;
    public static final int ACC_FINAL        = 0x0010;
    public static final int ACC_SYNCHRONIZED = 0x0020;
    public static final int ACC_BRIDGE       = 0x0040;
    public static final int ACC_VARARGS      = 0x0080;
    public static final int ACC_NATIVE       = 0x0100;
    public static final int ACC_ABSTRACT     = 0x0400;
    public static final int ACC_STRICT       = 0x0800;
    public static final int ACC_SYNTHETIC    = 0x1000;

    // Private constructor to prevent instantiation
    private Access() {
        throw new UnsupportedOperationException("Access is a utility class and cannot be instantiated.");
    }

    /**
     * Checks if the access flags indicate that the entity is public.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the public flag is set, {@code false} otherwise
     */
    public static boolean isPublic(int accessFlags) {
        return (accessFlags & ACC_PUBLIC) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is private.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the private flag is set, {@code false} otherwise
     */
    public static boolean isPrivate(int accessFlags) {
        return (accessFlags & ACC_PRIVATE) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is protected.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the protected flag is set, {@code false} otherwise
     */
    public static boolean isProtected(int accessFlags) {
        return (accessFlags & ACC_PROTECTED) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is static.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the static flag is set, {@code false} otherwise
     */
    public static boolean isStatic(int accessFlags) {
        return (accessFlags & ACC_STATIC) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is final.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the final flag is set, {@code false} otherwise
     */
    public static boolean isFinal(int accessFlags) {
        return (accessFlags & ACC_FINAL) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is synchronized.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the synchronized flag is set, {@code false} otherwise
     */
    public static boolean isSynchronized(int accessFlags) {
        return (accessFlags & ACC_SYNCHRONIZED) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is a bridge method.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the bridge flag is set, {@code false} otherwise
     */
    public static boolean isBridge(int accessFlags) {
        return (accessFlags & ACC_BRIDGE) != 0;
    }

    /**
     * Checks if the access flags indicate that the method has variable arguments.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the varargs flag is set, {@code false} otherwise
     */
    public static boolean isVarArgs(int accessFlags) {
        return (accessFlags & ACC_VARARGS) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is native.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the native flag is set, {@code false} otherwise
     */
    public static boolean isNative(int accessFlags) {
        return (accessFlags & ACC_NATIVE) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is abstract.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the abstract flag is set, {@code false} otherwise
     */
    public static boolean isAbstract(int accessFlags) {
        return (accessFlags & ACC_ABSTRACT) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is strictfp.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the strictfp flag is set, {@code false} otherwise
     */
    public static boolean isStrict(int accessFlags) {
        return (accessFlags & ACC_STRICT) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is synthetic.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the synthetic flag is set, {@code false} otherwise
     */
    public static boolean isSynthetic(int accessFlags) {
        return (accessFlags & ACC_SYNTHETIC) != 0;
    }

    /**
     * Checks if the entity is either public, protected, or private.
     *
     * @param accessFlags the access flags integer
     * @return {@code true} if the entity has a visibility modifier, {@code false} otherwise
     */
    public static boolean hasVisibility(int accessFlags) {
        return isPublic(accessFlags) || isProtected(accessFlags) || isPrivate(accessFlags);
    }
}
