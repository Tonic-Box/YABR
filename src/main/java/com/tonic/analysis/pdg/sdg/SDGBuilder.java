package com.tonic.analysis.pdg.sdg;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.callgraph.CallGraphNode;
import com.tonic.analysis.callgraph.CallSite;
import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.PDGBuilder;
import com.tonic.analysis.pdg.edge.PDGDependenceType;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGInstructionNode;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.sdg.node.*;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.utill.DescriptorUtil;
import lombok.Getter;

import java.util.*;

@Getter
public class SDGBuilder {

    private final CallGraph callGraph;
    private final Map<MethodReference, IRMethod> irMethods;
    private SDG sdg;

    private final Map<InvokeInstruction, SDGCallNode> invokeToCallNode = new HashMap<>();

    public SDGBuilder(CallGraph callGraph, Map<MethodReference, IRMethod> irMethods) {
        this.callGraph = callGraph;
        this.irMethods = irMethods;
    }

    public static SDG build(CallGraph callGraph, Map<MethodReference, IRMethod> irMethods) {
        SDGBuilder builder = new SDGBuilder(callGraph, irMethods);
        return builder.buildInternal();
    }

    private SDG buildInternal() {
        sdg = new SDG(callGraph);

        buildMethodPDGs();
        createProcedureNodes();
        createCallSiteNodes();
        connectParameterEdges();
        computeSummaryEdges();

        return sdg;
    }

    private void buildMethodPDGs() {
        for (Map.Entry<MethodReference, IRMethod> entry : irMethods.entrySet()) {
            MethodReference methodRef = entry.getKey();
            IRMethod irMethod = entry.getValue();

            PDG pdg = PDGBuilder.build(irMethod);
            sdg.addMethodPDG(methodRef, pdg);
        }
    }

    private void createProcedureNodes() {
        for (Map.Entry<MethodReference, IRMethod> entry : irMethods.entrySet()) {
            MethodReference methodRef = entry.getKey();
            IRMethod irMethod = entry.getValue();

            IRBlock entryBlock = irMethod.getEntryBlock();
            SDGEntryNode entryNode = new SDGEntryNode(
                sdg.allocateNodeId(), methodRef, entryBlock);

            PDG pdg = sdg.getPDG(methodRef);
            entryNode.setProcedurePDG(pdg);

            createFormalParameters(entryNode, irMethod, methodRef);
            createFormalOut(entryNode, irMethod, methodRef);

            sdg.addMethodEntry(methodRef, entryNode);
        }
    }

    private void createFormalParameters(SDGEntryNode entryNode, IRMethod irMethod, MethodReference methodRef) {
        List<SSAValue> params = irMethod.getParameters();
        List<String> paramTypes = DescriptorUtil.parseParameterDescriptors(methodRef.getDescriptor());

        int paramIndex = 0;
        if (!irMethod.isStatic()) {
            if (!params.isEmpty()) {
                SSAValue thisParam = params.get(0);
                SDGFormalInNode formalIn = new SDGFormalInNode(
                    sdg.allocateNodeId(), paramIndex, thisParam,
                    methodRef.getOwner(), irMethod.getEntryBlock());
                formalIn.setEntryNode(entryNode);
                entryNode.addFormalIn(formalIn);
                sdg.addNode(formalIn);
            }
            paramIndex++;
        }

        for (int i = 0; i < paramTypes.size() && paramIndex < params.size(); i++) {
            SSAValue param = params.get(paramIndex);
            SDGFormalInNode formalIn = new SDGFormalInNode(
                sdg.allocateNodeId(), paramIndex, param,
                paramTypes.get(i), irMethod.getEntryBlock());
            formalIn.setEntryNode(entryNode);
            entryNode.addFormalIn(formalIn);
            sdg.addNode(formalIn);
            paramIndex++;
        }
    }

    private void createFormalOut(SDGEntryNode entryNode, IRMethod irMethod, MethodReference methodRef) {
        String returnType = DescriptorUtil.parseReturnDescriptor(methodRef.getDescriptor());
        if ("V".equals(returnType)) {
            return;
        }

        SSAValue returnValue = findReturnValue(irMethod);
        IRBlock exitBlock = findExitBlock(irMethod);

        SDGFormalOutNode formalOut = new SDGFormalOutNode(
            sdg.allocateNodeId(), returnValue, returnType, exitBlock);
        formalOut.setEntryNode(entryNode);
        entryNode.setFormalOut(formalOut);
        sdg.addNode(formalOut);
    }

