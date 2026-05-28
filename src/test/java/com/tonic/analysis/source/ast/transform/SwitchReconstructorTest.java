package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.NewExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.BreakStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.SwitchCase;
import com.tonic.analysis.source.ast.stmt.SwitchStmt;
import com.tonic.analysis.source.ast.stmt.ThrowStmt;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwitchReconstructorTest {

    private final SwitchReconstructor transform = new SwitchReconstructor();

    // ── helpers ──────────────────────────────────────────────────────────────

    private VarRefExpr v() {
        return new VarRefExpr("v", PrimitiveSourceType.INT);
    }

    /** {@code v != c} */
    private Expression ne(int c) {
        return new BinaryExpr(BinaryOperator.NE, v(), LiteralExpr.ofInt(c), PrimitiveSourceType.BOOLEAN);
    }

    /** {@code v == c} */
    private Expression eq(int c) {
        return new BinaryExpr(BinaryOperator.EQ, v(), LiteralExpr.ofInt(c), PrimitiveSourceType.BOOLEAN);
    }

    /** {@code c == v} (constant on the left) */
    private Expression eqReversed(int c) {
        return new BinaryExpr(BinaryOperator.EQ, LiteralExpr.ofInt(c), v(), PrimitiveSourceType.BOOLEAN);
    }

    /** A recognizable handler: {@code int hN = N;} */
    private VarDeclStmt handler(int n) {
        return new VarDeclStmt(PrimitiveSourceType.INT, "h" + n, LiteralExpr.ofInt(n));
    }

    private BlockStmt block(Statement... stmts) {
        List<Statement> list = new ArrayList<>();
        for (Statement s : stmts) list.add(s);
        return new BlockStmt(list);
    }

    private ThrowStmt throwISE() {
        return new ThrowStmt(new NewExpr("java/lang/IllegalStateException",
                new ArrayList<>(), new ReferenceSourceType("IllegalStateException")));
    }

    private BlockStmt root(Statement s) {
        return block(s);
    }

    private int handlerValue(SwitchCase c) {
        assertEquals(1, c.statements().size(), "expected single-statement handler");
        VarDeclStmt d = (VarDeclStmt) c.statements().get(0);
        return (Integer) ((LiteralExpr) d.getInitializer()).getValue();
    }

    // ── positive cases ─────────────────────────────────────────────────────────

    @Test
    void foldsNotEqualCascadeWithDefault() {
        // if (v != 0) { if (v != 1) { if (v != 2) { default } else { h2 } } else { h1 } } else { h0 }
        IfStmt if2 = new IfStmt(ne(2), block(handler(99)), block(handler(2)));
        IfStmt if1 = new IfStmt(ne(1), block(if2), block(handler(1)));
        IfStmt if0 = new IfStmt(ne(0), block(if1), block(handler(0)));
        BlockStmt body = root(if0);

        assertTrue(transform.transform(body));
        assertEquals(1, body.getStatements().size());
        SwitchStmt sw = (SwitchStmt) body.getStatements().get(0);

        assertEquals(4, sw.getCaseCount());
        assertEquals(List.of(0), sw.getCases().get(0).labels());
        assertEquals(List.of(1), sw.getCases().get(1).labels());
        assertEquals(List.of(2), sw.getCases().get(2).labels());
        assertEquals(0, handlerValue(sw.getCases().get(0)));
        assertEquals(1, handlerValue(sw.getCases().get(1)));
        assertEquals(2, handlerValue(sw.getCases().get(2)));
        assertTrue(sw.hasDefault());
        assertEquals(99, handlerValue(sw.getDefaultCase()));
    }

    @Test
    void foldsEqualFormCascade() {
        // if (v == 0) { h0 } else if (v == 1) { h1 } else if (v == 2) { h2 } else { default }
        IfStmt if2 = new IfStmt(eq(2), block(handler(2)), block(handler(99)));
        IfStmt if1 = new IfStmt(eq(1), block(handler(1)), block(if2));
        IfStmt if0 = new IfStmt(eq(0), block(handler(0)), block(if1));
        BlockStmt body = root(if0);

        assertTrue(transform.transform(body));
        SwitchStmt sw = (SwitchStmt) body.getStatements().get(0);
        assertEquals(4, sw.getCaseCount());
        assertEquals(0, handlerValue(sw.getCases().get(0)));
        assertEquals(1, handlerValue(sw.getCases().get(1)));
        assertEquals(2, handlerValue(sw.getCases().get(2)));
        assertEquals(99, handlerValue(sw.getDefaultCase()));
    }

    @Test
    void handlesReversedOperandOrder() {
        // mixes "c == v" with "v == c"
        IfStmt if2 = new IfStmt(eqReversed(2), block(handler(2)), block(handler(99)));
        IfStmt if1 = new IfStmt(eq(1), block(handler(1)), block(if2));
        IfStmt if0 = new IfStmt(eqReversed(0), block(handler(0)), block(if1));
        BlockStmt body = root(if0);

        assertTrue(transform.transform(body));
        SwitchStmt sw = (SwitchStmt) body.getStatements().get(0);
        assertEquals(4, sw.getCaseCount());
        assertEquals(0, handlerValue(sw.getCases().get(0)));
        assertEquals(2, handlerValue(sw.getCases().get(2)));
    }

    @Test
    void handlesGuardFormTerminal() {
        // if (v != 0) { if (v != 1) { if (v != 2) { throw } h2... } else { h1 } } else { h0 }
        // innermost is guard-form: if (v != 2) { throw }  h2  (h2 trails the guard, no else)
        BlockStmt innermost = block(new IfStmt(ne(2), throwISE()), handler(2));
        IfStmt if1 = new IfStmt(ne(1), innermost, block(handler(1)));
        IfStmt if0 = new IfStmt(ne(0), block(if1), block(handler(0)));
        BlockStmt body = root(if0);

        assertTrue(transform.transform(body));
        SwitchStmt sw = (SwitchStmt) body.getStatements().get(0);
        assertEquals(List.of(0), sw.getCases().get(0).labels());
        assertEquals(List.of(1), sw.getCases().get(1).labels());
        assertEquals(List.of(2), sw.getCases().get(2).labels());
        assertEquals(2, handlerValue(sw.getCases().get(2)));
        assertTrue(sw.hasDefault());
        assertTrue(sw.getDefaultCase().statements().get(0) instanceof ThrowStmt);
    }

    // ── negative cases (must NOT fold) ──────────────────────────────────────────

    @Test
    void doesNotFoldSequentialReassigningIfs() {
        // if (v == 0) {h0}  if (v == 1) {h1}  if (v == 2) {h2}   — siblings, no else
        BlockStmt body = block(
                new IfStmt(eq(0), block(handler(0))),
                new IfStmt(eq(1), block(handler(1))),
                new IfStmt(eq(2), block(handler(2))));

        assertFalse(transform.transform(body));
        assertEquals(3, body.getStatements().size());
        for (Statement s : body.getStatements()) {
            assertTrue(s instanceof IfStmt);
        }
    }

    @Test
    void doesNotFoldWhenHandlerHasLoopBreak() {
        // a valid 3-cascade, but one handler contains a bare break (would retarget to the switch)
        IfStmt if2 = new IfStmt(ne(2), block(handler(99)), block(handler(2)));
        IfStmt if1 = new IfStmt(ne(1), block(if2), block(new BreakStmt()));
        IfStmt if0 = new IfStmt(ne(0), block(if1), block(handler(0)));
        BlockStmt body = root(if0);

        assertFalse(transform.transform(body));
        assertTrue(body.getStatements().get(0) instanceof IfStmt);
    }

    @Test
    void doesNotFoldFewerThanThreeCases() {
        // only two cases
        IfStmt if1 = new IfStmt(ne(1), block(handler(99)), block(handler(1)));
        IfStmt if0 = new IfStmt(ne(0), block(if1), block(handler(0)));
        BlockStmt body = root(if0);

        assertFalse(transform.transform(body));
        assertTrue(body.getStatements().get(0) instanceof IfStmt);
    }

    @Test
    void allowsContinueInHandler() {
        // continue is never captured by a switch, so a handler containing continue is still foldable
        IfStmt if2 = new IfStmt(ne(2), block(handler(99)), block(handler(2)));
        IfStmt if1 = new IfStmt(ne(1), block(if2), block(new com.tonic.analysis.source.ast.stmt.ContinueStmt()));
        IfStmt if0 = new IfStmt(ne(0), block(if1), block(handler(0)));
        BlockStmt body = root(if0);

        assertTrue(transform.transform(body));
        assertTrue(body.getStatements().get(0) instanceof SwitchStmt);
    }
}
