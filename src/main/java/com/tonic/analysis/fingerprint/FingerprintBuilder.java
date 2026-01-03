package com.tonic.analysis.fingerprint;

import com.tonic.analysis.fingerprint.features.Level0Features;
import com.tonic.analysis.fingerprint.features.Level1Features;
import com.tonic.analysis.fingerprint.features.Level2Features;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.parser.constpool.*;

import java.util.*;

import static com.tonic.utill.Opcode.*;

public class FingerprintBuilder {
    public FingerprintBuilder() {
    }

    public MethodFingerprint build(MethodEntry method, ClassFile classFile) {
        String methodId = buildMethodId(method, classFile);

        Level0Features l0 = extractLevel0(method, classFile);
        Level1Features l1 = extractLevel1(method, classFile);
        Level2Features l2 = extractLevel2(method, classFile);

        return new MethodFingerprint(methodId, l0, l1, l2);
    }

    private String buildMethodId(MethodEntry method, ClassFile classFile) {
        String className = classFile != null ? classFile.getClassName() : "unknown";
        return className + "." + method.getName() + method.getDesc();
    }

    private Level0Features extractLevel0(MethodEntry method, ClassFile classFile) {
        String desc = method.getDesc();
        String returnType = extractReturnType(desc);
        List<String> paramTypes = extractParamTypes(desc);

        CodeAttribute code = method.getCodeAttribute();
        int exceptionHandlers = 0;
        int monitorCount = 0;
        Set<String> externalCalls = new TreeSet<>();
        Set<String> fieldAccesses = new TreeSet<>();
        Set<String> instantiatedTypes = new TreeSet<>();

        if (code != null) {
            List<ExceptionTableEntry> exTable = code.getExceptionTable();
            if (exTable != null) {
                exceptionHandlers = exTable.size();
            }

            byte[] bytecode = code.getCode();
            if (bytecode != null && classFile != null) {
                ConstPool cp = classFile.getConstPool();
                extractLevel0FromBytecode(bytecode, cp, externalCalls, fieldAccesses,
                        instantiatedTypes, new int[]{0});
                monitorCount = countMonitorOps(bytecode);
            }
        }

        return new Level0Features(returnType, paramTypes.size(), paramTypes,
                exceptionHandlers, monitorCount,
                externalCalls, fieldAccesses, instantiatedTypes);
    }

    private void extractLevel0FromBytecode(byte[] bytecode, ConstPool cp,
                                           Set<String> externalCalls,
                                           Set<String> fieldAccesses,
                                           Set<String> instantiatedTypes,
                                           int[] monitorCountHolder) {
        int i = 0;
        while (i < bytecode.length) {
            int op = Byte.toUnsignedInt(bytecode[i]);
            int len = getInstructionLength(op, i, bytecode);
            if (len <= 0) {
                i++;
                continue;
            }

            switch (op) {
                case 0xB2: case 0xB3: case 0xB4: case 0xB5:
                    if (i + 2 < bytecode.length) {
                        int idx = readUnsignedShort(bytecode, i + 1);
                        String fieldRef = resolveFieldRef(cp, idx);
                        if (fieldRef != null) {
                            fieldAccesses.add(fieldRef);
                        }
                    }
                    break;

                case 0xB6: case 0xB7: case 0xB8:
                    if (i + 2 < bytecode.length) {
                        int idx = readUnsignedShort(bytecode, i + 1);
                        String methodRef = resolveMethodRef(cp, idx);
                        if (methodRef != null) {
                            externalCalls.add(methodRef);
                        }
                    }
                    break;

                case 0xB9:
                    if (i + 2 < bytecode.length) {
                        int idx = readUnsignedShort(bytecode, i + 1);
                        String methodRef = resolveInterfaceMethodRef(cp, idx);
                        if (methodRef != null) {
                            externalCalls.add(methodRef);
                        }
                    }
                    break;

                case 0xBB:
                    if (i + 2 < bytecode.length) {
                        int idx = readUnsignedShort(bytecode, i + 1);
                        String classRef = resolveClassRef(cp, idx);
                        if (classRef != null) {
                            instantiatedTypes.add(classRef);
                        }
                    }
                    break;
            }

            i += len;
        }
    }

    private int countMonitorOps(byte[] bytecode) {
        int count = 0;
        for (byte b : bytecode) {
            int op = Byte.toUnsignedInt(b);
            if (op == MONITORENTER.getCode() || op == MONITOREXIT.getCode()) {
                count++;
            }
        }
        return count;
    }

    private Level1Features extractLevel1(MethodEntry method, ClassFile classFile) {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return new Level1Features(0, 0, 0, new HashMap<>(), new HashMap<>(), new HashMap<>(), 0);
        }

        int loopCount = 0;
        int maxNesting = 0;
        int blockCount = 1;
        Map<String, Integer> branchTypes = new HashMap<>();
        Map<String, Integer> arithmeticOps = new HashMap<>();
        Map<String, Integer> invokeTypes = new HashMap<>();
        int arrayFlags = 0;

