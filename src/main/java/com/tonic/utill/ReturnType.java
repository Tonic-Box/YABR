package com.tonic.utill;

import lombok.Getter;

/**
 * Enum representing JVM return instruction types.
 */
@Getter
public enum ReturnType {
    IRETURN(0xAC, "ireturn"),
    LRETURN(0xAD, "lreturn"),
    FRETURN(0xAE, "freturn"),
    DRETURN(0xAF, "dreturn"),
    ARETURN(0xB0, "areturn"),
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

        return switch (desc) {
            case "I" -> IRETURN;
            case "J" -> LRETURN;
            case "F" -> FRETURN;
            case "D" -> DRETURN;
            case "V" -> RETURN;
            default -> {
                if (desc.startsWith("L") && desc.endsWith(";")) {
                    yield ARETURN;
                }
                if (desc.startsWith("[")) {
                    yield ARETURN;
                }
                throw new IllegalArgumentException("Unknown descriptor: " + desc);
            }
        };
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