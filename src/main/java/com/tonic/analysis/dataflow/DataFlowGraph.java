package com.tonic.analysis.dataflow;

import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * A data flow graph for a single method.
 * Built from SSA form using def-use chains.
 */
public class DataFlowGraph {

    private final IRMethod method;
    private final String methodName;
    private final List<DataFlowNode> nodes = new ArrayList<>();
    private final List<DataFlowEdge> edges = new ArrayList<>();

    // Indexes for fast lookup
    private final Map<SSAValue, DataFlowNode> valueToNode = new HashMap<>();
    private final Map<DataFlowNode, List<DataFlowEdge>> outgoingEdges = new HashMap<>();
    private final Map<DataFlowNode, List<DataFlowEdge>> incomingEdges = new HashMap<>();

    private int nextNodeId = 0;

    public DataFlowGraph(IRMethod method) {
        this.method = method;
        this.methodName = method.getName();
    }

    /**
     * Build the data flow graph from the IR method using def-use chains.
     */
    public void build() {
        // Compute def-use chains
        DefUseChains defUse = new DefUseChains(method);
        defUse.compute();

        // First pass: create nodes for all definitions
        for (IRBlock block : method.getBlocks()) {
            int instrIndex = 0;

            // Process phi instructions
            for (PhiInstruction phi : block.getPhiInstructions()) {
                createNodeForInstruction(phi, block.getId(), instrIndex);
                instrIndex++;
            }

            // Process regular instructions
            for (IRInstruction instr : block.getInstructions()) {
                createNodeForInstruction(instr, block.getId(), instrIndex);
                instrIndex++;
            }
        }

        // Second pass: create edges for def-use relationships
        for (Map.Entry<SSAValue, Set<IRInstruction>> entry : defUse.getUses().entrySet()) {
            SSAValue defValue = entry.getKey();
            Set<IRInstruction> uses = entry.getValue();

            DataFlowNode sourceNode = valueToNode.get(defValue);
            if (sourceNode == null) continue;

            for (IRInstruction useInstr : uses) {
                DataFlowNode targetNode = findNodeForInstruction(useInstr);
                if (targetNode != null && !sourceNode.equals(targetNode)) {
                    DataFlowEdgeType edgeType = determineEdgeType(useInstr, defValue);
                    addEdge(sourceNode, targetNode, edgeType);
                }
            }
        }
    }

    private void createNodeForInstruction(IRInstruction instr, int blockId, int instrIndex) {
        SSAValue result = instr.getResult();
        if (result == null) {
            // Instructions without results (stores, returns) - create sink nodes
            if (instr instanceof PutFieldInstruction) {
                createSinkNode(instr, DataFlowNodeType.FIELD_STORE, blockId, instrIndex);
            } else if (instr instanceof ReturnInstruction) {
                ReturnInstruction ret = (ReturnInstruction) instr;
                if (!ret.isVoidReturn()) {
                    createSinkNode(instr, DataFlowNodeType.RETURN, blockId, instrIndex);
                }
            }
            return;
        }

        DataFlowNodeType type = determineNodeType(instr);
        String name = result.getName();
        String description = instr.toString();

        DataFlowNode node = DataFlowNode.builder()
            .id(nextNodeId++)
            .type(type)
            .name(name)
            .description(description)
            .ssaValue(result)
            .instruction(instr)
            .location(blockId, instrIndex)
            .build();

        nodes.add(node);
        valueToNode.put(result, node);
        outgoingEdges.put(node, new ArrayList<>());
        incomingEdges.put(node, new ArrayList<>());
    }

    private void createSinkNode(IRInstruction instr, DataFlowNodeType type, int blockId, int instrIndex) {
        String name = type.getDisplayName();
        if (instr instanceof PutFieldInstruction) {
            name = "store→" + ((PutFieldInstruction) instr).getName();
        } else if (instr instanceof ReturnInstruction) {
            name = "return";
        }

        DataFlowNode node = DataFlowNode.builder()
            .id(nextNodeId++)
            .type(type)
            .name(name)
            .description(instr.toString())
            .instruction(instr)
            .location(blockId, instrIndex)
            .build();

        nodes.add(node);
        outgoingEdges.put(node, new ArrayList<>());
        incomingEdges.put(node, new ArrayList<>());

        // Create edges from operands to this sink
        for (Value operand : instr.getOperands()) {
            if (operand instanceof SSAValue) {
                DataFlowNode sourceNode = valueToNode.get(operand);
                if (sourceNode != null) {
                    DataFlowEdgeType edgeType = (type == DataFlowNodeType.FIELD_STORE) ?
                        DataFlowEdgeType.FIELD_STORE : DataFlowEdgeType.DEF_USE;
                    addEdge(sourceNode, node, edgeType);
                }
            }
        }
    }

