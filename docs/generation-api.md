[<- Back to README](../README.md) | [Bytecode API](bytecode-api.md) | [Frame Computation ->](frame-computation.md)

# Bytecode Generation API

The Generation API (`com.tonic.builder`) provides a fluent builder pattern for creating JVM bytecode from scratch.

---

## Basic Usage

```java
import com.tonic.builder.ClassBuilder;
import com.tonic.type.AccessFlags;

byte[] bytes = ClassBuilder.create("com/example/HelloWorld")
    .version(AccessFlags.V11, 0)
    .access(AccessFlags.ACC_PUBLIC)

    .addMethod(AccessFlags.ACC_PUBLIC, "<init>", "()V")
    .code()
        .aload(0)
        .invokespecial("java/lang/Object", "<init>", "()V")
        .vreturn()
    .end()
    .end()

    .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "main", "([Ljava/lang/String;)V")
    .code()
        .getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
        .ldc("Hello, World!")
        .invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
        .vreturn()
    .end()
    .end()

    .toByteArray();
```

---

## ClassBuilder

The entry point for creating new classes.

```java
ClassFile cf = ClassBuilder.create("com/example/MyClass")
    .version(AccessFlags.V11, 0)           // Java 11
    .access(AccessFlags.ACC_PUBLIC)        // public class
    .superClass("java/util/ArrayList")     // extends ArrayList
    .interfaces("java/io/Serializable")    // implements Serializable
    .build();
```

### ClassBuilder Methods

| Method | Description |
|--------|-------------|
| `create(String className)` | Create builder with internal class name |
| `version(int major, int minor)` | Set class file version (default: Java 11) |
| `access(int... flags)` | Set access flags (ACC_SUPER added automatically) |
| `superClass(String name)` | Set superclass (default: java/lang/Object) |
| `interfaces(String... names)` | Add implemented interfaces |
| `addField(int access, String name, String desc)` | Add a field, returns FieldBuilder |
| `addMethod(int access, String name, String desc)` | Add a method, returns MethodBuilder |
| `build()` | Build and return ClassFile |
| `toByteArray()` | Build and serialize to byte array |

---

## FieldBuilder

Configure fields after creation.

```java
ClassBuilder.create("com/example/Counter")
    .addField(AccessFlags.ACC_PRIVATE, "count", "I")
    .end()

    .addField(AccessFlags.ACC_PRIVATE | AccessFlags.ACC_STATIC, "instances", "I")
    .synthetic()
    .end()

    .addField(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC | AccessFlags.ACC_FINAL, "MAX", "I")
    .constantValue(100)
    .end()

    .build();
```

### FieldBuilder Methods

| Method | Description |
|--------|-------------|
| `constantValue(Object value)` | Set ConstantValue attribute |
| `synthetic()` | Mark field as synthetic |
| `deprecated()` | Mark field as deprecated |
| `end()` | Return to ClassBuilder |

---

## MethodBuilder

Configure methods and access the CodeBuilder.

```java
ClassBuilder.create("com/example/Calculator")
    .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "add", "(II)I")
    .maxStack(2)
    .maxLocals(2)
    .exceptions("java/lang/ArithmeticException")
    .code()
        .iload(0)
        .iload(1)
        .iadd()
        .ireturn()
    .end()
    .end()
    .build();
```

### MethodBuilder Methods

| Method | Description |
|--------|-------------|
| `code()` | Access CodeBuilder for bytecode instructions |
| `exceptions(String... types)` | Add throws clause |
| `maxStack(int value)` | Set max stack depth |
| `maxLocals(int value)` | Set max local variables |
| `end()` | Return to ClassBuilder |

---

## CodeBuilder

The fluent bytecode instruction builder with string-based labels.

### Load and Store Instructions

```java
.code()
    .iload(0)        // Load int from local 0
    .lload(2)        // Load long from local 2
    .fload(4)        // Load float from local 4
    .dload(5)        // Load double from local 5
    .aload(7)        // Load reference from local 7

    .istore(1)       // Store int to local 1
    .lstore(3)       // Store long to local 3
    .fstore(5)       // Store float to local 5
    .dstore(6)       // Store double to local 6
    .astore(8)       // Store reference to local 8
```

