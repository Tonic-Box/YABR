package com.tonic.utill;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DescriptorUtilTest {

    @Nested
    class CategorizationTests {

        @Test
        void categorizeVoidChar() {
            assertEquals(DescriptorUtil.DescriptorCategory.VOID, DescriptorUtil.categorize('V'));
        }

        @Test
        void categorizeBooleanChar() {
            assertEquals(DescriptorUtil.DescriptorCategory.BOOLEAN, DescriptorUtil.categorize('Z'));
        }

        @Test
        void categorizeByteChar() {
            assertEquals(DescriptorUtil.DescriptorCategory.BYTE, DescriptorUtil.categorize('B'));
        }

        @Test
        void categorizeCharChar() {
            assertEquals(DescriptorUtil.DescriptorCategory.CHAR, DescriptorUtil.categorize('C'));
        }

        @Test
        void categorizeShortChar() {
            assertEquals(DescriptorUtil.DescriptorCategory.SHORT, DescriptorUtil.categorize('S'));
        }

        @Test
        void categorizeIntChar() {
            assertEquals(DescriptorUtil.DescriptorCategory.INT, DescriptorUtil.categorize('I'));
        }

        @Test
        void categorizeLongChar() {
            assertEquals(DescriptorUtil.DescriptorCategory.LONG, DescriptorUtil.categorize('J'));
        }

        @Test
        void categorizeFloatChar() {
            assertEquals(DescriptorUtil.DescriptorCategory.FLOAT, DescriptorUtil.categorize('F'));
        }

        @Test
        void categorizeDoubleChar() {
            assertEquals(DescriptorUtil.DescriptorCategory.DOUBLE, DescriptorUtil.categorize('D'));
        }

        @Test
        void categorizeObjectChar() {
            assertEquals(DescriptorUtil.DescriptorCategory.OBJECT, DescriptorUtil.categorize('L'));
        }

        @Test
        void categorizeArrayChar() {
            assertEquals(DescriptorUtil.DescriptorCategory.ARRAY, DescriptorUtil.categorize('['));
        }

        @Test
        void categorizeInvalidChar() {
            assertNull(DescriptorUtil.categorize('X'));
        }

        @Test
        void categorizeStringVoid() {
            assertEquals(DescriptorUtil.DescriptorCategory.VOID, DescriptorUtil.categorize("V"));
        }

        @Test
        void categorizeStringInt() {
            assertEquals(DescriptorUtil.DescriptorCategory.INT, DescriptorUtil.categorize("I"));
        }

        @Test
        void categorizeStringObject() {
            assertEquals(DescriptorUtil.DescriptorCategory.OBJECT, DescriptorUtil.categorize("Ljava/lang/String;"));
        }

        @Test
        void categorizeStringArray() {
            assertEquals(DescriptorUtil.DescriptorCategory.ARRAY, DescriptorUtil.categorize("[I"));
        }

        @Test
        void categorizeNullString() {
            assertNull(DescriptorUtil.categorize((String) null));
        }

        @Test
        void categorizeEmptyString() {
            assertNull(DescriptorUtil.categorize(""));
        }
    }

    @Nested
    class PrimitiveTests {

        @Test
        void isPrimitiveVoid() {
            assertTrue(DescriptorUtil.isPrimitive('V'));
        }

        @Test
        void isPrimitiveBoolean() {
            assertTrue(DescriptorUtil.isPrimitive('Z'));
        }

        @Test
        void isPrimitiveByte() {
            assertTrue(DescriptorUtil.isPrimitive('B'));
        }

        @Test
        void isPrimitiveChar() {
            assertTrue(DescriptorUtil.isPrimitive('C'));
        }

        @Test
        void isPrimitiveShort() {
            assertTrue(DescriptorUtil.isPrimitive('S'));
        }

        @Test
        void isPrimitiveInt() {
            assertTrue(DescriptorUtil.isPrimitive('I'));
        }

        @Test
        void isPrimitiveLong() {
            assertTrue(DescriptorUtil.isPrimitive('J'));
        }

        @Test
        void isPrimitiveFloat() {
            assertTrue(DescriptorUtil.isPrimitive('F'));
        }

        @Test
        void isPrimitiveDouble() {
            assertTrue(DescriptorUtil.isPrimitive('D'));
        }

        @Test
        void isNotPrimitiveObject() {
            assertFalse(DescriptorUtil.isPrimitive('L'));
        }

        @Test
        void isNotPrimitiveArray() {
            assertFalse(DescriptorUtil.isPrimitive('['));
        }
    }

    @Nested
    class WideTypeTests {

        @Test
        void isWideTypeLong() {
            assertTrue(DescriptorUtil.isWideType("J"));
        }

        @Test
        void isWideTypeDouble() {
            assertTrue(DescriptorUtil.isWideType("D"));
        }

        @Test
        void isNotWideTypeInt() {
            assertFalse(DescriptorUtil.isWideType("I"));
        }

        @Test
        void isNotWideTypeVoid() {
            assertFalse(DescriptorUtil.isWideType("V"));
        }

        @Test
        void isNotWideTypeObject() {
            assertFalse(DescriptorUtil.isWideType("Ljava/lang/String;"));
        }

        @Test
        void isNotWideTypeNull() {
            assertFalse(DescriptorUtil.isWideType(null));
        }

        @Test
        void isNotWideTypeEmpty() {
            assertFalse(DescriptorUtil.isWideType(""));
        }
    }

    @Nested
    class TypeSlotsTests {

        @Test
        void getTypeSlotsLong() {
            assertEquals(2, DescriptorUtil.getTypeSlots("J"));
        }

        @Test
        void getTypeSlotsDouble() {
            assertEquals(2, DescriptorUtil.getTypeSlots("D"));
        }

        @Test
        void getTypeSlotsVoid() {
            assertEquals(0, DescriptorUtil.getTypeSlots("V"));
        }

        @Test
        void getTypeSlotsInt() {
            assertEquals(1, DescriptorUtil.getTypeSlots("I"));
        }

        @Test
        void getTypeSlotsObject() {
            assertEquals(1, DescriptorUtil.getTypeSlots("Ljava/lang/String;"));
        }

        @Test
        void getTypeSlotsNull() {
            assertEquals(1, DescriptorUtil.getTypeSlots(null));
        }

        @Test
        void getTypeSlotsEmpty() {
            assertEquals(1, DescriptorUtil.getTypeSlots(""));
        }
    }

    @Nested
    class ParameterParsingTests {

        @Test
        void parseParameterDescriptorsSimple() {
            List<String> params = DescriptorUtil.parseParameterDescriptors("(I)V");
            assertEquals(1, params.size());
            assertEquals("I", params.get(0));
        }

        @Test
        void parseParameterDescriptorsMultiple() {
            List<String> params = DescriptorUtil.parseParameterDescriptors("(IJLjava/lang/String;)V");
            assertEquals(3, params.size());
            assertEquals("I", params.get(0));
            assertEquals("J", params.get(1));
            assertEquals("Ljava/lang/String;", params.get(2));
        }

        @Test
        void parseParameterDescriptorsArray() {
            List<String> params = DescriptorUtil.parseParameterDescriptors("([I)V");
            assertEquals(1, params.size());
            assertEquals("[I", params.get(0));
        }

        @Test
        void parseParameterDescriptorsMultiDimensionalArray() {
            List<String> params = DescriptorUtil.parseParameterDescriptors("([[Ljava/lang/String;)V");
            assertEquals(1, params.size());
            assertEquals("[[Ljava/lang/String;", params.get(0));
        }

        @Test
        void parseParameterDescriptorsEmpty() {
            List<String> params = DescriptorUtil.parseParameterDescriptors("()V");
            assertTrue(params.isEmpty());
        }

        @Test
        void parseParameterDescriptorsNull() {
            List<String> params = DescriptorUtil.parseParameterDescriptors(null);
            assertTrue(params.isEmpty());
        }

        @Test
        void parseParameterDescriptorsNoOpenParen() {
            List<String> params = DescriptorUtil.parseParameterDescriptors("I)V");
            assertTrue(params.isEmpty());
        }

        @Test
        void parseParameterDescriptorsNoCloseParen() {
            List<String> params = DescriptorUtil.parseParameterDescriptors("(IV");
            assertTrue(params.isEmpty());
        }

        @Test
        void parseParameterDescriptorsComplexMix() {
            List<String> params = DescriptorUtil.parseParameterDescriptors("(I[JLjava/lang/Object;[[Ljava/lang/String;ZBCSD)V");
            assertEquals(9, params.size());
            assertEquals("I", params.get(0));
            assertEquals("[J", params.get(1));
            assertEquals("Ljava/lang/Object;", params.get(2));
            assertEquals("[[Ljava/lang/String;", params.get(3));
            assertEquals("Z", params.get(4));
            assertEquals("B", params.get(5));
            assertEquals("C", params.get(6));
            assertEquals("S", params.get(7));
            assertEquals("D", params.get(8));
        }
    }

    @Nested
    class ReturnDescriptorTests {

        @Test
        void parseReturnDescriptorVoid() {
            assertEquals("V", DescriptorUtil.parseReturnDescriptor("()V"));
        }

        @Test
        void parseReturnDescriptorInt() {
            assertEquals("I", DescriptorUtil.parseReturnDescriptor("()I"));
        }

        @Test
        void parseReturnDescriptorObject() {
            assertEquals("Ljava/lang/String;", DescriptorUtil.parseReturnDescriptor("()Ljava/lang/String;"));
        }

        @Test
        void parseReturnDescriptorWithParams() {
            assertEquals("I", DescriptorUtil.parseReturnDescriptor("(ILjava/lang/String;)I"));
        }

        @Test
        void parseReturnDescriptorNull() {
            assertNull(DescriptorUtil.parseReturnDescriptor(null));
        }

        @Test
        void parseReturnDescriptorNoCloseParen() {
            assertNull(DescriptorUtil.parseReturnDescriptor("(I"));
        }

        @Test
        void parseReturnDescriptorNoReturnType() {
            assertNull(DescriptorUtil.parseReturnDescriptor("()"));
        }
    }

    @Nested
    class CountingTests {

        @Test
        void countParametersZero() {
            assertEquals(0, DescriptorUtil.countParameters("()V"));
        }

        @Test
        void countParametersOne() {
            assertEquals(1, DescriptorUtil.countParameters("(I)V"));
        }

        @Test
        void countParametersMultiple() {
            assertEquals(3, DescriptorUtil.countParameters("(IJLjava/lang/String;)V"));
        }

        @Test
        void countParameterSlotsSimple() {
            assertEquals(1, DescriptorUtil.countParameterSlots("(I)V"));
        }

        @Test
        void countParameterSlotsWideTypes() {
            assertEquals(4, DescriptorUtil.countParameterSlots("(JD)V"));
        }

        @Test
        void countParameterSlotsMixed() {
            assertEquals(4, DescriptorUtil.countParameterSlots("(IJI)V"));
        }

        @Test
        void countParameterSlotsEmpty() {
            assertEquals(0, DescriptorUtil.countParameterSlots("()V"));
        }
    }

    @Nested
    class ClassNameExtractionTests {

        @Test
        void extractClassNamesSimpleObject() {
            Set<String> names = DescriptorUtil.extractClassNames("Ljava/lang/String;");
            assertEquals(1, names.size());
            assertTrue(names.contains("java/lang/String"));
        }

        @Test
        void extractClassNamesMultiple() {
            Set<String> names = DescriptorUtil.extractClassNames("(Ljava/lang/String;Ljava/util/List;)Ljava/lang/Object;");
            assertEquals(3, names.size());
            assertTrue(names.contains("java/lang/String"));
            assertTrue(names.contains("java/util/List"));
            assertTrue(names.contains("java/lang/Object"));
        }

        @Test
        void extractClassNamesArray() {
            Set<String> names = DescriptorUtil.extractClassNames("[Ljava/lang/String;");
            assertEquals(1, names.size());
            assertTrue(names.contains("java/lang/String"));
        }

        @Test
        void extractClassNamesPrimitive() {
            Set<String> names = DescriptorUtil.extractClassNames("I");
            assertTrue(names.isEmpty());
        }

        @Test
        void extractClassNamesNull() {
            Set<String> names = DescriptorUtil.extractClassNames(null);
            assertTrue(names.isEmpty());
        }

        @Test
        void extractClassNamesEmpty() {
            Set<String> names = DescriptorUtil.extractClassNames("");
            assertTrue(names.isEmpty());
        }

        @Test
        void extractClassNamesMalformed() {
            Set<String> names = DescriptorUtil.extractClassNames("Ljava/lang/String");
            assertTrue(names.isEmpty());
        }
    }

    @Nested
    class ArrayDescriptorTests {

        @Test
        void getArrayElementTypeSingleDimension() {
            assertEquals("I", DescriptorUtil.getArrayElementType("[I"));
        }

        @Test
        void getArrayElementTypeMultiDimension() {
            assertEquals("I", DescriptorUtil.getArrayElementType("[[I"));
        }

        @Test
        void getArrayElementTypeObject() {
            assertEquals("Ljava/lang/String;", DescriptorUtil.getArrayElementType("[Ljava/lang/String;"));
        }

        @Test
        void getArrayElementTypeNotArray() {
            assertNull(DescriptorUtil.getArrayElementType("I"));
        }

        @Test
        void getArrayElementTypeNull() {
            assertNull(DescriptorUtil.getArrayElementType(null));
        }

        @Test
        void getArrayDimensionsSingle() {
            assertEquals(1, DescriptorUtil.getArrayDimensions("[I"));
        }

        @Test
        void getArrayDimensionsDouble() {
            assertEquals(2, DescriptorUtil.getArrayDimensions("[[I"));
        }

        @Test
        void getArrayDimensionsTriple() {
            assertEquals(3, DescriptorUtil.getArrayDimensions("[[[Ljava/lang/String;"));
        }

        @Test
        void getArrayDimensionsNotArray() {
            assertEquals(0, DescriptorUtil.getArrayDimensions("I"));
        }

        @Test
        void getArrayDimensionsNull() {
            assertEquals(0, DescriptorUtil.getArrayDimensions(null));
        }
    }

    @Nested
    class ObjectDescriptorTests {

        @Test
        void extractClassNameValid() {
            assertEquals("java/lang/String", DescriptorUtil.extractClassName("Ljava/lang/String;"));
        }

        @Test
        void extractClassNameInnerClass() {
            assertEquals("com/example/Outer$Inner", DescriptorUtil.extractClassName("Lcom/example/Outer$Inner;"));
        }

        @Test
        void extractClassNameNoPrefix() {
            assertNull(DescriptorUtil.extractClassName("java/lang/String;"));
        }

        @Test
        void extractClassNameNoSuffix() {
            assertNull(DescriptorUtil.extractClassName("Ljava/lang/String"));
        }

        @Test
        void extractClassNameNull() {
            assertNull(DescriptorUtil.extractClassName(null));
        }

        @Test
        void toObjectDescriptorValid() {
            assertEquals("Ljava/lang/String;", DescriptorUtil.toObjectDescriptor("java/lang/String"));
        }

        @Test
        void toObjectDescriptorInnerClass() {
            assertEquals("Lcom/example/Outer$Inner;", DescriptorUtil.toObjectDescriptor("com/example/Outer$Inner"));
        }

        @Test
        void toObjectDescriptorNull() {
            assertNull(DescriptorUtil.toObjectDescriptor(null));
        }
    }
}
