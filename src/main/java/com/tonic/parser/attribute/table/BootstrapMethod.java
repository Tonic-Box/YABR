package com.tonic.parser.attribute.table;

import lombok.Getter;

import java.util.List;

/**
 * Represents a bootstrap method entry in the BootstrapMethods attribute.
 * Used for invokedynamic instructions and constant dynamic constants.
 */
@Getter
public class BootstrapMethod {
    private final int bootstrapMethodRef;
    private final List<Integer> bootstrapArguments;

    /**
     * Constructs a bootstrap method entry.
     *
     * @param bootstrapMethodRef constant pool index to the method handle
     * @param bootstrapArguments list of constant pool indices for arguments
     */
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