package com.tonic.analysis.xref;

import com.tonic.analysis.common.MethodReference;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Cross-reference store that keeps every xref indexed by source class, target
 * class, target method, target field and reference type for constant-time lookup.
 */
public class XrefDatabase
{

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
     * Stores a cross-reference and files it into every index it belongs to.
     * @param xref the reference to add
     */
    public void addXref(Xref xref)
    {
        allXrefs.add(xref);

        byTargetClass.computeIfAbsent(xref.getTargetClass(), k ->
            Collections.synchronizedList(new ArrayList<>())).add(xref);

        bySourceClass.computeIfAbsent(xref.getSourceClass(), k ->
            Collections.synchronizedList(new ArrayList<>())).add(xref);

        if (xref.isMethodRef())
        {
            MethodReference targetMethod = xref.getTargetMethodRef();
            if (targetMethod != null)
            {
                byTargetMethod.computeIfAbsent(targetMethod, k ->
                    Collections.synchronizedList(new ArrayList<>())).add(xref);
            }
        }

        MethodReference sourceMethod = xref.getSourceMethodRef();
        if (sourceMethod != null && sourceMethod.getName() != null)
        {
            bySourceMethod.computeIfAbsent(sourceMethod, k ->
                Collections.synchronizedList(new ArrayList<>())).add(xref);
        }

        if (xref.isFieldRef())
        {
            FieldReference targetField = xref.getTargetFieldRef();
            if (targetField != null)
            {
                byTargetField.computeIfAbsent(targetField, k ->
                    Collections.synchronizedList(new ArrayList<>())).add(xref);
            }
        }

        byType.computeIfAbsent(xref.getType(), k -> Collections.synchronizedList(new ArrayList<>())).add(xref);
    }

    /**
     * Stores a batch of cross-references.
     * @param xrefs the references to add
     */
    public void addAllXrefs(Collection<Xref> xrefs)
    {
        for (Xref xref : xrefs)
        {
            addXref(xref);
        }
    }

    // Query Methods

    /**
     * @param className internal name of the referenced class
     * @return the references pointing at that class, empty if none
     */
    public List<Xref> getRefsToClass(String className)
    {
        return byTargetClass.getOrDefault(className, Collections.emptyList());
    }

    /**
     * @param className internal name of the referring class
     * @return the references originating in that class, empty if none
     */
    public List<Xref> getRefsFromClass(String className)
    {
        return bySourceClass.getOrDefault(className, Collections.emptyList());
    }

    /**
     * @param method the called method
     * @return the references calling it, empty if none
     */
    public List<Xref> getRefsToMethod(MethodReference method)
    {
        return byTargetMethod.getOrDefault(method, Collections.emptyList());
    }

    /**
     * Looks up callers of a method named by its parts.
     * @param owner internal name of the declaring class
     * @param name the method name
     * @param desc the method descriptor
     * @return the references calling it, empty if none
     */
    public List<Xref> getRefsToMethod(String owner, String name, String desc)
    {
        return getRefsToMethod(new MethodReference(owner, name, desc));
    }

    /**
     * Looks up callers by owner and name only, for queries that carry no descriptor.
     * @param owner internal name or simple name of the declaring class, or null for any
     * @param name the method name, or null for any
     * @return the references calling any matching method
     */
    public List<Xref> getRefsToMethodByName(String owner, String name)
    {
        List<Xref> results = new ArrayList<>();
        for (var entry : byTargetMethod.entrySet())
        {
            MethodReference ref = entry.getKey();
            boolean ownerMatches = owner == null || owner.isEmpty() ||
                ref.getOwner().equals(owner) || ref.getOwner().endsWith("/" + owner);
            boolean nameMatches = name == null || name.isEmpty() || ref.getName().equals(name);
            if (ownerMatches && nameMatches)
            {
                results.addAll(entry.getValue());
            }
        }
        return results;
    }

    /**
     * @param method the calling method
     * @return the references originating in its body, empty if none
     */
    public List<Xref> getRefsFromMethod(MethodReference method)
    {
        return bySourceMethod.getOrDefault(method, Collections.emptyList());
    }

    /**
     * Looks up the references made by a method named by its parts.
     * @param owner internal name of the declaring class
     * @param name the method name
     * @param desc the method descriptor
     * @return the references originating in its body, empty if none
     */
    public List<Xref> getRefsFromMethod(String owner, String name, String desc)
    {
        return getRefsFromMethod(new MethodReference(owner, name, desc));
    }

    /**
     * @param field the accessed field
     * @return the references reading or writing it, empty if none
     */
    public List<Xref> getRefsToField(FieldReference field)
    {
        return byTargetField.getOrDefault(field, Collections.emptyList());
    }

    /**
     * Looks up accesses of a field named by its parts.
     * @param owner internal name of the declaring class
     * @param name the field name
     * @param desc the field descriptor
     * @return the references reading or writing it, empty if none
     */
    public List<Xref> getRefsToField(String owner, String name, String desc)
    {
        return getRefsToField(new FieldReference(owner, name, desc));
    }

    /**
     * @param type the reference kind to select
     * @return the references of that kind, empty if none
     */
    public List<Xref> getRefsByType(XrefType type)
    {
        return byType.getOrDefault(type, Collections.emptyList());
    }

    /**
     * @return every method-call reference
     */
    public List<Xref> getAllMethodCalls()
    {
        return getRefsByType(XrefType.METHOD_CALL);
    }

    /**
     * @return every field-read reference
     */
    public List<Xref> getAllFieldReads()
    {
        return getRefsByType(XrefType.FIELD_READ);
    }

