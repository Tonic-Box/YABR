package com.tonic.utill;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassNameUtilTest {

    @Nested
    class SimpleNameTests {

        @Test
        void getSimpleNameSimpleClass() {
            assertEquals("Bar", ClassNameUtil.getSimpleName("com/foo/Bar"));
        }

        @Test
        void getSimpleNameInnerClass() {
            assertEquals("Inner", ClassNameUtil.getSimpleName("com/foo/Bar$Inner"));
        }

        @Test
        void getSimpleNameNestedInnerClass() {
            assertEquals("DeepInner", ClassNameUtil.getSimpleName("com/foo/Bar$Inner$DeepInner"));
        }

        @Test
        void getSimpleNameNoPackage() {
            assertEquals("SimpleClass", ClassNameUtil.getSimpleName("SimpleClass"));
        }

        @Test
        void getSimpleNameNull() {
            assertNull(ClassNameUtil.getSimpleName(null));
        }

        @Test
        void getSimpleNameEmpty() {
            assertEquals("", ClassNameUtil.getSimpleName(""));
        }

        @Test
        void getSimpleNameOnlySlash() {
            assertEquals("", ClassNameUtil.getSimpleName("com/"));
        }

        @Test
        void getSimpleNameOnlyDollar() {
            assertEquals("", ClassNameUtil.getSimpleName("Outer$"));
        }

        @Test
        void getSimpleNameTrailingSlash() {
            assertEquals("", ClassNameUtil.getSimpleName("com/example/"));
        }
    }

    @Nested
    class SimpleNameWithInnerClassesTests {

        @Test
        void getSimpleNameWithInnerClassesNormal() {
            assertEquals("Bar", ClassNameUtil.getSimpleNameWithInnerClasses("com/foo/Bar"));
        }

        @Test
        void getSimpleNameWithInnerClassesInner() {
            assertEquals("Bar$Inner", ClassNameUtil.getSimpleNameWithInnerClasses("com/foo/Bar$Inner"));
        }

        @Test
        void getSimpleNameWithInnerClassesNested() {
            assertEquals("Bar$Inner$DeepInner",
                ClassNameUtil.getSimpleNameWithInnerClasses("com/foo/Bar$Inner$DeepInner"));
        }

        @Test
        void getSimpleNameWithInnerClassesNoPackage() {
            assertEquals("SimpleClass", ClassNameUtil.getSimpleNameWithInnerClasses("SimpleClass"));
        }

        @Test
        void getSimpleNameWithInnerClassesNull() {
            assertNull(ClassNameUtil.getSimpleNameWithInnerClasses(null));
        }

        @Test
        void getSimpleNameWithInnerClassesEmpty() {
            assertEquals("", ClassNameUtil.getSimpleNameWithInnerClasses(""));
        }
    }

    @Nested
    class PackageNameTests {

        @Test
        void getPackageNameNormal() {
            assertEquals("com/foo", ClassNameUtil.getPackageName("com/foo/Bar"));
        }

        @Test
        void getPackageNameDeepPackage() {
            assertEquals("com/example/deep/package", ClassNameUtil.getPackageName("com/example/deep/package/Class"));
        }

        @Test
        void getPackageNameNoPackage() {
            assertEquals("", ClassNameUtil.getPackageName("SimpleClass"));
        }

        @Test
        void getPackageNameInnerClass() {
            assertEquals("com/foo", ClassNameUtil.getPackageName("com/foo/Bar$Inner"));
        }

        @Test
        void getPackageNameNull() {
            assertEquals("", ClassNameUtil.getPackageName(null));
        }

        @Test
        void getPackageNameEmpty() {
            assertEquals("", ClassNameUtil.getPackageName(""));
        }

        @Test
        void getPackageNameSingleLevel() {
            assertEquals("com", ClassNameUtil.getPackageName("com/Class"));
        }
    }

    @Nested
    class PackageNameAsSourceTests {

        @Test
        void getPackageNameAsSourceNormal() {
            assertEquals("com.foo", ClassNameUtil.getPackageNameAsSource("com/foo/Bar"));
        }

        @Test
        void getPackageNameAsSourceDeep() {
            assertEquals("com.example.deep.package",
                ClassNameUtil.getPackageNameAsSource("com/example/deep/package/Class"));
        }

        @Test
        void getPackageNameAsSourceNoPackage() {
            assertEquals("", ClassNameUtil.getPackageNameAsSource("SimpleClass"));
        }

        @Test
        void getPackageNameAsSourceNull() {
            assertEquals("", ClassNameUtil.getPackageNameAsSource(null));
        }

        @Test
        void getPackageNameAsSourceEmpty() {
            assertEquals("", ClassNameUtil.getPackageNameAsSource(""));
        }
    }

    @Nested
    class NameConversionTests {

        @Test
        void toSourceNameNormal() {
            assertEquals("com.foo.Bar", ClassNameUtil.toSourceName("com/foo/Bar"));
        }

        @Test
        void toSourceNameInnerClass() {
            assertEquals("com.foo.Bar$Inner", ClassNameUtil.toSourceName("com/foo/Bar$Inner"));
        }

        @Test
        void toSourceNameNoPackage() {
            assertEquals("SimpleClass", ClassNameUtil.toSourceName("SimpleClass"));
        }

        @Test
        void toSourceNameNull() {
            assertNull(ClassNameUtil.toSourceName(null));
        }

        @Test
        void toSourceNameAlreadySource() {
            assertEquals("com.foo.Bar", ClassNameUtil.toSourceName("com.foo.Bar"));
        }

        @Test
        void toInternalNameNormal() {
            assertEquals("com/foo/Bar", ClassNameUtil.toInternalName("com.foo.Bar"));
        }

        @Test
        void toInternalNameInnerClass() {
            assertEquals("com/foo/Bar$Inner", ClassNameUtil.toInternalName("com.foo.Bar$Inner"));
        }

        @Test
        void toInternalNameNoPackage() {
            assertEquals("SimpleClass", ClassNameUtil.toInternalName("SimpleClass"));
        }

        @Test
        void toInternalNameNull() {
            assertNull(ClassNameUtil.toInternalName(null));
        }

        @Test
        void toInternalNameAlreadyInternal() {
            assertEquals("com/foo/Bar", ClassNameUtil.toInternalName("com/foo/Bar"));
        }
    }

    @Nested
    class InnerClassTests {

        @Test
        void isInnerClassTrue() {
            assertTrue(ClassNameUtil.isInnerClass("com/foo/Bar$Inner"));
        }

        @Test
        void isInnerClassFalse() {
            assertFalse(ClassNameUtil.isInnerClass("com/foo/Bar"));
        }

        @Test
        void isInnerClassNull() {
            assertFalse(ClassNameUtil.isInnerClass(null));
        }

        @Test
        void isInnerClassEmpty() {
            assertFalse(ClassNameUtil.isInnerClass(""));
        }

        @Test
        void isInnerClassNested() {
            assertTrue(ClassNameUtil.isInnerClass("com/foo/Bar$Inner$DeepInner"));
        }
    }

    @Nested
    class OuterClassNameTests {

        @Test
        void getOuterClassNameInnerClass() {
            assertEquals("com/foo/Bar", ClassNameUtil.getOuterClassName("com/foo/Bar$Inner"));
        }

        @Test
        void getOuterClassNameNestedInner() {
            assertEquals("com/foo/Bar", ClassNameUtil.getOuterClassName("com/foo/Bar$Inner$DeepInner"));
        }

        @Test
        void getOuterClassNameNotInner() {
            assertEquals("com/foo/Bar", ClassNameUtil.getOuterClassName("com/foo/Bar"));
        }

        @Test
        void getOuterClassNameNull() {
            assertNull(ClassNameUtil.getOuterClassName(null));
        }

        @Test
        void getOuterClassNameSimple() {
            assertEquals("SimpleClass", ClassNameUtil.getOuterClassName("SimpleClass"));
        }

        @Test
        void getOuterClassNameOnlyDollar() {
            assertEquals("", ClassNameUtil.getOuterClassName("$"));
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void handleClassNameWithNumbers() {
            assertEquals("Test123", ClassNameUtil.getSimpleName("com/example/Test123"));
        }

        @Test
        void handleClassNameWithUnderscore() {
            assertEquals("My_Class", ClassNameUtil.getSimpleName("com/example/My_Class"));
        }

        @Test
        void handleAnonymousInnerClass() {
            assertEquals("1", ClassNameUtil.getSimpleName("com/foo/Bar$1"));
        }

        @Test
        void handleAnonymousInnerClassOuter() {
            assertEquals("com/foo/Bar", ClassNameUtil.getOuterClassName("com/foo/Bar$1"));
        }

        @Test
        void handleLocalInnerClass() {
            assertEquals("1LocalClass", ClassNameUtil.getSimpleName("com/foo/Bar$1LocalClass"));
        }

        @Test
        void conversionRoundTripSourceToInternal() {
            String original = "com.foo.Bar";
            String internal = ClassNameUtil.toInternalName(original);
            String backToSource = ClassNameUtil.toSourceName(internal);
            assertEquals(original, backToSource);
        }

        @Test
        void conversionRoundTripInternalToSource() {
            String original = "com/foo/Bar";
            String source = ClassNameUtil.toSourceName(original);
            String backToInternal = ClassNameUtil.toInternalName(source);
            assertEquals(original, backToInternal);
        }
    }
}
