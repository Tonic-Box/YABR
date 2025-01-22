package com.tonic.analysis;

import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.analysis.instruction.*;
import com.tonic.utill.ReturnType;
import lombok.Getter;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * A class for analyzing and modifying the bytecode of a MethodEntry.
 * It allows iterating over bytecode instructions, inserting new instructions,
 * and automatically updating stack and local variable information.
 */
@Getter
public class CodeWriter {
    private final MethodEntry methodEntry;
    private final CodeAttribute codeAttribute;
    private byte[] bytecode;
    private final ConstPool constPool;

    // Mapping from bytecode offset to Instruction
    private final Map<Integer, Instruction> instructions = new TreeMap<>();

    // Current max stack and locals, updated as we modify bytecode
    private int maxStack;
    private int maxLocals;

    /**
     * Constructs a CodeWriter for the given MethodEntry.
     *
     * @param methodEntry The MethodEntry to manipulate.
     */
    public CodeWriter(MethodEntry methodEntry) {
        this.methodEntry = methodEntry;
        this.codeAttribute = methodEntry.getCodeAttribute();
        if (this.codeAttribute == null) {
            throw new IllegalArgumentException("MethodEntry does not contain a CodeAttribute.");
        }
        this.bytecode = this.codeAttribute.getCode();
        this.constPool = methodEntry.getClassFile().getConstPool();
        parseBytecode();
    }

    /**
     * Parses the bytecode into individual instructions.
     */
    private void parseBytecode() {
        instructions.clear();
        int offset = 0;
        while (offset < bytecode.length) {
            int opcode = Byte.toUnsignedInt(bytecode[offset]);
            Instruction instr = InstructionFactory.createInstruction(opcode, offset, bytecode, constPool);
            instructions.put(offset, instr);
            offset += instr.getLength();
        }
        // Initialize maxStack and maxLocals
        this.maxStack = codeAttribute.getMaxStack();
        this.maxLocals = codeAttribute.getMaxLocals();
    }

    /**
     * Iterates over all instructions.
     *
     * @return An Iterable of Instructions.
     */
    public Iterable<Instruction> getInstructions() {
        return instructions.values();
    }

    public int getInstructionCount()
    {
        return instructions.size();
    }

    public void insertInstruction(int offset, Instruction newInstr) {
        if (!instructions.containsKey(offset) && offset != bytecode.length) {
            throw new IllegalArgumentException("Invalid bytecode offset: " + offset);
        }

        List<Map.Entry<Integer, Instruction>> entries = new ArrayList<>(instructions.entrySet());
        Map<Integer, Instruction> updatedInstructions = new TreeMap<>();
        boolean inserted = false;

        for (Map.Entry<Integer, Instruction> entry : entries) {
            int currentOffset = entry.getKey();
            Instruction instr = entry.getValue();

            if (currentOffset == offset) {
                // Insert new instruction
                updatedInstructions.put(offset, newInstr);
                updatedInstructions.put(offset + newInstr.getLength(), instr);
                inserted = true;
            } else if (currentOffset > offset) {
                // Shift instructions after insertion point
                updatedInstructions.put(currentOffset + newInstr.getLength(), instr);
            } else {
                // Keep instructions before insertion point
                updatedInstructions.put(currentOffset, instr);
            }
        }

        if (!inserted && offset == bytecode.length) {
            // Append at the end
            updatedInstructions.put(offset, newInstr);
        }

        // Handle inserting into empty bytecode
        if (entries.isEmpty() && offset == 0) {
            updatedInstructions.put(0, newInstr);
        }

        instructions.clear();
        instructions.putAll(updatedInstructions);

        // Rebuild the bytecode
        rebuildBytecode();

        // Re-parse bytecode to update instruction mappings
        parseBytecode();
    }


