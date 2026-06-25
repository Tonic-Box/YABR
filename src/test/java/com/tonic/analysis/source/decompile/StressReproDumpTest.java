package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Decompiles every .class under stress-test/repros/classes to stress-test/repros/decompiled (one .java per
 * top-level class). Used by stress-test/verify.sh for decompile->recompile->run behavioral checks. No-op if the
 * input dir is absent.
 */
public class StressReproDumpTest {

    @Test
    public void dumpRepros() throws Exception {
        Path in = Paths.get("stress-test/repros/classes");
        Path out = Paths.get("stress-test/repros/decompiled");
        if (!Files.isDirectory(in)) {
            System.out.println("REPRO: no input dir " + in.toAbsolutePath() + " - skipping");
            return;
        }
        Files.createDirectories(out);
        // wipe previous outputs
        if (Files.isDirectory(out)) {
            Files.walk(out).filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
        }

        List<Path> classes = new ArrayList<>();
        Files.walk(in).filter(p -> p.toString().endsWith(".class")).forEach(classes::add);

        // Make every repro class resolvable in the default pool so cross-class lookups (e.g. enum-switch
        // $SwitchMap$ holders in synthetic sibling classes) work regardless of decompile order.
        ClassPool pool = ClassPool.getDefault();
        for (Path p : classes) {
            try {
                pool.loadClass(Files.readAllBytes(p));
            } catch (Throwable ignored) {
            }
        }

        int crashes = 0;
        for (Path p : classes) {
            String name = p.getFileName().toString();
            name = name.substring(0, name.length() - ".class".length());
            try {
                byte[] bytes = Files.readAllBytes(p);
                String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
                if (!name.contains("$")) {
                    Files.write(out.resolve(name + ".java"), src.getBytes());
                }
                Files.write(out.resolve(name + ".dec.txt"), src.getBytes());
            } catch (Throwable t) {
                crashes++;
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                Files.write(out.resolve(name + ".CRASH.txt"), (name + "\n" + sw).getBytes());
                System.out.println("REPRO CRASH: " + name + " -> " + t);
            }
        }
        System.out.println("REPRO: decompiled " + classes.size() + " classes, " + crashes + " crashes");
    }
}
