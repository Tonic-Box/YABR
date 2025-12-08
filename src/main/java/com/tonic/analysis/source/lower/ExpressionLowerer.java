package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowers AST Expression nodes to IR instructions.
 * Returns the SSAValue representing the result of the expression.
 */
public class ExpressionLowerer {

    private final LoweringContext ctx;

    public ExpressionLowerer(LoweringContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Lowers an expression and returns the SSAValue result.
     */
    public Value lower(Expression expr) {
        if (expr instanceof LiteralExpr) {
            return lowerLiteral((LiteralExpr) expr);
        } else if (expr instanceof VarRefExpr) {
            return lowerVarRef((VarRefExpr) expr);
        } else if (expr instanceof BinaryExpr) {
            return lowerBinary((BinaryExpr) expr);
        } else if (expr instanceof UnaryExpr) {
            return lowerUnary((UnaryExpr) expr);
        } else if (expr instanceof MethodCallExpr) {
            return lowerMethodCall((MethodCallExpr) expr);
        } else if (expr instanceof FieldAccessExpr) {
            return lowerFieldAccess((FieldAccessExpr) expr);
        } else if (expr instanceof ArrayAccessExpr) {
            return lowerArrayAccess((ArrayAccessExpr) expr);
        } else if (expr instanceof NewExpr) {
            return lowerNew((NewExpr) expr);
        } else if (expr instanceof NewArrayExpr) {
            return lowerNewArray((NewArrayExpr) expr);
        } else if (expr instanceof CastExpr) {
            return lowerCast((CastExpr) expr);
        } else if (expr instanceof TernaryExpr) {
            return lowerTernary((TernaryExpr) expr);
        } else if (expr instanceof InstanceOfExpr) {
            return lowerInstanceOf((InstanceOfExpr) expr);
        } else if (expr instanceof ThisExpr) {
            return lowerThis((ThisExpr) expr);
        } else if (expr instanceof ArrayInitExpr) {
            return lowerArrayInit((ArrayInitExpr) expr);
        } else {
            throw new LoweringException("Unsupported expression type: " + expr.getClass().getSimpleName());
        }
    }

    private Value lowerLiteral(LiteralExpr lit) {
        Constant constant = toConstant(lit.getValue(), lit.getType());

        // Create SSA value and instruction
        IRType irType = lit.getType().toIRType();
        SSAValue result = ctx.newValue(irType);
        ConstantInstruction instr = new ConstantInstruction(result, constant);
        ctx.getCurrentBlock().addInstruction(instr);

        return result;
    }

    private Constant toConstant(Object value, SourceType type) {
        if (value == null) {
            return NullConstant.INSTANCE;
        }

        if (type instanceof PrimitiveSourceType) {
            PrimitiveSourceType prim = (PrimitiveSourceType) type;
            if (prim == PrimitiveSourceType.BOOLEAN) {
                return IntConstant.of(((Boolean) value) ? 1 : 0);
            } else if (prim == PrimitiveSourceType.BYTE || prim == PrimitiveSourceType.CHAR ||
                       prim == PrimitiveSourceType.SHORT || prim == PrimitiveSourceType.INT) {
                return IntConstant.of(((Number) value).intValue());
            } else if (prim == PrimitiveSourceType.LONG) {
                return new LongConstant(((Number) value).longValue());
            } else if (prim == PrimitiveSourceType.FLOAT) {
                return new FloatConstant(((Number) value).floatValue());
            } else if (prim == PrimitiveSourceType.DOUBLE) {
                return new DoubleConstant(((Number) value).doubleValue());
            }
        }

        if (value instanceof String) {
            return new StringConstant((String) value);
        }

        throw new LoweringException("Cannot convert value to constant: " + value);
    }

    private Value lowerVarRef(VarRefExpr var) {
        // If the VarRefExpr has an attached SSAValue, use it directly
        if (var.getSsaValue() != null) {
            return var.getSsaValue();
        }

        // Otherwise look up by name in context
        return ctx.getVariable(var.getName());
    }

    private Value lowerBinary(BinaryExpr bin) {
        BinaryOperator op = bin.getOperator();

        // Handle assignment
        if (op == BinaryOperator.ASSIGN) {
            return lowerAssignment(bin);
        }

        // Handle compound assignment (+=, -=, etc.)
        if (op.isAssignment()) {
            return lowerCompoundAssignment(bin);
        }

        // Handle short-circuit logical operators
        if (op == BinaryOperator.AND || op == BinaryOperator.OR) {
            return lowerShortCircuit(bin);
        }

        // Handle comparison operators
        if (op.isComparison()) {
            return lowerComparison(bin);
        }

        // Handle arithmetic/bitwise operators
        Value left = lower(bin.getLeft());
        Value right = lower(bin.getRight());

        BinaryOp irOp = ReverseOperatorMapper.toIRBinaryOp(op);
        if (irOp == null) {
            throw new LoweringException("No IR binary op for: " + op);
        }

        IRType resultType = bin.getType().toIRType();
        SSAValue result = ctx.newValue(resultType);
        BinaryOpInstruction instr = new BinaryOpInstruction(result, irOp, left, right);
        ctx.getCurrentBlock().addInstruction(instr);

        return result;
    }

    private Value lowerAssignment(BinaryExpr bin) {
        Value rhs = lower(bin.getRight());

        Expression left = bin.getLeft();
        if (left instanceof VarRefExpr) {
            VarRefExpr varRef = (VarRefExpr) left;
            // Update variable in context
            ctx.setVariable(varRef.getName(), (SSAValue) rhs);
            return rhs;
        } else if (left instanceof FieldAccessExpr) {
            return lowerFieldStore((FieldAccessExpr) left, rhs);
        } else if (left instanceof ArrayAccessExpr) {
            return lowerArrayStore((ArrayAccessExpr) left, rhs);
        }

        throw new LoweringException("Invalid assignment target: " + left.getClass().getSimpleName());
    }

    private Value lowerCompoundAssignment(BinaryExpr bin) {
        BinaryOperator baseOp = ReverseOperatorMapper.getBaseOperator(bin.getOperator());
        if (baseOp == null) {
            throw new LoweringException("Unknown compound assignment: " + bin.getOperator());
        }

        // Load current value, apply operation, store result
        Expression left = bin.getLeft();
        Value leftVal = lower(left);
        Value rightVal = lower(bin.getRight());

        BinaryOp irOp = ReverseOperatorMapper.toIRBinaryOp(baseOp);
        IRType resultType = bin.getType().toIRType();
        SSAValue result = ctx.newValue(resultType);
        BinaryOpInstruction instr = new BinaryOpInstruction(result, irOp, leftVal, rightVal);
        ctx.getCurrentBlock().addInstruction(instr);

        // Store back
        if (left instanceof VarRefExpr) {
            ctx.setVariable(((VarRefExpr) left).getName(), result);
        } else if (left instanceof FieldAccessExpr) {
            lowerFieldStore((FieldAccessExpr) left, result);
        } else if (left instanceof ArrayAccessExpr) {
            lowerArrayStore((ArrayAccessExpr) left, result);
        }

        return result;
    }

    private Value lowerShortCircuit(BinaryExpr bin) {
        boolean isAnd = bin.getOperator() == BinaryOperator.AND;

        // Evaluate left operand
        Value left = lower(bin.getLeft());

        // Create blocks for short-circuit evaluation
        IRBlock evalRight = ctx.createBlock();
        IRBlock mergeBlock = ctx.createBlock();

        // Branch based on left value
        CompareOp cmp = isAnd ? CompareOp.IFEQ : CompareOp.IFNE;
        BranchInstruction branch;
        if (isAnd) {
            // AND: if left == 0, skip to merge with false
            branch = new BranchInstruction(cmp, left, mergeBlock, evalRight);
        } else {
            // OR: if left != 0, skip to merge with true
            branch = new BranchInstruction(cmp, left, mergeBlock, evalRight);
        }

        ctx.getCurrentBlock().addInstruction(branch);

        // Evaluate right in evalRight block
        ctx.setCurrentBlock(evalRight);
        Value right = lower(bin.getRight());
        IRBlock rightEndBlock = ctx.getCurrentBlock();
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(mergeBlock));

        // In merge block, create phi to select result
        ctx.setCurrentBlock(mergeBlock);
        SSAValue result = ctx.newValue(PrimitiveType.INT);
        PhiInstruction phi = new PhiInstruction(result);

        // From left evaluation (short-circuit path)
        IntConstant shortCircuitValue = isAnd ? IntConstant.ZERO : IntConstant.ONE;
        phi.addIncoming(shortCircuitValue, ctx.getCurrentBlock());
        phi.addIncoming(right, rightEndBlock);

        mergeBlock.addPhi(phi);

        return result;
    }

