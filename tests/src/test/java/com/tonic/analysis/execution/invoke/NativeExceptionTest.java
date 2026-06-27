package com.tonic.analysis.execution.invoke;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeExceptionTest {

    @Test
    void testConstruction() {
        NativeException ex = new NativeException("java/lang/RuntimeException", "Test message");

        assertEquals("java/lang/RuntimeException", ex.getExceptionClass());
        assertEquals("Test message", ex.getMessage());
    }

    @Test
    void testConstructionWithNullMessage() {
        NativeException ex = new NativeException("java/lang/Exception", null);

        assertEquals("java/lang/Exception", ex.getExceptionClass());
        assertNull(ex.getMessage());
    }

    @Test
    void testToString() {
        NativeException ex = new NativeException("java/lang/IllegalStateException", "Bad state");
        String str = ex.toString();

        assertTrue(str.contains("java/lang/IllegalStateException"));
        assertTrue(str.contains("Bad state"));
    }

    @Test
    void testExceptionClassAccessor() {
        NativeException ex = new NativeException("java/io/IOException", "IO error");
        assertEquals("java/io/IOException", ex.getExceptionClass());
    }

    @Test
    void testMessageAccessor() {
        NativeException ex = new NativeException("java/lang/Error", "Critical error");
        assertEquals("Critical error", ex.getMessage());
    }
}
