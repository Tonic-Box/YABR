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

    /**
     * Constructs a SameFrameExtended by reading from a class file.
     *
     * @param frameType the frame type identifier
     * @param classFile the class file to read from
     * @param constPool the constant pool for resolving references
     */
    public SameFrameExtended(int frameType, ClassFile classFile, ConstPool constPool) {
        super(frameType);
        this.offsetDelta = classFile.readUnsignedShort();
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