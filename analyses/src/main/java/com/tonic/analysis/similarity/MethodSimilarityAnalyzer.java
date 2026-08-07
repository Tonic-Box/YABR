package com.tonic.analysis.similarity;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.util.*;
import java.util.function.Consumer;

/**
 * Pairwise method-similarity analyzer over a ClassPool, used to find duplicates and renamed copies.
 */
public class MethodSimilarityAnalyzer
{

    private final ClassPool classPool;
    private final List<MethodSignature> signatures = new ArrayList<>();
    private Consumer<String> progressCallback;

    /**
     * Creates an analyzer over the given pool; call buildIndex before querying.
     * @param classPool the pool of classes to analyze
     */
    public MethodSimilarityAnalyzer(ClassPool classPool)
    {
        this.classPool = classPool;
    }

    /**
     * Sets a callback to receive progress messages during indexing and comparison.
     * @param callback the consumer of progress strings
     */
    public void setProgressCallback(Consumer<String> callback)
    {
        this.progressCallback = callback;
    }

    /**
     * Builds signatures for every method with code in the pool, replacing any previous index.
     */
    public void buildIndex()
    {
        signatures.clear();

        if (classPool == null || classPool.getClasses() == null)
        {
            return;
        }

        int methodCount = 0;
        for (ClassFile cf : classPool.getClasses())
        {
            String className = cf.getClassName();

            for (MethodEntry method : cf.getMethods())
            {
                if (method.getCodeAttribute() == null)
                {
                    continue; // Skip abstract/native methods
                }

                try
                {
                    MethodSignature sig = MethodSignature.fromMethod(method, className);
                    if (sig.getInstructionCount() > 0)
                    {
                        signatures.add(sig);
                        methodCount++;
                    }
                }
                catch (Exception e)
                {
                    // Skip methods that fail to analyze
                }
            }

            if (progressCallback != null && methodCount % 100 == 0)
            {
                progressCallback.accept("Indexed " + methodCount + " methods...");
            }
        }

        if (progressCallback != null)
        {
            progressCallback.accept("Index built: " + signatures.size() + " methods");
        }
    }

    /**
     * Compares every indexed pair and keeps those scoring at or above the threshold.
     * @param metric the metric to score with
     * @param minScore the minimum overall score to keep
     * @return matching pairs, best first
     */
    public List<SimilarityResult> findAllSimilar(SimilarityMetric metric, double minScore)
    {
        List<SimilarityResult> results = new ArrayList<>();

        int n = signatures.size();
        int comparisons = 0;
        int total = (n * (n - 1)) / 2;

        for (int i = 0; i < n; i++)
        {
            MethodSignature sig1 = signatures.get(i);

            for (int j = i + 1; j < n; j++)
            {
                MethodSignature sig2 = signatures.get(j);

                // Skip self-comparisons within the same class/method
                if (sig1.getClassName().equals(sig2.getClassName()) &&
                    sig1.getMethodName().equals(sig2.getMethodName()))
                    {
                    continue;
                }

                SimilarityResult result = compare(sig1, sig2, metric);
                if (result.getOverallScore() >= minScore)
                {
                    results.add(result);
                }

                comparisons++;
                if (progressCallback != null && comparisons % 10000 == 0)
                {
                    int percent = (comparisons * 100) / total;
                    progressCallback.accept("Comparing... " + percent + "% (" + results.size() + " matches)");
                }
            }
        }

        // Sort by score descending
        Collections.sort(results);

        if (progressCallback != null)
        {
            progressCallback.accept("Found " + results.size() + " similar method pairs");
        }

        return results;
    }

    /**
     * Finds indexed methods similar to the referenced method.
     * @param className the owning class name
     * @param methodName the method name
     * @param descriptor the method descriptor
     * @param metric the metric to score with
     * @param minScore the minimum overall score to keep
     * @return matching results, best first; empty if the method is not indexed
     */
    public List<SimilarityResult> findSimilarTo(String className, String methodName, String descriptor, SimilarityMetric metric, double minScore)
    {
        MethodSignature target = null;
        for (MethodSignature sig : signatures)
        {
            if (sig.getClassName().equals(className) &&
                sig.getMethodName().equals(methodName) &&
                sig.getDescriptor().equals(descriptor))
                {
                target = sig;
                break;
            }
        }

        if (target == null)
        {
            return Collections.emptyList();
        }

        return findSimilarTo(target, metric, minScore);
    }

