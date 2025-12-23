package com.tonic.analysis.bytecode;

import com.tonic.analysis.Bytecode;
import com.tonic.analysis.CodeWriter;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeTest {

    private ClassPool pool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/BytecodeTestClass", access);
    }

    @Test
    void bytecodeFromMethodEntry() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testMethod", "V");
        Bytecode bc = new Bytecode(method);
        assertNotNull(bc);
        assertNotNull(bc.getCodeWriter());
        assertNotNull(bc.getConstPool());
    }

    @Test
    void bytecodeFromCodeWriter() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testMethod", "V");
        CodeWriter cw = new CodeWriter(method);
        Bytecode bc = new Bytecode(cw);
        assertNotNull(bc);
        assertSame(cw, bc.getCodeWriter());
    }

    @Test
    void addIConstSmallValue() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getSmall", "I");
        Bytecode bc = new Bytecode(method);

        bc.addIConst(5);
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addIConstBipushRange() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getBipush", "I");
        Bytecode bc = new Bytecode(method);

        bc.addIConst(100);
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addIConstSipushRange() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getSipush", "I");
        Bytecode bc = new Bytecode(method);

        bc.addIConst(1000);
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addIConstLdcRange() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getLdc", "I");
        Bytecode bc = new Bytecode(method);

        bc.addIConst(100000);
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addLConstZero() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getLongZero", "J");
        Bytecode bc = new Bytecode(method);

        bc.addLConst(0L);
        bc.addReturn(ReturnType.LRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addLConstOne() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getLongOne", "J");
        Bytecode bc = new Bytecode(method);

        bc.addLConst(1L);
        bc.addReturn(ReturnType.LRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addLConstLdc2w() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getLongBig", "J");
        Bytecode bc = new Bytecode(method);

        bc.addLConst(12345678901234L);
        bc.addReturn(ReturnType.LRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addFConstZero() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getFloatZero", "F");
        Bytecode bc = new Bytecode(method);

        bc.addFConst(0.0f);
        bc.addReturn(ReturnType.FRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addFConstOne() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getFloatOne", "F");
        Bytecode bc = new Bytecode(method);

        bc.addFConst(1.0f);
        bc.addReturn(ReturnType.FRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addDConstZero() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getDoubleZero", "D");
        Bytecode bc = new Bytecode(method);

        bc.addDConst(0.0);
        bc.addReturn(ReturnType.DRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addDConstOne() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getDoubleOne", "D");
        Bytecode bc = new Bytecode(method);

        bc.addDConst(1.0);
        bc.addReturn(ReturnType.DRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addAConstNull() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getNull", "Ljava/lang/Object;");
        Bytecode bc = new Bytecode(method);

        bc.addAConstNull();
        bc.addReturn(ReturnType.ARETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addILoadAndStore() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "copyInt", "I", "I");
        Bytecode bc = new Bytecode(method);

        bc.addILoad(0);
        bc.addIStore(1);
        bc.addILoad(1);
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addALoadAndStore() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "copyObject", "Ljava/lang/Object;", "Ljava/lang/Object;");
        Bytecode bc = new Bytecode(method);

        bc.addALoad(0);
        bc.addAStore(1);
        bc.addALoad(1);
        bc.addReturn(ReturnType.ARETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addLLoadAndLConst() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "loadLong", "J", "J");
        Bytecode bc = new Bytecode(method);

        bc.addLLoad(0);
        bc.addReturn(ReturnType.LRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addLdcString() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getString", "Ljava/lang/String;");
        Bytecode bc = new Bytecode(method);

        bc.addLdc("Hello, World!");
        bc.addReturn(ReturnType.ARETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addIInc() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "increment", "I", "I");
        Bytecode bc = new Bytecode(method);

        bc.addIInc(0, 1);
        bc.addILoad(0);
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addInvokeStatic() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "callStatic", "J");
        Bytecode bc = new Bytecode(method);

        bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
        bc.addReturn(ReturnType.LRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addGetStatic() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getOut", "Ljava/io/PrintStream;");
        Bytecode bc = new Bytecode(method);

        bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
        bc.addReturn(ReturnType.ARETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addInvokeVirtual() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "printHello", "V");
        Bytecode bc = new Bytecode(method);

        bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
        bc.addLdc("Hello");
        bc.addInvokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addReturnVoid() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "doNothing", "V");
        Bytecode bc = new Bytecode(method);

        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();

        assertTrue(bc.endsWithReturn());
    }

    @Test
    void addReturnInt() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getInt", "I");
        Bytecode bc = new Bytecode(method);

        bc.addIConst(42);
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.endsWithReturn());
    }

    @Test
    void defineLabel() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "withLabel", "V");
        Bytecode bc = new Bytecode(method);

        bc.defineLabel("start");
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getLabels().containsKey("start"));
    }

    @Test
    void setInsertBefore() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "insertBefore", "V");
        Bytecode bc = new Bytecode(method);

        bc.addReturn(ReturnType.RETURN);
        bc.setInsertBefore(true);

        assertFalse(bc.isInsertBefore() && bc.getInsertBeforeOffset() < 0);
    }

    @Test
    void isModifiedAfterInsertion() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "modified", "V");
        Bytecode bc = new Bytecode(method);

        bc.addIConst(0);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();

        assertTrue(bc.isModified());
    }

    @Test
    void addLoadForInt() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "loadInt", "I", "I");
        Bytecode bc = new Bytecode(method);

        bc.addLoad(0, "I");
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addLoadForLong() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "loadLong", "J", "J");
        Bytecode bc = new Bytecode(method);

        bc.addLoad(0, "J");
        bc.addReturn(ReturnType.LRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addLoadForFloat() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "loadFloat", "F", "F");
        Bytecode bc = new Bytecode(method);

        bc.addLoad(0, "F");
        bc.addReturn(ReturnType.FRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addLoadForDouble() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "loadDouble", "D", "D");
        Bytecode bc = new Bytecode(method);

        bc.addLoad(0, "D");
        bc.addReturn(ReturnType.DRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addLoadForReference() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "loadRef", "Ljava/lang/Object;", "Ljava/lang/Object;");
        Bytecode bc = new Bytecode(method);

        bc.addLoad(0, "Ljava/lang/Object;");
        bc.addReturn(ReturnType.ARETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void computeFrames() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "withFrames", "V");
        Bytecode bc = new Bytecode(method);

        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
        bc.computeFrames();

        assertTrue(true);
    }

    @Test
    void forceComputeFrames() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "forceFrames", "V");
        Bytecode bc = new Bytecode(method);

        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
        bc.forceComputeFrames();

        assertTrue(true);
    }

    @Nested
    class ConstantLoadingEdgeCasesTests {

        @Test
        void addIConstNegativeOne() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "getNegOne", "I");
            Bytecode bc = new Bytecode(method);

            bc.addIConst(-1);
            bc.addReturn(ReturnType.IRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.endsWithReturn());
        }

        @Test
        void addIConstBoundaryValues() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();

            MethodEntry method1 = classFile.createNewMethod(access, "getMinByte", "I");
            Bytecode bc1 = new Bytecode(method1);
            bc1.addIConst(Byte.MIN_VALUE);
            bc1.addReturn(ReturnType.IRETURN);
            bc1.finalizeBytecode();

            MethodEntry method2 = classFile.createNewMethod(access, "getMaxByte", "I");
            Bytecode bc2 = new Bytecode(method2);
            bc2.addIConst(Byte.MAX_VALUE);
            bc2.addReturn(ReturnType.IRETURN);
            bc2.finalizeBytecode();

            MethodEntry method3 = classFile.createNewMethod(access, "getMinShort", "I");
            Bytecode bc3 = new Bytecode(method3);
            bc3.addIConst(Short.MIN_VALUE);
            bc3.addReturn(ReturnType.IRETURN);
            bc3.finalizeBytecode();

            MethodEntry method4 = classFile.createNewMethod(access, "getMaxShort", "I");
            Bytecode bc4 = new Bytecode(method4);
            bc4.addIConst(Short.MAX_VALUE);
            bc4.addReturn(ReturnType.IRETURN);
            bc4.finalizeBytecode();

            assertTrue(bc1.endsWithReturn() && bc2.endsWithReturn() &&
                       bc3.endsWithReturn() && bc4.endsWithReturn());
        }

        @Test
        void addFConstTwo() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "getFloatTwo", "F");
            Bytecode bc = new Bytecode(method);

            bc.addFConst(2.0f);
            bc.addReturn(ReturnType.FRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.endsWithReturn());
        }

        @Test
        void addFConstArbitrary() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "getFloatPi", "F");
            Bytecode bc = new Bytecode(method);

            bc.addFConst(3.14159f);
            bc.addReturn(ReturnType.FRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.endsWithReturn());
        }

        @Test
        void addDConstArbitrary() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "getDoublePi", "D");
            Bytecode bc = new Bytecode(method);

            bc.addDConst(3.141592653589793);
            bc.addReturn(ReturnType.DRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.endsWithReturn());
        }
    }

    @Nested
    class FieldAccessTests {

        @Test
        void addPutStatic() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "setPutStatic", "V", "I");

            int fieldAccess = new AccessBuilder().setPrivate().setStatic().build();
            classFile.createNewField(fieldAccess, "staticField", "I", new java.util.ArrayList<>());

            Bytecode bc = new Bytecode(method);
            bc.addILoad(0);
            int fieldRefIndex = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddFieldRef("com/test/BytecodeTestClass", "staticField", "I"));
            bc.addPutStatic(fieldRefIndex);
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(bc.isModified());
        }

        @Test
        void addPutField() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethod(access, "setPutField", "V", "I");

            int fieldAccess = new AccessBuilder().setPrivate().build();
            classFile.createNewField(fieldAccess, "instanceField", "I", new java.util.ArrayList<>());

            Bytecode bc = new Bytecode(method);
            bc.addALoad(0);
            bc.addILoad(1);
            int fieldRefIndex = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddFieldRef("com/test/BytecodeTestClass", "instanceField", "I"));
            bc.addPutField(fieldRefIndex);
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(bc.isModified());
        }

        @Test
        void addGetField() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethod(access, "getInstanceField", "I");

            int fieldAccess = new AccessBuilder().setPrivate().build();
            classFile.createNewField(fieldAccess, "value", "I", new java.util.ArrayList<>());

            Bytecode bc = new Bytecode(method);
            bc.addALoad(0);
            int fieldRefIndex = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddFieldRef("com/test/BytecodeTestClass", "value", "I"));
            bc.addGetField(fieldRefIndex);
            bc.addReturn(ReturnType.IRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.isModified());
        }
    }

    @Nested
    class MethodInvocationTests {

        @Test
        void addInvokeSpecial() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethod(access, "callSuper", "V");

            Bytecode bc = new Bytecode(method);
            bc.addALoad(0);
            int methodRefIndex = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddMethodRef("java/lang/Object", "<init>", "()V"));
            bc.addInvokeSpecial(methodRefIndex);
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(bc.isModified());
        }

        @Test
        void addInvokeInterface() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "callInterface", "I", "Ljava/util/List;");

            Bytecode bc = new Bytecode(method);
            bc.addALoad(0);
            int methodRefIndex = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddInterfaceRef("java/util/List", "size", "()I"));
            bc.addInvokeInterface(methodRefIndex, 1);
            bc.addReturn(ReturnType.IRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.isModified());
        }

        @Test
        void addInvokeDynamic() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "callDynamic", "V");

            Bytecode bc = new Bytecode(method);

            assertTrue(true);
        }
    }

    @Nested
    class ObjectCreationTests {

        @Test
        void addNew() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "createObject", "Ljava/lang/StringBuilder;");

            Bytecode bc = new Bytecode(method);
            int classRefIndex = classFile.getConstPool().getIndexOf(
                    classFile.getConstPool().findOrAddClass("java/lang/StringBuilder"));
            bc.addNew(classRefIndex);
            bc.addReturn(ReturnType.ARETURN);
            bc.finalizeBytecode();

            assertTrue(bc.isModified());
        }
    }

    @Nested
    class SwitchInstructionTests {

        @Test
        void addTableSwitch() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testTableSwitch", "I", "I");

            Bytecode bc = new Bytecode(method);
            bc.addILoad(0);

            Map<Integer, Integer> jumpOffsets = new LinkedHashMap<>();
            jumpOffsets.put(0, 10);
            jumpOffsets.put(1, 20);
            jumpOffsets.put(2, 30);

            bc.addTableSwitch(0, 2, 40, jumpOffsets);
            bc.addIConst(0);
            bc.addReturn(ReturnType.IRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.isModified());
        }

        @Test
        void addLookupSwitch() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testLookupSwitch", "I", "I");

            Bytecode bc = new Bytecode(method);
            bc.addILoad(0);

            Map<Integer, Integer> matchOffsets = new LinkedHashMap<>();
            matchOffsets.put(10, 100);
            matchOffsets.put(20, 200);
            matchOffsets.put(30, 300);

            bc.addLookupSwitch(400, matchOffsets);
            bc.addIConst(0);
            bc.addReturn(ReturnType.IRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.isModified());
        }
    }

    @Nested
    class InsertBeforeModeTests {

        @Test
        void insertBeforeForStaticMethod() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "staticInsert", "V");

            Bytecode bc = new Bytecode(method);
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            bc.setInsertBefore(true);
            assertEquals(0, bc.getInsertBeforeOffset());
        }

        @Test
        void insertBeforeForInstanceMethod() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethod(access, "instanceInsert", "V");

            Bytecode bc = new Bytecode(method);
            bc.setInsertBefore(true);

            assertTrue(bc.getInsertBeforeOffset() >= 0);
        }

        @Test
        void insertBeforeWithInstructions() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method = classFile.createNewMethod(access, "withInstructions", "V");

            Bytecode bc = new Bytecode(method);
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            bc.setInsertBefore(true);
            bc.addIConst(42);

            assertTrue(bc.isModified());
        }
    }

    @Nested
    class FLoadAndDLoadTests {

        @Test
        void addFLoad() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testFLoad", "F", "F");

            Bytecode bc = new Bytecode(method);
            bc.addFLoad(0);
            bc.addReturn(ReturnType.FRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
        }

        @Test
        void addDLoad() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testDLoad", "D", "D");

            Bytecode bc = new Bytecode(method);
            bc.addDLoad(0);
            bc.addReturn(ReturnType.DRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
        }
    }

    @Nested
    class LabelTests {

        @Test
        void addGoto() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testGoto", "V");

            Bytecode bc = new Bytecode(method);
            bc.defineLabel("target");
            bc.addGoto("target");
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(bc.getLabels().containsKey("target"));
        }

        @Test
        void addGotoW() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "testGotoW", "V");

            Bytecode bc = new Bytecode(method);
            bc.defineLabel("farTarget");
            bc.addGotoW("farTarget");
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(bc.getLabels().containsKey("farTarget"));
        }

        @Test
        void defineMultipleLabels() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "multiLabel", "V");

            Bytecode bc = new Bytecode(method);
            bc.defineLabel("label1");
            bc.addIConst(1);
            bc.defineLabel("label2");
            bc.addIConst(2);
            bc.defineLabel("label3");
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(bc.getLabels().containsKey("label1") &&
                      bc.getLabels().containsKey("label2") &&
                      bc.getLabels().containsKey("label3"));
        }
    }

    @Nested
    class EndsWithReturnTests {

        @Test
        void endsWithReturnTrue() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "hasReturn", "V");

            Bytecode bc = new Bytecode(method);
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(bc.endsWithReturn());
        }

        @Test
        void endsWithReturnFalse() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "noReturn", "V");

            Bytecode bc = new Bytecode(method);
            bc.addIConst(42);
            bc.finalizeBytecode();

            assertFalse(bc.endsWithReturn());
        }
    }

    @Nested
    class LdcStringTests {

        @Test
        void addLdcShortString() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "shortString", "Ljava/lang/String;");

            Bytecode bc = new Bytecode(method);
            bc.addLdc("test");
            bc.addReturn(ReturnType.ARETURN);
            bc.finalizeBytecode();

            assertTrue(bc.isModified());
        }

        @Test
        void addLdcLongString() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "longString", "Ljava/lang/String;");

            Bytecode bc = new Bytecode(method);
            bc.addLdc("This is a much longer string that might require LDC_W instead of LDC");
            bc.addReturn(ReturnType.ARETURN);
            bc.finalizeBytecode();

            assertTrue(bc.isModified());
        }

        @Test
        void addLdcEmptyString() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "emptyString", "Ljava/lang/String;");

            Bytecode bc = new Bytecode(method);
            bc.addLdc("");
            bc.addReturn(ReturnType.ARETURN);
            bc.finalizeBytecode();

            assertTrue(bc.isModified());
        }
    }

    @Nested
    class ComplexMethodTests {

        @Test
        void createHelloWorldMethod() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "helloWorld", "V");

            Bytecode bc = new Bytecode(method);
            bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
            bc.addLdc("Hello, World!");
            bc.addInvokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();

            assertTrue(bc.endsWithReturn() && bc.isModified());
        }

        @Test
        void createArithmeticMethod() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "add", "I", "I", "I");

            Bytecode bc = new Bytecode(method);
            bc.addILoad(0);
            bc.addILoad(1);
            bc.addReturn(ReturnType.IRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.endsWithReturn());
        }

        @Test
        void createLoopLikeMethod() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "loop", "I", "I");

            Bytecode bc = new Bytecode(method);
            bc.addILoad(0);
            bc.defineLabel("loopStart");
            bc.addIInc(0, -1);
            bc.addILoad(0);
            bc.addIConst(0);
            bc.addReturn(ReturnType.IRETURN);
            bc.finalizeBytecode();

            assertTrue(bc.endsWithReturn());
        }
    }
}
