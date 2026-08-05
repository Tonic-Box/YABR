package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Concurrent memoization cache for class, method, field, and assignability lookups.
 */
public class ResolutionCache
{

    private final ConcurrentMap<String, ClassFile> classCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ResolvedMethod> methodCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ResolvedField> fieldCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> assignabilityCache = new ConcurrentHashMap<>();

    /**
     * Returns the cached class, loading and caching it on a miss.
     * @param name cache key
     * @param loader supplier invoked on a miss
     * @return the cached or newly loaded class file
     */
    public ClassFile getClass(String name, Supplier<ClassFile> loader)
    {
        return classCache.computeIfAbsent(name, k -> loader.get());
    }

    /**
     * Returns the cached method resolution, resolving and caching it on a miss.
     * @param key cache key
     * @param resolver supplier invoked on a miss
     * @return the cached or newly resolved method
     */
    public ResolvedMethod getMethod(String key, Supplier<ResolvedMethod> resolver)
    {
        return methodCache.computeIfAbsent(key, k -> resolver.get());
    }

    /**
     * Returns the cached field resolution, resolving and caching it on a miss.
     * @param key cache key
     * @param resolver supplier invoked on a miss
     * @return the cached or newly resolved field
     */
    public ResolvedField getField(String key, Supplier<ResolvedField> resolver)
    {
        return fieldCache.computeIfAbsent(key, k -> resolver.get());
    }

    /**
     * Returns the cached assignability verdict, computing and caching it on a miss.
     * @param key cache key
     * @param computer supplier invoked on a miss
     * @return the cached or newly computed verdict
     */
    public Boolean getAssignability(String key, Supplier<Boolean> computer)
    {
        return assignabilityCache.computeIfAbsent(key, k -> computer.get());
    }

    /**
     * Empties all four caches.
     */
    public void clear()
    {
        classCache.clear();
        methodCache.clear();
        fieldCache.clear();
        assignabilityCache.clear();
    }

    /**
     * @return the total number of entries across all caches
     */
    public int size()
    {
        return classCache.size() + methodCache.size() + fieldCache.size() + assignabilityCache.size();
    }
}
