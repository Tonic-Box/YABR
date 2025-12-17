[<- Back to Analysis APIs](analysis-apis.md)

# Pattern Search API

The Pattern Search API (`com.tonic.analysis.pattern`) provides a high-level fluent interface for finding code patterns across the codebase.

---

## Basic Usage

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

---

## Finding Method Calls

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

---

## Finding Field Accesses

```java
// Find all field accesses on a class
List<SearchResult> reads = search.findFieldAccesses("java/lang/System");

// Find accesses to a specific field by name
List<SearchResult> results = search.findFieldsByName("out");
```

---

## Finding Type Operations

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

---

## Finding Object Allocations

```java
// Find all object allocations
List<SearchResult> allocs = search.findAllocations();

// Find allocations of a specific class
List<SearchResult> allocs = search.findAllocations("java/lang/StringBuilder");
```

---

## Finding Control Flow Patterns

```java
// Find null checks (if x == null / if x != null)
List<SearchResult> nullChecks = search.findNullChecks();

// Find throw statements
List<SearchResult> throws_ = search.findThrows();
```

---

## Combining Patterns

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

---

## Custom Patterns

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

---

## Integration with Other APIs

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

---

## Search Results

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

---

## Built-in Pattern Matchers

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

[<- Back to Analysis APIs](analysis-apis.md)
