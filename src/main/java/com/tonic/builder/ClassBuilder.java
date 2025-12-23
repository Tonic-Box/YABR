package com.tonic.builder;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.type.AccessFlags;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClassBuilder implements AccessFlags {

    private final String className;
    private int majorVersion = AccessFlags.V11;
    private int minorVersion = 0;
    private int accessFlags = ACC_PUBLIC | ACC_SUPER;
    private String superClassName = "java/lang/Object";
    private final List<String> interfaces = new ArrayList<>();
    private final List<FieldBuilder> fields = new ArrayList<>();
    private final List<MethodBuilder> methods = new ArrayList<>();
    private final List<BootstrapMethodDef> bootstrapMethods = new ArrayList<>();

    private ClassPool classPool;
    private ClassFile classFile;
    private ConstPool constPool;

    private ClassBuilder(String className) {
        this.className = className;
    }

    public static ClassBuilder create(String className) {
        return new ClassBuilder(className);
    }

    public ClassBuilder version(int major, int minor) {
        this.majorVersion = major;
        this.minorVersion = minor;
        return this;
    }

    public ClassBuilder access(int... flags) {
        this.accessFlags = ACC_SUPER;
        for (int flag : flags) {
            this.accessFlags |= flag;
        }
        return this;
    }

    public ClassBuilder superClass(String superName) {
        this.superClassName = superName;
        return this;
    }

    public ClassBuilder interfaces(String... interfaceNames) {
        for (String iface : interfaceNames) {
            this.interfaces.add(iface);
        }
        return this;
    }

    public FieldBuilder addField(int access, String name, String descriptor) {
        FieldBuilder fb = new FieldBuilder(this, access, name, descriptor);
        fields.add(fb);
        return fb;
    }

    public MethodBuilder addMethod(int access, String name, String descriptor) {
        MethodBuilder mb = new MethodBuilder(this, access, name, descriptor);
        methods.add(mb);
        return mb;
    }

    public ClassFile build() {
        try {
            classPool = new ClassPool(true);
            classFile = classPool.createNewClass(className, accessFlags);
            constPool = classFile.getConstPool();

            classFile.setMajorVersion(majorVersion);
            classFile.setMinorVersion(minorVersion);

            if (!superClassName.equals("java/lang/Object")) {
                classFile.setSuperClassName(superClassName);
            }

            for (String iface : interfaces) {
                classFile.addInterface(iface);
            }

            for (FieldBuilder fb : fields) {
                fb.buildField(classFile, constPool);
            }

            for (MethodBuilder mb : methods) {
                mb.buildMethod(classFile, constPool);
            }

            if (!bootstrapMethods.isEmpty()) {
                BootstrapMethodsAttribute bsmAttr = new BootstrapMethodsAttribute(constPool);
                for (BootstrapMethodDef def : bootstrapMethods) {
                    bsmAttr.addBootstrapMethod(def.methodHandleIndex, def.arguments);
                }
                classFile.getClassAttributes().add(bsmAttr);
            }

            return classFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to build class", e);
        }
    }

    public byte[] toByteArray() {
        if (classFile == null) {
            build();
        }

        try {
            return classFile.write();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize class", e);
        }
    }

    int addBootstrapMethod(int methodHandleIndex, List<Integer> arguments) {
        int index = bootstrapMethods.size();
        bootstrapMethods.add(new BootstrapMethodDef(methodHandleIndex, arguments));
        return index;
    }

    ConstPool getConstPool() {
        return constPool;
    }

    private static class BootstrapMethodDef {
        final int methodHandleIndex;
        final List<Integer> arguments;

        BootstrapMethodDef(int methodHandleIndex, List<Integer> arguments) {
            this.methodHandleIndex = methodHandleIndex;
            this.arguments = arguments;
        }
    }
}
