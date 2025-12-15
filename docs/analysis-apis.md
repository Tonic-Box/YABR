[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [SSA Transforms ->](ssa-transforms.md)

# Analysis APIs

YABR provides five high-level analysis APIs that build on top of the SSA IR system:

1. **Call Graph** - Method caller/callee relationships
2. **Dependency Analysis** - Class-level dependency tracking
3. **Type Inference** - Nullability and type state analysis
4. **Pattern Search** - Code pattern matching across the codebase
5. **Instrumentation** - Bytecode instrumentation with hooks

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