    /**
     * @return every field-write reference
     */
    public List<Xref> getAllFieldWrites()
    {
        return getRefsByType(XrefType.FIELD_WRITE);
    }

    /**
     * @return every class-instantiation reference
     */
    public List<Xref> getAllInstantiations()
    {
        return getRefsByType(XrefType.CLASS_INSTANTIATE);
    }

    /**
     * @return an unmodifiable view of every stored reference
     */
    public List<Xref> getAllXrefs()
    {
        return Collections.unmodifiableList(allXrefs);
    }

    // Combined Queries

    /**
     * Scans every reference for a target class or member containing the query,
     * with dots in the query treated as package separators.
     * @param query the substring to match
     * @return the matching references
     */
    public List<Xref> searchIncomingRefs(String query)
    {
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
     * Scans every reference for a source class or method containing the query,
     * with dots in the query treated as package separators.
     * @param query the substring to match
     * @return the matching references
     */
    public List<Xref> searchOutgoingRefs(String query)
    {
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
     * Collects callers of every indexed method carrying a given name, whatever
     * the owner or descriptor.
     * @param methodName the exact method name to match
     * @return the references calling any such method
     */
    public List<Xref> findCallersOfMethodNamed(String methodName)
    {
        return byTargetMethod.entrySet().stream()
            .filter(e -> e.getKey().getName().equals(methodName))
            .flatMap(e -> e.getValue().stream())
            .collect(Collectors.toList());
    }

    /**
     * Collects the references one class makes to another.
     * @param sourceClass internal name of the referring class
     * @param targetClass internal name of the referenced class
     * @return the references from source to target
     */
    public List<Xref> findRefsBetweenClasses(String sourceClass, String targetClass)
    {
        return getRefsFromClass(sourceClass).stream()
            .filter(xref -> xref.getTargetClass().equals(targetClass))
            .collect(Collectors.toList());
    }

    /**
     * @param className internal name of the referenced class
     * @return the incoming references bucketed by reference kind
     */
    public Map<XrefType, List<Xref>> groupIncomingByType(String className)
    {
        return getRefsToClass(className).stream()
            .collect(Collectors.groupingBy(Xref::getType));
    }

    /**
     * @param className internal name of the referring class
     * @return the outgoing references bucketed by reference kind
     */
    public Map<XrefType, List<Xref>> groupOutgoingByType(String className)
    {
        return getRefsFromClass(className).stream()
            .collect(Collectors.groupingBy(Xref::getType));
    }

    /**
     * @param className internal name of the referenced class
     * @return the distinct classes that reference it
     */
    public Set<String> getClassesReferencingClass(String className)
    {
        return getRefsToClass(className).stream()
            .map(Xref::getSourceClass)
            .collect(Collectors.toSet());
    }

    /**
     * @param className internal name of the referring class
     * @return the distinct classes it references
     */
    public Set<String> getClassesReferencedByClass(String className)
    {
        return getRefsFromClass(className).stream()
            .map(Xref::getTargetClass)
            .collect(Collectors.toSet());
    }

    // Statistics

    /**
     * @return the number of stored references
     */
    public int getTotalXrefCount()
    {
        return allXrefs.size();
    }

    /**
     * @return the reference count per reference kind
     */
    public Map<XrefType, Integer> getXrefCountByType()
    {
        return byType.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));
    }

    /**
     * @return the number of distinct referenced classes
     */
    public int getUniqueTargetClassCount()
    {
        return byTargetClass.size();
    }

    /**
     * @return the number of distinct referring classes
     */
    public int getUniqueSourceClassCount()
    {
        return bySourceClass.size();
    }

    /**
     * @return the number of distinct referenced methods
     */
    public int getUniqueTargetMethodCount()
    {
        return byTargetMethod.size();
    }

    /**
     * @return the number of distinct referenced fields
     */
    public int getUniqueTargetFieldCount()
    {
        return byTargetField.size();
    }

    /**
     * @return the build time ms
     */
    public long getBuildTimeMs()
    {
        return buildTimeMs;
    }

    /**
     * Records how long the indexing run took.
     * @param buildTimeMs elapsed build time in milliseconds
     */
    public void setBuildTimeMs(long buildTimeMs)
    {
        this.buildTimeMs = buildTimeMs;
    }

    /**
     * @return the total classes
     */
    public int getTotalClasses()
    {
        return totalClasses;
    }

    /**
     * Records how many classes the indexing run scanned.
     * @param totalClasses the scanned class count
     */
    public void setTotalClasses(int totalClasses)
    {
        this.totalClasses = totalClasses;
    }

    /**
     * @return the total methods
     */
    public int getTotalMethods()
    {
        return totalMethods;
    }

    /**
     * Records how many methods the indexing run scanned.
     * @param totalMethods the scanned method count
     */
    public void setTotalMethods(int totalMethods)
    {
        this.totalMethods = totalMethods;
    }

    /**
     * @return a one-line summary of the reference, class and method counts and build time
     */
    public String getSummary()
    {
        return String.format("XrefDatabase: %d xrefs, %d classes analyzed, %d methods, built in %dms",
            getTotalXrefCount(), totalClasses, totalMethods, buildTimeMs);
    }

    // Management

    /**
     * Clear all xrefs and indexes.
     */
    public void clear()
    {
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
     *
     * @return true if no cross-references have been recorded
     */
    public boolean isEmpty()
    {
        return allXrefs.isEmpty();
    }
}
