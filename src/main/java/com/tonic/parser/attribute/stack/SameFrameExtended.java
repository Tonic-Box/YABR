package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a SameFrameExtended.
 */
@Getter
public class SameFrameExtended extends StackMapFrame {
    private final int offsetDelta;

    public SameFrameExtended(int frameType, ClassFile classFile, ConstPool constPool) {
        super(frameType);
        this.offsetDelta = classFile.readUnsignedShort();
        // No verification types
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException {
        // offset_delta (u2)
        dos.writeShort(offsetDelta);
    }

    @Override
    public int getLength() {
        // 1 byte (frameType) + 2 bytes (offsetDelta)
        return 1 + 2;
    }

    @Override
    public String toString() {
        return "SameFrameExtended{frameType=" + frameType + ", offsetDelta=" + offsetDelta + "}";
    }
}