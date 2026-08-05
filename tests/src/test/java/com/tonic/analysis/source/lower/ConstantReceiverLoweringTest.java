package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Re-lowering must read a constant through its own simple name as a value. A bare identifier that is not a
 * local is otherwise taken for a class name, which makes the access static against a type that does not
 * exist - so a class using {@code MAX.x} is refused by the lowering entirely and never reaches the recompile
 * gates. Method calls already consulted the field table before concluding "class name"; field loads and
 * stores now do the same.
 *
 * The second case covers where the field is declared: a {@code static final} constant can come from an
 * implemented interface rather than a superclass, so resolving a field type walks the interface set the way
 * resolving a method return type already did.
 */
class ConstantReceiverLoweringTest
{

    private static final String STATIC_FIELD_RECEIVER = String.join("\n",
            "import java.awt.Point;",
            "public class ConstReceiver {",
            "    static final Point MAX = new Point(3, 4);",
            "    static void bump() {",
            "        MAX.x = 9;",
            "    }",
            "    public static String check() {",
            "        int before = MAX.x + MAX.y;",
            "        bump();",
            "        return before + \":\" + MAX.x;",
            "    }",
            "}",
            "");

    private static final String INTERFACE_CONSTANT_IFACE = String.join("\n",
            "public interface Labelled {",
            "    String LABEL = \"L\";",
            "    StringBuilder SHARED = new StringBuilder(\"s\");",
            "}",
            "");

    private static final String INTERFACE_CONSTANT_IMPL = String.join("\n",
            "public class UsesLabel implements Labelled {",
            "    public static String check() {",
            "        return LABEL + SHARED.length();",
            "    }",
            "}",
            "");

    @Test
    void aStaticConstantIsAValueReceiverNotAClassName() throws Exception
    {
        Path dir = compile("const-receiver", "ConstReceiver", STATIC_FIELD_RECEIVER);
        assertRoundTripBehavesTheSame(dir, "ConstReceiver", "7:9");
    }

    /**
     * Asserted on the re-lowered bytecode rather than by running it: the fixture implements an interface, and
     * loading it would need that interface on the test classloader too. What matters is that the constant's
     * declared type is found at all - unresolved, the field load carries an {@code Object} descriptor and the
     * call on it no longer links.
     */
    @Test
    void anInterfaceDeclaredConstantResolvesItsType() throws Exception
    {
        Path dir = compile("iface-const", "Labelled", INTERFACE_CONSTANT_IFACE, "UsesLabel", INTERFACE_CONSTANT_IMPL);
        ClassPool pool = new ClassPool();
        ClassFile cf = loadInto(pool, dir, "UsesLabel");

        String source = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, source, "UsesLabel"),
                "the decompiled source must re-lower:\n" + source);
        assertTrue(TestUtils.verifies(cf, pool), "the re-lowered class must verify:\n" + source);
        assertTrue(ClassDecompiler.decompile(cf).contains("SHARED.length()"),
                "the interface-declared constant must keep its declared type, so the call on it survives");
    }

    /**
     * Compiles the given simple-name/source pairs into a fresh temp directory with debug info.
     */
    private static Path compile(String prefix, String... nameThenSource) throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory(prefix);
        List<String> args = new ArrayList<>(List.of("-g", "-d", dir.toString()));
        for (int i = 0; i < nameThenSource.length; i += 2)
        {
            Path src = dir.resolve(nameThenSource[i] + ".java");
            Files.writeString(src, nameThenSource[i + 1]);
            args.add(src.toString());
        }
        assumeTrue(compiler.run(null, null, null, args.toArray(new String[0])) == 0, "fixture compiled");
        return dir;
    }

    /**
     * Decompiles {@code target}, re-lowers that source in place, and requires the class to still produce
     * {@code expected} from {@code check()} - with every class in {@code dir} in one pool, so sibling and
     * interface references resolve as they do in the sweeps.
     */
    private static void assertRoundTripBehavesTheSame(Path dir, String target, String expected) throws Exception
    {
        ClassPool pool = new ClassPool();
        ClassFile cf = loadInto(pool, dir, target);
        assertEquals(expected, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the fixture itself must produce the expected value");

        String source = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, source, target),
                "the decompiled source must re-lower:\n" + source);
        assertEquals(expected, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the re-lowered class must behave the same:\n" + source);
    }

    /**
     * Loads every class under {@code dir} into one pool - so siblings and interfaces resolve - and returns one.
     */
    private static ClassFile loadInto(ClassPool pool, Path dir, String target) throws Exception
    {
        List<Path> classes = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(dir))
        {
            walk.filter(x -> x.toString().endsWith(".class")).sorted().forEach(classes::add);
        }
        ClassFile found = null;
        for (Path p : classes)
        {
            ClassFile loaded = pool.loadClass(Files.readAllBytes(p));
            if (loaded.getClassName().equals(target))
            {
                found = loaded;
            }
        }
        assertNotNull(found, target + " must be compiled");
        return found;
    }
}
