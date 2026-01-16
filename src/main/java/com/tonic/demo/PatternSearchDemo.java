package com.tonic.demo;

import com.tonic.analysis.pattern.*;
import com.tonic.analysis.ssa.ir.NewInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.FileInputStream;
import java.util.List;

/**
 * Demo for the Pattern Search API.
 */
public class PatternSearchDemo {

    public static void main(String[] args) throws Exception {
        ClassPool pool = ClassPool.getDefault();

        if (args.length > 0) {
            // Load user-specified class files
            for (String path : args) {
                try (FileInputStream fis = new FileInputStream(path)) {
                    pool.loadClass(fis);
                }
            }
        }

        System.out.println("Pattern Search API Demo");
        System.out.println("=".repeat(50));

        // Get String class for demo
        ClassFile stringClass = pool.get("java/lang/String");
        if (stringClass == null) {
            System.out.println("String class not found in pool.");
            return;
        }

        PatternSearch search = new PatternSearch(pool);

        // ===== Demo 1: Find System.out.println calls =====
        System.out.println("\n=== Finding System.out calls in String ===");
        search.inAllMethodsOf(stringClass).limit(10);

        List<SearchResult> printCalls = search.findMethodCalls("java/io/PrintStream", "println");
        System.out.println("Found " + printCalls.size() + " println calls");
        for (SearchResult result : printCalls) {
            System.out.println("  " + result);
        }

        // ===== Demo 2: Find all object allocations =====
        System.out.println("\n=== Finding object allocations in String ===");
        List<SearchResult> allocations = search.findAllocations();
        System.out.println("Found " + allocations.size() + " allocations");
        int shown = 0;
        for (SearchResult result : allocations) {
            if (shown++ >= 10) {
                System.out.println("  ... and " + (allocations.size() - 10) + " more");
                break;
            }
            System.out.println("  " + result);
        }

        // ===== Demo 3: Find StringBuilder usage =====
        System.out.println("\n=== Finding StringBuilder usage ===");
        List<SearchResult> sbAllocs = search.findAllocations("java/lang/StringBuilder");
        System.out.println("Found " + sbAllocs.size() + " StringBuilder allocations");
        for (SearchResult result : sbAllocs) {
            System.out.println("  " + result);
        }

        // ===== Demo 4: Find instanceof checks =====
        System.out.println("\n=== Finding instanceof checks ===");
        List<SearchResult> instanceOfs = search.findInstanceOfChecks();
        System.out.println("Found " + instanceOfs.size() + " instanceof checks");
        shown = 0;
        for (SearchResult result : instanceOfs) {
            if (shown++ >= 5) {
                System.out.println("  ... and " + (instanceOfs.size() - 5) + " more");
                break;
            }
            System.out.println("  " + result);
        }

        // ===== Demo 5: Find type casts =====
        System.out.println("\n=== Finding type casts ===");
        List<SearchResult> casts = search.findCasts();
        System.out.println("Found " + casts.size() + " casts");
        shown = 0;
        for (SearchResult result : casts) {
            if (shown++ >= 5) {
                System.out.println("  ... and " + (casts.size() - 5) + " more");
                break;
            }
            System.out.println("  " + result);
        }

        // ===== Demo 6: Find throw statements =====
        System.out.println("\n=== Finding throw statements ===");
        List<SearchResult> throws_ = search.findThrows();
        System.out.println("Found " + throws_.size() + " throw statements");
        shown = 0;
        for (SearchResult result : throws_) {
            if (shown++ >= 5) {
                System.out.println("  ... and " + (throws_.size() - 5) + " more");
                break;
            }
            System.out.println("  " + result);
        }

        // ===== Demo 7: Custom pattern - find all String.equals calls =====
        System.out.println("\n=== Finding String.equals calls (custom pattern) ===");
        List<SearchResult> equalsCall = search.findPattern(
            Patterns.methodCall("java/lang/String", "equals")
        );
        System.out.println("Found " + equalsCall.size() + " String.equals calls");
        for (SearchResult result : equalsCall) {
            System.out.println("  " + result);
        }

        // ===== Demo 8: Find static method calls =====
        System.out.println("\n=== Finding static method calls ===");
        List<SearchResult> staticCalls = search.findPattern(
            Patterns.staticMethodCall()
        );
        System.out.println("Found " + staticCalls.size() + " static method calls");
        shown = 0;
        for (SearchResult result : staticCalls) {
            if (shown++ >= 5) {
                System.out.println("  ... and " + (staticCalls.size() - 5) + " more");
                break;
            }
            System.out.println("  " + result);
        }

        // ===== Demo 9: Combined pattern - new Exception allocations =====
        System.out.println("\n=== Finding Exception allocations ===");
        List<SearchResult> exceptionAllocs = search.findPattern(
            Patterns.and(
                Patterns.anyNew(),
                (instr, method, sourceMethod, classFile) -> {
                    if (!(instr instanceof NewInstruction)) return false;
                    String className = ((NewInstruction) instr).getClassName();
                    return className != null && className.contains("Exception");
                }
            )
        );
        System.out.println("Found " + exceptionAllocs.size() + " Exception allocations");
        for (SearchResult result : exceptionAllocs) {
            System.out.println("  " + result);
        }

        System.out.println("\n=== Demo Complete ===");
    }
}
