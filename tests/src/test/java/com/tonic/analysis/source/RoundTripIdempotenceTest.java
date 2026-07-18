package com.tonic.analysis.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip idempotence gate for the demo jar: for each class, {@code decompile -> recompile -> decompile}
 * must be a fixed point ({@code d1 == d2}) and the recompiled bytecode must verify. The recompile lowers all
 * methods and constructors (the original {@code <clinit>} is kept, so static initialization is trivially
 * stable and never a false failure). Prints a {@code idempotent: N/34} scoreboard plus, for each failing
 * class, the first differing region, so progress is visible as fixes land.
 */
class RoundTripIdempotenceTest {

    private static final String DIR = "C:/Users/zacke/IdeaProjects/DemoApplication/build/classes/java/main";

    @Test
    void demoClassesAreRoundTripIdempotent() throws Exception {
        Path root = Path.of(DIR);
        if (!Files.exists(root)) {
            return;
        }
        List<Path> classes;
        try (Stream<Path> s = Files.walk(root)) {
            classes = s.filter(p -> p.toString().endsWith(".class")).sorted().collect(Collectors.toList());
        }

        // One shared pool so inner/sibling type references (e.g. HeapAnalysisTest.Node, MainFrame fields)
        // resolve during recompile - an isolated per-class pool silently keeps such methods as their original
        // bytecode, which is a false pass.
        ClassPool pool = new ClassPool();
        List<ClassFile> cfs = new ArrayList<>();
        for (Path p : classes) {
            cfs.add(pool.loadClass(new ByteArrayInputStream(Files.readAllBytes(p))));
        }

        List<String> notIdempotent = new ArrayList<>();
        List<String> notVerifying = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> convergesNotFixed = new ArrayList<>();
        List<String> drifts = new ArrayList<>();
        StringBuilder report = new StringBuilder();

        for (ClassFile cf : cfs) {
            String name = cf.getClassName();
            String d1, d2, d3;
            boolean verified;
            try {
                d1 = ClassDecompiler.decompile(cf);
                if (!TestUtils.recompileSource(cf, pool, d1, name)) {
                    skipped.add(name); // non-class type (enum/interface/annotation) - out of scope for now
                    continue;
                }
                verified = TestUtils.verifies(cf, pool);
                d2 = ClassDecompiler.decompile(cf);
                TestUtils.recompileSource(cf, pool, d2, name);
                d3 = ClassDecompiler.decompile(cf);
            } catch (Throwable t) {
                notIdempotent.add(name + " (threw " + t + ")");
                continue;
            }
            if (!verified) {
                notVerifying.add(name);
            }
            if (!d2.equals(d3)) {
                drifts.add(name); // genuinely drifts - output keeps changing each round trip
            }
            if (!d1.equals(d2)) {
                notIdempotent.add(name);
                report.append("\n=== ").append(name).append(" ===\n").append(firstDiff(d1, d2));
                if (d2.equals(d3)) {
                    convergesNotFixed.add(name); // stabilizes after one round trip (d2==d3) - normalization, not drift
                }
            }
        }

        int graded = cfs.size() - skipped.size();
        int pass = graded - notIdempotent.size();
        int stable = graded - drifts.size();
        System.out.println("idempotent (d1==d2): " + pass + "/" + graded
                + "  |  stable/no-drift (d2==d3): " + stable + "/" + graded
                + "  (skipped " + skipped.size() + " non-class)"
                + (notVerifying.isEmpty() ? "" : "  | NOT VERIFYING: " + notVerifying));
        System.out.println("  converges-not-fixed (d1!=d2, d2==d3): " + convergesNotFixed);
        System.out.println("  genuinely drifts (d2!=d3): " + drifts);
        for (String n : notIdempotent) {
            System.out.println("  NOT IDEMPOTENT: " + n);
        }
        System.out.println(report);

        assertTrue(notIdempotent.isEmpty() && notVerifying.isEmpty(),
                "round-trip not a fixed point: " + pass + "/" + graded + " idempotent; "
                        + "not-idempotent=" + notIdempotent + " not-verifying=" + notVerifying);
    }

    /** A small window around the first differing line, for quick eyeballing. */
    private static String firstDiff(String a, String b) {
        String[] la = a.split("\n");
        String[] lb = b.split("\n");
        int i = 0;
        while (i < la.length && i < lb.length && la[i].equals(lb[i])) {
            i++;
        }
        StringBuilder sb = new StringBuilder();
        for (int j = Math.max(0, i - 2); j < Math.min(Math.max(la.length, lb.length), i + 4); j++) {
            String x = j < la.length ? la[j] : "<end>";
            String y = j < lb.length ? lb[j] : "<end>";
            sb.append(j == i ? ">> " : "   ").append("d1| ").append(x).append("\n");
            sb.append(j == i ? ">> " : "   ").append("d2| ").append(y).append("\n");
        }
        return sb.toString();
    }
}