### Constant Instructions

```java
.code()
    .iconst(5)       // Push int constant (handles -1 to 5 optimally)
    .lconst(100L)    // Push long constant
    .fconst(3.14f)   // Push float constant
    .dconst(2.718)   // Push double constant
    .aconst_null()   // Push null

    .bipush(100)     // Push byte as int
    .sipush(1000)    // Push short as int
    .ldc("Hello")    // Load from constant pool (String)
    .ldc(12345)      // Load from constant pool (Integer)
```

### Arithmetic Instructions

```java
.code()
    // Integer arithmetic
    .iload(0).iload(1).iadd()    // a + b
    .iload(0).iload(1).isub()    // a - b
    .iload(0).iload(1).imul()    // a * b
    .iload(0).iload(1).idiv()    // a / b
    .iload(0).iload(1).irem()    // a % b
    .iload(0).ineg()             // -a

    // Long arithmetic: ladd, lsub, lmul, ldiv, lrem, lneg
    // Float arithmetic: fadd, fsub, fmul, fdiv, frem, fneg
    // Double arithmetic: dadd, dsub, dmul, ddiv, drem, dneg
```

### Bitwise Instructions

```java
.code()
    .iload(0).iload(1).iand()    // a & b
    .iload(0).iload(1).ior()     // a | b
    .iload(0).iload(1).ixor()    // a ^ b
    .iload(0).iload(1).ishl()    // a << b
    .iload(0).iload(1).ishr()    // a >> b
    .iload(0).iload(1).iushr()   // a >>> b

    // Long variants: land, lor, lxor, lshl, lshr, lushr
```

### Type Conversion

```java
.code()
    .iload(0).i2l()    // int to long
    .iload(0).i2f()    // int to float
    .iload(0).i2d()    // int to double
    .lload(0).l2i()    // long to int
    .fload(0).f2i()    // float to int
    .dload(0).d2l()    // double to long
    .iload(0).i2b()    // int to byte
    .iload(0).i2c()    // int to char
    .iload(0).i2s()    // int to short
```

### Stack Manipulation

```java
.code()
    .pop()             // Pop top value
    .pop2()            // Pop top two values (or one long/double)
    .dup()             // Duplicate top value
    .dup_x1()          // Duplicate and insert below second
    .dup_x2()          // Duplicate and insert below third
    .dup2()            // Duplicate top two values
    .swap()            // Swap top two values
```

### Control Flow with Labels

```java
.code()
    .iload(0)
    .iload(1)
    .if_icmpge("first_greater")   // if (a >= b) goto first_greater
    .iload(1)
    .ireturn()
    .label("first_greater")
    .iload(0)
    .ireturn()
```

### Branch Instructions

| Instruction | Description |
|-------------|-------------|
| `label(String name)` | Define a label |
| `goto_(String label)` | Unconditional jump |
| `ifeq(String label)` | Jump if == 0 |
| `ifne(String label)` | Jump if != 0 |
| `iflt(String label)` | Jump if < 0 |
| `ifge(String label)` | Jump if >= 0 |
| `ifgt(String label)` | Jump if > 0 |
| `ifle(String label)` | Jump if <= 0 |
| `if_icmpeq(String label)` | Jump if ints equal |
| `if_icmpne(String label)` | Jump if ints not equal |
| `if_icmplt(String label)` | Jump if int < int |
| `if_icmpge(String label)` | Jump if int >= int |
| `if_icmpgt(String label)` | Jump if int > int |
| `if_icmple(String label)` | Jump if int <= int |
| `if_acmpeq(String label)` | Jump if refs equal |
| `if_acmpne(String label)` | Jump if refs not equal |
| `ifnull(String label)` | Jump if null |
| `ifnonnull(String label)` | Jump if not null |

