package com.tonic.analysis.source.emit;

import com.tonic.analysis.source.ast.decl.*;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.utill.ClassNameUtil;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Emits Java source code from AST nodes.
 */
public class SourceEmitter implements SourceVisitor<Void> {

    private final IndentingWriter writer;
    private final SourceEmitterConfig config;
    private final IdentifierNormalizer normalizer;
    @Getter
    private final Set<String> usedTypes = new HashSet<>();
    private String currentClassName;

    public SourceEmitter(IndentingWriter writer) {
        this(writer, SourceEmitterConfig.defaults());
    }

    public SourceEmitter(IndentingWriter writer, SourceEmitterConfig config) {
        this.writer = writer;
        this.config = config;
        this.normalizer = new IdentifierNormalizer(config.getIdentifierMode());
    }

    public void clearUsedTypes() {
        usedTypes.clear();
    }

    public void setCurrentClassName(String className) {
        this.currentClassName = className;
    }

    private void recordTypeUsage(String internalName) {
        if (internalName != null && !internalName.isEmpty() && !internalName.startsWith("[")) {
            usedTypes.add(internalName);
        }
    }

    /**
     * Emits a statement to a string.
     */
    public static String emit(Statement stmt) {
        return emit(stmt, SourceEmitterConfig.defaults());
    }

    /**
     * Emits a statement to a string with configuration.
     */
    public static String emit(Statement stmt, SourceEmitterConfig config) {
        IndentingWriter writer = IndentingWriter.toStringWriter();
        SourceEmitter emitter = new SourceEmitter(writer, config);
        stmt.accept(emitter);
        return writer.toString();
    }

    /**
     * Emits an expression to a string.
     */
    public static String emit(Expression expr) {
        IndentingWriter writer = IndentingWriter.toStringWriter();
        SourceEmitter emitter = new SourceEmitter(writer);
        expr.accept(emitter);
        return writer.toString();
    }

    /**
     * Emits a compilation unit to a string.
     */
    public static String emit(CompilationUnit cu) {
        return emit(cu, SourceEmitterConfig.defaults());
    }

    /**
     * Emits a compilation unit to a string with configuration.
     */
    public static String emit(CompilationUnit cu, SourceEmitterConfig config) {
        IndentingWriter writer = IndentingWriter.toStringWriter();
        SourceEmitter emitter = new SourceEmitter(writer, config);
        emitter.visitCompilationUnit(cu);
        return writer.toString();
    }

    @Override
    public Void visitCompilationUnit(CompilationUnit cu) {
        if (cu.hasPackage()) {
            writer.write("package ");
            writer.write(cu.getPackageName());
            writer.writeLine(";");
            writer.newLine();
        }

        for (ImportDecl imp : cu.getImports()) {
            visitImportDecl(imp);
        }
        if (!cu.getImports().isEmpty()) {
            writer.newLine();
        }

        for (TypeDecl type : cu.getTypes()) {
            if (type instanceof ClassDecl) {
                visitClassDecl((ClassDecl) type);
            } else if (type instanceof InterfaceDecl) {
                visitInterfaceDecl((InterfaceDecl) type);
            } else if (type instanceof EnumDecl) {
                visitEnumDecl((EnumDecl) type);
            }
        }

        return null;
    }

    @Override
    public Void visitImportDecl(ImportDecl decl) {
        writer.write("import ");
        if (decl.isStatic()) {
            writer.write("static ");
        }
        writer.write(decl.getName());
        if (decl.isWildcard()) {
            writer.write(".*");
        }
        writer.writeLine(";");
        return null;
    }

    @Override
    public Void visitClassDecl(ClassDecl decl) {
        emitAnnotations(decl.getAnnotations());
        emitModifiers(decl.getModifiers());
        writer.write("class ");
        writer.write(decl.getName());
        emitTypeParameters(decl.getTypeParameters());

        if (decl.getSuperclass() != null) {
            writer.write(" extends ");
            writer.write(decl.getSuperclass().toJavaSource());
        }

        if (!decl.getInterfaces().isEmpty()) {
            writer.write(" implements ");
            emitTypeList(decl.getInterfaces());
        }

        writer.writeLine(" {");
        writer.indent();
        writer.newLine();

        for (FieldDecl field : decl.getFields()) {
            visitFieldDecl(field);
        }
        if (!decl.getFields().isEmpty()) {
            writer.newLine();
        }

        for (ConstructorDecl ctor : decl.getConstructors()) {
            visitConstructorDecl(ctor);
            writer.newLine();
        }

        for (MethodDecl method : decl.getMethods()) {
            visitMethodDecl(method);
            writer.newLine();
        }

        for (TypeDecl inner : decl.getInnerTypes()) {
            if (inner instanceof ClassDecl) {
                visitClassDecl((ClassDecl) inner);
            } else if (inner instanceof InterfaceDecl) {
                visitInterfaceDecl((InterfaceDecl) inner);
            } else if (inner instanceof EnumDecl) {
                visitEnumDecl((EnumDecl) inner);
            }
        }

        writer.dedent();
        writer.writeLine("}");
        return null;
    }

