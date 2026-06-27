package com.tonic.analysis.execution.dispatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class MethodHandleInfoTest {

    @Nested
    class ConstructorTests {
        @Test
        void shouldCreateWithAllParameters() {
            MethodHandleInfo info = new MethodHandleInfo(
                MethodHandleInfo.REF_invokeVirtual,
                "java/lang/String",
                "length",
                "()I"
            );

            assertEquals(MethodHandleInfo.REF_invokeVirtual, info.getReferenceKind());
            assertEquals("java/lang/String", info.getOwner());
            assertEquals("length", info.getName());
            assertEquals("()I", info.getDescriptor());
        }
    }

    @Nested
    class ReferenceKindConstantsTests {
        @Test
        void shouldHaveCorrectGetFieldValue() {
            assertEquals(1, MethodHandleInfo.REF_getField);
        }

        @Test
        void shouldHaveCorrectGetStaticValue() {
            assertEquals(2, MethodHandleInfo.REF_getStatic);
        }

        @Test
        void shouldHaveCorrectPutFieldValue() {
            assertEquals(3, MethodHandleInfo.REF_putField);
        }

        @Test
        void shouldHaveCorrectPutStaticValue() {
            assertEquals(4, MethodHandleInfo.REF_putStatic);
        }

        @Test
        void shouldHaveCorrectInvokeVirtualValue() {
            assertEquals(5, MethodHandleInfo.REF_invokeVirtual);
        }

        @Test
        void shouldHaveCorrectInvokeStaticValue() {
            assertEquals(6, MethodHandleInfo.REF_invokeStatic);
        }

        @Test
        void shouldHaveCorrectInvokeSpecialValue() {
            assertEquals(7, MethodHandleInfo.REF_invokeSpecial);
        }

        @Test
        void shouldHaveCorrectNewInvokeSpecialValue() {
            assertEquals(8, MethodHandleInfo.REF_newInvokeSpecial);
        }

        @Test
        void shouldHaveCorrectInvokeInterfaceValue() {
            assertEquals(9, MethodHandleInfo.REF_invokeInterface);
        }
    }

    @Nested
    class FieldReferenceTests {
        @Test
        void shouldIdentifyGetFieldAsFieldReference() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_getField, "Owner", "field", "I");
            assertTrue(info.isFieldReference());
        }

        @Test
        void shouldIdentifyGetStaticAsFieldReference() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_getStatic, "Owner", "field", "I");
            assertTrue(info.isFieldReference());
        }

        @Test
        void shouldIdentifyPutFieldAsFieldReference() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_putField, "Owner", "field", "I");
            assertTrue(info.isFieldReference());
        }

        @Test
        void shouldIdentifyPutStaticAsFieldReference() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_putStatic, "Owner", "field", "I");
            assertTrue(info.isFieldReference());
        }

        @Test
        void shouldNotIdentifyInvokeVirtualAsFieldReference() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeVirtual, "Owner", "method", "()V");
            assertFalse(info.isFieldReference());
        }
    }

    @Nested
    class MethodReferenceTests {
        @Test
        void shouldIdentifyInvokeVirtualAsMethodReference() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeVirtual, "Owner", "method", "()V");
            assertTrue(info.isMethodReference());
        }

        @Test
        void shouldIdentifyInvokeStaticAsMethodReference() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeStatic, "Owner", "method", "()V");
            assertTrue(info.isMethodReference());
        }

        @Test
        void shouldIdentifyInvokeSpecialAsMethodReference() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeSpecial, "Owner", "method", "()V");
            assertTrue(info.isMethodReference());
        }

        @Test
        void shouldIdentifyNewInvokeSpecialAsMethodReference() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_newInvokeSpecial, "Owner", "<init>", "()V");
            assertTrue(info.isMethodReference());
        }

        @Test
        void shouldIdentifyInvokeInterfaceAsMethodReference() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeInterface, "Owner", "method", "()V");
            assertTrue(info.isMethodReference());
        }

        @Test
        void shouldNotIdentifyGetFieldAsMethodReference() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_getField, "Owner", "field", "I");
            assertFalse(info.isMethodReference());
        }
    }

    @Nested
    class GetterSetterTests {
        @Test
        void shouldIdentifyGetFieldAsGetter() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_getField, "Owner", "field", "I");
            assertTrue(info.isGetter());
        }

        @Test
        void shouldIdentifyGetStaticAsGetter() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_getStatic, "Owner", "field", "I");
            assertTrue(info.isGetter());
        }

        @Test
        void shouldNotIdentifyPutFieldAsGetter() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_putField, "Owner", "field", "I");
            assertFalse(info.isGetter());
        }

        @Test
        void shouldIdentifyPutFieldAsSetter() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_putField, "Owner", "field", "I");
            assertTrue(info.isSetter());
        }

        @Test
        void shouldIdentifyPutStaticAsSetter() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_putStatic, "Owner", "field", "I");
            assertTrue(info.isSetter());
        }

        @Test
        void shouldNotIdentifyGetFieldAsSetter() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_getField, "Owner", "field", "I");
            assertFalse(info.isSetter());
        }
    }

    @Nested
    class StaticTests {
        @Test
        void shouldIdentifyGetStaticAsStatic() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_getStatic, "Owner", "field", "I");
            assertTrue(info.isStatic());
        }

        @Test
        void shouldIdentifyPutStaticAsStatic() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_putStatic, "Owner", "field", "I");
            assertTrue(info.isStatic());
        }

        @Test
        void shouldIdentifyInvokeStaticAsStatic() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeStatic, "Owner", "method", "()V");
            assertTrue(info.isStatic());
        }

        @Test
        void shouldNotIdentifyGetFieldAsStatic() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_getField, "Owner", "field", "I");
            assertFalse(info.isStatic());
        }

        @Test
        void shouldNotIdentifyInvokeVirtualAsStatic() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeVirtual, "Owner", "method", "()V");
            assertFalse(info.isStatic());
        }
    }

    @Nested
    class ConstructorTests2 {
        @Test
        void shouldIdentifyNewInvokeSpecialAsConstructor() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_newInvokeSpecial, "Owner", "<init>", "()V");
            assertTrue(info.isConstructor());
        }

        @Test
        void shouldNotIdentifyInvokeSpecialAsConstructor() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeSpecial, "Owner", "<init>", "()V");
            assertFalse(info.isConstructor());
        }
    }

    @Nested
    class ReferenceKindNameTests {
        @Test
        void shouldReturnGetFieldName() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_getField, "O", "f", "I");
            assertEquals("REF_getField", info.getReferenceKindName());
        }

        @Test
        void shouldReturnGetStaticName() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_getStatic, "O", "f", "I");
            assertEquals("REF_getStatic", info.getReferenceKindName());
        }

        @Test
        void shouldReturnPutFieldName() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_putField, "O", "f", "I");
            assertEquals("REF_putField", info.getReferenceKindName());
        }

        @Test
        void shouldReturnPutStaticName() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_putStatic, "O", "f", "I");
            assertEquals("REF_putStatic", info.getReferenceKindName());
        }

        @Test
        void shouldReturnInvokeVirtualName() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeVirtual, "O", "m", "()V");
            assertEquals("REF_invokeVirtual", info.getReferenceKindName());
        }

        @Test
        void shouldReturnInvokeStaticName() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeStatic, "O", "m", "()V");
            assertEquals("REF_invokeStatic", info.getReferenceKindName());
        }

        @Test
        void shouldReturnInvokeSpecialName() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeSpecial, "O", "m", "()V");
            assertEquals("REF_invokeSpecial", info.getReferenceKindName());
        }

        @Test
        void shouldReturnNewInvokeSpecialName() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_newInvokeSpecial, "O", "<init>", "()V");
            assertEquals("REF_newInvokeSpecial", info.getReferenceKindName());
        }

        @Test
        void shouldReturnInvokeInterfaceName() {
            MethodHandleInfo info = new MethodHandleInfo(MethodHandleInfo.REF_invokeInterface, "O", "m", "()V");
            assertEquals("REF_invokeInterface", info.getReferenceKindName());
        }

        @Test
        void shouldReturnUnknownForInvalidKind() {
            MethodHandleInfo info = new MethodHandleInfo(99, "O", "m", "()V");
            assertTrue(info.getReferenceKindName().contains("unknown"));
        }
    }

    @Nested
    class ToStringTests {
        @Test
        void shouldFormatToString() {
            MethodHandleInfo info = new MethodHandleInfo(
                MethodHandleInfo.REF_invokeVirtual,
                "java/lang/String",
                "length",
                "()I"
            );
            String result = info.toString();

            assertTrue(result.contains("java/lang/String"));
            assertTrue(result.contains("length"));
            assertTrue(result.contains("()I"));
        }
    }
}
