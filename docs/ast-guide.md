[<- Back to README](../README.md) | [SSA Transforms](ssa-transforms.md) | [Frame Computation ->](frame-computation.md)

# AST Guide

The AST (Abstract Syntax Tree) system provides source-level representation of Java bytecode. It enables you to recover readable Java source from compiled classes, mutate the code at a high level, and recompile back to bytecode.

## What is the AST System?

The AST system bridges bytecode and source code:

- **Recovery** - Convert SSA IR to structured source AST
- **Mutation** - Modify code using familiar Java constructs
- **Emission** - Generate readable Java source from AST
- **Lowering** - Convert AST back to SSA IR for recompilation

This enables workflows like decompilation, code transformation, and instrumentation at the source level rather than manipulating raw bytecode.

## AST Pipeline

```
Bytecode --[Lift]--> SSA IR --[Recovery]--> AST
                                             |
                                         [Mutate]
                                             |
                                             v
Bytecode <--[Lower]-- SSA IR <--[Lowering]-- AST
                                             |
                                         [Emit]
                                             |
                                             v
                                        Java Source
```

The pipeline has two paths:
1. **Forward path** - Bytecode -> IR -> AST -> Source (decompilation)
2. **Round-trip path** - Bytecode -> IR -> AST -> Mutate -> IR -> Bytecode (transformation)

## Basic Usage

### Decompiling a Complete Class

The easiest way to decompile an entire class file to Java source is using `ClassDecompiler`:

```java
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.emit.SourceEmitterConfig;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

// Load the class
ClassFile cf = ClassPool.getDefault().loadClass(inputStream);

// Decompile with default settings (simple class names, includes imports)
String source = ClassDecompiler.decompile(cf);
System.out.println(source);

// Or with custom configuration
SourceEmitterConfig config = SourceEmitterConfig.builder()
    .useFullyQualifiedNames(false)  // Use simple names with imports
    .alwaysUseBraces(true)
    .build();

String source = ClassDecompiler.decompile(cf, config);
```

This produces complete Java source with:
- Package declaration
- Import statements (when using simple class names)
- Class declaration with modifiers, extends, implements
- Fields with access modifiers
- Static initializer blocks
- Constructors
- Methods

**Command-line usage:**

```bash
# Basic usage
java -cp build/classes/java/main com.tonic.demo.ast.Decompile MyClass.class

# With fully qualified names
java -cp build/classes/java/main com.tonic.demo.ast.Decompile MyClass.class --fqn

# With transform preset (none, minimal, standard, aggressive)
java -cp build/classes/java/main com.tonic.demo.ast.Decompile MyClass.class --preset=standard
java -cp build/classes/java/main com.tonic.demo.ast.Decompile MyClass.class --preset=aggressive --fqn
```

### Recovering AST from Bytecode

```java
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

// 1. Load the class
ClassPool pool = ClassPool.getDefault();
ClassFile cf = pool.loadClass(inputStream);
MethodEntry method = cf.getMethod("myMethod", "(I)I");

// 2. Lift to SSA IR
SSA ssa = new SSA(cf.getConstPool());
IRMethod ir = ssa.lift(method);

// 3. Recover AST
BlockStmt ast = MethodRecoverer.recoverMethod(ir, method);

// 4. Print as source
System.out.println(SourceEmitter.emit(ast));
```

### Emitting Source Code

```java
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.source.emit.SourceEmitterConfig;

// Simple emission
String source = SourceEmitter.emit(statement);
String exprCode = SourceEmitter.emit(expression);

// With configuration
SourceEmitterConfig config = SourceEmitterConfig.builder()
    .useVarKeyword(true)       // Use 'var' for local variables
    .alwaysUseBraces(true)     // Braces on all control structures
    .includeIRComments(false)  // Omit IR debug comments
    .build();

String formatted = SourceEmitter.emit(statement, config);
```

### Mutating the AST

AST nodes are mutable. You can traverse and modify them:

```java
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.stmt.*;

// Traverse and modify literals
void mutateBlock(BlockStmt block) {
    for (Statement stmt : block.getStatements()) {
        if (stmt instanceof ReturnStmt ret && ret.getValue() != null) {
            mutateExpression(ret.getValue());
        } else if (stmt instanceof VarDeclStmt decl && decl.getInitializer() != null) {
            mutateExpression(decl.getInitializer());
        }
    }
}

void mutateExpression(Expression expr) {
    if (expr instanceof LiteralExpr lit) {
        Object value = lit.getValue();
        if (value instanceof Integer i) {
            lit.setValue(i * 2);  // Double all integer literals
        }
    } else if (expr instanceof BinaryExpr bin) {
        mutateExpression(bin.getLeft());
        mutateExpression(bin.getRight());
    }
}
```

### Lowering AST to Bytecode

```java
import com.tonic.analysis.source.lower.ASTLowerer;

// After mutating the AST, lower back to IR
ASTLowerer lowerer = new ASTLowerer(cf.getConstPool());
lowerer.lower(ast, ir, method);

// Lower IR to bytecode
ssa.lower(ir, method);

// Export the modified class
cf.rebuild();
byte[] bytes = cf.write();
```

## AST Node Types

### Expressions (24 types)

| Expression | Description | Example |
|------------|-------------|---------|
| `LiteralExpr` | Constants | `42`, `"hello"`, `true` |
| `VarRefExpr` | Variable reference | `x`, `count` |
| `BinaryExpr` | Binary operation | `a + b`, `x == y` |
| `UnaryExpr` | Unary operation | `-x`, `!flag`, `++i` |
| `MethodCallExpr` | Method invocation | `obj.method(args)` |
| `FieldAccessExpr` | Field access | `obj.field`, `Class.staticField` |
| `ArrayAccessExpr` | Array index | `arr[i]` |
| `NewExpr` | Object creation | `new MyClass(args)` |
| `NewArrayExpr` | Array creation | `new int[10]` |
| `ArrayInitExpr` | Array initializer | `{1, 2, 3}` |
| `CastExpr` | Type cast | `(String) obj` |
| `InstanceOfExpr` | Type check | `obj instanceof String` |
| `TernaryExpr` | Conditional | `cond ? a : b` |
| `ThisExpr` | This reference | `this` |
| `SuperExpr` | Super reference | `super` |
| `ClassExpr` | Class literal | `String.class` |
| `LambdaExpr` | Lambda | `x -> x * 2` |
| `MethodRefExpr` | Method reference | `String::length` |

### Statements (18 types)

| Statement | Description | Example |
|-----------|-------------|---------|
| `BlockStmt` | Statement block | `{ ... }` |
| `VarDeclStmt` | Variable declaration | `int x = 5;` |
| `ExprStmt` | Expression statement | `foo();` |
| `ReturnStmt` | Return | `return x;` |
| `IfStmt` | Conditional | `if (cond) { } else { }` |
| `WhileStmt` | While loop | `while (cond) { }` |
| `DoWhileStmt` | Do-while loop | `do { } while (cond);` |
| `ForStmt` | For loop | `for (init; cond; update) { }` |
| `ForEachStmt` | Enhanced for | `for (T x : items) { }` |
| `SwitchStmt` | Switch | `switch (x) { case 1: ... }` |
| `BreakStmt` | Break | `break;`, `break label;` |
| `ContinueStmt` | Continue | `continue;` |
| `ThrowStmt` | Throw exception | `throw new Exception();` |
| `TryCatchStmt` | Try-catch | `try { } catch (E e) { }` |
| `SynchronizedStmt` | Synchronized | `synchronized (lock) { }` |
| `LabeledStmt` | Labeled statement | `label: while (...) { }` |

### Type System

```java
import com.tonic.analysis.source.ast.type.*;

// Primitive types
PrimitiveSourceType intType = PrimitiveSourceType.INT;
PrimitiveSourceType boolType = PrimitiveSourceType.BOOLEAN;

// Reference types
ReferenceSourceType stringType = ReferenceSourceType.of("java/lang/String");
ReferenceSourceType listType = ReferenceSourceType.of("java/util/List", "E");

// Array types
ArraySourceType intArray = ArraySourceType.of(PrimitiveSourceType.INT, 1);
ArraySourceType matrix = ArraySourceType.of(PrimitiveSourceType.DOUBLE, 2);

// Convert between IR and source types
SourceType sourceType = SourceType.fromIRType(irType);
IRType irType = sourceType.toIRType();

// Get Java source representation
String javaType = sourceType.toJavaSource();  // "int[]", "String", etc.
```

