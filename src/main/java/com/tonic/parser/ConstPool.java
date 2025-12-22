package com.tonic.parser;

import com.tonic.parser.constpool.*;
import com.tonic.parser.constpool.structure.FieldRef;
import com.tonic.parser.constpool.structure.InterfaceRef;
import com.tonic.parser.constpool.structure.MethodHandle;
import com.tonic.parser.constpool.structure.MethodRef;
import com.tonic.parser.constpool.structure.NameAndType;
import com.tonic.utill.Logger;
import lombok.Getter;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the Constant Pool of a Java class file.
 * Parses and stores all constant pool entries.
 */
@Getter
public class ConstPool {
    private ClassFile classFile;
    private final List<Item<?>> items;

    /**
     * Constructs a ConstPool by parsing constant pool entries from the given ClassFile.
     *
     * @param classFile the ClassFile to read data from
     */
    public ConstPool(final ClassFile classFile) {
        this.classFile = classFile;
        this.classFile.setConstPool(this);
        final int constantPoolCount = classFile.readUnsignedShort();
        this.items = new ArrayList<>();

        items.add(null);

        for (int i = 1; i < constantPoolCount; i++) {
            byte tag = (byte) classFile.readUnsignedByte();
            Item<?> item;
            switch (tag) {
                case Item.ITEM_UTF_8:
                    item = new Utf8Item();
                    break;
                case Item.ITEM_INTEGER:
                    item = new IntegerItem();
                    break;
                case Item.ITEM_FLOAT:
                    item = new FloatItem();
                    break;
                case Item.ITEM_LONG:
                    item = new LongItem();
                    break;
                case Item.ITEM_DOUBLE:
                    item = new DoubleItem();
                    break;
                case Item.ITEM_CLASS_REF:
                    item = new ClassRefItem();
                    break;
                case Item.ITEM_STRING_REF:
                    item = new StringRefItem();
                    break;
                case Item.ITEM_FIELD_REF:
                    item = new FieldRefItem();
                    break;
                case Item.ITEM_METHOD_REF:
                    item = new MethodRefItem();
                    break;
                case Item.ITEM_INTERFACE_REF:
                    item = new InterfaceRefItem();
                    break;
                case Item.ITEM_NAME_TYPE_REF:
                    item = new NameAndTypeRefItem();
                    break;
                case Item.ITEM_METHOD_HANDLE:
                    item = new MethodHandleItem();
                    break;
                case Item.ITEM_METHOD_TYPE:
                    item = new MethodTypeItem();
                    break;
                case Item.ITEM_DYNAMIC:
                    item = new ConstantDynamicItem();
                    break;
                case Item.ITEM_INVOKEDYNAMIC:
                    item = new InvokeDynamicItem();
                    break;
                case Item.ITEM_PACKAGE:
                    item = new PackageItem();
                    break;
                case Item.ITEM_MODULE:
                    item = new ModuleItem();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown constant pool tag: " + tag + " at index " + i);
            }

            item.read(classFile);
            item.setClassFile(classFile);
            items.add(item);

            Logger.info("Parsed constant pool entry " + i + ": " + item.getClass().getName());

            if (tag == Item.ITEM_LONG || tag == Item.ITEM_DOUBLE) {
                items.add(null);
                Logger.info("Long/Double entry at " + i + ", skipping next index");
                i++;
            }
        }
    }

    /**
     * Constructs an empty ConstPool.
     */
    public ConstPool() {
        this.classFile = null;
        this.items = new ArrayList<>();
        this.items.add(null);
    }

    /**
     * Sets the ClassFile reference for this constant pool.
     *
     * @param classFile the ClassFile to associate with this pool
     */
    public void setClassFile(ClassFile classFile) {
        this.classFile = classFile;
        for (Item<?> item : items) {
            if (item instanceof MethodRefItem) {
                ((MethodRefItem) item).setClassFile(classFile);
            }
        }
    }


