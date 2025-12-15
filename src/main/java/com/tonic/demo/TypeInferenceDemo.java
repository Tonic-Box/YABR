package com.tonic.demo;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.typeinference.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;
import java.util.Map;
import java.util.Set;

/**
 * Demo for the Type Inference API.
 */
public class TypeInferenceDemo {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            // Load user-specified class files
            ClassPool pool = ClassPool.getDefault();
            for (String path : args) {
                try (FileInputStream fis = new FileInputStream(path)) {
                    ClassFile cf = pool.loadClass(fis);
                    System.out.println("=== Class: " + cf.getClassName() + " ===");
                    analyzeClassMethods(cf);
                }
            }
        } else {
            System.out.println("Usage: TypeInferenceDemo <classfile1> [classfile2] ...");
            System.out.println("\nRunning demo with JDK class...\n");
            runJdkDemo();
        }
    }

    private static void runJdkDemo() {
        // Analyze a method from the JDK
        ClassPool pool = ClassPool.getDefault();

        // Find String class
        ClassFile stringClass = pool.get("java/lang/String");

        if (stringClass == null) {
            System.out.println("String class not found in ClassPool.");
            // Try Integer instead
            stringClass = pool.get("java/lang/Integer");
        }

        if (stringClass == null) {
            System.out.println("No suitable class found for demo.");
            return;
        }

        System.out.println("Analyzing: " + stringClass.getClassName());
        analyzeClassMethods(stringClass);
    }

    private static void analyzeClassMethods(ClassFile cf) {
        int methodCount = 0;
        int maxMethods = 5; // Limit for demo

        for (MethodEntry method : cf.getMethods()) {
            if (methodCount >= maxMethods) {
                System.out.println("\n... and " + (cf.getMethods().size() - maxMethods) + " more methods");
                break;
            }

            if (method.getCodeAttribute() == null) {
                continue; // Skip abstract/native methods
            }

            System.out.println("\n--- Method: " + method.getName() + method.getDesc() + " ---");

            try {
                // Build SSA IR
                SSA ssa = new SSA(cf.getConstPool());
                IRMethod irMethod = ssa.lift(method);

                if (irMethod == null) {
                    System.out.println("  Failed to build SSA IR");
                    continue;
                }

                // Run type inference
                TypeInferenceAnalyzer analyzer = new TypeInferenceAnalyzer(irMethod);
                analyzer.analyze();

                // Show results
                Map<SSAValue, TypeState> allStates = analyzer.getAllTypeStates();
                System.out.println("  Analyzed " + allStates.size() + " values");

                // Show definitely non-null values
                Set<SSAValue> nonNull = analyzer.getNonNullValues();
                System.out.println("  Definitely non-null: " + nonNull.size());
                int shown = 0;
                for (SSAValue v : nonNull) {
                    if (shown++ >= 5) {
                        System.out.println("    ... and " + (nonNull.size() - 5) + " more");
                        break;
                    }
                    System.out.println("    " + v.getName() + ": " + analyzer.getInferredType(v));
                }

                // Show nullable values
                int nullableCount = 0;
                for (Map.Entry<SSAValue, TypeState> e : allStates.entrySet()) {
                    if (e.getValue().getNullability() == Nullability.UNKNOWN &&
                        e.getValue().getAnyType() != null &&
                        e.getValue().getAnyType().isReference()) {
                        nullableCount++;
                    }
                }
                System.out.println("  Nullable (unknown): " + nullableCount);

                // Show definitely null values
                Set<SSAValue> nullValues = analyzer.getNullValues();
                if (!nullValues.isEmpty()) {
                    System.out.println("  Definitely null: " + nullValues.size());
                }

                methodCount++;
            } catch (Exception e) {
                System.out.println("  Error analyzing method: " + e.getMessage());
            }
        }
    }
}
