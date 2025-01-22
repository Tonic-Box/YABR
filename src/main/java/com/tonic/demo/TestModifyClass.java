package com.tonic.demo;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.Logger;
import com.tonic.utill.ReturnType;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class TestModifyClass
{
    public static void main(String[] args) throws IOException {
        Logger.setLog(false);
        ClassPool classPool = ClassPool.getDefault();
        try (InputStream is = TestClassCreation.class.getResourceAsStream("TestCase.class")) {
            if (is == null) {
                throw new IOException("Resource 'TestCase.class' not found.");
            }

            ClassFile classFile = classPool.loadClass(is);
            classFile.setClassName("com.tonic.TestCaseModified");

            int access = new AccessBuilder().setPublic().setStatic().build();

            classFile.createNewField(access, "testIntField", "I", new ArrayList<>());
            MethodEntry method = classFile.createNewMethod(false, access, "demoGetter", int.class);
            Bytecode bytecode = new Bytecode(method);

            ConstPool constPool = bytecode.getConstPool();
            int fieldRefIndex = constPool.findOrAddField("com/tonic/TestCase", "testIntField", "I");
            bytecode.addGetStatic(fieldRefIndex);
            bytecode.addReturn(ReturnType.IRETURN); // IRETURN opcode
            bytecode.finalizeBytecode();

            classFile.rebuild();
            System.out.println(classPool.get("com.tonic.TestCaseModified"));
        }
    }
}
