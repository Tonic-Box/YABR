package com.tonic.demo;

import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.ir.blocks.Block;
import com.tonic.analysis.ir.blocks.Expression;
import com.tonic.analysis.visitor.AbstractBlockVisitor;
import com.tonic.analysis.visitor.AbstractClassVisitor;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.Logger;

import java.io.IOException;
import java.util.ArrayList;

public class TestBlocks
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
    }

    /**
     * This class visitor will visit each method in the class and pass it to the TestBytecodeVisitor
     */
    public static final class TestClassVisitor extends AbstractClassVisitor
    {
        private final PrintBlockVisitor printBlockVisitor = new PrintBlockVisitor();
        @Override
        public void visitMethod(MethodEntry methodEntry) {
            super.visitMethod(methodEntry);
            try {
                System.out.println("Method: " + methodEntry.getName());
                printBlockVisitor.process(methodEntry);
                System.out.println();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void visitField(FieldEntry fieldEntry) {
            super.visitField(fieldEntry);
        }
    }

    public static class PrintBlockVisitor extends AbstractBlockVisitor
    {
        @Override
        public void visit(Expression expression) {
            System.out.println("\tExpression Block: " + expression);
            for(Instruction instruction : expression.getInstructions())
            {
                System.out.println("\t\tInstruction: " + instruction);
            }
        }

        @Override
        public void visit(Block other) {
            System.out.println("\tOther Block: " + other);
            for(Instruction instruction : other.getInstructions())
            {
                System.out.println("\t\tInstruction: " + instruction);
            }
        }
    }
}
