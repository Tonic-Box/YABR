[<- SDG API](sdg-api.md) | [Back to Analysis APIs ->](analysis-apis.md)

# Code Property Graph (CPG) API

The CPG API (`com.tonic.analysis.cpg`) provides a unified graph representation combining AST, CFG, and PDG information for comprehensive program analysis and security vulnerability detection.

---

## Building a CPG

```java
import com.tonic.analysis.cpg.CodePropertyGraph;
import com.tonic.analysis.cpg.CPGBuilder;

// Basic CPG (CFG edges only)
CodePropertyGraph cpg = CPGBuilder.forClassPool(classPool).build();

// With call graph edges
CodePropertyGraph cpg = CPGBuilder.forClassPool(classPool)
    .withCallGraph()
    .build();

// With PDG (data/control dependencies)
CodePropertyGraph cpg = CPGBuilder.forClassPool(classPool)
    .withCallGraph()
    .withPDG()
    .build();

// With SDG (interprocedural dependencies)
CodePropertyGraph cpg = CPGBuilder.forClassPool(classPool)
    .withCallGraph()
    .withSDG()  // implies withPDG()
    .build();

System.out.println(cpg);  // CPG[1234 nodes, 5678 edges, 42 methods]
```

---

## Node Types

| Type | Description | Wraps |
|------|-------------|-------|
| `METHOD` | Method definition | IRMethod |
| `BLOCK` | Basic block | IRBlock |
| `INSTRUCTION` | IR instruction | IRInstruction |
| `CALL_SITE` | Method call | InvokeInstruction |

---

## Edge Types

### CFG Edges (Control Flow)
| Type | Description |
|------|-------------|
| `CFG_NEXT` | Sequential control flow |
| `CFG_TRUE` | True branch of conditional |
| `CFG_FALSE` | False branch of conditional |
| `CFG_EXCEPTION` | Exception handler edge |
| `CFG_BACK` | Loop back edge |

### Data Flow Edges
| Type | Description |
|------|-------------|
| `DATA_DEF` | Definition to use |
| `DATA_USE` | Use of definition |
| `REACHING_DEF` | Reaching definition |
| `TAINT` | Explicit taint propagation |

### Control Dependence Edges
| Type | Description |
|------|-------------|
| `CONTROL_DEP` | General control dependency |
| `CONTROL_DEP_TRUE` | True-branch control dependency |
| `CONTROL_DEP_FALSE` | False-branch control dependency |

### Interprocedural Edges
| Type | Description |
|------|-------------|
| `CALL` | Call site to target method |
| `PARAM_IN` | Parameter passed to callee |
| `PARAM_OUT` | Return value to caller |
| `RETURN_VALUE` | Method return |
| `SUMMARY` | Transitive summary edge |

### Structural Edges
| Type | Description |
|------|-------------|
| `CONTAINS` | Parent contains child |
| `AST_CHILD` | AST parent-child |

---

## Fluent Query API

The CPG provides a powerful fluent query API similar to graph query languages:

```java
import com.tonic.analysis.cpg.query.CPGQuery;

// Get all methods
List<CPGNode> methods = cpg.query().methods().toList();

// Get methods matching pattern
List<CPGNode> mains = cpg.query().methods(".*main.*").toList();

// Get specific method
Optional<CPGNode> method = cpg.query()
    .method("com/example/MyClass", "process", "(Ljava/lang/String;)V")
    .first();

// Get all call sites
List<CPGNode> calls = cpg.query().callSites().toList();

// Get calls to specific method
List<CPGNode> sqlCalls = cpg.query()
    .callsTo("java/sql/Statement", "executeQuery")
    .toList();
```

---

## Graph Traversal

```java
// Traverse outgoing edges
List<CPGNode> children = cpg.query()
    .methods()
    .out(CPGEdgeType.CONTAINS)  // Get contained blocks
    .toList();

// Traverse incoming edges
List<CPGNode> callers = cpg.query()
    .callSites()
    .in(CPGEdgeType.CALL)
    .toList();

// CFG traversal
List<CPGNode> reachable = cpg.query()
    .instructions()
    .filter(n -> isEntryPoint(n))
    .cfgReachable()
    .toList();

// Data flow traversal
List<CPGNode> dataFlowIn = cpg.query()
    .instructions()
    .dataFlowIn()
    .toList();

// Call graph traversal
List<CPGNode> transitiveCallees = cpg.query()
    .methods()
    .calleesTransitive()
    .toList();
```

---

## Filtering and Chaining

```java
// Filter by predicate
List<CPGNode> returns = cpg.query()
    .instructions()
    .filter(n -> n instanceof InstructionNode &&
                 ((InstructionNode) n).isReturn())
    .toList();

// Filter by node type
List<CPGNode> blocks = cpg.query()
    .all()
    .filterType(CPGNodeType.BLOCK)
    .toList();

// Filter by property
List<CPGNode> withName = cpg.query()
    .all()
    .hasProperty("name", "execute")
    .toList();

// Chain operations
List<CPGNode> sqlInjectionRisk = cpg.query()
    .callsTo("java/sql/Statement", "executeQuery")
    .in(CPGEdgeType.CALL)
    .dataFlowIn()
    .filter(n -> isUserInput(n))
    .toList();
```

