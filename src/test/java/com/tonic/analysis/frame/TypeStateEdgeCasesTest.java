package com.tonic.analysis.frame;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypeStateEdgeCasesTest {

    private ClassPool pool;
    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/EdgeCases", access);
        constPool = classFile.getConstPool();
    }

    @Nested
    class DescriptorParsingEdgeCases {

        @Test
        void testParseMethodParametersWithAllPrimitiveTypes() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "allPrimitives", "(BCISZFDJJ)V");

            TypeState state = TypeState.fromMethodEntry(method, constPool);

            assertTrue(state.getLocalsCount() >= 9);
            assertEquals(VerificationType.INTEGER, state.getLocal(0));
            assertEquals(VerificationType.INTEGER, state.getLocal(1));
            assertEquals(VerificationType.INTEGER, state.getLocal(2));
            assertEquals(VerificationType.INTEGER, state.getLocal(3));
            assertEquals(VerificationType.INTEGER, state.getLocal(4));
            assertEquals(VerificationType.FLOAT, state.getLocal(5));
            assertEquals(VerificationType.DOUBLE, state.getLocal(6));
            assertEquals(VerificationType.LONG, state.getLocal(8));
        }

        @Test
        void testParseMethodParametersWithMultidimensionalArray() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "multiArray",
                    "([[[Ljava/lang/String;)V");

            TypeState state = TypeState.fromMethodEntry(method, constPool);

            assertEquals(1, state.getLocalsCount());
            assertTrue(state.getLocal(0) instanceof VerificationType.ObjectType);
        }

        @Test
        void testParseMethodParametersWithPrimitiveArrays() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, "primitiveArrays",
                    "([I[J[F[D)V");

            TypeState state = TypeState.fromMethodEntry(method, constPool);

            assertEquals(4, state.getLocalsCount());
            for (int i = 0; i < 4; i++) {
                assertTrue(state.getLocal(i) instanceof VerificationType.ObjectType);
            }
        }
    }

    @Nested
    class ReturnTypeEdgeCases {

        @Test
        void testGetReturnTypeForByte() {
            assertEquals(VerificationType.INTEGER, TypeState.getReturnType("()B", constPool));
        }

        @Test
        void testGetReturnTypeForChar() {
            assertEquals(VerificationType.INTEGER, TypeState.getReturnType("()C", constPool));
        }

        @Test
        void testGetReturnTypeForShort() {
            assertEquals(VerificationType.INTEGER, TypeState.getReturnType("()S", constPool));
        }

        @Test
        void testGetReturnTypeForBoolean() {
            assertEquals(VerificationType.INTEGER, TypeState.getReturnType("()Z", constPool));
        }

        @Test
        void testGetReturnTypeForMultidimensionalArray() {
            VerificationType type = TypeState.getReturnType("()[[[I", constPool);

            assertNotNull(type);
            assertTrue(type instanceof VerificationType.ObjectType);
        }
    }

    @Nested
    class StackOperationBoundaryTests {

        @Test
        void testPeekBeyondStackBounds() {
            TypeState state = TypeState.empty()
                    .push(VerificationType.INTEGER);

            assertThrows(IllegalStateException.class, () -> state.peek(5));
        }

        @Test
        void testPopExactStackSize() {
            TypeState state = TypeState.empty()
                    .push(VerificationType.INTEGER)
                    .push(VerificationType.FLOAT);

            TypeState result = state.pop(2);
            assertEquals(0, result.getStackSize());
        }

        @Test
        void testPushAfterMultiplePops() {
            TypeState state = TypeState.empty()
                    .push(VerificationType.INTEGER)
                    .push(VerificationType.FLOAT)
                    .pop()
                    .pop()
                    .push(VerificationType.DOUBLE);

            assertEquals(2, state.getStackSize());
            assertEquals(VerificationType.DOUBLE, state.peek(1));
        }
    }

    @Nested
    class LocalVariableBoundaryTests {

        @Test
        void testSetLocalAtLargeIndex() {
            TypeState state = TypeState.empty();

            TypeState result = state.setLocal(100, VerificationType.INTEGER);

            assertTrue(result.getLocalsCount() >= 101);
            assertEquals(VerificationType.INTEGER, result.getLocal(100));
        }

        @Test
        void testSetTwoSlotLocalAtExactBoundary() {
            TypeState state = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER);

            TypeState result = state.setLocal(1, VerificationType.LONG);

            assertEquals(VerificationType.LONG, result.getLocal(1));
            assertEquals(VerificationType.TOP, result.getLocal(2));
        }

        @Test
        void testSetTwoSlotLocalExpandsLocals() {
            TypeState state = TypeState.empty();

            TypeState result = state.setLocal(5, VerificationType.DOUBLE);

            assertTrue(result.getLocalsCount() >= 7);
            assertEquals(VerificationType.DOUBLE, result.getLocal(5));
            assertEquals(VerificationType.TOP, result.getLocal(6));
        }

        @Test
        void testGetLocalFromGap() {
            TypeState state = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .setLocal(5, VerificationType.FLOAT);

            assertEquals(VerificationType.TOP, state.getLocal(3));
        }
    }

    @Nested
    class MergeEdgeCases {

        @Test
        void testMergeWithEmptyStack() {
            TypeState state1 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER);

            TypeState state2 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .push(VerificationType.FLOAT);

            TypeState merged = state1.merge(state2);

            assertEquals(0, merged.getStackSize());
        }

        @Test
        void testMergeWithOneEmptyStack() {
            TypeState state1 = TypeState.empty()
                    .push(VerificationType.INTEGER);

            TypeState state2 = TypeState.empty();

            TypeState merged = state1.merge(state2);

            assertEquals(0, merged.getStackSize());
        }

        @Test
        void testMergeDifferentStackSameLocals() {
            TypeState state1 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .push(VerificationType.FLOAT);

            TypeState state2 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .push(VerificationType.DOUBLE);

            TypeState merged = state1.merge(state2);

            assertEquals(VerificationType.INTEGER, merged.getLocal(0));
            assertEquals(0, merged.getStackSize());
        }

        @Test
        void testMergeSameStackDifferentLocals() {
            TypeState state1 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .push(VerificationType.FLOAT);

            TypeState state2 = TypeState.empty()
                    .setLocal(0, VerificationType.LONG)
                    .push(VerificationType.FLOAT);

            TypeState merged = state1.merge(state2);

            assertEquals(VerificationType.TOP, merged.getLocal(0));
            assertEquals(1, merged.getStackSize());
        }

        @Test
        void testMergeWithDifferentLocalsSizes() {
            TypeState state1 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER);

            TypeState state2 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .setLocal(1, VerificationType.FLOAT)
                    .setLocal(2, VerificationType.DOUBLE);

            TypeState merged = state1.merge(state2);

            assertEquals(VerificationType.INTEGER, merged.getLocal(0));
            assertEquals(VerificationType.TOP, merged.getLocal(1));
            assertEquals(VerificationType.TOP, merged.getLocal(2));
        }

        @Test
        void testMergePreservesMatchingLocals() {
            TypeState state1 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .setLocal(1, VerificationType.FLOAT)
                    .setLocal(2, VerificationType.DOUBLE);

            TypeState state2 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .setLocal(1, VerificationType.LONG)
                    .setLocal(3, VerificationType.DOUBLE);

            TypeState merged = state1.merge(state2);

            assertEquals(VerificationType.INTEGER, merged.getLocal(0));
            assertEquals(VerificationType.TOP, merged.getLocal(1));
        }
    }

    @Nested
    class WithMethodsEdgeCases {

        @Test
        void testWithStackPreservesLocals() {
            TypeState state = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .setLocal(1, VerificationType.FLOAT);

            List<VerificationType> newStack = new ArrayList<>();
            newStack.add(VerificationType.DOUBLE);

            TypeState result = state.withStack(newStack);

            assertEquals(VerificationType.INTEGER, result.getLocal(0));
            assertEquals(VerificationType.FLOAT, result.getLocal(1));
            assertEquals(1, result.getStackSize());
        }

        @Test
        void testWithLocalsPreservesStack() {
            TypeState state = TypeState.empty()
                    .push(VerificationType.INTEGER)
                    .push(VerificationType.FLOAT);

            List<VerificationType> newLocals = new ArrayList<>();
            newLocals.add(VerificationType.DOUBLE);

            TypeState result = state.withLocals(newLocals);

            assertEquals(1, result.getLocalsCount());
            assertEquals(VerificationType.DOUBLE, result.getLocal(0));
            assertEquals(2, result.getStackSize());
        }
    }

    @Nested
    class EqualityEdgeCases {

        @Test
        void testEqualsSameInstance() {
            TypeState state = TypeState.empty()
                    .push(VerificationType.INTEGER);

            assertEquals(state, state);
        }

        @Test
        void testEqualsWithNull() {
            TypeState state = TypeState.empty();

            assertNotEquals(null, state);
        }

        @Test
        void testEqualsWithDifferentType() {
            TypeState state = TypeState.empty();

            assertNotEquals("string", state);
        }

        @Test
        void testEqualsDifferentLocals() {
            TypeState state1 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER);

            TypeState state2 = TypeState.empty()
                    .setLocal(0, VerificationType.FLOAT);

            assertNotEquals(state1, state2);
        }

        @Test
        void testHashCodeConsistency() {
            TypeState state1 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .push(VerificationType.FLOAT);

            TypeState state2 = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .push(VerificationType.FLOAT);

            assertEquals(state1.hashCode(), state2.hashCode());
        }

        @Test
        void testHashCodeDifference() {
            TypeState state1 = TypeState.empty()
                    .push(VerificationType.INTEGER);

            TypeState state2 = TypeState.empty()
                    .push(VerificationType.FLOAT);

            assertNotEquals(state1.hashCode(), state2.hashCode());
        }
    }

    @Nested
    class ConversionEdgeCases {

        @Test
        void testLocalsToVerificationTypeInfoWithTwoSlotTypes() {
            TypeState state = TypeState.empty()
                    .setLocal(0, VerificationType.LONG)
                    .setLocal(2, VerificationType.DOUBLE);

            var infos = state.localsToVerificationTypeInfo();

            assertEquals(4, infos.size());
        }

        @Test
        void testStackToVerificationTypeInfoWithTwoSlotTypes() {
            TypeState state = TypeState.empty()
                    .push(VerificationType.LONG)
                    .push(VerificationType.DOUBLE);

            var infos = state.stackToVerificationTypeInfo();

            assertEquals(4, infos.size());
        }

        @Test
        void testLocalsToVerificationTypeInfoEmpty() {
            TypeState state = TypeState.empty();

            var infos = state.localsToVerificationTypeInfo();

            assertEquals(0, infos.size());
        }

        @Test
        void testStackToVerificationTypeInfoEmpty() {
            TypeState state = TypeState.empty();

            var infos = state.stackToVerificationTypeInfo();

            assertEquals(0, infos.size());
        }
    }

    @Nested
    class ComplexStateManipulation {

        @Test
        void testSequentialLocalModifications() {
            TypeState state = TypeState.empty()
                    .setLocal(0, VerificationType.INTEGER)
                    .setLocal(1, VerificationType.FLOAT)
                    .setLocal(0, VerificationType.LONG)
                    .setLocal(3, VerificationType.DOUBLE);

            assertEquals(VerificationType.LONG, state.getLocal(0));
            assertEquals(VerificationType.TOP, state.getLocal(1));
            assertEquals(VerificationType.DOUBLE, state.getLocal(3));
            assertEquals(VerificationType.TOP, state.getLocal(4));
        }

        @Test
        void testComplexStackPushPopSequence() {
            TypeState state = TypeState.empty()
                    .push(VerificationType.INTEGER)
                    .push(VerificationType.FLOAT)
                    .push(VerificationType.DOUBLE)
                    .pop(2)
                    .push(VerificationType.LONG);

            TypeState finalState = state.pop().push(VerificationType.INTEGER);

            assertTrue(finalState.getStackSize() >= 2);
        }
    }
}
