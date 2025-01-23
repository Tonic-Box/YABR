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
 * A utility class for high-level bytecode manipulations.
 * It leverages the existing CodeWriter class to perform specific operations.
 */
@Getter
public class Bytecode {
    private final CodeWriter codeWriter;
    private final ConstPool constPool;
    private boolean insertBefore = false;
    @Setter
    private int insertBeforeOffset = 0;
    private final boolean isStatic;

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
        this.isStatic = Modifiers.isStatic(methodEntry.getAccess());
    }

    public Bytecode(CodeWriter codeWriter) {
        this.codeWriter = codeWriter;
        this.constPool = codeWriter.getConstPool();
        this.isStatic = Modifiers.isStatic(codeWriter.getMethodEntry().getAccess());
    }

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
     * @param bootstrapMethodIndex The bootstrap method index in the constant pool.
     * @param nameAndTypeIndex     The name and type index in the constant pool.
     */
    public void addInvokeDynamic(int bootstrapMethodIndex, int nameAndTypeIndex) {
        int offset = processOffset();
        codeWriter.insertInvokeDynamic(offset, bootstrapMethodIndex, nameAndTypeIndex);
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
            // ICONST_M1 to ICONST_5 (0x02 to 0x08)
            int opcode = 0x02 + (value + 1); // ICONST_M1 is 0x02
            instr = new IConstInstruction(opcode, processOffset(), value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            // BIPUSH (0x10)
            int opcode = 0x10;
            byte operand = (byte) value;
            Logger.info("BIPUSH: " + value + " -> " + operand);
            instr = new BipushInstruction(opcode, processOffset(), operand);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            // SIPUSH (0x11)
            int opcode = 0x11;
            short operand = (short) value;
            instr = new SipushInstruction(opcode, processOffset(), operand);
        } else {
            // LDC (0x12) for integer constants
            int opcode = 0x12;
            IntegerItem intItem = constPool.findOrAddInteger(value);
            int ldcIndex = constPool.getIndexOf(intItem);
            instr = new LdcInstruction(constPool, opcode, processOffset(), ldcIndex);
        }
        codeWriter.insertInstruction(processOffset(), instr);
        insertBeforeOffset += instr.getLength();
        Logger.info("Appended ICONST/IINC instruction with value " + value + " at offset " + (processOffset() - instr.getLength()));
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
            instr = new LConstInstruction(opcode, processOffset(), value);
        } else {
            // LDC2_W (0x14) for long constants
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
            instr = new FConstInstruction(0x0B, processOffset(), 0.0f); // FCONST_0
        } else if (value == 1.0f) {
            instr = new FConstInstruction(0x0C, processOffset(), 1.0f); // FCONST_1
        } else if (value == 2.0f) {
            instr = new FConstInstruction(0x0D, processOffset(), 2.0f); // FCONST_2
        } else {
            // LDC (0x12) for float constants
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
            // DCONST_0 or DCONST_1 (0x0E or 0x0F)
            int opcode = (value == 0.0d) ? 0x0E : 0x0F;
            instr = new DConstInstruction(opcode, processOffset(), value);
        } else {
            // LDC2_W (0x14) for double constants
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
        int opcode = 0x01; // ACONST_NULL
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
        int opcode = 0x16; // LLOAD
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
        int opcode = 0x17; // FLOAD
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
        int opcode = 0x18; // DLOAD
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
        // Define a label at the end to serve as the jump target
        defineLabel(labelName);
        int targetOffset = labels.get(labelName);

        // Calculate the branch offset relative to the GOTO instruction
        int gotoOffset = processOffset();
        short relativeOffset = (short) (targetOffset - (gotoOffset + 3)); // GOTO is 3 bytes long

        // Append the GOTO instruction
        GotoInstruction gotoInstr = new GotoInstruction(0xA7, gotoOffset, relativeOffset);
        codeWriter.appendInstruction(gotoInstr);
        insertBeforeOffset += gotoInstr.getLength();
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
        int gotoWOffset = processOffset();
        int relativeOffset = targetOffset - (gotoWOffset + 5); // GOTO_W is 5 bytes long

        // Append the GOTO_W instruction
        GotoInstruction gotoWInstr = new GotoInstruction(0xC8, gotoWOffset, relativeOffset);
        codeWriter.appendInstruction(gotoWInstr);
        insertBeforeOffset += gotoWInstr.getLength();
        Logger.info("Appended GOTO_W to label '" + labelName + "' at offset " + targetOffset);
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
        // Step 1: Add or find Utf8 entries for className, methodName, and methodDescriptor
        Utf8Item classNameUtf8 = constPool.findOrAddUtf8(className);
        Utf8Item methodNameUtf8 = constPool.findOrAddUtf8(methodName);
        Utf8Item methodDescUtf8 = constPool.findOrAddUtf8(methodDescriptor);

        // Step 2: Add or find ClassRef entry for className
        ClassRefItem classRef = constPool.findOrAddClass(classNameUtf8.getValue());

        // Step 3: Add or find NameAndType entry for methodName and methodDescriptor
        NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(methodNameUtf8.getIndex(constPool), methodDescUtf8.getIndex(constPool));

        // Step 4: Add or find MethodRef entry for classRef and nameAndType
        MethodRefItem methodRef = constPool.findOrAddMethodRef(classRef.getIndex(constPool), nameAndType.getIndex(constPool));

        // Step 5: Get the index of the MethodRef entry
        int methodRefIndex = constPool.getIndexOf(methodRef);

        // Step 6: Determine the insertion offset
        // For example, inserting at the end of the bytecode
        int insertOffset = codeWriter.getBytecodeSize();

        // Step 7: Insert the INVOKESTATIC instruction
        Instruction instruction = codeWriter.insertInvokeStatic(insertOffset, methodRefIndex);
        insertBeforeOffset += instruction.getLength();

        // Optional: Update maxStack and maxLocals if necessary
        // This depends on your implementation of CodeWriter and whether it handles stack analysis automatically
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
            // Check for aload_0 (opcode 0x2a)
            if (instruction instanceof ALoadInstruction && ((ALoadInstruction) instruction).getVarIndex() == 0) {
                ALoadInstruction aLoadInstruction = (ALoadInstruction) instruction; // aload_0
                offset += instruction.getLength(); // Increment offset by instruction length
                continue; // Skip to the next instruction
            }

            // Check for invokespecial <init> (opcode 0xb7)
            if (instruction instanceof InvokeSpecialInstruction) {
                InvokeSpecialInstruction invokeSpecialInstruction = (InvokeSpecialInstruction) instruction; // invokespecial
                // Validate that it is calling super.<init>
                String methodName = invokeSpecialInstruction.getMethodName(); // Assume instruction provides method details

                if ("<init>".equals(methodName)) {
                    offset += instruction.getLength(); // Increment offset by instruction length
                    continue; // Skip to the next instruction
                }
            }

            // If no more matching instructions, break the loop
            break;
        }
        return offset;
    }

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
        // Step 1: Add or find Utf8 entry for the string
        Utf8Item stringUtf8 = constPool.findOrAddUtf8(value);

        // Step 2: Add or find StringRef entry
        StringRefItem stringRef = constPool.findOrAddString(stringUtf8.getValue());

        // Step 3: Get the index of the StringRef entry
        int stringRefIndex = constPool.getIndexOf(stringRef);

        // Step 4: Decide whether to use LDC or LDC_W based on the index
        Instruction instruction;
        if (stringRefIndex <= 0xFF) {
            // Use LDC (opcode 0x12)
            instruction = codeWriter.insertLDC(processOffset(), stringRefIndex);
        } else {
            // Use LDC_W (opcode 0x13)
            instruction = codeWriter.insertLDCW(processOffset(), stringRefIndex);
        }
        insertBeforeOffset += instruction.getLength();
        // Optional: Update maxStack if your CodeWriter does not handle it automatically
        // codeWriter.updateMaxStackForLdc();
    }

    /**
     * Inserts an INVOKEVIRTUAL instruction into the bytecode.
     *
     * @param className        The fully qualified class name (e.g., "java/io/PrintStream").
     * @param methodName       The name of the method to invoke (e.g., "println").
     * @param methodDescriptor The method descriptor (e.g., "(Ljava/lang/String;)V").
     */
    public void addInvokeVirtual(String className, String methodName, String methodDescriptor) {
        // Step 1: Add or find Utf8 entries for className, methodName, and methodDescriptor
        Utf8Item classNameUtf8 = constPool.findOrAddUtf8(className);
        Utf8Item methodNameUtf8 = constPool.findOrAddUtf8(methodName);
        Utf8Item methodDescUtf8 = constPool.findOrAddUtf8(methodDescriptor);

        // Step 2: Add or find ClassRef entry for className
        ClassRefItem classRef = constPool.findOrAddClass(classNameUtf8.getValue());

        // Step 3: Add or find NameAndType entry for methodName and methodDescriptor
        NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(methodNameUtf8.getIndex(constPool), methodDescUtf8.getIndex(constPool));

        // Step 4: Add or find MethodRef entry for classRef and nameAndType
        MethodRefItem methodRef = constPool.findOrAddMethodRef(classRef.getIndex(constPool), nameAndType.getIndex(constPool));

        // Step 5: Get the index of the MethodRef entry
        int methodRefIndex = constPool.getIndexOf(methodRef);

        // Step 6: Determine the insertion offset
        int insertOffset = processOffset();

        // Step 7: Insert the INVOKEVIRTUAL instruction
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
        // Step 1: Add or find Utf8 entries for className, fieldName, and fieldDescriptor
        Utf8Item fieldNameUtf8 = constPool.findOrAddUtf8(fieldName);
        Utf8Item fieldDescUtf8 = constPool.findOrAddUtf8(fieldDescriptor);

        // Step 2: Add or find ClassRef entry for className
        ClassRefItem classRef = constPool.findOrAddClass(className);
        System.out.println("classRef: " + classRef.getClassName());

        // Step 3: Add or find NameAndType entry for fieldName and fieldDescriptor
        NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(fieldNameUtf8.getIndex(constPool), fieldDescUtf8.getIndex(constPool));

        // Step 4: Add or find FieldRef entry for classRef and nameAndType
        FieldRefItem fieldRef = constPool.findOrAddField(classRef.getClassName(), nameAndType.getName(), nameAndType.getDescriptor());

        // Step 5: Get the index of the FieldRef entry
        int fieldRefIndex = constPool.getIndexOf(fieldRef);

        // Step 6: Determine the insertion offset
        int insertOffset = processOffset();

        // Step 7: Insert the GETSTATIC instruction
        Instruction instruction = codeWriter.insertGetStatic(insertOffset, fieldRefIndex);
        insertBeforeOffset += instruction.getLength();

        // Optional: Update maxStack and maxLocals if necessary
        // This depends on your implementation of CodeWriter and whether it handles stack analysis automatically
    }
}
