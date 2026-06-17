package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.MethodHandleConstant;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * Represents bootstrap method information for invokedynamic and constant dynamic.
 * Contains the bootstrap method handle and its static arguments.
 */
@Getter
public class BootstrapMethodInfo {

    private final MethodHandleConstant bootstrapMethod;
    private final List<Constant> bootstrapArguments;

    /**
     * Creates a bootstrap method info.
     *
     * @param bootstrapMethod the bootstrap method handle
     * @param bootstrapArguments the static arguments to the bootstrap method
     */
    public BootstrapMethodInfo(MethodHandleConstant bootstrapMethod, List<Constant> bootstrapArguments) {
        this.bootstrapMethod = Objects.requireNonNull(bootstrapMethod, "bootstrapMethod cannot be null");
        this.bootstrapArguments = List.copyOf(bootstrapArguments);
    }

    /** Internal name of {@code LambdaMetafactory}, the lambda/method-reference bootstrap owner. */
    public static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";
    /** Internal name of {@code StringConcatFactory}, the string-concatenation bootstrap owner. */
    public static final String STRING_CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory";
    /** Internal name of {@code ObjectMethods}, the record-component bootstrap owner (Java 16+). */
    public static final String OBJECT_METHODS = "java/lang/runtime/ObjectMethods";
    /** Internal name of {@code SwitchBootstraps}, the pattern-switch bootstrap owner (Java 21+). */
    public static final String SWITCH_BOOTSTRAPS = "java/lang/runtime/SwitchBootstraps";

    /**
     * Tests whether the given owner is {@code StringConcatFactory}.
     *
     * @param owner the bootstrap owner internal name
     * @return true for the string-concatenation factory
     */
    public static boolean isStringConcat(String owner) {
        return STRING_CONCAT_FACTORY.equals(owner);
    }

    /**
     * Tests whether the given owner/name is the {@code LambdaMetafactory}
     * {@code metafactory}/{@code altMetafactory} bootstrap.
     *
     * @param owner the bootstrap owner internal name
     * @param name  the bootstrap method name
     * @return true for a lambda metafactory bootstrap
     */
    public static boolean isLambda(String owner, String name) {
        return LAMBDA_METAFACTORY.equals(owner) &&
               ("metafactory".equals(name) || "altMetafactory".equals(name));
    }

    /**
     * Tests whether the given owner/name is the {@code ObjectMethods.bootstrap} record bootstrap.
     *
     * @param owner the bootstrap owner internal name
     * @param name  the bootstrap method name
     * @return true for the record {@code ObjectMethods} bootstrap
     */
    public static boolean isRecord(String owner, String name) {
        return OBJECT_METHODS.equals(owner) && "bootstrap".equals(name);
    }

    /**
     * Tests whether the given owner is {@code SwitchBootstraps}.
     *
     * @param owner the bootstrap owner internal name
     * @return true for a pattern-switch bootstrap
     */
    public static boolean isSwitch(String owner) {
        return SWITCH_BOOTSTRAPS.equals(owner);
    }

    /**
     * Checks if this is a lambda metafactory bootstrap.
     *
     * @return true if this uses LambdaMetafactory
     */
    public boolean isLambdaMetafactory() {
        return isLambda(bootstrapMethod.getOwner(), bootstrapMethod.getName());
    }

    /**
     * Checks if this is a string concatenation bootstrap.
     *
     * @return true if this uses StringConcatFactory
     */
    public boolean isStringConcatFactory() {
        return isStringConcat(bootstrapMethod.getOwner());
    }

    /**
     * Checks if this is the records {@code ObjectMethods} bootstrap (Java 16) backing the
     * auto-generated {@code equals}/{@code hashCode}/{@code toString} of a record.
     *
     * @return true if this uses java.lang.runtime.ObjectMethods
     */
    public boolean isObjectMethodsBootstrap() {
        return isRecord(bootstrapMethod.getOwner(), bootstrapMethod.getName());
    }

    /**
     * Checks if this is a {@code SwitchBootstraps} bootstrap (Java 21 pattern switch):
     * {@code typeSwitch} or {@code enumSwitch}.
     *
     * @return true if this uses java.lang.runtime.SwitchBootstraps
     */
    public boolean isSwitchBootstrap() {
        return isSwitch(bootstrapMethod.getOwner());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Bootstrap[");
        sb.append(bootstrapMethod.getOwner()).append(".");
        sb.append(bootstrapMethod.getName());
        if (!bootstrapArguments.isEmpty()) {
            sb.append(", args=").append(bootstrapArguments.size());
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BootstrapMethodInfo)) return false;
        BootstrapMethodInfo that = (BootstrapMethodInfo) o;
        return Objects.equals(bootstrapMethod, that.bootstrapMethod) &&
                Objects.equals(bootstrapArguments, that.bootstrapArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bootstrapMethod, bootstrapArguments);
    }
}
