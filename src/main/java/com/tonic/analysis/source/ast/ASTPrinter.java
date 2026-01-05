package com.tonic.analysis.source.ast;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.List;

/**
 * Prints AST nodes in a structured tree format for debugging.
 * Similar to IRPrinter for SSA IR, but for the expression/statement AST.
 */
public class ASTPrinter implements SourceVisitor<Void> {

    private final StringBuilder output;
    private int indentLevel;
    private final String indentString;
    private final boolean showTypes;

    public ASTPrinter() {
        this(true);
    }

    public ASTPrinter(boolean showTypes) {
        this.output = new StringBuilder();
        this.indentLevel = 0;
        this.indentString = "  ";
        this.showTypes = showTypes;
    }

    public static String format(ASTNode node) {
        ASTPrinter printer = new ASTPrinter();
        node.accept(printer);
        return printer.toString();
    }

    public static String format(ASTNode node, boolean showTypes) {
        ASTPrinter printer = new ASTPrinter(showTypes);
        node.accept(printer);
        return printer.toString();
    }

    public static String formatCompact(ASTNode node) {
        return format(node, false);
    }

    @Override
    public String toString() {
        return output.toString();
    }

    private void indent() {
        indentLevel++;
    }

    private void dedent() {
        indentLevel--;
    }

    private void appendIndent() {
        for (int i = 0; i < indentLevel; i++) {
            output.append(indentString);
        }
    }

    private void appendLine(String text) {
        appendIndent();
        output.append(text).append("\n");
    }

    private void appendNode(String label, ASTNode node) {
        if (node == null) {
            appendLine(label + ": null");
        } else {
            appendLine(label + ":");
            indent();
            node.accept(this);
            dedent();
        }
    }

    private void appendType(SourceType type) {
        if (showTypes && type != null) {
            output.append(" : ").append(type.toJavaSource());
        }
    }