    /**
     * Retrieves the constant pool item at the specified index.
     *
     * @param index the 1-based index of the constant pool item
     * @return the constant pool item, or null if pointing to an unused slot
     * @throws IllegalArgumentException if the index is out of bounds
     */
    public Item<?> getItem(final int index) {
        if (index <= 0 || index >= items.size()) {
            throw new IllegalArgumentException("Constant pool index out of bounds: " + index);
        }
        return items.get(index);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Constant Pool:\n");
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item != null) {
                sb.append(i).append(": ").append(item.getClass().getSimpleName()).append("\n");
            } else {
                sb.append(i).append(": <unused>\n");
            }
        }
        return sb.toString();
    }

    /**
     * Adds a new item to the constant pool.
     *
     * @param newItem the item to add
     * @return the index where the item was added
     * @throws IllegalStateException if the ClassFile reference is not set
     */
    public int addItem(Item<?> newItem) {
        if(classFile != null) {
            newItem.setClassFile(classFile);
        } else {
            throw new IllegalStateException("Cannot add item to constant pool without a ClassFile reference.");
        }

        int index = items.size();
        items.add(newItem);

        if (newItem.getType() == Item.ITEM_LONG || newItem.getType() == Item.ITEM_DOUBLE) {
            items.add(null);
        }

        return index;
    }

    /**
     * Finds an existing ClassRefItem with the given name index or adds a new one.
     *
     * @param nameIndex the index of the class name in the constant pool
     * @return the existing or newly added ClassRefItem
     */
    public ClassRefItem findOrAddClassRef(int nameIndex) {
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof ClassRefItem) {
                ClassRefItem classRef = (ClassRefItem) item;
                if (classRef.getNameIndex() == nameIndex) {
                    return classRef;
                }
            }
        }
        ClassRefItem newClassRef = new ClassRefItem();
        newClassRef.setNameIndex(nameIndex);
        addItem(newClassRef);
        return newClassRef;
    }

    /**
     * Finds an existing MethodRefItem with the given class and nameAndType indices or adds a new one.
     *
     * @param classIndex        The index of the class in the constant pool.
     * @param nameAndTypeIndex The index of the NameAndType in the constant pool.
     * @return The existing or newly added MethodRefItem.
     */
    public MethodRefItem findOrAddMethodRef(int classIndex, int nameAndTypeIndex) {
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof MethodRefItem) {
                MethodRefItem methodRef = (MethodRefItem) item;
                if (methodRef.getValue().getClassIndex() == classIndex && methodRef.getValue().getNameAndTypeIndex() == nameAndTypeIndex) {
                    return methodRef;
                }
            }
        }
        MethodRefItem newMethodRef = new MethodRefItem();
        newMethodRef.setValue(new MethodRef(classIndex, nameAndTypeIndex));
        newMethodRef.setClassFile(classFile);
        addItem(newMethodRef);
        return newMethodRef;
    }

    /**
     * Finds or adds a Utf8Item with the specified value.
     *
     * @param value The UTF-8 string to find or add.
     * @return The existing or newly added Utf8Item.
     */
    public Utf8Item findOrAddUtf8(String value) {
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof Utf8Item) {
                Utf8Item utf8 = (Utf8Item) item;
                if (utf8.getValue().equals(value)) {
                    return utf8;
                }
            }
        }
        Utf8Item newUtf8 = new Utf8Item();
        newUtf8.setValue(value);
        addItem(newUtf8);
        return newUtf8;
    }

    /**
     * Always adds a new Utf8Item with the specified value.
     * Unlike findOrAddUtf8, this never reuses existing entries.
     * Use this when the value must not be affected by later constant pool modifications.
     *
     * @param value The UTF-8 string to add.
     * @return The newly added Utf8Item.
     */
    public Utf8Item addUtf8(String value) {
        Utf8Item newUtf8 = new Utf8Item();
        newUtf8.setValue(value);
        addItem(newUtf8);
        return newUtf8;
    }

    /**
     * Finds or adds a NameAndTypeRefItem with the specified name and descriptor indices.
     *
     * @param nameIndex The index of the name Utf8Item.
     * @param descIndex The index of the descriptor Utf8Item.
     * @return The existing or newly added NameAndTypeRefItem.
     */
    public NameAndTypeRefItem findOrAddNameAndType(int nameIndex, int descIndex) {
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof NameAndTypeRefItem) {
                NameAndTypeRefItem nt = (NameAndTypeRefItem) item;
                if (nt.getValue().getNameIndex() == nameIndex && nt.getValue().getDescriptorIndex() == descIndex) {
                    nt.setConstPool(this);
                    return nt;
                }
            }
        }
        NameAndTypeRefItem newNameAndType =  new NameAndTypeRefItem();
        newNameAndType.setConstPool(this);
        newNameAndType.setValue(new NameAndType(nameIndex, descIndex));
        addItem(newNameAndType);
        return newNameAndType;
    }

    public NameAndTypeRefItem findOrAddNameAndType(String name, String descriptor) {
        Utf8Item nameUtf8 = findOrAddUtf8(name);
        Utf8Item descUtf8 = findOrAddUtf8(descriptor);
        return findOrAddNameAndType(getIndexOf(nameUtf8), getIndexOf(descUtf8));
    }

    public int getIndexOf(Item<?> item) {
        for (int i = 1; i < items.size(); i++) {
            if (items.get(i) == item) {
                return i;
            }
        }
        throw new IllegalArgumentException("Item not found in constant pool.");
    }

    /**
     * Finds an existing DoubleItem with the given value or adds a new one to the constant pool.
     *
     * @param value The double value to find or add.
     * @return The existing or newly added DoubleItem.
     */
    public DoubleItem findOrAddDouble(double value) {
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof DoubleItem) {
                DoubleItem doubleItem = (DoubleItem) item;
                if (Double.compare(doubleItem.getValue(), value) == 0) {
                    return doubleItem;
                }
            }
        }
        DoubleItem newDouble = new DoubleItem();
        newDouble.setValue(value);
        addItem(newDouble);
        return newDouble;
    }

    /**
     * Finds an existing FloatItem with the given value or adds a new one to the constant pool.
     *
     * @param value The float value to find or add.
     * @return The existing or newly added FloatItem.
     */
    public FloatItem findOrAddFloat(float value) {
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof FloatItem) {
                FloatItem floatItem = (FloatItem) item;
                if (Float.compare(floatItem.getValue(), value) == 0) {
                    return floatItem;
                }
            }
        }
        FloatItem newFloat = new FloatItem();
        newFloat.setValue(value);
        addItem(newFloat);
        return newFloat;
    }

    /**
     * Finds an existing LongItem with the given value or adds a new one to the constant pool.
     *
     * @param value The long value to find or add.
     * @return The existing or newly added LongItem.
     */
    public LongItem findOrAddLong(long value) {
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof LongItem) {
                LongItem longItem = (LongItem) item;
                if (longItem.getValue() == value) {
                    return longItem;
                }
            }
        }
        LongItem newLong = new LongItem();
        newLong.setValue(value);
        addItem(newLong);
        return newLong;
    }

    /**
     * Finds an existing IntegerItem with the given value or adds a new one to the constant pool.
     *
     * @param value The integer value to find or add.
     * @return The existing or newly added IntegerItem.
     */
    public IntegerItem findOrAddInteger(int value) {
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof IntegerItem) {
                IntegerItem intItem = (IntegerItem) item;
                if (intItem.getValue() == value) {
                    return intItem;
                }
            }
        }
        IntegerItem newInteger = new IntegerItem();
        newInteger.setValue(value);
        addItem(newInteger);
        return newInteger;
    }

    /**
     * Finds an existing FieldRefItem for the specified class, field name, and field type,
     * or adds a new one to the constant pool.
     *
     * @param className The fully qualified name of the class (e.g., "com/tonic/TestCase").
     * @param fieldName The name of the field (e.g., "testIntField").
     * @param fieldType The descriptor of the field (e.g., "I" for int).
     * @return The index of the FieldRefItem in the constant pool.
     */
    public FieldRefItem findOrAddField(String className, String fieldName, String fieldType) {
        Utf8Item classNameUtf8 = findOrAddUtf8(className);
        Utf8Item fieldNameUtf8 = findOrAddUtf8(fieldName);
        Utf8Item fieldTypeUtf8 = findOrAddUtf8(fieldType);

        ClassRefItem classRef = findOrAddClassRef(getIndexOf(classNameUtf8));

        NameAndTypeRefItem nameAndType = findOrAddNameAndType(getIndexOf(fieldNameUtf8), getIndexOf(fieldTypeUtf8));

        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof FieldRefItem) {
                FieldRefItem fieldRef = (FieldRefItem) item;
                if (fieldRef.getValue().getClassIndex() == getIndexOf(classRef) &&
                        fieldRef.getValue().getNameAndTypeIndex() == getIndexOf(nameAndType)) {
                    return fieldRef;
                }
            }
        }

        FieldRefItem newFieldRef = new FieldRefItem();
        newFieldRef.setClassFile(classFile);
        newFieldRef.setValue(new FieldRef(getIndexOf(classRef), getIndexOf(nameAndType)));

        addItem(newFieldRef);
        return newFieldRef;
    }

    public StringRefItem findOrAddString(String value)
    {
        Utf8Item utf8 = findOrAddUtf8(value);
        for (int i = 1; i < items.size(); i++)
        {
            Item<?> item = items.get(i);
            if (item instanceof StringRefItem)
            {
                StringRefItem stringRef = (StringRefItem) item;
                if (stringRef.getValue() == getIndexOf(utf8))
                {
                    return stringRef;
                }
            }
        }
        StringRefItem newStringRef = new StringRefItem();
        newStringRef.setValue(getIndexOf(utf8));
        addItem(newStringRef);
        return newStringRef;
    }

    public ClassRefItem findOrAddClass(String className)
    {
        Utf8Item utf8 = findOrAddUtf8(className);
        for (int i = 1; i < items.size(); i++)
        {
            Item<?> item = items.get(i);
            if (item instanceof ClassRefItem)
            {
                ClassRefItem classRef = (ClassRefItem) item;
                if (classRef.getNameIndex() == getIndexOf(utf8))
                {
                    return classRef;
                }
            }
        }
        ClassRefItem newClassRef = new ClassRefItem();
        newClassRef.setNameIndex(getIndexOf(utf8));
        newClassRef.setClassFile(classFile);
        addItem(newClassRef);
        return newClassRef;
    }

    /**
     * Finds an existing MethodRefItem or creates a new one using string parameters.
     *
     * @param owner The fully qualified class name (e.g., "java/lang/String").
     * @param name The method name.
     * @param descriptor The method descriptor (e.g., "(II)I").
     * @return The existing or newly added MethodRefItem.
     */
    public MethodRefItem findOrAddMethodRef(String owner, String name, String descriptor) {
        Utf8Item ownerUtf8 = findOrAddUtf8(owner);
        Utf8Item nameUtf8 = findOrAddUtf8(name);
        Utf8Item descUtf8 = findOrAddUtf8(descriptor);

        ClassRefItem classRef = findOrAddClassRef(getIndexOf(ownerUtf8));
        NameAndTypeRefItem nameAndType = findOrAddNameAndType(getIndexOf(nameUtf8), getIndexOf(descUtf8));

        return findOrAddMethodRef(getIndexOf(classRef), getIndexOf(nameAndType));
    }

    /**
     * Finds an existing FieldRefItem or creates a new one using string parameters.
     * Alias for findOrAddField for consistency with method naming.
     *
     * @param owner The fully qualified class name (e.g., "java/lang/String").
     * @param name The field name.
     * @param descriptor The field descriptor (e.g., "I", "Ljava/lang/String;").
     * @return The existing or newly added FieldRefItem.
     */
    public FieldRefItem findOrAddFieldRef(String owner, String name, String descriptor) {
        return findOrAddField(owner, name, descriptor);
    }

    /**
     * Finds an existing InterfaceRefItem with the given class and name-and-type indices,
     * or creates a new one if none exists.
     *
     * @param classIndex The constant pool index of the class reference.
     * @param nameAndTypeIndex The constant pool index of the name-and-type reference.
     * @return The existing or newly added InterfaceRefItem.
     */
    public InterfaceRefItem findOrAddInterfaceRef(int classIndex, int nameAndTypeIndex) {
        for (Item<?> item : items) {
            if (item instanceof InterfaceRefItem) {
                InterfaceRefItem iface = (InterfaceRefItem) item;
                if (iface.getValue().getClassIndex() == classIndex &&
                    iface.getValue().getNameAndTypeIndex() == nameAndTypeIndex) {
                    return iface;
                }
            }
        }
        InterfaceRefItem newItem = new InterfaceRefItem();
        newItem.setValue(new com.tonic.parser.constpool.structure.InterfaceRef(classIndex, nameAndTypeIndex));
        newItem.setConstPool(this);
        items.add(newItem);
        return newItem;
    }

    /**
     * Finds an existing InterfaceRefItem or creates a new one using string parameters.
     *
     * @param owner The fully qualified interface name (e.g., "java/util/stream/Stream").
     * @param name The method name.
     * @param descriptor The method descriptor (e.g., "(Ljava/util/function/Consumer;)V").
     * @return The existing or newly added InterfaceRefItem.
     */
    public InterfaceRefItem findOrAddInterfaceRef(String owner, String name, String descriptor) {
        Utf8Item ownerUtf8 = findOrAddUtf8(owner);
        Utf8Item nameUtf8 = findOrAddUtf8(name);
        Utf8Item descUtf8 = findOrAddUtf8(descriptor);

        ClassRefItem classRef = findOrAddClassRef(getIndexOf(ownerUtf8));
        NameAndTypeRefItem nameAndType = findOrAddNameAndType(getIndexOf(nameUtf8), getIndexOf(descUtf8));

        return findOrAddInterfaceRef(getIndexOf(classRef), getIndexOf(nameAndType));
    }

    /**
     * Gets the class name from a ClassRefItem at the given constant pool index.
     *
     * @param classIndex The constant pool index of the ClassRefItem.
     * @return The class name (internal form), or null if not found.
     */
    public String getClassName(int classIndex) {
        if (classIndex == 0) return null;
        Item<?> item = getItem(classIndex);
        if (item instanceof ClassRefItem) {
            ClassRefItem classRef = (ClassRefItem) item;
            Utf8Item utf8 = (Utf8Item) getItem(classRef.getNameIndex());
            return utf8.getValue();
        }
        return null;
    }

    /**
     * Finds or adds a MethodHandle constant pool entry.
     *
     * @param refKind The reference kind (1-9 per JVM spec).
     * @param owner The class containing the referenced member.
     * @param name The name of the referenced member.
     * @param desc The descriptor of the referenced member.
     * @return The MethodHandleItem.
     */
    public MethodHandleItem findOrAddMethodHandle(int refKind, String owner, String name, String desc) {
        int refIndex;
        if (refKind >= 1 && refKind <= 4) {
            FieldRefItem fieldRef = findOrAddFieldRef(owner, name, desc);
            refIndex = getIndexOf(fieldRef);
        } else if (refKind == 9) {
            InterfaceRefItem interfaceRef = findOrAddInterfaceRef(owner, name, desc);
            refIndex = getIndexOf(interfaceRef);
        } else {
            MethodRefItem methodRef = findOrAddMethodRef(owner, name, desc);
            refIndex = getIndexOf(methodRef);
        }

        for (Item<?> item : items) {
            if (item instanceof MethodHandleItem) {
                MethodHandleItem mhItem = (MethodHandleItem) item;
                MethodHandle mh = mhItem.getValue();
                if (mh.getReferenceKind() == refKind && mh.getReferenceIndex() == refIndex) {
                    return mhItem;
                }
            }
        }

        MethodHandle mh = new MethodHandle(refKind, refIndex);
        MethodHandleItem newItem = new MethodHandleItem();
        newItem.setMethodHandle(mh);
        newItem.setClassFile(classFile);
        items.add(newItem);
        return newItem;
    }

    /**
     * Finds or adds a MethodType constant pool entry.
     *
     * @param descriptor The method descriptor (e.g., "(II)V").
     * @return The MethodTypeItem.
     */
    public MethodTypeItem findOrAddMethodType(String descriptor) {
        Utf8Item descUtf8 = findOrAddUtf8(descriptor);
        int descIndex = getIndexOf(descUtf8);

        for (Item<?> item : items) {
            if (item instanceof MethodTypeItem) {
                MethodTypeItem mtItem = (MethodTypeItem) item;
                if (mtItem.getValue() == descIndex) {
                    return mtItem;
                }
            }
        }

        MethodTypeItem newItem = new MethodTypeItem();
        newItem.setDescriptorIndex(descIndex);
        newItem.setClassFile(classFile);
        items.add(newItem);
        return newItem;
    }

    /**
     * Adds a MethodHandle constant pool entry by reference index.
     *
     * @param referenceKind The reference kind (1-9 per JVM spec).
     * @param referenceIndex The constant pool index of the referenced member.
     * @return The index of the newly added MethodHandleItem.
     */
    public int addMethodHandle(int referenceKind, int referenceIndex) {
        MethodHandle mh = new MethodHandle(referenceKind, referenceIndex);
        MethodHandleItem item = new MethodHandleItem();
        item.setMethodHandle(mh);
        return addItem(item);
    }

    /**
     * Adds a MethodType constant pool entry.
     *
     * @param descriptor The method descriptor (e.g., "(II)V").
     * @return The index of the newly added MethodTypeItem.
     */
    public int addMethodType(String descriptor) {
        Utf8Item descUtf8 = findOrAddUtf8(descriptor);
        int descIndex = getIndexOf(descUtf8);

        MethodTypeItem item = new MethodTypeItem();
        item.setDescriptorIndex(descIndex);
        return addItem(item);
    }

    /**
     * Adds an InvokeDynamic constant pool entry.
     *
     * @param bootstrapMethodAttrIndex The index into the bootstrap methods table.
     * @param nameAndTypeIndex The constant pool index of the name and type.
     * @return The index of the newly added InvokeDynamicItem.
     */
    public int addInvokeDynamic(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
        InvokeDynamicItem item = new InvokeDynamicItem(this, bootstrapMethodAttrIndex, nameAndTypeIndex);
        item.setClassFile(classFile);
        return addItem(item);
    }

    /**
     * Adds a MethodRef constant pool entry.
     *
     * @param owner The fully qualified class name (e.g., "java/lang/String").
     * @param name The method name.
     * @param descriptor The method descriptor (e.g., "(II)I").
     * @return The index of the newly added MethodRefItem.
     */
    public int addMethodRef(String owner, String name, String descriptor) {
        MethodRefItem item = findOrAddMethodRef(owner, name, descriptor);
        return getIndexOf(item);
    }

    /**
     * Adds a NameAndType constant pool entry.
     *
     * @param name The name string.
     * @param descriptor The descriptor string.
     * @return The index of the newly added NameAndTypeRefItem.
     */
    public int addNameAndType(String name, String descriptor) {
        NameAndTypeRefItem item = findOrAddNameAndType(name, descriptor);
        return getIndexOf(item);
    }
}
