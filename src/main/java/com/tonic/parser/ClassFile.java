package com.tonic.parser;

import com.tonic.analysis.Bytecode;
import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.analysis.visitor.AbstractClassVisitor;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.constpool.*;
import com.tonic.utill.*;
import lombok.Getter;
import lombok.Setter;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a Java class file with full parsing and manipulation capabilities.
 * Provides access to constant pool, fields, methods, and class attributes.
 */
@Getter
public class ClassFile extends AbstractParser {
    @Setter
    private int minorVersion;
    @Setter
    private int majorVersion;
    private int access;
    private int thisClass;
    private int superClass;
    @Setter
    private ConstPool constPool;
    private List<Attribute> classAttributes;
    private List<FieldEntry> fields;
    private List<MethodEntry> methods;
    private List<Integer> interfaces;

    private static final int MINOR_VERSION_OFFSET = 4;
    private static final int MAJOR_VERSION_OFFSET = 6;

    /**
     * Creates a ClassFile from raw class bytes.
     *
     * @param classBytes the raw bytes of a .class file
     */
    protected ClassFile(final byte[] classBytes) {
        super(classBytes);
    }

    /**
     * Creates a ClassFile from an InputStream.
     *
     * @param inputStream the input stream containing class file data
     * @throws IOException if reading from the stream fails
     */
    public ClassFile(final InputStream inputStream) throws IOException {
        super(inputStream.readAllBytes());
    }

    /**
     * Creates a new class file from scratch with default constructor and class initializer.
     *
     * @param className the internal class name (e.g., "com/example/MyClass")
     * @param accessFlags the class access flags
     */
    public ClassFile(String className, int accessFlags) {
        super(new byte[0], false);

        this.constPool = new ConstPool();
        this.constPool.setClassFile(this);

        Utf8Item thisClassNameUtf8 = constPool.findOrAddUtf8(className);
        Utf8Item superClassNameUtf8 = constPool.findOrAddUtf8("java/lang/Object");

        ClassRefItem thisClassRef = constPool.findOrAddClassRef(constPool.getIndexOf(thisClassNameUtf8));
        ClassRefItem superClassRef = constPool.findOrAddClassRef(constPool.getIndexOf(superClassNameUtf8));

        this.access = accessFlags;

        thisClass = constPool.getIndexOf(thisClassRef);
        superClass = constPool.getIndexOf(superClassRef);

        this.interfaces = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.methods = new ArrayList<>();
        this.classAttributes = new ArrayList<>();

        this.minorVersion = 0;
        this.majorVersion = 55;

        createDefaultConstructor();
        createDefaultClassInitializer();
    }

    /**
     * Creates and adds a default static class initializer.
     *
     * @return the created class initializer method
     */
    private MethodEntry createDefaultClassInitializer() {
        int constructorAccessFlags = new AccessBuilder().setPublic().setStatic().build();
        String constructorName = "<clinit>";
        String constructorDescriptor = "()V";

        Utf8Item nameUtf8 = constPool.findOrAddUtf8(constructorName);
        int nameIndex = constPool.getIndexOf(nameUtf8);

        Utf8Item descUtf8 = constPool.findOrAddUtf8(constructorDescriptor);
        int descIndex = constPool.getIndexOf(descUtf8);

        MethodEntry constructor = new MethodEntry(this, constructorAccessFlags, nameIndex, descIndex, new ArrayList<>());
        constructor.setName(constructorName);
        constructor.setDesc(constructorDescriptor);
        constructor.setOwnerName(getClassName());
        constructor.setKey(constructorName + constructorDescriptor);

        CodeAttribute codeAttr = new CodeAttribute("Code", constructor, constPool.getIndexOf(constPool.findOrAddUtf8("Code")), 0);
        codeAttr.setMaxStack(10);
        codeAttr.setMaxLocals(1);
        constructor.getAttributes().add(codeAttr);

        codeAttr.setCode(new byte[0]);

        Bytecode bytecode = new Bytecode(constructor);
        bytecode.addReturn(ReturnType.RETURN);

        try {
            bytecode.finalizeBytecode();
        } catch (IOException e) {
            Logger.error("Failed to finalize bytecode for default constructor: " + e.getMessage());
        }

        methods.add(constructor);
        return constructor;
    }

