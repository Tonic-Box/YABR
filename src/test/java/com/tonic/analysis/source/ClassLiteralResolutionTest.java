package com.tonic.analysis.source;

import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression for the unresolved class-constant bug: a reference type named by its simple name - notably a class
 * referring to itself in its own package - must lower to a FULLY-QUALIFIED class constant. {@code lowerClass} /
 * {@code lowerCast} / {@code lowerInstanceOf} used {@code SourceType.toIRType()} directly, which emitted the bare
 * simple name (e.g. {@code Demo} instead of {@code test/Demo}); at runtime the JVM then threw
 * {@code NoClassDefFoundError}/{@code ClassNotFoundException} when it resolved that class constant. Both constructs
 * below resolve the class constant at run time, so each reproduces the failure pre-fix and passes post-fix.
 */
public class ClassLiteralResolutionTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void ownClassLiteralResolvesToFullyQualifiedName() throws Exception {
        String source =
            "package test;\n" +
            "public class Demo {\n" +
            "    public static String selfName() {\n" +
            "        return Demo.class.getName();\n" +
            "    }\n" +
            "}\n";

        Class<?> cls = TestUtils.compileLinkAndLoad(source, "test/Demo");
        // Pre-fix: ldc of class constant `Demo` -> NoClassDefFoundError: Demo.
        assertEquals("test.Demo", cls.getMethod("selfName").invoke(null));
    }

    @Test
    void ownInstanceOfResolvesToFullyQualifiedName() throws Exception {
        String source =
            "package test;\n" +
            "public class InstDemo {\n" +
            "    public static boolean isSelf(Object o) {\n" +
            "        return o instanceof InstDemo;\n" +
            "    }\n" +
            "}\n";

        Class<?> cls = TestUtils.compileLinkAndLoad(source, "test/InstDemo");
        // Pre-fix: instanceof resolves class constant `InstDemo` -> ClassNotFoundException: InstDemo.
        // Post-fix: resolves test/InstDemo; a String is not an InstDemo -> false.
        assertEquals(Boolean.FALSE, cls.getMethod("isSelf", Object.class).invoke(null, "hello"));
    }
}
