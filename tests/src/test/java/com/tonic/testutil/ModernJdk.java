package com.tonic.testutil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test helper that locates installed JDKs by major version and uses them to compile hand-written
 * Java fixtures (records, sealed, switch expressions, pattern switch, ...) and run emitted classes.
 *
 * <p>The in-process test JVM is Java 11 and cannot load major-60+ classes, so modern fixtures are
 * compiled with a newer JDK's {@code javac} out-of-process, and emitted classes are
 * verified/executed with a newer {@code java}. JDK homes are resolved from common locations (and an
 * optional {@code JDK<N>_HOME} env override); tests should {@code assumeTrue(ModernJdk.available(N))}
 * so they skip gracefully where the JDK is absent rather than failing.
 */
public final class ModernJdk {

    private ModernJdk() {}

    private static final List<Path> ROOTS = new ArrayList<>();
    static {
        String home = System.getProperty("user.home");
        if (home != null) ROOTS.add(Paths.get(home, ".jdks"));
        ROOTS.add(Paths.get("C:", "Program Files", "Java"));
        ROOTS.add(Paths.get("C:", "Program Files", "Amazon Corretto"));
        String extra = System.getenv("YABR_JDK_ROOT");
        if (extra != null) ROOTS.add(Paths.get(extra));
    }

    /** Resolves a JDK home whose {@code release} file reports the given major version, or null. */
    public static Path jdkHome(int major) {
        String envOverride = System.getenv("JDK" + major + "_HOME");
        if (envOverride != null && hasJavac(Paths.get(envOverride))) return Paths.get(envOverride);
        for (Path root : ROOTS) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> children = Files.list(root)) {
                for (Path dir : children.collect(Collectors.toList())) {
                    if (hasJavac(dir) && releaseMajor(dir) == major) return dir;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static boolean available(int major) {
        return jdkHome(major) != null;
    }

    private static boolean hasJavac(Path home) {
        return Files.isRegularFile(home.resolve("bin").resolve("javac.exe"))
            || Files.isRegularFile(home.resolve("bin").resolve("javac"));
    }

    private static int releaseMajor(Path home) {
        Path release = home.resolve("release");
        if (!Files.isRegularFile(release)) return -1;
        try {
            for (String line : Files.readAllLines(release)) {
                if (line.startsWith("JAVA_VERSION=")) {
                    String v = line.substring(line.indexOf('"') + 1);
                    v = v.substring(0, v.indexOf('"'));
                    // "21.0.9" -> 21 ; "1.8.0_362" -> 8
                    if (v.startsWith("1.")) return Integer.parseInt(v.split("\\.")[1]);
                    return Integer.parseInt(v.split("\\.")[0]);
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static Path bin(Path home, String tool) {
        Path exe = home.resolve("bin").resolve(tool + ".exe");
        return Files.isRegularFile(exe) ? exe : home.resolve("bin").resolve(tool);
    }

    /**
     * Compiles the given sources with the JDK for {@code major} using {@code --release major}, into
     * a fresh temp directory. Returns a map of binary class name -> class bytes for every emitted
     * .class (including synthesized/nested classes). Throws if compilation fails.
     *
     * @param sources map of public-type simple name -> source text
     */
    public static Map<String, byte[]> compile(int major, Map<String, String> sources) throws Exception {
        Path jdk = jdkHome(major);
        if (jdk == null) throw new IllegalStateException("No JDK " + major + " found");
        Path work = Files.createTempDirectory("yabr-fixt-" + major + "-");
        Path src = Files.createDirectories(work.resolve("src"));
        Path out = Files.createDirectories(work.resolve("out"));

        List<String> cmd = new ArrayList<>();
        cmd.add(bin(jdk, "javac").toString());
        cmd.add("--release");
        cmd.add(Integer.toString(major));
        cmd.add("-d");
        cmd.add(out.toString());
        for (Map.Entry<String, String> e : sources.entrySet()) {
            Path f = src.resolve(e.getKey() + ".java");
            Files.write(f, e.getValue().getBytes(StandardCharsets.UTF_8));
            cmd.add(f.toString());
        }
        runProcess(cmd, work);

        Map<String, byte[]> classes = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(out)) {
            for (Path p : walk.filter(x -> x.toString().endsWith(".class")).collect(Collectors.toList())) {
                String rel = out.relativize(p).toString().replace('\\', '/').replace(".class", "");
                classes.put(rel.replace('/', '.'), Files.readAllBytes(p));
            }
        }
        return classes;
    }

    /** Convenience for a single source file. */
    public static Map<String, byte[]> compile(int major, String simpleName, String source) throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(simpleName, source);
        return compile(major, m);
    }

    /**
     * Writes the given classes to a temp dir and runs {@code mainClass} with the JDK for {@code major}
     * using {@code -Xverify:all}, returning captured stdout. Throws if the process exits non-zero
     * (a verifier error or exception), which is exactly what round-trip verification asserts against.
     */
    public static String runVerified(int major, Map<String, byte[]> classes, String mainClass) throws Exception {
        Path jdk = jdkHome(major);
        if (jdk == null) throw new IllegalStateException("No JDK " + major + " found");
        Path dir = Files.createTempDirectory("yabr-run-" + major + "-");
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            Path p = dir.resolve(e.getKey().replace('.', '/') + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(bin(jdk, "java").toString());
        cmd.add("-Xverify:all");
        cmd.add("-cp");
        cmd.add(dir.toString());
        cmd.add(mainClass);
        return runProcess(cmd, dir);
    }

    private static String runProcess(List<String> cmd, Path workingDir) throws Exception {
        Process proc = new ProcessBuilder(cmd)
                .directory(workingDir.toFile())
                .redirectErrorStream(true)
                .start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) output.append(line).append('\n');
        }
        int code = proc.waitFor();
        if (code != 0) {
            throw new IllegalStateException("Process exited " + code + ":\n" + output);
        }
        return output.toString();
    }
}
