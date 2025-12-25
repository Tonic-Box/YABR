[<- Back to Analysis APIs](analysis-apis.md)

# Execution API

The Execution API (`com.tonic.analysis.execution`) provides concrete bytecode execution for debugging, and dynamic analysis. Unlike the [Simulation API](simulation-api.md) which performs abstract interpretation over the IRs, this API executes actual bytecode with mutable state.

## Overview

| Aspect | Simulation API | Execution API |
|--------|----------------|---------------|
| **Purpose** | Static analysis, metrics | Dynamic execution, debugging |
| **Input** | SSA IR (`IRMethod`) | Raw bytecode (`MethodEntry`) |
| **State** | Immutable snapshots | Mutable runtime state |
| **Values** | Abstract (`SimValue`) | Concrete (`ConcreteValue`) |
| **Objects** | Not tracked | Fully simulated heap |
| **Use Cases** | Complexity metrics, flow analysis | Debugger, REPL, test harness |

---

## Quick Start

```java
import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.heap.*;
import com.tonic.analysis.execution.state.*;

// Build execution context
HeapManager heap = new SimpleHeapManager();
ClassResolver resolver = new ClassResolver(classPool);

BytecodeContext ctx = new BytecodeContext.Builder()
    .heapManager(heap)
    .classResolver(resolver)
    .maxCallDepth(100)
    .maxInstructions(10000)
    .build();

// Execute a method
BytecodeEngine engine = new BytecodeEngine(ctx);
BytecodeResult result = engine.execute(method, ConcreteValue.intValue(42));

if (result.isSuccess()) {
    ConcreteValue returnValue = result.getReturnValue();
    System.out.println("Result: " + returnValue.asInt());
}
```

---

## Core Classes

### BytecodeContext

Configuration for the execution engine:

```java
BytecodeContext ctx = new BytecodeContext.Builder()
    .heapManager(new SimpleHeapManager())
    .classResolver(new ClassResolver(pool))
    .mode(ExecutionMode.RECURSIVE)    // RECURSIVE or DELEGATED
    .maxCallDepth(100)                // Stack overflow protection
    .maxInstructions(100000)          // Infinite loop protection
    .trackStatistics(true)            // Collect execution stats
    .build();
```

| Method | Description |
|--------|-------------|
| `heapManager(mgr)` | Set heap manager for object allocation |
| `classResolver(res)` | Set class/method resolver |
| `mode(mode)` | Set invocation mode (RECURSIVE/DELEGATED) |
| `maxCallDepth(n)` | Maximum call stack depth |
| `maxInstructions(n)` | Maximum instructions before abort |
| `trackStatistics(bool)` | Enable execution statistics |

### BytecodeEngine

The main execution driver:

```java
BytecodeEngine engine = new BytecodeEngine(ctx);

// Full execution
BytecodeResult result = engine.execute(method, arg1, arg2);

// Step-by-step execution
engine.execute(method);
while (engine.step()) {
    StackFrame frame = engine.getCurrentFrame();
    System.out.println("PC: " + frame.getPC());
}

// Interrupt execution
engine.interrupt();
engine.reset();
```

| Method | Description |
|--------|-------------|
| `execute(method, args...)` | Execute method to completion |
| `step()` | Execute single instruction, returns false when done |
| `interrupt()` | Request execution stop |
| `reset()` | Clear state for new execution |
| `getCurrentFrame()` | Get current stack frame |
| `getCallStack()` | Get full call stack |
| `getInstructionCount()` | Get executed instruction count |
| `addListener(listener)` | Add execution listener |
| `ensureClassInitialized(className)` | Run `<clinit>` if not yet initialized |
| `isClassInitialized(className)` | Check if class has been initialized |
| `markClassInitialized(className)` | Mark class as initialized without running `<clinit>` |
| `resetClassInitialization()` | Clear all class initialization state |

### Class Initialization

The engine tracks which classes have been initialized and automatically runs `<clinit>` on first static field access:

```java
BytecodeEngine engine = new BytecodeEngine(ctx);

// Manual class initialization
engine.ensureClassInitialized("com/example/Config");

// Check initialization state
if (engine.isClassInitialized("com/example/Config")) {
    // Class has been initialized
}

// Mark as initialized without running <clinit>
engine.markClassInitialized("com/example/MockClass");

// Reset all initialization state
engine.resetClassInitialization();
```

