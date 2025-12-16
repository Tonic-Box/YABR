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
 * Tests for MethodInlining transform (ClassTransform).
 * Tests inlining of small methods into their call sites.
 */
class MethodInliningTest {

    private ClassPool pool;
    private ClassFile classFile;
    private SSA ssa;

    @BeforeEach
    void setUp() throws IOException {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/MethodInliningTestClass", access);
        ssa = new SSA(classFile.getConstPool());
    }

    @Test
    void getNameReturnsMethodInlining() {
        MethodInlining transform = new MethodInlining();
        assertEquals("MethodInlining", transform.getName());
    }

    @Test
    void returnsFalseWhenNothingToInline() throws IOException {
        MethodInlining transform = new MethodInlining();

        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "simple", "()V");

        boolean changed = transform.run(classFile, ssa);

        assertFalse(changed);
    }

    @Test
    void handlesEmptyClass() {
        MethodInlining transform = new MethodInlining();

        boolean changed = transform.run(classFile, ssa);

        assertFalse(changed);
    }

    @Test
    void preservesClassStructure() throws IOException {
        MethodInlining transform = new MethodInlining();

        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "test", "()I");

        String originalClassName = classFile.getClassName();

        transform.run(classFile, ssa);

        assertEquals(originalClassName, classFile.getClassName());
        assertNotNull(classFile.getMethods());
    }

    @Test
    void handlesMethodWithoutCode() throws IOException {
        MethodInlining transform = new MethodInlining();

        int access = new AccessBuilder().setPublic().setAbstract().build();
        MethodEntry abstractMethod = classFile.createNewMethod(access, "abstractMethod", "()V");

        boolean changed = transform.run(classFile, ssa);

        assertFalse(changed);
    }

    @Test
    void ignoresConstructors() throws IOException {
        MethodInlining transform = new MethodInlining();

        int access = new AccessBuilder().setPublic().build();
        MethodEntry constructor = classFile.createNewMethod(access, "<init>", "()V");

        transform.run(classFile, ssa);

        // ClassFile auto-creates default constructor + clinit, so expect at least 1 method
        assertTrue(classFile.getMethods().size() >= 1);
    }

    @Test
    void handlesMultipleMethods() throws IOException {
        MethodInlining transform = new MethodInlining();

        int access = new AccessBuilder().setPublic().build();
        MethodEntry method1 = classFile.createNewMethod(access, "method1", "()V");
        MethodEntry method2 = classFile.createNewMethod(access, "method2", "()I");
        MethodEntry method3 = classFile.createNewMethod(access, "method3", "(I)I");

        int initialMethodCount = classFile.getMethods().size();

        transform.run(classFile, ssa);

        assertEquals(initialMethodCount, classFile.getMethods().size());
    }
}
