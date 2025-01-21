package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.utill.Logger;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Abstract representation of an attribute in the class file.
 */
public abstract class Attribute {
    protected String name;
    protected MemberEntry parent;
    protected int nameIndex, length;

    public Attribute(String name, MemberEntry parent, int nameIndex, int length) {
        this.name = name;
        this.parent = parent;
        this.nameIndex = nameIndex;
        this.length = length;
    }

    /**
     * Reads the attribute data from the class file.
     *
     * @param classFile The ClassFile utility to read data.
     * @param length    The length of the attribute.
     */
    public abstract void read(ClassFile classFile, int length);

    /**
     * Factory method to instantiate the appropriate Attribute subclass based on the attribute name.
     *
     * @param classFile The ClassFile utility to read data.
     * @param constPool The constant pool for resolving attribute names.
     * @param parent    The parent MemberEntry (e.g., FieldEntry, MethodEntry).
     * @return An instance of the appropriate Attribute subclass.
     */
    public static Attribute get(ClassFile classFile, ConstPool constPool, MemberEntry parent) {
        // Record the byte index before reading the attribute
        int preReadIndex = classFile.getIndex();
        Logger.info("Reading attribute at byte index: " + preReadIndex);

        // Read the attribute name index
        int nameIndex = classFile.readUnsignedShort();
        Item<?> nameItem = constPool.getItem(nameIndex);

        if (!(nameItem instanceof Utf8Item)) {
            String errorMsg = "Attribute name at index " + nameIndex + " is not a Utf8Item.";
            Logger.error("ERROR: " + errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        String name = ((Utf8Item) nameItem).getValue();
        Logger.info("Attribute Name: " + name);

        // Read the attribute length
        long lengthLong = classFile.readUnsignedInt();
        if (lengthLong > Integer.MAX_VALUE) {
            String errorMsg = "Attribute length too large: " + lengthLong;
            Logger.error("ERROR: " + errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        int length = (int) lengthLong;
        Logger.info("Attribute Length: " + length);

        // Instantiate the appropriate Attribute subclass
        Attribute attribute;
        switch (name) {
            case "ConstantValue":
                attribute = new ConstantValueAttribute(name, parent, nameIndex, length);
                break;case "StackMapTable":
                attribute = new StackMapTableAttribute(name, parent, nameIndex, length);
                break;
            case "Code":
                attribute = new CodeAttribute(name, parent, nameIndex, length);
                break;

            case "Exceptions":
                attribute = new ExceptionsAttribute(name, parent, nameIndex, length);
                break;
            case "InnerClasses":
                attribute = new InnerClassesAttribute(name, parent, nameIndex, length);
                break;
            case "EnclosingMethod":
                attribute = new EnclosingMethodAttribute(name, parent, nameIndex, length);
                break;
            case "Synthetic":
                attribute = new SyntheticAttribute(name, parent, nameIndex, length);
                break;
            case "Signature":
                attribute = new SignatureAttribute(name, parent, nameIndex, length);
                break;
            case "SourceFile":
                attribute = new SourceFileAttribute(classFile, name, parent, nameIndex, length);
                break;
            case "SourceDebugExtension":
                attribute = new SourceDebugExtensionAttribute(name, parent, nameIndex, length);
                break;
            case "LineNumberTable":
                attribute = new LineNumberTableAttribute(name, parent, nameIndex, length);
                break;
            case "LocalVariableTable":
                attribute = new LocalVariableTableAttribute(name, parent, nameIndex, length);
                break;
            case "LocalVariableTypeTable":
                attribute = new LocalVariableTypeTableAttribute(name, parent, nameIndex, length);
                break;
            case "Deprecated":
                attribute = new DeprecatedAttribute(name, parent, nameIndex, length);
                break;
            case "RuntimeVisibleAnnotations":
                attribute = new RuntimeVisibleAnnotationsAttribute(name, parent, true, nameIndex, length);
                break;
            case "RuntimeInvisibleAnnotations":
                attribute = new RuntimeVisibleAnnotationsAttribute(name, parent, false, nameIndex, length);
                break;
            case "RuntimeVisibleParameterAnnotations":
                attribute = new RuntimeVisibleParameterAnnotationsAttribute(name, parent, true, nameIndex, length);
                break;
            case "RuntimeInvisibleParameterAnnotations":
                attribute = new RuntimeVisibleParameterAnnotationsAttribute(name, parent, false, nameIndex, length);
                break;
            case "AnnotationDefault":
                attribute = new AnnotationDefaultAttribute(name, parent, nameIndex, length);
                break;
            case "MethodParameters":
                attribute = new MethodParametersAttribute(name, parent, nameIndex, length);
                break;
            case "BootstrapMethods":
                attribute = new BootstrapMethodsAttribute(name, parent, nameIndex, length);
                break;
            case "Module":
                attribute = new ModuleAttribute(name, parent, nameIndex, length);
                break;
            case "NestHost":
                attribute = new NestHostAttribute(name, parent, nameIndex, length);
                break;
            case "NestMembers":
                attribute = new NestMembersAttribute(name, parent, nameIndex, length);
                break;
            // Add more cases for different attribute types as needed
            default:
                Logger.error("Warning: Unknown attribute '" + name + "'. Using GenericAttribute.");
                attribute = new GenericAttribute(name, parent, nameIndex, length);
                break;
        }

        // Read the attribute-specific data
        try {
            Logger.info("Starting to read attribute data for: " + name);
            attribute.read(classFile, length);
            Logger.info("Completed reading attribute: " + name);
        } catch (Exception e) {
            Logger.error("ERROR: Failed to read attribute '" + name + "'. Exception: " + e.getMessage());
            throw e; // Re-throw the exception after logging
        }

        // Calculate the byte index after reading the attribute
        int postReadIndex = classFile.getIndex();
        Logger.info("Finished attribute '" + name + "'. Byte index moved from " + preReadIndex + " to " + postReadIndex);
        Logger.info("---------------------------------------------------");

        return attribute;
    }

    /**
     * Writes the base attribute fields:
     *   - attribute_name_index (u2)
     *   - attribute_length (u4)
     * then the rest (info) must be written by subclasses.
     */
    public void write(DataOutputStream dos) throws IOException {
        // Make sure the length is correct (if you've changed anything)
        updateLength();

        dos.writeShort(nameIndex);
        dos.writeInt(length);

        // Then sub-class specific data (info)
        writeInfo(dos);
    }

    /**
     * Let each attribute type write its own body (the 'info' bytes).
     */
    protected abstract void writeInfo(DataOutputStream dos) throws IOException;

    public abstract void updateLength();

    @Override
    public String toString() {
        return "Attribute{name='" + name + "'}";
    }
}
