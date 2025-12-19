package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for statement AST classes.
 * Tests construction, getters, visitor pattern, parent/child relationships,
 * toString methods, and edge cases.
 */
class StatementASTTest {

    // ==================== Test Helpers ====================

    private static Expression createTestExpression() {
        return LiteralExpr.ofBoolean(true);
    }

    private static Statement createTestStatement() {
        return new ReturnStmt();
    }

    private static SourceLocation createTestLocation() {
        return new SourceLocation(10, 5);
    }

    private static class TestVisitor implements SourceVisitor<String> {
        @Override
        public String visitBlock(BlockStmt stmt) {
            return "visitBlock";
        }

        @Override
        public String visitIf(IfStmt stmt) {
            return "visitIf";
        }

        @Override
        public String visitWhile(WhileStmt stmt) {
            return "visitWhile";
        }

        @Override
        public String visitDoWhile(DoWhileStmt stmt) {
            return "visitDoWhile";
        }

        @Override
        public String visitFor(ForStmt stmt) {
            return "visitFor";
        }

        @Override
        public String visitForEach(ForEachStmt stmt) {
            return "visitForEach";
        }

        @Override
        public String visitSwitch(SwitchStmt stmt) {
            return "visitSwitch";
        }

        @Override
        public String visitTryCatch(TryCatchStmt stmt) {
            return "visitTryCatch";
        }

        @Override
        public String visitReturn(ReturnStmt stmt) {
            return "visitReturn";
        }

        @Override
        public String visitThrow(ThrowStmt stmt) {
            return "visitThrow";
        }

        @Override
        public String visitVarDecl(VarDeclStmt stmt) {
            return "visitVarDecl";
        }

        @Override
        public String visitExprStmt(ExprStmt stmt) {
            return "visitExprStmt";
        }

        @Override
        public String visitSynchronized(SynchronizedStmt stmt) {
            return "visitSynchronized";
        }

        @Override
        public String visitLabeled(LabeledStmt stmt) {
            return "visitLabeled";
        }

        @Override
        public String visitBreak(BreakStmt stmt) {
            return "visitBreak";
        }

        @Override
        public String visitContinue(ContinueStmt stmt) {
            return "visitContinue";
        }

        @Override
        public String visitIRRegion(IRRegionStmt stmt) {
            return "visitIRRegion";
        }

        @Override
        public String visitLiteral(com.tonic.analysis.source.ast.expr.LiteralExpr expr) {
            return "visitLiteral";
        }

        @Override
        public String visitVarRef(com.tonic.analysis.source.ast.expr.VarRefExpr expr) {
            return "visitVarRef";
        }

        @Override
        public String visitFieldAccess(com.tonic.analysis.source.ast.expr.FieldAccessExpr expr) {
            return "visitFieldAccess";
        }

        @Override
        public String visitArrayAccess(com.tonic.analysis.source.ast.expr.ArrayAccessExpr expr) {
            return "visitArrayAccess";
        }

        @Override
        public String visitMethodCall(com.tonic.analysis.source.ast.expr.MethodCallExpr expr) {
            return "visitMethodCall";
        }

        @Override
        public String visitNew(com.tonic.analysis.source.ast.expr.NewExpr expr) {
            return "visitNew";
        }

        @Override
        public String visitNewArray(com.tonic.analysis.source.ast.expr.NewArrayExpr expr) {
            return "visitNewArray";
        }

        @Override
        public String visitArrayInit(com.tonic.analysis.source.ast.expr.ArrayInitExpr expr) {
            return "visitArrayInit";
        }

        @Override
        public String visitBinary(com.tonic.analysis.source.ast.expr.BinaryExpr expr) {
            return "visitBinary";
        }

        @Override
        public String visitUnary(com.tonic.analysis.source.ast.expr.UnaryExpr expr) {
            return "visitUnary";
        }

        @Override
        public String visitCast(com.tonic.analysis.source.ast.expr.CastExpr expr) {
            return "visitCast";
        }

        @Override
        public String visitInstanceOf(com.tonic.analysis.source.ast.expr.InstanceOfExpr expr) {
            return "visitInstanceOf";
        }

        @Override
        public String visitTernary(com.tonic.analysis.source.ast.expr.TernaryExpr expr) {
            return "visitTernary";
        }

        @Override
        public String visitLambda(com.tonic.analysis.source.ast.expr.LambdaExpr expr) {
            return "visitLambda";
        }

        @Override
        public String visitMethodRef(com.tonic.analysis.source.ast.expr.MethodRefExpr expr) {
            return "visitMethodRef";
        }

        @Override
        public String visitThis(com.tonic.analysis.source.ast.expr.ThisExpr expr) {
            return "visitThis";
        }

        @Override
        public String visitSuper(com.tonic.analysis.source.ast.expr.SuperExpr expr) {
            return "visitSuper";
        }

        @Override
        public String visitClass(com.tonic.analysis.source.ast.expr.ClassExpr expr) {
            return "visitClass";
        }

        @Override
        public String visitDynamicConstant(com.tonic.analysis.source.ast.expr.DynamicConstantExpr expr) {
            return "visitDynamicConstant";
        }

        @Override
        public String visitInvokeDynamic(com.tonic.analysis.source.ast.expr.InvokeDynamicExpr expr) {
            return "visitInvokeDynamic";
        }

        @Override
        public String visitPrimitiveType(PrimitiveSourceType type) {
            return "visitPrimitiveType";
        }

        @Override
        public String visitReferenceType(ReferenceSourceType type) {
            return "visitReferenceType";
        }

        @Override
        public String visitArrayType(com.tonic.analysis.source.ast.type.ArraySourceType type) {
            return "visitArrayType";
        }

        @Override
        public String visitVoidType(com.tonic.analysis.source.ast.type.VoidSourceType type) {
            return "visitVoidType";
        }
    }

    // ==================== IfStmt Tests ====================

    @Test
    void ifStmt_basicConstructor() {
        Expression condition = createTestExpression();
        Statement thenBranch = createTestStatement();
        Statement elseBranch = createTestStatement();

        IfStmt ifStmt = new IfStmt(condition, thenBranch, elseBranch);

        assertNotNull(ifStmt);
        assertEquals(condition, ifStmt.getCondition());
        assertEquals(thenBranch, ifStmt.getThenBranch());
        assertEquals(elseBranch, ifStmt.getElseBranch());
        assertEquals(SourceLocation.UNKNOWN, ifStmt.getLocation());
    }

    @Test
    void ifStmt_withLocation() {
        Expression condition = createTestExpression();
        Statement thenBranch = createTestStatement();
        SourceLocation location = createTestLocation();

        IfStmt ifStmt = new IfStmt(condition, thenBranch, null, location);

        assertEquals(location, ifStmt.getLocation());
    }

    @Test
    void ifStmt_withoutElse() {
        Expression condition = createTestExpression();
        Statement thenBranch = createTestStatement();

        IfStmt ifStmt = new IfStmt(condition, thenBranch);

        assertFalse(ifStmt.hasElse());
        assertNull(ifStmt.getElseBranch());
        assertFalse(ifStmt.isElseIf());
    }

    @Test
    void ifStmt_withElse() {
        Expression condition = createTestExpression();
        Statement thenBranch = createTestStatement();
        Statement elseBranch = createTestStatement();

        IfStmt ifStmt = new IfStmt(condition, thenBranch, elseBranch);

        assertTrue(ifStmt.hasElse());
        assertNotNull(ifStmt.getElseBranch());
    }

    @Test
    void ifStmt_elseIfChain() {
        Expression condition1 = createTestExpression();
        Expression condition2 = createTestExpression();
        Statement thenBranch1 = createTestStatement();
        Statement thenBranch2 = createTestStatement();

        IfStmt innerIf = new IfStmt(condition2, thenBranch2);
        IfStmt outerIf = new IfStmt(condition1, thenBranch1, innerIf);

        assertTrue(outerIf.isElseIf());
    }

    @Test
    void ifStmt_parentChildRelationships() {
        Expression condition = createTestExpression();
        Statement thenBranch = createTestStatement();
        Statement elseBranch = createTestStatement();

        IfStmt ifStmt = new IfStmt(condition, thenBranch, elseBranch);

        assertEquals(ifStmt, condition.getParent());
        assertEquals(ifStmt, thenBranch.getParent());
        assertEquals(ifStmt, elseBranch.getParent());
    }

    @Test
    void ifStmt_setters() {
        IfStmt ifStmt = new IfStmt(createTestExpression(), createTestStatement());

        Expression newCondition = LiteralExpr.ofBoolean(false);
        Statement newThen = new ReturnStmt(LiteralExpr.ofInt(1));
        Statement newElse = new ReturnStmt(LiteralExpr.ofInt(2));

        ifStmt.setCondition(newCondition);
        ifStmt.setThenBranch(newThen);
        ifStmt.setElseBranch(newElse);

        assertEquals(newCondition, ifStmt.getCondition());
        assertEquals(newThen, ifStmt.getThenBranch());
        assertEquals(newElse, ifStmt.getElseBranch());
    }

