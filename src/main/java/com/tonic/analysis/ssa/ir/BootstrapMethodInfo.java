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

    /**
     * Checks if this is a lambda metafactory bootstrap.
     *
     * @return true if this uses LambdaMetafactory
     */
    public boolean isLambdaMetafactory() {
        return "java/lang/invoke/LambdaMetafactory".equals(bootstrapMethod.getOwner()) &&
               "metafactory".equals(bootstrapMethod.getName());
    }

    /**
     * Checks if this is a string concatenation bootstrap.
     *
     * @return true if this uses StringConcatFactory
     */
    public boolean isStringConcatFactory() {
        return "java/lang/invoke/StringConcatFactory".equals(bootstrapMethod.getOwner());
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
