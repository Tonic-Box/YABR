[<- Back to README](../README.md) | [Architecture](architecture.md) | [Bytecode API ->](bytecode-api.md)

# Working with Class Files

This guide covers the core parsing classes: ClassPool, ClassFile, ConstPool, MethodEntry, and FieldEntry.

## ClassPool

ClassPool is a container for ClassFile objects. It automatically loads all `java.base` classes on initialization.

### Getting the Default Pool

```java
ClassPool classPool = ClassPool.getDefault();
```

### Loading Classes

```java
// From InputStream
try (InputStream is = new FileInputStream("MyClass.class")) {
    ClassFile cf = classPool.loadClass(is);
}

// From byte array
byte[] classBytes = Files.readAllBytes(Path.of("MyClass.class"));
ClassFile cf = classPool.loadClass(classBytes);

// From system classloader
ClassFile cf = classPool.loadSystemClass("com/example/MyClass.class");

// Load entire JAR
try (JarFile jar = new JarFile("mylib.jar")) {
    classPool.loadJar(jar);
}
```

### Creating New Classes

```java
int access = new AccessBuilder()
    .setPublic()
    .build();

ClassFile cf = classPool.createNewClass("com/example/NewClass", access);
```

### Retrieving Classes

```java
// Get by internal name
ClassFile cf = classPool.get("java/lang/String");

// Add a class
classPool.put(classFile);
```

## ClassFile

ClassFile represents a complete Java class file.

### Basic Properties

```java
ClassFile cf = classPool.loadClass(inputStream);

// Class info
String className = cf.getClassName();        // "com/example/MyClass"
String superName = cf.getSuperClassName();   // "java/lang/Object"
int version = cf.getMajorVersion();          // 55 for Java 11
int access = cf.getAccess();                 // access flags

// Modify
cf.setClassName("com/example/Renamed");
cf.setSuperClassName("com/example/BaseClass");
```

### Interfaces

```java
// Get interfaces
List<Integer> interfaces = cf.getInterfaces();

// Add interface
cf.addInterface("java/io/Serializable");
```

### Fields and Methods

```java
// Access collections
List<FieldEntry> fields = cf.getFields();
List<MethodEntry> methods = cf.getMethods();

// Find specific members
MethodEntry main = methods.stream()
    .filter(m -> m.getName().equals("main"))
    .findFirst()
    .orElse(null);
```

### Writing Classes

```java
// Rebuild internal structures
cf.rebuild();

// Get bytes
byte[] classBytes = cf.write();

// Save to file
Files.write(Path.of("Output.class"), classBytes);
```

## ConstPool

The constant pool holds all constants used by the class.

### Getting Items

```java
ConstPool cp = cf.getConstPool();

// Get item by index
Item<?> item = cp.getItem(index);

// Get index of item
int idx = cp.getIndexOf(item);
```

### Finding or Adding Items

```java
// UTF8 strings
Utf8Item utf8 = cp.findOrAddUtf8("myString");

// Class references
ClassRefItem classRef = cp.findOrAddClassRef(utf8Index);
ClassRefItem classRef = cp.findOrAddClass("java/lang/String");

// Field references
FieldRefItem fieldRef = cp.findOrAddField("com/example/MyClass", "myField", "I");

// Method references
MethodRefItem methodRef = cp.findOrAddMethodRef(classIndex, nameAndTypeIndex);

// Name and type
NameAndTypeRefItem nat = cp.findOrAddNameAndType(nameIndex, descIndex);

// Primitives
IntegerItem intItem = cp.findOrAddInteger(42);
LongItem longItem = cp.findOrAddLong(100L);
FloatItem floatItem = cp.findOrAddFloat(3.14f);
DoubleItem doubleItem = cp.findOrAddDouble(2.718);

// Strings
StringRefItem stringRef = cp.findOrAddString("Hello");
```

## FieldEntry

Fields store data in a class.

### Creating Fields

```java
int fieldAccess = new AccessBuilder()
    .setPrivate()
    .build();

FieldEntry field = cf.createNewField(
    fieldAccess,
    "counter",
    "I",              // int
    new ArrayList<>() // attributes
);
```

### Field Descriptors

