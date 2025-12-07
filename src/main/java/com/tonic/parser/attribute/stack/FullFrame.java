package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a FullFrame in the StackMapTable attribute.
 * Contains complete type information for all local variables and stack items.
 */
@Getter
public class FullFrame extends StackMapFrame {
    private final int offsetDelta;
    private final List<VerificationTypeInfo> locals;
    private final List<VerificationTypeInfo> stack;

    /**
     * Constructs a FullFrame by reading from a class file.
     */
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

    /**
     * Constructs a FullFrame programmatically.
     *
     * @param offsetDelta the offset delta from the previous frame
     * @param locals the local variable types
     * @param stack the operand stack types
     */
    public FullFrame(int offsetDelta, List<VerificationTypeInfo> locals, List<VerificationTypeInfo> stack) {
        super(255);
        this.offsetDelta = offsetDelta;
        this.locals = new ArrayList<>(locals);
        this.stack = new ArrayList<>(stack);
    }

    @Override
    protected void writeFrameData(DataOutputStream dos) throws IOException {
        dos.writeShort(offsetDelta);
        dos.writeShort(locals.size());
        for (VerificationTypeInfo local : locals) {
            local.write(dos);
        }
        dos.writeShort(stack.size());
        for (VerificationTypeInfo stackItem : stack) {
            stackItem.write(dos);
        }
    }

    @Override
    public int getLength() {
        int size = 1 + 2;
        size += 2;
        for (VerificationTypeInfo local : locals) {
            size += local.getLength();
        }
        size += 2;
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