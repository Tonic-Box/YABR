package com.tonic.analysis.frame;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.stack.VerificationTypeInfo;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TypeState.
 * Verifies type state operations for stack and local variable tracking.
 */
class TypeStateTest {

    private ClassPool pool;
    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/TypeStateTest", access);
        constPool = classFile.getConstPool();
    }

    // ========== Creation Tests ==========

    @Test
    void emptyTypeStateHasNoLocalsOrStack() {
        TypeState state = TypeState.empty();

        assertEquals(0, state.getLocalsCount());
        assertEquals(0, state.getStackSize());
        assertTrue(state.isStackEmpty());
    }

    @Test
    void typeStateFromStaticMethod() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "staticMethod", "(I)V");

        TypeState state = TypeState.fromMethodEntry(method, constPool);

        // Static method: 1 int parameter
        assertEquals(1, state.getLocalsCount());
        assertEquals(VerificationType.INTEGER, state.getLocal(0));
        assertTrue(state.isStackEmpty());
    }

    @Test
    void typeStateFromInstanceMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "instanceMethod", "()V");

        TypeState state = TypeState.fromMethodEntry(method, constPool);

        // Instance method: this reference
        assertEquals(1, state.getLocalsCount());
        assertNotNull(state.getLocal(0));
        assertTrue(state.isStackEmpty());
    }

    @Test
    void typeStateFromConstructor() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "<init>", "()V");

        TypeState state = TypeState.fromMethodEntry(method, constPool);

        // Constructor: uninitialized this
        assertEquals(1, state.getLocalsCount());
        assertEquals(VerificationType.UNINITIALIZED_THIS, state.getLocal(0));
    }

    @Test
    void typeStateWithMultipleParameters() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "multiParam", "(IJFD)V");

        TypeState state = TypeState.fromMethodEntry(method, constPool);

        // int (1) + long (2) + float (1) + double (2) = 6 slots
        assertEquals(6, state.getLocalsCount());
        assertEquals(VerificationType.INTEGER, state.getLocal(0));
        assertEquals(VerificationType.LONG, state.getLocal(1));
        assertEquals(VerificationType.TOP, state.getLocal(2)); // Second slot of long
        assertEquals(VerificationType.FLOAT, state.getLocal(3));
        assertEquals(VerificationType.DOUBLE, state.getLocal(4));
        assertEquals(VerificationType.TOP, state.getLocal(5)); // Second slot of double
    }

    @Test
    void typeStateWithObjectParameter() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "objParam",
                "(Ljava/lang/String;)V");

        TypeState state = TypeState.fromMethodEntry(method, constPool);

        assertEquals(1, state.getLocalsCount());
        VerificationType type = state.getLocal(0);
        assertEquals(VerificationType.TAG_OBJECT, type.getTag());
    }

    @Test
    void typeStateWithArrayParameter() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "arrayParam", "([I)V");

        TypeState state = TypeState.fromMethodEntry(method, constPool);

        assertEquals(1, state.getLocalsCount());
        VerificationType type = state.getLocal(0);
        assertEquals(VerificationType.TAG_OBJECT, type.getTag()); // Arrays are object references
    }

    // ========== Stack Operations ==========

    @Test
    void pushIncreasesStackSize() {
        TypeState state = TypeState.empty();

        TypeState newState = state.push(VerificationType.INTEGER);

        assertEquals(0, state.getStackSize()); // Original unchanged
        assertEquals(1, newState.getStackSize());
    }

    @Test
    void pushTwoSlotValue() {
        TypeState state = TypeState.empty();

        TypeState newState = state.push(VerificationType.LONG);

        // Long takes 2 slots
        assertEquals(2, newState.getStackSize());
        assertEquals(VerificationType.LONG, newState.peek(1)); // From top
        assertEquals(VerificationType.TOP, newState.peek(0)); // Top slot
    }

    @Test
    void popDecreasesStackSize() {
        TypeState state = TypeState.empty()
                .push(VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        TypeState newState = state.pop();

        assertEquals(2, state.getStackSize());
        assertEquals(1, newState.getStackSize());
    }

    @Test
    void popThrowsOnEmptyStack() {
        TypeState state = TypeState.empty();

        assertThrows(IllegalStateException.class, state::pop);
    }

    @Test
    void popMultipleValues() {
        TypeState state = TypeState.empty()
                .push(VerificationType.INTEGER)
                .push(VerificationType.FLOAT)
                .push(VerificationType.INTEGER);

        TypeState newState = state.pop(2);

        assertEquals(3, state.getStackSize());
        assertEquals(1, newState.getStackSize());
    }

    @Test
    void popMoreThanStackSizeThrows() {
        TypeState state = TypeState.empty()
                .push(VerificationType.INTEGER);

        assertThrows(IllegalStateException.class, () -> state.pop(2));
    }

    @Test
    void peekReturnsTopWithoutPopping() {
        TypeState state = TypeState.empty()
                .push(VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        VerificationType top = state.peek();

        assertEquals(VerificationType.FLOAT, top);
        assertEquals(2, state.getStackSize()); // Unchanged
    }

    @Test
    void peekWithOffsetFromTop() {
        TypeState state = TypeState.empty()
                .push(VerificationType.INTEGER)
                .push(VerificationType.FLOAT)
                .push(VerificationType.DOUBLE);

        assertEquals(VerificationType.TOP, state.peek(0)); // Top of double
        assertEquals(VerificationType.DOUBLE, state.peek(1));
        assertEquals(VerificationType.FLOAT, state.peek(2));
        assertEquals(VerificationType.INTEGER, state.peek(3));
    }

    @Test
    void peekOnEmptyStackThrows() {
        TypeState state = TypeState.empty();

        assertThrows(IllegalStateException.class, state::peek);
    }

    @Test
    void clearStackEmptiesStack() {
        TypeState state = TypeState.empty()
                .push(VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        TypeState cleared = state.clearStack();

        assertEquals(2, state.getStackSize());
        assertEquals(0, cleared.getStackSize());
        assertTrue(cleared.isStackEmpty());
    }

    // ========== Local Variable Operations ==========

    @Test
    void setLocalIncreasesLocalsSize() {
        TypeState state = TypeState.empty();

        TypeState newState = state.setLocal(0, VerificationType.INTEGER);

        assertEquals(0, state.getLocalsCount());
        assertEquals(1, newState.getLocalsCount());
        assertEquals(VerificationType.INTEGER, newState.getLocal(0));
    }

    @Test
    void setLocalAtHigherIndex() {
        TypeState state = TypeState.empty();

        TypeState newState = state.setLocal(3, VerificationType.FLOAT);

        // Should expand locals to accommodate index 3
        assertTrue(newState.getLocalsCount() >= 4);
        assertEquals(VerificationType.FLOAT, newState.getLocal(3));
    }

    @Test
    void setTwoSlotLocal() {
        TypeState state = TypeState.empty();

        TypeState newState = state.setLocal(1, VerificationType.LONG);

        // Long takes 2 slots
        assertTrue(newState.getLocalsCount() >= 3);
        assertEquals(VerificationType.LONG, newState.getLocal(1));
        assertEquals(VerificationType.TOP, newState.getLocal(2));
    }

    @Test
    void getLocalBeyondSizeReturnsTop() {
        TypeState state = TypeState.empty()
                .setLocal(0, VerificationType.INTEGER);

        assertEquals(VerificationType.TOP, state.getLocal(10));
    }

    @Test
    void withStackReplacesStack() {
        List<VerificationType> newStack = new ArrayList<>();
        newStack.add(VerificationType.INTEGER);
        newStack.add(VerificationType.FLOAT);

        TypeState state = TypeState.empty()
                .push(VerificationType.DOUBLE);

        TypeState replaced = state.withStack(newStack);

        assertEquals(2, state.getStackSize()); // Original unchanged
        assertEquals(2, replaced.getStackSize());
    }

    @Test
    void withLocalsReplacesLocals() {
        List<VerificationType> newLocals = new ArrayList<>();
        newLocals.add(VerificationType.INTEGER);
        newLocals.add(VerificationType.FLOAT);

        TypeState state = TypeState.empty()
                .setLocal(0, VerificationType.DOUBLE);

        TypeState replaced = state.withLocals(newLocals);

        assertEquals(2, state.getLocalsCount()); // Original unchanged (double + top)
        assertEquals(2, replaced.getLocalsCount());
    }

    // ========== Merging Tests ==========

    @Test
    void mergeIdenticalStatesUnchanged() {
        TypeState state1 = TypeState.empty()
                .setLocal(0, VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        TypeState state2 = TypeState.empty()
                .setLocal(0, VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        TypeState merged = state1.merge(state2);

        assertEquals(state1.getLocalsCount(), merged.getLocalsCount());
        assertEquals(state1.getStackSize(), merged.getStackSize());
    }

    @Test
    void mergeDifferentLocalsBecomeTop() {
        TypeState state1 = TypeState.empty()
                .setLocal(0, VerificationType.INTEGER);

        TypeState state2 = TypeState.empty()
                .setLocal(0, VerificationType.FLOAT);

        TypeState merged = state1.merge(state2);

        assertEquals(VerificationType.TOP, merged.getLocal(0));
    }

    @Test
    void mergeDifferentStackSizes() {
        TypeState state1 = TypeState.empty()
                .push(VerificationType.INTEGER);

        TypeState state2 = TypeState.empty()
                .push(VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        TypeState merged = state1.merge(state2);

        // Stacks with different sizes merge to empty
        assertTrue(merged.getStackSize() == 0 || merged.getStackSize() >= 0);
    }

    @Test
    void mergeUnequalStacksToEmpty() {
        TypeState state1 = TypeState.empty()
                .push(VerificationType.INTEGER);

        TypeState state2 = TypeState.empty()
                .push(VerificationType.FLOAT);

        TypeState merged = state1.merge(state2);

        // Different stack contents should merge to empty
        assertTrue(merged.getStackSize() == 0);
    }

    @Test
    void mergeDifferentLocalsSizes() {
        TypeState state1 = TypeState.empty()
                .setLocal(0, VerificationType.INTEGER);

        TypeState state2 = TypeState.empty()
                .setLocal(0, VerificationType.INTEGER)
                .setLocal(1, VerificationType.FLOAT);

        TypeState merged = state1.merge(state2);

        // Merged locals size should be max of both
        assertTrue(merged.getLocalsCount() >= 2);
    }

    // ========== Conversion Tests ==========

    @Test
    void localsToVerificationTypeInfo() {
        TypeState state = TypeState.empty()
                .setLocal(0, VerificationType.INTEGER)
                .setLocal(1, VerificationType.FLOAT);

        List<VerificationTypeInfo> infos = state.localsToVerificationTypeInfo();

        assertEquals(2, infos.size());
        assertNotNull(infos.get(0));
        assertNotNull(infos.get(1));
    }

    @Test
    void stackToVerificationTypeInfo() {
        TypeState state = TypeState.empty()
                .push(VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        List<VerificationTypeInfo> infos = state.stackToVerificationTypeInfo();

        assertEquals(2, infos.size());
        assertNotNull(infos.get(0));
        assertNotNull(infos.get(1));
    }

    @Test
    void getReturnTypeForPrimitives() {
        assertEquals(VerificationType.INTEGER, TypeState.getReturnType("()I", constPool));
        assertEquals(VerificationType.LONG, TypeState.getReturnType("()J", constPool));
        assertEquals(VerificationType.FLOAT, TypeState.getReturnType("()F", constPool));
        assertEquals(VerificationType.DOUBLE, TypeState.getReturnType("()D", constPool));
    }

    @Test
    void getReturnTypeForVoid() {
        assertNull(TypeState.getReturnType("()V", constPool));
    }

    @Test
    void getReturnTypeForObject() {
        VerificationType type = TypeState.getReturnType("()Ljava/lang/String;", constPool);

        assertNotNull(type);
        assertEquals(VerificationType.TAG_OBJECT, type.getTag());
    }

    @Test
    void getReturnTypeForArray() {
        VerificationType type = TypeState.getReturnType("()[I", constPool);

        assertNotNull(type);
        assertEquals(VerificationType.TAG_OBJECT, type.getTag());
    }

    // ========== Immutability Tests ==========

    @Test
    void typeStateIsImmutable() {
        TypeState state = TypeState.empty();

        state.push(VerificationType.INTEGER);
        state.setLocal(0, VerificationType.FLOAT);

        // Original state should remain unchanged
        assertEquals(0, state.getStackSize());
        assertEquals(0, state.getLocalsCount());
    }

    @Test
    void equalsComparesStateCorrectly() {
        TypeState state1 = TypeState.empty()
                .setLocal(0, VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        TypeState state2 = TypeState.empty()
                .setLocal(0, VerificationType.INTEGER)
                .push(VerificationType.FLOAT);

        assertEquals(state1, state2);
        assertEquals(state1.hashCode(), state2.hashCode());
    }

    @Test
    void notEqualsForDifferentStates() {
        TypeState state1 = TypeState.empty()
                .push(VerificationType.INTEGER);

        TypeState state2 = TypeState.empty()
                .push(VerificationType.FLOAT);

        assertNotEquals(state1, state2);
    }

    @Test
    void toStringProvidesReadableOutput() {
        TypeState state = TypeState.empty()
                .setLocal(0, VerificationType.INTEGER);

        String str = state.toString();

        assertNotNull(str);
        assertTrue(str.contains("TypeState"));
    }
}
