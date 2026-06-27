package com.tonic.analysis.execution.heap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StringPoolTest {

    private StringPool pool;
    private AtomicInteger nextId;

    @BeforeEach
    void setUp() {
        nextId = new AtomicInteger(1);
        pool = new StringPool(nextId);
    }

    @Test
    void testInternReturnsSameInstanceForSameString() {
        ObjectInstance str1 = pool.intern("hello");
        ObjectInstance str2 = pool.intern("hello");

        assertSame(str1, str2);
    }

    @Test
    void testInternDifferentStringsAreDifferent() {
        ObjectInstance str1 = pool.intern("hello");
        ObjectInstance str2 = pool.intern("world");

        assertNotSame(str1, str2);
    }

    @Test
    void testInternCreatesStringObject() {
        ObjectInstance str = pool.intern("test");

        assertEquals("java/lang/String", str.getClassName());
    }

    @Test
    void testInternCreatesCharArray() {
        ObjectInstance str = pool.intern("test");

        Object charArrayObj = str.getField("java/lang/String", "value", "[C");
        assertNotNull(charArrayObj);
        assertTrue(charArrayObj instanceof ArrayInstance);

        ArrayInstance charArray = (ArrayInstance) charArrayObj;
        assertEquals(4, charArray.getLength());
        assertEquals('t', charArray.getChar(0));
        assertEquals('e', charArray.getChar(1));
        assertEquals('s', charArray.getChar(2));
        assertEquals('t', charArray.getChar(3));
    }

    @Test
    void testInternEmptyString() {
        ObjectInstance str = pool.intern("");

        Object charArrayObj = str.getField("java/lang/String", "value", "[C");
        ArrayInstance charArray = (ArrayInstance) charArrayObj;
        assertEquals(0, charArray.getLength());
    }

    @Test
    void testIsInternedTrue() {
        pool.intern("hello");
        assertTrue(pool.isInterned("hello"));
    }

    @Test
    void testIsInternedFalse() {
        assertFalse(pool.isInterned("hello"));
    }

    @Test
    void testSize() {
        assertEquals(0, pool.size());

        pool.intern("one");
        assertEquals(1, pool.size());

        pool.intern("two");
        assertEquals(2, pool.size());

        pool.intern("one");
        assertEquals(2, pool.size());
    }

    @Test
    void testClear() {
        pool.intern("one");
        pool.intern("two");
        assertEquals(2, pool.size());

        pool.clear();
        assertEquals(0, pool.size());
        assertFalse(pool.isInterned("one"));
        assertFalse(pool.isInterned("two"));
    }

    @Test
    void testInternMultipleTimes() {
        ObjectInstance str1 = pool.intern("repeated");
        ObjectInstance str2 = pool.intern("repeated");
        ObjectInstance str3 = pool.intern("repeated");

        assertSame(str1, str2);
        assertSame(str2, str3);
    }

    @Test
    void testInternUniqueIds() {
        ObjectInstance str1 = pool.intern("first");
        ObjectInstance str2 = pool.intern("second");

        assertNotEquals(str1.getId(), str2.getId());
    }

    @Test
    void testInternWithSpecialCharacters() {
        ObjectInstance str = pool.intern("Hello\nWorld\t!");

        Object charArrayObj = str.getField("java/lang/String", "value", "[C");
        ArrayInstance charArray = (ArrayInstance) charArrayObj;

        assertEquals(13, charArray.getLength());
        assertEquals('H', charArray.getChar(0));
        assertEquals('\n', charArray.getChar(5));
        assertEquals('\t', charArray.getChar(11));
    }

    @Test
    void testClearAndReintern() {
        ObjectInstance str1 = pool.intern("test");
        pool.clear();
        ObjectInstance str2 = pool.intern("test");

        assertNotSame(str1, str2);
    }

    @Test
    void testInternLongString() {
        String longString = "a".repeat(1000);
        ObjectInstance str = pool.intern(longString);

        Object charArrayObj = str.getField("java/lang/String", "value", "[C");
        ArrayInstance charArray = (ArrayInstance) charArrayObj;
        assertEquals(1000, charArray.getLength());
    }

    @Test
    void testInternConsumesIds() {
        int startId = nextId.get();
        pool.intern("test");
        int endId = nextId.get();

        assertTrue(endId > startId);
    }
}
