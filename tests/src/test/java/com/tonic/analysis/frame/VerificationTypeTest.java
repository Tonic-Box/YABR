package com.tonic.analysis.frame;

import com.tonic.parser.attribute.stack.VerificationTypeInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VerificationTypeTest {

    @Nested
    class PrimitiveTypeTests {

        @Test
        void primitiveConstantsHaveCorrectTags() {
            assertEquals(VerificationType.TAG_TOP, VerificationType.TOP.getTag());
            assertEquals(VerificationType.TAG_INTEGER, VerificationType.INTEGER.getTag());
            assertEquals(VerificationType.TAG_FLOAT, VerificationType.FLOAT.getTag());
            assertEquals(VerificationType.TAG_DOUBLE, VerificationType.DOUBLE.getTag());
            assertEquals(VerificationType.TAG_LONG, VerificationType.LONG.getTag());
            assertEquals(VerificationType.TAG_NULL, VerificationType.NULL.getTag());
            assertEquals(VerificationType.TAG_UNINITIALIZED_THIS, VerificationType.UNINITIALIZED_THIS.getTag());
        }

        @Test
        void primitiveConstantsHaveCorrectNames() {
            VerificationType.PrimitiveType top = (VerificationType.PrimitiveType) VerificationType.TOP;
            VerificationType.PrimitiveType integer = (VerificationType.PrimitiveType) VerificationType.INTEGER;
            VerificationType.PrimitiveType floatType = (VerificationType.PrimitiveType) VerificationType.FLOAT;
            VerificationType.PrimitiveType doubleType = (VerificationType.PrimitiveType) VerificationType.DOUBLE;
            VerificationType.PrimitiveType longType = (VerificationType.PrimitiveType) VerificationType.LONG;
            VerificationType.PrimitiveType nullType = (VerificationType.PrimitiveType) VerificationType.NULL;
            VerificationType.PrimitiveType uninitThis = (VerificationType.PrimitiveType) VerificationType.UNINITIALIZED_THIS;

            assertEquals("top", top.getName());
            assertEquals("int", integer.getName());
            assertEquals("float", floatType.getName());
            assertEquals("double", doubleType.getName());
            assertEquals("long", longType.getName());
            assertEquals("null", nullType.getName());
            assertEquals("uninitializedThis", uninitThis.getName());
        }

        @Test
        void primitiveToStringUsesName() {
            assertEquals("top", VerificationType.TOP.toString());
            assertEquals("int", VerificationType.INTEGER.toString());
            assertEquals("float", VerificationType.FLOAT.toString());
            assertEquals("double", VerificationType.DOUBLE.toString());
            assertEquals("long", VerificationType.LONG.toString());
            assertEquals("null", VerificationType.NULL.toString());
            assertEquals("uninitializedThis", VerificationType.UNINITIALIZED_THIS.toString());
        }

        @Test
        void primitiveEqualsComparesTag() {
            VerificationType.PrimitiveType int1 = (VerificationType.PrimitiveType) VerificationType.INTEGER;
            VerificationType.PrimitiveType int2 = (VerificationType.PrimitiveType) VerificationType.INTEGER;

            assertEquals(int1, int2);
            assertEquals(int1, int1);
        }

        @Test
        void primitiveNotEqualsWhenDifferentTag() {
            assertNotEquals(VerificationType.INTEGER, VerificationType.FLOAT);
            assertNotEquals(VerificationType.LONG, VerificationType.DOUBLE);
            assertFalse(VerificationType.INTEGER.equals(VerificationType.FLOAT));
            assertFalse(VerificationType.LONG.equals(VerificationType.DOUBLE));
        }

        @Test
        void primitiveNotEqualsWhenDifferentType() {
            VerificationType.PrimitiveType primitiveType = (VerificationType.PrimitiveType) VerificationType.INTEGER;
            VerificationType.ObjectType objectType = new VerificationType.ObjectType(1);

            assertNotEquals(primitiveType, objectType);
        }

        @Test
        void primitiveHashCodeBasedOnTag() {
            VerificationType.PrimitiveType int1 = (VerificationType.PrimitiveType) VerificationType.INTEGER;
            VerificationType.PrimitiveType int2 = (VerificationType.PrimitiveType) VerificationType.INTEGER;

            assertEquals(int1.hashCode(), int2.hashCode());
            assertEquals(VerificationType.TAG_INTEGER, int1.hashCode());
        }

        @Test
        void primitiveHashCodeDiffersForDifferentTags() {
            VerificationType.PrimitiveType intType = (VerificationType.PrimitiveType) VerificationType.INTEGER;
            VerificationType.PrimitiveType floatType = (VerificationType.PrimitiveType) VerificationType.FLOAT;

            assertNotEquals(intType.hashCode(), floatType.hashCode());
        }

        @Test
        void primitiveToVerificationTypeInfo() {
            VerificationTypeInfo info = VerificationType.INTEGER.toVerificationTypeInfo();

            assertNotNull(info);
            assertEquals(VerificationType.TAG_INTEGER, info.getTag());
            assertNull(info.getInfo());
        }
    }

    @Nested
    class ObjectTypeTests {

        @Test
        void objectTypeConstructorWithClassIndex() {
            VerificationType.ObjectType obj = new VerificationType.ObjectType(42);

            assertEquals(VerificationType.TAG_OBJECT, obj.getTag());
            assertEquals(42, obj.getClassIndex());
            assertNull(obj.getClassName());
        }

        @Test
        void objectTypeConstructorWithClassNameAndIndex() {
            VerificationType.ObjectType obj = new VerificationType.ObjectType(42, "java/lang/String");

            assertEquals(VerificationType.TAG_OBJECT, obj.getTag());
            assertEquals(42, obj.getClassIndex());
            assertEquals("java/lang/String", obj.getClassName());
        }

        @Test
        void objectTypeStaticFactoryMethodWithIndex() {
            VerificationType obj = VerificationType.object(100);

            assertEquals(VerificationType.TAG_OBJECT, obj.getTag());
            assertTrue(obj instanceof VerificationType.ObjectType);
            assertEquals(100, ((VerificationType.ObjectType) obj).getClassIndex());
            assertNull(((VerificationType.ObjectType) obj).getClassName());
        }

        @Test
        void objectTypeStaticFactoryMethodWithClassNameAndIndex() {
            VerificationType obj = VerificationType.object("java/util/List", 50);

            assertEquals(VerificationType.TAG_OBJECT, obj.getTag());
            assertTrue(obj instanceof VerificationType.ObjectType);
            assertEquals(50, ((VerificationType.ObjectType) obj).getClassIndex());
            assertEquals("java/util/List", ((VerificationType.ObjectType) obj).getClassName());
        }

        @Test
        void objectTypeToStringWithoutClassName() {
            VerificationType.ObjectType obj = new VerificationType.ObjectType(42);

            assertEquals("Object(#42)", obj.toString());
        }

        @Test
        void objectTypeToStringWithClassName() {
            VerificationType.ObjectType obj = new VerificationType.ObjectType(42, "java/lang/String");

            assertEquals("Object(java/lang/String)", obj.toString());
        }

        @Test
        void objectTypeEqualsComparesClassIndex() {
            VerificationType.ObjectType obj1 = new VerificationType.ObjectType(42, "java/lang/String");
            VerificationType.ObjectType obj2 = new VerificationType.ObjectType(42, "java/lang/Integer");

            assertEquals(obj1, obj2);
        }

        @Test
        void objectTypeEqualsSameInstance() {
            VerificationType.ObjectType obj = new VerificationType.ObjectType(42);

            assertEquals(obj, obj);
        }

        @Test
        void objectTypeNotEqualsWhenDifferentClassIndex() {
            VerificationType.ObjectType obj1 = new VerificationType.ObjectType(42);
            VerificationType.ObjectType obj2 = new VerificationType.ObjectType(43);

            assertNotEquals(obj1, obj2);
        }

        @Test
        void objectTypeNotEqualsWhenDifferentType() {
            VerificationType.ObjectType obj = new VerificationType.ObjectType(42);
            VerificationType.PrimitiveType prim = (VerificationType.PrimitiveType) VerificationType.INTEGER;

            assertNotEquals(obj, prim);
        }

        @Test
        void objectTypeHashCodeBasedOnTagAndClassIndex() {
            VerificationType.ObjectType obj1 = new VerificationType.ObjectType(42, "java/lang/String");
            VerificationType.ObjectType obj2 = new VerificationType.ObjectType(42, "java/lang/Integer");

            assertEquals(obj1.hashCode(), obj2.hashCode());
        }

        @Test
        void objectTypeHashCodeDiffersForDifferentClassIndex() {
            VerificationType.ObjectType obj1 = new VerificationType.ObjectType(42);
            VerificationType.ObjectType obj2 = new VerificationType.ObjectType(43);

            assertNotEquals(obj1.hashCode(), obj2.hashCode());
        }

        @Test
        void objectTypeToVerificationTypeInfo() {
            VerificationType.ObjectType obj = new VerificationType.ObjectType(42);
            VerificationTypeInfo info = obj.toVerificationTypeInfo();

            assertNotNull(info);
            assertEquals(VerificationType.TAG_OBJECT, info.getTag());
            assertEquals(42, (int) info.getInfo());
        }
    }

    @Nested
    class UninitializedTypeTests {

        @Test
        void uninitializedTypeConstructor() {
            VerificationType.UninitializedType uninit = new VerificationType.UninitializedType(100);

            assertEquals(VerificationType.TAG_UNINITIALIZED, uninit.getTag());
            assertEquals(100, uninit.getNewInstructionOffset());
        }

        @Test
        void uninitializedTypeConstructorWithZeroOffset() {
            VerificationType.UninitializedType uninit = new VerificationType.UninitializedType(0);

            assertEquals(0, uninit.getNewInstructionOffset());
        }

        @Test
        void uninitializedTypeConstructorWithNegativeOffset() {
            VerificationType.UninitializedType uninit = new VerificationType.UninitializedType(-1);

            assertEquals(-1, uninit.getNewInstructionOffset());
        }

        @Test
        void uninitializedTypeStaticFactoryMethod() {
            VerificationType uninit = VerificationType.uninitialized(200);

            assertEquals(VerificationType.TAG_UNINITIALIZED, uninit.getTag());
            assertTrue(uninit instanceof VerificationType.UninitializedType);
            assertEquals(200, ((VerificationType.UninitializedType) uninit).getNewInstructionOffset());
        }

        @Test
        void uninitializedTypeToString() {
            VerificationType.UninitializedType uninit = new VerificationType.UninitializedType(100);

            assertEquals("Uninitialized(@100)", uninit.toString());
        }

        @Test
        void uninitializedTypeToStringWithZeroOffset() {
            VerificationType.UninitializedType uninit = new VerificationType.UninitializedType(0);

            assertEquals("Uninitialized(@0)", uninit.toString());
        }

        @Test
        void uninitializedTypeEqualsSameOffset() {
            VerificationType.UninitializedType uninit1 = new VerificationType.UninitializedType(100);
            VerificationType.UninitializedType uninit2 = new VerificationType.UninitializedType(100);

            assertEquals(uninit1, uninit2);
        }

        @Test
        void uninitializedTypeEqualsSameInstance() {
            VerificationType.UninitializedType uninit = new VerificationType.UninitializedType(100);

            assertEquals(uninit, uninit);
        }

        @Test
        void uninitializedTypeNotEqualsWhenDifferentOffset() {
            VerificationType.UninitializedType uninit1 = new VerificationType.UninitializedType(100);
            VerificationType.UninitializedType uninit2 = new VerificationType.UninitializedType(200);

            assertNotEquals(uninit1, uninit2);
        }

        @Test
        void uninitializedTypeNotEqualsWhenDifferentType() {
            VerificationType.UninitializedType uninit = new VerificationType.UninitializedType(100);
            VerificationType.ObjectType obj = new VerificationType.ObjectType(42);

            assertNotEquals(uninit, obj);
        }

        @Test
        void uninitializedTypeHashCodeSameForSameOffset() {
            VerificationType.UninitializedType uninit1 = new VerificationType.UninitializedType(100);
            VerificationType.UninitializedType uninit2 = new VerificationType.UninitializedType(100);

            assertEquals(uninit1.hashCode(), uninit2.hashCode());
        }

        @Test
        void uninitializedTypeHashCodeDiffersForDifferentOffset() {
            VerificationType.UninitializedType uninit1 = new VerificationType.UninitializedType(100);
            VerificationType.UninitializedType uninit2 = new VerificationType.UninitializedType(200);

            assertNotEquals(uninit1.hashCode(), uninit2.hashCode());
        }

        @Test
        void uninitializedTypeToVerificationTypeInfo() {
            VerificationType.UninitializedType uninit = new VerificationType.UninitializedType(100);
            VerificationTypeInfo info = uninit.toVerificationTypeInfo();

            assertNotNull(info);
            assertEquals(VerificationType.TAG_UNINITIALIZED, info.getTag());
            assertEquals(100, (int) info.getInfo());
        }
    }

    @Nested
    class TwoSlotTests {

        @Test
        void longIsTwoSlot() {
            assertTrue(VerificationType.LONG.isTwoSlot());
        }

        @Test
        void doubleIsTwoSlot() {
            assertTrue(VerificationType.DOUBLE.isTwoSlot());
        }

        @Test
        void integerIsNotTwoSlot() {
            assertFalse(VerificationType.INTEGER.isTwoSlot());
        }

        @Test
        void floatIsNotTwoSlot() {
            assertFalse(VerificationType.FLOAT.isTwoSlot());
        }

        @Test
        void topIsNotTwoSlot() {
            assertFalse(VerificationType.TOP.isTwoSlot());
        }

        @Test
        void nullIsNotTwoSlot() {
            assertFalse(VerificationType.NULL.isTwoSlot());
        }

        @Test
        void objectIsNotTwoSlot() {
            VerificationType obj = VerificationType.object(42);
            assertFalse(obj.isTwoSlot());
        }

        @Test
        void uninitializedIsNotTwoSlot() {
            VerificationType uninit = VerificationType.uninitialized(100);
            assertFalse(uninit.isTwoSlot());
        }
    }

    @Nested
    class FromDescriptorCharTests {

        @Test
        void fromDescriptorByteReturnsInteger() {
            assertEquals(VerificationType.INTEGER, VerificationType.fromDescriptor('B'));
        }

        @Test
        void fromDescriptorCharReturnsInteger() {
            assertEquals(VerificationType.INTEGER, VerificationType.fromDescriptor('C'));
        }

        @Test
        void fromDescriptorIntReturnsInteger() {
            assertEquals(VerificationType.INTEGER, VerificationType.fromDescriptor('I'));
        }

        @Test
        void fromDescriptorShortReturnsInteger() {
            assertEquals(VerificationType.INTEGER, VerificationType.fromDescriptor('S'));
        }

        @Test
        void fromDescriptorBooleanReturnsInteger() {
            assertEquals(VerificationType.INTEGER, VerificationType.fromDescriptor('Z'));
        }

        @Test
        void fromDescriptorFloatReturnsFloat() {
            assertEquals(VerificationType.FLOAT, VerificationType.fromDescriptor('F'));
        }

        @Test
        void fromDescriptorDoubleReturnsDouble() {
            assertEquals(VerificationType.DOUBLE, VerificationType.fromDescriptor('D'));
        }

        @Test
        void fromDescriptorLongReturnsLong() {
            assertEquals(VerificationType.LONG, VerificationType.fromDescriptor('J'));
        }

        @Test
        void fromDescriptorInvalidCharThrows() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VerificationType.fromDescriptor('X')
            );
            assertTrue(ex.getMessage().contains("Unknown primitive descriptor"));
        }

        @Test
        void fromDescriptorObjectClassThrows() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VerificationType.fromDescriptor('L')
            );
            assertTrue(ex.getMessage().contains("Unknown primitive descriptor"));
        }

        @Test
        void fromDescriptorArrayThrows() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VerificationType.fromDescriptor('[')
            );
            assertTrue(ex.getMessage().contains("Unknown primitive descriptor"));
        }
    }

    @Nested
    class FromDescriptorStringTests {

        @Test
        void fromDescriptorStringEmptyThrows() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VerificationType.fromDescriptor("", 0)
            );
            assertTrue(ex.getMessage().contains("Empty descriptor"));
        }

        @Test
        void fromDescriptorStringByteReturnsInteger() {
            assertEquals(VerificationType.INTEGER, VerificationType.fromDescriptor("B", 0));
        }

        @Test
        void fromDescriptorStringCharReturnsInteger() {
            assertEquals(VerificationType.INTEGER, VerificationType.fromDescriptor("C", 0));
        }

        @Test
        void fromDescriptorStringIntReturnsInteger() {
            assertEquals(VerificationType.INTEGER, VerificationType.fromDescriptor("I", 0));
        }

        @Test
        void fromDescriptorStringShortReturnsInteger() {
            assertEquals(VerificationType.INTEGER, VerificationType.fromDescriptor("S", 0));
        }

        @Test
        void fromDescriptorStringBooleanReturnsInteger() {
            assertEquals(VerificationType.INTEGER, VerificationType.fromDescriptor("Z", 0));
        }

        @Test
        void fromDescriptorStringFloatReturnsFloat() {
            assertEquals(VerificationType.FLOAT, VerificationType.fromDescriptor("F", 0));
        }

        @Test
        void fromDescriptorStringDoubleReturnsDouble() {
            assertEquals(VerificationType.DOUBLE, VerificationType.fromDescriptor("D", 0));
        }

        @Test
        void fromDescriptorStringLongReturnsLong() {
            assertEquals(VerificationType.LONG, VerificationType.fromDescriptor("J", 0));
        }

        @Test
        void fromDescriptorStringClassReturnsObject() {
            VerificationType type = VerificationType.fromDescriptor("Ljava/lang/String;", 42);

            assertEquals(VerificationType.TAG_OBJECT, type.getTag());
            VerificationType.ObjectType objType = (VerificationType.ObjectType) type;
            assertEquals(42, objType.getClassIndex());
        }

        @Test
        void fromDescriptorStringArrayReturnsObject() {
            VerificationType type = VerificationType.fromDescriptor("[I", 100);

            assertEquals(VerificationType.TAG_OBJECT, type.getTag());
            VerificationType.ObjectType objType = (VerificationType.ObjectType) type;
            assertEquals(100, objType.getClassIndex());
        }

        @Test
        void fromDescriptorStringObjectArrayReturnsObject() {
            VerificationType type = VerificationType.fromDescriptor("[Ljava/lang/String;", 200);

            assertEquals(VerificationType.TAG_OBJECT, type.getTag());
            VerificationType.ObjectType objType = (VerificationType.ObjectType) type;
            assertEquals(200, objType.getClassIndex());
        }

        @Test
        void fromDescriptorStringInvalidThrows() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VerificationType.fromDescriptor("X", 0)
            );
            assertTrue(ex.getMessage().contains("Unknown descriptor"));
        }

        @Test
        void fromDescriptorStringVoidThrows() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VerificationType.fromDescriptor("V", 0)
            );
            assertTrue(ex.getMessage().contains("Unknown descriptor"));
        }
    }

    @Nested
    class TagConstantsTests {

        @Test
        void tagConstantsHaveCorrectValues() {
            assertEquals(0, VerificationType.TAG_TOP);
            assertEquals(1, VerificationType.TAG_INTEGER);
            assertEquals(2, VerificationType.TAG_FLOAT);
            assertEquals(3, VerificationType.TAG_DOUBLE);
            assertEquals(4, VerificationType.TAG_LONG);
            assertEquals(5, VerificationType.TAG_NULL);
            assertEquals(6, VerificationType.TAG_UNINITIALIZED_THIS);
            assertEquals(7, VerificationType.TAG_OBJECT);
            assertEquals(8, VerificationType.TAG_UNINITIALIZED);
        }
    }
}
