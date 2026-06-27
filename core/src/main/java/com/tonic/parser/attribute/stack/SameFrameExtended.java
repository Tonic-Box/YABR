package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a SameFrameExtended in the StackMapTable attribute.
 * Extended version of SameFrame with explicit offset delta.
 */
@Getter
public class SameFrameExtended extends StackMapFrame {
    private final int offsetDelta;

    public SameFrameExtended(int frameType, ClassFile classFile, ConstPool constPool) {
        super(frameType);
        this.offsetDelta = classFile.readUnsignedShort();
    }

    public SameFrameExtended(int offsetDelta) {
        super(251);
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
        return "SameFrameExtended{frameType=" + frameType + ", offsetDelta=" + offsetDelta + "}";
    }
}