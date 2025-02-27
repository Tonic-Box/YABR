package com.tonic.parser;

import com.tonic.parser.constpool.*;
import com.tonic.parser.constpool.structure.FieldRef;
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
     * Constructs a ConstPool by parsing the constant pool entries from the given ClassFile.
     *
     * @param classFile The ClassFile utility to read data from.
     */
    public ConstPool(final ClassFile classFile) {
        this.classFile = classFile;
        this.classFile.setConstPool(this);
        final int constantPoolCount = classFile.readUnsignedShort();
        this.items = new ArrayList<>();

        // Index 0 is unused; add a null to align indices.
        items.add(null);

        // Iterate through constant pool entries.
        for (int i = 1; i < constantPoolCount; i++) {
            byte tag = (byte) classFile.readUnsignedByte();
            Item<?> item = switch (tag) {
                case Item.ITEM_UTF_8 -> new Utf8Item();
                case Item.ITEM_INTEGER -> new IntegerItem();
                case Item.ITEM_FLOAT -> new FloatItem();
                case Item.ITEM_LONG -> new LongItem();
                case Item.ITEM_DOUBLE -> new DoubleItem();
                case Item.ITEM_CLASS_REF -> new ClassRefItem();
                case Item.ITEM_STRING_REF -> new StringRefItem();
                case Item.ITEM_FIELD_REF -> new FieldRefItem();
                case Item.ITEM_METHOD_REF -> new MethodRefItem();
                case Item.ITEM_INTERFACE_REF -> new InterfaceRefItem();
                case Item.ITEM_NAME_TYPE_REF -> new NameAndTypeRefItem();
                case Item.ITEM_METHOD_HANDLE -> new MethodHandleItem();
                case Item.ITEM_METHOD_TYPE -> new MethodTypeItem();
                case Item.ITEM_INVOKEDYNAMIC -> new InvokeDynamicItem();
                case Item.ITEM_PACKAGE -> // CONSTANT_Package
                        new PackageItem();
                case Item.ITEM_MODULE -> // CONSTANT_Module
                        new ModuleItem();
                default -> throw new IllegalArgumentException("Unknown constant pool tag: " + tag + " at index " + i);
            };

            // Read the item's data from the class file.
            item.read(classFile);
            item.setClassFile(classFile);
            items.add(item);

            // Log the parsed item
            Logger.info("Parsed constant pool entry " + i + ": " + item.getClass().getName());

            // Handle Long and Double which occupy two entries.
            if (tag == Item.ITEM_LONG || tag == Item.ITEM_DOUBLE) {
                // According to JVM Spec, the next entry is invalid and should be skipped.
                items.add(null);
                Logger.info("Long/Double entry at " + i + ", skipping next index");
                i++; // Increment index to skip the next entry.
            }
        }
    }

    public ConstPool() {
        this.classFile = null;
        this.items = new ArrayList<>();
        this.items.add(null); // Index 0 is unused.
    }

    public void setClassFile(ClassFile classFile) {
        this.classFile = classFile;
        // Assign classFile to existing items that require it
        for (Item<?> item : items) {
            if (item instanceof MethodRefItem) {
                ((MethodRefItem) item).setClassFile(classFile);
            }
            // Similarly, set classFile for other item types if needed
        }
    }


    /**
     * Retrieves the constant pool item at the specified index.
     *
     * @param index The 1-based index of the constant pool item.
     * @return The constant pool item, or null if the index is invalid or points to an unused slot.
     * @throws IllegalArgumentException If the index is out of bounds.
     */
    public Item<?> getItem(final int index) {
        if (index <= 0 || index >= items.size()) {
            throw new IllegalArgumentException("Constant pool index out of bounds: " + index);
        }
        return items.get(index);
    }

    /**
     * Returns a string representation of the constant pool.
     *
     * @return A formatted string listing all constant pool entries.
     */
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

    public int addItem(Item<?> newItem) {
        if(classFile != null)
        {
            newItem.setClassFile(classFile);
        }
        else
        {
            throw new IllegalStateException("Cannot add item to constant pool without a ClassFile reference.");
        }

        // The next available index is just the current size of 'items'.
        int index = items.size();
        items.add(newItem);

        // If this is a Long or Double, per the JVM spec we must add a null placeholder entry next.
        if (newItem.getType() == Item.ITEM_LONG || newItem.getType() == Item.ITEM_DOUBLE) {
            items.add(null);
        }

        return index;
    }

    /**
     * Finds an existing ClassRefItem with the given name index or adds a new one.
     *
     * @param nameIndex The index of the class name in the constant pool.
     * @return The existing or newly added ClassRefItem.
     */
    public ClassRefItem findOrAddClassRef(int nameIndex) {
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof ClassRefItem classRef) {
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
            if (item instanceof MethodRefItem methodRef) {
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
            if (item instanceof Utf8Item utf8) {
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
     * Finds or adds a NameAndTypeRefItem with the specified name and descriptor indices.
     *
     * @param nameIndex The index of the name Utf8Item.
     * @param descIndex The index of the descriptor Utf8Item.
     * @return The existing or newly added NameAndTypeRefItem.
     */
    public NameAndTypeRefItem findOrAddNameAndType(int nameIndex, int descIndex) {
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof NameAndTypeRefItem nt) {
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
            if (item instanceof DoubleItem doubleItem) {
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
            if (item instanceof FloatItem floatItem) {
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
            if (item instanceof LongItem longItem) {
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
            if (item instanceof IntegerItem intItem) {
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
        // Find or add Utf8 entries for className, fieldName, and fieldType
        Utf8Item classNameUtf8 = findOrAddUtf8(className);
        Utf8Item fieldNameUtf8 = findOrAddUtf8(fieldName);
        Utf8Item fieldTypeUtf8 = findOrAddUtf8(fieldType);

        // Find or add ClassRefItem for className
        ClassRefItem classRef = findOrAddClassRef(getIndexOf(classNameUtf8));

        // Find or add NameAndTypeRefItem for fieldName and fieldType
        NameAndTypeRefItem nameAndType = findOrAddNameAndType(getIndexOf(fieldNameUtf8), getIndexOf(fieldTypeUtf8));

        // Search for an existing FieldRefItem with the same classRef and nameAndType
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item instanceof FieldRefItem fieldRef) {
                if (fieldRef.getValue().getClassIndex() == getIndexOf(classRef) &&
                        fieldRef.getValue().getNameAndTypeIndex() == getIndexOf(nameAndType)) {
                    return fieldRef;
                }
            }
        }

        // If not found, create and add a new FieldRefItem
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
            if (item instanceof StringRefItem stringRef)
            {
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
            if (item instanceof ClassRefItem classRef)
            {
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
}
