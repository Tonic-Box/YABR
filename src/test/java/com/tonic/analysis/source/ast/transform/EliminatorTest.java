package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.ArraySourceType;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EliminatorTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    @Nested
    class SideEffectDetectorTests {

        @Nested
        class DeadStoreEliminatorSideEffectTests {
            private DeadStoreEliminator eliminator;

            @BeforeEach
            void setUp() {
                eliminator = new DeadStoreEliminator();
            }

            @Test
            void literalHasNoSideEffects() {
                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    LiteralExpr.ofInt(42)
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
                assertEquals(1, block.size());
            }

            @Test
            void varRefHasNoSideEffects() {
                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    new VarRefExpr("y", PrimitiveSourceType.INT)
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void thisExprHasNoSideEffects() {
                VarDeclStmt decl = new VarDeclStmt(
                    new ReferenceSourceType("Object"),
                    "x",
                    new ThisExpr(new ReferenceSourceType("Object"))
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", new ReferenceSourceType("Object")),
                    new ThisExpr(new ReferenceSourceType("Object")),
                    new ReferenceSourceType("Object")
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void superExprHasNoSideEffects() {
                VarDeclStmt decl = new VarDeclStmt(
                    new ReferenceSourceType("Object"),
                    "x",
                    new SuperExpr(new ReferenceSourceType("Object"))
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", new ReferenceSourceType("Object")),
                    LiteralExpr.ofNull(),
                    new ReferenceSourceType("Object")
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void classExprHasNoSideEffects() {
                VarDeclStmt decl = new VarDeclStmt(
                    new ReferenceSourceType("Class"),
                    "x",
                    new ClassExpr(PrimitiveSourceType.INT)
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", new ReferenceSourceType("Class")),
                    new ClassExpr(PrimitiveSourceType.DOUBLE),
                    new ReferenceSourceType("Class")
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void lambdaExprHasNoSideEffects() {
                LambdaExpr lambda = new LambdaExpr(
                    new ArrayList<>(),
                    new BlockStmt(),
                    new ReferenceSourceType("Function")
                );

                VarDeclStmt decl = new VarDeclStmt(
                    new ReferenceSourceType("Function"),
                    "x",
                    lambda
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", new ReferenceSourceType("Function")),
                    LiteralExpr.ofNull(),
                    new ReferenceSourceType("Function")
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void binaryExprWithAssignmentHasSideEffects() {
                BinaryExpr assignExpr = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("y", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(5),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    assignExpr
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertFalse(changed);
            }

            @Test
            void binaryExprWithCompoundAssignmentHasSideEffects() {
                BinaryExpr compoundAssign = new BinaryExpr(
                    BinaryOperator.ADD_ASSIGN,
                    new VarRefExpr("y", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(5),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    compoundAssign
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertFalse(changed);
            }

            @Test
            void binaryExprWithoutAssignmentHasNoSideEffects() {
                BinaryExpr addExpr = new BinaryExpr(
                    BinaryOperator.ADD,
                    LiteralExpr.ofInt(5),
                    LiteralExpr.ofInt(3),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    addExpr
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void unaryPreIncrementHasSideEffects() {
                UnaryExpr preInc = new UnaryExpr(
                    UnaryOperator.PRE_INC,
                    new VarRefExpr("y", PrimitiveSourceType.INT),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    preInc
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertFalse(changed);
            }

            @Test
            void unaryPostIncrementHasSideEffects() {
                UnaryExpr postInc = new UnaryExpr(
                    UnaryOperator.POST_INC,
                    new VarRefExpr("y", PrimitiveSourceType.INT),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    postInc
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertFalse(changed);
            }

            @Test
            void unaryPreDecrementHasSideEffects() {
                UnaryExpr preDec = new UnaryExpr(
                    UnaryOperator.PRE_DEC,
                    new VarRefExpr("y", PrimitiveSourceType.INT),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    preDec
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertFalse(changed);
            }

            @Test
            void unaryPostDecrementHasSideEffects() {
                UnaryExpr postDec = new UnaryExpr(
                    UnaryOperator.POST_DEC,
                    new VarRefExpr("y", PrimitiveSourceType.INT),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    postDec
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertFalse(changed);
            }

            @Test
            void unaryNegateHasNoSideEffects() {
                UnaryExpr negate = new UnaryExpr(
                    UnaryOperator.NEG,
                    LiteralExpr.ofInt(5),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    negate
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void ternaryWithoutSideEffectsIsOk() {
                TernaryExpr ternary = new TernaryExpr(
                    LiteralExpr.ofBoolean(true),
                    LiteralExpr.ofInt(1),
                    LiteralExpr.ofInt(2),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    ternary
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void ternaryWithSideEffectInConditionBlocked() {
                MethodCallExpr methodCall = new MethodCallExpr(
                    null,
                    "test",
                    "java/lang/Object",
                    new ArrayList<>(),
                    true,
                    PrimitiveSourceType.BOOLEAN
                );

                TernaryExpr ternary = new TernaryExpr(
                    methodCall,
                    LiteralExpr.ofInt(1),
                    LiteralExpr.ofInt(2),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    ternary
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertFalse(changed);
            }

            @Test
            void castExprHasNoSideEffects() {
                CastExpr cast = new CastExpr(
                    PrimitiveSourceType.DOUBLE,
                    LiteralExpr.ofInt(5)
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.DOUBLE,
                    "x",
                    cast
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.DOUBLE),
                    LiteralExpr.ofDouble(100.0),
                    PrimitiveSourceType.DOUBLE
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void instanceOfExprHasNoSideEffects() {
                InstanceOfExpr instanceOf = new InstanceOfExpr(
                    new VarRefExpr("obj", new ReferenceSourceType("Object")),
                    new ReferenceSourceType("String")
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.BOOLEAN,
                    "x",
                    instanceOf
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.BOOLEAN),
                    LiteralExpr.ofBoolean(true),
                    PrimitiveSourceType.BOOLEAN
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void methodCallHasSideEffects() {
                MethodCallExpr methodCall = new MethodCallExpr(
                    null,
                    "getValue",
                    "TestClass",
                    new ArrayList<>(),
                    true,
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    methodCall
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertFalse(changed);
            }

            @Test
            void newExprHasSideEffects() {
                NewExpr newExpr = new NewExpr(
                    "java/lang/Object",
                    new ArrayList<>(),
                    new ReferenceSourceType("Object")
                );

                VarDeclStmt decl = new VarDeclStmt(
                    new ReferenceSourceType("Object"),
                    "x",
                    newExpr
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", new ReferenceSourceType("Object")),
                    LiteralExpr.ofNull(),
                    new ReferenceSourceType("Object")
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertFalse(changed);
            }

            @Test
            void newArrayExprHasSideEffects() {
                NewArrayExpr newArray = new NewArrayExpr(
                    PrimitiveSourceType.INT,
                    Collections.singletonList(LiteralExpr.ofInt(10))
                );

                VarDeclStmt decl = new VarDeclStmt(
                    new ArraySourceType(PrimitiveSourceType.INT),
                    "x",
                    newArray
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", new ArraySourceType(PrimitiveSourceType.INT)),
                    LiteralExpr.ofNull(),
                    new ArraySourceType(PrimitiveSourceType.INT)
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertFalse(changed);
            }

            @Test
            void fieldAccessWithSimpleReceiverHasNoSideEffects() {
                FieldAccessExpr fieldAccess = new FieldAccessExpr(
                    new VarRefExpr("obj", new ReferenceSourceType("Object")),
                    "field",
                    "java/lang/Object",
                    false,
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    fieldAccess
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void arrayAccessHasNoSideEffects() {
                ArrayAccessExpr arrayAccess = new ArrayAccessExpr(
                    new VarRefExpr("arr", new ArraySourceType(PrimitiveSourceType.INT)),
                    LiteralExpr.ofInt(0),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    arrayAccess
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(100),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void arrayInitExprWithoutSideEffects() {
                ArrayInitExpr arrayInit = new ArrayInitExpr(
                    Arrays.asList(
                        LiteralExpr.ofInt(1),
                        LiteralExpr.ofInt(2),
                        LiteralExpr.ofInt(3)
                    ),
                    new ArraySourceType(PrimitiveSourceType.INT)
                );

                VarDeclStmt decl = new VarDeclStmt(
                    new ArraySourceType(PrimitiveSourceType.INT),
                    "x",
                    arrayInit
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", new ArraySourceType(PrimitiveSourceType.INT)),
                    LiteralExpr.ofNull(),
                    new ArraySourceType(PrimitiveSourceType.INT)
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
            }

            @Test
            void arrayInitExprWithSideEffects() {
                MethodCallExpr methodCall = new MethodCallExpr(
                    null,
                    "getValue",
                    "TestClass",
                    new ArrayList<>(),
                    true,
                    PrimitiveSourceType.INT
                );

                ArrayInitExpr arrayInit = new ArrayInitExpr(
                    Arrays.asList(
                        LiteralExpr.ofInt(1),
                        methodCall,
                        LiteralExpr.ofInt(3)
                    ),
                    new ArraySourceType(PrimitiveSourceType.INT)
                );

                VarDeclStmt decl = new VarDeclStmt(
                    new ArraySourceType(PrimitiveSourceType.INT),
                    "x",
                    arrayInit
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", new ArraySourceType(PrimitiveSourceType.INT)),
                    LiteralExpr.ofNull(),
                    new ArraySourceType(PrimitiveSourceType.INT)
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertFalse(changed);
            }
        }

        @Nested
        class DeadVariableEliminatorSideEffectTests {
            private DeadVariableEliminator eliminator;

            @BeforeEach
            void setUp() {
                eliminator = new DeadVariableEliminator();
            }

            @Test
            void removesDeclarationWithLiteralInitializer() {
                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "unused",
                    LiteralExpr.ofInt(42)
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
                assertEquals(0, block.size());
            }

            @Test
            void preservesMethodCallSideEffect() {
                MethodCallExpr methodCall = new MethodCallExpr(
                    null,
                    "compute",
                    "TestClass",
                    new ArrayList<>(),
                    true,
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "unused",
                    methodCall
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
                assertEquals(1, block.size());
                assertTrue(block.getStatements().get(0) instanceof ExprStmt);
            }

            @Test
            void preservesNewExprSideEffect() {
                NewExpr newExpr = new NewExpr(
                    "java/lang/Object",
                    new ArrayList<>(),
                    new ReferenceSourceType("Object")
                );

                VarDeclStmt decl = new VarDeclStmt(
                    new ReferenceSourceType("Object"),
                    "unused",
                    newExpr
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
                assertEquals(1, block.size());
                assertTrue(block.getStatements().get(0) instanceof ExprStmt);
            }

            @Test
            void preservesIncrementSideEffect() {
                UnaryExpr increment = new UnaryExpr(
                    UnaryOperator.PRE_INC,
                    new VarRefExpr("counter", PrimitiveSourceType.INT),
                    PrimitiveSourceType.INT
                );

                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "unused",
                    increment
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
                assertEquals(1, block.size());
                assertTrue(block.getStatements().get(0) instanceof ExprStmt);
            }

            @Test
            void removesUnusedAssignmentWithoutSideEffects() {
                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    LiteralExpr.ofInt(0)
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(10),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
                assertEquals(0, block.size());
            }

            @Test
            void preservesAssignmentWithMethodCallSideEffect() {
                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    LiteralExpr.ofInt(0)
                );

                MethodCallExpr methodCall = new MethodCallExpr(
                    null,
                    "compute",
                    "TestClass",
                    new ArrayList<>(),
                    true,
                    PrimitiveSourceType.INT
                );

                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    methodCall,
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(assign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                assertTrue(changed);
                assertEquals(1, block.size());
                assertTrue(block.getStatements().get(0) instanceof ExprStmt);
            }

            @Test
            void handlesCompoundAssignment() {
                VarDeclStmt decl = new VarDeclStmt(
                    PrimitiveSourceType.INT,
                    "x",
                    LiteralExpr.ofInt(0)
                );

                BinaryExpr compoundAssign = new BinaryExpr(
                    BinaryOperator.ADD_ASSIGN,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(5),
                    PrimitiveSourceType.INT
                );

                List<Statement> stmts = new ArrayList<>();
                stmts.add(decl);
                stmts.add(new ExprStmt(compoundAssign));
                BlockStmt block = new BlockStmt(stmts);

                boolean changed = eliminator.transform(block);

                // Just verify it runs without error(changed);
            }
        }
    }

    @Nested
    class DeadStoreEliminatorNestedStructureTests {
        private DeadStoreEliminator eliminator;

        @BeforeEach
        void setUp() {
            eliminator = new DeadStoreEliminator();
        }

        @Test
        void handleWhileStmt() {
            VarDeclStmt innerDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "y",
                LiteralExpr.ofInt(0)
            );

            BinaryExpr innerAssign = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("y", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(10),
                PrimitiveSourceType.INT
            );

            List<Statement> whileStmts = new ArrayList<>();
            whileStmts.add(innerDecl);
            whileStmts.add(new ExprStmt(innerAssign));
            whileStmts.add(new ReturnStmt(new VarRefExpr("y", PrimitiveSourceType.INT)));
            BlockStmt whileBody = new BlockStmt(whileStmts);

            WhileStmt whileStmt = new WhileStmt(
                LiteralExpr.ofBoolean(true),
                whileBody
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(whileStmt);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleDoWhileStmt() {
            VarDeclStmt innerDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "y",
                LiteralExpr.ofInt(0)
            );

            BinaryExpr innerAssign = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("y", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(10),
                PrimitiveSourceType.INT
            );

            List<Statement> doStmts = new ArrayList<>();
            doStmts.add(innerDecl);
            doStmts.add(new ExprStmt(innerAssign));
            doStmts.add(new ReturnStmt(new VarRefExpr("y", PrimitiveSourceType.INT)));
            BlockStmt doBody = new BlockStmt(doStmts);

            DoWhileStmt doWhile = new DoWhileStmt(
                doBody,
                LiteralExpr.ofBoolean(true)
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(doWhile);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleForStmt() {
            VarDeclStmt innerDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "y",
                LiteralExpr.ofInt(0)
            );

            BinaryExpr innerAssign = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("y", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(10),
                PrimitiveSourceType.INT
            );

            List<Statement> forStmts = new ArrayList<>();
            forStmts.add(innerDecl);
            forStmts.add(new ExprStmt(innerAssign));
            forStmts.add(new ReturnStmt(new VarRefExpr("y", PrimitiveSourceType.INT)));
            BlockStmt forBody = new BlockStmt(forStmts);

            ForStmt forStmt = new ForStmt(
                new ArrayList<>(),
                LiteralExpr.ofBoolean(true),
                new ArrayList<>(),
                forBody
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(forStmt);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleForEachStmt() {
            VarDeclStmt innerDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "y",
                LiteralExpr.ofInt(0)
            );

            BinaryExpr innerAssign = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("y", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(10),
                PrimitiveSourceType.INT
            );

            List<Statement> forEachStmts = new ArrayList<>();
            forEachStmts.add(innerDecl);
            forEachStmts.add(new ExprStmt(innerAssign));
            forEachStmts.add(new ReturnStmt(new VarRefExpr("y", PrimitiveSourceType.INT)));
            BlockStmt forEachBody = new BlockStmt(forEachStmts);

            ForEachStmt forEach = new ForEachStmt(
                new VarDeclStmt(PrimitiveSourceType.INT, "i", null),
                new VarRefExpr("arr", new ArraySourceType(PrimitiveSourceType.INT)),
                forEachBody
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(forEach);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleTryCatchStmt() {
            VarDeclStmt tryDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "y",
                LiteralExpr.ofInt(0)
            );

            BinaryExpr tryAssign = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("y", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(10),
                PrimitiveSourceType.INT
            );

            List<Statement> tryStmts = new ArrayList<>();
            tryStmts.add(tryDecl);
            tryStmts.add(new ExprStmt(tryAssign));
            tryStmts.add(new ReturnStmt(new VarRefExpr("y", PrimitiveSourceType.INT)));
            BlockStmt tryBlock = new BlockStmt(tryStmts);

            VarDeclStmt catchDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "z",
                LiteralExpr.ofInt(0)
            );

            BinaryExpr catchAssign = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("z", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(20),
                PrimitiveSourceType.INT
            );

            List<Statement> catchStmts = new ArrayList<>();
            catchStmts.add(catchDecl);
            catchStmts.add(new ExprStmt(catchAssign));
            catchStmts.add(new ReturnStmt(new VarRefExpr("z", PrimitiveSourceType.INT)));
            BlockStmt catchBlock = new BlockStmt(catchStmts);

            CatchClause catchClause = CatchClause.of(
                new ReferenceSourceType("Exception"),
                "e",
                catchBlock
            );

            TryCatchStmt tryCatch = new TryCatchStmt(
                tryBlock,
                Collections.singletonList(catchClause)
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(tryCatch);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleTryCatchWithFinally() {
            VarDeclStmt finallyDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "w",
                LiteralExpr.ofInt(0)
            );

            BinaryExpr finallyAssign = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("w", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(30),
                PrimitiveSourceType.INT
            );

            List<Statement> finallyStmts = new ArrayList<>();
            finallyStmts.add(finallyDecl);
            finallyStmts.add(new ExprStmt(finallyAssign));
            finallyStmts.add(new ReturnStmt(new VarRefExpr("w", PrimitiveSourceType.INT)));
            BlockStmt finallyBlock = new BlockStmt(finallyStmts);

            TryCatchStmt tryCatch = new TryCatchStmt(
                new BlockStmt(),
                new ArrayList<>(),
                finallyBlock
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(tryCatch);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleSynchronizedStmt() {
            VarDeclStmt innerDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "y",
                LiteralExpr.ofInt(0)
            );

            BinaryExpr innerAssign = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("y", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(10),
                PrimitiveSourceType.INT
            );

            List<Statement> syncStmts = new ArrayList<>();
            syncStmts.add(innerDecl);
            syncStmts.add(new ExprStmt(innerAssign));
            syncStmts.add(new ReturnStmt(new VarRefExpr("y", PrimitiveSourceType.INT)));
            BlockStmt syncBody = new BlockStmt(syncStmts);

            SynchronizedStmt syncStmt = new SynchronizedStmt(
                new ThisExpr(new ReferenceSourceType("Object")),
                syncBody
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(syncStmt);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleLabeledStmt() {
            VarDeclStmt innerDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "y",
                LiteralExpr.ofInt(0)
            );

            BinaryExpr innerAssign = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("y", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(10),
                PrimitiveSourceType.INT
            );

            List<Statement> labeledStmts = new ArrayList<>();
            labeledStmts.add(innerDecl);
            labeledStmts.add(new ExprStmt(innerAssign));
            labeledStmts.add(new ReturnStmt(new VarRefExpr("y", PrimitiveSourceType.INT)));
            BlockStmt labeledBody = new BlockStmt(labeledStmts);

            LabeledStmt labeled = new LabeledStmt(
                "label",
                labeledBody
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(labeled);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleElseBranch() {
            VarDeclStmt elseDecl = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "y",
                LiteralExpr.ofInt(0)
            );

            BinaryExpr elseAssign = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("y", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(20),
                PrimitiveSourceType.INT
            );

            List<Statement> elseStmts = new ArrayList<>();
            elseStmts.add(elseDecl);
            elseStmts.add(new ExprStmt(elseAssign));
            BlockStmt elseBlock = new BlockStmt(elseStmts);

            IfStmt ifStmt = new IfStmt(
                LiteralExpr.ofBoolean(true),
                new BlockStmt(),
                elseBlock
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(ifStmt);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }
    }

    @Nested
    class DeadVariableEliminatorNestedStructureTests {
        private DeadVariableEliminator eliminator;

        @BeforeEach
        void setUp() {
            eliminator = new DeadVariableEliminator();
        }

        @Test
        void handleWhileStmt() {
            VarDeclStmt unused = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "unused",
                LiteralExpr.ofInt(42)
            );

            List<Statement> whileStmts = new ArrayList<>();
            whileStmts.add(unused);
            BlockStmt whileBody = new BlockStmt(whileStmts);

            WhileStmt whileStmt = new WhileStmt(
                LiteralExpr.ofBoolean(true),
                whileBody
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(whileStmt);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleDoWhileStmt() {
            VarDeclStmt unused = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "unused",
                LiteralExpr.ofInt(42)
            );

            List<Statement> doStmts = new ArrayList<>();
            doStmts.add(unused);
            BlockStmt doBody = new BlockStmt(doStmts);

            DoWhileStmt doWhile = new DoWhileStmt(
                doBody,
                LiteralExpr.ofBoolean(true)
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(doWhile);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleForStmt() {
            VarDeclStmt unused = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "unused",
                LiteralExpr.ofInt(42)
            );

            List<Statement> forStmts = new ArrayList<>();
            forStmts.add(unused);
            BlockStmt forBody = new BlockStmt(forStmts);

            ForStmt forStmt = new ForStmt(
                new ArrayList<>(),
                LiteralExpr.ofBoolean(true),
                new ArrayList<>(),
                forBody
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(forStmt);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleForEachStmt() {
            VarDeclStmt unused = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "unused",
                LiteralExpr.ofInt(42)
            );

            List<Statement> forEachStmts = new ArrayList<>();
            forEachStmts.add(unused);
            BlockStmt forEachBody = new BlockStmt(forEachStmts);

            ForEachStmt forEach = new ForEachStmt(
                new VarDeclStmt(PrimitiveSourceType.INT, "i", null),
                new VarRefExpr("arr", new ArraySourceType(PrimitiveSourceType.INT)),
                forEachBody
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(forEach);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleTryCatchStmt() {
            VarDeclStmt tryUnused = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "tryUnused",
                LiteralExpr.ofInt(1)
            );

            List<Statement> tryStmts = new ArrayList<>();
            tryStmts.add(tryUnused);
            BlockStmt tryBlock = new BlockStmt(tryStmts);

            VarDeclStmt catchUnused = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "catchUnused",
                LiteralExpr.ofInt(2)
            );

            List<Statement> catchStmts = new ArrayList<>();
            catchStmts.add(catchUnused);
            BlockStmt catchBlock = new BlockStmt(catchStmts);

            CatchClause catchClause = CatchClause.of(
                new ReferenceSourceType("Exception"),
                "e",
                catchBlock
            );

            TryCatchStmt tryCatch = new TryCatchStmt(
                tryBlock,
                Collections.singletonList(catchClause)
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(tryCatch);
            BlockStmt block = new BlockStmt(stmts);

            boolean changed = eliminator.transform(block);

            assertTrue(changed);
        }

        @Test
        void handleFinallyBlock() {
            VarDeclStmt finallyUnused = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "finallyUnused",
                LiteralExpr.ofInt(3)
            );

            List<Statement> finallyStmts = new ArrayList<>();
            finallyStmts.add(finallyUnused);
            BlockStmt finallyBlock = new BlockStmt(finallyStmts);

            TryCatchStmt tryCatch = new TryCatchStmt(
                new BlockStmt(),
                new ArrayList<>(),
                finallyBlock
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(tryCatch);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleSynchronizedStmt() {
            VarDeclStmt unused = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "unused",
                LiteralExpr.ofInt(42)
            );

            List<Statement> syncStmts = new ArrayList<>();
            syncStmts.add(unused);
            BlockStmt syncBody = new BlockStmt(syncStmts);

            SynchronizedStmt syncStmt = new SynchronizedStmt(
                new ThisExpr(new ReferenceSourceType("Object")),
                syncBody
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(syncStmt);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }

        @Test
        void handleLabeledStmt() {
            VarDeclStmt unused = new VarDeclStmt(
                PrimitiveSourceType.INT,
                "unused",
                LiteralExpr.ofInt(42)
            );

            List<Statement> labeledStmts = new ArrayList<>();
            labeledStmts.add(unused);
            BlockStmt labeledBody = new BlockStmt(labeledStmts);

            LabeledStmt labeled = new LabeledStmt(
                "label",
                labeledBody
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(labeled);
            BlockStmt block = new BlockStmt(stmts);

            eliminator.transform(block);
        }
    }
}
