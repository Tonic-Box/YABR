[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [Frame Computation ->](frame-computation.md)

# SSA Transforms

YABR provides optimization transforms and analysis passes for SSA-form IR.

## Optimization Transforms

### Constant Folding

Evaluates constant expressions at compile time.

```java
// Before
v1 = const 2
v2 = const 3
v3 = ADD v1, v2

// After
v3 = const 5
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withConstantFolding();
ssa.transform(method);
```

**Supported operations:**
- Arithmetic: ADD, SUB, MUL, DIV, REM
- Bitwise: AND, OR, XOR, SHL, SHR, USHR
- Unary: NEG
- Comparisons for branch simplification

### Copy Propagation

Replaces uses of copied values with the original.

```java
// Before
v1 = load local[0]
v2 = v1           // copy
v3 = ADD v2, v2

// After
v1 = load local[0]
v3 = ADD v1, v1
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withCopyPropagation();
ssa.transform(method);
```

### Dead Code Elimination

Removes instructions whose results are never used.

```java
// Before
v1 = const 10
v2 = const 20     // unused
v3 = ADD v1, v1
return v3

// After
v1 = const 10
v3 = ADD v1, v1
return v3
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withDeadCodeElimination();
ssa.transform(method);
```

### Strength Reduction

Replaces expensive operations with cheaper equivalents.

```java
// Before
v1 = MUL x, 8      // Multiplication by power of 2
v2 = DIV y, 4      // Division by power of 2
v3 = REM z, 16     // Remainder by power of 2

// After
v1 = SHL x, 3      // x << 3
v2 = SHR y, 2      // y >> 2
v3 = AND z, 15     // z & 15
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withStrengthReduction();
ssa.transform(method);
```

**Supported patterns:**
- `x * 2^n` → `x << n` (multiplication by power of 2)
- `x / 2^n` → `x >> n` (division by power of 2, positive values)
- `x % 2^n` → `x & (2^n - 1)` (remainder by power of 2)

### Algebraic Simplification

Applies algebraic identities to simplify expressions.

```java
// Before
v1 = ADD x, 0      // Identity
v2 = MUL y, 1      // Identity
v3 = SUB z, z      // Self-subtraction
v4 = XOR w, w      // Self-XOR
v5 = AND a, 0      // Zero annihilator

// After
v1 = x             // copy
v2 = y             // copy
v3 = const 0       // always 0
v4 = const 0       // always 0
v5 = const 0       // always 0
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withAlgebraicSimplification();
ssa.transform(method);
```

**Supported patterns:**
- **Additive identity:** `x + 0` → `x`, `0 + x` → `x`, `x - 0` → `x`
- **Multiplicative identity:** `x * 1` → `x`, `1 * x` → `x`
- **Zero annihilator:** `x * 0` → `0`, `0 * x` → `0`, `x & 0` → `0`
- **Self-operations:** `x - x` → `0`, `x ^ x` → `0`
- **Bitwise identity:** `x | 0` → `x`, `x & -1` → `x`, `x ^ 0` → `x`
- **Shift identity:** `x << 0` → `x`, `x >> 0` → `x`, `x >>> 0` → `x`

### Phi Constant Propagation

Simplifies phi nodes when all incoming values are identical.

```java
// Before
B1:
    v1 = const 42
    goto B3
B2:
    v2 = const 42
    goto B3
B3:
    v3 = phi(B1:v1, B2:v2)   // Both incoming are 42
    return v3

// After
B3:
    v3 = const 42            // Phi replaced with constant
    return v3
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withPhiConstantPropagation();
ssa.transform(method);
```

**Supported patterns:**
- All incoming values are the same constant → replace with constant
- All incoming values are the same SSA value → replace with copy

### Peephole Optimizations

Small pattern-based optimizations on instruction sequences.

```java
// Before
v1 = NEG x
v2 = NEG v1        // Double negation

v3 = SHL y, 32     // Shift by type width (int)
v4 = SHL z, 0      // Shift by 0

// After
v2 = x             // Double negation eliminated

v3 = y             // Shift by 32 is identity for int
v4 = z             // Shift by 0 eliminated
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withPeepholeOptimizations();
ssa.transform(method);
```

**Supported patterns:**
- **Double negation:** `NEG(NEG(x))` → `x`
- **Shift normalization:** `x << 32` → `x` (for int), `x << 64` → `x` (for long)
- **Zero shift:** `x << 0` → `x`, `x >> 0` → `x`
- **Consecutive shifts:** `(x << a) << b` → `x << (a+b)` when safe

### Common Subexpression Elimination

Identifies identical expressions and reuses the first computed result.

```java
// Before
v1 = ADD x, y
v2 = MUL a, b
v3 = ADD x, y      // Same as v1
v4 = MUL a, b      // Same as v2

// After
v1 = ADD x, y
v2 = MUL a, b
v3 = v1            // Reuse v1
v4 = v2            // Reuse v2
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withCommonSubexpressionElimination();
ssa.transform(method);
```

**Features:**
- Expression hashing for efficient lookup
- Handles commutative operations (a+b equals b+a)
- Works across basic blocks within dominance region

### Null Check Elimination

Removes redundant null checks when objects are provably non-null.

```java
// Before
v1 = new Object()
if (v1 == null) goto error    // Redundant - v1 is non-null

// After
v1 = new Object()
// Null check eliminated
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withNullCheckElimination();
ssa.transform(method);
```

**Provably non-null sources:**
- After `new` instruction
- After successful null check in dominating block
- `this` reference in instance methods

### Conditional Constant Propagation

Evaluates constant branch conditions and eliminates unreachable code.

```java
// Before
v1 = const true
if (v1) goto B1 else goto B2    // Constant condition
B1:
    return 1
B2:
    return 2                     // Unreachable

// After
goto B1                          // Unconditional jump
B1:
    return 1
// B2 eliminated
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withConditionalConstantPropagation();
ssa.transform(method);
```

**Supported patterns:**
- Boolean constant conditions → eliminate dead branch
- Integer comparison with constants → evaluate at compile time
- Cascading elimination of unreachable blocks

### Loop-Invariant Code Motion

Moves computations that produce the same result in every loop iteration to the loop preheader.

```java
// Before
for (int i = 0; i < n; i++) {
    int k = a * b;     // a and b don't change in loop
    sum += k;
}

// After
int k = a * b;         // Hoisted to preheader
for (int i = 0; i < n; i++) {
    sum += k;
}
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withLoopInvariantCodeMotion();
ssa.transform(method);
```

**Requirements for hoisting:**
- All operands defined outside the loop or are constants
- Instruction has no side effects
- Instruction is not a phi, branch, or memory operation

### Induction Variable Simplification

Identifies and simplifies induction variables in loops.

```java
// Before
for (int i = 0; i < n; i++) {
    sum += i * 4;      // Derived induction variable
}

// After
int stride = 0;
for (int i = 0; i < n; i++) {
    sum += stride;     // Use derived variable directly
    stride += 4;       // Increment by stride
}
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withInductionVariableSimplification();
ssa.transform(method);
```

**Detected patterns:**
- **Basic induction variable:** `i = i + c` where c is constant
- **Derived induction variable:** `j = i * c + d` linear function of basic IV

### Combining Transforms

Apply multiple transforms for best results:

```java
SSA ssa = new SSA(constPool)
    .withConstantFolding()
    .withCopyPropagation()
    .withDeadCodeElimination();

// Standard set (basic optimizations)
SSA ssa = new SSA(constPool).withStandardOptimizations();

// All optimizations including loop transforms
SSA ssa = new SSA(constPool).withAllOptimizations();
```

**Recommended transform order:**

1. ConstantFolding - Evaluate constant expressions first
2. PhiConstantPropagation - Simplify redundant phi nodes
3. ConditionalConstantPropagation - Eliminate dead branches
4. AlgebraicSimplification - Apply algebraic identities
5. PeepholeOptimizations - Small pattern optimizations
6. StrengthReduction - Replace expensive operations
7. CommonSubexpressionElimination - Reuse computed values
8. CopyPropagation - Eliminate redundant copies
9. NullCheckElimination - Remove redundant null checks
10. LoopInvariantCodeMotion - Hoist loop-invariant code
11. InductionVariableSimplification - Optimize loop counters
12. JumpThreading - Thread jump chains
13. BlockMerging - Merge single-edge blocks
14. DeadCodeElimination - Clean up unused instructions (run last)

Transforms run iteratively until a fixed point is reached (no more changes) or a maximum iteration count (10).

### Jump Threading

Eliminates redundant jump chains by threading through empty goto blocks.

```java
// Before
B1: goto A
A:  goto B    // Empty goto block
B:  ...

// After
B1: goto B    // Direct jump to ultimate target
B:  ...
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withJumpThreading();
ssa.transform(method);
```

**Supported patterns:**
- `goto A; A: goto B` → `goto B`
- `if (cond) goto A; A: goto B` → `if (cond) goto B`
- Empty blocks (only a goto) in switch targets

### Block Merging

Merges blocks with a single predecessor/successor relationship.

```java
// Before
A: x = 1
   goto B
B: y = 2      // B has only A as predecessor
   return

// After
A: x = 1
   y = 2      // B's code merged into A
   return
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withBlockMerging();
ssa.transform(method);
```

**Merge conditions:**
- Block A has exactly one successor (B)
- Block B has exactly one predecessor (A)
- Block B has no phi instructions
- Block A ends with unconditional goto to B

## Class-Level Transforms

These transforms operate on entire classes rather than individual methods.

### Method Inlining

Replaces method calls with the body of the called method, eliminating call overhead and enabling further optimizations.

```java
// Before
public int compute(int x) {
    return helper(x) + 10;
}

private static int helper(int y) {
    return y * 2;
}

// After inlining helper into compute
public int compute(int x) {
    return (x * 2) + 10;  // helper body inlined
}
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withMethodInlining();
ssa.runClassTransforms(classFile);
```

**Inlining criteria:**
- **Private methods** - No virtual dispatch concerns
- **Final methods** - Cannot be overridden
- **Static methods** - No receiver type issues
- **Small methods** - Max 35 bytecodes (configurable)
- **No exception handlers** - Simplifies inlining (MVP limitation)
- **Same class only** - Intra-class inlining for safety

**Benefits:**
- Eliminates method call overhead (invoke, stack frame setup)
- Enables cross-method optimizations (constant folding, CSE)
- Reduces code indirection

**Limitations (MVP):**
- Only inlines within the same class
- Skips methods with exception handlers
- Skips synchronized and native methods
- Maximum inline depth of 5 levels

### Dead Method Elimination

Removes private methods that are never called after inlining.

```java
// Before (after inlining helper into compute)
public int compute(int x) {
    return (x * 2) + 10;
}

private static int helper(int y) {  // No longer called
    return y * 2;
}

// After dead method elimination
public int compute(int x) {
    return (x * 2) + 10;
}
// helper method removed
```

**Usage:**

```java
SSA ssa = new SSA(constPool).withDeadMethodElimination();
ssa.runClassTransforms(classFile);
```

**Elimination criteria:**
- **Private methods only** - Cannot be called from outside the class
- **Not called by any method** in the class
- **Not a constructor** or static initializer

**Best when combined with Method Inlining:**

```java
SSA ssa = new SSA(constPool)
    .withMethodInlining()
    .withDeadMethodElimination();  // Run after inlining

ssa.runClassTransforms(classFile);
```

### Running Class-Level Transforms

Class-level transforms use a different API than method-level transforms:

```java
// Create SSA with class-level transforms
SSA ssa = new SSA(constPool)
    .withMethodInlining()
    .withDeadMethodElimination();

// Run class-level transforms (inlining, dead method elimination)
boolean classModified = ssa.runClassTransforms(classFile);

// Optionally run method-level transforms after
ssa.withAllOptimizations();
for (MethodEntry method : classFile.getMethods()) {
    if (method.getCodeAttribute() == null) continue;
    ssa.transform(method);
}

// Rebuild class file
classFile.computeFrames();
classFile.rebuild();
```

### Writing Custom Class Transforms

Implement the `ClassTransform` interface:

```java
import com.tonic.analysis.ssa.transform.ClassTransform;
import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.ClassFile;

public class MyClassTransform implements ClassTransform {

    @Override
    public String getName() {
        return "MyClassTransform";
    }

    @Override
    public boolean run(ClassFile classFile, SSA ssa) {
        boolean changed = false;

        for (MethodEntry method : classFile.getMethods()) {
            // Process each method with access to the full class
            if (processMethod(classFile, method, ssa)) {
                changed = true;
            }
        }

        return changed;
    }

    private boolean processMethod(ClassFile classFile, MethodEntry method, SSA ssa) {
        // Your transformation logic here
        return false;
    }
}
```

## Analysis Passes

### Dominator Tree

Computes dominance relationships between blocks. Block A dominates block B if every path from entry to B goes through A.

```java
SSA ssa = new SSA(constPool);
IRMethod ir = ssa.lift(method);

DominatorTree domTree = ssa.computeDominators(ir);

// Check dominance
IRBlock a = ir.getEntryBlock();
IRBlock b = ir.getBlocks().get("block_1");
boolean dominates = domTree.dominates(a, b);

// Get immediate dominator
IRBlock idom = domTree.getImmediateDominator(b);

// Get dominance frontier
Set<IRBlock> frontier = domTree.getDominanceFrontier(a);
```

**Used for:**
- Phi function insertion
- Loop detection
- Code motion optimization

### Liveness Analysis

Determines which values are live (may be used later) at each program point.

```java
LivenessAnalysis liveness = ssa.computeLiveness(ir);

// Check if value is live at block entry
boolean live = liveness.isLiveIn(block, value);

// Check if value is live at block exit
boolean liveOut = liveness.isLiveOut(block, value);

// Get all live values at block entry
Set<SSAValue> liveIn = liveness.getLiveIn(block);

// Get all live values at block exit
Set<SSAValue> liveOut = liveness.getLiveOut(block);
```

**Used for:**
- Dead code elimination
- Register allocation
- Interference graph construction

### Def-Use Chains

Tracks definitions and uses of each value.

```java
DefUseChains defUse = ssa.computeDefUse(ir);

// Get definition point of a value
IRInstruction def = defUse.getDefinition(value);

// Get all uses of a value
Set<IRInstruction> uses = defUse.getUses(value);

// Get values used by an instruction
Set<SSAValue> usedValues = defUse.getUsedValues(instruction);

// Get value defined by an instruction
SSAValue definedValue = defUse.getDefinedValue(instruction);
```

**Used for:**
- Copy propagation
- Constant propagation
- Strength reduction

### Loop Analysis

Detects and analyzes loops in the control flow graph.

```java
LoopAnalysis loops = ssa.computeLoops(ir);

// Get all loops
Set<Loop> allLoops = loops.getLoops();

// Check if a block is in any loop
boolean inLoop = loops.isInLoop(block);

// Get the loop containing a block
Loop loop = loops.getLoopContaining(block);

// Loop properties
IRBlock header = loop.getHeader();
Set<IRBlock> body = loop.getBlocks();
Set<IRBlock> exits = loop.getExitBlocks();
int depth = loop.getNestingDepth();
```

**Used for:**
- Loop-invariant code motion
- Strength reduction
- Loop unrolling

## Writing Custom Transforms

Implement the `IRTransform` interface:

```java
import com.tonic.analysis.ssa.transform.IRTransform;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.*;

public class MyTransform implements IRTransform {

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocksInOrder()) {
            for (IRInstruction instr : new ArrayList<>(block.getInstructions())) {
                if (shouldOptimize(instr)) {
                    optimize(block, instr);
                    changed = true;
                }
            }
        }

        return changed;  // Return true if any changes were made
    }

    private boolean shouldOptimize(IRInstruction instr) {
        // Check if instruction can be optimized
        return false;
    }

    private void optimize(IRBlock block, IRInstruction instr) {
        // Perform optimization
    }
}
```

Register your transform:

```java
SSA ssa = new SSA(constPool)
    .addTransform(new MyTransform())
    .withStandardOptimizations();
```

## Lowering Back to Bytecode

After optimization, lower the IR back to bytecode:

```java
SSA ssa = new SSA(constPool);
IRMethod ir = ssa.lift(method);

// Apply transforms
ssa.runTransforms(ir);

// Lower to bytecode
ssa.lower(ir, method);

// Don't forget to compute frames if needed
classFile.computeFrames(method);
```

The lowerer handles:
- Phi elimination (insert copies in predecessor blocks)
- Instruction selection (IR -> bytecode)
- Stack scheduling (register -> stack-based)
- Local variable allocation

## Complete Example

```java
public void optimizeClass(ClassFile classFile) {
    ConstPool cp = classFile.getConstPool();

    // Configure optimizations - use all available transforms
    SSA ssa = new SSA(cp).withAllOptimizations();

    // Process each method
    for (MethodEntry method : classFile.getMethods()) {
        if (method.getCodeAttribute() == null) continue;
        if (method.getName().startsWith("<")) continue;  // Skip init

        // Lift to SSA
        IRMethod ir = ssa.lift(method);

        // Analyze
        DominatorTree domTree = ssa.computeDominators(ir);
        LoopAnalysis loops = ssa.computeLoops(ir);
        DefUseChains defUse = ssa.computeDefUse(ir);

        // Log analysis info
        System.out.println("Method: " + method.getName());
        System.out.println("  Loops: " + loops.getLoops().size());
        System.out.println("  Definitions: " + defUse.getDefinitions().size());

        // Optimize (runs all transforms iteratively)
        ssa.runTransforms(ir);

        // Lower back to bytecode
        ssa.lower(ir, method);
    }

    // Compute frames for all methods
    classFile.computeFrames();
}
```

---

[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [Frame Computation ->](frame-computation.md)
