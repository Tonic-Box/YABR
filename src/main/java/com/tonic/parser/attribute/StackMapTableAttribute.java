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
        this.frames = new ArrayList<>();
    }

    public StackMapTableAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
        this.frames = new ArrayList<>();
    }

    /**
     * Sets the frames for this StackMapTable.
     * Used when programmatically generating frames.
     *
     * @param frames The list of StackMapFrame entries
     */
    public void setFrames(List<StackMapFrame> frames) {
        this.frames = new ArrayList<>(frames);
        this.numberOfEntries = frames.size();
    }

    /**
     * Creates a new StackMapTableAttribute with the given frames.
     *
     * @param name The attribute name ("StackMapTable")
     * @param parent The parent MemberEntry (method)
     * @param nameIndex The constant pool index for the attribute name
     * @param frames The list of frames
     * @return A new StackMapTableAttribute
     */
    public static StackMapTableAttribute create(String name, MemberEntry parent, int nameIndex, List<StackMapFrame> frames) {
        StackMapTableAttribute attr = new StackMapTableAttribute(name, parent, nameIndex, 0);
        attr.setFrames(frames);
        attr.updateLength();
        return attr;
    }

    @Override
    public void read(ClassFile classFile, int length) {
        int startIndex = classFile.getIndex();

        this.numberOfEntries = classFile.readUnsignedShort();
        this.frames = new ArrayList<>(numberOfEntries);
        for (int i = 0; i < numberOfEntries; i++) {
            StackMapFrame frame = StackMapFrame.readFrame(classFile, parent.getClassFile().getConstPool());
            frames.add(frame);
        }

        int bytesRead = classFile.getIndex() - startIndex;
        if (bytesRead != length) {
            Logger.error("Warning: StackMapTableAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(numberOfEntries);
        for (StackMapFrame frame : frames) {
            frame.write(dos);
        }
    }

    @Override
    public void updateLength() {
        int size = 2;
        for (StackMapFrame frame : frames) {
            size += frame.getLength();
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
