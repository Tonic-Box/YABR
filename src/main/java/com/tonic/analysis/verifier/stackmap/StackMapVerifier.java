package com.tonic.analysis.verifier.stackmap;

import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.analysis.frame.TypeState;
import com.tonic.analysis.frame.VerificationType;
import com.tonic.analysis.verifier.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.StackMapTableAttribute;
import com.tonic.parser.attribute.stack.*;
import com.tonic.utill.Opcode;

import java.util.*;

import static com.tonic.utill.Opcode.*;

public class StackMapVerifier {
    private final ClassFile classFile;
    private final FrameComparator frameComparator;

    public StackMapVerifier(ClassFile classFile) {
        this.classFile = classFile;
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

        Map<Integer, TypeState> declaredFrames = parseDeclaredFrames(stackMap);
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

            if (opcode == TABLESWITCH.getCode()) {
                addTableSwitchTargets(offset, bytecode, targets);
            } else if (opcode == LOOKUPSWITCH.getCode()) {
                addLookupSwitchTargets(offset, bytecode, targets);
            }

            offset += length;
        }

        for (var entry : code.getExceptionTable()) {
            targets.add(entry.getHandlerPc());
        }

        return targets;
    }

    private Map<Integer, TypeState> parseDeclaredFrames(StackMapTableAttribute stackMap) {
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
        return (opcode >= IFEQ.getCode() && opcode <= IF_ACMPNE.getCode()) ||
               opcode == GOTO.getCode() || opcode == JSR.getCode() ||
               opcode == IFNULL.getCode() || opcode == IFNONNULL.getCode() ||
               opcode == GOTO_W.getCode() || opcode == JSR_W.getCode();
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
        if (opcode == TABLESWITCH.getCode()) {
            int padding = (4 - ((offset + 1) % 4)) % 4;
            int baseOff = offset + 1 + padding;
            if (baseOff + 12 > bytecode.length) return -1;
            int low = readInt(bytecode, baseOff + 4);
            int high = readInt(bytecode, baseOff + 8);
            if (low > high) return -1;
            return 1 + padding + 12 + (high - low + 1) * 4;
        }

        if (opcode == LOOKUPSWITCH.getCode()) {
            int padding = (4 - ((offset + 1) % 4)) % 4;
            int baseOff = offset + 1 + padding;
            if (baseOff + 8 > bytecode.length) return -1;
            int npairs = readInt(bytecode, baseOff + 4);
            if (npairs < 0) return -1;
            return 1 + padding + 8 + npairs * 8;
        }

        if (opcode == WIDE.getCode()) {
            if (offset + 1 >= bytecode.length) return -1;
            int wideOp = Byte.toUnsignedInt(bytecode[offset + 1]);
            return wideOp == IINC.getCode() ? 6 : 4;
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
