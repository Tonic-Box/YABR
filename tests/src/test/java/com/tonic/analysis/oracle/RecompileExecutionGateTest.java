package com.tonic.analysis.oracle;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A differential EXECUTION gate for the recompile (decompile → recompile) round trip. The other round-trip gates
 * are decompile-only or re-decompile the recompiled bytecode - they never load and RUN it, so a class that
 * recompiles to bytecode that re-decompiles to the same source but throws {@code NoSuchMethodError},
 * {@code IncompatibleClassChangeError}, or {@code ClassFormatError} at load/call time passes them silently. Each
 * fixture here is a self-contained class with a {@code public static String check()} that encodes its behavior;
 * the fixture is compiled with javac, decompiled, recompiled, LOADED and EXECUTED, and its result is compared to
 * the original's. The fixtures deliberately exercise the shapes that broke the recompiler when the class pool
 * lacks the JDK: a subtype argument to an interface/Object parameter, a call to a JDK interface method, a
 * constructor taking a supertype, a generic local, plus ordinary control flow, arithmetic, strings, and arrays.
 */
class RecompileExecutionGateTest {

    private static final Map<String, String> FIXTURES = new LinkedHashMap<>();
    static {
        FIXTURES.put("GateCollections",
                "import java.util.*;\n"
                + "public class GateCollections {\n"
                + "    public static String check() {\n"
                + "        List<String> l = new ArrayList<>();\n"
                + "        l.add(\"a\"); l.add(\"b\");\n"
                + "        Map<String, Integer> m = new HashMap<>();\n"
                + "        m.put(\"k\", 7);\n"
                + "        return l.size() + \":\" + l.get(0) + \":\" + m.get(\"k\");\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("GateInterfaceCalls",
                "import java.util.*;\n"
                + "public class GateInterfaceCalls {\n"
                + "    public static String check() {\n"
                + "        Collection<Integer> c = new ArrayList<>();\n"
                + "        c.add(3); c.add(1); c.add(2);\n"
                + "        CharSequence cs = \"hello\";\n"
                + "        return c.size() + \":\" + c.contains(2) + \":\" + cs.length() + \":\" + cs.charAt(1);\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("GateExceptionWrap",
                "public class GateExceptionWrap {\n"
                + "    public static String check() {\n"
                + "        try {\n"
                + "            Object o = null;\n"
                + "            o.toString();\n"
                + "            return \"no\";\n"
                + "        } catch (NullPointerException e) {\n"
                + "            RuntimeException r = new RuntimeException(\"wrapped\", e);\n"
                + "            return r.getMessage() + \"/\" + r.getCause().getClass().getSimpleName();\n"
                + "        }\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("GateSubtypeArgs",
                "import java.util.*;\n"
                + "public class GateSubtypeArgs {\n"
                + "    public static String check() {\n"
                + "        List<Object> l = new ArrayList<>();\n"
                + "        l.add(\"s\");\n"
                + "        Integer i = 5;\n"
                + "        return Objects.requireNonNull(i) + \":\" + l.size() + \":\" + String.valueOf(l.get(0));\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("GateControlFlow",
                "public class GateControlFlow {\n"
                + "    public static String check() {\n"
                + "        int sum = 0;\n"
                + "        for (int i = 0; i < 10; i++) { if (i % 2 == 0) { sum += i; } else { sum -= 1; } }\n"
                + "        int j = 0; while (j < 5) { sum *= 2; j++; }\n"
                + "        return \"\" + sum;\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("GateSwitch",
                "public class GateSwitch {\n"
                + "    static String label(int x) {\n"
                + "        switch (x) {\n"
                + "            case 0: return \"zero\";\n"
                + "            case 1: case 2: return \"small\";\n"
                + "            default: return \"big\";\n"
                + "        }\n"
                + "    }\n"
                + "    public static String check() {\n"
                + "        StringBuilder b = new StringBuilder();\n"
                + "        b.append(label(0)).append(label(1)).append(label(2)).append(label(9));\n"
                + "        return b.toString();\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("GateStrings",
                "public class GateStrings {\n"
                + "    public static String check() {\n"
                + "        StringBuilder sb = new StringBuilder();\n"
                + "        for (int i = 0; i < 4; i++) { sb.append(i).append(','); }\n"
                + "        String s = \"x\" + 1 + \"y\" + (2 + 3);\n"
                + "        return sb.toString() + \"|\" + s + \"|\" + s.substring(1, 3);\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("GateStringConcat",
                "public class GateStringConcat {\n"
                + "    public static String check() {\n"
                + "        int i = 42; char ch = 'z'; Object o = \"obj\"; long lo = 9L;\n"
                + "        String a = \"\" + i;\n"
                + "        String b = \"x\" + ch;\n"
                + "        String d = \"\" + o;\n"
                + "        String e = String.valueOf(lo);\n"
                + "        return a + \"|\" + b + \"|\" + d + \"|\" + e;\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("GateArrays",
                "public class GateArrays {\n"
                + "    public static String check() {\n"
                + "        int[] a = new int[5];\n"
                + "        for (int i = 0; i < a.length; i++) { a[i] = i * i; }\n"
                + "        String[] s = {\"p\", \"q\", \"r\"};\n"
                + "        return a.length + \":\" + a[3] + \":\" + s[2] + \":\" + s.length;\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("GateBoxing",
                "public class GateBoxing {\n"
                + "    public static String check() {\n"
                + "        Integer a = 40; int b = a + 2;\n"
                + "        Long c = 100L; long d = c - 1;\n"
                + "        Boolean flag = (b > 40);\n"
                + "        StringBuilder sb = new StringBuilder();\n"
                + "        sb.append(b).append(':').append(d).append(':').append(flag)\n"
                + "          .append(':').append(Integer.valueOf(b).compareTo(41));\n"
                + "        return sb.toString();\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("GateTryFinally",
                "public class GateTryFinally {\n"
                + "    static int events;\n"
                + "    static int run(int x) {\n"
                + "        events = 0;\n"
                + "        try { if (x < 0) { return events + 100; } return events + x; }\n"
                + "        finally { events = events + 1; }\n"
                + "    }\n"
                + "    public static String check() { int a = run(5); int e1 = events; int b = run(-1); return a + \":\" + e1 + \":\" + b + \":\" + events; }\n"
                + "}\n");
    }

