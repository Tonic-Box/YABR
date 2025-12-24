package com.tonic.renamer.mapping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassMappingTest {

    @Nested
    class ConstructorTests {

        @Test
        void validMappingCreatedSuccessfully() {
            ClassMapping mapping = new ClassMapping("com/old/MyClass", "com/new/RenamedClass");

            assertEquals("com/old/MyClass", mapping.getOldName());
            assertEquals("com/new/RenamedClass", mapping.getNewName());
        }

        @Test
        void rejectsNullOldName() {
            assertThrows(IllegalArgumentException.class, () ->
                new ClassMapping(null, "com/new/Class")
            );
        }

        @Test
        void rejectsNullNewName() {
            assertThrows(IllegalArgumentException.class, () ->
                new ClassMapping("com/old/Class", null)
            );
        }

        @Test
        void rejectsEmptyOldName() {
            assertThrows(IllegalArgumentException.class, () ->
                new ClassMapping("", "com/new/Class")
            );
        }

        @Test
        void rejectsEmptyNewName() {
            assertThrows(IllegalArgumentException.class, () ->
                new ClassMapping("com/old/Class", "")
            );
        }

        @Test
        void rejectsSameOldAndNewName() {
            assertThrows(IllegalArgumentException.class, () ->
                new ClassMapping("com/same/Class", "com/same/Class")
            );
        }
    }

    @Nested
    class EqualityTests {

        @Test
        void equalMappingsAreEqual() {
            ClassMapping mapping1 = new ClassMapping("com/old/Class", "com/new/Class");
            ClassMapping mapping2 = new ClassMapping("com/old/Class", "com/new/Class");

            assertEquals(mapping1, mapping2);
            assertEquals(mapping1.hashCode(), mapping2.hashCode());
        }

        @Test
        void differentOldNamesNotEqual() {
            ClassMapping mapping1 = new ClassMapping("com/old/ClassA", "com/new/Class");
            ClassMapping mapping2 = new ClassMapping("com/old/ClassB", "com/new/Class");

            assertNotEquals(mapping1, mapping2);
        }

        @Test
        void differentNewNamesNotEqual() {
            ClassMapping mapping1 = new ClassMapping("com/old/Class", "com/new/ClassA");
            ClassMapping mapping2 = new ClassMapping("com/old/Class", "com/new/ClassB");

            assertNotEquals(mapping1, mapping2);
        }

        @Test
        void equalToSelf() {
            ClassMapping mapping = new ClassMapping("com/old/Class", "com/new/Class");
            assertEquals(mapping, mapping);
        }

        @Test
        void notEqualToNull() {
            ClassMapping mapping = new ClassMapping("com/old/Class", "com/new/Class");
            assertNotEquals(mapping, null);
        }

        @Test
        void notEqualToDifferentType() {
            ClassMapping mapping = new ClassMapping("com/old/Class", "com/new/Class");
            assertNotEquals(mapping, "not a ClassMapping");
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringContainsOldAndNewNames() {
            ClassMapping mapping = new ClassMapping("com/old/Class", "com/new/Class");
            String str = mapping.toString();

            assertTrue(str.contains("com/old/Class"));
            assertTrue(str.contains("com/new/Class"));
            assertTrue(str.contains("->"));
        }
    }
}
