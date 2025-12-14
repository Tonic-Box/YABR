[<- Back to README](../README.md) | [Renamer API](renamer-api.md) | [AST Guide ->](ast-guide.md)

# AST Editor API

The AST Editor API provides a handler-based interface for traversing and transforming AST nodes. It enables targeted interception and replacement of specific expressions and statements during AST traversal.

## What is the AST Editor?

The AST Editor operates at the source AST level and provides:

- **Handler-based interception** - Register handlers for specific expression/statement types
- **Type-safe replacements** - Replace nodes with new AST nodes
- **Context awareness** - Access method info, nesting depth, and factory methods
- **Matcher-based filtering** - Use predicates for flexible node matching
- **Rich expression support** - 9 expression types + 6 statement types with handlers

## Quick Start

```java
import com.tonic.analysis.source.editor.ASTEditor;
import com.tonic.analysis.source.editor.Replacement;
import com.tonic.analysis.source.ast.stmt.BlockStmt;

// Get method body AST (from recovery or construction)
BlockStmt methodBody = ...;

// Create editor
ASTEditor editor = new ASTEditor(methodBody, "myMethod", "(I)V", "com/example/MyClass");

// Replace deprecated method calls
editor.onMethodCall((ctx, call) -> {
    if (call.getMethodName().equals("oldMethod")) {
        return Replacement.with(ctx.factory()
            .methodCall("newMethod")
            .on(call.getReceiver())
            .withArgs(call.getArguments())
            .build());
    }
    return Replacement.keep();
});

// Apply changes
editor.apply();
```

## Core API

### ASTEditor

The main entry point for AST editing:

```java
import com.tonic.analysis.source.editor.ASTEditor;
import com.tonic.analysis.source.ast.stmt.BlockStmt;

// Full constructor
ASTEditor editor = new ASTEditor(methodBody, methodName, descriptor, ownerClass);

// Minimal constructor
ASTEditor editor = new ASTEditor(methodBody);
```

### Expression Handlers

Register handlers for specific expression types:

```java
// Method calls
editor.onMethodCall((ctx, call) -> {
    // call.getMethodName(), call.getOwnerClass(), call.getArguments()
    return Replacement.keep();
});

// Field access
editor.onFieldAccess((ctx, access) -> {
    // access.getFieldName(), access.getOwnerClass(), access.getReceiver()
    return Replacement.keep();
});

// Object creation
editor.onNewExpr((ctx, newExpr) -> {
    // newExpr.getClassName(), newExpr.getArguments()
    return Replacement.keep();
});

// Array creation
editor.onNewArray((ctx, newArray) -> {
    // newArray.getComponentType(), newArray.getSizeExpression()
    return Replacement.keep();
});

// Cast expressions
editor.onCast((ctx, cast) -> {
    // cast.getTargetType(), cast.getExpression()
    return Replacement.keep();
});

// instanceof expressions
editor.onInstanceOf((ctx, iof) -> {
    // iof.getCheckType(), iof.getExpression()
    return Replacement.keep();
});

// Binary operations
editor.onBinaryExpr((ctx, binary) -> {
    // binary.getOperator(), binary.getLeft(), binary.getRight()
    return Replacement.keep();
});

// Unary operations
editor.onUnaryExpr((ctx, unary) -> {
    // unary.getOperator(), unary.getOperand()
    return Replacement.keep();
});

// Array access (with read/store differentiation)
editor.onArrayAccess((ctx, access, accessType) -> {
    // accessType: READ, STORE, or COMPOUND_ASSIGN
    return Replacement.keep();
});
```

### Array Access Handler

The array access handler provides context about whether the access is a read or store:

```java
import com.tonic.analysis.source.editor.handler.ArrayAccessHandler.ArrayAccessType;

// Handle all array accesses
editor.onArrayAccess((ctx, access, accessType) -> {
    switch (accessType) {
        case READ:           // value = array[i]
            System.out.println("Reading from " + access);
            break;
        case STORE:          // array[i] = value
            System.out.println("Storing to " + access);
            break;
        case COMPOUND_ASSIGN: // array[i] += value
            System.out.println("Compound assignment on " + access);
            break;
    }
    return Replacement.keep();
});

// Handle only reads
editor.onArrayRead((ctx, access, accessType) -> {
    return Replacement.keep();
});

// Handle only stores
editor.onArrayStore((ctx, access, accessType) -> {
    return Replacement.keep();
});
```

### Statement Handlers

Register handlers for statement types:

