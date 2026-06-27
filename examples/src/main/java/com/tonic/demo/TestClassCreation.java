package com.tonic.demo;
import com.tonic.analysis.ClassFactory;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.*;
import com.tonic.util.*;
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
        ClassFile classFile = ClassFactory.createClass(classPool, "com/tonic/ANewClass", classAccess);

        //Create a Static field with setter/getter
        FieldEntry staticField = classFile.createNewField(staticAccessPrivate, "testStaticIntField", "I", new ArrayList<>());
        ClassFactory.setFieldInitialValue(classFile, staticField, 12);
        ClassFactory.generateGetter(classFile, staticField, true);
        ClassFactory.generateSetter(classFile, staticField, true);

        //Create a field with setter/getter
        FieldEntry field = classFile.createNewField(accessPrivate, "testIntField", "I", new ArrayList<>());
        ClassFactory.setFieldInitialValue(classFile, field, 54);
        ClassFactory.generateGetter(classFile, field, false);
        ClassFactory.generateSetter(classFile, field, false);

        ClassFactory.computeFrames(classFile);

        //compile our changes in memory
        classFile.rebuild();

        //Print the class file
        System.out.println(classFile);

        //Save the class file to disk
        ClassFileUtil.saveClassFile(classFile.write(), "C:\\test\\new", "ANewClass");
    }
}