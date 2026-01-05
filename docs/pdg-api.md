[<- Back to Analysis APIs](analysis-apis.md) | [SDG API ->](sdg-api.md)

# Program Dependence Graph (PDG) API

The PDG API (`com.tonic.analysis.pdg`) provides intraprocedural program dependence analysis combining control and data dependencies for program slicing and security analysis.

---

## Building a PDG

```java
import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.PDGBuilder;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;

SSA ssa = new SSA(classFile.getConstPool());
IRMethod irMethod = ssa.lift(methodEntry);

PDG pdg = PDGBuilder.build(irMethod);

System.out.println("Nodes: " + pdg.getNodeCount());
System.out.println("Edges: " + pdg.getEdgeCount());
```

---

## Node Types

| Type | Description | Shape in DOT |
|------|-------------|--------------|
| `ENTRY` | Method entry point | ellipse (green) |
| `EXIT` | Method exit point | ellipse (pink) |
| `INSTRUCTION` | IR instruction | box (white) |
| `PHI` | SSA phi instruction | octagon (orange) |
| `CALL_SITE` | Method call | box (purple) |
| `BRANCH` | Conditional branch | diamond (blue) |

---

## Dependence Types

| Type | Category | Description |
|------|----------|-------------|
| `CONTROL_TRUE` | Control | True branch of conditional |
| `CONTROL_FALSE` | Control | False branch of conditional |
| `CONTROL_UNCONDITIONAL` | Control | Unconditional control dependency |
| `CONTROL_EXCEPTION` | Control | Exception handler dependency |
| `CONTROL_SWITCH` | Control | Switch case dependency |
| `DATA_DEF_USE` | Data | Definition to use chain |
| `DATA_PHI` | Data | Phi operand dependency |
| `DATA_ANTI` | Data | Anti-dependency (write-after-read) |
| `DATA_OUTPUT` | Data | Output dependency (write-after-write) |

---

## Querying Nodes

```java
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;

// Get entry and exit nodes
PDGNode entry = pdg.getEntryNode();
PDGNode exit = pdg.getExitNode();

// Get nodes by type
List<PDGNode> instructions = pdg.getNodesOfType(PDGNodeType.INSTRUCTION);
List<PDGNode> branches = pdg.getNodesOfType(PDGNodeType.BRANCH);

// Get node for specific instruction
PDGNode node = pdg.getInstructionNode(irInstruction);

// Get all nodes
Collection<PDGNode> allNodes = pdg.getNodes();
```

---

## Querying Edges

```java
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.edge.PDGDependenceType;

// Get all edges
Collection<PDGEdge> allEdges = pdg.getEdges();

// Filter edges by type
List<PDGEdge> controlEdges = allEdges.stream()
    .filter(e -> e.getType().isControlDependency())
    .collect(Collectors.toList());

List<PDGEdge> dataEdges = allEdges.stream()
    .filter(e -> e.getType().isDataDependency())
    .collect(Collectors.toList());

// Get incoming/outgoing edges for a node
List<PDGEdge> incoming = node.getIncomingEdges();
List<PDGEdge> outgoing = node.getOutgoingEdges();
```

---

## Program Slicing

```java
import com.tonic.analysis.pdg.slice.PDGSlicer;
import com.tonic.analysis.pdg.slice.SliceResult;

PDGSlicer slicer = new PDGSlicer(pdg);

// Backward slice (what affects this node?)
PDGNode criterion = pdg.getInstructionNode(returnInstr);
SliceResult backward = slicer.backwardSlice(criterion);

System.out.println("Backward slice contains " + backward.getSize() + " nodes");
for (PDGNode n : backward.getNodes()) {
    System.out.println("  " + n);
}

// Forward slice (what does this node affect?)
PDGNode param = pdg.getNodesOfType(PDGNodeType.ENTRY).get(0);
SliceResult forward = slicer.forwardSlice(param);

// Chop (intersection of backward from sink and forward from source)
SliceResult chop = slicer.chop(source, sink);

// Multi-criterion slice
SliceResult multi = slicer.backwardSlice(Set.of(node1, node2, node3));
```

---

## Taint Tracking

```java
// Mark a node as tainted
PDGNode paramNode = pdg.getNodesOfType(PDGNodeType.INSTRUCTION).get(0);
paramNode.setTainted(true);

// Check if node is tainted
if (node.isTainted()) {
    System.out.println("Node is tainted");
}

// Propagate taint using slicing
PDGSlicer slicer = new PDGSlicer(pdg);
SliceResult affected = slicer.forwardSlice(paramNode);

for (PDGNode n : affected.getNodes()) {
    n.setTainted(true);
}
```

---

## Printing PDGs

```java
import com.tonic.analysis.graph.print.PDGPrinter;
import com.tonic.analysis.graph.print.GraphPrinterConfig;

// Default printer
PDGPrinter printer = new PDGPrinter();
String output = printer.print(pdg);

// Verbose printer
PDGPrinter verbose = new PDGPrinter(GraphPrinterConfig.verbose());
System.out.println(verbose.print(pdg));

// Minimal printer
PDGPrinter minimal = new PDGPrinter(GraphPrinterConfig.minimal());
```

---

## DOT Export (Graphviz)

```java
import com.tonic.analysis.graph.export.PDGDOTExporter;
import com.tonic.analysis.graph.export.DOTExporterConfig;

// Export to DOT format
PDGDOTExporter exporter = new PDGDOTExporter();
String dot = exporter.export(pdg);

// Save to file
Files.writeString(Path.of("pdg.dot"), dot);

// With custom config
PDGDOTExporter custom = new PDGDOTExporter(DOTExporterConfig.builder()
    .includeLegend(true)
    .showNodeIds(true)
    .rankDir("LR")
    .build());
```

Render with Graphviz:
```bash
dot -Tpng pdg.dot -o pdg.png
```

---

## Key Classes

| Class | Description |
|-------|-------------|
| `PDG` | Main PDG container |
| `PDGBuilder` | Constructs PDG from IRMethod |
| `PDGNode` | Abstract base node class |
| `PDGInstructionNode` | Node wrapping an IRInstruction |
| `PDGRegionNode` | Entry/Exit region node |
| `PDGEdge` | Dependence edge |
| `PDGDependenceType` | Enum of edge types |
| `PDGNodeType` | Enum of node types |
| `PDGSlicer` | Slicing algorithms |
| `SliceResult` | Result container for slices |
| `PDGPrinter` | Text-based PDG output |
| `PDGDOTExporter` | Graphviz DOT export |

---

[<- Back to Analysis APIs](analysis-apis.md) | [SDG API ->](sdg-api.md)
