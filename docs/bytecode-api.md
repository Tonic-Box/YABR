[<- Back to README](../README.md) | [Class Files](class-files.md) | [Generation API](generation-api.md) | [Visitors ->](visitors.md)

# Bytecode API

YABR provides two APIs for bytecode manipulation: the high-level `Bytecode` class and the low-level `CodeWriter` class.

## Bytecode Class

The `Bytecode` class provides a convenient, high-level API for common bytecode operations.

### Creating a Bytecode Instance

```java
// From a MethodEntry
Bytecode bytecode = new Bytecode(methodEntry);

// From a CodeWriter
Bytecode bytecode = new Bytecode(codeWriter);
```

### Loading Values

#### Local Variables

```java
// Reference types (objects)
bytecode.addALoad(0);   // Load 'this' (for instance methods)
bytecode.addALoad(1);   // Load first reference parameter

// Integers
bytecode.addILoad(0);   // Load int from local 0
bytecode.addILoad(1);   // Load int from local 1

// Other primitives
bytecode.addLLoad(2);   // Load long from local 2
bytecode.addFLoad(3);   // Load float from local 3
bytecode.addDLoad(4);   // Load double from local 4

// Generic load based on descriptor
bytecode.addLoad(1, "I");              // int
bytecode.addLoad(1, "J");              // long
bytecode.addLoad(1, "Ljava/lang/String;"); // object
```

#### Constants

```java
// Integers
bytecode.addIConst(0);    // iconst_0
bytecode.addIConst(5);    // iconst_5
bytecode.addIConst(100);  // bipush 100
bytecode.addIConst(1000); // sipush 1000
bytecode.addIConst(100000); // ldc (from constant pool)

// Longs
bytecode.addLConst(0L);   // lconst_0
bytecode.addLConst(1L);   // lconst_1
bytecode.addLConst(100L); // ldc2_w

// Floats
bytecode.addFConst(0.0f); // fconst_0
bytecode.addFConst(1.0f); // fconst_1
bytecode.addFConst(2.0f); // fconst_2
bytecode.addFConst(3.14f); // ldc

// Doubles
bytecode.addDConst(0.0);  // dconst_0
bytecode.addDConst(1.0);  // dconst_1
bytecode.addDConst(2.718); // ldc2_w

// Null
bytecode.addAConstNull(); // aconst_null

// Strings
bytecode.addLdc("Hello, World!");
```

### Storing Values

```java
bytecode.addAStore(1);  // Store reference to local 1
bytecode.addIStore(2);  // Store int to local 2
```

### Field Access

```java
ConstPool cp = bytecode.getConstPool();

// Using field reference index
int fieldRef = cp.getIndexOf(cp.findOrAddField("com/example/MyClass", "myField", "I"));
bytecode.addGetField(fieldRef);
bytecode.addPutField(fieldRef);

// Static fields
bytecode.addGetStatic(fieldRef);
bytecode.addPutStatic(fieldRef);

// Using strings (adds to constant pool automatically)
bytecode.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
```

### Method Invocation

```java
// Using method reference index
int methodRef = cp.getIndexOf(cp.findOrAddMethodRef(classIdx, natIdx));
bytecode.addInvokeVirtual(methodRef);
bytecode.addInvokeSpecial(methodRef);  // constructors, super calls
bytecode.addInvokeStatic(methodRef);

// Using strings
bytecode.addInvokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
bytecode.addInvokeStatic("java/lang/Math", "max", "(II)I");

// Interface methods
bytecode.addInvokeInterface(interfaceMethodRef, argCount);

// Dynamic invocation
bytecode.addInvokeDynamic(cpIndex);
```

### Object Creation

```java
// New instance
int classRef = cp.getIndexOf(cp.findOrAddClass("java/lang/StringBuilder"));
bytecode.addNew(classRef);
```

### Control Flow

```java
// Labels
bytecode.defineLabel("loop_start");

// Goto
bytecode.addGoto("loop_start");
bytecode.addGotoW("loop_start");  // Wide goto for large offsets

// Increment local variable
bytecode.addIInc(0, 1);   // local[0]++
bytecode.addIInc(1, -1);  // local[1]--
```

### Return Instructions

