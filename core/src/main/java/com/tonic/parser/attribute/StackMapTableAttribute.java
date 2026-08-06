package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.stack.StackMapFrame;
import com.tonic.util.Logger;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The StackMapTable attribute: verification frames describing local and stack types at branch targets.
 */
public class StackMapTableAttribute extends Attribute
{
    private int numberOfEntries;
    private List<StackMapFrame> frames;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public StackMapTableAttribute(String name, MemberEntry parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
        this.frames = new ArrayList<>();
    }

    /**
     * Creates the attribute shell for parsing, attached to a class.
     * @param name the attribute name
     * @param parent the class the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public StackMapTableAttribute(String name, ClassFile parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
        this.frames = new ArrayList<>();
    }

    /**
     * @return the number of entries
     */
    public int getNumberOfEntries()
    {
        return numberOfEntries;
    }

    /**
     * @return the frames
     */
    public List<StackMapFrame> getFrames()
    {
        return frames;
    }

    /**
     * Sets the frames for this StackMapTable.
     * @param frames The list of StackMapFrame entries
     */
    public void setFrames(List<StackMapFrame> frames)
    {
        this.frames = new ArrayList<>(frames);
        this.numberOfEntries = frames.size();
    }

    /**
     * Creates a new StackMapTableAttribute with the given frames.
     * @param name The attribute name ("StackMapTable")
     * @param parent The parent MemberEntry (method)
     * @param nameIndex The constant pool index for the attribute name
     * @param frames The list of frames
     * @return A new StackMapTableAttribute
     */
    public static StackMapTableAttribute create(String name, MemberEntry parent, int nameIndex, List<StackMapFrame> frames)
    {
        StackMapTableAttribute attr = new StackMapTableAttribute(name, parent, nameIndex, 0);
        attr.setFrames(frames);
        attr.updateLength();
        return attr;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        int startIndex = classFile.getIndex();

        this.numberOfEntries = classFile.readUnsignedShort();
        this.frames = new ArrayList<>(numberOfEntries);
        for (int i = 0; i < numberOfEntries; i++)
        {
            StackMapFrame frame = StackMapFrame.readFrame(classFile, parent.getClassFile().getConstPool());
            frames.add(frame);
        }

        int bytesRead = classFile.getIndex() - startIndex;
        if (bytesRead != length)
        {
            Logger.error("Warning: StackMapTableAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.writeShort(numberOfEntries);
        for (StackMapFrame frame : frames)
        {
            frame.write(dos);
        }
    }

    @Override
    public void updateLength()
    {
        int size = 2;
        for (StackMapFrame frame : frames)
        {
            size += frame.getLength();
        }
        this.length = size;
    }


    @Override
    public String toString()
    {
        return "StackMapTableAttribute{" +
                "numberOfEntries=" + numberOfEntries +
                ", framesCount=" + frames.size() +
                '}';
    }
}