    @Test
    void ifStmt_nullConditionThrows() {
        assertThrows(NullPointerException.class, () ->
                new IfStmt(null, createTestStatement())
        );
    }

    @Test
    void ifStmt_nullThenBranchThrows() {
        assertThrows(NullPointerException.class, () ->
                new IfStmt(createTestExpression(), null)
        );
    }

    @Test
    void ifStmt_visitorPattern() {
        IfStmt ifStmt = new IfStmt(createTestExpression(), createTestStatement());
        TestVisitor visitor = new TestVisitor();

        String result = ifStmt.accept(visitor);

        assertEquals("visitIf", result);
    }

    @Test
    void ifStmt_toStringWithoutElse() {
        IfStmt ifStmt = new IfStmt(LiteralExpr.ofBoolean(true), createTestStatement());

        String str = ifStmt.toString();

        assertTrue(str.contains("if"));
        assertTrue(str.contains("true"));
        assertTrue(str.contains("then"));
        assertFalse(str.contains("else"));
    }

    @Test
    void ifStmt_toStringWithElse() {
        IfStmt ifStmt = new IfStmt(
                LiteralExpr.ofBoolean(true),
                createTestStatement(),
                createTestStatement()
        );

        String str = ifStmt.toString();

        assertTrue(str.contains("if"));
        assertTrue(str.contains("else"));
    }

    // ==================== WhileStmt Tests ====================

    @Test
    void whileStmt_basicConstructor() {
        Expression condition = createTestExpression();
        Statement body = createTestStatement();

        WhileStmt whileStmt = new WhileStmt(condition, body);

        assertNotNull(whileStmt);
        assertEquals(condition, whileStmt.getCondition());
        assertEquals(body, whileStmt.getBody());
        assertNull(whileStmt.getLabel());
    }

    @Test
    void whileStmt_withLabel() {
        Expression condition = createTestExpression();
        Statement body = createTestStatement();
        String label = "loop";

        WhileStmt whileStmt = new WhileStmt(condition, body, label);

        assertEquals(label, whileStmt.getLabel());
    }

    @Test
    void whileStmt_withLocation() {
        Expression condition = createTestExpression();
        Statement body = createTestStatement();
        SourceLocation location = createTestLocation();

        WhileStmt whileStmt = new WhileStmt(condition, body, null, location);

        assertEquals(location, whileStmt.getLocation());
    }

    @Test
    void whileStmt_parentChildRelationships() {
        Expression condition = createTestExpression();
        Statement body = createTestStatement();

        WhileStmt whileStmt = new WhileStmt(condition, body);

        assertEquals(whileStmt, condition.getParent());
        assertEquals(whileStmt, body.getParent());
    }

    @Test
    void whileStmt_setters() {
        WhileStmt whileStmt = new WhileStmt(createTestExpression(), createTestStatement());

        Expression newCondition = LiteralExpr.ofBoolean(false);
        Statement newBody = new ReturnStmt();
        String newLabel = "newLoop";

        whileStmt.setCondition(newCondition);
        whileStmt.setBody(newBody);
        whileStmt.setLabel(newLabel);

        assertEquals(newCondition, whileStmt.getCondition());
        assertEquals(newBody, whileStmt.getBody());
        assertEquals(newLabel, whileStmt.getLabel());
    }

    @Test
    void whileStmt_nullConditionThrows() {
        assertThrows(NullPointerException.class, () ->
                new WhileStmt(null, createTestStatement())
        );
    }

    @Test
    void whileStmt_nullBodyThrows() {
        assertThrows(NullPointerException.class, () ->
                new WhileStmt(createTestExpression(), null)
        );
    }

    @Test
    void whileStmt_visitorPattern() {
        WhileStmt whileStmt = new WhileStmt(createTestExpression(), createTestStatement());
        TestVisitor visitor = new TestVisitor();

        String result = whileStmt.accept(visitor);

        assertEquals("visitWhile", result);
    }

    @Test
    void whileStmt_toStringWithoutLabel() {
        WhileStmt whileStmt = new WhileStmt(LiteralExpr.ofBoolean(true), createTestStatement());

        String str = whileStmt.toString();

        assertTrue(str.contains("while"));
        assertTrue(str.contains("true"));
        assertFalse(str.contains(":"));
    }

    @Test
    void whileStmt_toStringWithLabel() {
        WhileStmt whileStmt = new WhileStmt(
                LiteralExpr.ofBoolean(true),
                createTestStatement(),
                "myLoop"
        );

        String str = whileStmt.toString();

        assertTrue(str.contains("myLoop:"));
        assertTrue(str.contains("while"));
    }

    // ==================== DoWhileStmt Tests ====================

    @Test
    void doWhileStmt_basicConstructor() {
        Statement body = createTestStatement();
        Expression condition = createTestExpression();

        DoWhileStmt doWhileStmt = new DoWhileStmt(body, condition);

        assertNotNull(doWhileStmt);
        assertEquals(body, doWhileStmt.getBody());
        assertEquals(condition, doWhileStmt.getCondition());
        assertNull(doWhileStmt.getLabel());
    }

    @Test
    void doWhileStmt_withLabel() {
        Statement body = createTestStatement();
        Expression condition = createTestExpression();
        String label = "loop";

        DoWhileStmt doWhileStmt = new DoWhileStmt(body, condition, label);

        assertEquals(label, doWhileStmt.getLabel());
    }

    @Test
    void doWhileStmt_withLocation() {
        Statement body = createTestStatement();
        Expression condition = createTestExpression();
        SourceLocation location = createTestLocation();

        DoWhileStmt doWhileStmt = new DoWhileStmt(body, condition, null, location);

        assertEquals(location, doWhileStmt.getLocation());
    }

    @Test
    void doWhileStmt_parentChildRelationships() {
        Statement body = createTestStatement();
        Expression condition = createTestExpression();

        DoWhileStmt doWhileStmt = new DoWhileStmt(body, condition);

        assertEquals(doWhileStmt, body.getParent());
        assertEquals(doWhileStmt, condition.getParent());
    }

    @Test
    void doWhileStmt_setters() {
        DoWhileStmt doWhileStmt = new DoWhileStmt(createTestStatement(), createTestExpression());

        Statement newBody = new ReturnStmt();
        Expression newCondition = LiteralExpr.ofBoolean(false);
        String newLabel = "newLoop";

        doWhileStmt.setBody(newBody);
        doWhileStmt.setCondition(newCondition);
        doWhileStmt.setLabel(newLabel);

        assertEquals(newBody, doWhileStmt.getBody());
        assertEquals(newCondition, doWhileStmt.getCondition());
        assertEquals(newLabel, doWhileStmt.getLabel());
    }

    @Test
    void doWhileStmt_nullBodyThrows() {
        assertThrows(NullPointerException.class, () ->
                new DoWhileStmt(null, createTestExpression())
        );
    }

    @Test
    void doWhileStmt_nullConditionThrows() {
        assertThrows(NullPointerException.class, () ->
                new DoWhileStmt(createTestStatement(), null)
        );
    }

    @Test
    void doWhileStmt_visitorPattern() {
        DoWhileStmt doWhileStmt = new DoWhileStmt(createTestStatement(), createTestExpression());
        TestVisitor visitor = new TestVisitor();

        String result = doWhileStmt.accept(visitor);

        assertEquals("visitDoWhile", result);
    }

    @Test
    void doWhileStmt_toStringWithoutLabel() {
        DoWhileStmt doWhileStmt = new DoWhileStmt(createTestStatement(), LiteralExpr.ofBoolean(true));

        String str = doWhileStmt.toString();

        assertTrue(str.contains("do"));
        assertTrue(str.contains("while"));
        assertTrue(str.contains("true"));
        assertFalse(str.contains("loop:"));
    }

    @Test
    void doWhileStmt_toStringWithLabel() {
        DoWhileStmt doWhileStmt = new DoWhileStmt(
                createTestStatement(),
                LiteralExpr.ofBoolean(true),
                "myLoop"
        );

        String str = doWhileStmt.toString();

        assertTrue(str.contains("myLoop:"));
        assertTrue(str.contains("do"));
        assertTrue(str.contains("while"));
    }

    // ==================== ForStmt Tests ====================

    @Test
    void forStmt_basicConstructor() {
        List<Statement> init = Arrays.asList(new VarDeclStmt(PrimitiveSourceType.INT, "i", LiteralExpr.ofInt(0)));
        Expression condition = createTestExpression();
        List<Expression> update = Arrays.asList(LiteralExpr.ofInt(1));
        Statement body = createTestStatement();

        ForStmt forStmt = new ForStmt(init, condition, update, body);

        assertNotNull(forStmt);
        assertEquals(1, forStmt.getInit().size());
        assertEquals(condition, forStmt.getCondition());
        assertEquals(1, forStmt.getUpdate().size());
        assertEquals(body, forStmt.getBody());
        assertNull(forStmt.getLabel());
    }