    private Value lowerComparison(BinaryExpr bin) {
        Value left = lower(bin.getLeft());
        Value right = lower(bin.getRight());

        // Create blocks for comparison result
        IRBlock trueBlock = ctx.createBlock();
        IRBlock falseBlock = ctx.createBlock();
        IRBlock mergeBlock = ctx.createBlock();

        // Get comparison operation
        CompareOp cmpOp = ReverseOperatorMapper.toCompareOp(bin.getOperator());

        // For non-int types, we need a compare instruction first
        SourceType leftType = bin.getLeft().getType();
        if (leftType == PrimitiveSourceType.LONG) {
            // LCMP produces -1, 0, or 1
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOpInstruction lcmp = new BinaryOpInstruction(cmpResult, BinaryOp.LCMP, left, right);
            ctx.getCurrentBlock().addInstruction(lcmp);

            // Then compare result against 0
            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueBlock, falseBlock);
            ctx.getCurrentBlock().addInstruction(branch);
        } else if (leftType == PrimitiveSourceType.FLOAT) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOp fcmp = ReverseOperatorMapper.getFloatCompareOp(bin.getOperator() == BinaryOperator.GT || bin.getOperator() == BinaryOperator.GE);
            BinaryOpInstruction fcmpInstr = new BinaryOpInstruction(cmpResult, fcmp, left, right);
            ctx.getCurrentBlock().addInstruction(fcmpInstr);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueBlock, falseBlock);
            ctx.getCurrentBlock().addInstruction(branch);
        } else if (leftType == PrimitiveSourceType.DOUBLE) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOp dcmp = ReverseOperatorMapper.getDoubleCompareOp(bin.getOperator() == BinaryOperator.GT || bin.getOperator() == BinaryOperator.GE);
            BinaryOpInstruction dcmpInstr = new BinaryOpInstruction(cmpResult, dcmp, left, right);
            ctx.getCurrentBlock().addInstruction(dcmpInstr);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueBlock, falseBlock);
            ctx.getCurrentBlock().addInstruction(branch);
        } else {
            // Integer comparison - direct branch
            BranchInstruction branch = new BranchInstruction(cmpOp, left, right, trueBlock, falseBlock);
            ctx.getCurrentBlock().addInstruction(branch);
        }

        // True block: set result to 1
        ctx.setCurrentBlock(trueBlock);
        SSAValue trueVal = ctx.newValue(PrimitiveType.INT);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(trueVal, IntConstant.ONE));
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(mergeBlock));

        // False block: set result to 0
        ctx.setCurrentBlock(falseBlock);
        SSAValue falseVal = ctx.newValue(PrimitiveType.INT);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(falseVal, IntConstant.ZERO));
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(mergeBlock));

        // Merge block with phi
        ctx.setCurrentBlock(mergeBlock);
        SSAValue result = ctx.newValue(PrimitiveType.INT);
        PhiInstruction phi = new PhiInstruction(result);
        phi.addIncoming(trueVal, trueBlock);
        phi.addIncoming(falseVal, falseBlock);
        mergeBlock.addPhi(phi);

        return result;
    }

    private Value lowerUnary(UnaryExpr unary) {
        UnaryOperator op = unary.getOperator();
        Value operand = lower(unary.getOperand());

        if (op == UnaryOperator.NEG) {
            IRType resultType = unary.getType().toIRType();
            SSAValue result = ctx.newValue(resultType);
            UnaryOpInstruction instr = new UnaryOpInstruction(result, UnaryOp.NEG, operand);
            ctx.getCurrentBlock().addInstruction(instr);
            return result;
        } else if (op == UnaryOperator.POS) {
            // No-op, return operand directly
            return operand;
        } else if (op == UnaryOperator.BNOT) {
            // Bitwise NOT: x ^ -1
            IRType resultType = unary.getType().toIRType();
            SSAValue minusOne = ctx.newValue(resultType);
            ctx.getCurrentBlock().addInstruction(new ConstantInstruction(minusOne, IntConstant.MINUS_ONE));

            SSAValue result = ctx.newValue(resultType);
            ctx.getCurrentBlock().addInstruction(new BinaryOpInstruction(result, BinaryOp.XOR, operand, minusOne));
            return result;
        } else if (op == UnaryOperator.NOT) {
            // Logical NOT: compare with 0, invert result
            IRBlock trueBlock = ctx.createBlock();
            IRBlock falseBlock = ctx.createBlock();
            IRBlock mergeBlock = ctx.createBlock();

            BranchInstruction branch = new BranchInstruction(CompareOp.IFEQ, operand, trueBlock, falseBlock);
            ctx.getCurrentBlock().addInstruction(branch);

            // operand == 0 -> result is 1 (true)
            ctx.setCurrentBlock(trueBlock);
            SSAValue trueVal = ctx.newValue(PrimitiveType.INT);
            ctx.getCurrentBlock().addInstruction(new ConstantInstruction(trueVal, IntConstant.ONE));
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(mergeBlock));

            // operand != 0 -> result is 0 (false)
            ctx.setCurrentBlock(falseBlock);
            SSAValue falseVal = ctx.newValue(PrimitiveType.INT);
            ctx.getCurrentBlock().addInstruction(new ConstantInstruction(falseVal, IntConstant.ZERO));
            ctx.getCurrentBlock().addInstruction(new GotoInstruction(mergeBlock));

            ctx.setCurrentBlock(mergeBlock);
            SSAValue result = ctx.newValue(PrimitiveType.INT);
            PhiInstruction phi = new PhiInstruction(result);
            phi.addIncoming(trueVal, trueBlock);
            phi.addIncoming(falseVal, falseBlock);
            mergeBlock.addPhi(phi);

            return result;
        } else if (op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC) {
            // ++x or --x: add/sub 1, store, return new value
            return lowerIncDec(unary, true);
        } else if (op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC) {
            // x++ or x--: save old value, add/sub 1, store, return old value
            return lowerIncDec(unary, false);
        } else {
            throw new LoweringException("Unsupported unary operator: " + op);
        }
    }

    private Value lowerIncDec(UnaryExpr unary, boolean isPrefix) {
        UnaryOperator op = unary.getOperator();
        boolean isInc = (op == UnaryOperator.PRE_INC || op == UnaryOperator.POST_INC);

        Expression operand = unary.getOperand();
        Value oldValue = lower(operand);

        // Create the increment/decrement
        IRType type = unary.getType().toIRType();
        SSAValue one = ctx.newValue(type);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(one, IntConstant.ONE));

        SSAValue newValue = ctx.newValue(type);
        BinaryOp binOp = isInc ? BinaryOp.ADD : BinaryOp.SUB;
        ctx.getCurrentBlock().addInstruction(new BinaryOpInstruction(newValue, binOp, oldValue, one));

        // Store back
        if (operand instanceof VarRefExpr) {
            ctx.setVariable(((VarRefExpr) operand).getName(), newValue);
        } else if (operand instanceof FieldAccessExpr) {
            lowerFieldStore((FieldAccessExpr) operand, newValue);
        } else if (operand instanceof ArrayAccessExpr) {
            lowerArrayStore((ArrayAccessExpr) operand, newValue);
        }

        return isPrefix ? newValue : oldValue;
    }

    private Value lowerMethodCall(MethodCallExpr call) {
        // Determine invoke type and gather arguments
        InvokeType invokeType;
        List<Value> args = new ArrayList<>();

        if (call.isStatic()) {
            invokeType = InvokeType.STATIC;
        } else {
            // Instance call - first argument is receiver
            Expression receiver = call.getReceiver();
            if (receiver != null) {
                args.add(lower(receiver));
            } else {
                // Implicit this
                args.add(ctx.getVariable("this"));
            }
            invokeType = InvokeType.VIRTUAL;
        }

        // Lower arguments
        for (Expression arg : call.getArguments()) {
            args.add(lower(arg));
        }

        // Build method descriptor from argument types
        String descriptor = buildMethodDescriptor(call);

        // Create result value if not void
        IRType returnType = call.getType().toIRType();
        SSAValue result = null;
        if (!(call.getType() instanceof com.tonic.analysis.source.ast.type.VoidSourceType)) {
            result = ctx.newValue(returnType);
        }

        InvokeInstruction instr = new InvokeInstruction(
            result, invokeType,
            call.getOwnerClass(),
            call.getMethodName(),
            descriptor,
            args
        );
        ctx.getCurrentBlock().addInstruction(instr);

        return result != null ? result : NullConstant.INSTANCE;
    }

    private String buildMethodDescriptor(MethodCallExpr call) {
        StringBuilder sb = new StringBuilder("(");
        for (Expression arg : call.getArguments()) {
            sb.append(arg.getType().toIRType().getDescriptor());
        }
        sb.append(")");
        sb.append(call.getType().toIRType().getDescriptor());
        return sb.toString();
    }

    private Value lowerFieldAccess(FieldAccessExpr field) {
        IRType fieldType = field.getType().toIRType();
        SSAValue result = ctx.newValue(fieldType);
        String descriptor = fieldType.getDescriptor();

        if (field.isStatic()) {
            GetFieldInstruction instr = new GetFieldInstruction(
                result, field.getOwnerClass(), field.getFieldName(), descriptor
            );
            ctx.getCurrentBlock().addInstruction(instr);
        } else {
            Expression receiver = field.getReceiver();
            Value receiverVal = receiver != null ? lower(receiver) : ctx.getVariable("this");
            GetFieldInstruction instr = new GetFieldInstruction(
                result, field.getOwnerClass(), field.getFieldName(), descriptor, receiverVal
            );
            ctx.getCurrentBlock().addInstruction(instr);
        }

        return result;
    }

    private Value lowerFieldStore(FieldAccessExpr field, Value value) {
        String descriptor = field.getType().toIRType().getDescriptor();

        if (field.isStatic()) {
            PutFieldInstruction instr = new PutFieldInstruction(
                field.getOwnerClass(), field.getFieldName(), descriptor, value
            );
            ctx.getCurrentBlock().addInstruction(instr);
        } else {
            Expression receiver = field.getReceiver();
            Value receiverVal = receiver != null ? lower(receiver) : ctx.getVariable("this");
            PutFieldInstruction instr = new PutFieldInstruction(
                field.getOwnerClass(), field.getFieldName(), descriptor, receiverVal, value
            );
            ctx.getCurrentBlock().addInstruction(instr);
        }

        return value;
    }

    private Value lowerArrayAccess(ArrayAccessExpr arr) {
        Value array = lower(arr.getArray());
        Value index = lower(arr.getIndex());

        IRType elementType = arr.getType().toIRType();
        SSAValue result = ctx.newValue(elementType);

        ArrayLoadInstruction instr = new ArrayLoadInstruction(result, array, index);
        ctx.getCurrentBlock().addInstruction(instr);

        return result;
    }

    private Value lowerArrayStore(ArrayAccessExpr arr, Value value) {
        Value array = lower(arr.getArray());
        Value index = lower(arr.getIndex());

        ArrayStoreInstruction instr = new ArrayStoreInstruction(array, index, value);
        ctx.getCurrentBlock().addInstruction(instr);

        return value;
    }

    private Value lowerNew(NewExpr newExpr) {
        String className = newExpr.getClassName();
        IRType type = new ReferenceType(className);
        SSAValue result = ctx.newValue(type);

        // NEW instruction allocates the object
        NewInstruction newInstr = new NewInstruction(result, className);
        ctx.getCurrentBlock().addInstruction(newInstr);

        // DUP and invoke <init> constructor
        List<Value> args = new ArrayList<>();
        args.add(result); // receiver for <init>

        for (Expression arg : newExpr.getArguments()) {
            args.add(lower(arg));
        }

        // Build constructor descriptor
        StringBuilder descBuilder = new StringBuilder("(");
        for (Expression arg : newExpr.getArguments()) {
            descBuilder.append(arg.getType().toIRType().getDescriptor());
        }
        descBuilder.append(")V");

        InvokeInstruction initInstr = new InvokeInstruction(
            InvokeType.SPECIAL, className, "<init>", descBuilder.toString(), args
        );
        ctx.getCurrentBlock().addInstruction(initInstr);

        return result;
    }

    private Value lowerNewArray(NewArrayExpr newArr) {
        // Get dimensions
        List<Value> dims = new ArrayList<>();
        for (Expression dim : newArr.getDimensions()) {
            dims.add(lower(dim));
        }

        IRType elementType = getElementType(newArr.getType());
        IRType arrayType = newArr.getType().toIRType();
        SSAValue result = ctx.newValue(arrayType);

        NewArrayInstruction instr = new NewArrayInstruction(result, elementType, dims);
        ctx.getCurrentBlock().addInstruction(instr);

        return result;
    }

    private IRType getElementType(SourceType arrayType) {
        if (arrayType instanceof com.tonic.analysis.source.ast.type.ArraySourceType) {
            com.tonic.analysis.source.ast.type.ArraySourceType arr =
                (com.tonic.analysis.source.ast.type.ArraySourceType) arrayType;
            return arr.getElementType().toIRType();
        }
        throw new LoweringException("Expected array type: " + arrayType);
    }

    private Value lowerCast(CastExpr cast) {
        Value operand = lower(cast.getExpression());
        SourceType fromType = cast.getExpression().getType();
        SourceType toType = cast.getTargetType();

        // Check for primitive cast
        UnaryOp castOp = ReverseOperatorMapper.getCastOp(fromType, toType);
        if (castOp != null) {
            IRType resultType = toType.toIRType();
            SSAValue result = ctx.newValue(resultType);
            UnaryOpInstruction instr = new UnaryOpInstruction(result, castOp, operand);
            ctx.getCurrentBlock().addInstruction(instr);
            return result;
        }

        // Reference cast (checkcast)
        if (toType instanceof ReferenceSourceType) {
            ReferenceSourceType refType = (ReferenceSourceType) toType;
            IRType resultType = toType.toIRType();
            SSAValue result = ctx.newValue(resultType);
            CastInstruction instr = new CastInstruction(result, operand, resultType);
            ctx.getCurrentBlock().addInstruction(instr);
            return result;
        }

        // No cast needed (widening that doesn't require instruction)
        return operand;
    }

    private Value lowerTernary(TernaryExpr ternary) {
        Value condition = lower(ternary.getCondition());

        IRBlock thenBlock = ctx.createBlock();
        IRBlock elseBlock = ctx.createBlock();
        IRBlock mergeBlock = ctx.createBlock();

        BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, condition, thenBlock, elseBlock);
        ctx.getCurrentBlock().addInstruction(branch);

        // Then branch
        ctx.setCurrentBlock(thenBlock);
        Value thenVal = lower(ternary.getThenExpr());
        IRBlock thenEndBlock = ctx.getCurrentBlock();
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(mergeBlock));

        // Else branch
        ctx.setCurrentBlock(elseBlock);
        Value elseVal = lower(ternary.getElseExpr());
        IRBlock elseEndBlock = ctx.getCurrentBlock();
        ctx.getCurrentBlock().addInstruction(new GotoInstruction(mergeBlock));

        // Merge with phi
        ctx.setCurrentBlock(mergeBlock);
        IRType resultType = ternary.getType().toIRType();
        SSAValue result = ctx.newValue(resultType);
        PhiInstruction phi = new PhiInstruction(result);
        phi.addIncoming(thenVal, thenEndBlock);
        phi.addIncoming(elseVal, elseEndBlock);
        mergeBlock.addPhi(phi);

        return result;
    }

    private Value lowerInstanceOf(InstanceOfExpr inst) {
        Value operand = lower(inst.getExpression());

        IRType checkType;
        if (inst.getCheckType() instanceof ReferenceSourceType) {
            checkType = inst.getCheckType().toIRType();
        } else {
            throw new LoweringException("instanceof requires reference type");
        }

        SSAValue result = ctx.newValue(PrimitiveType.INT);
        InstanceOfInstruction instr = new InstanceOfInstruction(result, operand, checkType);
        ctx.getCurrentBlock().addInstruction(instr);

        return result;
    }

    private Value lowerThis(ThisExpr thisExpr) {
        return ctx.getVariable("this");
    }

    private Value lowerArrayInit(ArrayInitExpr arrInit) {
        // Create array with size
        int size = arrInit.getElements().size();
        IRType elementType = getElementType(arrInit.getType());
        IRType arrayType = arrInit.getType().toIRType();

        SSAValue sizeVal = ctx.newValue(PrimitiveType.INT);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(sizeVal, IntConstant.of(size)));

        SSAValue result = ctx.newValue(arrayType);
        NewArrayInstruction newArr = new NewArrayInstruction(result, elementType, List.of(sizeVal));
        ctx.getCurrentBlock().addInstruction(newArr);

        // Store each element
        int i = 0;
        for (Expression elem : arrInit.getElements()) {
            Value elemVal = lower(elem);
            SSAValue indexVal = ctx.newValue(PrimitiveType.INT);
            ctx.getCurrentBlock().addInstruction(new ConstantInstruction(indexVal, IntConstant.of(i)));
            ctx.getCurrentBlock().addInstruction(new ArrayStoreInstruction(result, indexVal, elemVal));
            i++;
        }

        return result;
    }
}
