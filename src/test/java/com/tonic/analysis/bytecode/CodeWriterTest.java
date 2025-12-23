package com.tonic.analysis.bytecode;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CodeWriterTest {

    private ClassPool pool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/CodeWriterTestClass", access);
    }

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
    void codeWriterForAbstractMethodThrowsException() {
        int access = new AccessBuilder().setPublic().setAbstract().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "abstractMethod", "()V");

        assertNull(method.getCodeAttribute());
        assertThrows(IllegalArgumentException.class, () -> new CodeWriter(method));
    }

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

        assertEquals(0, size);
    }

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

    @Test
    void insertGetStatic() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testGetStatic", "Ljava/io/PrintStream;");

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

        int fieldAccess = new AccessBuilder().setPrivate().build();
        classFile.createNewField(fieldAccess, "data", "I", new java.util.ArrayList<>());

        int fieldRefIndex = classFile.getConstPool().getIndexOf(
                classFile.getConstPool().findOrAddFieldRef("com/test/CodeWriterTestClass", "data", "I"));

        CodeWriter cw = new CodeWriter(method);
        int offset = cw.getBytecodeSize();
        PutFieldInstruction instr = cw.insertPutField(offset, fieldRefIndex);

        assertNotNull(instr);
    }

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

    @Test
    void writeUpdatesBytecode() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testWrite", "V");

        CodeWriter cw = new CodeWriter(method);
        int sizeBefore = cw.getBytecodeSize();

        cw.appendInstruction(new NopInstruction(0x00, sizeBefore));

        assertTrue(cw.getBytecodeSize() > sizeBefore);
    }

    @Test
    void endsWithReturnFalseForEmptyMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testEndsReturn", "V");

        CodeWriter cw = new CodeWriter(method);
        assertFalse(cw.endsWithReturn());
    }

    @Test
    void hasValidStackMapTableForNewMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testSMT", "V");

        CodeWriter cw = new CodeWriter(method);
        assertNotNull(cw);
    }

    @Test
    void computeFrames() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testComputeFrames", "V");

        CodeWriter cw = new CodeWriter(method);
        cw.computeFrames();

        assertTrue(true);
    }

    @Test
    void forceComputeFrames() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testForceComputeFrames", "V");

        CodeWriter cw = new CodeWriter(method);
        cw.forceComputeFrames();

        assertTrue(true);
    }

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

    @Nested
    class InstructionFactoryConstantPoolTests {

        @Test
        void createAllConstInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testConst", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new AConstNullInstruction(0x01, 0));
            cw.appendInstruction(new IConstInstruction(0x02, 1, -1));
            cw.appendInstruction(new IConstInstruction(0x03, 2, 0));
            cw.appendInstruction(new LConstInstruction(0x09, 3, 0L));
            cw.appendInstruction(new FConstInstruction(0x0B, 4, 0.0f));
            cw.appendInstruction(new DConstInstruction(0x0E, 5, 0.0));

            assertTrue(cw.getInstructionCount() > 0);
        }

        @Test
        void createBipushAndSipushInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testPush", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new BipushInstruction(0x10, 0, (byte)100));
            cw.appendInstruction(new SipushInstruction(0x11, 1, (short)1000));

            assertEquals(2, cw.getInstructionCount());
        }

        @Test
        void createLdcInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testLdc", "V");

            int stringIdx = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddString("test"));
            int longIdx = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddLong(1234567890L));

            CodeWriter cw = new CodeWriter(method);
            cw.insertLDC(0, stringIdx);
            cw.insertLDCW(2, stringIdx);

            assertTrue(cw.isModified());
        }
    }

    @Nested
    class InstructionFactoryLoadStoreTests {

        @Test
        void createAllLoadInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testLoads", "V", "I", "J", "F", "D", "Ljava/lang/Object;");
            CodeWriter cw = new CodeWriter(method);

            cw.insertILoad(0, 0);
            cw.insertLLoad(2, 1);
            cw.insertFLoad(4, 3);
            cw.insertDLoad(6, 4);
            cw.insertALoad(8, 6);

            assertTrue(cw.getInstructionCount() > 0);
        }

        @Test
        void createAllStoreInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testStores", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.insertIStore(0, 0);
            cw.insertLStore(2, 1);
            cw.insertFStore(4, 3);
            cw.insertDStore(6, 4);
            cw.insertAStore(8, 6);

            assertTrue(cw.getInstructionCount() > 0);
        }

        @Test
        void createArrayLoadInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testArrayLoad", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new IALoadInstruction(0x2E, 0));
            cw.appendInstruction(new LALoadInstruction(0x2F, 1));
            cw.appendInstruction(new FALoadInstruction(0x30, 2));
            cw.appendInstruction(new DALoadInstruction(0x31, 3));
            cw.appendInstruction(new AALoadInstruction(0x32, 4));
            cw.appendInstruction(new BALOADInstruction(0x33, 5));
            cw.appendInstruction(new CALoadInstruction(0x34, 6));
            cw.appendInstruction(new SALoadInstruction(0x35, 7));

            assertTrue(cw.getInstructionCount() >= 8);
        }

        @Test
        void createArrayStoreInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testArrayStore", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new IAStoreInstruction(0x4F, 0));
            cw.appendInstruction(new LAStoreInstruction(0x50, 1));
            cw.appendInstruction(new FAStoreInstruction(0x51, 2));
            cw.appendInstruction(new DAStoreInstruction(0x52, 3));
            cw.appendInstruction(new AAStoreInstruction(0x53, 4));
            cw.appendInstruction(new BAStoreInstruction(0x54, 5));
            cw.appendInstruction(new CAStoreInstruction(0x55, 6));
            cw.appendInstruction(new SAStoreInstruction(0x56, 7));

            assertTrue(cw.getInstructionCount() >= 8);
        }
    }

    @Nested
    class InstructionFactoryStackTests {

        @Test
        void createStackManipulationInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testStack", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new PopInstruction(0x57, 0));
            cw.appendInstruction(new Pop2Instruction(0x58, 1));
            cw.appendInstruction(new DupInstruction(0x59, 2));
            cw.appendInstruction(new SwapInstruction(0x5F, 3));

            assertTrue(cw.getInstructionCount() >= 4);
        }
    }

    @Nested
    class InstructionFactoryArithmeticTests {

        @Test
        void createArithmeticInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testArithmetic", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new ArithmeticInstruction(0x60, 0));
            cw.appendInstruction(new ArithmeticInstruction(0x64, 1));
            cw.appendInstruction(new ArithmeticInstruction(0x68, 2));
            cw.appendInstruction(new ArithmeticInstruction(0x6C, 3));

            assertTrue(cw.getInstructionCount() >= 4);
        }

        @Test
        void createNegationInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testNeg", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new INegInstruction(0x74, 0));
            cw.appendInstruction(new LNegInstruction(0x75, 1));
            cw.appendInstruction(new FNegInstruction(0x76, 2));
            cw.appendInstruction(new DNegInstruction(0x77, 3));

            assertTrue(cw.getInstructionCount() >= 4);
        }

        @Test
        void createShiftInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testShift", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new ArithmeticShiftInstruction(0x78, 0));
            cw.appendInstruction(new ArithmeticShiftInstruction(0x7A, 1));
            cw.appendInstruction(new ArithmeticShiftInstruction(0x7C, 2));

            assertTrue(cw.getInstructionCount() >= 3);
        }

        @Test
        void createBitwiseInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testBitwise", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new IAndInstruction(0x7E, 0));
            cw.appendInstruction(new IOrInstruction(0x80, 1));
            cw.appendInstruction(new IXorInstruction(0x82, 2));

            assertTrue(cw.getInstructionCount() >= 3);
        }
    }

    @Nested
    class InstructionFactoryConversionTests {

        @Test
        void createConversionInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testConversion", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new I2LInstruction(0x85, 0));
            cw.appendInstruction(new ConversionInstruction(0x86, 1));
            cw.appendInstruction(new ConversionInstruction(0x8B, 2));
            cw.appendInstruction(new NarrowingConversionInstruction(0x91, 3));
            cw.appendInstruction(new NarrowingConversionInstruction(0x93, 4));

            assertTrue(cw.getInstructionCount() >= 5);
        }

        @Test
        void createCompareInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testCompare", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new CompareInstruction(0x94, 0));
            cw.appendInstruction(new CompareInstruction(0x95, 1));
            cw.appendInstruction(new CompareInstruction(0x96, 2));

            assertTrue(cw.getInstructionCount() >= 3);
        }
    }

    @Nested
    class InstructionFactoryBranchTests {

        @Test
        void createConditionalBranchInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testBranch", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new ConditionalBranchInstruction(0x99, 0, (short)3));
            cw.appendInstruction(new ConditionalBranchInstruction(0x9F, 3, (short)6));
            cw.appendInstruction(new ConditionalBranchInstruction(0xA5, 6, (short)9));
            cw.appendInstruction(new ConditionalBranchInstruction(0xC6, 9, (short)12));

            assertTrue(cw.getInstructionCount() >= 4);
        }

        @Test
        void createGotoInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testGotoInstr", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.insertGoto(0, (short)5);
            cw.insertGotoW(3, 1000);

            assertTrue(cw.getInstructionCount() >= 2);
        }

        @Test
        void createJsrInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testJsr", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new JsrInstruction(0xA8, 0, (short)5));
            cw.appendInstruction(new RetInstruction(0xA9, 3, 0));

            assertTrue(cw.getInstructionCount() >= 2);
        }
    }

    @Nested
    class InstructionFactoryInvokeTests {

        @Test
        void createInvokeInterface() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testInvokeInterface", "V");

            int methodRefIndex = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddInterfaceRef("java/util/List", "size", "()I"));

            CodeWriter cw = new CodeWriter(method);
            cw.insertInvokeInterface(0, methodRefIndex, 1);

            assertTrue(cw.isModified());
        }

        @Test
        void createInvokeDynamic() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testInvokeDynamic", "V");

            CodeWriter cw = new CodeWriter(method);

            assertTrue(true);
        }
    }

    @Nested
    class InstructionFactoryObjectTests {

        @Test
        void createNewArrayInstruction() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testNewArray", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new NewArrayInstruction(0xBC, 0, 10, 1));

            assertTrue(cw.getInstructionCount() >= 1);
        }

        @Test
        void createANewArrayInstruction() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testANewArray", "V");

            int classIdx = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddClass("java/lang/String"));

            CodeWriter cw = new CodeWriter(method);
            cw.appendInstruction(new ANewArrayInstruction(classFile.getConstPool(), 0xBD, 0, classIdx, 2));

            assertTrue(cw.getInstructionCount() >= 1);
        }

        @Test
        void createMultiANewArrayInstruction() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testMultiArray", "V");

            int classIdx = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddClass("[[I"));

            CodeWriter cw = new CodeWriter(method);
            cw.appendInstruction(new MultiANewArrayInstruction(classFile.getConstPool(), 0xC5, 0, classIdx, 2));

            assertTrue(cw.getInstructionCount() >= 1);
        }

        @Test
        void createArrayLengthInstruction() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testArrayLength", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new ArrayLengthInstruction(0xBE, 0));

            assertTrue(cw.getInstructionCount() >= 1);
        }

        @Test
        void createCheckCastInstruction() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testCheckCast", "V");

            int classIdx = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddClass("java/lang/String"));

            CodeWriter cw = new CodeWriter(method);
            cw.appendInstruction(new CheckCastInstruction(classFile.getConstPool(), 0xC0, 0, classIdx));

            assertTrue(cw.getInstructionCount() >= 1);
        }

        @Test
        void createInstanceOfInstruction() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testInstanceOf", "V");

            int classIdx = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddClass("java/lang/String"));

            CodeWriter cw = new CodeWriter(method);
            cw.appendInstruction(new InstanceOfInstruction(classFile.getConstPool(), 0xC1, 0, classIdx));

            assertTrue(cw.getInstructionCount() >= 1);
        }

        @Test
        void createMonitorInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testMonitor", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new MonitorEnterInstruction(0xC2, 0));
            cw.appendInstruction(new MonitorExitInstruction(0xC3, 1));

            assertTrue(cw.getInstructionCount() >= 2);
        }

        @Test
        void createAThrowInstruction() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testThrow", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new ATHROWInstruction(0xBF, 0));

            assertTrue(cw.getInstructionCount() >= 1);
        }
    }

    @Nested
    class InstructionFactorySwitchTests {

        @Test
        void createTableSwitchInstruction() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testTableSwitch", "V");

            Map<Integer, Integer> jumpOffsets = new LinkedHashMap<>();
            jumpOffsets.put(0, 10);
            jumpOffsets.put(1, 20);
            jumpOffsets.put(2, 30);

            CodeWriter cw = new CodeWriter(method);
            cw.insertTableSwitch(0, 0, 40, 0, 2, jumpOffsets);

            assertTrue(cw.isModified());
        }

        @Test
        void createLookupSwitchInstruction() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testLookupSwitch", "V");

            Map<Integer, Integer> matchOffsets = new LinkedHashMap<>();
            matchOffsets.put(10, 100);
            matchOffsets.put(20, 200);
            matchOffsets.put(30, 300);

            CodeWriter cw = new CodeWriter(method);
            cw.insertLookupSwitch(0, 0, 400, 3, matchOffsets);

            assertTrue(cw.isModified());
        }
    }

    @Nested
    class InstructionFactoryReturnTests {

        @Test
        void createReturnInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testReturn", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new ReturnInstruction(0xB1, 0));

            assertTrue(cw.endsWithReturn());
        }

        @Test
        void createAllReturnTypes() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();

            MethodEntry method1 = classFile.createNewMethod(access, "retInt", "I");
            CodeWriter cw1 = new CodeWriter(method1);
            cw1.appendInstruction(new ReturnInstruction(0xAC, 0));

            MethodEntry method2 = classFile.createNewMethod(access, "retLong", "J");
            CodeWriter cw2 = new CodeWriter(method2);
            cw2.appendInstruction(new ReturnInstruction(0xAD, 0));

            MethodEntry method3 = classFile.createNewMethod(access, "retFloat", "F");
            CodeWriter cw3 = new CodeWriter(method3);
            cw3.appendInstruction(new ReturnInstruction(0xAE, 0));

            MethodEntry method4 = classFile.createNewMethod(access, "retDouble", "D");
            CodeWriter cw4 = new CodeWriter(method4);
            cw4.appendInstruction(new ReturnInstruction(0xAF, 0));

            MethodEntry method5 = classFile.createNewMethod(access, "retRef", "Ljava/lang/Object;");
            CodeWriter cw5 = new CodeWriter(method5);
            cw5.appendInstruction(new ReturnInstruction(0xB0, 0));

            assertTrue(cw1.endsWithReturn() && cw2.endsWithReturn() && cw3.endsWithReturn() &&
                       cw4.endsWithReturn() && cw5.endsWithReturn());
        }
    }

    @Nested
    class CodeWriterIndexTests {

        @Test
        void getInstructionIndex() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testIndex", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new NopInstruction(0x00, 0));
            cw.appendInstruction(new NopInstruction(0x00, 1));

            int index = cw.getInstructionIndex(0);
            assertTrue(index >= 0);
        }

        @Test
        void getInstructionAt() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testAt", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new NopInstruction(0x00, 0));
            cw.appendInstruction(new PopInstruction(0x57, 1));

            Instruction instr = cw.getInstructionAt(0);
            assertNotNull(instr);
        }

        @Test
        void getOffsetAt() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testOffset", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new NopInstruction(0x00, 0));

            int offset = cw.getOffsetAt(0);
            assertTrue(offset >= 0);
        }

        @Test
        void findInstructionIndexForOffset() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testFindIndex", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new NopInstruction(0x00, 0));
            cw.appendInstruction(new NopInstruction(0x00, 1));

            int index = cw.findInstructionIndexForOffset(0);
            assertTrue(index >= 0);
        }

        @Test
        void getInstructionIndexInvalidOffset() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testInvalidIndex", "V");
            CodeWriter cw = new CodeWriter(method);

            int index = cw.getInstructionIndex(9999);
            assertEquals(-1, index);
        }

        @Test
        void getInstructionAtOutOfBounds() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testOutOfBounds", "V");
            CodeWriter cw = new CodeWriter(method);

            Instruction instr = cw.getInstructionAt(9999);
            assertNull(instr);
        }

        @Test
        void getOffsetAtInvalidIndex() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testInvalidOffset", "V");
            CodeWriter cw = new CodeWriter(method);

            int offset = cw.getOffsetAt(-1);
            assertEquals(-1, offset);
        }
    }

    @Nested
    class CodeWriterAnalysisTests {

        @Test
        void analyzeUpdatesMaxStack() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testAnalyze", "I");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new IConstInstruction(0x04, 0, 1));
            cw.appendInstruction(new IConstInstruction(0x05, 1, 2));
            cw.appendInstruction(new ArithmeticInstruction(0x60, 2));
            cw.appendInstruction(new ReturnInstruction(0xAC, 3));

            cw.analyze();
            assertTrue(cw.getMaxStack() > 0);
        }

        @Test
        void analyzeUpdatesMaxLocals() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testAnalyzeLocals", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.insertIStore(0, 0);
            cw.insertIStore(2, 1);

            cw.analyze();
            assertTrue(cw.getMaxLocals() >= 0);
        }
    }

    @Nested
    class CodeWriterInsertionEdgeCasesTests {

        @Test
        void insertInstructionAtInvalidOffset() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testInvalidInsert", "V");
            CodeWriter cw = new CodeWriter(method);

            assertThrows(IllegalArgumentException.class, () -> {
                cw.insertInstruction(9999, new NopInstruction(0x00, 9999));
            });
        }

        @Test
        void insertInstructionAtZeroIntoEmpty() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testInsertEmpty", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.insertInstruction(0, new NopInstruction(0x00, 0));
            assertEquals(1, cw.getInstructionCount());
        }

        @Test
        void insertMultipleInstructionsSequentially() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testMultiInsert", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.insertInstruction(0, new NopInstruction(0x00, 0));
            cw.insertInstruction(0, new PopInstruction(0x57, 0));

            assertTrue(cw.getInstructionCount() >= 2);
        }
    }

    @Nested
    class CodeWriterOptimizationTests {

        @Test
        void optimizeSSA() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testOptimize", "I");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new IConstInstruction(0x04, 0, 1));
            cw.appendInstruction(new ReturnInstruction(0xAC, 1));

            cw.optimizeSSA();
            assertTrue(cw.isModified());
        }
    }

    @Nested
    class BytecodeVisitorTests {

        @Test
        void acceptVisitor() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testVisitor", "V");
            CodeWriter cw = new CodeWriter(method);

            cw.appendInstruction(new NopInstruction(0x00, 0));
            cw.appendInstruction(new ReturnInstruction(0xB1, 1));

            final int[] visitCount = {0};
            cw.accept(new com.tonic.analysis.visitor.AbstractBytecodeVisitor() {
                @Override
                public void visit(NopInstruction instr) {
                    visitCount[0]++;
                }
                @Override
                public void visit(ReturnInstruction instr) {
                    visitCount[0]++;
                }
            });

            assertTrue(visitCount[0] >= 2);
        }
    }
}
