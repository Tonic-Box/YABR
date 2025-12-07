[<- Back to README](../README.md) | [SSA Transforms](ssa-transforms.md)

# Frame Computation

Java class files version 51+ (Java 7+) require StackMapTable attributes for bytecode verification. YABR can automatically compute these frames after bytecode modification.

## What is StackMapTable?

The StackMapTable attribute contains stack map frames that describe the types of local variables and operand stack entries at specific bytecode offsets. The JVM verifier uses these frames to efficiently verify bytecode without performing expensive dataflow analysis.

### Why Frames Are Needed

Without StackMapTable, the JVM must perform type inference across all possible control flow paths. With frames, the verifier only needs to check that:

1. Each instruction's operands match expected types
2. At branch targets, actual types match the declared frame

### When Frames Are Required

- **Always required** for class file version 51+ (Java 7+)
- **At branch targets** (after `if`, `goto`, exception handlers)
- **At exception handlers** (catch blocks)
- **After unconditional branches** (`goto`, `throw`, `return`)

## Using Frame Computation

### Via ClassFile

```java
// Compute frames for all methods
int count = classFile.computeFrames();
System.out.println("Computed frames for " + count + " methods");

// Compute frames for a specific method by name
boolean found = classFile.computeFrames("myMethod", "(II)I");

// Compute frames for a specific MethodEntry
classFile.computeFrames(methodEntry);
```

### Via Bytecode

```java
Bytecode bc = new Bytecode(method);
// ... make modifications ...
bc.finalizeBytecode();

// Compute frames if bytecode was modified
if (bc.isModified()) {
    bc.computeFrames();
}

// Force recomputation even if not modified
bc.forceComputeFrames();
```

### Via CodeWriter

```java
CodeWriter cw = new CodeWriter(method);
// ... make modifications ...
cw.write();

// Compute frames
cw.computeFrames();
cw.forceComputeFrames();
```

### Via FrameGenerator Directly

```java
import com.tonic.analysis.frame.FrameGenerator;

FrameGenerator generator = new FrameGenerator(constPool);
generator.updateStackMapTable(methodEntry);
```

## Frame Generation Process

The frame computation process:

1. **Build CFG** - Create control flow graph from bytecode
2. **Type Inference** - Infer types at each bytecode offset using abstract interpretation
3. **Identify Targets** - Find all branch targets needing frames
4. **Compute Deltas** - Calculate stack map frames relative to previous state
5. **Emit Frames** - Generate StackMapTable attribute

### Frame Types

The JVM defines several frame types for compact encoding:

| Frame Type | Description |
|------------|-------------|
| `same_frame` | Same locals, empty stack |
| `same_locals_1_stack_item` | Same locals, one stack item |
| `chop_frame` | Remove 1-3 locals, empty stack |
| `append_frame` | Add 1-3 locals, empty stack |
| `full_frame` | Explicit locals and stack |

## TypeState and Type Inference

The `TypeState` class tracks types during abstract interpretation:

```java
// Internal representation (not typically used directly)
TypeState state = new TypeState(maxLocals, maxStack);

// Type categories
// TOP - unknown/uninitialized
// INTEGER - int, byte, char, short, boolean
// FLOAT - float
// LONG - long (uses 2 slots)
// DOUBLE - double (uses 2 slots)
// NULL - null reference
// OBJECT - object reference with class name
// UNINITIALIZED - new object before <init>
```

## FrameComputer Workflow

The `FrameComputer` class performs the actual computation:

1. **Initialize** - Set initial state from method signature
2. **Iterate** - Process each instruction, updating type state
3. **Merge** - At join points, merge incoming type states
4. **Fixpoint** - Repeat until no changes occur
5. **Extract** - Get frames at required offsets

## Common Issues and Solutions

### VerifyError: Inconsistent stackmap frames

**Cause:** Frame types don't match actual types at branch target.

**Solution:** Ensure `computeFrames()` is called after all bytecode modifications.

