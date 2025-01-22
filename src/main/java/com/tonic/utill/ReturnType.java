package com.tonic.utill;

import lombok.Getter;

/**
 * Enum representing the types of return operations.
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

    public static ReturnType fromOpcode(int opcode) {
        for (ReturnType type : ReturnType.values()) {
            if (type.getOpcode() == opcode) {
                return type;
            }
        }
        return null;
    }
}