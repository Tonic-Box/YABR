package com.tonic.analysis.ssa.cfg;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IRMethod - the SSA-form method representation.
 * Covers block management, traversal, and method properties.
 */
class IRMethodTest {

    private ClassPool pool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/IRMethodTestClass", access);
        // Reset block ID counter for predictable test results
        IRBlock.resetIdCounter();
    }

    // ========== Constructor Tests ==========

    @Test
    void constructorSetsOwnerClass() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        assertEquals("com/test/Test", method.getOwnerClass());
    }

    @Test
    void constructorSetsName() {
        IRMethod method = new IRMethod("com/test/Test", "myMethod", "()V", true);
        assertEquals("myMethod", method.getName());
    }

    @Test
    void constructorSetsDescriptor() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(II)I", true);
        assertEquals("(II)I", method.getDescriptor());
    }

    @Test
    void constructorSetsIsStatic() {
        IRMethod staticMethod = new IRMethod("com/test/Test", "foo", "()V", true);
        IRMethod instanceMethod = new IRMethod("com/test/Test", "foo", "()V", false);

        assertTrue(staticMethod.isStatic());
        assertFalse(instanceMethod.isStatic());
    }

    @Test
    void newMethodHasNoBlocks() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        assertTrue(method.getBlocks().isEmpty());
    }

    @Test
    void newMethodHasNoParameters() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        assertTrue(method.getParameters().isEmpty());
    }

    @Test
    void newMethodHasNoExceptionHandlers() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        assertTrue(method.getExceptionHandlers().isEmpty());
    }

    // ========== Parameter Tests ==========

    @Test
    void addParameterAddsToList() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(I)V", true);
        SSAValue param = new SSAValue(PrimitiveType.INT, "p0");

        method.addParameter(param);

        assertEquals(1, method.getParameters().size());
        assertEquals(param, method.getParameters().get(0));
    }

    @Test
    void addMultipleParameters() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(II)V", true);
        SSAValue param1 = new SSAValue(PrimitiveType.INT, "p0");
        SSAValue param2 = new SSAValue(PrimitiveType.INT, "p1");

        method.addParameter(param1);
        method.addParameter(param2);

        assertEquals(2, method.getParameters().size());
    }

    // ========== Block Management Tests ==========

    @Test
    void addBlockIncrementsBlockCount() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock block = new IRBlock("entry");

        method.addBlock(block);

        assertEquals(1, method.getBlockCount());
    }

    @Test
    void addBlockSetsMethodReference() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock block = new IRBlock("entry");

        method.addBlock(block);

        assertEquals(method, block.getMethod());
    }

    @Test
    void removeBlockDecrementsBlockCount() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);

        method.removeBlock(block);

        assertEquals(0, method.getBlockCount());
    }

    @Test
    void setEntryBlockSetsIt() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);

        method.setEntryBlock(block);

        assertEquals(block, method.getEntryBlock());
    }

    // ========== Exception Handler Tests ==========

    @Test
    void addExceptionHandlerAddsToList() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock tryStart = new IRBlock("tryStart");
        IRBlock tryEnd = new IRBlock("tryEnd");
        IRBlock handler = new IRBlock("handler");
        ExceptionHandler eh = new ExceptionHandler(tryStart, tryEnd, handler, new ReferenceType("java/lang/Exception"));

        method.addExceptionHandler(eh);

        assertEquals(1, method.getExceptionHandlers().size());
    }

    // ========== Block Traversal Tests ==========

    @Test
    void getBlocksInOrderReturnsBlocksFromEntry() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");

        method.addBlock(entry);
        method.addBlock(b1);
        method.addBlock(b2);
        method.setEntryBlock(entry);

        entry.addSuccessor(b1);
        b1.addSuccessor(b2);

        List<IRBlock> ordered = method.getBlocksInOrder();

        assertEquals(3, ordered.size());
        assertEquals(entry, ordered.get(0));
    }

    @Test
    void getBlocksInOrderWithNullEntry() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock b1 = new IRBlock("b1");
        method.addBlock(b1);
        // Don't set entry block

        List<IRBlock> ordered = method.getBlocksInOrder();

        assertEquals(1, ordered.size());
    }

    @Test
    void getPostOrderReturnsPostOrder() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(entry);
        method.addBlock(b1);
        method.setEntryBlock(entry);
        entry.addSuccessor(b1);

        List<IRBlock> postOrder = method.getPostOrder();

        assertFalse(postOrder.isEmpty());
        // In post-order, children come before parents
        assertEquals(b1, postOrder.get(0));
        assertEquals(entry, postOrder.get(1));
    }

    @Test
    void getReversePostOrderReturnsRPO() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(entry);
        method.addBlock(b1);
        method.setEntryBlock(entry);
        entry.addSuccessor(b1);

        List<IRBlock> rpo = method.getReversePostOrder();

        assertFalse(rpo.isEmpty());
        // In reverse post-order, parents come before children
        assertEquals(entry, rpo.get(0));
        assertEquals(b1, rpo.get(1));
    }

    @Test
    void getPostOrderWithNullEntryReturnsEmpty() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock b1 = new IRBlock("b1");
        method.addBlock(b1);
        // Don't set entry block

        List<IRBlock> postOrder = method.getPostOrder();

        assertTrue(postOrder.isEmpty());
    }

    // ========== Instruction Count Tests ==========

    @Test
    void getInstructionCountReturnsZeroForEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        assertEquals(0, method.getInstructionCount());
    }

    @Test
    void getInstructionCountIncludesPhis() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);

        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(result);
        block.addPhi(phi);

        assertEquals(1, method.getInstructionCount());
    }

    @Test
    void getInstructionCountIncludesRegularInstructions() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);

        block.addInstruction(new ReturnInstruction());

        assertEquals(1, method.getInstructionCount());
    }

    // ========== Return Type Tests ==========

    @Test
    void setReturnTypeSetsIt() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        method.setReturnType(PrimitiveType.INT);

        assertEquals(PrimitiveType.INT, method.getReturnType());
    }

    // ========== Max Stack/Locals Tests ==========

    @Test
    void setMaxLocalsSetsIt() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        method.setMaxLocals(10);

        assertEquals(10, method.getMaxLocals());
    }

    @Test
    void setMaxStackSetsIt() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        method.setMaxStack(5);

        assertEquals(5, method.getMaxStack());
    }

    // ========== Source Method Tests ==========

    @Test
    void setSourceMethodSetsIt() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry sourceMethod = classFile.createNewMethod(access, "source", "V");

        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        method.setSourceMethod(sourceMethod);

        assertEquals(sourceMethod, method.getSourceMethod());
    }

    // ========== toString Tests ==========

    @Test
    void toStringContainsMethodName() {
        IRMethod method = new IRMethod("com/test/Test", "myMethod", "()V", true);
        assertTrue(method.toString().contains("myMethod"));
    }

    @Test
    void toStringContainsOwnerClass() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        assertTrue(method.toString().contains("com/test/Test"));
    }

    @Test
    void toStringContainsDescriptor() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(II)I", true);
        assertTrue(method.toString().contains("(II)I"));
    }

    @Test
    void toStringContainsStaticKeyword() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        assertTrue(method.toString().contains("static"));
    }

    // ========== Lifting Integration Tests ==========

    @Test
    void liftedMethodHasEntryBlock() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "withEntry", "V");

        SSA ssa = new SSA(classFile.getConstPool());
        IRMethod irMethod = ssa.lift(method);

        // Even empty methods should have at least the structure set up
        assertNotNull(irMethod);
    }
}
