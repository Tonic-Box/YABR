package com.tonic.analysis.cpg;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.cpg.edge.CPGEdgeType;
import com.tonic.analysis.cpg.node.*;
import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.PDGBuilder;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGInstructionNode;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.sdg.SDG;
import com.tonic.analysis.pdg.sdg.SDGBuilder;
import com.tonic.analysis.ssa.ir.BranchInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.EdgeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public class CPGBuilder {

    private final ClassPool classPool;
    private CodePropertyGraph cpg;
    private CallGraph callGraph;
    private boolean includeCallGraph = true;
    private boolean includePDG = true;
    private boolean includeSDG = false;

    private final Map<MethodReference, IRMethod> irMethods = new LinkedHashMap<>();
    private final Map<IRInstruction, InstructionNode> instructionNodes = new HashMap<>();
    private final Map<IRBlock, BlockNode> blockNodes = new HashMap<>();
    private final Map<MethodReference, MethodNode> methodNodes = new HashMap<>();

    public CPGBuilder(ClassPool classPool) {
        this.classPool = classPool;
    }

    public static CPGBuilder forClassPool(ClassPool pool) {
        return new CPGBuilder(pool);
    }

    public CPGBuilder withCallGraph(CallGraph callGraph) {
        this.callGraph = callGraph;
        this.includeCallGraph = true;
        return this;
    }

    public CPGBuilder withCallGraph() {
        this.callGraph = CallGraph.build(classPool);
        this.includeCallGraph = true;
        return this;
    }

    public CPGBuilder withPDG() {
        this.includePDG = true;
        return this;
    }

    public CPGBuilder withSDG() {
        this.includeSDG = true;
        this.includePDG = true;
        return this;
    }

    public CPGBuilder withoutCallGraph() {
        this.includeCallGraph = false;
        return this;
    }

    public CPGBuilder withoutPDG() {
        this.includePDG = false;
        return this;
    }

    public CodePropertyGraph build() {
        cpg = new CodePropertyGraph(classPool);

        liftAllMethods();
        buildMethodNodes();
        buildCFGEdges();

        if (includeCallGraph) {
            if (callGraph == null) {
                callGraph = CallGraph.build(classPool);
            }
            buildCallGraphEdges();
        }

        if (includePDG) {
            buildPDGEdges();
        }

        if (includeSDG && callGraph != null) {
            buildSDGEdges();
        }

        return cpg;
    }

    private void liftAllMethods() {
        for (ClassFile cf : getClassFiles()) {
            for (MethodEntry method : cf.getMethods()) {
                if (method.getCodeAttribute() == null) continue;

                try {
                    SSA ssa = new SSA(cf.getConstPool());
                    IRMethod irMethod = ssa.lift(method);
                    if (irMethod != null) {
                        MethodReference ref = new MethodReference(
                            cf.getClassName(), method.getName(), method.getDesc());
                        irMethods.put(ref, irMethod);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Collection<ClassFile> getClassFiles() {
        List<ClassFile> result = new ArrayList<>();
        for (ClassFile cf : classPool.getClasses()) {
            String className = cf.getClassName();
            if (!className.startsWith("java/")) {
                result.add(cf);
            }
        }
        return result;
    }

    private void buildMethodNodes() {
        for (Map.Entry<MethodReference, IRMethod> entry : irMethods.entrySet()) {
            MethodReference ref = entry.getKey();
            IRMethod irMethod = entry.getValue();

            MethodNode methodNode = new MethodNode(cpg.allocateNodeId(), irMethod, ref.getOwner());
            cpg.addNode(methodNode);
            methodNodes.put(ref, methodNode);

            buildBlockNodes(methodNode, irMethod);
            buildInstructionNodes(irMethod);
        }
    }

    private void buildBlockNodes(MethodNode methodNode, IRMethod irMethod) {
        for (IRBlock block : irMethod.getBlocks()) {
            BlockNode blockNode = new BlockNode(cpg.allocateNodeId(), block);
            cpg.addNode(blockNode);
            blockNodes.put(block, blockNode);

            cpg.addEdge(methodNode, blockNode, CPGEdgeType.CONTAINS);
        }
    }

    private void buildInstructionNodes(IRMethod irMethod) {
        for (IRBlock block : irMethod.getBlocks()) {
            BlockNode blockNode = blockNodes.get(block);
            int instrIndex = 0;

            for (PhiInstruction phi : block.getPhiInstructions()) {
                InstructionNode instrNode = new InstructionNode(
                    cpg.allocateNodeId(), phi, block.getId(), instrIndex++);
                cpg.addNode(instrNode);
                instructionNodes.put(phi, instrNode);
                cpg.addEdge(blockNode, instrNode, CPGEdgeType.CONTAINS);
            }

            for (IRInstruction instr : block.getInstructions()) {
                InstructionNode instrNode = new InstructionNode(
                    cpg.allocateNodeId(), instr, block.getId(), instrIndex++);
                cpg.addNode(instrNode);
                instructionNodes.put(instr, instrNode);
                cpg.addEdge(blockNode, instrNode, CPGEdgeType.CONTAINS);

                if (instr instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instr;
                    CallSiteNode callSite = new CallSiteNode(cpg.allocateNodeId(), invoke);
                    cpg.addNode(callSite);
                    cpg.addEdge(instrNode, callSite, CPGEdgeType.CALL);
                }
            }
        }
    }

    private void buildCFGEdges() {
        for (IRMethod irMethod : irMethods.values()) {
            for (IRBlock block : irMethod.getBlocks()) {
                BlockNode sourceBlock = blockNodes.get(block);
                if (sourceBlock == null) continue;

                for (IRBlock succ : block.getSuccessors()) {
                    BlockNode targetBlock = blockNodes.get(succ);
                    if (targetBlock == null) continue;

                    EdgeType edgeType = block.getEdgeType(succ);
                    CPGEdgeType cpgEdgeType = mapEdgeType(edgeType, block, succ);
                    cpg.addEdge(sourceBlock, targetBlock, cpgEdgeType);
                }
            }

            buildIntraBlockCFGEdges(irMethod);
        }
    }

    private void buildIntraBlockCFGEdges(IRMethod irMethod) {
        for (IRBlock block : irMethod.getBlocks()) {
            List<IRInstruction> allInstrs = new ArrayList<>();
            allInstrs.addAll(block.getPhiInstructions());
            allInstrs.addAll(block.getInstructions());

            for (int i = 0; i < allInstrs.size() - 1; i++) {
                InstructionNode source = instructionNodes.get(allInstrs.get(i));
                InstructionNode target = instructionNodes.get(allInstrs.get(i + 1));
                if (source != null && target != null) {
                    cpg.addEdge(source, target, CPGEdgeType.CFG_NEXT);
                }
            }
        }
    }

    private CPGEdgeType mapEdgeType(EdgeType edgeType, IRBlock source, IRBlock target) {
        if (edgeType == EdgeType.EXCEPTION) {
            return CPGEdgeType.CFG_EXCEPTION;
        }
        if (edgeType == EdgeType.BACK) {
            return CPGEdgeType.CFG_BACK;
        }

        IRInstruction terminator = source.getTerminator();
        if (terminator instanceof BranchInstruction) {
            BranchInstruction branch =
                (BranchInstruction) terminator;
            if (target == branch.getTrueTarget()) {
                return CPGEdgeType.CFG_TRUE;
            } else {
                return CPGEdgeType.CFG_FALSE;
            }
        }

        return CPGEdgeType.CFG_NEXT;
    }

    private void buildCallGraphEdges() {
        for (CallSiteNode callSite : cpg.nodes(CallSiteNode.class).collect(Collectors.toList())) {
            MethodReference targetRef = new MethodReference(
                callSite.getTargetOwner(),
                callSite.getTargetName(),
                callSite.getTargetDescriptor());

            MethodNode targetMethod = methodNodes.get(targetRef);
            if (targetMethod != null) {
                cpg.addEdge(callSite, targetMethod, CPGEdgeType.CALL);
            }
        }
    }

    private void buildPDGEdges() {
        for (Map.Entry<MethodReference, IRMethod> entry : irMethods.entrySet()) {
            IRMethod irMethod = entry.getValue();

            try {
                PDG pdg = PDGBuilder.build(irMethod);
                addPDGEdgesToCPG(pdg);
            } catch (Exception ignored) {
            }
        }
    }

    private void addPDGEdgesToCPG(PDG pdg) {
        for (PDGEdge pdgEdge : pdg.getEdges()) {
            CPGNode source = mapPDGNode(pdgEdge.getSource());
            CPGNode target = mapPDGNode(pdgEdge.getTarget());

            if (source == null || target == null) continue;

            CPGEdgeType edgeType = mapPDGEdgeType(pdgEdge);
            cpg.addEdge(source, target, edgeType);
        }
    }

    private CPGNode mapPDGNode(PDGNode pdgNode) {
        if (pdgNode instanceof PDGInstructionNode) {
            PDGInstructionNode instrNode = (PDGInstructionNode) pdgNode;
            return instructionNodes.get(instrNode.getInstruction());
        }
        return null;
    }

    private CPGEdgeType mapPDGEdgeType(PDGEdge pdgEdge) {
        switch (pdgEdge.getType()) {
            case CONTROL_TRUE:
                return CPGEdgeType.CONTROL_DEP_TRUE;
            case CONTROL_FALSE:
                return CPGEdgeType.CONTROL_DEP_FALSE;
            case CONTROL_UNCONDITIONAL:
            case CONTROL_EXCEPTION:
            case CONTROL_SWITCH:
                return CPGEdgeType.CONTROL_DEP;
            case DATA_DEF_USE:
            case DATA_PHI:
                return CPGEdgeType.DATA_DEF;
            default:
                return CPGEdgeType.DATA_DEF;
        }
    }

    private void buildSDGEdges() {
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        for (PDGEdge edge : sdg.getParameterEdges()) {
            CPGNode source = mapSDGNode(edge.getSource());
            CPGNode target = mapSDGNode(edge.getTarget());

            if (source != null && target != null) {
                CPGEdgeType edgeType = mapSDGEdgeType(edge);
                cpg.addEdge(source, target, edgeType);
            }
        }

        for (PDGEdge edge : sdg.getSummaryEdges()) {
            CPGNode source = mapSDGNode(edge.getSource());
            CPGNode target = mapSDGNode(edge.getTarget());

            if (source != null && target != null) {
                cpg.addEdge(source, target, CPGEdgeType.SUMMARY);
            }
        }
    }

    private CPGNode mapSDGNode(PDGNode pdgNode) {
        if (pdgNode instanceof PDGInstructionNode) {
            PDGInstructionNode instrNode = (PDGInstructionNode) pdgNode;
            return instructionNodes.get(instrNode.getInstruction());
        }
        return null;
    }

    private CPGEdgeType mapSDGEdgeType(PDGEdge edge) {
        switch (edge.getType()) {
            case PARAMETER_IN:
                return CPGEdgeType.PARAM_IN;
            case PARAMETER_OUT:
                return CPGEdgeType.PARAM_OUT;
            case CALL:
                return CPGEdgeType.CALL;
            case RETURN:
                return CPGEdgeType.RETURN_VALUE;
            case SUMMARY:
                return CPGEdgeType.SUMMARY;
            default:
                return CPGEdgeType.DATA_DEF;
        }
    }
}
