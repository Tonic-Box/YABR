package com.tonic.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class MethodHandleTest {

    @Nested
    class ConstructorTests {

        @Test
        void constructorSetsAllFields() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "abs",
                "(I)I"
            );

            assertEquals(MethodHandle.H_INVOKESTATIC, mh.getTag());
            assertEquals("java/lang/Math", mh.getOwner());
            assertEquals("abs", mh.getName());
            assertEquals("(I)I", mh.getDescriptor());
            assertFalse(mh.isInterface());
        }

        @Test
        void constructorWithIsInterface() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_INVOKEINTERFACE,
                "java/util/List",
                "size",
                "()I",
                true
            );

            assertEquals(MethodHandle.H_INVOKEINTERFACE, mh.getTag());
            assertEquals("java/util/List", mh.getOwner());
            assertEquals("size", mh.getName());
            assertEquals("()I", mh.getDescriptor());
            assertTrue(mh.isInterface());
        }
    }

    @Nested
    class HandleKindConstants {

        @Test
        void fieldHandleKinds() {
            assertEquals(1, MethodHandle.H_GETFIELD);
            assertEquals(2, MethodHandle.H_GETSTATIC);
            assertEquals(3, MethodHandle.H_PUTFIELD);
            assertEquals(4, MethodHandle.H_PUTSTATIC);
        }

        @Test
        void methodHandleKinds() {
            assertEquals(5, MethodHandle.H_INVOKEVIRTUAL);
            assertEquals(6, MethodHandle.H_INVOKESTATIC);
            assertEquals(7, MethodHandle.H_INVOKESPECIAL);
            assertEquals(8, MethodHandle.H_NEWINVOKESPECIAL);
            assertEquals(9, MethodHandle.H_INVOKEINTERFACE);
        }
    }

    @Nested
    class GettersTests {

        @Test
        void gettersReturnCorrectValues() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_INVOKEVIRTUAL,
                "com/example/MyClass",
                "doSomething",
                "(Ljava/lang/String;)V"
            );

            assertEquals(MethodHandle.H_INVOKEVIRTUAL, mh.getTag());
            assertEquals("com/example/MyClass", mh.getOwner());
            assertEquals("doSomething", mh.getName());
            assertEquals("(Ljava/lang/String;)V", mh.getDescriptor());
        }
    }

    @Nested
    class IsInterfaceTests {

        @Test
        void isInterfaceDefaultsFalse() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_INVOKEVIRTUAL,
                "java/lang/Object",
                "toString",
                "()Ljava/lang/String;"
            );

            assertFalse(mh.isInterface());
        }

        @Test
        void isInterfaceCanBeTrue() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_INVOKEINTERFACE,
                "java/util/Iterator",
                "hasNext",
                "()Z",
                true
            );

            assertTrue(mh.isInterface());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringContainsRelevantInfo() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Integer",
                "parseInt",
                "(Ljava/lang/String;)I"
            );

            String str = mh.toString();
            assertTrue(str.contains("tag=6"));
            assertTrue(str.contains("java/lang/Integer"));
            assertTrue(str.contains("parseInt"));
            assertTrue(str.contains("(Ljava/lang/String;)I"));
        }
    }

    @Nested
    class EqualityTests {

        @Test
        void equalHandlesAreEqual() {
            MethodHandle mh1 = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "max",
                "(II)I"
            );
            MethodHandle mh2 = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "max",
                "(II)I"
            );

            assertEquals(mh1, mh2);
            assertEquals(mh1.hashCode(), mh2.hashCode());
        }

        @Test
        void differentHandlesAreNotEqual() {
            MethodHandle mh1 = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "max",
                "(II)I"
            );
            MethodHandle mh2 = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "min",
                "(II)I"
            );

            assertNotEquals(mh1, mh2);
        }

        @Test
        void interfaceFlagAffectsEquality() {
            MethodHandle mh1 = new MethodHandle(
                MethodHandle.H_INVOKEINTERFACE,
                "java/util/List",
                "size",
                "()I",
                false
            );
            MethodHandle mh2 = new MethodHandle(
                MethodHandle.H_INVOKEINTERFACE,
                "java/util/List",
                "size",
                "()I",
                true
            );

            assertNotEquals(mh1, mh2);
        }

        @Test
        void equalsWithNullReturnsFalse() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "max",
                "(II)I"
            );

            assertNotEquals(null, mh);
        }

        @Test
        void equalsWithDifferentClassReturnsFalse() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "max",
                "(II)I"
            );

            assertNotEquals(mh, "NotAMethodHandle");
        }

        @Test
        void equalsWithDifferentTagReturnsFalse() {
            MethodHandle mh1 = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "max",
                "(II)I"
            );
            MethodHandle mh2 = new MethodHandle(
                MethodHandle.H_INVOKEVIRTUAL,
                "java/lang/Math",
                "max",
                "(II)I"
            );

            assertNotEquals(mh1, mh2);
        }

        @Test
        void equalsWithDifferentOwnerReturnsFalse() {
            MethodHandle mh1 = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "max",
                "(II)I"
            );
            MethodHandle mh2 = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Integer",
                "max",
                "(II)I"
            );

            assertNotEquals(mh1, mh2);
        }

        @Test
        void equalsWithSameInstanceReturnsTrue() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "max",
                "(II)I"
            );

            assertEquals(mh, mh);
        }
    }

    @Nested
    class HashCodeTests {

        @Test
        void hashCodeConsistentForEqualObjects() {
            MethodHandle mh1 = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "abs",
                "(I)I"
            );
            MethodHandle mh2 = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "abs",
                "(I)I"
            );

            assertEquals(mh1.hashCode(), mh2.hashCode());
        }

        @Test
        void hashCodeDifferentForDifferentObjects() {
            MethodHandle mh1 = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "abs",
                "(I)I"
            );
            MethodHandle mh2 = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/Math",
                "max",
                "(II)I"
            );

            assertNotEquals(mh1.hashCode(), mh2.hashCode());
        }
    }

    @Nested
    class FieldHandleTests {

        @Test
        void getFieldHandle() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_GETFIELD,
                "com/example/MyClass",
                "value",
                "I"
            );

            assertEquals(MethodHandle.H_GETFIELD, mh.getTag());
            assertEquals("com/example/MyClass", mh.getOwner());
            assertEquals("value", mh.getName());
            assertEquals("I", mh.getDescriptor());
        }

        @Test
        void putFieldHandle() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_PUTFIELD,
                "com/example/MyClass",
                "value",
                "I"
            );

            assertEquals(MethodHandle.H_PUTFIELD, mh.getTag());
        }

        @Test
        void getStaticFieldHandle() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_GETSTATIC,
                "java/lang/System",
                "out",
                "Ljava/io/PrintStream;"
            );

            assertEquals(MethodHandle.H_GETSTATIC, mh.getTag());
        }

        @Test
        void putStaticFieldHandle() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_PUTSTATIC,
                "com/example/MyClass",
                "staticField",
                "I"
            );

            assertEquals(MethodHandle.H_PUTSTATIC, mh.getTag());
        }
    }

    @Nested
    class SpecialMethodHandles {

        @Test
        void newInvokeSpecialHandle() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_NEWINVOKESPECIAL,
                "java/lang/StringBuilder",
                "<init>",
                "()V"
            );

            assertEquals(MethodHandle.H_NEWINVOKESPECIAL, mh.getTag());
        }

        @Test
        void invokeSpecialHandle() {
            MethodHandle mh = new MethodHandle(
                MethodHandle.H_INVOKESPECIAL,
                "java/lang/Object",
                "<init>",
                "()V"
            );

            assertEquals(MethodHandle.H_INVOKESPECIAL, mh.getTag());
        }
    }
}
