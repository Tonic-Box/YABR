package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.editor.util.ASTFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for EditorContext.
 * Covers context initialization, scope management, null-check tracking,
 * and all public methods.
 */
class EditorContextTest {

    private ASTFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ASTFactory();
    }

    // ========== Constructor and Initialization Tests ==========

    @Test
    void createContextWithFullParameters() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "testMethod", "(II)V", "com/example/Test");

        assertEquals(body, ctx.getMethodBody());
        assertEquals("testMethod", ctx.getMethodName());
        assertEquals("(II)V", ctx.getMethodDescriptor());
        assertEquals("com/example/Test", ctx.getOwnerClass());
        assertNotNull(ctx.factory());
    }

    @Test
    void createContextWithNullMethodName() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, null, "(II)V", "com/example/Test");

        assertEquals("<unknown>", ctx.getMethodName());
    }

    @Test
    void createContextWithNullMethodDescriptor() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "testMethod", null, "com/example/Test");

        assertEquals("()V", ctx.getMethodDescriptor());
    }

    @Test
    void createContextWithNullOwnerClass() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "testMethod", "(II)V", null);

        assertEquals("<unknown>", ctx.getOwnerClass());
    }

    @Test
    void createContextWithAllNullMetadata() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, null, null, null);

        assertEquals("<unknown>", ctx.getMethodName());
        assertEquals("()V", ctx.getMethodDescriptor());
        assertEquals("<unknown>", ctx.getOwnerClass());
    }

    @Test
    void initialStateHasZeroDepths() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        assertFalse(ctx.isInTryBlock());
        assertFalse(ctx.isInLoop());
        assertFalse(ctx.isInConditional());
    }

    @Test
    void initialStateHasEmptyCheckedVariables() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        Set<String> visible = ctx.getVisibleVariables();
        assertNotNull(visible);
        assertTrue(visible.isEmpty());
    }

    // ========== Method Metadata Tests ==========

    @Test
    void getMethodBodyReturnsCorrectBlock() {
        BlockStmt body = factory.block(factory.returnVoid());
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        assertSame(body, ctx.getMethodBody());
    }

    @Test
    void getMethodNameReturnsCorrectName() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "myMethod", "()V", "com/example/Test");

        assertEquals("myMethod", ctx.getMethodName());
    }

    @Test
    void getMethodDescriptorReturnsCorrectDescriptor() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "(Ljava/lang/String;I)Z", "com/example/Test");

        assertEquals("(Ljava/lang/String;I)Z", ctx.getMethodDescriptor());
    }

    @Test
    void getOwnerClassReturnsCorrectClass() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/MyClass");

        assertEquals("com/example/MyClass", ctx.getOwnerClass());
    }

    // ========== Current Statement Management Tests ==========

    @Test
    void currentStatementInitiallyNull() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        assertNull(ctx.getCurrentStatement());
    }

    @Test
    void setAndGetCurrentStatement() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ReturnStmt stmt = factory.returnVoid();
        ctx.setCurrentStatement(stmt);

        assertSame(stmt, ctx.getCurrentStatement());
    }

    @Test
    void setCurrentStatementToNull() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.setCurrentStatement(factory.returnVoid());
        ctx.setCurrentStatement(null);

        assertNull(ctx.getCurrentStatement());
    }

    @Test
    void setMultipleCurrentStatements() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ReturnStmt stmt1 = factory.returnVoid();
        ThrowStmt stmt2 = factory.throwStmt(factory.variable("ex"));

        ctx.setCurrentStatement(stmt1);
        assertSame(stmt1, ctx.getCurrentStatement());

        ctx.setCurrentStatement(stmt2);
        assertSame(stmt2, ctx.getCurrentStatement());
    }

    // ========== Enclosing Block Management Tests ==========

    @Test
    void enclosingBlockInitiallyNull() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        assertNull(ctx.getEnclosingBlock());
    }

    @Test
    void setAndGetEnclosingBlock() {
        BlockStmt body = factory.block();
        BlockStmt enclosing = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.setEnclosingBlock(enclosing);

        assertSame(enclosing, ctx.getEnclosingBlock());
    }

    @Test
    void setEnclosingBlockToNull() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.setEnclosingBlock(factory.block());
        ctx.setEnclosingBlock(null);

        assertNull(ctx.getEnclosingBlock());
    }

    // ========== Statement Index Tests ==========

    @Test
    void statementIndexInitiallyZero() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        assertEquals(0, ctx.getStatementIndex());
    }

    @Test
    void setAndGetStatementIndex() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.setStatementIndex(5);
        assertEquals(5, ctx.getStatementIndex());

        ctx.setStatementIndex(0);
        assertEquals(0, ctx.getStatementIndex());

        ctx.setStatementIndex(100);
        assertEquals(100, ctx.getStatementIndex());
    }

    @Test
    void setNegativeStatementIndex() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.setStatementIndex(-1);
        assertEquals(-1, ctx.getStatementIndex());
    }

    // ========== Try Block Scope Tests ==========

    @Test
    void enterTryIncrementsDepth() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        assertFalse(ctx.isInTryBlock());

        ctx.enterTry();
        assertTrue(ctx.isInTryBlock());
    }

    @Test
    void exitTryDecrementsDepth() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.enterTry();
        assertTrue(ctx.isInTryBlock());

        ctx.exitTry();
        assertFalse(ctx.isInTryBlock());
    }

    @Test
    void nestedTryBlocks() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.enterTry();
        assertTrue(ctx.isInTryBlock());

        ctx.enterTry();
        assertTrue(ctx.isInTryBlock());

        ctx.exitTry();
        assertTrue(ctx.isInTryBlock());

        ctx.exitTry();
        assertFalse(ctx.isInTryBlock());
    }

    @Test
    void exitTryBelowZeroStaysAtZero() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.exitTry();
        assertFalse(ctx.isInTryBlock());

        ctx.exitTry();
        assertFalse(ctx.isInTryBlock());
    }

    // ========== Loop Scope Tests ==========

    @Test
    void enterLoopIncrementsDepth() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        assertFalse(ctx.isInLoop());

        ctx.enterLoop();
        assertTrue(ctx.isInLoop());
    }

    @Test
    void exitLoopDecrementsDepth() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.enterLoop();
        assertTrue(ctx.isInLoop());

        ctx.exitLoop();
        assertFalse(ctx.isInLoop());
    }

    @Test
    void nestedLoops() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.enterLoop();
        assertTrue(ctx.isInLoop());

        ctx.enterLoop();
        assertTrue(ctx.isInLoop());

        ctx.enterLoop();
        assertTrue(ctx.isInLoop());

        ctx.exitLoop();
        assertTrue(ctx.isInLoop());

        ctx.exitLoop();
        assertTrue(ctx.isInLoop());

        ctx.exitLoop();
        assertFalse(ctx.isInLoop());
    }

    @Test
    void exitLoopBelowZeroStaysAtZero() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.exitLoop();
        assertFalse(ctx.isInLoop());

        ctx.exitLoop();
        assertFalse(ctx.isInLoop());
    }

    // ========== Conditional Scope Tests ==========

    @Test
    void enterConditionalIncrementsDepth() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        assertFalse(ctx.isInConditional());

        ctx.enterConditional();
        assertTrue(ctx.isInConditional());
    }

    @Test
    void exitConditionalDecrementsDepth() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.enterConditional();
        assertTrue(ctx.isInConditional());

        ctx.exitConditional();
        assertFalse(ctx.isInConditional());
    }

    @Test
    void nestedConditionals() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.enterConditional();
        assertTrue(ctx.isInConditional());

        ctx.enterConditional();
        assertTrue(ctx.isInConditional());

        ctx.exitConditional();
        assertTrue(ctx.isInConditional());

        ctx.exitConditional();
        assertFalse(ctx.isInConditional());
    }

    @Test
    void exitConditionalBelowZeroStaysAtZero() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.exitConditional();
        assertFalse(ctx.isInConditional());

        ctx.exitConditional();
        assertFalse(ctx.isInConditional());
    }

    // ========== Mixed Scope Tests ==========

    @Test
    void multipleScopeTypesIndependent() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.enterTry();
        ctx.enterLoop();
        ctx.enterConditional();

        assertTrue(ctx.isInTryBlock());
        assertTrue(ctx.isInLoop());
        assertTrue(ctx.isInConditional());

        ctx.exitConditional();
        assertTrue(ctx.isInTryBlock());
        assertTrue(ctx.isInLoop());
        assertFalse(ctx.isInConditional());

        ctx.exitLoop();
        assertTrue(ctx.isInTryBlock());
        assertFalse(ctx.isInLoop());
        assertFalse(ctx.isInConditional());

        ctx.exitTry();
        assertFalse(ctx.isInTryBlock());
        assertFalse(ctx.isInLoop());
        assertFalse(ctx.isInConditional());
    }

    // ========== Null-Check Tracking Tests ==========

    @Test
    void markVariableAsNullChecked() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.markNullChecked("myVar");

        Set<String> visible = ctx.getVisibleVariables();
        assertTrue(visible.contains("myVar"));
    }

    @Test
    void markMultipleVariablesAsNullChecked() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.markNullChecked("var1");
        ctx.markNullChecked("var2");
        ctx.markNullChecked("var3");

        Set<String> visible = ctx.getVisibleVariables();
        assertEquals(3, visible.size());
        assertTrue(visible.contains("var1"));
        assertTrue(visible.contains("var2"));
        assertTrue(visible.contains("var3"));
    }

    @Test
    void markNullCheckedWithNullName() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.markNullChecked(null);

        Set<String> visible = ctx.getVisibleVariables();
        assertTrue(visible.isEmpty());
    }

    @Test
    void markSameVariableTwice() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.markNullChecked("myVar");
        ctx.markNullChecked("myVar");

        Set<String> visible = ctx.getVisibleVariables();
        assertEquals(1, visible.size());
        assertTrue(visible.contains("myVar"));
    }

    @Test
    void clearNullChecksRemovesAllVariables() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.markNullChecked("var1");
        ctx.markNullChecked("var2");
        ctx.markNullChecked("var3");

        ctx.clearNullChecks();

        Set<String> visible = ctx.getVisibleVariables();
        assertTrue(visible.isEmpty());
    }

    @Test
    void clearNullChecksOnEmptySet() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.clearNullChecks();

        Set<String> visible = ctx.getVisibleVariables();
        assertTrue(visible.isEmpty());
    }

    @Test
    void isNullCheckedForVarRefExpr() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        VarRefExpr varRef = factory.variable("myVar");
        assertFalse(ctx.isNullChecked(varRef));

        ctx.markNullChecked("myVar");
        assertTrue(ctx.isNullChecked(varRef));
    }

    @Test
    void isNullCheckedForNonVarRefExpr() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        LiteralExpr literal = factory.intLiteral(42);
        assertFalse(ctx.isNullChecked(literal));

        // Even if we mark something, literals are never null-checked
        ctx.markNullChecked("someVar");
        assertFalse(ctx.isNullChecked(literal));
    }

    @Test
    void isNullCheckedForNullExpression() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        assertFalse(ctx.isNullChecked(null));
    }

    @Test
    void isNullCheckedForMethodCall() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        MethodCallExpr call = factory.methodCall("test").on("com/example/Test").build();
        assertFalse(ctx.isNullChecked(call));
    }

    // ========== Visible Variables Tests ==========

    @Test
    void getVisibleVariablesReturnsNewSet() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.markNullChecked("var1");

        Set<String> visible1 = ctx.getVisibleVariables();
        Set<String> visible2 = ctx.getVisibleVariables();

        assertNotSame(visible1, visible2);
        assertEquals(visible1, visible2);
    }

    @Test
    void modifyingReturnedSetDoesNotAffectContext() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.markNullChecked("var1");

        Set<String> visible = ctx.getVisibleVariables();
        visible.add("var2");

        Set<String> visible2 = ctx.getVisibleVariables();
        assertEquals(1, visible2.size());
        assertTrue(visible2.contains("var1"));
        assertFalse(visible2.contains("var2"));
    }

    // ========== Factory Access Tests ==========

    @Test
    void factoryReturnsNonNullInstance() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        assertNotNull(ctx.factory());
    }

    @Test
    void factoryCanCreateNodes() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ASTFactory f = ctx.factory();
        ReturnStmt stmt = f.returnStmt(f.intLiteral(42));

        assertNotNull(stmt);
        assertNotNull(stmt.getValue());
    }

    // ========== Replacement Helper Tests ==========

    @Test
    void insertBeforeCreatesCorrectReplacement() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        Statement stmt1 = factory.returnVoid();
        Statement stmt2 = factory.throwStmt(factory.variable("ex"));

        Replacement replacement = ctx.insertBefore(stmt1, stmt2);

        assertNotNull(replacement);
        assertEquals(Replacement.Type.INSERT_BEFORE, replacement.getType());
        assertEquals(2, replacement.getStatements().size());
    }

    @Test
    void insertAfterCreatesCorrectReplacement() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        Statement stmt = factory.returnVoid();

        Replacement replacement = ctx.insertAfter(stmt);

        assertNotNull(replacement);
        assertEquals(Replacement.Type.INSERT_AFTER, replacement.getType());
        assertEquals(1, replacement.getStatements().size());
    }

    // ========== Wrap With Null Check Tests ==========

    @Test
    void wrapWithNullCheckMarksVariableAsChecked() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        VarRefExpr varRef = factory.variable("myVar");

        assertFalse(ctx.isNullChecked(varRef));

        Replacement replacement = ctx.wrapWithNullCheck(varRef);

        assertTrue(ctx.isNullChecked(varRef));
        assertEquals(Replacement.Type.KEEP, replacement.getType());
    }

    @Test
    void wrapWithNullCheckOnNonVariable() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        LiteralExpr literal = factory.intLiteral(42);

        Replacement replacement = ctx.wrapWithNullCheck(literal);

        assertEquals(Replacement.Type.KEEP, replacement.getType());
        assertTrue(ctx.getVisibleVariables().isEmpty());
    }

    @Test
    void wrapWithNullCheckOnNull() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        Replacement replacement = ctx.wrapWithNullCheck(null);

        assertEquals(Replacement.Type.KEEP, replacement.getType());
    }

    // ========== Find Enclosing Statement Tests ==========

    @Test
    void findEnclosingStatementForExpressionInStatement() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        VarRefExpr varRef = factory.variable("x");
        ReturnStmt returnStmt = factory.returnStmt(varRef);

        Statement enclosing = ctx.findEnclosingStatement(varRef);

        assertSame(returnStmt, enclosing);
    }

    @Test
    void findEnclosingStatementForNestedExpression() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        VarRefExpr varRef = factory.variable("x");
        BinaryExpr binary = factory.add(varRef, factory.intLiteral(1), PrimitiveSourceType.INT);
        ReturnStmt returnStmt = factory.returnStmt(binary);

        Statement enclosing = ctx.findEnclosingStatement(varRef);

        assertSame(returnStmt, enclosing);
    }

    @Test
    void findEnclosingStatementForNullExpression() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        Statement enclosing = ctx.findEnclosingStatement(null);

        assertNull(enclosing);
    }

    @Test
    void findEnclosingStatementForExpressionWithoutParent() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        // Create standalone expression not attached to any statement
        VarRefExpr varRef = factory.variable("x");

        Statement enclosing = ctx.findEnclosingStatement(varRef);

        assertNull(enclosing);
    }

    // ========== Nested Context Tests ==========

    @Test
    void createNestedContextCopiesMetadata() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "testMethod", "(II)Z", "com/example/Test");

        EditorContext nested = ctx.createNestedContext();

        assertSame(body, nested.getMethodBody());
        assertEquals("testMethod", nested.getMethodName());
        assertEquals("(II)Z", nested.getMethodDescriptor());
        assertEquals("com/example/Test", nested.getOwnerClass());
    }

    @Test
    void createNestedContextCopiesScopeDepths() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.enterTry();
        ctx.enterTry();
        ctx.enterLoop();
        ctx.enterConditional();

        EditorContext nested = ctx.createNestedContext();

        assertTrue(nested.isInTryBlock());
        assertTrue(nested.isInLoop());
        assertTrue(nested.isInConditional());
    }

    @Test
    void createNestedContextCopiesNullCheckedVariables() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.markNullChecked("var1");
        ctx.markNullChecked("var2");

        EditorContext nested = ctx.createNestedContext();

        Set<String> visible = nested.getVisibleVariables();
        assertEquals(2, visible.size());
        assertTrue(visible.contains("var1"));
        assertTrue(visible.contains("var2"));
    }

    @Test
    void nestedContextIsIndependent() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.markNullChecked("var1");

        EditorContext nested = ctx.createNestedContext();

        // Modify nested context
        nested.markNullChecked("var2");
        nested.enterTry();

        // Original context should be unchanged
        Set<String> visible = ctx.getVisibleVariables();
        assertEquals(1, visible.size());
        assertTrue(visible.contains("var1"));
        assertFalse(visible.contains("var2"));
        assertFalse(ctx.isInTryBlock());

        // Nested context should have both changes
        Set<String> nestedVisible = nested.getVisibleVariables();
        assertEquals(2, nestedVisible.size());
        assertTrue(nestedVisible.contains("var1"));
        assertTrue(nestedVisible.contains("var2"));
        assertTrue(nested.isInTryBlock());
    }

    @Test
    void nestedContextDoesNotCopyCurrentStatement() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        ctx.setCurrentStatement(factory.returnVoid());
        ctx.setEnclosingBlock(factory.block());
        ctx.setStatementIndex(5);

        EditorContext nested = ctx.createNestedContext();

        // These should not be copied
        assertNull(nested.getCurrentStatement());
        assertNull(nested.getEnclosingBlock());
        assertEquals(0, nested.getStatementIndex());
    }

    // ========== Integration Tests ==========

    @Test
    void fullWorkflowSimulation() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "processData", "(Ljava/lang/String;)I", "com/example/Processor");

        // Simulate entering nested structures
        ctx.enterConditional();
        ctx.enterTry();
        ctx.enterLoop();

        // Set current position
        ReturnStmt stmt = factory.returnStmt(factory.intLiteral(0));
        ctx.setCurrentStatement(stmt);
        ctx.setEnclosingBlock(body);
        ctx.setStatementIndex(3);

        // Track null-checked variables
        ctx.markNullChecked("input");
        ctx.markNullChecked("result");

        // Verify all state
        assertEquals("processData", ctx.getMethodName());
        assertTrue(ctx.isInConditional());
        assertTrue(ctx.isInTryBlock());
        assertTrue(ctx.isInLoop());
        assertSame(stmt, ctx.getCurrentStatement());
        assertSame(body, ctx.getEnclosingBlock());
        assertEquals(3, ctx.getStatementIndex());

        Set<String> visible = ctx.getVisibleVariables();
        assertEquals(2, visible.size());
        assertTrue(visible.contains("input"));
        assertTrue(visible.contains("result"));

        // Create nested context
        EditorContext nested = ctx.createNestedContext();
        nested.markNullChecked("temp");

        // Verify nested has parent state + its own
        assertTrue(nested.isInTryBlock());
        assertEquals(3, nested.getVisibleVariables().size());

        // Exit scopes in original
        ctx.exitLoop();
        ctx.exitTry();
        ctx.exitConditional();

        assertFalse(ctx.isInLoop());
        assertFalse(ctx.isInTryBlock());
        assertFalse(ctx.isInConditional());

        // Nested should still have original depth
        assertTrue(nested.isInLoop());
        assertTrue(nested.isInTryBlock());
        assertTrue(nested.isInConditional());
    }

    @Test
    void complexNullCheckTracking() {
        BlockStmt body = factory.block();
        EditorContext ctx = new EditorContext(body, "test", "()V", "com/example/Test");

        // Create variables
        VarRefExpr var1 = factory.variable("obj1");
        VarRefExpr var2 = factory.variable("obj2");
        VarRefExpr var3 = factory.variable("obj3");

        // Initially none are checked
        assertFalse(ctx.isNullChecked(var1));
        assertFalse(ctx.isNullChecked(var2));
        assertFalse(ctx.isNullChecked(var3));

        // Check some variables
        ctx.wrapWithNullCheck(var1);
        ctx.markNullChecked("obj3");

        assertTrue(ctx.isNullChecked(var1));
        assertFalse(ctx.isNullChecked(var2));
        assertTrue(ctx.isNullChecked(var3));

        // Clear and verify
        ctx.clearNullChecks();

        assertFalse(ctx.isNullChecked(var1));
        assertFalse(ctx.isNullChecked(var2));
        assertFalse(ctx.isNullChecked(var3));
    }
}
