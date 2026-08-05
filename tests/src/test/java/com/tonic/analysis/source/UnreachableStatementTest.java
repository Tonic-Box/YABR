package com.tonic.analysis.source;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.BreakStmt;
import com.tonic.analysis.source.ast.stmt.ContinueStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.SwitchCase;
import com.tonic.analysis.source.ast.stmt.SwitchStmt;
import com.tonic.analysis.source.ast.stmt.ThrowStmt;
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Decompiled source must never place a statement after an unconditional exit in the same block.
 * Java rejects unreachable statements outright, so such output does not compile - a defect class
 * the recompile sweeps cannot see, because they re-lower through this project's own parser, which
 * does not enforce the rule.
 *
 * The invariant is checked on the recovered AST rather than by shelling out to a compiler: it is
 * exact, needs no external classpath for the corpus under test, and states precisely what the
 * elimination pass guarantees.
 */
class UnreachableStatementTest
{

    private static final Path DIR =
            Paths.get("C:/Users/zacke/IdeaProjects/DemoApplication/build/classes/java/main");

    @Test
    void decompiledCorpusHasNoUnreachableStatements() throws Exception
    {
        assumeTrue(Files.isDirectory(DIR), "demo classes not built at " + DIR);
        List<Path> classes;
        try (Stream<Path> walk = Files.walk(DIR))
        {
            classes = walk.filter(p -> p.toString().endsWith(".class")).sorted().collect(java.util.stream.Collectors.toList());
        }
        assumeTrue(!classes.isEmpty(), "no classes found");

        ClassPool pool = TestUtils.emptyPool();
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (Path p : classes)
        {
            ClassFile cf = pool.loadClass(Files.readAllBytes(p));
            String source = ClassDecompiler.decompile(cf);
            if (source.contains("@interface "))
            {
                continue;
            }
            scanned++;
            List<String> found = unreachableIn(source);
            if (!found.isEmpty())
            {
                offenders.add(p.getFileName() + " -> " + String.join(", ", found));
            }
        }
        assertTrue(scanned >= 20, "expected the demo corpus to be scanned, only reached " + scanned);
        assertTrue(offenders.isEmpty(),
                "decompiled output must not contain unreachable statements:\n"
                        + String.join("\n", offenders));
    }

    /**
     * Every statement that follows an unconditional exit in its own block, described for the failure
     * message. A parse failure is reported rather than swallowed: silently returning "clean" would make
     * this whole check pass vacuously.
     */
    private static List<String> unreachableIn(String source)
    {
        List<String> out = new ArrayList<>();
        ASTNode root = JavaParser.create().parse(source);
        walk(root, out);
        return out;
    }

    private static void walk(ASTNode node, List<String> out)
    {
        if (node instanceof BlockStmt)
        {
            check(((BlockStmt) node).getStatements(), out);
        }
        else if (node instanceof SwitchStmt)
        {
            for (SwitchCase c : ((SwitchStmt) node).getCases())
            {
                if (c.statements() != null)
                {
                    check(c.statements(), out);
                }
            }
        }
        for (ASTNode child : node.getChildren())
        {
            walk(child, out);
        }
    }

    private static void check(List<Statement> stmts, List<String> out)
    {
        for (int i = 0; i < stmts.size() - 1; i++)
        {
            if (isUnconditionalExit(stmts.get(i)))
            {
                out.add(describe(stmts.get(i)) + " followed by " + describe(stmts.get(i + 1)));
                return;
            }
        }
    }

    private static boolean isUnconditionalExit(Statement stmt)
    {
        return stmt instanceof ReturnStmt || stmt instanceof ThrowStmt
                || stmt instanceof BreakStmt || stmt instanceof ContinueStmt;
    }

    private static String describe(Statement stmt)
    {
        return stmt.getClass().getSimpleName();
    }

