package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.*;
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
import java.util.Map;

import com.tonic.analysis.source.emit.SourceEmitter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StatementRecoverer switch statement handling.
 * Coverage: tableswitch, lookupswitch, control flow patterns, edge cases.
 */
class SwitchRecoveryTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    // ========== Basic Tableswitch Tests ==========

    @Nested
    class TableswitchTests {

        @Test
        void simpleTableswitchWithConsecutiveCases() throws IOException {
            // switch (x) { case 0: return 10; case 1: return 20; case 2: return 30; case 3: return 40; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("simpleSwitch", "(I)I");

            Label case0 = mb.newLabel();
            Label case1 = mb.newLabel();
            Label case2 = mb.newLabel();
            Label case3 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 3, Map.of(
                    0, case0,
                    1, case1,
                    2, case2,
                    3, case3
                ), defaultCase)
                .label(case0)
                .iconst(10)
                .ireturn()
                .label(case1)
                .iconst(20)
                .ireturn()
                .label(case2)
                .iconst(30)
                .ireturn()
                .label(case3)
                .iconst(40)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "simpleSwitch");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void tableswitchWithOnlyDefaultCase() throws IOException {
            // switch (x) { default: return 100; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("defaultOnly", "(I)I");

            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 0, Map.of(), defaultCase)
                .label(defaultCase)
                .iconst(100)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "defaultOnly");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void tableswitchWithSingleCaseAndDefault() throws IOException {
            // switch (x) { case 0: return 50; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("singleCase", "(I)I");

            Label case0 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 0, Map.of(0, case0), defaultCase)
                .label(case0)
                .iconst(50)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "singleCase");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void tableswitchWithManyCases() throws IOException {
            // switch (x) { case 0-11: return case*10; default: return -1; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("manyCases", "(I)I");

            Label[] cases = new Label[12];
            for (int i = 0; i < 12; i++) {
                cases[i] = mb.newLabel();
            }
            Label defaultCase = mb.newLabel();

            Map<Integer, Label> caseMap = Map.ofEntries(
                Map.entry(0, cases[0]), Map.entry(1, cases[1]), Map.entry(2, cases[2]),
                Map.entry(3, cases[3]), Map.entry(4, cases[4]), Map.entry(5, cases[5]),
                Map.entry(6, cases[6]), Map.entry(7, cases[7]), Map.entry(8, cases[8]),
                Map.entry(9, cases[9]), Map.entry(10, cases[10]), Map.entry(11, cases[11])
            );

            mb.iload(0).tableswitch(0, 11, caseMap, defaultCase);

            for (int i = 0; i < 12; i++) {
                mb.label(cases[i]).iconst(i * 10).ireturn();
            }

            mb.label(defaultCase).iconst(-1).ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "manyCases");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void tableswitchAtEndOfMethod() throws IOException {
            // No code after switch - all cases return
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("endSwitch", "(I)I");

            Label case0 = mb.newLabel();
            Label case1 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 1, Map.of(0, case0, 1, case1), defaultCase)
                .label(case0)
                .iconst(1)
                .ireturn()
                .label(case1)
                .iconst(2)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "endSwitch");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }
    }

    // ========== Basic Lookupswitch Tests ==========

    @Nested
    class LookupswitchTests {

        @Test
        void simpleLookupswitchWithSparseCases() throws IOException {
            // switch (x) { case 1: return 100; case 10: return 200; case 100: return 300; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("sparseSwitch", "(I)I");

            Label case1 = mb.newLabel();
            Label case10 = mb.newLabel();
            Label case100 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .lookupswitch(Map.of(
                    1, case1,
                    10, case10,
                    100, case100
                ), defaultCase)
                .label(case1)
                .iconst(100)
                .ireturn()
                .label(case10)
                .iconst(200)
                .ireturn()
                .label(case100)
                .iconst(300)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "sparseSwitch");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void lookupswitchWithNegativeValues() throws IOException {
            // switch (x) { case -5: return 1; case 0: return 2; case 5: return 3; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("negativeSwitch", "(I)I");

            Label caseNeg5 = mb.newLabel();
            Label case0 = mb.newLabel();
            Label case5 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .lookupswitch(Map.of(
                    -5, caseNeg5,
                    0, case0,
                    5, case5
                ), defaultCase)
                .label(caseNeg5)
                .iconst(1)
                .ireturn()
                .label(case0)
                .iconst(2)
                .ireturn()
                .label(case5)
                .iconst(3)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "negativeSwitch");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void lookupswitchWithExtremeValues() throws IOException {
            // switch (x) { case MIN_VALUE: return 1; case MAX_VALUE: return 2; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("extremeSwitch", "(I)I");

            Label caseMin = mb.newLabel();
            Label caseMax = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .lookupswitch(Map.of(
                    Integer.MIN_VALUE, caseMin,
                    Integer.MAX_VALUE, caseMax
                ), defaultCase)
                .label(caseMin)
                .iconst(1)
                .ireturn()
                .label(caseMax)
                .iconst(2)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "extremeSwitch");
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
        void switchWithFallThrough() throws IOException {
            // switch (x) { case 0: case 1: return 10; case 2: return 20; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("fallThrough", "(I)I");

            Label case0and1 = mb.newLabel();
            Label case2 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 2, Map.of(
                    0, case0and1,
                    1, case0and1,
                    2, case2
                ), defaultCase)
                .label(case0and1)
                .iconst(10)
                .ireturn()
                .label(case2)
                .iconst(20)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "fallThrough");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void switchWithBreakInEachCase() throws IOException {
            // switch (x) { case 0: y=1; break; case 1: y=2; break; default: y=0; } return y;
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("withBreaks", "(I)I");

            Label case0 = mb.newLabel();
            Label case1 = mb.newLabel();
            Label defaultCase = mb.newLabel();
            Label end = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 1, Map.of(0, case0, 1, case1), defaultCase)
                .label(case0)
                .iconst(1)
                .istore(1)
                .goto_(end)
                .label(case1)
                .iconst(2)
                .istore(1)
                .goto_(end)
                .label(defaultCase)
                .iconst(0)
                .istore(1)
                .label(end)
                .iload(1)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "withBreaks");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void switchWithMixedFallThroughAndBreak() throws IOException {
            // switch (x) { case 0: case 1: y=10; break; case 2: y=20; case 3: y=30; break; default: y=0; } return y;
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("mixedFlow", "(I)I");

            Label case0and1 = mb.newLabel();
            Label case2 = mb.newLabel();
            Label case3 = mb.newLabel();
            Label defaultCase = mb.newLabel();
            Label end = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 3, Map.of(
                    0, case0and1,
                    1, case0and1,
                    2, case2,
                    3, case3
                ), defaultCase)
                .label(case0and1)
                .iconst(10)
                .istore(1)
                .goto_(end)
                .label(case2)
                .iconst(20)
                .istore(1)
                .label(case3)
                .iconst(30)
                .istore(1)
                .goto_(end)
                .label(defaultCase)
                .iconst(0)
                .istore(1)
                .label(end)
                .iload(1)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "mixedFlow");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void switchWithReturnInCases() throws IOException {
            // switch (x) { case 0: return 10; case 1: return 20; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("returnsInCases", "(I)I");

            Label case0 = mb.newLabel();
            Label case1 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 1, Map.of(0, case0, 1, case1), defaultCase)
                .label(case0)
                .iconst(10)
                .ireturn()
                .label(case1)
                .iconst(20)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "returnsInCases");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void switchWithEmptyCases() throws IOException {
            // switch (x) { case 0: case 1: case 2: return 100; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("emptyCases", "(I)I");

            Label allCases = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 2, Map.of(
                    0, allCases,
                    1, allCases,
                    2, allCases
                ), defaultCase)
                .label(allCases)
                .iconst(100)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "emptyCases");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }
    }

    // ========== Complex Pattern Tests ==========

    @Nested
    class ComplexPatternTests {

        @Test
        void nestedSwitchStatements() throws IOException {
            // switch (x) { case 0: switch(y) { case 0: return 1; default: return 2; } default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("nestedSwitch", "(II)I");

            Label outerCase0 = mb.newLabel();
            Label outerDefault = mb.newLabel();
            Label innerCase0 = mb.newLabel();
            Label innerDefault = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 0, Map.of(0, outerCase0), outerDefault)
                .label(outerCase0)
                .iload(1)
                .tableswitch(0, 0, Map.of(0, innerCase0), innerDefault)
                .label(innerCase0)
                .iconst(1)
                .ireturn()
                .label(innerDefault)
                .iconst(2)
                .ireturn()
                .label(outerDefault)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "nestedSwitch");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void switchInsideWhileLoop() throws IOException {
            // while (i > 0) { switch (x) { case 0: sum++; break; case 1: sum+=2; break; } i--; } return sum;
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("switchInLoop", "(II)I");

            Label loopStart = mb.newLabel();
            Label loopEnd = mb.newLabel();
            Label case0 = mb.newLabel();
            Label case1 = mb.newLabel();
            Label defaultCase = mb.newLabel();
            Label afterSwitch = mb.newLabel();

            mb.iconst(0)
                .istore(2) // sum
                .label(loopStart)
                .iload(1) // i
                .ifle(loopEnd)
                .iload(0) // x
                .tableswitch(0, 1, Map.of(0, case0, 1, case1), defaultCase)
                .label(case0)
                .iload(2)
                .iconst(1)
                .iadd()
                .istore(2)
                .goto_(afterSwitch)
                .label(case1)
                .iload(2)
                .iconst(2)
                .iadd()
                .istore(2)
                .goto_(afterSwitch)
                .label(defaultCase)
                .label(afterSwitch)
                .iinc(1, -1)
                .goto_(loopStart)
                .label(loopEnd)
                .iload(2)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "switchInLoop");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void loopInsideSwitchCase() throws IOException {
            // switch (x) { case 0: while (i > 0) { sum++; i--; } return sum; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("loopInSwitch", "(II)I");

            Label case0 = mb.newLabel();
            Label defaultCase = mb.newLabel();
            Label loopStart = mb.newLabel();
            Label loopEnd = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 0, Map.of(0, case0), defaultCase)
                .label(case0)
                .iconst(0)
                .istore(2) // sum
                .label(loopStart)
                .iload(1) // i
                .ifle(loopEnd)
                .iload(2)
                .iconst(1)
                .iadd()
                .istore(2)
                .iinc(1, -1)
                .goto_(loopStart)
                .label(loopEnd)
                .iload(2)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "loopInSwitch");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void switchInsideIfElse() throws IOException {
            // if (flag) { switch(x) { case 0: return 1; default: return 2; } } else { return 3; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("switchInIf", "(ZI)I");

            Label thenBranch = mb.newLabel();
            Label elseBranch = mb.newLabel();
            Label case0 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .ifeq(elseBranch)
                .label(thenBranch)
                .iload(1)
                .tableswitch(0, 0, Map.of(0, case0), defaultCase)
                .label(case0)
                .iconst(1)
                .ireturn()
                .label(defaultCase)
                .iconst(2)
                .ireturn()
                .label(elseBranch)
                .iconst(3)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "switchInIf");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void ifElseInsideSwitchCase() throws IOException {
            // switch (x) { case 0: if (flag) return 1; else return 2; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("ifInSwitch", "(IZ)I");

            Label case0 = mb.newLabel();
            Label defaultCase = mb.newLabel();
            Label elseBranch = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 0, Map.of(0, case0), defaultCase)
                .label(case0)
                .iload(1)
                .ifeq(elseBranch)
                .iconst(1)
                .ireturn()
                .label(elseBranch)
                .iconst(2)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "ifInSwitch");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }
    }

    // ========== Edge Cases ==========

    @Nested
    class EdgeCaseTests {

        @Test
        void switchOnNegativeValues() throws IOException {
            // switch (x) { case -3: return 1; case -2: return 2; case -1: return 3; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("negativeValues", "(I)I");

            Label caseNeg3 = mb.newLabel();
            Label caseNeg2 = mb.newLabel();
            Label caseNeg1 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .lookupswitch(Map.of(
                    -3, caseNeg3,
                    -2, caseNeg2,
                    -1, caseNeg1
                ), defaultCase)
                .label(caseNeg3)
                .iconst(1)
                .ireturn()
                .label(caseNeg2)
                .iconst(2)
                .ireturn()
                .label(caseNeg1)
                .iconst(3)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "negativeValues");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void switchWithIntegerExtremeValues() throws IOException {
            // switch (x) { case MIN_VALUE: return -1; case 0: return 0; case MAX_VALUE: return 1; default: return 99; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("extremeValues", "(I)I");

            Label caseMin = mb.newLabel();
            Label case0 = mb.newLabel();
            Label caseMax = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .lookupswitch(Map.of(
                    Integer.MIN_VALUE, caseMin,
                    0, case0,
                    Integer.MAX_VALUE, caseMax
                ), defaultCase)
                .label(caseMin)
                .iconst(-1)
                .ireturn()
                .label(case0)
                .iconst(0)
                .ireturn()
                .label(caseMax)
                .iconst(1)
                .ireturn()
                .label(defaultCase)
                .iconst(99)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "extremeValues");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void emptySwitchBodyOnlyDefault() throws IOException {
            // switch (x) { default: return x; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("emptySwitch", "(I)I");

            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .lookupswitch(Map.of(), defaultCase)
                .label(defaultCase)
                .iload(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "emptySwitch");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void switchWithSameTargetForMultipleCases() throws IOException {
            // switch (x) { case 0: case 5: case 10: return 100; default: return 0; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("sameTarget", "(I)I");

            Label sharedCase = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .lookupswitch(Map.of(
                    0, sharedCase,
                    5, sharedCase,
                    10, sharedCase
                ), defaultCase)
                .label(sharedCase)
                .iconst(100)
                .ireturn()
                .label(defaultCase)
                .iconst(0)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "sameTarget");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }

        @Test
        void switchAsLastStatementBeforeReturn() throws IOException {
            // int result; switch (x) { case 0: result=1; break; case 1: result=2; break; default: result=0; } return result;
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("beforeReturn", "(I)I");

            Label case0 = mb.newLabel();
            Label case1 = mb.newLabel();
            Label defaultCase = mb.newLabel();
            Label end = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 1, Map.of(0, case0, 1, case1), defaultCase)
                .label(case0)
                .iconst(1)
                .istore(1)
                .goto_(end)
                .label(case1)
                .iconst(2)
                .istore(1)
                .goto_(end)
                .label(defaultCase)
                .iconst(0)
                .istore(1)
                .label(end)
                .iload(1)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "beforeReturn");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            assertNotNull(body);
            assertFalse(body.getStatements().isEmpty());
        }
    }

    // ========== Default Case Content Tests ==========

    @Nested
    class DefaultCaseContentTests {

        @Test
        void defaultCaseWithStatementsHasContent() throws IOException {
            // Regression test: switch (x) { case 0: return 1; default: int y = 10; return y; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("defaultWithContent", "(I)I");

            Label case0 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 0, Map.of(0, case0), defaultCase)
                .label(case0)
                .iconst(1)
                .ireturn()
                .label(defaultCase)
                .iconst(10)
                .istore(1)
                .iload(1)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "defaultWithContent");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            SwitchStmt switchStmt = findSwitch(body);
            assertNotNull(switchStmt, "Should have a switch statement");

            SwitchCase defaultSwitchCase = switchStmt.getCases().stream()
                .filter(SwitchCase::isDefault)
                .findFirst()
                .orElse(null);
            assertNotNull(defaultSwitchCase, "Should have a default case");

            assertFalse(defaultSwitchCase.statements().isEmpty(),
                "Default case should have statements, not be empty");
        }

        @Test
        void defaultCaseWithMultipleStatements() throws IOException {
            // switch (x) { case 0: return 1; default: y = 5; y = y + 1; return y; }
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("defaultMultiStmt", "(I)I");

            Label case0 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 0, Map.of(0, case0), defaultCase)
                .label(case0)
                .iconst(1)
                .ireturn()
                .label(defaultCase)
                .iconst(5)
                .istore(1)
                .iload(1)
                .iconst(1)
                .iadd()
                .istore(1)
                .iload(1)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "defaultMultiStmt");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            SwitchStmt switchStmt = findSwitch(body);
            assertNotNull(switchStmt, "Should have a switch statement");

            SwitchCase defaultSwitchCase = switchStmt.getCases().stream()
                .filter(SwitchCase::isDefault)
                .findFirst()
                .orElse(null);
            assertNotNull(defaultSwitchCase, "Should have a default case");

            assertTrue(defaultSwitchCase.statements().size() >= 2,
                "Default case should have multiple statements");
        }

        @Test
        void defaultCaseEmittedWithContent() throws IOException {
            // Test that default case content is properly emitted in source output
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("defaultEmit", "(I)I");

            Label case0 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 0, Map.of(0, case0), defaultCase)
                .label(case0)
                .iconst(1)
                .ireturn()
                .label(defaultCase)
                .iconst(42)
                .istore(1)
                .iload(1)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "defaultEmit");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            String source = SourceEmitter.emit(body);

            assertTrue(source.contains("default:"), "Source should contain 'default:'");
            assertTrue(source.contains("42") || source.contains("local1 = "),
                "Default case should contain assignment or constant 42: " + source);
        }

        @Test
        void switchWithMergeBlockDefaultHasContent() throws IOException {
            // More complex case: switch with merge block after
            // switch (x) { case 0: y=1; break; case 1: y=2; break; default: y=99; } return y;
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("switchMerge", "(I)I");

            Label case0 = mb.newLabel();
            Label case1 = mb.newLabel();
            Label defaultCase = mb.newLabel();
            Label end = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 1, Map.of(0, case0, 1, case1), defaultCase)
                .label(case0)
                .iconst(1)
                .istore(1)
                .goto_(end)
                .label(case1)
                .iconst(2)
                .istore(1)
                .goto_(end)
                .label(defaultCase)
                .iconst(99)
                .istore(1)
                .label(end)
                .iload(1)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "switchMerge");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            SwitchStmt switchStmt = findSwitch(body);
            assertNotNull(switchStmt, "Should have a switch statement");

            SwitchCase defaultSwitchCase = switchStmt.getCases().stream()
                .filter(SwitchCase::isDefault)
                .findFirst()
                .orElse(null);
            assertNotNull(defaultSwitchCase, "Should have a default case");

            assertFalse(defaultSwitchCase.statements().isEmpty(),
                "Default case with merge block should still have assignment statement");
        }

        @Test
        void defaultTargetIsMergeBlock() throws IOException {
            // Edge case: default points to merge block (all cases fall through to default)
            // switch (x) { case 0: y=1; default: } return y;
            // Here default has no code - it's just the fall-through point
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("defaultIsMerge", "(I)I");

            Label case0 = mb.newLabel();
            Label defaultCase = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 0, Map.of(0, case0), defaultCase)
                .label(case0)
                .iconst(1)
                .istore(1)
                .label(defaultCase)
                .iload(1)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "defaultIsMerge");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            SwitchStmt switchStmt = findSwitch(body);
            assertNotNull(switchStmt, "Should have a switch statement");

            SwitchCase defaultSwitchCase = switchStmt.getCases().stream()
                .filter(SwitchCase::isDefault)
                .findFirst()
                .orElse(null);
            assertNotNull(defaultSwitchCase, "Should have a default case");
        }

        @Test
        void defaultCaseWithBreakToMerge() throws IOException {
            // switch (x) { case 0: y=1; break; default: y=99; } return y;
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchTest")
                .publicStaticMethod("defaultWithBreak", "(I)I");

            Label case0 = mb.newLabel();
            Label defaultCase = mb.newLabel();
            Label end = mb.newLabel();

            mb.iload(0)
                .tableswitch(0, 0, Map.of(0, case0), defaultCase)
                .label(case0)
                .iconst(1)
                .istore(1)
                .goto_(end)
                .label(defaultCase)
                .iconst(99)
                .istore(1)
                .label(end)
                .iload(1)
                .ireturn();

            ClassFile cf = mb.build();
            MethodEntry method = findMethod(cf, "defaultWithBreak");
            IRMethod ir = TestUtils.liftMethod(method);

            StatementRecoverer recoverer = createRecoverer(ir, method);
            BlockStmt body = recoverer.recoverMethod();

            SwitchStmt switchStmt = findSwitch(body);
            assertNotNull(switchStmt, "Should have a switch statement");

            SwitchCase defaultSwitchCase = switchStmt.getCases().stream()
                .filter(SwitchCase::isDefault)
                .findFirst()
                .orElse(null);
            assertNotNull(defaultSwitchCase, "Should have a default case");

            assertFalse(defaultSwitchCase.statements().isEmpty(),
                "Default case with break should still have assignment statement");
        }
    }

    // ========== Helper Methods ==========

    private SwitchStmt findSwitch(BlockStmt body) {
        for (Statement stmt : body.getStatements()) {
            if (stmt instanceof SwitchStmt) {
                return (SwitchStmt) stmt;
            }
        }
        return null;
    }

    private MethodEntry findMethod(ClassFile cf, String name) {
        return cf.getMethods().stream()
            .filter(m -> m.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Method not found: " + name));
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

        ControlFlowContext cfContext = new ControlFlowContext(
            ir, domTree, loopAnalysis, recoveryContext
        );

        return new StatementRecoverer(cfContext, analyzer, exprRecoverer);
    }
}
