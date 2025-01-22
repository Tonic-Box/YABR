package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.analysis.CodePrinter;
import com.tonic.utill.Logger;
import lombok.Getter;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the Code attribute.
 * Contains the bytecode and related information for a method.
 */
@Getter
public class CodeAttribute extends Attribute {
    @Setter
    private int maxStack, maxLocals;
    @Setter
    private byte[] code;
    private List<ExceptionTableEntry> exceptionTable = new ArrayList<>();
    @Setter
    private List<Attribute> attributes = new ArrayList<>();

    public CodeAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        int startIndex = classFile.getIndex(); // Record starting index

        this.maxStack = classFile.readUnsignedShort();
        this.maxLocals = classFile.readUnsignedShort();

        long codeLengthLong = classFile.readUnsignedInt();
        if (codeLengthLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Code attribute code_length too large: " + codeLengthLong);
        }
        int codeLength = (int) codeLengthLong;

        // Validate remaining bytes
        if (classFile.getLength() - classFile.getIndex() < codeLength) {
            throw new IllegalArgumentException("Not enough bytes to read code array. Requested: "
                    + codeLength + ", Available: " + (classFile.getLength() - classFile.getIndex()));
        }

        this.code = new byte[codeLength];
        classFile.readBytes(this.code, 0, codeLength);

        int exceptionTableLength = classFile.readUnsignedShort();
        this.exceptionTable = new ArrayList<>(exceptionTableLength);
        for (int i = 0; i < exceptionTableLength; i++) {
            int startPc = classFile.readUnsignedShort();
            int endPc = classFile.readUnsignedShort();
            int handlerPc = classFile.readUnsignedShort();
            int catchType = classFile.readUnsignedShort();
            exceptionTable.add(new ExceptionTableEntry(startPc, endPc, handlerPc, catchType));
        }

        int attributesCount = classFile.readUnsignedShort();
        this.attributes = new ArrayList<>(attributesCount);
        for (int i = 0; i < attributesCount; i++) {
            Attribute attribute = Attribute.get(classFile, parent.getClassFile().getConstPool(), parent);
            this.attributes.add(attribute);
        }

        int bytesRead = classFile.getIndex() - startIndex; // Calculate bytes read

        if (bytesRead != length) {
            Logger.error("Warning: CodeAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
            // Optionally, throw an exception or handle as needed
        }
    }

    public void setParent(MethodEntry methodEntry)
    {
        this.parent = methodEntry;
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        // 1. Write max_stack
        dos.writeShort(maxStack);

        // 2. Write max_locals
        dos.writeShort(maxLocals);

        // 3. Write code_length
        dos.writeInt(code.length);

        // 4. Write code bytes
        dos.write(code);

        // 5. Write exception_table_length
        dos.writeShort(exceptionTable.size());

        // 6. Write exception table entries
        for (ExceptionTableEntry entry : exceptionTable) {
            dos.writeShort(entry.getStartPc());
            dos.writeShort(entry.getEndPc());
            dos.writeShort(entry.getHandlerPc());
            dos.writeShort(entry.getCatchType());
        }

        // 7. Write attributes_count
        dos.writeShort(attributes.size());

        // 8. Write each nested attribute
        for (Attribute attr : attributes) {
            attr.write(dos);
            // 'write' on each sub-attribute will itself write:
            //   - attribute_name_index (u2)
            //   - attribute_length (u4)
            //   - info (the actual body of that attribute)
        }
    }

    @Override
    public void updateLength() {
        int codeLength = (code != null) ? code.length : 0;

        int baseInfoSize = 2  // max_stack
                + 2  // max_locals
                + 4  // code_length
                + codeLength
                + 2  // exception_table_length
                + (exceptionTable.size() * 8)
                + 2; // attributes_count

        int subAttributesSize = 0;
        for (Attribute attr : attributes) {
            // each sub-attribute has 2 (name_index) + 4 (its length) + its own info length
            // so we rely on the sub-attribute's length field for the "info" portion,
            // plus 6 bytes overhead (2 + 4).
            subAttributesSize += 6 + attr.length;
        }

        this.length = baseInfoSize + subAttributesSize;
    }

    /**
     * Pretty prints the bytecode into a human-readable format.
     *
     * @return A String representing the disassembled bytecode.
     */
    public String prettyPrintCode() {
        return CodePrinter.prettyPrintCode(this.code, parent.getClassFile().getConstPool());
    }

    @Override
    public String toString() {
        return "CodeAttribute{" +
                "maxStack=" + maxStack +
                ", maxLocals=" + maxLocals +
                ", codeLength=" + code.length +
                ", exceptionTableSize=" + exceptionTable.size() +
                ", attributesCount=" + attributes.size() +
                '}';
    }
}
