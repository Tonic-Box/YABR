package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.anotation.ElementValue;
import com.tonic.utill.Logger;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the AnnotationDefault attribute.
 * Specifies the default value for an annotation element.
 */
@Getter
public class AnnotationDefaultAttribute extends Attribute {
    private ElementValue defaultValue;

    public AnnotationDefaultAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        int startIndex = classFile.getIndex(); // Record starting index

        // Read the ElementValue
        this.defaultValue = ElementValue.readElementValue(classFile, parent.getClassFile().getConstPool());

        // Calculate bytes read
        int bytesRead = classFile.getIndex() - startIndex;

        if (bytesRead != length) {
            Logger.error("Warning: AnnotationDefaultAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
            // Optionally, throw an exception or handle as needed
            // throw new IllegalStateException("AnnotationDefaultAttribute read mismatch.");
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