    @Test
    void forStmt_emptyInitAndUpdate() {
        Expression condition = createTestExpression();
        Statement body = createTestStatement();

        ForStmt forStmt = new ForStmt(Collections.emptyList(), condition, Collections.emptyList(), body);

        assertTrue(forStmt.getInit().isEmpty());
        assertTrue(forStmt.getUpdate().isEmpty());
    }

    @Test
    void forStmt_nullInitAndUpdate() {
        Expression condition = createTestExpression();
        Statement body = createTestStatement();

        ForStmt forStmt = new ForStmt(null, condition, null, body);

        assertNotNull(forStmt.getInit());
        assertNotNull(forStmt.getUpdate());
        assertTrue(forStmt.getInit().isEmpty());
        assertTrue(forStmt.getUpdate().isEmpty());
    }

    @Test
    void forStmt_infiniteLoop() {
        Statement body = createTestStatement();

        ForStmt forStmt = ForStmt.infinite(body);

        assertNull(forStmt.getCondition());
        assertTrue(forStmt.isInfinite());
    }

    @Test
    void forStmt_notInfiniteWithCondition() {
        ForStmt forStmt = new ForStmt(
                Collections.emptyList(),
                createTestExpression(),
                Collections.emptyList(),
                createTestStatement()
        );

        assertFalse(forStmt.isInfinite());
    }

    @Test
    void forStmt_withLabel() {
        ForStmt forStmt = new ForStmt(
                Collections.emptyList(),
                createTestExpression(),
                Collections.emptyList(),
                createTestStatement(),
                "loop",
                SourceLocation.UNKNOWN
        );

        assertEquals("loop", forStmt.getLabel());
    }

    @Test
    void forStmt_addInit() {
        ForStmt forStmt = new ForStmt(
                Collections.emptyList(),
                createTestExpression(),
                Collections.emptyList(),
                createTestStatement()
        );

        Statement initStmt = new VarDeclStmt(PrimitiveSourceType.INT, "i", LiteralExpr.ofInt(0));
        forStmt.addInit(initStmt);

        assertEquals(1, forStmt.getInit().size());
        assertEquals(forStmt, initStmt.getParent());
    }

    @Test
    void forStmt_addUpdate() {
        ForStmt forStmt = new ForStmt(
                Collections.emptyList(),
                createTestExpression(),
                Collections.emptyList(),
                createTestStatement()
        );

        Expression updateExpr = LiteralExpr.ofInt(1);
        forStmt.addUpdate(updateExpr);

        assertEquals(1, forStmt.getUpdate().size());
        assertEquals(forStmt, updateExpr.getParent());
    }

    @Test
    void forStmt_addNullInitIgnored() {
        ForStmt forStmt = ForStmt.infinite(createTestStatement());

        forStmt.addInit(null);

        assertTrue(forStmt.getInit().isEmpty());
    }

    @Test
    void forStmt_addNullUpdateIgnored() {
        ForStmt forStmt = ForStmt.infinite(createTestStatement());

        forStmt.addUpdate(null);

        assertTrue(forStmt.getUpdate().isEmpty());
    }

    @Test
    void forStmt_filtersNullsInConstructor() {
        List<Statement> init = Arrays.asList(
                new VarDeclStmt(PrimitiveSourceType.INT, "i", LiteralExpr.ofInt(0)),
                null,
                new VarDeclStmt(PrimitiveSourceType.INT, "j", LiteralExpr.ofInt(1))
        );
        List<Expression> update = Arrays.asList(
                LiteralExpr.ofInt(1),
                null,
                LiteralExpr.ofInt(2)
        );

        ForStmt forStmt = new ForStmt(init, createTestExpression(), update, createTestStatement());

        assertEquals(2, forStmt.getInit().size());
        assertEquals(2, forStmt.getUpdate().size());
    }

    @Test
    void forStmt_parentChildRelationships() {
        Statement initStmt = new VarDeclStmt(PrimitiveSourceType.INT, "i", LiteralExpr.ofInt(0));
        Expression condition = createTestExpression();
        Expression updateExpr = LiteralExpr.ofInt(1);
        Statement body = createTestStatement();

        ForStmt forStmt = new ForStmt(
                Arrays.asList(initStmt),
                condition,
                Arrays.asList(updateExpr),
                body
        );

        assertEquals(forStmt, initStmt.getParent());
        assertEquals(forStmt, condition.getParent());
        assertEquals(forStmt, updateExpr.getParent());
        assertEquals(forStmt, body.getParent());
    }

    @Test
    void forStmt_nullConditionAllowed() {
        ForStmt forStmt = new ForStmt(
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                createTestStatement()
        );

        assertNull(forStmt.getCondition());
    }

    @Test
    void forStmt_nullBodyThrows() {
        assertThrows(NullPointerException.class, () ->
                new ForStmt(Collections.emptyList(), createTestExpression(), Collections.emptyList(), null)
        );
    }

    @Test
    void forStmt_setters() {
        ForStmt forStmt = ForStmt.infinite(createTestStatement());

        Expression newCondition = LiteralExpr.ofBoolean(false);
        Statement newBody = new ReturnStmt();
        String newLabel = "newLoop";

        forStmt.setCondition(newCondition);
        forStmt.setBody(newBody);
        forStmt.setLabel(newLabel);

        assertEquals(newCondition, forStmt.getCondition());
        assertEquals(newBody, forStmt.getBody());
        assertEquals(newLabel, forStmt.getLabel());
    }

    @Test
    void forStmt_visitorPattern() {
        ForStmt forStmt = ForStmt.infinite(createTestStatement());
        TestVisitor visitor = new TestVisitor();

        String result = forStmt.accept(visitor);

        assertEquals("visitFor", result);
    }

    @Test
    void forStmt_toString() {
        ForStmt forStmt = new ForStmt(
                Arrays.asList(new VarDeclStmt(PrimitiveSourceType.INT, "i", LiteralExpr.ofInt(0))),
                LiteralExpr.ofBoolean(true),
                Arrays.asList(LiteralExpr.ofInt(1)),
                createTestStatement()
        );

        String str = forStmt.toString();

        assertTrue(str.contains("for"));
        assertTrue(str.contains("init"));
        assertTrue(str.contains("update"));
    }

    @Test
    void forStmt_toStringWithLabel() {
        ForStmt forStmt = new ForStmt(
                Collections.emptyList(),
                createTestExpression(),
                Collections.emptyList(),
                createTestStatement(),
                "myLoop",
                SourceLocation.UNKNOWN
        );

        String str = forStmt.toString();

        assertTrue(str.contains("myLoop:"));
    }

    // ==================== ForEachStmt Tests ====================

    @Test
    void forEachStmt_basicConstructor() {
        VarDeclStmt variable = new VarDeclStmt(PrimitiveSourceType.INT, "item");
        Expression iterable = createTestExpression();
        Statement body = createTestStatement();

        ForEachStmt forEachStmt = new ForEachStmt(variable, iterable, body);

        assertNotNull(forEachStmt);
        assertEquals(variable, forEachStmt.getVariable());
        assertEquals(iterable, forEachStmt.getIterable());
        assertEquals(body, forEachStmt.getBody());
        assertNull(forEachStmt.getLabel());
    }

    @Test
    void forEachStmt_withLabel() {
        VarDeclStmt variable = new VarDeclStmt(PrimitiveSourceType.INT, "item");
        Expression iterable = createTestExpression();
        Statement body = createTestStatement();
        String label = "loop";

        ForEachStmt forEachStmt = new ForEachStmt(variable, iterable, body, label, SourceLocation.UNKNOWN);

        assertEquals(label, forEachStmt.getLabel());
    }

    @Test
    void forEachStmt_withLocation() {
        VarDeclStmt variable = new VarDeclStmt(PrimitiveSourceType.INT, "item");
        Expression iterable = createTestExpression();
        Statement body = createTestStatement();
        SourceLocation location = createTestLocation();

        ForEachStmt forEachStmt = new ForEachStmt(variable, iterable, body, null, location);

        assertEquals(location, forEachStmt.getLocation());
    }

    @Test
    void forEachStmt_parentChildRelationships() {
        VarDeclStmt variable = new VarDeclStmt(PrimitiveSourceType.INT, "item");
        Expression iterable = createTestExpression();
        Statement body = createTestStatement();

        ForEachStmt forEachStmt = new ForEachStmt(variable, iterable, body);

        assertEquals(forEachStmt, variable.getParent());
        assertEquals(forEachStmt, iterable.getParent());
        assertEquals(forEachStmt, body.getParent());
    }