    private DataFlowNodeType determineNodeType(IRInstruction instr) {
        if (instr instanceof PhiInstruction) {
            return DataFlowNodeType.PHI;
        } else if (instr instanceof ConstantInstruction) {
            return DataFlowNodeType.CONSTANT;
        } else if (instr instanceof LoadLocalInstruction) {
            // Check if it's a parameter (slot 0-n in first block)
            return DataFlowNodeType.PARAM;
        } else if (instr instanceof InvokeInstruction) {
            return DataFlowNodeType.INVOKE_RESULT;
        } else if (instr instanceof GetFieldInstruction) {
            return DataFlowNodeType.FIELD_LOAD;
        } else if (instr instanceof ArrayLoadInstruction) {
            return DataFlowNodeType.ARRAY_LOAD;
        } else if (instr instanceof BinaryOpInstruction) {
            return DataFlowNodeType.BINARY_OP;
        } else if (instr instanceof UnaryOpInstruction) {
            return DataFlowNodeType.UNARY_OP;
        } else if (instr instanceof CastInstruction) {
            return DataFlowNodeType.CAST;
        } else if (instr instanceof NewInstruction) {
            return DataFlowNodeType.NEW_OBJECT;
        } else {
            return DataFlowNodeType.LOCAL;
        }
    }

    private DataFlowEdgeType determineEdgeType(IRInstruction useInstr, SSAValue value) {
        if (useInstr instanceof PhiInstruction) {
            return DataFlowEdgeType.PHI_INPUT;
        } else if (useInstr instanceof InvokeInstruction) {
            return DataFlowEdgeType.CALL_ARG;
        } else if (useInstr instanceof PutFieldInstruction) {
            return DataFlowEdgeType.FIELD_STORE;
        } else if (useInstr instanceof ArrayStoreInstruction) {
            return DataFlowEdgeType.ARRAY_STORE;
        } else if (useInstr instanceof BinaryOpInstruction ||
                   useInstr instanceof UnaryOpInstruction) {
            return DataFlowEdgeType.OPERAND;
        }
        return DataFlowEdgeType.DEF_USE;
    }

    private DataFlowNode findNodeForInstruction(IRInstruction instr) {
        SSAValue result = instr.getResult();
        if (result != null) {
            return valueToNode.get(result);
        }
        // For sink instructions, find by instruction reference
        for (DataFlowNode node : nodes) {
            if (node.getInstruction() == instr) {
                return node;
            }
        }
        return null;
    }

    private void addEdge(DataFlowNode source, DataFlowNode target, DataFlowEdgeType type) {
        DataFlowEdge edge = new DataFlowEdge(source, target, type);
        edges.add(edge);
        outgoingEdges.get(source).add(edge);
        incomingEdges.get(target).add(edge);
    }

    // ==================== Query Methods ====================

    public List<DataFlowNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<DataFlowEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public DataFlowNode getNodeForValue(SSAValue value) {
        return valueToNode.get(value);
    }

    public List<DataFlowEdge> getOutgoingEdges(DataFlowNode node) {
        return outgoingEdges.getOrDefault(node, Collections.emptyList());
    }

    public List<DataFlowEdge> getIncomingEdges(DataFlowNode node) {
        return incomingEdges.getOrDefault(node, Collections.emptyList());
    }

    /**
     * Get all nodes that can be taint sources.
     */
    public List<DataFlowNode> getPotentialSources() {
        List<DataFlowNode> sources = new ArrayList<>();
        for (DataFlowNode node : nodes) {
            if (node.getType().canBeTaintSource()) {
                sources.add(node);
            }
        }
        return sources;
    }

    /**
     * Get all nodes that can be taint sinks.
     */
    public List<DataFlowNode> getPotentialSinks() {
        List<DataFlowNode> sinks = new ArrayList<>();
        for (DataFlowNode node : nodes) {
            if (node.getType().canBeTaintSink()) {
                sinks.add(node);
            }
        }
        return sinks;
    }

    /**
     * Get all nodes of a specific type.
     */
    public List<DataFlowNode> getNodesByType(DataFlowNodeType type) {
        List<DataFlowNode> result = new ArrayList<>();
        for (DataFlowNode node : nodes) {
            if (node.getType() == type) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Find all nodes reachable from a starting node (forward flow).
     */
    public Set<DataFlowNode> getReachableNodes(DataFlowNode start) {
        Set<DataFlowNode> reachable = new LinkedHashSet<>();
        Queue<DataFlowNode> worklist = new LinkedList<>();
        worklist.add(start);

        while (!worklist.isEmpty()) {
            DataFlowNode current = worklist.poll();
            if (!reachable.add(current)) continue;

            for (DataFlowEdge edge : getOutgoingEdges(current)) {
                worklist.add(edge.getTarget());
            }
        }

        return reachable;
    }

    /**
     * Find all nodes that flow into a target node (backward flow).
     */
    public Set<DataFlowNode> getFlowingIntoNodes(DataFlowNode target) {
        Set<DataFlowNode> flowing = new LinkedHashSet<>();
        Queue<DataFlowNode> worklist = new LinkedList<>();
        worklist.add(target);

        while (!worklist.isEmpty()) {
            DataFlowNode current = worklist.poll();
            if (!flowing.add(current)) continue;

            for (DataFlowEdge edge : getIncomingEdges(current)) {
                worklist.add(edge.getSource());
            }
        }

        return flowing;
    }

    public String getMethodName() {
        return methodName;
    }

    public IRMethod getMethod() {
        return method;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    @Override
    public String toString() {
        return "DataFlowGraph[" + methodName + ": " + nodes.size() + " nodes, " + edges.size() + " edges]";
    }
}
