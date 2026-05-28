package com.tonic.analysis.source.emit;

import com.tonic.analysis.source.ast.expr.ArrayAccessExpr;
import com.tonic.analysis.source.ast.expr.CastExpr;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.InstanceOfExpr;
import com.tonic.analysis.source.ast.expr.MethodCallExpr;
import com.tonic.analysis.source.ast.expr.UnaryExpr;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for emitter parenthesization: a subexpression whose operator
 * binds looser than its surrounding context must be wrapped in parentheses,
 * otherwise the emitted source mis-parses (or fails to compile).
 */
class EmitterPrecedenceTest {

    private final ReferenceSourceType OBJECT = ReferenceSourceType.OBJECT;

    /** {@code !(x instanceof Integer)} — NOT applied to instanceof must be parenthesized. */
    @Test
    void notOfInstanceOfIsParenthesized() {
        InstanceOfExpr io = new InstanceOfExpr(
                new VarRefExpr("x", OBJECT), new ReferenceSourceType("java/lang/Integer"));
        UnaryExpr not = new UnaryExpr(UnaryOperator.NOT, io, PrimitiveSourceType.BOOLEAN);

        String out = SourceEmitter.emit(not);
        assertEquals("!(x instanceof Integer)", out.trim());
    }

    /** {@code ((Long) a[0]).longValue()} — a cast used as a method receiver must be parenthesized. */
    @Test
    void castAsMethodReceiverIsParenthesized() {
        ArrayAccessExpr elem = new ArrayAccessExpr(
                new VarRefExpr("a", OBJECT), LiteralExpr.ofInt(0), OBJECT);
        CastExpr cast = new CastExpr(new ReferenceSourceType("java/lang/Long"), elem);
        MethodCallExpr call = new MethodCallExpr(
                cast, "longValue", "java/lang/Long", new ArrayList<>(), false, PrimitiveSourceType.LONG);

        String out = SourceEmitter.emit(call).trim();
        assertEquals("((Long) a[0]).longValue()", out);
    }

    /** A cast used as an array base must be parenthesized: {@code ((Object[]) x)[0]}. */
    @Test
    void castAsArrayBaseIsParenthesized() {
        CastExpr cast = new CastExpr(new ReferenceSourceType("java/lang/Object"),
                new VarRefExpr("x", OBJECT));
        ArrayAccessExpr access = new ArrayAccessExpr(cast, LiteralExpr.ofInt(0), OBJECT);

        String out = SourceEmitter.emit(access).trim();
        assertTrue(out.startsWith("((Object) x)["), "got: " + out);
    }

    /** Plain primary receivers must NOT gain spurious parentheses. */
    @Test
    void plainReceiverIsNotParenthesized() {
        MethodCallExpr call = new MethodCallExpr(
                new VarRefExpr("x", OBJECT), "hashCode", "java/lang/Object",
                new ArrayList<>(), false, PrimitiveSourceType.INT);
        String out = SourceEmitter.emit(call).trim();
        assertEquals("x.hashCode()", out);
    }

    /** {@code !flag} must NOT gain parentheses around a simple operand. */
    @Test
    void notOfSimpleOperandIsNotParenthesized() {
        UnaryExpr not = new UnaryExpr(UnaryOperator.NOT,
                new VarRefExpr("flag", PrimitiveSourceType.BOOLEAN), PrimitiveSourceType.BOOLEAN);
        String out = SourceEmitter.emit(not).trim();
        assertEquals("!flag", out);
    }
}