    /**
     * Creates and adds a default no-argument constructor to the class.
     *
     * @return the created constructor method
     */
    private MethodEntry createDefaultConstructor() {
        int constructorAccessFlags = Modifiers.PUBLIC;
        String constructorName = "<init>";
        String constructorDescriptor = "()V";

        Utf8Item nameUtf8 = constPool.findOrAddUtf8(constructorName);
        int nameIndex = constPool.getIndexOf(nameUtf8);

        Utf8Item descUtf8 = constPool.findOrAddUtf8(constructorDescriptor);
        int descIndex = constPool.getIndexOf(descUtf8);

        NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(nameIndex, descIndex);
        int nameAndTypeIndex = constPool.getIndexOf(nameAndType);

        MethodRefItem superConstructorRef = constPool.findOrAddMethodRef(superClass, nameAndTypeIndex);

        MethodEntry constructor = new MethodEntry(this, constructorAccessFlags, nameIndex, descIndex, new ArrayList<>());
        constructor.setName(constructorName);
        constructor.setDesc(constructorDescriptor);
        constructor.setOwnerName(getClassName());
        constructor.setKey(constructorName + constructorDescriptor);

        CodeAttribute codeAttr = new CodeAttribute("Code", constructor, constPool.getIndexOf(constPool.findOrAddUtf8("Code")), 0);
        codeAttr.setMaxStack(10);
        codeAttr.setMaxLocals(1);
        constructor.getAttributes().add(codeAttr);

        codeAttr.setCode(new byte[0]);

        Bytecode bytecode = new Bytecode(constructor);

        bytecode.addALoad(0);
        bytecode.addInvokeSpecial(constPool.getIndexOf(superConstructorRef));
        bytecode.addReturn(ReturnType.RETURN);

        try {
            bytecode.finalizeBytecode();
        } catch (IOException e) {
            Logger.error("Failed to finalize bytecode for default constructor: " + e.getMessage());
        }

        methods.add(constructor);

        return constructor;
    }


    @Override
    protected void process() {
        minorVersion = readUnsignedShort();
        majorVersion = readUnsignedShort();
        Logger.info("Version: " + majorVersion + "." + minorVersion);

        new ConstPool(this);
        Logger.info(constPool.toString());

        access = readUnsignedShort();
        Logger.info("Access Flags: 0x" + Integer.toHexString(access));

        thisClass = readUnsignedShort();
        superClass = readUnsignedShort();
        Logger.info("This Class Index: " + thisClass);
        Logger.info("Super Class Index: " + superClass);

        final int interfaceCount = readUnsignedShort();
        Logger.info("Interfaces Count: " + interfaceCount);
        interfaces = new ArrayList<>(interfaceCount);
        for (int i = 0; i < interfaceCount; i++) {
            int ifaceIndex = readUnsignedShort();
            interfaces.add(ifaceIndex);
            Logger.info("  Interface " + (i + 1) + " Index: " + ifaceIndex);
        }

        final int fieldCount = readUnsignedShort();
        Logger.info("Fields Count: " + fieldCount);
        fields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            FieldEntry field = new FieldEntry(this);
            fields.add(field);
            Logger.info("  Field " + (i + 1) + ": " + field);
        }

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

