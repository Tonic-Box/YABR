[<- Back to README](../README.md) | [Visitors](visitors.md) | [SSA Transforms ->](ssa-transforms.md)

# SSA Guide

Static Single Assignment (SSA) form is an intermediate representation where each variable is assigned exactly once. YABR's SSA system enables powerful analysis and optimization of Java bytecode.

## What is SSA?

In SSA form:
- Each variable is defined exactly once
- Phi functions merge values at control flow join points
- Makes dataflow analysis and optimization simpler

**Before SSA (stack-based bytecode):**
```
iload 0
iconst 1
iadd
istore 0    // x = x + 1
iload 0
iconst 2
iadd
istore 0    // x = x + 2
```

**After SSA:**
```
v1 = load local[0]
v2 = const 1
v3 = ADD v1, v2
v4 = const 2
v5 = ADD v3, v4
store local[0] = v5
```

## SSA Pipeline

```
Bytecode --[BytecodeLifter]--> SSA IR --[Transforms]--> Optimized IR --[BytecodeLowerer]--> Bytecode
```

The pipeline has three stages:
1. **Lift** - Convert stack-based bytecode to register-based SSA IR
2. **Transform** - Apply optimizations
3. **Lower** - Convert SSA IR back to bytecode

## Basic Usage

### The SSA Class

```java
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

ConstPool cp = classFile.getConstPool();
SSA ssa = new SSA(cp);

// Just lift (no transforms)
IRMethod irMethod = ssa.lift(methodEntry);

// Lift and optimize
IRMethod optimized = ssa.liftAndOptimize(methodEntry);

// Complete transform: lift -> optimize -> lower
ssa.transform(methodEntry);
```

### Configuring Optimizations

```java
SSA ssa = new SSA(cp)
    .withConstantFolding()
    .withCopyPropagation()
    .withDeadCodeElimination();

// Or use the standard set
SSA ssa = new SSA(cp).withStandardOptimizations();
```

### Manual Pipeline Control

```java
// 1. Lift to SSA
IRMethod irMethod = ssa.lift(methodEntry);

// 2. Run transforms manually
ssa.runTransforms(irMethod);

// 3. Lower back to bytecode
ssa.lower(irMethod, methodEntry);
```

## IR Structure

### IRMethod

Represents a method in SSA form:

```java
IRMethod ir = ssa.lift(methodEntry);

// Get blocks
IRBlock entry = ir.getEntryBlock();
List<IRBlock> blocks = ir.getBlocksInOrder();
int blockCount = ir.getBlocks().size();

// Method info
String name = ir.getName();
String descriptor = ir.getDescriptor();
```

### IRBlock

A basic block containing phi instructions and regular instructions:

```java
IRBlock block = ir.getEntryBlock();

// Block properties
String name = block.getName();  // "block_0"
List<IRBlock> preds = block.getPredecessors();
List<IRBlock> succs = block.getSuccessors();

// Instructions
List<PhiInstruction> phis = block.getPhiInstructions();
List<IRInstruction> instrs = block.getInstructions();

// Add instructions
block.addPhi(phiInstruction);
block.addInstruction(instruction);
```

### SSAValue

Values in SSA form:

```java
// Get result of an instruction
SSAValue result = instruction.getResult();

// Value properties
String name = result.getName();     // "v3"
SSAType type = result.getType();    // INT, LONG, OBJECT, etc.
```

## IR Instructions

YABR defines 29 IR instruction types:

### Constants and Loads

```java
// Constant value
ConstantInstruction ci;  // v1 = const 42

// Load from local variable
LoadLocalInstruction load;  // v2 = load local[0]

// Store to local variable
StoreLocalInstruction store;  // store local[1] = v3
```

### Arithmetic

```java
// Binary operations
BinaryOpInstruction bin;  // v3 = ADD v1, v2
// Ops: ADD, SUB, MUL, DIV, REM, AND, OR, XOR, SHL, SHR, USHR

// Unary operations
UnaryOpInstruction un;  // v3 = NEG v1
// Ops: NEG, I2L, I2F, I2D, L2I, etc.
```

### Control Flow

```java
// Conditional branch
BranchInstruction br;  // if v1 EQ v2 goto block_1 else block_2

// Unconditional jump
GotoInstruction gt;  // goto block_3

// Return
ReturnInstruction ret;  // return v5

// Switch
SwitchInstruction sw;  // switch v1 [3 cases]
```

### Field Access

```java
// Instance field
GetFieldInstruction get;  // v2 = getfield MyClass.field
PutFieldInstruction put;  // putfield MyClass.field = v3

// Static field
// (also represented by GetField/PutField with static flag)
```

