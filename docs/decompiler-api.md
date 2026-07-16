[<- Back to README](../README.md) | [AST Guide](ast-guide.md) | [AST Editor ->](ast-editor.md)

# Decompiler API

`ClassDecompiler` turns a `ClassFile` into Java source. It is the front end of YABR's
"(de)compiler" direction: each method is lifted to SSA, recovered into the structured
(expression/statement) IR, and emitted as source, while class-level structure (fields,
constructors, nested types, imports) is reconstructed around it.

This guide covers the decompiler entry points, emitter configuration, transform presets, and
the provenance data (line maps and source spans) the decompiler can produce. For the AST layer
the decompiler is built on - recovery, mutation, and emission of individual method bodies - see
the [AST Guide](ast-guide.md).

## Quick Decompile

The simplest path is the static `decompile` method:

```java
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

ClassFile cf = ClassPool.getDefault().loadClass(inputStream);

String source = ClassDecompiler.decompile(cf);
System.out.println(source);
```

This produces complete Java source with a package declaration, generated imports (simple names
by default), and the class body.

## Entry Points

| Call | Returns | Use |
|------|---------|-----|
| `ClassDecompiler.decompile(cf)` | `String` | One-shot decompile with default settings |
| `ClassDecompiler.decompile(cf, SourceEmitterConfig)` | `String` | One-shot with custom emitter options |
| `new ClassDecompiler(cf)` | instance | Reusable instance, default config |
| `new ClassDecompiler(cf, SourceEmitterConfig)` | instance | Instance with emitter options |
| `new ClassDecompiler(cf, DecompilerConfig)` | instance | Instance with emitter + transform config |
| `ClassDecompiler.builder(cf)...build()` | instance | Fluent configuration (see below) |
| `instance.decompile()` | `String` | Decompile to a source string |
| `instance.decompileWithLineMap()` | `DecompileResult` | Source **plus** line maps and member spans |
| `instance.decompile(IndentingWriter)` | `void` | Write source directly to a writer |

The builder wraps a `DecompilerConfig.Builder`:

```java
ClassDecompiler decompiler = ClassDecompiler.builder(cf)
    .config(emitterConfig)             // SourceEmitterConfig
    .preset(TransformPreset.STANDARD)  // transform preset
    .addTransform(new NullCheckElimination())
    .build();

String source = decompiler.decompile();
```

## Emitter Configuration

`SourceEmitterConfig` controls how source text is rendered. Build one with `SourceEmitterConfig.builder()`,
or use a preset: `defaults()`, `debug()` (adds IR comments and line numbers), or `compact()`.

| Option | Default | Effect |
|--------|---------|--------|
| `useFullyQualifiedNames(boolean)` | `false` | `false` = simple names + generated imports; `true` = fully-qualified names, no imports |
| `alwaysUseBraces(boolean)` | - | Always brace single-statement `if`/`for`/`while` bodies |
| `useVarKeyword(boolean)` | - | Emit `var` for local declarations where applicable |
| `includeIRComments(boolean)` | - | Annotate output with IR-level comments (debugging) |
| `includeLineNumbers(boolean)` | - | Emit original bytecode line numbers as comments |
| `blankLinesBetweenMethods(boolean)` | - | Insert blank lines between members |
| `indentString(String)` | - | Indentation unit (e.g. 4 spaces or a tab) |
| `maxLineLength(int)` | - | Soft wrap width |
| `identifierMode(IdentifierMode)` | `RAW` | Identifier rendering: `RAW` or `UNICODE_ESCAPE` |
| `resolveBootstrapMethods(boolean)` | - | Resolve `invokedynamic` bootstrap methods (e.g. lambdas, string concat) |

```java
SourceEmitterConfig emitterConfig = SourceEmitterConfig.builder()
    .useFullyQualifiedNames(false)  // simple names + imports (default)
    .alwaysUseBraces(true)
    .useVarKeyword(false)
    .build();

String source = ClassDecompiler.decompile(cf, emitterConfig);
```

## Transform Presets

The decompiler applies IR transforms to improve output quality. Choose a preset, or add
transforms manually. `TransformPreset`:

| Preset | Description | Transforms |
|--------|-------------|------------|
| `NONE` | No additional transforms (baseline only) | - |
| `MINIMAL` | Safe, minimal cleanup | ConstantFolding, CopyPropagation |
| `STANDARD` | Balanced optimization | Above + AlgebraicSimplification, RedundantCopyElimination, DeadCodeElimination |
| `AGGRESSIVE` | Maximum simplification | Above + StrengthReduction, Reassociate, CommonSubexpressionElimination, PhiConstantPropagation |

```java
ClassDecompiler decompiler = ClassDecompiler.builder(cf)
    .config(emitterConfig)
    .preset(TransformPreset.AGGRESSIVE)
    .build();

String source = decompiler.decompile();
```

