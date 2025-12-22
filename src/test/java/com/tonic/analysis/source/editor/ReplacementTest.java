package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Replacement class and its inner classes.
 * Tests all replacement types: Keep, Remove, ExprReplacement, StmtReplacement,
 * BlockReplacement, InsertBeforeReplacement, InsertAfterReplacement.
 */
class ReplacementTest {

    // ========== Helper Methods ==========

    private static Expression createTestExpression() {
        return LiteralExpr.ofInt(42);
    }

    private static Expression createTestExpression(String name) {
        return new VarRefExpr(name, ReferenceSourceType.OBJECT);
    }

    private static Statement createTestStatement() {
        return new ReturnStmt(LiteralExpr.ofInt(1));
    }

    private static Statement createTestStatement(int value) {
        return new ReturnStmt(LiteralExpr.ofInt(value));
    }

    // ========== Type Enum Tests ==========

    @Test
    void typeEnumHasAllValues() {
        Replacement.Type[] types = Replacement.Type.values();
        assertEquals(7, types.length);

        // Verify all expected types exist
        assertEquals(Replacement.Type.KEEP, Replacement.Type.valueOf("KEEP"));
        assertEquals(Replacement.Type.REPLACE_EXPR, Replacement.Type.valueOf("REPLACE_EXPR"));
        assertEquals(Replacement.Type.REPLACE_STMT, Replacement.Type.valueOf("REPLACE_STMT"));
        assertEquals(Replacement.Type.REPLACE_BLOCK, Replacement.Type.valueOf("REPLACE_BLOCK"));
        assertEquals(Replacement.Type.REMOVE, Replacement.Type.valueOf("REMOVE"));
        assertEquals(Replacement.Type.INSERT_BEFORE, Replacement.Type.valueOf("INSERT_BEFORE"));
        assertEquals(Replacement.Type.INSERT_AFTER, Replacement.Type.valueOf("INSERT_AFTER"));
    }

    // ========== Keep Replacement Tests ==========

    @Test
    void keepReturnsInstance() {
        Replacement r = Replacement.keep();
        assertNotNull(r);
    }

    @Test
    void keepReturnsSameInstance() {
        Replacement r1 = Replacement.keep();
        Replacement r2 = Replacement.keep();
        assertSame(r1, r2);
    }

    @Test
    void keepHasCorrectType() {
        Replacement r = Replacement.keep();
        assertEquals(Replacement.Type.KEEP, r.getType());
    }

    @Test
    void keepIsKeepReturnsTrue() {
        Replacement r = Replacement.keep();
        assertTrue(r.isKeep());
    }

    @Test
    void keepIsReplaceReturnsFalse() {
        Replacement r = Replacement.keep();
        assertFalse(r.isReplace());
    }

    @Test
    void keepIsRemoveReturnsFalse() {
        Replacement r = Replacement.keep();
        assertFalse(r.isRemove());
    }

    @Test
    void keepIsInsertReturnsFalse() {
        Replacement r = Replacement.keep();
        assertFalse(r.isInsert());
    }

    @Test
    void keepGetExpressionReturnsNull() {
        Replacement r = Replacement.keep();
        assertNull(r.getExpression());
    }

    @Test
    void keepGetStatementReturnsNull() {
        Replacement r = Replacement.keep();
        assertNull(r.getStatement());
    }

    @Test
    void keepGetStatementsReturnsEmptyList() {
        Replacement r = Replacement.keep();
        List<Statement> stmts = r.getStatements();
        assertNotNull(stmts);
        assertTrue(stmts.isEmpty());
    }

    @Test
    void keepToString() {
        Replacement r = Replacement.keep();
        assertEquals("Replacement.keep()", r.toString());
    }

    // ========== Remove Replacement Tests ==========

    @Test
    void removeReturnsInstance() {
        Replacement r = Replacement.remove();
        assertNotNull(r);
    }

    @Test
    void removeReturnsSameInstance() {
        Replacement r1 = Replacement.remove();
        Replacement r2 = Replacement.remove();
        assertSame(r1, r2);
    }

