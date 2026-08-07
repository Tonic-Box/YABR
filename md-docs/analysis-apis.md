[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [SSA Transforms ->](ssa-transforms.md)

# Analysis APIs

YABR provides fifteen high-level analysis APIs that build on top of the SSA IR system. Each API can be used independently or combined for powerful semantic queries.

---

## API Overview

| API | Package | Description | Guide |
|-----|---------|-------------|-------|
| **Call Graph** | `callgraph` | Method caller/callee relationships | [Details](call-graph-api.md) |
| **Dependency Analysis** | `dependency` | Class-level dependency tracking | [Details](dependency-api.md) |
| **Type Inference** | `typeinference` | Nullability and type state analysis | [Details](type-inference-api.md) |
| **Pattern Search** | `pattern` | Code pattern matching across the codebase | [Details](pattern-search-api.md) |
| **Instrumentation** | `instrumentation` | Bytecode instrumentation with hooks | [Details](instrumentation-api.md) |
| **Cross-References** | `xref` | Track all references to/from classes, methods, fields | [Details](xref-api.md) |
| **Data Flow** | `dataflow` | Build data flow graphs for taint analysis | [Details](dataflow-api.md) |
| **Method Similarity** | `similarity` | Find duplicate and similar methods | [Details](similarity-api.md) |
| **Simulation** | `simulation` | Abstract bytecode/IR simulation with metrics | [Details](simulation-api.md) |
| **Execution** | `execution` | Concrete bytecode execution and debugging | [Details](execution-api.md) |
| **Abstract Execution** | `absexec` | Operand-stack and local def-use over raw bytecode | [Details](abstract-execution-api.md) |
| **PDG** | `pdg` | Program Dependence Graph with slicing | [Details](pdg-api.md) |
| **SDG** | `pdg.sdg` | Interprocedural System Dependence Graph | [Details](sdg-api.md) |
| **CPG** | `cpg` | Code Property Graph with taint analysis | [Details](cpg-api.md) |
| **Query** | `query` | Composable query language for searching bytecode | [Details](query-api.md) |

---

## Call Graph API

Build a graph of method invocations across all classes in a ClassPool.

```java
CallGraph cg = CallGraph.build(pool);
Set<MethodReference> callers = cg.getCallers(target);
Set<MethodReference> reachable = cg.getReachableFrom(entryPoints);
```

**Key features:** Caller/callee queries, reachability analysis, virtual call resolution, dead code detection.

[Full documentation ->](call-graph-api.md)

---

## Dependency Analysis API

Track class-level dependencies by scanning constant pools.

```java
DependencyAnalyzer deps = new DependencyAnalyzer(pool);
Set<String> dependencies = deps.getDependencies("java/lang/String");
List<List<String>> cycles = deps.findCircularDependencies();
```

**Key features:** Dependency queries, transitive closures, circular dependency detection, dependency type filtering.

[Full documentation ->](dependency-api.md)

---

## Type Inference API

Perform dataflow analysis to infer types and nullability states for SSA values.

```java
TypeInferenceAnalyzer analyzer = new TypeInferenceAnalyzer(irMethod);
analyzer.analyze();
Nullability nullability = analyzer.getNullability(value);
```

**Key features:** Nullability analysis, type state tracking, polymorphic type sets.

[Full documentation ->](type-inference-api.md)

---

## Pattern Search API

High-level fluent interface for finding code patterns across the codebase.

```java
PatternSearch search = new PatternSearch(pool);
List<SearchResult> results = search.findMethodCalls("java/io/PrintStream", "println");
List<SearchResult> allocs = search.findAllocations("java/lang/StringBuilder");
```

**Key features:** Method call search, field access search, type operations, custom patterns, pattern composition.

[Full documentation ->](pattern-search-api.md)

---

## Instrumentation API

Fluent interface for adding hooks to bytecode at various instrumentation points.

```java
Instrumenter.forClass(classFile)
    .onMethodEntry()
        .callStatic("Profiler", "enter", "(Ljava/lang/String;)V")
        .withMethodName()
        .register()
    .apply();
```

**Key features:** Method entry/exit hooks, field write hooks, method call interception, annotation filtering.

[Full documentation ->](instrumentation-api.md)

---

## Cross-References (Xref) API

Track all references to and from classes, methods, and fields.

```java
XrefDatabase db = new XrefBuilder(pool).build();
Set<Xref> callers = db.getRefsToMethod(target);
Set<Xref> outgoing = db.getRefsFromMethod(source);
```

**Key features:** Incoming/outgoing reference queries, reference type filtering, unused code detection.

[Full documentation ->](xref-api.md)

---

## Data Flow API

Build data flow graphs from SSA IR for taint analysis and value tracking.

```java
DataFlowGraph dfg = new DataFlowGraph(irMethod);
dfg.build();
Set<DataFlowNode> reachable = dfg.getReachableNodes(sourceNode);
```

**Key features:** Taint source/sink identification, flow path queries, taint propagation.

[Full documentation ->](dataflow-api.md)

---

## Method Similarity API

Find duplicate and similar methods for de-obfuscation and pattern detection.

```java
MethodSimilarityAnalyzer analyzer = new MethodSimilarityAnalyzer(pool);
analyzer.buildIndex();
List<SimilarityResult> duplicates = analyzer.findDuplicates();
```

**Key features:** Multiple similarity metrics, duplicate detection, renamed copy detection, similarity groups.

[Full documentation ->](similarity-api.md)

---

## Simulation API

Abstract bytecode/IR execution simulation with metrics and value tracking.

```java
SimulationEngine engine = new SimulationEngine(ctx)
    .addListener(new StackOperationListener())
    .addListener(new AllocationListener());
SimulationResult result = engine.simulate(irMethod);
```

**Key features:** Stack operation tracking, allocation tracking, field access tracking, method call tracking, control flow tracking, value flow queries, inter-procedural simulation.

[Full documentation ->](simulation-api.md)

---

## Execution API

Concrete bytecode execution with full heap simulation and debugging support.

```java
BytecodeContext ctx = new BytecodeContext.Builder()
    .heapManager(new SimpleHeapManager())
    .classResolver(new ClassResolver(pool))
    .maxInstructions(100000)
    .build();

BytecodeEngine engine = new BytecodeEngine(ctx);
BytecodeResult result = engine.execute(method, ConcreteValue.intValue(42));

// Or debug interactively
DebugSession session = new DebugSession(ctx);
session.addBreakpoint(new Breakpoint("MyClass", "method", "()V", 10));
session.start(method);
DebugState state = session.stepOver();
```

**Key features:** Concrete value execution, mutable heap/stack, object simulation, native method handlers, breakpoints, step debugging, call stack inspection, full Java 11 support (invokedynamic, lambdas, string concatenation, constant dynamic, method handles).

[Full documentation ->](execution-api.md)

---

## Abstract Execution API

Path-exploring operand-stack and local-variable def-use over a single method's bytecode, without an abstract value domain.

```java
List<InsnContext> contexts = new ArrayList<>();
new Execution(method)
    .addVisitor(contexts::add)
    .run();

StackCtx operand = contexts.get(0).getPops().get(0);
Instruction producer = operand.getPushed().getInstruction();
```

**Key features:** Stack pop/push provenance, local store/read links, value resolution through store/load hops, per-path branch frames, intra-procedural scope, bounded loop termination with a configurable frame cap.

[Full documentation](abstract-execution-api.md)

---

## PDG (Program Dependence Graph) API

Build intraprocedural program dependence graphs combining control and data dependencies.

```java
PDG pdg = PDGBuilder.build(irMethod);
PDGSlicer slicer = new PDGSlicer(pdg);
SliceResult slice = slicer.backwardSlice(criterion);
```

**Key features:** Control dependencies via post-dominance, data dependencies via def-use chains, backward/forward slicing, chop computation, DOT export.

[Full documentation ->](pdg-api.md)

---

## SDG (System Dependence Graph) API

Build interprocedural dependence graphs for whole-program analysis.

```java
CallGraph callGraph = CallGraph.build(pool);
SDG sdg = SDGBuilder.build(callGraph, irMethods);
SDGSlicer slicer = new SDGSlicer(sdg);
SliceResult slice = slicer.contextSensitiveSlice(criterion);
```

**Key features:** Interprocedural slicing, summary edges, parameter passing edges, context-sensitive analysis, call graph integration.

[Full documentation ->](sdg-api.md)

---

## CPG (Code Property Graph) API

Unified graph combining CFG, PDG, and call graph for comprehensive analysis.

```java
CodePropertyGraph cpg = CPGBuilder.forClassPool(pool)
    .withCallGraph()
    .withPDG()
    .build();

List<TaintPath> vulns = new TaintQuery(cpg)
    .withDefaultSources()
    .withDefaultSinks()
    .analyze()
    .getPaths();
```

**Key features:** Fluent query API, taint analysis, data flow tracking, DOT export, unified traversal across CFG/PDG/call edges.

[Full documentation ->](cpg-api.md)

---

## Query API

Search loaded bytecode with a composable query language - structural matches, SSA/data-flow predicates, and instruction-sequence patterns - over a `ClassPool`.

```java
QueryService service = new QueryService(pool);
QueryService.QueryResult result = service.execute(
        "FIND methods WHERE HAS call WHERE (name == \"println\")",
        QueryService.QueryConfig.builder().timeBudgetMs(5000).build(),
        null);
List<QueryMatch> matches = result.results();
```

**Key features:** Textual `FIND ... WHERE ...` queries, quantifiers (`HAS`/`ALL`/`NONE`/`COUNT`), data flow (`flowsTo`/`flowsFrom`), `SEQUENCE` instruction patterns, class type-system predicates (`class.super`/`class.interfaces`, `class isSubtypeOf <type>`, `class.modifiers contains record`), positional `param(n).type`, navigable matches with bytecode-offset targets.

[Full documentation ->](query-api.md)

---

## Demo Programs

Runnable demos are in `examples/src/main/java/com/tonic/demo/`:

| Demo | Description |
|------|-------------|
| `CallGraphDemo.java` | Build and query call graph |
| `DependencyDemo.java` | Analyze class dependencies |
| `TypeInferenceDemo.java` | Run type inference analysis |
| `PatternSearchDemo.java` | Search for code patterns |
| `InstrumentationDemo.java` | Bytecode instrumentation |
| `SimulationDemo.java` | Bytecode simulation and metrics |
| `ExecutionDemo.java` | Concrete bytecode execution and debugging |

Run with:

```bash
java -cp examples/build/classes/java/main com.tonic.demo.CallGraphDemo
java -cp examples/build/classes/java/main com.tonic.demo.DependencyDemo
java -cp examples/build/classes/java/main com.tonic.demo.TypeInferenceDemo
java -cp examples/build/classes/java/main com.tonic.demo.PatternSearchDemo
java -cp examples/build/classes/java/main com.tonic.demo.InstrumentationDemo
java -cp examples/build/classes/java/main com.tonic.demo.SimulationDemo
java -cp examples/build/classes/java/main com.tonic.demo.ExecutionDemo
```

---

[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [SSA Transforms ->](ssa-transforms.md)
