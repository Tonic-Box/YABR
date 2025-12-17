[<- Back to Analysis APIs](analysis-apis.md)

# Type Inference API

The Type Inference API (`com.tonic.analysis.typeinference`) performs dataflow analysis to infer types and nullability states for SSA values.

---

## Running Type Inference

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

---

## Querying Nullability

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

---

## Nullability States

| State | Description |
|-------|-------------|
| `NULL` | Value is definitely null |
| `NOT_NULL` | Value is definitely not null |
| `UNKNOWN` | Value may be null or non-null |
| `BOTTOM` | No information (unreachable code) |

---

## Getting Type Information

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

---

## Type State at Control Flow Points

```java
// Get type state at block entry
TypeState entryState = analyzer.getTypeStateAtBlockEntry(block, value);

// Get type state at block exit
TypeState exitState = analyzer.getTypeStateAtBlockExit(block, value);
```

---

## Collecting Values by Nullability

```java
// Get all definitely-null values
Set<SSAValue> nullValues = analyzer.getNullValues();

// Get all definitely-non-null values
Set<SSAValue> nonNullValues = analyzer.getNonNullValues();

// Get all type states
Map<SSAValue, TypeState> allStates = analyzer.getAllTypeStates();
```

---

## Polymorphic Types

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

[<- Back to Analysis APIs](analysis-apis.md)
