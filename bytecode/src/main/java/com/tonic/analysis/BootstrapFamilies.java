package com.tonic.analysis;

/**
 * Owner/name predicates for the JDK bootstrap-method families reachable from invokedynamic and
 * constant-dynamic.
 */
public final class BootstrapFamilies
{

    /**
     * Internal name of {@code LambdaMetafactory}, the lambda/method-reference bootstrap owner.
     */
    public static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";
    /**
     * Internal name of {@code StringConcatFactory}, the string-concatenation bootstrap owner.
     */
    public static final String STRING_CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory";
    /**
     * Internal name of {@code ObjectMethods}, the record-component bootstrap owner (Java 16+).
     */
    public static final String OBJECT_METHODS = "java/lang/runtime/ObjectMethods";
    /**
     * Internal name of {@code SwitchBootstraps}, the pattern-switch bootstrap owner (Java 21+).
     */
    public static final String SWITCH_BOOTSTRAPS = "java/lang/runtime/SwitchBootstraps";

    private BootstrapFamilies()
    {
    }

    /**
     * Tests whether an owner is {@code StringConcatFactory}.
     *
     * @param owner internal name of the bootstrap owner
     * @return true if the owner is the string-concatenation factory
     */
    public static boolean isStringConcat(String owner)
    {
        return STRING_CONCAT_FACTORY.equals(owner);
    }

    /**
     * Tests whether an owner/name pair is the {@code LambdaMetafactory}
     * {@code metafactory} or {@code altMetafactory} bootstrap.
     *
     * @param owner internal name of the bootstrap owner
     * @param name bootstrap method name
     * @return true if the pair names a lambda metafactory bootstrap
     */
    public static boolean isLambda(String owner, String name)
    {
        return LAMBDA_METAFACTORY.equals(owner) &&
               ("metafactory".equals(name) || "altMetafactory".equals(name));
    }

    /**
     * Tests whether an owner/name pair is the {@code ObjectMethods.bootstrap} record bootstrap.
     *
     * @param owner internal name of the bootstrap owner
     * @param name bootstrap method name
     * @return true if the pair names the record bootstrap
     */
    public static boolean isRecord(String owner, String name)
    {
        return OBJECT_METHODS.equals(owner) && "bootstrap".equals(name);
    }

    /**
     * Tests whether an owner is {@code SwitchBootstraps}.
     *
     * @param owner internal name of the bootstrap owner
     * @return true if the owner is the pattern-switch bootstrap holder
     */
    public static boolean isSwitch(String owner)
    {
        return SWITCH_BOOTSTRAPS.equals(owner);
    }
}
