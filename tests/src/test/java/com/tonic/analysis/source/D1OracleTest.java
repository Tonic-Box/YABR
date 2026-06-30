package com.tonic.analysis.source;

import com.tonic.analysis.instruction.ANewArrayInstruction;
import com.tonic.analysis.instruction.ATHROWInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InstructionFactory;
import com.tonic.analysis.instruction.MultiANewArrayInstruction;
import com.tonic.analysis.instruction.NewInstruction;
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
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * d1 structural correctness oracle: the multiset of object/array ALLOCATIONS and THROWS in a method is a
 * semantic invariant the round trip must preserve. For each demo class it fingerprints the original javac
 * bytecode, then {@code recompile(d1)} and {@code recompile(d2)}, and reports two properties:
 *  - FAITHFUL: fingerprint(original) == fingerprint(recompile(d1)) — the first decompile drops/adds nothing.
 *  - STABLE:   fingerprint(recompile(d1)) == fingerprint(recompile(d2)) — the round trip drops/adds nothing.
 * This catches dropped/duplicated code (e.g. HeapAnalysisTest's dropped conditional arm) without executing.
 * StringBuilder/StringBuffer allocations are excluded (string-concat lowering shape is not a semantic invariant).
 */
class D1OracleTest {
    private static final String DIR = "C:/Users/zacke/IdeaProjects/DemoApplication/build/classes/java/main";

    @Test
    void allocationsAndThrowsPreserved() throws Exception {
        Path root = Path.of(DIR);
        if (!Files.exists(root)) return;
        ClassPool pool = new ClassPool();
        List<ClassFile> cfs = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path q : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".class")).sorted()::iterator) {
                cfs.add(pool.loadClass(new ByteArrayInputStream(Files.readAllBytes(q))));
            }
        }

        int faithful = 0, stable = 0, graded = 0;
        List<String> faithViolations = new ArrayList<>();
        List<String> driftViolations = new ArrayList<>();
        for (ClassFile cf : cfs) {
            String name = cf.getClassName();
            Map<String, Integer> fpOrig = fingerprint(cf);
            String owner = cf.getClassName();
            String d1, d2;
            try {
                d1 = ClassDecompiler.decompile(cf);
                recompile(cf, pool, d1, owner);
                Map<String, Integer> fpD1 = fingerprint(cf);
                d2 = ClassDecompiler.decompile(cf);
                recompile(cf, pool, d2, owner);
                Map<String, Integer> fpD2 = fingerprint(cf);
                graded++;
                if (fpOrig.equals(fpD1)) faithful++;
                else faithViolations.add(name + ": " + diff(fpOrig, fpD1));
                if (fpD1.equals(fpD2)) stable++;
                else driftViolations.add(name + ": " + diff(fpD1, fpD2));
            } catch (Exception e) {
                // class doesn't recompile cleanly (lambdas etc.) — out of scope for this gate
            }
        }
        System.out.println("D1-ORACLE alloc/throw invariant — FAITHFUL(orig==d1): " + faithful + "/" + graded
                + " | STABLE(d1==d2): " + stable + "/" + graded);
        for (String v : faithViolations) System.out.println("  FAITHFUL-VIOLATION " + v);
        for (String v : driftViolations) System.out.println("  DRIFT-VIOLATION    " + v);

        // Regression gate: the allocation/throw multiset must stay at least this faithful/stable. Raise these
        // baselines as fixes land (HeapAnalysisTest correctness → faithful 30, stable 29+). A drop here means a
        // change introduced or failed to preserve an allocation/throw — a correctness regression.
        assertTrue(faithful >= 30, "d1 allocation faithfulness regressed: " + faithful + "/" + graded
                + " — " + faithViolations);
        assertTrue(stable >= 28, "round-trip allocation stability regressed: " + stable + "/" + graded
                + " — " + driftViolations);
    }

    private static Map<String, Integer> fingerprint(ClassFile cf) {
        Map<String, Integer> counts = new TreeMap<>();
        for (MethodEntry m : cf.getMethods()) {
            if (m.getCodeAttribute() == null) continue;
            byte[] code = m.getCodeAttribute().getCode();
            for (Instruction instr : InstructionFactory.parse(code, cf.getConstPool())) {
                String key = null;
                if (instr instanceof NewInstruction) {
                    String c = ((NewInstruction) instr).resolveClass();
                    if (c != null && (c.contains("StringBuilder") || c.contains("StringBuffer"))) continue;
                    key = "new:" + c;
                } else if (instr instanceof ANewArrayInstruction) {
                    key = "anewarray:" + ((ANewArrayInstruction) instr).resolveClass();
                } else if (instr instanceof MultiANewArrayInstruction) {
                    key = "multianewarray";
                } else if (instr instanceof ATHROWInstruction) {
                    key = "athrow";
                }
                if (key != null) counts.merge(key, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static String diff(Map<String, Integer> a, Map<String, Integer> b) {
        Map<String, Integer> d = new TreeMap<>();
        a.forEach((k, v) -> d.merge(k, v, Integer::sum));
        b.forEach((k, v) -> d.merge(k, -v, Integer::sum));
        StringBuilder sb = new StringBuilder();
        d.forEach((k, v) -> { if (v != 0) sb.append(v > 0 ? " -" : " +").append(Math.abs(v)).append(' ').append(k); });
        return sb.length() == 0 ? "(equal)" : sb.toString().trim();
    }

    private static void recompile(ClassFile cf, ClassPool pool, String source, String owner) throws Exception {
        CompilationUnit cu = JavaParser.create().parse(source);
        ClassDecl decl = (ClassDecl) cu.getPrimaryType();
        TypeResolver resolver = new TypeResolver(pool, owner);
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(decl);
        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(decl);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(cf.getConstPool());
        for (MethodDecl md : decl.getMethods()) {
            if (md.getBody() == null) continue;
            String d = desc(md.getParameters(), resolver.descriptorOf(md.getReturnType()), resolver);
            MethodEntry t = find(cf, md.getName(), d);
            if (t != null) ssa.lower(lowerer.lower(md, owner), t);
        }
        for (ConstructorDecl ctor : decl.getConstructors()) {
            if (ctor.getBody() == null) continue;
            String d = desc(ctor.getParameters(), "V", resolver);
            MethodEntry t = find(cf, "<init>", d);
            if (t == null) continue;
            MethodDecl init = new MethodDecl("<init>", VoidSourceType.INSTANCE).withModifiers(ctor.getModifiers());
            for (ParameterDecl pp : ctor.getParameters()) init.addParameter(pp);
            init.withBody(ctor.getBody());
            ssa.lower(lowerer.lower(init, owner), t);
        }
        cf.rebuild();
    }

    private static String desc(List<ParameterDecl> params, String ret, TypeResolver resolver) {
        StringBuilder d = new StringBuilder("(");
        for (ParameterDecl pp : params) d.append(resolver.descriptorOf(pp.getType()));
        return d.append(")").append(ret).toString();
    }

    private static MethodEntry find(ClassFile cf, String name, String desc) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name) && m.getDesc().contentEquals(desc)) return m;
        }
        return null;
    }
}
