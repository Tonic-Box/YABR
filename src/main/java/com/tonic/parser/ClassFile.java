package com.tonic.parser;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.constpool.*;
import com.tonic.utill.Logger;
import com.tonic.utill.Modifiers;
import com.tonic.utill.ReturnType;
import lombok.Getter;
import lombok.Setter;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
public class ClassFile extends AbstractParser {
    /**
     * -- SETTER --
     *  Sets a new minor version and overwrites it in the underlying byte array.
     *
     * @param newMinor The new minor version.
     */
    @Setter
    private int minorVersion;
    /**
     * -- SETTER --
     *  Sets a new major version and overwrites it in the underlying byte array.
     *
     * @param newMajor The new major version.
     */
    @Setter
    private int majorVersion;

    /**
     * Sets the access flags (e.g. public, final, etc.) and updates the underlying bytes.
     *
     * @param newAccess The new access flags.
     */
    private int access;
    private int thisClass;
    private int superClass;
    private ConstPool constPool;
    private List<Attribute> classAttributes;
    private List<FieldEntry> fields;
    private List<MethodEntry> methods;
    private List<Integer> interfaces; // To store interface indices

    // --- Offsets in the raw .class file for the version bytes ---
    //  0..3  : magic = 0xCAFEBABE
    //  4..5  : minor_version (u2)
    //  6..7  : major_version (u2)
    private static final int MINOR_VERSION_OFFSET = 4;
    private static final int MAJOR_VERSION_OFFSET = 6;

    protected ClassFile(final byte[] classBytes) {
        super(classBytes);
    }

    public ClassFile(final InputStream inputStream) throws IOException {
        super(inputStream.readAllBytes());
    }

    public ClassFile(String className, int accessFlags) {
        super(new byte[0], false); // Initialize with an empty byte array; actual data will be constructed

        // Initialize constant pool
        this.constPool = new ConstPool();
        this.constPool.setClassFile(this);

        // Add necessary constant pool entries
        Utf8Item thisClassNameUtf8 = constPool.findOrAddUtf8(className);
        Utf8Item superClassNameUtf8 = constPool.findOrAddUtf8("java/lang/Object");

        ClassRefItem thisClassRef = constPool.findOrAddClassRef(constPool.getIndexOf(thisClassNameUtf8));
        ClassRefItem superClassRef = constPool.findOrAddClassRef(constPool.getIndexOf(superClassNameUtf8));

        // Set access flags
        this.access = accessFlags;

        // Set this_class and super_class indices
        thisClass = constPool.getIndexOf(thisClassRef);
        superClass = constPool.getIndexOf(superClassRef);

        // Initialize interfaces, fields, methods, and attributes
        this.interfaces = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.methods = new ArrayList<>();
        this.classAttributes = new ArrayList<>();

        // Set minor and major versions for Java 11
        this.minorVersion = 0;
        this.majorVersion = 55;

        // Add a default constructor
        createDefaultConstructor();
    }

