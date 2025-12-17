package com.tonic.demo;

import com.tonic.analysis.callgraph.*;
import com.tonic.analysis.common.MethodReference;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.FileInputStream;
import java.util.Set;

/**
 * Demo for the Call Graph API.
 */
public class CallGraphDemo {

    public static void main(String[] args) throws Exception {
        ClassPool pool = ClassPool.getDefault();

        // Load some classes - use YABR's own classes for testing
        if (args.length > 0) {
            // Load user-specified class file
            for (String path : args) {
                try (FileInputStream fis = new FileInputStream(path)) {
                    pool.loadClass(fis);
                }
            }
        } else {
            // Demo: Analyze YABR's own classes
            System.out.println("Usage: CallGraphDemo <classfile1> [classfile2] ...");
            System.out.println("\nRunning demo with synthetic test classes...\n");
            runSyntheticDemo(pool);
            return;
        }

        // Build call graph
        System.out.println("Building call graph...");
        CallGraph cg = CallGraph.build(pool);
        System.out.println(cg);
        System.out.println();

        // Show all methods
        System.out.println("=== Methods in ClassPool ===");
        for (CallGraphNode node : cg.getPoolNodes()) {
            System.out.println("  " + node.getReference());
            Set<MethodReference> callers = node.getCallers();
            if (!callers.isEmpty()) {
                System.out.println("    Called by: " + callers.size() + " method(s)");
            }
            Set<MethodReference> callees = node.getCallees();
            if (!callees.isEmpty()) {
                System.out.println("    Calls: " + callees.size() + " method(s)");
            }
        }
        System.out.println();

        // Find methods with no callers
        System.out.println("=== Methods with no callers (potential dead code) ===");
        Set<MethodReference> noCaller = cg.findMethodsWithNoCallers();
        for (MethodReference ref : noCaller) {
            System.out.println("  " + ref);
        }
    }

    private static void runSyntheticDemo(ClassPool pool) throws Exception {
        // Create a simple test class with methods that call each other
        ClassFile cf = pool.createNewClass("com/test/Demo", 0x21);

        // Add main method
        cf.createNewMethod(0x09, "main", "([Ljava/lang/String;)V");

        // Add helper methods
        cf.createNewMethod(0x01, "helperA", "()V");
        cf.createNewMethod(0x01, "helperB", "()V");
        cf.createNewMethod(0x02, "unusedPrivate", "()V"); // private, should be dead

        System.out.println("Created synthetic test class: " + cf.getClassName());
        System.out.println("Methods: main, helperA, helperB, unusedPrivate");
        System.out.println();

        // Build call graph
        CallGraph cg = CallGraph.build(pool);
        System.out.println(cg);
        System.out.println();

        // Show nodes
        System.out.println("=== Call Graph Nodes ===");
        for (CallGraphNode node : cg.getPoolNodes()) {
            System.out.println("  " + node);
        }
        System.out.println();

        // Since methods have no code, they won't have callees
        System.out.println("=== Methods with no callers ===");
        Set<MethodReference> noCaller = cg.findMethodsWithNoCallers();
        for (MethodReference ref : noCaller) {
            System.out.println("  " + ref);
        }

        System.out.println();
        System.out.println("Note: Methods have no bytecode, so no call edges are created.");
        System.out.println("Use with real .class files for full analysis.");
    }
}
