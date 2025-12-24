package com.tonic.renamer.mapping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MethodMappingTest {

    @Nested
    class ConstructorTests {

        @Test
        void validMappingWithPropagateCreatedSuccessfully() {
            MethodMapping mapping = new MethodMapping(
                "com/example/Service", "process", "(I)V", "handle", true
            );

            assertEquals("com/example/Service", mapping.getOwner());
            assertEquals("process", mapping.getOldName());
            assertEquals("(I)V", mapping.getDescriptor());
            assertEquals("handle", mapping.getNewName());
            assertTrue(mapping.isPropagate());
        }

        @Test
        void validMappingWithoutPropagateCreatedSuccessfully() {
            MethodMapping mapping = new MethodMapping(
                "com/example/Service", "process", "(I)V", "handle", false
            );

            assertFalse(mapping.isPropagate());
        }

        @Test
        void defaultConstructorSetsPropagateFalse() {
            MethodMapping mapping = new MethodMapping(
                "com/example/Service", "process", "(I)V", "handle"
            );

            assertFalse(mapping.isPropagate());
        }

        @Test
        void rejectsNullOwner() {
            assertThrows(IllegalArgumentException.class, () ->
                new MethodMapping(null, "method", "()V", "renamed")
            );
        }

        @Test
        void rejectsNullOldName() {
            assertThrows(IllegalArgumentException.class, () ->
                new MethodMapping("com/example/Class", null, "()V", "renamed")
            );
        }

        @Test
        void rejectsNullDescriptor() {
            assertThrows(IllegalArgumentException.class, () ->
                new MethodMapping("com/example/Class", "method", null, "renamed")
            );
        }

        @Test
        void rejectsNullNewName() {
            assertThrows(IllegalArgumentException.class, () ->
                new MethodMapping("com/example/Class", "method", "()V", null)
            );
        }

        @Test
        void rejectsEmptyOwner() {
            assertThrows(IllegalArgumentException.class, () ->
                new MethodMapping("", "method", "()V", "renamed")
            );
        }

        @Test
        void rejectsEmptyOldName() {
            assertThrows(IllegalArgumentException.class, () ->
                new MethodMapping("com/example/Class", "", "()V", "renamed")
            );
        }

        @Test
        void rejectsEmptyDescriptor() {
            assertThrows(IllegalArgumentException.class, () ->
                new MethodMapping("com/example/Class", "method", "", "renamed")
            );
        }

        @Test
        void rejectsEmptyNewName() {
            assertThrows(IllegalArgumentException.class, () ->
                new MethodMapping("com/example/Class", "method", "()V", "")
            );
        }

        @Test
        void rejectsSameOldAndNewName() {
            assertThrows(IllegalArgumentException.class, () ->
                new MethodMapping("com/example/Class", "method", "()V", "method")
            );
        }

        @Test
        void rejectsConstructorRename() {
            assertThrows(IllegalArgumentException.class, () ->
                new MethodMapping("com/example/Class", "<init>", "()V", "renamed")
            );
        }

        @Test
        void rejectsStaticInitializerRename() {
            assertThrows(IllegalArgumentException.class, () ->
                new MethodMapping("com/example/Class", "<clinit>", "()V", "renamed")
            );
        }
    }

    @Nested
    class FullyQualifiedNameTests {

        @Test
        void fullyQualifiedNameHasCorrectFormat() {
            MethodMapping mapping = new MethodMapping(
                "com/example/Service", "process", "(I)V", "handle"
            );

            assertEquals("com/example/Service.process:(I)V", mapping.getFullyQualifiedName());
        }

        @Test
        void fullyQualifiedNameWithComplexDescriptor() {
            MethodMapping mapping = new MethodMapping(
                "com/example/Service", "transform",
                "(Ljava/lang/String;ILjava/util/List;)Ljava/lang/Object;", "convert"
            );

            assertEquals(
                "com/example/Service.transform:(Ljava/lang/String;ILjava/util/List;)Ljava/lang/Object;",
                mapping.getFullyQualifiedName()
            );
        }
    }

    @Nested
    class EqualityTests {

        @Test
        void equalMappingsAreEqual() {
            MethodMapping mapping1 = new MethodMapping(
                "com/example/Class", "method", "()V", "renamed", true
            );
            MethodMapping mapping2 = new MethodMapping(
                "com/example/Class", "method", "()V", "renamed", true
            );

            assertEquals(mapping1, mapping2);
            assertEquals(mapping1.hashCode(), mapping2.hashCode());
        }

        @Test
        void differentPropagateFlagMakesNotEqual() {
            MethodMapping mapping1 = new MethodMapping(
                "com/example/Class", "method", "()V", "renamed", true
            );
            MethodMapping mapping2 = new MethodMapping(
                "com/example/Class", "method", "()V", "renamed", false
            );

            assertNotEquals(mapping1, mapping2);
        }

        @Test
        void differentOwnerMakesNotEqual() {
            MethodMapping mapping1 = new MethodMapping(
                "com/example/ClassA", "method", "()V", "renamed"
            );
            MethodMapping mapping2 = new MethodMapping(
                "com/example/ClassB", "method", "()V", "renamed"
            );

            assertNotEquals(mapping1, mapping2);
        }

        @Test
        void differentDescriptorMakesNotEqual() {
            MethodMapping mapping1 = new MethodMapping(
                "com/example/Class", "method", "(I)V", "renamed"
            );
            MethodMapping mapping2 = new MethodMapping(
                "com/example/Class", "method", "()V", "renamed"
            );

            assertNotEquals(mapping1, mapping2);
        }

        @Test
        void equalToSelf() {
            MethodMapping mapping = new MethodMapping(
                "com/example/Class", "method", "()V", "renamed"
            );
            assertEquals(mapping, mapping);
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringContainsAllComponents() {
            MethodMapping mapping = new MethodMapping(
                "com/example/Service", "process", "(I)V", "handle", false
            );
            String str = mapping.toString();

            assertTrue(str.contains("com/example/Service"));
            assertTrue(str.contains("process"));
            assertTrue(str.contains("(I)V"));
            assertTrue(str.contains("handle"));
            assertTrue(str.contains("->"));
        }

        @Test
        void toStringIndicatesPropagateWhenTrue() {
            MethodMapping mapping = new MethodMapping(
                "com/example/Service", "process", "(I)V", "handle", true
            );
            String str = mapping.toString();

            assertTrue(str.contains("propagate"));
        }

        @Test
        void toStringOmitsPropagateWhenFalse() {
            MethodMapping mapping = new MethodMapping(
                "com/example/Service", "process", "(I)V", "handle", false
            );
            String str = mapping.toString();

            assertFalse(str.contains("propagate"));
        }
    }
}
