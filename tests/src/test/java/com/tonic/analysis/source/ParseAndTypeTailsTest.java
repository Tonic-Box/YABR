package com.tonic.analysis.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Four parse/type tails that failed whole classes:
 * <ul>
 * <li>{@code var} is reserved only as a type name - fields, parameters and locals named {@code var}
 *     are legal and must parse in declaration, member-access and expression position.</li>
 * <li>A varargs parameter declares the element type but IS an array - {@code xs.length} and
 *     {@code xs[0]} on {@code int... xs} must lower against {@code int[]}, and the rebuilt
 *     descriptor must carry the array dimension.</li>
 * <li>A class literal may be stored in the pool in descriptor form ({@code [Ljava/lang/String;});
 *     it must emit as {@code String[].class} and that text must parse back.</li>
 * <li>{@code instanceof} against an array type is a reference check even though the checked type
 *     is not a ReferenceSourceType.</li>
 * </ul>
 */
class ParseAndTypeTailsTest {

    @Test
    void varIsALegalMemberAndVariableName() throws Exception {
        ClassFile cf = compile("VarName", new ClassPool(),
                "public class VarName {",
                "    static int var = 3;",
                "    static int use(int var) {",
                "        int sum = var + VarName.var;",
                "        return sum;",
                "    }",
                "    public static int check() { return use(4); }",
                "}");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals(7, original, "the fixture itself must add the parameter and the field");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "VarName"),
                "names spelled var must parse everywhere a name is legal:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void aVarargsParameterIsAnArray() throws Exception {
        ClassFile cf = compile("Tally", new ClassPool(),
                "public class Tally {",
                "    static int tally(String... xs) { return xs.length * 10 + xs[0].length(); }",
                "    public static int check() { return tally(\"ab\", \"c\"); }",
                "}");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals(22, original, "the fixture itself must see two elements");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Tally"),
                "xs.length must lower against int[], not int:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void anArrayClassLiteralRoundTrips() throws Exception {
        ClassFile cf = compile("Lits", new ClassPool(),
                "public class Lits {",
                "    public static String check() {",
                "        return String[].class.getSimpleName() + \"|\" + int[][].class.getSimpleName()",
                "                + \"|\" + Integer.class.getSimpleName();",
                "    }",
                "}");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("String[]|int[][]|Integer", original, "the fixture itself must name all three literals");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertFalse(d1.contains(";.class"), "a descriptor must not leak into a class literal:\n" + d1);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Lits"),
                "array class literals must emit and re-parse:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    @Test
    void anArrayInstanceOfLowers() throws Exception {
        ClassFile cf = compile("ArrInst", new ClassPool(),
                "public class ArrInst {",
                "    static int kind(Object o) {",
                "        if (o instanceof String[]) { return 1; }",
                "        if (o instanceof int[][]) { return 2; }",
                "        return 0;",
                "    }",
                "    public static int check() {",
                "        return kind(new String[0]) * 100 + kind(new int[1][1]) * 10 + kind(\"s\");",
                "    }",
                "}");
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals(120, original, "the fixture itself must distinguish the three kinds");

        ClassPool pool = new ClassPool();
        pool.loadClass(cf.write());
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "ArrInst"),
                "instanceof against an array type must lower:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }

    private static ClassFile compile(String name, ClassPool pool, String... lines) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory(name.toLowerCase());
        Path src = dir.resolve(name + ".java");
        Files.writeString(src, String.join(System.lineSeparator(), lines));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        return pool.loadClass(Files.readAllBytes(dir.resolve(name + ".class")));
    }
}