**Auto-initialization**: When GETSTATIC or PUTSTATIC is executed, the engine automatically calls `ensureClassInitialized()` for the field's owner class. This matches JVM semantics where static field access triggers class initialization.

### BytecodeResult

Execution outcome:

```java
BytecodeResult result = engine.execute(method);

// Check status
switch (result.getStatus()) {
    case COMPLETED:
        ConcreteValue ret = result.getReturnValue();
        break;
    case EXCEPTION:
        ObjectInstance ex = result.getException();
        List<String> trace = result.getStackTrace();
        break;
    case INTERRUPTED:
        // Execution was interrupted
        break;
    case INSTRUCTION_LIMIT:
        // Hit instruction limit
        break;
    case DEPTH_LIMIT:
        // Stack overflow
        break;
}

// Statistics
long instructions = result.getInstructionsExecuted();
long timeNanos = result.getExecutionTimeNanos();
```

---

## Value System

### ConcreteValue

Tagged union representing JVM values:

```java
// Factory methods
ConcreteValue intVal = ConcreteValue.intValue(42);
ConcreteValue longVal = ConcreteValue.longValue(100L);
ConcreteValue floatVal = ConcreteValue.floatValue(3.14f);
ConcreteValue doubleVal = ConcreteValue.doubleValue(2.718);
ConcreteValue refVal = ConcreteValue.reference(objectInstance);
ConcreteValue nullVal = ConcreteValue.nullRef();

// Type queries
ValueTag tag = value.getTag();      // INT, LONG, FLOAT, DOUBLE, REFERENCE, NULL
boolean isWide = value.isWide();    // true for long/double
boolean isNull = value.isNull();
int category = value.getCategory(); // 1 or 2 (for wide types)

// Value extraction
int i = value.asInt();
long l = value.asLong();
float f = value.asFloat();
double d = value.asDouble();
ObjectInstance ref = value.asReference();
```

### ConcreteStack

Mutable operand stack:

```java
ConcreteStack stack = new ConcreteStack(maxSize);

// Push/pop
stack.push(value);
stack.pushInt(42);
stack.pushLong(100L);
stack.pushReference(obj);
stack.pushNull();

ConcreteValue top = stack.pop();
ConcreteValue peek = stack.peek();
ConcreteValue peekN = stack.peek(2);  // 2 slots down

// JVM stack operations
stack.dup();      // ..., v -> ..., v, v
stack.dupX1();    // ..., v2, v1 -> ..., v1, v2, v1
stack.dupX2();    // ..., v3, v2, v1 -> ..., v1, v3, v2, v1
stack.dup2();     // ..., v2, v1 -> ..., v2, v1, v2, v1 (or wide)
stack.swap();     // ..., v2, v1 -> ..., v1, v2

// Info
int depth = stack.depth();
boolean empty = stack.isEmpty();
```

### ConcreteLocals

Mutable local variable storage:

```java
ConcreteLocals locals = new ConcreteLocals(maxLocals);

// Set/get
locals.set(0, value);
ConcreteValue val = locals.get(0);
boolean defined = locals.isDefined(0);

// Wide values (long/double take 2 slots)
locals.set(0, ConcreteValue.longValue(100L));
// Slot 1 is now undefined (occupied by wide value)
```

---

## Heap System

### ObjectInstance

Simulated object with field storage:

```java
ObjectInstance obj = heapManager.newObject("java/util/ArrayList");

// Identity
int id = obj.getId();
String className = obj.getClassName();
int hashCode = obj.getIdentityHashCode();

// Field access
obj.setField("java/util/ArrayList", "size", "I", 10);
Object size = obj.getField("java/util/ArrayList", "size", "I");

// Type checking
boolean isInstance = obj.isInstanceOf("java/util/List");
```

### ArrayInstance

Simulated array:

```java
// Primitive arrays
ArrayInstance intArray = heapManager.newArray("I", 10);
intArray.setInt(0, 42);
int val = intArray.getInt(0);
int len = intArray.getLength();

// Reference arrays
ArrayInstance objArray = heapManager.newArray("Ljava/lang/String;", 5);
objArray.setReference(0, stringInstance);
ObjectInstance str = objArray.getReference(0);

// Multi-dimensional
ArrayInstance matrix = heapManager.newMultiArray("[[I", new int[]{3, 4});
```

