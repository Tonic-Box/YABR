package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a SameLocals1StackItemFrame in the StackMapTable attribute.
 * Used when the frame has the same locals as the previous frame and one stack item.
 */
public class SameLocals1StackItemFrame extends StackMapFrame {
    private final VerificationTypeInfo stack;

    public SameLocals1StackItemFrame(int frameType, ClassFile classFile, ConstPool constPool) {
        super(frameType);
        this.stack = VerificationTypeInfo.readVerificationTypeInfo(classFile, constPool);
    }

    public SameLocals1StackItemFrame(int offsetDelta, VerificationTypeInfo stack) {
        super(64 + offsetDelta);
        this.stack = stack;
    }

    public VerificationTypeInfo getStack() {
        return stack;
    }

    @Override
    public int getOffsetDelta() {
        return frameType - 64;
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException {
        stack.write(dos);
    }

    @Override
    public int getLength() {
        return 1 + stack.getLength();
    }

    @Override
    public String toString() {
        return "SameLocals1StackItemFrame{frameType=" + frameType + ", stack=" + stack + "}";
    }
}