package com.tonic.parser.attribute.stack;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a SameFrame in the StackMapTable attribute.
 */
public class SameFrame extends StackMapFrame
{
    /**
     * Constructs a SameFrame.
     * @param frameType the frame type identifier
     */
    public SameFrame(int frameType)
    {
        super(frameType);
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException
    {
    }

    @Override
    public int getOffsetDelta()
    {
        return frameType;
    }

    @Override
    public int getLength()
    {
        return 1;
    }

    @Override
    public String toString()
    {
        return "SameFrame{frameType=" + frameType + "}";
    }
}