package com.tonic.analysis.source;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.ConstructorDecl;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.lower.TypeResolver;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.verifier.VerificationError;
import com.tonic.analysis.verifier.Verifier;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
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
                if (!recompile(cf, pool, d1, name)) {
                    skipped.add(name); // non-class type (enum/interface/annotation) - out of scope for now
                    continue;
                }
                verified = Verifier.builder().classPool(pool).build().verify(cf).getErrors()
                        .stream().noneMatch(VerificationError::isError);
                d2 = ClassDecompiler.decompile(cf);
                recompile(cf, pool, d2, name);
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

    /**
     * Lowers every method and constructor of the parsed source back into {@code cf} (keeping the original
     * clinit). Returns false (without recompiling) when the primary type is not a plain class.
     */
    private static boolean recompile(ClassFile cf, ClassPool pool, String source, String owner) throws Exception {
        if (source.contains("@interface ")) {
            return false; // annotation type - out of scope, and the parser rejects @interface
        }
        CompilationUnit cu = JavaParser.create().parse(source);
        if (!(cu.getPrimaryType() instanceof ClassDecl)) {
            return false;
        }
        ClassDecl decl = (ClassDecl) cu.getPrimaryType();
        TypeResolver resolver = new TypeResolver(pool, owner);
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(decl);
        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(cf.getConstPool());

        for (MethodDecl md : decl.getMethods()) {
            if (md.getBody() == null) {
                continue;
            }
            String d = desc(md.getParameters(), resolver.descriptorOf(md.getReturnType()), resolver);
            MethodEntry target = find(cf, md.getName(), d);
            if (target != null) {
                ssa.lower(lowerer.lower(md, owner), target);
            }
        }
        for (ConstructorDecl ctor : decl.getConstructors()) {
            if (ctor.getBody() == null) {
                continue;
            }
            String d = desc(ctor.getParameters(), "V", resolver);
            MethodEntry target = find(cf, "<init>", d);
            if (target == null) {
                continue;
            }
            MethodDecl init = new MethodDecl("<init>", VoidSourceType.INSTANCE).withModifiers(ctor.getModifiers());
            for (ParameterDecl pp : ctor.getParameters()) {
                init.addParameter(pp);
            }
            init.withBody(ctor.getBody());
            ssa.lower(lowerer.lower(init, owner), target);
        }
        cf.rebuild();
        return true;
    }

    private static String desc(List<ParameterDecl> params, String ret, TypeResolver resolver) {
        StringBuilder d = new StringBuilder("(");
        for (ParameterDecl pp : params) {
            d.append(resolver.descriptorOf(pp.getType()));
        }
        return d.append(")").append(ret).toString();
    }

    private static MethodEntry find(ClassFile cf, String name, String desc) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name) && m.getDesc().contentEquals(desc)) {
                return m;
            }
        }
        return null;
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
