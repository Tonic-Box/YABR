package com.tonic.analysis.pdg;

import com.tonic.analysis.pdg.edge.PDGDependenceType;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGInstructionNode;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGRegionNode;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.PostDominatorTree;
import com.tonic.analysis.ssa.cfg.EdgeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BranchInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import lombok.Getter;

import java.util.*;

@Getter
public class PDGBuilder {

    private final IRMethod method;
    private final PostDominatorTree postDomTree;
    private final DefUseChains defUseChains;
    private PDG pdg;

    private final Map<IRBlock, PDGNode> blockTerminatorNodes = new HashMap<>();

    public PDGBuilder(IRMethod method) {
        this.method = method;
        this.postDomTree = new PostDominatorTree(method);
        this.defUseChains = new DefUseChains(method);
    }

    public static PDG build(IRMethod method) {
        PDGBuilder builder = new PDGBuilder(method);
        return builder.buildInternal();
    }

    private PDG buildInternal() {
        postDomTree.compute();
        defUseChains.compute();

        pdg = new PDG(method);

        createEntryExitNodes();
        createInstructionNodes();
        computeControlDependencies();
        computeDataDependencies();
        handleExceptionEdges();

        return pdg;
    }

    private void createEntryExitNodes() {
        IRBlock entryBlock = method.getEntryBlock();
        PDGRegionNode entry = PDGRegionNode.createEntry(pdg.allocateNodeId(), method.getName(), entryBlock);
        pdg.addNode(entry);
        pdg.setEntryNode(entry);

        Set<IRBlock> exitBlocks = findExitBlocks();
        IRBlock primaryExit = exitBlocks.isEmpty() ? null : exitBlocks.iterator().next();
        PDGRegionNode exit = PDGRegionNode.createExit(pdg.allocateNodeId(), method.getName(), primaryExit);
        for (IRBlock exitBlock : exitBlocks) {
            exit.addCoveredBlock(exitBlock);
        }
        pdg.addNode(exit);
        pdg.setExitNode(exit);
    }

    private Set<IRBlock> findExitBlocks() {
        Set<IRBlock> exits = new HashSet<>();
        for (IRBlock block : method.getBlocks()) {
            IRInstruction terminator = block.getTerminator();
            if (terminator instanceof com.tonic.analysis.ssa.ir.ReturnInstruction) {
                exits.add(block);
            } else if (block.getSuccessors().isEmpty()) {
                exits.add(block);
            }
        }
        return exits;
    }

    private void createInstructionNodes() {
        for (IRBlock block : method.getBlocks()) {
            int instrIndex = 0;

            for (PhiInstruction phi : block.getPhiInstructions()) {
                PDGInstructionNode node = new PDGInstructionNode(
                    pdg.allocateNodeId(), phi, block, instrIndex++);
                pdg.addNode(node);
            }

            for (IRInstruction instr : block.getInstructions()) {
                PDGInstructionNode node = new PDGInstructionNode(
                    pdg.allocateNodeId(), instr, block, instrIndex++);
                pdg.addNode(node);

                if (instr.isTerminator()) {
                    blockTerminatorNodes.put(block, node);
                }
            }
        }
    }

    private void computeControlDependencies() {
        for (IRBlock block : method.getBlocks()) {
            for (IRBlock succ : block.getSuccessors()) {
                if (!postDomTree.strictlyPostDominates(succ, block)) {
                    addControlDependenciesOnPath(block, succ);
                }
            }
        }

        addEntryControlDependencies();
    }

    private void addControlDependenciesOnPath(IRBlock branchBlock, IRBlock startBlock) {
        IRBlock lca = findLCA(branchBlock, startBlock);
        PDGNode controllingNode = getControllingNodeForBlock(branchBlock);

        if (controllingNode == null) {
            return;
        }

        boolean branchCondition = determineBranchCondition(branchBlock, startBlock);
        PDGDependenceType edgeType = PDGDependenceType.forBranchCondition(branchCondition);

        Set<IRBlock> visited = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(startBlock);

        while (!worklist.isEmpty()) {
            IRBlock current = worklist.poll();
            if (visited.contains(current)) continue;
            if (current == lca) continue;

            visited.add(current);

            for (PDGNode node : pdg.getNodesInBlock(current)) {
                PDGEdge edge = new PDGEdge(controllingNode, node, edgeType,
                    null, null, branchCondition);
                pdg.addEdge(edge);
            }

            IRBlock postDom = postDomTree.getImmediatePostDominator(current);
            if (postDom != null && postDom != current && postDom != lca) {
                worklist.add(postDom);
            }
        }
    }

