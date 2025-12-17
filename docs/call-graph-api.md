[<- Back to Analysis APIs](analysis-apis.md)

# Call Graph API

The Call Graph API (`com.tonic.analysis.callgraph`) builds a graph of method invocations across all classes in a ClassPool.

---

## Building a Call Graph

```java
import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.callgraph.MethodReference;
import com.tonic.parser.ClassPool;

ClassPool pool = ClassPool.getDefault();
CallGraph cg = CallGraph.build(pool);

System.out.println(cg);  // CallGraph{methods=69089, edges=5787464}
```

---

## Querying Callers and Callees

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

---

## Reachability Analysis

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

---

## Virtual Call Resolution

```java
// Resolve possible targets of a virtual call
Set<MethodReference> targets = cg.resolveVirtualTargets(
    "java/util/List",  // declared type
    "add",
    "(Ljava/lang/Object;)Z"
);
// Returns ArrayList.add, LinkedList.add, etc.
```

---

## Key Classes

| Class | Description |
|-------|-------------|
| `MethodReference` | Identifies a method (owner, name, descriptor) |
| `CallSite` | A call location with invoke type (VIRTUAL, STATIC, etc.) |
| `CallGraphNode` | Node with incoming/outgoing call edges |
| `CallGraph` | Main API for querying the graph |
| `CallGraphBuilder` | Builds the graph by scanning SSA IR |

---

[<- Back to Analysis APIs](analysis-apis.md)
