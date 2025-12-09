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
                // Array type - need to capture the whole array descriptor
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

    private class RecoveryVisitor extends AbstractIRVisitor<Expression> {
        @Override
        public Expression visitBinaryOp(BinaryOpInstruction instr) {
            Expression left = recoverOperand(instr.getLeft());
            Expression right = recoverOperand(instr.getRight());
            BinaryOperator op = OperatorMapper.mapBinaryOp(instr.getOp());
            SourceType type = typeRecoverer.recoverType(instr.getResult());

            // For bitwise AND/OR/XOR, if at least one operand is boolean, result is boolean
            // JVM uses IAND/IOR/IXOR for boolean operations, but result should be typed as boolean
            // This also handles cases where one operand is boolean and the other is a boolean PHI
            // (which may be typed as int because it comes from 0/1 constants)
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
            // Handle <init> constructor calls
            if ("<init>".equals(instr.getName())) {
                return handleConstructorCall(instr);
            }

            // Handle invokedynamic (lambda expressions)
            if (instr.isDynamic()) {
                return handleInvokeDynamic(instr);
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

            // Parse parameter types from descriptor to provide type hints for boolean detection
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
            return new MethodCallExpr(receiver, instr.getName(), instr.getOwner(), args, isStatic, retType);
        }

        private Expression handleConstructorCall(InvokeInstruction instr) {
            java.util.List<Expression> args = new java.util.ArrayList<>();
            // Constructor args start at index 1 (index 0 is the uninitialized object)
            // Parse parameter types for boolean detection
            java.util.List<String> paramTypes = parseParameterTypes(instr.getDescriptor());

            for (int i = 1; i < instr.getArguments().size(); i++) {
                int paramIndex = i - 1; // Offset by 1 since index 0 is the receiver
                SourceType typeHint = null;
                if (paramIndex < paramTypes.size()) {
                    typeHint = typeRecoverer.recoverType(paramTypes.get(paramIndex));
                }
                args.add(recoverOperand(instr.getArguments().get(i), typeHint));
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

        /**
         * Handles invokedynamic instructions which typically create lambda expressions or method references.
         * Attempts to generate proper method references or lambda expressions with correct bodies.
         */
        private Expression handleInvokeDynamic(InvokeInstruction instr) {
            // Get the return type - this is the functional interface type
            SourceType returnType = typeRecoverer.recoverType(instr.getResult());

            // Try to look up bootstrap info from the ClassFile
            BootstrapMethodInfo bsInfo = lookupBootstrapInfo(instr);

            if (bsInfo != null && bsInfo.isLambdaMetafactory()) {
                return handleLambdaMetafactory(instr, bsInfo, returnType);
            }

            // Fallback: generate lambda with method call body
            return generateFallbackLambda(instr, returnType);
        }

        /**
         * Looks up bootstrap method info for an invokedynamic instruction from the ClassFile.
         */
        private BootstrapMethodInfo lookupBootstrapInfo(InvokeInstruction instr) {
            // First check if it's already available
            if (instr.hasBootstrapInfo()) {
                return instr.getBootstrapInfo();
            }

            try {
                com.tonic.parser.ClassFile classFile = context.getSourceMethod().getClassFile();
                if (classFile == null) return null;

                // Get the InvokeDynamic constant pool item
                int cpIndex = instr.getOriginalCpIndex();
                if (cpIndex <= 0) return null;

                com.tonic.parser.constpool.Item<?> item = classFile.getConstPool().getItem(cpIndex);
                if (!(item instanceof com.tonic.parser.constpool.InvokeDynamicItem indyItem)) return null;

                // Get bootstrap method index
                int bsmIndex = indyItem.getValue().getBootstrapMethodAttrIndex();

                // Find BootstrapMethods attribute
                com.tonic.parser.attribute.BootstrapMethodsAttribute bsmAttr = null;
                for (com.tonic.parser.attribute.Attribute attr : classFile.getClassAttributes()) {
                    if (attr instanceof com.tonic.parser.attribute.BootstrapMethodsAttribute bma) {
                        bsmAttr = bma;
                        break;
                    }
                }
                if (bsmAttr == null) return null;

                java.util.List<com.tonic.parser.attribute.table.BootstrapMethod> bootstrapMethods = bsmAttr.getBootstrapMethods();
                if (bsmIndex < 0 || bsmIndex >= bootstrapMethods.size()) return null;

                com.tonic.parser.attribute.table.BootstrapMethod bsm = bootstrapMethods.get(bsmIndex);

                // Get the bootstrap method handle
                com.tonic.parser.constpool.MethodHandleItem bsmHandleItem =
                    (com.tonic.parser.constpool.MethodHandleItem) classFile.getConstPool().getItem(bsm.getBootstrapMethodRef());
                com.tonic.parser.constpool.structure.MethodHandle bsmHandle = bsmHandleItem.getValue();

                // Create MethodHandleConstant for the bootstrap method
                String bsmOwner = resolveMethodHandleOwner(classFile.getConstPool(), bsmHandle);
                String bsmName = resolveMethodHandleName(classFile.getConstPool(), bsmHandle);
                String bsmDesc = resolveMethodHandleDesc(classFile.getConstPool(), bsmHandle);
                com.tonic.analysis.ssa.value.MethodHandleConstant bsmConst =
                    new com.tonic.analysis.ssa.value.MethodHandleConstant(bsmHandle.getReferenceKind(), bsmOwner, bsmName, bsmDesc);

                // Convert bootstrap arguments to Constants
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
            if (refItem instanceof com.tonic.parser.constpool.MethodRefItem methodRef) {
                return methodRef.getOwner();
            } else if (refItem instanceof com.tonic.parser.constpool.InterfaceRefItem ifaceRef) {
                return ifaceRef.getOwner();
            } else if (refItem instanceof com.tonic.parser.constpool.FieldRefItem fieldRef) {
                return fieldRef.getOwner();
            }
            return "";
        }

        private String resolveMethodHandleName(com.tonic.parser.ConstPool cp, com.tonic.parser.constpool.structure.MethodHandle handle) {
            com.tonic.parser.constpool.Item<?> refItem = cp.getItem(handle.getReferenceIndex());
            if (refItem instanceof com.tonic.parser.constpool.MethodRefItem methodRef) {
                return methodRef.getName();
            } else if (refItem instanceof com.tonic.parser.constpool.InterfaceRefItem ifaceRef) {
                return ifaceRef.getName();
            } else if (refItem instanceof com.tonic.parser.constpool.FieldRefItem fieldRef) {
                return fieldRef.getName();
            }
            return "";
        }

        private String resolveMethodHandleDesc(com.tonic.parser.ConstPool cp, com.tonic.parser.constpool.structure.MethodHandle handle) {
            com.tonic.parser.constpool.Item<?> refItem = cp.getItem(handle.getReferenceIndex());
            if (refItem instanceof com.tonic.parser.constpool.MethodRefItem methodRef) {
                return methodRef.getDescriptor();
            } else if (refItem instanceof com.tonic.parser.constpool.InterfaceRefItem ifaceRef) {
                return ifaceRef.getDescriptor();
            } else if (refItem instanceof com.tonic.parser.constpool.FieldRefItem fieldRef) {
                return fieldRef.getDescriptor();
            }
            return "";
        }

        private com.tonic.analysis.ssa.value.Constant convertCPItemToConstant(com.tonic.parser.ConstPool cp, int index) {
            com.tonic.parser.constpool.Item<?> item = cp.getItem(index);

            if (item instanceof com.tonic.parser.constpool.MethodTypeItem mtItem) {
                // MethodType constant - getValue() returns the descriptor index
                String desc = ((com.tonic.parser.constpool.Utf8Item) cp.getItem(mtItem.getValue())).getValue();
                return new com.tonic.analysis.ssa.value.MethodTypeConstant(desc);
            } else if (item instanceof com.tonic.parser.constpool.MethodHandleItem mhItem) {
                // MethodHandle constant
                com.tonic.parser.constpool.structure.MethodHandle handle = mhItem.getValue();
                String owner = resolveMethodHandleOwner(cp, handle);
                String name = resolveMethodHandleName(cp, handle);
                String desc = resolveMethodHandleDesc(cp, handle);
                return new com.tonic.analysis.ssa.value.MethodHandleConstant(handle.getReferenceKind(), owner, name, desc);
            } else if (item instanceof com.tonic.parser.constpool.IntegerItem intItem) {
                return new IntConstant(intItem.getValue());
            } else if (item instanceof com.tonic.parser.constpool.LongItem longItem) {
                return new LongConstant(longItem.getValue());
            } else if (item instanceof com.tonic.parser.constpool.FloatItem floatItem) {
                return new FloatConstant(floatItem.getValue());
            } else if (item instanceof com.tonic.parser.constpool.DoubleItem doubleItem) {
                return new DoubleConstant(doubleItem.getValue());
            } else if (item instanceof com.tonic.parser.constpool.StringRefItem strItem) {
                String value = ((com.tonic.parser.constpool.Utf8Item) cp.getItem(strItem.getValue())).getValue();
                return new StringConstant(value);
            }

            return null;
        }

        /**
         * Handles LambdaMetafactory bootstrap - can generate method reference or lambda.
         */
        private Expression handleLambdaMetafactory(InvokeInstruction instr, BootstrapMethodInfo bsInfo, SourceType returnType) {
            java.util.List<com.tonic.analysis.ssa.value.Constant> args = bsInfo.getBootstrapArguments();

            // Bootstrap arguments for LambdaMetafactory.metafactory:
            // 0: MethodType - SAM method type (what the functional interface expects)
            // 1: MethodHandle - the implementation method
            // 2: MethodType - instantiated method type (may differ for generics)

            if (args.size() >= 2 && args.get(1) instanceof com.tonic.analysis.ssa.value.MethodHandleConstant implHandle) {
                String implOwner = implHandle.getOwner();
                String implName = implHandle.getName();
                String implDesc = implHandle.getDescriptor();
                int refKind = implHandle.getReferenceKind();

                // Get the SAM method type to determine parameter count
                String samDescriptor = null;
                if (args.size() >= 1 && args.get(0) instanceof com.tonic.analysis.ssa.value.MethodTypeConstant samType) {
                    samDescriptor = samType.getDescriptor();
                }

                // Try to inline the lambda body if the implementation is in the same class
                // This produces cleaner output like CFR does, especially for obfuscated code
                // where synthetic lambdas may not have the standard "lambda$" naming
                String currentClassName = context.getSourceMethod().getClassFile() != null
                    ? context.getSourceMethod().getClassFile().getClassName() : null;
                boolean isInSameClass = currentClassName != null &&
                    implOwner.replace('/', '.').equals(currentClassName.replace('/', '.'));

                // First, try to inline the lambda body if it's in the same class
                if (isInSameClass) {
                    java.util.List<LambdaParameter> params = generateLambdaParameters(samDescriptor, instr.getName());
                    com.tonic.analysis.source.ast.ASTNode body = generateLambdaBody(implHandle, instr, params, samDescriptor);

                    // Only use the inlined body if it's not an empty fallback
                    if (body != null && !isEmptyBody(body)) {
                        return new LambdaExpr(params, body, returnType);
                    }
                }

                // Fallback to method reference if we couldn't inline or it's from a different class
                // Standard synthetic lambdas (lambda$...) that we couldn't inline should still be lambdas
                boolean isSyntheticLambda = implName.startsWith("lambda$");
                if (isSyntheticLambda) {
                    java.util.List<LambdaParameter> params = generateLambdaParameters(samDescriptor, instr.getName());
                    com.tonic.analysis.source.ast.ASTNode body = generateLambdaBody(implHandle, instr, params, samDescriptor);
                    return new LambdaExpr(params, body, returnType);
                }

                // Use method reference for external methods
                return createMethodReference(implHandle, instr, returnType);
            }

            // Fallback if bootstrap args are not what we expect
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

            // Handle constructor references
            if ("<init>".equals(name) || refKind == com.tonic.analysis.ssa.value.MethodHandleConstant.REF_newInvokeSpecial) {
                return MethodRefExpr.constructorRef(owner, returnType);
            }

            // Handle different reference kinds
            MethodRefKind kind;
            Expression receiver = null;

            if (refKind == com.tonic.analysis.ssa.value.MethodHandleConstant.REF_invokeStatic) {
                kind = MethodRefKind.STATIC;
            } else if (refKind == com.tonic.analysis.ssa.value.MethodHandleConstant.REF_invokeSpecial ||
                       refKind == com.tonic.analysis.ssa.value.MethodHandleConstant.REF_invokeVirtual ||
                       refKind == com.tonic.analysis.ssa.value.MethodHandleConstant.REF_invokeInterface) {
                // Check if there's a bound receiver in the invokedynamic arguments
                if (!instr.getArguments().isEmpty()) {
                    // Bound method reference - expr::method
                    receiver = recoverOperand(instr.getArguments().get(0));
                    kind = MethodRefKind.BOUND;
                } else {
                    // Unbound instance method reference - Type::method
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

            // Parse SAM descriptor to count parameters
            int paramCount = 0;
            if (samDescriptor != null && samDescriptor.startsWith("(")) {
                java.util.List<String> paramTypes = parseParameterTypes(samDescriptor);
                paramCount = paramTypes.size();
            }

            // Use appropriate parameter names based on method name and count
            if ("uncaughtException".equals(samMethodName)) {
                params.add(new LambdaParameter("t", null, true));
                params.add(new LambdaParameter("e", null, true));
            } else if ("hierarchyChanged".equals(samMethodName)) {
                params.add(new LambdaParameter("event", null, true));
            } else if ("run".equals(samMethodName) && paramCount == 0) {
                // Runnable - no params
            } else if ("accept".equals(samMethodName)) {
                params.add(new LambdaParameter("t", null, true));
            } else if ("apply".equals(samMethodName)) {
                params.add(new LambdaParameter("t", null, true));
            } else if ("test".equals(samMethodName)) {
                params.add(new LambdaParameter("t", null, true));
            } else if ("get".equals(samMethodName) && paramCount == 0) {
                // Supplier - no params
            } else {
                // Generic parameter names
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

            // Try to decompile the synthetic lambda method
            try {
                com.tonic.parser.ClassFile classFile = context.getSourceMethod().getClassFile();
                if (classFile != null) {
                    // Find the synthetic lambda method
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
                // Fall through to fallback
            }

            // Fallback: generate empty block or null
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
            if (body instanceof com.tonic.analysis.source.ast.stmt.BlockStmt block) {
                return block.getStatements().isEmpty();
            }
            if (body instanceof LiteralExpr lit) {
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
                // Lift the lambda method to IR
                com.tonic.parser.ClassFile classFile = context.getSourceMethod().getClassFile();
                com.tonic.analysis.ssa.SSA ssa = new com.tonic.analysis.ssa.SSA(classFile.getConstPool());
                IRMethod lambdaIR = ssa.lift(lambdaMethod);

                // Create a minimal recovery context for the lambda
                com.tonic.analysis.ssa.analysis.DefUseChains defUseChains =
                    new com.tonic.analysis.ssa.analysis.DefUseChains(lambdaIR);
                defUseChains.compute();

                RecoveryContext lambdaContext = new RecoveryContext(lambdaIR, lambdaMethod, defUseChains);

                // Map lambda parameters - the lambda method receives:
                // 1. Captured variables (from invokedynamic arguments) as first parameters
                // 2. SAM parameters as remaining parameters
                int capturedCount = instr.getArguments().size();
                boolean isInstanceMethod = !lambdaMethod.getDesc().startsWith("()") &&
                    (handle.getReferenceKind() != com.tonic.analysis.ssa.value.MethodHandleConstant.REF_invokeStatic);

                // Map captured variables to their expressions
                java.util.Map<Integer, Expression> capturedMapping = new java.util.HashMap<>();
                int slot = isInstanceMethod ? 1 : 0; // Start after 'this' if instance method

                for (int i = 0; i < capturedCount; i++) {
                    Expression capturedExpr = recoverOperand(instr.getArguments().get(i));
                    capturedMapping.put(slot, capturedExpr);
                    // Advance slot (assume single-slot for simplicity)
                    slot++;
                }

                // Map SAM parameters to lambda parameter names
                java.util.Map<Integer, String> paramMapping = new java.util.HashMap<>();
                for (int i = 0; i < params.size(); i++) {
                    paramMapping.put(slot + i, params.get(i).name());
                }

                // Set up variable names in lambda context
                if (isInstanceMethod) {
                    lambdaContext.setVariableName(findSSAForSlot(lambdaIR, 0), "this");
                }
                for (java.util.Map.Entry<Integer, String> entry : paramMapping.entrySet()) {
                    com.tonic.analysis.ssa.value.SSAValue slotSSA = findSSAForSlot(lambdaIR, entry.getKey());
                    if (slotSSA != null) {
                        lambdaContext.setVariableName(slotSSA, entry.getValue());
                    }
                }

                // Create expression recoverer for lambda with mapping
                LambdaExpressionRecoverer lambdaExprRecoverer =
                    new LambdaExpressionRecoverer(lambdaContext, capturedMapping, paramMapping);

                // Try to extract a simple expression body
                com.tonic.analysis.source.ast.ASTNode body = lambdaExprRecoverer.extractLambdaBody(lambdaIR);
                if (body != null) {
                    return body;
                }

            } catch (Exception e) {
                // Lambda decompilation failed - fall through to fallback
            }

            // Fallback
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
                    if (instr instanceof LoadLocalInstruction load && load.getLocalIndex() == slot) {
                        return load.getResult();
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
                // For simple lambdas, the body is often just:
                // - A single return statement with an expression
                // - A single void method call

                com.tonic.analysis.ssa.cfg.IRBlock entryBlock = lambdaIR.getEntryBlock();
                if (entryBlock == null) return null;

                java.util.List<IRInstruction> instructions = entryBlock.getInstructions();
                IRInstruction terminator = entryBlock.getTerminator();

                // Simple case: single block with return terminator
                if (terminator instanceof ReturnInstruction ret) {
                    Value returnValue = ret.getReturnValue();
                    if (returnValue != null) {
                        // Expression lambda with return value
                        Expression expr = recoverLambdaOperand(returnValue, lambdaIR);
                        return expr;
                    } else {
                        // Void lambda - find the last meaningful statement (usually an invoke)
                        // Skip load instructions as they're just setting up the call
                        for (int i = instructions.size() - 1; i >= 0; i--) {
                            IRInstruction instr = instructions.get(i);
                            // Invoke is the main action
                            if (instr instanceof InvokeInstruction) {
                                Expression expr = recoverLambdaInstruction(instr, lambdaIR);
                                if (expr != null) {
                                    return expr;
                                }
                            }
                        }
                        // No invoke found - empty body
                        return new com.tonic.analysis.source.ast.stmt.BlockStmt(java.util.Collections.emptyList());
                    }
                }

                // More complex: multiple blocks - need full statement recovery
                // For now, return null to trigger method reference fallback
                return null;
            }

            private Expression recoverLambdaOperand(Value value, IRMethod lambdaIR) {
                if (value instanceof com.tonic.analysis.ssa.value.SSAValue ssa) {
                    IRInstruction def = ssa.getDefinition();

                    // Handle parameter SSA values (they have names like "p0", "p1" and no definition)
                    String ssaName = ssa.getName();
                    if (def == null && ssaName != null && ssaName.startsWith("p")) {
                        try {
                            int paramIndex = Integer.parseInt(ssaName.substring(1));
                            // Map parameter index to slot considering static vs instance
                            int slot = lambdaIR.isStatic() ? paramIndex : paramIndex; // p0=arg0, p1=arg1, etc.
                            if (paramMapping.containsKey(slot)) {
                                String paramName = paramMapping.get(slot);
                                SourceType type = typeRecoverer.recoverType(ssa);
                                return new VarRefExpr(paramName, type != null ? type : com.tonic.analysis.source.ast.type.ReferenceSourceType.OBJECT, null);
                            }
                            // Check captured
                            if (capturedMapping.containsKey(slot)) {
                                return capturedMapping.get(slot);
                            }
                        } catch (NumberFormatException e) {
                            // Fall through
                        }
                    }

                    if (def instanceof LoadLocalInstruction load) {
                        int slot = load.getLocalIndex();
                        // Check if it's a captured variable
                        if (capturedMapping.containsKey(slot)) {
                            return capturedMapping.get(slot);
                        }
                        // Check if it's a lambda parameter
                        if (paramMapping.containsKey(slot)) {
                            String paramName = paramMapping.get(slot);
                            SourceType type = typeRecoverer.recoverType(ssa);
                            return new VarRefExpr(paramName, type != null ? type : com.tonic.analysis.source.ast.type.ReferenceSourceType.OBJECT, null);
                        }
                        // Check if slot 0 is 'this'
                        if (slot == 0 && !lambdaIR.isStatic()) {
                            return new ThisExpr(null);
                        }
                    }
                    // Recursively recover
                    if (def != null) {
                        return recoverLambdaInstruction(def, lambdaIR);
                    }
                }
                // Handle constants and other values
                if (value instanceof com.tonic.analysis.ssa.value.Constant constant) {
                    return recoverConstant(constant, null);
                }
                // Fall back to standard recovery only if we have a valid instruction
                if (value instanceof com.tonic.analysis.ssa.value.SSAValue ssaVal && ssaVal.getDefinition() != null) {
                    return recover(ssaVal.getDefinition());
                }
                // Last resort: generate a placeholder variable reference
                return new VarRefExpr("v" + System.identityHashCode(value),
                    com.tonic.analysis.source.ast.type.ReferenceSourceType.OBJECT, null);
            }

            private Expression recoverLambdaInstruction(IRInstruction instr, IRMethod lambdaIR) {
                if (instr == null) return null;

                if (instr instanceof InvokeInstruction invoke) {
                    // Recover method call with mapped arguments
                    java.util.List<Expression> args = new java.util.ArrayList<>();
                    int start = invoke.getInvokeType() == InvokeType.STATIC ? 0 : 1;

                    Expression receiver = null;
                    if (invoke.getInvokeType() != InvokeType.STATIC && !invoke.getArguments().isEmpty()) {
                        receiver = recoverLambdaOperand(invoke.getArguments().get(0), lambdaIR);
                        // Don't show 'this' receiver
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

                if (instr instanceof GetFieldInstruction getField) {
                    Expression obj = getField.isStatic() ? null :
                        recoverLambdaOperand(getField.getObjectRef(), lambdaIR);
                    if (obj instanceof ThisExpr) obj = null;
                    SourceType type = typeRecoverer.recoverType(getField.getResult());
                    return new FieldAccessExpr(obj, getField.getName(), getField.getOwner(), getField.isStatic(), type);
                }

                if (instr instanceof BinaryOpInstruction binOp) {
                    Expression left = recoverLambdaOperand(binOp.getLeft(), lambdaIR);
                    Expression right = recoverLambdaOperand(binOp.getRight(), lambdaIR);
                    BinaryOperator op = OperatorMapper.mapBinaryOp(binOp.getOp());
                    SourceType type = typeRecoverer.recoverType(binOp.getResult());
                    return new BinaryExpr(op, left, right, type);
                }

                if (instr instanceof ConstantInstruction constInstr) {
                    return recoverConstant(constInstr.getConstant(), null);
                }

                // For other instructions, try standard recovery
                return recover(instr);
            }
        }

        /**
         * Generates a fallback lambda when bootstrap info is not available.
         */
        private Expression generateFallbackLambda(InvokeInstruction instr, SourceType returnType) {
            String methodName = instr.getName();
            java.util.List<LambdaParameter> params = generateLambdaParameters(null, methodName);

            // For fallback, generate empty block for void or null for non-void
            // Check if likely void based on method name
            boolean likelyVoid = "uncaughtException".equals(methodName) ||
                                 "hierarchyChanged".equals(methodName) ||
                                 "run".equals(methodName) ||
                                 "accept".equals(methodName);

            if (likelyVoid) {
                // Generate an empty block statement
                com.tonic.analysis.source.ast.stmt.BlockStmt emptyBlock =
                    new com.tonic.analysis.source.ast.stmt.BlockStmt(java.util.Collections.emptyList());
                return new LambdaExpr(params, emptyBlock, returnType);
            } else {
                // For non-void, use null
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
            // First check if a variable name has been set for this SSA value
            // (e.g., by registerExceptionVariables for catch parameters)
            String name = context.getVariableName(instr.getResult());
            if (name == null) {
                name = "local" + localIndex;
            }
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
        public Expression visitNewArray(NewArrayInstruction instr) {
            // Recover the element type and dimensions
            SourceType elementType = SourceType.fromIRType(instr.getElementType());
            java.util.List<Expression> dims = new java.util.ArrayList<>();
            for (com.tonic.analysis.ssa.value.Value dimValue : instr.getDimensions()) {
                dims.add(recoverOperand(dimValue));
            }
            return new NewArrayExpr(elementType, dims);
        }

        @Override
        public Expression visitArrayLength(ArrayLengthInstruction instr) {
            // array.length - use "[]" as pseudo owner for arrays
            Expression array = recoverOperand(instr.getArray());
            SourceType type = com.tonic.analysis.source.ast.type.PrimitiveSourceType.INT;
            return new FieldAccessExpr(array, "length", "[]", false, type);
        }

        @Override
        public Expression visitInstanceOf(InstanceOfInstruction instr) {
            // expr instanceof Type
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
