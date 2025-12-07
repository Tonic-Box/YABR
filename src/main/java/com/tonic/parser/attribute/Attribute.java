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
    protected ClassFile hostClass;
    protected int nameIndex, length;

    public Attribute(String name, MemberEntry parent, int nameIndex, int length) {
        this.name = name;
        this.parent = parent;
        this.nameIndex = nameIndex;
        this.length = length;
    }

    public Attribute(String name, ClassFile hostClass, int nameIndex, int length) {
        this.name = name;
        this.hostClass = hostClass;
        this.nameIndex = nameIndex;
        this.length = length;
    }

    protected ClassFile getClassFile() {
        return parent != null ? parent.getClassFile() : hostClass;
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
        int preReadIndex = classFile.getIndex();
        Logger.info("Reading attribute at byte index: " + preReadIndex);

        int nameIndex = classFile.readUnsignedShort();
        Item<?> nameItem = constPool.getItem(nameIndex);

        if (!(nameItem instanceof Utf8Item)) {
            String errorMsg = "Attribute name at index " + nameIndex + " is not a Utf8Item.";
            Logger.error("ERROR: " + errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        String name = ((Utf8Item) nameItem).getValue();
        Logger.info("Attribute Name: " + name);

        long lengthLong = classFile.readUnsignedInt();
        if (lengthLong > Integer.MAX_VALUE) {
            String errorMsg = "Attribute length too large: " + lengthLong;
            Logger.error("ERROR: " + errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        int length = (int) lengthLong;
        Logger.info("Attribute Length: " + length);

        Attribute attribute;
        if(parent == null)
        {
            attribute = getClassAttribute(name, nameIndex, length, classFile);
        }
        else
        {
            attribute = getMethodAttribute(name, nameIndex, length, classFile, parent);
        }

        try {
            Logger.info("Starting to read attribute data for: " + name);
            attribute.read(classFile, length);
            Logger.info("Completed reading attribute: " + name);
        } catch (Exception e) {
            Logger.error("ERROR: Failed to read attribute '" + name + "'. Exception: " + e.getMessage());
            throw e;
        }

        int postReadIndex = classFile.getIndex();
        Logger.info("Finished attribute '" + name + "'. Byte index moved from " + preReadIndex + " to " + postReadIndex);
        Logger.info("---------------------------------------------------");

        return attribute;
    }

    private static Attribute getClassAttribute(String name, int nameIndex, int length, ClassFile classFile)
    {
        return switch (name) {
            case "ConstantValue" -> new ConstantValueAttribute(name, classFile, nameIndex, length);
            case "StackMapTable" -> new StackMapTableAttribute(name, classFile, nameIndex, length);
            case "Code" -> new CodeAttribute(name, classFile, nameIndex, length);
            case "Exceptions" -> new ExceptionsAttribute(name, classFile, nameIndex, length);
            case "InnerClasses" -> new InnerClassesAttribute(name, classFile, nameIndex, length);
            case "EnclosingMethod" -> new EnclosingMethodAttribute(name, classFile, nameIndex, length);
            case "Synthetic" -> new SyntheticAttribute(name, classFile, nameIndex, length);
            case "Signature" -> new SignatureAttribute(name, classFile, nameIndex, length);
            case "SourceFile" -> new SourceFileAttribute(classFile, name, null, nameIndex, length);
            case "SourceDebugExtension" -> new SourceDebugExtensionAttribute(name, classFile, nameIndex, length);
            case "LineNumberTable" -> new LineNumberTableAttribute(name, classFile, nameIndex, length);
            case "LocalVariableTable" -> new LocalVariableTableAttribute(name, classFile, nameIndex, length);
            case "LocalVariableTypeTable" -> new LocalVariableTypeTableAttribute(name, classFile, nameIndex, length);
            case "Deprecated" -> new DeprecatedAttribute(name, classFile, nameIndex, length);
            case "RuntimeVisibleAnnotations" ->
                    new RuntimeVisibleAnnotationsAttribute(name, classFile, true, nameIndex, length);
            case "RuntimeInvisibleAnnotations" ->
                    new RuntimeVisibleAnnotationsAttribute(name, classFile, false, nameIndex, length);
            case "RuntimeVisibleParameterAnnotations" ->
                    new RuntimeVisibleParameterAnnotationsAttribute(name, classFile, true, nameIndex, length);
            case "RuntimeInvisibleParameterAnnotations" ->
                    new RuntimeVisibleParameterAnnotationsAttribute(name, classFile, false, nameIndex, length);
            case "AnnotationDefault" -> new AnnotationDefaultAttribute(name, classFile, nameIndex, length);
            case "MethodParameters" -> new MethodParametersAttribute(name, classFile, nameIndex, length);
            case "BootstrapMethods" -> new BootstrapMethodsAttribute(name, classFile, nameIndex, length);
            case "Module" -> new ModuleAttribute(name, classFile, nameIndex, length);
            case "NestHost" -> new NestHostAttribute(name, classFile, nameIndex, length);
            case "NestMembers" -> new NestMembersAttribute(name, classFile, nameIndex, length);
            default -> {
                Logger.error("Warning: Unknown attribute '" + name + "'. Using GenericAttribute.");
                yield new GenericAttribute(name, classFile, nameIndex, length);
            }
        };
    }

    private static Attribute getMethodAttribute(String name, int nameIndex, int length,ClassFile classFile, MemberEntry parent)
    {
        return switch (name) {
            case "ConstantValue" -> new ConstantValueAttribute(name, parent, nameIndex, length);
            case "StackMapTable" -> new StackMapTableAttribute(name, parent, nameIndex, length);
            case "Code" -> new CodeAttribute(name, parent, nameIndex, length);
            case "Exceptions" -> new ExceptionsAttribute(name, parent, nameIndex, length);
            case "InnerClasses" -> new InnerClassesAttribute(name, parent, nameIndex, length);
            case "EnclosingMethod" -> new EnclosingMethodAttribute(name, parent, nameIndex, length);
            case "Synthetic" -> new SyntheticAttribute(name, parent, nameIndex, length);
            case "Signature" -> new SignatureAttribute(name, parent, nameIndex, length);
            case "SourceFile" -> new SourceFileAttribute(classFile, name, parent, nameIndex, length);
            case "SourceDebugExtension" -> new SourceDebugExtensionAttribute(name, parent, nameIndex, length);
            case "LineNumberTable" -> new LineNumberTableAttribute(name, parent, nameIndex, length);
            case "LocalVariableTable" -> new LocalVariableTableAttribute(name, parent, nameIndex, length);
            case "LocalVariableTypeTable" -> new LocalVariableTypeTableAttribute(name, parent, nameIndex, length);
            case "Deprecated" -> new DeprecatedAttribute(name, parent, nameIndex, length);
            case "RuntimeVisibleAnnotations" ->
                    new RuntimeVisibleAnnotationsAttribute(name, parent, true, nameIndex, length);
            case "RuntimeInvisibleAnnotations" ->
                    new RuntimeVisibleAnnotationsAttribute(name, parent, false, nameIndex, length);
            case "RuntimeVisibleParameterAnnotations" ->
                    new RuntimeVisibleParameterAnnotationsAttribute(name, parent, true, nameIndex, length);
            case "RuntimeInvisibleParameterAnnotations" ->
                    new RuntimeVisibleParameterAnnotationsAttribute(name, parent, false, nameIndex, length);
            case "AnnotationDefault" -> new AnnotationDefaultAttribute(name, parent, nameIndex, length);
            case "MethodParameters" -> new MethodParametersAttribute(name, parent, nameIndex, length);
            case "BootstrapMethods" -> new BootstrapMethodsAttribute(name, parent, nameIndex, length);
            case "Module" -> new ModuleAttribute(name, parent, nameIndex, length);
            case "NestHost" -> new NestHostAttribute(name, parent, nameIndex, length);
            case "NestMembers" -> new NestMembersAttribute(name, parent, nameIndex, length);
            default -> {
                Logger.error("Warning: Unknown attribute '" + name + "'. Using GenericAttribute.");
                yield new GenericAttribute(name, parent, nameIndex, length);
            }
        };
    }

    /**
     * Writes the attribute to the output stream.
     *
     * @param dos The output stream to write to
     * @throws IOException If an I/O error occurs
     */
    public void write(DataOutputStream dos) throws IOException {
        updateLength();

        dos.writeShort(nameIndex);
        dos.writeInt(length);

        writeInfo(dos);
    }

    /**
     * Writes the attribute-specific data (info bytes).
     *
     * @param dos The output stream to write to
     * @throws IOException If an I/O error occurs
     */
    protected abstract void writeInfo(DataOutputStream dos) throws IOException;

    public abstract void updateLength();

    @Override
    public String toString() {
        return "Attribute{name='" + name + "'}";
    }
}