    @Test
    void removeHasCorrectType() {
        Replacement r = Replacement.remove();
        assertEquals(Replacement.Type.REMOVE, r.getType());
    }

    @Test
    void removeIsRemoveReturnsTrue() {
        Replacement r = Replacement.remove();
        assertTrue(r.isRemove());
    }

    @Test
    void removeIsKeepReturnsFalse() {
        Replacement r = Replacement.remove();
        assertFalse(r.isKeep());
    }

    @Test
    void removeIsReplaceReturnsFalse() {
        Replacement r = Replacement.remove();
        assertFalse(r.isReplace());
    }

    @Test
    void removeIsInsertReturnsFalse() {
        Replacement r = Replacement.remove();
        assertFalse(r.isInsert());
    }

    @Test
    void removeGetExpressionReturnsNull() {
        Replacement r = Replacement.remove();
        assertNull(r.getExpression());
    }

    @Test
    void removeGetStatementReturnsNull() {
        Replacement r = Replacement.remove();
        assertNull(r.getStatement());
    }

    @Test
    void removeGetStatementsReturnsEmptyList() {
        Replacement r = Replacement.remove();
        List<Statement> stmts = r.getStatements();
        assertNotNull(stmts);
        assertTrue(stmts.isEmpty());
    }

    @Test
    void removeToString() {
        Replacement r = Replacement.remove();
        assertEquals("Replacement.remove()", r.toString());
    }

    // ========== Expression Replacement Tests ==========

    @Test
    void withExpressionCreatesReplacement() {
        Expression expr = createTestExpression();
        Replacement r = Replacement.with(expr);
        assertNotNull(r);
    }

    @Test
    void withExpressionHasCorrectType() {
        Expression expr = createTestExpression();
        Replacement r = Replacement.with(expr);
        assertEquals(Replacement.Type.REPLACE_EXPR, r.getType());
    }

    @Test
    void withExpressionIsReplaceReturnsTrue() {
        Expression expr = createTestExpression();
        Replacement r = Replacement.with(expr);
        assertTrue(r.isReplace());
    }

    @Test
    void withExpressionGetExpressionReturnsExpression() {
        Expression expr = createTestExpression();
        Replacement r = Replacement.with(expr);
        assertSame(expr, r.getExpression());
    }

    @Test
    void withExpressionGetStatementReturnsNull() {
        Expression expr = createTestExpression();
        Replacement r = Replacement.with(expr);
        assertNull(r.getStatement());
    }

    @Test
    void withExpressionGetStatementsReturnsEmptyList() {
        Expression expr = createTestExpression();
        Replacement r = Replacement.with(expr);
        List<Statement> stmts = r.getStatements();
        assertNotNull(stmts);
        assertTrue(stmts.isEmpty());
    }

