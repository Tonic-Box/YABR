[<- Back to Analysis APIs](analysis-apis.md)

# Simulation API

The Simulation API (`com.tonic.analysis.simulation`) provides a comprehensive bytecode/IR simulation system that tracks execution state, counts operations, traces value flow, and analyzes control flow paths.

## Overview

The simulation system is designed around these core principles:

1. **Layered Architecture**: Supports both instruction-level and block-level simulation modes
2. **Configurable Depth**: Intra-procedural by default, with optional inter-procedural analysis
3. **Event-Driven**: Listener pattern for flexible instrumentation
4. **Immutable State**: Functional state transitions for safe history/merging
5. **Query-Oriented**: Rich query API built on simulation results

---

## Quick Start

```java
import com.tonic.analysis.simulation.core.*;
import com.tonic.analysis.simulation.listener.*;
import com.tonic.analysis.simulation.metrics.*;

// Build simulation context
SimulationContext ctx = SimulationContext.defaults()
    .withMode(SimulationMode.INSTRUCTION);

// Create listeners
StackOperationListener stackListener = new StackOperationListener();
AllocationListener allocListener = new AllocationListener();

// Run simulation
SimulationEngine engine = new SimulationEngine(ctx)
    .addListener(stackListener)
    .addListener(allocListener);

SimulationResult result = engine.simulate(irMethod);

// Get metrics
StackMetrics stackMetrics = StackMetrics.from(stackListener);
System.out.println("Push count: " + stackMetrics.getPushCount());
System.out.println("Max depth: " + stackMetrics.getMaxDepth());
```

---

## Core Classes

### SimulationContext

Configuration for the simulation engine:

```java
SimulationContext ctx = SimulationContext.defaults()
    .withMode(SimulationMode.INSTRUCTION)   // INSTRUCTION or BLOCK
    .withMaxCallDepth(3)                     // 0 = intra-procedural only
    .withHeapTracking(true)                  // Track allocations
    .withValueTracking(true)                 // Track value flow
    .withStackOperationTracking(true);       // Track stack ops (default)
```

| Method | Description |
|--------|-------------|
| `defaults()` | Create default context |
| `withMode(mode)` | Set simulation granularity |
| `withMaxCallDepth(n)` | Set inter-procedural depth (0 = intra only) |
| `withHeapTracking(bool)` | Enable/disable heap tracking |
| `withValueTracking(bool)` | Enable/disable value tracking |
| `isInterProcedural()` | True if maxCallDepth > 0 |

### SimulationState

Immutable snapshot of execution state at a point during simulation:

```java
SimulationState state = SimulationState.empty();

// Stack operations (return new state)
state = state.push(SimValue.constant(42, PrimitiveType.INT, null));
state = state.pushWide(SimValue.ofType(PrimitiveType.LONG, null));
SimValue top = state.peek();       // Returns top (may be wide second slot)
SimValue val = state.peekValue();  // Returns actual value (skips wide slot)
state = state.pop();

// Local variable operations
state = state.setLocal(0, value);
state = state.setLocalWide(0, wideValue);  // Takes 2 slots
SimValue local = state.getLocal(0);

// Position tracking
state = state.atBlock(block);
state = state.atInstruction(5);
state = state.nextInstruction();

// Call depth
state = state.enterCall();  // For inter-procedural simulation
```

| Method | Description |
|--------|-------------|
| `push(value)` | Push value onto stack |
| `pushWide(value)` | Push wide value (long/double) |
| `pop()` / `pop(n)` | Pop value(s) from stack |
| `popWide()` | Pop wide value (2 slots) |
| `peek()` | View top of stack |
| `peekValue()` | View top value (handles wide types) |
| `dup()` / `swap()` | Stack manipulation |
| `setLocal(idx, val)` | Set local variable |
| `getLocal(idx)` | Get local variable |
| `merge(other)` | Merge states at control flow join |
| `snapshot()` | Create lightweight snapshot |

### SimValue

Represents a simulated value:

```java
// Factory methods
SimValue.constant(42, PrimitiveType.INT, sourceInstr);    // Known constant
SimValue.ofType(PrimitiveType.LONG, sourceInstr);         // Type only
SimValue.unknown(sourceInstr);                             // Unknown value
SimValue.fromSSA(ssaValue, sourceInstr);                   // From SSA

// Queries
boolean isConst = value.isConstant();
boolean isWide = value.isWide();          // long or double
boolean isRef = value.isReference();      // Object type
boolean isUnknown = value.isUnknown();    // No type info
IRType type = value.getType();
Object constVal = value.getConstantValue();
```

### StackState and LocalState

Immutable representations of execution context:

