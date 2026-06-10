package com.tonic.analysis;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.DoubleItem;
import com.tonic.parser.constpool.FieldRefItem;
import com.tonic.parser.constpool.FloatItem;
import com.tonic.parser.constpool.IntegerItem;
import com.tonic.parser.constpool.InterfaceRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.LongItem;
import com.tonic.parser.constpool.MethodHandleItem;
import com.tonic.parser.constpool.MethodRefItem;
import com.tonic.parser.constpool.MethodTypeItem;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.parser.constpool.structure.MethodHandle;

/**
 * Shared formatting of constant-pool entries for disassembly. Centralizing this keeps a single
 * resolver for the per-instruction renderer ({@link InstructionRenderer}) and the verbose
 * enrichment context ({@link DisassemblyContext}).
 */
final class ConstPoolFormat {

    private ConstPoolFormat() {
    }

    /**
     * Resolves a member/string/class reference to a human-readable string.
     *
     * @param constPool the constant pool
     * @param index     the constant pool index
     * @return a readable representation, or {@code UnknownReference} if the entry is not a reference
     */
    static String reference(ConstPool constPool, int index) {
        Item<?> item = constPool.getItem(index);
        if (item instanceof MethodRefItem) {
            MethodRefItem ref = (MethodRefItem) item;
            return ref.getClassName().replace('/', '.') + "." + ref.getName() + ref.getDescriptor();
        } else if (item instanceof InterfaceRefItem) {
            InterfaceRefItem ref = (InterfaceRefItem) item;
            return ref.getOwner().replace('/', '.') + "." + ref.getName() + ref.getDescriptor();
        } else if (item instanceof FieldRefItem) {
            FieldRefItem ref = (FieldRefItem) item;
            return ref.getClassName().replace('/', '.') + "." + ref.getName() + " " + ref.getDescriptor();
        } else if (item instanceof StringRefItem) {
            StringRefItem stringItem = (StringRefItem) item;
            Utf8Item utf8 = (Utf8Item) constPool.getItem(stringItem.getValue());
            return "\"" + utf8.getValue() + "\"";
        } else if (item instanceof ClassRefItem) {
            return ((ClassRefItem) item).getClassName().replace('/', '.');
        }
        return "UnknownReference";
    }

    /**
     * Resolves any loadable constant (a {@code ldc}-style entry or a bootstrap argument): primitives,
     * strings, classes, method types and method handles.
     *
     * @param constPool the constant pool
     * @param index     the constant pool index
     * @return a readable representation of the constant
     */
    static String constant(ConstPool constPool, int index) {
        Item<?> item = constPool.getItem(index);
        if (item instanceof IntegerItem) {
            return String.valueOf(((IntegerItem) item).getValue());
        } else if (item instanceof LongItem) {
            return ((LongItem) item).getValue() + "L";
        } else if (item instanceof FloatItem) {
            return ((FloatItem) item).getValue() + "f";
        } else if (item instanceof DoubleItem) {
            return ((DoubleItem) item).getValue() + "d";
        } else if (item instanceof MethodTypeItem) {
            return methodType(constPool, (MethodTypeItem) item);
        } else if (item instanceof MethodHandleItem) {
            return methodHandle(constPool, (MethodHandleItem) item);
        }
        return reference(constPool, index);
    }

    /**
     * Resolves a {@code CONSTANT_MethodHandle} at the given index to {@code kind owner.name desc}.
     *
     * @param constPool the constant pool
     * @param index     the constant pool index of the method handle
     * @return a readable method-handle representation
     */
    static String methodHandle(ConstPool constPool, int index) {
        Item<?> item = constPool.getItem(index);
        if (item instanceof MethodHandleItem) {
            return methodHandle(constPool, (MethodHandleItem) item);
        }
        return "UnknownHandle";
    }

    private static String methodHandle(ConstPool constPool, MethodHandleItem item) {
        MethodHandle handle = item.getValue();
        return referenceKindName(handle.getReferenceKind()) + " " + reference(constPool, handle.getReferenceIndex());
    }

    private static String methodType(ConstPool constPool, MethodTypeItem item) {
        Item<?> descItem = constPool.getItem(item.getValue());
        if (descItem instanceof Utf8Item) {
            return ((Utf8Item) descItem).getValue();
        }
        return "MethodType#" + item.getValue();
    }

    /**
     * Maps a method-handle reference kind (JVMS 4.4.8) to its mnemonic.
     */
    private static String referenceKindName(int kind) {
        switch (kind) {
            case 1: return "getField";
            case 2: return "getStatic";
            case 3: return "putField";
            case 4: return "putStatic";
            case 5: return "invokeVirtual";
            case 6: return "invokeStatic";
            case 7: return "invokeSpecial";
            case 8: return "newInvokeSpecial";
            case 9: return "invokeInterface";
            default: return "kind" + kind;
        }
    }
}
