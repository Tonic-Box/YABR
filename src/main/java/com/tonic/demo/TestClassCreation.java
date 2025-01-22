package com.tonic.demo;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.*;
import com.tonic.utill.*;
import java.io.IOException;
import java.util.ArrayList;

public class TestClassCreation {
    private static final int classAccess = new AccessBuilder()
            .setPublic()
            .build();
    private static final int staticAccessPrivate = new AccessBuilder()
            .setPrivate()
            .setStatic()
            .build();

    private static final int accessPrivate = new AccessBuilder()
            .setPrivate()
            .build();
    public static void main(String[] args) throws IOException {
        Logger.setLog(false);

        //Create a new class
        ClassPool classPool = ClassPool.getDefault();
        ClassFile classFile = classPool.createNewClass("com/tonic/ANewClass", classAccess);

        //Create a Static field with setter/getter
        FieldEntry staticField = classFile.createNewField(staticAccessPrivate, "testStaticIntField", "I", new ArrayList<>());
        generateIntGetter(classFile, staticField, true);
        generateIntSetter(classFile, staticField, true);

        //Create a field with setter/getter
        FieldEntry field = classFile.createNewField(accessPrivate, "testIntField", "I", new ArrayList<>());
        generateIntGetter(classFile, field, false);
        generateIntSetter(classFile, field, false);

        //compile our changes in memory
        classFile.rebuild();

        //Print the class file
        System.out.println(classFile);

        //Save the class file to disk
        ClassFileUtil.saveClassFile(classFile.write(), "C:\\test\\new", "ANewClass");
    }

    /**
     * Generate a setter for an int field
     * @param classFile The class file to add the setter to
     * @param entry The field entry to generate the setter for
     * @param isStatic Whether the field is static or not
     * @throws IOException io exception
     */
    public static void generateIntSetter(ClassFile classFile, FieldEntry entry, boolean isStatic) throws IOException {
        String name = "set" + entry.getName().substring(0, 1).toUpperCase() + entry.getName().substring(1);
        int access = isStatic ? new AccessBuilder().setPublic().setStatic().build() : new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(false, access, name, void.class, int.class);
        Bytecode bytecode = new Bytecode(method);
        int fieldRefIndex = bytecode.getConstPool().findOrAddField(entry.getOwnerName(), entry.getName(), entry.getDesc());
        if(!isStatic)
        {
            bytecode.addALoad(0);
            bytecode.addILoad(1);
            bytecode.addPutField(fieldRefIndex);
        }
        else
        {
            bytecode.addILoad(0);
            bytecode.addPutStatic(fieldRefIndex);
        }
        bytecode.addReturn(ReturnType.RETURN);
        bytecode.finalizeBytecode();
    }

    /**
     * Generate a getter for an int field
     * @param classFile The class file to add the getter to
     * @param entry The field entry to generate the getter for
     * @param isStatic Whether the field is static or not
     * @throws IOException io exception
     */
    private static void generateIntGetter(ClassFile classFile, FieldEntry entry, boolean isStatic) throws IOException {
        String name = "get" + entry.getName().substring(0, 1).toUpperCase() + entry.getName().substring(1);
        int access = isStatic ? new AccessBuilder().setPublic().setStatic().build() : new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(false, access, name, int.class);
        Bytecode bytecode = new Bytecode(method);
        int fieldRefIndex = bytecode.getConstPool().findOrAddField(entry.getOwnerName(), entry.getName(), entry.getDesc());
        System.out.println("fieldRefIndex: " + fieldRefIndex);
        System.out.println(entry);
        if(!isStatic)
        {
            bytecode.addALoad(0);
            bytecode.addGetField(fieldRefIndex);
        }
        else
        {
            bytecode.addGetStatic(fieldRefIndex);
        }
        bytecode.addReturn(ReturnType.IRETURN);
        bytecode.finalizeBytecode();
    }
}