    private <T extends ASTNode> void appendNodeList(String label, List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            appendLine(label + ": []");
        } else {
            appendLine(label + ": [" + nodes.size() + "]");
            indent();
            for (int i = 0; i < nodes.size(); i++) {
                appendLine("[" + i + "]:");
                indent();
                nodes.get(i).accept(this);
                dedent();
            }
            dedent();
        }
    }

    @Override
    public Void visitBinary(BinaryExpr expr) {
        appendIndent();
        output.append("BinaryExpr(").append(expr.getOperator().name()).append(")");
        appendType(expr.getType());
        output.append("\n");
        indent();
        appendNode("left", expr.getLeft());
        appendNode("right", expr.getRight());
        dedent();
        return null;
    }

    @Override
    public Void visitUnary(UnaryExpr expr) {
        appendIndent();
        output.append("UnaryExpr(").append(expr.getOperator().name()).append(")");
        appendType(expr.getType());
        output.append("\n");
        indent();
        appendNode("operand", expr.getOperand());
        dedent();
        return null;
    }

    @Override
    public Void visitLiteral(LiteralExpr expr) {
        appendIndent();
        output.append("LiteralExpr(");
        Object value = expr.getValue();
        if (value == null) {
            output.append("null");
        } else if (value instanceof String) {
            output.append("\"").append(escapeString((String) value)).append("\"");
        } else if (value instanceof Character) {
            output.append("'").append(value).append("'");
        } else {
            output.append(value);
        }
        output.append(")");
        appendType(expr.getType());
        output.append("\n");
        return null;
    }

    @Override
    public Void visitVarRef(VarRefExpr expr) {
        appendIndent();
        output.append("VarRefExpr(").append(expr.getName()).append(")");
        appendType(expr.getType());
        output.append("\n");
        return null;
    }

    @Override
    public Void visitFieldAccess(FieldAccessExpr expr) {
        appendIndent();
        output.append("FieldAccessExpr(").append(expr.getFieldName());
        if (expr.isStatic()) {
            output.append(", static, owner=").append(expr.getOwnerClass());
        }
        output.append(")");
        appendType(expr.getType());
        output.append("\n");
        if (!expr.isStatic() && expr.getReceiver() != null) {
            indent();
            appendNode("receiver", expr.getReceiver());
            dedent();
        }
        return null;
    }

    @Override
    public Void visitArrayAccess(ArrayAccessExpr expr) {
        appendIndent();
        output.append("ArrayAccessExpr");
        appendType(expr.getType());
        output.append("\n");
        indent();
        appendNode("array", expr.getArray());
        appendNode("index", expr.getIndex());
        dedent();
        return null;
    }

    @Override
    public Void visitMethodCall(MethodCallExpr expr) {
        appendIndent();
        output.append("MethodCallExpr(").append(expr.getMethodName());
        if (expr.isStatic()) {
            output.append(", static, owner=").append(expr.getOwnerClass());
        }
        output.append(")");
        appendType(expr.getType());
        output.append("\n");
        indent();
        if (!expr.isStatic() && expr.getReceiver() != null) {
            appendNode("receiver", expr.getReceiver());
        }
        appendNodeList("arguments", expr.getArguments());
        dedent();
        return null;
    }

    @Override
    public Void visitNew(NewExpr expr) {
        appendIndent();
        output.append("NewExpr(").append(expr.getClassName()).append(")");
        appendType(expr.getType());
        output.append("\n");
        indent();
        appendNodeList("arguments", expr.getArguments());
        dedent();
        return null;
    }

    @Override
    public Void visitNewArray(NewArrayExpr expr) {
        appendIndent();
        output.append("NewArrayExpr(elementType=").append(expr.getElementType().toJavaSource());
        output.append(", dims=").append(expr.getDimensions().size());
        output.append(")");
        appendType(expr.getType());
        output.append("\n");
        indent();
        appendNodeList("dimensions", expr.getDimensions());
        if (expr.getInitializer() != null) {
            appendNode("initializer", expr.getInitializer());
        }
        dedent();
        return null;
    }

    @Override
    public Void visitArrayInit(ArrayInitExpr expr) {
        appendIndent();
        output.append("ArrayInitExpr");
        appendType(expr.getType());
        output.append("\n");
        indent();
        appendNodeList("elements", expr.getElements());
        dedent();
        return null;
    }

    @Override
    public Void visitCast(CastExpr expr) {
        appendIndent();
        output.append("CastExpr(targetType=").append(expr.getTargetType().toJavaSource()).append(")");
        appendType(expr.getType());
        output.append("\n");
        indent();
        appendNode("expression", expr.getExpression());
        dedent();
        return null;
    }

    @Override
    public Void visitInstanceOf(InstanceOfExpr expr) {
        appendIndent();
        output.append("InstanceOfExpr(checkType=").append(expr.getCheckType().toJavaSource());
        if (expr.getPatternVariable() != null) {
            output.append(", pattern=").append(expr.getPatternVariable());
        }
        output.append(")");
        appendType(expr.getType());
        output.append("\n");
        indent();
        appendNode("expression", expr.getExpression());
        dedent();
        return null;
    }

    @Override
    public Void visitTernary(TernaryExpr expr) {
        appendIndent();
        output.append("TernaryExpr");
        appendType(expr.getType());
        output.append("\n");
        indent();
        appendNode("condition", expr.getCondition());
        appendNode("thenExpr", expr.getThenExpr());
        appendNode("elseExpr", expr.getElseExpr());
        dedent();
        return null;
    }

    @Override
    public Void visitThis(ThisExpr expr) {
        appendIndent();
        output.append("ThisExpr");
        appendType(expr.getType());
        output.append("\n");
        return null;
    }

    @Override
    public Void visitSuper(SuperExpr expr) {
        appendIndent();
        output.append("SuperExpr");
        appendType(expr.getType());
        output.append("\n");
        return null;
    }

    @Override
    public Void visitClass(ClassExpr expr) {
        appendIndent();
        output.append("ClassExpr(").append(expr.getClassType().toJavaSource()).append(")");
        appendType(expr.getType());
        output.append("\n");
        return null;
    }

    @Override
    public Void visitLambda(LambdaExpr expr) {
        appendIndent();
        output.append("LambdaExpr");
        appendType(expr.getType());
        output.append("\n");
        indent();
        List<LambdaParameter> params = expr.getParameters();
        if (params != null && !params.isEmpty()) {
            appendLine("parameters: [" + params.size() + "]");
            indent();
            for (int i = 0; i < params.size(); i++) {
                LambdaParameter p = params.get(i);
                appendLine("[" + i + "]: " + p.name() + " : " +
                    (p.type() != null ? p.type().toJavaSource() : "inferred"));
            }
            dedent();
        }
        appendNode("body", expr.getBody());
        dedent();
        return null;
    }

    @Override
    public Void visitMethodRef(MethodRefExpr expr) {
        appendIndent();
        output.append("MethodRefExpr(").append(expr.getMethodName());
        if (expr.getOwnerClass() != null) {
            output.append(", owner=").append(expr.getOwnerClass());
        }
        output.append(")");
        appendType(expr.getType());
        output.append("\n");
        if (expr.getReceiver() != null) {
            indent();
            appendNode("receiver", expr.getReceiver());
            dedent();
        }
        return null;
    }

    @Override
    public Void visitInvokeDynamic(InvokeDynamicExpr expr) {
        appendIndent();
        output.append("InvokeDynamicExpr(").append(expr.getName()).append(")");
        appendType(expr.getType());
        output.append("\n");
        indent();
        appendNodeList("arguments", expr.getArguments());
        dedent();
        return null;
    }

    @Override
    public Void visitDynamicConstant(DynamicConstantExpr expr) {
        appendIndent();
        output.append("DynamicConstantExpr(").append(expr.getName()).append(")");
        appendType(expr.getType());
        output.append("\n");
        return null;
    }

    @Override
    public Void visitBlock(BlockStmt stmt) {
        appendIndent();
        output.append("BlockStmt");
        output.append("\n");
        indent();
        appendNodeList("statements", stmt.getStatements());
        dedent();
        return null;
    }

    @Override
    public Void visitIf(IfStmt stmt) {
        appendIndent();
        output.append("IfStmt");
        output.append("\n");
        indent();
        appendNode("condition", stmt.getCondition());
        appendNode("thenBranch", stmt.getThenBranch());
        if (stmt.hasElse()) {
            appendNode("elseBranch", stmt.getElseBranch());
        }
        dedent();
        return null;
    }

    @Override
    public Void visitWhile(WhileStmt stmt) {
        appendIndent();
        output.append("WhileStmt");
        if (stmt.getLabel() != null) {
            output.append(" [label=").append(stmt.getLabel()).append("]");
        }
        output.append("\n");
        indent();
        appendNode("condition", stmt.getCondition());
        appendNode("body", stmt.getBody());
        dedent();
        return null;
    }

    @Override
    public Void visitDoWhile(DoWhileStmt stmt) {
        appendIndent();
        output.append("DoWhileStmt");
        if (stmt.getLabel() != null) {
            output.append(" [label=").append(stmt.getLabel()).append("]");
        }
        output.append("\n");
        indent();
        appendNode("body", stmt.getBody());
        appendNode("condition", stmt.getCondition());
        dedent();
        return null;
    }

    @Override
    public Void visitFor(ForStmt stmt) {
        appendIndent();
        output.append("ForStmt");
        if (stmt.getLabel() != null) {
            output.append(" [label=").append(stmt.getLabel()).append("]");
        }
        output.append("\n");
        indent();
        appendNodeList("init", stmt.getInit());
        appendNode("condition", stmt.getCondition());
        appendNodeList("update", stmt.getUpdate());
        appendNode("body", stmt.getBody());
        dedent();
        return null;
    }

    @Override
    public Void visitForEach(ForEachStmt stmt) {
        appendIndent();
        output.append("ForEachStmt");
        if (stmt.getLabel() != null) {
            output.append(" [label=").append(stmt.getLabel()).append("]");
        }
        output.append("\n");
        indent();
        appendNode("variable", stmt.getVariable());
        appendNode("iterable", stmt.getIterable());
        appendNode("body", stmt.getBody());
        dedent();
        return null;
    }

    @Override
    public Void visitSwitch(SwitchStmt stmt) {
        appendIndent();
        output.append("SwitchStmt");
        output.append("\n");
        indent();
        appendNode("selector", stmt.getSelector());
        List<SwitchCase> cases = stmt.getCases();
        appendLine("cases: [" + cases.size() + "]");
        indent();
        for (int i = 0; i < cases.size(); i++) {
            SwitchCase c = cases.get(i);
            appendIndent();
            output.append("[").append(i).append("]: ");
            if (c.isDefault()) {
                output.append("default");
            } else if (c.hasExpressionLabels()) {
                output.append("case ");
                List<Expression> labels = c.expressionLabels();
                for (int j = 0; j < labels.size(); j++) {
                    if (j > 0) output.append(", ");
                    output.append(labels.get(j));
                }
            } else {
                output.append("case ");
                List<Integer> labels = c.labels();
                for (int j = 0; j < labels.size(); j++) {
                    if (j > 0) output.append(", ");
                    output.append(labels.get(j));
                }
            }
            output.append("\n");
            indent();
            appendNodeList("statements", c.statements());
            dedent();
        }
        dedent();
        dedent();
        return null;
    }

    @Override
    public Void visitTryCatch(TryCatchStmt stmt) {
        appendIndent();
        output.append("TryCatchStmt");
        output.append("\n");
        indent();
        if (stmt.hasResources()) {
            appendNodeList("resources", stmt.getResources());
        }
        appendNode("tryBlock", stmt.getTryBlock());
        List<CatchClause> catches = stmt.getCatches();
        if (catches != null && !catches.isEmpty()) {
            appendLine("catchClauses: [" + catches.size() + "]");
            indent();
            for (int i = 0; i < catches.size(); i++) {
                CatchClause c = catches.get(i);
                appendIndent();
                output.append("[").append(i).append("]: catch(");
                List<SourceType> types = c.exceptionTypes();
                for (int j = 0; j < types.size(); j++) {
                    if (j > 0) output.append(" | ");
                    output.append(types.get(j).toJavaSource());
                }
                output.append(" ").append(c.variableName()).append(")\n");
                indent();
                appendNode("body", c.body());
                dedent();
            }
            dedent();
        }
        if (stmt.getFinallyBlock() != null) {
            appendNode("finallyBlock", stmt.getFinallyBlock());
        }
        dedent();
        return null;
    }

    @Override
    public Void visitReturn(ReturnStmt stmt) {
        appendIndent();
        output.append("ReturnStmt");
        output.append("\n");
        if (stmt.getValue() != null) {
            indent();
            appendNode("value", stmt.getValue());
            dedent();
        }
        return null;
    }

    @Override
    public Void visitThrow(ThrowStmt stmt) {
        appendIndent();
        output.append("ThrowStmt");
        output.append("\n");
        indent();
        appendNode("exception", stmt.getException());
        dedent();
        return null;
    }

    @Override
    public Void visitBreak(BreakStmt stmt) {
        appendIndent();
        output.append("BreakStmt");
        if (stmt.getTargetLabel() != null) {
            output.append(" [target=").append(stmt.getTargetLabel()).append("]");
        }
        output.append("\n");
        return null;
    }

    @Override
    public Void visitContinue(ContinueStmt stmt) {
        appendIndent();
        output.append("ContinueStmt");
        if (stmt.getTargetLabel() != null) {
            output.append(" [target=").append(stmt.getTargetLabel()).append("]");
        }
        output.append("\n");
        return null;
    }

    @Override
    public Void visitVarDecl(VarDeclStmt stmt) {
        appendIndent();
        output.append("VarDeclStmt(").append(stmt.getName());
        output.append(" : ").append(stmt.getType().toJavaSource());
        if (stmt.isFinal()) {
            output.append(", final");
        }
        output.append(")");
        output.append("\n");
        if (stmt.getInitializer() != null) {
            indent();
            appendNode("initializer", stmt.getInitializer());
            dedent();
        }
        return null;
    }

    @Override
    public Void visitExprStmt(ExprStmt stmt) {
        appendIndent();
        output.append("ExprStmt");
        output.append("\n");
        indent();
        appendNode("expression", stmt.getExpression());
        dedent();
        return null;
    }

    @Override
    public Void visitSynchronized(SynchronizedStmt stmt) {
        appendIndent();
        output.append("SynchronizedStmt");
        output.append("\n");
        indent();
        appendNode("lock", stmt.getLock());
        appendNode("body", stmt.getBody());
        dedent();
        return null;
    }

    @Override
    public Void visitLabeled(LabeledStmt stmt) {
        appendIndent();
        output.append("LabeledStmt(").append(stmt.getLabel()).append(")");
        output.append("\n");
        indent();
        appendNode("statement", stmt.getStatement());
        dedent();
        return null;
    }

    @Override
    public Void visitIRRegion(IRRegionStmt stmt) {
        appendIndent();
        output.append("IRRegionStmt(blocks=").append(stmt.getBlocks().size());
        if (stmt.getReason() != null) {
            output.append(", reason=\"").append(stmt.getReason()).append("\"");
        }
        output.append(")");
        output.append("\n");
        return null;
    }

    // ==================== Type visitors ====================

    @Override
    public Void visitPrimitiveType(PrimitiveSourceType type) {
        appendIndent();
        output.append("PrimitiveType(").append(type.toJavaSource()).append(")\n");
        return null;
    }

    @Override
    public Void visitReferenceType(ReferenceSourceType type) {
        appendIndent();
        output.append("ReferenceType(").append(type.getInternalName()).append(")\n");
        return null;
    }

    @Override
    public Void visitArrayType(ArraySourceType type) {
        appendIndent();
        output.append("ArrayType(elementType=").append(type.getElementType().toJavaSource());
        output.append(", dims=").append(type.getDimensions()).append(")\n");
        return null;
    }

    @Override
    public Void visitVoidType(VoidSourceType type) {
        appendIndent();
        output.append("VoidType\n");
        return null;
    }

    private String escapeString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                default:
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
