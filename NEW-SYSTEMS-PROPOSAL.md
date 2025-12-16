# New API/System Recommendations for YABR

## Current Capabilities Summary

### What We Have

**SSA IR Level (26 transforms, 5 analyses):**
- Transforms: ConstantFolding, CopyPropagation, DCE, CSE, AlgebraicSimplification, LoopInvariantCodeMotion, MethodInlining, NullCheckElimination, StrengthReduction, JumpThreading, BitTrackingDCE, InductionVariableSimplification, and more
- Analyses: DominatorTree, PostDominatorTree, LoopAnalysis, DefUseChains, LivenessAnalysis

**AST Level:**
- 22 expression types, 20 statement types
- ControlFlowSimplifier (7 patterns), DeadVariableEliminator, DeadStoreEliminator
- ASTEditor with handlers and matchers
- Full recovery and emission pipeline

**Infrastructure:**
- Renamer API (class/method/field renaming with hierarchy)
- ClassPool, ClassFile, ConstPool
- Frame computation for StackMapTable
- Bytecode/CodeWriter for manipulation

**Analysis APIs (NEW):**
- Call Graph API (`com.tonic.analysis.callgraph`)
- Dependency Analysis API (`com.tonic.analysis.dependency`)
- Type Inference API (`com.tonic.analysis.typeinference`)
- Pattern Search API (`com.tonic.analysis.pattern`)

---

## Top Recommendations for New Systems

### 1. ~~Pattern Matching / Search API~~ ✅ IMPLEMENTED
**Value: HIGH | Complexity: LOW** | **Status: COMPLETE**

Package: `com.tonic.analysis.pattern`

A higher-level API for finding code patterns across the codebase:
- Find all methods that call X
- Find all usages of field Y
- Find all instanceof checks for type Z
- Find all catch blocks catching exception E

```java
PatternSearch search = new PatternSearch(classPool);

// Find all System.out.println calls
List<SearchResult> results = search
    .inAllMethodsOf(classFile)
    .findMethodCalls("java/io/PrintStream", "println");

// Find all null checks
List<SearchResult> nullChecks = search.findNullChecks();

// Custom patterns with Patterns factory
List<SearchResult> exceptionAllocs = search.findPattern(
    Patterns.and(Patterns.anyNew(),
        (instr, m, s, c) -> instr instanceof NewInstruction &&
            ((NewInstruction)instr).getClassName().contains("Exception"))
);
```

See [docs/analysis-apis.md](docs/analysis-apis.md) for full documentation.

---

### 2. ~~Call Graph & Interprocedural Analysis Framework~~ ✅ IMPLEMENTED
**Value: HIGH | Complexity: MEDIUM** | **Status: COMPLETE**

Package: `com.tonic.analysis.callgraph`

Currently all analysis is intraprocedural (within a single method). A call graph would enable:
- Finding all callers of a method
- Finding all callees from a method
- Detecting dead methods (not just private - full reachability)
- Enabling interprocedural constant propagation
- Supporting escape analysis

```java
CallGraph cg = CallGraph.build(classPool);
Set<MethodEntry> callers = cg.getCallers(method);
Set<MethodEntry> callees = cg.getCallees(method);
Set<MethodEntry> reachable = cg.getReachableFrom(entryPoints);
```

**Why it fits:** Builds on existing ClassHierarchy infrastructure in renamer package.

---

### 3. ~~Dependency Analysis API~~ ✅ IMPLEMENTED
**Value: MEDIUM-HIGH | Complexity: LOW** | **Status: COMPLETE**

Package: `com.tonic.analysis.dependency`

Analyze and query class dependencies:
- What classes does X depend on?
- What classes depend on X?
- Find circular dependencies
- Generate dependency graph

```java
DependencyAnalyzer deps = new DependencyAnalyzer(classPool);

Set<String> dependencies = deps.getDependencies("com/example/MyClass");
Set<String> dependents = deps.getDependents("com/example/MyClass");
List<List<String>> cycles = deps.findCircularDependencies();
```

**Why it fits:** Uses existing constant pool scanning, pairs well with Renamer.

---

### 4. Instrumentation API
**Value: HIGH | Complexity: MEDIUM**

Higher-level API specifically for common instrumentation tasks:
- Method entry/exit hooks
- Exception interception
- Field access monitoring
- Timing/profiling injection

```java
Instrumenter inst = new Instrumenter(classPool);

// Add timing to all methods in a class
inst.onMethodEntry("com/example/Service", (ctx) -> {
    return ctx.factory().assignField("startTime", ctx.factory().currentTimeMillis());
});

inst.onMethodExit("com/example/Service", (ctx) -> {
    return ctx.factory().call("Logger", "log",
        ctx.factory().subtract(ctx.factory().currentTimeMillis(),
                               ctx.factory().readField("startTime")));
});

inst.apply();
```

**Why it fits:** Natural extension of ASTEditor for common use cases.

---

### 5. Batch Transform Pipeline
**Value: MEDIUM | Complexity: LOW**

Composable transformation pipeline:

```java
TransformPipeline pipeline = TransformPipeline.create()
    .add(new ConstantFolding())
    .add(new CopyPropagation())
    .add(new DeadCodeElimination())
    .runUntilFixpoint(true)
    .build();

// Apply to all methods
pipeline.apply(classPool);

// Or specific classes
pipeline.apply(classPool, "com/example/**");
```