    /**
     * Finds indexed methods similar to a given signature.
     * @param target the signature to match against
     * @param metric the metric to score with
     * @param minScore the minimum overall score to keep
     * @return matching results, best first
     */
    public List<SimilarityResult> findSimilarTo(MethodSignature target, SimilarityMetric metric, double minScore)
    {
        List<SimilarityResult> results = new ArrayList<>();

        for (MethodSignature sig : signatures)
        {
            if (sig == target) continue;

            SimilarityResult result = compare(target, sig, metric);
            if (result.getOverallScore() >= minScore)
            {
                results.add(result);
            }
        }

        Collections.sort(results);
        return results;
    }

    /**
     * Scores two signatures on every metric.
     * @param sig1 the first signature
     * @param sig2 the second signature
     * @param primaryMetric the metric that determines the overall score
     * @return the result holding all metric scores
     */
    public SimilarityResult compare(MethodSignature sig1, MethodSignature sig2, SimilarityMetric primaryMetric)
    {
        Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);

        scores.put(SimilarityMetric.EXACT_BYTECODE, sig1.compareExactBytecode(sig2));
        scores.put(SimilarityMetric.OPCODE_SEQUENCE, sig1.compareOpcodeSequence(sig2));
        scores.put(SimilarityMetric.STRUCTURAL, sig1.compareStructural(sig2));

        return new SimilarityResult(sig1, sig2, scores, primaryMetric);
    }

    /**
     * Finds pairs scoring at least 0.95 on the combined metric.
     * @return the potential duplicate pairs, best first
     */
    public List<SimilarityResult> findDuplicates()
    {
        return findAllSimilar(SimilarityMetric.COMBINED, 0.95);
    }

    /**
     * Finds pairs with near-identical opcode sequences but different method names.
     * @return the candidate renamed-copy pairs
     */
    public List<SimilarityResult> findRenamedCopies()
    {
        List<SimilarityResult> results = new ArrayList<>();

        for (SimilarityResult result : findAllSimilar(SimilarityMetric.OPCODE_SEQUENCE, 0.90))
        {
            // High opcode similarity but different names suggests renaming
            if (!result.getMethod1().getMethodName().equals(result.getMethod2().getMethodName()))
            {
                results.add(result);
            }
        }

        return results;
    }

    /**
     * Clusters similar methods into connected components over the pairwise similarity graph.
     * @param metric the metric to score with
     * @param minScore the minimum score for an edge
     * @return groups of two or more methods, largest first
     */
    public List<List<MethodSignature>> findSimilarityGroups(SimilarityMetric metric, double minScore)
    {
        List<SimilarityResult> pairs = findAllSimilar(metric, minScore);

        Map<MethodSignature, Set<MethodSignature>> adjacency = new HashMap<>();
        for (SimilarityResult result : pairs)
        {
            adjacency.computeIfAbsent(result.getMethod1(), k -> new HashSet<>()).add(result.getMethod2());
            adjacency.computeIfAbsent(result.getMethod2(), k -> new HashSet<>()).add(result.getMethod1());
        }

        // Find connected components
        List<List<MethodSignature>> groups = new ArrayList<>();
        Set<MethodSignature> visited = new HashSet<>();

        for (MethodSignature sig : adjacency.keySet())
        {
            if (visited.contains(sig)) continue;

            List<MethodSignature> group = new ArrayList<>();
            Queue<MethodSignature> queue = new LinkedList<>();
            queue.add(sig);

            while (!queue.isEmpty())
            {
                MethodSignature current = queue.poll();
                if (visited.add(current))
                {
                    group.add(current);
                    Set<MethodSignature> neighbors = adjacency.get(current);
                    if (neighbors != null)
                    {
                        queue.addAll(neighbors);
                    }
                }
            }

            if (group.size() > 1)
            {
                groups.add(group);
            }
        }

        groups.sort((a, b) -> Integer.compare(b.size(), a.size()));

        return groups;
    }

    /**
     * @return an unmodifiable view of the indexed signatures
     */
    public List<MethodSignature> getSignatures()
    {
        return Collections.unmodifiableList(signatures);
    }

    /**
     * @return the number of indexed methods
     */
    public int getMethodCount()
    {
        return signatures.size();
    }

    /**
     * Looks up an indexed signature by method reference.
     * @param className the owning class name
     * @param methodName the method name
     * @param descriptor the method descriptor
     * @return the matching signature, or null if not indexed
     */
    public MethodSignature getSignature(String className, String methodName, String descriptor)
    {
        for (MethodSignature sig : signatures)
        {
            if (sig.getClassName().equals(className) &&
                sig.getMethodName().equals(methodName) &&
                sig.getDescriptor().equals(descriptor))
                {
                return sig;
            }
        }
        return null;
    }
}
