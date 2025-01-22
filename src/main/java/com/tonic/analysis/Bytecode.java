package com.tonic.analysis;

import com.tonic.analysis.instruction.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.*;
import com.tonic.utill.Logger;
import com.tonic.utill.ReturnType;
import lombok.Getter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A utility class for high-level bytecode manipulations.
 * It leverages the existing CodeWriter class to perform specific operations.
 */
@Getter
public class Bytecode {
    private final CodeWriter codeWriter;
    private final ConstPool constPool;

    // Mapping from label names to bytecode offsets
    private final Map<String, Integer> labels = new HashMap<>();

    /**
     * Constructs a Bytecode utility for the given MethodEntry.
     *
     * @param methodEntry The MethodEntry whose bytecode is to be manipulated.
     */
    public Bytecode(MethodEntry methodEntry) {
        this.codeWriter = new CodeWriter(methodEntry);
        this.constPool = codeWriter.getConstPool();
    }

    /**
     * Defines a label at the current end of the bytecode.
     *
     * @param labelName The name of the label.
     */
    public void defineLabel(String labelName) {
        int currentOffset = codeWriter.getBytecodeSize();
        labels.put(labelName, currentOffset);
        Logger.info("Defined label '" + labelName + "' at offset " + currentOffset);
    }

    /**
     * Appends an INVOKEVIRTUAL instruction to the end of the bytecode.
     *
     * @param methodRefIndex The index into the constant pool for the method reference.
     */
    public void addInvokeVirtual(int methodRefIndex) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertInvokeVirtual(offset, methodRefIndex);
        Logger.info("Appended INVOKEVIRTUAL at offset " + offset);
    }

    /**
     * Appends an INVOKESPECIAL instruction to the end of the bytecode.
     *
     * @param methodRefIndex The index into the constant pool for the method reference.
     */
    public void addInvokeSpecial(int methodRefIndex) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertInvokeSpecial(offset, methodRefIndex);
        Logger.info("Appended INVOKESPECIAL at offset " + offset);
    }

    /**
     * Appends an INVOKESTATIC instruction to the end of the bytecode.
     *
     * @param methodRefIndex The index into the constant pool for the method reference.
     */
    public void addInvokeStatic(int methodRefIndex) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertInvokeStatic(offset, methodRefIndex);
        Logger.info("Appended INVOKESTATIC at offset " + offset);
    }

    /**
     * Appends an INVOKEINTERFACE instruction to the end of the bytecode.
     *
     * @param interfaceMethodRefIndex The index into the constant pool for the interface method reference.
     * @param count                   The count of arguments for the interface method.
     */
    public void addInvokeInterface(int interfaceMethodRefIndex, int count) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertInvokeInterface(offset, interfaceMethodRefIndex, count);
        Logger.info("Appended INVOKEINTERFACE at offset " + offset);
    }

    /**
     * Appends an INVOKEDYNAMIC instruction to the end of the bytecode.
     *
     * @param bootstrapMethodIndex The bootstrap method index in the constant pool.
     * @param nameAndTypeIndex     The name and type index in the constant pool.
     */
    public void addInvokeDynamic(int bootstrapMethodIndex, int nameAndTypeIndex) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertInvokeDynamic(offset, bootstrapMethodIndex, nameAndTypeIndex);
        Logger.info("Appended INVOKEDYNAMIC at offset " + offset);
    }

    /**
     * Appends a PUTSTATIC instruction to the end of the bytecode.
     *
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public void addPutStatic(int fieldRefIndex) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertPutStatic(offset, fieldRefIndex);
        Logger.info("Appended PUTSTATIC at offset " + offset);
    }


    /**
     * Appends a GETSTATIC instruction to the end of the bytecode.
     *
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public void addGetStatic(int fieldRefIndex) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertGetStatic(offset, fieldRefIndex);
        Logger.info("Appended GETSTATIC at offset " + offset);
    }

    /**
     * Appends a PUTFIELD instruction to the end of the bytecode.
     *
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public void addPutField(int fieldRefIndex) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertPutField(offset, fieldRefIndex);
        Logger.info("Appended PUTFIELD at offset " + offset);
    }

    /**
     * Appends a GETFIELD instruction to the end of the bytecode.
     *
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public void addGetField(int fieldRefIndex) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertGetField(offset, fieldRefIndex);
        Logger.info("Appended GETFIELD at offset " + offset);
    }

    /**
     * Appends a NEW instruction to the end of the bytecode.
     *
     * @param classRefIndex The index into the constant pool for the class reference.
     */
    public void addNew(int classRefIndex) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertNew(offset, classRefIndex);
        Logger.info("Appended NEW at offset " + offset);
    }

    /**
     * Appends an ALOAD instruction to the end of the bytecode.
     *
     * @param index The local variable index to load from.
     */
    public void addALoad(int index) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertALoad(offset, index);
        Logger.info("Appended ALOAD at offset " + offset);
    }

    /**
     * Appends an ASTORE instruction to the end of the bytecode.
     *
     * @param index The local variable index to store into.
     */
    public void addAStore(int index) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertAStore(offset, index);
        Logger.info("Appended ASTORE at offset " + offset);
    }

    /**
     * Appends an ILOAD instruction to the end of the bytecode.
     *
     * @param index The local variable index to load from.
     */
    public void addILoad(int index) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertILoad(offset, index);
        Logger.info("Appended ILOAD at offset " + offset);
    }

    /**
     * Appends an ISTORE instruction to the end of the bytecode.
     *
     * @param index The local variable index to store into.
     */
    public void addIStore(int index) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertIStore(offset, index);
        Logger.info("Appended ISTORE at offset " + offset);
    }

    /**
     * Appends an ICONST instruction to push an integer constant onto the stack.
     *
     * @param value The integer value to push.
     */
    public void addIConst(int value) {
        Instruction instr;
        if (value >= -1 && value <= 5) {
            // ICONST_M1 to ICONST_5 (0x02 to 0x08)
            int opcode = 0x02 + (value + 1); // ICONST_M1 is 0x02
            instr = new IConstInstruction(opcode, codeWriter.getBytecodeSize(), value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            // BIPUSH (0x10)
            int opcode = 0x10;
            byte operand = (byte) value;
            instr = new BipushInstruction(opcode, codeWriter.getBytecodeSize(), operand);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            // SIPUSH (0x11)
            int opcode = 0x11;
            short operand = (short) value;
            instr = new SipushInstruction(opcode, codeWriter.getBytecodeSize(), operand);
        } else {
            // LDC (0x12) for integer constants
            int opcode = 0x12;
            IntegerItem intItem = constPool.findOrAddInteger(value);
            int ldcIndex = constPool.getIndexOf(intItem);
            instr = new LdcInstruction(constPool, opcode, codeWriter.getBytecodeSize(), ldcIndex);
        }
        codeWriter.appendInstruction(instr);
        Logger.info("Appended ICONST/IINC instruction with value " + value + " at offset " + (codeWriter.getBytecodeSize() - instr.getLength()));
    }

    /**
     * Appends a LCONST instruction to push a long constant onto the stack.
     *
     * @param value The long value to push.
     */
    public void addLConst(long value) {
        Instruction instr;
        if (value == 0L || value == 1L) {
            // LCONST_0 or LCONST_1 (0x09 or 0x0A)
            int opcode = (value == 0L) ? 0x09 : 0x0A;
            instr = new LConstInstruction(opcode, codeWriter.getBytecodeSize(), value);
        } else {
            // LDC2_W (0x14) for long constants
            int opcode = 0x14;
            LongItem longItem = constPool.findOrAddLong(value);
            int ldc2WIndex = constPool.getIndexOf(longItem);
            instr = new Ldc2WInstruction(constPool, opcode, codeWriter.getBytecodeSize(), ldc2WIndex);
        }
        codeWriter.appendInstruction(instr);
        Logger.info("Appended LCONST instruction with value " + value + " at offset " + (codeWriter.getBytecodeSize() - instr.getLength()));
    }

    /**
     * Appends a FCONST instruction to push a float constant onto the stack.
     *
     * @param value The float value to push.
     */
    public void addFConst(float value) {
        Instruction instr;
        if (value == 0.0f) {
            instr = new FConstInstruction(0x0B, codeWriter.getBytecodeSize(), 0.0f); // FCONST_0
        } else if (value == 1.0f) {
            instr = new FConstInstruction(0x0C, codeWriter.getBytecodeSize(), 1.0f); // FCONST_1
        } else if (value == 2.0f) {
            instr = new FConstInstruction(0x0D, codeWriter.getBytecodeSize(), 2.0f); // FCONST_2
        } else {
            // LDC (0x12) for float constants
            int opcode = 0x12;
            FloatItem floatItem = constPool.findOrAddFloat(value);
            int ldcIndex = constPool.getIndexOf(floatItem);
            instr = new LdcInstruction(constPool, opcode, codeWriter.getBytecodeSize(), ldcIndex);
        }
        codeWriter.appendInstruction(instr);
        Logger.info("Appended FCONST instruction with value " + value + " at offset " + (codeWriter.getBytecodeSize() - instr.getLength()));
    }

    /**
     * Appends a DCONST instruction to push a double constant onto the stack.
     *
     * @param value The double value to push.
     */
    public void addDConst(double value) {
        Instruction instr;
        if (value == 0.0d || value == 1.0d) {
            // DCONST_0 or DCONST_1 (0x0E or 0x0F)
            int opcode = (value == 0.0d) ? 0x0E : 0x0F;
            instr = new DConstInstruction(opcode, codeWriter.getBytecodeSize(), value);
        } else {
            // LDC2_W (0x14) for double constants
            int opcode = 0x14;
            DoubleItem doubleItem = constPool.findOrAddDouble(value);
            int ldc2WIndex = constPool.getIndexOf(doubleItem);
            instr = new Ldc2WInstruction(constPool, opcode, codeWriter.getBytecodeSize(), ldc2WIndex);
        }
        codeWriter.appendInstruction(instr);
        Logger.info("Appended DCONST instruction with value " + value + " at offset " + (codeWriter.getBytecodeSize() - instr.getLength()));
    }

    /**
     * Appends an ACONST_NULL instruction to push a null reference onto the stack.
     */
    public void addAConstNull() {
        int opcode = 0x01; // ACONST_NULL
        Instruction instr = new AConstNullInstruction(opcode, codeWriter.getBytecodeSize());
        codeWriter.appendInstruction(instr);
        Logger.info("Appended ACONST_NULL instruction at offset " + (codeWriter.getBytecodeSize() - instr.getLength()));
    }

    /**
     * Appends a LLOAD instruction to load a long from a local variable.
     *
     * @param index The local variable index to load from.
     */
    public void addLLoad(int index) {
        int opcode = 0x16; // LLOAD
        Instruction instr = new LLoadInstruction(opcode, codeWriter.getBytecodeSize(), index);
        codeWriter.appendInstruction(instr);
        Logger.info("Appended LLOAD instruction for index " + index + " at offset " + (codeWriter.getBytecodeSize() - instr.getLength()));
    }

    /**
     * Appends a FLOAD instruction to load a float from a local variable.
     *
     * @param index The local variable index to load from.
     */
    public void addFLoad(int index) {
        int opcode = 0x17; // FLOAD
        Instruction instr = new FLoadInstruction(opcode, codeWriter.getBytecodeSize(), index);
        codeWriter.appendInstruction(instr);
        Logger.info("Appended FLOAD instruction for index " + index + " at offset " + (codeWriter.getBytecodeSize() - instr.getLength()));
    }

    /**
     * Appends a DLOAD instruction to load a double from a local variable.
     *
     * @param index The local variable index to load from.
     */
    public void addDLoad(int index) {
        int opcode = 0x18; // DLOAD
        Instruction instr = new DLoadInstruction(opcode, codeWriter.getBytecodeSize(), index);
        codeWriter.appendInstruction(instr);
        Logger.info("Appended DLOAD instruction for index " + index + " at offset " + (codeWriter.getBytecodeSize() - instr.getLength()));
    }


    /**
     * Appends a GOTO instruction to the end of the bytecode.
     *
     * @param labelName The name of the label to jump to.
     */
    public void addGoto(String labelName) {
        // Define a label at the end to serve as the jump target
        defineLabel(labelName);
        int targetOffset = labels.get(labelName);

        // Calculate the branch offset relative to the GOTO instruction
        int gotoOffset = codeWriter.getBytecodeSize();
        short relativeOffset = (short) (targetOffset - (gotoOffset + 3)); // GOTO is 3 bytes long

        // Append the GOTO instruction
        GotoInstruction gotoInstr = new GotoInstruction(0xA7, gotoOffset, relativeOffset);
        codeWriter.appendInstruction(gotoInstr);
        Logger.info("Appended GOTO to label '" + labelName + "' at offset " + targetOffset);
    }

    /**
     * Appends a GOTO_W instruction to the end of the bytecode.
     *
     * @param labelName The name of the label to jump to.
     */
    public void addGotoW(String labelName) {
        // Define a label at the end to serve as the jump target
        defineLabel(labelName);
        int targetOffset = labels.get(labelName);

        // Calculate the branch offset relative to the GOTO_W instruction
        int gotoWOffset = codeWriter.getBytecodeSize();
        int relativeOffset = targetOffset - (gotoWOffset + 5); // GOTO_W is 5 bytes long

        // Append the GOTO_W instruction
        GotoInstruction gotoWInstr = new GotoInstruction(0xC8, gotoWOffset, relativeOffset);
        codeWriter.appendInstruction(gotoWInstr);
        Logger.info("Appended GOTO_W to label '" + labelName + "' at offset " + targetOffset);
    }

    /**
     * Appends an IINC instruction to the end of the bytecode.
     *
     * @param varIndex   The local variable index to increment.
     * @param increment  The constant by which to increment the variable.
     */
    public void addIInc(int varIndex, int increment) {
        int offset = codeWriter.getBytecodeSize();
        codeWriter.insertIInc(offset, varIndex, increment);
        Logger.info("Appended IINC at offset " + offset + " for variable " + varIndex + " with increment " + increment);
    }

    /**
     * Appends a RETURN instruction to the end of the bytecode.
     *
     * @param returnType The opcode of the RETURN instruction (e.g., 0xAC for IRETURN).
     */
    public void addReturn(ReturnType returnType) {
        addReturn(returnType.getOpcode());
    }

    /**
     * Appends a RETURN instruction to the end of the bytecode.
     *
     * @param returnOpcode The opcode of the RETURN instruction (e.g., 0xAC for IRETURN).
     */
    public void addReturn(int returnOpcode) {
        int offset = codeWriter.getBytecodeSize();
        ReturnInstruction returnInstr = new ReturnInstruction(returnOpcode, offset);
        codeWriter.appendInstruction(returnInstr);
        Logger.info("Appended RETURN (opcode 0x" + Integer.toHexString(returnOpcode) + ") at offset " + offset);
    }

    /**
     * Finalizes the bytecode modifications by writing them back to the MethodEntry.
     *
     * @throws IOException If an I/O error occurs during writing.
     */
    public void finalizeBytecode() throws IOException {
        codeWriter.write();
        Logger.info("Finalized bytecode modifications.");
    }
}
