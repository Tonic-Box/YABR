package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ObfuscatedControlFlowTest {

    private ClassPool pool;
    private ClassFile classFile;
    private SSA ssa;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/SwitchDispatcher", access);
        ssa = new SSA(classFile.getConstPool());
    }

    @Nested
    class TableSwitchControlFlowTests {

        @Test
        void tableSwitchWithMultipleCasesAndMerge() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "dispatch", "I", "I");

            byte[] bytecode = buildTableSwitchBytecode();
            CodeAttribute code = method.getCodeAttribute();
            code.setCode(bytecode);
            code.setMaxStack(2);
            code.setMaxLocals(2);

            IRMethod ir = ssa.lift(method);

            assertNotNull(ir);
            assertTrue(ir.getBlocks().size() >= 5, "Should have entry + cases + default + exit blocks");

            ssa.lower(ir, method);

            CodeAttribute loweredCode = method.getCodeAttribute();
            assertNotNull(loweredCode);
            assertTrue(loweredCode.getCode().length > 0);
        }

        @Test
        void tableSwitchWithStackValuesAcrossBranches() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "stackDispatch", "I", "I");

            byte[] bytecode = buildTableSwitchWithStackBytecode();
            CodeAttribute code = method.getCodeAttribute();
            code.setCode(bytecode);
            code.setMaxStack(3);
            code.setMaxLocals(2);

            IRMethod ir = ssa.lift(method);

            assertNotNull(ir);

            boolean hasPhis = ir.getBlocks().stream()
                .anyMatch(b -> !b.getPhiInstructions().isEmpty());

            ssa.lower(ir, method);

            CodeAttribute loweredCode = method.getCodeAttribute();
            assertNotNull(loweredCode);
            assertTrue(loweredCode.getCode().length > 0);
        }

        private byte[] buildTableSwitchBytecode() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(0x1A);
            dos.writeByte(0xAA);

            int switchStart = 1;
            int padding = (4 - ((switchStart + 1) % 4)) % 4;
            for (int i = 0; i < padding; i++) {
                dos.writeByte(0);
            }

            int headerSize = 12 + 3 * 4;
            int case0Offset = 1 + 1 + padding + headerSize;
            int case1Offset = case0Offset + 5;
            int case2Offset = case1Offset + 5;
            int defaultOffset = case2Offset + 5;
            int exitOffset = defaultOffset + 5;

            dos.writeInt(defaultOffset - switchStart);
            dos.writeInt(0);
            dos.writeInt(2);
            dos.writeInt(case0Offset - switchStart);
            dos.writeInt(case1Offset - switchStart);
            dos.writeInt(case2Offset - switchStart);

            dos.writeByte(0x10); dos.writeByte(10);
            dos.writeByte(0xA7); dos.writeShort(exitOffset - case0Offset);

            dos.writeByte(0x10); dos.writeByte(20);
            dos.writeByte(0xA7); dos.writeShort(exitOffset - case1Offset);

            dos.writeByte(0x10); dos.writeByte(30);
            dos.writeByte(0xA7); dos.writeShort(exitOffset - case2Offset);

            dos.writeByte(0x10); dos.writeByte(-1);
            dos.writeByte(0xA7); dos.writeShort(exitOffset - defaultOffset);

            dos.writeByte(0xAC);

            return baos.toByteArray();
        }

        private byte[] buildTableSwitchWithStackBytecode() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(0x10); dos.writeByte(100);

            dos.writeByte(0x1A);
            dos.writeByte(0xAA);

            int switchStart = 3;
            int padding = (4 - ((switchStart + 1) % 4)) % 4;
            for (int i = 0; i < padding; i++) {
                dos.writeByte(0);
            }

            int headerSize = 12 + 2 * 4;
            int case0Offset = 2 + 1 + 1 + padding + headerSize;
            int case1Offset = case0Offset + 4;
            int defaultOffset = case1Offset + 4;
            int exitOffset = defaultOffset + 4;

            dos.writeInt(defaultOffset - switchStart);
            dos.writeInt(0);
            dos.writeInt(1);
            dos.writeInt(case0Offset - switchStart);
            dos.writeInt(case1Offset - switchStart);

            dos.writeByte(0x04);
            dos.writeByte(0xA7); dos.writeShort(exitOffset - case0Offset);

            dos.writeByte(0x05);
            dos.writeByte(0xA7); dos.writeShort(exitOffset - case1Offset);

            dos.writeByte(0x06);
            dos.writeByte(0xA7); dos.writeShort(exitOffset - defaultOffset);

            dos.writeByte(0x60);
            dos.writeByte(0xAC);

            return baos.toByteArray();
        }
    }

    @Nested
    class LookupSwitchControlFlowTests {

        @Test
        void lookupSwitchWithSparseCases() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "sparseDispatch", "I", "I");

            byte[] bytecode = buildLookupSwitchBytecode();
            CodeAttribute code = method.getCodeAttribute();
            code.setCode(bytecode);
            code.setMaxStack(2);
            code.setMaxLocals(2);

            IRMethod ir = ssa.lift(method);

            assertNotNull(ir);

            ssa.lower(ir, method);

            CodeAttribute loweredCode = method.getCodeAttribute();
            assertNotNull(loweredCode);
            assertTrue(loweredCode.getCode().length > 0);
        }

        private byte[] buildLookupSwitchBytecode() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(0x1A);
            dos.writeByte(0xAB);

            int switchStart = 1;
            int padding = (4 - ((switchStart + 1) % 4)) % 4;
            for (int i = 0; i < padding; i++) {
                dos.writeByte(0);
            }

            int headerSize = 8 + 2 * 8;
            int case100Offset = 1 + 1 + padding + headerSize;
            int case500Offset = case100Offset + 5;
            int defaultOffset = case500Offset + 5;
            int exitOffset = defaultOffset + 5;

            dos.writeInt(defaultOffset - switchStart);
            dos.writeInt(2);
            dos.writeInt(100); dos.writeInt(case100Offset - switchStart);
            dos.writeInt(500); dos.writeInt(case500Offset - switchStart);

            dos.writeByte(0x10); dos.writeByte(1);
            dos.writeByte(0xA7); dos.writeShort(exitOffset - case100Offset);

            dos.writeByte(0x10); dos.writeByte(2);
            dos.writeByte(0xA7); dos.writeShort(exitOffset - case500Offset);

            dos.writeByte(0x10); dos.writeByte(0);
            dos.writeByte(0xA7); dos.writeShort(exitOffset - defaultOffset);

            dos.writeByte(0xAC);

            return baos.toByteArray();
        }
    }

    @Nested
    class ComplexControlFlowTests {

        @Test
        void nestedSwitchAndBranches() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "complexFlow", "I", "I", "I");

            byte[] bytecode = buildComplexControlFlowBytecode();
            CodeAttribute code = method.getCodeAttribute();
            code.setCode(bytecode);
            code.setMaxStack(2);
            code.setMaxLocals(3);

            IRMethod ir = ssa.lift(method);

            assertNotNull(ir);
            assertTrue(ir.getBlocks().size() >= 4);

            ssa.lower(ir, method);

            CodeAttribute loweredCode = method.getCodeAttribute();
            assertNotNull(loweredCode);
        }

        @Test
        void switchFollowedByConditional() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "switchThenBranch", "I", "I", "I");

            byte[] bytecode = buildSwitchThenBranchBytecode();
            CodeAttribute code = method.getCodeAttribute();
            code.setCode(bytecode);
            code.setMaxStack(2);
            code.setMaxLocals(3);

            IRMethod ir = ssa.lift(method);

            assertNotNull(ir);

            ssa.lower(ir, method);

            CodeAttribute loweredCode = method.getCodeAttribute();
            assertNotNull(loweredCode);
            assertTrue(loweredCode.getCode().length > 0);
        }

        private byte[] buildComplexControlFlowBytecode() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(0x1A);
            dos.writeByte(0x1B);
            dos.writeByte(0x9F); dos.writeShort(7);

            dos.writeByte(0x10); dos.writeByte(1);
            dos.writeByte(0xA7); dos.writeShort(4);

            dos.writeByte(0x10); dos.writeByte(2);

            dos.writeByte(0xAC);

            return baos.toByteArray();
        }

        private byte[] buildSwitchThenBranchBytecode() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(0x1A);
            dos.writeByte(0xAA);

            int switchStart = 1;
            int padding = (4 - ((switchStart + 1) % 4)) % 4;
            for (int i = 0; i < padding; i++) {
                dos.writeByte(0);
            }

            int headerSize = 12 + 2 * 4;
            int case0Offset = 1 + 1 + padding + headerSize;
            int case1Offset = case0Offset + 4;
            int mergeOffset = case1Offset + 4;

            dos.writeInt(case1Offset - switchStart);
            dos.writeInt(0);
            dos.writeInt(1);
            dos.writeInt(case0Offset - switchStart);
            dos.writeInt(case1Offset - switchStart);

            dos.writeByte(0x04);
            dos.writeByte(0xA7); dos.writeShort(mergeOffset - case0Offset);

            dos.writeByte(0x05);
            dos.writeByte(0xA7); dos.writeShort(mergeOffset - case1Offset);

            dos.writeByte(0x3C);

            dos.writeByte(0x1B);
            dos.writeByte(0x9E); dos.writeShort(7);

            dos.writeByte(0x1A);
            dos.writeByte(0xA7); dos.writeShort(4);

            dos.writeByte(0x1B);

            dos.writeByte(0xAC);

            return baos.toByteArray();
        }
    }
}
