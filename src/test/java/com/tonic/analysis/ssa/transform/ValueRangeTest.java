package com.tonic.analysis.ssa.transform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ValueRange class.
 * Tests value range representation and operations.
 */
class ValueRangeTest {

    @Test
    void constructorCreatesRange() {
        ValueRange range = new ValueRange(5, 10);
        assertEquals(5, range.getMin());
        assertEquals(10, range.getMax());
    }

    @Test
    void isEmptyReturnsTrueForInvalidRange() {
        ValueRange range = new ValueRange(10, 5);
        assertTrue(range.isEmpty());
    }

    @Test
    void isEmptyReturnsFalseForValidRange() {
        ValueRange range = new ValueRange(5, 10);
        assertFalse(range.isEmpty());
    }

    @Test
    void isConstantReturnsTrueForSingleValue() {
        ValueRange range = new ValueRange(42, 42);
        assertTrue(range.isConstant());
    }

    @Test
    void isConstantReturnsFalseForRange() {
        ValueRange range = new ValueRange(5, 10);
        assertFalse(range.isConstant());
    }

    @Test
    void containsChecksValueInRange() {
        ValueRange range = new ValueRange(5, 10);
        assertTrue(range.contains(7));
        assertTrue(range.contains(5));
        assertTrue(range.contains(10));
        assertFalse(range.contains(4));
        assertFalse(range.contains(11));
    }

    @Test
    void intersectNarrowsRange() {
        ValueRange range1 = new ValueRange(5, 15);
        ValueRange range2 = new ValueRange(10, 20);
        ValueRange result = range1.intersect(range2);

        assertEquals(10, result.getMin());
        assertEquals(15, result.getMax());
    }

    @Test
    void intersectReturnsEmptyForNoOverlap() {
        ValueRange range1 = new ValueRange(5, 10);
        ValueRange range2 = new ValueRange(15, 20);
        ValueRange result = range1.intersect(range2);

        assertTrue(result.isEmpty());
    }

    @Test
    void lessThanCreatesCorrectRange() {
        ValueRange range = ValueRange.lessThan(10);
        assertEquals(Integer.MIN_VALUE, range.getMin());
        assertEquals(9, range.getMax());
    }

    @Test
    void lessOrEqualCreatesCorrectRange() {
        ValueRange range = ValueRange.lessOrEqual(10);
        assertEquals(Integer.MIN_VALUE, range.getMin());
        assertEquals(10, range.getMax());
    }

    @Test
    void greaterThanCreatesCorrectRange() {
        ValueRange range = ValueRange.greaterThan(10);
        assertEquals(11, range.getMin());
        assertEquals(Integer.MAX_VALUE, range.getMax());
    }

    @Test
    void greaterOrEqualCreatesCorrectRange() {
        ValueRange range = ValueRange.greaterOrEqual(10);
        assertEquals(10, range.getMin());
        assertEquals(Integer.MAX_VALUE, range.getMax());
    }

    @Test
    void equalToCreatesConstantRange() {
        ValueRange range = ValueRange.equalTo(42);
        assertEquals(42, range.getMin());
        assertEquals(42, range.getMax());
        assertTrue(range.isConstant());
    }

    @Test
    void fullIntRangeCoversAllIntegers() {
        ValueRange range = ValueRange.FULL_INT;
        assertEquals(Integer.MIN_VALUE, range.getMin());
        assertEquals(Integer.MAX_VALUE, range.getMax());
        assertTrue(range.contains(0));
        assertTrue(range.contains(Integer.MIN_VALUE));
        assertTrue(range.contains(Integer.MAX_VALUE));
    }

    @Test
    void emptyRangeIsEmpty() {
        ValueRange range = ValueRange.EMPTY;
        assertTrue(range.isEmpty());
        assertFalse(range.contains(0));
    }

    @Test
    void toStringFormatsRange() {
        ValueRange range = new ValueRange(5, 10);
        String str = range.toString();
        assertTrue(str.contains("5"));
        assertTrue(str.contains("10"));
    }

    @Test
    void toStringFormatsConstant() {
        ValueRange range = new ValueRange(42, 42);
        String str = range.toString();
        assertTrue(str.contains("42"));
    }

    @Test
    void toStringFormatsEmpty() {
        ValueRange range = ValueRange.EMPTY;
        assertEquals("[]", range.toString());
    }

    @Test
    void equalsComparesRanges() {
        ValueRange range1 = new ValueRange(5, 10);
        ValueRange range2 = new ValueRange(5, 10);
        ValueRange range3 = new ValueRange(6, 10);

        assertEquals(range1, range2);
        assertNotEquals(range1, range3);
    }

    @Test
    void hashCodeConsistent() {
        ValueRange range1 = new ValueRange(5, 10);
        ValueRange range2 = new ValueRange(5, 10);

        assertEquals(range1.hashCode(), range2.hashCode());
    }

    @Test
    void intersectWithEmptyReturnsEmpty() {
        ValueRange range = new ValueRange(5, 10);
        ValueRange result = range.intersect(ValueRange.EMPTY);

        assertTrue(result.isEmpty());
    }

    @Test
    void lessThanMinValueReturnsEmpty() {
        ValueRange range = ValueRange.lessThan(Integer.MIN_VALUE);
        assertTrue(range.isEmpty());
    }

    @Test
    void greaterThanMaxValueReturnsEmpty() {
        ValueRange range = ValueRange.greaterThan(Integer.MAX_VALUE);
        assertTrue(range.isEmpty());
    }
}
