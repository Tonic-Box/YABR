[<- Back to README](../README.md) | [Bytecode API](bytecode-api.md) | [SSA Guide ->](ssa-guide.md)

# Visitor Patterns

YABR provides three visitor patterns for traversing and transforming classes at different levels of abstraction.

## Overview

| Visitor | Level | Use Case |
|---------|-------|----------|
| `AbstractClassVisitor` | Class | Traverse fields, methods, attributes |
| `AbstractBytecodeVisitor` | Instruction | Analyze/transform bytecode |
| `AbstractBlockVisitor` | SSA IR | Work with SSA-form basic blocks |

## AbstractClassVisitor

Visits class-level elements: constant pool items, attributes, fields, and methods.

### Basic Usage

```java
import com.tonic.analysis.visitor.AbstractClassVisitor;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;

public class MyClassVisitor extends AbstractClassVisitor {

    @Override
    public void visitField(FieldEntry field) {
        System.out.println("Field: " + field.getName() + " " + field.getDesc());
        super.visitField(field);
    }

    @Override
    public void visitMethod(MethodEntry method) {
        System.out.println("Method: " + method.getName() + method.getDesc());
        super.visitMethod(method);
    }

    @Override
    public void visitClassAttribute(Attribute attr) {
        System.out.println("Attribute: " + attr.getName());
    }
}

// Apply the visitor
classFile.accept(new MyClassVisitor());
```

### Visiting Constant Pool Items

```java
public class ConstPoolVisitor extends AbstractClassVisitor {

    @Override
    public void visitUtf8(Utf8Item item) {
        System.out.println("UTF8: " + item.getValue());
    }

    @Override
    public void visitClassRef(ClassRefItem item) {
        System.out.println("Class: " + item.getClassName());
    }

    @Override
    public void visitMethodRef(MethodRefItem item) {
        System.out.println("Method: " + item.getOwner() + "." + item.getName());
    }

    @Override
    public void visitFieldRef(FieldRefItem item) {
        System.out.println("Field: " + item.getOwner() + "." + item.getName());
    }
}
```

## AbstractBytecodeVisitor

Visits individual bytecode instructions within a method.

### Basic Usage

```java
import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.instruction.*;

public class MyBytecodeVisitor extends AbstractBytecodeVisitor {

    @Override
    public void visit(InvokeVirtualInstruction instr) {
        System.out.println("INVOKEVIRTUAL: " + instr.getClassName() +
                          "." + instr.getMethodName());
        super.visit(instr);
    }

    @Override
    public void visit(GetFieldInstruction instr) {
        System.out.println("GETFIELD: " + instr.getFieldName());
        super.visit(instr);
    }

    @Override
    public void visit(ReturnInstruction instr) {
        System.out.println("RETURN at offset " + instr.getOffset());
        super.visit(instr);
    }
}
```

### Processing a Method

```java
MyBytecodeVisitor visitor = new MyBytecodeVisitor();
visitor.process(methodEntry);
```

### Modifying Bytecode

The visitor has access to `codeWriter` for modifications:

```java
public class LoggingVisitor extends AbstractBytecodeVisitor {

    @Override
    public void visit(ReturnInstruction instruction) {
        super.visit(instruction);

        // Insert logging before each return
        Bytecode bytecode = new Bytecode(codeWriter);
        bytecode.setInsertBefore(true);
        bytecode.setInsertBeforeOffset(instruction.getOffset());

        bytecode.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
        bytecode.addLdc("Returning from method");
        bytecode.addInvokeVirtual("java/io/PrintStream", "println",
                                  "(Ljava/lang/String;)V");

        try {
            bytecode.finalizeBytecode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
```

### Available Instruction Visitors

Override these methods to handle specific instruction types:

```java
// Control flow
void visit(GotoInstruction instr)
void visit(IfInstruction instr)
void visit(IfIcmpInstruction instr)
void visit(SwitchInstruction instr)
void visit(ReturnInstruction instr)

// Method calls
void visit(InvokeVirtualInstruction instr)
void visit(InvokeStaticInstruction instr)
void visit(InvokeSpecialInstruction instr)
void visit(InvokeInterfaceInstruction instr)
void visit(InvokeDynamicInstruction instr)

// Field access
void visit(GetFieldInstruction instr)
void visit(PutFieldInstruction instr)
void visit(GetStaticInstruction instr)
void visit(PutStaticInstruction instr)

// Object operations
void visit(NewInstruction instr)
void visit(CheckCastInstruction instr)
void visit(InstanceOfInstruction instr)

// Stack operations
void visit(ALoadInstruction instr)
void visit(ILoadInstruction instr)
void visit(AStoreInstruction instr)
void visit(IStoreInstruction instr)
void visit(LdcInstruction instr)
void visit(IConstInstruction instr)
// ... and many more
```

