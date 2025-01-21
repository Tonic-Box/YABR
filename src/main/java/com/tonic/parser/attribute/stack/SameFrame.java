package com.tonic.parser.attribute.stack;

import java.io.DataOutputStream;
import java.io.IOException;

/*
 * Represents a SameFrame.
 */
public class SameFrame extends StackMapFrame {
    public SameFrame(int frameType) {
        super(frameType);
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException {
        // No extra data to write
    }

    @Override
    public int getLength() {
        // Only the 1 byte for frameType
        return 1;
    }

    @Override
    public String toString() {
        return "SameFrame{frameType=" + frameType + "}";
    }
}