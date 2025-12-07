package com.tonic.utill;

/**
 * Utility class for constructing and checking access flags in Java class files.
 * Provides static methods to test various access modifiers based on access flag integers.
 * <p>
 * The {@code Modifiers} class defines constants for access flags as per the JVM specification
 * and offers methods to set and check these flags.
 * </p>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * // Building access flags for a public static final method
 * int accessFlags = new AccessBuilder()
 *                         .setPublic()
 *                         .setStatic()
 *                         .setFinal()
 *                         .build();
 *
 * // Checking if the method is static
 * boolean isStatic = Modifiers.isStatic(accessFlags); // returns true
 * }</pre>
 */
public final class Modifiers {

    public static final int PUBLIC       = 0x0001;
    public static final int PRIVATE      = 0x0002;
    public static final int PROTECTED    = 0x0004;
    public static final int STATIC       = 0x0008;
    public static final int FINAL        = 0x0010;
    public static final int SYNCHRONIZED = 0x0020;
    public static final int BRIDGE       = 0x0040;
    public static final int VARARGS      = 0x0080;
    public static final int NATIVE       = 0x0100;
    public static final int INTERFACE    = 0x0200;
    public static final int ABSTRACT     = 0x0400;
    public static final int STRICT       = 0x0800;
    public static final int SYNTHETIC    = 0x1000;
    public static final int ANNOTATION   = 0x2000;
    public static final int ENUM         = 0x4000;
    public static final int VOLATILE     = 0x0040;
    public static final int TRANSIENT    = 0x0080;

    /**
     * Private constructor to prevent instantiation.
     * The {@code Modifiers} class is a utility class and should not be instantiated.
     */
    private Modifiers() {
        throw new UnsupportedOperationException("Modifiers is a utility class and cannot be instantiated.");
    }

    /**
     * Sets the {@code public} access flag.
     * Clears {@code private} and {@code protected} flags to ensure only one visibility modifier is set.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code public} set
     */
    public static int setPublic(int mod) {
        return (mod & ~(PRIVATE | PROTECTED)) | PUBLIC;
    }

    /**
     * Sets the {@code private} access flag.
     * Clears {@code public} and {@code protected} flags to ensure only one visibility modifier is set.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code private} set
     */
    public static int setPrivate(int mod) {
        return (mod & ~(PUBLIC | PROTECTED)) | PRIVATE;
    }

    /**
     * Sets the {@code protected} access flag.
     * Clears {@code public} and {@code private} flags to ensure only one visibility modifier is set.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code protected} set
     */
    public static int setProtected(int mod) {
        return (mod & ~(PUBLIC | PRIVATE)) | PROTECTED;
    }

    /**
     * Sets the {@code static} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code static} set
     */
    public static int setStatic(int mod) {
        return mod | STATIC;
    }

    /**
     * Sets the {@code interface} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code interface} set
     */
    public static int setInterface(int mod) {
        return mod | INTERFACE;
    }

    /**
     * Sets the {@code final} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code final} set
     */
    public static int setFinal(int mod) {
        return mod | FINAL;
    }

    /**
     * Sets the {@code synchronized} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code synchronized} set
     */
    public static int setSynchronized(int mod) {
        return mod | SYNCHRONIZED;
    }

    /**
     * Sets the {@code bridge} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code bridge} set
     */
    public static int setBridge(int mod) {
        return mod | BRIDGE;
    }

    /**
     * Sets the {@code varargs} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code varargs} set
     */
    public static int setVarArgs(int mod) {
        return mod | VARARGS;
    }

    /**
     * Sets the {@code native} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code native} set
     */
    public static int setNative(int mod) {
        return mod | NATIVE;
    }

    /**
     * Sets the {@code abstract} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code abstract} set
     */
    public static int setAbstract(int mod) {
        return mod | ABSTRACT;
    }

    /**
     * Sets the {@code strictfp} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code strictfp} set
     */
    public static int setStrict(int mod) {
        return mod | STRICT;
    }

    /**
     * Sets the {@code synthetic} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code synthetic} set
     */
    public static int setSynthetic(int mod) {
        return mod | SYNTHETIC;
    }

    /**
     * Sets the {@code annotation} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code annotation} set
     */
    public static int setAnnotation(int mod) {
        return mod | ANNOTATION;
    }

    /**
     * Sets the {@code enum} access flag.
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code enum} set
     */
    public static int setEnum(int mod) {
        return mod | ENUM;
    }

    /**
     * Sets the {@code volatile} access flag.
     * <p>
     * Note: Typically used for fields.
     * </p>
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code volatile} set
     */
    public static int setVolatile(int mod) {
        return mod | VOLATILE;
    }