```java
// Return statements
editor.onReturn((ctx, ret) -> {
    // ret.getValue() (null for void return)
    return Replacement.keep();
});

// Throw statements
editor.onThrow((ctx, throwStmt) -> {
    // throwStmt.getException()
    return Replacement.keep();
});

// If statements
editor.onIf((ctx, ifStmt) -> {
    // ifStmt.getCondition(), ifStmt.getThenBranch(), ifStmt.getElseBranch()
    return Replacement.keep();
});

// Loop statements (for, while, do-while, for-each)
editor.onLoop((ctx, loop) -> {
    // loop is ForStmt, WhileStmt, DoWhileStmt, or ForEachStmt
    return Replacement.keep();
});

// Try-catch statements
editor.onTryCatch((ctx, tryCatch) -> {
    // tryCatch.getTryBlock(), tryCatch.getCatches(), tryCatch.getFinallyBlock()
    return Replacement.keep();
});

// Assignment statements (expression statements with assignment)
editor.onAssignment((ctx, assignment) -> {
    // assignment.getLeft(), assignment.getRight()
    return Replacement.keep();
});
```

### Matcher-Based Handlers

Use predicates for flexible filtering:

```java
import com.tonic.analysis.source.editor.matcher.ExprMatcher;
import com.tonic.analysis.source.editor.matcher.StmtMatcher;

// Expression matchers
editor.onExpr(ExprMatcher.methodCall("println"), (ctx, expr) -> {
    return Replacement.keep();
});

editor.onExpr(ExprMatcher.anyMethodCall().and(ExprMatcher.methodCall("deprecated")),
    (ctx, expr) -> {
        return Replacement.remove();
    });

// Statement matchers
editor.onStmt(StmtMatcher.returnWithValue(), (ctx, stmt) -> {
    return Replacement.keep();
});

editor.onStmt(StmtMatcher.anyLoop(), (ctx, stmt) -> {
    return Replacement.keep();
});
```

## Replacement System

### Replacement Types

```java
import com.tonic.analysis.source.editor.Replacement;

// Keep the original (no change)
return Replacement.keep();

// Replace with new expression
return Replacement.with(newExpression);

// Replace with new statement
return Replacement.with(newStatement);

// Replace with multiple statements
return Replacement.withBlock(stmt1, stmt2, stmt3);

// Remove entirely
return Replacement.remove();

// Insert statements before current
return Replacement.insertBefore(logStmt);

// Insert statements after current
return Replacement.insertAfter(cleanupStmt);
```

### Replacement Examples

```java
// Replace method call with literal
editor.onMethodCall((ctx, call) -> {
    if (call.getMethodName().equals("getConstant")) {
        return Replacement.with(ctx.factory().intLiteral(42));
    }
    return Replacement.keep();
});

// Add logging before returns
editor.onReturn((ctx, ret) -> {
    Statement logStmt = ctx.factory()
        .methodCall("info")
        .on("Logger")
        .withArgs(ctx.factory().stringLiteral("Returning from " + ctx.getMethodName()))
        .asStatement();
    return Replacement.insertBefore(logStmt);
});

// Remove debug statements
editor.onMethodCall((ctx, call) -> {
    if (call.getMethodName().equals("debug") &&
        call.getOwnerClass().equals("com/example/Logger")) {
        return Replacement.remove();
    }
    return Replacement.keep();
});

// Wrap with try-catch
editor.onMethodCall((ctx, call) -> {
    if (call.getMethodName().equals("riskyOperation")) {
        // Create try-catch wrapper
        TryCatchStmt wrapper = ...;
        return Replacement.with(wrapper);
    }
    return Replacement.keep();
});
```

## EditorContext

The context provides utilities and information during traversal:

```java
editor.onMethodCall((ctx, call) -> {
    // Method information
    String methodName = ctx.getMethodName();
    String descriptor = ctx.getMethodDescriptor();
    String owner = ctx.getOwnerClass();

    // Current location
    Statement current = ctx.getCurrentStatement();
    BlockStmt enclosing = ctx.getEnclosingBlock();
    int index = ctx.getStatementIndex();

    // Nesting information
    boolean inTry = ctx.isInTryBlock();
    boolean inLoop = ctx.isInLoop();
    boolean inConditional = ctx.isInConditional();

    // AST factory
    ASTFactory factory = ctx.factory();

    // Utility methods
    Replacement r = ctx.insertBefore(stmt);
    Replacement r = ctx.insertAfter(stmt);

    return Replacement.keep();
});
```

## ASTFactory

Build new AST nodes programmatically:

