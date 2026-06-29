package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.ArraySourceType;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.*;
import com.tonic.analysis.ssa.value.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        lowerCondition(condition, trueTarget, falseTarget, false);
    }

    /**
     * Lowers {@code condition} (optionally negated) as control flow that branches to {@code trueTarget} when
     * the (negated) condition holds, else {@code falseTarget}. A logical NOT inverts the leaf branch opcode and
     * applies De Morgan to {@code &&}/{@code ||} - keeping the THEN block as the branch's jump target - rather
     * than swapping the true/false targets. Swapping would make the ELSE arm the fall-through, flipping the
     * recovered branch polarity on the round trip (javac keeps the THEN arm as the jump target).
     */
    private void lowerCondition(Expression condition, IRBlock trueTarget, IRBlock falseTarget, boolean negate) {
        if (condition instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) condition;
            if (unary.getOperator() == UnaryOperator.NOT) {
                lowerCondition(unary.getOperand(), trueTarget, falseTarget, !negate);
                return;
            }
        }

        if (condition instanceof BinaryExpr) {
            BinaryExpr bin = (BinaryExpr) condition;
            BinaryOperator op = bin.getOperator();
            if (op.isComparison()) {
                lowerComparisonForControlFlow(bin, trueTarget, falseTarget, negate);
                return;
            }
            // Short-circuit && / ||: chain the operands as branches through an intermediate block. Under a
            // negation De Morgan turns && into || and vice versa, with the negation pushed into each operand.
            if (op == BinaryOperator.AND || op == BinaryOperator.OR) {
                boolean effectiveAnd = (op == BinaryOperator.AND) != negate;
                IRBlock evalRight = ctx.createBlock();
                if (effectiveAnd) {
                    lowerCondition(bin.getLeft(), evalRight, falseTarget, negate);
                    ctx.setCurrentBlock(evalRight);
                    lowerCondition(bin.getRight(), trueTarget, falseTarget, negate);
                } else {
                    lowerCondition(bin.getLeft(), trueTarget, evalRight, negate);
                    ctx.setCurrentBlock(evalRight);
                    lowerCondition(bin.getRight(), trueTarget, falseTarget, negate);
                }
                return;
            }
        }

        Value cond = lower(condition);
        CompareOp leaf = negate ? CompareOp.IFEQ : CompareOp.IFNE;
        BranchInstruction branch = new BranchInstruction(leaf, cond, trueTarget, falseTarget);
        ctx.getCurrentBlock().addInstruction(branch);
        ctx.getCurrentBlock().addSuccessor(trueTarget, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
        ctx.getCurrentBlock().addSuccessor(falseTarget, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
    }

    /** The inverse of {@code op} when {@code negate} (null-safe), else {@code op} unchanged. */
    private static CompareOp maybeInvert(CompareOp op, boolean negate) {
        return negate && op != null ? op.invert() : op;
    }

    private void lowerComparisonForControlFlow(BinaryExpr bin, IRBlock trueTarget, IRBlock falseTarget, boolean negate) {
        Value left = lower(bin.getLeft());
        Value right = lower(bin.getRight());

        // Reference == / != (including against null) must compare with acmp, not the integer icmp. Decide from the
        // lowered VALUE types (reliable) - the AST operand types are often unqualified or unset, and
        // getCommonComparisonType defaults non-floating operands to int.
        if (isReferenceValue(left) || isReferenceValue(right)) {
            BinaryOperator binOp = bin.getOperator();
            if (binOp == BinaryOperator.EQ || binOp == BinaryOperator.NE) {
                CompareOp op = binOp == BinaryOperator.EQ ? CompareOp.ACMPEQ : CompareOp.ACMPNE;
                ctx.getCurrentBlock().addInstruction(new BranchInstruction(maybeInvert(op, negate), left, right, trueTarget, falseTarget));
                ctx.getCurrentBlock().addSuccessor(trueTarget, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
                ctx.getCurrentBlock().addSuccessor(falseTarget, com.tonic.analysis.ssa.cfg.EdgeType.NORMAL);
                return;
            }
        }

        SourceType leftType = bin.getLeft().getType();
        SourceType rightType = bin.getRight().getType();
        SourceType commonType = getCommonComparisonType(leftType, rightType);
        IRType commonIRType = commonType.toIRType();

        left = widenIfNeeded(left, commonIRType);
        right = widenIfNeeded(right, commonIRType);

        CompareOp cmpOp = ReverseOperatorMapper.toCompareOp(bin.getOperator());
        IRBlock currentBlock = ctx.getCurrentBlock();

        if (commonType == PrimitiveSourceType.LONG) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOpInstruction lcmp = new BinaryOpInstruction(cmpResult, BinaryOp.LCMP, left, right);
            currentBlock.addInstruction(lcmp);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(maybeInvert(singleCmp, negate), cmpResult, trueTarget, falseTarget);
            currentBlock.addInstruction(branch);
        } else if (commonType == PrimitiveSourceType.FLOAT) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOp fcmp = ReverseOperatorMapper.getFloatCompareOp(bin.getOperator() == BinaryOperator.GT || bin.getOperator() == BinaryOperator.GE);
            BinaryOpInstruction fcmpInstr = new BinaryOpInstruction(cmpResult, fcmp, left, right);
            currentBlock.addInstruction(fcmpInstr);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(maybeInvert(singleCmp, negate), cmpResult, trueTarget, falseTarget);
            currentBlock.addInstruction(branch);
        } else if (commonType == PrimitiveSourceType.DOUBLE) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOp dcmp = ReverseOperatorMapper.getDoubleCompareOp(bin.getOperator() == BinaryOperator.GT || bin.getOperator() == BinaryOperator.GE);
            BinaryOpInstruction dcmpInstr = new BinaryOpInstruction(cmpResult, dcmp, left, right);
            currentBlock.addInstruction(dcmpInstr);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(maybeInvert(singleCmp, negate), cmpResult, trueTarget, falseTarget);
            currentBlock.addInstruction(branch);
        } else {
            BranchInstruction branch = new BranchInstruction(maybeInvert(cmpOp, negate), left, right, trueTarget, falseTarget);
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
        } else if (expr instanceof LambdaExpr) {
            return lowerLambda((LambdaExpr) expr);
        } else if (expr instanceof MethodRefExpr) {
            return lowerMethodRef((MethodRefExpr) expr);
        } else if (expr instanceof ClassExpr) {
            return lowerClass((ClassExpr) expr);
        } else if (expr instanceof SuperExpr) {
            return lowerSuper();
        } else if (expr instanceof InvokeDynamicExpr) {
            return lowerInvokeDynamic((InvokeDynamicExpr) expr);
        } else if (expr instanceof DynamicConstantExpr) {
            return lowerDynamicConstant((DynamicConstantExpr) expr);
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
            }
            // A char literal arrives as a Character, not a Number; treat it as its integer code value.
            Number num = (value instanceof Character) ? (int) (Character) value
                       : (value instanceof Number) ? (Number) value : null;
            if (num != null) {
                if (prim == PrimitiveSourceType.BYTE || prim == PrimitiveSourceType.CHAR ||
                    prim == PrimitiveSourceType.SHORT || prim == PrimitiveSourceType.INT) {
                    return IntConstant.of(num.intValue());
                } else if (prim == PrimitiveSourceType.LONG) {
                    return new LongConstant(num.longValue());
                } else if (prim == PrimitiveSourceType.FLOAT) {
                    return new FloatConstant(num.floatValue());
                } else if (prim == PrimitiveSourceType.DOUBLE) {
                    return new DoubleConstant(num.doubleValue());
                }
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
        String name = var.getName();
        if (ctx.hasVariable(name)) {
            return ctx.getVariable(name);
        }
        Value field = tryLowerImplicitFieldRead(name);
        if (field != null) {
            return field;
        }
        return ctx.getVariable(name);
    }

    /**
     * Resolves an unqualified name that is not a local variable as a read of a field
     * declared on the current class (or inherited). The decompiler emits own-class field
     * references as bare names; this rewrites them to the appropriate getfield/getstatic.
     * Returns null when the name does not resolve to a field, leaving the caller to surface
     * the original "undefined variable" error.
     */
    private Value tryLowerImplicitFieldRead(String name) {
        String ownerClass = ctx.getOwnerClass();
        if (ownerClass == null || ownerClass.isEmpty()) {
            return null;
        }
        SourceType fieldType = ctx.getTypeResolver().findFieldType(ownerClass, name);
        if (fieldType == null) {
            return null;
        }

        IRType irType = fieldType.toIRType();
        SSAValue result = ctx.newValue(irType);
        String descriptor = irType.getDescriptor();

        FieldAccessInstruction instr;
        if (ctx.getTypeResolver().isStaticField(ownerClass, name)) {
            instr = FieldAccessInstruction.createStaticLoad(result, ownerClass, name, descriptor);
        } else {
            if (!ctx.hasVariable("this")) {
                return null;
            }
            instr = FieldAccessInstruction.createLoad(result, ownerClass, name, descriptor, ctx.getVariable("this"));
        }
        ctx.getCurrentBlock().addInstruction(instr);
        return result;
    }

    /**
     * Stores a value into a field referenced by an unqualified name (no local variable of
     * that name exists). Mirrors {@link #tryLowerImplicitFieldRead} for the write side of
     * assignment, compound-assignment and increment/decrement. Returns false when the name
     * does not resolve to a field.
     */
    private boolean tryLowerImplicitFieldStore(String name, Value value) {
        String ownerClass = ctx.getOwnerClass();
        if (ownerClass == null || ownerClass.isEmpty()) {
            return false;
        }
        SourceType fieldType = ctx.getTypeResolver().findFieldType(ownerClass, name);
        if (fieldType == null) {
            return false;
        }

        String descriptor = fieldType.toIRType().getDescriptor();

        FieldAccessInstruction instr;
        if (ctx.getTypeResolver().isStaticField(ownerClass, name)) {
            instr = FieldAccessInstruction.createStaticStore(ownerClass, name, descriptor, value);
        } else {
            if (!ctx.hasVariable("this")) {
                return false;
            }
            instr = FieldAccessInstruction.createStore(ownerClass, name, descriptor, ctx.getVariable("this"), value);
        }
        ctx.getCurrentBlock().addInstruction(instr);
        return true;
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
        if (!(resultType instanceof PrimitiveType) || resultType == PrimitiveType.BOOLEAN) {
            IRType inferred = arithmeticResultType(left, right);
            if (inferred != null) {
                resultType = inferred;
            }
        }
        left = widenIfNeeded(left, resultType);
        right = widenIfNeeded(right, resultType);

        SSAValue result = ctx.newValue(resultType);
        BinaryOpInstruction instr = new BinaryOpInstruction(result, irOp, left, right);
        ctx.getCurrentBlock().addInstruction(instr);

        return result;
    }

    /**
     * Infers the result type of a binary arithmetic operation from its operand IR types,
     * following JVM numeric promotion (double &gt; float &gt; long &gt; int). Used as a fallback
     * when the AST node lacks a resolved numeric type, which happens for hand-written source
     * with unqualified self-references the parser could not type. Returns null for
     * non-numeric operands.
     */
    private IRType arithmeticResultType(Value left, Value right) {
        IRType lt = left.getType();
        IRType rt = right.getType();
        if (lt == PrimitiveType.DOUBLE || rt == PrimitiveType.DOUBLE) {
            return PrimitiveType.DOUBLE;
        }
        if (lt == PrimitiveType.FLOAT || rt == PrimitiveType.FLOAT) {
            return PrimitiveType.FLOAT;
        }
        if (lt == PrimitiveType.LONG || rt == PrimitiveType.LONG) {
            return PrimitiveType.LONG;
        }
        if (isIntegralIRType(lt) && isIntegralIRType(rt)) {
            return PrimitiveType.INT;
        }
        return null;
    }

    private boolean isIntegralIRType(IRType type) {
        return type == PrimitiveType.INT || type == PrimitiveType.BYTE
            || type == PrimitiveType.CHAR || type == PrimitiveType.SHORT
            || type == PrimitiveType.BOOLEAN;
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
                parts.add(lit.getValue());
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
            if (!ctx.hasVariable(varRef.getName()) && tryLowerImplicitFieldStore(varRef.getName(), rhs)) {
                return rhs;
            }
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
            VarRefExpr varRef = (VarRefExpr) left;
            if (ctx.hasVariable(varRef.getName()) || !tryLowerImplicitFieldStore(varRef.getName(), result)) {
                ctx.setVariable(varRef.getName(), result);
            }
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

        SourceType leftType = bin.getLeft().getType();
        SourceType rightType = bin.getRight().getType();
        SourceType commonType = getCommonComparisonType(leftType, rightType);
        IRType commonIRType = commonType.toIRType();

        left = widenIfNeeded(left, commonIRType);
        right = widenIfNeeded(right, commonIRType);

        IRBlock trueBlock = ctx.createBlock();
        IRBlock falseBlock = ctx.createBlock();
        IRBlock mergeBlock = ctx.createBlock();

        CompareOp cmpOp = ReverseOperatorMapper.toCompareOp(bin.getOperator());
        IRBlock currentBlock = ctx.getCurrentBlock();

        if (commonType == PrimitiveSourceType.LONG) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOpInstruction lcmp = new BinaryOpInstruction(cmpResult, BinaryOp.LCMP, left, right);
            currentBlock.addInstruction(lcmp);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueBlock, falseBlock);
            currentBlock.addInstruction(branch);
        } else if (commonType == PrimitiveSourceType.FLOAT) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOp fcmp = ReverseOperatorMapper.getFloatCompareOp(bin.getOperator() == BinaryOperator.GT || bin.getOperator() == BinaryOperator.GE);
            BinaryOpInstruction fcmpInstr = new BinaryOpInstruction(cmpResult, fcmp, left, right);
            currentBlock.addInstruction(fcmpInstr);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueBlock, falseBlock);
            currentBlock.addInstruction(branch);
        } else if (commonType == PrimitiveSourceType.DOUBLE) {
            SSAValue cmpResult = ctx.newValue(PrimitiveType.INT);
            BinaryOp dcmp = ReverseOperatorMapper.getDoubleCompareOp(bin.getOperator() == BinaryOperator.GT || bin.getOperator() == BinaryOperator.GE);
            BinaryOpInstruction dcmpInstr = new BinaryOpInstruction(cmpResult, dcmp, left, right);
            currentBlock.addInstruction(dcmpInstr);

            CompareOp singleCmp = ReverseOperatorMapper.toSingleOperandCompareOp(bin.getOperator());
            BranchInstruction branch = new BranchInstruction(singleCmp, cmpResult, trueBlock, falseBlock);
            currentBlock.addInstruction(branch);
        } else {
            // Reference == / != (value context, e.g. `return x != null`) must use acmp, not the integer icmp.
            CompareOp op = cmpOp;
            if (isReferenceValue(left) || isReferenceValue(right)) {
                if (bin.getOperator() == BinaryOperator.EQ) {
                    op = CompareOp.ACMPEQ;
                } else if (bin.getOperator() == BinaryOperator.NE) {
                    op = CompareOp.ACMPNE;
                }
            }
            BranchInstruction branch = new BranchInstruction(op, left, right, trueBlock, falseBlock);
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

        // Increment/decrement lower their own operand (read-modify-write); dispatch before the
        // eager operand lowering below so the operand isn't read twice, leaving a dead load.
        if (op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC) {
            return lowerIncDec(unary, true);
        } else if (op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC) {
            return lowerIncDec(unary, false);
        }

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
        } else {
            throw new LoweringException("Unsupported unary operator: " + op);
        }
    }

    /** The constant {@code 1} typed to match {@code type}, so {@code ++}/{@code --} on a long/double/float
     * operand adds a category-correct value (e.g. {@code lconst_1}, not {@code iconst_1}). */
    private static Constant oneConstant(IRType type) {
        if (type == PrimitiveType.LONG) {
            return LongConstant.ONE;
        }
        if (type == PrimitiveType.DOUBLE) {
            return DoubleConstant.ONE;
        }
        if (type == PrimitiveType.FLOAT) {
            return FloatConstant.ONE;
        }
        return IntConstant.ONE;
    }

    private Value lowerIncDec(UnaryExpr unary, boolean isPrefix) {
        UnaryOperator op = unary.getOperator();
        boolean isInc = (op == UnaryOperator.PRE_INC || op == UnaryOperator.POST_INC);

        Expression operand = unary.getOperand();
        Value oldValue = lower(operand);

        IRType type = oldValue != null ? oldValue.getType() : unary.getType().toIRType();
        SSAValue one = ctx.newValue(type);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(one, oneConstant(type)));

        SSAValue newValue = ctx.newValue(type);
        BinaryOp binOp = isInc ? BinaryOp.ADD : BinaryOp.SUB;
        ctx.getCurrentBlock().addInstruction(new BinaryOpInstruction(newValue, binOp, oldValue, one));

        if (operand instanceof VarRefExpr) {
            VarRefExpr varRef = (VarRefExpr) operand;
            if (ctx.hasVariable(varRef.getName()) || !tryLowerImplicitFieldStore(varRef.getName(), newValue)) {
                ctx.setVariable(varRef.getName(), newValue);
            }
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
                SourceType fieldType = ctx.hasVariable(varRef.getName())
                        ? null
                        : ctx.getTypeResolver().findFieldType(ctx.getOwnerClass(), varRef.getName());
                if (!ctx.hasVariable(varRef.getName()) && !(fieldType instanceof ReferenceSourceType)) {
                    // A bare identifier that is neither a local variable nor a field: a class name (static call).
                    invokeType = InvokeType.STATIC;
                    ownerClass = resolveClassName(varRef.getName());
                } else {
                    // A local variable, or an own-class field used as the receiver: a virtual call on its type.
                    Value receiverValue = lower(receiver);
                    args.add(receiverValue);
                    invokeType = InvokeType.VIRTUAL;
                    if (ownerClass == null || ownerClass.isEmpty()) {
                        ownerClass = fieldType instanceof ReferenceSourceType
                                ? ((ReferenceSourceType) fieldType).getInternalName()
                                : ownerClassFromValue(receiverValue, receiver);
                    }
                }
            } else if (receiver instanceof SuperExpr) {
                args.add(lower(receiver));
                invokeType = InvokeType.SPECIAL;
                if (ownerClass == null || ownerClass.isEmpty()) {
                    ownerClass = ctx.getSuperClassName();
                    if (ownerClass == null) {
                        ownerClass = "java/lang/Object";
                    }
                }
            } else if (receiver instanceof ThisExpr && "<init>".equals(call.getMethodName())) {
                // this(...) constructor chaining: invokespecial on the current class.
                args.add(lower(receiver));
                invokeType = InvokeType.SPECIAL;
                if (ownerClass == null || ownerClass.isEmpty()) {
                    ownerClass = ctx.getOwnerClass();
                }
            } else if (receiver != null) {
                Value receiverValue = lower(receiver);
                args.add(receiverValue);
                invokeType = InvokeType.VIRTUAL;
                if (ownerClass == null || ownerClass.isEmpty()) {
                    ownerClass = ownerClassFromValue(receiverValue, receiver);
                }
            } else {
                if (ownerClass == null || ownerClass.isEmpty()) {
                    ownerClass = ctx.getOwnerClass();
                }
                boolean staticContext = !ctx.hasVariable("this");
                boolean staticTarget = ctx.getTypeResolver().isStaticMethodInCurrentClass(call.getMethodName());
                if (staticContext || staticTarget) {
                    invokeType = InvokeType.STATIC;
                } else {
                    args.add(ctx.getVariable("this"));
                    invokeType = InvokeType.VIRTUAL;
                }
            }
        }

        List<Value> loweredArgs = new ArrayList<>();
        List<Expression> callArguments = call.getArguments();
        List<SourceType> calleeParamTypes = resolveCalleeParamTypes(ownerClass, call.getMethodName(), callArguments.size());
        // Track where each argument's instructions begin in the current block, so a varargs pack can later
        // move each element's computation to sit right before its array store (keeping it stack-resident
        // instead of materialized to a local). Only valid while all args lower into this one block.
        IRBlock argBlock = ctx.getCurrentBlock();
        int[] argInstrStart = new int[callArguments.size()];
        boolean argsSingleBlock = true;
        for (int i = 0; i < callArguments.size(); i++) {
            Expression arg = callArguments.get(i);
            if (calleeParamTypes != null && i < calleeParamTypes.size()) {
                arg = retypeFunctionalArg(arg, calleeParamTypes.get(i));
            }
            argInstrStart[i] = argsSingleBlock && ctx.getCurrentBlock() == argBlock
                    ? argBlock.getInstructions().size() : -1;
            loweredArgs.add(lower(arg));
            if (ctx.getCurrentBlock() != argBlock) {
                argsSingleBlock = false;
            }
        }
        List<SourceType> argTypes = new ArrayList<>();
        List<IRType> argIrTypes = new ArrayList<>();
        for (Value arg : loweredArgs) {
            if (arg instanceof SSAValue) {
                argTypes.add(irTypeToSourceType(arg.getType()));
            } else {
                argTypes.add(ReferenceSourceType.OBJECT);
            }
            IRType t = arg.getType();
            argIrTypes.add(t != null ? t : new ReferenceType("java/lang/Object"));
        }

        // Emit the method's DECLARED descriptor (the verifier resolves invokes by exact descriptor), selecting the
        // overload by argument types. Only fall back to building one from the lowered arg types when the callee isn't
        // in the pool - otherwise a subtype/erased argument (e.g. Map.put("k", objVal)) yields a bogus (String,String)
        // descriptor instead of the real (Object,Object) and fails verification.
        String declaredDescriptor =
            ctx.getTypeResolver().resolveMethodDescriptor(ownerClass, call.getMethodName(), argIrTypes);

        // Varargs calls are decompiled as flat trailing arguments; the bytecode invoke needs them packed into the
        // declared component[] array. Pack here (before adding to the arg list) when the resolved method is varargs.
        loweredArgs = packVarargsIfNeeded(ownerClass, call.getMethodName(), declaredDescriptor, loweredArgs,
                argBlock, argInstrStart, argsSingleBlock);
        args.addAll(loweredArgs);

        SourceType returnType;
        String descriptor;
        if (declaredDescriptor != null) {
            descriptor = declaredDescriptor;
            returnType = ctx.getTypeResolver().returnTypeFromDescriptor(declaredDescriptor);
        } else if ("<init>".equals(call.getMethodName())) {
            // A constructor (super(...)/this(...)) is always void; the generic fallback otherwise defaults an
            // unresolved <init> (e.g. a JDK super not in the pool) to Object, producing an invalid
            // (...)Ljava/lang/Object; descriptor that the decompiler then mis-renders as a duplicate super().
            returnType = VoidSourceType.INSTANCE;
            descriptor = buildMethodDescriptorWithReturn(argTypes, returnType);
        } else {
            returnType = resolveMethodReturnType(call, ownerClass, argTypes);
            descriptor = buildMethodDescriptorWithReturn(argTypes, returnType);
        }

        if (invokeType == InvokeType.VIRTUAL && ctx.getTypeResolver().isInterface(ownerClass)) {
            invokeType = InvokeType.INTERFACE;
        }

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

    /**
     * Packs the trailing arguments of a varargs call into a fresh array of the declared component type (the decompiler
     * renders varargs as flat arguments, but the invoke descriptor's last parameter is an array). Returns the original
     * list unchanged for non-varargs calls or when an array is already passed explicitly for the varargs parameter.
     */
    private List<Value> packVarargsIfNeeded(String ownerClass, String methodName, String declaredDescriptor,
                                            List<Value> loweredArgs, IRBlock argBlock, int[] argInstrStart,
                                            boolean argsSingleBlock) {
        if (declaredDescriptor == null
                || !ctx.getTypeResolver().isVarargsMethod(ownerClass, methodName, declaredDescriptor)) {
            return loweredArgs;
        }
        List<SourceType> params = ctx.getTypeResolver().paramTypesFromDescriptor(declaredDescriptor);
        if (params.isEmpty() || !(params.get(params.size() - 1) instanceof ArraySourceType)) {
            return loweredArgs;
        }
        int fixedCount = params.size() - 1;
        if (loweredArgs.size() == params.size() && loweredArgs.get(loweredArgs.size() - 1).getType() instanceof ArrayType) {
            return loweredArgs;   // an explicit array is already supplied for the varargs parameter
        }
        if (loweredArgs.size() < fixedCount) {
            return loweredArgs;
        }
        IRType componentType = ((ArraySourceType) params.get(params.size() - 1)).getElementType().toIRType();
        int count = loweredArgs.size() - fixedCount;

        // Pull each element's already-emitted computation out of the block so it can be re-emitted right before
        // its array store (keeping it stack-resident). Null when the move is unsafe - then the values stay put
        // (and get materialized to locals, the prior behavior).
        List<List<IRInstruction>> elementGroups =
                extractVarargsElementGroups(argBlock, argInstrStart, fixedCount, count, argsSingleBlock);

        IRBlock block = ctx.getCurrentBlock();
        SSAValue sizeVal = ctx.newValue(PrimitiveType.INT);
        block.addInstruction(new ConstantInstruction(sizeVal, IntConstant.of(count)));
        SSAValue arrayVal = ctx.newValue(new ArrayType(componentType));
        block.addInstruction(new NewArrayInstruction(arrayVal, componentType, List.of(sizeVal)));
        for (int i = 0; i < count; i++) {
            SSAValue idx = ctx.newValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(idx, IntConstant.of(i)));
            if (elementGroups != null) {
                for (IRInstruction moved : elementGroups.get(i)) {
                    block.addInstruction(moved);
                }
            }
            block.addInstruction(
                    ArrayAccessInstruction.createStore(arrayVal, idx, loweredArgs.get(fixedCount + i)));
        }
        List<Value> packed = new ArrayList<>(loweredArgs.subList(0, fixedCount));
        packed.add(arrayVal);
        return packed;
    }

    /**
     * If the varargs element arguments all lowered contiguously into {@code argBlock} (which must still be the
     * current block), removes their instructions from the block and returns them grouped per element, so the
     * caller can re-emit each group immediately before its array store. Returns null when the move would be
     * unsafe - multi-block args, a changed current block, or a non-monotonic/invalid range - in which case the
     * already-emitted values are left in place (and the scheduler materializes them, as before).
     */
    private List<List<IRInstruction>> extractVarargsElementGroups(IRBlock argBlock, int[] argInstrStart,
                                                                  int fixedCount, int count, boolean argsSingleBlock) {
        if (!argsSingleBlock || ctx.getCurrentBlock() != argBlock || count <= 0
                || fixedCount + count > argInstrStart.length) {
            return null;
        }
        List<IRInstruction> instrs = argBlock.getInstructions();
        int end = instrs.size();
        int[] bounds = new int[count + 1];
        bounds[count] = end;
        for (int j = 0; j < count; j++) {
            int start = argInstrStart[fixedCount + j];
            if (start < 0 || start > end || (j > 0 && start < bounds[j - 1])) {
                return null;
            }
            bounds[j] = start;
        }
        List<List<IRInstruction>> groups = new ArrayList<>();
        for (int j = 0; j < count; j++) {
            groups.add(new ArrayList<>(instrs.subList(bounds[j], bounds[j + 1])));
        }
        instrs.subList(bounds[0], end).clear();
        return groups;
    }

    private List<SourceType> resolveCalleeParamTypes(String ownerClass, String methodName, int argCount) {
        List<SourceType> jdk = jdkConsumerParamTypes(ownerClass, methodName, argCount);
        if (jdk != null) {
            return jdk;
        }
        if (ownerClass == null || ownerClass.isEmpty()) {
            return null;
        }
        String descriptor = ctx.getTypeResolver().resolveMethodDescriptor(ownerClass, methodName, argCount);
        if (descriptor == null) {
            return null;
        }
        return ctx.getTypeResolver().paramTypesFromDescriptor(descriptor);
    }

    private List<SourceType> jdkConsumerParamTypes(String ownerClass, String methodName, int argCount) {
        if (ownerClass == null) {
            return null;
        }
        String runnable = "java/lang/Runnable";
        String consumer = "java/util/function/Consumer";
        switch (ownerClass) {
            case "javax/swing/SwingUtilities":
            case "java/awt/EventQueue":
                if (("invokeLater".equals(methodName) || "invokeAndWait".equals(methodName)) && argCount == 1) {
                    return List.of(new ReferenceSourceType(runnable));
                }
                break;
            case "java/util/concurrent/Executor":
            case "java/util/concurrent/ExecutorService":
                if ("execute".equals(methodName) && argCount == 1) {
                    return List.of(new ReferenceSourceType(runnable));
                }
                break;
            default:
                break;
        }
        if (("forEach".equals(methodName)) && argCount == 1
                && (ownerClass.startsWith("java/util/") || ownerClass.startsWith("java/lang/Iterable"))) {
            return List.of(new ReferenceSourceType(consumer));
        }
        return null;
    }

    /**
     * Re-types a lambda argument with the functional-interface type the callee expects, when the
     * lambda's own type is unknown (Object). Other argument kinds are returned unchanged.
     */
    private Expression retypeFunctionalArg(Expression arg, SourceType expected) {
        if (!(arg instanceof LambdaExpr) || !(expected instanceof ReferenceSourceType)) {
            return arg;
        }
        String expectedName = ((ReferenceSourceType) expected).getInternalName();
        if (expectedName == null || expectedName.isEmpty() || "java/lang/Object".equals(expectedName)) {
            return arg;
        }
        LambdaExpr lambda = (LambdaExpr) arg;
        SourceType current = lambda.getType();
        if (current instanceof ReferenceSourceType
                && !"java/lang/Object".equals(((ReferenceSourceType) current).getInternalName())) {
            return arg;
        }
        return new LambdaExpr(lambda.getParameters(), lambda.getBody(), expected)
                .withImplMethodKey(lambda.getImplMethodKey());
    }

    /**
     * Resolves the owning class for a virtual call on a local-variable receiver. The lowered value's IR type
     * is authoritative (e.g. a caught exception, whose AST reference carries no declared type), so it is
     * preferred over the AST-based {@link #resolveReceiverOwnerClass} fallback.
     */
    private String ownerClassFromValue(Value receiverValue, Expression receiver) {
        if (receiverValue instanceof SSAValue && receiverValue.getType() instanceof ReferenceType) {
            String internalName = ((ReferenceType) receiverValue.getType()).getInternalName();
            if (internalName != null && !internalName.isEmpty() && !internalName.equals("java/lang/Object")) {
                return internalName;
            }
        }
        return resolveReceiverOwnerClass(receiver);
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
                return internalName.contains("/") ? internalName : resolveClassName(internalName);
            }
        }
        return "java/lang/Object";
    }

    private SourceType resolveMethodReturnType(MethodCallExpr call, String ownerClass, List<SourceType> argTypes) {
        SourceType declaredType = call.getType();
        // Value check, not identity: the decompiler hands us fresh ReferenceSourceType("java/lang/Object") instances,
        // so `== ReferenceSourceType.OBJECT` misses them and a real return type (e.g. LocalDateTime.isAfter -> boolean)
        // is left as Object -> wrong descriptor -> "Bad type on operand stack" when an ifeq consumes it.
        if (isObjectOrNull(declaredType)) {
            SourceType resolved = ctx.getTypeResolver().resolveMethodReturnType(ownerClass, call.getMethodName(), argTypes);
            if (resolved != null) {
                return resolved;
            }
        }
        return declaredType != null ? declaredType : ReferenceSourceType.OBJECT;
    }

    private static boolean isObjectOrNull(SourceType t) {
        return t == null
            || (t instanceof ReferenceSourceType && "java/lang/Object".equals(((ReferenceSourceType) t).getInternalName()));
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

    private Value lowerFieldAccess(FieldAccessExpr field) {
        String ownerClass;
        boolean isStatic = field.isStatic();
        Expression receiver = field.getReceiver();

        // `array.length` is the arraylength instruction, not a field load. Decide from the lowered receiver's type
        // (reliable) so a genuine field literally named "length" on a class still lowers as a getfield. Skip
        // class-name receivers (static access). Lower the receiver exactly once to preserve side effects.
        if (!isStatic && "length".equals(field.getFieldName()) && receiver != null
                && !(receiver instanceof VarRefExpr && !ctx.hasVariable(((VarRefExpr) receiver).getName()))) {
            Value recv = lower(receiver);
            if (recv.getType() instanceof ArrayType) {
                SSAValue len = ctx.newValue(PrimitiveType.INT);
                ctx.getCurrentBlock().addInstruction(SimpleInstruction.createArrayLength(len, recv));
                return len;
            }
            String owner = recv.getType() instanceof ReferenceType
                ? ((ReferenceType) recv.getType()).getInternalName() : "java/lang/Object";
            IRType ft = resolveFieldType(field, owner);
            SSAValue res = ctx.newValue(ft);
            ctx.getCurrentBlock().addInstruction(
                FieldAccessInstruction.createLoad(res, owner, field.getFieldName(), ft.getDescriptor(), recv));
            return res;
        }

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
        } else if (receiver instanceof ThisExpr) {
            ownerClass = ctx.getOwnerClass();
        } else if (receiver instanceof SuperExpr) {
            ownerClass = ctx.getSuperClassName();
            if (ownerClass == null || ownerClass.isEmpty()) {
                ownerClass = "java/lang/Object";
            }
        } else {
            String qualifiedOwner = resolveQualifiedTypeReceiver(receiver);
            if (qualifiedOwner != null) {
                ownerClass = qualifiedOwner;
                isStatic = true;
            } else {
                ownerClass = field.getOwnerClass();
                if (ownerClass == null || ownerClass.isEmpty()) {
                    ownerClass = ctx.getOwnerClass();
                }
            }
        }

        ownerClass = normalizeOwnerClass(ownerClass);

        Value receiverVal = null;
        if (!isStatic) {
            receiverVal = receiver != null ? lower(receiver) : ctx.getVariable("this");
            String fromValue = receiverOwner(receiverVal);
            if (fromValue != null) {
                ownerClass = fromValue;   // the receiver's actual type beats the decompiler's owner guess
            }
        }

        IRType fieldType = resolveFieldType(field, ownerClass);
        SSAValue result = ctx.newValue(fieldType);
        String descriptor = fieldType.getDescriptor();

        FieldAccessInstruction instr;
        if (isStatic) {
            instr = FieldAccessInstruction.createStaticLoad(result, ownerClass, field.getFieldName(), descriptor);
        } else {
            instr = FieldAccessInstruction.createLoad(result, ownerClass, field.getFieldName(), descriptor, receiverVal);
        }
        ctx.getCurrentBlock().addInstruction(instr);

        return result;
    }

    /** The receiver value's reference type as an internal owner name, or null when it is not a usable named reference. */
    private static String receiverOwner(Value receiverVal) {
        if (receiverVal != null && receiverVal.getType() instanceof ReferenceType) {
            String n = ((ReferenceType) receiverVal.getType()).getInternalName();
            if (n != null && !n.isEmpty() && !n.equals("java/lang/Object")) {
                return n;
            }
        }
        return null;
    }

    private IRType resolveFieldType(FieldAccessExpr field, String ownerClass) {
        SourceType declaredType = field.getType();
        if (isObjectOrNull(declaredType)) {
            SourceType resolved = ctx.getTypeResolver().resolveFieldType(ownerClass, field.getFieldName());
            if (resolved == null) {
                String fieldRef = (ownerClass != null ? ownerClass.replace('/', '.') : "<unknown>")
                    + "." + field.getFieldName();
                throw new LoweringException("Unable to resolve field type: " + fieldRef);
            }
            return resolved.toIRType();
        }
        // Resolve the declared (AST) type's reference name to its FQN via the descriptor, rather than the raw
        // toIRType() - else a wildcard/same-package field type (e.g. DefaultListModel) emits an unqualified
        // `LDefaultListModel;` descriptor -> NoClassDefFoundError, and poisons the field value's owner downstream.
        return IRType.fromDescriptor(ctx.getTypeResolver().descriptorOf(declaredType));
    }

    private String resolveClassName(String simpleName) {
        return ctx.getTypeResolver().resolveClassName(simpleName);
    }

    /**
     * If {@code receiver} is a pure dotted-name chain that names a class in the pool, returns that class's
     * internal name; otherwise null. The decompiler emits a static/enum member fully qualified
     * ({@code a.b.Outer$Inner.CONST}), which the parser builds as a field-access chain rather than a type
     * reference - so a {@code .CONST} access whose receiver is such a chain is really a static field access on
     * the named class. A chain rooted at a local variable is a genuine field access and is left alone.
     */
    private String resolveQualifiedTypeReceiver(Expression receiver) {
        String dottedName = flattenDottedName(receiver);
        if (dottedName == null) {
            return null;
        }
        String internalName = ctx.getTypeResolver().resolveInternalName(dottedName);
        return ctx.getTypeResolver().classExists(internalName) ? internalName : null;
    }

    private String flattenDottedName(Expression expr) {
        if (expr instanceof VarRefExpr) {
            String name = ((VarRefExpr) expr).getName();
            return ctx.hasVariable(name) ? null : name;
        }
        if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr access = (FieldAccessExpr) expr;
            String base = flattenDottedName(access.getReceiver());
            return base != null ? base + "." + access.getFieldName() : null;
        }
        return null;
    }

    /**
     * Normalizes a field-owner class name to a fully-qualified internal name. Decompiled source
     * refers to same-package and imported types by their simple name; this resolves such names
     * (e.g. {@code MainFrame} -> {@code osrs/dev/MainFrame}) against imports and the loaded pool so
     * the field can be located. Already-qualified or empty names are returned unchanged.
     */
    private String normalizeOwnerClass(String ownerClass) {
        if (ownerClass == null || ownerClass.isEmpty()) {
            return ownerClass;
        }
        // resolveInternalName (not resolveClassName) so a nested type spelled Outer.Inner / Outer/Inner becomes
        // Outer$Inner, not a bogus Outer/Inner package boundary -> ClassNotFoundException on the nested class.
        return ctx.getTypeResolver().resolveInternalName(ownerClass);
    }

    /**
     * Converts a source type to an IR type with its class name(s) resolved through the type resolver, so a simple,
     * same-package, or imported name (e.g. {@code Main} in its own package) becomes the fully-qualified internal
     * name. Use this where a reference type names a class constant in the bytecode - {@code .class} literals, casts,
     * and {@code instanceof} - which {@link SourceType#toIRType()} alone leaves unqualified (emitting e.g. {@code
     * Main} instead of {@code osrs/dev/Main}, which fails to load at runtime).
     */
    private IRType resolveTypeForConstant(SourceType type) {
        if (type instanceof ReferenceSourceType) {
            String resolved = normalizeOwnerClass(((ReferenceSourceType) type).getInternalName());
            if (resolved != null && !resolved.isEmpty()) {
                return new ReferenceType(resolved);
            }
        } else if (type instanceof ArraySourceType) {
            ArraySourceType array = (ArraySourceType) type;
            return new ArrayType(resolveTypeForConstant(array.getElementType()), array.getTotalDimensions());
        }
        return type.toIRType();
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
        } else if (receiver instanceof ThisExpr) {
            ownerClass = ctx.getOwnerClass();
        } else if (receiver instanceof SuperExpr) {
            ownerClass = ctx.getSuperClassName();
            if (ownerClass == null || ownerClass.isEmpty()) {
                ownerClass = "java/lang/Object";
            }
        } else {
            String qualifiedOwner = resolveQualifiedTypeReceiver(receiver);
            if (qualifiedOwner != null) {
                ownerClass = qualifiedOwner;
                isStatic = true;
            } else {
                ownerClass = field.getOwnerClass();
                if (ownerClass == null || ownerClass.isEmpty()) {
                    ownerClass = ctx.getOwnerClass();
                }
            }
        }

        ownerClass = normalizeOwnerClass(ownerClass);

        Value receiverVal = null;
        if (!isStatic) {
            receiverVal = receiver != null ? lower(receiver) : ctx.getVariable("this");
            String fromValue = receiverOwner(receiverVal);
            if (fromValue != null) {
                ownerClass = fromValue;   // the receiver's actual type beats the decompiler's owner guess
            }
        }

        IRType fieldType = resolveFieldType(field, ownerClass);
        String descriptor = fieldType.getDescriptor();

        FieldAccessInstruction instr;
        if (isStatic) {
            instr = FieldAccessInstruction.createStaticStore(ownerClass, field.getFieldName(), descriptor, value);
        } else {
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

        SourceType arrayType = resolveArrayType(arr.getArray());
        if (arrayType instanceof ArraySourceType) {
            elementType = ((ArraySourceType) arrayType).getElementType().toIRType();
        } else if (declaredType != null && declaredType != ReferenceSourceType.OBJECT) {
            elementType = declaredType.toIRType();
        } else {
            elementType = new ReferenceType("java/lang/Object");
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
            if (ctx.hasVariable(varRef.getName())) {
                SSAValue val = ctx.getVariable(varRef.getName());
                if (val != null) {
                    IRType irType = val.getType();
                    if (irType instanceof ArrayType) {
                        ArrayType arrType = (ArrayType) irType;
                        return new ArraySourceType(irTypeToSourceType(arrType.getElementType()));
                    }
                }
            } else {
                String ownerClass = ctx.getOwnerClass();
                if (ownerClass != null && !ownerClass.isEmpty()) {
                    SourceType resolved = ctx.getTypeResolver().findFieldType(ownerClass, varRef.getName());
                    if (resolved != null) {
                        return resolved;
                    }
                }
            }
        }
        return arrayExpr.getType();
    }

    private SourceType irTypeToSourceType(IRType irType) {
        if (irType == PrimitiveType.INT) {
            return PrimitiveSourceType.INT;
        } else if (irType == PrimitiveType.LONG) {
            return PrimitiveSourceType.LONG;
        } else if (irType == PrimitiveType.FLOAT) {
            return PrimitiveSourceType.FLOAT;
        } else if (irType == PrimitiveType.DOUBLE) {
            return PrimitiveSourceType.DOUBLE;
        } else if (irType == PrimitiveType.BOOLEAN) {
            return PrimitiveSourceType.BOOLEAN;
        } else if (irType == PrimitiveType.BYTE) {
            return PrimitiveSourceType.BYTE;
        } else if (irType == PrimitiveType.CHAR) {
            return PrimitiveSourceType.CHAR;
        } else if (irType == PrimitiveType.SHORT) {
            return PrimitiveSourceType.SHORT;
        } else if (irType instanceof ReferenceType) {
            return new ReferenceSourceType(((ReferenceType) irType).getInternalName());
        } else if (irType instanceof ArrayType) {
            ArrayType arr = (ArrayType) irType;
            return new ArraySourceType(irTypeToSourceType(arr.getElementType()), arr.getDimensions());
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
        String className = normalizeOwnerClass(newExpr.getClassName());
        IRType type = new ReferenceType(className);
        SSAValue result = ctx.newValue(type);

        NewInstruction newInstr = new NewInstruction(result, className);
        ctx.getCurrentBlock().addInstruction(newInstr);

        List<Value> args = new ArrayList<>();
        args.add(result);

        for (Expression arg : newExpr.getArguments()) {
            args.add(lower(arg));
        }

        // Prefer the constructor's real (fully-qualified) descriptor from the pool; only fall back to building one
        // from the args when the class isn't loaded - and then from the lowered VALUE types (resolved), never the
        // raw AST types (which leave wildcard/same-package names unqualified -> e.g. `new LoginDialog(parent)`
        // emitting `(LFrame;)V` instead of `(Ljava/awt/Frame;)V` -> ClassNotFoundException: Frame).
        List<IRType> argIrTypes = new ArrayList<>();
        for (int k = 1; k < args.size(); k++) {
            argIrTypes.add(args.get(k).getType());
        }
        // Match the constructor by argument types (disambiguates same-arity overloads, e.g. ArrayList(int) vs
        // ArrayList(Collection)); only when the class isn't in the pool do we build a descriptor from the value types.
        String descriptor = ctx.getTypeResolver().resolveConstructorDescriptor(className, argIrTypes);
        if (descriptor == null) {
            StringBuilder descBuilder = new StringBuilder("(");
            for (IRType argIrType : argIrTypes) {
                descBuilder.append(argIrType.getDescriptor());
            }
            descBuilder.append(")V");
            descriptor = descBuilder.toString();
        }

        InvokeInstruction initInstr = new InvokeInstruction(
            InvokeType.SPECIAL, className, "<init>", descriptor, args
        );
        ctx.getCurrentBlock().addInstruction(initInstr);

        return result;
    }

    private Value lowerNewArray(NewArrayExpr newArr) {
        // new T[]{...}: no dimension expression, an inline initializer instead. Emit the length from the
        // element count, allocate, then store each element - otherwise NEWARRAY gets no count on the stack
        // (stack underflow) and the elements are dropped.
        if (newArr.hasInitializer()) {
            return lowerNewArrayWithInitializer(newArr);
        }

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

    private Value lowerNewArrayWithInitializer(NewArrayExpr newArr) {
        List<Expression> elements = newArr.getInitializer().getElements();
        int size = elements.size();
        IRType elementType = getElementType(newArr.getType());
        IRType arrayType = newArr.getType().toIRType();

        SSAValue sizeVal = ctx.newValue(PrimitiveType.INT);
        ctx.getCurrentBlock().addInstruction(new ConstantInstruction(sizeVal, IntConstant.of(size)));

        SSAValue result = ctx.newValue(arrayType);
        ctx.getCurrentBlock().addInstruction(new NewArrayInstruction(result, elementType, List.of(sizeVal)));

        int i = 0;
        for (Expression elem : elements) {
            Value elemVal = lower(elem);
            SSAValue indexVal = ctx.newValue(PrimitiveType.INT);
            ctx.getCurrentBlock().addInstruction(new ConstantInstruction(indexVal, IntConstant.of(i)));
            ctx.getCurrentBlock().addInstruction(ArrayAccessInstruction.createStore(result, indexVal, elemVal));
            i++;
        }

        return result;
    }

    private IRType getElementType(SourceType arrayType) {
        if (arrayType instanceof ArraySourceType) {
            ArraySourceType arr = (ArraySourceType) arrayType;
            return arr.getElementType().toIRType();
        }
        throw new LoweringException("Expected array type: " + arrayType);
    }

    private Value lowerCast(CastExpr cast) {
        Value operand = lower(cast.getExpression());
        SourceType toType = cast.getTargetType();

        SourceType fromType = cast.getExpression().getType();
        if (operand instanceof SSAValue) {
            SourceType actualType = irTypeToSourceType(operand.getType());
            if (actualType != null) {
                fromType = actualType;
            }
        }

        UnaryOp castOp = ReverseOperatorMapper.getCastOp(fromType, toType);
        if (castOp != null) {
            IRType resultType = toType.toIRType();
            SSAValue result = ctx.newValue(resultType);
            UnaryOpInstruction instr = new UnaryOpInstruction(result, castOp, operand);
            ctx.getCurrentBlock().addInstruction(instr);
            return result;
        }

        if (toType instanceof ReferenceSourceType) {
            IRType resultType = resolveTypeForConstant(toType);
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
            checkType = resolveTypeForConstant(inst.getCheckType());
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

    private Value lowerClass(ClassExpr classExpr) {
        IRType classType = resolveTypeForConstant(classExpr.getClassType());
        ClassConstant constant = new ClassConstant(classType);
        SSAValue result = ctx.newValue(ReferenceType.CLASS);
        ConstantInstruction instr = new ConstantInstruction(result, constant);
        ctx.getCurrentBlock().addInstruction(instr);
        return result;
    }

    private Value lowerSuper() {
        return ctx.getVariable("this");
    }

    private Value lowerInvokeDynamic(InvokeDynamicExpr expr) {
        List<Value> args = new ArrayList<>();
        for (Expression arg : expr.getArguments()) {
            args.add(lower(arg));
        }

        String bsmDesc = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;" +
                         "Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;";

        MethodHandleConstant bsm = new MethodHandleConstant(
            MethodHandleConstant.REF_invokeStatic,
            expr.getBootstrapOwner(),
            expr.getBootstrapName(),
            bsmDesc
        );

        // For SwitchBootstraps.typeSwitch the bootstrap static arguments are the case-type Class
        // constants, in declaration order; other bootstraps modeled here carry none.
        List<Constant> bsArgs = new ArrayList<>();
        for (String classArg : expr.getBootstrapClassArgs()) {
            bsArgs.add(new ClassConstant(classArg));
        }
        BootstrapMethodInfo bsInfo = new BootstrapMethodInfo(bsm, bsArgs);

        IRType returnType = expr.getType().toIRType();
        SSAValue result = null;
        if (!(returnType instanceof VoidType)) {
            result = ctx.newValue(returnType);
        }

        InvokeInstruction indy = new InvokeInstruction(
            result, InvokeType.DYNAMIC, null,
            expr.getName(), expr.getDescriptor(),
            args, 0, bsInfo
        );
        ctx.getCurrentBlock().addInstruction(indy);

        return result != null ? result : NullConstant.INSTANCE;
    }

    private Value lowerDynamicConstant(DynamicConstantExpr expr) {
        DynamicConstant dynConst = new DynamicConstant(
            expr.getName(),
            expr.getDescriptor(),
            expr.getBootstrapMethodIndex(),
            0
        );

        IRType resultType = expr.getType().toIRType();
        SSAValue result = ctx.newValue(resultType);
        ConstantInstruction instr = new ConstantInstruction(result, dynConst);
        ctx.getCurrentBlock().addInstruction(instr);
        return result;
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

    private static boolean isReferenceValue(Value v) {
        return v != null && v.getType() != null && v.getType().isReference();
    }

    private SourceType getCommonComparisonType(SourceType left, SourceType right) {
        if (left == PrimitiveSourceType.DOUBLE || right == PrimitiveSourceType.DOUBLE) {
            return PrimitiveSourceType.DOUBLE;
        }
        if (left == PrimitiveSourceType.FLOAT || right == PrimitiveSourceType.FLOAT) {
            return PrimitiveSourceType.FLOAT;
        }
        if (left == PrimitiveSourceType.LONG || right == PrimitiveSourceType.LONG) {
            return PrimitiveSourceType.LONG;
        }
        return PrimitiveSourceType.INT;
    }

    private Value lowerLambda(LambdaExpr lambda) {
        List<SyntheticLambdaMethod.CapturedVariable> captures = collectCaptures(lambda);
        String lambdaMethodName = ctx.generateLambdaMethodName();
        String ownerClass = ctx.getOwnerClass();
        if (ownerClass == null) {
            ownerClass = "UnknownClass";
        }

        SourceType lambdaType = lambda.getType();
        String samInterfaceName = extractInterfaceName(lambdaType);

        // Resolve the functional interface's single abstract method so the lambda's return type
        // and SAM descriptor reflect the real interface (e.g. Runnable -> ()V) rather than
        // defaulting block-bodied lambdas to Object. This keeps the invokedynamic call site and a
        // later-materialized synthetic method in agreement.
        String[] sam = ctx.getTypeResolver().resolveSamMethod(samInterfaceName);
        SourceType returnType;
        String samMethodName;
        String samDescriptor;
        if (sam != null) {
            samMethodName = sam[0];
            samDescriptor = sam[1];
            returnType = ctx.getTypeResolver().returnTypeFromDescriptor(sam[1]);
        } else {
            returnType = inferLambdaReturnType(lambda);
            samMethodName = extractSamMethodName(samInterfaceName);
            samDescriptor = extractSamDescriptor(lambda.getParameters(), returnType);
        }

        String syntheticDescriptor = buildSyntheticMethodDescriptor(captures, lambda.getParameters(), returnType);
        boolean isStatic = !capturesThis(captures);

        SyntheticLambdaMethod synthetic = new SyntheticLambdaMethod(
            lambdaMethodName, syntheticDescriptor, isStatic,
            captures, lambda.getBody(), lambda.getParameters(), returnType
        );
        ctx.registerSyntheticMethod(synthetic);

        MethodHandleConstant bsm = new MethodHandleConstant(
            MethodHandleConstant.REF_invokeStatic,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
            "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)" +
            "Ljava/lang/invoke/CallSite;"
        );

        int implRefKind = isStatic ? MethodHandleConstant.REF_invokeStatic : MethodHandleConstant.REF_invokeSpecial;
        MethodHandleConstant implHandle = new MethodHandleConstant(
            implRefKind, ownerClass, lambdaMethodName, syntheticDescriptor
        );

        List<Constant> bsArgs = new ArrayList<>();
        bsArgs.add(new MethodTypeConstant(samDescriptor));
        bsArgs.add(implHandle);
        bsArgs.add(new MethodTypeConstant(samDescriptor));

        BootstrapMethodInfo bsInfo = new BootstrapMethodInfo(bsm, bsArgs);

        List<Value> dynArgs = new ArrayList<>();
        for (SyntheticLambdaMethod.CapturedVariable capture : captures) {
            if ("this".equals(capture.getName())) {
                dynArgs.add(ctx.getVariable("this"));
            } else {
                dynArgs.add(ctx.getVariable(capture.getName()));
            }
        }

        String callSiteDescriptor = buildCallSiteDescriptor(captures, lambdaType);
        IRType resultType = lambdaType.toIRType();
        SSAValue result = ctx.newValue(resultType);

        InvokeInstruction indy = new InvokeInstruction(
            result, InvokeType.DYNAMIC, null, samMethodName, callSiteDescriptor, dynArgs, 0, bsInfo
        );
        ctx.getCurrentBlock().addInstruction(indy);

        return result;
    }

    private Value lowerMethodRef(MethodRefExpr methodRef) {
        String ownerClass = methodRef.getOwnerClass();
        String methodName = methodRef.getMethodName();
        MethodRefKind kind = methodRef.getKind();
        SourceType returnType = methodRef.getType();

        String samInterfaceName = extractInterfaceName(returnType);
        String samMethodName = extractSamMethodName(samInterfaceName);

        int refKind;
        String implDescriptor;
        boolean hasBoundReceiver = false;
        Value boundReceiver = null;

        switch (kind) {
            case STATIC:
                refKind = MethodHandleConstant.REF_invokeStatic;
                implDescriptor = inferMethodDescriptor(ownerClass, methodName, kind, returnType);
                break;
            case INSTANCE:
                refKind = MethodHandleConstant.REF_invokeVirtual;
                implDescriptor = inferMethodDescriptor(ownerClass, methodName, kind, returnType);
                break;
            case BOUND:
                refKind = MethodHandleConstant.REF_invokeVirtual;
                implDescriptor = inferMethodDescriptor(ownerClass, methodName, kind, returnType);
                if (methodRef.getReceiver() != null) {
                    boundReceiver = lower(methodRef.getReceiver());
                    hasBoundReceiver = true;
                }
                break;
            case CONSTRUCTOR:
                refKind = MethodHandleConstant.REF_newInvokeSpecial;
                methodName = "<init>";
                implDescriptor = inferConstructorDescriptor(ownerClass, returnType);
                break;
            case ARRAY_CONSTRUCTOR:
                return lowerArrayConstructorRef(methodRef);
            default:
                throw new LoweringException("Unsupported method reference kind: " + kind);
        }

        MethodHandleConstant bsm = new MethodHandleConstant(
            MethodHandleConstant.REF_invokeStatic,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
            "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)" +
            "Ljava/lang/invoke/CallSite;"
        );

        MethodHandleConstant implHandle = new MethodHandleConstant(refKind, ownerClass, methodName, implDescriptor);

        String samDescriptor = inferSamDescriptorFromMethodRef(methodRef, implDescriptor, kind);

        List<Constant> bsArgs = new ArrayList<>();
        bsArgs.add(new MethodTypeConstant(samDescriptor));
        bsArgs.add(implHandle);
        bsArgs.add(new MethodTypeConstant(samDescriptor));

        BootstrapMethodInfo bsInfo = new BootstrapMethodInfo(bsm, bsArgs);

        List<Value> dynArgs = new ArrayList<>();
        if (hasBoundReceiver && boundReceiver != null) {
            dynArgs.add(boundReceiver);
        }

        String callSiteDescriptor = buildMethodRefCallSiteDescriptor(methodRef, hasBoundReceiver);
        IRType resultType = returnType.toIRType();
        SSAValue result = ctx.newValue(resultType);

        InvokeInstruction indy = new InvokeInstruction(
            result, InvokeType.DYNAMIC, null, samMethodName, callSiteDescriptor, dynArgs, 0, bsInfo
        );
        ctx.getCurrentBlock().addInstruction(indy);

        return result;
    }

    private Value lowerArrayConstructorRef(MethodRefExpr methodRef) {
        SourceType returnType = methodRef.getType();
        String samInterfaceName = extractInterfaceName(returnType);
        String samMethodName = extractSamMethodName(samInterfaceName);

        String arrayTypeName = methodRef.getOwnerClass();
        ArrayTypeInfo arrayInfo = parseArrayType(arrayTypeName);

        String syntheticName = ctx.generateArrayConstructorMethodName();
        String arrayDescriptor = arrayInfo.getArrayDescriptor();
        String syntheticDescriptor = "(I)" + arrayDescriptor;

        SyntheticArrayConstructor synthetic = new SyntheticArrayConstructor(
            syntheticName, arrayInfo.elementType, arrayInfo.dimensions
        );
        ctx.registerArrayConstructor(synthetic);

        String ownerClass = ctx.getOwnerClass();
        if (ownerClass == null || ownerClass.isEmpty()) {
            ownerClass = "UnknownClass";
        }

        MethodHandleConstant bsm = new MethodHandleConstant(
            MethodHandleConstant.REF_invokeStatic,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
            "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)" +
            "Ljava/lang/invoke/CallSite;"
        );

        MethodHandleConstant implHandle = new MethodHandleConstant(
            MethodHandleConstant.REF_invokeStatic,
            ownerClass,
            syntheticName,
            syntheticDescriptor
        );

        String samDescErased = "(I)Ljava/lang/Object;";

        List<Constant> bsArgs = new ArrayList<>();
        bsArgs.add(new MethodTypeConstant(samDescErased));
        bsArgs.add(implHandle);
        bsArgs.add(new MethodTypeConstant(syntheticDescriptor));

        BootstrapMethodInfo bsInfo = new BootstrapMethodInfo(bsm, bsArgs);

        String callSiteDescriptor = "()" + returnType.toIRType().getDescriptor();
        IRType resultType = returnType.toIRType();
        SSAValue result = ctx.newValue(resultType);

        InvokeInstruction indy = new InvokeInstruction(
            result, InvokeType.DYNAMIC, null, samMethodName, callSiteDescriptor, new ArrayList<>(), 0, bsInfo
        );
        ctx.getCurrentBlock().addInstruction(indy);

        return result;
    }

    private List<SyntheticLambdaMethod.CapturedVariable> collectCaptures(LambdaExpr lambda) {
        Set<String> paramNames = new HashSet<>();
        for (LambdaParameter param : lambda.getParameters()) {
            paramNames.add(param.name());
        }

        Set<String> capturedNames = new HashSet<>();
        List<SyntheticLambdaMethod.CapturedVariable> captures = new ArrayList<>();

        CaptureCollector collector = new CaptureCollector(paramNames, capturedNames);
        lambda.getBody().accept(collector);

        for (String name : capturedNames) {
            if (ctx.hasVariable(name)) {
                SSAValue val = ctx.getVariable(name);
                SourceType type = irTypeToSourceType(val.getType());
                captures.add(new SyntheticLambdaMethod.CapturedVariable(name, type));
            }
        }

        return captures;
    }

    private boolean capturesThis(List<SyntheticLambdaMethod.CapturedVariable> captures) {
        for (SyntheticLambdaMethod.CapturedVariable c : captures) {
            if ("this".equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    private SourceType inferLambdaReturnType(LambdaExpr lambda) {
        ASTNode body = lambda.getBody();
        if (body instanceof Expression) {
            return ((Expression) body).getType();
        }
        return ReferenceSourceType.OBJECT;
    }

    private String buildSyntheticMethodDescriptor(List<SyntheticLambdaMethod.CapturedVariable> captures,
                                                   List<LambdaParameter> params, SourceType returnType) {
        StringBuilder sb = new StringBuilder("(");
        for (SyntheticLambdaMethod.CapturedVariable c : captures) {
            sb.append(c.getType().toIRType().getDescriptor());
        }
        for (LambdaParameter p : params) {
            if (p.type() != null) {
                sb.append(p.type().toIRType().getDescriptor());
            } else {
                sb.append("Ljava/lang/Object;");
            }
        }
        sb.append(")");
        if (returnType == null || returnType instanceof VoidSourceType) {
            sb.append("V");
        } else {
            sb.append(returnType.toIRType().getDescriptor());
        }
        return sb.toString();
    }

    private String extractInterfaceName(SourceType type) {
        if (type instanceof ReferenceSourceType) {
            return ((ReferenceSourceType) type).getInternalName();
        }
        return "java/lang/Object";
    }

    private String extractSamMethodName(String interfaceName) {
        if (interfaceName.endsWith("Supplier")) return "get";
        if (interfaceName.endsWith("Consumer")) return "accept";
        if (interfaceName.endsWith("Function")) return "apply";
        if (interfaceName.endsWith("BiFunction")) return "apply";
        if (interfaceName.endsWith("Predicate")) return "test";
        if (interfaceName.endsWith("Runnable")) return "run";
        if (interfaceName.endsWith("Callable")) return "call";
        if (interfaceName.endsWith("Comparator")) return "compare";
        return "apply";
    }

    private String extractSamDescriptor(List<LambdaParameter> params, SourceType returnType) {
        StringBuilder sb = new StringBuilder("(");
        for (LambdaParameter p : params) {
            if (p.type() != null) {
                sb.append(p.type().toIRType().getDescriptor());
            } else {
                sb.append("Ljava/lang/Object;");
            }
        }
        sb.append(")");
        if (returnType == null || returnType instanceof VoidSourceType) {
            sb.append("V");
        } else {
            sb.append(returnType.toIRType().getDescriptor());
        }
        return sb.toString();
    }

    private String buildCallSiteDescriptor(List<SyntheticLambdaMethod.CapturedVariable> captures, SourceType lambdaType) {
        StringBuilder sb = new StringBuilder("(");
        for (SyntheticLambdaMethod.CapturedVariable c : captures) {
            sb.append(c.getType().toIRType().getDescriptor());
        }
        sb.append(")");
        sb.append(lambdaType.toIRType().getDescriptor());
        return sb.toString();
    }

    private String inferMethodDescriptor(String ownerClass, String methodName, MethodRefKind kind, SourceType samType) {
        int expectedParamCount = inferExpectedParamCount(samType, kind);
        String descriptor = ctx.getTypeResolver().resolveMethodDescriptor(ownerClass, methodName, expectedParamCount);
        if (descriptor != null) {
            return descriptor;
        }
        return "()Ljava/lang/Object;";
    }

    private String inferConstructorDescriptor(String ownerClass, SourceType samType) {
        int expectedParamCount = inferExpectedParamCount(samType, MethodRefKind.CONSTRUCTOR);
        String descriptor = ctx.getTypeResolver().resolveConstructorDescriptor(ownerClass, expectedParamCount);
        if (descriptor != null) {
            return descriptor;
        }
        return "()V";
    }

    private int inferExpectedParamCount(SourceType samType, MethodRefKind kind) {
        if (samType == null) {
            return -1;
        }
        String typeName = extractInterfaceName(samType);
        int baseCount = getSamParamCount(typeName);
        if (kind == MethodRefKind.INSTANCE && baseCount > 0) {
            return baseCount - 1;
        }
        return baseCount;
    }

    private int getSamParamCount(String interfaceName) {
        if (interfaceName.endsWith("Supplier") || interfaceName.endsWith("Callable")) return 0;
        if (interfaceName.endsWith("Runnable")) return 0;
        if (interfaceName.endsWith("Consumer") || interfaceName.endsWith("Function") ||
            interfaceName.endsWith("Predicate") || interfaceName.endsWith("UnaryOperator")) return 1;
        if (interfaceName.endsWith("BiConsumer") || interfaceName.endsWith("BiFunction") ||
            interfaceName.endsWith("BiPredicate") || interfaceName.endsWith("BinaryOperator") ||
            interfaceName.endsWith("Comparator")) return 2;
        if (interfaceName.contains("IntFunction") || interfaceName.contains("LongFunction") ||
            interfaceName.contains("DoubleFunction") || interfaceName.contains("ToIntFunction") ||
            interfaceName.contains("ToLongFunction") || interfaceName.contains("ToDoubleFunction")) return 1;
        return -1;
    }

    private String inferSamDescriptorFromMethodRef(MethodRefExpr methodRef, String implDescriptor, MethodRefKind kind) {
        SourceType targetType = methodRef.getType();
        if (targetType != null) {
            String internalName = extractInterfaceName(targetType);
            if (internalName != null) {
                int lastSlash = internalName.lastIndexOf('/');
                String simpleName = lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
                String samDescriptor = getSamDescriptor(simpleName, implDescriptor);
                if (samDescriptor != null) {
                    return samDescriptor;
                }
            }
        }

        if (kind == MethodRefKind.INSTANCE) {
            String returnDesc = implDescriptor.substring(implDescriptor.indexOf(')') + 1);
            return "(Ljava/lang/Object;)" + returnDesc;
        }
        return implDescriptor;
    }

    private String getSamDescriptor(String interfaceName, String implDescriptor) {
        String implReturn = implDescriptor.substring(implDescriptor.indexOf(')') + 1);

        switch (interfaceName) {
            case "Runnable":
                return "()V";
            case "Callable":
            case "Supplier":
                return "()" + implReturn;
            case "BooleanSupplier":
                return "()Z";
            case "IntSupplier":
                return "()I";
            case "LongSupplier":
                return "()J";
            case "DoubleSupplier":
                return "()D";
            case "Consumer":
                return "(Ljava/lang/Object;)V";
            case "IntConsumer":
                return "(I)V";
            case "LongConsumer":
                return "(J)V";
            case "DoubleConsumer":
                return "(D)V";
            case "BiConsumer":
                return "(Ljava/lang/Object;Ljava/lang/Object;)V";
            case "ObjIntConsumer":
                return "(Ljava/lang/Object;I)V";
            case "ObjLongConsumer":
                return "(Ljava/lang/Object;J)V";
            case "ObjDoubleConsumer":
                return "(Ljava/lang/Object;D)V";
            case "Predicate":
                return "(Ljava/lang/Object;)Z";
            case "IntPredicate":
                return "(I)Z";
            case "LongPredicate":
                return "(J)Z";
            case "DoublePredicate":
                return "(D)Z";
            case "BiPredicate":
                return "(Ljava/lang/Object;Ljava/lang/Object;)Z";
            case "Function":
                return "(Ljava/lang/Object;)" + implReturn;
            case "IntFunction":
                return "(I)" + implReturn;
            case "LongFunction":
                return "(J)" + implReturn;
            case "DoubleFunction":
                return "(D)" + implReturn;
            case "ToIntFunction":
                return "(Ljava/lang/Object;)I";
            case "ToLongFunction":
                return "(Ljava/lang/Object;)J";
            case "ToDoubleFunction":
                return "(Ljava/lang/Object;)D";
            case "IntToLongFunction":
                return "(I)J";
            case "IntToDoubleFunction":
                return "(I)D";
            case "LongToIntFunction":
                return "(J)I";
            case "LongToDoubleFunction":
                return "(J)D";
            case "DoubleToIntFunction":
                return "(D)I";
            case "DoubleToLongFunction":
                return "(D)J";
            case "BiFunction":
                return "(Ljava/lang/Object;Ljava/lang/Object;)" + implReturn;
            case "ToIntBiFunction":
                return "(Ljava/lang/Object;Ljava/lang/Object;)I";
            case "ToLongBiFunction":
                return "(Ljava/lang/Object;Ljava/lang/Object;)J";
            case "ToDoubleBiFunction":
                return "(Ljava/lang/Object;Ljava/lang/Object;)D";
            case "UnaryOperator":
                return "(Ljava/lang/Object;)Ljava/lang/Object;";
            case "IntUnaryOperator":
                return "(I)I";
            case "LongUnaryOperator":
                return "(J)J";
            case "DoubleUnaryOperator":
                return "(D)D";
            case "BinaryOperator":
                return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
            case "IntBinaryOperator":
                return "(II)I";
            case "LongBinaryOperator":
                return "(JJ)J";
            case "DoubleBinaryOperator":
                return "(DD)D";
            case "Comparator":
                return "(Ljava/lang/Object;Ljava/lang/Object;)I";
            default:
                return null;
        }
    }

    private String buildMethodRefCallSiteDescriptor(MethodRefExpr methodRef, boolean hasBoundReceiver) {
        StringBuilder sb = new StringBuilder("(");
        if (hasBoundReceiver && methodRef.getReceiver() != null) {
            SourceType receiverType = methodRef.getReceiver().getType();
            sb.append(receiverType.toIRType().getDescriptor());
        }
        sb.append(")");
        sb.append(methodRef.getType().toIRType().getDescriptor());
        return sb.toString();
    }

    private static class CaptureCollector extends AbstractSourceVisitor<Void> {
        private final Set<String> paramNames;
        private final Set<String> capturedNames;

        CaptureCollector(Set<String> paramNames, Set<String> capturedNames) {
            this.paramNames = paramNames;
            this.capturedNames = capturedNames;
        }

        @Override
        public Void visitVarRef(VarRefExpr expr) {
            String name = expr.getName();
            if (!paramNames.contains(name)) {
                capturedNames.add(name);
            }
            return null;
        }
    }

    private static class ArrayTypeInfo {
        final SourceType elementType;
        final int dimensions;

        ArrayTypeInfo(SourceType elementType, int dimensions) {
            this.elementType = elementType;
            this.dimensions = dimensions;
        }

        String getArrayDescriptor() {
            return "[".repeat(Math.max(0, dimensions)) +
                    elementType.toIRType().getDescriptor();
        }
    }

    private ArrayTypeInfo parseArrayType(String arrayTypeName) {
        int dims = 0;
        String baseName = arrayTypeName;

        while (baseName.endsWith("[]")) {
            dims++;
            baseName = baseName.substring(0, baseName.length() - 2);
        }

        if (dims == 0) {
            dims = countLeadingBrackets(arrayTypeName);
            if (dims > 0) {
                baseName = arrayTypeName.substring(dims);
                if (baseName.startsWith("L") && baseName.endsWith(";")) {
                    baseName = baseName.substring(1, baseName.length() - 1);
                }
            }
        }

        if (dims == 0) {
            dims = 1;
        }

        SourceType elementType = parseElementType(baseName);
        return new ArrayTypeInfo(elementType, dims);
    }

    private int countLeadingBrackets(String s) {
        int count = 0;
        for (int i = 0; i < s.length() && s.charAt(i) == '['; i++) {
            count++;
        }
        return count;
    }

    private SourceType parseElementType(String typeName) {
        switch (typeName) {
            case "int":
            case "I":
                return PrimitiveSourceType.INT;
            case "long":
            case "J":
                return PrimitiveSourceType.LONG;
            case "double":
            case "D":
                return PrimitiveSourceType.DOUBLE;
            case "float":
            case "F":
                return PrimitiveSourceType.FLOAT;
            case "boolean":
            case "Z":
                return PrimitiveSourceType.BOOLEAN;
            case "byte":
            case "B":
                return PrimitiveSourceType.BYTE;
            case "char":
            case "C":
                return PrimitiveSourceType.CHAR;
            case "short":
            case "S":
                return PrimitiveSourceType.SHORT;
            default:
                String internalName = typeName.replace('.', '/');
                return new ReferenceSourceType(internalName);
        }
    }
}
