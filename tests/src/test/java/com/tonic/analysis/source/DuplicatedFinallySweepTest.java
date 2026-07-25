package com.tonic.analysis.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sweep for the duplicated-finally-effect signature: a statement directly following a {@code finally}
 * block that is identical to a statement inside it. That is the shape of an inlined finally copy folded
 * into the clause AND left behind in the body - the finally's side effects (a lock release, a stream
 * close) run twice on that path. Legitimate path duplication (a shared tail copied into separate
 * branches) never places the copy in sequence after its own clause, so this stays a clean signal.
 *
 * <p>Opt-in like the verify sweep: pass {@code -Dverify.sweep.jar=<path>}; skipped otherwise.
 */
class DuplicatedFinallySweepTest {

    /**
     * Classes with a triaged, not-yet-fixed duplicate - all one family: the finally body CARRIES CONTROL
     * FLOW (a guarded close, a try-with-resources suppress dispatch), so its inlined copies are branchy
     * subgraphs the straight-line de-duplication cannot match, and one copy survives after the clause.
     * Fixing the family needs subgraph-shaped template matching in the finally de-dup. This sweep found
     * four of the five; they stay listed so the gate holds everywhere else. New entries must not be
     * added without the same level of documentation.
     */
    private static final Set<String> KNOWN_REMAINING = Set.of(
            "jme3tools/savegame/SaveGame",
            "org/lwjgl/system/SharedLibraryLoader",
            "org/lwjgl/vulkan/awt/PlatformMacOSXVKCanvas",
            "org/lwjgl/vulkan/awt/PlatformWin32VKCanvas",
            "org/lwjgl/vulkan/awt/PlatformX11VKCanvas");

    @Test
    void noStatementRepeatsItsOwnFinallyClause() throws Exception {
        String jarProp = System.getProperty("verify.sweep.jar");
        Assumptions.assumeTrue(jarProp != null,
                "set -Dverify.sweep.jar=<path-to-jar> to run the duplicated-finally sweep");
        Path jar = Path.of(jarProp);
        Assumptions.assumeTrue(Files.exists(jar), "verify.sweep.jar not found: " + jarProp);

        ClassPool pool = new ClassPool();
        List<ClassFile> cfs = load(jar, pool);

        Set<String> flagged = new TreeSet<>();
        int graded = 0, skipped = 0, knownRemaining = 0;
        for (ClassFile cf : cfs) {
            String source;
            try {
                source = ClassDecompiler.decompile(cf);
            } catch (Throwable t) {
                skipped++;
                continue;
            }
            graded++;
            if (hasPostFinallyDuplicate(source)) {
                if (KNOWN_REMAINING.contains(cf.getClassName())) {
                    knownRemaining++;
                } else {
                    flagged.add(cf.getClassName());
                }
            }
        }

        System.out.println("[dup-finally-sweep] graded=" + graded + " skipped=" + skipped
                + " flagged=" + flagged.size() + " known-remaining=" + knownRemaining);
        for (String c : flagged) {
            System.out.println("  DUPLICATED FINALLY EFFECT: " + c);
        }
        assertTrue(flagged.isEmpty(),
                "a statement after a finally block repeats a statement inside it - the finally's side "
                        + "effects run twice on that path:\n" + flagged);
    }

    /**
     * Scans decompiled source for a non-trivial statement line inside a {@code finally} block that
     * reappears among the first statement lines after the block's closing brace. Lines are compared
     * trimmed; only lines performing a call are considered (assignments of constants and braces are
     * not effects worth flagging).
     */
    private static boolean hasPostFinallyDuplicate(String source) {
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().equals("finally {")) {
                continue;
            }
            int depth = 1;
            Set<String> body = new HashSet<>();
            int j = i + 1;
            for (; j < lines.length && depth > 0; j++) {
                String t = lines[j].trim();
                if (t.endsWith("{")) {
                    depth++;
                } else if (t.equals("}")) {
                    depth--;
                    continue;
                }
                if (depth > 0 && isEffectLine(t)) {
                    body.add(t);
                }
            }
            int rel = 0;
            for (int k = j, seen = 0; k < lines.length && seen < 6 && rel >= 0; k++) {
                String t = lines[k].trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (t.equals("}")) {
                    rel--;
                    continue;
                }
                if (t.endsWith("{")) {
                    rel++;
                    continue;
                }
                seen++;
                if (body.contains(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A line whose re-execution is observable: it contains a call (not a bare brace or constant store). */
    private static boolean isEffectLine(String trimmed) {
        return trimmed.contains("(") && trimmed.endsWith(";");
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
