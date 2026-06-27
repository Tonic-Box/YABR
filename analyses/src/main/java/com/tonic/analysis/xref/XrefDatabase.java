package com.tonic.analysis.xref;

import com.tonic.analysis.common.MethodReference;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Indexed database for cross-references with fast lookup capabilities.
 *
 * Provides efficient queries for:
 * - All references TO a class/method/field (incoming)
 * - All references FROM a class/method (outgoing)
 * - References by type
 * - References within a specific class
 */
public class XrefDatabase {

    // Primary storage
    private final List<Xref> allXrefs = Collections.synchronizedList(new ArrayList<>());

    // Index by target class (incoming refs to a class)
    private final Map<String, List<Xref>> byTargetClass = new ConcurrentHashMap<>();

    // Index by source class (outgoing refs from a class)
    private final Map<String, List<Xref>> bySourceClass = new ConcurrentHashMap<>();

    // Index by target method (incoming refs to a method)
    private final Map<MethodReference, List<Xref>> byTargetMethod = new ConcurrentHashMap<>();

    // Index by source method (outgoing refs from a method)
    private final Map<MethodReference, List<Xref>> bySourceMethod = new ConcurrentHashMap<>();

    // Index by target field (incoming refs to a field)
    private final Map<FieldReference, List<Xref>> byTargetField = new ConcurrentHashMap<>();

    // Index by reference type
    private final Map<XrefType, List<Xref>> byType = new ConcurrentHashMap<>();

    // Statistics
    private long buildTimeMs;
    private int totalClasses;
    private int totalMethods;

    /**
     * Add a cross-reference to the database and update all indexes.
     */
    public void addXref(Xref xref) {
        allXrefs.add(xref);

        // Index by target class
        byTargetClass.computeIfAbsent(xref.getTargetClass(), k ->
            Collections.synchronizedList(new ArrayList<>())).add(xref);

        // Index by source class
        bySourceClass.computeIfAbsent(xref.getSourceClass(), k ->
            Collections.synchronizedList(new ArrayList<>())).add(xref);

        // Index by target method
        if (xref.isMethodRef()) {
            MethodReference targetMethod = xref.getTargetMethodRef();
            if (targetMethod != null) {
                byTargetMethod.computeIfAbsent(targetMethod, k ->
                    Collections.synchronizedList(new ArrayList<>())).add(xref);
            }
        }

        // Index by source method
        MethodReference sourceMethod = xref.getSourceMethodRef();
        if (sourceMethod != null && sourceMethod.getName() != null) {
            bySourceMethod.computeIfAbsent(sourceMethod, k ->
                Collections.synchronizedList(new ArrayList<>())).add(xref);
        }

        // Index by target field
        if (xref.isFieldRef()) {
            FieldReference targetField = xref.getTargetFieldRef();
            if (targetField != null) {
                byTargetField.computeIfAbsent(targetField, k ->
                    Collections.synchronizedList(new ArrayList<>())).add(xref);
            }
        }

        // Index by type
        byType.computeIfAbsent(xref.getType(), k ->
            Collections.synchronizedList(new ArrayList<>())).add(xref);
    }

    /**
     * Add multiple xrefs at once.
     */
    public void addAllXrefs(Collection<Xref> xrefs) {
        for (Xref xref : xrefs) {
            addXref(xref);
        }
    }

    // ==================== Query Methods ====================

    /**
     * Get all references TO a class (incoming).
     */
    public List<Xref> getRefsToClass(String className) {
        return byTargetClass.getOrDefault(className, Collections.emptyList());
    }

    /**
     * Get all references FROM a class (outgoing).
     */
    public List<Xref> getRefsFromClass(String className) {
        return bySourceClass.getOrDefault(className, Collections.emptyList());
    }

    /**
     * Get all references TO a method (incoming - who calls this method?).
     */
    public List<Xref> getRefsToMethod(MethodReference method) {
        return byTargetMethod.getOrDefault(method, Collections.emptyList());
    }

    /**
     * Get all references TO a method by components.
     */
    public List<Xref> getRefsToMethod(String owner, String name, String desc) {
        return getRefsToMethod(new MethodReference(owner, name, desc));
    }

    /**
     * Get all references TO a method by owner and name, ignoring descriptor.
     * Useful for queries that don't specify a descriptor (e.g., finding all calls to println).
     */
    public List<Xref> getRefsToMethodByName(String owner, String name) {
        List<Xref> results = new ArrayList<>();
        for (var entry : byTargetMethod.entrySet()) {
            MethodReference ref = entry.getKey();
            boolean ownerMatches = owner == null || owner.isEmpty() ||
                ref.getOwner().equals(owner) || ref.getOwner().endsWith("/" + owner);
            boolean nameMatches = name == null || name.isEmpty() || ref.getName().equals(name);
            if (ownerMatches && nameMatches) {
                results.addAll(entry.getValue());
            }
        }
        return results;
    }

    /**
     * Get all references FROM a method (outgoing - what does this method call?).
     */
    public List<Xref> getRefsFromMethod(MethodReference method) {
        return bySourceMethod.getOrDefault(method, Collections.emptyList());
    }

    /**
     * Get all references FROM a method by components.
     */
    public List<Xref> getRefsFromMethod(String owner, String name, String desc) {
        return getRefsFromMethod(new MethodReference(owner, name, desc));
    }

    /**
     * Get all references TO a field (incoming - who reads/writes this field?).
     */
    public List<Xref> getRefsToField(FieldReference field) {
        return byTargetField.getOrDefault(field, Collections.emptyList());
    }

    /**
     * Get all references TO a field by components.
     */
    public List<Xref> getRefsToField(String owner, String name, String desc) {
        return getRefsToField(new FieldReference(owner, name, desc));
    }

