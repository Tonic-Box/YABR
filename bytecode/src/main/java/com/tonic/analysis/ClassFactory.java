package com.tonic.analysis;

import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.MethodRefItem;
import com.tonic.parser.constpool.NameAndTypeRefItem;
import com.tonic.util.AccessBuilder;
import com.tonic.util.Logger;
import com.tonic.util.Modifiers;
import com.tonic.util.ReturnType;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Generator of bytecode-backed members on a structural {@link ClassFile} - constructors,
 * initializers, accessors, field initial values, method bodies, and StackMapTable frames.
 */
public final class ClassFactory
{

    private ClassFactory()
    {
    }

    /**
     * Creates a detached class carrying a default constructor and class initializer.
     *
     * @param className binary name of the new class
     * @param accessFlags class access flags
     * @return the new class file
     */
    public static ClassFile createClass(String className, int accessFlags)
    {
        ClassFile cf = new ClassFile(className, accessFlags);
        addDefaultConstructor(cf);
        addDefaultClassInitializer(cf);
        return cf;
    }

    /**
     * Creates a class registered in a pool, adds a default constructor and class initializer,
     * then rebuilds it.
     *
     * @param pool pool that owns the new class
     * @param className binary name of the new class
     * @param accessFlags class access flags
     * @return the new class file
     * @throws IOException if the pool cannot create the class or the rebuild fails
     */
    public static ClassFile createClass(ClassPool pool, String className, int accessFlags) throws IOException
    {
        ClassFile cf = pool.createNewClass(className, accessFlags);
        addDefaultConstructor(cf);
        addDefaultClassInitializer(cf);
        cf.rebuild();
        return cf;
    }

    /**
     * Adds a no-argument constructor whose body only invokes the superclass constructor.
     *
     * @param cf class to receive the constructor
     * @return the added constructor
     */
    public static MethodEntry addDefaultConstructor(ClassFile cf)
    {
        String name = "<init>";
        String desc = "()V";
        int nameIndex = cf.getConstPool().utf8Index(name);
        int descIndex = cf.getConstPool().utf8Index(desc);
        NameAndTypeRefItem nameAndType = cf.getConstPool().findOrAddNameAndType(nameIndex, descIndex);
        int nameAndTypeIndex = cf.getConstPool().getIndexOf(nameAndType);
        MethodRefItem superConstructorRef = cf.getConstPool().findOrAddMethodRef(cf.getSuperClass(), nameAndTypeIndex);

        MethodEntry constructor = new MethodEntry(cf, Modifiers.PUBLIC, nameIndex, descIndex, new ArrayList<>());
        constructor.setName(name);
        constructor.setDesc(desc);
        constructor.setOwnerName(cf.getClassName());
        constructor.setKey(name + desc);

        CodeAttribute codeAttr = newCodeAttribute(cf, constructor, 1, 1);
        constructor.getAttributes().add(codeAttr);
        codeAttr.setCode(new byte[0]);

        Bytecode bytecode = new Bytecode(constructor);
        bytecode.addALoad(0);
        bytecode.addInvokeSpecial(cf.getConstPool().getIndexOf(superConstructorRef));
        bytecode.addReturn(ReturnType.RETURN);
        finalizeBody(bytecode,"default constructor");

        cf.getMethods().add(constructor);
        return constructor;
    }

    /**
     * Adds an empty static class initializer.
     *
     * @param cf class to receive the initializer
     * @return the added initializer
     */
    public static MethodEntry addDefaultClassInitializer(ClassFile cf)
    {
        String name = "<clinit>";
        String desc = "()V";
        int accessFlags = new AccessBuilder().setPublic().setStatic().build();
        int nameIndex = cf.getConstPool().utf8Index(name);
        int descIndex = cf.getConstPool().utf8Index(desc);

        MethodEntry initializer = new MethodEntry(cf, accessFlags, nameIndex, descIndex, new ArrayList<>());
        initializer.setName(name);
        initializer.setDesc(desc);
        initializer.setOwnerName(cf.getClassName());
        initializer.setKey(name + desc);

        CodeAttribute codeAttr = newCodeAttribute(cf, initializer, 0, 0);
        initializer.getAttributes().add(codeAttr);
        codeAttr.setCode(new byte[0]);

        Bytecode bytecode = new Bytecode(initializer);
        bytecode.addReturn(ReturnType.RETURN);
        finalizeBody(bytecode,"default class initializer");

        cf.getMethods().add(initializer);
        return initializer;
    }

