package com.tonic.analysis;

import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.StackMapTableAttribute;
import com.tonic.analysis.instruction.*;
import com.tonic.utill.Logger;
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

    protected final Map<Integer, Instruction> instructions = new TreeMap<>();

    private int maxStack;
    private int maxLocals;

    private boolean modified = false;

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
    protected void parseBytecode() {
        instructions.clear();
        int offset = 0;
        boolean debugMode = false;
        while (offset < bytecode.length) {
            int opcode = Byte.toUnsignedInt(bytecode[offset]);
            Instruction instr = InstructionFactory.createInstruction(opcode, offset, bytecode, constPool);
            instructions.put(offset, instr);
            int instrLength = instr.getLength();
            if (instr instanceof LdcWInstruction) {
                LdcWInstruction ldc = (LdcWInstruction) instr;
                if (ldc.getCpIndex() <= 0) {
                    debugMode = true;
                    System.err.println("DEBUG: Invalid LDC_W at offset " + offset + " with cpIndex=" + ldc.getCpIndex());
                    System.err.println("DEBUG: Bytecode bytes at offset: " +
                        String.format("%02X %02X %02X", bytecode[offset],
                            offset+1 < bytecode.length ? bytecode[offset+1] : 0,
                            offset+2 < bytecode.length ? bytecode[offset+2] : 0));
                    System.err.println("DEBUG: First 30 bytes of method bytecode:");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(30, bytecode.length); i++) {
                        sb.append(String.format("%02X ", bytecode[i]));
                    }
                    System.err.println(sb.toString());
                    System.err.println("DEBUG: Previously parsed instructions:");
                    for (var entry : instructions.entrySet()) {
                        System.err.println("  offset " + entry.getKey() + ": " + entry.getValue().getClass().getSimpleName() + " length=" + entry.getValue().getLength());
                    }
                }
            }
            offset += instrLength;
        }
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

    /**
     * Inserts an instruction into the bytecode and updates blocks accordingly.
     *
     * @param offset   The bytecode offset to insert the instruction at.
     * @param newInstr The new Instruction to insert.
     */
    public void insertInstruction(int offset, Instruction newInstr) {
        if (!instructions.containsKey(offset) && offset != bytecode.length) {
            throw new IllegalArgumentException("Invalid bytecode offset: " + offset);
        }

        this.modified = true;

        Logger.info("Inserting instruction at offset: " + offset);
        Logger.info("Instruction to insert: " + newInstr);

        List<Map.Entry<Integer, Instruction>> entries = new ArrayList<>(instructions.entrySet());
        Map<Integer, Instruction> updatedInstructions = new TreeMap<>();
        boolean inserted = false;

        for (Map.Entry<Integer, Instruction> entry : entries) {
            int currentOffset = entry.getKey();
            Instruction instr = entry.getValue();

            if (currentOffset == offset) {
                updatedInstructions.put(offset, newInstr);
                updatedInstructions.put(offset + newInstr.getLength(), instr);
                inserted = true;
                Logger.info("Inserted new instruction at " + offset + ", shifted existing instruction to " + (offset + newInstr.getLength()));
            } else if (currentOffset > offset) {
                int newShiftedOffset = currentOffset + newInstr.getLength();
                updatedInstructions.put(newShiftedOffset, instr);
                Logger.info("Shifted instruction from " + currentOffset + " to " + newShiftedOffset);
            } else {
                updatedInstructions.put(currentOffset, instr);
            }
        }

        if (!inserted && offset == bytecode.length) {
            updatedInstructions.put(offset, newInstr);
            Logger.info("Appended instruction at end offset: " + offset);
        }

        if (entries.isEmpty() && offset == 0) {
            updatedInstructions.put(0, newInstr);
            Logger.info("Inserted instruction into empty bytecode at offset 0");
        }

        instructions.clear();
        instructions.putAll(updatedInstructions);

        rebuildBytecode();

        parseBytecode();
    }


    /**
     * Rebuilds the bytecode array from the current instructions.
     */
    protected void rebuildBytecode() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            for (Instruction instr : instructions.values()) {
                Logger.info("Writing instruction at offset " + instr.getOffset() + ": " + instr);
                instr.write(dos);
            }
            dos.flush();
            bytecode = baos.toByteArray();
            codeAttribute.setCode(bytecode);
            Logger.info("Rebuilt bytecode: " + Arrays.toString(bytecode));
        } catch (IOException e) {
            Logger.error("Failed to rebuild bytecode: " + e.getMessage());
        }
    }

    /**
     * Analyzes the current bytecode to update maxStack and maxLocals.
     * This is a simplified analysis and may not cover all cases.
     */
    public void analyze() {
        int currentStack = 0;
        int peakStack = 0;
        int currentLocals = maxLocals;

        for (Instruction instr : instructions.values()) {
            int stackChange = instr.getStackChange();
            currentStack += stackChange;
            if (currentStack > peakStack) {
                peakStack = currentStack;
            }

            if (instr.getLocalChange() > 0) {
                currentLocals += instr.getLocalChange();
                if (currentLocals > maxLocals) {
                    maxLocals = currentLocals;
                }
            }
        }

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

    /**
     * Returns whether the bytecode has been modified since loading.
     *
     * @return true if modified, false otherwise
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * Checks if the CodeAttribute has a valid StackMapTable.
     *
     * @return true if a StackMapTable exists with frames
     */
    public boolean hasValidStackMapTable() {
        for (var attr : codeAttribute.getAttributes()) {
            if (attr instanceof StackMapTableAttribute) {
                StackMapTableAttribute smt = (StackMapTableAttribute) attr;
                return smt.getFrames() != null && !smt.getFrames().isEmpty();
            }
        }
        return false;
    }

    /**
     * Computes and updates the StackMapTable frames for this method.
     * This is an opt-in operation - call this after making bytecode modifications.
     * <p>
     * If the bytecode hasn't been modified and a valid StackMapTable exists,
     * this method preserves the existing frames.
     * <p>
     * Usage:
     * <pre>
     * CodeWriter codeWriter = new CodeWriter(method);
     * // ... insert instructions ...
     * codeWriter.write();
     * codeWriter.computeFrames(); // Regenerate StackMapTable
     * </pre>
     */
    public void computeFrames() {
        if (!modified && hasValidStackMapTable()) {
            Logger.info("Preserving existing StackMapTable (bytecode not modified)");
            return;
        }

        Logger.info("Computing StackMapTable frames for method: " + methodEntry.getName());
        FrameGenerator generator = new FrameGenerator(constPool);
        generator.updateStackMapTable(methodEntry);
        Logger.info("StackMapTable computation complete");
    }

    public void forceComputeFrames() {
        Logger.info("Force computing StackMapTable frames for method: " + methodEntry.getName());
        FrameGenerator generator = new FrameGenerator(constPool);
        generator.updateStackMapTable(methodEntry);
        Logger.info("StackMapTable computation complete");
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
            Logger.info("Parsing opcode: 0x" + Integer.toHexString(opcode) + " at offset: " + offset);
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
                    int iconstValue = (opcode == 0x02) ? -1 : (opcode - 0x03);
                    return new IConstInstruction(opcode, offset, iconstValue);

                case 0x09:
                case 0x0A:
                    long lconstValue = (opcode == 0x09) ? 0L : 1L;
                    return new LConstInstruction(opcode, offset, lconstValue);

                case 0x0B:
                case 0x0C:
                case 0x0D:
                    float fconstValue = (opcode == 0x0B) ? 0.0f : ((opcode == 0x0C) ? 1.0f : 2.0f);
                    return new FConstInstruction(opcode, offset, fconstValue);

                case 0x0E:
                case 0x0F:
                    double dconstValue = (opcode == 0x0E) ? 0.0 : 1.0;
                    return new DConstInstruction(opcode, offset, dconstValue);

                case 0x10:
                    if (offset + 1 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    byte bipushValue = bytecode[offset + 1];
                    return new BipushInstruction(opcode, offset, bipushValue);

                case 0x11:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    short sipushValue = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                    return new SipushInstruction(opcode, offset, sipushValue);

                case 0x12:
                    if (offset + 1 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int ldcIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                    return new LdcInstruction(constPool, opcode, offset, ldcIndex);

                case 0x13:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int ldcWIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    Logger.info("LDC_W: cpIndex = " + ldcWIndex);
                    return new LdcWInstruction(constPool, opcode, offset, ldcWIndex);

                case 0x14:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int retVarIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                    return new RetInstruction(opcode, offset, retVarIndex);

                case 0xB6:
                case 0xB7:
                case 0xB8:
                    return parseInvokeInstruction(opcode, offset, bytecode, constPool);

                case 0xB9:
                    if (offset + 4 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int invokeInterfaceIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    int count = Byte.toUnsignedInt(bytecode[offset + 3]);
                    return new InvokeInterfaceInstruction(constPool, opcode, offset, invokeInterfaceIndex, count);

                case 0xBA:
                    if (offset + 4 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int invokedynamicCpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new InvokeDynamicInstruction(constPool, opcode, offset, invokedynamicCpIndex);

                case 0xB2:
                case 0xB4:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int fieldRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new GetFieldInstruction(constPool, opcode, offset, fieldRefIndex);

                case 0xB3:
                case 0xB5:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int putFieldRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new PutFieldInstruction(constPool, opcode, offset, putFieldRefIndex);

                case 0xBB:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int newClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new NewInstruction(constPool, opcode, offset, newClassIndex);

                case 0xBC:
                    if (offset + 1 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int newarrayTypeCode = Byte.toUnsignedInt(bytecode[offset + 1]);
                    return new NewArrayInstruction(opcode, offset, newarrayTypeCode, 1);

                case 0xBD:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int anewarrayClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new ANewArrayInstruction(constPool, opcode, offset, anewarrayClassIndex, 2);

                case 0xBE:
                    return new ArrayLengthInstruction(opcode, offset);

                case 0xBF:
                    return new ATHROWInstruction(opcode, offset);

                case 0xC0:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int typeIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new CheckCastInstruction(constPool, opcode, offset, typeIndex);

                case 0xC5:
                    if (offset + 3 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
            switch (opcode) {
                case 0x15:
                    return "ILOAD";
                case 0x16:
                    return "LLOAD";
                case 0x17:
                    return "FLOAD";
                case 0x18:
                    return "DLOAD";
                case 0x19:
                    return "ALOAD";
                default:
                    return "UNKNOWN_LOAD";
            }
        }

        /**
         * Helper method to get the instruction name based on opcode.
         *
         * @param opcode The opcode.
         * @return The name of the store instruction.
         */
        private static String getStoreInstructionName(int opcode) {
            switch (opcode) {
                case 0x36:
                    return "ISTORE";
                case 0x37:
                    return "LSTORE";
                case 0x38:
                    return "FSTORE";
                case 0x39:
                    return "DSTORE";
                case 0x3A:
                    return "ASTORE";
                default:
                    return "UNKNOWN_STORE";
            }
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
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
            if (opcode == 0xA7) {
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                }
                short branchOffset = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                return new GotoInstruction(opcode, offset, branchOffset);
            } else if (opcode == 0xC8) {
                if (offset + 4 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
            if (opcode == 0xA8) {
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                }
                short jsrOffset = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                return new JsrInstruction(opcode, offset, jsrOffset);
            } else if (opcode == 0xC9) {
                if (offset + 4 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
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
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }
            int methodRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
            switch (opcode) {
                case 0xB6:
                    return new InvokeVirtualInstruction(constPool, opcode, offset, methodRefIndex);
                case 0xB7:
                    return new InvokeSpecialInstruction(constPool, opcode, offset, methodRefIndex);
                case 0xB8:
                    return new InvokeStaticInstruction(constPool, opcode, offset, methodRefIndex);
                default:
                    return new UnknownInstruction(opcode, offset, 3);
            }
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
            int pairsLength = npairs * 8;

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
            if (offset + 1 >= bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }

            int modifiedOpcodeCode = Byte.toUnsignedInt(bytecode[offset + 1]);

            switch (modifiedOpcodeCode) {
                case 0x15:
                case 0x16:
                case 0x17:
                case 0x18:
                case 0x19:
                    if (offset + 3 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int varIndexLoad = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                    switch (modifiedOpcodeCode) {
                        case 0x15:
                            return new ILoadInstruction(opcode, offset, varIndexLoad);
                        case 0x16:
                            return new LLoadInstruction(opcode, offset, varIndexLoad);
                        case 0x17:
                            return new FLoadInstruction(opcode, offset, varIndexLoad);
                        case 0x18:
                            return new DLoadInstruction(opcode, offset, varIndexLoad);
                        case 0x19:
                            return new ALoadInstruction(opcode, offset, varIndexLoad);
                        default:
                            return new UnknownInstruction(opcode, offset, 4);
                    }

                case 0x36:
                case 0x37:
                case 0x38:
                case 0x39:
                case 0x3A:
                    if (offset + 3 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int varIndexStore = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                    switch (modifiedOpcodeCode) {
                        case 0x36:
                            return new IStoreInstruction(opcode, offset, varIndexStore);
                        case 0x37:
                            return new LStoreInstruction(opcode, offset, varIndexStore);
                        case 0x38:
                            return new FStoreInstruction(opcode, offset, varIndexStore);
                        case 0x39:
                            return new DStoreInstruction(opcode, offset, varIndexStore);
                        case 0x3A:
                            return new AStoreInstruction(opcode, offset, varIndexStore);
                        default:
                            return new UnknownInstruction(opcode, offset, 4);
                    }

                case 0x84:
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
        int opcode = 0xB6;
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
        int opcode = 0xB7;
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
        int opcode = 0xB8;
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
        int opcode = 0xB9;
        InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, opcode, offset, interfaceMethodRefIndex, count);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an INVOKEDYNAMIC instruction at the specified bytecode offset.
     *
     * @param offset  The bytecode offset to insert the instruction at.
     * @param cpIndex The constant pool index to the CONSTANT_InvokeDynamic_info entry.
     */
    public InvokeDynamicInstruction insertInvokeDynamic(int offset, int cpIndex) {
        int opcode = 0xBA;
        InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, opcode, offset, cpIndex);
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
        int opcode = 0x19;
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
        int opcode = 0x3A;
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
        int opcode = 0xB2;
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
        int opcode = 0xB4;
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
        int opcode = 0xB3;
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
        int opcode = 0xB5;
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
        int opcode = 0x15;
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
        int opcode = 0x36;
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
        int opcode = 0x16;
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
        int opcode = 0x37;
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
        int opcode = 0x17;
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
        int opcode = 0x38;
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
        int opcode = 0x18;
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
        int opcode = 0x39;
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
        int opcode = 0x84;
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
        int opcode = 0xA7;
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
        final int GOTO_W_OPCODE = 0xC8;

        GotoInstruction gotoWInstr = new GotoInstruction(GOTO_W_OPCODE, offset, branchOffset);

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
        int opcode = 0xBB;
        NewInstruction newInstr = new NewInstruction(constPool, opcode, offset, classRefIndex);
        insertInstruction(offset, newInstr);
        return newInstr;
    }

    /**
     * Inserts an LDC instruction at the specified bytecode offset.
     *
     * @param offset             The bytecode offset to insert the instruction at.
     * @param constantPoolIndex  The index into the constant pool for the constant.
     */
    public LdcInstruction insertLDC(int offset, int constantPoolIndex) {
        int opcode = 0x12;
        LdcInstruction ldcInstr = new LdcInstruction(constPool, opcode, offset, constantPoolIndex);
        insertInstruction(offset, ldcInstr);
        return ldcInstr;
    }

    /**
     * Inserts an LDC_W instruction at the specified bytecode offset.
     *
     * @param offset            The bytecode offset to insert the instruction at.
     * @param constantPoolIndex The 2-byte index into the constant pool for the constant.
     */
    public LdcWInstruction insertLDCW(int offset, int constantPoolIndex) {
        int opcode = 0x13;
        Logger.info("Inserting LDC_W at offset " + offset + " with index " + constantPoolIndex);
        LdcWInstruction ldcWInstr = new LdcWInstruction(constPool, opcode, offset, constantPoolIndex);
        insertInstruction(offset, ldcWInstr);
        return ldcWInstr;
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


    /**
     * Accepts a BytecodeVisitor to traverse and operate on the instructions.
     *
     * @param visitor The BytecodeVisitor implementation.
     */
    public void accept(AbstractBytecodeVisitor visitor) {
        for (Map.Entry<Integer, Instruction> entry : instructions.entrySet()) {
            Instruction instr = entry.getValue();
            instr.accept(visitor);
        }
    }

    /**
     * Lifts this method's bytecode to SSA-form IR.
     *
     * @return The IRMethod in SSA form
     */
    public IRMethod toSSA() {
        SSA ssa = new SSA(constPool);
        return ssa.lift(methodEntry);
    }

    /**
     * Lowers an SSA-form IRMethod back to bytecode and updates this method.
     *
     * @param irMethod The IRMethod to lower
     */
    public void fromSSA(IRMethod irMethod) {
        SSA ssa = new SSA(constPool);
        ssa.lower(irMethod, methodEntry);
        this.bytecode = codeAttribute.getCode();
        parseBytecode();
        this.modified = true;
    }

    /**
     * Lifts to SSA, runs standard optimizations, and lowers back to bytecode.
     */
    public void optimizeSSA() {
        SSA ssa = new SSA(constPool).withStandardOptimizations();
        IRMethod irMethod = ssa.lift(methodEntry);
        ssa.runTransforms(irMethod);
        ssa.lower(irMethod, methodEntry);
        this.bytecode = codeAttribute.getCode();
        parseBytecode();
        this.modified = true;
    }
}
