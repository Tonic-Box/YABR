package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.anotation.Annotation;
import com.tonic.utill.Logger;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the RuntimeVisibleAnnotations or RuntimeInvisibleAnnotations attribute.
 * Stores annotations that are either visible or invisible at runtime.
 */
@Getter
public class RuntimeVisibleAnnotationsAttribute extends Attribute {
    private List<Annotation> annotations;
    private final boolean visible;

    public RuntimeVisibleAnnotationsAttribute(String name, MemberEntry parent, boolean visible, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
        this.visible = visible;
    }

    public RuntimeVisibleAnnotationsAttribute(String name, ClassFile parent, boolean visible, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
        this.visible = visible;
    }

    @Override
    public void read(ClassFile classFile, int length) {
        int startIndex = classFile.getIndex();

        if (length < 2) {
            throw new IllegalArgumentException("Annotations attribute length must be at least 2, found: " + length);
        }
        int numAnnotations = classFile.readUnsignedShort();
        this.annotations = new ArrayList<>(numAnnotations);
        for (int i = 0; i < numAnnotations; i++) {
            Annotation annotation = Annotation.readAnnotation(classFile, getClassFile().getConstPool());
            annotations.add(annotation);
        }

        int bytesRead = classFile.getIndex() - startIndex;
        if (bytesRead != length) {
            Logger.error("Warning: " + (visible ? "RuntimeVisible" : "RuntimeInvisible") +
                    "AnnotationsAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(annotations.size());
        for (Annotation ann : annotations) {
            ann.write(dos);
        }
    }

    @Override
    public void updateLength() {
        int size = 2;
        for (Annotation ann : annotations) {
            size += ann.getLength();
        }
        this.length = size;
    }

    @Override
    public String toString() {
        return (visible ? "RuntimeVisibleAnnotationsAttribute" : "RuntimeInvisibleAnnotationsAttribute") +
                "{annotations=" + annotations + "}";
    }
}

