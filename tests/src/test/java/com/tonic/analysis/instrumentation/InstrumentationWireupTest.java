package com.tonic.analysis.instrumentation;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.lift.BytecodeLifter;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.Test;

import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the array-store element-type filter and exception-handler instrumentation,
 * both wired up after they were previously placeholders.
 */
class InstrumentationWireupTest {

    // ========== Array-store element-type filter ==========

    @Test
    void arrayStoreFilterInstrumentsMatchingElementType() throws Exception {
        int count = Instrumenter.forClass(intArrayStoreClass("com/test/ArrFilterMatch"))
                .onArrayStore()
                    .forArrayType("[I")
                    .callStatic("com/test/Hooks", "onArrayStore", "()V")
                    .register()
                .apply();

        assertEquals(1, count, "an int[] store must be instrumented when the filter matches the element type");
    }

    @Test
    void arrayStoreFilterSkipsNonMatchingElementType() throws Exception {
        int count = Instrumenter.forClass(intArrayStoreClass("com/test/ArrFilterSkip"))
                .onArrayStore()
                    .forArrayType("[J")
                    .callStatic("com/test/Hooks", "onArrayStore", "()V")
                    .register()
                .apply();

        assertEquals(0, count, "an int[] store must be skipped when the filter is a different array type");
    }

    private ClassFile intArrayStoreClass(String name) throws Exception {
        // Stores straight into the freshly allocated int[] so the array's element type stays precise.
        return BytecodeBuilder.forClass(name)
                .publicStaticMethod("arrayStore", "()V")
                    .iconst(5)
                    .newarray(10) // T_INT
                    .iconst(0)
                    .iconst(42)
                    .iastore()
                    .vreturn()
                .endMethod()
                .build();
    }

    // ========== Exception-handler instrumentation ==========

    @Test
    void lifterExposesHandlerExceptionValue() throws Exception {
        IRMethod ir = liftDemoMethod("osrs/dev/auth/PasswordHasher", "verifyPassword");
        assertNotNull(ir, "verifyPassword should be present in the demo jar");
        assertFalse(ir.getExceptionHandlers().isEmpty(), "verifyPassword has try-catch handlers");

        boolean anyValue = ir.getExceptionHandlers().stream()
                .anyMatch(h -> ir.getHandlerExceptionValue(h.getHandlerBlock()) != null);
        assertTrue(anyValue, "the lifter must expose the caught-exception value for at least one handler");
    }

    @Test
    void exceptionHookInstrumentsHandlers() throws Exception {
        int count = Instrumenter.forClass(loadDemoClass("osrs/dev/auth/PasswordHasher"))
                .onException()
                    .callStatic("com/test/Hooks", "onException", "(Ljava/lang/Throwable;)V")
                    .withException()
                    .register()
                .apply();

        assertTrue(count >= 1, "a method with try-catch must have at least one exception handler instrumented");
    }

    private IRMethod liftDemoMethod(String className, String methodName) throws Exception {
        ClassFile cf = loadDemoClass(className);
        for (MethodEntry method : cf.getMethods()) {
            if (methodName.equals(method.getName()) && method.getCodeAttribute() != null) {
                return new BytecodeLifter(cf.getConstPool()).lift(method);
            }
        }
        return null;
    }

    private ClassFile loadDemoClass(String className) throws Exception {
        try (JarFile jar = new JarFile("src/test/resources/DemoJar.jar")) {
            JarEntry entry = jar.getJarEntry(className + ".class");
            assertNotNull(entry, "DemoJar must contain " + className);
            return new ClassFile(jar.getInputStream(entry));
        }
    }
}
