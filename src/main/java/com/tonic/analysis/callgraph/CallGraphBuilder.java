package com.tonic.analysis.callgraph;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.InvokeType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassHierarchyBuilder;

import java.util.List;
import java.util.Set;

/**
 * Builds a CallGraph from a ClassPool by scanning all methods for invocations.
 */
public class CallGraphBuilder {

    /**
     * Builds a complete call graph from the given ClassPool.
     *
     * @param classPool the ClassPool containing all classes to analyze
     * @return the built CallGraph
     */
    public static CallGraph build(ClassPool classPool) {
        // Build class hierarchy for virtual dispatch resolution
        ClassHierarchy hierarchy = ClassHierarchyBuilder.build(classPool);
        CallGraph graph = new CallGraph(classPool, hierarchy);

        // Get all classes from the pool
        List<ClassFile> classes = classPool.getClasses();
        if (classes == null || classes.isEmpty()) {
            return graph;
        }

        // First pass: create nodes for all methods
        for (ClassFile cf : classes) {
            for (MethodEntry method : cf.getMethods()) {
                MethodReference ref = new MethodReference(
                        cf.getClassName(),
                        method.getName(),
                        method.getDesc()
                );
                graph.getOrCreateNode(ref, method);
            }
        }

        // Second pass: scan each method for invocations
        for (ClassFile cf : classes) {
            for (MethodEntry method : cf.getMethods()) {
                if (method.getCodeAttribute() == null) continue;

                MethodReference callerRef = new MethodReference(
                        cf.getClassName(),
                        method.getName(),
                        method.getDesc()
                );

                scanMethodForCalls(graph, callerRef, method, cf);
            }
        }

        return graph;
    }

    /**
     * Scans a method for all call sites and adds edges to the graph.
     */
    private static void scanMethodForCalls(CallGraph graph, MethodReference callerRef,
                                           MethodEntry method, ClassFile cf) {
        try {
            // Use SSA lifter to get IR representation
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod irMethod = ssa.lift(method);

            if (irMethod == null || irMethod.getEntryBlock() == null) {
                return;
            }

            CallGraphNode callerNode = graph.getOrCreateNode(callerRef, method);

            // Scan all blocks for invoke instructions
            for (IRBlock block : irMethod.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof InvokeInstruction) {
                        processInvokeInstruction(graph, callerNode, callerRef, (InvokeInstruction) instr);
                    }
                }
            }
        } catch (Exception e) {
            // Silently skip methods that fail to lift
        }
    }

    /**
     * Processes a single invoke instruction and adds edges to the graph.
     */
    private static void processInvokeInstruction(CallGraph graph, CallGraphNode callerNode,
                                                  MethodReference callerRef, InvokeInstruction invoke) {
        InvokeType invokeType = invoke.getInvokeType();
        String targetOwner = invoke.getOwner();
        String targetName = invoke.getName();
        String targetDesc = invoke.getDescriptor();

        // Handle different invoke types
        if (invokeType == InvokeType.VIRTUAL || invokeType == InvokeType.INTERFACE) {
            // For virtual/interface calls, resolve all possible targets
            Set<MethodReference> targets = graph.resolveVirtualTargets(targetOwner, targetName, targetDesc);
            for (MethodReference target : targets) {
                addCallEdge(graph, callerNode, callerRef, target, invokeType);
            }
        } else if (invokeType == InvokeType.DYNAMIC) {
            // For invokedynamic, create a synthetic reference
            MethodReference target = new MethodReference(
                    "[dynamic]",
                    targetName,
                    targetDesc
            );
            addCallEdge(graph, callerNode, callerRef, target, invokeType);
        } else {
            // STATIC and SPECIAL calls have a single target
            MethodReference target = new MethodReference(targetOwner, targetName, targetDesc);
            addCallEdge(graph, callerNode, callerRef, target, invokeType);
        }
    }

    /**
     * Adds a call edge between caller and callee.
     */
    private static void addCallEdge(CallGraph graph, CallGraphNode callerNode,
                                    MethodReference callerRef, MethodReference targetRef,
                                    InvokeType invokeType) {
        // Get or create target node
        CallGraphNode targetNode = graph.getOrCreateNode(targetRef, null);

        // Create call site
        CallSite site = new CallSite(callerRef, targetRef, invokeType);

        // Add edges
        callerNode.addOutgoingCall(site);
        targetNode.addIncomingCall(site);
    }
}