### HeapManager

Object allocation, string handling, and static field storage:

```java
HeapManager heap = new SimpleHeapManager();

// Allocation
ObjectInstance obj = heap.newObject("java/lang/Object");
ArrayInstance arr = heap.newArray("I", 100);

// String interning and extraction
ObjectInstance str1 = heap.internString("hello");
ObjectInstance str2 = heap.internString("hello");
// str1 == str2 (same instance)

// Extract Java String from ObjectInstance (reverse of intern)
String extracted = heap.extractString(str1);
// extracted.equals("hello") == true

// Static field storage
heap.putStaticField("com/example/Config", "DEBUG", "Z", true);
Object value = heap.getStaticField("com/example/Config", "DEBUG", "Z");
boolean hasField = heap.hasStaticField("com/example/Config", "DEBUG", "Z");
heap.clearStaticFields();  // Reset all static fields

// Statistics
long count = heap.objectCount();
```

| Method | Description |
|--------|-------------|
| `newObject(className)` | Allocate new object instance |
| `newArray(type, length)` | Allocate primitive or reference array |
| `newMultiArray(type, dims)` | Allocate multi-dimensional array |
| `internString(value)` | Get/create interned String instance |
| `extractString(instance)` | Extract Java String from ObjectInstance |
| `putStaticField(owner, name, desc, value)` | Store static field value |
| `getStaticField(owner, name, desc)` | Retrieve static field value |
| `hasStaticField(owner, name, desc)` | Check if static field is set |
| `clearStaticFields()` | Clear all static field storage |
| `objectCount()` | Get total allocated object count |

---

## Method Invocation

### ExecutionMode

Two invocation strategies:

```java
// RECURSIVE: Engine handles method calls internally
BytecodeContext recursive = new BytecodeContext.Builder()
    .mode(ExecutionMode.RECURSIVE)
    .build();

// DELEGATED: External callback handles method calls
BytecodeContext delegated = new BytecodeContext.Builder()
    .mode(ExecutionMode.DELEGATED)
    .build();
```

### NativeRegistry

Register handlers for native methods:

```java
NativeRegistry registry = new NativeRegistry();

// Register individual handler
registry.register("java/lang/System", "currentTimeMillis", "()J",
    (receiver, args, ctx) -> ConcreteValue.longValue(System.currentTimeMillis()));

// Register defaults (Object, String, Math, System basics)
registry.registerDefaults();

// Check/execute
if (registry.hasHandler(method)) {
    ConcreteValue result = registry.execute(method, receiver, args, nativeContext);
}
```

Default handlers include:
- `java/lang/Object`: `hashCode()`, `equals()`, `getClass()`
- `java/lang/System`: `currentTimeMillis()`, `nanoTime()`, `arraycopy()`, `identityHashCode()`
- `java/lang/String`: `length()`, `charAt()`, `intern()`, `getBytes()`, `toCharArray()`, `substring()`, `equals()`, `hashCode()`, `isEmpty()`, `concat()`, `valueOf()`
- `java/lang/String` constructors: `<init>([B)`, `<init>([B,String)`, `<init>([C)`
- `java/lang/Math`: `abs()`, `max()`, `min()`, `sqrt()`, `sin()`, `cos()`, `tan()`
- `java/lang/Float/Double`: bit conversion methods
- `java/util/Base64`: `getDecoder()`, `getEncoder()`
- `java/util/Base64$Decoder`: `decode(String)`, `decode(byte[])`
- `java/util/Base64$Encoder`: `encode(byte[])`, `encodeToString(byte[])`

### InvocationHandler

Custom method invocation handling:

```java
// RecursiveHandler - push new frame onto call stack
RecursiveHandler handler = new RecursiveHandler(resolver, registry);
InvocationResult result = handler.invoke(method, receiver, args, context);

if (result.isPushFrame()) {
    StackFrame newFrame = result.getNewFrame();
} else if (result.isNativeHandled()) {
    ConcreteValue returnValue = result.getReturnValue();
} else if (result.isException()) {
    ObjectInstance exception = result.getException();
}

// DelegatingHandler - callback to external code
DelegatingHandler delegating = new DelegatingHandler((method, receiver, args) -> {
    // Custom invocation logic
    return ConcreteValue.intValue(42);
});
```

