package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.*;
import com.tonic.analysis.ssa.visitor.AbstractIRVisitor;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.parser.attribute.table.BootstrapMethod;
import com.tonic.parser.constpool.*;
import com.tonic.parser.constpool.structure.MethodHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        if (value instanceof Constant) {
            Constant c = (Constant) value;
            return recoverConstant(c, typeHint);
        }
        SSAValue ssa = (SSAValue) value;

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

        IRInstruction def = ssa.getDefinition();
        if (def != null && shouldInlineExpression(def)) {
            if (def instanceof ConstantInstruction) {
                ConstantInstruction constInstr = (ConstantInstruction) def;
                return recoverConstant(constInstr.getConstant(), typeHint);
            }

            if (def instanceof LoadLocalInstruction) {
                LoadLocalInstruction load = (LoadLocalInstruction) def;
                SSAValue loadResult = load.getResult();

                if (loadResult != null && context.isMaterialized(loadResult)) {
                    String name = context.getVariableName(loadResult);
                    if (name != null) {
                        SourceType type = typeRecoverer.recoverType(loadResult);
                        return new VarRefExpr(name, type, loadResult);
                    }
                }

                if (loadResult != null && context.isRecovered(loadResult)) {
                    return applyTypeHint(context.getCachedExpression(loadResult), typeHint);
                }

                Expression expr = recover(def);
                return applyTypeHint(expr, typeHint);
            }

            if (context.isRecovered(ssa)) {
                return applyTypeHint(context.getCachedExpression(ssa), typeHint);
            }
            Expression expr = recover(def);
            context.cacheExpression(ssa, expr);
            return applyTypeHint(expr, typeHint);
        }

        if (context.isRecovered(ssa)) {
            return applyTypeHint(context.getCachedExpression(ssa), typeHint);
        }

        String name = context.getVariableName(ssa);
        if (name == null) name = "v" + ssa.getId();
        SourceType type = typeRecoverer.recoverType(ssa);
        if ("this".equals(name)) {
            return new ThisExpr(type);
        }
        return new VarRefExpr(name, type, ssa);
    }

    private Expression applyTypeHint(Expression expr, SourceType typeHint) {
        if (typeHint == PrimitiveSourceType.BOOLEAN && expr instanceof LiteralExpr) {
            LiteralExpr lit = (LiteralExpr) expr;
            Object value = lit.getValue();
            if (value instanceof Integer) {
                int intVal = (Integer) value;
                return LiteralExpr.ofBoolean(intVal != 0);
            }
        }
        if (typeHint == PrimitiveSourceType.CHAR && expr instanceof LiteralExpr) {
            LiteralExpr lit = (LiteralExpr) expr;
            Object value = lit.getValue();
            if (value instanceof Integer) {
                int intVal = (Integer) value;
                return LiteralExpr.ofChar((char) intVal);
            }
        }
        return expr;
    }

    /**
     * Determines if an instruction's result should be inlined at usage sites
     * rather than assigned to an intermediate variable.
     */
    private boolean shouldInlineExpression(IRInstruction instr) {
        if (instr instanceof ConstantInstruction) {
            return true;
        }

        if (instr instanceof LoadLocalInstruction) {
            // Don't inline if the result is materialized (it's a named variable)
            // This ensures that local variables like 'c' in 'GridBagConstraints c = new GridBagConstraints()'
            // are properly referenced as 'c.fill' instead of being re-inlined as 'new GridBagConstraints().fill'
            SSAValue result = instr.getResult();
            if (result != null && context.isMaterialized(result)) {
                return false;
            }
            return true;
        }

        if (instr instanceof PhiInstruction) {
            return false;
        }

        SSAValue result = instr.getResult();
        if (result != null) {
            int useCount = result.getUses().size();
            if (useCount <= 1) {
                return true;
            }
        }

        return false;
    }

    private Expression recoverConstant(Constant c) {
        return recoverConstant(c, null);
    }

    /**
     * Recovers a constant with optional type hint for boolean detection.
     */
    public Expression recoverConstant(Constant c, SourceType typeHint) {
        if (c instanceof IntConstant) {
            IntConstant i = (IntConstant) c;
            int val = i.getValue();
            if (typeHint instanceof PrimitiveSourceType) {
                PrimitiveSourceType pst = (PrimitiveSourceType) typeHint;
                if (pst == PrimitiveSourceType.BOOLEAN) {
                    return LiteralExpr.ofBoolean(val != 0);
                }
                if (pst == PrimitiveSourceType.CHAR) {
                    return LiteralExpr.ofChar((char) val);
                }
            }
            return LiteralExpr.ofInt(val);
        } else if (c instanceof LongConstant) {
            LongConstant l = (LongConstant) c;
            return LiteralExpr.ofLong(l.getValue());
        } else if (c instanceof FloatConstant) {
            FloatConstant f = (FloatConstant) c;
            return LiteralExpr.ofFloat(f.getValue());
        } else if (c instanceof DoubleConstant) {
            DoubleConstant d = (DoubleConstant) c;
            return LiteralExpr.ofDouble(d.getValue());
        } else if (c instanceof StringConstant) {
            StringConstant s = (StringConstant) c;
            return LiteralExpr.ofString(s.getValue());
        } else if (c instanceof NullConstant) {
            return LiteralExpr.ofNull();
        } else if (c instanceof ClassConstant) {
            ClassConstant cc = (ClassConstant) c;
            String className = cc.getClassName();
            SourceType classType = new ReferenceSourceType(className, Collections.emptyList());
            return new ClassExpr(classType);
        } else if (c instanceof DynamicConstant) {
            DynamicConstant dc = (DynamicConstant) c;
            return resolveDynamicConstant(dc);
        }
        return LiteralExpr.ofNull();
    }

    /**
     * Resolves a DynamicConstant (CONDY) to a DynamicConstantExpr with bootstrap info.
     * This is extracted to allow use from both recoverConstant and the inner RecoveryVisitor.
     */
    private Expression resolveDynamicConstant(DynamicConstant dc) {
        SourceType type = SourceType.fromIRType(dc.getType());
        try {
            ClassFile classFile = context.getSourceMethod().getClassFile();
            if (classFile == null) {
                return new DynamicConstantExpr(dc.getName(), dc.getDescriptor(),
                        dc.getBootstrapMethodIndex(), type);
            }

            BootstrapMethodsAttribute bsmAttr = null;
            for (Attribute attr : classFile.getClassAttributes()) {
                if (attr instanceof BootstrapMethodsAttribute) {
                    bsmAttr = (BootstrapMethodsAttribute) attr;
                    break;
                }
            }
            if (bsmAttr == null) {
                return new DynamicConstantExpr(dc.getName(), dc.getDescriptor(),
                        dc.getBootstrapMethodIndex(), type);
            }

            int bsmIndex = dc.getBootstrapMethodIndex();
            if (bsmIndex < 0 || bsmIndex >= bsmAttr.getBootstrapMethods().size()) {
                return new DynamicConstantExpr(dc.getName(), dc.getDescriptor(),
                        dc.getBootstrapMethodIndex(), type);
            }

            BootstrapMethod bsm = bsmAttr.getBootstrapMethods().get(bsmIndex);
            MethodHandleItem bsmHandleItem =
                (MethodHandleItem) classFile.getConstPool().getItem(bsm.getBootstrapMethodRef());
            MethodHandle bsmHandle = bsmHandleItem.getValue();

            String bsmOwner = resolveMethodHandleOwner(classFile.getConstPool(), bsmHandle);
            String bsmName = resolveMethodHandleName(classFile.getConstPool(), bsmHandle);
            String bsmDesc = resolveMethodHandleDesc(classFile.getConstPool(), bsmHandle);

            // For unknown bootstrap methods, return DynamicConstantExpr with full info
            return new DynamicConstantExpr(dc.getName(), dc.getDescriptor(),
                    dc.getBootstrapMethodIndex(), bsmOwner, bsmName, bsmDesc, type);
        } catch (Exception e) {
            return new DynamicConstantExpr(dc.getName(), dc.getDescriptor(),
                    dc.getBootstrapMethodIndex(), type);
        }
    }

    private String resolveMethodHandleOwner(ConstPool cp,
                                             MethodHandle handle) {
        Item<?> refItem = cp.getItem(handle.getReferenceIndex());
        if (refItem instanceof MethodRefItem) {
            return ((MethodRefItem) refItem).getOwner();
        } else if (refItem instanceof InterfaceRefItem) {
            return ((InterfaceRefItem) refItem).getOwner();
        } else if (refItem instanceof FieldRefItem) {
            return ((FieldRefItem) refItem).getOwner();
        }
        return "";
    }

    private String resolveMethodHandleName(ConstPool cp,
                                            MethodHandle handle) {
        Item<?> refItem = cp.getItem(handle.getReferenceIndex());
        if (refItem instanceof MethodRefItem) {
            return ((MethodRefItem) refItem).getName();
        } else if (refItem instanceof InterfaceRefItem) {
            return ((InterfaceRefItem) refItem).getName();
        } else if (refItem instanceof FieldRefItem) {
            return ((FieldRefItem) refItem).getName();
        }
        return "";
    }

    private String resolveMethodHandleDesc(ConstPool cp,
                                            MethodHandle handle) {
        Item<?> refItem = cp.getItem(handle.getReferenceIndex());
        if (refItem instanceof MethodRefItem) {
            return ((MethodRefItem) refItem).getDescriptor();
        } else if (refItem instanceof InterfaceRefItem) {
            return ((InterfaceRefItem) refItem).getDescriptor();
        } else if (refItem instanceof FieldRefItem) {
            return ((FieldRefItem) refItem).getDescriptor();
        }
        return "";
    }

    /**
     * Parses parameter types from a method descriptor.
     * E.g., "(ZILjava/lang/String;)V" returns ["Z", "I", "Ljava/lang/String;"]
     */
    private List<String> parseParameterTypes(String desc) {
        List<String> types = new ArrayList<>();
        if (desc == null || !desc.startsWith("(")) return types;

        int idx = 1;
        while (idx < desc.length() && desc.charAt(idx) != ')') {
            int start = idx;
            char c = desc.charAt(idx);
            if (c == 'L') {
                int end = desc.indexOf(';', idx);
                if (end > 0) {
                    types.add(desc.substring(start, end + 1));
                    idx = end + 1;
                } else {
                    break;
                }
            } else if (c == '[') {
                StringBuilder arrayDesc = new StringBuilder();
                while (idx < desc.length() && desc.charAt(idx) == '[') {
                    arrayDesc.append('[');
                    idx++;
                }
                if (idx < desc.length()) {
                    char elemType = desc.charAt(idx);
                    if (elemType == 'L') {
                        int end = desc.indexOf(';', idx);
                        if (end > 0) {
                            arrayDesc.append(desc, idx, end + 1);
                            idx = end + 1;
                        }
                    } else {
                        arrayDesc.append(elemType);
                        idx++;
                    }
                }
                types.add(arrayDesc.toString());
            } else {
                types.add(String.valueOf(c));
                idx++;
            }
        }
        return types;
    }

    /**
     * Attempts to collapse a StringBuilder chain into a string concatenation expression.
     * Pattern: new StringBuilder().append(a).append(b).toString() → a + b
     */
    private Expression tryCollapseStringBuilder(MethodCallExpr call) {
        if (!"toString".equals(call.getMethodName())) return null;
        if (!isStringBuilderExpr(call.getReceiver())) return null;

        List<Expression> parts = new ArrayList<>();
        Expression current = call.getReceiver();

        while (current instanceof MethodCallExpr) {
            MethodCallExpr mc = (MethodCallExpr) current;
            if ("append".equals(mc.getMethodName()) && isStringBuilderExpr(mc)) {
                if (!mc.getArguments().isEmpty()) {
                    parts.add(0, mc.getArguments().get(0));
                }
                current = mc.getReceiver();
            } else {
                break;
            }
        }

        if (!(current instanceof NewExpr)) {
            return null;
        }
        NewExpr ne = (NewExpr) current;
        if (!ne.getClassName().contains("StringBuilder")) {
            return null;
        }

        if (parts.isEmpty()) {
            return LiteralExpr.ofString("");
        }

        Expression result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            result = new BinaryExpr(BinaryOperator.ADD, result, parts.get(i),
                                    ReferenceSourceType.STRING);
        }
        return result;
    }

    /**
     * Checks if an expression is a StringBuilder (method call on StringBuilder or new StringBuilder).
     */
    private boolean isStringBuilderExpr(Expression expr) {
        if (expr instanceof MethodCallExpr) {
            MethodCallExpr mc = (MethodCallExpr) expr;
            return mc.getOwnerClass().contains("StringBuilder");
        }
        if (expr instanceof NewExpr) {
            NewExpr ne = (NewExpr) expr;
            return ne.getClassName().contains("StringBuilder");
        }
        return false;
    }

    private class RecoveryVisitor extends AbstractIRVisitor<Expression> {
        @Override
        public Expression visitBinaryOp(BinaryOpInstruction instr) {
            Expression left = recoverOperand(instr.getLeft());
            Expression right = recoverOperand(instr.getRight());
            BinaryOperator op = OperatorMapper.mapBinaryOp(instr.getOp());
            SourceType type = typeRecoverer.recoverType(instr.getResult());

            if ((op == BinaryOperator.BAND || op == BinaryOperator.BOR || op == BinaryOperator.BXOR) &&
                (isBooleanType(left.getType()) || isBooleanType(right.getType()))) {
                type = PrimitiveSourceType.BOOLEAN;
            }

            return new BinaryExpr(op, left, right, type);
        }

        private boolean isBooleanType(SourceType type) {
            return type == PrimitiveSourceType.BOOLEAN;
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
        public Expression visitCopy(CopyInstruction instr) {
            Value source = instr.getSource();
            while (source instanceof SSAValue) {
                SSAValue ssaSource = (SSAValue) source;
                if (context.isRecovered(ssaSource)) {
                    return context.getCachedExpression(ssaSource);
                }
                String name = context.getVariableName(ssaSource);
                if (name != null) {
                    SourceType type = typeRecoverer.recoverType(ssaSource);
                    return new VarRefExpr(name, type, ssaSource);
                }
                IRInstruction def = ssaSource.getDefinition();
                if (def == null) break;
                if (def instanceof CopyInstruction) {
                    source = ((CopyInstruction) def).getSource();
                    continue;
                }
                Expression expr = recover(def);
                context.cacheExpression(ssaSource, expr);
                return expr;
            }
            if (source instanceof Constant) {
                return recoverConstant((Constant) source);
            }
            return null;
        }

        @Override
        public Expression visitInvoke(InvokeInstruction instr) {
            if ("<init>".equals(instr.getName())) {
                return handleConstructorCall(instr);
            }

            if (instr.isDynamic()) {
                return handleInvokeDynamic(instr);
            }

            Expression receiver = null;
            if (instr.getInvokeType() != InvokeType.STATIC && !instr.getArguments().isEmpty()) {
                Value receiverValue = instr.getArguments().get(0);
                if (receiverValue instanceof SSAValue) {
                    SSAValue ssaReceiver = (SSAValue) receiverValue;
                    String name = context.getVariableName(ssaReceiver);
                    if (!"this".equals(name)) {
                        receiver = recoverOperand(receiverValue);
                    }
                } else {
                    receiver = recoverOperand(receiverValue);
                }
            }
            List<Expression> args = new ArrayList<>();
            int start = (instr.getInvokeType() == InvokeType.STATIC) ? 0 : 1;

            List<String> paramTypes = parseParameterTypes(instr.getDescriptor());

            for (int i = start; i < instr.getArguments().size(); i++) {
                int paramIndex = i - start;
                SourceType typeHint = null;
                if (paramIndex < paramTypes.size()) {
                    typeHint = typeRecoverer.recoverType(paramTypes.get(paramIndex));
                }
                args.add(recoverOperand(instr.getArguments().get(i), typeHint));
            }
            SourceType retType = typeRecoverer.recoverType(instr.getResult());
            boolean isStatic = instr.getInvokeType() == InvokeType.STATIC;
            MethodCallExpr call = new MethodCallExpr(receiver, instr.getName(), instr.getOwner(), args, isStatic, retType);

            Expression collapsed = tryCollapseStringBuilder(call);
            return collapsed != null ? collapsed : call;
        }

        private Expression handleConstructorCall(InvokeInstruction instr) {
            List<Expression> args = new ArrayList<>();
            List<String> paramTypes = parseParameterTypes(instr.getDescriptor());

            for (int i = 1; i < instr.getArguments().size(); i++) {
                int paramIndex = i - 1;
                SourceType typeHint = null;
                if (paramIndex < paramTypes.size()) {
                    typeHint = typeRecoverer.recoverType(paramTypes.get(paramIndex));
                }
                args.add(recoverOperand(instr.getArguments().get(i), typeHint));
            }

            if (!instr.getArguments().isEmpty()) {
                Value receiver = instr.getArguments().get(0);
                if (receiver instanceof SSAValue) {
                    SSAValue ssaReceiver = (SSAValue) receiver;
                    String name = context.getVariableName(ssaReceiver);
                    if ("this".equals(name)) {
                        String ownerClass = instr.getOwner();
                        String methodClass = context.getIrMethod().getOwnerClass();
                        if (ownerClass != null && methodClass != null && !ownerClass.equals(methodClass)) {
                            return new MethodCallExpr(null, "super", ownerClass, args, false, null);
                        } else {
                            return new MethodCallExpr(null, "this", ownerClass, args, false, null);
                        }
                    }

                    if (context.isPendingNew(ssaReceiver)) {
                        String className = context.consumePendingNew(ssaReceiver);
                        NewExpr newExpr = new NewExpr(className);
                        for (Expression arg : args) {
                            newExpr.addArgument(arg);
                        }
                        context.cacheExpression(ssaReceiver, newExpr);
                        return newExpr;
                    }
                }
            }

            NewExpr newExpr = new NewExpr(instr.getOwner());
            for (Expression arg : args) {
                newExpr.addArgument(arg);
            }
            return newExpr;
        }

        /**
         * Handles invokedynamic instructions which typically create lambda expressions or method references.
         * Attempts to generate proper method references or lambda expressions with correct bodies.
         */
        private Expression handleInvokeDynamic(InvokeInstruction instr) {
            SourceType returnType = typeRecoverer.recoverType(instr.getResult());

            BootstrapMethodInfo bsInfo = lookupBootstrapInfo(instr);

            if (bsInfo != null) {
                if (bsInfo.isLambdaMetafactory()) {
                    return handleLambdaMetafactory(instr, bsInfo, returnType);
                }
                if (bsInfo.isStringConcatFactory()) {
                    return handleStringConcat(instr, bsInfo);
                }

                // For other known bootstrap methods, create InvokeDynamicExpr with bootstrap info
                return createInvokeDynamicExpr(instr, bsInfo, returnType);
            }

            // No bootstrap info available - still create descriptive expression
            return createInvokeDynamicExprNoBootstrap(instr, returnType);
        }

        /**
         * Creates an InvokeDynamicExpr with full bootstrap method information.
         */
        private Expression createInvokeDynamicExpr(InvokeInstruction instr, BootstrapMethodInfo bsInfo,
                                                    SourceType returnType) {
            MethodHandleConstant bsm = bsInfo.getBootstrapMethod();

            // Recover arguments
            List<Expression> args = new ArrayList<>();
            for (Value arg : instr.getArguments()) {
                args.add(recoverOperand(arg));
            }

            String bsmOwner = bsm != null ? bsm.getOwner() : "unknown";
            String bsmName = bsm != null ? bsm.getName() : "unknown";

            return new InvokeDynamicExpr(
                    instr.getName(),
                    instr.getDescriptor(),
                    args,
                    bsmOwner,
                    bsmName,
                    returnType
            );
        }

        /**
         * Creates an InvokeDynamicExpr when bootstrap info is unavailable.
         */
        private Expression createInvokeDynamicExprNoBootstrap(InvokeInstruction instr, SourceType returnType) {
            List<Expression> args = new ArrayList<>();
            for (Value arg : instr.getArguments()) {
                args.add(recoverOperand(arg));
            }

            return new InvokeDynamicExpr(
                    instr.getName(),
                    instr.getDescriptor(),
                    args,
                    returnType
            );
        }

        /**
         * Looks up bootstrap method info for an invokedynamic instruction from the ClassFile.
         */
        private BootstrapMethodInfo lookupBootstrapInfo(InvokeInstruction instr) {
            if (instr.hasBootstrapInfo()) {
                return instr.getBootstrapInfo();
            }

            try {
                ClassFile classFile = context.getSourceMethod().getClassFile();
                if (classFile == null) return null;

                int cpIndex = instr.getOriginalCpIndex();
                if (cpIndex <= 0) return null;

                Item<?> item = classFile.getConstPool().getItem(cpIndex);
                if (!(item instanceof InvokeDynamicItem)) return null;
                InvokeDynamicItem indyItem = (InvokeDynamicItem) item;

                int bsmIndex = indyItem.getValue().getBootstrapMethodAttrIndex();

                BootstrapMethodsAttribute bsmAttr = null;
                for (Attribute attr : classFile.getClassAttributes()) {
                    if (attr instanceof BootstrapMethodsAttribute) {
                        bsmAttr = (BootstrapMethodsAttribute) attr;
                        break;
                    }
                }
                if (bsmAttr == null) return null;

                List<BootstrapMethod> bootstrapMethods = bsmAttr.getBootstrapMethods();
                if (bsmIndex < 0 || bsmIndex >= bootstrapMethods.size()) return null;

                BootstrapMethod bsm = bootstrapMethods.get(bsmIndex);

                MethodHandleItem bsmHandleItem =
                    (MethodHandleItem) classFile.getConstPool().getItem(bsm.getBootstrapMethodRef());
                MethodHandle bsmHandle = bsmHandleItem.getValue();

                String bsmOwner = resolveMethodHandleOwner(classFile.getConstPool(), bsmHandle);
                String bsmName = resolveMethodHandleName(classFile.getConstPool(), bsmHandle);
                String bsmDesc = resolveMethodHandleDesc(classFile.getConstPool(), bsmHandle);
                MethodHandleConstant bsmConst =
                    new MethodHandleConstant(bsmHandle.getReferenceKind(), bsmOwner, bsmName, bsmDesc);

                List<Constant> bsArgs = new ArrayList<>();
                for (int argIndex : bsm.getBootstrapArguments()) {
                    Constant argConst = convertCPItemToConstant(classFile.getConstPool(), argIndex);
                    if (argConst != null) {
                        bsArgs.add(argConst);
                    }
                }

                return new BootstrapMethodInfo(bsmConst, bsArgs);

            } catch (Exception e) {
                return null;
            }
        }

        private String resolveMethodHandleOwner(ConstPool cp, MethodHandle handle) {
            Item<?> refItem = cp.getItem(handle.getReferenceIndex());
            if (refItem instanceof MethodRefItem) {
                MethodRefItem methodRef = (MethodRefItem) refItem;
                return methodRef.getOwner();
            } else if (refItem instanceof InterfaceRefItem) {
                InterfaceRefItem ifaceRef = (InterfaceRefItem) refItem;
                return ifaceRef.getOwner();
            } else if (refItem instanceof FieldRefItem) {
                FieldRefItem fieldRef = (FieldRefItem) refItem;
                return fieldRef.getOwner();
            }
            return "";
        }

        private String resolveMethodHandleName(ConstPool cp, MethodHandle handle) {
            Item<?> refItem = cp.getItem(handle.getReferenceIndex());
            if (refItem instanceof MethodRefItem) {
                MethodRefItem methodRef = (MethodRefItem) refItem;
                return methodRef.getName();
            } else if (refItem instanceof InterfaceRefItem) {
                InterfaceRefItem ifaceRef = (InterfaceRefItem) refItem;
                return ifaceRef.getName();
            } else if (refItem instanceof FieldRefItem) {
                FieldRefItem fieldRef = (FieldRefItem) refItem;
                return fieldRef.getName();
            }
            return "";
        }

        private String resolveMethodHandleDesc(ConstPool cp, MethodHandle handle) {
            Item<?> refItem = cp.getItem(handle.getReferenceIndex());
            if (refItem instanceof MethodRefItem) {
                MethodRefItem methodRef = (MethodRefItem) refItem;
                return methodRef.getDescriptor();
            } else if (refItem instanceof InterfaceRefItem) {
                InterfaceRefItem ifaceRef = (InterfaceRefItem) refItem;
                return ifaceRef.getDescriptor();
            } else if (refItem instanceof FieldRefItem) {
                FieldRefItem fieldRef = (FieldRefItem) refItem;
                return fieldRef.getDescriptor();
            }
            return "";
        }

        private Constant convertCPItemToConstant(ConstPool cp, int index) {
            Item<?> item = cp.getItem(index);

            if (item instanceof MethodTypeItem) {
                MethodTypeItem mtItem = (MethodTypeItem) item;
                String desc = ((Utf8Item) cp.getItem(mtItem.getValue())).getValue();
                return new MethodTypeConstant(desc);
            } else if (item instanceof MethodHandleItem) {
                MethodHandleItem mhItem = (MethodHandleItem) item;
                MethodHandle handle = mhItem.getValue();
                String owner = resolveMethodHandleOwner(cp, handle);
                String name = resolveMethodHandleName(cp, handle);
                String desc = resolveMethodHandleDesc(cp, handle);
                return new MethodHandleConstant(handle.getReferenceKind(), owner, name, desc);
            } else if (item instanceof IntegerItem) {
                IntegerItem intItem = (IntegerItem) item;
                return new IntConstant(intItem.getValue());
            } else if (item instanceof LongItem) {
                LongItem longItem = (LongItem) item;
                return new LongConstant(longItem.getValue());
            } else if (item instanceof FloatItem) {
                FloatItem floatItem = (FloatItem) item;
                return new FloatConstant(floatItem.getValue());
            } else if (item instanceof DoubleItem) {
                DoubleItem doubleItem = (DoubleItem) item;
                return new DoubleConstant(doubleItem.getValue());
            } else if (item instanceof StringRefItem) {
                StringRefItem strItem = (StringRefItem) item;
                String value = ((Utf8Item) cp.getItem(strItem.getValue())).getValue();
                return new StringConstant(value);
            } else if (item instanceof ConstantDynamicItem) {
                ConstantDynamicItem cdItem = (ConstantDynamicItem) item;
                return new DynamicConstant(
                    cdItem.getName(),
                    cdItem.getDescriptor(),
                    cdItem.getBootstrapMethodAttrIndex(),
                    index
                );
            }

            return null;
        }

        /**
         * Handles StringConcatFactory bootstrap - builds string concatenation expression.
         */
        private Expression handleStringConcat(InvokeInstruction instr, BootstrapMethodInfo bsInfo) {
            List<Constant> bsArgs = bsInfo.getBootstrapArguments();
            List<Value> stackArgs = instr.getArguments();

            String recipe = "";
            if (!bsArgs.isEmpty() && bsArgs.get(0) instanceof StringConstant) {
                StringConstant sc = (StringConstant) bsArgs.get(0);
                recipe = sc.getValue();
            }

            List<Expression> parts = new ArrayList<>();
            int stackIdx = 0;
            int constantIdx = 1;

            for (int i = 0; i < recipe.length(); i++) {
                char c = recipe.charAt(i);
                if (c == '\u0001') {
                    if (stackIdx < stackArgs.size()) {
                        parts.add(recoverOperand(stackArgs.get(stackIdx++)));
                    }
                } else if (c == '\u0002') {
                    if (constantIdx < bsArgs.size()) {
                        Constant arg = bsArgs.get(constantIdx++);
                        parts.add(recoverConstantAsExpression(arg));
                    }
                } else {
                    StringBuilder sb = new StringBuilder();
                    while (i < recipe.length() && recipe.charAt(i) != '\u0001' && recipe.charAt(i) != '\u0002') {
                        sb.append(recipe.charAt(i++));
                    }
                    i--;
                    if (sb.length() > 0) {
                        parts.add(LiteralExpr.ofString(sb.toString()));
                    }
                }
            }

            if (parts.isEmpty()) {
                return LiteralExpr.ofString("");
            }
            Expression result = parts.get(0);
            for (int i = 1; i < parts.size(); i++) {
                result = new BinaryExpr(BinaryOperator.ADD, result, parts.get(i), ReferenceSourceType.STRING);
            }
            return result;
        }

        /**
         * Converts a Constant to an Expression, handling DynamicConstant specially.
         */
        private Expression recoverConstantAsExpression(Constant c) {
            if (c instanceof DynamicConstant) {
                DynamicConstant dc = (DynamicConstant) c;
                return resolveDynamicConstantExpression(dc);
            }
            return recoverConstant(c, null);
        }

        /**
         * Resolves a DynamicConstant (CONDY) to a method call expression.
         * For unknown bootstrap methods, returns a DynamicConstantExpr with bootstrap info.
         */
        private Expression resolveDynamicConstantExpression(DynamicConstant dc) {
            SourceType type = SourceType.fromIRType(dc.getType());
            try {
                ClassFile classFile = context.getSourceMethod().getClassFile();
                if (classFile == null) {
                    // Fallback: return descriptive expression without BSM details
                    return new DynamicConstantExpr(dc.getName(), dc.getDescriptor(),
                            dc.getBootstrapMethodIndex(), type);
                }

                BootstrapMethodsAttribute bsmAttr = null;
                for (Attribute attr : classFile.getClassAttributes()) {
                    if (attr instanceof BootstrapMethodsAttribute) {
                        BootstrapMethodsAttribute bma = (BootstrapMethodsAttribute) attr;
                        bsmAttr = bma;
                        break;
                    }
                }
                if (bsmAttr == null) {
                    return new DynamicConstantExpr(dc.getName(), dc.getDescriptor(),
                            dc.getBootstrapMethodIndex(), type);
                }

                int bsmIndex = dc.getBootstrapMethodIndex();
                if (bsmIndex < 0 || bsmIndex >= bsmAttr.getBootstrapMethods().size()) {
                    return new DynamicConstantExpr(dc.getName(), dc.getDescriptor(),
                            dc.getBootstrapMethodIndex(), type);
                }

                BootstrapMethod bsm = bsmAttr.getBootstrapMethods().get(bsmIndex);
                MethodHandleItem bsmHandleItem =
                    (MethodHandleItem) classFile.getConstPool().getItem(bsm.getBootstrapMethodRef());
                MethodHandle bsmHandle = bsmHandleItem.getValue();

                String bsmOwner = resolveMethodHandleOwner(classFile.getConstPool(), bsmHandle);
                String bsmName = resolveMethodHandleName(classFile.getConstPool(), bsmHandle);
                String bsmDesc = resolveMethodHandleDesc(classFile.getConstPool(), bsmHandle);

                // Check for known bootstrap methods that we can fully resolve
                if ("java/lang/invoke/ConstantBootstraps".equals(bsmOwner) && "invoke".equals(bsmName)) {
                    return resolveConstantBootstrapsInvoke(classFile, bsm);
                }

                // For unknown bootstrap methods, return DynamicConstantExpr with full info
                return new DynamicConstantExpr(dc.getName(), dc.getDescriptor(),
                        dc.getBootstrapMethodIndex(), bsmOwner, bsmName, bsmDesc, type);
            } catch (Exception e) {
                // Fallback with minimal info on error
                return new DynamicConstantExpr(dc.getName(), dc.getDescriptor(),
                        dc.getBootstrapMethodIndex(), type);
            }
        }

        /**
         * Resolves ConstantBootstraps.invoke to the actual method call it wraps.
         */
        private Expression resolveConstantBootstrapsInvoke(ClassFile classFile,
                                                           BootstrapMethod bsm) {
            List<Integer> args = bsm.getBootstrapArguments();
            if (args.isEmpty()) return LiteralExpr.ofNull();

            Item<?> mhItem = classFile.getConstPool().getItem(args.get(0));
            if (!(mhItem instanceof MethodHandleItem)) {
                return LiteralExpr.ofNull();
            }
            MethodHandleItem handleItem = (MethodHandleItem) mhItem;

            MethodHandle handle = handleItem.getValue();
            String owner = resolveMethodHandleOwner(classFile.getConstPool(), handle);
            String name = resolveMethodHandleName(classFile.getConstPool(), handle);
            String desc = resolveMethodHandleDesc(classFile.getConstPool(), handle);

            List<Expression> callArgs = new ArrayList<>();
            for (int i = 1; i < args.size(); i++) {
                Constant argConst = convertCPItemToConstant(classFile.getConstPool(), args.get(i));
                if (argConst != null) {
                    callArgs.add(recoverConstant(argConst, null));
                }
            }

            String retDesc = desc.substring(desc.indexOf(')') + 1);
            SourceType retType = SourceType.fromIRType(IRType.fromDescriptor(retDesc));

            return MethodCallExpr.staticCall(owner, name, callArgs, retType);
        }

        /**
         * Handles LambdaMetafactory bootstrap - can generate method reference or lambda.
         */
        private Expression handleLambdaMetafactory(InvokeInstruction instr, BootstrapMethodInfo bsInfo, SourceType returnType) {
            List<Constant> args = bsInfo.getBootstrapArguments();

            if (args.size() >= 2 && args.get(1) instanceof MethodHandleConstant) {
                MethodHandleConstant implHandle = (MethodHandleConstant) args.get(1);
                String implOwner = implHandle.getOwner();
                String implName = implHandle.getName();
                String implDesc = implHandle.getDescriptor();
                int refKind = implHandle.getReferenceKind();

                String samDescriptor = null;
                if (args.size() >= 1 && args.get(0) instanceof MethodTypeConstant) {
                    MethodTypeConstant samType = (MethodTypeConstant) args.get(0);
                    samDescriptor = samType.getDescriptor();
                }

                String currentClassName = context.getSourceMethod().getClassFile() != null
                    ? context.getSourceMethod().getClassFile().getClassName() : null;
                boolean isInSameClass = currentClassName != null &&
                    implOwner.replace('/', '.').equals(currentClassName.replace('/', '.'));

                if (isInSameClass) {
                    List<LambdaParameter> params = generateLambdaParameters(samDescriptor, instr.getName());
                    ASTNode body = generateLambdaBody(implHandle, instr, params, samDescriptor);

                    if (body != null && !isEmptyBody(body)) {
                        return new LambdaExpr(params, body, returnType);
                    }
                }

                boolean isSyntheticLambda = implName.startsWith("lambda$");
                if (isSyntheticLambda) {
                    List<LambdaParameter> params = generateLambdaParameters(samDescriptor, instr.getName());
                    ASTNode body = generateLambdaBody(implHandle, instr, params, samDescriptor);
                    return new LambdaExpr(params, body, returnType);
                }

                return createMethodReference(implHandle, instr, returnType);
            }

            return generateFallbackLambda(instr, returnType);
        }

        /**
         * Creates a method reference expression from a method handle.
         */
        private Expression createMethodReference(MethodHandleConstant handle,
                                                  InvokeInstruction instr, SourceType returnType) {
            String owner = handle.getOwner();
            String name = handle.getName();
            int refKind = handle.getReferenceKind();

            if ("<init>".equals(name) || refKind == MethodHandleConstant.REF_newInvokeSpecial) {
                return MethodRefExpr.constructorRef(owner, returnType);
            }

            MethodRefKind kind;
            Expression receiver = null;

            if (refKind == MethodHandleConstant.REF_invokeStatic) {
                kind = MethodRefKind.STATIC;
            } else if (refKind == MethodHandleConstant.REF_invokeSpecial ||
                       refKind == MethodHandleConstant.REF_invokeVirtual ||
                       refKind == MethodHandleConstant.REF_invokeInterface) {
                if (!instr.getArguments().isEmpty()) {
                    receiver = recoverOperand(instr.getArguments().get(0));
                    kind = MethodRefKind.BOUND;
                } else {
                    kind = MethodRefKind.INSTANCE;
                }
            } else {
                kind = MethodRefKind.INSTANCE;
            }

            return new MethodRefExpr(receiver, name, owner, kind, returnType);
        }

        /**
         * Generates lambda parameters from SAM descriptor.
         */
        private List<LambdaParameter> generateLambdaParameters(String samDescriptor, String samMethodName) {
            List<LambdaParameter> params = new ArrayList<>();

            int paramCount = 0;
            if (samDescriptor != null && samDescriptor.startsWith("(")) {
                List<String> paramTypes = parseParameterTypes(samDescriptor);
                paramCount = paramTypes.size();
            }

            if ("uncaughtException".equals(samMethodName)) {
                params.add(new LambdaParameter("t", null, true));
                params.add(new LambdaParameter("e", null, true));
            } else if ("hierarchyChanged".equals(samMethodName)) {
                params.add(new LambdaParameter("event", null, true));
            } else if ("run".equals(samMethodName) && paramCount == 0) {
            } else if ("accept".equals(samMethodName)) {
                params.add(new LambdaParameter("t", null, true));
            } else if ("apply".equals(samMethodName)) {
                params.add(new LambdaParameter("t", null, true));
            } else if ("test".equals(samMethodName)) {
                params.add(new LambdaParameter("t", null, true));
            } else if ("get".equals(samMethodName) && paramCount == 0) {
            } else {
                for (int i = 0; i < paramCount; i++) {
                    String name = paramCount == 1 ? "arg" : "arg" + i;
                    params.add(new LambdaParameter(name, null, true));
                }
            }

            return params;
        }

        /**
         * Generates a lambda body by decompiling the synthetic lambda method.
         * Maps captured variables and lambda parameters to produce the actual body.
         */
        private ASTNode generateLambdaBody(
                MethodHandleConstant handle,
                InvokeInstruction instr,
                List<LambdaParameter> params,
                String samDescriptor) {

            String implName = handle.getName();
            String implOwner = handle.getOwner();

            try {
                ClassFile classFile = context.getSourceMethod().getClassFile();
                if (classFile != null) {
                    MethodEntry lambdaMethod = null;
                    for (MethodEntry method : classFile.getMethods()) {
                        if (method.getName().equals(implName)) {
                            lambdaMethod = method;
                            break;
                        }
                    }

                    if (lambdaMethod != null && lambdaMethod.getCodeAttribute() != null) {
                        return decompileLambdaMethod(lambdaMethod, instr, params, handle, samDescriptor);
                    }
                }
            } catch (Exception e) {
            }

            boolean isVoidReturn = samDescriptor != null && samDescriptor.endsWith(")V");
            if (isVoidReturn) {
                return new BlockStmt(Collections.emptyList());
            } else {
                return LiteralExpr.ofNull();
            }
        }

        /**
         * Checks if a lambda body is empty (fallback case).
         */
        private boolean isEmptyBody(ASTNode body) {
            if (body instanceof BlockStmt) {
                BlockStmt block = (BlockStmt) body;
                return block.getStatements().isEmpty();
            }
            if (body instanceof LiteralExpr) {
                LiteralExpr lit = (LiteralExpr) body;
                return lit.getValue() == null; // null literal
            }
            return false;
        }

        /**
         * Decompiles a synthetic lambda method and returns its body as an AST node.
         */
        private ASTNode decompileLambdaMethod(
                MethodEntry lambdaMethod,
                InvokeInstruction instr,
                List<LambdaParameter> params,
                MethodHandleConstant handle,
                String samDescriptor) {

            try {
                ClassFile classFile = context.getSourceMethod().getClassFile();
                SSA ssa = new SSA(classFile.getConstPool());
                IRMethod lambdaIR = ssa.lift(lambdaMethod);

                DefUseChains defUseChains =
                    new DefUseChains(lambdaIR);
                defUseChains.compute();

                RecoveryContext lambdaContext = new RecoveryContext(lambdaIR, lambdaMethod, defUseChains);

                int capturedCount = instr.getArguments().size();
                boolean isInstanceMethod = !lambdaMethod.getDesc().startsWith("()") &&
                    (handle.getReferenceKind() != MethodHandleConstant.REF_invokeStatic);

                Map<Integer, Expression> capturedMapping = new HashMap<>();
                int slot = isInstanceMethod ? 1 : 0;

                for (int i = 0; i < capturedCount; i++) {
                    Expression capturedExpr = recoverOperand(instr.getArguments().get(i));
                    capturedMapping.put(slot, capturedExpr);
                    slot++;
                }

                Map<Integer, String> paramMapping = new HashMap<>();
                for (int i = 0; i < params.size(); i++) {
                    paramMapping.put(slot + i, params.get(i).name());
                }

                if (isInstanceMethod) {
                    lambdaContext.setVariableName(findSSAForSlot(lambdaIR, 0), "this");
                }
                for (Map.Entry<Integer, String> entry : paramMapping.entrySet()) {
                    SSAValue slotSSA = findSSAForSlot(lambdaIR, entry.getKey());
                    if (slotSSA != null) {
                        lambdaContext.setVariableName(slotSSA, entry.getValue());
                    }
                }

                LambdaExpressionRecoverer lambdaExprRecoverer =
                    new LambdaExpressionRecoverer(lambdaContext, capturedMapping, paramMapping);

                ASTNode body = lambdaExprRecoverer.extractLambdaBody(lambdaIR);
                if (body != null) {
                    return body;
                }

            } catch (Exception e) {
            }

            boolean isVoidReturn = samDescriptor != null && samDescriptor.endsWith(")V");
            if (isVoidReturn) {
                return new BlockStmt(Collections.emptyList());
            } else {
                return LiteralExpr.ofNull();
            }
        }

        /**
         * Finds the SSA value for a given local slot using the method's parameters.
         * After SSA lifting, LoadLocalInstruction no longer exists in the IR - parameters
         * are tracked directly in IRMethod.getParameters().
         */
        private SSAValue findSSAForSlot(IRMethod method, int slot) {
            List<SSAValue> params = method.getParameters();
            int currentSlot = 0;

            for (SSAValue param : params) {
                if (currentSlot == slot) {
                    return param;
                }
                currentSlot++;
                if (param.getType() != null && param.getType().isTwoSlot()) {
                    currentSlot++;
                }
            }
            return null;
        }

        private class LambdaExpressionRecoverer extends ExpressionRecoverer {
            private final Map<Integer, Expression> capturedMapping;
            private final Map<Integer, String> paramMapping;

            LambdaExpressionRecoverer(RecoveryContext ctx,
                                       Map<Integer, Expression> capturedMapping,
                                       Map<Integer, String> paramMapping) {
                super(ctx);
                this.capturedMapping = capturedMapping;
                this.paramMapping = paramMapping;
            }

            ASTNode extractLambdaBody(IRMethod lambdaIR) {
                IRBlock entryBlock = lambdaIR.getEntryBlock();
                if (entryBlock == null) return null;

                if (lambdaIR.getBlocks().size() == 1) {
                    List<IRInstruction> instructions = entryBlock.getInstructions();
                    IRInstruction terminator = entryBlock.getTerminator();

                    if (terminator instanceof ReturnInstruction) {
                        ReturnInstruction ret = (ReturnInstruction) terminator;
                        Value returnValue = ret.getReturnValue();
                        if (returnValue != null) {
                            return recoverLambdaOperand(returnValue, lambdaIR);
                        }
                        if (instructions.size() == 1 && instructions.get(0) instanceof InvokeInstruction) {
                            Expression expr = recoverLambdaInstruction(instructions.get(0), lambdaIR);
                            if (expr != null) {
                                return expr;
                            }
                        }
                    }
                }

                return extractFullLambdaBody(lambdaIR);
            }

            private ASTNode extractFullLambdaBody(IRMethod lambdaIR) {
                try {
                    MethodEntry lambdaMethod = lambdaIR.getSourceMethod();
                    if (lambdaMethod == null) {
                        return new BlockStmt(Collections.emptyList());
                    }

                    MethodRecoverer recoverer = new MethodRecoverer(lambdaIR, lambdaMethod);
                    recoverer.analyze();
                    recoverer.initializeRecovery();

                    for (Map.Entry<Integer, Expression> entry : capturedMapping.entrySet()) {
                        int slot = entry.getKey();
                        Expression capturedExpr = entry.getValue();
                        SSAValue ssaForSlot = findSSAForSlot(lambdaIR, slot);
                        if (ssaForSlot != null) {
                            if (capturedExpr instanceof VarRefExpr) {
                                VarRefExpr varRef = (VarRefExpr) capturedExpr;
                                recoverer.getRecoveryContext().setVariableName(ssaForSlot, varRef.getName());
                            } else if (capturedExpr instanceof ThisExpr) {
                                recoverer.getRecoveryContext().setVariableName(ssaForSlot, "this");
                            }
                        }
                    }

                    for (Map.Entry<Integer, String> entry : paramMapping.entrySet()) {
                        int slot = entry.getKey();
                        String paramName = entry.getValue();
                        SSAValue ssaForSlot = findSSAForSlot(lambdaIR, slot);
                        if (ssaForSlot != null) {
                            recoverer.getRecoveryContext().setVariableName(ssaForSlot, paramName);
                        }
                    }

                    BlockStmt body = recoverer.recover();
                    if (body != null && !body.getStatements().isEmpty()) {
                        return simplifyLambdaBody(body);
                    }
                } catch (Exception e) {
                }
                return new BlockStmt(Collections.emptyList());
            }

            private ASTNode simplifyLambdaBody(BlockStmt body) {
                List<? extends Statement> stmts = body.getStatements();
                if (stmts.size() == 1) {
                    Statement stmt = stmts.get(0);
                    if (stmt instanceof ReturnStmt) {
                        ReturnStmt ret = (ReturnStmt) stmt;
                        if (ret.getValue() != null) {
                            return ret.getValue();
                        }
                    }
                    if (stmt instanceof ExprStmt) {
                        ExprStmt exprStmt = (ExprStmt) stmt;
                        return exprStmt.getExpression();
                    }
                }
                return body;
            }

            private Expression recoverLambdaOperand(Value value, IRMethod lambdaIR) {
                if (value instanceof SSAValue) {
                    SSAValue ssa = (SSAValue) value;
                    IRInstruction def = ssa.getDefinition();
                    String ssaName = ssa.getName();

                    if (def == null) {
                        if ("this".equals(ssaName)) {
                            return new ThisExpr(null);
                        }
                        if (ssaName != null && ssaName.startsWith("p")) {
                            try {
                                int paramIndex = Integer.parseInt(ssaName.substring(1));
                                int slot = calculateSlotFromParamIndex(lambdaIR, paramIndex);
                                if (paramMapping.containsKey(slot)) {
                                    String paramName = paramMapping.get(slot);
                                    SourceType type = typeRecoverer.recoverType(ssa);
                                    return new VarRefExpr(paramName, type != null ? type : ReferenceSourceType.OBJECT, null);
                                }
                                if (capturedMapping.containsKey(slot)) {
                                    return capturedMapping.get(slot);
                                }
                            } catch (NumberFormatException e) {
                            }
                        }
                        int slot = findSlotForParameter(lambdaIR, ssa);
                        if (slot >= 0) {
                            if (capturedMapping.containsKey(slot)) {
                                return capturedMapping.get(slot);
                            }
                            if (paramMapping.containsKey(slot)) {
                                String paramName = paramMapping.get(slot);
                                SourceType type = typeRecoverer.recoverType(ssa);
                                return new VarRefExpr(paramName, type != null ? type : ReferenceSourceType.OBJECT, null);
                            }
                            if (slot == 0 && !lambdaIR.isStatic()) {
                                return new ThisExpr(null);
                            }
                        }
                    }

                    if (def != null) {
                        return recoverLambdaInstruction(def, lambdaIR);
                    }
                }
                if (value instanceof Constant) {
                    Constant constant = (Constant) value;
                    return recoverConstant(constant, null);
                }
                if (value instanceof SSAValue) {
                    SSAValue ssaVal = (SSAValue) value;
                    if (ssaVal.getDefinition() != null) {
                        return recover(ssaVal.getDefinition());
                    }
                }
                return new VarRefExpr("v" + System.identityHashCode(value), ReferenceSourceType.OBJECT, null);
            }

            private int calculateSlotFromParamIndex(IRMethod method, int paramIndex) {
                List<SSAValue> params = method.getParameters();
                int slot = 0;
                int pIdx = 0;
                for (SSAValue param : params) {
                    if ("this".equals(param.getName())) {
                        slot++;
                        continue;
                    }
                    if (pIdx == paramIndex) {
                        return slot;
                    }
                    slot++;
                    if (param.getType() != null && param.getType().isTwoSlot()) {
                        slot++;
                    }
                    pIdx++;
                }
                return -1;
            }

            private int findSlotForParameter(IRMethod method, SSAValue target) {
                List<SSAValue> params = method.getParameters();
                int slot = 0;
                for (SSAValue param : params) {
                    if (param == target) {
                        return slot;
                    }
                    slot++;
                    if (param.getType() != null && param.getType().isTwoSlot()) {
                        slot++;
                    }
                }
                return -1;
            }

            private Expression recoverLambdaInstruction(IRInstruction instr, IRMethod lambdaIR) {
                if (instr == null) return null;

                if (instr instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instr;
                    List<Expression> args = new ArrayList<>();
                    int start = invoke.getInvokeType() == InvokeType.STATIC ? 0 : 1;

                    Expression receiver = null;
                    if (invoke.getInvokeType() != InvokeType.STATIC && !invoke.getArguments().isEmpty()) {
                        receiver = recoverLambdaOperand(invoke.getArguments().get(0), lambdaIR);
                        if (receiver instanceof ThisExpr) {
                            receiver = null;
                        }
                    }

                    for (int i = start; i < invoke.getArguments().size(); i++) {
                        args.add(recoverLambdaOperand(invoke.getArguments().get(i), lambdaIR));
                    }

                    SourceType retType = typeRecoverer.recoverType(invoke.getResult());
                    return new MethodCallExpr(receiver, invoke.getName(), invoke.getOwner(), args,
                        invoke.getInvokeType() == InvokeType.STATIC, retType);
                }

                if (instr instanceof FieldAccessInstruction) {
                    FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
                    if (fieldAccess.isLoad()) {
                        Expression obj = fieldAccess.isStatic() ? null :
                            recoverLambdaOperand(fieldAccess.getObjectRef(), lambdaIR);
                        if (obj instanceof ThisExpr) obj = null;
                        SourceType type = typeRecoverer.recoverType(fieldAccess.getResult());
                        return new FieldAccessExpr(obj, fieldAccess.getName(), fieldAccess.getOwner(), fieldAccess.isStatic(), type);
                    }
                }

                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                    Expression left = recoverLambdaOperand(binOp.getLeft(), lambdaIR);
                    Expression right = recoverLambdaOperand(binOp.getRight(), lambdaIR);
                    BinaryOperator op = OperatorMapper.mapBinaryOp(binOp.getOp());
                    SourceType type = typeRecoverer.recoverType(binOp.getResult());
                    return new BinaryExpr(op, left, right, type);
                }

                if (instr instanceof ConstantInstruction) {
                    ConstantInstruction constInstr = (ConstantInstruction) instr;
                    return recoverConstant(constInstr.getConstant(), null);
                }

                return recover(instr);
            }
        }

        private Expression generateFallbackLambda(InvokeInstruction instr, SourceType returnType) {
            String methodName = instr.getName();
            List<LambdaParameter> params = generateLambdaParameters(null, methodName);

            boolean likelyVoid = "uncaughtException".equals(methodName) ||
                                 "hierarchyChanged".equals(methodName) ||
                                 "run".equals(methodName) ||
                                 "accept".equals(methodName);

            if (likelyVoid) {
                BlockStmt emptyBlock = new BlockStmt(Collections.emptyList());
                return new LambdaExpr(params, emptyBlock, returnType);
            } else {
                return new LambdaExpr(params, LiteralExpr.ofNull(), returnType);
            }
        }

        private List<String> parseParameterTypes(String desc) {
            List<String> types = new ArrayList<>();
            if (desc == null || !desc.startsWith("(")) return types;

            int idx = 1;
            while (idx < desc.length() && desc.charAt(idx) != ')') {
                int start = idx;
                char c = desc.charAt(idx);
                if (c == 'L') {
                    int end = desc.indexOf(';', idx);
                    if (end > 0) {
                        types.add(desc.substring(start, end + 1));
                        idx = end + 1;
                    } else {
                        break;
                    }
                } else if (c == '[') {
                    idx++;
                } else {
                    types.add(String.valueOf(c));
                    idx++;
                }
            }
            return types;
        }

        @Override
        public Expression visitNew(NewInstruction instr) {
            if (instr.getResult() != null) {
                context.registerPendingNew(instr.getResult(), instr.getClassName());
            }
            return new NewExpr(instr.getClassName());
        }

        public Expression visitFieldAccess(FieldAccessInstruction instr) {
            if (!instr.isLoad()) {
                return null;
            }
            Expression receiver = instr.isStatic() ? null : recoverOperand(instr.getObjectRef());
            SourceType type = typeRecoverer.recoverType(instr.getResult());
            return new FieldAccessExpr(receiver, instr.getName(), instr.getOwner(), instr.isStatic(), type);
        }

        public Expression visitArrayAccess(ArrayAccessInstruction instr) {
            if (!instr.isLoad()) {
                return null;
            }
            Expression array = recoverOperand(instr.getArray());
            Expression index = recoverOperand(instr.getIndex());
            SourceType type = typeRecoverer.recoverType(instr.getResult());
            return new ArrayAccessExpr(array, index, type);
        }

        @Override
        public Expression visitLoadLocal(LoadLocalInstruction instr) {
            int localIndex = instr.getLocalIndex();
            IRMethod method = instr.getBlock() != null ? instr.getBlock().getMethod() : null;
            boolean isStatic = method == null || method.isStatic();

            if (!isStatic && localIndex == 0) {
                SourceType type = typeRecoverer.recoverType(instr.getResult());
                return new ThisExpr(type);
            }

            String name = context.getVariableName(instr.getResult());
            if (name == null) {
                name = "local" + localIndex;
            }
            SourceType type = typeRecoverer.recoverType(instr.getResult());
            return new VarRefExpr(name, type, instr.getResult());
        }

        public Expression visitTypeCheck(TypeCheckInstruction instr) {
            if (instr.isCast()) {
                Expression operand = recoverOperand(instr.getOperand());
                SourceType targetType = SourceType.fromIRType(instr.getTargetType());
                return new CastExpr(targetType, operand);
            } else if (instr.isInstanceOf()) {
                Expression operand = recoverOperand(instr.getOperand());
                SourceType targetType = SourceType.fromIRType(instr.getTargetType());
                return new InstanceOfExpr(operand, targetType);
            }
            return null;
        }

        @Override
        public Expression visitNewArray(NewArrayInstruction instr) {
            SourceType elementType = SourceType.fromIRType(instr.getElementType());
            List<Expression> dims = new ArrayList<>();
            for (Value dimValue : instr.getDimensions()) {
                dims.add(recoverOperand(dimValue));
            }
            return new NewArrayExpr(elementType, dims);
        }

        public Expression visitSimple(SimpleInstruction instr) {
            if (instr.getOp() == SimpleOp.ARRAYLENGTH) {
                Expression array = recoverOperand(instr.getOperand());
                SourceType type = PrimitiveSourceType.INT;
                return new FieldAccessExpr(array, "length", "[]", false, type);
            }
            return null;
        }

        @Override
        protected Expression defaultValue() {
            return LiteralExpr.ofNull();
        }
    }
}
