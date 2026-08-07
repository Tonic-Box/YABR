package com.tonic.analysis.instruction;

import com.tonic.parser.ConstPool;
import com.tonic.util.Opcode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tonic.util.Opcode.*;

/**
 * Decodes raw bytecode into {@link Instruction} objects.
 */
public final class InstructionFactory
{

    private InstructionFactory()
    {
    }

    /**
     * Parses a method body into its instructions, in offset order.
     * @param code the method bytecode
     * @param constPool the class's constant pool for operand resolution
     * @return the decoded instructions in offset order
     */
    public static List<Instruction> parse(byte[] code, ConstPool constPool)
    {
        List<Instruction> out = new ArrayList<>();
        int offset = 0;
        while (offset < code.length)
        {
            Instruction instr = createInstruction(Byte.toUnsignedInt(code[offset]), offset, code, constPool);
            out.add(instr);
            offset += instr.getLength();
        }
        return out;
    }

    /**
     * Decodes the single instruction starting at an offset, reading its operands from the bytecode.
     * @param opcode the opcode byte at the offset
     * @param offset the bytecode offset of the instruction
     * @param bytecode the full method bytecode
     * @param constPool the class's constant pool for operand resolution
     * @return the decoded instruction
     */
    public static Instruction createInstruction(int opcode, int offset, byte[] bytecode, ConstPool constPool)
    {
        switch (Opcode.fromCode(opcode))
        {
            case NOP:
                return new NopInstruction(opcode, offset);

            case ACONST_NULL:
                return new AConstNullInstruction(opcode, offset);

            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
                int iconstValue = (opcode == ICONST_M1.getCode()) ? -1 : (opcode - ICONST_0.getCode());
                return new IConstInstruction(opcode, offset, iconstValue);

            case LCONST_0:
            case LCONST_1:
                long lconstValue = (opcode == LCONST_0.getCode()) ? 0L : 1L;
                return new LConstInstruction(opcode, offset, lconstValue);

            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
                float fconstValue = (opcode == FCONST_0.getCode()) ? 0.0f : ((opcode == FCONST_1.getCode()) ? 1.0f : 2.0f);
                return new FConstInstruction(opcode, offset, fconstValue);

            case DCONST_0:
            case DCONST_1:
                double dconstValue = (opcode == DCONST_0.getCode()) ? 0.0 : 1.0;
                return new DConstInstruction(opcode, offset, dconstValue);

            case BIPUSH:
                if (offset + 1 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                byte bipushValue = bytecode[offset + 1];
                return new BipushInstruction(opcode, offset, bipushValue);

            case SIPUSH:
                if (offset + 2 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                short sipushValue = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                return new SipushInstruction(opcode, offset, sipushValue);

            case LDC:
                if (offset + 1 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int ldcIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                return new LdcInstruction(constPool, opcode, offset, ldcIndex);

            case LDC_W:
                if (offset + 2 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int ldcWIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new LdcWInstruction(constPool, opcode, offset, ldcWIndex);

            case LDC2_W:
                if (offset + 2 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int ldc2WIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new Ldc2WInstruction(constPool, opcode, offset, ldc2WIndex);

            case ILOAD:
            case LLOAD:
            case FLOAD:
            case DLOAD:
            case ALOAD:
                return createLoadInstruction(opcode, offset, bytecode);

            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3:
                int iloadIndex = opcode - ILOAD_0.getCode();
                return new ILoadInstruction(opcode, offset, iloadIndex);

            case LLOAD_0:
            case LLOAD_1:
            case LLOAD_2:
            case LLOAD_3:
                int lloadIndex = opcode - LLOAD_0.getCode();
                return new LLoadInstruction(opcode, offset, lloadIndex);

            case FLOAD_0:
            case FLOAD_1:
            case FLOAD_2:
            case FLOAD_3:
                int floadIndex = opcode - FLOAD_0.getCode();
                return new FLoadInstruction(opcode, offset, floadIndex);

            case DLOAD_0:
            case DLOAD_1:
            case DLOAD_2:
            case DLOAD_3:
                int dloadIndex = opcode - DLOAD_0.getCode();
                return new DLoadInstruction(opcode, offset, dloadIndex);

            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3:
                int aloadIndex = opcode - ALOAD_0.getCode();
                return new ALoadInstruction(opcode, offset, aloadIndex);

            case IALOAD:
                return new IALoadInstruction(opcode, offset);

            case LALOAD:
                return new LALoadInstruction(opcode, offset);

            case FALOAD:
                return new FALoadInstruction(opcode, offset);

            case DALOAD:
                return new DALoadInstruction(opcode, offset);

            case AALOAD:
                return new AALoadInstruction(opcode, offset);

            case BALOAD:
                return new BALOADInstruction(opcode, offset);

            case CALOAD:
                return new CALoadInstruction(opcode, offset);

            case SALOAD:
                return new SALoadInstruction(opcode, offset);

            case ISTORE:
            case LSTORE:
            case FSTORE:
            case DSTORE:
            case ASTORE:
                return createStoreInstruction(opcode, offset, bytecode);

            case ISTORE_0:
            case ISTORE_1:
            case ISTORE_2:
            case ISTORE_3:
                int istoreIndex = opcode - ISTORE_0.getCode();
                return new IStoreInstruction(opcode, offset, istoreIndex);

            case LSTORE_0:
            case LSTORE_1:
            case LSTORE_2:
            case LSTORE_3:
                int lstoreIndex = opcode - LSTORE_0.getCode();
                return new LStoreInstruction(opcode, offset, lstoreIndex);

            case FSTORE_0:
            case FSTORE_1:
            case FSTORE_2:
            case FSTORE_3:
                int fstoreIndex = opcode - FSTORE_0.getCode();
                return new FStoreInstruction(opcode, offset, fstoreIndex);

            case DSTORE_0:
            case DSTORE_1:
            case DSTORE_2:
            case DSTORE_3:
                int dstoreIndex = opcode - DSTORE_0.getCode();
                return new DStoreInstruction(opcode, offset, dstoreIndex);

            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3:
                int astoreIndex = opcode - ASTORE_0.getCode();
                return new AStoreInstruction(opcode, offset, astoreIndex);

            case IASTORE:
                return new IAStoreInstruction(opcode, offset);

            case LASTORE:
                return new LAStoreInstruction(opcode, offset);

            case FASTORE:
                return new FAStoreInstruction(opcode, offset);

            case DASTORE:
                return new DAStoreInstruction(opcode, offset);

            case AASTORE:
                return new AAStoreInstruction(opcode, offset);

            case BASTORE:
                return new BAStoreInstruction(opcode, offset);

            case CASTORE:
                return new CAStoreInstruction(opcode, offset);

            case SASTORE:
                return new SAStoreInstruction(opcode, offset);

            case POP:
                return new PopInstruction(opcode, offset);

            case POP2:
                return new Pop2Instruction(opcode, offset);

            case DUP:
            case DUP_X1:
            case DUP_X2:
            case DUP2:
            case DUP2_X1:
            case DUP2_X2:
                return new DupInstruction(opcode, offset);

            case SWAP:
                return new SwapInstruction(opcode, offset);

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
                return new ArithmeticInstruction(opcode, offset);

            case INEG:
                return new INegInstruction(opcode, offset);

            case LNEG:
                return new LNegInstruction(opcode, offset);

            case FNEG:
                return new FNegInstruction(opcode, offset);

            case DNEG:
                return new DNegInstruction(opcode, offset);

            case ISHL:
            case LSHL:
            case ISHR:
            case LSHR:
            case IUSHR:
            case LUSHR:
                return new ArithmeticShiftInstruction(opcode, offset);

            case IAND:
            case LAND:
                return new IAndInstruction(opcode, offset);

            case IOR:
            case LOR:
                return new IOrInstruction(opcode, offset);

            case IXOR:
            case LXOR:
                return new IXorInstruction(opcode, offset);

            case IINC:
                if (offset + 2 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int iincVarIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                int iincConst = bytecode[offset + 2];
                return new IIncInstruction(opcode, offset, iincVarIndex, iincConst);

            case I2L:
                return new I2LInstruction(opcode, offset);

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
                return new ConversionInstruction(opcode, offset);

            case I2B:
            case I2C:
            case I2S:
                return new NarrowingConversionInstruction(opcode, offset);

            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                return new CompareInstruction(opcode, offset);

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

            case IFNULL:
            case IFNONNULL:
                if (offset + 2 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                short branchOffsetCond = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                return new ConditionalBranchInstruction(opcode, offset, branchOffsetCond);

            case GOTO:
            case GOTO_W:
                return parseGotoInstruction(opcode, offset, bytecode);

            case JSR:
            case JSR_W:
                return parseJsrInstruction(opcode, offset, bytecode);

            case RET:
                if (offset + 1 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int retVarIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                return new RetInstruction(opcode, offset, retVarIndex);

            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
                return parseInvokeInstruction(opcode, offset, bytecode, constPool);

            case INVOKEINTERFACE:
                if (offset + 4 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int invokeInterfaceIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                int count = Byte.toUnsignedInt(bytecode[offset + 3]);
                return new InvokeInterfaceInstruction(constPool, opcode, offset, invokeInterfaceIndex, count);

            case INVOKEDYNAMIC:
                if (offset + 4 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int invokedynamicCpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new InvokeDynamicInstruction(constPool, opcode, offset, invokedynamicCpIndex);

            case GETSTATIC:
            case GETFIELD:
                if (offset + 2 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int fieldRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new GetFieldInstruction(constPool, opcode, offset, fieldRefIndex);

            case PUTSTATIC:
            case PUTFIELD:
                if (offset + 2 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int putFieldRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new PutFieldInstruction(constPool, opcode, offset, putFieldRefIndex);

            case NEW:
                if (offset + 2 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int newClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new NewObjectInstruction(constPool, opcode, offset, newClassIndex);

            case NEWARRAY:
                if (offset + 1 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int newarrayTypeCode = Byte.toUnsignedInt(bytecode[offset + 1]);
                return new NewPrimitiveArrayInstruction(opcode, offset, newarrayTypeCode, 1);

            case ANEWARRAY:
                if (offset + 2 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int anewarrayClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new ANewArrayInstruction(constPool, opcode, offset, anewarrayClassIndex, 2);

            case ARRAYLENGTH:
                return new ArrayLengthInstruction(opcode, offset);

            case ATHROW:
                return new ATHROWInstruction(opcode, offset);

            case CHECKCAST:
                if (offset + 2 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int typeIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new CheckCastInstruction(constPool, opcode, offset, typeIndex);

            case MULTIANEWARRAY:
                if (offset + 3 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int multianewarrayClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                int dimensions = Byte.toUnsignedInt(bytecode[offset + 3]);
                return new MultiANewArrayInstruction(constPool, opcode, offset, multianewarrayClassIndex, dimensions);

            case LOOKUPSWITCH:
                return parseLookupSwitchInstruction(opcode, offset, bytecode);

            case TABLESWITCH:
                return parseTableSwitchInstruction(opcode, offset, bytecode);

            case WIDE:
                return parseWideInstruction(opcode, offset, bytecode);

            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
            case RETURN_:
                return new MethodReturnInstruction(opcode, offset);

            case INSTANCEOF:
                if (offset + 2 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int instanceOfClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                return new InstanceOfInstruction(constPool, opcode, offset, instanceOfClassIndex);

            case MONITOREXIT:
                return new MonitorExitInstruction(opcode, offset);

            case MONITORENTER:
                return new MonitorEnterInstruction(opcode, offset);

            default:
                return new UnknownInstruction(opcode, offset, 1);
        }
    }

    /**
     * Decodes a wide-index load, reading its one-byte local index.
     * @param opcode the load opcode
     * @param offset the bytecode offset
     * @param bytecode the entire bytecode array
     * @return the load instruction, or UnknownInstruction if the operand is truncated
     */
    private static Instruction createLoadInstruction(int opcode, int offset, byte[] bytecode)
    {
        if (offset + 1 >= bytecode.length)
        {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }
        int varIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
        switch (Opcode.fromCode(opcode))
        {
            case ILOAD:
                return new ILoadInstruction(opcode, offset, varIndex);
            case LLOAD:
                return new LLoadInstruction(opcode, offset, varIndex);
            case FLOAD:
                return new FLoadInstruction(opcode, offset, varIndex);
            case DLOAD:
                return new DLoadInstruction(opcode, offset, varIndex);
            case ALOAD:
                return new ALoadInstruction(opcode, offset, varIndex);
            default:
                return new UnknownInstruction(opcode, offset, 2);
        }
    }

    /**
     * Decodes a store, reading its one-byte local index.
     * @param opcode the store opcode
     * @param offset the bytecode offset
     * @param bytecode the entire bytecode array
     * @return the store instruction, or UnknownInstruction if the operand is truncated
     */
    private static Instruction createStoreInstruction(int opcode, int offset, byte[] bytecode)
    {
        if (offset + 1 >= bytecode.length)
        {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }
        int varIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
        switch (Opcode.fromCode(opcode))
        {
            case ISTORE:
                return new IStoreInstruction(opcode, offset, varIndex);
            case LSTORE:
                return new LStoreInstruction(opcode, offset, varIndex);
            case FSTORE:
                return new FStoreInstruction(opcode, offset, varIndex);
            case DSTORE:
                return new DStoreInstruction(opcode, offset, varIndex);
            case ASTORE:
                return new AStoreInstruction(opcode, offset, varIndex);
            default:
                return new UnknownInstruction(opcode, offset, 2);
        }
    }

    /**
     * Parses a GOTO instruction (0xA7) or GOTO_W (0xC8).
     * @param opcode   The opcode of the GOTO instruction.
     * @param offset   The bytecode offset.
     * @param bytecode The entire bytecode array.
     * @return A GotoInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction parseGotoInstruction(int opcode, int offset, byte[] bytecode)
    {
        if (opcode == GOTO.getCode())
        {
            if (offset + 2 >= bytecode.length)
            {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
            }
            short branchOffset = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
            return new GotoInstruction(opcode, offset, branchOffset);
        }
        else if (opcode == GOTO_W.getCode())
        {
            if (offset + 4 >= bytecode.length)
            {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
            }
            int branchOffset = (bytecode[offset + 1] << 24) | ((bytecode[offset + 2] & 0xFF) << 16) |
                    ((bytecode[offset + 3] & 0xFF) << 8) | (bytecode[offset + 4] & 0xFF);
            return new GotoInstruction(opcode, offset, branchOffset);
        }
        else
        {
            return new UnknownInstruction(opcode, offset, 1);
        }
    }

    /**
     * Parses a JSR instruction (0xA8) or JSR_W (0xC9).
     * @param opcode   The opcode of the JSR instruction.
     * @param offset   The bytecode offset.
     * @param bytecode The entire bytecode array.
     * @return A JsrInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction parseJsrInstruction(int opcode, int offset, byte[] bytecode)
    {
        if (opcode == JSR.getCode())
        {
            if (offset + 2 >= bytecode.length)
            {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
            }
            short jsrOffset = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
            return new JsrInstruction(opcode, offset, jsrOffset);
        }
        else if (opcode == JSR_W.getCode())
        {
            if (offset + 4 >= bytecode.length)
            {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
            }
            int jsrOffset = (bytecode[offset + 1] << 24) | ((bytecode[offset + 2] & 0xFF) << 16) |
                    ((bytecode[offset + 3] & 0xFF) << 8) | (bytecode[offset + 4] & 0xFF);
            return new JsrInstruction(opcode, offset, jsrOffset);
        }
        else
        {
            return new UnknownInstruction(opcode, offset, 1);
        }
    }

    /**
     * Parses INVOKEVIRTUAL, INVOKESPECIAL, and INVOKESTATIC instructions (0xB6 - 0xB8).
     * @param opcode     The opcode of the invoke instruction.
     * @param offset     The bytecode offset.
     * @param bytecode   The entire bytecode array.
     * @param constPool  The constant pool associated with the class.
     * @return The corresponding InvokeInstruction instance.
     */
    private static Instruction parseInvokeInstruction(int opcode, int offset, byte[] bytecode, ConstPool constPool)
    {
        if (offset + 2 >= bytecode.length)
        {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }
        int methodRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
        if (opcode == INVOKEVIRTUAL.getCode())
        {
            return new InvokeVirtualInstruction(constPool, opcode, offset, methodRefIndex);
        }
        else if (opcode == INVOKESPECIAL.getCode())
        {
            return new InvokeSpecialInstruction(constPool, opcode, offset, methodRefIndex);
        }
        else if (opcode == INVOKESTATIC.getCode())
        {
            return new InvokeStaticInstruction(constPool, opcode, offset, methodRefIndex);
        }
        return new UnknownInstruction(opcode, offset, 3);
    }

    /**
     * Parses a LOOKUPSWITCH instruction starting at the given offset.
     * @param opcode    The opcode of the instruction (0xAB).
     * @param offset    The bytecode offset of the instruction.
     * @param bytecode  The entire bytecode array.
     * @return A LookupSwitchInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction parseLookupSwitchInstruction(int opcode, int offset, byte[] bytecode)
    {
        int padding = (4 - ((offset + 1) % 4)) % 4;
        int defaultOffsetPos = offset + 1 + padding;
        int npairsPos = defaultOffsetPos + 4;

        if (npairsPos + 4 > bytecode.length)
        {
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

        if (pairsStart + pairsLength > bytecode.length)
        {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }

        Map<Integer, Integer> matchOffsets = new LinkedHashMap<>();
        for (int i = 0; i < npairs; i++)
        {
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
     * @param opcode    The opcode of the instruction (0xAA).
     * @param offset    The bytecode offset of the instruction.
     * @param bytecode  The entire bytecode array.
     * @return A TableSwitchInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction parseTableSwitchInstruction(int opcode, int offset, byte[] bytecode)
    {
        int padding = (4 - ((offset + 1) % 4)) % 4;
        int defaultOffsetPos = offset + 1 + padding;
        int lowPos = defaultOffsetPos + 4;
        int highPos = lowPos + 4;

        if (highPos + 4 > bytecode.length)
        {
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

        if (jumpOffsetsStart + jumpOffsetsLength > bytecode.length)
        {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }

        Map<Integer, Integer> jumpOffsets = new LinkedHashMap<>();
        for (int i = 0; i <= high - low; i++)
        {
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
     * @param opcode    The opcode of the instruction (0xC4).
     * @param offset    The bytecode offset of the instruction.
     * @param bytecode  The entire bytecode array.
     * @return A WideInstruction instance or UnknownInstruction if malformed.
     */
    private static Instruction parseWideInstruction(int opcode, int offset, byte[] bytecode)
    {
        if (offset + 1 >= bytecode.length)
        {
            return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
        }

        int modifiedOpcodeCode = Byte.toUnsignedInt(bytecode[offset + 1]);

        switch (Opcode.fromCode(modifiedOpcodeCode))
        {
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

            case RET:
                if (offset + 3 >= bytecode.length)
                {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset, bytecode);
                }
                int varIndexLoad = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                return new WideInstruction(opcode, offset, Opcode.fromCode(modifiedOpcodeCode), varIndexLoad);

            case IINC:
                if (offset + 5 >= bytecode.length)
                {
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
