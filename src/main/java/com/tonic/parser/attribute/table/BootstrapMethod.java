package com.tonic.parser.attribute.table;

import lombok.Getter;

import java.util.List;

/**
 * Represents a bootstrap method.
 */
@Getter
public class BootstrapMethod {
    private final int bootstrapMethodRef;
    private final List<Integer> bootstrapArguments;

    public BootstrapMethod(int bootstrapMethodRef, List<Integer> bootstrapArguments) {
        this.bootstrapMethodRef = bootstrapMethodRef;
        this.bootstrapArguments = bootstrapArguments;
    }

    @Override
    public String toString() {
        return "BootstrapMethod{" +
                "bootstrapMethodRef=" + bootstrapMethodRef +
                ", bootstrapArguments=" + bootstrapArguments +
                '}';
    }
}