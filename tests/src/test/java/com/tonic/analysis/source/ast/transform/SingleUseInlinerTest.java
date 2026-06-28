package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.MethodCallExpr;
import com.tonic.analysis.source.ast.expr.UnaryExpr;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.ForStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A single-use local must NOT be inlined into a loop condition (re-evaluated each iteration): inlining a
 * loop-invariant call there recomputes it every iteration and breaks decompile/recompile round-trip
 * stability. It should still inline into ordinary (non-loop) uses.
 */
class SingleUseInlinerTest {

    private static VarRefExpr ref(String name) {
        return new VarRefExpr(name, PrimitiveSourceType.INT);
    }

    private static boolean declares(BlockStmt block, String name) {
        return block.getStatements().stream()
            .anyMatch(s -> s instanceof VarDeclStmt && ((VarDeclStmt) s).getName().equals(name));
    }

    @Test
    void doesNotInlineLoopInvariantCallIntoForCondition() {
        // int x = Foo.bound();
        // for (int i = 0; i < x; i++) { }
        MethodCallExpr call = new MethodCallExpr(null, "bound", "Foo", new ArrayList<>(), true, PrimitiveSourceType.INT);
        VarDeclStmt decl = new VarDeclStmt(PrimitiveSourceType.INT, "x", call);

        List<Statement> init = new ArrayList<>();
        init.add(new VarDeclStmt(PrimitiveSourceType.INT, "i", LiteralExpr.ofInt(0)));
        Expression cond = new BinaryExpr(BinaryOperator.LT, ref("i"), ref("x"), PrimitiveSourceType.BOOLEAN);
        List<Expression> update = new ArrayList<>();
        update.add(new UnaryExpr(UnaryOperator.POST_INC, ref("i"), PrimitiveSourceType.INT));
        ForStmt forStmt = new ForStmt(init, cond, update, new BlockStmt(new ArrayList<>()));

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(forStmt);
        BlockStmt block = new BlockStmt(stmts);

        new SingleUseInliner().transform(block);

        assertTrue(declares(block, "x"),
            "loop-invariant call must stay a local, not be inlined into the for-condition: " + block.getStatements());
    }

    @Test
    void stillInlinesIntoNonLoopUse() {
        // int x = Foo.val();
        // if (x > 0) { }
        MethodCallExpr call = new MethodCallExpr(null, "val", "Foo", new ArrayList<>(), true, PrimitiveSourceType.INT);
        VarDeclStmt decl = new VarDeclStmt(PrimitiveSourceType.INT, "x", call);
        Expression cond = new BinaryExpr(BinaryOperator.GT, ref("x"), LiteralExpr.ofInt(0), PrimitiveSourceType.BOOLEAN);
        IfStmt ifStmt = new IfStmt(cond, new BlockStmt(new ArrayList<>()));

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(ifStmt);
        BlockStmt block = new BlockStmt(stmts);

        new SingleUseInliner().transform(block);

        assertFalse(declares(block, "x"),
            "a single use in a non-loop if should still inline: " + block.getStatements());
    }
}
