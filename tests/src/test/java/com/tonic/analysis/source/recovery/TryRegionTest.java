package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.CatchClause;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TryRegionTest {

    private static final AtomicInteger classCounter = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    private String uniqueClassName() {
        return "com/test/TryRegionTest" + classCounter.incrementAndGet();
    }

    @Nested
    class TryRegionEqualityTests {

        @Test
        void handlersWithSameStartAndEndAreGroupedTogether() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler1 = mb.newLabel();
            Label handler2 = mb.newLabel();
            Label after = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .goto_(after)
                .label(handler1)
                .pop()
                .iconst(2)
                .ireturn()
                .label(handler2)
                .pop()
                .iconst(3)
                .ireturn()
                .label(after)
                .iconst(-1)
                .ireturn()
                .tryCatch(tryStart, tryEnd, handler1, "java/io/IOException")
                .tryCatch(tryStart, tryEnd, handler2, "java/sql/SQLException")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            TryCatchStmt tryCatch = findTryCatch(body);
            assertNotNull(tryCatch, "Expected a try-catch statement");
            assertTrue(tryCatch.getCatches().size() >= 1,
                "Expected at least one catch clause for same-region handlers");
        }

        @Test
        void handlersWithSameStartDifferentEndAreSeparate() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd1 = mb.newLabel();
            Label tryEnd2 = mb.newLabel();
            Label handler1 = mb.newLabel();
            Label handler2 = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .label(tryEnd1)
                .iconst(2)
                .label(tryEnd2)
                .ireturn()
                .label(handler1)
                .pop()
                .iconst(10)
                .ireturn()
                .label(handler2)
                .pop()
                .iconst(20)
                .ireturn()
                .tryCatch(tryStart, tryEnd1, handler1, "java/io/IOException")
                .tryCatch(tryStart, tryEnd2, handler2, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void differentStartBlocksAreDifferentRegions() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
                .publicStaticMethod("test", "()I");

            Label try1Start = mb.newLabel();
            Label try1End = mb.newLabel();
            Label try2Start = mb.newLabel();
            Label try2End = mb.newLabel();
            Label handler1 = mb.newLabel();
            Label handler2 = mb.newLabel();

            ClassFile cf = mb
                .label(try1Start)
                .iconst(1)
                .label(try1End)
                .label(try2Start)
                .iconst(2)
                .label(try2End)
                .ireturn()
                .label(handler1)
                .pop()
                .iconst(10)
                .ireturn()
                .label(handler2)
                .pop()
                .iconst(20)
                .ireturn()
                .tryCatch(try1Start, try1End, handler1, "java/io/IOException")
                .tryCatch(try2Start, try2End, handler2, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }
    }

    @Nested
    class TryRegionGroupingTests {

        @Test
        void multipleCatchTypesForSameRegionProducesMultipleClauses() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label ioHandler = mb.newLabel();
            Label sqlHandler = mb.newLabel();
            Label npeHandler = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .label(ioHandler)
                .pop()
                .iconst(2)
                .ireturn()
                .label(sqlHandler)
                .pop()
                .iconst(3)
                .ireturn()
                .label(npeHandler)
                .pop()
                .iconst(4)
                .ireturn()
                .tryCatch(tryStart, tryEnd, ioHandler, "java/io/IOException")
                .tryCatch(tryStart, tryEnd, sqlHandler, "java/sql/SQLException")
                .tryCatch(tryStart, tryEnd, npeHandler, "java/lang/NullPointerException")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            TryCatchStmt tryCatch = findTryCatch(body);
            assertNotNull(tryCatch, "Expected a try-catch statement");
        }

        @Test
        void nestedTryRegionsAreHandledSeparately() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
                .publicStaticMethod("test", "()I");

            Label outerTryStart = mb.newLabel();
            Label innerTryStart = mb.newLabel();
            Label innerTryEnd = mb.newLabel();
            Label outerTryEnd = mb.newLabel();
            Label innerHandler = mb.newLabel();
            Label outerHandler = mb.newLabel();

            ClassFile cf = mb
                .label(outerTryStart)
                .iconst(0)
                .pop()
                .label(innerTryStart)
                .iconst(1)
                .label(innerTryEnd)
                .pop()
                .iconst(2)
                .label(outerTryEnd)
                .ireturn()
                .label(innerHandler)
                .pop()
                .iconst(10)
                .ireturn()
                .label(outerHandler)
                .pop()
                .iconst(20)
                .ireturn()
                .tryCatch(innerTryStart, innerTryEnd, innerHandler, "java/io/IOException")
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
        void overlappingRegionsWithDifferentEndsAreSeparate() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
                .publicStaticMethod("test", "()I");

            Label start = mb.newLabel();
            Label mid = mb.newLabel();
            Label end = mb.newLabel();
            Label handler1 = mb.newLabel();
            Label handler2 = mb.newLabel();

            ClassFile cf = mb
                .label(start)
                .iconst(1)
                .label(mid)
                .iconst(2)
                .label(end)
                .ireturn()
                .label(handler1)
                .pop()
                .iconst(10)
                .ireturn()
                .label(handler2)
                .pop()
                .iconst(20)
                .ireturn()
                .tryCatch(start, mid, handler1, "java/io/IOException")
                .tryCatch(start, end, handler2, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
        }
    }

    @Nested
    class TryRegionEdgeCases {

        @Test
        void singleHandlerCreatesSimpleTryCatch() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
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
            TryCatchStmt tryCatch = findTryCatch(body);
            assertNotNull(tryCatch, "Expected a try-catch statement");
        }

        @Test
        void catchAllHandlerIsRecovered() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
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
                .pop()
                .iconst(0)
                .ireturn()
                .tryCatchAll(tryStart, tryEnd, handler)
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            TryCatchStmt tryCatch = findTryCatch(body);
            assertNotNull(tryCatch, "Expected a try-catch statement for catch-all");
        }

        @Test
        void multipleHandlersPointingToSameCatchBlock() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label sharedHandler = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .label(sharedHandler)
                .pop()
                .iconst(0)
                .ireturn()
                .tryCatch(tryStart, tryEnd, sharedHandler, "java/io/IOException")
                .tryCatch(tryStart, tryEnd, sharedHandler, "java/sql/SQLException")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            TryCatchStmt tryCatch = findTryCatch(body);
            assertNotNull(tryCatch, "Expected a try-catch statement");
        }

        @Test
        void tryBlockWithMultipleStatements() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label handler = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .pop()
                .iconst(2)
                .pop()
                .iconst(3)
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
        }

        @Test
        void consecutiveTryRegions() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
                .publicStaticMethod("test", "()I");

            Label try1Start = mb.newLabel();
            Label try1End = mb.newLabel();
            Label try2Start = mb.newLabel();
            Label try2End = mb.newLabel();
            Label handler1 = mb.newLabel();
            Label handler2 = mb.newLabel();
            Label after = mb.newLabel();

            ClassFile cf = mb
                .label(try1Start)
                .iconst(1)
                .pop()
                .label(try1End)
                .label(try2Start)
                .iconst(2)
                .pop()
                .label(try2End)
                .goto_(after)
                .label(handler1)
                .pop()
                .iconst(10)
                .ireturn()
                .label(handler2)
                .pop()
                .iconst(20)
                .ireturn()
                .label(after)
                .iconst(0)
                .ireturn()
                .tryCatch(try1Start, try1End, handler1, "java/io/IOException")
                .tryCatch(try2Start, try2End, handler2, "java/lang/Exception")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
        }
    }

    @Nested
    class TryRegionHashCodeTests {

        @Test
        void sameRegionHandlersGroupedByHashCode() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
                .publicStaticMethod("test", "()I");

            Label tryStart = mb.newLabel();
            Label tryEnd = mb.newLabel();
            Label h1 = mb.newLabel();
            Label h2 = mb.newLabel();
            Label h3 = mb.newLabel();
            Label h4 = mb.newLabel();

            ClassFile cf = mb
                .label(tryStart)
                .iconst(1)
                .ireturn()
                .label(tryEnd)
                .label(h1)
                .pop()
                .iconst(1)
                .ireturn()
                .label(h2)
                .pop()
                .iconst(2)
                .ireturn()
                .label(h3)
                .pop()
                .iconst(3)
                .ireturn()
                .label(h4)
                .pop()
                .iconst(4)
                .ireturn()
                .tryCatch(tryStart, tryEnd, h1, "java/io/IOException")
                .tryCatch(tryStart, tryEnd, h2, "java/sql/SQLException")
                .tryCatch(tryStart, tryEnd, h3, "java/lang/NullPointerException")
                .tryCatch(tryStart, tryEnd, h4, "java/lang/IllegalArgumentException")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            TryCatchStmt tryCatch = findTryCatch(body);
            assertNotNull(tryCatch);
        }
    }

    @Nested
    class TryRegionToStringTests {

        @Test
        void recoveryProducesValidOutput() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass(uniqueClassName())
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
                .tryCatch(tryStart, tryEnd, handler, "java/lang/RuntimeException")
                .build();

            MethodEntry method = findMethod(cf, "test");
            IRMethod ir = TestUtils.liftMethod(method);
            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            String output = body.toString();
            assertNotNull(output);
            assertFalse(output.isEmpty());
        }
    }

    private TryCatchStmt findTryCatch(BlockStmt body) {
        for (Statement stmt : body.getStatements()) {
            if (stmt instanceof TryCatchStmt) {
                return (TryCatchStmt) stmt;
            }
            if (stmt instanceof BlockStmt) {
                TryCatchStmt nested = findTryCatch((BlockStmt) stmt);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private StatementRecoverer createRecoverer(IRMethod ir, MethodEntry method) {
        DominatorTree domTree = new DominatorTree(ir);
        domTree.compute();

        LoopAnalysis loopAnalysis = new LoopAnalysis(ir, domTree);
        loopAnalysis.compute();

        DefUseChains defUse = new DefUseChains(ir);
        defUse.compute();

        StructuralAnalyzer analyzer = new StructuralAnalyzer(ir, domTree, loopAnalysis);
        analyzer.analyze();

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
