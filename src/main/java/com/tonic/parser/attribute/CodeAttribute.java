package com.tonic.parser.attribute;

import com.tonic.analysis.visitor.AbstractMethodVisitor;
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

    public CodeAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        int startIndex = classFile.getIndex();

        this.maxStack = classFile.readUnsignedShort();
        this.maxLocals = classFile.readUnsignedShort();

        long codeLengthLong = classFile.readUnsignedInt();
        if (codeLengthLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Code attribute code_length too large: " + codeLengthLong);
        }
        int codeLength = (int) codeLengthLong;

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
            Attribute attribute = Attribute.get(classFile, getClassFile().getConstPool(), parent);
            this.attributes.add(attribute);
        }

        int bytesRead = classFile.getIndex() - startIndex;

        if (bytesRead != length) {
            Logger.error("Warning: CodeAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
        }
    }

    /**
     * Sets the parent method entry for this code attribute.
     *
     * @param methodEntry The parent method entry
     */
    public void setParent(MethodEntry methodEntry)
    {
        this.parent = methodEntry;
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(maxStack);
        dos.writeShort(maxLocals);
        dos.writeInt(code.length);
        dos.write(code);
        dos.writeShort(exceptionTable.size());

        for (ExceptionTableEntry entry : exceptionTable) {
            dos.writeShort(entry.getStartPc());
            dos.writeShort(entry.getEndPc());
            dos.writeShort(entry.getHandlerPc());
            dos.writeShort(entry.getCatchType());
        }

        dos.writeShort(attributes.size());

        for (Attribute attr : attributes) {
            attr.write(dos);
        }
    }

    @Override
    public void updateLength() {
        int codeLength = (code != null) ? code.length : 0;

        int baseInfoSize = 2
                + 2
                + 4
                + codeLength
                + 2
                + (exceptionTable.size() * 8)
                + 2;

        int subAttributesSize = 0;
        for (Attribute attr : attributes) {
            subAttributesSize += 6 + attr.length;
        }

        this.length = baseInfoSize + subAttributesSize;
    }

    /**
     * Generates a human-readable disassembly of the bytecode.
     *
     * @return Disassembled bytecode string
     */
    public String prettyPrintCode() {
        return CodePrinter.prettyPrintCode(this.code, getClassFile().getConstPool());
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

    /**
     * Accepts a visitor for bytecode analysis.
     *
     * @param abstractMethodVisitor The visitor to accept
     */
    public void accept(AbstractMethodVisitor abstractMethodVisitor)
    {
    }
}