    /**
     * Sets the {@code transient} access flag.
     * <p>
     * Note: Typically used for fields.
     * </p>
     *
     * @param mod the current access flags
     * @return the updated access flags with {@code transient} set
     */
    public static int setTransient(int mod) {
        return mod | TRANSIENT;
    }

    /**
     * Checks if the access flags indicate that the entity is public.
     *
     * @param mod the access flags integer
     * @return {@code true} if the public flag is set, {@code false} otherwise
     */
    public static boolean isPublic(int mod) {
        return (mod & PUBLIC) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is private.
     *
     * @param mod the access flags integer
     * @return {@code true} if the private flag is set, {@code false} otherwise
     */
    public static boolean isPrivate(int mod) {
        return (mod & PRIVATE) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is protected.
     *
     * @param mod the access flags integer
     * @return {@code true} if the protected flag is set, {@code false} otherwise
     */
    public static boolean isProtected(int mod) {
        return (mod & PROTECTED) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is static.
     *
     * @param mod the access flags integer
     * @return {@code true} if the static flag is set, {@code false} otherwise
     */
    public static boolean isStatic(int mod) {
        return (mod & STATIC) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is final.
     *
     * @param mod the access flags integer
     * @return {@code true} if the final flag is set, {@code false} otherwise
     */
    public static boolean isFinal(int mod) {
        return (mod & FINAL) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is synchronized.
     *
     * @param mod the access flags integer
     * @return {@code true} if the synchronized flag is set, {@code false} otherwise
     */
    public static boolean isSynchronized(int mod) {
        return (mod & SYNCHRONIZED) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is a bridge method.
     *
     * @param mod the access flags integer
     * @return {@code true} if the bridge flag is set, {@code false} otherwise
     */
    public static boolean isBridge(int mod) {
        return (mod & BRIDGE) != 0;
    }

    /**
     * Checks if the access flags indicate that the method has variable arguments.
     *
     * @param mod the access flags integer
     * @return {@code true} if the varargs flag is set, {@code false} otherwise
     */
    public static boolean isVarArgs(int mod) {
        return (mod & VARARGS) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is native.
     *
     * @param mod the access flags integer
     * @return {@code true} if the native flag is set, {@code false} otherwise
     */
    public static boolean isNative(int mod) {
        return (mod & NATIVE) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is abstract.
     *
     * @param mod the access flags integer
     * @return {@code true} if the abstract flag is set, {@code false} otherwise
     */
    public static boolean isAbstract(int mod) {
        return (mod & ABSTRACT) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is strictfp.
     *
     * @param mod the access flags integer
     * @return {@code true} if the strictfp flag is set, {@code false} otherwise
     */
    public static boolean isStrict(int mod) {
        return (mod & STRICT) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is synthetic.
     *
     * @param mod the access flags integer
     * @return {@code true} if the synthetic flag is set, {@code false} otherwise
     */
    public static boolean isSynthetic(int mod) {
        return (mod & SYNTHETIC) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is an annotation.
     *
     * @param mod the access flags integer
     * @return {@code true} if the annotation flag is set, {@code false} otherwise
     */
    public static boolean isAnnotation(int mod) {
        return (mod & ANNOTATION) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is an enum.
     *
     * @param mod the access flags integer
     * @return {@code true} if the enum flag is set, {@code false} otherwise
     */
    public static boolean isEnum(int mod) {
        return (mod & ENUM) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is an interface.
     *
     * @param mod the access flags integer
     * @return {@code true} if the interface flag is set, {@code false} otherwise
     */
    public static boolean isInterface(int mod) {
        return (mod & INTERFACE) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is volatile.
     * <p>
     * Note: Typically used for fields.
     * </p>
     *
     * @param mod the access flags integer
     * @return {@code true} if the volatile flag is set, {@code false} otherwise
     */
    public static boolean isVolatile(int mod) {
        return (mod & VOLATILE) != 0;
    }

    /**
     * Checks if the access flags indicate that the entity is transient.
     * <p>
     * Note: Typically used for fields.
     * </p>
     *
     * @param mod the access flags integer
     * @return {@code true} if the transient flag is set, {@code false} otherwise
     */
    public static boolean isTransient(int mod) {
        return (mod & TRANSIENT) != 0;
    }

    /**
     * Determines if the entity has package-private visibility.
     * <p>
     * This is true if none of {@code public}, {@code protected}, or {@code private} flags are set.
     * </p>
     *
     * @param mod the access flags integer
     * @return {@code true} if the entity has package-private visibility, {@code false} otherwise
     */
    public static boolean isPackagePrivate(int mod) {
        return (mod & (PUBLIC | PRIVATE | PROTECTED)) == 0;
    }

    /**
     * Returns a string describing the access modifier flags in the specified modifier.
     *
     * @param mod modifier flags.
     * @return a string representation of the modifiers
     */
    public static String toString(int mod) {
        return java.lang.reflect.Modifier.toString(mod);
    }
}