    private SSAValue findReturnValue(IRMethod method) {
        for (IRBlock block : method.getBlocks()) {
            for (var instr : block.getInstructions()) {
                if (instr instanceof ReturnInstruction) {
                    ReturnInstruction ret = (ReturnInstruction) instr;
                    Value retVal = ret.getReturnValue();
                    if (retVal instanceof SSAValue) {
                        return (SSAValue) retVal;
                    }
                }
            }
        }
        return null;
    }

    private IRBlock findExitBlock(IRMethod method) {
        for (IRBlock block : method.getBlocks()) {
            for (var instr : block.getInstructions()) {
                if (instr instanceof ReturnInstruction) {
                    return block;
                }
            }
        }
        return null;
    }

    private void createCallSiteNodes() {
        for (Map.Entry<MethodReference, PDG> entry : sdg.getMethodPDGs().entrySet()) {
            MethodReference callerRef = entry.getKey();
            PDG pdg = entry.getValue();
            IRMethod irMethod = irMethods.get(callerRef);

            if (irMethod == null) continue;

            for (PDGNode node : pdg.getNodes()) {
                if (node instanceof PDGInstructionNode) {
                    PDGInstructionNode instrNode = (PDGInstructionNode) node;
                    if (instrNode.getInstruction() instanceof InvokeInstruction) {
                        InvokeInstruction invoke = (InvokeInstruction) instrNode.getInstruction();
                        createCallSiteForInvoke(invoke, instrNode, callerRef);
                    }
                }
            }
        }
    }

    private void createCallSiteForInvoke(InvokeInstruction invoke, PDGInstructionNode instrNode,
                                         MethodReference callerRef) {
        CallGraphNode callerNode = callGraph.getNode(
            callerRef.getOwner(), callerRef.getName(), callerRef.getDescriptor());

        CallSite callSite = null;
        if (callerNode != null) {
            for (CallSite cs : callerNode.getOutgoingCalls()) {
                MethodReference target = cs.getTarget();
                if (target.getOwner().equals(invoke.getOwner())
                    && target.getName().equals(invoke.getName())
                    && target.getDescriptor().equals(invoke.getDescriptor())) {
                    callSite = cs;
                    break;
                }
            }
        }

        SDGCallNode callNode = new SDGCallNode(
            sdg.allocateNodeId(), invoke, callSite, instrNode.getBlock());
        sdg.addNode(callNode);
        invokeToCallNode.put(invoke, callNode);

        createActualParameters(callNode, invoke);
        createActualOut(callNode, invoke);

        linkCallToTargets(callNode, invoke);
    }

    private void createActualParameters(SDGCallNode callNode, InvokeInstruction invoke) {
        List<Value> args = invoke.getArguments();
        int paramIndex = 0;

        if (invoke.getInvokeType() != com.tonic.analysis.ssa.ir.InvokeType.STATIC) {
            Value receiver = invoke.getReceiver();
            if (receiver != null) {
                SDGActualInNode actualIn = new SDGActualInNode(
                    sdg.allocateNodeId(), paramIndex, receiver, callNode.getBlock());
                actualIn.setCallNode(callNode);
                callNode.addActualIn(actualIn);
                sdg.addNode(actualIn);
            }
            paramIndex++;
        }

        for (Value arg : args) {
            SDGActualInNode actualIn = new SDGActualInNode(
                sdg.allocateNodeId(), paramIndex, arg, callNode.getBlock());
            actualIn.setCallNode(callNode);
            callNode.addActualIn(actualIn);
            sdg.addNode(actualIn);
            paramIndex++;
        }
    }

    private void createActualOut(SDGCallNode callNode, InvokeInstruction invoke) {
        SSAValue result = invoke.getResult();
        if (result == null) return;

        SDGActualOutNode actualOut = new SDGActualOutNode(
            sdg.allocateNodeId(), result, callNode.getBlock());
        actualOut.setCallNode(callNode);
        callNode.setActualOut(actualOut);
        sdg.addNode(actualOut);
    }

    private void linkCallToTargets(SDGCallNode callNode, InvokeInstruction invoke) {
        MethodReference targetRef = new MethodReference(
            invoke.getOwner(), invoke.getName(), invoke.getDescriptor());

        SDGEntryNode targetEntry = sdg.getEntry(targetRef);
        if (targetEntry != null) {
            sdg.registerCallTarget(callNode, targetEntry);
        }
    }

