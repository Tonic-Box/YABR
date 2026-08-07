package com.tonic.testutil;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Reports, per class, how far a jar or class directory gets through the pipeline. This is the investigation
 * tool the sweeps in {@code VerifySweepTest} and {@code RoundTripIdempotenceTest} assert on: when one of them
 * goes red, or when a change is expected to widen coverage, this prints the per-class outcome so the
 * difference can be diffed rather than inferred from a single total.
 *
 * <pre>
 *   java -cp &lt;test runtime classpath&gt; com.tonic.testutil.DecompileSweep &lt;jar-or-dir&gt; [--verbose]
 * </pre>
 *
 * Every class is loaded into ONE pool, as the gates do, so sibling and interface references resolve. Each is
 * then reported as one of:
 * - {@code FAILED} - decompiling threw. A defect, always.
 * - {@code SKIPPED} - decompiled, but re-lowering could not take the source back. Also a defect, but a
 *       narrower one: the recompile gates cannot grade this class, so it is invisible to them.
 * - {@code DRIFTS} - re-lowered, but decompiling the result gives different text. Not a fixed point.
 * - {@code GRADED} - round-trips to identical source. What every class should be.
 * The trailing summary line is the number to compare between runs.
 */
public final class DecompileSweep
{

    private DecompileSweep() {}

    /**
     * One class's outcome, worst first so a sort surfaces the defects.
     */
    private enum Outcome { FAILED, SKIPPED, DRIFTS, GRADED }

    public static void main(String[] args) throws Exception
    {
        if (args.length < 1)
        {
            System.out.println("usage: DecompileSweep <jar-or-class-dir> [--verbose]");
            return;
        }
        boolean verbose = args.length > 1 && "--verbose".equals(args[1]);
        Path target = Paths.get(args[0]);
        if (!Files.exists(target))
        {
            System.out.println("not found: " + target);
            return;
        }

        ClassPool pool = new ClassPool();
        List<ClassFile> classes = load(target, pool);
        int[] counts = new int[Outcome.values().length];

        for (ClassFile cf : classes)
        {
            Outcome outcome = sweep(cf, pool, verbose);
            counts[outcome.ordinal()]++;
            if (outcome != Outcome.GRADED || verbose)
            {
                System.out.println(outcome + " " + cf.getClassName());
            }
        }

        StringBuilder summary = new StringBuilder("[sweep] classes=").append(classes.size());
        for (Outcome outcome : Outcome.values())
        {
            summary.append(' ').append(outcome.name().toLowerCase(Locale.ROOT))
                    .append('=').append(counts[outcome.ordinal()]);
        }
        System.out.println(summary);
    }

    private static Outcome sweep(ClassFile cf, ClassPool pool, boolean verbose)
    {
        String first;
        try
        {
            first = ClassDecompiler.decompile(cf);
        }
        catch (Throwable decompileFailure)
        {
            if (verbose)
            {
                System.out.println("  " + decompileFailure);
            }
            return Outcome.FAILED;
        }
        try
        {
            if (!TestUtils.recompileSource(cf, pool, first, cf.getClassName()))
            {
                return Outcome.SKIPPED;
            }
        }
        catch (Throwable relowerFailure)
        {
            if (verbose)
            {
                System.out.println("  " + relowerFailure);
            }
            return Outcome.SKIPPED;
        }
        try
        {
            return first.equals(ClassDecompiler.decompile(cf)) ? Outcome.GRADED : Outcome.DRIFTS;
        }
        catch (Throwable secondFailure)
        {
            if (verbose)
            {
                System.out.println("  " + secondFailure);
            }
            return Outcome.FAILED;
        }
    }

    /**
     * Every class in a jar or under a directory, in a stable order, all sharing {@code pool}.
     */
    private static List<ClassFile> load(Path target, ClassPool pool) throws Exception
    {
        List<ClassFile> classes = new ArrayList<>();
        if (Files.isDirectory(target))
        {
            List<Path> paths = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(target))
            {
                walk.filter(p -> p.toString().endsWith(".class")).sorted().forEach(paths::add);
            }
            for (Path p : paths)
            {
                addQuietly(classes, pool, Files.readAllBytes(p));
            }
            return classes;
        }
        try (JarFile jar = new JarFile(target.toFile()))
        {
            List<JarEntry> entries = new ArrayList<>();
            Enumeration<JarEntry> en = jar.entries();
            while (en.hasMoreElements())
            {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF")
                        && !name.contains("module-info") && !name.contains("package-info"))
                {
                    entries.add(e);
                }
            }
            entries.sort(java.util.Comparator.comparing(JarEntry::getName));
            for (JarEntry e : entries)
            {
                try (InputStream in = jar.getInputStream(e))
                {
                    addQuietly(classes, pool, in.readAllBytes());
                }
            }
        }
        return classes;
    }

    /**
     * A class file this project's own parser cannot read is not what the sweep measures, so it is dropped.
     */
    private static void addQuietly(List<ClassFile> classes, ClassPool pool, byte[] bytes)
    {
        try
        {
            classes.add(pool.loadClass(new ByteArrayInputStream(bytes)));
        }
        catch (Throwable unparseable)
        {
            // not a class this parser handles - out of scope for the sweep
        }
    }
}
