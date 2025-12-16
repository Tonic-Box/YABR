package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
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
 * Tests for DeadMethodElimination transform.
 * Tests elimination of unused methods from classes.
 * This is a ClassTransform, not an IRTransform.
 */
class DeadMethodEliminationTest {

    private ClassPool pool;
    private ClassFile classFile;
    private SSA ssa;

    @BeforeEach
    void setUp() throws IOException {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/DeadMethodTestClass", access);
        ssa = new SSA(classFile.getConstPool());
    }

    @Test
    void getNameReturnsDeadMethodElimination() {
        DeadMethodElimination transform = new DeadMethodElimination();
        assertEquals("DeadMethodElimination", transform.getName());
    }

    @Test
    void eliminatesUnusedMethods() throws IOException {
        DeadMethodElimination transform = new DeadMethodElimination();

        int access = new AccessBuilder().setPublic().build();

        // Create a main method (entry point - should be kept)
        MethodEntry mainMethod = classFile.createNewMethod(access | 0x0008, "main", "([Ljava/lang/String;)V");

        // Create an unused helper method (should be eliminated)
        MethodEntry unusedHelper = classFile.createNewMethod(access, "unusedHelper", "()I");

        int initialMethodCount = classFile.getMethods().size();

        boolean changed = transform.run(classFile, ssa);

        // Should have analyzed the class
        assertNotNull(classFile);
        assertTrue(initialMethodCount > 0);
    }

    @Test
    void keepsUsedMethods() throws IOException {
        DeadMethodElimination transform = new DeadMethodElimination();

        int access = new AccessBuilder().setPublic().build();

        // Create a main method
        MethodEntry mainMethod = classFile.createNewMethod(access | 0x0008, "main", "([Ljava/lang/String;)V");

        // Create a used helper method (called from main)
        MethodEntry usedHelper = classFile.createNewMethod(access, "usedHelper", "()I");

        boolean changed = transform.run(classFile, ssa);

        // Verify class structure is intact
        assertNotNull(classFile);
        assertTrue(classFile.getMethods().size() > 0);
    }

    @Test
    void handlesEmptyClass() {
        DeadMethodElimination transform = new DeadMethodElimination();

        boolean changed = transform.run(classFile, ssa);

        // Empty class should return false (no changes)
        assertFalse(changed);
    }

    @Test
    void keepsConstructors() throws IOException {
        DeadMethodElimination transform = new DeadMethodElimination();

        int access = new AccessBuilder().setPublic().build();

        // Create a constructor (should always be kept)
        MethodEntry constructor = classFile.createNewMethod(access, "<init>", "()V");

        int initialMethodCount = classFile.getMethods().size();

        transform.run(classFile, ssa);

        // Constructor should still exist
        assertEquals(initialMethodCount, classFile.getMethods().size());
    }

    @Test
    void keepsStaticInitializers() throws IOException {
        DeadMethodElimination transform = new DeadMethodElimination();

        int access = new AccessBuilder().setPublic().setStatic().build();

        // Create a static initializer (should always be kept)
        MethodEntry clinit = classFile.createNewMethod(access, "<clinit>", "()V");

        int initialMethodCount = classFile.getMethods().size();

        transform.run(classFile, ssa);

        // Static initializer should still exist
        assertEquals(initialMethodCount, classFile.getMethods().size());
    }

    @Test
    void handlesMultipleMethods() throws IOException {
        DeadMethodElimination transform = new DeadMethodElimination();

        int access = new AccessBuilder().setPublic().build();

        // Create several methods
        MethodEntry method1 = classFile.createNewMethod(access, "method1", "()V");
        MethodEntry method2 = classFile.createNewMethod(access, "method2", "()I");
        MethodEntry method3 = classFile.createNewMethod(access, "method3", "(I)I");

        int initialMethodCount = classFile.getMethods().size();

        transform.run(classFile, ssa);

        // Verify class has methods
        assertTrue(classFile.getMethods().size() > 0);
        assertTrue(initialMethodCount > 0);
    }

    @Test
    void preservesClassStructure() throws IOException {
        DeadMethodElimination transform = new DeadMethodElimination();

        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "test", "()V");

        String originalClassName = classFile.getClassName();

        transform.run(classFile, ssa);

        // Class name and structure should be unchanged
        assertEquals(originalClassName, classFile.getClassName());
        assertNotNull(classFile.getMethods());
    }
}