    /**
     * Get all references of a specific type.
     */
    public List<Xref> getRefsByType(XrefType type) {
        return byType.getOrDefault(type, Collections.emptyList());
    }

    /**
     * Get all method calls in the database.
     */
    public List<Xref> getAllMethodCalls() {
        return getRefsByType(XrefType.METHOD_CALL);
    }

    /**
     * Get all field reads in the database.
     */
    public List<Xref> getAllFieldReads() {
        return getRefsByType(XrefType.FIELD_READ);
    }

    /**
     * Get all field writes in the database.
     */
    public List<Xref> getAllFieldWrites() {
        return getRefsByType(XrefType.FIELD_WRITE);
    }

    /**
     * Get all class instantiations.
     */
    public List<Xref> getAllInstantiations() {
        return getRefsByType(XrefType.CLASS_INSTANTIATE);
    }

    /**
     * Get all xrefs.
     */
    public List<Xref> getAllXrefs() {
        return Collections.unmodifiableList(allXrefs);
    }

    // ==================== Combined Queries ====================

    /**
     * Get all incoming refs to a symbol (class, method, or field by name search).
     */
    public List<Xref> searchIncomingRefs(String query) {
        String normalizedQuery = query.replace('.', '/');

        return allXrefs.stream()
            .filter(xref -> {
                String target = xref.getTargetClass();
                String member = xref.getTargetMember();
                return target.contains(normalizedQuery) ||
                       (member != null && member.contains(query));
            })
            .collect(Collectors.toList());
    }

    /**
     * Get all outgoing refs from a symbol.
     */
    public List<Xref> searchOutgoingRefs(String query) {
        String normalizedQuery = query.replace('.', '/');

        return allXrefs.stream()
            .filter(xref -> {
                String source = xref.getSourceClass();
                String method = xref.getSourceMethod();
                return source.contains(normalizedQuery) ||
                       (method != null && method.contains(query));
            })
            .collect(Collectors.toList());
    }

    /**
     * Find all callers of methods matching a pattern (for "find usages" style queries).
     */
    public List<Xref> findCallersOfMethodNamed(String methodName) {
        return byTargetMethod.entrySet().stream()
            .filter(e -> e.getKey().getName().equals(methodName))
            .flatMap(e -> e.getValue().stream())
            .collect(Collectors.toList());
    }

    /**
     * Find all references between two classes.
     */
    public List<Xref> findRefsBetweenClasses(String sourceClass, String targetClass) {
        return getRefsFromClass(sourceClass).stream()
            .filter(xref -> xref.getTargetClass().equals(targetClass))
            .collect(Collectors.toList());
    }

    /**
     * Group incoming refs by type.
     */
    public Map<XrefType, List<Xref>> groupIncomingByType(String className) {
        return getRefsToClass(className).stream()
            .collect(Collectors.groupingBy(Xref::getType));
    }

    /**
     * Group outgoing refs by type.
     */
    public Map<XrefType, List<Xref>> groupOutgoingByType(String className) {
        return getRefsFromClass(className).stream()
            .collect(Collectors.groupingBy(Xref::getType));
    }

    /**
     * Get all unique classes that reference a given class.
     */
    public Set<String> getClassesReferencingClass(String className) {
        return getRefsToClass(className).stream()
            .map(Xref::getSourceClass)
            .collect(Collectors.toSet());
    }

    /**
     * Get all unique classes referenced by a given class.
     */
    public Set<String> getClassesReferencedByClass(String className) {
        return getRefsFromClass(className).stream()
            .map(Xref::getTargetClass)
            .collect(Collectors.toSet());
    }

    // ==================== Statistics ====================

    /**
     * Get total number of cross-references.
     */
    public int getTotalXrefCount() {
        return allXrefs.size();
    }

    /**
     * Get count of xrefs by type.
     */
    public Map<XrefType, Integer> getXrefCountByType() {
        return byType.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));
    }

    /**
     * Get number of unique target classes.
     */
    public int getUniqueTargetClassCount() {
        return byTargetClass.size();
    }

    /**
     * Get number of unique source classes.
     */
    public int getUniqueSourceClassCount() {
        return bySourceClass.size();
    }

    /**
     * Get number of unique target methods.
     */
    public int getUniqueTargetMethodCount() {
        return byTargetMethod.size();
    }

    /**
     * Get number of unique target fields.
     */
    public int getUniqueTargetFieldCount() {
        return byTargetField.size();
    }

    public long getBuildTimeMs() {
        return buildTimeMs;
    }

    public void setBuildTimeMs(long buildTimeMs) {
        this.buildTimeMs = buildTimeMs;
    }

    public int getTotalClasses() {
        return totalClasses;
    }

    public void setTotalClasses(int totalClasses) {
        this.totalClasses = totalClasses;
    }

    public int getTotalMethods() {
        return totalMethods;
    }

    public void setTotalMethods(int totalMethods) {
        this.totalMethods = totalMethods;
    }

    /**
     * Get a summary string for display.
     */
    public String getSummary() {
        return String.format("XrefDatabase: %d xrefs, %d classes analyzed, %d methods, built in %dms",
            getTotalXrefCount(), totalClasses, totalMethods, buildTimeMs);
    }

    // ==================== Management ====================

    /**
     * Clear all xrefs and indexes.
     */
    public void clear() {
        allXrefs.clear();
        byTargetClass.clear();
        bySourceClass.clear();
        byTargetMethod.clear();
        bySourceMethod.clear();
        byTargetField.clear();
        byType.clear();
        buildTimeMs = 0;
        totalClasses = 0;
        totalMethods = 0;
    }

    /**
     * Check if the database is empty.
     */
    public boolean isEmpty() {
        return allXrefs.isEmpty();
    }
}
