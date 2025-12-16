package com.tonic.analysis.bytecode;

import com.tonic.analysis.Bytecode;
import com.tonic.analysis.CodeWriter;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestClassLoader;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.ReturnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Bytecode high-level manipulation API.
 * Covers instruction insertion, constant loading, method invocation, and finalization.
 */
class BytecodeTest {

    private ClassPool pool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/BytecodeTestClass", access);
    }

    // ========== Constructor Tests ==========

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

    // ========== Integer Constant Tests ==========

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

        bc.addIConst(100); // In BIPUSH range
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addIConstSipushRange() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getSipush", "I");
        Bytecode bc = new Bytecode(method);

        bc.addIConst(1000); // In SIPUSH range
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    @Test
    void addIConstLdcRange() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "getLdc", "I");
        Bytecode bc = new Bytecode(method);

        bc.addIConst(100000); // Requires LDC
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        assertTrue(bc.getCodeWriter().getBytecodeSize() > 0);
    }

    // ========== Long Constant Tests ==========

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

    // ========== Float/Double Constant Tests ==========

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

    // ========== Null Constant Test ==========

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

    // ========== Load/Store Tests ==========

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

    // ========== LDC String Test ==========

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

    // ========== IINC Test ==========

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

    // ========== Method Invocation Tests ==========

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

    // ========== Return Tests ==========

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

    // ========== Labels and Goto Tests ==========

    @Test
    void defineLabel() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "withLabel", "V");
        Bytecode bc = new Bytecode(method);

        bc.defineLabel("start");
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();

        // Label should be defined
        assertTrue(bc.getLabels().containsKey("start"));
    }

    // ========== Insert Before Tests ==========

    @Test
    void setInsertBefore() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "insertBefore", "V");
        Bytecode bc = new Bytecode(method);

        bc.addReturn(ReturnType.RETURN);
        bc.setInsertBefore(true);

        // After setting insertBefore, the offset should be calculated properly
        assertFalse(bc.isInsertBefore() && bc.getInsertBeforeOffset() < 0);
    }

    // ========== Modification State Tests ==========

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

    // ========== Generic Load Based on Type ==========

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

    // ========== Frame Computation Tests ==========

    @Test
    void computeFrames() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "withFrames", "V");
        Bytecode bc = new Bytecode(method);

        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
        bc.computeFrames();

        // No exception means success
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

        // No exception means success
        assertTrue(true);
    }
}