    /**
     * Rebuilds the bytecode array from the current instructions.
     */
    private void rebuildBytecode() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            for (Instruction instr : instructions.values()) {
                instr.write(dos);
            }
            dos.flush();
            bytecode = baos.toByteArray();
            codeAttribute.setCode(bytecode);
        } catch (IOException e) {
            // Replace Logger with appropriate logging mechanism
            System.err.println("Failed to rebuild bytecode: " + e.getMessage());
        }
    }

    /**
     * Analyzes the current bytecode to update maxStack and maxLocals.
     * This is a simplified analysis and may not cover all cases.
     */
    public void analyze() {
        // Reset stack and locals tracking
        int currentStack = 0;
        int peakStack = 0;
        int currentLocals = maxLocals;

        for (Instruction instr : instructions.values()) {
            int stackChange = instr.getStackChange();
            currentStack += stackChange;
            if (currentStack > peakStack) {
                peakStack = currentStack;
            }

            // Update locals if needed
            if (instr.getLocalChange() > 0) {
                currentLocals += instr.getLocalChange();
                if (currentLocals > maxLocals) {
                    maxLocals = currentLocals;
                }
            }
        }

        // Update maxStack and maxLocals in CodeAttribute
        if (peakStack > maxStack) {
            maxStack = peakStack;
            codeAttribute.setMaxStack(maxStack);
        }
        if (currentLocals > maxLocals) {
            maxLocals = currentLocals;
            codeAttribute.setMaxLocals(maxLocals);
        }
    }

    /**
     * Writes the modified bytecode back to the MethodEntry.
     *
     * @throws IOException If an I/O error occurs.
     */
    public void write() throws IOException {
        analyze();
        rebuildBytecode();
    }

    public int getBytecodeSize()
    {
        return bytecode.length;
    }

    public boolean endsWithReturn()
    {
        if (bytecode.length < 1)
        {
            return false;
        }
        int lastOpcode = Byte.toUnsignedInt(bytecode[bytecode.length - 1]);
        return ReturnType.isReturnOpcode(lastOpcode);
    }

    /**
     * A factory for creating Instruction instances based on opcode.
     */
    public static class InstructionFactory {
        public static Instruction createInstruction(int opcode, int offset, byte[] bytecode, ConstPool constPool) {
            switch (opcode) {
                // Simple Instructions
                case 0x00: // NOP
                    return new NopInstruction(opcode, offset);

                case 0x01: // ACONST_NULL
                    return new AConstNullInstruction(opcode, offset);

                // ICONST_M1 to ICONST_5 (0x02 - 0x08)
                case 0x02: // ICONST_M1
                case 0x03: // ICONST_0
                case 0x04: // ICONST_1
                case 0x05: // ICONST_2
                case 0x06: // ICONST_3
                case 0x07: // ICONST_4
                case 0x08: // ICONST_5
                    int iconstValue = (opcode == 0x02) ? -1 : (opcode - 0x03);
                    return new IConstInstruction(opcode, offset, iconstValue);

                // LCONST_0 to LCONST_1 (0x09 - 0x0A)
                case 0x09: // LCONST_0
                case 0x0A: // LCONST_1
                    long lconstValue = (opcode == 0x09) ? 0L : 1L;
                    return new LConstInstruction(opcode, offset, lconstValue);

                // FCONST_0 to FCONST_2 (0x0B - 0x0D)
                case 0x0B: // FCONST_0
                case 0x0C: // FCONST_1
                case 0x0D: // FCONST_2
                    float fconstValue = (opcode == 0x0B) ? 0.0f : ((opcode == 0x0C) ? 1.0f : 2.0f);
                    return new FConstInstruction(opcode, offset, fconstValue);

                // DCONST_0 to DCONST_1 (0x0E - 0x0F)
                case 0x0E: // DCONST_0
                case 0x0F: // DCONST_1
                    double dconstValue = (opcode == 0x0E) ? 0.0 : 1.0;
                    return new DConstInstruction(opcode, offset, dconstValue);

                // BIPUSH (0x10)
                case 0x10: // BIPUSH
                    if (offset + 1 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    byte bipushValue = bytecode[offset + 1];
                    return new BipushInstruction(opcode, offset, bipushValue);

                // SIPUSH (0x11)
                case 0x11: // SIPUSH
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    short sipushValue = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                    return new SipushInstruction(opcode, offset, sipushValue);

                // LDC (0x12)
                case 0x12: // LDC
                    if (offset + 1 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int ldcIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                    return new LdcInstruction(constPool, opcode, offset, ldcIndex);

                // LDC_W (0x13)
                case 0x13: // LDC_W
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int ldcWIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new LdcWInstruction(constPool, opcode, offset, ldcWIndex);

                // LDC2_W (0x14)
                case 0x14: // LDC2_W
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int ldc2WIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new Ldc2WInstruction(constPool, opcode, offset, ldc2WIndex);

                // Load Instructions (ILOAD, LLOAD, FLOAD, DLOAD, ALOAD)
                case 0x15: // ILOAD
                case 0x16: // LLOAD
                case 0x17: // FLOAD
                case 0x18: // DLOAD
                case 0x19: // ALOAD
                    return createLoadInstruction(opcode, offset, bytecode, getLoadInstructionName(opcode));

                // Load Instructions with Indexed Opcodes (ILOAD_0 to ILOAD_3, etc.)
                case 0x1A: // ILOAD_0
                case 0x1B: // ILOAD_1
                case 0x1C: // ILOAD_2
                case 0x1D: // ILOAD_3
                    int iloadIndex = opcode - 0x1A;
                    return new ILoadInstruction(opcode, offset, iloadIndex);

                case 0x1E: // LLOAD_0
                case 0x1F: // LLOAD_1
                case 0x20: // LLOAD_2
                case 0x21: // LLOAD_3
                    int lloadIndex = opcode - 0x1E;
                    return new LLoadInstruction(opcode, offset, lloadIndex);

                case 0x22: // FLOAD_0
                case 0x23: // FLOAD_1
                case 0x24: // FLOAD_2
                case 0x25: // FLOAD_3
                    int floadIndex = opcode - 0x22;
                    return new FLoadInstruction(opcode, offset, floadIndex);

                case 0x26: // DLOAD_0
                case 0x27: // DLOAD_1
                case 0x28: // DLOAD_2
                case 0x29: // DLOAD_3
                    int dloadIndex = opcode - 0x26;
                    return new DLoadInstruction(opcode, offset, dloadIndex);

                case 0x2A: // ALOAD_0
                case 0x2B: // ALOAD_1
                case 0x2C: // ALOAD_2
                case 0x2D: // ALOAD_3
                    int aloadIndex = opcode - 0x2A;
                    return new ALoadInstruction(opcode, offset, aloadIndex);

                // Array Load Instructions
                case 0x2E: // IALOAD
                    return new IALoadInstruction(opcode, offset);

                case 0x2F: // LALOAD
                    return new LALoadInstruction(opcode, offset);

                case 0x30: // FALOAD
                    return new FALoadInstruction(opcode, offset);

                case 0x31: // DALOAD
                    return new DALoadInstruction(opcode, offset);

                case 0x32: // AALOAD
                    return new AALoadInstruction(opcode, offset);

                case 0x33: // BALOAD
                    return new BALOADInstruction(opcode, offset);

                case 0x34: // CALOAD
                    return new CALoadInstruction(opcode, offset);

                case 0x35: // SALOAD
                    return new SALoadInstruction(opcode, offset);

                // Store Instructions (ISTORE, LSTORE, FSTORE, DSTORE, ASTORE)
                case 0x36: // ISTORE
                case 0x37: // LSTORE
                case 0x38: // FSTORE
                case 0x39: // DSTORE
                case 0x3A: // ASTORE
                    return createStoreInstruction(opcode, offset, bytecode, getStoreInstructionName(opcode));

                // Store Instructions with Indexed Opcodes (ISTORE_0 to ISTORE_3, etc.)
                case 0x3B: // ISTORE_0
                case 0x3C: // ISTORE_1
                case 0x3D: // ISTORE_2
                case 0x3E: // ISTORE_3
                    int istoreIndex = opcode - 0x3B;
                    return new IStoreInstruction(opcode, offset, istoreIndex);

                case 0x3F: // LSTORE_0
                case 0x40: // LSTORE_1
                case 0x41: // LSTORE_2
                case 0x42: // LSTORE_3
                    int lstoreIndex = opcode - 0x3F;
                    return new LStoreInstruction(opcode, offset, lstoreIndex);

                case 0x43: // FSTORE_0
                case 0x44: // FSTORE_1
                case 0x45: // FSTORE_2
                case 0x46: // FSTORE_3
                    int fstoreIndex = opcode - 0x43;
                    return new FStoreInstruction(opcode, offset, fstoreIndex);

                case 0x47: // DSTORE_0
                case 0x48: // DSTORE_1
                case 0x49: // DSTORE_2
                case 0x4A: // DSTORE_3
                    int dstoreIndex = opcode - 0x47;
                    return new DStoreInstruction(opcode, offset, dstoreIndex);

                case 0x4B: // ASTORE_0
                case 0x4C: // ASTORE_1
                case 0x4D: // ASTORE_2
                case 0x4E: // ASTORE_3
                    int astoreIndex = opcode - 0x4B;
                    return new AStoreInstruction(opcode, offset, astoreIndex);

                // Array Store Instructions
                case 0x4F: // IASTORE
                    return new IASToreInstruction(opcode, offset);

                case 0x50: // LASTORE
                    return new LASToreInstruction(opcode, offset);

                case 0x51: // FASTORE
                    return new FASToreInstruction(opcode, offset);

                case 0x52: // DASTORE
                    return new DASToreInstruction(opcode, offset);

                case 0x53: // AASTORE
                    return new AASToreInstruction(opcode, offset);

                case 0x54: // BASTORE
                    return new BASToreInstruction(opcode, offset);

                case 0x55: // CASTORE
                    return new CASToreInstruction(opcode, offset);

                case 0x56: // SASTORE
                    return new SASToreInstruction(opcode, offset);

                // POP and its variants
                case 0x57: // POP
                    return new PopInstruction(opcode, offset);

                case 0x58: // POP2
                    return new Pop2Instruction(opcode, offset);

                // DUP and its variants
                case 0x59: // DUP
                case 0x5A: // DUP_X1
                case 0x5B: // DUP_X2
                case 0x5C: // DUP2
                case 0x5D: // DUP2_X1
                case 0x5E: // DUP2_X2
                    return new DupInstruction(opcode, offset);

                // SWAP
                case 0x5F: // SWAP
                    return new SwapInstruction(opcode, offset);

                // Arithmetic Instructions
                case 0x60: // IADD
                case 0x61: // LADD
                case 0x62: // FADD
                case 0x63: // DADD
                case 0x64: // ISUB
                case 0x65: // LSUB
                case 0x66: // FSUB
                case 0x67: // DSUB
                case 0x68: // IMUL
                case 0x69: // LMUL
                case 0x6A: // FMUL
                case 0x6B: // DMUL
                case 0x6C: // IDIV
                case 0x6D: // LDIV
                case 0x6E: // FDIV
                case 0x6F: // DDIV
                case 0x70: // IREM
                case 0x71: // LREM
                case 0x72: // FREM
                case 0x73: // DREM
                    return new ArithmeticInstruction(opcode, offset);

                case 0x74: // INEG
                    return new INegInstruction(opcode, offset);

                case 0x75: // LNEG
                    return new LNegInstruction(opcode, offset);

                case 0x76: // FNEG
                    return new FNegInstruction(opcode, offset);

                case 0x77: // DNEG
                    return new DNegInstruction(opcode, offset);

                case 0x78: // ISHL
                case 0x79: // LSHL
                case 0x7A: // ISHR
                case 0x7B: // LSHR
                case 0x7C: // IUSHR
                case 0x7D: // LUSHR
                    return new ArithmeticShiftInstruction(opcode, offset);

                case 0x7E: // IAND
                case 0x7F: // LAND
                    return new IAndInstruction(opcode, offset);

                case 0x80: // IOR
                case 0x81: // LOR
                    return new IOrInstruction(opcode, offset);

                case 0x82: // IXOR
                case 0x83: // LXOR
                    return new IXorInstruction(opcode, offset);

                // IINC (0x84)
                case 0x84: // IINC
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int iincVarIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                    int iincConst = bytecode[offset + 2];
                    return new IIncInstruction(opcode, offset, iincVarIndex, iincConst);

                // Conversion Instructions (I2L, I2F, etc.)
                case 0x85: // I2L
                    return new I2LInstruction(opcode, offset);

                case 0x86: // I2F
                case 0x87: // I2D
                case 0x88: // L2I
                case 0x89: // L2F
                case 0x8A: // L2D
                case 0x8B: // F2I
                case 0x8C: // F2L
                case 0x8D: // F2D
                case 0x8E: // D2I
                case 0x8F: // D2L
                case 0x90: // D2F
                    return new ConversionInstruction(opcode, offset);

                // Narrowing Conversion Instructions (I2B, I2C, I2S)
                case 0x91: // I2B
                case 0x92: // I2C
                case 0x93: // I2S
                    return new NarrowingConversionInstruction(opcode, offset);

                // Compare Instructions (LCMP, FCMPL, etc.)
                case 0x94: // LCMP
                case 0x95: // FCMPL
                case 0x96: // FCMPG
                case 0x97: // DCMPL
                case 0x98: // DCMPG
                    return new CompareInstruction(opcode, offset);

                // Conditional Branch Instructions (IFEQ - IF_ACMPNE)
                case 0x99: // IFEQ
                case 0x9A: // IFNE
                case 0x9B: // IFLT
                case 0x9C: // IFGE
                case 0x9D: // IFGT
                case 0x9E: // IFLE
                case 0x9F: // IF_ICMPEQ
                case 0xA0: // IF_ICMPNE
                case 0xA1: // IF_ICMPLT
                case 0xA2: // IF_ICMPGE
                case 0xA3: // IF_ICMPGT
                case 0xA4: // IF_ICMPLE
                case 0xA5: // IF_ACMPEQ
                case 0xA6: // IF_ACMPNE
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    // **Corrected: Interpret branchOffset as signed**
                    short branchOffsetCond = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                    return new ConditionalBranchInstruction(opcode, offset, branchOffsetCond);

                // GOTO (0xA7) and GOTO_W (0xC8)
                case 0xA7: // GOTO
                case 0xC8: // GOTO_W
                    return parseGotoInstruction(opcode, offset, bytecode);

                // JSR (0xA8) and JSR_W (0xC9)
                case 0xA8: // JSR
                case 0xC9: // JSR_W
                    return parseJsrInstruction(opcode, offset, bytecode);

                // RET (0xA9)
                case 0xA9: // RET
                    if (offset + 1 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int retVarIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                    return new RetInstruction(opcode, offset, retVarIndex);

                // INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC (0xB6 - 0xB8)
                case 0xB6: // INVOKEVIRTUAL
                case 0xB7: // INVOKESPECIAL
                case 0xB8: // INVOKESTATIC
                    return parseInvokeInstruction(opcode, offset, bytecode, constPool);

                // INVOKEINTERFACE (0xB9)
                case 0xB9: // INVOKEINTERFACE
                    if (offset + 4 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int invokeInterfaceIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    int count = Byte.toUnsignedInt(bytecode[offset + 3]);
                    // The fourth byte should be zero as per JVM spec
                    return new InvokeInterfaceInstruction(constPool, opcode, offset, invokeInterfaceIndex, count);

                // INVOKEDYNAMIC (0xBA)
                case 0xBA: // INVOKEDYNAMIC
                    if (offset + 4 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int invokedynamicBootstrapIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    int invokedynamicNameAndTypeIndex = ((bytecode[offset + 3] & 0xFF) << 8) | (bytecode[offset + 4] & 0xFF);
                    return new InvokeDynamicInstruction(constPool, opcode, offset, invokedynamicBootstrapIndex, invokedynamicNameAndTypeIndex);

                // GETSTATIC (0xB2) and GETFIELD (0xB4)
                case 0xB2: // GETSTATIC
                case 0xB4: // GETFIELD
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int fieldRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new GetFieldInstruction(constPool, opcode, offset, fieldRefIndex);

                // PUTSTATIC (0xB3) and PUTFIELD (0xB5)
                case 0xB3: // PUTSTATIC
                case 0xB5: // PUTFIELD
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int putFieldRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new PutFieldInstruction(constPool, opcode, offset, putFieldRefIndex);

                // NEW (0xBB)
                case 0xBB: // NEW
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int newClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new NewInstruction(constPool, opcode, offset, newClassIndex);

                // NEWARRAY (0xBC)
                case 0xBC: // NEWARRAY
                    if (offset + 1 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int newarrayTypeCode = Byte.toUnsignedInt(bytecode[offset + 1]);
                    return new NewArrayInstruction(opcode, offset, newarrayTypeCode, 1);

                // ANEWARRAY (0xBD)
                case 0xBD: // ANEWARRAY
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int anewarrayClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new ANewArrayInstruction(constPool, opcode, offset, anewarrayClassIndex, 2);

                // ARRAYLENGTH (0xBE)
                case 0xBE: // ARRAYLENGTH
                    return new ArrayLengthInstruction(opcode, offset);

                // ATHROW (0xBF)
                case 0xBF: // ATHROW
                    return new ATHROWInstruction(opcode, offset);

                case 0xC0: // CHECKCAST
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int typeIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new CheckCastInstruction(constPool, opcode, offset, typeIndex);

                // MULTIANEWARRAY (0xC5)
                case 0xC5: // MULTIANEWARRAY
                    if (offset + 3 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int multianewarrayClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    int dimensions = Byte.toUnsignedInt(bytecode[offset + 3]);
                    return new MultiANewArrayInstruction(constPool, opcode, offset, multianewarrayClassIndex, dimensions);

                // LOOKUPSWITCH (0xAB)
                case 0xAB: // LOOKUPSWITCH
                    return parseLookupSwitchInstruction(opcode, offset, bytecode);

                // TABLESWITCH (0xAA)
                case 0xAA: // TABLESWITCH
                    return parseTableSwitchInstruction(opcode, offset, bytecode);

                // WIDE (0xC4)
                case 0xC4: // WIDE
                    return parseWideInstruction(opcode, offset, bytecode);

                // RETURN instructions (0xAC - 0xB1)
                case 0xAC: // IRETURN
                case 0xAD: // LRETURN
                case 0xAE: // FRETURN
                case 0xAF: // DRETURN
                case 0xB0: // ARETURN
                case 0xB1: // RETURN
                    return new ReturnInstruction(opcode, offset);

                // UNKNOWN or UNIMPLEMENTED OPCODES
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
            return switch (opcode) {
                case 0x15 -> "ILOAD";
                case 0x16 -> "LLOAD";
                case 0x17 -> "FLOAD";
                case 0x18 -> "DLOAD";
                case 0x19 -> "ALOAD";
                default -> "UNKNOWN_LOAD";
            };
        }

        /**
         * Helper method to get the instruction name based on opcode.
         *
         * @param opcode The opcode.
         * @return The name of the store instruction.
         */
        private static String getStoreInstructionName(int opcode) {
            return switch (opcode) {
                case 0x36 -> "ISTORE";
                case 0x37 -> "LSTORE";
                case 0x38 -> "FSTORE";
                case 0x39 -> "DSTORE";
                case 0x3A -> "ASTORE";
                default -> "UNKNOWN_STORE";
            };
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
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }
            int varIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
            return switch (instructionName) {
                case "ILOAD" -> new ILoadInstruction(opcode, offset, varIndex);
                case "LLOAD" -> new LLoadInstruction(opcode, offset, varIndex);
                case "FLOAD" -> new FLoadInstruction(opcode, offset, varIndex);
                case "DLOAD" -> new DLoadInstruction(opcode, offset, varIndex);
                case "ALOAD" -> new ALoadInstruction(opcode, offset, varIndex);
                default -> new UnknownInstruction(opcode, offset, operandBytes + 1);
            };
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
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }
            int varIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
            return switch (instructionName) {
                case "ISTORE" -> new IStoreInstruction(opcode, offset, varIndex);
                case "LSTORE" -> new LStoreInstruction(opcode, offset, varIndex);
                case "FSTORE" -> new FStoreInstruction(opcode, offset, varIndex);
                case "DSTORE" -> new DStoreInstruction(opcode, offset, varIndex);
                case "ASTORE" -> new AStoreInstruction(opcode, offset, varIndex);
                default -> new UnknownInstruction(opcode, offset, operandBytes + 1);
            };
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
            if (opcode == 0xA7) { // GOTO
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                }
                // **Corrected: Interpret branchOffset as signed short**
                short branchOffset = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                return new GotoInstruction(opcode, offset, branchOffset);
            } else if (opcode == 0xC8) { // GOTO_W
                if (offset + 4 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                }
                // **Interpret branchOffset as signed int**
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
            if (opcode == 0xA8) { // JSR
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                }
                // **Corrected: Interpret jsrOffset as signed short**
                short jsrOffset = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                return new JsrInstruction(opcode, offset, jsrOffset);
            } else if (opcode == 0xC9) { // JSR_W
                if (offset + 4 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                }
                // **Interpret jsrOffset as signed int**
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
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }
            int methodRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
            return switch (opcode) {
                case 0xB6 -> new InvokeVirtualInstruction(constPool, opcode, offset, methodRefIndex);
                case 0xB7 -> new InvokeSpecialInstruction(constPool, opcode, offset, methodRefIndex);
                case 0xB8 -> new InvokeStaticInstruction(constPool, opcode, offset, methodRefIndex);
                default -> new UnknownInstruction(opcode, offset, 3);
            };
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
            // LOOKUPSWITCH alignment: after opcode, padding to reach a 4-byte boundary
            int padding = (4 - ((offset + 1) % 4)) % 4;
            int defaultOffsetPos = offset + 1 + padding;
            int npairsPos = defaultOffsetPos + 4;

            if (npairsPos + 4 > bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
            int pairsLength = npairs * 8; // Each pair has 4 bytes key and 4 bytes offset

            if (pairsStart + pairsLength > bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
            // TABLESWITCH alignment: after opcode, padding to reach a 4-byte boundary
            int padding = (4 - ((offset + 1) % 4)) % 4;
            int defaultOffsetPos = offset + 1 + padding;
            int lowPos = defaultOffsetPos + 4;
            int highPos = lowPos + 4;

            if (highPos + 4 > bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
            // Ensure there is at least one more byte for the modified opcode
            if (offset + 1 >= bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }

            // Read the modified opcode
            int modifiedOpcodeCode = Byte.toUnsignedInt(bytecode[offset + 1]);

            switch (modifiedOpcodeCode) {
                case 0x15: // ILOAD
                case 0x16: // LLOAD
                case 0x17: // FLOAD
                case 0x18: // DLOAD
                case 0x19: // ALOAD
                    if (offset + 3 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int varIndexLoad = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                    return switch (modifiedOpcodeCode) {
                        case 0x15 -> new ILoadInstruction(opcode, offset, varIndexLoad);
                        case 0x16 -> new LLoadInstruction(opcode, offset, varIndexLoad);
                        case 0x17 -> new FLoadInstruction(opcode, offset, varIndexLoad);
                        case 0x18 -> new DLoadInstruction(opcode, offset, varIndexLoad);
                        case 0x19 -> new ALoadInstruction(opcode, offset, varIndexLoad);
                        default -> new UnknownInstruction(opcode, offset, 4);
                    };

                case 0x36: // ISTORE
                case 0x37: // LSTORE
                case 0x38: // FSTORE
                case 0x39: // DSTORE
                case 0x3A: // ASTORE
                    if (offset + 3 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int varIndexStore = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                    return switch (modifiedOpcodeCode) {
                        case 0x36 -> new IStoreInstruction(opcode, offset, varIndexStore);
                        case 0x37 -> new LStoreInstruction(opcode, offset, varIndexStore);
                        case 0x38 -> new FStoreInstruction(opcode, offset, varIndexStore);
                        case 0x39 -> new DStoreInstruction(opcode, offset, varIndexStore);
                        case 0x3A -> new AStoreInstruction(opcode, offset, varIndexStore);
                        default -> new UnknownInstruction(opcode, offset, 4);
                    };

                case 0x84: // IINC
                    if (offset + 5 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int wideVarIndex = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                    int wideConstValue = ((bytecode[offset + 4] & 0xFF) << 8) | (bytecode[offset + 5] & 0xFF);
                    return new WideIIncInstruction(opcode, offset, wideVarIndex, wideConstValue);

                default:
                    return new UnknownInstruction(opcode, offset, 2);
            }
        }
    }

    /**
     * Inserts an INVOKEVIRTUAL instruction at the specified bytecode offset.
     *
     * @param offset          The bytecode offset to insert the instruction at.
     * @param methodRefIndex  The index into the constant pool for the method reference.
     */
    public InvokeVirtualInstruction insertInvokeVirtual(int offset, int methodRefIndex) {
        int opcode = 0xB6; // INVOKEVIRTUAL
        InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, opcode, offset, methodRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an INVOKESPECIAL instruction at the specified bytecode offset.
     *
     * @param offset          The bytecode offset to insert the instruction at.
     * @param methodRefIndex  The index into the constant pool for the method reference.
     */
    public InvokeSpecialInstruction insertInvokeSpecial(int offset, int methodRefIndex) {
        int opcode = 0xB7; // INVOKESPECIAL
        InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, opcode, offset, methodRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an INVOKESTATIC instruction at the specified bytecode offset.
     *
     * @param offset          The bytecode offset to insert the instruction at.
     * @param methodRefIndex  The index into the constant pool for the method reference.
     */
    public InvokeStaticInstruction insertInvokeStatic(int offset, int methodRefIndex) {
        int opcode = 0xB8; // INVOKESTATIC
        InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, opcode, offset, methodRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an INVOKEINTERFACE instruction at the specified bytecode offset.
     *
     * @param offset                  The bytecode offset to insert the instruction at.
     * @param interfaceMethodRefIndex The index into the constant pool for the interface method reference.
     * @param count                   The count of arguments for the interface method.
     */
    public InvokeInterfaceInstruction insertInvokeInterface(int offset, int interfaceMethodRefIndex, int count) {
        int opcode = 0xB9; // INVOKEINTERFACE
        InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, opcode, offset, interfaceMethodRefIndex, count);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an INVOKEDYNAMIC instruction at the specified bytecode offset.
     *
     * @param offset                  The bytecode offset to insert the instruction at.
     * @param bootstrapMethodIndex    The bootstrap method index in the constant pool.
     * @param nameAndTypeIndex        The name and type index in the constant pool.
     */
    public InvokeDynamicInstruction insertInvokeDynamic(int offset, int bootstrapMethodIndex, int nameAndTypeIndex) {
        int opcode = 0xBA; // INVOKEDYNAMIC
        InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, opcode, offset, bootstrapMethodIndex, nameAndTypeIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an ALOAD instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to load from.
     */
    public ALoadInstruction insertALoad(int offset, int index) {
        int opcode = 0x19; // ALOAD
        ALoadInstruction instr = new ALoadInstruction(opcode, offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an ASTORE instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to store into.
     */
    public AStoreInstruction insertAStore(int offset, int index) {
        int opcode = 0x3A; // ASTORE
        AStoreInstruction instr = new AStoreInstruction(opcode, offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a GETSTATIC instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public GetFieldInstruction insertGetStatic(int offset, int fieldRefIndex) {
        int opcode = 0xB2; // GETSTATIC
        GetFieldInstruction instr = new GetFieldInstruction(constPool, opcode, offset, fieldRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a GETFIELD instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public GetFieldInstruction insertGetField(int offset, int fieldRefIndex) {
        int opcode = 0xB4; // GETFIELD
        GetFieldInstruction instr = new GetFieldInstruction(constPool, opcode, offset, fieldRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a PUTSTATIC instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public PutFieldInstruction insertPutStatic(int offset, int fieldRefIndex) {
        int opcode = 0xB3; // PUTSTATIC
        PutFieldInstruction instr = new PutFieldInstruction(constPool, opcode, offset, fieldRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a PUTFIELD instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public PutFieldInstruction insertPutField(int offset, int fieldRefIndex) {
        int opcode = 0xB5; // PUTFIELD
        PutFieldInstruction instr = new PutFieldInstruction(constPool, opcode, offset, fieldRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an ILOAD instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to load from.
     */
    public ILoadInstruction insertILoad(int offset, int index) {
        int opcode = 0x15; // ILOAD
        ILoadInstruction instr = new ILoadInstruction(opcode, offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an ISTORE instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to store into.
     */
    public IStoreInstruction insertIStore(int offset, int index) {
        int opcode = 0x36; // ISTORE
        IStoreInstruction instr = new IStoreInstruction(opcode, offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an LLOAD instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to load from.
     */
    public LLoadInstruction insertLLoad(int offset, int index) {
        int opcode = 0x16; // LLOAD
        LLoadInstruction instr = new LLoadInstruction(opcode, offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an LSTORE instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to store into.
     */
    public LStoreInstruction insertLStore(int offset, int index) {
        int opcode = 0x37; // LSTORE
        LStoreInstruction instr = new LStoreInstruction(opcode, offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an FLOAD instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to load from.
     */
    public FLoadInstruction insertFLoad(int offset, int index) {
        int opcode = 0x17; // FLOAD
        FLoadInstruction instr = new FLoadInstruction(opcode, offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an FSTORE instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to store into.
     */
    public FStoreInstruction insertFStore(int offset, int index) {
        int opcode = 0x38; // FSTORE
        FStoreInstruction instr = new FStoreInstruction(opcode, offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a DLOAD instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to load from.
     */
    public DLoadInstruction insertDLoad(int offset, int index) {
        int opcode = 0x18; // DLOAD
        DLoadInstruction instr = new DLoadInstruction(opcode, offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a DSTORE instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to store into.
     */
    public DStoreInstruction insertDStore(int offset, int index) {
        int opcode = 0x39; // DSTORE
        DStoreInstruction instr = new DStoreInstruction(opcode, offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an IINC instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param varIndex The local variable index to increment.
     * @param increment The constant by which to increment the variable.
     */
    public IIncInstruction insertIInc(int offset, int varIndex, int increment) {
        int opcode = 0x84; // IINC
        IIncInstruction instr = new IIncInstruction(opcode, offset, varIndex, increment);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a GOTO instruction at the specified bytecode offset with a signed short branch offset.
     *
     * @param offset       The bytecode offset to insert the GOTO instruction at.
     * @param branchOffset The signed short branch offset relative to the GOTO instruction.
     */
    public GotoInstruction insertGoto(int offset, short branchOffset) {
        int opcode = 0xA7; // GOTO opcode
        GotoInstruction gotoInstr = new GotoInstruction(opcode, offset, branchOffset);
        insertInstruction(offset, gotoInstr);
        return gotoInstr;
    }

    /**
     * Inserts a GOTO_W instruction at the specified bytecode offset with a 32-bit branch offset.
     *
     * @param offset       The bytecode offset to insert the GOTO_W instruction at.
     * @param branchOffset The signed 32-bit branch offset relative to the GOTO_W instruction.
     * @throws IllegalArgumentException If the specified offset is invalid.
     */
    public GotoInstruction insertGotoW(int offset, int branchOffset) {
        // Opcode for GOTO_W
        final int GOTO_W_OPCODE = 0xC8;

        // Create a new GOTO_W instruction
        GotoInstruction gotoWInstr = new GotoInstruction(GOTO_W_OPCODE, offset, branchOffset);

        // Insert the GOTO_W instruction at the specified offset
        insertInstruction(offset, gotoWInstr);
        return gotoWInstr;
    }

    /**
     * Inserts a NEW instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param classRefIndex The index into the constant pool for the class reference.
     */
    public NewInstruction insertNew(int offset, int classRefIndex) {
        int opcode = 0xBB; // NEW opcode
        NewInstruction newInstr = new NewInstruction(constPool, opcode, offset, classRefIndex);
        insertInstruction(offset, newInstr);
        return newInstr;
    }

    /**
     * Appends a new instruction to the end of the bytecode.
     *
     * @param newInstr The Instruction to append.
     */
    public void appendInstruction(Instruction newInstr) {
        int appendOffset = bytecode.length;
        instructions.put(appendOffset, newInstr);
        rebuildBytecode();
        parseBytecode();
    }
}
