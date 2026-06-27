package com.tonic.analysis.execution.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MethodInfoTest {

    @Test
    void constructor_shouldInitializeAllFields() {
        MethodInfo info = new MethodInfo("java/lang/String", "charAt", "(I)C", false, false);

        assertNotNull(info);
        assertEquals("java/lang/String", info.getOwnerClass());
        assertEquals("charAt", info.getMethodName());
        assertEquals("(I)C", info.getDescriptor());
        assertFalse(info.isStatic());
        assertFalse(info.isInterface());
    }

    @Test
    void getOwnerClass_shouldReturnCorrectValue() {
        MethodInfo info = new MethodInfo("com/example/Calculator", "add", "(II)I", true, false);

        assertEquals("com/example/Calculator", info.getOwnerClass());
    }

    @Test
    void getMethodName_shouldReturnCorrectValue() {
        MethodInfo info = new MethodInfo("com/example/Calculator", "multiply", "(DD)D", false, false);

        assertEquals("multiply", info.getMethodName());
    }

    @Test
    void getDescriptor_shouldReturnCorrectValue() {
        MethodInfo info = new MethodInfo("com/example/Service", "process", "(Ljava/lang/String;)V", false, false);

        assertEquals("(Ljava/lang/String;)V", info.getDescriptor());
    }

    @Test
    void isStatic_shouldReturnTrueForStaticMethod() {
        MethodInfo info = new MethodInfo("java/lang/Math", "abs", "(I)I", true, false);

        assertTrue(info.isStatic());
    }

    @Test
    void isStatic_shouldReturnFalseForInstanceMethod() {
        MethodInfo info = new MethodInfo("java/lang/String", "toLowerCase", "()Ljava/lang/String;", false, false);

        assertFalse(info.isStatic());
    }

    @Test
    void isInterface_shouldReturnTrueForInterfaceMethod() {
        MethodInfo info = new MethodInfo("java/util/List", "size", "()I", false, true);

        assertTrue(info.isInterface());
    }

    @Test
    void isInterface_shouldReturnFalseForNonInterfaceMethod() {
        MethodInfo info = new MethodInfo("java/util/ArrayList", "size", "()I", false, false);

        assertFalse(info.isInterface());
    }

    @Test
    void toString_shouldIncludeStaticMarkerForStaticMethod() {
        MethodInfo info = new MethodInfo("com/example/Utils", "helper", "()V", true, false);

        String result = info.toString();

        assertTrue(result.contains("(static)"));
        assertTrue(result.startsWith("com/example/Utils.helper()V"));
    }

    @Test
    void toString_shouldIncludeInterfaceMarkerForInterfaceMethod() {
        MethodInfo info = new MethodInfo("java/lang/Runnable", "run", "()V", false, true);

        String result = info.toString();

        assertTrue(result.contains("(interface)"));
        assertTrue(result.startsWith("java/lang/Runnable.run()V"));
    }

    @Test
    void toString_shouldIncludeBothMarkersForStaticInterfaceMethod() {
        MethodInfo info = new MethodInfo("java/util/Comparator", "naturalOrder", "()Ljava/util/Comparator;", true, true);

        String result = info.toString();

        assertTrue(result.contains("(static)"));
        assertTrue(result.contains("(interface)"));
    }

    @Test
    void toString_shouldNotIncludeMarkersForNormalMethod() {
        MethodInfo info = new MethodInfo("com/example/Bean", "getValue", "()I", false, false);

        String result = info.toString();

        assertEquals("com/example/Bean.getValue()I", result);
        assertFalse(result.contains("(static)"));
        assertFalse(result.contains("(interface)"));
    }

    @Test
    void toString_shouldFormatWithOwnerClassAndMethodNameAndDescriptor() {
        MethodInfo info = new MethodInfo("java/io/PrintStream", "println", "(Ljava/lang/String;)V", false, false);

        String result = info.toString();

        assertTrue(result.startsWith("java/io/PrintStream.println(Ljava/lang/String;)V"));
    }

    @Test
    void shouldHandleComplexMethodDescriptors() {
        MethodInfo info = new MethodInfo("com/example/Processor", "transform",
            "([Ljava/lang/String;Ljava/util/Map;)Ljava/util/List;", false, false);

        assertEquals("([Ljava/lang/String;Ljava/util/Map;)Ljava/util/List;", info.getDescriptor());
        assertTrue(info.toString().contains("([Ljava/lang/String;Ljava/util/Map;)Ljava/util/List;"));
    }
}
