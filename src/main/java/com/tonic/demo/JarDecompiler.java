package com.tonic.demo;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.decompile.DecompilerConfig;
import com.tonic.analysis.source.decompile.TransformPreset;
import com.tonic.parser.ClassFile;
import com.tonic.utill.ClassNameUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Utility to decompile all classes in a JAR file to Java source files.
 *
 * Usage: java JarDecompiler &lt;input.jar&gt; &lt;output-dir&gt;
 *
 * The output directory will be cleared before decompilation begins.
 * Classes are written to subdirectories matching their package structure.
 */
public class JarDecompiler {

    private final Path inputJar;
    private final Path outputDir;
    private final DecompilerConfig config;

    private int successCount = 0;
    private int failCount = 0;

    public JarDecompiler(Path inputJar, Path outputDir) {
        this(inputJar, outputDir, DecompilerConfig.defaults());
    }

    public JarDecompiler(Path inputJar, Path outputDir, DecompilerConfig config) {
        this.inputJar = inputJar;
        this.outputDir = outputDir;
        this.config = config;
    }

    public void decompile() throws IOException {
        if (!Files.exists(inputJar)) {
            throw new FileNotFoundException("Input JAR not found: " + inputJar);
        }

        clearDirectory(outputDir);
        Files.createDirectories(outputDir);

        System.out.println("Decompiling: " + inputJar);
        System.out.println("Output: " + outputDir);
        System.out.println();

        try (JarFile jar = new JarFile(inputJar.toFile())) {
            // First pass: inner classes (for switch map analysis)
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class") && name.contains("$")) {
                    decompileEntry(jar, entry);
                }
            }

            // Second pass: outer classes
            entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class") && !name.contains("$")) {
                    decompileEntry(jar, entry);
                }
            }
        }

        System.out.println();
        System.out.println("Decompilation complete:");
        System.out.println("  Success: " + successCount);
        System.out.println("  Failed: " + failCount);
    }

    private void decompileEntry(JarFile jar, JarEntry entry) {
        String name = entry.getName();
        String className = name.substring(0, name.length() - 6); // Remove .class

        try (InputStream is = jar.getInputStream(entry)) {
            ClassFile classFile = new ClassFile(is);

            ClassDecompiler decompiler = new ClassDecompiler(classFile, config);
            String source = decompiler.decompile();

            Path outputPath = getOutputPath(className);
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, source.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            System.out.println("  [OK] " + className);
            successCount++;

        } catch (Exception e) {
            System.err.println("  [FAIL] " + className + ": " + e.getMessage());
            failCount++;
        }
    }

    private Path getOutputPath(String className) {
        String packagePath = className.replace('/', File.separatorChar);
        String simpleClassName = ClassNameUtil.getSimpleName(className);

        // Handle inner classes - use OuterClass$InnerClass.java naming
        if (className.contains("$")) {
            int lastSlash = className.lastIndexOf('/');
            String classNamePart = lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
            String packagePart = lastSlash >= 0 ? className.substring(0, lastSlash).replace('/', File.separatorChar) : "";

            if (packagePart.isEmpty()) {
                return outputDir.resolve(classNamePart + ".java");
            }
            return outputDir.resolve(packagePart).resolve(classNamePart + ".java");
        }

        return outputDir.resolve(packagePath + ".java");
    }

    private void clearDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (!d.equals(dir)) {
                    Files.delete(d);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        System.out.println("Cleared output directory: " + dir);
    }


    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java JarDecompiler <input.jar> <output-dir> [--optimize]");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --optimize    Apply optimization transforms before decompilation");
            System.exit(1);
        }

        Path inputJar = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);

        boolean optimize = false;
        for (int i = 2; i < args.length; i++) {
            if ("--optimize".equals(args[i])) {
                optimize = true;
            }
        }

        DecompilerConfig config;
        if (optimize) {
            config = DecompilerConfig.builder()
                    .preset(TransformPreset.STANDARD)
                    .build();
            System.out.println("Using optimization preset: STANDARD");
        } else {
            config = DecompilerConfig.defaults();
        }

        try {
            JarDecompiler decompiler = new JarDecompiler(inputJar, outputDir, config);
            decompiler.decompile();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
