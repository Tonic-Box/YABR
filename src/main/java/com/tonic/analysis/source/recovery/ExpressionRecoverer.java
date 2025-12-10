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
                return recover(def);
            }

            if (context.isRecovered(ssa)) {
                return context.getCachedExpression(ssa);
            }
            Expression expr = recover(def);
            context.cacheExpression(ssa, expr);
            return expr;
        }

        if (context.isRecovered(ssa)) {
            return context.getCachedExpression(ssa);
        }

        String name = context.getVariableName(ssa);
        if (name == null) name = "v" + ssa.getId();
        SourceType type = typeRecoverer.recoverType(ssa);
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
        if (instr instanceof ConstantInstruction) {
            return true;
        }

        if (instr instanceof LoadLocalInstruction) {
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
            if (typeHint instanceof com.tonic.analysis.source.ast.type.PrimitiveSourceType) {
                com.tonic.analysis.source.ast.type.PrimitiveSourceType pst =
                    (com.tonic.analysis.source.ast.type.PrimitiveSourceType) typeHint;
                if (pst == com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN) {
                    return LiteralExpr.ofBoolean(val != 0);
                }
                if (pst == com.tonic.analysis.source.ast.type.PrimitiveSourceType.CHAR) {
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
            SourceType classType = new ReferenceSourceType(className, java.util.Collections.emptyList());
            return new ClassExpr(classType);
        }
        return LiteralExpr.ofNull();
    }

    /**
     * Parses parameter types from a method descriptor.
     * E.g., "(ZILjava/lang/String;)V" returns ["Z", "I", "Ljava/lang/String;"]
     */
    private java.util.List<String> parseParameterTypes(String desc) {
        java.util.List<String> types = new java.util.ArrayList<>();
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

        java.util.List<Expression> parts = new java.util.ArrayList<>();
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
                type = com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN;
            }

            return new BinaryExpr(op, left, right, type);
        }

        private boolean isBooleanType(SourceType type) {
            return type == com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN;
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
            java.util.List<Expression> args = new java.util.ArrayList<>();
            int start = (instr.getInvokeType() == InvokeType.STATIC) ? 0 : 1;

            java.util.List<String> paramTypes = parseParameterTypes(instr.getDescriptor());

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
            java.util.List<Expression> args = new java.util.ArrayList<>();
            java.util.List<String> paramTypes = parseParameterTypes(instr.getDescriptor());

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
            }

            return generateFallbackLambda(instr, returnType);
        }

        /**
         * Looks up bootstrap method info for an invokedynamic instruction from the ClassFile.
         */
        private BootstrapMethodInfo lookupBootstrapInfo(InvokeInstruction instr) {
            if (instr.hasBootstrapInfo()) {
                return instr.getBootstrapInfo();
            }

            try {
                com.tonic.parser.ClassFile classFile = context.getSourceMethod().getClassFile();
                if (classFile == null) return null;

                int cpIndex = instr.getOriginalCpIndex();
                if (cpIndex <= 0) return null;

                com.tonic.parser.constpool.Item<?> item = classFile.getConstPool().getItem(cpIndex);
                if (!(item instanceof com.tonic.parser.constpool.InvokeDynamicItem)) return null;
                com.tonic.parser.constpool.InvokeDynamicItem indyItem = (com.tonic.parser.constpool.InvokeDynamicItem) item;

                int bsmIndex = indyItem.getValue().getBootstrapMethodAttrIndex();

                com.tonic.parser.attribute.BootstrapMethodsAttribute bsmAttr = null;
                for (com.tonic.parser.attribute.Attribute attr : classFile.getClassAttributes()) {
                    if (attr instanceof com.tonic.parser.attribute.BootstrapMethodsAttribute) {
                        bsmAttr = (com.tonic.parser.attribute.BootstrapMethodsAttribute) attr;
                        break;
                    }
                }
                if (bsmAttr == null) return null;

                java.util.List<com.tonic.parser.attribute.table.BootstrapMethod> bootstrapMethods = bsmAttr.getBootstrapMethods();
                if (bsmIndex < 0 || bsmIndex >= bootstrapMethods.size()) return null;

                com.tonic.parser.attribute.table.BootstrapMethod bsm = bootstrapMethods.get(bsmIndex);

                com.tonic.parser.constpool.MethodHandleItem bsmHandleItem =
                    (com.tonic.parser.constpool.MethodHandleItem) classFile.getConstPool().getItem(bsm.getBootstrapMethodRef());
                com.tonic.parser.constpool.structure.MethodHandle bsmHandle = bsmHandleItem.getValue();

                String bsmOwner = resolveMethodHandleOwner(classFile.getConstPool(), bsmHandle);
                String bsmName = resolveMethodHandleName(classFile.getConstPool(), bsmHandle);
                String bsmDesc = resolveMethodHandleDesc(classFile.getConstPool(), bsmHandle);
                com.tonic.analysis.ssa.value.MethodHandleConstant bsmConst =
                    new com.tonic.analysis.ssa.value.MethodHandleConstant(bsmHandle.getReferenceKind(), bsmOwner, bsmName, bsmDesc);

                java.util.List<com.tonic.analysis.ssa.value.Constant> bsArgs = new java.util.ArrayList<>();
                for (int argIndex : bsm.getBootstrapArguments()) {
                    com.tonic.analysis.ssa.value.Constant argConst = convertCPItemToConstant(classFile.getConstPool(), argIndex);
                    if (argConst != null) {
                        bsArgs.add(argConst);
                    }
                }

                return new BootstrapMethodInfo(bsmConst, bsArgs);

            } catch (Exception e) {
                return null;
            }
        }

        private String resolveMethodHandleOwner(com.tonic.parser.ConstPool cp, com.tonic.parser.constpool.structure.MethodHandle handle) {
            com.tonic.parser.constpool.Item<?> refItem = cp.getItem(handle.getReferenceIndex());
            if (refItem instanceof com.tonic.parser.constpool.MethodRefItem) {
                com.tonic.parser.constpool.MethodRefItem methodRef = (com.tonic.parser.constpool.MethodRefItem) refItem;
                return methodRef.getOwner();
            } else if (refItem instanceof com.tonic.parser.constpool.InterfaceRefItem) {
                com.tonic.parser.constpool.InterfaceRefItem ifaceRef = (com.tonic.parser.constpool.InterfaceRefItem) refItem;
                return ifaceRef.getOwner();
            } else if (refItem instanceof com.tonic.parser.constpool.FieldRefItem) {
                com.tonic.parser.constpool.FieldRefItem fieldRef = (com.tonic.parser.constpool.FieldRefItem) refItem;
                return fieldRef.getOwner();
            }
            return "";
        }

        private String resolveMethodHandleName(com.tonic.parser.ConstPool cp, com.tonic.parser.constpool.structure.MethodHandle handle) {
            com.tonic.parser.constpool.Item<?> refItem = cp.getItem(handle.getReferenceIndex());
            if (refItem instanceof com.tonic.parser.constpool.MethodRefItem) {
                com.tonic.parser.constpool.MethodRefItem methodRef = (com.tonic.parser.constpool.MethodRefItem) refItem;
                return methodRef.getName();
            } else if (refItem instanceof com.tonic.parser.constpool.InterfaceRefItem) {
                com.tonic.parser.constpool.InterfaceRefItem ifaceRef = (com.tonic.parser.constpool.InterfaceRefItem) refItem;
                return ifaceRef.getName();
            } else if (refItem instanceof com.tonic.parser.constpool.FieldRefItem) {
                com.tonic.parser.constpool.FieldRefItem fieldRef = (com.tonic.parser.constpool.FieldRefItem) refItem;
                return fieldRef.getName();
            }
            return "";
        }

        private String resolveMethodHandleDesc(com.tonic.parser.ConstPool cp, com.tonic.parser.constpool.structure.MethodHandle handle) {
            com.tonic.parser.constpool.Item<?> refItem = cp.getItem(handle.getReferenceIndex());
            if (refItem instanceof com.tonic.parser.constpool.MethodRefItem) {
                com.tonic.parser.constpool.MethodRefItem methodRef = (com.tonic.parser.constpool.MethodRefItem) refItem;
                return methodRef.getDescriptor();
            } else if (refItem instanceof com.tonic.parser.constpool.InterfaceRefItem) {
                com.tonic.parser.constpool.InterfaceRefItem ifaceRef = (com.tonic.parser.constpool.InterfaceRefItem) refItem;
                return ifaceRef.getDescriptor();
            } else if (refItem instanceof com.tonic.parser.constpool.FieldRefItem) {
                com.tonic.parser.constpool.FieldRefItem fieldRef = (com.tonic.parser.constpool.FieldRefItem) refItem;
                return fieldRef.getDescriptor();
            }
            return "";
        }

        private com.tonic.analysis.ssa.value.Constant convertCPItemToConstant(com.tonic.parser.ConstPool cp, int index) {
            com.tonic.parser.constpool.Item<?> item = cp.getItem(index);

            if (item instanceof com.tonic.parser.constpool.MethodTypeItem) {
                com.tonic.parser.constpool.MethodTypeItem mtItem = (com.tonic.parser.constpool.MethodTypeItem) item;
                String desc = ((com.tonic.parser.constpool.Utf8Item) cp.getItem(mtItem.getValue())).getValue();
                return new com.tonic.analysis.ssa.value.MethodTypeConstant(desc);
            } else if (item instanceof com.tonic.parser.constpool.MethodHandleItem) {
                com.tonic.parser.constpool.MethodHandleItem mhItem = (com.tonic.parser.constpool.MethodHandleItem) item;
                com.tonic.parser.constpool.structure.MethodHandle handle = mhItem.getValue();
                String owner = resolveMethodHandleOwner(cp, handle);
                String name = resolveMethodHandleName(cp, handle);
                String desc = resolveMethodHandleDesc(cp, handle);
                return new com.tonic.analysis.ssa.value.MethodHandleConstant(handle.getReferenceKind(), owner, name, desc);
            } else if (item instanceof com.tonic.parser.constpool.IntegerItem) {
                com.tonic.parser.constpool.IntegerItem intItem = (com.tonic.parser.constpool.IntegerItem) item;
                return new IntConstant(intItem.getValue());
            } else if (item instanceof com.tonic.parser.constpool.LongItem) {
                com.tonic.parser.constpool.LongItem longItem = (com.tonic.parser.constpool.LongItem) item;
                return new LongConstant(longItem.getValue());
            } else if (item instanceof com.tonic.parser.constpool.FloatItem) {
                com.tonic.parser.constpool.FloatItem floatItem = (com.tonic.parser.constpool.FloatItem) item;
                return new FloatConstant(floatItem.getValue());
            } else if (item instanceof com.tonic.parser.constpool.DoubleItem) {
                com.tonic.parser.constpool.DoubleItem doubleItem = (com.tonic.parser.constpool.DoubleItem) item;
                return new DoubleConstant(doubleItem.getValue());
            } else if (item instanceof com.tonic.parser.constpool.StringRefItem) {
                com.tonic.parser.constpool.StringRefItem strItem = (com.tonic.parser.constpool.StringRefItem) item;
                String value = ((com.tonic.parser.constpool.Utf8Item) cp.getItem(strItem.getValue())).getValue();
                return new StringConstant(value);
            } else if (item instanceof com.tonic.parser.constpool.ConstantDynamicItem) {
                com.tonic.parser.constpool.ConstantDynamicItem cdItem = (com.tonic.parser.constpool.ConstantDynamicItem) item;
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
            java.util.List<Constant> bsArgs = bsInfo.getBootstrapArguments();
            java.util.List<Value> stackArgs = instr.getArguments();

            String recipe = "";
            if (!bsArgs.isEmpty() && bsArgs.get(0) instanceof StringConstant) {
                StringConstant sc = (StringConstant) bsArgs.get(0);
                recipe = sc.getValue();
            }

            java.util.List<Expression> parts = new java.util.ArrayList<>();
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
         */
        private Expression resolveDynamicConstantExpression(DynamicConstant dc) {
            try {
                com.tonic.parser.ClassFile classFile = context.getSourceMethod().getClassFile();
                if (classFile == null) return LiteralExpr.ofNull();

                com.tonic.parser.attribute.BootstrapMethodsAttribute bsmAttr = null;
                for (com.tonic.parser.attribute.Attribute attr : classFile.getClassAttributes()) {
                    if (attr instanceof com.tonic.parser.attribute.BootstrapMethodsAttribute) {
                        com.tonic.parser.attribute.BootstrapMethodsAttribute bma = (com.tonic.parser.attribute.BootstrapMethodsAttribute) attr;
                        bsmAttr = bma;
                        break;
                    }
                }
                if (bsmAttr == null) return LiteralExpr.ofNull();

                int bsmIndex = dc.getBootstrapMethodIndex();
                if (bsmIndex < 0 || bsmIndex >= bsmAttr.getBootstrapMethods().size()) {
                    return LiteralExpr.ofNull();
                }

                com.tonic.parser.attribute.table.BootstrapMethod bsm = bsmAttr.getBootstrapMethods().get(bsmIndex);
                com.tonic.parser.constpool.MethodHandleItem bsmHandleItem =
                    (com.tonic.parser.constpool.MethodHandleItem) classFile.getConstPool().getItem(bsm.getBootstrapMethodRef());
                com.tonic.parser.constpool.structure.MethodHandle bsmHandle = bsmHandleItem.getValue();

                String bsmOwner = resolveMethodHandleOwner(classFile.getConstPool(), bsmHandle);
                String bsmName = resolveMethodHandleName(classFile.getConstPool(), bsmHandle);

                if ("java/lang/invoke/ConstantBootstraps".equals(bsmOwner) && "invoke".equals(bsmName)) {
                    return resolveConstantBootstrapsInvoke(classFile, bsm);
                }

                return LiteralExpr.ofNull();
            } catch (Exception e) {
                return LiteralExpr.ofNull();
            }
        }

        /**
         * Resolves ConstantBootstraps.invoke to the actual method call it wraps.
         */
        private Expression resolveConstantBootstrapsInvoke(com.tonic.parser.ClassFile classFile,
                                                           com.tonic.parser.attribute.table.BootstrapMethod bsm) {
            java.util.List<Integer> args = bsm.getBootstrapArguments();
            if (args.isEmpty()) return LiteralExpr.ofNull();

            com.tonic.parser.constpool.Item<?> mhItem = classFile.getConstPool().getItem(args.get(0));
            if (!(mhItem instanceof com.tonic.parser.constpool.MethodHandleItem)) {
                return LiteralExpr.ofNull();
            }
            com.tonic.parser.constpool.MethodHandleItem handleItem = (com.tonic.parser.constpool.MethodHandleItem) mhItem;

            com.tonic.parser.constpool.structure.MethodHandle handle = handleItem.getValue();
            String owner = resolveMethodHandleOwner(classFile.getConstPool(), handle);
            String name = resolveMethodHandleName(classFile.getConstPool(), handle);
            String desc = resolveMethodHandleDesc(classFile.getConstPool(), handle);

            java.util.List<Expression> callArgs = new java.util.ArrayList<>();
            for (int i = 1; i < args.size(); i++) {
                Constant argConst = convertCPItemToConstant(classFile.getConstPool(), args.get(i));
                if (argConst != null) {
                    callArgs.add(recoverConstant(argConst, null));
                }
            }

            String retDesc = desc.substring(desc.indexOf(')') + 1);
            SourceType retType = SourceType.fromIRType(com.tonic.analysis.ssa.type.IRType.fromDescriptor(retDesc));

            return MethodCallExpr.staticCall(owner, name, callArgs, retType);
        }

        /**
         * Handles LambdaMetafactory bootstrap - can generate method reference or lambda.
         */
        private Expression handleLambdaMetafactory(InvokeInstruction instr, BootstrapMethodInfo bsInfo, SourceType returnType) {
            java.util.List<com.tonic.analysis.ssa.value.Constant> args = bsInfo.getBootstrapArguments();

            if (args.size() >= 2 && args.get(1) instanceof com.tonic.analysis.ssa.value.MethodHandleConstant) {
                com.tonic.analysis.ssa.value.MethodHandleConstant implHandle = (com.tonic.analysis.ssa.value.MethodHandleConstant) args.get(1);
                String implOwner = implHandle.getOwner();
                String implName = implHandle.getName();
                String implDesc = implHandle.getDescriptor();
                int refKind = implHandle.getReferenceKind();

                String samDescriptor = null;
                if (args.size() >= 1 && args.get(0) instanceof com.tonic.analysis.ssa.value.MethodTypeConstant) {
                    com.tonic.analysis.ssa.value.MethodTypeConstant samType = (com.tonic.analysis.ssa.value.MethodTypeConstant) args.get(0);
                    samDescriptor = samType.getDescriptor();
                }

                String currentClassName = context.getSourceMethod().getClassFile() != null
                    ? context.getSourceMethod().getClassFile().getClassName() : null;
                boolean isInSameClass = currentClassName != null &&
                    implOwner.replace('/', '.').equals(currentClassName.replace('/', '.'));

                if (isInSameClass) {
                    java.util.List<LambdaParameter> params = generateLambdaParameters(samDescriptor, instr.getName());
                    com.tonic.analysis.source.ast.ASTNode body = generateLambdaBody(implHandle, instr, params, samDescriptor);

                    if (body != null && !isEmptyBody(body)) {
                        return new LambdaExpr(params, body, returnType);
                    }
                }

                boolean isSyntheticLambda = implName.startsWith("lambda$");
                if (isSyntheticLambda) {
                    java.util.List<LambdaParameter> params = generateLambdaParameters(samDescriptor, instr.getName());
                    com.tonic.analysis.source.ast.ASTNode body = generateLambdaBody(implHandle, instr, params, samDescriptor);
                    return new LambdaExpr(params, body, returnType);
                }

                return createMethodReference(implHandle, instr, returnType);
            }

            return generateFallbackLambda(instr, returnType);
        }

        /**
         * Creates a method reference expression from a method handle.
         */
        private Expression createMethodReference(com.tonic.analysis.ssa.value.MethodHandleConstant handle,
                                                  InvokeInstruction instr, SourceType returnType) {
            String owner = handle.getOwner();
            String name = handle.getName();
            int refKind = handle.getReferenceKind();

            if ("<init>".equals(name) || refKind == com.tonic.analysis.ssa.value.MethodHandleConstant.REF_newInvokeSpecial) {
                return MethodRefExpr.constructorRef(owner, returnType);
            }

            MethodRefKind kind;
            Expression receiver = null;

            if (refKind == com.tonic.analysis.ssa.value.MethodHandleConstant.REF_invokeStatic) {
                kind = MethodRefKind.STATIC;
            } else if (refKind == com.tonic.analysis.ssa.value.MethodHandleConstant.REF_invokeSpecial ||
                       refKind == com.tonic.analysis.ssa.value.MethodHandleConstant.REF_invokeVirtual ||
                       refKind == com.tonic.analysis.ssa.value.MethodHandleConstant.REF_invokeInterface) {
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
        private java.util.List<LambdaParameter> generateLambdaParameters(String samDescriptor, String samMethodName) {
            java.util.List<LambdaParameter> params = new java.util.ArrayList<>();

            int paramCount = 0;
            if (samDescriptor != null && samDescriptor.startsWith("(")) {
                java.util.List<String> paramTypes = parseParameterTypes(samDescriptor);
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
        private com.tonic.analysis.source.ast.ASTNode generateLambdaBody(
                com.tonic.analysis.ssa.value.MethodHandleConstant handle,
                InvokeInstruction instr,
                java.util.List<LambdaParameter> params,
                String samDescriptor) {

            String implName = handle.getName();
            String implOwner = handle.getOwner();

            try {
                com.tonic.parser.ClassFile classFile = context.getSourceMethod().getClassFile();
                if (classFile != null) {
                    com.tonic.parser.MethodEntry lambdaMethod = null;
                    for (com.tonic.parser.MethodEntry method : classFile.getMethods()) {
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
                return new com.tonic.analysis.source.ast.stmt.BlockStmt(java.util.Collections.emptyList());
            } else {
                return LiteralExpr.ofNull();
            }
        }

        /**
         * Checks if a lambda body is empty (fallback case).
         */
        private boolean isEmptyBody(com.tonic.analysis.source.ast.ASTNode body) {
            if (body instanceof com.tonic.analysis.source.ast.stmt.BlockStmt) {
                com.tonic.analysis.source.ast.stmt.BlockStmt block = (com.tonic.analysis.source.ast.stmt.BlockStmt) body;
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
        private com.tonic.analysis.source.ast.ASTNode decompileLambdaMethod(
                com.tonic.parser.MethodEntry lambdaMethod,
                InvokeInstruction instr,
                java.util.List<LambdaParameter> params,
                com.tonic.analysis.ssa.value.MethodHandleConstant handle,
                String samDescriptor) {

            try {
                com.tonic.parser.ClassFile classFile = context.getSourceMethod().getClassFile();
                com.tonic.analysis.ssa.SSA ssa = new com.tonic.analysis.ssa.SSA(classFile.getConstPool());
                IRMethod lambdaIR = ssa.lift(lambdaMethod);

                com.tonic.analysis.ssa.analysis.DefUseChains defUseChains =
                    new com.tonic.analysis.ssa.analysis.DefUseChains(lambdaIR);
                defUseChains.compute();

                RecoveryContext lambdaContext = new RecoveryContext(lambdaIR, lambdaMethod, defUseChains);

                int capturedCount = instr.getArguments().size();
                boolean isInstanceMethod = !lambdaMethod.getDesc().startsWith("()") &&
                    (handle.getReferenceKind() != com.tonic.analysis.ssa.value.MethodHandleConstant.REF_invokeStatic);

                java.util.Map<Integer, Expression> capturedMapping = new java.util.HashMap<>();
                int slot = isInstanceMethod ? 1 : 0;

                for (int i = 0; i < capturedCount; i++) {
                    Expression capturedExpr = recoverOperand(instr.getArguments().get(i));
                    capturedMapping.put(slot, capturedExpr);
                    slot++;
                }

                java.util.Map<Integer, String> paramMapping = new java.util.HashMap<>();
                for (int i = 0; i < params.size(); i++) {
                    paramMapping.put(slot + i, params.get(i).name());
                }

                if (isInstanceMethod) {
                    lambdaContext.setVariableName(findSSAForSlot(lambdaIR, 0), "this");
                }
                for (java.util.Map.Entry<Integer, String> entry : paramMapping.entrySet()) {
                    com.tonic.analysis.ssa.value.SSAValue slotSSA = findSSAForSlot(lambdaIR, entry.getKey());
                    if (slotSSA != null) {
                        lambdaContext.setVariableName(slotSSA, entry.getValue());
                    }
                }

                LambdaExpressionRecoverer lambdaExprRecoverer =
                    new LambdaExpressionRecoverer(lambdaContext, capturedMapping, paramMapping);

                com.tonic.analysis.source.ast.ASTNode body = lambdaExprRecoverer.extractLambdaBody(lambdaIR);
                if (body != null) {
                    return body;
                }

            } catch (Exception e) {
            }

            boolean isVoidReturn = samDescriptor != null && samDescriptor.endsWith(")V");
            if (isVoidReturn) {
                return new com.tonic.analysis.source.ast.stmt.BlockStmt(java.util.Collections.emptyList());
            } else {
                return LiteralExpr.ofNull();
            }
        }

        /**
         * Finds the SSA value for a given local slot in the method's entry block.
         */
        private com.tonic.analysis.ssa.value.SSAValue findSSAForSlot(IRMethod method, int slot) {
            for (com.tonic.analysis.ssa.cfg.IRBlock block : method.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof LoadLocalInstruction) {
                        LoadLocalInstruction load = (LoadLocalInstruction) instr;
                        if (load.getLocalIndex() == slot) {
                            return load.getResult();
                        }
                    }
                }
            }
            return null;
        }

        /**
         * Specialized expression recoverer for lambda bodies that maps captured variables
         * and SAM parameters to appropriate expressions.
         */
        private class LambdaExpressionRecoverer extends ExpressionRecoverer {
            private final java.util.Map<Integer, Expression> capturedMapping;
            private final java.util.Map<Integer, String> paramMapping;

            LambdaExpressionRecoverer(RecoveryContext ctx,
                                       java.util.Map<Integer, Expression> capturedMapping,
                                       java.util.Map<Integer, String> paramMapping) {
                super(ctx);
                this.capturedMapping = capturedMapping;
                this.paramMapping = paramMapping;
            }

            /**
             * Extracts the lambda body from the IR method.
             * For simple lambdas, returns the single expression.
             * For complex lambdas, returns a block statement.
             */
            com.tonic.analysis.source.ast.ASTNode extractLambdaBody(IRMethod lambdaIR) {
                com.tonic.analysis.ssa.cfg.IRBlock entryBlock = lambdaIR.getEntryBlock();
                if (entryBlock == null) return null;

                java.util.List<IRInstruction> instructions = entryBlock.getInstructions();
                IRInstruction terminator = entryBlock.getTerminator();

                if (terminator instanceof ReturnInstruction) {
                    ReturnInstruction ret = (ReturnInstruction) terminator;
                    Value returnValue = ret.getReturnValue();
                    if (returnValue != null) {
                        Expression expr = recoverLambdaOperand(returnValue, lambdaIR);
                        return expr;
                    } else {
                        for (int i = instructions.size() - 1; i >= 0; i--) {
                            IRInstruction instr = instructions.get(i);
                            if (instr instanceof InvokeInstruction) {
                                Expression expr = recoverLambdaInstruction(instr, lambdaIR);
                                if (expr != null) {
                                    return expr;
                                }
                            }
                        }
                        return new com.tonic.analysis.source.ast.stmt.BlockStmt(java.util.Collections.emptyList());
                    }
                }

                return null;
            }

            private Expression recoverLambdaOperand(Value value, IRMethod lambdaIR) {
                if (value instanceof com.tonic.analysis.ssa.value.SSAValue) {
                    com.tonic.analysis.ssa.value.SSAValue ssa = (com.tonic.analysis.ssa.value.SSAValue) value;
                    IRInstruction def = ssa.getDefinition();

                    String ssaName = ssa.getName();
                    if (def == null && ssaName != null && ssaName.startsWith("p")) {
                        try {
                            int paramIndex = Integer.parseInt(ssaName.substring(1));
                            int slot = lambdaIR.isStatic() ? paramIndex : paramIndex;
                            if (paramMapping.containsKey(slot)) {
                                String paramName = paramMapping.get(slot);
                                SourceType type = typeRecoverer.recoverType(ssa);
                                return new VarRefExpr(paramName, type != null ? type : com.tonic.analysis.source.ast.type.ReferenceSourceType.OBJECT, null);
                            }
                            if (capturedMapping.containsKey(slot)) {
                                return capturedMapping.get(slot);
                            }
                        } catch (NumberFormatException e) {
                        }
                    }

                    if (def instanceof LoadLocalInstruction) {
                        LoadLocalInstruction load = (LoadLocalInstruction) def;
                        int slot = load.getLocalIndex();
                        if (capturedMapping.containsKey(slot)) {
                            return capturedMapping.get(slot);
                        }
                        if (paramMapping.containsKey(slot)) {
                            String paramName = paramMapping.get(slot);
                            SourceType type = typeRecoverer.recoverType(ssa);
                            return new VarRefExpr(paramName, type != null ? type : com.tonic.analysis.source.ast.type.ReferenceSourceType.OBJECT, null);
                        }
                        if (slot == 0 && !lambdaIR.isStatic()) {
                            return new ThisExpr(null);
                        }
                    }
                    if (def != null) {
                        return recoverLambdaInstruction(def, lambdaIR);
                    }
                }
                if (value instanceof com.tonic.analysis.ssa.value.Constant) {
                    com.tonic.analysis.ssa.value.Constant constant = (com.tonic.analysis.ssa.value.Constant) value;
                    return recoverConstant(constant, null);
                }
                if (value instanceof com.tonic.analysis.ssa.value.SSAValue) {
                    com.tonic.analysis.ssa.value.SSAValue ssaVal = (com.tonic.analysis.ssa.value.SSAValue) value;
                    if (ssaVal.getDefinition() != null) {
                        return recover(ssaVal.getDefinition());
                    }
                }
                return new VarRefExpr("v" + System.identityHashCode(value),
                    com.tonic.analysis.source.ast.type.ReferenceSourceType.OBJECT, null);
            }

            private Expression recoverLambdaInstruction(IRInstruction instr, IRMethod lambdaIR) {
                if (instr == null) return null;

                if (instr instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instr;
                    java.util.List<Expression> args = new java.util.ArrayList<>();
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

                if (instr instanceof GetFieldInstruction) {
                    GetFieldInstruction getField = (GetFieldInstruction) instr;
                    Expression obj = getField.isStatic() ? null :
                        recoverLambdaOperand(getField.getObjectRef(), lambdaIR);
                    if (obj instanceof ThisExpr) obj = null;
                    SourceType type = typeRecoverer.recoverType(getField.getResult());
                    return new FieldAccessExpr(obj, getField.getName(), getField.getOwner(), getField.isStatic(), type);
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

        /**
         * Generates a fallback lambda when bootstrap info is not available.
         */
        private Expression generateFallbackLambda(InvokeInstruction instr, SourceType returnType) {
            String methodName = instr.getName();
            java.util.List<LambdaParameter> params = generateLambdaParameters(null, methodName);

            boolean likelyVoid = "uncaughtException".equals(methodName) ||
                                 "hierarchyChanged".equals(methodName) ||
                                 "run".equals(methodName) ||
                                 "accept".equals(methodName);

            if (likelyVoid) {
                com.tonic.analysis.source.ast.stmt.BlockStmt emptyBlock =
                    new com.tonic.analysis.source.ast.stmt.BlockStmt(java.util.Collections.emptyList());
                return new LambdaExpr(params, emptyBlock, returnType);
            } else {
                return new LambdaExpr(params, LiteralExpr.ofNull(), returnType);
            }
        }

        private java.util.List<String> parseParameterTypes(String desc) {
            java.util.List<String> types = new java.util.ArrayList<>();
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

        @Override
        public Expression visitCast(CastInstruction instr) {
            Expression operand = recoverOperand(instr.getObjectRef());
            SourceType targetType = SourceType.fromIRType(instr.getTargetType());
            return new CastExpr(targetType, operand);
        }

        @Override
        public Expression visitNewArray(NewArrayInstruction instr) {
            SourceType elementType = SourceType.fromIRType(instr.getElementType());
            java.util.List<Expression> dims = new java.util.ArrayList<>();
            for (com.tonic.analysis.ssa.value.Value dimValue : instr.getDimensions()) {
                dims.add(recoverOperand(dimValue));
            }
            return new NewArrayExpr(elementType, dims);
        }

        @Override
        public Expression visitArrayLength(ArrayLengthInstruction instr) {
            Expression array = recoverOperand(instr.getArray());
            SourceType type = com.tonic.analysis.source.ast.type.PrimitiveSourceType.INT;
            return new FieldAccessExpr(array, "length", "[]", false, type);
        }

        @Override
        public Expression visitInstanceOf(InstanceOfInstruction instr) {
            Expression operand = recoverOperand(instr.getObjectRef());
            SourceType targetType = SourceType.fromIRType(instr.getCheckType());
            return new InstanceOfExpr(operand, targetType);
        }

        @Override
        protected Expression defaultValue() {
            return LiteralExpr.ofNull();
        }
    }
}