    @Test
    void forEachStmt_setters() {
        ForEachStmt forEachStmt = new ForEachStmt(
                new VarDeclStmt(PrimitiveSourceType.INT, "item"),
                createTestExpression(),
                createTestStatement()
        );

        VarDeclStmt newVariable = new VarDeclStmt(PrimitiveSourceType.LONG, "newItem");
        Expression newIterable = LiteralExpr.ofString("list");
        Statement newBody = new ReturnStmt();
        String newLabel = "newLoop";

        forEachStmt.setVariable(newVariable);
        forEachStmt.setIterable(newIterable);
        forEachStmt.setBody(newBody);
        forEachStmt.setLabel(newLabel);

        assertEquals(newVariable, forEachStmt.getVariable());
        assertEquals(newIterable, forEachStmt.getIterable());
        assertEquals(newBody, forEachStmt.getBody());
        assertEquals(newLabel, forEachStmt.getLabel());
    }

    @Test
    void forEachStmt_nullVariableThrows() {
        assertThrows(NullPointerException.class, () ->
                new ForEachStmt(null, createTestExpression(), createTestStatement())
        );
    }

    @Test
    void forEachStmt_nullIterableThrows() {
        assertThrows(NullPointerException.class, () ->
                new ForEachStmt(new VarDeclStmt(PrimitiveSourceType.INT, "item"), null, createTestStatement())
        );
    }

    @Test
    void forEachStmt_nullBodyThrows() {
        assertThrows(NullPointerException.class, () ->
                new ForEachStmt(new VarDeclStmt(PrimitiveSourceType.INT, "item"), createTestExpression(), null)
        );
    }

    @Test
    void forEachStmt_visitorPattern() {
        ForEachStmt forEachStmt = new ForEachStmt(
                new VarDeclStmt(PrimitiveSourceType.INT, "item"),
                createTestExpression(),
                createTestStatement()
        );
        TestVisitor visitor = new TestVisitor();

        String result = forEachStmt.accept(visitor);

        assertEquals("visitForEach", result);
    }

    @Test
    void forEachStmt_toStringWithoutLabel() {
        ForEachStmt forEachStmt = new ForEachStmt(
                new VarDeclStmt(PrimitiveSourceType.INT, "item"),
                LiteralExpr.ofString("list"),
                createTestStatement()
        );

        String str = forEachStmt.toString();

        assertTrue(str.contains("for"));
        assertTrue(str.contains("int"));
        assertTrue(str.contains("item"));
        assertTrue(str.contains(":"));
        assertTrue(str.contains("list"));
    }

    @Test
    void forEachStmt_toStringWithLabel() {
        ForEachStmt forEachStmt = new ForEachStmt(
                new VarDeclStmt(PrimitiveSourceType.INT, "item"),
                LiteralExpr.ofString("list"),
                createTestStatement(),
                "myLoop",
                SourceLocation.UNKNOWN
        );

        String str = forEachStmt.toString();

        assertTrue(str.contains("myLoop:"));
        assertTrue(str.contains("for"));
    }

    // ==================== SwitchStmt Tests ====================

    @Test
    void switchStmt_basicConstructor() {
        Expression selector = createTestExpression();

        SwitchStmt switchStmt = new SwitchStmt(selector);

        assertNotNull(switchStmt);
        assertEquals(selector, switchStmt.getSelector());
        assertTrue(switchStmt.getCases().isEmpty());
        assertEquals(0, switchStmt.getCaseCount());
    }

    @Test
    void switchStmt_withCases() {
        Expression selector = createTestExpression();
        SwitchCase case1 = SwitchCase.of(1, Arrays.asList(createTestStatement()));
        SwitchCase case2 = SwitchCase.of(2, Arrays.asList(createTestStatement()));

        SwitchStmt switchStmt = new SwitchStmt(selector, Arrays.asList(case1, case2));

        assertEquals(2, switchStmt.getCaseCount());
        assertEquals(2, switchStmt.getCases().size());
    }

    @Test
    void switchStmt_addCase() {
        SwitchStmt switchStmt = new SwitchStmt(createTestExpression());
        SwitchCase newCase = SwitchCase.of(1, Arrays.asList(createTestStatement()));

        switchStmt.addCase(newCase);

        assertEquals(1, switchStmt.getCaseCount());
    }

    @Test
    void switchStmt_defaultCase() {
        SwitchStmt switchStmt = new SwitchStmt(createTestExpression());
        SwitchCase defaultCase = SwitchCase.defaultCase(Arrays.asList(createTestStatement()));

        switchStmt.addCase(defaultCase);

        assertTrue(switchStmt.hasDefault());
        assertEquals(defaultCase, switchStmt.getDefaultCase());
    }

    @Test
    void switchStmt_noDefaultCase() {
        SwitchStmt switchStmt = new SwitchStmt(createTestExpression());
        SwitchCase normalCase = SwitchCase.of(1, Arrays.asList(createTestStatement()));

        switchStmt.addCase(normalCase);

        assertFalse(switchStmt.hasDefault());
        assertNull(switchStmt.getDefaultCase());
    }

    @Test
    void switchStmt_nullSelectorThrows() {
        assertThrows(NullPointerException.class, () ->
                new SwitchStmt(null)
        );
    }

    @Test
    void switchStmt_nullCasesAllowed() {
        SwitchStmt switchStmt = new SwitchStmt(createTestExpression(), null);

        assertNotNull(switchStmt.getCases());
        assertTrue(switchStmt.getCases().isEmpty());
    }

    @Test
    void switchStmt_parentChildRelationships() {
        Expression selector = createTestExpression();

        SwitchStmt switchStmt = new SwitchStmt(selector);

        assertEquals(switchStmt, selector.getParent());
    }

    @Test
    void switchStmt_setters() {
        SwitchStmt switchStmt = new SwitchStmt(createTestExpression());

        Expression newSelector = LiteralExpr.ofInt(42);
        switchStmt.setSelector(newSelector);

        assertEquals(newSelector, switchStmt.getSelector());
    }

    @Test
    void switchStmt_withLocation() {
        Expression selector = createTestExpression();
        SourceLocation location = createTestLocation();

        SwitchStmt switchStmt = new SwitchStmt(selector, Collections.emptyList(), location);

        assertEquals(location, switchStmt.getLocation());
    }

    @Test
    void switchStmt_visitorPattern() {
        SwitchStmt switchStmt = new SwitchStmt(createTestExpression());
        TestVisitor visitor = new TestVisitor();

        String result = switchStmt.accept(visitor);

        assertEquals("visitSwitch", result);
    }

    @Test
    void switchStmt_toString() {
        SwitchStmt switchStmt = new SwitchStmt(
                LiteralExpr.ofInt(42),
                Arrays.asList(
                        SwitchCase.of(1, Arrays.asList(createTestStatement())),
                        SwitchCase.of(2, Arrays.asList(createTestStatement()))
                )
        );

        String str = switchStmt.toString();

        assertTrue(str.contains("switch"));
        assertTrue(str.contains("42"));
        assertTrue(str.contains("2 cases"));
    }

    // ==================== SwitchCase Tests ====================

    @Test
    void switchCase_basicConstructor() {
        List<Integer> labels = Arrays.asList(1, 2, 3);
        List<Statement> statements = Arrays.asList(createTestStatement());

        SwitchCase switchCase = new SwitchCase(labels, false, statements);

        assertNotNull(switchCase);
        assertEquals(3, switchCase.labels().size());
        assertEquals(1, switchCase.statements().size());
        assertFalse(switchCase.isDefault());
    }

    @Test
    void switchCase_singleLabel() {
        SwitchCase switchCase = SwitchCase.of(42, Arrays.asList(createTestStatement()));

        assertEquals(1, switchCase.labels().size());
        assertEquals(42, switchCase.labels().get(0));
        assertFalse(switchCase.isDefault());
    }

    @Test
    void switchCase_multipleLabels() {
        List<Integer> labels = Arrays.asList(1, 2, 3);
        SwitchCase switchCase = SwitchCase.of(labels, Arrays.asList(createTestStatement()));

        assertEquals(3, switchCase.labels().size());
    }

    @Test
    void switchCase_defaultCase() {
        SwitchCase switchCase = SwitchCase.defaultCase(Arrays.asList(createTestStatement()));

        assertTrue(switchCase.isDefault());
        assertTrue(switchCase.labels().isEmpty());
    }

    @Test
    void switchCase_nullLabels() {
        SwitchCase switchCase = new SwitchCase(null, false, Arrays.asList(createTestStatement()));

        assertNotNull(switchCase.labels());
        assertTrue(switchCase.labels().isEmpty());
    }

    @Test
    void switchCase_nullStatements() {
        SwitchCase switchCase = new SwitchCase(Arrays.asList(1), false, null);

        assertNotNull(switchCase.statements());
        assertTrue(switchCase.statements().isEmpty());
    }

