[<- Back to README](../README.md) | [AST Editor](ast-editor.md) | [AST Guide ->](ast-guide.md)

# Renamer API

The Renamer API provides a high-level, type-safe interface for renaming classes, methods, and fields across an entire ClassPool. It handles all the complexity of updating constant pool references, descriptor remapping, hierarchy-aware method renaming, and validation.

## What is the Renamer API?

The Renamer API addresses the challenges of renaming in Java bytecode:

- **Reference Updates** - All constant pool references across all classes are automatically updated
- **Hierarchy Awareness** - Method renames can propagate through inheritance hierarchies
- **Descriptor Remapping** - Method and field descriptors containing renamed classes are updated
- **Signature Handling** - Generic signatures are properly remapped
- **Validation** - Pre-application validation catches conflicts and errors
- **InvokeDynamic Support** - Bootstrap method references are updated

## Quick Start

```java
import com.tonic.parser.ClassPool;
import com.tonic.renamer.Renamer;

// Load classes
ClassPool pool = ClassPool.getDefault();
pool.loadClass(inputStream1);
pool.loadClass(inputStream2);

// Create renamer and define mappings
Renamer renamer = new Renamer(pool);
renamer.mapClass("com/old/MyClass", "com/new/RenamedClass")
       .mapMethodInHierarchy("com/old/Service", "process", "(I)V", "handle")
       .mapField("com/old/Model", "data", "Ljava/lang/String;", "content")
       .apply();

// Export modified classes
for (ClassFile cf : pool.getClasses()) {
    cf.rebuild();
    byte[] bytes = cf.write();
    // Save bytes...
}
```

## Core API

### Renamer

The main entry point for all renaming operations:

```java
import com.tonic.renamer.Renamer;

Renamer renamer = new Renamer(classPool);
```

#### Class Renaming

```java
// Rename a class (updates all references across the pool)
renamer.mapClass("com/example/OldName", "com/example/NewName");

// Multiple class renames
renamer.mapClass("com/old/ClassA", "com/new/ClassA")
       .mapClass("com/old/ClassB", "com/new/ClassB")
       .mapClass("com/old/ClassC", "com/new/ClassC");
```

When a class is renamed:
- The class's own name is updated
- All `CONSTANT_Class` references are updated
- Method and field descriptors containing the class are remapped
- Generic signatures are updated
- InvokeDynamic bootstrap methods are updated

#### Method Renaming

```java
// Single class only - no hierarchy propagation
renamer.mapMethod("com/example/MyClass", "oldMethod", "(I)V", "newMethod");

// With hierarchy propagation - renames overrides too
renamer.mapMethodInHierarchy("com/example/Base", "process", "(I)V", "handle");
```

**When to use `mapMethod` vs `mapMethodInHierarchy`:**

| Method | Use Case |
|--------|----------|
| `mapMethod` | Private methods, static methods, final methods |
| `mapMethodInHierarchy` | Virtual methods that may be overridden |

Example hierarchy:
```java
interface Service {
    void process(int x);  // Original
}

class BaseService implements Service {
    @Override
    public void process(int x) { ... }
}

class ChildService extends BaseService {
    @Override
    public void process(int x) { ... }
}
```

Using `mapMethodInHierarchy("com/example/Service", "process", "(I)V", "handle")` will rename `process` to `handle` in:
- `Service` interface
- `BaseService` class
- `ChildService` class

#### Field Renaming

```java
// Rename a field
renamer.mapField("com/example/MyClass", "oldField", "Ljava/lang/String;", "newField");

// The descriptor is required to distinguish overloaded fields (rare but possible)
renamer.mapField("com/example/Data", "value", "I", "intValue");
renamer.mapField("com/example/Data", "value", "D", "doubleValue");
```

### Validation

The Renamer validates all mappings before applying them:

