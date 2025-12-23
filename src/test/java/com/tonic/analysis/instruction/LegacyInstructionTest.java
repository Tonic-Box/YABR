package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.parser.ConstPool;
import com.tonic.utill.Opcode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class LegacyInstructionTest {

    private TestVisitor visitor;

    @BeforeEach
    void setUp() {
        visitor = new TestVisitor();
    }

    @Nested
    class WideIIncInstructionTests {

        @Test
        void constructorSetsCorrectFields() {
            WideIIncInstruction instr = new WideIIncInstruction(0xC4, 10, 300, 500);

            assertEquals(0xC4, instr.getOpcode());
            assertEquals(10, instr.getOffset());
            assertEquals(300, instr.getVarIndex());
            assertEquals(500, instr.getConstValue());
        }

        @Test
        void hasCorrectLength() {
            WideIIncInstruction instr = new WideIIncInstruction(0xC4, 0, 0, 0);

            assertEquals(6, instr.getLength());
        }

        @Test
        void stackChangeIsZero() {
            WideIIncInstruction instr = new WideIIncInstruction(0xC4, 0, 100, 50);

            assertEquals(0, instr.getStackChange());
        }

        @Test
        void localChangeIsZero() {
            WideIIncInstruction instr = new WideIIncInstruction(0xC4, 0, 100, 50);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void writesCorrectBytecode() throws IOException {
            WideIIncInstruction instr = new WideIIncInstruction(0xC4, 0, 258, 515);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(6, bytes.length);
            assertEquals((byte) 0xC4, bytes[0]);
            assertEquals((byte) 0x84, bytes[1]);
            assertEquals((byte) 0x01, bytes[2]);
            assertEquals((byte) 0x02, bytes[3]);
            assertEquals((byte) 0x02, bytes[4]);
            assertEquals((byte) 0x03, bytes[5]);
        }

        @Test
        void toStringContainsAllFields() {
            WideIIncInstruction instr = new WideIIncInstruction(0xC4, 42, 256, 128);

            String str = instr.toString();
            assertTrue(str.contains("WideIIncInstruction"));
            assertTrue(str.contains("varIndex=256"));
            assertTrue(str.contains("constValue=128"));
            assertTrue(str.contains("offset=42"));
        }

        @Test
        void acceptsVisitor() {
            WideIIncInstruction instr = new WideIIncInstruction(0xC4, 0, 0, 0);

            instr.accept(visitor);

            assertTrue(visitor.visitedWideIInc);
        }

        @Test
        void handlesNegativeConstValue() {
            WideIIncInstruction instr = new WideIIncInstruction(0xC4, 0, 10, -500);

            assertEquals(-500, instr.getConstValue());
        }

        @Test
        void handlesMaxVarIndex() {
            WideIIncInstruction instr = new WideIIncInstruction(0xC4, 0, 65535, 100);

            assertEquals(65535, instr.getVarIndex());
        }
    }

    @Nested
    class WideInstructionTests {

        @Test
        void constructorForVariableInstructions() {
            WideInstruction instr = new WideInstruction(0xC4, 20, Opcode.ILOAD, 300);

            assertEquals(0xC4, instr.getOpcode());
            assertEquals(20, instr.getOffset());
            assertEquals(Opcode.ILOAD, instr.getModifiedOpcode());
            assertEquals(300, instr.getVarIndex());
            assertEquals(0, instr.getConstValue());
        }

        @Test
        void constructorForIIncInstruction() {
            WideInstruction instr = new WideInstruction(0xC4, 30, Opcode.IINC, 400, 200);

            assertEquals(0xC4, instr.getOpcode());
            assertEquals(30, instr.getOffset());
            assertEquals(Opcode.IINC, instr.getModifiedOpcode());
            assertEquals(400, instr.getVarIndex());
            assertEquals(200, instr.getConstValue());
        }

        @Test
        void variableInstructionHasLength4() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.ALOAD, 100);

            assertEquals(4, instr.getLength());
        }

        @Test
        void iincInstructionHasLength6() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.IINC, 100, 50);

            assertEquals(6, instr.getLength());
        }

        @Test
        void iloadStackChangeIsOne() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.ILOAD, 100);

            assertEquals(1, instr.getStackChange());
        }

        @Test
        void floadStackChangeIsOne() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.FLOAD, 100);

            assertEquals(1, instr.getStackChange());
        }

        @Test
        void aloadStackChangeIsOne() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.ALOAD, 100);

            assertEquals(1, instr.getStackChange());
        }

        @Test
        void istoreStackChangeIsMinusOne() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.ISTORE, 100);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void fstoreStackChangeIsMinusOne() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.FSTORE, 100);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void astoreStackChangeIsMinusOne() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.ASTORE, 100);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void iincStackChangeIsZero() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.IINC, 100, 50);

            assertEquals(0, instr.getStackChange());
        }

        @Test
        void defaultStackChangeIsZero() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.NOP, 100);

            assertEquals(0, instr.getStackChange());
        }

        @Test
        void localChangeIsZero() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.ILOAD, 100);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void writesCorrectBytecodeForLoad() throws IOException {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.ILOAD, 258);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals((byte) 0xC4, bytes[0]);
            assertEquals((byte) 0x15, bytes[1]);
            assertEquals((byte) 0x01, bytes[2]);
            assertEquals((byte) 0x02, bytes[3]);
        }

        @Test
        void writesCorrectBytecodeForIInc() throws IOException {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.IINC, 258, 515);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(6, bytes.length);
            assertEquals((byte) 0xC4, bytes[0]);
            assertEquals((byte) 0x84, bytes[1]);
            assertEquals((byte) 0x01, bytes[2]);
            assertEquals((byte) 0x02, bytes[3]);
            assertEquals((byte) 0x02, bytes[4]);
            assertEquals((byte) 0x03, bytes[5]);
        }

        @Test
        void toStringFormatForLoad() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.ALOAD, 300);

            String str = instr.toString();
            assertEquals("WIDE ALOAD 300", str);
        }

        @Test
        void toStringFormatForIInc() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.IINC, 300, 150);

            String str = instr.toString();
            assertEquals("WIDE IINC 300 150", str);
        }

        @Test
        void acceptsVisitor() {
            WideInstruction instr = new WideInstruction(0xC4, 0, Opcode.ILOAD, 100);

            instr.accept(visitor);

            assertTrue(visitor.visitedWide);
        }
    }

    @Nested
    class JsrInstructionTests {

        @Test
        void constructorSetsCorrectFields() {
            JsrInstruction instr = new JsrInstruction(0xA8, 50, 100);

            assertEquals(0xA8, instr.getOpcode());
            assertEquals(50, instr.getOffset());
            assertEquals(100, instr.getBranchOffset());
        }

        @Test
        void hasCorrectLength() {
            JsrInstruction instr = new JsrInstruction(0xA8, 0, 0);

            assertEquals(3, instr.getLength());
        }

        @Test
        void stackChangeIsOne() {
            JsrInstruction instr = new JsrInstruction(0xA8, 0, 100);

            assertEquals(1, instr.getStackChange());
        }

        @Test
        void localChangeIsZero() {
            JsrInstruction instr = new JsrInstruction(0xA8, 0, 100);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void writesCorrectBytecode() throws IOException {
            JsrInstruction instr = new JsrInstruction(0xA8, 0, 515);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(3, bytes.length);
            assertEquals((byte) 0xA8, bytes[0]);
            assertEquals((byte) 0x02, bytes[1]);
            assertEquals((byte) 0x03, bytes[2]);
        }

        @Test
        void toStringContainsBranchOffset() {
            JsrInstruction instr = new JsrInstruction(0xA8, 0, 200);

            String str = instr.toString();
            assertEquals("JSR 200", str);
        }

        @Test
        void acceptsVisitor() {
            JsrInstruction instr = new JsrInstruction(0xA8, 0, 100);

            instr.accept(visitor);

            assertTrue(visitor.visitedJsr);
        }

        @Test
        void throwsExceptionForInvalidOpcode() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new JsrInstruction(0xA7, 0, 100);
            });

            assertTrue(exception.getMessage().contains("Invalid opcode for JsrInstruction"));
        }

        @Test
        void handlesNegativeBranchOffset() {
            JsrInstruction instr = new JsrInstruction(0xA8, 0, -100);

            assertEquals(-100, instr.getBranchOffset());
        }

        @Test
        void handlesZeroBranchOffset() {
            JsrInstruction instr = new JsrInstruction(0xA8, 0, 0);

            assertEquals(0, instr.getBranchOffset());
        }
    }

    @Nested
    class RetInstructionTests {

        @Test
        void constructorSetsCorrectFields() {
            RetInstruction instr = new RetInstruction(0xA9, 25, 10);

            assertEquals(0xA9, instr.getOpcode());
            assertEquals(25, instr.getOffset());
            assertEquals(10, instr.getVarIndex());
        }

        @Test
        void hasCorrectLength() {
            RetInstruction instr = new RetInstruction(0xA9, 0, 0);

            assertEquals(2, instr.getLength());
        }

        @Test
        void stackChangeIsZero() {
            RetInstruction instr = new RetInstruction(0xA9, 0, 5);

            assertEquals(0, instr.getStackChange());
        }

        @Test
        void localChangeIsZero() {
            RetInstruction instr = new RetInstruction(0xA9, 0, 5);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void writesCorrectBytecode() throws IOException {
            RetInstruction instr = new RetInstruction(0xA9, 0, 42);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(2, bytes.length);
            assertEquals((byte) 0xA9, bytes[0]);
            assertEquals((byte) 42, bytes[1]);
        }

        @Test
        void toStringContainsVarIndex() {
            RetInstruction instr = new RetInstruction(0xA9, 0, 15);

            String str = instr.toString();
            assertEquals("RET 15", str);
        }

        @Test
        void acceptsVisitor() {
            RetInstruction instr = new RetInstruction(0xA9, 0, 5);

            instr.accept(visitor);

            assertTrue(visitor.visitedRet);
        }

        @Test
        void throwsExceptionForInvalidOpcode() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new RetInstruction(0xA8, 0, 5);
            });

            assertTrue(exception.getMessage().contains("Invalid opcode for RetInstruction"));
        }

        @Test
        void handlesZeroVarIndex() {
            RetInstruction instr = new RetInstruction(0xA9, 0, 0);

            assertEquals(0, instr.getVarIndex());
        }

        @Test
        void handlesMaxByteVarIndex() {
            RetInstruction instr = new RetInstruction(0xA9, 0, 255);

            assertEquals(255, instr.getVarIndex());
        }
    }

    @Nested
    class UnknownInstructionTests {

        @Test
        void constructorSetsCorrectFields() {
            UnknownInstruction instr = new UnknownInstruction(0xFF, 100, 3);

            assertEquals(0xFF, instr.getOpcode());
            assertEquals(100, instr.getOffset());
            assertEquals(3, instr.getLength());
        }

        @Test
        void lengthOneCreatesZeroSizeOperandArray() {
            UnknownInstruction instr = new UnknownInstruction(0xFE, 0, 1);

            assertEquals(1, instr.getLength());
        }

        @Test
        void lengthThreeCreatesTwoByteOperandArray() {
            UnknownInstruction instr = new UnknownInstruction(0xFD, 0, 3);

            assertEquals(3, instr.getLength());
        }

        @Test
        void stackChangeIsZero() {
            UnknownInstruction instr = new UnknownInstruction(0xFF, 0, 5);

            assertEquals(0, instr.getStackChange());
        }

        @Test
        void localChangeIsZero() {
            UnknownInstruction instr = new UnknownInstruction(0xFF, 0, 5);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void writesCorrectBytecodeForLengthOne() throws IOException {
            UnknownInstruction instr = new UnknownInstruction(0xFE, 0, 1);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(1, bytes.length);
            assertEquals((byte) 0xFE, bytes[0]);
        }

        @Test
        void writesCorrectBytecodeForLongerInstruction() throws IOException {
            UnknownInstruction instr = new UnknownInstruction(0xFD, 0, 4);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals((byte) 0xFD, bytes[0]);
            assertEquals((byte) 0x00, bytes[1]);
            assertEquals((byte) 0x00, bytes[2]);
            assertEquals((byte) 0x00, bytes[3]);
        }

        @Test
        void toStringContainsOpcodeInHex() {
            UnknownInstruction instr = new UnknownInstruction(0xAB, 0, 3);

            String str = instr.toString();
            assertEquals("UNKNOWN_OPCODE_0xAB", str);
        }

        @Test
        void toStringFormatsOpcodeWithTwoDigits() {
            UnknownInstruction instr = new UnknownInstruction(0x05, 0, 1);

            String str = instr.toString();
            assertEquals("UNKNOWN_OPCODE_0x05", str);
        }

        @Test
        void acceptsVisitor() {
            UnknownInstruction instr = new UnknownInstruction(0xFF, 0, 1);

            instr.accept(visitor);

            assertTrue(visitor.visitedUnknown);
        }

        @Test
        void handlesLargeLength() {
            UnknownInstruction instr = new UnknownInstruction(0xFC, 0, 10);

            assertEquals(10, instr.getLength());
        }
    }

    private static class TestVisitor extends AbstractBytecodeVisitor {
        boolean visitedWideIInc = false;
        boolean visitedWide = false;
        boolean visitedJsr = false;
        boolean visitedRet = false;
        boolean visitedUnknown = false;

        @Override
        public void visit(WideIIncInstruction instr) {
            visitedWideIInc = true;
        }

        @Override
        public void visit(WideInstruction instr) {
            visitedWide = true;
        }

        @Override
        public void visit(JsrInstruction instr) {
            visitedJsr = true;
        }

        @Override
        public void visit(RetInstruction instr) {
            visitedRet = true;
        }

        @Override
        public void visit(UnknownInstruction instr) {
            visitedUnknown = true;
        }
    }
}
