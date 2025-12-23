package com.tonic.analysis.execution.dispatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class ConstantDynamicInfoTest {

    @Nested
    class ConstructorTests {
        @Test
        void shouldCreateWithAllParameters() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(5, "myConstant", "Ljava/lang/String;", 42);

            assertEquals(5, info.getBootstrapMethodIndex());
            assertEquals("myConstant", info.getName());
            assertEquals("Ljava/lang/String;", info.getDescriptor());
            assertEquals(42, info.getConstantPoolIndex());
        }

        @Test
        void shouldHandleNullName() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, null, "I", 1);
            assertNull(info.getName());
        }

        @Test
        void shouldHandleNullDescriptor() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "test", null, 1);
            assertNull(info.getDescriptor());
        }
    }

    @Nested
    class WideTypeTests {
        @Test
        void shouldIdentifyLongAsWide() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "longConst", "J", 1);
            assertTrue(info.isWideType());
        }

        @Test
        void shouldIdentifyDoubleAsWide() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "doubleConst", "D", 1);
            assertTrue(info.isWideType());
        }

        @Test
        void shouldNotIdentifyIntAsWide() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "intConst", "I", 1);
            assertFalse(info.isWideType());
        }

        @Test
        void shouldNotIdentifyFloatAsWide() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "floatConst", "F", 1);
            assertFalse(info.isWideType());
        }

        @Test
        void shouldNotIdentifyReferenceAsWide() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "refConst", "Ljava/lang/Object;", 1);
            assertFalse(info.isWideType());
        }

        @Test
        void shouldHandleNullDescriptorForWideCheck() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "test", null, 1);
            assertFalse(info.isWideType());
        }
    }

    @Nested
    class PrimitiveTypeTests {
        @Test
        void shouldIdentifyIntAsPrimitive() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "I", 1);
            assertTrue(info.isPrimitive());
        }

        @Test
        void shouldIdentifyLongAsPrimitive() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "J", 1);
            assertTrue(info.isPrimitive());
        }

        @Test
        void shouldIdentifyFloatAsPrimitive() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "F", 1);
            assertTrue(info.isPrimitive());
        }

        @Test
        void shouldIdentifyDoubleAsPrimitive() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "D", 1);
            assertTrue(info.isPrimitive());
        }

        @Test
        void shouldIdentifyBooleanAsPrimitive() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "Z", 1);
            assertTrue(info.isPrimitive());
        }

        @Test
        void shouldIdentifyByteAsPrimitive() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "B", 1);
            assertTrue(info.isPrimitive());
        }

        @Test
        void shouldIdentifyCharAsPrimitive() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "C", 1);
            assertTrue(info.isPrimitive());
        }

        @Test
        void shouldIdentifyShortAsPrimitive() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "S", 1);
            assertTrue(info.isPrimitive());
        }

        @Test
        void shouldNotIdentifyReferenceAsPrimitive() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "Ljava/lang/String;", 1);
            assertFalse(info.isPrimitive());
        }

        @Test
        void shouldNotIdentifyArrayAsPrimitive() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "[I", 1);
            assertFalse(info.isPrimitive());
        }
    }

    @Nested
    class ReferenceTypeTests {
        @Test
        void shouldIdentifyObjectAsReference() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "Ljava/lang/Object;", 1);
            assertTrue(info.isReference());
        }

        @Test
        void shouldIdentifyArrayAsReference() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "[Ljava/lang/String;", 1);
            assertTrue(info.isReference());
        }

        @Test
        void shouldIdentifyPrimitiveArrayAsReference() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "[I", 1);
            assertTrue(info.isReference());
        }

        @Test
        void shouldNotIdentifyPrimitiveAsReference() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(0, "c", "I", 1);
            assertFalse(info.isReference());
        }
    }

    @Nested
    class ToStringTests {
        @Test
        void shouldFormatToString() {
            ConstantDynamicInfo info = new ConstantDynamicInfo(3, "myConst", "I", 10);
            String result = info.toString();

            assertTrue(result.contains("myConst"));
            assertTrue(result.contains("I"));
            assertTrue(result.contains("3") || result.contains("bsm=3"));
        }
    }
}
