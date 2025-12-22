package com.tonic.analysis;

import com.tonic.analysis.instruction.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.*;
import com.tonic.utill.Logger;
import com.tonic.utill.Modifiers;
import com.tonic.utill.ReturnType;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * High-level bytecode manipulation API.
 * Provides convenient methods for inserting common bytecode instructions into a method.
 */
@Getter
public class Bytecode {
    private final CodeWriter codeWriter;
    private final ConstPool constPool;
    private boolean insertBefore = false;
    @Setter
    private int insertBeforeOffset = 0;
    private final boolean isStatic;

    private final Map<String, Integer> labels = new HashMap<>();

    /**
     * Constructs a Bytecode utility for the given MethodEntry.
     *
     * @param methodEntry The MethodEntry whose bytecode is to be manipulated.
     */
    public Bytecode(MethodEntry methodEntry) {
        this.codeWriter = new CodeWriter(methodEntry);
        this.constPool = codeWriter.getConstPool();
        this.isStatic = Modifiers.isStatic(methodEntry.getAccess());
    }

    /**
     * Constructs a Bytecode utility for the given CodeWriter.
     *
     * @param codeWriter The CodeWriter instance to use for bytecode manipulation.
     */
    public Bytecode(CodeWriter codeWriter) {
        this.codeWriter = codeWriter;
        this.constPool = codeWriter.getConstPool();
        this.isStatic = Modifiers.isStatic(codeWriter.getMethodEntry().getAccess());
    }

    /**
     * Sets whether instructions should be inserted before the current position.
     *
     * @param insertBefore True to insert before the current position, false to append.
     */
    public void setInsertBefore(boolean insertBefore)
    {
        this.insertBefore = insertBefore;
        this.insertBeforeOffset = processOffsetForInsertBeforeBase();
    }

    /**
     * Defines a label at the current end of the bytecode.
     *
     * @param labelName The name of the label.
     */
    public void defineLabel(String labelName) {
        int currentOffset = processOffset();
        labels.put(labelName, currentOffset);
        Logger.info("Defined label '" + labelName + "' at offset " + currentOffset);
    }

    /**
     * Appends an INVOKEVIRTUAL instruction to the end of the bytecode.
     *
     * @param methodRefIndex The index into the constant pool for the method reference.
     */
    public void addInvokeVirtual(int methodRefIndex) {
        int offset = processOffset();
        codeWriter.insertInvokeVirtual(offset, methodRefIndex);
        insertBeforeOffset += 3;
        Logger.info("Appended INVOKEVIRTUAL at offset " + offset);
    }

    /**
     * Appends an INVOKESPECIAL instruction to the end of the bytecode.
     *
     * @param methodRefIndex The index into the constant pool for the method reference.
     */
    public void addInvokeSpecial(int methodRefIndex) {
        int offset = processOffset();
        codeWriter.insertInvokeSpecial(offset, methodRefIndex);
        insertBeforeOffset += 3;
        Logger.info("Appended INVOKESPECIAL at offset " + offset);
    }

    /**
     * Appends an INVOKESTATIC instruction to the end of the bytecode.
     *
     * @param methodRefIndex The index into the constant pool for the method reference.
     */
    public void addInvokeStatic(int methodRefIndex) {
        int offset = processOffset();
        codeWriter.insertInvokeStatic(offset, methodRefIndex);
        insertBeforeOffset += 3;
        Logger.info("Appended INVOKESTATIC at offset " + offset);
    }

    /**
     * Appends an INVOKEINTERFACE instruction to the end of the bytecode.
     *
     * @param interfaceMethodRefIndex The index into the constant pool for the interface method reference.
     * @param count                   The count of arguments for the interface method.
     */
    public void addInvokeInterface(int interfaceMethodRefIndex, int count) {
        int offset = processOffset();
        codeWriter.insertInvokeInterface(offset, interfaceMethodRefIndex, count);
        insertBeforeOffset += 5;
        Logger.info("Appended INVOKEINTERFACE at offset " + offset);
    }

