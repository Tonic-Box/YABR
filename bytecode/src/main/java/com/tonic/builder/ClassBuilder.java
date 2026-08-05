package com.tonic.builder;

import com.tonic.analysis.ClassFactory;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.type.AccessFlags;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fluent builder that assembles a {@link ClassFile} from scratch - version, access, hierarchy,
 * fields, methods, annotations, and bootstrap methods.
 */
public class ClassBuilder implements AccessFlags
{

    private final String className;
    private int majorVersion = AccessFlags.V11;
    private int minorVersion = 0;
    private int accessFlags = ACC_PUBLIC | ACC_SUPER;
    private String superClassName = "java/lang/Object";
    private final List<String> interfaces = new ArrayList<>();
    private final List<FieldBuilder> fields = new ArrayList<>();
    private final List<MethodBuilder> methods = new ArrayList<>();
    private final List<AnnotationBuilder<ClassBuilder>> annotations = new ArrayList<>();
    private final List<BootstrapMethodDef> bootstrapMethods = new ArrayList<>();

    private ClassFile classFile;
    private ConstPool constPool;

    private ClassBuilder(String className)
    {
        this.className = className;
    }

    /**
     * Starts a new class with public access, {@code java/lang/Object} superclass, and Java 11 version.
     * @param className the internal name of the class to build
     * @return a new builder
     */
    public static ClassBuilder create(String className)
    {
        return new ClassBuilder(className);
    }

    /**
     * Sets the class-file version.
     * @param major the major version
     * @param minor the minor version
     * @return this builder
     */
    public ClassBuilder version(int major, int minor)
    {
        this.majorVersion = major;
        this.minorVersion = minor;
        return this;
    }

    /**
     * Replaces the access flags with the given flags ORed onto ACC_SUPER.
     * @param flags the access flags to combine
     * @return this builder
     */
    public ClassBuilder access(int... flags)
    {
        this.accessFlags = ACC_SUPER;
        for (int flag : flags)
        {
            this.accessFlags |= flag;
        }
        return this;
    }

    /**
     * Sets the superclass.
     * @param superName the superclass internal name
     * @return this builder
     */
    public ClassBuilder superClass(String superName)
    {
        this.superClassName = superName;
        return this;
    }

    /**
     * Adds implemented interfaces.
     * @param interfaceNames the interfaces' internal names
     * @return this builder
     */
    public ClassBuilder interfaces(String... interfaceNames)
    {
        Collections.addAll(this.interfaces, interfaceNames);
        return this;
    }

    /**
     * Opens a field on this class; call {@link FieldBuilder#end()} to return here.
     * @param access the field's access flags
     * @param name the field name
     * @param descriptor the field descriptor
     * @return the nested field builder
     */
    public FieldBuilder addField(int access, String name, String descriptor)
    {
        FieldBuilder fb = new FieldBuilder(this, access, name, descriptor);
        fields.add(fb);
        return fb;
    }

    /**
     * Opens a method on this class; call {@link MethodBuilder#end()} to return here.
     * @param access the method's access flags
     * @param name the method name
     * @param descriptor the method descriptor
     * @return the nested method builder
     */
    public MethodBuilder addMethod(int access, String name, String descriptor)
    {
        MethodBuilder mb = new MethodBuilder(this, access, name, descriptor);
        methods.add(mb);
        return mb;
    }

    /**
     * Opens an annotation on this class; call {@link AnnotationBuilder#end()} to return here.
     * @param type the annotation's type descriptor
     * @return the nested annotation builder
     */
    public AnnotationBuilder<ClassBuilder> annotate(String type)
    {
        AnnotationBuilder<ClassBuilder> annotation = AnnotationBuilder.forParent(this, type);
        annotations.add(annotation);
        return annotation;
    }

    /**
     * Assembles the accumulated specification into a {@link ClassFile}.
     * @return the built class file
     * @throws RuntimeException if assembling the class fails
     */
    public ClassFile build()
    {
        try
        {
            ClassPool classPool = new ClassPool(true);
            classFile = ClassFactory.createClass(classPool, className, accessFlags);
            constPool = classFile.getConstPool();

            classFile.setMajorVersion(majorVersion);
            classFile.setMinorVersion(minorVersion);

            if (!superClassName.equals("java/lang/Object"))
            {
                classFile.setSuperClassName(superClassName);
            }

            for (String iface : interfaces)
            {
                classFile.addInterface(iface);
            }

            for (FieldBuilder fb : fields)
            {
                fb.buildField(classFile, constPool);
            }

            for (MethodBuilder mb : methods)
            {
                mb.buildMethod(classFile, constPool);
            }

            for (AnnotationBuilder<ClassBuilder> annotation : annotations)
            {
                annotation.attachTo(classFile, constPool);
            }

            if (!bootstrapMethods.isEmpty())
            {
                BootstrapMethodsAttribute bsmAttr = new BootstrapMethodsAttribute(constPool);
                for (BootstrapMethodDef def : bootstrapMethods)
                {
                    bsmAttr.addBootstrapMethod(def.methodHandleIndex, def.arguments);
                }
                classFile.getClassAttributes().add(bsmAttr);
            }

            return classFile;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to build class", e);
        }
    }

    /**
     * Serializes the class to class-file bytes, building it first if needed.
     * @return the serialized class-file bytes
     * @throws RuntimeException if building or serializing the class fails
     */
    public byte[] toByteArray()
    {
        if (classFile == null)
        {
            build();
        }

        try
        {
            return classFile.write();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to serialize class", e);
        }
    }

    int addBootstrapMethod(int methodHandleIndex, List<Integer> arguments)
    {
        int index = bootstrapMethods.size();
        bootstrapMethods.add(new BootstrapMethodDef(methodHandleIndex, arguments));
        return index;
    }

    ConstPool getConstPool()
    {
        return constPool;
    }

    private static class BootstrapMethodDef
    {
        final int methodHandleIndex;
        final List<Integer> arguments;

        BootstrapMethodDef(int methodHandleIndex, List<Integer> arguments)
        {
            this.methodHandleIndex = methodHandleIndex;
            this.arguments = arguments;
        }
    }
}