```java
// Stack operations
StackState stack = StackState.empty();
stack = stack.push(value);
stack = stack.pushWide(wideValue);    // Adds value + second slot marker
SimValue top = stack.peekValue();      // Skips wide second slots
int depth = stack.depth();
int maxSeen = stack.maxDepth();

// Local variable operations
LocalState locals = LocalState.empty();
locals = locals.set(0, value);
locals = locals.setWide(0, wideValue);  // Occupies slots 0 and 1
SimValue local = locals.get(0);
boolean defined = locals.isDefined(0);
```

---

## Simulation Engine

The `SimulationEngine` drives simulation with configured listeners:

```java
SimulationEngine engine = new SimulationEngine(ctx);

// Add listeners
engine.addListener(new StackOperationListener());
engine.addListener(new AllocationListener());

// Run simulation
SimulationResult result = engine.simulate(irMethod);

// Incremental simulation
SimulationState state = SimulationState.empty();
for (IRInstruction instr : block.getInstructions()) {
    state = engine.step(state, instr);
}
```

| Method | Description |
|--------|-------------|
| `addListener(listener)` | Add simulation listener |
| `simulate(irMethod)` | Run full method simulation |
| `step(state, instr)` | Execute single instruction |
| `getContext()` | Get simulation context |

---

## Listeners

### SimulationListener Interface

Event hooks for custom instrumentation:

```java
public interface SimulationListener {
    // Lifecycle
    default void onSimulationStart(IRMethod method) {}
    default void onSimulationEnd(IRMethod method, SimulationResult result) {}

    // Block events
    default void onBlockEntry(IRBlock block, SimulationState state) {}
    default void onBlockExit(IRBlock block, SimulationState state) {}

    // Instruction events
    default void onBeforeInstruction(IRInstruction instr, SimulationState state) {}
    default void onAfterInstruction(IRInstruction instr, SimulationState before, SimulationState after) {}

    // Stack operations
    default void onStackPush(SimValue value, IRInstruction source) {}
    default void onStackPop(SimValue value, IRInstruction consumer) {}

    // Memory operations
    default void onAllocation(IRInstruction instr, SimulationState state) {}
    default void onFieldRead(IRInstruction instr, SimulationState state) {}
    default void onFieldWrite(IRInstruction instr, SimulationState state) {}

    // Control flow
    default void onBranch(IRInstruction instr, SimulationState state) {}
    default void onMethodCall(IRInstruction instr, SimulationState state) {}
    default void onMethodReturn(IRInstruction instr, SimulationState state) {}
}
```

### Built-in Listeners

#### StackOperationListener

Tracks stack operations:

```java
StackOperationListener listener = new StackOperationListener();
// or with history tracking:
StackOperationListener listener = new StackOperationListener(true);

listener.onSimulationStart(null);

// After simulation
int pushCount = listener.getPushCount();
int popCount = listener.getPopCount();
int maxDepth = listener.getMaxDepth();
int currentDepth = listener.getCurrentDepth();
int totalOps = listener.getTotalOperations();

// If history tracking enabled
List<Integer> depthHistory = listener.getDepthHistory();
```

#### AllocationListener

Tracks object allocations:

```java
AllocationListener listener = new AllocationListener();
listener.onSimulationStart(null);

// After simulation
int objectCount = listener.getObjectCount();
int arrayCount = listener.getArrayCount();
int totalCount = listener.getTotalCount();
Set<String> types = listener.getAllocatedTypes();
int countForType = listener.getAllocationCount("java/util/ArrayList");
```

#### FieldAccessListener

Tracks field read/write operations:

```java
FieldAccessListener listener = new FieldAccessListener();
listener.onSimulationStart(null);

// After simulation
int totalReads = listener.getTotalReads();
int totalWrites = listener.getTotalWrites();
int instanceReads = listener.getInstanceReads();
int staticReads = listener.getStaticReads();
Set<String> readFields = listener.getReadFields();    // "owner.fieldName"
Set<String> writtenFields = listener.getWrittenFields();
```

#### MethodCallListener

Tracks method invocations:

```java
MethodCallListener listener = new MethodCallListener();
listener.onSimulationStart(null);

// After simulation
int totalCalls = listener.getTotalCalls();
int virtualCalls = listener.getVirtualCalls();
int staticCalls = listener.getStaticCalls();
int interfaceCalls = listener.getInterfaceCalls();
int specialCalls = listener.getSpecialCalls();
Set<String> calledMethods = listener.getCalledMethods();  // "owner.name:desc"
int callCount = listener.getCallCount("java/lang/String.equals:(Ljava/lang/Object;)Z");
```

#### ControlFlowListener

Tracks control flow events:

```java
ControlFlowListener listener = new ControlFlowListener();
listener.onSimulationStart(null);

// After simulation
int blocksVisited = listener.getBlocksVisited();
int branchCount = listener.getBranchCount();
int switchCount = listener.getSwitchCount();
int returnCount = listener.getReturnCount();
int throwCount = listener.getThrowCount();
Set<Integer> visitedBlockIds = listener.getVisitedBlockIds();
```

