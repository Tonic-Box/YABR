package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an AppendFrame.
 */
@Getter
public class AppendFrame extends StackMapFrame {
    private final int offsetDelta;
    private final List<VerificationTypeInfo> locals;

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
        // offset_delta (u2)
        dos.writeShort(offsetDelta);
        // locals
        for (VerificationTypeInfo local : locals) {
            local.write(dos);
        }
    }

    @Override
    public int getLength() {
        // The total size includes:
        //   1 byte for frameType (already in the "full" frame size)
        //   2 bytes for offsetDelta
        //   sum of each local's length
        int size = 1  // frameType itself
                + 2; // offsetDelta
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