    /**
     * Creates a method whose generated body immediately returns a zero or null value
     * of the declared return type.
     *
     * @param cf class to receive the method
     * @param accessFlags method access flags
     * @param methodName method name
     * @param methodDescriptor method descriptor
     * @return the added method
     */
    public static MethodEntry createMethodWithBody(ClassFile cf, int accessFlags, String methodName, String methodDescriptor)
    {
        MethodEntry method = cf.createNewMethodWithDescriptor(accessFlags, methodName, methodDescriptor);

        Bytecode bytecode = new Bytecode(method);
        if (!Modifiers.isStatic(accessFlags))
        {
            bytecode.addALoad(0);
        }
        String returnType = methodDescriptor.split("\\)")[1];
        switch (returnType)
        {
            case "V":
                bytecode.addReturn(ReturnType.RETURN.getOpcode());
                break;
            case "I":
            case "S":
            case "B":
            case "C":
            case "Z":
                bytecode.addILoad(0);
                bytecode.addIConst(0);
                bytecode.addReturn(ReturnType.IRETURN.getOpcode());
                break;
            case "J":
                bytecode.addLLoad(0);
                bytecode.addReturn(ReturnType.LRETURN.getOpcode());
                break;
            case "F":
                bytecode.addFLoad(0);
                bytecode.addReturn(ReturnType.FRETURN.getOpcode());
                break;
            case "D":
                bytecode.addDLoad(0);
                bytecode.addReturn(ReturnType.DRETURN.getOpcode());
                break;
            default:
                bytecode.addAConstNull();
                bytecode.addReturn(ReturnType.ARETURN.getOpcode());
                break;
        }
        finalizeBody(bytecode,method.getName());
        return method;
    }

    /**
     * Generates a getter named {@code get<Field>} for a field.
     *
     * @param cf class to receive the getter
     * @param entry field to read
     * @param isStatic true to emit a static getter reading a static field
     * @return the generated getter
     * @throws IOException if the method body cannot be finalized
     */
    public static MethodEntry generateGetter(ClassFile cf, FieldEntry entry, boolean isStatic) throws IOException
    {
        String name = "get" + entry.getName().substring(0, 1).toUpperCase() + entry.getName().substring(1);
        return generateGetter(cf, entry, name, isStatic);
    }

    /**
     * Generates a getter under a caller-chosen name for a field.
     *
     * @param cf class to receive the getter
     * @param entry field to read
     * @param name name of the generated method
     * @param isStatic true to emit a static getter reading a static field
     * @return the generated getter
     * @throws IOException if the method body cannot be finalized
     */
    public static MethodEntry generateGetter(ClassFile cf, FieldEntry entry, String name, boolean isStatic) throws IOException
    {
        int access = isStatic
                ? new AccessBuilder().setPublic().setStatic().build()
                : new AccessBuilder().setPublic().build();

        MethodEntry method = cf.createNewMethod(access, name, entry.getDesc());

        Bytecode bytecode = new Bytecode(method);
        int fieldRefIndex = cf.getConstPool().getIndexOf(bytecode.getConstPool().findOrAddField(entry.getOwnerName(), entry.getName(), entry.getDesc()));

        if (!isStatic)
        {
            bytecode.addALoad(0);
            bytecode.addGetField(fieldRefIndex);
        }
        else
        {
            bytecode.addGetStatic(fieldRefIndex);
        }

        bytecode.addReturn(ReturnType.fromDescriptor(entry.getDesc()));
        bytecode.finalizeBytecode();
        return method;
    }

    /**
     * Generates a setter named {@code set<Field>} for a field.
     *
     * @param cf class to receive the setter
     * @param entry field to write
     * @param isStatic true to emit a static setter writing a static field
     * @return the generated setter
     * @throws IOException if the method body cannot be finalized
     */
    public static MethodEntry generateSetter(ClassFile cf, FieldEntry entry, boolean isStatic) throws IOException
    {
        String name = "set" + entry.getName().substring(0, 1).toUpperCase() + entry.getName().substring(1);
        return generateSetter(cf, entry, name, isStatic);
    }

    /**
     * Generates a setter under a caller-chosen name for a field.
     *
     * @param cf class to receive the setter
     * @param entry field to write
     * @param name name of the generated method
     * @param isStatic true to emit a static setter writing a static field
     * @return the generated setter
     * @throws IOException if the method body cannot be finalized
     */
    public static MethodEntry generateSetter(ClassFile cf, FieldEntry entry, String name, boolean isStatic) throws IOException
    {
        int access = isStatic
                ? new AccessBuilder().setPublic().setStatic().build()
                : new AccessBuilder().setPublic().build();

        MethodEntry method = cf.createNewMethod(access, name, "V", entry.getDesc());

        Bytecode bytecode = new Bytecode(method);
        int fieldRefIndex = cf.getConstPool().getIndexOf(bytecode.getConstPool().findOrAddField(entry.getOwnerName(), entry.getName(), entry.getDesc()));

        if (!isStatic)
        {
            bytecode.addALoad(0);
            bytecode.addLoad(1, entry.getDesc());
            bytecode.addPutField(fieldRefIndex);
        }
        else
        {
            bytecode.addLoad(0, entry.getDesc());
            bytecode.addPutStatic(fieldRefIndex);
        }

        bytecode.addReturn(ReturnType.RETURN);
        bytecode.finalizeBytecode();
        return method;
    }

