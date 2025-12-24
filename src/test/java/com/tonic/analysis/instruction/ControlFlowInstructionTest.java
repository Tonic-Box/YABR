package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.utill.ReturnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ControlFlowInstructionTest {

    private TestVisitor visitor;

    @BeforeEach
    void setUp() {
        visitor = new TestVisitor();
    }

    @Nested
    class ConditionalBranchInstructionTests {

        @Nested
        class SingleValueComparisonTests {

            @Test
            void ifeqHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x99, 10, (short) 5);

                assertEquals(0x99, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IFEQ, instr.getType());
            }

            @Test
            void ifneHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9A, 10, (short) 5);

                assertEquals(0x9A, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IFNE, instr.getType());
            }

            @Test
            void ifltHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9B, 10, (short) 5);

                assertEquals(0x9B, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IFLT, instr.getType());
            }

            @Test
            void ifgeHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9C, 10, (short) 5);

                assertEquals(0x9C, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IFGE, instr.getType());
            }

            @Test
            void ifgtHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9D, 10, (short) 5);

                assertEquals(0x9D, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IFGT, instr.getType());
            }

            @Test
            void ifleHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9E, 10, (short) 5);

                assertEquals(0x9E, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IFLE, instr.getType());
            }

            @Test
            void singleValueComparisonsPopsOneValue() {
                ConditionalBranchInstruction ifeq = new ConditionalBranchInstruction(0x99, 10, (short) 5);
                ConditionalBranchInstruction ifne = new ConditionalBranchInstruction(0x9A, 10, (short) 5);
                ConditionalBranchInstruction iflt = new ConditionalBranchInstruction(0x9B, 10, (short) 5);
                ConditionalBranchInstruction ifge = new ConditionalBranchInstruction(0x9C, 10, (short) 5);
                ConditionalBranchInstruction ifgt = new ConditionalBranchInstruction(0x9D, 10, (short) 5);
                ConditionalBranchInstruction ifle = new ConditionalBranchInstruction(0x9E, 10, (short) 5);

                assertEquals(-1, ifeq.getStackChange());
                assertEquals(-1, ifne.getStackChange());
                assertEquals(-1, iflt.getStackChange());
                assertEquals(-1, ifge.getStackChange());
                assertEquals(-1, ifgt.getStackChange());
                assertEquals(-1, ifle.getStackChange());
            }
        }

        @Nested
        class IntegerComparisonTests {

            @Test
            void ifIcmpeqHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9F, 10, (short) 5);

                assertEquals(0x9F, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IF_ICMPEQ, instr.getType());
            }

            @Test
            void ifIcmpneHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA0, 10, (short) 5);

                assertEquals(0xA0, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IF_ICMPNE, instr.getType());
            }

            @Test
            void ifIcmpltHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA1, 10, (short) 5);

                assertEquals(0xA1, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IF_ICMPLT, instr.getType());
            }

            @Test
            void ifIcmpgeHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA2, 10, (short) 5);

                assertEquals(0xA2, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IF_ICMPGE, instr.getType());
            }

            @Test
            void ifIcmpgtHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA3, 10, (short) 5);

                assertEquals(0xA3, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IF_ICMPGT, instr.getType());
            }

            @Test
            void ifIcmpleHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA4, 10, (short) 5);

                assertEquals(0xA4, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IF_ICMPLE, instr.getType());
            }

            @Test
            void integerComparisonsPropsTwoValues() {
                ConditionalBranchInstruction ifIcmpeq = new ConditionalBranchInstruction(0x9F, 10, (short) 5);
                ConditionalBranchInstruction ifIcmpne = new ConditionalBranchInstruction(0xA0, 10, (short) 5);
                ConditionalBranchInstruction ifIcmplt = new ConditionalBranchInstruction(0xA1, 10, (short) 5);

                assertEquals(-2, ifIcmpeq.getStackChange());
                assertEquals(-2, ifIcmpne.getStackChange());
                assertEquals(-2, ifIcmplt.getStackChange());
            }
        }

        @Nested
        class ReferenceComparisonTests {

            @Test
            void ifAcmpeqHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA5, 10, (short) 5);

                assertEquals(0xA5, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IF_ACMPEQ, instr.getType());
            }

            @Test
            void ifAcmpneHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA6, 10, (short) 5);

                assertEquals(0xA6, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IF_ACMPNE, instr.getType());
            }

            @Test
            void referenceComparisonsPropsTwoValues() {
                ConditionalBranchInstruction ifAcmpeq = new ConditionalBranchInstruction(0xA5, 10, (short) 5);
                ConditionalBranchInstruction ifAcmpne = new ConditionalBranchInstruction(0xA6, 10, (short) 5);

                assertEquals(-2, ifAcmpeq.getStackChange());
                assertEquals(-2, ifAcmpne.getStackChange());
            }
        }

        @Nested
        class NullCheckTests {

            @Test
            void ifnullHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xC6, 10, (short) 5);

                assertEquals(0xC6, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IFNULL, instr.getType());
            }

            @Test
            void ifnonnullHasCorrectOpcodeAndType() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xC7, 10, (short) 5);

                assertEquals(0xC7, instr.getOpcode());
                assertEquals(ConditionalBranchInstruction.BranchType.IFNONNULL, instr.getType());
            }

            @Test
            void nullChecksPopsOneValue() {
                ConditionalBranchInstruction ifnull = new ConditionalBranchInstruction(0xC6, 10, (short) 5);
                ConditionalBranchInstruction ifnonnull = new ConditionalBranchInstruction(0xC7, 10, (short) 5);

                assertEquals(-1, ifnull.getStackChange());
                assertEquals(-1, ifnonnull.getStackChange());
            }
        }

        @Nested
        class BranchOffsetTests {

            @Test
            void branchOffsetStoredCorrectly() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x99, 10, (short) 42);

                assertEquals(42, instr.getBranchOffset());
            }

            @Test
            void negativeBranchOffsetStoredCorrectly() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x99, 10, (short) -15);

                assertEquals(-15, instr.getBranchOffset());
            }

            @Test
            void zeroBranchOffsetStoredCorrectly() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x99, 10, (short) 0);

                assertEquals(0, instr.getBranchOffset());
            }
        }

        @Nested
        class CommonBehaviorTests {

            @Test
            void allBranchesHaveLengthThree() {
                ConditionalBranchInstruction ifeq = new ConditionalBranchInstruction(0x99, 10, (short) 5);
                ConditionalBranchInstruction ifIcmpeq = new ConditionalBranchInstruction(0x9F, 10, (short) 5);
                ConditionalBranchInstruction ifAcmpeq = new ConditionalBranchInstruction(0xA5, 10, (short) 5);
                ConditionalBranchInstruction ifnull = new ConditionalBranchInstruction(0xC6, 10, (short) 5);

                assertEquals(3, ifeq.getLength());
                assertEquals(3, ifIcmpeq.getLength());
                assertEquals(3, ifAcmpeq.getLength());
                assertEquals(3, ifnull.getLength());
            }

            @Test
            void allBranchesHaveZeroLocalChange() {
                ConditionalBranchInstruction ifeq = new ConditionalBranchInstruction(0x99, 10, (short) 5);
                ConditionalBranchInstruction ifIcmpeq = new ConditionalBranchInstruction(0x9F, 10, (short) 5);

                assertEquals(0, ifeq.getLocalChange());
                assertEquals(0, ifIcmpeq.getLocalChange());
            }

            @Test
            void writesCorrectBytecode() throws IOException {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x99, 10, (short) 0x0123);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                instr.write(dos);
                dos.flush();

                byte[] bytes = baos.toByteArray();
                assertEquals(3, bytes.length);
                assertEquals((byte) 0x99, bytes[0]);
                assertEquals((byte) 0x01, bytes[1]);
                assertEquals((byte) 0x23, bytes[2]);
            }

            @Test
            void toStringReturnsCorrectFormat() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x99, 10, (short) 42);

                String result = instr.toString();

                assertTrue(result.contains("IFEQ"));
                assertTrue(result.contains("42"));
            }

            @Test
            void acceptsVisitor() {
                ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x99, 10, (short) 5);

                instr.accept(visitor);

                assertTrue(visitor.visitedConditionalBranch);
            }

            @Test
            void throwsExceptionForInvalidOpcode() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                    new ConditionalBranchInstruction(0x00, 10, (short) 5);
                });

                assertTrue(exception.getMessage().contains("Invalid Conditional Branch opcode"));
            }

            @Test
            void branchTypeFromOpcodeReturnsCorrectType() {
                assertEquals(ConditionalBranchInstruction.BranchType.IFEQ,
                        ConditionalBranchInstruction.BranchType.fromOpcode(0x99));
                assertEquals(ConditionalBranchInstruction.BranchType.IFNONNULL,
                        ConditionalBranchInstruction.BranchType.fromOpcode(0xC7));
            }

            @Test
            void branchTypeFromOpcodeReturnsNullForInvalidOpcode() {
                assertNull(ConditionalBranchInstruction.BranchType.fromOpcode(0xFF));
            }

            @Test
            void branchTypeGettersReturnCorrectValues() {
                ConditionalBranchInstruction.BranchType type = ConditionalBranchInstruction.BranchType.IFEQ;

                assertEquals(0x99, type.getOpcode());
                assertEquals("ifeq", type.getMnemonic());
            }
        }
    }

    @Nested
    class GotoInstructionTests {

        @Nested
        class GotoTests {

            @Test
            void gotoHasCorrectOpcodeAndType() {
                GotoInstruction instr = new GotoInstruction(0xA7, 10, (short) 42);

                assertEquals(0xA7, instr.getOpcode());
                assertEquals(GotoInstruction.GotoType.GOTO, instr.getType());
            }

            @Test
            void gotoHasLengthThree() {
                GotoInstruction instr = new GotoInstruction(0xA7, 10, (short) 42);

                assertEquals(3, instr.getLength());
            }

            @Test
            void gotoBranchOffsetStoredCorrectly() {
                GotoInstruction instr = new GotoInstruction(0xA7, 10, (short) 42);

                assertEquals(42, instr.getBranchOffset());
            }

            @Test
            void gotoNegativeBranchOffsetStoredCorrectly() {
                GotoInstruction instr = new GotoInstruction(0xA7, 10, (short) -100);

                assertEquals(-100, instr.getBranchOffset());
            }

            @Test
            void gotoWritesCorrectBytecode() throws IOException {
                GotoInstruction instr = new GotoInstruction(0xA7, 10, (short) 0x0123);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                instr.write(dos);
                dos.flush();

                byte[] bytes = baos.toByteArray();
                assertEquals(3, bytes.length);
                assertEquals((byte) 0xA7, bytes[0]);
                assertEquals((byte) 0x01, bytes[1]);
                assertEquals((byte) 0x23, bytes[2]);
            }
        }

        @Nested
        class GotoWTests {

            @Test
            void gotoWHasCorrectOpcodeAndType() {
                GotoInstruction instr = new GotoInstruction(0xC8, 10, 42);

                assertEquals(0xC8, instr.getOpcode());
                assertEquals(GotoInstruction.GotoType.GOTO_W, instr.getType());
            }

            @Test
            void gotoWHasLengthFive() {
                GotoInstruction instr = new GotoInstruction(0xC8, 10, 42);

                assertEquals(5, instr.getLength());
            }

            @Test
            void gotoWBranchOffsetStoredCorrectly() {
                GotoInstruction instr = new GotoInstruction(0xC8, 10, 0x12345678);

                assertEquals(0x12345678, instr.getBranchOffsetWide());
            }

            @Test
            void gotoWNegativeBranchOffsetStoredCorrectly() {
                GotoInstruction instr = new GotoInstruction(0xC8, 10, -100000);

                assertEquals(-100000, instr.getBranchOffsetWide());
            }

            @Test
            void gotoWWritesCorrectBytecode() throws IOException {
                GotoInstruction instr = new GotoInstruction(0xC8, 10, 0x12345678);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                instr.write(dos);
                dos.flush();

                byte[] bytes = baos.toByteArray();
                assertEquals(5, bytes.length);
                assertEquals((byte) 0xC8, bytes[0]);
                assertEquals((byte) 0x12, bytes[1]);
                assertEquals((byte) 0x34, bytes[2]);
                assertEquals((byte) 0x56, bytes[3]);
                assertEquals((byte) 0x78, bytes[4]);
            }
        }

        @Nested
        class CommonBehaviorTests {

            @Test
            void gotoHasZeroStackChange() {
                GotoInstruction gotoInstr = new GotoInstruction(0xA7, 10, (short) 42);
                GotoInstruction gotoWInstr = new GotoInstruction(0xC8, 10, 42);

                assertEquals(0, gotoInstr.getStackChange());
                assertEquals(0, gotoWInstr.getStackChange());
            }

            @Test
            void gotoHasZeroLocalChange() {
                GotoInstruction gotoInstr = new GotoInstruction(0xA7, 10, (short) 42);
                GotoInstruction gotoWInstr = new GotoInstruction(0xC8, 10, 42);

                assertEquals(0, gotoInstr.getLocalChange());
                assertEquals(0, gotoWInstr.getLocalChange());
            }

            @Test
            void toStringReturnsCorrectFormat() {
                GotoInstruction gotoInstr = new GotoInstruction(0xA7, 10, (short) 42);
                GotoInstruction gotoWInstr = new GotoInstruction(0xC8, 10, 100);

                assertTrue(gotoInstr.toString().contains("GOTO"));
                assertTrue(gotoWInstr.toString().contains("GOTO_W"));
            }

            @Test
            void acceptsVisitor() {
                GotoInstruction instr = new GotoInstruction(0xA7, 10, (short) 42);

                instr.accept(visitor);

                assertTrue(visitor.visitedGoto);
            }

            @Test
            void throwsExceptionForInvalidOpcode() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                    new GotoInstruction(0x00, 10, (short) 5);
                });

                assertTrue(exception.getMessage().contains("Invalid GOTO opcode"));
            }

            @Test
            void gotoTypeFromOpcodeReturnsCorrectType() {
                assertEquals(GotoInstruction.GotoType.GOTO,
                        GotoInstruction.GotoType.fromOpcode(0xA7));
                assertEquals(GotoInstruction.GotoType.GOTO_W,
                        GotoInstruction.GotoType.fromOpcode(0xC8));
            }

            @Test
            void gotoTypeFromOpcodeReturnsNullForInvalidOpcode() {
                assertNull(GotoInstruction.GotoType.fromOpcode(0xFF));
            }
        }
    }

    @Nested
    class TableSwitchInstructionTests {

        @Test
        void tableSwitchHasCorrectOpcode() {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            jumpOffsets.put(0, 10);
            jumpOffsets.put(1, 20);
            jumpOffsets.put(2, 30);

            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 0, 0, 100, 0, 2, jumpOffsets);

            assertEquals(0xAA, instr.getOpcode());
        }

        @Test
        void tableSwitchLengthCalculatedCorrectly() {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            jumpOffsets.put(0, 10);
            jumpOffsets.put(1, 20);
            jumpOffsets.put(2, 30);

            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 0, 0, 100, 0, 2, jumpOffsets);

            int expectedLength = 1 + 0 + 12 + ((2 - 0 + 1) * 4);
            assertEquals(expectedLength, instr.getLength());
        }

        @Test
        void tableSwitchWithPaddingCalculatesLengthCorrectly() {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            jumpOffsets.put(5, 10);
            jumpOffsets.put(6, 20);

            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 1, 2, 100, 5, 6, jumpOffsets);

            int expectedLength = 1 + 2 + 12 + ((6 - 5 + 1) * 4);
            assertEquals(expectedLength, instr.getLength());
        }

        @Test
        void tableSwitchStoresDefaultOffset() {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 0, 0, 999, 0, 2, jumpOffsets);

            assertEquals(999, instr.getDefaultOffset());
        }

        @Test
        void tableSwitchStoresLowAndHigh() {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 0, 0, 100, 10, 20, jumpOffsets);

            assertEquals(10, instr.getLow());
            assertEquals(20, instr.getHigh());
        }

        @Test
        void tableSwitchStoresJumpOffsets() {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            jumpOffsets.put(0, 10);
            jumpOffsets.put(1, 20);
            jumpOffsets.put(2, 30);

            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 0, 0, 100, 0, 2, jumpOffsets);

            assertEquals(jumpOffsets, instr.getJumpOffsets());
        }

        @Test
        void tableSwitchHasStackChangeMinusOne() {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 0, 0, 100, 0, 2, jumpOffsets);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void tableSwitchHasZeroLocalChange() {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 0, 0, 100, 0, 2, jumpOffsets);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void tableSwitchWritesCorrectBytecode() throws IOException {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            jumpOffsets.put(0, 10);
            jumpOffsets.put(1, 20);

            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 0, 0, 100, 0, 1, jumpOffsets);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals((byte) 0xAA, bytes[0]);
        }

        @Test
        void tableSwitchWritesDefaultOffsetForMissingKey() throws IOException {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            jumpOffsets.put(0, 10);

            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 0, 0, 999, 0, 1, jumpOffsets);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            int key1Offset = java.nio.ByteBuffer.wrap(bytes, 17, 4).getInt();
            assertEquals(999, key1Offset);
        }

        @Test
        void tableSwitchToStringContainsRelevantInfo() {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            jumpOffsets.put(0, 10);
            jumpOffsets.put(1, 20);

            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 0, 0, 100, 0, 1, jumpOffsets);

            String result = instr.toString();

            assertTrue(result.contains("TABLESWITCH"));
            assertTrue(result.contains("default=100"));
            assertTrue(result.contains("low=0"));
            assertTrue(result.contains("high=1"));
        }

        @Test
        void tableSwitchAcceptsVisitor() {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();
            TableSwitchInstruction instr = new TableSwitchInstruction(0xAA, 0, 0, 100, 0, 2, jumpOffsets);

            instr.accept(visitor);

            assertTrue(visitor.visitedTableSwitch);
        }

        @Test
        void tableSwitchThrowsExceptionForInvalidOpcode() {
            Map<Integer, Integer> jumpOffsets = new HashMap<>();

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new TableSwitchInstruction(0xAB, 0, 0, 100, 0, 2, jumpOffsets);
            });

            assertTrue(exception.getMessage().contains("Invalid opcode for TableSwitchInstruction"));
        }
    }

    @Nested
    class LookupSwitchInstructionTests {

        @Test
        void lookupSwitchHasCorrectOpcode() {
            Map<Integer, Integer> matchOffsets = new HashMap<>();
            matchOffsets.put(10, 100);
            matchOffsets.put(20, 200);

            LookupSwitchInstruction instr = new LookupSwitchInstruction(0xAB, 0, 0, 999, 2, matchOffsets);

            assertEquals(0xAB, instr.getOpcode());
        }

        @Test
        void lookupSwitchLengthCalculatedCorrectly() {
            Map<Integer, Integer> matchOffsets = new HashMap<>();
            matchOffsets.put(10, 100);
            matchOffsets.put(20, 200);

            LookupSwitchInstruction instr = new LookupSwitchInstruction(0xAB, 0, 0, 999, 2, matchOffsets);

            int expectedLength = 1 + 0 + 8 + (2 * 8);
            assertEquals(expectedLength, instr.getLength());
        }

        @Test
        void lookupSwitchWithPaddingCalculatesLengthCorrectly() {
            Map<Integer, Integer> matchOffsets = new HashMap<>();
            matchOffsets.put(10, 100);

            LookupSwitchInstruction instr = new LookupSwitchInstruction(0xAB, 1, 3, 999, 1, matchOffsets);

            int expectedLength = 1 + 3 + 8 + (1 * 8);
            assertEquals(expectedLength, instr.getLength());
        }

        @Test
        void lookupSwitchStoresDefaultOffset() {
            Map<Integer, Integer> matchOffsets = new HashMap<>();
            LookupSwitchInstruction instr = new LookupSwitchInstruction(0xAB, 0, 0, 888, 0, matchOffsets);

            assertEquals(888, instr.getDefaultOffset());
        }

        @Test
        void lookupSwitchStoresNpairs() {
            Map<Integer, Integer> matchOffsets = new HashMap<>();
            matchOffsets.put(10, 100);
            matchOffsets.put(20, 200);
            matchOffsets.put(30, 300);

            LookupSwitchInstruction instr = new LookupSwitchInstruction(0xAB, 0, 0, 999, 3, matchOffsets);

            assertEquals(3, instr.getNpairs());
        }

        @Test
        void lookupSwitchStoresMatchOffsets() {
            Map<Integer, Integer> matchOffsets = new HashMap<>();
            matchOffsets.put(10, 100);
            matchOffsets.put(20, 200);

            LookupSwitchInstruction instr = new LookupSwitchInstruction(0xAB, 0, 0, 999, 2, matchOffsets);

            assertEquals(matchOffsets, instr.getMatchOffsets());
        }

        @Test
        void lookupSwitchHasStackChangeMinusOne() {
            Map<Integer, Integer> matchOffsets = new HashMap<>();
            LookupSwitchInstruction instr = new LookupSwitchInstruction(0xAB, 0, 0, 999, 0, matchOffsets);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void lookupSwitchHasZeroLocalChange() {
            Map<Integer, Integer> matchOffsets = new HashMap<>();
            LookupSwitchInstruction instr = new LookupSwitchInstruction(0xAB, 0, 0, 999, 0, matchOffsets);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void lookupSwitchWritesCorrectBytecode() throws IOException {
            Map<Integer, Integer> matchOffsets = new HashMap<>();
            matchOffsets.put(10, 100);

            LookupSwitchInstruction instr = new LookupSwitchInstruction(0xAB, 0, 0, 999, 1, matchOffsets);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals((byte) 0xAB, bytes[0]);
        }

        @Test
        void lookupSwitchToStringContainsRelevantInfo() {
            Map<Integer, Integer> matchOffsets = new HashMap<>();
            matchOffsets.put(10, 100);
            matchOffsets.put(20, 200);

            LookupSwitchInstruction instr = new LookupSwitchInstruction(0xAB, 0, 0, 999, 2, matchOffsets);

            String result = instr.toString();

            assertTrue(result.contains("LOOKUPSWITCH"));
            assertTrue(result.contains("default=999"));
            assertTrue(result.contains("npairs=2"));
        }

        @Test
        void lookupSwitchAcceptsVisitor() {
            Map<Integer, Integer> matchOffsets = new HashMap<>();
            LookupSwitchInstruction instr = new LookupSwitchInstruction(0xAB, 0, 0, 999, 0, matchOffsets);

            instr.accept(visitor);

            assertTrue(visitor.visitedLookupSwitch);
        }

        @Test
        void lookupSwitchThrowsExceptionForInvalidOpcode() {
            Map<Integer, Integer> matchOffsets = new HashMap<>();

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new LookupSwitchInstruction(0xAA, 0, 0, 999, 0, matchOffsets);
            });

            assertTrue(exception.getMessage().contains("Invalid opcode for LookupSwitchInstruction"));
        }
    }

    @Nested
    class ReturnInstructionTests {

        @Test
        void ireturnHasCorrectOpcodeAndType() {
            ReturnInstruction instr = new ReturnInstruction(0xAC, 10);

            assertEquals(0xAC, instr.getOpcode());
            assertEquals(ReturnType.IRETURN, instr.getType());
        }

        @Test
        void lreturnHasCorrectOpcodeAndType() {
            ReturnInstruction instr = new ReturnInstruction(0xAD, 10);

            assertEquals(0xAD, instr.getOpcode());
            assertEquals(ReturnType.LRETURN, instr.getType());
        }

        @Test
        void freturnHasCorrectOpcodeAndType() {
            ReturnInstruction instr = new ReturnInstruction(0xAE, 10);

            assertEquals(0xAE, instr.getOpcode());
            assertEquals(ReturnType.FRETURN, instr.getType());
        }

        @Test
        void dreturnHasCorrectOpcodeAndType() {
            ReturnInstruction instr = new ReturnInstruction(0xAF, 10);

            assertEquals(0xAF, instr.getOpcode());
            assertEquals(ReturnType.DRETURN, instr.getType());
        }

        @Test
        void areturnHasCorrectOpcodeAndType() {
            ReturnInstruction instr = new ReturnInstruction(0xB0, 10);

            assertEquals(0xB0, instr.getOpcode());
            assertEquals(ReturnType.ARETURN, instr.getType());
        }

        @Test
        void returnHasCorrectOpcodeAndType() {
            ReturnInstruction instr = new ReturnInstruction(0xB1, 10);

            assertEquals(0xB1, instr.getOpcode());
            assertEquals(ReturnType.RETURN, instr.getType());
        }

        @Test
        void singleWordReturnsPopsOneValue() {
            ReturnInstruction ireturn = new ReturnInstruction(0xAC, 10);
            ReturnInstruction freturn = new ReturnInstruction(0xAE, 10);
            ReturnInstruction areturn = new ReturnInstruction(0xB0, 10);

            assertEquals(-1, ireturn.getStackChange());
            assertEquals(-1, freturn.getStackChange());
            assertEquals(-1, areturn.getStackChange());
        }

        @Test
        void doubleWordReturnsPropsTwoValues() {
            ReturnInstruction lreturn = new ReturnInstruction(0xAD, 10);
            ReturnInstruction dreturn = new ReturnInstruction(0xAF, 10);

            assertEquals(-2, lreturn.getStackChange());
            assertEquals(-2, dreturn.getStackChange());
        }

        @Test
        void voidReturnHasNoStackChange() {
            ReturnInstruction returnInstr = new ReturnInstruction(0xB1, 10);

            assertEquals(0, returnInstr.getStackChange());
        }

        @Test
        void allReturnsHaveLengthOne() {
            ReturnInstruction ireturn = new ReturnInstruction(0xAC, 10);
            ReturnInstruction lreturn = new ReturnInstruction(0xAD, 10);
            ReturnInstruction freturn = new ReturnInstruction(0xAE, 10);
            ReturnInstruction dreturn = new ReturnInstruction(0xAF, 10);
            ReturnInstruction areturn = new ReturnInstruction(0xB0, 10);
            ReturnInstruction returnInstr = new ReturnInstruction(0xB1, 10);

            assertEquals(1, ireturn.getLength());
            assertEquals(1, lreturn.getLength());
            assertEquals(1, freturn.getLength());
            assertEquals(1, dreturn.getLength());
            assertEquals(1, areturn.getLength());
            assertEquals(1, returnInstr.getLength());
        }

        @Test
        void allReturnsHaveZeroLocalChange() {
            ReturnInstruction ireturn = new ReturnInstruction(0xAC, 10);
            ReturnInstruction returnInstr = new ReturnInstruction(0xB1, 10);

            assertEquals(0, ireturn.getLocalChange());
            assertEquals(0, returnInstr.getLocalChange());
        }

        @Test
        void returnWritesCorrectBytecode() throws IOException {
            ReturnInstruction instr = new ReturnInstruction(0xAC, 10);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(1, bytes.length);
            assertEquals((byte) 0xAC, bytes[0]);
        }

        @Test
        void returnToStringReturnsUpperCaseMnemonic() {
            ReturnInstruction ireturn = new ReturnInstruction(0xAC, 10);
            ReturnInstruction lreturn = new ReturnInstruction(0xAD, 10);
            ReturnInstruction returnInstr = new ReturnInstruction(0xB1, 10);

            assertEquals("IRETURN", ireturn.toString());
            assertEquals("LRETURN", lreturn.toString());
            assertEquals("RETURN", returnInstr.toString());
        }

        @Test
        void returnAcceptsVisitor() {
            ReturnInstruction instr = new ReturnInstruction(0xAC, 10);

            instr.accept(visitor);

            assertTrue(visitor.visitedReturn);
        }

        @Test
        void throwsExceptionForInvalidOpcode() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new ReturnInstruction(0x00, 10);
            });

            assertTrue(exception.getMessage().contains("Invalid Return opcode"));
        }

        @Test
        void constructorSetsCorrectOffset() {
            ReturnInstruction instr = new ReturnInstruction(0xAC, 42);

            assertEquals(42, instr.getOffset());
        }
    }

    private static class TestVisitor extends AbstractBytecodeVisitor {
        boolean visitedConditionalBranch = false;
        boolean visitedGoto = false;
        boolean visitedTableSwitch = false;
        boolean visitedLookupSwitch = false;
        boolean visitedReturn = false;

        @Override
        public void visit(ConditionalBranchInstruction instr) {
            visitedConditionalBranch = true;
        }

        @Override
        public void visit(GotoInstruction instr) {
            visitedGoto = true;
        }

        @Override
        public void visit(TableSwitchInstruction instr) {
            visitedTableSwitch = true;
        }

        @Override
        public void visit(LookupSwitchInstruction instr) {
            visitedLookupSwitch = true;
        }

        @Override
        public void visit(ReturnInstruction instr) {
            visitedReturn = true;
        }
    }
}
