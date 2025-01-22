package com.tonic.parser.constpool;

import com.tonic.analysis.visitor.AbstractClassVisitor;
import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Abstract representation of a constant pool item.
 *
 * @param <T> The type of the value held by the constant pool item.
 */
public abstract class Item<T> {
    // Constant Pool Tags
    public static final byte ITEM_UTF_8         =  0x1;
    public static final byte ITEM_INTEGER       =  0x3;
    public static final byte ITEM_FLOAT         =  0x4;
    public static final byte ITEM_LONG          =  0x5;
    public static final byte ITEM_DOUBLE        =  0x6;
    public static final byte ITEM_CLASS_REF     =  0x7;
    public static final byte ITEM_STRING_REF    =  0x8;
    public static final byte ITEM_FIELD_REF     =  0x9;
    public static final byte ITEM_METHOD_REF    =  0xA;
    public static final byte ITEM_INTERFACE_REF =  0xB;
    public static final byte ITEM_NAME_TYPE_REF =  0xC;
    public static final byte ITEM_METHOD_HANDLE =  0xF;
    public static final byte ITEM_METHOD_TYPE   = 0x10;
    public static final byte ITEM_INVOKEDYNAMIC = 0x12;
    public static final byte ITEM_PACKAGE = 0x13; // CONSTANT_Package
    public static final byte ITEM_MODULE  = 0x14; // CONSTANT_Module

    /**
     * Reads the constant pool item from the class file.
     *
     * @param classFile The ClassFile utility to read data.
     */
    public abstract void read(ClassFile classFile);

    /**
     * Writes the item to the output stream (excluding the tag byte, which is written by the caller).
     */
    public abstract void write(DataOutputStream dos) throws IOException;

    /**
     * Returns the type of the constant pool item.
     *
     * @return The constant pool tag.
     */
    public abstract byte getType();

    /**
     * Returns the value held by the constant pool item.
     *
     * @return The value of type T.
     */
    public abstract T getValue();

    public void accept(AbstractClassVisitor visitor)
    {
        visitor.visitConstPoolItem(this);
    }
}
