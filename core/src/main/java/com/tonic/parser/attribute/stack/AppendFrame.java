package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an AppendFrame in the StackMapTable attribute.
 * Used when the frame has additional local variables compared to the previous frame.
 */
public class AppendFrame extends StackMapFrame
{
    private final int offsetDelta;
    private final List<VerificationTypeInfo> locals;

    /**
     * Reads the offset delta and the frame type minus 251 appended locals from the class file.
     * @param frameType the frame tag, in the range 252 to 254
     * @param classFile the class file positioned at the frame body
     * @param constPool the pool used to resolve object verification types
     */
    public AppendFrame(int frameType, ClassFile classFile, ConstPool constPool)
    {
        super(frameType);
        int numberOfLocals = frameType - 251;
        this.offsetDelta = classFile.readUnsignedShort();
        this.locals = new ArrayList<>(numberOfLocals);
        for (int i = 0; i < numberOfLocals; i++)
        {
            VerificationTypeInfo local = VerificationTypeInfo.readVerificationTypeInfo(classFile, constPool);
            locals.add(local);
        }
    }

    /**
     * Creates a frame from already-decoded values, copying the locals list.
     * @param frameType the frame tag, in the range 252 to 254
     * @param offsetDelta the bytecode offset delta from the previous frame
     * @param locals the appended local verification types
     */
    public AppendFrame(int frameType, int offsetDelta, List<VerificationTypeInfo> locals)
    {
        super(frameType);
        this.offsetDelta = offsetDelta;
        this.locals = new ArrayList<>(locals);
    }

    /**
     * @return the locals
     */
    public List<VerificationTypeInfo> getLocals()
    {
        return locals;
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
        for (VerificationTypeInfo local : locals)
        {
            local.write(dos);
        }
    }

    @Override
    public int getLength()
    {
        int size = 1 + 2;
        for (VerificationTypeInfo local : locals)
        {
            size += local.getLength();
        }
        return size;
    }

    @Override
    public String toString()
    {
        return "AppendFrame{" +
                "frameType=" + frameType +
                ", offsetDelta=" + offsetDelta +
                ", locals=" + locals +
                '}';
    }
}