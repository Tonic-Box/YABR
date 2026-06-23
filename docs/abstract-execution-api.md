[<- Back to Analysis APIs](analysis-apis.md)

# Abstract Execution API

The Abstract Execution API (`com.tonic.analysis.absexec`) is a path-exploring interpreter over a single method's
bytecode that builds operand-stack and local-variable def-use links without an abstract value domain. It answers
provenance questions directly: which instruction produced the value an instruction popped, which instructions read
a value a store wrote, and what the original pushing instruction of a value is after following local store/load
hops.

## Overview

| Aspect | Simulation API | Execution API | Abstract Execution API |
|--------|----------------|---------------|------------------------|
| **Purpose** | Static metrics over IR | Concrete execution | Operand-stack/local def-use |
| **Input** | SSA IR (`IRMethod`) | Raw bytecode (`MethodEntry`) | Raw bytecode (`MethodEntry` or instruction list) |
| **State** | Immutable snapshots | Mutable runtime state | Per-path frame of stack/local contexts |
| **Values** | Abstract (`SimValue`) | Concrete (`ConcreteValue`) | None; constants read from the pushing instruction |
| **Scope** | Intra/inter-procedural | Inter-procedural | Intra-procedural |
| **Output** | Metrics, value flow | Result and heap state | One `InsnContext` per executed instruction |

The interpreter forks one path per branch target, so an instruction executed on several paths produces several
`InsnContext` records. It does not model an abstract value domain; constants and operands are recovered by reading
them off the producing instruction through the def-use links.

---

## Quick Start

```java
import com.tonic.analysis.absexec.*;
import com.tonic.analysis.instruction.Instruction;

List<InsnContext> contexts = new ArrayList<>();
new Execution(method)
    .addVisitor(contexts::add)
    .run();

// Inspect one instruction's operands
for (InsnContext ctx : contexts) {
    for (StackCtx pop : ctx.getPops()) {
        Instruction producer = pop.getPushed().getInstruction();
        // producer pushed a value that ctx.getInstruction() popped
    }
}
```

The visitor receives every executed instruction context. `wasExecuted(Instruction)` reports whether an instruction
was reached on any path.

---

## Core Concepts

### Execution

`Execution` drives the interpretation. Construct it from a method, register one or more visitors, and call `run()`.

```java
Execution exec = new Execution(method);
exec.addVisitor(ctx -> { /* ... */ });
exec.run();
boolean reached = exec.wasExecuted(someInstruction);
```

A second constructor takes a caller-provided instruction list:

```java
List<Instruction> insns = new CodeWriter(method).getInstructionList();
new Execution(method, insns).addVisitor(visitor).run();
```

Use this form when the caller will subsequently mutate those instructions, so the `Instruction` objects returned by
`InsnContext.getInstruction()` are identical to the ones the caller reads and writes back.

### InsnContext

`InsnContext` records one execution of one instruction: the stack values it popped and pushed, the locals it read,
and the frames it branched to. Each popped or pushed value is a `StackCtx`; each local read is a `VarCtx`.

| Method | Returns | Description |
|--------|---------|-------------|
| `getInstruction()` | `Instruction` | The instruction this context executed |
| `getPops()` | `List<StackCtx>` | Operand-stack values popped, in pop order |
| `getPushes()` | `List<StackCtx>` | Operand-stack values pushed |
| `getReads()` | `List<VarCtx>` | Local-variable slots read |
| `getBranches()` | `List<Frame>` | Frames forked at this instruction (branches/switches) |
| `getFrame()` | `Frame` | The path state this context executed in |
| `resolve()` | `InsnContext` | The originating context after following store/load hops |

### StackCtx

A `StackCtx` is one operand-stack slot. It links the value to the `InsnContext` that pushed it and the contexts that
popped it. A long or double occupies a single wide slot.

| Method | Returns | Description |
|--------|---------|-------------|
| `getPushed()` | `InsnContext` | The instruction-execution that pushed this value |
| `getPopped()` | `List<InsnContext>` | The instruction-executions that popped this value |
| `isWide()` | `boolean` | True for long/double values |

### VarCtx

A `VarCtx` is one local-variable slot's contents. It links to the `InsnContext` that stored it and the contexts that
read it. Entry parameters have no storing instruction.

| Method | Returns | Description |
|--------|---------|-------------|
| `getInstructionWhichStored()` | `InsnContext` | The store that wrote this value, or null for a parameter |
| `getRead()` | `List<InsnContext>` | The instruction-executions that read this value |
| `isParameter()` | `boolean` | True for an entry parameter slot |
| `isWide()` | `boolean` | True for long/double values |

### resolve

`InsnContext.resolve()` follows a value back to the instruction that actually produced it, skipping single
store/load hops:

- For a `putfield`/`putstatic` or a store, it resolves the value being set (the first popped operand).
- For a load, it follows the local's storing instruction, unless the local is an entry parameter.
- For any other instruction, it returns the same context.

This recovers, for example, the constant behind `value = ldc K; istore v; ... iload v` as the `ldc` context.

---

## Branching and Termination

Each branch target forks a fresh `Frame` that copies the operand stack and local table and continues from the
target. A per-method visited-edge guard stops a path from re-entering a branch edge it already took, which
terminates loops. The total number of frame runs is bounded by the number of distinct branch edges.

A pathologically branchy method can still spawn many frames. The `absexec.framecap` system property (default 4000)
caps the number of frame runs; once it is exceeded the run stops and the remainder of the method is left
un-analyzed. `wasExecuted` and the recorded contexts then reflect partial coverage.

```
-Dabsexec.framecap=8000
```

---

## Modelling and Limitations

- There is no abstract value domain. Read constants and operands from the producing instruction through the
  def-use links and `resolve()`.
- Analysis is intra-procedural; calls are modelled by their stack effect only.
- Each value, including long and double, is one logical `StackCtx`, so pop and push counts are uniform.
- Field access, constants, multiplies, `dup`/`swap` forms, loads, stores, and branches are modelled precisely.
  Other instructions fall back to their declared stack change, so their `InsnContext` records the correct pop/push
  counts but not per-operand structure.
- Large or highly branchy methods may be partially analyzed once `absexec.framecap` is reached.

---

[<- Back to Analysis APIs](analysis-apis.md)
