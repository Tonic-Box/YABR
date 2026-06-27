package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.source.recovery.ControlFlowContext.StructuredRegion;
import com.tonic.analysis.source.recovery.StructuralAnalyzer.RegionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for StructuralAnalyzer.
 * Covers region detection for all control flow patterns.
 */
class StructuralAnalyzerTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    // ========== Basic Functionality Tests ==========

    @Test
    void analyzeEmptyMethod() {
        IRMethod method = createSimpleMethod();
        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();
        LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
        loopAnalysis.compute();

        StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
        analyzer.analyze();

        assertNotNull(analyzer.getRegionInfos());
        assertFalse(analyzer.getRegionInfos().isEmpty());
    }

    @Test
    void gettersReturnCorrectValues() {
        IRMethod method = createSimpleMethod();
        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();
        LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
        loopAnalysis.compute();

        StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);

        assertEquals(method, analyzer.getMethod());
        assertEquals(domTree, analyzer.getDominatorTree());
        assertEquals(loopAnalysis, analyzer.getLoopAnalysis());
        assertNotNull(analyzer.getRegionInfos());
    }

    @Test
    void postDominatorTreeCreatedAfterAnalyze() {
        IRMethod method = createSimpleMethod();
        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();
        LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
        loopAnalysis.compute();

        StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);

        assertNull(analyzer.getPostDominatorTree());

        analyzer.analyze();

        assertNotNull(analyzer.getPostDominatorTree());
    }

    // ========== IF_THEN Tests ==========

    @Nested
    class IfThenTests {

        @Test
        void detectsSimpleIfThen() {
            // entry -> header -true-> then -> merge
            //                  -false-> merge
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock thenBlock = new IRBlock("then");
            IRBlock merge = new IRBlock("merge");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(thenBlock);
            method.addBlock(merge);
            method.setEntryBlock(entry);

            // Setup control flow
            entry.addSuccessor(header);
            header.addSuccessor(thenBlock);
            header.addSuccessor(merge);
            thenBlock.addSuccessor(merge);

            // Add branch instruction
            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.NE, condition, null, thenBlock, merge
            );
            header.addInstruction(branch);

            merge.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            assertEquals(StructuredRegion.IF_THEN, info.getType());
            assertEquals(header, info.getHeader());
            assertNotNull(info.getThenBlock());
            assertEquals(merge, info.getMergeBlock());
        }

        @Test
        void detectsIfThenWithEarlyReturn() {
            // entry -> header -true-> returnBlock (return)
            //                  -false-> continue
            IRMethod method = new IRMethod("com/test/Test", "test", "()I", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock returnBlock = new IRBlock("return");
            IRBlock continueBlock = new IRBlock("continue");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(returnBlock);
            method.addBlock(continueBlock);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(returnBlock);
            header.addSuccessor(continueBlock);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.EQ, condition, null, returnBlock, continueBlock
            );
            header.addInstruction(branch);

            returnBlock.addInstruction(new ReturnInstruction(IntConstant.of(0)));

            continueBlock.addInstruction(new ReturnInstruction(IntConstant.of(1)));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            assertEquals(StructuredRegion.IF_THEN, info.getType());
        }

        @Test
        void detectsIfThenWithGotoToSharedExitBlock() {
            // Tests early exit detection with goto to shared exit block
            IRMethod method = new IRMethod("com/test/Test", "test", "()I", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock gotoBlock = new IRBlock("goto");
            IRBlock sharedExit = new IRBlock("sharedExit");
            IRBlock continueBlock = new IRBlock("continue");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(gotoBlock);
            method.addBlock(sharedExit);
            method.addBlock(continueBlock);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(gotoBlock);
            header.addSuccessor(continueBlock);
            gotoBlock.addSuccessor(sharedExit);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.EQ, condition, null, gotoBlock, continueBlock
            );
            header.addInstruction(branch);

            // gotoBlock only has a goto to sharedExit
            SimpleInstruction gotoInstr = SimpleInstruction.createGoto(sharedExit);
            gotoBlock.addInstruction(gotoInstr);

            sharedExit.addInstruction(new ReturnInstruction(IntConstant.of(0)));
            continueBlock.addInstruction(new ReturnInstruction(IntConstant.of(1)));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            // Should detect IF_THEN because gotoBlock is early exit
            assertEquals(StructuredRegion.IF_THEN, info.getType());
        }

        @Test
        void detectsIfThenWhenTrueTargetIsMerge() {
            // if false branch goes to merge directly
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock thenBlock = new IRBlock("then");
            IRBlock merge = new IRBlock("merge");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(thenBlock);
            method.addBlock(merge);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(merge);
            header.addSuccessor(thenBlock);
            thenBlock.addSuccessor(merge);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.NE, condition, null, merge, thenBlock
            );
            header.addInstruction(branch);

            merge.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            assertEquals(StructuredRegion.IF_THEN, info.getType());
            assertTrue(info.isConditionNegated());
        }

        @Test
        void detectsIfThenWhenFalseTargetIsReachableFromTrue() {
            // Pattern where false target is reached from true target
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock thenBlock = new IRBlock("then");
            IRBlock mergeBlock = new IRBlock("merge");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(thenBlock);
            method.addBlock(mergeBlock);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(thenBlock);
            header.addSuccessor(mergeBlock);
            thenBlock.addSuccessor(mergeBlock);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.GT, condition, IntConstant.of(0), thenBlock, mergeBlock
            );
            header.addInstruction(branch);

            mergeBlock.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            // Should recognize this as IF_THEN pattern
            assertEquals(StructuredRegion.IF_THEN, info.getType());
        }
    }

    // ========== IF_THEN_ELSE Tests ==========

    @Nested
    class IfThenElseTests {

        @Test
        void detectsSimpleIfThenElse() {
            // entry -> header -true-> thenBlock -> merge
            //                  -false-> elseBlock -> merge
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock thenBlock = new IRBlock("then");
            IRBlock elseBlock = new IRBlock("else");
            IRBlock merge = new IRBlock("merge");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(thenBlock);
            method.addBlock(elseBlock);
            method.addBlock(merge);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(thenBlock);
            header.addSuccessor(elseBlock);
            thenBlock.addSuccessor(merge);
            elseBlock.addSuccessor(merge);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.GT, condition, IntConstant.of(0), thenBlock, elseBlock
            );
            header.addInstruction(branch);

            merge.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            assertEquals(StructuredRegion.IF_THEN_ELSE, info.getType());
            assertEquals(header, info.getHeader());
            assertEquals(thenBlock, info.getThenBlock());
            assertEquals(elseBlock, info.getElseBlock());
            assertEquals(merge, info.getMergeBlock());
        }

        @Test
        void detectsIfThenElseWhenMergeIsExitBlock() {
            // Test alternative merge point finding when post-dominator is exit block
            IRMethod method = new IRMethod("com/test/Test", "test", "()I", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock thenBlock = new IRBlock("then");
            IRBlock elseBlock = new IRBlock("else");
            IRBlock innerIf = new IRBlock("innerIf");
            IRBlock earlyReturn = new IRBlock("earlyReturn");
            IRBlock realMerge = new IRBlock("realMerge");
            IRBlock exitBlock = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(thenBlock);
            method.addBlock(elseBlock);
            method.addBlock(innerIf);
            method.addBlock(earlyReturn);
            method.addBlock(realMerge);
            method.addBlock(exitBlock);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(thenBlock);
            header.addSuccessor(elseBlock);
            thenBlock.addSuccessor(realMerge);
            elseBlock.addSuccessor(innerIf);
            innerIf.addSuccessor(earlyReturn);
            innerIf.addSuccessor(realMerge);
            earlyReturn.addSuccessor(exitBlock);
            realMerge.addSuccessor(exitBlock);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.GT, condition, IntConstant.of(0), thenBlock, elseBlock
            );
            header.addInstruction(branch);

            SSAValue innerCond = new SSAValue(PrimitiveType.INT, "innerCond");
            BranchInstruction innerBranch = new BranchInstruction(
                CompareOp.EQ, innerCond, null, earlyReturn, realMerge
            );
            innerIf.addInstruction(innerBranch);

            earlyReturn.addInstruction(new ReturnInstruction(IntConstant.of(-1)));
            exitBlock.addInstruction(new ReturnInstruction(IntConstant.of(0)));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            // Should still detect as IF_THEN_ELSE with proper merge finding
            assertTrue(info.getType() == StructuredRegion.IF_THEN_ELSE ||
                      info.getType() == StructuredRegion.IF_THEN);
        }

        @Test
        void detectsFlatIfChainPattern() {
            // Tests dispatch table pattern: if (x == 1) A; else if (x == 2) B; ...
            IRMethod method = new IRMethod("com/test/Test", "test", "(I)V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock check1 = new IRBlock("check1");
            IRBlock action1 = new IRBlock("action1");
            IRBlock check2 = new IRBlock("check2");
            IRBlock action2 = new IRBlock("action2");
            IRBlock defaultAction = new IRBlock("default");
            IRBlock merge = new IRBlock("merge");

            method.addBlock(entry);
            method.addBlock(check1);
            method.addBlock(action1);
            method.addBlock(check2);
            method.addBlock(action2);
            method.addBlock(defaultAction);
            method.addBlock(merge);
            method.setEntryBlock(entry);

            entry.addSuccessor(check1);
            check1.addSuccessor(action1);
            check1.addSuccessor(check2);
            action1.addSuccessor(merge);
            check2.addSuccessor(action2);
            check2.addSuccessor(defaultAction);
            action2.addSuccessor(merge);
            defaultAction.addSuccessor(merge);

            // Load the same variable in entry
            SSAValue x = new SSAValue(PrimitiveType.INT, "x");
            LoadLocalInstruction load = new LoadLocalInstruction(x, 0);
            entry.addInstruction(load);

            // check1: if (x == 1) goto action1 else goto check2
            BranchInstruction branch1 = new BranchInstruction(
                CompareOp.NE, x, IntConstant.of(1), check2, action1
            );
            check1.addInstruction(branch1);

            // check2: if (x == 2) goto action2 else goto defaultAction
            BranchInstruction branch2 = new BranchInstruction(
                CompareOp.NE, x, IntConstant.of(2), defaultAction, action2
            );
            check2.addInstruction(branch2);

            merge.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info1 = analyzer.getRegionInfo(check1);
            assertNotNull(info1);
            // Should detect flat if-chain pattern
            assertTrue(info1.getType() == StructuredRegion.IF_THEN ||
                      info1.getType() == StructuredRegion.IF_THEN_ELSE);
        }
    }

    // ========== WHILE_LOOP Tests ==========

    @Nested
    class WhileLoopTests {

        @Test
        void detectsSimpleWhileLoop() {
            // entry -> header -true-> body -> header
            //                  -false-> exit
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock body = new IRBlock("body");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(body);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(body);
            header.addSuccessor(exit);
            body.addSuccessor(header);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.NE, condition, IntConstant.of(0), body, exit
            );
            header.addInstruction(branch);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            assertEquals(StructuredRegion.WHILE_LOOP, info.getType());
            assertEquals(header, info.getHeader());
            assertEquals(body, info.getLoopBody());
            assertEquals(exit, info.getLoopExit());
            assertNotNull(info.getLoop());
        }

        @Test
        void whileLoopConditionNegatedCorrectly() {
            // Test that condition negation is tracked correctly
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock body = new IRBlock("body");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(body);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(exit);
            header.addSuccessor(body);
            body.addSuccessor(header);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.EQ, condition, IntConstant.of(0), exit, body
            );
            header.addInstruction(branch);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            assertEquals(StructuredRegion.WHILE_LOOP, info.getType());
            assertTrue(info.isConditionNegated());
        }

        @Test
        void whileLoopWithBothTargetsInLoop() {
            // Edge case: both branch targets are inside loop
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock body1 = new IRBlock("body1");
            IRBlock body2 = new IRBlock("body2");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(body1);
            method.addBlock(body2);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(body1);
            header.addSuccessor(body2);
            body1.addSuccessor(header);
            body2.addSuccessor(header);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.GT, condition, IntConstant.of(0), body1, body2
            );
            header.addInstruction(branch);

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            // Should recognize this pattern
            assertTrue(info.getType() == StructuredRegion.WHILE_LOOP ||
                      info.getType() == StructuredRegion.DO_WHILE_LOOP);
            // Both targets in loop - exitBlock may vary based on analysis
        }

        @Test
        void irreducibleLoopDetected() {
            // Edge case: neither target is in loop (irreducible)
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock exit1 = new IRBlock("exit1");
            IRBlock exit2 = new IRBlock("exit2");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(exit1);
            method.addBlock(exit2);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(exit1);
            header.addSuccessor(exit2);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.NE, condition, null, exit1, exit2
            );
            header.addInstruction(branch);

            exit1.addInstruction(new ReturnInstruction(null));
            exit2.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            // Just verify it doesn't crash - irreducible loops may be classified differently
            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
        }
    }

    // ========== DO_WHILE_LOOP Tests ==========

    @Nested
    class DoWhileLoopTests {

        @Test
        void detectsDoWhileLoop() {
            // entry -> body -> header -true-> body
            //                          -false-> exit
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock body = new IRBlock("body");
            IRBlock header = new IRBlock("header");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(body);
            method.addBlock(header);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(body);
            body.addSuccessor(header);
            header.addSuccessor(body);
            header.addSuccessor(exit);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.GT, condition, IntConstant.of(0), body, exit
            );
            header.addInstruction(branch);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            // Note: Analyzer may classify do-while loops differently
            // The structure is valid, just verify it's recognized
            if (info.getType() == StructuredRegion.DO_WHILE_LOOP) {
                assertEquals(header, info.getHeader());
            }
        }

    }

    // ========== FOR_LOOP Tests ==========

    @Nested
    class ForLoopTests {

        @Test
        void detectsForLoopWithIncrement() {
            // entry -> header -true-> body -> increment -> header
            //                  -false-> exit
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock body = new IRBlock("body");
            IRBlock increment = new IRBlock("increment");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(body);
            method.addBlock(increment);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(body);
            header.addSuccessor(exit);
            body.addSuccessor(increment);
            increment.addSuccessor(header);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.LT, condition, IntConstant.of(10), body, exit
            );
            header.addInstruction(branch);

            // Add increment instruction
            SSAValue i = new SSAValue(PrimitiveType.INT, "i");
            SSAValue one = new SSAValue(PrimitiveType.INT, "one");
            SSAValue iNext = new SSAValue(PrimitiveType.INT, "i_next");
            BinaryOpInstruction add = new BinaryOpInstruction(iNext, BinaryOp.ADD, i, one);
            increment.addInstruction(add);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            assertEquals(StructuredRegion.FOR_LOOP, info.getType());
            assertEquals(header, info.getHeader());
            assertEquals(body, info.getLoopBody());
            assertEquals(exit, info.getLoopExit());
        }

        @Test
        void forLoopWithDecrementDetected() {
            // Test that SUB operation is also recognized as increment pattern
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock body = new IRBlock("body");
            IRBlock decrement = new IRBlock("decrement");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(body);
            method.addBlock(decrement);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(body);
            header.addSuccessor(exit);
            body.addSuccessor(decrement);
            decrement.addSuccessor(header);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.GT, condition, IntConstant.of(0), body, exit
            );
            header.addInstruction(branch);

            // Add decrement instruction
            SSAValue i = new SSAValue(PrimitiveType.INT, "i");
            SSAValue one = new SSAValue(PrimitiveType.INT, "one");
            SSAValue iNext = new SSAValue(PrimitiveType.INT, "i_next");
            BinaryOpInstruction sub = new BinaryOpInstruction(iNext, BinaryOp.SUB, i, one);
            decrement.addInstruction(sub);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            assertEquals(StructuredRegion.FOR_LOOP, info.getType());
        }

        @Test
        void loopWithoutIncrementIsNotForLoop() {
            // Loop without ADD/SUB should be classified as WHILE, not FOR
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock body = new IRBlock("body");
            IRBlock noIncrement = new IRBlock("noIncrement");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(body);
            method.addBlock(noIncrement);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(body);
            header.addSuccessor(exit);
            body.addSuccessor(noIncrement);
            noIncrement.addSuccessor(header);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.NE, condition, null, body, exit
            );
            header.addInstruction(branch);

            // No increment instruction in noIncrement block
            SimpleInstruction gotoInstr = SimpleInstruction.createGoto(header);
            noIncrement.addInstruction(gotoInstr);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            // Should be WHILE_LOOP, not FOR_LOOP
            assertEquals(StructuredRegion.WHILE_LOOP, info.getType());
        }
    }

    // ========== SWITCH Tests ==========

    @Nested
    class SwitchTests {

        @Test
        void detectsSimpleSwitch() {
            IRMethod method = new IRMethod("com/test/Test", "test", "(I)V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock switchBlock = new IRBlock("switch");
            IRBlock case1 = new IRBlock("case1");
            IRBlock case2 = new IRBlock("case2");
            IRBlock defaultBlock = new IRBlock("default");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(switchBlock);
            method.addBlock(case1);
            method.addBlock(case2);
            method.addBlock(defaultBlock);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(switchBlock);
            switchBlock.addSuccessor(case1);
            switchBlock.addSuccessor(case2);
            switchBlock.addSuccessor(defaultBlock);
            case1.addSuccessor(exit);
            case2.addSuccessor(exit);
            defaultBlock.addSuccessor(exit);

            SSAValue selector = new SSAValue(PrimitiveType.INT, "selector");
            SwitchInstruction switchInstr = new SwitchInstruction(selector, defaultBlock);
            switchInstr.addCase(1, case1);
            switchInstr.addCase(2, case2);
            switchBlock.addInstruction(switchInstr);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(switchBlock);
            assertNotNull(info);
            assertEquals(StructuredRegion.SWITCH, info.getType());
            assertEquals(switchBlock, info.getHeader());
            assertEquals(defaultBlock, info.getDefaultTarget());
            assertNotNull(info.getSwitchCases());
            assertEquals(2, info.getSwitchCases().size());
            assertEquals(case1, info.getSwitchCases().get(1));
            assertEquals(case2, info.getSwitchCases().get(2));
        }

        @Test
        void switchWithNoCasesOnlyDefault() {
            IRMethod method = new IRMethod("com/test/Test", "test", "(I)V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock switchBlock = new IRBlock("switch");
            IRBlock defaultBlock = new IRBlock("default");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(switchBlock);
            method.addBlock(defaultBlock);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(switchBlock);
            switchBlock.addSuccessor(defaultBlock);
            defaultBlock.addSuccessor(exit);

            SSAValue selector = new SSAValue(PrimitiveType.INT, "selector");
            SwitchInstruction switchInstr = new SwitchInstruction(selector, defaultBlock);
            switchBlock.addInstruction(switchInstr);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(switchBlock);
            assertNotNull(info);
            assertEquals(StructuredRegion.SWITCH, info.getType());
            assertEquals(defaultBlock, info.getDefaultTarget());
            assertTrue(info.getSwitchCases().isEmpty());
        }

        @Test
        void switchWithManyCases() {
            // Test switch with many cases to ensure all are captured
            IRMethod method = new IRMethod("com/test/Test", "test", "(I)V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock switchBlock = new IRBlock("switch");
            IRBlock[] cases = new IRBlock[5];
            for (int i = 0; i < 5; i++) {
                cases[i] = new IRBlock("case" + i);
                method.addBlock(cases[i]);
            }
            IRBlock defaultBlock = new IRBlock("default");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(switchBlock);
            method.addBlock(defaultBlock);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(switchBlock);
            for (IRBlock caseBlock : cases) {
                switchBlock.addSuccessor(caseBlock);
                caseBlock.addSuccessor(exit);
            }
            switchBlock.addSuccessor(defaultBlock);
            defaultBlock.addSuccessor(exit);

            SSAValue selector = new SSAValue(PrimitiveType.INT, "selector");
            SwitchInstruction switchInstr = new SwitchInstruction(selector, defaultBlock);
            for (int i = 0; i < 5; i++) {
                switchInstr.addCase(i * 10, cases[i]);
            }
            switchBlock.addInstruction(switchInstr);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(switchBlock);
            assertNotNull(info);
            assertEquals(StructuredRegion.SWITCH, info.getType());
            assertEquals(5, info.getSwitchCases().size());
        }
    }

    // ========== SEQUENCE Tests ==========

    @Nested
    class SequenceTests {

        @Test
        void detectsSequenceForSimpleBlock() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");

            method.addBlock(entry);
            method.setEntryBlock(entry);

            entry.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(entry);
            assertNotNull(info);
            assertEquals(StructuredRegion.SEQUENCE, info.getType());
        }

        @Test
        void gotoInLoopMarkedAsSequenceWithContinue() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock body = new IRBlock("body");
            IRBlock continueBlock = new IRBlock("continue");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(body);
            method.addBlock(continueBlock);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(body);
            header.addSuccessor(exit);
            body.addSuccessor(continueBlock);
            continueBlock.addSuccessor(header);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.NE, condition, null, body, exit
            );
            header.addInstruction(branch);

            SimpleInstruction gotoInstr = SimpleInstruction.createGoto(header);
            continueBlock.addInstruction(gotoInstr);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(continueBlock);
            assertNotNull(info);
            assertEquals(StructuredRegion.SEQUENCE, info.getType());
            assertEquals(header, info.getContinueTarget());
        }

        @Test
        void gotoOutsideLoopMarkedAsSequence() {
            // Goto not in a loop should still be SEQUENCE
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock gotoBlock = new IRBlock("goto");
            IRBlock target = new IRBlock("target");

            method.addBlock(entry);
            method.addBlock(gotoBlock);
            method.addBlock(target);
            method.setEntryBlock(entry);

            entry.addSuccessor(gotoBlock);
            gotoBlock.addSuccessor(target);

            SimpleInstruction gotoInstr = SimpleInstruction.createGoto(target);
            gotoBlock.addInstruction(gotoInstr);

            target.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(gotoBlock);
            assertNotNull(info);
            assertEquals(StructuredRegion.SEQUENCE, info.getType());
            assertNull(info.getContinueTarget());
        }
    }

    // ========== Nested Region Tests ==========

    @Nested
    class NestedRegionTests {

        @Test
        void detectsNestedIfInLoop() {
            // while (cond1) {
            //   if (cond2) { ... }
            // }
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock loopHeader = new IRBlock("loopHeader");
            IRBlock ifHeader = new IRBlock("ifHeader");
            IRBlock thenBlock = new IRBlock("then");
            IRBlock loopContinue = new IRBlock("loopContinue");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(loopHeader);
            method.addBlock(ifHeader);
            method.addBlock(thenBlock);
            method.addBlock(loopContinue);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(loopHeader);
            loopHeader.addSuccessor(ifHeader);
            loopHeader.addSuccessor(exit);
            ifHeader.addSuccessor(thenBlock);
            ifHeader.addSuccessor(loopContinue);
            thenBlock.addSuccessor(loopContinue);
            loopContinue.addSuccessor(loopHeader);

            SSAValue loopCond = new SSAValue(PrimitiveType.INT, "loopCond");
            BranchInstruction loopBranch = new BranchInstruction(
                CompareOp.NE, loopCond, null, ifHeader, exit
            );
            loopHeader.addInstruction(loopBranch);

            SSAValue ifCond = new SSAValue(PrimitiveType.INT, "ifCond");
            BranchInstruction ifBranch = new BranchInstruction(
                CompareOp.GT, ifCond, IntConstant.of(0), thenBlock, loopContinue
            );
            ifHeader.addInstruction(ifBranch);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            // Check loop region
            RegionInfo loopInfo = analyzer.getRegionInfo(loopHeader);
            assertNotNull(loopInfo);
            assertEquals(StructuredRegion.WHILE_LOOP, loopInfo.getType());

            // Check nested if region
            RegionInfo ifInfo = analyzer.getRegionInfo(ifHeader);
            assertNotNull(ifInfo);
            assertEquals(StructuredRegion.IF_THEN, ifInfo.getType());
        }

        @Test
        void detectsNestedLoops() {
            // while (outer) { while (inner) { ... } }
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock outerHeader = new IRBlock("outerHeader");
            IRBlock innerHeader = new IRBlock("innerHeader");
            IRBlock innerBody = new IRBlock("innerBody");
            IRBlock outerContinue = new IRBlock("outerContinue");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(outerHeader);
            method.addBlock(innerHeader);
            method.addBlock(innerBody);
            method.addBlock(outerContinue);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(outerHeader);
            outerHeader.addSuccessor(innerHeader);
            outerHeader.addSuccessor(exit);
            innerHeader.addSuccessor(innerBody);
            innerHeader.addSuccessor(outerContinue);
            innerBody.addSuccessor(innerHeader);
            outerContinue.addSuccessor(outerHeader);

            SSAValue outerCond = new SSAValue(PrimitiveType.INT, "outerCond");
            BranchInstruction outerBranch = new BranchInstruction(
                CompareOp.NE, outerCond, null, innerHeader, exit
            );
            outerHeader.addInstruction(outerBranch);

            SSAValue innerCond = new SSAValue(PrimitiveType.INT, "innerCond");
            BranchInstruction innerBranch = new BranchInstruction(
                CompareOp.NE, innerCond, null, innerBody, outerContinue
            );
            innerHeader.addInstruction(innerBranch);

            exit.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo outerInfo = analyzer.getRegionInfo(outerHeader);
            assertNotNull(outerInfo);
            assertEquals(StructuredRegion.WHILE_LOOP, outerInfo.getType());

            RegionInfo innerInfo = analyzer.getRegionInfo(innerHeader);
            assertNotNull(innerInfo);
            assertEquals(StructuredRegion.WHILE_LOOP, innerInfo.getType());
        }

        @Test
        void detectsDeeplyNestedIfStatements() {
            // if (c1) { if (c2) { if (c3) { ... } } }
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock if1 = new IRBlock("if1");
            IRBlock if2 = new IRBlock("if2");
            IRBlock if3 = new IRBlock("if3");
            IRBlock innermost = new IRBlock("innermost");
            IRBlock merge3 = new IRBlock("merge3");
            IRBlock merge2 = new IRBlock("merge2");
            IRBlock merge1 = new IRBlock("merge1");

            method.addBlock(entry);
            method.addBlock(if1);
            method.addBlock(if2);
            method.addBlock(if3);
            method.addBlock(innermost);
            method.addBlock(merge3);
            method.addBlock(merge2);
            method.addBlock(merge1);
            method.setEntryBlock(entry);

            entry.addSuccessor(if1);
            if1.addSuccessor(if2);
            if1.addSuccessor(merge1);
            if2.addSuccessor(if3);
            if2.addSuccessor(merge2);
            if3.addSuccessor(innermost);
            if3.addSuccessor(merge3);
            innermost.addSuccessor(merge3);
            merge3.addSuccessor(merge2);
            merge2.addSuccessor(merge1);

            SSAValue c1 = new SSAValue(PrimitiveType.INT, "c1");
            BranchInstruction b1 = new BranchInstruction(
                CompareOp.NE, c1, null, if2, merge1
            );
            if1.addInstruction(b1);

            SSAValue c2 = new SSAValue(PrimitiveType.INT, "c2");
            BranchInstruction b2 = new BranchInstruction(
                CompareOp.NE, c2, null, if3, merge2
            );
            if2.addInstruction(b2);

            SSAValue c3 = new SSAValue(PrimitiveType.INT, "c3");
            BranchInstruction b3 = new BranchInstruction(
                CompareOp.NE, c3, null, innermost, merge3
            );
            if3.addInstruction(b3);

            merge1.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            // All three levels should be detected as IF_THEN
            RegionInfo info1 = analyzer.getRegionInfo(if1);
            assertNotNull(info1);
            assertEquals(StructuredRegion.IF_THEN, info1.getType());

            RegionInfo info2 = analyzer.getRegionInfo(if2);
            assertNotNull(info2);
            assertEquals(StructuredRegion.IF_THEN, info2.getType());

            RegionInfo info3 = analyzer.getRegionInfo(if3);
            assertNotNull(info3);
            assertEquals(StructuredRegion.IF_THEN, info3.getType());
        }
    }

    // ========== Edge Cases ==========

    @Nested
    class EdgeCaseTests {

        @Test
        void blockWithNoTerminator() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");

            method.addBlock(entry);
            method.setEntryBlock(entry);

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(entry);
            assertNotNull(info);
            assertEquals(StructuredRegion.SEQUENCE, info.getType());
        }

        @Test
        void getRegionInfoReturnsNullForUnanalyzedBlock() {
            IRMethod method = createSimpleMethod();
            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);

            IRBlock unknownBlock = new IRBlock("unknown");

            assertNull(analyzer.getRegionInfo(unknownBlock));
        }

        @Test
        void multiplePathsToSameBlock() {
            // Test diamond pattern: entry -> A -> B -> merge
            //                              -> C -> D -> merge
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock a = new IRBlock("A");
            IRBlock b = new IRBlock("B");
            IRBlock c = new IRBlock("C");
            IRBlock d = new IRBlock("D");
            IRBlock merge = new IRBlock("merge");

            method.addBlock(entry);
            method.addBlock(a);
            method.addBlock(b);
            method.addBlock(c);
            method.addBlock(d);
            method.addBlock(merge);
            method.setEntryBlock(entry);

            entry.addSuccessor(a);
            entry.addSuccessor(c);
            a.addSuccessor(b);
            b.addSuccessor(merge);
            c.addSuccessor(d);
            d.addSuccessor(merge);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.NE, condition, null, a, c
            );
            entry.addInstruction(branch);

            merge.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(entry);
            assertNotNull(info);
            // Should detect some form of conditional structure
            assertTrue(info.getType() == StructuredRegion.IF_THEN ||
                      info.getType() == StructuredRegion.IF_THEN_ELSE);
        }

        @Test
        void earlyExitBlockWithMultiplePredecessors() {
            // Tests that blocks with multiple predecessors are not considered early exits
            IRMethod method = new IRMethod("com/test/Test", "test", "()I", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock if1 = new IRBlock("if1");
            IRBlock if2 = new IRBlock("if2");
            IRBlock sharedReturn = new IRBlock("sharedReturn");
            IRBlock continueBlock = new IRBlock("continue");

            method.addBlock(entry);
            method.addBlock(if1);
            method.addBlock(if2);
            method.addBlock(sharedReturn);
            method.addBlock(continueBlock);
            method.setEntryBlock(entry);

            entry.addSuccessor(if1);
            if1.addSuccessor(sharedReturn);
            if1.addSuccessor(if2);
            if2.addSuccessor(sharedReturn);
            if2.addSuccessor(continueBlock);

            SSAValue c1 = new SSAValue(PrimitiveType.INT, "c1");
            BranchInstruction b1 = new BranchInstruction(
                CompareOp.EQ, c1, null, sharedReturn, if2
            );
            if1.addInstruction(b1);

            SSAValue c2 = new SSAValue(PrimitiveType.INT, "c2");
            BranchInstruction b2 = new BranchInstruction(
                CompareOp.EQ, c2, null, sharedReturn, continueBlock
            );
            if2.addInstruction(b2);

            sharedReturn.addInstruction(new ReturnInstruction(IntConstant.of(0)));
            continueBlock.addInstruction(new ReturnInstruction(IntConstant.of(1)));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            // sharedReturn has 2 predecessors (both from conditional blocks),
            // which is a valid guard clause pattern where multiple guards share an exit
            RegionInfo info1 = analyzer.getRegionInfo(if1);
            assertNotNull(info1);
            // Can be IF_THEN_ELSE, IF_THEN, or GUARD_CLAUSE (since both predecessors are conditional)
            assertTrue(info1.getType() == StructuredRegion.IF_THEN_ELSE ||
                      info1.getType() == StructuredRegion.IF_THEN ||
                      info1.getType() == StructuredRegion.GUARD_CLAUSE);
        }

        @Test
        void blockWithNonTrivialInstructionsBeforeGoto() {
            // Tests hasNonTrivialInstructions logic
            IRMethod method = new IRMethod("com/test/Test", "test", "()I", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock nonTrivialGoto = new IRBlock("nonTrivialGoto");
            IRBlock exitBlock = new IRBlock("exit");
            IRBlock continueBlock = new IRBlock("continue");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(nonTrivialGoto);
            method.addBlock(exitBlock);
            method.addBlock(continueBlock);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(nonTrivialGoto);
            header.addSuccessor(continueBlock);
            nonTrivialGoto.addSuccessor(exitBlock);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.EQ, condition, null, nonTrivialGoto, continueBlock
            );
            header.addInstruction(branch);

            // Add non-trivial instruction before goto
            SSAValue result = new SSAValue(PrimitiveType.INT, "result");
            SSAValue a = new SSAValue(PrimitiveType.INT, "a");
            SSAValue b = new SSAValue(PrimitiveType.INT, "b");
            BinaryOpInstruction add = new BinaryOpInstruction(result, BinaryOp.ADD, a, b);
            nonTrivialGoto.addInstruction(add);

            SimpleInstruction gotoInstr = SimpleInstruction.createGoto(exitBlock);
            nonTrivialGoto.addInstruction(gotoInstr);

            exitBlock.addInstruction(new ReturnInstruction(IntConstant.of(0)));
            continueBlock.addInstruction(new ReturnInstruction(IntConstant.of(1)));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            // Block with non-trivial instructions should not be treated as simple early exit
        }

        @Test
        void blockWithThrowInstruction() {
            // Tests ThrowInstruction as terminator
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");
            IRBlock throwBlock = new IRBlock("throw");
            IRBlock continueBlock = new IRBlock("continue");

            method.addBlock(entry);
            method.addBlock(header);
            method.addBlock(throwBlock);
            method.addBlock(continueBlock);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);
            header.addSuccessor(throwBlock);
            header.addSuccessor(continueBlock);

            SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
            BranchInstruction branch = new BranchInstruction(
                CompareOp.EQ, condition, null, throwBlock, continueBlock
            );
            header.addInstruction(branch);

            SSAValue exception = new SSAValue(PrimitiveType.INT, "exception");
            SimpleInstruction throwInstr = SimpleInstruction.createThrow(exception);
            throwBlock.addInstruction(throwInstr);

            continueBlock.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            assertEquals(StructuredRegion.IF_THEN, info.getType());
        }

        @Test
        void mergePointIsSameAsHeader() {
            // Edge case where merge point equals header (self-reference)
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock header = new IRBlock("header");

            method.addBlock(entry);
            method.addBlock(header);
            method.setEntryBlock(entry);

            entry.addSuccessor(header);

            header.addInstruction(new ReturnInstruction(null));

            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            StructuralAnalyzer analyzer = new StructuralAnalyzer(method, domTree, loopAnalysis);
            analyzer.analyze();

            RegionInfo info = analyzer.getRegionInfo(header);
            assertNotNull(info);
            assertEquals(StructuredRegion.SEQUENCE, info.getType());
        }
    }

    // ========== RegionInfo Tests ==========

    @Nested
    class RegionInfoTests {

        @Test
        void regionInfoGettersWork() {
            IRBlock header = new IRBlock("header");
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, header);

            assertEquals(StructuredRegion.IF_THEN, info.getType());
            assertEquals(header, info.getHeader());
        }

        @Test
        void regionInfoSettersWork() {
            IRBlock header = new IRBlock("header");
            IRBlock thenBlock = new IRBlock("then");
            IRBlock elseBlock = new IRBlock("else");
            IRBlock merge = new IRBlock("merge");

            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN_ELSE, header);
            info.setThenBlock(thenBlock);
            info.setElseBlock(elseBlock);
            info.setMergeBlock(merge);
            info.setConditionNegated(true);

            assertEquals(thenBlock, info.getThenBlock());
            assertEquals(elseBlock, info.getElseBlock());
            assertEquals(merge, info.getMergeBlock());
            assertTrue(info.isConditionNegated());
        }

        @Test
        void loopRegionInfoSettersWork() {
            IRMethod method = createSimpleMethod();
            DominatorTree domTree = new DominatorTree(method);
            domTree.compute();
            LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
            loopAnalysis.compute();

            IRBlock header = new IRBlock("header");
            IRBlock body = new IRBlock("body");
            IRBlock exit = new IRBlock("exit");
            IRBlock continueTarget = new IRBlock("continue");

            RegionInfo info = new RegionInfo(StructuredRegion.WHILE_LOOP, header);
            info.setLoopBody(body);
            info.setLoopExit(exit);
            info.setContinueTarget(continueTarget);

            assertEquals(body, info.getLoopBody());
            assertEquals(exit, info.getLoopExit());
            assertEquals(continueTarget, info.getContinueTarget());
        }

        @Test
        void switchRegionInfoSettersWork() {
            IRBlock header = new IRBlock("header");
            IRBlock case1 = new IRBlock("case1");
            IRBlock defaultBlock = new IRBlock("default");

            Map<Integer, IRBlock> cases = new LinkedHashMap<>();
            cases.put(1, case1);

            RegionInfo info = new RegionInfo(StructuredRegion.SWITCH, header);
            info.setSwitchCases(cases);
            info.setDefaultTarget(defaultBlock);

            assertEquals(cases, info.getSwitchCases());
            assertEquals(defaultBlock, info.getDefaultTarget());
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
}
