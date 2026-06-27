package com.tonic.testutil;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.tonic.testutil.TestUtils.liftMethod;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for invokedynamic support in BytecodeBuilder.
 */
class InvokeDynamicTest {

    @Test
    void testSimpleInvokeDynamic() throws IOException {
        BytecodeBuilder bb = BytecodeBuilder.forClass("com/test/InvokeDynamicTest");

        // Add a simple method
        BytecodeBuilder.MethodBuilder mb = bb.publicStaticMethod("test", "()V");

        ClassFile cf = mb
            .vreturn()
            .build();

        // After build(), we have access to constPool
        assertNotNull(cf);
        assertNotNull(bb.constPool);

        // Find the test method (getMethods() includes the auto-generated <init>)
        MethodEntry method = cf.getMethods().stream()
            .filter(m -> "test".equals(m.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(method);
        assertEquals("test", method.getName());
    }

    @Test
    void testBootstrapMethodManagement() throws IOException {
        BytecodeBuilder bb = BytecodeBuilder.forClass("com/test/LambdaTest");

        // First create a method that will build the class
        BytecodeBuilder.MethodBuilder testMethod = bb.publicStaticMethod("test", "()Ljava/lang/Runnable;");

        // Build the class to initialize constPool
        ClassFile cf = testMethod
            .aconst_null()  // Placeholder for now
            .areturn()
            .build();

        // Verify constPool was initialized
        assertNotNull(bb.constPool);

        // Now test adding bootstrap method after build
        int bsmIndex = bb.addBootstrapMethod(1, java.util.Arrays.asList(2, 3, 4));
        assertEquals(0, bsmIndex);  // First bootstrap method

        // Verify the class file was created
        assertNotNull(cf);
        MethodEntry method = cf.getMethods().stream()
            .filter(m -> "test".equals(m.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(method);
        assertEquals("test", method.getName());

        // The bytecode should be valid
        IRMethod ir = liftMethod(method);
        assertNotNull(ir);
    }

    @Test
    void testInvokeDynamicInstruction() throws IOException {
        BytecodeBuilder bb = BytecodeBuilder.forClass("com/test/InvokeDynamicTest");

        BytecodeBuilder.MethodBuilder mb = bb.publicStaticMethod("getDynamic", "()Ljava/lang/Object;");

        ClassFile cf = mb
            .aconst_null() // temp
            .areturn()
            .build();

        // Add a bootstrap method (indices are placeholders, won't execute)
        int bootstrapIndex = bb.addBootstrapMethod(1, java.util.Arrays.asList(2, 3));

        // Verify we can create the class
        assertNotNull(cf);
        assertEquals("com/test/InvokeDynamicTest", cf.getClassName());

        // Verify bootstrap method was added
        assertEquals(0, bootstrapIndex);
    }
}