#### CompositeListener

Combines multiple listeners:

```java
CompositeListener composite = new CompositeListener(
    new StackOperationListener(),
    new AllocationListener(),
    new MethodCallListener()
);

engine.addListener(composite);
```

---

## Metrics Classes

### StackMetrics

```java
StackMetrics metrics = StackMetrics.from(stackListener);
// or combine multiple
StackMetrics combined = metrics1.combine(metrics2);

int pushes = metrics.getPushCount();
int pops = metrics.getPopCount();
int maxDepth = metrics.getMaxDepth();
int totalOps = metrics.getTotalOperations();
int netChange = metrics.getNetChange();     // pushes - pops
boolean grows = metrics.hasStackGrowth();   // netChange > 0
```

### AllocationMetrics

```java
AllocationMetrics metrics = AllocationMetrics.from(allocListener);

int objects = metrics.getObjectCount();
int arrays = metrics.getArrayCount();
int total = metrics.getTotalCount();
int distinctTypes = metrics.getDistinctTypeCount();
boolean hasAllocs = metrics.hasAllocations();
```

### AccessMetrics

```java
AccessMetrics metrics = AccessMetrics.from(fieldListener);

int fieldReads = metrics.getFieldReads();
int fieldWrites = metrics.getFieldWrites();
int arrayReads = metrics.getArrayReads();
int arrayWrites = metrics.getArrayWrites();
int totalAccesses = metrics.getTotalAccesses();
int totalFieldAccesses = metrics.getTotalFieldAccesses();
int totalArrayAccesses = metrics.getTotalArrayAccesses();
```

### CallMetrics

```java
CallMetrics metrics = CallMetrics.from(callListener);

int total = metrics.getTotalCalls();
int virtual = metrics.getVirtualCalls();
int static_ = metrics.getStaticCalls();
int interface_ = metrics.getInterfaceCalls();
int special = metrics.getSpecialCalls();
int distinctMethods = metrics.getDistinctMethods();
int polymorphic = metrics.getPolymorphicCalls();
boolean hasCalls = metrics.hasCalls();
```

### PathMetrics

```java
PathMetrics metrics = PathMetrics.from(cfListener);

int blocksVisited = metrics.getBlocksVisited();
int branches = metrics.getBranchCount();
int switches = metrics.getSwitchCount();
int returns = metrics.getReturnCount();
int throws_ = metrics.getThrowCount();
int totalEntries = metrics.getTotalBlockEntries();
int complexity = metrics.getComplexityIndicator();  // branches + switches + 1
boolean hasLoops = metrics.hasLoops();
```

---

## Query API

### ValueFlowQuery

Trace value origins and uses:

```java
ValueFlowQuery query = new ValueFlowQuery(result);

// Get all values that reach a specific value
Set<SimValue> sources = query.getSourcesOf(targetValue);

// Get all values that a specific value flows to
Set<SimValue> targets = query.getTargetsOf(sourceValue);

// Check if values are connected
boolean flows = query.flowsTo(source, target);

// Get the definition of a value
SimValue def = query.getDefinition(value);
IRInstruction defInstr = query.getDefiningInstruction(value);

// Get all uses of a value
List<IRInstruction> uses = query.getUses(value);
```

### PathQuery

Control flow path analysis:

```java
PathQuery query = new PathQuery(irMethod);

// Reachability
boolean canReach = query.canReach(fromBlock, toBlock);

// Path enumeration
List<List<IRBlock>> allPaths = query.getAllPaths(fromBlock, toBlock);
List<IRBlock> shortestPath = query.getShortestPath(fromBlock, toBlock);

// Path properties (with simulation results)
int maxDepthOnPath = query.getMaxStackDepthOnPath(path, result);
```

---

## Inter-procedural Simulation

For cross-method analysis, use `InterProceduralEngine`:

```java
SimulationContext ctx = SimulationContext.defaults()
    .withMaxCallDepth(3);  // Follow calls up to 3 levels

InterProceduralEngine engine = new InterProceduralEngine(ctx, classPool);
engine.addListener(new MethodCallListener());

SimulationResult result = engine.simulate(entryMethod);

// Transitive call metrics
CallMetrics metrics = result.getCallMetrics();
```

---

## Complete Example

