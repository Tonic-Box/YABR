[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [SSA Transforms ->](ssa-transforms.md)

# Analysis APIs

YABR provides ten high-level analysis APIs that build on top of the SSA IR system. Each API can be used independently or combined for powerful semantic queries.

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

---

## Call Graph API

Build a graph of method invocations across all classes in a ClassPool.

```java
CallGraph cg = CallGraph.build(pool);
Set<MethodReference> callers = cg.getCallers(target);
Set<MethodReference> reachable = cg.getReachableFrom(entryPoints);
```

**Key features:** Caller/callee queries, reachability analysis, virtual call resolution, dead code detection.

[Full documentation →](call-graph-api.md)

---

## Dependency Analysis API

Track class-level dependencies by scanning constant pools.

```java
DependencyAnalyzer deps = new DependencyAnalyzer(pool);
Set<String> dependencies = deps.getDependencies("java/lang/String");
List<List<String>> cycles = deps.findCircularDependencies();
```

**Key features:** Dependency queries, transitive closures, circular dependency detection, dependency type filtering.

[Full documentation →](dependency-api.md)

---

## Type Inference API

Perform dataflow analysis to infer types and nullability states for SSA values.

```java
TypeInferenceAnalyzer analyzer = new TypeInferenceAnalyzer(irMethod);
analyzer.analyze();
Nullability nullability = analyzer.getNullability(value);
```

**Key features:** Nullability analysis, type state tracking, polymorphic type sets.

[Full documentation →](type-inference-api.md)

---

## Pattern Search API

High-level fluent interface for finding code patterns across the codebase.

```java
PatternSearch search = new PatternSearch(pool);
List<SearchResult> results = search.findMethodCalls("java/io/PrintStream", "println");
List<SearchResult> allocs = search.findAllocations("java/lang/StringBuilder");
```

**Key features:** Method call search, field access search, type operations, custom patterns, pattern composition.

[Full documentation →](pattern-search-api.md)

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

[Full documentation →](instrumentation-api.md)

---

## Cross-References (Xref) API

Track all references to and from classes, methods, and fields.

```java
XrefDatabase db = new XrefBuilder(pool).build();
Set<Xref> callers = db.getRefsToMethod(target);
Set<Xref> outgoing = db.getRefsFromMethod(source);
```

**Key features:** Incoming/outgoing reference queries, reference type filtering, unused code detection.

[Full documentation →](xref-api.md)

---

## Data Flow API

Build data flow graphs from SSA IR for taint analysis and value tracking.

```java
DataFlowGraph dfg = new DataFlowGraph(irMethod);
dfg.build();
Set<DataFlowNode> reachable = dfg.getReachableNodes(sourceNode);
```

**Key features:** Taint source/sink identification, flow path queries, taint propagation.

[Full documentation →](dataflow-api.md)

---

## Method Similarity API

Find duplicate and similar methods for de-obfuscation and pattern detection.

```java
MethodSimilarityAnalyzer analyzer = new MethodSimilarityAnalyzer(pool);
analyzer.buildIndex();
List<SimilarityResult> duplicates = analyzer.findDuplicates();
```

**Key features:** Multiple similarity metrics, duplicate detection, renamed copy detection, similarity groups.

[Full documentation →](similarity-api.md)

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

[Full documentation →](simulation-api.md)

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

[Full documentation →](execution-api.md)

---

## Demo Programs

Runnable demos are in `src/main/java/com/tonic/demo/`:

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
java -cp build/classes/java/main com.tonic.demo.CallGraphDemo
java -cp build/classes/java/main com.tonic.demo.DependencyDemo
java -cp build/classes/java/main com.tonic.demo.TypeInferenceDemo
java -cp build/classes/java/main com.tonic.demo.PatternSearchDemo
java -cp build/classes/java/main com.tonic.demo.InstrumentationDemo
java -cp build/classes/java/main com.tonic.demo.SimulationDemo
java -cp build/classes/java/main com.tonic.demo.ExecutionDemo
```

---

[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [SSA Transforms ->](ssa-transforms.md)