```java
import com.tonic.renamer.validation.ValidationResult;

// Validate without applying
ValidationResult result = renamer.validate();

if (!result.isValid()) {
    System.out.println("Errors:");
    for (String error : result.getErrors()) {
        System.out.println("  - " + error);
    }
}

if (result.hasWarnings()) {
    System.out.println("Warnings:");
    for (String warning : result.getWarnings()) {
        System.out.println("  - " + warning);
    }
}

// apply() validates automatically and throws RenameException on failure
try {
    renamer.apply();
} catch (RenameException e) {
    System.err.println("Rename failed: " + e.getMessage());
}

// Or skip validation (use with caution)
renamer.applyUnsafe();
```

**Validation checks:**

| Check | Description |
|-------|-------------|
| Class exists | Source class must exist in the pool |
| Method exists | Method with exact name and descriptor must exist |
| Field exists | Field with exact name must exist |
| Valid identifiers | New names must be valid Java identifiers |
| No conflicts | New names must not conflict with existing members |
| No circular renames | Detects A->B, B->A patterns |
| No chained renames | Warns about A->B->C patterns |

### Class Hierarchy

The Renamer builds a class hierarchy for method resolution:

```java
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassNode;

// Get the hierarchy
ClassHierarchy hierarchy = renamer.getHierarchy();

// Query inheritance
ClassNode node = hierarchy.getNode("com/example/MyClass");
ClassNode superClass = node.getSuperClass();
Set<ClassNode> interfaces = node.getInterfaces();
Set<ClassNode> ancestors = node.getAllAncestors();
Set<ClassNode> descendants = node.getAllDescendants();

// Check relationships
boolean isAncestor = hierarchy.isAncestorOf("java/lang/Object", "com/example/MyClass");

// Find all methods in a hierarchy
Set<ClassNode> methodClasses = hierarchy.findMethodHierarchy(
    "com/example/Base", "process", "(I)V");
```

### Finding Overrides

Query which methods would be affected by a hierarchy rename:

```java
import com.tonic.parser.MethodEntry;

Set<MethodEntry> overrides = renamer.findOverrides(
    "com/example/Service", "process", "(I)V");

for (MethodEntry method : overrides) {
    System.out.println(method.getOwner().getClassName() + "." + method.getName());
}
```

## Package Structure

```
com.tonic.renamer/
├── Renamer.java                    # Main API entry point
├── RenamerContext.java             # Internal context for operations
├── ClassRenamer.java               # Handles class renaming
├── MethodRenamer.java              # Handles method renaming
├── FieldRenamer.java               # Handles field renaming
├── mapping/
│   ├── MappingStore.java           # Stores all mappings
│   ├── RenameMapping.java          # Base mapping interface
│   ├── ClassMapping.java           # Class name mapping
│   ├── MethodMapping.java          # Method name mapping
│   └── FieldMapping.java           # Field name mapping
├── descriptor/
│   ├── DescriptorRemapper.java     # Remaps type descriptors
│   └── SignatureRemapper.java      # Remaps generic signatures
├── hierarchy/
│   ├── ClassHierarchy.java         # Complete class hierarchy
│   ├── ClassHierarchyBuilder.java  # Builds hierarchy from pool
│   └── ClassNode.java              # Node in hierarchy graph
├── validation/
│   ├── RenameValidator.java        # Validates mappings
│   └── ValidationResult.java       # Validation results
└── exception/
    ├── RenameException.java        # General rename error
    ├── NameConflictException.java  # Name already exists
    └── InvalidNameException.java   # Invalid identifier
```

## Descriptor Remapping

When classes are renamed, all descriptors containing those classes are automatically remapped:

```java
// If com/old/MyClass is renamed to com/new/RenamedClass:

// Field descriptor
"Lcom/old/MyClass;" -> "Lcom/new/RenamedClass;"

// Method descriptor
"(Lcom/old/MyClass;I)Lcom/old/MyClass;" -> "(Lcom/new/RenamedClass;I)Lcom/new/RenamedClass;"

// Array types
"[Lcom/old/MyClass;" -> "[Lcom/new/RenamedClass;"
```

### Signature Remapping

Generic signatures are also remapped:

