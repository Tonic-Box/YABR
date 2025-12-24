package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ArithmeticInstructionTest {

    private TestVisitor visitor;

    @BeforeEach
    void setUp() {
        visitor = new TestVisitor();
    }

    @Nested
    class AddInstructionTests {

        @Test
        void iaddHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x60, 0);

            assertEquals(0x60, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.IADD, instr.getType());
        }

        @Test
        void laddHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x61, 0);

            assertEquals(0x61, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.LADD, instr.getType());
        }

        @Test
        void faddHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x62, 0);

            assertEquals(0x62, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.FADD, instr.getType());
        }

        @Test
        void daddHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x63, 0);

            assertEquals(0x63, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.DADD, instr.getType());
        }

        @Test
        void iaddStackChangeIsMinusOne() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x60, 0);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void laddStackChangeIsMinusTwo() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x61, 0);

            assertEquals(-2, instr.getStackChange());
        }

        @Test
        void faddStackChangeIsMinusOne() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x62, 0);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void daddStackChangeIsMinusTwo() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x63, 0);

            assertEquals(-2, instr.getStackChange());
        }
    }

    @Nested
    class SubInstructionTests {

        @Test
        void isubHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x64, 0);

            assertEquals(0x64, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.ISUB, instr.getType());
        }

        @Test
        void lsubHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x65, 0);

            assertEquals(0x65, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.LSUB, instr.getType());
        }

        @Test
        void fsubHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x66, 0);

            assertEquals(0x66, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.FSUB, instr.getType());
        }

        @Test
        void dsubHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x67, 0);

            assertEquals(0x67, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.DSUB, instr.getType());
        }

        @Test
        void isubStackChangeIsMinusOne() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x64, 0);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void lsubStackChangeIsMinusTwo() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x65, 0);

            assertEquals(-2, instr.getStackChange());
        }
    }

    @Nested
    class MulInstructionTests {

        @Test
        void imulHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x68, 0);

            assertEquals(0x68, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.IMUL, instr.getType());
        }

        @Test
        void lmulHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x69, 0);

            assertEquals(0x69, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.LMUL, instr.getType());
        }

        @Test
        void fmulHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x6A, 0);

            assertEquals(0x6A, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.FMUL, instr.getType());
        }

        @Test
        void dmulHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x6B, 0);

            assertEquals(0x6B, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.DMUL, instr.getType());
        }

        @Test
        void imulStackChangeIsMinusOne() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x68, 0);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void lmulStackChangeIsMinusTwo() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x69, 0);

            assertEquals(-2, instr.getStackChange());
        }
    }

    @Nested
    class DivInstructionTests {

        @Test
        void idivHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x6C, 0);

            assertEquals(0x6C, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.IDIV, instr.getType());
        }

        @Test
        void ldivHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x6D, 0);

            assertEquals(0x6D, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.LDIV, instr.getType());
        }

        @Test
        void fdivHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x6E, 0);

            assertEquals(0x6E, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.FDIV, instr.getType());
        }

        @Test
        void ddivHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x6F, 0);

            assertEquals(0x6F, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.DDIV, instr.getType());
        }

        @Test
        void idivStackChangeIsMinusOne() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x6C, 0);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void ldivStackChangeIsMinusTwo() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x6D, 0);

            assertEquals(-2, instr.getStackChange());
        }
    }

    @Nested
    class RemInstructionTests {

        @Test
        void iremHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x70, 0);

            assertEquals(0x70, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.IREM, instr.getType());
        }

        @Test
        void lremHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x71, 0);

            assertEquals(0x71, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.LREM, instr.getType());
        }

        @Test
        void fremHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x72, 0);

            assertEquals(0x72, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.FREM, instr.getType());
        }

        @Test
        void dremHasCorrectOpcode() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x73, 0);

            assertEquals(0x73, instr.getOpcode());
            assertEquals(ArithmeticInstruction.ArithmeticType.DREM, instr.getType());
        }

        @Test
        void iremStackChangeIsMinusOne() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x70, 0);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void lremStackChangeIsMinusTwo() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x71, 0);

            assertEquals(-2, instr.getStackChange());
        }
    }

    @Nested
    class CommonBehaviorTests {

        @Test
        void allInstructionsHaveLengthOne() {
            ArithmeticInstruction iadd = new ArithmeticInstruction(0x60, 0);
            ArithmeticInstruction ladd = new ArithmeticInstruction(0x61, 0);
            ArithmeticInstruction fadd = new ArithmeticInstruction(0x62, 0);
            ArithmeticInstruction dadd = new ArithmeticInstruction(0x63, 0);

            assertEquals(1, iadd.getLength());
            assertEquals(1, ladd.getLength());
            assertEquals(1, fadd.getLength());
            assertEquals(1, dadd.getLength());
        }

        @Test
        void allInstructionsHaveZeroLocalChange() {
            ArithmeticInstruction iadd = new ArithmeticInstruction(0x60, 0);
            ArithmeticInstruction lsub = new ArithmeticInstruction(0x65, 0);
            ArithmeticInstruction fmul = new ArithmeticInstruction(0x6A, 0);
            ArithmeticInstruction ddiv = new ArithmeticInstruction(0x6F, 0);

            assertEquals(0, iadd.getLocalChange());
            assertEquals(0, lsub.getLocalChange());
            assertEquals(0, fmul.getLocalChange());
            assertEquals(0, ddiv.getLocalChange());
        }

        @Test
        void writesCorrectOpcode() throws IOException {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x68, 0);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(1, bytes.length);
            assertEquals((byte) 0x68, bytes[0]);
        }

        @Test
        void toStringReturnsUpperCaseMnemonic() {
            ArithmeticInstruction iadd = new ArithmeticInstruction(0x60, 0);
            ArithmeticInstruction lsub = new ArithmeticInstruction(0x65, 0);
            ArithmeticInstruction fmul = new ArithmeticInstruction(0x6A, 0);
            ArithmeticInstruction ddiv = new ArithmeticInstruction(0x6F, 0);

            assertEquals("IADD", iadd.toString());
            assertEquals("LSUB", lsub.toString());
            assertEquals("FMUL", fmul.toString());
            assertEquals("DDIV", ddiv.toString());
        }

        @Test
        void acceptsVisitor() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x60, 0);

            instr.accept(visitor);

            assertTrue(visitor.visitedArithmetic);
        }

        @Test
        void throwsExceptionForInvalidOpcode() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new ArithmeticInstruction(0x00, 0);
            });

            assertTrue(exception.getMessage().contains("Invalid Arithmetic opcode"));
        }

        @Test
        void constructorSetsCorrectOffset() {
            ArithmeticInstruction instr = new ArithmeticInstruction(0x60, 42);

            assertEquals(42, instr.getOffset());
        }

        @Test
        void arithmeticTypeFromOpcodeReturnsCorrectType() {
            assertEquals(ArithmeticInstruction.ArithmeticType.IADD,
                        ArithmeticInstruction.ArithmeticType.fromOpcode(0x60));
            assertEquals(ArithmeticInstruction.ArithmeticType.DREM,
                        ArithmeticInstruction.ArithmeticType.fromOpcode(0x73));
        }

        @Test
        void arithmeticTypeFromOpcodeReturnsNullForInvalidOpcode() {
            assertNull(ArithmeticInstruction.ArithmeticType.fromOpcode(0xFF));
        }

        @Test
        void arithmeticTypeGettersReturnCorrectValues() {
            ArithmeticInstruction.ArithmeticType type = ArithmeticInstruction.ArithmeticType.IADD;

            assertEquals(0x60, type.getOpcode());
            assertEquals("iadd", type.getMnemonic());
        }
    }

    @Nested
    class NegInstructionTests {

        @Test
        void inegHasCorrectOpcodeAndLength() {
            INegInstruction instr = new INegInstruction(0x74, 0);

            assertEquals(0x74, instr.getOpcode());
            assertEquals(1, instr.getLength());
        }

        @Test
        void lnegHasCorrectOpcodeAndLength() {
            LNegInstruction instr = new LNegInstruction(0x75, 0);

            assertEquals(0x75, instr.getOpcode());
            assertEquals(1, instr.getLength());
        }

        @Test
        void fnegHasCorrectOpcodeAndLength() {
            FNegInstruction instr = new FNegInstruction(0x76, 0);

            assertEquals(0x76, instr.getOpcode());
            assertEquals(1, instr.getLength());
        }

        @Test
        void dnegHasCorrectOpcodeAndLength() {
            DNegInstruction instr = new DNegInstruction(0x77, 0);

            assertEquals(0x77, instr.getOpcode());
            assertEquals(1, instr.getLength());
        }

        @Test
        void allNegInstructionsHaveZeroStackChange() {
            INegInstruction ineg = new INegInstruction(0x74, 0);
            LNegInstruction lneg = new LNegInstruction(0x75, 0);
            FNegInstruction fneg = new FNegInstruction(0x76, 0);
            DNegInstruction dneg = new DNegInstruction(0x77, 0);

            assertEquals(0, ineg.getStackChange());
            assertEquals(0, lneg.getStackChange());
            assertEquals(0, fneg.getStackChange());
            assertEquals(0, dneg.getStackChange());
        }

        @Test
        void allNegInstructionsHaveZeroLocalChange() {
            INegInstruction ineg = new INegInstruction(0x74, 0);
            LNegInstruction lneg = new LNegInstruction(0x75, 0);
            FNegInstruction fneg = new FNegInstruction(0x76, 0);
            DNegInstruction dneg = new DNegInstruction(0x77, 0);

            assertEquals(0, ineg.getLocalChange());
            assertEquals(0, lneg.getLocalChange());
            assertEquals(0, fneg.getLocalChange());
            assertEquals(0, dneg.getLocalChange());
        }

        @Test
        void inegToStringReturnsCorrectMnemonic() {
            INegInstruction instr = new INegInstruction(0x74, 0);

            assertEquals("INEG", instr.toString());
        }

        @Test
        void lnegToStringReturnsCorrectMnemonic() {
            LNegInstruction instr = new LNegInstruction(0x75, 0);

            assertEquals("LNEG", instr.toString());
        }

        @Test
        void fnegToStringReturnsCorrectMnemonic() {
            FNegInstruction instr = new FNegInstruction(0x76, 0);

            assertEquals("FNEG", instr.toString());
        }

        @Test
        void dnegToStringReturnsCorrectMnemonic() {
            DNegInstruction instr = new DNegInstruction(0x77, 0);

            assertEquals("DNEG", instr.toString());
        }

        @Test
        void inegWritesCorrectBytecode() throws IOException {
            INegInstruction instr = new INegInstruction(0x74, 0);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(1, bytes.length);
            assertEquals((byte) 0x74, bytes[0]);
        }

        @Test
        void lnegWritesCorrectBytecode() throws IOException {
            LNegInstruction instr = new LNegInstruction(0x75, 0);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(1, bytes.length);
            assertEquals((byte) 0x75, bytes[0]);
        }

        @Test
        void fnegAcceptsVisitor() {
            FNegInstruction instr = new FNegInstruction(0x76, 0);

            instr.accept(visitor);

            assertTrue(visitor.visitedFNeg);
        }

        @Test
        void dnegAcceptsVisitor() {
            DNegInstruction instr = new DNegInstruction(0x77, 0);

            instr.accept(visitor);

            assertTrue(visitor.visitedDNeg);
        }
    }

    private static class TestVisitor extends AbstractBytecodeVisitor {
        boolean visitedArithmetic = false;
        boolean visitedFNeg = false;
        boolean visitedDNeg = false;

        @Override
        public void visit(ArithmeticInstruction instr) {
            visitedArithmetic = true;
        }

        @Override
        public void visit(FNegInstruction instr) {
            visitedFNeg = true;
        }

        @Override
        public void visit(DNegInstruction instr) {
            visitedDNeg = true;
        }
    }
}