```java
// Wrong - frames computed before modification
classFile.computeFrames();
bytecode.addILoad(0);
bytecode.finalizeBytecode();

// Right - frames computed after modification
bytecode.addILoad(0);
bytecode.finalizeBytecode();
classFile.computeFrames();
```

### VerifyError: Stack map does not match

**Cause:** Control flow change without frame update.

**Solution:** Always recompute frames after adding/removing branches.

```java
bytecode.addGoto("label");  // Adds control flow
bytecode.finalizeBytecode();
bytecode.computeFrames();   // Must recompute
```

### Missing StackMapTable

**Cause:** Method modified but frames not computed.

**Solution:** Call `computeFrames()` for modified methods.

```java
// After modification
if (bytecode.isModified()) {
    bytecode.computeFrames();
}
```

### Frame computation fails

**Cause:** Invalid bytecode that can't be analyzed.

**Solution:** Ensure bytecode is valid before computing frames:
- Balanced stack operations
- Valid local variable indices
- Reachable code only
- Proper exception handler ranges

## Best Practices

### 1. Compute Frames Last

Always compute frames as the final step after all bytecode modifications:

```java
// Make all modifications first
bytecode.addILoad(0);
bytecode.addIConst(1);
// ... more modifications ...
bytecode.addReturn(ReturnType.IRETURN);

// Finalize and compute frames
bytecode.finalizeBytecode();
classFile.computeFrames(method);
```

### 2. Use Class-Level Computation

When modifying multiple methods, compute frames once at the class level:

```java
for (MethodEntry method : classFile.getMethods()) {
    // Modify method
    Bytecode bc = new Bytecode(method);
    // ... modifications ...
    bc.finalizeBytecode();
}

// Compute all frames at once
classFile.computeFrames();
```

### 3. Handle Errors Gracefully

Frame computation may fail for invalid bytecode:

```java
try {
    classFile.computeFrames(method);
} catch (Exception e) {
    System.err.println("Frame computation failed for " + method.getName());
    System.err.println("Bytecode may be invalid: " + e.getMessage());
}
```

### 4. Verify with JVM

After modification, verify the class loads correctly:

```java
byte[] classBytes = classFile.write();

// Verify by loading
ClassLoader loader = new ClassLoader() {
    @Override
    protected Class<?> findClass(String name) {
        return defineClass(name, classBytes, 0, classBytes.length);
    }
};

try {
    Class<?> clazz = loader.loadClass(classFile.getClassName().replace('/', '.'));
    System.out.println("Class verified successfully");
} catch (VerifyError e) {
    System.err.println("Verification failed: " + e.getMessage());
}
```

## Complete Example

```java
public void modifyAndVerify(ClassFile classFile) throws IOException {
    for (MethodEntry method : classFile.getMethods()) {
        if (method.getCodeAttribute() == null) continue;
        if (method.getName().startsWith("<")) continue;

        // Add entry logging
        Bytecode bc = new Bytecode(method);
        bc.setInsertBefore(true);

        bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
        bc.addLdc("Entering: " + method.getName());
        bc.addInvokeVirtual("java/io/PrintStream", "println",
                          "(Ljava/lang/String;)V");

        bc.finalizeBytecode();
    }

    // Compute frames for all modified methods
    int frameCount = classFile.computeFrames();
    System.out.println("Computed frames for " + frameCount + " methods");

    // Rebuild and write
    classFile.rebuild();
    byte[] bytes = classFile.write();

    // Verify
    Files.write(Path.of("Modified.class"), bytes);

    // Test loading
    try {
        Class<?> clazz = new URLClassLoader(
            new URL[]{new File(".").toURI().toURL()}
        ).loadClass("Modified");
        System.out.println("Verification successful");
    } catch (Exception e) {
        System.err.println("Verification failed: " + e.getMessage());
    }
}
```

---

[<- Back to README](../README.md) | [SSA Transforms](ssa-transforms.md)
