package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an AppendFrame in the StackMapTable attribute.
 * Used when the frame has additional local variables compared to the previous frame.
 */
@Getter
public class AppendFrame extends StackMapFrame {
    private final int offsetDelta;
    private final List<VerificationTypeInfo> locals;

    /**
     * Constructs an AppendFrame by reading from a class file.
     *
     * @param frameType the frame type identifier
     * @param classFile the class file to read from
     * @param constPool the constant pool for resolving references
     */
    public AppendFrame(int frameType, ClassFile classFile, ConstPool constPool) {
        super(frameType);
        int numberOfLocals = frameType - 251;
        this.offsetDelta = classFile.readUnsignedShort();
        this.locals = new ArrayList<>(numberOfLocals);
        for (int i = 0; i < numberOfLocals; i++) {
            VerificationTypeInfo local = VerificationTypeInfo.readVerificationTypeInfo(classFile, constPool);
            locals.add(local);
        }
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException {
        dos.writeShort(offsetDelta);
        for (VerificationTypeInfo local : locals) {
            local.write(dos);
        }
    }

    @Override
    public int getLength() {
        int size = 1 + 2;
        for (VerificationTypeInfo local : locals) {
            size += local.getLength();
        }
        return size;
    }

    @Override
    public String toString() {
        return "AppendFrame{" +
                "frameType=" + frameType +
                ", offsetDelta=" + offsetDelta +
                ", locals=" + locals +
                '}';
    }
}