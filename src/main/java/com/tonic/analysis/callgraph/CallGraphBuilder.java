package com.tonic.analysis.callgraph;

import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BootstrapMethodInfo;
import com.tonic.analysis.ssa.ir.ConstantInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.InvokeType;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.DynamicConstant;
import com.tonic.analysis.ssa.value.MethodHandleConstant;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.parser.attribute.table.BootstrapMethod;
import com.tonic.parser.constpool.InterfaceRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.MethodHandleItem;
import com.tonic.parser.constpool.MethodRefItem;
import com.tonic.parser.constpool.structure.MethodHandle;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassHierarchyBuilder;
import lombok.Getter;

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
    @Getter
    private static String currentMethod = null;

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

                currentMethod = cf.getClassName() + "." + method.getName() + method.getDesc();
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
            BootstrapMethodsAttribute bsmAttr = findBootstrapMethodsAttribute(cf);

            for (IRBlock block : irMethod.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof InvokeInstruction) {
                        processInvokeInstruction(graph, callerNode, callerRef, (InvokeInstruction) instr);
                    } else if (instr instanceof ConstantInstruction) {
                        processConstantInstruction(graph, callerNode, callerRef,
                                (ConstantInstruction) instr, cf.getConstPool(), bsmAttr);
                    }
                }
            }
        } catch (Exception e) {
            // Silently skip methods that fail to analyze
        } catch (StackOverflowError e) {
            System.err.println("[CallGraph] StackOverflowError in method: " + currentMethod);
            // Continue with next method
        }
    }

    /**
     * Finds the BootstrapMethodsAttribute in the class file.
     */
    private static BootstrapMethodsAttribute findBootstrapMethodsAttribute(ClassFile cf) {
        for (Attribute attr : cf.getClassAttributes()) {
            if (attr instanceof BootstrapMethodsAttribute) {
                return (BootstrapMethodsAttribute) attr;
            }
        }
        return null;
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
            processDynamicInvoke(graph, callerNode, callerRef, invoke, targetName, targetDesc);
        } else {
            MethodReference target = new MethodReference(targetOwner, targetName, targetDesc);
            addCallEdge(graph, callerNode, callerRef, target, invokeType);
        }
    }

    /**
     * Processes an invokedynamic instruction, extracting bootstrap method and any
     * method handle targets from bootstrap arguments (including lambda implementations).
     */
    private static void processDynamicInvoke(CallGraph graph, CallGraphNode callerNode,
                                              MethodReference callerRef, InvokeInstruction invoke,
                                              String targetName, String targetDesc) {
        BootstrapMethodInfo bsmInfo = invoke.getBootstrapInfo();

        if (bsmInfo == null) {
            MethodReference target = new MethodReference("[dynamic]", targetName, targetDesc);
            addCallEdge(graph, callerNode, callerRef, target, InvokeType.DYNAMIC);
            return;
        }

        MethodHandleConstant bsm = bsmInfo.getBootstrapMethod();

        if (bsm.isMethodReference()) {
            MethodReference bsmRef = new MethodReference(
                    bsm.getOwner(),
                    bsm.getName(),
                    bsm.getDescriptor()
            );
            addCallEdge(graph, callerNode, callerRef, bsmRef, InvokeType.DYNAMIC);
        }

        List<Constant> bsmArgs = bsmInfo.getBootstrapArguments();

        if (bsmInfo.isLambdaMetafactory() && bsmArgs.size() >= 2) {
            Constant implArg = bsmArgs.get(1);
            if (implArg instanceof MethodHandleConstant) {
                MethodHandleConstant implHandle = (MethodHandleConstant) implArg;
                if (implHandle.isMethodReference()) {
                    MethodReference lambdaImpl = new MethodReference(
                            implHandle.getOwner(),
                            implHandle.getName(),
                            implHandle.getDescriptor()
                    );
                    addCallEdge(graph, callerNode, callerRef, lambdaImpl, InvokeType.DYNAMIC);
                }
            }
        } else {
            for (Constant arg : bsmArgs) {
                if (arg instanceof MethodHandleConstant) {
                    MethodHandleConstant mh = (MethodHandleConstant) arg;
                    if (mh.isMethodReference()) {
                        MethodReference target = new MethodReference(
                                mh.getOwner(),
                                mh.getName(),
                                mh.getDescriptor()
                        );
                        addCallEdge(graph, callerNode, callerRef, target, InvokeType.DYNAMIC);
                    }
                }
            }
        }
    }

    /**
     * Processes a constant instruction, specifically looking for DynamicConstant (condy)
     * and extracting the bootstrap method targets.
     */
    private static void processConstantInstruction(CallGraph graph, CallGraphNode callerNode,
                                                    MethodReference callerRef, ConstantInstruction constInstr,
                                                    ConstPool constPool, BootstrapMethodsAttribute bsmAttr) {
        Constant constant = constInstr.getConstant();
        if (!(constant instanceof DynamicConstant)) {
            return;
        }

        DynamicConstant dynConst = (DynamicConstant) constant;
        if (bsmAttr == null) {
            return;
        }

        int bsmIndex = dynConst.getBootstrapMethodIndex();
        List<BootstrapMethod> bootstrapMethods = bsmAttr.getBootstrapMethods();
        if (bsmIndex < 0 || bsmIndex >= bootstrapMethods.size()) {
            return;
        }

        BootstrapMethod bsm = bootstrapMethods.get(bsmIndex);
        processBootstrapMethod(graph, callerNode, callerRef, bsm, constPool);
    }

    /**
     * Processes a bootstrap method entry, extracting the bootstrap method handle
     * and any method handles in the arguments.
     */
    private static void processBootstrapMethod(CallGraph graph, CallGraphNode callerNode,
                                                MethodReference callerRef, BootstrapMethod bsm,
                                                ConstPool constPool) {
        int bsmRef = bsm.getBootstrapMethodRef();
        Item<?> bsmItem = constPool.getItem(bsmRef);
        if (bsmItem instanceof MethodHandleItem) {
            MethodHandle mh = ((MethodHandleItem) bsmItem).getValue();
            if (isMethodInvocation(mh.getReferenceKind())) {
                MethodReference bsmTarget = resolveMethodHandle(mh, constPool);
                if (bsmTarget != null) {
                    addCallEdge(graph, callerNode, callerRef, bsmTarget, InvokeType.DYNAMIC);
                }
            }
        }

        for (Integer argIndex : bsm.getBootstrapArguments()) {
            Item<?> argItem = constPool.getItem(argIndex);
            if (argItem instanceof MethodHandleItem) {
                MethodHandle mh = ((MethodHandleItem) argItem).getValue();
                if (isMethodInvocation(mh.getReferenceKind())) {
                    MethodReference argTarget = resolveMethodHandle(mh, constPool);
                    if (argTarget != null) {
                        addCallEdge(graph, callerNode, callerRef, argTarget, InvokeType.DYNAMIC);
                    }
                }
            }
        }
    }

    /**
     * Checks if a method handle reference kind represents a method invocation.
     */
    private static boolean isMethodInvocation(int refKind) {
        return refKind >= MethodHandleConstant.REF_invokeVirtual &&
               refKind <= MethodHandleConstant.REF_invokeInterface;
    }

    /**
     * Resolves a MethodHandle from the constant pool to a MethodReference.
     */
    private static MethodReference resolveMethodHandle(MethodHandle mh, ConstPool constPool) {
        try {
            int memberRef = mh.getReferenceIndex();
            Item<?> item = constPool.getItem(memberRef);

            String owner = null;
            String name = null;
            String desc = null;

            if (item instanceof MethodRefItem) {
                MethodRefItem mri = (MethodRefItem) item;
                owner = mri.getOwner();
                name = mri.getName();
                desc = mri.getDescriptor();
            } else if (item instanceof InterfaceRefItem) {
                InterfaceRefItem iri = (InterfaceRefItem) item;
                owner = iri.getOwner();
                name = iri.getName();
                desc = iri.getDescriptor();
            }

            if (owner != null && name != null && desc != null) {
                return new MethodReference(owner, name, desc);
            }
            return null;
        } catch (Exception e) {
            return null;
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
