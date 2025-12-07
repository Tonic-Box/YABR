package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.InvokeDynamicItem;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the INVOKEDYNAMIC instruction (0xBA).
 *
 * Per JVM spec, the instruction format is:
 * - opcode (1 byte)
 * - constant pool index to CONSTANT_InvokeDynamic_info (2 bytes)
 * - two reserved zero bytes (2 bytes)
 * Total: 5 bytes
 */
public class InvokeDynamicInstruction extends Instruction {
    @Getter
    private final int cpIndex;
    private final ConstPool constPool;

    /**
     * Constructs an InvokeDynamicInstruction.
     *
     * @param constPool The constant pool associated with the class.
     * @param opcode    The opcode of the instruction.
     * @param offset    The bytecode offset of the instruction.
     * @param cpIndex   The constant pool index to CONSTANT_InvokeDynamic_info.
     */
    public InvokeDynamicInstruction(ConstPool constPool, int opcode, int offset, int cpIndex) {
        super(opcode, offset, 5); // opcode + 2 bytes CP index + 2 reserved zero bytes
        if (opcode != 0xBA) {
            throw new IllegalArgumentException("Invalid opcode for InvokeDynamicInstruction: " + opcode);
        }
        this.cpIndex = cpIndex;
        this.constPool = constPool;
    }

    /**
     * Gets the bootstrap method attribute index from the InvokeDynamicItem.
     *
     * @return The bootstrap method attribute index, or -1 if invalid.
     */
    public int getBootstrapMethodAttrIndex() {
        var item = constPool.getItem(cpIndex);
        if (!(item instanceof InvokeDynamicItem)) {
            return -1;
        }
        return ((InvokeDynamicItem) item).getValue().getBootstrapMethodAttrIndex();
    }

    /**
     * Gets the name and type index from the InvokeDynamicItem.
     *
     * @return The name and type index, or -1 if invalid.
     */
    public int getNameAndTypeIndex() {
        var item = constPool.getItem(cpIndex);
        if (!(item instanceof InvokeDynamicItem)) {
            return -1;
        }
        return ((InvokeDynamicItem) item).getValue().getNameAndTypeIndex();
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the INVOKEDYNAMIC opcode and its operands to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeShort(cpIndex);
        dos.writeShort(0); // Two reserved zero bytes per JVM spec
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (depends on method signature).
     */
    @Override
    public int getStackChange() {
        var item = constPool.getItem(cpIndex);
        if (!(item instanceof InvokeDynamicItem)) {
            return 0;
        }
        InvokeDynamicItem idItem = (InvokeDynamicItem) item;
        int params = idItem.getParameterCount();
        int returnSlots = idItem.getReturnTypeSlots();
        return -params + returnSlots;
    }

    /**
     * Returns the change in local variables caused by this instruction.
     *
     * @return The local variables size change (none).
     */
    @Override
    public int getLocalChange() {
        return 0;
    }

    /**
     * Resolves and returns a string representation of the invokedynamic method.
     *
     * @return The invokedynamic method as a string.
     */
    public String resolveMethod() {
        if (cpIndex == 0) {
            return "NotInClassPool";
        }
        var item = constPool.getItem(cpIndex);
        if (!(item instanceof InvokeDynamicItem)) {
            return "InvalidCPItem(expected InvokeDynamic, got " + item.getClass().getSimpleName() + ")";
        }
        InvokeDynamicItem idItem = (InvokeDynamicItem) item;
        String name = idItem.getName();
        String desc = idItem.getDescriptor();
        return name + desc;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, CP index, and resolved method.
     */
    @Override
    public String toString() {
        return String.format("INVOKEDYNAMIC #%d // %s", cpIndex, resolveMethod());
    }
}
