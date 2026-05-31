[<- Back to README](../README.md) | [LLVM Lowering](llvm-lowering.md) | [SSA Guide](ssa-guide.md) | [SSA Transforms](ssa-transforms.md)

# LLVM IR Lifting

YABR can lift textual LLVM IR (`.ll`) back to SSA IR — the reverse of [LLVM Lowering](llvm-lowering.md). Together they close the loop for the **LLVM optimizer round-trip**:

```
YABR SSA IR
    |
    v
LlvmLowering.lower()  →  .ll text
    |
    v  (optional: run opt -O2 -S)
optimized .ll text
    |
    v
LlvmLifter.lift()  →  IRMethod
    |
    v
SSA.runTransforms()  →  optimize further
    |
    v
SSA.lower()  →  bytecode
```

You get LLVM's scalar passes (constant propagation, loop unrolling, inlining, ...) for free, applied directly to YABR IR.

## Scope

The lifter handles the **computational subset** produced by `LlvmLowering` under `ObjectModel.NONE`:

| Supported | Not supported |
|---|---|
| Integer / long / float / double arithmetic | `jvm_*` runtime-ABI calls |
| All type conversions (`sext`, `trunc`, `sitofp`, ...) | Object/heap operations |
| Phi functions | Try/catch landingpads |
| Integer branches (`icmp` + `br i1`) | |
| `switch` | |
| `goto` (`br label`) | |
| `return` (void and value) | |
| Static method calls | |

Synthesized temporaries that the lowerer emits for multi-step expansions are **folded back** to the originating single IR instruction:

| Multi-step LLVM emission | Folded back to |
|---|---|
| `and i32 %amount, 31` + `shl i32 %val, %mask` | `BinaryOpInstruction(SHL, val, amount)` |
| `and i32 %amount, 31` + `ashr i32 %val, %mask` | `BinaryOpInstruction(SHR, val, amount)` |
| `and i32 %amount, 63` + `lshr i64 %val, %mask` | `BinaryOpInstruction(USHR, val, amount)` |
| `trunc i32 %v to i8` + `sext i8 %t to i32` | `UnaryOpInstruction(I2B, v)` |
| `trunc i32 %v to i16` + `zext i16 %t to i32` | `UnaryOpInstruction(I2C, v)` |
| `trunc i32 %v to i16` + `sext i16 %t to i32` | `UnaryOpInstruction(I2S, v)` |

## Quick Start

```java
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.llvm.LlvmLowering;
import com.tonic.analysis.ssa.llvm.lift.LlvmLifter;

ConstPool cp = classFile.getConstPool();

// 1. Lift original bytecode to SSA
IRMethod original = new SSA(cp).lift(method);

// 2. Lower to LLVM IR
String ll = new LlvmLowering().lower(original);

// 3. (Optionally: run "opt -O2 -S" on ll here)

// 4. Lift back to SSA
IRMethod lifted = new LlvmLifter().lift(ll);
```

### Optimizer round-trip

```java
// Write .ll to a temp file, run opt, read back
Path tmp = Files.createTempFile("opt_", ".ll");
Files.writeString(tmp, ll);

Process p = new ProcessBuilder("opt", "-O2", "-S", tmp.toString(), "-o", "-")
    .redirectErrorStream(true)
    .start();
String optimized = new String(p.getInputStream().readAllBytes());
p.waitFor();

// Lift optimized IR back to SSA
IRMethod optimizedIr = new LlvmLifter().lift(optimized);

// Lower back to bytecode using the original MethodEntry for metadata
SSA ssa = new SSA(cp);
ssa.lower(optimizedIr, methodEntry);
```

### Lower an entire module and lift back

```java
// Lower several methods into one .ll module
List<IRMethod> methods = classFile.getMethods().stream()
    .filter(m -> m.getCodeAttribute() != null)
    .map(m -> ssa.lift(m))
    .collect(Collectors.toList());

String module = new LlvmLowering().lowerToModule(methods);

// Lift all functions back
List<IRMethod> liftedMethods = new LlvmLifter().liftModule(module);
```

## Public API

The module exposes exactly two public types; everything else is package-private.

