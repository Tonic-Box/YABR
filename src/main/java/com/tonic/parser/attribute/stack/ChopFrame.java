package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a ChopFrame in the StackMapTable attribute.
 * Used when the frame has fewer local variables than the previous frame.
 */
@Getter
public class ChopFrame extends StackMapFrame {
    private final int offsetDelta;

    /**
     * Constructs a ChopFrame by reading from a class file.
     *
     * @param frameType the frame type identifier
     * @param classFile the class file to read from
     * @param constPool the constant pool for resolving references
     */
    public ChopFrame(int frameType, ClassFile classFile, ConstPool constPool) {
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
        return "ChopFrame{frameType=" + frameType + ", offsetDelta=" + offsetDelta + "}";
    }
}