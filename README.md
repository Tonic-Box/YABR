# YABR - Yet Another Bytecode Reader/Writer

YABR is a bidirectional Java (de)compiler, program-analysis, and transformation framework. It spans
bytecode, SSA, a structured IR, execution, and source, so a program can be lifted, analyzed,
transformed, executed, compiled, and decompiled across one ecosystem.

[![](https://www.jitpack.io/v/Tonic-Box/YABR.svg)](https://www.jitpack.io/#Tonic-Box/YABR)

## Projects using YABR

- [JStudio](https://github.com/Tonic-Box/JStudio) - A Java reverse engineering and analysis IDE.
- [J2CS](https://github.com/Tonic-Box/J2CS) - A Java to C# transpiler.
- [Dukes-8-Bit-Challenge](https://github.com/Tonic-Box/Dukes-8-Bit-Challenge) - A game-jam submission using YABR in `buildSrc/` for compile-time transformations.

## Building
- Java 11+
```bash
./gradlew build
./gradlew :all:shadowJar
```

## Installation

The published artifact is the merged jar from the `all` module. Via JitPack:

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.Tonic-Box.YABR:all:<version>")
}
```

## Quick Start

```java
// Load a class
ClassPool pool = ClassPool.getDefault();
ClassFile cf = pool.loadClass(inputStream);

// Create a new class with a default constructor
int access = new AccessBuilder().setPublic().build();
ClassFile newClass = ClassFactory.createClass(pool, "com/example/MyClass", access);

// Add a field with a getter and setter
FieldEntry field = newClass.createNewField(access, "value", "I", new ArrayList<>());
ClassFactory.generateGetter(newClass, field, false);
ClassFactory.generateSetter(newClass, field, false);

// Write the class
newClass.rebuild();
byte[] bytes = newClass.write();
```

## Examples

Runnable examples are in [`examples/src/main/java/com/tonic/demo/`](examples/src/main/java/com/tonic/demo/),
including class creation, SSA transformation, LLVM lowering, AST mutation, decompilation, call
graphs, dependency analysis, type inference, and pattern search.

## Documentation

### Getting Started
| Guide | Description |
|-------|-------------|
| [Quick Start](docs/quick-start.md) | Load, create, and modify classes in five minutes |
| [Architecture](docs/architecture.md) | Module structure and design |

### Class Files & Bytecode
| Guide | Description |
|-------|-------------|
| [Class Files](docs/class-files.md) | ClassPool, ClassFile, ConstPool |
| [Bytecode API](docs/bytecode-api.md) | Low-level bytecode editing |
| [Generation API](docs/generation-api.md) | Fluent class/method generation |
| [Visitors](docs/visitors.md) | Traversal and transformation patterns |
| [Frame Computation](docs/frame-computation.md) | StackMapTable generation |

### SSA & IR
| Guide | Description |
|-------|-------------|
| [SSA Guide](docs/ssa-guide.md) | SSA intermediate representation |
| [SSA Transforms](docs/ssa-transforms.md) | Optimization and analysis passes |
| [LLVM Lowering](docs/llvm-lowering.md) | Lower SSA IR to textual LLVM IR |
| [LLVM Lifting](docs/llvm-lifting.md) | Lift LLVM IR back to SSA |

### Source & Decompilation
| Guide | Description |
|-------|-------------|
| [AST Guide](docs/ast-guide.md) | AST recovery, mutation, and emission |
| [AST Editor](docs/ast-editor.md) | ExprEditor-style AST transformation |
| [Parser API](docs/parser-api.md) | Java source parser |
| [Decompiler API](docs/decompiler-api.md) | Class -> Java source decompilation |

### Analysis
| Guide | Description |
|-------|-------------|
| [Analysis APIs](docs/analysis-apis.md) | Overview of the analysis suite |
| [Call Graph API](docs/call-graph-api.md) | Call-graph construction |
| [Data Flow API](docs/dataflow-api.md) | Data-flow analysis |
| [Dependency API](docs/dependency-api.md) | Dependency analysis |
| [PDG API](docs/pdg-api.md) | Program Dependence Graph with slicing |
| [SDG API](docs/sdg-api.md) | Interprocedural System Dependence Graph |
| [CPG API](docs/cpg-api.md) | Code Property Graph with taint analysis |
| [Pattern Search API](docs/pattern-search-api.md) | Structural bytecode pattern search |
| [Similarity API](docs/similarity-api.md) | Method similarity and fingerprinting |
| [Type Inference API](docs/type-inference-api.md) | Type inference |
| [Xref API](docs/xref-api.md) | Cross-references |

### Execution & Runtime
| Guide | Description |
|-------|-------------|
| [Execution API](docs/execution-api.md) | Concrete bytecode execution |
| [Simulation API](docs/simulation-api.md) | Abstract simulation |
| [Abstract Execution API](docs/abstract-execution-api.md) | Operand-stack and local def-use |
| [Instrumentation API](docs/instrumentation-api.md) | Bytecode instrumentation and hooks |
| [Verifier API](docs/verifier-api.md) | Bytecode verification |

### Language & Query
| Guide | Description |
|-------|-------------|
| [Query API](docs/query-api.md) | Bytecode query language |
| [Renamer API](docs/renamer-api.md) | Class, method, and field renaming |

## Project Layout

YABR is a Gradle multi-project. Production code is split into layered modules (`core`, `bytecode`,
`renamer`, `ssa`, `source`, `analyses`, `execution`, `query`) whose dependencies are acyclic and
build-enforced; the `all` module merges them into a single jar. See
[Architecture](docs/architecture.md).

## Acknowledgements

Inspired by [classpooly](https://github.com/d-o-g/classpooly).

## License

MIT
