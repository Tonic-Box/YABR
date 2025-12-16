package com.tonic.analysis.similarity;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.util.*;
import java.util.function.Consumer;

/**
 * Analyzes methods for similarity to find duplicates and patterns.
 */
public class MethodSimilarityAnalyzer {

    private final ClassPool classPool;
    private final List<MethodSignature> signatures = new ArrayList<>();
    private Consumer<String> progressCallback;

    public MethodSimilarityAnalyzer(ClassPool classPool) {
        this.classPool = classPool;
    }

    /**
     * Set a callback to receive progress updates.
     */
    public void setProgressCallback(Consumer<String> callback) {
        this.progressCallback = callback;
    }

    /**
     * Build signatures for all methods in the class pool.
     */
    public void buildIndex() {
        signatures.clear();

        if (classPool == null || classPool.getClasses() == null) {
            return;
        }

        int methodCount = 0;
        for (ClassFile cf : classPool.getClasses()) {
            String className = cf.getClassName();

            for (MethodEntry method : cf.getMethods()) {
                if (method.getCodeAttribute() == null) {
                    continue; // Skip abstract/native methods
                }

                try {
                    MethodSignature sig = MethodSignature.fromMethod(method, className);
                    if (sig.getInstructionCount() > 0) {
                        signatures.add(sig);
                        methodCount++;
                    }
                } catch (Exception e) {
                    // Skip methods that fail to analyze
                }
            }

            if (progressCallback != null && methodCount % 100 == 0) {
                progressCallback.accept("Indexed " + methodCount + " methods...");
            }
        }

        if (progressCallback != null) {
            progressCallback.accept("Index built: " + signatures.size() + " methods");
        }
    }

    /**
     * Find all pairs of similar methods above a threshold.
     */
    public List<SimilarityResult> findAllSimilar(SimilarityMetric metric, double minScore) {
        List<SimilarityResult> results = new ArrayList<>();

        int n = signatures.size();
        int comparisons = 0;
        int total = (n * (n - 1)) / 2;

        for (int i = 0; i < n; i++) {
            MethodSignature sig1 = signatures.get(i);

            for (int j = i + 1; j < n; j++) {
                MethodSignature sig2 = signatures.get(j);

                // Skip self-comparisons within the same class/method
                if (sig1.getClassName().equals(sig2.getClassName()) &&
                    sig1.getMethodName().equals(sig2.getMethodName())) {
                    continue;
                }

                SimilarityResult result = compare(sig1, sig2, metric);
                if (result.getOverallScore() >= minScore) {
                    results.add(result);
                }

                comparisons++;
                if (progressCallback != null && comparisons % 10000 == 0) {
                    int percent = (comparisons * 100) / total;
                    progressCallback.accept("Comparing... " + percent + "% (" + results.size() + " matches)");
                }
            }
        }

        // Sort by score descending
        Collections.sort(results);

        if (progressCallback != null) {
            progressCallback.accept("Found " + results.size() + " similar method pairs");
        }

        return results;
    }

    /**
     * Find methods similar to a specific method.
     */
    public List<SimilarityResult> findSimilarTo(String className, String methodName, String descriptor,
                                                SimilarityMetric metric, double minScore) {
        // Find the target signature
        MethodSignature target = null;
        for (MethodSignature sig : signatures) {
            if (sig.getClassName().equals(className) &&
                sig.getMethodName().equals(methodName) &&
                sig.getDescriptor().equals(descriptor)) {
                target = sig;
                break;
            }
        }

        if (target == null) {
            return Collections.emptyList();
        }

        return findSimilarTo(target, metric, minScore);
    }

    /**
     * Find methods similar to a given signature.
     */
    public List<SimilarityResult> findSimilarTo(MethodSignature target, SimilarityMetric metric, double minScore) {
        List<SimilarityResult> results = new ArrayList<>();

        for (MethodSignature sig : signatures) {
            if (sig == target) continue;

            SimilarityResult result = compare(target, sig, metric);
            if (result.getOverallScore() >= minScore) {
                results.add(result);
            }
        }

        Collections.sort(results);
        return results;
    }

    /**
     * Compare two methods and compute similarity scores.
     */
    public SimilarityResult compare(MethodSignature sig1, MethodSignature sig2, SimilarityMetric primaryMetric) {
        Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);

        // Compute all individual metrics
        scores.put(SimilarityMetric.EXACT_BYTECODE, sig1.compareExactBytecode(sig2));
        scores.put(SimilarityMetric.OPCODE_SEQUENCE, sig1.compareOpcodeSequence(sig2));
        scores.put(SimilarityMetric.STRUCTURAL, sig1.compareStructural(sig2));

        return new SimilarityResult(sig1, sig2, scores, primaryMetric);
    }

    /**
     * Find potential duplicates (very high similarity).
     */
    public List<SimilarityResult> findDuplicates() {
        return findAllSimilar(SimilarityMetric.COMBINED, 0.95);
    }

    /**
     * Find methods that might be renamed copies (obfuscation).
     */
    public List<SimilarityResult> findRenamedCopies() {
        List<SimilarityResult> results = new ArrayList<>();

        for (SimilarityResult result : findAllSimilar(SimilarityMetric.OPCODE_SEQUENCE, 0.90)) {
            // High opcode similarity but different names suggests renaming
            if (!result.getMethod1().getMethodName().equals(result.getMethod2().getMethodName())) {
                results.add(result);
            }
        }

        return results;
    }

    /**
     * Get groups of similar methods.
     */
    public List<List<MethodSignature>> findSimilarityGroups(SimilarityMetric metric, double minScore) {
        List<SimilarityResult> pairs = findAllSimilar(metric, minScore);

        // Build adjacency map
        Map<MethodSignature, Set<MethodSignature>> adjacency = new HashMap<>();
        for (SimilarityResult result : pairs) {
            adjacency.computeIfAbsent(result.getMethod1(), k -> new HashSet<>()).add(result.getMethod2());
            adjacency.computeIfAbsent(result.getMethod2(), k -> new HashSet<>()).add(result.getMethod1());
        }

        // Find connected components
        List<List<MethodSignature>> groups = new ArrayList<>();
        Set<MethodSignature> visited = new HashSet<>();

        for (MethodSignature sig : adjacency.keySet()) {
            if (visited.contains(sig)) continue;

            List<MethodSignature> group = new ArrayList<>();
            Queue<MethodSignature> queue = new LinkedList<>();
            queue.add(sig);

            while (!queue.isEmpty()) {
                MethodSignature current = queue.poll();
                if (visited.add(current)) {
                    group.add(current);
                    Set<MethodSignature> neighbors = adjacency.get(current);
                    if (neighbors != null) {
                        queue.addAll(neighbors);
                    }
                }
            }

            if (group.size() > 1) {
                groups.add(group);
            }
        }

        // Sort groups by size (largest first)
        groups.sort((a, b) -> Integer.compare(b.size(), a.size()));

        return groups;
    }

    /**
     * Get all method signatures.
     */
    public List<MethodSignature> getSignatures() {
        return Collections.unmodifiableList(signatures);
    }

    /**
     * Get count of indexed methods.
     */
    public int getMethodCount() {
        return signatures.size();
    }

    /**
     * Get a signature by method reference.
     */
    public MethodSignature getSignature(String className, String methodName, String descriptor) {
        for (MethodSignature sig : signatures) {
            if (sig.getClassName().equals(className) &&
                sig.getMethodName().equals(methodName) &&
                sig.getDescriptor().equals(descriptor)) {
                return sig;
            }
        }
        return null;
    }
}
