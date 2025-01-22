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
        final int constantPoolCount = classFile.readUnsignedShort();
        this.items = new ArrayList<>(constantPoolCount);

        // Index 0 is unused; add a null to align indices.
        items.add(null);

        // Iterate through constant pool entries.
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
                case Item.ITEM_INVOKEDYNAMIC:
                    item = new InvokeDynamicItem();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown constant pool tag: " + tag + " at index " + i);
            }

            // Read the item's data from the class file.
            item.read(classFile);
            items.add(item);

            // Log the parsed item
            Logger.info("Parsed constant pool entry " + i + ": " + item);

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
                    return nt;
                }
            }
        }
        NameAndTypeRefItem newNameAndType = new NameAndTypeRefItem();
        newNameAndType.setConstPool(this);
        newNameAndType.setValue(new NameAndType(nameIndex, descIndex));
        addItem(newNameAndType);
        return newNameAndType;
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
    public int findOrAddField(String className, String fieldName, String fieldType) {
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
            if (item instanceof FieldRefItem) {
                FieldRefItem fieldRef = (FieldRefItem) item;
                if (fieldRef.getValue().getClassIndex() == getIndexOf(classRef) &&
                        fieldRef.getValue().getNameAndTypeIndex() == getIndexOf(nameAndType)) {
                    return i;
                }
            }
        }

        // If not found, create and add a new FieldRefItem
        FieldRefItem newFieldRef = new FieldRefItem();
        newFieldRef.setClassFile(classFile);
        newFieldRef.setValue(new FieldRef(getIndexOf(classRef), getIndexOf(nameAndType)));

        return addItem(newFieldRef);
    }

}