    private IRBlock findLCA(IRBlock block1, IRBlock block2) {
        Set<IRBlock> ancestors = new HashSet<>();
        IRBlock runner = block1;
        while (runner != null) {
            ancestors.add(runner);
            IRBlock ipdom = postDomTree.getImmediatePostDominator(runner);
            if (ipdom == runner) break;
            runner = ipdom;
        }

        runner = block2;
        while (runner != null) {
            if (ancestors.contains(runner)) {
                return runner;
            }
            IRBlock ipdom = postDomTree.getImmediatePostDominator(runner);
            if (ipdom == runner) break;
            runner = ipdom;
        }

        return null;
    }

    private PDGNode getControllingNodeForBlock(IRBlock block) {
        PDGNode termNode = blockTerminatorNodes.get(block);
        if (termNode != null) {
            return termNode;
        }

        List<PDGNode> nodesInBlock = pdg.getNodesInBlock(block);
        return nodesInBlock.isEmpty() ? pdg.getEntryNode() : nodesInBlock.get(nodesInBlock.size() - 1);
    }

    private boolean determineBranchCondition(IRBlock branchBlock, IRBlock targetBlock) {
        IRInstruction terminator = branchBlock.getTerminator();
        if (terminator instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) terminator;
            return targetBlock == branch.getTrueTarget();
        }
        return true;
    }

    private void addEntryControlDependencies() {
        PDGRegionNode entry = pdg.getEntryNode();
        IRBlock entryBlock = method.getEntryBlock();

        if (entryBlock == null) return;

        for (PDGNode node : pdg.getNodesInBlock(entryBlock)) {
            if (!hasControlDependency(node)) {
                pdg.addEdge(new PDGEdge(entry, node, PDGDependenceType.CONTROL_UNCONDITIONAL));
            }
        }

        for (PDGNode node : pdg.getNodes()) {
            if (node != entry && node != pdg.getExitNode() && !hasControlDependency(node)) {
                pdg.addEdge(new PDGEdge(entry, node, PDGDependenceType.CONTROL_UNCONDITIONAL));
            }
        }
    }

    private boolean hasControlDependency(PDGNode node) {
        for (PDGEdge edge : node.getIncomingEdges()) {
            if (edge.isControlDependence()) {
                return true;
            }
        }
        return false;
    }

    private void computeDataDependencies() {
        Map<SSAValue, Set<IRInstruction>> uses = defUseChains.getUses();

        for (Map.Entry<SSAValue, Set<IRInstruction>> entry : uses.entrySet()) {
            SSAValue defValue = entry.getKey();
            Set<IRInstruction> useInstrs = entry.getValue();

            PDGNode sourceNode = pdg.getNodeForValue(defValue);
            if (sourceNode == null) continue;

            for (IRInstruction useInstr : useInstrs) {
                PDGNode targetNode = pdg.getNodeForInstruction(useInstr);
                if (targetNode == null || sourceNode.equals(targetNode)) continue;

                PDGDependenceType edgeType = determineDataEdgeType(useInstr, defValue);
                String label = null;

                if (useInstr instanceof PhiInstruction) {
                    PhiInstruction phi = (PhiInstruction) useInstr;
                    IRBlock sourceBlock = findPhiSourceBlock(phi, defValue);
                    if (sourceBlock != null) {
                        label = "B" + sourceBlock.getId();
                    }
                }

                PDGEdge edge = new PDGEdge(sourceNode, targetNode, edgeType, label, defValue, false);
                pdg.addEdge(edge);
            }
        }
    }

    @SuppressWarnings("unused")
    private PDGDependenceType determineDataEdgeType(IRInstruction useInstr, SSAValue value) {
        // TODO: use "value" for memory dependency analysis
        if (useInstr instanceof PhiInstruction) {
            return PDGDependenceType.DATA_PHI;
        }
        return PDGDependenceType.DATA_DEF_USE;
    }

    private IRBlock findPhiSourceBlock(PhiInstruction phi, SSAValue value) {
        Map<IRBlock, Value> incoming = phi.getIncomingValues();
        for (Map.Entry<IRBlock, Value> entry : incoming.entrySet()) {
            if (entry.getValue() == value) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void handleExceptionEdges() {
        for (IRBlock block : method.getBlocks()) {
            for (IRBlock succ : block.getSuccessors()) {
                EdgeType edgeType = block.getEdgeType(succ);
                if (edgeType == EdgeType.EXCEPTION) {
                    PDGNode controllingNode = getControllingNodeForBlock(block);
                    if (controllingNode == null) continue;

                    for (PDGNode node : pdg.getNodesInBlock(succ)) {
                        PDGEdge edge = new PDGEdge(controllingNode, node,
                            PDGDependenceType.CONTROL_EXCEPTION);
                        pdg.addEdge(edge);
                    }
                }
            }
        }
    }
}
