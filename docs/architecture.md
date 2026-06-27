[<- Back to README](../README.md) | [Quick Start](quick-start.md) | [Class Files ->](class-files.md)

# Architecture Overview

YABR is a Java bytecode library built as a set of Gradle subprojects whose dependencies form an
acyclic layering. Each subproject owns a slice of the `com.tonic` package space; the build forbids
upward or cyclic dependencies, so the layering cannot decay. All subprojects are merged into a
single published artifact (`com.tonic:YABR`), so consumers depend on one jar.

## Module Layering

Each module depends only on the modules below it.

```
query      analyses      execution      source
   |           |             |            |
   |           +------+------+------------+
   |                  |
   +--- ssa <---------+
         |
         +--- bytecode <--- renamer
                 |             |
                 +------+------+
                        |
                       core
```

| Module | Depends on | Contents |
|--------|------------|----------|
| `core` | (none) | `parser`, `parser.constpool`, `parser.attribute`, `parser.util`, `parser.visitor`, `util`, `exception`, `type` |
| `renamer` | core | `renamer` |
| `bytecode` | core | `analysis` (Bytecode, CodeWriter, CodePrinter, ClassFactory, ...), `analysis.instruction`, `analysis.visitor`, `analysis.frame`, `analysis.common`, `builder` |
| `ssa` | core, bytecode | `analysis.ssa` and sub-packages (ir, cfg, lift, lower, transform, analysis, llvm, visitor) |
| `source` | core, bytecode, ssa | `analysis.source` and sub-packages (ast, recovery, lower, emit, decompile, editor, parser) |
| `analyses` | core, bytecode, ssa, source, renamer | `analysis.callgraph`, `.xref`, `.dataflow`, `.dependency`, `.similarity`, `.pattern`, `.typeinference`, `.cpg`, `.pdg`, `.graph`, `.fingerprint` |
| `execution` | core, bytecode, ssa, analyses, renamer | `analysis.execution`, `.absexec`, `.simulation`, `.instrumentation`, `.verifier` |
| `query` | core, bytecode, ssa, renamer | `analysis.query` |

The `all` subproject aggregates the above into the shadow jar; `examples` holds runnable demos and
is not published.

## Layers

### core

The structural model of a class file. `ClassPool` loads classes (from the JRT image on Java 9+ or
`rt.jar` on Java 8); `ClassFile`, `MethodEntry`, `FieldEntry`, `ConstPool`, and the attribute types
parse and hold the `.class` contents. This layer is purely structural: it has no dependency on the
bytecode-emission API. Generating members (default constructors, accessors, method bodies) lives in
`bytecode` via `ClassFactory`.

### bytecode

Bytecode reading, writing, and disassembly. `Bytecode` is the high-level instruction-builder API;
`CodeWriter` is the low-level instruction-list editor with branch relinking and offset layout;
`CodePrinter` disassembles; `ClassFactory` generates members on a `ClassFile`; `builder` provides
fluent class/method builders. `analysis.frame` computes StackMapTable frames.

### ssa

SSA-form IR. `SSA` lifts bytecode to `IRMethod` (a CFG of `IRBlock`s holding phi and value
instructions), runs transforms, and lowers back to bytecode. An alternative leaf backend lowers the
computational subset of the IR to textual LLVM IR (see [LLVM Lowering](llvm-lowering.md)).

### source

Source-level representation. Recovery turns SSA IR into an AST; the emitter renders the AST as Java;
the lowerer turns an AST back into IR. `ClassDecompiler` runs the full pipeline; `ASTEditor` applies
targeted AST transformations.

### analyses, execution, query

Higher-level analyses built on the IR and bytecode: call graphs, cross-references, data flow, code
property and program dependence graphs, similarity, pattern search, and type inference (`analyses`);
concrete execution, abstract simulation, instrumentation, and verification (`execution`); and a
composable bytecode query language (`query`).

### renamer

Class, method, and field renaming with constant-pool updates, hierarchy-aware method renaming, and
descriptor remapping. Depends only on `core`.

## Pipelines

### Parse

```
.class bytes -> ClassFile (verifies 0xCAFEBABE) -> ConstPool -> fields, methods, attributes
```

### Bytecode edit

```
MethodEntry -> CodeWriter (wraps CodeAttribute) -> insert/replace instructions -> write() -> ClassFile.write()
```

### SSA round-trip

```
MethodEntry -> lift (CFG + phi insertion + renaming) -> transforms -> lower -> bytecode
```

### Decompile

```
MethodEntry -> SSA IR -> recovery -> AST -> emit -> Java source
```

## Key Design Decisions

1. Lazy loading: `ClassPool` loads built-in classes on demand.
2. Mutable model: `ClassFile` and its components are mutable.
3. Layered modules: dependencies are acyclic and build-enforced.
4. SSA for analysis: SSA form simplifies data flow and optimization.
5. Automatic frames: StackMapTable generation for Java 7+ verification.

## Related Documentation

- [Working with Class Files](class-files.md)
- [Bytecode API](bytecode-api.md)
- [Visitors](visitors.md)
- [SSA Guide](ssa-guide.md)
- [LLVM Lowering](llvm-lowering.md)
- [AST Guide](ast-guide.md)
- [AST Editor](ast-editor.md)
- [Analysis APIs](analysis-apis.md)
- [Execution API](execution-api.md)
- [Renamer API](renamer-api.md)
- [Frame Computation](frame-computation.md)

---

[<- Back to README](../README.md) | [Quick Start](quick-start.md) | [Class Files ->](class-files.md)