    /**
     * Appends an INVOKEDYNAMIC instruction to the end of the bytecode.
     *
     * @param cpIndex The constant pool index to the CONSTANT_InvokeDynamic_info entry.
     */
    public void addInvokeDynamic(int cpIndex) {
        int offset = processOffset();
        codeWriter.insertInvokeDynamic(offset, cpIndex);
        insertBeforeOffset += 5;
        Logger.info("Appended INVOKEDYNAMIC at offset " + offset);
    }

    /**
     * Appends a PUTSTATIC instruction to the end of the bytecode.
     *
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public void addPutStatic(int fieldRefIndex) {
        int offset = processOffset();
        codeWriter.insertPutStatic(offset, fieldRefIndex);
        insertBeforeOffset += 3;
        Logger.info("Appended PUTSTATIC at offset " + offset);
    }


    /**
     * Appends a GETSTATIC instruction to the end of the bytecode.
     *
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public void addGetStatic(int fieldRefIndex) {
        int offset = processOffset();
        codeWriter.insertGetStatic(offset, fieldRefIndex);
        insertBeforeOffset += 3;
        Logger.info("Appended GETSTATIC at offset " + offset);
    }

    /**
     * Appends a PUTFIELD instruction to the end of the bytecode.
     *
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public void addPutField(int fieldRefIndex) {
        int offset = processOffset();
        codeWriter.insertPutField(offset, fieldRefIndex);
        insertBeforeOffset += 3;
        Logger.info("Appended PUTFIELD at offset " + offset);
    }

    /**
     * Appends a GETFIELD instruction to the end of the bytecode.
     *
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public void addGetField(int fieldRefIndex) {
        int offset = processOffset();
        codeWriter.insertGetField(offset, fieldRefIndex);
        insertBeforeOffset += 3;
        Logger.info("Appended GETFIELD at offset " + offset);
    }

    /**
     * Appends a NEW instruction to the end of the bytecode.
     *
     * @param classRefIndex The index into the constant pool for the class reference.
     */
    public void addNew(int classRefIndex) {
        int offset = processOffset();
        codeWriter.insertNew(offset, classRefIndex);
        insertBeforeOffset += 3;
        Logger.info("Appended NEW at offset " + offset);
    }

    /**
     * Appends an ALOAD instruction to the end of the bytecode.
     *
     * @param index The local variable index to load from.
     */
    public void addALoad(int index) {
        int offset = processOffset();
        Instruction instruction = codeWriter.insertALoad(offset, index);
        insertBeforeOffset += instruction.getLength();
        Logger.info("Appended ALOAD at offset " + offset);
    }

    /**
     * Appends an ASTORE instruction to the end of the bytecode.
     *
     * @param index The local variable index to store into.
     */
    public void addAStore(int index) {
        int offset = processOffset();
        Instruction instruction = codeWriter.insertAStore(offset, index);
        insertBeforeOffset += instruction.getLength();
        Logger.info("Appended ASTORE at offset " + offset);
    }

    /**
     * Appends an ILOAD instruction to the end of the bytecode.
     *
     * @param index The local variable index to load from.
     */
    public void addILoad(int index) {
        int offset = processOffset();
        Instruction instruction = codeWriter.insertILoad(offset, index);
        insertBeforeOffset += instruction.getLength();
        Logger.info("Appended ILOAD at offset " + offset);
    }

