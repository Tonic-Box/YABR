package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.ImportDecl;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.FieldAccessExpr;
import com.tonic.analysis.source.ast.expr.MethodCallExpr;
import com.tonic.analysis.source.ast.expr.SuperExpr;
import com.tonic.analysis.source.ast.expr.ThisExpr;
import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.SwitchStmt;
import com.tonic.analysis.source.ast.stmt.SynchronizedStmt;
import com.tonic.analysis.source.ast.stmt.TryCatchStmt;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.analysis.ssa.ir.NewArrayInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.lift.PhiInserter;
import com.tonic.analysis.ssa.lift.VariableRenamer;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Main facade for lowering AST back to IR.
 * Converts source-level AST statements into SSA IR that can be lowered to bytecode.
 */
public class ASTLowerer {

    private final ConstPool constPool;
    private final ClassPool classPool;
    private ClassDecl currentClassDecl;
    private List<ImportDecl> imports = new ArrayList<>();

    /** Synthetic lambda methods produced by lowering, awaiting materialization into the class. */
    private final List<SyntheticLambdaMethod> pendingLambdas = new ArrayList<>();

    /** Synthetic array-constructor methods produced by lowering, awaiting materialization. */
    private final List<SyntheticArrayConstructor> pendingArrayConstructors = new ArrayList<>();

    public ASTLowerer(ConstPool constPool, ClassPool classPool) {
        this.constPool = constPool;
        this.classPool = classPool;
    }

    public void setCurrentClassDecl(ClassDecl currentClassDecl) {
        this.currentClassDecl = currentClassDecl;
    }

    public void setImports(List<ImportDecl> imports) {
        this.imports = imports;
    }