```java
// Using ReturnType enum
bytecode.addReturn(ReturnType.RETURN);   // void
bytecode.addReturn(ReturnType.IRETURN);  // int
bytecode.addReturn(ReturnType.LRETURN);  // long
bytecode.addReturn(ReturnType.FRETURN);  // float
bytecode.addReturn(ReturnType.DRETURN);  // double
bytecode.addReturn(ReturnType.ARETURN);  // reference

// Using opcode
bytecode.addReturn(0xB1);  // return
bytecode.addReturn(0xAC);  // ireturn

// From descriptor
bytecode.addReturn(ReturnType.fromDescriptor("I"));  // ireturn
bytecode.addReturn(ReturnType.fromDescriptor("V"));  // return
```

### Insertion Mode

By default, instructions are appended to the end. You can insert before existing code:

```java
bytecode.setInsertBefore(true);
bytecode.setInsertBeforeOffset(0);  // Insert at beginning

// Now instructions will be inserted at the beginning
bytecode.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
bytecode.addLdc("Method entry");
bytecode.addInvokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
```

### Finalizing

After making changes, finalize to write back to the method:

```java
bytecode.finalizeBytecode();

// Check if modifications were made
if (bytecode.isModified()) {
    // Optionally compute stack frames
    bytecode.computeFrames();
}
```

## CodeWriter Class

For more control, use `CodeWriter` directly:

```java
CodeWriter cw = new CodeWriter(methodEntry);

// Get existing instructions
List<Instruction> instructions = cw.getInstructions();

// Insert at specific offset
cw.insertInvokeVirtual(offset, methodRefIndex);
cw.insertGetStatic(offset, fieldRefIndex);
cw.insertALoad(offset, varIndex);
cw.insertILoad(offset, varIndex);
cw.insertIInc(offset, varIndex, increment);
cw.insertLDC(offset, cpIndex);
cw.insertLDCW(offset, cpIndex);

// Append instructions
cw.appendInstruction(instruction);
cw.insertInstruction(offset, instruction);

// Get bytecode info
int size = cw.getBytecodeSize();
boolean endsWithReturn = cw.endsWithReturn();

// Write changes
cw.write();

// Frame computation
cw.computeFrames();
cw.forceComputeFrames();
```

### Structural Editing (by instruction handle)

Beyond offset-based insertion, `CodeWriter` supports editing keyed on an instruction **handle** (the
`Instruction` object itself). Every structural edit runs a relink/layout pass that recomputes
instruction offsets, branch/switch targets (by identity — so jumps survive arbitrary shifts), switch
padding, the exception table, and the StackMapTable. (This also fixes a former hazard where inserting
inside a branch span left stale branch offsets.)

```java
CodeWriter cw = new CodeWriter(methodEntry);
Instruction handle = cw.getInstructions().iterator().next();

cw.insertBefore(handle, newInstr);        // or a List<Instruction> (e.g. a spliced body)
cw.insertAfter(handle, newInstr);
cw.replaceInstruction(handle, replacement); // branches that targeted `handle` are retargeted
cw.removeInstruction(handle);               // throws if `handle` is a branch/switch target

// When building branches that jump to a known instruction, register the target by identity:
cw.setBranchTarget(branchInstr, targetInstr);
cw.setSwitchTargets(switchInstr, defaultTarget, caseTargets);

// Replace the whole body (e.g. a cloned/grafted method body); offsets/frames are rebuilt:
cw.replaceBody(instructionList);
cw.replaceBody(instructionList, exceptionTableEntries);
```

### Cloning a range

`cloneRange` copies a contiguous instruction range into a fresh, self-contained list, shifting every
local-variable index by `localOffset` and recomputing internal branch/switch targets for the cloned
block — the mechanical building block for inlining a method body.

```java
List<Instruction> body = cw.cloneRange(first, last, localOffset);
host.insertBefore(callSite, body);   // splice the cloned body in; relink wires it up
```
Shifted local-variable ops use the narrowest valid encoding: the compact `xload_<n>` form for index
0–3, the general form for 4–255, and the `wide` form beyond 255.

To splice a body **across constant pools** into a method the target already owns (e.g. merging into an
existing `<clinit>`), drive the public `ConstPoolRemapper` directly — it re-resolves every operand
(including bootstrap methods) into the target pool:
```java
ConstPoolRemapper remap = new ConstPoolRemapper(sourceClass, targetClass);
CodeWriter.ClonedRange cr = sourceCw.cloneRangeWithTargets(first, last, base, targetPool, remap::remap);
targetCw.replaceBody(cr);            // or insertBefore(handle, cr) to merge rather than replace
```

### Reference retargeting and cross-class grafting