    @Override
    public Void visitInterfaceDecl(InterfaceDecl decl) {
        emitAnnotations(decl.getAnnotations());
        emitModifiers(decl.getModifiers());
        writer.write("interface ");
        writer.write(decl.getName());
        emitTypeParameters(decl.getTypeParameters());

        if (!decl.getExtendedInterfaces().isEmpty()) {
            writer.write(" extends ");
            emitTypeList(decl.getExtendedInterfaces());
        }

        writer.writeLine(" {");
        writer.indent();
        writer.newLine();

        for (FieldDecl field : decl.getFields()) {
            visitFieldDecl(field);
        }
        if (!decl.getFields().isEmpty()) {
            writer.newLine();
        }

        for (MethodDecl method : decl.getMethods()) {
            visitMethodDecl(method);
            writer.newLine();
        }

        for (TypeDecl inner : decl.getInnerTypes()) {
            if (inner instanceof ClassDecl) {
                visitClassDecl((ClassDecl) inner);
            } else if (inner instanceof InterfaceDecl) {
                visitInterfaceDecl((InterfaceDecl) inner);
            }
        }

        writer.dedent();
        writer.writeLine("}");
        return null;
    }

    @Override
    public Void visitEnumDecl(EnumDecl decl) {
        emitAnnotations(decl.getAnnotations());
        emitModifiers(decl.getModifiers());
        writer.write("enum ");
        writer.write(decl.getName());

        if (!decl.getInterfaces().isEmpty()) {
            writer.write(" implements ");
            emitTypeList(decl.getInterfaces());
        }

        writer.writeLine(" {");
        writer.indent();

        List<EnumConstantDecl> constants = decl.getConstants();
        for (int i = 0; i < constants.size(); i++) {
            visitEnumConstantDecl(constants.get(i));
            if (i < constants.size() - 1) {
                writer.writeLine(",");
            } else if (!decl.getFields().isEmpty() || !decl.getMethods().isEmpty() || !decl.getConstructors().isEmpty()) {
                writer.writeLine(";");
            } else {
                writer.newLine();
            }
        }

        if (!decl.getFields().isEmpty() || !decl.getMethods().isEmpty() || !decl.getConstructors().isEmpty()) {
            writer.newLine();

            for (FieldDecl field : decl.getFields()) {
                visitFieldDecl(field);
            }
            if (!decl.getFields().isEmpty()) {
                writer.newLine();
            }

            for (ConstructorDecl ctor : decl.getConstructors()) {
                visitConstructorDecl(ctor);
                writer.newLine();
            }

            for (MethodDecl method : decl.getMethods()) {
                visitMethodDecl(method);
                writer.newLine();
            }
        }

        writer.dedent();
        writer.writeLine("}");
        return null;
    }

    @Override
    public Void visitEnumConstantDecl(EnumConstantDecl decl) {
        emitAnnotations(decl.getAnnotations());
        writer.write(decl.getName());

        if (!decl.getArguments().isEmpty()) {
            writer.write("(");
            emitExpressionList(decl.getArguments());
            writer.write(")");
        }

        if (decl.hasBody()) {
            writer.writeLine(" {");
            writer.indent();
            for (FieldDecl field : decl.getFields()) {
                visitFieldDecl(field);
            }
            for (MethodDecl method : decl.getMethods()) {
                visitMethodDecl(method);
            }
            writer.dedent();
            writer.write("}");
        }

        return null;
    }

    @Override
    public Void visitMethodDecl(MethodDecl decl) {
        emitAnnotations(decl.getAnnotations());
        emitModifiers(decl.getModifiers());
        emitTypeParameters(decl.getTypeParameters());

        decl.getReturnType().accept(this);
        writer.write(" ");
        writer.write(decl.getName());
        writer.write("(");
        emitParameters(decl.getParameters());
        writer.write(")");

        if (!decl.getThrowsTypes().isEmpty()) {
            writer.write(" throws ");
            emitTypeList(decl.getThrowsTypes());
        }

        if (decl.getBody() != null) {
            writer.write(" ");
            decl.getBody().accept(this);
        } else {
            writer.writeLine(";");
        }

        return null;
    }

    @Override
    public Void visitConstructorDecl(ConstructorDecl decl) {
        emitAnnotations(decl.getAnnotations());
        emitModifiers(decl.getModifiers());
        emitTypeParameters(decl.getTypeParameters());

        writer.write(decl.getName());
        writer.write("(");
        emitParameters(decl.getParameters());
        writer.write(")");

        if (!decl.getThrowsTypes().isEmpty()) {
            writer.write(" throws ");
            emitTypeList(decl.getThrowsTypes());
        }

        writer.write(" ");
        decl.getBody().accept(this);

        return null;
    }

    @Override
    public Void visitFieldDecl(FieldDecl decl) {
        emitAnnotations(decl.getAnnotations());
        emitModifiers(decl.getModifiers());
        decl.getType().accept(this);
        writer.write(" ");
        writer.write(decl.getName());

        if (decl.getInitializer() != null) {
            writer.write(" = ");
            decl.getInitializer().accept(this);
        }

        writer.writeLine(";");
        return null;
    }

    @Override
    public Void visitParameterDecl(ParameterDecl decl) {
        emitAnnotations(decl.getAnnotations());
        if (decl.isFinal()) {
            writer.write("final ");
        }
        decl.getType().accept(this);
        if (decl.isVarArgs()) {
            writer.write("...");
        }
        writer.write(" ");
        writer.write(decl.getName());
        return null;
    }