    private void connectParameterEdges() {
        for (SDGCallNode callNode : invokeToCallNode.values()) {
            SDGEntryNode targetEntry = callNode.getTargetEntry();
            if (targetEntry == null) continue;

            sdg.addEdge(new PDGEdge(callNode, targetEntry, PDGDependenceType.CALL));

            List<SDGActualInNode> actualIns = callNode.getActualIns();
            List<SDGFormalInNode> formalIns = targetEntry.getFormalIns();

            int minParams = Math.min(actualIns.size(), formalIns.size());
            for (int i = 0; i < minParams; i++) {
                SDGActualInNode actualIn = actualIns.get(i);
                SDGFormalInNode formalIn = formalIns.get(i);

                sdg.addEdge(new PDGEdge(actualIn, formalIn, PDGDependenceType.PARAMETER_IN));
            }

            SDGFormalOutNode formalOut = targetEntry.getFormalOut();
            SDGActualOutNode actualOut = callNode.getActualOut();

            if (formalOut != null && actualOut != null) {
                sdg.addEdge(new PDGEdge(formalOut, actualOut, PDGDependenceType.PARAMETER_OUT));
            }
        }
    }

    private void computeSummaryEdges() {
        Map<SDGEntryNode, Set<SummaryInfo>> methodSummaries = new HashMap<>();

        for (SDGEntryNode entry : sdg.getMethodEntries().values()) {
            Set<SummaryInfo> summaries = computeMethodSummary(entry);
            methodSummaries.put(entry, summaries);
        }

        boolean changed = true;
        int maxIterations = sdg.getMethodCount() * 10;
        int iterations = 0;

        while (changed && iterations < maxIterations) {
            changed = false;
            iterations++;

            for (SDGCallNode callNode : invokeToCallNode.values()) {
                SDGEntryNode targetEntry = callNode.getTargetEntry();
                if (targetEntry == null) continue;

                Set<SummaryInfo> targetSummaries = methodSummaries.get(targetEntry);
                if (targetSummaries == null) continue;

                for (SummaryInfo summary : targetSummaries) {
                    SDGActualInNode actualIn = callNode.getActualIn(summary.fromParam);
                    PDGNode actualTarget = summary.toReturn
                        ? callNode.getActualOut()
                        : callNode.getActualIn(summary.toParam);

                    if (actualIn != null && actualTarget != null) {
                        PDGEdge summaryEdge = new PDGEdge(
                            actualIn, actualTarget, PDGDependenceType.SUMMARY);

                        if (!sdg.getSummaryEdges().contains(summaryEdge)) {
                            sdg.addEdge(summaryEdge);
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    private Set<SummaryInfo> computeMethodSummary(SDGEntryNode entry) {
        Set<SummaryInfo> summaries = new HashSet<>();
        PDG pdg = entry.getProcedurePDG();

        if (pdg == null) return summaries;

        SDGFormalOutNode formalOut = entry.getFormalOut();
        if (formalOut == null) return summaries;

        for (SDGFormalInNode formalIn : entry.getFormalIns()) {
            if (isReachable(formalIn, formalOut)) {
                summaries.add(new SummaryInfo(formalIn.getParameterIndex(), true, -1));
            }
        }

        return summaries;
    }

    private boolean isReachable(PDGNode source, PDGNode target) {
        Set<PDGNode> visited = new HashSet<>();
        Deque<PDGNode> worklist = new ArrayDeque<>();
        worklist.add(source);

        while (!worklist.isEmpty()) {
            PDGNode current = worklist.poll();
            if (current == target) return true;
            if (!visited.add(current)) continue;

            for (PDGEdge edge : current.getOutgoingEdges()) {
                if (!edge.isInterprocedural()) {
                    worklist.add(edge.getTarget());
                }
            }
        }
        return false;
    }

    private static class SummaryInfo {
        final int fromParam;
        final boolean toReturn;
        final int toParam;

        SummaryInfo(int fromParam, boolean toReturn, int toParam) {
            this.fromParam = fromParam;
            this.toReturn = toReturn;
            this.toParam = toParam;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SummaryInfo that = (SummaryInfo) o;
            return fromParam == that.fromParam && toReturn == that.toReturn && toParam == that.toParam;
        }

        @Override
        public int hashCode() {
            return Objects.hash(fromParam, toReturn, toParam);
        }
    }
}
