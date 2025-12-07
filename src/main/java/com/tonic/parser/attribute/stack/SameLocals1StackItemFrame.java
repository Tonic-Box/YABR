package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.StackMapTableAttribute;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a SameLocals1StackItemFrame in the StackMapTable attribute.
 * Used when the frame has the same locals as the previous frame and one stack item.
 */
@Getter
public class SameLocals1StackItemFrame extends StackMapFrame {
    private final VerificationTypeInfo stack;

    /**
     * Constructs a SameLocals1StackItemFrame by reading from a class file.
     *
     * @param frameType the frame type identifier
     * @param classFile the class file to read from
     * @param constPool the constant pool for resolving references
     */
    public SameLocals1StackItemFrame(int frameType, ClassFile classFile, ConstPool constPool) {
        super(frameType);
        this.stack = VerificationTypeInfo.readVerificationTypeInfo(classFile, constPool);
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