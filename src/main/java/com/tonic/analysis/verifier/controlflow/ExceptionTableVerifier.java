package com.tonic.analysis.verifier.controlflow;

import com.tonic.analysis.verifier.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;

import java.util.*;

public class ExceptionTableVerifier {
    private final ClassFile classFile;
    private final VerifierConfig config;

    public ExceptionTableVerifier(ClassFile classFile, VerifierConfig config) {
        this.classFile = classFile;
        this.config = config;
    }

    public void verify(MethodEntry method, ErrorCollector collector) {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return;
        }

        byte[] bytecode = code.getCode();
        if (bytecode == null) {
            return;
        }

        List<ExceptionTableEntry> exceptionTable = code.getExceptionTable();
        if (exceptionTable == null || exceptionTable.isEmpty()) {
            return;
        }

        Set<Integer> instructionBoundaries = computeInstructionBoundaries(bytecode);

        for (int i = 0; i < exceptionTable.size(); i++) {
            if (collector.shouldStop()) return;

            ExceptionTableEntry entry = exceptionTable.get(i);
            verifyExceptionHandler(entry, i, bytecode, instructionBoundaries, collector);
        }

        verifyOverlappingHandlers(exceptionTable, collector);
    }

    private void verifyExceptionHandler(ExceptionTableEntry entry, int index, byte[] bytecode,
                                        Set<Integer> boundaries, ErrorCollector collector) {
        int startPc = entry.getStartPc();
        int endPc = entry.getEndPc();
        int handlerPc = entry.getHandlerPc();
        int catchType = entry.getCatchType();

        if (startPc >= endPc) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_EXCEPTION_HANDLER,
                    startPc,
                    "Exception handler " + index + ": start_pc (" + startPc +
                    ") must be less than end_pc (" + endPc + ")"
            ));
        }

        if (startPc < 0 || startPc >= bytecode.length) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_EXCEPTION_HANDLER,
                    startPc,
                    "Exception handler " + index + ": start_pc (" + startPc +
                    ") is outside code bounds [0, " + bytecode.length + ")"
            ));
        } else if (!boundaries.contains(startPc)) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_EXCEPTION_HANDLER,
                    startPc,
                    "Exception handler " + index + ": start_pc (" + startPc +
                    ") is not at an instruction boundary"
            ));
        }

        if (endPc < 0 || endPc > bytecode.length) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_EXCEPTION_HANDLER,
                    endPc,
                    "Exception handler " + index + ": end_pc (" + endPc +
                    ") is outside code bounds [0, " + bytecode.length + "]"
            ));
        } else if (endPc < bytecode.length && !boundaries.contains(endPc)) {
            collector.addWarning(new VerificationError(
                    VerificationErrorType.INVALID_EXCEPTION_HANDLER,
                    endPc,
                    "Exception handler " + index + ": end_pc (" + endPc +
                    ") is not at an instruction boundary",
                    VerificationError.Severity.WARNING
            ));
        }

        if (handlerPc < 0 || handlerPc >= bytecode.length) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_EXCEPTION_HANDLER,
                    handlerPc,
                    "Exception handler " + index + ": handler_pc (" + handlerPc +
                    ") is outside code bounds [0, " + bytecode.length + ")"
            ));
        } else if (!boundaries.contains(handlerPc)) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_EXCEPTION_HANDLER,
                    handlerPc,
                    "Exception handler " + index + ": handler_pc (" + handlerPc +
                    ") is not at an instruction boundary"
            ));
        }

        if (catchType != 0) {
            verifyCatchType(catchType, index, collector);
        }
    }

    private void verifyCatchType(int catchType, int handlerIndex, ErrorCollector collector) {
        if (classFile == null) return;

        ConstPool constPool = classFile.getConstPool();
        if (constPool == null) return;

        if (catchType <= 0) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CATCH_TYPE,
                    -1,
                    "Exception handler " + handlerIndex + ": catch_type index " + catchType +
                    " must be greater than 0"
            ));
            return;
        }

        Item<?> item = constPool.getItem(catchType);
        if (item == null) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CATCH_TYPE,
                    -1,
                    "Exception handler " + handlerIndex + ": catch_type index " + catchType +
                    " has no entry in constant pool"
            ));
            return;
        }

        if (!(item instanceof ClassRefItem)) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CATCH_TYPE,
                    -1,
                    "Exception handler " + handlerIndex + ": catch_type index " + catchType +
                    " is not a Class entry (found " + item.getClass().getSimpleName() + ")"
            ));
        }
    }

    private void verifyOverlappingHandlers(List<ExceptionTableEntry> exceptionTable, ErrorCollector collector) {
        Map<Integer, List<Integer>> catchTypeRanges = new HashMap<>();

        for (int i = 0; i < exceptionTable.size(); i++) {
            ExceptionTableEntry entry = exceptionTable.get(i);
            int catchType = entry.getCatchType();

            if (!catchTypeRanges.containsKey(catchType)) {
                catchTypeRanges.put(catchType, new ArrayList<>());
            }
            catchTypeRanges.get(catchType).add(i);
        }

        for (Map.Entry<Integer, List<Integer>> mapEntry : catchTypeRanges.entrySet()) {
            List<Integer> indices = mapEntry.getValue();
            if (indices.size() < 2) continue;

            for (int i = 0; i < indices.size(); i++) {
                for (int j = i + 1; j < indices.size(); j++) {
                    ExceptionTableEntry e1 = exceptionTable.get(indices.get(i));
                    ExceptionTableEntry e2 = exceptionTable.get(indices.get(j));

                    if (rangesOverlap(e1.getStartPc(), e1.getEndPc(), e2.getStartPc(), e2.getEndPc())) {
                        collector.addWarning(new VerificationError(
                                VerificationErrorType.EXCEPTION_HANDLER_OVERLAP,
                                e1.getStartPc(),
                                "Exception handlers " + indices.get(i) + " and " + indices.get(j) +
                                " have overlapping ranges for same catch type " + mapEntry.getKey(),
                                VerificationError.Severity.WARNING
                        ));
                    }
                }
            }
        }
    }

    private boolean rangesOverlap(int start1, int end1, int start2, int end2) {
        return start1 < end2 && start2 < end1;
    }

    private Set<Integer> computeInstructionBoundaries(byte[] bytecode) {
        Set<Integer> boundaries = new HashSet<>();
        int offset = 0;

        while (offset < bytecode.length) {
            boundaries.add(offset);
            int opcode = Byte.toUnsignedInt(bytecode[offset]);
            int length = getInstructionLength(opcode, offset, bytecode);
            if (length <= 0) {
                offset++;
            } else {
                offset += length;
            }
        }

        return boundaries;
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
                return 1;
        }
    }

    private int readInt(byte[] bytecode, int offset) {
        return ((bytecode[offset] & 0xFF) << 24) |
               ((bytecode[offset + 1] & 0xFF) << 16) |
               ((bytecode[offset + 2] & 0xFF) << 8) |
               (bytecode[offset + 3] & 0xFF);
    }
}
