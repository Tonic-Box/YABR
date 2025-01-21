package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.StackMapTableAttribute;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a SameLocals1StackItemFrameExtended.
 */
@Getter
public class SameLocals1StackItemFrameExtended extends StackMapFrame {
    private final int offsetDelta;
    private final VerificationTypeInfo stack;

    public SameLocals1StackItemFrameExtended(int frameType, ClassFile classFile, ConstPool constPool) {
        super(frameType);
        this.offsetDelta = classFile.readUnsignedShort();
        this.stack = VerificationTypeInfo.readVerificationTypeInfo(classFile, constPool);
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException {
        // offset_delta (u2)
        dos.writeShort(offsetDelta);
        // stack item
        stack.write(dos);
    }

    @Override
    public int getLength() {
        // 1 byte for frameType + 2 bytes for offset_delta + stack item
        return 1 + 2 + stack.getLength();
    }

    @Override
    public String toString() {
        return "SameLocals1StackItemFrameExtended{" +
                "frameType=" + frameType +
                ", offsetDelta=" + offsetDelta +
                ", stack=" + stack +
                '}';
    }
}