### `LlvmLifter`

| Method | Description |
|---|---|
| `LlvmLifter()` | Construct with default config. |
| `LlvmLifter(LlvmLifterConfig)` | Construct with a custom config. |
| `IRMethod lift(String)` | Parse the first `define` in the module text and return it as an `IRMethod`. Throws `LlvmLiftException` if none found. |
| `List<IRMethod> liftModule(String)` | Parse all `define` functions in the module text and return them in order. |

### `LlvmLifterConfig`

Currently a placeholder; exists to keep the API symmetric with `LlvmLoweringConfig`. Call `LlvmLifterConfig.defaults()` or omit it.

### `LlvmLiftException`

Thrown on parse errors (unknown type, undefined register, missing block label). Extends `RuntimeException`.

## How it works

The lifter is a two-pass construction over the LLVM text:

**Pass 1 — allocation.** Walk all instruction lines to collect every `%v<id>` assignment and its LLVM type. Allocate one `SSAValue` per register. Allocate one `IRBlock` per label line. Register method parameters from the `define` signature.

**Pass 2 — construction.** Walk each block's instruction lines, dispatch on opcode, and construct the corresponding `IRInstruction`. Each `BinaryOpInstruction`, `UnaryOpInstruction`, `PhiInstruction`, `BranchInstruction`, `SwitchInstruction`, `ReturnInstruction`, or `InvokeInstruction` is appended to its block and CFG edges are wired from terminators. `icmp` temporaries are buffered and consumed by the subsequent `br i1` to produce a single `BranchInstruction`. Phi incoming arms are resolved after all blocks are processed (forward refs are safe because SSA values are named by stable id).

**TempFoldingPass.** After construction, a structural-pattern pass collapses the known multi-step expansions (shift mask, narrowing conversions) back to single IR instructions by inspecting the raw RHS string stored for each `%t<n>` temporary.

## Naming conventions in the lifted IR

| LLVM operand | SSA form |
|---|---|
| `%v<id>` | `SSAValue` with the same id |
| `%t<n>` | Consumed by folding; any unconsumed one becomes a `SSAValue` with auto-id (DCE removes it) |
| `B<id>:` | `IRBlock` with label `B<id>` |
| `@"owner.name(desc)"` | `InvokeInstruction` with owner/name/descriptor demangled |

## Demo

`com.tonic.demo.LlvmRoundTripDemo` shows the round-trip:

```bash
# Built-in add() method — lowers, lifts, re-lowers, checks equality
java -cp build/classes/java/main com.tonic.demo.LlvmRoundTripDemo

# A class file — lowers and lifts each method
java -cp build/classes/java/main com.tonic.demo.LlvmRoundTripDemo MyClass.class
```

If `opt` is installed the demo also runs `opt -O2 -S` and prints the lifted, optimized IR.

## Known limitations

- **Computational subset only.** LLVM IR produced under `ObjectModel.RUNTIME_ABI` (the full object model) can be parsed on a best-effort basis, but `jvm_*` ABI calls and `landingpad` blocks are silently skipped — the resulting `IRMethod` will be incomplete.
- **3-way compare expansion.** The `select`-chain produced by `lcmp`/`fcmp[lg]`/`dcmp[lg]` is not yet folded back to the originating compare op; it materializes as raw `BinaryOpInstruction` nodes. The lowerer re-emits the correct expansion on the next lower pass, so the round-trip is still lossless for the purpose of `lowerToModule`.
- **`opt` output.** After LLVM's optimizer runs, new temporaries and restructured control flow may appear. The lifter handles them correctly for arithmetic and branches; complex LLVM idioms (e.g., vector ops, intrinsics) are skipped.

---

## Next Steps

- [LLVM Lowering](llvm-lowering.md) - the lowering direction and the runtime ABI
- [SSA Guide](ssa-guide.md) - the IR produced by the lifter
- [SSA Transforms](ssa-transforms.md) - optimization passes to run on the lifted IR

---

[<- Back to README](../README.md) | [LLVM Lowering](llvm-lowering.md) | [SSA Guide](ssa-guide.md) | [SSA Transforms](ssa-transforms.md)
