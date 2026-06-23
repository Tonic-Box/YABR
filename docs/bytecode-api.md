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

// Iterate existing instructions, or get a random-access snapshot
Iterable<Instruction> it = cw.getInstructions();
List<Instruction> instructions = cw.getInstructionList();   // bytecode-ordered, identity-indexed

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
cw.writeWithoutMaxStack();   // serialize without the CFG max_stack pass (provisional bound only)

// Frame computation
cw.computeFrames();
cw.forceComputeFrames();
```

### Structural Editing (by instruction handle)

Beyond offset-based insertion, `CodeWriter` supports editing keyed on an instruction **handle** (the
`Instruction` object itself). Every structural edit runs a relink/layout pass that recomputes
instruction offsets, branch/switch targets, switch padding, the exception table, and the StackMapTable
— all **by identity**, so jumps and try/catch boundaries survive arbitrary shifts. (This also fixes
former hazards where inserting inside a branch span left stale branch offsets, and where a spliced
block's offsets collided with the host's and corrupted the exception table.)

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

A `ClonedRange` (from `cloneRangeWithTargets` or `CodeBuilder.assemble`) carries its branch/switch
targets **and** any try/catch regions by identity, so `replaceBody(cr)` / `insertBefore(handle, cr)` /
`insertAfter(handle, cr)` splice an exception-bearing body that relinks correctly wherever it lands.

A `ClonedRange` can also carry branches that exit into the host: **external labels** (bind to a host
instruction before splicing via `cr.bindLabel(name, hostInsn)` or the
`insertBefore`/`insertAfter(handle, cr, Map<String,Instruction>)` overloads) and **continuation
branches** (auto-bound to the splice successor — `insertBefore` → the handle, `insertAfter` → the
instruction after it; `replaceBody` has no successor and rejects them). See
[Generation API](generation-api.md) for authoring these via `CodeBuilder.externalLabel` / a tail label,
and the cloning section below for `redirectReturns()`.

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

**Control transfers that leave the range.** A cloned range can exit into the host, reusing the
identity-based splice binding:

- **Out-of-range branches** (a partial range with a `break`/jump to a shared exit) carry their original
  target by identity, so the cloned branch resolves against the host at splice time. This is automatic
  and **same-method-only**: splicing such a range into a *different* method (where the carried target
  doesn't exist) throws `IllegalStateException` at splice rather than producing a class-load
  `VerifyError`. (Cross-method relocation of an exiting branch isn't supported; rebind with
  `setBranchTarget` if you must.)
- **Returns** can be redirected so an inlined body continues after the splice point instead of
  returning from the host — opt in on the produced range:
  ```java
  CodeWriter.ClonedRange body = sourceCw.cloneRangeWithTargets(first, last, base, pool, remap::remap);
  body.redirectReturns();                  // cloned returns -> continuation gotos
  hostCw.insertBefore(continuationPoint, body);   // they bind to the splice successor
  ```
  Each cloned `return` becomes a continuation branch (bound like a tail label: `insertBefore` → the
  handle, `insertAfter` → the instruction after it); `replaceBody` has no successor and rejects them.
  A value-returning body leaves its result on the stack at the continuation. Leave `redirectReturns()`
  off to relocate a body whose returns should stay returns.

To fold **several** redirected bodies before one instruction, use `insertChainBefore(at, bodies)` — it
chains them so each body's continuation falls through into the next and the last into `at`. Do **not**
hand-stack them with repeated `insertBefore(at, …)`: that binds every body's continuation to `at`, so
earlier bodies skip later ones (a silent miscompile).
```java
hostCw.insertChainBefore(callSuccessor, List.of(bodyA.redirectReturns(), bodyB.redirectReturns()));
```

### Reference retargeting and cross-class grafting

`ConstPool`/`ClassFile` can canonicalize every reference to a class from one name to another, and
`MethodGrafter` copies a method into another class, re-resolving its constant-pool references
symbolically into the target pool (and regenerating frames):

```java
import com.tonic.analysis.MethodGrafter;

// Rewrite EVERY reference to Sound so it names Game (pool-wide):
int n = gameClass.redirectClassReferences("pkg/Sound", "pkg/Game");   // redirectOwner is an alias

