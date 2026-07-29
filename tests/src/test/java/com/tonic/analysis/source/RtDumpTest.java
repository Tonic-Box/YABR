package com.tonic.analysis.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.RoundTripCorpus;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Investigation aid for round-trip drift: decompiles a class, recompiles its own output, and
 * decompiles again twice, writing all three generations to {@code %TEMP%/rt} so they can be diffed.
 * It asserts nothing - {@link RoundTripIdempotenceTest} is the gate; this exists so that when that
 * gate goes red, the differing text is one command away.
 * <p>
 * Targets default to the classes that have historically drifted and can be overridden with
 * {@code -Dyabr.rt.targets=A,B,C} (simple names, matched against the class name's tail).
 */
class RtDumpTest {

    private static final String DEFAULT_TARGETS =
            "SymbolicExecutionTests,Main,HeapAnalysisTest,AuthenticationCoordinator,SessionManager";

    @Test
    void dump() throws Exception {
        Path root = RoundTripCorpus.path();
        assumeTrue(Files.isDirectory(root), "demo classes not built at " + root);

        String[] targets = System.getProperty("yabr.rt.targets", DEFAULT_TARGETS).split(",");
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "rt");
        Files.createDirectories(out);

        for (String target : targets) {
            String name = target.trim();
            if (name.isEmpty()) {
                continue;
            }
            ClassPool pool = new ClassPool();
            ClassFile cf = locate(root, pool, name);
            if (cf == null) {
                System.out.println("RT " + name + " NOT FOUND");
                continue;
            }
            String owner = cf.getClassName();
            try {
                String d1 = ClassDecompiler.decompile(cf);
                if (!TestUtils.recompileSource(cf, pool, d1, owner)) {
                    System.out.println("RT " + name + " NOT RECOMPILABLE");
                    continue;
                }
                String d2 = ClassDecompiler.decompile(cf);
                TestUtils.recompileSource(cf, pool, d2, owner);
                String d3 = ClassDecompiler.decompile(cf);

                Files.writeString(out.resolve(name + "_d1.java"), d1);
                Files.writeString(out.resolve(name + "_d2.java"), d2);
                Files.writeString(out.resolve(name + "_d3.java"), d3);
                System.out.println("RT " + name
                        + " d1==d2=" + d1.equals(d2)
                        + " d2==d3=" + d2.equals(d3)
                        + (d1.equals(d2) ? "" : " firstDiff=" + firstDiff(d1, d2)));
            } catch (Exception failure) {
                System.out.println("RT " + name + " FAILED: " + failure);
            }
        }
        System.out.println("RT dumps written to " + out);
    }

    /** The last top-level class under {@code root} whose name ends with {@code target}. */
    private static ClassFile locate(Path root, ClassPool pool, String target) throws Exception {
        ClassFile found = null;
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(x -> x.toString().endsWith(".class")).sorted().forEach(paths::add);
        }
        for (Path q : paths) {
            ClassFile c = pool.loadClass(new ByteArrayInputStream(Files.readAllBytes(q)));
            if (c.getClassName().endsWith(target) && !c.getClassName().contains("$")) {
                found = c;
            }
        }
        return found;
    }

    /** A short window around the first differing character, to point at the drifting construct. */
    private static String firstDiff(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        int from = Math.max(0, i - 60);
        return "@" + i + " a=[" + slice(a, from, i + 60) + "] b=[" + slice(b, from, i + 60) + "]";
    }

    private static String slice(String s, int from, int to) {
        return s.substring(Math.min(from, s.length()), Math.min(to, s.length())).replace('\n', '|');
    }
}
