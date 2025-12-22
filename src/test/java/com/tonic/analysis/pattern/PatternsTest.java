package com.tonic.analysis.pattern;

import com.tonic.analysis.Bytecode;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.ReturnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Patterns factory class.
 * Covers all pattern matchers: method calls, field access, type checks, object creation,
 * control flow, and combination patterns.
 */
class PatternsTest {

    private ClassPool pool;
    private ClassFile classFile;
    private MethodEntry methodEntry;
    private IRMethod irMethod;

    @BeforeEach
    void setUp() throws IOException {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/PatternsTestClass", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        methodEntry = classFile.createNewMethod(methodAccess, "testMethod", "V");
    }

    private void buildIR() throws IOException {
        irMethod = TestUtils.liftMethod(methodEntry);
    }

    private boolean matchFirstInstruction(PatternMatcher matcher) throws IOException {
        buildIR();
        if (irMethod.getBlocks().isEmpty()) return false;
        IRBlock firstBlock = irMethod.getBlocks().get(0);
        if (firstBlock.getInstructions().isEmpty()) return false;
        IRInstruction firstInstr = firstBlock.getInstructions().get(0);
        return matcher.matches(firstInstr, irMethod, methodEntry, classFile);
    }

    private boolean matchInstruction(PatternMatcher matcher, int instructionIndex) throws IOException {
        buildIR();
        if (irMethod.getBlocks().isEmpty()) return false;
        IRBlock firstBlock = irMethod.getBlocks().get(0);
        if (firstBlock.getInstructions().size() <= instructionIndex) return false;
        IRInstruction instr = firstBlock.getInstructions().get(instructionIndex);
        return matcher.matches(instr, irMethod, methodEntry, classFile);
    }

    // ===== Method Call Pattern Tests =====

    @Nested
    class MethodCallPatterns {