```java
import com.tonic.analysis.simulation.core.*;
import com.tonic.analysis.simulation.listener.*;
import com.tonic.analysis.simulation.metrics.*;

public class SimulationExample {
    public static void analyzeMethod(IRMethod method) {
        // Configure simulation
        SimulationContext ctx = SimulationContext.defaults()
            .withMode(SimulationMode.INSTRUCTION)
            .withValueTracking(true);

        // Create listeners
        StackOperationListener stackListener = new StackOperationListener(true);
        AllocationListener allocListener = new AllocationListener();
        FieldAccessListener fieldListener = new FieldAccessListener();
        MethodCallListener callListener = new MethodCallListener();
        ControlFlowListener cfListener = new ControlFlowListener();

        // Run simulation
        SimulationEngine engine = new SimulationEngine(ctx)
            .addListener(stackListener)
            .addListener(allocListener)
            .addListener(fieldListener)
            .addListener(callListener)
            .addListener(cfListener);

        SimulationResult result = engine.simulate(method);

        // Collect metrics
        StackMetrics stackMetrics = StackMetrics.from(stackListener);
        AllocationMetrics allocMetrics = AllocationMetrics.from(allocListener);
        AccessMetrics accessMetrics = AccessMetrics.from(fieldListener);
        CallMetrics callMetrics = CallMetrics.from(callListener);
        PathMetrics pathMetrics = PathMetrics.from(cfListener);

        // Report
        System.out.println("=== Simulation Results ===");
        System.out.println("Stack: " + stackMetrics.getPushCount() + " pushes, " +
            stackMetrics.getPopCount() + " pops, max depth " + stackMetrics.getMaxDepth());
        System.out.println("Allocations: " + allocMetrics.getTotalCount() + " total, " +
            allocMetrics.getDistinctTypeCount() + " distinct types");
        System.out.println("Field accesses: " + accessMetrics.getFieldReads() + " reads, " +
            accessMetrics.getFieldWrites() + " writes");
        System.out.println("Method calls: " + callMetrics.getTotalCalls() + " total, " +
            callMetrics.getDistinctMethods() + " distinct methods");
        System.out.println("Control flow: " + pathMetrics.getBlocksVisited() + " blocks, " +
            pathMetrics.getBranchCount() + " branches");
    }
}
```

---

## Package Structure

```
com.tonic.analysis.simulation/
├── core/
│   ├── SimulationContext.java      - Configuration
│   ├── SimulationState.java        - Immutable execution state
│   ├── SimulationEngine.java       - Main simulation driver
│   ├── SimulationResult.java       - Results container
│   ├── SimulationMode.java         - INSTRUCTION or BLOCK
│   ├── StateSnapshot.java          - Lightweight snapshot
│   └── InterProceduralEngine.java  - Cross-method simulation
├── state/
│   ├── SimValue.java               - Simulated value
│   ├── StackState.java             - Operand stack
│   ├── LocalState.java             - Local variables
│   └── CallStackState.java         - Inter-procedural call stack
├── listener/
│   ├── SimulationListener.java     - Event interface
│   ├── AbstractListener.java       - Base implementation
│   ├── CompositeListener.java      - Multi-listener adapter
│   ├── StackOperationListener.java - Stack tracking
│   ├── AllocationListener.java     - Allocation tracking
│   ├── FieldAccessListener.java    - Field access tracking
│   ├── MethodCallListener.java     - Call tracking
│   └── ControlFlowListener.java    - Control flow tracking
├── metrics/
│   ├── StackMetrics.java           - Stack statistics
│   ├── AllocationMetrics.java      - Allocation statistics
│   ├── AccessMetrics.java          - Field/array statistics
│   ├── CallMetrics.java            - Call statistics
│   └── PathMetrics.java            - Control flow statistics
├── query/
│   ├── ValueFlowQuery.java         - Value flow analysis
│   └── PathQuery.java              - Path analysis
└── util/
    └── StateTransitions.java       - Instruction effects
```

---

## Key Classes Summary

| Class | Description |
|-------|-------------|
| `SimulationContext` | Configuration for simulation |
| `SimulationState` | Immutable execution state snapshot |
| `SimulationEngine` | Drives simulation with listeners |
| `SimulationResult` | Collected simulation data |
| `SimValue` | Represents a simulated value |
| `StackState` | Immutable operand stack |
| `LocalState` | Immutable local variables |
| `SimulationListener` | Event hooks interface |
| `StackOperationListener` | Tracks push/pop operations |
| `AllocationListener` | Tracks object allocations |
| `FieldAccessListener` | Tracks field read/write |
| `MethodCallListener` | Tracks method calls |
| `ControlFlowListener` | Tracks branches/blocks |
| `StackMetrics` | Stack operation statistics |
| `AllocationMetrics` | Allocation statistics |
| `AccessMetrics` | Field/array access statistics |
| `CallMetrics` | Method call statistics |
| `PathMetrics` | Control flow statistics |
| `ValueFlowQuery` | Value origin/use queries |
| `PathQuery` | Control flow path queries |
| `InterProceduralEngine` | Cross-method simulation |

---

[<- Back to Analysis APIs](analysis-apis.md)
