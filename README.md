A simple java ClassFIle and bytecode reader/writer I wrote as a learning exercize.

Demos can be found here: https://github.com/Tonic-Box/YABR/tree/main/src/main/java/com/tonic/demo

Inspired by: https://github.com/d-o-g/classpooly

## Example Code:

### New Class Creation:
```java
ClassPool classPool = ClassPool.getDefault();
ClassFile classFile = classPool.createNewClass("com/tonic/ANewClass", classAccess);
```


### Load a class from disk:
```java
ClassPool classPool = ClassPool.getDefault();

try (InputStream is = TestClassCreation.class.getResourceAsStream("TestCase.class")) {
    if (is == null) {
        throw new IOException("Resource 'TestCase.class' not found.");
    }

    ClassFile classFile = classPool.loadClass(is);

    // ...
}
```

### Load a builtin java class:
```java
ClassPool.getDefault().loadClass("java/lang/Object.class");
```


### Field creation:
```java
ClassFile classFile = ...;

int staticAccessPrivate = new AccessBuilder()
    .setPrivate()
    .setStatic()
    .build();

FieldEntry staticField = classFile.createNewField(
    staticAccessPrivate, 
    "testStaticIntField", 
    "I", 
    new ArrayList<>()
);

classFile.setFieldInitialValue(staticField, 12);
```


### Method creation and Bytecode Api:
```java
// Initialize class pool and access modifiers
ClassPool classPool = ...;
int access = new AccessBuilder()
    .setPublic()
    .setStatic()
    .build();

// Create a new field in the class
classFile.createNewField(
    access, 
    "testIntField", 
    "I", 
    new ArrayList<>()
);

// Create a new method
MethodEntry method = classFile.createNewMethod(access, "demoGetter", int.class);
Bytecode bytecode = new Bytecode(method);
ConstPool constPool = bytecode.getConstPool();

// Add field reference and generate bytecode instructions
int fieldRefIndex = constPool.findOrAddField("com/tonic/TestCase", "testIntField", "I");
bytecode.addGetStatic(fieldRefIndex);
bytecode.addReturn(ReturnType.IRETURN); // Add IRETURN opcode
bytecode.finalizeBytecode();

// Rebuild the class and print it
classFile.rebuild();
System.out.println(classFile);
```