### Loops

```java
.code()
    .iconst(0)
    .istore(1)              // result = 0
    .iconst(0)
    .istore(2)              // i = 0
    .label("loop")
    .iload(2)
    .iload(0)
    .if_icmpge("end")       // if (i >= n) goto end
    .iload(1)
    .iload(2)
    .iadd()
    .istore(1)              // result += i
    .iinc(2, 1)             // i++
    .goto_("loop")
    .label("end")
    .iload(1)
    .ireturn()
```

### Method Invocation

```java
.code()
    // Virtual method call
    .aload(0)
    .invokevirtual("java/lang/Object", "hashCode", "()I")

    // Static method call
    .iload(0)
    .iload(1)
    .invokestatic("java/lang/Math", "max", "(II)I")

    // Constructor call
    .new_("java/lang/StringBuilder")
    .dup()
    .invokespecial("java/lang/StringBuilder", "<init>", "()V")

    // Interface method call
    .aload(0)
    .invokeinterface("java/util/List", "size", "()I")
```

### Field Access

```java
.code()
    // Instance field
    .aload(0)
    .getfield("com/example/Counter", "count", "I")

    .aload(0)
    .iload(1)
    .putfield("com/example/Counter", "count", "I")

    // Static field
    .getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")

    .iconst(42)
    .putstatic("com/example/Config", "value", "I")
```

### Object Creation

```java
.code()
    // New object
    .new_("java/lang/StringBuilder")
    .dup()
    .invokespecial("java/lang/StringBuilder", "<init>", "()V")

    // Primitive array
    .iconst(10)
    .newarray(AccessFlags.T_INT)    // new int[10]

    // Object array
    .iconst(5)
    .anewarray("java/lang/String")  // new String[5]

    // Multi-dimensional array
    .iconst(3)
    .iconst(4)
    .multianewarray("[[I", 2)       // new int[3][4]
```

### Array Operations

```java
.code()
    // Load from array
    .aload(0).iconst(0).iaload()    // intArray[0]
    .aload(0).iconst(0).aaload()    // objectArray[0]
    .aload(0).iconst(0).baload()    // byteArray[0]

    // Store to array
    .aload(0).iconst(0).iconst(42).iastore()    // intArray[0] = 42
    .aload(0).iconst(0).aconst_null().aastore() // objectArray[0] = null

    // Array length
    .aload(0).arraylength()
```

### Type Checking

```java
.code()
    .aload(0)
    .checkcast("java/lang/String")     // (String) obj

    .aload(0)
    .instanceof_("java/util/List")     // obj instanceof List
```

### Exception Handling

```java
.code()
    .trycatch("try_start", "try_end", "handler", "java/lang/NumberFormatException")
    .label("try_start")
    .aload(0)
    .invokestatic("java/lang/Integer", "parseInt", "(Ljava/lang/String;)I")
    .ireturn()
    .label("try_end")
    .label("handler")
    .pop()
    .iconst(0)
    .ireturn()
```

### Return Instructions

```java
.code()
    .vreturn()    // return void
    .ireturn()    // return int
    .lreturn()    // return long
    .freturn()    // return float
    .dreturn()    // return double
    .areturn()    // return reference
```

### Monitor Instructions

```java
.code()
    .aload(0)
    .monitorenter()
    // synchronized block
    .aload(0)
    .monitorexit()
```

---

## TypeDescriptor

Parse and manipulate JVM type descriptors.

```java
import com.tonic.type.TypeDescriptor;

// Primitive types
TypeDescriptor intType = TypeDescriptor.INT_TYPE;
TypeDescriptor longType = TypeDescriptor.LONG_TYPE;

// Object types
TypeDescriptor stringType = TypeDescriptor.forClass("java/lang/String");

// Array types
TypeDescriptor intArray = TypeDescriptor.forArray(TypeDescriptor.INT_TYPE, 1);

// Method descriptors
TypeDescriptor method = TypeDescriptor.forMethod(
    TypeDescriptor.INT_TYPE,       // return type
    TypeDescriptor.INT_TYPE,       // param 1
    TypeDescriptor.INT_TYPE        // param 2
);

// Parse from string
TypeDescriptor parsed = TypeDescriptor.parse("Ljava/lang/String;");

// Query properties
int sort = stringType.getSort();           // OBJECT
int size = TypeDescriptor.LONG_TYPE.getSize(); // 2 (slots)
int loadOp = intType.getLoadOpcode();      // ILOAD opcode
```