    /**
     * Appends an ISTORE instruction to the end of the bytecode.
     *
     * @param index The local variable index to store into.
     */
    public void addIStore(int index) {
        int offset = processOffset();
        Instruction instruction = codeWriter.insertIStore(offset, index);
        insertBeforeOffset += instruction.getLength();
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
            int opcode = 0x02 + (value + 1);
            instr = new IConstInstruction(opcode, processOffset(), value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            int opcode = 0x10;
            byte operand = (byte) value;
            instr = new BipushInstruction(opcode, processOffset(), operand);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            int opcode = 0x11;
            short operand = (short) value;
            instr = new SipushInstruction(opcode, processOffset(), operand);
        } else {
            int opcode = 0x12;
            IntegerItem intItem = constPool.findOrAddInteger(value);
            int ldcIndex = constPool.getIndexOf(intItem);
            instr = new LdcInstruction(constPool, opcode, processOffset(), ldcIndex);
        }
        codeWriter.insertInstruction(processOffset(), instr);
        insertBeforeOffset += instr.getLength();
    }

    /**
     * Appends a LCONST instruction to push a long constant onto the stack.
     *
     * @param value The long value to push.
     */
    public void addLConst(long value) {
        Instruction instr;
        if (value == 0L || value == 1L) {
            int opcode = (value == 0L) ? 0x09 : 0x0A;
            instr = new LConstInstruction(opcode, processOffset(), value);
        } else {
            int opcode = 0x14;
            LongItem longItem = constPool.findOrAddLong(value);
            int ldc2WIndex = constPool.getIndexOf(longItem);
            instr = new Ldc2WInstruction(constPool, opcode, processOffset(), ldc2WIndex);
        }
        codeWriter.insertInstruction(processOffset(), instr);
        insertBeforeOffset += instr.getLength();
        Logger.info("Appended LCONST instruction with value " + value + " at offset " + (processOffset() - instr.getLength()));
    }

    /**
     * Appends a FCONST instruction to push a float constant onto the stack.
     *
     * @param value The float value to push.
     */
    public void addFConst(float value) {
        Instruction instr;
        if (value == 0.0f) {
            instr = new FConstInstruction(0x0B, processOffset(), 0.0f);
        } else if (value == 1.0f) {
            instr = new FConstInstruction(0x0C, processOffset(), 1.0f);
        } else if (value == 2.0f) {
            instr = new FConstInstruction(0x0D, processOffset(), 2.0f);
        } else {
            int opcode = 0x12;
            FloatItem floatItem = constPool.findOrAddFloat(value);
            int ldcIndex = constPool.getIndexOf(floatItem);
            instr = new LdcInstruction(constPool, opcode, processOffset(), ldcIndex);
        }
        codeWriter.insertInstruction(processOffset(), instr);
        insertBeforeOffset += instr.getLength();
        Logger.info("Appended FCONST instruction with value " + value + " at offset " + (processOffset() - instr.getLength()));
    }

    /**
     * Appends a DCONST instruction to push a double constant onto the stack.
     *
     * @param value The double value to push.
     */
    public void addDConst(double value) {
        Instruction instr;
        if (value == 0.0d || value == 1.0d) {
            int opcode = (value == 0.0d) ? 0x0E : 0x0F;
            instr = new DConstInstruction(opcode, processOffset(), value);
        } else {
            int opcode = 0x14;
            DoubleItem doubleItem = constPool.findOrAddDouble(value);
            int ldc2WIndex = constPool.getIndexOf(doubleItem);
            instr = new Ldc2WInstruction(constPool, opcode, processOffset(), ldc2WIndex);
        }
        codeWriter.insertInstruction(processOffset(), instr);
        insertBeforeOffset += instr.getLength();
        Logger.info("Appended DCONST instruction with value " + value + " at offset " + (processOffset() - instr.getLength()));
    }

    /**
     * Appends an ACONST_NULL instruction to push a null reference onto the stack.
     */
    public void addAConstNull() {
        int opcode = 0x01;
        Instruction instr = new AConstNullInstruction(opcode, processOffset());
        codeWriter.insertInstruction(processOffset(), instr);
        insertBeforeOffset += instr.getLength();
        Logger.info("Appended ACONST_NULL instruction at offset " + (processOffset() - instr.getLength()));
    }