### Method Invocation

```java
InvokeInstruction inv;  // v4 = invoke VIRTUAL MyClass.method(2 args)
// Types: VIRTUAL, STATIC, SPECIAL, INTERFACE, DYNAMIC
```

### Object Operations

```java
NewInstruction ni;           // v1 = new MyClass
NewArrayInstruction na;      // v2 = newarray int
ArrayLoadInstruction al;     // v3 = arrayload v1[v2]
ArrayStoreInstruction as;    // arraystore v1[v2] = v3
ArrayLengthInstruction len;  // v4 = arraylength v1
CastInstruction cast;        // v5 = checkcast v1 to String
InstanceOfInstruction iof;   // v6 = instanceof v1 String
```

### Synchronization

```java
MonitorEnterInstruction me;  // monitorenter v1
MonitorExitInstruction mx;   // monitorexit v1
```

### Exception Handling

```java
ThrowInstruction th;  // throw v1
```

### Phi Functions

```java
PhiInstruction phi;  // v3 = phi(block_0:v1, block_1:v2)

// Get incoming values
Map<IRBlock, SSAValue> incoming = phi.getIncomingValues();

// Add incoming value
phi.addIncoming(predecessorBlock, value);
```

## Using IRPrinter

Format IR for debugging:

```java
import com.tonic.analysis.ssa.IRPrinter;

// Format single instruction
System.out.println(IRPrinter.format(instruction));
// Output: v3 = ADD v1, v2

// Format block header
System.out.println(IRPrinter.formatBlockHeader(block));
// Output:
// Block: block_0
//   Predecessors: []
//   Successors: [block_1, block_2]

// Format entire method
System.out.println(IRPrinter.format(irMethod));
```

## Example: Analyzing a Method

```java
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.IRPrinter;
import com.tonic.analysis.ssa.cfg.*;
import com.tonic.analysis.ssa.ir.*;

public void analyzeMethod(MethodEntry method) {
    ConstPool cp = method.getClassFile().getConstPool();
    SSA ssa = new SSA(cp);

    IRMethod ir = ssa.lift(method);

    System.out.println("=== " + ir.getName() + ir.getDescriptor() + " ===");

    for (IRBlock block : ir.getBlocksInOrder()) {
        System.out.println("\n" + block.getName() + ":");
        System.out.println("  preds: " + block.getPredecessors().stream()
            .map(IRBlock::getName).toList());

        // Print phi instructions
        for (PhiInstruction phi : block.getPhiInstructions()) {
            System.out.println("  [PHI] " + IRPrinter.format(phi));
        }

        // Print regular instructions
        for (IRInstruction instr : block.getInstructions()) {
            System.out.println("  " + IRPrinter.format(instr));

            // Count instruction types
            if (instr instanceof InvokeInstruction) {
                System.out.println("    ^ method call");
            } else if (instr instanceof BranchInstruction) {
                System.out.println("    ^ conditional");
            }
        }
    }
}
```

## Example: Simple Optimization

```java
public void optimizeMethod(MethodEntry method) {
    ConstPool cp = method.getClassFile().getConstPool();

    // Configure optimizations
    SSA ssa = new SSA(cp)
        .withConstantFolding()      // 2 + 3 -> 5
        .withCopyPropagation()      // x = y; use(x) -> use(y)
        .withDeadCodeElimination(); // Remove unused code

    // Lift to SSA
    IRMethod ir = ssa.lift(method);
    System.out.println("Before optimization:\n" + IRPrinter.format(ir));

    // Run transforms
    ssa.runTransforms(ir);
    System.out.println("After optimization:\n" + IRPrinter.format(ir));

    // Lower back to bytecode
    ssa.lower(ir, method);
}
```

## Integration with Visitors

```java
import com.tonic.analysis.visitor.AbstractBlockVisitor;

public class IRAnalyzer extends AbstractBlockVisitor {

    private int invokeCount = 0;
    private int branchCount = 0;

    @Override
    public void visitInstruction(IRInstruction instruction) {
        if (instruction instanceof InvokeInstruction) {
            invokeCount++;
        } else if (instruction instanceof BranchInstruction) {
            branchCount++;
        }
    }

    public void printStats() {
        System.out.println("Method calls: " + invokeCount);
        System.out.println("Branches: " + branchCount);
    }
}

// Usage
IRAnalyzer analyzer = new IRAnalyzer();
analyzer.process(methodEntry);
analyzer.printStats();
```

---

[<- Back to README](../README.md) | [Visitors](visitors.md) | [SSA Transforms ->](ssa-transforms.md)