    @Override
    public Void visitAnnotationExpr(AnnotationExpr expr) {
        writer.write("@");
        writer.write(expr.getAnnotationType().toJavaSource());

        if (!expr.getValues().isEmpty()) {
            writer.write("(");
            List<AnnotationValue> values = expr.getValues();
            if (values.size() == 1 && "value".equals(values.get(0).getName())) {
                values.get(0).getValue().accept(this);
            } else {
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) writer.write(", ");
                    AnnotationValue av = values.get(i);
                    writer.write(av.getName());
                    writer.write(" = ");
                    av.getValue().accept(this);
                }
            }
            writer.write(")");
        }

        return null;
    }

    private void emitAnnotations(List<AnnotationExpr> annotations) {
        for (AnnotationExpr ann : annotations) {
            visitAnnotationExpr(ann);
            writer.newLine();
        }
    }

    private void emitModifiers(Set<Modifier> modifiers) {
        for (Modifier mod : Modifier.values()) {
            if (modifiers.contains(mod)) {
                writer.write(mod.getKeyword());
                writer.write(" ");
            }
        }
    }

    private void emitTypeParameters(List<SourceType> typeParams) {
        if (!typeParams.isEmpty()) {
            writer.write("<");
            for (int i = 0; i < typeParams.size(); i++) {
                if (i > 0) writer.write(", ");
                writer.write(typeParams.get(i).toJavaSource());
            }
            writer.write(">");
        }
    }

    private void emitTypeList(List<SourceType> types) {
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) writer.write(", ");
            writer.write(types.get(i).toJavaSource());
        }
    }

    private void emitParameters(List<ParameterDecl> params) {
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) writer.write(", ");
            visitParameterDecl(params.get(i));
        }
    }

    @Override
    public Void visitBlock(BlockStmt stmt) {
        List<Statement> stmts = stmt.getStatements();
        if (stmts.isEmpty()) {
            writer.writeLine("{}");
            return null;
        }
        writer.writeLine("{");
        writer.indent();
        for (Statement s : stmts) {
            if (s instanceof BlockStmt) {
                BlockStmt nestedBlock = (BlockStmt) s;
                for (Statement nested : nestedBlock.getStatements()) {
                    nested.accept(this);
                }
            } else {
                s.accept(this);
            }
        }
        writer.dedent();
        writer.writeLine("}");
        return null;
    }

    @Override
    public Void visitIf(IfStmt stmt) {
        boolean thenEmpty = isEmptyBlock(stmt.getThenBranch());
        boolean elseEmpty = stmt.getElseBranch() == null || isEmptyBlock(stmt.getElseBranch());
        if (thenEmpty && elseEmpty) {
            return null;
        }

        writer.write("if (");
        stmt.getCondition().accept(this);
        writer.write(") ");

        if (stmt.getThenBranch() instanceof BlockStmt) {
            stmt.getThenBranch().accept(this);
        } else if (config.isAlwaysUseBraces()) {
            writer.writeLine("{");
            writer.indent();
            stmt.getThenBranch().accept(this);
            writer.dedent();
            writer.writeLine("}");
        } else {
            writer.newLine();
            writer.indent();
            stmt.getThenBranch().accept(this);
            writer.dedent();
        }

        if (stmt.getElseBranch() != null && !isEmptyBlock(stmt.getElseBranch())) {
            writer.write("else ");
            if (stmt.getElseBranch() instanceof IfStmt) {
                stmt.getElseBranch().accept(this);
            } else if (stmt.getElseBranch() instanceof BlockStmt) {
                stmt.getElseBranch().accept(this);
            } else if (config.isAlwaysUseBraces()) {
                writer.writeLine("{");
                writer.indent();
                stmt.getElseBranch().accept(this);
                writer.dedent();
                writer.writeLine("}");
            } else {
                writer.newLine();
                writer.indent();
                stmt.getElseBranch().accept(this);
                writer.dedent();
            }
        }
        return null;
    }

    @Override
    public Void visitWhile(WhileStmt stmt) {
        writer.write("while (");
        stmt.getCondition().accept(this);
        writer.write(") ");
        emitBody(stmt.getBody());
        return null;
    }

    @Override
    public Void visitDoWhile(DoWhileStmt stmt) {
        writer.write("do ");
        emitBody(stmt.getBody());
        writer.write("while (");
        stmt.getCondition().accept(this);
        writer.writeLine(");");
        return null;
    }

    @Override
    public Void visitFor(ForStmt stmt) {
        writer.write("for (");

        List<Statement> init = stmt.getInit();
        if (!init.isEmpty()) {
            for (int i = 0; i < init.size(); i++) {
                if (i > 0) writer.write(", ");
                Statement s = init.get(i);
                if (s instanceof VarDeclStmt) {
                    VarDeclStmt vds = (VarDeclStmt) s;
                    emitVarDeclNoSemicolon(vds);
                } else if (s instanceof ExprStmt) {
                    ExprStmt es = (ExprStmt) s;
                    es.getExpression().accept(this);
                }
            }
        }
        writer.write("; ");

        if (stmt.getCondition() != null) {
            stmt.getCondition().accept(this);
        }
        writer.write("; ");

        List<Expression> update = stmt.getUpdate();
        for (int i = 0; i < update.size(); i++) {
            if (i > 0) writer.write(", ");
            update.get(i).accept(this);
        }
        writer.write(") ");

        emitBody(stmt.getBody());
        return null;
    }

    @Override
    public Void visitForEach(ForEachStmt stmt) {
        writer.write("for (");
        VarDeclStmt var = stmt.getVariable();
        var.getType().accept(this);
        writer.write(" ");
        writer.write(var.getName());
        writer.write(" : ");
        stmt.getIterable().accept(this);
        writer.write(") ");
        emitBody(stmt.getBody());
        return null;
    }

    @Override
    public Void visitSwitch(SwitchStmt stmt) {
        writer.write("switch (");
        stmt.getSelector().accept(this);
        writer.writeLine(") {");
        writer.indent();

        List<SwitchCase> cases = stmt.getCases();
        for (SwitchCase switchCase : cases) {
            if (switchCase.isDefault()) {
                writer.writeLine("default:");
            } else if (switchCase.hasExpressionLabels()) {
                for (Expression label : switchCase.expressionLabels()) {
                    writer.write("case ");
                    label.accept(this);
                    writer.writeLine(":");
                }
            } else {
                for (Integer label : switchCase.labels()) {
                    writer.writeLine("case " + label + ":");
                }
            }
            writer.indent();
            List<Statement> stmts = switchCase.statements();
            for (Statement s : stmts) {
                s.accept(this);
            }
            if (!stmts.isEmpty() && needsBreak(stmts)) {
                writer.writeLine("break;");
            }
            writer.dedent();
        }

        writer.dedent();
        writer.writeLine("}");
        return null;
    }

    private boolean needsBreak(List<Statement> statements) {
        if (statements.isEmpty()) {
            return false;
        }
        Statement last = statements.get(statements.size() - 1);
        if (last instanceof ReturnStmt || last instanceof ThrowStmt || last instanceof BreakStmt) {
            return false;
        }
        if (last instanceof BlockStmt) {
            BlockStmt block = (BlockStmt) last;
            if (!block.getStatements().isEmpty()) {
                return needsBreak(block.getStatements());
            }
        }
        return true;
    }

    @Override
    public Void visitTryCatch(TryCatchStmt stmt) {
        writer.write("try ");

        if (!stmt.getResources().isEmpty()) {
            writer.write("(");
            List<Expression> resources = stmt.getResources();
            for (int i = 0; i < resources.size(); i++) {
                if (i > 0) writer.write("; ");
                resources.get(i).accept(this);
            }
            writer.write(") ");
        }

        stmt.getTryBlock().accept(this);

        for (CatchClause catchClause : stmt.getCatches()) {
            writer.write("catch (");
            List<SourceType> types = catchClause.exceptionTypes();
            for (int i = 0; i < types.size(); i++) {
                if (i > 0) writer.write(" | ");
                types.get(i).accept(this);
            }
            writer.write(" ");
            writer.write(catchClause.variableName());
            writer.write(") ");
            catchClause.body().accept(this);
        }

        if (stmt.getFinallyBlock() != null) {
            writer.write("finally ");
            stmt.getFinallyBlock().accept(this);
        }

        return null;
    }

    @Override
    public Void visitReturn(ReturnStmt stmt) {
        if (stmt.getValue() == null) {
            writer.writeLine("return;");
        } else {
            writer.write("return ");
            Expression value = stmt.getValue();
            SourceType retType = stmt.getMethodReturnType();
            if (value instanceof LiteralExpr && retType != null) {
                LiteralExpr lit = (LiteralExpr) value;
                Object val = lit.getValue();
                if (val instanceof Integer && retType == PrimitiveSourceType.BOOLEAN) {
                    int intVal = (Integer) val;
                    writer.write(intVal != 0 ? "true" : "false");
                    writer.writeLine(";");
                    return null;
                }
                if (val instanceof Integer && retType == PrimitiveSourceType.CHAR) {
                    int intVal = (Integer) val;
                    writer.write("'" + escapeChar((char) intVal) + "'");
                    writer.writeLine(";");
                    return null;
                }
            }
            value.accept(this);
            writer.writeLine(";");
        }
        return null;
    }

    @Override
    public Void visitThrow(ThrowStmt stmt) {
        writer.write("throw ");
        stmt.getException().accept(this);
        writer.writeLine(";");
        return null;
    }

    @Override
    public Void visitVarDecl(VarDeclStmt stmt) {
        emitVarDeclNoSemicolon(stmt);
        writer.writeLine(";");
        return null;
    }

    private void emitVarDeclNoSemicolon(VarDeclStmt stmt) {
        if (stmt.isFinal()) {
            writer.write("final ");
        }

        if (stmt.isUseVarKeyword() && config.isUseVarKeyword()) {
            writer.write("var ");
        } else {
            SourceType type = stmt.getType();
            type.accept(this);
            writer.write(" ");
        }

        writer.write(normalizer.normalize(stmt.getName(), IdentifierNormalizer.IdentifierType.VARIABLE));

        if (stmt.getInitializer() != null) {
            writer.write(" = ");
            stmt.getInitializer().accept(this);
        }
    }

    @Override
    public Void visitExprStmt(ExprStmt stmt) {
        stmt.getExpression().accept(this);
        writer.writeLine(";");
        return null;
    }

    @Override
    public Void visitSynchronized(SynchronizedStmt stmt) {
        writer.write("synchronized (");
        stmt.getLock().accept(this);
        writer.write(") ");
        stmt.getBody().accept(this);
        return null;
    }

    @Override
    public Void visitLabeled(LabeledStmt stmt) {
        writer.write(stmt.getLabel());
        writer.writeLine(":");
        stmt.getStatement().accept(this);
        return null;
    }

    @Override
    public Void visitBreak(BreakStmt stmt) {
        if (stmt.getTargetLabel() != null) {
            writer.writeLine("break " + stmt.getTargetLabel() + ";");
        } else {
            writer.writeLine("break;");
        }
        return null;
    }

    @Override
    public Void visitContinue(ContinueStmt stmt) {
        if (stmt.getTargetLabel() != null) {
            writer.writeLine("continue " + stmt.getTargetLabel() + ";");
        } else {
            writer.writeLine("continue;");
        }
        return null;
    }

    @Override
    public Void visitIRRegion(IRRegionStmt stmt) {
        writer.writeLine("/* IR Region - irreducible control flow */");
        writer.writeLine("/* Blocks: " + stmt.getBlocks().size() + " */");
        // Emit block contents as comments for debugging
        for (var block : stmt.getBlocks()) {
            writer.writeLine("// " + block.getName() + ":");
            for (var phi : block.getPhiInstructions()) {
                writer.writeLine("//   " + phi);
            }
            for (var instr : block.getInstructions()) {
                writer.writeLine("//   " + instr);
            }
        }
        return null;
    }

    @Override
    public Void visitLiteral(LiteralExpr expr) {
        writer.write(formatLiteral(expr));
        return null;
    }

    private String formatLiteral(LiteralExpr expr) {
        Object value = expr.getValue();
        if (value == null) {
            return "null";
        }
        SourceType type = expr.getType();
        if (value instanceof Integer && type == PrimitiveSourceType.BOOLEAN) {
            int intVal = (Integer) value;
            return intVal != 0 ? "true" : "false";
        }
        if (value instanceof Integer && type == PrimitiveSourceType.CHAR) {
            int intVal = (Integer) value;
            return "'" + escapeChar((char) intVal) + "'";
        }
        if (value instanceof String) {
            String s = (String) value;
            return "\"" + escapeString(s) + "\"";
        }
        if (value instanceof Character) {
            Character c = (Character) value;
            return "'" + escapeChar(c) + "'";
        }
        if (value instanceof Long) {
            Long l = (Long) value;
            String symbolic = getSymbolicConstant(l, true);
            if (symbolic != null) {
                return symbolic;
            }
            return l + "L";
        }
        if (value instanceof Float) {
            Float f = (Float) value;
            String symbolic = getSymbolicFloatConstant(f);
            if (symbolic != null) {
                return symbolic;
            }
            return f + "f";
        }
        if (value instanceof Double) {
            Double d = (Double) value;
            String symbolic = getSymbolicDoubleConstant(d);
            if (symbolic != null) {
                return symbolic;
            }
            return d + "d";
        }
        if (value instanceof Boolean) {
            Boolean b = (Boolean) value;
            return b.toString();
        }
        if (value instanceof Integer) {
            Integer i = (Integer) value;
            String symbolic = getSymbolicConstant((long) i, false);
            if (symbolic != null) {
                return symbolic;
            }
        }
        return value.toString();
    }

    private String getSymbolicConstant(long value, boolean isLong) {
        if (value == Long.MAX_VALUE) {
            return "Long.MAX_VALUE";
        }
        if (value == Long.MIN_VALUE) {
            return "Long.MIN_VALUE";
        }
        if (value == Integer.MAX_VALUE) {
            return isLong ? "(long) Integer.MAX_VALUE" : "Integer.MAX_VALUE";
        }
        if (value == Integer.MIN_VALUE) {
            return isLong ? "(long) Integer.MIN_VALUE" : "Integer.MIN_VALUE";
        }
        if (value == Short.MAX_VALUE) {
            return isLong ? "(long) Short.MAX_VALUE" : "Short.MAX_VALUE";
        }
        if (value == Short.MIN_VALUE) {
            return isLong ? "(long) Short.MIN_VALUE" : "Short.MIN_VALUE";
        }
        if (value == Byte.MAX_VALUE) {
            return isLong ? "(long) Byte.MAX_VALUE" : "Byte.MAX_VALUE";
        }
        if (value == Byte.MIN_VALUE) {
            return isLong ? "(long) Byte.MIN_VALUE" : "Byte.MIN_VALUE";
        }
        return null;
    }

    private String getSymbolicFloatConstant(float value) {
        if (value == Float.MAX_VALUE) {
            return "Float.MAX_VALUE";
        }
        if (value == Float.MIN_VALUE) {
            return "Float.MIN_VALUE";
        }
        if (value == Float.MIN_NORMAL) {
            return "Float.MIN_NORMAL";
        }
        if (Float.isNaN(value)) {
            return "Float.NaN";
        }
        if (value == Float.POSITIVE_INFINITY) {
            return "Float.POSITIVE_INFINITY";
        }
        if (value == Float.NEGATIVE_INFINITY) {
            return "Float.NEGATIVE_INFINITY";
        }
        return null;
    }

    private String getSymbolicDoubleConstant(double value) {
        if (value == Double.MAX_VALUE) {
            return "Double.MAX_VALUE";
        }
        if (value == Double.MIN_VALUE) {
            return "Double.MIN_VALUE";
        }
        if (value == Double.MIN_NORMAL) {
            return "Double.MIN_NORMAL";
        }
        if (Double.isNaN(value)) {
            return "Double.NaN";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "Double.POSITIVE_INFINITY";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "Double.NEGATIVE_INFINITY";
        }
        return null;
    }

    private String escapeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(escapeChar(c));
        }
        return sb.toString();
    }

    private String escapeChar(char c) {
        switch (c) {
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\t':
                return "\\t";
            case '\\':
                return "\\\\";
            case '"':
                return "\\\"";
            case '\'':
                return "\\'";
            default:
                return c < 32 ? String.format("\\u%04x", (int) c) : String.valueOf(c);
        }
    }

    @Override
    public Void visitVarRef(VarRefExpr expr) {
        writer.write(normalizer.normalize(expr.getName(), IdentifierNormalizer.IdentifierType.VARIABLE));
        return null;
    }

    @Override
    public Void visitFieldAccess(FieldAccessExpr expr) {
        if (expr.isStatic()) {
            String ownerClass = expr.getOwnerClass();
            boolean isSelfReference = currentClassName != null &&
                (currentClassName.equals(ownerClass) ||
                 currentClassName.replace('/', '.').equals(ownerClass) ||
                 currentClassName.equals(ownerClass.replace('.', '/')));
            if (!isSelfReference) {
                writer.write(formatClassName(ownerClass));
                writer.write(".");
            }
        } else if (expr.getReceiver() != null) {
            expr.getReceiver().accept(this);
            writer.write(".");
        }
        writer.write(normalizer.normalize(expr.getFieldName(), IdentifierNormalizer.IdentifierType.FIELD));
        return null;
    }

    @Override
    public Void visitArrayAccess(ArrayAccessExpr expr) {
        expr.getArray().accept(this);
        writer.write("[");
        expr.getIndex().accept(this);
        writer.write("]");
        return null;
    }

    @Override
    public Void visitMethodCall(MethodCallExpr expr) {
        if (expr.isStatic()) {
            writer.write(formatClassName(expr.getOwnerClass()));
            writer.write(".");
        } else if (expr.getReceiver() != null) {
            Expression receiver = expr.getReceiver();
            SourceType receiverType = receiver.getType();
            String ownerClass = expr.getOwnerClass();

            boolean needsCast = false;
            if (receiverType instanceof ReferenceSourceType && ownerClass != null) {
                ReferenceSourceType refType = (ReferenceSourceType) receiverType;
                String receiverClass = refType.getInternalName();
                if (!ownerClass.equals(receiverClass) && !"java/lang/Object".equals(ownerClass)) {
                    needsCast = true;
                }
            }

            if (needsCast) {
                writer.write("((");
                writer.write(formatClassName(ownerClass));
                writer.write(") ");
                receiver.accept(this);
                writer.write(")");
            } else {
                receiver.accept(this);
            }
            writer.write(".");
        }

        writer.write(normalizer.normalize(expr.getMethodName(), IdentifierNormalizer.IdentifierType.METHOD));
        writer.write("(");
        emitExpressionList(expr.getArguments());
        writer.write(")");
        return null;
    }

    @Override
    public Void visitNew(NewExpr expr) {
        writer.write("new ");
        writer.write(formatClassName(expr.getClassName()));
        writer.write("(");
        emitExpressionList(expr.getArguments());
        writer.write(")");
        return null;
    }

    @Override
    public Void visitNewArray(NewArrayExpr expr) {
        writer.write("new ");
        expr.getElementType().accept(this);

        if (expr.hasInitializer()) {
            writer.write("[] ");
            expr.getInitializer().accept(this);
        } else {
            for (Expression dim : expr.getDimensions()) {
                writer.write("[");
                dim.accept(this);
                writer.write("]");
            }
        }
        return null;
    }

    @Override
    public Void visitArrayInit(ArrayInitExpr expr) {
        writer.write("{");
        emitExpressionList(expr.getElements());
        writer.write("}");
        return null;
    }

    @Override
    public Void visitBinary(BinaryExpr expr) {
        if (tryEmitIncrementDecrement(expr)) {
            return null;
        }

        boolean needsParens = needsParentheses(expr);
        if (needsParens) writer.write("(");

        expr.getLeft().accept(this);
        writer.write(" ");
        writer.write(expr.getOperator().getSymbol());
        writer.write(" ");
        expr.getRight().accept(this);

        if (needsParens) writer.write(")");
        return null;
    }

    private boolean tryEmitIncrementDecrement(BinaryExpr expr) {
        BinaryOperator op = expr.getOperator();
        Expression left = expr.getLeft();
        Expression right = expr.getRight();

        if (op == BinaryOperator.ADD_ASSIGN && isLiteralOne(right)) {
            left.accept(this);
            writer.write("++");
            return true;
        }
        if (op == BinaryOperator.SUB_ASSIGN && isLiteralOne(right)) {
            left.accept(this);
            writer.write("--");
            return true;
        }

        if (op == BinaryOperator.ASSIGN && right instanceof BinaryExpr) {
            BinaryExpr rightBinary = (BinaryExpr) right;
            BinaryOperator rightOp = rightBinary.getOperator();
            Expression rightLeft = rightBinary.getLeft();
            Expression rightRight = rightBinary.getRight();

            if (rightOp == BinaryOperator.ADD && expressionsEqual(left, rightLeft) && isLiteralOne(rightRight)) {
                left.accept(this);
                writer.write("++");
                return true;
            }
            if (rightOp == BinaryOperator.SUB && expressionsEqual(left, rightLeft) && isLiteralOne(rightRight)) {
                left.accept(this);
                writer.write("--");
                return true;
            }
        }

        return false;
    }

    private boolean isLiteralOne(Expression expr) {
        if (!(expr instanceof LiteralExpr)) {
            return false;
        }
        Object value = ((LiteralExpr) expr).getValue();
        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }
        return false;
    }

    private boolean expressionsEqual(Expression a, Expression b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getClass() != b.getClass()) {
            return false;
        }
        if (a instanceof VarRefExpr && b instanceof VarRefExpr) {
            return ((VarRefExpr) a).getName().equals(((VarRefExpr) b).getName());
        }
        if (a instanceof FieldAccessExpr && b instanceof FieldAccessExpr) {
            FieldAccessExpr fa = (FieldAccessExpr) a;
            FieldAccessExpr fb = (FieldAccessExpr) b;
            if (!fa.getFieldName().equals(fb.getFieldName())) {
                return false;
            }
            if (fa.isStatic() && fb.isStatic()) {
                return fa.getOwnerClass().equals(fb.getOwnerClass());
            }
            return expressionsEqual(fa.getReceiver(), fb.getReceiver());
        }
        if (a instanceof ArrayAccessExpr && b instanceof ArrayAccessExpr) {
            ArrayAccessExpr aa = (ArrayAccessExpr) a;
            ArrayAccessExpr ab = (ArrayAccessExpr) b;
            return expressionsEqual(aa.getArray(), ab.getArray()) &&
                   expressionsEqual(aa.getIndex(), ab.getIndex());
        }
        if (a instanceof LiteralExpr && b instanceof LiteralExpr) {
            Object va = ((LiteralExpr) a).getValue();
            Object vb = ((LiteralExpr) b).getValue();
            return va == null ? vb == null : va.equals(vb);
        }
        return false;
    }

    @Override
    public Void visitUnary(UnaryExpr expr) {
        if (expr.getOperator().isPrefix()) {
            writer.write(getUnaryOperatorSymbol(expr.getOperator()));
            boolean needsParens = expr.getOperand() instanceof BinaryExpr;
            if (needsParens) {
                writer.write("(");
            }
            expr.getOperand().accept(this);
            if (needsParens) {
                writer.write(")");
            }
        } else {
            expr.getOperand().accept(this);
            writer.write(getUnaryOperatorSymbol(expr.getOperator()));
        }
        return null;
    }

    @Override
    public Void visitCast(CastExpr expr) {
        writer.write("(");
        expr.getTargetType().accept(this);
        writer.write(") ");
        Expression inner = expr.getExpression();
        boolean needsParens = inner instanceof BinaryExpr || inner instanceof TernaryExpr;
        if (needsParens) {
            writer.write("(");
        }
        inner.accept(this);
        if (needsParens) {
            writer.write(")");
        }
        return null;
    }

    @Override
    public Void visitInstanceOf(InstanceOfExpr expr) {
        expr.getExpression().accept(this);
        writer.write(" instanceof ");
        expr.getCheckType().accept(this);
        if (expr.getPatternVariable() != null) {
            writer.write(" ");
            writer.write(expr.getPatternVariable());
        }
        return null;
    }

    @Override
    public Void visitTernary(TernaryExpr expr) {
        expr.getCondition().accept(this);
        writer.write(" ? ");
        expr.getThenExpr().accept(this);
        writer.write(" : ");
        expr.getElseExpr().accept(this);
        return null;
    }

    @Override
    public Void visitLambda(LambdaExpr expr) {
        List<LambdaParameter> params = expr.getParameters();
        if (params.isEmpty()) {
            writer.write("()");
        } else if (params.size() == 1 && params.get(0).type() == null) {
            writer.write(params.get(0).name());
        } else {
            writer.write("(");
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) writer.write(", ");
                LambdaParameter p = params.get(i);
                if (p.type() != null) {
                    writer.write(p.type().toJavaSource());
                    writer.write(" ");
                }
                writer.write(p.name());
            }
            writer.write(")");
        }

        writer.write(" -> ");

        if (expr.getBody() instanceof BlockStmt) {
            BlockStmt blockStmt = (BlockStmt) expr.getBody();
            if (blockStmt.getStatements().isEmpty()) {
                writer.write("{ }");
            } else {
                expr.getBody().accept(this);
            }
        } else if (expr.getBody() instanceof ExprStmt) {
            ExprStmt exprStmt = (ExprStmt) expr.getBody();
            exprStmt.getExpression().accept(this);
        } else {
            expr.getBody().accept(this);
        }
        return null;
    }

    @Override
    public Void visitMethodRef(MethodRefExpr expr) {
        if (expr.getReceiver() != null) {
            expr.getReceiver().accept(this);
        } else {
            writer.write(formatClassName(expr.getOwnerClass()));
        }
        writer.write("::");
        writer.write(normalizer.normalize(expr.getMethodName(), IdentifierNormalizer.IdentifierType.METHOD));
        return null;
    }

    @Override
    public Void visitThis(ThisExpr expr) {
        writer.write("this");
        return null;
    }

    @Override
    public Void visitSuper(SuperExpr expr) {
        writer.write("super");
        return null;
    }

    @Override
    public Void visitClass(ClassExpr expr) {
        writer.write(expr.getClassType().toJavaSource());
        writer.write(".class");
        return null;
    }

    @Override
    public Void visitDynamicConstant(DynamicConstantExpr expr) {
        if (config.isResolveBootstrapMethods() && !"unknown".equals(expr.getBootstrapOwner())) {
            // Emit as actual static method call: owner.bootstrapName()
            writer.write(formatClassName(expr.getBootstrapOwner()));
            writer.write(".");
            writer.write(normalizer.normalize(expr.getBootstrapName(), IdentifierNormalizer.IdentifierType.METHOD));
            writer.write("()");
            writer.write(" /* condy */");
        } else {
            // Emit as a comment that shows the condy information
            writer.write("/* condy:\"");
            writer.write(normalizer.normalize(expr.getName(), IdentifierNormalizer.IdentifierType.CONSTANT));
            writer.write("\" ");
            writer.write(expr.getDescriptor());
            writer.write(" @bsm ");
            writer.write(normalizer.normalizeClassName(expr.getFormattedBootstrapMethod()));
            writer.write(" */");
        }
        return null;
    }

    @Override
    public Void visitInvokeDynamic(InvokeDynamicExpr expr) {
        if (config.isResolveBootstrapMethods() && !"unknown".equals(expr.getBootstrapOwner())) {
            // Emit as actual static method call: owner.bootstrapName(args)
            writer.write(formatClassName(expr.getBootstrapOwner()));
            writer.write(".");
            writer.write(normalizer.normalize(expr.getBootstrapName(), IdentifierNormalizer.IdentifierType.METHOD));
            writer.write("(");
            emitExpressionList(expr.getArguments());
            writer.write(")");
            writer.write(" /* indy */");
        } else {
            // Emit as invokedynamic pseudo-call with comment
            writer.write("invokedynamic(\"");
            writer.write(normalizer.normalize(expr.getName(), IdentifierNormalizer.IdentifierType.METHOD));
            writer.write("\"");
            if (!expr.getArguments().isEmpty()) {
                writer.write(", ");
                emitExpressionList(expr.getArguments());
            }
            writer.write(") /* @bsm ");
            writer.write(normalizer.normalizeClassName(expr.getFormattedBootstrapMethod()));
            writer.write(" */");
        }
        return null;
    }

    @Override
    public Void visitPrimitiveType(PrimitiveSourceType type) {
        writer.write(type.toJavaSource());
        return null;
    }

    @Override
    public Void visitReferenceType(ReferenceSourceType type) {
        recordTypeUsage(type.getInternalName());
        for (SourceType typeArg : type.getTypeArguments()) {
            if (typeArg instanceof ReferenceSourceType) {
                recordTypeUsage(((ReferenceSourceType) typeArg).getInternalName());
            }
        }
        writer.write(type.toJavaSource());
        return null;
    }

    @Override
    public Void visitArrayType(ArraySourceType type) {
        SourceType elemType = type.getElementType();
        if (elemType instanceof ReferenceSourceType) {
            recordTypeUsage(((ReferenceSourceType) elemType).getInternalName());
        }
        writer.write(type.toJavaSource());
        return null;
    }

    @Override
    public Void visitVoidType(VoidSourceType type) {
        writer.write("void");
        return null;
    }

    /**
     * Checks if a statement is an empty block (no statements inside).
     */
    private boolean isEmptyBlock(Statement stmt) {
        if (stmt instanceof BlockStmt) {
            BlockStmt block = (BlockStmt) stmt;
            return block.getStatements().isEmpty();
        }
        return false;
    }

    private void emitBody(Statement body) {
        if (body instanceof BlockStmt) {
            body.accept(this);
        } else if (config.isAlwaysUseBraces()) {
            writer.writeLine("{");
            writer.indent();
            body.accept(this);
            writer.dedent();
            writer.writeLine("}");
        } else {
            writer.newLine();
            writer.indent();
            body.accept(this);
            writer.dedent();
        }
    }

    private void emitExpressionList(List<Expression> exprs) {
        for (int i = 0; i < exprs.size(); i++) {
            if (i > 0) writer.write(", ");
            exprs.get(i).accept(this);
        }
    }

    private String formatClassName(String internalName) {
        if (internalName == null) return "";
        recordTypeUsage(internalName);
        String formatted;
        if (config.isUseFullyQualifiedNames() || internalName.contains("$")) {
            formatted = ClassNameUtil.toSourceName(internalName);
        } else {
            formatted = ClassNameUtil.getSimpleNameWithInnerClasses(internalName);
        }
        return normalizer.normalizeClassName(formatted);
    }

    private boolean needsParentheses(BinaryExpr expr) {
        if (expr.getParent() instanceof BinaryExpr) {
            BinaryExpr parent = (BinaryExpr) expr.getParent();
            return expr.getOperator().getPrecedence() < parent.getOperator().getPrecedence();
        }
        return false;
    }

    private String getUnaryOperatorSymbol(UnaryOperator op) {
        switch (op) {
            case NEG:
                return "-";
            case POS:
                return "+";
            case NOT:
                return "!";
            case BNOT:
                return "~";
            case PRE_INC:
                return "++";
            case PRE_DEC:
                return "--";
            case POST_INC:
                return "++";
            case POST_DEC:
                return "--";
            default:
                throw new IllegalArgumentException("Unknown unary operator: " + op);
        }
    }
}
