package com.tonic.analysis.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Three re-lowering tails that produced unverifiable or unresolvable bytecode:
 * <ul>
 * <li>A member accessed through a TYPE-VARIABLE-typed receiver resolves against the variable's
 *     erasure ({@code T extends Bound} erases to {@code Bound}), as javac erases it.</li>
 * <li>{@code (long) byteValue & 255L} needs {@code i2l} (byte/short/char sit as int on the stack;
 *     their conversions and widenings are the int ones) and the {@code &} must be {@code land} even
 *     when the parse-time type guessed int - binary numeric promotion is never narrower than an
 *     operand's promoted type.</li>
 * <li>A lambda assigned to a FIELD takes its functional interface from the field's declared type;
 *     without it the invokedynamic site returns void and the following putfield underflows.</li>
 * </ul>
 */
class LoweringTailsTest {

    @Test
    void aTypeVariableReceiverErasesToItsBound() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Holder",
                "public class Holder<T extends StringBuilder> {",
                "    T item;",
                "    Holder(T item) { this.item = item; }",
                "    int size() { return this.item.length(); }",
                "    public static int check() {",
                "        return new Holder<>(new StringBuilder(\"abcd\")).size();",
                "    }",
                "}");
        ClassFile cf = loaded.get("Holder");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals(4, original, "the fixture itself must measure through the bound");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Holder"),
                "a T-typed receiver must resolve members on its bound:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aGenericBoundErasesToItsRawType() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Ring",
                "public class Ring<T extends Comparable<T>> {",
                "    T lo;",
                "    T hi;",
                "    Ring(T lo, T hi) { this.lo = lo; this.hi = hi; }",
                "    int span() { return this.hi.compareTo(this.lo); }",
                "    public static int check() { return new Ring<>(3, 8).span(); }",
                "}");
        ClassFile cf = loaded.get("Ring");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals(1, original, "the fixture itself must compare through the bound");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Ring"),
                "T extends Comparable<T> must erase to Comparable, not Object:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aMaskedByteWidensToLong() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Pack",
                "public class Pack {",
                "    static long conv(byte[] bytes, int offset) {",
                "        return ((long) bytes[offset + 1] & 255L) + (((long) bytes[offset] & 255L) << 8);",
                "    }",
                "    public static long check() { return conv(new byte[] {2, 3}, 0); }",
                "}");
        ClassFile cf = loaded.get("Pack");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals(515L, original, "the fixture itself must pack both bytes");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Pack"),
                "the byte must widen and the mask must be a long op:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aFieldStoredLambdaTakesTheFieldsInterface() throws Exception {
        Map<String, ClassFile> loaded = compileAll("Gate",
                "import java.util.function.Predicate;",
                "public class Gate {",
                "    private Predicate<String> filter;",
                "    private int min;",
                "    Gate(int min) {",
                "        this.min = min;",
                "        this.filter = s -> s.length() > this.min;",
                "    }",
                "    public static String check() {",
                "        Gate g = new Gate(2);",
                "        return g.filter.test(\"abc\") + \"|\" + g.filter.test(\"a\");",
                "    }",
                "}");
        ClassFile cf = loaded.get("Gate");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("true|false", original, "the fixture itself must filter by length");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Gate"),
                "the lambda's interface must come from the field's declared type:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    private static Map<String, ClassFile> compileAll(String primary, String... lines) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory(primary.toLowerCase());
        Path src = dir.resolve(primary + ".java");
        Files.writeString(src, String.join(System.lineSeparator(), lines));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        ClassPool pool = new ClassPool();
        Map<String, ClassFile> loaded = new HashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.class")) {
            for (Path p : stream) {
                ClassFile cf = pool.loadClass(Files.readAllBytes(p));
                loaded.put(cf.getClassName(), cf);
            }
        }
        return loaded;
    }
}