    /**
     * Creates and adds a default no-argument constructor to the class.
     */
    private void createDefaultConstructor() {
        // Define access flags for the constructor (public)
        int constructorAccessFlags = Modifiers.PUBLIC;

        // Constructor name is "<init>"
        String constructorName = "<init>";

        // Constructor descriptor is "()V" (no arguments, void return)
        String constructorDescriptor = "()V";

        // Add or find Utf8 entries
        Utf8Item nameUtf8 = constPool.findOrAddUtf8(constructorName);
        int nameIndex = constPool.getIndexOf(nameUtf8);

        Utf8Item descUtf8 = constPool.findOrAddUtf8(constructorDescriptor);
        int descIndex = constPool.getIndexOf(descUtf8);

        // Add or find NameAndTypeRefItem
        NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(nameIndex, descIndex);
        int nameAndTypeIndex = constPool.getIndexOf(nameAndType);

        // Add or find MethodRefItem for superclass constructor
        MethodRefItem superConstructorRef = constPool.findOrAddMethodRef(superClass, nameAndTypeIndex);

        // Create a new MethodEntry for the constructor
        MethodEntry constructor = new MethodEntry(this, constructorAccessFlags, nameIndex, descIndex, new ArrayList<>());
        constructor.setName(constructorName);
        constructor.setDesc(constructorDescriptor);
        constructor.setOwnerName(getClassName());
        constructor.setKey(constructorName + constructorDescriptor);

        // Create CodeAttribute for the constructor
        CodeAttribute codeAttr = new CodeAttribute("Code", constructor, constPool.getIndexOf(constPool.findOrAddUtf8("Code")), 0);
        codeAttr.setMaxStack(10); // Set an appropriate max stack size
        codeAttr.setMaxLocals(1); // +1 for 'this' if not static
        constructor.getAttributes().add(codeAttr);

        // **Initialize the code to an empty byte array to prevent NullPointerException**
        codeAttr.setCode(new byte[0]);

        // Initialize Bytecode for the constructor
        Bytecode bytecode = new Bytecode(constructor);

        // Bytecode instructions for:
        // aload_0
        // invokespecial <init> of superclass
        // return
        bytecode.addALoad(0); // Load 'this'
        bytecode.addInvokeSpecial(constPool.getIndexOf(superConstructorRef)); // Call super.<init>()
        bytecode.addReturn(ReturnType.RETURN_); // RETURN

        // Finalize bytecode (which sets the code in codeAttr)
        try {
            bytecode.finalizeBytecode();
        } catch (IOException e) {
            Logger.error("Failed to finalize bytecode for default constructor: " + e.getMessage());
        }

        // Add the constructor to the methods list
        methods.add(constructor);
    }


    @Override
    protected void process() {
        // Read minor and major versions
        minorVersion = readUnsignedShort();  // read from offsets 4..5
        majorVersion = readUnsignedShort();  // read from offsets 6..7
        Logger.info("Version: " + majorVersion + "." + minorVersion);

        // Parse constant pool
        constPool = new ConstPool(this);
        Logger.info(constPool.toString());

        // Read access flags
        access = readUnsignedShort();
        Logger.info("Access Flags: 0x" + Integer.toHexString(access));

        // Read this_class and super_class
        thisClass = readUnsignedShort();
        superClass = readUnsignedShort();
        Logger.info("This Class Index: " + thisClass);
        Logger.info("Super Class Index: " + superClass);

        // Read interfaces
        final int interfaceCount = readUnsignedShort();
        Logger.info("Interfaces Count: " + interfaceCount);
        interfaces = new ArrayList<>(interfaceCount);
        for (int i = 0; i < interfaceCount; i++) {
            int ifaceIndex = readUnsignedShort();
            interfaces.add(ifaceIndex);
            Logger.info("  Interface " + (i + 1) + " Index: " + ifaceIndex);
        }

        // Read fields
        final int fieldCount = readUnsignedShort();
        Logger.info("Fields Count: " + fieldCount);
        fields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            FieldEntry field = new FieldEntry(this);
            fields.add(field);
            Logger.info("  Field " + (i + 1) + ": " + field);
        }

        // Read methods
        final int methodCount = readUnsignedShort();
        Logger.info("Methods Count: " + methodCount);
        methods = new ArrayList<>(methodCount);
        for (int i = 0; i < methodCount; i++) {
            MethodEntry method = new MethodEntry(this);
            methods.add(method);
            Logger.info("  Method " + (i + 1) + ": " + method);

            CodeAttribute codeAttr = method.getCodeAttribute();
            if (codeAttr != null) {
                Logger.info("    Code Attribute: " + codeAttr);
            }
        }

