[<- PDG API](pdg-api.md) | [CPG API ->](cpg-api.md)

# System Dependence Graph (SDG) API

The SDG API (`com.tonic.analysis.pdg.sdg`) provides interprocedural program dependence analysis for whole-program slicing and security analysis across method boundaries.

---

## Building an SDG

```java
import com.tonic.analysis.pdg.sdg.SDG;
import com.tonic.analysis.pdg.sdg.SDGBuilder;
import com.tonic.analysis.callgraph.CallGraph;

// Build call graph first
CallGraph callGraph = CallGraph.build(classPool);

// Build SDG from call graph
SDG sdg = SDGBuilder.build(callGraph);

System.out.println("Entry nodes: " + sdg.getEntryNodes().size());
System.out.println("Call nodes: " + sdg.getCallNodesCount());
System.out.println("Parameter edges: " + sdg.getParameterEdges().size());
System.out.println("Summary edges: " + sdg.getSummaryEdges().size());
```

---

## SDG Node Types

| Node Type | Description | Shape in DOT |
|-----------|-------------|--------------|
| `SDGEntryNode` | Method entry point | house (green) |
| `SDGCallNode` | Call site | box (purple) |
| `SDGFormalInNode` | Formal parameter at entry | ellipse (blue) |
| `SDGFormalOutNode` | Formal return at exit | ellipse (pink) |
| `SDGActualInNode` | Actual parameter at call | diamond (light blue) |
| `SDGActualOutNode` | Actual return at call | diamond (light pink) |

---

## Interprocedural Edge Types

| Type | Description |
|------|-------------|
| `PARAMETER_IN` | Actual-in to formal-in (call parameter passing) |
| `PARAMETER_OUT` | Formal-out to actual-out (return value passing) |
| `CALL` | Call node to entry node |
| `RETURN` | Exit to call site return |
| `SUMMARY` | Transitive dependency through called procedure |

Summary edges enable efficient slicing by summarizing the effect of called procedures.

---

## Querying the SDG

```java
import com.tonic.analysis.pdg.sdg.node.*;

// Get all entry nodes (one per method)
List<SDGEntryNode> entries = sdg.getEntryNodes();

// Get entry by method name
SDGEntryNode entry = sdg.getEntryNode("com/example/MyClass.myMethod(II)I");

// Get formal parameters for an entry
List<SDGFormalInNode> formalIns = sdg.getFormalIns(entry);
List<SDGFormalOutNode> formalOuts = sdg.getFormalOuts(entry);

// Get call sites within a method
List<SDGCallNode> calls = sdg.getCallNodes(entry);

// Get actual parameters for a call
List<SDGActualInNode> actualIns = sdg.getActualIns(call);
List<SDGActualOutNode> actualOuts = sdg.getActualOuts(call);

// Get all nodes
Collection<PDGNode> allNodes = sdg.getAllNodes();
```

---

## Interprocedural Slicing

```java
import com.tonic.analysis.pdg.sdg.slice.SDGSlicer;
import com.tonic.analysis.pdg.slice.SliceResult;

SDGSlicer slicer = new SDGSlicer(sdg);

// Backward slice from a node (context-sensitive)
PDGNode criterion = sdg.getEntryNode("com/example/Sink.execute(Ljava/lang/String;)V");
SliceResult slice = slicer.backwardSlice(criterion);

System.out.println("Slice includes " + slice.getSize() + " nodes");

// Forward slice
SliceResult forward = slicer.forwardSlice(sourceNode);

// Context-sensitive slicing respects calling context
// Nodes are only included if there's a valid calling path
```

---

## Understanding Summary Edges

Summary edges represent transitive dependencies through called procedures:

```
caller() {                    callee(x) {
  a = input();                  y = transform(x);
  b = callee(a);  ------>       return y;
  sink(b);                    }
}
```

The SDG creates:
1. `PARAMETER_IN` edge: `actual_in(a)` → `formal_in(x)`
2. Data flow in callee: `formal_in(x)` → `formal_out(return)`
3. `PARAMETER_OUT` edge: `formal_out(return)` → `actual_out(b)`
4. `SUMMARY` edge: `actual_in(a)` → `actual_out(b)` (transitive)

Summary edges allow efficient slicing without repeatedly analyzing callees.

---

## Printing SDGs

```java
import com.tonic.analysis.graph.print.SDGPrinter;
import com.tonic.analysis.graph.print.GraphPrinterConfig;

SDGPrinter printer = new SDGPrinter();
String output = printer.print(sdg);

// Verbose output with all details
SDGPrinter verbose = new SDGPrinter(GraphPrinterConfig.verbose());
System.out.println(verbose.print(sdg));
```

---

## DOT Export (Graphviz)

```java
import com.tonic.analysis.graph.export.SDGDOTExporter;
import com.tonic.analysis.graph.export.DOTExporterConfig;

SDGDOTExporter exporter = new SDGDOTExporter();
String dot = exporter.export(sdg);

// Save to file
Files.writeString(Path.of("sdg.dot"), dot);

// Cluster by method
SDGDOTExporter clustered = new SDGDOTExporter(DOTExporterConfig.builder()
    .clusterByMethod(true)
    .includeLegend(true)
    .build());
```

Render:
```bash
dot -Tpng sdg.dot -o sdg.png
```

---

## Integration with PDG

The SDG incorporates individual method PDGs:

```java
// Build PDG for a single method
IRMethod irMethod = ssa.lift(methodEntry);
PDG pdg = PDGBuilder.build(irMethod);

// SDGBuilder automatically creates PDGs for all methods
CallGraph callGraph = CallGraph.build(classPool);
SDG sdg = SDGBuilder.build(callGraph);

// SDG contains intraprocedural edges from PDGs
// plus interprocedural parameter/summary edges
```

---

## Key Classes

| Class | Description |
|-------|-------------|
| `SDG` | System Dependence Graph container |
| `SDGBuilder` | Constructs SDG from CallGraph |
| `SDGEntryNode` | Method entry point |
| `SDGCallNode` | Call site with actual parameters |
| `SDGFormalInNode` | Formal input parameter |
| `SDGFormalOutNode` | Formal output (return) |
| `SDGActualInNode` | Actual input at call |
| `SDGActualOutNode` | Actual output at call |
| `SDGSlicer` | Context-sensitive interprocedural slicing |
| `SDGPrinter` | Text-based SDG output |
| `SDGDOTExporter` | Graphviz DOT export |

---

[<- PDG API](pdg-api.md) | [CPG API ->](cpg-api.md)
