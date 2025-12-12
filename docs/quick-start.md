[<- Back to README](../README.md) | [Architecture ->](architecture.md)

# Quick Start Guide

Get up and running with YABR in 5 minutes.

## Prerequisites

- Java 11 or higher
- Gradle (or use the included wrapper)

## Setup

Add YABR to your project or clone the repository:

```bash
git clone https://github.com/Tonic-Box/YABR.git
cd YABR
./gradlew build
```

## Your First Program

### Loading a Class

```java
import com.tonic.parser.ClassPool;
import com.tonic.parser.ClassFile;

// Get the default class pool (includes java.base classes)
ClassPool classPool = ClassPool.getDefault();

// Load from resources
try (InputStream is = getClass().getResourceAsStream("MyClass.class")) {
    ClassFile classFile = classPool.loadClass(is);
    System.out.println("Loaded: " + classFile.getClassName());
}
```

### Creating a New Class

```java
import com.tonic.parser.ClassPool;
import com.tonic.parser.ClassFile;
import com.tonic.utill.AccessBuilder;

ClassPool classPool = ClassPool.getDefault();

int access = new AccessBuilder()
    .setPublic()
    .build();

ClassFile classFile = classPool.createNewClass("com/example/MyClass", access);
classFile.rebuild();

// Write to bytes
byte[] classBytes = classFile.write();
```

### Adding a Field

```java
import com.tonic.parser.FieldEntry;

int fieldAccess = new AccessBuilder()
    .setPrivate()
    .build();

FieldEntry field = classFile.createNewField(
    fieldAccess,
    "myField",
    "I",  // int type
    new ArrayList<>()
);

// Set initial value
classFile.setFieldInitialValue(field, 42);

// Generate getter and setter
classFile.generateGetter(field, false);
classFile.generateSetter(field, false);
```

### Adding a Method

```java
import com.tonic.parser.MethodEntry;
import com.tonic.analysis.Bytecode;
import com.tonic.utill.ReturnType;

int methodAccess = new AccessBuilder()
    .setPublic()
    .setStatic()
    .build();

MethodEntry method = classFile.createNewMethod(
    methodAccess,
    "add",
    int.class,      // return type
    int.class,      // param 1
    int.class       // param 2
);

Bytecode bytecode = new Bytecode(method);
bytecode.addILoad(0);           // Load first parameter
bytecode.addILoad(1);           // Load second parameter
// Note: IADD would need to be added via CodeWriter
bytecode.addReturn(ReturnType.IRETURN);
bytecode.finalizeBytecode();
```

## Running the Demos

YABR includes several demo programs to help you understand the API:

```bash
# Compile the project
./gradlew build

# Run a demo
java -cp build/classes/java/main com.tonic.demo.TestBlocks
```

Available demos in `src/main/java/com/tonic/demo/`:
- `TestBlocks.java` - SSA IR block visitor pattern
- `TestBytecodeVisitor.java` - Bytecode-level visitor pattern
- `TestClassCreation.java` - Creating classes programmatically
- `TestSSADemo.java` - Complete SSA transformation example

## Next Steps

- [Architecture Overview](architecture.md) - Understand how YABR is structured
- [Working with Class Files](class-files.md) - Deep dive into ClassPool and ClassFile
- [Bytecode API](bytecode-api.md) - Low-level bytecode manipulation
- [Visitors](visitors.md) - Traversing and transforming classes
- [SSA Guide](ssa-guide.md) - Static Single Assignment intermediate representation

## Common Patterns

### Load, Modify, Save

```java
// Load
ClassFile cf = classPool.loadClass(inputStream);

// Modify
cf.setClassName("com/example/RenamedClass");

// Save
byte[] modified = cf.write();
Files.write(Path.of("RenamedClass.class"), modified);
```

### Visitor-Based Transformation

```java
classFile.accept(new AbstractClassVisitor() {
    @Override
    public void visitMethod(MethodEntry method) {
        System.out.println("Method: " + method.getName());
    }
});
```

---

[<- Back to README](../README.md) | [Architecture ->](architecture.md)
