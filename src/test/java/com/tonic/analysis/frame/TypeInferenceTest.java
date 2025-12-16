package com.tonic.analysis.frame;

import com.tonic.analysis.instruction.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for TypeInference class.
 * Tests all bytecode instruction types and their effects on TypeState.
 */
class TypeInferenceTest {

    private ConstPool constPool;
    private TypeInference inference;
    private TypeState initialState;

    @BeforeEach
    void setUp() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile classFile = pool.createNewClass("com/test/TypeInferenceTest", access);
        constPool = classFile.getConstPool();
        inference = new TypeInference(constPool);

        // Create initial state with empty stack and no locals
        initialState = TypeState.empty();
    }

    // ========== 1. Constant Push Instructions (0x00-0x14) ==========

    @Test
    void testNop() {
        Instruction nop = new NopInstruction(0x00, 0);
        TypeState result = inference.apply(initialState, nop);

        assertEquals(0, result.getStackSize());
        assertSame(initialState, result);
    }

    @Test
    void testAConstNull() {
        Instruction aconst = new AConstNullInstruction(0x01, 0);
        TypeState result = inference.apply(initialState, aconst);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.NULL, result.peek());
    }

    @Test
    void testIConstM1() {
        Instruction iconst = new IConstInstruction(0x02, 0, -1);
        TypeState result = inference.apply(initialState, iconst);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testIConst0Through5() {
        for (int opcode = 0x03; opcode <= 0x08; opcode++) {
            Instruction iconst = new IConstInstruction(opcode, 0, opcode - 0x03);
            TypeState result = inference.apply(initialState, iconst);

            assertEquals(1, result.getStackSize());
            assertEquals(VerificationType.INTEGER, result.peek());
        }
    }

    @Test
    void testLConst0() {
        Instruction lconst = new LConstInstruction(0x09, 0, 0L);
        TypeState result = inference.apply(initialState, lconst);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.TOP, result.peek());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testLConst1() {
        Instruction lconst = new LConstInstruction(0x0A, 0, 1L);
        TypeState result = inference.apply(initialState, lconst);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testFConst0Through2() {
        for (int opcode = 0x0B; opcode <= 0x0D; opcode++) {
            Instruction fconst = new FConstInstruction(opcode, 0, (float)(opcode - 0x0B));
            TypeState result = inference.apply(initialState, fconst);

            assertEquals(1, result.getStackSize());
            assertEquals(VerificationType.FLOAT, result.peek());
        }
    }

    @Test
    void testDConst0() {
        Instruction dconst = new DConstInstruction(0x0E, 0, 0.0);
        TypeState result = inference.apply(initialState, dconst);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.DOUBLE, result.peek(1));
    }

    @Test
    void testDConst1() {
        Instruction dconst = new DConstInstruction(0x0F, 0, 1.0);
        TypeState result = inference.apply(initialState, dconst);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.DOUBLE, result.peek(1));
    }

    @Test
    void testBipush() {
        Instruction bipush = new BipushInstruction(0x10, 0, (byte)42);
        TypeState result = inference.apply(initialState, bipush);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testSipush() {
        Instruction sipush = new SipushInstruction(0x11, 0, (short)1000);
        TypeState result = inference.apply(initialState, sipush);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testLdcInteger() {
        int intIndex = constPool.findOrAddInteger(42).getIndex(constPool);
        Instruction ldc = new LdcInstruction(constPool, 0x12, 0, intIndex);
        TypeState result = inference.apply(initialState, ldc);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testLdcFloat() {
        int floatIndex = constPool.findOrAddFloat(3.14f).getIndex(constPool);
        Instruction ldc = new LdcInstruction(constPool, 0x12, 0, floatIndex);
        TypeState result = inference.apply(initialState, ldc);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.FLOAT, result.peek());
    }

    @Test
    void testLdcString() {
        int stringIndex = constPool.findOrAddString("test").getIndex(constPool);
        Instruction ldc = new LdcInstruction(constPool, 0x12, 0, stringIndex);
        TypeState result = inference.apply(initialState, ldc);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
    }

    @Test
    void testLdcClass() {
        int classIndex = constPool.findOrAddClass("java/lang/String").getIndex(constPool);
        ClassRefItem classRef = constPool.findOrAddClass("java/lang/String");
        // Create LDC that loads a Class constant
        Instruction ldc = new LdcInstruction(constPool, 0x12, 0, classIndex);
        TypeState result = inference.apply(initialState, ldc);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
    }

    @Test
    void testLdcWLong() {
        int longIndex = constPool.findOrAddLong(42L).getIndex(constPool);
        Instruction ldc2w = new Ldc2WInstruction(constPool, 0x14, 0, longIndex);
        TypeState result = inference.apply(initialState, ldc2w);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testLdcWDouble() {
        int doubleIndex = constPool.findOrAddDouble(3.14).getIndex(constPool);
        Instruction ldc2w = new Ldc2WInstruction(constPool, 0x14, 0, doubleIndex);
        TypeState result = inference.apply(initialState, ldc2w);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.DOUBLE, result.peek(1));
    }

    // ========== 2. Load Instructions (0x15-0x2D) ==========

    @Test
    void testILoad() {
        Instruction iload = new ILoadInstruction(0x15, 0, 5);
        TypeState result = inference.apply(initialState, iload);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testILoad0Through3() {
        for (int opcode = 0x1A; opcode <= 0x1D; opcode++) {
            Instruction iload = new ILoadInstruction(opcode, 0, opcode - 0x1A);
            TypeState result = inference.apply(initialState, iload);

            assertEquals(1, result.getStackSize());
            assertEquals(VerificationType.INTEGER, result.peek());
        }
    }

    @Test
    void testLLoad() {
        Instruction lload = new LLoadInstruction(0x16, 0, 5);
        TypeState result = inference.apply(initialState, lload);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testLLoad0Through3() {
        for (int opcode = 0x1E; opcode <= 0x21; opcode++) {
            Instruction lload = new LLoadInstruction(opcode, 0, opcode - 0x1E);
            TypeState result = inference.apply(initialState, lload);

            assertEquals(2, result.getStackSize());
            assertEquals(VerificationType.LONG, result.peek(1));
        }
    }

    @Test
    void testFLoad() {
        Instruction fload = new FLoadInstruction(0x17, 0, 5);
        TypeState result = inference.apply(initialState, fload);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.FLOAT, result.peek());
    }

    @Test
    void testFLoad0Through3() {
        for (int opcode = 0x22; opcode <= 0x25; opcode++) {
            Instruction fload = new FLoadInstruction(opcode, 0, opcode - 0x22);
            TypeState result = inference.apply(initialState, fload);

            assertEquals(1, result.getStackSize());
            assertEquals(VerificationType.FLOAT, result.peek());
        }
    }

    @Test
    void testDLoad() {
        Instruction dload = new DLoadInstruction(0x18, 0, 5);
        TypeState result = inference.apply(initialState, dload);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.DOUBLE, result.peek(1));
    }

    @Test
    void testDLoad0Through3() {
        for (int opcode = 0x26; opcode <= 0x29; opcode++) {
            Instruction dload = new DLoadInstruction(opcode, 0, opcode - 0x26);
            TypeState result = inference.apply(initialState, dload);

            assertEquals(2, result.getStackSize());
            assertEquals(VerificationType.DOUBLE, result.peek(1));
        }
    }

    @Test
    void testALoad() {
        // Setup state with object in local 5
        int objClassIndex = constPool.findOrAddClass("java/lang/Object").getIndex(constPool);
        TypeState stateWithLocal = initialState.setLocal(5, VerificationType.object(objClassIndex));

        Instruction aload = new ALoadInstruction(0x19, 0, 5);
        TypeState result = inference.apply(stateWithLocal, aload);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.object(objClassIndex), result.peek());
    }

    @Test
    void testALoad0Through3() {
        int objClassIndex = constPool.findOrAddClass("java/lang/Object").getIndex(constPool);

        for (int opcode = 0x2A; opcode <= 0x2D; opcode++) {
            int localIndex = opcode - 0x2A;
            TypeState stateWithLocal = initialState.setLocal(localIndex, VerificationType.object(objClassIndex));

            Instruction aload = new ALoadInstruction(opcode, 0, localIndex);
            TypeState result = inference.apply(stateWithLocal, aload);

            assertEquals(1, result.getStackSize());
            assertEquals(VerificationType.object(objClassIndex), result.peek());
        }
    }

    // ========== 3. Array Load Instructions (0x2E-0x35) ==========

    @Test
    void testIALoad() {
        // Setup: array and index on stack
        TypeState state = initialState.push(VerificationType.object(1)).push(VerificationType.INTEGER);

        Instruction iaload = new IALoadInstruction(0x2E, 0);
        TypeState result = inference.apply(state, iaload);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testLALoad() {
        TypeState state = initialState.push(VerificationType.object(1)).push(VerificationType.INTEGER);

        Instruction laload = new LALoadInstruction(0x2F, 0);
        TypeState result = inference.apply(state, laload);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testFALoad() {
        TypeState state = initialState.push(VerificationType.object(1)).push(VerificationType.INTEGER);

        Instruction faload = new FALoadInstruction(0x30, 0);
        TypeState result = inference.apply(state, faload);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.FLOAT, result.peek());
    }

    @Test
    void testDALoad() {
        TypeState state = initialState.push(VerificationType.object(1)).push(VerificationType.INTEGER);

        Instruction daload = new DALoadInstruction(0x31, 0);
        TypeState result = inference.apply(state, daload);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.DOUBLE, result.peek(1));
    }

    @Test
    void testAALoad() {
        int arrayClassIndex = constPool.findOrAddClass("[Ljava/lang/Object;").getIndex(constPool);
        TypeState state = initialState
                .push(VerificationType.object(arrayClassIndex))
                .push(VerificationType.INTEGER);

        Instruction aaload = new AALoadInstruction(0x32, 0);
        TypeState result = inference.apply(state, aaload);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
    }

    @Test
    void testBALoad() {
        TypeState state = initialState.push(VerificationType.object(1)).push(VerificationType.INTEGER);

        Instruction baload = new BALOADInstruction(0x33, 0);
        TypeState result = inference.apply(state, baload);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testCALoad() {
        TypeState state = initialState.push(VerificationType.object(1)).push(VerificationType.INTEGER);

        Instruction caload = new CALoadInstruction(0x34, 0);
        TypeState result = inference.apply(state, caload);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testSALoad() {
        TypeState state = initialState.push(VerificationType.object(1)).push(VerificationType.INTEGER);

        Instruction saload = new SALoadInstruction(0x35, 0);
        TypeState result = inference.apply(state, saload);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    // ========== 4. Store Instructions (0x36-0x4E) ==========

    @Test
    void testIStore() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction istore = new IStoreInstruction(0x36, 0, 5);
        TypeState result = inference.apply(state, istore);

        assertEquals(0, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.getLocal(5));
    }

    @Test
    void testIStore0Through3() {
        for (int opcode = 0x3B; opcode <= 0x3E; opcode++) {
            TypeState state = initialState.push(VerificationType.INTEGER);
            int localIndex = opcode - 0x3B;

            Instruction istore = new IStoreInstruction(opcode, 0, localIndex);
            TypeState result = inference.apply(state, istore);

            assertEquals(0, result.getStackSize());
            assertEquals(VerificationType.INTEGER, result.getLocal(localIndex));
        }
    }

    @Test
    void testLStore() {
        TypeState state = initialState.push(VerificationType.LONG);

        Instruction lstore = new LStoreInstruction(0x37, 0, 5);
        TypeState result = inference.apply(state, lstore);

        assertEquals(0, result.getStackSize());
        assertEquals(VerificationType.LONG, result.getLocal(5));
        assertEquals(VerificationType.TOP, result.getLocal(6));
    }

    @Test
    void testLStore0Through3() {
        for (int opcode = 0x3F; opcode <= 0x42; opcode++) {
            TypeState state = initialState.push(VerificationType.LONG);
            int localIndex = opcode - 0x3F;

            Instruction lstore = new LStoreInstruction(opcode, 0, localIndex);
            TypeState result = inference.apply(state, lstore);

            assertEquals(0, result.getStackSize());
            assertEquals(VerificationType.LONG, result.getLocal(localIndex));
        }
    }

    @Test
    void testFStore() {
        TypeState state = initialState.push(VerificationType.FLOAT);

        Instruction fstore = new FStoreInstruction(0x38, 0, 5);
        TypeState result = inference.apply(state, fstore);

        assertEquals(0, result.getStackSize());
        assertEquals(VerificationType.FLOAT, result.getLocal(5));
    }

    @Test
    void testFStore0Through3() {
        for (int opcode = 0x43; opcode <= 0x46; opcode++) {
            TypeState state = initialState.push(VerificationType.FLOAT);
            int localIndex = opcode - 0x43;

            Instruction fstore = new FStoreInstruction(opcode, 0, localIndex);
            TypeState result = inference.apply(state, fstore);

            assertEquals(0, result.getStackSize());
            assertEquals(VerificationType.FLOAT, result.getLocal(localIndex));
        }
    }

    @Test
    void testDStore() {
        TypeState state = initialState.push(VerificationType.DOUBLE);

        Instruction dstore = new DStoreInstruction(0x39, 0, 5);
        TypeState result = inference.apply(state, dstore);

        assertEquals(0, result.getStackSize());
        assertEquals(VerificationType.DOUBLE, result.getLocal(5));
        assertEquals(VerificationType.TOP, result.getLocal(6));
    }

    @Test
    void testDStore0Through3() {
        for (int opcode = 0x47; opcode <= 0x4A; opcode++) {
            TypeState state = initialState.push(VerificationType.DOUBLE);
            int localIndex = opcode - 0x47;

            Instruction dstore = new DStoreInstruction(opcode, 0, localIndex);
            TypeState result = inference.apply(state, dstore);

            assertEquals(0, result.getStackSize());
            assertEquals(VerificationType.DOUBLE, result.getLocal(localIndex));
        }
    }

    @Test
    void testAStore() {
        int objClassIndex = constPool.findOrAddClass("java/lang/Object").getIndex(constPool);
        TypeState state = initialState.push(VerificationType.object(objClassIndex));

        Instruction astore = new AStoreInstruction(0x3A, 0, 5);
        TypeState result = inference.apply(state, astore);

        assertEquals(0, result.getStackSize());
        assertEquals(VerificationType.object(objClassIndex), result.getLocal(5));
    }

    @Test
    void testAStore0Through3() {
        int objClassIndex = constPool.findOrAddClass("java/lang/Object").getIndex(constPool);

        for (int opcode = 0x4B; opcode <= 0x4E; opcode++) {
            TypeState state = initialState.push(VerificationType.object(objClassIndex));
            int localIndex = opcode - 0x4B;

            Instruction astore = new AStoreInstruction(opcode, 0, localIndex);
            TypeState result = inference.apply(state, astore);

            assertEquals(0, result.getStackSize());
            assertEquals(VerificationType.object(objClassIndex), result.getLocal(localIndex));
        }
    }

    // ========== 5. Array Store Instructions (0x4F-0x56) ==========

    @Test
    void testIAStore() {
        // Setup: array, index, value on stack
        TypeState state = initialState
                .push(VerificationType.object(1))
                .push(VerificationType.INTEGER)
                .push(VerificationType.INTEGER);

        Instruction iastore = new IAStoreInstruction(0x4F, 0);
        TypeState result = inference.apply(state, iastore);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testLAStore() {
        TypeState state = initialState
                .push(VerificationType.object(1))
                .push(VerificationType.INTEGER)
                .push(VerificationType.LONG);

        Instruction lastore = new LAStoreInstruction(0x50, 0);
        TypeState result = inference.apply(state, lastore);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testFAStore() {
        TypeState state = initialState
                .push(VerificationType.object(1))
                .push(VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        Instruction fastore = new FAStoreInstruction(0x51, 0);
        TypeState result = inference.apply(state, fastore);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testDAStore() {
        TypeState state = initialState
                .push(VerificationType.object(1))
                .push(VerificationType.INTEGER)
                .push(VerificationType.DOUBLE);

        Instruction dastore = new DAStoreInstruction(0x52, 0);
        TypeState result = inference.apply(state, dastore);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testAAStore() {
        TypeState state = initialState
                .push(VerificationType.object(1))
                .push(VerificationType.INTEGER)
                .push(VerificationType.object(2));

        Instruction aastore = new AAStoreInstruction(0x53, 0);
        TypeState result = inference.apply(state, aastore);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testBAStore() {
        TypeState state = initialState
                .push(VerificationType.object(1))
                .push(VerificationType.INTEGER)
                .push(VerificationType.INTEGER);

        Instruction bastore = new BAStoreInstruction(0x54, 0);
        TypeState result = inference.apply(state, bastore);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testCAStore() {
        TypeState state = initialState
                .push(VerificationType.object(1))
                .push(VerificationType.INTEGER)
                .push(VerificationType.INTEGER);

        Instruction castore = new CAStoreInstruction(0x55, 0);
        TypeState result = inference.apply(state, castore);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testSAStore() {
        TypeState state = initialState
                .push(VerificationType.object(1))
                .push(VerificationType.INTEGER)
                .push(VerificationType.INTEGER);

        Instruction sastore = new SAStoreInstruction(0x56, 0);
        TypeState result = inference.apply(state, sastore);

        assertEquals(0, result.getStackSize());
    }

    // ========== 6. Stack Manipulation (0x57-0x5F) ==========

    @Test
    void testPop() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction pop = new PopInstruction(0x57, 0);
        TypeState result = inference.apply(state, pop);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testPop2() {
        TypeState state = initialState
                .push(VerificationType.INTEGER)
                .push(VerificationType.INTEGER);

        Instruction pop2 = new Pop2Instruction(0x58, 0);
        TypeState result = inference.apply(state, pop2);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testDup() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction dup = new DupInstruction(0x59, 0);
        TypeState result = inference.apply(state, dup);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek(0));
        assertEquals(VerificationType.INTEGER, result.peek(1));
    }

    @Test
    void testDupX1() {
        TypeState state = initialState
                .push(VerificationType.FLOAT)
                .push(VerificationType.INTEGER);

        Instruction dupx1 = new DupInstruction(0x5A, 0);
        TypeState result = inference.apply(state, dupx1);

        assertEquals(3, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek(0));
        assertEquals(VerificationType.FLOAT, result.peek(1));
        assertEquals(VerificationType.INTEGER, result.peek(2));
    }

    @Test
    void testDupX2() {
        TypeState state = initialState
                .push(VerificationType.FLOAT)
                .push(VerificationType.FLOAT)
                .push(VerificationType.INTEGER);

        Instruction dupx2 = new DupInstruction(0x5B, 0);
        TypeState result = inference.apply(state, dupx2);

        assertEquals(4, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek(0));
        assertEquals(VerificationType.FLOAT, result.peek(1));
        assertEquals(VerificationType.FLOAT, result.peek(2));
        assertEquals(VerificationType.INTEGER, result.peek(3));
    }

    @Test
    void testDup2() {
        TypeState state = initialState
                .push(VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        Instruction dup2 = new DupInstruction(0x5C, 0);
        TypeState result = inference.apply(state, dup2);

        assertEquals(4, result.getStackSize());
        assertEquals(VerificationType.FLOAT, result.peek(0));
        assertEquals(VerificationType.INTEGER, result.peek(1));
        assertEquals(VerificationType.FLOAT, result.peek(2));
        assertEquals(VerificationType.INTEGER, result.peek(3));
    }

    @Test
    void testDup2X1() {
        TypeState state = initialState
                .push(VerificationType.FLOAT)
                .push(VerificationType.INTEGER)
                .push(VerificationType.INTEGER);

        Instruction dup2x1 = new DupInstruction(0x5D, 0);
        TypeState result = inference.apply(state, dup2x1);

        assertEquals(5, result.getStackSize());
    }

    @Test
    void testDup2X2() {
        TypeState state = initialState
                .push(VerificationType.FLOAT)
                .push(VerificationType.FLOAT)
                .push(VerificationType.INTEGER)
                .push(VerificationType.INTEGER);

        Instruction dup2x2 = new DupInstruction(0x5E, 0);
        TypeState result = inference.apply(state, dup2x2);

        assertEquals(6, result.getStackSize());
    }

    @Test
    void testSwap() {
        TypeState state = initialState
                .push(VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        Instruction swap = new SwapInstruction(0x5F, 0);
        TypeState result = inference.apply(state, swap);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek(0));
        assertEquals(VerificationType.FLOAT, result.peek(1));
    }

    // ========== 7. Arithmetic Operations (0x60-0x84) ==========

    @Test
    void testIAdd() {
        TypeState state = initialState
                .push(VerificationType.INTEGER)
                .push(VerificationType.INTEGER);

        Instruction iadd = new ArithmeticInstruction(0x60, 0);
        TypeState result = inference.apply(state, iadd);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testLAdd() {
        TypeState state = initialState
                .push(VerificationType.LONG)
                .push(VerificationType.LONG);

        Instruction ladd = new ArithmeticInstruction(0x61, 0);
        TypeState result = inference.apply(state, ladd);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testFAdd() {
        TypeState state = initialState
                .push(VerificationType.FLOAT)
                .push(VerificationType.FLOAT);

        Instruction fadd = new ArithmeticInstruction(0x62, 0);
        TypeState result = inference.apply(state, fadd);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.FLOAT, result.peek());
    }

    @Test
    void testDAdd() {
        TypeState state = initialState
                .push(VerificationType.DOUBLE)
                .push(VerificationType.DOUBLE);

        Instruction dadd = new ArithmeticInstruction(0x63, 0);
        TypeState result = inference.apply(state, dadd);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.DOUBLE, result.peek(1));
    }

    @Test
    void testIntegerArithmeticOps() {
        int[] opcodes = {0x60, 0x64, 0x68, 0x6C, 0x70}; // iadd, isub, imul, idiv, irem

        for (int opcode : opcodes) {
            TypeState state = initialState
                    .push(VerificationType.INTEGER)
                    .push(VerificationType.INTEGER);

            Instruction instr = new ArithmeticInstruction(opcode, 0);
            TypeState result = inference.apply(state, instr);

            assertEquals(1, result.getStackSize());
            assertEquals(VerificationType.INTEGER, result.peek());
        }
    }

    @Test
    void testIntegerLogicalOps() {
        // Test iand (0x7E), ior (0x80), ixor (0x82) using specific instruction classes
        TypeState state = initialState
                .push(VerificationType.INTEGER)
                .push(VerificationType.INTEGER);

        // iand
        Instruction iand = new IAndInstruction(0x7E, 0);
        TypeState result = inference.apply(state, iand);
        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());

        // ior
        state = initialState.push(VerificationType.INTEGER).push(VerificationType.INTEGER);
        Instruction ior = new IOrInstruction(0x80, 0);
        result = inference.apply(state, ior);
        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());

        // ixor
        state = initialState.push(VerificationType.INTEGER).push(VerificationType.INTEGER);
        Instruction ixor = new IXorInstruction(0x82, 0);
        result = inference.apply(state, ixor);
        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testLongArithmeticOps() {
        int[] opcodes = {0x61, 0x65, 0x69, 0x6D, 0x71}; // ladd, lsub, lmul, ldiv, lrem

        for (int opcode : opcodes) {
            TypeState state = initialState
                    .push(VerificationType.LONG)
                    .push(VerificationType.LONG);

            Instruction instr = new ArithmeticInstruction(opcode, 0);
            TypeState result = inference.apply(state, instr);

            assertEquals(2, result.getStackSize());
            assertEquals(VerificationType.LONG, result.peek(1));
        }
    }

    @Test
    void testLongLogicalOps() {
        // Test land (0x7F), lor (0x81), lxor (0x83) using specific instruction classes
        TypeState state = initialState
                .push(VerificationType.LONG)
                .push(VerificationType.LONG);

        // land
        Instruction land = new LandInstruction(0x7F, 0);
        TypeState result = inference.apply(state, land);
        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));

        // lor
        state = initialState.push(VerificationType.LONG).push(VerificationType.LONG);
        Instruction lor = new LorInstruction(0x81, 0);
        result = inference.apply(state, lor);
        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));

        // lxor
        state = initialState.push(VerificationType.LONG).push(VerificationType.LONG);
        Instruction lxor = new LXorInstruction(0x83, 0);
        result = inference.apply(state, lxor);
        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testFloatArithmeticOps() {
        int[] opcodes = {0x66, 0x6A, 0x6E, 0x72}; // fsub, fmul, fdiv, frem

        for (int opcode : opcodes) {
            TypeState state = initialState
                    .push(VerificationType.FLOAT)
                    .push(VerificationType.FLOAT);

            Instruction instr = new ArithmeticInstruction(opcode, 0);
            TypeState result = inference.apply(state, instr);

            assertEquals(1, result.getStackSize());
            assertEquals(VerificationType.FLOAT, result.peek());
        }
    }

    @Test
    void testDoubleArithmeticOps() {
        int[] opcodes = {0x67, 0x6B, 0x6F, 0x73}; // dsub, dmul, ddiv, drem

        for (int opcode : opcodes) {
            TypeState state = initialState
                    .push(VerificationType.DOUBLE)
                    .push(VerificationType.DOUBLE);

            Instruction instr = new ArithmeticInstruction(opcode, 0);
            TypeState result = inference.apply(state, instr);

            assertEquals(2, result.getStackSize());
            assertEquals(VerificationType.DOUBLE, result.peek(1));
        }
    }

    @Test
    void testINeg() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction ineg = new INegInstruction(0x74, 0);
        TypeState result = inference.apply(state, ineg);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testLNeg() {
        TypeState state = initialState.push(VerificationType.LONG);

        Instruction lneg = new LNegInstruction(0x75, 0);
        TypeState result = inference.apply(state, lneg);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testFNeg() {
        TypeState state = initialState.push(VerificationType.FLOAT);

        Instruction fneg = new FNegInstruction(0x76, 0);
        TypeState result = inference.apply(state, fneg);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.FLOAT, result.peek());
    }

    @Test
    void testDNeg() {
        TypeState state = initialState.push(VerificationType.DOUBLE);

        Instruction dneg = new DNegInstruction(0x77, 0);
        TypeState result = inference.apply(state, dneg);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.DOUBLE, result.peek(1));
    }

    @Test
    void testIShiftOps() {
        int[] opcodes = {0x78, 0x7A, 0x7C}; // ishl, ishr, iushr

        for (int opcode : opcodes) {
            TypeState state = initialState
                    .push(VerificationType.INTEGER)
                    .push(VerificationType.INTEGER);

            Instruction instr = new ArithmeticShiftInstruction(opcode, 0);
            TypeState result = inference.apply(state, instr);

            assertEquals(1, result.getStackSize());
            assertEquals(VerificationType.INTEGER, result.peek());
        }
    }

    @Test
    void testLShiftOps() {
        int[] opcodes = {0x79, 0x7B, 0x7D}; // lshl, lshr, lushr

        for (int opcode : opcodes) {
            TypeState state = initialState
                    .push(VerificationType.LONG)
                    .push(VerificationType.INTEGER);

            Instruction instr = new ArithmeticShiftInstruction(opcode, 0);
            TypeState result = inference.apply(state, instr);

            assertEquals(2, result.getStackSize());
            assertEquals(VerificationType.LONG, result.peek(1));
        }
    }

    @Test
    void testIInc() {
        TypeState state = initialState.setLocal(5, VerificationType.INTEGER);

        Instruction iinc = new IIncInstruction(0x84, 0, 5, 1);
        TypeState result = inference.apply(state, iinc);

        assertEquals(0, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.getLocal(5));
    }

    // ========== 8. Type Conversions (0x85-0x93) ==========

    @Test
    void testI2L() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction i2l = new I2LInstruction(0x85, 0);
        TypeState result = inference.apply(state, i2l);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testI2F() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction i2f = new ConversionInstruction(0x86, 0);
        TypeState result = inference.apply(state, i2f);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.FLOAT, result.peek());
    }

    @Test
    void testI2D() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction i2d = new ConversionInstruction(0x87, 0);
        TypeState result = inference.apply(state, i2d);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.DOUBLE, result.peek(1));
    }

    @Test
    void testL2I() {
        TypeState state = initialState.push(VerificationType.LONG);

        Instruction l2i = new ConversionInstruction(0x88, 0);
        TypeState result = inference.apply(state, l2i);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testL2F() {
        TypeState state = initialState.push(VerificationType.LONG);

        Instruction l2f = new ConversionInstruction(0x89, 0);
        TypeState result = inference.apply(state, l2f);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.FLOAT, result.peek());
    }

    @Test
    void testL2D() {
        TypeState state = initialState.push(VerificationType.LONG);

        Instruction l2d = new ConversionInstruction(0x8A, 0);
        TypeState result = inference.apply(state, l2d);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.DOUBLE, result.peek(1));
    }

    @Test
    void testF2I() {
        TypeState state = initialState.push(VerificationType.FLOAT);

        Instruction f2i = new ConversionInstruction(0x8B, 0);
        TypeState result = inference.apply(state, f2i);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testF2L() {
        TypeState state = initialState.push(VerificationType.FLOAT);

        Instruction f2l = new ConversionInstruction(0x8C, 0);
        TypeState result = inference.apply(state, f2l);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testF2D() {
        TypeState state = initialState.push(VerificationType.FLOAT);

        Instruction f2d = new ConversionInstruction(0x8D, 0);
        TypeState result = inference.apply(state, f2d);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.DOUBLE, result.peek(1));
    }

    @Test
    void testD2I() {
        TypeState state = initialState.push(VerificationType.DOUBLE);

        Instruction d2i = new ConversionInstruction(0x8E, 0);
        TypeState result = inference.apply(state, d2i);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testD2L() {
        TypeState state = initialState.push(VerificationType.DOUBLE);

        Instruction d2l = new ConversionInstruction(0x8F, 0);
        TypeState result = inference.apply(state, d2l);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testD2F() {
        TypeState state = initialState.push(VerificationType.DOUBLE);

        Instruction d2f = new ConversionInstruction(0x90, 0);
        TypeState result = inference.apply(state, d2f);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.FLOAT, result.peek());
    }

    @Test
    void testI2B() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction i2b = new NarrowingConversionInstruction(0x91, 0);
        TypeState result = inference.apply(state, i2b);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testI2C() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction i2c = new NarrowingConversionInstruction(0x92, 0);
        TypeState result = inference.apply(state, i2c);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testI2S() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction i2s = new NarrowingConversionInstruction(0x93, 0);
        TypeState result = inference.apply(state, i2s);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    // ========== 9. Comparisons (0x94-0x98) ==========

    @Test
    void testLCmp() {
        TypeState state = initialState
                .push(VerificationType.LONG)
                .push(VerificationType.LONG);

        Instruction lcmp = new CompareInstruction(0x94, 0);
        TypeState result = inference.apply(state, lcmp);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testFCmpL() {
        TypeState state = initialState
                .push(VerificationType.FLOAT)
                .push(VerificationType.FLOAT);

        Instruction fcmpl = new CompareInstruction(0x95, 0);
        TypeState result = inference.apply(state, fcmpl);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testFCmpG() {
        TypeState state = initialState
                .push(VerificationType.FLOAT)
                .push(VerificationType.FLOAT);

        Instruction fcmpg = new CompareInstruction(0x96, 0);
        TypeState result = inference.apply(state, fcmpg);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testDCmpL() {
        TypeState state = initialState
                .push(VerificationType.DOUBLE)
                .push(VerificationType.DOUBLE);

        Instruction dcmpl = new CompareInstruction(0x97, 0);
        TypeState result = inference.apply(state, dcmpl);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testDCmpG() {
        TypeState state = initialState
                .push(VerificationType.DOUBLE)
                .push(VerificationType.DOUBLE);

        Instruction dcmpg = new CompareInstruction(0x98, 0);
        TypeState result = inference.apply(state, dcmpg);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    // ========== 10. Control Flow (0x99-0xAB) ==========

    @Test
    void testConditionalBranchesOneOperand() {
        int[] opcodes = {0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E}; // ifeq, ifne, iflt, ifge, ifgt, ifle

        for (int opcode : opcodes) {
            TypeState state = initialState.push(VerificationType.INTEGER);

            Instruction branch = new ConditionalBranchInstruction(opcode, 0, (short)10);
            TypeState result = inference.apply(state, branch);

            assertEquals(0, result.getStackSize());
        }
    }

    @Test
    void testConditionalBranchesTwoOperands() {
        int[] opcodes = {0x9F, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4}; // if_icmpeq, if_icmpne, if_icmplt, if_icmpge, if_icmpgt, if_icmple

        for (int opcode : opcodes) {
            TypeState state = initialState
                    .push(VerificationType.INTEGER)
                    .push(VerificationType.INTEGER);

            Instruction branch = new ConditionalBranchInstruction(opcode, 0, (short)10);
            TypeState result = inference.apply(state, branch);

            assertEquals(0, result.getStackSize());
        }
    }

    @Test
    void testIfACmpEq() {
        TypeState state = initialState
                .push(VerificationType.object(1))
                .push(VerificationType.object(1));

        Instruction branch = new ConditionalBranchInstruction(0xA5, 0, (short)10);
        TypeState result = inference.apply(state, branch);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testIfACmpNe() {
        TypeState state = initialState
                .push(VerificationType.object(1))
                .push(VerificationType.object(1));

        Instruction branch = new ConditionalBranchInstruction(0xA6, 0, (short)10);
        TypeState result = inference.apply(state, branch);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testGoto() {
        Instruction gotoInstr = new GotoInstruction(0xA7, 0, (short)10);
        TypeState result = inference.apply(initialState, gotoInstr);

        assertEquals(0, result.getStackSize());
        assertSame(initialState, result);
    }

    @Test
    void testJsr() {
        Instruction jsr = new JsrInstruction(0xA8, 0, 10);
        TypeState result = inference.apply(initialState, jsr);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.TOP, result.peek());
    }

    @Test
    void testRet() {
        Instruction ret = new RetInstruction(0xA9, 0, 5);
        TypeState result = inference.apply(initialState, ret);

        assertSame(initialState, result);
    }

    @Test
    void testTableSwitch() {
        java.util.Map<Integer, Integer> jumpOffsets = new java.util.HashMap<>();
        jumpOffsets.put(0, 15);
        jumpOffsets.put(1, 20);
        jumpOffsets.put(2, 25);
        Instruction tableswitch = new TableSwitchInstruction(0xAA, 0, 0, 10, 0, 2, jumpOffsets);
        TypeState state = initialState.push(VerificationType.INTEGER);

        TypeState result = inference.apply(state, tableswitch);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testLookupSwitch() {
        java.util.Map<Integer, Integer> matchOffsets = new java.util.HashMap<>();
        matchOffsets.put(1, 15);
        matchOffsets.put(2, 20);
        Instruction lookupswitch = new LookupSwitchInstruction(0xAB, 0, 0, 10, 2, matchOffsets);
        TypeState state = initialState.push(VerificationType.INTEGER);

        TypeState result = inference.apply(state, lookupswitch);

        assertEquals(0, result.getStackSize());
    }

    // ========== 11. Returns (0xAC-0xB1) ==========

    @Test
    void testIReturn() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction ireturn = new ReturnInstruction(0xAC, 0);
        TypeState result = inference.apply(state, ireturn);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testLReturn() {
        TypeState state = initialState.push(VerificationType.LONG);

        Instruction lreturn = new ReturnInstruction(0xAD, 0);
        TypeState result = inference.apply(state, lreturn);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testFReturn() {
        TypeState state = initialState.push(VerificationType.FLOAT);

        Instruction freturn = new ReturnInstruction(0xAE, 0);
        TypeState result = inference.apply(state, freturn);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testDReturn() {
        TypeState state = initialState.push(VerificationType.DOUBLE);

        Instruction dreturn = new ReturnInstruction(0xAF, 0);
        TypeState result = inference.apply(state, dreturn);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testAReturn() {
        TypeState state = initialState.push(VerificationType.object(1));

        Instruction areturn = new ReturnInstruction(0xB0, 0);
        TypeState result = inference.apply(state, areturn);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testReturn() {
        TypeState state = initialState.push(VerificationType.INTEGER).push(VerificationType.FLOAT);

        Instruction returnInstr = new ReturnInstruction(0xB1, 0);
        TypeState result = inference.apply(state, returnInstr);

        assertEquals(0, result.getStackSize());
    }

    // ========== 12. Field Access (0xB2-0xB5) ==========

    @Test
    void testGetStatic() {
        // Create field reference with integer type
        FieldRefItem fieldRef = constPool.findOrAddFieldRef("com/test/Test", "testField", "I");
        int fieldIndex = fieldRef.getIndex(constPool);

        Instruction getstatic = new GetFieldInstruction(constPool, 0xB2, 0, fieldIndex);
        TypeState result = inference.apply(initialState, getstatic);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testGetStaticLong() {
        // Create field reference with long type
        FieldRefItem fieldRef = constPool.findOrAddFieldRef("com/test/Test", "testField", "J");
        int fieldIndex = fieldRef.getIndex(constPool);

        Instruction getstatic = new GetFieldInstruction(constPool, 0xB2, 0, fieldIndex);
        TypeState result = inference.apply(initialState, getstatic);

        assertEquals(2, result.getStackSize());
        assertEquals(VerificationType.LONG, result.peek(1));
    }

    @Test
    void testPutStatic() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        FieldRefItem fieldRef = constPool.findOrAddFieldRef("com/test/Test", "testField", "I");
        int fieldIndex = fieldRef.getIndex(constPool);

        Instruction putstatic = new PutFieldInstruction(constPool, 0xB3, 0, fieldIndex);
        TypeState result = inference.apply(state, putstatic);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testGetField() {
        int objClassIndex = constPool.findOrAddClass("com/test/Test").getIndex(constPool);
        TypeState state = initialState.push(VerificationType.object(objClassIndex));

        FieldRefItem fieldRef = constPool.findOrAddFieldRef("com/test/Test", "testField", "I");
        int fieldIndex = fieldRef.getIndex(constPool);

        Instruction getfield = new GetFieldInstruction(constPool, 0xB4, 0, fieldIndex);
        TypeState result = inference.apply(state, getfield);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testPutField() {
        int objClassIndex = constPool.findOrAddClass("com/test/Test").getIndex(constPool);
        TypeState state = initialState
                .push(VerificationType.object(objClassIndex))
                .push(VerificationType.INTEGER);

        FieldRefItem fieldRef = constPool.findOrAddFieldRef("com/test/Test", "testField", "I");
        int fieldIndex = fieldRef.getIndex(constPool);

        Instruction putfield = new PutFieldInstruction(constPool, 0xB5, 0, fieldIndex);
        TypeState result = inference.apply(state, putfield);

        assertEquals(0, result.getStackSize());
    }

    // ========== 13. Method Invocation (0xB6-0xBA) ==========

    @Test
    void testInvokeVirtual() {
        int classIndex = constPool.findOrAddClass("java/lang/Object").getIndex(constPool);
        TypeState state = initialState.push(VerificationType.object(classIndex));

        int nameIndex = constPool.findOrAddUtf8("toString").getIndex(constPool);
        int descIndex = constPool.findOrAddUtf8("()Ljava/lang/String;").getIndex(constPool);
        int natIndex = constPool.findOrAddNameAndType(nameIndex, descIndex).getIndex(constPool);
        int methodIndex = constPool.findOrAddMethodRef(classIndex, natIndex).getIndex(constPool);

        Instruction invoke = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);
        TypeState result = inference.apply(state, invoke);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
    }

    @Test
    void testInvokeVirtualWithArgs() {
        int classIndex = constPool.findOrAddClass("java/lang/StringBuilder").getIndex(constPool);
        TypeState state = initialState
                .push(VerificationType.object(classIndex))
                .push(VerificationType.INTEGER);

        int nameIndex = constPool.findOrAddUtf8("append").getIndex(constPool);
        int descIndex = constPool.findOrAddUtf8("(I)Ljava/lang/StringBuilder;").getIndex(constPool);
        int natIndex = constPool.findOrAddNameAndType(nameIndex, descIndex).getIndex(constPool);
        int methodIndex = constPool.findOrAddMethodRef(classIndex, natIndex).getIndex(constPool);

        Instruction invoke = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);
        TypeState result = inference.apply(state, invoke);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
    }

    @Test
    void testInvokeSpecial() {
        int classIndex = constPool.findOrAddClass("java/lang/Object").getIndex(constPool);
        TypeState state = initialState.push(VerificationType.UNINITIALIZED_THIS);

        int nameIndex = constPool.findOrAddUtf8("<init>").getIndex(constPool);
        int descIndex = constPool.findOrAddUtf8("()V").getIndex(constPool);
        int natIndex = constPool.findOrAddNameAndType(nameIndex, descIndex).getIndex(constPool);
        int methodIndex = constPool.findOrAddMethodRef(classIndex, natIndex).getIndex(constPool);

        Instruction invoke = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);
        TypeState result = inference.apply(state, invoke);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testInvokeStatic() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        int classIndex = constPool.findOrAddClass("java/lang/Integer").getIndex(constPool);
        int nameIndex = constPool.findOrAddUtf8("valueOf").getIndex(constPool);
        int descIndex = constPool.findOrAddUtf8("(I)Ljava/lang/Integer;").getIndex(constPool);
        int natIndex = constPool.findOrAddNameAndType(nameIndex, descIndex).getIndex(constPool);
        int methodIndex = constPool.findOrAddMethodRef(classIndex, natIndex).getIndex(constPool);

        Instruction invoke = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);
        TypeState result = inference.apply(state, invoke);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
    }

    @Test
    void testInvokeInterface() {
        int classIndex = constPool.findOrAddClass("java/util/List").getIndex(constPool);
        TypeState state = initialState.push(VerificationType.object(classIndex));

        int nameIndex = constPool.findOrAddUtf8("size").getIndex(constPool);
        int descIndex = constPool.findOrAddUtf8("()I").getIndex(constPool);
        int natIndex = constPool.findOrAddNameAndType(nameIndex, descIndex).getIndex(constPool);
        int methodIndex = constPool.findOrAddInterfaceRef(classIndex, natIndex).getIndex(constPool);

        Instruction invoke = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 1);
        TypeState result = inference.apply(state, invoke);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testInvokeDynamic() {
        int nameIndex = constPool.findOrAddUtf8("test").getIndex(constPool);
        int descIndex = constPool.findOrAddUtf8("()Ljava/lang/String;").getIndex(constPool);
        int natIndex = constPool.findOrAddNameAndType(nameIndex, descIndex).getIndex(constPool);

        // Create an InvokeDynamicItem with bootstrap method attr index 0 and the name and type index
        InvokeDynamicItem indyItem = new InvokeDynamicItem(constPool, 0, natIndex);
        int indyIndex = constPool.addItem(indyItem);

        Instruction invoke = new InvokeDynamicInstruction(constPool, 0xBA, 0, indyIndex);
        TypeState result = inference.apply(initialState, invoke);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
    }

    // ========== 14. Object Operations (0xBB-0xC5) ==========

    @Test
    void testNew() {
        Instruction newInstr = new NewInstruction(constPool, 0xBB, 0,
                constPool.findOrAddClass("java/lang/Object").getIndex(constPool));
        TypeState result = inference.apply(initialState, newInstr);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.UninitializedType);
        assertEquals(0, ((VerificationType.UninitializedType) result.peek()).getNewInstructionOffset());
    }

    @Test
    void testNewArray() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction newarray = new NewArrayInstruction(0xBC, 0, 10, 0); // T_INT
        TypeState result = inference.apply(state, newarray);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
    }

    @Test
    void testANewArray() {
        TypeState state = initialState.push(VerificationType.INTEGER);

        int classIndex = constPool.findOrAddClass("java/lang/String").getIndex(constPool);
        Instruction anewarray = new ANewArrayInstruction(constPool, 0xBD, 0, classIndex, 0);
        TypeState result = inference.apply(state, anewarray);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
    }

    @Test
    void testArrayLength() {
        TypeState state = initialState.push(VerificationType.object(1));

        Instruction arraylength = new ArrayLengthInstruction(0xBE, 0);
        TypeState result = inference.apply(state, arraylength);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testAThrow() {
        TypeState state = initialState.push(VerificationType.object(1));

        Instruction athrow = new ATHROWInstruction(0xBF, 0);
        TypeState result = inference.apply(state, athrow);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testCheckCast() {
        TypeState state = initialState.push(VerificationType.object(1));

        int classIndex = constPool.findOrAddClass("java/lang/String").getIndex(constPool);
        Instruction checkcast = new CheckCastInstruction(constPool, 0xC0, 0, classIndex);
        TypeState result = inference.apply(state, checkcast);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
        assertEquals(classIndex, ((VerificationType.ObjectType) result.peek()).getClassIndex());
    }

    @Test
    void testInstanceOf() {
        TypeState state = initialState.push(VerificationType.object(1));

        int classIndex = constPool.findOrAddClass("java/lang/String").getIndex(constPool);
        Instruction instanceof_ = new InstanceOfInstruction(constPool, 0xC1, 0, classIndex);
        TypeState result = inference.apply(state, instanceof_);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.INTEGER, result.peek());
    }

    @Test
    void testMonitorEnter() {
        TypeState state = initialState.push(VerificationType.object(1));

        Instruction monitorenter = new MonitorEnterInstruction(0xC2, 0);
        TypeState result = inference.apply(state, monitorenter);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testMonitorExit() {
        TypeState state = initialState.push(VerificationType.object(1));

        Instruction monitorexit = new MonitorExitInstruction(0xC3, 0);
        TypeState result = inference.apply(state, monitorexit);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testWide() {
        Instruction wide = new WideInstruction(0xC4, 0, com.tonic.utill.Opcode.ILOAD, 300);
        TypeState result = inference.apply(initialState, wide);

        assertSame(initialState, result);
    }

    @Test
    void testMultiANewArray() {
        TypeState state = initialState
                .push(VerificationType.INTEGER)
                .push(VerificationType.INTEGER);

        int classIndex = constPool.findOrAddClass("[[Ljava/lang/Object;").getIndex(constPool);
        Instruction multianewarray = new MultiANewArrayInstruction(constPool, 0xC5, 0, classIndex, 2);
        TypeState result = inference.apply(state, multianewarray);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
        assertEquals(classIndex, ((VerificationType.ObjectType) result.peek()).getClassIndex());
    }

    @Test
    void testIfNull() {
        TypeState state = initialState.push(VerificationType.object(1));

        Instruction ifnull = new ConditionalBranchInstruction(0xC6, 0, (short)10);
        TypeState result = inference.apply(state, ifnull);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testIfNonNull() {
        TypeState state = initialState.push(VerificationType.object(1));

        Instruction ifnonnull = new ConditionalBranchInstruction(0xC7, 0, (short)10);
        TypeState result = inference.apply(state, ifnonnull);

        assertEquals(0, result.getStackSize());
    }

    @Test
    void testGotoW() {
        Instruction gotow = new GotoInstruction(0xC8, 0, 100000);
        TypeState result = inference.apply(initialState, gotow);

        assertSame(initialState, result);
    }

    @Test
    void testJsrW() {
        // JSR_W (0xC9) is handled similarly to JSR but with a wider branch offset
        // Create a mock instruction with opcode 0xC9
        Instruction jsrw = new GotoInstruction(0xC8, 0, 100000) {
            @Override
            public int getOpcode() {
                return 0xC9;
            }
        };
        TypeState result = inference.apply(initialState, jsrw);

        assertEquals(1, result.getStackSize());
        assertEquals(VerificationType.TOP, result.peek());
    }

    // ========== Edge Cases and Integration Tests ==========

    @Test
    void testComplexStackManipulation() {
        // Test a sequence: push int, push float, dup_x1, pop
        TypeState state = initialState
                .push(VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        Instruction dupx1 = new DupInstruction(0x5A, 0);
        state = inference.apply(state, dupx1);

        assertEquals(3, state.getStackSize());
        assertEquals(VerificationType.FLOAT, state.peek(0));
        assertEquals(VerificationType.INTEGER, state.peek(1));
        assertEquals(VerificationType.FLOAT, state.peek(2));
    }

    @Test
    void testLocalVariableOverwrite() {
        // Store int in local 5, then store float in local 5
        TypeState state = initialState.push(VerificationType.INTEGER);

        Instruction istore = new IStoreInstruction(0x36, 0, 5);
        state = inference.apply(state, istore);
        assertEquals(VerificationType.INTEGER, state.getLocal(5));

        state = state.push(VerificationType.FLOAT);
        Instruction fstore = new FStoreInstruction(0x38, 0, 5);
        state = inference.apply(state, fstore);
        assertEquals(VerificationType.FLOAT, state.getLocal(5));
    }

    @Test
    void testLongDoubleTwoSlotHandling() {
        // Ensure long and double properly occupy two slots
        TypeState state = initialState.push(VerificationType.LONG);

        assertEquals(2, state.getStackSize());
        assertEquals(VerificationType.LONG, state.peek(1));
        assertEquals(VerificationType.TOP, state.peek(0));

        Instruction lstore = new LStoreInstruction(0x37, 0, 0);
        state = inference.apply(state, lstore);

        assertEquals(VerificationType.LONG, state.getLocal(0));
        assertEquals(VerificationType.TOP, state.getLocal(1));
    }

    @Test
    void testMethodCallWithMultipleArgs() {
        int classIndex = constPool.findOrAddClass("java/lang/String").getIndex(constPool);
        TypeState state = initialState
                .push(VerificationType.object(classIndex))
                .push(VerificationType.INTEGER)
                .push(VerificationType.INTEGER);

        int nameIndex = constPool.findOrAddUtf8("substring").getIndex(constPool);
        int descIndex = constPool.findOrAddUtf8("(II)Ljava/lang/String;").getIndex(constPool);
        int natIndex = constPool.findOrAddNameAndType(nameIndex, descIndex).getIndex(constPool);
        int methodIndex = constPool.findOrAddMethodRef(classIndex, natIndex).getIndex(constPool);

        Instruction invoke = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);
        TypeState result = inference.apply(state, invoke);

        assertEquals(1, result.getStackSize());
        assertTrue(result.peek() instanceof VerificationType.ObjectType);
    }

    @Test
    void testUnknownOpcodeReturnsStateUnchanged() {
        Instruction unknown = new UnknownInstruction(0xFF, 0, 1);
        TypeState result = inference.apply(initialState, unknown);

        assertSame(initialState, result);
    }
}
