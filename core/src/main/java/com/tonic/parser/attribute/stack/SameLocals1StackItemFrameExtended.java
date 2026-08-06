package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a SameLocals1StackItemFrameExtended in the StackMapTable attribute.
 */
public class SameLocals1StackItemFrameExtended extends StackMapFrame
{
    private final int offsetDelta;
    private final VerificationTypeInfo stack;

    /**
     * Reads the offset delta and stack item from the class file's current position.
     * @param frameType raw frame type tag
     * @param classFile source being read
     * @param constPool pool used to resolve object verification types
     */
    public SameLocals1StackItemFrameExtended(int frameType, ClassFile classFile, ConstPool constPool)
    {
        super(frameType);
        this.offsetDelta = classFile.readUnsignedShort();
        this.stack = VerificationTypeInfo.readVerificationTypeInfo(classFile, constPool);
    }

    /**
     * Creates a frame with frame type 247.
     * @param offsetDelta bytecode offset delta from the previous frame
     * @param stack the single stack item
     */
    public SameLocals1StackItemFrameExtended(int offsetDelta, VerificationTypeInfo stack)
    {
        super(247);
        this.offsetDelta = offsetDelta;
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
        return offsetDelta;
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException
    {
        dos.writeShort(offsetDelta);
        stack.write(dos);
    }

    @Override
    public int getLength()
    {
        return 1 + 2 + stack.getLength();
    }

    @Override
    public String toString()
    {
        return "SameLocals1StackItemFrameExtended{" +
                "frameType=" + frameType +
                ", offsetDelta=" + offsetDelta +
                ", stack=" + stack +
                '}';
    }
}