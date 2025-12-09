package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.cfg.IRMethod;
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
        return recoverOperand(value, null);
    }

    /**
     * Recovers an operand with optional type hint for boolean detection.
     */
    public Expression recoverOperand(Value value, SourceType typeHint) {
        if (value instanceof Constant c) {
            return recoverConstant(c, typeHint);
        }
        SSAValue ssa = (SSAValue) value;

        // If this value has been materialized into a variable, return a variable reference
        // This prevents inlining the same expression multiple times
        if (context.isMaterialized(ssa)) {
            String name = context.getVariableName(ssa);
            if (name != null) {
                SourceType type = typeRecoverer.recoverType(ssa);
                if ("this".equals(name)) {
                    return new ThisExpr(type);
                }
                return new VarRefExpr(name, type, ssa);
            }
        }

        // For values without cached expressions, check if they should be inlined
        IRInstruction def = ssa.getDefinition();
        if (def != null && shouldInlineExpression(def)) {
            // Special handling for constants - don't use cache since type hint matters
            // The same int value 0 could be boolean false in one context, int 0 in another
            if (def instanceof ConstantInstruction constInstr) {
                return recoverConstant(constInstr.getConstant(), typeHint);
            }

            // LoadLocal should ALWAYS be freshly recovered - don't use cache
            // This ensures slot 0 always returns 'this' in instance methods
            if (def instanceof LoadLocalInstruction) {
                return recover(def);
            }

            // For non-constants, check cache
            if (context.isRecovered(ssa)) {
                return context.getCachedExpression(ssa);
            }
            Expression expr = recover(def);
            context.cacheExpression(ssa, expr);
            return expr;
        }

        // Check cache for non-inlined values
        if (context.isRecovered(ssa)) {
            return context.getCachedExpression(ssa);
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
        // Constants should always be inlined
        if (instr instanceof ConstantInstruction) {
            return true;
        }

        // LoadLocal should always be inlined - it just returns the local variable or 'this'
        if (instr instanceof LoadLocalInstruction) {
            return true;
        }

        // PHI instructions should NEVER be inlined - they represent merged values
        // and should always reference the declared variable
        if (instr instanceof PhiInstruction) {
            return false;
        }

        // Check for single-use values - these can be safely inlined
        SSAValue result = instr.getResult();
        if (result != null) {
            int useCount = result.getUses().size();
            if (useCount <= 1) {
                // Single-use values can be inlined for most instruction types
                // This includes: BinaryOp, UnaryOp, ArrayLoad, GetField, Invoke, Cast, etc.
                return true;
            }
        }

        // For multi-use method calls and field accesses, don't inline (use variable)
        // Other multi-use instructions should also not be inlined
        return false;
    }

    private Expression recoverConstant(Constant c) {
        return recoverConstant(c, null);
    }

    /**
     * Recovers a constant with optional type hint for boolean detection.
     */
    public Expression recoverConstant(Constant c, SourceType typeHint) {
        if (c instanceof IntConstant i) {
            int val = i.getValue();
            // Check if this is a boolean context
            if (typeHint instanceof com.tonic.analysis.source.ast.type.PrimitiveSourceType pst
                && pst == com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN) {
                return LiteralExpr.ofBoolean(val != 0);
            }
            // Heuristic: 0 or 1 in certain contexts may be boolean
            return LiteralExpr.ofInt(val);
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
        } else if (c instanceof ClassConstant cc) {
            // Handle class literals like MyClass.class
            String className = cc.getClassName();
            // className is in internal format (e.g., "java/lang/String" or "bm")
            // Create ReferenceSourceType directly from internal name
            SourceType classType = new ReferenceSourceType(className, java.util.Collections.emptyList());
            return new ClassExpr(classType);
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
            // Handle <init> constructor calls
            if ("<init>".equals(instr.getName())) {
                return handleConstructorCall(instr);
            }

            Expression receiver = null;
            if (instr.getInvokeType() != InvokeType.STATIC && !instr.getArguments().isEmpty()) {
                Value receiverValue = instr.getArguments().get(0);
                // Skip 'this' receiver for method calls in same class
                if (receiverValue instanceof SSAValue ssaReceiver) {
                    String name = context.getVariableName(ssaReceiver);
                    if (!"this".equals(name)) {
                        receiver = recoverOperand(receiverValue);
                    }
                } else {
                    receiver = recoverOperand(receiverValue);
                }
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

        private Expression handleConstructorCall(InvokeInstruction instr) {
            java.util.List<Expression> args = new java.util.ArrayList<>();
            // Constructor args start at index 1 (index 0 is the uninitialized object)
            for (int i = 1; i < instr.getArguments().size(); i++) {
                args.add(recoverOperand(instr.getArguments().get(i)));
            }

            // Check if this is super() or this() call
            if (!instr.getArguments().isEmpty()) {
                Value receiver = instr.getArguments().get(0);
                if (receiver instanceof SSAValue ssaReceiver) {
                    String name = context.getVariableName(ssaReceiver);
                    if ("this".equals(name)) {
                        // this.<init>() is either super() or this()
                        String ownerClass = instr.getOwner();
                        String methodClass = context.getIrMethod().getOwnerClass();
                        if (ownerClass != null && methodClass != null && !ownerClass.equals(methodClass)) {
                            // Calling parent constructor
                            return new MethodCallExpr(null, "super", ownerClass, args, false, null);
                        } else {
                            // Calling this() - delegating constructor
                            return new MethodCallExpr(null, "this", ownerClass, args, false, null);
                        }
                    }

                    // Check if receiver is a pending new instruction
                    if (context.isPendingNew(ssaReceiver)) {
                        String className = context.consumePendingNew(ssaReceiver);
                        NewExpr newExpr = new NewExpr(className);
                        for (Expression arg : args) {
                            newExpr.addArgument(arg);
                        }
                        // Cache this combined expression for later use
                        context.cacheExpression(ssaReceiver, newExpr);
                        return newExpr;
                    }
                }
            }

            // Fallback: create NewExpr with the owner class
            NewExpr newExpr = new NewExpr(instr.getOwner());
            for (Expression arg : args) {
                newExpr.addArgument(arg);
            }
            return newExpr;
        }

        @Override
        public Expression visitNew(NewInstruction instr) {
            // Register this as a pending new instruction - will be combined with <init> call
            if (instr.getResult() != null) {
                context.registerPendingNew(instr.getResult(), instr.getClassName());
            }
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
            // LoadLocal should return a reference to the source local slot
            int localIndex = instr.getLocalIndex();
            // For instance methods, slot 0 is 'this'
            IRMethod method = instr.getBlock() != null ? instr.getBlock().getMethod() : null;
            boolean isStatic = method == null || method.isStatic();

            if (!isStatic && localIndex == 0) {
                // Return 'this' reference
                SourceType type = typeRecoverer.recoverType(instr.getResult());
                return new ThisExpr(type);
            }

            // For other locals, return a variable reference
            String name = "local" + localIndex;
            SourceType type = typeRecoverer.recoverType(instr.getResult());
            return new VarRefExpr(name, type, instr.getResult());
        }

        @Override
        public Expression visitCast(CastInstruction instr) {
            // Recover the operand being cast
            Expression operand = recoverOperand(instr.getObjectRef());
            // Get the target type from the instruction
            SourceType targetType = SourceType.fromIRType(instr.getTargetType());
            return new CastExpr(targetType, operand);
        }

        @Override
        protected Expression defaultValue() {
            return LiteralExpr.ofNull();
        }
    }
}