## Recovery System

### MethodRecoverer

The main entry point for AST recovery:

```java
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.source.recovery.NameRecoveryStrategy;

// Simple recovery
BlockStmt ast = MethodRecoverer.recoverMethod(irMethod, methodEntry);

// With custom name strategy
BlockStmt ast = MethodRecoverer.recoverMethod(
    irMethod,
    methodEntry,
    NameRecoveryStrategy.PREFER_DEBUG_INFO
);

// Advanced: step-by-step recovery
MethodRecoverer recoverer = new MethodRecoverer(irMethod, methodEntry);
recoverer.analyze();            // Compute dominators, loops, def-use
recoverer.initializeRecovery(); // Set up contexts and name recoverer
BlockStmt body = recoverer.recover();

// Access analysis results
DominatorTree domTree = recoverer.getDominatorTree();
LoopAnalysis loops = recoverer.getLoopAnalysis();
```

### Name Recovery Strategies

| Strategy | Description |
|----------|-------------|
| `PREFER_DEBUG_INFO` | Use debug symbols when available, fall back to synthetic |
| `SYNTHETIC_ONLY` | Always generate synthetic names (v0, v1, ...) |

## Lowering System

### ASTLowerer

Converts AST back to SSA IR:

```java
import com.tonic.analysis.source.lower.ASTLowerer;

// Create lowerer
ASTLowerer lowerer = new ASTLowerer(constPool);

// Option 1: Lower into existing IRMethod
lowerer.lower(bodyAst, existingIRMethod, methodEntry);

// Option 2: Replace method body entirely
lowerer.replaceBody(newBodyAst, existingIRMethod);

// Option 3: Create new IRMethod from scratch
IRMethod newMethod = ASTLowerer.lowerMethod(
    bodyAst,                    // BlockStmt
    "methodName",               // method name
    "com/example/MyClass",      // owner class (internal name)
    false,                      // isStatic
    List.of(PrimitiveSourceType.INT),  // parameter types
    PrimitiveSourceType.INT,    // return type
    constPool
);
```

### Control Flow Lowering

The lowerer generates proper control flow graphs:

**If-Then-Else:**
```
entry: branch cond -> thenBlock, elseBlock
thenBlock: ... goto mergeBlock
elseBlock: ... goto mergeBlock
mergeBlock: ...
```

**While Loop:**
```
entry: goto condBlock
condBlock: branch cond -> bodyBlock, exitBlock
bodyBlock: ... goto condBlock
exitBlock: ...
```

**For Loop:**
```
entry: [init] goto condBlock
condBlock: branch cond -> bodyBlock, exitBlock
bodyBlock: ... goto updateBlock
updateBlock: [update] goto condBlock
exitBlock: ...
```

## Source Emission

### SourceEmitter

```java
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.source.emit.SourceEmitterConfig;
import com.tonic.analysis.source.emit.IndentingWriter;

// Emit to string
String source = SourceEmitter.emit(statement);

// Emit with configuration
SourceEmitterConfig config = SourceEmitterConfig.defaults();
String source = SourceEmitter.emit(statement, config);

// Emit to custom writer
IndentingWriter writer = IndentingWriter.toStringWriter();
SourceEmitter emitter = new SourceEmitter(writer, config);
statement.accept(emitter);
String result = writer.toString();
```

### Configuration Options

```java
SourceEmitterConfig config = SourceEmitterConfig.builder()
    .useVarKeyword(true)           // Use 'var' keyword
    .alwaysUseBraces(true)         // Braces even for single statements
    .useFullyQualifiedNames(false) // Simple class names
    .includeIRComments(false)      // Omit IR debug info
    .build();

// Presets
SourceEmitterConfig defaults = SourceEmitterConfig.defaults();
SourceEmitterConfig debug = SourceEmitterConfig.debug();
SourceEmitterConfig compact = SourceEmitterConfig.compact();
```

## Example: Complete Roundtrip

This example demonstrates the full transformation pipeline:

