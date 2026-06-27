package com.tonic.demo;

import com.tonic.analysis.instrumentation.Instrumenter;
import com.tonic.parser.ClassFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Demonstrates the Instrumentation API usage.
 *
 * <p>The Instrumentation API provides a fluent interface for adding hooks
 * to bytecode at various instrumentation points:</p>
 *
 * <ul>
 *   <li>Method entry/exit</li>
 *   <li>Field read/write</li>
 *   <li>Array load/store</li>
 *   <li>Method call interception</li>
 *   <li>Exception handlers</li>
 * </ul>
 */
public class InstrumentationDemo {

    public static void main(String[] args) throws IOException {
        System.out.println("=== YABR Instrumentation API Demo ===\n");

        // Load a sample class
        ClassFile targetClass = loadSampleClass();
        if (targetClass == null) {
            System.out.println("Could not load sample class. Using inline example.");
            demonstrateAPIUsage();
            return;
        }

        demonstrateMethodHooks(targetClass);
        demonstrateFieldHooks(targetClass);
        demonstrateArrayHooks(targetClass);
        demonstrateMethodCallHooks(targetClass);
    }

    private static ClassFile loadSampleClass() throws IOException {
        // Try to load this demo class itself
        String className = InstrumentationDemo.class.getName().replace('.', '/') + ".class";
        try (InputStream is = InstrumentationDemo.class.getClassLoader().getResourceAsStream(className)) {
            if (is != null) {
                return new ClassFile(is);
            }
        }
        return null;
    }

    private static void demonstrateMethodHooks(ClassFile targetClass) {
        System.out.println("--- Method Entry/Exit Hooks ---");

        Instrumenter.InstrumentationReport report = Instrumenter.forClass(targetClass)
                // Hook all method entries
                .onMethodEntry()
                    .callStatic("com/example/Profiler", "onMethodEntry",
                            "(Ljava/lang/String;Ljava/lang/String;)V")
                    .withClassName()
                    .withMethodName()
                    .register()

                // Hook all method exits
                .onMethodExit()
                    .callStatic("com/example/Profiler", "onMethodExit",
                            "(Ljava/lang/String;Ljava/lang/Object;)V")
                    .withMethodName()
                    .withReturnValue()
                    .register()

                // Skip constructors and static initializers
                .skipConstructors(true)
                .skipStaticInitializers(true)

                .applyWithReport();

        System.out.println("Method hooks result: " + report);
        System.out.println();
    }

    private static void demonstrateFieldHooks(ClassFile targetClass) {
        System.out.println("--- Field Write Hooks ---");

        Instrumenter.InstrumentationReport report = Instrumenter.forClass(targetClass)
                // Hook all field writes
                .onFieldWrite()
                    .callStatic("com/example/FieldTracker", "onFieldWrite",
                            "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V")
                    .withOwner()
                    .withFieldName()
                    .withNewValue()
                    .register()

                .applyWithReport();

        System.out.println("Field write hooks result: " + report);
        System.out.println();
    }

    private static void demonstrateArrayHooks(ClassFile targetClass) {
        System.out.println("--- Array Store Hooks ---");

        Instrumenter.InstrumentationReport report = Instrumenter.forClass(targetClass)
                // Hook array stores
                .onArrayStore()
                    .callStatic("com/example/ArrayMonitor", "onArrayStore",
                            "(Ljava/lang/Object;ILjava/lang/Object;)V")
                    .withAll()  // array, index, value
                    .register()

                .applyWithReport();

        System.out.println("Array store hooks result: " + report);
        System.out.println();
    }

    private static void demonstrateMethodCallHooks(ClassFile targetClass) {
        System.out.println("--- Method Call Interception ---");

        Instrumenter.InstrumentationReport report = Instrumenter.forClass(targetClass)
                // Hook calls to System.out.println
                .onMethodCall()
                    .targeting("java/io/PrintStream", "println")
                    .before()
                    .callStatic("com/example/IOHooks", "beforePrintln",
                            "(Ljava/lang/Object;)V")
                    .withReceiver()
                    .register()

                .applyWithReport();

        System.out.println("Method call hooks result: " + report);
        System.out.println();
    }

    private static void demonstrateAPIUsage() {
        System.out.println("--- Instrumentation API Usage Examples ---\n");

        System.out.println("1. Method Entry Hook with Parameters:");
        System.out.println("   Instrumenter.forClass(classFile)");
        System.out.println("       .onMethodEntry()");
        System.out.println("           .inPackage(\"com/example/service/\")");
        System.out.println("           .callStatic(\"com/example/Hooks\", \"onEntry\", \"(...)\")");
        System.out.println("           .withMethodName()");
        System.out.println("           .withAllParameters()");
        System.out.println("           .register()");
        System.out.println("       .apply();");
        System.out.println();

        System.out.println("2. Field Write Hook for Tracking Assignments:");
        System.out.println("   Instrumenter.forClass(classFile)");
        System.out.println("       .onFieldWrite()");
        System.out.println("           .forField(\"balance\")");
        System.out.println("           .callStatic(\"com/example/AuditLog\", \"onBalanceChange\", \"(...)\")");
        System.out.println("           .withOwner()");
        System.out.println("           .withOldValue()");
        System.out.println("           .withNewValue()");
        System.out.println("           .register()");
        System.out.println("       .apply();");
        System.out.println();

        System.out.println("3. Array Store Hook for Security Monitoring:");
        System.out.println("   Instrumenter.forClass(classFile)");
        System.out.println("       .onArrayStore()");
        System.out.println("           .inPackage(\"com/example/sensitive/\")");
        System.out.println("           .forObjectArrays()");
        System.out.println("           .callStatic(\"com/example/Security\", \"validateArrayStore\", \"(...)\")");
        System.out.println("           .withAll()");
        System.out.println("           .register()");
        System.out.println("       .apply();");
        System.out.println();

        System.out.println("4. Method Call Interception for Mocking:");
        System.out.println("   Instrumenter.forClass(classFile)");
        System.out.println("       .onMethodCall()");
        System.out.println("           .targeting(\"java/sql/Connection\", \"prepareStatement\")");
        System.out.println("           .before()");
        System.out.println("           .callStatic(\"com/test/MockDB\", \"interceptQuery\", \"(...)\")");
        System.out.println("           .withArguments()");
        System.out.println("           .register()");
        System.out.println("       .apply();");
        System.out.println();

        System.out.println("5. Combined Profiling Setup:");
        System.out.println("   int points = Instrumenter.forClasses(classFile1, classFile2)");
        System.out.println("       .onMethodEntry()");
        System.out.println("           .callStatic(\"Profiler\", \"enter\", \"(...)\")");
        System.out.println("           .withMethodName()");
        System.out.println("           .register()");
        System.out.println("       .onMethodExit()");
        System.out.println("           .callStatic(\"Profiler\", \"exit\", \"(...)\")");
        System.out.println("           .withMethodName()");
        System.out.println("           .register()");
        System.out.println("       .skipAbstract(true)");
        System.out.println("       .skipNative(true)");
        System.out.println("       .apply();");
    }

    // Sample fields and methods for instrumentation demo
    private int counter;
    private String name;
    private int[] values = new int[10];

    public void sampleMethod(String input) {
        this.name = input;
        this.counter++;
        values[0] = counter;
        System.out.println("Sample: " + name);
    }

    public int getCounter() {
        return counter;
    }
}
