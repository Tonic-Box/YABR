package com.tonic.parser.attribute.table;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

/**
 * Represents an entry in the LocalVariableTable attribute.
 * Describes a local variable's name, type, and scope within a method.
 */
public class LocalVariableTableEntry {
    private final ConstPool constPool;
    @Getter
    private final int startPc, lengthPc, nameIndex, descriptorIndex, index;

    /**
     * Constructs a local variable table entry.
     *
     * @param constPool the constant pool for resolving references
     * @param startPc the bytecode offset where the variable scope begins
     * @param lengthPc the length of the variable scope in bytecode
     * @param nameIndex constant pool index of the variable name
     * @param descriptorIndex constant pool index of the variable type descriptor
     * @param index the local variable index in the frame
     */
    public LocalVariableTableEntry(ConstPool constPool, int startPc, int lengthPc, int nameIndex, int descriptorIndex, int index) {
        this.constPool = constPool;
        this.startPc = startPc;
        this.lengthPc = lengthPc;
        this.nameIndex = nameIndex;
        this.descriptorIndex = descriptorIndex;
        this.index = index;
    }

    @Override
    public String toString() {
        String name = resolveUtf8(nameIndex);
        String descriptor = resolveUtf8(descriptorIndex);
        return "LocalVariableTableEntry{" +
                "startPc=" + startPc +
                ", lengthPc=" + lengthPc +
                ", name='" + name + '\'' +
                ", descriptor='" + descriptor + '\'' +
                ", index=" + index +
                '}';
    }

    private String resolveUtf8(int utf8Index) {
        Item<?> utf8Item = constPool.getItem(utf8Index);
        if (utf8Item instanceof Utf8Item) {
            return ((Utf8Item) utf8Item).getValue();
        }
        return "Unknown";
    }
}