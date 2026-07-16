package com.tonic.analysis.oracle;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Report-mode corpus run of the recovery-equivalence oracle over the demo classes. Prints an
 * equivalence rate and any NOT_EQUIVALENT counterexamples for triage. Not a pass/fail gate (a
 * NOT_EQUIVALENT is either a real recovery bug or an oracle false positive - both need investigation,
 * not a red build); the controlled positive/negative tests live in {@link RecoveryEquivalenceTest}.
 */
class RecoveryEquivalenceCorpusTest {

    private static final String DIR = "C:/Users/zacke/IdeaProjects/DemoApplication/build/classes/java/main";

    @Test
    void reportEquivalenceOverDemoCorpus() throws Exception {
        Path root = Path.of(DIR);
        if (!Files.exists(root)) {
            System.out.println("[oracle corpus] demo dir absent - skipped");
            return;
        }
        List<Path> classFiles;
        try (Stream<Path> s = Files.walk(root)) {
            classFiles = s.filter(p -> p.toString().endsWith(".class")).sorted().collect(Collectors.toList());
        }

        ClassPool pool = new ClassPool();
        List<ClassFile> cfs = new ArrayList<>();
        for (Path p : classFiles) {
            cfs.add(pool.loadClass(new ByteArrayInputStream(Files.readAllBytes(p))));
        }

        RecoveryEquivalenceOracle oracle = new RecoveryEquivalenceOracle(6, 0x9E3779B9L, 150_000);
        int equivalent = 0, notEquivalent = 0, inconclusive = 0;
        List<String> counterexamples = new ArrayList<>();

        for (ClassFile cf : cfs) {
            for (RecoveryEquivalenceOracle.MethodVerdict mv : oracle.checkClass(cf, pool)) {
                switch (mv.verdict.kind) {
                    case EQUIVALENT: equivalent++; break;
                    case INCONCLUSIVE: inconclusive++; break;
                    case NOT_EQUIVALENT:
                        notEquivalent++;
                        if (counterexamples.size() < 40) {
                            counterexamples.add(cf.getClassName() + "#" + mv.method + " -> " + mv.verdict.detail);
                        }
                        break;
                }
            }
        }

        int total = equivalent + notEquivalent + inconclusive;
        System.out.println("[oracle corpus] classes=" + cfs.size() + " methods=" + total
                + "  EQUIVALENT=" + equivalent + "  NOT_EQUIVALENT=" + notEquivalent
                + "  INCONCLUSIVE=" + inconclusive);
        for (String c : counterexamples) {
            System.out.println("  NOT_EQUIVALENT: " + c);
        }
    }
}
