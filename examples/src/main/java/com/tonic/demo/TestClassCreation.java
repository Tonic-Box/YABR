package com.tonic.demo;
import com.tonic.analysis.ClassFactory;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.*;
import com.tonic.util.*;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Demo showing ClassFactory building a class with initialized fields and generated accessors.
 */
public class TestClassCreation
{
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
    /**
     * Creates a class with two fields plus accessors, rebuilds it, and prints the result.
     * @param args unused
     * @throws IOException if class generation fails
     */
    public static void main(String[] args) throws IOException
    {
        Logger.setLog(false);

        ClassPool classPool = ClassPool.getDefault();
        ClassFile classFile = ClassFactory.createClass(classPool, "com/tonic/ANewClass", classAccess);

        FieldEntry staticField = classFile.createNewField(staticAccessPrivate, "testStaticIntField", "I", new ArrayList<>());
        ClassFactory.setFieldInitialValue(classFile, staticField, 12);
        ClassFactory.generateGetter(classFile, staticField, true);
        ClassFactory.generateSetter(classFile, staticField, true);

        FieldEntry field = classFile.createNewField(accessPrivate, "testIntField", "I", new ArrayList<>());
        ClassFactory.setFieldInitialValue(classFile, field, 54);
        ClassFactory.generateGetter(classFile, field, false);
        ClassFactory.generateSetter(classFile, field, false);

        ClassFactory.computeFrames(classFile);

        //compile our changes in memory
        classFile.rebuild();

        System.out.println(classFile);

        //Save the class file to disk
        ClassFileUtil.saveClassFile(classFile.write(), "C:\\test\\new", "ANewClass");
    }
}