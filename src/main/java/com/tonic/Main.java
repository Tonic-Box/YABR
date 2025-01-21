package com.tonic;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws IOException {
        // Disable logger output (optional)
        Logger.setLog(false);

        // Acquire our default ClassPool instance
        ClassPool classPool = ClassPool.getDefault();

        // Use a try-with-resources to ensure the InputStream is properly closed
        try (InputStream is = Main.class.getResourceAsStream("TestCase.class")) {
            if (is == null) {
                throw new IOException("Resource 'TestCase.class' not found.");
            }
            ClassFile classFile = classPool.loadClass(is);
            classFile.createNewMethod(0x0001, "testVoid", void.class);
            classFile.createNewMethod(0x0001, "testInt", int.class);
            classFile.createNewMethod(0x0001, "testObjWithParams", Object.class, int.class, String.class);

            classFile.createNewField(0x0001, "testIntField", "I", new ArrayList<>());
            MethodEntry method = classFile.createNewMethod(false, 0x0001, "demoGetter", int.class);
            Bytecode bytecode = new Bytecode(method);

            ConstPool constPool = bytecode.getConstPool();
            int fieldRefIndex = constPool.findOrAddField("com/tonic/TestCase", "testIntField", "I");
            bytecode.addALoad(0);
            bytecode.addGetField(fieldRefIndex);
            bytecode.addReturn(0xAC); // IRETURN opcode
            bytecode.finalizeBytecode();

            classFile.rebuild();
            System.out.println(classPool.get("com.tonic.TestCase"));
        }
    }
}