package com.tonic.testutil;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.ConstructorDecl;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.lower.TypeResolver;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.verifier.VerificationError;
import com.tonic.analysis.verifier.VerificationErrorType;
import com.tonic.analysis.verifier.Verifier;
import java.util.List;
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
import com.tonic.type.AccessFlags;
import com.tonic.util.AccessBuilder;
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
     * Loads a ClassFile via a fresh class loader (defines it, without forcing linking). Note this does NOT
     * trigger full bytecode verification on its own - use {@link #linkAndVerify(ClassFile)} for that.
     *
     * @param cf the ClassFile to load
     * @return the loaded Class
     * @throws Exception if loading fails
     */
    public static Class<?> loadAndVerify(ClassFile cf) throws Exception {
        byte[] bytes = cf.write();
        TestClassLoader loader = new TestClassLoader();
        return loader.defineClass(cf.getClassName().replace('/', '.'), bytes);
    }

    /**
     * Defines and <b>links</b> a class, forcing the JVM bytecode verifier to run over the whole class
     * (including its StackMapTable). Throws {@link VerifyError} if the generated bytecode is invalid.
     * This is the reliable way to assert a recompiled class is verifiable - plain {@code defineClass}
     * defers verification to link time and so never runs for an unused class.
     *
     * @param cf the ClassFile to verify
     * @throws Exception if loading, linking, or verification fails
     */
    public static void linkAndVerify(ClassFile cf) throws Exception {
        byte[] bytes = cf.write();
        String binaryName = cf.getClassName().replace('/', '.');
        TestClassLoader loader = new TestClassLoader();
        loader.defineClass(binaryName, bytes);
        Class.forName(binaryName, true, loader);
    }

    /**
     * Compiles a Java source string, then defines, links, and returns the resulting {@link Class}, so a test
     * can reflectively invoke its methods to assert run-time behaviour (not just verifiability).
     *
     * @param source the Java source of one top-level class
     * @param internalClassName the class's internal name (e.g. {@code "test/Demo"})
     * @return the loaded, verified Class
     * @throws Exception if compiling, linking, or verification fails
     */
    public static Class<?> compileLinkAndLoad(String source, String internalClassName) throws Exception {
        ClassFile cf = compileSource(source, internalClassName);
        byte[] bytes = cf.write();
        String binaryName = internalClassName.replace('/', '.');
        TestClassLoader loader = new TestClassLoader();
        loader.defineClass(binaryName, bytes);
        return Class.forName(binaryName, true, loader);
    }

    /**
     * Compiles a single-class Java source string to a {@link ClassFile} through the full source pipeline
     * (parse -> AST lower -> SSA -> bytecode), mirroring how live recompilation works. Methods without a
     * body are skipped. The owning class resolves its own members and JDK references via a fresh pool.
     *
     * @param source the Java source of one top-level class
     * @param internalClassName the class's internal name (e.g. {@code "test/Demo"})
     * @return the compiled ClassFile
     * @throws Exception if parsing or lowering fails
     */
    public static ClassFile compileSource(String source, String internalClassName) throws Exception {
        CompilationUnit cu = JavaParser.create().parse(source);
        ClassDecl classDecl = (ClassDecl) cu.getTypes().get(0);

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.createNewClass(internalClassName, new AccessBuilder().setPublic().build());

        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(classDecl);
        lowerer.setImports(cu.getImports());

        for (MethodDecl methodDecl : classDecl.getMethods()) {
            if (methodDecl.getBody() == null) {
                continue;
            }
            StringBuilder desc = new StringBuilder("(");
            for (ParameterDecl p : methodDecl.getParameters()) {
                desc.append(p.getType().toIRType().getDescriptor());
            }
            desc.append(")").append(methodDecl.getReturnType().toIRType().getDescriptor());
            String descriptor = desc.toString();

            int access = 0;
            if (methodDecl.isPublic()) access |= AccessFlags.ACC_PUBLIC;
            if (methodDecl.isPrivate()) access |= AccessFlags.ACC_PRIVATE;
            if (methodDecl.isProtected()) access |= AccessFlags.ACC_PROTECTED;
            if (methodDecl.isStatic()) access |= AccessFlags.ACC_STATIC;
            final int methodAccess = access;

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals(methodDecl.getName()) && m.getDesc().equals(descriptor))
                    .findFirst()
                    .orElseGet(() -> cf.createNewMethodWithDescriptor(methodAccess, methodDecl.getName(), descriptor));

            IRMethod ir = lowerer.lower(methodDecl, internalClassName);
            new SSA(cf.getConstPool()).lower(ir, method);
        }
        return cf;
    }

    /**
     * Re-lowers a decompiled {@code source} back into {@code cf} - every method and constructor with a body
     * (the original {@code <clinit>} is left as-is), resolving type references against {@code pool} with
     * {@code owner} as the current class. Mirrors how live recompilation works. Returns false without
     * recompiling when the primary type is not a plain class (annotation/enum/interface out of scope).
     */
    public static boolean recompileSource(ClassFile cf, ClassPool pool, String source, String owner) throws Exception {
        if (source.contains("@interface ")) {
            return false;
        }
        CompilationUnit cu = JavaParser.create().parse(source);
        if (!(cu.getPrimaryType() instanceof ClassDecl)) {
            return false;
        }
        ClassDecl decl = (ClassDecl) cu.getPrimaryType();
        TypeResolver resolver = new TypeResolver(pool, owner);
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(decl);
        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(cf.getConstPool());

        for (MethodDecl md : decl.getMethods()) {
            if (md.getBody() == null) {
                continue;
            }
            MethodEntry target = findMethodEntry(cf, md.getName(),
                    methodDescriptor(md.getParameters(), resolver.descriptorOf(md.getReturnType()), resolver));
            if (target != null) {
                ssa.lower(lowerer.lower(md, owner), target);
            }
        }
        for (ConstructorDecl ctor : decl.getConstructors()) {
            if (ctor.getBody() == null) {
                continue;
            }
            MethodEntry target = findMethodEntry(cf, "<init>", methodDescriptor(ctor.getParameters(), "V", resolver));
            if (target == null) {
                continue;
            }
            MethodDecl init = new MethodDecl("<init>", VoidSourceType.INSTANCE).withModifiers(ctor.getModifiers());
            for (ParameterDecl pp : ctor.getParameters()) {
                init.addParameter(pp);
            }
            init.withBody(ctor.getBody());
            ssa.lower(lowerer.lower(init, owner), target);
        }
        cf.rebuild();
        return true;
    }

    /** True when {@code cf} passes the bytecode verifier with no error-level findings. */
    public static boolean verifies(ClassFile cf, ClassPool pool) {
        return Verifier.builder().classPool(pool).build().verify(cf).getErrors()
                .stream().noneMatch(VerificationError::isError);
    }

    /**
     * True when verifying {@code cf} finds a control-flow drop - a method that falls off its end or a path
     * that does not return, the signature of a dropped {@code return}/{@code throw}. Other verify errors
     * (stack, type, frame) are re-lowering-pipeline artifacts, not recovery drops, and are ignored - so
     * this isolates structural drops from the pipeline noise a large jar produces.
     */
    public static boolean hasControlFlowDrop(ClassFile cf, ClassPool pool) {
        return Verifier.builder().classPool(pool).build().verify(cf).getErrors().stream()
                .anyMatch(e -> e.isError()
                        && (e.getType() == VerificationErrorType.INSTRUCTION_FALLS_OFF_END
                            || e.getType() == VerificationErrorType.PATH_DOES_NOT_RETURN));
    }

    private static String methodDescriptor(List<ParameterDecl> params, String ret, TypeResolver resolver) {
        StringBuilder d = new StringBuilder("(");
        for (ParameterDecl pp : params) {
            d.append(resolver.descriptorOf(pp.getType()));
        }
        return d.append(")").append(ret).toString();
    }

    private static MethodEntry findMethodEntry(ClassFile cf, String name, String desc) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name) && m.getDesc().contentEquals(desc)) {
                return m;
            }
        }
        return null;
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
