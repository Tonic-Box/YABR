# SSA IR Migration Guide

## Overview

This document describes the SSA IR redesign that reduces instruction classes from 25 to 14 by consolidating symmetric instruction pairs into unified classes with mode enums.

## Instruction Type Changes

### Removed Classes (Replaced by Combined Classes)

| Old Class | Replacement | How to Migrate |
|-----------|-------------|----------------|
| `GetFieldInstruction` | `FieldAccessInstruction(LOAD)` | Use `FieldAccessInstruction.createLoad()` or `createStaticLoad()` |
| `PutFieldInstruction` | `FieldAccessInstruction(STORE)` | Use `FieldAccessInstruction.createStore()` or `createStaticStore()` |
| `ArrayLoadInstruction` | `ArrayAccessInstruction(LOAD)` | Use `ArrayAccessInstruction.createLoad()` |
| `ArrayStoreInstruction` | `ArrayAccessInstruction(STORE)` | Use `ArrayAccessInstruction.createStore()` |
| `CastInstruction` | `TypeCheckInstruction(CAST)` | Use `TypeCheckInstruction.createCast()` |
| `InstanceOfInstruction` | `TypeCheckInstruction(INSTANCEOF)` | Use `TypeCheckInstruction.createInstanceOf()` |
| `ArrayLengthInstruction` | `SimpleInstruction(ARRAYLENGTH)` | Use `SimpleInstruction.createArrayLength()` |
| `GotoInstruction` | `SimpleInstruction(GOTO)` | Use `SimpleInstruction.createGoto()` |
| `ThrowInstruction` | `SimpleInstruction(ATHROW)` | Use `SimpleInstruction.createThrow()` |
| `MonitorEnterInstruction` | `SimpleInstruction(MONITORENTER)` | Use `SimpleInstruction.createMonitorEnter()` |
| `MonitorExitInstruction` | `SimpleInstruction(MONITOREXIT)` | Use `SimpleInstruction.createMonitorExit()` |

### New Enums

```java
// For FieldAccessInstruction and ArrayAccessInstruction
public enum AccessMode {
    LOAD,   // Reading a field/array element
    STORE   // Writing a field/array element
}

// For TypeCheckInstruction
public enum TypeCheckOp {
    CAST,       // Explicit type cast
    INSTANCEOF  // Type check operation
}

// For SimpleInstruction
public enum SimpleOp {
    ARRAYLENGTH,   // Get array length
    MONITORENTER,  // Enter synchronized block
    MONITOREXIT,   // Exit synchronized block
    ATHROW,        // Throw exception
    GOTO           // Unconditional jump
}
```

## New Combined Instruction Classes

### FieldAccessInstruction

Combines `GetFieldInstruction` and `PutFieldInstruction`:

```java
// Factory methods (preferred)
FieldAccessInstruction.createLoad(result, owner, name, descriptor, objectRef)
FieldAccessInstruction.createStaticLoad(result, owner, name, descriptor)
FieldAccessInstruction.createStore(owner, name, descriptor, objectRef, value)
FieldAccessInstruction.createStaticStore(owner, name, descriptor, value)

// Check mode
instr.isLoad()   // true for field reads
instr.isStore()  // true for field writes

// Access properties
instr.getOwner()      // String: class name
instr.getName()       // String: field name
instr.getDescriptor() // String: field type descriptor
instr.isStatic()      // boolean: static access
instr.getObjectRef()  // Value: receiver (null if static)
instr.getValue()      // Value: stored value (null if load)
```

### ArrayAccessInstruction

Combines `ArrayLoadInstruction` and `ArrayStoreInstruction`:

```java
// Factory methods
ArrayAccessInstruction.createLoad(result, array, index)
ArrayAccessInstruction.createStore(array, index, value)

// Check mode
instr.isLoad()   // true for array reads
instr.isStore()  // true for array writes

// Access properties
instr.getArray()  // Value: array reference
instr.getIndex()  // Value: array index
instr.getValue()  // Value: stored value (null if load)
```

### TypeCheckInstruction

Combines `CastInstruction` and `InstanceOfInstruction`:

```java
// Factory methods
TypeCheckInstruction.createCast(result, operand, targetType)
TypeCheckInstruction.createInstanceOf(result, operand, checkType)

// Check operation type
instr.isCast()       // true for cast operations
instr.isInstanceOf() // true for instanceof checks

// Access properties
instr.getOperand()    // Value: the value being checked/cast
instr.getTargetType() // IRType: target type for cast or check type for instanceof
```

### SimpleInstruction

Combines simple opcodes with minimal operands:

```java
// Factory methods
SimpleInstruction.createArrayLength(result, array)
SimpleInstruction.createMonitorEnter(objectRef)
SimpleInstruction.createMonitorExit(objectRef)
SimpleInstruction.createThrow(exception)
SimpleInstruction.createGoto(targetBlock)

// Check operation type
instr.getOp() == SimpleOp.ARRAYLENGTH
instr.getOp() == SimpleOp.MONITORENTER
instr.getOp() == SimpleOp.MONITOREXIT
instr.getOp() == SimpleOp.ATHROW
instr.getOp() == SimpleOp.GOTO

// Access properties
instr.getOperand()  // Value: operand for ARRAYLENGTH, MONITOR*, ATHROW
instr.getTarget()   // IRBlock: target for GOTO
```

## IRVisitor Changes

### Removed Visitor Methods

```java
// These methods are removed from IRVisitor interface:
T visitGetField(GetFieldInstruction getField);
T visitPutField(PutFieldInstruction putField);
T visitArrayLoad(ArrayLoadInstruction arrayLoad);
T visitArrayStore(ArrayStoreInstruction arrayStore);
T visitCast(CastInstruction cast);
T visitInstanceOf(InstanceOfInstruction instanceOf);
T visitArrayLength(ArrayLengthInstruction arrayLength);
T visitGoto(GotoInstruction gotoInstr);
T visitThrow(ThrowInstruction throwInstr);
T visitMonitorEnter(MonitorEnterInstruction monitorEnter);
T visitMonitorExit(MonitorExitInstruction monitorExit);
```

### New Visitor Methods

```java
// These methods are added to IRVisitor interface:
T visitFieldAccess(FieldAccessInstruction fieldAccess);
T visitArrayAccess(ArrayAccessInstruction arrayAccess);
T visitTypeCheck(TypeCheckInstruction typeCheck);
T visitSimple(SimpleInstruction simple);
```

### Migration Pattern for Visitors

**Before (separate handlers):**
```java
@Override
public Void visitGetField(GetFieldInstruction instr) {
    // Handle field read
    return null;
}

@Override
public Void visitPutField(PutFieldInstruction instr) {
    // Handle field write
    return null;
}
```

**After (combined handler):**
```java
@Override
public Void visitFieldAccess(FieldAccessInstruction instr) {
    if (instr.isLoad()) {
        // Handle field read
    } else {
        // Handle field write
    }
    return null;
}
```

## Migration Status

**Migration Complete**: All old instruction types have been removed from the codebase. Only the new consolidated types are available:

```java
// Field access (replaces GetFieldInstruction/PutFieldInstruction)
if (instr instanceof FieldAccessInstruction) {
    FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
    if (fieldAccess.isLoad()) {
        // Handle field read
    } else {
        // Handle field write
    }
}
```

## Migration Examples

### Example 1: Processing Field Accesses

**Before:**
```java
for (IRInstruction instr : block.getInstructions()) {
    if (instr instanceof GetFieldInstruction) {
        GetFieldInstruction getField = (GetFieldInstruction) instr;
        String fieldName = getField.getName();
        // Process field read
    }
    if (instr instanceof PutFieldInstruction) {
        PutFieldInstruction putField = (PutFieldInstruction) instr;
        Value storedValue = putField.getValue();
        // Process field write
    }
}
```

**After:**
```java
for (IRInstruction instr : block.getInstructions()) {
    if (instr instanceof FieldAccessInstruction) {
        FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
        String fieldName = fieldAccess.getName();
        if (fieldAccess.isLoad()) {
            // Process field read
        } else {
            Value storedValue = fieldAccess.getValue();
            // Process field write
        }
    }
}
```

### Example 2: Creating Field Instructions

**Before:**
```java
// Instance field read
GetFieldInstruction getField = new GetFieldInstruction(
    result, owner, name, descriptor, objectRef);

// Static field write
PutFieldInstruction putField = new PutFieldInstruction(
    owner, name, descriptor, value);
```

**After:**
```java
// Instance field read
FieldAccessInstruction getField = FieldAccessInstruction.createLoad(
    result, owner, name, descriptor, objectRef);

// Static field write
FieldAccessInstruction putField = FieldAccessInstruction.createStaticStore(
    owner, name, descriptor, value);
```

### Example 3: Handling Control Flow

**Before:**
```java
IRInstruction terminator = block.getTerminator();
if (terminator instanceof GotoInstruction) {
    GotoInstruction gotoInstr = (GotoInstruction) terminator;
    IRBlock target = gotoInstr.getTarget();
}
if (terminator instanceof ThrowInstruction) {
    ThrowInstruction throwInstr = (ThrowInstruction) terminator;
    Value exception = throwInstr.getException();
}
```

