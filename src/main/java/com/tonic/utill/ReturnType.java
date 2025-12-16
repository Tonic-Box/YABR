package com.tonic.utill;

import lombok.Getter;

/**
 * Enum representing JVM return instruction types.
 */
@Getter
public enum ReturnType {
    /** Integer return instruction. */
    IRETURN(0xAC, "ireturn"),
    /** Long return instruction. */
    LRETURN(0xAD, "lreturn"),
    /** Float return instruction. */
    FRETURN(0xAE, "freturn"),
    /** Double return instruction. */
    DRETURN(0xAF, "dreturn"),
    /** Reference return instruction. */
    ARETURN(0xB0, "areturn"),
    /** Void return instruction. */
    RETURN(0xB1, "return");

    private final int opcode;
    private final String mnemonic;

    ReturnType(int opcode, String mnemonic) {
        this.opcode = opcode;
        this.mnemonic = mnemonic;
    }

    /**
     * Retrieves the ReturnType corresponding to the specified opcode.
     *
     * @param opcode the return instruction opcode
     * @return the corresponding ReturnType, or null if not found
     */
    public static ReturnType fromOpcode(int opcode) {
        for (ReturnType type : ReturnType.values()) {
            if (type.getOpcode() == opcode) {
                return type;
            }
        }
        return null;
    }

    /**
     * Determines the appropriate return type based on a method descriptor.
     *
     * @param desc the type descriptor (e.g., "I", "Ljava/lang/String;")
     * @return the corresponding ReturnType
     * @throws IllegalArgumentException if descriptor is null, empty, or unknown
     */
    public static ReturnType fromDescriptor(String desc) {
        if (desc == null || desc.isEmpty()) {
            throw new IllegalArgumentException("Descriptor cannot be null or empty");
        }

        switch (desc) {
            case "I":
                return IRETURN;
            case "J":
                return LRETURN;
            case "F":
                return FRETURN;
            case "D":
                return DRETURN;
            case "V":
                return RETURN;
            default:
                if (desc.startsWith("L") && desc.endsWith(";")) {
                    return ARETURN;
                }
                if (desc.startsWith("[")) {
                    return ARETURN;
                }
                throw new IllegalArgumentException("Unknown descriptor: " + desc);
        }
    }

    /**
     * Checks if the given opcode is a return instruction.
     *
     * @param opcode the opcode to check
     * @return true if the opcode represents a return instruction, false otherwise
     */
    public static boolean isReturnOpcode(int opcode) {
        return fromOpcode(opcode) != null;
    }
}