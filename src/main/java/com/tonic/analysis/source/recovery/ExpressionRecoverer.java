package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;
import com.tonic.analysis.ssa.visitor.AbstractIRVisitor;

/**
 * Converts SSA IR instructions to source Expression trees.
 */
public class ExpressionRecoverer {

    private final RecoveryContext context;
    private final TypeRecoverer typeRecoverer;

    public ExpressionRecoverer(RecoveryContext context) {
        this.context = context;
        this.typeRecoverer = new TypeRecoverer();
    }

    public Expression recover(IRInstruction instr) {
        return instr.accept(new RecoveryVisitor());
    }

    public Expression recoverOperand(Value value) {
        if (value instanceof Constant c) {
            return recoverConstant(c);
        }
        SSAValue ssa = (SSAValue) value;

        // Check for cached expression first
        if (context.isRecovered(ssa)) {
            return context.getCachedExpression(ssa);
        }

        // For values without cached expressions, check if they should be inlined
        IRInstruction def = ssa.getDefinition();
        if (def != null && shouldInlineExpression(def)) {
            Expression expr = recover(def);
            context.cacheExpression(ssa, expr);
            return expr;
        }

        // Fall back to variable reference
        String name = context.getVariableName(ssa);
        if (name == null) name = "v" + ssa.getId();
        SourceType type = typeRecoverer.recoverType(ssa);
        // Return ThisExpr for 'this' references
        if ("this".equals(name)) {
            return new ThisExpr(type);
        }
        return new VarRefExpr(name, type, ssa);
    }

    /**
     * Determines if an instruction's result should be inlined at usage sites
     * rather than assigned to an intermediate variable.
     */
    private boolean shouldInlineExpression(IRInstruction instr) {
        // Pure expressions that should be inlined:
        // - Field accesses (static and instance)
        // - Constants
        // - Method invocations (for conditions like `if (obj.method())`)
        return instr instanceof GetFieldInstruction ||
               instr instanceof ConstantInstruction ||
               instr instanceof InvokeInstruction;
    }

    private Expression recoverConstant(Constant c) {
        if (c instanceof IntConstant i) {
            return LiteralExpr.ofInt(i.getValue());
        } else if (c instanceof LongConstant l) {
            return LiteralExpr.ofLong(l.getValue());
        } else if (c instanceof FloatConstant f) {
            return LiteralExpr.ofFloat(f.getValue());
        } else if (c instanceof DoubleConstant d) {
            return LiteralExpr.ofDouble(d.getValue());
        } else if (c instanceof StringConstant s) {
            return LiteralExpr.ofString(s.getValue());
        } else if (c instanceof NullConstant) {
            return LiteralExpr.ofNull();
        }
        return LiteralExpr.ofNull();
    }

    private class RecoveryVisitor extends AbstractIRVisitor<Expression> {
        @Override
        public Expression visitBinaryOp(BinaryOpInstruction instr) {
            Expression left = recoverOperand(instr.getLeft());
            Expression right = recoverOperand(instr.getRight());
            BinaryOperator op = OperatorMapper.mapBinaryOp(instr.getOp());
            SourceType type = typeRecoverer.recoverType(instr.getResult());
            return new BinaryExpr(op, left, right, type);
        }

        @Override
        public Expression visitUnaryOp(UnaryOpInstruction instr) {
            Expression operand = recoverOperand(instr.getOperand());
            if (OperatorMapper.isTypeConversion(instr.getOp())) {
                String desc = OperatorMapper.getConversionTargetType(instr.getOp());
                SourceType target = typeRecoverer.recoverType(instr.getResult());
                return new CastExpr(target, operand);
            }
            UnaryOperator op = OperatorMapper.mapUnaryOp(instr.getOp());
            SourceType type = typeRecoverer.recoverType(instr.getResult());
            return new UnaryExpr(op, operand, type);
        }

        @Override
        public Expression visitConstant(ConstantInstruction instr) {
            return recoverConstant(instr.getConstant());
        }

        @Override
        public Expression visitInvoke(InvokeInstruction instr) {
            Expression receiver = null;
            if (instr.getInvokeType() != InvokeType.STATIC && !instr.getArguments().isEmpty()) {
                receiver = recoverOperand(instr.getArguments().get(0));
            }
            java.util.List<Expression> args = new java.util.ArrayList<>();
            int start = (instr.getInvokeType() == InvokeType.STATIC) ? 0 : 1;
            for (int i = start; i < instr.getArguments().size(); i++) {
                args.add(recoverOperand(instr.getArguments().get(i)));
            }
            SourceType retType = typeRecoverer.recoverType(instr.getResult());
            boolean isStatic = instr.getInvokeType() == InvokeType.STATIC;
            return new MethodCallExpr(receiver, instr.getName(), instr.getOwner(), args, isStatic, retType);
        }

        @Override
        public Expression visitNew(NewInstruction instr) {
            return new NewExpr(instr.getClassName());
        }

        @Override
        public Expression visitGetField(GetFieldInstruction instr) {
            Expression receiver = instr.isStatic() ? null : recoverOperand(instr.getObjectRef());
            SourceType type = typeRecoverer.recoverType(instr.getResult());
            return new FieldAccessExpr(receiver, instr.getName(), instr.getOwner(), instr.isStatic(), type);
        }

        @Override
        public Expression visitArrayLoad(ArrayLoadInstruction instr) {
            Expression array = recoverOperand(instr.getArray());
            Expression index = recoverOperand(instr.getIndex());
            SourceType type = typeRecoverer.recoverType(instr.getResult());
            return new ArrayAccessExpr(array, index, type);
        }

        @Override
        public Expression visitLoadLocal(LoadLocalInstruction instr) {
            // LoadLocal just returns the variable reference for the SSA result
            return recoverOperand(instr.getResult());
        }

        @Override
        protected Expression defaultValue() {
            return LiteralExpr.ofNull();
        }
    }
}
