package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A same_frame_extended stack map entry - unchanged type state, with the offset delta stored explicitly.
 */
public class SameFrameExtended extends StackMapFrame
{
    private final int offsetDelta;

    /**
     * Reads the frame's offset delta from the class file at its current position.
     * @param frameType the frame tag already consumed, always 251
     * @param classFile the class file being parsed
     */
    public SameFrameExtended(int frameType, ClassFile classFile)
    {
        super(frameType);
        this.offsetDelta = classFile.readUnsignedShort();
    }

    /**
     * Creates a frame with tag 251 for emission.
     * @param offsetDelta bytecode offset delta from the previous frame
     */
    public SameFrameExtended(int offsetDelta)
    {
        super(251);
        this.offsetDelta = offsetDelta;
    }

    @Override
    public int getOffsetDelta()
    {
        return offsetDelta;
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException
    {
        dos.writeShort(offsetDelta);
    }

    @Override
    public int getLength()
    {
        return 1 + 2;
    }

    @Override
    public String toString()
    {
        return "SameFrameExtended{frameType=" + frameType + ", offsetDelta=" + offsetDelta + "}";
    }
}