    @Test
    void switchCase_immutableLists() {
        List<Integer> labels = Arrays.asList(1, 2);
        List<Statement> statements = Arrays.asList(createTestStatement());

        SwitchCase switchCase = new SwitchCase(labels, false, statements);

        assertThrows(UnsupportedOperationException.class, () ->
                switchCase.labels().add(3)
        );
        assertThrows(UnsupportedOperationException.class, () ->
                switchCase.statements().add(createTestStatement())
        );
    }

    @Test
    void switchCase_equalsAndHashCode() {
        List<Integer> labels = Arrays.asList(1);
        List<Statement> statements = Arrays.asList(createTestStatement());

        SwitchCase case1 = new SwitchCase(labels, false, statements);
        SwitchCase case2 = new SwitchCase(labels, false, statements);

        assertEquals(case1, case2);
        assertEquals(case1.hashCode(), case2.hashCode());
    }

    @Test
    void switchCase_toString() {
        SwitchCase switchCase = SwitchCase.of(42, Arrays.asList(createTestStatement()));

        String str = switchCase.toString();

        assertTrue(str.contains("SwitchCase"));
        assertTrue(str.contains("42"));
    }

    // ==================== TryCatchStmt Tests ====================

    @Test
    void tryCatchStmt_basicConstructor() {
        Statement tryBlock = createTestStatement();
        CatchClause catchClause = CatchClause.of(
                ReferenceSourceType.OBJECT,
                "e",
                createTestStatement()
        );

        TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, Arrays.asList(catchClause));

