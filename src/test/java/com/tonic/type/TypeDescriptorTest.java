package com.tonic.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class TypeDescriptorTest {

    @Nested
    class ParsePrimitiveTypes {

        @Test
        void parseIntDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("I");
            assertEquals(TypeDescriptor.INT, td.getSort());
            assertTrue(td.isPrimitive());
            assertFalse(td.isArray());
            assertFalse(td.isObject());
            assertEquals("I", td.getDescriptor());
        }

        @Test
        void parseLongDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("J");
            assertEquals(TypeDescriptor.LONG, td.getSort());
            assertTrue(td.isPrimitive());
            assertEquals("J", td.getDescriptor());
        }

        @Test
        void parseDoubleDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("D");
            assertEquals(TypeDescriptor.DOUBLE, td.getSort());
            assertTrue(td.isPrimitive());
        }

        @Test
        void parseFloatDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("F");
            assertEquals(TypeDescriptor.FLOAT, td.getSort());
            assertTrue(td.isPrimitive());
        }

        @Test
        void parseBooleanDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("Z");
            assertEquals(TypeDescriptor.BOOLEAN, td.getSort());
            assertTrue(td.isPrimitive());
        }

        @Test
        void parseByteDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("B");
            assertEquals(TypeDescriptor.BYTE, td.getSort());
            assertTrue(td.isPrimitive());
        }

        @Test
        void parseCharDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("C");
            assertEquals(TypeDescriptor.CHAR, td.getSort());
            assertTrue(td.isPrimitive());
        }

        @Test
        void parseShortDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("S");
            assertEquals(TypeDescriptor.SHORT, td.getSort());
            assertTrue(td.isPrimitive());
        }

        @Test
        void parseVoidDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("V");
            assertEquals(TypeDescriptor.VOID, td.getSort());
        }
    }

    @Nested
    class ParseObjectType {

        @Test
        void parseStringDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("Ljava/lang/String;");
            assertEquals(TypeDescriptor.OBJECT, td.getSort());
            assertTrue(td.isObject());
            assertFalse(td.isPrimitive());
            assertEquals("java/lang/String", td.getInternalName());
            assertEquals("Ljava/lang/String;", td.getDescriptor());
        }

        @Test
        void parseObjectDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("Ljava/lang/Object;");
            assertEquals(TypeDescriptor.OBJECT, td.getSort());
            assertEquals("java/lang/Object", td.getInternalName());
        }
    }

    @Nested
    class ParseArrayType {

        @Test
        void parseIntArrayDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("[I");
            assertEquals(TypeDescriptor.ARRAY, td.getSort());
            assertTrue(td.isArray());
            assertEquals(1, td.getDimensions());
            assertEquals(TypeDescriptor.INT, td.getElementType().getSort());
        }

        @Test
        void parseObjectArrayDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("[Ljava/lang/Object;");
            assertEquals(TypeDescriptor.ARRAY, td.getSort());
            assertEquals(1, td.getDimensions());
            assertEquals(TypeDescriptor.OBJECT, td.getElementType().getSort());
        }

        @Test
        void parseMultiDimensionalArray() {
            TypeDescriptor td = TypeDescriptor.parse("[[Ljava/lang/Object;");
            assertEquals(TypeDescriptor.ARRAY, td.getSort());
            assertEquals(2, td.getDimensions());
        }
    }

    @Nested
    class ParseMethodDescriptor {

        @Test
        void parseSimpleMethodDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("(ILjava/lang/String;)V");
            assertEquals(TypeDescriptor.METHOD, td.getSort());
            assertTrue(td.isMethod());

            TypeDescriptor returnType = td.getReturnType();
            assertEquals(TypeDescriptor.VOID, returnType.getSort());

            TypeDescriptor[] args = td.getArgumentTypes();
            assertEquals(2, args.length);
            assertEquals(TypeDescriptor.INT, args[0].getSort());
            assertEquals(TypeDescriptor.OBJECT, args[1].getSort());
        }

        @Test
        void parseNoArgMethodDescriptor() {
            TypeDescriptor td = TypeDescriptor.parse("()I");
            assertEquals(TypeDescriptor.METHOD, td.getSort());
            assertEquals(TypeDescriptor.INT, td.getReturnType().getSort());
            assertEquals(0, td.getArgumentTypes().length);
        }
    }

    @Nested
    class ForClassMethod {

        @Test
        void forClassCreatesObjectDescriptor() {
            TypeDescriptor td = TypeDescriptor.forClass("java/lang/String");
            assertEquals(TypeDescriptor.OBJECT, td.getSort());
            assertEquals("java/lang/String", td.getInternalName());
            assertEquals("Ljava/lang/String;", td.getDescriptor());
        }
    }

    @Nested
    class ForArrayMethod {

        @Test
        void forArrayCreatesArrayDescriptor() {
            TypeDescriptor td = TypeDescriptor.forArray(TypeDescriptor.INT_TYPE, 1);
            assertEquals(TypeDescriptor.ARRAY, td.getSort());
            assertEquals("[I", td.getDescriptor());
        }

        @Test
        void forArrayCreatesMultiDimensionalDescriptor() {
            TypeDescriptor td = TypeDescriptor.forArray(TypeDescriptor.DOUBLE_TYPE, 3);
            assertEquals(TypeDescriptor.ARRAY, td.getSort());
            assertEquals("[[[D", td.getDescriptor());
        }
    }

    @Nested
    class ForMethodMethod {

        @Test
        void forMethodCreatesMethodDescriptor() {
            TypeDescriptor td = TypeDescriptor.forMethod(
                TypeDescriptor.VOID_TYPE,
                TypeDescriptor.INT_TYPE,
                TypeDescriptor.forClass("java/lang/String")
            );
            assertEquals(TypeDescriptor.METHOD, td.getSort());
            assertEquals("(ILjava/lang/String;)V", td.getDescriptor());
        }
    }

    @Nested
    class GetSize {

        @Test
        void wideTypesReturnTwo() {
            assertEquals(2, TypeDescriptor.LONG_TYPE.getSize());
            assertEquals(2, TypeDescriptor.DOUBLE_TYPE.getSize());
        }

        @Test
        void narrowTypesReturnOne() {
            assertEquals(1, TypeDescriptor.INT_TYPE.getSize());
            assertEquals(1, TypeDescriptor.FLOAT_TYPE.getSize());
            assertEquals(1, TypeDescriptor.forClass("java/lang/Object").getSize());
        }

        @Test
        void voidReturnsZero() {
            assertEquals(0, TypeDescriptor.VOID_TYPE.getSize());
        }
    }

    @Nested
    class GetOpcodes {

        @Test
        void getLoadOpcode() {
            assertEquals(AccessFlags.ILOAD, TypeDescriptor.INT_TYPE.getLoadOpcode());
            assertEquals(AccessFlags.LLOAD, TypeDescriptor.LONG_TYPE.getLoadOpcode());
            assertEquals(AccessFlags.FLOAD, TypeDescriptor.FLOAT_TYPE.getLoadOpcode());
            assertEquals(AccessFlags.DLOAD, TypeDescriptor.DOUBLE_TYPE.getLoadOpcode());
            assertEquals(AccessFlags.ALOAD, TypeDescriptor.forClass("java/lang/String").getLoadOpcode());
        }

        @Test
        void getStoreOpcode() {
            assertEquals(AccessFlags.ISTORE, TypeDescriptor.INT_TYPE.getStoreOpcode());
            assertEquals(AccessFlags.LSTORE, TypeDescriptor.LONG_TYPE.getStoreOpcode());
            assertEquals(AccessFlags.FSTORE, TypeDescriptor.FLOAT_TYPE.getStoreOpcode());
            assertEquals(AccessFlags.DSTORE, TypeDescriptor.DOUBLE_TYPE.getStoreOpcode());
            assertEquals(AccessFlags.ASTORE, TypeDescriptor.forClass("java/lang/String").getStoreOpcode());
        }

        @Test
        void getReturnOpcode() {
            assertEquals(AccessFlags.IRETURN, TypeDescriptor.INT_TYPE.getReturnOpcode());
            assertEquals(AccessFlags.LRETURN, TypeDescriptor.LONG_TYPE.getReturnOpcode());
            assertEquals(AccessFlags.FRETURN, TypeDescriptor.FLOAT_TYPE.getReturnOpcode());
            assertEquals(AccessFlags.DRETURN, TypeDescriptor.DOUBLE_TYPE.getReturnOpcode());
            assertEquals(AccessFlags.ARETURN, TypeDescriptor.forClass("java/lang/String").getReturnOpcode());
            assertEquals(AccessFlags.RETURN, TypeDescriptor.VOID_TYPE.getReturnOpcode());
        }
    }

    @Nested
    class GetArgumentsSize {

        @Test
        void argumentsSizeCountsSlots() {
            TypeDescriptor td = TypeDescriptor.parse("(IJD)V");
            assertEquals(5, td.getArgumentsSize());
        }

        @Test
        void noArgsReturnsZero() {
            TypeDescriptor td = TypeDescriptor.parse("()V");
            assertEquals(0, td.getArgumentsSize());
        }
    }

    @Nested
    class PrimitiveTypeInstances {

        @Test
        void primitiveTypesAreCached() {
            assertSame(TypeDescriptor.INT_TYPE, TypeDescriptor.parse("I"));
            assertSame(TypeDescriptor.LONG_TYPE, TypeDescriptor.parse("J"));
            assertSame(TypeDescriptor.DOUBLE_TYPE, TypeDescriptor.parse("D"));
            assertSame(TypeDescriptor.FLOAT_TYPE, TypeDescriptor.parse("F"));
            assertSame(TypeDescriptor.BOOLEAN_TYPE, TypeDescriptor.parse("Z"));
            assertSame(TypeDescriptor.BYTE_TYPE, TypeDescriptor.parse("B"));
            assertSame(TypeDescriptor.CHAR_TYPE, TypeDescriptor.parse("C"));
            assertSame(TypeDescriptor.SHORT_TYPE, TypeDescriptor.parse("S"));
            assertSame(TypeDescriptor.VOID_TYPE, TypeDescriptor.parse("V"));
        }
    }

    @Nested
    class ParseErrorCases {

        @Test
        void parseNullThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                TypeDescriptor.parse(null);
            });
        }

        @Test
        void parseEmptyStringThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                TypeDescriptor.parse("");
            });
        }

        @Test
        void parseInvalidDescriptorThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                TypeDescriptor.parse("X");
            });
        }

        @Test
        void parseInvalidCharacterThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                TypeDescriptor.parse("@");
            });
        }
    }

    @Nested
    class GetOpcodeErrorCases {

        @Test
        void getLoadOpcodeForMethodThrowsException() {
            TypeDescriptor td = TypeDescriptor.parse("()V");
            assertThrows(IllegalStateException.class, () -> {
                td.getLoadOpcode();
            });
        }

        @Test
        void getLoadOpcodeForVoidThrowsException() {
            assertThrows(IllegalStateException.class, () -> {
                TypeDescriptor.VOID_TYPE.getLoadOpcode();
            });
        }

        @Test
        void getStoreOpcodeForMethodThrowsException() {
            TypeDescriptor td = TypeDescriptor.parse("()V");
            assertThrows(IllegalStateException.class, () -> {
                td.getStoreOpcode();
            });
        }

        @Test
        void getStoreOpcodeForVoidThrowsException() {
            assertThrows(IllegalStateException.class, () -> {
                TypeDescriptor.VOID_TYPE.getStoreOpcode();
            });
        }

        @Test
        void getReturnOpcodeForMethodThrowsException() {
            TypeDescriptor td = TypeDescriptor.parse("()V");
            assertThrows(IllegalStateException.class, () -> {
                td.getReturnOpcode();
            });
        }
    }

    @Nested
    class GetInternalNameTests {

        @Test
        void getInternalNameForArrayOfPrimitives() {
            TypeDescriptor td = TypeDescriptor.parse("[I");
            assertNull(td.getInternalName());
        }

        @Test
        void getInternalNameForArrayOfObjects() {
            TypeDescriptor td = TypeDescriptor.parse("[Ljava/lang/String;");
            assertEquals("java/lang/String", td.getInternalName());
        }

        @Test
        void getInternalNameForPrimitiveReturnsNull() {
            assertNull(TypeDescriptor.INT_TYPE.getInternalName());
        }
    }

    @Nested
    class GetElementTypeTests {

        @Test
        void getElementTypeForMultiDimensionalArray() {
            TypeDescriptor td = TypeDescriptor.parse("[[[I");
            TypeDescriptor elem = td.getElementType();
            assertNotNull(elem);
            assertEquals(TypeDescriptor.INT, elem.getSort());
            assertEquals("I", elem.getDescriptor());
        }

        @Test
        void getElementTypeForNonArrayReturnsNull() {
            assertNull(TypeDescriptor.INT_TYPE.getElementType());
        }
    }

    @Nested
    class GetSizeTests {

        @Test
        void getSizeForAllPrimitiveTypes() {
            assertEquals(1, TypeDescriptor.INT_TYPE.getSize());
            assertEquals(1, TypeDescriptor.FLOAT_TYPE.getSize());
            assertEquals(1, TypeDescriptor.BYTE_TYPE.getSize());
            assertEquals(1, TypeDescriptor.CHAR_TYPE.getSize());
            assertEquals(1, TypeDescriptor.SHORT_TYPE.getSize());
            assertEquals(1, TypeDescriptor.BOOLEAN_TYPE.getSize());
            assertEquals(2, TypeDescriptor.LONG_TYPE.getSize());
            assertEquals(2, TypeDescriptor.DOUBLE_TYPE.getSize());
            assertEquals(0, TypeDescriptor.VOID_TYPE.getSize());
        }

        @Test
        void getSizeForArrayIsOne() {
            TypeDescriptor td = TypeDescriptor.parse("[I");
            assertEquals(1, td.getSize());
        }

        @Test
        void getSizeForObjectIsOne() {
            TypeDescriptor td = TypeDescriptor.forClass("java/lang/Object");
            assertEquals(1, td.getSize());
        }

        @Test
        void getSizeForMethodIsOne() {
            TypeDescriptor td = TypeDescriptor.parse("()V");
            assertEquals(1, td.getSize());
        }
    }

    @Nested
    class AdditionalOpcodeTests {

        @Test
        void getAllLoadOpcodes() {
            assertEquals(AccessFlags.ILOAD, TypeDescriptor.BOOLEAN_TYPE.getLoadOpcode());
            assertEquals(AccessFlags.ILOAD, TypeDescriptor.BYTE_TYPE.getLoadOpcode());
            assertEquals(AccessFlags.ILOAD, TypeDescriptor.CHAR_TYPE.getLoadOpcode());
            assertEquals(AccessFlags.ILOAD, TypeDescriptor.SHORT_TYPE.getLoadOpcode());
            assertEquals(AccessFlags.ALOAD, TypeDescriptor.parse("[I").getLoadOpcode());
        }

        @Test
        void getAllStoreOpcodes() {
            assertEquals(AccessFlags.ISTORE, TypeDescriptor.BOOLEAN_TYPE.getStoreOpcode());
            assertEquals(AccessFlags.ISTORE, TypeDescriptor.BYTE_TYPE.getStoreOpcode());
            assertEquals(AccessFlags.ISTORE, TypeDescriptor.CHAR_TYPE.getStoreOpcode());
            assertEquals(AccessFlags.ISTORE, TypeDescriptor.SHORT_TYPE.getStoreOpcode());
            assertEquals(AccessFlags.ASTORE, TypeDescriptor.parse("[I").getStoreOpcode());
        }

        @Test
        void getAllReturnOpcodes() {
            assertEquals(AccessFlags.IRETURN, TypeDescriptor.BOOLEAN_TYPE.getReturnOpcode());
            assertEquals(AccessFlags.IRETURN, TypeDescriptor.BYTE_TYPE.getReturnOpcode());
            assertEquals(AccessFlags.IRETURN, TypeDescriptor.CHAR_TYPE.getReturnOpcode());
            assertEquals(AccessFlags.IRETURN, TypeDescriptor.SHORT_TYPE.getReturnOpcode());
            assertEquals(AccessFlags.ARETURN, TypeDescriptor.parse("[I").getReturnOpcode());
        }
    }

    @Nested
    class EqualsAndHashCodeTests {

        @Test
        void equalDescriptorsAreEqual() {
            TypeDescriptor td1 = TypeDescriptor.parse("Ljava/lang/String;");
            TypeDescriptor td2 = TypeDescriptor.parse("Ljava/lang/String;");
            assertEquals(td1, td2);
            assertEquals(td1.hashCode(), td2.hashCode());
        }

        @Test
        void differentDescriptorsAreNotEqual() {
            TypeDescriptor td1 = TypeDescriptor.parse("I");
            TypeDescriptor td2 = TypeDescriptor.parse("J");
            assertNotEquals(td1, td2);
        }

        @Test
        void sameInstanceIsEqual() {
            TypeDescriptor td = TypeDescriptor.INT_TYPE;
            assertEquals(td, td);
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringReturnsDescriptor() {
            assertEquals("I", TypeDescriptor.INT_TYPE.toString());
            assertEquals("Ljava/lang/String;", TypeDescriptor.forClass("java/lang/String").toString());
            assertEquals("[I", TypeDescriptor.parse("[I").toString());
            assertEquals("()V", TypeDescriptor.parse("()V").toString());
        }
    }

    @Nested
    class GetClassNameTests {

        @Test
        void getClassNameConvertsSlashesToDots() {
            TypeDescriptor td = TypeDescriptor.forClass("java/lang/String");
            assertEquals("java.lang.String", td.getClassName());
        }

        @Test
        void getClassNameForPrimitiveReturnsNull() {
            assertNull(TypeDescriptor.INT_TYPE.getClassName());
        }
    }
}