    /**
     * Prepends a store of a constant to a field at the top of the class initializer for a static
     * field, or the no-argument constructor for an instance field, creating that method if absent.
     *
     * @param cf class owning the field
     * @param field field to initialize
     * @param value Integer, Long, Float, Double, or String constant to store
     * @throws IOException if the method body cannot be finalized
     * @throws IllegalArgumentException if the value is not one of the supported constant types
     */
    public static void setFieldInitialValue(ClassFile cf, FieldEntry field, Object value) throws IOException
    {
        boolean isStatic = Modifiers.isStatic(field.getAccess());

        MethodEntry method = isStatic
                ? cf.getMethods().stream()
                .filter(m -> m.getName().equals("<clinit>") && m.getDesc().equals("()V"))
                .findFirst()
                .orElseGet(() -> addDefaultClassInitializer(cf))
                : cf.getMethods().stream()
                .filter(m -> m.getName().equals("<init>") && m.getDesc().equals("()V"))
                .findFirst()
                .orElseGet(() -> addDefaultConstructor(cf));

        CodeAttribute codeAttr = method.getCodeAttribute();
        if (codeAttr == null)
        {
            codeAttr = newCodeAttribute(cf, method, 10, isStatic ? 0 : 1);
            method.getAttributes().add(codeAttr);
            codeAttr.setCode(new byte[0]);
        }

        Bytecode bytecode = new Bytecode(method);
        bytecode.setInsertBefore(true);

        if (!isStatic)
        {
            bytecode.addALoad(0);
        }

        if (value instanceof Integer)
        {
            bytecode.addIConst((int) value);
        }
        else if (value instanceof Long)
        {
            bytecode.addLConst((long) value);
        }
        else if (value instanceof Float)
        {
            bytecode.addIConst(Float.floatToRawIntBits((float) value));
        }
        else if (value instanceof Double)
        {
            bytecode.addLConst(Double.doubleToRawLongBits((double) value));
        }
        else if (value instanceof String)
        {
            Item<?> stringItem = cf.getConstPool().findOrAddString((String) value);
            bytecode.addIConst(cf.getConstPool().getIndexOf(stringItem));
        }
        else
        {
            throw new IllegalArgumentException("Unsupported value type: " + value.getClass().getName());
        }

        int fieldRefIndex = cf.getConstPool().getIndexOf(cf.getConstPool().findOrAddField(field.getOwnerName(), field.getName(), field.getDesc()));
        if (isStatic)
        {
            bytecode.addPutStatic(fieldRefIndex);
        }
        else
        {
            bytecode.addPutField(fieldRefIndex);
        }

        bytecode.finalizeBytecode();
    }

    /**
     * Computes StackMapTable frames for every method in a class that carries code.
     *
     * @param cf class to process
     * @return the number of methods that had frames computed
     */
    public static int computeFrames(ClassFile cf)
    {
        FrameGenerator generator = new FrameGenerator(cf.getConstPool());
        int count = 0;
        for (MethodEntry method : cf.getMethods())
        {
            if (method.getCodeAttribute() != null)
            {
                generator.updateStackMapTable(method);
                count++;
            }
        }
        Logger.info("Computed StackMapTable frames for " + count + " methods in " + cf.getClassName());
        return count;
    }

    /**
     * Computes StackMapTable frames for one method looked up by name and descriptor.
     *
     * @param cf class to search
     * @param methodName method name
     * @param descriptor method descriptor
     * @return true if the method was found and frames were computed
     */
    public static boolean computeFrames(ClassFile cf, String methodName, String descriptor)
    {
        for (MethodEntry method : cf.getMethods())
        {
            if (method.getName().equals(methodName) && method.getDesc().equals(descriptor))
            {
                if (method.getCodeAttribute() != null)
                {
                    new FrameGenerator(cf.getConstPool()).updateStackMapTable(method);
                    Logger.info("Computed StackMapTable frames for " + methodName + descriptor);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Computes StackMapTable frames for one method, doing nothing if it carries no code.
     *
     * @param cf class supplying the constant pool
     * @param method method to process
     */
    public static void computeFrames(ClassFile cf, MethodEntry method)
    {
        if (method.getCodeAttribute() != null)
        {
            new FrameGenerator(cf.getConstPool()).updateStackMapTable(method);
            Logger.info("Computed StackMapTable frames for " + method.getName() + method.getDesc());
        }
    }

    private static CodeAttribute newCodeAttribute(ClassFile cf, MethodEntry owner, int maxStack, int maxLocals)
    {
        CodeAttribute codeAttr = new CodeAttribute("Code", owner, cf.getConstPool().utf8Index("Code"), 0);
        codeAttr.setMaxStack(maxStack);
        codeAttr.setMaxLocals(maxLocals);
        return codeAttr;
    }

    private static void finalizeBody(Bytecode bytecode, String what)
    {
        try
        {
            bytecode.finalizeBytecode();
        }
        catch (IOException e)
        {
            Logger.error("Failed to finalize bytecode for " + what + ": " + e.getMessage());
        }
    }
}
