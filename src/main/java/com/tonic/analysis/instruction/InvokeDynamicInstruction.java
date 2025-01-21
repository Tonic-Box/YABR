package com.tonic.analysis.instruction;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.InvokeDynamicItem;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the INVOKEDYNAMIC instruction (0xBA).
 */
public class InvokeDynamicInstruction extends Instruction {
    private final int bootstrapMethodAttrIndex;
    private final int nameAndTypeIndex;
    private final ConstPool constPool;

    /**
     * Constructs an InvokeDynamicInstruction.
     *
     * @param constPool               The constant pool associated with the class.
     * @param opcode                  The opcode of the instruction.
     * @param offset                  The bytecode offset of the instruction.
     * @param bootstrapMethodAttrIndex The bootstrap method attribute index.
     * @param nameAndTypeIndex        The name and type index for the method.
     */
    public InvokeDynamicInstruction(ConstPool constPool, int opcode, int offset, int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
        super(opcode, offset, 5); // opcode + two bytes bootstrap index + two bytes name and type index
        if (opcode != 0xBA) {
            throw new IllegalArgumentException("Invalid opcode for InvokeDynamicInstruction: " + opcode);
        }
        this.bootstrapMethodAttrIndex = bootstrapMethodAttrIndex;
        this.nameAndTypeIndex = nameAndTypeIndex;
        this.constPool = constPool;
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
        dos.writeShort(bootstrapMethodAttrIndex);
        dos.writeShort(nameAndTypeIndex);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (depends on method signature).
     */
    @Override
    public int getStackChange() {
        InvokeDynamicItem method = (InvokeDynamicItem) constPool.getItem(nameAndTypeIndex);
        int params = method.getParameterCount();
        int returnSlots = method.getReturnTypeSlots();
        return -params + returnSlots; // Pops parameters, pushes return value
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
     * Returns the bootstrap method attribute index used by this instruction.
     *
     * @return The bootstrap method attribute index.
     */
    public int getBootstrapMethodAttrIndex() {
        return bootstrapMethodAttrIndex;
    }

    /**
     * Returns the name and type index used by this instruction.
     *
     * @return The name and type index.
     */
    public int getNameAndTypeIndex() {
        return nameAndTypeIndex;
    }

    /**
     * Resolves and returns a string representation of the invokedynamic method.
     *
     * @return The invokedynamic method as a string.
     */
    public String resolveMethod() {
        if(nameAndTypeIndex == 0)
        {
            return "NotInClassPool";
        }
        InvokeDynamicItem method = (InvokeDynamicItem) constPool.getItem(nameAndTypeIndex);
        return method.toString();
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, bootstrap index, name and type index, and resolved method.
     */
    @Override
    public String toString() {
        return String.format("INVOKEDYNAMIC #%d #%d // %s", bootstrapMethodAttrIndex, nameAndTypeIndex, resolveMethod());
    }
}
