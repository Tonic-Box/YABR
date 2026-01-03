package com.tonic.analysis.verifier.structural;

import com.tonic.analysis.verifier.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.utill.Opcode;
import java.util.HashSet;
import java.util.Set;

import static com.tonic.utill.Opcode.*;

public class StructuralVerifier {
    private final OperandValidator operandValidator;

    private static final Set<Integer> RESERVED_OPCODES;
    static {
        Set<Integer> reserved = new HashSet<>();
        reserved.add(INVOKEDYNAMIC.getCode());
        for (int i = BREAKPOINT.getCode(); i <= 0xFE; i++) {
            reserved.add(i);
        }
        RESERVED_OPCODES = Set.copyOf(reserved);
    }

    private static final Set<Integer> WIDEABLE_OPCODES = Set.of(
            ILOAD.getCode(), LLOAD.getCode(), FLOAD.getCode(), DLOAD.getCode(), ALOAD.getCode(),
            ISTORE.getCode(), LSTORE.getCode(), FSTORE.getCode(), DSTORE.getCode(), ASTORE.getCode(),
            IINC.getCode(), RET.getCode()
    );

    public StructuralVerifier(ClassFile classFile) {
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

            if (opcode > JSR_W.getCode() || RESERVED_OPCODES.contains(opcode)) {
                if (opcode != INVOKEDYNAMIC.getCode()) {
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

            if (opcode == WIDE.getCode()) {
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
                } else {
                    collector.addError(new VerificationError(
                            VerificationErrorType.INVALID_BRANCH_TARGET,
                            offset,
                            "Branch target " + target + " is outside code bounds [0, " + bytecode.length + ")"
                    ));
                }
            }

            if (opcode == TABLESWITCH.getCode()) {
                verifySwitchTargets(offset, bytecode, boundaries, collector, true);
            } else if (opcode == LOOKUPSWITCH.getCode()) {
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

        if (!isTerminatingInstruction(lastOpcode)) {
            collector.addWarning(new VerificationError(
                    VerificationErrorType.INSTRUCTION_FALLS_OFF_END,
                    lastOffset,
                    "Method does not end with a terminating instruction (opcode 0x" +
                        Integer.toHexString(lastOpcode) + ")",
                    VerificationError.Severity.WARNING
            ));
        }
    }

    private boolean isTerminatingInstruction(int opcode) {
        return (opcode >= IRETURN.getCode() && opcode <= RETURN_.getCode()) ||
               opcode == ATHROW.getCode() ||
               opcode == GOTO.getCode() ||
               opcode == GOTO_W.getCode();
    }

    private boolean isBranchInstruction(int opcode) {
        return (opcode >= IFEQ.getCode() && opcode <= IF_ACMPNE.getCode()) ||
               opcode == GOTO.getCode() ||
               opcode == JSR.getCode() ||
               opcode == IFNULL.getCode() ||
               opcode == IFNONNULL.getCode() ||
               opcode == GOTO_W.getCode() ||
               opcode == JSR_W.getCode();
    }

    private int getBranchTarget(int opcode, int offset, byte[] bytecode) {
        if (opcode == GOTO_W.getCode() || opcode == JSR_W.getCode()) {
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
        if (opcode == TABLESWITCH.getCode()) {
            int padding = (4 - ((offset + 1) % 4)) % 4;
            int baseOffset = offset + 1 + padding;
            if (baseOffset + 12 > bytecode.length) return -1;
            int low = readInt(bytecode, baseOffset + 4);
            int high = readInt(bytecode, baseOffset + 8);
            if (low > high) return -1;
            return 1 + padding + 12 + (high - low + 1) * 4;
        }

        if (opcode == LOOKUPSWITCH.getCode()) {
            int padding = (4 - ((offset + 1) % 4)) % 4;
            int baseOffset = offset + 1 + padding;
            if (baseOffset + 8 > bytecode.length) return -1;
            int npairs = readInt(bytecode, baseOffset + 4);
            if (npairs < 0) return -1;
            return 1 + padding + 8 + npairs * 8;
        }

        if (opcode == WIDE.getCode()) {
            if (offset + 1 >= bytecode.length) return -1;
            int wideOpcode = Byte.toUnsignedInt(bytecode[offset + 1]);
            return wideOpcode == IINC.getCode() ? 6 : 4;
        }

        Opcode op = Opcode.fromCode(opcode);
        if (op == Opcode.UNKNOWN) {
            return -1;
        }
        return 1 + op.getOperandCount();
    }

    private int readInt(byte[] bytecode, int offset) {
        return ((bytecode[offset] & 0xFF) << 24) |
               ((bytecode[offset + 1] & 0xFF) << 16) |
               ((bytecode[offset + 2] & 0xFF) << 8) |
               (bytecode[offset + 3] & 0xFF);
    }
}
