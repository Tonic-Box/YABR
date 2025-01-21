package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.StackMapTableAttribute;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a SameLocals1StackItemFrame.
 */
@Getter
public class SameLocals1StackItemFrame extends StackMapFrame {
    private final VerificationTypeInfo stack;

    public SameLocals1StackItemFrame(int frameType, ClassFile classFile, ConstPool constPool) {
        super(frameType);
        this.stack = VerificationTypeInfo.readVerificationTypeInfo(classFile, constPool);
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException {
        // 1 stack item
        stack.write(dos);
    }

    @Override
    public int getLength() {
        // 1 byte for frameType + stack item length
        return 1 + stack.getLength();
    }

    @Override
    public String toString() {
        return "SameLocals1StackItemFrame{frameType=" + frameType + ", stack=" + stack + "}";
    }
}