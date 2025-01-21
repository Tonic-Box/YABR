package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.utill.Logger;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a single StackMapFrame.
 */
@Getter
public abstract class StackMapFrame {
    protected int frameType;

    public StackMapFrame(int frameType) {
        this.frameType = frameType;
    }

    public static StackMapFrame readFrame(ClassFile classFile, ConstPool constPool) {
        int frameStartIndex = classFile.getIndex();
        Logger.info("StackMapFrame: Reading frame at byte index: " + frameStartIndex);

        int frameType = classFile.readUnsignedByte();
        StackMapFrame frame;

        if (frameType >= 0 && frameType <= 63) {
            frame = new SameFrame(frameType);
        } else if (frameType >= 64 && frameType <= 127) {
            frame = new SameLocals1StackItemFrame(frameType, classFile, constPool);
        } else if (frameType == 247) {
            frame = new SameLocals1StackItemFrameExtended(frameType, classFile, constPool);
        } else if (frameType >= 248 && frameType <= 250) {
            frame = new ChopFrame(frameType, classFile, constPool);
        } else if (frameType == 251) {
            frame = new SameFrameExtended(frameType, classFile, constPool);
        } else if (frameType >= 252 && frameType <= 254) {
            frame = new AppendFrame(frameType, classFile, constPool);
        } else if (frameType == 255) {
            frame = new FullFrame(frameType, classFile, constPool);
        } else {
            throw new IllegalArgumentException("Unknown StackMapFrame type: " + frameType);
        }

        int frameEndIndex = classFile.getIndex();
        int bytesConsumed = frameEndIndex - frameStartIndex;
        Logger.info("StackMapFrame: frameType=" + frameType + ", bytesConsumed=" + bytesConsumed);

        return frame;
    }

    /**
     * Writes this StackMapFrame to the output stream.
     *  - Writes the frameType (1 byte)
     *  - Then calls {@code writeFrameData(dos)} in the subclass to handle the rest.
     */
    public final void write(DataOutputStream dos) throws IOException {
        // 1. Write the frame type
        dos.writeByte(frameType);
        // 2. Subclass writes any additional data
        writeFrameData(dos);
    }

    /**
     * Each concrete subclass must implement how it writes the rest of its data.
     */
    protected abstract void writeFrameData(DataOutputStream dos) throws IOException;

    /**
     * If you maintain lengths, you may also declare an abstract getLength() here.
     */
    public abstract int getLength();

    @Override
    public String toString() {
        return "StackMapFrame{frameType=" + frameType + "}";
    }
}