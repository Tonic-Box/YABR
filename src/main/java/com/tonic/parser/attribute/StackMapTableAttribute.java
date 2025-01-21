package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.stack.StackMapFrame;
import com.tonic.utill.Logger;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the StackMapTable attribute.
 * Provides type information for verification.
 */
@Getter
public class StackMapTableAttribute extends Attribute {
    private int numberOfEntries;
    private List<StackMapFrame> frames;

    public StackMapTableAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        int startIndex = classFile.getIndex(); // Record starting index

        this.numberOfEntries = classFile.readUnsignedShort();
        this.frames = new ArrayList<>(numberOfEntries);
        for (int i = 0; i < numberOfEntries; i++) {
            StackMapFrame frame = StackMapFrame.readFrame(classFile, parent.getClassFile().getConstPool());
            frames.add(frame);
        }

        int bytesRead = classFile.getIndex() - startIndex;
        if (bytesRead != length) {
            Logger.error("Warning: StackMapTableAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
            // Optionally, throw an exception or handle as needed
            // throw new IllegalStateException("StackMapTableAttribute read mismatch.");
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        // number_of_entries (u2)
        dos.writeShort(numberOfEntries);
        // each frame
        for (StackMapFrame frame : frames) {
            frame.write(dos);
        }
    }

    @Override
    public void updateLength() {
        // 2 bytes for number_of_entries, plus sum of each frame's length
        int size = 2;
        for (StackMapFrame frame : frames) {
            size += frame.getLength(); // you need a method getLength() in StackMapFrame
        }
        this.length = size;
    }


    @Override
    public String toString() {
        return "StackMapTableAttribute{" +
                "numberOfEntries=" + numberOfEntries +
                ", framesCount=" + frames.size() +
                '}';
    }
}
