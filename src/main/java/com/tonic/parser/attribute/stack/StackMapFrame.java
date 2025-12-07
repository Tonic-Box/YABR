package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.utill.Logger;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Base class for stack map frames in the StackMapTable attribute.
 * Stack map frames describe the type state at specific bytecode offsets.
 */
@Getter
public abstract class StackMapFrame {
    protected int frameType;

    /**
     * Constructs a StackMapFrame with the specified frame type.
     *
     * @param frameType the frame type identifier
     */
    public StackMapFrame(int frameType) {
        this.frameType = frameType;
    }

    /**
     * Reads a stack map frame from the class file.
     *
     * @param classFile the class file to read from
     * @param constPool the constant pool for resolving references
     * @return the parsed StackMapFrame
     */
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
     * Writes this stack map frame to the output stream.
     *
     * @param dos the output stream to write to
     * @throws IOException if an I/O error occurs
     */
    public final void write(DataOutputStream dos) throws IOException {
        dos.writeByte(frameType);
        writeFrameData(dos);
    }

    /**
     * Writes the frame-specific data to the output stream.
     *
     * @param dos the output stream to write to
     * @throws IOException if an I/O error occurs
     */
    protected abstract void writeFrameData(DataOutputStream dos) throws IOException;

    /**
     * Returns the total length of this frame in bytes.
     *
     * @return the length in bytes
     */
    public abstract int getLength();

    @Override
    public String toString() {
        return "StackMapFrame{frameType=" + frameType + "}";
    }
}