**Why it fits:** We have transforms but no easy way to compose them across classes.

---

### 6. ~~Type Inference / Type Analysis~~ ✅ IMPLEMENTED
**Value: MEDIUM-HIGH | Complexity: MEDIUM** | **Status: COMPLETE**

Package: `com.tonic.analysis.typeinference`

Enhanced type analysis beyond what's in SSA:
- Infer types for locals without debug info
- Track null/non-null state
- Compute type bounds at each program point
- Support for generics analysis

```java
TypeAnalyzer types = new TypeAnalyzer(method);

// Get inferred type at a specific point
SourceType type = types.getTypeAt(ssaValue);

// Check nullability
Nullability nullState = types.getNullability(ssaValue);

// Get all possible types (for polymorphic calls)
Set<SourceType> possibleTypes = types.getPossibleTypes(receiver);
```

**Why it fits:** Builds on existing SSA infrastructure and DefUseChains.

---

### 7. Diff / Comparison API
**Value: MEDIUM | Complexity: LOW**

Compare two versions of code:
- Method-level diff (what changed?)
- AST diff for semantic comparison
- Signature compatibility checking

```java
ClassDiff diff = ClassDiff.compare(oldClass, newClass);

for (MethodDiff md : diff.getChangedMethods()) {
    System.out.println(md.getName() + ": " + md.getChangeType());
    // ADDED, REMOVED, SIGNATURE_CHANGED, BODY_CHANGED
}

// Check if changes are binary compatible
boolean compatible = diff.isBinaryCompatible();
```

**Why it fits:** Useful for migrations, pairs with Renamer for refactoring.

---

### 8. Annotation Processing Framework
**Value: MEDIUM | Complexity: MEDIUM**

Process and transform based on annotations:
- Find all methods with @Deprecated
- Add annotations programmatically
- Transform based on annotation values

```java
AnnotationProcessor proc = new AnnotationProcessor(classPool);

// Find all deprecated elements
List<AnnotatedElement> deprecated = proc.findAnnotated("java/lang/Deprecated");

// Add annotation to method
proc.addAnnotation(method, "com/example/Generated",
    Map.of("value", "YABR", "date", "2024-01-01"));

// Transform based on annotations
proc.onAnnotation("com/example/Traced", (ctx, element) -> {
    if (element instanceof MethodEntry) {
        // inject tracing
    }
});
```

**Why it fits:** Annotations are parsed but not queryable; common use case.

---

### 9. Clone Detection
**Value: MEDIUM | Complexity: HIGH**

Detect duplicate/similar code:
- Exact clones (identical code)
- Near clones (minor differences)
- Semantic clones (same logic, different syntax)

```java
CloneDetector detector = new CloneDetector(classPool);
detector.setMinimumSize(10); // statements
detector.setSimilarityThreshold(0.8);

List<CloneGroup> clones = detector.findClones();
for (CloneGroup group : clones) {
    System.out.println("Clone group with " + group.size() + " instances:");
    for (CloneInstance inst : group) {
        System.out.println("  " + inst.getLocation());
    }
}
```

**Why it fits:** Uses AST comparison, helps with refactoring.

---

### 10. Metrics / Code Quality API
**Value: LOW-MEDIUM | Complexity: LOW**

Calculate code metrics:
- Cyclomatic complexity
- Lines of code
- Method/class size
- Coupling metrics

```java
Metrics metrics = Metrics.analyze(classFile);

int complexity = metrics.getCyclomaticComplexity(method);
int loc = metrics.getLinesOfCode(method);
int coupling = metrics.getEfferentCoupling(); // dependencies out
int cohesion = metrics.getLackOfCohesion();
```

**Why it fits:** Simple analysis using existing CFG infrastructure.

---

## Prioritized Summary

| Rank | System | Value | Effort | Status | Rationale |
|------|--------|-------|--------|--------|-----------|
| 1 | **Pattern Search API** | HIGH | LOW | ✅ DONE | Most immediately useful, extends existing matchers |
| 2 | **Call Graph** | HIGH | MEDIUM | ✅ DONE | Enables many other analyses, high leverage |
| 3 | **Dependency Analysis** | HIGH | LOW | ✅ DONE | Simple to implement, very practical |
| 4 | **Instrumentation API** | HIGH | MEDIUM | ⏳ TODO | Common use case, great UX improvement |
| 5 | **Batch Transform Pipeline** | MEDIUM | LOW | ⏳ TODO | Quick win, improves usability |
| 6 | **Type Inference** | MEDIUM-HIGH | MEDIUM | ✅ DONE | Improves decompilation quality |
| 7 | **Diff API** | MEDIUM | LOW | ⏳ TODO | Useful for migrations |
| 8 | **Annotation Processing** | MEDIUM | MEDIUM | ⏳ TODO | Common use case |
| 9 | **Clone Detection** | MEDIUM | HIGH | ⏳ TODO | Complex but valuable |
| 10 | **Metrics API** | LOW-MEDIUM | LOW | ⏳ TODO | Nice to have |

**Progress: 4/10 systems implemented**
