package com.tonic.analysis;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.FieldRefItem;
import com.tonic.parser.constpool.MethodRefItem;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.utill.Logger;
import com.tonic.utill.Opcode;

public class CodePrinter {

    /**
     * Disassembles the given bytecode into a human-readable format.
     *
     * @param code      the method's bytecode
     * @param constPool the constant pool for resolving references
     * @return a string representing the disassembled bytecode
     */
    public static String prettyPrintCode(byte[] code, ConstPool constPool) {
        StringBuilder sb = new StringBuilder();
        int pc = 0;

        while (pc < code.length) {
            // 1) Read the opcode.
            int opcodeValue = Byte.toUnsignedInt(code[pc]);
            Opcode opcode = Opcode.fromCode(opcodeValue);
            String mnemonic = opcode.getMnemonic();

            // Log entering the opcode
            Logger.info("DEBUG: Decoding opcode " + mnemonic + " at pc=" + pc);

            // Print offset and mnemonic
            sb.append(String.format("%04d: %-20s", pc, mnemonic));

            // Advance pc by 1 for the opcode byte
            pc += 1;

            switch (opcode) {
                //----------------------------------------------------------------------
                // (A) No-operand opcodes
                //----------------------------------------------------------------------
                case NOP:
                case ACONST_NULL:
                case ICONST_M1:
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5:
                case LCONST_0:
                case LCONST_1:
                case FCONST_0:
                case FCONST_1:
                case FCONST_2:
                case DCONST_0:
                case DCONST_1:
                case IALOAD:
                case LALOAD:
                case FALOAD:
                case DALOAD:
                case AALOAD:
                case BALOAD:
                case CALOAD:
                case SALOAD:
                case POP:
                case POP2:
                case DUP:
                case DUP_X1:
                case DUP_X2:
                case DUP2:
                case DUP2_X1:
                case DUP2_X2:
                case SWAP:
                case IADD:
                case LADD:
                case FADD:
                case DADD:
                case ISUB:
                case LSUB:
                case FSUB:
                case DSUB:
                case IMUL:
                case LMUL:
                case FMUL:
                case DMUL:
                case IDIV:
                case LDIV:
                case FDIV:
                case DDIV:
                case IREM:
                case LREM:
                case FREM:
                case DREM:
                case INEG:
                case LNEG:
                case FNEG:
                case DNEG:
                case ISHL:
                case LSHL:
                case ISHR:
                case LSHR:
                case IUSHR:
                case LUSHR:
                case IAND:
                case LAND:
                case IOR:
                case LOR:
                case IXOR:
                case LXOR:
                case I2L:
                case I2F:
                case I2D:
                case L2I:
                case L2F:
                case L2D:
                case F2I:
                case F2L:
                case F2D:
                case D2I:
                case D2L:
                case D2F:
                case I2B:
                case I2C:
                case I2S:
                case LCMP:
                case FCMPL:
                case FCMPG:
                case DCMPL:
                case DCMPG:
                case ILOAD_0:
                case ILOAD_1:
                case ILOAD_2:
                case ILOAD_3:
                case LLOAD_0:
                case LLOAD_1:
                case LLOAD_2:
                case LLOAD_3:
                case FLOAD_0:
                case FLOAD_1:
                case FLOAD_2:
                case FLOAD_3:
                case DLOAD_0:
                case DLOAD_1:
                case DLOAD_2:
                case DLOAD_3:
                case ALOAD_0:
                case ALOAD_1:
                case ALOAD_2:
                case ALOAD_3:
                case ISTORE_0:
                case ISTORE_1:
                case ISTORE_2:
                case ISTORE_3:
                case LSTORE_0:
                case LSTORE_1:
                case LSTORE_2:
                case LSTORE_3:
                case FSTORE_0:
                case FSTORE_1:
                case FSTORE_2:
                case FSTORE_3:
                case DSTORE_0:
                case DSTORE_1:
                case DSTORE_2:
                case DSTORE_3:
                case ASTORE_0:
                case ASTORE_1:
                case ASTORE_2:
                case ASTORE_3:
                case IASTORE:
                case LASTORE:
                case FASTORE:
                case DASTORE:
                case AASTORE:
                case BASTORE:
                case CASTORE:
                case SASTORE:
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                case ARETURN:
                case RETURN_:
                case ARRAYLENGTH:
                case ATHROW:
                case MONITORENTER:
                case MONITOREXIT:
                case BREAKPOINT:
                    // No additional bytes to read.
                    break;

                //----------------------------------------------------------------------
                // (B) Single-byte immediate (e.g. BIPUSH).
                //----------------------------------------------------------------------
                case BIPUSH:
                    if (pc >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int byteValue = code[pc++];
                    sb.append(byteValue);
                    break;

                //----------------------------------------------------------------------
                // (C) Single-byte local index instructions (iload, etc.)
                //----------------------------------------------------------------------
                case ILOAD:
                case LLOAD:
                case FLOAD:
                case DLOAD:
                case ALOAD:
                case ISTORE:
                case LSTORE:
                case FSTORE:
                case DSTORE:
                case ASTORE:
                    if (pc >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int localIndex = Byte.toUnsignedInt(code[pc++]);
                    sb.append(localIndex);
                    break;

                case LDC:
                    if (pc >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int ldcIndex = Byte.toUnsignedInt(code[pc++]);
                    Logger.info("DEBUG: about to call resolveConstantPoolReference(" + ldcIndex
                            + ") at pc=" + (pc - 1));
                    sb.append("#").append(ldcIndex)
                            .append(" (").append(resolveConstantPoolReference(ldcIndex, constPool)).append(")");
                    break;

                //----------------------------------------------------------------------
                // (D) Two-byte immediate (SIPUSH).
                //----------------------------------------------------------------------
                case SIPUSH:
                    if (pc + 1 >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int sipushValue = ((code[pc] & 0xFF) << 8) | (code[pc + 1] & 0xFF);
                    sb.append(sipushValue);
                    pc += 2;
                    break;

                //----------------------------------------------------------------------
                // (E) Two-byte constant-pool references
                //----------------------------------------------------------------------
                case GETSTATIC:
                case PUTSTATIC:
                case GETFIELD:
                case PUTFIELD:
                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case CHECKCAST:
                case INSTANCEOF:
                case NEW:
                case ANEWARRAY:
                case MULTIANEWARRAY:
                case LDC_W:
                case LDC2_W:
                    if (pc + 1 >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int cpIndex = ((code[pc] & 0xFF) << 8) | (code[pc + 1] & 0xFF);
                    Logger.info("DEBUG: about to call resolveConstantPoolReference(" + cpIndex
                            + ") at pc=" + (pc - 1));
                    sb.append("#").append(cpIndex)
                            .append(" (").append(resolveConstantPoolReference(cpIndex, constPool)).append(")");
                    pc += 2;
                    break;

                // (J) Additional conditional branch instructions with 2-byte offset
                case IFEQ:
                case IFNE:
                case IFLT:
                case IFGE:
                case IFGT:
                case IFLE:
                case IF_ICMPEQ:
                case IF_ICMPNE:
                case IF_ICMPLT:
                case IF_ICMPGE:
                case IF_ICMPGT:
                case IF_ICMPLE:
                case IF_ACMPEQ:
                case IF_ACMPNE:
                case GOTO:        // 0xA7
                case JSR:         // 0xA8
                    if (pc + 1 >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int branchOffset2 = ((code[pc] & 0xFF) << 8) | (code[pc + 1] & 0xFF);
                    sb.append(branchOffset2);
                    pc += 2;
                    break;

                // RET has a single local variable index operand
                case RET:
                    if (pc >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int retIndex = Byte.toUnsignedInt(code[pc++]);
                    sb.append(retIndex);
                    break;

                //----------------------------------------------------------------------
                // (F) Four-byte references/instructions
                //----------------------------------------------------------------------
                case INVOKEINTERFACE: {
                    if (pc + 3 >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int cpIndex1 = ((code[pc] & 0xFF) << 8) | (code[pc + 1] & 0xFF);
                    Logger.info("DEBUG: INVOKEINTERFACE with cpIndex=" + cpIndex1
                            + " at pc=" + (pc - 1));
                    int count = code[pc + 2] & 0xFF;
                    int zero = code[pc + 3] & 0xFF;
                    pc += 4;

                    sb.append("#").append(cpIndex1)
                            .append(" (").append(resolveConstantPoolReference(cpIndex1, constPool)).append(")")
                            .append(", count=").append(count)
                            .append(", zero=").append(zero);
                    break;
                }

                case INVOKEDYNAMIC: {
                    if (pc + 3 >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int cpIndex2 = ((code[pc] & 0xFF) << 8) | (code[pc + 1] & 0xFF);
                    Logger.info("DEBUG: INVOKEDYNAMIC with cpIndex=" + cpIndex2
                            + " at pc=" + (pc - 1));
                    int zero1 = code[pc + 2] & 0xFF;
                    int zero2 = code[pc + 3] & 0xFF;
                    pc += 4;

                    sb.append("#").append(cpIndex2)
                            .append(" (InvokeDynamic bootstrapMethodIndex=")
                            .append(cpIndex2).append(")")
                            .append(", zero1=").append(zero1)
                            .append(", zero2=").append(zero2);
                    break;
                }

                //----------------------------------------------------------------------
                // (G) NEWARRAY uses a single byte for 'atype'.
                //----------------------------------------------------------------------
                case NEWARRAY:
                    if (pc >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int atype = code[pc++];
                    sb.append(atypeDescription(atype));
                    break;

                //----------------------------------------------------------------------
                // (H) IINC: varIndex, constValue
                //----------------------------------------------------------------------
                case IINC:
                    if (pc + 1 >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int varIndexIinc = Byte.toUnsignedInt(code[pc++]);
                    int constValueIinc = Byte.toUnsignedInt(code[pc++]);
                    sb.append(varIndexIinc).append(", ").append(constValueIinc);
                    break;

                //----------------------------------------------------------------------
                // (I) WIDE can modify next instruction's operand size
                //----------------------------------------------------------------------
                case WIDE:
                    if (pc >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int wideOpcodeValue = Byte.toUnsignedInt(code[pc++]);
                    Opcode wideOpcode = Opcode.fromCode(wideOpcodeValue);
                    Logger.info("DEBUG: WIDE sub-opcode " + wideOpcode.getMnemonic()
                            + " at pc=" + (pc - 1));
                    sb.append(wideOpcode.getMnemonic());
                    switch (wideOpcode) {
                        case ILOAD:
                        case FLOAD:
                        case LLOAD:
                        case DLOAD:
                        case ALOAD:
                        case ISTORE:
                        case FSTORE:
                        case LSTORE:
                        case DSTORE:
                        case ASTORE:
                            if (pc + 1 >= code.length) {
                                sb.append(" <invalid>");
                                break;
                            }
                            int wideVarIndex = ((code[pc] & 0xFF) << 8) | (code[pc + 1] & 0xFF);
                            sb.append(" ").append(wideVarIndex);
                            pc += 2;
                            break;
                        case IINC:
                            if (pc + 3 >= code.length) {
                                sb.append(" <invalid>");
                                break;
                            }
                            int wideIincVar = ((code[pc] & 0xFF) << 8) | (code[pc + 1] & 0xFF);
                            int wideIincConst = ((code[pc + 2] & 0xFF) << 8) | (code[pc + 3] & 0xFF);
                            sb.append(" ").append(wideIincVar)
                                    .append(", ").append(wideIincConst);
                            pc += 4;
                            break;
                        default:
                            sb.append(" <unsupported>");
                            break;
                    }
                    break;

                //----------------------------------------------------------------------
                // (J) Conditional branch instructions with 2-byte offset
                //----------------------------------------------------------------------
                case IFNULL:
                case IFNONNULL:
                    if (pc + 1 >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int branchOffset = ((code[pc] & 0xFF) << 8) | (code[pc + 1] & 0xFF);
                    sb.append(branchOffset);
                    pc += 2;
                    break;

                //----------------------------------------------------------------------
                // (K) GOTO_W, JSR_W => 4-byte offsets
                //----------------------------------------------------------------------
                case GOTO_W:
                case JSR_W:
                    if (pc + 3 >= code.length) {
                        sb.append(" <invalid>");
                        break;
                    }
                    int branchOffsetW = ((code[pc] & 0xFF) << 24)
                            | ((code[pc + 1] & 0xFF) << 16)
                            | ((code[pc + 2] & 0xFF) << 8)
                            |  (code[pc + 3] & 0xFF);
                    sb.append(branchOffsetW);
                    pc += 4;
                    break;

                //----------------------------------------------------------------------
                // (L) Switch instructions (LOOKUPSWITCH, TABLESWITCH)
                //----------------------------------------------------------------------
                case TABLESWITCH: {
                    // Align to 4 bytes
                    int padding = (4 - (pc % 4)) % 4;
                    pc += padding;

                    int defaultOffset = readIntFromCode(code, pc);
                    pc += 4;
                    int low = readIntFromCode(code, pc);
                    pc += 4;
                    int high = readIntFromCode(code, pc);
                    pc += 4;

                    sb.append(" default=").append(defaultOffset)
                            .append(", low=").append(low)
                            .append(", high=").append(high);

                    int count2 = high - low + 1;
                    sb.append(", count=").append(count2);

                    for (int i = 0; i < count2; i++) {
                        int jumpOffset = readIntFromCode(code, pc);
                        pc += 4;
                        sb.append("\n        case[").append(low + i)
                                .append("] => offset ").append(jumpOffset);
                    }
                    break;
                }

                case LOOKUPSWITCH: {
                    // Align to 4 bytes
                    int padding = (4 - (pc % 4)) % 4;
                    pc += padding;

                    int defaultOffset = readIntFromCode(code, pc);
                    pc += 4;

                    int npairs = readIntFromCode(code, pc);
                    pc += 4;

                    sb.append(" default=").append(defaultOffset)
                            .append(", npairs=").append(npairs);

                    for (int i = 0; i < npairs; i++) {
                        int match = readIntFromCode(code, pc);
                        pc += 4;
                        int jumpOffset = readIntFromCode(code, pc);
                        pc += 4;
                        sb.append("\n        match=").append(match)
                                .append(" => offset ").append(jumpOffset);
                    }
                    break;
                }

                //----------------------------------------------------------------------
                // (M) Default: unknown opcode
                //----------------------------------------------------------------------
                default:
                    sb.append(String.format("<unknown opcode 0x%02X>", opcodeValue));
                    break;
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Reads 4 bytes from the code array at the given offset as a big-endian int.
     */
    private static int readIntFromCode(byte[] code, int pos) {
        if (pos + 3 >= code.length) {
            throw new IndexOutOfBoundsException("Not enough bytes to read an int at position " + pos);
        }
        return ((code[pos] & 0xFF) << 24)
                | ((code[pos + 1] & 0xFF) << 16)
                | ((code[pos + 2] & 0xFF) << 8)
                |  (code[pos + 3] & 0xFF);
    }

    /**
     * Resolves a constant pool reference based on the index and returns a human-readable string.
     */
    private static String resolveConstantPoolReference(int index, ConstPool constPool) {
        Logger.info("DEBUG: resolveConstantPoolReference(" + index + ")");
        if (constPool.getItem(index) instanceof MethodRefItem) {
            MethodRefItem methodRef = (MethodRefItem) constPool.getItem(index);
            String className = methodRef.getClassName().replace('/', '.');
            String methodName = methodRef.getName();
            String methodDesc = methodRef.getDescriptor();
            return className + "." + methodName + methodDesc;
        } else if (constPool.getItem(index) instanceof FieldRefItem) {
            FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(index);
            String className = fieldRef.getClassName().replace('/', '.');
            String fieldName = fieldRef.getName();
            String fieldDesc = fieldRef.getDescriptor();
            return className + "." + fieldName + " " + fieldDesc;
        } else if (constPool.getItem(index) instanceof StringRefItem) {
            StringRefItem stringItem = (StringRefItem) constPool.getItem(index);
            return "\"" + stringItem.getValue() + "\"";
        } else if (constPool.getItem(index) instanceof ClassRefItem) {
            ClassRefItem classRef = (ClassRefItem) constPool.getItem(index);
            return classRef.getClassName().replace('/', '.');
        } else {
            return "UnknownReference";
        }
    }

    /**
     * Provides a description for the atype in NEWARRAY instruction.
     */
    private static String atypeDescription(int atype) {
        switch (atype) {
            case 4:
                return "boolean";
            case 5:
                return "char";
            case 6:
                return "float";
            case 7:
                return "double";
            case 8:
                return "byte";
            case 9:
                return "short";
            case 10:
                return "int";
            case 11:
                return "long";
            default:
                return "unknown_atype_" + atype;
        }
    }
}