        byte[] bytecode = code.getCode();
        if (bytecode != null) {
            int i = 0;
            while (i < bytecode.length) {
                int op = Byte.toUnsignedInt(bytecode[i]);
                int len = getInstructionLength(op, i, bytecode);
                if (len <= 0) {
                    i++;
                    continue;
                }

                categorizeBranchOp(op, branchTypes);
                categorizeArithmeticOp(op, arithmeticOps);
                categorizeInvokeOp(op, invokeTypes);
                arrayFlags |= getArrayFlag(op);

                if (isBranchInstruction(op)) {
                    blockCount++;
                }

                i += len;
            }

            loopCount = estimateLoopCount(bytecode);
        }

        return new Level1Features(loopCount, maxNesting, blockCount,
                branchTypes, arithmeticOps, invokeTypes, arrayFlags);
    }

    private Level2Features extractLevel2(MethodEntry method, ClassFile classFile) {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return new Level2Features(new HashMap<>(), new HashMap<>(), 0, new HashMap<>(), new HashMap<>());
        }

        Map<String, Integer> opcodeNgrams = new HashMap<>();
        Map<String, Integer> cfgEdges = new HashMap<>();
        int dominanceDepth = 1;
        Map<String, Integer> terminatorTypes = new HashMap<>();
        Map<String, Integer> instructionTypes = new HashMap<>();

        byte[] bytecode = code.getCode();
        if (bytecode != null) {
            String prevCategory = null;
            int i = 0;
            while (i < bytecode.length) {
                int op = Byte.toUnsignedInt(bytecode[i]);
                int len = getInstructionLength(op, i, bytecode);
                if (len <= 0) {
                    i++;
                    continue;
                }

                String category = Level2Features.getOpcodeCategory(op);
                instructionTypes.merge(category, 1, Integer::sum);

                if (prevCategory != null) {
                    String ngram = prevCategory + "->" + category;
                    opcodeNgrams.merge(ngram, 1, Integer::sum);
                }
                prevCategory = category;

                if (isTerminator(op)) {
                    String termType = getTerminatorType(op);
                    terminatorTypes.merge(termType, 1, Integer::sum);
                }

                i += len;
            }
        }

        return new Level2Features(opcodeNgrams, cfgEdges, dominanceDepth,
                terminatorTypes, instructionTypes);
    }

    private String extractReturnType(String desc) {
        int idx = desc.lastIndexOf(')');
        return idx >= 0 ? desc.substring(idx + 1) : "V";
    }

    private List<String> extractParamTypes(String desc) {
        List<String> types = new ArrayList<>();
        int i = 1;
        while (i < desc.length() && desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                if (end < 0) break;
                types.add(desc.substring(i, end + 1));
                i = end + 1;
            } else if (c == '[') {
                int start = i;
                while (i < desc.length() && desc.charAt(i) == '[') i++;
                if (i >= desc.length()) break;
                if (desc.charAt(i) == 'L') {
                    int end = desc.indexOf(';', i);
                    if (end < 0) break;
                    types.add(desc.substring(start, end + 1));
                    i = end + 1;
                } else {
                    types.add(desc.substring(start, i + 1));
                    i++;
                }
            } else {
                types.add(String.valueOf(c));
                i++;
            }
        }
        return types;
    }

    private void categorizeBranchOp(int op, Map<String, Integer> branches) {
        if (op >= IFEQ.getCode() && op <= IF_ACMPNE.getCode()) {
            branches.merge("conditional", 1, Integer::sum);
        } else if (op == GOTO.getCode() || op == GOTO_W.getCode()) {
            branches.merge("goto", 1, Integer::sum);
        } else if (op == TABLESWITCH.getCode() || op == LOOKUPSWITCH.getCode()) {
            branches.merge("switch", 1, Integer::sum);
        }
    }

    private void categorizeArithmeticOp(int op, Map<String, Integer> arithmetic) {
        if ((op >= IADD.getCode() && op <= LXOR.getCode()) || (op >= INEG.getCode() && op <= DNEG.getCode())) {
            arithmetic.merge("math", 1, Integer::sum);
        }
    }

    private void categorizeInvokeOp(int op, Map<String, Integer> invokes) {
        switch (op) {
            case 0xB6: invokes.merge("virtual", 1, Integer::sum); break;
            case 0xB7: invokes.merge("special", 1, Integer::sum); break;
            case 0xB8: invokes.merge("static", 1, Integer::sum); break;
            case 0xB9: invokes.merge("interface", 1, Integer::sum); break;
            case 0xBA: invokes.merge("dynamic", 1, Integer::sum); break;
        }
    }

    private int getArrayFlag(int op) {
        if (op >= IALOAD.getCode() && op <= SALOAD.getCode()) return Level1Features.ARRAY_LOAD;
        if (op >= IASTORE.getCode() && op <= SASTORE.getCode()) return Level1Features.ARRAY_STORE;
        if (op == NEWARRAY.getCode() || op == ANEWARRAY.getCode() || op == MULTIANEWARRAY.getCode()) return Level1Features.ARRAY_NEW;
        if (op == ARRAYLENGTH.getCode()) return Level1Features.ARRAY_LENGTH;
        return 0;
    }

    private boolean isBranchInstruction(int op) {
        return (op >= IFEQ.getCode() && op <= IF_ACMPNE.getCode()) || op == GOTO.getCode() || op == JSR.getCode() ||
               op == TABLESWITCH.getCode() || op == LOOKUPSWITCH.getCode() || op == GOTO_W.getCode() || op == JSR_W.getCode();
    }

    private boolean isTerminator(int op) {
        return (op >= IRETURN.getCode() && op <= RETURN_.getCode()) || op == ATHROW.getCode() ||
               op == GOTO.getCode() || op == GOTO_W.getCode();
    }

    private String getTerminatorType(int op) {
        if (op >= IRETURN.getCode() && op <= ARETURN.getCode()) return "return_value";
        if (op == RETURN_.getCode()) return "return_void";
        if (op == ATHROW.getCode()) return "athrow";
        if (op == GOTO.getCode() || op == GOTO_W.getCode()) return "goto";
        return "other";
    }

    private int estimateLoopCount(byte[] bytecode) {
        int backwardJumps = 0;
        int i = 0;
        while (i < bytecode.length) {
            int op = Byte.toUnsignedInt(bytecode[i]);
            int len = getInstructionLength(op, i, bytecode);
            if (len <= 0) {
                i++;
                continue;
            }

            if ((op >= IFEQ.getCode() && op <= IF_ACMPNE.getCode()) || op == GOTO.getCode()) {
                if (i + 2 < bytecode.length) {
                    short offset = (short) readUnsignedShort(bytecode, i + 1);
                    if (offset < 0) {
                        backwardJumps++;
                    }
                }
            } else if (op == GOTO_W.getCode()) {
                if (i + 4 < bytecode.length) {
                    int offset = readInt(bytecode, i + 1);
                    if (offset < 0) {
                        backwardJumps++;
                    }
                }
            }

            i += len;
        }
        return backwardJumps;
    }

    private String resolveFieldRef(ConstPool cp, int idx) {
        if (cp == null) return null;
        Item<?> item = cp.getItem(idx);
        if (item instanceof FieldRefItem) {
            FieldRefItem ref = (FieldRefItem) item;
            return ref.getClassName() + "." + ref.getName();
        }
        return null;
    }

    private String resolveMethodRef(ConstPool cp, int idx) {
        if (cp == null) return null;
        Item<?> item = cp.getItem(idx);
        if (item instanceof MethodRefItem) {
            MethodRefItem ref = (MethodRefItem) item;
            return ref.getClassName() + "." + ref.getName() + ref.getDescriptor();
        }
        return null;
    }

    private String resolveInterfaceMethodRef(ConstPool cp, int idx) {
        if (cp == null) return null;
        Item<?> item = cp.getItem(idx);
        if (item instanceof InterfaceRefItem) {
            InterfaceRefItem ref = (InterfaceRefItem) item;
            return ref.getOwner() + "." + ref.getName() + ref.getDescriptor();
        }
        return null;
    }

    private String resolveClassRef(ConstPool cp, int idx) {
        if (cp == null) return null;
        Item<?> item = cp.getItem(idx);
        if (item instanceof ClassRefItem) {
            return ((ClassRefItem) item).getClassName();
        }
        return null;
    }

    private int readUnsignedShort(byte[] bytecode, int offset) {
        return ((bytecode[offset] & 0xFF) << 8) | (bytecode[offset + 1] & 0xFF);
    }

    private int readInt(byte[] bytecode, int offset) {
        return ((bytecode[offset] & 0xFF) << 24) |
               ((bytecode[offset + 1] & 0xFF) << 16) |
               ((bytecode[offset + 2] & 0xFF) << 8) |
               (bytecode[offset + 3] & 0xFF);
    }

    private int getInstructionLength(int opcode, int offset, byte[] bytecode) {
        switch (opcode) {
            case 0x00: case 0x01: case 0x02: case 0x03: case 0x04:
            case 0x05: case 0x06: case 0x07: case 0x08: case 0x09:
            case 0x0A: case 0x0B: case 0x0C: case 0x0D: case 0x0E:
            case 0x0F: return 1;
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
            case 0xA7: return 3;
            case 0xA8: return 3;
            case 0xA9: return 2;
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
            case 0xB0: case 0xB1: return 1;
            case 0xB2: case 0xB3: case 0xB4: case 0xB5: return 3;
            case 0xB6: case 0xB7: case 0xB8: return 3;
            case 0xB9: return 5;
            case 0xBA: return 5;
            case 0xBB: return 3;
            case 0xBC: return 2;
            case 0xBD: return 3;
            case 0xBE: case 0xBF: return 1;
            case 0xC0: case 0xC1: return 3;
            case 0xC2: case 0xC3: return 1;
            case 0xC4: {
                if (offset + 1 >= bytecode.length) return -1;
                int wideOpcode = Byte.toUnsignedInt(bytecode[offset + 1]);
                if (wideOpcode == IINC.getCode()) return 6;
                return 4;
            }
            case 0xC5: return 4;
            case 0xC6: case 0xC7: return 3;
            case 0xC8: case 0xC9: return 5;
            default: return 1;
        }
    }
}