---

## Debugging Support

### DebugSession

High-level debugger controller:

```java
DebugSession session = new DebugSession(ctx);

// Breakpoints
session.addBreakpoint(new Breakpoint("MyClass", "myMethod", "()V", 10));
session.addBreakpoint(new Breakpoint("MyClass", "myMethod", "()V", 25));

// Start debugging
session.start(method, args);

// Step controls
DebugState state = session.stepInto();   // Step into method calls
state = session.stepOver();               // Step over method calls
state = session.stepOut();                // Run until current method returns
state = session.runToCursor(50);          // Run until PC reaches 50
state = session.resume();                 // Run until breakpoint or end

// State inspection
StackFrame frame = session.getCurrentFrame();
List<?> callStack = session.getCallStack();

// Stop
session.stop();
BytecodeResult result = session.getResult();
```

### DebugState

Snapshot for UI display:

```java
DebugState state = session.getCurrentState();

// Location
String methodSig = state.getMethodSignature();
int pc = state.getPC();
int lineNumber = state.getLineNumber();

// Stack
List<ConcreteValue> stack = state.getOperandStack();

// Locals
Map<Integer, ConcreteValue> locals = state.getLocalVariables();

// Call stack
List<String> callStack = state.getCallStackTrace();
```

### Breakpoint

Execution pause points:

```java
Breakpoint bp = new Breakpoint("com/example/MyClass", "process", "(I)V", 15);

// Enable/disable
bp.setEnabled(false);
boolean enabled = bp.isEnabled();

// Conditional breakpoint (hit count)
bp.setHitCount(5);  // Break after 5 hits

// Check match
boolean matches = bp.matches(method, pc);
```

### BreakpointManager

Breakpoint collection management:

```java
BreakpointManager mgr = new BreakpointManager();

mgr.addBreakpoint(bp1);
mgr.addBreakpoint(bp2);
mgr.removeBreakpoint(bp1);

List<Breakpoint> all = mgr.getAllBreakpoints();
List<Breakpoint> forMethod = mgr.getBreakpointsForMethod("MyClass", "method", "()V");
Breakpoint atPC = mgr.checkBreakpoint("MyClass", "method", "()V", 10);

mgr.removeAllBreakpoints();
```

### StepMode

Stepping granularity:

```java
public enum StepMode {
    INTO,           // Step into method calls
    OVER,           // Step over method calls (stay in current method)
    OUT,            // Run until current method returns
    RUN_TO_CURSOR   // Run until specific PC
}
```

### DebugEventListener

Debug session events:

```java
session.addListener(new DebugEventListener() {
    @Override
    public void onSessionStart(DebugSession session) {
        System.out.println("Debug session started");
    }

    @Override
    public void onSessionStop(DebugSession session, BytecodeResult result) {
        System.out.println("Debug session ended: " + result.getStatus());
    }

    @Override
    public void onBreakpointHit(DebugSession session, Breakpoint bp) {
        System.out.println("Hit breakpoint at PC " + bp.getPC());
    }

    @Override
    public void onStepComplete(DebugSession session, DebugState state) {
        System.out.println("Stepped to PC " + state.getPC());
    }

    @Override
    public void onException(DebugSession session, ObjectInstance exception) {
        System.out.println("Exception: " + exception.getClassName());
    }

    @Override
    public void onStateChange(DebugSession session,
                              DebugSessionState oldState,
                              DebugSessionState newState) {
        System.out.println("State: " + oldState + " -> " + newState);
    }
});
```

---

## Complete Example

