[<- Back to README](../README.md) | [Simulation API](simulation-api.md) | [Frame Computation ->](frame-computation.md)

# Verifier API

The verifier checks that a `ClassFile` (or a single method) is well-formed and would pass the JVM's
own bytecode verification. It is useful after generating or transforming bytecode - run it before
writing a class to disk to catch structural, type, control-flow, and StackMapTable problems with a
readable error report instead of a `VerifyError` at class-load time.

Verification is organized into four dimensions, each independently toggleable:

| Dimension | Checks |
|-----------|--------|
| **Structural** | opcode validity, operand ranges, constant-pool indices/types, branch targets, `wide` prefixes, code length, local indices |
| **Type** | operand-stack and local types, stack under/overflow vs `max_stack`, uninitialized access, return-type compatibility, join-point type merges |
| **Control flow** | every path returns/throws, reachability, exception-handler ranges, catch types, JSR/RET |
| **StackMapTable** | presence and correctness of frames at branch targets (types, stack, locals, offsets) |

## Quick Start

```java
import com.tonic.analysis.verifier.Verifier;
import com.tonic.analysis.verifier.VerificationResult;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ClassFile;

ClassPool pool = ClassPool.getDefault();
ClassFile cf = pool.loadClass(inputStream);

Verifier verifier = Verifier.builder()
    .classPool(pool)   // used for type-hierarchy checks (e.g. catch-type is a Throwable)
    .build();

VerificationResult result = verifier.verify(cf);
if (!result.isValid()) {
    System.out.println(result.formatReport());
}
```

## Entry Points

Build a `Verifier` with `Verifier.builder()`, then verify at class, method, or pool granularity:

| Call | Verifies |
|------|----------|
| `verify(ClassFile)` | every method in a class |
| `verify(MethodEntry)` | a single method |
| `verifyAll(ClassPool)` | every class in a pool (results merged) |

Each returns a `VerificationResult`.

```java
VerificationResult classResult  = verifier.verify(cf);
VerificationResult methodResult = verifier.verify(cf.getMethod("doWork", "(I)I"));
VerificationResult poolResult   = verifier.verifyAll(pool);
```

## Configuration

Configure via the `Verifier.Builder` shortcuts or a `VerifierConfig`. `VerifierConfig.defaults()`
enables all four dimensions in collect-all mode.

**Builder shortcuts:**

```java
Verifier verifier = Verifier.builder()
    .classPool(pool)
    .failFast()          // stop at the first error (vs collectAll())
    .build();
```

**Full config:**

```java
import com.tonic.analysis.verifier.VerifierConfig;

VerifierConfig config = VerifierConfig.builder()
    .verifyStructure(true)
    .strictTypeChecking(true)
    .verifyControlFlow(true)
    .verifyStackMapTable(true)
    .errorMode(VerifierConfig.ErrorMode.COLLECT_ALL)  // or FAIL_FAST
    .maxErrors(100)
    .treatWarningsAsErrors(false)
    .build();

Verifier verifier = Verifier.builder().classPool(pool).config(config).build();
```

| Option | Effect |
|--------|--------|
| `verifyStructure(boolean)` | run the structural dimension |
| `strictTypeChecking(boolean)` | run the type dimension |
| `verifyControlFlow(boolean)` | run the control-flow dimension |
| `verifyStackMapTable(boolean)` | run the StackMapTable dimension |
| `errorMode(ErrorMode)` | `FAIL_FAST` stops at the first error; `COLLECT_ALL` gathers them |
| `maxErrors(int)` | cap the number of collected errors |
| `treatWarningsAsErrors(boolean)` | promote warnings to errors (fails `isValid()`) |

`failFast()` / `collectAll()` on the builder are shortcuts for the corresponding `ErrorMode`.

## Reading the Result

`VerificationResult` separates errors from warnings and can format a human-readable report.

```java
if (result.isValid()) {
    System.out.println("OK");
} else {
    System.out.println(result.getErrorCount() + " error(s), "
            + result.getWarningCount() + " warning(s)");

    for (VerificationError err : result.getErrors()) {
        System.out.println(err.format());  // type, offset, message, location
    }
    System.out.println(result.formatReport());
}
```

| Method | Returns |
|--------|---------|
| `isValid()` | `true` when there are no errors (warnings alone still pass unless promoted) |
| `getErrors()` / `getWarnings()` | the error / warning lists |
| `getAllIssues()` | errors and warnings combined |
| `getErrorCount()` / `getWarningCount()` / `getTotalIssueCount()` | counts |
| `hasErrors()` / `hasWarnings()` | booleans |
| `getClassName()` / `getMethodName()` | where the result applies |
| `formatReport()` | a full formatted report string |
| `merge(other)` | combine two results (used by `verifyAll`) |

## Errors

Each `VerificationError` carries a `VerificationErrorType`, the offending `bytecodeOffset`, a
message, a `Severity` (`ERROR` or `WARNING`), and an optional class/method location.

```java
VerificationError err = result.getErrors().get(0);
err.getType();            // VerificationErrorType, e.g. TYPE_MISMATCH
err.getType().getDescription();  // "Operand type does not match instruction requirement"
err.getBytecodeOffset();  // where in the method
err.getSeverity();        // Severity.ERROR / WARNING
```

`VerificationErrorType` enumerates the specific failures, grouped by dimension - a sample:

- **Structural**: `INVALID_OPCODE`, `INVALID_OPERAND`, `INVALID_CONSTANT_POOL_INDEX`,
  `INVALID_BRANCH_TARGET`, `INSTRUCTION_FALLS_OFF_END`, `CODE_TOO_LONG`, `INVALID_LOCAL_INDEX`
- **Type**: `STACK_UNDERFLOW`, `STACK_OVERFLOW`, `TYPE_MISMATCH`, `UNINITIALIZED_ACCESS`,
  `INCOMPATIBLE_RETURN_TYPE`, `LOCALS_OVERFLOW`, `MERGE_CONFLICT`
- **Control flow**: `PATH_DOES_NOT_RETURN`, `UNREACHABLE_CODE`, `INVALID_EXCEPTION_HANDLER`,
  `EXCEPTION_HANDLER_OVERLAP`, `INVALID_CATCH_TYPE`, `JSR_MISMATCH`
- **StackMapTable**: `MISSING_STACKMAP_FRAME`, `FRAME_TYPE_MISMATCH`, `FRAME_STACK_MISMATCH`,
  `FRAME_LOCALS_MISMATCH`, `INVALID_FRAME_OFFSET`

## Under the Hood

The four dimensions are implemented by dedicated verifiers under
`com.tonic.analysis.verifier`:

- `structural.StructuralVerifier` - opcode/operand/constant-pool well-formedness
- `type.TypeVerifier` - abstract type checking over the operand stack and locals
- `controlflow.ControlFlowVerifier` and `controlflow.ExceptionTableVerifier` - reachability, returns, handler ranges
- `stackmap.StackMapVerifier` - StackMapTable frame agreement

The StackMapTable dimension pairs with [Frame Computation](frame-computation.md), which generates
the frames the verifier checks.

---

[<- Back to README](../README.md) | [Simulation API](simulation-api.md) | [Frame Computation ->](frame-computation.md)
