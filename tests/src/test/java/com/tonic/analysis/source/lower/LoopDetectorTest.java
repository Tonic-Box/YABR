package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.LambdaExpr;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.WhileStmt;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which loops LoopDetector attributes to the method being scanned; a lambda body lowers to a
 * separate synthetic method, so its loops belong to that method rather than the enclosing one.
 */
class LoopDetectorTest
{

    private static WhileStmt loop()
    {
        return new WhileStmt(LiteralExpr.ofBoolean(true), new BlockStmt());
    }

    private static LambdaExpr lambda(Statement body)
    {
        SourceType type = new ReferenceSourceType("java/lang/Runnable");
        return new LambdaExpr(null, body, type);
    }

    private static ExprStmt lambdaWrapping(Statement body)
    {
        return new ExprStmt(lambda(body));
    }

    private static BlockStmt blockOf(Statement... statements)
    {
        return new BlockStmt(List.of(statements));
    }

    @Test
    void aLoopInTheScannedBodyIsFound()
    {
        assertTrue(new LoopDetector().visit(blockOf(loop())),
            "a loop written directly in the body is the method's own loop");
    }

    @Test
    void aBodyWithNoLoopAtAllReportsNone()
    {
        assertFalse(new LoopDetector().visit(blockOf(new ReturnStmt())));
    }

    @Test
    void aLoopInsideALambdaBodyIsNotTheScannedMethodsLoop()
    {
        BlockStmt body = blockOf(lambdaWrapping(blockOf(loop())));

        assertFalse(new LoopDetector().visit(body),
            "the lambda body lowers to its own synthetic method, so its loop is not this method's");
    }

    @Test
    void aLoopOutsideALambdaIsStillFoundWhenALambdaIsPresent()
    {
        BlockStmt body = blockOf(lambdaWrapping(blockOf(loop())), loop());

        assertTrue(new LoopDetector().visit(body),
            "skipping the lambda must not blind the walk to the loops around it");
    }

    @Test
    void aLambdaReachedAfterALoopDoesNotClearTheFinding()
    {
        BlockStmt body = blockOf(loop(), lambdaWrapping(blockOf(loop())));

        assertTrue(new LoopDetector().visit(body),
            "the loop before the lambda is still the method's own");
    }

    @Test
    void visitLambdaReportsTheInheritedFinding()
    {
        LoopDetector detector = new LoopDetector();
        detector.visit(blockOf(loop()));

        assertTrue(detector.visitLambda(lambda(blockOf(loop()))),
            "the skip must hand back the state it inherited rather than a hardcoded false");
    }

    @Test
    void aLoopHoldingALambdaIsStillTheScannedMethodsLoop()
    {
        WhileStmt outer = new WhileStmt(LiteralExpr.ofBoolean(true),
            blockOf(lambdaWrapping(blockOf(loop()))));

        assertTrue(new LoopDetector().visit(blockOf(outer)),
            "the enclosing loop is the method's own regardless of what its body captures");
    }

    @Test
    void aLambdaNestedUnderABranchIsStillSkipped()
    {
        IfStmt branch = new IfStmt(LiteralExpr.ofBoolean(true),
            blockOf(lambdaWrapping(blockOf(loop()))));

        assertFalse(new LoopDetector().visit(blockOf(branch)),
            "the skip must hold wherever the lambda sits, not only at statement level");
    }

    @Test
    void detectorInstancesResetBetweenScans()
    {
        LoopDetector detector = new LoopDetector();

        assertTrue(detector.visit(blockOf(loop())));
        assertFalse(detector.visit(blockOf(lambdaWrapping(blockOf(loop())))),
            "a reused detector must not carry the previous scan's finding");
        assertFalse(detector.visit(new BlockStmt()));
    }
}