    /**
     * Lowers an AST method body to a new IRMethod.
     *
     * @param body the method body as BlockStmt
     * @param methodName the method name
     * @param ownerClass the owning class (internal name)
     * @param isStatic whether the method is static
     * @param parameters list of parameter types
     * @param returnType the return type
     * @return the generated IRMethod
     */
    public IRMethod lower(BlockStmt body, String methodName, String ownerClass,
                          boolean isStatic, List<SourceType> parameters,
                          SourceType returnType) {

        TypeResolver typeResolver = new TypeResolver(classPool, ownerClass);
        typeResolver.setCurrentClassDecl(currentClassDecl);
        typeResolver.setImports(imports);

        String superClassName = resolveSuperClassName(typeResolver);
        if ("<init>".equals(methodName)) {
            ensureConstructorChainCall(body, superClassName);
        }

        String descriptor = buildDescriptor(parameters, returnType, typeResolver);
        IRMethod irMethod = new IRMethod(ownerClass, methodName, descriptor, isStatic);

        LoweringContext ctx = new LoweringContext(irMethod, constPool, typeResolver);
        ctx.setOwnerClass(ownerClass);
        ctx.setCurrentMethodName(methodName);
        ctx.setSuperClassName(superClassName);

        IRBlock entryBlock = ctx.createBlock();
        irMethod.setEntryBlock(entryBlock);
        ctx.setCurrentBlock(entryBlock);

        if (!isStatic) {
            IRType thisType = new ReferenceType(ownerClass);
            SSAValue thisVal = ctx.newValue(thisType);
            irMethod.addParameter(thisVal);
            ctx.setVariable("this", thisVal);
        }

        for (int i = 0; i < parameters.size(); i++) {
            IRType paramType = resolvedParamType(parameters.get(i), typeResolver);
            SSAValue paramVal = ctx.newValue(paramType);
            irMethod.addParameter(paramVal);
            ctx.setVariable("arg" + i, paramVal);
        }

        ExpressionLowerer exprLowerer = new ExpressionLowerer(ctx);
        StatementLowerer stmtLowerer = new StatementLowerer(ctx, exprLowerer);

        stmtLowerer.lower(body);

        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new ReturnInstruction());
        }

        drainSynthetics(ctx);
        return irMethod;
    }

    public IRMethod lower(MethodDecl methodDecl, String ownerClass) {
        BlockStmt body = methodDecl.getBody();
        if (body == null) {
            throw new LoweringException("Cannot lower abstract method: " + methodDecl.getName());
        }
        new com.tonic.analysis.source.ast.transform.PatternInstanceOfDesugar().transform(body);
        new com.tonic.analysis.source.ast.transform.PatternSwitchDesugar(classPool).transform(body);
        new com.tonic.analysis.source.ast.transform.SwitchExpressionDesugar().transform(body);

        List<ParameterDecl> paramDecls = methodDecl.getParameters();
        List<SourceType> parameters = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();
        for (ParameterDecl p : paramDecls) {
            parameters.add(p.getType());
            paramNames.add(p.getName());
        }

        SourceType returnType = methodDecl.getReturnType();
        boolean isStatic = methodDecl.isStatic();
        String methodName = methodDecl.getName();

        TypeResolver typeResolver = new TypeResolver(classPool, ownerClass);
        typeResolver.setCurrentClassDecl(currentClassDecl);
        typeResolver.setImports(imports);

        String superClassName = resolveSuperClassName(typeResolver);
        if ("<init>".equals(methodName)) {
            ensureConstructorChainCall(body, superClassName);
        }

        String descriptor = buildDescriptor(parameters, returnType, typeResolver);
        IRMethod irMethod = new IRMethod(ownerClass, methodName, descriptor, isStatic);

        LoweringContext ctx = new LoweringContext(irMethod, constPool, typeResolver);
        ctx.setOwnerClass(ownerClass);
        ctx.setCurrentMethodName(methodName);
        ctx.setSuperClassName(superClassName);

        // Use the slot-based form + real SSA construction not only for loops but for any branch
        // merge (if/else, switch): the direct-value path inserts no phi at a merge, so a variable
        // assigned in branches and read afterward would wrongly take the last branch's value.
        boolean hasLoops = containsLoops(body) || containsBranches(body);
        if (hasLoops) {
            ctx.setEmitLocalInstructions(true);
            int paramSlotCount = isStatic ? 0 : 1;
            for (SourceType param : parameters) {
                paramSlotCount++;
                if (param.toIRType().isTwoSlot()) {
                    paramSlotCount++;
                }
            }
            ctx.initializeLocalSlots(paramSlotCount);
        }

        IRBlock entryBlock = ctx.createBlock();
        irMethod.setEntryBlock(entryBlock);
        ctx.setCurrentBlock(entryBlock);

        int paramSlot = 0;
        if (!isStatic) {
            IRType thisType = new ReferenceType(ownerClass);
            SSAValue thisVal = ctx.newValue(thisType);
            irMethod.addParameter(thisVal);
            ctx.declareLocal("this", thisType, true);
            if (hasLoops) {
                ctx.registerParameter("this", paramSlot, thisVal);
            } else {
                ctx.setVariable("this", thisVal);
            }
            paramSlot++;
        }

        for (int i = 0; i < parameters.size(); i++) {
            IRType paramType = resolvedParamType(parameters.get(i), typeResolver);
            SSAValue paramVal = ctx.newValue(paramType);
            irMethod.addParameter(paramVal);
            ctx.declareLocal(paramNames.get(i), paramType, true);
            if (hasLoops) {
                ctx.registerParameter(paramNames.get(i), paramSlot, paramVal);
            } else {
                ctx.setVariable(paramNames.get(i), paramVal);
            }
            paramSlot++;
            if (paramType.isTwoSlot()) {
                paramSlot++;
            }
        }

        ExpressionLowerer exprLowerer = new ExpressionLowerer(ctx);
        StatementLowerer stmtLowerer = new StatementLowerer(ctx, exprLowerer);

        stmtLowerer.lower(body);

        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new ReturnInstruction());
        }

        if (hasLoops) {
            constructSSAForm(irMethod);
        }

        drainSynthetics(ctx);
        return irMethod;
    }

    /**
     * Internal name of the lowered class's superclass, or {@code java/lang/Object} when none is declared
     * (an implicit-Object class). Lets {@code super(...)}/{@code super.x} resolve to the real superclass
     * instead of always defaulting to Object.
     */
    private String resolveSuperClassName(TypeResolver typeResolver) {
        SourceType superType = currentClassDecl != null ? currentClassDecl.getSuperclass() : null;
        if (superType == null) {
            return "java/lang/Object";
        }
        String descriptor = typeResolver.descriptorOf(superType);
        if (descriptor != null && descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1);
        }
        return "java/lang/Object";
    }

    /**
     * Ensures a constructor body begins with a {@code super(...)}/{@code this(...)} chain call. The
     * decompiler strips the implicit no-arg {@code super()}, so a body that lacks an explicit chain call
     * would lower to an unverifiable {@code <init>}; this prepends a synthetic {@code super()} targeting
     * {@code superClassName}.
     */
    private void ensureConstructorChainCall(BlockStmt body, String superClassName) {
        List<Statement> statements = body.getStatements();
        // javac emits synthetic outer-instance / captured-variable field initializers (this$0, val$...)
        // BEFORE the super() call, and the decompiler drops the now-implicit super(). Re-inject it after
        // any such leading run - not at index 0 - so the synthetic fields keep preceding super().
        int idx = 0;
        while (idx < statements.size() && isSyntheticCaptureFieldInit(statements.get(idx))) {
            idx++;
        }
        if (idx < statements.size() && isConstructorChainCall(statements.get(idx))) {
            return;
        }
        MethodCallExpr superCall = new MethodCallExpr(
                new SuperExpr(ReferenceSourceType.OBJECT), "<init>", superClassName,
                new ArrayList<>(), false, VoidSourceType.INSTANCE);
        statements.add(idx, new ExprStmt(superCall));
    }

    /** Whether {@code stmt} is a {@code super(...)}/{@code this(...)} constructor-chain call. */
    private boolean isConstructorChainCall(Statement stmt) {
        if (!(stmt instanceof ExprStmt)) {
            return false;
        }
        Expression expr = ((ExprStmt) stmt).getExpression();
        if (!(expr instanceof MethodCallExpr)) {
            return false;
        }
        MethodCallExpr call = (MethodCallExpr) expr;
        Expression receiver = call.getReceiver();
        if (receiver instanceof SuperExpr || receiver instanceof ThisExpr) {
            return true;
        }
        return receiver == null
                && ("super".equals(call.getMethodName()) || "this".equals(call.getMethodName()));
    }

    /**
     * Whether {@code stmt} is an assignment to a javac synthetic capture field on {@code this} - the
     * enclosing-instance reference ({@code this$0}, {@code this$1}, ...) or a captured local
     * ({@code val$...}). These are emitted before super(); ordinary field assignments are not, so the
     * name pattern is what keeps normal constructors emitting super() first.
     */
    private boolean isSyntheticCaptureFieldInit(Statement stmt) {
        if (!(stmt instanceof ExprStmt)) {
            return false;
        }
        Expression expr = ((ExprStmt) stmt).getExpression();
        if (!(expr instanceof BinaryExpr)) {
            return false;
        }
        BinaryExpr assign = (BinaryExpr) expr;
        if (assign.getOperator() != BinaryOperator.ASSIGN || !(assign.getLeft() instanceof FieldAccessExpr)) {
            return false;
        }
        FieldAccessExpr field = (FieldAccessExpr) assign.getLeft();
        if (!(field.getReceiver() instanceof ThisExpr)) {
            return false;
        }
        return isSyntheticCaptureFieldName(field.getFieldName());
    }

    private static boolean isSyntheticCaptureFieldName(String name) {
        if (name == null) {
            return false;
        }
        if (name.startsWith("val$")) {
            return true;
        }
        if (name.length() > 5 && name.startsWith("this$")) {
            for (int i = 5; i < name.length(); i++) {
                if (!Character.isDigit(name.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Moves the synthetic methods registered on a finished lowering context into this lowerer's
     * pending queues, so callers can materialize them into the class after the user methods.
     */
    private void drainSynthetics(LoweringContext ctx) {
        pendingLambdas.addAll(ctx.getSyntheticMethods());
        pendingArrayConstructors.addAll(ctx.getArrayConstructors());
        ctx.clearSyntheticMethods();
        ctx.clearArrayConstructors();
    }

    /** Whether any synthetic methods are awaiting materialization. */
    public boolean hasPendingSynthetics() {
        return !pendingLambdas.isEmpty() || !pendingArrayConstructors.isEmpty();
    }

    /** Removes and returns the queued synthetic lambda methods. */
    public List<SyntheticLambdaMethod> drainPendingLambdas() {
        List<SyntheticLambdaMethod> drained = new ArrayList<>(pendingLambdas);
        pendingLambdas.clear();
        return drained;
    }

    /** Removes and returns the queued synthetic array-constructor methods. */
    public List<SyntheticArrayConstructor> drainPendingArrayConstructors() {
        List<SyntheticArrayConstructor> drained = new ArrayList<>(pendingArrayConstructors);
        pendingArrayConstructors.clear();
        return drained;
    }

    /**
     * Lowers a synthetic lambda method body into an IRMethod. Captured variables and lambda
     * parameters are registered as locals by their source names, in the synthetic descriptor's
     * parameter order. Only static synthetics (lambdas that do not capture {@code this}) are
     * supported; an instance-capturing synthetic throws so the caller can preserve the original.
     */
    public IRMethod lowerSyntheticLambda(SyntheticLambdaMethod synthetic, String ownerClass) {
        if (!synthetic.isStatic()) {
            throw new LoweringException(
                "Instance-capturing lambda synthetic not supported for emission: " + synthetic.getName());
        }

        BlockStmt body = syntheticBody(synthetic);
        IRMethod irMethod = new IRMethod(ownerClass, synthetic.getName(), synthetic.getDescriptor(), true);

        TypeResolver typeResolver = new TypeResolver(classPool, ownerClass);
        typeResolver.setCurrentClassDecl(currentClassDecl);
        typeResolver.setImports(imports);
        LoweringContext ctx = new LoweringContext(irMethod, constPool, typeResolver);
        ctx.setOwnerClass(ownerClass);
        ctx.setCurrentMethodName(synthetic.getName());

        List<SourceType> paramTypes = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();
        for (SyntheticLambdaMethod.CapturedVariable capture : synthetic.getCaptures()) {
            paramTypes.add(capture.getType());
            paramNames.add(capture.getName());
        }
        for (var param : synthetic.getParameters()) {
            paramTypes.add(param.type() != null ? param.type() : ReferenceSourceType.OBJECT);
            paramNames.add(param.name());
        }

        // Use the slot-based form + real SSA construction not only for loops but for any branch
        // merge (if/else, switch): the direct-value path inserts no phi at a merge, so a variable
        // assigned in branches and read afterward would wrongly take the last branch's value.
        boolean hasLoops = containsLoops(body) || containsBranches(body);
        if (hasLoops) {
            ctx.setEmitLocalInstructions(true);
            int paramSlotCount = 0;
            for (SourceType type : paramTypes) {
                paramSlotCount++;
                if (type.toIRType().isTwoSlot()) {
                    paramSlotCount++;
                }
            }
            ctx.initializeLocalSlots(paramSlotCount);
        }

        IRBlock entryBlock = ctx.createBlock();
        irMethod.setEntryBlock(entryBlock);
        ctx.setCurrentBlock(entryBlock);

        int paramSlot = 0;
        for (int i = 0; i < paramTypes.size(); i++) {
            IRType paramType = paramTypes.get(i).toIRType();
            SSAValue paramVal = ctx.newValue(paramType);
            irMethod.addParameter(paramVal);
            if (hasLoops) {
                ctx.registerParameter(paramNames.get(i), paramSlot, paramVal);
            } else {
                ctx.setVariable(paramNames.get(i), paramVal);
            }
            paramSlot++;
            if (paramType.isTwoSlot()) {
                paramSlot++;
            }
        }

        ExpressionLowerer exprLowerer = new ExpressionLowerer(ctx);
        StatementLowerer stmtLowerer = new StatementLowerer(ctx, exprLowerer);
        stmtLowerer.lower(body);

        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new ReturnInstruction());
        }

        if (hasLoops) {
            constructSSAForm(irMethod);
        }

        drainSynthetics(ctx);
        return irMethod;
    }

    /**
     * Lowers a synthetic array-constructor method ({@code (I)[T} returning {@code new T[arg0]}).
     */
    public IRMethod lowerSyntheticArrayConstructor(SyntheticArrayConstructor constructor, String ownerClass) {
        IRMethod irMethod = new IRMethod(ownerClass, constructor.getName(), constructor.getDescriptor(), true);

        TypeResolver typeResolver = new TypeResolver(classPool, ownerClass);
        typeResolver.setCurrentClassDecl(currentClassDecl);
        typeResolver.setImports(imports);
        LoweringContext ctx = new LoweringContext(irMethod, constPool, typeResolver);
        ctx.setOwnerClass(ownerClass);
        ctx.setCurrentMethodName(constructor.getName());

        IRBlock entryBlock = ctx.createBlock();
        irMethod.setEntryBlock(entryBlock);
        ctx.setCurrentBlock(entryBlock);

        SSAValue size = ctx.newValue(PrimitiveType.INT);
        irMethod.addParameter(size);

        String arrayDescriptor = constructor.getArrayTypeDescriptor();
        IRType arrayType = IRType.fromDescriptor(arrayDescriptor);
        IRType componentType = IRType.fromDescriptor(arrayDescriptor.substring(1));
        SSAValue array = ctx.newValue(arrayType);
        ctx.getCurrentBlock().addInstruction(new NewArrayInstruction(array, componentType, size));
        ctx.getCurrentBlock().addInstruction(new ReturnInstruction(array));

        return irMethod;
    }

    private BlockStmt syntheticBody(SyntheticLambdaMethod synthetic) {
        if (synthetic.getBody() instanceof BlockStmt) {
            return (BlockStmt) synthetic.getBody();
        }
        Expression expr = (Expression) synthetic.getBody();
        BlockStmt block = new BlockStmt();
        if (synthetic.getReturnType() == null || synthetic.getReturnType() instanceof VoidSourceType) {
            block.addStatement(new ExprStmt(expr));
        } else {
            block.addStatement(new ReturnStmt(expr));
        }
        return block;
    }

    private boolean containsLoops(BlockStmt body) {
        return new LoopDetector().visit(body);
    }

    /** True if the body contains an if/switch — control flow that can merge a variable's value. */
    private boolean containsBranches(BlockStmt body) {
        return containsBranchNode(body);
    }

    private boolean containsBranchNode(ASTNode node) {
        // try/catch and synchronized create control-flow merges (the protected/handler paths join a
        // continuation), so a variable assigned inside and read afterwards needs the slot-based SSA form
        // and a phi at the join - exactly like if/switch.
        if (node instanceof IfStmt || node instanceof SwitchStmt
                || node instanceof TryCatchStmt || node instanceof SynchronizedStmt) {
            return true;
        }
        for (ASTNode child : node.getChildren()) {
            if (containsBranchNode(child)) {
                return true;
            }
        }
        return false;
    }

    private void constructSSAForm(IRMethod irMethod) {
        DominatorTree domTree = new DominatorTree(irMethod);
        domTree.compute();

        PhiInserter phiInserter = new PhiInserter(domTree);
        phiInserter.insertPhis(irMethod);

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(irMethod);

        removeDeadPhis(irMethod);
    }

    /**
     * Removes phi functions whose result is never used, iterating to a fixpoint so a phi that becomes dead
     * once its only consumer (another dead phi) is removed is also dropped. Minimal (unpruned) SSA places a
     * phi at every dominance frontier of a definition; for a variable defined on only one path into a join
     * (e.g. an exception handler's caught-exception local, dead at the continuation) this yields a malformed
     * phi missing an entry for the other predecessor, which breaks frame generation. Such a phi is always
     * unused for valid source, so pruning dead phis here removes it without affecting live values.
     */
    private void removeDeadPhis(IRMethod irMethod) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (IRBlock block : irMethod.getBlocks()) {
                for (PhiInstruction phi : new ArrayList<>(block.getPhiInstructions())) {
                    if (!phi.getResult().getUses().isEmpty()) {
                        continue;
                    }
                    for (IRBlock pred : new ArrayList<>(phi.getIncomingBlocks())) {
                        phi.removeIncoming(pred);
                    }
                    block.removePhi(phi);
                    changed = true;
                }
            }
        }
    }

    /**
     * Convenience method to lower and replace an IRMethod's body from AST.
     *
     * @param body the new method body
     * @param irMethod the existing IRMethod
     */
    public void replaceBody(BlockStmt body, IRMethod irMethod) {
        TypeResolver typeResolver = new TypeResolver(classPool, irMethod.getOwnerClass());
        typeResolver.setCurrentClassDecl(currentClassDecl);
        typeResolver.setImports(imports);
        LoweringContext ctx = new LoweringContext(irMethod, constPool, typeResolver);

        irMethod.getBlocks().clear();

        IRBlock entryBlock = ctx.createBlock();
        irMethod.setEntryBlock(entryBlock);
        ctx.setCurrentBlock(entryBlock);

        boolean isStatic = irMethod.isStatic();
        List<SSAValue> params = irMethod.getParameters();

        if (!isStatic && !params.isEmpty()) {
            ctx.setVariable("this", params.get(0));
        }

        int paramOffset = isStatic ? 0 : 1;
        for (int i = paramOffset; i < params.size(); i++) {
            ctx.setVariable("arg" + (i - paramOffset), params.get(i));
        }

        ExpressionLowerer exprLowerer = new ExpressionLowerer(ctx);
        StatementLowerer stmtLowerer = new StatementLowerer(ctx, exprLowerer);
        stmtLowerer.lower(body);

        if (ctx.getCurrentBlock().getTerminator() == null) {
            ctx.getCurrentBlock().addInstruction(new ReturnInstruction());
        }
    }

    private String buildDescriptor(List<SourceType> parameters, SourceType returnType, TypeResolver resolver) {
        StringBuilder sb = new StringBuilder("(");
        for (SourceType param : parameters) {
            sb.append(resolver.descriptorOf(param));
        }
        sb.append(")");
        sb.append(resolver.descriptorOf(returnType));
        return sb.toString();
    }

    /** A parameter's IR type with reference names resolved to FQN (imports/same-package), via the descriptor - so the
     * SSA value and its StackMapTable frame use {@code java/awt/Frame}, not a bare {@code Frame} CONSTANT_Class. */
    private static IRType resolvedParamType(SourceType param, TypeResolver resolver) {
        return IRType.fromDescriptor(resolver.descriptorOf(param));
    }

    /**
     * Static convenience method to lower AST to IR.
     */
    public static IRMethod lowerMethod(BlockStmt body, String methodName, String ownerClass,
                                       boolean isStatic, List<SourceType> parameters,
                                       SourceType returnType, ConstPool constPool, ClassPool classPool) {
        ASTLowerer lowerer = new ASTLowerer(constPool, classPool);
        return lowerer.lower(body, methodName, ownerClass, isStatic, parameters, returnType);
    }
}
