package com.tonic.demo;

import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.debug.*;
import com.tonic.analysis.execution.heap.*;
import com.tonic.analysis.execution.invoke.*;
import com.tonic.analysis.execution.resolve.*;
import com.tonic.analysis.execution.state.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;

/**
 * Demo for the Execution API.
 *
 * <p>Shows how to:
 * <ul>
 *   <li>Configure execution context</li>
 *   <li>Execute methods with concrete values</li>
 *   <li>Use the debug session for stepping</li>
 *   <li>Set breakpoints and inspect state</li>
 *   <li>Handle execution results</li>
 * </ul>
 */
public class ExecutionDemo {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            ClassPool pool = ClassPool.getDefault();
            for (String path : args) {
                try (FileInputStream fis = new FileInputStream(path)) {
                    pool.loadClass(fis);
                }
            }

            ClassFile cf = pool.getClasses().iterator().next();
            analyzeClass(cf, pool);
        } else {
            System.out.println("Usage: ExecutionDemo <classfile>");
            System.out.println("\nRunning demo with synthetic examples...\n");
            runSyntheticDemo();
        }
    }

    private static void analyzeClass(ClassFile cf, ClassPool pool) {
        System.out.println("=== Executing methods in: " + cf.getClassName() + " ===\n");

        HeapManager heap = new SimpleHeapManager();
        ClassResolver resolver = new ClassResolver(pool);

        NativeRegistry natives = new NativeRegistry();
        natives.registerDefaults();

        BytecodeContext ctx = new BytecodeContext.Builder()
            .heapManager(heap)
            .classResolver(resolver)
            .maxCallDepth(50)
            .maxInstructions(10000)
            .trackStatistics(true)
            .build();

        BytecodeEngine engine = new BytecodeEngine(ctx);

        for (MethodEntry method : cf.getMethods()) {
            if (method.getCodeAttribute() == null) {
                continue;
            }
            if (method.getName().equals("<clinit>")) {
                continue;
            }

            System.out.println("Method: " + method.getName() + method.getDesc());
            System.out.println("-".repeat(60));

            try {
                BytecodeResult result = engine.execute(method);
                displayResult(result);
            } catch (Exception e) {
                System.out.println("  Error: " + e.getMessage());
            }

            System.out.println();
            engine.reset();
        }
    }

    private static void displayResult(BytecodeResult result) {
        System.out.println("  Status: " + result.getStatus());
        System.out.println("  Instructions: " + result.getInstructionsExecuted());
        System.out.println("  Time: " + (result.getExecutionTimeNanos() / 1_000_000.0) + " ms");

        if (result.isSuccess()) {
            ConcreteValue returnValue = result.getReturnValue();
            if (returnValue != null && !returnValue.isNull()) {
                System.out.println("  Return: " + formatValue(returnValue));
            }
        } else if (result.hasException()) {
            ObjectInstance ex = result.getException();
            System.out.println("  Exception: " + ex.getClassName());
            if (!result.getStackTrace().isEmpty()) {
                System.out.println("  Stack trace:");
                for (String frame : result.getStackTrace()) {
                    System.out.println("    " + frame);
                }
            }
        }
    }

    private static String formatValue(ConcreteValue value) {
        switch (value.getTag()) {
            case INT: return "int(" + value.asInt() + ")";
            case LONG: return "long(" + value.asLong() + ")";
            case FLOAT: return "float(" + value.asFloat() + ")";
            case DOUBLE: return "double(" + value.asDouble() + ")";
            case REFERENCE:
                ObjectInstance ref = value.asReference();
                return ref != null ? ref.getClassName() + "@" + ref.getId() : "null";
            case NULL: return "null";
            default: return value.toString();
        }
    }

    private static void runSyntheticDemo() {
        System.out.println("=== Execution API Demo ===\n");

        demoConcreteValues();
        demoStackOperations();
        demoHeapOperations();
        demoDebugSession();
    }

    private static void demoConcreteValues() {
        System.out.println("--- Demo 1: Concrete Values ---\n");

        ConcreteValue intVal = ConcreteValue.intValue(42);
        ConcreteValue longVal = ConcreteValue.longValue(1234567890123L);
        ConcreteValue floatVal = ConcreteValue.floatValue(3.14f);
        ConcreteValue doubleVal = ConcreteValue.doubleValue(2.718281828);
        ConcreteValue nullVal = ConcreteValue.nullRef();

        System.out.println("Integer value: " + intVal.asInt() + " (category=" + intVal.getCategory() + ")");
        System.out.println("Long value: " + longVal.asLong() + " (category=" + longVal.getCategory() + ", wide=" + longVal.isWide() + ")");
        System.out.println("Float value: " + floatVal.asFloat());
        System.out.println("Double value: " + doubleVal.asDouble() + " (wide=" + doubleVal.isWide() + ")");
        System.out.println("Null reference: isNull=" + nullVal.isNull());
        System.out.println();
    }

    private static void demoStackOperations() {
        System.out.println("--- Demo 2: Stack Operations ---\n");

        ConcreteStack stack = new ConcreteStack(10);

        stack.pushInt(10);
        stack.pushInt(20);
        System.out.println("After pushing 10 and 20: depth=" + stack.depth());

        stack.dup();
        System.out.println("After dup(): depth=" + stack.depth() + ", top=" + stack.peek().asInt());

        stack.swap();
        System.out.println("After swap(): top=" + stack.peek().asInt());

        ConcreteValue a = stack.pop();
        ConcreteValue b = stack.pop();
        System.out.println("Popped: " + a.asInt() + ", " + b.asInt());

        stack.pushLong(9999999999L);
        System.out.println("After pushing long: depth=" + stack.depth());
        System.out.println();
    }

    private static void demoHeapOperations() {
        System.out.println("--- Demo 3: Heap Operations ---\n");

        HeapManager heap = new SimpleHeapManager();

        ObjectInstance obj1 = heap.newObject("java/util/ArrayList");
        ObjectInstance obj2 = heap.newObject("java/lang/StringBuilder");
        System.out.println("Created objects: " + obj1.getClassName() + "@" + obj1.getId() +
                          ", " + obj2.getClassName() + "@" + obj2.getId());

        obj1.setField("java/util/ArrayList", "size", "I", 5);
        Object size = obj1.getField("java/util/ArrayList", "size", "I");
        System.out.println("Set and get field 'size': " + size);

        ArrayInstance arr = heap.newArray("I", 5);
        arr.setInt(0, 100);
        arr.setInt(1, 200);
        System.out.println("Created int array, length=" + arr.getLength() +
                          ", values=[" + arr.getInt(0) + ", " + arr.getInt(1) + ", ...]");

        ObjectInstance str1 = heap.internString("hello");
        ObjectInstance str2 = heap.internString("hello");
        System.out.println("String interning: same instance = " + (str1 == str2));

        System.out.println("Total objects: " + heap.objectCount());
        System.out.println();
    }

    private static void demoDebugSession() {
        System.out.println("--- Demo 4: Debug Session ---\n");

        System.out.println("DebugSession features:");
        System.out.println("  - State machine: IDLE -> PAUSED -> RUNNING -> STOPPED");
        System.out.println("  - Breakpoints at specific bytecode offsets");
        System.out.println("  - Step modes: INTO, OVER, OUT, RUN_TO_CURSOR");
        System.out.println("  - State snapshots for UI display");
        System.out.println();

        Breakpoint bp = new Breakpoint("com/example/MyClass", "process", "(I)V", 10);
        System.out.println("Created breakpoint: " + bp.getClassName() + "." +
                          bp.getMethodName() + " at PC=" + bp.getPC());

        BreakpointManager mgr = new BreakpointManager();
        mgr.addBreakpoint(bp);
        mgr.addBreakpoint(new Breakpoint("com/example/MyClass", "process", "(I)V", 25));
        System.out.println("Breakpoints registered: " + mgr.getAllBreakpoints().size());

        System.out.println("\nDebug workflow:");
        System.out.println("  1. session.start(method) -> PAUSED");
        System.out.println("  2. session.stepOver() -> execute one instruction");
        System.out.println("  3. session.resume() -> run until breakpoint");
        System.out.println("  4. session.stop() -> STOPPED, get result");
        System.out.println();
    }
}