### TypeDescriptor Methods

| Method | Description |
|--------|-------------|
| `forClass(String internalName)` | Create object type descriptor |
| `forArray(TypeDescriptor element, int dims)` | Create array type |
| `forMethod(TypeDescriptor ret, TypeDescriptor... params)` | Create method descriptor |
| `parse(String descriptor)` | Parse descriptor string |
| `getSort()` | Get type sort (VOID, INT, OBJECT, ARRAY, METHOD) |
| `getSize()` | Get stack slot size (1 or 2) |
| `getLoadOpcode()` | Get appropriate LOAD opcode |
| `getStoreOpcode()` | Get appropriate STORE opcode |
| `getReturnOpcode()` | Get appropriate RETURN opcode |
| `getInternalName()` | Get internal class name (for objects) |
| `getElementType()` | Get array element type |
| `getReturnType()` | Get method return type |
| `getArgumentTypes()` | Get method parameter types |

---

## AccessFlags

Interface containing all JVM constants.

```java
import com.tonic.type.AccessFlags;

// Access modifiers
int flags = AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC | AccessFlags.ACC_FINAL;

// Class versions
int java11 = AccessFlags.V11;
int java17 = AccessFlags.V17;

// Array type constants for newarray
int intArrayType = AccessFlags.T_INT;
int longArrayType = AccessFlags.T_LONG;
```

### Access Flag Constants

| Flag | Value | Description |
|------|-------|-------------|
| `ACC_PUBLIC` | 0x0001 | public |
| `ACC_PRIVATE` | 0x0002 | private |
| `ACC_PROTECTED` | 0x0004 | protected |
| `ACC_STATIC` | 0x0008 | static |
| `ACC_FINAL` | 0x0010 | final |
| `ACC_SYNCHRONIZED` | 0x0020 | synchronized (method) |
| `ACC_VOLATILE` | 0x0040 | volatile (field) |
| `ACC_TRANSIENT` | 0x0080 | transient (field) |
| `ACC_NATIVE` | 0x0100 | native |
| `ACC_INTERFACE` | 0x0200 | interface |
| `ACC_ABSTRACT` | 0x0400 | abstract |
| `ACC_SYNTHETIC` | 0x1000 | synthetic |
| `ACC_ANNOTATION` | 0x2000 | annotation |
| `ACC_ENUM` | 0x4000 | enum |

### Version Constants

| Constant | Value | Java Version |
|----------|-------|--------------|
| `V1_5` | 49 | Java 5 |
| `V1_6` | 50 | Java 6 |
| `V1_7` | 51 | Java 7 |
| `V1_8` | 52 | Java 8 |
| `V11` | 55 | Java 11 |
| `V17` | 61 | Java 17 |
| `V21` | 65 | Java 21 |

### Array Type Constants

| Constant | Value | Type |
|----------|-------|------|
| `T_BOOLEAN` | 4 | boolean[] |
| `T_CHAR` | 5 | char[] |
| `T_FLOAT` | 6 | float[] |
| `T_DOUBLE` | 7 | double[] |
| `T_BYTE` | 8 | byte[] |
| `T_SHORT` | 9 | short[] |
| `T_INT` | 10 | int[] |
| `T_LONG` | 11 | long[] |

---

## MethodHandle

Method handle reference for invokedynamic bootstrap methods.

```java
import com.tonic.type.MethodHandle;

MethodHandle bootstrap = new MethodHandle(
    MethodHandle.H_INVOKESTATIC,
    "java/lang/invoke/LambdaMetafactory",
    "metafactory",
    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
    false
);
```