    private static Path classesDir;

    @BeforeAll
    static void compileFixtures() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        classesDir = Files.createTempDirectory("recompile-exec-gate");
        for (Map.Entry<String, String> e : FIXTURES.entrySet()) {
            Files.writeString(classesDir.resolve(e.getKey() + ".java"), e.getValue());
        }
        String[] sources = FIXTURES.keySet().stream()
                .map(n -> classesDir.resolve(n + ".java").toString())
                .toArray(String[]::new);
        String[] args = new String[sources.length + 3];
        args[0] = "-g";
        args[1] = "-d";
        args[2] = classesDir.toString();
        System.arraycopy(sources, 0, args, 3, sources.length);
        assumeTrue(compiler.run(null, null, null, args) == 0, "fixtures compiled");
    }

    static java.util.Set<String> fixtureNames() {
        return FIXTURES.keySet();
    }

    @ParameterizedTest
    @MethodSource("fixtureNames")
    void recompiledBehavesLikeOriginal(String name) throws Exception {
        byte[] original = Files.readAllBytes(classesDir.resolve(name + ".class"));
        Object originalResult = runBytes(name, original);

        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(original);
        String source = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, source, name), name + " must be recompilable");
        Object recompiledResult = runBytes(name, cf.write());

        assertEquals(originalResult, recompiledResult,
                name + " must produce the same result after decompile + recompile");
    }

    /**
     * Defines {@code bytes} as class {@code name} in a fresh loader and runs its {@code check()}. Using the JVM's
     * own verification and execution (not YABR's pool-dependent verifier) is the ground truth: it catches a
     * VerifyError or ClassFormatError at load, and a NoSuchMethodError or IncompatibleClassChangeError at call.
     */
    private static Object runBytes(String name, byte[] bytes) throws Exception {
        ClassLoader loader = new ClassLoader(RecompileExecutionGateTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String n) throws ClassNotFoundException {
                if (n.equals(name)) {
                    return defineClass(n, bytes, 0, bytes.length);
                }
                throw new ClassNotFoundException(n);
            }
        };
        return loader.loadClass(name).getDeclaredMethod("check").invoke(null);
    }
}
