package com.tonic.analysis.instrumentation.filter;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for instrumentation filters.
 *
 * Tests ClassFilter, MethodFilter, PackageFilter, FieldFilter, and AnnotationFilter.
 */
class InstrumentationFilterTest {

    // ========== ClassFilter Tests ==========

    @Nested
    class ClassFilterTests {

        @Test
        void exactClassMatch() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/example/MyClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            ClassFilter filter = ClassFilter.exact("com/example/MyClass");
            assertTrue(filter.matchesClass(cf));
        }

        @Test
        void exactClassNoMatch() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/example/OtherClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            ClassFilter filter = ClassFilter.exact("com/example/MyClass");
            assertFalse(filter.matchesClass(cf));
        }

        @Test
        void wildcardSingleLevel() throws IOException {
            ClassFile cf1 = BytecodeBuilder.forClass("com/example/MyClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();
            ClassFile cf2 = BytecodeBuilder.forClass("com/example/OtherClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();
            ClassFile cf3 = BytecodeBuilder.forClass("com/example/sub/Nested")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            ClassFilter filter = ClassFilter.matching("com/example/*");

            assertTrue(filter.matchesClass(cf1));
            assertTrue(filter.matchesClass(cf2));
            assertFalse(filter.matchesClass(cf3)); // subpackage doesn't match single *
        }

        @Test
        void wildcardDoubleLevel() throws IOException {
            ClassFile cf1 = BytecodeBuilder.forClass("com/example/MyClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();
            ClassFile cf2 = BytecodeBuilder.forClass("com/example/sub/Nested")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();
            ClassFile cf3 = BytecodeBuilder.forClass("org/other/Class")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            ClassFilter filter = ClassFilter.matching("com/example/**");

            assertTrue(filter.matchesClass(cf1));
            assertTrue(filter.matchesClass(cf2));
            assertFalse(filter.matchesClass(cf3));
        }

        @Test
        void dotNotationConvertedToSlash() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/example/MyClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            ClassFilter filter = new ClassFilter("com.example.MyClass");
            assertTrue(filter.matchesClass(cf));
        }

        @Test
        void isWildcardFlag() {
            ClassFilter exact = ClassFilter.exact("com/example/MyClass");
            ClassFilter wildcard = ClassFilter.matching("com/example/*");

            assertFalse(exact.isWildcard());
            assertTrue(wildcard.isWildcard());
        }

        @Test
        void filterToString() {
            ClassFilter filter = ClassFilter.exact("com/example/MyClass");
            String str = filter.toString();
            assertTrue(str.contains("ClassFilter"));
            assertTrue(str.contains("com/example/MyClass"));
        }

        @Test
        void matchesMethodDefaultsToTrue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/example/MyClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            ClassFilter filter = ClassFilter.exact("com/example/MyClass");
            MethodEntry method = cf.getMethods().get(0);

            // ClassFilter doesn't filter methods by default
            assertTrue(filter.matchesMethod(method));
        }

        @Test
        void matchesFieldDefaultsToTrue() {
            ClassFilter filter = ClassFilter.exact("com/example/MyClass");
            assertTrue(filter.matchesField("owner", "name", "I"));
        }
    }

    // ========== MethodFilter Tests ==========

    @Nested
    class MethodFilterTests {

        @Test
        void exactNameMatch() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Methods")
                .publicStaticMethod("process", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "process");
            MethodFilter filter = MethodFilter.named("process");

            assertTrue(filter.matchesMethod(method));
        }

        @Test
        void exactNameNoMatch() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Methods")
                .publicStaticMethod("process", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "process");
            MethodFilter filter = MethodFilter.named("handle");

            assertFalse(filter.matchesMethod(method));
        }

        @Test
        void startingWithPattern() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Methods")
                .publicStaticMethod("getData", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "getData");

            assertTrue(MethodFilter.startingWith("get").matchesMethod(method));
            assertFalse(MethodFilter.startingWith("set").matchesMethod(method));
        }

        @Test
        void endingWithPattern() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Methods")
                .publicStaticMethod("processData", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "processData");

            assertTrue(MethodFilter.endingWith("Data").matchesMethod(method));
            assertFalse(MethodFilter.endingWith("Info").matchesMethod(method));
        }

        @Test
        void containingPattern() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Methods")
                .publicStaticMethod("handleUserData", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "handleUserData");

            assertTrue(MethodFilter.containing("User").matchesMethod(method));
            assertFalse(MethodFilter.containing("Admin").matchesMethod(method));
        }

        @Test
        void descriptorMatching() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Methods")
                .publicStaticMethod("add", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "add");

            assertTrue(MethodFilter.signature("add", "(II)I").matchesMethod(method));
            assertFalse(MethodFilter.signature("add", "(I)I").matchesMethod(method));
        }

        @Test
        void descriptorWildcard() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Methods")
                .publicStaticMethod("getValue", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "getValue");
            MethodFilter filter = new MethodFilter("getValue", "(*)I");

            assertTrue(filter.matchesMethod(method));
        }

        @Test
        void wildcardPattern() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Methods")
                .publicStaticMethod("getUser", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "getUser");
            MethodFilter filter = MethodFilter.matching("get*");

            assertTrue(filter.matchesMethod(method));
            assertTrue(filter.isWildcard());
        }

        @Test
        void filterToString() {
            MethodFilter nameOnly = MethodFilter.named("process");
            MethodFilter withDesc = MethodFilter.signature("add", "(II)I");

            String str1 = nameOnly.toString();
            String str2 = withDesc.toString();

            assertTrue(str1.contains("MethodFilter"));
            assertTrue(str1.contains("process"));
            assertTrue(str2.contains("(II)I"));
        }

        @Test
        void matchesClassDefaultsToTrue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Methods")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodFilter filter = MethodFilter.named("test");
            assertTrue(filter.matchesClass(cf));
        }
    }

    // ========== PackageFilter Tests ==========

    @Nested
    class PackageFilterTests {

        @Test
        void packagePrefixMatch() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/example/MyClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            PackageFilter filter = PackageFilter.of("com/example");
            assertTrue(filter.matchesClass(cf));
        }

        @Test
        void subpackageIncluded() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/example/sub/Nested")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            PackageFilter filter = PackageFilter.forPackage("com/example");
            assertTrue(filter.matchesClass(cf));
        }

        @Test
        void subpackageExcluded() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/example/sub/Nested")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            PackageFilter filter = PackageFilter.exactPackage("com/example");
            assertFalse(filter.matchesClass(cf));
        }

        @Test
        void exactPackageMatch() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/example/MyClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            PackageFilter filter = PackageFilter.exactPackage("com/example");
            assertTrue(filter.matchesClass(cf));
        }

        @Test
        void differentPackageNoMatch() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("org/other/OtherClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            PackageFilter filter = PackageFilter.of("com/example");
            assertFalse(filter.matchesClass(cf));
        }

        @Test
        void dotNotationNormalized() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/example/MyClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            PackageFilter filter = new PackageFilter("com.example");
            assertTrue(filter.matchesClass(cf));
        }

        @Test
        void trailingSlashNormalized() {
            PackageFilter filter1 = new PackageFilter("com/example/");
            PackageFilter filter2 = new PackageFilter("com/example");

            assertEquals(filter1.getPackagePrefix(), filter2.getPackagePrefix());
        }

        @Test
        void filterToString() {
            PackageFilter with = PackageFilter.of("com/example");
            PackageFilter without = PackageFilter.exactPackage("com/example");

            String str1 = with.toString();
            String str2 = without.toString();

            assertTrue(str1.contains("PackageFilter"));
            assertTrue(str1.contains("**")); // includes subpackages
            assertFalse(str2.contains("**"));
        }

        @Test
        void includeSubpackagesFlag() {
            PackageFilter with = PackageFilter.of("com/example");
            PackageFilter without = PackageFilter.exactPackage("com/example");

            assertTrue(with.isIncludeSubpackages());
            assertFalse(without.isIncludeSubpackages());
        }
    }

    // ========== FieldFilter Tests ==========

    @Nested
    class FieldFilterTests {

        @Test
        void exactNameMatch() {
            FieldFilter filter = FieldFilter.named("count");

            assertTrue(filter.matchesField("com/test/Test", "count", "I"));
            assertFalse(filter.matchesField("com/test/Test", "size", "I"));
        }

        @Test
        void wildcardNameMatch() {
            FieldFilter filter = FieldFilter.matching("m_*");

            assertTrue(filter.matchesField("any", "m_value", "I"));
            assertTrue(filter.matchesField("any", "m_count", "I"));
            assertFalse(filter.matchesField("any", "value", "I"));
        }

        @Test
        void typeFilter() {
            FieldFilter filter = FieldFilter.ofType("Ljava/lang/String;");

            assertTrue(filter.matchesField("any", "any", "Ljava/lang/String;"));
            assertFalse(filter.matchesField("any", "any", "I"));
        }

        @Test
        void classFilter() {
            FieldFilter filter = FieldFilter.inClass("com/example/MyClass");

            assertTrue(filter.matchesField("com/example/MyClass", "field1", "I"));
            assertTrue(filter.matchesField("com/example/MyClass", "field2", "J"));
            assertFalse(filter.matchesField("com/other/Other", "field1", "I"));
        }

        @Test
        void specificFieldFilter() {
            FieldFilter filter = FieldFilter.specific("com/example/Config", "timeout");

            assertTrue(filter.matchesField("com/example/Config", "timeout", "J"));
            assertFalse(filter.matchesField("com/example/Config", "retries", "I"));
            assertFalse(filter.matchesField("com/other/Config", "timeout", "J"));
        }

        @Test
        void combinedPatterns() {
            FieldFilter filter = new FieldFilter("com/example/*", "m_*", "L*;");

            assertTrue(filter.matchesField("com/example/Test", "m_name", "Ljava/lang/String;"));
            assertFalse(filter.matchesField("com/other/Test", "m_name", "Ljava/lang/String;"));
            assertFalse(filter.matchesField("com/example/Test", "value", "Ljava/lang/String;"));
            assertFalse(filter.matchesField("com/example/Test", "m_count", "I"));
        }

        @Test
        void nullPatternMatchesAll() {
            // namePattern only, owner and type can be anything
            FieldFilter filter = FieldFilter.named("data");

            assertTrue(filter.matchesField("any/Owner", "data", "I"));
            assertTrue(filter.matchesField("other/Owner", "data", "Ljava/lang/String;"));
        }

        @Test
        void filterToString() {
            FieldFilter filter = FieldFilter.specific("com/example/Test", "field");
            String str = filter.toString();

            assertTrue(str.contains("FieldFilter"));
            assertTrue(str.contains("owner"));
            assertTrue(str.contains("name"));
        }

        @Test
        void forFieldAlias() {
            FieldFilter f1 = FieldFilter.named("test");
            FieldFilter f2 = FieldFilter.forField("test");

            // Both should match the same pattern
            assertTrue(f1.matchesField("any", "test", "I"));
            assertTrue(f2.matchesField("any", "test", "I"));
        }
    }

    // ========== AnnotationFilter Tests ==========

    @Nested
    class AnnotationFilterTests {

        @Test
        void filterCreation() {
            AnnotationFilter filter = AnnotationFilter.forAnnotation("Ljavax/inject/Inject;");

            assertEquals("Ljavax/inject/Inject;", filter.getAnnotationType());
            assertTrue(filter.isMatchPresent());
            assertTrue(filter.isCheckClass());
            assertTrue(filter.isCheckMethod());
        }

        @Test
        void filterWithoutAnnotation() {
            AnnotationFilter filter = AnnotationFilter.withoutAnnotation("LDeprecated;");

            assertEquals("LDeprecated;", filter.getAnnotationType());
            assertFalse(filter.isMatchPresent());
        }

        @Test
        void builderPattern() {
            AnnotationFilter filter = AnnotationFilter.builder()
                .annotationType("Ljavax/inject/Inject;")
                .matchPresent(true)
                .checkClass(false)
                .checkMethod(true)
                .build();

            assertFalse(filter.isCheckClass());
            assertTrue(filter.isCheckMethod());
        }

        @Test
        void matchesClassWithoutAnnotation() throws IOException {
            // Class without annotations
            ClassFile cf = BytecodeBuilder.forClass("com/test/NoAnnot")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            AnnotationFilter presentFilter = AnnotationFilter.forAnnotation("LTest;");
            AnnotationFilter absentFilter = AnnotationFilter.withoutAnnotation("LTest;");

            // No annotation means: present filter returns false, absent filter returns true
            assertFalse(presentFilter.matchesClass(cf));
            assertTrue(absentFilter.matchesClass(cf));
        }

        @Test
        void matchesMethodWithoutAnnotation() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NoAnnot")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");

            AnnotationFilter presentFilter = AnnotationFilter.forAnnotation("LTest;");
            AnnotationFilter absentFilter = AnnotationFilter.withoutAnnotation("LTest;");

            assertFalse(presentFilter.matchesMethod(method));
            assertTrue(absentFilter.matchesMethod(method));
        }

        @Test
        void matchesFieldAlwaysTrue() {
            AnnotationFilter filter = AnnotationFilter.forAnnotation("LTest;");

            // Field annotation checking not implemented, always returns true
            assertTrue(filter.matchesField("any", "any", "I"));
        }

        @Test
        void matchesMethodCallAlwaysTrue() {
            AnnotationFilter filter = AnnotationFilter.forAnnotation("LTest;");

            // Method call annotation checking not implemented, always returns true
            assertTrue(filter.matchesMethodCall("any", "any", "()V"));
        }

        @Test
        void checkClassDisabled() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NoAnnot")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            AnnotationFilter filter = AnnotationFilter.builder()
                .annotationType("LTest;")
                .matchPresent(true)
                .checkClass(false)
                .build();

            // When checkClass is false, matchesClass always returns true
            assertTrue(filter.matchesClass(cf));
        }

        @Test
        void checkMethodDisabled() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NoAnnot")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "test");

            AnnotationFilter filter = AnnotationFilter.builder()
                .annotationType("LTest;")
                .matchPresent(true)
                .checkMethod(false)
                .build();

            // When checkMethod is false, matchesMethod always returns true
            assertTrue(filter.matchesMethod(method));
        }
    }

    // ========== InstrumentationFilter Interface Tests ==========

    @Nested
    class InstrumentationFilterInterfaceTests {

        @Test
        void defaultMatchesClassReturnsTrue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            // Anonymous filter with all defaults
            InstrumentationFilter filter = new InstrumentationFilter() {};

            assertTrue(filter.matchesClass(cf));
        }

        @Test
        void defaultMatchesMethodReturnsTrue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            InstrumentationFilter filter = new InstrumentationFilter() {};
            assertTrue(filter.matchesMethod(cf.getMethods().get(0)));
        }

        @Test
        void defaultMatchesFieldReturnsTrue() {
            InstrumentationFilter filter = new InstrumentationFilter() {};
            assertTrue(filter.matchesField("owner", "name", "I"));
        }

        @Test
        void defaultMatchesMethodCallReturnsTrue() {
            InstrumentationFilter filter = new InstrumentationFilter() {};
            assertTrue(filter.matchesMethodCall("owner", "name", "()V"));
        }
    }

    // ========== Filter Composition Tests ==========

    @Nested
    class FilterCompositionTests {

        @Test
        void multipleFiltersAndLogic() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/example/ServiceImpl")
                .publicStaticMethod("getData", "()V")
                    .vreturn()
                .build();

            // Combine filters manually (AND logic)
            ClassFilter classFilter = ClassFilter.matching("com/example/*");
            MethodFilter methodFilter = MethodFilter.startingWith("get");

            MethodEntry method = findMethod(cf, "getData");

            boolean matchesBoth = classFilter.matchesClass(cf) && methodFilter.matchesMethod(method);
            assertTrue(matchesBoth);
        }

        @Test
        void differentPackagesOrLogic() throws IOException {
            ClassFile cf1 = BytecodeBuilder.forClass("com/api/Handler")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();
            ClassFile cf2 = BytecodeBuilder.forClass("com/internal/Processor")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();
            ClassFile cf3 = BytecodeBuilder.forClass("org/other/Thing")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            // OR logic: match com/api OR com/internal
            PackageFilter filter1 = PackageFilter.of("com/api");
            PackageFilter filter2 = PackageFilter.of("com/internal");

            assertTrue(filter1.matchesClass(cf1) || filter2.matchesClass(cf1));
            assertTrue(filter1.matchesClass(cf2) || filter2.matchesClass(cf2));
            assertFalse(filter1.matchesClass(cf3) || filter2.matchesClass(cf3));
        }
    }

    // ========== Edge Cases ==========

    @Nested
    class EdgeCaseTests {

        @Test
        void emptyClassName() throws IOException {
            // Edge case: class name with no package
            ClassFile cf = BytecodeBuilder.forClass("SimpleClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            ClassFilter filter = ClassFilter.exact("SimpleClass");
            assertTrue(filter.matchesClass(cf));
        }

        @Test
        void specialMethodNames() throws IOException {
            // Test underscore prefix pattern - common in generated code
            ClassFile cf = BytecodeBuilder.forClass("com/test/Special")
                .publicStaticMethod("_generated_0", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "_generated_0");
            MethodFilter filter = MethodFilter.matching("_generated_*");

            assertTrue(filter.matchesMethod(method));
        }

        @Test
        void deeplyNestedPackage() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/example/very/deep/nested/pkg/MyClass")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            PackageFilter filter = PackageFilter.of("com/example");
            assertTrue(filter.matchesClass(cf));
        }

        @Test
        void initMethodFilter() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Init")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            // <init> is constructor
            MethodEntry init = cf.getMethods().get(0);
            assertEquals("<init>", init.getName());

            MethodFilter filter = MethodFilter.named("<init>");
            assertTrue(filter.matchesMethod(init));
        }
    }

    // ========== Helper Methods ==========

    private MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Method not found: " + name);
    }
}
