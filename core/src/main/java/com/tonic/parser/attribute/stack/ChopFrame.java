package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a ChopFrame in the StackMapTable attribute.
 * Used when the frame has fewer local variables than the previous frame.
 */
public class ChopFrame extends StackMapFrame {
    private final int offsetDelta;

    public ChopFrame(int frameType, ClassFile classFile) {
        super(frameType);
        this.offsetDelta = classFile.readUnsignedShort();
    }

    public ChopFrame(int frameType, int offsetDelta) {
        super(frameType);
        this.offsetDelta = offsetDelta;
    }

    @Override
    public int getOffsetDelta() {
        return offsetDelta;
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException {
        dos.writeShort(offsetDelta);
    }

    @Override
    public int getLength() {
        return 1 + 2;
    }

    @Override
    public String toString() {
        return "ChopFrame{frameType=" + frameType + ", offsetDelta=" + offsetDelta + "}";
    }
}