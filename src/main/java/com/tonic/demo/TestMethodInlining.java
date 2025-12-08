package com.tonic.demo;

import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.ClassFileUtil;
import com.tonic.utill.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * Tests method inlining and dead method elimination.
 */
public class TestMethodInlining {

    public static void main(String[] args) throws IOException {
        Logger.setLog(false);
        ClassPool classPool = ClassPool.getDefault();

        try (InputStream is = TestMethodInlining.class.getResourceAsStream("InlineTestClass.class")) {
            if (is == null) {
                System.out.println("InlineTestClass.class not found - creating test manually");
                testWithSimpleClass(classPool);
                return;
            }

            ClassFile classFile = classPool.loadClass(is);
            testInlining(classFile);
        }
    }

    private static void testWithSimpleClass(ClassPool classPool) throws IOException {
        // Load SSAShowcase which has some methods we can test with
        try (InputStream is = TestMethodInlining.class.getResourceAsStream("SSAShowcase.class")) {
            if (is == null) {
                throw new IOException("SSAShowcase.class not found");
            }

            ClassFile classFile = classPool.loadClass(is);
            testInlining(classFile);
        }
    }

    private static void testInlining(ClassFile classFile) throws IOException {
        ConstPool constPool = classFile.getConstPool();

        System.out.println("=== Method Inlining Test ===");
        System.out.println("Class: " + classFile.getClassName());
        System.out.println();

        // Count methods before
        int methodCountBefore = classFile.getMethods().size();
        System.out.println("Methods before: " + methodCountBefore);
        for (MethodEntry method : classFile.getMethods()) {
            int codeSize = method.getCodeAttribute() != null ?
                method.getCodeAttribute().getCode().length : 0;
            System.out.println("  - " + method.getName() + method.getDesc() +
                " (" + codeSize + " bytes, access=0x" +
                Integer.toHexString(method.getAccess()) + ")");
        }
        System.out.println();

        // Create SSA with method inlining and dead method elimination
        SSA ssa = new SSA(constPool)
            .withMethodInlining()
            .withDeadMethodElimination();

        // Run class-level transforms
        System.out.println("Running class transforms...");
        boolean changed = ssa.runClassTransforms(classFile);
        System.out.println("Class modified: " + changed);
        System.out.println();

        // Count methods after
        int methodCountAfter = classFile.getMethods().size();
        System.out.println("Methods after: " + methodCountAfter);
        for (MethodEntry method : classFile.getMethods()) {
            int codeSize = method.getCodeAttribute() != null ?
                method.getCodeAttribute().getCode().length : 0;
            System.out.println("  - " + method.getName() + method.getDesc() +
                " (" + codeSize + " bytes)");
        }
        System.out.println();

        int eliminated = methodCountBefore - methodCountAfter;
        if (eliminated > 0) {
            System.out.println("Eliminated " + eliminated + " dead method(s)!");
        } else {
            System.out.println("No methods eliminated (may not have private helper methods)");
        }

        // Rebuild and save
        classFile.setClassName(classFile.getClassName() + "_Inlined");
        classFile.computeFrames();
        classFile.rebuild();

        String outputName = classFile.getClassName().replace('/', '_');
        ClassFileUtil.saveClassFile(classFile.write(), "C:\\test\\new", outputName);
        System.out.println("Output: " + outputName + ".class");
    }
}
