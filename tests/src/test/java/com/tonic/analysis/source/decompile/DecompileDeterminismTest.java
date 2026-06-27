package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decompilation must be a pure function: the same class bytes must always produce
 * the same source. This previously failed because {@code SSAValue}/{@code IRBlock}/
 * {@code IRInstruction} used identity hashing, so object-keyed {@code HashSet}/
 * {@code HashMap} iteration order (and thus naming, declaration hoisting, and
 * loop shaping) varied between runs.
 */
public class DecompileDeterminismTest {

    private String decompile(byte[] bytes) throws Exception {
        return new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
    }

    @Test
    public void decompilationIsDeterministicAcrossRuns() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/DemoJar.jar")) {
            if (is == null) {
                System.out.println("DemoJar.jar not found in resources; skipping");
                return;
            }
            JarInputStream jar = new JarInputStream(is);
            JarEntry entry;
            int checked = 0;
            List<String> mismatches = new ArrayList<>();
            while ((entry = jar.getNextJarEntry()) != null) {
                if (!entry.getName().endsWith(".class")) continue;
                byte[] bytes = jar.readAllBytes();

                String first = decompile(bytes);
                String second = decompile(bytes);
                String third = decompile(bytes);

                checked++;
                if (!first.equals(second) || !second.equals(third)) {
                    mismatches.add(entry.getName());
                }
            }

            assertTrue(checked > 0, "expected at least one class in DemoJar.jar");
            assertTrue(mismatches.isEmpty(),
                    "Decompilation was non-deterministic for: " + mismatches);
        }
    }
}
