package com.tonic.parser.attribute.table;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

/**
 * Represents an entry in the LocalVariableTypeTable attribute.
 * Describes a local variable's generic signature and scope within a method.
 */
public class LocalVariableTypeTableEntry {
    private final ConstPool constPool;
    @Getter
    private final int startPc, lengthPc, nameIndex, signatureIndex, index;

    /**
     * Constructs a local variable type table entry.
     *
     * @param constPool the constant pool for resolving references
     * @param startPc the bytecode offset where the variable scope begins
     * @param lengthPc the length of the variable scope in bytecode
     * @param nameIndex constant pool index of the variable name
     * @param signatureIndex constant pool index of the generic signature
     * @param index the local variable index in the frame
     */
    public LocalVariableTypeTableEntry(ConstPool constPool, int startPc, int lengthPc, int nameIndex, int signatureIndex, int index) {
        this.constPool = constPool;
        this.startPc = startPc;
        this.lengthPc = lengthPc;
        this.nameIndex = nameIndex;
        this.signatureIndex = signatureIndex;
        this.index = index;
    }

    @Override
    public String toString() {
        String name = resolveUtf8(nameIndex);
        String signature = resolveUtf8(signatureIndex);
        return "LocalVariableTypeTableEntry{" +
                "startPc=" + startPc +
                ", lengthPc=" + lengthPc +
                ", name='" + name + '\'' +
                ", signature='" + signature + '\'' +
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