        final int attributesCount = readUnsignedShort();
        classAttributes = new ArrayList<>(attributesCount);
        Logger.info("Class Attributes Count: " + attributesCount);
        for (int i = 0; i < attributesCount; i++) {
            Attribute attribute = Attribute.get(this, constPool, null);
            Logger.info("  Class Attribute " + (i + 1) + ": " + attribute);
            classAttributes.add(attribute);
        }
    }

    @Override
    protected boolean verify() {
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

    /**
     * Sets the class name (internal name like "com/tonic/TestClass").
     *
     * @param newName the new internal class name (e.g., "com/tonic/NewName")
     */
    public void setClassName(String newName) {
        String internalName = newName.replace('.', '/');
        String oldInternalName = getClassName();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(thisClass);
        Utf8Item utf8 = (Utf8Item) constPool.getItem(classRef.getValue());
        utf8.setValue(internalName);

        for (MethodEntry method : methods) {
            method.setOwnerName(internalName);
        }
        for (FieldEntry field : fields) {
            field.setOwnerName(internalName);
        }

        String oldDescriptor = "L" + oldInternalName + ";";
        String newDescriptor = "L" + internalName + ";";

        for (Item<?> item : constPool.getItems()) {
            if (item instanceof Utf8Item) {
                Utf8Item u = (Utf8Item) item;
                String value = u.getValue();
                if (value.contains(oldDescriptor)) {
                    u.setValue(value.replace(oldDescriptor, newDescriptor));
                }
            }
        }
    }

    /**
     * Sets the superclass name (internal name like "java/lang/Object").
     *
     * @param newSuperName the new internal name for the superclass (e.g., "java/lang/String")
     */
    public void setSuperClassName(String newSuperName) {
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(superClass);
        Utf8Item utf8 = (Utf8Item) constPool.getItem(classRef.getValue());
        utf8.setValue(newSuperName);
    }

    /**
     * Adds a new interface to the interfaces list.
     *
     * @param interfaceName the internal name of the interface (e.g., "java/util/List")
     */
    public void addInterface(String interfaceName) {
        String internalName = interfaceName.replace('.', '/');

        int existingIndex = -1;
        List<Item<?>> cpItems = constPool.getItems();
        for (int i = 1; i < cpItems.size(); i++) {
            Item<?> item = cpItems.get(i);
            if (item instanceof ClassRefItem) {
                ClassRefItem classRef = (ClassRefItem) item;
                Utf8Item nameUtf8 = (Utf8Item) constPool.getItem(classRef.getValue());
                if (nameUtf8 != null && nameUtf8.getValue().equals(internalName)) {
                    existingIndex = i;
                    break;
                }
            }
        }

        if (existingIndex == -1) {
            Utf8Item newUtf8 = new Utf8Item();
            newUtf8.setValue(internalName);
            int utf8Index = constPool.addItem(newUtf8);

            ClassRefItem newClassRef = new ClassRefItem();
            newClassRef.setValue(utf8Index);

            existingIndex = constPool.addItem(newClassRef);
        }

        if (!interfaces.contains(existingIndex)) {
            interfaces.add(existingIndex);
        }
    }

    /**
     * Adds a new field to the class.
     *
     * @param accessFlags the access flags for the field (e.g., 0x0001 for public)
     * @param fieldName the name of the field
     * @param fieldDescriptor the descriptor of the field (e.g., "Ljava/lang/String;")
     * @param attributes a list of attributes for the field (can be null)
     * @return the created FieldEntry
     */
    public FieldEntry createNewField(int accessFlags, String fieldName, String fieldDescriptor, List<Attribute> attributes) {
        if (attributes == null) {
            attributes = new ArrayList<>();
        }

        fieldDescriptor = TypeUtil.validateDescriptorFormat(fieldDescriptor);

        Utf8Item nameUtf8 = constPool.findOrAddUtf8(fieldName);
        int nameIndex = constPool.getIndexOf(nameUtf8);

        Utf8Item descUtf8 = constPool.findOrAddUtf8(fieldDescriptor);
        int descIndex = constPool.getIndexOf(descUtf8);

        constPool.findOrAddNameAndType(nameIndex, descIndex);

        FieldEntry newField = new FieldEntry();
        newField.setClassFile(this);
        newField.setAccess(accessFlags);
        newField.setNameIndex(nameIndex);
        newField.setDescIndex(descIndex);
        newField.setAttributes(new ArrayList<>(attributes));
        newField.setOwnerName(getClassName().replace('.', '/'));
        newField.setName(fieldName);
        newField.setDesc(fieldDescriptor);
        newField.setKey(fieldName + fieldDescriptor);

        fields.add(newField);

        return newField;
    }

    /**
     * Removes a field from the class.
     *
     * @param fieldName the name of the field to remove
     * @param fieldDescriptor the descriptor of the field to remove
     * @return true if the field was found and removed
     */
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

    /**
     * Sets the initial value of a field in either clinit (static) or init (non-static) method.
     *
     * @param field the field to set the initial value for
     * @param value the value to set (supports primitives and Strings)
     * @throws IOException if there is an error modifying bytecode
     */
    public void setFieldInitialValue(FieldEntry field, Object value) throws IOException {
        boolean isStatic = Modifiers.isStatic(field.getAccess());

        MethodEntry method = isStatic
                ? methods.stream()
                .filter(m -> m.getName().equals("<clinit>") && m.getDesc().equals("()V"))
                .findFirst()
                .orElseGet(this::createDefaultClassInitializer)
                : methods.stream()
                .filter(m -> m.getName().equals("<init>") && m.getDesc().equals("()V"))
                .findFirst()
                .orElseGet(this::createDefaultConstructor);

        CodeAttribute codeAttr = method.getCodeAttribute();
        if (codeAttr == null) {
            codeAttr = new CodeAttribute("Code", method, constPool.getIndexOf(constPool.findOrAddUtf8("Code")), 0);
            codeAttr.setMaxStack(10);
            codeAttr.setMaxLocals(isStatic ? 0 : 1);
            method.getAttributes().add(codeAttr);
            codeAttr.setCode(new byte[0]);
        }

        Bytecode bytecode = new Bytecode(method);
        bytecode.setInsertBefore(true);

        if(!isStatic) {
            bytecode.addALoad(0);
        }

        if (value instanceof Integer) {
            bytecode.addIConst((int) value);
        } else if (value instanceof Long) {
            bytecode.addLConst((long) value);
        } else if (value instanceof Float) {
            bytecode.addIConst(Float.floatToRawIntBits((float) value));
        } else if (value instanceof Double) {
            bytecode.addLConst(Double.doubleToRawLongBits((double) value));
        } else if (value instanceof String) {
            Item<?> stringItem = constPool.findOrAddString((String) value);
            bytecode.addIConst(constPool.getIndexOf(stringItem));
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + value.getClass().getName());
        }

        int fieldRefIndex = constPool.getIndexOf(constPool.findOrAddField(field.getOwnerName(), field.getName(), field.getDesc()));
        if (isStatic) {
            bytecode.addPutStatic(fieldRefIndex);
        } else {
            bytecode.addPutField(fieldRefIndex);
        }

        bytecode.finalizeBytecode();
    }




    /**
     * Generates a setter method for the given field.
     *
     * @param entry    The field entry to generate the setter for.
     * @param isStatic Whether the field is static or not.
     * @throws IOException If an error occurs while generating the setter.
     */
    public MethodEntry generateSetter(FieldEntry entry, boolean isStatic) throws IOException {
        String name = "set" + entry.getName().substring(0, 1).toUpperCase() + entry.getName().substring(1);
        return generateSetter(entry, name, isStatic);
    }

    /**
     * Generates a setter method for the given field with the specified name.
     *
     * @param entry    The field entry to generate the setter for.
     * @param name     The name of the setter method.
     * @param isStatic Whether the field is static or not.
     * @throws IOException If an error occurs while generating the setter.
     */
    public MethodEntry generateSetter(FieldEntry entry, String name, boolean isStatic) throws IOException {
        int access = isStatic
                ? new AccessBuilder().setPublic().setStatic().build()
                : new AccessBuilder().setPublic().build();

        MethodEntry method = createNewMethod(access, name, "V", entry.getDesc());

        Bytecode bytecode = new Bytecode(method);
        int fieldRefIndex = constPool.getIndexOf(bytecode.getConstPool().findOrAddField(entry.getOwnerName(), entry.getName(), entry.getDesc()));

        if (!isStatic) {
            bytecode.addALoad(0);
            bytecode.addLoad(1, entry.getDesc());
            bytecode.addPutField(fieldRefIndex);
        } else {
            bytecode.addLoad(0, entry.getDesc());
            bytecode.addPutStatic(fieldRefIndex);
        }

        bytecode.addReturn(ReturnType.RETURN);
        bytecode.finalizeBytecode();

        return method;
    }

    /**
     * Generates a getter method for the given field.
     *
     * @param entry    The field entry to generate the getter for.
     * @param isStatic Whether the field is static or not.
     * @throws IOException If an error occurs while generating the getter.
     */
    public MethodEntry generateGetter(FieldEntry entry, boolean isStatic) throws IOException {
        String name = "get" + entry.getName().substring(0, 1).toUpperCase() + entry.getName().substring(1);
        return generateGetter(entry, name, isStatic);
    }

    /**
     * Generates a getter method for the given field with the specified name.
     *
     * @param entry    The field entry to generate the getter for.
     * @param name     The name of the getter method.
     * @param isStatic Whether the field is static or not.
     * @throws IOException If an error occurs while generating the getter.
     */
    public MethodEntry generateGetter(FieldEntry entry, String name, boolean isStatic) throws IOException {
        int access = isStatic
                ? new AccessBuilder().setPublic().setStatic().build()
                : new AccessBuilder().setPublic().build();

        MethodEntry method = createNewMethod(access, name, entry.getDesc());

        Bytecode bytecode = new Bytecode(method);
        int fieldRefIndex = constPool.getIndexOf(bytecode.getConstPool().findOrAddField(entry.getOwnerName(), entry.getName(), entry.getDesc()));

        if (!isStatic) {
            bytecode.addALoad(0);
            bytecode.addGetField(fieldRefIndex);
        } else {
            bytecode.addGetStatic(fieldRefIndex);
        }

        bytecode.addReturn(ReturnType.fromDescriptor(entry.getDesc()));
        bytecode.finalizeBytecode();
        return method;
    }


    /**
     * Rebuilds this ClassFile into a fresh byte array using the current in-memory state.
     *
     * @return a new byte array representing the updated class file
     * @throws IOException if an I/O error occurs while writing
     */
    public byte[] write() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bos)) {

            dos.writeInt(0xCAFEBABE);

            dos.writeShort(minorVersion);
            dos.writeShort(majorVersion);

            int cpCountToWrite = computeConstantPoolCount();
            dos.writeShort(cpCountToWrite);

            List<Item<?>> cpItems = constPool.getItems();
            for (int i = 1; i < cpItems.size(); i++) {
                Item<?> itm = cpItems.get(i);
                if (itm == null) {
                    continue;
                }
                dos.writeByte(itm.getType());
                itm.write(dos);

                if (itm.getType() == Item.ITEM_LONG || itm.getType() == Item.ITEM_DOUBLE) {
                    i++;
                }
            }

            dos.writeShort(access);
            dos.writeShort(thisClass);
            dos.writeShort(superClass);

            dos.writeShort(interfaces.size());
            for (int ifcIndex : interfaces) {
                dos.writeShort(ifcIndex);
            }

            dos.writeShort(fields.size());
            for (FieldEntry f : fields) {
                f.write(dos);
            }

            dos.writeShort(methods.size());
            for (MethodEntry m : methods) {
                m.write(dos);
            }

            dos.writeShort(classAttributes.size());
            for (Attribute attr : classAttributes) {
                attr.write(dos);
            }

            dos.flush();
        }

        return bos.toByteArray();
    }

    /**
     * Computes the constant pool count for the class file header.
     *
     * @return the constant pool count value
     */
    private int computeConstantPoolCount() {
        int realCount = 1;
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

        sb.append("  Version: ").append(majorVersion).append(".").append(minorVersion).append("\n");

        sb.append("  Access Flags: 0x").append(Integer.toHexString(access)).append(" (")
                .append(getAccessFlagsDescription(access)).append(")").append("\n");

        sb.append("  This Class: ").append(getClassName()).append("\n");
        sb.append("  Super Class: ").append(getSuperClassName()).append("\n");

        if (!interfaces.isEmpty()) {
            sb.append("  Interfaces:\n");
            for (int ifaceIndex : interfaces) {
                sb.append("    - ").append(resolveClassName(ifaceIndex)).append("\n");
            }
        } else {
            sb.append("  Interfaces: None\n");
        }

        sb.append("\n").append(constPool.toString()).append("\n");

        if (!fields.isEmpty()) {
            sb.append("  Fields:\n");
            for (FieldEntry field : fields) {
                sb.append("    ").append(field).append("\n");
            }
        } else {
            sb.append("  Fields: None\n");
        }

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
     * Creates a new method with Class-based parameter types.
     *
     * @param accessFlags the access flags for the method
     * @param methodName the name of the method
     * @param returnType the return type as a Class object
     * @param parameterTypes the parameter types as Class objects
     * @return the created MethodEntry
     */
    public MethodEntry createNewMethod(int accessFlags, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        Logger.info("Creating method: " + methodName + " with return type: " + returnType.getName() + " and parameters: " + Arrays.toString(parameterTypes));
        String methodDescriptor = generateMethodDescriptor(returnType, parameterTypes);
        Logger.info("Generated method descriptor: " + methodDescriptor);
        return createNewMethod(false, accessFlags, methodName, methodDescriptor, (Object[]) parameterTypes);
    }

    /**
     * Creates a new method with String-based parameter types.
     *
     * @param accessFlags the access flags for the method
     * @param methodName the name of the method
     * @param returnType the return type as a descriptor string
     * @param parameterTypes the parameter types as descriptor strings
     * @return the created MethodEntry
     */
    public MethodEntry createNewMethod(int accessFlags, String methodName, String returnType, String... parameterTypes) {
        returnType = TypeUtil.validateDescriptorFormat(returnType);
        Logger.info("Creating method: " + methodName + " with return type: " + returnType + " and parameters: " + Arrays.toString(parameterTypes));
        String methodDescriptor = generateMethodDescriptor(returnType, parameterTypes);
        Logger.info("Generated method descriptor: " + methodDescriptor);
        return createNewMethod(false, accessFlags, methodName, methodDescriptor, (Object[]) parameterTypes);
    }

    /**
     * Creates a new method with a complete method descriptor.
     * Use this when you have a pre-built descriptor like "(Lcom/test/ClassB;)V".
     *
     * @param accessFlags the access flags for the method
     * @param methodName the name of the method
     * @param methodDescriptor the complete method descriptor (e.g., "(Ljava/lang/String;I)V")
     * @return the created MethodEntry
     */
    public MethodEntry createNewMethodWithDescriptor(int accessFlags, String methodName, String methodDescriptor) {
        Logger.info("Creating method: " + methodName + " with descriptor: " + methodDescriptor);
        // Count parameters from descriptor for maxLocals calculation
        int paramCount = countParametersFromDescriptor(methodDescriptor);
        return createNewMethod(false, accessFlags, methodName, methodDescriptor, new Object[paramCount]);
    }

    /**
     * Counts the number of parameters in a method descriptor.
     */
    private int countParametersFromDescriptor(String descriptor) {
        if (descriptor == null || !descriptor.startsWith("(")) return 0;
        int count = 0;
        int i = 1; // skip opening (
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                // Object type - skip to ;
                int end = descriptor.indexOf(';', i);
                if (end > i) {
                    i = end + 1;
                } else {
                    i++;
                }
                count++;
            } else if (c == '[') {
                // Array - skip to element type
                i++;
            } else {
                // Primitive or end of array
                if (c != '[') count++;
                i++;
            }
        }
        return count;
    }

    /**
     * Creates a new method with optional default body generation.
     *
     * @param addDefaultBody whether to add a default body to the method
     * @param accessFlags the access flags for the method
     * @param methodName the name of the method
     * @param methodDescriptor the method descriptor string
     * @param parameterTypes the parameter types
     * @return the created MethodEntry
     */
    public MethodEntry createNewMethod(boolean addDefaultBody, int accessFlags, String methodName, String methodDescriptor, Object... parameterTypes) {
        Logger.info("Generated method descriptor: " + methodDescriptor);

        Utf8Item nameUtf8 = constPool.findOrAddUtf8(methodName);
        int nameIndex = constPool.getIndexOf(nameUtf8);

        Utf8Item descUtf8 = constPool.findOrAddUtf8(methodDescriptor);
        int descIndex = constPool.getIndexOf(descUtf8);

        Utf8Item codeUtf8 = constPool.findOrAddUtf8("Code");
        int codeNameIndex = constPool.getIndexOf(codeUtf8);

        CodeAttribute codeAttr = new CodeAttribute("Code", (MethodEntry)null, codeNameIndex, 0);
        codeAttr.setMaxStack(10);
        int maxLocals = Modifiers.isStatic(accessFlags) ? parameterTypes.length : parameterTypes.length + 1;
        codeAttr.setMaxLocals(maxLocals);
        codeAttr.setCode(new byte[0]);
        codeAttr.setAttributes(new ArrayList<>());

        List<Attribute> methodAttributes = new ArrayList<>();
        methodAttributes.add(codeAttr);

        MethodEntry newMethod = new MethodEntry(this, accessFlags, nameIndex, descIndex, methodAttributes);
        newMethod.setName(methodName);
        newMethod.setDesc(methodDescriptor);
        newMethod.setOwnerName(getClassName());
        newMethod.setKey(methodName + methodDescriptor);

        codeAttr.setParent(newMethod);

        methods.add(newMethod);
        Logger.info("Method " + methodName + " created successfully.");

        if(!addDefaultBody)
            return newMethod;

        Bytecode bytecode = new Bytecode(newMethod);
        if(!Modifiers.isStatic(accessFlags)) {
            Logger.info("Adding 'this' to the stack");
            bytecode.addALoad(0);
        }

        String returnType = methodDescriptor.split("\\)")[1];
        switch (returnType) {
            case "V":
                bytecode.addReturn(0xB1);
                break;
            case "I":
            case "S":
            case "B":
            case "C":
            case "Z":
                bytecode.addILoad(0);
                bytecode.addIConst(0);
                bytecode.addReturn(0xAC);
                break;
            case "J":
                bytecode.addLLoad(0);
                bytecode.addReturn(0xAD);
                break;
            case "F":
                bytecode.addFLoad(0);
                bytecode.addReturn(0xAE);
                break;
            case "D":
                bytecode.addDLoad(0);
                bytecode.addReturn(0xAF);
                break;
            default:
                bytecode.addAConstNull();
                bytecode.addReturn(0xB0);
                break;
        }

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
     * Generates a JVM method descriptor string based on the provided return type and parameter types.
     *
     * @param returnType     The return type of the method.
     * @param parameterTypes The parameter types of the method.
     * @return The JVM method descriptor string.
     */
    private String generateMethodDescriptor(String returnType, String... parameterTypes) {
        StringBuilder descriptor = new StringBuilder();
        descriptor.append('(');
        for (String paramType : parameterTypes) {
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
     * Returns the JVM type descriptor for a given field type descriptor string.
     *
     * @param descriptor The field type descriptor string.
     * @return The JVM type descriptor string.
     */
    private String getTypeDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("Descriptor cannot be null or empty");
        }

        return TypeUtil.validateDescriptorFormat(descriptor);
    }

    public void accept(AbstractClassVisitor visitor) {
        for(Item<?> item : constPool.getItems()) {
            if(item != null) {
                item.accept(visitor);
            }
        }

        for(Attribute attr : classAttributes) {
            visitor.visitClassAttribute(attr);
        }

        for (FieldEntry field : fields) {
            field.accept(visitor);
        }
        for (MethodEntry method : methods) {
            method.accept(visitor);
        }
    }

    /**
     * Computes StackMapTable frames for all methods in this class.
     * This should be called after modifying bytecode to ensure the class
     * passes verification on Java 7+ (class file version 51+).
     *
     * <p>Methods without a CodeAttribute (abstract/native) are skipped.
     *
     * @return the number of methods that had frames computed
     */
    public int computeFrames() {
        FrameGenerator generator = new FrameGenerator(constPool);
        int count = 0;
        for (MethodEntry method : methods) {
            if (method.getCodeAttribute() != null) {
                generator.updateStackMapTable(method);
                count++;
            }
        }
        Logger.info("Computed StackMapTable frames for " + count + " methods in " + getClassName());
        return count;
    }

    /**
     * Computes StackMapTable frames for a specific method by name and descriptor.
     *
     * @param methodName the method name (e.g., "myMethod")
     * @param descriptor the method descriptor (e.g., "(II)I")
     * @return true if the method was found and frames were computed, false otherwise
     */
    public boolean computeFrames(String methodName, String descriptor) {
        for (MethodEntry method : methods) {
            if (method.getName().equals(methodName) && method.getDesc().equals(descriptor)) {
                if (method.getCodeAttribute() != null) {
                    FrameGenerator generator = new FrameGenerator(constPool);
                    generator.updateStackMapTable(method);
                    Logger.info("Computed StackMapTable frames for " + methodName + descriptor);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Computes StackMapTable frames for a specific method.
     *
     * @param method the method to compute frames for
     */
    public void computeFrames(MethodEntry method) {
        if (method.getCodeAttribute() != null) {
            FrameGenerator generator = new FrameGenerator(constPool);
            generator.updateStackMapTable(method);
            Logger.info("Computed StackMapTable frames for " + method.getName() + method.getDesc());
        }
    }
}