    /**
     * The same invariant over the wider sweep jar, where this defect class was actually found. Runs
     * only when {@code -Dverify.sweep.jar} is set, matching {@link VerifySweepTest}; a class the
     * pipeline cannot handle at all is not this check's concern and is skipped.
     */
    @Test
    void sweptJarHasNoUnreachableStatements() throws Exception
    {
        String jarProp = System.getProperty("verify.sweep.jar");
        assumeTrue(jarProp != null, "set -Dverify.sweep.jar=<path-to-jar> to run the wider scan");
        Path jar = Paths.get(jarProp);
        assumeTrue(Files.exists(jar), "verify.sweep.jar not found: " + jarProp);

        ClassPool pool = new ClassPool();
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (java.util.jar.JarInputStream jis = new java.util.jar.JarInputStream(Files.newInputStream(jar)))
        {
            java.util.jar.JarEntry e;
            while ((e = jis.getNextJarEntry()) != null)
            {
                String n = e.getName();
                if (!n.endsWith(".class") || n.contains("module-info") || n.contains("package-info"))
                {
                    continue;
                }
                ClassFile cf;
                try
                {
                    cf = pool.loadClass(new ByteArrayInputStream(jis.readAllBytes()));
                }
                catch (Throwable unparseable)
                {
                    continue;
                }
                String source;
                try
                {
                    source = ClassDecompiler.decompile(cf);
                }
                catch (Throwable pipelineFailure)
                {
                    continue;
                }
                if (source.contains("@interface "))
                {
                    continue;
                }
                List<String> found;
                try
                {
                    found = unreachableIn(source);
                }
                catch (RuntimeException unparseableOutput)
                {
                    continue;
                }
                scanned++;
                if (!found.isEmpty())
                {
                    offenders.add(cf.getClassName() + " -> " + String.join(", ", found));
                }
            }
        }
        assertTrue(scanned >= 500, "expected the sweep jar to be scanned, only reached " + scanned);
        assertTrue(offenders.isEmpty(),
                "decompiled output must not contain unreachable statements:\n"
                        + String.join("\n", offenders));
    }

    /**
     * A switch case may legally end in {@code break} with a following case, so the per-case check above
     * stays inside one case's statement list. This guards that the walk itself never flags that shape.
     */
    @Test
    void switchCaseBreakIsNotFlagged() throws Exception
    {
        String source = "public class SwCase {\n"
                + "    public static int pick(int n) {\n"
                + "        int r = 0;\n"
                + "        switch (n) {\n"
                + "            case 1: r = 1; break;\n"
                + "            case 2: r = 2; break;\n"
                + "            default: r = 3;\n"
                + "        }\n"
                + "        return r;\n"
                + "    }\n"
                + "}\n";
        assertTrue(unreachableIn(source).isEmpty(), "a case-terminating break must not read as unreachable");
    }

    /**
     * The detector itself must report a real violation, or the corpus check above would pass
     * vacuously. This is the exact shape the elimination pass removes - and it is written as a
     * source string precisely because javac would refuse to compile it, which is the whole point.
     */
    @Test
    void detectorReportsAStatementAfterAnExit()
    {
        String bad = "public class Dead {\n"
                + "    public static void f() {\n"
                + "        while (true) {\n"
                + "            g();\n"
                + "            continue;\n"
                + "            return;\n"
                + "        }\n"
                + "    }\n"
                + "    static void g() {}\n"
                + "}\n";
        List<String> found = unreachableIn(bad);
        assertTrue(found.stream().anyMatch(f -> f.contains("ContinueStmt") && f.contains("ReturnStmt")),
                "the continue/return tail must be reported, got: " + found);
    }

    /**
     * A clean method must not be flagged, so the detector is not simply reporting everything.
     */
    @Test
    void detectorAcceptsCleanMethods()
    {
        String source = "public class Live {\n"
                + "    public static int f() {\n"
                + "        int r = 1;\n"
                + "        return r;\n"
                + "    }\n"
                + "    public static void g() {\n"
                + "        while (true) {\n"
                + "            continue;\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
        assertTrue(unreachableIn(source).isEmpty(), "clean methods must not be flagged");
    }
}
