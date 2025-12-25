package com.tonic.analysis.verifier.stackmap;

import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.analysis.frame.TypeState;
import com.tonic.analysis.frame.VerificationType;
import com.tonic.analysis.verifier.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.StackMapTableAttribute;
import com.tonic.parser.attribute.stack.*;

import java.util.*;

public class StackMapVerifier {
    private final ClassFile classFile;
    private final ClassPool classPool;
    private final VerifierConfig config;
    private final FrameComparator frameComparator;

    public StackMapVerifier(ClassFile classFile, ClassPool classPool, VerifierConfig config) {
        this.classFile = classFile;
        this.classPool = classPool;
        this.config = config;
        this.frameComparator = new FrameComparator();
    }

    public void verify(MethodEntry method, ErrorCollector collector) {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return;
        }

        StackMapTableAttribute stackMap = findStackMapTable(code);

        Set<Integer> branchTargets = findBranchTargets(code);
        if (branchTargets.isEmpty()) {
            return;
        }

        if (stackMap == null) {
            for (int target : branchTargets) {
                collector.addError(new VerificationError(
                        VerificationErrorType.MISSING_STACKMAP_FRAME,
                        target,
                        "No StackMapTable attribute but branch target exists at offset " + target
                ));
                if (collector.shouldStop()) return;
            }
            return;
        }

        Map<Integer, TypeState> declaredFrames = parseDeclaredFrames(stackMap, code);
        Map<Integer, TypeState> computedFrames = computeExpectedFrames(method);

        for (int target : branchTargets) {
            if (collector.shouldStop()) return;

            TypeState declared = declaredFrames.get(target);
            TypeState computed = computedFrames.get(target);

            if (declared == null) {
                collector.addError(new VerificationError(
                        VerificationErrorType.MISSING_STACKMAP_FRAME,
                        target,
                        "No StackMapTable frame at branch target offset " + target
                ));
                continue;
            }

            if (computed == null) {
                continue;
            }

            List<FrameComparator.FrameMismatch> mismatches = frameComparator.compare(declared, computed);
            for (FrameComparator.FrameMismatch mismatch : mismatches) {
                if (mismatch.isCritical()) {
                    collector.addError(new VerificationError(
                            VerificationErrorType.FRAME_TYPE_MISMATCH,
                            target,
                            mismatch.getDescription()
                    ));
                } else {
                    collector.addWarning(new VerificationError(
                            VerificationErrorType.FRAME_TYPE_MISMATCH,
                            target,
                            mismatch.getDescription(),
                            VerificationError.Severity.WARNING
                    ));
                }
            }
        }

