package com.tonic.analysis.source.emit;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.utill.ClassNameUtil;

import java.util.List;

/**
 * Emits Java source code from AST nodes.
 */
public class SourceEmitter implements SourceVisitor<Void> {

    private final IndentingWriter writer;
    private final SourceEmitterConfig config;

    public SourceEmitter(IndentingWriter writer) {
        this(writer, SourceEmitterConfig.defaults());
    }

    public SourceEmitter(IndentingWriter writer, SourceEmitterConfig config) {
        this.writer = writer;
        this.config = config;
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

    @Override
    public Void visitBlock(BlockStmt stmt) {
        writer.writeLine("{");
        writer.indent();
        for (Statement s : stmt.getStatements()) {
            s.accept(this);
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
        writer.write(var.getType().toJavaSource());
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

        for (SwitchCase switchCase : stmt.getCases()) {
            if (switchCase.isDefault()) {
                writer.writeLine("default:");
            } else {
                for (Integer label : switchCase.labels()) {
                    writer.writeLine("case " + label + ":");
                }
            }
            writer.indent();
            for (Statement s : switchCase.statements()) {
                s.accept(this);
            }
            writer.dedent();
        }

        writer.dedent();
        writer.writeLine("}");
        return null;
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
                writer.write(types.get(i).toJavaSource());
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
            stmt.getValue().accept(this);
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
            writer.write(stmt.getType().toJavaSource());
            writer.write(" ");
        }

        writer.write(stmt.getName());

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
            return l + "L";
        }
        if (value instanceof Float) {
            Float f = (Float) value;
            return f + "f";
        }
        if (value instanceof Double) {
            Double d = (Double) value;
            return d + "d";
        }
        if (value instanceof Boolean) {
            Boolean b = (Boolean) value;
            return b.toString();
        }
        return value.toString();
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
        writer.write(expr.getName());
        return null;
    }

    @Override
    public Void visitFieldAccess(FieldAccessExpr expr) {
        if (expr.isStatic()) {
            writer.write(formatClassName(expr.getOwnerClass()));
        } else if (expr.getReceiver() != null) {
            expr.getReceiver().accept(this);
        }
        writer.write(".");
        writer.write(expr.getFieldName());
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
            expr.getReceiver().accept(this);
            writer.write(".");
        }

        writer.write(expr.getMethodName());
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
        writer.write(expr.getElementType().toJavaSource());

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

    @Override
    public Void visitUnary(UnaryExpr expr) {
        if (expr.getOperator().isPrefix()) {
            writer.write(getUnaryOperatorSymbol(expr.getOperator()));
            boolean needsParens = expr.getOperand() instanceof com.tonic.analysis.source.ast.expr.BinaryExpr;
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
        writer.write(expr.getTargetType().toJavaSource());
        writer.write(") ");
        expr.getExpression().accept(this);
        return null;
    }

    @Override
    public Void visitInstanceOf(InstanceOfExpr expr) {
        expr.getExpression().accept(this);
        writer.write(" instanceof ");
        writer.write(expr.getCheckType().toJavaSource());
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
        writer.write(expr.getMethodName());
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
    public Void visitPrimitiveType(PrimitiveSourceType type) {
        writer.write(type.toJavaSource());
        return null;
    }

    @Override
    public Void visitReferenceType(ReferenceSourceType type) {
        writer.write(type.toJavaSource());
        return null;
    }

    @Override
    public Void visitArrayType(ArraySourceType type) {
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
        if (config.isUseFullyQualifiedNames()) {
            return ClassNameUtil.toSourceName(internalName);
        }
        return ClassNameUtil.getSimpleNameWithInnerClasses(internalName);
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
