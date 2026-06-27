package com.tonic.testutil;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.ConditionalBranchInstruction;
import com.tonic.analysis.instruction.GotoInstruction;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BytecodeBuilder control flow support (labels, branches, jumps).
 */
class BytecodeBuilderControlFlowTest {

    // Helper to find method by name
    private MethodEntry findMethod(ClassFile cf, String name) {
        return cf.getMethods().stream()
            .filter(m -> m.getName().equals(name))
            .findFirst()
            .orElse(null);
    }

    // ========== Forward Jump Tests ==========

    @Test
    void testSimpleIfForwardJump() throws IOException {
        // if (a > 0) { return 1; } return 0;
        // iload_0, ifle end, iconst_1, ireturn, end: iconst_0, ireturn
        BytecodeBuilder.Label end = null;

        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/IfTest")
            .publicStaticMethod("test", "(I)I");

        BytecodeBuilder.Label endLabel = mb.newLabel();

        ClassFile cf = mb
            .iload(0)
            .ifle(endLabel)     // if <= 0, jump to end
            .iconst(1)
            .ireturn()
            .label(endLabel)    // end:
            .iconst(0)
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);

        // Verify bytecode structure
        CodeWriter cw = new CodeWriter(method);
        int instructionCount = cw.getInstructionCount();
        assertTrue(instructionCount >= 5, "Should have at least 5 instructions");

