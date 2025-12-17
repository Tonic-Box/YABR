[<- Back to Analysis APIs](analysis-apis.md)

# Cross-References (Xref) API

The Cross-References API (`com.tonic.analysis.xref`) tracks all references to and from classes, methods, and fields. It answers questions like "who calls this method?" and "what does this method call?"

---

## Building the Xref Database

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

---

## Querying Incoming References (Who References This?)

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

---

## Querying Outgoing References (What Does This Reference?)

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

---

## Reference Types (XrefType)

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

---

## Filtering by Reference Type

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

---

## Searching References

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

---

## Finding Unused Code

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

---

## Statistics

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

---

## Key Classes

| Class | Description |
|-------|-------------|
| `XrefBuilder` | Builds XrefDatabase by scanning ClassPool |
| `XrefDatabase` | Indexed database with fast lookup methods |
| `Xref` | A single reference with source/target info |
| `XrefType` | Enum of 12 reference types |
| `MethodReference` | Method identifier (owner, name, descriptor) |
| `FieldReference` | Field identifier (owner, name, descriptor) |

---

[<- Back to Analysis APIs](analysis-apis.md)
