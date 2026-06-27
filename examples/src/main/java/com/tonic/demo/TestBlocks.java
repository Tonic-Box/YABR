package com.tonic.demo;
import com.tonic.analysis.ClassFactory;

import com.tonic.analysis.ssa.IRPrinter;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.visitor.AbstractBlockVisitor;
import com.tonic.parser.visitor.AbstractClassVisitor;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.util.AccessBuilder;
import com.tonic.util.Logger;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Demonstrates the SSA IR block visitor pattern.
 */
public class TestBlocks {

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
        ClassFile classFile = ClassFactory.createClass(classPool, "com/tonic/ANewClass", classAccess);

        FieldEntry staticField = classFile.createNewField(staticAccessPrivate, "testStaticIntField", "I", new ArrayList<>());
        ClassFactory.setFieldInitialValue(classFile, staticField, 12);
        ClassFactory.generateGetter(classFile, staticField, true);
        ClassFactory.generateSetter(classFile, staticField, true);

        FieldEntry field = classFile.createNewField(accessPrivate, "testIntField", "I", new ArrayList<>());
        ClassFactory.setFieldInitialValue(classFile, field, 54);
        ClassFactory.generateGetter(classFile, field, false);
        ClassFactory.generateSetter(classFile, field, false);

        classFile.rebuild();

        classFile.accept(new TestClassVisitor());
    }

    /**
     * Class visitor that visits each method and processes it with the SSA IR visitor.
     */
    public static final class TestClassVisitor extends AbstractClassVisitor {

        private final PrintBlockVisitor printBlockVisitor = new PrintBlockVisitor();

        @Override
        public void visitMethod(MethodEntry methodEntry) {
            super.visitMethod(methodEntry);
            try {
                System.out.println("Method: " + methodEntry.getName() + methodEntry.getDesc());
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

    /**
     * Visitor that prints SSA IR blocks and instructions.
     */
    public static class PrintBlockVisitor extends AbstractBlockVisitor {

        @Override
        public void visitBlock(IRBlock block) {
            System.out.println("\tBlock: " + block.getName());
            System.out.println("\t  Predecessors: " + block.getPredecessors().stream()
                    .map(IRBlock::getName).collect(java.util.stream.Collectors.toList()));
            System.out.println("\t  Successors: " + block.getSuccessors().stream()
                    .map(IRBlock::getName).collect(java.util.stream.Collectors.toList()));

            super.visitBlock(block);
        }

        @Override
        public void visitPhi(PhiInstruction phi) {
            System.out.println("\t\t[PHI] " + IRPrinter.format(phi));
        }

        @Override
        public void visitInstruction(IRInstruction instruction) {
            System.out.println("\t\t" + IRPrinter.format(instruction));
        }
    }
}
