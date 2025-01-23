package com.tonic.demo;

import com.tonic.analysis.Bytecode;
import com.tonic.analysis.instruction.ReturnInstruction;
import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.AbstractClassVisitor;
import com.tonic.parser.*;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.ClassFileUtil;
import com.tonic.utill.Logger;
import java.io.IOException;
import java.util.ArrayList;

public class TestVisitor
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

    public static void main(String[] args) throws IOException {
        Logger.setLog(false);
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

        classFile.accept(new TestClassVisitor());

        //compile our changes in memory
        classFile.rebuild();

        System.out.println(classFile);

        //save the class file to disc
        //ClassFileUtil.saveClassFile(classFile.write(), "C:\\test\\new", "ANewClass");
    }

    /**
     * This class visitor will visit each method in the class and pass it to the TestBytecodeVisitor
     */
    public static final class TestClassVisitor extends AbstractClassVisitor
    {
        private final TestBytecodeVisitor bytecodeVisitor = new TestBytecodeVisitor();
        @Override
        public void visitMethod(MethodEntry methodEntry) {
            super.visitMethod(methodEntry);
            if(methodEntry.getName().contains("lambda$") || methodEntry.getName().startsWith("<"))
                return;

            try {
                bytecodeVisitor.process(methodEntry);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void visitField(FieldEntry fieldEntry) {
            super.visitField(fieldEntry);
        }
    }

    /**
     * This visitor will add a System.out.println call to each exit point of the method
     */
    public static final class TestBytecodeVisitor extends AbstractBytecodeVisitor
    {
        /**
         * we add a sout call to each exit point of the method just before the return instruction
         * @param instruction the return instruction
         */
        @Override
        public void visit(ReturnInstruction instruction) {
            super.visit(instruction);
            Bytecode bytecode = new Bytecode(codeWriter);
            bytecode.setInsertBefore(true);
            if(method.isVoidReturn())
                bytecode.setInsertBeforeOffset(instruction.getOffset());
            bytecode.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
            bytecode.addLdc("Hello, World!");
            bytecode.addInvokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
            try {
                bytecode.finalizeBytecode();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
