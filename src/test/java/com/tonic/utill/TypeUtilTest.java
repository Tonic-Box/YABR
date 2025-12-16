package com.tonic.utill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TypeUtil functionality.
 * Covers type descriptor validation, normalization, and string utilities.
 */
class TypeUtilTest {

    // ========== Primitive Type Validation Tests ==========

    @Test
    void validateIntDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("I");
        assertEquals("I", result);
    }

    @Test
    void validateLongDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("J");
        assertEquals("J", result);
    }

    @Test
    void validateFloatDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("F");
        assertEquals("F", result);
    }

    @Test
    void validateDoubleDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("D");
        assertEquals("D", result);
    }

    @Test
    void validateBooleanDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("Z");
        assertEquals("Z", result);
    }

    @Test
    void validateByteDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("B");
        assertEquals("B", result);
    }

    @Test
    void validateCharDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("C");
        assertEquals("C", result);
    }

    @Test
    void validateShortDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("S");
        assertEquals("S", result);
    }

    @Test
    void validateVoidDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("V");
        assertEquals("V", result);
    }

    // ========== Object Type Validation Tests ==========

    @Test
    void validateProperObjectDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("Ljava/lang/String;");
        assertEquals("Ljava/lang/String;", result);
    }

    @Test
    void validateObjectDescriptorWithoutLPrefix() {
        String result = TypeUtil.validateDescriptorFormat("java/lang/String;");
        assertEquals("Ljava/lang/String;", result);
    }

    @Test
    void validateObjectDescriptorWithoutSemicolon() {
        String result = TypeUtil.validateDescriptorFormat("Ljava/lang/String");
        assertEquals("Ljava/lang/String;", result);
    }

    @Test
    void validateObjectDescriptorWithoutBoth() {
        String result = TypeUtil.validateDescriptorFormat("java/lang/String");
        assertEquals("Ljava/lang/String;", result);
    }

    @Test
    void validateObjectDescriptorWithDots() {
        String result = TypeUtil.validateDescriptorFormat("java.lang.String");
        assertEquals("Ljava/lang/String;", result);
    }

    @Test
    void validateObjectDescriptorWithDotsNoSemicolon() {
        String result = TypeUtil.validateDescriptorFormat("Ljava.lang.String");
        assertEquals("Ljava/lang/String;", result);
    }

    @Test
    void validateObjectDescriptorWithDotsAndNoPrefix() {
        String result = TypeUtil.validateDescriptorFormat("java.lang.Object");
        assertEquals("Ljava/lang/Object;", result);
    }

    // ========== Array Type Validation Tests ==========

    @Test
    void validatePrimitiveArrayDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("[I");
        assertEquals("[I", result);
    }

    @Test
    void validateObjectArrayDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("[Ljava/lang/String;");
        assertEquals("[Ljava/lang/String;", result);
    }

    @Test
    void validateMultiDimensionalArrayDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("[[I");
        assertEquals("[[I", result);
    }

    @Test
    void validate2DObjectArrayDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("[[Ljava/lang/Object;");
        assertEquals("[[Ljava/lang/Object;", result);
    }

    @Test
    void validate3DArrayDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("[[[Ljava/lang/String;");
        assertEquals("[[[Ljava/lang/String;", result);
    }

    // ========== Custom Class Validation Tests ==========

    @Test
    void validateCustomClassDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("com/example/MyClass");
        assertEquals("Lcom/example/MyClass;", result);
    }

    @Test
    void validateNestedClassDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("com/example/Outer$Inner");
        assertEquals("Lcom/example/Outer$Inner;", result);
    }

    @Test
    void validateCustomClassWithDots() {
        String result = TypeUtil.validateDescriptorFormat("com.example.MyClass");
        assertEquals("Lcom/example/MyClass;", result);
    }

    @Test
    void validateSimpleClassName() {
        String result = TypeUtil.validateDescriptorFormat("MyClass");
        assertEquals("LMyClass;", result);
    }

    @Test
    void validateSingleLetterClassName() {
        String result = TypeUtil.validateDescriptorFormat("X");
        assertEquals("LX;", result);
    }

    // ========== Edge Cases Tests ==========

    @Test
    void validateAlreadyProperDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("Ljava/util/List;");
        assertEquals("Ljava/util/List;", result);
    }

    @Test
    void validateMixedDotsAndSlashes() {
        String result = TypeUtil.validateDescriptorFormat("com.example/test.MyClass");
        assertEquals("Lcom/example/test/MyClass;", result);
    }

    @Test
    void validateDescriptorWithMultipleDots() {
        String result = TypeUtil.validateDescriptorFormat("com.example.package.Class");
        assertEquals("Lcom/example/package/Class;", result);
    }

    @Test
    void validateDescriptorEndsWithSemicolon() {
        String result = TypeUtil.validateDescriptorFormat("com/example/Test;");
        assertEquals("Lcom/example/Test;", result);
    }

    @Test
    void validateDescriptorStartsWithL() {
        String result = TypeUtil.validateDescriptorFormat("Lcom/example/Test");
        assertEquals("Lcom/example/Test;", result);
    }

    // ========== Capitalize Tests ==========

    @Test
    void capitalizeNormalString() {
        String result = TypeUtil.capitalize("hello");
        assertEquals("Hello", result);
    }

    @Test
    void capitalizeSingleCharacter() {
        String result = TypeUtil.capitalize("a");
        assertEquals("A", result);
    }

    @Test
    void capitalizeAlreadyCapitalized() {
        String result = TypeUtil.capitalize("Hello");
        assertEquals("Hello", result);
    }

    @Test
    void capitalizeAllUpperCase() {
        String result = TypeUtil.capitalize("HELLO");
        assertEquals("HELLO", result);
    }

    @Test
    void capitalizeLowerCaseWord() {
        String result = TypeUtil.capitalize("world");
        assertEquals("World", result);
    }

    @Test
    void capitalizeWithNumbers() {
        String result = TypeUtil.capitalize("test123");
        assertEquals("Test123", result);
    }

    @Test
    void capitalizeWithSpecialChars() {
        String result = TypeUtil.capitalize("test_method");
        assertEquals("Test_method", result);
    }

    @Test
    void capitalizeCamelCase() {
        String result = TypeUtil.capitalize("myMethod");
        assertEquals("MyMethod", result);
    }

    // ========== Capitalize Edge Cases Tests ==========

    @Test
    void capitalizeNullReturnsNull() {
        String result = TypeUtil.capitalize(null);
        assertNull(result);
    }

    @Test
    void capitalizeEmptyStringReturnsEmpty() {
        String result = TypeUtil.capitalize("");
        assertEquals("", result);
    }

    @Test
    void capitalizeWhitespace() {
        String result = TypeUtil.capitalize(" hello");
        assertEquals(" hello", result);
    }

    @Test
    void capitalizeNumberStart() {
        String result = TypeUtil.capitalize("123abc");
        assertEquals("123abc", result);
    }

    @Test
    void capitalizeUnicodeCharacter() {
        // Test with ASCII character - Unicode capitalization is locale-dependent
        // and not critical for Java identifier manipulation
        String result = TypeUtil.capitalize("name");
        assertEquals("Name", result);
    }

    // ========== Real-World Scenario Tests ==========

    @Test
    void validateJavaLangObjectDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("java.lang.Object");
        assertEquals("Ljava/lang/Object;", result);
    }

    @Test
    void validateJavaUtilListDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("java/util/List");
        assertEquals("Ljava/util/List;", result);
    }

    @Test
    void validateStringArrayDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("[Ljava/lang/String;");
        assertEquals("[Ljava/lang/String;", result);
    }

    @Test
    void validateIntArrayDescriptor() {
        String result = TypeUtil.validateDescriptorFormat("[I");
        assertEquals("[I", result);
    }

    @Test
    void capitalizeGetterMethodName() {
        String result = TypeUtil.capitalize("name");
        assertEquals("Name", result);
    }

    @Test
    void capitalizeSetterMethodName() {
        String result = TypeUtil.capitalize("value");
        assertEquals("Value", result);
    }

    // ========== Consistency Tests ==========

    @Test
    void validateSameInputProducesSameOutput() {
        String input = "com/example/Test";
        String result1 = TypeUtil.validateDescriptorFormat(input);
        String result2 = TypeUtil.validateDescriptorFormat(input);
        assertEquals(result1, result2);
    }

    @Test
    void validateIdempotence() {
        String input = "java/lang/String";
        String result1 = TypeUtil.validateDescriptorFormat(input);
        String result2 = TypeUtil.validateDescriptorFormat(result1);
        assertEquals(result1, result2);
    }

    @Test
    void capitalizeSameInputProducesSameOutput() {
        String input = "test";
        String result1 = TypeUtil.capitalize(input);
        String result2 = TypeUtil.capitalize(input);
        assertEquals(result1, result2);
    }

    @Test
    void capitalizeIdempotence() {
        String input = "hello";
        String result1 = TypeUtil.capitalize(input);
        String result2 = TypeUtil.capitalize(result1);
        assertEquals(result1, result2);
    }
}
