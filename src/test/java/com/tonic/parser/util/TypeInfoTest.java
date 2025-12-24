package com.tonic.parser.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypeInfoTest {

    @Nested
    class PrimitiveTypeConstants {

        @Test
        void voidType() {
            assertEquals("V", TypeInfo.VOID.getDescriptor());
            assertEquals(0, TypeInfo.VOID.getSize());
            assertTrue(TypeInfo.VOID.isPrimitive());
            assertFalse(TypeInfo.VOID.isArray());
            assertTrue(TypeInfo.VOID.isVoid());
        }

        @Test
        void booleanType() {
            assertEquals("Z", TypeInfo.BOOLEAN.getDescriptor());
            assertEquals(1, TypeInfo.BOOLEAN.getSize());
            assertTrue(TypeInfo.BOOLEAN.isPrimitive());
            assertFalse(TypeInfo.BOOLEAN.isArray());
        }

        @Test
        void byteType() {
            assertEquals("B", TypeInfo.BYTE.getDescriptor());
            assertEquals(1, TypeInfo.BYTE.getSize());
            assertTrue(TypeInfo.BYTE.isPrimitive());
        }

        @Test
        void charType() {
            assertEquals("C", TypeInfo.CHAR.getDescriptor());
            assertEquals(1, TypeInfo.CHAR.getSize());
            assertTrue(TypeInfo.CHAR.isPrimitive());
        }

        @Test
        void shortType() {
            assertEquals("S", TypeInfo.SHORT.getDescriptor());
            assertEquals(1, TypeInfo.SHORT.getSize());
            assertTrue(TypeInfo.SHORT.isPrimitive());
        }

        @Test
        void intType() {
            assertEquals("I", TypeInfo.INT.getDescriptor());
            assertEquals(1, TypeInfo.INT.getSize());
            assertTrue(TypeInfo.INT.isPrimitive());
        }

        @Test
        void floatType() {
            assertEquals("F", TypeInfo.FLOAT.getDescriptor());
            assertEquals(1, TypeInfo.FLOAT.getSize());
            assertTrue(TypeInfo.FLOAT.isPrimitive());
        }

        @Test
        void longType() {
            assertEquals("J", TypeInfo.LONG.getDescriptor());
            assertEquals(2, TypeInfo.LONG.getSize());
            assertTrue(TypeInfo.LONG.isPrimitive());
            assertTrue(TypeInfo.LONG.isWide());
        }

        @Test
        void doubleType() {
            assertEquals("D", TypeInfo.DOUBLE.getDescriptor());
            assertEquals(2, TypeInfo.DOUBLE.getSize());
            assertTrue(TypeInfo.DOUBLE.isPrimitive());
            assertTrue(TypeInfo.DOUBLE.isWide());
        }
    }

    @Nested
    class OfMethodTests {

        @Test
        void ofPrimitiveV() {
            TypeInfo type = TypeInfo.of("V");
            assertSame(TypeInfo.VOID, type);
        }

        @Test
        void ofPrimitiveZ() {
            TypeInfo type = TypeInfo.of("Z");
            assertSame(TypeInfo.BOOLEAN, type);
        }

        @Test
        void ofPrimitiveB() {
            TypeInfo type = TypeInfo.of("B");
            assertSame(TypeInfo.BYTE, type);
        }

        @Test
        void ofPrimitiveC() {
            TypeInfo type = TypeInfo.of("C");
            assertSame(TypeInfo.CHAR, type);
        }

        @Test
        void ofPrimitiveS() {
            TypeInfo type = TypeInfo.of("S");
            assertSame(TypeInfo.SHORT, type);
        }

        @Test
        void ofPrimitiveI() {
            TypeInfo type = TypeInfo.of("I");
            assertSame(TypeInfo.INT, type);
        }

        @Test
        void ofPrimitiveF() {
            TypeInfo type = TypeInfo.of("F");
            assertSame(TypeInfo.FLOAT, type);
        }

        @Test
        void ofPrimitiveJ() {
            TypeInfo type = TypeInfo.of("J");
            assertSame(TypeInfo.LONG, type);
        }

        @Test
        void ofPrimitiveD() {
            TypeInfo type = TypeInfo.of("D");
            assertSame(TypeInfo.DOUBLE, type);
        }

        @Test
        void ofObjectType() {
            TypeInfo type = TypeInfo.of("Ljava/lang/String;");
            assertEquals("Ljava/lang/String;", type.getDescriptor());
            assertEquals(1, type.getSize());
            assertFalse(type.isPrimitive());
            assertFalse(type.isArray());
            assertTrue(type.isReference());
        }

        @Test
        void ofSingleDimensionArray() {
            TypeInfo type = TypeInfo.of("[I");
            assertEquals("[I", type.getDescriptor());
            assertEquals(1, type.getSize());
            assertFalse(type.isPrimitive());
            assertTrue(type.isArray());
            assertTrue(type.isReference());
        }

        @Test
        void ofMultiDimensionArray() {
            TypeInfo type = TypeInfo.of("[[Ljava/lang/Object;");
            assertEquals("[[Ljava/lang/Object;", type.getDescriptor());
            assertTrue(type.isArray());
            assertEquals(1, type.getSize());
        }

        @Test
        void ofNullThrows() {
            assertThrows(IllegalArgumentException.class, () -> TypeInfo.of(null));
        }

        @Test
        void ofEmptyThrows() {
            assertThrows(IllegalArgumentException.class, () -> TypeInfo.of(""));
        }

        @Test
        void ofInvalidDescriptorThrows() {
            assertThrows(IllegalArgumentException.class, () -> TypeInfo.of("X"));
            assertThrows(IllegalArgumentException.class, () -> TypeInfo.of("invalid"));
        }
    }

    @Nested
    class ForClassNameTests {

        @Test
        void forClassName() {
            TypeInfo type = TypeInfo.forClassName("java/lang/String");
            assertEquals("Ljava/lang/String;", type.getDescriptor());
            assertEquals(1, type.getSize());
            assertFalse(type.isPrimitive());
            assertFalse(type.isArray());
        }

        @Test
        void forClassNameNested() {
            TypeInfo type = TypeInfo.forClassName("com/example/Outer$Inner");
            assertEquals("Lcom/example/Outer$Inner;", type.getDescriptor());
        }

        @Test
        void forClassNameNullThrows() {
            assertThrows(IllegalArgumentException.class, () -> TypeInfo.forClassName(null));
        }

        @Test
        void forClassNameEmptyThrows() {
            assertThrows(IllegalArgumentException.class, () -> TypeInfo.forClassName(""));
        }
    }

    @Nested
    class ForArrayTypeTests {

        @Test
        void forArrayTypeSingleDimension() {
            TypeInfo type = TypeInfo.forArrayType(TypeInfo.INT, 1);
            assertEquals("[I", type.getDescriptor());
            assertTrue(type.isArray());
            assertEquals(1, type.getSize());
        }

        @Test
        void forArrayTypeMultiDimension() {
            TypeInfo type = TypeInfo.forArrayType(TypeInfo.INT, 3);
            assertEquals("[[[I", type.getDescriptor());
            assertTrue(type.isArray());
        }

        @Test
        void forArrayTypeObject() {
            TypeInfo elementType = TypeInfo.forClassName("java/lang/String");
            TypeInfo arrayType = TypeInfo.forArrayType(elementType, 1);
            assertEquals("[Ljava/lang/String;", arrayType.getDescriptor());
            assertTrue(arrayType.isArray());
        }

        @Test
        void forArrayTypeMultiDimensionObject() {
            TypeInfo elementType = TypeInfo.forClassName("java/lang/Object");
            TypeInfo arrayType = TypeInfo.forArrayType(elementType, 2);
            assertEquals("[[Ljava/lang/Object;", arrayType.getDescriptor());
            assertTrue(arrayType.isArray());
        }
    }

    @Nested
    class PropertyTests {

        @Test
        void isVoidOnlyForVoid() {
            assertTrue(TypeInfo.VOID.isVoid());
            assertFalse(TypeInfo.INT.isVoid());
            assertFalse(TypeInfo.of("Ljava/lang/String;").isVoid());
        }

        @Test
        void isWideForLongAndDouble() {
            assertTrue(TypeInfo.LONG.isWide());
            assertTrue(TypeInfo.DOUBLE.isWide());
            assertFalse(TypeInfo.INT.isWide());
            assertFalse(TypeInfo.FLOAT.isWide());
            assertFalse(TypeInfo.VOID.isWide());
        }

        @Test
        void isReferenceForObjectsAndArrays() {
            assertFalse(TypeInfo.INT.isReference());
            assertFalse(TypeInfo.VOID.isReference());
            assertTrue(TypeInfo.of("Ljava/lang/String;").isReference());
            assertTrue(TypeInfo.of("[I").isReference());
            assertTrue(TypeInfo.of("[[Ljava/lang/Object;").isReference());
        }

        @Test
        void isPrimitiveForPrimitives() {
            assertTrue(TypeInfo.INT.isPrimitive());
            assertTrue(TypeInfo.VOID.isPrimitive());
            assertTrue(TypeInfo.BOOLEAN.isPrimitive());
            assertFalse(TypeInfo.of("Ljava/lang/String;").isPrimitive());
            assertFalse(TypeInfo.of("[I").isPrimitive());
        }

        @Test
        void isArrayForArrayTypes() {
            assertFalse(TypeInfo.INT.isArray());
            assertFalse(TypeInfo.of("Ljava/lang/String;").isArray());
            assertTrue(TypeInfo.of("[I").isArray());
            assertTrue(TypeInfo.of("[[Ljava/lang/Object;").isArray());
        }
    }

    @Nested
    class ClassNameTests {

        @Test
        void getClassNameForPrimitive() {
            assertNull(TypeInfo.INT.getClassName());
            assertNull(TypeInfo.VOID.getClassName());
        }

        @Test
        void getClassNameForObject() {
            TypeInfo type = TypeInfo.of("Ljava/lang/String;");
            assertEquals("java/lang/String", type.getClassName());
        }

        @Test
        void getClassNameForArray() {
            TypeInfo type = TypeInfo.of("[I");
            assertEquals("[I", type.getClassName());
        }

        @Test
        void getClassNameForObjectArray() {
            TypeInfo type = TypeInfo.of("[Ljava/lang/Object;");
            assertEquals("[Ljava/lang/Object;", type.getClassName());
        }
    }

    @Nested
    class InternalNameTests {

        @Test
        void getInternalNameForPrimitive() {
            assertEquals("I", TypeInfo.INT.getInternalName());
            assertEquals("V", TypeInfo.VOID.getInternalName());
        }

        @Test
        void getInternalNameForObject() {
            TypeInfo type = TypeInfo.of("Ljava/lang/String;");
            assertEquals("java/lang/String", type.getInternalName());
        }

        @Test
        void getInternalNameForArray() {
            TypeInfo type = TypeInfo.of("[I");
            assertEquals("[I", type.getInternalName());
        }
    }

    @Nested
    class ArrayOperations {

        @Test
        void getElementTypeForSingleDimensionArray() {
            TypeInfo arrayType = TypeInfo.of("[I");
            TypeInfo elementType = arrayType.getElementType();
            assertSame(TypeInfo.INT, elementType);
        }

        @Test
        void getElementTypeForMultiDimensionArray() {
            TypeInfo arrayType = TypeInfo.of("[[I");
            TypeInfo elementType = arrayType.getElementType();
            assertEquals("[I", elementType.getDescriptor());
            assertTrue(elementType.isArray());
        }

        @Test
        void getElementTypeForObjectArray() {
            TypeInfo arrayType = TypeInfo.of("[Ljava/lang/String;");
            TypeInfo elementType = arrayType.getElementType();
            assertEquals("Ljava/lang/String;", elementType.getDescriptor());
            assertFalse(elementType.isArray());
        }

        @Test
        void getElementTypeForNonArray() {
            assertNull(TypeInfo.INT.getElementType());
            assertNull(TypeInfo.of("Ljava/lang/String;").getElementType());
        }

        @Test
        void getArrayDimensionsForNonArray() {
            assertEquals(0, TypeInfo.INT.getArrayDimensions());
            assertEquals(0, TypeInfo.of("Ljava/lang/String;").getArrayDimensions());
        }

        @Test
        void getArrayDimensionsForSingleDimension() {
            TypeInfo type = TypeInfo.of("[I");
            assertEquals(1, type.getArrayDimensions());
        }

        @Test
        void getArrayDimensionsForMultiDimension() {
            TypeInfo type = TypeInfo.of("[[I");
            assertEquals(2, type.getArrayDimensions());
        }

        @Test
        void getArrayDimensionsForFiveDimensions() {
            TypeInfo type = TypeInfo.of("[[[[[Ljava/lang/Object;");
            assertEquals(5, type.getArrayDimensions());
        }
    }

    @Nested
    class EqualsAndHashCodeTests {

        @Test
        void equalsSameInstance() {
            TypeInfo type = TypeInfo.of("Ljava/lang/String;");
            assertEquals(type, type);
        }

        @Test
        void equalsSameDescriptor() {
            TypeInfo type1 = TypeInfo.of("Ljava/lang/String;");
            TypeInfo type2 = TypeInfo.of("Ljava/lang/String;");
            assertEquals(type1, type2);
        }

        @Test
        void equalsPrimitiveConstants() {
            assertEquals(TypeInfo.INT, TypeInfo.of("I"));
            assertEquals(TypeInfo.VOID, TypeInfo.of("V"));
        }

        @Test
        void notEqualsDifferentDescriptor() {
            TypeInfo type1 = TypeInfo.of("Ljava/lang/String;");
            TypeInfo type2 = TypeInfo.of("Ljava/lang/Object;");
            assertNotEquals(type1, type2);
        }

        @Test
        void notEqualsNull() {
            TypeInfo type = TypeInfo.of("Ljava/lang/String;");
            assertNotEquals(type, null);
        }

        @Test
        void notEqualsDifferentType() {
            TypeInfo type = TypeInfo.of("Ljava/lang/String;");
            assertNotEquals(type, "Ljava/lang/String;");
        }

        @Test
        void hashCodeConsistent() {
            TypeInfo type1 = TypeInfo.of("Ljava/lang/String;");
            TypeInfo type2 = TypeInfo.of("Ljava/lang/String;");
            assertEquals(type1.hashCode(), type2.hashCode());
        }

        @Test
        void hashCodeMatchesDescriptor() {
            TypeInfo type = TypeInfo.of("Ljava/lang/String;");
            assertEquals("Ljava/lang/String;".hashCode(), type.hashCode());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringReturnsDescript() {
            assertEquals("I", TypeInfo.INT.toString());
            assertEquals("V", TypeInfo.VOID.toString());
            assertEquals("Ljava/lang/String;", TypeInfo.of("Ljava/lang/String;").toString());
            assertEquals("[I", TypeInfo.of("[I").toString());
        }
    }
}