```java
ASTFactory factory = ctx.factory();

// Literals
LiteralExpr intLit = factory.intLiteral(42);
LiteralExpr strLit = factory.stringLiteral("hello");
LiteralExpr boolLit = factory.boolLiteral(true);
LiteralExpr nullLit = factory.nullLiteral();

// Variables
VarRefExpr varRef = factory.variable("x", PrimitiveSourceType.INT);

// Field access
FieldAccessExpr field = factory.fieldAccess(target, "fieldName", "com/example/Owner", type);
FieldAccessExpr staticField = factory.staticField("com/example/Owner", "CONSTANT", type);

// Method calls (builder pattern)
MethodCallExpr call = factory.methodCall("process")
    .on(receiver)                    // instance call
    .owner("com/example/Service")
    .withArgs(arg1, arg2)
    .returning(PrimitiveSourceType.INT)
    .build();

// Static method call
MethodCallExpr staticCall = factory.methodCall("valueOf")
    .on("java/lang/Integer")         // static call
    .withArgs(factory.intLiteral(42))
    .returning(new ReferenceSourceType("java/lang/Integer"))
    .build();

// Object creation
NewExpr newObj = factory.newInstance("java/util/ArrayList")
    .withArgs()
    .build();

// Array creation
NewArrayExpr newArr = factory.newArray("int", factory.intLiteral(10));

// Binary operations
BinaryExpr add = factory.add(left, right, PrimitiveSourceType.INT);
BinaryExpr eq = factory.equals(left, right);
BinaryExpr and = factory.and(left, right);

// Unary operations
UnaryExpr neg = factory.negate(operand, PrimitiveSourceType.INT);
UnaryExpr not = factory.not(operand);

// Type operations
CastExpr cast = factory.cast("java/lang/String", expr);
InstanceOfExpr iof = factory.instanceOf(expr, "java/lang/String");

// Statements
ExprStmt exprStmt = factory.exprStmt(expr);
ReturnStmt ret = factory.returnStmt(value);
ReturnStmt voidRet = factory.returnVoid();
ThrowStmt throwStmt = factory.throwStmt(exception);
BlockStmt block = factory.block(stmt1, stmt2, stmt3);
IfStmt ifStmt = factory.ifStmt(condition, thenBranch);
IfStmt ifElse = factory.ifElseStmt(condition, thenBranch, elseBranch);
VarDeclStmt varDecl = factory.varDecl("int", "x", factory.intLiteral(0));
```

## Matchers

### ExprMatcher

```java
import com.tonic.analysis.source.editor.matcher.ExprMatcher;

// Method call matchers
ExprMatcher m1 = ExprMatcher.methodCall("println");
ExprMatcher m2 = ExprMatcher.methodCall("java/io/PrintStream", "println");
ExprMatcher m3 = ExprMatcher.methodCall("java/io/PrintStream", "println", 1); // arg count

// Field access matchers
ExprMatcher m4 = ExprMatcher.fieldAccess("value");
ExprMatcher m5 = ExprMatcher.fieldAccess("java/lang/System", "out");

// Type matchers
ExprMatcher m6 = ExprMatcher.newExpr("java/util/ArrayList");
ExprMatcher m7 = ExprMatcher.cast("java/lang/String");
ExprMatcher m8 = ExprMatcher.instanceOf("java/util/List");
ExprMatcher m9 = ExprMatcher.anyCast();
ExprMatcher m10 = ExprMatcher.anyInstanceOf();

// Generic matchers
ExprMatcher m11 = ExprMatcher.anyMethodCall();
ExprMatcher m12 = ExprMatcher.anyFieldAccess();
ExprMatcher m13 = ExprMatcher.anyBinary();
ExprMatcher m14 = ExprMatcher.anyUnary();
ExprMatcher m15 = ExprMatcher.anyLiteral();
ExprMatcher m16 = ExprMatcher.anyArrayAccess();

// Operator matchers
ExprMatcher m17 = ExprMatcher.binaryOp(BinaryOperator.ADD);
ExprMatcher m18 = ExprMatcher.assignment();
ExprMatcher m19 = ExprMatcher.comparison();

// Combinators
ExprMatcher combined = m1.and(m2);          // Both must match
ExprMatcher either = m1.or(m2);             // Either must match
ExprMatcher negated = m1.not();             // Must not match

// Custom predicate
ExprMatcher custom = ExprMatcher.custom(expr -> {
    return expr instanceof MethodCallExpr &&
           ((MethodCallExpr) expr).getArgumentCount() > 3;
}, "methodWithManyArgs");

// Match all / none
ExprMatcher all = ExprMatcher.any();
ExprMatcher none = ExprMatcher.none();
```

### StmtMatcher