```java
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.*;
import com.tonic.utill.ClassFileUtil;

public class ASTRoundtrip {

    public static void main(String[] args) throws Exception {
        // 1. Load the class
        ClassPool pool = ClassPool.getDefault();
        ClassFile cf = pool.loadClass(
            ASTRoundtrip.class.getResourceAsStream("Target.class"));
        MethodEntry method = cf.getMethod("calculate", "(I)I");
        ConstPool constPool = cf.getConstPool();

        // 2. Lift to SSA IR
        SSA ssa = new SSA(constPool);
        IRMethod ir = ssa.lift(method);
        System.out.println("Lifted: " + ir.getBlocks().size() + " blocks");

        // 3. Recover AST
        BlockStmt ast = MethodRecoverer.recoverMethod(ir, method);
        System.out.println("\nOriginal source:");
        System.out.println(SourceEmitter.emit(ast));

        // 4. Mutate: double all integer literals
        mutateIntegers(ast);
        System.out.println("\nMutated source:");
        System.out.println(SourceEmitter.emit(ast));

        // 5. Lower AST back to IR
        ASTLowerer lowerer = new ASTLowerer(constPool);
        lowerer.lower(ast, ir, method);

        // 6. Lower IR to bytecode
        ssa.lower(ir, method);

        // 7. Export modified class
        cf.rebuild();
        ClassFileUtil.saveClassFile(cf.write(), "output", cf.getClassName());
        System.out.println("\nExported: " + cf.getClassName() + ".class");
    }

    static void mutateIntegers(BlockStmt block) {
        for (Statement stmt : block.getStatements()) {
            mutateStatement(stmt);
        }
    }

    static void mutateStatement(Statement stmt) {
        if (stmt instanceof ReturnStmt ret && ret.getValue() != null) {
            mutateExpr(ret.getValue());
        } else if (stmt instanceof VarDeclStmt decl && decl.getInitializer() != null) {
            mutateExpr(decl.getInitializer());
        } else if (stmt instanceof BlockStmt block) {
            mutateIntegers(block);
        } else if (stmt instanceof IfStmt ifStmt) {
            mutateExpr(ifStmt.getCondition());
            mutateStatement(ifStmt.getThenBranch());
            if (ifStmt.getElseBranch() != null) {
                mutateStatement(ifStmt.getElseBranch());
            }
        }
    }

    static void mutateExpr(Expression expr) {
        if (expr instanceof LiteralExpr lit && lit.getValue() instanceof Integer i) {
            lit.setValue(i * 2);
        } else if (expr instanceof BinaryExpr bin) {
            mutateExpr(bin.getLeft());
            mutateExpr(bin.getRight());
        }
    }
}
```

## Visitor Pattern

The AST system supports the visitor pattern:

```java
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

// Implement a visitor
public class LiteralCounter extends AbstractSourceVisitor<Integer> {
    private int count = 0;

    @Override
    public Integer visitLiteral(LiteralExpr expr) {
        count++;
        return count;
    }

    @Override
    public Integer visitBinary(BinaryExpr expr) {
        expr.getLeft().accept(this);
        expr.getRight().accept(this);
        return count;
    }

    public int getCount() {
        return count;
    }
}

// Use the visitor
LiteralCounter counter = new LiteralCounter();
statement.accept(counter);
System.out.println("Literals found: " + counter.getCount());
```

## Class Decompilation

### ClassDecompiler

The `ClassDecompiler` provides full class decompilation to Java source:

```java
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.decompile.DecompilerConfig;
import com.tonic.analysis.source.decompile.TransformPreset;
import com.tonic.analysis.source.emit.SourceEmitterConfig;

// Simple usage
String source = ClassDecompiler.decompile(classFile);

// With emitter configuration
SourceEmitterConfig emitterConfig = SourceEmitterConfig.builder()
    .useFullyQualifiedNames(false)  // Simple names + imports (default)
    .alwaysUseBraces(true)
    .useVarKeyword(false)
    .build();

String source = ClassDecompiler.decompile(classFile, emitterConfig);

// Using the builder API with transform presets
ClassDecompiler decompiler = ClassDecompiler.builder(classFile)
    .config(emitterConfig)
    .preset(TransformPreset.STANDARD)
    .build();

String source = decompiler.decompile();

// Or write directly to a custom writer
IndentingWriter writer = new IndentingWriter(new FileWriter("Output.java"));
decompiler.decompile(writer);
```

### Configurable Transform Pipeline

