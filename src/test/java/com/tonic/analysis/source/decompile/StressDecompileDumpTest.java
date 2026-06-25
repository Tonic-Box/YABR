package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Decompiles every stress-test class; a thrown exception is itself a decompiler bug. */
public class StressDecompileDumpTest {

    @Test
    public void dumpAll() throws Exception {
        Path classesDir = Paths.get("stress-test/classes");
        Path outDir = Paths.get("stress-test/decompiled");
        Files.createDirectories(outDir);

        List<Path> classes = new ArrayList<>();
        Files.walk(classesDir).filter(p -> p.toString().endsWith(".class")).forEach(classes::add);

        int crashes = 0;
        for (Path p : classes) {
            String fileName = p.getFileName().toString();
            String className = fileName.substring(0, fileName.length() - ".class".length());
            try {
                byte[] bytes = Files.readAllBytes(p);
                String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
                Files.write(outDir.resolve(className + ".dec.txt"), src.getBytes());
                if (!className.contains("$")) {
                    Files.write(outDir.resolve(className + ".java"), src.getBytes());
                }
            } catch (Throwable t) {
                crashes++;
                System.out.println("DECOMPILE CRASH: " + className + " -> " + t);
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                Files.write(outDir.resolve(className + ".CRASH.txt"),
                        (className + "\n" + sw).getBytes());
            }
        }
        System.out.println("STRESS: decompiled " + classes.size() + " classes, " + crashes + " crashes -> "
                + outDir.toAbsolutePath());
    }
}
