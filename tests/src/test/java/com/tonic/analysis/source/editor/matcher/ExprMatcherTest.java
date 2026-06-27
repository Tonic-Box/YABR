package com.tonic.analysis.source.editor.matcher;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for ExprMatcher predicate-based expression matching.
 */
class ExprMatcherTest {

    // Helper method to create a simple variable reference for use as receiver/operand
    private VarRefExpr createVar(String name) {
        return new VarRefExpr(name, PrimitiveSourceType.INT);
    }

    // Helper method to create a simple literal expression
    private LiteralExpr createIntLiteral(int value) {
        return LiteralExpr.ofInt(value);
    }

    // ===== Basic Matching Tests =====

    @Test
    void testMatches_returnsTrueForMatchingExpression() {
        MethodCallExpr call = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.methodCall("abs");

        assertTrue(matcher.matches(call));
    }

    @Test
    void testMatches_returnsFalseForNonMatchingExpression() {
        MethodCallExpr call = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.methodCall("sqrt");

        assertFalse(matcher.matches(call));
    }

    @Test
    void testMatches_returnsFalseForNullExpression() {
        ExprMatcher matcher = ExprMatcher.methodCall("abs");

        assertFalse(matcher.matches(null));
    }

    // ===== Method Call Matcher Tests =====

    @Test
    void testMethodCall_matchesByMethodNameOnly() {
        MethodCallExpr call1 = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);
        MethodCallExpr call2 = MethodCallExpr.staticCall("java/lang/Integer", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);
        FieldAccessExpr field = FieldAccessExpr.staticField("java/lang/System", "out",
            ReferenceSourceType.OBJECT);

        ExprMatcher matcher = ExprMatcher.methodCall("abs");

