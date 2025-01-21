package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a FullFrame.
 */
@Getter
public class FullFrame extends StackMapFrame {
    private final int offsetDelta;
    private final List<VerificationTypeInfo> locals;
    private final List<VerificationTypeInfo> stack;

    public FullFrame(int frameType, ClassFile classFile, ConstPool constPool) {
        super(frameType);
        this.offsetDelta = classFile.readUnsignedShort();
        int numberOfLocals = classFile.readUnsignedShort();
        this.locals = new ArrayList<>(numberOfLocals);
        for (int i = 0; i < numberOfLocals; i++) {
            VerificationTypeInfo local = VerificationTypeInfo.readVerificationTypeInfo(classFile, constPool);
            locals.add(local);
        }
        int numberOfStackItems = classFile.readUnsignedShort();
        this.stack = new ArrayList<>(numberOfStackItems);
        for (int i = 0; i < numberOfStackItems; i++) {
            VerificationTypeInfo stackItem = VerificationTypeInfo.readVerificationTypeInfo(classFile, constPool);
            stack.add(stackItem);
        }
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException {
        // offset_delta (u2)
        dos.writeShort(offsetDelta);

        // number_of_locals (u2)
        dos.writeShort(locals.size());
        // locals
        for (VerificationTypeInfo local : locals) {
            local.write(dos);
        }

        // number_of_stack_items (u2)
        dos.writeShort(stack.size());
        // stack
        for (VerificationTypeInfo stackItem : stack) {
            stackItem.write(dos);
        }
    }

    @Override
    public int getLength() {
        // 1 byte for frameType + 2 for offsetDelta
        // + 2 for number_of_locals + each local
        // + 2 for number_of_stack_items + each stack item
        int size = 1 + 2; // frameType + offsetDelta
        size += 2; // number_of_locals
        for (VerificationTypeInfo local : locals) {
            size += local.getLength();
        }
        size += 2; // number_of_stack_items
        for (VerificationTypeInfo stackItem : stack) {
            size += stackItem.getLength();
        }
        return size;
    }

    @Override
    public String toString() {
        return "FullFrame{" +
                "frameType=" + frameType +
                ", offsetDelta=" + offsetDelta +
                ", locals=" + locals +
                ", stack=" + stack +
                '}';
    }
}