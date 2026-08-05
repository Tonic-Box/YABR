package com.tonic.demo;
import com.tonic.analysis.ClassFactory;

import com.tonic.analysis.callgraph.*;
import com.tonic.analysis.common.MethodReference;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.FileInputStream;
import java.util.Set;

/**
 * Demo showing call graph construction and queries over loaded classes.
 */
public class CallGraphDemo
{

    /**
     * Builds and prints a call graph for the given class files, or a synthetic demo when none are given.
     * @param args paths of class files to load
     * @throws Exception if a class file cannot be read or parsed
     */
    public static void main(String[] args) throws Exception
    {
        ClassPool pool = ClassPool.getDefault();

        // Load some classes - use YABR's own classes for testing
        if (args.length > 0)
        {
            for (String path : args)
            {
                try (FileInputStream fis = new FileInputStream(path))
                {
                    pool.loadClass(fis);
                }
            }
        }
        else
        {
            // Demo: Analyze YABR's own classes
            System.out.println("Usage: CallGraphDemo <classfile1> [classfile2] ...");
            System.out.println("\nRunning demo with synthetic test classes...\n");
            runSyntheticDemo(pool);
            return;
        }

        System.out.println("Building call graph...");
        CallGraph cg = CallGraph.build(pool);
        System.out.println(cg);
        System.out.println();

        // Show all methods
        System.out.println("=== Methods in ClassPool ===");
        for (CallGraphNode node : cg.getPoolNodes())
        {
            System.out.println("  " + node.getReference());
            Set<MethodReference> callers = node.getCallers();
            if (!callers.isEmpty())
            {
                System.out.println("    Called by: " + callers.size() + " method(s)");
            }
            Set<MethodReference> callees = node.getCallees();
            if (!callees.isEmpty())
            {
                System.out.println("    Calls: " + callees.size() + " method(s)");
            }
        }
        System.out.println();

        // Find methods with no callers
        System.out.println("=== Methods with no callers (potential dead code) ===");
        Set<MethodReference> noCaller = cg.findMethodsWithNoCallers();
        for (MethodReference ref : noCaller)
        {
            System.out.println("  " + ref);
        }
    }

    private static void runSyntheticDemo(ClassPool pool) throws Exception
    {
        // Create a simple test class with methods that call each other
        ClassFile cf = ClassFactory.createClass(pool, "com/test/Demo", 0x21);

        cf.createNewMethod(0x09, "main", "([Ljava/lang/String;)V");

        cf.createNewMethod(0x01, "helperA", "()V");
        cf.createNewMethod(0x01, "helperB", "()V");
        cf.createNewMethod(0x02, "unusedPrivate", "()V"); // private, should be dead

        System.out.println("Created synthetic test class: " + cf.getClassName());
        System.out.println("Methods: main, helperA, helperB, unusedPrivate");
        System.out.println();

        CallGraph cg = CallGraph.build(pool);
        System.out.println(cg);
        System.out.println();

        // Show nodes
        System.out.println("=== Call Graph Nodes ===");
        for (CallGraphNode node : cg.getPoolNodes())
        {
            System.out.println("  " + node);
        }
        System.out.println();

        // Since methods have no code, they won't have callees
        System.out.println("=== Methods with no callers ===");
        Set<MethodReference> noCaller = cg.findMethodsWithNoCallers();
        for (MethodReference ref : noCaller)
        {
            System.out.println("  " + ref);
        }

        System.out.println();
        System.out.println("Note: Methods have no bytecode, so no call edges are created.");
        System.out.println("Use with real .class files for full analysis.");
    }
}