        assertNotNull(tryCatch);
        assertEquals(tryBlock, tryCatch.getTryBlock());
        assertEquals(1, tryCatch.getCatches().size());
        assertNull(tryCatch.getFinallyBlock());
        assertFalse(tryCatch.hasFinally());
        assertTrue(tryCatch.hasCatch());
    }

    @Test
    void tryCatchStmt_withFinally() {
        Statement tryBlock = createTestStatement();
        Statement finallyBlock = createTestStatement();

        TryCatchStmt tryCatch = new TryCatchStmt(
                tryBlock,
                Collections.emptyList(),
                finallyBlock
        );

        assertTrue(tryCatch.hasFinally());
        assertEquals(finallyBlock, tryCatch.getFinallyBlock());
        assertFalse(tryCatch.hasCatch());
    }

    @Test
    void tryCatchStmt_withResources() {
        Statement tryBlock = createTestStatement();
        Expression resource = createTestExpression();

        TryCatchStmt tryCatch = new TryCatchStmt(
                tryBlock,
                Collections.emptyList(),
                null,
                Arrays.asList(resource),
                SourceLocation.UNKNOWN
        );

        assertTrue(tryCatch.hasResources());
        assertEquals(1, tryCatch.getResources().size());
    }

    @Test
    void tryCatchStmt_addCatch() {
        TryCatchStmt tryCatch = new TryCatchStmt(
                createTestStatement(),
                Collections.emptyList()
        );

        CatchClause catchClause = CatchClause.of(
                ReferenceSourceType.OBJECT,
                "e",
                createTestStatement()
        );
        tryCatch.addCatch(catchClause);

        assertEquals(1, tryCatch.getCatches().size());
        assertTrue(tryCatch.hasCatch());
    }

    @Test
    void tryCatchStmt_addResource() {
        TryCatchStmt tryCatch = new TryCatchStmt(
                createTestStatement(),
                Collections.emptyList()
        );

        Expression resource = createTestExpression();
        tryCatch.addResource(resource);

        assertEquals(1, tryCatch.getResources().size());
        assertTrue(tryCatch.hasResources());
        assertEquals(tryCatch, resource.getParent());
    }

    @Test
    void tryCatchStmt_nullTryBlockThrows() {
        assertThrows(NullPointerException.class, () ->
                new TryCatchStmt(null, Collections.emptyList())
        );
    }

    @Test
    void tryCatchStmt_nullCatchesAllowed() {
        TryCatchStmt tryCatch = new TryCatchStmt(createTestStatement(), null);

        assertNotNull(tryCatch.getCatches());
        assertTrue(tryCatch.getCatches().isEmpty());
    }

    @Test
    void tryCatchStmt_nullResourcesAllowed() {
        TryCatchStmt tryCatch = new TryCatchStmt(
                createTestStatement(),
                Collections.emptyList(),
                null,
                null,
                SourceLocation.UNKNOWN
        );

        assertNotNull(tryCatch.getResources());
        assertTrue(tryCatch.getResources().isEmpty());
    }

    @Test
    void tryCatchStmt_parentChildRelationships() {
        Statement tryBlock = createTestStatement();
        Statement catchBody = createTestStatement();
        CatchClause catchClause = CatchClause.of(ReferenceSourceType.OBJECT, "e", catchBody);
        Statement finallyBlock = createTestStatement();
        Expression resource = createTestExpression();

        TryCatchStmt tryCatch = new TryCatchStmt(
                tryBlock,
                Arrays.asList(catchClause),
                finallyBlock,
                Arrays.asList(resource),
                SourceLocation.UNKNOWN
        );

        assertEquals(tryCatch, tryBlock.getParent());
        assertEquals(tryCatch, catchBody.getParent());
        assertEquals(tryCatch, finallyBlock.getParent());
        assertEquals(tryCatch, resource.getParent());
    }

    @Test
    void tryCatchStmt_setters() {
        TryCatchStmt tryCatch = new TryCatchStmt(createTestStatement(), Collections.emptyList());

        Statement newTryBlock = new ReturnStmt();
        Statement newFinallyBlock = new ReturnStmt();

        tryCatch.setTryBlock(newTryBlock);
        tryCatch.setFinallyBlock(newFinallyBlock);

        assertEquals(newTryBlock, tryCatch.getTryBlock());
        assertEquals(newFinallyBlock, tryCatch.getFinallyBlock());
    }

    @Test
    void tryCatchStmt_visitorPattern() {
        TryCatchStmt tryCatch = new TryCatchStmt(createTestStatement(), Collections.emptyList());
        TestVisitor visitor = new TestVisitor();

        String result = tryCatch.accept(visitor);

        assertEquals("visitTryCatch", result);
    }

    @Test
    void tryCatchStmt_toStringWithCatch() {
        CatchClause catchClause = CatchClause.of(ReferenceSourceType.OBJECT, "e", createTestStatement());
        TryCatchStmt tryCatch = new TryCatchStmt(
                createTestStatement(),
                Arrays.asList(catchClause)
        );

        String str = tryCatch.toString();

        assertTrue(str.contains("try"));
        assertTrue(str.contains("catch"));
        assertTrue(str.contains("1 handlers"));
    }

    @Test
    void tryCatchStmt_toStringWithFinally() {
        TryCatchStmt tryCatch = new TryCatchStmt(
                createTestStatement(),
                Collections.emptyList(),
                createTestStatement()
        );

        String str = tryCatch.toString();

        assertTrue(str.contains("try"));
        assertTrue(str.contains("finally"));
    }

    @Test
    void tryCatchStmt_toStringWithResources() {
        TryCatchStmt tryCatch = new TryCatchStmt(
                createTestStatement(),
                Collections.emptyList(),
                null,
                Arrays.asList(createTestExpression(), createTestExpression()),
                SourceLocation.UNKNOWN
        );

        String str = tryCatch.toString();

        assertTrue(str.contains("try"));
        assertTrue(str.contains("2 resources"));
    }

    // ==================== CatchClause Tests ====================

    @Test
    void catchClause_basicConstructor() {
        SourceType exceptionType = ReferenceSourceType.OBJECT;
        String variableName = "ex";
        Statement body = createTestStatement();

        CatchClause catchClause = CatchClause.of(exceptionType, variableName, body);

        assertNotNull(catchClause);
        assertEquals(1, catchClause.exceptionTypes().size());
        assertEquals(exceptionType, catchClause.exceptionTypes().get(0));
        assertEquals(variableName, catchClause.variableName());
        assertEquals(body, catchClause.body());
        assertFalse(catchClause.isMultiCatch());
    }

    @Test
    void catchClause_multiCatch() {
        List<SourceType> types = Arrays.asList(
                ReferenceSourceType.OBJECT,
                ReferenceSourceType.STRING
        );
        CatchClause catchClause = CatchClause.multiCatch(types, "ex", createTestStatement());

        assertTrue(catchClause.isMultiCatch());
        assertEquals(2, catchClause.exceptionTypes().size());
    }

    @Test
    void catchClause_getPrimaryType() {
        SourceType primaryType = ReferenceSourceType.OBJECT;
        List<SourceType> types = Arrays.asList(primaryType, ReferenceSourceType.STRING);

        CatchClause catchClause = CatchClause.multiCatch(types, "ex", createTestStatement());

        assertEquals(primaryType, catchClause.getPrimaryType());
    }

    @Test
    void catchClause_nullExceptionTypesThrows() {
        assertThrows(NullPointerException.class, () ->
                new CatchClause(null, "ex", createTestStatement())
        );
    }

    @Test
    void catchClause_emptyExceptionTypesThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new CatchClause(Collections.emptyList(), "ex", createTestStatement())
        );
    }

    @Test
    void catchClause_nullVariableNameThrows() {
        assertThrows(NullPointerException.class, () ->
                CatchClause.of(ReferenceSourceType.OBJECT, null, createTestStatement())
        );
    }

    @Test
    void catchClause_nullBodyThrows() {
        assertThrows(NullPointerException.class, () ->
                CatchClause.of(ReferenceSourceType.OBJECT, "ex", null)
        );
    }

    @Test
    void catchClause_immutableExceptionTypes() {
        List<SourceType> types = Arrays.asList(ReferenceSourceType.OBJECT);
        CatchClause catchClause = CatchClause.of(ReferenceSourceType.OBJECT, "ex", createTestStatement());

        assertThrows(UnsupportedOperationException.class, () ->
                catchClause.exceptionTypes().add(ReferenceSourceType.STRING)
        );
    }

    @Test
    void catchClause_equalsAndHashCode() {
        Statement sharedBody = createTestStatement();
        CatchClause clause1 = CatchClause.of(ReferenceSourceType.OBJECT, "ex", sharedBody);
        CatchClause clause2 = CatchClause.of(ReferenceSourceType.OBJECT, "ex", sharedBody);

        assertEquals(clause1, clause2);
        assertEquals(clause1.hashCode(), clause2.hashCode());
    }

    @Test
    void catchClause_toString() {
        CatchClause catchClause = CatchClause.of(ReferenceSourceType.OBJECT, "ex", createTestStatement());

        String str = catchClause.toString();

        assertTrue(str.contains("CatchClause"));
        assertTrue(str.contains("ex"));
    }

    // ==================== ReturnStmt Tests ====================

    @Test
    void returnStmt_voidReturn() {
        ReturnStmt returnStmt = new ReturnStmt();

        assertNotNull(returnStmt);
        assertNull(returnStmt.getValue());
        assertTrue(returnStmt.isVoidReturn());
    }

    @Test
    void returnStmt_withValue() {
        Expression value = LiteralExpr.ofInt(42);

        ReturnStmt returnStmt = new ReturnStmt(value);

        assertEquals(value, returnStmt.getValue());
        assertFalse(returnStmt.isVoidReturn());
    }

    @Test
    void returnStmt_withLocation() {
        Expression value = LiteralExpr.ofInt(42);
        SourceLocation location = createTestLocation();

        ReturnStmt returnStmt = new ReturnStmt(value, location);

        assertEquals(location, returnStmt.getLocation());
    }

    @Test
    void returnStmt_parentChildRelationship() {
        Expression value = createTestExpression();

        ReturnStmt returnStmt = new ReturnStmt(value);

        assertEquals(returnStmt, value.getParent());
    }

    @Test
    void returnStmt_setter() {
        ReturnStmt returnStmt = new ReturnStmt();

        Expression newValue = LiteralExpr.ofInt(100);
        returnStmt.setValue(newValue);

        assertEquals(newValue, returnStmt.getValue());
        assertFalse(returnStmt.isVoidReturn());
    }

    @Test
    void returnStmt_visitorPattern() {
        ReturnStmt returnStmt = new ReturnStmt(LiteralExpr.ofInt(42));
        TestVisitor visitor = new TestVisitor();

        String result = returnStmt.accept(visitor);

        assertEquals("visitReturn", result);
    }

    @Test
    void returnStmt_toStringVoid() {
        ReturnStmt returnStmt = new ReturnStmt();

        String str = returnStmt.toString();

        assertEquals("return", str);
    }

    @Test
    void returnStmt_toStringWithValue() {
        ReturnStmt returnStmt = new ReturnStmt(LiteralExpr.ofInt(42));

        String str = returnStmt.toString();

        assertTrue(str.contains("return"));
        assertTrue(str.contains("42"));
    }

    // ==================== ThrowStmt Tests ====================

    @Test
    void throwStmt_basicConstructor() {
        Expression exception = createTestExpression();

        ThrowStmt throwStmt = new ThrowStmt(exception);

        assertNotNull(throwStmt);
        assertEquals(exception, throwStmt.getException());
    }

    @Test
    void throwStmt_withLocation() {
        Expression exception = createTestExpression();
        SourceLocation location = createTestLocation();

        ThrowStmt throwStmt = new ThrowStmt(exception, location);

        assertEquals(location, throwStmt.getLocation());
    }

    @Test
    void throwStmt_nullExceptionThrows() {
        assertThrows(NullPointerException.class, () ->
                new ThrowStmt(null)
        );
    }

    @Test
    void throwStmt_parentChildRelationship() {
        Expression exception = createTestExpression();

        ThrowStmt throwStmt = new ThrowStmt(exception);

        assertEquals(throwStmt, exception.getParent());
    }

    @Test
    void throwStmt_setter() {
        ThrowStmt throwStmt = new ThrowStmt(createTestExpression());

        Expression newException = LiteralExpr.ofString("error");
        throwStmt.setException(newException);

        assertEquals(newException, throwStmt.getException());
    }

    @Test
    void throwStmt_visitorPattern() {
        ThrowStmt throwStmt = new ThrowStmt(createTestExpression());
        TestVisitor visitor = new TestVisitor();

        String result = throwStmt.accept(visitor);

        assertEquals("visitThrow", result);
    }

    @Test
    void throwStmt_toString() {
        ThrowStmt throwStmt = new ThrowStmt(LiteralExpr.ofString("error"));

        String str = throwStmt.toString();

        assertTrue(str.contains("throw"));
        assertTrue(str.contains("error"));
    }

    // ==================== BreakStmt Tests ====================

    @Test
    void breakStmt_unlabeled() {
        BreakStmt breakStmt = new BreakStmt();

        assertNotNull(breakStmt);
        assertNull(breakStmt.getTargetLabel());
        assertFalse(breakStmt.hasLabel());
    }

    @Test
    void breakStmt_labeled() {
        String label = "loopLabel";

        BreakStmt breakStmt = new BreakStmt(label);

        assertEquals(label, breakStmt.getTargetLabel());
        assertTrue(breakStmt.hasLabel());
    }

    @Test
    void breakStmt_withLocation() {
        String label = "loopLabel";
        SourceLocation location = createTestLocation();

        BreakStmt breakStmt = new BreakStmt(label, location);

        assertEquals(location, breakStmt.getLocation());
    }

    @Test
    void breakStmt_setter() {
        BreakStmt breakStmt = new BreakStmt();

        String newLabel = "newLabel";
        breakStmt.setTargetLabel(newLabel);

        assertEquals(newLabel, breakStmt.getTargetLabel());
        assertTrue(breakStmt.hasLabel());
    }

    @Test
    void breakStmt_visitorPattern() {
        BreakStmt breakStmt = new BreakStmt("label");
        TestVisitor visitor = new TestVisitor();

        String result = breakStmt.accept(visitor);

        assertEquals("visitBreak", result);
    }

    @Test
    void breakStmt_toStringUnlabeled() {
        BreakStmt breakStmt = new BreakStmt();

        String str = breakStmt.toString();

        assertEquals("break", str);
    }

    @Test
    void breakStmt_toStringLabeled() {
        BreakStmt breakStmt = new BreakStmt("loopLabel");

        String str = breakStmt.toString();

        assertEquals("break loopLabel", str);
    }

    // ==================== ContinueStmt Tests ====================

    @Test
    void continueStmt_unlabeled() {
        ContinueStmt continueStmt = new ContinueStmt();

        assertNotNull(continueStmt);
        assertNull(continueStmt.getTargetLabel());
        assertFalse(continueStmt.hasLabel());
    }

    @Test
    void continueStmt_labeled() {
        String label = "loopLabel";

        ContinueStmt continueStmt = new ContinueStmt(label);

        assertEquals(label, continueStmt.getTargetLabel());
        assertTrue(continueStmt.hasLabel());
    }

    @Test
    void continueStmt_withLocation() {
        String label = "loopLabel";
        SourceLocation location = createTestLocation();

        ContinueStmt continueStmt = new ContinueStmt(label, location);

        assertEquals(location, continueStmt.getLocation());
    }

    @Test
    void continueStmt_setter() {
        ContinueStmt continueStmt = new ContinueStmt();

        String newLabel = "newLabel";
        continueStmt.setTargetLabel(newLabel);

        assertEquals(newLabel, continueStmt.getTargetLabel());
        assertTrue(continueStmt.hasLabel());
    }

    @Test
    void continueStmt_visitorPattern() {
        ContinueStmt continueStmt = new ContinueStmt("label");
        TestVisitor visitor = new TestVisitor();

        String result = continueStmt.accept(visitor);

        assertEquals("visitContinue", result);
    }

    @Test
    void continueStmt_toStringUnlabeled() {
        ContinueStmt continueStmt = new ContinueStmt();

        String str = continueStmt.toString();

        assertEquals("continue", str);
    }

    @Test
    void continueStmt_toStringLabeled() {
        ContinueStmt continueStmt = new ContinueStmt("loopLabel");

        String str = continueStmt.toString();

        assertEquals("continue loopLabel", str);
    }

    // ==================== BlockStmt Tests ====================

    @Test
    void blockStmt_emptyConstructor() {
        BlockStmt blockStmt = new BlockStmt();

        assertNotNull(blockStmt);
        assertTrue(blockStmt.isEmpty());
        assertEquals(0, blockStmt.size());
    }

    @Test
    void blockStmt_withStatements() {
        List<Statement> statements = Arrays.asList(
                createTestStatement(),
                createTestStatement()
        );

        BlockStmt blockStmt = new BlockStmt(statements);

        assertEquals(2, blockStmt.size());
        assertFalse(blockStmt.isEmpty());
    }

    @Test
    void blockStmt_filtersNullStatements() {
        List<Statement> statements = Arrays.asList(
                createTestStatement(),
                null,
                createTestStatement()
        );

        BlockStmt blockStmt = new BlockStmt(statements);

        assertEquals(2, blockStmt.size());
    }

    @Test
    void blockStmt_addStatement() {
        BlockStmt blockStmt = new BlockStmt();
        Statement stmt = createTestStatement();

        blockStmt.addStatement(stmt);

        assertEquals(1, blockStmt.size());
        assertEquals(blockStmt, stmt.getParent());
    }

    @Test
    void blockStmt_addNullStatementIgnored() {
        BlockStmt blockStmt = new BlockStmt();

        blockStmt.addStatement(null);

        assertEquals(0, blockStmt.size());
    }

    @Test
    void blockStmt_insertStatement() {
        BlockStmt blockStmt = new BlockStmt();
        Statement stmt1 = new ReturnStmt(LiteralExpr.ofInt(1));
        Statement stmt2 = new ReturnStmt(LiteralExpr.ofInt(2));
        Statement stmt3 = new ReturnStmt(LiteralExpr.ofInt(3));

        blockStmt.addStatement(stmt1);
        blockStmt.addStatement(stmt3);
        blockStmt.insertStatement(1, stmt2);

        assertEquals(3, blockStmt.size());
        assertEquals(stmt2, blockStmt.getStatements().get(1));
        assertEquals(blockStmt, stmt2.getParent());
    }

    @Test
    void blockStmt_insertNullStatementIgnored() {
        BlockStmt blockStmt = new BlockStmt();
        blockStmt.addStatement(createTestStatement());

        blockStmt.insertStatement(0, null);

        assertEquals(1, blockStmt.size());
    }

    @Test
    void blockStmt_removeStatement() {
        Statement stmt = createTestStatement();
        BlockStmt blockStmt = new BlockStmt(Arrays.asList(stmt));

        boolean removed = blockStmt.removeStatement(stmt);

        assertTrue(removed);
        assertTrue(blockStmt.isEmpty());
    }

    @Test
    void blockStmt_removeNonExistentStatement() {
        BlockStmt blockStmt = new BlockStmt(Arrays.asList(createTestStatement()));
        Statement otherStmt = createTestStatement();

        boolean removed = blockStmt.removeStatement(otherStmt);

        assertFalse(removed);
        assertEquals(1, blockStmt.size());
    }

    @Test
    void blockStmt_parentChildRelationships() {
        Statement stmt1 = createTestStatement();
        Statement stmt2 = createTestStatement();

        BlockStmt blockStmt = new BlockStmt(Arrays.asList(stmt1, stmt2));

        assertEquals(blockStmt, stmt1.getParent());
        assertEquals(blockStmt, stmt2.getParent());
    }

    @Test
    void blockStmt_withLocation() {
        SourceLocation location = createTestLocation();

        BlockStmt blockStmt = new BlockStmt(Collections.emptyList(), location);

        assertEquals(location, blockStmt.getLocation());
    }

    @Test
    void blockStmt_visitorPattern() {
        BlockStmt blockStmt = new BlockStmt();
        TestVisitor visitor = new TestVisitor();

        String result = blockStmt.accept(visitor);

        assertEquals("visitBlock", result);
    }

    @Test
    void blockStmt_toString() {
        BlockStmt blockStmt = new BlockStmt(Arrays.asList(
                createTestStatement(),
                createTestStatement(),
                createTestStatement()
        ));

        String str = blockStmt.toString();

        assertTrue(str.contains("{"));
        assertTrue(str.contains("3 statements"));
        assertTrue(str.contains("}"));
    }

    // ==================== ExprStmt Tests ====================

    @Test
    void exprStmt_basicConstructor() {
        Expression expression = createTestExpression();

        ExprStmt exprStmt = new ExprStmt(expression);

        assertNotNull(exprStmt);
        assertEquals(expression, exprStmt.getExpression());
    }

    @Test
    void exprStmt_withLocation() {
        Expression expression = createTestExpression();
        SourceLocation location = createTestLocation();

        ExprStmt exprStmt = new ExprStmt(expression, location);

        assertEquals(location, exprStmt.getLocation());
    }

    @Test
    void exprStmt_nullExpressionThrows() {
        assertThrows(NullPointerException.class, () ->
                new ExprStmt(null)
        );
    }

    @Test
    void exprStmt_parentChildRelationship() {
        Expression expression = createTestExpression();

        ExprStmt exprStmt = new ExprStmt(expression);

        assertEquals(exprStmt, expression.getParent());
    }

    @Test
    void exprStmt_setter() {
        ExprStmt exprStmt = new ExprStmt(createTestExpression());

        Expression newExpression = LiteralExpr.ofInt(999);
        exprStmt.setExpression(newExpression);

        assertEquals(newExpression, exprStmt.getExpression());
    }

    @Test
    void exprStmt_visitorPattern() {
        ExprStmt exprStmt = new ExprStmt(createTestExpression());
        TestVisitor visitor = new TestVisitor();

        String result = exprStmt.accept(visitor);

        assertEquals("visitExprStmt", result);
    }

    @Test
    void exprStmt_toString() {
        ExprStmt exprStmt = new ExprStmt(LiteralExpr.ofInt(42));

        String str = exprStmt.toString();

        assertTrue(str.contains("42"));
        assertTrue(str.endsWith(";"));
    }

    // ==================== VarDeclStmt Tests ====================

    @Test
    void varDeclStmt_basicConstructor() {
        SourceType type = PrimitiveSourceType.INT;
        String name = "x";

        VarDeclStmt varDecl = new VarDeclStmt(type, name);

        assertNotNull(varDecl);
        assertEquals(type, varDecl.getType());
        assertEquals(name, varDecl.getName());
        assertNull(varDecl.getInitializer());
        assertFalse(varDecl.hasInitializer());
        assertFalse(varDecl.isUseVarKeyword());
        assertFalse(varDecl.isFinal());
    }

    @Test
    void varDeclStmt_withInitializer() {
        SourceType type = PrimitiveSourceType.INT;
        String name = "x";
        Expression initializer = LiteralExpr.ofInt(42);

        VarDeclStmt varDecl = new VarDeclStmt(type, name, initializer);

        assertEquals(initializer, varDecl.getInitializer());
        assertTrue(varDecl.hasInitializer());
    }

    @Test
    void varDeclStmt_withVarKeyword() {
        SourceType type = PrimitiveSourceType.INT;
        String name = "x";
        Expression initializer = LiteralExpr.ofInt(42);

        VarDeclStmt varDecl = VarDeclStmt.withVar(type, name, initializer);

        assertTrue(varDecl.isUseVarKeyword());
        assertEquals(type, varDecl.getType());
        assertEquals(initializer, varDecl.getInitializer());
    }

    @Test
    void varDeclStmt_finalModifier() {
        VarDeclStmt varDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "x",
                LiteralExpr.ofInt(42),
                false,
                true,
                SourceLocation.UNKNOWN
        );

        assertTrue(varDecl.isFinal());
    }

    @Test
    void varDeclStmt_nullTypeThrows() {
        assertThrows(NullPointerException.class, () ->
                new VarDeclStmt(null, "x")
        );
    }

    @Test
    void varDeclStmt_nullNameThrows() {
        assertThrows(NullPointerException.class, () ->
                new VarDeclStmt(PrimitiveSourceType.INT, null)
        );
    }

    @Test
    void varDeclStmt_parentChildRelationship() {
        Expression initializer = createTestExpression();

        VarDeclStmt varDecl = new VarDeclStmt(PrimitiveSourceType.INT, "x", initializer);

        assertEquals(varDecl, initializer.getParent());
    }

    @Test
    void varDeclStmt_setters() {
        VarDeclStmt varDecl = new VarDeclStmt(PrimitiveSourceType.INT, "x");

        SourceType newType = PrimitiveSourceType.LONG;
        String newName = "y";
        Expression newInitializer = LiteralExpr.ofLong(100L);

        varDecl.setType(newType);
        varDecl.setName(newName);
        varDecl.setInitializer(newInitializer);
        varDecl.setUseVarKeyword(true);
        varDecl.setFinal(true);

        assertEquals(newType, varDecl.getType());
        assertEquals(newName, varDecl.getName());
        assertEquals(newInitializer, varDecl.getInitializer());
        assertTrue(varDecl.isUseVarKeyword());
        assertTrue(varDecl.isFinal());
    }

    @Test
    void varDeclStmt_visitorPattern() {
        VarDeclStmt varDecl = new VarDeclStmt(PrimitiveSourceType.INT, "x");
        TestVisitor visitor = new TestVisitor();

        String result = varDecl.accept(visitor);

        assertEquals("visitVarDecl", result);
    }

    @Test
    void varDeclStmt_toStringWithoutInitializer() {
        VarDeclStmt varDecl = new VarDeclStmt(PrimitiveSourceType.INT, "x");

        String str = varDecl.toString();

        assertTrue(str.contains("int"));
        assertTrue(str.contains("x"));
        assertFalse(str.contains("="));
    }

    @Test
    void varDeclStmt_toStringWithInitializer() {
        VarDeclStmt varDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "x",
                LiteralExpr.ofInt(42)
        );

        String str = varDecl.toString();

        assertTrue(str.contains("int"));
        assertTrue(str.contains("x"));
        assertTrue(str.contains("="));
        assertTrue(str.contains("42"));
    }

    @Test
    void varDeclStmt_toStringWithFinal() {
        VarDeclStmt varDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "x",
                LiteralExpr.ofInt(42),
                false,
                true,
                SourceLocation.UNKNOWN
        );

        String str = varDecl.toString();

        assertTrue(str.contains("final"));
    }

    @Test
    void varDeclStmt_toStringWithVar() {
        VarDeclStmt varDecl = VarDeclStmt.withVar(
                PrimitiveSourceType.INT,
                "x",
                LiteralExpr.ofInt(42)
        );

        String str = varDecl.toString();

        assertTrue(str.contains("var"));
        assertFalse(str.contains("int"));
    }

    // ==================== SynchronizedStmt Tests ====================

    @Test
    void synchronizedStmt_basicConstructor() {
        Expression lock = createTestExpression();
        Statement body = createTestStatement();

        SynchronizedStmt syncStmt = new SynchronizedStmt(lock, body);

        assertNotNull(syncStmt);
        assertEquals(lock, syncStmt.getLock());
        assertEquals(body, syncStmt.getBody());
    }

    @Test
    void synchronizedStmt_withLocation() {
        Expression lock = createTestExpression();
        Statement body = createTestStatement();
        SourceLocation location = createTestLocation();

        SynchronizedStmt syncStmt = new SynchronizedStmt(lock, body, location);

        assertEquals(location, syncStmt.getLocation());
    }

    @Test
    void synchronizedStmt_nullLockThrows() {
        assertThrows(NullPointerException.class, () ->
                new SynchronizedStmt(null, createTestStatement())
        );
    }

    @Test
    void synchronizedStmt_nullBodyThrows() {
        assertThrows(NullPointerException.class, () ->
                new SynchronizedStmt(createTestExpression(), null)
        );
    }

    @Test
    void synchronizedStmt_parentChildRelationships() {
        Expression lock = createTestExpression();
        Statement body = createTestStatement();

        SynchronizedStmt syncStmt = new SynchronizedStmt(lock, body);

        assertEquals(syncStmt, lock.getParent());
        assertEquals(syncStmt, body.getParent());
    }

    @Test
    void synchronizedStmt_setters() {
        SynchronizedStmt syncStmt = new SynchronizedStmt(createTestExpression(), createTestStatement());

        Expression newLock = LiteralExpr.ofString("lock");
        Statement newBody = new ReturnStmt();

        syncStmt.setLock(newLock);
        syncStmt.setBody(newBody);

        assertEquals(newLock, syncStmt.getLock());
        assertEquals(newBody, syncStmt.getBody());
    }

    @Test
    void synchronizedStmt_visitorPattern() {
        SynchronizedStmt syncStmt = new SynchronizedStmt(createTestExpression(), createTestStatement());
        TestVisitor visitor = new TestVisitor();

        String result = syncStmt.accept(visitor);

        assertEquals("visitSynchronized", result);
    }

    @Test
    void synchronizedStmt_toString() {
        SynchronizedStmt syncStmt = new SynchronizedStmt(
                LiteralExpr.ofString("lock"),
                createTestStatement()
        );

        String str = syncStmt.toString();

        assertTrue(str.contains("synchronized"));
        assertTrue(str.contains("lock"));
        assertTrue(str.contains("{"));
        assertTrue(str.contains("}"));
    }

    // ==================== LabeledStmt Tests ====================

    @Test
    void labeledStmt_basicConstructor() {
        String label = "myLabel";
        Statement statement = createTestStatement();

        LabeledStmt labeledStmt = new LabeledStmt(label, statement);

        assertNotNull(labeledStmt);
        assertEquals(label, labeledStmt.getLabel());
        assertEquals(statement, labeledStmt.getStatement());
    }

    @Test
    void labeledStmt_withLocation() {
        String label = "myLabel";
        Statement statement = createTestStatement();
        SourceLocation location = createTestLocation();

        LabeledStmt labeledStmt = new LabeledStmt(label, statement, location);

        assertEquals(location, labeledStmt.getLocation());
    }

    @Test
    void labeledStmt_nullLabelThrows() {
        assertThrows(NullPointerException.class, () ->
                new LabeledStmt(null, createTestStatement())
        );
    }

    @Test
    void labeledStmt_nullStatementThrows() {
        assertThrows(NullPointerException.class, () ->
                new LabeledStmt("label", null)
        );
    }

    @Test
    void labeledStmt_parentChildRelationship() {
        String label = "myLabel";
        Statement statement = createTestStatement();

        LabeledStmt labeledStmt = new LabeledStmt(label, statement);

        assertEquals(labeledStmt, statement.getParent());
    }

    @Test
    void labeledStmt_setter() {
        LabeledStmt labeledStmt = new LabeledStmt("label", createTestStatement());

        Statement newStatement = new ReturnStmt(LiteralExpr.ofInt(42));
        labeledStmt.setStatement(newStatement);

        assertEquals(newStatement, labeledStmt.getStatement());
    }

    @Test
    void labeledStmt_visitorPattern() {
        LabeledStmt labeledStmt = new LabeledStmt("label", createTestStatement());
        TestVisitor visitor = new TestVisitor();

        String result = labeledStmt.accept(visitor);

        assertEquals("visitLabeled", result);
    }

    @Test
    void labeledStmt_toString() {
        LabeledStmt labeledStmt = new LabeledStmt("myLabel", new ReturnStmt());

        String str = labeledStmt.toString();

        assertTrue(str.contains("myLabel:"));
        assertTrue(str.contains("return"));
    }

    // ==================== Statement Interface Tests ====================

    @Test
    void statement_defaultLabelIsNull() {
        Statement stmt = new ReturnStmt();

        assertNull(stmt.getLabel());
    }

    @Test
    void statement_labelForWhileLoop() {
        WhileStmt whileStmt = new WhileStmt(createTestExpression(), createTestStatement(), "loop");

        assertEquals("loop", whileStmt.getLabel());
    }

    @Test
    void statement_labelForForLoop() {
        ForStmt forStmt = new ForStmt(
                Collections.emptyList(),
                createTestExpression(),
                Collections.emptyList(),
                createTestStatement(),
                "loop",
                SourceLocation.UNKNOWN
        );

        assertEquals("loop", forStmt.getLabel());
    }

    @Test
    void statement_labelForDoWhileLoop() {
        DoWhileStmt doWhileStmt = new DoWhileStmt(
                createTestStatement(),
                createTestExpression(),
                "loop"
        );

        assertEquals("loop", doWhileStmt.getLabel());
    }

    @Test
    void statement_labelForForEachLoop() {
        ForEachStmt forEachStmt = new ForEachStmt(
                new VarDeclStmt(PrimitiveSourceType.INT, "item"),
                createTestExpression(),
                createTestStatement(),
                "loop",
                SourceLocation.UNKNOWN
        );

        assertEquals("loop", forEachStmt.getLabel());
    }

    @Test
    void statement_labelForLabeledStmt() {
        LabeledStmt labeledStmt = new LabeledStmt("myLabel", createTestStatement());

        assertEquals("myLabel", labeledStmt.getLabel());
    }
}
