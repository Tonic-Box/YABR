package com.tonic.analysis.source.emit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IdentifierNormalizer - handling of obfuscated/non-standard identifiers.
 */
class IdentifierNormalizerTest {

    // ========== RAW Mode Tests ==========

    @Test
    void rawModeKeepsIdentifiersUnchanged() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.RAW);

        assertEquals("validName", normalizer.normalize("validName", IdentifierNormalizer.IdentifierType.METHOD));
        assertEquals("?invalid?", normalizer.normalize("?invalid?", IdentifierNormalizer.IdentifierType.METHOD));
        assertEquals("123start", normalizer.normalize("123start", IdentifierNormalizer.IdentifierType.FIELD));
    }

    @Test
    void rawModeKeepsClassNamesUnchanged() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.RAW);

        assertEquals("com/test/MyClass", normalizer.normalizeClassName("com/test/MyClass"));
        assertEquals("x/???/Test", normalizer.normalizeClassName("x/???/Test"));
    }

    // ========== Unicode Escape Mode Tests ==========

    @Test
    void unicodeEscapeModeKeepsValidIdentifiers() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.UNICODE_ESCAPE);

        assertEquals("validName", normalizer.normalize("validName", IdentifierNormalizer.IdentifierType.METHOD));
        assertEquals("_underscore", normalizer.normalize("_underscore", IdentifierNormalizer.IdentifierType.FIELD));
        assertEquals("$dollar", normalizer.normalize("$dollar", IdentifierNormalizer.IdentifierType.VARIABLE));
    }

    @Test
    void unicodeEscapeModeEscapesInvalidStartChar() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.UNICODE_ESCAPE);

        String result = normalizer.normalize("1startWithNumber", IdentifierNormalizer.IdentifierType.FIELD);
        assertTrue(result.startsWith("\\u0031")); // '1' = 0x31
        assertTrue(result.contains("startWithNumber"));
    }

    @Test
    void unicodeEscapeModeEscapesSpecialChars() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.UNICODE_ESCAPE);

        String result = normalizer.normalize("name?with?question", IdentifierNormalizer.IdentifierType.METHOD);
        assertTrue(result.contains("\\u003f")); // '?' = 0x3F
        assertTrue(result.contains("name"));
        assertTrue(result.contains("with"));
    }

    @Test
    void unicodeEscapeModeEscapesInvalidChars() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.UNICODE_ESCAPE);

        // Test with characters that are not valid Java identifier parts
        String input = "name@with#special"; // @ and # are not valid identifier chars
        String result = normalizer.normalize(input, IdentifierNormalizer.IdentifierType.METHOD);

        assertTrue(result.contains("\\u0040")); // '@' = 0x40
        assertTrue(result.contains("\\u0023")); // '#' = 0x23
        assertTrue(result.contains("name"));
        assertTrue(result.contains("with"));
    }

    @Test
    void unicodeEscapeModeHandlesClassNameParts() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.UNICODE_ESCAPE);

        String result = normalizer.normalizeClassName("x/test?pkg/MyClass");
        assertTrue(result.contains("x"));
        assertTrue(result.contains("\\u003f")); // '?' escaped
        assertTrue(result.contains("MyClass"));
    }

    // ========== Semantic Rename Mode Tests ==========

    @Test
    void semanticRenameModeKeepsValidIdentifiers() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.SEMANTIC_RENAME);

        assertEquals("validMethod", normalizer.normalize("validMethod", IdentifierNormalizer.IdentifierType.METHOD));
        assertEquals("validField", normalizer.normalize("validField", IdentifierNormalizer.IdentifierType.FIELD));
    }

    @Test
    void semanticRenameModeRenamesInvalidMethodNames() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.SEMANTIC_RENAME);

        String result = normalizer.normalize("???invalid", IdentifierNormalizer.IdentifierType.METHOD);
        assertTrue(result.startsWith("method_"));
    }

    @Test
    void semanticRenameModeRenamesInvalidFieldNames() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.SEMANTIC_RENAME);

        String result = normalizer.normalize("123field", IdentifierNormalizer.IdentifierType.FIELD);
        assertTrue(result.startsWith("field_"));
    }

    @Test
    void semanticRenameModeRenamesInvalidClassNames() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.SEMANTIC_RENAME);

        String result = normalizer.normalize("???Class", IdentifierNormalizer.IdentifierType.CLASS);
        assertTrue(result.startsWith("Class_"));
    }

    @Test
    void semanticRenameModeRenamesInvalidVariableNames() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.SEMANTIC_RENAME);

        String result = normalizer.normalize("@var", IdentifierNormalizer.IdentifierType.VARIABLE);
        assertTrue(result.startsWith("var_"));
    }

    @Test
    void semanticRenameModeRenamesInvalidConstantNames() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.SEMANTIC_RENAME);

        String result = normalizer.normalize("!!!const", IdentifierNormalizer.IdentifierType.CONSTANT);
        assertTrue(result.startsWith("const_"));
    }

    @Test
    void semanticRenameModeIsConsistent() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.SEMANTIC_RENAME);

        String first = normalizer.normalize("???same", IdentifierNormalizer.IdentifierType.METHOD);
        String second = normalizer.normalize("???same", IdentifierNormalizer.IdentifierType.METHOD);

        assertEquals(first, second, "Same invalid identifier should get same renamed value");
    }

    @Test
    void semanticRenameModeIncrementsCounters() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.SEMANTIC_RENAME);

        String first = normalizer.normalize("???a", IdentifierNormalizer.IdentifierType.METHOD);
        String second = normalizer.normalize("???b", IdentifierNormalizer.IdentifierType.METHOD);

        assertNotEquals(first, second, "Different invalid identifiers should get different names");
        assertTrue(first.startsWith("method_"));
        assertTrue(second.startsWith("method_"));
    }

    @Test
    void semanticRenameModeHandlesClassNameParts() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.SEMANTIC_RENAME);

        String result = normalizer.normalizeClassName("x/pkg/???Invalid");
        assertTrue(result.contains("x"));
        assertTrue(result.contains("pkg"));
        assertTrue(result.contains("Class_")); // The class name part gets renamed
    }

    // ========== Valid Identifier Detection Tests ==========

    @Test
    void isValidJavaIdentifierForValidNames() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.RAW);

        assertTrue(normalizer.isValidJavaIdentifier("validName"));
        assertTrue(normalizer.isValidJavaIdentifier("_underscore"));
        assertTrue(normalizer.isValidJavaIdentifier("$dollar"));
        assertTrue(normalizer.isValidJavaIdentifier("CamelCase"));
        assertTrue(normalizer.isValidJavaIdentifier("with123numbers"));
    }

    @Test
    void isValidJavaIdentifierForInvalidNames() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.RAW);

        assertFalse(normalizer.isValidJavaIdentifier("123startWithNumber"));
        assertFalse(normalizer.isValidJavaIdentifier("has space"));
        assertFalse(normalizer.isValidJavaIdentifier("has?question"));
        assertFalse(normalizer.isValidJavaIdentifier(""));
        assertFalse(normalizer.isValidJavaIdentifier(null));
    }

    @Test
    void isValidJavaIdentifierRejectsKeywords() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.RAW);

        assertFalse(normalizer.isValidJavaIdentifier("class"));
        assertFalse(normalizer.isValidJavaIdentifier("public"));
        assertFalse(normalizer.isValidJavaIdentifier("static"));
        assertFalse(normalizer.isValidJavaIdentifier("void"));
        assertFalse(normalizer.isValidJavaIdentifier("true"));
        assertFalse(normalizer.isValidJavaIdentifier("false"));
        assertFalse(normalizer.isValidJavaIdentifier("null"));
    }

    // ========== Reset Tests ==========

    @Test
    void resetClearsCountersAndCache() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.SEMANTIC_RENAME);

        String beforeReset = normalizer.normalize("???test", IdentifierNormalizer.IdentifierType.METHOD);
        normalizer.reset();
        String afterReset = normalizer.normalize("???test", IdentifierNormalizer.IdentifierType.METHOD);

        // After reset, counter starts again so we should get method_1 again
        assertEquals(beforeReset, afterReset);
    }

    // ========== Null and Edge Case Tests ==========

    @Test
    void handlesNullIdentifier() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.UNICODE_ESCAPE);

        assertNull(normalizer.normalize(null, IdentifierNormalizer.IdentifierType.METHOD));
    }

    @Test
    void handlesEmptyIdentifier() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.UNICODE_ESCAPE);

        assertEquals("", normalizer.normalize("", IdentifierNormalizer.IdentifierType.METHOD));
    }

    @Test
    void handlesNullClassName() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.UNICODE_ESCAPE);

        assertNull(normalizer.normalizeClassName(null));
    }

    @Test
    void handlesEmptyClassName() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.UNICODE_ESCAPE);

        assertEquals("", normalizer.normalizeClassName(""));
    }

    // ========== escapeToUnicode Direct Tests ==========

    @Test
    void escapeToUnicodeHandlesInvalidChars() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.RAW);

        // Space is not valid in Java identifiers
        String result = normalizer.escapeToUnicode("test char");
        assertTrue(result.contains("\\u0020")); // space = 0x20
    }

    @Test
    void escapeToUnicodePreservesValidChars() {
        IdentifierNormalizer normalizer = new IdentifierNormalizer(IdentifierMode.RAW);

        String result = normalizer.escapeToUnicode("validName123");
        assertEquals("validName123", result);
    }
}
