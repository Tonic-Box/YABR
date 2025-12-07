package com.tonic.parser;

import com.tonic.analysis.visitor.AbstractClassVisitor;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Represents a field entry in the class file.
 */
@Getter
public class FieldEntry extends MemberEntry {

    /**
     * Constructs an empty FieldEntry for dynamic creation.
     */
    public FieldEntry() {
    }

    /**
     * Constructs a FieldEntry by parsing from the class file.
     *
     * @param classFile the ClassFile instance being parsed
     */
    public FieldEntry(ClassFile classFile) {
        this.classFile = classFile;

        this.access = classFile.readUnsignedShort();
        this.nameIndex = classFile.readUnsignedShort();
        this.descIndex = classFile.readUnsignedShort();

        final int attributeCount = classFile.readUnsignedShort();
        this.attributes = new ArrayList<>(attributeCount);

        for (int i = 0; i < attributeCount; i++) {
            Attribute attribute = Attribute.get(classFile, classFile.getConstPool(), this);
            this.attributes.add(attribute);
        }

        this.ownerName = classFile.getClassName().replace('.', '/');
        this.name = resolveUtf8(nameIndex);
        this.desc = resolveUtf8(descIndex);
        this.key = computeKey();
    }

    public void setName(String newName) {
        Utf8Item newNameUtf8 = classFile.getConstPool().findOrAddUtf8(newName);
        this.nameIndex = classFile.getConstPool().getIndexOf(newNameUtf8);
        this.name = newName;
        this.key = computeKey();
    }

    private String resolveUtf8(int index) {
        Utf8Item utf8Item = (Utf8Item) classFile.getConstPool().getItem(index);
        if (utf8Item != null) {
            return utf8Item.getValue();
        } else {
            return "Unknown";
        }
    }

    private String computeKey() {
        return name + desc;
    }

    @Override
    public String toString() {
        return "FieldEntry{" +
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
        visitor.visitField(this);
    }
}
