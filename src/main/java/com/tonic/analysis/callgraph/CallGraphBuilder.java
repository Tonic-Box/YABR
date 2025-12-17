package com.tonic.analysis.callgraph;

import com.tonic.analysis.common.MethodReference;
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
        ClassHierarchy hierarchy = ClassHierarchyBuilder.build(classPool);
        CallGraph graph = new CallGraph(classPool, hierarchy);

        List<ClassFile> classes = classPool.getClasses();
        if (classes == null || classes.isEmpty()) {
            return graph;
        }

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
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod irMethod = ssa.lift(method);

            if (irMethod == null || irMethod.getEntryBlock() == null) {
                return;
            }

            CallGraphNode callerNode = graph.getOrCreateNode(callerRef, method);

            for (IRBlock block : irMethod.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof InvokeInstruction) {
                        processInvokeInstruction(graph, callerNode, callerRef, (InvokeInstruction) instr);
                    }
                }
            }
        } catch (Exception e) {
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

        if (invokeType == InvokeType.VIRTUAL || invokeType == InvokeType.INTERFACE) {
            Set<MethodReference> targets = graph.resolveVirtualTargets(targetOwner, targetName, targetDesc);
            for (MethodReference target : targets) {
                addCallEdge(graph, callerNode, callerRef, target, invokeType);
            }
        } else if (invokeType == InvokeType.DYNAMIC) {
            MethodReference target = new MethodReference(
                    "[dynamic]",
                    targetName,
                    targetDesc
            );
            addCallEdge(graph, callerNode, callerRef, target, invokeType);
        } else {
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
        CallGraphNode targetNode = graph.getOrCreateNode(targetRef, null);
        CallSite site = new CallSite(callerRef, targetRef, invokeType);
        callerNode.addOutgoingCall(site);
        targetNode.addIncomingCall(site);
    }
}
