package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a SameLocals1StackItemFrame in the StackMapTable attribute.
 * Used when the frame has the same locals as the previous frame and one stack item.
 */
public class SameLocals1StackItemFrame extends StackMapFrame
{
    private final VerificationTypeInfo stack;

    /**
     * Reads the single stack entry for this frame from the class file.
     * @param frameType the raw frame tag, 64 plus the offset delta
     * @param classFile the class file being read
     * @param constPool the constant pool backing verification type references
     */
    public SameLocals1StackItemFrame(int frameType, ClassFile classFile, ConstPool constPool)
    {
        super(frameType);
        this.stack = VerificationTypeInfo.readVerificationTypeInfo(classFile, constPool);
    }

    /**
     * Builds a frame directly, encoding the delta into the frame tag.
     * @param offsetDelta the bytecode offset delta, 0 to 63
     * @param stack the single stack entry
     */
    public SameLocals1StackItemFrame(int offsetDelta, VerificationTypeInfo stack)
    {
        super(64 + offsetDelta);
        this.stack = stack;
    }

    /**
     * @return the stack
     */
    public VerificationTypeInfo getStack()
    {
        return stack;
    }

    @Override
    public int getOffsetDelta()
    {
        return frameType - 64;
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException
    {
        stack.write(dos);
    }

    @Override
    public int getLength()
    {
        return 1 + stack.getLength();
    }

    @Override
    public String toString()
    {
        return "SameLocals1StackItemFrame{frameType=" + frameType + ", stack=" + stack + "}";
    }
}