**Manual configuration** - add specific transforms, optionally on top of a preset:

```java
import com.tonic.analysis.ssa.transform.*;

ClassDecompiler decompiler = ClassDecompiler.builder(cf)
    .config(emitterConfig)
    .preset(TransformPreset.MINIMAL)
    .addTransform(new NullCheckElimination())
    .addTransform(new AlgebraicSimplification())
    .build();
```

**Reusable config** - build a `DecompilerConfig` once and share it across class files:

```java
DecompilerConfig decompilerConfig = DecompilerConfig.builder()
    .emitterConfig(emitterConfig)
    .preset(TransformPreset.STANDARD)
    .addTransform(new LoopInvariantCodeMotion())
    .build();

ClassDecompiler d1 = new ClassDecompiler(cf1, decompilerConfig);
ClassDecompiler d2 = new ClassDecompiler(cf2, decompilerConfig);
```

**Baseline transforms** always run first, regardless of preset:
- `ControlFlowReducibility` - converts irreducible control flow to reducible form
- `DuplicateBlockMerging` - merges duplicate blocks created by reducibility

Preset and manual transforms run after these. The available passes are described in
[SSA Transforms](ssa-transforms.md).

## Line Maps and Source Spans

`decompileWithLineMap()` returns a `DecompileResult` that carries the source **and** provenance
back to the bytecode, useful for debuggers, source-mapped tooling, and slicing.

```java
DecompileResult result = new ClassDecompiler(cf).decompileWithLineMap();
String source = result.getSource();
```

**Line maps** - per method (keyed by name + descriptor), a `NavigableMap<Integer, Integer>` from
bytecode offset to the 1-based output line of the statement recovered from that offset. Resolve an
arbitrary PC with `ceilingEntry`/`floorEntry` - inlined expressions live in a later-offset consumer
statement, so prefer `ceiling`. Statements whose provenance did not survive recovery/transforms
simply have no entry.

```java
NavigableMap<Integer, Integer> lineMap = result.getLineMap("doWork", "(I)V");
int line = lineMap.ceilingEntry(callSitePc).getValue();
```

An inlined lambda body's statements are keyed under the lambda's own synthetic impl method (e.g.
`"lambda$doWork$0"` + descriptor), not the enclosing method, since they carry that method's
offsets - so a PC inside a lambda resolves via `getLineMap("lambda$doWork$0", "()V")`.

**Source spans** - every emitted member gets its exact text span (annotations + signature through
the closing brace), for slicing a declaration out of the class source without parsing.

```java
DecompileResult.MethodSpan span = result.getMethodSpan("doWork", "(I)V");
String[] lines = result.getSource().split("\n");
String methodText = String.join("\n",
        java.util.Arrays.asList(lines).subList(span.getStartLine() - 1, span.getEndLine()));

// Fields and the class declaration carry the same spans (MethodSpan is a MemberSpan subtype).
DecompileResult.MemberSpan fieldSpan = result.getFieldSpan("count", "I");
DecompileResult.MemberSpan classSpan = result.getClassSpan();
```

Field spans are keyed by name + descriptor; the class span covers its annotations through the
`... {` signature line.

## Import Generation

When `useFullyQualifiedNames` is `false` (the default), the decompiler generates imports by:

1. Scanning the constant pool for all referenced class types
2. Filtering out `java.lang.*` classes (implicitly imported in Java)
3. Filtering out classes in the same package as the decompiled class
4. Sorting imports alphabetically

Subpackages of `java.lang` (like `java.lang.invoke.*` or `java.lang.reflect.*`) are NOT implicitly
imported and will be included.

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

## Output Structure

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

## Handling Decompilation Failures

If a method body fails to decompile, the decompiler inserts a comment instead of crashing, so a
class with a few unsupported methods still produces usable output:

```java
public void problematicMethod() {
    // Failed to decompile: <error message>
}
```

## Runnable Demos

- [`examples/.../demo/ast/Decompile.java`](../examples/src/main/java/com/tonic/demo/ast/Decompile.java) - a CLI that decompiles a single `.class`, with `--fqn` and `--preset=none|minimal|standard|aggressive` flags:

  ```bash
  java -cp examples/build/classes/java/main com.tonic.demo.ast.Decompile MyClass.class --preset=standard
  ```

- [`examples/.../demo/JarDecompiler.java`](../examples/src/main/java/com/tonic/demo/JarDecompiler.java) - walks a jar and decompiles every class.

---

## Related

- [AST Guide](ast-guide.md) - the recovery/mutation/emission layer the decompiler is built on
- [SSA Transforms](ssa-transforms.md) - the transform passes referenced by the presets
- [Class Files](class-files.md) - loading `ClassFile` instances to decompile

---

[<- Back to README](../README.md) | [AST Guide](ast-guide.md) | [AST Editor ->](ast-editor.md)