```java
import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.debug.*;
import com.tonic.analysis.execution.heap.*;
import com.tonic.analysis.execution.invoke.*;
import com.tonic.analysis.execution.resolve.*;
import com.tonic.analysis.execution.state.*;

public class ExecutionExample {
    public static void debugMethod(MethodEntry method, ClassPool pool) {
        // Setup
        HeapManager heap = new SimpleHeapManager();
        ClassResolver resolver = new ClassResolver(pool);

        NativeRegistry natives = new NativeRegistry();
        natives.registerDefaults();

        BytecodeContext ctx = new BytecodeContext.Builder()
            .heapManager(heap)
            .classResolver(resolver)
            .maxCallDepth(50)
            .maxInstructions(100000)
            .trackStatistics(true)
            .build();

        // Create debug session
        DebugSession session = new DebugSession(ctx);

        // Add breakpoint
        session.addBreakpoint(new Breakpoint(
            method.getOwnerName(),
            method.getName(),
            method.getDesc(),
            0  // First instruction
        ));

        // Add listener
        session.addListener(new DebugEventListener() {
            @Override
            public void onBreakpointHit(DebugSession s, Breakpoint bp) {
                DebugState state = s.getCurrentState();
                System.out.println("=== Breakpoint Hit ===");
                System.out.println("Method: " + state.getMethodSignature());
                System.out.println("PC: " + state.getPC());
                System.out.println("Line: " + state.getLineNumber());
                System.out.println("Stack depth: " + state.getOperandStack().size());
            }
        });

        // Start and step through
        session.start(method, ConcreteValue.intValue(10));

        while (session.isPaused()) {
            DebugState state = session.stepOver();
            System.out.println("PC: " + state.getPC() +
                             " Stack: " + state.getOperandStack());
        }

        // Get result
        if (session.isStopped()) {
            BytecodeResult result = session.getResult();
            System.out.println("=== Execution Complete ===");
            System.out.println("Status: " + result.getStatus());
            System.out.println("Instructions: " + result.getInstructionsExecuted());
            System.out.println("Time: " + result.getExecutionTimeNanos() / 1_000_000.0 + "ms");

            if (result.isSuccess()) {
                System.out.println("Return: " + result.getReturnValue());
            } else if (result.hasException()) {
                System.out.println("Exception: " + result.getException().getClassName());
                result.getStackTrace().forEach(System.out::println);
            }
        }
    }
}
```

---

## Package Structure

```
com.tonic.analysis.execution/
├── core/
│   ├── BytecodeEngine.java       - Main execution driver
│   ├── BytecodeContext.java      - Configuration builder
│   ├── BytecodeResult.java       - Execution outcome
│   ├── ExecutionMode.java        - RECURSIVE or DELEGATED
│   └── ExecutionException.java   - Execution errors
├── state/
│   ├── ConcreteValue.java        - Tagged union value
│   ├── ValueTag.java             - Value type tags
│   ├── ConcreteStack.java        - Mutable operand stack
│   └── ConcreteLocals.java       - Mutable local variables
├── frame/
│   ├── StackFrame.java           - Method activation record
│   └── CallStack.java            - Call stack management
├── heap/
│   ├── HeapManager.java          - Allocation interface
│   ├── SimpleHeapManager.java    - Default implementation
│   ├── ObjectInstance.java       - Simulated object
│   ├── ArrayInstance.java        - Simulated array
│   └── StringPool.java           - String interning
├── resolve/
│   ├── ClassResolver.java        - Class/method resolution
│   ├── ResolutionCache.java      - Resolution caching
│   ├── ResolvedMethod.java       - Resolved method info
│   └── ResolvedField.java        - Resolved field info
├── dispatch/
│   ├── OpcodeDispatcher.java     - Opcode execution (150+)
│   ├── DispatchResult.java       - Dispatch outcomes
│   ├── DispatchContext.java      - Dispatch context
│   ├── InvokeDynamicInfo.java    - invokedynamic call site info
│   ├── ConstantDynamicInfo.java  - CONSTANT_Dynamic info
│   ├── MethodHandleInfo.java     - CONSTANT_MethodHandle info
│   ├── MethodTypeInfo.java       - CONSTANT_MethodType info
│   └── FieldInfo.java            - Resolved field info
├── invoke/
│   ├── InvocationHandler.java    - Invocation interface
│   ├── RecursiveHandler.java     - Internal call handling
│   ├── DelegatingHandler.java    - External call handling
│   ├── NativeRegistry.java       - Native method handlers
│   ├── NativeContext.java        - Native execution context
│   ├── NativeException.java      - Native method errors
│   ├── StringConcatHandler.java  - String concatenation handler
│   └── LambdaProxyFactory.java   - Lambda proxy creation
├── listener/
│   ├── BytecodeListener.java     - Execution events
│   ├── TracingListener.java      - Execution tracing
│   └── StatisticsListener.java   - Execution metrics
└── debug/
    ├── DebugSession.java         - Debug controller
    ├── DebugState.java           - UI snapshot
    ├── DebugSessionState.java    - Session state enum
    ├── DebugEventListener.java   - Debug events
    ├── Breakpoint.java           - Pause point
    ├── BreakpointManager.java    - Breakpoint collection
    ├── StepMode.java             - Step granularity
    └── InstructionInterceptor.java - Execution interception
```

