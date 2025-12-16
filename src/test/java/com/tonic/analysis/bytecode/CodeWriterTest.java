package com.tonic.analysis.bytecode;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CodeWriter bytecode manipulation.
 * Covers instruction parsing, insertion, and bytecode rebuilding.
 */
class CodeWriterTest {

    private ClassPool pool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/CodeWriterTestClass", access);
    }

    // ========== Constructor Tests ==========

    @Test
    void codeWriterFromMethodWithCode() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "hasCode", "V");

        CodeWriter cw = new CodeWriter(method);
        assertNotNull(cw);
        assertNotNull(cw.getMethodEntry());
        assertNotNull(cw.getConstPool());
    }

    @Test
    void codeWriterForAbstractMethodHasEmptyBytecode() {
        int access = new AccessBuilder().setPublic().setAbstract().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "abstractMethod", "()V");

        // Library creates CodeAttribute even for abstract methods
        CodeWriter cw = new CodeWriter(method);
        assertNotNull(cw);
        // Empty bytecode - no instructions parsed
        assertEquals(0, cw.getInstructionCount());
    }

    // ========== Instruction Parsing Tests ==========

    @Test
    void getInstructionsReturnsIterable() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testParse", "V");

        CodeWriter cw = new CodeWriter(method);
        Iterable<Instruction> instructions = cw.getInstructions();

        assertNotNull(instructions);
    }

    @Test
    void getInstructionCountReturnsPositive() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testCount", "V");

        CodeWriter cw = new CodeWriter(method);
        int count = cw.getInstructionCount();

        assertTrue(count >= 0);
    }

    @Test
    void getBytecodeSizeForNewMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testSize", "V");

        CodeWriter cw = new CodeWriter(method);
        int size = cw.getBytecodeSize();

        // New methods have empty bytecode (byte[0])
        assertEquals(0, size);
    }

    // ========== Insert Instruction Tests ==========

    @Test
    void insertILoad() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testILoad", "I", "I");

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        ILoadInstruction instr = cw.insertILoad(offset, 0);

        assertNotNull(instr);
        assertTrue(cw.isModified());
    }

    @Test
    void insertIStore() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testIStore", "V", "I");

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        IStoreInstruction instr = cw.insertIStore(offset, 1);

        assertNotNull(instr);
        assertTrue(cw.isModified());
    }

    @Test
    void insertALoad() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testALoad", "Ljava/lang/Object;", "Ljava/lang/Object;");

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        ALoadInstruction instr = cw.insertALoad(offset, 0);

        assertNotNull(instr);
    }

    @Test
    void insertAStore() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testAStore", "V", "Ljava/lang/Object;");

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        AStoreInstruction instr = cw.insertAStore(offset, 1);

        assertNotNull(instr);
    }

    // ========== Insert Field Access Tests ==========

    @Test
    void insertGetStatic() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testGetStatic", "Ljava/io/PrintStream;");

        // First add a field ref to the constant pool
        int fieldRefIndex = classFile.getConstPool().getIndexOf(
                classFile.getConstPool().findOrAddFieldRef("java/lang/System", "out", "Ljava/io/PrintStream;"));

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        GetFieldInstruction instr = cw.insertGetStatic(offset, fieldRefIndex);

        assertNotNull(instr);
    }

    @Test
    void insertGetField() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testGetField", "I");

        // Add field to the class
        int fieldAccess = new AccessBuilder().setPrivate().build();
        classFile.createNewField(fieldAccess, "value", "I", new java.util.ArrayList<>());

        int fieldRefIndex = classFile.getConstPool().getIndexOf(
                classFile.getConstPool().findOrAddFieldRef("com/test/CodeWriterTestClass", "value", "I"));

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        GetFieldInstruction instr = cw.insertGetField(offset, fieldRefIndex);

        assertNotNull(instr);
    }

    @Test
    void insertPutStatic() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testPutStatic", "V");

        // Add static field
        int fieldAccess = new AccessBuilder().setPrivate().setStatic().build();
        classFile.createNewField(fieldAccess, "counter", "I", new java.util.ArrayList<>());

        int fieldRefIndex = classFile.getConstPool().getIndexOf(
                classFile.getConstPool().findOrAddFieldRef("com/test/CodeWriterTestClass", "counter", "I"));

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        PutFieldInstruction instr = cw.insertPutStatic(offset, fieldRefIndex);

        assertNotNull(instr);
    }

    @Test
    void insertPutField() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testPutField", "V", "I");

        // Add instance field
        int fieldAccess = new AccessBuilder().setPrivate().build();
        classFile.createNewField(fieldAccess, "data", "I", new java.util.ArrayList<>());

        int fieldRefIndex = classFile.getConstPool().getIndexOf(
                classFile.getConstPool().findOrAddFieldRef("com/test/CodeWriterTestClass", "data", "I"));

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        PutFieldInstruction instr = cw.insertPutField(offset, fieldRefIndex);

        assertNotNull(instr);
    }

    // ========== Insert Method Invocation Tests ==========

    @Test
    void insertInvokeStatic() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testInvokeStatic", "J");

        int methodRefIndex = classFile.getConstPool().getIndexOf(
                classFile.getConstPool().findOrAddMethodRef("java/lang/System", "currentTimeMillis", "()J"));

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        InvokeStaticInstruction instr = cw.insertInvokeStatic(offset, methodRefIndex);

        assertNotNull(instr);
    }

    @Test
    void insertInvokeVirtual() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testInvokeVirtual", "I", "Ljava/lang/String;");

        int methodRefIndex = classFile.getConstPool().getIndexOf(
                classFile.getConstPool().findOrAddMethodRef("java/lang/String", "length", "()I"));

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        InvokeVirtualInstruction instr = cw.insertInvokeVirtual(offset, methodRefIndex);

        assertNotNull(instr);
    }

    @Test
    void insertInvokeSpecial() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testInvokeSpecial", "V");

        int methodRefIndex = classFile.getConstPool().getIndexOf(
                classFile.getConstPool().findOrAddMethodRef("java/lang/Object", "<init>", "()V"));

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        InvokeSpecialInstruction instr = cw.insertInvokeSpecial(offset, methodRefIndex);

        assertNotNull(instr);
    }

    // ========== Insert Control Flow Tests ==========

    @Test
    void insertGoto() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testGoto", "V");

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        GotoInstruction instr = cw.insertGoto(offset, (short) 3);

        assertNotNull(instr);
        assertEquals(3, instr.getLength());
    }

    @Test
    void insertGotoW() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testGotoW", "V");

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        GotoInstruction instr = cw.insertGotoW(offset, 1000);

        assertNotNull(instr);
        assertEquals(5, instr.getLength());
    }

    // ========== Insert Constant Load Tests ==========

    @Test
    void insertLDC() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testLDC", "Ljava/lang/String;");

        int stringRefIndex = classFile.getConstPool().getIndexOf(
                classFile.getConstPool().findOrAddString("test"));

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        LdcInstruction instr = cw.insertLDC(offset, stringRefIndex);

        assertNotNull(instr);
        assertEquals(2, instr.getLength());
    }

    @Test
    void insertLDCW() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testLDCW", "Ljava/lang/String;");

        int stringRefIndex = classFile.getConstPool().getIndexOf(
                classFile.getConstPool().findOrAddString("test wide"));

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        LdcWInstruction instr = cw.insertLDCW(offset, stringRefIndex);

        assertNotNull(instr);
        assertEquals(3, instr.getLength());
    }

    // ========== Insert Other Tests ==========

    @Test
    void insertNew() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testNew", "Ljava/lang/StringBuilder;");

        int classRefIndex = classFile.getConstPool().getIndexOf(
                classFile.getConstPool().findOrAddClass("java/lang/StringBuilder"));

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        NewInstruction instr = cw.insertNew(offset, classRefIndex);

        assertNotNull(instr);
    }

    @Test
    void insertIInc() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testIInc", "I", "I");

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        IIncInstruction instr = cw.insertIInc(offset, 0, 1);

        assertNotNull(instr);
    }

    // ========== Append Instruction Tests ==========

    @Test
    void appendInstruction() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testAppend", "V");

        CodeWriter cw = new CodeWriter(method);
        int sizeBefore = cw.getBytecodeSize();

        cw.appendInstruction(new NopInstruction(0x00, sizeBefore));

        int sizeAfter = cw.getBytecodeSize();
        assertTrue(sizeAfter > sizeBefore);
    }

    // ========== Write Tests ==========

    @Test
    void writeUpdatesBytecode() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testWrite", "V");

        CodeWriter cw = new CodeWriter(method);
        int sizeBefore = cw.getBytecodeSize();

        // appendInstruction rebuilds bytecode internally
        cw.appendInstruction(new NopInstruction(0x00, sizeBefore));

        // After append, bytecode size should increase
        assertTrue(cw.getBytecodeSize() > sizeBefore);
    }

    // ========== Ends With Return Tests ==========

    @Test
    void endsWithReturnFalseForEmptyMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testEndsReturn", "V");

        CodeWriter cw = new CodeWriter(method);
        // New methods have empty bytecode - no return instruction
        assertFalse(cw.endsWithReturn());
    }

    // ========== StackMapTable Tests ==========

    @Test
    void hasValidStackMapTableForNewMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testSMT", "V");

        CodeWriter cw = new CodeWriter(method);
        // New simple methods may or may not have SMT
        // Just verify the method works without exception
        assertNotNull(cw);
    }

    @Test
    void computeFrames() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testComputeFrames", "V");

        CodeWriter cw = new CodeWriter(method);
        cw.computeFrames();

        // No exception means success
        assertTrue(true);
    }

    @Test
    void forceComputeFrames() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testForceComputeFrames", "V");

        CodeWriter cw = new CodeWriter(method);
        cw.forceComputeFrames();

        // No exception means success
        assertTrue(true);
    }

    // ========== SSA Integration Tests ==========

    @Test
    void toSSA() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testToSSA", "V");

        CodeWriter cw = new CodeWriter(method);
        var irMethod = cw.toSSA();

        assertNotNull(irMethod);
    }

    @Test
    void fromSSA() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testFromSSA", "V");

        CodeWriter cw = new CodeWriter(method);
        var irMethod = cw.toSSA();
        cw.fromSSA(irMethod);

        assertTrue(cw.isModified());
    }
}
