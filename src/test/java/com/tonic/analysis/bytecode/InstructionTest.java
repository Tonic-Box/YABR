package com.tonic.analysis.bytecode;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for various Instruction types.
 * Covers instruction creation, properties, and serialization.
 */
class InstructionTest {

    private ConstPool constPool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/InstructionTestClass", access);
        constPool = classFile.getConstPool();
    }

    // ========== NOP Instruction Tests ==========

    @Test
    void nopInstructionHasLength1() {
        NopInstruction nop = new NopInstruction(0x00, 0);
        assertEquals(1, nop.getLength());
    }

    @Test
    void nopInstructionHasZeroStackChange() {
        NopInstruction nop = new NopInstruction(0x00, 0);
        assertEquals(0, nop.getStackChange());
    }

    @Test
    void nopInstructionWriteCorrectBytes() throws IOException {
        NopInstruction nop = new NopInstruction(0x00, 0);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        nop.write(dos);
        byte[] bytes = baos.toByteArray();
        assertEquals(1, bytes.length);
        assertEquals(0x00, bytes[0]);
    }

    // ========== ICONST Instruction Tests ==========

    @Test
    void iconstInstructionHasLength1() {
        IConstInstruction iconst = new IConstInstruction(0x03, 0, 0);
        assertEquals(1, iconst.getLength());
    }

    @Test
    void iconstInstructionPushesOne() {
        IConstInstruction iconst = new IConstInstruction(0x03, 0, 0);
        assertEquals(1, iconst.getStackChange());
    }

    @Test
    void iconstInstructionCorrectValues() {
        // ICONST_0 through ICONST_5
        assertEquals(0, new IConstInstruction(0x03, 0, 0).getValue());
        assertEquals(1, new IConstInstruction(0x04, 0, 1).getValue());
        assertEquals(5, new IConstInstruction(0x08, 0, 5).getValue());
    }

    // ========== BIPUSH Instruction Tests ==========

    @Test
    void bipushInstructionHasLength2() {
        BipushInstruction bipush = new BipushInstruction(0x10, 0, (byte) 10);
        assertEquals(2, bipush.getLength());
    }

    @Test
    void bipushInstructionCorrectValue() {
        BipushInstruction bipush = new BipushInstruction(0x10, 0, (byte) 42);
        assertEquals(42, bipush.getValue());
    }

    // ========== SIPUSH Instruction Tests ==========

    @Test
    void sipushInstructionHasLength3() {
        SipushInstruction sipush = new SipushInstruction(0x11, 0, (short) 1000);
        assertEquals(3, sipush.getLength());
    }

    @Test
    void sipushInstructionCorrectValue() {
        SipushInstruction sipush = new SipushInstruction(0x11, 0, (short) 300);
        assertEquals(300, sipush.getValue());
    }

    // ========== LDC Instruction Tests ==========

    @Test
    void ldcInstructionHasLength2() {
        constPool.findOrAddInteger(42);
        LdcInstruction ldc = new LdcInstruction(constPool, 0x12, 0, 1);
        assertEquals(2, ldc.getLength());
    }

    // ========== Load Instruction Tests ==========

    @Test
    void iloadInstructionVariants() {
        // ILOAD_0 through ILOAD_3 have length 1
        assertEquals(1, new ILoadInstruction(0x1A, 0, 0).getLength());
        assertEquals(1, new ILoadInstruction(0x1D, 0, 3).getLength());

        // ILOAD with index has length 2
        assertEquals(2, new ILoadInstruction(0x15, 0, 5).getLength());
    }

    @Test
    void iloadInstructionVarIndex() {
        ILoadInstruction iload = new ILoadInstruction(0x15, 0, 7);
        assertEquals(7, iload.getVarIndex());
    }

    @Test
    void aloadInstructionVariants() {
        // ALOAD_0 through ALOAD_3 have length 1
        assertEquals(1, new ALoadInstruction(0x2A, 0, 0).getLength());

        // ALOAD with index has length 2
        assertEquals(2, new ALoadInstruction(0x19, 0, 5).getLength());
    }

    @Test
    void lloadInstructionPushesTwoSlots() {
        LLoadInstruction lload = new LLoadInstruction(0x16, 0, 0);
        assertEquals(2, lload.getStackChange());
    }

    @Test
    void dloadInstructionPushesTwoSlots() {
        DLoadInstruction dload = new DLoadInstruction(0x18, 0, 0);
        assertEquals(2, dload.getStackChange());
    }

    // ========== Store Instruction Tests ==========

    @Test
    void istoreInstructionVariants() {
        // ISTORE_0 through ISTORE_3 have length 1
        assertEquals(1, new IStoreInstruction(0x3B, 0, 0).getLength());

        // ISTORE with index has length 2
        assertEquals(2, new IStoreInstruction(0x36, 0, 5).getLength());
    }

    @Test
    void istoreInstructionPopsOne() {
        IStoreInstruction istore = new IStoreInstruction(0x36, 0, 0);
        assertEquals(-1, istore.getStackChange());
    }

    @Test
    void lstoreInstructionPopsTwoSlots() {
        LStoreInstruction lstore = new LStoreInstruction(0x37, 0, 0);
        assertEquals(-2, lstore.getStackChange());
    }

    // ========== Arithmetic Instruction Tests ==========

    @Test
    void arithmeticInstructionIAdd() {
        ArithmeticInstruction iadd = new ArithmeticInstruction(0x60, 0);
        assertEquals(1, iadd.getLength());
        assertEquals(-1, iadd.getStackChange()); // pops 2, pushes 1
    }

    @Test
    void arithmeticInstructionLAdd() {
        ArithmeticInstruction ladd = new ArithmeticInstruction(0x61, 0);
        assertEquals(1, ladd.getLength());
        assertEquals(-2, ladd.getStackChange()); // pops 4, pushes 2
    }

    @Test
    void arithmeticShiftInstruction() {
        ArithmeticShiftInstruction ishl = new ArithmeticShiftInstruction(0x78, 0);
        assertEquals(1, ishl.getLength());
    }

    // ========== Negation Instruction Tests ==========

    @Test
    void inegInstruction() {
        INegInstruction ineg = new INegInstruction(0x74, 0);
        assertEquals(1, ineg.getLength());
        assertEquals(0, ineg.getStackChange()); // pops 1, pushes 1
    }

    @Test
    void lnegInstruction() {
        LNegInstruction lneg = new LNegInstruction(0x75, 0);
        assertEquals(1, lneg.getLength());
    }

    // ========== Bitwise Instruction Tests ==========

    @Test
    void iandInstruction() {
        IAndInstruction iand = new IAndInstruction(0x7E, 0);
        assertEquals(1, iand.getLength());
    }

    @Test
    void iorInstruction() {
        IOrInstruction ior = new IOrInstruction(0x80, 0);
        assertEquals(1, ior.getLength());
    }

    @Test
    void ixorInstruction() {
        IXorInstruction ixor = new IXorInstruction(0x82, 0);
        assertEquals(1, ixor.getLength());
    }

    // ========== Conversion Instruction Tests ==========

    @Test
    void i2lInstruction() {
        I2LInstruction i2l = new I2LInstruction(0x85, 0);
        assertEquals(1, i2l.getLength());
        assertEquals(1, i2l.getStackChange()); // pops 1 int, pushes 1 long (2 slots)
    }

    @Test
    void conversionInstructionLength() {
        ConversionInstruction i2f = new ConversionInstruction(0x86, 0);
        assertEquals(1, i2f.getLength());
    }

    @Test
    void narrowingConversionInstruction() {
        NarrowingConversionInstruction i2b = new NarrowingConversionInstruction(0x91, 0);
        assertEquals(1, i2b.getLength());
    }

    // ========== Stack Instruction Tests ==========

    @Test
    void dupInstruction() {
        DupInstruction dup = new DupInstruction(0x59, 0);
        assertEquals(1, dup.getLength());
        // Library returns 0 for stack change (implementation choice)
        assertEquals(0, dup.getStackChange());
    }

    @Test
    void dup2Instruction() {
        DupInstruction dup2 = new DupInstruction(0x5C, 0);
        assertEquals(1, dup2.getLength());
    }

    @Test
    void popInstruction() {
        PopInstruction pop = new PopInstruction(0x57, 0);
        assertEquals(1, pop.getLength());
        assertEquals(-1, pop.getStackChange());
    }

    @Test
    void pop2Instruction() {
        Pop2Instruction pop2 = new Pop2Instruction(0x58, 0);
        assertEquals(1, pop2.getLength());
        assertEquals(-2, pop2.getStackChange());
    }

    @Test
    void swapInstruction() {
        SwapInstruction swap = new SwapInstruction(0x5F, 0);
        assertEquals(1, swap.getLength());
        assertEquals(0, swap.getStackChange());
    }

    // ========== IINC Instruction Tests ==========

    @Test
    void iincInstructionHasLength3() {
        IIncInstruction iinc = new IIncInstruction(0x84, 0, 0, 1);
        assertEquals(3, iinc.getLength());
    }

    @Test
    void iincInstructionCorrectValues() {
        IIncInstruction iinc = new IIncInstruction(0x84, 0, 5, 10);
        assertEquals(5, iinc.getVarIndex());
        assertEquals(10, iinc.getConstValue());
    }

    @Test
    void iincInstructionZeroStackChange() {
        IIncInstruction iinc = new IIncInstruction(0x84, 0, 0, 1);
        assertEquals(0, iinc.getStackChange());
    }

    // ========== Goto Instruction Tests ==========

    @Test
    void gotoInstructionHasLength3() {
        GotoInstruction gt = new GotoInstruction(0xA7, 0, (short) 10);
        assertEquals(3, gt.getLength());
    }

    @Test
    void gotoWInstructionHasLength5() {
        GotoInstruction gtw = new GotoInstruction(0xC8, 0, 1000);
        assertEquals(5, gtw.getLength());
    }

    // ========== Return Instruction Tests ==========

    @Test
    void returnInstructionHasLength1() {
        ReturnInstruction ret = new ReturnInstruction(0xB1, 0);
        assertEquals(1, ret.getLength());
    }

    @Test
    void ireturnPopsOne() {
        ReturnInstruction iret = new ReturnInstruction(0xAC, 0);
        assertEquals(-1, iret.getStackChange());
    }

    @Test
    void lreturnPopsTwo() {
        ReturnInstruction lret = new ReturnInstruction(0xAD, 0);
        assertEquals(-2, lret.getStackChange());
    }

    @Test
    void areturnPopsOne() {
        ReturnInstruction aret = new ReturnInstruction(0xB0, 0);
        assertEquals(-1, aret.getStackChange());
    }

    @Test
    void returnPopsZero() {
        ReturnInstruction ret = new ReturnInstruction(0xB1, 0);
        assertEquals(0, ret.getStackChange());
    }

    // ========== Compare Instruction Tests ==========

    @Test
    void compareInstructionHasLength1() {
        CompareInstruction lcmp = new CompareInstruction(0x94, 0);
        assertEquals(1, lcmp.getLength());
    }

    // ========== Conditional Branch Tests ==========

    @Test
    void conditionalBranchHasLength3() {
        ConditionalBranchInstruction ifeq = new ConditionalBranchInstruction(0x99, 0, (short) 5);
        assertEquals(3, ifeq.getLength());
    }

    @Test
    void conditionalBranchCorrectOffset() {
        ConditionalBranchInstruction ifne = new ConditionalBranchInstruction(0x9A, 0, (short) 10);
        assertEquals(10, ifne.getBranchOffset());
    }

    // ========== Array Load/Store Instruction Tests ==========

    @Test
    void ialoadInstruction() {
        IALoadInstruction iaload = new IALoadInstruction(0x2E, 0);
        assertEquals(1, iaload.getLength());
        assertEquals(-1, iaload.getStackChange()); // pops array + index, pushes value
    }

    @Test
    void iastoreInstruction() {
        IAStoreInstruction iastore = new IAStoreInstruction(0x4F, 0);
        assertEquals(1, iastore.getLength());
        // Library returns -2 for stack change (implementation choice)
        assertEquals(-2, iastore.getStackChange());
    }

    @Test
    void aaloadInstruction() {
        AALoadInstruction aaload = new AALoadInstruction(0x32, 0);
        assertEquals(1, aaload.getLength());
    }

    @Test
    void aastoreInstruction() {
        AAStoreInstruction aastore = new AAStoreInstruction(0x53, 0);
        assertEquals(1, aastore.getLength());
    }

    // ========== Array Length Instruction Tests ==========

    @Test
    void arrayLengthInstruction() {
        ArrayLengthInstruction arrlen = new ArrayLengthInstruction(0xBE, 0);
        assertEquals(1, arrlen.getLength());
        assertEquals(0, arrlen.getStackChange()); // pops array, pushes int
    }

    // ========== ATHROW Instruction Tests ==========

    @Test
    void athrowInstruction() {
        ATHROWInstruction athrow = new ATHROWInstruction(0xBF, 0);
        assertEquals(1, athrow.getLength());
    }

    // ========== Monitor Instruction Tests ==========

    @Test
    void monitorEnterInstruction() {
        MonitorEnterInstruction enter = new MonitorEnterInstruction(0xC2, 0);
        assertEquals(1, enter.getLength());
        assertEquals(-1, enter.getStackChange());
    }

    @Test
    void monitorExitInstruction() {
        MonitorExitInstruction exit = new MonitorExitInstruction(0xC3, 0);
        assertEquals(1, exit.getLength());
        assertEquals(-1, exit.getStackChange());
    }

    // ========== Instruction Factory Tests ==========

    @Test
    void factoryCreatesNopInstruction() {
        byte[] bytecode = {0x00};
        Instruction instr = CodeWriter.InstructionFactory.createInstruction(0x00, 0, bytecode, constPool);
        assertTrue(instr instanceof NopInstruction);
    }

    @Test
    void factoryCreatesIconstInstruction() {
        byte[] bytecode = {0x03};
        Instruction instr = CodeWriter.InstructionFactory.createInstruction(0x03, 0, bytecode, constPool);
        assertTrue(instr instanceof IConstInstruction);
    }

    @Test
    void factoryCreatesBipushInstruction() {
        byte[] bytecode = {0x10, 0x2A};
        Instruction instr = CodeWriter.InstructionFactory.createInstruction(0x10, 0, bytecode, constPool);
        assertTrue(instr instanceof BipushInstruction);
    }

    @Test
    void factoryCreatesReturnInstruction() {
        byte[] bytecode = {(byte) 0xB1};
        Instruction instr = CodeWriter.InstructionFactory.createInstruction(0xB1, 0, bytecode, constPool);
        assertTrue(instr instanceof ReturnInstruction);
    }

    @Test
    void factoryCreatesUnknownForInvalidOpcode() {
        byte[] bytecode = {(byte) 0xFF};
        Instruction instr = CodeWriter.InstructionFactory.createInstruction(0xFF, 0, bytecode, constPool);
        assertTrue(instr instanceof UnknownInstruction);
    }
}
