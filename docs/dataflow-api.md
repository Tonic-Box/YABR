[<- Back to Analysis APIs](analysis-apis.md)

# Data Flow API

The Data Flow API (`com.tonic.analysis.dataflow`) builds data flow graphs from SSA IR for taint analysis and value tracking.

---

## Building a Data Flow Graph

```java
import com.tonic.analysis.dataflow.DataFlowGraph;
import com.tonic.analysis.dataflow.DataFlowNode;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;

SSA ssa = new SSA(classFile.getConstPool());
IRMethod irMethod = ssa.lift(methodEntry);

DataFlowGraph dfg = new DataFlowGraph(irMethod);
dfg.build();

System.out.println("Nodes: " + dfg.getNodeCount());
System.out.println("Edges: " + dfg.getEdgeCount());
```

---

## Node Types

| Type | Description | Taint Source? | Taint Sink? |
|------|-------------|---------------|-------------|
| `PARAM` | Method parameter | Yes | No |
| `LOCAL` | Local variable definition | No | No |
| `CONSTANT` | Constant value | No | No |
| `PHI` | SSA phi node (merge point) | No | No |
| `INVOKE_RESULT` | Return value from method call | Yes | No |
| `FIELD_LOAD` | Value loaded from field | Yes | No |
| `ARRAY_LOAD` | Value loaded from array | Yes | No |
| `BINARY_OP` | Result of binary operation | No | No |
| `UNARY_OP` | Result of unary operation | No | No |
| `CAST` | Result of type cast | No | No |
| `NEW_OBJECT` | Newly created object | No | No |
| `RETURN` | Return value | No | Yes |
| `FIELD_STORE` | Value stored to field | No | Yes |
| `ARRAY_STORE` | Value stored to array | No | Yes |
| `INVOKE_ARG` | Argument passed to method | No | Yes |

---

## Querying Nodes

```java
import com.tonic.analysis.dataflow.DataFlowNodeType;

// Get all nodes by type
List<DataFlowNode> params = dfg.getNodesByType(DataFlowNodeType.PARAM);
List<DataFlowNode> invokes = dfg.getNodesByType(DataFlowNodeType.INVOKE_RESULT);

// Get potential taint sources (PARAM, FIELD_LOAD, INVOKE_RESULT)
List<DataFlowNode> sources = dfg.getPotentialSources();

// Get potential taint sinks (RETURN, FIELD_STORE, INVOKE_ARG)
List<DataFlowNode> sinks = dfg.getPotentialSinks();

// Find node for a specific SSA value
DataFlowNode node = dfg.getNodeForValue(ssaValue);
```

---

## Edge Types

| Type | Description |
|------|-------------|
| `DEF_USE` | Definition flows to use |
| `PHI_INPUT` | Value flows into phi node |
| `CALL_ARG` | Value passed as method argument |
| `CALL_RETURN` | Return value flows to caller |
| `FIELD_STORE` | Value stored to field |
| `FIELD_LOAD` | Value loaded from field |
| `ARRAY_STORE` | Value stored to array |
| `ARRAY_LOAD` | Value loaded from array |
| `OPERAND` | Value used as operation operand |

---

## Querying Flow Paths

```java
import com.tonic.analysis.dataflow.DataFlowEdge;

// Get edges flowing out of a node
List<DataFlowEdge> outgoing = dfg.getOutgoingEdges(node);

// Get edges flowing into a node
List<DataFlowEdge> incoming = dfg.getIncomingEdges(node);

// Get all nodes reachable from a source (forward flow)
Set<DataFlowNode> reachable = dfg.getReachableNodes(sourceNode);

// Get all nodes that flow into a target (backward flow)
Set<DataFlowNode> flowing = dfg.getFlowingIntoNodes(targetNode);
```

---

## Taint Analysis

```java
// Mark a node as tainted
DataFlowNode param0 = params.get(0);
param0.setTainted(true);
param0.setTaintSource("user_input");

// Propagate taint through the graph
for (DataFlowNode reachable : dfg.getReachableNodes(param0)) {
    reachable.setTainted(true);
    reachable.setTaintSource(param0.getTaintSource());
}

// Check if tainted data reaches any sinks
for (DataFlowNode sink : dfg.getPotentialSinks()) {
    if (sink.isTainted()) {
        System.out.println("TAINT WARNING: " + sink.getTaintSource() +
            " flows to " + sink.getName());
    }
}
```

---

## Example: Track Parameter Flow

```java
// Track where parameter 0 flows
DataFlowNode param = dfg.getNodesByType(DataFlowNodeType.PARAM).get(0);

System.out.println("Parameter " + param.getName() + " flows to:");
for (DataFlowNode target : dfg.getReachableNodes(param)) {
    System.out.println("  -> " + target.getType() + ": " + target.getName());
}
```

---

## Key Classes

| Class | Description |
|-------|-------------|
| `DataFlowGraph` | Main graph built from IRMethod |
| `DataFlowNode` | Node representing a value with taint tracking |
| `DataFlowEdge` | Edge representing data flow between nodes |
| `DataFlowNodeType` | Enum of node types (PARAM, LOCAL, etc.) |
| `DataFlowEdgeType` | Enum of edge types (DEF_USE, CALL_ARG, etc.) |

---

[<- Back to Analysis APIs](analysis-apis.md)
