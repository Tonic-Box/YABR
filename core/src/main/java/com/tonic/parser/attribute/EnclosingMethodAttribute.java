package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.constpool.*;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the EnclosingMethod attribute.
 * Indicates the method that encloses a local or anonymous class.
 */
@Getter
public class EnclosingMethodAttribute extends Attribute {
    private int classIndex;
    private int methodIndex;

    public EnclosingMethodAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public EnclosingMethodAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length != 4) {
            throw new IllegalArgumentException("EnclosingMethod attribute length must be 4, found: " + length);
        }
        this.classIndex = classFile.readUnsignedShort();
        this.methodIndex = classFile.readUnsignedShort();
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(classIndex);
        dos.writeShort(methodIndex);
    }

    @Override
    public void updateLength() {
        this.length = 4;
    }

    @Override
    public String toString() {
        String className = resolveClassName(classIndex);
        String methodName = methodIndex == 0 ? "None" : resolveMethodName(methodIndex);
        return "EnclosingMethodAttribute{" +
                "className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                '}';
    }

    private String resolveClassName(int classInfoIndex) {
        Item<?> classRefItem = getClassFile().getConstPool().getItem(classInfoIndex);
        if (classRefItem instanceof ClassRefItem) {
            int nameIndex = ((ClassRefItem) classRefItem).getValue();
            Item<?> utf8Item = getClassFile().getConstPool().getItem(nameIndex);
            if (utf8Item instanceof Utf8Item) {
                return ((Utf8Item) utf8Item).getValue().replace('/', '.');
            }
        }
        return "Unknown";
    }

    private String resolveMethodName(int methodIndex) {
        Item<?> methodRefItem = getClassFile().getConstPool().getItem(methodIndex);
        if (methodRefItem instanceof MethodRefItem) {
            MethodRefItem methodRef = (MethodRefItem) methodRefItem;
            Item<?> nameAndTypeItem = getClassFile().getConstPool().getItem(methodRef.getValue().getNameAndTypeIndex());
            if (nameAndTypeItem instanceof NameAndTypeRefItem) {
                NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) nameAndTypeItem;
                Item<?> nameItem = getClassFile().getConstPool().getItem(nameAndType.getValue().getNameIndex());
                if (nameItem instanceof Utf8Item) {
                    return ((Utf8Item) nameItem).getValue();
                }
            }
        }
        return "Unknown";
    }
}
