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
        classFile.setFieldInitialValue(staticField, 12);
        classFile.generateGetter(staticField, true);
        classFile.generateSetter(staticField, true);

        //Create a field with setter/getter
        FieldEntry field = classFile.createNewField(accessPrivate, "testIntField", "I", new ArrayList<>());
        classFile.setFieldInitialValue(field, 54);
        classFile.generateGetter(field, false);
        classFile.generateSetter(field, false);

        //compile our changes in memory
        classFile.rebuild();

        //Print the class file
        System.out.println(classFile);

        //Save the class file to disk
        ClassFileUtil.saveClassFile(classFile.write(), "C:\\test\\new", "ANewClass");
    }
}