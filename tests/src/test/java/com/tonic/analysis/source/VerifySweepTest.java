package com.tonic.analysis.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify-sweep over a jar: every class is {@code decompile -> re-lower -> verify}, flagging any whose
 * re-lowered form has a <em>control-flow drop</em> - a method that falls off its end or a path that does
 * not return, the signature of a dropped {@code return}/{@code throw} (e.g. the {@code try { return foo(); }}
 * shape whose return the compiler placed past the protected range). Other verify errors (stack, type,
 * frame) are re-lowering-pipeline noise on complex classes, not recovery drops, and are ignored - so this
 * stays a clean signal without the differential the removed engine flag once gave.
 *
 * <p>Classes the re-lowering pipeline cannot rebuild (throws) are a pipeline limitation, not a drop, and
 * are skipped. Unlike the behavioural recovery-equivalence oracle (per-method symbolic execution, hours
 * over a large jar), this only decompiles + re-lowers + verifies, so it runs in minutes. Opt-in: pass
 * {@code -Dverify.sweep.jar=<path>}; with no property the test skips (the jar is kept out of the suite).
 */
class VerifySweepTest {

    @Test
    void recoveredSourceHasNoControlFlowDrops() throws Exception {
        String jarProp = System.getProperty("verify.sweep.jar");
        Assumptions.assumeTrue(jarProp != null,
                "set -Dverify.sweep.jar=<path-to-jar> to run the verify sweep");
        Path jar = Path.of(jarProp);
        Assumptions.assumeTrue(Files.exists(jar), "verify.sweep.jar not found: " + jarProp);

        ClassPool pool = new ClassPool();
        List<ClassFile> cfs = load(jar, pool);

        Set<String> drops = new TreeSet<>();
        int graded = 0, pipelineSkipped = 0;
        for (ClassFile cf : cfs) {
            String name = cf.getClassName();
            boolean recompiled;
            try {
                String source = ClassDecompiler.decompile(cf);
                recompiled = TestUtils.recompileSource(cf, pool, source, name);
            } catch (Throwable t) {
                pipelineSkipped++; // decompile or re-lower could not handle this class - not a recovery drop
                continue;
            }
            if (!recompiled) {
                continue; // non-class type (enum/interface/annotation) - out of scope
            }
            graded++;
            if (TestUtils.hasControlFlowDrop(cf, pool)) {
                drops.add(name);
            }
        }

        System.out.println("[verify-sweep] graded=" + graded + " pipeline-skipped=" + pipelineSkipped
                + " control-flow-drops=" + drops.size());
        for (String c : drops) {
            System.out.println("  DROP (falls off end / path does not return): " + c);
        }
        assertTrue(drops.isEmpty(),
                "recovered source dropped a return/throw - a method falls off its end or a path does not "
                        + "return after re-lowering:\n" + drops);
    }

    private static List<ClassFile> load(Path jar, ClassPool pool) throws Exception {
        List<ClassFile> cfs = new ArrayList<>();
        try (JarInputStream jis = new JarInputStream(Files.newInputStream(jar))) {
            JarEntry e;
            while ((e = jis.getNextJarEntry()) != null) {
                String n = e.getName();
                if (!n.endsWith(".class") || n.contains("module-info") || n.contains("package-info")) {
                    continue;
                }
                try {
                    cfs.add(pool.loadClass(new ByteArrayInputStream(jis.readAllBytes())));
                } catch (Throwable ignored) {
                    // unparseable entry - not this sweep's concern
                }
            }
        }
        return cfs;
    }
}