```java
import com.tonic.analysis.source.editor.matcher.StmtMatcher;

// Return matchers
StmtMatcher s1 = StmtMatcher.returnStmt();
StmtMatcher s2 = StmtMatcher.returnWithValue();
StmtMatcher s3 = StmtMatcher.voidReturn();

// Control flow matchers
StmtMatcher s4 = StmtMatcher.throwStmt();
StmtMatcher s5 = StmtMatcher.ifStmt();
StmtMatcher s6 = StmtMatcher.ifElseStmt();
StmtMatcher s7 = StmtMatcher.ifOnlyStmt();  // if without else

// Loop matchers
StmtMatcher s8 = StmtMatcher.anyLoop();
StmtMatcher s9 = StmtMatcher.forStmt();
StmtMatcher s10 = StmtMatcher.whileStmt();
StmtMatcher s11 = StmtMatcher.doWhileStmt();
StmtMatcher s12 = StmtMatcher.forEachStmt();

// Other matchers
StmtMatcher s13 = StmtMatcher.tryCatchStmt();
StmtMatcher s14 = StmtMatcher.switchStmt();
StmtMatcher s15 = StmtMatcher.synchronizedStmt();
StmtMatcher s16 = StmtMatcher.blockStmt();
StmtMatcher s17 = StmtMatcher.exprStmt();
StmtMatcher s18 = StmtMatcher.varDeclStmt();
StmtMatcher s19 = StmtMatcher.breakStmt();
StmtMatcher s20 = StmtMatcher.continueStmt();
StmtMatcher s21 = StmtMatcher.labeledStmt();
StmtMatcher s22 = StmtMatcher.withLabel("outer");

// Expression statement matchers
StmtMatcher s23 = StmtMatcher.methodCallStmt();
StmtMatcher s24 = StmtMatcher.assignmentStmt();

// Combinators (same as ExprMatcher)
StmtMatcher combined = s1.and(s2);
StmtMatcher either = s1.or(s2);
StmtMatcher negated = s1.not();
```

## Convenience Editors

### ExpressionEditor

Simplified API for expression-focused editing:

```java
import com.tonic.analysis.source.editor.ExpressionEditor;

ExpressionEditor editor = new ExpressionEditor(methodBody, "test", "()V", "com/example/Test");

// Fluent handlers
editor.onMethodCall(handler)
      .onFieldAccess(handler)
      .onNewExpr(handler)
      .onArrayAccess(handler)
      .onArrayRead(handler)
      .onArrayStore(handler);

// Convenience replacements
editor.replaceMethodCall("com/old/Api", "deprecated", (ctx, call) -> {
    return ctx.factory().methodCall("newMethod").on(call.getReceiver()).build();
});

editor.removeMethodCall("com/debug/Logger", "trace");

editor.replaceFieldAccess("com/old/Constants", "OLD_VALUE", (ctx, access) -> {
    return ctx.factory().intLiteral(42);
});

// Query methods
List<Expression> calls = editor.findMethodCalls();
List<Expression> specificCalls = editor.findMethodCalls("com/example/Service", "process");
List<Expression> fields = editor.findFieldAccesses();
List<Expression> arrays = editor.findArrayAccesses();

editor.apply();
```

### StatementEditor

Simplified API for statement-focused editing:

```java
import com.tonic.analysis.source.editor.StatementEditor;

StatementEditor editor = new StatementEditor(methodBody, "test", "()V", "com/example/Test");

// Fluent handlers
editor.onReturn(handler)
      .onThrow(handler)
      .onIf(handler)
      .onLoop(handler)
      .onTryCatch(handler);

// Convenience methods
editor.insertBeforeReturns(logStatement);
editor.insertBeforeValueReturns(validateStatement);
editor.insertBeforeVoidReturns(cleanupStatement);
editor.insertBeforeThrows(logStatement);
editor.wrapLoops(enterStmt, exitStmt);
editor.removeStmts(StmtMatcher.voidReturn());

// Query methods
List<Statement> returns = editor.findReturns();
List<Statement> valueReturns = editor.findValueReturns();
List<Statement> loops = editor.findLoops();
List<Statement> tryCatches = editor.findTryCatches();

editor.apply();
```

## Package Structure

