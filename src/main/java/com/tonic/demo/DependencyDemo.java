package com.tonic.demo;

import com.tonic.analysis.dependency.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Demo for the Dependency Analysis API.
 */
public class DependencyDemo {

    public static void main(String[] args) throws Exception {
        ClassPool pool = ClassPool.getDefault();

        if (args.length > 0) {
            // Load user-specified class files
            for (String path : args) {
                try (FileInputStream fis = new FileInputStream(path)) {
                    pool.loadClass(fis);
                }
            }
        } else {
            System.out.println("Usage: DependencyDemo <classfile1> [classfile2] ...");
            System.out.println("\nRunning demo with synthetic test classes...\n");
            runSyntheticDemo(pool);
            return;
        }

        // Build dependency graph
        System.out.println("Building dependency graph...");
        DependencyAnalyzer deps = new DependencyAnalyzer(pool);
        System.out.println(deps);
        System.out.println();

        // Show dependencies for each class
        System.out.println("=== Class Dependencies ===");
        for (DependencyNode node : deps.getPoolNodes()) {
            System.out.println(node.getClassName() + ":");
            System.out.println("  Dependencies: " + node.getDependencyCount());
            System.out.println("  Dependents: " + node.getDependentCount());

            // Show top 5 dependencies
            int count = 0;
            for (String dep : node.getDependencies()) {
                if (count++ >= 5) {
                    System.out.println("    ... and " + (node.getDependencyCount() - 5) + " more");
                    break;
                }
                System.out.println("    -> " + dep);
            }
        }
        System.out.println();

        // Find circular dependencies
        System.out.println("=== Circular Dependencies ===");
        List<List<String>> cycles = deps.findCircularDependencies();
        if (cycles.isEmpty()) {
            System.out.println("  No circular dependencies found.");
        } else {
            for (List<String> cycle : cycles) {
                System.out.println("  Cycle: " + String.join(" -> ", cycle));
            }
        }
    }

    private static void runSyntheticDemo(ClassPool pool) throws Exception {
        // Create test classes with dependencies
        ClassFile classA = pool.createNewClass("com/test/ClassA", 0x21);
        ClassFile classB = pool.createNewClass("com/test/ClassB", 0x21);
        ClassFile classC = pool.createNewClass("com/test/ClassC", 0x21);

        // ClassA extends Object (default)
        // ClassB has ClassA as field
        classB.createNewField(0x01, "a", "Lcom/test/ClassA;", new ArrayList<>());
        // ClassC has ClassB as method parameter
        classC.createNewMethodWithDescriptor(0x01, "process", "(Lcom/test/ClassB;)V");

        System.out.println("Created test classes:");
        System.out.println("  ClassA (base class)");
        System.out.println("  ClassB (has field of type ClassA)");
        System.out.println("  ClassC (has method with ClassB parameter)");
        System.out.println();

        // Build dependency graph
        DependencyAnalyzer deps = new DependencyAnalyzer(pool);
        System.out.println(deps);
        System.out.println();

        // Show our test classes
        System.out.println("=== Test Class Dependencies ===");
        for (String className : new String[]{"com/test/ClassA", "com/test/ClassB", "com/test/ClassC"}) {
            DependencyNode node = deps.getNode(className);
            if (node != null) {
                System.out.println(className + ":");
                System.out.println("  Dependencies: " + node.getDependencies());
                System.out.println("  Dependents: " + node.getDependents());
            }
        }

        // Test transitive dependencies
        System.out.println("\n=== Transitive Dependencies ===");
        Set<String> transitive = deps.getTransitiveDependencies("com/test/ClassC");
        System.out.println("ClassC transitively depends on: " + transitive.size() + " classes");
        for (String dep : transitive) {
            if (dep.startsWith("com/test/")) {
                System.out.println("  -> " + dep);
            }
        }

        // Find circular dependencies
        System.out.println("\n=== Circular Dependencies ===");
        List<List<String>> cycles = deps.findCircularDependencies();
        if (cycles.isEmpty()) {
            System.out.println("  No circular dependencies found (as expected).");
        } else {
            for (List<String> cycle : cycles) {
                System.out.println("  Cycle: " + String.join(" -> ", cycle));
            }
        }
    }
}
