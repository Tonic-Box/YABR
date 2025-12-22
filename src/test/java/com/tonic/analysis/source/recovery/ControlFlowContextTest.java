package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.source.recovery.ControlFlowContext.StructuredRegion;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ControlFlowContext.
 * Covers state management, statement handling, stop blocks stack, and structured regions.
 */
class ControlFlowContextTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    // ========== Constructor and Getters Tests ==========

    @Nested
    class ConstructorAndGetterTests {

        @Test
        void constructorInitializesAllFields() throws IOException {
            IRMethod method = createSimpleMethod();
            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();
            RecoveryContext recoveryContext = createRecoveryContext(method);

            ControlFlowContext context = new ControlFlowContext(
                method, domTree, loopAnalysis, recoveryContext
            );

            assertEquals(method, context.getIrMethod());
            assertEquals(domTree, context.getDominatorTree());
            assertEquals(loopAnalysis, context.getLoopAnalysis());
            assertEquals(recoveryContext, context.getExpressionContext());
        }

        @Test
        void initialStateIsEmpty() throws IOException {
            ControlFlowContext context = createContext();

            assertNotNull(context.getProcessedBlocks());
            assertTrue(context.getProcessedBlocks().isEmpty());
            assertNotNull(context.getBlockStatements());
            assertTrue(context.getBlockStatements().isEmpty());
            assertNotNull(context.getBlockToRegion());
            assertTrue(context.getBlockToRegion().isEmpty());
            assertNotNull(context.getBlockLabels());
            assertTrue(context.getBlockLabels().isEmpty());
            assertNotNull(context.getPendingStatements());
            assertTrue(context.getPendingStatements().isEmpty());
            assertNotNull(context.getStopBlocksStack());
            assertTrue(context.getStopBlocksStack().isEmpty());
        }

        @Test
        void labelCounterStartsAtZero() throws IOException {
            ControlFlowContext context = createContext();
            IRMethod method = context.getIrMethod();
            IRBlock block = method.getEntryBlock();

            String label = context.getOrCreateLabel(block);
            assertEquals("label0", label);
        }
    }

    // ========== Processed Blocks Tests ==========

    @Nested
    class ProcessedBlocksTests {

        @Test
        void markProcessedAddsBlock() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();

            assertFalse(context.isProcessed(block));

            context.markProcessed(block);

            assertTrue(context.isProcessed(block));
        }

        @Test
        void markProcessedMultipleBlocks() throws IOException {
            ControlFlowContext context = createContext();
            IRMethod method = context.getIrMethod();
            IRBlock block1 = method.getEntryBlock();
            IRBlock block2 = new IRBlock("second");
            method.addBlock(block2);

            context.markProcessed(block1);
            context.markProcessed(block2);

            assertTrue(context.isProcessed(block1));
            assertTrue(context.isProcessed(block2));
            assertEquals(2, context.getProcessedBlocks().size());
        }

        @Test
        void isProcessedReturnsFalseForUnmarkedBlock() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock unmarked = new IRBlock("unmarked");

            assertFalse(context.isProcessed(unmarked));
        }

        @Test
        void markProcessedIsIdempotent() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();

            context.markProcessed(block);
            context.markProcessed(block);
            context.markProcessed(block);

            assertEquals(1, context.getProcessedBlocks().size());
            assertTrue(context.isProcessed(block));
        }
    }

    // ========== Statement Management Tests ==========

    @Nested
    class StatementManagementTests {

        @Test
        void setStatementsStoresStatements() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();
            List<Statement> statements = Arrays.asList(
                new ReturnStmt(null)
            );

            context.setStatements(block, statements);

            List<Statement> retrieved = context.getStatements(block);
            assertEquals(1, retrieved.size());
            assertSame(statements, retrieved);
        }

        @Test
        void getStatementsReturnsEmptyListForUnsetBlock() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = new IRBlock("unset");

            List<Statement> statements = context.getStatements(block);

            assertNotNull(statements);
            assertTrue(statements.isEmpty());
        }

        @Test
        void setStatementsReplacesExisting() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();
            List<Statement> first = Arrays.asList(new ReturnStmt(null));
            List<Statement> second = Arrays.asList(
                new ReturnStmt(null),
                new ReturnStmt(null)
            );

            context.setStatements(block, first);
            context.setStatements(block, second);

            List<Statement> retrieved = context.getStatements(block);
            assertEquals(2, retrieved.size());
            assertSame(second, retrieved);
        }

        @Test
        void setStatementsWithEmptyList() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();
            List<Statement> empty = Collections.emptyList();

            context.setStatements(block, empty);

            List<Statement> retrieved = context.getStatements(block);
            assertTrue(retrieved.isEmpty());
        }

        @Test
        void setStatementsForMultipleBlocks() throws IOException {
            ControlFlowContext context = createContext();
            IRMethod method = context.getIrMethod();
            IRBlock block1 = method.getEntryBlock();
            IRBlock block2 = new IRBlock("second");
            method.addBlock(block2);

            List<Statement> stmts1 = Arrays.asList(new ReturnStmt(null));
            List<Statement> stmts2 = Arrays.asList(
                new ReturnStmt(null),
                new ReturnStmt(null)
            );

            context.setStatements(block1, stmts1);
            context.setStatements(block2, stmts2);

            assertEquals(1, context.getStatements(block1).size());
            assertEquals(2, context.getStatements(block2).size());
        }
    }

    // ========== Structured Region Tests ==========

    @Nested
    class StructuredRegionTests {

        @Test
        void setRegionStoresRegion() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();

            context.setRegion(block, StructuredRegion.IF_THEN);

            assertEquals(StructuredRegion.IF_THEN, context.getRegion(block));
        }

        @Test
        void getRegionReturnsNullForUnsetBlock() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = new IRBlock("unset");

            assertNull(context.getRegion(block));
        }

        @Test
        void setRegionReplacesExisting() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();

            context.setRegion(block, StructuredRegion.IF_THEN);
            context.setRegion(block, StructuredRegion.WHILE_LOOP);

            assertEquals(StructuredRegion.WHILE_LOOP, context.getRegion(block));
        }

        @Test
        void setDifferentRegionsForDifferentBlocks() throws IOException {
            ControlFlowContext context = createContext();
            IRMethod method = context.getIrMethod();
            IRBlock block1 = method.getEntryBlock();
            IRBlock block2 = new IRBlock("second");
            method.addBlock(block2);

            context.setRegion(block1, StructuredRegion.IF_THEN);
            context.setRegion(block2, StructuredRegion.FOR_LOOP);

            assertEquals(StructuredRegion.IF_THEN, context.getRegion(block1));
            assertEquals(StructuredRegion.FOR_LOOP, context.getRegion(block2));
        }

        @Test
        void allStructuredRegionTypesSupported() throws IOException {
            ControlFlowContext context = createContext();
            IRMethod method = context.getIrMethod();

            for (StructuredRegion region : StructuredRegion.values()) {
                IRBlock block = new IRBlock("test_" + region);
                method.addBlock(block);
                context.setRegion(block, region);
                assertEquals(region, context.getRegion(block));
            }
        }
    }

    // ========== Label Management Tests ==========

    @Nested
    class LabelManagementTests {

        @Test
        void getOrCreateLabelCreatesNewLabel() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();

            String label = context.getOrCreateLabel(block);

            assertNotNull(label);
            assertEquals("label0", label);
        }

        @Test
        void getOrCreateLabelReturnsExistingLabel() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();

            String label1 = context.getOrCreateLabel(block);
            String label2 = context.getOrCreateLabel(block);

            assertEquals(label1, label2);
        }

        @Test
        void labelCounterIncrementsForEachNewBlock() throws IOException {
            ControlFlowContext context = createContext();
            IRMethod method = context.getIrMethod();
            IRBlock block1 = method.getEntryBlock();
            IRBlock block2 = new IRBlock("second");
            IRBlock block3 = new IRBlock("third");

            String label1 = context.getOrCreateLabel(block1);
            String label2 = context.getOrCreateLabel(block2);
            String label3 = context.getOrCreateLabel(block3);

            assertEquals("label0", label1);
            assertEquals("label1", label2);
            assertEquals("label2", label3);
        }

        @Test
        void hasLabelReturnsFalseForUnlabeledBlock() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();

            assertFalse(context.hasLabel(block));
        }

        @Test
        void hasLabelReturnsTrueAfterCreation() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();

            context.getOrCreateLabel(block);

            assertTrue(context.hasLabel(block));
        }

        @Test
        void getLabelReturnsNullForUnlabeledBlock() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();

            assertNull(context.getLabel(block));
        }

        @Test
        void getLabelReturnsLabelAfterCreation() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock block = context.getIrMethod().getEntryBlock();

            String created = context.getOrCreateLabel(block);
            String retrieved = context.getLabel(block);

            assertEquals(created, retrieved);
        }
    }

    // ========== Pending Statements Tests ==========

    @Nested
    class PendingStatementsTests {

        @Test
        void addPendingStatementsStoresStatements() throws IOException {
            ControlFlowContext context = createContext();
            List<Statement> statements = Arrays.asList(new ReturnStmt(null));

            context.addPendingStatements(statements);

            assertEquals(1, context.getPendingStatements().size());
        }

        @Test
        void addPendingStatementsAppendsToExisting() throws IOException {
            ControlFlowContext context = createContext();
            List<Statement> first = Arrays.asList(new ReturnStmt(null));
            List<Statement> second = Arrays.asList(new ReturnStmt(null));

            context.addPendingStatements(first);
            context.addPendingStatements(second);

            assertEquals(2, context.getPendingStatements().size());
        }

        @Test
        void collectPendingStatementsReturnsAndClears() throws IOException {
            ControlFlowContext context = createContext();
            List<Statement> statements = Arrays.asList(
                new ReturnStmt(null),
                new ReturnStmt(null)
            );
            context.addPendingStatements(statements);

            List<Statement> collected = context.collectPendingStatements();

            assertEquals(2, collected.size());
            assertTrue(context.getPendingStatements().isEmpty());
        }

        @Test
        void collectPendingStatementsReturnsEmptyWhenNoPending() throws IOException {
            ControlFlowContext context = createContext();

            List<Statement> collected = context.collectPendingStatements();

            assertNotNull(collected);
            assertTrue(collected.isEmpty());
        }

        @Test
        void collectPendingStatementsCreatesNewList() throws IOException {
            ControlFlowContext context = createContext();
            List<Statement> statements = Arrays.asList(new ReturnStmt(null));
            context.addPendingStatements(statements);

            List<Statement> collected1 = context.collectPendingStatements();
            context.addPendingStatements(statements);
            List<Statement> collected2 = context.collectPendingStatements();

            assertNotSame(collected1, collected2);
            assertEquals(1, collected1.size());
            assertEquals(1, collected2.size());
        }

        @Test
        void multipleCollectsWithoutAdding() throws IOException {
            ControlFlowContext context = createContext();
            List<Statement> statements = Arrays.asList(new ReturnStmt(null));
            context.addPendingStatements(statements);

            List<Statement> collected1 = context.collectPendingStatements();
            List<Statement> collected2 = context.collectPendingStatements();

            assertEquals(1, collected1.size());
            assertTrue(collected2.isEmpty());
        }

        @Test
        void addEmptyPendingStatements() throws IOException {
            ControlFlowContext context = createContext();
            List<Statement> empty = Collections.emptyList();

            context.addPendingStatements(empty);

            assertTrue(context.getPendingStatements().isEmpty());
        }
    }

    // ========== Stop Blocks Stack Tests ==========

    @Nested
    class StopBlocksStackTests {

        @Test
        void pushStopBlocksAddsToStack() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock stop1 = new IRBlock("stop1");
            Set<IRBlock> stopSet = new HashSet<>(Arrays.asList(stop1));

            context.pushStopBlocks(stopSet);

            assertEquals(1, context.getStopBlocksStack().size());
        }

        @Test
        void popStopBlocksRemovesFromStack() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock stop1 = new IRBlock("stop1");
            Set<IRBlock> stopSet = new HashSet<>(Arrays.asList(stop1));

            context.pushStopBlocks(stopSet);
            context.popStopBlocks();

            assertTrue(context.getStopBlocksStack().isEmpty());
        }

        @Test
        void popStopBlocksOnEmptyStackDoesNotThrow() throws IOException {
            ControlFlowContext context = createContext();

            assertDoesNotThrow(() -> context.popStopBlocks());
        }

        @Test
        void multiplePushAndPop() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock stop1 = new IRBlock("stop1");
            IRBlock stop2 = new IRBlock("stop2");
            Set<IRBlock> set1 = new HashSet<>(Arrays.asList(stop1));
            Set<IRBlock> set2 = new HashSet<>(Arrays.asList(stop2));

            context.pushStopBlocks(set1);
            context.pushStopBlocks(set2);
            assertEquals(2, context.getStopBlocksStack().size());

            context.popStopBlocks();
            assertEquals(1, context.getStopBlocksStack().size());

            context.popStopBlocks();
            assertTrue(context.getStopBlocksStack().isEmpty());
        }

        @Test
        void getAllStopBlocksReturnsEmpty() throws IOException {
            ControlFlowContext context = createContext();

            Set<IRBlock> all = context.getAllStopBlocks();

            assertNotNull(all);
            assertTrue(all.isEmpty());
        }

        @Test
        void getAllStopBlocksReturnsSingleLevel() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock stop1 = new IRBlock("stop1");
            IRBlock stop2 = new IRBlock("stop2");
            Set<IRBlock> stopSet = new HashSet<>(Arrays.asList(stop1, stop2));

            context.pushStopBlocks(stopSet);

            Set<IRBlock> all = context.getAllStopBlocks();
            assertEquals(2, all.size());
            assertTrue(all.contains(stop1));
            assertTrue(all.contains(stop2));
        }

        @Test
        void getAllStopBlocksCombinesMultipleLevels() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock stop1 = new IRBlock("stop1");
            IRBlock stop2 = new IRBlock("stop2");
            IRBlock stop3 = new IRBlock("stop3");
            Set<IRBlock> set1 = new HashSet<>(Arrays.asList(stop1));
            Set<IRBlock> set2 = new HashSet<>(Arrays.asList(stop2, stop3));

            context.pushStopBlocks(set1);
            context.pushStopBlocks(set2);

            Set<IRBlock> all = context.getAllStopBlocks();
            assertEquals(3, all.size());
            assertTrue(all.contains(stop1));
            assertTrue(all.contains(stop2));
            assertTrue(all.contains(stop3));
        }

        @Test
        void getAllStopBlocksAfterPop() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock stop1 = new IRBlock("stop1");
            IRBlock stop2 = new IRBlock("stop2");
            Set<IRBlock> set1 = new HashSet<>(Arrays.asList(stop1));
            Set<IRBlock> set2 = new HashSet<>(Arrays.asList(stop2));

            context.pushStopBlocks(set1);
            context.pushStopBlocks(set2);
            context.popStopBlocks();

            Set<IRBlock> all = context.getAllStopBlocks();
            assertEquals(1, all.size());
            assertTrue(all.contains(stop1));
            assertFalse(all.contains(stop2));
        }

        @Test
        void pushEmptyStopBlocks() throws IOException {
            ControlFlowContext context = createContext();
            Set<IRBlock> empty = Collections.emptySet();

            context.pushStopBlocks(empty);

            assertEquals(1, context.getStopBlocksStack().size());
            assertTrue(context.getAllStopBlocks().isEmpty());
        }

        @Test
        void getAllStopBlocksWithOverlappingSets() throws IOException {
            ControlFlowContext context = createContext();
            IRBlock stop1 = new IRBlock("stop1");
            IRBlock stop2 = new IRBlock("stop2");
            Set<IRBlock> set1 = new HashSet<>(Arrays.asList(stop1, stop2));
            Set<IRBlock> set2 = new HashSet<>(Arrays.asList(stop2));

            context.pushStopBlocks(set1);
            context.pushStopBlocks(set2);

            Set<IRBlock> all = context.getAllStopBlocks();
            // Should have unique blocks only
            assertEquals(2, all.size());
            assertTrue(all.contains(stop1));
            assertTrue(all.contains(stop2));
        }
    }

    // ========== Integration Tests ==========

    @Nested
    class IntegrationTests {

        @Test
        void simulateLoopProcessing() throws IOException {
            ControlFlowContext context = createContext();
            IRMethod method = context.getIrMethod();
            IRBlock header = method.getEntryBlock();
            IRBlock body = new IRBlock("body");
            IRBlock exit = new IRBlock("exit");
            method.addBlock(body);
            method.addBlock(exit);

            // Set up loop structure
            context.setRegion(header, StructuredRegion.WHILE_LOOP);
            context.markProcessed(header);

            // Push stop blocks for the loop
            Set<IRBlock> stopBlocks = new HashSet<>(Arrays.asList(exit));
            context.pushStopBlocks(stopBlocks);

            // Process body
            context.markProcessed(body);
            List<Statement> bodyStmts = Arrays.asList(new ReturnStmt(null));
            context.setStatements(body, bodyStmts);

            // Exit loop
            context.popStopBlocks();
            context.markProcessed(exit);

            // Verify state
            assertTrue(context.isProcessed(header));
            assertTrue(context.isProcessed(body));
            assertTrue(context.isProcessed(exit));
            assertEquals(StructuredRegion.WHILE_LOOP, context.getRegion(header));
            assertEquals(1, context.getStatements(body).size());
            assertTrue(context.getStopBlocksStack().isEmpty());
        }

        @Test
        void simulateNestedLoops() throws IOException {
            ControlFlowContext context = createContext();
            IRMethod method = context.getIrMethod();
            IRBlock outerHeader = method.getEntryBlock();
            IRBlock innerHeader = new IRBlock("innerHeader");
            IRBlock innerBody = new IRBlock("innerBody");
            IRBlock innerExit = new IRBlock("innerExit");
            IRBlock outerExit = new IRBlock("outerExit");
            method.addBlock(innerHeader);
            method.addBlock(innerBody);
            method.addBlock(innerExit);
            method.addBlock(outerExit);

            // Outer loop
            Set<IRBlock> outerStops = new HashSet<>(Arrays.asList(outerExit));
            context.pushStopBlocks(outerStops);
            context.setRegion(outerHeader, StructuredRegion.WHILE_LOOP);

            // Inner loop
            Set<IRBlock> innerStops = new HashSet<>(Arrays.asList(innerExit));
            context.pushStopBlocks(innerStops);
            context.setRegion(innerHeader, StructuredRegion.WHILE_LOOP);

            // Should have both sets of stop blocks
            Set<IRBlock> allStops = context.getAllStopBlocks();
            assertEquals(2, allStops.size());
            assertTrue(allStops.contains(outerExit));
            assertTrue(allStops.contains(innerExit));

            // Exit inner loop
            context.popStopBlocks();
            allStops = context.getAllStopBlocks();
            assertEquals(1, allStops.size());
            assertTrue(allStops.contains(outerExit));

            // Exit outer loop
            context.popStopBlocks();
            assertTrue(context.getAllStopBlocks().isEmpty());
        }

        @Test
        void simulateIfStatementWithLabels() throws IOException {
            ControlFlowContext context = createContext();
            IRMethod method = context.getIrMethod();
            IRBlock header = method.getEntryBlock();
            IRBlock thenBlock = new IRBlock("then");
            IRBlock elseBlock = new IRBlock("else");
            IRBlock merge = new IRBlock("merge");
            method.addBlock(thenBlock);
            method.addBlock(elseBlock);
            method.addBlock(merge);

            // Mark header as if-then-else
            context.setRegion(header, StructuredRegion.IF_THEN_ELSE);
            context.markProcessed(header);

            // Process branches
            context.markProcessed(thenBlock);
            context.markProcessed(elseBlock);

            // Create label for merge (in case of break)
            String mergeLabel = context.getOrCreateLabel(merge);

            // Process merge
            context.markProcessed(merge);

            // Verify
            assertTrue(context.hasLabel(merge));
            assertEquals("label0", mergeLabel);
            assertTrue(context.isProcessed(header));
            assertTrue(context.isProcessed(thenBlock));
            assertTrue(context.isProcessed(elseBlock));
            assertTrue(context.isProcessed(merge));
        }

        @Test
        void simulatePendingStatementsInControlFlow() throws IOException {
            ControlFlowContext context = createContext();
            IRMethod method = context.getIrMethod();
            IRBlock header = method.getEntryBlock();

            // Add pending statements (e.g., from header processing)
            List<Statement> headerStmts = Arrays.asList(new ReturnStmt(null));
            context.addPendingStatements(headerStmts);

            // Set region
            context.setRegion(header, StructuredRegion.IF_THEN);

            // Collect pending before creating structured statement
            List<Statement> pending = context.collectPendingStatements();

            // Verify
            assertEquals(1, pending.size());
            assertTrue(context.getPendingStatements().isEmpty());
            assertEquals(StructuredRegion.IF_THEN, context.getRegion(header));
        }

        @Test
        void complexStateTransitions() throws IOException {
            ControlFlowContext context = createContext();
            IRMethod method = context.getIrMethod();
            IRBlock b1 = method.getEntryBlock();
            IRBlock b2 = new IRBlock("b2");
            IRBlock b3 = new IRBlock("b3");
            method.addBlock(b2);
            method.addBlock(b3);

            // Simulate complex processing
            context.markProcessed(b1);
            context.setRegion(b1, StructuredRegion.IF_THEN);
            context.setStatements(b1, Arrays.asList(new ReturnStmt(null)));
            String label1 = context.getOrCreateLabel(b1);

            Set<IRBlock> stops1 = new HashSet<>(Arrays.asList(b3));
            context.pushStopBlocks(stops1);

            context.markProcessed(b2);
            context.setRegion(b2, StructuredRegion.SEQUENCE);
            context.setStatements(b2, Arrays.asList(new ReturnStmt(null), new ReturnStmt(null)));
            context.addPendingStatements(Arrays.asList(new ReturnStmt(null)));

            Set<IRBlock> stops2 = new HashSet<>(Arrays.asList(b2));
            context.pushStopBlocks(stops2);

            // Verify complex state
            assertTrue(context.isProcessed(b1));
            assertTrue(context.isProcessed(b2));
            assertEquals(StructuredRegion.IF_THEN, context.getRegion(b1));
            assertEquals(StructuredRegion.SEQUENCE, context.getRegion(b2));
            assertEquals(1, context.getStatements(b1).size());
            assertEquals(2, context.getStatements(b2).size());
            assertTrue(context.hasLabel(b1));
            assertEquals("label0", label1);
            assertEquals(1, context.getPendingStatements().size());
            assertEquals(2, context.getAllStopBlocks().size());

            // Cleanup
            context.popStopBlocks();
            context.popStopBlocks();
            List<Statement> pending = context.collectPendingStatements();

            assertEquals(1, pending.size());
            assertTrue(context.getAllStopBlocks().isEmpty());
        }
    }

    // ========== Helper Methods ==========

    private IRMethod createSimpleMethod() {
        IRMethod method = new IRMethod("com/test/Test", "simple", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction(null));
        return method;
    }

    private RecoveryContext createRecoveryContext(IRMethod method) throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
            .publicStaticMethod("test", "()V")
                .vreturn()
            .build();
        MethodEntry methodEntry = cf.getMethods().get(0);
        DefUseChains defUse = new DefUseChains(method);
        defUse.compute();
        return new RecoveryContext(method, methodEntry, defUse);
    }

    private ControlFlowContext createContext() throws IOException {
        IRMethod method = createSimpleMethod();
        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();
        LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
        loopAnalysis.compute();
        RecoveryContext recoveryContext = createRecoveryContext(method);

        return new ControlFlowContext(method, domTree, loopAnalysis, recoveryContext);
    }
}
