[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [SSA Transforms ->](ssa-transforms.md)

# Analysis APIs

YABR provides eight high-level analysis APIs that build on top of the SSA IR system:

1. **Call Graph** - Method caller/callee relationships
2. **Dependency Analysis** - Class-level dependency tracking
3. **Type Inference** - Nullability and type state analysis
4. **Pattern Search** - Code pattern matching across the codebase
5. **Instrumentation** - Bytecode instrumentation with hooks
6. **Cross-References** - Track all references to/from classes, methods, and fields
7. **Data Flow** - Build data flow graphs for taint analysis
8. **Method Similarity** - Find duplicate and similar methods

These APIs can be used independently or combined for powerful semantic queries.

---

## Call Graph API

The Call Graph API (`com.tonic.analysis.callgraph`) builds a graph of method invocations across all classes in a ClassPool.

### Building a Call Graph

```java
import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.callgraph.MethodReference;
import com.tonic.parser.ClassPool;

ClassPool pool = ClassPool.getDefault();
CallGraph cg = CallGraph.build(pool);

System.out.println(cg);  // CallGraph{methods=69089, edges=5787464}
```

### Querying Callers and Callees

```java
// Find all methods that call String.equals
MethodReference target = new MethodReference(
    "java/lang/String",
    "equals",
    "(Ljava/lang/Object;)Z"
);

Set<MethodReference> callers = cg.getCallers(target);
System.out.println("Methods calling String.equals: " + callers.size());

// Find all methods called by a specific method
MethodReference caller = new MethodReference(
    "com/example/MyClass",
    "process",
    "(Ljava/lang/String;)V"
);

Set<MethodReference> callees = cg.getCallees(caller);
for (MethodReference callee : callees) {
    System.out.println("  -> " + callee.getOwner() + "." + callee.getName());
}
```

### Reachability Analysis

```java
// Find all methods reachable from entry points
Set<MethodReference> entryPoints = Set.of(
    new MethodReference("com/example/Main", "main", "([Ljava/lang/String;)V")
);

Set<MethodReference> reachable = cg.getReachableFrom(entryPoints);
System.out.println("Reachable methods: " + reachable.size());

// Find dead methods (not reachable from entry points)
Set<MethodReference> allMethods = cg.getAllMethods();
Set<MethodReference> dead = new HashSet<>(allMethods);
dead.removeAll(reachable);
System.out.println("Dead methods: " + dead.size());
```

### Virtual Call Resolution

```java
// Resolve possible targets of a virtual call
Set<MethodReference> targets = cg.resolveVirtualTargets(
    "java/util/List",  // declared type
    "add",
    "(Ljava/lang/Object;)Z"
);
// Returns ArrayList.add, LinkedList.add, etc.
```

### Call Graph Components

| Class | Description |
|-------|-------------|
| `MethodReference` | Identifies a method (owner, name, descriptor) |
| `CallSite` | A call location with invoke type (VIRTUAL, STATIC, etc.) |
| `CallGraphNode` | Node with incoming/outgoing call edges |
| `CallGraph` | Main API for querying the graph |
| `CallGraphBuilder` | Builds the graph by scanning SSA IR |

---

## Dependency Analysis API

The Dependency Analysis API (`com.tonic.analysis.dependency`) tracks class-level dependencies by scanning constant pools.

### Building a Dependency Graph

```java
import com.tonic.analysis.dependency.DependencyAnalyzer;
import com.tonic.parser.ClassPool;

ClassPool pool = ClassPool.getDefault();
DependencyAnalyzer deps = new DependencyAnalyzer(pool);

System.out.println(deps);  // DependencyAnalyzer{classes=7517, inPool=7516, edges=174566}
```

### Querying Dependencies

```java
// What classes does String depend on?
Set<String> dependencies = deps.getDependencies("java/lang/String");
System.out.println("String depends on " + dependencies.size() + " classes");

// What classes depend on String?
Set<String> dependents = deps.getDependents("java/lang/String");
System.out.println(dependents.size() + " classes depend on String");

// Transitive dependencies (full closure)
Set<String> transitive = deps.getTransitiveDependencies("com/example/MyClass");
```

### Circular Dependency Detection

```java
List<List<String>> cycles = deps.findCircularDependencies();

if (cycles.isEmpty()) {
    System.out.println("No circular dependencies found");
} else {
    for (List<String> cycle : cycles) {
        System.out.println("Cycle: " + String.join(" -> ", cycle));
    }
}
```

### Dependency Types

The `DependencyType` enum categorizes dependencies:

| Type | Description |
|------|-------------|
| `EXTENDS` | Superclass relationship |
| `IMPLEMENTS` | Interface implementation |
| `FIELD_TYPE` | Type of a field |
| `PARAMETER_TYPE` | Method parameter type |
| `RETURN_TYPE` | Method return type |
| `METHOD_CALL` | Method invocation target |
| `FIELD_ACCESS` | Field read/write target |
| `CLASS_LITERAL` | Class literal reference |
| `EXCEPTION` | Catch/throws clause |
| `ANNOTATION` | Annotation type |

### Filtering by Dependency Type

```java
DependencyNode node = deps.getNode("com/example/MyClass");

// Get only inheritance dependencies
Set<String> superTypes = node.getDependenciesByType(DependencyType.EXTENDS);
Set<String> interfaces = node.getDependenciesByType(DependencyType.IMPLEMENTS);

// Get all method call targets
Set<String> callTargets = node.getDependenciesByType(DependencyType.METHOD_CALL);
```

### Finding Special Classes

```java
// Classes with no dependencies (leaf classes)
Set<String> leaves = deps.findLeafClasses();

// Classes with no dependents (root classes)
Set<String> roots = deps.findRootClasses();

// Classes in a specific package
Set<String> packageClasses = deps.getClassesInPackage("com/example/");
```

---

## Type Inference API

The Type Inference API (`com.tonic.analysis.typeinference`) performs dataflow analysis to infer types and nullability states for SSA values.

### Running Type Inference

```java
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.typeinference.TypeInferenceAnalyzer;
import com.tonic.analysis.typeinference.Nullability;
import com.tonic.analysis.typeinference.TypeState;

SSA ssa = new SSA(classFile.getConstPool());
IRMethod irMethod = ssa.lift(methodEntry);

TypeInferenceAnalyzer analyzer = new TypeInferenceAnalyzer(irMethod);
analyzer.analyze();
```

### Querying Nullability

```java
// Check nullability of a value
SSAValue value = instruction.getResult();
Nullability nullability = analyzer.getNullability(value);

if (nullability.isDefinitelyNull()) {
    System.out.println(value.getName() + " is always null");
} else if (nullability.isDefinitelyNotNull()) {
    System.out.println(value.getName() + " is never null");
} else {
    System.out.println(value.getName() + " may be null");
}

// Convenience methods
boolean mayBeNull = analyzer.isDefinitelyNull(value);
boolean safe = analyzer.isDefinitelyNotNull(value);
```

### Nullability States

| State | Description |
|-------|-------------|
| `NULL` | Value is definitely null |
| `NOT_NULL` | Value is definitely not null |
| `UNKNOWN` | Value may be null or non-null |
| `BOTTOM` | No information (unreachable code) |

### Getting Type Information

```java
// Get inferred type
IRType type = analyzer.getInferredType(value);

// Get full type state (type + nullability)
TypeState state = analyzer.getTypeState(value);
System.out.println(value.getName() + ": " + state);
// Output: v3: java/lang/String?  (nullable String)
// Output: v5: java/lang/StringBuilder!  (non-null StringBuilder)

// Check if type is precisely known (exact type, not subtype)
boolean precise = analyzer.hasPreciseType(value);
```

### Type State at Control Flow Points

```java
// Get type state at block entry
TypeState entryState = analyzer.getTypeStateAtBlockEntry(block, value);

// Get type state at block exit
TypeState exitState = analyzer.getTypeStateAtBlockExit(block, value);
```

### Collecting Values by Nullability

```java
// Get all definitely-null values
Set<SSAValue> nullValues = analyzer.getNullValues();

// Get all definitely-non-null values
Set<SSAValue> nonNullValues = analyzer.getNonNullValues();

// Get all type states
Map<SSAValue, TypeState> allStates = analyzer.getAllTypeStates();
```

### Polymorphic Types

For polymorphic receivers (e.g., interface calls), the analyzer tracks multiple possible types:

```java
TypeSet possibleTypes = analyzer.getPossibleTypes(receiver);

if (possibleTypes.isSingleton()) {
    IRType exactType = possibleTypes.getSingleType();
} else {
    System.out.println("Possible types: " + possibleTypes);
    // Output: {ArrayList, LinkedList, Vector}
}
```

---

## Pattern Search API

The Pattern Search API (`com.tonic.analysis.pattern`) provides a high-level fluent interface for finding code patterns across the codebase.

### Basic Usage

```java
import com.tonic.analysis.pattern.PatternSearch;
import com.tonic.analysis.pattern.Patterns;
import com.tonic.analysis.pattern.SearchResult;

ClassPool pool = ClassPool.getDefault();
ClassFile stringClass = pool.get("java/lang/String");

PatternSearch search = new PatternSearch(pool);

// Search in all methods of a class
search.inAllMethodsOf(stringClass);

// Limit results
search.limit(100);
```

### Finding Method Calls

```java
// Find all calls to System.out.println
List<SearchResult> results = search.findMethodCalls(
    "java/io/PrintStream",
    "println"
);

// Find all calls to any method on a class
List<SearchResult> results = search.findMethodCalls("java/lang/StringBuilder");

// Find calls using a custom pattern
List<SearchResult> results = search.findPattern(
    Patterns.methodCallNameMatching("get.*")  // Regex pattern
);
```

### Finding Field Accesses

```java
// Find all field accesses on a class
List<SearchResult> reads = search.findFieldAccesses("java/lang/System");

// Find accesses to a specific field by name
List<SearchResult> results = search.findFieldsByName("out");
```

### Finding Type Operations

```java
// Find all instanceof checks
List<SearchResult> checks = search.findInstanceOfChecks();

// Find instanceof for a specific type
List<SearchResult> checks = search.findInstanceOfChecks("java/lang/String");

// Find all type casts
List<SearchResult> casts = search.findCasts();

// Find casts to a specific type
List<SearchResult> casts = search.findCastsTo("java/util/List");
```

### Finding Object Allocations

```java
// Find all object allocations
List<SearchResult> allocs = search.findAllocations();

// Find allocations of a specific class
List<SearchResult> allocs = search.findAllocations("java/lang/StringBuilder");
```

### Finding Control Flow Patterns

```java
// Find null checks (if x == null / if x != null)
List<SearchResult> nullChecks = search.findNullChecks();

// Find throw statements
List<SearchResult> throws_ = search.findThrows();
```

### Combining Patterns

```java
// AND: Match instructions matching ALL patterns
PatternMatcher staticStringCall = Patterns.and(
    Patterns.staticMethodCall(),
    Patterns.methodCallTo("java/lang/String")
);

// OR: Match instructions matching ANY pattern
PatternMatcher allocation = Patterns.or(
    Patterns.anyNew(),
    Patterns.anyNewArray()
);

// NOT: Negate a pattern
PatternMatcher nonStaticCall = Patterns.not(Patterns.staticMethodCall());

List<SearchResult> results = search.findPattern(staticStringCall);
```

### Custom Patterns

```java
// Create a custom pattern with a lambda
List<SearchResult> exceptionAllocs = search.findPattern(
    (instr, method, sourceMethod, classFile) -> {
        if (!(instr instanceof NewInstruction)) return false;
        String className = ((NewInstruction) instr).getClassName();
        return className != null && className.endsWith("Exception");
    }
);
```

### Integration with Other APIs

The Pattern Search API can leverage Call Graph, Dependency Analysis, and Type Inference:

```java
PatternSearch search = new PatternSearch(pool)
    .withCallGraph()       // Build call graph for caller/callee queries
    .withDependencies()    // Build dependency graph
    .withTypeInference();  // Enable nullability-aware searches

// Find all callers of a method
List<SearchResult> callers = search.findCallersOf(
    "java/lang/String",
    "equals",
    "(Ljava/lang/Object;)Z"
);

// Find classes depending on a class
List<SearchResult> dependents = search.findDependentsOf("java/util/List");

// Find potential null pointer dereferences
List<SearchResult> nullDerefs = search.findPotentialNullDereferences();
```

### Search Results

Each `SearchResult` contains:

```java
for (SearchResult result : results) {
    // Location information
    String className = result.getClassName();
    String methodName = result.getMethodName();
    String location = result.getLocation();  // "com/example/Foo.bar(I)V @ 42"

    // The matched instruction (if applicable)
    IRInstruction instruction = result.getInstruction();

    // Description of the match
    String description = result.getDescription();

    System.out.println(result);  // Full formatted output
}
```

### Built-in Pattern Matchers

| Pattern | Description |
|---------|-------------|
| `anyMethodCall()` | Any method invocation |
| `methodCallTo(owner)` | Calls to methods on a specific class |
| `methodCall(owner, name)` | Calls to a specific method |
| `methodCallNameMatching(regex)` | Method names matching regex |
| `staticMethodCall()` | Static method calls only |
| `virtualMethodCall()` | Virtual/interface calls |
| `dynamicCall()` | Invokedynamic calls |
| `anyFieldRead()` | Any field read (getfield/getstatic) |
| `anyFieldWrite()` | Any field write (putfield/putstatic) |
| `fieldAccessOn(owner)` | Field access on specific class |
| `fieldNamed(name)` | Field access by name |
| `anyInstanceOf()` | Any instanceof check |
| `instanceOf(type)` | instanceof for specific type |
| `anyCast()` | Any checkcast |
| `castTo(type)` | Cast to specific type |
| `anyNew()` | Any object allocation |
| `newInstance(class)` | Allocation of specific class |
| `anyNewArray()` | Any array allocation |
| `nullCheck()` | Null comparison in branch |
| `anyThrow()` | Any throw statement |
| `anyReturn()` | Any return statement |

---

## Instrumentation API

The Instrumentation API (`com.tonic.analysis.instrumentation`) provides a fluent interface for adding hooks to bytecode at various instrumentation points.

### Basic Usage

```java
import com.tonic.analysis.instrumentation.Instrumenter;
import com.tonic.parser.ClassFile;

ClassFile classFile = new ClassFile(inputStream);

int points = Instrumenter.forClass(classFile)
    .onMethodEntry()
        .inPackage("com/example/service/")
        .callStatic("com/example/Hooks", "onEntry", "(Ljava/lang/String;)V")
        .withMethodName()
        .register()
    .apply();

System.out.println("Applied " + points + " instrumentation points");
```

### Method Entry/Exit Hooks

```java
Instrumenter.forClass(classFile)
    // Hook all method entries
    .onMethodEntry()
        .callStatic("com/example/Profiler", "onMethodEntry",
            "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V")
        .withClassName()
        .withMethodName()
        .withAllParameters()  // Pass params as Object[]
        .register()

    // Hook all method exits
    .onMethodExit()
        .callStatic("com/example/Profiler", "onMethodExit",
            "(Ljava/lang/String;Ljava/lang/Object;)V")
        .withMethodName()
        .withReturnValue()    // Boxed if primitive
        .register()

    .skipConstructors(true)
    .apply();
```

### Field Write Hooks

```java
Instrumenter.forClass(classFile)
    .onFieldWrite()
        .forField("balance")  // Or use pattern: "*Id"
        .callStatic("com/example/AuditLog", "onFieldChange",
            "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V")
        .withOwner()          // Object owning the field
        .withFieldName()      // Field name as String
        .withOldValue()       // Value before write
        .withNewValue()       // Value being written
        .register()
    .apply();
```

### Array Store Hooks

```java
Instrumenter.forClass(classFile)
    .onArrayStore()
        .inPackage("com/example/sensitive/")
        .forObjectArrays()    // Only Object[] arrays
        .callStatic("com/example/Security", "validateArrayStore",
            "(Ljava/lang/Object;ILjava/lang/Object;)V")
        .withArray()          // Array reference
        .withIndex()          // Index being written
        .withValue()          // Value being stored
        .register()
    .apply();
```

### Method Call Interception

```java
Instrumenter.forClass(classFile)
    // Hook calls to specific methods
    .onMethodCall()
        .targeting("java/sql/Connection", "prepareStatement")
        .before()
        .callStatic("com/example/DBHooks", "beforeQuery",
            "(Ljava/lang/Object;[Ljava/lang/Object;)V")
        .withReceiver()       // The Connection object
        .withArguments()      // Arguments as Object[]
        .register()

    // Hook after calls
    .onMethodCall()
        .targeting("java/io/PrintStream", "println")
        .after()
        .callStatic("com/example/IOHooks", "afterPrint",
            "(Ljava/lang/Object;)V")
        .withResult()         // Return value
        .register()
    .apply();
```

### Filtering by Annotation

```java
Instrumenter.forClass(classFile)
    .onMethodEntry()
        .withAnnotation("Ljavax/inject/Inject;")  // Only @Inject methods
        .callStatic("com/example/DIHooks", "onInject", "()V")
        .register()
    .apply();
```

### Instrumentation Report

```java
Instrumenter.InstrumentationReport report = Instrumenter.forClass(classFile)
    .onMethodEntry()
        .callStatic("Profiler", "enter", "(Ljava/lang/String;)V")
        .withMethodName()
        .register()
    .applyWithReport();

System.out.println("Classes: " + report.getClassesInstrumented());
System.out.println("Methods: " + report.getMethodsInstrumented());
System.out.println("Points:  " + report.getTotalInstrumentationPoints());
System.out.println("Errors:  " + report.getErrors());
```

### Configuration Options

| Option | Description |
|--------|-------------|
| `skipAbstract(true)` | Skip abstract methods (default: true) |
| `skipNative(true)` | Skip native methods (default: true) |
| `skipConstructors(true)` | Skip `<init>` methods |
| `skipStaticInitializers(true)` | Skip `<clinit>` methods |
| `skipSynthetic(true)` | Skip synthetic methods |
| `skipBridge(true)` | Skip bridge methods |
| `failOnError(true)` | Throw on instrumentation errors |
| `verbose(true)` | Log instrumentation progress |

### Hook Types

| Hook | Description | Builder |
|------|-------------|---------|
| Method Entry | At method start | `onMethodEntry()` |
| Method Exit | Before return | `onMethodExit()` |
| Field Write | Before PUTFIELD/PUTSTATIC | `onFieldWrite()` |
| Field Read | After GETFIELD/GETSTATIC | `onFieldRead()` |
| Array Store | Before AASTORE/IASTORE/etc. | `onArrayStore()` |
| Array Load | After AALOAD/IALOAD/etc. | `onArrayLoad()` |
| Method Call | Before/after INVOKEVIRTUAL/etc. | `onMethodCall()` |
| Exception | At exception handler entry | `onException()` |

### Filter Types

| Filter | Description |
|--------|-------------|
| `inClass(className)` | Exact class match |
| `inClassMatching(pattern)` | Wildcard class match |
| `inPackage(prefix)` | Package prefix match |
| `matchingMethod(pattern)` | Method name pattern |
| `forField(name)` | Field name match |
| `forFieldsMatching(pattern)` | Field name pattern |
| `ofType(descriptor)` | Field type filter |
| `withAnnotation(type)` | Annotation presence |
| `staticOnly()` | Static members only |
| `instanceOnly()` | Instance members only |

### Typical Use Cases

1. **Profiling** - Method entry/exit timing
2. **Logging** - Trace method calls
3. **Security** - Validate field writes
4. **Mocking** - Intercept external calls
5. **Coverage** - Track code execution
6. **Auditing** - Record data changes

---

## Cross-References (Xref) API

The Cross-References API (`com.tonic.analysis.xref`) tracks all references to and from classes, methods, and fields. It answers questions like "who calls this method?" and "what does this method call?"

### Building the Xref Database

```java
import com.tonic.analysis.xref.XrefBuilder;
import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.parser.ClassPool;

ClassPool pool = ClassPool.getDefault();
XrefBuilder builder = new XrefBuilder(pool);

// Optional: track progress
builder.setProgressCallback(msg -> System.out.println(msg));

XrefDatabase db = builder.build();
System.out.println(db.getSummary());
// Output: XrefDatabase{xrefs=125432, classes=7517, methods=45231, fields=12045}
```

### Querying Incoming References (Who References This?)

```java
import com.tonic.analysis.xref.Xref;
import com.tonic.analysis.xref.MethodReference;

// Find all references TO a class
Set<Xref> refsToString = db.getRefsToClass("java/lang/String");

// Find all callers of a method
MethodReference target = new MethodReference(
    "java/lang/String", "equals", "(Ljava/lang/Object;)Z"
);
Set<Xref> callers = db.getRefsToMethod(target);

for (Xref ref : callers) {
    System.out.println(ref.getSourceDisplay() + " -> " + ref.getTargetDisplay());
}

// Find all reads/writes to a field
Set<Xref> fieldRefs = db.getRefsToField("java/lang/System", "out", "Ljava/io/PrintStream;");
```

### Querying Outgoing References (What Does This Reference?)

```java
// Find all references FROM a class
Set<Xref> refsFromMyClass = db.getRefsFromClass("com/example/MyClass");

// Find all methods/fields referenced by a method
MethodReference source = new MethodReference(
    "com/example/MyClass", "process", "(Ljava/lang/String;)V"
);
Set<Xref> outgoing = db.getRefsFromMethod(source);

for (Xref ref : outgoing) {
    System.out.println("  -> " + ref.getType() + ": " + ref.getTargetDisplay());
}
```

### Reference Types (XrefType)

| Type | Description |
|------|-------------|
| `METHOD_CALL` | Direct method invocation |
| `METHOD_OVERRIDE` | Method that overrides a parent method |
| `FIELD_READ` | Read access to a field (getfield/getstatic) |
| `FIELD_WRITE` | Write access to a field (putfield/putstatic) |
| `CLASS_INSTANTIATE` | Object allocation (new) |
| `CLASS_CAST` | Type cast expression |
| `CLASS_INSTANCEOF` | instanceof type check |
| `CLASS_EXTENDS` | Superclass relationship |
| `CLASS_IMPLEMENTS` | Interface implementation |
| `CLASS_ANNOTATION` | Annotation applied to element |
| `TYPE_PARAMETER` | Generic type parameter usage |
| `TYPE_LOCAL_VAR` | Type used in local variable |

### Filtering by Reference Type

```java
import com.tonic.analysis.xref.XrefType;

// Get only method calls
Set<Xref> methodCalls = db.getRefsByType(XrefType.METHOD_CALL);

// Get all field reads
Set<Xref> fieldReads = db.getAllFieldReads();

// Get all instantiations
Set<Xref> instantiations = db.getAllInstantiations();

// Group incoming references by type
Map<XrefType, Set<Xref>> grouped = db.groupIncomingByType("com/example/MyClass");
for (Map.Entry<XrefType, Set<Xref>> entry : grouped.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue().size() + " refs");
}
```

### Searching References

```java
// Search for references by name pattern
Set<Xref> matching = db.searchIncomingRefs("equals");

// Find all callers of methods named "get*"
Set<Xref> getterCallers = db.findCallersOfMethodNamed("get");

// Find references between two specific classes
Set<Xref> between = db.findRefsBetweenClasses(
    "com/example/ServiceA",
    "com/example/ServiceB"
);
```

### Finding Unused Code

```java
// Find classes with no incoming references (potentially dead code)
Set<String> allClasses = db.getUniqueTargetClassCount() > 0
    ? /* your class list */ : Set.of();

for (String className : allClasses) {
    Set<Xref> incoming = db.getRefsToClass(className);
    if (incoming.isEmpty()) {
        System.out.println("Potentially unused: " + className);
    }
}

// Find which classes reference a given class
Set<String> dependents = db.getClassesReferencingClass("com/example/Util");
```

### Statistics

```java
// Database statistics
System.out.println("Total xrefs: " + db.getTotalXrefCount());
System.out.println("Unique target classes: " + db.getUniqueTargetClassCount());
System.out.println("Unique source classes: " + db.getUniqueSourceClassCount());
System.out.println("Unique target methods: " + db.getUniqueTargetMethodCount());
System.out.println("Unique target fields: " + db.getUniqueTargetFieldCount());

// Counts by type
Map<XrefType, Integer> byType = db.getXrefCountByType();
for (Map.Entry<XrefType, Integer> entry : byType.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}
```

### Key Classes

| Class | Description |
|-------|-------------|
| `XrefBuilder` | Builds XrefDatabase by scanning ClassPool |
| `XrefDatabase` | Indexed database with fast lookup methods |
| `Xref` | A single reference with source/target info |
| `XrefType` | Enum of 12 reference types |
| `MethodReference` | Method identifier (owner, name, descriptor) |
| `FieldReference` | Field identifier (owner, name, descriptor) |

---

## Data Flow API

The Data Flow API (`com.tonic.analysis.dataflow`) builds data flow graphs from SSA IR for taint analysis and value tracking.

### Building a Data Flow Graph

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

### Node Types

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

### Querying Nodes

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

### Edge Types

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

### Querying Flow Paths

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

### Taint Analysis

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

### Example: Track Parameter Flow

```java
// Track where parameter 0 flows
DataFlowNode param = dfg.getNodesByType(DataFlowNodeType.PARAM).get(0);

System.out.println("Parameter " + param.getName() + " flows to:");
for (DataFlowNode target : dfg.getReachableNodes(param)) {
    System.out.println("  -> " + target.getType() + ": " + target.getName());
}
```

### Key Classes

| Class | Description |
|-------|-------------|
| `DataFlowGraph` | Main graph built from IRMethod |
| `DataFlowNode` | Node representing a value with taint tracking |
| `DataFlowEdge` | Edge representing data flow between nodes |
| `DataFlowNodeType` | Enum of node types (PARAM, LOCAL, etc.) |
| `DataFlowEdgeType` | Enum of edge types (DEF_USE, CALL_ARG, etc.) |

---

## Method Similarity API

The Method Similarity API (`com.tonic.analysis.similarity`) finds duplicate and similar methods for de-obfuscation and pattern detection.

### Building the Method Index

```java
import com.tonic.analysis.similarity.MethodSimilarityAnalyzer;
import com.tonic.analysis.similarity.SimilarityMetric;
import com.tonic.analysis.similarity.SimilarityResult;
import com.tonic.parser.ClassPool;

ClassPool pool = ClassPool.getDefault();
MethodSimilarityAnalyzer analyzer = new MethodSimilarityAnalyzer(pool);

// Optional: track progress
analyzer.setProgressCallback(msg -> System.out.println(msg));

analyzer.buildIndex();
System.out.println("Indexed " + analyzer.getMethodCount() + " methods");
```

### Similarity Metrics

| Metric | Description | Weight |
|--------|-------------|--------|
| `EXACT_BYTECODE` | Byte-for-byte bytecode match (MD5 hash) | 1.0 |
| `OPCODE_SEQUENCE` | Same opcode sequence (ignores operands), uses LCS | 0.8 |
| `CONTROL_FLOW` | Isomorphic control flow graph structure | 0.7 |
| `STRUCTURAL` | Similar metrics (size, branches, calls) | 0.5 |
| `COMBINED` | Weighted combination of all metrics | 1.0 |

### Finding Similar Methods

```java
// Find all similar method pairs above threshold
List<SimilarityResult> similar = analyzer.findAllSimilar(
    SimilarityMetric.COMBINED,
    0.80  // 80% minimum similarity
);

for (SimilarityResult result : similar) {
    System.out.printf("%d%% similar: %s <-> %s%n",
        result.getScorePercent(),
        result.getMethod1().getDisplayName(),
        result.getMethod2().getDisplayName());
}
```

### Finding Exact Duplicates

```java
// Find methods that are >= 95% similar (near-duplicates)
List<SimilarityResult> duplicates = analyzer.findDuplicates();

System.out.println("Found " + duplicates.size() + " duplicate method pairs");

for (SimilarityResult dup : duplicates) {
    System.out.println(dup.getSummary());  // "Exact/Near duplicate (98%)"
}
```

### Finding Renamed Copies (Obfuscation Detection)

```java
// Find methods with same opcodes but different names (potential obfuscation)
List<SimilarityResult> renamed = analyzer.findRenamedCopies();

for (SimilarityResult result : renamed) {
    System.out.printf("Potential rename: %s -> %s (%.0f%% opcode match)%n",
        result.getMethod1().getDisplayName(),
        result.getMethod2().getDisplayName(),
        result.getScore(SimilarityMetric.OPCODE_SEQUENCE) * 100);
}
```

### Comparing Two Specific Methods

```java
import com.tonic.analysis.similarity.MethodSignature;

// Get signatures for two methods
MethodSignature sig1 = analyzer.getSignature(
    "com/example/ClassA", "process", "(I)V"
);
MethodSignature sig2 = analyzer.getSignature(
    "com/example/ClassB", "handle", "(I)V"
);

// Compare them
SimilarityResult result = analyzer.compare(sig1, sig2, SimilarityMetric.COMBINED);

System.out.println("Overall: " + result.getScorePercent() + "%");
System.out.println("Exact bytecode: " + result.getScore(SimilarityMetric.EXACT_BYTECODE));
System.out.println("Opcode sequence: " + result.getScore(SimilarityMetric.OPCODE_SEQUENCE));
System.out.println("Structural: " + result.getScore(SimilarityMetric.STRUCTURAL));
```

### Finding Methods Similar to a Target

```java
// Find all methods similar to a specific method
List<SimilarityResult> similar = analyzer.findSimilarTo(
    "com/example/MyClass",
    "myMethod",
    "(Ljava/lang/String;)V",
    SimilarityMetric.OPCODE_SEQUENCE,
    0.70  // 70% minimum
);

for (SimilarityResult result : similar) {
    System.out.println(result.getMethod2().getDisplayName() +
        ": " + result.getScorePercent() + "%");
}
```

### Finding Similarity Groups

```java
// Find groups of similar methods (connected components)
List<List<MethodSignature>> groups = analyzer.findSimilarityGroups(
    SimilarityMetric.COMBINED,
    0.85
);

System.out.println("Found " + groups.size() + " similarity groups");

for (List<MethodSignature> group : groups) {
    System.out.println("Group of " + group.size() + " methods:");
    for (MethodSignature sig : group) {
        System.out.println("  - " + sig.getDisplayName());
    }
}
```

### Method Signature Details

```java
MethodSignature sig = analyzer.getSignature("com/example/MyClass", "process", "(I)V");

// Fingerprint data
System.out.println("Instructions: " + sig.getInstructionCount());
System.out.println("Branches: " + sig.getBranchCount());
System.out.println("Loops: " + sig.getLoopCount());
System.out.println("Method calls: " + sig.getCallCount());
System.out.println("Field accesses: " + sig.getFieldAccessCount());
System.out.println("Max stack: " + sig.getMaxStack());
System.out.println("Max locals: " + sig.getMaxLocals());
```

### Key Classes

| Class | Description |
|-------|-------------|
| `MethodSimilarityAnalyzer` | Main analyzer with indexing and search |
| `MethodSignature` | Method fingerprint (hash, opcodes, metrics) |
| `SimilarityResult` | Comparison result with scores |
| `SimilarityMetric` | Enum of comparison metrics |

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

Run with:

```bash
java -cp build/classes/java/main com.tonic.demo.CallGraphDemo
java -cp build/classes/java/main com.tonic.demo.DependencyDemo
java -cp build/classes/java/main com.tonic.demo.TypeInferenceDemo
java -cp build/classes/java/main com.tonic.demo.PatternSearchDemo
java -cp build/classes/java/main com.tonic.demo.InstrumentationDemo
```

---

[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [SSA Transforms ->](ssa-transforms.md)
