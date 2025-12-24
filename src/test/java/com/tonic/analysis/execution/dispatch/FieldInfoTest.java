package com.tonic.analysis.execution.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldInfoTest {

    @Test
    void constructor_shouldInitializeAllFields() {
        FieldInfo info = new FieldInfo("java/lang/String", "value", "[C", true);

        assertNotNull(info);
        assertEquals("java/lang/String", info.getOwnerClass());
        assertEquals("value", info.getFieldName());
        assertEquals("[C", info.getDescriptor());
        assertTrue(info.isStatic());
    }

    @Test
    void getOwnerClass_shouldReturnCorrectValue() {
        FieldInfo info = new FieldInfo("com/example/Test", "counter", "I", false);

        assertEquals("com/example/Test", info.getOwnerClass());
    }

    @Test
    void getFieldName_shouldReturnCorrectValue() {
        FieldInfo info = new FieldInfo("com/example/Test", "counter", "I", false);

        assertEquals("counter", info.getFieldName());
    }

    @Test
    void getDescriptor_shouldReturnCorrectValue() {
        FieldInfo info = new FieldInfo("com/example/Test", "counter", "I", false);

        assertEquals("I", info.getDescriptor());
    }

    @Test
    void isStatic_shouldReturnTrueForStaticField() {
        FieldInfo info = new FieldInfo("com/example/Config", "DEFAULT_SIZE", "I", true);

        assertTrue(info.isStatic());
    }

    @Test
    void isStatic_shouldReturnFalseForInstanceField() {
        FieldInfo info = new FieldInfo("com/example/Point", "x", "D", false);

        assertFalse(info.isStatic());
    }

    @Test
    void toString_shouldIncludeStaticMarkerForStaticField() {
        FieldInfo info = new FieldInfo("com/example/Config", "MAX_VALUE", "I", true);

        String result = info.toString();

        assertEquals("com/example/Config.MAX_VALUE:I (static)", result);
        assertTrue(result.contains("(static)"));
    }

    @Test
    void toString_shouldNotIncludeStaticMarkerForInstanceField() {
        FieldInfo info = new FieldInfo("com/example/Point", "y", "D", false);

        String result = info.toString();

        assertEquals("com/example/Point.y:D", result);
        assertFalse(result.contains("(static)"));
    }

    @Test
    void toString_shouldFormatWithOwnerClassAndFieldNameAndDescriptor() {
        FieldInfo info = new FieldInfo("java/lang/System", "out", "Ljava/io/PrintStream;", true);

        String result = info.toString();

        assertTrue(result.startsWith("java/lang/System.out:Ljava/io/PrintStream;"));
    }

    @Test
    void shouldHandleComplexDescriptors() {
        FieldInfo info = new FieldInfo("com/example/Data", "matrix", "[[Ljava/lang/Object;", false);

        assertEquals("[[Ljava/lang/Object;", info.getDescriptor());
        assertTrue(info.toString().contains("[[Ljava/lang/Object;"));
    }
}