        verifyFrameOffsets(stackMap, code, collector);
    }

    private StackMapTableAttribute findStackMapTable(CodeAttribute code) {
        if (code.getAttributes() == null) {
            return null;
        }

        for (Attribute attr : code.getAttributes()) {
            if (attr instanceof StackMapTableAttribute) {
                return (StackMapTableAttribute) attr;
            }
        }
        return null;
    }

    private Set<Integer> findBranchTargets(CodeAttribute code) {
        Set<Integer> targets = new TreeSet<>();
        byte[] bytecode = code.getCode();
        if (bytecode == null) {
            return targets;
        }

        int offset = 0;
        while (offset < bytecode.length) {
            int opcode = Byte.toUnsignedInt(bytecode[offset]);
            int length = getInstructionLength(opcode, offset, bytecode);
            if (length <= 0) {
                offset++;
                continue;
            }

            if (isBranchInstruction(opcode)) {
                int target = getBranchTarget(opcode, offset, bytecode);
                if (target >= 0 && target < bytecode.length && target != offset + length) {
                    targets.add(target);
                }
            }

            if (opcode == 0xAA) {
                addTableSwitchTargets(offset, bytecode, targets);
            } else if (opcode == 0xAB) {
                addLookupSwitchTargets(offset, bytecode, targets);
            }

            offset += length;
        }

        for (var entry : code.getExceptionTable()) {
            targets.add(entry.getHandlerPc());
        }

        return targets;
    }

    private Map<Integer, TypeState> parseDeclaredFrames(StackMapTableAttribute stackMap, CodeAttribute code) {
        Map<Integer, TypeState> frames = new HashMap<>();

        List<StackMapFrame> entries = stackMap.getFrames();
        if (entries == null || entries.isEmpty()) {
            return frames;
        }

        ConstPool constPool = classFile != null ? classFile.getConstPool() : null;
        int currentOffset = -1;
        List<VerificationType> currentLocals = new ArrayList<>();
        List<VerificationType> currentStack = new ArrayList<>();

        for (StackMapFrame frame : entries) {
            int offsetDelta = getOffsetDelta(frame);
            if (currentOffset == -1) {
                currentOffset = offsetDelta;
            } else {
                currentOffset = currentOffset + offsetDelta + 1;
            }

            updateFrameState(frame, currentLocals, currentStack, constPool);

            TypeState state = new TypeState(
                    new ArrayList<>(currentLocals),
                    new ArrayList<>(currentStack)
            );
            frames.put(currentOffset, state);
        }

        return frames;
    }

    private int getOffsetDelta(StackMapFrame frame) {
        int frameType = frame.getFrameType();
        if (frame instanceof SameFrame) {
            return frameType;
        } else if (frame instanceof SameLocals1StackItemFrame) {
            return frameType - 64;
        } else if (frame instanceof SameLocals1StackItemFrameExtended) {
            return ((SameLocals1StackItemFrameExtended) frame).getOffsetDelta();
        } else if (frame instanceof ChopFrame) {
            return ((ChopFrame) frame).getOffsetDelta();
        } else if (frame instanceof SameFrameExtended) {
            return ((SameFrameExtended) frame).getOffsetDelta();
        } else if (frame instanceof AppendFrame) {
            return ((AppendFrame) frame).getOffsetDelta();
        } else if (frame instanceof FullFrame) {
            return ((FullFrame) frame).getOffsetDelta();
        }
        return 0;
    }

    private void updateFrameState(StackMapFrame frame, List<VerificationType> locals,
                                  List<VerificationType> stack, ConstPool constPool) {
        if (frame instanceof SameFrame) {
            stack.clear();
        } else if (frame instanceof SameLocals1StackItemFrame) {
            SameLocals1StackItemFrame slsif = (SameLocals1StackItemFrame) frame;
            stack.clear();
            stack.add(convertVerificationTypeInfo(slsif.getStack(), constPool));
        } else if (frame instanceof SameLocals1StackItemFrameExtended) {
            SameLocals1StackItemFrameExtended ext = (SameLocals1StackItemFrameExtended) frame;
            stack.clear();
            stack.add(convertVerificationTypeInfo(ext.getStack(), constPool));
        } else if (frame instanceof ChopFrame) {
            ChopFrame chop = (ChopFrame) frame;
            int chopCount = 251 - chop.getFrameType();
            for (int i = 0; i < chopCount && !locals.isEmpty(); i++) {
                locals.remove(locals.size() - 1);
            }
            stack.clear();
        } else if (frame instanceof SameFrameExtended) {
            stack.clear();
        } else if (frame instanceof AppendFrame) {
            AppendFrame append = (AppendFrame) frame;
            for (VerificationTypeInfo info : append.getLocals()) {
                locals.add(convertVerificationTypeInfo(info, constPool));
            }
            stack.clear();
        } else if (frame instanceof FullFrame) {
            FullFrame full = (FullFrame) frame;
            locals.clear();
            for (VerificationTypeInfo info : full.getLocals()) {
                locals.add(convertVerificationTypeInfo(info, constPool));
            }
            stack.clear();
            for (VerificationTypeInfo info : full.getStack()) {
                stack.add(convertVerificationTypeInfo(info, constPool));
            }
        }
    }

    private VerificationType convertVerificationTypeInfo(VerificationTypeInfo info, ConstPool constPool) {
        if (info == null) {
            return VerificationType.TOP;
        }

        int tag = info.getTag();
        switch (tag) {
            case 0: return VerificationType.TOP;
            case 1: return VerificationType.INTEGER;
            case 2: return VerificationType.FLOAT;
            case 3: return VerificationType.DOUBLE;
            case 4: return VerificationType.LONG;
            case 5: return VerificationType.NULL;
            case 6: return VerificationType.UNINITIALIZED_THIS;
            case 7:
                Object cpInfo = info.getInfo();
                int cpIndex = cpInfo instanceof Integer ? (Integer) cpInfo : 0;
                return VerificationType.object(cpIndex);
            case 8:
                Object offsetInfo = info.getInfo();
                int offsetVal = offsetInfo instanceof Integer ? (Integer) offsetInfo : 0;
                return VerificationType.uninitialized(offsetVal);
            default:
                return VerificationType.TOP;
        }
    }

    private Map<Integer, TypeState> computeExpectedFrames(MethodEntry method) {
        ConstPool constPool = classFile != null ? classFile.getConstPool() : null;
        if (constPool == null) {
            return Collections.emptyMap();
        }

        try {
            FrameGenerator generator = new FrameGenerator(constPool);
            var frames = generator.computeFrames(method);

            Map<Integer, TypeState> result = new HashMap<>();
            int currentOffset = -1;
            for (var frame : frames) {
                int offsetDelta = getOffsetDelta(frame);
                if (currentOffset == -1) {
                    currentOffset = offsetDelta;
                } else {
                    currentOffset = currentOffset + offsetDelta + 1;
                }

                if (frame instanceof FullFrame) {
                    FullFrame full = (FullFrame) frame;
                    List<VerificationType> locals = new ArrayList<>();
                    List<VerificationType> stack = new ArrayList<>();

                    for (VerificationTypeInfo info : full.getLocals()) {
                        locals.add(convertVerificationTypeInfo(info, constPool));
                    }
                    for (VerificationTypeInfo info : full.getStack()) {
                        stack.add(convertVerificationTypeInfo(info, constPool));
                    }

                    result.put(currentOffset, new TypeState(locals, stack));
                }
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private void verifyFrameOffsets(StackMapTableAttribute stackMap, CodeAttribute code, ErrorCollector collector) {
        byte[] bytecode = code.getCode();
        if (bytecode == null) return;

        Set<Integer> boundaries = computeInstructionBoundaries(bytecode);

        List<StackMapFrame> entries = stackMap.getFrames();
        if (entries == null) return;

        int currentOffset = -1;
        for (StackMapFrame frame : entries) {
            int offsetDelta = getOffsetDelta(frame);
            if (currentOffset == -1) {
                currentOffset = offsetDelta;
            } else {
                currentOffset = currentOffset + offsetDelta + 1;
            }

            if (currentOffset < 0 || currentOffset >= bytecode.length) {
                collector.addError(new VerificationError(
                        VerificationErrorType.INVALID_FRAME_OFFSET,
                        currentOffset,
                        "StackMapTable frame offset " + currentOffset + " is outside code bounds"
                ));
            } else if (!boundaries.contains(currentOffset)) {
                collector.addError(new VerificationError(
                        VerificationErrorType.INVALID_FRAME_OFFSET,
                        currentOffset,
                        "StackMapTable frame offset " + currentOffset + " is not at instruction boundary"
                ));
            }
        }
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

    private boolean isBranchInstruction(int opcode) {
        return (opcode >= 0x99 && opcode <= 0xA6) ||
               opcode == 0xA7 || opcode == 0xA8 ||
               opcode == 0xC6 || opcode == 0xC7 ||
               opcode == 0xC8 || opcode == 0xC9;
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

    private void addTableSwitchTargets(int offset, byte[] bytecode, Set<Integer> targets) {
        int padding = (4 - ((offset + 1) % 4)) % 4;
        int baseOffset = offset + 1 + padding;
        if (baseOffset + 12 > bytecode.length) return;

        int defaultOffset = readInt(bytecode, baseOffset);
        targets.add(offset + defaultOffset);

        int low = readInt(bytecode, baseOffset + 4);
        int high = readInt(bytecode, baseOffset + 8);
        if (low > high) return;

        int jumpTableStart = baseOffset + 12;
        for (int i = 0; i <= high - low; i++) {
            if (jumpTableStart + (i + 1) * 4 > bytecode.length) break;
            int jumpOffset = readInt(bytecode, jumpTableStart + i * 4);
            targets.add(offset + jumpOffset);
        }
    }

    private void addLookupSwitchTargets(int offset, byte[] bytecode, Set<Integer> targets) {
        int padding = (4 - ((offset + 1) % 4)) % 4;
        int baseOffset = offset + 1 + padding;
        if (baseOffset + 8 > bytecode.length) return;

        int defaultOffset = readInt(bytecode, baseOffset);
        targets.add(offset + defaultOffset);

        int npairs = readInt(bytecode, baseOffset + 4);
        if (npairs < 0) return;

        int pairStart = baseOffset + 8;
        for (int i = 0; i < npairs; i++) {
            if (pairStart + (i + 1) * 8 > bytecode.length) break;
            int jumpOffset = readInt(bytecode, pairStart + i * 8 + 4);
            targets.add(offset + jumpOffset);
        }
    }

    private int getInstructionLength(int opcode, int offset, byte[] bytecode) {
        switch (opcode) {
            case 0x00: case 0x01: case 0x02: case 0x03: case 0x04:
            case 0x05: case 0x06: case 0x07: case 0x08: case 0x09:
            case 0x0A: case 0x0B: case 0x0C: case 0x0D: case 0x0E:
            case 0x0F:
                return 1;
            case 0x10: return 2;
            case 0x11: return 3;
            case 0x12: return 2;
            case 0x13: case 0x14: return 3;
            case 0x15: case 0x16: case 0x17: case 0x18: case 0x19: return 2;
            case 0x1A: case 0x1B: case 0x1C: case 0x1D:
            case 0x1E: case 0x1F: case 0x20: case 0x21:
            case 0x22: case 0x23: case 0x24: case 0x25:
            case 0x26: case 0x27: case 0x28: case 0x29:
            case 0x2A: case 0x2B: case 0x2C: case 0x2D:
            case 0x2E: case 0x2F: case 0x30: case 0x31:
            case 0x32: case 0x33: case 0x34: case 0x35: return 1;
            case 0x36: case 0x37: case 0x38: case 0x39: case 0x3A: return 2;
            case 0x3B: case 0x3C: case 0x3D: case 0x3E:
            case 0x3F: case 0x40: case 0x41: case 0x42:
            case 0x43: case 0x44: case 0x45: case 0x46:
            case 0x47: case 0x48: case 0x49: case 0x4A:
            case 0x4B: case 0x4C: case 0x4D: case 0x4E:
            case 0x4F: case 0x50: case 0x51: case 0x52:
            case 0x53: case 0x54: case 0x55: case 0x56:
            case 0x57: case 0x58: case 0x59: case 0x5A:
            case 0x5B: case 0x5C: case 0x5D: case 0x5E:
            case 0x5F: return 1;
            case 0x60: case 0x61: case 0x62: case 0x63:
            case 0x64: case 0x65: case 0x66: case 0x67:
            case 0x68: case 0x69: case 0x6A: case 0x6B:
            case 0x6C: case 0x6D: case 0x6E: case 0x6F:
            case 0x70: case 0x71: case 0x72: case 0x73:
            case 0x74: case 0x75: case 0x76: case 0x77:
            case 0x78: case 0x79: case 0x7A: case 0x7B:
            case 0x7C: case 0x7D: case 0x7E: case 0x7F:
            case 0x80: case 0x81: case 0x82: case 0x83: return 1;
            case 0x84: return 3;
            case 0x85: case 0x86: case 0x87: case 0x88:
            case 0x89: case 0x8A: case 0x8B: case 0x8C:
            case 0x8D: case 0x8E: case 0x8F: case 0x90:
            case 0x91: case 0x92: case 0x93: case 0x94:
            case 0x95: case 0x96: case 0x97: case 0x98: return 1;
            case 0x99: case 0x9A: case 0x9B: case 0x9C:
            case 0x9D: case 0x9E: case 0x9F: case 0xA0:
            case 0xA1: case 0xA2: case 0xA3: case 0xA4:
            case 0xA5: case 0xA6: return 3;
            case 0xA7: case 0xA8: return 3;
            case 0xA9: return 2;
            case 0xAA: {
                int padding = (4 - ((offset + 1) % 4)) % 4;
                int baseOff = offset + 1 + padding;
                if (baseOff + 12 > bytecode.length) return -1;
                int low = readInt(bytecode, baseOff + 4);
                int high = readInt(bytecode, baseOff + 8);
                if (low > high) return -1;
                return 1 + padding + 12 + (high - low + 1) * 4;
            }
            case 0xAB: {
                int padding = (4 - ((offset + 1) % 4)) % 4;
                int baseOff = offset + 1 + padding;
                if (baseOff + 8 > bytecode.length) return -1;
                int npairs = readInt(bytecode, baseOff + 4);
                if (npairs < 0) return -1;
                return 1 + padding + 8 + npairs * 8;
            }
            case 0xAC: case 0xAD: case 0xAE: case 0xAF:
            case 0xB0: case 0xB1: return 1;
            case 0xB2: case 0xB3: case 0xB4: case 0xB5: return 3;
            case 0xB6: case 0xB7: case 0xB8: return 3;
            case 0xB9: case 0xBA: return 5;
            case 0xBB: return 3;
            case 0xBC: return 2;
            case 0xBD: return 3;
            case 0xBE: case 0xBF: return 1;
            case 0xC0: case 0xC1: return 3;
            case 0xC2: case 0xC3: return 1;
            case 0xC4: {
                if (offset + 1 >= bytecode.length) return -1;
                int wideOp = Byte.toUnsignedInt(bytecode[offset + 1]);
                return wideOp == 0x84 ? 6 : 4;
            }
            case 0xC5: return 4;
            case 0xC6: case 0xC7: return 3;
            case 0xC8: case 0xC9: return 5;
            default: return 1;
        }
    }

    private int readInt(byte[] bytecode, int offset) {
        return ((bytecode[offset] & 0xFF) << 24) |
               ((bytecode[offset + 1] & 0xFF) << 16) |
               ((bytecode[offset + 2] & 0xFF) << 8) |
               (bytecode[offset + 3] & 0xFF);
    }
}