    @Test
    void withNullExpressionThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.with((Expression) null);
        });
        assertEquals("Replacement expression cannot be null", e.getMessage());
    }

    @Test
    void withExpressionToString() {
        Expression expr = LiteralExpr.ofInt(42);
        Replacement r = Replacement.with(expr);
        String str = r.toString();
        assertTrue(str.contains("Replacement.with("));
        assertTrue(str.contains("42"));
    }

    // ========== Statement Replacement Tests ==========

    @Test
    void withStatementCreatesReplacement() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.with(stmt);
        assertNotNull(r);
    }

    @Test
    void withStatementHasCorrectType() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.with(stmt);
        assertEquals(Replacement.Type.REPLACE_STMT, r.getType());
    }

    @Test
    void withStatementIsReplaceReturnsTrue() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.with(stmt);
        assertTrue(r.isReplace());
    }

    @Test
    void withStatementGetStatementReturnsStatement() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.with(stmt);
        assertSame(stmt, r.getStatement());
    }

    @Test
    void withStatementGetExpressionReturnsNull() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.with(stmt);
        assertNull(r.getExpression());
    }

    @Test
    void withStatementGetStatementsReturnsEmptyList() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.with(stmt);
        List<Statement> stmts = r.getStatements();
        assertNotNull(stmts);
        assertTrue(stmts.isEmpty());
    }

    @Test
    void withNullStatementThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.with((Statement) null);
        });
        assertEquals("Replacement statement cannot be null", e.getMessage());
    }

    @Test
    void withStatementToString() {
        Statement stmt = new ReturnStmt(LiteralExpr.ofInt(5));
        Replacement r = Replacement.with(stmt);
        String str = r.toString();
        assertTrue(str.contains("Replacement.with("));
    }

    // ========== Block Replacement Tests (varargs) ==========

    @Test
    void withBlockVarargsCreatesReplacement() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Replacement r = Replacement.withBlock(stmt1, stmt2);
        assertNotNull(r);
    }

    @Test
    void withBlockVarargsHasCorrectType() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Replacement r = Replacement.withBlock(stmt1, stmt2);
        assertEquals(Replacement.Type.REPLACE_BLOCK, r.getType());
    }

    @Test
    void withBlockVarargsIsReplaceReturnsTrue() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Replacement r = Replacement.withBlock(stmt1, stmt2);
        assertTrue(r.isReplace());
    }

    @Test
    void withBlockVarargsGetStatementsReturnsStatements() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Replacement r = Replacement.withBlock(stmt1, stmt2);
        List<Statement> stmts = r.getStatements();
        assertEquals(2, stmts.size());
        assertSame(stmt1, stmts.get(0));
        assertSame(stmt2, stmts.get(1));
    }

    @Test
    void withBlockVarargsGetExpressionReturnsNull() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.withBlock(stmt);
        assertNull(r.getExpression());
    }

    @Test
    void withBlockVarargsGetStatementReturnsNull() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.withBlock(stmt);
        assertNull(r.getStatement());
    }

    @Test
    void withBlockVarargsSingleStatement() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.withBlock(stmt);
        List<Statement> stmts = r.getStatements();
        assertEquals(1, stmts.size());
        assertSame(stmt, stmts.get(0));
    }

    @Test
    void withBlockVarargsMultipleStatements() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Statement stmt3 = createTestStatement(3);
        Replacement r = Replacement.withBlock(stmt1, stmt2, stmt3);
        List<Statement> stmts = r.getStatements();
        assertEquals(3, stmts.size());
    }

    @Test
    void withBlockVarargsNullThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.withBlock((Statement[]) null);
        });
        assertEquals("Block replacement requires at least one statement", e.getMessage());
    }

    @Test
    void withBlockVarargsEmptyThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.withBlock(new Statement[0]);
        });
        assertEquals("Block replacement requires at least one statement", e.getMessage());
    }

    @Test
    void withBlockVarargsToString() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Replacement r = Replacement.withBlock(stmt1, stmt2);
        assertEquals("Replacement.withBlock(2 statements)", r.toString());
    }

    // ========== Block Replacement Tests (List) ==========

    @Test
    void withBlockListCreatesReplacement() {
        List<Statement> stmts = Arrays.asList(createTestStatement(1), createTestStatement(2));
        Replacement r = Replacement.withBlock(stmts);
        assertNotNull(r);
    }

    @Test
    void withBlockListHasCorrectType() {
        List<Statement> stmts = Arrays.asList(createTestStatement(1), createTestStatement(2));
        Replacement r = Replacement.withBlock(stmts);
        assertEquals(Replacement.Type.REPLACE_BLOCK, r.getType());
    }

    @Test
    void withBlockListGetStatementsReturnsStatements() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        List<Statement> stmts = Arrays.asList(stmt1, stmt2);
        Replacement r = Replacement.withBlock(stmts);
        List<Statement> result = r.getStatements();
        assertEquals(2, result.size());
        assertSame(stmt1, result.get(0));
        assertSame(stmt2, result.get(1));
    }

    @Test
    void withBlockListNullThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.withBlock((List<Statement>) null);
        });
        assertEquals("Block replacement requires at least one statement", e.getMessage());
    }

    @Test
    void withBlockListEmptyThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.withBlock(Collections.emptyList());
        });
        assertEquals("Block replacement requires at least one statement", e.getMessage());
    }

    @Test
    void withBlockListToString() {
        List<Statement> stmts = Arrays.asList(createTestStatement(1), createTestStatement(2), createTestStatement(3));
        Replacement r = Replacement.withBlock(stmts);
        assertEquals("Replacement.withBlock(3 statements)", r.toString());
    }

    // ========== Insert Before Replacement Tests (varargs) ==========

    @Test
    void insertBeforeVarargsCreatesReplacement() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Replacement r = Replacement.insertBefore(stmt1, stmt2);
        assertNotNull(r);
    }

    @Test
    void insertBeforeVarargsHasCorrectType() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.insertBefore(stmt);
        assertEquals(Replacement.Type.INSERT_BEFORE, r.getType());
    }

    @Test
    void insertBeforeVarargsIsInsertReturnsTrue() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.insertBefore(stmt);
        assertTrue(r.isInsert());
    }

    @Test
    void insertBeforeVarargsGetStatementsReturnsStatements() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Replacement r = Replacement.insertBefore(stmt1, stmt2);
        List<Statement> stmts = r.getStatements();
        assertEquals(2, stmts.size());
        assertSame(stmt1, stmts.get(0));
        assertSame(stmt2, stmts.get(1));
    }

    @Test
    void insertBeforeVarargsGetExpressionReturnsNull() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.insertBefore(stmt);
        assertNull(r.getExpression());
    }

    @Test
    void insertBeforeVarargsGetStatementReturnsNull() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.insertBefore(stmt);
        assertNull(r.getStatement());
    }

    @Test
    void insertBeforeVarargsNullThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.insertBefore((Statement[]) null);
        });
        assertEquals("Insert requires at least one statement", e.getMessage());
    }

    @Test
    void insertBeforeVarargsEmptyThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.insertBefore(new Statement[0]);
        });
        assertEquals("Insert requires at least one statement", e.getMessage());
    }

    @Test
    void insertBeforeVarargsToString() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Replacement r = Replacement.insertBefore(stmt1, stmt2);
        assertEquals("Replacement.insertBefore(2 statements)", r.toString());
    }

    // ========== Insert Before Replacement Tests (List) ==========

    @Test
    void insertBeforeListCreatesReplacement() {
        List<Statement> stmts = Arrays.asList(createTestStatement(1), createTestStatement(2));
        Replacement r = Replacement.insertBefore(stmts);
        assertNotNull(r);
    }

    @Test
    void insertBeforeListHasCorrectType() {
        List<Statement> stmts = Arrays.asList(createTestStatement());
        Replacement r = Replacement.insertBefore(stmts);
        assertEquals(Replacement.Type.INSERT_BEFORE, r.getType());
    }

    @Test
    void insertBeforeListGetStatementsReturnsStatements() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        List<Statement> stmts = Arrays.asList(stmt1, stmt2);
        Replacement r = Replacement.insertBefore(stmts);
        List<Statement> result = r.getStatements();
        assertEquals(2, result.size());
        assertSame(stmt1, result.get(0));
        assertSame(stmt2, result.get(1));
    }

    @Test
    void insertBeforeListNullThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.insertBefore((List<Statement>) null);
        });
        assertEquals("Insert requires at least one statement", e.getMessage());
    }

    @Test
    void insertBeforeListEmptyThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.insertBefore(Collections.emptyList());
        });
        assertEquals("Insert requires at least one statement", e.getMessage());
    }

    @Test
    void insertBeforeListToString() {
        List<Statement> stmts = Arrays.asList(createTestStatement(1), createTestStatement(2), createTestStatement(3));
        Replacement r = Replacement.insertBefore(stmts);
        assertEquals("Replacement.insertBefore(3 statements)", r.toString());
    }

    // ========== Insert After Replacement Tests (varargs) ==========

    @Test
    void insertAfterVarargsCreatesReplacement() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Replacement r = Replacement.insertAfter(stmt1, stmt2);
        assertNotNull(r);
    }

    @Test
    void insertAfterVarargsHasCorrectType() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.insertAfter(stmt);
        assertEquals(Replacement.Type.INSERT_AFTER, r.getType());
    }

    @Test
    void insertAfterVarargsIsInsertReturnsTrue() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.insertAfter(stmt);
        assertTrue(r.isInsert());
    }

    @Test
    void insertAfterVarargsGetStatementsReturnsStatements() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Replacement r = Replacement.insertAfter(stmt1, stmt2);
        List<Statement> stmts = r.getStatements();
        assertEquals(2, stmts.size());
        assertSame(stmt1, stmts.get(0));
        assertSame(stmt2, stmts.get(1));
    }

    @Test
    void insertAfterVarargsGetExpressionReturnsNull() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.insertAfter(stmt);
        assertNull(r.getExpression());
    }

    @Test
    void insertAfterVarargsGetStatementReturnsNull() {
        Statement stmt = createTestStatement();
        Replacement r = Replacement.insertAfter(stmt);
        assertNull(r.getStatement());
    }

    @Test
    void insertAfterVarargsNullThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.insertAfter((Statement[]) null);
        });
        assertEquals("Insert requires at least one statement", e.getMessage());
    }

    @Test
    void insertAfterVarargsEmptyThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.insertAfter(new Statement[0]);
        });
        assertEquals("Insert requires at least one statement", e.getMessage());
    }

    @Test
    void insertAfterVarargsToString() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Replacement r = Replacement.insertAfter(stmt1, stmt2);
        assertEquals("Replacement.insertAfter(2 statements)", r.toString());
    }

    // ========== Insert After Replacement Tests (List) ==========

    @Test
    void insertAfterListCreatesReplacement() {
        List<Statement> stmts = Arrays.asList(createTestStatement(1), createTestStatement(2));
        Replacement r = Replacement.insertAfter(stmts);
        assertNotNull(r);
    }

    @Test
    void insertAfterListHasCorrectType() {
        List<Statement> stmts = Arrays.asList(createTestStatement());
        Replacement r = Replacement.insertAfter(stmts);
        assertEquals(Replacement.Type.INSERT_AFTER, r.getType());
    }

    @Test
    void insertAfterListGetStatementsReturnsStatements() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        List<Statement> stmts = Arrays.asList(stmt1, stmt2);
        Replacement r = Replacement.insertAfter(stmts);
        List<Statement> result = r.getStatements();
        assertEquals(2, result.size());
        assertSame(stmt1, result.get(0));
        assertSame(stmt2, result.get(1));
    }

    @Test
    void insertAfterListNullThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.insertAfter((List<Statement>) null);
        });
        assertEquals("Insert requires at least one statement", e.getMessage());
    }

    @Test
    void insertAfterListEmptyThrowsException() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Replacement.insertAfter(Collections.emptyList());
        });
        assertEquals("Insert requires at least one statement", e.getMessage());
    }

    @Test
    void insertAfterListToString() {
        List<Statement> stmts = Arrays.asList(createTestStatement(1), createTestStatement(2), createTestStatement(3));
        Replacement r = Replacement.insertAfter(stmts);
        assertEquals("Replacement.insertAfter(3 statements)", r.toString());
    }

    // ========== Type Classification Tests ==========

    @Test
    void isReplaceReturnsTrueForExprReplacement() {
        Replacement r = Replacement.with(createTestExpression());
        assertTrue(r.isReplace());
        assertFalse(r.isKeep());
        assertFalse(r.isRemove());
        assertFalse(r.isInsert());
    }

    @Test
    void isReplaceReturnsTrueForStmtReplacement() {
        Replacement r = Replacement.with(createTestStatement());
        assertTrue(r.isReplace());
        assertFalse(r.isKeep());
        assertFalse(r.isRemove());
        assertFalse(r.isInsert());
    }

    @Test
    void isReplaceReturnsTrueForBlockReplacement() {
        Replacement r = Replacement.withBlock(createTestStatement());
        assertTrue(r.isReplace());
        assertFalse(r.isKeep());
        assertFalse(r.isRemove());
        assertFalse(r.isInsert());
    }

    @Test
    void isInsertReturnsTrueForInsertBefore() {
        Replacement r = Replacement.insertBefore(createTestStatement());
        assertTrue(r.isInsert());
        assertFalse(r.isKeep());
        assertFalse(r.isRemove());
        assertFalse(r.isReplace());
    }

    @Test
    void isInsertReturnsTrueForInsertAfter() {
        Replacement r = Replacement.insertAfter(createTestStatement());
        assertTrue(r.isInsert());
        assertFalse(r.isKeep());
        assertFalse(r.isRemove());
        assertFalse(r.isReplace());
    }

    // ========== Edge Cases and Integration Tests ==========

    @Test
    void multipleReplacementsWithDifferentTypes() {
        Expression expr = createTestExpression();
        Statement stmt = createTestStatement();

        Replacement r1 = Replacement.with(expr);
        Replacement r2 = Replacement.with(stmt);
        Replacement r3 = Replacement.keep();
        Replacement r4 = Replacement.remove();

        // Verify they're all different objects (except singletons)
        assertNotSame(r1, r2);
        assertNotSame(r1, r3);
        assertNotSame(r1, r4);
        assertNotSame(r2, r3);
        assertNotSame(r2, r4);
        assertSame(r3, Replacement.keep());
        assertSame(r4, Replacement.remove());
    }

    @Test
    void replacementWithComplexExpression() {
        // Create a variable reference expression
        VarRefExpr var = new VarRefExpr("myVariable", PrimitiveSourceType.INT);
        Replacement r = Replacement.with(var);

        assertEquals(Replacement.Type.REPLACE_EXPR, r.getType());
        assertSame(var, r.getExpression());
    }

    @Test
    void replacementWithComplexStatement() {
        // Create an expression statement
        ExprStmt exprStmt = new ExprStmt(LiteralExpr.ofString("test"));
        Replacement r = Replacement.with(exprStmt);

        assertEquals(Replacement.Type.REPLACE_STMT, r.getType());
        assertSame(exprStmt, r.getStatement());
    }

    @Test
    void blockReplacementWithMixedStatements() {
        ReturnStmt returnStmt = new ReturnStmt(LiteralExpr.ofInt(1));
        ExprStmt exprStmt = new ExprStmt(LiteralExpr.ofString("test"));

        Replacement r = Replacement.withBlock(returnStmt, exprStmt);
        List<Statement> stmts = r.getStatements();

        assertEquals(2, stmts.size());
        assertSame(returnStmt, stmts.get(0));
        assertSame(exprStmt, stmts.get(1));
    }

    @Test
    void insertReplacementWithMultipleStatements() {
        Statement stmt1 = createTestStatement(1);
        Statement stmt2 = createTestStatement(2);
        Statement stmt3 = createTestStatement(3);

        Replacement before = Replacement.insertBefore(stmt1, stmt2, stmt3);
        Replacement after = Replacement.insertAfter(stmt1, stmt2, stmt3);

        assertEquals(3, before.getStatements().size());
        assertEquals(3, after.getStatements().size());
    }

    @Test
    void verifyTypeGetterForAllReplacements() {
        assertEquals(Replacement.Type.KEEP, Replacement.keep().getType());
        assertEquals(Replacement.Type.REMOVE, Replacement.remove().getType());
        assertEquals(Replacement.Type.REPLACE_EXPR, Replacement.with(createTestExpression()).getType());
        assertEquals(Replacement.Type.REPLACE_STMT, Replacement.with(createTestStatement()).getType());
        assertEquals(Replacement.Type.REPLACE_BLOCK, Replacement.withBlock(createTestStatement()).getType());
        assertEquals(Replacement.Type.INSERT_BEFORE, Replacement.insertBefore(createTestStatement()).getType());
        assertEquals(Replacement.Type.INSERT_AFTER, Replacement.insertAfter(createTestStatement()).getType());
    }
}
