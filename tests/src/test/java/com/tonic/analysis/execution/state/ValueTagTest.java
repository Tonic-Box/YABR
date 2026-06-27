package com.tonic.analysis.execution.state;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValueTagTest {

    @Nested
    class EnumValuesTests {

        @Test
        void allEnumValuesExist() {
            assertEquals(7, ValueTag.values().length);
            assertNotNull(ValueTag.INT);
            assertNotNull(ValueTag.LONG);
            assertNotNull(ValueTag.FLOAT);
            assertNotNull(ValueTag.DOUBLE);
            assertNotNull(ValueTag.REFERENCE);
            assertNotNull(ValueTag.NULL);
            assertNotNull(ValueTag.RETURN_ADDRESS);
        }

        @Test
        void valueOfReturnsCorrectEnum() {
            assertEquals(ValueTag.INT, ValueTag.valueOf("INT"));
            assertEquals(ValueTag.LONG, ValueTag.valueOf("LONG"));
            assertEquals(ValueTag.FLOAT, ValueTag.valueOf("FLOAT"));
            assertEquals(ValueTag.DOUBLE, ValueTag.valueOf("DOUBLE"));
            assertEquals(ValueTag.REFERENCE, ValueTag.valueOf("REFERENCE"));
            assertEquals(ValueTag.NULL, ValueTag.valueOf("NULL"));
            assertEquals(ValueTag.RETURN_ADDRESS, ValueTag.valueOf("RETURN_ADDRESS"));
        }

        @Test
        void valueOfInvalidNameThrows() {
            assertThrows(IllegalArgumentException.class, () -> ValueTag.valueOf("INVALID"));
        }
    }

    @Nested
    class CategoryTests {

        @Test
        void intHasCategory1() {
            assertEquals(1, ValueTag.INT.getCategory());
        }

        @Test
        void longHasCategory2() {
            assertEquals(2, ValueTag.LONG.getCategory());
        }

        @Test
        void floatHasCategory1() {
            assertEquals(1, ValueTag.FLOAT.getCategory());
        }

        @Test
        void doubleHasCategory2() {
            assertEquals(2, ValueTag.DOUBLE.getCategory());
        }

        @Test
        void referenceHasCategory1() {
            assertEquals(1, ValueTag.REFERENCE.getCategory());
        }

        @Test
        void nullHasCategory1() {
            assertEquals(1, ValueTag.NULL.getCategory());
        }

        @Test
        void returnAddressHasCategory1() {
            assertEquals(1, ValueTag.RETURN_ADDRESS.getCategory());
        }
    }

    @Nested
    class IsWideTests {

        @Test
        void intIsNotWide() {
            assertFalse(ValueTag.INT.isWide());
        }

        @Test
        void longIsWide() {
            assertTrue(ValueTag.LONG.isWide());
        }

        @Test
        void floatIsNotWide() {
            assertFalse(ValueTag.FLOAT.isWide());
        }

        @Test
        void doubleIsWide() {
            assertTrue(ValueTag.DOUBLE.isWide());
        }

        @Test
        void referenceIsNotWide() {
            assertFalse(ValueTag.REFERENCE.isWide());
        }

        @Test
        void nullIsNotWide() {
            assertFalse(ValueTag.NULL.isWide());
        }

        @Test
        void returnAddressIsNotWide() {
            assertFalse(ValueTag.RETURN_ADDRESS.isWide());
        }
    }

    @Nested
    class IntegrationTests {

        @Test
        void wideTypesHaveCategory2() {
            for (ValueTag tag : ValueTag.values()) {
                if (tag.isWide()) {
                    assertEquals(2, tag.getCategory());
                }
            }
        }

        @Test
        void nonWideTypesHaveCategory1() {
            for (ValueTag tag : ValueTag.values()) {
                if (!tag.isWide()) {
                    assertEquals(1, tag.getCategory());
                }
            }
        }

        @Test
        void onlyLongAndDoubleAreWide() {
            int wideCount = 0;
            for (ValueTag tag : ValueTag.values()) {
                if (tag.isWide()) {
                    wideCount++;
                    assertTrue(tag == ValueTag.LONG || tag == ValueTag.DOUBLE);
                }
            }
            assertEquals(2, wideCount);
        }
    }
}