The decompiler applies IR transforms to improve output quality. You can configure which transforms run using presets or by adding transforms manually.

**Transform Presets:**

| Preset | Description | Transforms |
|--------|-------------|------------|
| `NONE` | No additional transforms (baseline only) | - |
| `MINIMAL` | Safe, minimal cleanup | ConstantFolding, CopyPropagation |
| `STANDARD` | Balanced optimization | Above + AlgebraicSimplification, RedundantCopyElimination, DeadCodeElimination |
| `AGGRESSIVE` | Maximum simplification | Above + StrengthReduction, Reassociate, CommonSubexpressionElimination, PhiConstantPropagation |

**Using Presets:**

```java
// Use a preset
ClassDecompiler decompiler = ClassDecompiler.builder(classFile)
    .config(emitterConfig)
    .preset(TransformPreset.AGGRESSIVE)
    .build();

String source = decompiler.decompile();
```

**Manual Transform Configuration:**

```java
import com.tonic.analysis.ssa.transform.*;

// Add specific transforms manually
ClassDecompiler decompiler = ClassDecompiler.builder(classFile)
    .config(emitterConfig)
    .addTransform(new ConstantFolding())
    .addTransform(new CopyPropagation())
    .addTransform(new DeadCodeElimination())
    .build();
```

**Combining Presets with Additional Transforms:**

```java
// Start with a preset, then add more transforms
ClassDecompiler decompiler = ClassDecompiler.builder(classFile)
    .config(emitterConfig)
    .preset(TransformPreset.MINIMAL)
    .addTransform(new NullCheckElimination())
    .addTransform(new AlgebraicSimplification())
    .build();
```

**Using DecompilerConfig Directly:**

```java
// Build config separately for reuse
DecompilerConfig decompilerConfig = DecompilerConfig.builder()
    .emitterConfig(emitterConfig)
    .preset(TransformPreset.STANDARD)
    .addTransform(new LoopInvariantCodeMotion())
    .build();

// Use with multiple class files
ClassDecompiler decompiler1 = new ClassDecompiler(classFile1, decompilerConfig);
ClassDecompiler decompiler2 = new ClassDecompiler(classFile2, decompilerConfig);
```

**Baseline Transforms:**

The decompiler always applies these baseline transforms regardless of configuration:
- `ControlFlowReducibility` - Converts irreducible control flow to reducible form
- `DuplicateBlockMerging` - Merges duplicate blocks created by reducibility

Additional transforms from presets or manual configuration run after these baseline transforms.

### Import Statement Generation

When `useFullyQualifiedNames` is `false` (the default), the decompiler automatically generates import statements by:

1. Scanning the constant pool for all referenced class types
2. Filtering out `java.lang.*` classes (implicitly imported in Java)
3. Filtering out classes in the same package as the decompiled class
4. Sorting imports alphabetically

Note that subpackages of `java.lang` (like `java.lang.invoke.*` or `java.lang.reflect.*`) are NOT implicitly imported and will be included in the import list.

Example output:

```java
package com.example;

import java.awt.Canvas;
import java.awt.Graphics;
import java.util.List;
import javax.swing.JFrame;

public class MyClass extends JFrame {
    private Canvas canvas;
    private List items;

    // ... methods
}
```

### Output Structure

The decompiler produces properly structured Java source:

```
package declaration
<blank line>
import statements (sorted, when using simple names)
<blank line>
class declaration {

    fields
    <blank line>
    static initializer (if present)
    <blank line>
    constructors
    <blank line>
    methods

}
```

### Handling Decompilation Failures

If a method body fails to decompile, the decompiler inserts a comment instead of crashing:

```java
public void problematicMethod() {
    // Failed to decompile: <error message>
}
```

This allows partial decompilation of classes even when some methods have complex or unsupported bytecode patterns.

## AST Transforms

AST transforms operate on the recovered AST to improve output readability. Unlike IR transforms (which operate on SSA form), AST transforms work on the high-level Java-like structure after recovery.

### ASTTransform Interface

```java
package com.tonic.analysis.source.ast.transform;

public interface ASTTransform {
    String getName();
    boolean transform(BlockStmt block);
}
```

The `transform` method modifies the block in place and returns `true` if any changes were made.

### ControlFlowSimplifier

The `ControlFlowSimplifier` applies multiple cleanup passes to improve decompiled output quality:

