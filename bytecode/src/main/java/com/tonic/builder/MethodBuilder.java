package com.tonic.builder;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MethodBuilder {

    private final ClassBuilder parent;
    private final int access;
    private final String name;
    private final String descriptor;
    private CodeBuilder codeBuilder;
    private final List<String> exceptions = new ArrayList<>();
    private Integer maxStack;
    private Integer maxLocals;

    MethodBuilder(ClassBuilder parent, int access, String name, String descriptor) {
        this.parent = parent;
        this.access = access;
        this.name = name;
        this.descriptor = descriptor;
    }

    public CodeBuilder code() {
        if (codeBuilder == null) {
            codeBuilder = new CodeBuilder(this);
        }
        return codeBuilder;
    }

    public MethodBuilder exceptions(String... exceptionTypes) {
        for (String ex : exceptionTypes) {
            exceptions.add(ex);
        }
        return this;
    }

    public MethodBuilder maxStack(int maxStack) {
        this.maxStack = maxStack;
        return this;
    }

    public MethodBuilder maxLocals(int maxLocals) {
        this.maxLocals = maxLocals;
        return this;
    }

    public ClassBuilder end() {
        return parent;
    }

    ClassBuilder getParent() {
        return parent;
    }

    int getAccess() {
        return access;
    }

    String getName() {
        return name;
    }

    String getDescriptor() {
        return descriptor;
    }

    void buildMethod(ClassFile classFile, ConstPool constPool) throws IOException {
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, name, descriptor);

        if (codeBuilder != null) {
            codeBuilder.buildCode(method, constPool);
        }

        if (maxStack != null && method.getCodeAttribute() != null) {
            method.getCodeAttribute().setMaxStack(maxStack);
        }
        if (maxLocals != null && method.getCodeAttribute() != null) {
            method.getCodeAttribute().setMaxLocals(maxLocals);
        }
    }
}
