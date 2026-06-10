package com.tonic.analysis.instruction;

import com.tonic.parser.ConstPool;
import com.tonic.utill.Opcode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tonic.utill.Opcode.*;

/**
 * Decodes raw bytecode into {@link Instruction} objects. The single byte->Instruction decoder,
 * shared by {@code CodeWriter} (for editing) and {@code CodePrinter} (for disassembly).
 */
public final class InstructionFactory {

    private InstructionFactory() {
    }

    /** Parses a method body into its instructions, in offset order. */
    public static List<Instruction> parse(byte[] code, ConstPool constPool) {
        List<Instruction> out = new ArrayList<>();
        int offset = 0;
        while (offset < code.length) {
            Instruction instr = createInstruction(Byte.toUnsignedInt(code[offset]), offset, code, constPool);
            out.add(instr);
            offset += instr.getLength();
        }
        return out;
    }

    public static Instruction createInstruction(int opcode, int offset, byte[] bytecode, ConstPool constPool) {
        switch (opcode) {
            case 0x00:
                return new NopInstruction(opcode, offset);

            case 0x01:
                return new AConstNullInstruction(opcode, offset);

            case 0x02:
            case 0x03:
            case 0x04:
            case 0x05:
            case 0x06:
            case 0x07:
            case 0x08:
                int iconstValue = (opcode == ICONST_M1.getCode()) ? -1 : (opcode - ICONST_0.getCode());
                return new IConstInstruction(opcode, offset, iconstValue);

            case 0x09:
            case 0x0A:
                long lconstValue = (opcode == LCONST_0.getCode()) ? 0L : 1L;
                return new LConstInstruction(opcode, offset, lconstValue);

            case 0x0B:
            case 0x0C:
            case 0x0D:
                float fconstValue = (opcode == FCONST_0.getCode()) ? 0.0f : ((opcode == FCONST_1.getCode()) ? 1.0f : 2.0f);
                return new FConstInstruction(opcode, offset, fconstValue);

            case 0x0E:
            case 0x0F:
                double dconstValue = (opcode == DCONST_0.getCode()) ? 0.0 : 1.0;
                return new DConstInstruction(opcode, offset, dconstValue);

            case 0x10:
                if (offset + 1 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                byte bipushValue = bytecode[offset + 1];
                return new BipushInstruction(opcode, offset, bipushValue);

            case 0x11:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                short sipushValue = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                return new SipushInstruction(opcode, offset, sipushValue);

            case 0x12:
                if (offset + 1 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int ldcIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                return new LdcInstruction(constPool, opcode, offset, ldcIndex);

            case 0x13:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int ldcWIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new LdcWInstruction(constPool, opcode, offset, ldcWIndex);

            case 0x14:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int ldc2WIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new Ldc2WInstruction(constPool, opcode, offset, ldc2WIndex);

            case 0x15:
            case 0x16:
            case 0x17:
            case 0x18:
            case 0x19:
                return createLoadInstruction(opcode, offset, bytecode, getLoadInstructionName(opcode));

            case 0x1A:
            case 0x1B:
            case 0x1C:
            case 0x1D:
                int iloadIndex = opcode - 0x1A;
                return new ILoadInstruction(opcode, offset, iloadIndex);

            case 0x1E:
            case 0x1F:
            case 0x20:
            case 0x21:
                int lloadIndex = opcode - 0x1E;
                return new LLoadInstruction(opcode, offset, lloadIndex);

            case 0x22:
            case 0x23:
            case 0x24:
            case 0x25:
                int floadIndex = opcode - 0x22;
                return new FLoadInstruction(opcode, offset, floadIndex);

            case 0x26:
            case 0x27:
            case 0x28:
            case 0x29:
                int dloadIndex = opcode - 0x26;
                return new DLoadInstruction(opcode, offset, dloadIndex);

            case 0x2A:
            case 0x2B:
            case 0x2C:
            case 0x2D:
                int aloadIndex = opcode - 0x2A;
                return new ALoadInstruction(opcode, offset, aloadIndex);

            case 0x2E:
                return new IALoadInstruction(opcode, offset);

            case 0x2F:
                return new LALoadInstruction(opcode, offset);

            case 0x30:
                return new FALoadInstruction(opcode, offset);

            case 0x31:
                return new DALoadInstruction(opcode, offset);

            case 0x32:
                return new AALoadInstruction(opcode, offset);

            case 0x33:
                return new BALOADInstruction(opcode, offset);

            case 0x34:
                return new CALoadInstruction(opcode, offset);

            case 0x35:
                return new SALoadInstruction(opcode, offset);

            case 0x36:
            case 0x37:
            case 0x38:
            case 0x39:
            case 0x3A:
                return createStoreInstruction(opcode, offset, bytecode, getStoreInstructionName(opcode));

            case 0x3B:
            case 0x3C:
            case 0x3D:
            case 0x3E:
                int istoreIndex = opcode - 0x3B;
                return new IStoreInstruction(opcode, offset, istoreIndex);

            case 0x3F:
            case 0x40:
            case 0x41:
            case 0x42:
                int lstoreIndex = opcode - 0x3F;
                return new LStoreInstruction(opcode, offset, lstoreIndex);

            case 0x43:
            case 0x44:
            case 0x45:
            case 0x46:
                int fstoreIndex = opcode - 0x43;
                return new FStoreInstruction(opcode, offset, fstoreIndex);

            case 0x47:
            case 0x48:
            case 0x49:
            case 0x4A:
                int dstoreIndex = opcode - 0x47;
                return new DStoreInstruction(opcode, offset, dstoreIndex);

            case 0x4B:
            case 0x4C:
            case 0x4D:
            case 0x4E:
                int astoreIndex = opcode - 0x4B;
                return new AStoreInstruction(opcode, offset, astoreIndex);

            case 0x4F:
                return new IAStoreInstruction(opcode, offset);

            case 0x50:
                return new LAStoreInstruction(opcode, offset);

            case 0x51:
                return new FAStoreInstruction(opcode, offset);

            case 0x52:
                return new DAStoreInstruction(opcode, offset);

            case 0x53:
                return new AAStoreInstruction(opcode, offset);

            case 0x54:
                return new BAStoreInstruction(opcode, offset);

            case 0x55:
                return new CAStoreInstruction(opcode, offset);

            case 0x56:
                return new SAStoreInstruction(opcode, offset);

            case 0x57:
                return new PopInstruction(opcode, offset);

            case 0x58:
                return new Pop2Instruction(opcode, offset);

            case 0x59:
            case 0x5A:
            case 0x5B:
            case 0x5C:
            case 0x5D:
            case 0x5E:
                return new DupInstruction(opcode, offset);

            case 0x5F:
                return new SwapInstruction(opcode, offset);

            case 0x60:
            case 0x61:
            case 0x62:
            case 0x63:
            case 0x64:
            case 0x65:
            case 0x66:
            case 0x67:
            case 0x68:
            case 0x69:
            case 0x6A:
            case 0x6B:
            case 0x6C:
            case 0x6D:
            case 0x6E:
            case 0x6F:
            case 0x70:
            case 0x71:
            case 0x72:
            case 0x73:
                return new ArithmeticInstruction(opcode, offset);

            case 0x74:
                return new INegInstruction(opcode, offset);

            case 0x75:
                return new LNegInstruction(opcode, offset);

            case 0x76:
                return new FNegInstruction(opcode, offset);

            case 0x77:
                return new DNegInstruction(opcode, offset);

            case 0x78:
            case 0x79:
            case 0x7A:
            case 0x7B:
            case 0x7C:
            case 0x7D:
                return new ArithmeticShiftInstruction(opcode, offset);

            case 0x7E:
            case 0x7F:
                return new IAndInstruction(opcode, offset);

            case 0x80:
            case 0x81:
                return new IOrInstruction(opcode, offset);

            case 0x82:
            case 0x83:
                return new IXorInstruction(opcode, offset);

            case 0x84:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int iincVarIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                int iincConst = bytecode[offset + 2];
                return new IIncInstruction(opcode, offset, iincVarIndex, iincConst);

            case 0x85:
                return new I2LInstruction(opcode, offset);

            case 0x86:
            case 0x87:
            case 0x88:
            case 0x89:
            case 0x8A:
            case 0x8B:
            case 0x8C:
            case 0x8D:
            case 0x8E:
            case 0x8F:
            case 0x90:
                return new ConversionInstruction(opcode, offset);

            case 0x91:
            case 0x92:
            case 0x93:
                return new NarrowingConversionInstruction(opcode, offset);

            case 0x94:
            case 0x95:
            case 0x96:
            case 0x97:
            case 0x98:
                return new CompareInstruction(opcode, offset);

            case 0x99:
            case 0x9A:
            case 0x9B:
            case 0x9C:
            case 0x9D:
            case 0x9E:
            case 0x9F:
            case 0xA0:
            case 0xA1:
            case 0xA2:
            case 0xA3:
            case 0xA4:
            case 0xA5:
            case 0xA6:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                short branchOffsetCond = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                return new ConditionalBranchInstruction(opcode, offset, branchOffsetCond);

            case 0xA7:
            case 0xC8:
                return parseGotoInstruction(opcode, offset, bytecode);

            case 0xA8:
            case 0xC9:
                return parseJsrInstruction(opcode, offset, bytecode);

            case 0xA9:
                if (offset + 1 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int retVarIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                return new RetInstruction(opcode, offset, retVarIndex);

            case 0xB6:
            case 0xB7:
            case 0xB8:
                return parseInvokeInstruction(opcode, offset, bytecode, constPool);

            case 0xB9:
                if (offset + 4 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int invokeInterfaceIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                int count = Byte.toUnsignedInt(bytecode[offset + 3]);
                return new InvokeInterfaceInstruction(constPool, opcode, offset, invokeInterfaceIndex, count);

            case 0xBA:
                if (offset + 4 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int invokedynamicCpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new InvokeDynamicInstruction(constPool, opcode, offset, invokedynamicCpIndex);

            case 0xB2:
            case 0xB4:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int fieldRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new GetFieldInstruction(constPool, opcode, offset, fieldRefIndex);

            case 0xB3:
            case 0xB5:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int putFieldRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new PutFieldInstruction(constPool, opcode, offset, putFieldRefIndex);

            case 0xBB:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int newClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new NewInstruction(constPool, opcode, offset, newClassIndex);

            case 0xBC:
                if (offset + 1 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int newarrayTypeCode = Byte.toUnsignedInt(bytecode[offset + 1]);
                return new NewArrayInstruction(opcode, offset, newarrayTypeCode, 1);

            case 0xBD:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int anewarrayClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new ANewArrayInstruction(constPool, opcode, offset, anewarrayClassIndex, 2);

            case 0xBE:
                return new ArrayLengthInstruction(opcode, offset);

            case 0xBF:
                return new ATHROWInstruction(opcode, offset);

            case 0xC0:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int typeIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new CheckCastInstruction(constPool, opcode, offset, typeIndex);

            case 0xC5:
                if (offset + 3 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int multianewarrayClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                int dimensions = Byte.toUnsignedInt(bytecode[offset + 3]);
                return new MultiANewArrayInstruction(constPool, opcode, offset, multianewarrayClassIndex, dimensions);

            case 0xAB:
                return parseLookupSwitchInstruction(opcode, offset, bytecode);

            case 0xAA:
                return parseTableSwitchInstruction(opcode, offset, bytecode);

            case 0xC4:
                return parseWideInstruction(opcode, offset, bytecode);

            case 0xAC:
            case 0xAD:
            case 0xAE:
            case 0xAF:
            case 0xB0:
            case 0xB1:
                return new ReturnInstruction(opcode, offset);

            case 0xC1:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int instanceOfClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new InstanceOfInstruction(constPool, opcode, offset, instanceOfClassIndex);

            case 0xC3:
                return new MonitorExitInstruction(opcode, offset);

            case 0xC2:
                return new MonitorEnterInstruction(opcode, offset);

            case 0xC6:
            case 0xC7:
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                short branchOffsetNull = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                return new ConditionalBranchInstruction(opcode, offset, branchOffsetNull);

            default:
                return new UnknownInstruction(opcode, offset, 1);
        }
    }

    /**
     * Helper method to get the instruction name based on opcode.
     *
     * @param opcode The opcode.
     * @return The name of the load instruction.
     */
    private static String getLoadInstructionName(int opcode) {
        if (opcode == ILOAD.getCode()) return "ILOAD";
        if (opcode == LLOAD.getCode()) return "LLOAD";
        if (opcode == FLOAD.getCode()) return "FLOAD";
        if (opcode == DLOAD.getCode()) return "DLOAD";
        if (opcode == ALOAD.getCode()) return "ALOAD";
        return "UNKNOWN_LOAD";
    }

    /**
     * Helper method to get the instruction name based on opcode.
     *
     * @param opcode The opcode.
     * @return The name of the store instruction.
     */
    private static String getStoreInstructionName(int opcode) {
        if (opcode == ISTORE.getCode()) return "ISTORE";
        if (opcode == LSTORE.getCode()) return "LSTORE";
        if (opcode == FSTORE.getCode()) return "FSTORE";
        if (opcode == DSTORE.getCode()) return "DSTORE";
        if (opcode == ASTORE.getCode()) return "ASTORE";
        return "UNKNOWN_STORE";
    }

    /**
     * Helper method to create Load Instructions.
     *
     * @param opcode           The opcode of the load instruction.
     * @param offset           The bytecode offset.
     * @param bytecode         The entire bytecode array.
     * @param instructionName  The name of the instruction (e.g., "ILOAD").
     * @return A LoadInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction createLoadInstruction(int opcode, int offset, byte[] bytecode, String instructionName) {
        int operandBytes = 1;
        if (offset + operandBytes >= bytecode.length) {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }
        int varIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
        switch (instructionName) {
            case "ILOAD":
                return new ILoadInstruction(opcode, offset, varIndex);
            case "LLOAD":
                return new LLoadInstruction(opcode, offset, varIndex);
            case "FLOAD":
                return new FLoadInstruction(opcode, offset, varIndex);
            case "DLOAD":
                return new DLoadInstruction(opcode, offset, varIndex);
            case "ALOAD":
                return new ALoadInstruction(opcode, offset, varIndex);
            default:
                return new UnknownInstruction(opcode, offset, operandBytes + 1);
        }
    }

    /**
     * Helper method to create Store Instructions.
     *
     * @param opcode           The opcode of the store instruction.
     * @param offset           The bytecode offset.
     * @param bytecode         The entire bytecode array.
     * @param instructionName  The name of the instruction (e.g., "ISTORE").
     * @return A StoreInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction createStoreInstruction(int opcode, int offset, byte[] bytecode, String instructionName) {
        int operandBytes = 1;
        if (offset + operandBytes >= bytecode.length) {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }
        int varIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
        switch (instructionName) {
            case "ISTORE":
                return new IStoreInstruction(opcode, offset, varIndex);
            case "LSTORE":
                return new LStoreInstruction(opcode, offset, varIndex);
            case "FSTORE":
                return new FStoreInstruction(opcode, offset, varIndex);
            case "DSTORE":
                return new DStoreInstruction(opcode, offset, varIndex);
            case "ASTORE":
                return new AStoreInstruction(opcode, offset, varIndex);
            default:
                return new UnknownInstruction(opcode, offset, operandBytes + 1);
        }
    }

    /**
     * Parses a GOTO instruction (0xA7) or GOTO_W (0xC8).
     *
     * @param opcode   The opcode of the GOTO instruction.
     * @param offset   The bytecode offset.
     * @param bytecode The entire bytecode array.
     * @return A GotoInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction parseGotoInstruction(int opcode, int offset, byte[] bytecode) {
        if (opcode == GOTO.getCode()) {
            if (offset + 2 >= bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
            }
            short branchOffset = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
            return new GotoInstruction(opcode, offset, branchOffset);
        } else if (opcode == GOTO_W.getCode()) {
            if (offset + 4 >= bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
            }
            int branchOffset = (bytecode[offset + 1] << 24) | ((bytecode[offset + 2] & 0xFF) << 16) |
                    ((bytecode[offset + 3] & 0xFF) << 8) | (bytecode[offset + 4] & 0xFF);
            return new GotoInstruction(opcode, offset, branchOffset);
        } else {
            return new UnknownInstruction(opcode, offset, 1);
        }
    }

    /**
     * Parses a JSR instruction (0xA8) or JSR_W (0xC9).
     *
     * @param opcode   The opcode of the JSR instruction.
     * @param offset   The bytecode offset.
     * @param bytecode The entire bytecode array.
     * @return A JsrInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction parseJsrInstruction(int opcode, int offset, byte[] bytecode) {
        if (opcode == JSR.getCode()) {
            if (offset + 2 >= bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
            }
            short jsrOffset = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
            return new JsrInstruction(opcode, offset, jsrOffset);
        } else if (opcode == JSR_W.getCode()) {
            if (offset + 4 >= bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
            }
            int jsrOffset = (bytecode[offset + 1] << 24) | ((bytecode[offset + 2] & 0xFF) << 16) |
                    ((bytecode[offset + 3] & 0xFF) << 8) | (bytecode[offset + 4] & 0xFF);
            return new JsrInstruction(opcode, offset, jsrOffset);
        } else {
            return new UnknownInstruction(opcode, offset, 1);
        }
    }

    /**
     * Parses INVOKEVIRTUAL, INVOKESPECIAL, and INVOKESTATIC instructions (0xB6 - 0xB8).
     *
     * @param opcode     The opcode of the invoke instruction.
     * @param offset     The bytecode offset.
     * @param bytecode   The entire bytecode array.
     * @param constPool  The constant pool associated with the class.
     * @return The corresponding InvokeInstruction instance.
     */
    private static Instruction parseInvokeInstruction(int opcode, int offset, byte[] bytecode, ConstPool constPool) {
        if (offset + 2 >= bytecode.length) {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }
        int methodRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
        if (opcode == INVOKEVIRTUAL.getCode()) {
            return new InvokeVirtualInstruction(constPool, opcode, offset, methodRefIndex);
        } else if (opcode == INVOKESPECIAL.getCode()) {
            return new InvokeSpecialInstruction(constPool, opcode, offset, methodRefIndex);
        } else if (opcode == INVOKESTATIC.getCode()) {
            return new InvokeStaticInstruction(constPool, opcode, offset, methodRefIndex);
        }
        return new UnknownInstruction(opcode, offset, 3);
    }

    /**
     * Parses a LOOKUPSWITCH instruction starting at the given offset.
     *
     * @param opcode    The opcode of the instruction (0xAB).
     * @param offset    The bytecode offset of the instruction.
     * @param bytecode  The entire bytecode array.
     * @return A LookupSwitchInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction parseLookupSwitchInstruction(int opcode, int offset, byte[] bytecode) {
        int padding = (4 - ((offset + 1) % 4)) % 4;
        int defaultOffsetPos = offset + 1 + padding;
        int npairsPos = defaultOffsetPos + 4;

        if (npairsPos + 4 > bytecode.length) {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }

        int defaultOffset = ((bytecode[defaultOffsetPos] & 0xFF) << 24) |
                ((bytecode[defaultOffsetPos + 1] & 0xFF) << 16) |
                ((bytecode[defaultOffsetPos + 2] & 0xFF) << 8) |
                (bytecode[defaultOffsetPos + 3] & 0xFF);

        int npairs = ((bytecode[npairsPos] & 0xFF) << 24) |
                ((bytecode[npairsPos + 1] & 0xFF) << 16) |
                ((bytecode[npairsPos + 2] & 0xFF) << 8) |
                (bytecode[npairsPos + 3] & 0xFF);

        int pairsStart = npairsPos + 4;
        int pairsLength = npairs * 8;

        if (pairsStart + pairsLength > bytecode.length) {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }

        Map<Integer, Integer> matchOffsets = new LinkedHashMap<>();
        for (int i = 0; i < npairs; i++) {
            int keyPos = pairsStart + i * 8;
            int jumpOffsetPos = keyPos + 4;

            int key = ((bytecode[keyPos] & 0xFF) << 24) |
                    ((bytecode[keyPos + 1] & 0xFF) << 16) |
                    ((bytecode[keyPos + 2] & 0xFF) << 8) |
                    (bytecode[keyPos + 3] & 0xFF);

            int jumpOffset = ((bytecode[jumpOffsetPos] & 0xFF) << 24) |
                    ((bytecode[jumpOffsetPos + 1] & 0xFF) << 16) |
                    ((bytecode[jumpOffsetPos + 2] & 0xFF) << 8) |
                    (bytecode[jumpOffsetPos + 3] & 0xFF);

            matchOffsets.put(key, jumpOffset);
        }

        return new LookupSwitchInstruction(opcode, offset, padding, defaultOffset, npairs, matchOffsets);
    }

    /**
     * Parses a TABLESWITCH instruction starting at the given offset.
     *
     * @param opcode    The opcode of the instruction (0xAA).
     * @param offset    The bytecode offset of the instruction.
     * @param bytecode  The entire bytecode array.
     * @return A TableSwitchInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction parseTableSwitchInstruction(int opcode, int offset, byte[] bytecode) {
        int padding = (4 - ((offset + 1) % 4)) % 4;
        int defaultOffsetPos = offset + 1 + padding;
        int lowPos = defaultOffsetPos + 4;
        int highPos = lowPos + 4;

        if (highPos + 4 > bytecode.length) {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }

        int defaultOffset = ((bytecode[defaultOffsetPos] & 0xFF) << 24) |
                ((bytecode[defaultOffsetPos + 1] & 0xFF) << 16) |
                ((bytecode[defaultOffsetPos + 2] & 0xFF) << 8) |
                (bytecode[defaultOffsetPos + 3] & 0xFF);

        int low = ((bytecode[lowPos] & 0xFF) << 24) |
                ((bytecode[lowPos + 1] & 0xFF) << 16) |
                ((bytecode[lowPos + 2] & 0xFF) << 8) |
                (bytecode[lowPos + 3] & 0xFF);

        int high = ((bytecode[highPos] & 0xFF) << 24) |
                ((bytecode[highPos + 1] & 0xFF) << 16) |
                ((bytecode[highPos + 2] & 0xFF) << 8) |
                (bytecode[highPos + 3] & 0xFF);

        int jumpOffsetsStart = highPos + 4;
        int jumpOffsetsLength = (high - low + 1) * 4;

        if (jumpOffsetsStart + jumpOffsetsLength > bytecode.length) {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }

        Map<Integer, Integer> jumpOffsets = new LinkedHashMap<>();
        for (int i = 0; i <= high - low; i++) {
            int jumpOffsetPos = jumpOffsetsStart + i * 4;
            int jumpOffset = ((bytecode[jumpOffsetPos] & 0xFF) << 24) |
                    ((bytecode[jumpOffsetPos + 1] & 0xFF) << 16) |
                    ((bytecode[jumpOffsetPos + 2] & 0xFF) << 8) |
                    (bytecode[jumpOffsetPos + 3] & 0xFF);
            int key = low + i;
            jumpOffsets.put(key, jumpOffset);
        }

        return new TableSwitchInstruction(opcode, offset, padding, defaultOffset, low, high, jumpOffsets);
    }

    /**
     * Parses a WIDE instruction starting at the given offset.
     *
     * @param opcode    The opcode of the instruction (0xC4).
     * @param offset    The bytecode offset of the instruction.
     * @param bytecode  The entire bytecode array.
     * @return A WideInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction parseWideInstruction(int opcode, int offset, byte[] bytecode) {
        if (offset + 1 >= bytecode.length) {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }

        int modifiedOpcodeCode = Byte.toUnsignedInt(bytecode[offset + 1]);

        switch (modifiedOpcodeCode) {
            case 0x15:
            case 0x16:
            case 0x17:
            case 0x18:
            case 0x19:
                if (offset + 3 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int varIndexLoad = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                return new WideInstruction(opcode, offset, Opcode.fromCode(modifiedOpcodeCode), varIndexLoad);

            case 0x36:
            case 0x37:
            case 0x38:
            case 0x39:
            case 0x3A:
                if (offset + 3 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int varIndexStore = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                return new WideInstruction(opcode, offset, Opcode.fromCode(modifiedOpcodeCode), varIndexStore);

            case 0x84:
                if (offset + 5 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int wideVarIndex = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                int wideConstValue = ((bytecode[offset + 4] & 0xFF) << 8) | (bytecode[offset + 5] & 0xFF);
                return new WideIIncInstruction(opcode, offset, wideVarIndex, wideConstValue);

            default:
                return new UnknownInstruction(opcode, offset, 2);
        }
    }
}
