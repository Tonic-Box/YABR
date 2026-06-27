package com.tonic.demo;

import com.tonic.analysis.simulation.core.*;
import com.tonic.analysis.simulation.listener.*;
import com.tonic.analysis.simulation.metrics.*;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;

/**
 * Demo for the Simulation API.
 *
 * <p>Shows how to:
 * <ul>
 *   <li>Configure simulation context</li>
 *   <li>Use built-in listeners</li>
 *   <li>Collect and display metrics</li>
 *   <li>Track stack operations</li>
 *   <li>Track allocations and method calls</li>
 * </ul>
 */
public class SimulationDemo {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            // Load user-specified class file
            ClassPool pool = ClassPool.getDefault();
            for (String path : args) {
                try (FileInputStream fis = new FileInputStream(path)) {
                    pool.loadClass(fis);
                }
            }

            // Analyze first class
            ClassFile cf = pool.getClasses().iterator().next();
            analyzeClass(cf);
        } else {
            System.out.println("Usage: SimulationDemo <classfile>");
            System.out.println("\nRunning demo with synthetic examples...\n");
            runSyntheticDemo();
        }
    }

    private static void analyzeClass(ClassFile cf) {
        System.out.println("=== Analyzing: " + cf.getClassName() + " ===\n");

        SSA ssa = new SSA(cf.getConstPool());

        for (MethodEntry method : cf.getMethods()) {
            if (method.getCodeAttribute() == null) {
                // Skip abstract/native methods that have no code
                continue;
            }

            System.out.println("Method: " + method.getName() + method.getDesc());
            System.out.println("-".repeat(60));

            try {
                IRMethod irMethod = ssa.lift(method);
                analyzeMethod(irMethod);
            } catch (Exception e) {
                System.out.println("  Error: " + e.getMessage());
            }

            System.out.println();
        }
    }

    private static void analyzeMethod(IRMethod irMethod) {
        // Configure simulation
        SimulationContext ctx = SimulationContext.defaults()
            .withMode(SimulationMode.INSTRUCTION)
            .withValueTracking(true)
            .withStackOperationTracking(true);

        // Create listeners
        StackOperationListener stackListener = new StackOperationListener(true);
        AllocationListener allocListener = new AllocationListener();
        FieldAccessListener fieldListener = new FieldAccessListener();
        MethodCallListener callListener = new MethodCallListener();
        ControlFlowListener cfListener = new ControlFlowListener();

        // Run simulation
        SimulationEngine engine = new SimulationEngine(ctx)
            .addListener(stackListener)
            .addListener(allocListener)
            .addListener(fieldListener)
            .addListener(callListener)
            .addListener(cfListener);

        SimulationResult result = engine.simulate(irMethod);

        // Collect metrics
        StackMetrics stackMetrics = StackMetrics.from(stackListener);
        AllocationMetrics allocMetrics = AllocationMetrics.from(allocListener);
        AccessMetrics accessMetrics = AccessMetrics.from(fieldListener);
        CallMetrics callMetrics = CallMetrics.from(callListener);
        PathMetrics pathMetrics = PathMetrics.from(cfListener);

        // Display results
        System.out.println("  Stack Operations:");
        System.out.println("    Pushes: " + stackMetrics.getPushCount());
        System.out.println("    Pops:   " + stackMetrics.getPopCount());
        System.out.println("    Max Depth: " + stackMetrics.getMaxDepth());
        System.out.println("    Net Change: " + stackMetrics.getNetChange());

        if (allocMetrics.hasAllocations()) {
            System.out.println("  Allocations:");
            System.out.println("    Objects: " + allocMetrics.getObjectCount());
            System.out.println("    Arrays:  " + allocMetrics.getArrayCount());
            System.out.println("    Distinct Types: " + allocMetrics.getDistinctTypeCount());
        }

        if (accessMetrics.hasAccesses()) {
            System.out.println("  Field Accesses:");
            System.out.println("    Reads:  " + accessMetrics.getFieldReads());
            System.out.println("    Writes: " + accessMetrics.getFieldWrites());
        }

        if (callMetrics.hasCalls()) {
            System.out.println("  Method Calls:");
            System.out.println("    Total:     " + callMetrics.getTotalCalls());
            System.out.println("    Virtual:   " + callMetrics.getVirtualCalls());
            System.out.println("    Static:    " + callMetrics.getStaticCalls());
            System.out.println("    Interface: " + callMetrics.getInterfaceCalls());
            System.out.println("    Distinct Methods: " + callMetrics.getDistinctMethods());
        }

        System.out.println("  Control Flow:");
        System.out.println("    Blocks Visited: " + pathMetrics.getBlocksVisited());
        System.out.println("    Branches: " + pathMetrics.getBranchCount());
        System.out.println("    Returns:  " + pathMetrics.getReturnCount());
        if (pathMetrics.hasLoops()) {
            System.out.println("    Has Loops: yes");
        }
        System.out.println("    Complexity: " + pathMetrics.getComplexityIndicator());
    }

    private static void runSyntheticDemo() {
        System.out.println("=== Simulation API Demo ===\n");

        // Demo 1: Basic stack operations
        demoStackOperations();

        // Demo 2: Metrics collection
        demoMetricsCollection();

        // Demo 3: Listener composition
        demoListenerComposition();
    }

    private static void demoStackOperations() {
        System.out.println("--- Demo 1: Basic Stack Operations ---\n");

        StackOperationListener listener = new StackOperationListener(true);
        listener.onSimulationStart(null);

        // Simulate some operations
        SimValue intVal = SimValue.constant(42, PrimitiveType.INT, null);
        SimValue longVal = SimValue.ofType(PrimitiveType.LONG, null);
        SimValue refVal = SimValue.unknown(null);

        listener.onStackPush(intVal, null);
        System.out.println("Pushed int: depth=" + listener.getCurrentDepth());

        listener.onStackPush(longVal, null);
        listener.onStackPush(SimValue.wideSecondSlot(), null); // Wide takes 2 slots
        System.out.println("Pushed long: depth=" + listener.getCurrentDepth());

        listener.onStackPush(refVal, null);
        System.out.println("Pushed ref: depth=" + listener.getCurrentDepth());

        listener.onStackPop(refVal, null);
        System.out.println("Popped ref: depth=" + listener.getCurrentDepth());

        System.out.println("\nFinal stats:");
        System.out.println("  Push count: " + listener.getPushCount());
        System.out.println("  Pop count: " + listener.getPopCount());
        System.out.println("  Max depth: " + listener.getMaxDepth());
        System.out.println("  Depth history: " + listener.getDepthHistory());
        System.out.println();
    }

    private static void demoMetricsCollection() {
        System.out.println("--- Demo 2: Metrics Collection ---\n");

        // Stack metrics
        StackOperationListener s1 = new StackOperationListener();
        s1.onSimulationStart(null);
        SimValue val = SimValue.unknown(null);
        s1.onStackPush(val, null);
        s1.onStackPush(val, null);
        s1.onStackPop(val, null);

        StackOperationListener s2 = new StackOperationListener();
        s2.onSimulationStart(null);
        s2.onStackPush(val, null);
        s2.onStackPush(val, null);
        s2.onStackPush(val, null);

        StackMetrics m1 = StackMetrics.from(s1);
        StackMetrics m2 = StackMetrics.from(s2);
        StackMetrics combined = m1.combine(m2);

        System.out.println("Metrics from listener 1:");
        System.out.println("  Pushes=" + m1.getPushCount() + ", Pops=" + m1.getPopCount() +
            ", MaxDepth=" + m1.getMaxDepth());

        System.out.println("Metrics from listener 2:");
        System.out.println("  Pushes=" + m2.getPushCount() + ", Pops=" + m2.getPopCount() +
            ", MaxDepth=" + m2.getMaxDepth());

        System.out.println("Combined metrics:");
        System.out.println("  Pushes=" + combined.getPushCount() + ", Pops=" + combined.getPopCount() +
            ", MaxDepth=" + combined.getMaxDepth());

        System.out.println();
    }

    private static void demoListenerComposition() {
        System.out.println("--- Demo 3: Listener Composition ---\n");

        // Create individual listeners
        StackOperationListener stackListener = new StackOperationListener();
        AllocationListener allocListener = new AllocationListener();
        MethodCallListener callListener = new MethodCallListener();

        // Compose them
        CompositeListener composite = new CompositeListener(
            stackListener, allocListener, callListener
        );

        // Start simulation
        composite.onSimulationStart(null);

        // Simulate events
        SimValue val = SimValue.unknown(null);
        composite.onStackPush(val, null);
        composite.onStackPush(val, null);
        composite.onStackPop(val, null);

        System.out.println("After composite listener events:");
        System.out.println("  Stack pushes: " + stackListener.getPushCount());
        System.out.println("  Stack pops: " + stackListener.getPopCount());
        System.out.println("  Stack depth: " + stackListener.getCurrentDepth());

        System.out.println("\nComposite listener dispatches events to all child listeners.");
        System.out.println();
    }
}
