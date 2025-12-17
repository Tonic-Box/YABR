[<- Back to Analysis APIs](analysis-apis.md)

# Dependency Analysis API

The Dependency Analysis API (`com.tonic.analysis.dependency`) tracks class-level dependencies by scanning constant pools.

---

## Building a Dependency Graph

```java
import com.tonic.analysis.dependency.DependencyAnalyzer;
import com.tonic.parser.ClassPool;

ClassPool pool = ClassPool.getDefault();
DependencyAnalyzer deps = new DependencyAnalyzer(pool);

System.out.println(deps);  // DependencyAnalyzer{classes=7517, inPool=7516, edges=174566}
```

---

## Querying Dependencies

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

---

## Circular Dependency Detection

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

---

## Dependency Types

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

---

## Filtering by Dependency Type

```java
DependencyNode node = deps.getNode("com/example/MyClass");

// Get only inheritance dependencies
Set<String> superTypes = node.getDependenciesByType(DependencyType.EXTENDS);
Set<String> interfaces = node.getDependenciesByType(DependencyType.IMPLEMENTS);

// Get all method call targets
Set<String> callTargets = node.getDependenciesByType(DependencyType.METHOD_CALL);
```

---

## Finding Special Classes

```java
// Classes with no dependencies (leaf classes)
Set<String> leaves = deps.findLeafClasses();

// Classes with no dependents (root classes)
Set<String> roots = deps.findRootClasses();

// Classes in a specific package
Set<String> packageClasses = deps.getClassesInPackage("com/example/");
```

---

[<- Back to Analysis APIs](analysis-apis.md)
