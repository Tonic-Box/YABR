package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.annotation.ElementValue;
import com.tonic.util.Logger;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the AnnotationDefault attribute.
 * Specifies the default value for an annotation element.
 */
public class AnnotationDefaultAttribute extends Attribute {
    private ElementValue defaultValue;

    public AnnotationDefaultAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public AnnotationDefaultAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public ElementValue getDefaultValue() {
        return defaultValue;
    }

    @Override
    public void read(ClassFile classFile, int length) {
        int startIndex = classFile.getIndex();

        this.defaultValue = ElementValue.readElementValue(classFile, getClassFile().getConstPool());

        int bytesRead = classFile.getIndex() - startIndex;

        if (bytesRead != length) {
            Logger.error("Warning: AnnotationDefaultAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        defaultValue.write(dos);
    }

    @Override
    public void updateLength() {
        this.length = defaultValue.getLength();
    }

    @Override
    public String toString() {
        return "AnnotationDefaultAttribute{defaultValue=" + defaultValue + "}";
    }
}
