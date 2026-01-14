package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.ImportDecl;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.lift.PhiInserter;
import com.tonic.analysis.ssa.lift.VariableRenamer;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Main facade for lowering AST back to IR.
 * Converts source-level AST statements into SSA IR that can be lowered to bytecode.
 */
@RequiredArgsConstructor
public class ASTLowerer {

    private final ConstPool constPool;
    private final ClassPool classPool;
    @Setter
    private ClassDecl currentClassDecl;
    @Setter
    private List<ImportDecl> imports = new ArrayList<>();

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

        TypeResolver typeResolver = new TypeResolver(classPool, ownerClass);
        typeResolver.setCurrentClassDecl(currentClassDecl);
        typeResolver.setImports(imports);
        LoweringContext ctx = new LoweringContext(irMethod, constPool, typeResolver);
        ctx.setOwnerClass(ownerClass);
        ctx.setCurrentMethodName(methodName);

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

    public IRMethod lower(MethodDecl methodDecl, String ownerClass) {
        BlockStmt body = methodDecl.getBody();
        if (body == null) {
            throw new LoweringException("Cannot lower abstract method: " + methodDecl.getName());
        }

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

        String descriptor = buildDescriptor(parameters, returnType);
        IRMethod irMethod = new IRMethod(ownerClass, methodName, descriptor, isStatic);

        TypeResolver typeResolver = new TypeResolver(classPool, ownerClass);
        typeResolver.setCurrentClassDecl(currentClassDecl);
        typeResolver.setImports(imports);
        LoweringContext ctx = new LoweringContext(irMethod, constPool, typeResolver);
        ctx.setOwnerClass(ownerClass);
        ctx.setCurrentMethodName(methodName);

        boolean hasLoops = containsLoops(body);
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
            IRType thisType = new com.tonic.analysis.ssa.type.ReferenceType(ownerClass);
            SSAValue thisVal = ctx.newValue(thisType);
            irMethod.addParameter(thisVal);
            if (hasLoops) {
                ctx.registerParameter("this", paramSlot, thisVal);
            } else {
                ctx.setVariable("this", thisVal);
            }
            paramSlot++;
        }

        for (int i = 0; i < parameters.size(); i++) {
            IRType paramType = parameters.get(i).toIRType();
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

        return irMethod;
    }

    private boolean containsLoops(BlockStmt body) {
        return new LoopDetector().visit(body);
    }

    private void constructSSAForm(IRMethod irMethod) {
        DominatorTree domTree = new DominatorTree(irMethod);
        domTree.compute();

        PhiInserter phiInserter = new PhiInserter(domTree);
        phiInserter.insertPhis(irMethod);

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(irMethod);
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
                                       SourceType returnType, ConstPool constPool, ClassPool classPool) {
        ASTLowerer lowerer = new ASTLowerer(constPool, classPool);
        return lowerer.lower(body, methodName, ownerClass, isStatic, parameters, returnType);
    }
}
