package com.tonic.analysis.source;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.lower.TypeResolver;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sweep: load every DemoApplication class, decompile each top-level user class with YABR, recompile it through the
 * YABR pipeline (mirroring JStudio's {@code SourceCompiler.lowerToClassFile}), then load every recompiled class under
 * a classloader to force JVM link/verification. Reports each class that fails to recompile or verify - flushing out
 * decompile->recompile fidelity bugs in one pass instead of one-per-run.
 */
public class DemoJarRecompileSweepTest {

    private static final String CLASSES_DIR =
        "C:/Users/zacke/IdeaProjects/DemoApplication/build/classes/java/main";

    @Test
    void sweepDemoApplication() throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path classesDir = Path.of(CLASSES_DIR);
        if (!Files.isDirectory(classesDir)) {
            System.out.println("DemoApplication classes not found at " + CLASSES_DIR + " - skipping sweep");
            return;
        }

        ClassPool pool = new ClassPool();
        Map<String, byte[]> out = new LinkedHashMap<>();
        List<Path> classFiles;
        try (var s = Files.walk(classesDir)) {
            classFiles = s.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
        }
        for (Path p : classFiles) {
            byte[] b = Files.readAllBytes(p);
            ClassFile cf = pool.loadClass(b);
            out.put(cf.getClassName(), b);
        }

        List<String> topLevel = pool.getClasses().stream()
            .filter(c -> (c.getAccess() & 0x0200) == 0)   // skip interfaces / @interface (not recompilable)
            .map(ClassFile::getClassName)
            .filter(n -> n.startsWith("osrs/dev/") && !n.contains("$"))
            .sorted()
            .collect(Collectors.toList());

        Map<String, String> failures = new LinkedHashMap<>();

        for (String name : topLevel) {
            ClassFile cf = pool.get(name);
            try {
                String src = ClassDecompiler.decompile(cf);
                Path srcFile = Path.of("build", "sweep-src", name + ".java");
                Files.createDirectories(srcFile.getParent());
                Files.writeString(srcFile, src);
                byte[] recompiled = recompile(cf, pool, src, name, failures);
                out.put(name, recompiled);
            } catch (Throwable t) {
                failures.put(name, "recompile threw: " + t);
            }
        }

        Path tmp = Path.of("build", "sweep-out");
        if (Files.isDirectory(tmp)) {
            try (var s = Files.walk(tmp)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        Files.createDirectories(tmp);
        System.out.println("recompiled output dir: " + tmp.toAbsolutePath());
        for (var e : out.entrySet()) {
            Path f = tmp.resolve(e.getKey() + ".class");
            Files.createDirectories(f.getParent());
            Files.write(f, e.getValue());
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[]{tmp.toUri().toURL()}, getClass().getClassLoader())) {
            for (String name : topLevel) {
                String binary = name.replace('/', '.');
                try {
                    Class.forName(binary, true, loader);
                } catch (ExceptionInInitializerError ie) {
                    // class linked + verified OK; its static initializer threw (headless/env) - not a recompile bug
                } catch (LinkageError le) {
                    // VerifyError / NoClassDefFoundError / etc. at link-verify time = a recompile fidelity bug
                    failures.merge(name, "verify: " + le, (a, b) -> a);
                } catch (Throwable t) {
                    // any other throwable here means it got past verification
                }
            }
        }

        System.out.println("=== DemoApplication recompile sweep: " + topLevel.size()
            + " top-level classes, " + failures.size() + " failing ===");
        failures.forEach((k, v) -> System.out.println("FAIL  " + k + "\n      " + v));
        assertTrue(failures.isEmpty(),
            failures.size() + " classes failed decompile->recompile->verify: " + failures.keySet());
    }

    /** Mirrors SourceCompiler.lowerToClassFile: re-lower each method of the decompiled source into the class. */
    private static byte[] recompile(ClassFile original, ClassPool pool, String source,
                                    String name, Map<String, String> failures) throws Exception {
        CompilationUnit cu = com.tonic.analysis.source.parser.JavaParser.create().parse(source);
        if (!(cu.getPrimaryType() instanceof ClassDecl)) {
            return original.write();   // interfaces/enums/annotations: leave as-is
        }
        ClassDecl classDecl = (ClassDecl) cu.getPrimaryType();
        String ownerClass = original.getClassName();

        TypeResolver resolver = new TypeResolver(pool, ownerClass);
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(classDecl);

        ASTLowerer lowerer = new ASTLowerer(original.getConstPool(), pool);
        lowerer.setCurrentClassDecl(classDecl);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(original.getConstPool());

        for (MethodDecl md : classDecl.getMethods()) {
            if (md.getBody() == null) {
                continue;
            }
            String desc = descriptorOf(md, resolver);
            MethodEntry target = findMethod(original, md.getName(), desc);
            if (target == null) {
                continue;   // a synthetic/new method we won't touch
            }
            try {
                IRMethod ir = lowerer.lower(md, ownerClass);
                ssa.lower(ir, target);
            } catch (Throwable t) {
                failures.merge(name, "lower " + md.getName() + desc + ": " + t, (a, b) -> a + " | " + b);
            }
        }

        original.rebuild();
        return original.write();
    }

    private static String descriptorOf(MethodDecl md, TypeResolver resolver) {
        StringBuilder sb = new StringBuilder("(");
        for (ParameterDecl p : md.getParameters()) {
            sb.append(resolver.descriptorOf(p.getType()));
        }
        sb.append(")").append(resolver.descriptorOf(md.getReturnType()));
        return sb.toString();
    }

    private static MethodEntry findMethod(ClassFile cf, String methodName, String desc) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(methodName) && m.getDesc().equals(desc)) {
                return m;
            }
        }
        return null;
    }
}