        // Read class attributes
        final int attributesCount = readUnsignedShort();
        classAttributes = new ArrayList<>(attributesCount);
        Logger.info("Class Attributes Count: " + attributesCount);
        for (int i = 0; i < attributesCount; i++) {
            Attribute attribute =
                    Attribute.get(this, constPool, null); // class attrs have no "parent" member
            Logger.info("  Class Attribute " + (i + 1) + ": " + attribute);
            classAttributes.add(attribute);
        }
    }

    @Override
    protected boolean verify() {
        // The first 4 bytes must be 0xCAFEBABE
        return readInt() == 0xCAFEBABE;
    }

    /**
     * Retrieves the class name from the constant pool.
     * @return The class name as a String.
     */
    public String getClassName() {
        return resolveClassName(thisClass);
    }

    /**
     * Retrieves the superclass name from the constant pool.
     * @return The superclass name as a String.
     */
    public String getSuperClassName() {
        return resolveClassName(superClass);
    }

    // -------------------------------------------------------------------------
    //  MUTATOR METHODS
    // -------------------------------------------------------------------------

    /**
     * Sets the class name (internal name like "com/tonic/TestClass").
     * <p>
     * <strong>Warning:</strong> If the new name is longer than the old one,
     * this may corrupt the class unless you rebuild the constant pool carefully.
     *
     * @param newName The new internal class name, e.g. "com/tonic/NewName"
     */
    public void setClassName(String newName) {
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(thisClass);
        Utf8Item utf8 = (Utf8Item) constPool.getItem(classRef.getValue());
        utf8.setValue(newName);
    }

    /**
     * Sets the superclass name (internal name like "java/lang/Object").
     * <p>
     * <strong>Warning:</strong> Same caution about string length as setClassName().
     *
     * @param newSuperName The new internal name for the superclass, e.g. "java/lang/String"
     */
    public void setSuperClassName(String newSuperName) {
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(superClass);
        Utf8Item utf8 = (Utf8Item) constPool.getItem(classRef.getValue());
        utf8.setValue(newSuperName);
    }

    /****
     * Adds a new interface to the interfaces list. If the given interface name
     * does not exist in the constant pool, it creates a new Utf8Item and a new
     * ClassRefItem for it. Then it adds the resulting index to the interfaces list
     * (unless it’s already present).
     *
     * @param interfaceName The internal name of the interface, e.g. "java/util/List".
     */
    public void addInterface(String interfaceName) {
        // Ensure the interface name uses '/' instead of '.' (if needed):
        String internalName = interfaceName.replace('.', '/');

        // 1. Check if the constant pool already contains a ClassRefItem for this name.
        int existingIndex = -1;
        List<Item<?>> cpItems = constPool.getItems();
        for (int i = 1; i < cpItems.size(); i++) {
            Item<?> item = cpItems.get(i);
            if (item instanceof ClassRefItem) {
                ClassRefItem classRef = (ClassRefItem) item;
                // getClassName() returns e.g. "java.util.List" with '.' or null if not set up,
                // so compare with the underlying Utf8Item or internalName logic:
                Utf8Item nameUtf8 = (Utf8Item) constPool.getItem(classRef.getValue());
                if (nameUtf8 != null && nameUtf8.getValue().equals(internalName)) {
                    existingIndex = i;
                    break;
                }
            }
        }

        // 2. If not found, create a new Utf8Item and a new ClassRefItem
        if (existingIndex == -1) {
            // Create a new Utf8Item for the interface name
            Utf8Item newUtf8 = new Utf8Item();
            newUtf8.setValue(internalName);
            int utf8Index = constPool.addItem(newUtf8); // adds to constant pool, returns new index

            // Create a new ClassRefItem that references this UTF-8
            ClassRefItem newClassRef = new ClassRefItem();
            // We'll set the 'value' to the index of the new Utf8Item
            newClassRef.setValue(utf8Index);
            // ^ You may need a small helper or setter. If you do not have one, do:
            // newClassRef.setValue(utf8Index);

            int classRefIndex = constPool.addItem(newClassRef);

            existingIndex = classRefIndex;
        }

        // 3. Finally, add it to the interfaces list if not already present
        if (!interfaces.contains(existingIndex)) {
            interfaces.add(existingIndex);
        }
    }

    /**
     * Adds a new field to the class. If the field name or descriptor does not exist in the constant pool,
     * it adds the necessary Utf8Item and NameAndTypeRefItem entries. Then, it creates a new FieldEntry
     * and adds it to the fields list.
     *
     * @param accessFlags    The access flags for the field (e.g., 0x0001 for public).
     * @param fieldName      The name of the field, e.g., "myField".
     * @param fieldDescriptor The descriptor of the field, e.g., "Ljava/lang/String;".
     * @param attributes      A list of attributes for the field. Can be empty or null.
     */
    public FieldEntry createNewField(int accessFlags, String fieldName, String fieldDescriptor, List<Attribute> attributes) {
        if (attributes == null) {
            attributes = new ArrayList<>();
        }

        // 1. Add or find Utf8Item for field name
        Utf8Item nameUtf8 = constPool.findOrAddUtf8(fieldName);
        int nameIndex = constPool.getIndexOf(nameUtf8);

        // 2. Add or find Utf8Item for field descriptor
        Utf8Item descUtf8 = constPool.findOrAddUtf8(fieldDescriptor);
        int descIndex = constPool.getIndexOf(descUtf8);

        // 3. Add or find NameAndTypeRefItem
        NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(nameIndex, descIndex);
        int nameAndTypeIndex = constPool.getIndexOf(nameAndType);

        // 4. Create the new FieldRefItem (optional, depending on usage)
        // If fields reference FieldRefItem, uncomment the following lines:
        //FieldRefItem fieldRef = constPool.findOrAddFieldRef(thisClass, nameAndTypeIndex);
        //int fieldRefIndex = constPool.getIndexOf(fieldRef);

        // 5. Create the new FieldEntry
        FieldEntry newField = new FieldEntry();
        newField.setClassFile(this);
        newField.setAccess(accessFlags);
        newField.setNameIndex(nameIndex);
        newField.setDescIndex(descIndex);
        newField.setAttributes(new ArrayList<>(attributes));
        newField.setOwnerName(getClassName());
        newField.setName(fieldName);
        newField.setDesc(fieldDescriptor);
        newField.setKey(fieldName + fieldDescriptor);

        // 6. Add the new FieldEntry to the fields list
        fields.add(newField);

        return newField;
    }

    public boolean removeField(String fieldName, String fieldDescriptor) {
        for (int i = 0; i < fields.size(); i++) {
            FieldEntry field = fields.get(i);
            if (field.getName().equals(fieldName) && field.getDesc().equals(fieldDescriptor)) {
                fields.remove(i);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------
    // Rebuild logic
    // ------------------------------------------------------------------------

    /**
     * Rebuilds this ClassFile into a fresh byte[] using the current in-memory state.
     *
     * @return a new byte[] representing the updated class file.
     * @throws IOException if an I/O error occurs while writing to the in-memory stream.
     */
    public byte[] write() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bos)) {

            // 1) magic number
            dos.writeInt(0xCAFEBABE);

            // 2) minor_version, major_version
            dos.writeShort(minorVersion);
            dos.writeShort(majorVersion);

            // 3) constant_pool_count
            int cpCountToWrite = computeConstantPoolCount();
            dos.writeShort(cpCountToWrite);

            // 3a) Write each constant pool item
            //     Note: The actual item indices run 1..cpCountToWrite-1.
            //     Some items are null placeholders after longs/doubles.
            List<Item<?>> cpItems = constPool.getItems();
            for (int i = 1; i < cpItems.size(); i++) {
                Item<?> itm = cpItems.get(i);
                if (itm == null) {
                    // skip placeholder slot
                    continue;
                }
                // Write tag
                dos.writeByte(itm.getType());
                // Write item data
                itm.write(dos);

                // If it's a Long/Double, skip the next slot
                if (itm.getType() == Item.ITEM_LONG || itm.getType() == Item.ITEM_DOUBLE) {
                    i++;
                }
            }

            // 4) access flags, this_class, super_class
            dos.writeShort(access);
            dos.writeShort(thisClass);
            dos.writeShort(superClass);

            // 5) interfaces
            dos.writeShort(interfaces.size());
            for (int ifcIndex : interfaces) {
                dos.writeShort(ifcIndex);
            }

            // 6) fields
            dos.writeShort(fields.size());
            for (FieldEntry f : fields) {
                f.write(dos);  // We'll assume FieldEntry has a write(DataOutputStream) method
            }

            // 7) methods
            dos.writeShort(methods.size());
            for (MethodEntry m : methods) {
                m.write(dos);  // We'll assume MethodEntry has a write(DataOutputStream) method
            }

            // 8) class-level attributes
            dos.writeShort(classAttributes.size());
            for (Attribute attr : classAttributes) {
                attr.write(dos);  // We'll assume Attribute has a write(DataOutputStream) method
            }

            dos.flush();
        }

        // Return the newly built byte array
        return bos.toByteArray();
    }

    /**
     * Computes the number of entries to write in the constant_pool_count field.
     *
     * The actual count includes 1-based indices up to the last real item,
     * but we skip placeholder slots following Long or Double items.
     *
     * Example:
     * If the last used index (considering the skip for doubles/longs) is 12,
     * then constant_pool_count is 13.
     */
    private int computeConstantPoolCount() {
        int realCount = 1; // The spec says: if the highest index is N, we store (N+1).
        List<Item<?>> cpItems = constPool.getItems();

        for (int i = 1; i < cpItems.size(); i++) {
            Item<?> itm = cpItems.get(i);
            if (itm != null) {
                realCount++;
                if (itm.getType() == Item.ITEM_LONG || itm.getType() == Item.ITEM_DOUBLE) {
                    i++;
                }
            }
        }
        return realCount;
    }

    // --- Utility to resolve class name from thisClass or superClass ---
    private String resolveClassName(int classIndex) {
        try {
            ClassRefItem classRef = (ClassRefItem) constPool.getItem(classIndex);
            return classRef.getClassName();
        } catch (ClassCastException | IllegalArgumentException e) {
            return "InvalidClassRef(" + classIndex + ")";
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ClassFile {\n");

        // Version
        sb.append("  Version: ").append(majorVersion).append(".").append(minorVersion).append("\n");

        // Access Flags
        sb.append("  Access Flags: 0x").append(Integer.toHexString(access)).append(" (")
                .append(getAccessFlagsDescription(access)).append(")").append("\n");

        // Class / Superclass
        sb.append("  This Class: ").append(getClassName()).append("\n");
        sb.append("  Super Class: ").append(getSuperClassName()).append("\n");

        // Interfaces
        if (!interfaces.isEmpty()) {
            sb.append("  Interfaces:\n");
            for (int ifaceIndex : interfaces) {
                sb.append("    - ").append(resolveClassName(ifaceIndex)).append("\n");
            }
        } else {
            sb.append("  Interfaces: None\n");
        }

        // Constant Pool
        sb.append("\n").append(constPool.toString()).append("\n");

        // Fields
        if (!fields.isEmpty()) {
            sb.append("  Fields:\n");
            for (FieldEntry field : fields) {
                sb.append("    ").append(field).append("\n");
            }
        } else {
            sb.append("  Fields: None\n");
        }

        // Methods
        if (!methods.isEmpty()) {
            sb.append("\n  Methods:\n");
            for (MethodEntry method : methods) {
                sb.append("    ").append(method).append("\n");
                CodeAttribute codeAttr = method.getCodeAttribute();
                if (codeAttr != null) {
                    sb.append(codeAttr.prettyPrintCode()).append("\n");
                }
            }
        } else {
            sb.append("\n  Methods: None\n");
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Provides a human-readable description of access flags.
     */
    private String getAccessFlagsDescription(int access) {
        List<String> flags = new ArrayList<>();
        if ((access & 0x0001) != 0) flags.add("public");
        if ((access & 0x0010) != 0) flags.add("final");
        if ((access & 0x0020) != 0) flags.add("super");
        if ((access & 0x0200) != 0) flags.add("interface");
        if ((access & 0x0400) != 0) flags.add("abstract");
        if ((access & 0x1000) != 0) flags.add("synthetic");
        if ((access & 0x2000) != 0) flags.add("annotation");
        if ((access & 0x4000) != 0) flags.add("enum");

        return String.join(", ", flags);
    }

    /**
     * Creates and adds a new method to the class file using the Bytecode utility.
     *
     * @param accessFlags    The access flags for the method (e.g., 0x0001 for public).
     * @param methodName     The name of the method, e.g., "myMethod".
     * @param returnType     The return type of the method as a Class object, e.g., void.class, int.class.
     * @param parameterTypes The parameter types of the method as Class objects.
     */
    public MethodEntry createNewMethod(int accessFlags, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        return createNewMethod(true, accessFlags, methodName, returnType, parameterTypes);
    }

    /**
     * Creates and adds a new method to the class file using the Bytecode utility.
     *
     * @apiNote assumed non-static method at the moment
     *
     * @param addDefaultBody Whether to add a default body to the method.
     * @param accessFlags    The access flags for the method (e.g., 0x0001 for public).
     * @param methodName     The name of the method, e.g., "myMethod".
     * @param returnType     The return type of the method as a Class object, e.g., void.class, int.class.
     * @param parameterTypes The parameter types of the method as Class objects.
     */
    public MethodEntry createNewMethod(boolean addDefaultBody, int accessFlags, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        Logger.info("Creating method: " + methodName + " with return type: " + returnType.getName() + " and parameters: " + Arrays.toString(parameterTypes));

        String methodDescriptor = generateMethodDescriptor(returnType, parameterTypes);
        Logger.info("Generated method descriptor: " + methodDescriptor);

        Utf8Item nameUtf8 = constPool.findOrAddUtf8(methodName);
        int nameIndex = constPool.getIndexOf(nameUtf8);

        // 3. Add method descriptor to constant pool
        Utf8Item descUtf8 = constPool.findOrAddUtf8(methodDescriptor);
        int descIndex = constPool.getIndexOf(descUtf8);

        // 4. Add or find "Code" in the constant pool
        Utf8Item codeUtf8 = constPool.findOrAddUtf8("Code");
        int codeNameIndex = constPool.getIndexOf(codeUtf8);

        // 5. Create CodeAttribute with the correct nameIndex
        CodeAttribute codeAttr = new CodeAttribute("Code", null, codeNameIndex, 0); // Parent will be set later
        codeAttr.setMaxStack(10); // Set an appropriate max stack size
        int maxLocals = Modifiers.isStatic(accessFlags) ? parameterTypes.length : parameterTypes.length + 1;
        codeAttr.setMaxLocals(maxLocals); // +1 for 'this' if not static
        codeAttr.setCode(new byte[0]); // Initialize with empty bytecode
        codeAttr.setAttributes(new ArrayList<>()); // Add any additional attributes if necessary

        // 6. Create a list of attributes and add the CodeAttribute
        List<Attribute> methodAttributes = new ArrayList<>();
        methodAttributes.add(codeAttr);

        // 7. Create the new MethodEntry using the correct constructor
        MethodEntry newMethod = new MethodEntry(this, accessFlags, nameIndex, descIndex, methodAttributes);
        newMethod.setName(methodName);
        newMethod.setDesc(methodDescriptor);
        newMethod.setOwnerName(getClassName());
        newMethod.setKey(methodName + methodDescriptor);

        // 8. Set the parent in CodeAttribute
        codeAttr.setParent(newMethod); // Ensure CodeAttribute has a setParent method

        // 9. Add the new MethodEntry to the methods list
        methods.add(newMethod);
        Logger.info("Method " + methodName + " created successfully.");

        // 10. Build the method body
        if(!addDefaultBody)
            return newMethod;

        Bytecode bytecode = new Bytecode(newMethod);
        if(!Modifiers.isStatic(accessFlags))
        {
            System.out.println("Adding 'this' to the stack");
            bytecode.addALoad(0);
        }

        // Example: Append instructions based on return type
        if (returnType.equals(void.class)) {
            bytecode.addReturn(0xB1); // RETURN
        } else if (returnType.equals(int.class) || returnType.equals(short.class) ||
                returnType.equals(byte.class) || returnType.equals(char.class) ||
                returnType.equals(boolean.class)) {
            bytecode.addILoad(0); // Example: Load 'this' or a local variable
            bytecode.addIConst(0); // Push integer constant 0
            bytecode.addReturn(0xAC); // IRETURN
        } else if (returnType.equals(long.class)) {
            bytecode.addLLoad(0); // Example: Load a long local variable
            bytecode.addReturn(0xAD); // LRETURN
        } else if (returnType.equals(float.class)) {
            bytecode.addFLoad(0); // Example: Load a float local variable
            bytecode.addReturn(0xAE); // FRETURN
        } else if (returnType.equals(double.class)) {
            bytecode.addDLoad(0); // Example: Load a double local variable
            bytecode.addReturn(0xAF); // DRETURN
        } else {
            bytecode.addAConstNull(); // Example: Load an object reference
            bytecode.addReturn(0xB0); // ARETURN
        }

        // Finalize and write back the bytecode modifications
        try {
            bytecode.finalizeBytecode();
            Logger.info("Bytecode for method " + methodName + " finalized successfully.");
        } catch (IOException e) {
            Logger.error("Failed to finalize bytecode for method " + methodName + ": " + e.getMessage());
        }
        return newMethod;
    }

    /**
     * Generates a JVM method descriptor string based on the provided return type and parameter types.
     *
     * @param returnType     The return type of the method.
     * @param parameterTypes The parameter types of the method.
     * @return The JVM method descriptor string.
     */
    private String generateMethodDescriptor(Class<?> returnType, Class<?>... parameterTypes) {
        StringBuilder descriptor = new StringBuilder();
        descriptor.append('(');
        for (Class<?> paramType : parameterTypes) {
            descriptor.append(getTypeDescriptor(paramType));
        }
        descriptor.append(')');
        descriptor.append(getTypeDescriptor(returnType));
        return descriptor.toString();
    }

    /**
     * Returns the JVM type descriptor for a given Class object.
     *
     * @param clazz The Class object.
     * @return The JVM type descriptor string.
     */
    private String getTypeDescriptor(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            if (clazz == void.class) {
                return "V";
            } else if (clazz == int.class) {
                return "I";
            } else if (clazz == boolean.class) {
                return "Z";
            } else if (clazz == byte.class) {
                return "B";
            } else if (clazz == char.class) {
                return "C";
            } else if (clazz == short.class) {
                return "S";
            } else if (clazz == long.class) {
                return "J";
            } else if (clazz == float.class) {
                return "F";
            } else if (clazz == double.class) {
                return "D";
            }
            throw new IllegalArgumentException("Unrecognized primitive type: " + clazz.getName());
        } else if (clazz.isArray()) {
            return clazz.getName().replace('.', '/');
        } else {
            return "L" + clazz.getName().replace('.', '/') + ";";
        }
    }

    /**
     * Generates bytecode for the method body based on the return type and parameters.
     *
     * @param returnType     The return type of the method (e.g., void, int, java/lang/String).
     * @return A byte array representing the method's bytecode.
     */
    private byte[] generateBytecodeForReturnType(Class<?> returnType) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            switch (returnType.getName()) {
                case "void":
                    dos.writeByte(0xB1); // RETURN
                    break;
                case "int":
                case "short":
                case "byte":
                case "char":
                case "boolean":
                    dos.writeByte(0x03); // ICONST_0
                    dos.writeByte(0xAC); // IRETURN
                    break;
                case "long":
                    dos.writeByte(0x09); // LCONST_0
                    dos.writeByte(0xAD); // LRETURN
                    break;
                case "float":
                    dos.writeByte(0x0B); // FCONST_0
                    dos.writeByte(0xAE); // FRETURN
                    break;
                case "double":
                    dos.writeByte(0x0E); // DCONST_0
                    dos.writeByte(0xAF); // DRETURN
                    break;
                default:
                    // Assume it's an object type
                    dos.writeByte(0x01); // ACONST_NULL
                    dos.writeByte(0xB0); // ARETURN
                    break;
            }
            dos.flush();
        } catch (IOException e) {
            Logger.error("Error generating bytecode: " + e.getMessage());
        }
        return baos.toByteArray();
    }
}
