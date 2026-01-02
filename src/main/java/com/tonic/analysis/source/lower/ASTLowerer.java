package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.VoidType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.util.List;

/**
 * Main facade for lowering AST back to IR.
 * Converts source-level AST statements into SSA IR that can be lowered to bytecode.
 */
public class ASTLowerer {

    private final ConstPool constPool;

    /**
     * Creates a new AST lowerer.
     *
     * @param constPool the constant pool for references
     */
    public ASTLowerer(ConstPool constPool) {
        this.constPool = constPool;
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

        String descriptor = buildDescriptor(parameters, returnType);
        IRMethod irMethod = new IRMethod(ownerClass, methodName, descriptor, isStatic);

        LoweringContext ctx = new LoweringContext(irMethod, constPool);

        IRBlock entryBlock = ctx.createBlock();
        irMethod.setEntryBlock(entryBlock);
        ctx.setCurrentBlock(entryBlock);

        if (!isStatic) {
            IRType thisType = new com.tonic.analysis.ssa.type.ReferenceType(ownerClass);
            SSAValue thisVal = ctx.newValue(thisType);
            irMethod.addParameter(thisVal);
            ctx.setVariable("this", thisVal);
        }

        for (int i = 0; i < parameters.size(); i++) {
            IRType paramType = parameters.get(i).toIRType();
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

        return irMethod;
    }

    /**
     * Lowers an AST method body into an existing IRMethod (replaces contents).
     *
     * @param body the method body as BlockStmt
     * @param irMethod the target IRMethod to populate
     * @param method the source MethodEntry for parameter info
     */
    public void lower(BlockStmt body, IRMethod irMethod, MethodEntry method) {
        irMethod.getBlocks().clear();

        LoweringContext ctx = new LoweringContext(irMethod, constPool);

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

    /**
     * Convenience method to lower and replace an IRMethod's body from AST.
     *
     * @param body the new method body
     * @param irMethod the existing IRMethod
     */
    public void replaceBody(BlockStmt body, IRMethod irMethod) {
        LoweringContext ctx = new LoweringContext(irMethod, constPool);

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

    private String buildDescriptor(List<SourceType> parameters, SourceType returnType) {
        StringBuilder sb = new StringBuilder("(");
        for (SourceType param : parameters) {
            sb.append(param.toIRType().getDescriptor());
        }
        sb.append(")");
        sb.append(returnType.toIRType().getDescriptor());
        return sb.toString();
    }

    /**
     * Static convenience method to lower AST to IR.
     */
    public static IRMethod lowerMethod(BlockStmt body, String methodName, String ownerClass,
                                       boolean isStatic, List<SourceType> parameters,
                                       SourceType returnType, ConstPool constPool) {
        ASTLowerer lowerer = new ASTLowerer(constPool);
        return lowerer.lower(body, methodName, ownerClass, isStatic, parameters, returnType);
    }
}
