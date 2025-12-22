package com.tonic.analysis.pattern;

import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.InvokeType;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the SearchResult class.
 * Tests cover construction, property access, location formatting, equality, and edge cases.
 */
class SearchResultTest {

    private ClassPool pool;
    private ClassFile classFile;
    private MethodEntry method;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/TestClass", access);
        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        method = classFile.createNewMethod(methodAccess, "testMethod", "V");
    }

    // ========== Test Data Factory Methods ==========

    private IRInstruction createMockInstruction() {
        // Create a simple invoke instruction as mock
        return new InvokeInstruction(
            InvokeType.VIRTUAL,
            "java/lang/Object",
            "toString",
            "()Ljava/lang/String;",
            Collections.<Value>emptyList()
        );
    }

    private SearchResult createFullResult(String description) {
        IRInstruction instruction = createMockInstruction();
        return new SearchResult(classFile, method, instruction, 42, description);
    }

    private SearchResult createMethodResult(String description) {
        return new SearchResult(classFile, method, description);
    }

    private SearchResult createClassResult(String description) {
        return new SearchResult(classFile, description);
    }

    // ========== Construction Tests ==========

    @Nested
    class ConstructionTests {

        @Test
        void constructWithAllParameters() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result = new SearchResult(classFile, method, instruction, 42, "Test description");

            assertNotNull(result);
            assertEquals(classFile, result.getClassFile());
            assertEquals(method, result.getMethod());
            assertEquals(instruction, result.getInstruction());
            assertEquals(42, result.getBytecodeOffset());
            assertEquals("Test description", result.getDescription());
        }

        @Test
        void constructWithClassMethodAndDescription() {
            SearchResult result = new SearchResult(classFile, method, "Method level result");

            assertNotNull(result);
            assertEquals(classFile, result.getClassFile());
            assertEquals(method, result.getMethod());
            assertNull(result.getInstruction());
            assertEquals(-1, result.getBytecodeOffset());
            assertEquals("Method level result", result.getDescription());
        }

        @Test
        void constructWithClassAndDescription() {
            SearchResult result = new SearchResult(classFile, "Class level result");

            assertNotNull(result);
            assertEquals(classFile, result.getClassFile());
            assertNull(result.getMethod());
            assertNull(result.getInstruction());
            assertEquals(-1, result.getBytecodeOffset());
            assertEquals("Class level result", result.getDescription());
        }

        @Test
        void constructWithZeroOffset() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result = new SearchResult(classFile, method, instruction, 0, "Zero offset");

            assertEquals(0, result.getBytecodeOffset());
        }

        @Test
        void constructWithNegativeOffset() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result = new SearchResult(classFile, method, instruction, -5, "Negative offset");

            assertEquals(-5, result.getBytecodeOffset());
        }

        @Test
        void constructWithLargeOffset() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result = new SearchResult(classFile, method, instruction, 65535, "Large offset");

            assertEquals(65535, result.getBytecodeOffset());
        }

        @Test
        void constructWithNullInstruction() {
            SearchResult result = new SearchResult(classFile, method, null, 10, "Null instruction");

            assertNull(result.getInstruction());
            assertEquals(10, result.getBytecodeOffset());
        }

        @Test
        void constructWithNullClassFile() {
            SearchResult result = new SearchResult(null, method, "Null classFile");

            assertNull(result.getClassFile());
            assertEquals(method, result.getMethod());
        }

        @Test
        void constructWithNullMethod() {
            SearchResult result = new SearchResult(classFile, null, "Null method");

            assertEquals(classFile, result.getClassFile());
            assertNull(result.getMethod());
        }

        @Test
        void constructWithAllNullsExceptDescription() {
            SearchResult result = new SearchResult(null, null, null, -1, "All nulls");

            assertNull(result.getClassFile());
            assertNull(result.getMethod());
            assertNull(result.getInstruction());
            assertEquals(-1, result.getBytecodeOffset());
            assertEquals("All nulls", result.getDescription());
        }
    }

    // ========== Property Access Tests ==========

    @Nested
    class PropertyAccessTests {

        @Test
        void getClassFileReturnsCorrectValue() {
            SearchResult result = createFullResult("Test");

            assertEquals(classFile, result.getClassFile());
        }

        @Test
        void getMethodReturnsCorrectValue() {
            SearchResult result = createFullResult("Test");

            assertEquals(method, result.getMethod());
        }

        @Test
        void getInstructionReturnsCorrectValue() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result = new SearchResult(classFile, method, instruction, 10, "Test");

            assertEquals(instruction, result.getInstruction());
        }

        @Test
        void getBytecodeOffsetReturnsCorrectValue() {
            SearchResult result = createFullResult("Test");

            assertEquals(42, result.getBytecodeOffset());
        }

        @Test
        void getDescriptionReturnsCorrectValue() {
            SearchResult result = createFullResult("Custom description");

            assertEquals("Custom description", result.getDescription());
        }

        @Test
        void getDescriptionWithEmptyString() {
            SearchResult result = new SearchResult(classFile, method, "");

            assertEquals("", result.getDescription());
        }

        @Test
        void getDescriptionWithNullDescription() {
            SearchResult result = new SearchResult(classFile, method, null);

            assertNull(result.getDescription());
        }
    }

    // ========== Derived Property Tests ==========

    @Nested
    class DerivedPropertyTests {

        @Test
        void getClassNameReturnsCorrectValue() {
            SearchResult result = createFullResult("Test");

            assertEquals("com/test/TestClass", result.getClassName());
        }

        @Test
        void getClassNameWithNullClassFile() {
            SearchResult result = new SearchResult(null, method, "Test");

            assertNull(result.getClassName());
        }

        @Test
        void getMethodNameReturnsCorrectValue() {
            SearchResult result = createFullResult("Test");

            assertEquals("testMethod", result.getMethodName());
        }

        @Test
        void getMethodNameWithNullMethod() {
            SearchResult result = createClassResult("Test");

            assertNull(result.getMethodName());
        }

        @Test
        void getMethodDescriptorReturnsCorrectValue() {
            SearchResult result = createFullResult("Test");

            assertEquals("()V", result.getMethodDescriptor());
        }

        @Test
        void getMethodDescriptorWithNullMethod() {
            SearchResult result = createClassResult("Test");

            assertNull(result.getMethodDescriptor());
        }

        @Test
        void getMethodDescriptorWithComplexSignature() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry complexMethod = classFile.createNewMethodWithDescriptor(access, "complex",
                "(ILjava/lang/String;)Ljava/util/List;");
            SearchResult result = new SearchResult(classFile, complexMethod, "Complex method");

            assertEquals("(ILjava/lang/String;)Ljava/util/List;", result.getMethodDescriptor());
        }
    }

    // ========== Location Formatting Tests ==========

    @Nested
    class LocationFormattingTests {

        @Test
        void getLocationWithFullInformation() {
            SearchResult result = createFullResult("Test");

            String location = result.getLocation();

            assertNotNull(location);
            assertTrue(location.contains("com/test/TestClass"));
            assertTrue(location.contains("testMethod"));
            assertTrue(location.contains("()V"));
            assertTrue(location.contains("42"));
            assertTrue(location.contains("@"));
        }

        @Test
        void getLocationWithClassAndMethod() {
            SearchResult result = createMethodResult("Test");

            String location = result.getLocation();

            assertNotNull(location);
            assertTrue(location.contains("com/test/TestClass"));
            assertTrue(location.contains("testMethod"));
            assertFalse(location.contains("@"));
        }

        @Test
        void getLocationWithClassOnly() {
            SearchResult result = createClassResult("Test");

            String location = result.getLocation();

            assertEquals("com/test/TestClass", location);
        }

        @Test
        void getLocationWithNullClassFile() {
            SearchResult result = new SearchResult(null, method, "Test");

            String location = result.getLocation();

            assertNotNull(location);
            assertTrue(location.contains("testMethod"));
        }

        @Test
        void getLocationWithNullMethod() {
            SearchResult result = new SearchResult(classFile, null, "Test");

            String location = result.getLocation();

            assertEquals("com/test/TestClass", location);
        }

        @Test
        void getLocationWithZeroOffset() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result = new SearchResult(classFile, method, instruction, 0, "Test");

            String location = result.getLocation();

            assertTrue(location.contains("@ 0"));
        }

        @Test
        void getLocationWithNegativeOffsetDoesNotIncludeAt() {
            SearchResult result = createMethodResult("Test");

            String location = result.getLocation();

            assertFalse(location.contains("@"));
        }

        @Test
        void getLocationWithAllNulls() {
            SearchResult result = new SearchResult(null, null, "Test");

            String location = result.getLocation();

            assertNotNull(location);
            assertEquals("", location);
        }

        @Test
        void getLocationWithConstructorMethod() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry constructor = classFile.createNewMethod(access, "<init>", "V");
            SearchResult result = new SearchResult(classFile, constructor, "Constructor");

            String location = result.getLocation();

            assertTrue(location.contains("<init>"));
        }

        @Test
        void getLocationWithStaticInitializer() throws IOException {
            int access = new AccessBuilder().setStatic().build();
            MethodEntry clinit = classFile.createNewMethod(access, "<clinit>", "V");
            SearchResult result = new SearchResult(classFile, clinit, "Static init");

            String location = result.getLocation();

            assertTrue(location.contains("<clinit>"));
        }
    }

    // ========== ToString Tests ==========

    @Nested
    class ToStringTests {

        @Test
        void toStringContainsLocationAndDescription() {
            SearchResult result = createFullResult("Found method call");

            String str = result.toString();

            assertNotNull(str);
            assertTrue(str.contains("com/test/TestClass"));
            assertTrue(str.contains("testMethod"));
            assertTrue(str.contains("Found method call"));
            assertTrue(str.contains(":"));
        }

        @Test
        void toStringWithClassResult() {
            SearchResult result = createClassResult("Class-level finding");

            String str = result.toString();

            assertTrue(str.contains("com/test/TestClass"));
            assertTrue(str.contains("Class-level finding"));
        }

        @Test
        void toStringWithMethodResult() {
            SearchResult result = createMethodResult("Method-level finding");

            String str = result.toString();

            assertTrue(str.contains("testMethod"));
            assertTrue(str.contains("Method-level finding"));
        }

        @Test
        void toStringWithNullDescription() {
            SearchResult result = new SearchResult(classFile, method, null);

            String str = result.toString();

            assertNotNull(str);
            assertTrue(str.contains("null"));
        }

        @Test
        void toStringWithEmptyDescription() {
            SearchResult result = new SearchResult(classFile, method, "");

            String str = result.toString();

            assertNotNull(str);
            assertTrue(str.contains(":"));
        }

        @Test
        void toStringFormatMatchesExpectedPattern() {
            SearchResult result = createFullResult("Test description");

            String str = result.toString();

            // Format should be: location: description
            assertTrue(str.matches(".*:.*"));
        }
    }

    // ========== Equality Tests ==========

    @Nested
    class EqualityTests {

        @Test
        void equalsSameInstance() {
            SearchResult result = createFullResult("Test");

            assertEquals(result, result);
        }

        @Test
        void equalsIdenticalResults() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result1 = new SearchResult(classFile, method, instruction, 42, "Test");
            SearchResult result2 = new SearchResult(classFile, method, instruction, 42, "Test");

            assertEquals(result1, result2);
        }

        @Test
        void equalsWithDifferentDescriptions() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result1 = new SearchResult(classFile, method, instruction, 42, "Description 1");
            SearchResult result2 = new SearchResult(classFile, method, instruction, 42, "Description 2");

            // Description is not part of equality
            assertEquals(result1, result2);
        }

        @Test
        void equalsWithNullDescriptions() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result1 = new SearchResult(classFile, method, instruction, 42, null);
            SearchResult result2 = new SearchResult(classFile, method, instruction, 42, null);

            assertEquals(result1, result2);
        }

        @Test
        void notEqualsDifferentClassFile() throws IOException {
            ClassFile otherClassFile = pool.createNewClass("com/test/OtherClass",
                new AccessBuilder().setPublic().build());
            SearchResult result1 = new SearchResult(classFile, method, "Test");
            SearchResult result2 = new SearchResult(otherClassFile, method, "Test");

            assertNotEquals(result1, result2);
        }

        @Test
        void notEqualsDifferentMethod() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry otherMethod = classFile.createNewMethod(access, "otherMethod", "V");
            SearchResult result1 = new SearchResult(classFile, method, "Test");
            SearchResult result2 = new SearchResult(classFile, otherMethod, "Test");

            assertNotEquals(result1, result2);
        }

        @Test
        void notEqualsDifferentInstruction() {
            IRInstruction instruction1 = createMockInstruction();
            IRInstruction instruction2 = createMockInstruction();
            SearchResult result1 = new SearchResult(classFile, method, instruction1, 42, "Test");
            SearchResult result2 = new SearchResult(classFile, method, instruction2, 42, "Test");

            // Different instruction instances should not be equal
            assertNotEquals(result1, result2);
        }

        @Test
        void notEqualsDifferentOffset() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result1 = new SearchResult(classFile, method, instruction, 42, "Test");
            SearchResult result2 = new SearchResult(classFile, method, instruction, 50, "Test");

            assertNotEquals(result1, result2);
        }

        @Test
        void notEqualsNull() {
            SearchResult result = createFullResult("Test");

            assertNotEquals(null, result);
        }

        @Test
        void notEqualsDifferentType() {
            SearchResult result = createFullResult("Test");
            String notASearchResult = "Not a SearchResult";

            assertNotEquals(result, notASearchResult);
        }

        @Test
        void equalsWithNullClassFiles() {
            SearchResult result1 = new SearchResult(null, method, "Test");
            SearchResult result2 = new SearchResult(null, method, "Test");

            assertEquals(result1, result2);
        }

        @Test
        void equalsWithNullMethods() {
            SearchResult result1 = new SearchResult(classFile, null, "Test");
            SearchResult result2 = new SearchResult(classFile, null, "Test");

            assertEquals(result1, result2);
        }

        @Test
        void equalsWithNullInstructions() {
            SearchResult result1 = new SearchResult(classFile, method, null, 10, "Test");
            SearchResult result2 = new SearchResult(classFile, method, null, 10, "Test");

            assertEquals(result1, result2);
        }

        @Test
        void notEqualsOneNullClassFile() {
            SearchResult result1 = new SearchResult(classFile, method, "Test");
            SearchResult result2 = new SearchResult(null, method, "Test");

            assertNotEquals(result1, result2);
        }

        @Test
        void notEqualsOneNullMethod() {
            SearchResult result1 = new SearchResult(classFile, method, "Test");
            SearchResult result2 = new SearchResult(classFile, null, "Test");

            assertNotEquals(result1, result2);
        }

        @Test
        void notEqualsOneNullInstruction() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result1 = new SearchResult(classFile, method, instruction, 10, "Test");
            SearchResult result2 = new SearchResult(classFile, method, null, 10, "Test");

            assertNotEquals(result1, result2);
        }
    }

    // ========== HashCode Tests ==========

    @Nested
    class HashCodeTests {

        @Test
        void hashCodeConsistentWithEquals() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result1 = new SearchResult(classFile, method, instruction, 42, "Test");
            SearchResult result2 = new SearchResult(classFile, method, instruction, 42, "Different description");

            assertEquals(result1, result2);
            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        void hashCodeStableAcrossMultipleCalls() {
            SearchResult result = createFullResult("Test");

            int hash1 = result.hashCode();
            int hash2 = result.hashCode();

            assertEquals(hash1, hash2);
        }

        @Test
        void hashCodeDifferentForDifferentOffsets() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result1 = new SearchResult(classFile, method, instruction, 10, "Test");
            SearchResult result2 = new SearchResult(classFile, method, instruction, 20, "Test");

            assertNotEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        void hashCodeWithNullClassFile() {
            SearchResult result1 = new SearchResult(null, method, "Test");
            SearchResult result2 = new SearchResult(null, method, "Test");

            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        void hashCodeWithNullMethod() {
            SearchResult result1 = new SearchResult(classFile, null, "Test");
            SearchResult result2 = new SearchResult(classFile, null, "Test");

            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        void hashCodeWithNullInstruction() {
            SearchResult result1 = new SearchResult(classFile, method, null, 10, "Test");
            SearchResult result2 = new SearchResult(classFile, method, null, 10, "Test");

            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        void hashCodeWithAllNulls() {
            SearchResult result1 = new SearchResult(null, null, null, -1, "Test");
            SearchResult result2 = new SearchResult(null, null, null, -1, "Different");

            assertEquals(result1.hashCode(), result2.hashCode());
        }
    }

    // ========== Edge Cases and Complex Scenarios ==========

    @Nested
    class EdgeCasesAndComplexScenarios {

        @Test
        void multipleResultsFromSameClass() {
            SearchResult result1 = createMethodResult("Finding 1");
            SearchResult result2 = createMethodResult("Finding 2");

            // Same parameters - descriptions don't affect equality, only location does
            assertEquals(result1, result2);
        }

        @Test
        void resultWithInnerClass() throws IOException {
            ClassFile innerClass = pool.createNewClass("com/test/Outer$Inner",
                new AccessBuilder().setPublic().build());
            SearchResult result = new SearchResult(innerClass, "Inner class finding");

            assertTrue(result.getClassName().contains("$"));
            assertEquals("com/test/Outer$Inner", result.getClassName());
        }

        @Test
        void resultWithAnonymousClass() throws IOException {
            ClassFile anonymousClass = pool.createNewClass("com/test/MyClass$1",
                new AccessBuilder().setPublic().build());
            SearchResult result = new SearchResult(anonymousClass, "Anonymous class finding");

            assertTrue(result.getClassName().contains("$1"));
            assertEquals("com/test/MyClass$1", result.getClassName());
        }

        @Test
        void resultWithLambdaGeneratedClass() throws IOException {
            ClassFile lambdaClass = pool.createNewClass("com/test/Lambda$$Lambda$1",
                new AccessBuilder().setPublic().build());
            SearchResult result = new SearchResult(lambdaClass, "Lambda finding");

            assertTrue(result.getClassName().contains("Lambda"));
        }

        @Test
        void resultWithComplexPackageStructure() throws IOException {
            ClassFile deepClass = pool.createNewClass("com/example/project/module/submodule/DeepClass",
                new AccessBuilder().setPublic().build());
            SearchResult result = new SearchResult(deepClass, "Deep package finding");

            assertEquals("com/example/project/module/submodule/DeepClass", result.getClassName());
            assertTrue(result.getLocation().contains("com/example/project/module/submodule/DeepClass"));
        }

        @Test
        void resultWithOverloadedMethods() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry method1 = classFile.createNewMethod(access, "process", "V");
            MethodEntry method2 = classFile.createNewMethod(access, "process", "(I)V");
            MethodEntry method3 = classFile.createNewMethod(access, "process", "(Ljava/lang/String;)V");

            SearchResult result1 = new SearchResult(classFile, method1, "Overload 1");
            SearchResult result2 = new SearchResult(classFile, method2, "Overload 2");
            SearchResult result3 = new SearchResult(classFile, method3, "Overload 3");

            assertNotEquals(result1, result2);
            assertNotEquals(result2, result3);
            assertNotEquals(result1, result3);

            assertTrue(result1.getLocation().contains("()V"));
            assertTrue(result2.getLocation().contains("(I)V"));
            assertTrue(result3.getLocation().contains("(Ljava/lang/String;)V"));
        }

        @Test
        void resultIntegrityAfterConstruction() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result = new SearchResult(classFile, method, instruction, 100, "Integrity test");

            // Verify all properties remain consistent
            assertEquals(classFile, result.getClassFile());
            assertEquals(method, result.getMethod());
            assertEquals(instruction, result.getInstruction());
            assertEquals(100, result.getBytecodeOffset());
            assertEquals("Integrity test", result.getDescription());
            assertEquals("com/test/TestClass", result.getClassName());
            assertEquals("testMethod", result.getMethodName());
            assertEquals("()V", result.getMethodDescriptor());
        }

        @Test
        void multipleResultsAtDifferentOffsetsInSameMethod() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result1 = new SearchResult(classFile, method, instruction, 10, "First call");
            SearchResult result2 = new SearchResult(classFile, method, instruction, 20, "Second call");
            SearchResult result3 = new SearchResult(classFile, method, instruction, 30, "Third call");

            assertNotEquals(result1, result2);
            assertNotEquals(result2, result3);
            assertEquals(method, result1.getMethod());
            assertEquals(method, result2.getMethod());
            assertEquals(method, result3.getMethod());
        }

        @Test
        void resultWithVeryLongDescription() {
            String longDescription = "This is a very long description that contains a lot of information " +
                "about the search result and what was found during the pattern matching process. " +
                "It may include details about the specific code pattern, the context in which it was found, " +
                "and why it matches the search criteria. This tests handling of lengthy descriptive text.";

            SearchResult result = new SearchResult(classFile, method, longDescription);

            assertEquals(longDescription, result.getDescription());
            assertTrue(result.toString().contains(longDescription));
        }

        @Test
        void resultWithSpecialCharactersInDescription() {
            String specialDesc = "Found: <init>(){} @ line: 42 [special chars: $#@!%]";
            SearchResult result = new SearchResult(classFile, method, specialDesc);

            assertEquals(specialDesc, result.getDescription());
            assertTrue(result.toString().contains(specialDesc));
        }

        @Test
        void resultWithUnicodeInDescription() {
            String unicodeDesc = "Found method call \u4E2D\u6587 (Chinese) \u65E5\u672C\u8A9E (Japanese)";
            SearchResult result = new SearchResult(classFile, method, unicodeDesc);

            assertEquals(unicodeDesc, result.getDescription());
        }

        @Test
        void resultComparisonSymmetry() {
            SearchResult result1 = createMethodResult("Test");
            SearchResult result2 = createMethodResult("Test");

            assertEquals(result1, result2);
            assertEquals(result2, result1);
        }

        @Test
        void resultComparisonTransitivity() {
            SearchResult result1 = createMethodResult("Test");
            SearchResult result2 = createMethodResult("Test");
            SearchResult result3 = createMethodResult("Test");

            assertEquals(result1, result2);
            assertEquals(result2, result3);
            assertEquals(result1, result3);
        }

        @Test
        void resultWithMaxIntOffset() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result = new SearchResult(classFile, method, instruction, Integer.MAX_VALUE, "Max offset");

            assertEquals(Integer.MAX_VALUE, result.getBytecodeOffset());
            assertTrue(result.getLocation().contains(String.valueOf(Integer.MAX_VALUE)));
        }

        @Test
        void resultWithMinIntOffset() {
            IRInstruction instruction = createMockInstruction();
            SearchResult result = new SearchResult(classFile, method, instruction, Integer.MIN_VALUE, "Min offset");

            assertEquals(Integer.MIN_VALUE, result.getBytecodeOffset());
        }
    }
}
