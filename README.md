# YABR - Yet Another Bytecode Reader/Writer

A Java bytecode library with class-file parsing, bytecode editing, mutable IRs, (de)compilation,
and a set of higher-level analyses.

[![](https://www.jitpack.io/v/Tonic-Box/YABR.svg)](https://www.jitpack.io/#Tonic-Box/YABR)
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

## Documentation

| Guide | Description |
|-------|-------------|
| [Quick Start](docs/quick-start.md) | Getting started |
| [Architecture](docs/architecture.md) | Module structure and design |
| [Class Files](docs/class-files.md) | ClassPool, ClassFile, ConstPool |
| [Bytecode API](docs/bytecode-api.md) | Bytecode editing |
| [Generation API](docs/generation-api.md) | Fluent class generation |
| [Visitors](docs/visitors.md) | Traversal patterns |
| [SSA Guide](docs/ssa-guide.md) | SSA intermediate representation |
| [SSA Transforms](docs/ssa-transforms.md) | Optimizations and analysis |
| [LLVM Lowering](docs/llvm-lowering.md) | Lower SSA IR to textual LLVM IR |
| [LLVM Lifting](docs/llvm-lifting.md) | Lift LLVM IR back to SSA |
| [SSA IR Migration](docs/SSA_IR_MIGRATION.md) | API changes from the SSA IR redesign |
| [Analysis APIs](docs/analysis-apis.md) | Code analysis and semantic queries |
| [PDG API](docs/pdg-api.md) | Program Dependence Graph with slicing |
| [SDG API](docs/sdg-api.md) | Interprocedural System Dependence Graph |
| [CPG API](docs/cpg-api.md) | Code Property Graph with taint analysis |
| [Query API](docs/query-api.md) | Bytecode query language |
| [Abstract Execution API](docs/abstract-execution-api.md) | Operand-stack and local def-use |
| [AST Guide](docs/ast-guide.md) | AST recovery, mutation, and emission |
| [AST Editor](docs/ast-editor.md) | ExprEditor-style AST transformation |
| [Renamer API](docs/renamer-api.md) | Class, method, and field renaming |
| [Frame Computation](docs/frame-computation.md) | StackMapTable generation |

## Bytecode Generation

```java
import com.tonic.builder.ClassBuilder;
import com.tonic.type.AccessFlags;

byte[] bytes = ClassBuilder.create("com/example/Calculator")
    .version(AccessFlags.V11, 0)
    .access(AccessFlags.ACC_PUBLIC)
    .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "add", "(II)I")
    .code()
        .iload(0)
        .iload(1)
        .iadd()
        .ireturn()
    .end()
    .end()
    .toByteArray();
```

## SSA Pipeline

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

ssa.transform(method);  // lift, optimize, and lower
```

The IR can also be lowered to textual LLVM IR (`.ll`) and lifted back. See
[LLVM Lowering](docs/llvm-lowering.md) and [LLVM Lifting](docs/llvm-lifting.md).

## Decompilation

```java
import com.tonic.analysis.source.decompile.ClassDecompiler;

String source = ClassDecompiler.decompile(classFile);

// With per-method bytecode-offset-to-source-line maps
DecompileResult result = new ClassDecompiler(classFile).decompileWithLineMap();
```

## Analysis APIs

Call graph, dependency, type inference, pattern search, xrefs, data flow, similarity, and graph
analyses (PDG, SDG, CPG) are documented in [Analysis APIs](docs/analysis-apis.md).

## Examples

Runnable examples are in [`examples/src/main/java/com/tonic/demo/`](examples/src/main/java/com/tonic/demo/),
including class creation, SSA transformation, LLVM lowering, AST mutation, decompilation, call
graphs, dependency analysis, type inference, and pattern search.

## Project Layout

YABR is a Gradle multi-project. Production code is split into layered modules (`core`, `bytecode`,
`renamer`, `ssa`, `source`, `analyses`, `execution`, `query`) whose dependencies are acyclic and
build-enforced; the `all` module merges them into a single jar. See
[Architecture](docs/architecture.md).

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

## Building

```bash
./gradlew build              # build and test all modules
./gradlew :all:shadowJar     # produce the merged jar
```

## Requirements

- Java 11+

## Acknowledgements

Inspired by [classpooly](https://github.com/d-o-g/classpooly).

## License

MIT
