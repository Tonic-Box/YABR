package com.tonic.demo;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.*;
import java.io.IOException;
import java.util.ArrayList;

public class TestClassCreation {
    public static void main(String[] args) throws IOException {
        Logger.setLog(false);
        ClassPool classPool = ClassPool.getDefault();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile classFile = classPool.createNewClass("com/tonic/ANewClass", classAccess);

        int access = new AccessBuilder().setPublic().setStatic().build();

        classFile.createNewField(access, "testStaticIntField", "I", new ArrayList<>());
        MethodEntry method = classFile.createNewMethod(false, access, "demoStaticGetter", int.class);
        Bytecode bytecode = new Bytecode(method);
        int fieldRefIndex = bytecode.getConstPool().findOrAddField("com/tonic/ANewClass", "testStaticIntField", "I");
        bytecode.addGetStatic(fieldRefIndex);
        bytecode.addReturn(ReturnType.IRETURN);
        bytecode.finalizeBytecode();

        MethodEntry method3 = classFile.createNewMethod(false, access, "demoStaticSetter", void.class, int.class);
        Bytecode bytecode3 = new Bytecode(method3);
        int fieldRefIndex3 = bytecode.getConstPool().findOrAddField("com/tonic/ANewClass", "testStaticIntField", "I");
        bytecode3.addILoad(0);
        bytecode3.addPutStatic(fieldRefIndex3);
        bytecode3.addReturn(ReturnType.RETURN_);

        int access2 = new AccessBuilder().setPublic().build();

        classFile.createNewField(access2, "testIntField", "I", new ArrayList<>());
        MethodEntry method2 = classFile.createNewMethod(false, access2, "demoGetter", int.class);
        Bytecode bytecode2 = new Bytecode(method2);
        bytecode2.addALoad(0);
        int fieldRefIndex2 = bytecode2.getConstPool().findOrAddField("com/tonic/ANewClass", "testIntField", "I");
        bytecode2.addGetField(fieldRefIndex2);
        bytecode2.addReturn(ReturnType.IRETURN);
        bytecode2.finalizeBytecode();

        classFile.rebuild();

        System.out.println(classFile);

        //Save the class file to disk
        //ClassFileUtil.saveClassFile(classFile.write(), "C:\\test\\new", "ANewClass");
    }
}