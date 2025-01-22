A simple java ClassFIle and bytecode reader/writer I wrote as a learning exercize.

Demos can be found here: https://github.com/Tonic-Box/YABR/tree/main/src/main/java/com/tonic/demo

Inspired by: https://github.com/d-o-g/classpooly

__**Example Code:**__

New Class Creation:
```java
ClassPool classPool = ClassPool.getDefault();
ClassFile classFile = classPool.createNewClass("com/tonic/ANewClass", classAccess);
```


Load a class from disk:
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

Load a builtin java class:
```java
ClassPool.getDefault().loadClass("java/lang/Object.class");
```


Field/method creation:
```java
ClassFile classFile = ...;

int staticAccess = new AccessBuilder()
        .setPublic()
        .setStatic()
        .build();

// Create a Static field
classFile.createNewField(staticAccess, "testStaticIntField", "I", new ArrayList<>());

// Create a Static method for our getter
MethodEntry getter = classFile.createNewMethod(false, staticAccess, "demoStaticGetter", int.class);
Bytecode getterBytecode = new Bytecode(getter);
ConstPool constPool = getterBytecode.getConstPool();
int fieldRefIndex = constPool.findOrAddField("com/tonic/ANewClass", "testStaticIntField", "I");
getterBytecode.addGetStatic(fieldRefIndex);
getterBytecode.addReturn(ReturnType.IRETURN);
getterBytecode.finalizeBytecode();

// Create a Static method for our setter
MethodEntry setterMethod = classFile.createNewMethod(false, staticAccess, "demoStaticSetter", void.class, int.class);
Bytecode setterBytecode = new Bytecode(setterMethod);
ConstPool constPool2 = getterBytecode.getConstPool();
int fieldRefIndex2 = constPool2.findOrAddField("com/tonic/ANewClass", "testStaticIntField", "I");
setterBytecode.addILoad(0);
setterBytecode.addPutStatic(fieldRefIndex2);
setterBytecode.addReturn(ReturnType.RETURN);

classFile.rebuild();

System.out.println(classFile);
```
