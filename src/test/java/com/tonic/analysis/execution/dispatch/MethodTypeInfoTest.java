package com.tonic.analysis.execution.dispatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class MethodTypeInfoTest {

    @Nested
    class ConstructorTests {
        @Test
        void shouldCreateWithDescriptor() {
            MethodTypeInfo info = new MethodTypeInfo("(II)V");
            assertEquals("(II)V", info.getDescriptor());
        }

        @Test
        void shouldHandleNullDescriptor() {
            MethodTypeInfo info = new MethodTypeInfo(null);
            assertNull(info.getDescriptor());
        }
    }

    @Nested
    class ReturnTypeTests {
        @Test
        void shouldExtractVoidReturnType() {
            MethodTypeInfo info = new MethodTypeInfo("()V");
            assertEquals("V", info.getReturnType());
        }

        @Test
        void shouldExtractIntReturnType() {
            MethodTypeInfo info = new MethodTypeInfo("()I");
            assertEquals("I", info.getReturnType());
        }

        @Test
        void shouldExtractLongReturnType() {
            MethodTypeInfo info = new MethodTypeInfo("()J");
            assertEquals("J", info.getReturnType());
        }

        @Test
        void shouldExtractObjectReturnType() {
            MethodTypeInfo info = new MethodTypeInfo("()Ljava/lang/String;");
            assertEquals("Ljava/lang/String;", info.getReturnType());
        }

        @Test
        void shouldExtractArrayReturnType() {
            MethodTypeInfo info = new MethodTypeInfo("()[I");
            assertEquals("[I", info.getReturnType());
        }

        @Test
        void shouldHandleNullDescriptorForReturnType() {
            MethodTypeInfo info = new MethodTypeInfo(null);
            assertEquals("V", info.getReturnType());
        }

        @Test
        void shouldHandleMalformedDescriptor() {
            MethodTypeInfo info = new MethodTypeInfo("invalid");
            assertEquals("V", info.getReturnType());
        }
    }

    @Nested
    class VoidReturnTests {
        @Test
        void shouldReturnTrueForVoidReturn() {
            MethodTypeInfo info = new MethodTypeInfo("()V");
            assertTrue(info.isVoidReturn());
        }

        @Test
        void shouldReturnFalseForNonVoidReturn() {
            MethodTypeInfo info = new MethodTypeInfo("()I");
            assertFalse(info.isVoidReturn());
        }

        @Test
        void shouldReturnTrueForNullDescriptor() {
            MethodTypeInfo info = new MethodTypeInfo(null);
            assertTrue(info.isVoidReturn());
        }
    }

    @Nested
    class ParameterCountTests {
        @Test
        void shouldCountZeroParameters() {
            MethodTypeInfo info = new MethodTypeInfo("()V");
            assertEquals(0, info.getParameterCount());
        }

        @Test
        void shouldCountOneIntParameter() {
            MethodTypeInfo info = new MethodTypeInfo("(I)V");
            assertEquals(1, info.getParameterCount());
        }

        @Test
        void shouldCountMultiplePrimitiveParameters() {
            MethodTypeInfo info = new MethodTypeInfo("(IJFD)V");
            assertEquals(4, info.getParameterCount());
        }

        @Test
        void shouldCountObjectParameter() {
            MethodTypeInfo info = new MethodTypeInfo("(Ljava/lang/String;)V");
            assertEquals(1, info.getParameterCount());
        }

        @Test
        void shouldCountMixedParameters() {
            MethodTypeInfo info = new MethodTypeInfo("(ILjava/lang/String;J)V");
            assertEquals(3, info.getParameterCount());
        }

        @Test
        void shouldCountArrayParameters() {
            MethodTypeInfo info = new MethodTypeInfo("([I[Ljava/lang/Object;)V");
            assertEquals(2, info.getParameterCount());
        }

        @Test
        void shouldHandleNullDescriptor() {
            MethodTypeInfo info = new MethodTypeInfo(null);
            assertEquals(0, info.getParameterCount());
        }
    }

    @Nested
    class ParameterTypesTests {
        @Test
        void shouldReturnEmptyForNoParameters() {
            MethodTypeInfo info = new MethodTypeInfo("()V");
            String[] types = info.getParameterTypes();
            assertEquals(0, types.length);
        }

        @Test
        void shouldReturnPrimitiveTypes() {
            MethodTypeInfo info = new MethodTypeInfo("(IJFD)V");
            String[] types = info.getParameterTypes();

            assertEquals(4, types.length);
            assertEquals("I", types[0]);
            assertEquals("J", types[1]);
            assertEquals("F", types[2]);
            assertEquals("D", types[3]);
        }

        @Test
        void shouldReturnObjectTypes() {
            MethodTypeInfo info = new MethodTypeInfo("(Ljava/lang/String;Ljava/util/List;)V");
            String[] types = info.getParameterTypes();

            assertEquals(2, types.length);
            assertEquals("Ljava/lang/String;", types[0]);
            assertEquals("Ljava/util/List;", types[1]);
        }

        @Test
        void shouldReturnArrayTypes() {
            MethodTypeInfo info = new MethodTypeInfo("([I[[Ljava/lang/Object;)V");
            String[] types = info.getParameterTypes();

            assertEquals(2, types.length);
            assertEquals("[I", types[0]);
            assertEquals("[[Ljava/lang/Object;", types[1]);
        }

        @Test
        void shouldReturnMixedTypes() {
            MethodTypeInfo info = new MethodTypeInfo("(ILjava/lang/String;[D)Ljava/lang/Object;");
            String[] types = info.getParameterTypes();

            assertEquals(3, types.length);
            assertEquals("I", types[0]);
            assertEquals("Ljava/lang/String;", types[1]);
            assertEquals("[D", types[2]);
        }
    }

    @Nested
    class ParameterSlotsTests {
        @Test
        void shouldReturnZeroForNoParameters() {
            MethodTypeInfo info = new MethodTypeInfo("()V");
            assertEquals(0, info.getParameterSlots());
        }

        @Test
        void shouldReturnOneSlotForInt() {
            MethodTypeInfo info = new MethodTypeInfo("(I)V");
            assertEquals(1, info.getParameterSlots());
        }

        @Test
        void shouldReturnTwoSlotsForLong() {
            MethodTypeInfo info = new MethodTypeInfo("(J)V");
            assertEquals(2, info.getParameterSlots());
        }

        @Test
        void shouldReturnTwoSlotsForDouble() {
            MethodTypeInfo info = new MethodTypeInfo("(D)V");
            assertEquals(2, info.getParameterSlots());
        }

        @Test
        void shouldReturnOneSlotForFloat() {
            MethodTypeInfo info = new MethodTypeInfo("(F)V");
            assertEquals(1, info.getParameterSlots());
        }

        @Test
        void shouldReturnOneSlotForObject() {
            MethodTypeInfo info = new MethodTypeInfo("(Ljava/lang/String;)V");
            assertEquals(1, info.getParameterSlots());
        }

        @Test
        void shouldReturnOneSlotForArray() {
            MethodTypeInfo info = new MethodTypeInfo("([I)V");
            assertEquals(1, info.getParameterSlots());
        }

        @Test
        void shouldCalculateMixedSlots() {
            MethodTypeInfo info = new MethodTypeInfo("(IJDFLjava/lang/Object;)V");
            assertEquals(7, info.getParameterSlots());
        }

        @Test
        void shouldHandleNullDescriptor() {
            MethodTypeInfo info = new MethodTypeInfo(null);
            assertEquals(0, info.getParameterSlots());
        }
    }

    @Nested
    class ToStringTests {
        @Test
        void shouldFormatToString() {
            MethodTypeInfo info = new MethodTypeInfo("(IJ)Ljava/lang/String;");
            String result = info.toString();

            assertTrue(result.contains("(IJ)Ljava/lang/String;") || result.contains("desc"));
        }
    }
}