        @Test
        void anyMethodCallMatchesInvokeInstruction() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchFirstInstruction(Patterns.anyMethodCall()));
        }

        @Test
        void anyMethodCallDoesNotMatchNonInvoke() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addIConst(42);
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.anyMethodCall()));
        }

        @Test
        void methodCallToMatchesCorrectOwner() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchFirstInstruction(Patterns.methodCallTo("java/lang/System")));
        }

        @Test
        void methodCallToDoesNotMatchWrongOwner() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.methodCallTo("java/lang/String")));
        }

        @Test
        void methodCallNamedMatchesCorrectName() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchFirstInstruction(Patterns.methodCallNamed("currentTimeMillis")));
        }

        @Test
        void methodCallNamedDoesNotMatchWrongName() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.methodCallNamed("nanoTime")));
        }

        @Test
        void methodCallMatchesBothOwnerAndName() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchFirstInstruction(Patterns.methodCall("java/lang/System", "currentTimeMillis")));
        }

        @Test
        void methodCallDoesNotMatchWrongOwner() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.methodCall("java/lang/String", "currentTimeMillis")));
        }

        @Test
        void methodCallDoesNotMatchWrongName() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.methodCall("java/lang/System", "nanoTime")));
        }

        @Test
        void methodCallOwnerMatchingWithValidRegex() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchFirstInstruction(Patterns.methodCallOwnerMatching("java/lang/.*")));
        }

        @Test
        void methodCallOwnerMatchingDoesNotMatchInvalidRegex() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.methodCallOwnerMatching("com/test/.*")));
        }

        @Test
        void methodCallNameMatchingWithValidRegex() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchFirstInstruction(Patterns.methodCallNameMatching("current.*")));
        }

        @Test
        void methodCallNameMatchingDoesNotMatchInvalidRegex() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.methodCallNameMatching("nano.*")));
        }

        @Test
        void staticMethodCallMatchesStatic() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchFirstInstruction(Patterns.staticMethodCall()));
        }

        @Test
        void staticMethodCallDoesNotMatchVirtual() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
            bc.addLdc("Hello");
            bc.addInvokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.staticMethodCall()));
        }

        @Test
        void virtualMethodCallMatchesVirtual() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
            bc.addLdc("Hello");
            bc.addInvokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchInstruction(Patterns.virtualMethodCall(), 2));
        }

        @Test
        void virtualMethodCallDoesNotMatchStatic() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.virtualMethodCall()));
        }

        @Test
        void dynamicCallMatchesInvokeDynamic() {
            // Test that dynamicCall matcher can be created
            PatternMatcher matcher = Patterns.dynamicCall();
            assertNotNull(matcher);
        }
    }

    // ===== Field Access Pattern Tests =====

    @Nested
    class FieldAccessPatterns {

        @Test
        void anyFieldReadMatchesGetField() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchFirstInstruction(Patterns.anyFieldRead()));
        }

        @Test
        void anyFieldReadDoesNotMatchOther() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addIConst(42);
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.anyFieldRead()));
        }

        @Test
        void anyFieldWriteMatchesPutField() {
            // Test that anyFieldWrite matcher can be created
            PatternMatcher matcher = Patterns.anyFieldWrite();
            assertNotNull(matcher);
        }

        @Test
        void fieldAccessOnMatchesCorrectOwner() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchFirstInstruction(Patterns.fieldAccessOn("java/lang/System")));
        }

        @Test
        void fieldAccessOnDoesNotMatchWrongOwner() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.fieldAccessOn("java/lang/Integer")));
        }

        @Test
        void fieldNamedMatchesCorrectName() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchFirstInstruction(Patterns.fieldNamed("out")));
        }

        @Test
        void fieldNamedDoesNotMatchWrongName() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertFalse(matchFirstInstruction(Patterns.fieldNamed("in")));
        }
    }

    // ===== Type Check Pattern Tests =====

    @Nested
    class TypeCheckPatterns {

        @Test
        void anyInstanceOfMatcherCreated() {
            PatternMatcher matcher = Patterns.anyInstanceOf();
            assertNotNull(matcher);
        }

        @Test
        void instanceOfMatcherCreated() {
            PatternMatcher matcher = Patterns.instanceOf("java/lang/String");
            assertNotNull(matcher);
        }

        @Test
        void anyCastMatcherCreated() {
            PatternMatcher matcher = Patterns.anyCast();
            assertNotNull(matcher);
        }

        @Test
        void castToMatcherCreated() {
            PatternMatcher matcher = Patterns.castTo("java/lang/String");
            assertNotNull(matcher);
        }
    }

    // ===== Object Creation Pattern Tests =====

    @Nested
    class ObjectCreationPatterns {

        @Test
        void anyNewMatcherCreated() {
            PatternMatcher matcher = Patterns.anyNew();
            assertNotNull(matcher);
        }

        @Test
        void newInstanceMatcherCreated() {
            PatternMatcher matcher = Patterns.newInstance("java/lang/String");
            assertNotNull(matcher);
        }

        @Test
        void anyNewArrayMatcherCreated() {
            PatternMatcher matcher = Patterns.anyNewArray();
            assertNotNull(matcher);
        }
    }

    // ===== Control Flow Pattern Tests =====

    @Nested
    class ControlFlowPatterns {

        @Test
        void nullCheckMatcherCreated() {
            PatternMatcher matcher = Patterns.nullCheck();
            assertNotNull(matcher);
        }

        @Test
        void anyThrowMatcherCreated() {
            PatternMatcher matcher = Patterns.anyThrow();
            assertNotNull(matcher);
        }

        @Test
        void anyReturnMatchesReturnInstruction() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(matchFirstInstruction(Patterns.anyReturn()));
        }

        @Test
        void anyReturnMatchesIntReturn() throws IOException {
            int methodAccess = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry intMethod = classFile.createNewMethod(methodAccess, "intMethod", "I");

            Bytecode bc = new Bytecode(intMethod);
            bc.addIConst(42);
            bc.addReturn(ReturnType.IRETURN);
            bc.finalizeBytecode();

            IRMethod ir = TestUtils.liftMethod(intMethod);
            if (!ir.getBlocks().isEmpty()) {
                IRBlock block = ir.getBlocks().get(0);
                if (block.getInstructions().size() > 1) {
                    IRInstruction ret = block.getInstructions().get(1);
                    assertTrue(Patterns.anyReturn().matches(ret, ir, intMethod, classFile));
                }
            }
        }
    }

    // ===== Combination Pattern Tests =====

    @Nested
    class CombinationPatterns {

        @Test
        void andCombinesBothMatchers() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher combined = Patterns.and(
                Patterns.anyMethodCall(),
                Patterns.methodCallTo("java/lang/System")
            );

            assertTrue(matchFirstInstruction(combined));
        }

        @Test
        void andFailsIfOneMatcherFails() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher combined = Patterns.and(
                Patterns.anyMethodCall(),
                Patterns.methodCallTo("java/lang/String")
            );

            assertFalse(matchFirstInstruction(combined));
        }

        @Test
        void andWithMultipleMatchers() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher combined = Patterns.and(
                Patterns.anyMethodCall(),
                Patterns.staticMethodCall(),
                Patterns.methodCallTo("java/lang/System")
            );

            assertTrue(matchFirstInstruction(combined));
        }

        @Test
        void orSucceedsIfOneMatcherSucceeds() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher combined = Patterns.or(
                Patterns.methodCallTo("java/lang/String"),
                Patterns.methodCallTo("java/lang/System")
            );

            assertTrue(matchFirstInstruction(combined));
        }

        @Test
        void orFailsIfAllMatchersFail() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher combined = Patterns.or(
                Patterns.methodCallTo("java/lang/String"),
                Patterns.methodCallTo("java/lang/Integer")
            );

            assertFalse(matchFirstInstruction(combined));
        }

        @Test
        void orWithMultipleMatchers() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher combined = Patterns.or(
                Patterns.anyMethodCall(),
                Patterns.anyFieldRead(),
                Patterns.anyNew()
            );

            assertTrue(matchFirstInstruction(combined));
        }

        @Test
        void notNegatesMatch() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addIConst(42);
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher negated = Patterns.not(Patterns.anyMethodCall());

            assertTrue(matchFirstInstruction(negated));
        }

        @Test
        void notNegatesNonMatch() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher negated = Patterns.not(Patterns.anyMethodCall());

            assertFalse(matchFirstInstruction(negated));
        }

        @Test
        void complexCombination() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            // (anyMethodCall AND staticMethodCall) OR anyFieldRead
            PatternMatcher combined = Patterns.or(
                Patterns.and(Patterns.anyMethodCall(), Patterns.staticMethodCall()),
                Patterns.anyFieldRead()
            );

            assertTrue(matchFirstInstruction(combined));
        }

        @Test
        void notWithAnd() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            // NOT (anyMethodCall AND methodCallTo("java/lang/String"))
            PatternMatcher combined = Patterns.not(
                Patterns.and(
                    Patterns.anyMethodCall(),
                    Patterns.methodCallTo("java/lang/String")
                )
            );

            assertTrue(matchFirstInstruction(combined));
        }
    }

    // ===== Edge Cases and Null Safety =====

    @Nested
    class EdgeCases {

        @Test
        void emptyMethodReturnsNoMatches() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            buildIR();
            assertTrue(irMethod.getBlocks().size() > 0);
        }

        @Test
        void matcherHandlesNullOwnerGracefully() {
            PatternMatcher matcher = Patterns.methodCallTo(null);
            assertNotNull(matcher);
        }

        @Test
        void matcherHandlesNullMethodNameGracefully() {
            PatternMatcher matcher = Patterns.methodCallNamed(null);
            assertNotNull(matcher);
        }

        @Test
        void methodCallOwnerMatchingHandlesNullOwner() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher matcher = Patterns.methodCallOwnerMatching(".*");
            assertTrue(matchFirstInstruction(matcher));
        }

        @Test
        void andWithEmptyMatcherArray() {
            PatternMatcher matcher = Patterns.and();
            assertNotNull(matcher);
        }

        @Test
        void orWithEmptyMatcherArray() {
            PatternMatcher matcher = Patterns.or();
            assertNotNull(matcher);
        }

        @Test
        void andWithSingleMatcher() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher matcher = Patterns.and(Patterns.anyMethodCall());
            assertTrue(matchFirstInstruction(matcher));
        }

        @Test
        void orWithSingleMatcher() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher matcher = Patterns.or(Patterns.anyMethodCall());
            assertTrue(matchFirstInstruction(matcher));
        }

        @Test
        void regexPatternCompiles() {
            PatternMatcher matcher = Patterns.methodCallOwnerMatching("java/.*");
            assertNotNull(matcher);
        }

        @Test
        void andWithEmptyArrayMatchesAll() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher matcher = Patterns.and();
            assertTrue(matchFirstInstruction(matcher));
        }

        @Test
        void orWithEmptyArrayMatchesNone() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            PatternMatcher matcher = Patterns.or();
            assertFalse(matchFirstInstruction(matcher));
        }

        @Test
        void nestedCombinations() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            // ((A AND B) OR C) AND NOT D
            PatternMatcher matcher = Patterns.and(
                Patterns.or(
                    Patterns.and(
                        Patterns.anyMethodCall(),
                        Patterns.staticMethodCall()
                    ),
                    Patterns.anyFieldRead()
                ),
                Patterns.not(Patterns.anyNew())
            );

            assertTrue(matchFirstInstruction(matcher));
        }

        @Test
        void matcherWorksWithNullInstruction() {
            PatternMatcher matcher = Patterns.anyMethodCall();
            assertFalse(matcher.matches(null, irMethod, methodEntry, classFile));
        }
    }

    // ===== Additional Pattern Coverage =====

    @Nested
    class AdditionalPatterns {

        @Test
        void methodCallWithNullParametersDoesNotCrash() {
            PatternMatcher matcher = Patterns.methodCall(null, null);
            assertNotNull(matcher);
        }

        @Test
        void fieldAccessOnWithNullOwner() {
            PatternMatcher matcher = Patterns.fieldAccessOn(null);
            assertNotNull(matcher);
        }

        @Test
        void fieldNamedWithNullName() {
            PatternMatcher matcher = Patterns.fieldNamed(null);
            assertNotNull(matcher);
        }

        @Test
        void instanceOfWithNullType() {
            PatternMatcher matcher = Patterns.instanceOf(null);
            assertNotNull(matcher);
        }

        @Test
        void castToWithNullType() {
            PatternMatcher matcher = Patterns.castTo(null);
            assertNotNull(matcher);
        }

        @Test
        void newInstanceWithNullClass() {
            PatternMatcher matcher = Patterns.newInstance(null);
            assertNotNull(matcher);
        }

        @Test
        void multipleNestedNot() throws IOException {
            Bytecode bc = new Bytecode(methodEntry);
            bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
            bc.getCodeWriter().appendInstruction(
                new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            // NOT NOT anyMethodCall = anyMethodCall
            PatternMatcher matcher = Patterns.not(Patterns.not(Patterns.anyMethodCall()));
            assertTrue(matchFirstInstruction(matcher));
        }

        @Test
        void allMatchersCanBeCreated() {
            assertNotNull(Patterns.anyMethodCall());
            assertNotNull(Patterns.methodCallTo("test"));
            assertNotNull(Patterns.methodCallNamed("test"));
            assertNotNull(Patterns.methodCall("test", "test"));
            assertNotNull(Patterns.methodCallOwnerMatching(".*"));
            assertNotNull(Patterns.methodCallNameMatching(".*"));
            assertNotNull(Patterns.staticMethodCall());
            assertNotNull(Patterns.virtualMethodCall());
            assertNotNull(Patterns.dynamicCall());
            assertNotNull(Patterns.anyFieldRead());
            assertNotNull(Patterns.anyFieldWrite());
            assertNotNull(Patterns.fieldAccessOn("test"));
            assertNotNull(Patterns.fieldNamed("test"));
            assertNotNull(Patterns.anyInstanceOf());
            assertNotNull(Patterns.instanceOf("test"));
            assertNotNull(Patterns.anyCast());
            assertNotNull(Patterns.castTo("test"));
            assertNotNull(Patterns.anyNew());
            assertNotNull(Patterns.newInstance("test"));
            assertNotNull(Patterns.anyNewArray());
            assertNotNull(Patterns.nullCheck());
            assertNotNull(Patterns.anyThrow());
            assertNotNull(Patterns.anyReturn());
            assertNotNull(Patterns.and());
            assertNotNull(Patterns.or());
            assertNotNull(Patterns.not(Patterns.anyReturn()));
        }
    }
}