| Descriptor | Type |
|------------|------|
| `B` | byte |
| `C` | char |
| `D` | double |
| `F` | float |
| `I` | int |
| `J` | long |
| `S` | short |
| `Z` | boolean |
| `Ljava/lang/String;` | Object type |
| `[I` | int array |
| `[[Ljava/lang/String;` | 2D String array |

### Setting Initial Values

```java
// Primitives
cf.setFieldInitialValue(field, 42);          // int
cf.setFieldInitialValue(field, 100L);        // long
cf.setFieldInitialValue(field, 3.14f);       // float
cf.setFieldInitialValue(field, 2.718);       // double
cf.setFieldInitialValue(field, "Hello");     // String
```

### Generating Accessors

```java
// Instance field
cf.generateGetter(field, false);  // getCounter()
cf.generateSetter(field, false);  // setCounter(int)

// Static field
cf.generateGetter(staticField, true);  // getStaticField()
cf.generateSetter(staticField, true);  // setStaticField(int)

// Custom names
cf.generateGetter(field, "readValue", false);
cf.generateSetter(field, "writeValue", false);
```

### Removing Fields

```java
boolean removed = cf.removeField("fieldName", "I");
```

## MethodEntry

Methods define behavior in a class.

### Creating Methods

```java
// With Class types
MethodEntry method = cf.createNewMethod(
    access,
    "add",
    int.class,       // return type
    int.class,       // param 1
    int.class        // param 2
);

// With descriptor strings
MethodEntry method = cf.createNewMethod(
    access,
    "process",
    "V",             // void return
    "Ljava/lang/String;"  // String param
);
```

### Method Properties

```java
String name = method.getName();          // "myMethod"
String desc = method.getDesc();          // "(II)I"
int access = method.getAccess();         // access flags
String owner = method.getOwnerName();    // "com/example/MyClass"

// Check modifiers
boolean isStatic = Modifiers.isStatic(access);
boolean isPublic = Modifiers.isPublic(access);
```

### CodeAttribute

```java
CodeAttribute code = method.getCodeAttribute();

if (code != null) {
    int maxStack = code.getMaxStack();
    int maxLocals = code.getMaxLocals();
    byte[] bytecode = code.getCode();

    // Pretty print
    System.out.println(code.prettyPrintCode());
}
```

## AccessBuilder

Fluent API for constructing access flags:

```java
int access = new AccessBuilder()
    .setPublic()
    .setStatic()
    .setFinal()
    .build();

// Available modifiers:
// .setPublic(), .setPrivate(), .setProtected()
// .setStatic(), .setFinal(), .setAbstract()
// .setSynchronized(), .setNative(), .setStrict()
```

## Frame Computation

For Java 7+ classes, compute StackMapTable frames after modifying bytecode:

```java
// Compute frames for all methods
int count = cf.computeFrames();

// Compute for specific method
cf.computeFrames("myMethod", "(II)I");

// Compute for a method entry
cf.computeFrames(methodEntry);
```

## Complete Example

```java
ClassPool pool = ClassPool.getDefault();

// Create class
int classAccess = new AccessBuilder().setPublic().build();
ClassFile cf = pool.createNewClass("com/example/Counter", classAccess);

// Add field
int fieldAccess = new AccessBuilder().setPrivate().build();
FieldEntry countField = cf.createNewField(fieldAccess, "count", "I", new ArrayList<>());
cf.setFieldInitialValue(countField, 0);

// Generate getter/setter
cf.generateGetter(countField, false);
cf.generateSetter(countField, false);

// Add increment method
int methodAccess = new AccessBuilder().setPublic().build();
MethodEntry increment = cf.createNewMethod(methodAccess, "increment", "V");

Bytecode bc = new Bytecode(increment);
bc.addALoad(0);  // this
bc.addALoad(0);  // this (for getfield)
int fieldRef = cf.getConstPool().getIndexOf(
    cf.getConstPool().findOrAddField("com/example/Counter", "count", "I")
);
bc.addGetField(fieldRef);
bc.addIConst(1);
// IADD would be added here
bc.addPutField(fieldRef);
bc.addReturn(ReturnType.RETURN);
bc.finalizeBytecode();

// Build and write
cf.rebuild();
byte[] bytes = cf.write();
Files.write(Path.of("Counter.class"), bytes);
```

---

[<- Back to README](../README.md) | [Architecture](architecture.md) | [Bytecode API ->](bytecode-api.md)
