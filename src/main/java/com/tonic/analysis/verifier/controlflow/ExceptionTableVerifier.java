package com.tonic.analysis.verifier.controlflow;

import com.tonic.analysis.verifier.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.utill.Opcode;

import java.util.*;

import static com.tonic.utill.Opcode.*;

public class ExceptionTableVerifier {
    private final ClassFile classFile;

    public ExceptionTableVerifier(ClassFile classFile) {
        this.classFile = classFile;
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
            return 1;
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
