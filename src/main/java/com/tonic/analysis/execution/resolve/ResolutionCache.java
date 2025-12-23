package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class ResolutionCache {

    private final ConcurrentMap<String, ClassFile> classCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ResolvedMethod> methodCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ResolvedField> fieldCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> assignabilityCache = new ConcurrentHashMap<>();

    public ClassFile getClass(String name, Supplier<ClassFile> loader) {
        return classCache.computeIfAbsent(name, k -> loader.get());
    }

    public ResolvedMethod getMethod(String key, Supplier<ResolvedMethod> resolver) {
        return methodCache.computeIfAbsent(key, k -> resolver.get());
    }

    public ResolvedField getField(String key, Supplier<ResolvedField> resolver) {
        return fieldCache.computeIfAbsent(key, k -> resolver.get());
    }

    public Boolean getAssignability(String key, Supplier<Boolean> computer) {
        return assignabilityCache.computeIfAbsent(key, k -> computer.get());
    }

    public void clear() {
        classCache.clear();
        methodCache.clear();
        fieldCache.clear();
        assignabilityCache.clear();
    }

    public int size() {
        return classCache.size() + methodCache.size() + fieldCache.size() + assignabilityCache.size();
    }
}
