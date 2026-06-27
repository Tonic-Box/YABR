package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.StackMapTableAttribute;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a SameLocals1StackItemFrameExtended in the StackMapTable attribute.
 * Extended version with explicit offset delta and one stack item.
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

    public SameLocals1StackItemFrameExtended(int offsetDelta, VerificationTypeInfo stack) {
        super(247);
        this.offsetDelta = offsetDelta;
        this.stack = stack;
    }

    @Override
    public int getOffsetDelta() {
        return offsetDelta;
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException {
        dos.writeShort(offsetDelta);
        stack.write(dos);
    }

    @Override
    public int getLength() {
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