        assertTrue(matcher.matches(call1));
        assertTrue(matcher.matches(call2));
        assertFalse(matcher.matches(field));
    }

    @Test
    void testMethodCall_matchesByOwnerClassAndMethodName() {
        MethodCallExpr mathAbs = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);
        MethodCallExpr integerAbs = MethodCallExpr.staticCall("java/lang/Integer", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.methodCall("java/lang/Math", "abs");

        assertTrue(matcher.matches(mathAbs));
        assertFalse(matcher.matches(integerAbs));
    }

    @Test
    void testMethodCall_matchesByOwnerClassMethodNameAndArgCount() {
        MethodCallExpr oneArg = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);
        MethodCallExpr twoArgs = MethodCallExpr.staticCall("java/lang/Math", "max",
            List.of(createIntLiteral(5), createIntLiteral(10)), PrimitiveSourceType.INT);

        ExprMatcher matcherOneArg = ExprMatcher.methodCall("java/lang/Math", "abs", 1);
        ExprMatcher matcherTwoArgs = ExprMatcher.methodCall("java/lang/Math", "abs", 2);

        assertTrue(matcherOneArg.matches(oneArg));
        assertFalse(matcherOneArg.matches(twoArgs));
        assertFalse(matcherTwoArgs.matches(oneArg));
    }

    @Test
    void testMethodCall_ownerClassNormalizesDotsToSlashes() {
        MethodCallExpr call = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);

        // Should match with dot notation
        ExprMatcher matcherDots = ExprMatcher.methodCall("java.lang.Math", "abs");
        ExprMatcher matcherSlashes = ExprMatcher.methodCall("java/lang/Math", "abs");

        assertTrue(matcherDots.matches(call));
        assertTrue(matcherSlashes.matches(call));
    }

    // ===== Field Access Matcher Tests =====

    @Test
    void testFieldAccess_matchesByFieldNameOnly() {
        FieldAccessExpr field1 = FieldAccessExpr.staticField("java/lang/System", "out",
            ReferenceSourceType.OBJECT);
        FieldAccessExpr field2 = FieldAccessExpr.staticField("com/example/Logger", "out",
            ReferenceSourceType.OBJECT);
        MethodCallExpr method = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.fieldAccess("out");

        assertTrue(matcher.matches(field1));
        assertTrue(matcher.matches(field2));
        assertFalse(matcher.matches(method));
    }

    @Test
    void testFieldAccess_matchesByOwnerClassAndFieldName() {
        FieldAccessExpr sysOut = FieldAccessExpr.staticField("java/lang/System", "out",
            ReferenceSourceType.OBJECT);
        FieldAccessExpr loggerOut = FieldAccessExpr.staticField("com/example/Logger", "out",
            ReferenceSourceType.OBJECT);

        ExprMatcher matcher = ExprMatcher.fieldAccess("java/lang/System", "out");

        assertTrue(matcher.matches(sysOut));
        assertFalse(matcher.matches(loggerOut));
    }

    @Test
    void testFieldAccess_ownerClassNormalizesDotsToSlashes() {
        FieldAccessExpr field = FieldAccessExpr.staticField("java/lang/System", "out",
            ReferenceSourceType.OBJECT);

        ExprMatcher matcherDots = ExprMatcher.fieldAccess("java.lang.System", "out");
        ExprMatcher matcherSlashes = ExprMatcher.fieldAccess("java/lang/System", "out");

        assertTrue(matcherDots.matches(field));
        assertTrue(matcherSlashes.matches(field));
    }

    // ===== Creation Matcher Tests =====

    @Test
    void testNewExpr_matchesByClassName() {
        NewExpr stringNew = new NewExpr("java/lang/String", List.of());
        NewExpr listNew = new NewExpr("java/util/ArrayList", List.of());
        NewArrayExpr arrayNew = NewArrayExpr.withSize(PrimitiveSourceType.INT, createIntLiteral(10));

        ExprMatcher matcher = ExprMatcher.newExpr("java/lang/String");

        assertTrue(matcher.matches(stringNew));
        assertFalse(matcher.matches(listNew));
        assertFalse(matcher.matches(arrayNew));
    }

    @Test
    void testNewExpr_normalizesDotsToSlashes() {
        NewExpr stringNew = new NewExpr("java/lang/String", List.of());

        ExprMatcher matcherDots = ExprMatcher.newExpr("java.lang.String");
        ExprMatcher matcherSlashes = ExprMatcher.newExpr("java/lang/String");

        assertTrue(matcherDots.matches(stringNew));
        assertTrue(matcherSlashes.matches(stringNew));
    }

    @Test
    void testNewArray_matchesAnyNewArrayExpression() {
        NewArrayExpr intArray = NewArrayExpr.withSize(PrimitiveSourceType.INT, createIntLiteral(10));
        NewArrayExpr stringArray = NewArrayExpr.withSize(
            new ReferenceSourceType("java/lang/String"), createIntLiteral(5));
        NewExpr objectNew = new NewExpr("java/lang/Object", List.of());

        ExprMatcher matcher = ExprMatcher.newArray();

        assertTrue(matcher.matches(intArray));
        assertTrue(matcher.matches(stringArray));
        assertFalse(matcher.matches(objectNew));
    }

    // ===== Type Operation Matcher Tests =====

    @Test
    void testCast_matchesByTargetType() {
        CastExpr intCast = new CastExpr(PrimitiveSourceType.INT, createVar("x"));
        CastExpr doubleCast = new CastExpr(PrimitiveSourceType.DOUBLE, createVar("x"));
        CastExpr stringCast = new CastExpr(ReferenceSourceType.STRING, createVar("x"));

        ExprMatcher intMatcher = ExprMatcher.cast("int");
        ExprMatcher stringMatcher = ExprMatcher.cast("String");

        assertTrue(intMatcher.matches(intCast));
        assertFalse(intMatcher.matches(doubleCast));
        assertFalse(intMatcher.matches(stringCast));
        assertTrue(stringMatcher.matches(stringCast));
    }

    @Test
    void testAnyCast_matchesAnyCastExpression() {
        CastExpr intCast = new CastExpr(PrimitiveSourceType.INT, createVar("x"));
        CastExpr stringCast = new CastExpr(ReferenceSourceType.STRING, createVar("x"));
        BinaryExpr binary = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.anyCast();

        assertTrue(matcher.matches(intCast));
        assertTrue(matcher.matches(stringCast));
        assertFalse(matcher.matches(binary));
    }

    @Test
    void testInstanceOf_matchesByCheckedType() {
        InstanceOfExpr stringCheck = new InstanceOfExpr(createVar("obj"), ReferenceSourceType.STRING);
        InstanceOfExpr integerCheck = new InstanceOfExpr(createVar("obj"),
            new ReferenceSourceType("java/lang/Integer"));

        ExprMatcher stringMatcher = ExprMatcher.instanceOf("String");
        ExprMatcher integerMatcher = ExprMatcher.instanceOf("Integer");

        assertTrue(stringMatcher.matches(stringCheck));
        assertFalse(stringMatcher.matches(integerCheck));
        assertTrue(integerMatcher.matches(integerCheck));
    }

    @Test
    void testAnyInstanceOf_matchesAnyInstanceOfExpression() {
        InstanceOfExpr stringCheck = new InstanceOfExpr(createVar("obj"), ReferenceSourceType.STRING);
        InstanceOfExpr integerCheck = new InstanceOfExpr(createVar("obj"),
            new ReferenceSourceType("java/lang/Integer"));
        CastExpr cast = new CastExpr(ReferenceSourceType.STRING, createVar("x"));

        ExprMatcher matcher = ExprMatcher.anyInstanceOf();

        assertTrue(matcher.matches(stringCheck));
        assertTrue(matcher.matches(integerCheck));
        assertFalse(matcher.matches(cast));
    }

    // ===== Type-Based Matcher Tests =====

    @Test
    void testOfType_matchesExpressionByClass() {
        MethodCallExpr methodCall = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);
        FieldAccessExpr fieldAccess = FieldAccessExpr.staticField("java/lang/System", "out",
            ReferenceSourceType.OBJECT);
        BinaryExpr binary = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);

        ExprMatcher methodMatcher = ExprMatcher.ofType(MethodCallExpr.class);
        ExprMatcher fieldMatcher = ExprMatcher.ofType(FieldAccessExpr.class);

        assertTrue(methodMatcher.matches(methodCall));
        assertFalse(methodMatcher.matches(fieldAccess));
        assertFalse(methodMatcher.matches(binary));

        assertTrue(fieldMatcher.matches(fieldAccess));
        assertFalse(fieldMatcher.matches(methodCall));
    }

    @Test
    void testAnyMethodCall_matchesAllMethodCallExpressions() {
        MethodCallExpr call1 = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);
        MethodCallExpr call2 = MethodCallExpr.staticCall("java/lang/String", "valueOf",
            List.of(createIntLiteral(5)), ReferenceSourceType.STRING);
        FieldAccessExpr field = FieldAccessExpr.staticField("java/lang/System", "out",
            ReferenceSourceType.OBJECT);

        ExprMatcher matcher = ExprMatcher.anyMethodCall();

        assertTrue(matcher.matches(call1));
        assertTrue(matcher.matches(call2));
        assertFalse(matcher.matches(field));
    }

    @Test
    void testAnyFieldAccess_matchesAllFieldAccessExpressions() {
        FieldAccessExpr field1 = FieldAccessExpr.staticField("java/lang/System", "out",
            ReferenceSourceType.OBJECT);
        FieldAccessExpr field2 = FieldAccessExpr.staticField("java/lang/Math", "PI",
            PrimitiveSourceType.DOUBLE);
        MethodCallExpr method = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.anyFieldAccess();

        assertTrue(matcher.matches(field1));
        assertTrue(matcher.matches(field2));
        assertFalse(matcher.matches(method));
    }

    @Test
    void testAnyBinary_matchesAllBinaryExpressions() {
        BinaryExpr add = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);
        BinaryExpr assign = new BinaryExpr(BinaryOperator.ASSIGN, createVar("x"),
            createIntLiteral(5), PrimitiveSourceType.INT);
        UnaryExpr unary = new UnaryExpr(UnaryOperator.NEG, createIntLiteral(5),
            PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.anyBinary();

        assertTrue(matcher.matches(add));
        assertTrue(matcher.matches(assign));
        assertFalse(matcher.matches(unary));
    }

    @Test
    void testAnyUnary_matchesAllUnaryExpressions() {
        UnaryExpr neg = new UnaryExpr(UnaryOperator.NEG, createIntLiteral(5),
            PrimitiveSourceType.INT);
        UnaryExpr not = new UnaryExpr(UnaryOperator.NOT,
            LiteralExpr.ofBoolean(true), PrimitiveSourceType.BOOLEAN);
        BinaryExpr binary = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.anyUnary();

        assertTrue(matcher.matches(neg));
        assertTrue(matcher.matches(not));
        assertFalse(matcher.matches(binary));
    }

    @Test
    void testAnyLiteral_matchesAllLiteralExpressions() {
        LiteralExpr intLit = LiteralExpr.ofInt(42);
        LiteralExpr stringLit = LiteralExpr.ofString("hello");
        LiteralExpr nullLit = LiteralExpr.ofNull();
        BinaryExpr binary = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.anyLiteral();

        assertTrue(matcher.matches(intLit));
        assertTrue(matcher.matches(stringLit));
        assertTrue(matcher.matches(nullLit));
        assertFalse(matcher.matches(binary));
    }

    @Test
    void testAnyArrayAccess_matchesAllArrayAccessExpressions() {
        ArrayAccessExpr access1 = new ArrayAccessExpr(createVar("arr"), createIntLiteral(0),
            PrimitiveSourceType.INT);
        ArrayAccessExpr access2 = new ArrayAccessExpr(createVar("arr"), createVar("i"),
            PrimitiveSourceType.INT);
        NewArrayExpr newArray = NewArrayExpr.withSize(PrimitiveSourceType.INT, createIntLiteral(10));

        ExprMatcher matcher = ExprMatcher.anyArrayAccess();

        assertTrue(matcher.matches(access1));
        assertTrue(matcher.matches(access2));
        assertFalse(matcher.matches(newArray));
    }

    // ===== Operator Matcher Tests =====

    @Test
    void testBinaryOp_matchesByOperator() {
        BinaryExpr add = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);
        BinaryExpr mul = new BinaryExpr(BinaryOperator.MUL, createIntLiteral(3),
            createIntLiteral(4), PrimitiveSourceType.INT);
        BinaryExpr eq = new BinaryExpr(BinaryOperator.EQ, createIntLiteral(1),
            createIntLiteral(1), PrimitiveSourceType.BOOLEAN);

        ExprMatcher addMatcher = ExprMatcher.binaryOp(BinaryOperator.ADD);
        ExprMatcher eqMatcher = ExprMatcher.binaryOp(BinaryOperator.EQ);

        assertTrue(addMatcher.matches(add));
        assertFalse(addMatcher.matches(mul));
        assertFalse(addMatcher.matches(eq));

        assertTrue(eqMatcher.matches(eq));
        assertFalse(eqMatcher.matches(add));
    }

    @Test
    void testUnaryOp_matchesByOperator() {
        UnaryExpr neg = new UnaryExpr(UnaryOperator.NEG, createIntLiteral(5),
            PrimitiveSourceType.INT);
        UnaryExpr not = new UnaryExpr(UnaryOperator.NOT, LiteralExpr.ofBoolean(true),
            PrimitiveSourceType.BOOLEAN);
        UnaryExpr preInc = new UnaryExpr(UnaryOperator.PRE_INC, createVar("x"),
            PrimitiveSourceType.INT);

        ExprMatcher negMatcher = ExprMatcher.unaryOp(UnaryOperator.NEG);
        ExprMatcher notMatcher = ExprMatcher.unaryOp(UnaryOperator.NOT);

        assertTrue(negMatcher.matches(neg));
        assertFalse(negMatcher.matches(not));
        assertFalse(negMatcher.matches(preInc));

        assertTrue(notMatcher.matches(not));
        assertFalse(notMatcher.matches(neg));
    }

    @Test
    void testAssignment_matchesAssignmentExpressions() {
        BinaryExpr assign = new BinaryExpr(BinaryOperator.ASSIGN, createVar("x"),
            createIntLiteral(5), PrimitiveSourceType.INT);
        BinaryExpr addAssign = new BinaryExpr(BinaryOperator.ADD_ASSIGN, createVar("x"),
            createIntLiteral(5), PrimitiveSourceType.INT);
        BinaryExpr add = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.assignment();

        assertTrue(matcher.matches(assign));
        assertTrue(matcher.matches(addAssign));
        assertFalse(matcher.matches(add));
    }

    @Test
    void testComparison_matchesComparisonExpressions() {
        BinaryExpr eq = new BinaryExpr(BinaryOperator.EQ, createIntLiteral(1),
            createIntLiteral(1), PrimitiveSourceType.BOOLEAN);
        BinaryExpr lt = new BinaryExpr(BinaryOperator.LT, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.BOOLEAN);
        BinaryExpr ge = new BinaryExpr(BinaryOperator.GE, createIntLiteral(2),
            createIntLiteral(1), PrimitiveSourceType.BOOLEAN);
        BinaryExpr add = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.comparison();

        assertTrue(matcher.matches(eq));
        assertTrue(matcher.matches(lt));
        assertTrue(matcher.matches(ge));
        assertFalse(matcher.matches(add));
    }

    // ===== Combinator Tests =====

    @Test
    void testAnd_requiresBothMatchersToMatch() {
        BinaryExpr add = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);
        BinaryExpr assign = new BinaryExpr(BinaryOperator.ASSIGN, createVar("x"),
            createIntLiteral(5), PrimitiveSourceType.INT);
        UnaryExpr neg = new UnaryExpr(UnaryOperator.NEG, createIntLiteral(5),
            PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.anyBinary().and(ExprMatcher.assignment());

        assertTrue(matcher.matches(assign));
        assertFalse(matcher.matches(add));  // Binary but not assignment
        assertFalse(matcher.matches(neg));  // Not binary
    }

    @Test
    void testOr_matchesIfEitherMatcherMatches() {
        MethodCallExpr method = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);
        FieldAccessExpr field = FieldAccessExpr.staticField("java/lang/System", "out",
            ReferenceSourceType.OBJECT);
        BinaryExpr binary = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.anyMethodCall().or(ExprMatcher.anyFieldAccess());

        assertTrue(matcher.matches(method));
        assertTrue(matcher.matches(field));
        assertFalse(matcher.matches(binary));
    }

    @Test
    void testNot_invertsMatchResult() {
        BinaryExpr add = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);
        BinaryExpr assign = new BinaryExpr(BinaryOperator.ASSIGN, createVar("x"),
            createIntLiteral(5), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.assignment().not();

        assertFalse(matcher.matches(assign));
        assertTrue(matcher.matches(add));
    }

    @Test
    void testAny_matchesAllExpressions() {
        MethodCallExpr method = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);
        FieldAccessExpr field = FieldAccessExpr.staticField("java/lang/System", "out",
            ReferenceSourceType.OBJECT);
        BinaryExpr binary = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);
        LiteralExpr literal = LiteralExpr.ofInt(42);

        ExprMatcher matcher = ExprMatcher.any();

        assertTrue(matcher.matches(method));
        assertTrue(matcher.matches(field));
        assertTrue(matcher.matches(binary));
        assertTrue(matcher.matches(literal));
    }

    @Test
    void testNone_matchesNoExpressions() {
        MethodCallExpr method = MethodCallExpr.staticCall("java/lang/Math", "abs",
            List.of(createIntLiteral(5)), PrimitiveSourceType.INT);
        FieldAccessExpr field = FieldAccessExpr.staticField("java/lang/System", "out",
            ReferenceSourceType.OBJECT);
        BinaryExpr binary = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.none();

        assertFalse(matcher.matches(method));
        assertFalse(matcher.matches(field));
        assertFalse(matcher.matches(binary));
    }

    @Test
    void testComplexCombination_multipleCombinatorsWork() {
        // Match binary expressions that are either assignments OR comparisons
        BinaryExpr assign = new BinaryExpr(BinaryOperator.ASSIGN, createVar("x"),
            createIntLiteral(5), PrimitiveSourceType.INT);
        BinaryExpr eq = new BinaryExpr(BinaryOperator.EQ, createIntLiteral(1),
            createIntLiteral(1), PrimitiveSourceType.BOOLEAN);
        BinaryExpr add = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);
        UnaryExpr neg = new UnaryExpr(UnaryOperator.NEG, createIntLiteral(5),
            PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.anyBinary()
            .and(ExprMatcher.assignment().or(ExprMatcher.comparison()));

        assertTrue(matcher.matches(assign));
        assertTrue(matcher.matches(eq));
        assertFalse(matcher.matches(add));  // Binary but neither assignment nor comparison
        assertFalse(matcher.matches(neg));  // Not binary
    }

    // ===== Custom Matcher Tests =====

    @Test
    void testCustom_withPredicateOnly() {
        LiteralExpr intLit = LiteralExpr.ofInt(42);
        LiteralExpr stringLit = LiteralExpr.ofString("hello");

        ExprMatcher matcher = ExprMatcher.custom(expr ->
            expr instanceof LiteralExpr && ((LiteralExpr) expr).isNumeric()
        );

        assertTrue(matcher.matches(intLit));
        assertFalse(matcher.matches(stringLit));
    }

    @Test
    void testCustom_withPredicateAndDescription() {
        LiteralExpr intLit = LiteralExpr.ofInt(42);
        LiteralExpr stringLit = LiteralExpr.ofString("hello");

        ExprMatcher matcher = ExprMatcher.custom(
            expr -> expr instanceof LiteralExpr && ((LiteralExpr) expr).isNumeric(),
            "numeric literal"
        );

        assertTrue(matcher.matches(intLit));
        assertFalse(matcher.matches(stringLit));
        assertTrue(matcher.toString().contains("numeric literal"));
    }

    @Test
    void testCustom_canBeUsedInCombinators() {
        LiteralExpr intLit = LiteralExpr.ofInt(42);
        LiteralExpr stringLit = LiteralExpr.ofString("hello");
        BinaryExpr add = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.anyLiteral().and(
            ExprMatcher.custom(expr -> ((LiteralExpr) expr).isNumeric())
        );

        assertTrue(matcher.matches(intLit));
        assertFalse(matcher.matches(stringLit));
        assertFalse(matcher.matches(add));
    }

    // ===== ToString Tests =====

    @Test
    void testToString_providesDescriptiveOutput() {
        ExprMatcher methodMatcher = ExprMatcher.methodCall("abs");
        ExprMatcher fieldMatcher = ExprMatcher.fieldAccess("java/lang/System", "out");
        ExprMatcher combinedMatcher = ExprMatcher.anyBinary().and(ExprMatcher.assignment());

        assertTrue(methodMatcher.toString().contains("methodCall(abs)"));
        assertTrue(fieldMatcher.toString().contains("fieldAccess"));
        assertTrue(combinedMatcher.toString().contains("&&"));
    }

    // ===== Edge Cases =====

    @Test
    void testMethodCall_withZeroArguments() {
        MethodCallExpr noArgs = MethodCallExpr.staticCall("com/example/Utils", "init",
            List.of(), PrimitiveSourceType.INT);

        ExprMatcher matcher = ExprMatcher.methodCall("com/example/Utils", "init", 0);

        assertTrue(matcher.matches(noArgs));
    }

    @Test
    void testNewExpr_withInnerClass() {
        NewExpr innerClass = new NewExpr("com/example/Outer$Inner", List.of());

        ExprMatcher matcherSlash = ExprMatcher.newExpr("com/example/Outer$Inner");
        ExprMatcher matcherDot = ExprMatcher.newExpr("com.example.Outer$Inner");

        assertTrue(matcherSlash.matches(innerClass));
        assertTrue(matcherDot.matches(innerClass));
    }

    @Test
    void testCombinators_preserveNullSafety() {
        ExprMatcher matcher = ExprMatcher.anyBinary()
            .and(ExprMatcher.assignment())
            .or(ExprMatcher.comparison())
            .not();

        // Should not throw on null
        assertFalse(matcher.matches(null));
    }

    @Test
    void testComplexNesting_andOrNotCombinations() {
        // (binary && assignment) || (!comparison)
        BinaryExpr assign = new BinaryExpr(BinaryOperator.ASSIGN, createVar("x"),
            createIntLiteral(5), PrimitiveSourceType.INT);
        BinaryExpr add = new BinaryExpr(BinaryOperator.ADD, createIntLiteral(1),
            createIntLiteral(2), PrimitiveSourceType.INT);
        BinaryExpr eq = new BinaryExpr(BinaryOperator.EQ, createIntLiteral(1),
            createIntLiteral(1), PrimitiveSourceType.BOOLEAN);

        ExprMatcher matcher = ExprMatcher.anyBinary()
            .and(ExprMatcher.assignment())
            .or(ExprMatcher.comparison().not());

        assertTrue(matcher.matches(assign));  // binary && assignment
        assertTrue(matcher.matches(add));     // !comparison
        assertFalse(matcher.matches(eq));     // comparison (so !comparison is false)
    }

    // ===== VarRefExpr Class for Testing =====

    /**
     * Simple VarRefExpr implementation for testing purposes.
     */
    private static class VarRefExpr implements Expression {
        private final String name;
        private final SourceType type;
        private com.tonic.analysis.source.ast.ASTNode parent;

        VarRefExpr(String name, SourceType type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public SourceType getType() {
            return type;
        }

        @Override
        public com.tonic.analysis.source.ast.SourceLocation getLocation() {
            return com.tonic.analysis.source.ast.SourceLocation.UNKNOWN;
        }

        @Override
        public com.tonic.analysis.source.ast.ASTNode getParent() {
            return parent;
        }

        @Override
        public void setParent(com.tonic.analysis.source.ast.ASTNode parent) {
            this.parent = parent;
        }

        @Override
        public <T> T accept(com.tonic.analysis.source.visitor.SourceVisitor<T> visitor) {
            return null;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
