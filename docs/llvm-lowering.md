[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [SSA Transforms](ssa-transforms.md) | [LLVM Lifting ->](llvm-lifting.md) | [Migration Guide](SSA_IR_MIGRATION.md)

# LLVM IR Lowering

YABR can lower SSA IR to **textual LLVM IR** (`.ll`). This is a leaf backend that sits at the end of the SSA pipeline, parallel to the bytecode lowerer: where `BytecodeLowerer` targets the JVM, `LlvmLowering` targets LLVM. It is the first building block of an eventual Java -> native / WebAssembly pipeline.

```
                          +--[BytecodeLowerer]--> Bytecode (JVM)
Bytecode --[lift]--> SSA IR
                          +--[LlvmLowering]-----> LLVM IR (.ll)  ->  lli / clang / wasm
```

Output is pure text, so the module adds **no native dependency** — you can emit `.ll` without LLVM installed, then feed it to `lli`, `clang`, or any LLVM toolchain.

## Two object models

The lowering is gated by `LlvmLoweringConfig.ObjectModel`:

- **`NONE`** (default) — the **computational subset**: arithmetic, conversions, control flow, phis, and static primitive calls. Anything touching the object/reference/runtime model routes to `UnsupportedLowering` (`UnsupportedOperationException: "LLVM lowering: <op> not yet supported"`). Output is self-contained and `lli`-runnable. This is the strict, sound boundary.
- **`RUNTIME_ABI`** (`LlvmLoweringConfig.fullObjectModel()`) — the **full construct set**: references map to opaque `ptr`, and object operations lower to `call`s into a documented `jvm_*` runtime ABI. Output is valid IR but **not runnable standalone** — a runtime library must implement the ABI + EH personality.

| Construct | `NONE` | `RUNTIME_ABI` |
|---|---|---|
| Arithmetic, conversions, phis, branches, `switch`, `goto`, `return`, static calls | ✅ | ✅ |
| Reference / array params & returns, instance methods (`this`) | rejected | ✅ (`ptr`) |
| `null`, reference compares (`if_acmp*`, `ifnull`) | rejected | ✅ (`icmp … ptr`) |
| Static fields (`getstatic`/`putstatic`) | rejected | ✅ (LLVM globals) |
| Instance fields, arrays, `arraylength`, `new`, `newarray` | rejected | ✅ (`jvm_*` ABI) |
| Virtual / interface / special / dynamic invoke | rejected | ✅ (vtable/itable lookup, indy ABI) |
| `checkcast` / `instanceof`, `athrow`, monitors | rejected | ✅ (`jvm_*` ABI) |
| String / Class `ldc` | rejected | ✅ (`jvm_intern_string` / `jvm_class_object`) |
| try/catch (exception handlers) | rejected | ✅ (`invoke`/`landingpad` + personality) |

### Runtime ABI (the `RUNTIME_ABI` contract)

References are opaque `ptr`; class/field/method identities are interned C-string names rather than resolved offsets/vtables (the backend stays layout-agnostic and decoupled from `ClassPool`). A future runtime library must implement these to link/run the emitted IR. Defined once in `LlvmRuntimeAbi`:

| Construct | Emitted IR |
|---|---|
| `new C` | `%o = call ptr @jvm_new(ptr @.str.C)` |
| `newarray` / `anewarray` | `call ptr @jvm_newarray_<kind>(i32 len)` / `@jvm_anewarray(ptr elem, i32 len)` (multi-dim: `@jvm_multianewarray`) |
| `arraylength` | `call i32 @jvm_arraylength(ptr a)` |
| array load/store | `call <t> @jvm_aload_<kind>(ptr a, i32 i)` / `call void @jvm_astore_<kind>(ptr a, i32 i, <t> v)` (`kind` ∈ z/b/c/s/i/j/f/d/a) |
| getfield/putfield | `call <t> @"jvm.gf owner.name desc"(ptr obj)` / `call void @"jvm.pf …"(ptr obj, <t> v)` |
| getstatic/putstatic | LLVM global `@"owner.name"` — `load`/`store`; defined `zeroinitializer` if the owner is lowered in the module, else `external global` |
| invokevirtual / interface | `%fp = call ptr @jvm_vtable_lookup(ptr recv, ptr methodId)` (or `@jvm_itable_lookup`) then `call <ret> %fp(ptr recv, …)` |
| invokespecial / static | direct `call <ret> @mangled(<recv?>, …)` |
| invokedynamic | per-call-site extern `call <ret> @"jvm.indy owner.name desc #cp"(…)` |
| checkcast / instanceof | `call ptr @jvm_checkcast(ptr o, ptr T)` / `call i32 @jvm_instanceof(ptr o, ptr T)` |
| String / Class `ldc` | `call ptr @jvm_intern_string(ptr bytes, i32 len)` / `call ptr @jvm_class_object(ptr name)` |
| athrow | `call void @jvm_throw(ptr e)` then `unreachable` |
| monitorenter / exit | `@jvm_monitor_enter(ptr)` / `@jvm_monitor_exit(ptr)` |
| try/catch | covered-region calls become `invoke … unwind label %Lpad`; `landingpad { ptr, i32 } cleanup`; `@jvm_match_catch(ptr exc, ptr T)` dispatch; the handler reads its exception via `@jvm_current_exception()`; the `define` carries `personality ptr @jvm_personality` |

**Approximations (honest scope).** invokedynamic is emitted as a **per-call-site ABI symbol**, not desugared into synthetic lambda classes — lambda/string-concat semantics live behind the runtime. Exception handling emits structurally valid `invoke`/`landingpad` IR against `@jvm_personality`, but is validated by **IR shape, not execution**, and only converts in-region `call`s (the common thrower) to `invoke`; nested/complex handler tables may need refinement. Static fields of `byte`/`short`/`char`/`boolean` collapse to `i32` globals (consistent with the operand-stack model).

## Quick Start

```java
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.llvm.LlvmLowering;

ConstPool cp = classFile.getConstPool();
IRMethod ir = new SSA(cp).lift(method);

// Computational subset (default): self-contained, lli-runnable
String ll = new LlvmLowering().lower(ir);

// Full object/runtime model: emits the jvm_* runtime ABI for the whole construct set
String full = new LlvmLowering(LlvmLoweringConfig.fullObjectModel()).lower(ir);
System.out.println(full);
```

The `SSA` facade also exposes a one-line convenience method that mirrors `SSA.lower`:

```java
SSA ssa = new SSA(cp);
String ll = ssa.toLlvm(ssa.lift(method));
```

### Example output

For a static `int add(int, int)`:

```llvm
define i32 @"T.add (II)I"(i32 %v0, i32 %v1) {
B0:
  %v4 = add i32 %v0, %v1
  ret i32 %v4
}
```

Parameters are bound to their SSA value names (`%v0`, `%v1`); blocks are labelled `B<id>` from the CFG; the function symbol is the mangled method reference (see [Symbol mangling](#symbol-mangling)). Dead `load local` / `store local` artifacts left over from lifting emit nothing — data flow is carried entirely by SSA values and phis.

## Public API

The module exposes exactly two public types; everything else is package-private.

### `LlvmLowering`

| Method | Description |
|---|---|
| `LlvmLowering()` | Construct with default config. |
| `LlvmLowering(LlvmLoweringConfig)` | Construct with a custom config (null falls back to defaults). |
| `String lower(IRMethod)` | Lower a single method to a complete module (sorted `declare`s + one `define`). |
| `String lowerToModule(List<IRMethod>)` | Lower several methods into one module; callees that are defined in the module are not re-declared. |

### `LlvmLoweringConfig`

Builder + presets, following the project's `DecompilerConfig` convention.

```java
LlvmLoweringConfig config = LlvmLoweringConfig.builder()
    .targetTriple("x86_64-unknown-linux-gnu")  // emitted as a module header, or null to omit
    .dataLayout("e-m:e-i64:64-f80:128-n8:16:32:64-S128")
    .emitDivisionGuards(false)                  // see "Known divergences"
    .objectModel(LlvmLoweringConfig.ObjectModel.RUNTIME_ABI)  // default NONE; see "Two object models"
    .build();

// Shorthand preset for the full object/runtime model:
LlvmLoweringConfig full = LlvmLoweringConfig.fullObjectModel();

String ll = new LlvmLowering(config).lower(ir);
```

`LlvmLoweringConfig.defaults()` emits no target header and raw LLVM arithmetic.

## How the lowering maps

### Type mapping

LLVM has no sub-word integer arithmetic types, so the JVM's `boolean`/`byte`/`char`/`short`/`int` all collapse to `i32` for operands (matching the JVM's own stack widening). Width only reappears inside narrowing conversions.

| IR type | LLVM type |
|---|---|
| `boolean`, `byte`, `char`, `short`, `int` | `i32` |
| `long` | `i64` |
| `float` | `float` |
| `double` | `double` |
| `void` | `void` |
| reference / array | rejected |

### Arithmetic and signedness

Java integer arithmetic is signed, so `DIV` -> `sdiv` and `REM` -> `srem`. Floating point uses `fadd`/`fsub`/`fmul`/`fdiv`/`frem`; `NEG` is `fneg` for floats and `sub <ty> 0, x` for integers.

### Shift masking

The JVM masks the shift count (`& 31` for `int`, `& 63` for `long`), but LLVM shifts are undefined behaviour when the count is `>= bitwidth`. The lowerer emits the mask explicitly:

```llvm
%t0 = and i32 %v1, 31
%v3 = lshr i32 %v0, %t0
```

`SHL` -> `shl`, `SHR` -> `ashr` (arithmetic / sign-extending), `USHR` -> `lshr` (logical / zero-filling). Shift amounts are zero-extended first when the value type is wider than the count.

### Conversions

| IR op | LLVM | Notes |
|---|---|---|
| `I2L` | `sext` | |
| `L2I` | `trunc` | |
| `I2F`, `I2D`, `L2F`, `L2D` | `sitofp` | signed int -> fp |
| `F2I`, `F2L`, `D2I`, `D2L` | `fptosi` | fp -> signed int |
| `F2D` | `fpext` | |
| `D2F` | `fptrunc` | |
| `I2B` | `trunc i8` + `sext` | sign-extend back to i32 |
| `I2C` | `trunc i16` + `zext` | **zero**-extend (char is unsigned) |
| `I2S` | `trunc i16` + `sext` | |

### Three-way compares

`lcmp`, `fcmpl`/`fcmpg`, `dcmpl`/`dcmpg` produce `-1`/`0`/`1` and have no native LLVM equivalent. They expand to two compares plus two `select`s. The float forms differ only in NaN handling: `...g` yields `+1` on NaN, `...l` yields `-1`, implemented by OR-ing the ordered compare with an unordered (`fcmp uno`) check.

```llvm
%t0 = icmp slt i64 %v0, %v1
%t1 = icmp sgt i64 %v0, %v1
%t2 = select i1 %t1, i32 1, i32 0
%v4 = select i1 %t0, i32 -1, i32 %t2
```

### Control flow

- **Branches** compute an `i1` via `icmp <pred>` (two-operand `if_icmp*`, or against `0` for `ifeq..ifle`), then `br i1 %c, label %B<true>, label %B<false>`. Predicates map `EQ/NE/LT/GE/GT/LE` -> `eq/ne/slt/sge/sgt/sle`.
- **`goto`** -> `br label %B<target>`.
- **`switch`** -> `switch i32 %key, label %B<default> [ i32 c, label %B<t> ... ]`, cases preserved in order for deterministic output.

### Phi functions

Phis lower to **native LLVM `phi`** — no SSA destruction, no parallel-copy insertion (unlike the JVM bytecode lowerer, which must destruct SSA). Because every SSA value is named `%v<id>` by its stable id before any block is walked, loop back-edge and forward references resolve symbolically with no ordering or fix-up pass:

```llvm
%v7 = phi i32 [%v2, %B0], [%v9, %B2]
```

### Static calls and declares

Static invokes lower to `call`; each distinct external callee is recorded and emitted as a sorted `declare` in the module header. Callees that are themselves defined in the same module (via `lowerToModule`) are not re-declared.

```llvm
declare double @"java/lang/Math.pow (DD)D"(double, double)
```

### Symbol mangling

Method symbols are **quoted** LLVM globals of the form `@"owner.name descriptor"`. Quoting lets the owner's `/`-separated internal name and the descriptor live in the symbol verbatim, and including the descriptor makes overloads collide-free. The same mangled string is used at the `define`, every `call`, and the `declare`, so they always line up.

### Determinism

Output is byte-identical across runs: value and label names come from stable ids, and `declare`s are sorted. This makes the backend output reproducible and diffable (covered by `LlvmLoweringDeterminismTest`).

## Known divergences

These are documented gaps where raw LLVM differs from JVM semantics, each with a designed extension point:

- **Integer division** — `sdiv`/`srem` are undefined behaviour on divide-by-zero and on `INT_MIN / -1`, where the JVM throws `ArithmeticException` or wraps. `LlvmLoweringConfig.emitDivisionGuards` (default `false`) is the seam for emitting JVM-faithful guards.
- **`fptosi`** — undefined on overflow / NaN, where the JVM saturates or returns `0`. The future fix swaps in `llvm.fptosi.sat.*` behind the same conversion arm.
- **Shift masking** is always emitted (cheap), so shift counts do not diverge.

## Lowering multiple methods

```java
List<IRMethod> methods = new ArrayList<>();
for (MethodEntry m : classFile.getMethods()) {
    if (/* computational */) {
        methods.add(ssa.lift(m));
    }
}
String module = new LlvmLowering().lowerToModule(methods);
```

All `define`s share one set of `declare`s, and intra-module callees are resolved against the defined symbols rather than redeclared.

## Demo

`com.tonic.demo.LlvmLoweringDemo` loads a class file, lifts each method, and prints the lowered LLVM IR (reporting the methods it skips as out of subset):

```bash
java -cp build/classes/java/main com.tonic.demo.LlvmLoweringDemo MyClass.class
```

## Extending past the subset

Adding object/heap/dispatch support is localized to the ~12 `UnsupportedLowering.reject(...)` sites in `SsaToLlvmLowerer` (the `visitFieldAccess`, `visitArrayAccess`, `visitNew`, `visitNewArray`, `visitTypeCheck`, and non-static `visitInvoke` arms). Each is the place where the corresponding runtime lowering would slot in once a memory/object model is chosen, without touching the surrounding arithmetic and control-flow lowering.

---

## Next Steps

- [LLVM Lifting](llvm-lifting.md) - lift LLVM IR back to SSA for the optimizer round-trip
- [SSA Guide](ssa-guide.md) - the IR this backend consumes
- [SSA Transforms](ssa-transforms.md) - optimizations to run before lowering
- [Architecture](architecture.md) - where the LLVM backend fits in the system

---

[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [SSA Transforms](ssa-transforms.md) | [LLVM Lifting ->](llvm-lifting.md) | [Migration Guide](SSA_IR_MIGRATION.md)
