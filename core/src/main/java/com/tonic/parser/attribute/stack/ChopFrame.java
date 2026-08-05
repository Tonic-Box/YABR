package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a ChopFrame in the StackMapTable attribute.
 * Used when the frame has fewer local variables than the previous frame.
 */
public class ChopFrame extends StackMapFrame
{
    private final int offsetDelta;

    /**
     * Reads the offset delta from the class file at the current position.
     * @param frameType the raw frame type tag
     * @param classFile the source being read
     */
    public ChopFrame(int frameType, ClassFile classFile)
    {
        super(frameType);
        this.offsetDelta = classFile.readUnsignedShort();
    }

    /**
     * Creates a frame with an explicit offset delta.
     * @param frameType the raw frame type tag
     * @param offsetDelta the bytecode offset delta from the previous frame
     */
    public ChopFrame(int frameType, int offsetDelta)
    {
        super(frameType);
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
        return "ChopFrame{frameType=" + frameType + ", offsetDelta=" + offsetDelta + "}";
    }
}