```
com.tonic.analysis.source.editor/
├── ASTEditor.java                  # Main orchestrating class
├── ExpressionEditor.java           # Expression-focused convenience API
├── StatementEditor.java            # Statement-focused convenience API
├── EditorContext.java              # Context during traversal
├── Replacement.java                # Replacement result types
├── handler/
│   ├── ExpressionHandler.java      # Base expression handler
│   ├── StatementHandler.java       # Base statement handler
│   ├── MethodCallHandler.java      # Method call expressions
│   ├── FieldAccessHandler.java     # Field access expressions
│   ├── NewExprHandler.java         # new Object() expressions
│   ├── NewArrayHandler.java        # new Type[] expressions
│   ├── CastHandler.java            # Cast expressions
│   ├── InstanceOfHandler.java      # instanceof expressions
│   ├── BinaryExprHandler.java      # Binary operators
│   ├── UnaryExprHandler.java       # Unary operators
│   ├── ArrayAccessHandler.java     # Array access with read/store
│   ├── ReturnHandler.java          # Return statements
│   ├── ThrowHandler.java           # Throw statements
│   ├── IfHandler.java              # If statements
│   ├── LoopHandler.java            # For/while/do-while/for-each
│   ├── TryCatchHandler.java        # Try-catch statements
│   └── AssignmentHandler.java      # Assignment statements
├── matcher/
│   ├── ExprMatcher.java            # Expression predicate matching
│   └── StmtMatcher.java            # Statement predicate matching
└── util/
    └── ASTFactory.java             # AST node creation factory
```

## Example: Logging Injection

Add method entry/exit logging:

```java
public class LoggingInjector {

    public static void injectLogging(BlockStmt methodBody, String methodName) {
        ASTEditor editor = new ASTEditor(methodBody, methodName, "()V", "com/example/MyClass");
        ASTFactory factory = new ASTFactory();

        // Add entry logging at start
        Statement entryLog = factory.methodCall("info")
            .on("java/util/logging/Logger")
            .withArgs(factory.stringLiteral("Entering: " + methodName))
            .asStatement();
        methodBody.insertStatement(0, entryLog);

        // Add exit logging before returns
        editor.onReturn((ctx, ret) -> {
            Statement exitLog = ctx.factory().methodCall("info")
                .on("java/util/logging/Logger")
                .withArgs(ctx.factory().stringLiteral("Exiting: " + methodName))
                .asStatement();
            return Replacement.insertBefore(exitLog);
        });

        editor.apply();
    }
}
```

## Example: API Migration

Replace deprecated API calls:

```java
public class ApiMigrator {

    public static void migrateApi(BlockStmt methodBody) {
        ASTEditor editor = new ASTEditor(methodBody);

        // Replace deprecated method calls
        editor.onMethodCall((ctx, call) -> {
            if (call.getOwnerClass().equals("com/old/Api")) {
                switch (call.getMethodName()) {
                    case "oldMethod":
                        return Replacement.with(ctx.factory()
                            .methodCall("newMethod")
                            .on("com/new/Api")
                            .withArgs(call.getArguments())
                            .returning(call.getType())
                            .build());

                    case "deprecatedHelper":
                        // Remove entirely
                        return Replacement.remove();
                }
            }
            return Replacement.keep();
        });

        // Replace deprecated field access
        editor.onFieldAccess((ctx, access) -> {
            if (access.getOwnerClass().equals("com/old/Constants") &&
                access.getFieldName().equals("OLD_VALUE")) {
                return Replacement.with(ctx.factory()
                    .staticField("com/new/Constants", "NEW_VALUE", access.getType()));
            }
            return Replacement.keep();
        });

        editor.apply();
    }
}
```

## Example: Null Check Insertion

Add null checks before field accesses:

```java
public class NullCheckInserter {

    public static void insertNullChecks(BlockStmt methodBody) {
        ExpressionEditor editor = new ExpressionEditor(methodBody, "test", "()V", "Test");

        editor.onFieldAccess((ctx, access) -> {
            // Skip static field access
            if (access.isStatic()) {
                return Replacement.keep();
            }

            Expression receiver = access.getReceiver();
            if (receiver != null && !ctx.isNullChecked(receiver)) {
                // Create: if (receiver == null) throw new NullPointerException();
                IfStmt nullCheck = ctx.factory().ifStmt(
                    ctx.factory().equals(receiver, ctx.factory().nullLiteral()),
                    ctx.factory().throwStmt(
                        ctx.factory().newInstance("java/lang/NullPointerException").build()
                    )
                );
                return Replacement.insertBefore(nullCheck);
            }
            return Replacement.keep();
        });

        editor.apply();
    }
}
```

## Related Documentation

- [Renamer API](renamer-api.md) - Class/method/field renaming
- [AST Guide](ast-guide.md) - AST recovery, emission, and lowering
- [Architecture](architecture.md) - System overview

---

[<- Back to README](../README.md) | [Renamer API](renamer-api.md) | [AST Guide ->](ast-guide.md)
