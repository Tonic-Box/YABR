package com.tonic.analysis.verifier.structural;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.analysis.verifier.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;

import java.util.HashSet;
import java.util.Set;

public class StructuralVerifier {
    private final ClassFile classFile;
    private final VerifierConfig config;
    private final OperandValidator operandValidator;

    private static final Set<Integer> RESERVED_OPCODES = Set.of(
            0xBA,
            0xCA, 0xCB, 0xCC, 0xCD, 0xCE, 0xCF,
            0xD0, 0xD1, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7,
            0xD8, 0xD9, 0xDA, 0xDB, 0xDC, 0xDD, 0xDE, 0xDF,
            0xE0, 0xE1, 0xE2, 0xE3, 0xE4, 0xE5, 0xE6, 0xE7,
            0xE8, 0xE9, 0xEA, 0xEB, 0xEC, 0xED, 0xEE, 0xEF,
            0xF0, 0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7,
            0xF8, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD, 0xFE
    );

    private static final Set<Integer> WIDEABLE_OPCODES = Set.of(
            0x15, 0x16, 0x17, 0x18, 0x19,
            0x36, 0x37, 0x38, 0x39, 0x3A,
            0x84, 0xA9
    );

    public StructuralVerifier(ClassFile classFile, VerifierConfig config) {
        this.classFile = classFile;
        this.config = config;
        this.operandValidator = new OperandValidator(classFile != null ? classFile.getConstPool() : null);
    }

    public void verify(MethodEntry method, ErrorCollector collector) {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return;
        }

        byte[] bytecode = code.getCode();
        if (bytecode == null || bytecode.length == 0) {
            return;
        }

        if (bytecode.length > 65535) {
            collector.addError(new VerificationError(
                    VerificationErrorType.CODE_TOO_LONG,
                    0,
                    "Code length " + bytecode.length + " exceeds maximum of 65535 bytes"
            ));
            if (collector.shouldStop()) return;
        }

        Set<Integer> instructionBoundaries = new HashSet<>();
        int offset = 0;

        while (offset < bytecode.length) {
            if (collector.shouldStop()) return;

            instructionBoundaries.add(offset);
            int opcode = Byte.toUnsignedInt(bytecode[offset]);

            if (opcode > 0xC9 || RESERVED_OPCODES.contains(opcode)) {
                if (opcode != 0xBA) {
                    collector.addError(new VerificationError(
                            VerificationErrorType.INVALID_OPCODE,
                            offset,
                            "Invalid opcode 0x" + Integer.toHexString(opcode)
                    ));
                    if (collector.shouldStop()) return;
                    offset++;
                    continue;
                }
            }

            if (opcode == 0xC4) {
                if (offset + 1 >= bytecode.length) {
                    collector.addError(new VerificationError(
                            VerificationErrorType.INSTRUCTION_FALLS_OFF_END,
                            offset,
                            "WIDE instruction at end of code"
                    ));
                    return;
                }
                int wideOpcode = Byte.toUnsignedInt(bytecode[offset + 1]);
                if (!WIDEABLE_OPCODES.contains(wideOpcode)) {
                    collector.addError(new VerificationError(
                            VerificationErrorType.INVALID_WIDE_OPCODE,
                            offset,
                            "WIDE prefix followed by non-wideable opcode 0x" + Integer.toHexString(wideOpcode)
                    ));
                    if (collector.shouldStop()) return;
                }
            }

            int length = getInstructionLength(opcode, offset, bytecode);
            if (length <= 0) {
                collector.addError(new VerificationError(
                        VerificationErrorType.INVALID_OPCODE,
                        offset,
                        "Could not determine instruction length for opcode 0x" + Integer.toHexString(opcode)
                ));
                return;
            }

            if (offset + length > bytecode.length) {
                collector.addError(new VerificationError(
                        VerificationErrorType.INSTRUCTION_FALLS_OFF_END,
                        offset,
                        "Instruction at offset " + offset + " extends past end of code"
                ));
                return;
            }

            operandValidator.validate(opcode, offset, bytecode, collector);
            if (collector.shouldStop()) return;

            offset += length;
        }

