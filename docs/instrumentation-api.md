[<- Back to Analysis APIs](analysis-apis.md)

# Instrumentation API

The Instrumentation API (`com.tonic.analysis.instrumentation`) provides a fluent interface for adding hooks to bytecode at various instrumentation points.

---

## Basic Usage

```java
import com.tonic.analysis.instrumentation.Instrumenter;
import com.tonic.parser.ClassFile;

ClassFile classFile = new ClassFile(inputStream);

int points = Instrumenter.forClass(classFile)
    .onMethodEntry()
        .inPackage("com/example/service/")
        .callStatic("com/example/Hooks", "onEntry", "(Ljava/lang/String;)V")
        .withMethodName()
        .register()
    .apply();

System.out.println("Applied " + points + " instrumentation points");
```

---

## Method Entry/Exit Hooks

```java
Instrumenter.forClass(classFile)
    // Hook all method entries
    .onMethodEntry()
        .callStatic("com/example/Profiler", "onMethodEntry",
            "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V")
        .withClassName()
        .withMethodName()
        .withAllParameters()  // Pass params as Object[]
        .register()

    // Hook all method exits
    .onMethodExit()
        .callStatic("com/example/Profiler", "onMethodExit",
            "(Ljava/lang/String;Ljava/lang/Object;)V")
        .withMethodName()
        .withReturnValue()    // Boxed if primitive
        .register()

    .skipConstructors(true)
    .apply();
```

---

## Field Write Hooks

```java
Instrumenter.forClass(classFile)
    .onFieldWrite()
        .forField("balance")  // Or use pattern: "*Id"
        .callStatic("com/example/AuditLog", "onFieldChange",
            "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V")
        .withOwner()          // Object owning the field
        .withFieldName()      // Field name as String
        .withOldValue()       // Value before write
        .withNewValue()       // Value being written
        .register()
    .apply();
```

---

## Array Store Hooks

```java
Instrumenter.forClass(classFile)
    .onArrayStore()
        .inPackage("com/example/sensitive/")
        .forObjectArrays()    // Only Object[] arrays
        .callStatic("com/example/Security", "validateArrayStore",
            "(Ljava/lang/Object;ILjava/lang/Object;)V")
        .withArray()          // Array reference
        .withIndex()          // Index being written
        .withValue()          // Value being stored
        .register()
    .apply();
```

---

## Method Call Interception

```java
Instrumenter.forClass(classFile)
    // Hook calls to specific methods
    .onMethodCall()
        .targeting("java/sql/Connection", "prepareStatement")
        .before()
        .callStatic("com/example/DBHooks", "beforeQuery",
            "(Ljava/lang/Object;[Ljava/lang/Object;)V")
        .withReceiver()       // The Connection object
        .withArguments()      // Arguments as Object[]
        .register()

    // Hook after calls
    .onMethodCall()
        .targeting("java/io/PrintStream", "println")
        .after()
        .callStatic("com/example/IOHooks", "afterPrint",
            "(Ljava/lang/Object;)V")
        .withResult()         // Return value
        .register()
    .apply();
```

---

## Filtering by Annotation

```java
Instrumenter.forClass(classFile)
    .onMethodEntry()
        .withAnnotation("Ljavax/inject/Inject;")  // Only @Inject methods
        .callStatic("com/example/DIHooks", "onInject", "()V")
        .register()
    .apply();
```

---

## Instrumentation Report

```java
Instrumenter.InstrumentationReport report = Instrumenter.forClass(classFile)
    .onMethodEntry()
        .callStatic("Profiler", "enter", "(Ljava/lang/String;)V")
        .withMethodName()
        .register()
    .applyWithReport();

System.out.println("Classes: " + report.getClassesInstrumented());
System.out.println("Methods: " + report.getMethodsInstrumented());
System.out.println("Points:  " + report.getTotalInstrumentationPoints());
System.out.println("Errors:  " + report.getErrors());
```

---

## Configuration Options

| Option | Description |
|--------|-------------|
| `skipAbstract(true)` | Skip abstract methods (default: true) |
| `skipNative(true)` | Skip native methods (default: true) |
| `skipConstructors(true)` | Skip `<init>` methods |
| `skipStaticInitializers(true)` | Skip `<clinit>` methods |
| `skipSynthetic(true)` | Skip synthetic methods |
| `skipBridge(true)` | Skip bridge methods |
| `failOnError(true)` | Throw on instrumentation errors |
| `verbose(true)` | Log instrumentation progress |

---

## Hook Types

| Hook | Description | Builder |
|------|-------------|---------|
| Method Entry | At method start | `onMethodEntry()` |
| Method Exit | Before return | `onMethodExit()` |
| Field Write | Before PUTFIELD/PUTSTATIC | `onFieldWrite()` |
| Field Read | After GETFIELD/GETSTATIC | `onFieldRead()` |
| Array Store | Before AASTORE/IASTORE/etc. | `onArrayStore()` |
| Array Load | After AALOAD/IALOAD/etc. | `onArrayLoad()` |
| Method Call | Before/after INVOKEVIRTUAL/etc. | `onMethodCall()` |
| Exception | At exception handler entry | `onException()` |

---

## Filter Types

| Filter | Description |
|--------|-------------|
| `inClass(className)` | Exact class match |
| `inClassMatching(pattern)` | Wildcard class match |
| `inPackage(prefix)` | Package prefix match |
| `matchingMethod(pattern)` | Method name pattern |
| `forField(name)` | Field name match |
| `forFieldsMatching(pattern)` | Field name pattern |
| `ofType(descriptor)` | Field type filter |
| `withAnnotation(type)` | Annotation presence |
| `staticOnly()` | Static members only |
| `instanceOnly()` | Instance members only |

---

## Typical Use Cases

1. **Profiling** - Method entry/exit timing
2. **Logging** - Trace method calls
3. **Security** - Validate field writes
4. **Mocking** - Intercept external calls
5. **Coverage** - Track code execution
6. **Auditing** - Record data changes

---

[<- Back to Analysis APIs](analysis-apis.md)
