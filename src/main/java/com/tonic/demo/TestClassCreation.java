package com.tonic.demo;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
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

        int staticAccess = new AccessBuilder()
                .setPublic()
                .setStatic()
                .build();

        //Create a Static field
        classFile.createNewField(staticAccess, "testStaticIntField", "I", new ArrayList<>());

        //Create a Static method for our getter
        MethodEntry getter = classFile.createNewMethod(false, staticAccess, "demoStaticGetter", int.class);
        Bytecode getterBytecode = new Bytecode(getter);
        ConstPool constPool = getterBytecode.getConstPool();
        int fieldRefIndex = constPool.findOrAddField("com/tonic/ANewClass", "testStaticIntField", "I");
        getterBytecode.addGetStatic(fieldRefIndex);
        getterBytecode.addReturn(ReturnType.IRETURN);
        getterBytecode.finalizeBytecode();

        //Create a Static method for our setter
        MethodEntry setterMethod = classFile.createNewMethod(false, staticAccess, "demoStaticSetter", void.class, int.class);
        Bytecode setterBytecode = new Bytecode(setterMethod);
        ConstPool constPool2 = getterBytecode.getConstPool();
        int fieldRefIndex2 = constPool2.findOrAddField("com/tonic/ANewClass", "testStaticIntField", "I");
        setterBytecode.addILoad(0);
        setterBytecode.addPutStatic(fieldRefIndex2);
        setterBytecode.addReturn(ReturnType.RETURN);

        classFile.rebuild();

        System.out.println(classFile);

        int access2 = new AccessBuilder().setPublic().build();

        classFile.createNewField(access2, "testIntField", "I", new ArrayList<>());
        MethodEntry method2 = classFile.createNewMethod(false, access2, "demoGetter", int.class);
        Bytecode bytecode2 = new Bytecode(method2);
        bytecode2.addALoad(0);
        int fieldRefIndex3 = bytecode2.getConstPool().findOrAddField("com/tonic/ANewClass", "testIntField", "I");
        bytecode2.addGetField(fieldRefIndex3);
        bytecode2.addReturn(ReturnType.IRETURN);
        bytecode2.finalizeBytecode();

        classFile.rebuild();

        System.out.println(classFile);

        //Save the class file to disk
        //ClassFileUtil.saveClassFile(classFile.write(), "C:\\test\\new", "ANewClass");
    }
}