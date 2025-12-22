package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.TryCatchStmt;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.BytecodeBuilder.Label;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for StatementRecoverer.TryRegion exception handling recovery.
 * Tests cover basic try-catch, control flow, nested structures, and edge cases.
 */
class TryCatchRecoveryTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    // ========== Basic Try-Catch Tests ==========

    @Nested
    class BasicTryCatchTests {

        @Test
        void simpleTryCatchWithSingleExceptionType() throws IOException {
            // try { return 1; } catch (Exception e) { return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler = mb.newLabel();
            Label after = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .goto_(after)
                .label(handler)
                .pop()  // Pop exception object
                .iconst(0)
                .ireturn()
                .label(after)
                .iconst(-1)
                .ireturn()
                .tryCatch(tryStart, tryEnd, handler, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());

            // Should contain try-catch structure
            boolean hasTryCatch = body.getStatements().stream()
                .anyMatch(s -> s instanceof TryCatchStmt);
            assertTrue(hasTryCatch, "Expected try-catch statement");
        }

        @Test
        void tryCatchWithCatchAll() throws IOException {
            // try { return 1; } finally { /* cleanup */ }
            // Represented as catch-all handler
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .label(handler)
                .pop()  // Pop exception object
                .iconst(0)
                .ireturn()
                .tryCatchAll(tryStart, tryEnd, handler)
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void tryCatchWithMultipleCatchBlocks() throws IOException {
            // try { return 1; } catch (RuntimeException e) { return 2; } catch (Exception e) { return 3; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label runtimeHandler = mb.newLabel();
            Label exceptionHandler = mb.newLabel();
            Label after = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .goto_(after)
                .label(runtimeHandler)
                .pop()
                .iconst(2)
                .ireturn()
                .label(exceptionHandler)
                .pop()
                .iconst(3)
                .ireturn()
                .label(after)
                .iconst(-1)
                .ireturn()
                .tryCatch(tryStart, tryEnd, runtimeHandler, "java/lang/RuntimeException")
                .tryCatch(tryStart, tryEnd, exceptionHandler, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());

            // Should contain try-catch with multiple handlers
            boolean hasTryCatch = body.getStatements().stream()
                .anyMatch(s -> s instanceof TryCatchStmt);
            assertTrue(hasTryCatch, "Expected try-catch statement");
        }

        @Test
        void tryCatchWithNoThrowInTryBlock() throws IOException {
            // try { int x = 1; return x; } catch (Exception e) { return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler = mb.newLabel();
            Label after = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .istore(0)
                .iload(0)
                .ireturn()
                .label(tryEnd)
                .goto_(after)
                .label(handler)
                .pop()
                .iconst(0)
                .ireturn()
                .label(after)
                .iconst(-1)
                .ireturn()
                .tryCatch(tryStart, tryEnd, handler, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }
    }

    // ========== Control Flow Tests ==========

    @Nested
    class ControlFlowTests {

        @Test
        void tryCatchWithReturnInTryBlock() throws IOException {
            // try { return 42; } catch (Exception e) { return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(42)
                .ireturn()
                .label(tryEnd)
                .label(handler)
                .pop()
                .iconst(0)
                .ireturn()
                .tryCatch(tryStart, tryEnd, handler, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void tryCatchWithReturnInCatchBlock() throws IOException {
            // try { int x = 1; } catch (Exception e) { return -1; } return 0;
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler = mb.newLabel();
            Label after = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .istore(0)
                .label(tryEnd)
                .goto_(after)
                .label(handler)
                .pop()
                .iconst(-1)
                .ireturn()
                .label(after)
                .iconst(0)
                .ireturn()
                .tryCatch(tryStart, tryEnd, handler, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void tryCatchWithThrowInCatchBlock() throws IOException {
            // try { return 1; } catch (Exception e) { throw e; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .label(handler)
                .astore(0)  // Store exception
                .aload(0)   // Load exception
                .athrow()   // Rethrow
                .tryCatch(tryStart, tryEnd, handler, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void tryCatchFinallyPattern() throws IOException {
            // try { return 1; } catch (Exception e) { return 2; } finally { /* cleanup */ }
            // Finally is represented as a catch-all handler
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label catchHandler = mb.newLabel();
            Label finallyHandler = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .label(catchHandler)
                .pop()
                .iconst(2)
                .ireturn()
                .label(finallyHandler)
                .pop()
                .iconst(3)
                .ireturn()
                .tryCatch(tryStart, tryEnd, catchHandler, "java/lang/Exception")
                .tryCatchAll(tryStart, tryEnd, finallyHandler)
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }
    }

    // ========== Nested Structure Tests ==========

    @Nested
    class NestedStructureTests {

        @Test
        void nestedTryCatchBlocks() throws IOException {
            // try { try { return 1; } catch (RuntimeException e) { return 2; } } catch (Exception e) { return 3; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label outerTryStart = mb.newLabel();
            Label innerTryStart = mb.newLabel();
            Label innerTryEnd = mb.newLabel();
            Label innerHandler = mb.newLabel();
            Label outerTryEnd = mb.newLabel();
            Label outerHandler = mb.newLabel();

            ClassFile cf = mb
                .label(outerTryStart)
                .label(innerTryStart)
                .iconst(1)
                .ireturn()
                .label(innerTryEnd)
                .label(innerHandler)
                .pop()
                .iconst(2)
                .ireturn()
                .label(outerTryEnd)
                .label(outerHandler)
                .pop()
                .iconst(3)
                .ireturn()
                .tryCatch(innerTryStart, innerTryEnd, innerHandler, "java/lang/RuntimeException")
                .tryCatch(outerTryStart, outerTryEnd, outerHandler, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void tryCatchInsideLoop() throws IOException {
            // for (int i = 0; i < 3; i++) { try { continue; } catch (Exception e) { break; } }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()V");

            Label loopStart = mb.newLabel();
            Label loopEnd = mb.newLabel();
            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler = mb.newLabel();
            Label increment = mb.newLabel();

            ClassFile cf = mb
                .iconst(0)
                .istore(0)  // i = 0
                .label(loopStart)
                .iload(0)
                .iconst(3)
                .if_icmpge(loopEnd)  // if i >= 3, exit
                .label(tryStart)
                .goto_(increment)  // continue
                .label(tryEnd)
                .goto_(increment)
                .label(handler)
                .pop()
                .goto_(loopEnd)  // break
                .label(increment)
                .iinc(0, 1)  // i++
                .goto_(loopStart)
                .label(loopEnd)
                .vreturn()
                .tryCatch(tryStart, tryEnd, handler, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void loopInsideTryBlock() throws IOException {
            // try { for (int i = 0; i < 3; i++) { } return 1; } catch (Exception e) { return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label loopStart = mb.newLabel();
            Label loopEnd = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(0)
                .istore(0)  // i = 0
                .label(loopStart)
                .iload(0)
                .iconst(3)
                .if_icmpge(loopEnd)  // if i >= 3, exit
                .iinc(0, 1)  // i++
                .goto_(loopStart)
                .label(loopEnd)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .label(handler)
                .pop()
                .iconst(0)
                .ireturn()
                .tryCatch(tryStart, tryEnd, handler, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void tryCatchInsideIfElse() throws IOException {
            // if (x > 0) { try { return 1; } catch (Exception e) { return 2; } } else { return 3; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "(I)I");

            Label elseLabel = mb.newLabel();
            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler = mb.newLabel();

            ClassFile cf = mb
                .iload(0)
                .ifle(elseLabel)  // if x <= 0, go to else
                .label(tryStart)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .label(handler)
                .pop()
                .iconst(2)
                .ireturn()
                .label(elseLabel)
                .iconst(3)
                .ireturn()
                .tryCatch(tryStart, tryEnd, handler, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }
    }

    // ========== Edge Case Tests ==========

    @Nested
    class EdgeCaseTests {

        @Test
        void multipleExceptionHandlersForSameRange() throws IOException {
            // try { return 1; } catch (IOException e) { return 2; } catch (SQLException e) { return 3; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label ioHandler = mb.newLabel();
            Label sqlHandler = mb.newLabel();
            Label after = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .goto_(after)
                .label(ioHandler)
                .pop()
                .iconst(2)
                .ireturn()
                .label(sqlHandler)
                .pop()
                .iconst(3)
                .ireturn()
                .label(after)
                .iconst(-1)
                .ireturn()
                .tryCatch(tryStart, tryEnd, ioHandler, "java/io/IOException")
                .tryCatch(tryStart, tryEnd, sqlHandler, "java/sql/SQLException")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void overlappingTryRegions() throws IOException {
            // Complex case: overlapping try regions with different handlers
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label try1Start = mb.newLabel();
            Label try1End = mb.newLabel();
            Label try2Start = mb.newLabel();
            Label try2End = mb.newLabel();
            Label handler1 = mb.newLabel();
            Label handler2 = mb.newLabel();

            ClassFile cf = mb
                .label(try1Start)
                .label(try2Start)
                .iconst(1)
                .istore(0)
                .label(try2End)
                .iload(0)
                .ireturn()
                .label(try1End)
                .label(handler1)
                .pop()
                .iconst(2)
                .ireturn()
                .label(handler2)
                .pop()
                .iconst(3)
                .ireturn()
                .tryCatch(try1Start, try1End, handler1, "java/lang/Exception")
                .tryCatch(try2Start, try2End, handler2, "java/lang/RuntimeException")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void tryBlockThatAlwaysThrows() throws IOException {
            // try { throw new Exception(); } catch (Exception e) { return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryTest")
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                // new Exception() - simplified version, just load null and throw
                .aconst_null()
                .athrow()
                .label(tryEnd)
                .label(handler)
                .pop()
                .iconst(0)
                .ireturn()
                .tryCatch(tryStart, tryEnd, handler, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }
    }

    // ========== Helper Methods ==========

    private StatementRecoverer createRecoverer(IRMethod ir, MethodEntry method) {
        // Create analysis components
        DominatorTree domTree = new DominatorTree(ir);
        domTree.compute();

        LoopAnalysis loopAnalysis = new LoopAnalysis(ir, domTree);
        loopAnalysis.compute();

        DefUseChains defUse = new DefUseChains(ir);
        defUse.compute();

        StructuralAnalyzer analyzer = new StructuralAnalyzer(ir, domTree, loopAnalysis);
        analyzer.analyze();

        // Create recovery components
        RecoveryContext recoveryContext = new RecoveryContext(ir, method, defUse);
        ExpressionRecoverer exprRecoverer = new ExpressionRecoverer(recoveryContext);
        ControlFlowContext cfContext = new ControlFlowContext(ir, domTree, loopAnalysis, recoveryContext);

        return new StatementRecoverer(cfContext, analyzer, exprRecoverer);
    }

    private MethodEntry findMethod(ClassFile cf, String name) {
        return cf.getMethods().stream()
            .filter(m -> m.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Method not found: " + name));
    }
}
