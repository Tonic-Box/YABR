package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.ArraySourceType;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.ast.type.VoidSourceType;
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

    /**
     * Creates a new expression lowerer.
     *
     * @param ctx the lowering context
     */
    public ExpressionLowerer(LoweringContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Lowers a condition expression for control flow (if/while/for).
     * Creates a branch instruction directly without creating extra blocks.
     *
     * @param condition the condition expression
     * @param trueTarget block to branch to if condition is true
     * @param falseTarget block to branch to if condition is false
     */
    public void lowerCondition(Expression condition, IRBlock trueTarget, IRBlock falseTarget) {
        if (condition instanceof BinaryExpr) {
            BinaryExpr bin = (BinaryExpr) condition;
            if (bin.getOperator().isComparison()) {
                lowerComparisonForControlFlow(bin, trueTarget, falseTarget);
                return;
            }
        }

        Value cond = lower(condition);
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, cond, trueTarget, falseTarget);
        ctx.getCurrentBlock().addInstruction(branch);
        ctx.getCurrentBlock().addSuccessor(trueTarget, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        ctx.getCurrentBlock().addSuccessor(falseTarget, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
    }

    private void lowerComparisonForControlFlow(BinaryExpr bin, IRBlock trueTarget, IRBlock falseTarget) {
        Value left = lower(bin.getLeft());
        Value right = lower(bin.getRight());

        CompareOp cmpOp = ReverseOperatorMapper.toCompareOp(bin.getOperator());
        IRBlock currentBlock = ctx.getCurrentBlock();

        SourceType leftType = bin.getLeft().getType();
        if (leftType == PrimitiveSourceType.LONG) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOpInstruction lcmp = new BinaryOpInstruction(cmpResult, BinaryOp.LCMP, left, right);
            currentBlock.addInstruction(lcmp);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueTarget, falseTarget);
            currentBlock.addInstruction(branch);
        } else if (leftType == PrimitiveSourceType.FLOAT) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOp fcmp = ReverseOperatorMapper.getFloatCompareOp(bin.getOperator() == BinaryOperator.GT || bin.getOperator() == BinaryOperator.GE);
            BinaryOpInstruction fcmpInstr = new BinaryOpInstruction(cmpResult, fcmp, left, right);
            currentBlock.addInstruction(fcmpInstr);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueTarget, falseTarget);
            currentBlock.addInstruction(branch);
        } else if (leftType == PrimitiveSourceType.DOUBLE) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOp dcmp = ReverseOperatorMapper.getDoubleCompareOp(bin.getOperator() == BinaryOperator.GT || bin.getOperator() == BinaryOperator.GE);
            BinaryOpInstruction dcmpInstr = new BinaryOpInstruction(cmpResult, dcmp, left, right);
            currentBlock.addInstruction(dcmpInstr);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueTarget, falseTarget);
            currentBlock.addInstruction(branch);
        } else {
            BranchInstruction branch = new BranchInstruction(cmpOp, left, right, trueTarget, falseTarget);
            currentBlock.addInstruction(branch);
        }

        currentBlock.addSuccessor(trueTarget, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        currentBlock.addSuccessor(falseTarget, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
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
            return lowerThis();
        } else if (expr instanceof ArrayInitExpr) {
            return lowerArrayInit((ArrayInitExpr) expr);
        } else {
            throw new LoweringException("Unsupported expression type: " + expr.getClass().getSimpleName());
        }
    }

    private Value lowerLiteral(LiteralExpr lit) {
        Constant constant = toConstant(lit.getValue(), lit.getType());

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
        if (var.getSsaValue() != null) {
            return var.getSsaValue();
        }
        return ctx.getVariable(var.getName());
    }

    private Value lowerBinary(BinaryExpr bin) {
        BinaryOperator op = bin.getOperator();

        if (op == BinaryOperator.ASSIGN) {
            return lowerAssignment(bin);
        }

        if (op.isAssignment()) {
            return lowerCompoundAssignment(bin);
        }

        if (op == BinaryOperator.AND || op == BinaryOperator.OR) {
            return lowerShortCircuit(bin);
        }

        if (op.isComparison()) {
            return lowerComparison(bin);
        }

        if (op == BinaryOperator.ADD && isStringType(bin.getType())) {
            return lowerStringConcat(bin.getLeft(), bin.getRight());
        }

        Value left = lower(bin.getLeft());
        Value right = lower(bin.getRight());

        BinaryOp irOp = ReverseOperatorMapper.toIRBinaryOp(op);
        if (irOp == null) {
            throw new LoweringException("No IR binary op for: " + op);
        }

        IRType resultType = bin.getType().toIRType();
        left = widenIfNeeded(left, resultType);
        right = widenIfNeeded(right, resultType);

        SSAValue result = ctx.newValue(resultType);
        BinaryOpInstruction instr = new BinaryOpInstruction(result, irOp, left, right);
        ctx.getCurrentBlock().addInstruction(instr);

        return result;
    }

    private boolean isStringType(SourceType type) {
        if (type instanceof ReferenceSourceType) {
            String name = ((ReferenceSourceType) type).getInternalName();
            return "java/lang/String".equals(name);
        }
        return false;
    }

    private Value lowerStringConcat(Expression leftExpr, Expression rightExpr) {
        List<Object> parts = new ArrayList<>();
        collectConcatParts(leftExpr, parts);
        collectConcatParts(rightExpr, parts);

        StringBuilder recipe = new StringBuilder();
        StringBuilder descriptor = new StringBuilder("(");
        List<Value> dynamicArgs = new ArrayList<>();

        for (Object part : parts) {
            if (part instanceof String) {
                recipe.append((String) part);
            } else if (part instanceof Value) {
                recipe.append('\u0001');
                Value v = (Value) part;
                dynamicArgs.add(v);
                descriptor.append(getDescriptorForValue(v));
            }
        }
        descriptor.append(")Ljava/lang/String;");

        MethodHandleConstant bsm = new MethodHandleConstant(
            MethodHandleConstant.REF_invokeStatic,
            "java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"
        );

        List<Constant> bsArgs = new ArrayList<>();
        bsArgs.add(new StringConstant(recipe.toString()));

        BootstrapMethodInfo bsInfo = new BootstrapMethodInfo(bsm, bsArgs);

        ReferenceType stringType = new ReferenceType("java/lang/String");
        SSAValue result = ctx.newValue(stringType);

        InvokeInstruction indy = new InvokeInstruction(
            result,
            InvokeType.DYNAMIC,
            null,
            "makeConcatWithConstants",
            descriptor.toString(),
            dynamicArgs,
            0,
            bsInfo
        );
        ctx.getCurrentBlock().addInstruction(indy);

        return result;
    }

    private void collectConcatParts(Expression expr, List<Object> parts) {
        if (expr instanceof BinaryExpr) {
            BinaryExpr bin = (BinaryExpr) expr;
            if (bin.getOperator() == BinaryOperator.ADD && isStringType(bin.getType())) {
                collectConcatParts(bin.getLeft(), parts);
                collectConcatParts(bin.getRight(), parts);
                return;
            }
        }
        if (expr instanceof LiteralExpr) {
            LiteralExpr lit = (LiteralExpr) expr;
            if (lit.getValue() instanceof String) {
                parts.add((String) lit.getValue());
                return;
            }
        }
        parts.add(lower(expr));
    }

    private String getDescriptorForValue(Value value) {
        IRType type = value.getType();
        if (type == PrimitiveType.INT) return "I";
        if (type == PrimitiveType.LONG) return "J";
        if (type == PrimitiveType.FLOAT) return "F";
        if (type == PrimitiveType.DOUBLE) return "D";
        if (type == PrimitiveType.BOOLEAN) return "Z";
        if (type == PrimitiveType.CHAR) return "C";
        if (type == PrimitiveType.BYTE) return "B";
        if (type == PrimitiveType.SHORT) return "S";
        if (type instanceof ReferenceType) {
            return "L" + ((ReferenceType) type).getInternalName() + ";";
        }
        return "Ljava/lang/Object;";
    }

    private Value lowerAssignment(BinaryExpr bin) {
        Value rhs = lower(bin.getRight());

        Expression left = bin.getLeft();
        if (left instanceof VarRefExpr) {
            VarRefExpr varRef = (VarRefExpr) left;
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

        Expression left = bin.getLeft();
        Value leftVal = lower(left);
        Value rightVal = lower(bin.getRight());

        BinaryOp irOp = ReverseOperatorMapper.toIRBinaryOp(baseOp);
        IRType resultType = bin.getType().toIRType();
        SSAValue result = ctx.newValue(resultType);
        BinaryOpInstruction instr = new BinaryOpInstruction(result, irOp, leftVal, rightVal);
        ctx.getCurrentBlock().addInstruction(instr);

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

        Value left = lower(bin.getLeft());

        IRBlock shortCircuitBlock = ctx.createBlock();
        IRBlock evalRight = ctx.createBlock();
        IRBlock mergeBlock = ctx.createBlock();

        IRBlock currentBlock = ctx.getCurrentBlock();
        CompareOp cmp = isAnd ? CompareOp.IFEQ : CompareOp.IFNE;
        BranchInstruction branch = new BranchInstruction(cmp, left, shortCircuitBlock, evalRight);
        currentBlock.addInstruction(branch);
        currentBlock.addSuccessor(shortCircuitBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        currentBlock.addSuccessor(evalRight, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(shortCircuitBlock);
        IntConstant shortCircuitValue = isAnd ? IntConstant.ZERO : IntConstant.ONE;
        SSAValue shortCircuitResult = ctx.newValue(PrimitiveType.INT);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(shortCircuitResult, shortCircuitValue));
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(mergeBlock));
        shortCircuitBlock.addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(evalRight);
        Value right = lower(bin.getRight());
        IRBlock rightEndBlock = ctx.getCurrentBlock();
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(mergeBlock));
        rightEndBlock.addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(mergeBlock);
        SSAValue result = ctx.newValue(PrimitiveType.INT);
        PhiInstruction phi = new PhiInstruction(result);
        phi.addIncoming(shortCircuitResult, shortCircuitBlock);
        phi.addIncoming(right, rightEndBlock);
        mergeBlock.addPhi(phi);

        return result;
    }

    private Value lowerComparison(BinaryExpr bin) {
        Value left = lower(bin.getLeft());
        Value right = lower(bin.getRight());

        IRBlock trueBlock = ctx.createBlock();
        IRBlock falseBlock = ctx.createBlock();
        IRBlock mergeBlock = ctx.createBlock();

        CompareOp cmpOp = ReverseOperatorMapper.toCompareOp(bin.getOperator());
        IRBlock currentBlock = ctx.getCurrentBlock();

        SourceType leftType = bin.getLeft().getType();
        if (leftType == PrimitiveSourceType.LONG) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOpInstruction lcmp = new BinaryOpInstruction(cmpResult, BinaryOp.LCMP, left, right);
            currentBlock.addInstruction(lcmp);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueBlock, falseBlock);
            currentBlock.addInstruction(branch);
        } else if (leftType == PrimitiveSourceType.FLOAT) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOp fcmp = ReverseOperatorMapper.getFloatCompareOp(bin.getOperator() == BinaryOperator.GT || bin.getOperator() == BinaryOperator.GE);
            BinaryOpInstruction fcmpInstr = new BinaryOpInstruction(cmpResult, fcmp, left, right);
            currentBlock.addInstruction(fcmpInstr);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueBlock, falseBlock);
            currentBlock.addInstruction(branch);
        } else if (leftType == PrimitiveSourceType.DOUBLE) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOp dcmp = ReverseOperatorMapper.getDoubleCompareOp(bin.getOperator() == BinaryOperator.GT || bin.getOperator() == BinaryOperator.GE);
            BinaryOpInstruction dcmpInstr = new BinaryOpInstruction(cmpResult, dcmp, left, right);
            currentBlock.addInstruction(dcmpInstr);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueBlock, falseBlock);
            currentBlock.addInstruction(branch);
        } else {
            BranchInstruction branch = new BranchInstruction(cmpOp, left, right, trueBlock, falseBlock);
            currentBlock.addInstruction(branch);
        }

        currentBlock.addSuccessor(trueBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        currentBlock.addSuccessor(falseBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(trueBlock);
        SSAValue trueVal = ctx.newValue(PrimitiveType.INT);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(trueVal, IntConstant.ONE));
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(mergeBlock));
        trueBlock.addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(falseBlock);
        SSAValue falseVal = ctx.newValue(PrimitiveType.INT);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(falseVal, IntConstant.ZERO));
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(mergeBlock));
        falseBlock.addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

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
            return operand;
        } else if (op == UnaryOperator.BNOT) {
            IRType resultType = unary.getType().toIRType();
            SSAValue minusOne = ctx.newValue(resultType);
            ctx.getCurrentBlock().addInstruction(new ConstantInstruction(minusOne, IntConstant.MINUS_ONE));

            SSAValue result = ctx.newValue(resultType);
            ctx.getCurrentBlock().addInstruction(new BinaryOpInstruction(result, BinaryOp.XOR, operand, minusOne));
            return result;
        } else if (op == UnaryOperator.NOT) {
            IRBlock trueBlock = ctx.createBlock();
            IRBlock falseBlock = ctx.createBlock();
            IRBlock mergeBlock = ctx.createBlock();

            IRBlock currentBlock = ctx.getCurrentBlock();
            BranchInstruction branch = new BranchInstruction(CompareOp.IFEQ, operand, trueBlock, falseBlock);
            currentBlock.addInstruction(branch);
            currentBlock.addSuccessor(trueBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
            currentBlock.addSuccessor(falseBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

            ctx.setCurrentBlock(trueBlock);
            SSAValue trueVal = ctx.newValue(PrimitiveType.INT);
            ctx.getCurrentBlock().addInstruction(new ConstantInstruction(trueVal, IntConstant.ONE));
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(mergeBlock));
            trueBlock.addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

            ctx.setCurrentBlock(falseBlock);
            SSAValue falseVal = ctx.newValue(PrimitiveType.INT);
            ctx.getCurrentBlock().addInstruction(new ConstantInstruction(falseVal, IntConstant.ZERO));
            ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(mergeBlock));
            falseBlock.addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

            ctx.setCurrentBlock(mergeBlock);
            SSAValue result = ctx.newValue(PrimitiveType.INT);
            PhiInstruction phi = new PhiInstruction(result);
            phi.addIncoming(trueVal, trueBlock);
            phi.addIncoming(falseVal, falseBlock);
            mergeBlock.addPhi(phi);

            return result;
        } else if (op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC) {
            return lowerIncDec(unary, true);
        } else if (op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC) {
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

        IRType type = unary.getType().toIRType();
        SSAValue one = ctx.newValue(type);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(one, IntConstant.ONE));

        SSAValue newValue = ctx.newValue(type);
        BinaryOp binOp = isInc ? BinaryOp.ADD : BinaryOp.SUB;
        ctx.getCurrentBlock().addInstruction(new BinaryOpInstruction(newValue, binOp, oldValue, one));

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
        InvokeType invokeType;
        List<Value> args = new ArrayList<>();
        String ownerClass = call.getOwnerClass();

        if (call.isStatic()) {
            invokeType = InvokeType.STATIC;
        } else {
            Expression receiver = call.getReceiver();
            if (receiver instanceof VarRefExpr) {
                VarRefExpr varRef = (VarRefExpr) receiver;
                if (!ctx.hasVariable(varRef.getName())) {
                    invokeType = InvokeType.STATIC;
                    ownerClass = resolveClassName(varRef.getName());
                } else {
                    args.add(lower(receiver));
                    invokeType = InvokeType.VIRTUAL;
                }
            } else if (receiver != null) {
                args.add(lower(receiver));
                invokeType = InvokeType.VIRTUAL;
                if (ownerClass == null || ownerClass.isEmpty()) {
                    ownerClass = resolveReceiverOwnerClass(receiver);
                }
            } else {
                args.add(ctx.getVariable("this"));
                invokeType = InvokeType.VIRTUAL;
            }
        }

        List<Value> loweredArgs = new ArrayList<>();
        for (Expression arg : call.getArguments()) {
            loweredArgs.add(lower(arg));
        }
        args.addAll(loweredArgs);

        List<SourceType> argTypes = new ArrayList<>();
        for (Value arg : loweredArgs) {
            if (arg instanceof SSAValue) {
                argTypes.add(irTypeToSourceType(arg.getType()));
            } else {
                argTypes.add(ReferenceSourceType.OBJECT);
            }
        }

        SourceType returnType = resolveMethodReturnType(call, ownerClass, argTypes);
        String descriptor = buildMethodDescriptorWithReturn(argTypes, returnType);

        SSAValue result = null;
        if (!(returnType instanceof VoidSourceType)) {
            result = ctx.newValue(returnType.toIRType());
        }

        InvokeInstruction instr = new InvokeInstruction(
            result, invokeType,
            ownerClass,
            call.getMethodName(),
            descriptor,
            args
        );
        ctx.getCurrentBlock().addInstruction(instr);

        return result != null ? result : NullConstant.INSTANCE;
    }

    private String resolveReceiverOwnerClass(Expression receiver) {
        if (receiver instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) receiver;
            Expression fieldReceiver = field.getReceiver();
            String fieldOwner;
            if (fieldReceiver instanceof VarRefExpr) {
                VarRefExpr varRef = (VarRefExpr) fieldReceiver;
                if (!ctx.hasVariable(varRef.getName())) {
                    fieldOwner = resolveClassName(varRef.getName());
                } else {
                    return "java/lang/Object";
                }
            } else {
                fieldOwner = field.getOwnerClass();
                if (fieldOwner == null || fieldOwner.isEmpty()) {
                    return "java/lang/Object";
                }
            }
            SourceType fieldType = ctx.getTypeResolver().resolveFieldType(fieldOwner, field.getFieldName());
            if (fieldType instanceof ReferenceSourceType) {
                return ((ReferenceSourceType) fieldType).getInternalName();
            }
        }
        if (receiver instanceof MethodCallExpr) {
            MethodCallExpr methodCall = (MethodCallExpr) receiver;
            String methodOwner = methodCall.getOwnerClass();
            if (methodOwner == null || methodOwner.isEmpty()) {
                Expression methodReceiver = methodCall.getReceiver();
                if (methodReceiver instanceof VarRefExpr) {
                    VarRefExpr varRef = (VarRefExpr) methodReceiver;
                    if (!ctx.hasVariable(varRef.getName())) {
                        methodOwner = resolveClassName(varRef.getName());
                    }
                } else if (methodReceiver != null) {
                    methodOwner = resolveReceiverOwnerClass(methodReceiver);
                }
            }
            if (methodOwner != null && !methodOwner.isEmpty()) {
                List<SourceType> argTypes = new ArrayList<>();
                for (Expression arg : methodCall.getArguments()) {
                    argTypes.add(arg.getType() != null ? arg.getType() : ReferenceSourceType.OBJECT);
                }
                SourceType returnType = ctx.getTypeResolver().resolveMethodReturnType(
                    methodOwner, methodCall.getMethodName(), argTypes);
                if (returnType instanceof ReferenceSourceType) {
                    String internalName = ((ReferenceSourceType) returnType).getInternalName();
                    if (internalName != null && !internalName.isEmpty() && !internalName.equals("java/lang/Object")) {
                        return internalName;
                    }
                }
            }
        }
        SourceType type = receiver.getType();
        if (type instanceof ReferenceSourceType) {
            String internalName = ((ReferenceSourceType) type).getInternalName();
            if (internalName != null && !internalName.isEmpty() && !internalName.equals("java/lang/Object")) {
                return internalName;
            }
        }
        return "java/lang/Object";
    }

    private SourceType resolveMethodReturnType(MethodCallExpr call, String ownerClass, List<SourceType> argTypes) {
        SourceType declaredType = call.getType();
        if (declaredType == ReferenceSourceType.OBJECT || declaredType == null) {
            SourceType resolved = ctx.getTypeResolver().resolveMethodReturnType(ownerClass, call.getMethodName(), argTypes);
            if (resolved != null) {
                return resolved;
            }
        }
        return declaredType != null ? declaredType : ReferenceSourceType.OBJECT;
    }

    private String buildMethodDescriptorWithReturn(List<SourceType> argTypes, SourceType returnType) {
        StringBuilder sb = new StringBuilder("(");
        for (SourceType t : argTypes) {
            sb.append(t.toIRType().getDescriptor());
        }
        sb.append(")");
        sb.append(returnType.toIRType().getDescriptor());
        return sb.toString();
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
        String ownerClass;
        boolean isStatic = field.isStatic();
        Expression receiver = field.getReceiver();

        if (isStatic) {
            ownerClass = field.getOwnerClass();
        } else if (receiver instanceof VarRefExpr) {
            VarRefExpr varRef = (VarRefExpr) receiver;
            if (!ctx.hasVariable(varRef.getName())) {
                ownerClass = resolveClassName(varRef.getName());
                isStatic = true;
            } else {
                ownerClass = field.getOwnerClass();
            }
        } else {
            ownerClass = field.getOwnerClass();
        }

        IRType fieldType = resolveFieldType(field, ownerClass);
        SSAValue result = ctx.newValue(fieldType);
        String descriptor = fieldType.getDescriptor();

        FieldAccessInstruction instr;
        if (isStatic) {
            instr = FieldAccessInstruction.createStaticLoad(result, ownerClass, field.getFieldName(), descriptor);
        } else {
            Value receiverVal = receiver != null ? lower(receiver) : ctx.getVariable("this");
            instr = FieldAccessInstruction.createLoad(result, ownerClass, field.getFieldName(), descriptor, receiverVal);
        }
        ctx.getCurrentBlock().addInstruction(instr);

        return result;
    }

    private IRType resolveFieldType(FieldAccessExpr field, String ownerClass) {
        SourceType declaredType = field.getType();
        if (declaredType == ReferenceSourceType.OBJECT || declaredType == null) {
            SourceType resolved = ctx.getTypeResolver().resolveFieldType(ownerClass, field.getFieldName());
            return resolved.toIRType();
        }
        return declaredType.toIRType();
    }

    private String resolveClassName(String simpleName) {
        return ctx.getTypeResolver().resolveClassName(simpleName);
    }

    private Value lowerFieldStore(FieldAccessExpr field, Value value) {
        String ownerClass;
        boolean isStatic = field.isStatic();
        Expression receiver = field.getReceiver();

        if (isStatic) {
            ownerClass = field.getOwnerClass();
        } else if (receiver instanceof VarRefExpr) {
            VarRefExpr varRef = (VarRefExpr) receiver;
            if (!ctx.hasVariable(varRef.getName())) {
                ownerClass = resolveClassName(varRef.getName());
                isStatic = true;
            } else {
                ownerClass = field.getOwnerClass();
            }
        } else {
            ownerClass = field.getOwnerClass();
        }

        IRType fieldType = resolveFieldType(field, ownerClass);
        String descriptor = fieldType.getDescriptor();

        FieldAccessInstruction instr;
        if (isStatic) {
            instr = FieldAccessInstruction.createStaticStore(ownerClass, field.getFieldName(), descriptor, value);
        } else {
            Value receiverVal = receiver != null ? lower(receiver) : ctx.getVariable("this");
            instr = FieldAccessInstruction.createStore(ownerClass, field.getFieldName(), descriptor, receiverVal, value);
        }
        ctx.getCurrentBlock().addInstruction(instr);

        return value;
    }

    private Value lowerArrayAccess(ArrayAccessExpr arr) {
        Value array = lower(arr.getArray());
        Value index = lower(arr.getIndex());

        SourceType declaredType = arr.getType();
        IRType elementType;

        if (declaredType == ReferenceSourceType.OBJECT || declaredType == null) {
            SourceType arrayType = resolveArrayType(arr.getArray());
            if (arrayType instanceof ArraySourceType) {
                elementType = ((ArraySourceType) arrayType).getElementType().toIRType();
            } else {
                elementType = declaredType.toIRType();
            }
        } else {
            elementType = declaredType.toIRType();
        }

        SSAValue result = ctx.newValue(elementType);

        ArrayAccessInstruction instr = ArrayAccessInstruction.createLoad(result, array, index);
        ctx.getCurrentBlock().addInstruction(instr);

        return result;
    }

    private SourceType resolveArrayType(Expression arrayExpr) {
        if (arrayExpr instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) arrayExpr;
            String ownerClass = field.getOwnerClass();
            if (ownerClass == null || ownerClass.isEmpty() || ownerClass.equals("java/lang/Object")) {
                Expression receiver = field.getReceiver();
                if (receiver instanceof VarRefExpr) {
                    VarRefExpr varRef = (VarRefExpr) receiver;
                    if (!ctx.hasVariable(varRef.getName())) {
                        ownerClass = resolveClassName(varRef.getName());
                    }
                }
            }
            if (ownerClass != null && !ownerClass.isEmpty()) {
                SourceType resolved = ctx.getTypeResolver().resolveFieldType(ownerClass, field.getFieldName());
                if (resolved != null) {
                    return resolved;
                }
            }
        } else if (arrayExpr instanceof VarRefExpr) {
            VarRefExpr varRef = (VarRefExpr) arrayExpr;
            SSAValue val = ctx.getVariable(varRef.getName());
            if (val != null) {
                IRType irType = val.getType();
                if (irType instanceof com.tonic.analysis.ssa.type.ArrayType) {
                    com.tonic.analysis.ssa.type.ArrayType arrType = (com.tonic.analysis.ssa.type.ArrayType) irType;
                    return new ArraySourceType(irTypeToSourceType(arrType.getElementType()));
                }
            }
        }
        return arrayExpr.getType();
    }

    private SourceType irTypeToSourceType(IRType irType) {
        if (irType == com.tonic.analysis.ssa.type.PrimitiveType.INT) {
            return PrimitiveSourceType.INT;
        } else if (irType == com.tonic.analysis.ssa.type.PrimitiveType.LONG) {
            return PrimitiveSourceType.LONG;
        } else if (irType == com.tonic.analysis.ssa.type.PrimitiveType.FLOAT) {
            return PrimitiveSourceType.FLOAT;
        } else if (irType == com.tonic.analysis.ssa.type.PrimitiveType.DOUBLE) {
            return PrimitiveSourceType.DOUBLE;
        } else if (irType == com.tonic.analysis.ssa.type.PrimitiveType.BOOLEAN) {
            return PrimitiveSourceType.BOOLEAN;
        } else if (irType == com.tonic.analysis.ssa.type.PrimitiveType.BYTE) {
            return PrimitiveSourceType.BYTE;
        } else if (irType == com.tonic.analysis.ssa.type.PrimitiveType.CHAR) {
            return PrimitiveSourceType.CHAR;
        } else if (irType == com.tonic.analysis.ssa.type.PrimitiveType.SHORT) {
            return PrimitiveSourceType.SHORT;
        } else if (irType instanceof com.tonic.analysis.ssa.type.ReferenceType) {
            return new ReferenceSourceType(((com.tonic.analysis.ssa.type.ReferenceType) irType).getInternalName());
        }
        return ReferenceSourceType.OBJECT;
    }

    private Value lowerArrayStore(ArrayAccessExpr arr, Value value) {
        Value array = lower(arr.getArray());
        Value index = lower(arr.getIndex());

        ArrayAccessInstruction instr = ArrayAccessInstruction.createStore(array, index, value);
        ctx.getCurrentBlock().addInstruction(instr);

        return value;
    }

    private Value lowerNew(NewExpr newExpr) {
        String className = newExpr.getClassName();
        IRType type = new ReferenceType(className);
        SSAValue result = ctx.newValue(type);

        NewInstruction newInstr = new NewInstruction(result, className);
        ctx.getCurrentBlock().addInstruction(newInstr);

        List<Value> args = new ArrayList<>();
        args.add(result);

        for (Expression arg : newExpr.getArguments()) {
            args.add(lower(arg));
        }

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

        UnaryOp castOp = ReverseOperatorMapper.getCastOp(fromType, toType);
        if (castOp != null) {
            IRType resultType = toType.toIRType();
            SSAValue result = ctx.newValue(resultType);
            UnaryOpInstruction instr = new UnaryOpInstruction(result, castOp, operand);
            ctx.getCurrentBlock().addInstruction(instr);
            return result;
        }

        if (toType instanceof ReferenceSourceType) {
            IRType resultType = toType.toIRType();
            SSAValue result = ctx.newValue(resultType);
            TypeCheckInstruction instr = TypeCheckInstruction.createCast(result, operand, resultType);
            ctx.getCurrentBlock().addInstruction(instr);
            return result;
        }

        return operand;
    }

    private Value lowerTernary(TernaryExpr ternary) {
        IRBlock thenBlock = ctx.createBlock();
        IRBlock elseBlock = ctx.createBlock();
        IRBlock mergeBlock = ctx.createBlock();

        lowerCondition(ternary.getCondition(), thenBlock, elseBlock);

        ctx.setCurrentBlock(thenBlock);
        Value thenVal = lower(ternary.getThenExpr());
        IRBlock thenEndBlock = ctx.getCurrentBlock();
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(mergeBlock));
        thenEndBlock.addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

        ctx.setCurrentBlock(elseBlock);
        Value elseVal = lower(ternary.getElseExpr());
        IRBlock elseEndBlock = ctx.getCurrentBlock();
        ctx.getCurrentBlock().addInstruction(SimpleInstruction.createGoto(mergeBlock));
        elseEndBlock.addSuccessor(mergeBlock, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);

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
        TypeCheckInstruction instr = TypeCheckInstruction.createInstanceOf(result, operand, checkType);
        ctx.getCurrentBlock().addInstruction(instr);

        return result;
    }

    private Value lowerThis() {
        return ctx.getVariable("this");
    }

    private Value lowerArrayInit(ArrayInitExpr arrInit) {
        int size = arrInit.getElements().size();
        IRType elementType = getElementType(arrInit.getType());
        IRType arrayType = arrInit.getType().toIRType();

        SSAValue sizeVal = ctx.newValue(PrimitiveType.INT);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(sizeVal, IntConstant.of(size)));

        SSAValue result = ctx.newValue(arrayType);
        NewArrayInstruction newArr = new NewArrayInstruction(result, elementType, List.of(sizeVal));
        ctx.getCurrentBlock().addInstruction(newArr);

        int i = 0;
        for (Expression elem : arrInit.getElements()) {
            Value elemVal = lower(elem);
            SSAValue indexVal = ctx.newValue(PrimitiveType.INT);
            ctx.getCurrentBlock().addInstruction(new ConstantInstruction(indexVal, IntConstant.of(i)));
            ArrayAccessInstruction storeInstr = ArrayAccessInstruction.createStore(result, indexVal, elemVal);
            ctx.getCurrentBlock().addInstruction(storeInstr);
            i++;
        }

        return result;
    }

    private Value widenIfNeeded(Value value, IRType targetType) {
        if (!(value instanceof SSAValue)) {
            return value;
        }

        SSAValue ssaValue = (SSAValue) value;
        IRType sourceType = ssaValue.getType();

        if (sourceType.equals(targetType)) {
            return value;
        }

        if (!(sourceType instanceof PrimitiveType) || !(targetType instanceof PrimitiveType)) {
            return value;
        }

        PrimitiveType srcPrim = (PrimitiveType) sourceType;
        PrimitiveType tgtPrim = (PrimitiveType) targetType;

        UnaryOp conversionOp = getWideningOp(srcPrim, tgtPrim);
        if (conversionOp == null) {
            return value;
        }

        SSAValue widened = ctx.newValue(targetType);
        UnaryOpInstruction instr = new UnaryOpInstruction(widened, conversionOp, value);
        ctx.getCurrentBlock().addInstruction(instr);
        return widened;
    }

    private UnaryOp getWideningOp(PrimitiveType from, PrimitiveType to) {
        if (from == PrimitiveType.INT) {
            if (to == PrimitiveType.LONG) return UnaryOp.I2L;
            if (to == PrimitiveType.FLOAT) return UnaryOp.I2F;
            if (to == PrimitiveType.DOUBLE) return UnaryOp.I2D;
        } else if (from == PrimitiveType.LONG) {
            if (to == PrimitiveType.FLOAT) return UnaryOp.L2F;
            if (to == PrimitiveType.DOUBLE) return UnaryOp.L2D;
        } else if (from == PrimitiveType.FLOAT) {
            if (to == PrimitiveType.DOUBLE) return UnaryOp.F2D;
        }
        return null;
    }
}
