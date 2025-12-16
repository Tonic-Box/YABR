package com.tonic.testutil;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.LongConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.AccessBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Core test utilities for YABR unit tests.
 * Provides helper methods for creating and manipulating test fixtures.
 */
public final class TestUtils {

    private TestUtils() {
        // Utility class
    }

    // ========== ClassPool Utilities ==========

    /**
     * Creates an empty ClassPool without loading built-in classes.
     * Faster for tests that don't need java.* classes.
     *
     * @return empty ClassPool
     */
    public static ClassPool emptyPool() {
        return new ClassPool(true);
    }

    /**
     * Creates a minimal test class with the given name.
     *
     * @param className internal class name (e.g., "com/test/MyClass")
     * @return the created ClassFile
     * @throws IOException if class creation fails
     */
    public static ClassFile createMinimalClass(String className) throws IOException {
        ClassPool pool = emptyPool();
        int access = new AccessBuilder().setPublic().build();
        return pool.createNewClass(className, access);
    }

    /**
     * Creates a test class with a single method.
     *
     * @param className internal class name
     * @param methodName method name
     * @param desc method descriptor
     * @param bytecodeSetup consumer to set up bytecode
     * @return the created ClassFile
     * @throws IOException if creation fails
     */
    public static ClassFile createClassWithMethod(String className, String methodName,
                                                   String desc, Consumer<com.tonic.analysis.Bytecode> bytecodeSetup)
            throws IOException {
        ClassPool pool = emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass(className, classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = cf.createNewMethodWithDescriptor(methodAccess, methodName, desc);

        com.tonic.analysis.Bytecode bc = new com.tonic.analysis.Bytecode(method);
        bytecodeSetup.accept(bc);
        bc.finalizeBytecode();

        return cf;
    }

    /**
     * Loads a test fixture class by name.
     *
     * @param fixtureName simple class name from fixtures package
     * @return the loaded ClassFile
     * @throws IOException if loading fails
     */
    public static ClassFile loadTestFixture(String fixtureName) throws IOException {
        String resourcePath = "com/tonic/fixtures/" + fixtureName + ".class";
        try (InputStream is = TestUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Test fixture not found: " + resourcePath);
            }
            ClassPool pool = emptyPool();
            return pool.loadClass(is);
        }
    }

    // ========== SSA Utilities ==========

    /**
     * Lifts a method to SSA form.
     *
     * @param method the method to lift
     * @return the SSA IR method
     */
    public static IRMethod liftMethod(MethodEntry method) {
        ConstPool cp = method.getClassFile().getConstPool();
        SSA ssa = new SSA(cp);
        return ssa.lift(method);
    }

    /**
     * Creates a simple IR method for testing.
     *
     * @return a minimal IRMethod with entry block
     */
    public static IRMethod createSimpleIRMethod() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        IRMethod method = new IRMethod("com/test/Test", "testMethod", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // Add void return
        ReturnInstruction ret = new ReturnInstruction(null);
        entry.addInstruction(ret);

        return method;
    }

    /**
     * Creates an SSA value with the given type and name.
     *
     * @param type the IR type
     * @param name the value name
     * @return the created SSAValue
     */
    public static SSAValue createSSAValue(IRType type, String name) {
        return new SSAValue(type, name);
    }

    /**
     * Creates an integer SSA value.
     *
     * @param name the value name
     * @return the created SSAValue
     */
    public static SSAValue intValue(String name) {
        return new SSAValue(PrimitiveType.INT, name);
    }

    /**
     * Creates an IntConstant from an integer value.
     *
     * @param value the integer value
     * @return the IntConstant
     */
    public static IntConstant intConst(int value) {
        return IntConstant.of(value);
    }

    /**
     * Creates a LongConstant from a long value.
     *
     * @param value the long value
     * @return the LongConstant
     */
    public static LongConstant longConst(long value) {
        return LongConstant.of(value);
    }

    // ========== Verification Utilities ==========

    /**
     * Performs a round-trip test: write class bytes and re-parse.
     *
     * @param cf the ClassFile to round-trip
     * @return the re-parsed ClassFile
     * @throws IOException if round-trip fails
     */
    public static ClassFile roundTrip(ClassFile cf) throws IOException {
        byte[] bytes = cf.write();
        ClassPool pool = emptyPool();
        return pool.loadClass(bytes);
    }

    /**
     * Loads and verifies a ClassFile using the JVM.
     *
     * @param cf the ClassFile to verify
     * @return the loaded Class
     * @throws Exception if loading or verification fails
     */
    public static Class<?> loadAndVerify(ClassFile cf) throws Exception {
        byte[] bytes = cf.write();
        TestClassLoader loader = new TestClassLoader();
        return loader.defineClass(cf.getClassName().replace('/', '.'), bytes);
    }

    /**
     * Asserts that two ClassFiles have the same structure.
     *
     * @param expected expected ClassFile
     * @param actual actual ClassFile
     */
    public static void assertClassFilesEqual(ClassFile expected, ClassFile actual) {
        assertEquals(expected.getClassName(), actual.getClassName(), "Class name mismatch");
        assertEquals(expected.getSuperClassName(), actual.getSuperClassName(), "Super class mismatch");
        assertEquals(expected.getMajorVersion(), actual.getMajorVersion(), "Major version mismatch");
        assertEquals(expected.getMinorVersion(), actual.getMinorVersion(), "Minor version mismatch");
        assertEquals(expected.getAccess(), actual.getAccess(), "Access flags mismatch");
        assertEquals(expected.getMethods().size(), actual.getMethods().size(), "Method count mismatch");
        assertEquals(expected.getFields().size(), actual.getFields().size(), "Field count mismatch");
    }

    /**
     * Asserts that an IR method has the expected number of blocks.
     *
     * @param method the IR method
     * @param expected expected block count
     */
    public static void assertBlockCount(IRMethod method, int expected) {
        assertEquals(expected, method.getBlockCount(),
                "Expected " + expected + " blocks but found " + method.getBlockCount());
    }

    /**
     * Asserts that an IR block has the expected number of instructions (excluding phi).
     *
     * @param block the IR block
     * @param expected expected instruction count
     */
    public static void assertInstructionCount(IRBlock block, int expected) {
        assertEquals(expected, block.getInstructions().size(),
                "Expected " + expected + " instructions but found " + block.getInstructions().size());
    }

    // ========== Debug Utilities ==========

    /**
     * Resets SSA ID counters for deterministic test output.
     */
    public static void resetSSACounters() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }
}
