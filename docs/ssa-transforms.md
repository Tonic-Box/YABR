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

### Combining Transforms

Apply multiple transforms for best results:

```java
SSA ssa = new SSA(constPool)
    .withConstantFolding()
    .withCopyPropagation()
    .withDeadCodeElimination();

// Or use the standard set
SSA ssa = new SSA(constPool).withStandardOptimizations();
```

Transforms run iteratively until a fixed point is reached (no more changes) or a maximum iteration count (10).

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

### Example: Strength Reduction

Replace expensive operations with cheaper ones:

```java
public class StrengthReduction implements IRTransform {

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocksInOrder()) {
            List<IRInstruction> instrs = new ArrayList<>(block.getInstructions());

            for (int i = 0; i < instrs.size(); i++) {
                IRInstruction instr = instrs.get(i);

                if (instr instanceof BinaryOpInstruction bin) {
                    IRInstruction replacement = tryReduce(bin);
                    if (replacement != null) {
                        block.getInstructions().set(i, replacement);
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    private IRInstruction tryReduce(BinaryOpInstruction bin) {
        // x * 2 -> x << 1
        if (bin.getOp() == BinaryOp.MUL) {
            if (isConstant(bin.getRight(), 2)) {
                return new BinaryOpInstruction(
                    bin.getResult(),
                    BinaryOp.SHL,
                    bin.getLeft(),
                    constantValue(1)
                );
            }
        }

        // x / 2 -> x >> 1 (for positive x)
        if (bin.getOp() == BinaryOp.DIV) {
            if (isConstant(bin.getRight(), 2)) {
                return new BinaryOpInstruction(
                    bin.getResult(),
                    BinaryOp.SHR,
                    bin.getLeft(),
                    constantValue(1)
                );
            }
        }

        return null;
    }

    private boolean isConstant(SSAValue value, int expected) {
        // Check if value is a constant with expected value
        return false;
    }

    private SSAValue constantValue(int value) {
        // Create a constant value
        return null;
    }
}
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

    // Configure optimizations
    SSA ssa = new SSA(cp)
        .withConstantFolding()
        .withCopyPropagation()
        .withDeadCodeElimination()
        .addTransform(new StrengthReduction());

    // Process each method
    for (MethodEntry method : classFile.getMethods()) {
        if (method.getCodeAttribute() == null) continue;
        if (method.getName().startsWith("<")) continue;  // Skip init

        // Lift
        IRMethod ir = ssa.lift(method);

        // Analyze
        DominatorTree domTree = ssa.computeDominators(ir);
        LoopAnalysis loops = ssa.computeLoops(ir);

        // Log loop info
        for (Loop loop : loops.getLoops()) {
            System.out.println("Found loop at " + loop.getHeader().getName());
        }

        // Optimize
        ssa.runTransforms(ir);

        // Lower
        ssa.lower(ir, method);
    }

    // Compute frames for all methods
    classFile.computeFrames();
}
```

---

[<- Back to README](../README.md) | [SSA Guide](ssa-guide.md) | [Frame Computation ->](frame-computation.md)
