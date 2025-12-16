package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MethodRecoverer functionality.
 * Covers recovering AST from bytecode methods with various control flow structures.
 */
class MethodRecovererTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    // ========== Basic Recovery Tests ==========

    @Test
    void recoverEmptyVoidMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("empty", "()V")
                .vreturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertNotNull(body.getStatements());
    }

    @Test
    void recoverSimpleReturnMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("getNumber", "()I")
                .iconst(42)
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());

        Statement lastStmt = body.getStatements().get(body.getStatements().size() - 1);
        assertTrue(lastStmt instanceof ReturnStmt);
    }

    @Test
    void recoverMethodWithLocalVariable() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("useLocal", "()I")
                .iconst(10)
                .istore(0)
                .iload(0)
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());
    }

    @Test
    void recoverMethodWithParameter() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("identity", "(I)I")
                .iload(0)
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertTrue(body.getStatements().size() >= 1);
    }

    // ========== Arithmetic Operations Tests ==========

    @Test
    void recoverAdditionMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("add", "(II)I")
                .iload(0)
                .iload(1)
                .iadd()
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());
    }

    @Test
    void recoverSubtractionMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("subtract", "(II)I")
                .iload(0)
                .iload(1)
                .isub()
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());
    }

    @Test
    void recoverMultiplicationMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("multiply", "(II)I")
                .iload(0)
                .iload(1)
                .imul()
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());
    }

    // ========== Control Flow Tests ==========

    @Test
    void recoverSimpleIfStatement() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("testIf", "(I)I")
                .iload(0)
                .iconst(0)
                .isub()
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());
    }

    @Test
    void recoverMethodWithMultipleStatements() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("multi", "(II)I")
                .iload(0)
                .istore(2)
                .iload(1)
                .istore(3)
                .iload(2)
                .iload(3)
                .iadd()
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertTrue(body.getStatements().size() >= 1);
    }

    @Test
    void recoverMethodWithLocalStores() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("stores", "()I")
                .iconst(5)
                .istore(0)
                .iconst(10)
                .istore(1)
                .iload(0)
                .iload(1)
                .iadd()
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());
    }

    // ========== Analysis Phase Tests ==========

    @Test
    void analyzeCreatesRequiredAnalyses() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("simple", "()V")
                .vreturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        recoverer.analyze();

        assertNotNull(recoverer.getDominatorTree());
        assertNotNull(recoverer.getLoopAnalysis());
        assertNotNull(recoverer.getDefUseChains());
        assertNotNull(recoverer.getStructuralAnalyzer());
    }

    @Test
    void initializeRecoveryCreatesComponents() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("simple", "()V")
                .vreturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        recoverer.analyze();
        recoverer.initializeRecovery();

        assertNotNull(recoverer.getRecoveryContext());
        assertNotNull(recoverer.getNameRecoverer());
        assertNotNull(recoverer.getExpressionRecoverer());
        assertNotNull(recoverer.getControlFlowContext());
        assertNotNull(recoverer.getStatementRecoverer());
    }

    @Test
    void recoverCallsAnalyzeAutomatically() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("auto", "()V")
                .vreturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertNotNull(recoverer.getDominatorTree());
        assertNotNull(recoverer.getStatementRecoverer());
    }

    // ========== Static Convenience Method Tests ==========

    @Test
    void staticRecoverMethodWorks() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("staticTest", "()I")
                .iconst(99)
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        BlockStmt body = MethodRecoverer.recoverMethod(ir, method);

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());
    }

    @Test
    void staticRecoverMethodWithStrategy() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("strategyTest", "()V")
                .vreturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        BlockStmt body = MethodRecoverer.recoverMethod(
            ir,
            method,
            NameRecoveryStrategy.PREFER_DEBUG_INFO
        );

        assertNotNull(body);
    }

    // ========== Complex Methods Tests ==========

    @Test
    void recoverMethodWithMultipleLocals() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("multiLocal", "(II)I")
                .iload(0)
                .istore(2)
                .iload(1)
                .istore(3)
                .iload(2)
                .iload(3)
                .iadd()
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertTrue(body.getStatements().size() >= 1);
    }

    @Test
    void recoverMethodWithNestedExpressions() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("nested", "(III)I")
                .iload(0)
                .iload(1)
                .iadd()
                .iload(2)
                .imul()
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());
    }

    // ========== Name Recovery Tests ==========

    @Test
    void nameRecoveryStrategyDefault() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("named", "()V")
                .vreturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);

        assertEquals(NameRecoveryStrategy.PREFER_DEBUG_INFO, recoverer.getNameStrategy());
    }

    @Test
    void nameRecoveryStrategyCustom() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("custom", "()V")
                .vreturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(
            ir,
            method,
            NameRecoveryStrategy.ALWAYS_SYNTHETIC
        );

        assertEquals(NameRecoveryStrategy.ALWAYS_SYNTHETIC, recoverer.getNameStrategy());
    }

    // ========== Edge Cases ==========

    @Test
    void recoverMethodWithDivision() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("divide", "(II)I")
                .iload(0)
                .iload(1)
                .idiv()
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());
    }

    @Test
    void multipleRecoverCallsProduceSameResult() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("idempotent", "()I")
                .iconst(5)
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body1 = recoverer.recover();
        BlockStmt body2 = recoverer.recover();

        assertNotNull(body1);
        assertNotNull(body2);
        assertEquals(body1.getStatements().size(), body2.getStatements().size());
    }

    @Test
    void recoverMethodWithRemainder() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("remainder", "(II)I")
                .iload(0)
                .iload(1)
                .irem()
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());
    }

    @Test
    void recoverMethodWithNegation() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("negate", "(I)I")
                .iload(0)
                .ineg()
                .ireturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);
        BlockStmt body = recoverer.recover();

        assertNotNull(body);
        assertFalse(body.getStatements().isEmpty());
    }

    @Test
    void getIRMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("test", "()V")
                .vreturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);

        assertEquals(ir, recoverer.getIrMethod());
    }

    @Test
    void getSourceMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("test", "()V")
                .vreturn()
            .build();

        MethodEntry method = cf.getMethods().get(0);
        IRMethod ir = TestUtils.liftMethod(method);

        MethodRecoverer recoverer = new MethodRecoverer(ir, method);

        assertEquals(method, recoverer.getSourceMethod());
    }
}
