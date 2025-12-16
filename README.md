# YABR - Yet Another Bytecode Reader/Writer

A Java bytecode manipulation library with SSA-form intermediate representation for analysis and optimization.

## Features

- **Class file parsing** - Read and write Java `.class` files
- **Bytecode manipulation** - High-level and low-level APIs for modifying bytecode
- **SSA IR system** - Lift bytecode to SSA form, optimize, and lower back
- **Source AST system** - Recover, mutate, and emit Java source from bytecode
- **Class decompilation** - Full class decompilation with imports, fields, and methods
- **Analysis APIs** - Call graph, dependency, type inference, pattern search, xrefs, data flow, similarity
- **Visitor patterns** - Traverse classes at multiple abstraction levels
- **Frame computation** - Automatic StackMapTable generation for Java 7+

## Quick Start

```java
// Load a class
ClassPool pool = ClassPool.getDefault();
ClassFile cf = pool.loadClass(inputStream);

// Create a new class
int access = new AccessBuilder().setPublic().build();
ClassFile newClass = pool.createNewClass("com/example/MyClass", access);

// Add a field with getter/setter
FieldEntry field = newClass.createNewField(access, "value", "I", new ArrayList<>());
newClass.generateGetter(field, false);
newClass.generateSetter(field, false);

// Write the class
newClass.rebuild();
byte[] bytes = newClass.write();
```

## Documentation

| Guide | Description |
|-------|-------------|
| [Quick Start](docs/quick-start.md) | Get running in 5 minutes |
| [Architecture](docs/architecture.md) | System overview and design |
| [Class Files](docs/class-files.md) | ClassPool, ClassFile, ConstPool |
| [Bytecode API](docs/bytecode-api.md) | Bytecode manipulation |
| [Visitors](docs/visitors.md) | Traversal patterns |
| [SSA Guide](docs/ssa-guide.md) | SSA intermediate representation |
| [SSA Transforms](docs/ssa-transforms.md) | Optimizations and analysis |
| [Analysis APIs](docs/analysis-apis.md) | Call graph, dependencies, type inference, pattern search, xrefs, data flow, similarity |
| [AST Guide](docs/ast-guide.md) | Source-level AST recovery, mutation, and emission |
| [AST Editor](docs/ast-editor.md) | ExprEditor-style AST transformation |
| [Renamer API](docs/renamer-api.md) | Class, method, and field renaming |
| [Frame Computation](docs/frame-computation.md) | StackMapTable generation |

## Examples

Runnable examples are in [`src/main/java/com/tonic/demo/`](src/main/java/com/tonic/demo/):

- `TestBlocks.java` - SSA IR block visitor
- `TestBytecodeVisitor.java` - Bytecode-level visitor
- `TestClassCreation.java` - Creating classes programmatically
- `TestSSADemo.java` - Complete SSA transformation
- `ASTMutationDemo.java` - AST recovery, mutation, and recompilation
- `SourceASTDemo.java` - AST node construction and source emission
- `ast/Decompile.java` - Command-line class decompiler
- `CallGraphDemo.java` - Build and query method call graph
- `DependencyDemo.java` - Analyze class dependencies
- `TypeInferenceDemo.java` - Type and nullability inference
- `PatternSearchDemo.java` - Search for code patterns

## SSA Pipeline

YABR includes a full SSA transformation system:

```
Bytecode -> Lift -> SSA IR -> Optimize -> Lower -> Bytecode
                       |                    ^
                   [Recover]            [Lower]
                       |                    |
                       v                    |
                      AST ----[Mutate]----> AST
```

```java
SSA ssa = new SSA(constPool)
    .withConstantFolding()
    .withCopyPropagation()
    .withDeadCodeElimination();

ssa.transform(method);  // Lift, optimize, and lower
```

The AST path enables source-level transformations:

```java
// Recover AST from IR
BlockStmt ast = MethodRecoverer.recoverMethod(irMethod, method);
System.out.println(SourceEmitter.emit(ast));  // Print as Java source

// Mutate and recompile
ast.getStatements().forEach(stmt -> { /* modify */ });
new ASTLowerer(constPool).lower(ast, irMethod, method);
ssa.lower(irMethod, method);
```

## Class Decompilation

Decompile entire class files to Java source with imports:

```java
import com.tonic.analysis.source.decompile.ClassDecompiler;

// Simple one-liner
String source = ClassDecompiler.decompile(classFile);

// With configuration
SourceEmitterConfig config = SourceEmitterConfig.builder()
    .useFullyQualifiedNames(false)  // Use simple names + imports
    .alwaysUseBraces(true)
    .build();

String source = ClassDecompiler.decompile(classFile, config);
```

Command-line usage:

```bash
java -cp build/classes/java/main com.tonic.demo.ast.Decompile MyClass.class
java -cp build/classes/java/main com.tonic.demo.ast.Decompile MyClass.class --fqn
```

## Analysis APIs

YABR provides high-level APIs for code analysis:

```java
// Call Graph - find method callers/callees
CallGraph cg = CallGraph.build(classPool);
Set<MethodReference> callers = cg.getCallers(methodRef);

// Dependency Analysis - track class dependencies
DependencyAnalyzer deps = new DependencyAnalyzer(classPool);
Set<String> dependencies = deps.getDependencies("com/example/MyClass");
List<List<String>> cycles = deps.findCircularDependencies();

// Type Inference - nullability analysis
TypeInferenceAnalyzer types = new TypeInferenceAnalyzer(irMethod);
types.analyze();
Nullability nullState = types.getNullability(ssaValue);

// Pattern Search - find code patterns
PatternSearch search = new PatternSearch(classPool)
    .inAllMethodsOf(classFile)
    .withCallGraph();
List<SearchResult> results = search.findMethodCalls("java/io/PrintStream", "println");

// Cross-References - track all references to/from symbols
XrefDatabase xrefs = new XrefBuilder(classPool).build();
Set<Xref> callers = xrefs.getRefsToMethod(methodRef);  // Who calls this?
Set<Xref> outgoing = xrefs.getRefsFromClass(className); // What does this reference?

// Data Flow - build flow graphs for taint analysis
DataFlowGraph dfg = new DataFlowGraph(irMethod);
dfg.build();
Set<DataFlowNode> reachable = dfg.getReachableNodes(paramNode);

// Method Similarity - find duplicates and similar methods
MethodSimilarityAnalyzer similarity = new MethodSimilarityAnalyzer(classPool);
similarity.buildIndex();
List<SimilarityResult> duplicates = similarity.findDuplicates();
List<SimilarityResult> renamed = similarity.findRenamedCopies();  // Obfuscation detection
```

See [Analysis APIs](docs/analysis-apis.md) for complete documentation.

## Building

```bash
./gradlew build
```

## Requirements

- Java 11+

## Acknowledgements

Inspired by [classpooly](https://github.com/d-o-g/classpooly).

## License

MIT

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Tonic-Box/YABR)