## AbstractBlockVisitor

Visits SSA-form IR blocks and instructions. This is the highest level of abstraction.

### Basic Usage

```java
import com.tonic.analysis.visitor.AbstractBlockVisitor;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.IRPrinter;

public class MyBlockVisitor extends AbstractBlockVisitor {

    @Override
    public void visitBlock(IRBlock block) {
        System.out.println("Block: " + block.getName());
        System.out.println("  Predecessors: " + block.getPredecessors().stream()
            .map(IRBlock::getName).toList());
        System.out.println("  Successors: " + block.getSuccessors().stream()
            .map(IRBlock::getName).toList());

        super.visitBlock(block);  // Visit instructions
    }

    @Override
    public void visitPhi(PhiInstruction phi) {
        System.out.println("  [PHI] " + IRPrinter.format(phi));
    }

    @Override
    public void visitInstruction(IRInstruction instruction) {
        System.out.println("  " + IRPrinter.format(instruction));
    }
}
```

### Processing a Method

```java
MyBlockVisitor visitor = new MyBlockVisitor();
visitor.process(methodEntry);  // Lifts to SSA automatically

// Access the IR method after processing
IRMethod irMethod = visitor.getIRMethod();
```

### IRPrinter Utility

Use `IRPrinter` to format IR instructions as readable strings:

```java
import com.tonic.analysis.ssa.IRPrinter;

// Format single instruction
String formatted = IRPrinter.format(instruction);

// Format block header
String header = IRPrinter.formatBlockHeader(block);

// Format entire method
String methodStr = IRPrinter.format(irMethod);
```

Example output:
```
v3 = phi(block_0:v1, block_1:v2)
v4 = ADD v1, v2
v5 = const 42
store local[1] = v4
if v4 EQ 0 goto block_2 else block_3
return v5
```

## Combining Visitors

### Class -> Method -> Bytecode

```java
public class InstrumentingVisitor extends AbstractClassVisitor {

    @Override
    public void visitMethod(MethodEntry method) {
        // Skip constructors and static initializers
        if (method.getName().startsWith("<")) {
            return;
        }

        // Apply bytecode visitor
        new MethodInstrumenter().process(method);
    }
}

public class MethodInstrumenter extends AbstractBytecodeVisitor {

    @Override
    public void visit(ReturnInstruction instruction) {
        // Add instrumentation before return
        Bytecode bc = new Bytecode(codeWriter);
        bc.setInsertBefore(true);
        bc.setInsertBeforeOffset(instruction.getOffset());

        // ... add instrumentation

        try {
            bc.finalizeBytecode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
```

### Using Visitors with SSA Analysis

```java
public class OptimizingVisitor extends AbstractClassVisitor {

    @Override
    public void visitMethod(MethodEntry method) {
        if (method.getCodeAttribute() == null) return;

        ConstPool cp = method.getClassFile().getConstPool();
        SSA ssa = new SSA(cp)
            .withConstantFolding()
            .withCopyPropagation()
            .withDeadCodeElimination();

        // Transform: lift -> optimize -> lower
        ssa.transform(method);
    }
}
```

## Complete Example: Method Entry/Exit Logging

```java
public class LoggingVisitor extends AbstractBytecodeVisitor {

    @Override
    public void visitMethod(MethodEntry method) {
        super.visitMethod(method);

        // Add entry logging at the start
        Bytecode bc = new Bytecode(method);
        bc.setInsertBefore(true);

        bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
        bc.addLdc("Entering: " + method.getName());
        bc.addInvokeVirtual("java/io/PrintStream", "println",
                          "(Ljava/lang/String;)V");

        try {
            bc.finalizeBytecode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void visit(ReturnInstruction instruction) {
        super.visit(instruction);

        // Add exit logging before each return
        Bytecode bc = new Bytecode(codeWriter);
        bc.setInsertBefore(true);
        bc.setInsertBeforeOffset(instruction.getOffset());

        bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
        bc.addLdc("Exiting: " + method.getName());
        bc.addInvokeVirtual("java/io/PrintStream", "println",
                          "(Ljava/lang/String;)V");

        try {
            bc.finalizeBytecode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

// Apply to a class
classFile.accept(new AbstractClassVisitor() {
    @Override
    public void visitMethod(MethodEntry method) {
        if (!method.getName().startsWith("<")) {
            new LoggingVisitor().process(method);
        }
    }
});
```

---

[<- Back to README](../README.md) | [Bytecode API](bytecode-api.md) | [SSA Guide ->](ssa-guide.md)