        // Verify we can lift to IR (proves bytecode is valid)
        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
        assertTrue(ir.getBlocks().size() >= 2, "Should have multiple basic blocks");
    }

    @Test
    void testIfElseForwardJumps() throws IOException {
        // if (a > 0) { return 1; } else { return -1; }
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/IfElseTest")
            .publicStaticMethod("test", "(I)I");

        BytecodeBuilder.Label elseLabel = mb.newLabel();
        BytecodeBuilder.Label endLabel = mb.newLabel();

        ClassFile cf = mb
            .iload(0)
            .ifle(elseLabel)    // if <= 0, jump to else
            .iconst(1)
            .goto_(endLabel)    // jump over else
            .label(elseLabel)   // else:
            .iconst(-1)
            .label(endLabel)    // end:
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);

        // Lift to verify structure
        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
    }

    // ========== Backward Jump Tests (Loops) ==========

    @Test
    void testWhileLoopBackwardJump() throws IOException {
        // int sum = 0; while (i > 0) { sum += i; i--; } return sum;
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/WhileTest")
            .publicStaticMethod("sum", "(I)I");

        BytecodeBuilder.Label loopStart = mb.newLabel();
        BytecodeBuilder.Label loopEnd = mb.newLabel();

        ClassFile cf = mb
            .iconst(0)          // sum = 0
            .istore(1)          // store in local 1
            .label(loopStart)   // loop:
            .iload(0)           // load i
            .ifle(loopEnd)      // if i <= 0, exit loop
            .iload(1)           // load sum
            .iload(0)           // load i
            .iadd()             // sum + i
            .istore(1)          // store sum
            .iinc(0, -1)        // i--
            .goto_(loopStart)   // back to loop
            .label(loopEnd)     // end:
            .iload(1)           // load sum
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "sum");
        assertNotNull(method);

        // Verify we have a backward jump (loop)
        CodeWriter cw = new CodeWriter(method);
        boolean hasBackwardJump = false;
        for (Instruction instr : cw.getInstructions()) {
            if (instr instanceof GotoInstruction) {
                GotoInstruction gotoInstr = (GotoInstruction) instr;
                // Backward jumps have negative offsets
                if (gotoInstr.getBranchOffset() < 0) {
                    hasBackwardJump = true;
                }
            }
        }
        assertTrue(hasBackwardJump, "Should have a backward jump for the loop");

        // Verify IR structure
        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
    }

    @Test
    void testForLoopPattern() throws IOException {
        // for (int i = 0; i < n; i++) { result++; } return result;
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/ForTest")
            .publicStaticMethod("countTo", "(I)I");

        BytecodeBuilder.Label condition = mb.newLabel();
        BytecodeBuilder.Label loopEnd = mb.newLabel();

        ClassFile cf = mb
            .iconst(0)          // result = 0
            .istore(1)
            .iconst(0)          // i = 0
            .istore(2)
            .label(condition)   // condition:
            .iload(2)           // load i
            .iload(0)           // load n
            .if_icmpge(loopEnd) // if i >= n, exit
            .iinc(1, 1)         // result++
            .iinc(2, 1)         // i++
            .goto_(condition)   // back to condition
            .label(loopEnd)     // end:
            .iload(1)           // load result
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "countTo");
        assertNotNull(method);

        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
    }

    // ========== All Branch Types ==========

    @Test
    void testAllConditionalBranches() throws IOException {
        // Test each conditional branch type
        testBranch("ifeq", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(0).ifeq(l).iconst(1).ireturn().label(l).iconst(0).ireturn();
        });

        testBranch("ifne", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(1).ifne(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });

        testBranch("iflt", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(-1).iflt(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });

        testBranch("ifge", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(0).ifge(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });

        testBranch("ifgt", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(1).ifgt(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });

        testBranch("ifle", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(0).ifle(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });
    }

    @Test
    void testIntComparisonBranches() throws IOException {
        testBranch("if_icmpeq", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(1).iconst(1).if_icmpeq(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });

        testBranch("if_icmpne", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(1).iconst(2).if_icmpne(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });

        testBranch("if_icmplt", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(1).iconst(2).if_icmplt(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });

        testBranch("if_icmpge", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(2).iconst(1).if_icmpge(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });

        testBranch("if_icmpgt", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(2).iconst(1).if_icmpgt(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });

        testBranch("if_icmple", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.iconst(1).iconst(2).if_icmple(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });
    }

    @Test
    void testNullCheckBranches() throws IOException {
        testBranchObject("ifnull", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.aconst_null().ifnull(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });

        testBranchObject("ifnonnull", mb -> {
            BytecodeBuilder.Label l = mb.newLabel();
            return mb.aload(0).ifnonnull(l).iconst(0).ireturn().label(l).iconst(1).ireturn();
        });
    }

    // ========== Nested Control Flow ==========

    @Test
    void testNestedIfStatements() throws IOException {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/NestedIf")
            .publicStaticMethod("classify", "(I)I");

        BytecodeBuilder.Label negative = mb.newLabel();
        BytecodeBuilder.Label zero = mb.newLabel();
        BytecodeBuilder.Label end = mb.newLabel();

        ClassFile cf = mb
            .iload(0)
            .iflt(negative)     // if < 0, negative
            .iload(0)
            .ifeq(zero)         // if == 0, zero
            .iconst(1)          // positive
            .goto_(end)
            .label(negative)
            .iconst(-1)         // negative
            .goto_(end)
            .label(zero)
            .iconst(0)          // zero
            .label(end)
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "classify");
        assertNotNull(method);

        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
        assertTrue(ir.getBlocks().size() >= 4, "Should have multiple blocks for nested ifs");
    }

    @Test
    void testNestedLoops() throws IOException {
        // Outer loop with inner loop
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/NestedLoops")
            .publicStaticMethod("nestedCount", "(II)I");

        BytecodeBuilder.Label outerStart = mb.newLabel();
        BytecodeBuilder.Label innerStart = mb.newLabel();
        BytecodeBuilder.Label innerEnd = mb.newLabel();
        BytecodeBuilder.Label outerEnd = mb.newLabel();

        ClassFile cf = mb
            .iconst(0)          // count = 0
            .istore(2)
            .label(outerStart)  // outer:
            .iload(0)           // i
            .ifle(outerEnd)     // if i <= 0, exit outer
            .iload(1)           // j
            .istore(3)          // temp = j
            .label(innerStart)  // inner:
            .iload(3)           // temp
            .ifle(innerEnd)     // if temp <= 0, exit inner
            .iinc(2, 1)         // count++
            .iinc(3, -1)        // temp--
            .goto_(innerStart)  // back to inner
            .label(innerEnd)    // inner end:
            .iinc(0, -1)        // i--
            .goto_(outerStart)  // back to outer
            .label(outerEnd)    // outer end:
            .iload(2)           // count
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "nestedCount");
        assertNotNull(method);

        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
    }

    // ========== Comparison Instructions ==========

    @Test
    void testLongComparison() throws IOException {
        // Compare two longs: return 1 if a > b, -1 if a < b, 0 if equal
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/LongCmp")
            .publicStaticMethod("compare", "(JJ)I");

        BytecodeBuilder.Label negative = mb.newLabel();
        BytecodeBuilder.Label positive = mb.newLabel();
        BytecodeBuilder.Label end = mb.newLabel();

        ClassFile cf = mb
            .lload(0)           // load first long (takes slots 0,1)
            .lload(2)           // load second long (takes slots 2,3)
            .lcmp()             // compare longs: pushes -1, 0, or 1
            .dup()              // duplicate for second comparison
            .iflt(negative)     // if result < 0, jump to negative
            .ifgt(positive)     // if result > 0, jump to positive
            .iconst(0)          // equal: return 0
            .goto_(end)
            .label(negative)
            .pop()              // discard the duplicate
            .iconst(-1)
            .goto_(end)
            .label(positive)
            .iconst(1)
            .label(end)
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "compare");
        assertNotNull(method);

        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
    }

    @Test
    void testFloatComparison() throws IOException {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/FloatCmp")
            .publicStaticMethod("isGreater", "(FF)I");

        BytecodeBuilder.Label yes = mb.newLabel();

        ClassFile cf = mb
            .fload(0)
            .fload(1)
            .fcmpg()            // compare floats (greater on NaN)
            .ifgt(yes)
            .iconst(0)
            .ireturn()
            .label(yes)
            .iconst(1)
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "isGreater");
        assertNotNull(method);

        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
    }

    // ========== Edge Cases ==========

    @Test
    void testMultipleLabelsAtSamePosition() throws IOException {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/MultiLabel")
            .publicStaticMethod("test", "()I");

        BytecodeBuilder.Label l1 = mb.newLabel();
        BytecodeBuilder.Label l2 = mb.newLabel();

        // Both labels at same position
        ClassFile cf = mb
            .iconst(1)
            .ifeq(l1)
            .iconst(1)
            .ireturn()
            .label(l1)
            .label(l2)          // Another label at same position
            .iconst(0)
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);
    }

    @Test
    void testLabelAtEnd() throws IOException {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/LabelEnd")
            .publicStaticMethod("test", "()V");

        BytecodeBuilder.Label end = mb.newLabel();

        ClassFile cf = mb
            .goto_(end)
            .label(end)
            .vreturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);
    }

    // ========== Exception Handling Tests ==========

    @Test
    void testSimpleTryCatch() throws IOException {
        // try { result = 1 / 0; } catch (ArithmeticException e) { result = -1; } return result;
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryCatchTest")
            .publicStaticMethod("test", "()I");

        BytecodeBuilder.Label tryStart = mb.newLabel();
        BytecodeBuilder.Label tryEnd = mb.newLabel();
        BytecodeBuilder.Label handler = mb.newLabel();
        BytecodeBuilder.Label end = mb.newLabel();

        ClassFile cf = mb
            .label(tryStart)
            .iconst(1)
            .iconst(0)
            .idiv()             // Will throw ArithmeticException
            .istore(0)          // result = 1/0
            .label(tryEnd)
            .goto_(end)
            .label(handler)
            .astore(1)          // Store exception
            .iconst(-1)
            .istore(0)          // result = -1
            .label(end)
            .iload(0)
            .ireturn()
            .tryCatch(tryStart, tryEnd, handler, "java/lang/ArithmeticException")
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);

        // Verify exception table was created
        com.tonic.parser.attribute.CodeAttribute code = method.getCodeAttribute();
        assertNotNull(code);
        assertFalse(code.getExceptionTable().isEmpty(), "Exception table should not be empty");
        assertEquals(1, code.getExceptionTable().size(), "Should have one exception handler");

        // Verify exception table entry details
        com.tonic.parser.attribute.table.ExceptionTableEntry entry = code.getExceptionTable().get(0);
        assertTrue(entry.getStartPc() >= 0, "Start PC should be valid");
        assertTrue(entry.getEndPc() > entry.getStartPc(), "End PC should be after start PC");
        assertTrue(entry.getHandlerPc() >= 0, "Handler PC should be valid");
        assertTrue(entry.getCatchType() > 0, "Catch type should reference ArithmeticException");

        // Verify the catch type references the correct class
        ConstPool constPool = method.getClassFile().getConstPool();
        String exceptionClass = constPool.getClassName(entry.getCatchType());
        assertEquals("java/lang/ArithmeticException", exceptionClass);
    }

    @Test
    void testTryCatchAll() throws IOException {
        // try { return 1; } catch (Throwable t) { return -1; }
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryCatchAllTest")
            .publicStaticMethod("test", "()I");

        BytecodeBuilder.Label tryStart = mb.newLabel();
        BytecodeBuilder.Label tryEnd = mb.newLabel();
        BytecodeBuilder.Label handler = mb.newLabel();

        ClassFile cf = mb
            .label(tryStart)
            .iconst(1)
            .ireturn()
            .label(tryEnd)
            .label(handler)
            .astore(0)          // Store exception
            .iconst(-1)
            .ireturn()
            .tryCatchAll(tryStart, tryEnd, handler)
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);

        // Verify exception table
        com.tonic.parser.attribute.CodeAttribute code = method.getCodeAttribute();
        assertFalse(code.getExceptionTable().isEmpty());

        // Verify catch-all has catchType = 0
        com.tonic.parser.attribute.table.ExceptionTableEntry entry = code.getExceptionTable().get(0);
        assertEquals(0, entry.getCatchType(), "Catch-all handler should have catchType = 0");
    }

    @Test
    void testMultipleCatchBlocks() throws IOException {
        // try { ... } catch (ArithmeticException e) { ... } catch (Exception e) { ... }
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/MultiCatchTest")
            .publicStaticMethod("test", "()I");

        BytecodeBuilder.Label tryStart = mb.newLabel();
        BytecodeBuilder.Label tryEnd = mb.newLabel();
        BytecodeBuilder.Label arithmeticHandler = mb.newLabel();
        BytecodeBuilder.Label exceptionHandler = mb.newLabel();
        BytecodeBuilder.Label end = mb.newLabel();

        ClassFile cf = mb
            .label(tryStart)
            .iconst(1)
            .iconst(0)
            .idiv()
            .istore(0)
            .label(tryEnd)
            .goto_(end)
            .label(arithmeticHandler)
            .astore(1)
            .iconst(-1)
            .istore(0)
            .goto_(end)
            .label(exceptionHandler)
            .astore(1)
            .iconst(-2)
            .istore(0)
            .label(end)
            .iload(0)
            .ireturn()
            .tryCatch(tryStart, tryEnd, arithmeticHandler, "java/lang/ArithmeticException")
            .tryCatch(tryStart, tryEnd, exceptionHandler, "java/lang/Exception")
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);

        // Verify multiple exception handlers
        com.tonic.parser.attribute.CodeAttribute code = method.getCodeAttribute();
        assertEquals(2, code.getExceptionTable().size(), "Should have two exception handlers");

        // Verify both handlers cover the same try block
        com.tonic.parser.attribute.table.ExceptionTableEntry entry1 = code.getExceptionTable().get(0);
        com.tonic.parser.attribute.table.ExceptionTableEntry entry2 = code.getExceptionTable().get(1);
        assertEquals(entry1.getStartPc(), entry2.getStartPc(), "Both handlers should have same start");
        assertEquals(entry1.getEndPc(), entry2.getEndPc(), "Both handlers should have same end");
        assertNotEquals(entry1.getHandlerPc(), entry2.getHandlerPc(), "Handlers should be different");
    }

    @Test
    void testNestedTryCatch() throws IOException {
        // try { try { ... } catch (ArithmeticException e) { ... } } catch (Exception e) { ... }
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/NestedTryCatchTest")
            .publicStaticMethod("test", "()I");

        BytecodeBuilder.Label outerTryStart = mb.newLabel();
        BytecodeBuilder.Label innerTryStart = mb.newLabel();
        BytecodeBuilder.Label innerTryEnd = mb.newLabel();
        BytecodeBuilder.Label innerHandler = mb.newLabel();
        BytecodeBuilder.Label outerTryEnd = mb.newLabel();
        BytecodeBuilder.Label outerHandler = mb.newLabel();
        BytecodeBuilder.Label end = mb.newLabel();

        ClassFile cf = mb
            .label(outerTryStart)
            .label(innerTryStart)
            .iconst(1)
            .iconst(0)
            .idiv()
            .istore(0)
            .label(innerTryEnd)
            .goto_(end)
            .label(innerHandler)
            .astore(1)
            .iconst(-1)
            .istore(0)
            .label(outerTryEnd)
            .goto_(end)
            .label(outerHandler)
            .astore(1)
            .iconst(-2)
            .istore(0)
            .label(end)
            .iload(0)
            .ireturn()
            .tryCatch(innerTryStart, innerTryEnd, innerHandler, "java/lang/ArithmeticException")
            .tryCatch(outerTryStart, outerTryEnd, outerHandler, "java/lang/Exception")
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);

        // Verify nested exception handlers
        com.tonic.parser.attribute.CodeAttribute code = method.getCodeAttribute();
        assertEquals(2, code.getExceptionTable().size(), "Should have two exception handlers");

        // Verify IR can handle exception table
        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir, "Should successfully lift method with exception handlers");
    }

    // ========== Switch Statement Tests ==========

    @Test
    void testSimpleTableSwitch() throws IOException {
        // switch (x) { case 1: return 10; case 2: return 20; default: return 0; }
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TableSwitchTest")
            .publicStaticMethod("test", "(I)I");

        BytecodeBuilder.Label case1 = mb.newLabel();
        BytecodeBuilder.Label case2 = mb.newLabel();
        BytecodeBuilder.Label defaultCase = mb.newLabel();

        Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
        cases.put(1, case1);
        cases.put(2, case2);

        ClassFile cf = mb
            .iload(0)
            .tableswitch(1, 2, cases, defaultCase)
            .label(case1)
            .iconst(10)
            .ireturn()
            .label(case2)
            .iconst(20)
            .ireturn()
            .label(defaultCase)
            .iconst(0)
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);

        // Verify we can lift to IR
        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
        assertTrue(ir.getBlocks().size() >= 4, "Should have at least 4 blocks (switch + 3 cases)");
    }

    @Test
    void testTableSwitchWithGaps() throws IOException {
        // switch (x) { case 0: return 0; case 2: return 20; default: return -1; }
        // case 1 is missing, so it should use default
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TableSwitchGapsTest")
            .publicStaticMethod("test", "(I)I");

        BytecodeBuilder.Label case0 = mb.newLabel();
        BytecodeBuilder.Label case2 = mb.newLabel();
        BytecodeBuilder.Label defaultCase = mb.newLabel();

        Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
        cases.put(0, case0);
        cases.put(2, case2);
        // case 1 is missing - will use default

        ClassFile cf = mb
            .iload(0)
            .tableswitch(0, 2, cases, defaultCase)
            .label(case0)
            .iconst(0)
            .ireturn()
            .label(case2)
            .iconst(20)
            .ireturn()
            .label(defaultCase)
            .iconst(-1)
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);

        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
    }

    @Test
    void testLookupSwitch() throws IOException {
        // switch (x) { case 10: return 1; case 100: return 2; case 1000: return 3; default: return 0; }
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/LookupSwitchTest")
            .publicStaticMethod("test", "(I)I");

        BytecodeBuilder.Label case10 = mb.newLabel();
        BytecodeBuilder.Label case100 = mb.newLabel();
        BytecodeBuilder.Label case1000 = mb.newLabel();
        BytecodeBuilder.Label defaultCase = mb.newLabel();

        Map<Integer, BytecodeBuilder.Label> cases = new java.util.LinkedHashMap<>();
        cases.put(10, case10);
        cases.put(100, case100);
        cases.put(1000, case1000);

        ClassFile cf = mb
            .iload(0)
            .lookupswitch(cases, defaultCase)
            .label(case10)
            .iconst(1)
            .ireturn()
            .label(case100)
            .iconst(2)
            .ireturn()
            .label(case1000)
            .iconst(3)
            .ireturn()
            .label(defaultCase)
            .iconst(0)
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);

        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
        assertTrue(ir.getBlocks().size() >= 5, "Should have at least 5 blocks (switch + 4 cases)");
    }

    @Test
    void testSwitchWithFallthrough() throws IOException {
        // switch (x) { case 1: case 2: return 12; default: return 0; }
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchFallthroughTest")
            .publicStaticMethod("test", "(I)I");

        BytecodeBuilder.Label case12 = mb.newLabel();
        BytecodeBuilder.Label defaultCase = mb.newLabel();

        Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
        cases.put(1, case12);
        cases.put(2, case12);  // Both go to same label

        ClassFile cf = mb
            .iload(0)
            .tableswitch(1, 2, cases, defaultCase)
            .label(case12)
            .iconst(12)
            .ireturn()
            .label(defaultCase)
            .iconst(0)
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);

        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
    }

    @Test
    void testNestedSwitch() throws IOException {
        // Outer switch with inner switch
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/NestedSwitchTest")
            .publicStaticMethod("test", "(II)I");

        // Outer switch labels
        BytecodeBuilder.Label outerCase1 = mb.newLabel();
        BytecodeBuilder.Label outerCase2 = mb.newLabel();
        BytecodeBuilder.Label outerDefault = mb.newLabel();

        // Inner switch labels (for case 1)
        BytecodeBuilder.Label innerCase10 = mb.newLabel();
        BytecodeBuilder.Label innerCase20 = mb.newLabel();
        BytecodeBuilder.Label innerDefault = mb.newLabel();

        Map<Integer, BytecodeBuilder.Label> outerCases = new HashMap<>();
        outerCases.put(1, outerCase1);
        outerCases.put(2, outerCase2);

        Map<Integer, BytecodeBuilder.Label> innerCases = new HashMap<>();
        innerCases.put(10, innerCase10);
        innerCases.put(20, innerCase20);

        ClassFile cf = mb
            .iload(0)
            .tableswitch(1, 2, outerCases, outerDefault)
            .label(outerCase1)
            .iload(1)  // Inner switch on second parameter
            .tableswitch(10, 20, innerCases, innerDefault)
            .label(innerCase10)
            .iconst(110)
            .ireturn()
            .label(innerCase20)
            .iconst(120)
            .ireturn()
            .label(innerDefault)
            .iconst(100)
            .ireturn()
            .label(outerCase2)
            .iconst(2)
            .ireturn()
            .label(outerDefault)
            .iconst(0)
            .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method);

        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir);
    }

    // ========== Helper Methods ==========

    @FunctionalInterface
    interface BranchBuilder {
        BytecodeBuilder.MethodBuilder build(BytecodeBuilder.MethodBuilder mb);
    }

    private void testBranch(String branchType, BranchBuilder builder) throws IOException {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/Branch" + branchType)
            .publicStaticMethod("test", "()I");

        ClassFile cf = builder.build(mb).build();
        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method, "Method should exist for " + branchType);

        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir, "Should lift to IR for " + branchType);
    }

    private void testBranchObject(String branchType, BranchBuilder builder) throws IOException {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/Branch" + branchType)
            .publicStaticMethod("test", "(Ljava/lang/Object;)I");

        ClassFile cf = builder.build(mb).build();
        MethodEntry method = findMethod(cf, "test");
        assertNotNull(method, "Method should exist for " + branchType);

        SSA ssa = new SSA(method.getClassFile().getConstPool());
        IRMethod ir = ssa.lift(method);
        assertNotNull(ir, "Should lift to IR for " + branchType);
    }
}
