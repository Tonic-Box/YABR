[<- Back to Analysis APIs](analysis-apis.md)

# Method Similarity API

The Method Similarity API (`com.tonic.analysis.similarity`) finds duplicate and similar methods for de-obfuscation and pattern detection.

---

## Building the Method Index

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

---

## Similarity Metrics

| Metric | Description | Weight |
|--------|-------------|--------|
| `EXACT_BYTECODE` | Byte-for-byte bytecode match (MD5 hash) | 1.0 |
| `OPCODE_SEQUENCE` | Same opcode sequence (ignores operands), uses LCS | 0.8 |
| `CONTROL_FLOW` | Isomorphic control flow graph structure | 0.7 |
| `STRUCTURAL` | Similar metrics (size, branches, calls) | 0.5 |
| `COMBINED` | Weighted combination of all metrics | 1.0 |

---

## Finding Similar Methods

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

---

## Finding Exact Duplicates

```java
// Find methods that are >= 95% similar (near-duplicates)
List<SimilarityResult> duplicates = analyzer.findDuplicates();

System.out.println("Found " + duplicates.size() + " duplicate method pairs");

for (SimilarityResult dup : duplicates) {
    System.out.println(dup.getSummary());  // "Exact/Near duplicate (98%)"
}
```

---

## Finding Renamed Copies (Obfuscation Detection)

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

---

## Comparing Two Specific Methods

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

---

## Finding Methods Similar to a Target

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

---

## Finding Similarity Groups

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

---

## Method Signature Details

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

---

## Key Classes

| Class | Description |
|-------|-------------|
| `MethodSimilarityAnalyzer` | Main analyzer with indexing and search |
| `MethodSignature` | Method fingerprint (hash, opcodes, metrics) |
| `SimilarityResult` | Comparison result with scores |
| `SimilarityMetric` | Enum of comparison metrics |

---

[<- Back to Analysis APIs](analysis-apis.md)