`ConstPool`/`ClassFile` can repoint every member reference (method/field/interface) from one owner to
another, and `MethodGrafter` copies a method into another class, re-resolving its constant-pool
references symbolically into the target pool (and regenerating frames):

```java
import com.tonic.analysis.MethodGrafter;

// Redirect every Sound.* member reference to Game.* (pool-wide):
int n = gameClass.redirectOwner("pkg/Sound", "pkg/Game");

// Move a method from one ClassFile into another (returns the new method on the target):
MethodEntry moved = MethodGrafter.graftMethod(soundClass, soundMethod, gameClass);
gameClass.redirectOwner("pkg/Sound", "pkg/Game"); // repoint its self-calls, if desired
```

`graftMethod` remaps method/field/interface/class references, `ldc` constants
(String/Class/int/float/long/double/MethodHandle/MethodType), and `invokedynamic`/dynamic constants —
for the latter the referenced bootstrap method (handle + static arguments) is copied and remapped into
the target's `BootstrapMethods` attribute. Structural edits (including the relink after a graft) also
**widen branches automatically** (`goto`→`goto_w`, and an over-long conditional becomes an inverted
conditional skipping a `goto_w`) when an edit pushes a branch span past ±32 KB.

### Frameless write

To serialize without a `StackMapTable` (e.g. for tooling that emits frame-free class versions or
recomputes frames downstream), strip the attribute before writing. A frameless class only loads where
frames aren't required (class major version &lt; 50, or `-Xverify:none`).

```java
classFile.stripStackMapTables();
byte[] bytes = classFile.write();
```

## Common Patterns

### Print Statement

```java
// System.out.println("Hello");
bytecode.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
bytecode.addLdc("Hello");
bytecode.addInvokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
```

### Call Super Constructor

```java
// super();
bytecode.addALoad(0);  // this
int superInit = cp.getIndexOf(cp.findOrAddMethodRef(
    cp.getIndexOf(cp.findOrAddClass("java/lang/Object")),
    cp.getIndexOf(cp.findOrAddNameAndType(
        cp.getIndexOf(cp.findOrAddUtf8("<init>")),
        cp.getIndexOf(cp.findOrAddUtf8("()V"))
    ))
));
bytecode.addInvokeSpecial(superInit);
```

### Get and Set Field

```java
// this.field = value;
bytecode.addALoad(0);      // this
bytecode.addILoad(1);      // value parameter
bytecode.addPutField(fieldRef);

// return this.field;
bytecode.addALoad(0);      // this
bytecode.addGetField(fieldRef);
bytecode.addReturn(ReturnType.IRETURN);
```

### Static Field Access

```java
// MyClass.staticField = 42;
bytecode.addIConst(42);
bytecode.addPutStatic(fieldRef);

// int x = MyClass.staticField;
bytecode.addGetStatic(fieldRef);
bytecode.addIStore(1);
```

## Instruction Types

YABR supports all JVM instructions through the `com.tonic.analysis.instruction` package:

| Category | Examples |
|----------|----------|
| Constants | `IConstInstruction`, `LConstInstruction`, `LdcInstruction` |
| Loads | `ALoadInstruction`, `ILoadInstruction`, `LLoadInstruction` |
| Stores | `AStoreInstruction`, `IStoreInstruction`, `LStoreInstruction` |
| Stack | `DupInstruction`, `PopInstruction`, `SwapInstruction` |
| Math | `IAddInstruction`, `ISubInstruction`, `IMulInstruction` |
| Conversion | `I2LInstruction`, `L2IInstruction`, `I2FInstruction` |
| Comparison | `IfInstruction`, `IfIcmpInstruction`, `IfAcmpInstruction` |
| Control | `GotoInstruction`, `ReturnInstruction`, `SwitchInstruction` |
| Reference | `GetFieldInstruction`, `PutFieldInstruction`, `NewInstruction` |
| Invoke | `InvokeVirtualInstruction`, `InvokeStaticInstruction` |

## Stack Frame Computation

After modifying bytecode, you may need to recompute StackMapTable frames:

```java
// Via Bytecode
bytecode.computeFrames();

// Via CodeWriter
codeWriter.computeFrames();

// Via ClassFile (all methods)
classFile.computeFrames();

// Via ClassFile (specific method)
classFile.computeFrames("methodName", "(II)I");
```

---

[<- Back to README](../README.md) | [Class Files](class-files.md) | [Generation API](generation-api.md) | [Visitors ->](visitors.md)