    /**
     * Appends a LLOAD instruction to load a long from a local variable.
     *
     * @param index The local variable index to load from.
     */
    public void addLLoad(int index) {
        int opcode = 0x16;
        Instruction instr = new LLoadInstruction(opcode, processOffset(), index);
        codeWriter.insertInstruction(processOffset(), instr);
        insertBeforeOffset += instr.getLength();
        Logger.info("Appended LLOAD instruction for index " + index + " at offset " + (processOffset() - instr.getLength()));
    }

    /**
     * Appends a FLOAD instruction to load a float from a local variable.
     *
     * @param index The local variable index to load from.
     */
    public void addFLoad(int index) {
        int opcode = 0x17;
        Instruction instr = new FLoadInstruction(opcode, processOffset(), index);
        codeWriter.insertInstruction(processOffset(), instr);
        insertBeforeOffset += instr.getLength();
        Logger.info("Appended FLOAD instruction for index " + index + " at offset " + (processOffset() - instr.getLength()));
    }

    /**
     * Appends a DLOAD instruction to load a double from a local variable.
     *
     * @param index The local variable index to load from.
     */
    public void addDLoad(int index) {
        int opcode = 0x18;
        Instruction instr = new DLoadInstruction(opcode, processOffset(), index);
        codeWriter.insertInstruction(processOffset(), instr);
        insertBeforeOffset += instr.getLength();
        Logger.info("Appended DLOAD instruction for index " + index + " at offset " + (processOffset() - instr.getLength()));
    }


    /**
     * Appends a GOTO instruction to the end of the bytecode.
     *
     * @param labelName The name of the label to jump to.
     */
    public void addGoto(String labelName) {
        defineLabel(labelName);
        int targetOffset = labels.get(labelName);

        int gotoOffset = processOffset();
        short relativeOffset = (short) (targetOffset - (gotoOffset + 3));

        GotoInstruction gotoInstr = new GotoInstruction(0xA7, gotoOffset, relativeOffset);
        codeWriter.appendInstruction(gotoInstr);
        insertBeforeOffset += gotoInstr.getLength();
    }

    /**
     * Appends a GOTO_W instruction to the end of the bytecode.
     *
     * @param labelName The name of the label to jump to.
     */
    public void addGotoW(String labelName) {
        defineLabel(labelName);
        int targetOffset = labels.get(labelName);

        int gotoWOffset = processOffset();
        int relativeOffset = targetOffset - (gotoWOffset + 5);

        GotoInstruction gotoWInstr = new GotoInstruction(0xC8, gotoWOffset, relativeOffset);
        codeWriter.appendInstruction(gotoWInstr);
        insertBeforeOffset += gotoWInstr.getLength();
    }