---

## Java 11 Support

The Execution API provides full Java 11 bytecode support including dynamic features:

### Supported Dynamic Features

| Feature | Bytecode | Description |
|---------|----------|-------------|
| Lambda expressions | `invokedynamic` | `Runnable r = () -> doSomething();` |
| Method references | `invokedynamic` | `list.forEach(System.out::println);` |
| String concatenation | `invokedynamic` | `"Hello " + name + "!"` (Java 9+) |
| Constant dynamic | `ldc` | Dynamic constants via bootstrap methods |
| Method handles | `ldc` | `CONSTANT_MethodHandle` loading |
| Method types | `ldc` | `CONSTANT_MethodType` loading |

### InvokeDynamicInfo

When `invokedynamic` is encountered, the engine creates an `InvokeDynamicInfo` object:

```java
// After dispatch returns INVOKE_DYNAMIC
InvokeDynamicInfo info = context.getPendingInvokeDynamic();

String methodName = info.getMethodName();        // e.g., "run", "apply", "makeConcatWithConstants"
String descriptor = info.getDescriptor();        // e.g., "()Ljava/lang/Runnable;"
int bsmIndex = info.getBootstrapMethodIndex();   // Bootstrap method index

// Pattern detection
if (info.isLambdaMetafactory()) {
    // Lambda or method reference
} else if (info.isStringConcat()) {
    // String concatenation (Java 9+)
}
```

### ConstantDynamicInfo

When `ldc` loads a `CONSTANT_Dynamic` entry, the engine returns `CONSTANT_DYNAMIC`:

```java
// After dispatch returns CONSTANT_DYNAMIC
ConstantDynamicInfo info = context.getPendingConstantDynamic();

String name = info.getName();                    // Constant name
String descriptor = info.getDescriptor();        // Type descriptor (e.g., "I", "J", "Ljava/lang/String;")
int bsmIndex = info.getBootstrapMethodIndex();   // Bootstrap method index

// Type queries
boolean isWide = info.isWideType();              // true for J (long) or D (double)
boolean isPrimitive = info.isPrimitive();        // true for I, J, F, D, Z, B, C, S
boolean isReference = info.isReference();        // true for L... or [... types
```

### MethodHandleInfo

When `ldc` loads a `CONSTANT_MethodHandle` entry:

```java
// After dispatch returns METHOD_HANDLE
MethodHandleInfo info = context.getPendingMethodHandle();

int kind = info.getReferenceKind();              // 1-9 (REF_getField through REF_invokeInterface)
String owner = info.getOwner();                  // Owner class
String name = info.getName();                    // Field/method name
String desc = info.getDescriptor();              // Field/method descriptor

// Classification
boolean isField = info.isFieldReference();       // kinds 1-4
boolean isMethod = info.isMethodReference();     // kinds 5-9
boolean isGetter = info.isGetter();              // REF_getField or REF_getStatic
boolean isSetter = info.isSetter();              // REF_putField or REF_putStatic
boolean isStatic = info.isStatic();              // Static field/method reference
boolean isCtor = info.isConstructor();           // REF_newInvokeSpecial
```

Reference kind constants:
```java
MethodHandleInfo.REF_getField         // 1
MethodHandleInfo.REF_getStatic        // 2
MethodHandleInfo.REF_putField         // 3
MethodHandleInfo.REF_putStatic        // 4
MethodHandleInfo.REF_invokeVirtual    // 5
MethodHandleInfo.REF_invokeStatic     // 6
MethodHandleInfo.REF_invokeSpecial    // 7
MethodHandleInfo.REF_newInvokeSpecial // 8
MethodHandleInfo.REF_invokeInterface  // 9
```

