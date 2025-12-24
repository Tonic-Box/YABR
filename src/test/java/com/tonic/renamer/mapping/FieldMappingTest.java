package com.tonic.renamer.mapping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldMappingTest {

    @Nested
    class ConstructorTests {

        @Test
        void validMappingCreatedSuccessfully() {
            FieldMapping mapping = new FieldMapping(
                "com/example/Model", "data", "Ljava/lang/String;", "content"
            );

            assertEquals("com/example/Model", mapping.getOwner());
            assertEquals("data", mapping.getOldName());
            assertEquals("Ljava/lang/String;", mapping.getDescriptor());
            assertEquals("content", mapping.getNewName());
        }

        @Test
        void rejectsNullOwner() {
            assertThrows(IllegalArgumentException.class, () ->
                new FieldMapping(null, "field", "I", "renamed")
            );
        }

        @Test
        void rejectsNullOldName() {
            assertThrows(IllegalArgumentException.class, () ->
                new FieldMapping("com/example/Class", null, "I", "renamed")
            );
        }

        @Test
        void rejectsNullDescriptor() {
            assertThrows(IllegalArgumentException.class, () ->
                new FieldMapping("com/example/Class", "field", null, "renamed")
            );
        }

        @Test
        void rejectsNullNewName() {
            assertThrows(IllegalArgumentException.class, () ->
                new FieldMapping("com/example/Class", "field", "I", null)
            );
        }

        @Test
        void rejectsEmptyOwner() {
            assertThrows(IllegalArgumentException.class, () ->
                new FieldMapping("", "field", "I", "renamed")
            );
        }

        @Test
        void rejectsEmptyOldName() {
            assertThrows(IllegalArgumentException.class, () ->
                new FieldMapping("com/example/Class", "", "I", "renamed")
            );
        }

        @Test
        void rejectsEmptyDescriptor() {
            assertThrows(IllegalArgumentException.class, () ->
                new FieldMapping("com/example/Class", "field", "", "renamed")
            );
        }

        @Test
        void rejectsEmptyNewName() {
            assertThrows(IllegalArgumentException.class, () ->
                new FieldMapping("com/example/Class", "field", "I", "")
            );
        }

        @Test
        void rejectsSameOldAndNewName() {
            assertThrows(IllegalArgumentException.class, () ->
                new FieldMapping("com/example/Class", "field", "I", "field")
            );
        }
    }

    @Nested
    class FullyQualifiedNameTests {

        @Test
        void fullyQualifiedNameHasCorrectFormat() {
            FieldMapping mapping = new FieldMapping(
                "com/example/Model", "count", "I", "total"
            );

            assertEquals("com/example/Model.count:I", mapping.getFullyQualifiedName());
        }

        @Test
        void fullyQualifiedNameWithObjectDescriptor() {
            FieldMapping mapping = new FieldMapping(
                "com/example/Model", "data", "Ljava/lang/String;", "content"
            );

            assertEquals("com/example/Model.data:Ljava/lang/String;",
                mapping.getFullyQualifiedName());
        }
    }

    @Nested
    class EqualityTests {

        @Test
        void equalMappingsAreEqual() {
            FieldMapping mapping1 = new FieldMapping(
                "com/example/Class", "field", "I", "renamed"
            );
            FieldMapping mapping2 = new FieldMapping(
                "com/example/Class", "field", "I", "renamed"
            );

            assertEquals(mapping1, mapping2);
            assertEquals(mapping1.hashCode(), mapping2.hashCode());
        }

        @Test
        void differentOwnerMakesNotEqual() {
            FieldMapping mapping1 = new FieldMapping(
                "com/example/ClassA", "field", "I", "renamed"
            );
            FieldMapping mapping2 = new FieldMapping(
                "com/example/ClassB", "field", "I", "renamed"
            );

            assertNotEquals(mapping1, mapping2);
        }

        @Test
        void differentDescriptorMakesNotEqual() {
            FieldMapping mapping1 = new FieldMapping(
                "com/example/Class", "field", "I", "renamed"
            );
            FieldMapping mapping2 = new FieldMapping(
                "com/example/Class", "field", "J", "renamed"
            );

            assertNotEquals(mapping1, mapping2);
        }

        @Test
        void equalToSelf() {
            FieldMapping mapping = new FieldMapping(
                "com/example/Class", "field", "I", "renamed"
            );
            assertEquals(mapping, mapping);
        }

        @Test
        void notEqualToNull() {
            FieldMapping mapping = new FieldMapping(
                "com/example/Class", "field", "I", "renamed"
            );
            assertNotEquals(mapping, null);
        }

        @Test
        void notEqualToDifferentType() {
            FieldMapping mapping = new FieldMapping(
                "com/example/Class", "field", "I", "renamed"
            );
            assertNotEquals(mapping, "not a FieldMapping");
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringContainsAllComponents() {
            FieldMapping mapping = new FieldMapping(
                "com/example/Model", "data", "Ljava/lang/String;", "content"
            );
            String str = mapping.toString();

            assertTrue(str.contains("com/example/Model"));
            assertTrue(str.contains("data"));
            assertTrue(str.contains("Ljava/lang/String;"));
            assertTrue(str.contains("content"));
            assertTrue(str.contains("->"));
        }
    }
}
