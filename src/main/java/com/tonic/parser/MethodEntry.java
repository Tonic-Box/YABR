package com.tonic.parser;

import com.tonic.analysis.visitor.AbstractClassVisitor;
import com.tonic.analysis.visitor.AbstractMethodVisitor;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a method entry in the class file.
 */
@Getter
public class MethodEntry extends MemberEntry {

    /**
     * Constructs a MethodEntry by parsing from the class file.
     *
     * @param classFile the ClassFile instance being parsed
     */
    public MethodEntry(ClassFile classFile) {
        this.classFile = classFile;

        this.access = classFile.readUnsignedShort();
        this.nameIndex = classFile.readUnsignedShort();
        this.descIndex = classFile.readUnsignedShort();

        int attributesCount = classFile.readUnsignedShort();
        this.attributes = new ArrayList<>(attributesCount);

        for (int i = 0; i < attributesCount; i++) {
            Attribute attribute = Attribute.get(classFile, classFile.getConstPool(), this);
            this.attributes.add(attribute);
        }

        this.name = resolveUtf8(nameIndex);
        this.desc = resolveUtf8(descIndex);
        this.ownerName = classFile.getClassName().replace('.', '/');
        this.key = computeKey();
    }

    /**
     * Constructs a MethodEntry for dynamically creating methods.
     *
     * @param classFile the ClassFile instance
     * @param accessFlags the access flags for the method
     * @param nameIndex the index of the method name in the constant pool
     * @param descIndex the index of the method descriptor in the constant pool
     * @param attributes the list of attributes for the method
     */
    public MethodEntry(ClassFile classFile, int accessFlags, int nameIndex, int descIndex, List<Attribute> attributes) {
        this.classFile = classFile;
        this.access = accessFlags;
        this.nameIndex = nameIndex;
        this.descIndex = descIndex;
        this.attributes = (attributes != null) ? attributes : new ArrayList<>();
        this.name = resolveUtf8(nameIndex);
        this.desc = resolveUtf8(descIndex);
        this.ownerName = classFile.getClassName();
        this.key = computeKey();

        if(ownerName != null) {
            this.ownerName = ownerName.replace('.', '/');
        }
    }

    @Override
    public void setName(String newName) {
        Utf8Item newNameUtf8 = (Utf8Item) classFile.getConstPool().findOrAddUtf8(newName);
        this.nameIndex = classFile.getConstPool().getIndexOf(newNameUtf8);
        this.name = newName;
        this.key = computeKey();
    }

    private String resolveUtf8(int index) {
        return ((Utf8Item) classFile.getConstPool().getItem(index)).getValue();
    }

    private String computeKey() {
        return name + desc;
    }

    /**
     * Retrieves the CodeAttribute associated with this method.
     *
     * @return the CodeAttribute if present, otherwise null
     */
    public CodeAttribute getCodeAttribute() {
        for (Attribute attr : attributes) {
            if (attr instanceof CodeAttribute) {
                return (CodeAttribute) attr;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "MethodEntry{" +
                "ownerName='" + ownerName + '\'' +
                ", name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                ", key='" + key + '\'' +
                ", access=0x" + Integer.toHexString(access) +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(access);
        dos.writeShort(nameIndex);
        dos.writeShort(descIndex);
        dos.writeShort(attributes.size());
        for (Attribute attr : attributes) {
            attr.write(dos);
        }
    }

    public void accept(AbstractClassVisitor visitor)
    {
        visitor.visitMethod(this);
    }

    public void accept(AbstractMethodVisitor visitor) throws IOException {
        visitor.visit(this);
    }

    public boolean isVoidReturn() {
        return desc.endsWith(")V");
    }

    public boolean isPrimitiveReturn() {
        return desc.endsWith(")Z") || desc.endsWith(")B") || desc.endsWith(")C") || desc.endsWith(")S") || desc.endsWith(")I") || desc.endsWith(")J") || desc.endsWith(")F") || desc.endsWith(")D");
    }

    public boolean isReferenceReturn() {
        return desc.endsWith(")L");
    }

    public boolean isPrimitiveArrayReturn() {
        return desc.endsWith(")[Z") || desc.endsWith(")[B") || desc.endsWith(")[C") || desc.endsWith(")[S") || desc.endsWith(")[I") || desc.endsWith(")[J") || desc.endsWith(")[F") || desc.endsWith(")[D");
    }

    public boolean isReferenceArrayReturn() {
        return desc.endsWith(")[L");
    }
}
