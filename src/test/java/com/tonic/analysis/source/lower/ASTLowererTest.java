package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ASTLowerer - lowering AST back to SSA IR.
 * Uses lenient assertions focusing on successful lowering rather than exact IR structure.
 */
class ASTLowererTest {

    private ClassPool pool;
    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/ASTLowererTestClass", access);
        constPool = classFile.getConstPool();

        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    // ========== Basic Lowering Tests ==========

    @Test
    void lowerEmptyMethodBody() {
        BlockStmt body = new BlockStmt();

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "emptyMethod",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertNotNull(irMethod);
        assertEquals("emptyMethod", irMethod.getName());
        assertEquals("com/test/Test", irMethod.getOwnerClass());
        assertTrue(irMethod.isStatic());
    }

    @Test
    void lowerMethodWithReturnStatement() {
        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();
        stmts.add(new ReturnStmt(LiteralExpr.ofInt(42)));
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "returnInt",
            "com/test/Test",
            true,
            List.of(),
            PrimitiveSourceType.INT
        );

        assertNotNull(irMethod);
        assertNotNull(irMethod.getEntryBlock());
        assertNotNull(irMethod.getEntryBlock().getTerminator());
    }

    @Test
    void lowerMethodWithVoidReturn() {
        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();
        stmts.add(new ReturnStmt());
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "voidMethod",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertNotNull(irMethod);
        assertNotNull(irMethod.getEntryBlock());
    }

    @Test
    void lowerStaticMethodHasNoThisParameter() {
        BlockStmt body = new BlockStmt();

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "staticMethod",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertTrue(irMethod.getParameters().isEmpty());
    }

    @Test
    void lowerInstanceMethodHasThisParameter() {
        BlockStmt body = new BlockStmt();

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "instanceMethod",
            "com/test/Test",
            false,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertFalse(irMethod.getParameters().isEmpty());
        assertEquals(1, irMethod.getParameters().size());
    }

    // ========== Parameter Handling Tests ==========

    @Test
    void lowerMethodWithSingleParameter() {
        BlockStmt body = new BlockStmt();
        List<SourceType> params = new ArrayList<>();
        params.add(PrimitiveSourceType.INT);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "withParam",
            "com/test/Test",
            true,
            params,
            VoidSourceType.INSTANCE
        );

        assertEquals(1, irMethod.getParameters().size());
    }

    @Test
    void lowerMethodWithMultipleParameters() {
        BlockStmt body = new BlockStmt();
        List<SourceType> params = new ArrayList<>();
        params.add(PrimitiveSourceType.INT);
        params.add(PrimitiveSourceType.LONG);
        params.add(PrimitiveSourceType.FLOAT);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "multiParam",
            "com/test/Test",
            true,
            params,
            VoidSourceType.INSTANCE
        );

        assertEquals(3, irMethod.getParameters().size());
    }

    @Test
    void lowerInstanceMethodParametersIncludeThis() {
        BlockStmt body = new BlockStmt();
        List<SourceType> params = new ArrayList<>();
        params.add(PrimitiveSourceType.INT);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "instanceWithParam",
            "com/test/Test",
            false,
            params,
            VoidSourceType.INSTANCE
        );

        assertEquals(2, irMethod.getParameters().size());
    }

    // ========== Variable Declaration Tests ==========

    @Test
    void lowerVariableDeclarationWithoutInitializer() {
        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "x"));
        stmts.add(new ReturnStmt());
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "declareVar",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertNotNull(irMethod);
        assertNotNull(irMethod.getEntryBlock());
    }

    @Test
    void lowerVariableDeclarationWithInitializer() {
        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "x", LiteralExpr.ofInt(10)));
        stmts.add(new ReturnStmt());
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "declareAndInit",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertNotNull(irMethod);
        assertTrue(irMethod.getEntryBlock().getInstructions().size() > 0);
    }

    // ========== Expression Lowering Tests ==========

    @Test
    void lowerAssignmentExpression() {
        BinaryExpr assignment = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(42),
            PrimitiveSourceType.INT
        );

        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "x"));
        stmts.add(new ExprStmt(assignment));
        stmts.add(new ReturnStmt());
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "assignVar",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertNotNull(irMethod);
        assertTrue(irMethod.getInstructionCount() > 0);
    }

    @Test
    void lowerBinaryExpression() {
        BinaryExpr addition = new BinaryExpr(
            BinaryOperator.ADD,
            LiteralExpr.ofInt(1),
            LiteralExpr.ofInt(2),
            PrimitiveSourceType.INT
        );

        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();
        stmts.add(new ReturnStmt(addition));
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "addNumbers",
            "com/test/Test",
            true,
            List.of(),
            PrimitiveSourceType.INT
        );

        assertNotNull(irMethod);
        assertNotNull(irMethod.getEntryBlock());
    }

    @Test
    void lowerLiteralExpression() {
        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();
        stmts.add(new ReturnStmt(LiteralExpr.ofInt(123)));
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "returnLiteral",
            "com/test/Test",
            true,
            List.of(),
            PrimitiveSourceType.INT
        );

        assertNotNull(irMethod);
        assertNotNull(irMethod.getEntryBlock().getTerminator());
    }

    // ========== Method Descriptor Tests ==========

    @Test
    void lowerMethodBuildsCorrectDescriptorForNoParams() {
        BlockStmt body = new BlockStmt();

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "noParams",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertEquals("()V", irMethod.getDescriptor());
    }

    @Test
    void lowerMethodBuildsCorrectDescriptorWithIntReturn() {
        BlockStmt body = new BlockStmt();

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "returnInt",
            "com/test/Test",
            true,
            List.of(),
            PrimitiveSourceType.INT
        );

        assertEquals("()I", irMethod.getDescriptor());
    }

    @Test
    void lowerMethodBuildsCorrectDescriptorWithParams() {
        BlockStmt body = new BlockStmt();
        List<SourceType> params = new ArrayList<>();
        params.add(PrimitiveSourceType.INT);
        params.add(PrimitiveSourceType.LONG);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "withParams",
            "com/test/Test",
            true,
            params,
            VoidSourceType.INSTANCE
        );

        assertEquals("(IJ)V", irMethod.getDescriptor());
    }

    // ========== Block Structure Tests ==========

    @Test
    void lowerMethodCreatesEntryBlock() {
        BlockStmt body = new BlockStmt();

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "hasEntry",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertNotNull(irMethod.getEntryBlock());
    }

    @Test
    void lowerMethodEntryBlockIsInBlockList() {
        BlockStmt body = new BlockStmt();

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "entryInList",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertTrue(irMethod.getBlocks().contains(irMethod.getEntryBlock()));
    }

    @Test
    void lowerMethodAddsImplicitReturnIfMissing() {
        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "x", LiteralExpr.ofInt(1)));
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "implicitReturn",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertNotNull(irMethod.getEntryBlock().getTerminator());
    }

    // ========== Existing IRMethod Lowering Tests ==========

    @Test
    void replaceBodyReplacesExistingBlocks() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "original", "V");
        IRMethod irMethod = new IRMethod("com/test/Test", "original", "()V", true);
        irMethod.addBlock(new IRBlock("oldBlock"));

        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();
        stmts.add(new ReturnStmt());
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        lowerer.replaceBody(body, irMethod);

        assertNotNull(irMethod.getEntryBlock());
    }

    @Test
    void replaceBodySetsNewEntryBlock() {
        IRMethod irMethod = new IRMethod("com/test/Test", "test", "()V", true);

        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();
        stmts.add(new ReturnStmt());
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        lowerer.replaceBody(body, irMethod);

        assertNotNull(irMethod.getEntryBlock());
    }

    // ========== Static Convenience Method Tests ==========

    @Test
    void staticLowerMethodWorks() {
        BlockStmt body = new BlockStmt();

        IRMethod irMethod = ASTLowerer.lowerMethod(
            body,
            "staticHelper",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE,
            constPool,
            pool
        );

        assertNotNull(irMethod);
        assertEquals("staticHelper", irMethod.getName());
    }

    // ========== Complex Lowering Tests ==========

    @Test
    void lowerMethodWithMultipleStatements() {
        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "a", LiteralExpr.ofInt(1)));
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "b", LiteralExpr.ofInt(2)));

        BinaryExpr sum = new BinaryExpr(
            BinaryOperator.ADD,
            new VarRefExpr("a", PrimitiveSourceType.INT),
            new VarRefExpr("b", PrimitiveSourceType.INT),
            PrimitiveSourceType.INT
        );
        stmts.add(new ReturnStmt(sum));
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "sumTwoVars",
            "com/test/Test",
            true,
            List.of(),
            PrimitiveSourceType.INT
        );

        assertNotNull(irMethod);
        assertTrue(irMethod.getInstructionCount() > 0);
    }

    @Test
    void lowerMethodDoesNotThrowOnComplexAST() {
        List<com.tonic.analysis.source.ast.stmt.Statement> stmts = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            stmts.add(new VarDeclStmt(
                PrimitiveSourceType.INT,
                "var" + i,
                LiteralExpr.ofInt(i)
            ));
        }

        stmts.add(new ReturnStmt(new VarRefExpr("var0", PrimitiveSourceType.INT)));
        BlockStmt body = new BlockStmt(stmts);

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);

        assertDoesNotThrow(() -> lowerer.lower(
            body,
            "complex",
            "com/test/Test",
            true,
            List.of(),
            PrimitiveSourceType.INT
        ));
    }

    @Test
    void lowerMethodPreservesMethodName() {
        BlockStmt body = new BlockStmt();

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "mySpecialMethod",
            "com/test/Test",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertEquals("mySpecialMethod", irMethod.getName());
    }

    @Test
    void lowerMethodPreservesOwnerClass() {
        BlockStmt body = new BlockStmt();

        ASTLowerer lowerer = new ASTLowerer(constPool, pool);
        IRMethod irMethod = lowerer.lower(
            body,
            "test",
            "com/example/MyClass",
            true,
            List.of(),
            VoidSourceType.INSTANCE
        );

        assertEquals("com/example/MyClass", irMethod.getOwnerClass());
    }
}