        verifyBranchTargets(bytecode, instructionBoundaries, collector);
        if (collector.shouldStop()) return;

        verifyLastInstruction(bytecode, collector);
    }

    private void verifyBranchTargets(byte[] bytecode, Set<Integer> boundaries, ErrorCollector collector) {
        int offset = 0;
        while (offset < bytecode.length) {
            if (collector.shouldStop()) return;

            int opcode = Byte.toUnsignedInt(bytecode[offset]);
            int length = getInstructionLength(opcode, offset, bytecode);
            if (length <= 0) {
                offset++;
                continue;
            }

            if (isBranchInstruction(opcode)) {
                int target = getBranchTarget(opcode, offset, bytecode);
                if (target >= 0 && target < bytecode.length) {
                    if (!boundaries.contains(target)) {
                        collector.addError(new VerificationError(
                                VerificationErrorType.INVALID_BRANCH_TARGET,
                                offset,
                                "Branch target " + target + " is not an instruction boundary"
                        ));
                    }
                } else if (target < 0 || target >= bytecode.length) {
                    collector.addError(new VerificationError(
                            VerificationErrorType.INVALID_BRANCH_TARGET,
                            offset,
                            "Branch target " + target + " is outside code bounds [0, " + bytecode.length + ")"
                    ));
                }
            }

            if (opcode == 0xAA) {
                verifySwitchTargets(offset, bytecode, boundaries, collector, true);
            } else if (opcode == 0xAB) {
                verifySwitchTargets(offset, bytecode, boundaries, collector, false);
            }

            offset += length;
        }
    }

    private void verifySwitchTargets(int offset, byte[] bytecode, Set<Integer> boundaries,
                                     ErrorCollector collector, boolean isTableSwitch) {
        int padding = (4 - ((offset + 1) % 4)) % 4;
        int baseOffset = offset + 1 + padding;

        if (baseOffset + 4 > bytecode.length) return;

        int defaultOffset = readInt(bytecode, baseOffset);
        int defaultTarget = offset + defaultOffset;

        if (defaultTarget < 0 || defaultTarget >= bytecode.length || !boundaries.contains(defaultTarget)) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_BRANCH_TARGET,
                    offset,
                    "Switch default target " + defaultTarget + " is invalid"
            ));
        }

        if (isTableSwitch) {
            if (baseOffset + 12 > bytecode.length) return;
            int low = readInt(bytecode, baseOffset + 4);
            int high = readInt(bytecode, baseOffset + 8);

            if (low > high) {
                collector.addError(new VerificationError(
                        VerificationErrorType.INVALID_OPERAND,
                        offset,
                        "TABLESWITCH low (" + low + ") > high (" + high + ")"
                ));
                return;
            }

            int jumpTableStart = baseOffset + 12;
            for (int i = 0; i <= high - low; i++) {
                if (jumpTableStart + (i + 1) * 4 > bytecode.length) break;
                int jumpOffset = readInt(bytecode, jumpTableStart + i * 4);
                int target = offset + jumpOffset;
                if (target < 0 || target >= bytecode.length || !boundaries.contains(target)) {
                    collector.addError(new VerificationError(
                            VerificationErrorType.INVALID_BRANCH_TARGET,
                            offset,
                            "TABLESWITCH case " + (low + i) + " target " + target + " is invalid"
                    ));
                }
            }
        } else {
            if (baseOffset + 8 > bytecode.length) return;
            int npairs = readInt(bytecode, baseOffset + 4);

            if (npairs < 0) {
                collector.addError(new VerificationError(
                        VerificationErrorType.INVALID_OPERAND,
                        offset,
                        "LOOKUPSWITCH npairs (" + npairs + ") is negative"
                ));
                return;
            }

            int pairStart = baseOffset + 8;
            int lastKey = Integer.MIN_VALUE;
            boolean firstKey = true;

            for (int i = 0; i < npairs; i++) {
                if (pairStart + (i + 1) * 8 > bytecode.length) break;
                int key = readInt(bytecode, pairStart + i * 8);
                int jumpOffset = readInt(bytecode, pairStart + i * 8 + 4);
                int target = offset + jumpOffset;

                if (!firstKey && key <= lastKey) {
                    collector.addWarning(new VerificationError(
                            VerificationErrorType.INVALID_OPERAND,
                            offset,
                            "LOOKUPSWITCH keys not in ascending order at pair " + i,
                            VerificationError.Severity.WARNING
                    ));
                }
                firstKey = false;
                lastKey = key;

                if (target < 0 || target >= bytecode.length || !boundaries.contains(target)) {
                    collector.addError(new VerificationError(
                            VerificationErrorType.INVALID_BRANCH_TARGET,
                            offset,
                            "LOOKUPSWITCH case " + key + " target " + target + " is invalid"
                    ));
                }
            }
        }
    }

    private void verifyLastInstruction(byte[] bytecode, ErrorCollector collector) {
        if (bytecode.length == 0) return;

        int offset = 0;
        int lastOffset = 0;
        int lastOpcode = 0;

        while (offset < bytecode.length) {
            lastOffset = offset;
            lastOpcode = Byte.toUnsignedInt(bytecode[offset]);
            int length = getInstructionLength(lastOpcode, offset, bytecode);
            if (length <= 0) break;
            offset += length;
        }
    }

    private boolean isBranchInstruction(int opcode) {
        return (opcode >= 0x99 && opcode <= 0xA6) ||
               opcode == 0xA7 ||
               opcode == 0xA8 ||
               opcode == 0xC6 ||
               opcode == 0xC7 ||
               opcode == 0xC8 ||
               opcode == 0xC9;
    }

    private int getBranchTarget(int opcode, int offset, byte[] bytecode) {
        if (opcode == 0xC8 || opcode == 0xC9) {
            if (offset + 4 >= bytecode.length) return -1;
            int branchOffset = readInt(bytecode, offset + 1);
            return offset + branchOffset;
        } else {
            if (offset + 2 >= bytecode.length) return -1;
            short branchOffset = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
            return offset + branchOffset;
        }
    }

    private int getInstructionLength(int opcode, int offset, byte[] bytecode) {
        switch (opcode) {
            case 0x00: case 0x01: case 0x02: case 0x03: case 0x04:
            case 0x05: case 0x06: case 0x07: case 0x08: case 0x09:
            case 0x0A: case 0x0B: case 0x0C: case 0x0D: case 0x0E:
            case 0x0F:
                return 1;
            case 0x10:
                return 2;
            case 0x11:
                return 3;
            case 0x12:
                return 2;
            case 0x13: case 0x14:
                return 3;
            case 0x15: case 0x16: case 0x17: case 0x18: case 0x19:
                return 2;
            case 0x1A: case 0x1B: case 0x1C: case 0x1D:
            case 0x1E: case 0x1F: case 0x20: case 0x21:
            case 0x22: case 0x23: case 0x24: case 0x25:
            case 0x26: case 0x27: case 0x28: case 0x29:
            case 0x2A: case 0x2B: case 0x2C: case 0x2D:
            case 0x2E: case 0x2F: case 0x30: case 0x31:
            case 0x32: case 0x33: case 0x34: case 0x35:
                return 1;
            case 0x36: case 0x37: case 0x38: case 0x39: case 0x3A:
                return 2;
            case 0x3B: case 0x3C: case 0x3D: case 0x3E:
            case 0x3F: case 0x40: case 0x41: case 0x42:
            case 0x43: case 0x44: case 0x45: case 0x46:
            case 0x47: case 0x48: case 0x49: case 0x4A:
            case 0x4B: case 0x4C: case 0x4D: case 0x4E:
            case 0x4F: case 0x50: case 0x51: case 0x52:
            case 0x53: case 0x54: case 0x55: case 0x56:
            case 0x57: case 0x58: case 0x59: case 0x5A:
            case 0x5B: case 0x5C: case 0x5D: case 0x5E:
            case 0x5F:
                return 1;
            case 0x60: case 0x61: case 0x62: case 0x63:
            case 0x64: case 0x65: case 0x66: case 0x67:
            case 0x68: case 0x69: case 0x6A: case 0x6B:
            case 0x6C: case 0x6D: case 0x6E: case 0x6F:
            case 0x70: case 0x71: case 0x72: case 0x73:
            case 0x74: case 0x75: case 0x76: case 0x77:
            case 0x78: case 0x79: case 0x7A: case 0x7B:
            case 0x7C: case 0x7D: case 0x7E: case 0x7F:
            case 0x80: case 0x81: case 0x82: case 0x83:
                return 1;
            case 0x84:
                return 3;
            case 0x85: case 0x86: case 0x87: case 0x88:
            case 0x89: case 0x8A: case 0x8B: case 0x8C:
            case 0x8D: case 0x8E: case 0x8F: case 0x90:
            case 0x91: case 0x92: case 0x93: case 0x94:
            case 0x95: case 0x96: case 0x97: case 0x98:
                return 1;
            case 0x99: case 0x9A: case 0x9B: case 0x9C:
            case 0x9D: case 0x9E: case 0x9F: case 0xA0:
            case 0xA1: case 0xA2: case 0xA3: case 0xA4:
            case 0xA5: case 0xA6:
                return 3;
            case 0xA7:
                return 3;
            case 0xA8:
                return 3;
            case 0xA9:
                return 2;
            case 0xAA: {
                int padding = (4 - ((offset + 1) % 4)) % 4;
                int baseOffset = offset + 1 + padding;
                if (baseOffset + 12 > bytecode.length) return -1;
                int low = readInt(bytecode, baseOffset + 4);
                int high = readInt(bytecode, baseOffset + 8);
                if (low > high) return -1;
                return 1 + padding + 12 + (high - low + 1) * 4;
            }
            case 0xAB: {
                int padding = (4 - ((offset + 1) % 4)) % 4;
                int baseOffset = offset + 1 + padding;
                if (baseOffset + 8 > bytecode.length) return -1;
                int npairs = readInt(bytecode, baseOffset + 4);
                if (npairs < 0) return -1;
                return 1 + padding + 8 + npairs * 8;
            }
            case 0xAC: case 0xAD: case 0xAE: case 0xAF:
            case 0xB0: case 0xB1:
                return 1;
            case 0xB2: case 0xB3: case 0xB4: case 0xB5:
                return 3;
            case 0xB6: case 0xB7: case 0xB8:
                return 3;
            case 0xB9:
                return 5;
            case 0xBA:
                return 5;
            case 0xBB:
                return 3;
            case 0xBC:
                return 2;
            case 0xBD:
                return 3;
            case 0xBE: case 0xBF:
                return 1;
            case 0xC0: case 0xC1:
                return 3;
            case 0xC2: case 0xC3:
                return 1;
            case 0xC4: {
                if (offset + 1 >= bytecode.length) return -1;
                int wideOpcode = Byte.toUnsignedInt(bytecode[offset + 1]);
                if (wideOpcode == 0x84) {
                    return 6;
                } else {
                    return 4;
                }
            }
            case 0xC5:
                return 4;
            case 0xC6: case 0xC7:
                return 3;
            case 0xC8: case 0xC9:
                return 5;
            default:
                return -1;
        }
    }

    private int readInt(byte[] bytecode, int offset) {
        return ((bytecode[offset] & 0xFF) << 24) |
               ((bytecode[offset + 1] & 0xFF) << 16) |
               ((bytecode[offset + 2] & 0xFF) << 8) |
               (bytecode[offset + 3] & 0xFF);
    }
}