```java
// Generic class signature
"<T:Lcom/old/MyClass;>Ljava/lang/Object;"
    -> "<T:Lcom/new/RenamedClass;>Ljava/lang/Object;"

// Method signature with type parameters
"<T:Ljava/lang/Object;>(Lcom/old/MyClass;TT;)TT;"
    -> "<T:Ljava/lang/Object;>(Lcom/new/RenamedClass;TT;)TT;"
```

## Example: Obfuscation Reversal

A common use case is reversing obfuscation using a mapping file:

```java
public class DeobfuscationExample {

    public static void main(String[] args) throws Exception {
        // Load obfuscated classes
        ClassPool pool = ClassPool.getDefault();
        loadClassesFromJar(pool, "obfuscated.jar");

        // Create renamer
        Renamer renamer = new Renamer(pool);

        // Load mappings (e.g., from ProGuard mapping file)
        Map<String, String> classMappings = loadMappings("mapping.txt");

        for (Map.Entry<String, String> entry : classMappings.entrySet()) {
            String obfuscated = entry.getKey();
            String original = entry.getValue();
            renamer.mapClass(obfuscated, original);
        }

        // Apply all renames
        renamer.apply();

        // Export deobfuscated classes
        for (ClassFile cf : pool.getClasses()) {
            cf.rebuild();
            saveClass(cf, "deobfuscated/");
        }
    }
}
```

## Example: API Migration

Rename deprecated APIs across a codebase:

```java
public class ApiMigrationExample {

    public static void main(String[] args) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        loadProject(pool, "src/");

        Renamer renamer = new Renamer(pool);

        // Rename deprecated class
        renamer.mapClass("com/example/OldService", "com/example/NewService");

        // Rename deprecated methods (with hierarchy support)
        renamer.mapMethodInHierarchy(
            "com/example/OldService", "doWork", "()V", "execute");
        renamer.mapMethodInHierarchy(
            "com/example/OldService", "getData", "()Ljava/lang/Object;", "fetch");

        // Rename deprecated fields
        renamer.mapField(
            "com/example/OldService", "TIMEOUT", "I", "DEFAULT_TIMEOUT");

        // Validate before applying
        ValidationResult result = renamer.validate();
        if (!result.isValid()) {
            System.err.println(result.getReport());
            return;
        }

        // Apply changes
        renamer.apply();

        // Export
        exportProject(pool, "migrated/");
    }
}
```

## Error Handling

### RenameException

Thrown when renaming fails:

```java
try {
    renamer.apply();
} catch (RenameException e) {
    // Contains validation report or error details
    System.err.println(e.getMessage());
}
```

### NameConflictException

Thrown when a name already exists:

```java
// This would throw NameConflictException if "newField" already exists
renamer.mapField("com/example/MyClass", "oldField", "I", "newField");
```

### InvalidNameException

Thrown for invalid Java identifiers:

```java
// This would throw InvalidNameException
renamer.mapClass("com/example/MyClass", "com/example/123Invalid");
```

## Best Practices

1. **Validate first** - Always call `validate()` before `apply()` in production code
2. **Use hierarchy methods** - Use `mapMethodInHierarchy` for any non-private, non-static method
3. **Batch operations** - Define all mappings before calling `apply()` for efficiency
4. **Preserve originals** - Keep backup copies of classes before renaming
5. **Handle warnings** - Review warnings even if validation passes

## Limitations

- **External classes** - Cannot rename classes outside the ClassPool (e.g., JDK classes)
- **Reflection** - String-based reflection calls are not updated (e.g., `Class.forName("...")`)
- **Resources** - Non-class resources referencing class names are not updated
- **Annotations** - String values in annotations are not updated

## Related Documentation

- [AST Editor](ast-editor.md) - Source-level expression/statement editing
- [AST Guide](ast-guide.md) - Source AST recovery and emission
- [Architecture](architecture.md) - System overview

---

[<- Back to README](../README.md) | [AST Editor](ast-editor.md) | [AST Guide ->](ast-guide.md)