```java
import com.tonic.analysis.source.ast.transform.ControlFlowSimplifier;

ControlFlowSimplifier simplifier = new ControlFlowSimplifier();
boolean changed = simplifier.transform(methodBody);
```

**Transformations Applied:**

| Transform | Before | After |
|-----------|--------|-------|
| Empty block inversion | `if (x) { } else { body }` | `if (!x) { body }` |
| Guard clause conversion | `if (x) { long } else { return; }` | `if (!x) return; long` |
| Comparison flipping | `if (!(a == b))` | `if (a != b)` |
| AND-chain merging | `if (a) { if (b) { body } }` | `if (a && b) { body }` |
| Self-assignment removal | `x = x;` | *(removed)* |
| Duplicate assignment removal | `x = 0; x = 0;` | `x = 0;` |
| If-else to ternary | `if (c) { x = 0; } else { x = 1; }` | `x = c ? 0 : 1;` |
| Declaration movement | `int x = 0; ... x = val;` | `int x = val;` |
| Declaration into blocks | Declarations at method start | Moved into try/nested blocks |

**Comparison Flipping:**

When negating comparisons, the simplifier flips operators instead of wrapping with `!`:

| Original | Flipped |
|----------|---------|
| `!(a == b)` | `a != b` |
| `!(a != b)` | `a == b` |
| `!(a < b)` | `a >= b` |
| `!(a <= b)` | `a > b` |
| `!(a > b)` | `a <= b` |
| `!(a >= b)` | `a < b` |

**Declaration Movement:**

The simplifier moves variable declarations from method/block start to their first assignment when safe:

```java
// Before
int local3 = 0;
int local4 = 0;
doSomething();
local3 = computeValue();
local4 = otherValue();

// After
doSomething();
int local3 = computeValue();
int local4 = otherValue();
```

Safety checks ensure:
- Variable is not read between declaration and first assignment
- First assignment is a simple `=` (not `+=`, etc.)
- No control flow between declaration and assignment

**Moving Declarations Into Nested Blocks:**

When a variable is only used inside a try block (not in catch/finally), the declaration is moved inside:

```java
// Before
int local2 = 0;
try {
    local2 = getValue();
    use(local2);
} catch (Exception e) {
    // local2 not used here
}

// After
try {
    int local2 = getValue();
    use(local2);
} catch (Exception e) {
}
```

### Integration with ClassDecompiler

AST transforms are applied automatically after method recovery:

```java
// In ClassDecompiler, after recovering method body:
BlockStmt body = methodRecoverer.recover();
astSimplifier.transform(body);  // Apply AST transforms
emitBlockContents(body, writer);
```

### Writing Custom AST Transforms

```java
public class MyTransform implements ASTTransform {

    @Override
    public String getName() {
        return "MyTransform";
    }

    @Override
    public boolean transform(BlockStmt block) {
        boolean changed = false;
        List<Statement> stmts = block.getStatements();

        for (int i = 0; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);

            // Your transformation logic here
            if (shouldTransform(stmt)) {
                stmts.set(i, transformStatement(stmt));
                changed = true;
            }

            // Recurse into nested blocks
            if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                if (ifStmt.getThenBranch() instanceof BlockStmt) {
                    changed |= transform((BlockStmt) ifStmt.getThenBranch());
                }
            }
            // ... handle other control structures
        }

        return changed;
    }
}
```

### Measured Improvements

On obfuscated code, the ControlFlowSimplifier achieves:

| Metric | Improvement |
|--------|-------------|
| Else blocks | -14% |
| Negated conditions `if (!(...)` | -92% |
| Self-assignments | -98% |
| Ternary expressions | +148 (from if-else conversion) |
| AND conditions | +315 (from nested if merging) |

## Related Documentation

- [AST Editor](ast-editor.md) - ExprEditor-style AST transformations
- [Renamer API](renamer-api.md) - Class, method, and field renaming
- [SSA Guide](ssa-guide.md) - SSA intermediate representation
- [SSA Transforms](ssa-transforms.md) - Optimizations and analysis
- [Architecture](architecture.md) - System overview

---

[<- Back to README](../README.md) | [SSA Transforms](ssa-transforms.md) | [Frame Computation ->](frame-computation.md)