**After:**
```java
IRInstruction terminator = block.getTerminator();
if (terminator instanceof SimpleInstruction) {
    SimpleInstruction simple = (SimpleInstruction) terminator;
    if (simple.getOp() == SimpleOp.GOTO) {
        IRBlock target = simple.getTarget();
    }
    if (simple.getOp() == SimpleOp.ATHROW) {
        Value exception = simple.getOperand();
    }
}
```

## API Method Mappings

### GetFieldInstruction → FieldAccessInstruction

| Old Method | New Method |
|------------|------------|
| `getOwner()` | `getOwner()` |
| `getName()` | `getName()` |
| `getDescriptor()` | `getDescriptor()` |
| `isStatic()` | `isStatic()` |
| `getObjectRef()` | `getObjectRef()` |
| N/A | `isLoad()` (returns true) |

### PutFieldInstruction → FieldAccessInstruction

| Old Method | New Method |
|------------|------------|
| `getOwner()` | `getOwner()` |
| `getName()` | `getName()` |
| `getDescriptor()` | `getDescriptor()` |
| `isStatic()` | `isStatic()` |
| `getObjectRef()` | `getObjectRef()` |
| `getValue()` | `getValue()` |
| N/A | `isStore()` (returns true) |

### CastInstruction → TypeCheckInstruction

| Old Method | New Method |
|------------|------------|
| `getObjectRef()` | `getOperand()` |
| `getTargetType()` | `getTargetType()` |
| N/A | `isCast()` (returns true) |

### InstanceOfInstruction → TypeCheckInstruction

| Old Method | New Method |
|------------|------------|
| `getObjectRef()` | `getOperand()` |
| `getCheckType()` | `getTargetType()` |
| N/A | `isInstanceOf()` (returns true) |

### ThrowInstruction → SimpleInstruction

| Old Method | New Method |
|------------|------------|
| `getException()` | `getOperand()` |
| N/A | `getOp()` returns `SimpleOp.ATHROW` |

### GotoInstruction → SimpleInstruction

| Old Method | New Method |
|------------|------------|
| `getTarget()` | `getTarget()` |
| N/A | `getOp()` returns `SimpleOp.GOTO` |

### ArrayLengthInstruction → SimpleInstruction

| Old Method | New Method |
|------------|------------|
| `getArray()` | `getOperand()` |
| N/A | `getOp()` returns `SimpleOp.ARRAYLENGTH` |

## Files Summary

### New Files Created
- `com/tonic/analysis/ssa/ir/AccessMode.java`
- `com/tonic/analysis/ssa/ir/TypeCheckOp.java`
- `com/tonic/analysis/ssa/ir/SimpleOp.java`
- `com/tonic/analysis/ssa/ir/FieldAccessInstruction.java`
- `com/tonic/analysis/ssa/ir/ArrayAccessInstruction.java`
- `com/tonic/analysis/ssa/ir/TypeCheckInstruction.java`
- `com/tonic/analysis/ssa/ir/SimpleInstruction.java`

### Deleted Files (migration complete)
The following instruction classes have been removed:
- `GetFieldInstruction.java` → replaced by `FieldAccessInstruction`
- `PutFieldInstruction.java` → replaced by `FieldAccessInstruction`
- `ArrayLoadInstruction.java` → replaced by `ArrayAccessInstruction`
- `ArrayStoreInstruction.java` → replaced by `ArrayAccessInstruction`
- `CastInstruction.java` → replaced by `TypeCheckInstruction`
- `InstanceOfInstruction.java` → replaced by `TypeCheckInstruction`
- `ArrayLengthInstruction.java` → replaced by `SimpleInstruction`
- `GotoInstruction.java` → replaced by `SimpleInstruction`
- `ThrowInstruction.java` → replaced by `SimpleInstruction`
- `MonitorEnterInstruction.java` → replaced by `SimpleInstruction`
- `MonitorExitInstruction.java` → replaced by `SimpleInstruction`

## Testing

After migrating code to use new instruction types:

1. Run full test suite: `./gradlew test`
2. Verify all tests pass (8870+ tests)
3. Check code coverage report at: `build/reports/jacoco/test/html/index.html`

## Notes

- The `LoadLocalInstruction` and `StoreLocalInstruction` classes remain for use during bytecode lifting, but should not appear in final SSA form
- **Migration is complete** - old instruction types have been removed from the codebase
- Use factory methods (e.g., `createLoad()`, `createStore()`) rather than constructors for new instruction types
