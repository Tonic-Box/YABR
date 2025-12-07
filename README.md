# YABR - Yet Another Bytecode Reader/Writer

A Java bytecode manipulation library with SSA-form intermediate representation for analysis and optimization.

## Features

- **Class file parsing** - Read and write Java `.class` files
- **Bytecode manipulation** - High-level and low-level APIs for modifying bytecode
- **SSA IR system** - Lift bytecode to SSA form, optimize, and lower back
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
| [Frame Computation](docs/frame-computation.md) | StackMapTable generation |

## Examples

Runnable examples are in [`src/main/java/com/tonic/demo/`](src/main/java/com/tonic/demo/):

- `TestBlocks.java` - SSA IR block visitor
- `TestBytecodeVisitor.java` - Bytecode-level visitor
- `TestClassCreation.java` - Creating classes programmatically
- `TestSSADemo.java` - Complete SSA transformation

## SSA Pipeline

YABR includes a full SSA transformation system:

```
Bytecode -> Lift -> SSA IR -> Optimize -> Lower -> Bytecode
```

```java
SSA ssa = new SSA(constPool)
    .withConstantFolding()
    .withCopyPropagation()
    .withDeadCodeElimination();

ssa.transform(method);  // Lift, optimize, and lower
```

## Building

```bash
./gradlew build
```

## Requirements

- Java 17+

## Acknowledgements

Inspired by [classpooly](https://github.com/d-o-g/classpooly).

## License

MIT

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Tonic-Box/YABR)