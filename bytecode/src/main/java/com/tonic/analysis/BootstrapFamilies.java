package com.tonic.analysis;

/**
 * Classification predicates for the well-known JDK bootstrap-method families used by
 * invokedynamic / constant-dynamic: lambdas, string concatenation, records, and pattern switch.
 *
 * These are pure owner/name string checks and live in the bytecode layer so that both
 * bytecode-level analysis ({@link Bootstraps}) and the SSA IR
 * ({@code com.tonic.analysis.ssa.ir.BootstrapMethodInfo}) can share them without a cyclic
 * dependency between the two layers.
 */
public final class BootstrapFamilies {

    /** Internal name of {@code LambdaMetafactory}, the lambda/method-reference bootstrap owner. */
    public static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";
    /** Internal name of {@code StringConcatFactory}, the string-concatenation bootstrap owner. */
    public static final String STRING_CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory";
    /** Internal name of {@code ObjectMethods}, the record-component bootstrap owner (Java 16+). */
    public static final String OBJECT_METHODS = "java/lang/runtime/ObjectMethods";
    /** Internal name of {@code SwitchBootstraps}, the pattern-switch bootstrap owner (Java 21+). */
    public static final String SWITCH_BOOTSTRAPS = "java/lang/runtime/SwitchBootstraps";

    private BootstrapFamilies() {
    }

    /**
     * Tests whether the given owner is {@code StringConcatFactory}.
     */
    public static boolean isStringConcat(String owner) {
        return STRING_CONCAT_FACTORY.equals(owner);
    }

    /**
     * Tests whether the given owner/name is the {@code LambdaMetafactory}
     * {@code metafactory}/{@code altMetafactory} bootstrap.
     */
    public static boolean isLambda(String owner, String name) {
        return LAMBDA_METAFACTORY.equals(owner) &&
               ("metafactory".equals(name) || "altMetafactory".equals(name));
    }

    /**
     * Tests whether the given owner/name is the {@code ObjectMethods.bootstrap} record bootstrap.
     */
    public static boolean isRecord(String owner, String name) {
        return OBJECT_METHODS.equals(owner) && "bootstrap".equals(name);
    }

    /**
     * Tests whether the given owner is {@code SwitchBootstraps}.
     */
    public static boolean isSwitch(String owner) {
        return SWITCH_BOOTSTRAPS.equals(owner);
    }
}