### Handle Kind Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `H_GETFIELD` | 1 | getfield |
| `H_GETSTATIC` | 2 | getstatic |
| `H_PUTFIELD` | 3 | putfield |
| `H_PUTSTATIC` | 4 | putstatic |
| `H_INVOKEVIRTUAL` | 5 | invokevirtual |
| `H_INVOKESTATIC` | 6 | invokestatic |
| `H_INVOKESPECIAL` | 7 | invokespecial |
| `H_NEWINVOKESPECIAL` | 8 | new + invokespecial |
| `H_INVOKEINTERFACE` | 9 | invokeinterface |

---

## Complete Example: Lambda-style Class

```java
ClassFile cf = ClassBuilder.create("com/example/MyClass$$Lambda$1")
    .version(AccessFlags.V11, 0)
    .access(AccessFlags.ACC_FINAL, AccessFlags.ACC_SYNTHETIC)
    .superClass("java/lang/Object")
    .interfaces("java/util/function/Function")

    // Captured variable field
    .addField(AccessFlags.ACC_PRIVATE | AccessFlags.ACC_FINAL, "arg$0", "Ljava/lang/String;")
    .end()

    // Constructor
    .addMethod(AccessFlags.ACC_PRIVATE, "<init>", "(Ljava/lang/String;)V")
    .code()
        .aload(0)
        .invokespecial("java/lang/Object", "<init>", "()V")
        .aload(0)
        .aload(1)
        .putfield("com/example/MyClass$$Lambda$1", "arg$0", "Ljava/lang/String;")
        .vreturn()
    .end()
    .end()

    // SAM method implementation
    .addMethod(AccessFlags.ACC_PUBLIC, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;")
    .code()
        .aload(0)
        .getfield("com/example/MyClass$$Lambda$1", "arg$0", "Ljava/lang/String;")
        .areturn()
    .end()
    .end()

    .build();
```

---

## Integration with YABR Analysis

Generated classes integrate with YABR's analysis infrastructure:

```java
ClassFile cf = ClassBuilder.create("com/example/Generated")
    .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "test", "(I)I")
    .code()
        .iload(0)
        .iconst(2)
        .imul()
        .ireturn()
    .end()
    .end()
    .build();

// Analyze generated bytecode
MethodEntry method = cf.getMethods().stream()
    .filter(m -> m.getName().equals("test"))
    .findFirst().orElseThrow();

Bytecode bc = new Bytecode(method);
for (Instruction insn : bc.getCodeWriter().getInstructions()) {
    System.out.println(insn);
}

// Apply SSA transformations
SSA ssa = new SSA(cf.getConstPool())
    .withConstantFolding();
ssa.transform(method);
```

---

## Migration from ASM

| ASM | YABR |
|-----|------|
| `ClassWriter cw = new ClassWriter(...)` | `ClassBuilder.create(...)` |
| `cw.visit(V11, ACC_PUBLIC, ...)` | `.version(V11, 0).access(ACC_PUBLIC)` |
| `MethodVisitor mv = cw.visitMethod(...)` | `.addMethod(...).code()` |
| `mv.visitVarInsn(ILOAD, 0)` | `.iload(0)` |
| `mv.visitInsn(IADD)` | `.iadd()` |
| `mv.visitMethodInsn(INVOKEVIRTUAL, ...)` | `.invokevirtual(...)` |
| `Label l = new Label()` | String labels: `"myLabel"` |
| `mv.visitLabel(l)` | `.label("myLabel")` |
| `mv.visitJumpInsn(GOTO, l)` | `.goto_("myLabel")` |
| `Type.INT_TYPE` | `TypeDescriptor.INT_TYPE` |
| `new Handle(H_INVOKESTATIC, ...)` | `new MethodHandle(H_INVOKESTATIC, ...)` |

---

[<- Back to README](../README.md) | [Bytecode API](bytecode-api.md) | [Frame Computation ->](frame-computation.md)