    /**
     * Appends an IINC instruction to the end of the bytecode.
     *
     * @param varIndex   The local variable index to increment.
     * @param increment  The constant by which to increment the variable.
     */
    public void addIInc(int varIndex, int increment) {
        int offset = processOffset();
        Instruction instruction = codeWriter.insertIInc(offset, varIndex, increment);
        insertBeforeOffset += instruction.getLength();
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
        int offset = processOffset();
        ReturnInstruction returnInstr = new ReturnInstruction(returnOpcode, offset);
        codeWriter.insertInstruction(processOffset(), returnInstr);
        insertBeforeOffset += returnInstr.getLength();
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

    /**
     * Computes and updates the StackMapTable frames for this method.
     * This is an opt-in operation - call this after making bytecode modifications.
     * If the bytecode hasn't been modified and a valid StackMapTable exists, this method preserves the existing frames.
     */
    public void computeFrames() {
        codeWriter.computeFrames();
    }

    /**
     * Forces recomputation of StackMapTable frames, even if bytecode wasn't modified.
     */
    public void forceComputeFrames() {
        codeWriter.forceComputeFrames();
    }

    /**
     * Returns whether the bytecode has been modified since loading.
     *
     * @return true if modified, false otherwise
     */
    public boolean isModified() {
        return codeWriter.isModified();
    }

    /**
     * Appends a load instruction based on the type descriptor.
     *
     * @param i The local variable index.
     * @param desc The type descriptor (I, J, F, D, or reference type).
     */
    public void addLoad(int i, String desc)
    {
        switch (desc)
        {
            case "I":
                addILoad(i);
                break;
            case "J":
                addLLoad(i);
                break;
            case "F":
                addFLoad(i);
                break;
            case "D":
                addDLoad(i);
                break;
            default:
                addALoad(i);
        }
    }

    /**
     * Inserts an INVOKESTATIC instruction into the bytecode.
     *
     * @param className         The fully qualified class name (e.g., "java/lang/System").
     * @param methodName        The name of the static method to invoke (e.g., "currentTimeMillis").
     * @param methodDescriptor  The method descriptor (e.g., "()J").
     */
    public void addInvokeStatic(String className, String methodName, String methodDescriptor) {
        Utf8Item classNameUtf8 = constPool.findOrAddUtf8(className);
        Utf8Item methodNameUtf8 = constPool.findOrAddUtf8(methodName);
        Utf8Item methodDescUtf8 = constPool.findOrAddUtf8(methodDescriptor);

        ClassRefItem classRef = constPool.findOrAddClass(classNameUtf8.getValue());

        NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(methodNameUtf8.getIndex(constPool), methodDescUtf8.getIndex(constPool));

        MethodRefItem methodRef = constPool.findOrAddMethodRef(classRef.getIndex(constPool), nameAndType.getIndex(constPool));

        int methodRefIndex = constPool.getIndexOf(methodRef);

        int insertOffset = codeWriter.getBytecodeSize();

        Instruction instruction = codeWriter.insertInvokeStatic(insertOffset, methodRefIndex);
        insertBeforeOffset += instruction.getLength();
    }


    private int processOffset()
    {
        return insertBefore ? insertBeforeOffset : codeWriter.getBytecodeSize();
    }

    private int processOffsetForInsertBeforeBase()
    {
        if(!insertBefore)
            return codeWriter.getBytecodeSize();

        int offset = 0;

        if(isStatic)
            return offset;

        for (Instruction instruction : codeWriter.getInstructions()) {
            if (instruction instanceof ALoadInstruction && ((ALoadInstruction) instruction).getVarIndex() == 0) {
                ALoadInstruction aLoadInstruction = (ALoadInstruction) instruction;
                offset += instruction.getLength();
                continue;
            }

            if (instruction instanceof InvokeSpecialInstruction) {
                InvokeSpecialInstruction invokeSpecialInstruction = (InvokeSpecialInstruction) instruction;
                String methodName = invokeSpecialInstruction.getMethodName();

                if ("<init>".equals(methodName)) {
                    offset += instruction.getLength();
                    continue;
                }
            }

            break;
        }
        return offset;
    }

    /**
     * Checks if the bytecode ends with a return instruction.
     *
     * @return true if the bytecode ends with a return instruction, false otherwise.
     */
    public boolean endsWithReturn()
    {
        return codeWriter.endsWithReturn();
    }

    /**
     * Inserts an LDC or LDC_W instruction to load a String constant onto the operand stack.
     *
     * @param value The String constant to load.
     */
    public void addLdc(String value) {
        int offset = processOffset();
        Utf8Item stringUtf8 = constPool.findOrAddUtf8(value);

        StringRefItem stringRef = constPool.findOrAddString(stringUtf8.getValue());

        int stringRefIndex = constPool.getIndexOf(stringRef);

        Instruction instruction;
        if (stringRefIndex <= 0xFF) {
            instruction = codeWriter.insertLDC(processOffset(), stringRefIndex);
        } else {
            instruction = codeWriter.insertLDCW(processOffset(), stringRefIndex);
        }
        insertBeforeOffset += instruction.getLength();
    }

    /**
     * Inserts an INVOKEVIRTUAL instruction into the bytecode.
     *
     * @param className        The fully qualified class name (e.g., "java/io/PrintStream").
     * @param methodName       The name of the method to invoke (e.g., "println").
     * @param methodDescriptor The method descriptor (e.g., "(Ljava/lang/String;)V").
     */
    public void addInvokeVirtual(String className, String methodName, String methodDescriptor) {
        Utf8Item classNameUtf8 = constPool.findOrAddUtf8(className);
        Utf8Item methodNameUtf8 = constPool.findOrAddUtf8(methodName);
        Utf8Item methodDescUtf8 = constPool.findOrAddUtf8(methodDescriptor);

        ClassRefItem classRef = constPool.findOrAddClass(classNameUtf8.getValue());

        NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(methodNameUtf8.getIndex(constPool), methodDescUtf8.getIndex(constPool));

        MethodRefItem methodRef = constPool.findOrAddMethodRef(classRef.getIndex(constPool), nameAndType.getIndex(constPool));

        int methodRefIndex = constPool.getIndexOf(methodRef);

        int insertOffset = processOffset();

        Instruction instruction = codeWriter.insertInvokeVirtual(insertOffset, methodRefIndex);
        insertBeforeOffset += instruction.getLength();
    }

    /**
     * Inserts a GETSTATIC instruction into the bytecode.
     *
     * @param className       The fully qualified class name (e.g., "java/lang/System").
     * @param fieldName       The name of the static field (e.g., "out").
     * @param fieldDescriptor The field descriptor (e.g., "Ljava/io/PrintStream;").
     */
    public void addGetStatic(String className, String fieldName, String fieldDescriptor) {
        Utf8Item fieldNameUtf8 = constPool.findOrAddUtf8(fieldName);
        Utf8Item fieldDescUtf8 = constPool.findOrAddUtf8(fieldDescriptor);

        ClassRefItem classRef = constPool.findOrAddClass(className);

        NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(fieldNameUtf8.getIndex(constPool), fieldDescUtf8.getIndex(constPool));

        FieldRefItem fieldRef = constPool.findOrAddField(classRef.getClassName(), nameAndType.getName(), nameAndType.getDescriptor());

        int fieldRefIndex = constPool.getIndexOf(fieldRef);

        int insertOffset = processOffset();

        Instruction instruction = codeWriter.insertGetStatic(insertOffset, fieldRefIndex);
        insertBeforeOffset += instruction.getLength();
    }

    /**
     * Appends a TABLESWITCH instruction to the bytecode.
     *
     * @param low           The lowest key value.
     * @param high          The highest key value.
     * @param defaultOffset The default branch offset (relative to this instruction).
     * @param jumpOffsets   Map of key values to branch offsets (relative to this instruction).
     */
    public void addTableSwitch(int low, int high, int defaultOffset, Map<Integer, Integer> jumpOffsets) {
        int offset = processOffset();
        int padding = (4 - ((offset + 1) % 4)) % 4;
        TableSwitchInstruction instr = codeWriter.insertTableSwitch(
            offset, padding, defaultOffset, low, high, jumpOffsets);
        insertBeforeOffset += instr.getLength();
    }

    /**
     * Appends a LOOKUPSWITCH instruction to the bytecode.
     *
     * @param defaultOffset The default branch offset (relative to this instruction).
     * @param matchOffsets  Map of case values to branch offsets (relative to this instruction).
     */
    public void addLookupSwitch(int defaultOffset, Map<Integer, Integer> matchOffsets) {
        int offset = processOffset();
        int padding = (4 - ((offset + 1) % 4)) % 4;
        int npairs = matchOffsets.size();
        LookupSwitchInstruction instr = codeWriter.insertLookupSwitch(
            offset, padding, defaultOffset, npairs, matchOffsets);
        insertBeforeOffset += instr.getLength();
    }
}