// Move a method from one ClassFile into another (returns the new method on the target):
MethodEntry moved = MethodGrafter.graftMethod(soundClass, soundMethod, gameClass);
gameClass.redirectClassReferences("pkg/Sound", "pkg/Game"); // repoint its self-calls, if desired

// Or overwrite an existing method's body in place (same remapping), keeping the target's member entry
// and order - which is what makes a JVMTI live redefine accept the result (bodies may change, the member
// set may not):
MethodGrafter.replaceMethodBody(srcClass, srcMethod, targetClass, targetMethod);
```

`redirectClassReferences` (and its alias `redirectOwner`) rewrites *all* reference kinds, since they
share a `CONSTANT_Class`: `new`/`checkcast`/`instanceof`/`anewarray` operands, exception `catch_type`,
member-ref owners, `invokedynamic` bootstrap handle/arg classes, **and** class names embedded in
method/field/`MethodType` descriptors and array class refs (`[LB;`). The `ClassFile` form also refreshes
the cached descriptor/owner on its fields and methods. **Not** rewritten: class names in generic
`Signature` attributes — use the [Renamer](renamer-api.md) for generic-aware full renames.

`graftMethod` and `replaceMethodBody` remap method/field/interface/class references, `ldc` constants
(String/Class/int/float/long/double/MethodHandle/MethodType), and `invokedynamic`/dynamic constants —
for the latter the referenced bootstrap method (handle + static arguments) is copied and remapped into
the target's `BootstrapMethods` attribute. Structural edits (including the relink after a graft) also
**widen branches automatically** (`goto`→`goto_w`, and an over-long conditional becomes an inverted
conditional skipping a `goto_w`) when an edit pushes a branch span past ±32 KB.

The bootstrap-method table is reachable directly on `ClassFile`, which is what the graft path and
hand-built `invokedynamic` (e.g. `CodeBuilder.assemble`) both use:

```java
BootstrapMethodsAttribute bsm = classFile.getBootstrapMethodsAttribute();   // null if none yet
int index = classFile.addBootstrapMethod(methodHandleIndex, argIndices);    // find-or-create + dedup
```

`addBootstrapMethod` creates the `BootstrapMethods` attribute on first use, returns an existing entry's
index when the handle and static arguments already match, and otherwise appends — so repeated calls
never duplicate an identical bootstrap.

Verbose disassembly (`DisassemblyOptions.verbose()` / `withResolveBootstraps`, see
[class-files.md](class-files.md#verbose-disassembly)) resolves these bootstraps for both
`invokedynamic` (`// BSM: …`) and `CONSTANT_Dynamic` loads (`// condy BSM: …`), rendering a
`StringConcatFactory` recipe with readable `{arg}`/`{const}` markers.

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

The four owner-bearing invokes — `Invoke{Virtual,Special,Static,Interface}Instruction` — implement the
common interface **`InvokeInsn`** (`getOwnerClass()`/`getMethodName()`/`getMethodDescriptor()`, plus
`isStatic()`/`isInterface()`), so a call site of any kind is handled uniformly:
```java
if (insn instanceof InvokeInsn call) {
    cg.addEdge(call.getOwnerClass(), call.getMethodName(), call.getMethodDescriptor());
}
```
`invokedynamic` (`InvokeDynamicInstruction`) is intentionally excluded — it has no owning class.

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

### max_stack

`max_stack` is computed over the control-flow graph, not a linear scan — it accounts for every
reachable path (loop back-edges, joins, and exception-handler entry states, where the JVM pushes the
caught exception). `write()` and the relink after any structural edit apply it automatically, so you
no longer need to set `max_stack` by hand after generating branches or loops. It is raise-only (never
under-counts; an existing larger value is left as-is). To force it explicitly:

```java
int maxStack = codeWriter.computeMaxStack();   // CFG-correct; updates the CodeAttribute
```

When a caller makes many edits across several passes and recomputes frames or `max_stack` once at the end,
`writeWithoutMaxStack()` serializes the relinked instructions without the per-write CFG max_stack pass. It sets a
provisional `max_stack`/`max_locals` from a linear scan only, so the final `computeFrames()` or `computeMaxStack()`
must still run before the class is used.

---

[<- Back to README](../README.md) | [Class Files](class-files.md) | [Generation API](generation-api.md) | [Visitors ->](visitors.md)