---

## Terminal Operations

```java
// Collect to list
List<CPGNode> list = query.toList();

// Collect to set (unique)
Set<CPGNode> set = query.toSet();

// Get first result
Optional<CPGNode> first = query.first();

// Count results
long count = query.count();

// Check existence
boolean exists = query.exists();

// Iterate
query.forEach(node -> process(node));

// Map to values
List<String> names = query.map(n -> n.getLabel()).toList();
```

---

## Taint Analysis

```java
import com.tonic.analysis.cpg.taint.*;

TaintQuery taint = new TaintQuery(cpg)
    .withDefaultSources()   // HTTP params, file reads, etc.
    .withDefaultSinks()     // SQL exec, command exec, etc.
    .withDefaultSanitizers()
    .maxPathLength(50)
    .interprocedural(true);

TaintAnalysisResult result = taint.analyze();

System.out.println(result.getSummary());
// === Taint Analysis Summary ===
// Total paths found: 12
// Unsanitized: 3
// Sanitized: 9
// By Severity:
//   CRITICAL: 1
//   HIGH: 2

// Get detailed findings
for (TaintPath path : result.getCriticalPaths()) {
    System.out.println(path.formatPath());
}
```

### Custom Sources and Sinks

```java
TaintQuery taint = new TaintQuery(cpg)
    .addSource(TaintSource.builder()
        .name("Custom Input")
        .ownerPattern("com/example/Input")
        .methodPattern("get.*")
        .taintsReturnValue(true)
        .taintType(TaintType.USER_INPUT)
        .build())
    .addSink(TaintSink.builder()
        .name("Custom Sink")
        .ownerPattern("com/example/Dangerous")
        .methodPattern("execute.*")
        .vulnerabilityType(VulnerabilityType.CODE_INJECTION)
        .severity(Severity.CRITICAL)
        .build())
    .addSanitizer("com/example/Sanitizer", "sanitize.*");
```

---

## Printing CPGs

```java
import com.tonic.analysis.graph.print.CPGPrinter;
import com.tonic.analysis.graph.print.GraphPrinterConfig;

CPGPrinter printer = new CPGPrinter();
String output = printer.print(cpg);

// Verbose with properties
CPGPrinter verbose = new CPGPrinter(GraphPrinterConfig.verbose());

// Minimal summary
CPGPrinter minimal = new CPGPrinter(GraphPrinterConfig.minimal());
```

---

## DOT Export (Graphviz)

```java
import com.tonic.analysis.graph.export.CPGDOTExporter;

CPGDOTExporter exporter = new CPGDOTExporter();
String dot = exporter.export(cpg);

// CFG edges only
String cfgDot = new CPGDOTExporter().cfgOnly().export(cpg);

// Data flow only
String dataDot = new CPGDOTExporter().dataFlowOnly().export(cpg);

// Call graph only
String callDot = new CPGDOTExporter().callGraphOnly().export(cpg);

// Specific edge types
String customDot = new CPGDOTExporter()
    .includeEdgeTypes(CPGEdgeType.CFG_NEXT, CPGEdgeType.DATA_DEF)
    .export(cpg);
```

Render:
```bash
dot -Tpng cpg.dot -o cpg.png
dot -Tsvg cpg.dot -o cpg.svg
```

---

## Statistics

```java
// Basic counts
int nodeCount = cpg.getNodeCount();
int edgeCount = cpg.getEdgeCount();
int methodCount = cpg.getMethodCount();

// Edge type breakdown
Map<CPGEdgeType, Integer> edgeCounts = cpg.getEdgeTypeCounts();
for (var entry : edgeCounts.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}

// Specific edge type count
int cfgEdges = cpg.getEdgeCount(CPGEdgeType.CFG_NEXT);
```

---

## Key Classes

| Class | Description |
|-------|-------------|
| `CodePropertyGraph` | Main unified graph container |
| `CPGBuilder` | Fluent builder for CPG construction |
| `CPGIndex` | Fast lookup indexes |
| `CPGNode` | Abstract base node |
| `MethodNode` | Method definition node |
| `BlockNode` | Basic block node |
| `InstructionNode` | Instruction node |
| `CallSiteNode` | Method call node |
| `CPGEdge` | Edge with properties |
| `CPGEdgeType` | Enum of all edge types |
| `CPGQuery` | Fluent query builder |
| `TaintQuery` | Taint analysis queries |
| `TaintSource` | Source specification |
| `TaintSink` | Sink specification |
| `TaintPath` | Vulnerability path |
| `TaintAnalysisResult` | Analysis results |
| `CPGPrinter` | Text output |
| `CPGDOTExporter` | Graphviz export |

---

[<- SDG API](sdg-api.md) | [Back to Analysis APIs ->](analysis-apis.md)