### MethodTypeInfo

When `ldc` loads a `CONSTANT_MethodType` entry:

```java
// After dispatch returns METHOD_TYPE
MethodTypeInfo info = context.getPendingMethodType();

String desc = info.getDescriptor();              // e.g., "(ILjava/lang/String;)V"
String returnType = info.getReturnType();        // e.g., "V"
boolean isVoid = info.isVoidReturn();            // true if returns void

// Parameter analysis
int paramCount = info.getParameterCount();       // Number of parameters
String[] paramTypes = info.getParameterTypes();  // Individual parameter types
int slots = info.getParameterSlots();            // Total slots (long/double = 2)
```

### StringConcatHandler

Handles `StringConcatFactory` bootstrap methods for string concatenation:

```java
StringConcatHandler handler = new StringConcatHandler();

// Check if invokedynamic is string concatenation
if (handler.isStringConcat(invokeDynamicInfo)) {
    // Execute concatenation with recipe
    String recipe = "\u0001 + \u0001 = \u0001";  // \u0001 = dynamic arg, \u0002 = constant
    ConcreteValue[] args = { intVal1, intVal2, resultVal };
    Object[] constants = {};

    String result = handler.executeConcat(info, args, recipe, constants);
    // Result: "1 + 2 = 3"
}

// Recipe analysis
int dynamicArgCount = handler.countDynamicArgs(recipe);
int constantCount = handler.countConstants(recipe);
String readable = handler.parseRecipe(recipe);  // "{arg} + {arg} = {arg}"
```

Recipe format:
- `\u0001` (TAG_ARG) - Insert dynamic argument from stack
- `\u0002` (TAG_CONST) - Insert constant from bootstrap arguments
- Other characters - Literal text

### LambdaProxyFactory

Creates proxy objects for lambda expressions:

```java
LambdaProxyFactory factory = new LambdaProxyFactory(heapManager);

// Check if invokedynamic is lambda
if (factory.isLambdaFactory(invokeDynamicInfo)) {
    // Captured variables from stack
    ConcreteValue[] captured = { ConcreteValue.intValue(42) };

    // Create proxy object
    ObjectInstance proxy = factory.createProxy(info, captured);
    // proxy.getClassName() = "$Lambda$1"
    // proxy.getField("$Lambda$1", "capture$0", "I") = 42
}

// Descriptor analysis
int capturedCount = factory.getCapturedArgCount(descriptor);
String interfaceType = factory.extractInterfaceType(descriptor);
```

### Simulated Behavior

Since bootstrap methods cannot be executed in isolation, the engine simulates common patterns:

| Pattern | Dispatch Result | Simulated Result |
|---------|-----------------|------------------|
| Lambda metafactory | `INVOKE_DYNAMIC` | Proxy object with captured values |
| String concat | `INVOKE_DYNAMIC` | Concatenated string |
| Constant dynamic (int) | `CONSTANT_DYNAMIC` | Default value (0) |
| Constant dynamic (long/double) | `CONSTANT_DYNAMIC` | Default value (0L/0.0) |
| Constant dynamic (reference) | `CONSTANT_DYNAMIC` | Simulated object |
| Method handle | `METHOD_HANDLE` | Simulated MethodHandle object |
| Method type | `METHOD_TYPE` | Simulated MethodType object |

---

## Key Differences from Simulation API

| Feature | Simulation API | Execution API |
|---------|----------------|---------------|
| State mutability | Immutable (functional) | Mutable (imperative) |
| Object model | Abstract types only | Full heap simulation |
| Method calls | Tracked but not executed | Actually executed |
| Debugging | Not supported | Full debugger support |
| Performance | Fast (no allocation) | Slower (full execution) |
| Use case | Static analysis | Dynamic analysis |

Use **Simulation API** when you need:
- Stack depth analysis
- Allocation counting
- Control flow metrics
- Value flow tracking

Use **Execution API** when you need:
- Actual method execution
- Interactive debugging
- REPL environment
- Test harness

---

[<- Back to Analysis APIs](analysis-apis.md)
